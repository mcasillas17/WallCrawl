package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

/**
 * Creates conservative, structurally valid targets for any catalog exercise.
 * Reviewed programming metadata enriches these defaults but is never an eligibility gate.
 */
class DefaultExercisePrescriptionFactory {

    fun create(
        exercise: Exercise,
        context: WorkoutGenerationContext
    ): ExercisePrescription = when (exercise.type) {
        ExerciseType.WEIGHT_REPS -> createWeightRepetitionPrescription(exercise, context)
        ExerciseType.BODYWEIGHT_REPS -> createBodyweightRepetitionPrescription(exercise, context)
        ExerciseType.ASSISTED_BODYWEIGHT -> ExercisePrescription(
            exerciseType = exercise.type,
            targetSets = 3,
            repRange = RepRange(6, 10),
            restSeconds = 90
        )

        ExerciseType.DURATION -> ExercisePrescription(
            exerciseType = exercise.type,
            targetSets = if (exercise.isStretch) 1 else 3,
            targetDurationSeconds = if (exercise.isStretch) 30 else 45,
            restSeconds = if (exercise.isStretch) 15 else 45
        )

        ExerciseType.DISTANCE_DURATION -> ExercisePrescription(
            exerciseType = exercise.type,
            targetSets = 1,
            targetDurationSeconds = 600,
            restSeconds = 0
        )
    }

    private fun createWeightRepetitionPrescription(
        exercise: Exercise,
        context: WorkoutGenerationContext
    ): ExercisePrescription {
        val goals = context.fitnessGoals.ifEmpty { setOf(context.fitnessGoal) }
        val isCompound = exercise.programming?.mechanics == MechanicsType.COMPOUND
        val breakWeeks = context.userProfile.returningAfterBreakWeeks

        val baseSets = when {
            FitnessGoal.STRENGTH in goals -> if (isCompound) 4 else 3
            FitnessGoal.ATHLETIC_PERFORMANCE in goals -> 4
            else -> 3
        }
        val targetSets = when {
            breakWeeks >= 52 -> 2 // 1+ years break: strict 2 working sets to protect connective tissue
            breakWeeks >= 4 -> if (isCompound) minOf(baseSets, 3) else 2 // 1-12 months break
            else -> baseSets
        }

        val repRange = when {
            breakWeeks >= 52 && FitnessGoal.STRENGTH in goals && isCompound -> RepRange(6, 8)
            FitnessGoal.STRENGTH in goals && isCompound -> RepRange(4, 6)
            FitnessGoal.BUILD_MUSCLE in goals ->
                exercise.programming?.recommendedRepRange ?: RepRange(8, 12)
            FitnessGoal.STRENGTH in goals -> RepRange(6, 8)
            FitnessGoal.ATHLETIC_PERFORMANCE in goals -> RepRange(5, 8)
            FitnessGoal.GENERAL_FITNESS in goals -> RepRange(10, 12)
            FitnessGoal.FAT_LOSS in goals -> RepRange(12, 15)
            else -> RepRange(8, 12)
        }
        val restSeconds = when {
            FitnessGoal.STRENGTH in goals && isCompound -> 120
            FitnessGoal.FAT_LOSS in goals && !isCompound -> 60
            FitnessGoal.STRENGTH in goals -> 90
            FitnessGoal.ATHLETIC_PERFORMANCE in goals -> 90
            else -> 90
        }

        return ExercisePrescription(
            exerciseType = exercise.type,
            targetSets = targetSets,
            repRange = repRange,
            targetWeight = suggestedTargetWeight(exercise, context, repRange.max),
            restSeconds = restSeconds
        )
    }

    private fun createBodyweightRepetitionPrescription(
        exercise: Exercise,
        context: WorkoutGenerationContext
    ): ExercisePrescription {
        val goals = context.fitnessGoals.ifEmpty { setOf(context.fitnessGoal) }
        val isCompound = exercise.programming?.mechanics == MechanicsType.COMPOUND
        val breakWeeks = context.userProfile.returningAfterBreakWeeks

        val baseSets = if (FitnessGoal.ATHLETIC_PERFORMANCE in goals || (FitnessGoal.STRENGTH in goals && isCompound)) 4 else 3
        val targetSets = when {
            breakWeeks >= 52 -> 2 // 1+ years break: 2 working sets
            breakWeeks >= 4 -> minOf(baseSets, 3)
            else -> baseSets
        }

        val repRange = when {
            breakWeeks >= 52 && FitnessGoal.STRENGTH in goals && isCompound -> RepRange(8, 12)
            FitnessGoal.STRENGTH in goals && isCompound -> RepRange(6, 10)
            FitnessGoal.BUILD_MUSCLE in goals ->
                exercise.programming?.recommendedRepRange ?: RepRange(8, 15)
            FitnessGoal.STRENGTH in goals -> RepRange(6, 10)
            FitnessGoal.ATHLETIC_PERFORMANCE in goals -> RepRange(6, 12)
            FitnessGoal.GENERAL_FITNESS in goals -> RepRange(10, 15)
            FitnessGoal.FAT_LOSS in goals -> RepRange(12, 20)
            else -> RepRange(8, 15)
        }
        val restSeconds = if (FitnessGoal.STRENGTH in goals) 120 else 75
        return ExercisePrescription(
            exerciseType = exercise.type,
            targetSets = targetSets,
            repRange = repRange,
            restSeconds = restSeconds
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

        // No performance history exists yet: only a load the user explicitly confirmed
        // during onboarding is safe to prescribe. Never invent a starting number.
        // Repository validation already rejects malformed values before persistence, but
        // a directly constructed context must not be trusted to propagate NaN/negative.
        return context.userProfile.confirmedStartingLoads[exercise.id]
            ?.takeIf { it.isFinite() && it >= 0.0 }
    }
}
