package wallcrawl.elopenmike.com.core.ui.localization

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.locale.AppLanguage
import wallcrawl.elopenmike.com.core.model.BreakGuidance
import wallcrawl.elopenmike.com.core.model.BreakRange
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutEmphasis
import wallcrawl.elopenmike.com.core.model.WorkoutSplit

/**
 * The one place a domain value is turned into something a person reads.
 *
 * Every mapping is an exhaustive `when` over an enum, so adding a case to the domain fails
 * the build here rather than shipping an untranslated screen. Nothing in this file is
 * consulted by planning, eligibility, persistence, or the archive: the enum name remains
 * the identifier everywhere else.
 */

@get:StringRes
val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.language_option_system
        AppLanguage.ENGLISH -> R.string.language_option_english
        AppLanguage.SPANISH -> R.string.language_option_spanish
    }

/**
 * The label for a control too small for a language's full name: a two-letter code, or
 * "Auto" for following the device, matching how the theme control abbreviates itself.
 */
@get:StringRes
val AppLanguage.shortLabelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.language_option_system_short
        AppLanguage.ENGLISH -> R.string.language_option_english_short
        AppLanguage.SPANISH -> R.string.language_option_spanish_short
    }

@get:StringRes
val FitnessGoal.labelRes: Int
    get() = when (this) {
        FitnessGoal.BUILD_MUSCLE -> R.string.goal_build_muscle_label
        FitnessGoal.STRENGTH -> R.string.goal_strength_label
        FitnessGoal.GENERAL_FITNESS -> R.string.goal_general_fitness_label
        FitnessGoal.FAT_LOSS -> R.string.goal_fat_loss_label
        FitnessGoal.ATHLETIC_PERFORMANCE -> R.string.goal_athletic_performance_label
    }

@get:StringRes
val FitnessGoal.descriptionRes: Int
    get() = when (this) {
        FitnessGoal.BUILD_MUSCLE -> R.string.goal_build_muscle_description
        FitnessGoal.STRENGTH -> R.string.goal_strength_description
        FitnessGoal.GENERAL_FITNESS -> R.string.goal_general_fitness_description
        FitnessGoal.FAT_LOSS -> R.string.goal_fat_loss_description
        FitnessGoal.ATHLETIC_PERFORMANCE -> R.string.goal_athletic_performance_description
    }

@get:StringRes
val ExperienceLevel.labelRes: Int
    get() = when (this) {
        ExperienceLevel.BEGINNER -> R.string.experience_beginner_label
        ExperienceLevel.INTERMEDIATE -> R.string.experience_intermediate_label
        ExperienceLevel.ADVANCED -> R.string.experience_advanced_label
    }

@get:StringRes
val ExperienceLevel.descriptionRes: Int
    get() = when (this) {
        ExperienceLevel.BEGINNER -> R.string.experience_beginner_description
        ExperienceLevel.INTERMEDIATE -> R.string.experience_intermediate_description
        ExperienceLevel.ADVANCED -> R.string.experience_advanced_description
    }

@get:StringRes
val TrainingConstraint.labelRes: Int
    get() = when (this) {
        TrainingConstraint.SHOULDER_SENSITIVE -> R.string.constraint_shoulder_label
        TrainingConstraint.ELBOW_SENSITIVE -> R.string.constraint_elbow_label
        TrainingConstraint.WRIST_SENSITIVE -> R.string.constraint_wrist_label
        TrainingConstraint.LOWER_BACK_SENSITIVE -> R.string.constraint_lower_back_label
        TrainingConstraint.HIP_SENSITIVE -> R.string.constraint_hip_label
        TrainingConstraint.KNEE_SENSITIVE -> R.string.constraint_knee_label
        TrainingConstraint.LOW_IMPACT_ONLY -> R.string.constraint_low_impact_label
    }

@get:StringRes
val PriorityLevel.labelRes: Int
    get() = when (this) {
        PriorityLevel.LOW -> R.string.priority_low
        PriorityLevel.NORMAL -> R.string.priority_normal
        PriorityLevel.HIGH -> R.string.priority_high
    }

@get:StringRes
val ThemePreference.labelRes: Int
    get() = when (this) {
        ThemePreference.SYSTEM -> R.string.theme_system_label
        ThemePreference.DARK -> R.string.theme_dark_label
        ThemePreference.LIGHT -> R.string.theme_light_label
    }

