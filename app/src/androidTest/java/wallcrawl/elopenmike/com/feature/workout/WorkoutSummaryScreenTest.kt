package wallcrawl.elopenmike.com.feature.workout

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.core.ui.theme.LightBackground

@RunWith(AndroidJUnit4::class)
class WorkoutSummaryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun externalVolumeNamesLoadTimesRepetitionsRatherThanLoadAlone() {
        render(Locale.ENGLISH, ThemePreference.DARK, volume = 425.0)
        compose.onNodeWithText("425 kg × reps").performScrollTo().assertIsDisplayed()
    }

    @Test fun summaryDetailActionHasReadableLightThemeContrast() {
        render(Locale.ENGLISH, ThemePreference.LIGHT, openDetails = {})
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("View workout details").performScrollTo()
        compose.onNodeWithText("View workout details", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertThat(ColorUtils.calculateContrast(
            layouts.single().layoutInput.style.color.toArgb(), LightBackground.toArgb()
        )).isAtLeast(4.5)
    }

    @Test fun zeroExternalVolumeDoesNotInventBodyweightWork() {
        render(Locale.ENGLISH, ThemePreference.DARK)
        compose.onNodeWithText("Bodyweight").assertDoesNotExist()
        compose.onNodeWithText("No external-load volume recorded").performScrollTo().assertIsDisplayed()
    }

    @Test fun longSpanishSummaryAt320DpAndLargeTextKeepsDoneAccessible() {
        var done = false
        render(Locale.forLanguageTag("es-MX"), ThemePreference.LIGHT, 1.8f) { done = true }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Listo"))
        compose.onNodeWithText("Listo").assertIsDisplayed().performClick()
        assertThat(done).isTrue()
        val label = "No se registró volumen de carga externa"
        compose.onNodeWithText(label).performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertThat(layouts.single().hasVisualOverflow).isFalse()
    }

    private fun render(
        locale: Locale,
        theme: ThemePreference,
        scale: Float = 1f,
        volume: Double = 0.0,
        openDetails: (() -> Unit)? = null,
        done: () -> Unit = {}
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
            fontScale = scale
        }
        val localized = context.createConfigurationContext(configuration)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(context.resources.displayMetrics.density, scale)
            ) {
                WallCrawlTheme(themePreference = theme) {
                    Box(Modifier.width(320.dp)) {
                        WorkoutSummaryScreen(
                            WorkoutSummary(
                                sessionId = "finished", workoutName = "Nombre original del entrenamiento",
                                durationMinutes = 123, totalSetsCompleted = 50, totalVolume = volume,
                                prCount = 0, unit = WeightUnit.KG, completedAtTimestamp = 1_700_000_000_000L
                            ),
                            onDone = done,
                            onOpenDetails = openDetails
                        )
                    }
                }
            }
        }
    }
}
