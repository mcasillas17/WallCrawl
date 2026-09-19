package wallcrawl.elopenmike.com.core.model

data class ProgressionProvenance(
    val exerciseId: String,
    val reason: ProgressionReason,
    val axis: ProgressionAxis?,
    val sourceSessionIds: List<String>,
    val baseConfigurationDigest: String
) {
    init {
        require(exerciseId.isRecordableProgressionId())
        require(baseConfigurationDigest.matches(Regex("[a-f0-9]{64}")))
        require(sourceSessionIds.size <= 2 && sourceSessionIds.distinct().size == sourceSessionIds.size)
        require(sourceSessionIds.all { it.isRecordableProgressionId() })
        require((reason == ProgressionReason.ADVANCED) == (axis != null))
        require(reason != ProgressionReason.ADVANCED || sourceSessionIds.size == 2)
    }
}

fun ProgressionDecision.provenance() = ProgressionProvenance(
    exerciseId, reason, axis, sourceSessionIds, baseConfigurationDigest
)

/** Versioned bounded groups in the existing immutable recommendation reason channel. */
object ProgressionReasonCode {
    private const val PREFIX = "${ProgressionDecision.VERSION}."
    const val MAX_EXERCISES = 6
    const val MAX_TOKENS_PER_EXERCISE = 7

    fun encode(reasons: List<ProgressionProvenance>): List<String> {
        require(reasons.size <= MAX_EXERCISES)
        require(reasons.map { it.exerciseId }.distinct().size == reasons.size)
        return reasons.flatMapIndexed { index, reason ->
            val key = "$PREFIX$index"
            listOf(
                key, "$key.EXERCISE:${reason.exerciseId}", "$key.REASON:${reason.reason.name}",
                "$key.AXIS:${reason.axis?.name ?: "NONE"}", "$key.BASIS:${reason.baseConfigurationDigest}"
            ) + reason.sourceSessionIds.mapIndexed { source, id -> "$key.SOURCE$source:$id" }
        }
    }

    fun decode(codes: List<String>): List<ProgressionProvenance> {
        val groups = sortedMapOf<Int, MutableMap<String, String>>()
        codes.filter { it.startsWith(PREFIX) }.forEach { code ->
            val remainder = code.removePrefix(PREFIX)
            val indexText = remainder.substringBefore('.')
            val index = indexText.toIntOrNull()
            require(index != null && index in 0 until MAX_EXERCISES && index.toString() == indexText) {
                "Invalid progression reason index."
            }
            val group = groups.getOrPut(index) { linkedMapOf() }
            val suffix = remainder.substringAfter('.', "")
            val field = if (suffix.isEmpty()) "HEADER" else suffix.substringBefore(':')
            val value = if (suffix.isEmpty()) "" else suffix.substringAfter(':', "")
            require(field in setOf("HEADER", "EXERCISE", "REASON", "AXIS", "BASIS", "SOURCE0", "SOURCE1"))
            require(suffix.isEmpty() || value.isNotBlank())
            require(group.putIfAbsent(field, value) == null) { "Duplicate progression reason field." }
        }
        require(groups.keys.toList() == (0 until groups.size).toList()) {
            "Progression reasons must have contiguous indices."
        }
        val results = groups.values.map { group ->
            require(group.keys.containsAll(listOf("HEADER", "EXERCISE", "REASON", "AXIS", "BASIS")))
            require("SOURCE1" !in group || "SOURCE0" in group)
            ProgressionProvenance(
                exerciseId = group.getValue("EXERCISE"),
                reason = ProgressionReason.valueOf(group.getValue("REASON")),
                axis = group.getValue("AXIS").takeUnless { it == "NONE" }?.let(ProgressionAxis::valueOf),
                sourceSessionIds = listOfNotNull(group["SOURCE0"], group["SOURCE1"]),
                baseConfigurationDigest = group.getValue("BASIS")
            )
        }
        require(results.map { it.exerciseId }.distinct().size == results.size)
        return results
    }
}
