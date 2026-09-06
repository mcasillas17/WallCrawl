package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
 * The onboarding wizard's header language chip.
 *
 * Profile offers the same preference as a segmented control, covered by
 * `AppPreferencesCardTest`. Both write through one `AppLanguageController`, so neither holds
 * a preference the other could contradict.
 *
 * The controller is injected here rather than exercised for real: applying a language
 * recreates the activity, which a Compose rule cannot host. What this pins is what the chip
 * shows, what it offers, and what it reports back — `AppLanguageTest` covers how a stored
 * tag maps to an option.
 */
@RunWith(AndroidJUnit4::class)
class LanguageChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theChipShowsTheLanguageBeingRead() {
        // Under System default the code is still concrete: it answers "what am I looking
        // at", which is the only question a two-letter chip can answer.
        composeRule.setContent {
            WallCrawlTheme { LanguageChip(current = AppLanguage.SYSTEM, onSelect = {}) }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.language_option_english_short))
            .assertIsDisplayed()
    }

    @Test
    fun theChipStaysReachableEvenThoughItIsSmall() {
        composeRule.setContent {
            WallCrawlTheme { LanguageChip(current = AppLanguage.SYSTEM, onSelect = {}) }
        }

        val chip = composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.language_option_accessibility,
                context.getString(AppLanguage.ENGLISH.labelRes)
            )
        )
        chip.assertIsDisplayed()
        chip.assertHeightIsAtLeast(48.dp)
        chip.assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun openingItStillOffersSystemDefaultEnglishAndSpanish() {
        // Shrinking the control must not drop System default: following the device is the
        // behaviour a fresh install depends on.
        composeRule.setContent {
            WallCrawlTheme { LanguageChip(current = AppLanguage.SYSTEM, onSelect = {}) }
        }

        openChip()

        AppLanguage.entries.forEach { language ->
            composeRule
                .onNodeWithContentDescription(context.getString(language.labelRes))
                .assertIsDisplayed()
        }
    }

    @Test
    fun choosingALanguageReportsItToTheSharedPreference() {
        val chosen = mutableListOf<AppLanguage>()
        composeRule.setContent {
            WallCrawlTheme {
                LanguageChip(current = AppLanguage.SYSTEM, onSelect = { chosen += it })
            }
        }

        openChip()
        composeRule
            .onNodeWithContentDescription(context.getString(AppLanguage.SPANISH.labelRes))
            .performClick()

        assertThat(chosen).containsExactly(AppLanguage.SPANISH)
    }

    private fun openChip() {
        composeRule.onNodeWithContentDescription(
            context.getString(
                R.string.language_option_accessibility,
                context.getString(AppLanguage.ENGLISH.labelRes)
            )
        ).performClick()
        composeRule.waitForIdle()
    }
}
