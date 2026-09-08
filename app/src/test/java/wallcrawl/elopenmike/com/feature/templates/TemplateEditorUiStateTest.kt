package wallcrawl.elopenmike.com.feature.templates

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.PlannerFixtureContextFactory
import wallcrawl.elopenmike.com.core.exercise.ExerciseSearchIndex
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalization
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.hasUnresolvedEquipmentRequirements
import wallcrawl.elopenmike.com.core.model.missingEquipmentAlternatives

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

    @Test
    fun equipmentWarningData_keepsAlternativeCombinationsAndDistinguishesUnresolvedSetups() {
        val alternative = InMemoryExerciseCatalog.SAMPLE_EXERCISES
            .single { it.id == "romanian-deadlift" }
        assertThat(alternative.missingEquipmentAlternatives(emptyList()))
            .containsExactly(listOf(StandardEquipment.BARBELL), listOf(StandardEquipment.DUMBBELL))
        assertThat(alternative.missingEquipmentAlternatives(listOf(StandardEquipment.DUMBBELL)))
            .isEmpty()
        assertThat(alternative.hasUnresolvedEquipmentRequirements).isFalse()

        val kickback = exercise.copy(id = "banded-kickback")
        assertThat(kickback.missingEquipmentAlternatives(listOf(StandardEquipment.RESISTANCE_BAND)))
            .containsExactly(listOf(StandardEquipment.BAND_ANCHOR_LOW, StandardEquipment.BAND_KICKBACK_ATTACHMENT_SUPPORT))
        val row = exercise.copy(id = "banded-row")
        assertThat(row.hasUnresolvedEquipmentRequirements).isTrue()
        assertThat(row.missingEquipmentAlternatives(StandardEquipment.ALL)).isEmpty()
    }
}
