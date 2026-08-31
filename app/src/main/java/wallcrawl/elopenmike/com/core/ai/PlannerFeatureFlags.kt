package wallcrawl.elopenmike.com.core.ai

/** Local, dependency-injected planner rollout switches. Production uses the defaults. */
data class PlannerFeatureFlags(
    val reviewedCapabilityEligibility: Boolean = false
)
