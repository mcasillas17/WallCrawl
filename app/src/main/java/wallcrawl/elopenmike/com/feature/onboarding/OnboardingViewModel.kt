package wallcrawl.elopenmike.com.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.StarterProfile

/**
 * Drives the one-screen basic setup. Nothing here reaches Today: the starter profile only
 * becomes usable for planning once [save] persists it with `onboardingCompleted = true`.
 */
class OnboardingViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val mutableState = MutableStateFlow(restoreDraft(savedStateHandle))
    val uiState: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    fun toggleGoal(goal: FitnessGoal) {
        updateState { it.copy(goals = if (goal in it.goals) it.goals - goal else it.goals + goal, error = null) }
    }

    fun updateDaysPerWeek(days: Int) {
        updateState { it.copy(daysPerWeek = days, error = null) }
    }

    fun updateDurationMinutes(minutes: Int) {
        updateState { it.copy(durationMinutes = minutes, error = null) }
    }

    /** Persists the starter profile in one save. [regionCountry] picks the weight unit. */
    fun save(regionCountry: String) {
        val state = mutableState.value
        if (!state.canSave) return

        viewModelScope.launch {
            updateState { it.copy(isSaving = true, error = null) }
            try {
                val profile = StarterProfile.apply(
                    base = userProfileRepository.getProfileOnce(),
                    goals = state.goals,
                    daysPerWeek = state.daysPerWeek,
                    durationMinutes = state.durationMinutes,
                    regionCountry = regionCountry
                )
                userProfileRepository.saveProfile(profile)
                updateState { it.copy(isSaving = false, isComplete = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                updateState { it.copy(isSaving = false, error = OnboardingError.INVALID_FIELD) }
            } catch (e: Exception) {
                updateState { it.copy(isSaving = false, error = OnboardingError.SAVE_FAILED) }
            }
        }
    }

    private fun updateState(transform: (OnboardingUiState) -> OnboardingUiState) {
        mutableState.value = transform(mutableState.value)
        persistDraft(mutableState.value)
    }

    private fun persistDraft(state: OnboardingUiState) {
        savedStateHandle[STATE_GOALS] = ArrayList(state.goals.map { it.name })
        savedStateHandle[STATE_DAYS_PER_WEEK] = state.daysPerWeek
        savedStateHandle[STATE_DURATION_MINUTES] = state.durationMinutes
    }

    companion object {
        private const val STATE_GOALS = "onboarding.goals"
        private const val STATE_DAYS_PER_WEEK = "onboarding.daysPerWeek"
        private const val STATE_DURATION_MINUTES = "onboarding.durationMinutes"

        private fun restoreDraft(savedStateHandle: SavedStateHandle): OnboardingUiState {
            val defaults = OnboardingUiState()
            return defaults.copy(
                goals = savedStateHandle.get<ArrayList<String>>(STATE_GOALS).orEmpty()
                    .mapNotNull { name -> FitnessGoal.entries.firstOrNull { it.name == name } }.toSet(),
                daysPerWeek = savedStateHandle[STATE_DAYS_PER_WEEK] ?: defaults.daysPerWeek,
                durationMinutes = savedStateHandle[STATE_DURATION_MINUTES]
                    ?: defaults.durationMinutes
            )
        }

        fun provideFactory(
            userProfileRepository: UserProfileRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OnboardingViewModel(
                    userProfileRepository = userProfileRepository,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
