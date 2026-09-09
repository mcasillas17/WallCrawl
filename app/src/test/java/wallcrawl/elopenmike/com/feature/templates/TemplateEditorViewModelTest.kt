package wallcrawl.elopenmike.com.feature.templates

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.PlannerFixtureContextFactory
import wallcrawl.elopenmike.com.core.database.repository.FakeUserProfileDao
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.core.model.hasUnresolvedEquipmentRequirements
import wallcrawl.elopenmike.com.core.model.missingEquipmentAlternatives
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun manualAddAndSave_allowMissingAndUnresolvedSetupsWithoutFilteringCatalog() = runTest {
        val exercises = PlannerFixtureContextFactory().bundledCatalogProjection().exercises
        val profiles = OfflineUserProfileRepository(FakeUserProfileDao())
        profiles.saveProfile(UserProfile(availableEquipment = listOf(StandardEquipment.RESISTANCE_BAND)))
        val templates = RecordingTemplateRepository()
        val viewModel = TemplateEditorViewModel(null, templates, profiles, InMemoryExerciseCatalog(exercises))
        val loaded = viewModel.uiState.first { !it.isLoading }
        assertThat(loaded.errorMessage).isNull()
        assertThat(loaded.catalogExercises).hasSize(302)
        val pulldown = exercises.single { it.id == "banded-lat-pulldown" }
        val row = exercises.single { it.id == "banded-row" }
        assertThat(pulldown.missingEquipmentAlternatives(loaded.availableEquipment))
            .containsExactly(listOf(StandardEquipment.BAND_ANCHOR_OVERHEAD, StandardEquipment.CHAIR))
        assertThat(row.hasUnresolvedEquipmentRequirements).isTrue()
        assertThat(row.missingEquipmentAlternatives(StandardEquipment.ALL)).isEmpty()

        viewModel.openPicker()
        viewModel.updateQuery("banded-lat-pulldown")
        assertThat(viewModel.uiState.value.filteredExercises).containsExactly(pulldown)
        viewModel.addExercise(pulldown)
        viewModel.addExercise(row)
        viewModel.updateName("Manual band choices")
        var saved = false
        viewModel.save { saved = true }
        advanceUntilIdle()

        assertThat(saved).isTrue()
        assertThat(templates.values.value.single().exercises.map { it.exerciseId })
            .containsExactly(pulldown.id, row.id).inOrder()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    private class RecordingTemplateRepository : WorkoutTemplateRepository {
        val values = MutableStateFlow<List<WorkoutTemplate>>(emptyList())
        override fun observeTemplates() = values
        override fun observeTemplate(templateId: String) = values.map { list ->
            list.firstOrNull { it.id == templateId }
        }
        override suspend fun getTemplate(templateId: String) = values.value.firstOrNull { it.id == templateId }
        override suspend fun saveTemplate(template: WorkoutTemplate) {
            values.value = values.value.filterNot { it.id == template.id } + template
        }
        override suspend fun deleteTemplate(templateId: String) {
            values.value = values.value.filterNot { it.id == templateId }
        }
    }
}
