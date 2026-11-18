package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ConditionPlanningResult
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.PrescriptionPurpose
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.clearsJointSensitivities
import wallcrawl.elopenmike.com.core.model.hasRequiredEquipment

/** Small catalog allowlist for general training phases. Condition prescriptions have their own rules. */
internal object WorkoutPhasePolicy {
    const val VERSION = "workout-phases-v1"

    private data class Movement(
        val purpose: PrescriptionPurpose,
        val type: ExerciseType,
        val capability: MovementCapabilityType? = null,
        val sensitiveJoints: Set<TrainingConstraint> = emptySet()
    )

    private val movements = buildMap {
        fun warmup(id: String, capability: MovementCapabilityType? = null, activation: Boolean = false) {
            put(id, Movement(PrescriptionPurpose.WARMUP,
                if (activation) ExerciseType.BODYWEIGHT_REPS else ExerciseType.DURATION, capability))
        }
        fun cooldown(id: String, vararg joints: TrainingConstraint, floor: Boolean = false) {
            put(id, Movement(PrescriptionPurpose.COOLDOWN, ExerciseType.DURATION,
                MovementCapabilityType.FLOOR_TRANSITION.takeIf { floor }, joints.toSet()))
        }
        warmup("arm-circles")
        warmup("leg-swings-stretch", MovementCapabilityType.BALANCE_WITHOUT_SUPPORT)
        warmup("cat-cow-stretch", MovementCapabilityType.FLOOR_TRANSITION)
        warmup("torso-twist-stretch")
        warmup("band-pull-apart", MovementCapabilityType.BALANCE_WITHOUT_SUPPORT, activation = true)
        warmup("glute-bridge", MovementCapabilityType.FLOOR_TRANSITION, activation = true)
        warmup("dead-bug", MovementCapabilityType.FLOOR_TRANSITION, activation = true)
        cooldown("cross-body-shoulder-stretch", TrainingConstraint.SHOULDER_SENSITIVE)
        cooldown("doorway-chest-stretch", TrainingConstraint.SHOULDER_SENSITIVE)
        cooldown("hamstring-stretch", TrainingConstraint.HIP_SENSITIVE, TrainingConstraint.KNEE_SENSITIVE,
            TrainingConstraint.LOWER_BACK_SENSITIVE)
        cooldown("childs-pose", TrainingConstraint.KNEE_SENSITIVE, floor = true)
        cooldown("kneeling-hip-flexor-stretch", TrainingConstraint.HIP_SENSITIVE, TrainingConstraint.KNEE_SENSITIVE, floor = true)
    }

    fun isCatalogCandidate(exercise: Exercise): Boolean = movements[exercise.id]?.type == exercise.type

    fun eligible(exercise: Exercise, profile: UserProfile): Boolean {
        val movement = movements[exercise.id] ?: return false
        val restrictions = profile.demandRestrictions
        return isCatalogCandidate(exercise) &&
            profile.activeConditions.isEmpty() && profile.unresolvedTrainingConstraintsRaw == null &&
            exercise.id !in profile.planningExclusions && exercise.hasRequiredEquipment(profile.availableEquipment) &&
            restrictions.permitsReportDemands(exercise.id) && !restrictions.ranksLower(exercise.id) &&
            clearsJointSensitivities(exercise.id, profile, emptySet()) &&
            movement.sensitiveJoints.none { it in profile.trainingConstraints } &&
            (listOfNotNull(movement.capability) + exercise.acceptedMetadata()?.capabilityRequirements.orEmpty()).none {
                profile.movementCapabilities[it] in setOf(CapabilityLevel.AVOID, CapabilityLevel.LIMITED)
            }
    }

    fun prescription(exercise: Exercise): ExercisePrescription {
        val movement = movements.getValue(exercise.id)
        return ExercisePrescription(
            exerciseType = exercise.type,
            targetSets = 1,
            repRange = RepRange(6, 6).takeIf { exercise.type == ExerciseType.BODYWEIGHT_REPS },
            targetDurationSeconds = when {
                exercise.type != ExerciseType.DURATION -> null
                movement.purpose == PrescriptionPurpose.WARMUP -> 60
                else -> 30
            },
            restSeconds = 0,
            purpose = movement.purpose
        )
    }

    fun select(context: WorkoutGenerationContext, work: List<Exercise>, seconds: Long): List<PlannedExercise> {
        if (context.conditionPlanning !is ConditionPlanningResult.NotApplicable || seconds <= 0) return emptyList()
        val usedIds = work.mapTo(hashSetOf(), Exercise::id)
        val primary = work.flatMap { it.primaryMuscles }.toSet()
        val secondary = work.flatMap { it.secondaryMuscles }.toSet() - primary
        val goals = context.fitnessGoals.ifEmpty { setOf(context.fitnessGoal) }
        val preferActivation = FitnessGoal.STRENGTH in goals || FitnessGoal.BUILD_MUSCLE in goals
        fun relevance(exercise: Exercise) =
            exercise.primaryMuscles.count(primary::contains) * 4 +
                exercise.secondaryMuscles.count(primary::contains) * 2 +
                exercise.primaryMuscles.count(secondary::contains) * 2 +
                exercise.secondaryMuscles.count(secondary::contains)
        val ranked = context.phaseExercises.filter { it.id !in usedIds && relevance(it) > 0 && eligible(it, context.userProfile) }
            .sortedWith(compareByDescending(::relevance).thenBy { (it.type == ExerciseType.BODYWEIGHT_REPS) != preferActivation }.thenBy(Exercise::id))
            .map { PlannedExercise(it.id, prescription(it)) }
        fun cost(exercise: PlannedExercise) = WorkoutDurationEstimator.estimateSeconds(listOf(exercise))
        val warmups = ranked.filter { it.prescription.purpose == PrescriptionPurpose.WARMUP }
        val cooldown = ranked.firstOrNull { it.prescription.purpose == PrescriptionPurpose.COOLDOWN && cost(it) <= seconds }
        val warmup = warmups.firstOrNull { cost(it) + (cooldown?.let(::cost) ?: 0) <= seconds }
            ?: warmups.firstOrNull { cost(it) <= seconds }
        return listOfNotNull(warmup, cooldown?.takeIf { cost(it) + (warmup?.let(::cost) ?: 0) <= seconds })
    }
}
