package wallcrawl.elopenmike.com.feature.today

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanner
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_generatesOnceAndCountsCompletedWorkoutsInRollingWeek() = runTest {
        val now = 20 * DAY_MILLIS
        val profileRepository = TodayUserProfileRepository(UserProfile())
        val workoutRepository = TodayWorkoutRepository(
            completedSessions = listOf(
                completedSession("recent-1", now - DAY_MILLIS),
                completedSession("recent-2", now - (6 * DAY_MILLIS)),
                completedSession("old", now - (8 * DAY_MILLIS))
            )
        )
        val catalog = InMemoryExerciseCatalog()
        val contextBuilder = WorkoutGenerationContextBuilder(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            exerciseCatalog = catalog,
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            nowTimestamp = { now }
        )
        val planner = RecordingWorkoutPlanner()
        val viewModel = TodayViewModel(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            workoutGenerationContextBuilder = contextBuilder,
            workoutPlanner = planner,
            workoutValidator = GeneratedWorkoutValidator(catalog),
            nowTimestamp = { now }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertThat(planner.generateCalls).isEqualTo(1)
        assertThat(state.completedThisWeek).isEqualTo(2)
        assertThat(state.suggestedWorkout.exercises).isNotEmpty()
    }

    @Test
    fun profileChange_regeneratesWithUpdatedContextAfterInitialGeneration() = runTest {
        val now = 20 * DAY_MILLIS
        val profileRepository = TodayUserProfileRepository(UserProfile())
        val workoutRepository = TodayWorkoutRepository(emptyList())
        val catalog = InMemoryExerciseCatalog()
        val planner = RecordingWorkoutPlanner()
        val viewModel = TodayViewModel(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                userProfileRepository = profileRepository,
                workoutRepository = workoutRepository,
                exerciseCatalog = catalog,
                exerciseFilter = ExerciseFilter(),
                historyAnalyzer = WorkoutHistoryAnalyzer(),
                nowTimestamp = { now }
            ),
            workoutPlanner = planner,
            workoutValidator = GeneratedWorkoutValidator(catalog),
            nowTimestamp = { now }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        profileRepository.updatePrimaryGoal(FitnessGoal.STRENGTH)
        advanceUntilIdle()

        assertThat(planner.generateCalls).isEqualTo(2)
        assertThat(planner.contexts.last().fitnessGoal).isEqualTo(FitnessGoal.STRENGTH)
    }

    private fun completedSession(id: String, completedAtTimestamp: Long) = WorkoutSession(
        id = id,
        name = "Workout $id",
        completedAtTimestamp = completedAtTimestamp,
        status = SessionStatus.COMPLETED
    )

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
    }
}

private class RecordingWorkoutPlanner : WorkoutPlanner {
    var generateCalls: Int = 0
        private set
    val contexts = mutableListOf<WorkoutGenerationContext>()

    override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
        generateCalls += 1
        contexts += context
        val exercise = context.allowedExercises.first()
        return GeneratedWorkout(
            name = "Generated",
            focusMuscles = exercise.primaryMuscles,
            estimatedDurationMinutes = 30,
            exercises = listOf(
                GeneratedExercise(
                    exerciseId = exercise.id,
                    targetSets = 3,
                    repMin = 8,
                    repMax = 10
                )
            )
        )
    }
}

private class TodayUserProfileRepository(
    initialProfile: UserProfile
) : UserProfileRepository {
    private val profile = MutableStateFlow(initialProfile)

    override fun getUserProfile(): Flow<UserProfile> = profile
    override suspend fun getProfileOnce(): UserProfile = profile.value
    override suspend fun saveUserProfile(profile: UserProfile) {
        this.profile.value = profile
    }

    override suspend fun updatePrimaryGoal(goal: FitnessGoal) =
        profile.update { it.copy(primaryGoal = goal) }

    override suspend fun updateExperienceLevel(level: ExperienceLevel) =
        profile.update { it.copy(experienceLevel = level) }

    override suspend fun updatePreferredDuration(minutes: Int) =
        profile.update { it.copy(preferredDurationMinutes = minutes) }

    override suspend fun updateDaysPerWeek(days: Int) =
        profile.update { it.copy(daysPerWeek = days) }

    override suspend fun updateEquipment(equipment: List<String>) =
        profile.update { it.copy(availableEquipment = equipment) }

    override suspend fun updateUnit(unit: WeightUnit) =
        profile.update { it.copy(preferredUnit = unit) }

    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) =
        profile.update { it.copy(musclePriorities = priorities) }

    override suspend fun updateExcludedExercises(excludedIds: List<String>) =
        profile.update { it.copy(excludedExerciseIds = excludedIds) }
}

private class TodayWorkoutRepository(
    completedSessions: List<WorkoutSession>
) : WorkoutRepository {
    private val activeSession = MutableStateFlow<WorkoutSession?>(null)
    private val completed = MutableStateFlow(completedSessions)

    override fun observeActiveSession(): Flow<WorkoutSession?> = activeSession
    override suspend fun getActiveSessionOnce(): WorkoutSession? = activeSession.value
    override suspend fun getSessionById(sessionId: String): WorkoutSession? =
        activeSession.value?.takeIf { it.id == sessionId } ?: completed.value.firstOrNull { it.id == sessionId }

    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(null)
    override fun observeCompletedSessions(): Flow<List<WorkoutSession>> = completed
    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> =
        completed.value.sortedByDescending { it.completedAtTimestamp }.take(limit)

    override suspend fun startWorkoutFromGenerated(generated: GeneratedWorkout): WorkoutSession =
        error("Not used")

    override suspend fun logSetCompletion(
        setId: String,
        reps: Int?,
        weight: Double?,
        isCompleted: Boolean
    ) = Unit

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): WorkoutSummary = error("Not used")

    override suspend fun cancelWorkout(sessionId: String) = Unit
}
