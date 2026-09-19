package wallcrawl.elopenmike.com.core.ai

import java.security.MessageDigest
import java.util.Locale
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.ProgressionDecision
import wallcrawl.elopenmike.com.core.model.ProgressionReason
import wallcrawl.elopenmike.com.core.model.ProgressionReasonCode
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutOrigin
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.convertWeight

/**
 * Sufficient history for the factory's all-completed-sets comparison, shared with identity.
 * Null means no completed sets; missing reps retain the existing comparison-only zero.
 */
internal fun ExercisePerformanceHistory.minimumCompletedReps(): Int? =
    recentSets.asSequence().filter { it.isCompleted }.minOfOrNull { it.completedReps ?: 0 }

/** A recorded working value is a source, not permission to add a progression step. */
internal fun WorkoutGenerationContext.recordedWorkLoad(exerciseId: String, type: ExerciseType): Double? {
    if (type != ExerciseType.WEIGHT_REPS && type != ExerciseType.ASSISTED_BODYWEIGHT) return null
    val sessions = recentWorkoutHistory.ifEmpty { progressionHistory }
    ProgressionEngine.requireBounded(sessions)
    return sessions.asSequence()
        .filter { it.status == SessionStatus.COMPLETED &&
            it.completedAtTimestamp?.let { time -> time > 0 && time <= historyAsOfTimestamp } == true }
        .sortedWith(compareByDescending<wallcrawl.elopenmike.com.core.model.WorkoutSession> {
            it.completedAtTimestamp
        }.thenBy { it.id })
        .flatMap { session ->
            session.exercises.filter { it.exerciseId == exerciseId && it.prescription.exerciseType == type }
                .flatMap { it.sets }.sortedByDescending { it.setNumber }.asSequence()
                .filter { it.isCompleted && it.type != SetType.WARMUP && it.exerciseType == type &&
                    (it.completedReps ?: 0) > 0 && it.stopReason == null && it.stoppedAtTimestamp == null &&
                    (it.completedAtTimestamp?.let { time -> time > 0 && time <= historyAsOfTimestamp } ?: true) &&
                    it.completedDurationSeconds == null && it.completedDistanceMeters == null &&
                    (if (type == ExerciseType.WEIGHT_REPS) it.completedAssistanceWeight == null else it.completedWeight == null)
                }
                .mapNotNull { set ->
                    (if (type == ExerciseType.WEIGHT_REPS) set.completedWeight else set.completedAssistanceWeight)
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                        ?.let { convertWeight(it, session.weightUnit, preferredUnits) }
                }
        }.firstOrNull()
}

private fun Double?.representableTargetLoad(): Double? =
    this?.takeIf { it.isFinite() && it in 0.0..ExercisePrescription.MAX_WEIGHT }

/**
 * Shares manual defaults while compiling versioned progression/deload on the reviewed path.
 */
