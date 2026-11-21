package wallcrawl.elopenmike.com.feature.onboarding

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.ProfileGender

/**
 * Steps in the friendly multi-step onboarding wizard.
 */
enum class OnboardingStep(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int
) {
    WELCOME(
        titleRes = R.string.onboarding_welcome_title,
        subtitleRes = R.string.onboarding_welcome_subtitle
    ),
    GOALS(
        titleRes = R.string.onboarding_goals_title,
        subtitleRes = R.string.onboarding_goals_subtitle
    ),
    EXPERIENCE_UNIT(
        titleRes = R.string.onboarding_experience_title,
        subtitleRes = R.string.onboarding_experience_subtitle
    ),
    SCHEDULE(
        titleRes = R.string.onboarding_schedule_title,
        subtitleRes = R.string.onboarding_schedule_subtitle
    ),
    SUMMARY(
        titleRes = R.string.onboarding_summary_title,
        subtitleRes = R.string.onboarding_summary_subtitle
    );

    val stepNumber: Int get() = ordinal + 1

    companion object {
        val totalSteps: Int get() = entries.size
    }
}

enum class OnboardingError(@StringRes val messageRes: Int) {
    REQUIRED_FIELD(R.string.onboarding_error_required_field),
    MOVEMENT_REQUIRED(R.string.onboarding_error_movement_required),
    INVALID_FIELD(R.string.onboarding_error_invalid_field),
    SAVE_FAILED(R.string.onboarding_error_save_failed)
}

/**
 * The current onboarding form values plus wizard step, error, and completion state.
 */
data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val name: String = "",
    val gender: ProfileGender = ProfileGender.UNSPECIFIED,
    val goals: Set<FitnessGoal> = setOf(FitnessGoal.BUILD_MUSCLE),
    val experience: ExperienceLevel = ExperienceLevel.BEGINNER,
    val daysPerWeek: Int = 3,
    val durationMinutes: Int = 45,
    val unit: WeightUnit = WeightUnit.LBS,
    val isSaving: Boolean = false,
    val error: OnboardingError? = null,
    val isComplete: Boolean = false
) {
    val goal: FitnessGoal get() = goals.firstOrNull() ?: FitnessGoal.BUILD_MUSCLE

    val canProceedCurrentStep: Boolean
        get() = when (currentStep) {
            OnboardingStep.WELCOME -> name.trim().isNotBlank()
            OnboardingStep.GOALS -> goals.isNotEmpty()
            OnboardingStep.EXPERIENCE_UNIT -> true
            OnboardingStep.SCHEDULE -> daysPerWeek in 2..6 && durationMinutes in 20..120
            OnboardingStep.SUMMARY -> !isSaving && name.trim().isNotBlank() && goals.isNotEmpty()
        }

    val isFirstStep: Boolean get() = currentStep.ordinal == 0
    val isLastStep: Boolean get() = currentStep.ordinal == OnboardingStep.entries.lastIndex
}
