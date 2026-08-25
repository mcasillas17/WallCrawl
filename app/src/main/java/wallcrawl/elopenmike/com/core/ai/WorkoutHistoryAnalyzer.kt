package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

/**
 * Converts completed workout sessions into the compact performance summaries used by
 * workout planners. The analyzer is pure so history policy stays independent of Room.
 */
class WorkoutHistoryAnalyzer {

    fun exerciseHistory(
        sessions: List<WorkoutSession>
    ): Map<String, ExercisePerformanceHistory> {
        val completedSessions = sessions
            .filter { it.status == SessionStatus.COMPLETED && it.completedAtTimestamp != null }
            .sortedByDescending { it.completedAtTimestamp }

        val performancesByExerciseId = completedSessions
            .flatMap { session ->
                session.exercises.map { exercise ->
                    ExercisePerformance(
                        exerciseId = exercise.exerciseId,
                        completedAtTimestamp = session.completedAtTimestamp ?: Long.MIN_VALUE,
                        completedSets = exercise.sets.completedPerformanceSets()
                    )
                }
            }
            .filter { it.completedSets.isNotEmpty() }
            .groupBy { it.exerciseId }

        return performancesByExerciseId.mapValues { (exerciseId, performances) ->
            val latestPerformance = performances.maxBy { it.completedAtTimestamp }
            val latestSet = latestPerformance.completedSets.maxBy { it.setNumber }
            val bestEstimatedOneRepMax = performances
                .asSequence()
                .flatMap { it.completedSets.asSequence() }
                .mapNotNull(::estimatedOneRepMax)
                .maxOrNull()

            ExercisePerformanceHistory(
                exerciseId = exerciseId,
                lastWeight = latestSet.completedWeight,
                lastReps = latestSet.completedReps,
                bestEstimated1RM = bestEstimatedOneRepMax,
                recentSets = latestPerformance.completedSets.sortedBy { it.setNumber }
            )
        }
    }

    fun recentlyTrainedMuscles(
        sessions: List<WorkoutSession>,
        nowTimestamp: Long,
        lookbackHours: Int = DEFAULT_RECOVERY_LOOKBACK_HOURS
    ): List<String> {
        require(lookbackHours >= 0) { "lookbackHours must not be negative." }
        val earliestTimestamp = nowTimestamp - (lookbackHours * HOUR_MILLIS)

        return sessions
            .asSequence()
            .filter { session ->
                val completedAt = session.completedAtTimestamp
                session.status == SessionStatus.COMPLETED &&
                    completedAt != null &&
                    completedAt in earliestTimestamp..nowTimestamp
            }
            .sortedByDescending { it.completedAtTimestamp }
            .flatMap { it.focusMuscles.asSequence() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    fun latestCompletedExercisePerformance(
        sessions: List<WorkoutSession>,
        exerciseId: String
    ): CompletedExercisePerformance? {
        require(exerciseId.isNotBlank()) { "exerciseId must not be blank." }

        return sessions
            .asSequence()
            .filter { it.status == SessionStatus.COMPLETED && it.completedAtTimestamp != null }
            .sortedByDescending { it.completedAtTimestamp }
            .mapNotNull { session ->
                val completedSets = session.exercises
                    .firstOrNull { it.exerciseId == exerciseId }
                    ?.sets
                    ?.completedPerformanceSets()
                    ?.sortedBy { it.setNumber }
                    .orEmpty()
                if (completedSets.isEmpty()) {
                    null
                } else {
                    CompletedExercisePerformance(
                        sessionCompletedAtTimestamp = requireNotNull(session.completedAtTimestamp),
                        sets = completedSets
                    )
                }
            }
            .firstOrNull()
    }

    private fun List<WorkoutSet>.completedPerformanceSets(): List<WorkoutSet> =
        filter { set ->
            set.isCompleted &&
                set.completedReps != null &&
                set.completedReps > 0 &&
                (set.completedWeight == null ||
                    (set.completedWeight.isFinite() && set.completedWeight >= 0.0))
        }

    private fun estimatedOneRepMax(set: WorkoutSet): Double? {
        val weight = set.completedWeight ?: return null
        val reps = set.completedReps ?: return null
        if (!weight.isFinite() || weight <= 0.0 || reps <= 0) return null
        return weight * (1.0 + reps / 30.0)
    }

    private data class ExercisePerformance(
        val exerciseId: String,
        val completedAtTimestamp: Long,
        val completedSets: List<WorkoutSet>
    )

    private companion object {
        const val DEFAULT_RECOVERY_LOOKBACK_HOURS = 72
        const val HOUR_MILLIS = 60 * 60 * 1_000L
    }
}

data class CompletedExercisePerformance(
    val sessionCompletedAtTimestamp: Long,
    val sets: List<WorkoutSet>
)
