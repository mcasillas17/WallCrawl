package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WeightUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fake on-device workout recommendation engine.
 * Mimics an intelligent local LLM by selecting structured workouts tailored to user goals,
 * muscle priorities, and time budget, while STRICTLY selecting from [context.allowedExercises].
 */
class FakeWorkoutPlanner : WorkoutPlanner {

    private val generationCounter = AtomicInteger(0)

    override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
        val candidates = context.allowedExercises
        if (candidates.isEmpty()) {
            throw WorkoutValidationException("Cannot generate workout: no allowed candidate exercises available.")
        }
        if (candidates.any { it.programming == null }) {
            throw WorkoutValidationException(
                "Cannot generate workout: every allowed candidate requires reviewed programming metadata."
            )
        }

        val generationIndex = generationCounter.getAndIncrement()
        val splitType = determineSplitType(context, generationIndex)

        val selectedExercises = selectExercisesForSplit(splitType, candidates, context)
        val generatedExerciseList = selectedExercises.map { exercise ->
            createGeneratedExercise(exercise, context)
        }

        val workoutName = generateWorkoutTitle(splitType, context.fitnessGoal)
        val focusMuscles = extractFocusMuscles(selectedExercises)
        val estimatedDuration = calculateEstimatedDuration(generatedExerciseList)

