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
        if (exercise.targetSets <= 0) {
            throw WorkoutValidationException(
                "Invalid target sets (${exercise.targetSets}) for exercise '${exercise.exerciseId}'."
            )
        }

        if (exercise.repMin <= 0 || exercise.repMax < exercise.repMin) {
            throw WorkoutValidationException(
                "Invalid rep range (${exercise.repMin}–${exercise.repMax}) for exercise '${exercise.exerciseId}'."
            )
        }
    }
}

class WorkoutValidationException(message: String) : IllegalArgumentException(message)
