package wallcrawl.elopenmike.com.core.exercise.workoutguide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.StringReader
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.model.MuscleVocabulary
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile

@RunWith(AndroidJUnit4::class)
class WorkoutGuideCatalogParserTest {

    private val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
    private val parser = WorkoutGuideCatalogParser()

    @Test
    fun parse_packagedCatalogLoadsEveryExerciseAndFrame() {
        val snapshot = assets.open("workout-guide/catalog.json").bufferedReader().use(parser::parse)

        assertThat(snapshot.exercises).hasSize(302)
        assertThat(snapshot.exercises.map { it.id }.toSet()).hasSize(302)
        assertThat(snapshot.framesByExerciseId.values.flatten()).hasSize(906)
        assertThat(snapshot.exercises.count { it.programming != null }).isEqualTo(12)
        assertThat(snapshot.exercises.map { it.id }).containsAtLeastElementsIn(HISTORICAL_IDS)

        snapshot.framesByExerciseId.values.flatten().forEach { frame ->
            assets.open(frame.assetPath).use { stream ->
                assertThat(stream.read()).isNotEqualTo(-1)
            }
        }
    }

    @Test
    fun parse_everyMuscleInThePackagedCatalogIsCanonical() {
        val snapshot = assets.open("workout-guide/catalog.json").bufferedReader().use(parser::parse)

        val unrecognized = snapshot.exercises
            .flatMap { it.primaryMuscles + it.secondaryMuscles }
            .distinct()
            .filterNot(MuscleVocabulary::isCanonical)

        // A catalog update that introduces new muscle vocabulary must be taught to
        // MuscleVocabulary, otherwise those exercises silently stop matching every split.
        assertThat(unrecognized).isEmpty()
        snapshot.exercises.forEach { exercise ->
            assertThat(exercise.primaryMuscles).isNotEmpty()
        }
    }

    @Test
    fun parse_resolvesUpstreamMuscleNamesOntoTheAppVocabulary() {
        val snapshot = assets.open("workout-guide/catalog.json").bufferedReader().use(parser::parse)

        val backSquat = snapshot.exercises.single { it.id == "barbell-back-squat" }
        assertThat(backSquat.primaryMuscles).contains(StandardMuscles.QUADS)

        // Upstream files the deadlift under the umbrella term "Posterior Chain". One group
        // is named as the primary mover so weekly set counts stay one per set; the rest
        // become secondary, which split matching also reads.
        val deadlift = snapshot.exercises.single { it.id == "barbell-deadlift" }
        assertThat(deadlift.primaryMuscles).containsExactly(StandardMuscles.HAMSTRINGS)
        assertThat(deadlift.secondaryMuscles).containsAtLeast(
            StandardMuscles.GLUTES,
            StandardMuscles.LOWER_BACK
        )
    }

    @Test
    fun parse_exposesCatalogProvenanceForTheCreditsScreen() {
        val snapshot = assets.open("workout-guide/catalog.json").bufferedReader().use(parser::parse)

        val attribution = snapshot.catalogAttribution
        assertThat(attribution.attribution.license).isEqualTo("CC BY-SA 4.0")
        assertThat(attribution.repository).startsWith("https://")
        assertThat(attribution.commit).hasLength(40)
        assertThat(attribution.exerciseCount).isEqualTo(302)
        assertThat(attribution.frameCount).isEqualTo(906)
    }

    @Test
    fun parse_preservesAliasedIdentityAndSourceMetadata() {
        val snapshot = assets.open("workout-guide/catalog.json").bufferedReader().use(parser::parse)

        val benchPress = snapshot.exercises.single { it.id == "barbell-bench-press" }
        assertThat(benchPress.source?.sourceId).isEqualTo("exercise-bench-press")
        assertThat(benchPress.source?.sourceSlug).isEqualTo("bench-press")
        assertThat(benchPress.searchAliases).contains("Barbell Bench Press")
        assertThat(benchPress.source?.attribution?.license).isEqualTo("CC BY-SA 4.0")
        assertThat(snapshot.framesByExerciseId.getValue(benchPress.id)).hasSize(3)
    }

