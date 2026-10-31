package wallcrawl.elopenmike.com.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.DeloadReasonCode
import wallcrawl.elopenmike.com.core.model.DeloadRecommendationProvenance
import wallcrawl.elopenmike.com.core.model.DeloadSource
import wallcrawl.elopenmike.com.core.model.ProgressionProvenance
import wallcrawl.elopenmike.com.core.model.ProgressionAxis
import wallcrawl.elopenmike.com.core.model.ProgressionReason
import wallcrawl.elopenmike.com.core.model.ProgressionReasonCode
import wallcrawl.elopenmike.com.core.model.RecommendationRecord

@RunWith(AndroidJUnit4::class)
class LocalDataArchiveProgressionTest {
    @Test fun maximumStructuredProgressionAndDeloadProvenanceRoundTripsWithoutShorteningIdentifiers() {
        val progression = (0 until ProgressionReasonCode.MAX_EXERCISES).map { index ->
            ProgressionProvenance(
                exerciseId = "$index".padEnd(200, 'e'),
                reason = ProgressionReason.HOLD_ACCEPTED_DELOAD,
                axis = null,
                sourceSessionIds = listOf("first".padEnd(200, 'a'), "second".padEnd(200, 'b')),
                baseConfigurationDigest = "a".repeat(64)
            )
        }
        val deload = DeloadRecommendationProvenance(42, "d".repeat(256), DeloadSource.EXPLICIT_REQUEST)
        val structured = ProgressionReasonCode.encode(progression) + DeloadReasonCode.encode(deload)
        val codes = structured + (0 until RecommendationRecord.MAX_REASON_CODES - structured.size).map {
            "OPAQUE_$it".padEnd(RecommendationRecord.MAX_REASON_TOKEN_LENGTH, 'x')
        }
        assertTrue(codes.any { it.length > 200 })
        val archive = archive(codes)

        val restored = LocalDataArchiveCodec.read(bytes(archive).inputStream())

        assertEquals(archive, restored)
        val restoredCodes = restored.snapshot.recommendationRecords.single().reasonCodes
        assertEquals(RecommendationRecord.MAX_REASON_CODES, restoredCodes.size)
        assertEquals(progression, ProgressionReasonCode.decode(restoredCodes))
        assertEquals(deload, DeloadReasonCode.decode(restoredCodes))
    }

    @Test fun malformedStructuredEnumsAreRejectedWithoutEchoingTheArchivedLiteral() {
        val progression = ProgressionProvenance("exercise", ProgressionReason.HOLD_ACCEPTED_DELOAD,
            null, emptyList(), "a".repeat(64))
        val codes = ProgressionReasonCode.encode(listOf(progression)) +
            DeloadReasonCode.encode(DeloadRecommendationProvenance(1, "offer", DeloadSource.EXPLICIT_REQUEST))
        val original = bytes(archive(codes)).decodeToString()
        for (token in listOf("REASON:HOLD_ACCEPTED_DELOAD", "SOURCE:EXPLICIT_REQUEST")) {
            val privateLiteral = "private-unsupported-literal"
            val malformed = original.replace(token, token.substringBefore(':') + ":" + privateLiteral)
            assertNotEquals(original, malformed)
            val error = assertThrows(LocalDataArchiveException::class.java) {
                LocalDataArchiveCodec.read(malformed.byteInputStream())
            }
            assertEquals(ArchiveRejection.INVALID_VALUE, error.rejection)
            assertFalse("Archived literals must not reach diagnostics", error.message.orEmpty().contains(privateLiteral))
        }
    }

