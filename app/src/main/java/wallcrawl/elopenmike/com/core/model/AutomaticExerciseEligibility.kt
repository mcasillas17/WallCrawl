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

/**
 * Why one exercise was allowed into, or kept out of, automatic planning.
 *
 * `APPROVED` and `MISSING_APPROVED_METADATA` are named for the human-only review state that
 * predates `AI_ACCEPTED`, and both names are frozen because they are reported outward. They
 * mean accepted and not accepted: an AI-accepted record reports `APPROVED`, and a draft or a
 * record whose provenance does not match its state reports `MISSING_APPROVED_METADATA`.
 */
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
    /** No candidate carried accepted reviewed metadata; the name is frozen, see above. */
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
