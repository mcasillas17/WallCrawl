package wallcrawl.elopenmike.com.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.MuscleDoseAccounting

/**
 * Archive format version 2 and its recommendation records.
 *
 * The load-bearing property is compatibility in both directions: a version 2 document
 * carries the records, and a version 1 document — the only kind any shipped build has ever
 * written — still restores exactly as it did before.
 */
@RunWith(AndroidJUnit4::class)
class LocalDataArchiveRecommendationTest {

    @Test
    fun theCurrentFormat_isVersionTwoAndReadsEveryEarlierVersion() {
        assertThat(LocalDataArchiveFormat.ARCHIVE_VERSION).isEqualTo(2)
        assertThat(LocalDataArchiveFormat.SUPPORTED_ARCHIVE_VERSIONS.toList())
            .containsExactly(1, 2)
            .inOrder()
    }

    @Test
    fun roundTrip_preservesEveryRecommendationField() {
        val original = LocalDataArchiveFixtures.archive()

        val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(original.toBytes()))

        assertThat(restored.snapshot.recommendationRecords)
            .isEqualTo(original.snapshot.recommendationRecords)
        val repaired = restored.snapshot.recommendationRecords.single { it.outcome == "REPAIRED" }
        assertThat(repaired.reasonCodes).containsExactly("WEEKLY_ALLOWANCE_EXCEEDED")
        assertThat(repaired.doseAccounting).containsExactly(
            MuscleDoseAccounting("Back", completedSets = 0, proposedSets = 3, allowanceSets = null),
            MuscleDoseAccounting("Chest", completedSets = 4, proposedSets = 2, allowanceSets = 6)
        ).inOrder()
        // An absent allowance stays absent rather than becoming zero.
        assertThat(repaired.doseAccounting.single { it.muscle == "Back" }.allowanceSets).isNull()
    }

    @Test
    fun aVersionOneArchive_stillReadsAndCarriesNoRecords() {
        val versionOne = LocalDataArchiveFixtures.archive(
            metadata = LocalDataArchiveFixtures.metadata(archiveVersion = 1),
            snapshot = LocalDataArchiveFixtures.snapshot(recommendationRecords = emptyList())
        )

        val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(versionOne.toBytes()))

        assertThat(restored).isEqualTo(versionOne)
        assertThat(restored.snapshot.recommendationRecords).isEmpty()
        assertThat(restored.snapshot.sessions).isEqualTo(versionOne.snapshot.sessions)
    }

    @Test
    fun aVersionOneArchiveCarryingRecords_isRefusedRatherThanQuietlyUpgraded() {
        val document = LocalDataArchiveFixtures
            .archive()
            .toBytes()
            .decodeToString()
            .replace("\"archiveVersion\":2", "\"archiveVersion\":1")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(document.encodeToByteArray()))
        }

        assertThat(error.rejection).isEqualTo(ArchiveRejection.MALFORMED)
    }

    @Test
    fun anArchiveFromANewerBuild_isStillRefused() {
        val document = LocalDataArchiveFixtures
            .archive()
            .toBytes()
            .decodeToString()
            .replace("\"archiveVersion\":2", "\"archiveVersion\":3")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(document.encodeToByteArray()))
        }

        assertThat(error.rejection).isEqualTo(ArchiveRejection.UNSUPPORTED_VERSION)
    }

    @Test
    fun aRecordNamingAnUnknownSession_isRefusedBeforeAnythingIsWritten() {
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                recommendationRecords = LocalDataArchiveFixtures.recommendationRecords()
                    .map { it.copy(sessionId = "session-that-is-not-here") }
                    .take(1)
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(archive.toBytes()))
        }

        assertThat(error.rejection).isEqualTo(ArchiveRejection.INCONSISTENT)
    }

    @Test
    fun twoRecordsForOneSession_areRefused() {
        val record = LocalDataArchiveFixtures.recommendationRecords().first()
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                recommendationRecords = listOf(record, record.copy(outcome = "REPAIRED"))
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(archive.toBytes()))
        }

        assertThat(error.rejection).isEqualTo(ArchiveRejection.INCONSISTENT)
    }

    @Test
    fun anEditedDoseAccountingPayload_isRefusedRatherThanPartlyUnderstood() {
        val document = LocalDataArchiveFixtures
            .archive()
            .toBytes()
            .decodeToString()
            .replace("Chest\\t4\\t2\\t6", "Chest\\t4\\t2")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(document.encodeToByteArray()))
        }

        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
    }

    @Test
    fun anEditedRecord_isCaughtByTheChecksumLikeEveryOtherValue() {
        val document = LocalDataArchiveFixtures
            .archive()
            .toBytes()
            .decodeToString()
            .replace("\"outcome\":\"REPAIRED\"", "\"outcome\":\"VALID\"")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(document.encodeToByteArray()))
        }

        assertThat(error.rejection).isEqualTo(ArchiveRejection.CHECKSUM_MISMATCH)
    }

    private fun LocalDataArchive.toBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        LocalDataArchiveCodec.write(this) { output }
        return output.toByteArray()
    }
}
