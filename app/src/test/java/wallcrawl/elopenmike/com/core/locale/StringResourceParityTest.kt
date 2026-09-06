package wallcrawl.elopenmike.com.core.locale

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards English and Spanish against drifting apart, in CI.
 *
 * A missing key silently renders English inside an otherwise Spanish screen, and a
 * mismatched format specifier crashes at runtime with an
 * `IllegalFormatException` the moment that string is shown — neither is something a
 * Compose test would reliably catch, because it would have to visit every screen in both
 * languages to find one of them.
 *
 * The files are read directly rather than through `R`, because JVM unit tests have no
 * resource table. This is the same approach `BundledCatalogVocabularyTest` uses for the
 * bundled catalog.
 */
class StringResourceParityTest {

    private val english = StringResources.read(ENGLISH_FILE)
    private val spanish = StringResources.read(SPANISH_FILE)

    @Test
    fun bothResourceFilesWereActuallyRead() {
        // Without this, every assertion below would pass vacuously if the paths were wrong.
        assertThat(ENGLISH_FILE.exists()).isTrue()
        assertThat(SPANISH_FILE.exists()).isTrue()
        assertThat(english.strings.size).isAtLeast(200)
        assertThat(english.plurals.size).isAtLeast(15)
        assertThat(spanish.strings).isNotEmpty()
        assertThat(spanish.plurals).isNotEmpty()
    }

    @Test
    fun spanishTranslatesEveryTranslatableEnglishString() {
        val missing = english.translatableStringKeys - spanish.strings.keys
        assertThat(missing).isEmpty()
    }

    @Test
    fun spanishTranslatesEveryEnglishPlural() {
        val missing = english.plurals.keys - spanish.plurals.keys
        assertThat(missing).isEmpty()
    }

    @Test
    fun spanishAddsNoKeyEnglishDoesNotDefine() {
        // An orphaned Spanish key is dead weight at best; at worst it is a typo that leaves
        // the real key untranslated while looking translated in the diff.
        assertThat(spanish.strings.keys - english.strings.keys).isEmpty()
        assertThat(spanish.plurals.keys - english.plurals.keys).isEmpty()
    }

    @Test
    fun spanishNeverTranslatesAStringMarkedUntranslatable() {
        val wronglyTranslated = english.strings.keys
            .minus(english.translatableStringKeys)
            .intersect(spanish.strings.keys)
        assertThat(wronglyTranslated).isEmpty()
    }

    @Test
    fun everyTranslatedStringUsesTheSameFormatArguments() {
        val mismatched = spanish.strings
            .filterKeys { it in english.strings }
            .filter { (key, value) ->
                formatArguments(value) != formatArguments(english.strings.getValue(key))
            }
            .keys
        assertThat(mismatched).isEmpty()
    }

    @Test
    fun everyTranslatedPluralUsesTheSameFormatArguments() {
        val mismatched = spanish.plurals
            .filterKeys { it in english.plurals }
            .filter { (key, items) ->
                val expected = english.plurals.getValue(key).values
                    .flatMap(::formatArguments)
                    .toSet()
                items.values.any { formatArguments(it) != expected }
            }
            .keys
        assertThat(mismatched).isEmpty()
    }

    @Test
    fun everyPluralDefinesTheQuantitiesItsLanguageNeeds() {
        // Both English and Spanish select between exactly `one` and `other`. A plural that
        // only declares `other` renders the plural form for a single set.
        (english.plurals + spanish.plurals).forEach { (key, items) ->
            assertWithMessage(key).that(items.keys).containsAtLeast("one", "other")
        }
    }

    @Test
    fun noSpanishStringWasLeftAsItsEnglishSource() {
        // Catches a copy-paste that never got translated. Strings that are legitimately
        // identical in both languages (symbols, format-only strings, shared abbreviations)
        // are listed explicitly so a new one has to be a deliberate decision.
        val untranslated = spanish.strings
            .filter { (key, value) ->
                key !in IDENTICAL_BY_DESIGN && value == english.strings[key]
            }
            .keys
        assertThat(untranslated).isEmpty()
    }

