package wallcrawl.elopenmike.com.core.ui.components

import android.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.WallCrawlApplication
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class ExerciseIllustrationMotionTest {
    private val motion = object : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(0f)
    }
    @get:Rule val compose = createComposeRule(motion)

    @Test fun compactWorkoutIllustrationRespectsMotionChanges() = checkMotion(88)

    @Test fun expandedWorkoutIllustrationRespectsMotionChanges() = checkMotion(210)

    private fun checkMotion(height: Int) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WallCrawlApplication
        val exercise = runBlocking { app.container.exerciseCatalog.getExerciseById("incline-dumbbell-press")!! }
        compose.setContent {
            WallCrawlTheme {
                val modifier = if (height == 88) Modifier.width(88.dp) else Modifier
                ExerciseIllustration(exercise, app.container.exerciseVisualProvider, modifier, height)
            }
        }
        val description = app.getString(R.string.illustration_content_description, exercise.name)
        val images = compose.onAllNodesWithContentDescription(description)
        fun artworkRow(): List<Int> {
            if (images.fetchSemanticsNodes().isEmpty()) return emptyList()
            val pixels = images[0].captureToImage().asAndroidBitmap()
            // A center row crosses the figure and the flat background, clear of the frame counter.
            return (pixels.width / 4 until pixels.width - pixels.width / 4).map { x ->
                Color.red(pixels.getPixel(x, pixels.height / 2))
            }
        }
        fun hasArtwork(row: List<Int>) = row.isNotEmpty() && row.max() - row.min() > 16
        compose.waitUntil(15_000) { hasArtwork(artworkRow()) }
        val still = artworkRow()
        compose.mainClock.advanceTimeBy(1_100)
        assertThat(artworkRow()).isEqualTo(still)
        compose.runOnIdle { motion.scaleFactor = 1f }
        compose.mainClock.advanceTimeBy(950)
        compose.waitUntil(8_000) { artworkRow().let { hasArtwork(it) && it != still } }
        compose.runOnIdle { motion.scaleFactor = 0f }
        compose.waitUntil(8_000) { artworkRow() == still }
        compose.mainClock.advanceTimeBy(1_100)
        assertThat(artworkRow()).isEqualTo(still)
        if (height == 210) {
            compose.onNodeWithText(app.getString(R.string.illustration_frame_counter, "1", "3")).assertExists()
        }
    }
}
