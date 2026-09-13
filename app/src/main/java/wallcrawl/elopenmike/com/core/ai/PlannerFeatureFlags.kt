package wallcrawl.elopenmike.com.core.ai

/** Local, dependency-injected planner rollout switches. */
data class PlannerFeatureFlags(
    val reviewedCapabilityEligibility: Boolean = false
) {
    companion object {
        /**
         * The exact composition `WallCrawlApplication` injects.
         *
         * Changing this one value is the whole rollout. `ProductionPlannerCompositionTest`
         * asserts both halves — that this value is `true`, and that the application still
         * injects this constant rather than building its own — so neither route changes the
         * rollout without a failing test. Reviewed planning needs accepted metadata, and the
         * bundled catalog now carries an audited `AI_ACCEPTED` cohort, which is what that
         * suite and `TodayProductionLifecycleTest` plan from end to end.
         */
        val PRODUCTION = PlannerFeatureFlags(reviewedCapabilityEligibility = true)
    }
}
