package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * Derives the adaptation state a deterministic plan is built under.
 *
 * The policy is pure and total: it reads only the profile, performs no I/O, and cannot fail.
 *
 * It deliberately derives just two of the nine declared [AdaptationState] values, which is
 * exactly what the planner did before this policy existed. The remaining states have entry
 * and exit conditions written in terms of weekly dose targets and comparable outcomes that
 * do not exist yet, so deriving them here would be invention rather than policy.
 *
 * Widening the output is a safety-relevant change rather than an improvement in isolation:
 * `ExerciseEligibilityPolicy` withholds advanced-complexity exercises only while the state is
 * one of [CEILING_COVERED_STATES], so any additional state lifts that ceiling.
 */
class AdaptationStatePolicy {

    fun derive(profile: UserProfile): AdaptationState =
        if (profile.returningAfterBreakWeeks > 0) {
            AdaptationState.RETURNING
        } else {
            AdaptationState.UNCALIBRATED
        }

    companion object {
        /**
         * The states for which the temporary advanced-complexity ceiling applies.
         *
         * This mirrors the condition in `ExerciseEligibilityPolicy`. The two are coupled by a
         * regression test rather than by a shared call, because the ceiling is that policy's
         * decision to make and this one only has to stay inside it.
         */
        val CEILING_COVERED_STATES: Set<AdaptationState> =
            setOf(AdaptationState.UNCALIBRATED, AdaptationState.RETURNING)
    }
}
