package wallcrawl.elopenmike.com.core.exercise

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.ProgressionType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles

/** Injectable in-memory catalog retained for focused tests and previews. */
class InMemoryExerciseCatalog(
    exercises: List<Exercise> = SAMPLE_EXERCISES
) : ExerciseCatalog {

    private val exerciseState = MutableStateFlow(exercises)

    override fun getAllExercises(): Flow<List<Exercise>> = exerciseState

    override suspend fun getExerciseById(id: String): Exercise? =
        exerciseState.value.find { it.id.equals(id, ignoreCase = true) }

    override fun searchExercises(
        query: String,
        muscle: String?,
        equipment: String?
    ): Flow<List<Exercise>> {
        val normalizedQuery = query.trim()
        return exerciseState.map { exercises ->
            exercises.filter { exercise ->
                val matchesQuery = normalizedQuery.isBlank() ||
                    exercise.name.contains(normalizedQuery, ignoreCase = true) ||
                    exercise.searchAliases.any { it.contains(normalizedQuery, ignoreCase = true) } ||
                    exercise.primaryMuscles.any { it.contains(normalizedQuery, ignoreCase = true) } ||
                    exercise.secondaryMuscles.any { it.contains(normalizedQuery, ignoreCase = true) } ||
                    exercise.listedEquipment.any { it.contains(normalizedQuery, ignoreCase = true) }

                val matchesMuscle = muscle.isNullOrBlank() ||
                    exercise.primaryMuscles.any { it.equals(muscle, ignoreCase = true) } ||
                    exercise.secondaryMuscles.any { it.equals(muscle, ignoreCase = true) }

                val matchesEquipment = equipment.isNullOrBlank() ||
                    exercise.listedEquipment.any { it.equals(equipment, ignoreCase = true) }

                matchesQuery && matchesMuscle && matchesEquipment
            }
        }
    }

    override suspend fun getMuscleGroups(): List<String> = exerciseState.value
        .flatMap { it.primaryMuscles + it.secondaryMuscles }
        .distinctBy(String::lowercase)
        .sortedBy(String::lowercase)

    override suspend fun getEquipmentTypes(): List<String> = exerciseState.value
        .flatMap { it.listedEquipment }
        .distinctBy(String::lowercase)
        .sortedBy(String::lowercase)

    companion object {
        val SAMPLE_EXERCISES = listOf(
            programmedExercise(
                id = "incline-dumbbell-press",
                name = "Incline Dumbbell Press",
                primaryMuscles = listOf(StandardMuscles.CHEST),
                secondaryMuscles = listOf(StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS),
                listedEquipment = listOf(StandardEquipment.DUMBBELL),
                requiredEquipmentCombinations = listOf(listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)),
                type = ExerciseType.WEIGHT_REPS,
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.COMPOUND,
                repRange = RepRange(8, 10),
                fatigueScore = 3,
                coachingSummary = "Upper chest press using dumbbells on an incline bench with a controlled range of motion."
            ),
            programmedExercise(
                id = "barbell-bench-press",
                name = "Barbell Bench Press",
                primaryMuscles = listOf(StandardMuscles.CHEST),
                secondaryMuscles = listOf(StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS),
                listedEquipment = listOf(StandardEquipment.BARBELL),
                requiredEquipmentCombinations = listOf(
                    listOf(StandardEquipment.BARBELL, StandardEquipment.BENCH, StandardEquipment.SQUAT_RACK)
                ),
                type = ExerciseType.WEIGHT_REPS,
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.COMPOUND,
                repRange = RepRange(5, 8),
                fatigueScore = 4,
                coachingSummary = "Foundational horizontal press for chest, shoulder, and triceps strength."
            ),
            programmedExercise(
                id = "pull-ups",
                name = "Pull-ups",
                primaryMuscles = listOf(StandardMuscles.BACK, StandardMuscles.LATS),
                secondaryMuscles = listOf(StandardMuscles.BICEPS, StandardMuscles.FOREARMS),
                listedEquipment = listOf(StandardEquipment.BODYWEIGHT),
                requiredEquipmentCombinations = listOf(listOf(StandardEquipment.PULLUP_BAR)),
                type = ExerciseType.BODYWEIGHT_REPS,
                movementPattern = MovementPattern.VERTICAL_PULL,
                difficulty = Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.COMPOUND,
                repRange = RepRange(6, 12),
                fatigueScore = 3,
                coachingSummary = "Bodyweight vertical pull emphasizing the lats, upper back, biceps, and grip."
            ),
            programmedExercise(
                id = "barbell-deadlift",
                name = "Barbell Deadlift",
                primaryMuscles = listOf(StandardMuscles.BACK, StandardMuscles.HAMSTRINGS, StandardMuscles.GLUTES),
                secondaryMuscles = listOf(StandardMuscles.FOREARMS, StandardMuscles.CORE),
                listedEquipment = listOf(StandardEquipment.BARBELL),
                requiredEquipmentCombinations = listOf(listOf(StandardEquipment.BARBELL)),
                type = ExerciseType.WEIGHT_REPS,
                movementPattern = MovementPattern.HINGE,
                difficulty = Difficulty.ADVANCED,
                mechanics = MechanicsType.COMPOUND,
                repRange = RepRange(4, 6),
                fatigueScore = 5,
                coachingSummary = "Heavy hip hinge for posterior-chain strength, trunk bracing, and grip."
            ),
            programmedExercise(
                id = "barbell-back-squat",
                name = "Barbell Back Squat",
                primaryMuscles = listOf(StandardMuscles.QUADS, StandardMuscles.GLUTES),
                secondaryMuscles = listOf(StandardMuscles.HAMSTRINGS, StandardMuscles.CALVES, StandardMuscles.CORE),
                listedEquipment = listOf(StandardEquipment.BARBELL),
                requiredEquipmentCombinations = listOf(listOf(StandardEquipment.BARBELL, StandardEquipment.SQUAT_RACK)),
                type = ExerciseType.WEIGHT_REPS,
                movementPattern = MovementPattern.SQUAT,
                difficulty = Difficulty.ADVANCED,
                mechanics = MechanicsType.COMPOUND,
                repRange = RepRange(6, 8),
                fatigueScore = 5,
                coachingSummary = "Barbell squat emphasizing quadriceps and glutes with full-body bracing."
            ),
            programmedExercise(
                id = "dumbbell-shoulder-press",
                name = "Dumbbell Shoulder Press",
                primaryMuscles = listOf(StandardMuscles.SHOULDERS),
                secondaryMuscles = listOf(StandardMuscles.TRICEPS),
                listedEquipment = listOf(StandardEquipment.DUMBBELL),
                requiredEquipmentCombinations = listOf(listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)),
                type = ExerciseType.WEIGHT_REPS,
                movementPattern = MovementPattern.VERTICAL_PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.COMPOUND,
                repRange = RepRange(8, 12),
                fatigueScore = 3,
                coachingSummary = "Seated dumbbell overhead press for shoulder and triceps strength."
            ),
            programmedExercise(
                id = "dumbbell-lateral-raise",
                name = "Dumbbell Lateral Raise",
                primaryMuscles = listOf(StandardMuscles.SHOULDERS),
                secondaryMuscles = emptyList(),
                listedEquipment = listOf(StandardEquipment.DUMBBELL),
                requiredEquipmentCombinations = listOf(listOf(StandardEquipment.DUMBBELL)),
                type = ExerciseType.WEIGHT_REPS,
                movementPattern = MovementPattern.ISOLATION,
                difficulty = Difficulty.BEGINNER,
                mechanics = MechanicsType.ISOLATION,
                repRange = RepRange(12, 15),
                fatigueScore = 2,
                coachingSummary = "Shoulder isolation emphasizing the lateral deltoids with controlled arm elevation."
            ),
            programmedExercise(
                id = "cable-triceps-pushdown",
                name = "Cable Triceps Pushdown",
                primaryMuscles = listOf(StandardMuscles.TRICEPS),
                secondaryMuscles = emptyList(),
                listedEquipment = listOf(StandardEquipment.CABLE),
                requiredEquipmentCombinations = listOf(listOf(StandardEquipment.CABLE)),
                type = ExerciseType.WEIGHT_REPS,
                movementPattern = MovementPattern.ISOLATION,
                difficulty = Difficulty.BEGINNER,
                mechanics = MechanicsType.ISOLATION,
                repRange = RepRange(10, 15),
                fatigueScore = 2,
                coachingSummary = "Cable elbow extension that trains the triceps through a controlled range."
            ),
            programmedExercise(
                id = "barbell-bicep-curl",
                name = "Barbell Bicep Curl",
                primaryMuscles = listOf(StandardMuscles.BICEPS),
                secondaryMuscles = listOf(StandardMuscles.FOREARMS),
                listedEquipment = listOf(StandardEquipment.BARBELL),
                requiredEquipmentCombinations = listOf(listOf(StandardEquipment.BARBELL)),
                type = ExerciseType.WEIGHT_REPS,
                movementPattern = MovementPattern.ISOLATION,
                difficulty = Difficulty.BEGINNER,
                mechanics = MechanicsType.ISOLATION,
                repRange = RepRange(8, 12),
                fatigueScore = 2,
                coachingSummary = "Standing curl for biceps and forearm flexors using a barbell or EZ-bar."
            ),
            programmedExercise(
                id = "parallel-bar-dips",
                name = "Parallel Bar Dips",
                primaryMuscles = listOf(StandardMuscles.CHEST, StandardMuscles.TRICEPS),
                secondaryMuscles = listOf(StandardMuscles.SHOULDERS),
                listedEquipment = listOf(StandardEquipment.BODYWEIGHT),
                requiredEquipmentCombinations = listOf(listOf(StandardEquipment.DIP_BARS)),
                type = ExerciseType.BODYWEIGHT_REPS,
                movementPattern = MovementPattern.VERTICAL_PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.COMPOUND,
                repRange = RepRange(8, 12),
                fatigueScore = 3,
                coachingSummary = "Parallel-bar press emphasizing the chest and triceps with controlled shoulder depth."
            ),
            programmedExercise(
                id = "hanging-leg-raise",
                name = "Hanging Leg Raise",
                primaryMuscles = listOf(StandardMuscles.CORE, StandardMuscles.ABS),
                secondaryMuscles = listOf(StandardMuscles.FOREARMS),
                listedEquipment = listOf(StandardEquipment.BODYWEIGHT),
                requiredEquipmentCombinations = listOf(listOf(StandardEquipment.PULLUP_BAR)),
                type = ExerciseType.BODYWEIGHT_REPS,
                movementPattern = MovementPattern.CORE,
                difficulty = Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.ISOLATION,
                repRange = RepRange(10, 15),
                fatigueScore = 2,
                progressionType = ProgressionType.REPETITIONS,
                coachingSummary = "Hanging trunk and hip flexion for the abdominals, hip flexors, and grip."
            ),
            programmedExercise(
                id = "romanian-deadlift",
                name = "Romanian Deadlift (RDL)",
                primaryMuscles = listOf(StandardMuscles.HAMSTRINGS, StandardMuscles.GLUTES),
                secondaryMuscles = listOf(StandardMuscles.BACK, StandardMuscles.FOREARMS),
                listedEquipment = listOf(StandardEquipment.BARBELL),
                requiredEquipmentCombinations = listOf(
                    listOf(StandardEquipment.BARBELL),
                    listOf(StandardEquipment.DUMBBELL)
                ),
                type = ExerciseType.WEIGHT_REPS,
                movementPattern = MovementPattern.HINGE,
                difficulty = Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.COMPOUND,
                repRange = RepRange(8, 10),
                fatigueScore = 4,
                coachingSummary = "Controlled hip hinge emphasizing the hamstrings and glutes under load."
            )
        )

        private fun programmedExercise(
            id: String,
            name: String,
            primaryMuscles: List<String>,
            secondaryMuscles: List<String>,
            listedEquipment: List<String>,
            requiredEquipmentCombinations: List<List<String>>,
            type: ExerciseType,
            movementPattern: MovementPattern,
            difficulty: Difficulty,
            mechanics: MechanicsType,
            repRange: RepRange,
            fatigueScore: Int,
            progressionType: ProgressionType = ProgressionType.REPETITIONS_THEN_LOAD,
            coachingSummary: String
        ) = Exercise(
            id = id,
            name = name,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles,
            listedEquipment = listedEquipment,
            type = type,
            programming = ExerciseProgrammingMetadata(
                requiredEquipmentCombinations = requiredEquipmentCombinations,
                movementPattern = movementPattern,
                difficulty = difficulty,
                mechanics = mechanics,
                recommendedRepRange = repRange,
                fatigueScore = fatigueScore,
                progressionType = progressionType,
                coachingSummary = coachingSummary
            )
        )
    }
}
