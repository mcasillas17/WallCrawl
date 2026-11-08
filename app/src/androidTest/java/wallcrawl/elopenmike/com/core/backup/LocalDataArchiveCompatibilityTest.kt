package wallcrawl.elopenmike.com.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.PrescriptionPurpose
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.WorkoutExercise

@RunWith(AndroidJUnit4::class)
class LocalDataArchiveCompatibilityTest {
    // These bytes come from the original codecs and fixtures, in ascending version order:
    // aa1dd3bf5, 930399cec, cf426c9fa, 91a0938de, 08f027026, b0905b27e,
    // e993a1770 (reported IDs/roles), f87b866af (selections/context).
    @Test fun originalWriterBytesAndChecksumsSurviveReadingAndUpgrade() {
        val fixtures = (1..6).map { "v$it" } + listOf("v7-reported", "v7-context")
        fixtures.forEach { name ->
            val original = fixture(name)
            val archive = LocalDataArchiveCodec.read(original.inputStream())
            assertArrayEquals(name, original, write(archive))
            val current = archive.copy(metadata = archive.metadata.copy(archiveVersion = 8))
            assertEquals(name, archive.snapshot, LocalDataArchiveCodec.read(write(current).inputStream()).snapshot)
        }
    }

    @Test fun version7KeepsTheSpecificFactsEachWriterRecorded() {
        val reported = LocalDataArchiveCodec.read(fixture("v7-reported").inputStream()).snapshot
        val reportedProfile = requireNotNull(reported.profile)
        assertEquals(listOf("lumbar:future|", " future / id "), reportedProfile.reportedConditions.toList())
        assertTrue(reportedProfile.activeConditions.isEmpty())
        assertTrue(reported.pendingConditionReassessments.isEmpty())
        val session = reported.sessions.single()
        assertNull(session.conditionContext)
        val row = session.exercises.single()
        assertTrue(row.isLegacyMobility)
        assertEquals(PrescriptionPurpose.SUPPORTIVE, row.prescription.purpose)
        assertEquals(25, row.prescription.holdSeconds)
        assertEquals(RepRange(4, 6), row.prescription.repRange)
        assertNull(row.prescription.targetDurationSeconds)
        assertEquals(8, row.sets.single().completedReps)
        val context = LocalDataArchiveCodec.read(fixture("v7-context").inputStream()).snapshot
        val contextProfile = requireNotNull(context.profile)
        assertTrue(contextProfile.reportedConditions.isEmpty())
        assertEquals("answered", contextProfile.activeConditions.single().answers["unknown"])
        assertEquals("KNEE_SENSITIVE|||FUTURE_UNKNOWN", contextProfile.unresolvedTrainingConstraintsRaw)
        val frozen = context.sessions.single().conditionContext!!
        assertEquals("v4", frozen.sourceSupportVersions["future"])
        assertEquals(frozen.canonicalDigest, context.recommendationRecords.single().conditionContextDigest)
        assertFalse(context.sessions.single().exercises.single().isLegacyMobility)
        assertNull(context.sessions.single().exercises.single().prescription.holdSeconds)
        assertEquals("prior-stop", context.pendingConditionReassessments.single().eventId)
    }

    @Test fun version7RejectsMixedFieldsEvenWhenTheyWouldWriteTheSameChecksum() {
        val reported = fixture("v7-reported").decodeToString()
        val context = fixture("v7-context").decodeToString()
        listOf(
            reported.replaceFirst("\"reportedConditions\":", "\"activeConditions\":\"[]\",\"reportedConditions\":"),
            reported.replaceFirst("\"role\":\"MOBILITY\"", "\"role\":\"MAIN\"").replaceFirst("\"targetSets\":", "\"purpose\":\"ORDINARY\",\"targetSets\":"),
            context.replaceFirst("\"activeConditions\":", "\"reportedConditions\":[],\"activeConditions\":"),
            context.replaceFirst("\"prescription\":", "\"role\":\"MAIN\",\"prescription\":"),
            context.replaceFirst("\"targetSets\":", "\"holdSeconds\":1,\"targetSets\":")
        ).forEach { json ->
            val failure = assertThrows(LocalDataArchiveException::class.java) { LocalDataArchiveCodec.read(json.byteInputStream()) }
            assertEquals(ArchiveRejection.MALFORMED, failure.rejection)
        }
    }

    @Test fun version7RefusesIdsItsWriterCouldNotStoreAndVersion8RejectsLegacyRole() {
        val reported = fixture("v7-reported").decodeToString()
        val reserved = reported.replace("lumbar:future|", "an|||id")
        assertEquals(ArchiveRejection.INVALID_VALUE, assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(reserved.byteInputStream())
        }.rejection)
        val roleInCurrent = reported.replace("\"archiveVersion\":7", "\"archiveVersion\":8")
        assertEquals(ArchiveRejection.MALFORMED, assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(roleInCurrent.byteInputStream())
        }.rejection)
    }

    @Test fun version8RejectsAnOrdinaryRowMarkedAsHistoricalMobility() {
        val reported = LocalDataArchiveCodec.read(fixture("v7-reported").inputStream())
        val current = write(reported.copy(metadata = reported.metadata.copy(archiveVersion = 8))).decodeToString()
        val contradictory = current.replace("\"purpose\":\"SUPPORTIVE\"", "\"purpose\":\"ORDINARY\"")
        val failure = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(contradictory.byteInputStream())
        }
        assertEquals(ArchiveRejection.INVALID_VALUE, failure.rejection)
    }

    @Test fun noVersion7RewriteCanRelabelSupportiveHistory() {
        val reported = LocalDataArchiveCodec.read(fixture("v7-reported").inputStream())
        val originalSession = reported.snapshot.sessions.single()
        val row = originalSession.exercises.single()
        val noHold = row.copy(prescription = row.prescription.copy(holdSeconds = null))
        val noIds = reported.snapshot.profile!!.copy(reportedConditions = emptySet())
        fun withRow(exercise: WorkoutExercise) = reported.copy(
            snapshot = reported.snapshot.copy(profile = noIds, sessions = listOf(originalSession.copy(exercises = listOf(exercise))))
        )
        val authoredSupportive = withRow(noHold.copy(isLegacyMobility = false))
        val historical = withRow(noHold).copy(version7Layout = ArchiveV7Layout.CONDITION_CONTEXT)
        listOf(authoredSupportive, historical).forEach { archive ->
            var opened = false
            assertThrows(IllegalArgumentException::class.java) {
                LocalDataArchiveCodec.write(archive) {
                    opened = true
                    ByteArrayOutputStream()
                }
            }
            assertFalse(opened)
        }
    }

    private fun fixture(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open("archives/$name.json").use { it.readBytes() }

    private fun write(archive: LocalDataArchive): ByteArray =
        ByteArrayOutputStream().also { output -> LocalDataArchiveCodec.write(archive) { output } }.toByteArray()
}
