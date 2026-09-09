package wallcrawl.elopenmike.com.core.exercise

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.equipmentRequirements
import wallcrawl.elopenmike.com.core.model.isEquipmentSatisfiedBy
import wallcrawl.elopenmike.com.core.model.normalizedEquipmentSet

/**
 * Filter layer that enforces hard constraints on the exercise library before
 * passing candidate exercises to the planner.
 *
 * Every planner sees only what survives this stage, so an exercise the user cannot perform
 * (missing equipment, explicitly excluded) can never be chosen by any of them.
 */
class ExerciseFilter {

    /**
     * Filters a list of exercises based on user profile constraints:
     * 1. Explicit equipment availability, including fixed-anchor setup corrections
     * 2. User excluded exercise IDs
     * 3. Target muscle focus (optional)
     */
    fun filterCandidates(
        allExercises: List<Exercise>,
        profile: UserProfile,
        targetMuscles: List<String>? = null
    ): List<Exercise> {
        val ownedEquipment = profile.availableEquipment.normalizedEquipmentSet()
        val excludedIds = profile.excludedExerciseIds.toSet()

        return allExercises.filter { exercise ->
            // 1. Check exclusions
            if (exercise.id in excludedIds) {
                return@filter false
            }

            // 2. Any complete alternative suffices; unresolved setups have no alternatives.
            if (!exercise.equipmentRequirements.isEquipmentSatisfiedBy(ownedEquipment)) {
                return@filter false
            }

            // 3. Check target muscle focus if specified
            if (!targetMuscles.isNullOrEmpty()) {
                val matchesMuscle = exercise.primaryMuscles.any { pm ->
                    targetMuscles.any { tm -> tm.equals(pm, ignoreCase = true) }
                } || exercise.secondaryMuscles.any { sm ->
                    targetMuscles.any { tm -> tm.equals(sm, ignoreCase = true) }
                }
                if (!matchesMuscle) {
                    return@filter false
                }
            }

            true
        }
    }
}
