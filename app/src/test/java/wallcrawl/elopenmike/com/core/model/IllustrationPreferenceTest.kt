package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IllustrationPreferenceTest {
    @Test
    fun automaticUsesGenderAndKeepsTheExistingMaleDefault() {
        assertThat(UserProfile().illustrationVariant).isEqualTo(IllustrationVariant.MALE)
        assertThat(UserProfile(gender = ProfileGender.WOMAN).illustrationVariant)
            .isEqualTo(IllustrationVariant.FEMALE)
        for (gender in listOf(ProfileGender.MAN, ProfileGender.NON_BINARY, ProfileGender.UNSPECIFIED)) {
            assertThat(UserProfile(gender = gender).illustrationVariant).isEqualTo(IllustrationVariant.MALE)
        }
    }

    @Test
    fun legacyIllustrationOverrideCannotOverrideProfileGender() {
        for (gender in ProfileGender.entries) {
            val expected = if (gender == ProfileGender.WOMAN) IllustrationVariant.FEMALE else IllustrationVariant.MALE
            for (legacyPreference in IllustrationPreference.entries) {
                val profile = UserProfile(gender = gender, illustrationPreference = legacyPreference)
                assertThat(profile.illustrationVariant).isEqualTo(expected)
            }
        }
    }
}
