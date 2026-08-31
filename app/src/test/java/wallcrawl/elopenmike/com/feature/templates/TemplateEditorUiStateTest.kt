package wallcrawl.elopenmike.com.feature.templates

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.PlannerFixtureContextFactory
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
                catalogExercises = listOf(exercise)
            ).filteredExercises

            assertThat(result).containsExactly(exercise)
        }
    }

    @Test
    fun filteredExercises_returnsAllExercisesForBlankQuery() {
        val otherExercise = exercise.copy(id = "front-squat", name = "Front Squat")

        val result = TemplateEditorUiState(
            query = "   ",
            catalogExercises = listOf(exercise, otherExercise)
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
            catalogExercises = bundledExercises,
            availableEquipment = emptySet()
        )

        assertThat(state.filteredExercises).hasSize(302)
        assertThat(state.filteredExercises.map(Exercise::id))
            .containsExactlyElementsIn(bundledExercises.map(Exercise::id))
            .inOrder()
    }
}
