package wallcrawl.elopenmike.com.feature.onboarding

import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.WeightUnit

/**
 * The current onboarding form values plus the load/error/completion state for the screen.
 */
data class OnboardingUiState(
    val name: String = "",
    val goal: FitnessGoal = FitnessGoal.GENERAL_FITNESS,
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
)
