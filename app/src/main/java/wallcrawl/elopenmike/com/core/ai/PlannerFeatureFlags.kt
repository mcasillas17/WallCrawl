package wallcrawl.elopenmike.com.core.ai

/** Local, dependency-injected planner rollout switches. */
data class PlannerFeatureFlags(
    val reviewedCapabilityEligibility: Boolean = false
) {
    companion object {
        /**
         * The exact composition `WallCrawlApplication` injects.
         *
         * Enabling reviewed planning is a deliberate edit to this one value.
         * `FixedAnchorBandEligibilityTest` asserts both halves — that this value is `false`, and
         * that the application still injects this constant rather than building its own — so
         * neither route flips the rollout without a failing test. Reviewed planning needs
         * human-approved metadata first; the bundled catalog has none, so an enabled flag would
         * refuse every automatic workout.
         */
        val PRODUCTION = PlannerFeatureFlags(reviewedCapabilityEligibility = false)
    }
}
