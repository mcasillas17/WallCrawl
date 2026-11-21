package wallcrawl.elopenmike.com.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
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
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.ProfileGender

/**
 * Drives first-run onboarding. Nothing here reaches Today: the profile this produces
 * only becomes usable for planning once [complete] persists it with
 * `onboardingCompleted = true`.
 */
class OnboardingViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val mutableState = MutableStateFlow(restoreDraft(savedStateHandle))
    val uiState: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    fun updateName(name: String) {
        updateState { it.copy(name = name.take(NAME_MAX_LENGTH), error = null) }
    }

    fun updateGender(gender: ProfileGender) {
        updateState { it.copy(gender = gender) }
    }


    fun toggleGoal(goal: FitnessGoal) {
        val current = mutableState.value.goals
        val updated = if (goal in current) {
            if (current.size > 1) current - goal else current
        } else {
            current + goal
        }
        updateState { it.copy(goals = updated, error = null) }
    }

    fun updateGoals(goals: Set<FitnessGoal>) {
        if (goals.isNotEmpty()) {
            updateState { it.copy(goals = goals, error = null) }
        }
    }

    fun updateGoal(goal: FitnessGoal) {
        updateGoals(setOf(goal))
    }

    fun updateExperience(experience: ExperienceLevel) {
        updateState { it.copy(experience = experience, error = null) }
    }

    fun updateDaysPerWeek(days: Int) {
        updateState { it.copy(daysPerWeek = days, error = null) }
    }

    fun updateDurationMinutes(minutes: Int) {
        updateState { it.copy(durationMinutes = minutes, error = null) }
    }

    fun updateUnit(unit: WeightUnit) {
        updateState { it.copy(unit = unit, error = null) }
    }

    fun nextStep() {
        val state = mutableState.value
        if (!state.canProceedCurrentStep) {
            updateState {
                it.copy(
                    error = OnboardingError.REQUIRED_FIELD
                )
            }
            return
        }
        val current = state.currentStep
        val nextOrdinal = current.ordinal + 1
        if (nextOrdinal < OnboardingStep.entries.size) {
            updateState { it.copy(
                currentStep = OnboardingStep.entries[nextOrdinal],
                error = null
            ) }
        } else {
            complete()
        }
    }

    fun previousStep() {
        val current = mutableState.value.currentStep
        val prevOrdinal = current.ordinal - 1
        if (prevOrdinal >= 0) {
            updateState { it.copy(
                currentStep = OnboardingStep.entries[prevOrdinal],
                error = null
            ) }
        }
    }

    fun goToStep(step: OnboardingStep) {
        updateState { it.copy(currentStep = step, error = null) }
    }

    /**
     * Finishes onboarding with the given inputs (defaulting to whatever is already in
     * [uiState]) and persists them as one atomic profile save. Confirmed starting loads
     * are intentionally not collected here.
     */
    fun complete(
        name: String = mutableState.value.name,
        goals: Set<FitnessGoal> = mutableState.value.goals,
        experience: ExperienceLevel = mutableState.value.experience,
        daysPerWeek: Int = mutableState.value.daysPerWeek,
        durationMinutes: Int = mutableState.value.durationMinutes,
        unit: WeightUnit = mutableState.value.unit,
        gender: ProfileGender = mutableState.value.gender
    ) {
        updateState { it.copy(
            name = name,
            goals = goals,
            experience = experience,
            daysPerWeek = daysPerWeek,
            durationMinutes = durationMinutes,
            unit = unit,
            gender = gender,
        ) }

        viewModelScope.launch {
            updateState { it.copy(isSaving = true, error = null) }
            try {
                val current = userProfileRepository.getProfileOnce()
                val profile = current.copy(
                    name = name,
                    goals = goals,
                    experienceLevel = experience,
                    gender = gender,
                    daysPerWeek = daysPerWeek,
                    preferredDurationMinutes = durationMinutes,
                    preferredUnit = unit,
                    onboardingCompleted = true
                )
                userProfileRepository.saveProfile(profile)
                updateState { it.copy(isSaving = false, isComplete = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                updateState {
                    it.copy(
                        isSaving = false,
                        error = OnboardingError.INVALID_FIELD
                    )
                }
            } catch (e: Exception) {
                updateState { it.copy(
                    isSaving = false,
                    error = OnboardingError.SAVE_FAILED
                ) }
            }
        }
    }

    private fun updateState(transform: (OnboardingUiState) -> OnboardingUiState) {
        mutableState.value = transform(mutableState.value)
        persistDraft(mutableState.value)
    }

    private fun persistDraft(state: OnboardingUiState) {
        savedStateHandle[STATE_CURRENT_STEP] = state.currentStep.name
        savedStateHandle[STATE_NAME] = state.name
        savedStateHandle[STATE_GENDER] = state.gender.name
        savedStateHandle[STATE_GOALS] = ArrayList(state.goals.map { it.name })
        savedStateHandle[STATE_EXPERIENCE] = state.experience.name
        savedStateHandle[STATE_DAYS_PER_WEEK] = state.daysPerWeek
        savedStateHandle[STATE_DURATION_MINUTES] = state.durationMinutes
        savedStateHandle[STATE_UNIT] = state.unit.name
    }

    companion object {
        private const val NAME_MAX_LENGTH = 60
        private const val STATE_CURRENT_STEP = "onboarding.currentStep"
        private const val STATE_NAME = "onboarding.name"
        private const val STATE_GENDER = "onboarding.gender"
        private const val STATE_GOALS = "onboarding.goals"
        private const val STATE_EXPERIENCE = "onboarding.experience"
        private const val STATE_DAYS_PER_WEEK = "onboarding.daysPerWeek"
        private const val STATE_DURATION_MINUTES = "onboarding.durationMinutes"
        private const val STATE_UNIT = "onboarding.unit"

        private fun restoreDraft(savedStateHandle: SavedStateHandle): OnboardingUiState {
            val defaults = OnboardingUiState()
            return defaults.copy(
                currentStep = enumValueOrDefault(
                    savedStateHandle[STATE_CURRENT_STEP],
                    defaults.currentStep
                ),
                name = savedStateHandle[STATE_NAME] ?: defaults.name,
                gender = enumValueOrDefault(savedStateHandle[STATE_GENDER], defaults.gender),
                goals = enumSetOrDefault(savedStateHandle[STATE_GOALS], defaults.goals),
                experience = enumValueOrDefault(
                    savedStateHandle[STATE_EXPERIENCE],
                    defaults.experience
                ),
                daysPerWeek = savedStateHandle[STATE_DAYS_PER_WEEK] ?: defaults.daysPerWeek,
                durationMinutes = savedStateHandle[STATE_DURATION_MINUTES]
                    ?: defaults.durationMinutes,
                unit = enumValueOrDefault(savedStateHandle[STATE_UNIT], defaults.unit)
            )
        }

        internal inline fun <reified T : Enum<T>> enumValueOrDefault(
            persistedName: String?,
            defaultValue: T
        ): T {
            if (persistedName == null) return defaultValue
            return try {
                enumValueOf<T>(persistedName)
            } catch (error: IllegalArgumentException) {
                defaultValue
            }
        }

        internal inline fun <reified T : Enum<T>> enumSetOrDefault(
            persistedNames: List<String>?,
            defaultValue: Set<T>
        ): Set<T> {
            if (persistedNames == null) return defaultValue
            return persistedNames.mapNotNull { name ->
                try {
                    enumValueOf<T>(name)
                } catch (error: IllegalArgumentException) {
                    null
                }
            }.toSet()
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
