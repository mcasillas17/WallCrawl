package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.dao.ConditionOutcomeRow
import wallcrawl.elopenmike.com.core.database.dao.conditionOutcomeDigest
import wallcrawl.elopenmike.com.core.model.conditionDigest

class ConditionOutcomeDigestTest {
    @Test fun streamingPreservesCanonicalBytesIncludingEscapesAndEveryOutcomeField() {
        val row = ConditionOutcomeRow("set\\\"\n💪", "session", "exercise", 1, true, 2, null, null,
            "COMPLETED", 3, "SUPPORTIVE", 8, 10.5, 2.5, 20, 100.5, 5.5f, 2, true)
        val changes = listOf(
            row.copy(setId = "other"), row.copy(sessionId = "other"), row.copy(exerciseId = "other"),
            row.copy(sessionStartedAtTimestamp = 2), row.copy(isCompleted = false),
            row.copy(completedAtTimestamp = null), row.copy(stoppedAtTimestamp = 3), row.copy(stopReason = "PAIN_STOP"),
            row.copy(sessionStatus = "CANCELLED"), row.copy(sessionCompletedAt = 4), row.copy(purpose = "ORDINARY"),
            row.copy(completedReps = 9), row.copy(completedWeight = 11.5), row.copy(completedAssistanceWeight = 3.5),
            row.copy(completedDurationSeconds = 21), row.copy(completedDistanceMeters = 101.5),
            row.copy(rpe = 6.5f), row.copy(rir = 3), row.copy(feltManageable = false)
        )
        (listOf(emptyList(), listOf(row), listOf(row, row)) + changes.map { listOf(row, it) }).forEach { rows ->
            assertThat(conditionOutcomeDigest(rows.asSequence())).isEqualTo(conditionDigest(rows.map { it.toString() }))
        }
        changes.forEach { changed ->
            assertThat(conditionOutcomeDigest(sequenceOf(changed))).isNotEqualTo(conditionOutcomeDigest(sequenceOf(row)))
        }
        assertThat(conditionOutcomeDigest(sequenceOf(row, changes.first())))
            .isNotEqualTo(conditionOutcomeDigest(sequenceOf(changes.first(), row)))
    }
}
