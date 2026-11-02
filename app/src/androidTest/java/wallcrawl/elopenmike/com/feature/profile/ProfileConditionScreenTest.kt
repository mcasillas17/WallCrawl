package wallcrawl.elopenmike.com.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidanceResult
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidanceStore
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/** ProfileViewModel and ProfileScreen together, against the overlay the app ships. */
@RunWith(AndroidJUnit4::class)
class ProfileConditionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var profiles: OfflineUserProfileRepository
    private val store = ConditionGuidanceStore(context.assets)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        profiles = OfflineUserProfileRepository(database.userProfileDao())
    }

    @After
    fun tearDown() {
        composeRule.waitForIdle()
        database.close()
    }

    @Test
    fun addingAConditionShowsItsNotes() {
        val (id, record) = shipped().entries.first()
        show()

        scrollTo(hasTestTag("condition-chip-$id"))
        composeRule.onNodeWithTag("condition-chip-$id").performClick()

        val note = record.notes.getValue("en").first()
        scrollTo(hasText(note, substring = true))
        composeRule.onNodeWithText(note, substring = true).assertIsDisplayed()
        val flag = record.redFlags.getValue("en").first()
        scrollTo(hasText(flag, substring = true))
        composeRule.onNodeWithText(flag, substring = true).assertIsDisplayed()

        // Nothing is saved until it is acknowledged and confirmed.
        scrollTo(hasTestTag("condition-confirm"))
        composeRule.onNodeWithTag("condition-confirm").assertIsNotEnabled()
        assertThat(runBlocking { profiles.getProfileOnce().reportedConditions }).isEmpty()

        scrollTo(hasTestTag("condition-ack"))
        composeRule.onNodeWithTag("condition-ack").performClick()
        scrollTo(hasTestTag("condition-confirm"))
        composeRule.onNodeWithTag("condition-confirm").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertThat(runBlocking { profiles.getProfileOnce().reportedConditions }).containsExactly(id)
    }

    @Test
    fun theLongestRedFlagsStillReachConfirmAtLargestTextOn320By640() {
        val (id, record) = shipped().entries.maxBy { (_, r) ->
            r.redFlags.getValue("en").sumOf { it.length } + r.notes.getValue("en").sumOf { it.length }
        }
        show(fontScale = 2f, widthDp = 320, heightDp = 640)

        scrollTo(hasTestTag("condition-chip-$id"))
        composeRule.onNodeWithTag("condition-chip-$id").performClick()
        scrollTo(hasText(record.redFlags.getValue("en").last(), substring = true))

        scrollTo(hasTestTag("condition-ack"))
        composeRule.onNodeWithTag("condition-ack").performClick()
        scrollTo(hasTestTag("condition-confirm"))
        composeRule.onNodeWithTag("condition-confirm").assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertThat(runBlocking { profiles.getProfileOnce().reportedConditions }).containsExactly(id)
    }

    @Test
    fun anExcludedExerciseIsListedByNameAndCanBePutBack() {
        runBlocking {
            profiles.saveProfile(UserProfile(excludedExerciseIds = listOf("barbell-back-squat", "pull-ups")))
        }
        show(seed = false)

        scrollTo(hasText(context.getString(R.string.profile_excluded_heading)))
        composeRule.onNodeWithText(context.getString(R.string.profile_excluded_heading)).assertIsDisplayed()
        scrollTo(hasTestTag("excluded-remove-barbell-back-squat"))
        composeRule.onNodeWithTag("excluded-remove-barbell-back-squat").performClick()
        composeRule.waitForIdle()

        assertThat(runBlocking { profiles.getProfileOnce().excludedExerciseIds }).containsExactly("pull-ups")
    }

    @Test
    fun noExcludedExercisesShowsNothing() {
        show()
        scrollTo(hasTestTag("condition-unavailable").or(hasText(context.getString(R.string.condition_add_prompt))))
        assertThat(
            composeRule.onAllNodesWithText(context.getString(R.string.profile_excluded_heading))
                .fetchSemanticsNodes()
        ).isEmpty()
    }

    private fun shipped() =
        (runBlocking { store.guidance() } as ConditionGuidanceResult.Loaded).guidance.conditions

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
    }

    private fun show(
        seed: Boolean = true,
        fontScale: Float = 1f,
        widthDp: Int? = null,
        heightDp: Int? = null
    ) {
        if (seed) runBlocking { profiles.saveProfile(UserProfile()) }
        val viewModel = ProfileViewModel(profiles, store)
        composeRule.setContent {
            WallCrawlTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    val size = if (widthDp != null && heightDp != null) {
                        Modifier.size(widthDp.dp, heightDp.dp)
                    } else Modifier
                    Box(size) { ProfileScreen(viewModel = viewModel, onOpenCredits = {}) }
                }
            }
        }
        composeRule.waitUntil(10_000) {
            (viewModel.uiState.value as? ProfileUiState.Success)?.conditionGuidance != null
        }
        composeRule.waitForIdle()
    }
}
