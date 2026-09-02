package wallcrawl.elopenmike.com.core.model

/** Nullable-at-the-prescription-boundary target for repetitions left in reserve. */
data class EffortTarget(
    val minRir: Int,
    val maxRir: Int
) {
    init {
        require(minRir in MIN_RIR..MAX_RIR) {
            "Minimum RIR must be between $MIN_RIR and $MAX_RIR."
        }
        require(maxRir in minRir..MAX_RIR) {
            "Maximum RIR must be between minimum RIR and $MAX_RIR."
        }
    }

    private companion object {
        const val MIN_RIR = 1
        const val MAX_RIR = 10
    }
}

/** Product-policy rest classification; exact seconds remain separately editable. */
enum class RestClass {
    SHORT,
    MODERATE,
    LONG
}

/** Whether a classified rest target came from product policy or an explicit user choice. */
enum class RestTargetSource {
    PRODUCT_POLICY,
    USER_PREFERENCE
}

/** An explicit per-exercise rest choice that may override a future automatic target. */
data class UserRestPreference(
    val restClass: RestClass,
    val restSeconds: Int
) {
    init {
        require(restSeconds in MIN_REST_SECONDS..MAX_REST_SECONDS) {
            "Rest seconds must be between $MIN_REST_SECONDS and $MAX_REST_SECONDS."
        }
    }

    private companion object {
        const val MIN_REST_SECONDS = 0
        const val MAX_REST_SECONDS = 1_800
    }
}
