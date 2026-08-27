package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Rule-based on-device workout planner: the tier that always works.
 *
 * Selects structured workouts from user goals, muscle priorities, and time budget, STRICTLY
 * from [WorkoutGenerationContext.allowedExercises]. No model is involved; when a generative
 * tier is added this stays as its fallback, so its output must be good on its own.
 */
class FakeWorkoutPlanner(
    private val prescriptionFactory: DefaultExercisePrescriptionFactory =
        DefaultExercisePrescriptionFactory()
) : WorkoutPlanner {

    private val generationCounter = AtomicInteger(0)

    override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
        val candidates = context.allowedExercises
        if (candidates.isEmpty()) {
            throw WorkoutValidationException(
                message = "Cannot generate workout: no allowed candidate exercises available.",
                failure = WorkoutPlanningFailure.NO_CANDIDATES
            )
        }
        val generationIndex = generationCounter.getAndIncrement()
        val splitType = determineSplitType(context, generationIndex, candidates)

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
        generationIndex: Int,
        candidates: List<Exercise>
    ): SplitType {
        val highPriorityMuscles = context.musclePriorities
            .filter { it.value == PriorityLevel.HIGH }
            .keys

        val preferred = SplitType.entries.filter { split ->
            highPriorityMuscles.any { it in split.targetMuscles }
        }
        val rotation = preferred.ifEmpty { SplitType.DEFAULT_ROTATION }

        // Only rotate onto splits this profile can actually fill. Picking blind and then
        // failing would strand anyone whose priorities resolve to a single split their
        // equipment cannot train — every retry would land on the same empty split.
        val trainable = rotation.filter { split -> candidates.any { it.trains(split) } }
        if (trainable.isEmpty()) {
            throw WorkoutValidationException(
                message = "No available exercise trains any split for this profile.",
                failure = WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT
            )
        }

        return trainable[generationIndex.mod(trainable.size)]
    }

    private fun Exercise.trains(split: SplitType): Boolean =
        primaryMuscles.any { it in split.targetMuscles } ||
            secondaryMuscles.any { it in split.targetMuscles }

    private fun selectExercisesForSplit(
        split: SplitType,
        candidates: List<Exercise>,
        context: WorkoutGenerationContext
    ): List<Exercise> {
        // A candidate that trains none of the split's muscles is not a substitute for one that
        // does. Widening back to the whole catalog is what used to hand a Push day whatever
        // sorted first; determineSplitType has already guaranteed this split is fillable.
        val matchingCandidates = candidates.filter { it.trains(split) }
        check(matchingCandidates.isNotEmpty()) {
            "Split ${split.displayName} was selected without any matching candidate."
        }

        // Prefer reviewed compound lifts, then allow every remaining matching exercise.
        val compounds = matchingCandidates.filter { it.programming?.mechanics == MechanicsType.COMPOUND }
        val exerciseCountTarget = when {
            context.preferredWorkoutDurationMinutes <= 35 -> 3
            context.preferredWorkoutDurationMinutes <= 55 -> 5
            else -> 6
        }

        val result = mutableListOf<Exercise>()
        // Pick primary compound lifts first
        result.addAll(compounds.take(minOf(3, exerciseCountTarget - 1)))

        // Fill remaining slots from the entire matching catalog. Programming metadata
        // influences ordering but never prevents an otherwise valid exercise from selection.
        val remainingSlots = exerciseCountTarget - result.size
        if (remainingSlots > 0) {
            val availableRemaining = matchingCandidates.filter { it !in result }
            result.addAll(availableRemaining.take(remainingSlots))
        }

        return result
    }

    private fun createGeneratedExercise(
        exercise: Exercise,
        context: WorkoutGenerationContext
    ): GeneratedExercise {
        return GeneratedExercise(
            exerciseId = exercise.id,
            prescription = prescriptionFactory.create(exercise, context),
            notes = exercise.programming?.coachingSummary.orEmpty()
        )
    }

    private fun generateWorkoutTitle(split: SplitType, goal: FitnessGoal): String {
        val prefix = split.displayName
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
        val restTimeSeconds = exercises.sumOf { it.targetSets * it.restSeconds }
        val executionTimeSeconds = exercises.sumOf { exercise ->
            exercise.targetSets * (exercise.prescription.targetDurationSeconds ?: 45)
        }
        return ((restTimeSeconds + executionTimeSeconds) / 60).coerceIn(1, 240)
    }

    private enum class SplitType(
        val displayName: String,
        val targetMuscles: List<String>
    ) {
        PUSH(
            displayName = "Push",
            targetMuscles = listOf(
                StandardMuscles.CHEST,
                StandardMuscles.SHOULDERS,
                StandardMuscles.TRICEPS
            )
        ),
        PULL(
            displayName = "Pull",
            targetMuscles = listOf(
                StandardMuscles.BACK,
                StandardMuscles.UPPER_BACK,
                StandardMuscles.LATS,
                StandardMuscles.REAR_DELTS,
                StandardMuscles.BICEPS,
                StandardMuscles.FOREARMS
            )
        ),
        LEGS(
            displayName = "Legs",
            targetMuscles = listOf(
                StandardMuscles.QUADS,
                StandardMuscles.HAMSTRINGS,
                StandardMuscles.GLUTES,
                StandardMuscles.CALVES,
                StandardMuscles.ADDUCTORS,
                StandardMuscles.HIPS,
                // Hip hinges are the app's lower-back work; without this a Lower Back
                // priority would select no split at all.
                StandardMuscles.LOWER_BACK
            )
        ),
        UPPER_BODY(
            displayName = "Upper Body",
            targetMuscles = listOf(
                StandardMuscles.CHEST,
                StandardMuscles.BACK,
                StandardMuscles.UPPER_BACK,
                StandardMuscles.LATS,
                StandardMuscles.SHOULDERS,
                StandardMuscles.REAR_DELTS,
                StandardMuscles.BICEPS,
                StandardMuscles.TRICEPS
            )
        ),
        FULL_BODY(
            displayName = "Full Body",
            targetMuscles = listOf(
                StandardMuscles.CHEST,
                StandardMuscles.BACK,
                StandardMuscles.QUADS,
                StandardMuscles.HAMSTRINGS,
                StandardMuscles.GLUTES,
                StandardMuscles.CORE
            )
        );

        companion object {
            /**
             * Used when no muscle is marked high priority. FULL_BODY is the only split that
             * trains Core, so it stays in the rotation rather than being unreachable.
             */
            val DEFAULT_ROTATION = listOf(PUSH, PULL, LEGS, UPPER_BODY, FULL_BODY)
        }
    }
}
