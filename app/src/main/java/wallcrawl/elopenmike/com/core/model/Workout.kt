package wallcrawl.elopenmike.com.core.model

import java.util.UUID

/**
 * High-level AI-generated workout recommendation.
 */
data class GeneratedWorkout(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val focusMuscles: List<String>,
    val estimatedDurationMinutes: Int,
    val exercises: List<GeneratedExercise>,
    val rationale: String = ""
)

/**
 * Individual exercise specification inside a [GeneratedWorkout].
 * The model MUST only choose [exerciseId] from approved catalog candidate IDs.
 */
data class GeneratedExercise(
    val exerciseId: String,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val targetWeight: Double? = null,
    val restSeconds: Int = 90,
    val notes: String = ""
)

/**
 * A persistent workout session representing either an active or completed workout.
 */
data class WorkoutSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startedAtTimestamp: Long = System.currentTimeMillis(),
    val completedAtTimestamp: Long? = null,
    val targetDurationMinutes: Int = 50,
    val actualDurationMinutes: Int = 0,
    val weightUnit: WeightUnit = WeightUnit.LBS,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val focusMuscles: List<String> = emptyList(),
    val exercises: List<WorkoutExercise> = emptyList(),
    val notes: String = ""
) {
    val totalVolume: Double
        get() = exercises.sumOf { it.totalVolume }

    val completedSetsCount: Int
        get() = exercises.sumOf { ex -> ex.sets.count { it.isCompleted } }

    val totalSetsCount: Int
        get() = exercises.sumOf { it.sets.size }
}

enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

/**
 * An exercise instance within an active or completed [WorkoutSession].
 */
data class WorkoutExercise(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int,
    val targetRepMin: Int,
    val targetRepMax: Int,
    val targetWeight: Double? = null,
    val notes: String = "",
    val sets: List<WorkoutSet> = emptyList()
) {
    val totalVolume: Double
        get() = sets.filter { it.isCompleted }.sumOf { (it.completedWeight ?: 0.0) * (it.completedReps ?: 0) }

    val targetRepRange: RepRange
        get() = RepRange(targetRepMin, targetRepMax)
}

/**
 * An individual set within a [WorkoutExercise].
 */
data class WorkoutSet(
    val id: String = UUID.randomUUID().toString(),
    val workoutExerciseId: String,
    val setNumber: Int,
    val targetReps: Int,
    val completedReps: Int? = null,
    val targetWeight: Double? = null,
    val completedWeight: Double? = null,
    val isCompleted: Boolean = false,
    val rpe: Float? = null,
    val rir: Int? = null,
    val type: SetType = SetType.NORMAL
)

enum class SetType {
    WARMUP,
    NORMAL,
    DROPSET,
    MYOREP,
    FAILURE
}
