package wallcrawl.elopenmike.com.feature.personalize

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.SelectedCondition
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingError
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalizeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val starter = UserProfile(
        personalizationCompleted = false,
        experienceLevel = ExperienceLevel.BEGINNER,
        trainingConstraints = setOf(TrainingConstraint.LOW_IMPACT_ONLY, TrainingConstraint.KNEE_SENSITIVE)
    )

    private fun TestScope.loaded(
        repository: RecordingUserProfileRepository,
        handle: SavedStateHandle = SavedStateHandle()
    ): PersonalizeViewModel = PersonalizeViewModel(repository, handle).also { advanceUntilIdle() }

    private fun answerAll(viewModel: PersonalizeViewModel, level: CapabilityLevel = CapabilityLevel.UNKNOWN) {
        MovementCapabilityType.entries.forEach { viewModel.updateMovementCapability(it, level) }
    }

    @Test
    fun draft_dropsStarterLowImpact() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository(starter))
        val state = viewModel.uiState.value
        assertThat(state.isLoaded).isTrue()
        assertThat(state.constraints).containsExactly(TrainingConstraint.KNEE_SENSITIVE)
        assertThat(state.experience).isEqualTo(ExperienceLevel.BEGINNER)
        assertThat(state.currentStep).isEqualTo(PersonalizeStep.EQUIPMENT)
    }

    @Test
    fun draft_keepsLowImpactForAnAlreadyPersonalizedProfile() = runTest {
        val profile = starter.copy(
            personalizationCompleted = true,
            movementCapabilities = MovementCapabilities.from(
                MovementCapabilityType.entries.associateWith { CapabilityLevel.LIMITED }
            )
        )
        val state = loaded(RecordingUserProfileRepository(profile)).uiState.value
        assertThat(state.constraints).containsExactly(TrainingConstraint.LOW_IMPACT_ONLY, TrainingConstraint.KNEE_SENSITIVE)
        assertThat(state.capabilityAnswers)
            .containsExactlyEntriesIn(MovementCapabilityType.entries.associateWith { CapabilityLevel.LIMITED })
    }

    @Test
    fun finish_savesAnswersAndMarksPersonalized() = runTest {
        val repository = RecordingUserProfileRepository(starter)
        val viewModel = loaded(repository)
        viewModel.toggleEquipment(StandardEquipment.DUMBBELL)
        viewModel.updateExperience(ExperienceLevel.ADVANCED)
        viewModel.updateReturningAfterBreakWeeks(6)
        answerAll(viewModel, CapabilityLevel.COMFORTABLE)

        viewModel.finish()
        advanceUntilIdle()

        assertThat(repository.saved).hasSize(1)
        val saved = repository.saved.single()
        assertThat(saved.personalizationCompleted).isTrue()
        assertThat(saved.availableEquipment).containsExactly(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL)
        assertThat(saved.trainingConstraints).containsExactly(TrainingConstraint.KNEE_SENSITIVE)
        assertThat(saved.experienceLevel).isEqualTo(ExperienceLevel.ADVANCED)
        assertThat(saved.returningAfterBreakWeeks).isEqualTo(6)
        MovementCapabilityType.entries.forEach {
            assertThat(saved.movementCapabilities[it]).isEqualTo(CapabilityLevel.COMFORTABLE)
        }
        assertThat(viewModel.uiState.value.isComplete).isTrue()
    }

    @Test
    fun name_isOptional_blankKeepsTheCurrentNameAndTypedOneIsTrimmed() = runTest {
        val blank = RecordingUserProfileRepository(starter)
        loaded(blank).apply { updateName("   "); answerAll(this); finish() }
        advanceUntilIdle()
        assertThat(blank.saved.single().name).isEqualTo(UserProfile().name)

        val typed = RecordingUserProfileRepository(starter)
        loaded(typed).apply { updateName("  Ana  "); answerAll(this); finish() }
        advanceUntilIdle()
        assertThat(typed.saved.single().name).isEqualTo("Ana")
    }

    @Test
    fun name_placeholderIsNotPrefilledButARealNameIsAndItSurvivesRecreation() = runTest {
        assertThat(loaded(RecordingUserProfileRepository(starter)).uiState.value.name).isEmpty()
        assertThat(loaded(RecordingUserProfileRepository(starter.copy(name = "Sam"))).uiState.value.name)
            .isEqualTo("Sam")

        val handle = SavedStateHandle()
        loaded(RecordingUserProfileRepository(starter), handle).updateName("Lu\nis")
        val restored = loaded(RecordingUserProfileRepository(starter), handle).uiState.value.name
        assertThat(restored).isEqualTo("Lu is")
    }

    @Test
    fun name_isCappedAtMaxLength() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository(starter))
        viewModel.updateName("x".repeat(100))
        assertThat(viewModel.uiState.value.name).hasLength(UserProfile.MAX_NAME_LENGTH)
    }

    @Test
    fun finish_keepsLowImpactWhenUserSelectsIt() = runTest {
        val repository = RecordingUserProfileRepository(starter)
        val viewModel = loaded(repository)
        viewModel.toggleConstraint(TrainingConstraint.LOW_IMPACT_ONLY)
        answerAll(viewModel)
        viewModel.finish()
        advanceUntilIdle()
        assertThat(repository.saved.single().trainingConstraints)
            .containsExactly(TrainingConstraint.LOW_IMPACT_ONLY, TrainingConstraint.KNEE_SENSITIVE)
    }

    @Test
    fun previousStep_onFirstStep_returnsFalseAndSavesNothing() = runTest {
        val repository = RecordingUserProfileRepository(starter)
        val viewModel = loaded(repository)
        viewModel.toggleEquipment(StandardEquipment.DUMBBELL)
        assertThat(viewModel.previousStep()).isFalse()
        advanceUntilIdle()
        assertThat(repository.saved).isEmpty()
    }

    @Test
    fun previousStep_onConditions_goesToMovement() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository(starter))
        viewModel.nextStep()
        viewModel.nextStep()
        answerAll(viewModel)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(PersonalizeStep.CONDITIONS)
        assertThat(viewModel.previousStep()).isTrue()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(PersonalizeStep.MOVEMENT)
    }

    @Test
    fun draft_survivesRecreationFromSavedState() = runTest {
        val repository = RecordingUserProfileRepository(starter)
        val handle = SavedStateHandle()
        val original = loaded(repository, handle)
        original.toggleEquipment(StandardEquipment.BARBELL)
        original.toggleConstraint(TrainingConstraint.KNEE_SENSITIVE)
        original.nextStep()

        val recreated = loaded(repository, handle)
        val state = recreated.uiState.value
        assertThat(state.currentStep).isEqualTo(PersonalizeStep.EXPERIENCE)
        assertThat(state.equipment).containsExactly(StandardEquipment.BODYWEIGHT, StandardEquipment.BARBELL)
        assertThat(state.constraints).isEmpty()
        assertThat(repository.saved).isEmpty()
    }

    @Test
    fun nextStep_onEmptyEquipment_staysWithRequiredFieldError() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository(starter))
        viewModel.toggleEquipment(StandardEquipment.BODYWEIGHT)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(PersonalizeStep.EQUIPMENT)
        assertThat(viewModel.uiState.value.error).isEqualTo(OnboardingError.REQUIRED_FIELD)
    }

    // Equipment and constraint helpers, moved from OnboardingViewModelTest.

    @Test
    fun toggleEquipment_addsThenRemovesEquipment() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository())
        viewModel.toggleEquipment(StandardEquipment.DUMBBELL)
        assertThat(viewModel.uiState.value.equipment)
            .containsExactly(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL)
        viewModel.toggleEquipment(StandardEquipment.DUMBBELL)
        assertThat(viewModel.uiState.value.equipment).containsExactly(StandardEquipment.BODYWEIGHT)
    }

    @Test
    fun toggleConstraint_addsThenRemovesConstraint() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository())
        viewModel.toggleConstraint(TrainingConstraint.KNEE_SENSITIVE)
        assertThat(viewModel.uiState.value.constraints).containsExactly(TrainingConstraint.KNEE_SENSITIVE)
        viewModel.toggleConstraint(TrainingConstraint.KNEE_SENSITIVE)
        assertThat(viewModel.uiState.value.constraints).isEmpty()
    }

    @Test
    fun equipmentAndConstraintHelpers_updateStateCorrectly() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository())
        viewModel.selectFullGymEquipment()
        assertThat(viewModel.uiState.value.equipment).containsExactlyElementsIn(StandardEquipment.FULL_GYM)
        viewModel.resetEquipmentToBodyweight()
        assertThat(viewModel.uiState.value.equipment).containsExactly(StandardEquipment.BODYWEIGHT)
        viewModel.toggleConstraint(TrainingConstraint.LOWER_BACK_SENSITIVE)
        viewModel.toggleConstraint(TrainingConstraint.WRIST_SENSITIVE)
        assertThat(viewModel.uiState.value.constraints).hasSize(2)
        viewModel.clearConstraints()
        assertThat(viewModel.uiState.value.constraints).isEmpty()
    }

    @Test
    fun fullGym_doesNotConfirmAnyBandSetup() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository())
        viewModel.selectFullGymEquipment()
        assertThat(viewModel.uiState.value.equipment).containsExactlyElementsIn(StandardEquipment.FULL_GYM)
        assertThat(viewModel.uiState.value.equipment.intersect(StandardEquipment.BAND_SETUPS.toSet())).isEmpty()
    }

    @Test
    fun fullGymPreservesConfirmedSupportsButDoesNotGrantThem() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository())
        val supports = listOf("Padded Plinth", "Calf Support", "Pillow", "Upper-Arm Support Roll")
        viewModel.selectFullGymEquipment()
        assertThat(viewModel.uiState.value.equipment).containsNoneIn(supports)
        supports.forEach(viewModel::toggleEquipment)
        viewModel.selectFullGymEquipment()
        assertThat(viewModel.uiState.value.equipment).containsExactlyElementsIn(StandardEquipment.FULL_GYM + supports)
    }

    @Test
    fun fullGym_preservesOnlyExplicitBandConfirmationsAcrossSavedState() = runTest {
        StandardEquipment.BAND_SETUPS.forEach { confirmed ->
            val handle = SavedStateHandle()
            val repository = RecordingUserProfileRepository()
            val original = loaded(repository, handle)
            original.toggleEquipment(confirmed)
            original.selectFullGymEquipment()

            val recreated = loaded(repository, handle)
            assertThat(recreated.uiState.value.equipment).containsExactlyElementsIn(StandardEquipment.FULL_GYM + confirmed)
            recreated.toggleEquipment(confirmed)
            assertThat(loaded(repository, handle).uiState.value.equipment)
                .containsExactlyElementsIn(StandardEquipment.FULL_GYM)
        }
    }

    @Test
    fun oldSavedEquipmentDraft_doesNotExpandWhenRestoredOrFinished() = runTest {
        val repository = RecordingUserProfileRepository()
        val handle = SavedStateHandle(
            mapOf(
                "personalize.currentStep" to PersonalizeStep.EQUIPMENT.name,
                "personalize.equipment" to ArrayList(StandardEquipment.FULL_GYM)
            )
        )
        val viewModel = loaded(repository, handle)
        assertThat(viewModel.uiState.value.equipment).containsExactlyElementsIn(StandardEquipment.FULL_GYM)
        answerAll(viewModel)
        viewModel.finish()
        advanceUntilIdle()
        assertThat(repository.saved.single().availableEquipment).containsExactlyElementsIn(StandardEquipment.FULL_GYM)
    }

    @Test
    fun explicitBandConfirmations_persistOnFinishAndBodyweightResetClearsThem() = runTest {
        val repository = RecordingUserProfileRepository(starter)
        val handle = SavedStateHandle()
        val viewModel = loaded(repository, handle)
        StandardEquipment.BAND_SETUPS.forEach(viewModel::toggleEquipment)
        answerAll(viewModel)
        viewModel.finish()
        advanceUntilIdle()
        assertThat(repository.saved.single().availableEquipment)
            .containsExactlyElementsIn(listOf(StandardEquipment.BODYWEIGHT) + StandardEquipment.BAND_SETUPS)
        viewModel.resetEquipmentToBodyweight()
        assertThat(loaded(repository, handle).uiState.value.equipment).containsExactly(StandardEquipment.BODYWEIGHT)
    }

    @Test
    fun updateReturningAfterBreakWeeks_supportsMultiYearBreakValues() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository())
        viewModel.updateReturningAfterBreakWeeks(104)
        assertThat(viewModel.uiState.value.returningAfterBreakWeeks).isEqualTo(104)
    }

    // Movement validation, moved from OnboardingViewModelTest.

    @Test
    fun experienceStep_neverBlocksAndLeadsToTheMovementQuestions() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository(starter))
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(PersonalizeStep.EXPERIENCE)
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isTrue()
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(PersonalizeStep.MOVEMENT)
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun movementStep_requiresAnExplicitAnswerForEveryCapability() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository(starter))
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.nextStep()
        val state = viewModel.uiState.value
        assertThat(state.currentStep).isEqualTo(PersonalizeStep.MOVEMENT)
        assertThat(state.unansweredCapability).isEqualTo(MovementCapabilityType.IMPACT)
        assertThat(state.error).isEqualTo(OnboardingError.MOVEMENT_REQUIRED)
        assertThat(state.canProceedCurrentStep).isFalse()
    }

    @Test
    fun explicitNotSure_isAnsweredAndPersistsAsUnknown() = runTest {
        val repository = RecordingUserProfileRepository(starter)
        val viewModel = loaded(repository)
        answerAll(viewModel, CapabilityLevel.UNKNOWN)
        viewModel.finish()
        advanceUntilIdle()
        assertThat(repository.saved).hasSize(1)
        MovementCapabilityType.entries.forEach {
            assertThat(repository.saved.single().movementCapabilities[it]).isEqualTo(CapabilityLevel.UNKNOWN)
        }
    }

    @Test
    fun incompleteCapabilityDraft_cannotFinish() = runTest {
        val repository = RecordingUserProfileRepository(starter)
        val viewModel = loaded(repository)
        viewModel.nextStep()
        viewModel.nextStep()
        viewModel.updateMovementCapability(MovementCapabilityType.IMPACT, CapabilityLevel.COMFORTABLE)
        viewModel.finish()
        advanceUntilIdle()
        assertThat(repository.saved).isEmpty()
        val state = viewModel.uiState.value
        assertThat(state.isComplete).isFalse()
        assertThat(state.currentStep).isEqualTo(PersonalizeStep.MOVEMENT)
        assertThat(state.error).isEqualTo(OnboardingError.MOVEMENT_REQUIRED)
        assertThat(state.unansweredCapability).isEqualTo(MovementCapabilityType.FLOOR_TRANSITION)
    }

    @Test
    fun backAndForwardNavigation_retainsExplicitCapabilityAnswers() = runTest {
        val viewModel = loaded(RecordingUserProfileRepository(starter))
        viewModel.nextStep()
        viewModel.updateMovementCapability(MovementCapabilityType.IMPACT, CapabilityLevel.LIMITED)
        viewModel.previousStep()
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.capabilityAnswers)
            .containsExactly(MovementCapabilityType.IMPACT, CapabilityLevel.LIMITED)
    }

    @Test
    fun savedStateRecreation_preservesAnsweredKeysWithoutAnsweringMissingKeys() = runTest {
        val repository = RecordingUserProfileRepository(starter)
        val handle = SavedStateHandle()
        val original = loaded(repository, handle)
        original.nextStep()
        original.updateMovementCapability(MovementCapabilityType.IMPACT, CapabilityLevel.UNKNOWN)
        original.updateMovementCapability(MovementCapabilityType.FLOOR_TRANSITION, CapabilityLevel.LIMITED)

        val state = loaded(repository, handle).uiState.value
        assertThat(state.currentStep).isEqualTo(PersonalizeStep.EXPERIENCE)
        assertThat(state.capabilityAnswers).containsExactly(
            MovementCapabilityType.IMPACT, CapabilityLevel.UNKNOWN,
            MovementCapabilityType.FLOOR_TRANSITION, CapabilityLevel.LIMITED
        )
        assertThat(state.unansweredCapability).isEqualTo(MovementCapabilityType.UNSUPPORTED_SQUAT)
    }

    @Test
    fun finish_surfacesRepositoryValidationErrorsWithoutCompleting() = runTest {
        val repository = RecordingUserProfileRepository(starter)
        val viewModel = loaded(repository)
        viewModel.toggleEquipment(StandardEquipment.BODYWEIGHT)
        answerAll(viewModel)
        viewModel.finish()
        advanceUntilIdle()
        assertThat(repository.saved).isEmpty()
        assertThat(viewModel.uiState.value.isComplete).isFalse()
        assertThat(viewModel.uiState.value.error).isEqualTo(OnboardingError.INVALID_FIELD)
    }

    // Condition drafts, moved from OnboardingViewModelTest.

    @Test
    fun untouchedRetainedConditionsAndExplicitEmptyUseOneAtomicSave() = runTest {
        val unknown = SelectedCondition("unknown", 7, (1..32).associate { "future-$it" to if (it % 2 == 0) null else "value" })
        val repository = RecordingUserProfileRepository(starter.copy(activeConditions = listOf(unknown)))
        val untouched = loaded(repository)
        answerAll(untouched)
        untouched.finish()
        advanceUntilIdle()
        assertThat(repository.saved).hasSize(1)
        assertThat(repository.saved.single().activeConditions).containsExactly(unknown)

        val handle = SavedStateHandle()
        val edited = loaded(repository, handle)
        answerAll(edited)
        edited.updateConditions(listOf(unknown))
        assertThat(loaded(repository, handle).uiState.value.conditionDraft).containsExactly(unknown)
        edited.updateConditions(emptyList())
        val restored = loaded(repository, handle)
        restored.finish()
        advanceUntilIdle()
        assertThat(repository.saved).hasSize(2)
        assertThat(repository.saved.last().activeConditions).isEmpty()
    }

    @Test
    fun reportedConditionsSurviveDraftRecreationAndMergeOnlyExplicitEdits() = runTest {
        val opaque = " future / id "
        val repository = RecordingUserProfileRepository(starter.copy(reportedConditions = setOf(opaque, "gluteal_tendinopathy")))
        val handle = SavedStateHandle()
        val original = loaded(repository, handle)
        original.setReportedCondition("piriformis_syndrome", true)
        original.setReportedCondition("gluteal_tendinopathy", false)
        original.setReportedCondition("reference-only", true)
        answerAll(original)

        val restored = loaded(repository, handle)
        assertThat(restored.uiState.value.reportedConditions).containsExactly(opaque, "piriformis_syndrome")
        repository.saveUserProfile(repository.getProfileOnce().copy(reportedConditions = setOf(opaque, "new report", "gluteal_tendinopathy")))
        restored.finish()
        advanceUntilIdle()
        assertThat(repository.saved.single().reportedConditions).containsExactly(opaque, "new report", "piriformis_syndrome")
        assertThat(repository.saved.single().activeConditions).isEmpty()
    }
}

