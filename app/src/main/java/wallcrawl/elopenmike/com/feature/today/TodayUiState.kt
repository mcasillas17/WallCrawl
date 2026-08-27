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
        val completedThisWeek: Int = 0
    ) : TodayUiState

    /**
     * Generation failed. [activeSession] rides along because the Today banner is the only
     * route back into a workout already in progress — losing it here would strand a
     * half-logged session behind an error card.
     */
    data class Error(
        val message: String,
        val activeSession: WorkoutSession? = null
    ) : TodayUiState
}
