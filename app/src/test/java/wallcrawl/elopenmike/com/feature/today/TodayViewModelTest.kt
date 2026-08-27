package wallcrawl.elopenmike.com.feature.today

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
            nowTimestamp = { now },
            clock = flowOf(now)
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
            nowTimestamp = { now },
            clock = flowOf(now)
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

    @Test
    fun startWorkout_afterProfileConstraintChange_neverPersistsStaleExercise() = runTest {
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
            nowTimestamp = { now },
            clock = flowOf(now)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        val staleExerciseId = planner.contexts.single().allowedExercises.first().id

        profileRepository.updateExcludedExercises(listOf(staleExerciseId))
        viewModel.startWorkout {}
        advanceUntilIdle()

        assertThat(workoutRepository.startRequests).isEmpty()
        assertThat(planner.contexts.last().allowedExercises.map { it.id })
            .doesNotContain(staleExerciseId)

        viewModel.startWorkout {}
        advanceUntilIdle()

        assertThat(workoutRepository.startRequests).hasSize(1)
        assertThat(workoutRepository.startRequests.single().workout.exercises.map { it.exerciseId })
            .doesNotContain(staleExerciseId)
    }

    @Test
    fun startWorkout_persistsTheUnitUsedToGenerateTargets() = runTest {
        val now = 20 * DAY_MILLIS
        val profileRepository = TodayUserProfileRepository(
            UserProfile(preferredUnit = WeightUnit.KG)
        )
        val workoutRepository = TodayWorkoutRepository(emptyList())
        val catalog = InMemoryExerciseCatalog()
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
            workoutPlanner = RecordingWorkoutPlanner(),
            workoutValidator = GeneratedWorkoutValidator(catalog),
            nowTimestamp = { now },
            clock = flowOf(now)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.startWorkout {}
        advanceUntilIdle()

        assertThat(workoutRepository.startRequests.single().userProfile.preferredUnit)
            .isEqualTo(WeightUnit.KG)
    }

    @Test
    fun completedThisWeek_updatesForNewCompletionsAndMovingClock() = runTest {
        val now = 20 * DAY_MILLIS
        val clock = MutableStateFlow(now)
        val profileRepository = TodayUserProfileRepository(UserProfile())
        val workoutRepository = TodayWorkoutRepository(
            listOf(completedSession("recent", now - DAY_MILLIS))
        )
        val catalog = InMemoryExerciseCatalog()
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
            workoutPlanner = RecordingWorkoutPlanner(),
            workoutValidator = GeneratedWorkoutValidator(catalog),
            nowTimestamp = { clock.value },
            clock = clock
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertThat((viewModel.uiState.value as TodayUiState.Success).completedThisWeek)
            .isEqualTo(1)

        workoutRepository.addCompletedSession(
            completedSession("new", now + 1_000L)
        )
        advanceUntilIdle()

        assertThat((viewModel.uiState.value as TodayUiState.Success).completedThisWeek)
            .isEqualTo(2)

        clock.value = now + (8 * DAY_MILLIS)
        advanceUntilIdle()

        assertThat((viewModel.uiState.value as TodayUiState.Success).completedThisWeek)
            .isEqualTo(0)
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
        this.profile.update { current -> profile.copy(revision = current.revision + 1L) }
    }

    override suspend fun updatePrimaryGoal(goal: FitnessGoal) =
        updateProfile { it.copy(primaryGoal = goal) }

    override suspend fun updateExperienceLevel(level: ExperienceLevel) =
        updateProfile { it.copy(experienceLevel = level) }

    override suspend fun updatePreferredDuration(minutes: Int) =
        updateProfile { it.copy(preferredDurationMinutes = minutes) }

    override suspend fun updateDaysPerWeek(days: Int) =
        updateProfile { it.copy(daysPerWeek = days) }

    override suspend fun updateEquipment(equipment: List<String>) =
        updateProfile { it.copy(availableEquipment = equipment) }

    override suspend fun updateUnit(unit: WeightUnit) =
        updateProfile { it.copy(preferredUnit = unit) }

    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) =
        updateProfile { it.copy(musclePriorities = priorities) }

    override suspend fun updateExcludedExercises(excludedIds: List<String>) =
        updateProfile { it.copy(excludedExerciseIds = excludedIds) }

    private fun updateProfile(transform: (UserProfile) -> UserProfile) {
        profile.update { current ->
            transform(current).copy(revision = current.revision + 1L)
        }
    }
}

private class TodayWorkoutRepository(
    completedSessions: List<WorkoutSession>
) : WorkoutRepository {
    val startRequests = mutableListOf<StartWorkoutRequest>()
    private val activeSession = MutableStateFlow<WorkoutSession?>(null)
    private val completed = MutableStateFlow(completedSessions)

    override fun observeActiveSession(): Flow<WorkoutSession?> = activeSession
    override suspend fun getActiveSessionOnce(): WorkoutSession? = activeSession.value
    override suspend fun getSessionById(sessionId: String): WorkoutSession? =
        activeSession.value?.takeIf { it.id == sessionId } ?: completed.value.firstOrNull { it.id == sessionId }

    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(null)
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> = completed

    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(completed.value.size)

    override fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int> =
        completed.map { sessions ->
            sessions.count { session ->
                session.completedAtTimestamp?.let { it >= startTimestamp } == true
            }
        }
    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> =
        completed.value.sortedByDescending { it.completedAtTimestamp }.take(limit)

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        userProfile: UserProfile
    ): WorkoutSession {
        startRequests += StartWorkoutRequest(generated, userProfile)
        return WorkoutSession(
            id = "started-session",
            name = generated.name,
            weightUnit = userProfile.preferredUnit
        )
    }

    override suspend fun startWorkoutFromTemplate(
        template: wallcrawl.elopenmike.com.core.model.WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    fun addCompletedSession(session: WorkoutSession) {
        completed.update { it + session }
    }

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

private data class StartWorkoutRequest(
    val workout: GeneratedWorkout,
    val userProfile: UserProfile
)
