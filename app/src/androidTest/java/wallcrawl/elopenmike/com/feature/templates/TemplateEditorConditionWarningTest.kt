package wallcrawl.elopenmike.com.feature.templates

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.conditions.ConditionAndroidFixtures
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidanceResult
import wallcrawl.elopenmike.com.core.conditions.MovementDemand
import wallcrawl.elopenmike.com.core.conditions.manualNoticeFor
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/** Warnings are shown beside exercises; none is hidden or refused. */
@RunWith(AndroidJUnit4::class)
class TemplateEditorConditionWarningTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var profiles: OfflineUserProfileRepository

    private fun sample(id: String): Exercise =
        InMemoryExerciseCatalog.SAMPLE_EXERCISES.single { it.id == id }

    private val avoided = sample("barbell-back-squat")
    private val adjusted = sample("barbell-deadlift")
    private val unreviewed = sample("pull-ups")
    private val plain = sample("barbell-bench-press")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        profiles = OfflineUserProfileRepository(database.userProfileDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun templateEditorWarnsOnRestrictedAndUnreviewedWithoutHiding() {
        val editor = show(
            profile = UserProfile(reportedConditions = setOf(ConditionAndroidFixtures.CONDITION_ID)),
            result = ConditionAndroidFixtures.loaded(
                exerciseDemands = mapOf(
                    avoided.id to setOf(MovementDemand.PLYOMETRIC_IMPACT),
                    adjusted.id to setOf(MovementDemand.HEAVY_EXTERNAL_LOAD),
                    plain.id to emptySet()
                ),
                avoid = setOf(MovementDemand.PLYOMETRIC_IMPACT),
                modify = setOf(MovementDemand.HEAVY_EXTERNAL_LOAD),
                demandCues = mapOf(MovementDemand.HEAVY_EXTERNAL_LOAD to mapOf("en" to "Keep the load light."))
            )
        )

        // All four are in the list: nothing is hidden.
        assertThat(editor.uiState.value.selectedExercises).hasSize(4)
        expectText(R.string.editor_condition_avoid)
        expectText(R.string.editor_condition_modify)
        expectText(R.string.condition_cue, "Keep the load light.")
        expectText(R.string.editor_condition_unreviewed)
        // The exercise with no restriction gets no notice at all, and no claim of suitability.
        assertThat(editor.restrictions.value.manualNoticeFor(plain.id)).isNull()
    }

    @Test
    fun noRestrictionsShowNoWarnings() {
        show(profile = UserProfile(), result = ConditionGuidanceResult.Unavailable)

        listOf(
            R.string.editor_condition_avoid,
            R.string.editor_condition_modify,
            R.string.editor_condition_unreviewed,
            R.string.editor_condition_unavailable
        ).forEach { res ->
            assertThat(composeRule.onAllNodesWithText(context.getString(res)).fetchSemanticsNodes())
                .isEmpty()
        }
    }

    @Test
    fun anUnavailableOverlayShowsOneGeneralNoticeNotAWarningPerExercise() {
        show(
            profile = UserProfile(reportedConditions = setOf(ConditionAndroidFixtures.CONDITION_ID)),
            result = ConditionGuidanceResult.Unavailable
        )

        expectText(R.string.editor_condition_unavailable)
        assertThat(
            composeRule.onAllNodesWithText(context.getString(R.string.editor_condition_unavailable))
                .fetchSemanticsNodes()
        ).hasSize(1)
        assertThat(
            composeRule.onAllNodesWithText(context.getString(R.string.editor_condition_unreviewed))
                .fetchSemanticsNodes()
        ).isEmpty()
    }

    private fun expectText(res: Int, vararg args: Any) {
        val text = context.getString(res, *args)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun show(profile: UserProfile, result: ConditionGuidanceResult): TemplateEditorViewModel {
        runBlocking { profiles.saveProfile(profile) }
        val catalog = InMemoryExerciseCatalog(listOf(avoided, adjusted, unreviewed, plain))
        val viewModel = TemplateEditorViewModel(
            templateId = null,
            templateRepository = OfflineWorkoutTemplateRepository(database.workoutTemplateDao(), catalog),
            userProfileRepository = profiles,
            exerciseCatalog = catalog,
            locale = { Locale.US },
            conditionGuidanceSource = ConditionAndroidFixtures.source(result)
        )
        composeRule.setContent {
            WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                TemplateEditorScreen(viewModel = viewModel, onBack = {}, onSaved = {})
            }
        }
        composeRule.waitUntil(10_000) { !viewModel.uiState.value.isLoading }
        composeRule.runOnIdle {
            listOf(avoided, adjusted, unreviewed, plain).forEach(viewModel::addExercise)
        }
        composeRule.waitUntil(10_000) { viewModel.restrictions.value !=
            wallcrawl.elopenmike.com.core.conditions.ConditionRestrictions.None ||
            profile.reportedConditions.isEmpty() }
        composeRule.waitForIdle()
        return viewModel
    }
}