class DefaultExercisePrescriptionFactory(
    private val stateBasedTrainingPolicy: StateBasedTrainingPolicy =
        StateBasedTrainingPolicy()
) {

    fun create(
        exercise: Exercise,
        context: WorkoutGenerationContext
    ): ExercisePrescription = createDecision(exercise, context).prescription

    fun createDecision(exercise: Exercise, context: WorkoutGenerationContext): ProgressionDecision {
        val ordinary = createGuidedBase(exercise, context)
        val basis = baseConfigurationDigest(exercise, context, ordinary)
        fun hold(reference: ExercisePrescription, reason: ProgressionReason, result: ExercisePrescription = reference) =
            ProgressionDecision(exercise.id, reason, null, reference, result, emptyList(), basis)
        if (context.trainingProgramState == null) return hold(ordinary, ProgressionReason.HOLD_LEGACY_PATH)
        ProgressionEngine.requireBounded(context.progressionHistory)
        val sourceLoad = context.recordedWorkLoad(exercise.id, exercise.type)
            ?: context.userProfile.confirmedStartingLoads[exercise.id].takeIf {
                exercise.type == ExerciseType.WEIGHT_REPS
            }
        val sourceOutsideTargetBounds = sourceLoad != null && sourceLoad.representableTargetLoad() == null
        val latest = context.progressionHistory
            .filter { it.status == SessionStatus.COMPLETED &&
                (it.completedAtTimestamp ?: Long.MAX_VALUE) <= context.historyAsOfTimestamp &&
                it.exercises.count { ex -> ex.exerciseId == exercise.id } == 1
            }
            .sortedWith(compareByDescending<wallcrawl.elopenmike.com.core.model.WorkoutSession> {
                it.startedAtTimestamp
            }.thenBy { it.id }).firstOrNull()
        val previous = latest?.let { session ->
            context.recentRecommendationRecords[session.id]?.let { record ->
                ProgressionReasonCode.decode(record.reasonCodes).firstOrNull { it.exerciseId == exercise.id }
            }
        }
        val latestPrescription = latest?.exercises?.single { it.exerciseId == exercise.id }?.prescription
        val reference = if (previous?.baseConfigurationDigest == basis &&
            latest.origin == WorkoutOrigin.PLANNER &&
            latestPrescription?.exerciseType == ordinary.exerciseType
        ) {
            when (ordinary.exerciseType) {
                ExerciseType.BODYWEIGHT_REPS -> ordinary.copy(repRange = latestPrescription.repRange)
                ExerciseType.DURATION -> ordinary.copy(targetDurationSeconds = latestPrescription.targetDurationSeconds)
                else -> ordinary
            }
        } else ordinary
        if (context.deloadPreferences?.let(DeloadOfferPolicy::accepted) != null) {
            return hold(
                reference, ProgressionReason.HOLD_ACCEPTED_DELOAD,
                reference.copy(targetSets = maxOf(1, reference.targetSets - 1))
            )
        }
        if (sourceOutsideTargetBounds) return hold(reference, ProgressionReason.HOLD_AT_BOUND)
        if (context.userProfile.returningAfterBreakWeeks > 0 ||
            context.trainingProgramState.adaptationState == AdaptationState.RETURNING
        ) return hold(reference, ProgressionReason.HOLD_RETURNING_GUIDANCE)
        if (previous != null && previous.baseConfigurationDigest != basis) {
            return hold(reference, ProgressionReason.HOLD_CONFIGURATION_CHANGED)
        }
        if (exercise.type == ExerciseType.DISTANCE_DURATION || !exercise.isStrengthWork()) {
            return hold(reference, ProgressionReason.HOLD_UNSUPPORTED_AUTOMATIC_SHAPE)
        }
        return ProgressionEngine().evaluate(
            exercise.id, reference, context.progressionHistory, context.preferredUnits,
            context.historyAsOfTimestamp, basis
        )
    }

    private fun createGuidedBase(exercise: Exercise, context: WorkoutGenerationContext): ExercisePrescription {
        val basePrescription = when (exercise.type) {
            ExerciseType.WEIGHT_REPS -> createWeightRepetitionPrescription(exercise, context)
            ExerciseType.BODYWEIGHT_REPS ->
                createBodyweightRepetitionPrescription(exercise, context)

            ExerciseType.ASSISTED_BODYWEIGHT -> ExercisePrescription(
                exerciseType = exercise.type,
                targetSets = 3,
                repRange = RepRange(6, 10),
                targetAssistanceWeight = if (context.trainingProgramState != null) {
                    context.recordedWorkLoad(exercise.id, ExerciseType.ASSISTED_BODYWEIGHT).representableTargetLoad()
                } else null,
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
        val programState = context.trainingProgramState ?: return basePrescription
        return when (
            val result = stateBasedTrainingPolicy.evaluate(
                exercise = exercise,
                basePrescription = basePrescription,
                profile = context.userProfile,
                fitnessGoals = context.fitnessGoals.ifEmpty { setOf(context.fitnessGoal) },
                programState = programState,
                priorUserRestPreference = context.priorUserRestPreferences[exercise.id]
            )
        ) {
            is TrainingPolicyResult.Applied -> result.prescription
            is TrainingPolicyResult.NoGuidance,
            is TrainingPolicyResult.Failure -> throw TrainingPolicyResultException(result)
        }
    }

    private fun baseConfigurationDigest(
        exercise: Exercise,
        context: WorkoutGenerationContext,
        ordinary: ExercisePrescription
    ): String {
        val metadata = exercise.acceptedMetadata()
        val text = listOf(
            ProgressionDecision.VERSION, stateBasedTrainingPolicy.policyVersion.name,
            exercise.id, context.catalogVersion.orEmpty(),
            metadata?.provenance?.schemaVersion.toString(), metadata?.provenance?.policyVersion.toString(),
            metadata?.aiReviewProvenance?.reviewedContentSha256.orEmpty(),
            metadata?.provenance?.reviewedAtEpochMillis.toString(),
            context.fitnessGoals.ifEmpty { setOf(context.fitnessGoal) }.map { it.name }.sorted().joinToString(","),
            context.userProfile.returningAfterBreakWeeks.toString(),
            ordinary.exerciseType.name, ordinary.repRange.toString(),
            ordinary.targetDurationSeconds.toString(), ordinary.targetDistanceMeters.toString()
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
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
            breakWeeks >= 52 -> 2 // Versioned product cap for a reported long break.
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
        val confirmed = context.userProfile.confirmedStartingLoads[exercise.id]
            ?.takeIf { it.isFinite() && it >= 0.0 }
        if (context.trainingProgramState != null) {
            val recorded = context.recordedWorkLoad(exercise.id, ExerciseType.WEIGHT_REPS)
            return if (recorded != null) recorded.representableTargetLoad() else confirmed.representableTargetLoad()
        }
        val priorPerformance = context.exerciseHistory[exercise.id]
        val priorWeight = priorPerformance?.lastWeight
        if (priorWeight != null && priorWeight.isFinite() && priorWeight >= 0.0) {
            val reachedTopOfRange = priorPerformance.minimumCompletedReps()
                ?.let { it >= targetRepMaximum } == true
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
        // during onboarding may be used. Never invent a starting number.
        // Repository validation already rejects malformed values before persistence, but
        // a directly constructed context must not be trusted to propagate NaN/negative.
        return confirmed
    }
}
