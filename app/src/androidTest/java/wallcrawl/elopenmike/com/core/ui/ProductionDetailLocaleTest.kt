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
            compose.runOnUiThread { AppLanguageController.apply(AppLanguage.ENGLISH) }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.locales[0].language == "en" }
            val englishActivity = compose.activity
            openDetail()
            compose.onNodeWithText("Primary:", substring = true).assertExists()
            compose.runOnUiThread { AppLanguageController.apply(AppLanguage.SPANISH) }
            compose.waitUntil(10_000) { compose.activity !== englishActivity && compose.activity.resources.configuration.locales[0].language == "es" }
            openDetail()
            compose.onNodeWithText("Principal:", substring = true).assertExists()
            compose.onNodeWithText("Secundarios:", substring = true).assertExists()
            compose.onNodeWithText("Equipo indicado:", substring = true).assertExists()
            compose.onNodeWithText("Programación pendiente de revisión").assertExists()
            compose.onNodeWithText("Primary:", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Programming review pending").assertDoesNotExist()
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
            compose.onNodeWithText("Programación pendiente de revisión").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(prior) }
            instrumentation.waitForIdleSync()
            assertThat(AppCompatDelegate.getApplicationLocales()).isEqualTo(prior)
        }
    }

    private fun openDetail() {
        val vm = ExercisesViewModel(original.exerciseCatalog)
        val exercise = runBlocking { original.exerciseCatalog.getExerciseById("pht-single-leg-calf-supported-isometric-hamstring-bridge")!! }
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val vocabulary = rememberExerciseVocabulary(original.exerciseLocalizationSource)
                CompositionLocalProvider(LocalExerciseVocabulary provides vocabulary) {
                    WallCrawlTheme { ExercisesScreen(vm, original.exerciseVisualProvider) }
                }
            }
            vm.openExerciseDetail(exercise)
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Programming review pending") or hasText("Programación pendiente de revisión")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
