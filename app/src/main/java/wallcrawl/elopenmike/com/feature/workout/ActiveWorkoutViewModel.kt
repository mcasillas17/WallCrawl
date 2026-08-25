package wallcrawl.elopenmike.com.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActiveWorkoutViewModel(
    private val sessionId: String,
    private val workoutRepository: WorkoutRepository,
    private val userProfileRepository: UserProfileRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val workoutHistoryAnalyzer: WorkoutHistoryAnalyzer
) : ViewModel() {

    private val currentExerciseIndexFlow = MutableStateFlow(0)
    private val currentCatalogExerciseFlow = MutableStateFlow<Exercise?>(null)
    private val completedSummaryFlow = MutableStateFlow<WorkoutSummary?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)

    // Keep the active session, profile preferences, and completed history synchronized.
    private val sessionAndProfileFlow = combine(
        workoutRepository.observeSession(sessionId),
        userProfileRepository.getUserProfile(),
        workoutRepository.observeCompletedSessions()
    ) { session, profile, completedSessions ->
        SessionProfileHistory(session, profile, completedSessions)
    }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionAndProfileFlow,
        currentExerciseIndexFlow,
        currentCatalogExerciseFlow,
        completedSummaryFlow,
        errorFlow
    ) { sessionProfileHistory, exerciseIndex, catalogEx, summary, error ->
        val (session, profile, completedSessions) = sessionProfileHistory
        if (error != null) {
            ActiveWorkoutUiState.Error(error)
        } else if (summary != null) {
            ActiveWorkoutUiState.Completed(summary)
        } else if (session == null) {
            ActiveWorkoutUiState.Loading
        } else {
            val currentEx = session.exercises.getOrNull(exerciseIndex)
            if (currentEx != null && catalogEx?.id != currentEx.exerciseId) {
                loadCatalogExercise(currentEx.exerciseId)
            }

            val previousPerformance = currentEx?.let { exercise ->
                workoutHistoryAnalyzer.latestCompletedExercisePerformance(
                    sessions = completedSessions,
                    exerciseId = exercise.exerciseId
                )
            }

            ActiveWorkoutUiState.Active(
                session = session,
                currentExerciseIndex = exerciseIndex,
                currentCatalogExercise = catalogEx,
                preferredUnit = profile.preferredUnit,
                previousSets = previousPerformance?.sets.orEmpty(),
                previousSessionTimestamp = previousPerformance?.sessionCompletedAtTimestamp
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActiveWorkoutUiState.Loading
    )

    private fun loadCatalogExercise(exerciseId: String) {
        viewModelScope.launch {
            val ex = exerciseCatalog.getExerciseById(exerciseId)
            currentCatalogExerciseFlow.value = ex
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
        viewModelScope.launch {
            try {
                workoutRepository.logSetCompletion(
                    setId = setId,
                    reps = reps,
                    weight = weight,
                    isCompleted = isCompleted
                )
            } catch (e: Exception) {
                errorFlow.value = "Failed to update set: ${e.message}"
            }
        }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            val currentState = uiState.value as? ActiveWorkoutUiState.Active ?: return@launch
            try {
                val elapsedMinutes = elapsedWorkoutMinutes(
                    startedAtTimestamp = currentState.session.startedAtTimestamp,
                    nowTimestamp = System.currentTimeMillis()
                )
                val summary = workoutRepository.completeWorkout(sessionId, elapsedMinutes)
                completedSummaryFlow.value = summary
            } catch (e: Exception) {
                errorFlow.value = "Failed to finish workout: ${e.message}"
            }
        }
    }

    fun cancelWorkout(onCancelled: () -> Unit) {
        viewModelScope.launch {
            try {
                workoutRepository.cancelWorkout(sessionId)
                onCancelled()
            } catch (e: Exception) {
                errorFlow.value = "Failed to cancel workout: ${e.message}"
            }
        }
    }

    companion object {
        fun provideFactory(
            sessionId: String,
            workoutRepository: WorkoutRepository,
            userProfileRepository: UserProfileRepository,
            exerciseCatalog: ExerciseCatalog,
            workoutHistoryAnalyzer: WorkoutHistoryAnalyzer
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ActiveWorkoutViewModel(
                    sessionId,
                    workoutRepository,
                    userProfileRepository,
                    exerciseCatalog,
                    workoutHistoryAnalyzer
                ) as T
            }
        }
    }

    private data class SessionProfileHistory(
        val session: WorkoutSession?,
        val profile: wallcrawl.elopenmike.com.core.model.UserProfile,
        val completedSessions: List<WorkoutSession>
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
