package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.WallCrawlApplication
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class ExerciseIllustrationMotionTest {
    private val motion = object : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(0f)
    }
    @get:Rule val compose = createComposeRule(motion)

    @Test fun bothWorkoutSizesRespectTheMotionScaleWhenItChanges() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WallCrawlApplication
        val exercise = runBlocking { app.container.exerciseCatalog.getExerciseById("incline-dumbbell-press")!! }
        compose.setContent {
            WallCrawlTheme {
                Column {
                    ExerciseIllustration(exercise, app.container.exerciseVisualProvider, Modifier.width(88.dp).testTag("small"), 88)
                    ExerciseIllustration(exercise, app.container.exerciseVisualProvider, height = 180)
                }
            }
        }
        val small = compose.onNodeWithTag("small")
        fun frame() = small.captureToImage().asAndroidBitmap()
        // Rounded corners can expose the white window even before the SVG has decoded.
        fun artworkLoaded(pixels: android.graphics.Bitmap): Boolean {
            val inset = pixels.width / 8
            var brightPixels = 0
            for (y in inset until pixels.height - inset step 2) {
                for (x in inset until pixels.width - inset step 2) {
                    if (android.graphics.Color.red(pixels.getPixel(x, y)) > 225 && ++brightPixels >= 25) return true
                }
            }
            return false
        }
        compose.waitUntil(15_000) { artworkLoaded(frame()) }
        val still = frame()
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithText("Frame 1 of 3").assertExists()
        assertThat(still.sameAs(frame())).isTrue()
        compose.runOnIdle { motion.scaleFactor = 1f }
        compose.mainClock.advanceTimeBy(950)
        compose.waitUntil(8_000) { frame().let { artworkLoaded(it) && !still.sameAs(it) } }
        compose.runOnIdle { motion.scaleFactor = 0f }
        compose.waitUntil(8_000) { still.sameAs(frame()) }
        compose.onNodeWithText("Frame 1 of 3").assertExists()
    }
}
