package wallcrawl.elopenmike.com.app

import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.ui.test.isOn
import androidx.test.filters.SdkSuppress
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import wallcrawl.elopenmike.com.core.ui.components.GymFloorSetRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.TextLayoutResult
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import wallcrawl.elopenmike.com.test.hasLiveRegion
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingViewModel
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingScreen
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.accessibility.disableAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.ui.localization.shortLabelRes
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.AppContainer
import wallcrawl.elopenmike.com.DefaultAppContainer
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import wallcrawl.elopenmike.com.core.ai.TrainingProgramStateProvider
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingStep

/**
 * Sweeps the real app, route by route, for what Compose can catch automatically: unnamed or
 * too-small controls, labels read twice, and screens with nothing to jump to. API 34+ also runs
 * Google's Accessibility Test Framework, which adds measured contrast. Passing this is not proof
 * of accessibility; see docs/accessibility.md for what only TalkBack can show.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibilityBaselineTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var container: AppContainer
    private lateinit var nav: NavHostController
    private lateinit var localized: Context
    private val findings = mutableListOf<String>()

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        val gate = Mutex()
        val assets = DefaultAppContainer(context)
        container = object : AppContainer by assets {
            override val database = this@AccessibilityBaselineTest.database
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

    @After fun teardown() {
        compose.waitForIdle()
        database.close()
    }

    @Test fun everyRouteInLightTheme() = sweep(Locale.ENGLISH, ThemePreference.LIGHT, 1f)

    @Test fun everyRouteInDarkTheme() = sweep(Locale.ENGLISH, ThemePreference.DARK, 1f)

    /** Just under where rows stack at this width, so they are as tight as they get unstacked. */
    @Test fun everyRouteInSpanishAt320Dp() =
        sweep(Locale.forLanguageTag("es-MX"), ThemePreference.LIGHT, 1f, narrow = true)

    /** The smallest stock scale above 1 that stacks at 320 dp. */
    @Test fun everyRouteInSpanishAt320DpAndMediumText() =
        sweep(Locale.forLanguageTag("es-MX"), ThemePreference.LIGHT, 1.15f)

    @Test fun everyRouteInSpanishAt320DpAndLargeTextLight() =
        sweep(Locale.forLanguageTag("es-MX"), ThemePreference.LIGHT, 1.8f)

    @Test fun everyRouteInSpanishAt320DpAndLargeTextDark() =
        sweep(Locale.forLanguageTag("es-MX"), ThemePreference.DARK, 1.8f)

    @Test fun everyRouteInEnglishAt320DpAndLargeTextLight() = sweep(Locale.ENGLISH, ThemePreference.LIGHT, 1.8f)

    @Test fun everyRouteInEnglishAt320DpAndLargeTextDark() = sweep(Locale.ENGLISH, ThemePreference.DARK, 1.8f)

    @Test fun everyOnboardingStep() = onboardingSweep(Locale.ENGLISH, ThemePreference.LIGHT, 1f)

    @Test fun everyOnboardingStepInSpanishDarkAtLargeText() =
        onboardingSweep(Locale.forLanguageTag("es-MX"), ThemePreference.DARK, 1.8f)

    @Test fun everyOnboardingStepInSpanishLightAtLargeText() =
        onboardingSweep(Locale.forLanguageTag("es-MX"), ThemePreference.LIGHT, 1.8f)

    @Test fun everyOnboardingStepInEnglishDarkAtLargeText() =
        onboardingSweep(Locale.ENGLISH, ThemePreference.DARK, 1.8f)

    @Test fun everyOnboardingStepInEnglishLightAtLargeText() =
        onboardingSweep(Locale.ENGLISH, ThemePreference.LIGHT, 1.8f)

    /** Answers only what a step requires, so it reaches and scans all eight. */
    private fun onboardingSweep(locale: Locale, theme: ThemePreference, fontScale: Float) = runBlocking {
        saveProfile(completed = false)
        show(locale, theme, fontScale, AppRoutes.ONBOARDING)
        compose.onNode(hasSetTextAction()).performTextReplacement("Gwen")
        val resources = localized.resources
        val notSure = resources.getString(R.string.capability_level_unknown)
        val next = resources.getString(R.string.onboarding_action_continue)
        val total = OnboardingStep.entries.size
        for (step in 1..total) {
            scan("onboarding step $step")
            if (step == total) break
            if (step == 2) {
                val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
                assertThat(compose.onAllNodes(checkbox).fetchSemanticsNodes()).isNotEmpty()
                // With one goal chosen, that one is unavailable rather than silently refused.
                compose.onNode(checkbox and isOn()).assertIsNotEnabled()
            }
            if (step == 4) {
                // Continue without the required answers: the refusal is announced where it appears.
                compose.onNodeWithText(next).performClick()
                val refusal = compose.onNode(hasText(resources.getString(R.string.onboarding_error_movement_required)), useUnmergedTree = true)
                refusal.assert(hasLiveRegion())
                // Refused again: the same words, but a new node, so TalkBack says them again.
                val first = refusal.fetchSemanticsNode().id
                compose.onNodeWithText(next).performClick()
                compose.waitForIdle()
                assertThat(refusal.fetchSemanticsNode().id).isNotEqualTo(first)
            }
            val options = hasContentDescription(notSure, substring = true) and hasClickAction()
            compose.onAllNodes(options).fetchSemanticsNodes().indices.forEach { index ->
                compose.onAllNodes(options)[index].performScrollTo().performClick()
            }
            compose.onNodeWithText(next).performClick()
            compose.waitForIdle()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(resources.getString(R.string.onboarding_step_counter, "${step + 1}", "$total"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        report()
    }

    /** System Back walks the wizard back a step and keeps the answer; nothing is written yet. */
    @Test fun onboardingBackKeepsTheAnswerAndWritesNothing() = runBlocking<Unit> {
        saveProfile(completed = false)
        val before = container.userProfileRepository.getUserProfile().first()
        show(Locale.ENGLISH, ThemePreference.LIGHT, 1f, AppRoutes.ONBOARDING)
        compose.onNode(hasSetTextAction()).performTextReplacement("Gwen")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Step 2 of 8").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Step 1 of 8").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Step 1 of 8")).assertExists()
        compose.onNode(hasSetTextAction()).assert(hasText("Gwen"))
        assertThat(container.userProfileRepository.getUserProfile().first()).isEqualTo(before)
    }

    /** The handler acts only on commit: this pins that a cancelled gesture does nothing, and a committed one steps back. Neither loses the answer or writes. */
    @Test fun onboardingBackGestureKeepsTheAnswerAndWritesNothing() = runBlocking<Unit> {
        saveProfile(completed = false)
        val before = container.userProfileRepository.getUserProfile().first()
        show(Locale.ENGLISH, ThemePreference.LIGHT, 1f, AppRoutes.ONBOARDING)
        compose.onNode(hasSetTextAction()).performTextReplacement("Gwen")
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Step 2 of 8").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Step 2 of 8")).assertExists()
        val back = compose.activity.onBackPressedDispatcher
        fun start() {
            back.dispatchOnBackStarted(BackEventCompat(0f, 1200f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(300f, 1200f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.runOnUiThread { start(); back.dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.onNodeWithText("Step 2 of 8").assertExists()
        compose.runOnUiThread { start(); back.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Step 1 of 8").assertExists()
        compose.onNode(hasSetTextAction()).assert(hasText("Gwen"))
        assertThat(container.userProfileRepository.getUserProfile().first()).isEqualTo(before)
    }

    /** During a restore every way back agrees: both arrows are off and system Back stays on the step. */
    @Test fun everyBackIsHeldOnALaterStepDuringARestore() {
        var restoring by mutableStateOf(false)
        val viewModel = OnboardingViewModel(container.userProfileRepository)
        compose.setContent {
            WallCrawlTheme { OnboardingScreen(viewModel = viewModel, onCompleted = {}, isRestoreInFlight = restoring) }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("Gwen")
        compose.onNodeWithText(context.getString(R.string.onboarding_action_continue)).performClick()
        compose.onNodeWithText(context.getString(R.string.onboarding_step_counter, "2", "8")).assertExists()
        restoring = true
        compose.waitForIdle()
        // The header arrow is the only on-screen Back; system Back is held too.
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).assertIsNotEnabled()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.onboarding_step_counter, "2", "8")).assertExists()
    }

    /** Back must not leave the app, and cancel the restore, while one runs on the first step. */
    @Test fun backIsHeldWhileARestoreIsInFlight() {
        var restoring by mutableStateOf(false)
        val viewModel = OnboardingViewModel(container.userProfileRepository)
        compose.setContent {
            WallCrawlTheme { OnboardingScreen(viewModel = viewModel, onCompleted = {}, isRestoreInFlight = restoring) }
        }
        compose.waitForIdle()
        compose.runOnUiThread { assertThat(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks()).isFalse() }
        restoring = true
        compose.waitForIdle()
        compose.runOnUiThread {
            assertThat(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks()).isTrue()
            compose.activity.onBackPressedDispatcher.onBackPressed()
            assertThat(compose.activity.isFinishing).isFalse()
        }
        compose.waitForIdle()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.WELCOME)
    }

    /** A weighted set with a previous value and feedback open: the fullest the logger gets. */
    @Test fun fullSetRowAt320DpAndLargeText() {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag("es-MX")))
            fontScale = 1.8f
            screenWidthDp = 320
        }
        localized = context.createConfigurationContext(configuration)
        // Logged, since the logger offers feedback only on a resolved set.
        var set by mutableStateOf(WorkoutSet(id = "s", workoutExerciseId = "e", setNumber = 1, exerciseType = ExerciseType.WEIGHT_REPS, targetReps = 8, targetWeight = 47.5,
            completedReps = 8, completedWeight = 47.5, isCompleted = true))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) compose.enableAccessibilityChecks()
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(context.resources.displayMetrics.density, 1.8f)
            ) {
                WallCrawlTheme {
                    Box(Modifier.requiredWidth(288.dp).verticalScroll(rememberScrollState())) { // 320 dp less the screen padding
                        GymFloorSetRow(
                            set = set, weightUnit = "kg", previousSet = set.copy(completedWeight = 47.5, completedReps = 8),
                            onCompletionChanged = { _, _ -> }, onSkipSet = { _, _ -> },
                            onRecordEffort = { _, _ -> }, onRecordFeltManageable = {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(localized.getString(R.string.set_feedback_show)).performClick()
        compose.waitForIdle()
        structural("full set row", top = false)
        atf("full set row", top = false)
        // Every recorded stop reason, none of which may squeeze the set number away.
        SetStopReason.entries.forEach { reason ->
            set = set.copy(stopReason = reason)
            compose.waitForIdle()
            structural("set stopped: $reason", top = false)
            atf("set stopped: $reason", top = false)
        }
        report()
    }

    /** The delete question shows with its actions, and Cancel keeps the routine. Large text in a dialog is a manual check: see Limitations. */
    @Test fun templateDeleteDialogAt320Dp() = runBlocking {
        saveProfile(completed = true)
        val name = "Routine ".repeat(15).trim().padEnd(WorkoutTemplate.MAX_NAME_LENGTH, 'x')
        saveRoutine(name)
        show(Locale.ENGLISH, ThemePreference.LIGHT, 1.8f, AppRoutes.TODAY)
        compose.runOnIdle { nav.navigate(AppRoutes.WORKOUT_TEMPLATES) }
        val delete = localized.getString(R.string.a11y_delete_named, name)
        compose.waitUntilAtLeastOneExists(hasContentDescription(delete), 15_000)
        checked("template delete dialog", top = true) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(delete))
            compose.onNodeWithContentDescription(delete).performClick()
        }
        compose.waitForIdle()
        compose.onNodeWithText(localized.getString(R.string.template_delete_title, name)).assertIsDisplayed()
        // The body scrolls itself, so it stays on screen and any overflow is reachable by scrolling it.
        compose.onNodeWithText(localized.getString(R.string.template_delete_body)).assertIsDisplayed()
        compose.onNodeWithText(localized.getString(R.string.action_delete)).assertIsDisplayed().assertHasClickAction()
        val cancel = localized.getString(R.string.action_cancel)
        compose.onNodeWithText(cancel).assertIsDisplayed()
        checked("template delete dialog", top = true) { compose.onNodeWithText(cancel).performClick() }
        compose.waitForIdle()
        assertThat(container.workoutTemplateRepository.getTemplate("saved")).isNotNull()
        report()
    }

    /** Each detector reports a known-bad fixture, so a clean sweep means something. */
    @Test fun theScanReportsKnownBadFixtures() {
        compose.setContent {
            Column {
                Box(Modifier.width(100.dp)) { Text("Pneumonoultramicroscopicsilicovolcanoconiosis", fontSize = 30.sp) }
                Box(Modifier.size(48.dp).clickable {})
                Box(Modifier.size(30.dp).clickable(role = Role.Button) {}.semantics { contentDescription = "tiny" })
                Box(Modifier.size(48.dp).clickable {}.semantics { contentDescription = "roleless" })
                Box(Modifier.size(31.dp).clickable(enabled = false, role = Role.Button) {}.semantics { contentDescription = "off" })
                Box(Modifier.semantics(mergeDescendants = true) {}) { Text("twice"); Text("twice") }
                Box(Modifier.width(0.dp)) { Text("gone") }
                BasicTextField("", {})
            }
        }
        compose.waitForIdle()
        structural("bad", top = true)
        listOf("word broken", "unnamed", "target 30x30", "target 31x31", "no role: roleless", "read twice", "squeezed to nothing", "field has no name", "no heading")
            .forEach { expected -> assertWithMessage("$expected in $findings").that(findings.any { expected in it }).isTrue() }
    }

    /** ATF reaches the sweep through a different path from the structural detectors, so it gets its own. */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test fun atfReportsAKnownBadFixture() {
        compose.enableAccessibilityChecks()
        compose.setContent {
            Box(Modifier.size(20.dp).clickable(role = Role.Button) {}.semantics { contentDescription = "tiny" })
        }
        compose.waitForIdle()
        atf("bad", top = true)
        assertWithMessage("$findings").that(findings.any { "bad ATF:" in it && "touch target" in it }).isTrue()
    }

    /** The detail sheet has a heading and a reachable last control. A sheet is its own window: see Limitations for its font scale. */
    @Test fun exerciseDetailSheetAt320Dp() = runBlocking {
        saveProfile(completed = true)
        show(Locale.ENGLISH, ThemePreference.LIGHT, 1.8f, AppRoutes.TODAY)
        compose.runOnIdle { nav.navigate(AppRoutes.EXERCISES) }
        // At this size on a short screen the filters fill the view and the catalog starts below it.
        // The screen's list comes first; the filter rows inside it are lazy lists too.
        compose.waitUntil(15_000) {
            runCatching {
                compose.onAllNodes(hasScrollToIndexAction()).onFirst()
                    .performScrollToNode(hasText("Ab Wheel Rollout", substring = true))
            }.isSuccess
        }
        checked("exercise detail sheet", top = false) {
            compose.onNode(hasText("Ab Wheel Rollout", substring = true) and hasClickAction()).performClick()
        }
        val close = localized.getString(R.string.exercise_detail_close)
        compose.waitUntilAtLeastOneExists(hasText(close), 10_000)
        structural("exercise detail sheet", top = false)
        atf("exercise detail sheet", top = false)
        // Material's drag handle is 32 dp wide, which ATF would report and so cancel this scroll; Close
        // details is the accessible way out. ATF's touch-target results are not counted on this sheet,
        // so structural() is its size check. Other ATF checks count, and docs/accessibility.md records the gap.
        withoutActionChecks {
            compose.onNodeWithText(close).performScrollTo().assertIsDisplayed().assertHasClickAction()
        }
        // Material's handle again, as structural() measures it: its size and missing role, and the
        // roleless dismiss target behind the sheet.
        findings.removeAll {
            "target 32x48 dp: Drag handle" in it || "no role: Drag handle" in it || "no role: Close sheet" in it
        }
        compose.onNode(isHeading() and hasText("Ab Wheel Rollout")).assertExists()
        report()
    }

    /** Selection reads as state, and an activation moves it. */
    @Test fun profileChoicesExposeTheirSelection() = runBlocking<Unit> {
        saveProfile(completed = true)
        show(Locale.ENGLISH, ThemePreference.LIGHT, 1f, AppRoutes.TODAY)
        compose.runOnIdle { nav.navigate(AppRoutes.PROFILE) }
        compose.onNodeWithText(localized.getString(R.string.guided_training_preferences)).performClick()
        // The page is a lazy list: its later rows exist only once scrolled to.
        compose.waitUntilAtLeastOneExists(hasText(localized.getString(R.string.guided_goals)), 10_000)
        var list = compose.onAllNodes(hasScrollAction())[0]
        list.performScrollToNode(hasContentDescription("4 days per week"))
        compose.onNodeWithContentDescription("2 days per week").assertIsSelected()
        compose.onNodeWithContentDescription("4 days per week").assertIsNotSelected().performClick()
        compose.waitUntil(5_000) {
            runBlocking { container.userProfileRepository.getUserProfile().first().daysPerWeek } == 4
        }
        compose.onNodeWithContentDescription("4 days per week").assertIsSelected()
        compose.onNodeWithContentDescription("2 days per week").assertIsNotSelected()
        // A multi-select reads as checkboxes that reflect and change the saved goals; the slider speaks minutes.
        val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        val current = hasText(localized.getString(FitnessGoal.GENERAL_FITNESS.labelRes), substring = true) and checkbox
        val other = hasText(localized.getString(FitnessGoal.STRENGTH.labelRes), substring = true) and checkbox
        list.performScrollToNode(current)
        // The last goal is unavailable rather than silently refused, and a tap changes nothing.
        compose.onNode(current).assertIsOn().assertIsNotEnabled().performClick()
        compose.waitForIdle()
        assertThat(container.userProfileRepository.getUserProfile().first().goals).contains(FitnessGoal.GENERAL_FITNESS)
        list.performScrollToNode(other)
        compose.onNode(other).assertIsOff().performClick()
        compose.waitUntil(5_000) {
            FitnessGoal.STRENGTH in runBlocking { container.userProfileRepository.getUserProfile().first().goals }
        }
        list.performScrollToNode(hasContentDescription(localized.getString(R.string.profile_preferred_duration)))
        assertThat(
            compose.onAllNodes(hasContentDescription(localized.getString(R.string.profile_preferred_duration)))
                .fetchSemanticsNodes().single().config.getOrNull(SemanticsProperties.StateDescription)
        ).contains("min")

        // Each muscle's priority is a radio group: the selected one says so, and a tap moves it.
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        // A muscle not already at High: some start there.
        val high = hasContentDescription(" priority: " + localized.getString(PriorityLevel.HIGH.labelRes), substring = true) and radio
        val notYet = high and SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
        list.performScrollToNode(notYet)
        val name = compose.onAllNodes(notYet)[0].fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        val before = runBlocking { container.userProfileRepository.getUserProfile().first().musclePriorities }
        compose.onNode(hasContentDescription(name) and radio).performClick()
        compose.waitUntil(5_000) {
            runBlocking { container.userProfileRepository.getUserProfile().first().musclePriorities } != before
        }
        compose.onNode(hasContentDescription(name) and radio).assertIsSelected()

        // The unit, on App preferences, is a radio group too.
        compose.onNodeWithContentDescription(localized.getString(R.string.action_back)).performClick()
        compose.onNodeWithText(localized.getString(R.string.guided_app_preferences)).performClick()
        val pounds = hasText(localized.getString(WeightUnit.LBS.shortLabelRes)) and radio
        val kilograms = hasText(localized.getString(WeightUnit.KG.shortLabelRes)) and radio
        compose.waitForIdle()
        list = compose.onAllNodes(hasScrollAction())[0]
        list.performScrollToNode(kilograms)
        compose.onNode(pounds).assertIsSelected()
        compose.onNode(kilograms).assertIsNotSelected().performClick()
        compose.waitUntil(5_000) {
            runBlocking { container.userProfileRepository.getUserProfile().first().preferredUnit } == WeightUnit.KG
        }
        compose.onNode(kilograms).assertIsSelected()
        compose.onNode(pounds).assertIsNotSelected()
    }

    private fun sweep(locale: Locale, theme: ThemePreference, fontScale: Float, narrow: Boolean = fontScale > 1f) = runBlocking {
        saveProfile(completed = true)
        seedRecorded("recorded")
        saveRoutine()
        show(locale, theme, fontScale, AppRoutes.TODAY, narrow)
        // The interim layout already has a heading, and no list; wait for the built workout.
        compose.waitUntilAtLeastOneExists(hasTestTag("today-list"), 15_000)
        scan(AppRoutes.TODAY)
        // After the first scan: an open session changes Today, and disables its regenerate control.
        seedActive("active")
        // Back to the top, where the open session's banner is, once Room has delivered it.
        compose.onNodeWithTag("today-list").performScrollToIndex(0)
        compose.waitUntilAtLeastOneExists(hasText(localized.getString(R.string.guided_train_resume)), 10_000)
        scan(AppRoutes.TODAY)
        listOf(
            AppRoutes.PROGRESS, AppRoutes.TRENDS, AppRoutes.MUSCLES, AppRoutes.EXERCISES, AppRoutes.CONDITION_REFERENCE,
            AppRoutes.PROFILE, AppRoutes.CREDITS, AppRoutes.PROGRAM,
            AppRoutes.HISTORY, AppRoutes.historyDetail("recorded"), AppRoutes.WORKOUT_TEMPLATES,
            AppRoutes.TEMPLATE_NEW, AppRoutes.editTemplate("saved"), AppRoutes.activeWorkout("active"),
            // A finished session's route shows the workout summary.
            AppRoutes.activeWorkout("recorded")
        ).forEach { route ->
            compose.runOnIdle { nav.navigate(route) }
            compose.waitUntil(10_000) { nav.currentBackStackEntry?.let { routeOf(it) } == route }
            // These routes show their heading while still loading; only their content proves they finished.
            loadedContent(route)?.let { compose.waitUntilAtLeastOneExists(hasText(it, substring = true), 15_000) }
            scan(route)
            if (route == AppRoutes.PROFILE) scanSettingsPages()
        }
        report()
    }

    /** Profile opens on an overview; each settings page is its own screen, so each is opened and scanned. */
    private fun scanSettingsPages() {
        listOf(
            R.string.guided_training_preferences, R.string.guided_equipment, R.string.guided_movement_restrictions,
            R.string.guided_app_preferences, R.string.guided_your_data
        ).forEach { title ->
            val name = localized.getString(title)
            // Moving between pages is not what is checked; each page is scanned once it shows.
            withoutActionChecks {
                compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(name))
                compose.onNodeWithText(name).performClick()
            }
            compose.waitForIdle()
            scan("profile/$name")
            withoutActionChecks { compose.onNodeWithContentDescription(localized.getString(R.string.action_back)).performClick() }
            compose.waitForIdle()
        }
    }

    private fun loadedContent(route: String): String? = when (route) {
        AppRoutes.PROGRAM -> localized.getString(R.string.program_start)
        AppRoutes.CREDITS -> localized.getString(R.string.credits_illustrations_heading)
        AppRoutes.HISTORY, AppRoutes.historyDetail("recorded") -> LONG_SESSION_NAME
        AppRoutes.WORKOUT_TEMPLATES, AppRoutes.editTemplate("saved") -> "Saved routine"
        AppRoutes.TEMPLATE_NEW -> localized.getString(R.string.editor_name_label)
        // The library loads from assets after the title shows; the about text only appears with the list.
        AppRoutes.CONDITION_REFERENCE -> localized.getString(R.string.guided_research_about)
        else -> null
    }

    private fun routeOf(entry: NavBackStackEntry): String {
        var route = entry.destination.route.orEmpty()
        entry.arguments?.let { args ->
            args.keySet().forEach { key -> route = route.replace("{$key}", args.getString(key).orEmpty()) }
        }
        return route
    }

    private fun show(locale: Locale, theme: ThemePreference, fontScale: Float, expected: String, narrow: Boolean = fontScale > 1f) {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
            this.fontScale = fontScale
            // The narrow box below stands in for a narrow screen, and the app reads this to decide.
            if (narrow) screenWidthDp = 320
        }
        localized = context.createConfigurationContext(configuration)
        // The checks need to run on the composed tree; API 26-33 only get the structural scan.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) compose.enableAccessibilityChecks()
        compose.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(
                LocalContext provides localized,
                // A localized context is no longer the activity, and Profile launches document pickers.
                LocalActivityResultRegistryOwner provides compose.activity,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(context.resources.displayMetrics.density, fontScale)
            ) {
                WallCrawlTheme(themePreference = theme) {
                    Box(if (narrow) Modifier.width(320.dp) else Modifier) { WallCrawlApp(container, nav) }
                }
            }
        }
        compose.waitUntil(10_000) { nav.currentDestination?.route == expected }
    }

    /** Scans the route as it first appears, then down its main list until it stops moving. */
    private fun scan(route: String) {
        // Most loading states have no heading; slow devices stay on them for a while. The routes that do
        // show one while loading wait for their content first; see loadedContent().
        runCatching { compose.waitUntilAtLeastOneExists(isHeading(), 15_000) }
        compose.waitForIdle()
        structural(route, top = true)
        atf(route, top = true)
        var position = -1f
        // Slow, so it does not fling past rows between stops. ponytail: 12 swipes do not reach the end of the 302-item exercise list; Limitations says so.
        repeat(12) {
            val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
            val list = compose.onAllNodes(vertical).fetchSemanticsNodes().firstOrNull() ?: return
            val now = list.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: return
            if (now == position) return
            position = now
            withoutActionChecks { compose.onAllNodes(vertical)[0].performTouchInput { swipeUp(durationMillis = 1_000) } }
            compose.waitForIdle()
            structural(route, top = false)
            atf(route, top = false)
        }
    }

    /** ATF runs before an action and would cancel it on a waived report; callers run [atf] themselves. */
    private fun withoutActionChecks(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return action()
        compose.disableAccessibilityChecks()
        try {
            action()
        } finally {
            compose.enableAccessibilityChecks()
        }
    }

    private fun atf(route: String, top: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        checked(route, top) { compose.onRoot().tryPerformAccessibilityChecks() }
    }

    /**
     * ATF also runs on each action; its report becomes a finding, and anything else still fails the
     * test. Below the top of a route it measures what is visible, so a control half under the edge
     * looks small; [structural] measures true sizes there, and ATF's other checks still count.
     */
    private fun checked(route: String, top: Boolean, action: () -> Unit) {
        try {
            action()
        } catch (e: RuntimeException) {
            if (e.javaClass.simpleName != "AccessibilityViewCheckException") throw e
            // One finding per result, so a waiver for one control cannot take others with it. Anything
            // the check says counts, whatever its wording, except its "There were N results" header.
            e.message.orEmpty().lines()
                .filter { it.isNotBlank() && !ATF_HEADER.containsMatchIn(it) && (top || "touch target" !in it) }
                .forEach { findings += "$route ATF: $it" }
        }
    }

    private fun structural(route: String, top: Boolean) {
        val density = compose.activity.resources.displayMetrics.density
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        var headings = 0
        fun walk(node: SemanticsNode) {
            // Inferred from one failure: a lazy list can compose a row ahead of showing it, so it is not measured yet.
            if (!node.layoutInfo.isPlaced) return
            val c = node.config
            if (c.contains(SemanticsProperties.Heading)) headings++
            val descriptions = c.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            val texts = c.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            val editable = c.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
            val spoken = descriptions + texts
            spoken.groupBy { it.trim() }.filter { it.key.isNotEmpty() && it.value.size > 1 }.keys.forEach {
                findings += "$route read twice: \"$it\""
            }
            val laidOut = node.layoutInfo
            if ((texts.isNotEmpty() || descriptions.isNotEmpty()) && (laidOut.width == 0 || laidOut.height == 0)) {
                findings += "$route squeezed to nothing: ${spoken.joinToString()}"
            }
            if (c.contains(SemanticsProperties.EditableText) && spoken.none { it.isNotBlank() }) {
                findings += "$route field has no name"
            }
            val actionable = c.contains(SemanticsActions.OnClick)
            val inlineLink = c.getOrNull(SemanticsProperties.LinkTestMarker) != null
            if (actionable && !inlineLink) {
                val name = (spoken + editable + c.getOrNull(SemanticsProperties.PaneTitle).orEmpty()).filter { it.isNotBlank() }
                    .joinToString().ifBlank { node.children.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.joinToString() }
                if (name.isBlank()) findings += "$route unnamed control at ${node.boundsInRoot}"
                if (!c.contains(SemanticsProperties.Role) && !c.contains(SemanticsProperties.EditableText)) {
                    findings += "$route control has no role: $name"
                }
                // Layout sizes ignore a list clipping the control at its edge; the outer one includes
                // Material's minimum interactive padding, which is also the control's touch area.
                val w = maxOf(node.size.width, node.layoutInfo.width).toFloat() / density
                val h = maxOf(node.size.height, node.layoutInfo.height).toFloat() / density
                if (w < MIN_TARGET_DP - 0.5f || h < MIN_TARGET_DP - 0.5f) {
                    findings += "$route target %.0fx%.0f dp: %s".format(w, h, name)
                }
            }
            node.children.forEach(::walk)
        }
        roots.forEach(::walk)
        // Words broken across lines, which only show in the laid-out text of the unmerged tree.
        fun walkText(node: SemanticsNode) {
            if (!node.layoutInfo.isPlaced) return
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.forEach { layout ->
                val text = layout.layoutInput.text.text
                (0 until layout.lineCount - 1).forEach { line ->
                    val end = layout.getLineEnd(line)
                    val tokenStart = text.lastIndexOf(' ', end - 1) + 1
                    val tokenEnd = text.indexOf(' ', end).let { if (it < 0) text.length else it }
                    // A web address or file path has no break point to offer, and no layout can fit a word
                    // wider than a whole 320 dp screen's content without shrinking the reader's text.
                    val isAddress = '/' in text.substring(tokenStart, tokenEnd)
                    val tokenWidth = (tokenStart until tokenEnd).sumOf { layout.getBoundingBox(it).width.toDouble() }
                    val widerThanScreen = tokenWidth > layout.size.width && layout.size.width / density >= WIDEST_LINE_DP - 1
                    if (!isAddress && !widerThanScreen && end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter()) {
                        findings += "$route word broken: \"${text.substring(tokenStart, end)}|${text.substring(end, tokenEnd)}\" in \"$text\" (word %.0f dp, box %.0f dp)".format(tokenWidth / density, layout.size.width / density)
                    }
                }
            }
            node.children.forEach(::walkText)
        }
        compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::walkText)
        if (top && headings == 0) findings += "$route has no heading"
    }

    private fun report() {
        assertWithMessage(findings.distinct().joinToString("\n")).that(findings.distinct()).isEmpty()
    }

    private suspend fun saveRoutine(name: String = "Saved routine") {
        container.workoutTemplateRepository.saveTemplate(
            WorkoutTemplate(
                id = "saved", name = name,
                exercises = listOf(PlannedExercise("push-up", ExercisePrescription(exerciseType = ExerciseType.BODYWEIGHT_REPS, targetSets = 2, repRange = RepRange(8, 12))))
            )
        )
    }

    private suspend fun saveProfile(completed: Boolean) {
        container.userProfileRepository.saveProfile(UserProfile(name = "Alex", daysPerWeek = 2,
            availableEquipment = listOf(StandardEquipment.BODYWEIGHT), experienceLevel = ExperienceLevel.BEGINNER,
            goals = setOf(FitnessGoal.GENERAL_FITNESS),
            movementCapabilities = MovementCapabilities.from(MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }),
            onboardingCompleted = completed))
    }

    private suspend fun seedRecorded(id: String) {
        val done = System.currentTimeMillis() - 3_600_000L
        insert(id, LONG_SESSION_NAME, done - 600_000L, done, SessionStatus.COMPLETED, done - 60_000L, 12, true)
    }

    private suspend fun seedActive(id: String) {
        // Today resumes an open session, so the sweep needs its active route open only while scanning.
        val now = System.currentTimeMillis()
        insert(id, LONG_SESSION_NAME, now, null, SessionStatus.IN_PROGRESS, null, null, false)
    }

    private suspend fun insert(
        id: String, name: String, start: Long, end: Long?, status: SessionStatus,
        completedAt: Long?, reps: Int?, done: Boolean
    ) {
        database.workoutSessionDao().insertWorkout(
            WorkoutSessionEntity(id, name, start, end, 15, 10, WeightUnit.KG, status, WorkoutOrigin.CUSTOM_TEMPLATE, null, "", ""),
            listOf(WorkoutExerciseEntity(
                id = "$id-exercise", sessionId = id, exerciseId = "push-up", orderIndex = 0,
                exerciseType = ExerciseType.BODYWEIGHT_REPS, targetSets = 1, targetRepMin = 8, targetRepMax = 12,
                targetWeight = null, notes = ""
            )),
            listOf(WorkoutSetEntity(
                id = "$id-set", workoutExerciseId = "$id-exercise", setNumber = 1,
                exerciseType = ExerciseType.BODYWEIGHT_REPS, targetReps = 12, completedReps = reps,
                targetWeight = null, completedWeight = null, isCompleted = done, rpe = null, rir = null,
                completedAtTimestamp = completedAt, type = SetType.NORMAL
            ))
        )
    }

    private companion object {
        const val MIN_TARGET_DP = 48f
        const val WIDEST_LINE_DP = 288f

        /** As long as generated titles get: short seed names hid words breaking in the rows that show them. */
        const val LONG_SESSION_NAME = "Cuerpo completo · Acondicionamiento"
        val ATF_HEADER = Regex("^There (was|were) \\d+ accessibility results?:")
    }
}
