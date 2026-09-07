package wallcrawl.elopenmike.com.core.locale

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Test
import org.w3c.dom.Element

/**
 * Holds every language to the same claim boundary.
 *
 * A translation is the easiest place for a safety claim to grow: "conservative volume"
 * becomes "protects your joints", and nothing in the build notices. These checks run over
 * the shipped resource files in both languages, so wording that promises injury prevention,
 * a diagnosis, or a medical outcome fails here rather than shipping.
 *
 * The forbidden lists are the vocabulary of a claim WallCrawl does not make, not a
 * blocklist of unpleasant words: `docs/architecture.md` and `ROADMAP.md` set the boundary
 * these enforce — product defaults, never physiological or medical assertions.
 */
class SafetyCopyTest {

    private val english = readStrings(ENGLISH_FILE)
    private val spanish = readStrings(SPANISH_FILE)
    private val translatedExerciseText = readTranslatedExerciseText(OVERLAY_FILE)

    @Test
    fun bothResourceFilesWereActuallyRead() {
        assertThat(english).isNotEmpty()
        assertThat(spanish).isNotEmpty()
        assertThat(english.keys).containsAtLeast(
            "stop_reason_pain",
            "break_guidance_hiatus",
            "generated_rationale_reentry"
        )
    }

    @Test
    fun aStopReasonSaysOnlyThatTheUserStoppedInEveryLanguage() {
        val stopReasons = listOf(
            "stop_reason_user_skipped",
            "stop_reason_pain",
            "stop_reason_equipment",
            "stop_reason_time",
            "stop_reason_other"
        )
        listOf(english, spanish).forEach { strings ->
            stopReasons.forEach { key ->
                val value = strings.getValue(key)
                assertWithMessage(key).that(value).isNotEmpty()
                assertNoDiagnosticLanguage(key, value)
            }
        }
    }

    @Test
    fun noTrainingCopyPromisesAMedicalOutcomeInEitherLanguage() {
        // Every string that talks about volume, breaks, or joints. These describe what
        // WallCrawl plans, never what it will do to a body.
        val claimSensitiveKeys = english.keys.filter { key ->
            key.startsWith("break_guidance_") ||
                key.startsWith("break_range_") ||
                key.startsWith("generated_rationale_") ||
                key.startsWith("onboarding_break_") ||
                key.startsWith("onboarding_safety_") ||
                key.startsWith("profile_break_") ||
                key.startsWith("movement_capability_") ||
                // Planning refusals explain a configured product limit. "This week's planned
                // sets are already covered" is one sentence away from "more would hurt you",
                // and that sentence must never ship in either language.
                key.startsWith("today_error_")
        }
        assertThat(claimSensitiveKeys).isNotEmpty()

        listOf(english, spanish).forEach { strings ->
            claimSensitiveKeys.forEach { key ->
                strings[key]?.let { value -> assertNoMedicalPromise(key, value) }
            }
        }
    }

    @Test
    fun noTranslatedExerciseTextPromisesAMedicalOutcome() {
        // The overlay is the other half of the shipped Spanish: 302 names and 131 coaching
        // summaries that reach the same screens as the resource strings, and that the
        // resource-file checks above cannot see.
        assertThat(translatedExerciseText).isNotEmpty()
        translatedExerciseText.forEach { (key, value) -> assertNoMedicalPromise(key, value) }
    }

    private fun assertNoDiagnosticLanguage(key: String, value: String) {
        val forbidden = listOf(
            // English
            "injur", "diagnos", "symptom", "medical", "pain level",
            // Spanish
            "lesión", "lesion", "diagnóst", "diagnost", "síntoma", "sintoma", "médic", "medic"
        )
        forbidden.forEach { word ->
            assertWithMessage("$key must not read as a diagnosis: $word")
                .that(value.lowercase()).doesNotContain(word)
        }
    }

    private fun assertNoMedicalPromise(key: String, value: String) {
        val forbidden = listOf(
            // English
            "injur", "prevent", "heal", "safely rebuild", "protect your joint",
            "protect joint", "protects your", "tendon", "connective tissue",
            // Spanish
            "lesion", "lesión", "prevenir", "previene", "cura", "sanar",
            "protege tus", "proteger tus articulaciones", "tendón", "tendon",
            "tejido conectivo"
        )
        forbidden.forEach { word ->
            assertWithMessage("$key must not promise a medical outcome: $word")
                .that(value.lowercase()).doesNotContain(word)
        }
    }

    /** Every translated exercise name and coaching summary, keyed for a readable failure. */
    private fun readTranslatedExerciseText(file: File): Map<String, String> {
        val exercises = (JSONTokener(file.readText()).nextValue() as JSONObject)
            .getJSONObject("exercises")
        return buildMap {
            exercises.keys().forEach { id ->
                val languages = exercises.getJSONObject(id)
                languages.keys().forEach { language ->
                    val text = languages.getJSONObject(language)
                    listOf("name", "coachingSummary").forEach { field ->
                        text.optString(field).takeIf(String::isNotBlank)?.let { value ->
                            put("$id.$language.$field", value)
                        }
                    }
                }
            }
        }
    }

    private fun readStrings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                put(element.getAttribute("name"), element.textContent)
            }
        }
    }

    private companion object {
        val RESOURCE_ROOT: File = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/res") }
            .firstOrNull(File::isDirectory)
            ?: File("src/main/res")

        val ENGLISH_FILE = File(RESOURCE_ROOT, "values/strings.xml")
        val SPANISH_FILE = File(RESOURCE_ROOT, "values-es/strings.xml")
        val OVERLAY_FILE = File(
            RESOURCE_ROOT.parentFile,
            "assets/localization/exercise-localization.json"
        )
    }
}
