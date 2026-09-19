package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * Derives the adaptation state a deterministic plan is built under.
 *
 * A reported return takes precedence over an accepted, unconsumed one-workout deload.
 * Offers alone do not change state. No global calibration or recovery state is inferred.
 */
class AdaptationStatePolicy {

    fun derive(profile: UserProfile, acceptedDeload: Boolean = false): AdaptationState =
        if (profile.returningAfterBreakWeeks > 0) {
            AdaptationState.RETURNING
        } else if (acceptedDeload) {
            AdaptationState.HOLD
        } else {
            AdaptationState.UNCALIBRATED
        }

    companion object {
        /** Only the eligibility policy's explicit history/regression exceptions lift it. */
        val CEILING_COVERED_STATES: Set<AdaptationState> =
            AdaptationState.entries.toSet()
    }
}
