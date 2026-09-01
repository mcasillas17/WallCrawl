package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.UserProfile

class AdaptationStatePolicyTest {

    private val policy = AdaptationStatePolicy()

    @Test
    fun aReportedBreakDerivesReturning() {
        val profile = UserProfile(returningAfterBreakWeeks = 3)

        assertThat(policy.derive(profile)).isEqualTo(AdaptationState.RETURNING)
    }

    @Test
    fun noReportedBreakDerivesUncalibrated() {
        val profile = UserProfile(returningAfterBreakWeeks = 0)

        assertThat(policy.derive(profile)).isEqualTo(AdaptationState.UNCALIBRATED)
    }

    /**
     * `ExerciseEligibilityPolicy` applies the temporary advanced-complexity ceiling with an
     * allow-by-default check on exactly UNCALIBRATED and RETURNING. Emitting any other state
     * lifts that ceiling, so a user who has not calibrated could be offered advanced work
     * without a single test failing while the reviewed-eligibility flag is disabled.
     *
     * If this policy learns a third state, this test fails: update the ceiling in
     * `ExerciseEligibilityPolicy` deliberately, and in the same change.
     */
    @Test
    fun everyDerivableStateIsOneTheAdvancedCeilingCovers() {
        val derivable = (0..12).mapTo(mutableSetOf()) { weeks ->
            policy.derive(UserProfile(returningAfterBreakWeeks = weeks))
        }

        assertThat(derivable).isNotEmpty()
        assertThat(AdaptationStatePolicy.CEILING_COVERED_STATES).containsAtLeastElementsIn(derivable)
    }
}
