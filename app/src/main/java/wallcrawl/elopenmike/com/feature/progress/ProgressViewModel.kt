package wallcrawl.elopenmike.com.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        workoutRepository.observeCompletedSessions(),
        exerciseCatalog.getAllExercises()
    ) { profile, completedSessions, exercises ->
        ProgressUiState.Success(
            overview = progressCalculator.calculate(
                completedSessions = completedSessions,
                profile = profile,
                catalogExercises = exercises,
                nowTimestamp = nowTimestamp()
            ),
            preferredUnit = profile.preferredUnit
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProgressUiState.Loading
    )

    companion object {
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
