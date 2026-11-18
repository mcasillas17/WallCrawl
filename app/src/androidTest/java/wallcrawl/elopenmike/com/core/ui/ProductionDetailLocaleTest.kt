package wallcrawl.elopenmike.com.core.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import wallcrawl.elopenmike.com.*
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.locale.*
import wallcrawl.elopenmike.com.core.ui.localization.*
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.feature.exercises.*

/** Real AppCompat activity/controller recreation; no resource/configuration overrides. */
class ProductionDetailLocaleTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as WallCrawlApplication
    private lateinit var original: AppContainer
    private lateinit var database: WallCrawlDatabase
    @get:Rule val rules = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            original = app.container
            database = Room.inMemoryDatabaseBuilder(app, WallCrawlDatabase::class.java).build()
            app.container = object : AppContainer by original {
                override val userProfileRepository = OfflineUserProfileRepository(database.userProfileDao())
            }
        }
        override fun after() { app.container = original; database.close() }
    }).around(compose)

    @Test fun productionLanguageSwitchRecreatesAndReopensSpanishDetail() {
        val prior = AppCompatDelegate.getApplicationLocales()
        try {
            showInLanguage(AppLanguage.ENGLISH)
            openDetail()
            compose.onNodeWithText("Primary focus").assertExists()
            showInLanguage(AppLanguage.SPANISH)
            openDetail()
            compose.onNodeWithText("Enfoque principal").assertExists()
            compose.onNodeWithText("Enfoque secundario").assertExists()
            compose.onNodeWithText("Equipo indicado:", substring = true).assertExists()
            compose.onNode(hasText("Ejercicio compuesto") and hasText("Sin clasificar")).assertExists()
            compose.onNode(hasText("Dificultad") and hasText("Sin evaluar")).assertExists()
            compose.onNode(hasText("Puntuación de fatiga") and hasText("Sin evaluar")).assertExists()
            compose.onNodeWithText("Primary focus").assertDoesNotExist()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("Cuadro", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            assertThat(compose.onAllNodes(hasText("Apoyo para la pantorrilla", substring = true)).fetchSemanticsNodes()).isNotEmpty()
            compose.onNodeWithText("Cerrar los detalles").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Cerrar los detalles", useUnmergedTree = true).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(20.dp)
            val pixels = compose.onNodeWithText("Cerrar los detalles", useUnmergedTree = true).captureToImage().toPixelMap()
            assertThat((0 until pixels.height).any { y -> (0 until pixels.width).any { x ->
                val pixel = pixels[x, y]; pixel.red > 0.8f && pixel.green > 0.8f && pixel.blue > 0.8f
            } }).isTrue()
            val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: app.filesDir
            output.mkdirs()
            File(output, "production-detail-es-recreated-close.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("Cerrar los detalles").performClick()
            compose.onNodeWithText("Enfoque principal").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(prior) }
            instrumentation.waitForIdleSync()
            assertThat(AppCompatDelegate.getApplicationLocales()).isEqualTo(prior)
        }
    }

    @Test fun arnoldPressShowsMovementAndAllFiveDetailsWithoutFooterLinks() {
        val prior = AppCompatDelegate.getApplicationLocales()
        try {
            showInLanguage(AppLanguage.ENGLISH)
            openDetail("arnold-press")
            compose.onNodeWithText("palms facing you", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("Primary focus") and hasText("Shoulders")).assertExists()
            compose.onNode(hasText("Secondary focus") and hasText("Triceps")).assertExists()
            compose.onNode(hasText("Compound exercise") and hasText("Yes")).assertExists()
            compose.onNode(hasText("Difficulty") and hasText("Intermediate")).assertExists()
            compose.onNode(hasText("Fatigue score") and hasText("3/5")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Bryl Lim").assertDoesNotExist()
            compose.onNodeWithText("CC BY-SA 4.0").assertDoesNotExist()
            compose.onAllNodesWithText("Condition guide").assertCountEquals(1)

            val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: app.filesDir
            output.mkdirs()
            File(output, "exercise-detail-arnold-press.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(prior) }
            instrumentation.waitForIdleSync()
        }
    }

    private fun showInLanguage(language: AppLanguage) {
        val currentActivity = compose.activity
        // Even an explicit English preference on an English device can recreate the activity.
        if (currentActivity.resources.configuration.locales[0].language == language.languageTag) return
        compose.runOnUiThread { AppLanguageController.apply(language) }
        compose.waitUntil(10_000) {
            val activity = compose.activity
            activity !== currentActivity && activity.resources.configuration.locales[0].language == language.languageTag
        }
    }

    private fun openDetail(exerciseId: String = "pht-single-leg-calf-supported-isometric-hamstring-bridge") {
        val vm = ExercisesViewModel(original.exerciseCatalog)
        val exercise = runBlocking { original.exerciseCatalog.getExerciseById(exerciseId)!! }
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val vocabulary = rememberExerciseVocabulary(original.exerciseLocalizationSource)
                CompositionLocalProvider(LocalExerciseVocabulary provides vocabulary) {
                    WallCrawlTheme { ExercisesScreen(vm, original.exerciseVisualProvider, onOpenConditionReference = {}) }
                }
            }
            vm.openExerciseDetail(exercise)
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.exercise_detail_close))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
