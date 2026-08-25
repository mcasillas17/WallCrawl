package wallcrawl.elopenmike.com.core.model

/**
 * Exercise domain model aligned with the open-source Workout Guide catalog schema
 * and enriched with extensible metadata for training science and AI generation.
 */
data class Exercise(
    val id: String,
    val name: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val type: ExerciseType = ExerciseType.WEIGHTED_REPS,
    val movementPattern: MovementPattern = MovementPattern.OTHER,
    val difficulty: Difficulty = Difficulty.INTERMEDIATE,
    val compoundOrIsolation: MechanicsType = MechanicsType.COMPOUND,
    val recommendedRepRange: RepRange = RepRange(min = 8, max = 12),
    val fatigueScore: Int = 3, // Scale 1 (low fatigue) to 5 (high systemic fatigue)
    val description: String = ""
)

enum class ExerciseType {
    WEIGHTED_REPS,
    BODYWEIGHT_REPS,
    TIMED,
    CARDIO,
    DISTANCE
}

enum class MovementPattern {
    HORIZONTAL_PUSH,
    VERTICAL_PUSH,
    HORIZONTAL_PULL,
    VERTICAL_PULL,
    SQUAT,
    HINGE,
    LUNGE,
    ISOLATION,
    CARRY,
    CORE,
    OTHER
}

enum class MechanicsType {
    COMPOUND,
    ISOLATION
}

enum class Difficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

data class RepRange(
    val min: Int,
    val max: Int
) {
    override fun toString(): String = if (min == max) "$min" else "$min–$max"
}

object StandardMuscles {
    const val CHEST = "Chest"
    const val SHOULDERS = "Shoulders"
    const val TRICEPS = "Triceps"
    const val BACK = "Back"
    const val LATS = "Lats"
    const val BICEPS = "Biceps"
    const val FOREARMS = "Forearms"
    const val QUADS = "Quadriceps"
    const val HAMSTRINGS = "Hamstrings"
    const val GLUTES = "Glutes"
    const val CALVES = "Calves"
    const val CORE = "Core"
    const val ABS = "Abs"

    val ALL = listOf(
        CHEST, SHOULDERS, TRICEPS, BACK, LATS, BICEPS, FOREARMS,
        QUADS, HAMSTRINGS, GLUTES, CALVES, CORE, ABS
    )
}

object StandardEquipment {
    const val BARBELL = "Barbell"
    const val DUMBBELL = "Dumbbell"
    const val CABLE = "Cable"
    const val MACHINE = "Machine"
    const val BODYWEIGHT = "Bodyweight"
    const val KETTLEBELL = "Kettlebell"
    const val RESISTANCE_BAND = "Resistance Band"
    const val BENCH = "Bench"
    const val PULLUP_BAR = "Pull-up Bar"

    val ALL = listOf(
        BARBELL, DUMBBELL, CABLE, MACHINE, BODYWEIGHT,
        KETTLEBELL, RESISTANCE_BAND, BENCH, PULLUP_BAR
    )
}
