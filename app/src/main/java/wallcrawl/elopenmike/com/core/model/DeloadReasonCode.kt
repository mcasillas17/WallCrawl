package wallcrawl.elopenmike.com.core.model

data class DeloadRecommendationProvenance(
    val revision: Long,
    val acceptedOfferId: String? = null,
    val source: DeloadSource? = null
) {
    init {
        require(revision >= 0)
        require((acceptedOfferId == null) == (source == null))
        acceptedOfferId?.let {
            require(revision > 0)
            requireDeloadToken(it)
        }
    }
}

object DeloadReasonCode {
    private const val PREFIX = "$DELOAD_POLICY_VERSION."
    const val MAX_TOKENS = 3

    fun encode(value: DeloadRecommendationProvenance?): List<String> =
        if (value == null) emptyList() else listOf("$PREFIX" + "REVISION:${value.revision}") +
            listOfNotNull(
                value.acceptedOfferId?.let { "$PREFIX" + "ACCEPTED:$it" },
                value.source?.let { "$PREFIX" + "SOURCE:${it.name}" }
            )

    fun decode(codes: List<String>): DeloadRecommendationProvenance? {
        val known = codes.filter { it.startsWith(PREFIX) }
        if (known.isEmpty()) return null
        val fields = linkedMapOf<String, String>()
        known.forEach { code ->
            val field = code.removePrefix(PREFIX).substringBefore(':')
            val value = code.substringAfter(':', "")
            require(field in setOf("REVISION", "ACCEPTED", "SOURCE") && value.isNotBlank())
            require(fields.putIfAbsent(field, value) == null) { "Duplicate deload provenance field." }
        }
        val text = requireNotNull(fields["REVISION"]) { "Deload provenance requires its decision revision." }
        val revision = requireNotNull(text.toLongOrNull()) { "Invalid deload decision revision." }
        require(text == revision.toString())
        return DeloadRecommendationProvenance(
            revision, fields["ACCEPTED"], fields["SOURCE"]?.let(DeloadSource::valueOf)
        )
    }
}
