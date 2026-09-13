package wallcrawl.elopenmike.com.core.model

/**
 * Stable recommendation-record encoding for structured workout ranking reasons.
 *
 * One reason uses four independently bounded tokens so full catalog exercise IDs fit the
 * existing recommendation record without a schema change.
 */
object WorkoutRankingReasonCode {
    private const val PREFIX = "SUPPORTED_REGRESSION_PREFERENCE_V1."
    private const val PREFERRED = "PREFERRED:"
    private const val SOURCE = "SOURCE:"
    private const val CAPABILITY = "CAPABILITY:"

    fun encode(reasons: List<WorkoutRankingReason>): List<String> =
        reasons.flatMapIndexed { index, reason ->
            when (reason) {
                is WorkoutRankingReason.SupportedRegressionPreference -> listOf(
                    "$PREFIX$index",
                    "$PREFIX$index.$PREFERRED${reason.preferredExerciseId}",
                    "$PREFIX$index.$SOURCE${reason.sourceExerciseId}",
                    "$PREFIX$index.$CAPABILITY${reason.capability.name}"
                )
            }
        }

    fun decode(codes: List<String>): List<WorkoutRankingReason> {
        val structuredCodes = codes.filter { it.startsWith(PREFIX) }
        if (structuredCodes.isEmpty()) return emptyList()

        val groups = linkedMapOf<Int, MutableMap<String, String>>()
        structuredCodes.forEach { code ->
            val remainder = code.removePrefix(PREFIX)
            val indexText = remainder.substringBefore('.')
            val index = indexText.toIntOrNull()
                ?.takeIf { it >= 0 }
                ?: throw IllegalArgumentException("Invalid supported-regression reason index.")
            val group = groups.getOrPut(index) { linkedMapOf() }
            if (remainder == indexText) {
                require(group.putIfAbsent("header", "") == null) {
                    "Duplicate supported-regression reason header."
                }
                return@forEach
            }

            val field = remainder.substringAfter('.', missingDelimiterValue = "")
            val (name, value) = when {
                field.startsWith(PREFERRED) -> "preferred" to field.removePrefix(PREFERRED)
                field.startsWith(SOURCE) -> "source" to field.removePrefix(SOURCE)
                field.startsWith(CAPABILITY) -> "capability" to field.removePrefix(CAPABILITY)
                else -> throw IllegalArgumentException("Unknown supported-regression reason field.")
            }
            require(value.isNotBlank()) { "Supported-regression reason values must not be blank." }
            require(group.putIfAbsent(name, value) == null) {
                "Duplicate supported-regression reason field."
            }
        }

        require(groups.keys.sorted() == (0 until groups.size).toList()) {
            "Supported-regression reason indices must be contiguous."
        }
        val rawReasons = groups.toSortedMap().values.map { group ->
            require(group.keys == setOf("header", "preferred", "source", "capability")) {
                "Supported-regression reason fields are incomplete."
            }
            require(group.getValue("preferred") != group.getValue("source")) {
                "A supported regression preference needs distinct endpoints."
            }
            Triple(
                group.getValue("preferred"),
                group.getValue("source"),
                group.getValue("capability")
            )
        }
        require(rawReasons.size == rawReasons.distinct().size) {
            "Supported-regression reasons must be unique."
        }
        return rawReasons.mapNotNull { (preferred, source, capabilityName) ->
            // Recommendation records preserve version-like values as text. A future app may
            // add a capability without changing the archive envelope, so older builds retain
            // that opaque reason code while omitting only the explanation they cannot render.
            val capability = MovementCapabilityType.entries.firstOrNull {
                it.name == capabilityName
            } ?: return@mapNotNull null
            WorkoutRankingReason.SupportedRegressionPreference(
                preferredExerciseId = preferred,
                sourceExerciseId = source,
                capability = capability
            )
        }
    }
}
