package wallcrawl.elopenmike.com.feature.welcome

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class WelcomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var started = 0

    private fun show(reduced: Boolean = false, talkBack: Boolean = false, theme: ThemePreference = ThemePreference.DARK) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WallCrawlTheme(theme) {
                WelcomeScreen(onGetStarted = { started++ }, reducedMotion = reduced, touchExploration = talkBack)
            }
        }
    }

    private fun assertAbout() {
        compose.onNodeWithTag(WELCOME_INTRO_TEST_TAG).assertDoesNotExist()
        compose.onNodeWithTag(WELCOME_ABOUT_TEST_TAG).assertExists()
        compose.onNodeWithText(context.getString(R.string.welcome_about_headline)).assertIsDisplayed()
    }

    @Test fun introPlaysThenShowsAboutAndGetStartedReportsBack() {
        show()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag(WELCOME_INTRO_TEST_TAG).assertExists()
        compose.mainClock.advanceTimeBy(1600 + 3200)
        assertAbout()
        compose.onNodeWithText(context.getString(R.string.welcome_get_started)).performClick()
        assertThat(started).isEqualTo(1)
    }

    @Test fun skipLeavesTheIntroWithin150ms() {
        show(theme = ThemePreference.LIGHT)
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText(context.getString(R.string.welcome_skip)).performClick()
        compose.mainClock.advanceTimeBy(200)
        assertAbout()
    }

    @Test fun backDuringTheIntroSkips() {
        show()
        compose.mainClock.advanceTimeBy(400)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(200)
        assertAbout()
    }

    @Test fun reducedMotionStillReachesAbout() {
        show(reduced = true)
        compose.mainClock.advanceTimeBy(2200)
        assertAbout()
    }

    @Test fun theBuilderCardReadsAsOneNodeAndPlanYourWeekIsGone() {
        show(reduced = true)
        compose.mainClock.advanceTimeBy(2200)
        val card = compose.onNodeWithTag(WELCOME_BUILDER_TEST_TAG).fetchSemanticsNode()
        val read = card.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
        assertThat(read).containsExactly(
            context.getString(R.string.welcome_builder_title),
            context.getString(R.string.welcome_builder_goals),
            context.getString(R.string.welcome_builder_progress),
            context.getString(R.string.welcome_builder_equipment),
            context.getString(R.string.welcome_builder_output_title),
            context.getString(R.string.welcome_builder_output_body),
        ).inOrder()
        compose.onNodeWithText("Plan your week").assertDoesNotExist()
        compose.onNodeWithText("What WallCrawl does").assertDoesNotExist()
    }

    @Test fun talkBackStartsOnAbout() {
        show(talkBack = true)
        compose.mainClock.advanceTimeBy(300)
        assertAbout()
        // Scrolling animates; with the clock paused it would never finish (320×640 needs it).
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(context.getString(R.string.welcome_privacy_body)).performScrollTo().assertIsDisplayed()
    }
}
