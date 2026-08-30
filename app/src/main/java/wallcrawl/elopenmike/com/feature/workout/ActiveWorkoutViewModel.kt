package wallcrawl.elopenmike.com.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetValuesDraft
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActiveWorkoutViewModel(
    private val sessionId: String,
    private val workoutRepository: WorkoutRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val workoutHistoryAnalyzer: WorkoutHistoryAnalyzer,
    // Wall-clock stamp for persisted set outcomes, injected so tests are deterministic.
    // The rest timer never uses this clock; it has its own monotonic one.
    private val nowMillis: () -> Long = System::currentTimeMillis,
    elapsedRealtimeClock: ElapsedRealtimeClock = ElapsedRealtimeClock.System
) : ViewModel() {

    private val currentExerciseIndexFlow = MutableStateFlow(0)
    private val currentCatalogExerciseFlow = MutableStateFlow<Exercise?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)
    // A failed set update must never become a terminal, screen-replacing error the way
    // errorFlow does (see uiState below): it is a recoverable, dismissible condition on
    // the Active state, cleared automatically the next time a set update succeeds.
    private val setUpdateErrorFlow = MutableStateFlow<String?>(null)
    private val summaryFlow = MutableStateFlow<WorkoutSummary?>(null)
    private var finishRequested = false
    private var summaryJob: Job? = null

    // Rest timer state lives here so it survives recomposition and configuration changes.
    // It is intentionally not restored after process death; see RestTimerStateMachine.
    private val restTimer = RestTimerStateMachine(elapsedRealtimeClock)
    private val restTimerFlow = MutableStateFlow(RestTimerUiState.Idle)

    private val confirmationFlow = MutableStateFlow(ConfirmationState())
    // Set IDs whose completion transition is currently being written. A second tap on the
    // same set while the first write is in flight is a double tap, not a second set: it
    // must not produce another write or another rest timer.
    private val completionsInFlight = mutableSetOf<String>()
    // The outcome most recently written for a set, held only until the observed session
    // catches up. Room commits a write before its invalidation-driven query re-emits, so
    // for a short window the session still reports the old row and the completion control
    // still renders unchecked. Without this, a tap in that window would read a stale
    // "not completed" set and produce a second write, a fresh completion timestamp, and a
    // restarted rest period. All access is on the main dispatcher, like the flows above.
    private val outcomesAwaitingObservation = mutableMapOf<String, SetPerformanceInput>()
    private var latestSession: WorkoutSession? = null

    private val sessionHistoryFlow = combine(
        workoutRepository.observeSession(sessionId),
        workoutRepository.observeCompletedSessions(limit = MAX_PREVIOUS_PERFORMANCE_SESSIONS)
    ) { session, completedSessions ->
        SessionHistory(session, completedSessions)
    }

    private val errorStateFlow = combine(
        errorFlow,
        setUpdateErrorFlow,
        restTimerFlow,
        confirmationFlow
    ) { error, setUpdateError, restTimerState, confirmations ->
        ScreenState(error, setUpdateError, restTimerState, confirmations)
    }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionHistoryFlow,
        currentExerciseIndexFlow,
        currentCatalogExerciseFlow,
        errorStateFlow,
        summaryFlow
    ) { sessionHistory, exerciseIndex, catalogEx, screenState, summary ->
        val (session, completedSessions) = sessionHistory
        latestSession = session
        forgetOutcomesObservedIn(session)
        val error = screenState.error
        if (session == null) {
            ActiveWorkoutUiState.Loading
        } else if (session.status == SessionStatus.COMPLETED) {
            // The repository owns the summary so its personal-record count is computed once,
            // over one history window, whether the workout just finished or is revisited.
            when {
                summary?.sessionId == session.id -> ActiveWorkoutUiState.Completed(summary)
                error != null -> ActiveWorkoutUiState.Error(error)
                else -> {
                    loadSummary(session.id)
                    ActiveWorkoutUiState.Loading
                }
            }
        } else if (error != null) {
            ActiveWorkoutUiState.Error(error)
        } else if (session.status != SessionStatus.IN_PROGRESS) {
            ActiveWorkoutUiState.Error("Workout session is no longer active.")
        } else if (session.exercises.isEmpty()) {
            ActiveWorkoutUiState.Error("Workout session contains no exercises.")
        } else {
            val safeExerciseIndex = exerciseIndex.coerceIn(0, session.exercises.lastIndex)
            val currentEx = session.exercises[safeExerciseIndex]
            if (catalogEx?.id != currentEx.exerciseId) {
                loadCatalogExercise(currentEx.exerciseId)
            }

            val previousPerformance = workoutHistoryAnalyzer.latestCompletedExercisePerformance(
                sessions = completedSessions,
                exerciseId = currentEx.exerciseId
            )

            ActiveWorkoutUiState.Active(
                session = session,
                currentExerciseIndex = safeExerciseIndex,
                currentCatalogExercise = catalogEx,
                weightUnit = session.weightUnit,
                previousSets = previousPerformance?.sets.orEmpty(),
                previousSessionTimestamp = previousPerformance?.sessionCompletedAtTimestamp,
                previousWeightUnit = previousPerformance?.weightUnit ?: session.weightUnit,
                setUpdateError = screenState.setUpdateError,
                restTimer = screenState.restTimer,
                pendingFinish = screenState.confirmations.pendingFinish,
                isConfirmingDiscard = screenState.confirmations.isConfirmingDiscard
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActiveWorkoutUiState.Loading
    )

    private fun loadCatalogExercise(exerciseId: String) {
        viewModelScope.launch {
            try {
                val ex = exerciseCatalog.getExerciseById(exerciseId)
                currentCatalogExerciseFlow.value = ex
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorFlow.value =
                    "The offline exercise catalog could not load this workout's exercise details."
            }
        }
    }

    fun nextExercise() {
        val currentState = uiState.value as? ActiveWorkoutUiState.Active ?: return
        if (currentState.currentExerciseIndex < currentState.totalExercises - 1) {
            currentExerciseIndexFlow.value = currentState.currentExerciseIndex + 1
        }
    }

    fun previousExercise() {
        val currentState = uiState.value as? ActiveWorkoutUiState.Active ?: return
        if (currentState.currentExerciseIndex > 0) {
            currentExerciseIndexFlow.value = currentState.currentExerciseIndex - 1
        }
    }

    fun goToExercise(index: Int) {
        currentExerciseIndexFlow.value = index
    }

    fun updateSet(setId: String, reps: Int?, weight: Double?, isCompleted: Boolean) {
        updateSet(
            setId,
            SetPerformanceInput(
                reps = reps,
                weight = weight,
                completedAtTimestamp = if (isCompleted) nowMillis() else null,
                isCompleted = isCompleted
            )
        )
    }

    fun updateSet(setId: String, performance: SetPerformanceInput) {
        val current = currentOutcome(setId)
        // A repeat of the outcome this set already has is a duplicate tap or a re-emitted
        // edit, not new information, so it is never written again.
        if (current == performance) return
        val wasCompleted = current?.isCompleted == true
        // Rest starts only on a genuine false -> true completion transition, so editing a
        // value on an already-completed set never hands out another rest period.
        val startsRest = performance.isCompleted && !wasCompleted
        if (startsRest && !completionsInFlight.add(setId)) return

        viewModelScope.launch {
            try {
                workoutRepository.logSetCompletion(
                    setId = setId,
                    performance = performance
                )
                outcomesAwaitingObservation[setId] = performance
                setUpdateErrorFlow.value = null
                if (startsRest) startRestFor(setId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The write did not land, so the observed session remains the truth.
                outcomesAwaitingObservation.remove(setId)
                setUpdateErrorFlow.value = "Failed to update set: ${e.message}"
            } finally {
                if (startsRest) completionsInFlight.remove(setId)
            }
        }
    }

    /**
     * Persists edited values without changing the outcome: correcting a number on a
     * completed set keeps it completed, and on a stopped set keeps it stopped.
     */
    fun updateSetValues(setId: String, values: SetValuesDraft) {
        val current = currentOutcome(setId) ?: return
        updateSet(setId, current.withValues(values))
    }

    /**
     * Explicitly completes or un-completes a set.
     *
     * Completing stamps the outcome timestamp here, in one place, and clears any stop
     * reason. Un-completing clears every completion-only field in the same write, so the
     * row can never claim completion without a timestamp or keep feedback for work that
     * is no longer recorded as done.
     */
    fun setCompletion(setId: String, values: SetValuesDraft, completed: Boolean) {
        val existing = currentOutcome(setId) ?: SetPerformanceInput(isCompleted = false)
        val updated = if (completed) {
            existing.withValues(values).copy(
                stopReason = null,
                stoppedAtTimestamp = null,
                completedAtTimestamp = existing.completedAtTimestamp
                    ?.takeIf { existing.isCompleted }
                    ?: nowMillis(),
                isCompleted = true
            )
        } else {
            existing.withValues(values).copy(
                rpe = null,
                rir = null,
                feltManageable = null,
                completedAtTimestamp = null,
                stoppedAtTimestamp = null,
                stopReason = null,
                isCompleted = false
            )
        }
        updateSet(setId, updated)
    }

    /**
     * Records that the user deliberately skipped or stopped this set, with a typed
     * reason. Nothing about the reason is diagnostic, and no rest period follows work
     * that was not performed.
     */
    fun skipSet(setId: String, reason: SetStopReason) {
        // Partial work already entered for an unfinished set is kept; a set that was
        // completed and is now being stopped drops its completion-only fields, which the
        // outcome invariants require anyway.
        val base = currentOutcome(setId)
            ?.takeIf { !it.isCompleted }
            ?: SetPerformanceInput(isCompleted = false)
        updateSet(
            setId,
            base.copy(
                rpe = null,
                rir = null,
                feltManageable = null,
                completedAtTimestamp = null,
                stopReason = reason,
                stoppedAtTimestamp = nowMillis(),
                isCompleted = false
            )
        )
    }

    /** Records the optional manageable confirmation for work the user already completed. */
    fun recordFeltManageable(setId: String, feltManageable: Boolean) {
        val current = currentOutcome(setId) ?: return
        if (!current.isCompleted) return
        updateSet(setId, current.copy(feltManageable = feltManageable))
    }

    /**
     * Records optional effort feedback. Both values stay nullable: leaving them unanswered
     * is a first-class outcome and never blocks completing a set.
     */
    fun recordEffort(setId: String, rpe: Float?, rir: Int?) {
        val current = currentOutcome(setId) ?: return
        if (!current.isCompleted && current.stopReason == null) return
        updateSet(setId, current.copy(rpe = rpe, rir = rir))
    }

    /** Dismisses a recoverable set-update error without requiring another edit. */
    fun dismissSetUpdateError() {
        setUpdateErrorFlow.value = null
    }

    /** Re-evaluates the countdown against the monotonic clock; the UI calls this while resting. */
    fun onRestTimerTick() {
        restTimer.refresh()
        publishRestTimer()
    }

    fun addRestTime() {
        restTimer.addThirtySeconds()
        publishRestTimer()
    }

    fun skipRest() {
        restTimer.skip()
        publishRestTimer()
    }

    fun cancelRest() {
        restTimer.cancel()
        publishRestTimer()
    }

    /**
     * Asks to finish. With open sets this only raises a confirmation carrying their count;
     * nothing is persisted until the user explicitly confirms.
     */
    fun requestFinish() {
        if (finishRequested) return
        val session = (uiState.value as? ActiveWorkoutUiState.Active)?.session ?: return
        when (val decision = session.finishDecision()) {
            FinishDecision.Complete -> finishWorkout()
            is FinishDecision.ConfirmIncomplete ->
                confirmationFlow.value = confirmationFlow.value.copy(pendingFinish = decision)
        }
    }

    fun confirmFinish() {
        confirmationFlow.value = confirmationFlow.value.copy(pendingFinish = null)
        finishWorkout()
    }

    fun dismissFinishConfirmation() {
        confirmationFlow.value = confirmationFlow.value.copy(pendingFinish = null)
    }

    fun requestCancel() {
        confirmationFlow.value = confirmationFlow.value.copy(isConfirmingDiscard = true)
    }

    fun dismissCancelConfirmation() {
        confirmationFlow.value = confirmationFlow.value.copy(isConfirmingDiscard = false)
    }

    fun finishWorkout() {
        if (finishRequested) return
        val currentState = uiState.value as? ActiveWorkoutUiState.Active ?: return
        finishRequested = true
        viewModelScope.launch {
            try {
                val elapsedMinutes = elapsedWorkoutMinutes(
                    startedAtTimestamp = currentState.session.startedAtTimestamp,
                    nowTimestamp = nowMillis()
                )
                summaryFlow.value = workoutRepository.completeWorkout(sessionId, elapsedMinutes)
                restTimer.cancel()
                publishRestTimer()
            } catch (e: CancellationException) {
                finishRequested = false
                throw e
            } catch (e: Exception) {
                finishRequested = false
                errorFlow.value = "Failed to finish workout: ${e.message}"
            }
        }
    }

    fun confirmCancel(onCancelled: () -> Unit) {
        confirmationFlow.value = confirmationFlow.value.copy(isConfirmingDiscard = false)
        cancelWorkout(onCancelled)
    }

    fun cancelWorkout(onCancelled: () -> Unit) {
        viewModelScope.launch {
            try {
                workoutRepository.cancelWorkout(sessionId)
                onCancelled()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorFlow.value = "Failed to cancel workout: ${e.message}"
            }
        }
    }

    private fun startRestFor(setId: String) {
        val restSeconds = exerciseContaining(setId)?.prescription?.restSeconds ?: return
        restTimer.start(setId = setId, restSeconds = restSeconds)
        publishRestTimer()
    }

    private fun publishRestTimer() {
        restTimerFlow.value = RestTimerUiState(
            state = restTimer.state.value,
            remainingSeconds = restTimer.remainingSeconds()
        )
    }

    /**
     * The outcome this ViewModel believes a set currently has: the last one it wrote if
     * the observed session has not caught up yet, otherwise whatever is persisted.
     */
    private fun currentOutcome(setId: String): SetPerformanceInput? =
        outcomesAwaitingObservation[setId] ?: findSet(setId)?.toPerformanceInput()

    /** Drops pending outcomes the observed session now reports, so it becomes the truth again. */
    private fun forgetOutcomesObservedIn(session: WorkoutSession?) {
        if (outcomesAwaitingObservation.isEmpty()) return
        val observed = session
            ?.exercises
            ?.flatMap { it.sets }
            ?.associate { it.id to it.toPerformanceInput() }
            .orEmpty()
        outcomesAwaitingObservation.entries.removeAll { (setId, pending) ->
            observed[setId] == pending
        }
    }

    private fun currentSession(): WorkoutSession? =
        (uiState.value as? ActiveWorkoutUiState.Active)?.session ?: latestSession

    private fun findSet(setId: String): WorkoutSet? = currentSession()
        ?.exercises
        ?.firstNotNullOfOrNull { exercise -> exercise.sets.firstOrNull { it.id == setId } }

    private fun exerciseContaining(setId: String): WorkoutExercise? = currentSession()
        ?.exercises
        ?.firstOrNull { exercise -> exercise.sets.any { it.id == setId } }

    companion object {
        private const val MAX_PREVIOUS_PERFORMANCE_SESSIONS = 50

        fun provideFactory(
            sessionId: String,
            workoutRepository: WorkoutRepository,
            exerciseCatalog: ExerciseCatalog,
            workoutHistoryAnalyzer: WorkoutHistoryAnalyzer
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ActiveWorkoutViewModel(
                    sessionId,
                    workoutRepository,
                    exerciseCatalog,
                    workoutHistoryAnalyzer
                ) as T
            }
        }
    }

    private data class SessionHistory(
        val session: WorkoutSession?,
        val completedSessions: List<WorkoutSession>
    )

    private data class ConfirmationState(
        val pendingFinish: FinishDecision.ConfirmIncomplete? = null,
        val isConfirmingDiscard: Boolean = false
    )

    private data class ScreenState(
        val error: String?,
        val setUpdateError: String?,
        val restTimer: RestTimerUiState,
        val confirmations: ConfirmationState
    )

    private fun loadSummary(completedSessionId: String) {
        // completeWorkout already returns the summary, and Room publishes the completed
        // session before it returns; without this the finish path reads history twice.
        if (finishRequested || summaryJob?.isActive == true) return
        summaryJob = viewModelScope.launch {
            try {
                summaryFlow.value = workoutRepository.getWorkoutSummary(completedSessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorFlow.value = "Failed to load workout summary: ${e.message}"
            }
        }
    }

}

/**
 * The values already recorded for a set, as an input that re-states the same outcome.
 *
 * Feedback edits build on this so that answering "felt manageable" or adding an RPE never
 * silently rewrites the performance values or the completion timestamp.
 */
internal fun WorkoutSet.toPerformanceInput(): SetPerformanceInput = SetPerformanceInput(
    reps = completedReps,
    weight = completedWeight,
    assistanceWeight = completedAssistanceWeight,
    durationSeconds = completedDurationSeconds,
    distanceMeters = completedDistanceMeters,
    rpe = rpe,
    rir = rir,
    feltManageable = feltManageable,
    completedAtTimestamp = completedAtTimestamp,
    stoppedAtTimestamp = stoppedAtTimestamp,
    stopReason = stopReason,
    isCompleted = isCompleted
)

/** The same outcome with new type-specific values. */
internal fun SetPerformanceInput.withValues(values: SetValuesDraft): SetPerformanceInput = copy(
    reps = values.reps,
    weight = values.weight,
    assistanceWeight = values.assistanceWeight,
    durationSeconds = values.durationSeconds,
    distanceMeters = values.distanceMeters
)

internal fun elapsedWorkoutMinutes(
    startedAtTimestamp: Long,
    nowTimestamp: Long
): Int {
    val elapsedMillis = if (startedAtTimestamp < 0 || nowTimestamp <= startedAtTimestamp) {
        0L
    } else {
        nowTimestamp - startedAtTimestamp
    }
    return (elapsedMillis / 60_000L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
        .coerceAtLeast(1)
}
