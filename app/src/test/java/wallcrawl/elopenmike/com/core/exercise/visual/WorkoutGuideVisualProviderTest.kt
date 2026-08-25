package wallcrawl.elopenmike.com.core.exercise.visual

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkoutGuideVisualProviderTest {

    private val provider = WorkoutGuideVisualProvider()

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

        assertThat(provider.framesFor("dumbbell-lateral-raise").map { it.assetPath })
            .containsExactly(
                "workout-guide/assets/lateral-raise/frame-1.svg",
                "workout-guide/assets/lateral-raise/frame-2.svg",
                "workout-guide/assets/lateral-raise/frame-3.svg"
            )
            .inOrder()
    }

    @Test
    fun framesFor_unknownOrBlankId_returnsNoFrames() {
        assertThat(provider.framesFor("unknown-exercise")).isEmpty()
        assertThat(provider.framesFor(" ")).isEmpty()
    }
}
