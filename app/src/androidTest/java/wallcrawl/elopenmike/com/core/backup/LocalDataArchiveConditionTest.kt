package wallcrawl.elopenmike.com.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class LocalDataArchiveConditionTest {
    @Test fun currentArchivePreservesUnsupportedSelections() {
        val original = LocalDataArchiveFixtures.archive().let { archive ->
            archive.copy(snapshot = archive.snapshot.copy(profile = archive.snapshot.profile!!.copy(
                activeConditions = listOf(SelectedCondition("future-condition", 3, mapOf("question" to "future-answer")))
            )))
        }
        val output = ByteArrayOutputStream()
        LocalDataArchiveCodec.write(original) { output }
        assertEquals(original, LocalDataArchiveCodec.read(ByteArrayInputStream(output.toByteArray())))
    }
    @Test fun fullContextAndPendingMarkerRoundTripInOriginalOrder() {
        val base = LocalDataArchiveFixtures.archive()
        val selections = listOf(SelectedCondition("z-future", 2, mapOf("q" to "unknown")), SelectedCondition("a-future", 1, emptyMap()))
        val context = SessionConditionContext(1, 9, selections, selections.associate { it.conditionId to "v9" },
            emptyList(), emptyList(), listOf("z-event", "a-event"))
        val record = base.snapshot.recommendationRecords.first()
        val sessions = base.snapshot.sessions.map { session ->
            if (session.id != record.sessionId) session else session.copy(conditionContext = context,
                exercises = session.exercises.mapIndexed { index, exercise ->
                    if (index != 0) exercise else exercise.copy(prescription = exercise.prescription.copy(purpose = PrescriptionPurpose.SUPPORTIVE))
                })
        }
        val marker = PendingConditionReassessment("default_user", "future", "exercise", "event", "a".repeat(64), 99, 100, "deleted-set")
        val snapshot = base.snapshot.copy(profile = base.snapshot.profile!!.copy(activeConditions = selections,
            unresolvedTrainingConstraintsRaw = "KNEE_SENSITIVE|||FUTURE_SENSITIVE"), sessions = sessions,
            recommendationRecords = base.snapshot.recommendationRecords.map { if (it.sessionId == record.sessionId) it.copy(conditionContextDigest = context.canonicalDigest) else it },
            pendingConditionReassessments = listOf(marker))
        val original = base.copy(snapshot = snapshot)
        assertEquals(original, decode(encode(original)))
    }

    @Test fun downgradeRefusesBeforeOpeningAndOldVersionsRejectNewFields() {
        val base = LocalDataArchiveFixtures.archive()
        for (version in 1..6) {
            val old = base.copy(metadata = base.metadata.copy(archiveVersion = version), snapshot = base.snapshot.copy(
                recommendationRecords = if (version == 1) emptyList() else base.snapshot.recommendationRecords,
                profile = base.snapshot.profile!!.copy(activeConditions = listOf(SelectedCondition("future", 1, emptyMap())))))
            var opened = false
            assertThrows(IllegalArgumentException::class.java) { LocalDataArchiveCodec.write(old) { opened = true; ByteArrayOutputStream() } }
            assertFalse(opened)
            val legacy = old.copy(snapshot = old.snapshot.copy(profile = old.snapshot.profile!!.copy(activeConditions = emptyList())))
            val bytes = encode(legacy)
            assertArrayEquals(bytes, encode(decode(bytes)))
            val smuggled = bytes.decodeToString().replaceFirst("\"trainingConstraints\":", "\"activeConditions\":\"[]\",\"trainingConstraints\":")
            assertThrows(LocalDataArchiveException::class.java) { decode(smuggled.encodeToByteArray()) }
        }
    }

    @Test fun invalidDigestAndOversizedOpaqueRestrictionsRefuseBeforeOpening() {
        val base = LocalDataArchiveFixtures.archive()
        val record = base.snapshot.recommendationRecords.first()
        val invalid = base.copy(snapshot = base.snapshot.copy(recommendationRecords = base.snapshot.recommendationRecords.map {
            if (it.sessionId == record.sessionId) it.copy(conditionContextDigest = "a".repeat(64)) else it
        }))
        val oversized = base.copy(snapshot = base.snapshot.copy(profile = base.snapshot.profile!!.copy(unresolvedTrainingConstraintsRaw = "x".repeat(8193))))
        listOf(invalid, oversized).forEach { archive ->
            var opened = false
            assertThrows(IllegalArgumentException::class.java) { LocalDataArchiveCodec.write(archive) { opened = true; ByteArrayOutputStream() } }
            assertFalse(opened)
        }
    }

    @Test fun manualContextWithoutRecommendationRoundTrips() {
        val base = LocalDataArchiveFixtures.archive()
        val session = base.snapshot.sessions.first()
        val context = SessionConditionContext(1, 1, emptyList(), emptyMap(), emptyList(), emptyList(), emptyList())
        val original = base.copy(snapshot = base.snapshot.copy(
            sessions = base.snapshot.sessions.map { if (it.id == session.id) it.copy(conditionContext = context) else it },
            recommendationRecords = base.snapshot.recommendationRecords.filter { it.sessionId != session.id }))
        assertEquals(original, decode(encode(original)))
    }

    @Test fun malformedAndOversizedConditionJsonAreRejected() {
        val base = encode(LocalDataArchiveFixtures.archive()).decodeToString()
        listOf("{", " ".repeat(1_048_577)).forEach { value ->
            val json = org.json.JSONObject(base)
            json.getJSONObject("data").getJSONObject("profile").put("activeConditions", value)
            assertThrows(LocalDataArchiveException::class.java) { decode(json.toString().encodeToByteArray()) }
        }
    }

    @Test fun mismatchedFrozenRuleReferenceIsRejected() {
        val base = LocalDataArchiveFixtures.archive()
        val session = base.snapshot.sessions.first()
        val reference = ConditionRuleReference("future", "v1", "case", "rule", 1)
        val rule = AppliedConditionRule(reference, ConditionExerciseIdentity("exercise", "a".repeat(64),
            ExerciseType.DURATION, "b".repeat(64)), "execution", PrescriptionPurpose.SUPPORTIVE, ConditionText("en", "es"))
        val context = SessionConditionContext(1, 1, listOf(SelectedCondition("future", 1, emptyMap())),
            mapOf("future" to "v1"), listOf(rule), emptyList(), emptyList())
        val original = base.copy(snapshot = base.snapshot.copy(
            sessions = base.snapshot.sessions.map { if (it.id == session.id) it.copy(conditionContext = context) else it },
            recommendationRecords = base.snapshot.recommendationRecords.filter { it.sessionId != session.id }))
        val document = org.json.JSONObject(encode(original).decodeToString())
        val sessions = document.getJSONObject("data").getJSONArray("sessions")
        val row = (0 until sessions.length()).map { sessions.getJSONObject(it) }.first { it.getString("id") == session.id }
        val frozen = org.json.JSONObject(row.getString("conditionContext"))
        frozen.getJSONObject("sourceSupportVersions").put("future", "v2")
        row.put("conditionContext", frozen.toString())
        assertThrows(LocalDataArchiveException::class.java) { decode(document.toString().encodeToByteArray()) }
    }

    private fun encode(archive: LocalDataArchive): ByteArray = ByteArrayOutputStream().also { LocalDataArchiveCodec.write(archive) { it } }.toByteArray()
    private fun decode(bytes: ByteArray): LocalDataArchive = LocalDataArchiveCodec.read(ByteArrayInputStream(bytes))

}
