package wallcrawl.elopenmike.com.feature.workout

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetOutcomeRules
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun completedPersistedSession_restoresCompletedUiWithActualDurationAndStoredUnit() = runTest {
        val repository = ActiveWorkoutRepository(
            workoutSession(status = SessionStatus.COMPLETED).copy(
                targetDurationMinutes = 50,
                actualDurationMinutes = 12,
                completedAtTimestamp = 5_000L,
                weightUnit = WeightUnit.KG
            )
        )
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value as ActiveWorkoutUiState.Completed
        assertThat(state.summary.durationMinutes).isEqualTo(12)
        assertThat(state.summary.unit).isEqualTo(WeightUnit.KG)
        assertThat(state.summary.totalSetsCompleted).isEqualTo(1)
    }

    @Test
    fun requestedExerciseIndexOutsideSession_isClampedToPersistedContents() = runTest {
        val repository = ActiveWorkoutRepository(workoutSession(SessionStatus.IN_PROGRESS))
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.goToExercise(99)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ActiveWorkoutUiState.Active
        assertThat(state.currentExerciseIndex).isEqualTo(0)
        assertThat(state.currentExercise).isNotNull()
    }

    @Test
    fun doubleFinishAndDelayedSetFailure_keepPersistedCompletionVisible() = runTest {
        val repository = ActiveWorkoutRepository(workoutSession(SessionStatus.IN_PROGRESS))
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.finishWorkout()
        viewModel.finishWorkout()
        advanceUntilIdle()

        assertThat(repository.completeCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value).isInstanceOf(ActiveWorkoutUiState.Completed::class.java)

        repository.failSetUpdates = true
        viewModel.updateSet("set", reps = 9, weight = 20.0, isCompleted = true)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isInstanceOf(ActiveWorkoutUiState.Completed::class.java)
    }

    @Test
    fun clearingLoadOnACompletedSet_doesNotEjectTheActiveWorkout() = runTest {
        // Reproduces the regression: a user clearing the weight field to retype it on an
        // already-completed set previously sent isCompleted=true with weight=null, the
        // repository rejected it, and the whole active workout was replaced by a
        // permanent full-screen error.
        val repository = ActiveWorkoutRepository(workoutSession(SessionStatus.IN_PROGRESS))
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.updateSet("set", reps = 10, weight = null, isCompleted = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ActiveWorkoutUiState.Active::class.java)
        assertThat((state as ActiveWorkoutUiState.Active).setUpdateError).isNotNull()

        // A subsequent valid edit recovers: the transient error clears and the set
        // persists as completed with the real load, without ever having left Active.
        viewModel.updateSet("set", reps = 10, weight = 25.0, isCompleted = true)
        advanceUntilIdle()

        val recovered = viewModel.uiState.value as ActiveWorkoutUiState.Active
        assertThat(recovered.setUpdateError).isNull()
        assertThat(repository.lastPersistedWeight).isEqualTo(25.0)
    }

    @Test
    fun clearingRepsOnACompletedSet_doesNotEjectTheActiveWorkout() = runTest {
        // The same latent problem exists for reps as for load.
        val repository = ActiveWorkoutRepository(workoutSession(SessionStatus.IN_PROGRESS))
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.updateSet("set", reps = null, weight = 20.0, isCompleted = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(ActiveWorkoutUiState.Active::class.java)
        assertThat((state as ActiveWorkoutUiState.Active).setUpdateError).isNotNull()
    }

    @Test
    fun dismissSetUpdateError_clearsTheTransientErrorWithoutAnotherWrite() = runTest {
        val repository = ActiveWorkoutRepository(workoutSession(SessionStatus.IN_PROGRESS))
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.updateSet("set", reps = 10, weight = null, isCompleted = true)
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as ActiveWorkoutUiState.Active).setUpdateError)
            .isNotNull()

        viewModel.dismissSetUpdateError()
        advanceUntilIdle()

        assertThat((viewModel.uiState.value as ActiveWorkoutUiState.Active).setUpdateError)
            .isNull()
    }

    @Test
    fun catalogLookupFailure_becomesVisibleError() = runTest {
        val repository = ActiveWorkoutRepository(workoutSession(SessionStatus.IN_PROGRESS))
        val viewModel = viewModel(repository, FailingExerciseCatalog())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value as ActiveWorkoutUiState.Error
        assertThat(state.message).contains("offline exercise catalog")
    }

    private fun viewModel(
        repository: WorkoutRepository,
        exerciseCatalog: ExerciseCatalog = InMemoryExerciseCatalog()
    ) = ActiveWorkoutViewModel(
        sessionId = SESSION_ID,
        workoutRepository = repository,
        exerciseCatalog = exerciseCatalog,
        workoutHistoryAnalyzer = WorkoutHistoryAnalyzer()
    )

    private fun workoutSession(status: SessionStatus): WorkoutSession {
        val workoutExerciseId = "workout-exercise"
        return WorkoutSession(
            id = SESSION_ID,
            name = "Workout",
            status = status,
            exercises = listOf(
                WorkoutExercise(
                    id = workoutExerciseId,
                    sessionId = SESSION_ID,
                    exerciseId = "incline-dumbbell-press",
                    orderIndex = 0,
                    targetSets = 1,
                    targetRepMin = 8,
                    targetRepMax = 10,
                    sets = listOf(
                        WorkoutSet(
                            id = "set",
                            workoutExerciseId = workoutExerciseId,
                            setNumber = 1,
                            targetReps = 10,
                            completedReps = 10,
                            completedWeight = 20.0,
                            isCompleted = true
                        )
                    )
                )
            )
        )
    }

    private companion object {
        const val SESSION_ID = "session"
    }
}

