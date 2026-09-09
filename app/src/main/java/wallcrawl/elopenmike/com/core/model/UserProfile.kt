package wallcrawl.elopenmike.com.core.model

/**
 * User Profile and training preferences for WallCrawl.
 */
data class UserProfile(
    val id: String = DEFAULT_PROFILE_ID,
    val revision: Long = 0,
    val name: String = "Crawler",
    val goals: Set<FitnessGoal> = setOf(FitnessGoal.BUILD_MUSCLE),
    val experienceLevel: ExperienceLevel = ExperienceLevel.INTERMEDIATE,
    val preferredDurationMinutes: Int = 50,
    val daysPerWeek: Int = 4,
    // A fresh profile assumes nothing about the user's gym access: only bodyweight
    // training is safe to assume without onboarding confirming what equipment exists.
    val availableEquipment: List<String> = listOf(StandardEquipment.BODYWEIGHT),
    val preferredUnit: WeightUnit = WeightUnit.LBS,
    val musclePriorities: Map<String, PriorityLevel> = mapOf(
        StandardMuscles.CHEST to PriorityLevel.HIGH,
        StandardMuscles.SHOULDERS to PriorityLevel.HIGH,
        StandardMuscles.BACK to PriorityLevel.NORMAL,
        StandardMuscles.TRICEPS to PriorityLevel.NORMAL,
        StandardMuscles.BICEPS to PriorityLevel.NORMAL,
        StandardMuscles.QUADS to PriorityLevel.NORMAL,
        StandardMuscles.HAMSTRINGS to PriorityLevel.NORMAL,
        StandardMuscles.GLUTES to PriorityLevel.NORMAL,
        StandardMuscles.CORE to PriorityLevel.NORMAL
    ),
    val excludedExerciseIds: List<String> = emptyList(),
    // Must stay false until onboarding explicitly confirms the fields above and below it;
    // Today must not generate or render a workout for an unconfirmed profile.
    val onboardingCompleted: Boolean = false,
    val trainingConstraints: Set<TrainingConstraint> = emptySet(),
    val returningAfterBreakWeeks: Int = 0,
    // Never populated with a guessed or catalog default: a load only lands here once a
    // user has explicitly confirmed it (see Task 2), so this starts and stays empty.
    val confirmedStartingLoads: Map<String, Double> = emptyMap(),
    val movementCapabilities: MovementCapabilities = MovementCapabilities.unknown(),
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val gender: ProfileGender = ProfileGender.UNSPECIFIED,
    // Compatibility data from the first integration preview; artwork now reads gender only.
    val illustrationPreference: IllustrationPreference = IllustrationPreference.AUTOMATIC
) {
    val primaryGoal: FitnessGoal get() = goals.firstOrNull() ?: FitnessGoal.BUILD_MUSCLE

    companion object {
        const val DEFAULT_PROFILE_ID = "default_user"
    }
}

enum class ThemePreference {
    SYSTEM,
    DARK,
    LIGHT
}

enum class FitnessGoal {
    BUILD_MUSCLE,
    STRENGTH,
    GENERAL_FITNESS,
    FAT_LOSS,
    ATHLETIC_PERFORMANCE
}

enum class ExperienceLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

/**
 * The unit a profile logs and reads loads in.
 *
 * [symbol] is the SI-style abbreviation, which is written the same way in every language
 * WallCrawl ships, so it is domain data rather than a translated label. Switching language
 * never changes the unit or the stored value.
 */
enum class WeightUnit(val symbol: String) {
    LBS("lb"),
    KG("kg")
}

fun convertWeight(value: Double, from: WeightUnit, to: WeightUnit): Double {
    require(value.isFinite() && value >= 0.0) { "Weight must be finite and not negative." }
    if (from == to) return value
    return when (from) {
        WeightUnit.LBS -> value * KILOGRAMS_PER_POUND
        WeightUnit.KG -> value / KILOGRAMS_PER_POUND
    }
}

private const val KILOGRAMS_PER_POUND = 0.45359237

enum class PriorityLevel(val multiplier: Float) {
    LOW(0.6f),
    NORMAL(1.0f),
    HIGH(1.4f)
}

/**
 * How long a returning user has been away, as a bounded set of ranges.
 *
 * The ranges are identity, not copy: [weeks] is what the planner reads, and the title,
 * subtitle, and guidance that describe each one are string resources chosen by
 * `wallcrawl.elopenmike.com.core.ui.localization.DomainLabels`. That keeps the volume
 * decision identical in every language.
 */
enum class BreakRange(val weeks: Int) {
    NONE(0),
    SHORT(4),
    MODERATE(12),
    EXTENDED(26),
    LONG(52),
    HIATUS(104),
    MULTI_YEAR(156)
}

/** The wording tier used to explain what a break means for the coming plan. */
enum class BreakGuidance {
    NONE,
    SHORT,
    MODERATE,
    LONG,
    HIATUS
}

object BreakDurationHelper {
    val RANGES: List<BreakRange> = BreakRange.entries

    fun findMatchingRange(weeks: Int): BreakRange = when {
        weeks <= 0 -> BreakRange.NONE
        weeks in 1..8 -> BreakRange.SHORT
        weeks in 9..18 -> BreakRange.MODERATE
        weeks in 19..38 -> BreakRange.EXTENDED
        weeks in 39..77 -> BreakRange.LONG
        weeks in 78..129 -> BreakRange.HIATUS
        else -> BreakRange.MULTI_YEAR
    }

    fun guidanceFor(weeks: Int): BreakGuidance = when {
        weeks <= 0 -> BreakGuidance.NONE
        weeks in 1..8 -> BreakGuidance.SHORT
        weeks in 9..38 -> BreakGuidance.MODERATE
        weeks in 39..77 -> BreakGuidance.LONG
        else -> BreakGuidance.HIATUS
    }
}
