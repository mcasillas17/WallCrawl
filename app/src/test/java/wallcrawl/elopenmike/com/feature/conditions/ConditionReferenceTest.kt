package wallcrawl.elopenmike.com.feature.conditions

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.Assert.assertThrows

class ConditionReferenceTest {
    private val json = javaClass.classLoader!!.getResourceAsStream("condition-reference/library.json")!!
        .bufferedReader().use { it.readText() }
    private val library = readConditionReference(json)

    @Test fun actualReferenceSupportsBothNamesAliasesAndAccentInsensitiveRegionSearch() {
        val all = library.flatMap { it.entries }
        assertThat(all).hasSize(50)
        assertThat(all.flatMap { it.sources }).hasSize(196)
        assertThat(all.count { it.relatedReading.isNotEmpty() }).isEqualTo(8)
        assertThat(all.single { it.id == "piriformis-syndrome" }.relatedReading.map { it.access })
            .containsAtLeast("full_text", "abstract_only")
        listOf("piriformis", "sindrome del piriforme", "piriformis muscle syndrome").forEach { query ->
            assertThat(library.search(query).flatMap { it.entries }.map { it.id }).containsExactly("piriformis-syndrome")
        }
        assertThat(library.search("gluteos").flatMap { it.entries }).hasSize(7)
        assertThat(library.search("no matching condition")).isEmpty()
    }

    @Test fun invalidSchemesAndCredentialedLinksNeverReachThePublicationButtons() {
        val url = library.first().entries.first().sources.first().url
        listOf("javascript:alert(1)", "http://example.org/article", "https://pubmed.ncbi.nlm.nih.gov@evil.test/article").forEach {
            assertThrows(IllegalArgumentException::class.java) { readConditionReference(json.replace(url, it)) }
        }
    }
}
