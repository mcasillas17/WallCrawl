package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.BreakDurationHelper
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WorkoutEmphasis
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutRationaleSpec
import wallcrawl.elopenmike.com.core.model.WorkoutSplit
import wallcrawl.elopenmike.com.core.model.WorkoutTitleSpec
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
        DefaultExercisePrescriptionFactory(),
    private val difficultyRankingPolicy: ExerciseDifficultyRankingPolicy =
        ExerciseDifficultyRankingPolicy(),
    private val capabilityPreferenceRankingPolicy: CapabilityPreferenceRankingPolicy =
        CapabilityPreferenceRankingPolicy()
) : WorkoutPlanner {

    private val generationCounter = AtomicInteger(0)

    override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
        val candidates = context.allowedExercises
        if (candidates.isEmpty()) {
            val reviewedFailure =
                (context.automaticEligibilityResult as? AutomaticEligibilityResult.NoCandidates)
                    ?.failure
            throw WorkoutValidationException(
                message = "Cannot generate workout: no allowed candidate exercises available.",
                failure = if (reviewedFailure != null) {
                    WorkoutPlanningFailure.REVIEWED_ELIGIBILITY_NO_CANDIDATES
                } else {
                    WorkoutPlanningFailure.NO_CANDIDATES
                },
                automaticEligibilityFailure = reviewedFailure
            )
        }
        val generationIndex = generationCounter.getAndIncrement()
        val splitType = determineSplit(context, generationIndex, candidates)

        val selectedExercises = selectExercisesForSplit(splitType, candidates, context)
        val generatedExerciseList = selectedExercises.map { exercise ->
            createGeneratedExercise(exercise, context)
        }

        val breakWeeks = context.userProfile.returningAfterBreakWeeks
        val isLongBreak = breakWeeks >= 52
        val isMediumBreak = breakWeeks in 4..51

        val goals = context.fitnessGoals.ifEmpty { setOf(context.fitnessGoal) }
        val focusMuscles = extractFocusMuscles(selectedExercises)
        val estimatedDuration = WorkoutDurationEstimator.estimateMinutes(generatedExerciseList)
        val breakRange = BreakDurationHelper.findMatchingRange(breakWeeks)
        // Ordered by the enum rather than by set iteration, so the same profile always
        // produces the same title and the same explanation.
        val orderedGoals = FitnessGoal.entries.filter { it in goals }

        val rationale = when {
            isLongBreak -> WorkoutRationaleSpec.ReEntryRamp(breakRange)
            isMediumBreak -> WorkoutRationaleSpec.BreakRecovery(orderedGoals, breakRange)
            else -> WorkoutRationaleSpec.GoalFocus(orderedGoals, focusMuscles)
        }

        return GeneratedWorkout(
            title = WorkoutTitleSpec(
                split = splitType,
                emphasis = emphasisFor(goals),
                isReEntry = isLongBreak
            ),
            focusMuscles = focusMuscles,
            estimatedDurationMinutes = estimatedDuration,
            exercises = generatedExerciseList,
            rationale = rationale
        )
    }

    private fun determineSplit(
        context: WorkoutGenerationContext,
        generationIndex: Int,
        candidates: List<Exercise>
    ): WorkoutSplit {
        val highPriorityMuscles = context.musclePriorities
            .filter { it.value == PriorityLevel.HIGH }
            .keys

        val preferred = WorkoutSplit.entries.filter { split ->
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

        fun List<WorkoutSplit>.fillable() = filter { split -> candidates.any { it.trains(split) } }
        val trainable = preferred.fillable().ifEmpty { WorkoutSplit.DEFAULT_ROTATION.fillable() }
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

    private fun Exercise.trains(split: WorkoutSplit): Boolean =
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
        split: WorkoutSplit,
        candidates: List<Exercise>,
        context: WorkoutGenerationContext
    ): List<Exercise> {
        // A candidate that trains none of the split's muscles is not a substitute for one that
        // does. Widening back to the whole catalog is what used to hand a Push day whatever
        // sorted first; determineSplit has already guaranteed this split is fillable.
        val matchingCandidates = candidates.filter { it.trains(split) }
        check(matchingCandidates.isNotEmpty()) {
            "Split ${split.name} was selected without any matching candidate."
        }

        val exerciseCountTarget = when {
            context.preferredWorkoutDurationMinutes <= 35 -> 3
            context.preferredWorkoutDurationMinutes <= 55 -> 5
            else -> 6
        }
        val compoundSlots = minOf(3, exerciseCountTarget - 1)
        val reviewedEligibilityEnabled = context.automaticEligibilityResult != null
        val capabilityPenalties = capabilityPreferenceRankingPolicy.penalties(
            candidateExerciseIds = matchingCandidates.map(Exercise::id),
            automaticEligibilityResult = context.automaticEligibilityResult,
            capabilityEvidence = context.capabilityEvidence
        )

        val result = mutableListOf<Exercise>()
        result.addAll(
            chooseCompounds(
                split = split,
                candidates = matchingCandidates,
                slots = compoundSlots,
                context = context,
                reviewedEligibilityEnabled = reviewedEligibilityEnabled,
                capabilityPenalties = capabilityPenalties
            )
        )

        // Fill the remaining slots from every matching exercise. Programming metadata
        // influences ordering but never prevents an otherwise valid exercise from selection.
        val remainingSlots = exerciseCountTarget - result.size
        if (remainingSlots > 0) {
            val accessories = matchingCandidates
                .filterNot { it in result }
                .sortedWith(
                    accessoryOrder(
                        split = split,
                        context = context,
                        reviewedEligibilityEnabled = reviewedEligibilityEnabled,
                        capabilityPenalties = capabilityPenalties
                    )
                )
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
        split: WorkoutSplit,
        candidates: List<Exercise>,
        slots: Int,
        context: WorkoutGenerationContext,
        reviewedEligibilityEnabled: Boolean,
        capabilityPenalties: Map<String, Int>
    ): List<Exercise> {
        if (slots <= 0) return emptyList()
        val compounds = candidates
            .filter { it.programming?.mechanics == MechanicsType.COMPOUND }
            .sortedWith(
                compareByDescending<Exercise> { it.trainsAsPrimary(split) }
                    .thenBy { capabilityPenalties.getValue(it.id) }
                    .thenBy {
                        difficultyRankingPolicy.aboveExperiencePenalty(
                            exercise = it,
                            experienceLevel = context.experienceLevel,
                            reviewedEligibilityEnabled = reviewedEligibilityEnabled
                        )
                    }
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
    private fun accessoryOrder(
        split: WorkoutSplit,
        context: WorkoutGenerationContext,
        reviewedEligibilityEnabled: Boolean,
        capabilityPenalties: Map<String, Int>
    ): Comparator<Exercise> =
        compareByDescending<Exercise> { it.trainsAsPrimary(split) }
            .thenByDescending { it.programming?.mechanics == MechanicsType.ISOLATION }
            .thenByDescending { it.programming != null }
            .thenBy { capabilityPenalties.getValue(it.id) }
            .thenBy {
                difficultyRankingPolicy.aboveExperiencePenalty(
                    exercise = it,
                    experienceLevel = context.experienceLevel,
                    reviewedEligibilityEnabled = reviewedEligibilityEnabled
                )
            }
            .thenByDescending { it.programming?.fatigueScore ?: 0 }
            .thenBy { it.id }

    private fun Exercise.trainsAsPrimary(split: WorkoutSplit): Boolean =
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

    private fun emphasisFor(goals: Set<FitnessGoal>): WorkoutEmphasis = when {
        FitnessGoal.STRENGTH in goals && FitnessGoal.BUILD_MUSCLE in goals ->
            WorkoutEmphasis.POWER_AND_HYPERTROPHY
        FitnessGoal.STRENGTH in goals && FitnessGoal.ATHLETIC_PERFORMANCE in goals ->
            WorkoutEmphasis.POWER_AND_PERFORMANCE
        FitnessGoal.FAT_LOSS in goals && FitnessGoal.ATHLETIC_PERFORMANCE in goals ->
            WorkoutEmphasis.ATHLETIC_CONDITIONING
        FitnessGoal.BUILD_MUSCLE in goals && FitnessGoal.FAT_LOSS in goals ->
            WorkoutEmphasis.HYPERTROPHY_AND_DEFINITION
        FitnessGoal.BUILD_MUSCLE in goals -> WorkoutEmphasis.HYPERTROPHY
        FitnessGoal.STRENGTH in goals -> WorkoutEmphasis.POWER_AND_STRENGTH
        FitnessGoal.ATHLETIC_PERFORMANCE in goals -> WorkoutEmphasis.AGILITY_AND_EXPLOSIVENESS
        FitnessGoal.FAT_LOSS in goals -> WorkoutEmphasis.HIGH_DENSITY_CIRCUIT
        FitnessGoal.GENERAL_FITNESS in goals -> WorkoutEmphasis.ATHLETIC_FOUNDATION
        else -> WorkoutEmphasis.CONDITIONING
    }

    private fun extractFocusMuscles(exercises: List<Exercise>): List<String> {
        return exercises
            .flatMap { it.primaryMuscles }
            .distinct()
            .take(3)
    }

}
