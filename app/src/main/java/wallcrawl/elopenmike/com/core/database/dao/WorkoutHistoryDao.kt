package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits
import wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.progress.PersonalRecordBaseline
import wallcrawl.elopenmike.com.core.model.WorkoutHistoryEntry

private const val MAX_RECORD_BASELINES = LocalDataArchiveLimits.MAX_EXERCISES_PER_SESSION * 2

// Shared by candidate selection and the returned comparison-set projection. Session/set aliases
// are s/e/w in both queries. Legacy null set times remain unknown, never fabricated or excluded.
private const val ELIGIBLE_PREVIOUS_SET = """
    w.exerciseType = e.exerciseType AND w.isCompleted = 1
    AND w.type IN ('NORMAL', 'DROPSET', 'MYOREP', 'FAILURE')
    AND w.stopReason IS NULL AND w.stoppedAtTimestamp IS NULL
    AND (w.completedAtTimestamp IS NULL
        OR w.completedAtTimestamp BETWEEN s.startedAtTimestamp AND s.completedAtTimestamp)
    AND (
        (e.exerciseType IN ('WEIGHT_REPS', 'BODYWEIGHT_REPS', 'ASSISTED_BODYWEIGHT')
            AND w.completedReps BETWEEN 1 AND ${LocalDataArchiveLimits.MAX_REPETITIONS}
            AND w.completedDurationSeconds IS NULL AND w.completedDistanceMeters IS NULL
            AND (
                (e.exerciseType = 'WEIGHT_REPS'
                    AND w.completedWeight > 0 AND w.completedWeight <= ${LocalDataArchiveLimits.MAX_WEIGHT}
                    AND w.completedAssistanceWeight IS NULL)
                OR (e.exerciseType = 'BODYWEIGHT_REPS'
                    AND w.completedWeight IS NULL AND w.completedAssistanceWeight IS NULL)
                OR (e.exerciseType = 'ASSISTED_BODYWEIGHT'
                    AND w.completedWeight IS NULL
                    AND (w.completedAssistanceWeight IS NULL
                        OR w.completedAssistanceWeight BETWEEN 0 AND ${LocalDataArchiveLimits.MAX_WEIGHT}))
            ))
        OR (e.exerciseType = 'DURATION'
            AND w.completedReps IS NULL AND w.completedWeight IS NULL
            AND w.completedAssistanceWeight IS NULL AND w.completedDistanceMeters IS NULL
            AND w.completedDurationSeconds BETWEEN 1 AND ${LocalDataArchiveLimits.MAX_DURATION_SECONDS})
        OR (e.exerciseType = 'DISTANCE_DURATION'
            AND w.completedReps IS NULL AND w.completedWeight IS NULL AND w.completedAssistanceWeight IS NULL
            AND ((e.targetDurationSeconds IS NULL AND w.completedDurationSeconds IS NULL)
                OR (e.targetDurationSeconds IS NOT NULL
                    AND w.completedDurationSeconds BETWEEN 1 AND ${LocalDataArchiveLimits.MAX_DURATION_SECONDS}))
            AND ((e.targetDistanceMeters IS NULL AND w.completedDistanceMeters IS NULL)
                OR (e.targetDistanceMeters IS NOT NULL
                    AND w.completedDistanceMeters > 0
                    AND w.completedDistanceMeters <= ${LocalDataArchiveLimits.MAX_DISTANCE_METERS})))
    )
"""

private const val PRIOR_SESSION_CHRONOLOGY = """
    viewed.status = 'COMPLETED' AND viewed.startedAtTimestamp > 0
    AND viewed.completedAtTimestamp >= viewed.startedAtTimestamp
    AND s.id != viewed.id AND s.status = 'COMPLETED' AND s.startedAtTimestamp > 0
    AND s.completedAtTimestamp >= s.startedAtTimestamp
    AND s.completedAtTimestamp < viewed.startedAtTimestamp
"""

