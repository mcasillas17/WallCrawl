package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StarterProfileTest {

    @Test
    fun apply_writesStarterValues() {
        val base = UserProfile(
            name = "Kept",
            gender = ProfileGender.WOMAN,
            experienceLevel = ExperienceLevel.ADVANCED,
            availableEquipment = listOf(StandardEquipment.BARBELL),
            trainingConstraints = setOf(TrainingConstraint.KNEE_SENSITIVE),
            onboardingCompleted = false,
            personalizationCompleted = true
        )

        val profile = StarterProfile.apply(base, setOf(FitnessGoal.STRENGTH), 4, 45, "DE")

        assertThat(profile.name).isEqualTo(UserProfile().name)
        assertThat(profile.goals).containsExactly(FitnessGoal.STRENGTH)
        assertThat(profile.daysPerWeek).isEqualTo(4)
        assertThat(profile.preferredDurationMinutes).isEqualTo(45)
        assertThat(profile.experienceLevel).isEqualTo(ExperienceLevel.BEGINNER)
        assertThat(profile.preferredUnit).isEqualTo(WeightUnit.KG)
        assertThat(profile.availableEquipment).containsExactly(StandardEquipment.BODYWEIGHT)
        assertThat(profile.trainingConstraints).containsExactly(TrainingConstraint.LOW_IMPACT_ONLY)
        assertThat(profile.movementCapabilities).isEqualTo(MovementCapabilities.unknown())
        assertThat(profile.activeConditions).isEmpty()
        assertThat(profile.reportedConditions).isEmpty()
        assertThat(profile.gender).isEqualTo(ProfileGender.UNSPECIFIED)
        assertThat(profile.onboardingCompleted).isTrue()
        assertThat(profile.personalizationCompleted).isFalse()
    }

    @Test
    fun apply_writesTheChosenGender() {
        val profile = StarterProfile.apply(UserProfile(), setOf(FitnessGoal.STRENGTH), 3, 30, "US", ProfileGender.WOMAN)

        assertThat(profile.gender).isEqualTo(ProfileGender.WOMAN)
        assertThat(profile.illustrationVariant).isEqualTo(IllustrationVariant.FEMALE)
    }

    @Test
    fun unitFor_US_LR_MM_isLbs() {
        listOf("US", "LR", "MM").forEach {
            assertThat(StarterProfile.unitFor(it)).isEqualTo(WeightUnit.LBS)
        }
    }

    @Test
    fun unitFor_DE_isKg() {
        assertThat(StarterProfile.unitFor("DE")).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun unitFor_emptyRegion_isKg() {
        assertThat(StarterProfile.unitFor("")).isEqualTo(WeightUnit.KG)
    }
}
