package wallcrawl.elopenmike.com.feature.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.PerformanceObservation
import wallcrawl.elopenmike.com.core.model.PerformanceSeries
import wallcrawl.elopenmike.com.core.model.PerformanceShape
import wallcrawl.elopenmike.com.core.model.WeightUnit

@RunWith(AndroidJUnit4::class)
class ExerciseHistoryContentTest {
    @get:Rule val compose = createComposeRule()
    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val day = LocalDate.of(2026, 11, 9)
    private val midnight = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun render(series: List<PerformanceSeries>): MutableState<ExerciseHistoryUiState.Loaded> {
        val state = ExerciseHistoryUiState.Loaded(
            exerciseOptions = listOf("press"), catalogNames = mapOf("press" to "Test press"),
            exerciseId = "press", preferredUnit = WeightUnit.LBS, performance = series,
            period = ProgressPeriod.MONTH, start = day.withDayOfMonth(1), end = day,
            today = day, zoneId = ZoneOffset.UTC
        )
        val screen = mutableStateOf(state)
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ExerciseHistoryContent(screen.value, {}, {}, { _, _ -> }, {})
            }
        }
        return screen
    }

    private fun assertSelectedReps(reps: Int) {
        compose.onNodeWithContentDescription(resources.getString(R.string.analytics_metric_reps))
            .assert(SemanticsMatcher("Selected point has $reps reps") {
                it.config[SemanticsProperties.StateDescription].endsWith("· $reps")
            })
    }

    @Test fun previousSessionUpdatesBothWeightAndRepsEvenOnTheSameDate() {
        val morning = PerformanceObservation("morning", "First", midnight + 3600000, listOf("m"), 65.0, 9)
        val evening = PerformanceObservation("evening", "Next", midnight + 7200000, listOf("e"), 70.0, 6)
        render(listOf(PerformanceSeries(PerformanceShape.WEIGHTED_REPS, listOf(morning, evening), null)))

        compose.onNodeWithText("70 lb").assertExists()
        assertSelectedReps(6)
        compose.onNodeWithContentDescription(resources.getString(R.string.history_chart_previous)).performScrollTo().performClick()
        compose.onNodeWithText("65 lb").assertExists()
        assertSelectedReps(9)
    }

    @Test fun tappingFirstPointReadsItsRepsWithoutDrawingAWeightSeries() {
        val first = PerformanceObservation("m", "First", midnight - 7 * 86400000, listOf("m"), 5.0)
        val second = PerformanceObservation("e", "Next", midnight, listOf("e"), 14.0)
        render(listOf(PerformanceSeries(PerformanceShape.BODYWEIGHT_REPS, listOf(first, second), null)))
        compose.onNodeWithText(resources.getString(R.string.history_chart_weight)).assertDoesNotExist()
        compose.onNodeWithContentDescription(resources.getString(R.string.analytics_metric_reps))
            .performScrollTo().performTouchInput { click(Offset(1f, center.y)) }
        assertSelectedReps(5)
    }

    @Test fun tapsDistinguishHeightsWhenTwoWorkoutsFinishedAtTheSameInstant() {
        val first = PerformanceObservation("lower", "First", midnight, listOf("a"), 2.0)
        val second = PerformanceObservation("upper", "Second", midnight, listOf("b"), 10.0)
        render(listOf(PerformanceSeries(PerformanceShape.BODYWEIGHT_REPS, listOf(first, second), null)))
        val plot = compose.onNodeWithContentDescription(resources.getString(R.string.analytics_metric_reps))
        plot.performScrollTo().performTouchInput { click(Offset(width * 8f / 9f, height * 0.8f)) }
        assertSelectedReps(2)
        plot.performTouchInput { click(Offset(width * 8f / 9f, 2f)) }
        assertSelectedReps(10)
    }

    @Test fun monthAdvancingAtMidnightKeepsTheInspectedSession() {
        val first = PerformanceObservation("first", "First", midnight - 2 * 86400000, listOf("a"), 3.0)
        val second = PerformanceObservation("next", "Next", midnight, listOf("b"), 7.0)
        val screen = render(listOf(PerformanceSeries(PerformanceShape.BODYWEIGHT_REPS, listOf(first, second), null)))
        compose.onNodeWithContentDescription(resources.getString(R.string.history_chart_previous)).performScrollTo().performClick()
        assertSelectedReps(3)
        compose.runOnIdle { screen.value = screen.value.copy(today = day.plusDays(1), end = day.plusDays(1)) }
        assertSelectedReps(3)
    }
}
