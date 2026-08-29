package wallcrawl.elopenmike.com.feature.onboarding

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

        viewModel.complete(
            name = "Alex",
            goal = FitnessGoal.STRENGTH,
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
        assertThat(saved.experienceLevel).isEqualTo(ExperienceLevel.BEGINNER)
        assertThat(saved.daysPerWeek).isEqualTo(3)
        assertThat(saved.preferredDurationMinutes).isEqualTo(45)
        assertThat(saved.preferredUnit).isEqualTo(WeightUnit.KG)
        assertThat(saved.availableEquipment)
            .containsExactly(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL)
        assertThat(saved.trainingConstraints).containsExactly(TrainingConstraint.SHOULDER_SENSITIVE)
        assertThat(saved.returningAfterBreakWeeks).isEqualTo(6)
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
            goal = FitnessGoal.STRENGTH,
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
        require(profile.daysPerWeek in 2..6) { "daysPerWeek must be between 2 and 6." }
        require(profile.preferredDurationMinutes in 20..120) {
            "preferredDurationMinutes must be between 20 and 120."
        }
        require(profile.availableEquipment.isNotEmpty()) { "availableEquipment must not be empty." }
        saved += profile
        this.profile.value = profile
    }

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
}