    @Test
    fun packagedCatalog_all302ExercisesCanEnterThePlannerCandidatePool() {
        val exercises = assets.open("workout-guide/catalog.json")
            .bufferedReader()
            .use(parser::parse)
            .exercises

        val catalogEquipment = exercises.flatMap { it.listedEquipment }.toSet()
        val candidates = ExerciseFilter().filterCandidates(
            allExercises = exercises,
            profile = UserProfile(availableEquipment = StandardEquipment.ALL)
        )

        assertThat(StandardEquipment.ALL).containsAtLeastElementsIn(catalogEquipment)
        assertThat(candidates.map { it.id }.toSet()).hasSize(302)
    }

    @Test
    fun parse_rejectsUnsupportedSchema() {
        val malformed = """{"schemaVersion":2,"source":{},"exercises":[]}"""

        try {
            parser.parse(StringReader(malformed))
            throw AssertionError("Expected catalog format failure")
        } catch (error: WorkoutGuideCatalogFormatException) {
            assertThat(error.message).contains("schemaVersion")
        }
    }

    @Test
    fun parse_rejectsDuplicateExerciseIds() {
        val malformed = catalogJson("${exerciseJson()} , ${exerciseJson()}")

        assertFormatFailure(malformed, "Duplicate exercise id")
    }

    @Test
    fun parse_rejectsInvalidVisualSpecification() {
        val malformed = catalogJson(exerciseJson())
            .replace("\"frameCount\": 3", "\"frameCount\": 4")

        assertFormatFailure(malformed, "frameCount")
    }

    @Test
    fun parse_rejectsTruncatedJsonWithOriginalCause() {
        try {
            parser.parse(StringReader("""{"schemaVersion":1,"exercises":["""))
            throw AssertionError("Expected catalog format failure")
        } catch (error: WorkoutGuideCatalogFormatException) {
            assertThat(error.cause).isNotNull()
        }
    }

    private fun assertFormatFailure(json: String, messageFragment: String) {
        try {
            parser.parse(StringReader(json))
            throw AssertionError("Expected catalog format failure")
        } catch (error: WorkoutGuideCatalogFormatException) {
            assertThat(error.message).contains(messageFragment)
        }
    }

    private fun catalogJson(exercises: String): String = """
        {
          "schemaVersion": 1,
          "source": {
            "repository": "https://github.com/bryllim/workout-guide",
            "commit": "ba0b709cb20430361b2cb33aaadd20998164a916",
            "assetLicense": "CC-BY-SA-4.0",
            "attribution": {
              "creator": "Bryl Lim",
              "creatorUrl": "https://bryllim.com",
              "license": "CC BY-SA 4.0",
              "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/"
            }
          },
          "visuals": {"frameCount": 3, "widthPx": 512, "heightPx": 512, "format": "svg"},
          "exercises": [$exercises]
        }
    """.trimIndent()

    private fun exerciseJson(): String {
        return """
            {
              "id": "sample",
              "sourceId": "exercise-sample",
              "sourceSlug": "sample",
              "name": "Sample Exercise",
              "searchAliases": [],
              "primaryMuscles": ["Core"],
              "secondaryMuscles": [],
              "listedEquipment": ["Bodyweight"],
              "exerciseType": "bodyweight_reps",
              "isStretch": false
            }
        """.trimIndent()
    }

    private companion object {
        val HISTORICAL_IDS = setOf(
            "incline-dumbbell-press",
            "barbell-bench-press",
            "pull-ups",
            "barbell-deadlift",
            "barbell-back-squat",
            "dumbbell-shoulder-press",
            "dumbbell-lateral-raise",
            "cable-triceps-pushdown",
            "barbell-bicep-curl",
            "parallel-bar-dips",
            "hanging-leg-raise",
            "romanian-deadlift"
        )
    }
}
