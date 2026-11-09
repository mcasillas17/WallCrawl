package wallcrawl.elopenmike.com.feature.workout

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.CompletedExercisePerformance
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PrescriptionPurpose
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetValuesDraft
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModel(
    private val sessionId: String,
    private val workoutRepository: WorkoutRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val workoutHistoryAnalyzer: WorkoutHistoryAnalyzer,
    // Wall-clock stamp for persisted set outcomes, injected so tests are deterministic.
    // The rest timer never uses this clock; it has its own monotonic one.
    private val nowMillis: () -> Long = System::currentTimeMillis,
    elapsedRealtimeClock: ElapsedRealtimeClock = ElapsedRealtimeClock.System,
    private val programRepository: wallcrawl.elopenmike.com.core.database.repository.ProgramRepository? = null,
    private val userProfileRepository: UserProfileRepository? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val currentExerciseIndexFlow = savedStateHandle.getStateFlow("exerciseIndex", 0)
    private var catalogRequest: kotlinx.coroutines.Job? = null
    private var requestedExerciseId: String? = null
    private val currentCatalogExerciseFlow = MutableStateFlow<Exercise?>(null)
    // Every failure the screen can show is a resource id rather than a sentence: an
    // exception message is written for a log and cannot be translated.
    @StringRes
    private val errorFlow = MutableStateFlow<Int?>(null)
    // A failed set update must never become a terminal, screen-replacing error the way
    // errorFlow does (see uiState below): it is a recoverable, dismissible condition on
    // the Active state, cleared automatically the next time a set update succeeds.
    private val setUpdateErrorFlow = MutableStateFlow<Int?>(null)
    private val programFinishUnsupportedFlow = MutableStateFlow(false)
    private val refreshRequests = MutableStateFlow(0L)

    // Rest timer state lives here so it survives recomposition and configuration changes.
    // It is intentionally not restored after process death; see RestTimerStateMachine.
    private val restTimer = RestTimerStateMachine(elapsedRealtimeClock)
    private val restTimerFlow = MutableStateFlow(RestTimerUiState.Idle)

    private val confirmationFlow = MutableStateFlow(ConfirmationState())
    // A second tap cannot interleave with an uncommitted write to the same set.
    private val writesInFlight = mutableSetOf<String>()
    // The outcome most recently written for a set, held only until the observed session
    // catches up. Room commits a write before its invalidation-driven query re-emits, so
    // for a short window the session still reports the old row and the completion control
    // still renders unchecked. Without this, a tap in that window would read a stale
    // "not completed" set and produce a second write, a fresh completion timestamp, and a
    // restarted rest period. All access is on the main dispatcher, like the flows above.
    private val outcomesAwaitingObservation = mutableMapOf<String, SetPerformanceInput>()
    private var latestSession: WorkoutSession? = null

    private val sessionHistoryFlow = refreshRequests.flatMapLatest {
        flow {
            coroutineScope {
                val sessions = flow {
                    emitAll(workoutRepository.observeSession(sessionId))
                }.stateIn(this)
                emitAll(sessions.map { it?.status }.distinctUntilChanged().flatMapLatest { status ->
                    val matchingSessions = sessions.filter { it?.status == status }
                    if (status == SessionStatus.IN_PROGRESS) {
                        workoutRepository.observeCompletedSessions(MAX_PREVIOUS_PERFORMANCE_SESSIONS).flatMapLatest { history ->
                            val active = sessions.value
                            val startedAt = active?.startedAtTimestamp ?: 0L
                            val previous = history.filter { session ->
                                session.completedAtTimestamp?.let { it < startedAt } == true
                            }
                            val older = if (history.size >= MAX_PREVIOUS_PERFORMANCE_SESSIONS) olderWorkingSets(active, previous) else emptyMap()
                            matchingSessions.map { session -> SessionHistory(session, previous, olderWorkingSets = older) }
                        }
                    } else {
                        matchingSessions.transformLatest { session ->
                            if (session?.status == SessionStatus.COMPLETED) {
                                emit(SessionHistory(null, emptyList(), isLoading = true))
                                emit(SessionHistory(session, emptyList(),
                                    summary = workoutRepository.getWorkoutSummary(session.id)))
                            } else {
                                emit(SessionHistory(session, emptyList()))
                            }
                        }
                    }
                })
            }
        }.onStart {
            emit(SessionHistory(null, emptyList(), isLoading = true))
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(SessionHistory(null, emptyList(), readError = R.string.workout_summary_error))
        }
    }

    private val associationFlow = refreshRequests.flatMapLatest {
        programRepository?.let { repository ->
            flow { emitAll(repository.observeAssociation(sessionId)) }
        }?.map { AssociationState(it) }
            ?.catch { error ->
                if (error is CancellationException) throw error
                if (!wallcrawl.elopenmike.com.feature.program.isExpectedProgramReadFailure(error)) throw error
                emit(AssociationState(error = R.string.program_read_failed))
            } ?: kotlinx.coroutines.flow.flowOf(AssociationState())
    }
    private data class AssociationState(
        val association: wallcrawl.elopenmike.com.core.model.ProgramHistoryAssociation? = null,
        val error: Int? = null
    )
    private val errorStateFlow = combine(
        errorFlow,
        setUpdateErrorFlow,
        restTimerFlow,
        confirmationFlow,
        programFinishUnsupportedFlow
    ) { error, setUpdateError, restTimerState, confirmations, programFinishUnsupported ->
        ScreenState(error, setUpdateError, restTimerState, confirmations, programFinishUnsupported)
    }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionHistoryFlow,
        currentExerciseIndexFlow,
        currentCatalogExerciseFlow,
        errorStateFlow,
        associationFlow
    ) { sessionHistory, exerciseIndex, catalogEx, screenState, associationState ->
        val (session, completedSessions) = sessionHistory
        latestSession = session
        forgetOutcomesObservedIn(session)
        val error = screenState.error
        if (sessionHistory.isLoading) {
            ActiveWorkoutUiState.Loading
        } else if (sessionHistory.readError != null) {
            ActiveWorkoutUiState.Error(sessionHistory.readError)
        } else if (session == null) {
            ActiveWorkoutUiState.Error(R.string.workout_no_longer_active)
        } else if (session.status == SessionStatus.COMPLETED) {
            sessionHistory.summary?.let {
                ActiveWorkoutUiState.Completed(it, associationState.association?.supportedSnapshot != null &&
                    session.exercises.any { exercise -> exercise.sets.any { set -> !set.isCompleted } })
            }
                ?: ActiveWorkoutUiState.Error(R.string.workout_no_longer_active)
        } else if (error != null) {
            ActiveWorkoutUiState.Error(error)
        } else if (session.status != SessionStatus.IN_PROGRESS) {
            ActiveWorkoutUiState.Error(R.string.workout_no_longer_active)
        } else if (session.exercises.isEmpty()) {
            ActiveWorkoutUiState.Error(R.string.workout_has_no_exercises)
        } else {
            val safeExerciseIndex = exerciseIndex.coerceIn(0, session.exercises.lastIndex)
            val currentEx = session.exercises[safeExerciseIndex]
            if (requestedExerciseId != currentEx.exerciseId) {
                loadCatalogExercise(currentEx.exerciseId)
            }

            val previousPerformance = workoutHistoryAnalyzer.latestCompletedExercisePerformance(
                sessions = completedSessions,
                exerciseId = currentEx.exerciseId,
                purpose = currentEx.prescription.purpose,
                isLegacyMobility = currentEx.isLegacyMobility
            ) ?: sessionHistory.olderWorkingSets[currentEx.exerciseId]?.takeIf {
                currentEx.prescription.purpose == PrescriptionPurpose.ORDINARY && !currentEx.isLegacyMobility &&
                    currentEx.prescription.exerciseType == ExerciseType.WEIGHT_REPS
            }

            ActiveWorkoutUiState.Active(
                session = session,
                currentExerciseIndex = safeExerciseIndex,
                currentCatalogExercise = catalogEx?.takeIf { it.id == currentEx.exerciseId },
                isSaving = screenState.confirmations.saving || screenState.confirmations.ending || outcomesAwaitingObservation.isNotEmpty(),
                weightUnit = session.weightUnit,
                previousSets = previousPerformance?.sets.orEmpty(),
                previousSessionTimestamp = previousPerformance?.sessionCompletedAtTimestamp,
                previousWeightUnit = previousPerformance?.weightUnit ?: session.weightUnit,
                setUpdateError = screenState.setUpdateError,
                restTimer = screenState.restTimer,
                pendingFinish = screenState.confirmations.pendingFinish,
                isConfirmingDiscard = screenState.confirmations.isConfirmingDiscard,
                programAssociation = associationState.association,
                programReadError = associationState.error,
                programFinishUnsupported = screenState.programFinishUnsupported,
                painExclusionOffer = screenState.confirmations.painExclusionOffer
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        initialValue = ActiveWorkoutUiState.Loading
    )

    fun retry() {
        requestedExerciseId = null
        errorFlow.value = null
        refreshRequests.update { it + 1 }
    }

    private fun loadCatalogExercise(exerciseId: String) {
        requestedExerciseId = exerciseId
        catalogRequest?.cancel()
        catalogRequest = viewModelScope.launch {
            try {
                val ex = exerciseCatalog.getExerciseById(exerciseId)
                currentCatalogExerciseFlow.value = ex
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorFlow.value = R.string.workout_catalog_error
            }
        }
    }

    fun nextExercise() {
        val currentState = uiState.value as? ActiveWorkoutUiState.Active ?: return
        if (currentState.currentExerciseIndex < currentState.totalExercises - 1) {
            savedStateHandle["exerciseIndex"] = currentState.currentExerciseIndex + 1
        }
    }

    fun previousExercise() {
        val currentState = uiState.value as? ActiveWorkoutUiState.Active ?: return
        if (currentState.currentExerciseIndex > 0) {
            savedStateHandle["exerciseIndex"] = currentState.currentExerciseIndex - 1
        }
    }

    fun goToExercise(index: Int) {
        savedStateHandle["exerciseIndex"] = index
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
        if (uiState.value !is ActiveWorkoutUiState.Active || confirmationFlow.value.ending) return
        val current = currentOutcome(setId)
        // A repeat of the outcome this set already has is a duplicate tap or a re-emitted
        // edit, not new information, so it is never written again.
        if (current == performance) return
        val stoppedExerciseId = if (performance.stopReason == SetStopReason.PAIN_STOP && current?.stopReason != SetStopReason.PAIN_STOP) {
            exerciseContaining(setId)?.exerciseId
        } else null
        val wasCompleted = current?.isCompleted == true
        // Rest starts only on a genuine false -> true completion transition, so editing a
        // value on an already-completed set never hands out another rest period.
        val startsRest = performance.isCompleted && !wasCompleted
        if (!writesInFlight.add(setId)) return
        confirmationFlow.update { it.copy(saving = true) }
        setUpdateErrorFlow.value = null

        viewModelScope.launch {
            try {
                workoutRepository.logSetCompletion(
                    setId = setId,
                    performance = performance
                )
                outcomesAwaitingObservation[setId] = performance
                setUpdateErrorFlow.value = null
                if (startsRest) startRestFor(setId)
                if (stoppedExerciseId != null) offerPainExclusion(stoppedExerciseId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The write did not land, so the observed session remains the truth.
                setUpdateErrorFlow.value = R.string.workout_set_update_error
            } finally {
                writesInFlight.remove(setId)
                confirmationFlow.update { it.copy(saving = writesInFlight.isNotEmpty()) }
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
    fun skipSet(setId: String, reason: SetStopReason, values: SetValuesDraft? = null) {
        // Partial work already entered for an unfinished set is kept; a set that was
        // completed and is now being stopped drops its completion-only fields, which the
        // outcome invariants require anyway.
        val base = currentOutcome(setId)
            ?.takeIf { !it.isCompleted }
            ?: SetPerformanceInput(isCompleted = false)
        val stopped = if (values == null || currentOutcome(setId)?.isCompleted == true) base else base.withValues(values)
        updateSet(
            setId,
            stopped.copy(
                rpe = null,
                rir = null,
                feltManageable = null,
                completedAtTimestamp = null,
                stopReason = reason,
                stoppedAtTimestamp = base.stoppedAtTimestamp?.takeIf { base.stopReason == reason } ?: nowMillis(),
                isCompleted = false
            )
        )
    }

    private suspend fun offerPainExclusion(exerciseId: String) {
        val repository = userProfileRepository ?: return
        val profile = try {
            repository.getUserProfile().first()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return // The stop was recorded even if exclusions could not be read.
        }
        val confirmations = confirmationFlow.value
        if (exerciseId !in profile.excludedExerciseIds && exerciseId !in confirmations.pendingExclusions) {
            confirmationFlow.value = confirmations.copy(pendingExclusions = confirmations.pendingExclusions + exerciseId)
        }
    }

    fun acceptPainExclusion(exerciseId: String) {
        val repository = userProfileRepository ?: return
        val confirmations = confirmationFlow.value
        if (confirmations.pendingExclusions.firstOrNull() != exerciseId || confirmations.exclusionSaving) return
        confirmationFlow.value = confirmations.copy(exclusionSaving = true, exclusionSaveFailed = false)
        viewModelScope.launch {
            try {
                repository.setExerciseExcluded(exerciseId, true)
                confirmationFlow.update { it.withoutFirstExclusion() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                confirmationFlow.update { it.copy(exclusionSaving = false, exclusionSaveFailed = true) }
            }
        }
    }

    fun dismissPainExclusionOffer(exerciseId: String) {
        val confirmations = confirmationFlow.value
        if (confirmations.pendingExclusions.firstOrNull() == exerciseId && !confirmations.exclusionSaving) {
            confirmationFlow.value = confirmations.withoutFirstExclusion()
        }
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
        if (writePending()) return
        val session = (uiState.value as? ActiveWorkoutUiState.Active)?.session ?: return
        when (val decision = session.finishDecision()) {
            FinishDecision.Complete -> finishWorkout()
            is FinishDecision.ConfirmIncomplete ->
                confirmationFlow.value = confirmationFlow.value.copy(pendingFinish = decision)
        }
    }

    fun confirmFinish() {
        if (confirmationFlow.value.pendingFinish == null) return
        confirmationFlow.value = confirmationFlow.value.copy(pendingFinish = null)
        finishWorkout()
    }

    fun dismissFinishConfirmation() {
        confirmationFlow.value = confirmationFlow.value.copy(pendingFinish = null)
    }

    fun requestCancel() {
        if (writePending()) return
        confirmationFlow.value = confirmationFlow.value.copy(isConfirmingDiscard = true)
    }

    fun dismissCancelConfirmation() {
        confirmationFlow.value = confirmationFlow.value.copy(isConfirmingDiscard = false)
    }

    fun finishWorkout() {
        if (writePending()) return
        val currentState = uiState.value as? ActiveWorkoutUiState.Active ?: return
        confirmationFlow.update { it.copy(ending = true) }
        viewModelScope.launch {
            try {
                val elapsedMinutes = elapsedWorkoutMinutes(
                    startedAtTimestamp = currentState.session.startedAtTimestamp,
                    nowTimestamp = nowMillis()
                )
                // Its return describes the completion transaction. The observed read stays
                // authoritative if deletion/restore replaces history before this call resumes.
                workoutRepository.completeWorkout(sessionId, elapsedMinutes)
                restTimer.cancel()
                publishRestTimer()
            } catch (e: CancellationException) {
                confirmationFlow.update { it.copy(ending = false) }
                throw e
            } catch (e: wallcrawl.elopenmike.com.core.model.ProgramException) {
                confirmationFlow.update { it.copy(ending = false) }
                if (e.failure == wallcrawl.elopenmike.com.core.model.ProgramFailure.UNSUPPORTED_POLICY) {
                    programFinishUnsupportedFlow.value = true
                    setUpdateErrorFlow.value = R.string.program_finish_unsupported
                } else setUpdateErrorFlow.value = R.string.workout_finish_error
            } catch (e: Exception) {
                confirmationFlow.update { it.copy(ending = false) }
                errorFlow.value = R.string.workout_finish_error
            }
        }
    }

    fun confirmCancel(onCancelled: () -> Unit) {
        if (!confirmationFlow.value.isConfirmingDiscard) return
        confirmationFlow.value = confirmationFlow.value.copy(isConfirmingDiscard = false)
        cancelWorkout(onCancelled)
    }

    fun cancelWorkout(onCancelled: () -> Unit) {
        if (writePending() || uiState.value !is ActiveWorkoutUiState.Active) return
        confirmationFlow.update { it.copy(ending = true) }
        viewModelScope.launch {
            try {
                workoutRepository.cancelWorkout(sessionId)
                onCancelled()
            } catch (e: CancellationException) {
                confirmationFlow.update { it.copy(ending = false) }
                throw e
            } catch (e: Exception) {
                confirmationFlow.update { it.copy(ending = false) }
                errorFlow.value = R.string.workout_cancel_error
            }
        }
    }

    private fun writePending() = confirmationFlow.value.ending || writesInFlight.isNotEmpty() || outcomesAwaitingObservation.isNotEmpty()

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
            workoutHistoryAnalyzer: WorkoutHistoryAnalyzer,
            programRepository: wallcrawl.elopenmike.com.core.database.repository.ProgramRepository? = null,
            userProfileRepository: UserProfileRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ActiveWorkoutViewModel(
                    sessionId, workoutRepository, exerciseCatalog, workoutHistoryAnalyzer,
                    programRepository = programRepository, userProfileRepository = userProfileRepository,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }

    private suspend fun olderWorkingSets(
        active: WorkoutSession?, recent: List<WorkoutSession>
    ): Map<String, CompletedExercisePerformance> {
        if (active == null || active.startedAtTimestamp <= 0) return emptyMap()
        val missing = active.exercises.filter { exercise ->
            exercise.prescription.exerciseType == ExerciseType.WEIGHT_REPS && exercise.prescription.purpose == PrescriptionPurpose.ORDINARY &&
                !exercise.isLegacyMobility && workoutHistoryAnalyzer.latestCompletedExercisePerformance(recent, exercise.exerciseId) == null
        }.map { it.exerciseId }.distinct()
        return if (missing.isEmpty()) emptyMap() else workoutRepository.getLatestWorkingSets(missing, active.startedAtTimestamp - 1)
    }

    private data class SessionHistory(
        val session: WorkoutSession?,
        val completedSessions: List<WorkoutSession>,
        val summary: WorkoutSummary? = null,
        val olderWorkingSets: Map<String, CompletedExercisePerformance> = emptyMap(),
        val isLoading: Boolean = false,
        @StringRes val readError: Int? = null
    )

    private data class ConfirmationState(
        val saving: Boolean = false,
        val ending: Boolean = false,
        val pendingFinish: FinishDecision.ConfirmIncomplete? = null,
        val isConfirmingDiscard: Boolean = false,
        val pendingExclusions: List<String> = emptyList(),
        val exclusionSaving: Boolean = false,
        val exclusionSaveFailed: Boolean = false
    ) {
        val painExclusionOffer: PainExclusionOffer?
            get() = pendingExclusions.firstOrNull()?.let { PainExclusionOffer(it, exclusionSaving, exclusionSaveFailed) }

        fun withoutFirstExclusion() = copy(
            pendingExclusions = pendingExclusions.drop(1), exclusionSaving = false, exclusionSaveFailed = false
        )
    }

    private data class ScreenState(
        @StringRes val error: Int?,
        @StringRes val setUpdateError: Int?,
        val restTimer: RestTimerUiState,
        val confirmations: ConfirmationState,
        val programFinishUnsupported: Boolean
    )

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
