package wallcrawl.elopenmike.com.feature.conditions

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalization

internal data class ReferenceNames(val en: String, val es: String) {
    fun display(spanish: Boolean) = if (spanish) es else en
}

internal data class ReferenceRegion(val names: ReferenceNames, val entries: List<ReferenceCondition>)

internal data class ReferenceCondition(
    val id: String,
    val names: ReferenceNames,
    val aliases: List<String>,
    val sources: List<ReferenceSource>,
    val relatedReading: List<ReadingCitation>
)

internal data class ReadingCitation(val title: String, val url: String, val access: String, val exerciseIds: List<String>)

internal data class ReferenceSource(
    val id: String,
    val title: String,
    val url: String,
    val fullText: Boolean,
    val population: String,
    val findings: String,
    val limitations: String,
    val exerciseIds: List<String>,
    val executionNote: String?
)

internal fun readConditionReference(json: String): List<ReferenceRegion> {
    fun JSONArray.strings() = List(length()) { getString(it) }
    fun JSONObject.referenceNames() = ReferenceNames(getString("en"), getString("es"))
    fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T) = List(length()) { transform(getJSONObject(it)) }
    fun JSONObject.publicationUrl() = getString("url").also { value ->
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && (uri.port == -1 || uri.port == 443))
    }
    val ids = mutableSetOf<String>()
    return JSONArray(json).mapObjects { group ->
        ReferenceRegion(group.referenceNames(), group.getJSONArray("entries").mapObjects { entry ->
            val id = entry.getString("id")
            require(id.isNotBlank() && ids.add(id))
            ReferenceCondition(id, entry.referenceNames(), entry.getJSONArray("aliases").strings(),
                entry.getJSONArray("sources").mapObjects { source ->
                    val url = source.publicationUrl()
                    val access = source.getString("access")
                    require(access == "full-text" || access == "abstract-only")
                    ReferenceSource(source.getString("id"), source.getString("title"), url, access == "full-text",
                        source.getString("population"), source.getString("findings"), source.getString("limitations"),
                        source.getJSONArray("exerciseIds").strings(), source.optString("executionNote").takeIf { it.isNotBlank() })
                }, entry.getJSONArray("relatedReading").mapObjects { ref ->
                    val access = ref.getString("access")
                    require(access in setOf("full_text", "abstract_only", "registry_or_summary"))
                    ReadingCitation(ref.getString("title"), ref.publicationUrl(), access, ref.optJSONArray("exerciseIds")?.strings().orEmpty())
                })
        })
    }.also { require(ids.isNotEmpty()) }
}

internal fun List<ReferenceRegion>.search(query: String): List<ReferenceRegion> {
    val term = ExerciseLocalization.normalizeForSearch(query.trim())
    if (term.isEmpty()) return this
    fun matches(text: String) = ExerciseLocalization.normalizeForSearch(text).contains(term)
    return mapNotNull { region ->
        if (matches(region.names.en) || matches(region.names.es)) region else {
            val entries = region.entries.filter { matches(it.names.en) || matches(it.names.es) || it.aliases.any(::matches) }
            if (entries.isEmpty()) null else region.copy(entries = entries)
        }
    }
}
