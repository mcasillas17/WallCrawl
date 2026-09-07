package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
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
 *
 * The checks are computed once, as [ProgramViolation] values, and exposed two ways:
 * [structuralViolations] reports all of them, which is what whole-program validation needs
 * in order to explain a rejection completely, and [validate] throws on the first, which is
 * the older single-reason contract its existing callers still use.
 */
class GeneratedWorkoutValidator(
    private val exerciseCatalog: ExerciseCatalog
) {

    /**
     * Every structural problem in [workout], in report order, or an empty list.
     *
     * Unlike [validate] this does not stop at the first problem: a caller assembling a
     * complete rejection has to be able to show every reason at once.
     */
    suspend fun structuralViolations(
        workout: GeneratedWorkout,
        allowedExerciseIds: Set<String>?
    ): List<ProgramViolation> {
        val violations = mutableListOf<ProgramViolation>()

        // The title is a structured spec rather than a string, so there is no blank name to
        // guard against: a planner cannot produce one without naming a split and an emphasis.
        if (
            workout.estimatedDurationMinutes !in
            WorkoutDurationEstimator.MIN_MINUTES..WorkoutDurationEstimator.MAX_MINUTES
        ) {
            violations += ProgramViolation(
                code = ProgramViolationCode.DURATION_OUT_OF_BOUNDS,
                detail = workout.estimatedDurationMinutes.toString()
            )
        }

        if (workout.exercises.isEmpty()) {
            violations += ProgramViolation(ProgramViolationCode.EMPTY_RECOMMENDATION)
            return violations
        }

        workout.exercises.forEachIndexed { index, exercise ->
            violations += exerciseViolations(index, exercise, allowedExerciseIds)
        }
        return violations
    }

    /**
     * Validates [workout] against the catalog and optional [allowedExerciseIds].
     * @throws WorkoutValidationException if any constraint fails.
     */
    suspend fun validate(
        workout: GeneratedWorkout,
        allowedExerciseIds: Set<String>? = null
    ): GeneratedWorkout {
        structuralViolations(workout, allowedExerciseIds).firstOrNull()?.let { violation ->
            throw WorkoutValidationException(violation.legacyMessage())
        }
        return workout
    }

    private suspend fun exerciseViolations(
        index: Int,
        exercise: GeneratedExercise,
        allowedExerciseIds: Set<String>?
    ): List<ProgramViolation> {
        if (exercise.exerciseId.isBlank()) {
            return listOf(
                ProgramViolation(
                    code = ProgramViolationCode.BLANK_EXERCISE_ID,
                    orderIndex = index
                )
            )
        }

        // Verify exercise exists in the official catalog.
        val catalogExercise = exerciseCatalog.getExerciseById(exercise.exerciseId)
            ?: return listOf(
                ProgramViolation(
                    code = ProgramViolationCode.UNKNOWN_EXERCISE_ID,
                    exerciseId = exercise.exerciseId,
                    orderIndex = index
                )
            )

        val violations = mutableListOf<ProgramViolation>()
        // If a candidate filter was enforced, verify it was in the allowed list.
        if (allowedExerciseIds != null && exercise.exerciseId !in allowedExerciseIds) {
            violations += ProgramViolation(
                code = ProgramViolationCode.NOT_IN_CANDIDATE_SET,
                exerciseId = exercise.exerciseId,
                orderIndex = index
            )
        }

        if (exercise.prescription.exerciseType != catalogExercise.type) {
            violations += ProgramViolation(
                code = ProgramViolationCode.PRESCRIPTION_TYPE_MISMATCH,
                exerciseId = exercise.exerciseId,
                orderIndex = index,
                detail = "${exercise.prescription.exerciseType.name}!=${catalogExercise.type.name}"
            )
        }
        return violations
    }

    /**
     * The message [validate] has always thrown for this problem.
     *
     * It is written for logs and tests rather than for a reader, which is exactly why the
     * screen maps typed codes instead of rendering these strings.
     */
    private fun ProgramViolation.legacyMessage(): String = when (code) {
        ProgramViolationCode.DURATION_OUT_OF_BOUNDS ->
            "Invalid workout duration ($detail minutes)."

        ProgramViolationCode.EMPTY_RECOMMENDATION ->
            "Generated workout has no exercises."

        ProgramViolationCode.BLANK_EXERCISE_ID ->
            "Exercise at index $orderIndex has blank exerciseId."

        ProgramViolationCode.UNKNOWN_EXERCISE_ID ->
            "Hallucinated or invalid exercise ID: '$exerciseId' at index $orderIndex " +
                "does not exist in catalog."

        ProgramViolationCode.NOT_IN_CANDIDATE_SET ->
            "Exercise '$exerciseId' was not in the allowed candidate list."

        ProgramViolationCode.PRESCRIPTION_TYPE_MISMATCH ->
            "Prescription type does not match catalog type ($detail) for " +
                "exercise '$exerciseId'."

        else -> "Generated workout violated $code."
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

    /** The enabled reviewed eligibility gate rejected every automatic candidate. */
    REVIEWED_ELIGIBILITY_NO_CANDIDATES,

    /** Candidates exist, but all of them are cardio or mobility work. */
    NO_STRENGTH_CANDIDATES,

    /** Strength candidates exist, but none of them train any split. */
    NO_CANDIDATES_FOR_ANY_SPLIT,

    /** A generated workout broke the catalog or prescription contract. */
    INVALID_GENERATED_WORKOUT
}

class WorkoutValidationException(
    message: String,
    val failure: WorkoutPlanningFailure = WorkoutPlanningFailure.INVALID_GENERATED_WORKOUT,
    val automaticEligibilityFailure: AutomaticEligibilityFailure? = null
) : IllegalArgumentException(message)
