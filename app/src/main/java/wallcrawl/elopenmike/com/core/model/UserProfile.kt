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
    val confirmedStartingLoads: Map<String, Double> = emptyMap()
) {
    val primaryGoal: FitnessGoal get() = goals.firstOrNull() ?: FitnessGoal.BUILD_MUSCLE

    companion object {
        const val DEFAULT_PROFILE_ID = "default_user"
    }
}

enum class FitnessGoal(val displayName: String, val description: String) {
    BUILD_MUSCLE("Build Muscle", "Hypertrophy focus with moderate-high volume, and lower reps on the heavy compounds."),
    STRENGTH("Strength", "Heavy compound lifts with lower reps (3–6) and longer rest periods."),
    GENERAL_FITNESS("General Fitness", "Balanced functional strength, endurance, and mobility."),
    FAT_LOSS("Fat Loss", "High density training with steady pace and compound movements."),
    ATHLETIC_PERFORMANCE("Athletic Performance", "Power, agility, explosive strength, and rotational control.")
}

enum class ExperienceLevel(val displayName: String) {
    BEGINNER("Beginner (<1 year)"),
    INTERMEDIATE("Intermediate (1–3 years)"),
    ADVANCED("Advanced (3+ years)")
}

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

enum class PriorityLevel(val multiplier: Float, val label: String) {
    LOW(0.6f, "Low"),
    NORMAL(1.0f, "Normal"),
    HIGH(1.4f, "High")
}

data class BreakRange(
    val weeks: Int,
    val title: String,
    val subtitle: String
)

object BreakDurationHelper {
    val RANGES = listOf(
        BreakRange(0, "Consistent / No Break", "Regular training without extended gaps"),
        BreakRange(4, "1–4 Weeks", "Short break • Quick momentum recovery"),
        BreakRange(12, "1–3 Months", "Moderate break • Tendon re-adaptation"),
        BreakRange(26, "3–6 Months", "Extended break • Conservative volume ramp-up"),
        BreakRange(52, "6–12 Months", "Long break • Conservative baseline reset"),
        BreakRange(104, "1–2 Years", "Extended hiatus • Re-entry active (2 sets/exercise)"),
        BreakRange(156, "2+ Years", "Multi-year hiatus • Full re-entry & fresh baseline")
    )

    val PRESETS = RANGES.map { it.weeks to it.title }

    fun findMatchingRange(weeks: Int): BreakRange = when {
        weeks <= 0 -> RANGES[0]
        weeks in 1..8 -> RANGES[1]
        weeks in 9..18 -> RANGES[2]
        weeks in 19..38 -> RANGES[3]
        weeks in 39..77 -> RANGES[4]
        weeks in 78..129 -> RANGES[5]
        else -> RANGES[6]
    }

    fun formatLabel(weeks: Int): String = findMatchingRange(weeks).title

    fun formatDetailedLabel(weeks: Int): String {
        val range = findMatchingRange(weeks)
        return "${range.title} • ${range.subtitle}"
    }

    fun guidanceText(weeks: Int): String = when {
        weeks <= 0 -> "No recent break. Standard adaptive progression."
        weeks in 1..8 -> "Short break. Quick ramp-up to normal training volume."
        weeks in 9..38 -> "Moderate break. We'll start with conservative volume to protect connective tissue and ease back in."
        weeks in 39..77 -> "Significant break. Sets and starting weights are scaled to avoid excessive soreness while rebuilding capacity."
        else -> "Extended hiatus (1+ years). Volume is capped at 2 sets per movement to protect joint tendons and safely rebuild connective tissue."
    }
}


