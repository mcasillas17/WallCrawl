package wallcrawl.elopenmike.com.core.exercise

import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory implementation of [ExerciseCatalog] populated with 12 structured exercises
 * conforming to Workout Guide schemas with extensible metadata.
 */
class InMemoryExerciseCatalog : ExerciseCatalog {

    private val exercises = MutableStateFlow(SAMPLE_EXERCISES)

    override fun getAllExercises(): Flow<List<Exercise>> = exercises

    override suspend fun getExerciseById(id: String): Exercise? {
        return exercises.value.find { it.id.equals(id, ignoreCase = true) }
    }

    override fun searchExercises(
        query: String,
        muscle: String?,
        equipment: String?
    ): Flow<List<Exercise>> {
        return exercises.map { list ->
            list.filter { exercise ->
                val matchesQuery = query.isBlank() ||
                    exercise.name.contains(query, ignoreCase = true) ||
                    exercise.primaryMuscles.any { it.contains(query, ignoreCase = true) } ||
                    exercise.secondaryMuscles.any { it.contains(query, ignoreCase = true) }

                val matchesMuscle = muscle.isNullOrBlank() ||
                    exercise.primaryMuscles.any { it.equals(muscle, ignoreCase = true) } ||
                    exercise.secondaryMuscles.any { it.equals(muscle, ignoreCase = true) }

                val matchesEquipment = equipment.isNullOrBlank() ||
                    exercise.equipment.any { it.equals(equipment, ignoreCase = true) }

                matchesQuery && matchesMuscle && matchesEquipment
            }
        }
    }

    override suspend fun getMuscleGroups(): List<String> {
        return exercises.value
            .flatMap { it.primaryMuscles + it.secondaryMuscles }
            .distinct()
            .sorted()
    }

    override suspend fun getEquipmentTypes(): List<String> {
        return exercises.value
            .flatMap { it.equipment }
            .distinct()
            .sorted()
    }

