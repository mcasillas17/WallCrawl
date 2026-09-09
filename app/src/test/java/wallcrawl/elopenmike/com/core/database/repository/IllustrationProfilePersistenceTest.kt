package wallcrawl.elopenmike.com.core.database.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.*

class IllustrationProfilePersistenceTest {
    @Test
    fun genderDrivesArtworkAfterReloadEvenWithLegacyOverrideData() = runTest {
        val dao = FakeUserProfileDao()
        val repository = OfflineUserProfileRepository(dao)
        repository.updateGender(ProfileGender.WOMAN)
        repository.saveProfile(repository.getProfileOnce().copy(illustrationPreference = IllustrationPreference.MALE))
        repository.updatePreferredDuration(60)
        val restored = OfflineUserProfileRepository(dao).getProfileOnce()
        assertThat(restored.gender).isEqualTo(ProfileGender.WOMAN)
        assertThat(restored.illustrationPreference).isEqualTo(IllustrationPreference.MALE)
        assertThat(restored.preferredDurationMinutes).isEqualTo(60)
        assertThat(restored.illustrationVariant).isEqualTo(IllustrationVariant.FEMALE)
    }
}
