package wallcrawl.elopenmike.com.core.exercise.visual

import java.util.Locale
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource

/**
 * Resolves WallCrawl exercise IDs to the pinned Workout Guide catalog bundled in assets.
 * Raw upstream paths remain confined to this integration boundary.
 */
class WorkoutGuideVisualProvider(
    private val source: WorkoutGuideCatalogSource
) : ExerciseVisualProvider {

    override fun framesFor(exerciseId: String): List<ExerciseVisual> =
        source.currentSnapshot()
            ?.framesByExerciseId
            ?.get(exerciseId.trim().lowercase(Locale.ROOT))
            .orEmpty()
}
