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
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @Test
    fun profileGenderSurvivesDraftRecreationAndCompletion() = runTest {
        val repository = RecordingUserProfileRepository()
        val savedState = SavedStateHandle()
        val first = OnboardingViewModel(repository, savedState)
        first.updateName("Illustration Crawler")
        first.updateGender(wallcrawl.elopenmike.com.core.model.ProfileGender.WOMAN)
        val restored = OnboardingViewModel(repository, savedState)
        assertThat(restored.uiState.value.gender).isEqualTo(wallcrawl.elopenmike.com.core.model.ProfileGender.WOMAN)
        restored.complete()
        advanceUntilIdle()
        val profile = repository.getProfileOnce()
        assertThat(profile.gender).isEqualTo(wallcrawl.elopenmike.com.core.model.ProfileGender.WOMAN)
        assertThat(profile.onboardingCompleted).isTrue()
    }

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
        assertThat(state.isComplete).isFalse()
    }

    @Test
    fun complete_persistsAllRequiredPlanningInputsInOneSave() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.complete(
            name = "Alex",
            goals = setOf(FitnessGoal.STRENGTH),
            experience = ExperienceLevel.BEGINNER,
            daysPerWeek = 3,
            durationMinutes = 45,
            unit = WeightUnit.KG
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
        // Equipment, movement and conditions now belong to personalization; onboarding
        // leaves the profile's existing values alone.
        assertThat(saved.availableEquipment).isEqualTo(UserProfile().availableEquipment)
        assertThat(saved.trainingConstraints).isEqualTo(UserProfile().trainingConstraints)
        assertThat(saved.movementCapabilities).isEqualTo(UserProfile().movementCapabilities)
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

        viewModel.complete(
            name = "Alex",
            goals = setOf(FitnessGoal.STRENGTH),
            experience = ExperienceLevel.BEGINNER,
            daysPerWeek = 1,
            durationMinutes = 45,
            unit = WeightUnit.KG
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

        // Step 3 -> Step 4 (Schedule)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SCHEDULE)

        // Step 4 -> Step 5 (Summary)
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SUMMARY)
        assertThat(viewModel.uiState.value.isLastStep).isTrue()
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isTrue()

        // Can step backward
        viewModel.previousStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SCHEDULE)

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
        original.goToStep(OnboardingStep.SCHEDULE)

        val afterLanguageChange = OnboardingViewModel(repository, savedStateHandle)
        val state = afterLanguageChange.uiState.value

        assertThat(state.currentStep).isEqualTo(OnboardingStep.SCHEDULE)
        assertThat(state.name).isEqualTo("Gabriela")
        assertThat(state.goals).containsExactly(FitnessGoal.STRENGTH)
        assertThat(state.experience).isEqualTo(ExperienceLevel.ADVANCED)
        assertThat(state.unit).isEqualTo(WeightUnit.KG)
        assertThat(state.daysPerWeek).isEqualTo(5)
        assertThat(state.durationMinutes).isEqualTo(75)
        // Nothing was saved: a language change is not a profile write.
        assertThat(repository.saved).isEmpty()
    }

    @Test
    fun languageIsNotAnOnboardingAnswerAndAddsNoStep() {
        // The selector sits on the Welcome step and is never required. The wizard keeps the
        // same steps it had, and no state here records a language.
        assertThat(OnboardingStep.totalSteps).isEqualTo(5)
        assertThat(OnboardingStep.entries.first()).isEqualTo(OnboardingStep.WELCOME)

        val viewModel = OnboardingViewModel(RecordingUserProfileRepository(), SavedStateHandle())
        val fieldNames = viewModel.uiState.value::class.java.declaredFields.map { it.name }
        assertThat(fieldNames.map { it.lowercase() })
            .containsNoneOf("language", "locale", "languagetag")
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
    override suspend fun updateGender(gender: wallcrawl.elopenmike.com.core.model.ProfileGender) = saveProfile(getProfileOnce().copy(gender = gender))
    override suspend fun updateThemePreference(themePreference: wallcrawl.elopenmike.com.core.model.ThemePreference) = error("Not used")
}