    companion object {
        val SAMPLE_EXERCISES = listOf(
            Exercise(
                id = "incline-dumbbell-press",
                name = "Incline Dumbbell Press",
                primaryMuscles = listOf(StandardMuscles.CHEST),
                secondaryMuscles = listOf(StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS),
                equipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
                type = ExerciseType.WEIGHTED_REPS,
                imageFrames = listOf("incline_db_press_1.svg", "incline_db_press_2.svg"),
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                compoundOrIsolation = MechanicsType.COMPOUND,
                recommendedRepRange = RepRange(8, 10),
                fatigueScore = 3,
                description = "Upper chest focus with dumbbells on a 30-45 degree incline bench for deep stretch and continuous tension."
            ),
            Exercise(
                id = "barbell-bench-press",
                name = "Barbell Bench Press",
                primaryMuscles = listOf(StandardMuscles.CHEST),
                secondaryMuscles = listOf(StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS),
                equipment = listOf(StandardEquipment.BARBELL, StandardEquipment.BENCH),
                type = ExerciseType.WEIGHTED_REPS,
                imageFrames = listOf("bench_press_1.svg", "bench_press_2.svg"),
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                compoundOrIsolation = MechanicsType.COMPOUND,
                recommendedRepRange = RepRange(5, 8),
                fatigueScore = 4,
                description = "Foundational horizontal press building mid-chest strength, front deltoid stability, and tricep power."
            ),
            Exercise(
                id = "pull-ups",
                name = "Pull-ups",
                primaryMuscles = listOf(StandardMuscles.BACK, StandardMuscles.LATS),
                secondaryMuscles = listOf(StandardMuscles.BICEPS, StandardMuscles.FOREARMS),
                equipment = listOf(StandardEquipment.PULLUP_BAR, StandardEquipment.BODYWEIGHT),
                type = ExerciseType.BODYWEIGHT_REPS,
                imageFrames = listOf("pullup_1.svg", "pullup_2.svg"),
                movementPattern = MovementPattern.VERTICAL_PULL,
                difficulty = Difficulty.INTERMEDIATE,
                compoundOrIsolation = MechanicsType.COMPOUND,
                recommendedRepRange = RepRange(6, 12),
                fatigueScore = 3,
                description = "Classic calisthenic vertical pull targeting lat width, upper back thickness, and functional grip control."
            ),
            Exercise(
                id = "barbell-deadlift",
                name = "Barbell Deadlift",
                primaryMuscles = listOf(StandardMuscles.BACK, StandardMuscles.HAMSTRINGS, StandardMuscles.GLUTES),
                secondaryMuscles = listOf(StandardMuscles.FOREARMS, StandardMuscles.CORE),
                equipment = listOf(StandardEquipment.BARBELL),
                type = ExerciseType.WEIGHTED_REPS,
                imageFrames = listOf("deadlift_1.svg", "deadlift_2.svg"),
                movementPattern = MovementPattern.HINGE,
                difficulty = Difficulty.ADVANCED,
                compoundOrIsolation = MechanicsType.COMPOUND,
                recommendedRepRange = RepRange(4, 6),
                fatigueScore = 5,
                description = "Total posterior chain anchor building immense pull strength, spinal erector density, and hip drive."
            ),
            Exercise(
                id = "barbell-back-squat",
                name = "Barbell Back Squat",
                primaryMuscles = listOf(StandardMuscles.QUADS, StandardMuscles.GLUTES),
                secondaryMuscles = listOf(StandardMuscles.HAMSTRINGS, StandardMuscles.CALVES, StandardMuscles.CORE),
                equipment = listOf(StandardEquipment.BARBELL),
                type = ExerciseType.WEIGHTED_REPS,
                imageFrames = listOf("squat_1.svg", "squat_2.svg"),
                movementPattern = MovementPattern.SQUAT,
                difficulty = Difficulty.ADVANCED,
                compoundOrIsolation = MechanicsType.COMPOUND,
                recommendedRepRange = RepRange(6, 8),
                fatigueScore = 5,
                description = "King of lower-body movements developing quad hypertrophy, knee stability, and core bracing endurance."
            ),
            Exercise(
                id = "dumbbell-shoulder-press",
                name = "Dumbbell Shoulder Press",
                primaryMuscles = listOf(StandardMuscles.SHOULDERS),
                secondaryMuscles = listOf(StandardMuscles.TRICEPS),
                equipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
                type = ExerciseType.WEIGHTED_REPS,
                imageFrames = listOf("db_shoulder_press_1.svg", "db_shoulder_press_2.svg"),
                movementPattern = MovementPattern.VERTICAL_PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                compoundOrIsolation = MechanicsType.COMPOUND,
                recommendedRepRange = RepRange(8, 12),
                fatigueScore = 3,
                description = "Seated overhead press targeting anterior and lateral deltoids with freedom of rotational arm path."
            ),
            Exercise(
                id = "dumbbell-lateral-raise",
                name = "Dumbbell Lateral Raise",
                primaryMuscles = listOf(StandardMuscles.SHOULDERS),
                secondaryMuscles = listOf(StandardMuscles.TRICEPS),
                equipment = listOf(StandardEquipment.DUMBBELL),
                type = ExerciseType.WEIGHTED_REPS,
                imageFrames = listOf("lateral_raise_1.svg", "lateral_raise_2.svg"),
                movementPattern = MovementPattern.ISOLATION,
                difficulty = Difficulty.BEGINNER,
                compoundOrIsolation = MechanicsType.ISOLATION,
                recommendedRepRange = RepRange(12, 15),
                fatigueScore = 2,
                description = "Direct lateral deltoid isolation for capped shoulder aesthetics and lateral cap definition."
            ),
            Exercise(
                id = "cable-triceps-pushdown",
                name = "Cable Triceps Pushdown",
                primaryMuscles = listOf(StandardMuscles.TRICEPS),
                secondaryMuscles = emptyList(),
                equipment = listOf(StandardEquipment.CABLE),
                type = ExerciseType.WEIGHTED_REPS,
                imageFrames = listOf("tricep_pushdown_1.svg", "tricep_pushdown_2.svg"),
                movementPattern = MovementPattern.ISOLATION,
                difficulty = Difficulty.BEGINNER,
                compoundOrIsolation = MechanicsType.ISOLATION,
                recommendedRepRange = RepRange(10, 15),
                fatigueScore = 2,
                description = "Constant tension elbow extension targeting the lateral and medial heads of the triceps brachii."
            ),
            Exercise(
                id = "barbell-bicep-curl",
                name = "Barbell Bicep Curl",
                primaryMuscles = listOf(StandardMuscles.BICEPS),
                secondaryMuscles = listOf(StandardMuscles.FOREARMS),
                equipment = listOf(StandardEquipment.BARBELL),
                type = ExerciseType.WEIGHTED_REPS,
                imageFrames = listOf("bicep_curl_1.svg", "bicep_curl_2.svg"),
                movementPattern = MovementPattern.ISOLATION,
                difficulty = Difficulty.BEGINNER,
                compoundOrIsolation = MechanicsType.ISOLATION,
                recommendedRepRange = RepRange(8, 12),
                fatigueScore = 2,
                description = "Strict standing arm flexion building peak bicep volume and forearm flexor strength."
            ),
            Exercise(
                id = "parallel-bar-dips",
                name = "Parallel Bar Dips",
                primaryMuscles = listOf(StandardMuscles.CHEST, StandardMuscles.TRICEPS),
                secondaryMuscles = listOf(StandardMuscles.SHOULDERS),
                equipment = listOf(StandardEquipment.BODYWEIGHT),
                type = ExerciseType.BODYWEIGHT_REPS,
                imageFrames = listOf("dips_1.svg", "dips_2.svg"),
                movementPattern = MovementPattern.VERTICAL_PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                compoundOrIsolation = MechanicsType.COMPOUND,
                recommendedRepRange = RepRange(8, 12),
                fatigueScore = 3,
                description = "Bodyweight compound press hitting lower chest fibers and tricep lockouts with forward torso lean."
            ),
            Exercise(
                id = "hanging-leg-raise",
                name = "Hanging Leg Raise",
                primaryMuscles = listOf(StandardMuscles.CORE, StandardMuscles.ABS),
                secondaryMuscles = listOf(StandardMuscles.FOREARMS),
                equipment = listOf(StandardEquipment.PULLUP_BAR, StandardEquipment.BODYWEIGHT),
                type = ExerciseType.BODYWEIGHT_REPS,
                imageFrames = listOf("leg_raise_1.svg", "leg_raise_2.svg"),
                movementPattern = MovementPattern.CORE,
                difficulty = Difficulty.INTERMEDIATE,
                compoundOrIsolation = MechanicsType.ISOLATION,
                recommendedRepRange = RepRange(10, 15),
                fatigueScore = 2,
                description = "Hanging core flexion prioritizing the lower rectus abdominis, hip flexors, and grip endurance."
            ),
            Exercise(
                id = "romanian-deadlift",
                name = "Romanian Deadlift (RDL)",
                primaryMuscles = listOf(StandardMuscles.HAMSTRINGS, StandardMuscles.GLUTES),
                secondaryMuscles = listOf(StandardMuscles.BACK, StandardMuscles.FOREARMS),
                equipment = listOf(StandardEquipment.BARBELL, StandardEquipment.DUMBBELL),
                type = ExerciseType.WEIGHTED_REPS,
                imageFrames = listOf("rdl_1.svg", "rdl_2.svg"),
                movementPattern = MovementPattern.HINGE,
                difficulty = Difficulty.INTERMEDIATE,
                compoundOrIsolation = MechanicsType.COMPOUND,
                recommendedRepRange = RepRange(8, 10),
                fatigueScore = 4,
                description = "Controlled eccentric hip hinge maximizing hamstring stretch under load and glute contraction."
            )
        )
    }
}
