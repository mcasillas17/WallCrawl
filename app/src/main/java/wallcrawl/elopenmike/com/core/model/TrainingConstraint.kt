package wallcrawl.elopenmike.com.core.model

/**
 * Safety-relevant limitations a user has confirmed during onboarding.
 *
 * Deterministic planning code uses these to exclude or substitute exercises; they are
 * never inferred, only explicitly confirmed by the user. The enum name is the persisted
 * identifier and never changes; the label shown on screen is a string resource chosen by
 * `wallcrawl.elopenmike.com.core.ui.localization.DomainLabels`.
 */
enum class TrainingConstraint {
    SHOULDER_SENSITIVE,
    ELBOW_SENSITIVE,
    WRIST_SENSITIVE,
    LOWER_BACK_SENSITIVE,
    HIP_SENSITIVE,
    KNEE_SENSITIVE,
    LOW_IMPACT_ONLY
}
