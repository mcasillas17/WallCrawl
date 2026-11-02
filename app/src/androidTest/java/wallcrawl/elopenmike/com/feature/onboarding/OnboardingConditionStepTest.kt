package wallcrawl.elopenmike.com.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidanceStore
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/** The SAFETY step with the shipped overlay: optional, never a new step, blocked only while pending. */
@RunWith(AndroidJUnit4::class)
class OnboardingConditionStepTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var viewModel: OnboardingViewModel
    private val id = "piriformis_syndrome"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        viewModel = OnboardingViewModel(
            OfflineUserProfileRepository(database.userProfileDao()),
            conditionGuidanceSource = ConditionGuidanceStore(context.assets)
        )
        viewModel.goToStep(OnboardingStep.SAFETY)
        composeRule.setContent {
            WallCrawlTheme { OnboardingScreen(viewModel = viewModel, onCompleted = {}) }
        }
        composeRule.waitUntil(10_000) { viewModel.uiState.value.conditionGuidance != null }
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        composeRule.waitForIdle()
        database.close()
    }

    @Test
    fun aPendingConditionBlocksContinueUntilAddedAndTheSummaryNamesIt() {
        scrollTo(hasTestTag("condition-chip-$id"))
        composeRule.onNodeWithTag("condition-chip-$id").performClick()
        scrollTo(hasTestTag("condition-confirm"))
        composeRule.onNodeWithTag("condition-confirm").assertIsNotEnabled()

        composeRule.onNodeWithText(context.getString(R.string.onboarding_action_continue)).performClick()
        composeRule.waitForIdle()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SAFETY)
        composeRule.onNodeWithText(context.getString(R.string.onboarding_error_condition_pending))
            .assertIsDisplayed()

        scrollTo(hasTestTag("condition-ack"))
        composeRule.onNodeWithTag("condition-ack").performClick()
        scrollTo(hasTestTag("condition-confirm"))
        composeRule.onNodeWithTag("condition-confirm").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertThat(viewModel.uiState.value.reportedConditions).containsExactly(id)

        composeRule.onNodeWithText(context.getString(R.string.onboarding_action_continue)).performClick()
        composeRule.waitForIdle()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SUMMARY)
        val name = context.getString(R.string.condition_name_piriformis_syndrome)
        scrollTo(hasText(name, substring = true))
        composeRule.onNodeWithText(name, substring = true).assertIsDisplayed()
    }

    @Test
    fun noConditionAddsNoRequirement() {
        composeRule.onNodeWithText(context.getString(R.string.onboarding_action_continue)).performClick()
        composeRule.waitForIdle()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SUMMARY)
        assertThat(
            composeRule.onAllNodesWithText(context.getString(R.string.onboarding_error_condition_pending))
                .fetchSemanticsNodes()
        ).isEmpty()
    }

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
    }
}
