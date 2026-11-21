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
    fun save_withoutGoal_doesNotSave() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)

        assertThat(viewModel.uiState.value.canSave).isFalse()
        viewModel.save("US")
        advanceUntilIdle()

        assertThat(repository.saved).isEmpty()
        assertThat(viewModel.uiState.value.isComplete).isFalse()
    }

    @Test
    fun save_persistsStarterProfile() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.updateGoal(FitnessGoal.STRENGTH)
        viewModel.updateDaysPerWeek(5)
        viewModel.updateDurationMinutes(60)
        viewModel.save("US")
        advanceUntilIdle()

        val saved = repository.saved.single()
        assertThat(saved.goals).containsExactly(FitnessGoal.STRENGTH)
        assertThat(saved.daysPerWeek).isEqualTo(5)
        assertThat(saved.preferredDurationMinutes).isEqualTo(60)
        assertThat(saved.preferredUnit).isEqualTo(WeightUnit.LBS)
        assertThat(saved.experienceLevel).isEqualTo(ExperienceLevel.BEGINNER)
        assertThat(saved.availableEquipment).containsExactly(StandardEquipment.BODYWEIGHT)
        assertThat(saved.trainingConstraints).containsExactly(TrainingConstraint.LOW_IMPACT_ONLY)
        assertThat(saved.onboardingCompleted).isTrue()
        assertThat(saved.personalizationCompleted).isFalse()
        assertThat(viewModel.uiState.value.isComplete).isTrue()
    }

    @Test
    fun defaults_areThreeDaysThirtyMinutesNoGoal() {
        val state = OnboardingViewModel(RecordingUserProfileRepository()).uiState.value

        assertThat(state.goal).isNull()
        assertThat(state.daysPerWeek).isEqualTo(3)
        assertThat(state.durationMinutes).isEqualTo(30)
    }

    @Test
    fun draftSurvivesRecreation() {
        val repository = RecordingUserProfileRepository()
        val savedState = SavedStateHandle()
        val original = OnboardingViewModel(repository, savedState)
        original.updateGoal(FitnessGoal.FAT_LOSS)
        original.updateDaysPerWeek(5)
        original.updateDurationMinutes(45)

        val state = OnboardingViewModel(repository, savedState).uiState.value

        assertThat(state.goal).isEqualTo(FitnessGoal.FAT_LOSS)
        assertThat(state.daysPerWeek).isEqualTo(5)
        assertThat(state.durationMinutes).isEqualTo(45)
        assertThat(repository.saved).isEmpty()
    }

    @Test
    fun save_surfacesRepositoryErrorsWithoutCompleting() = runTest {
        val repository = RecordingUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
        viewModel.updateGoal(FitnessGoal.STRENGTH)
        viewModel.updateDaysPerWeek(1)

        viewModel.save("US")
        advanceUntilIdle()

        assertThat(repository.saved).isEmpty()
        assertThat(viewModel.uiState.value.isComplete).isFalse()
        assertThat(viewModel.uiState.value.error).isEqualTo(OnboardingError.INVALID_FIELD)
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
