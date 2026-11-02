package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.json.JSONObject
import org.junit.Test

/**
 * Guards the shipped catalog against muscle-vocabulary drift, in CI.
 *
 * The parser that normally performs this mapping needs `android.util.JsonReader`, which is
 * unavailable to JVM unit tests, and instrumentation tests are not part of the CI command.
 * So this reads the bundled catalog directly and checks the muscle names it actually
 * contains. Without it, a catalog update could introduce a name no split matches and
 * nothing would fail until a user noticed exercises had gone missing from their plans.
 */
class BundledCatalogVocabularyTest {

    private val catalog: String = CATALOG_FILE.readText()

    @Test
    fun bundledCatalogIsPresentAndParsedByThisTest() {
        // If the extraction below silently matched nothing, every other assertion here
        // would pass vacuously. Pin the shape of what was read instead.
        assertThat(CATALOG_FILE.exists()).isTrue()
        assertThat(muscleArrays("primaryMuscles")).hasSize(EXPECTED_EXERCISES)
        assertThat(muscleArrays("secondaryMuscles")).hasSize(EXPECTED_EXERCISES)
    }

    @Test
    fun everyMuscleNameInTheBundledCatalogIsKnownToTheVocabulary() {
        val unknown = allMuscleNames().filterNot(MuscleVocabulary::isCanonical).toSortedSet()

        assertThat(unknown).isEmpty()
    }

    @Test
    fun everyExerciseKeepsAtLeastOnePrimaryMuscleAfterCanonicalization() {
        val emptyAfterMapping = muscleArrays("primaryMuscles")
            .filter { names -> names.mapNotNull(MuscleVocabulary::canonicalizePrimary).isEmpty() }

        assertThat(emptyAfterMapping).isEmpty()
    }

    @Test
    fun everyUpstreamNameContributesExactlyOnePrimaryGroup() {
        // Weekly set counts credit a set to every primary muscle, so an umbrella name such
        // as "Legs" must resolve to one group: expanding it would report one set of lunges
        // as three. Asserting per name, because a count over the whole list would also pass
        // if canonicalization dropped names entirely.
        val distinctNames = muscleArrays("primaryMuscles").flatten().distinct()
        assertThat(distinctNames).isNotEmpty()

        distinctNames.forEach { name ->
            assertThat(MuscleVocabulary.canonicalizePrimary(name)).isNotNull()
        }
        muscleArrays("primaryMuscles").forEach { names ->
            val primaries = names.mapNotNull(MuscleVocabulary::canonicalizePrimary)
            assertThat(primaries).hasSize(names.size)
        }
    }

    @Test
    fun umbrellaNamesResolveToTheirDocumentedPrimaryMover() {
        // These are the names that cover several groups at once; pinning the representative
        // keeps it a decision rather than an artifact of expansion order.
        assertThat(MuscleVocabulary.canonicalizePrimary("Legs")).isEqualTo(StandardMuscles.QUADS)
        assertThat(MuscleVocabulary.canonicalizePrimary("Posterior Chain"))
            .isEqualTo(StandardMuscles.HAMSTRINGS)
    }

    private fun ownedEntries(): List<JSONObject> = JSONObject(catalog).getJSONArray("exercises").let { array ->
        (0 until array.length()).map(array::getJSONObject).filter { it.has("attribution") }
    }

    @Test
    fun ownedExercisesAreMarkedAndAttributed() {
        val owned = ownedEntries()
        assertThat(owned.map { it.getString("id") }).containsExactlyElementsIn(OWNED_IDS)
        assertThat(JSONObject(catalog).getJSONArray("exercises").length() - owned.size)
            .isEqualTo(UPSTREAM_EXERCISES)
        owned.forEach { entry ->
            val id = entry.getString("id")
            val attribution = entry.getJSONObject("attribution")
            assertThat(attribution.getString("license")).isEqualTo(OWNED_LICENSE)
            assertThat(attribution.getString("creator")).isEqualTo("WallCrawl project")
            assertThat(attribution.getString("licenseUrl")).startsWith("https://")
            assertThat(entry.getString("sourceId")).isEqualTo("owned-$id")
            // No metadata means these never enter main selection.
            assertThat(entry.has("reviewedMetadata")).isFalse()
            assertThat(entry.has("programming")).isFalse()
            assertThat(entry.getBoolean("isStretch")).isEqualTo(id in OWNED_STRETCHES)
        }
        val notices = File(ASSETS, "workout-guide/ATTRIBUTION.md").readText()
        assertThat(notices).contains(OWNED_LICENSE)
        OWNED_IDS.forEach { assertThat(notices).contains("`$it`") }
        assertThat(File(ASSETS, "workout-guide/NOTICE.md").readText()).contains("Owned exercises: 7")
        assertThat(File(ASSETS, "workout-guide/NOTICE.md").readText()).contains("Imported exercises: 302")
    }

    @Test
    fun everyOwnedExerciseHasFramesAndSpanishText() {
        val localization = JSONObject(File(ASSETS, "localization/exercise-localization.json").readText())
            .getJSONObject("exercises")
        val demands = JSONObject(File(ASSETS, "conditions/condition-guidance.json").readText())
            .getJSONObject("exerciseDemands")
        ownedEntries().forEach { entry ->
            val id = entry.getString("id")
            (1..3).forEach { frame ->
                val svg = File(ASSETS, "workout-guide/assets/$id/frame-$frame.svg")
                assertThat(svg.isFile).isTrue()
                assertThat(svg.readText()).contains("viewBox=\"0 0 512 512\"")
            }
            val spanish = localization.getJSONObject(id).getJSONObject("es").getString("name")
            assertThat(spanish).isNotEmpty()
            assertThat(spanish).isNotEqualTo(entry.getString("name"))
            assertThat(demands.has(id)).isTrue()
        }
    }

    private fun allMuscleNames(): Set<String> =
        (muscleArrays("primaryMuscles") + muscleArrays("secondaryMuscles")).flatten().toSet()

    /**
     * Extracts the string arrays stored under [field] for every exercise in the catalog.
     *
     * Assumes what the importer actually emits: flat arrays of plain strings with no escaped
     * quotes or nesting. `bundledCatalogIsPresentAndParsedByThisTest` pins the entry count so
     * a change in that shape shows up as a failure here rather than as silent under-reading.
     */
    private fun muscleArrays(field: String): List<List<String>> =
        Regex("\"$field\"\\s*:\\s*\\[([^\\]]*)]")
            .findAll(catalog)
            .map { match ->
                Regex("\"([^\"]*)\"").findAll(match.groupValues[1])
                    .map { it.groupValues[1] }
                    .toList()
            }
            .toList()

    private companion object {
        const val UPSTREAM_EXERCISES = 302
        const val EXPECTED_EXERCISES = UPSTREAM_EXERCISES + 7
        const val OWNED_LICENSE = "Original work by the WallCrawl project"
        val ASSETS = File("src/main/assets")
        val CATALOG_FILE = File(ASSETS, "workout-guide/catalog.json")
        val OWNED_STRETCHES = setOf("figure-four-stretch", "lying-piriformis-stretch", "wrist-extensor-stretch")
        val OWNED_IDS = OWNED_STRETCHES + setOf(
            "side-lying-isometric-hip-abduction-hold",
            "standing-isometric-gluteal-contraction",
            "tendon-gliding-sequence",
            "straight-leg-raise"
        )
    }
}
