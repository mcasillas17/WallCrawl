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
import com.google.common.truth.Truth.assertThat
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
        compose.setContent { WallCrawlTheme {
            ProgramPlanningChoice(WorkoutPlanningIntent.PROGRAM, false, true) { choice = it }
        } }
        compose.onNodeWithTag("planning-PROGRAM").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag("planning-ORDINARY").assertIsEnabled().performClick()
        assertThat(choice).isEqualTo(WorkoutPlanningIntent.ORDINARY)
    }
}
