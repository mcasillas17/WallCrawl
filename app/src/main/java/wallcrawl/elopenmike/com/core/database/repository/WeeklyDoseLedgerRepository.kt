package wallcrawl.elopenmike.com.core.database.repository

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import wallcrawl.elopenmike.com.core.ai.LedgerSourceFingerprint
import wallcrawl.elopenmike.com.core.ai.WeeklyDoseLedgerCalculator
import wallcrawl.elopenmike.com.core.database.dao.CompletedWorkoutHistoryDao
import wallcrawl.elopenmike.com.core.database.dao.WeeklyDoseLedgerStateDao
import wallcrawl.elopenmike.com.core.database.entity.WeeklyDoseLedgerStateEntity
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutSession
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSnapshot
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger

/**
 * Reads the weekly resistance-training exposure for one profile.
 *
 * A ledger is always reconstructed from completed history. The cache exists only to avoid
 * repeating that work, never to hold a number that history no longer supports.
 */
interface WeeklyDoseLedgerRepository {

    /** The ledger for the ISO week containing [instant] as observed in [zoneId]. */
    suspend fun weeklyLedgerAt(
        profileId: String,
        instant: Instant,
        zoneId: ZoneId
    ): WeeklyDoseLedger

    /** The ledger for the ISO week the injected clock is currently in, as seen in [zoneId]. */
    suspend fun currentWeeklyLedger(profileId: String, zoneId: ZoneId): WeeklyDoseLedger
}

/**
 * Reconstructs and caches `PRIMARY_ONLY_V1` weekly ledgers from the local database.
 *
 * The data flow is one direction only:
 *
 * ```text
 * completed history + bundled approved metadata
 *   -> weekly range query
 *   -> pure calculator
 *   -> local reconstructable cache
 *   -> local consumers
 * ```
 *
 * Nothing here increments a stored count. Completing a set writes history; a ledger is only
 * ever produced by recomputing from that history, so the cache can be deleted or corrupted
 * without changing a single credited set.
 *
 * Database and catalog failures propagate. An unreadable catalog is not an empty training
 * week, and reporting one as the other would quietly under-report a user's real work.
 */
class OfflineWeeklyDoseLedgerRepository(
    private val historyDao: CompletedWorkoutHistoryDao,
    private val ledgerStateDao: WeeklyDoseLedgerStateDao,
    private val catalogSource: WorkoutGuideCatalogSource,
    private val calculator: WeeklyDoseLedgerCalculator = WeeklyDoseLedgerCalculator(),
    private val clock: Clock = Clock.systemDefaultZone()
) : WeeklyDoseLedgerRepository {

    override suspend fun currentWeeklyLedger(
        profileId: String,
        zoneId: ZoneId
    ): WeeklyDoseLedger = weeklyLedgerAt(profileId, clock.instant(), zoneId)

    override suspend fun weeklyLedgerAt(
        profileId: String,
        instant: Instant,
        zoneId: ZoneId
    ): WeeklyDoseLedger {
        require(profileId.isNotBlank()) { "profileId must not be blank." }
        val week = TrainingWeek.containing(instant, zoneId)

        val snapshot = catalogSource.snapshot()
        val exercisesById = snapshot.exercisesById()
        val catalogVersion = snapshot.catalogAttribution.commit
        check(catalogVersion.isNotBlank()) {
            "The bundled catalog is missing the source commit that identifies its version."
        }
        val reviewPolicyVersion = snapshot.reviewPolicyVersion()

        val sessions = historyDao
            .getCompletedSessionsInRange(week.startEpochMillis, week.endEpochMillisExclusive)
            .map { it.toWorkoutSession() }

        val fingerprint = LedgerSourceFingerprint.of(
            sessions = sessions,
            exercisesById = exercisesById,
            policyVersion = POLICY_VERSION,
            week = week,
            catalogVersion = catalogVersion,
            reviewPolicyVersion = reviewPolicyVersion
        )

        readUsableCache(
            profileId = profileId,
            week = week,
            catalogVersion = catalogVersion,
            reviewPolicyVersion = reviewPolicyVersion,
            fingerprint = fingerprint
        )?.let { return it }

        val ledger = calculator.calculate(
            sessions = sessions,
            exercisesById = exercisesById,
            policyVersion = POLICY_VERSION,
            week = week,
            catalogVersion = catalogVersion,
            reviewPolicyVersion = reviewPolicyVersion
        )

        // Replaced wholesale, never incremented. Two concurrent readers of the same week
        // compute the same ledger, so either write leaves the row in the same state.
        ledgerStateDao.upsertCachedLedger(
            WeeklyDoseLedgerStateEntity(
                profileId = profileId,
                weekStartEpochDay = week.startEpochDay,
                timeZoneId = week.zoneId.id,
                policyVersion = POLICY_VERSION.name,
                catalogVersion = catalogVersion,
                reviewPolicyVersion = reviewPolicyVersion,
                ledgerPayload = WeeklyDoseLedgerPayload.encode(ledger),
                sourceFingerprint = fingerprint,
                generatedAtTimestamp = clock.millis()
            )
        )
        return ledger
    }

    /**
     * The cached ledger for this week, or null when it cannot be trusted.
     *
     * The fingerprint already covers the catalog and review-policy versions; they are also
     * compared directly so a row written by a different build has to agree on both counts
     * before it is served.
     */
    private suspend fun readUsableCache(
        profileId: String,
        week: TrainingWeek,
        catalogVersion: String,
        reviewPolicyVersion: Int,
        fingerprint: String
    ): WeeklyDoseLedger? {
        val cached = ledgerStateDao.findCachedLedger(
            profileId = profileId,
            weekStartEpochDay = week.startEpochDay,
            timeZoneId = week.zoneId.id,
            policyVersion = POLICY_VERSION.name
        ) ?: return null

        if (cached.sourceFingerprint != fingerprint) return null
        if (cached.catalogVersion != catalogVersion) return null
        if (cached.reviewPolicyVersion != reviewPolicyVersion) return null

        return WeeklyDoseLedgerPayload.decode(
            payload = cached.ledgerPayload,
            policyVersion = POLICY_VERSION,
            weekStartEpochDay = cached.weekStartEpochDay,
            timeZoneId = cached.timeZoneId,
            catalogVersion = cached.catalogVersion,
            reviewPolicyVersion = cached.reviewPolicyVersion
        )
    }

    /**
     * Catalog ids are matched exactly.
     *
     * An id that does not resolve exactly is reported as `UNKNOWN_EXERCISE` rather than
     * matched loosely, because a near-miss is not evidence of which muscle was trained.
     */
    private fun WorkoutGuideCatalogSnapshot.exercisesById(): Map<String, Exercise> =
        exercises.associateBy(Exercise::id)

    /**
     * The review-policy version this catalog's reviewed metadata was authored under.
     *
     * It is read from the catalog rather than hard-coded, so shipping metadata authored
     * under a new review policy invalidates every cached ledger instead of silently
     * reinterpreting old counts. A catalog carrying no reviewed metadata reports 0.
     */
    private fun WorkoutGuideCatalogSnapshot.reviewPolicyVersion(): Int =
        exercises.mapNotNull { it.reviewedMetadata?.provenance?.policyVersion }.maxOrNull() ?: 0

    private companion object {
        val POLICY_VERSION = LedgerPolicyVersion.PRIMARY_ONLY_V1
    }
}
