package wallcrawl.elopenmike.com.core.database.repository

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.relation.WorkoutExerciseWithSets
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutExercise
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutSession
import wallcrawl.elopenmike.com.core.model.ProgressionReasonCode
import wallcrawl.elopenmike.com.core.model.RecommendationRecord
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutHistoryEntry
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator

interface WorkoutHistoryRepository {
    /** Missing sessions emit null; unreadable stored data fails the flow. */
    fun observeDetail(sessionId: String): Flow<WorkoutHistoryDetail?>
    /** Zero-based pages of 20 completed sessions, with no accumulated history in memory. */
    fun observeHistory(page: Int): Flow<WorkoutHistoryPage>
}

data class WorkoutHistoryDetail(
    val session: WorkoutSession,
    val summary: WorkoutSummary?,
    val recommendation: RecommendationRecord?,
    /** Keys are viewed exercise-instance IDs, not catalog IDs. */
    val previousPerformances: Map<String, HistoricalExercisePerformance>,
    /** Only the cited exercise slice for this viewed instance, never a partial session graph. */
    val progressionSources: Map<HistoricalSourceKey, HistoricalExercisePerformance>
)

data class HistoricalSourceKey(val sourceSessionId: String, val viewedExerciseInstanceId: String)

/** Metadata sufficient to label an earlier exercise slice, not an incomplete WorkoutSession. */
data class HistoricalSessionHeader(
    val id: String,
    val name: String,
    val startedAtTimestamp: Long,
    val completedAtTimestamp: Long,
    val weightUnit: WeightUnit
)

/** Both ordinary comparisons and recorded citations contain only eligible historical sets. */
data class HistoricalExercisePerformance(val session: HistoricalSessionHeader, val exercise: WorkoutExercise)

data class WorkoutHistoryPage(val sessions: List<WorkoutHistoryEntry>, val hasOlder: Boolean)

class OfflineWorkoutHistoryRepository(
    private val database: WallCrawlDatabase,
    private val localDataWriteGate: Mutex
) : WorkoutHistoryRepository {
    private val calculator = ProgressCalculator()

    override fun observeDetail(sessionId: String): Flow<WorkoutHistoryDetail?> {
        require(sessionId.isNotBlank()) { "sessionId must not be blank." }
        return observeRead(
            "workout_sessions", "workout_exercises", "workout_sets", "workout_recommendation_records"
        ) {
            val dao = database.workoutSessionDao()
            val session = dao.getBoundedHistorySessions(listOf(sessionId)).singleOrNull()?.toWorkoutSession()
                ?: return@observeRead null
            // The mapper rejects known corrupt accounting/reason groups; absence alone is normal.
            val recommendation = dao.getRecommendationRecord(sessionId)?.toRecommendationRecord()
            val provenance = recommendation?.let { ProgressionReasonCode.decode(it.reasonCodes) }.orEmpty()
            val sourcePairs = provenance.flatMap { reason ->
                reason.sourceSessionIds.map { it + '\u001f' + reason.exerciseId }
            }.distinct()
            require(sourcePairs.size <= ProgressionReasonCode.MAX_EXERCISES * 2) {
                "Stored progression sources exceed their representation bound."
            }
            val sourceIds = provenance.flatMap { it.sourceSessionIds }.distinct()
            val citedIds = if (sourcePairs.isEmpty()) emptyList() else {
                dao.selectCitedPerformanceIds(sessionId, sourceIds, sourcePairs)
            }
            val previousIds = dao.selectPreviousPerformanceIds(sessionId)
            val performances = readPerformances(
                (previousIds + citedIds).map { it.previousExerciseId }.distinct()
            )
            val sources = citedIds.associate { ids ->
                HistoricalSourceKey(ids.previousSessionId, ids.viewedExerciseId) to
                    performances.getValue(ids.previousExerciseId)
            }
            val previous = previousIds.associate { ids ->
                ids.viewedExerciseId to performances.getValue(ids.previousExerciseId)
            }
            WorkoutHistoryDetail(
                session = session,
                summary = if (session.status == SessionStatus.COMPLETED) {
                    calculator.summarize(session, dao.getPersonalRecordBaselines(sessionId))
                } else null,
                recommendation = recommendation,
                previousPerformances = previous,
                progressionSources = sources
            )
        }
    }

    /** Invoked inside the detail transaction; no unrelated exercises or sets are materialized. */
    private suspend fun readPerformances(exerciseIds: List<String>): Map<String, HistoricalExercisePerformance> {
        // Each viewed instance has one ordinary comparison and at most two recorded citations.
        require(exerciseIds.size <= LocalDataArchiveLimits.MAX_EXERCISES_PER_SESSION * 3) {
            "Historical exercise projection exceeds its representation bound."
        }
        if (exerciseIds.isEmpty()) return emptyMap()
        val dao = database.workoutSessionDao()
        val rows = dao.selectHistoricalExercises(exerciseIds)
        check(rows.map { it.exercise.id }.toSet() == exerciseIds.toSet()) {
            "Selected historical exercise could not be read back."
        }
        val headers = rows.associate { row ->
            require(ExerciseType.entries.any { it.name == row.exerciseTypeName }) {
                "Historical exercise projection contains an unknown exercise type."
            }
            val unit = requireNotNull(WeightUnit.entries.firstOrNull { it.name == row.weightUnitName }) {
                "Historical session header contains an unknown weight unit."
            }
            require(row.statusName == SessionStatus.COMPLETED.name && row.startedAtTimestamp > 0 &&
                row.completedAtTimestamp != null && row.completedAtTimestamp >= row.startedAtTimestamp) {
                "Historical session header has invalid completed chronology."
            }
            row.exercise.id to HistoricalSessionHeader(
                row.exercise.sessionId, row.sessionName, row.startedAtTimestamp,
                row.completedAtTimestamp, unit
            )
        }
        val counts = dao.selectHistoricalSetCounts(exerciseIds)
        require(counts.all { it.setCount <= LocalDataArchiveLimits.MAX_SETS_PER_EXERCISE } &&
            counts.sumOf { it.setCount } <= LocalDataArchiveLimits.MAX_TOTAL_SETS) {
            "Historical performance sets exceed their representation bound."
        }
        val sets = dao.selectEligibleHistoricalSets(exerciseIds).groupBy { it.workoutExerciseId }
        return rows.associate { row ->
            val eligible = sets[row.exercise.id].orEmpty()
            check(eligible.isNotEmpty()) { "Selected historical performance has no eligible sets." }
            row.exercise.id to HistoricalExercisePerformance(
                headers.getValue(row.exercise.id),
                WorkoutExerciseWithSets(row.exercise, eligible).toWorkoutExercise()
            )
        }
    }

    override fun observeHistory(page: Int): Flow<WorkoutHistoryPage> {
        require(page >= 0) { "History page must be nonnegative." }
        return observeRead("workout_sessions") {
            val dao = database.workoutSessionDao()
            val sessions = dao.selectHistoryPage(PAGE_SIZE + 1, page.toLong() * PAGE_SIZE)
            WorkoutHistoryPage(
                sessions = sessions.take(PAGE_SIZE),
                hasOlder = sessions.size > PAGE_SIZE
            )
        }
    }

    private fun <T> observeRead(vararg tables: String, read: suspend () -> T): Flow<T> =
        database.invalidationTracker.createFlow(*tables).map {
            localDataWriteGate.withLock { database.withTransaction { read() } }
        }.flowOn(Dispatchers.IO)

    private companion object {
        const val PAGE_SIZE = 20
    }
}
