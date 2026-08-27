package wallcrawl.elopenmike.com.core.exercise.visual

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSnapshot
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.exercise.workoutguide.testCatalogAttribution

class WorkoutGuideVisualProviderTest {

    private val inclineFrames = (1..3).map { frame ->
        ExerciseVisual("workout-guide/assets/incline-dumbbell-press/frame-$frame.svg")
    }
    private val pullUpFrames = (1..3).map { frame ->
        ExerciseVisual("workout-guide/assets/pull-up/frame-$frame.svg")
    }
    private val snapshot = WorkoutGuideCatalogSnapshot(
        exercises = emptyList(),
        framesByExerciseId = mapOf(
            "incline-dumbbell-press" to inclineFrames,
            "pull-ups" to pullUpFrames
        ),
        catalogAttribution = testCatalogAttribution(frameCount = 6)
    )
    private val provider = WorkoutGuideVisualProvider(FixedSource(snapshot))

    @Test
    fun framesFor_directCatalogId_returnsOrderedBundledFrames() {
        assertThat(provider.framesFor("incline-dumbbell-press").map { it.assetPath })
            .containsExactly(
                "workout-guide/assets/incline-dumbbell-press/frame-1.svg",
                "workout-guide/assets/incline-dumbbell-press/frame-2.svg",
                "workout-guide/assets/incline-dumbbell-press/frame-3.svg"
            )
            .inOrder()
    }

    @Test
    fun framesFor_wallCrawlAlias_mapsToPinnedUpstreamAssetId() {
        assertThat(provider.framesFor("pull-ups").map { it.assetPath })
            .containsExactly(
                "workout-guide/assets/pull-up/frame-1.svg",
                "workout-guide/assets/pull-up/frame-2.svg",
                "workout-guide/assets/pull-up/frame-3.svg"
            )
            .inOrder()
    }

    @Test
    fun framesFor_unknownOrBlankId_returnsNoFrames() {
        assertThat(provider.framesFor("unknown-exercise")).isEmpty()
        assertThat(provider.framesFor(" ")).isEmpty()
    }

    private class FixedSource(
        private val snapshot: WorkoutGuideCatalogSnapshot
    ) : WorkoutGuideCatalogSource {
        override suspend fun snapshot(): WorkoutGuideCatalogSnapshot = snapshot
        override fun currentSnapshot(): WorkoutGuideCatalogSnapshot = snapshot
    }
}
