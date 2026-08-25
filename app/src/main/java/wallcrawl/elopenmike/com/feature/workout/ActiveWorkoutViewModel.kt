package wallcrawl.elopenmike.com.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.WeightUnit
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
    private val exerciseCatalog: ExerciseCatalog
) : ViewModel() {

    private val currentExerciseIndexFlow = MutableStateFlow(0)
    private val currentCatalogExerciseFlow = MutableStateFlow<Exercise?>(null)
    private val completedSummaryFlow = MutableStateFlow<WorkoutSummary?>(null)
    private val errorFlow = MutableStateFlow<String?>(null)

    // Combine session and profile first
    private val sessionAndProfileFlow = combine(
        workoutRepository.observeSession(sessionId),
        userProfileRepository.getUserProfile()
    ) { session, profile ->
        Pair(session, profile)
    }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        sessionAndProfileFlow,
        currentExerciseIndexFlow,
        currentCatalogExerciseFlow,
        completedSummaryFlow,
        errorFlow
    ) { (session, profile), exerciseIndex, catalogEx, summary, error ->
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

            val previousHistory = getPreviousHistoryForExercise(currentEx?.exerciseId, profile.preferredUnit)

            ActiveWorkoutUiState.Active(
                session = session,
                currentExerciseIndex = exerciseIndex,
                currentCatalogExercise = catalogEx,
                preferredUnit = profile.preferredUnit,
                previousHistory = previousHistory
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

    private fun getPreviousHistoryForExercise(exerciseId: String?, unit: WeightUnit): List<String> {
        return when (exerciseId) {
            "incline-dumbbell-press" -> listOf(
                "45 ${unit.symbol} × 10",
                "45 ${unit.symbol} × 9",
                "45 ${unit.symbol} × 8"
            )
            "barbell-bench-press" -> listOf(
                "135 ${unit.symbol} × 8",
                "135 ${unit.symbol} × 7",
                "135 ${unit.symbol} × 6"
            )
            "pull-ups" -> listOf(
                "Bodyweight × 8",
                "Bodyweight × 7",
                "Bodyweight × 6"
            )
            "barbell-back-squat" -> listOf(
                "185 ${unit.symbol} × 8",
                "185 ${unit.symbol} × 8",
                "185 ${unit.symbol} × 6"
            )
            else -> listOf(
                "Last session: 3 sets completed",
                "Targeting progressive overload"
            )
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
                val elapsedMinutes = ((System.currentTimeMillis() - currentState.session.startedAtTimestamp) / (60 * 1000)).toInt()
                    .coerceAtLeast(currentState.session.targetDurationMinutes)
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
            exerciseCatalog: ExerciseCatalog
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ActiveWorkoutViewModel(
                    sessionId,
                    workoutRepository,
                    userProfileRepository,
                    exerciseCatalog
                ) as T
            }
        }
    }
}
