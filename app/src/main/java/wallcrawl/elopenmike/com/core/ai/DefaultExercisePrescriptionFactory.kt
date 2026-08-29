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
        val isCompound = exercise.programming?.mechanics == MechanicsType.COMPOUND
        val targetSets = when (context.fitnessGoal) {
            FitnessGoal.STRENGTH -> if (isCompound) 4 else 3
            FitnessGoal.ATHLETIC_PERFORMANCE -> 4
            else -> 3
        }
        val repRange = when (context.fitnessGoal) {
            FitnessGoal.STRENGTH -> if (isCompound) RepRange(4, 6) else RepRange(6, 8)
            FitnessGoal.BUILD_MUSCLE ->
                exercise.programming?.recommendedRepRange ?: RepRange(8, 12)
            FitnessGoal.GENERAL_FITNESS -> RepRange(10, 12)
            FitnessGoal.FAT_LOSS -> RepRange(12, 15)
            FitnessGoal.ATHLETIC_PERFORMANCE -> RepRange(5, 8)
        }

        return ExercisePrescription(
            exerciseType = exercise.type,
            targetSets = targetSets,
            repRange = repRange,
            targetWeight = suggestedTargetWeight(exercise, context, repRange.max),
            restSeconds = if (context.fitnessGoal == FitnessGoal.STRENGTH) 120 else 90
        )
    }

    private fun createBodyweightRepetitionPrescription(
        exercise: Exercise,
        context: WorkoutGenerationContext
    ): ExercisePrescription {
        val repRange = when (context.fitnessGoal) {
            FitnessGoal.STRENGTH -> RepRange(6, 10)
            FitnessGoal.BUILD_MUSCLE ->
                exercise.programming?.recommendedRepRange ?: RepRange(8, 15)
            FitnessGoal.GENERAL_FITNESS -> RepRange(10, 15)
            FitnessGoal.FAT_LOSS -> RepRange(12, 20)
            FitnessGoal.ATHLETIC_PERFORMANCE -> RepRange(6, 12)
        }
        return ExercisePrescription(
            exerciseType = exercise.type,
            targetSets = if (context.fitnessGoal == FitnessGoal.ATHLETIC_PERFORMANCE) 4 else 3,
            repRange = repRange,
            restSeconds = if (context.fitnessGoal == FitnessGoal.STRENGTH) 120 else 75
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
        return context.userProfile.confirmedStartingLoads[exercise.id]
    }
}
