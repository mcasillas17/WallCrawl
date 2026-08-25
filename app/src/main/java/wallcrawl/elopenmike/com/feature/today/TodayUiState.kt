package wallcrawl.elopenmike.com.feature.today

import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutSession

sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Success(
        val userProfile: UserProfile,
        val suggestedWorkout: GeneratedWorkout,
        val activeSession: WorkoutSession? = null,
        val isRegenerating: Boolean = false,
        val completedThisWeek: Int = 3
    ) : TodayUiState

    data class Error(val message: String) : TodayUiState
}
