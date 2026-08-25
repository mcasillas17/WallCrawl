package wallcrawl.elopenmike.com.core.exercise.visual

/**
 * Resolves WallCrawl exercise IDs to the pinned Workout Guide subset bundled in assets.
 * Raw upstream paths remain confined to this integration boundary.
 */
class WorkoutGuideVisualProvider : ExerciseVisualProvider {

    override fun framesFor(exerciseId: String): List<ExerciseVisual> {
        val workoutGuideId = WORKOUT_GUIDE_IDS[exerciseId] ?: return emptyList()
        return (1..FRAME_COUNT).map { frameNumber ->
            ExerciseVisual(
                assetPath = "$ASSET_ROOT/$workoutGuideId/frame-$frameNumber.svg"
            )
        }
    }

    private companion object {
        const val ASSET_ROOT = "workout-guide/assets"
        const val FRAME_COUNT = 3

        val WORKOUT_GUIDE_IDS = mapOf(
            "incline-dumbbell-press" to "incline-dumbbell-press",
            "pull-ups" to "pull-up",
            "dumbbell-lateral-raise" to "lateral-raise"
        )
    }
}
