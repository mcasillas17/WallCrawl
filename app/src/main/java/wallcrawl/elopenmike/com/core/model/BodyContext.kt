package wallcrawl.elopenmike.com.core.model

import java.util.Collections

/** Stable persistence identifiers for movement-comfort questions. */
enum class MovementCapabilityType {
    IMPACT,
    FLOOR_TRANSITION,
    UNSUPPORTED_SQUAT,
    UPPER_BODY_BODYWEIGHT_PUSH,
    VERTICAL_PULL_OR_HANG,
    BALANCE_WITHOUT_SUPPORT,
    CONTINUOUS_ACTIVITY
}

/** A user's current answer for one movement capability. */
enum class CapabilityLevel {
    UNKNOWN,
    COMFORTABLE,
    LIMITED,
    AVOID
}

/**
 * A complete, immutable capability set.
 *
 * Callers construct values through [from], which fills every omitted capability with
 * [CapabilityLevel.UNKNOWN]. The stored map is defensively copied and cannot later be
 * changed by a caller that retains its input map.
 */
class MovementCapabilities private constructor(
    val values: Map<MovementCapabilityType, CapabilityLevel>
) {
    operator fun get(type: MovementCapabilityType): CapabilityLevel =
        values[type] ?: CapabilityLevel.UNKNOWN

    override fun equals(other: Any?): Boolean =
        this === other || other is MovementCapabilities && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "MovementCapabilities(<redacted>)"

    companion object {
        fun from(
            values: Map<MovementCapabilityType, CapabilityLevel>
        ): MovementCapabilities = MovementCapabilities(
            Collections.unmodifiableMap(
                MovementCapabilityType.entries.associateWith { type ->
                    values[type] ?: CapabilityLevel.UNKNOWN
                }
            )
        )

        fun unknown(): MovementCapabilities = from(emptyMap())
    }
}
