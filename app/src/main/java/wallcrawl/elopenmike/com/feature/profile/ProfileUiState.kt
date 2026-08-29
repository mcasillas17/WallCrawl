package wallcrawl.elopenmike.com.feature.profile

import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Success(
        val profile: UserProfile,
        val isSaving: Boolean = false,
        val availableEquipmentOptions: List<String> = emptyList(),
        val availableMuscleOptions: List<String> = emptyList(),
        val availableConstraintOptions: List<TrainingConstraint> = TrainingConstraint.entries
    ) : ProfileUiState

    data class Error(val message: String) : ProfileUiState
}
