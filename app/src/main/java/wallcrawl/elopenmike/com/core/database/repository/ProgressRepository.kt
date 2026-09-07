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
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator

data class ProgressSnapshot(
    val overview: ProgressOverview,
    val preferredUnit: WeightUnit,
    val week: TrainingWeek,
    val reviewedDose: WeeklyDoseLedger,
    val previousReviewedDose: WeeklyDoseLedger
)

interface ProgressRepository {
    /** Null means the local profile was deleted or has not been created, not a read failure. */
    fun observeProgress(now: () -> Instant, zoneId: ZoneId): Flow<ProgressSnapshot?>
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
    private val calculator: ProgressCalculator = ProgressCalculator()
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

    private companion object {
        const val MAX_ANALYTICS_SESSIONS = 500
    }
}