// Select an unknown relevant type too, so strict projection decoding reports corruption rather
// than silently relabelling it via the legacy converter or skipping it as incompatible evidence.
private const val ELIGIBLE_PREVIOUS_EXERCISE = """
    e.exerciseId = v.exerciseId AND (
        e.exerciseType NOT IN ('WEIGHT_REPS', 'BODYWEIGHT_REPS', 'ASSISTED_BODYWEIGHT', 'DURATION', 'DISTANCE_DURATION')
        OR (e.exerciseType = v.exerciseType
            AND (v.exerciseType != 'DISTANCE_DURATION' OR (
                (e.targetDurationSeconds IS NULL) = (v.targetDurationSeconds IS NULL)
                AND (e.targetDistanceMeters IS NULL) = (v.targetDistanceMeters IS NULL)))
            AND EXISTS (SELECT 1 FROM workout_sets w
                WHERE w.workoutExerciseId = e.id AND ($ELIGIBLE_PREVIOUS_SET)))
    )
"""

/** Bounded read projections; no cache, catalog, profile, or policy is involved. */
@Dao
interface WorkoutHistoryDao {
    @Query("""
        SELECT id, name, completedAtTimestamp, actualDurationMinutes
        FROM workout_sessions WHERE status = 'COMPLETED'
        ORDER BY COALESCE(completedAtTimestamp, startedAtTimestamp) DESC, startedAtTimestamp DESC, id ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun selectHistoryPage(limit: Int, offset: Long): List<WorkoutHistoryEntry>

    @Query("""
        SELECT s.id AS sessionId,
            (SELECT COUNT(*) FROM workout_exercises e WHERE e.sessionId = s.id) AS exerciseCount,
            COALESCE((SELECT MAX(setCount) FROM (
                SELECT COUNT(*) AS setCount FROM workout_sets w
                JOIN workout_exercises e ON e.id = w.workoutExerciseId
                WHERE e.sessionId = s.id GROUP BY e.id
            )), 0) AS maximumSetCount,
            (SELECT COUNT(*) FROM workout_sets w JOIN workout_exercises e ON e.id = w.workoutExerciseId
                WHERE e.sessionId = s.id) AS totalSetCount,
            (s.weightUnit NOT IN ('LBS', 'KG')
                OR s.status NOT IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')
                OR s.origin NOT IN ('PLANNER', 'CUSTOM_TEMPLATE')
                OR EXISTS (SELECT 1 FROM workout_exercises e WHERE e.sessionId = s.id
                    AND (e.exerciseType NOT IN ('WEIGHT_REPS', 'BODYWEIGHT_REPS', 'ASSISTED_BODYWEIGHT',
                        'DURATION', 'DISTANCE_DURATION')
                        OR (e.targetRepMin IS NULL) != (e.targetRepMax IS NULL)))
                OR EXISTS (SELECT 1 FROM workout_sets w JOIN workout_exercises e ON e.id = w.workoutExerciseId
                    WHERE e.sessionId = s.id AND (
                        w.exerciseType NOT IN ('WEIGHT_REPS', 'BODYWEIGHT_REPS', 'ASSISTED_BODYWEIGHT',
                            'DURATION', 'DISTANCE_DURATION')
                        OR w.type NOT IN ('WARMUP', 'NORMAL', 'DROPSET', 'MYOREP', 'FAILURE'))))
                AS invalidMetadata
        FROM workout_sessions s WHERE s.id IN (:sessionIds)
    """)
    suspend fun selectHistorySizes(sessionIds: List<String>): List<HistorySessionSize>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id IN (:sessionIds)")
    suspend fun selectHistorySessions(sessionIds: List<String>): List<WorkoutSessionWithExercisesAndSets>

    /** Check child cardinality before Room loads relations, rather than truncate after loading. */
    @Transaction
    suspend fun getBoundedHistorySessions(sessionIds: List<String>): List<WorkoutSessionWithExercisesAndSets> {
        require(sessionIds.size <= LocalDataArchiveLimits.MAX_EXERCISES_PER_SESSION) {
            "History session batch exceeds its representation bound."
        }
        if (sessionIds.isEmpty()) return emptyList()
        val sizes = selectHistorySizes(sessionIds)
        sizes.forEach {
            require(it.exerciseCount <= LocalDataArchiveLimits.MAX_EXERCISES_PER_SESSION &&
                it.maximumSetCount <= LocalDataArchiveLimits.MAX_SETS_PER_EXERCISE) {
                "Stored workout history exceeds its representation bound."
            }
            require(!it.invalidMetadata) { "Stored workout history contains invalid typed metadata." }
        }
        require(sizes.sumOf { it.totalSetCount } <= LocalDataArchiveLimits.MAX_TOTAL_SETS) {
            "Stored workout history batch exceeds its set representation bound."
        }
        return selectHistorySessions(sessionIds)
    }

    /**
     * SQL scans all relevant prior evidence but returns at most one row per viewed ID/type/unit.
     * The finite-load and completed-rep predicates mirror the calculator's record semantics;
     * warm-ups deliberately remain eligible, unlike ordinary previous-performance comparisons.
     */
    @Query("""
        SELECT e.exerciseId, e.exerciseType, s.weightUnit AS weightUnitName,
            MAX(CASE WHEN w.completedWeight > 0 THEN w.completedWeight END) AS maxWeight,
            MAX(w.completedReps) AS maxReps
        FROM workout_sessions s
        JOIN workout_exercises e ON e.sessionId = s.id
        JOIN workout_sets w ON w.workoutExerciseId = e.id
        JOIN workout_sessions viewed ON viewed.id = :sessionId
        WHERE viewed.status = 'COMPLETED' AND viewed.startedAtTimestamp > 0
          AND viewed.completedAtTimestamp >= viewed.startedAtTimestamp
          AND s.id != viewed.id AND s.status = 'COMPLETED' AND s.startedAtTimestamp > 0
          AND s.completedAtTimestamp >= s.startedAtTimestamp
          AND s.completedAtTimestamp < viewed.startedAtTimestamp
          AND EXISTS (SELECT 1 FROM workout_exercises v
              WHERE v.sessionId = viewed.id AND v.exerciseId = e.exerciseId AND v.exerciseType = e.exerciseType)
          AND e.exerciseType IN ('WEIGHT_REPS', 'BODYWEIGHT_REPS', 'ASSISTED_BODYWEIGHT')
          AND w.exerciseType = e.exerciseType AND w.isCompleted = 1
          AND w.stopReason IS NULL AND w.stoppedAtTimestamp IS NULL AND w.completedReps > 0
          AND (w.completedWeight IS NULL OR w.completedWeight BETWEEN 0 AND 1.7976931348623157e308)
        GROUP BY e.exerciseId, e.exerciseType, s.weightUnit
        LIMIT ${MAX_RECORD_BASELINES + 1}
    """)
    suspend fun selectPersonalRecordBaselines(sessionId: String): List<PersonalRecordBaselineRow>

    suspend fun getPersonalRecordBaselines(sessionId: String): List<PersonalRecordBaseline> {
        val rows = selectPersonalRecordBaselines(sessionId)
        require(rows.size <= MAX_RECORD_BASELINES) { "Stored record baseline exceeds its representation bound." }
        return rows.map { row ->
            PersonalRecordBaseline(
                row.exerciseId, row.exerciseType,
                requireNotNull(WeightUnit.entries.firstOrNull { it.name == row.weightUnitName }) {
                    "Stored record baseline contains an unknown weight unit."
                },
                row.maxWeight, row.maxReps
            )
        }
    }

    /**
     * One bounded seek per viewed exercise inside SQL, never a global history sample.
     * EXISTS ignores open/stopped/warm-up-only observations without loading their sets.
     * Distance/time's three persisted shapes are compared independently.
     */
    @Query("""
        SELECT v.id AS viewedExerciseId, prior.id AS previousExerciseId, prior.sessionId AS previousSessionId
        FROM workout_exercises v
        JOIN workout_sessions viewed ON viewed.id = v.sessionId
        JOIN workout_exercises prior ON prior.id = (
            SELECT e.id FROM workout_exercises e
            JOIN workout_sessions s ON s.id = e.sessionId
            WHERE ($PRIOR_SESSION_CHRONOLOGY) AND ($ELIGIBLE_PREVIOUS_EXERCISE)
            ORDER BY s.completedAtTimestamp DESC, s.startedAtTimestamp DESC, s.id ASC, e.orderIndex ASC, e.id ASC
            LIMIT 1
        )
        WHERE viewed.id = :sessionId AND viewed.status = 'COMPLETED' AND viewed.startedAtTimestamp > 0
          AND viewed.completedAtTimestamp >= viewed.startedAtTimestamp
        ORDER BY v.orderIndex ASC, v.id ASC
    """)
    suspend fun selectPreviousPerformanceIds(sessionId: String): List<PreviousPerformanceIds>

    /**
     * Only decoded source/exercise pairs are considered. U+001F cannot occur in validated
     * provenance IDs. Duplicate viewed instances keep separate keys, without duplicate graph reads.
     */
    @Query("""
        SELECT v.id AS viewedExerciseId, prior.id AS previousExerciseId, s.id AS previousSessionId
        FROM workout_exercises v
        JOIN workout_sessions viewed ON viewed.id = v.sessionId
        JOIN workout_sessions s ON s.id IN (:sourceIds)
        JOIN workout_exercises prior ON prior.id = (
            SELECT e.id FROM workout_exercises e
            WHERE e.sessionId = s.id AND ($ELIGIBLE_PREVIOUS_EXERCISE)
            ORDER BY e.orderIndex ASC, e.id ASC LIMIT 1
        )
        WHERE viewed.id = :sessionId AND ($PRIOR_SESSION_CHRONOLOGY)
          AND (s.id || char(31) || v.exerciseId) IN (:sourceExercisePairs)
        ORDER BY v.orderIndex ASC, v.id ASC, s.id ASC
    """)
    suspend fun selectCitedPerformanceIds(
        sessionId: String, sourceIds: List<String>, sourceExercisePairs: List<String>
    ): List<PreviousPerformanceIds>

    /** No session relations: only selected exercise rows and the header fields actually displayed. */
    @Query("""
        SELECT e.*, e.exerciseType AS exerciseTypeName,
            s.name AS sessionName, s.startedAtTimestamp, s.completedAtTimestamp,
            s.weightUnit AS weightUnitName, s.status AS statusName
        FROM workout_exercises e JOIN workout_sessions s ON s.id = e.sessionId
        WHERE e.id IN (:exerciseIds)
    """)
    suspend fun selectHistoricalExercises(exerciseIds: List<String>): List<HistoricalExerciseRow>

    @Query("""
        SELECT w.workoutExerciseId, COUNT(*) AS setCount FROM workout_sets w
        JOIN workout_exercises e ON e.id = w.workoutExerciseId
        JOIN workout_sessions s ON s.id = e.sessionId
        WHERE e.id IN (:exerciseIds) AND ($ELIGIBLE_PREVIOUS_SET)
        GROUP BY w.workoutExerciseId
    """)
    suspend fun selectHistoricalSetCounts(exerciseIds: List<String>): List<HistoricalSetCount>

    /** Called after bounds on these eligible sets, not on unrelated source graphs, are checked. */
    @Query("""
        SELECT w.* FROM workout_sets w
        JOIN workout_exercises e ON e.id = w.workoutExerciseId
        JOIN workout_sessions s ON s.id = e.sessionId
        WHERE e.id IN (:exerciseIds) AND ($ELIGIBLE_PREVIOUS_SET)
    """)
    suspend fun selectEligibleHistoricalSets(exerciseIds: List<String>): List<WorkoutSetEntity>
}

data class HistoricalExerciseRow(
    @Embedded val exercise: WorkoutExerciseEntity,
    val exerciseTypeName: String,
    val sessionName: String,
    val startedAtTimestamp: Long,
    val completedAtTimestamp: Long?,
    val weightUnitName: String,
    val statusName: String
)

data class HistoricalSetCount(val workoutExerciseId: String, val setCount: Long)

/** Raw unit text bypasses the legacy converter's LBS fallback; the history boundary decodes it. */
data class PersonalRecordBaselineRow(
    val exerciseId: String,
    val exerciseType: ExerciseType,
    val weightUnitName: String,
    val maxWeight: Double?,
    val maxReps: Int?
)

data class HistorySessionSize(
    val sessionId: String,
    val exerciseCount: Long,
    val maximumSetCount: Long,
    val totalSetCount: Long,
    val invalidMetadata: Boolean
)

data class PreviousPerformanceIds(
    val viewedExerciseId: String,
    val previousExerciseId: String,
    val previousSessionId: String
)
