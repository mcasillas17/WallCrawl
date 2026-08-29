package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
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

        val breakWeeks = context.userProfile.returningAfterBreakWeeks
        val isLongBreak = breakWeeks >= 52
        val isMediumBreak = breakWeeks in 4..51

        val baseWorkoutName = generateWorkoutTitle(splitType, context.fitnessGoals.ifEmpty { setOf(context.fitnessGoal) })
        val workoutName = if (isLongBreak) "$baseWorkoutName (Re-entry)" else baseWorkoutName
        val focusMuscles = extractFocusMuscles(selectedExercises)
        val estimatedDuration = calculateEstimatedDuration(generatedExerciseList)
        val goalsDisplay = context.fitnessGoals.ifEmpty { setOf(context.fitnessGoal) }
            .joinToString(" + ") { it.displayName }

        val rationale = when {
            isLongBreak -> "Re-entry Ramp-Up Active: Volume is capped at 2 sets to protect joint tendons and safely rebuild capacity after ${wallcrawl.elopenmike.com.core.model.BreakDurationHelper.formatLabel(breakWeeks)}."
            isMediumBreak -> "Generated for $goalsDisplay with conservative volume scaling for break recovery (${wallcrawl.elopenmike.com.core.model.BreakDurationHelper.formatLabel(breakWeeks)})."
            else -> "Generated for $goalsDisplay with priority on ${focusMuscles.joinToString(", ")}."
        }

        return GeneratedWorkout(
            name = workoutName,
            focusMuscles = focusMuscles,
            estimatedDurationMinutes = estimatedDuration,
            exercises = generatedExerciseList,
            rationale = rationale
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

        // Only rotate onto splits this profile can actually fill, and fall back to any
        // fillable split when the preferred ones are not. Preferring a split the equipment
        // cannot train and then failing would strand the user: the choice is deterministic,
        // so every retry lands on the same empty split.
        if (candidates.none { it.isStrengthWork() }) {
            throw WorkoutValidationException(
                message = "Every available candidate is cardio or mobility work.",
                failure = WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES
            )
        }

        fun List<SplitType>.fillable() = filter { split -> candidates.any { it.trains(split) } }
        val trainable = preferred.fillable().ifEmpty { SplitType.DEFAULT_ROTATION.fillable() }
        if (trainable.isEmpty()) {
            throw WorkoutValidationException(
                message = "No available exercise trains any split for this profile.",
                failure = WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT
            )
        }

        return trainable[rotationSeed(context, generationIndex).mod(trainable.size)]
    }

    /**
     * Advances the split from one training day to the next.
     *
     * The in-memory counter alone resets whenever the process is killed, so a user who
     * opens the app once a day would see the first split every day. Completed workouts
     * carry the rotation across restarts; the counter still varies within a session so
     * regenerating offers something different.
     */
    private fun rotationSeed(context: WorkoutGenerationContext, generationIndex: Int): Int =
        context.completedWorkoutCount + generationIndex

    private fun Exercise.trains(split: SplitType): Boolean =
        isStrengthWork() &&
            (
                primaryMuscles.any { it in split.targetMuscles } ||
                    secondaryMuscles.any { it in split.targetMuscles }
                )

    /**
     * Whether this belongs in a prescribed strength slot with sets and reps.
     *
     * Cardio machines and stretches are tagged with the muscles they involve, so once
     * upstream's umbrella names were resolved they started matching splits — putting
     * "Walking" in a Legs · Hypertrophy plan alongside squats. They stay in the catalog to
     * browse and to build custom workouts from; they are not prescribed as training slots.
     *
     * The test is what can be prescribed, not whether conditioning is involved: a kettlebell
     * swing is loaded work for reps that happens to be tagged Cardio, and a plank is a timed
     * hold that is not. Only untimed-distance work and conditioning drills measured purely
     * in time are dropped.
     */
    private fun Exercise.isStrengthWork(): Boolean = when {
        isStretch -> false
        type == ExerciseType.DISTANCE_DURATION -> false
        type == ExerciseType.DURATION -> !isConditioning()
        else -> true
    }

    private fun Exercise.isConditioning(): Boolean =
        (primaryMuscles + secondaryMuscles).any { it == StandardMuscles.CARDIO }

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

        val exerciseCountTarget = when {
            context.preferredWorkoutDurationMinutes <= 35 -> 3
            context.preferredWorkoutDurationMinutes <= 55 -> 5
            else -> 6
        }
        val compoundSlots = minOf(3, exerciseCountTarget - 1)

        val result = mutableListOf<Exercise>()
        result.addAll(chooseCompounds(split, matchingCandidates, compoundSlots))

        // Fill the remaining slots from every matching exercise. Programming metadata
        // influences ordering but never prevents an otherwise valid exercise from selection.
        val remainingSlots = exerciseCountTarget - result.size
        if (remainingSlots > 0) {
            val accessories = matchingCandidates
                .filterNot { it in result }
                .sortedWith(accessoryOrder(split))
            result.addAll(accessories.take(remainingSlots))
        }

        return result
    }

    /**
     * Picks the heavy work a session is built around.
     *
     * Ordering is by what the exercise trains and how much it demands, because taking
     * candidates in catalog order means taking them alphabetically: a push day led with
     * Arnold Press and a bench dip while the bench press and overhead press sat unused.
     * One exercise per movement pattern keeps the session from becoming three of the
     * same lift.
     */
    private fun chooseCompounds(
        split: SplitType,
        candidates: List<Exercise>,
        slots: Int
    ): List<Exercise> {
        if (slots <= 0) return emptyList()
        val compounds = candidates
            .filter { it.programming?.mechanics == MechanicsType.COMPOUND }
            .sortedWith(
                compareByDescending<Exercise> { it.trainsAsPrimary(split) }
                    .thenByDescending { it.programming?.fatigueScore ?: 0 }
                    .thenBy { it.id }
            )

        val chosen = mutableListOf<Exercise>()
        val usedPatterns = mutableSetOf<MovementPattern>()
        for (exercise in compounds) {
            if (chosen.size == slots) break
            val pattern = exercise.programming?.movementPattern
            if (pattern != null && !usedPatterns.add(pattern)) continue
            chosen.add(exercise)
        }
        // A split may not offer `slots` distinct patterns; take the best of what is left.
        for (exercise in compounds) {
            if (chosen.size == slots) break
            if (exercise !in chosen) chosen.add(exercise)
        }
        return chosen
    }

    /**
     * Orders the work that fills the rest of the session.
     *
     * Exercises that train the split directly come before ones that only brush against it,
     * and isolation work comes before more compounds: the heavy work is already chosen, so
     * another squat pattern adds fatigue where an accessory adds the volume that was missing.
     */
    private fun accessoryOrder(split: SplitType): Comparator<Exercise> =
        compareByDescending<Exercise> { it.trainsAsPrimary(split) }
            .thenByDescending { it.programming?.mechanics == MechanicsType.ISOLATION }
            .thenByDescending { it.programming != null }
            .thenByDescending { it.programming?.fatigueScore ?: 0 }
            .thenBy { it.id }

    private fun Exercise.trainsAsPrimary(split: SplitType): Boolean =
        primaryMuscles.any { it in split.targetMuscles }

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

    private fun generateWorkoutTitle(split: SplitType, goals: Set<FitnessGoal>): String {
        val prefix = split.displayName
        val suffix = when {
            FitnessGoal.STRENGTH in goals && FitnessGoal.BUILD_MUSCLE in goals -> "Power & Hypertrophy"
            FitnessGoal.STRENGTH in goals && FitnessGoal.ATHLETIC_PERFORMANCE in goals -> "Power & Performance"
            FitnessGoal.FAT_LOSS in goals && FitnessGoal.ATHLETIC_PERFORMANCE in goals -> "Athletic Conditioning"
            FitnessGoal.BUILD_MUSCLE in goals && FitnessGoal.FAT_LOSS in goals -> "Hypertrophy & Definition"
            FitnessGoal.BUILD_MUSCLE in goals -> "Hypertrophy"
            FitnessGoal.STRENGTH in goals -> "Power & Strength"
            FitnessGoal.ATHLETIC_PERFORMANCE in goals -> "Agility & Explosiveness"
            FitnessGoal.FAT_LOSS in goals -> "High-Density Circuit"
            FitnessGoal.GENERAL_FITNESS in goals -> "Athletic Foundation"
            else -> "Conditioning"
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
