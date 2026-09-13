package wallcrawl.elopenmike.com.core.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.WorkoutRationaleSpec
import wallcrawl.elopenmike.com.core.model.WorkoutRankingReason
import wallcrawl.elopenmike.com.core.model.WorkoutTitleSpec

/**
 * Writes the planner's structured output as a sentence in the reader's language.
 *
 * Everything here is parameterised: no title or explanation is assembled by concatenating
 * translated fragments, because word order and agreement differ between languages and a
 * sentence stitched together in English order reads wrong in Spanish.
 */

/** The generated workout's title, e.g. "Push · Hypertrophy" or "Empuje · Hipertrofia". */
@Composable
fun generatedWorkoutTitle(spec: WorkoutTitleSpec): String {
    val split = stringResource(spec.split.labelRes)
    val emphasis = stringResource(spec.emphasis.labelRes)
    return stringResource(
        if (spec.isReEntry) {
            R.string.generated_workout_title_reentry
        } else {
            R.string.generated_workout_title
        },
        split,
        emphasis
    )
}

/**
 * Why the planner produced this workout, written for the reader.
 *
 * [unavailableFocusMuscles] adds one sentence naming the prioritised muscles nothing
 * available trains as its main target. It is appended rather than folded into each
 * variant so a re-entry or post-break session explains the missing emphasis too, and so
 * the sentence a started session records is the same one that was on screen.
 */
@Composable
fun generatedWorkoutRationale(
    spec: WorkoutRationaleSpec,
    unavailableFocusMuscles: List<String> = emptyList(),
    rankingReasons: List<WorkoutRankingReason> = emptyList()
): String {
    val sentences = buildList {
        add(rationaleSentence(spec))
        unavailableFocusNotice(unavailableFocusMuscles)?.let(::add)
        workoutRankingNotice(rankingReasons)?.let(::add)
    }
    return sentences.joinToString(separator = " ")
}

@Composable
fun workoutRankingNotice(rankingReasons: List<WorkoutRankingReason>): String? {
    if (rankingReasons.isEmpty()) return null
    return rankingReasons.map { rankingReason ->
        when (rankingReason) {
            is WorkoutRankingReason.SupportedRegressionPreference -> stringResource(
                R.string.generated_rationale_supported_regression_preference,
                stringResource(rankingReason.capability.labelRes)
            )
        }
    }.distinct().joinToString(separator = " ")
}

/**
 * The sentence naming prioritised muscles nothing available trains, or null when there are
 * none to name.
 *
 * Separate from the explanation above because the two have different audiences. The
 * explanation is what a started session records; this sentence is what the card shows,
 * and only when there is actually something to say. Surfacing the whole explanation on
 * every session would be a product change this does not make.
 */
@Composable
fun unavailableFocusNotice(unavailableFocusMuscles: List<String>): String? {
    if (unavailableFocusMuscles.isEmpty()) return null
    val vocabulary = LocalExerciseVocabulary.current
    val separator = stringResource(R.string.list_separator)
    val muscles = unavailableFocusMuscles.map { vocabulary.muscle(it) }.joinToString(separator)
    return stringResource(R.string.generated_rationale_focus_unavailable, muscles)
}

@Composable
private fun rationaleSentence(spec: WorkoutRationaleSpec): String {
    val vocabulary = LocalExerciseVocabulary.current
    return when (spec) {
        is WorkoutRationaleSpec.ReEntryRamp -> stringResource(
            R.string.generated_rationale_reentry,
            stringResource(spec.breakRange.titleRes)
        )

        is WorkoutRationaleSpec.BreakRecovery -> stringResource(
            R.string.generated_rationale_break_recovery,
            goalList(spec.goals),
            stringResource(spec.breakRange.titleRes)
        )

        is WorkoutRationaleSpec.GoalFocus -> stringResource(
            R.string.generated_rationale_goal_focus,
            goalList(spec.goals),
            spec.focusMuscles.joinToString(stringResource(R.string.list_separator)) {
                vocabulary.muscle(it)
            }
        )
    }
}

@Composable
private fun goalList(goals: List<FitnessGoal>): String {
    val separator = stringResource(R.string.generated_workout_goal_separator)
    // Resolved with `map` (inline, so a composable call is allowed) before joining:
    // joinToString's transform is not inline and cannot read resources.
    return goals.map { goal -> stringResource(goal.labelRes) }.joinToString(separator)
}
