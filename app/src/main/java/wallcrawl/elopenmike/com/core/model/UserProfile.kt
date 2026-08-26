package wallcrawl.elopenmike.com.core.model

/**
 * User Profile and training preferences for WallCrawl.
 */
data class UserProfile(
    val id: String = DEFAULT_PROFILE_ID,
    val revision: Long = 0,
    val name: String = "Crawler",
    val primaryGoal: FitnessGoal = FitnessGoal.BUILD_MUSCLE,
    val experienceLevel: ExperienceLevel = ExperienceLevel.INTERMEDIATE,
    val preferredDurationMinutes: Int = 50,
    val daysPerWeek: Int = 4,
    val availableEquipment: List<String> = listOf(
        StandardEquipment.BARBELL,
        StandardEquipment.DUMBBELL,
        StandardEquipment.BENCH,
        StandardEquipment.PULLUP_BAR,
        StandardEquipment.DIP_BARS,
        StandardEquipment.SQUAT_RACK,
        StandardEquipment.BODYWEIGHT,
        StandardEquipment.CABLE
    ),
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
    val excludedExerciseIds: List<String> = emptyList()
) {
    companion object {
        const val DEFAULT_PROFILE_ID = "default_user"
    }
}

enum class FitnessGoal(val displayName: String, val description: String) {
    BUILD_MUSCLE("Build Muscle", "Hypertrophy focus with moderate-high volume and 8–15 rep targets."),
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
