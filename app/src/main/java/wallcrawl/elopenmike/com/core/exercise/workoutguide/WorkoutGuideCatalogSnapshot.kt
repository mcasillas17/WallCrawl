package wallcrawl.elopenmike.com.core.exercise.workoutguide

import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.model.Exercise

/**
 * One validated, immutable view of the bundled Workout Guide import.
 * Catalog facts and their visual frames are loaded together so they cannot drift.
 */
data class WorkoutGuideCatalogSnapshot(
    val exercises: List<Exercise>,
    val framesByExerciseId: Map<String, List<ExerciseVisual>>
)

interface WorkoutGuideCatalogSource {
    suspend fun snapshot(): WorkoutGuideCatalogSnapshot

    /** Returns the cached snapshot without performing asset I/O. */
    fun currentSnapshot(): WorkoutGuideCatalogSnapshot?
}
