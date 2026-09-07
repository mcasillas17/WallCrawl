package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.PlannedExercise

/**
 * The one named estimator that turns a proposed session into minutes.
 *
 * ## Why it is named
 *
 * Whole-program validation has to recompute a proposal's duration and compare it to the
 * number the planner reported. That comparison is only meaningful if both sides agree on
 * what a minute of training contains, so the assumptions are stated once here and used by
 * the planner and the validator rather than written out twice and left to drift.
 *
 * ## Assumptions
 *
 * - A set takes its own `targetDurationSeconds` when it has one, and 45 seconds otherwise.
 *   Repetition work does not record how long a set takes, and 45 seconds is the figure the
 *   planner has always used; it is a planning convenience, not a measurement.
 * - Rest is counted once per set, including after an exercise's final set, which stands in
 *   for the transition into the next exercise.
 * - Seconds are divided by 60 with truncation, then clamped to 1..240 minutes.
 *
 * ## What it is not
 *
 * It is an estimate, never a promise about how long a session will take, and its bounds are
 * structural limits on a representable duration rather than physiological limits. It says
 * nothing about whether a proposal fits the user's requested duration; version 1 of the
 * validator deliberately enforces no relationship between the two.
 */
object WorkoutDurationEstimator {

    /** The estimator identity recorded with a validated recommendation. */
    const val VERSION: String = "DURATION_ESTIMATOR_V1"

    /**
     * How far a reported estimate may differ from this estimator before it is a violation.
     *
     * A planner sharing this object deviates by zero. The tolerance exists because
     * `WorkoutPlanner` admits other implementations, which may round differently.
     */
    const val TOLERANCE_MINUTES: Int = 1

    const val MIN_MINUTES: Int = 1
    const val MAX_MINUTES: Int = 240

    private const val DEFAULT_SET_SECONDS = 45

    fun estimateMinutes(exercises: List<PlannedExercise>): Int {
        // Long arithmetic so twenty maximal exercises cannot overflow before the clamp.
        val totalSeconds = exercises.sumOf { exercise ->
            val prescription = exercise.prescription
            val setSeconds = prescription.targetDurationSeconds ?: DEFAULT_SET_SECONDS
            prescription.targetSets.toLong() * (setSeconds.toLong() + prescription.restSeconds)
        }
        return (totalSeconds / 60L).coerceIn(MIN_MINUTES.toLong(), MAX_MINUTES.toLong()).toInt()
    }
}
