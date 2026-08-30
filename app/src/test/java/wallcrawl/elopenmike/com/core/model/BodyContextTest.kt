package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BodyContextTest {

    @Test
    fun unknown_containsEveryCapabilityAtTheConservativeLevel() {
        val capabilities = MovementCapabilities.unknown()

        assertThat(capabilities.values.keys)
            .containsExactlyElementsIn(MovementCapabilityType.entries)
        assertThat(capabilities.values.values)
            .containsExactlyElementsIn(
                List(MovementCapabilityType.entries.size) { CapabilityLevel.UNKNOWN }
            )
    }

    @Test
    fun from_normalizesEveryMissingCapabilityToUnknown() {
        val capabilities = MovementCapabilities.from(
            mapOf(MovementCapabilityType.IMPACT to CapabilityLevel.LIMITED)
        )

        assertThat(capabilities[MovementCapabilityType.IMPACT])
            .isEqualTo(CapabilityLevel.LIMITED)
        assertThat(capabilities[MovementCapabilityType.FLOOR_TRANSITION])
            .isEqualTo(CapabilityLevel.UNKNOWN)
        assertThat(capabilities.values).hasSize(MovementCapabilityType.entries.size)
    }

    @Test
    fun from_defensivelyCopiesCallerOwnedMaps() {
        val callerValues = mutableMapOf(
            MovementCapabilityType.IMPACT to CapabilityLevel.COMFORTABLE
        )
        val capabilities = MovementCapabilities.from(callerValues)

        callerValues[MovementCapabilityType.IMPACT] = CapabilityLevel.AVOID

        assertThat(capabilities[MovementCapabilityType.IMPACT])
            .isEqualTo(CapabilityLevel.COMFORTABLE)
    }

    @Test
    fun userProfile_defaultsEveryCapabilityToUnknown() {
        val profile = UserProfile()

        MovementCapabilityType.entries.forEach { type ->
            assertThat(profile.movementCapabilities[type]).isEqualTo(CapabilityLevel.UNKNOWN)
        }
    }
}
