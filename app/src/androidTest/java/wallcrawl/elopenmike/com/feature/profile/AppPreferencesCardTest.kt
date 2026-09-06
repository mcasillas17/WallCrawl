package wallcrawl.elopenmike.com.feature.profile

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.locale.AppLanguage
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/**
 * Language and theme are one group, not two.
 *
 * The README and `docs/localization.md` send a reader to Profile → App preferences to
 * change the language. While the control merely sat beside that card under a heading of
 * its own, that route was wrong in the interface even though it was right in the code, and
 * nothing failed. This is what makes the documented route true.
 *
 * The expected text comes from resources rather than from English literals, so this reads
 * the grouping and not the wording — and so it still passes on a device running in Spanish,
 * which is the configuration the whole change exists to support.
 */
@RunWith(AndroidJUnit4::class)
class AppPreferencesCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun text(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun text(@StringRes id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test
    fun appPreferencesHoldsBothTheThemeAndTheLanguage() {
        composeRule.setContent {
            WallCrawlTheme {
                AppPreferencesCard(currentTheme = ThemePreference.SYSTEM, onSelectTheme = {})
            }
        }

        composeRule.onNodeWithText(text(R.string.profile_app_preferences_heading))
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.profile_theme_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.language_setting_title)).assertIsDisplayed()

        AppLanguage.entries.forEach { language ->
            composeRule.onNodeWithContentDescription(
                text(R.string.language_option_accessibility, text(language.labelRes))
            ).assertIsDisplayed()
        }
    }

    @Test
    fun theAbbreviatedSegmentsAreStillBigEnoughToHit() {
        // "EN" is two characters wide. The label is what shrinks; the target is not.
        composeRule.setContent {
            WallCrawlTheme {
                AppPreferencesCard(currentTheme = ThemePreference.SYSTEM, onSelectTheme = {})
            }
        }

        AppLanguage.entries.forEach { language ->
            composeRule.onNodeWithContentDescription(
                text(R.string.language_option_accessibility, text(language.labelRes))
            )
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
        }
    }
}
