package wallcrawl.elopenmike.com.core.model

/**
 * Stable recommendation-record encoding for structured workout ranking reasons.
 *
 * Reasons use independently bounded tokens so full catalog exercise IDs fit the
 * existing recommendation record without a schema change.
 */
object WorkoutRankingReasonCode {
    private const val PREFIX = "SUPPORTED_REGRESSION_PREFERENCE_V1."
    private const val SCHEDULING = "TRAINING_FREQUENCY_RECENCY_V1."
    private const val PREFERRED = "PREFERRED:"
    private const val SOURCE = "SOURCE:"
    private const val CAPABILITY = "CAPABILITY:"

    fun encode(reasons: List<WorkoutRankingReason>): List<String> =
        reasons.flatMapIndexed { index, reason ->
            when (reason) {
                is WorkoutRankingReason.TrainingFrequencyRecencyPreference -> listOf(
                    "$SCHEDULING$index",
                    "$SCHEDULING$index.PREFERRED:${reason.preferredExerciseId}",
                    "$SCHEDULING$index.ALTERNATIVE:${reason.alternativeExerciseId}",
                    "$SCHEDULING$index.PRIMARY:${reason.directPrimaryMuscle}",
                    "$SCHEDULING$index.FREQUENCY:${reason.daysPerWeek}",
                    "$SCHEDULING$index.ELAPSED:${reason.daysSinceLastPractice}"
                )
                is WorkoutRankingReason.SupportedRegressionPreference -> listOf(
                    "$PREFIX$index",
                    "$PREFIX$index.$PREFERRED${reason.preferredExerciseId}",
                    "$PREFIX$index.$SOURCE${reason.sourceExerciseId}",
                    "$PREFIX$index.$CAPABILITY${reason.capability.name}"
                )
            }
        }

    fun decode(codes: List<String>): List<WorkoutRankingReason> {
        val structuredCodes = codes.filter { it.startsWith(PREFIX) || it.startsWith(SCHEDULING) }
        if (structuredCodes.isEmpty()) return emptyList()

        val groups = linkedMapOf<Int, MutableMap<String, String>>()
        structuredCodes.forEach { code ->
            val prefix = if (code.startsWith(PREFIX)) PREFIX else SCHEDULING
            val remainder = code.removePrefix(prefix)
            val indexText = remainder.substringBefore('.')
            val index = indexText.toIntOrNull()
                ?.takeIf { it >= 0 }
                ?: throw IllegalArgumentException("Invalid ranking reason index.")
            val group = groups.getOrPut(index) { linkedMapOf() }
            val previousKind = group.putIfAbsent("kind", prefix)
            require(previousKind == null || previousKind == prefix) { "Mixed reason kinds share an index." }
            if (remainder == indexText) {
                require(group.putIfAbsent("header", "") == null) {
                    "Duplicate ranking reason header."
                }
                return@forEach
            }

            val field = remainder.substringAfter('.', missingDelimiterValue = "")
            val (name, value) = when {
                field.startsWith(PREFERRED) -> "preferred" to field.removePrefix(PREFERRED)
                prefix == PREFIX && field.startsWith(SOURCE) -> "source" to field.removePrefix(SOURCE)
                prefix == PREFIX && field.startsWith(CAPABILITY) -> "capability" to field.removePrefix(CAPABILITY)
                prefix == SCHEDULING && field.startsWith("ALTERNATIVE:") -> "alternative" to field.removePrefix("ALTERNATIVE:")
                prefix == SCHEDULING && field.startsWith("PRIMARY:") -> "primary" to field.removePrefix("PRIMARY:")
                prefix == SCHEDULING && field.startsWith("FREQUENCY:") -> "frequency" to field.removePrefix("FREQUENCY:")
                prefix == SCHEDULING && field.startsWith("ELAPSED:") -> "elapsed" to field.removePrefix("ELAPSED:")
                else -> throw IllegalArgumentException("Unknown ranking reason field.")
            }
            require(value.isNotBlank()) { "Ranking reason values must not be blank." }
            require(group.putIfAbsent(name, value) == null) {
                "Duplicate ranking reason field."
            }
        }

        // Future version groups stay opaque, but their headers may occupy mixed-list indices.
        val opaqueIndices = codes.filterNot { it.startsWith(PREFIX) || it.startsWith(SCHEDULING) }
            .mapNotNull { FUTURE_HEADER.matchEntire(it)?.groupValues?.get(2)?.toIntOrNull() }.toSet()
        // Do not validate a future version's indexing convention. Its header can bridge
        // known groups, but an opaque tail or overlapping index does not corrupt V1 data.
        val lastKnownIndex = groups.keys.max()
        val allIndices = groups.keys + opaqueIndices.filter { it <= lastKnownIndex }
        require(allIndices.sorted() == (0 until allIndices.size).toList()) {
            "Ranking reason indices must be contiguous."
        }
        val rawReasons = groups.toSortedMap().values.toList()
        require(rawReasons.size == rawReasons.distinct().size) {
            "Ranking reasons must be unique."
        }
        return rawReasons.mapNotNull { group ->
            if (group.getValue("kind") == SCHEDULING) {
                require(group.keys == setOf("kind", "header", "preferred", "alternative", "primary", "frequency", "elapsed")) {
                    "Scheduling reason fields are incomplete."
                }
                return@mapNotNull WorkoutRankingReason.TrainingFrequencyRecencyPreference(
                    group.getValue("preferred"), group.getValue("alternative"), group.getValue("primary"),
                    requireNotNull(group.getValue("frequency").toIntOrNull()),
                    requireNotNull(group.getValue("elapsed").toIntOrNull())
                )
            }
            require(group.keys == setOf("kind", "header", "preferred", "source", "capability")) {
                "Supported-regression reason fields are incomplete."
            }
            val preferred = group.getValue("preferred")
            val source = group.getValue("source")
            val capabilityName = group.getValue("capability")
            require(preferred != source) { "A supported regression preference needs distinct endpoints." }
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

    private val FUTURE_HEADER =
        Regex("(SUPPORTED_REGRESSION_PREFERENCE|TRAINING_FREQUENCY_RECENCY)_V[0-9]+\\.([0-9]+)")
}
