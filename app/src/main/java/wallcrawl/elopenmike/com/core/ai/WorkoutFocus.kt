package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WorkoutSplit

/**
 * What makes a session's advertised split genuine.
 *
 * A split is a promise about what the session trains. Matching a split's muscles anywhere,
 * including in the descriptive secondary list, is not that promise: shoulder involvement in
 * a row or an anti-rotation drill made `PUSH` look fillable for a band-only inventory, and
 * the user got a Push day with no pushing in it.
 *
 * The rule below is deliberately about the muscle an exercise trains **as its own purpose**
 * and not about its movement pattern. Only 131 of the 302 bundled entries carry legacy
 * programming, and genuine pushes such as `archer-push-up` and `handstand-push-up` carry no
 * pattern at all, so requiring one would invent an absence rather than describe a fact.
 *
 * This is a truthfulness rule about one label. It is not a claim that a session must cover
 * every pattern, that repeating a movement is harmful, or that a supported session is
 * medically appropriate for anyone.
 */

/**
 * The muscles [this] trains as its own purpose, rather than ones it merely involves.
 *
 * Approved reviewed metadata wins where the reviewed contract applies, so the enabled path
 * reads the single designated `directPrimaryMuscle` instead of the broad legacy list. A
 * `DRAFT` record is skipped for the same reason every other rule skips one: a draft is not
 * approval, and it must never become a silent source of production behaviour. Legacy
 * `primaryMuscles` is the fallback, which is what the shipped path uses today.
 */
internal fun Exercise.focusMuscles(): List<String> = reviewedMetadata
    ?.takeIf { it.reviewState == ReviewState.APPROVED }
    ?.takeIf { it.isWellFormedApprovedMetadata() }
    ?.let { listOf(it.directPrimaryMuscle) }
    ?: primaryMuscles

/**
 * The muscles [this] involves without training them as its own purpose.
 *
 * The same approved-then-legacy resolution as [focusMuscles], so both halves of an
 * exercise's muscle description come from one source. Reading the approved record's
 * `directPrimaryMuscle` while still reading the legacy secondary list would mix two
 * classifications of the same exercise.
 */
internal fun Exercise.involvedMuscles(): List<String> = reviewedMetadata
    ?.takeIf { it.reviewState == ReviewState.APPROVED }
    ?.takeIf { it.isWellFormedApprovedMetadata() }
    ?.descriptiveSecondaryMuscles
    ?.toList()
    ?: secondaryMuscles

/**
 * Whether [exercise] genuinely supports this split's advertised focus.
 *
 * Used by split selection, by the ordering that fills the session, by whole-program
 * validation and by the tests, so those four cannot disagree about what a Push day is.
 */
internal fun WorkoutSplit.trainsAsFocus(exercise: Exercise): Boolean =
    exercise.isStrengthWork() && exercise.focusMuscles().any { it in targetMuscles }

/**
 * Whether [exercise] may occupy a slot in this split at all.
 *
 * Deliberately a **superset** of [trainsAsFocus]: once the focus is genuinely established,
 * an exercise that only brushes the split is still legal accessory work. It can never be
 * the evidence for the split's name.
 *
 * The superset relation is what keeps fillability and selection honest. A predicate that
 * read the approved `directPrimaryMuscle` for fillability and the legacy muscle lists for
 * slot eligibility could call a split fillable and then drop the only candidate that made
 * it so, which is the mismatch this whole contract exists to remove.
 */
internal fun WorkoutSplit.canFill(exercise: Exercise): Boolean =
    trainsAsFocus(exercise) ||
        (exercise.isStrengthWork() && exercise.involvedMuscles().any { it in targetMuscles })

/**
 * Whether this belongs in a prescribed strength slot with sets and reps.
 *
 * Cardio machines and stretches are tagged with the muscles they involve, so once
 * upstream's umbrella names were resolved they started matching splits — putting
 * "Walking" in a Legs · Hypertrophy plan alongside squats. They stay in the catalog to
 * browse and to build custom workouts from; they are not prescribed as training slots.
 *
 * The test is what can be prescribed, not whether conditioning is involved: a kettlebell
 * swing is loaded work for reps that happens to be tagged Cardio, and a plank is a timed
 * hold that is not. Only untimed-distance work and conditioning drills measured purely
 * in time are dropped.
 */
internal fun Exercise.isStrengthWork(): Boolean = when {
    isStretch -> false
    type == ExerciseType.DISTANCE_DURATION -> false
    type == ExerciseType.DURATION -> !isConditioning()
    else -> true
}

private fun Exercise.isConditioning(): Boolean =
    (primaryMuscles + secondaryMuscles).any { it == StandardMuscles.CARDIO }
