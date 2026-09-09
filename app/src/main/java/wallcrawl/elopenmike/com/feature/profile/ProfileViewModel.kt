package wallcrawl.elopenmike.com.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.ProfileGender
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProfileViewModel(
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val capabilityEditorFlow = MutableStateFlow(CapabilityEditorState())
    // Capability saves also replace the equipment list as part of a whole-profile write.
    private val equipmentEdits = Mutex()

    val uiState: StateFlow<ProfileUiState> = combine(
        userProfileRepository.getUserProfile(),
        capabilityEditorFlow
    ) { profile, editor ->
        val state: ProfileUiState = ProfileUiState.Success(
            profile = profile,
            isSaving = editor.isSaving,
            movementCapabilityDraft = editor.draft,
            movementCapabilityError = editor.error,
            availableEquipmentOptions = StandardEquipment.ALL,
            availableMuscleOptions = StandardMuscles.PRIORITY_OPTIONS,
            availableConstraintOptions = TrainingConstraint.entries
        )
        state
    }.catch { error ->
        // The error carried no message anyone could read before; it now surfaces a
        // resource-backed one, the same way every other screen reports a failed load.
        if (error is CancellationException) throw error
        emit(ProfileUiState.Error())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState.Loading
    )

    fun updateThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            userProfileRepository.updateThemePreference(theme)
        }
    }

    fun updateGender(gender: ProfileGender) {
        viewModelScope.launch { userProfileRepository.updateGender(gender) }
    }


    fun toggleGoal(goal: FitnessGoal) {
        viewModelScope.launch {
            val current = userProfileRepository.getProfileOnce()
            val currentGoals = current.goals
            val updated = if (goal in currentGoals) {
                if (currentGoals.size > 1) currentGoals - goal else currentGoals
            } else {
                currentGoals + goal
            }
            userProfileRepository.updateGoals(updated)
        }
    }

    fun updateGoals(goals: Set<FitnessGoal>) {
        if (goals.isEmpty()) return
        viewModelScope.launch {
            userProfileRepository.updateGoals(goals)
        }
    }

    fun updateGoal(goal: FitnessGoal) {
        viewModelScope.launch {
            userProfileRepository.updatePrimaryGoal(goal)
        }
    }

    fun updateExperience(experience: ExperienceLevel) {
        viewModelScope.launch {
            userProfileRepository.updateExperienceLevel(experience)
        }
    }

    fun updateDuration(durationMinutes: Int) {
        viewModelScope.launch {
            userProfileRepository.updatePreferredDuration(durationMinutes)
        }
    }

    fun updateDaysPerWeek(days: Int) {
        viewModelScope.launch {
            userProfileRepository.updateDaysPerWeek(days)
        }
    }

    fun updateUnit(unit: WeightUnit) {
        viewModelScope.launch {
            userProfileRepository.updateUnit(unit)
        }
    }

    fun toggleEquipment(equipment: String) {
        viewModelScope.launch {
            equipmentEdits.withLock {
                val current = userProfileRepository.getProfileOnce()
                val updated = if (equipment in current.availableEquipment) {
                    current.availableEquipment - equipment
                } else {
                    current.availableEquipment + equipment
                }
                userProfileRepository.updateEquipment(updated)
            }
        }
    }

    fun setMusclePriority(muscle: String, priority: PriorityLevel) {
        viewModelScope.launch {
            val current = userProfileRepository.getProfileOnce()
            val updated = current.musclePriorities.toMutableMap().apply {
                put(muscle, priority)
            }
            userProfileRepository.updateMusclePriorities(updated)
        }
    }

    fun toggleTrainingConstraint(constraint: TrainingConstraint) {
        viewModelScope.launch {
            val current = userProfileRepository.getProfileOnce()
            val updated = if (constraint in current.trainingConstraints) {
                current.trainingConstraints - constraint
            } else {
                current.trainingConstraints + constraint
            }
            userProfileRepository.updateTrainingConstraints(updated)
        }
    }

    fun updateReturningAfterBreakWeeks(weeks: Int) {
        viewModelScope.launch {
            userProfileRepository.updateReturningAfterBreakWeeks(weeks)
        }
    }

    fun startMovementCapabilityEditing() {
        val profile = (uiState.value as? ProfileUiState.Success)?.profile ?: return
        capabilityEditorFlow.value = CapabilityEditorState(
            draft = profile.movementCapabilities
        )
    }

    fun updateMovementCapabilityDraft(
        type: MovementCapabilityType,
        level: CapabilityLevel
    ) {
        val editor = capabilityEditorFlow.value
        val draft = editor.draft ?: return
        if (editor.isSaving) return
        capabilityEditorFlow.value = editor.copy(
            draft = MovementCapabilities.from(draft.values + (type to level)),
            error = null
        )
    }

    fun cancelMovementCapabilityEditing() {
        if (capabilityEditorFlow.value.isSaving) return
        capabilityEditorFlow.value = CapabilityEditorState()
    }

    fun saveMovementCapabilities() {
        val draft = capabilityEditorFlow.value.draft ?: return
        if (capabilityEditorFlow.value.isSaving) return

        viewModelScope.launch {
            capabilityEditorFlow.value = capabilityEditorFlow.value.copy(
                isSaving = true,
                error = null
            )
            try {
                equipmentEdits.withLock {
                    val current = userProfileRepository.getProfileOnce()
                    userProfileRepository.saveProfile(
                        current.copy(movementCapabilities = draft)
                    )
                }
                capabilityEditorFlow.value = CapabilityEditorState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                capabilityEditorFlow.value = capabilityEditorFlow.value.copy(
                    isSaving = false,
                    error = ProfileCapabilityError.INVALID
                )
            } catch (error: Exception) {
                capabilityEditorFlow.value = capabilityEditorFlow.value.copy(
                    isSaving = false,
                    error = ProfileCapabilityError.SAVE_FAILED
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            userProfileRepository: UserProfileRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProfileViewModel(userProfileRepository) as T
            }
        }
    }

    private data class CapabilityEditorState(
        val draft: MovementCapabilities? = null,
        val isSaving: Boolean = false,
        val error: ProfileCapabilityError? = null
    )
}
