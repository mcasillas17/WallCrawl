package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.ProgressionAxis
import wallcrawl.elopenmike.com.core.model.ProgressionProvenance
import wallcrawl.elopenmike.com.core.model.ProgressionReason
import wallcrawl.elopenmike.com.core.model.ProgressionReasonCode

class ProgressionReasonCodeTest {
    private val provenance = ProgressionProvenance(
        exerciseId = "exercise", reason = ProgressionReason.ADVANCED,
        axis = ProgressionAxis.LOAD, sourceSessionIds = listOf("first", "second"),
        baseConfigurationDigest = "a".repeat(64)
    )

    @Test
    fun roundTrip_preservesExactDecisionWithoutDuplicatingMeasurements() {
        val codes = ProgressionReasonCode.encode(listOf(provenance))
        assertThat(codes).hasSize(7)
        assertThat(ProgressionReasonCode.decode(codes)).containsExactly(provenance)
        assertThat(ProgressionReasonCode.decode(codes + "UNRECOGNIZED_V2")).containsExactly(provenance)
    }

    @Test
    fun holdAndOldRecordsRemainExplicit() {
        val hold = provenance.copy(reason = ProgressionReason.HOLD_MISSING_EFFORT, axis = null, sourceSessionIds = emptyList())
        assertThat(ProgressionReasonCode.decode(ProgressionReasonCode.encode(listOf(hold))))
            .containsExactly(hold)
        assertThat(ProgressionReasonCode.decode(listOf("WEEKLY_ALLOWANCE_EXCEEDED"))).isEmpty()
        assertThat(ProgressionReasonCode.decode(listOf("ONE_VARIABLE_PROGRESSION_V2.0"))).isEmpty()
    }

    @Test
    fun malformedCurrentGroupsAreRejectedNotPartiallyExplained() {
        val codes = ProgressionReasonCode.encode(listOf(provenance))
        val malformed = listOf(
            codes.drop(1), codes + codes.first(),
            codes.map { it.replace(".0", ".2") },
            codes + "ONE_VARIABLE_PROGRESSION_V1.0.EXTRA:unexpected",
            codes.filterNot { it.contains("BASIS:") },
            codes.map { if (it.contains("AXIS:")) it.substringBefore("AXIS:") + "AXIS:NONE" else it },
            codes.map { if (it.contains("SOURCE1:")) it.substringBefore("SOURCE1:") + "SOURCE1:first" else it }
        )
        malformed.forEach {
            assertThrows(IllegalArgumentException::class.java) { ProgressionReasonCode.decode(it) }
        }
    }
}
