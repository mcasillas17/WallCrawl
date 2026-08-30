package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class MovementCapabilityControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun optionSemantics_exposeCapabilityOptionAndSelectedState() {
        var selectedLevel: CapabilityLevel? by mutableStateOf(null)

        composeRule.setContent {
            WallCrawlTheme {
                MovementCapabilityQuestion(
                    type = MovementCapabilityType.IMPACT,
                    selectedLevel = selectedLevel,
                    onSelect = { selectedLevel = it }
                )
            }
        }

        val notSure = composeRule.onNodeWithContentDescription(
            "Impact tolerance, Not sure",
            useUnmergedTree = true
        )
        notSure.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        )
        notSure.assertIsNotSelected()
        notSure.performClick()
        notSure.assertIsSelected()
    }
}
