package wallcrawl.elopenmike.com.feature.welcome

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
        compose.mainClock.advanceTimeBy(3700 + 3200)
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

    @Test fun reducedMotionHoldsTheIntroUntil3500msThenReachesAbout() {
        show(reduced = true)
        compose.mainClock.advanceTimeBy(3400)
        compose.onNodeWithTag(WELCOME_INTRO_TEST_TAG).assertExists()
        compose.mainClock.advanceTimeBy(600)
        assertAbout()
    }

    @Test fun theDiagramReadsAsOneSentenceAndTheOldContentIsGone() {
        show(reduced = true)
        compose.mainClock.advanceTimeBy(4000)
        val diagram = compose.onNodeWithTag(WELCOME_FLOW_TEST_TAG).fetchSemanticsNode()
        assertThat(diagram.config[SemanticsProperties.ContentDescription])
            .containsExactly(context.getString(R.string.welcome_flow_a11y))
        // The one-word labels are drawn but not read; TalkBack hears only the sentence.
        assertThat(SemanticsProperties.Text in diagram.config).isFalse()
        assertThat(diagram.children).isEmpty()
        listOf(R.string.welcome_privacy_no_account, R.string.welcome_privacy_no_ads,
            R.string.welcome_privacy_no_tracking, R.string.welcome_privacy_offline)
            .forEach { compose.onNodeWithText(context.getString(it)).assertIsDisplayed() }
        listOf("What WallCrawl does", "Plan your week", "Private by design", "Workouts built for you")
            .forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    /** At 320 dp and 200% text the diagram grows with its labels and the badges stay below it. */
    @Test fun largeTextOnANarrowScreenGrowsTheDiagramWithoutOverlap() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                WallCrawlTheme(ThemePreference.DARK) {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        WelcomeScreen(onGetStarted = {}, reducedMotion = true, touchExploration = true)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        val diagram = compose.onNodeWithTag(WELCOME_FLOW_TEST_TAG).getUnclippedBoundsInRoot()
        assertThat((diagram.bottom - diagram.top).value).isGreaterThan(326f)
        val badge = compose.onNodeWithText(context.getString(R.string.welcome_privacy_no_account)).getUnclippedBoundsInRoot()
        assertThat(badge.top.value).isAtLeast(diagram.bottom.value + 27f)
    }

    @Test fun talkBackStartsOnAbout() {
        show(talkBack = true)
        compose.mainClock.advanceTimeBy(300)
        assertAbout()
        // Scrolling animates; with the clock paused it would never finish (320×640 needs it).
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(context.getString(R.string.welcome_privacy_offline)).performScrollTo().assertIsDisplayed()
    }
}
