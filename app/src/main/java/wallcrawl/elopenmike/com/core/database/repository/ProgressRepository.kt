package wallcrawl.elopenmike.com.core.database.repository

import androidx.room.withTransaction
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutSession
import wallcrawl.elopenmike.com.core.ai.TrainingFrequencyRecencyPolicy
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits
import wallcrawl.elopenmike.com.core.ai.acceptedReviewPolicyVersion
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.model.AnalyticsScope
import wallcrawl.elopenmike.com.core.model.AnalyticsRange
import wallcrawl.elopenmike.com.core.model.ProgressAnalytics
import wallcrawl.elopenmike.com.core.model.RecommendationRecord
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.progress.AnalyticsRangeTooLargeException
import wallcrawl.elopenmike.com.core.progress.ProgressAnalyticsCalculator
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator

data class ProgressSnapshot(
    val overview: ProgressOverview,
    val preferredUnit: WeightUnit,
    val week: TrainingWeek,
    val reviewedDose: WeeklyDoseLedger,
    val previousReviewedDose: WeeklyDoseLedger
)

/** A recommendation recorded by one range session, kept for decoding by the history presenter. */
data class RecordedSessionDecision(
    val sessionId: String,
    val sessionName: String,
    val completedAtTimestamp: Long,
    val record: RecommendationRecord
)

data class ProgressAnalyticsSnapshot(
    val analytics: ProgressAnalytics,
    val preferredUnit: WeightUnit,
    /**
     * Distinct exercise IDs in completed history, the exercise filter's options; null when there are
     * more than the chooser can list, so none is hidden from a partial list.
     */
    val exerciseOptions: List<String>?,
    /** Current catalog names, an untranslated fallback label only; IDs remain the identity. */
    val catalogNames: Map<String, String>,
    /** Exercise scope only: recorded provenance of range sessions containing the exercise. */
    val decisions: List<RecordedSessionDecision>
)

interface ProgressRepository {
    /** Null means the local profile was deleted or has not been created, not a read failure. */
    fun observeProgress(now: () -> Instant, zoneId: ZoneId): Flow<ProgressSnapshot?>

    /**
     * Null means no local profile. Reads only; never writes history, provenance or the ledger cache.
     * Every metric is computed per read, so only [range] and [scope] select what is read.
     */
    fun observeAnalytics(
        range: AnalyticsRange,
        scope: AnalyticsScope,
        now: () -> Instant,
        zoneId: ZoneId
    ): Flow<ProgressAnalyticsSnapshot?>
}

/**
 * Reads activity and reviewed accounting from one history revision.
 *
 * The shared write gate precedes the Room transaction because ledger reads may rebuild
 * their cache. A read after deletion never bootstraps a profile or recreates a cache row.
 * Cache invalidations are deliberately not observed: rebuilding a cache is not new activity.
 */
