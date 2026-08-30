package wallcrawl.elopenmike.com.feature.workout

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetValuesDraft
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.test.MainDispatcherRule

/**
 * Gym-floor behaviour of the active workout: the rest timer's lifecycle, the typed
 * skip/stop path, and the finish and discard safeguards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutFeedbackViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeElapsedRealtimeClock()

    @Test
    fun completingASet_startsRestFromThatExercisesPersistedRestSeconds() = runTest {
        val repository = FakeRepository(session())
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        advanceUntilIdle()

        val rest = active(viewModel).restTimer
        assertThat(rest.state).isEqualTo(
            RestTimerState.Running(setId = "set-1", deadlineElapsedRealtime = 120_000L)
        )
        assertThat(rest.remainingSeconds).isEqualTo(120)
    }

    @Test
    fun editingAnAlreadyCompletedSet_doesNotRestartRest() = runTest {
        val repository = FakeRepository(session())
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()
        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        advanceUntilIdle()

        clock.advanceMillis(30_000L)
        viewModel.updateSet("set-1", completion(reps = 11, weight = 42.5))
        advanceUntilIdle()

        // Same deadline as the original completion: correcting a logged value is not a
        // new set, so it must not hand the user another two minutes of rest.
        assertThat(active(viewModel).restTimer.state).isEqualTo(
            RestTimerState.Running(setId = "set-1", deadlineElapsedRealtime = 120_000L)
        )
    }

    @Test
    fun clearingCompletion_doesNotStartRest() = runTest {
        val repository = FakeRepository(session())
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.updateSet(
            "set-1",
            SetPerformanceInput(reps = 10, weight = 40.0, isCompleted = false)
        )
        advanceUntilIdle()

        assertThat(active(viewModel).restTimer.state).isEqualTo(RestTimerState.Idle)
    }

    @Test
    fun doubleTappingCompletion_writesOnceAndStartsOneTimer() = runTest {
        val repository = FakeRepository(session())
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        advanceUntilIdle()

        assertThat(repository.persistedInputs).hasSize(1)
        assertThat(active(viewModel).restTimer.state).isEqualTo(
            RestTimerState.Running(setId = "set-1", deadlineElapsedRealtime = 120_000L)
        )
    }

    @Test
    fun secondTapBeforeTheWriteIsObserved_neitherRewritesTheSetNorRestartsRest() = runTest {
        // Room publishes a committed write asynchronously, so for a short window the
        // observed session still reports the set as incomplete and the completion
        // control still renders unchecked. A second tap in that window must not become a
        // second write, a second completion timestamp, or a second rest period.
        val repository = FakeRepository(session())
        repository.deferPublishing = true
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.setCompletion("set-1", SetValuesDraft(reps = 10, weight = 40.0), completed = true)
        advanceUntilIdle()
        val deadlineAfterFirstTap = active(viewModel).restTimer.state

        clock.advanceMillis(30_000L)
        viewModel.setCompletion("set-1", SetValuesDraft(reps = 10, weight = 40.0), completed = true)
        advanceUntilIdle()

        assertThat(repository.persistedInputs).hasSize(1)
        assertThat(active(viewModel).restTimer.state).isEqualTo(deadlineAfterFirstTap)
    }

    @Test
    fun editingValuesBeforeTheWriteIsObserved_keepsTheOriginalCompletionTimestamp() = runTest {
        val repository = FakeRepository(session())
        repository.deferPublishing = true
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.setCompletion("set-1", SetValuesDraft(reps = 10, weight = 40.0), completed = true)
        advanceUntilIdle()
        viewModel.updateSetValues("set-1", SetValuesDraft(reps = 11, weight = 42.5))
        advanceUntilIdle()

        val edit = repository.persistedInputs.last()
        assertThat(edit.reps).isEqualTo(11)
        assertThat(edit.weight).isEqualTo(42.5)
        assertThat(edit.isCompleted).isTrue()
        assertThat(edit.completedAtTimestamp).isEqualTo(WALL_CLOCK_MILLIS)
        assertThat(active(viewModel).restTimer.state).isInstanceOf(
            RestTimerState.Running::class.java
        )
    }

    @Test
    fun restCountdown_expiresOnceTheMonotonicClockPassesTheDeadline() = runTest {
        val repository = FakeRepository(session())
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()
        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        advanceUntilIdle()

        // The app spent the whole rest period in the background and ticked only on return.
        clock.advanceMillis(120_000L)
        viewModel.onRestTimerTick()
        advanceUntilIdle()

        val rest = active(viewModel).restTimer
        assertThat(rest.state).isEqualTo(RestTimerState.Expired("set-1"))
        assertThat(rest.remainingSeconds).isEqualTo(0)
    }

    @Test
    fun addSkipAndCancelRest_areExplicitAndVisibleInUiState() = runTest {
        val repository = FakeRepository(session())
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()
        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        advanceUntilIdle()

        viewModel.addRestTime()
        advanceUntilIdle()
        assertThat(active(viewModel).restTimer.remainingSeconds).isEqualTo(150)

        viewModel.skipRest()
        advanceUntilIdle()
        assertThat(active(viewModel).restTimer.state).isEqualTo(RestTimerState.Expired("set-1"))

        viewModel.cancelRest()
        advanceUntilIdle()
        assertThat(active(viewModel).restTimer.state).isEqualTo(RestTimerState.Idle)
    }

    @Test
    fun exerciseWithZeroRest_completesWithoutShowingACountdown() = runTest {
        val repository = FakeRepository(session(restSeconds = 0))
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        advanceUntilIdle()

        assertThat(active(viewModel).restTimer.state).isEqualTo(RestTimerState.Idle)
        assertThat(repository.persistedInputs).hasSize(1)
    }

    @Test
    fun skippingASet_recordsATypedReasonWithATimestampAndNoRest() = runTest {
        val repository = FakeRepository(session())
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.skipSet("set-1", SetStopReason.PAIN_STOP)
        advanceUntilIdle()

        val persisted = repository.persistedInputs.single()
        assertThat(persisted.stopReason).isEqualTo(SetStopReason.PAIN_STOP)
        assertThat(persisted.isCompleted).isFalse()
        assertThat(persisted.stoppedAtTimestamp).isEqualTo(WALL_CLOCK_MILLIS)
        assertThat(persisted.completedAtTimestamp).isNull()
        assertThat(persisted.feltManageable).isNull()
        assertThat(active(viewModel).restTimer.state).isEqualTo(RestTimerState.Idle)
    }

    @Test
    fun recordingManageable_keepsTheSetCompletedAndItsValues() = runTest {
        val repository = FakeRepository(session())
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()
        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        advanceUntilIdle()

        viewModel.recordFeltManageable("set-1", feltManageable = false)
        advanceUntilIdle()

        val persisted = repository.persistedInputs.last()
        assertThat(persisted.isCompleted).isTrue()
        assertThat(persisted.feltManageable).isFalse()
        assertThat(persisted.reps).isEqualTo(10)
        assertThat(persisted.weight).isEqualTo(40.0)
        assertThat(persisted.completedAtTimestamp).isEqualTo(WALL_CLOCK_MILLIS)
    }

    @Test
    fun recordingEffort_leavesCompletionAndValuesUntouchedAndNeverBlocksIt() = runTest {
        val repository = FakeRepository(session())
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()
        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        advanceUntilIdle()

        viewModel.recordEffort("set-1", rpe = 8f, rir = 2)
        advanceUntilIdle()

        val persisted = repository.persistedInputs.last()
        assertThat(persisted.rpe).isEqualTo(8f)
        assertThat(persisted.rir).isEqualTo(2)
        assertThat(persisted.isCompleted).isTrue()
    }

    @Test
    fun finishingWithOpenSets_asksForConfirmationBeforePersistingAnything() = runTest {
        val repository = FakeRepository(session(setCount = 3))
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()
        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        advanceUntilIdle()

        viewModel.requestFinish()
        advanceUntilIdle()

        assertThat(active(viewModel).pendingFinish)
            .isEqualTo(FinishDecision.ConfirmIncomplete(openSetCount = 2))
        assertThat(repository.completeCalls).isEqualTo(0)
    }

    @Test
    fun skippedSetsAreResolvedAndDoNotCountAsOpen() = runTest {
        val repository = FakeRepository(session(setCount = 2))
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()
        viewModel.updateSet("set-1", completion(reps = 10, weight = 40.0))
        advanceUntilIdle()
        viewModel.skipSet("set-2", SetStopReason.TIME_CONSTRAINT)
        advanceUntilIdle()

        viewModel.requestFinish()
        advanceUntilIdle()

        assertThat(repository.completeCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value)
            .isInstanceOf(ActiveWorkoutUiState.Completed::class.java)
    }

    @Test
    fun dismissingTheFinishConfirmation_persistsNothing() = runTest {
        val repository = FakeRepository(session(setCount = 2))
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.requestFinish()
        advanceUntilIdle()
        viewModel.dismissFinishConfirmation()
        advanceUntilIdle()

        assertThat(active(viewModel).pendingFinish).isNull()
        assertThat(repository.completeCalls).isEqualTo(0)
    }

    @Test
    fun confirmingFinish_isIdempotentUnderDoubleTaps() = runTest {
        val repository = FakeRepository(session(setCount = 2))
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()
        viewModel.requestFinish()
        advanceUntilIdle()

        viewModel.confirmFinish()
        viewModel.confirmFinish()
        advanceUntilIdle()

        assertThat(repository.completeCalls).isEqualTo(1)
    }

    @Test
    fun repeatedFinishRequestsWithOpenSets_doNotStackConfirmationsOrWrites() = runTest {
        val repository = FakeRepository(session(setCount = 2))
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.requestFinish()
        viewModel.requestFinish()
        advanceUntilIdle()

        assertThat(active(viewModel).pendingFinish)
            .isEqualTo(FinishDecision.ConfirmIncomplete(openSetCount = 2))
        assertThat(repository.completeCalls).isEqualTo(0)
    }

    @Test
    fun discardingAWorkout_requiresExplicitConfirmation() = runTest {
        val repository = FakeRepository(session())
        val viewModel = viewModel(repository)
        collect(viewModel)
        advanceUntilIdle()

        var discarded = false
        viewModel.requestCancel()
        advanceUntilIdle()
        assertThat(active(viewModel).isConfirmingDiscard).isTrue()
        assertThat(repository.cancelCalls).isEqualTo(0)

        viewModel.dismissCancelConfirmation()
        advanceUntilIdle()
        assertThat(active(viewModel).isConfirmingDiscard).isFalse()
        assertThat(repository.cancelCalls).isEqualTo(0)

        viewModel.requestCancel()
        viewModel.confirmCancel { discarded = true }
        advanceUntilIdle()
        assertThat(repository.cancelCalls).isEqualTo(1)
        assertThat(discarded).isTrue()
    }

    private fun TestScope.collect(viewModel: ActiveWorkoutViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private fun active(viewModel: ActiveWorkoutViewModel) =
        viewModel.uiState.value as ActiveWorkoutUiState.Active

    private fun completion(reps: Int, weight: Double) = SetPerformanceInput(
        reps = reps,
        weight = weight,
        completedAtTimestamp = WALL_CLOCK_MILLIS,
        isCompleted = true
    )

    private fun viewModel(repository: FakeRepository) = ActiveWorkoutViewModel(
        sessionId = SESSION_ID,
        workoutRepository = repository,
        exerciseCatalog = InMemoryExerciseCatalog(),
        workoutHistoryAnalyzer = WorkoutHistoryAnalyzer(),
        nowMillis = { WALL_CLOCK_MILLIS },
        elapsedRealtimeClock = clock
    )

    private fun session(
        setCount: Int = 1,
        restSeconds: Int = 120
    ): WorkoutSession {
        val workoutExerciseId = "workout-exercise"
        return WorkoutSession(
            id = SESSION_ID,
            name = "Workout",
            status = SessionStatus.IN_PROGRESS,
            exercises = listOf(
                WorkoutExercise(
                    id = workoutExerciseId,
                    sessionId = SESSION_ID,
                    exerciseId = "incline-dumbbell-press",
                    orderIndex = 0,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = setCount,
                        repRange = RepRange(8, 10),
                        targetWeight = 40.0,
                        restSeconds = restSeconds
                    ),
                    sets = (1..setCount).map { number ->
                        WorkoutSet(
                            id = "set-$number",
                            workoutExerciseId = workoutExerciseId,
                            setNumber = number,
                            targetReps = 10,
                            targetWeight = 40.0,
                            isCompleted = false
                        )
                    }
                )
            )
        )
    }

    private companion object {
        const val SESSION_ID = "session"
        const val WALL_CLOCK_MILLIS = 1_777_777L
    }
}