private class FailingExerciseCatalog : ExerciseCatalog {
    private val failure = IllegalStateException("asset parse failed")

    override fun getAllExercises(): Flow<List<Exercise>> = error("Not used")
    override suspend fun getExerciseById(id: String): Exercise? = throw failure
    override fun searchExercises(
        query: String,
        muscle: String?,
        equipment: String?
    ): Flow<List<Exercise>> = error("Not used")

    override suspend fun getMuscleGroups(): List<String> = error("Not used")
    override suspend fun getEquipmentTypes(): List<String> = error("Not used")
}

private class ActiveWorkoutRepository(initialSession: WorkoutSession) : WorkoutRepository {
    private val session = MutableStateFlow<WorkoutSession?>(initialSession)
    var completeCalls: Int = 0
        private set
    var failSetUpdates: Boolean = false
    var lastPersistedWeight: Double? = null
        private set
    var lastPersistedReps: Int? = null
        private set
    val persistedInputs = mutableListOf<SetPerformanceInput>()

    override fun observeActiveSession(): Flow<WorkoutSession?> = session
    override suspend fun getActiveSessionOnce(): WorkoutSession? = session.value
    override suspend fun getSessionById(sessionId: String): WorkoutSession? = session.value
    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = session
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> =
        flowOf(emptyList())

    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(0)
    override fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int> = flowOf(0)

    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> = emptyList()

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun startWorkoutFromTemplate(
        template: wallcrawl.elopenmike.com.core.model.WorkoutTemplate,
        userProfile: wallcrawl.elopenmike.com.core.model.UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun logSetCompletion(setId: String, performance: SetPerformanceInput) {
        if (failSetUpdates) error("Session is already complete")
        // Mirrors OfflineWorkoutRepository's real guards so this fake rejects exactly what
        // production rejects: the typed outcome invariants plus the completed
        // weight-and-reps requirement of a positive load and positive reps.
        SetOutcomeRules.requireValidOutcome(performance)
        require(
            !performance.isCompleted ||
                ((performance.reps ?: 0) > 0 && (performance.weight ?: 0.0) > 0.0)
        ) {
            "A completed weight and repetition set must have a positive load."
        }
        persistedInputs += performance
        lastPersistedWeight = performance.weight
        lastPersistedReps = performance.reps
    }

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): WorkoutSummary {
        completeCalls += 1
        val completed = requireNotNull(session.value).copy(
            status = SessionStatus.COMPLETED,
            completedAtTimestamp = 5_000L,
            actualDurationMinutes = actualDurationMinutes
        )
        session.value = completed
        return WorkoutSummary(
            sessionId = completed.id,
            workoutName = completed.name,
            durationMinutes = completed.actualDurationMinutes,
            totalSetsCompleted = completed.completedSetsCount,
            totalVolume = completed.totalVolume,
            unit = completed.weightUnit,
            completedAtTimestamp = requireNotNull(completed.completedAtTimestamp)
        )
    }

    override suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary? {
        val current = session.value ?: return null
        if (current.status != SessionStatus.COMPLETED) return null
        return WorkoutSummary(
            sessionId = current.id,
            workoutName = current.name,
            durationMinutes = current.actualDurationMinutes,
            totalSetsCompleted = current.completedSetsCount,
            totalVolume = current.totalVolume,
            unit = current.weightUnit,
            completedAtTimestamp = current.completedAtTimestamp ?: current.startedAtTimestamp
        )
    }

    override suspend fun cancelWorkout(sessionId: String) = Unit
}
