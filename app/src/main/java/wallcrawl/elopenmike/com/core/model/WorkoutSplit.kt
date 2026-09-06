package wallcrawl.elopenmike.com.core.model

/**
 * The training day a generated workout fills.
 *
 * [targetMuscles] is canonical vocabulary the planner matches on, not something a person
 * reads: the split's name on screen is a string resource. Keeping the two apart is what
 * lets the interface be Spanish while selection stays byte-identical in every language.
 */
enum class WorkoutSplit(val targetMuscles: List<String>) {
    PUSH(
        listOf(
            StandardMuscles.CHEST,
            StandardMuscles.SHOULDERS,
            StandardMuscles.TRICEPS
        )
    ),
    PULL(
        listOf(
            StandardMuscles.BACK,
            StandardMuscles.UPPER_BACK,
            StandardMuscles.LATS,
            StandardMuscles.REAR_DELTS,
            StandardMuscles.BICEPS,
            StandardMuscles.FOREARMS
        )
    ),
    LEGS(
        listOf(
            StandardMuscles.QUADS,
            StandardMuscles.HAMSTRINGS,
            StandardMuscles.GLUTES,
            StandardMuscles.CALVES,
            StandardMuscles.ADDUCTORS,
            StandardMuscles.HIPS,
            // Hip hinges are the app's lower-back work; without this a Lower Back
            // priority would select no split at all.
            StandardMuscles.LOWER_BACK
        )
    ),
    UPPER_BODY(
        listOf(
            StandardMuscles.CHEST,
            StandardMuscles.BACK,
            StandardMuscles.UPPER_BACK,
            StandardMuscles.LATS,
            StandardMuscles.SHOULDERS,
            StandardMuscles.REAR_DELTS,
            StandardMuscles.BICEPS,
            StandardMuscles.TRICEPS
        )
    ),
    FULL_BODY(
        listOf(
            StandardMuscles.CHEST,
            StandardMuscles.BACK,
            StandardMuscles.QUADS,
            StandardMuscles.HAMSTRINGS,
            StandardMuscles.GLUTES,
            StandardMuscles.CORE
        )
    );

    companion object {
        /**
         * Used when no muscle is marked high priority. FULL_BODY is the only split that
         * trains Core, so it stays in the rotation rather than being unreachable.
         */
        val DEFAULT_ROTATION = listOf(PUSH, PULL, LEGS, UPPER_BODY, FULL_BODY)
    }
}

/** The goal combination a generated workout emphasises, chosen from the user's goals. */
enum class WorkoutEmphasis {
    POWER_AND_HYPERTROPHY,
    POWER_AND_PERFORMANCE,
    ATHLETIC_CONDITIONING,
    HYPERTROPHY_AND_DEFINITION,
    HYPERTROPHY,
    POWER_AND_STRENGTH,
    AGILITY_AND_EXPLOSIVENESS,
    HIGH_DENSITY_CIRCUIT,
    ATHLETIC_FOUNDATION,
    CONDITIONING
}

/**
 * Everything needed to write a generated workout's title, in any language.
 *
 * The planner produces this instead of a sentence, so the same plan reads as
 * "Push · Hypertrophy" or "Empuje · Hipertrofia" without the planner knowing either
 * language, and without the title becoming an input to any later decision.
 */
data class WorkoutTitleSpec(
    val split: WorkoutSplit,
    val emphasis: WorkoutEmphasis,
    val isReEntry: Boolean = false
)

/**
 * Why the planner produced this workout, as structured facts rather than prose.
 *
 * The wording is a string resource; the facts here are what actually drove the plan.
 */
sealed interface WorkoutRationaleSpec {
    /** Volume is capped for a return after a year or more away. */
    data class ReEntryRamp(val breakRange: BreakRange) : WorkoutRationaleSpec

    /** A shorter break still scales volume down. */
    data class BreakRecovery(
        val goals: List<FitnessGoal>,
        val breakRange: BreakRange
    ) : WorkoutRationaleSpec

    /** The ordinary case: goals plus the muscles this day prioritises. */
    data class GoalFocus(
        val goals: List<FitnessGoal>,
        val focusMuscles: List<String>
    ) : WorkoutRationaleSpec
}
