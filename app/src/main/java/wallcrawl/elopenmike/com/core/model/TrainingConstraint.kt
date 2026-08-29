package wallcrawl.elopenmike.com.core.model

/**
 * Safety-relevant limitations a user has confirmed during onboarding.
 *
 * Deterministic planning code uses these to exclude or substitute exercises; they are
 * never inferred, only explicitly confirmed by the user.
 */
enum class TrainingConstraint(val displayName: String) {
    SHOULDER_SENSITIVE("Shoulder sensitive"),
    ELBOW_SENSITIVE("Elbow sensitive"),
    WRIST_SENSITIVE("Wrist sensitive"),
    LOWER_BACK_SENSITIVE("Lower back sensitive"),
    HIP_SENSITIVE("Hip sensitive"),
    KNEE_SENSITIVE("Knee sensitive"),
    LOW_IMPACT_ONLY("Low impact only")
}
