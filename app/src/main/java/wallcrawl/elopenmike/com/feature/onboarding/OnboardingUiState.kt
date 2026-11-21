package wallcrawl.elopenmike.com.feature.onboarding

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.FitnessGoal

enum class OnboardingError(@StringRes val messageRes: Int) {
    REQUIRED_FIELD(R.string.onboarding_error_required_field),
    MOVEMENT_REQUIRED(R.string.onboarding_error_movement_required),
    INVALID_FIELD(R.string.onboarding_error_invalid_field),
    SAVE_FAILED(R.string.onboarding_error_save_failed)
}

/** The basic-setup answers plus save and completion state. */
data class OnboardingUiState(
    val goal: FitnessGoal? = null,
    val daysPerWeek: Int = 3,
    val durationMinutes: Int = 30,
    val isSaving: Boolean = false,
    val error: OnboardingError? = null,
    val isComplete: Boolean = false
) {
    val canSave: Boolean get() = goal != null && !isSaving
}
