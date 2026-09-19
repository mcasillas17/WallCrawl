package wallcrawl.elopenmike.com.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.ai.DeloadOfferPolicy
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class LocalDataArchiveDeloadTest {
    private val offer = DeloadOffer("request-1", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
    private fun archive(status: DeloadChoiceStatus = DeloadChoiceStatus.ACCEPTED) =
        LocalDataArchiveFixtures.archive().let { base ->
            base.copy(snapshot = base.snapshot.copy(deloadPreferences = DeloadPreferences(
                revision = 2, choice = DeloadChoice(offer, status, 1_000,
                    if (status == DeloadChoiceStatus.CONSUMED) "deleted-cancelled-session" else null),
                lastHandledReturnKey = DeloadOfferPolicy.returnKey(base.snapshot.profile!!)
            )))
        }

    @Test fun allChoicesRoundTripIncludingConsumedLinkToDeletedCancelledWorkout() {
        assertEquals(4, LocalDataArchiveFormat.ARCHIVE_VERSION)
        DeloadChoiceStatus.entries.forEach { status ->
            val original = archive(status)
            assertEquals(original, LocalDataArchiveCodec.read(ByteArrayInputStream(bytes(original))))
        }
    }

    @Test fun earlierFormatsKeepTheirCanonicalChecksumsAndNeverInventPreferences() {
        for (version in 1..3) {
            val base = LocalDataArchiveFixtures.archive()
            val original = base.copy(metadata = base.metadata.copy(archiveVersion = version),
                snapshot = base.snapshot.copy(recommendationRecords =
                    if (version == 1) emptyList() else base.snapshot.recommendationRecords))
            val encoded = bytes(original)
            val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(encoded))
            assertNull(restored.snapshot.deloadPreferences)
            assertArrayEquals(encoded, bytes(restored))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bytes(archive().let { it.copy(metadata = it.metadata.copy(archiveVersion = 3)) })
        }
    }

    @Test fun strictFieldsEnumsVersionsBoundsAndCrossFieldRulesAreCheckedBeforeChecksum() {
        val valid = bytes(archive()).decodeToString()
        val edits = listOf(
            "\"status\":\"ACCEPTED\"" to "\"status\":\"secret-invalid\"",
            "\"source\":\"EXPLICIT_REQUEST\"" to "\"source\":\"secret-invalid\"",
            "\"policyVersion\":\"${DeloadOfferPolicy.VERSION}\"" to "\"policyVersion\":\"secret-invalid\"",
            "\"id\":\"request-1\"" to "\"id\":\"secret\\ninvalid\"",
            "\"id\":\"request-1\"" to "\"id\":\"secret|||invalid\"",
            "\"status\":\"ACCEPTED\"" to "\"status\":\"CONSUMED\"",
            "\"decidedAtEpochMillis\":1000" to "\"decidedAtEpochMillis\":-1",
            "\"revision\":2,\"choice\"" to "\"revision\":0,\"choice\"",
            "\"profileId\":\"default_user\"" to "\"profileId\":\"secret-invalid\"",
            "\"status\":\"ACCEPTED\"" to "\"status\":\"ACCEPTED\",\"extra\":true",
            "\"status\":\"ACCEPTED\"" to "\"status\":\"ACCEPTED\",\"status\":\"ACCEPTED\""
        )
        edits.forEach { (from, to) ->
            assertTrue("Mutation must target an existing field", valid.contains(from))
            val error = assertThrows(LocalDataArchiveException::class.java) {
                LocalDataArchiveCodec.read(ByteArrayInputStream(valid.replace(from, to).encodeToByteArray()))
            }
            assertNotEquals(ArchiveRejection.CHECKSUM_MISMATCH, error.rejection)
            assertFalse(error.message.orEmpty().contains("secret-invalid"))
        }
    }

    @Test fun preferencesRequireOwningOnboardedProfileAndConsumedLinkCannotNameManualWorkout() {
        val base = archive()
        listOf(null, base.snapshot.profile!!.copy(onboardingCompleted = false)).forEach { profile ->
            assertThrows(LocalDataArchiveException::class.java) {
                bytes(base.copy(snapshot = base.snapshot.copy(profile = profile)))
            }
        }
        val session = base.snapshot.sessions.first().copy(origin = WorkoutOrigin.CUSTOM_TEMPLATE)
        val consumed = base.snapshot.deloadPreferences!!.let {
            it.copy(choice = it.choice!!.copy(status = DeloadChoiceStatus.CONSUMED, sessionId = session.id))
        }
        assertThrows(LocalDataArchiveException::class.java) {
            bytes(base.copy(snapshot = base.snapshot.copy(
                sessions = base.snapshot.sessions.map { if (it.id == session.id) session else it },
                deloadPreferences = consumed)))
        }
    }

    private fun bytes(archive: LocalDataArchive): ByteArray =
        ByteArrayOutputStream().also { sink -> LocalDataArchiveCodec.write(archive) { sink } }.toByteArray()
}
