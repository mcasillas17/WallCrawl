package wallcrawl.elopenmike.com.app

import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import wallcrawl.elopenmike.com.AppContainer
import wallcrawl.elopenmike.com.DefaultAppContainer
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.feature.conditions.ConditionReferenceScreen
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class ConditionReferenceNavigationTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    @get:Rule val rules = RuleChain.outerRule(object : ExternalResource() {
        override fun after() { if (::database.isInitialized) database.close() }
    }).around(compose)

    @Test fun exerciseTabOpensReferenceAndPublicationThenBackRestoresSearch() = checkReference("en")
    @Test fun spanishNamesAndLabelsSurviveRestorationAndReadOnlyBrowsing() = checkReference("es")

    @Test fun missingOfflineReferenceExplainsFailureAndCanRetry() {
        var retryAvailable = false
        val bundled = DefaultAppContainer(context)
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val wrapped = object : ContextWrapper(context) {
            override fun getAssets() = if (retryAvailable) context.assets else testAssets
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides wrapped, LocalResources provides context.resources) {
                WallCrawlTheme { ConditionReferenceScreen(bundled.exerciseCatalog, bundled.exerciseVisualProvider, onBack = {}) }
            }
        }
        val retry = context.getString(R.string.history_retry)
        compose.waitUntil(15_000) { compose.onAllNodesWithText(retry).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.condition_reference_unavailable)).assertIsDisplayed()
        retryAvailable = true
        compose.onNodeWithText(retry).performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val directory = File(output)
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "condition-reference-$name.png").outputStream().use { stream ->
            check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private fun checkReference(language: String) {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        val bundled = DefaultAppContainer(context)
        val gate = kotlinx.coroutines.sync.Mutex()
        val container = object : AppContainer by bundled {
            override val database = this@ConditionReferenceNavigationTest.database
            override val userProfileRepository = OfflineUserProfileRepository(database.userProfileDao(), gate)
            override val workoutRepository = OfflineWorkoutRepository(database.workoutSessionDao(), database.workoutSetDao(), localDataWriteGate = gate)
            override val programRepository = OfflineProgramRepository(database, gate)
            override val deloadRepository = OfflineDeloadRepository(database.deloadPreferencesDao(), gate)
            override val workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(userProfileRepository, workoutRepository,
                exerciseCatalog, exerciseFilter, workoutHistoryAnalyzer, deloadRepository = deloadRepository, programRepository = programRepository)
        }
        val profile = runBlocking {
            container.userProfileRepository.saveProfile(UserProfile(onboardingCompleted = true))
            container.userProfileRepository.getUserProfile().first()
        }
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); fontScale = 1.3f }
        val localized = context.createConfigurationContext(config)
        val text: (Int) -> String = localized.resources::getString
        val restoration = StateRestorationTester(compose)
        var opened: String? = null
        var failToOpen = true
        val links = object : UriHandler {
            override fun openUri(uri: String) {
                if (failToOpen) throw IllegalArgumentException("No browser")
                opened = uri
            }
        }
        lateinit var nav: NavHostController
        restoration.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources,
                LocalConfiguration provides config, LocalDensity provides Density(context.resources.displayMetrics.density, config.fontScale),
                LocalUriHandler provides links) {
                WallCrawlTheme(if (language == "es") ThemePreference.LIGHT else ThemePreference.DARK) {
                    WallCrawlApp(container = container, navController = nav, profile = profile)
                }
            }
        }
        val entry = text(R.string.guided_condition_guide)
        compose.waitUntil(15_000) { nav.currentDestination?.route == AppRoutes.TODAY }
        compose.onNode(hasText(text(R.string.nav_exercises)) and hasAnyAncestor(hasTestTag(WALL_CRAWL_BOTTOM_BAR_TEST_TAG))).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(entry).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.guided_profile_settings)).performClick()
        compose.waitUntil { nav.currentDestination?.route == AppRoutes.PROFILE }
        compose.onNodeWithTag(WALL_CRAWL_BOTTOM_BAR_TEST_TAG).assertDoesNotExist()
        capture("settings-$language")
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        compose.waitUntil { nav.currentDestination?.route == AppRoutes.EXERCISES }
        capture("library-$language")
        compose.onNodeWithText(entry).performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        val query = if (language == "es") "sindrome del piriforme" else "piriformis muscle syndrome"
        capture("list-$language")
        compose.onNode(hasSetTextAction()).performTextInput("not-in-the-library")
        compose.onNodeWithText(text(R.string.condition_reference_empty)).assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement(query)
        val name = if (language == "es") "Síndrome del piriforme" else "Piriformis syndrome"
        compose.onNodeWithText(name).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text(R.string.condition_reference_publications)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(name).assertExists()
        capture("detail-$language")
        val list = compose.onNode(hasScrollToIndexAction())
        val movement = if (language == "es") "Estiramiento piriforme tumbado con apoyo en pared" else "Supine Wall-Supported Piriformis Stretch"
        list.performScrollToNode(hasText(movement))
        compose.onNodeWithText(movement).performClick()
        val close = text(R.string.exercise_detail_close)
        compose.waitUntil(15_000) { compose.onAllNodesWithText(close).fetchSemanticsNodes().isNotEmpty() }
        val frame = if (language == "es") "Cuadro" else "Frame"
        compose.waitUntil(15_000) { compose.onAllNodesWithText(frame, substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(close).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.condition_reference_illustrations)).assertExists()
        val open = text(R.string.condition_reference_open)
        list.performScrollToNode(hasText(open))
        assertThat(opened).isNull()
        compose.onAllNodesWithText(open).onFirst().performClick()
        compose.onNodeWithText(text(R.string.condition_reference_link_error)).assertExists()
        failToOpen = false
        compose.onAllNodesWithText(open).onFirst().performClick()
        assertThat(opened).isEqualTo("https://link.springer.com/article/10.1186/s13102-023-00802-4")
        val notes = text(R.string.condition_reference_show_notes)
        list.performScrollToNode(hasText(notes))
        compose.onAllNodesWithText(notes).onFirst().performClick()
        list.performScrollToNode(hasText(text(R.string.condition_reference_limits)))
        compose.onNodeWithText(text(R.string.condition_reference_limits)).assertIsDisplayed()
        val related = text(R.string.condition_related_show).substringBefore("(").trim()
        list.performScrollToNode(hasText(related, substring = true))
        compose.onNodeWithText(related, substring = true).performClick()
        list.performScrollToNode(hasText(text(R.string.condition_related_note)))
        compose.onNodeWithText(text(R.string.condition_related_note)).assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(hasSetTextAction()).assertTextContains(query)
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        compose.onNodeWithText(entry).assertExists()
        assertThat(runBlocking { container.userProfileRepository.getUserProfile().first() }).isEqualTo(profile)
    }
}
