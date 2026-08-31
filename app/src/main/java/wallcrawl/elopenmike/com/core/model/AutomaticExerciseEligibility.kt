package wallcrawl.elopenmike.com.core.model

enum class AdaptationState {
    NEEDS_ONBOARDING,
    UNCALIBRATED,
    INITIATE,
    BUILD,
    DEVELOP,
    HOLD,
    RETURNING,
    DELOAD_OFFERED,
    RECALIBRATE
}

enum class EligibilityReason {
    APPROVED,
    MISSING_APPROVED_METADATA,
    USER_EXCLUDED,
    MISSING_EQUIPMENT,
    CAPABILITY_AVOID,
    HIGH_IMPACT_DISALLOWED,
    UNMAPPED_TRAINING_CONSTRAINT,
    ADVANCED_WHILE_UNCALIBRATED,
    ADVANCED_WHILE_RETURNING
}

sealed interface EligibilityPreference {
    val capability: MovementCapabilityType

    data class Limited(
        override val capability: MovementCapabilityType
    ) : EligibilityPreference

    data class Unknown(
        override val capability: MovementCapabilityType
    ) : EligibilityPreference
}

data class EligibilityDecision(
    val exerciseId: String,
    val eligible: Boolean,
    val reasons: List<EligibilityReason>,
    val preferences: List<EligibilityPreference> = emptyList()
)

enum class AutomaticEligibilityFailure {
    NO_APPROVED_METADATA,
    USER_EXCLUSIONS_REMOVED_ALL,
    EQUIPMENT_REMOVED_ALL,
    CAPABILITIES_REMOVED_ALL,
    TRAINING_CONSTRAINTS_REMOVED_ALL,
    CALIBRATION_COMPLEXITY_REMOVED_ALL,
    NO_ELIGIBLE_CANDIDATES
}

sealed interface AutomaticEligibilityResult {
    val decisions: List<EligibilityDecision>

    data class Candidates(
        val exercises: List<Exercise>,
        override val decisions: List<EligibilityDecision>
    ) : AutomaticEligibilityResult

    data class NoCandidates(
        val failure: AutomaticEligibilityFailure,
        override val decisions: List<EligibilityDecision>
    ) : AutomaticEligibilityResult
}
