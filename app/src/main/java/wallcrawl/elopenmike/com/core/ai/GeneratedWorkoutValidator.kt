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
 * The checks are reported as [ProgramViolation] values rather than as a first-failure
 * message, because whole-program validation has to be able to explain a rejection
 * completely. `ProgramValidator` is the only caller; it composes these with the rules that
 * need the whole proposal rather than one exercise at a time.
 */
class GeneratedWorkoutValidator(
    private val exerciseCatalog: ExerciseCatalog
) {

    /**
     * Every structural problem in [workout], in report order, or an empty list.
     *
     * It deliberately does not stop at the first problem: a caller assembling a rejection
     * has to be able to show every reason at once.
     *
     * [allowedExerciseIds] is the candidate set the generation context allowed, and
     * membership in it is always checked. There is no "skip the candidate check" mode: a
     * planner's output staying inside the set it was given is the guarantee this exists for.
     */
    suspend fun structuralViolations(
        workout: GeneratedWorkout,
        allowedExerciseIds: Set<String>
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

    private suspend fun exerciseViolations(
        index: Int,
        exercise: GeneratedExercise,
        allowedExerciseIds: Set<String>
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
        // Verify it was in the allowed list.
        if (exercise.exerciseId !in allowedExerciseIds) {
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
}

/**
 * Why planning stopped. The UI maps this to copy; a future planner chain maps it to a
 * recovery strategy (repair and retry, fall back to another tier, or surface to the user),
 * which string matching on [WorkoutValidationException.message] could not support.
 */
enum class WorkoutPlanningFailure {
    /**
     * Nothing survived the legacy candidate filter, which checks exactly two things:
     * equipment availability and the user's explicit exclusions.
     *
     * Declared `TrainingConstraint`s are deliberately absent from that list. They narrow
     * candidates only on the reviewed path, whose empty result is reported as
     * [REVIEWED_ELIGIBILITY_NO_CANDIDATES] instead. There is no recovery filter either;
     * candidate selection reads no timestamps.
     */
    NO_CANDIDATES,

    /** The enabled reviewed eligibility gate rejected every automatic candidate. */
    REVIEWED_ELIGIBILITY_NO_CANDIDATES,

    /** Candidates exist, but all of them are cardio or mobility work. */
    NO_STRENGTH_CANDIDATES,

    /** Strength candidates exist, but none of them train any split. */
    NO_CANDIDATES_FOR_ANY_SPLIT
}

/**
 * A planner could not produce a plan at all.
 *
 * [failure] has no default: every throw site names why, so a new one cannot inherit a
 * reason that happens to be listed first. A workout that was produced but broke a rule is
 * not this — that is a [ProgramViolation], which carries every reason rather than one.
 */
class WorkoutValidationException(
    message: String,
    val failure: WorkoutPlanningFailure,
    val automaticEligibilityFailure: AutomaticEligibilityFailure? = null
) : IllegalArgumentException(message)