@get:StringRes
val ThemePreference.shortLabelRes: Int
    get() = when (this) {
        ThemePreference.SYSTEM -> R.string.theme_system_short
        ThemePreference.DARK -> R.string.theme_dark_short
        ThemePreference.LIGHT -> R.string.theme_light_short
    }

@get:StringRes
val ThemePreference.descriptionRes: Int
    get() = when (this) {
        ThemePreference.SYSTEM -> R.string.theme_system_description
        ThemePreference.DARK -> R.string.theme_dark_description
        ThemePreference.LIGHT -> R.string.theme_light_description
    }

@get:StringRes
val WeightUnit.shortLabelRes: Int
    get() = when (this) {
        WeightUnit.LBS -> R.string.weight_unit_lbs_short
        WeightUnit.KG -> R.string.weight_unit_kg_short
    }

@get:StringRes
val WeightUnit.longLabelRes: Int
    get() = when (this) {
        WeightUnit.LBS -> R.string.weight_unit_lbs_long
        WeightUnit.KG -> R.string.weight_unit_kg_long
    }

@get:StringRes
val SetStopReason.labelRes: Int
    get() = when (this) {
        SetStopReason.USER_SKIPPED -> R.string.stop_reason_user_skipped
        // Says only that the user chose to stop. Never phrased as a symptom, an injury,
        // or advice, in any language.
        SetStopReason.PAIN_STOP -> R.string.stop_reason_pain
        SetStopReason.EQUIPMENT_UNAVAILABLE -> R.string.stop_reason_equipment
        SetStopReason.TIME_CONSTRAINT -> R.string.stop_reason_time
        SetStopReason.OTHER -> R.string.stop_reason_other
    }

@get:StringRes
val MechanicsType.labelRes: Int
    get() = when (this) {
        MechanicsType.COMPOUND -> R.string.mechanics_compound
        MechanicsType.ISOLATION -> R.string.mechanics_isolation
    }

@get:StringRes
val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.BEGINNER -> R.string.difficulty_beginner
        Difficulty.INTERMEDIATE -> R.string.difficulty_intermediate
        Difficulty.ADVANCED -> R.string.difficulty_advanced
    }

@get:StringRes
val MovementPattern.labelRes: Int
    get() = when (this) {
        MovementPattern.HORIZONTAL_PUSH -> R.string.movement_pattern_horizontal_push
        MovementPattern.VERTICAL_PUSH -> R.string.movement_pattern_vertical_push
        MovementPattern.HORIZONTAL_PULL -> R.string.movement_pattern_horizontal_pull
        MovementPattern.VERTICAL_PULL -> R.string.movement_pattern_vertical_pull
        MovementPattern.SQUAT -> R.string.movement_pattern_squat
        MovementPattern.HINGE -> R.string.movement_pattern_hinge
        MovementPattern.LUNGE -> R.string.movement_pattern_lunge
        MovementPattern.ISOLATION -> R.string.movement_pattern_isolation
        MovementPattern.CARRY -> R.string.movement_pattern_carry
        MovementPattern.CORE -> R.string.movement_pattern_core
        MovementPattern.OTHER -> R.string.movement_pattern_other
    }

@get:StringRes
val WorkoutSplit.labelRes: Int
    get() = when (this) {
        WorkoutSplit.PUSH -> R.string.split_push
        WorkoutSplit.PULL -> R.string.split_pull
        WorkoutSplit.LEGS -> R.string.split_legs
        WorkoutSplit.UPPER_BODY -> R.string.split_upper_body
        WorkoutSplit.FULL_BODY -> R.string.split_full_body
    }

@get:StringRes
val WorkoutEmphasis.labelRes: Int
    get() = when (this) {
        WorkoutEmphasis.POWER_AND_HYPERTROPHY -> R.string.emphasis_power_hypertrophy
        WorkoutEmphasis.POWER_AND_PERFORMANCE -> R.string.emphasis_power_performance
        WorkoutEmphasis.ATHLETIC_CONDITIONING -> R.string.emphasis_athletic_conditioning
        WorkoutEmphasis.HYPERTROPHY_AND_DEFINITION -> R.string.emphasis_hypertrophy_definition
        WorkoutEmphasis.HYPERTROPHY -> R.string.emphasis_hypertrophy
        WorkoutEmphasis.POWER_AND_STRENGTH -> R.string.emphasis_power_strength
        WorkoutEmphasis.AGILITY_AND_EXPLOSIVENESS -> R.string.emphasis_agility_explosiveness
        WorkoutEmphasis.HIGH_DENSITY_CIRCUIT -> R.string.emphasis_high_density_circuit
        WorkoutEmphasis.ATHLETIC_FOUNDATION -> R.string.emphasis_athletic_foundation
        WorkoutEmphasis.CONDITIONING -> R.string.emphasis_conditioning
    }

