package wallcrawl.elopenmike.com.feature.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

@RunWith(AndroidJUnit4::class)
class ProfileNameFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val saved = mutableListOf<String>()
    private var shown by mutableStateOf(true)

    private fun show(name: String = "Crawler") {
        composeRule.setContent {
            WallCrawlTheme { if (shown) ProfileNameField(name = name, onSave = { saved += it }) }
        }
    }

    @Test fun doneSavesTheTypedName() {
        show()
        composeRule.onNodeWithTag(PROFILE_NAME_FIELD_TEST_TAG).performTextReplacement("Ana")
        composeRule.onNodeWithTag(PROFILE_NAME_FIELD_TEST_TAG).performImeAction()
        composeRule.runOnIdle { assertThat(saved).containsExactly("Ana") }
    }

    @Test fun aBlankEntryIsNotSavedAndTheNameComesBack() {
        show(name = "Sam")
        composeRule.onNodeWithTag(PROFILE_NAME_FIELD_TEST_TAG).performTextReplacement("  ")
        composeRule.onNodeWithTag(PROFILE_NAME_FIELD_TEST_TAG).performImeAction()
        composeRule.onNodeWithTag(PROFILE_NAME_FIELD_TEST_TAG).assertTextContains("Sam")
        composeRule.runOnIdle { assertThat(saved).isEmpty() }
    }

    @Test fun leavingMidEditSaves() {
        show()
        composeRule.onNodeWithTag(PROFILE_NAME_FIELD_TEST_TAG).performTextReplacement("Lu")
        composeRule.runOnIdle { shown = false }
        composeRule.runOnIdle { assertThat(saved).containsExactly("Lu") }
    }
}
