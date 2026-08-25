package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout

/**
 * Validates that an AI-generated workout contains only valid, existing exercises
 * from the catalog and respects schema constraints.
 *
 * This is a critical barrier against LLM hallucination: if the model invents
 * a non-existent exercise ID or violates set/rep boundaries, this validator
 * rejects the payload immediately.
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

        // Validate set/rep ranges
        if (exercise.targetSets !in MIN_TARGET_SETS..MAX_TARGET_SETS) {
            throw WorkoutValidationException(
                "Invalid target sets (${exercise.targetSets}) for exercise '${exercise.exerciseId}'."
            )
        }

        if (
            exercise.repMin !in MIN_REPS..MAX_REPS ||
            exercise.repMax !in exercise.repMin..MAX_REPS
        ) {
            throw WorkoutValidationException(
                "Invalid rep range (${exercise.repMin}–${exercise.repMax}) for exercise '${exercise.exerciseId}'."
            )
        }

        val targetWeight = exercise.targetWeight
        if (targetWeight != null && (!targetWeight.isFinite() || targetWeight < 0.0)) {
            throw WorkoutValidationException(
                "Invalid target weight for exercise '${exercise.exerciseId}'."
            )
        }

        if (exercise.restSeconds !in MIN_REST_SECONDS..MAX_REST_SECONDS) {
            throw WorkoutValidationException(
                "Invalid rest period (${exercise.restSeconds} seconds) for exercise '${exercise.exerciseId}'."
            )
        }
    }

    private companion object {
        const val MIN_DURATION_MINUTES = 1
        const val MAX_DURATION_MINUTES = 240
        const val MIN_TARGET_SETS = 1
        const val MAX_TARGET_SETS = 20
        const val MIN_REPS = 1
        const val MAX_REPS = 1_000
        const val MIN_REST_SECONDS = 0
        const val MAX_REST_SECONDS = 1_800
    }
}

class WorkoutValidationException(message: String) : IllegalArgumentException(message)
