package wallcrawl.elopenmike.com.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ProgressViewModel(
    private val workoutRepository: WorkoutRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = combine(
        userProfileRepository.getUserProfile(),
        workoutRepository.observeProgressOverview()
    ) { profile, overview ->
        ProgressUiState.Success(
            overview = overview,
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
            userProfileRepository: UserProfileRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProgressViewModel(workoutRepository, userProfileRepository) as T
            }
        }
    }
}
