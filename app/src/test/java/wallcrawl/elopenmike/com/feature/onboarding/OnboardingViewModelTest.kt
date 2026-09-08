package wallcrawl.elopenmike.com.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_startsConservativeAndIncomplete() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.equipment).containsExactly(StandardEquipment.BODYWEIGHT)
        assertThat(state.constraints).isEmpty()
        assertThat(state.returningAfterBreakWeeks).isEqualTo(0)
        assertThat(state.isComplete).isFalse()
        assertThat(state.equipmentOptions).containsAtLeastElementsIn(StandardEquipment.ALL)
        assertThat(state.constraintOptions).containsExactlyElementsIn(TrainingConstraint.entries)
    }

    @Test
    fun toggleEquipment_addsThenRemovesEquipment() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.toggleEquipment(StandardEquipment.DUMBBELL)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.equipment)
            .containsExactly(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL)

        viewModel.toggleEquipment(StandardEquipment.DUMBBELL)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.equipment).containsExactly(StandardEquipment.BODYWEIGHT)
    }

    @Test
    fun toggleConstraint_addsThenRemovesConstraint() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.toggleConstraint(TrainingConstraint.KNEE_SENSITIVE)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.constraints).containsExactly(TrainingConstraint.KNEE_SENSITIVE)

        viewModel.toggleConstraint(TrainingConstraint.KNEE_SENSITIVE)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.constraints).isEmpty()
    }

    @Test
    fun complete_persistsAllRequiredPlanningInputsInOneSave() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        answerAllCapabilities(viewModel, CapabilityLevel.UNKNOWN)

        viewModel.complete(
            name = "Alex",
            goals = setOf(FitnessGoal.STRENGTH),
            experience = ExperienceLevel.BEGINNER,
            daysPerWeek = 3,
            durationMinutes = 45,
            unit = WeightUnit.KG,
            equipment = setOf(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL),
            constraints = setOf(TrainingConstraint.SHOULDER_SENSITIVE),
            returningAfterBreakWeeks = 6
        )
        advanceUntilIdle()

        // Onboarding must produce exactly one revision update, not one write per field.
        assertThat(repository.saved).hasSize(1)
        val saved = repository.saved.single()
        assertThat(saved.onboardingCompleted).isTrue()
        assertThat(saved.name).isEqualTo("Alex")
        assertThat(saved.primaryGoal).isEqualTo(FitnessGoal.STRENGTH)
        assertThat(saved.goals).containsExactly(FitnessGoal.STRENGTH)
        assertThat(saved.experienceLevel).isEqualTo(ExperienceLevel.BEGINNER)
        assertThat(saved.daysPerWeek).isEqualTo(3)
        assertThat(saved.preferredDurationMinutes).isEqualTo(45)
        assertThat(saved.preferredUnit).isEqualTo(WeightUnit.KG)
        assertThat(saved.availableEquipment)
            .containsExactly(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL)
        assertThat(saved.trainingConstraints).containsExactly(TrainingConstraint.SHOULDER_SENSITIVE)
        assertThat(saved.returningAfterBreakWeeks).isEqualTo(6)
        MovementCapabilityType.entries.forEach { type ->
            assertThat(saved.movementCapabilities[type]).isEqualTo(CapabilityLevel.UNKNOWN)
        }
        assertThat(saved.confirmedStartingLoads).isEmpty()
        assertThat(viewModel.uiState.value.isComplete).isTrue()
    }

    @Test
    fun complete_surfacesRepositoryValidationErrorsWithoutMarkingOnboardingComplete() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        answerAllCapabilities(viewModel, CapabilityLevel.UNKNOWN)

        viewModel.complete(
            name = "Alex",
            goals = setOf(FitnessGoal.STRENGTH),
            experience = ExperienceLevel.BEGINNER,
            daysPerWeek = 3,
            durationMinutes = 45,
            unit = WeightUnit.KG,
            equipment = emptySet(),
            constraints = emptySet(),
            returningAfterBreakWeeks = 0
        )
        advanceUntilIdle()

        assertThat(repository.saved).isEmpty()
        assertThat(viewModel.uiState.value.isComplete).isFalse()
        assertThat(viewModel.uiState.value.error).isNotNull()
    }

    @Test
    fun wizardStepNavigation_progressesThroughAllStepsAndCompletes() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.WELCOME)
        assertThat(viewModel.uiState.value.isFirstStep).isTrue()
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isFalse()

        // Provide name to proceed from welcome
        viewModel.updateName("Spider-Crawler")
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isTrue()

        // Step 1 -> Step 2 (Goals)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.GOALS)
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isTrue()

        // Step 2 -> Step 3 (Experience & Unit)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.EXPERIENCE_UNIT)
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isTrue()

        // Step 3 -> Step 4 (Movement preferences)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep)
            .isEqualTo(OnboardingStep.MOVEMENT_CAPABILITY)
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isFalse()
        answerAllCapabilities(viewModel, CapabilityLevel.UNKNOWN)
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isTrue()

        // Step 4 -> Step 5 (Schedule)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SCHEDULE)

        // Step 5 -> Step 6 (Equipment)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.EQUIPMENT)
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isTrue()

        // Step 6 -> Step 7 (Safety)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SAFETY)

        // Step 7 -> Step 8 (Summary)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SUMMARY)
        assertThat(viewModel.uiState.value.isLastStep).isTrue()
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isTrue()

        // Can step backward
        viewModel.previousStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SAFETY)

        // Jump directly to summary and complete
        viewModel.goToStep(OnboardingStep.SUMMARY)
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SUMMARY)

        // Calling nextStep on last step invokes complete()
        viewModel.nextStep()
        advanceUntilIdle()

        assertThat(repository.saved).hasSize(1)
        assertThat(repository.saved.single().onboardingCompleted).isTrue()
        assertThat(repository.saved.single().name).isEqualTo("Spider-Crawler")
        assertThat(viewModel.uiState.value.isComplete).isTrue()
    }

    @Test
    fun equipmentAndConstraintHelpers_updateStateCorrectly() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.selectFullGymEquipment()
        assertThat(viewModel.uiState.value.equipment)
            .containsExactlyElementsIn(StandardEquipment.FULL_GYM)

        // Test resetEquipmentToBodyweight
        viewModel.resetEquipmentToBodyweight()
        assertThat(viewModel.uiState.value.equipment)
            .containsExactly(StandardEquipment.BODYWEIGHT)

        // Test clearConstraints
        viewModel.toggleConstraint(TrainingConstraint.LOWER_BACK_SENSITIVE)
        viewModel.toggleConstraint(TrainingConstraint.WRIST_SENSITIVE)
        assertThat(viewModel.uiState.value.constraints).hasSize(2)

        viewModel.clearConstraints()
        assertThat(viewModel.uiState.value.constraints).isEmpty()
    }

    @Test
    fun fullGym_doesNotConfirmAnyBandSetup() {
        val viewModel = OnboardingViewModel(RecordingUserProfileRepository())

        viewModel.selectFullGymEquipment()

        assertThat(viewModel.uiState.value.equipment)
            .containsExactlyElementsIn(StandardEquipment.FULL_GYM)
        assertThat(viewModel.uiState.value.equipment.intersect(StandardEquipment.BAND_SETUPS.toSet()))
            .isEmpty()
    }

    @Test
    fun fullGym_preservesOnlyExplicitBandConfirmationsAcrossSavedState() {
        StandardEquipment.BAND_SETUPS.forEach { confirmed ->
            val handle = SavedStateHandle()
            val repository = RecordingUserProfileRepository()
            val original = OnboardingViewModel(repository, handle)
            original.toggleEquipment(confirmed)
            original.selectFullGymEquipment()

            val recreated = OnboardingViewModel(repository, handle)
            assertThat(recreated.uiState.value.equipment)
                .containsExactlyElementsIn(StandardEquipment.FULL_GYM + confirmed)
            recreated.toggleEquipment(confirmed)
            assertThat(OnboardingViewModel(repository, handle).uiState.value.equipment)
                .containsExactlyElementsIn(StandardEquipment.FULL_GYM)
        }
    }

    @Test
    fun oldSavedEquipmentDraft_doesNotExpandWhenRestoredOrCompleted() = runTest {
        val repository = RecordingUserProfileRepository()
        val handle = SavedStateHandle(
            mapOf("onboarding.equipment" to ArrayList(StandardEquipment.FULL_GYM))
        )
        val viewModel = OnboardingViewModel(repository, handle)
        assertThat(viewModel.uiState.value.equipment)
            .containsExactlyElementsIn(StandardEquipment.FULL_GYM)
        answerAllCapabilities(viewModel, CapabilityLevel.UNKNOWN)

        viewModel.complete()
        advanceUntilIdle()

        assertThat(repository.saved.single().availableEquipment)
            .containsExactlyElementsIn(StandardEquipment.FULL_GYM)
    }

    @Test
    fun explicitBandConfirmations_persistOnCompletionAndBodyweightResetClearsThem() = runTest {
        val repository = RecordingUserProfileRepository()
        val handle = SavedStateHandle()
        val viewModel = OnboardingViewModel(repository, handle)
        StandardEquipment.BAND_SETUPS.forEach(viewModel::toggleEquipment)
        answerAllCapabilities(viewModel, CapabilityLevel.UNKNOWN)
        viewModel.complete()
        advanceUntilIdle()

        assertThat(repository.saved.single().availableEquipment)
            .containsExactlyElementsIn(listOf(StandardEquipment.BODYWEIGHT) + StandardEquipment.BAND_SETUPS)
        viewModel.resetEquipmentToBodyweight()
        assertThat(OnboardingViewModel(repository, handle).uiState.value.equipment)
            .containsExactly(StandardEquipment.BODYWEIGHT)
    }

    @Test
    fun toggleGoal_addsAndRemovesGoalsWithoutDroppingLastGoal() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // Starts with BUILD_MUSCLE
        assertThat(viewModel.uiState.value.goals).containsExactly(FitnessGoal.BUILD_MUSCLE)

        // Toggle STRENGTH on
        viewModel.toggleGoal(FitnessGoal.STRENGTH)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.goals)
            .containsExactly(FitnessGoal.BUILD_MUSCLE, FitnessGoal.STRENGTH)

        // Toggle BUILD_MUSCLE off
        viewModel.toggleGoal(FitnessGoal.BUILD_MUSCLE)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.goals).containsExactly(FitnessGoal.STRENGTH)

        // Attempting to toggle STRENGTH off (last remaining) keeps it selected
        viewModel.toggleGoal(FitnessGoal.STRENGTH)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.goals).containsExactly(FitnessGoal.STRENGTH)
    }

    @Test
    fun updateReturningAfterBreakWeeks_supportsMultiYearBreakValues() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.updateReturningAfterBreakWeeks(104) // 2 years
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.returningAfterBreakWeeks).isEqualTo(104)
    }

    @Test
    fun movementCapabilityStep_requiresAnExplicitAnswerForEveryCapability() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)

        viewModel.goToStep(OnboardingStep.MOVEMENT_CAPABILITY)
        viewModel.nextStep()

        assertThat(viewModel.uiState.value.currentStep)
            .isEqualTo(OnboardingStep.MOVEMENT_CAPABILITY)
        assertThat(viewModel.uiState.value.unansweredCapability)
            .isEqualTo(MovementCapabilityType.IMPACT)
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isFalse()
    }

    @Test
    fun explicitNotSure_isAnsweredAndPersistsAsUnknown() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.updateName("Alex")
        MovementCapabilityType.entries.forEach { type ->
            viewModel.updateMovementCapability(type, CapabilityLevel.UNKNOWN)
        }

        viewModel.complete()
        advanceUntilIdle()

        assertThat(repository.saved).hasSize(1)
        MovementCapabilityType.entries.forEach { type ->
            assertThat(repository.saved.single().movementCapabilities[type])
                .isEqualTo(CapabilityLevel.UNKNOWN)
        }
    }

    @Test
    fun incompleteCapabilityDraft_cannotCompleteOnboarding() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        viewModel.updateName("Alex")
        viewModel.updateMovementCapability(
            MovementCapabilityType.IMPACT,
            CapabilityLevel.COMFORTABLE
        )

        viewModel.complete()
        advanceUntilIdle()

        assertThat(repository.saved).isEmpty()
        assertThat(viewModel.uiState.value.isComplete).isFalse()
        assertThat(viewModel.uiState.value.unansweredCapability)
            .isEqualTo(MovementCapabilityType.FLOOR_TRANSITION)
    }

    @Test
    fun backAndForwardNavigation_retainsExplicitCapabilityAnswers() {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        viewModel.goToStep(OnboardingStep.MOVEMENT_CAPABILITY)
        viewModel.updateMovementCapability(
            MovementCapabilityType.IMPACT,
            CapabilityLevel.LIMITED
        )

        viewModel.previousStep()
        viewModel.goToStep(OnboardingStep.MOVEMENT_CAPABILITY)

        assertThat(viewModel.uiState.value.capabilityAnswers)
            .containsExactly(MovementCapabilityType.IMPACT, CapabilityLevel.LIMITED)
    }

    @Test
    fun savedStateRecreation_preservesAnsweredKeysWithoutAnsweringMissingKeys() {
        val repository = RecordingUserProfileRepository()
        val savedStateHandle = SavedStateHandle()
        val original = OnboardingViewModel(repository, savedStateHandle)
        original.goToStep(OnboardingStep.MOVEMENT_CAPABILITY)
        original.updateMovementCapability(
            MovementCapabilityType.IMPACT,
            CapabilityLevel.UNKNOWN
        )
        original.updateMovementCapability(
            MovementCapabilityType.FLOOR_TRANSITION,
            CapabilityLevel.LIMITED
        )

        val recreated = OnboardingViewModel(repository, savedStateHandle)

        assertThat(recreated.uiState.value.currentStep)
            .isEqualTo(OnboardingStep.MOVEMENT_CAPABILITY)
        assertThat(recreated.uiState.value.capabilityAnswers)
            .containsExactly(
                MovementCapabilityType.IMPACT,
                CapabilityLevel.UNKNOWN,
                MovementCapabilityType.FLOOR_TRANSITION,
                CapabilityLevel.LIMITED
            )
        assertThat(recreated.uiState.value.capabilityAnswers)
            .doesNotContainKey(MovementCapabilityType.UNSUPPORTED_SQUAT)
        assertThat(recreated.uiState.value.unansweredCapability)
            .isEqualTo(MovementCapabilityType.UNSUPPORTED_SQUAT)
    }

    @Test
    fun changingLanguageMidWizardKeepsEveryAnswerAndTheCurrentStep() {
        // Applying an app language recreates the activity. The ViewModel is retained across
        // that, and every answer is written to the SavedStateHandle as it is made, so this
        // is the same recreation the language switch causes — no answer is lost and the
        // wizard does not jump back to step one.
        val repository = RecordingUserProfileRepository()
        val savedStateHandle = SavedStateHandle()
        val original = OnboardingViewModel(repository, savedStateHandle)
        original.updateName("Gabriela")
        original.updateGoals(setOf(FitnessGoal.STRENGTH))
        original.updateExperience(ExperienceLevel.ADVANCED)
        original.updateUnit(WeightUnit.KG)
        original.updateDaysPerWeek(5)
        original.updateDurationMinutes(75)
        original.toggleEquipment(StandardEquipment.BARBELL)
        original.toggleConstraint(TrainingConstraint.KNEE_SENSITIVE)
        original.updateReturningAfterBreakWeeks(12)
        answerAllCapabilities(original, CapabilityLevel.COMFORTABLE)
        original.goToStep(OnboardingStep.EQUIPMENT)

        val afterLanguageChange = OnboardingViewModel(repository, savedStateHandle)
        val state = afterLanguageChange.uiState.value

        assertThat(state.currentStep).isEqualTo(OnboardingStep.EQUIPMENT)
        assertThat(state.name).isEqualTo("Gabriela")
        assertThat(state.goals).containsExactly(FitnessGoal.STRENGTH)
        assertThat(state.experience).isEqualTo(ExperienceLevel.ADVANCED)
        assertThat(state.unit).isEqualTo(WeightUnit.KG)
        assertThat(state.daysPerWeek).isEqualTo(5)
        assertThat(state.durationMinutes).isEqualTo(75)
        assertThat(state.equipment).contains(StandardEquipment.BARBELL)
        assertThat(state.constraints).containsExactly(TrainingConstraint.KNEE_SENSITIVE)
        assertThat(state.returningAfterBreakWeeks).isEqualTo(12)
        assertThat(state.unansweredCapability).isNull()
        // Nothing was saved: a language change is not a profile write.
        assertThat(repository.saved).isEmpty()
    }

    @Test
    fun languageIsNotAnOnboardingAnswerAndAddsNoStep() {
        // The selector sits on the Welcome step and is never required. The wizard keeps the
        // same eight steps it had, and no state here records a language.
        assertThat(OnboardingStep.totalSteps).isEqualTo(8)
        assertThat(OnboardingStep.entries.first()).isEqualTo(OnboardingStep.WELCOME)

        val viewModel = OnboardingViewModel(RecordingUserProfileRepository(), SavedStateHandle())
        val fieldNames = viewModel.uiState.value::class.java.declaredFields.map { it.name }
        assertThat(fieldNames.map { it.lowercase() })
            .containsNoneOf("language", "locale", "languagetag")
    }

    private fun answerAllCapabilities(
        viewModel: OnboardingViewModel,
        level: CapabilityLevel
    ) {
        MovementCapabilityType.entries.forEach { type ->
            viewModel.updateMovementCapability(type, level)
        }
    }
}

private class RecordingUserProfileRepository : UserProfileRepository {
    private val profile = MutableStateFlow(UserProfile())
    val saved = mutableListOf<UserProfile>()

    override fun getUserProfile(): Flow<UserProfile> = profile
    override suspend fun getProfileOnce(): UserProfile = profile.value

    override suspend fun saveUserProfile(profile: UserProfile) {
        this.profile.value = profile
    }

    override suspend fun saveProfile(profile: UserProfile) {
        require(profile.goals.isNotEmpty()) { "goals must not be empty." }
        require(profile.daysPerWeek in 2..6) { "daysPerWeek must be between 2 and 6." }
        require(profile.preferredDurationMinutes in 20..120) {
            "preferredDurationMinutes must be between 20 and 120."
        }
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
    override suspend fun updateThemePreference(themePreference: wallcrawl.elopenmike.com.core.model.ThemePreference) = error("Not used")
}
