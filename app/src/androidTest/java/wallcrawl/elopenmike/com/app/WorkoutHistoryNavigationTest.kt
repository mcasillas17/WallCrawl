package wallcrawl.elopenmike.com.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.AppContainer
import wallcrawl.elopenmike.com.DefaultAppContainer
import wallcrawl.elopenmike.com.WallCrawlApplication
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import wallcrawl.elopenmike.com.core.ai.TrainingProgramStateProvider
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.repository.OfflineDeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineProgressRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutHistoryRepository
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutOrigin
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.test.hasLiveRegion

@RunWith(AndroidJUnit4::class)
class WorkoutHistoryNavigationTest {
    private val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var container: AppContainer
    private lateinit var navigation: NavHostController
    private lateinit var restoration: StateRestorationTester
    // When set, a template start waits for it, so the library can be seen mid-start.
    private var startGate: CompletableDeferred<Unit>? = null
    // Starts the list accepted (its first act is reading the template, before it suspends),
    // and those the workout repository has answered.
    private val startsAccepted = java.util.concurrent.atomic.AtomicInteger()
    private val startsFinished = java.util.concurrent.atomic.AtomicInteger()

    // Compose and navigation ViewModels must stop observing Room before it is closed.
    @get:Rule val rules = RuleChain.outerRule(object : ExternalResource() {
        override fun after() {
            if (::database.isInitialized) database.close()
        }
    }).around(compose).around(object : ExternalResource() {
        // A test can end mid-transition; disposing a composition that is still recomposing
        // crashed Compose's slot table on CI, so each test settles before the screen is torn down.
        override fun after() = compose.waitForIdle()
    })

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        val gate = Mutex()
        val assets = DefaultAppContainer(context)
        container = object : AppContainer by assets {
            override val database = this@WorkoutHistoryNavigationTest.database
            override val userProfileRepository = OfflineUserProfileRepository(database.userProfileDao(), gate)
            private val offlineWorkouts = OfflineWorkoutRepository(
                database.workoutSessionDao(), database.workoutSetDao()
            )
            override val workoutRepository = object :
                wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository by offlineWorkouts {
                override suspend fun startWorkoutFromTemplate(
                    template: wallcrawl.elopenmike.com.core.model.WorkoutTemplate,
                    userProfile: UserProfile
                ): wallcrawl.elopenmike.com.core.model.WorkoutSession {
                    startGate?.await()
                    return offlineWorkouts.startWorkoutFromTemplate(template, userProfile)
                        .also { startsFinished.incrementAndGet() }
                }
            }
            override val workoutHistoryRepository = OfflineWorkoutHistoryRepository(database, gate)
            override val programRepository = wallcrawl.elopenmike.com.core.database.repository.OfflineProgramRepository(database, gate)
            override val deloadRepository = OfflineDeloadRepository(database.deloadPreferencesDao(), gate)
            // In-memory too, so the template tests below never read the device's own data.
            private val offlineTemplates = wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository(
                database.workoutTemplateDao(), exerciseCatalog, gate
            )
            override val workoutTemplateRepository = object :
                wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository by offlineTemplates {
                override suspend fun getTemplate(templateId: String) =
                    offlineTemplates.getTemplate(templateId.also { startsAccepted.incrementAndGet() })
            }
            override val weeklyDoseLedgerRepository = OfflineWeeklyDoseLedgerRepository(
                database.completedWorkoutHistoryDao(), database.weeklyDoseLedgerStateDao(),
                workoutGuideCatalogSource
            )
            override val progressRepository = OfflineProgressRepository(
                database, workoutGuideCatalogSource, weeklyDoseLedgerRepository, gate
            )
            override val localDataBackupRepository = OfflineLocalDataBackupRepository(
                database.localDataBackupDao(), "test", 1L, { null }, gate
            )
            override val workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                userProfileRepository, workoutRepository, exerciseCatalog, exerciseFilter,
                workoutHistoryAnalyzer, plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
                trainingProgramStateProvider = TrainingProgramStateProvider(weeklyDoseLedgerRepository),
                deloadRepository = deloadRepository,
                programRepository = programRepository
            )
        }
        container.userProfileRepository.saveProfile(UserProfile(onboardingCompleted = true))
        repeat(25) { index -> seed("history-$index", index) }
    }

    @Test fun databaseRemainsOpenUntilCompositionIsDisposed() {
        compose.setContent {
            DisposableEffect(Unit) {
                onDispose { assertThat(database.isOpen).isTrue() }
            }
        }
        compose.waitForIdle()
    }

    @Test fun aDoubleTapOnCreateWorkoutOpensOneEditor() {
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.WORKOUT_TEMPLATES) }
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.WORKOUT_TEMPLATES }
        // The button belongs to the empty state, which appears once the library has loaded.
        val createLabel = context.getString(R.string.templates_action_create)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(createLabel)).fetchSemanticsNodes().size == 1
        }
        val create = compose.onNodeWithText(createLabel)
        // Both taps land before a frame, as a fast double tap does during the transition.
        compose.mainClock.autoAdvance = false
        create.performClick()
        create.performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertThat(navigation.currentBackStack.value.count { it.destination.route == AppRoutes.TEMPLATE_NEW })
                .isEqualTo(1)
        }
    }

    @Test fun aDoubleTapOnTheLibraryBackArrowLeavesOnce() {
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.WORKOUT_TEMPLATES) }
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.WORKOUT_TEMPLATES }
        compose.waitForIdle()
        val back = compose.onNodeWithContentDescription(context.getString(R.string.action_back))
        // Both taps land before a frame, as a fast double tap does during the transition.
        compose.mainClock.autoAdvance = false
        back.performClick()
        back.performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertThat(navigation.currentDestination?.route).isEqualTo(AppRoutes.TODAY) }
    }

    @Test fun whileAStartIsInFlightNothingElseInTheLibraryOpens() {
        val gate = CompletableDeferred<Unit>()
        startGate = gate
        saveNavTemplate()
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.WORKOUT_TEMPLATES) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(context.getString(R.string.template_action_start)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.template_action_start)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(context.getString(R.string.template_action_starting)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.template_action_starting)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.templates_create_content_description))
            .assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.action_edit)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.action_delete)).assertIsNotEnabled()
        // Leaving now would cancel a start that may already have written its session.
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).assertIsNotEnabled()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        compose.runOnIdle {
            assertThat(navigation.currentDestination?.route).isEqualTo(AppRoutes.WORKOUT_TEMPLATES)
        }

        gate.complete(Unit)
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.ACTIVE_WORKOUT }
    }

    @Test fun aStartThatFinishesAfterRecreationOpensTheWorkoutThroughTheNewNavigator() {
        val gate = CompletableDeferred<Unit>()
        startGate = gate
        saveNavTemplate()
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.WORKOUT_TEMPLATES) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(context.getString(R.string.template_action_start)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.template_action_start)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(context.getString(R.string.template_action_starting)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // A rotation mid-start: the composition and its navigator are rebuilt, the list's
        // ViewModel and its running start are not.
        restoration.emulateSavedInstanceStateRestore()
        gate.complete(Unit)
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.ACTIVE_WORKOUT }
        compose.runOnIdle {
            assertThat(navigation.currentBackStack.value.count { it.destination.route == AppRoutes.ACTIVE_WORKOUT })
                .isEqualTo(1)
        }
    }

    @Test fun startingATemplateAgainDuringTheTransitionOpensOneWorkout() {
        saveNavTemplate()
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.WORKOUT_TEMPLATES) }
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.WORKOUT_TEMPLATES }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(context.getString(R.string.template_action_start)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val start = compose.onNodeWithText(context.getString(R.string.template_action_start))
        start.performClick()
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.ACTIVE_WORKOUT }
        // The list is still composed while the transition runs; its start button is enabled
        // again once the first start returned.
        // The second start is held until the list has left composition, the slowest case:
        // a start that finished then would have nobody on screen to consume it.
        val second = CompletableDeferred<Unit>()
        startGate = second
        compose.mainClock.autoAdvance = false
        start.assertIsEnabled()
        start.performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        second.complete(Unit)
        // The repository answers off the main thread, where idling does not reach. A start
        // that got through was counted the moment it was tapped, so it is waited for here.
        compose.waitUntil(10_000) { startsFinished.get() == startsAccepted.get() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertThat(navigation.currentBackStack.value.count { it.destination.route == AppRoutes.ACTIVE_WORKOUT })
                .isEqualTo(1)
        }
        // Back reaches the list and stays there: the tap during the transition left no
        // started session behind to send the user forward again.
        compose.runOnIdle { navigation.popBackStack() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertThat(navigation.currentDestination?.route).isEqualTo(AppRoutes.WORKOUT_TEMPLATES)
        }
    }

    @Test fun aRefusedStartIsAnnouncedAndOpeningTheEditorClearsIt() {
        runBlocking {
            // Written beneath the repository, as a template saved before units were recorded.
            database.workoutTemplateDao().replaceTemplate(
                wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity(
                    id = "legacy", name = "Legacy", notes = "",
                    createdAtTimestamp = 1L, updatedAtTimestamp = 1L, weightUnit = null
                ),
                listOf(
                    wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity(
                        templateId = "legacy", orderIndex = 0, exerciseId = "barbell-back-squat",
                        exerciseType = wallcrawl.elopenmike.com.core.model.ExerciseType.WEIGHT_REPS,
                        targetSets = 3, targetRepMin = 5, targetRepMax = 8, targetWeight = 100.0,
                        targetAssistanceWeight = null, targetDurationSeconds = null,
                        targetDistanceMeters = null, restSeconds = 90, notes = ""
                    )
                )
            )
        }
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.WORKOUT_TEMPLATES) }
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.WORKOUT_TEMPLATES }
        val startLabel = context.getString(R.string.template_action_start)
        // The list loads from Room after navigation settles.
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(startLabel)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(startLabel).performClick()
        val message = context.getString(R.string.template_error_unit_unconfirmed)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(message)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(
            hasText(message).and(hasLiveRegion())
        ).assertExists()

        compose.onNodeWithContentDescription(context.getString(R.string.action_edit)).performClick()
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.TEMPLATE_EDIT }
        compose.runOnIdle { navigation.popBackStack() }
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.WORKOUT_TEMPLATES }
        compose.onAllNodes(hasText(message)).assertCountEquals(0)
    }

    @Test fun progressCardOpensPersistedDetailAndBackReturnsToProgress() {
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.PROGRESS) }
        val name = "Recorded workout 24"
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(context.getString(R.string.progress_activity_heading)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
        capture("history-progress-en")
        compose.onNodeWithText(name).performClick()
        compose.waitUntil(10_000) { navigation.currentDestination?.route == "history/{sessionId}" }
        assertThat(navigation.currentBackStackEntry?.arguments?.getString("sessionId"))
            .isEqualTo("history-24")
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Recorded workout 24")).fetchSemanticsNodes().isNotEmpty()
        }
        capture("history-detail-en")
        compose.runOnIdle { navigation.popBackStack() }
        assertThat(navigation.currentDestination?.route).isEqualTo(AppRoutes.PROGRESS)
    }

    @Test fun anAnalyticsObservationOpensThatExactWorkoutAndTheFilterSurvivesRecreation() = runBlocking {
        val now = System.currentTimeMillis()
        seedPushUps("analytics-older", "Analytics push older", now - 9 * DAY, 10)
        seedPushUps("analytics-newer", "Analytics push newer", now - 60_000L, 12)
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.PROGRESS) }
        selectPushUpScope()
        restoration.emulateSavedInstanceStateRestore()
        // Bounded saved state: the selection, not the chart, returns after recreation.
        scrollProgressTo(hasText(context.getString(R.string.analytics_shape_bodyweight)))
        scrollProgressTo(hasText("Analytics push older", substring = true))
        compose.onNode(hasText("Analytics push older", substring = true)).performClick()
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.HISTORY_DETAIL }
        assertThat(navigation.currentBackStackEntry?.arguments?.getString("sessionId")).isEqualTo("analytics-older")
        compose.runOnIdle { navigation.popBackStack() }
        scrollProgressTo(hasText(context.getString(R.string.analytics_choose_exercise, "Push-up")))
    }

    @Test fun anAnalyticsWeekListsItsContributorsAndEachOpensItsOwnWorkout() = runBlocking {
        val now = System.currentTimeMillis()
        seedPushUps("analytics-this-week", "Analytics push this week", now - 60_000L, 12)
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.PROGRESS) }
        scrollProgressTo(hasText("(this week so far)", substring = true))
        compose.onNode(hasText("(this week so far)", substring = true)).performClick()
        scrollProgressTo(hasText("Analytics push this week", substring = true))
        capture("progress-analytics-en")
        compose.onNode(hasText("Analytics push this week", substring = true)).performClick()
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.HISTORY_DETAIL }
        assertThat(navigation.currentBackStackEntry?.arguments?.getString("sessionId")).isEqualTo("analytics-this-week")
    }

    private fun selectPushUpScope() {
        val chip = context.getString(R.string.analytics_scope_exercise)
        scrollProgressTo(hasText(chip))
        compose.onNodeWithText(chip).performClick()
        val menuItem = androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isPopup()) and hasText("Push-up")
        compose.waitUntil(10_000) { compose.onAllNodes(menuItem).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(menuItem).performClick()
    }

    /** Lazy rows below the fold are not composed, so scroll until the (async) node exists. */
    private fun scrollProgressTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        compose.waitUntil(10_000) {
            runCatching { compose.onNode(hasScrollAction()).performScrollToNode(matcher) }.isSuccess
        }
    }

    private suspend fun seedPushUps(id: String, name: String, completedAt: Long, reps: Int) {
        database.workoutSessionDao().insertWorkout(
            WorkoutSessionEntity(
                id, name, completedAt - 600_000L, completedAt, 15, 10, WeightUnit.KG,
                SessionStatus.COMPLETED, WorkoutOrigin.CUSTOM_TEMPLATE, null, "", ""
            ),
            listOf(WorkoutExerciseEntity(
                id = "$id-exercise", sessionId = id, exerciseId = "push-up", orderIndex = 0,
                exerciseType = wallcrawl.elopenmike.com.core.model.ExerciseType.BODYWEIGHT_REPS,
                targetSets = 1, targetRepMin = 8, targetRepMax = 12, targetWeight = null, notes = ""
            )),
            listOf(WorkoutSetEntity(
                id = "$id-set", workoutExerciseId = "$id-exercise", setNumber = 1,
                exerciseType = wallcrawl.elopenmike.com.core.model.ExerciseType.BODYWEIGHT_REPS,
                targetReps = reps, completedReps = reps, targetWeight = null, completedWeight = null,
                isCompleted = true, rpe = null, rir = null, completedAtTimestamp = completedAt - 60_000L,
                type = SetType.NORMAL
            ))
        )
    }

    @Test fun directOldDetailSurvivesSavedStateRecreationAndDoesNotWrite() = runBlocking {
        val original = container.workoutRepository.getSessionById("history-0")
        val choice = container.deloadRepository.get()
        show()
        compose.runOnIdle { navigation.navigate("history/history-0") }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Recorded workout 0")).fetchSemanticsNodes().isNotEmpty()
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(10_000) {
            navigation.currentBackStackEntry?.arguments?.getString("sessionId") == "history-0"
        }
        assertThat(container.workoutRepository.getSessionById("history-0")).isEqualTo(original)
        assertThat(container.deloadRepository.get()).isEqualTo(choice)
        assertThat(container.workoutRepository.getActiveSessionOnce()).isNull()
    }

    @Test fun routeKeepsSpecialCharactersInThePersistedId() = runBlocking {
        val id = "archived/session ?#%+á"
        seed(id, 26)
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.historyDetail(id)) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Recorded workout 26")).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(navigation.currentBackStackEntry?.arguments?.getString("sessionId")).isEqualTo(id)
        assertThat(AppRoutes::class.java.fields.map { it.name }).doesNotContain("WORKOUT_SUMMARY")
    }

    @Test fun listMakesOldWorkoutsReachableBeyondRecentOverview() {
        show()
        compose.runOnIdle { navigation.navigate("history") }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Recorded workout 24")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Older workouts"))
        compose.onNodeWithText("Older workouts").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Recorded workout 4")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Recorded workout 0")).fetchSemanticsNodes().isNotEmpty()
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Recorded workout 4")).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(hasText("Recorded workout 0")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Recorded workout 0"))
        capture("history-older-en")
        compose.onNodeWithText("Recorded workout 0").performClick()
        compose.waitUntil(10_000) {
            navigation.currentBackStackEntry?.arguments?.getString("sessionId") == "history-0"
        }
    }

    @Test fun spanishLargeTextDetailUsesRecordedTextInLightTheme() {
        show(Locale.forLanguageTag("es-MX"), ThemePreference.LIGHT, 1.8f)
        compose.runOnIdle { navigation.navigate(AppRoutes.historyDetail("history-0")) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Recorded workout 0")).fetchSemanticsNodes().isNotEmpty()
        }
        capture("history-detail-es-large-light")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Stored exercise note"))
        capture("history-exercise-es-large-light")
    }

    @Test fun spanishDetailInDarkThemeUsesTheRealRoomProjection() {
        show(Locale.forLanguageTag("es-MX"), ThemePreference.DARK)
        compose.runOnIdle { navigation.navigate(AppRoutes.historyDetail("history-0")) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Recorded workout 0")).fetchSemanticsNodes().isNotEmpty()
        }
        capture("history-detail-es-dark")
    }

    @Test fun deletionWhileDetailIsOpenClearsTheBackStackBeforeRestore() = runBlocking {
        val archive = ByteArrayOutputStream()
        container.localDataBackupRepository.exportTo { archive }
        show()
        compose.runOnIdle { navigation.navigate("history/history-0") }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Recorded workout 0")).fetchSemanticsNodes().isNotEmpty()
        }
        container.localDataBackupRepository.deleteAllLocalData()
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.ONBOARDING }
        assertThat(compose.onAllNodes(hasText("Recorded workout 0")).fetchSemanticsNodes()).isEmpty()
        container.localDataBackupRepository.restoreFrom(ByteArrayInputStream(archive.toByteArray()))
        compose.waitForIdle()
        assertThat(navigation.currentDestination?.route).isNotEqualTo("history/{sessionId}")
    }

    @Test fun completedActiveRouteKeepsDoneFlowWithoutCompletingAgain() = runBlocking {
        val original = container.workoutRepository.getSessionById("history-0")
        show()
        compose.runOnIdle { navigation.navigate(AppRoutes.activeWorkout("history-0")) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(context.getString(R.string.summary_action_done)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        capture("history-summary-en")
        compose.onNodeWithText(context.getString(R.string.summary_action_done))
            .performScrollTo()
        capture("history-summary-actions-en")
        compose.onNodeWithText(context.getString(R.string.summary_action_done)).performClick()
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.PROGRESS }
        assertThat(container.workoutRepository.getSessionById("history-0")).isEqualTo(original)
    }

    @Test fun archiveRoundTripKeepsLegacyDetailWithoutInventingProvenance() = runBlocking {
        val archive = ByteArrayOutputStream()
        container.localDataBackupRepository.exportTo { archive }
        val restoredDatabase = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        try {
            val gate = Mutex()
            val backup = OfflineLocalDataBackupRepository(
                restoredDatabase.localDataBackupDao(), "test", 1L, { null }, gate
            )
            backup.restoreFrom(ByteArrayInputStream(archive.toByteArray()))
            val detail = OfflineWorkoutHistoryRepository(restoredDatabase, gate)
                .observeDetail("history-0").first()
            assertThat(detail?.session).isEqualTo(container.workoutRepository.getSessionById("history-0"))
            assertThat(detail?.recommendation).isNull()
        } finally {
            restoredDatabase.close()
        }

        // Explicit opt-in for installed-app lifecycle evidence on a dedicated empty emulator.
        if (InstrumentationRegistry.getArguments().getString("seedHistoryDemo") == "true") {
            val application = context.applicationContext as WallCrawlApplication
            val backup = application.container.localDataBackupRepository
            check(backup.isRestoreAllowed()) { "History demo requires a fresh destination." }
            backup.restoreFrom(ByteArrayInputStream(archive.toByteArray()))
        }
    }

    private fun saveNavTemplate() {
        runBlocking {
            container.workoutTemplateRepository.saveTemplate(
                wallcrawl.elopenmike.com.core.model.WorkoutTemplate(
                    id = "nav-template",
                    name = "Nav template",
                    exercises = listOf(
                        wallcrawl.elopenmike.com.core.model.PlannedExercise(
                            "push-up",
                            wallcrawl.elopenmike.com.core.model.ExercisePrescription(
                                exerciseType = wallcrawl.elopenmike.com.core.model.ExerciseType.BODYWEIGHT_REPS,
                                targetSets = 1,
                                repRange = wallcrawl.elopenmike.com.core.model.RepRange(5, 8)
                            )
                        )
                    )
                )
            )
        }
    }

    private fun show(
        locale: Locale = Locale.ENGLISH,
        theme: ThemePreference = ThemePreference.DARK,
        fontScale: Float = 1f
    ) {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
            this.fontScale = fontScale
        }
        val localized = context.createConfigurationContext(configuration)
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            navigation = rememberNavController()
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(context.resources.displayMetrics.density, fontScale)
            ) {
                WallCrawlTheme(themePreference = theme) {
                    Box(if (fontScale > 1f) Modifier.width(320.dp) else Modifier) {
                        WallCrawlApp(container, navigation)
                    }
                }
            }
        }
        compose.waitUntil(10_000) { navigation.currentDestination?.route == AppRoutes.TODAY }
    }

    private companion object {
        const val DAY = 24 * 3_600_000L
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureHistoryScreenshots") != "true") return
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "history-screenshots")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private suspend fun seed(id: String, index: Int) {
        val start = 1_700_000_000_000L + index * 3_600_000L
        val exerciseId = "$id-exercise"
        database.workoutSessionDao().insertWorkout(
            WorkoutSessionEntity(
                id, "Recorded workout $index", start, start + 600_000L,
                15, 10, WeightUnit.KG, SessionStatus.COMPLETED,
                WorkoutOrigin.CUSTOM_TEMPLATE, null, "", "Original notes"
            ),
            listOf(WorkoutExerciseEntity(
                id = exerciseId, sessionId = id, exerciseId = "missing-catalog-id",
                orderIndex = 0, targetSets = 1, targetRepMin = 8, targetRepMax = 12,
                targetWeight = 40.0, notes = "Stored exercise note"
            )),
            listOf(WorkoutSetEntity(
                id = "$id-set", workoutExerciseId = exerciseId, setNumber = 1,
                targetReps = 12, completedReps = 10, targetWeight = 40.0,
                completedWeight = 42.5, isCompleted = true, rpe = null, rir = 3,
                completedAtTimestamp = start + 60_000L, type = SetType.NORMAL
            ))
        )
    }
}
