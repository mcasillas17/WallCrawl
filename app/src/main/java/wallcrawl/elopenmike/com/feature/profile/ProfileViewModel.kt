package wallcrawl.elopenmike.com.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidanceResult
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidanceSource
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.feature.conditions.PendingCondition
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
    private val userProfileRepository: UserProfileRepository,
    conditionGuidanceSource: ConditionGuidanceSource? = null
) : ViewModel() {

    private val capabilityEditorFlow = MutableStateFlow(CapabilityEditorState())
    private val conditionEditorFlow = MutableStateFlow(ConditionEditorState())
    // Capability saves also replace the equipment list as part of a whole-profile write.
    private val equipmentEdits = Mutex()

    val uiState: StateFlow<ProfileUiState> = combine(
        userProfileRepository.getUserProfile(),
        capabilityEditorFlow,
        conditionEditorFlow
    ) { profile, editor, conditions ->
        val state: ProfileUiState = ProfileUiState.Success(
            profile = profile,
            isSaving = editor.isSaving,
            movementCapabilityDraft = editor.draft,
            movementCapabilityError = editor.error,
            availableEquipmentOptions = StandardEquipment.ALL,
            availableMuscleOptions = StandardMuscles.PRIORITY_OPTIONS,
            availableConstraintOptions = TrainingConstraint.entries,
            conditionGuidance = conditions.guidance,
            pendingCondition = conditions.pending,
            safetyErrorRes = conditions.errorRes
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

    init {
        conditionGuidanceSource?.let { source ->
            viewModelScope.launch {
                val result = try {
                    source.guidance()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    ConditionGuidanceResult.Unavailable
                }
                conditionEditorFlow.value = conditionEditorFlow.value.copy(guidance = result)
            }
        }
    }

    /** Starts adding [id]. Nothing is saved until the warning signs are acknowledged and confirmed. */
    fun selectCondition(id: String) {
        val guidance = conditionEditorFlow.value.guidance as? ConditionGuidanceResult.Loaded ?: return
        if (id !in guidance.guidance.conditions) return
        conditionEditorFlow.value = conditionEditorFlow.value.copy(pending = PendingCondition(id))
    }

    fun setConditionAcknowledged(acknowledged: Boolean) {
        val pending = conditionEditorFlow.value.pending ?: return
        conditionEditorFlow.value = conditionEditorFlow.value.copy(
            pending = pending.copy(acknowledged = acknowledged)
        )
    }

    fun cancelPendingCondition() {
        conditionEditorFlow.value = conditionEditorFlow.value.copy(pending = null)
    }

    fun confirmPendingCondition() {
        val pending = conditionEditorFlow.value.pending?.takeIf { it.acknowledged } ?: return
        viewModelScope.launch {
            try {
                userProfileRepository.editReportedConditions { it + pending.id }
                conditionEditorFlow.value =
                    conditionEditorFlow.value.copy(pending = null, errorRes = null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The prompt stays open so the user can try again; nothing was saved.
                conditionEditorFlow.value =
                    conditionEditorFlow.value.copy(errorRes = R.string.profile_condition_save_error)
            }
        }
    }

    /** Removing never asks for acknowledgement, and works whether or not the overlay loaded. */
    fun removeCondition(id: String) {
        viewModelScope.launch {
            try {
                userProfileRepository.editReportedConditions { it - id }
                conditionEditorFlow.value = conditionEditorFlow.value.copy(errorRes = null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                conditionEditorFlow.value =
                    conditionEditorFlow.value.copy(errorRes = R.string.profile_condition_save_error)
            }
        }
    }

    /** Undoes a pain-stop exclusion (or any other): the exercise may be planned again. */
    fun removeExcludedExercise(exerciseId: String) {
        viewModelScope.launch {
            try {
                userProfileRepository.editExcludedExercises { it - exerciseId }
                conditionEditorFlow.value = conditionEditorFlow.value.copy(errorRes = null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                conditionEditorFlow.value =
                    conditionEditorFlow.value.copy(errorRes = R.string.profile_exclusion_save_error)
            }
        }
    }

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
            userProfileRepository: UserProfileRepository,
            conditionGuidanceSource: ConditionGuidanceSource
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProfileViewModel(userProfileRepository, conditionGuidanceSource) as T
            }
        }
    }

    private data class ConditionEditorState(
        val guidance: ConditionGuidanceResult? = null,
        val pending: PendingCondition? = null,
        @androidx.annotation.StringRes val errorRes: Int? = null
    )

    private data class CapabilityEditorState(
        val draft: MovementCapabilities? = null,
        val isSaving: Boolean = false,
        val error: ProfileCapabilityError? = null
    )
}
