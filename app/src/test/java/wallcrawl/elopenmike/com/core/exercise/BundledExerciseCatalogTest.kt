package wallcrawl.elopenmike.com.core.exercise

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSnapshot
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.exercise.workoutguide.testCatalogAttribution
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles

class BundledExerciseCatalogTest {

    private val inclinePress = InMemoryExerciseCatalog.SAMPLE_EXERCISES
        .single { it.id == "incline-dumbbell-press" }
        .copy(searchAliases = listOf("Incline DB Press"))
    private val lateralRaise = InMemoryExerciseCatalog.SAMPLE_EXERCISES
        .single { it.id == "dumbbell-lateral-raise" }
    private val snapshot = WorkoutGuideCatalogSnapshot(
        exercises = listOf(lateralRaise, inclinePress),
        framesByExerciseId = mapOf(
            inclinePress.id to listOf(ExerciseVisual("workout-guide/assets/incline-dumbbell-press/frame-1.svg"))
        ),
        catalogAttribution = testCatalogAttribution(exerciseCount = 2, frameCount = 1)
    )
    private val catalog = BundledExerciseCatalog(FixedSource(snapshot))

    @Test
    fun getAllExercises_preservesGeneratedOrder() = runTest {
        assertThat(catalog.getAllExercises().first().map { it.id })
            .containsExactly("dumbbell-lateral-raise", "incline-dumbbell-press")
            .inOrder()
    }

    @Test
    fun getExerciseById_isCaseInsensitive() = runTest {
        assertThat(catalog.getExerciseById("INCLINE-DUMBBELL-PRESS")?.id)
            .isEqualTo("incline-dumbbell-press")
    }

    @Test
    fun searchExercises_matchesAliasMuscleAndListedEquipment() = runTest {
        assertThat(catalog.searchExercises(query = "DB Press").first().map { it.id })
            .containsExactly("incline-dumbbell-press")
        assertThat(catalog.searchExercises(query = StandardMuscles.CHEST).first().map { it.id })
            .containsExactly("incline-dumbbell-press")
        assertThat(catalog.searchExercises(query = StandardEquipment.DUMBBELL).first())
            .hasSize(2)
    }

    @Test
    fun searchExercises_appliesExactMuscleAndEquipmentFilters() = runTest {
        assertThat(
            catalog.searchExercises(
                muscle = StandardMuscles.CHEST,
                equipment = StandardEquipment.DUMBBELL
            ).first().map { it.id }
        ).containsExactly("incline-dumbbell-press")
    }

    @Test
    fun distinctFilterValues_areCaseInsensitiveAndSorted() = runTest {
        assertThat(catalog.getEquipmentTypes()).containsExactly(StandardEquipment.DUMBBELL)
        assertThat(catalog.getMuscleGroups()).contains(StandardMuscles.CHEST)
        assertThat(catalog.getMuscleGroups()).isInOrder(String.CASE_INSENSITIVE_ORDER)
    }

    @Test
    fun sourceFailure_propagatesToCatalogFlow() = runTest {
        val failure = IllegalStateException("catalog unavailable")
        val failingCatalog = BundledExerciseCatalog(FailingSource(failure))

        try {
            failingCatalog.getAllExercises().first()
            throw AssertionError("Expected catalog failure")
        } catch (error: IllegalStateException) {
            assertThat(error).isSameInstanceAs(failure)
        }
    }

    private class FixedSource(
        private val value: WorkoutGuideCatalogSnapshot
    ) : WorkoutGuideCatalogSource {
        override suspend fun snapshot(): WorkoutGuideCatalogSnapshot = value
        override fun currentSnapshot(): WorkoutGuideCatalogSnapshot = value
    }

    private class FailingSource(
        private val failure: IllegalStateException
    ) : WorkoutGuideCatalogSource {
        override suspend fun snapshot(): WorkoutGuideCatalogSnapshot = throw failure
        override fun currentSnapshot(): WorkoutGuideCatalogSnapshot? = null
    }
}
