package wallcrawl.elopenmike.com.feature.program

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import wallcrawl.elopenmike.com.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.WorkoutPlanningIntent
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

@RunWith(AndroidJUnit4::class)
class ProgramAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeSpanishProgramStartLabelFitsInsideTheHouseButton() {
        val label = "Iniciar sesión del programa"
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.8f)) {
                WallCrawlTheme { Box(Modifier.width(288.dp)) { WallCrawlPrimaryButton(label, {}) } }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertThat(layouts).hasSize(1)
        assertThat(layouts.single().hasVisualOverflow).isFalse()
        compose.onNodeWithText(label).assertHasClickAction().assertIsEnabled()
    }

    @Test fun unavailableProgramOptionIsDisabledWhileOrdinaryChoiceStaysActionable() {
        var choice: WorkoutPlanningIntent? = null
        compose.setContent { WallCrawlTheme { Surface {
            ProgramPlanningChoice(WorkoutPlanningIntent.PROGRAM, false, true) { choice = it }
        } } }
        compose.onNodeWithTag("planning-PROGRAM").assertIsSelected().assertIsNotEnabled()
            .onParent().assert(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.SelectableGroup))
        // The unavailable row's label dims with its radio, not only the circle.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val unavailable = renderedContrast(context.getString(R.string.program_session_choice))
        val available = renderedContrast(context.getString(R.string.guided_train_ordinary))
        assertWithMessage("unavailable $unavailable vs available $available").that(unavailable).isLessThan(available / 2)
        compose.onNodeWithTag("planning-ORDINARY").assertIsEnabled().performClick()
        assertThat(choice).isEqualTo(WorkoutPlanningIntent.ORDINARY)
    }

    /** The strongest contrast any glyph pixel has against the label's own background. */
    private fun renderedContrast(text: String): Double {
        val pixels = compose.onNodeWithText(text, useUnmergedTree = true).captureToImage().toPixelMap()
        val background = pixels[0, 0].luminance()
        var contrast = 1.0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val foreground = pixels[x, y].luminance()
            contrast = maxOf(contrast, (maxOf(background, foreground) + 0.05) / (minOf(background, foreground) + 0.05))
        }
        return contrast
    }
}
