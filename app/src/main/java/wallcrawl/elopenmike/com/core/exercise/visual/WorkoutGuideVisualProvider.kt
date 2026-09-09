package wallcrawl.elopenmike.com.core.exercise.visual

import java.util.Locale
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.model.IllustrationVariant

/**
 * Resolves WallCrawl exercise IDs to the pinned Workout Guide catalog bundled in assets.
 * Raw upstream paths remain confined to this integration boundary.
 */
class WorkoutGuideVisualProvider(
    private val source: WorkoutGuideCatalogSource,
    private val illustrations: IllustrationCatalog? = null
) : ExerciseVisualProvider {

    override fun framesFor(exerciseId: String): List<ExerciseVisual> =
        source.currentSnapshot()
            ?.framesByExerciseId
            ?.get(exerciseId.trim().lowercase(Locale.ROOT))
            .orEmpty()

    override suspend fun framesFor(exerciseId: String, variant: IllustrationVariant): List<ExerciseVisual> {
        val originals = framesFor(exerciseId)
        if (originals.isEmpty()) return emptyList()
        val id = exerciseId.trim().lowercase(Locale.ROOT)
        val paths = illustrations?.framesFor(id, variant).orEmpty()
        // A whole sequence falls back together, so an incomplete variant never alternates people.
        return if (paths.size == 3) paths.mapIndexed { index, path ->
            originals.getOrElse(index) { originals.first() }.copy(assetPath = path)
        } else originals
    }
}
