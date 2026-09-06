package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.locale.AppLanguage
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/**
 * The onboarding Welcome step's language card.
 *
 * Profile shows the same control inside its App preferences card, through `LanguageOptions`
 * rather than this wrapper, and `AppPreferencesCardTest` pins that grouping. Both go through
 * one `AppLanguageController`, so neither holds a preference the other could contradict.
 *
 * The controller is injected here rather than exercised for real: applying a language
 * recreates the activity, which a Compose rule cannot host. What this pins is the options
 * offered and the selection reported back — `AppLanguageTest` covers how a stored tag maps
 * to an option.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theSelectorOffersSystemDefaultEnglishAndSpanish() {
        composeRule.setContent {
            WallCrawlTheme { LanguageSelector(current = AppLanguage.SYSTEM, onSelect = {}) }
        }

        composeRule.onNodeWithText(context.getString(R.string.language_option_system))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.language_option_english))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.language_option_spanish))
            .assertIsDisplayed()
    }

    @Test
    fun choosingALanguageReportsItToTheSharedPreference() {
        val chosen = mutableListOf<AppLanguage>()
        composeRule.setContent {
            WallCrawlTheme {
                LanguageSelector(current = AppLanguage.SYSTEM, onSelect = { chosen += it })
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.language_option_spanish))
            .performClick()

        assertThat(chosen).containsExactly(AppLanguage.SPANISH)
    }

    @Test
    fun eachOptionAnnouncesItselfAsALanguageChoice() {
        composeRule.setContent {
            WallCrawlTheme { LanguageSelector(current = AppLanguage.ENGLISH, onSelect = {}) }
        }

        AppLanguage.entries.forEach { language ->
            val label = context.getString(language.labelRes)
            composeRule
                .onNodeWithContentDescription(
                    context.getString(R.string.language_option_accessibility, label)
                )
                .assertIsDisplayed()
        }
    }
}