    @Test
    fun everyAllowlistedKeyIsReallyIdenticalInBothLanguages() {
        // An exemption for a key that is in fact translated is worse than no exemption:
        // it silently stops guarding that key if the translation is ever lost.
        val notIdentical = IDENTICAL_BY_DESIGN.filter { key ->
            spanish.strings[key] != english.strings[key]
        }
        assertWithMessage("listed as identical by design but not identical")
            .that(notIdentical).isEmpty()
    }

    private fun formatArguments(value: String): Set<String> =
        FORMAT_ARGUMENT.findAll(value).map { it.value }.toSet()

    private class StringResources(
        val strings: Map<String, String>,
        val translatableStringKeys: Set<String>,
        val plurals: Map<String, Map<String, String>>
    ) {
        companion object {
            fun read(file: File): StringResources {
                val document = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = false
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }.newDocumentBuilder().parse(file)

                val strings = mutableMapOf<String, String>()
                val translatable = mutableSetOf<String>()
                document.getElementsByTagName("string").let { nodes ->
                    for (index in 0 until nodes.length) {
                        val element = nodes.item(index) as Element
                        val name = element.getAttribute("name")
                        strings[name] = element.textContent
                        if (element.getAttribute("translatable") != "false") translatable += name
                    }
                }

                val plurals = mutableMapOf<String, Map<String, String>>()
                document.getElementsByTagName("plurals").let { nodes ->
                    for (index in 0 until nodes.length) {
                        val element = nodes.item(index) as Element
                        val items = mutableMapOf<String, String>()
                        val itemNodes = element.getElementsByTagName("item")
                        for (itemIndex in 0 until itemNodes.length) {
                            val item = itemNodes.item(itemIndex) as Element
                            items[item.getAttribute("quantity")] = item.textContent
                        }
                        plurals[element.getAttribute("name")] = items
                    }
                }
                return StringResources(strings, translatable, plurals)
            }
        }
    }

    private companion object {
        val FORMAT_ARGUMENT = Regex("""%\d+\$[sd]""")

        /** Resource root, found by walking up from wherever the test runner started. */
        val RESOURCE_ROOT: File = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/res") }
            .firstOrNull(File::isDirectory)
            ?: File("src/main/res")

        val ENGLISH_FILE = File(RESOURCE_ROOT, "values/strings.xml")
        val SPANISH_FILE = File(RESOURCE_ROOT, "values-es/strings.xml")

        /**
         * Keys whose Spanish text is deliberately identical to the English text: symbols,
         * abbreviations that are used unchanged in Spanish training vocabulary, and strings
         * that are pure layout.
         */
        val IDENTICAL_BY_DESIGN = setOf(
            "value_not_available",
            "answer_no",
            "priority_normal",
            "theme_system_short",
            "weight_unit_lbs_short",
            "weight_unit_kg_short",
            "set_field_reps",
            "movement_pattern_core",
            "movement_capability_option_accessibility",
            "onboarding_summary_value_schedule",
            "onboarding_summary_value_units",
            "onboarding_summary_value_break",
            "generated_workout_title",
            "today_stat_duration",
            "today_exercise_position",
            "editor_position",
            "previous_load",
            "previous_weight_reps",
            "previous_entry",
            "prescription_sets_by_reps",
            "prescription_seconds",
            "prescription_meters",
            "prescription_minutes",
            "prescription_rep_range",
            "rest_add_thirty",
            "rest_countdown",
            "set_field_with_unit",
            "summary_duration_value",
            "template_summary",
            "progress_volume_value",
            "progress_trend_change",
            "progress_percentage",
            "progress_percentage_growth",
            "progress_history_duration",
            "progress_performance_weight_reps",
            "profile_duration_value"
        )
    }
}
