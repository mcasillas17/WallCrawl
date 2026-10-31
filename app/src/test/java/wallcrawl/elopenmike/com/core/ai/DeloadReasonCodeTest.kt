package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.DeloadReasonCode
import wallcrawl.elopenmike.com.core.model.DeloadRecommendationProvenance
import wallcrawl.elopenmike.com.core.model.DeloadSource

class DeloadReasonCodeTest {
    @Test
    fun sourceChoiceAndVersionSurviveWithoutReconstructingTheOldProfile() {
        val original = DeloadRecommendationProvenance(2, "offer", DeloadSource.RETURNING)
        assertThat(DeloadReasonCode.decode(DeloadReasonCode.encode(original))).isEqualTo(original)
        assertThat(DeloadReasonCode.decode(emptyList())).isNull()
        assertThat(DeloadReasonCode.decode(listOf("DELOAD_ONE_WORKOUT_V2.REVISION:3"))).isNull()
    }

    @Test
    fun malformedKnownProvenanceFailsRatherThanBecomingNoAcceptedChoice() {
        val invalid = listOf(
            listOf("DELOAD_ONE_WORKOUT_V1.ACCEPTED:offer"),
            listOf("DELOAD_ONE_WORKOUT_V1.REVISION:-1"),
            listOf("DELOAD_ONE_WORKOUT_V1.REVISION:0", "DELOAD_ONE_WORKOUT_V1.ACCEPTED:offer", "DELOAD_ONE_WORKOUT_V1.SOURCE:RETURNING"),
            listOf("DELOAD_ONE_WORKOUT_V1.REVISION:2", "DELOAD_ONE_WORKOUT_V1.REVISION:2"),
            listOf("DELOAD_ONE_WORKOUT_V1.REVISION:2", "DELOAD_ONE_WORKOUT_V1.ACCEPTED:offer"),
            listOf("DELOAD_ONE_WORKOUT_V1.REVISION:2", "DELOAD_ONE_WORKOUT_V1.SOURCE:RETURNING"),
            listOf("DELOAD_ONE_WORKOUT_V1.REVISION:2", "DELOAD_ONE_WORKOUT_V1.EXTRA:x")
        )
        invalid.forEach {
            assertThrows(IllegalArgumentException::class.java) { DeloadReasonCode.decode(it) }
        }
    }
}
