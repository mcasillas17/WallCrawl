package wallcrawl.elopenmike.com.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.IllustrationPreference
import wallcrawl.elopenmike.com.core.model.ProfileGender

@RunWith(AndroidJUnit4::class)
class IllustrationArchiveTest {
    @Test
    fun genderAndOverrideRoundTripWithHistoryInVersionThree() {
        val profile = LocalDataArchiveFixtures.profile().copy(
            gender = ProfileGender.WOMAN,
            illustrationPreference = IllustrationPreference.MALE
        )
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot().copy(profile = profile)
        )
        val bytes = encode(archive)
        val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(bytes))
        assertThat(restored).isEqualTo(archive)
        assertThat(restored.snapshot.profile!!.gender).isEqualTo(ProfileGender.WOMAN)
        assertThat(restored.snapshot.profile!!.illustrationPreference).isEqualTo(IllustrationPreference.MALE)
    }

    @Test
    fun earlierArchivesKeepTheirChecksumAndReceiveOptionalDefaults() {
        for (version in 1..2) {
            val archive = LocalDataArchiveFixtures.archive(
                metadata = LocalDataArchiveFixtures.metadata(archiveVersion = version),
                snapshot = LocalDataArchiveFixtures.snapshot(recommendationRecords = emptyList())
            )
            val bytes = encode(archive)
            assertThat(bytes.decodeToString()).doesNotContain("\"gender\"")
            assertThat(bytes.decodeToString()).doesNotContain("\"illustrationPreference\"")
            val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(bytes))
            assertThat(restored).isEqualTo(archive)
            assertThat(encode(restored).toList()).isEqualTo(bytes.toList())
            assertThat(restored.snapshot.profile!!.gender).isEqualTo(ProfileGender.UNSPECIFIED)
            assertThat(restored.snapshot.profile!!.illustrationPreference).isEqualTo(IllustrationPreference.AUTOMATIC)
        }
    }

    @Test
    fun invalidOrMissingNewFieldsAreRejectedBeforeRestore() {
        val json = encode(LocalDataArchiveFixtures.archive()).decodeToString()
        for (bad in listOf(
            json.replace("\"gender\":\"UNSPECIFIED\"", "\"gender\":\"UNKNOWN_VALUE\""),
            json.replace(",\"illustrationPreference\":\"AUTOMATIC\"", ""),
            json.replace("\"archiveVersion\":3", "\"archiveVersion\":2")
        )) {
            assertThrows(LocalDataArchiveException::class.java) {
                LocalDataArchiveCodec.read(ByteArrayInputStream(bad.encodeToByteArray()))
            }
        }
    }

    @Test
    fun legacyExportCannotSilentlyDropChosenGender() {
        val archive = LocalDataArchiveFixtures.archive(
            metadata = LocalDataArchiveFixtures.metadata(archiveVersion = 2),
            snapshot = LocalDataArchiveFixtures.snapshot().copy(
                profile = LocalDataArchiveFixtures.profile().copy(gender = ProfileGender.WOMAN)
            )
        )
        var opened = false
        assertThrows(IllegalArgumentException::class.java) {
            LocalDataArchiveCodec.write(archive) { opened = true; ByteArrayOutputStream() }
        }
        assertThat(opened).isFalse()
    }

    private fun encode(archive: LocalDataArchive): ByteArray {
        val output = ByteArrayOutputStream()
        LocalDataArchiveCodec.write(archive) { output }
        return output.toByteArray()
    }
}
