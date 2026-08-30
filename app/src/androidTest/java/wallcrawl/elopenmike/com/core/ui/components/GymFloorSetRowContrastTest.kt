package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/**
 * The logger has to stay readable in both themes.
 *
 * The app's typography carries light-on-dark colours in its text styles, so any Material
 * component that falls back to those defaults renders near-white text -- invisible on a
 * light background. These tests pin the rendered result rather than the implementation:
 * each label must actually contain dark pixels in the light theme and light pixels in the
 * dark theme.
 */
class GymFloorSetRowContrastTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightTheme_keepsEverySetControlReadableAgainstItsOwnBackground() {
        showRow(ThemePreference.LIGHT)

        assertReadable("Skip or stop")
        assertReadable("Add feedback (optional)")
        assertReadableByDescription("Load kg for set 1")
        assertReadableByDescription("Set 1 felt manageable, Yes")
        assertReadableByDescription("Set 1 complete")
    }

    @Test
    fun darkTheme_keepsEverySetControlReadableAgainstItsOwnBackground() {
        showRow(ThemePreference.DARK)

        assertReadable("Skip or stop")
        assertReadable("Add feedback (optional)")
        assertReadableByDescription("Load kg for set 1")
        assertReadableByDescription("Set 1 felt manageable, Yes")
        assertReadableByDescription("Set 1 complete")
    }

    private fun assertReadable(text: String) {
        assertThat(composeRule.onNodeWithText(text).bestContrastRatio())
            .isAtLeast(MINIMUM_TEXT_CONTRAST)
    }

    private fun assertReadableByDescription(description: String) {
        assertThat(composeRule.onNodeWithContentDescription(description).bestContrastRatio())
            .isAtLeast(MINIMUM_TEXT_CONTRAST)
    }

    private fun showRow(theme: ThemePreference) {
        composeRule.setContent {
            WallCrawlTheme(themePreference = theme) {
                // An opaque themed surface, exactly like the real screen: without it the
                // row would be captured over a transparent window and any washed-out
                // label would still look readable against the empty background.
                Surface(color = MaterialTheme.colorScheme.background) {
                GymFloorSetRow(
                    set = completedSet(),
                    weightUnit = "kg",
                    previousSet = null,
                    onValuesChanged = {},
                    onCompletionChanged = { _, _ -> },
                    onSkipSet = {},
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * The best WCAG contrast ratio between any pixel of this component and the component's
     * own background, sampled from its top-left corner. A washed-out label leaves every
     * pixel close to the background and scores close to 1.
     */
    private fun SemanticsNodeInteraction.bestContrastRatio(): Double {
        val pixels = captureToImage().toPixelMap()
        val background = pixels[0, 0].relativeLuminance()
        var best = 1.0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val candidate = pixels[x, y].relativeLuminance()
                val lighter = maxOf(candidate, background)
                val darker = minOf(candidate, background)
                best = maxOf(best, (lighter + 0.05) / (darker + 0.05))
            }
        }
        return best
    }

    private fun androidx.compose.ui.graphics.Color.relativeLuminance(): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun completedSet() = WorkoutSet(
        id = "set-1",
        workoutExerciseId = "exercise",
        setNumber = 1,
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetReps = 8,
        targetWeight = 40.0,
        completedReps = 8,
        completedWeight = 40.0,
        completedAtTimestamp = 1_777_777L,
        isCompleted = true
    )

    private companion object {
        /** WCAG AA for normal-size text. */
        const val MINIMUM_TEXT_CONTRAST = 4.5
    }
}
