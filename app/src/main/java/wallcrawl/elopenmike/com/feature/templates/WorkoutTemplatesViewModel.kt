package wallcrawl.elopenmike.com.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

data class WorkoutTemplatesUiState(
    val isLoading: Boolean = true,
    val templates: List<WorkoutTemplate> = emptyList(),
    val startingTemplateId: String? = null,
    val errorMessage: String? = null
)

class WorkoutTemplatesViewModel(
    private val templateRepository: WorkoutTemplateRepository,
    private val workoutRepository: WorkoutRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {
    private val startingTemplateId = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WorkoutTemplatesUiState> = combine(
        templateRepository.observeTemplates(),
        startingTemplateId,
        errorMessage
    ) { templates, startingId, error ->
        WorkoutTemplatesUiState(
            isLoading = false,
            templates = templates,
            startingTemplateId = startingId,
            errorMessage = error
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(WorkoutTemplatesUiState(isLoading = false, errorMessage = error.message))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutTemplatesUiState()
    )

    fun startTemplate(template: WorkoutTemplate, onStarted: (String) -> Unit) {
        if (startingTemplateId.value != null) return
        viewModelScope.launch {
            startingTemplateId.value = template.id
            try {
                val currentTemplate = checkNotNull(templateRepository.getTemplate(template.id)) {
                    "This workout no longer exists."
                }
                val profile = userProfileRepository.getProfileOnce()
                val session = workoutRepository.startWorkoutFromTemplate(currentTemplate, profile)
                onStarted(session.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: "Unable to start this workout."
            } finally {
                startingTemplateId.value = null
            }
        }
    }

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            try {
                templateRepository.deleteTemplate(templateId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value = error.message ?: "Unable to delete this workout."
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
            userProfileRepository: UserProfileRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WorkoutTemplatesViewModel(
                    templateRepository,
                    workoutRepository,
                    userProfileRepository
                ) as T
        }
    }
}
