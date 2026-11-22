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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
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

/** The welcome screens open on every launch until onboarding is completed. */
class WelcomeNavigationTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    /** The flag older versions wrote after Get Started; it no longer skips anything. */
    private val prefs = context.getSharedPreferences("first_launch", Context.MODE_PRIVATE)
    private lateinit var database: WallCrawlDatabase
    private lateinit var nav: NavHostController

    // The database closes only after the activity is gone: Today keeps querying until then.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun after() {
            if (::database.isInitialized) database.close()
            prefs.edit().clear().commit()
        }
    }).around(compose)

    @Before fun clearPrefs() {
        prefs.edit().clear().commit()
        context.getSharedPreferences(TODAY_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
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

    @Test fun freshInstallShowsWelcomeThenGetStartedOpensOnboarding() {
        launch(UserProfile(onboardingCompleted = false))
        waitForRoute(AppRoutes.WELCOME)

        // The intro plays on its own clock here; WelcomeScreenTest drives Skip deterministically.
        val getStarted = context.getString(R.string.welcome_get_started)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(getStarted).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(getStarted).performClick()

        waitForRoute(AppRoutes.ONBOARDING)
        assertThat(nav.previousBackStackEntry).isNull()
    }

    @Test fun anUnfinishedOnboardingShowsWelcomeAgainEvenIfAnOldVersionMarkedItSeen() {
        prefs.edit().putBoolean("intro_seen", true).commit()
        launch(UserProfile(onboardingCompleted = false))
        waitForRoute(AppRoutes.WELCOME)
    }

    @Test fun onboardedUsersOpenToday() {
        launch(UserProfile(onboardingCompleted = true))
        waitForRoute(AppRoutes.TODAY)
    }
}
