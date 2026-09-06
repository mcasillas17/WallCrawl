package wallcrawl.elopenmike.com.core.exercise.localization

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.MuscleVocabulary
import wallcrawl.elopenmike.com.core.model.StandardEquipment

/**
 * Guards the shipped translation overlay against the catalog, in CI.
 *
 * "Half-translated" is the failure this exists to prevent: a catalog update that adds an
 * exercise leaves it silently in English on an otherwise Spanish screen, and nothing in the
 * build notices. The parser needs `android.util.JsonReader`, which JVM unit tests do not
 * have, so this reads both documents directly — the same approach
 * `BundledCatalogVocabularyTest` uses for the catalog itself.
 */
class BundledExerciseLocalizationTest {

    private val catalog = readJson(CATALOG_FILE)
    private val overlay = readJson(OVERLAY_FILE)

    private val catalogIds: List<String> = catalog.getJSONArray("exercises").let { array ->
        (0 until array.length()).map { array.getJSONObject(it).getString("id") }
    }

    private val overlayExercises: JSONObject = overlay.getJSONObject("exercises")

    @Test
    fun bothDocumentsWerePresentAndParsedByThisTest() {
        // Without this, every assertion below could pass vacuously.
        assertThat(CATALOG_FILE.exists()).isTrue()
        assertThat(OVERLAY_FILE.exists()).isTrue()
        assertThat(catalogIds).hasSize(EXPECTED_EXERCISES)
        assertThat(overlay.getInt("schemaVersion")).isEqualTo(1)
        assertThat(overlay.getJSONArray("languages").getString(0)).isEqualTo("es")
    }

    @Test
    fun everyCatalogExerciseHasASpanishName() {
        val missing = catalogIds.filter { id ->
            overlayExercises.optJSONObject(id)
                ?.optJSONObject("es")
                ?.optString("name")
                .isNullOrBlank()
        }
        assertThat(missing).isEmpty()
    }

    @Test
    fun theOverlayNamesNoExerciseTheCatalogDoesNotContain() {
        // An orphaned key is a rename or a typo: the translation is written but reaches
        // nobody, while the exercise it was meant for stays in English.
        val orphans = overlayExercises.keys().asSequence().toSet() - catalogIds.toSet()
        assertThat(orphans).isEmpty()
    }

    @Test
    fun everyCoachingSummaryTheCatalogShipsIsTranslated() {
        val array = catalog.getJSONArray("exercises")
        val missing = (0 until array.length()).mapNotNull { index ->
            val exercise = array.getJSONObject(index)
            val english = exercise.optJSONObject("programming")?.optString("coachingSummary")
            if (english.isNullOrBlank()) return@mapNotNull null
            val spanish = overlayExercises.optJSONObject(exercise.getString("id"))
                ?.optJSONObject("es")
                ?.optString("coachingSummary")
            if (spanish.isNullOrBlank()) exercise.getString("id") else null
        }
        assertThat(missing).isEmpty()
    }

    @Test
    fun noSpanishCoachingSummaryExistsWithoutAnEnglishOne() {
        // A translation with nothing behind it would be prose no reviewer ever approved.
        val array = catalog.getJSONArray("exercises")
        val withEnglish = (0 until array.length())
            .map { array.getJSONObject(it) }
            .filter { !it.optJSONObject("programming")?.optString("coachingSummary").isNullOrBlank() }
            .map { it.getString("id") }
            .toSet()

        val invented = overlayExercises.keys().asSequence().filter { id ->
            val spanish = overlayExercises.getJSONObject(id)
                .optJSONObject("es")
                ?.optString("coachingSummary")
            !spanish.isNullOrBlank() && id !in withEnglish
        }.toList()
        assertThat(invented).isEmpty()
    }

    @Test
    fun everyCanonicalMuscleTheCatalogUsesHasASpanishWord() {
        val muscles = overlay.getJSONObject("vocabulary").getJSONObject("muscles")
        val array = catalog.getJSONArray("exercises")
        val canonical = (0 until array.length())
            .map { array.getJSONObject(it) }
            .flatMap { exercise ->
                listOf("primaryMuscles", "secondaryMuscles").flatMap { field ->
                    exercise.optJSONArray(field)?.let { values ->
                        (0 until values.length()).map { values.getString(it) }
                    }.orEmpty()
                }
            }
            .flatMap(MuscleVocabulary::canonicalize)
            .toSortedSet()

        assertThat(canonical).isNotEmpty()
        canonical.forEach { muscle ->
            assertWithMessage(muscle).that(muscles.optJSONObject(muscle)?.optString("es"))
                .isNotEmpty()
        }
    }

    @Test
    fun everyStandardEquipmentNameHasASpanishWord() {
        val equipment = overlay.getJSONObject("vocabulary").getJSONObject("equipment")
        StandardEquipment.ALL.forEach { name ->
            assertWithMessage(name).that(equipment.optJSONObject(name)?.optString("es"))
                .isNotEmpty()
        }
    }

    @Test
    fun everyEquipmentNameTheCatalogListsHasASpanishWord() {
        val equipment = overlay.getJSONObject("vocabulary").getJSONObject("equipment")
        val array = catalog.getJSONArray("exercises")
        val listed = (0 until array.length())
            .map { array.getJSONObject(it) }
            .flatMap { exercise ->
                exercise.optJSONArray("listedEquipment")?.let { values ->
                    (0 until values.length()).map { values.getString(it) }
                }.orEmpty()
            }
            .toSortedSet()

        assertThat(listed).isNotEmpty()
        listed.forEach { name ->
            assertWithMessage(name).that(equipment.optJSONObject(name)?.optString("es"))
                .isNotEmpty()
        }
    }

    @Test
    fun noSpanishNameWasLeftAsItsEnglishSource() {
        // A copy-paste that never got translated reads as "translated" in a diff. The
        // exceptions are names that really are the same word in both languages.
        val identicalByDesign = setOf("burpee", "skierg", "superman", "sprawl", "v-up", "bird-dog")
        val array = catalog.getJSONArray("exercises")
        val untranslated = (0 until array.length()).mapNotNull { index ->
            val exercise = array.getJSONObject(index)
            val id = exercise.getString("id")
            if (id in identicalByDesign) return@mapNotNull null
            val spanish = overlayExercises.optJSONObject(id)?.optJSONObject("es")?.optString("name")
            if (spanish == exercise.getString("name")) id else null
        }
        assertThat(untranslated).isEmpty()
    }

    @Test
    fun theOverlayContainsNoDuplicateKeys() {
        // A repeated key would silently discard one translation while the file still looked
        // complete. `readJson` rejects one, which is why the whole document parsing at all
        // is the assertion — and the second half proves that guard is real rather than a
        // parser that quietly keeps the last value.
        readJson(OVERLAY_FILE)

        val withDuplicate = """{"exercises": {"squat": {}, "squat": {}}}"""
        assertThrows(JSONException::class.java) {
            JSONTokener(withDuplicate).nextValue()
        }
    }

    private fun readJson(file: File): JSONObject =
        JSONTokener(file.readText()).nextValue() as JSONObject

    private companion object {
        const val EXPECTED_EXERCISES = 302

        val ASSET_ROOT: File = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/assets") }
            .firstOrNull(File::isDirectory)
            ?: File("src/main/assets")

        val CATALOG_FILE = File(ASSET_ROOT, "workout-guide/catalog.json")
        val OVERLAY_FILE = File(ASSET_ROOT, "localization/exercise-localization.json")
    }
}
