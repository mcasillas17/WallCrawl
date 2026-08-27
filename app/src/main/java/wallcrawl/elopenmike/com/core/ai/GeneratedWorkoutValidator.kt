package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout

/**
 * Validates that a generated workout contains only valid, existing exercises from the
 * catalog, stayed inside the allowed candidate set, and uses the catalog type.
 * Structural prescription constraints are enforced when [GeneratedExercise] is constructed.
 *
 * Today's planner is rule-based and cannot invent an exercise, so this mostly guards
 * against programming mistakes. It is the barrier a generative planner would need, and
 * runs on every planner's output so that guarantee holds the day one is added.
 */
class GeneratedWorkoutValidator(
    private val exerciseCatalog: ExerciseCatalog
) {

    /**
     * Validates [workout] against the catalog and optional [allowedExerciseIds].
     * @throws WorkoutValidationException if any constraint fails.
     */
    suspend fun validate(
        workout: GeneratedWorkout,
        allowedExerciseIds: Set<String>? = null
    ): GeneratedWorkout {
        if (workout.name.isBlank()) {
            throw WorkoutValidationException("Generated workout has a blank name.")
        }

        if (workout.estimatedDurationMinutes !in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES) {
            throw WorkoutValidationException(
                "Invalid workout duration (${workout.estimatedDurationMinutes} minutes)."
            )
        }

        if (workout.exercises.isEmpty()) {
            throw WorkoutValidationException("Generated workout has no exercises.")
        }

        workout.exercises.forEachIndexed { index, exercise ->
            validateExercise(index, exercise, allowedExerciseIds)
        }

        return workout
    }

    private suspend fun validateExercise(
        index: Int,
        exercise: GeneratedExercise,
        allowedExerciseIds: Set<String>?
    ) {
        if (exercise.exerciseId.isBlank()) {
            throw WorkoutValidationException("Exercise at index $index has blank exerciseId.")
        }

        // Verify exercise exists in the official catalog
        val catalogExercise = exerciseCatalog.getExerciseById(exercise.exerciseId)
            ?: throw WorkoutValidationException(
                "Hallucinated or invalid exercise ID: '${exercise.exerciseId}' at index $index does not exist in catalog."
            )

        // If a candidate filter was enforced, verify it was in the allowed list
        if (allowedExerciseIds != null && exercise.exerciseId !in allowedExerciseIds) {
            throw WorkoutValidationException(
                "Exercise '${exercise.exerciseId}' was not in the allowed candidate list."
            )
        }

        if (exercise.prescription.exerciseType != catalogExercise.type) {
            throw WorkoutValidationException(
                "Prescription type '${exercise.prescription.exerciseType}' does not match catalog " +
                    "type '${catalogExercise.type}' for exercise '${exercise.exerciseId}'."
            )
        }
    }

    private companion object {
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 240
    }
}

/**
 * Why planning stopped. The UI maps this to copy; a future planner chain maps it to a
 * recovery strategy (repair and retry, fall back to another tier, or surface to the user),
 * which string matching on [WorkoutValidationException.message] could not support.
 */
enum class WorkoutPlanningFailure {
    /** Nothing survived the equipment, exclusion, and recovery filters. */
    NO_CANDIDATES,

    /** Candidates exist, but none of them train any split this profile can be given. */
    NO_CANDIDATES_FOR_ANY_SPLIT,

    /** A generated workout broke the catalog or prescription contract. */
    INVALID_GENERATED_WORKOUT
}

class WorkoutValidationException(
    message: String,
    val failure: WorkoutPlanningFailure = WorkoutPlanningFailure.INVALID_GENERATED_WORKOUT
) : IllegalArgumentException(message)
