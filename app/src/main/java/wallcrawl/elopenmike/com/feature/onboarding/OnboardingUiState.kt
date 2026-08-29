package wallcrawl.elopenmike.com.feature.onboarding

import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.WeightUnit

/**
 * Steps in the friendly multi-step onboarding wizard.
 */
enum class OnboardingStep(
    val title: String,
    val subtitle: String
) {
    WELCOME(
        title = "Welcome, Crawler",
        subtitle = "Let's set up your personal training identity."
    ),
    GOALS(
        title = "Fitness Goals",
        subtitle = "Select all outcomes you want to train for."
    ),
    EXPERIENCE_UNIT(
        title = "Experience & Units",
        subtitle = "Select your preferred weight unit and training background."
    ),
    SCHEDULE(
        title = "Training Schedule",
        subtitle = "Set up your weekly routine and preferred session length."
    ),
    EQUIPMENT(
        title = "Available Equipment",
        subtitle = "Select only the equipment you actually have access to."
    ),
    SAFETY(
        title = "Safety & Recovery",
        subtitle = "Tell us if any areas need conservative exercise selection."
    ),
    SUMMARY(
        title = "Ready to Begin",
        subtitle = "Review your setup before generating your first workout."
    );

    val stepNumber: Int get() = ordinal + 1

    companion object {
        val totalSteps: Int get() = entries.size
    }
}

/**
 * The current onboarding form values plus wizard step, error, and completion state.
 */
data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val name: String = "",
    val goals: Set<FitnessGoal> = setOf(FitnessGoal.BUILD_MUSCLE),
    val experience: ExperienceLevel = ExperienceLevel.BEGINNER,
    val daysPerWeek: Int = 3,
    val durationMinutes: Int = 45,
    val unit: WeightUnit = WeightUnit.LBS,
    val equipment: Set<String> = setOf(StandardEquipment.BODYWEIGHT),
    val constraints: Set<TrainingConstraint> = emptySet(),
    val returningAfterBreakWeeks: Int = 0,
    val equipmentOptions: List<String> = StandardEquipment.ALL,
    val constraintOptions: List<TrainingConstraint> = TrainingConstraint.entries,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false
) {
    val goal: FitnessGoal get() = goals.firstOrNull() ?: FitnessGoal.BUILD_MUSCLE

    val canProceedCurrentStep: Boolean
        get() = when (currentStep) {
            OnboardingStep.WELCOME -> name.trim().isNotBlank()
            OnboardingStep.GOALS -> goals.isNotEmpty()
            OnboardingStep.EXPERIENCE_UNIT -> true
            OnboardingStep.SCHEDULE -> daysPerWeek in 2..6 && durationMinutes in 20..120
            OnboardingStep.EQUIPMENT -> equipment.isNotEmpty()
            OnboardingStep.SAFETY -> true
            OnboardingStep.SUMMARY -> !isSaving && name.trim().isNotBlank() && equipment.isNotEmpty() && goals.isNotEmpty()
        }

    val isFirstStep: Boolean get() = currentStep.ordinal == 0
    val isLastStep: Boolean get() = currentStep.ordinal == OnboardingStep.entries.lastIndex
}
