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
import androidx.compose.ui.test.hasScrollAction
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

@RunWith(AndroidJUnit4::class)
class WorkoutHistoryNavigationTest {
    private val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var container: AppContainer
    private lateinit var navigation: NavHostController
    private lateinit var restoration: StateRestorationTester

    // Compose and navigation ViewModels must stop observing Room before it is closed.
    @get:Rule val rules = RuleChain.outerRule(object : ExternalResource() {
        override fun after() {
            if (::database.isInitialized) database.close()
        }
    }).around(compose)

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        val gate = Mutex()
        val assets = DefaultAppContainer(context)
        container = object : AppContainer by assets {
            override val database = this@WorkoutHistoryNavigationTest.database
            override val userProfileRepository = OfflineUserProfileRepository(database.userProfileDao(), gate)
            override val workoutRepository = OfflineWorkoutRepository(
                database.workoutSessionDao(), database.workoutSetDao()
            )
            override val workoutHistoryRepository = OfflineWorkoutHistoryRepository(database, gate)
            override val deloadRepository = OfflineDeloadRepository(database.deloadPreferencesDao(), gate)
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
                deloadRepository = deloadRepository
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
