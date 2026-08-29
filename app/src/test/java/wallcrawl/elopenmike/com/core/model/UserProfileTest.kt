package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserProfileTest {

    @Test
    fun freshProfile_requiresOnboardingAndHasNoAssumedGym() {
        val profile = UserProfile()

        assertThat(profile.onboardingCompleted).isFalse()
        assertThat(profile.availableEquipment).containsExactly(StandardEquipment.BODYWEIGHT)
        assertThat(profile.trainingConstraints).isEmpty()
        assertThat(profile.returningAfterBreakWeeks).isEqualTo(0)
        assertThat(profile.confirmedStartingLoads).isEmpty()
    }

    @Test
    fun trainingConstraint_hasTheFullSafetyVocabulary() {
        assertThat(TrainingConstraint.entries.map { it.name }).containsExactly(
            "SHOULDER_SENSITIVE",
            "ELBOW_SENSITIVE",
            "WRIST_SENSITIVE",
            "LOWER_BACK_SENSITIVE",
            "HIP_SENSITIVE",
            "KNEE_SENSITIVE",
            "LOW_IMPACT_ONLY"
        )
    }
}