class OfflineProgressRepository(
    private val database: WallCrawlDatabase,
    private val catalogSource: WorkoutGuideCatalogSource,
    private val ledgerRepository: WeeklyDoseLedgerRepository,
    private val localDataWriteGate: Mutex,
    private val calculator: ProgressCalculator = ProgressCalculator(),
    private val analyticsCalculator: ProgressAnalyticsCalculator = ProgressAnalyticsCalculator()
) : ProgressRepository {
    override fun observeProgress(now: () -> Instant, zoneId: ZoneId): Flow<ProgressSnapshot?> {
        return database.invalidationTracker.createFlow(
            "user_profiles", "workout_sessions", "workout_exercises", "workout_sets"
        ).map {
            localDataWriteGate.withLock {
                database.withTransaction {
                    val instant = now()
                    val week = TrainingWeek.containing(instant, zoneId)
                    val previousWeek = TrainingWeek.startingOn(week.startEpochDay - 7, zoneId)
                    val profile = database.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)
                        ?.toUserProfile() ?: return@withTransaction null
                    val catalog = catalogSource.snapshot()
                    val historyDao = database.completedWorkoutHistoryDao()
                    val weeklySessions = historyDao.getCompletedSessionsInRange(
                        previousWeek.startEpochMillis, week.endEpochMillisExclusive
                    ).map { it.toWorkoutSession() }
                    val recent = database.workoutSessionDao()
                        .getRecentCompletedSessions(MAX_ANALYTICS_SESSIONS)
                        .map { it.toWorkoutSession() }
                    val timestamps = historyDao.getCompletedTimestamps()
                    val currentLedger = ledgerRepository.weeklyLedgerAt(profile.id, instant, zoneId)
                    val previousLedger = ledgerRepository.weeklyLedgerAt(
                        profile.id, Instant.ofEpochMilli(previousWeek.startEpochMillis), zoneId
                    )
                    check(currentLedger.catalogVersion == catalog.catalogAttribution.commit &&
                        previousLedger.catalogVersion == catalog.catalogAttribution.commit) {
                        "The Progress catalog changed during accounting."
                    }
                    ProgressSnapshot(
                        overview = calculator.calculate(
                            completedSessions = recent,
                            profile = profile,
                            catalogExercises = catalog.exercises,
                            nowTimestamp = instant.toEpochMilli(),
                            zoneId = zoneId,
                            weeklySessions = weeklySessions,
                            completedTimestamps = timestamps
                        ),
                        preferredUnit = profile.preferredUnit,
                        week = week,
                        reviewedDose = currentLedger,
                        previousReviewedDose = previousLedger
                    )
                }
            }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * The same gate, transaction and no-bootstrap profile read as [observeProgress]. Sizes and
     * strict type validity are checked before the range graph is loaded; the ledger is computed
     * by the pure calculator, so no cache row is written.
     */
    override fun observeAnalytics(
        range: AnalyticsRange,
        scope: AnalyticsScope,
        now: () -> Instant,
        zoneId: ZoneId
    ): Flow<ProgressAnalyticsSnapshot?> = database.invalidationTracker.createFlow(
        "user_profiles", "workout_sessions", "workout_exercises", "workout_sets", "workout_recommendation_records"
    ).map {
        localDataWriteGate.withLock {
            database.withTransaction {
                val instant = now()
                val weeks = ProgressAnalyticsCalculator.rangeWeeks(range, instant, zoneId)
                val start = weeks.first().startEpochMillis
                val end = weeks.last().endEpochMillisExclusive
                val profile = database.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)
                    ?.toUserProfile() ?: return@withTransaction null
                val catalog = catalogSource.snapshot()
                val dao = database.workoutSessionDao()
                val size = dao.selectAnalyticsRangeSize(start, end)
                if (size.sessionCount > MAX_RANGE_SESSIONS || size.exerciseCount > MAX_RANGE_EXERCISES ||
                    size.setCount > MAX_RANGE_SETS
                ) {
                    throw AnalyticsRangeTooLargeException()
                }
                require(!size.invalidMetadata) { "Stored workout history contains invalid typed metadata." }
                val sessions = dao.getCompletedSessionsInRange(start, end, MAX_RANGE_SESSIONS)
                    .map { it.toWorkoutSession() }
                val exerciseId = (scope as? AnalyticsScope.Exercise)?.exerciseId
                val comparable = exerciseId
                    ?.let { dao.selectAnalyticsComparableSetIds(start, end, it).toSet() }
                    .orEmpty()
                val sessionsById = sessions.associateBy { it.id }
                val decisions = exerciseId?.let { id ->
                    dao.selectAnalyticsRecommendationRecords(start, end, id).map { entity ->
                        val session = sessionsById.getValue(entity.sessionId)
                        RecordedSessionDecision(
                            session.id, session.name, requireNotNull(session.completedAtTimestamp),
                            entity.toRecommendationRecord()
                        )
                    }
                }.orEmpty()
                // Overflow lists nothing rather than a partial list; the chart itself is unaffected.
                val options = dao.selectAnalyticsExerciseIds(MAX_EXERCISE_OPTIONS + 1)
                    .takeIf { it.size <= MAX_EXERCISE_OPTIONS }
                val exercisesById = catalog.exercises.associateBy { it.id }
                // Everything is read; the pure calculation runs after the gate and transaction are
                // released, so writers wait only for the reads, not for the computation.
                val compute: () -> ProgressAnalyticsSnapshot = {
                    ProgressAnalyticsSnapshot(
                        analytics = analyticsCalculator.calculate(
                            range = range,
                            scope = scope,
                            sessions = sessions,
                            comparableSetIds = comparable,
                            exercisesById = exercisesById,
                            catalogVersion = catalog.catalogAttribution.commit,
                            reviewPolicyVersion = catalog.exercises.acceptedReviewPolicyVersion(),
                            preferredUnit = profile.preferredUnit,
                            now = instant,
                            zoneId = zoneId
                        ),
                        preferredUnit = profile.preferredUnit,
                        exerciseOptions = options,
                        // The selected exercise keeps its name even when the option list overflows.
                        catalogNames = (options.orEmpty() + listOfNotNull(exerciseId)).distinct()
                            .mapNotNull { id -> exercisesById[id]?.let { id to it.name } }.toMap(),
                        decisions = decisions
                    )
                }
                compute
            }
        }?.invoke()
    }.flowOn(Dispatchers.Default)

    private companion object {
        const val MAX_ANALYTICS_SESSIONS = 500
        // The scheduling range's representational bounds; software limits, not training ones.
        const val MAX_RANGE_SESSIONS = TrainingFrequencyRecencyPolicy.MAX_SESSIONS
        const val MAX_RANGE_SETS = TrainingFrequencyRecencyPolicy.MAX_SETS
        const val MAX_RANGE_EXERCISES = MAX_RANGE_SESSIONS * LocalDataArchiveLimits.MAX_EXERCISES_PER_SESSION
        const val MAX_EXERCISE_OPTIONS = 1_000
    }
}
