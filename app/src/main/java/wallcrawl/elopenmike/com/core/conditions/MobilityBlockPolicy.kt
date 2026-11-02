package wallcrawl.elopenmike.com.core.conditions

import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.equipmentRequirements
import wallcrawl.elopenmike.com.core.model.isEquipmentSatisfiedBy
import wallcrawl.elopenmike.com.core.model.normalizedEquipmentSet

/**
 * The short mobility routine shown ahead of a session for a reported condition.
 *
 * Deterministic and independent of the main selection: the same routines, seed and profile
 * give the same block, and an empty block (no conditions, no overlay, or nothing left after
 * filtering) never fails a plan. Only conditions contribute routines, never joint labels.
 * For an item shared by several conditions, the first condition's dose wins by sorted id.
 */
object MobilityBlockPolicy {
    const val MAX_ITEMS = 3

    /**
     * Routine entries of the selected conditions, in stable id order then authored order,
     * without repeats. Rotation, filtering and the limit are applied by [select].
     */
    fun routineEntries(restrictions: ConditionRestrictions): List<RoutineEntry> {
        if (restrictions !is ConditionRestrictions.Active) return emptyList()
        return restrictions.conditionIds.sorted()
            .flatMap { restrictions.guidance.conditions[it]?.routine.orEmpty() }
            .distinctBy(RoutineEntry::exerciseId)
    }

    /** Whether [exerciseId] carries a demand some selected condition says to avoid. */
    fun isAvoided(restrictions: ConditionRestrictions, exerciseId: String): Boolean =
        restrictions is ConditionRestrictions.Active &&
            restrictions.guidance.exerciseDemands[exerciseId].orEmpty().any { it in restrictions.avoid }

    fun select(
        restrictions: ConditionRestrictions,
        exercises: Map<String, Exercise>,
        profile: UserProfile,
        mainExerciseIds: Set<String>,
        seed: Long
    ): List<PlannedExercise> {
        val entries = routineEntries(restrictions)
        if (entries.isEmpty()) return emptyList()
        val owned = profile.availableEquipment.normalizedEquipmentSet()
        val excluded = profile.excludedExerciseIds.toSet()
        val start = seed.mod(entries.size.toLong()).toInt()
        // Filter before taking, so a removed item does not shrink the block needlessly.
        return (entries.drop(start) + entries.take(start))
            .filter { entry ->
                val exercise = exercises[entry.exerciseId]
                exercise != null &&
                    exercise.type == entry.expectedType() &&
                    !(entry.requiresFloorTransition &&
                        profile.movementCapabilities[MovementCapabilityType.FLOOR_TRANSITION] ==
                        CapabilityLevel.AVOID) &&
                    entry.exerciseId !in excluded &&
                    entry.exerciseId !in mainExerciseIds &&
                    exercise.equipmentRequirements.isEquipmentSatisfiedBy(owned) &&
                    !isAvoided(restrictions, entry.exerciseId)
            }
            .take(MAX_ITEMS)
            .map(::planned)
    }

    private fun RoutineEntry.expectedType() =
        if (targetDurationSeconds != null) ExerciseType.DURATION else ExerciseType.BODYWEIGHT_REPS

    private fun planned(entry: RoutineEntry): PlannedExercise {
        // 0 means the overlay publishes no rest, which is not the same as a zero-second rest.
        val rest = entry.restSeconds.takeIf { it > 0 } ?: ExercisePrescription.DEFAULT_REST_SECONDS
        val prescription = if (entry.targetDurationSeconds != null) {
            ExercisePrescription(
                exerciseType = ExerciseType.DURATION,
                targetSets = entry.targetSets,
                targetDurationSeconds = entry.targetDurationSeconds,
                restSeconds = rest
            )
        } else {
            val reps = checkNotNull(entry.targetReps) { "A routine entry needs reps or a duration." }
            ExercisePrescription(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                targetSets = entry.targetSets,
                repRange = RepRange(reps, reps),
                restSeconds = rest,
                holdSeconds = entry.holdSeconds
            )
        }
        return PlannedExercise(entry.exerciseId, prescription)
    }
}