        return GeneratedWorkout(
            name = workoutName,
            focusMuscles = focusMuscles,
            estimatedDurationMinutes = estimatedDuration,
            exercises = generatedExerciseList,
            rationale = "Generated for ${context.fitnessGoal.displayName} with priority on ${focusMuscles.joinToString(", ")}."
        )
    }

    private fun determineSplitType(
        context: WorkoutGenerationContext,
        generationIndex: Int
    ): SplitType {
        val highPriorityMuscles = context.musclePriorities
            .filter { it.value == PriorityLevel.HIGH }
            .keys

        val splits = mutableListOf<SplitType>()

        if (highPriorityMuscles.any { it in listOf(StandardMuscles.CHEST, StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS) }) {
            splits.add(SplitType.PUSH)
        }
        if (highPriorityMuscles.any { it in listOf(StandardMuscles.BACK, StandardMuscles.LATS, StandardMuscles.BICEPS) }) {
            splits.add(SplitType.PULL)
        }
        if (highPriorityMuscles.any { it in listOf(StandardMuscles.QUADS, StandardMuscles.HAMSTRINGS, StandardMuscles.GLUTES) }) {
            splits.add(SplitType.LEGS)
        }

        if (splits.isEmpty()) {
            splits.addAll(listOf(SplitType.PUSH, SplitType.PULL, SplitType.LEGS, SplitType.UPPER_BODY))
        }

        return splits[generationIndex % splits.size]
    }

    private fun selectExercisesForSplit(
        split: SplitType,
        candidates: List<Exercise>,
        context: WorkoutGenerationContext
    ): List<Exercise> {
        val targetMuscles = when (split) {
            SplitType.PUSH -> listOf(StandardMuscles.CHEST, StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS)
            SplitType.PULL -> listOf(StandardMuscles.BACK, StandardMuscles.LATS, StandardMuscles.BICEPS)
            SplitType.LEGS -> listOf(StandardMuscles.QUADS, StandardMuscles.HAMSTRINGS, StandardMuscles.GLUTES, StandardMuscles.CALVES)
            SplitType.UPPER_BODY -> listOf(StandardMuscles.CHEST, StandardMuscles.BACK, StandardMuscles.SHOULDERS, StandardMuscles.ARMS())
            SplitType.FULL_BODY -> listOf(StandardMuscles.CHEST, StandardMuscles.BACK, StandardMuscles.QUADS, StandardMuscles.CORE)
        }

        // Filter candidates matching target muscles
        val matchingCandidates = candidates.filter { exercise ->
            exercise.primaryMuscles.any { it in targetMuscles } ||
                exercise.secondaryMuscles.any { it in targetMuscles }
        }.ifEmpty { candidates }

        // Partition compound vs isolation
        val compounds = matchingCandidates.filter { it.programming?.mechanics == MechanicsType.COMPOUND }
        val isolations = matchingCandidates.filter { it.programming?.mechanics == MechanicsType.ISOLATION }

        val exerciseCountTarget = when {
            context.preferredWorkoutDurationMinutes <= 35 -> 3
            context.preferredWorkoutDurationMinutes <= 55 -> 5
            else -> 6
        }

        val result = mutableListOf<Exercise>()
        // Pick primary compound lifts first
        result.addAll(compounds.take(minOf(3, exerciseCountTarget - 1)))

        // Fill remaining slots with isolation / secondary exercises
        val remainingSlots = exerciseCountTarget - result.size
        if (remainingSlots > 0) {
            val availableRemaining = (isolations + compounds).filter { it !in result }
            result.addAll(availableRemaining.take(remainingSlots))
        }

        return if (result.isNotEmpty()) result else candidates.take(exerciseCountTarget)
    }

    private fun createGeneratedExercise(
        exercise: Exercise,
        context: WorkoutGenerationContext
    ): GeneratedExercise {
        val programming = exercise.programming
            ?: throw WorkoutValidationException(
                "Cannot generate workout: ${exercise.id} has no reviewed programming metadata."
            )
        val sets = when (context.fitnessGoal) {
            FitnessGoal.STRENGTH -> if (programming.mechanics == MechanicsType.COMPOUND) 4 else 3
            FitnessGoal.BUILD_MUSCLE -> 3
            FitnessGoal.GENERAL_FITNESS -> 3
            FitnessGoal.FAT_LOSS -> 3
            FitnessGoal.ATHLETIC_PERFORMANCE -> 4
        }

        val (repMin, repMax) = when (context.fitnessGoal) {
            FitnessGoal.STRENGTH -> if (programming.mechanics == MechanicsType.COMPOUND) 4 to 6 else 6 to 8
            FitnessGoal.BUILD_MUSCLE -> programming.recommendedRepRange.min to programming.recommendedRepRange.max
            FitnessGoal.GENERAL_FITNESS -> 10 to 12
            FitnessGoal.FAT_LOSS -> 12 to 15
            FitnessGoal.ATHLETIC_PERFORMANCE -> 5 to 8
        }

        val targetWeight = suggestedTargetWeight(
            exercise = exercise,
            context = context,
            targetRepMaximum = repMax
        )

        val restSeconds = if (context.fitnessGoal == FitnessGoal.STRENGTH) 120 else 90

        return GeneratedExercise(
            exerciseId = exercise.id,
            targetSets = sets,
            repMin = repMin,
            repMax = repMax,
            targetWeight = targetWeight,
            restSeconds = restSeconds,
            notes = programming.coachingSummary
        )
    }

    private fun suggestedTargetWeight(
        exercise: Exercise,
        context: WorkoutGenerationContext,
        targetRepMaximum: Int
    ): Double? {
        val priorPerformance = context.exerciseHistory[exercise.id]
        val priorWeight = priorPerformance?.lastWeight
        if (priorWeight != null && priorWeight.isFinite() && priorWeight >= 0.0) {
            val completedRecentSets = priorPerformance.recentSets.filter { it.isCompleted }
            val reachedTopOfRange = completedRecentSets.isNotEmpty() &&
                completedRecentSets.all { (it.completedReps ?: 0) >= targetRepMaximum }
            return if (reachedTopOfRange) {
                priorWeight + when (context.preferredUnits) {
                    WeightUnit.LBS -> 5.0
                    WeightUnit.KG -> 2.5
                }
            } else {
                priorWeight
            }
        }

        return sampleStartingWeight(exercise.id)
    }

    private fun sampleStartingWeight(exerciseId: String): Double? = when (exerciseId) {
        "incline-dumbbell-press" -> 47.5
        "barbell-bench-press" -> 135.0
        "barbell-deadlift" -> 225.0
        "barbell-back-squat" -> 185.0
        "dumbbell-shoulder-press" -> 35.0
        "dumbbell-lateral-raise" -> 20.0
        "cable-triceps-pushdown" -> 42.5
        "barbell-bicep-curl" -> 55.0
        "romanian-deadlift" -> 135.0
        else -> null
    }

    private fun generateWorkoutTitle(split: SplitType, goal: FitnessGoal): String {
        val prefix = when (split) {
            SplitType.PUSH -> "Push"
            SplitType.PULL -> "Pull"
            SplitType.LEGS -> "Legs"
            SplitType.UPPER_BODY -> "Upper Body"
            SplitType.FULL_BODY -> "Full Body"
        }
        val suffix = when (goal) {
            FitnessGoal.BUILD_MUSCLE -> "Hypertrophy"
            FitnessGoal.STRENGTH -> "Power & Strength"
            FitnessGoal.ATHLETIC_PERFORMANCE -> "Agility & Explosiveness"
            FitnessGoal.FAT_LOSS -> "High-Density Circuit"
            FitnessGoal.GENERAL_FITNESS -> "Athletic Foundation"
        }
        return "$prefix · $suffix"
    }

    private fun extractFocusMuscles(exercises: List<Exercise>): List<String> {
        return exercises
            .flatMap { it.primaryMuscles }
            .distinct()
            .take(3)
    }

    private fun calculateEstimatedDuration(exercises: List<GeneratedExercise>): Int {
        val totalSets = exercises.sumOf { it.targetSets }
        val restTimeSeconds = exercises.sumOf { it.targetSets * it.restSeconds }
        val executionTimeSeconds = totalSets * 45 // approx 45s per set
        return ((restTimeSeconds + executionTimeSeconds) / 60).coerceAtLeast(25)
    }

    private enum class SplitType {
        PUSH,
        PULL,
        LEGS,
        UPPER_BODY,
        FULL_BODY
    }

    private fun StandardMuscles.ARMS(): String = StandardMuscles.BICEPS
}
