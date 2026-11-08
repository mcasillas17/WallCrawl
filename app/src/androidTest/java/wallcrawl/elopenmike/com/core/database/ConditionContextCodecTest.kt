package wallcrawl.elopenmike.com.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.repository.ConditionContextCodec
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class ConditionContextCodecTest {
    @Test fun completeContextAtCollectionBoundsRoundTripsAllFrozenEvidence() {
        val selections = (0 until 32).map { index -> SelectedCondition("condition-$index", 1,
            (0 until 32).associate { "answer-$it" to "future-token" }) }
        val reference = ConditionRuleReference("condition-0", "v1", "case", "rule", 1)
        val rule = AppliedConditionRule(reference, ConditionExerciseIdentity("exercise", "a".repeat(64),
            ExerciseType.DURATION, "b".repeat(64)), "execution", PrescriptionPurpose.SUPPORTIVE,
            ConditionText("English", "Español"))
        val value = SessionConditionContext(1, 1, selections, selections.associate { it.conditionId to "v1" },
            List(512) { rule }, List(512) { ConditionScheduleEvidence(reference, null, 100) },
            List(512) { "event-${(511 - it).toString().padStart(3, '0')}" })
        val encoded = ConditionContextCodec.encodeSession(value)
        assertEquals(value, ConditionContextCodec.decodeSession(encoded))
        assertEquals(value.canonicalDigest, ConditionContextCodec.decodeSession(encoded).canonicalDigest)
    }

    @Test fun encodingCannotPersistAContextThatExceedsTheReadTransportBound() {
        val reference = ConditionRuleReference("condition", "v1", "case", "rule", 1)
        val rule = AppliedConditionRule(reference, ConditionExerciseIdentity("exercise", "a".repeat(64),
            ExerciseType.DURATION, "b".repeat(64)), "execution", PrescriptionPurpose.SUPPORTIVE,
            ConditionText("e".repeat(4_096), "s".repeat(4_096)))
        val value = SessionConditionContext(1, 1, listOf(SelectedCondition("condition", 1, emptyMap())),
            mapOf("condition" to "v1"), List(512) { rule }, emptyList(), emptyList())
        assertThrows(IllegalArgumentException::class.java) { ConditionContextCodec.encodeSession(value) }
    }

    @Test fun duplicateNestedFieldsWrongTypesUnknownFieldsAndNoncanonicalFormsReject() {
        listOf(
            """[{"answers":{},"conditionId":"future","conditionId":"future","contextSchemaVersion":1}]""",
            """[{"answers":{"answer":"yes","answer":"no"},"conditionId":"future","contextSchemaVersion":1}]""",
            """[{"answers":{},"conditionId":"future","contextSchemaVersion":"1"}]""",
            """[{"answers":{},"conditionId":"future","contextSchemaVersion":1,"extra":null}]""",
            """[{"answers":{},"conditionId":"future","contextSchemaVersion":1.0}]""",
            " []"
        ).forEach { json -> assertThrows(IllegalArgumentException::class.java) { ConditionContextCodec.decodeSelections(json) } }
        assertThrows(android.util.MalformedJsonException::class.java) { ConditionContextCodec.decodeSelections("[]null") }
        val empty = ConditionContextCodec.encodeSession(SessionConditionContext(1, 1, emptyList(), emptyMap(), emptyList(), emptyList(), emptyList()))
        assertThrows(IllegalArgumentException::class.java) {
            ConditionContextCodec.decodeSession(empty.replace("\"formatVersion\":1", "\"formatVersion\":2"))
        }
    }

    @Test fun selectionsRetainUnknownTokensAndEncodeCanonically() {
        val selection = SelectedCondition("future-condition", 3, mapOf("unknown-answer" to "token", "optional" to null))
        val selections = listOf(selection, SelectedCondition("another-future-condition", 2, emptyMap()))
        val encoded = ConditionContextCodec.encodeSelections(selections)
        assertEquals(selections, ConditionContextCodec.decodeSelections(encoded))
        assertEquals(encoded, ConditionContextCodec.encodeSelections(ConditionContextCodec.decodeSelections(encoded)))
        assertThrows(IllegalArgumentException::class.java) {
            ConditionContextCodec.decodeSelections("""{"formatVersion":1,"formatVersion":1,"selections":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConditionContextCodec.decodeSelections(" ".repeat(1_048_577))
        }
    }

    @Test fun frozenContextRoundTripsWithoutInventingRules() {
        val value = SessionConditionContext(1, 2, listOf(SelectedCondition("future-condition", 1, emptyMap())),
            mapOf("future-condition" to "v1"), emptyList(), emptyList(), emptyList())
        assertEquals(value, ConditionContextCodec.decodeSession(ConditionContextCodec.encodeSession(value)))
        assertEquals(value.canonicalDigest, ConditionContextCodec.decodeSession(ConditionContextCodec.encodeSession(value)).canonicalDigest)
    }
}
