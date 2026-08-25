package wallcrawl.elopenmike.com.core.exercise

import wallcrawl.elopenmike.com.core.model.Exercise
import kotlinx.coroutines.flow.Flow

/**
 * Clean catalog interface abstracting the exercise library.
 * This allows swapping in-memory sample data with bundled Workout Guide JSON/SVG manifests
 * or remote updates without modifying domain or UI logic.
 */
interface ExerciseCatalog {
    /**
     * Observe all available exercises in the catalog.
     */
    fun getAllExercises(): Flow<List<Exercise>>

    /**
     * Retrieve an exercise by its unique ID.
     */
    suspend fun getExerciseById(id: String): Exercise?

    /**
     * Search exercises by query string, filtering on name, muscles, and equipment.
     */
    fun searchExercises(
        query: String = "",
        muscle: String? = null,
        equipment: String? = null
    ): Flow<List<Exercise>>

    /**
     * Get a list of all distinct primary muscle groups available in the catalog.
     */
    suspend fun getMuscleGroups(): List<String>

    /**
     * Get a list of all distinct equipment types required across the catalog.
     */
    suspend fun getEquipmentTypes(): List<String>
}
