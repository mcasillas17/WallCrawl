package wallcrawl.elopenmike.com.feature.personalize

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ConditionSupportRecord
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.SelectedCondition
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingError

enum class PersonalizeStep(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int
) {
    EQUIPMENT(R.string.onboarding_equipment_title, R.string.onboarding_equipment_subtitle),
    EXPERIENCE_MOVEMENT(R.string.personalize_experience_title, R.string.onboarding_movement_capability_subtitle),
    CONDITIONS(R.string.onboarding_safety_title, R.string.onboarding_safety_subtitle);

    val stepNumber: Int get() = ordinal + 1
}

/**
 * The personalization draft. Nothing in it reaches the profile until
 * [PersonalizeViewModel.finish] saves it in one write.
 */
data class PersonalizeUiState(
    val currentStep: PersonalizeStep = PersonalizeStep.EQUIPMENT,
    /** False until the draft has been copied from the profile (or restored from saved state). */
    val isLoaded: Boolean = false,
    val conditionDraft: List<SelectedCondition>? = null,
    val retainedConditions: List<SelectedCondition> = emptyList(),
    val conditionRecords: List<ConditionSupportRecord> = emptyList(),
    val conditionLoadFailed: Boolean = false,
    val retainedReports: Set<String> = emptySet(),
    val addedReports: Set<String> = emptySet(),
    val removedReports: Set<String> = emptySet(),
    val experience: ExperienceLevel = ExperienceLevel.BEGINNER,
    val equipment: Set<String> = setOf(StandardEquipment.BODYWEIGHT),
    val constraints: Set<TrainingConstraint> = emptySet(),
    val capabilityAnswers: Map<MovementCapabilityType, CapabilityLevel> = emptyMap(),
    val returningAfterBreakWeeks: Int = 0,
    val equipmentOptions: List<String> = StandardEquipment.ALL,
    val constraintOptions: List<TrainingConstraint> = TrainingConstraint.entries,
    val isSaving: Boolean = false,
    val error: OnboardingError? = null,
    val isComplete: Boolean = false
) {
    val reportedConditions: Set<String> get() = applyReportEdits(retainedReports)

    fun applyReportEdits(saved: Set<String>): Set<String> = (saved + addedReports) - removedReports

    val unansweredCapability: MovementCapabilityType?
        get() = MovementCapabilityType.entries.firstOrNull { it !in capabilityAnswers }

    val canProceedCurrentStep: Boolean
        get() = when (currentStep) {
            PersonalizeStep.EQUIPMENT -> equipment.isNotEmpty()
            PersonalizeStep.EXPERIENCE_MOVEMENT -> unansweredCapability == null
            PersonalizeStep.CONDITIONS -> !isSaving
        }

    val isLastStep: Boolean get() = currentStep.ordinal == PersonalizeStep.entries.lastIndex
}
