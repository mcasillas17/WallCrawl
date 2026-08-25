package wallcrawl.elopenmike.com.core.exercise

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * Filter layer that enforces hard constraints on the exercise library before
 * passing candidate exercises to the AI / planner.
 *
 * This ensures the LLM never sees or chooses exercises the user cannot perform
 * (e.g. missing equipment or user-excluded exercises).
 */
class ExerciseFilter {

    /**
     * Filters a list of exercises based on user profile constraints:
     * 1. Equipment availability (exercise requires only equipment the user owns or bodyweight)
     * 2. User excluded exercise IDs
     * 3. Target muscle focus (optional)
     */
    fun filterCandidates(
        allExercises: List<Exercise>,
        profile: UserProfile,
        targetMuscles: List<String>? = null
    ): List<Exercise> {
        val ownedEquipment = profile.availableEquipment.toSet() + setOf(StandardEquipment.BODYWEIGHT)
        val excludedIds = profile.excludedExerciseIds.toSet()

        return allExercises.filter { exercise ->
            // 1. Check exclusions
            if (exercise.id in excludedIds) {
                return@filter false
            }

            // 2. Check equipment requirements (all required equipment must be available)
            val hasRequiredEquipment = exercise.equipment.isEmpty() ||
                exercise.equipment.all { eq -> eq in ownedEquipment }
            if (!hasRequiredEquipment) {
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
