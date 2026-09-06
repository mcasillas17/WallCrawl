package wallcrawl.elopenmike.com.core.exercise

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalization
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationSource
import wallcrawl.elopenmike.com.core.exercise.localization.LocalizedExerciseText
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSnapshot
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.exercise.workoutguide.testCatalogAttribution
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles

/**
 * Search resolves the same catalog ids whichever language the query is typed in.
 *
 * A translated label is a label: it can be searched by, and it can never become the key an
 * eligibility, muscle, equipment, or dose decision is made on. These tests pin both halves.
 */
class LocalizedExerciseSearchTest {

    private val benchPress = InMemoryExerciseCatalog.SAMPLE_EXERCISES
        .single { it.id == "barbell-bench-press" }
    private val squat = InMemoryExerciseCatalog.SAMPLE_EXERCISES
        .single { it.id == "barbell-back-squat" }
    private val lateralRaise = InMemoryExerciseCatalog.SAMPLE_EXERCISES
        .single { it.id == "dumbbell-lateral-raise" }

    private val snapshot = WorkoutGuideCatalogSnapshot(
        exercises = listOf(benchPress, squat, lateralRaise),
        framesByExerciseId = mapOf(
            benchPress.id to listOf(ExerciseVisual("workout-guide/assets/bench-press/frame-1.svg"))
        ),
        catalogAttribution = testCatalogAttribution(exerciseCount = 3, frameCount = 1)
    )

    private val localization = ExerciseLocalization(
        languages = listOf("es"),
        exercisesById = mapOf(
            "barbell-bench-press" to mapOf(
                "es" to LocalizedExerciseText(
                    name = "Press de banca",
                    aliases = listOf("Press de banca con barra")
                )
            ),
            "barbell-back-squat" to mapOf(
                "es" to LocalizedExerciseText(name = "Sentadilla con barra")
            ),
            "dumbbell-lateral-raise" to mapOf(
                "es" to LocalizedExerciseText(name = "Elevación lateral")
            )
        ),
        musclesByCanonicalName = mapOf(
            StandardMuscles.CHEST to mapOf("es" to "Pecho"),
            StandardMuscles.QUADS to mapOf("es" to "Cuádriceps"),
            StandardMuscles.SHOULDERS to mapOf("es" to "Hombros")
        ),
        equipmentByCanonicalName = mapOf(
            StandardEquipment.BARBELL to mapOf("es" to "Barra"),
            StandardEquipment.DUMBBELL to mapOf("es" to "Mancuerna")
        )
    )

    private val catalog = BundledExerciseCatalog(
        source = FixedSource(snapshot),
        localizationSource = FixedLocalization(localization)
    )

    @Test
    fun aSpanishQueryResolvesTheSameIdsAsItsEnglishCounterpart() = runTest {
        assertThat(catalog.searchExercises(query = "sentadilla").first().map { it.id })
            .containsExactlyElementsIn(catalog.searchExercises(query = "squat").first().map { it.id })
        assertThat(catalog.searchExercises(query = "press de banca").first().map { it.id })
            .containsExactly("barbell-bench-press")
        assertThat(catalog.searchExercises(query = "bench press").first().map { it.id })
            .containsExactly("barbell-bench-press")
    }

    @Test
    fun anEnglishQueryStillWorksWhileTheInterfaceIsSpanish() = runTest {
        // Nothing about the query depends on the reader's current language: this catalog
        // has no idea what it is, on purpose.
        assertThat(catalog.searchExercises(query = "lateral raise").first().map { it.id })
            .containsExactly("dumbbell-lateral-raise")
    }

    @Test
    fun searchIsAccentToleranceInBothDirections() = runTest {
        listOf("elevación", "elevacion", "ELEVACIÓN", "Elevacion").forEach { query ->
            assertWithMessage(query)
                .that(catalog.searchExercises(query = query).first().map { it.id })
                .containsExactly("dumbbell-lateral-raise")
        }
        listOf("cuádriceps", "cuadriceps").forEach { query ->
            assertWithMessage(query)
                .that(catalog.searchExercises(query = query).first().map { it.id })
                .contains("barbell-back-squat")
        }
    }

    @Test
    fun aSpanishMuscleOrEquipmentWordFindsTheExercisesTaggedWithItsCanonicalName() = runTest {
        assertThat(catalog.searchExercises(query = "pecho").first().map { it.id })
            .contains("barbell-bench-press")
        assertThat(catalog.searchExercises(query = "mancuerna").first().map { it.id })
            .containsExactly("dumbbell-lateral-raise")
    }

    @Test
    fun theFilterArgumentsStayCanonicalEnglishKeys() = runTest {
        // The chips display a translated label but pass the canonical key, which is what
        // the catalog matches on. A translated value must not filter.
        assertThat(
            catalog.searchExercises(muscle = StandardMuscles.CHEST).first().map { it.id }
        ).containsExactly("barbell-bench-press")
        assertThat(catalog.searchExercises(muscle = "Pecho").first()).isEmpty()
        assertThat(
            catalog.searchExercises(equipment = StandardEquipment.DUMBBELL).first().map { it.id }
        ).containsExactly("dumbbell-lateral-raise")
        assertThat(catalog.searchExercises(equipment = "Mancuerna").first()).isEmpty()
    }

    @Test
    fun theExercisesTheCatalogReturnsKeepTheirCanonicalNamesAndVocabulary() = runTest {
        // The overlay is a display and search layer. Nothing downstream — eligibility,
        // muscle matching, equipment filtering, dose accounting — ever sees a translation.
        val squatResult = catalog.searchExercises(query = "sentadilla").first().single()

        assertThat(squatResult.name).isEqualTo(squat.name)
        assertThat(squatResult.primaryMuscles).isEqualTo(squat.primaryMuscles)
        assertThat(squatResult.listedEquipment).isEqualTo(squat.listedEquipment)
        assertThat(squatResult.id).isEqualTo("barbell-back-squat")
    }

    @Test
    fun searchStillWorksWithNoOverlayAtAll() = runTest {
        val englishOnly = BundledExerciseCatalog(FixedSource(snapshot))

        assertThat(englishOnly.searchExercises(query = "bench press").first().map { it.id })
            .containsExactly("barbell-bench-press")
        assertThat(englishOnly.searchExercises(query = "sentadilla").first()).isEmpty()
    }

    private class FixedSource(
        private val value: WorkoutGuideCatalogSnapshot
    ) : WorkoutGuideCatalogSource {
        override suspend fun snapshot(): WorkoutGuideCatalogSnapshot = value
        override fun currentSnapshot(): WorkoutGuideCatalogSnapshot = value
    }

    private class FixedLocalization(
        private val value: ExerciseLocalization
    ) : ExerciseLocalizationSource {
        override suspend fun localization(): ExerciseLocalization = value
        override fun currentLocalization(): ExerciseLocalization = value
    }
}
