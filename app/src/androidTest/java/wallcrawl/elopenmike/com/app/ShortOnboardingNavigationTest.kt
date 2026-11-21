package wallcrawl.elopenmike.com.app

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import wallcrawl.elopenmike.com.AppContainer
import wallcrawl.elopenmike.com.DefaultAppContainer
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import wallcrawl.elopenmike.com.core.ai.TrainingProgramStateProvider
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineDeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineProgramRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineProgressRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutHistoryRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/** The short onboarding journey on the real app graph: basic setup, the offers, and personalization. */
class ShortOnboardingNavigationTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var container: AppContainer
    private lateinit var nav: NavHostController

    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun after() { if (::database.isInitialized) database.close(); clearPrefs() }
    }).around(compose).around(object : ExternalResource() {
        override fun after() = compose.waitForIdle()
    })

    private fun clearPrefs() = listOf(FIRST_LAUNCH_PREFS, TODAY_PREFS, "personalize_prompt").forEach {
        context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Before fun setup() {
        clearPrefs()
        context.getSharedPreferences(FIRST_LAUNCH_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_INTRO_SEEN, true).commit()
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        val gate = Mutex()
        val assets = DefaultAppContainer(context)
        container = object : AppContainer by assets {
            override val database = this@ShortOnboardingNavigationTest.database
            override val userProfileRepository = OfflineUserProfileRepository(database.userProfileDao(), gate)
            override val workoutRepository = OfflineWorkoutRepository(database.workoutSessionDao(), database.workoutSetDao())
            override val workoutHistoryRepository = OfflineWorkoutHistoryRepository(database, gate)
            override val programRepository = OfflineProgramRepository(database, gate)
            override val deloadRepository = OfflineDeloadRepository(database.deloadPreferencesDao(), gate)
            override val weeklyDoseLedgerRepository = OfflineWeeklyDoseLedgerRepository(database.completedWorkoutHistoryDao(), database.weeklyDoseLedgerStateDao(), workoutGuideCatalogSource)
            override val progressRepository = OfflineProgressRepository(database, workoutGuideCatalogSource, weeklyDoseLedgerRepository, gate)
            override val workoutTemplateRepository = OfflineWorkoutTemplateRepository(database.workoutTemplateDao(), exerciseCatalog, gate)
            override val localDataBackupRepository = OfflineLocalDataBackupRepository(database.localDataBackupDao(), "test", 1, { null }, gate)
            override val workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(userProfileRepository, workoutRepository,
                exerciseCatalog, exerciseFilter, workoutHistoryAnalyzer, plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
                trainingProgramStateProvider = TrainingProgramStateProvider(weeklyDoseLedgerRepository),
                deloadRepository = deloadRepository, programRepository = programRepository)
        }
    }

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            nav = rememberNavController()
            val base = LocalConfiguration.current
            val config = Configuration(base).apply { this.fontScale = fontScale }
            CompositionLocalProvider(
                LocalConfiguration provides config,
                LocalDensity provides Density(context.resources.displayMetrics.density, fontScale)
            ) {
                WallCrawlTheme { Box(if (fontScale > 1f) Modifier.width(320.dp) else Modifier) { WallCrawlApp(container, nav) } }
            }
        }
    }

    private fun text(id: Int) = context.getString(id)
    private fun route(route: String) = compose.waitUntil(15_000) { nav.currentDestination?.route == route }
    private fun hasNode(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(id: Int) = compose.waitUntil(15_000) { hasNode(text(id)) }
    private fun awaitToday() {
        route(AppRoutes.TODAY)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("today-list").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Clicks a button, scrolling its lazy or plain scroll container first when needed. */
    private fun click(id: Int) {
        val button = hasText(text(id)) and hasClickAction() and isEnabled()
        compose.waitUntil(15_000) {
            if (compose.onAllNodes(button).fetchSemanticsNodes().isEmpty()) {
                runCatching { compose.onNode(hasScrollAction()).performScrollToNode(hasText(text(id))) }
            }
            compose.onAllNodes(button).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(button).performClick()
    }

    private fun answerEveryMovementQuestion() {
        val comfortable = text(CapabilityLevel.COMFORTABLE.labelRes)
        repeat(MovementCapabilityType.entries.size) { i ->
            compose.onAllNodesWithText(comfortable)[i].performScrollTo().performClick()
        }
    }
    private fun waitStep(n: Int) = compose.waitUntil(15_000) { hasNode(context.getString(R.string.personalize_step_counter, "$n", "3")) }
    private fun cardVisible() = hasNode(text(R.string.personalize_today_title))
    private fun awaitCardState(visible: Boolean) = compose.waitUntil(15_000) { cardVisible() == visible }

    /** Fails if the list is missing or the target is not in it. */
    private fun scrollTodayTo(id: Int) {
        compose.onNodeWithTag("today-list").assertExists().performScrollToNode(hasText(text(id)))
    }

    /**
     * Absence means something only once Today has loaded and the card's slot (the top of the list)
     * has been composed: wait for the workout, then look at the top.
     */
    private fun assertNoTodayCard() {
        scrollTodayTo(R.string.today_action_start)
        compose.onNodeWithTag("today-list").performScrollToIndex(0)
        compose.waitForIdle()
        assertThat(cardVisible()).isFalse()
    }

    private fun openProfile() {
        compose.runOnIdle { nav.navigate("profile") }
        route("profile")
        compose.onNodeWithTag("profile-settings-list").assertExists()
    }

    /** Scrolls the whole settings list (failing if it is absent) and reports whether the row is at its top. */
    private fun profileRowShown(): Boolean {
        val list = compose.onNodeWithTag("profile-settings-list").assertExists()
        list.performScrollToNode(hasText(text(R.string.guided_about_credits)))
        list.performScrollToIndex(0)
        compose.waitForIdle()
        return hasNode(text(R.string.personalize_profile_row))
    }

    /** Done leaves the summary for Progress, so Today is reached through the bottom bar. */
    private fun doneThenToday() {
        click(R.string.summary_action_done)
        route(AppRoutes.PROGRESS)
        compose.onNode(hasText(text(R.string.nav_today)) and hasAnyAncestor(hasTestTag(WALL_CRAWL_BOTTOM_BAR_TEST_TAG))).performClick()
        awaitToday()
    }

    private fun basicSetupToToday() {
        compose.onNodeWithText(text(FitnessGoal.GENERAL_FITNESS.labelRes)).performClick()
        click(R.string.onboarding_basic_cta)
        awaitToday()
    }

    private fun finishFirstWorkoutToSummary() {
        click(R.string.today_action_start)
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.ACTIVE_WORKOUT }
        waitText(R.string.workout_action_finish)
        click(R.string.workout_action_finish)
        waitText(R.string.finish_confirm_action)
        compose.onNodeWithText(text(R.string.finish_confirm_action)).performClick()
        waitText(R.string.personalize_summary_title)
    }

    @Test fun freshInstall_basicSetup_landsOnTodayWithWorkoutAndNoCard() {
        show()
        route(AppRoutes.ONBOARDING)
        basicSetupToToday()
        assertNoTodayCard()
        assertThat(runBlocking { container.userProfileRepository.getProfileOnce() }.personalizationCompleted).isFalse()
    }

    @Test fun completedWorkout_summaryOffersPersonalize_notNow_todayShowsCard() {
        show()
        route(AppRoutes.ONBOARDING)
        basicSetupToToday()
        finishFirstWorkoutToSummary()
        click(R.string.personalize_not_now)
        compose.waitUntil(15_000) { !hasNode(text(R.string.personalize_summary_title)) }
        doneThenToday()
        scrollTodayTo(R.string.personalize_today_title)
        awaitCardState(true)
        // Positive control for the "row gone" check in the Finish test: same helper, row present.
        openProfile()
        assertThat(profileRowShown()).isTrue()
    }

    @Test fun personalize_threeSteps_finish_removesCardAndProfileRow() {
        show()
        route(AppRoutes.ONBOARDING)
        basicSetupToToday()
        finishFirstWorkoutToSummary()
        click(R.string.personalize_action)
        route(AppRoutes.PERSONALIZE)
        waitText(R.string.personalize_next)
        click(R.string.personalize_next)
        waitStep(2)
        answerEveryMovementQuestion()
        click(R.string.personalize_next)
        waitStep(3)
        click(R.string.personalize_finish)
        awaitToday()
        assertThat(nav.currentDestination?.route).isEqualTo(AppRoutes.TODAY)
        assertThat(runBlocking { container.userProfileRepository.getProfileOnce() }.personalizationCompleted).isTrue()
        assertNoTodayCard()
        openProfile()
        assertThat(profileRowShown()).isFalse()
    }

    @Test fun todayCard_dismiss_hidesIt() {
        show()
        route(AppRoutes.ONBOARDING)
        basicSetupToToday()
        finishFirstWorkoutToSummary()
        click(R.string.personalize_not_now)
        doneThenToday()
        scrollTodayTo(R.string.personalize_today_title)
        awaitCardState(true)
        compose.onNode(hasContentDescription(text(R.string.personalize_dismiss_a11y))).performClick()
        awaitCardState(false)
    }

    @Test fun existingPersonalizedProfile_seesNoPrompts() {
        runBlocking { container.userProfileRepository.saveProfile(UserProfile(name = "Veteran", onboardingCompleted = true, personalizationCompleted = true)) }
        show()
        awaitToday()
        finishFirstWorkoutNoOffer()
        doneThenToday()
        assertNoTodayCard()
        openProfile()
        assertThat(profileRowShown()).isFalse()
    }

    @Test fun deleteAllData_forgetsThePersonalizeDismissal() {
        runBlocking { container.userProfileRepository.saveProfile(UserProfile(name = "Dismisser", onboardingCompleted = true)) }
        val prefs = context.getSharedPreferences("personalize_prompt", Context.MODE_PRIVATE)
        prefs.edit().putLong("dismissed_at", System.currentTimeMillis()).commit()
        show()
        awaitToday()
        runBlocking { container.localDataBackupRepository.deleteAllLocalData() }
        route(AppRoutes.ONBOARDING)
        assertThat(prefs.contains("dismissed_at")).isFalse()
    }

    private fun finishFirstWorkoutNoOffer() {
        click(R.string.today_action_start)
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.ACTIVE_WORKOUT }
        waitText(R.string.workout_action_finish)
        click(R.string.workout_action_finish)
        waitText(R.string.finish_confirm_action)
        compose.onNodeWithText(text(R.string.finish_confirm_action)).performClick()
        waitText(R.string.summary_action_done)
        assertThat(hasNode(text(R.string.personalize_summary_title))).isFalse()
    }

    @Test fun basicSetup_fitsAt200PercentFont() {
        show(fontScale = 2f)
        route(AppRoutes.ONBOARDING)
        FitnessGoal.entries.forEach { goal ->
            val tile = compose.onNodeWithText(text(goal.labelRes))
            if (compose.onAllNodes(hasText(text(goal.labelRes)) and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) tile.performScrollTo()
            tile.assertIsDisplayed()
        }
        compose.onNodeWithText(text(R.string.onboarding_basic_cta)).assertIsDisplayed()
    }
}
