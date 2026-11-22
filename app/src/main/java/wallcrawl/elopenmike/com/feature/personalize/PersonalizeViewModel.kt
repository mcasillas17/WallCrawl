package wallcrawl.elopenmike.com.feature.personalize

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
import wallcrawl.elopenmike.com.core.ai.ConditionPlanningPolicy
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ConditionSupportCatalog
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.ReportedCondition
import wallcrawl.elopenmike.com.core.model.SelectedCondition
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.ui.decodeConditionDraft
import wallcrawl.elopenmike.com.core.ui.encodeConditionDraft
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingError
import wallcrawl.elopenmike.com.core.ui.enumSetOrDefault
import wallcrawl.elopenmike.com.core.ui.enumValueOrDefault

/**
 * Drives the four-step personalization flow. The draft starts from the current profile
 * and nothing is written until [finish] saves it once with `personalizationCompleted = true`.
 */
class PersonalizeViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    conditionSupport: suspend () -> ConditionSupportCatalog = { ConditionSupportCatalog("empty", emptyMap()) }
) : ViewModel() {

    private val mutableState = MutableStateFlow(restoreDraft(savedStateHandle))
    val uiState: StateFlow<PersonalizeUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val current = userProfileRepository.getProfileOnce()
                // A restored draft already holds the user's edits; only a fresh one copies the profile.
                updateState { state ->
                    val retained = state.copy(
                        retainedConditions = current.activeConditions,
                        retainedReports = current.reportedConditions
                    )
                    if (state.isLoaded) retained else retained.withDraftFrom(current)
                }
                val records = conditionSupport().records.values.filter(ConditionPlanningPolicy()::supports)
                updateState { it.copy(conditionRecords = records) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { updateState { it.copy(conditionLoadFailed = true) } }
        }
    }

    fun setReportedCondition(id: String, selected: Boolean) {
        if (mutableState.value.isSaving || (selected && id !in ReportedCondition.ids)) return
        edit { state ->
            if (selected) state.copy(addedReports = state.addedReports + id, removedReports = state.removedReports - id)
            else state.copy(addedReports = state.addedReports - id, removedReports = state.removedReports + id)
        }
    }

    fun updateConditions(selections: List<SelectedCondition>) {
        if (!mutableState.value.isSaving) edit { it.copy(conditionDraft = selections.toList()) }
    }

    fun updateName(name: String) {
        edit { it.copy(name = UserProfile.nameInput(name)) }
    }

    fun updateExperience(experience: ExperienceLevel) {
        edit { it.copy(experience = experience, error = null) }
    }

    fun toggleEquipment(equipment: String) {
        edit { state ->
            val current = state.equipment
            state.copy(equipment = if (equipment in current) current - equipment else current + equipment, error = null)
        }
    }

    fun toggleConstraint(constraint: TrainingConstraint) {
        edit { state ->
            val current = state.constraints
            state.copy(constraints = if (constraint in current) current - constraint else current + constraint, error = null)
        }
    }

    fun updateReturningAfterBreakWeeks(weeks: Int) {
        edit { it.copy(returningAfterBreakWeeks = weeks, error = null) }
    }

    fun updateMovementCapability(type: MovementCapabilityType, level: CapabilityLevel) {
        edit { state -> state.copy(capabilityAnswers = state.capabilityAnswers + (type to level), error = null) }
    }

    fun selectFullGymEquipment() {
        edit { state ->
            state.copy(
                equipment = StandardEquipment.FULL_GYM.toSet() +
                    state.equipment.filter { it in StandardEquipment.BAND_SETUPS || it in StandardEquipment.SUPPORT_ASSEMBLY },
                error = null
            )
        }
    }

    fun resetEquipmentToBodyweight() {
        edit { it.copy(equipment = setOf(StandardEquipment.BODYWEIGHT), error = null) }
    }

    fun clearConstraints() {
        edit { it.copy(constraints = emptySet(), error = null) }
    }

    fun nextStep() {
        val state = mutableState.value
        if (!state.isLoaded) return
        if (!state.canProceedCurrentStep) {
            updateState {
                it.copy(
                    error = if (state.currentStep == PersonalizeStep.MOVEMENT) {
                        OnboardingError.MOVEMENT_REQUIRED
                    } else {
                        OnboardingError.REQUIRED_FIELD
                    }
                )
            }
            return
        }
        if (state.isLastStep) finish()
        else updateState { it.copy(currentStep = PersonalizeStep.entries[state.currentStep.ordinal + 1], error = null) }
    }

    /** Goes back one step. Returns false on the first step, where back leaves the flow. */
    fun previousStep(): Boolean {
        val state = mutableState.value
        if (state.currentStep.ordinal == 0) return false
        if (!state.isSaving) {
            updateState { it.copy(currentStep = PersonalizeStep.entries[state.currentStep.ordinal - 1], error = null) }
        }
        return true
    }

    /** Validates the movement answers, then saves every draft field in one profile write. */
    fun finish() {
        val draft = mutableState.value
        if (!draft.isLoaded || draft.isSaving) return
        if (draft.unansweredCapability != null) {
            updateState {
                it.copy(currentStep = PersonalizeStep.MOVEMENT, error = OnboardingError.MOVEMENT_REQUIRED)
            }
            return
        }

        viewModelScope.launch {
            updateState { it.copy(isSaving = true, error = null) }
            try {
                val current = userProfileRepository.getProfileOnce()
                userProfileRepository.saveProfile(
                    current.copy(
                        name = draft.name.trim().ifEmpty { current.name },
                        experienceLevel = draft.experience,
                        availableEquipment = draft.equipment.toList(),
                        trainingConstraints = draft.constraints,
                        returningAfterBreakWeeks = draft.returningAfterBreakWeeks,
                        movementCapabilities = MovementCapabilities.from(draft.capabilityAnswers),
                        reportedConditions = draft.applyReportEdits(current.reportedConditions),
                        activeConditions = draft.conditionDraft ?: current.activeConditions,
                        personalizationCompleted = true
                    )
                )
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

    /** A user edit. Ignored until the draft has been loaded, so the load cannot overwrite it. */
    private fun edit(transform: (PersonalizeUiState) -> PersonalizeUiState) {
        if (mutableState.value.isLoaded) updateState(transform)
    }

    private fun updateState(transform: (PersonalizeUiState) -> PersonalizeUiState) {
        mutableState.value = transform(mutableState.value)
        // Persist only a loaded draft: an unloaded one would restore as if it were the user's.
        if (mutableState.value.isLoaded) persistDraft(mutableState.value)
    }

    private fun persistDraft(state: PersonalizeUiState) {
        savedStateHandle[STATE_ADDED_REPORTS] = ArrayList(state.addedReports)
        savedStateHandle[STATE_REMOVED_REPORTS] = ArrayList(state.removedReports)
        state.conditionDraft?.let { savedStateHandle[STATE_CONDITIONS] = encodeConditionDraft(it) }
        savedStateHandle[STATE_CURRENT_STEP] = state.currentStep.name
        savedStateHandle[STATE_NAME] = state.name
        savedStateHandle[STATE_EXPERIENCE] = state.experience.name
        savedStateHandle[STATE_EQUIPMENT] = ArrayList(state.equipment)
        savedStateHandle[STATE_CONSTRAINTS] = ArrayList(state.constraints.map { it.name })
        savedStateHandle[STATE_BREAK_WEEKS] = state.returningAfterBreakWeeks
        savedStateHandle[STATE_CAPABILITY_ANSWERS] = ArrayList(
            state.capabilityAnswers.entries.map { (type, level) -> "${type.name}:${level.name}" }
        )
    }

    companion object {
        private const val STATE_ADDED_REPORTS = "personalize.addedReports"
        private const val STATE_REMOVED_REPORTS = "personalize.removedReports"
        private const val STATE_CONDITIONS = "personalize.conditions"
        private const val STATE_CURRENT_STEP = "personalize.currentStep"
        private const val STATE_NAME = "personalize.name"
        private const val STATE_EXPERIENCE = "personalize.experience"
        private const val STATE_EQUIPMENT = "personalize.equipment"
        private const val STATE_CONSTRAINTS = "personalize.constraints"
        private const val STATE_BREAK_WEEKS = "personalize.returningAfterBreakWeeks"
        private const val STATE_CAPABILITY_ANSWERS = "personalize.capabilityAnswers"

        /**
         * Copies the profile into a fresh draft. A starter profile's `LOW_IMPACT_ONLY` and its
         * unanswered (all-unknown) capabilities are not the user's choices, so neither is
         * pre-selected; the user picks them explicitly, as onboarding required.
         */
        private fun PersonalizeUiState.withDraftFrom(profile: UserProfile): PersonalizeUiState {
            val starter = !profile.personalizationCompleted
            return copy(
                isLoaded = true,
                // The model default is a placeholder, not the user's name, so it is not prefilled.
                name = UserProfile.displayName(profile.name),
                experience = profile.experienceLevel,
                equipment = profile.availableEquipment.toSet(),
                constraints = if (starter) profile.trainingConstraints - TrainingConstraint.LOW_IMPACT_ONLY
                    else profile.trainingConstraints,
                returningAfterBreakWeeks = profile.returningAfterBreakWeeks,
                capabilityAnswers = if (starter) emptyMap() else profile.movementCapabilities.values
            )
        }

        private fun restoreDraft(savedStateHandle: SavedStateHandle): PersonalizeUiState {
            val defaults = PersonalizeUiState()
            val step = savedStateHandle.get<String>(STATE_CURRENT_STEP) ?: return defaults
            return defaults.copy(
                isLoaded = true,
                addedReports = savedStateHandle.get<List<String>>(STATE_ADDED_REPORTS).orEmpty().intersect(ReportedCondition.ids),
                removedReports = savedStateHandle.get<List<String>>(STATE_REMOVED_REPORTS).orEmpty().toSet(),
                conditionDraft = savedStateHandle.get<List<String>>(STATE_CONDITIONS)?.let(::decodeConditionDraft),
                currentStep = enumValueOrDefault(step, defaults.currentStep),
                name = savedStateHandle[STATE_NAME] ?: defaults.name,
                experience = enumValueOrDefault(savedStateHandle[STATE_EXPERIENCE], defaults.experience),
                equipment = savedStateHandle.get<List<String>>(STATE_EQUIPMENT)?.toSet() ?: defaults.equipment,
                constraints = enumSetOrDefault(savedStateHandle[STATE_CONSTRAINTS], defaults.constraints),
                returningAfterBreakWeeks = savedStateHandle[STATE_BREAK_WEEKS] ?: defaults.returningAfterBreakWeeks,
                capabilityAnswers = decodeCapabilityAnswers(savedStateHandle[STATE_CAPABILITY_ANSWERS])
            )
        }

        private fun decodeCapabilityAnswers(
            persistedAnswers: List<String>?
        ): Map<MovementCapabilityType, CapabilityLevel> {
            if (persistedAnswers == null) return emptyMap()
            return persistedAnswers.mapNotNull { answer ->
                val parts = answer.split(':', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val type = try {
                    MovementCapabilityType.valueOf(parts[0])
                } catch (error: IllegalArgumentException) {
                    return@mapNotNull null
                }
                val level = try {
                    CapabilityLevel.valueOf(parts[1])
                } catch (error: IllegalArgumentException) {
                    return@mapNotNull null
                }
                type to level
            }.toMap()
        }

        fun provideFactory(
            userProfileRepository: UserProfileRepository,
            conditionSupport: suspend () -> ConditionSupportCatalog = { ConditionSupportCatalog("empty", emptyMap()) }
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PersonalizeViewModel(
                    userProfileRepository = userProfileRepository,
                    savedStateHandle = createSavedStateHandle(),
                    conditionSupport = conditionSupport
                )
            }
        }
    }
}