@get:StringRes
val BreakRange.titleRes: Int
    get() = when (this) {
        BreakRange.NONE -> R.string.break_range_none_title
        BreakRange.SHORT -> R.string.break_range_short_title
        BreakRange.MODERATE -> R.string.break_range_moderate_title
        BreakRange.EXTENDED -> R.string.break_range_extended_title
        BreakRange.LONG -> R.string.break_range_long_title
        BreakRange.HIATUS -> R.string.break_range_hiatus_title
        BreakRange.MULTI_YEAR -> R.string.break_range_multi_year_title
    }

@get:StringRes
val BreakRange.subtitleRes: Int
    get() = when (this) {
        BreakRange.NONE -> R.string.break_range_none_subtitle
        BreakRange.SHORT -> R.string.break_range_short_subtitle
        BreakRange.MODERATE -> R.string.break_range_moderate_subtitle
        BreakRange.EXTENDED -> R.string.break_range_extended_subtitle
        BreakRange.LONG -> R.string.break_range_long_subtitle
        BreakRange.HIATUS -> R.string.break_range_hiatus_subtitle
        BreakRange.MULTI_YEAR -> R.string.break_range_multi_year_subtitle
    }

@get:StringRes
val BreakGuidance.messageRes: Int
    get() = when (this) {
        BreakGuidance.NONE -> R.string.break_guidance_none
        BreakGuidance.SHORT -> R.string.break_guidance_short
        BreakGuidance.MODERATE -> R.string.break_guidance_moderate
        BreakGuidance.LONG -> R.string.break_guidance_long
        BreakGuidance.HIATUS -> R.string.break_guidance_hiatus
    }

@get:StringRes
val MovementCapabilityType.labelRes: Int
    get() = when (this) {
        MovementCapabilityType.IMPACT -> R.string.movement_capability_impact_label
        MovementCapabilityType.FLOOR_TRANSITION -> R.string.movement_capability_floor_transition_label
        MovementCapabilityType.UNSUPPORTED_SQUAT -> R.string.movement_capability_unsupported_squat_label
        MovementCapabilityType.UPPER_BODY_BODYWEIGHT_PUSH ->
            R.string.movement_capability_upper_body_push_label
        MovementCapabilityType.VERTICAL_PULL_OR_HANG -> R.string.movement_capability_vertical_pull_label
        MovementCapabilityType.BALANCE_WITHOUT_SUPPORT -> R.string.movement_capability_balance_label
        MovementCapabilityType.CONTINUOUS_ACTIVITY ->
            R.string.movement_capability_continuous_activity_label
    }

@get:StringRes
val MovementCapabilityType.descriptionRes: Int
    get() = when (this) {
        MovementCapabilityType.IMPACT -> R.string.movement_capability_impact_description
        MovementCapabilityType.FLOOR_TRANSITION ->
            R.string.movement_capability_floor_transition_description
        MovementCapabilityType.UNSUPPORTED_SQUAT ->
            R.string.movement_capability_unsupported_squat_description
        MovementCapabilityType.UPPER_BODY_BODYWEIGHT_PUSH ->
            R.string.movement_capability_upper_body_push_description
        MovementCapabilityType.VERTICAL_PULL_OR_HANG ->
            R.string.movement_capability_vertical_pull_description
        MovementCapabilityType.BALANCE_WITHOUT_SUPPORT ->
            R.string.movement_capability_balance_description
        MovementCapabilityType.CONTINUOUS_ACTIVITY ->
            R.string.movement_capability_continuous_activity_description
    }

@get:StringRes
val CapabilityLevel.labelRes: Int
    get() = when (this) {
        CapabilityLevel.UNKNOWN -> R.string.capability_level_unknown
        CapabilityLevel.COMFORTABLE -> R.string.capability_level_comfortable
        CapabilityLevel.LIMITED -> R.string.capability_level_limited
        CapabilityLevel.AVOID -> R.string.capability_level_avoid
    }

/** Copy for the split description under the training-days selector. */
@StringRes
fun trainingDaysDescriptionRes(daysPerWeek: Int): Int = when (daysPerWeek) {
    2 -> R.string.schedule_days_2
    3 -> R.string.schedule_days_3
    4 -> R.string.schedule_days_4
    5 -> R.string.schedule_days_5
    else -> R.string.schedule_days_6
}
