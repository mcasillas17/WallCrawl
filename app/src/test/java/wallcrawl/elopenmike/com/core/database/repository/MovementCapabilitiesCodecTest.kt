package wallcrawl.elopenmike.com.core.database.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType

class MovementCapabilitiesCodecTest {

    @Test
    fun completeCapabilities_roundTripWithoutChangingAnyAnswer() {
        val original = MovementCapabilities.from(
            mapOf(
                MovementCapabilityType.IMPACT to CapabilityLevel.COMFORTABLE,
                MovementCapabilityType.FLOOR_TRANSITION to CapabilityLevel.LIMITED,
                MovementCapabilityType.UNSUPPORTED_SQUAT to CapabilityLevel.AVOID,
                MovementCapabilityType.UPPER_BODY_BODYWEIGHT_PUSH to CapabilityLevel.UNKNOWN,
                MovementCapabilityType.VERTICAL_PULL_OR_HANG to CapabilityLevel.COMFORTABLE,
                MovementCapabilityType.BALANCE_WITHOUT_SUPPORT to CapabilityLevel.LIMITED,
                MovementCapabilityType.CONTINUOUS_ACTIVITY to CapabilityLevel.AVOID
            )
        )

        val decoded = MovementCapabilitiesCodec.decode(
            MovementCapabilitiesCodec.encode(original)
        )

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun partialJson_normalizesMissingKnownKeysToUnknown() {
        val decoded = MovementCapabilitiesCodec.decode(
            """{"IMPACT":"LIMITED"}"""
        )

        assertThat(decoded[MovementCapabilityType.IMPACT]).isEqualTo(CapabilityLevel.LIMITED)
        assertThat(decoded[MovementCapabilityType.FLOOR_TRANSITION])
            .isEqualTo(CapabilityLevel.UNKNOWN)
        assertThat(decoded.values).hasSize(MovementCapabilityType.entries.size)
    }

    @Test
    fun malformedOrWrongShapeJson_decodesEveryCapabilityToUnknown() {
        val malformedValues = listOf(
            "",
            "not-json",
            "[]",
            """{"IMPACT":true}""",
            """{"IMPACT":{"level":"LIMITED"}}""",
            """{"IMPACT":"LIMITED",}""",
            """{"IMPACT":"LIMITED"} trailing"""
        )

        malformedValues.forEach { raw ->
            assertAllUnknown(MovementCapabilitiesCodec.decode(raw))
        }
    }

    @Test
    fun oversizedJson_decodesEveryCapabilityToUnknown() {
        val oversized = "{" + "x".repeat(4_096) + "}"

        assertAllUnknown(MovementCapabilitiesCodec.decode(oversized))
    }

    @Test
    fun unknownFutureKeys_areIgnoredWithoutDiscardingKnownAnswers() {
        val decoded = MovementCapabilitiesCodec.decode(
            """{"FUTURE_CAPABILITY":"COMFORTABLE","IMPACT":"AVOID"}"""
        )

        assertThat(decoded[MovementCapabilityType.IMPACT]).isEqualTo(CapabilityLevel.AVOID)
        assertThat(decoded[MovementCapabilityType.FLOOR_TRANSITION])
            .isEqualTo(CapabilityLevel.UNKNOWN)
    }

    @Test
    fun unknownEnumValue_decodesThatKnownCapabilityToUnknown() {
        val decoded = MovementCapabilitiesCodec.decode(
            """{"IMPACT":"EXCELLENT","FLOOR_TRANSITION":"LIMITED"}"""
        )

        assertThat(decoded[MovementCapabilityType.IMPACT]).isEqualTo(CapabilityLevel.UNKNOWN)
        assertThat(decoded[MovementCapabilityType.FLOOR_TRANSITION])
            .isEqualTo(CapabilityLevel.LIMITED)
    }

    @Test
    fun duplicateKeys_areRejectedAsMalformedRatherThanChoosingOneValue() {
        val decoded = MovementCapabilitiesCodec.decode(
            """{"IMPACT":"COMFORTABLE","IMPACT":"AVOID"}"""
        )

        assertAllUnknown(decoded)
    }

    @Test
    fun encode_writesOnlyKnownEnumNamesInStableOrder() {
        val encoded = MovementCapabilitiesCodec.encode(
            MovementCapabilities.from(
                mapOf(MovementCapabilityType.IMPACT to CapabilityLevel.LIMITED)
            )
        )

        assertThat(encoded).isEqualTo(
            "{" +
                "\"IMPACT\":\"LIMITED\"," +
                "\"FLOOR_TRANSITION\":\"UNKNOWN\"," +
                "\"UNSUPPORTED_SQUAT\":\"UNKNOWN\"," +
                "\"UPPER_BODY_BODYWEIGHT_PUSH\":\"UNKNOWN\"," +
                "\"VERTICAL_PULL_OR_HANG\":\"UNKNOWN\"," +
                "\"BALANCE_WITHOUT_SUPPORT\":\"UNKNOWN\"," +
                "\"CONTINUOUS_ACTIVITY\":\"UNKNOWN\"}"
        )
    }

    private fun assertAllUnknown(capabilities: MovementCapabilities) {
        MovementCapabilityType.entries.forEach { type ->
            assertThat(capabilities[type]).isEqualTo(CapabilityLevel.UNKNOWN)
        }
    }
}