    @Test fun archiveStillRejectsTokensAndListsBeyondTheCurrentDomainLimits() {
        val original = bytes(archive(listOf("ORIGINAL"))).decodeToString()
        val oversized = "\"reasonCodes\":[\"" + "x".repeat(RecommendationRecord.MAX_REASON_TOKEN_LENGTH + 1) + "\"]"
        val tooMany = (0..RecommendationRecord.MAX_REASON_CODES).joinToString(
            prefix = "\"reasonCodes\":[", postfix = "]") { "\"OPAQUE_$it\"" }
        for ((replacement, rejection) in listOf(
            oversized to ArchiveRejection.INVALID_VALUE, tooMany to ArchiveRejection.TOO_LARGE
        )) {
            val malformed = original.replace("\"reasonCodes\":[\"ORIGINAL\"]", replacement)
            assertNotEquals(original, malformed)
            val error = assertThrows(LocalDataArchiveException::class.java) {
                LocalDataArchiveCodec.read(malformed.byteInputStream())
            }
            assertEquals(rejection, error.rejection)
        }
    }

    @Test fun knownProgressionRequiresArchivedSourcesAndAnExerciseInTheRecommendationsSession() {
        val codes = ProgressionReasonCode.encode(listOf(ProgressionProvenance(
            "planned-exercise", ProgressionReason.ADVANCED, ProgressionAxis.LOAD,
            listOf("old-one", "old-two"), "a".repeat(64)
        )))
        val valid = archive(codes)
        assertEquals(valid, LocalDataArchiveCodec.read(bytes(valid).inputStream()))
        val recordSessionId = valid.snapshot.recommendationRecords.single().sessionId
        val missingSource = valid.copy(snapshot = valid.snapshot.copy(
            sessions = valid.snapshot.sessions.filterNot { it.id == "old-one" }
        ))
        val missingExercise = valid.copy(snapshot = valid.snapshot.copy(
            sessions = valid.snapshot.sessions.map {
                if (it.id == recordSessionId) it.copy(exercises = emptyList()) else it
            }
        ))
        for (invalid in listOf(missingSource, missingExercise)) {
            var opened = false
            val error = assertThrows(LocalDataArchiveException::class.java) {
                LocalDataArchiveCodec.write(invalid) { opened = true; ByteArrayOutputStream() }
            }
            assertEquals(ArchiveRejection.INCONSISTENT, error.rejection)
            assertFalse(opened)
        }
    }

    @Test fun unknownFutureProgressionVersionsRemainOpaqueWithoutInventedReferentialRules() {
        val codes = listOf(
            "ONE_VARIABLE_PROGRESSION_V99.0.EXERCISE:future-exercise",
            "ONE_VARIABLE_PROGRESSION_V99.0.SOURCE0:future-source"
        )
        val original = archive(codes)
        assertEquals(original, LocalDataArchiveCodec.read(bytes(original).inputStream()))
    }

    private fun archive(codes: List<String>): LocalDataArchive {
        val record = LocalDataArchiveFixtures.recommendationRecords().first().copy(reasonCodes = codes)
        val snapshot = LocalDataArchiveFixtures.snapshot(recommendationRecords = listOf(record))
        val progression = ProgressionReasonCode.decode(codes)
        val recordSession = snapshot.sessions.first { it.id == record.sessionId }
        val sessions = snapshot.sessions.map { session ->
            if (session.id != record.sessionId || progression.isEmpty()) session
            else session.copy(exercises = progression.mapIndexed { index, reason ->
                recordSession.exercises.first().copy(
                    id = "provenance-exercise-$index", exerciseId = reason.exerciseId,
                    orderIndex = index, sets = emptyList()
                )
            })
        }
        val sources = progression.flatMap { it.sourceSessionIds }.distinct()
            .filterNot { id -> sessions.any { it.id == id } }
            .map { id -> recordSession.copy(id = id, exercises = emptyList()) }
        return LocalDataArchiveFixtures.archive(snapshot = snapshot.copy(sessions = sessions + sources))
    }

    private fun bytes(archive: LocalDataArchive): ByteArray =
        ByteArrayOutputStream().also { sink -> LocalDataArchiveCodec.write(archive) { sink } }.toByteArray()
}