private class RecordingUserProfileRepository(initial: UserProfile = UserProfile()) : UserProfileRepository {
    private val profile = MutableStateFlow(initial)
    val saved = mutableListOf<UserProfile>()

    override fun getUserProfile(): Flow<UserProfile> = profile
    override suspend fun getProfileOnce(): UserProfile = profile.value

    override suspend fun saveUserProfile(profile: UserProfile) {
        this.profile.value = profile
    }

    override suspend fun saveProfile(profile: UserProfile) {
        require(profile.availableEquipment.isNotEmpty()) { "availableEquipment must not be empty." }
        saved += profile
        this.profile.value = profile
    }

    override suspend fun updateGoals(goals: Set<FitnessGoal>) = error("Not used")
    override suspend fun updatePrimaryGoal(goal: FitnessGoal) = error("Not used")
    override suspend fun updateExperienceLevel(level: ExperienceLevel) = error("Not used")
    override suspend fun updatePreferredDuration(minutes: Int) = error("Not used")
    override suspend fun updateDaysPerWeek(days: Int) = error("Not used")
    override suspend fun updateEquipment(equipment: List<String>) = error("Not used")
    override suspend fun updateUnit(unit: WeightUnit) = error("Not used")
    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) = error("Not used")
    override suspend fun updateExcludedExercises(excludedIds: List<String>) = error("Not used")
    override suspend fun updateTrainingConstraints(constraints: Set<TrainingConstraint>) = error("Not used")
    override suspend fun updateReturningAfterBreakWeeks(weeks: Int) = error("Not used")
    override suspend fun updateGender(gender: wallcrawl.elopenmike.com.core.model.ProfileGender) = error("Not used")
    override suspend fun updateThemePreference(themePreference: wallcrawl.elopenmike.com.core.model.ThemePreference) = error("Not used")
}
