package wallcrawl.elopenmike.com.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import wallcrawl.elopenmike.com.core.ui.*
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.ai.ConditionPlanningPolicy
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
    private val userProfileRepository: UserProfileRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    conditionSupport: suspend () -> ConditionSupportCatalog = { ConditionSupportCatalog("empty", emptyMap()) },
    workoutRepository: WorkoutRepository? = null
) : ViewModel() {

    private val conditionState = MutableStateFlow(ConditionUiState(
        draft = savedStateHandle.get<List<String>>(CONDITION_DRAFT)?.let(::decodeConditionDraft),
        reassessmentEventId = savedStateHandle[REASSESS_EVENT],
        reassessmentAnswers = savedStateHandle.get<List<String>>(REASSESS_ANSWERS)
            ?.let { decodeConditionDraft(it).single().answers } ?: emptyMap()
    ))
    val conditions: StateFlow<ConditionUiState> = conditionState

    init {
        viewModelScope.launch {
            try {
                val records = conditionSupport().records.values.filter(ConditionPlanningPolicy()::supports)
                conditionState.value = conditionState.value.copy(records = records)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { conditionState.value = conditionState.value.copy(loadFailed = true) }
        }
        if (workoutRepository != null) viewModelScope.launch {
            workoutRepository.observePendingConditionReassessments(UserProfile.DEFAULT_PROFILE_ID)
                .catch { error ->
                    if (error is CancellationException) throw error
                    conditionState.value = conditionState.value.copy(pendingFailed = true)
                }.collect { rows -> conditionState.value = conditionState.value.copy(pending = rows, pendingFailed = false) }
        }
    }

    fun startConditionEditing() {
        val profile = (uiState.value as? ProfileUiState.Success)?.profile ?: return
        if (conditionState.value.draft == null) updateConditionDraft(profile.activeConditions)
    }

    fun updateConditionDraft(selections: List<SelectedCondition>) {
        if (conditionState.value.saving) return
        savedStateHandle[CONDITION_DRAFT] = encodeConditionDraft(selections)
        conditionState.value = conditionState.value.copy(draft = selections.toList(), saveFailed = false)
    }

    fun cancelConditionEditing() {
        if (conditionState.value.saving) return
        savedStateHandle.remove<List<String>>(CONDITION_DRAFT)
        conditionState.value = conditionState.value.copy(draft = null, saveFailed = false)
    }

    fun saveConditions() {
        val draft = conditionState.value.draft ?: return
        if (conditionState.value.saving) return
        conditionState.value = conditionState.value.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                userProfileRepository.updateActiveConditions(draft)
                conditionState.value = conditionState.value.copy(saving = false)
                cancelConditionEditing()
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { conditionState.value = conditionState.value.copy(saving = false, saveFailed = true) }
        }
    }

    fun updateReassessmentAnswers(eventId: String, answers: Map<String, String?>) {
        if (conditionState.value.saving) return
        if (answers.size > SelectedCondition.MAX_ANSWERS) {
            conditionState.value = conditionState.value.copy(reassessmentAnswerLimitEventId = eventId)
            return
        }
        val encoded = encodeConditionDraft(listOf(SelectedCondition("answers", 1, answers)))
        savedStateHandle[REASSESS_EVENT] = eventId
        savedStateHandle[REASSESS_ANSWERS] = encoded
        conditionState.value = conditionState.value.copy(reassessmentEventId = eventId,
            reassessmentAnswers = answers.toMap(), reassessmentRefusal = null, reassessmentAnswerLimitEventId = null, saveFailed = false)
    }

    fun reassess(event: PendingConditionReassessment) {
        val state = conditionState.value
        if (state.saving || state.reassessmentEventId != event.eventId) return
        conditionState.value = state.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                userProfileRepository.reassessCondition(event.conditionId, event.exerciseId, event.eventId, state.reassessmentAnswers)
                savedStateHandle.remove<String>(REASSESS_EVENT)
                savedStateHandle.remove<List<String>>(REASSESS_ANSWERS)
                conditionState.value = conditionState.value.copy(saving = false, reassessmentEventId = null,
                    reassessmentAnswers = emptyMap(), reassessmentRefusal = null, reassessmentAnswerLimitEventId = null)
            } catch (error: CancellationException) { throw error
            } catch (error: ConditionReassessmentRefused) {
                conditionState.value = conditionState.value.copy(saving = false, reassessmentRefusal = error)
            } catch (_: Exception) { conditionState.value = conditionState.value.copy(saving = false, saveFailed = true) }
        }
    }

    private val capabilityEditorFlow = MutableStateFlow(CapabilityEditorState())
    // Capability saves also replace the equipment list as part of a whole-profile write.
    private val equipmentEdits = Mutex()

    private val exclusionSaveFailed = MutableStateFlow(false)

    val uiState: StateFlow<ProfileUiState> = combine(
        userProfileRepository.getUserProfile(),
        capabilityEditorFlow,
        exclusionSaveFailed
    ) { profile, editor, exclusionFailed ->
        val state: ProfileUiState = ProfileUiState.Success(
            profile = profile,
            isSaving = editor.isSaving,
            movementCapabilityDraft = editor.draft,
            movementCapabilityError = editor.error,
            exclusionSaveFailed = exclusionFailed,
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

    fun removeReportedCondition(conditionId: String) {
        viewModelScope.launch {
            try {
                userProfileRepository.removeReportedCondition(conditionId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                conditionState.value = conditionState.value.copy(saveFailed = true)
            }
        }
    }

    fun removeExcludedExercise(exerciseId: String) {
        viewModelScope.launch {
            try {
                userProfileRepository.setExerciseExcluded(exerciseId, false)
                exclusionSaveFailed.value = false
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                exclusionSaveFailed.value = true
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
            if (current.unresolvedTrainingConstraintsRaw != null) return@launch
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

        capabilityEditorFlow.value = capabilityEditorFlow.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
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
        private const val CONDITION_DRAFT = "profile.conditions"
        private const val REASSESS_EVENT = "profile.reassessment.event"
        private const val REASSESS_ANSWERS = "profile.reassessment.answers"
        fun provideFactory(
            userProfileRepository: UserProfileRepository,
            conditionSupport: suspend () -> ConditionSupportCatalog = { ConditionSupportCatalog("empty", emptyMap()) },
            workoutRepository: WorkoutRepository? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(userProfileRepository, createSavedStateHandle(), conditionSupport, workoutRepository) }
        }
    }

    private data class CapabilityEditorState(
        val draft: MovementCapabilities? = null,
        val isSaving: Boolean = false,
        val error: ProfileCapabilityError? = null
    )
}
