package wallcrawl.elopenmike.com.feature.profile

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Success(
        val profile: UserProfile,
        val isSaving: Boolean = false,
        val movementCapabilityDraft: MovementCapabilities? = null,
        val movementCapabilityError: ProfileCapabilityError? = null,
        val availableEquipmentOptions: List<String> = emptyList(),
        val availableMuscleOptions: List<String> = emptyList(),
        val availableConstraintOptions: List<TrainingConstraint> = TrainingConstraint.entries
    ) : ProfileUiState

    data class Error(val message: String) : ProfileUiState
}

enum class ProfileCapabilityError(@StringRes val messageRes: Int) {
    INVALID(R.string.profile_capability_invalid_error),
    SAVE_FAILED(R.string.profile_capability_save_error)
}
