package wallcrawl.elopenmike.com.feature.templates

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.PlannerFixtureContextFactory
import wallcrawl.elopenmike.com.core.exercise.ExerciseSearchIndex
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalization
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType

class TemplateEditorUiStateTest {

    private val exercise = Exercise(
        id = "barbell-back-squat",
        name = "Barbell Back Squat",
        searchAliases = listOf("High Bar Squat"),
        primaryMuscles = listOf("Quadriceps"),
        secondaryMuscles = listOf("Glutes"),
        listedEquipment = listOf("Barbell", "Squat Rack"),
        type = ExerciseType.WEIGHT_REPS
    )

    @Test
    fun filteredExercises_matchesTheSameCatalogFactsUsersCanSearchInTheLibrary() {
        listOf(
            "barbell-back",
            "High Bar",
            "Quadriceps",
            "Glutes",
            "Squat Rack"
        ).forEach { query ->
            val result = TemplateEditorUiState(
                query = query,
                searchIndex = ExerciseSearchIndex(listOf(exercise), ExerciseLocalization.EMPTY)
            ).filteredExercises

            assertThat(result).containsExactly(exercise)
        }
    }

    @Test
    fun filteredExercises_returnsAllExercisesForBlankQuery() {
        val otherExercise = exercise.copy(id = "front-squat", name = "Front Squat")

        val result = TemplateEditorUiState(
            query = "   ",
            searchIndex = ExerciseSearchIndex(listOf(exercise, otherExercise), ExerciseLocalization.EMPTY)
        ).filteredExercises

        assertThat(result).containsExactly(exercise, otherExercise).inOrder()
    }

    @Test
    fun filteredExercises_withNoProfileEquipmentRetainsAll302BundledManualChoices() {
        val bundledExercises = PlannerFixtureContextFactory()
            .bundledCatalogProjection()
            .exercises

        val state = TemplateEditorUiState(
            query = "",
            searchIndex = ExerciseSearchIndex(bundledExercises, ExerciseLocalization.EMPTY),
            availableEquipment = emptySet()
        )

        assertThat(state.filteredExercises).hasSize(302)
        assertThat(state.filteredExercises.map(Exercise::id))
            .containsExactlyElementsIn(bundledExercises.map(Exercise::id))
            .inOrder()
    }
}
