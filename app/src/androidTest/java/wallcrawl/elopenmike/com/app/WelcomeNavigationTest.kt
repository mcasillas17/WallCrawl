package wallcrawl.elopenmike.com.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.AppContainer
import wallcrawl.elopenmike.com.DefaultAppContainer
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.database.repository.OfflineDeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineProgramRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/** Where first launch lands, and that the intro is remembered as a device setting. */
class WelcomeNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences(FIRST_LAUNCH_PREFS, Context.MODE_PRIVATE)
    private lateinit var database: WallCrawlDatabase
    private lateinit var nav: NavHostController

    @Before fun clearPrefs() {
        prefs.edit().clear().commit()
        context.getSharedPreferences(TODAY_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun close() {
        if (::database.isInitialized) database.close()
        prefs.edit().clear().commit()
    }

    private fun launch(profile: UserProfile) {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        val bundled = DefaultAppContainer(context)
        val gate = kotlinx.coroutines.sync.Mutex()
        val container = object : AppContainer by bundled {
            override val database = this@WelcomeNavigationTest.database
            override val userProfileRepository = OfflineUserProfileRepository(database.userProfileDao(), gate)
            override val workoutRepository = OfflineWorkoutRepository(database.workoutSessionDao(), database.workoutSetDao(), localDataWriteGate = gate)
            override val programRepository = OfflineProgramRepository(database, gate)
            override val deloadRepository = OfflineDeloadRepository(database.deloadPreferencesDao(), gate)
            override val workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(userProfileRepository, workoutRepository,
                exerciseCatalog, exerciseFilter, workoutHistoryAnalyzer, deloadRepository = deloadRepository, programRepository = programRepository)
        }
        runBlocking { container.userProfileRepository.saveProfile(profile) }
        compose.setContent {
            nav = rememberNavController()
            WallCrawlTheme { WallCrawlApp(container = container, navController = nav) }
        }
    }

    private fun waitForRoute(route: String) = compose.waitUntil(15_000) { nav.currentDestination?.route == route }

    @Test fun freshInstallShowsIntroThenGetStartedOpensOnboardingAndIsRemembered() {
        launch(UserProfile(onboardingCompleted = false))
        waitForRoute(AppRoutes.WELCOME)
        assertThat(prefs.getBoolean(KEY_INTRO_SEEN, false)).isFalse()

        // The intro plays on its own clock here; WelcomeScreenTest drives Skip deterministically.
        val getStarted = context.getString(R.string.welcome_get_started)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(getStarted).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(getStarted).performClick()

        waitForRoute(AppRoutes.ONBOARDING)
        assertThat(prefs.getBoolean(KEY_INTRO_SEEN, false)).isTrue()
        assertThat(nav.previousBackStackEntry).isNull()
    }

    @Test fun seenIntroGoesStraightToOnboarding() {
        prefs.edit().putBoolean(KEY_INTRO_SEEN, true).commit()
        launch(UserProfile(onboardingCompleted = false))
        waitForRoute(AppRoutes.ONBOARDING)
    }

    @Test fun onboardedUsersOpenTodayAndNeverSeeTheIntroLater() {
        launch(UserProfile(onboardingCompleted = true))
        waitForRoute(AppRoutes.TODAY)
        compose.waitUntil(5_000) { prefs.getBoolean(KEY_INTRO_SEEN, false) }
    }
}
