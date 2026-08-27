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
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator
import kotlinx.coroutines.CancellationException
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
    private val progressCalculator: ProgressCalculator = ProgressCalculator()
) : ViewModel() {

    private val currentExerciseIndexFlow = MutableStateFlow(0)
    private val currentCatalogExerciseFlow = MutableStateFlow<Exercise?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)
    private var finishRequested = false

    private val sessionHistoryFlow = combine(
        workoutRepository.observeSession(sessionId),
        workoutRepository.observeCompletedSessions(limit = MAX_PREVIOUS_PERFORMANCE_SESSIONS)
    ) { session, completedSessions ->
        SessionHistory(session, completedSessions)
    }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionHistoryFlow,
        currentExerciseIndexFlow,
        currentCatalogExerciseFlow,
        errorFlow
    ) { sessionHistory, exerciseIndex, catalogEx, error ->
        val (session, completedSessions) = sessionHistory
        if (session == null) {
            ActiveWorkoutUiState.Loading
        } else if (session.status == SessionStatus.COMPLETED) {
            ActiveWorkoutUiState.Completed(session.toSummary(completedSessions))
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
                previousWeightUnit = previousPerformance?.weightUnit ?: session.weightUnit
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
            SetPerformanceInput(reps = reps, weight = weight, isCompleted = isCompleted)
        )
    }

    fun updateSet(setId: String, performance: SetPerformanceInput) {
        viewModelScope.launch {
            try {
                workoutRepository.logSetCompletion(
                    setId = setId,
                    performance = performance
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorFlow.value = "Failed to update set: ${e.message}"
            }
        }
    }

    fun finishWorkout() {
        if (finishRequested) return
        val currentState = uiState.value as? ActiveWorkoutUiState.Active ?: return
        finishRequested = true
        viewModelScope.launch {
            try {
                val elapsedMinutes = elapsedWorkoutMinutes(
                    startedAtTimestamp = currentState.session.startedAtTimestamp,
                    nowTimestamp = System.currentTimeMillis()
                )
                workoutRepository.completeWorkout(sessionId, elapsedMinutes)
            } catch (e: CancellationException) {
                finishRequested = false
                throw e
            } catch (e: Exception) {
                finishRequested = false
                errorFlow.value = "Failed to finish workout: ${e.message}"
            }
        }
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

    private fun WorkoutSession.toSummary(completedSessions: List<WorkoutSession>) = WorkoutSummary(
        sessionId = id,
        workoutName = name,
        durationMinutes = actualDurationMinutes,
        totalSetsCompleted = completedSetsCount,
        totalVolume = totalVolume,
        prCount = progressCalculator.countPersonalRecords(
            session = this,
            priorCompletedSessions = completedSessions
        ),
        unit = weightUnit,
        completedAtTimestamp = completedAtTimestamp ?: startedAtTimestamp
    )

}

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
