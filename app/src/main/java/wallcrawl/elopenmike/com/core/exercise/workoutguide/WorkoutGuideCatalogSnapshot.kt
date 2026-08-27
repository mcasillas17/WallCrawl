package wallcrawl.elopenmike.com.core.exercise.workoutguide

import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseAttribution

/**
 * One validated, immutable view of the bundled Workout Guide import.
 * Catalog facts and their visual frames are loaded together so they cannot drift.
 */
data class WorkoutGuideCatalogSnapshot(
    val exercises: List<Exercise>,
    val framesByExerciseId: Map<String, List<ExerciseVisual>>,
    val catalogAttribution: CatalogAttribution
)

/**
 * Provenance of the bundled catalog, shown in the in-app credits screen.
 * The CC BY-SA 4.0 licence on the bundled artwork requires this to reach the user, not
 * just the repository, so it is carried through the snapshot rather than dropped at parse.
 */
data class CatalogAttribution(
    val repository: String,
    val commit: String,
    val assetLicense: String,
    val attribution: ExerciseAttribution,
    val exerciseCount: Int,
    val frameCount: Int
)

interface WorkoutGuideCatalogSource {
    suspend fun snapshot(): WorkoutGuideCatalogSnapshot

    /** Returns the cached snapshot without performing asset I/O. */
    fun currentSnapshot(): WorkoutGuideCatalogSnapshot?
}
