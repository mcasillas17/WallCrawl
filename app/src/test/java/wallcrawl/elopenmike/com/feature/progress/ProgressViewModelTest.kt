package wallcrawl.elopenmike.com.feature.progress

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun catalogFailure_becomesVisibleError() = runTest {
        val viewModel = ProgressViewModel(
            workoutRepository = EmptyWorkoutRepository(),
            userProfileRepository = FixedUserProfileRepository(),
            exerciseCatalog = FailingExerciseCatalog(),
            progressCalculator = ProgressCalculator()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value as ProgressUiState.Error
        assertThat(state.message).contains("offline exercise catalog")
    }
}

private class FailingExerciseCatalog : ExerciseCatalog {
    override fun getAllExercises(): Flow<List<Exercise>> = flow {
        throw IllegalStateException("asset parse failed")
    }

    override suspend fun getExerciseById(id: String): Exercise? = error("Not used")
    override fun searchExercises(
        query: String,
        muscle: String?,
        equipment: String?
    ): Flow<List<Exercise>> = error("Not used")

    override suspend fun getMuscleGroups(): List<String> = error("Not used")
    override suspend fun getEquipmentTypes(): List<String> = error("Not used")
}

private class FixedUserProfileRepository : UserProfileRepository {
    private val profile = UserProfile()

    override fun getUserProfile(): Flow<UserProfile> = flowOf(profile)
    override suspend fun getProfileOnce(): UserProfile = profile
    override suspend fun saveUserProfile(profile: UserProfile) = error("Not used")
    override suspend fun updatePrimaryGoal(goal: FitnessGoal) = error("Not used")
    override suspend fun updateExperienceLevel(level: ExperienceLevel) = error("Not used")
    override suspend fun updatePreferredDuration(minutes: Int) = error("Not used")
    override suspend fun updateDaysPerWeek(days: Int) = error("Not used")
    override suspend fun updateEquipment(equipment: List<String>) = error("Not used")
    override suspend fun updateUnit(unit: WeightUnit) = error("Not used")
    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) = error("Not used")
    override suspend fun updateExcludedExercises(excludedIds: List<String>) = error("Not used")
}

private class EmptyWorkoutRepository : WorkoutRepository {
    override fun observeActiveSession(): Flow<WorkoutSession?> = flowOf(null)
    override suspend fun getActiveSessionOnce(): WorkoutSession? = null
    override suspend fun getSessionById(sessionId: String): WorkoutSession? = null
    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(null)
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> = flowOf(emptyList())
    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(0)
    override fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int> = flowOf(0)
    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> = emptyList()
    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun startWorkoutFromTemplate(
        template: wallcrawl.elopenmike.com.core.model.WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun logSetCompletion(
        setId: String,
        reps: Int?,
        weight: Double?,
        isCompleted: Boolean
    ) = error("Not used")

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): WorkoutSummary = error("Not used")

    override suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary? = error("Not used")

    override suspend fun cancelWorkout(sessionId: String) = error("Not used")
}
