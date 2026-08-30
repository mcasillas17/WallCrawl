package wallcrawl.elopenmike.com.core.model

import java.util.UUID

/**
 * High-level generated workout recommendation.
 */
data class GeneratedWorkout(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val focusMuscles: List<String>,
    val estimatedDurationMinutes: Int,
    val exercises: List<PlannedExercise>,
    val rationale: String = ""
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
    val origin: WorkoutOrigin = WorkoutOrigin.PLANNER,
    val sourceTemplateId: String? = null,
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
    val prescription: ExercisePrescription,
    val notes: String = "",
    val sets: List<WorkoutSet> = emptyList()
) {
    /** Compatibility constructor for persisted repetition-only sessions before schema 4. */
    constructor(
        id: String = UUID.randomUUID().toString(),
        sessionId: String,
        exerciseId: String,
        orderIndex: Int,
        targetSets: Int,
        targetRepMin: Int,
        targetRepMax: Int,
        targetWeight: Double? = null,
        notes: String = "",
        sets: List<WorkoutSet> = emptyList()
    ) : this(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        prescription = ExercisePrescription(
            exerciseType = ExerciseType.WEIGHT_REPS,
            targetSets = targetSets,
            repRange = RepRange(targetRepMin, targetRepMax),
            targetWeight = targetWeight
        ),
        notes = notes,
        sets = sets
    )

    val totalVolume: Double
        get() = sets.filter { it.isCompleted }.sumOf { (it.completedWeight ?: 0.0) * (it.completedReps ?: 0) }

    val targetSets: Int get() = prescription.targetSets
    val targetRepMin: Int get() = prescription.repRange?.min ?: 0
    val targetRepMax: Int get() = prescription.repRange?.max ?: 0
    val targetWeight: Double? get() = prescription.targetWeight
    val targetRepRange: RepRange? get() = prescription.repRange
}

/**
 * An individual set within a [WorkoutExercise].
 */
data class WorkoutSet(
    val id: String = UUID.randomUUID().toString(),
    val workoutExerciseId: String,
    val setNumber: Int,
    val exerciseType: ExerciseType = ExerciseType.WEIGHT_REPS,
    val targetReps: Int? = null,
    val completedReps: Int? = null,
    val targetWeight: Double? = null,
    val completedWeight: Double? = null,
    val targetAssistanceWeight: Double? = null,
    val completedAssistanceWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val completedDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val completedDistanceMeters: Double? = null,
    val isCompleted: Boolean = false,
    val rpe: Float? = null,
    val rir: Int? = null,
    val feltManageable: Boolean? = null,
    val completedAtTimestamp: Long? = null,
    val stoppedAtTimestamp: Long? = null,
    val stopReason: SetStopReason? = null,
    val type: SetType = SetType.NORMAL
) {
    /** True once the user resolved this set, either by completing it or by stopping it. */
    val isResolved: Boolean get() = isCompleted || stopReason != null
}

enum class SetType {
    WARMUP,
    NORMAL,
    DROPSET,
    MYOREP,
    FAILURE
}
