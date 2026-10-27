package wallcrawl.elopenmike.com.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.*
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.*
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

@RunWith(AndroidJUnit4::class)
class ProgramNavigationTest {
    private val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var container: AppContainer
    private lateinit var nav: NavHostController
    private lateinit var restoration: StateRestorationTester
    private var localized = context
    @get:Rule val rules = RuleChain.outerRule(object : ExternalResource() {
        override fun after() { if (::database.isInitialized) database.close() }
    }).around(compose)

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        val gate = Mutex()
        val assets = DefaultAppContainer(context)
        container = object : AppContainer by assets {
            override val database = this@ProgramNavigationTest.database
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
        container.userProfileRepository.saveProfile(UserProfile(name = "Program test profile", daysPerWeek = 2,
            availableEquipment = listOf(StandardEquipment.BODYWEIGHT), experienceLevel = ExperienceLevel.BEGINNER,
            goals = setOf(FitnessGoal.GENERAL_FITNESS),
            movementCapabilities = MovementCapabilities.from(MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }),
            onboardingCompleted = true))
    }

    @Test fun todayDisclosureExplainsProgramWeeksAndSharedCalendarAllowances() = runBlocking {
        enroll(); show(); chooseProgram()
        assertTodayDisclosureReadable(1f)
        capture("program-today-disclosure-en")
        assertThat((container.programRepository.get() as ProgramReadState.Supported).progress.completedSlots).isEqualTo(0)
        assertThat(container.workoutRepository.getActiveSessionOnce()).isNull()
    }

    @Test fun todayDisclosureRemainsReachableInSpanishAt320DpWithLargeText() = runBlocking {
        enroll(); show(Locale.forLanguageTag("es-MX"), 1.8f); chooseProgram()
        assertTodayDisclosureReadable(1.8f)
    }

    @Test fun todayAndProgressOpenManagementAndStaleConfirmationCannotReplaceAnotherRun() = runBlocking {
        show()
        click(R.string.program_manage)
        waitText(R.string.program_start)
        click(R.string.program_start)
        waitText(R.string.program_pause)
        val before = container.programRepository.getHeader()
        click(R.string.program_restart)
        waitText(R.string.program_confirm)
        val profile = container.userProfileRepository.getUserProfile().first()
        container.programRepository.act(ProgramAction.RESTART, ProgramExpectation(profile.id, profile.revision, before.programId, before.revision), true)
        val replacement = container.programRepository.getHeader()
        compose.onNodeWithText(text(R.string.program_confirm)).performClick()
        waitText(R.string.program_stale)
        assertThat(container.programRepository.getHeader()).isEqualTo(replacement)
        capture("program-stale-confirmation-en")
        compose.runOnIdle { nav.popBackStack(); nav.navigate(AppRoutes.PROGRESS) }
        waitText(R.string.program_manage)
        click(R.string.program_manage)
        assertThat(nav.currentDestination?.route).isEqualTo(AppRoutes.PROGRAM)
    }

    @Test fun explicitProgramSelectionSurvivesSavedStateAndPartialFinishRecordsItsFrozenRun() = runBlocking {
        enroll()
        show()
        chooseProgram()
        restoration.emulateSavedInstanceStateRestore()
        waitText(R.string.program_start_session)
        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("planning-PROGRAM"))
        compose.onNodeWithTag("planning-PROGRAM").assertIsSelected()
        capture("program-today-en")
        click(R.string.program_start_session)
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.ACTIVE_WORKOUT }
        val session = checkNotNull(container.workoutRepository.getActiveSessionOnce())
        val membership = checkNotNull(container.programRepository.observeAssociation(session.id).first())
        compose.onAllNodesWithText(text(R.string.workout_action_finish)).onFirst().performClick()
        waitText(R.string.program_partial_finish)
        capture("program-partial-finish-en")
        compose.onNodeWithText(text(R.string.finish_confirm_action)).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text(R.string.summary_action_done)).fetchSemanticsNodes().isNotEmpty() }
        assertThat((container.programRepository.get() as ProgramReadState.Supported).progress.completedSlots).isEqualTo(1)
        assertThat(container.workoutRepository.getSessionById(session.id)?.completedSetsCount).isEqualTo(0)
        compose.runOnIdle { nav.navigate(AppRoutes.historyDetail(session.id)) }
        waitText(R.string.program_recorded_heading)
        capture("program-history-en")
        assertThat(container.workoutHistoryRepository.observeDetail(session.id).first()?.programAssociation).isEqualTo(membership)
    }

    @Test fun spanishLargeTextKeepsControlsReachableAndModeAndSlotDoNotDependOnLocale() = runBlocking {
        enroll()
        show(Locale.forLanguageTag("es-MX"), 1.8f)
        click(R.string.program_manage)
        waitText(R.string.program_pause)
        capture("program-management-es-320-font180")
        click(R.string.program_pause)
        waitText(R.string.program_resume)
        click(R.string.program_resume)
        waitText(R.string.program_pause)
        click(R.string.program_restart)
        waitText(R.string.program_confirm)
        capture("program-confirmation-es-320-font180")
        compose.onNodeWithText(text(R.string.program_keep)).performClick()
        assertThat((container.programRepository.get() as ProgramReadState.Supported).progress.nextPosition?.slotOrdinal).isEqualTo(0)
    }

    @Test fun spanishPartialFinishExplanationAndActionsRemainReachable() = runBlocking {
        enroll(); show(Locale.forLanguageTag("es-MX"), 1.8f); chooseProgram(); click(R.string.program_start_session)
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.ACTIVE_WORKOUT }
        val finishLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(text(R.string.workout_action_finish), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(finishLayouts) }
        val finishLayout = finishLayouts.single()
        assertThat(finishLayout.didOverflowHeight).isFalse()
        repeat(finishLayout.lineCount) { line ->
            assertThat(finishLayout.getLineLeft(line)).isAtLeast(0f)
            assertThat(finishLayout.getLineRight(line)).isAtMost(finishLayout.size.width.toFloat())
        }
        compose.onAllNodesWithText(text(R.string.workout_action_finish)).onFirst().performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).assertExists()
        capture("program-partial-finish-es-320-font180-top")
        val explanation = compose.onNode(hasText(text(R.string.program_partial_finish)) and hasAnyAncestor(isDialog()))
        explanation.performScrollTo().assertIsDisplayed()
        val explanationLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        explanation.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(explanationLayouts) }
        val explanationLayout = explanationLayouts.single()
        assertThat(explanationLayout.didOverflowHeight).isFalse()
        repeat(explanationLayout.lineCount) { line ->
            assertThat(explanationLayout.getLineLeft(line)).isAtLeast(0f)
            assertThat(explanationLayout.getLineRight(line)).isAtMost(explanationLayout.size.width.toFloat())
        }
        val scroll = compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog()))
        assertThat(explanation.fetchSemanticsNode().positionInRoot.y + explanationLayout.getLineTop(0))
            .isAtLeast(scroll.fetchSemanticsNode().boundsInRoot.top)
        scroll.performTouchInput { swipeUp() }
        assertThat(explanation.fetchSemanticsNode().positionInRoot.y + explanationLayout.getLineBottom(explanationLayout.lineCount - 1))
            .isAtMost(scroll.fetchSemanticsNode().boundsInRoot.bottom)
        compose.onNodeWithText(text(R.string.finish_confirm_action)).assertIsEnabled().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.finish_confirm_keep_going)).assertIsEnabled().assertIsDisplayed()
        capture("program-partial-finish-es-320-font180")
        compose.onNodeWithText(text(R.string.finish_confirm_keep_going)).performClick()
        assertThat(container.workoutRepository.getActiveSessionOnce()).isNotNull()
    }

    @Test fun unsupportedConfigurationLeavesOrdinaryTemplatesAndActiveResumeAccessible() = runBlocking {
        enroll()
        database.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET configurationPolicyVersion='FUTURE_POLICY'")
        show()
        waitText(R.string.program_unsupported)
        click(R.string.today_action_open_templates)
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.WORKOUT_TEMPLATES }
        compose.runOnIdle { nav.popBackStack() }
        click(R.string.today_action_start)
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.ACTIVE_WORKOUT }
        val active = checkNotNull(container.workoutRepository.getActiveSessionOnce())
        assertThat(container.programRepository.observeAssociation(active.id).first()).isNull()
        compose.runOnIdle { nav.popBackStack() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("today-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("today-list").performScrollToIndex(0)
        waitText(R.string.today_action_resume)
        capture("program-unsupported-active-resume-en")
        click(R.string.today_action_resume)
        assertThat(nav.currentDestination?.route).isEqualTo(AppRoutes.ACTIVE_WORKOUT)
    }

    @Test fun profileMismatchHasReplaceAndEditActions() = runBlocking {
        enroll()
        val profile = container.userProfileRepository.getUserProfile().first()
        container.userProfileRepository.saveProfile(profile.copy(daysPerWeek = 3))
        show()
        click(R.string.program_manage)
        waitText(R.string.program_profile_mismatch)
        click(R.string.program_replace)
        waitText(R.string.program_confirm)
        compose.onNodeWithText(text(R.string.program_confirm)).performClick()
        waitText(R.string.program_pause)
        assertThat((container.programRepository.get() as ProgramReadState.Supported).enrollment.configuration.sessionsPerWeek).isEqualTo(3)
        capture("program-replacement-en")
    }

    @Test fun unsupportedFrozenProgramFinishKeepsTheActiveLoggerAndExplicitCancel() = runBlocking {
        enroll(); show(); chooseProgram(); click(R.string.program_start_session)
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.ACTIVE_WORKOUT }
        val session = checkNotNull(container.workoutRepository.getActiveSessionOnce())
        database.withTransaction {
            database.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET configurationPolicyVersion='FUTURE_POLICY'")
            database.openHelper.writableDatabase.execSQL("UPDATE program_session_associations SET configurationPolicyVersion='FUTURE_POLICY'")
            val association = database.programDao().getProgramAssociation(session.id)!!
            val digest = association.rawAssociationDigest()
            database.openHelper.writableDatabase.execSQL("UPDATE workout_recommendation_records SET programAssociationDigest=? WHERE sessionId=?", arrayOf(digest, session.id))
        }
        compose.runOnIdle { nav.popBackStack(); nav.navigate(AppRoutes.activeWorkout(session.id)) }
        waitText(R.string.workout_action_finish)
        compose.onAllNodesWithText(text(R.string.workout_action_finish)).onFirst().performClick()
        waitText(R.string.finish_confirm_action)
        compose.onNodeWithText(text(R.string.program_partial_finish), substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.finish_confirm_action)).performClick()
        waitText(R.string.program_finish_unsupported)
        assertThat(container.workoutRepository.getActiveSessionOnce()?.id).isEqualTo(session.id)
        capture("program-unsupported-finish-en")
        compose.onNodeWithContentDescription(text(R.string.workout_close_content_description)).performClick()
        waitText(R.string.discard_confirm_action)
        compose.onNodeWithText(text(R.string.discard_confirm_action)).performClick()
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.TODAY }
        assertThat(container.workoutRepository.getActiveSessionOnce()).isNull()
        assertThat(container.programRepository.observeAssociation(session.id).first()).isNull()
    }

    @Test fun unsupportedEnrollmentRefusalRemovesAdvancePromiseForAKnownFrozenSnapshot() = runBlocking {
        enroll(); show(); chooseProgram(); click(R.string.program_start_session)
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.ACTIVE_WORKOUT }
        val session = checkNotNull(container.workoutRepository.getActiveSessionOnce())
        database.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET configurationPolicyVersion='FUTURE_POLICY'")
        compose.onAllNodesWithText(text(R.string.workout_action_finish)).onFirst().performClick()
        waitText(R.string.finish_confirm_action)
        compose.onNodeWithText(text(R.string.finish_confirm_action)).performClick()
        waitText(R.string.program_finish_unsupported)
        assertThat(container.programRepository.observeAssociation(session.id).first()?.supportedSnapshot).isNotNull()
        compose.onNodeWithText(text(R.string.program_partial_finish), substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.workout_dismiss_error)).performClick()
        compose.onAllNodesWithText(text(R.string.workout_action_finish)).onFirst().performClick()
        waitText(R.string.finish_confirm_action)
        compose.onNodeWithText(text(R.string.program_partial_finish), substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.finish_confirm_keep_going)).performClick()
        compose.onNodeWithContentDescription(text(R.string.workout_close_content_description)).assertHasClickAction()
        assertThat(container.workoutRepository.getActiveSessionOnce()?.id).isEqualTo(session.id)
    }

    @Test fun programReadFailureHasRetryAndDoesNotRemoveOrdinaryOrManualAccess() = runBlocking {
        val original = container
        var failing = true
        container = object : AppContainer by original {
            override val programRepository = object : ProgramRepository by original.programRepository {
                override fun observe(profileId: String): kotlinx.coroutines.flow.Flow<ProgramReadState> = kotlinx.coroutines.flow.flow {
                    if (failing) throw android.database.sqlite.SQLiteException("injected storage read failure")
                    emitAll(original.programRepository.observe(profileId))
                }
            }
        }
        show(); waitText(R.string.program_read_failed)
        click(R.string.program_manage)
        waitText(R.string.program_read_failed)
        failing = false
        compose.onNodeWithText(text(R.string.action_try_again)).performClick()
        waitText(R.string.program_start)
        assertThat(container.programRepository.get()).isInstanceOf(ProgramReadState.Empty::class.java)
    }

    @Test fun completedRunHasNoNextSlotAndKeepsBoundedHistoryLinks() = runBlocking {
        enroll()
        repeat(8) { index ->
            val context = container.workoutGenerationContextBuilder.build(WorkoutPlanningIntent.PROGRAM)
            val plan = container.programValidator.validate(container.workoutPlanner.generateWorkout(context), context, allowRepair = true) as ProgramValidationResult.Valid
            val session = container.workoutRepository.startWorkoutFromGenerated(plan.workout, "Program test slot ${index + 1}",
                "Test profile", context.userProfile, plan.snapshot)
            container.workoutRepository.completeWorkout(session.id, 1)
        }
        show()
        click(R.string.program_manage)
        waitText(R.string.program_completed_body)
        val completed = container.programRepository.get() as ProgramReadState.Supported
        assertThat(completed.progress.nextPosition).isNull()
        assertThat(completed.progress.completedSlots).isEqualTo(8)
        capture("program-completed-en")
        compose.onNodeWithText("Block 2 · Week 4 · Session 2").performScrollTo().performClick()
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.HISTORY_DETAIL }
        waitText(R.string.program_recorded_heading)
    }

    @Test fun deletionDuringProgramConfirmationClearsProtectedNavigationAndSavedModeBeforeRestore() = runBlocking {
        enroll()
        val archive = ByteArrayOutputStream()
        container.localDataBackupRepository.exportTo { archive }
        val originalHeader = container.programRepository.getHeader()
        show(); chooseProgram(); click(R.string.program_manage); click(R.string.program_restart)
        waitText(R.string.program_confirm)
        container.localDataBackupRepository.deleteAllLocalData()
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.ONBOARDING }
        compose.onNodeWithText(text(R.string.program_confirm)).assertDoesNotExist()
        compose.onNodeWithTag("program-management").assertDoesNotExist()
        assertThat(nav.previousBackStackEntry).isNull()
        container.localDataBackupRepository.restoreFrom(ByteArrayInputStream(archive.toByteArray()))
        compose.runOnIdle {
            nav.navigate(AppRoutes.TODAY) {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        waitText(R.string.program_manage)
        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("planning-ORDINARY"))
        compose.onNodeWithTag("planning-ORDINARY").assertIsSelected()
        compose.onNodeWithText(text(R.string.program_confirm)).assertDoesNotExist()
        assertThat(container.programRepository.getHeader()).isEqualTo(originalHeader)
        click(R.string.program_manage)
        waitText(R.string.program_pause)
        compose.onNodeWithText(text(R.string.program_confirm)).assertDoesNotExist()
    }

    @Test fun productionArchiveCanSeedTheDedicatedProcessRecreationDemo() = runBlocking {
        enroll()
        val archive = ByteArrayOutputStream()
        container.localDataBackupRepository.exportTo { archive }
        if (InstrumentationRegistry.getArguments().getString("seedProgramDemo") == "true") {
            val destination = (context.applicationContext as WallCrawlApplication).container.localDataBackupRepository
            check(destination.isRestoreAllowed()) { "Program demo requires a fresh dedicated destination." }
            destination.restoreFrom(ByteArrayInputStream(archive.toByteArray()))
        }
        assertThat((container.programRepository.get() as ProgramReadState.Supported).progress.completedSlots).isEqualTo(0)
    }

    private fun assertTodayDisclosureReadable(fontScale: Float) {
        compose.onNodeWithTag("today-list").performScrollToNode(hasText(text(R.string.program_disclosure)))
        val disclosure = compose.onNodeWithText(text(R.string.program_disclosure))
        disclosure.performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        disclosure.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertThat(layout.layoutInput.density.fontScale).isEqualTo(fontScale)
        assertThat(layout.didOverflowHeight).isFalse()
        repeat(layout.lineCount) { line ->
            assertThat(layout.getLineLeft(line)).isAtLeast(0f)
            assertThat(layout.getLineRight(line)).isAtMost(layout.size.width.toFloat())
        }
    }

    private suspend fun enroll() {
        val profile = container.userProfileRepository.getUserProfile().first()
        container.programRepository.act(ProgramAction.START, ProgramExpectation(profile.id, profile.revision, null, 0))
    }
    private fun chooseProgram() {
        compose.waitUntil(15_000) { compose.onNodeWithTag("today-list").isDisplayed() }
        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("planning-PROGRAM"))
        compose.waitUntil(15_000) {
            compose.onNode(hasTestTag("planning-PROGRAM") and isEnabled()).isDisplayed()
        }
        compose.onNodeWithTag("planning-PROGRAM").performClick()
        compose.onNodeWithTag("today-list").performScrollToNode(hasText(text(R.string.today_plan_heading)))
        waitText(R.string.program_start_session)
    }
    private fun text(id: Int) = localized.getString(id)
    private fun waitText(id: Int) { compose.waitUntil(15_000) { compose.onAllNodesWithText(text(id), substring = true).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(id: Int) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("today-list").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(text(id)).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag("today-list").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("today-list").performScrollToNode(hasText(text(id)))
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText(text(id)) and hasClickAction()).fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        compose.onNodeWithText(text(id)).performScrollTo().performClick()
    }
    private fun show(locale: Locale = Locale.ENGLISH, fontScale: Float = 1f) {
        val configuration = Configuration(context.resources.configuration).apply { setLocales(LocaleList(locale)); this.fontScale = fontScale }
        localized = context.createConfigurationContext(configuration)
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
                LocalDensity provides Density(context.resources.displayMetrics.density, fontScale)) {
                WallCrawlTheme { Box(if (fontScale > 1f) Modifier.width(320.dp) else Modifier) { WallCrawlApp(container, nav) } }
            }
        }
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.TODAY }
        waitText(R.string.program_manage)
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureProgramScreenshots") != "true") return
        compose.waitForIdle()
        val folder = File(context.getExternalFilesDir(null), "program-screenshots")
        check(folder.isDirectory || folder.mkdirs())
        File(folder, "$name.png").outputStream().use { stream ->
            check(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }
}
