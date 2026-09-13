package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkoutRankingReasonCodeTest {

    @Test
    fun encodeAndDecode_roundTripsMultipleReasons() {
        val reasons = listOf(
            reason("target-a", "source-a", MovementCapabilityType.FLOOR_TRANSITION),
            reason("target-b", "source-b", MovementCapabilityType.BALANCE_WITHOUT_SUPPORT)
        )

        assertThat(WorkoutRankingReasonCode.decode(WorkoutRankingReasonCode.encode(reasons)))
            .containsExactlyElementsIn(reasons)
            .inOrder()
    }

    @Test
    fun decode_rejectsMalformedStructuredGroups() {
        listOf(
            listOf("SUPPORTED_REGRESSION_PREFERENCE_V1.x"),
            listOf("SUPPORTED_REGRESSION_PREFERENCE_V1.-1"),
            listOf(
                "SUPPORTED_REGRESSION_PREFERENCE_V1.0",
                "SUPPORTED_REGRESSION_PREFERENCE_V1.0"
            ),
            validCodes() + "SUPPORTED_REGRESSION_PREFERENCE_V1.0.SOURCE:duplicate",
            validCodes() + "SUPPORTED_REGRESSION_PREFERENCE_V1.0.UNKNOWN:value",
            validCodes().map { it.replace("PREFERRED:target", "PREFERRED:") },
            validCodes().map { it.replace(".0", ".1") },
            validCodes().dropLast(1),
            validCodes()
                .map { it.replace("SOURCE:source", "SOURCE:target") }
                .map {
                    it.replace(
                        "CAPABILITY:FLOOR_TRANSITION",
                        "CAPABILITY:FUTURE_CAPABILITY"
                    )
                },
            WorkoutRankingReasonCode.encode(
                listOf(
                    reason("target", "source", MovementCapabilityType.FLOOR_TRANSITION),
                    reason("target", "source", MovementCapabilityType.FLOOR_TRANSITION)
                )
            ),
            WorkoutRankingReasonCode.encode(
                listOf(
                    reason("target", "source", MovementCapabilityType.FLOOR_TRANSITION),
                    reason("target", "source", MovementCapabilityType.FLOOR_TRANSITION)
                )
            ).map {
                it.replace(
                    "CAPABILITY:FLOOR_TRANSITION",
                    "CAPABILITY:FUTURE_CAPABILITY"
                )
            }
        ).forEach { codes ->
            assertThrows(IllegalArgumentException::class.java) {
                WorkoutRankingReasonCode.decode(codes)
            }
        }
    }

    @Test
    fun decode_preservesForwardCompatibilityForUnknownCapabilities() {
        val futureCodes = validCodes().map {
            it.replace("CAPABILITY:FLOOR_TRANSITION", "CAPABILITY:FUTURE_CAPABILITY")
        }

        assertThat(WorkoutRankingReasonCode.decode(futureCodes)).isEmpty()
    }

    private fun validCodes(): List<String> = WorkoutRankingReasonCode.encode(
        listOf(reason("target", "source", MovementCapabilityType.FLOOR_TRANSITION))
    )

    private fun reason(
        targetId: String,
        sourceId: String,
        capability: MovementCapabilityType
    ): WorkoutRankingReason = WorkoutRankingReason.SupportedRegressionPreference(
        preferredExerciseId = targetId,
        sourceExerciseId = sourceId,
        capability = capability
    )
}
