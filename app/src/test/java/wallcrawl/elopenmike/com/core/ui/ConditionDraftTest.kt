package wallcrawl.elopenmike.com.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.SelectedCondition

class ConditionDraftTest {
    @Test fun canonicalDraftRetainsUnknownSchemaTokensNullAndResponseOnlyAnswers() {
        val selected = listOf(SelectedCondition("withdrawn", 8, mapOf("side" to null, "response" to "future-token")))
        assertThat(decodeConditionDraft(encodeConditionDraft(selected))).isEqualTo(selected)
        assertThat(decodeConditionDraft(encodeConditionDraft(emptyList()))).isEmpty()
    }

    @Test fun fullAnswerBudgetRoundTripsUnknownAndNullFactsWithoutTruncation() {
        val answers = (1..32).associate { "unknown-$it" to if (it % 2 == 0) null else "future" }
        val selected = listOf(SelectedCondition("retained", 8, answers))
        assertThat(decodeConditionDraft(encodeConditionDraft(selected))).isEqualTo(selected)
        assertThrows(IllegalArgumentException::class.java) {
            decodeConditionDraft(listOf("retained:8:" + (answers + ("extra" to "answer")).toSortedMap()
                .entries.joinToString(":") { "${it.key}=${it.value.orEmpty()}" }))
        }
    }

    @Test fun corruptedOrOversizedSavedStateIsNotSilentlyNormalized() {
        listOf(listOf("condition:1:a=first:a=second"), listOf("condition:01:a=answer"),
            listOf("condition:1:missing-separator"), List(33) { "condition-$it:1" }).forEach { rows ->
            assertThrows(IllegalArgumentException::class.java) { decodeConditionDraft(rows) }
        }
    }
}
