package wallcrawl.elopenmike.com.core.exercise.visual

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.IllustrationVariant

class IllustrationCatalogTest {
    @Test
    fun bundledVariantsCoverEveryCatalogExerciseWithThreeOrderedFrames() = runTest {
        val loader = javaClass.classLoader!!
        val source = loader.getResourceAsStream("exercise-illustrations/index.json")
        assertThat(source).isNotNull()
        val catalog = IllustrationCatalog { source!!.bufferedReader().use { it.readText() } }
        val exercises = org.json.JSONObject(loader.getResource("workout-guide/catalog.json")!!.readText())
            .getJSONArray("exercises")
        for (i in 0 until exercises.length()) {
            val id = exercises.getJSONObject(i).getString("id")
            for (variant in IllustrationVariant.entries) {
                val paths = catalog.framesFor(id, variant)
                assertThat(paths).hasSize(3)
                paths.forEachIndexed { index, path ->
                    assertThat(path).endsWith("/frame-${index + 1}.svg")
                    assertThat(loader.getResource(path)).isNotNull()
                }
            }
        }
    }

    @Test
    fun missingVariantOrIncompleteSequenceReturnsNoFramesAndDoesNotMixVariants() = runTest {
        val catalog = IllustrationCatalog { """{"schemaVersion":1,"exercises":{"squat":{"male":["exercise-illustrations/male/squat/frame-1.svg"]}}}""" }
        assertThat(catalog.framesFor("squat", IllustrationVariant.MALE)).isEmpty()
        assertThat(catalog.framesFor("squat", IllustrationVariant.FEMALE)).isEmpty()
        assertThat(catalog.framesFor("unknown", IllustrationVariant.MALE)).isEmpty()
    }

    @Test
    fun invalidManifestAndExternalPathsReturnNoFrames() = runTest {
        assertThat(IllustrationCatalog { "invalid json" }.framesFor("squat", IllustrationVariant.MALE)).isEmpty()
        val paths = (1..3).joinToString(",") { "\"https://example.test/frame-$it.svg\"" }
        val catalog = IllustrationCatalog { """{"schemaVersion":1,"exercises":{"squat":{"male":[$paths]}}}""" }
        assertThat(catalog.framesFor("squat", IllustrationVariant.MALE)).isEmpty()
    }
}
