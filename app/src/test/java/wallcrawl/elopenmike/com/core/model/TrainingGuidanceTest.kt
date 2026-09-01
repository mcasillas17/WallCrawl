package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TrainingGuidanceTest {

    @Test
    fun effortTarget_acceptsOrderedNonFailureRirRange() {
        assertThat(EffortTarget(minRir = 2, maxRir = 4))
            .isEqualTo(EffortTarget(minRir = 2, maxRir = 4))
    }

    @Test
    fun effortTarget_rejectsFailureAndUnorderedRanges() {
        assertThrows(IllegalArgumentException::class.java) {
            EffortTarget(minRir = 0, maxRir = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EffortTarget(minRir = 4, maxRir = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EffortTarget(minRir = 1, maxRir = 11)
        }
    }

    @Test
    fun userRestPreference_acceptsThePrescriptionRange() {
        assertThat(UserRestPreference(RestClass.SHORT, restSeconds = 0).restSeconds)
            .isEqualTo(0)
        assertThat(UserRestPreference(RestClass.LONG, restSeconds = 1_800).restSeconds)
            .isEqualTo(1_800)
    }

    @Test
    fun userRestPreference_rejectsSecondsOutsideThePrescriptionRange() {
        listOf(-1, 1_801).forEach { invalidSeconds ->
            assertThrows(IllegalArgumentException::class.java) {
                UserRestPreference(RestClass.MODERATE, restSeconds = invalidSeconds)
            }
        }
    }
}
