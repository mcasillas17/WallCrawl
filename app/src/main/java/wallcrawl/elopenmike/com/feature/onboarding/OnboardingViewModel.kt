package wallcrawl.elopenmike.com.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.WeightUnit

/**
 * Drives first-run onboarding. Nothing here reaches Today: the profile this produces
 * only becomes usable for planning once [complete] persists it with
 * `onboardingCompleted = true`.
 */
class OnboardingViewModel(
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val mutableState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    fun updateName(name: String) {
        mutableState.value = mutableState.value.copy(name = name.take(NAME_MAX_LENGTH), error = null)
    }

    fun toggleGoal(goal: FitnessGoal) {
        val current = mutableState.value.goals
        val updated = if (goal in current) {
            if (current.size > 1) current - goal else current
        } else {
            current + goal
        }
        mutableState.value = mutableState.value.copy(goals = updated, error = null)
    }

    fun updateGoals(goals: Set<FitnessGoal>) {
        if (goals.isNotEmpty()) {
            mutableState.value = mutableState.value.copy(goals = goals, error = null)
        }
    }

    fun updateGoal(goal: FitnessGoal) {
        updateGoals(setOf(goal))
    }

    fun updateExperience(experience: ExperienceLevel) {
        mutableState.value = mutableState.value.copy(experience = experience, error = null)
    }

    fun updateDaysPerWeek(days: Int) {
        mutableState.value = mutableState.value.copy(daysPerWeek = days, error = null)
    }

    fun updateDurationMinutes(minutes: Int) {
        mutableState.value = mutableState.value.copy(durationMinutes = minutes, error = null)
    }

    fun updateUnit(unit: WeightUnit) {
        mutableState.value = mutableState.value.copy(unit = unit, error = null)
    }

    fun toggleEquipment(equipment: String) {
        val current = mutableState.value.equipment
        val updated = if (equipment in current) current - equipment else current + equipment
        mutableState.value = mutableState.value.copy(equipment = updated, error = null)
    }

    fun toggleConstraint(constraint: TrainingConstraint) {
        val current = mutableState.value.constraints
        val updated = if (constraint in current) current - constraint else current + constraint
        mutableState.value = mutableState.value.copy(constraints = updated, error = null)
    }

    fun updateReturningAfterBreakWeeks(weeks: Int) {
        mutableState.value = mutableState.value.copy(returningAfterBreakWeeks = weeks, error = null)
    }

    fun selectAllEquipment() {
        mutableState.value = mutableState.value.copy(
            equipment = mutableState.value.equipmentOptions.toSet(),
            error = null
        )
    }

    fun resetEquipmentToBodyweight() {
        mutableState.value = mutableState.value.copy(
            equipment = setOf(wallcrawl.elopenmike.com.core.model.StandardEquipment.BODYWEIGHT),
            error = null
        )
    }

    fun clearConstraints() {
        mutableState.value = mutableState.value.copy(constraints = emptySet(), error = null)
    }

    fun nextStep() {
        val current = mutableState.value.currentStep
        val nextOrdinal = current.ordinal + 1
        if (nextOrdinal < OnboardingStep.entries.size) {
            mutableState.value = mutableState.value.copy(
                currentStep = OnboardingStep.entries[nextOrdinal],
                error = null
            )
        } else {
            complete()
        }
    }

    fun previousStep() {
        val current = mutableState.value.currentStep
        val prevOrdinal = current.ordinal - 1
        if (prevOrdinal >= 0) {
            mutableState.value = mutableState.value.copy(
                currentStep = OnboardingStep.entries[prevOrdinal],
                error = null
            )
        }
    }

    fun goToStep(step: OnboardingStep) {
        mutableState.value = mutableState.value.copy(currentStep = step, error = null)
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
        equipment: Set<String> = mutableState.value.equipment,
        constraints: Set<TrainingConstraint> = mutableState.value.constraints,
        returningAfterBreakWeeks: Int = mutableState.value.returningAfterBreakWeeks
    ) {
        mutableState.value = mutableState.value.copy(
            name = name,
            goals = goals,
            experience = experience,
            daysPerWeek = daysPerWeek,
            durationMinutes = durationMinutes,
            unit = unit,
            equipment = equipment,
            constraints = constraints,
            returningAfterBreakWeeks = returningAfterBreakWeeks
        )

        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            try {
                val current = userProfileRepository.getProfileOnce()
                val profile = current.copy(
                    name = name,
                    goals = goals,
                    experienceLevel = experience,
                    daysPerWeek = daysPerWeek,
                    preferredDurationMinutes = durationMinutes,
                    preferredUnit = unit,
                    availableEquipment = equipment.toList(),
                    trainingConstraints = constraints,
                    returningAfterBreakWeeks = returningAfterBreakWeeks,
                    onboardingCompleted = true
                )
                userProfileRepository.saveProfile(profile)
                mutableState.value = mutableState.value.copy(isSaving = false, isComplete = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Couldn't save your profile. Try again."
                )
            }
        }
    }

    companion object {
        private const val NAME_MAX_LENGTH = 60

        fun provideFactory(
            userProfileRepository: UserProfileRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OnboardingViewModel(userProfileRepository) as T
            }
        }
    }
}
