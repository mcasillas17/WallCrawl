package wallcrawl.elopenmike.com.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ProgressViewModel(
    private val workoutRepository: WorkoutRepository,
    private val userProfileRepository: UserProfileRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val progressCalculator: ProgressCalculator,
    private val nowTimestamp: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = combine(
        userProfileRepository.getUserProfile(),
        workoutRepository.observeCompletedSessions(limit = MAX_ANALYTICS_SESSIONS),
        workoutRepository.observeCompletedWorkoutCount(),
        exerciseCatalog.getAllExercises()
    ) { profile, completedSessions, totalCompletedCount, exercises ->
        val state: ProgressUiState = ProgressUiState.Success(
            overview = progressCalculator.calculate(
                completedSessions = completedSessions,
                profile = profile,
                catalogExercises = exercises,
                nowTimestamp = nowTimestamp()
            ).copy(totalWorkoutsLogged = totalCompletedCount),
            preferredUnit = profile.preferredUnit
        )
        state
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(
            ProgressUiState.Error(
                "Progress could not be loaded because the offline exercise catalog or workout data is unavailable."
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProgressUiState.Loading
    )

    companion object {
        private const val MAX_ANALYTICS_SESSIONS = 500

        fun provideFactory(
            workoutRepository: WorkoutRepository,
            userProfileRepository: UserProfileRepository,
            exerciseCatalog: ExerciseCatalog,
            progressCalculator: ProgressCalculator
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProgressViewModel(
                    workoutRepository = workoutRepository,
                    userProfileRepository = userProfileRepository,
                    exerciseCatalog = exerciseCatalog,
                    progressCalculator = progressCalculator
                ) as T
            }
        }
    }
}
