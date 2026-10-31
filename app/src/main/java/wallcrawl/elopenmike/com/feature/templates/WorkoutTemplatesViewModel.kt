package wallcrawl.elopenmike.com.feature.templates

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.model.UnconfirmedTemplateUnitException
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

data class WorkoutTemplatesUiState(
    val isLoading: Boolean = true,
    val templates: List<WorkoutTemplate> = emptyList(),
    val startingTemplateId: String? = null,
    /**
     * A session a start created, until the screen has navigated to it. State rather than a
     * callback held by the start, so a start that finishes after the activity is recreated
     * opens the workout through the current navigator, not the destroyed one.
     */
    val startedSessionId: String? = null,
    /**
     * How many exercises the bundled catalog holds, so the empty state can say so without
     * a number hard-coded into a translated sentence that would then need updating twice.
     */
    val catalogSize: Int = 0,
    @StringRes val errorMessage: Int? = null
)

class WorkoutTemplatesViewModel(
    private val templateRepository: WorkoutTemplateRepository,
    private val workoutRepository: WorkoutRepository,
    private val userProfileRepository: UserProfileRepository,
    private val exerciseCatalog: ExerciseCatalog
) : ViewModel() {
    private val startingTemplateId = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<Int?>(null)
    private val startedSessionId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WorkoutTemplatesUiState> = combine(
        templateRepository.observeTemplates(),
        startingTemplateId,
        errorMessage,
        startedSessionId,
        exerciseCatalog.getAllExercises()
    ) { templates, startingId, error, startedId, catalog ->
        WorkoutTemplatesUiState(
            isLoading = false,
            templates = templates,
            startingTemplateId = startingId,
            startedSessionId = startedId,
            catalogSize = catalog.size,
            errorMessage = error
        )
    }.catch { error ->
        // The repository's exception message is developer text; the screen shows a typed,
        // translated reason instead.
        if (error is CancellationException) throw error
        emit(
            WorkoutTemplatesUiState(
                isLoading = false,
                errorMessage = R.string.editor_error_load_failed
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutTemplatesUiState()
    )

    fun startTemplate(template: WorkoutTemplate) {
        if (startingTemplateId.value != null) return
        // Locked before launching, so a second call cannot pass the check whatever the
        // dispatcher, as the editor's save sets isSaving.
        startingTemplateId.value = template.id
        // A new attempt answers whatever the last one said, such as a unit to confirm.
        errorMessage.value = null
        viewModelScope.launch {
            try {
                val currentTemplate = templateRepository.getTemplate(template.id)
                if (currentTemplate == null) {
                    errorMessage.value = R.string.template_error_missing
                    return@launch
                }
                val profile = userProfileRepository.getProfileOnce()
                startedSessionId.value = workoutRepository.startWorkoutFromTemplate(currentTemplate, profile).id
            } catch (error: CancellationException) {
                throw error
            } catch (error: UnconfirmedTemplateUnitException) {
                // Its loads were saved before templates recorded a unit, so it cannot start a
                // new workout without guessing one. (A workout in progress is resumed by the
                // repository instead, which copies nothing and needs no unit.)
                errorMessage.value = R.string.template_error_unit_unconfirmed
            } catch (error: Exception) {
                errorMessage.value = R.string.template_error_start_failed
            } finally {
                startingTemplateId.value = null
            }
        }
    }

    /** The screen has navigated to [WorkoutTemplatesUiState.startedSessionId]. */
    fun onStartHandled() {
        startedSessionId.value = null
    }

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            try {
                templateRepository.deleteTemplate(templateId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = R.string.template_error_delete_failed
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    companion object {
        fun provideFactory(
            templateRepository: WorkoutTemplateRepository,
            workoutRepository: WorkoutRepository,
            userProfileRepository: UserProfileRepository,
            exerciseCatalog: ExerciseCatalog
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WorkoutTemplatesViewModel(
                    templateRepository,
                    workoutRepository,
                    userProfileRepository,
                    exerciseCatalog
                ) as T
        }
    }
}
