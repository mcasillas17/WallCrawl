package wallcrawl.elopenmike.com.feature.progress

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.AnalyticsBucket
import wallcrawl.elopenmike.com.core.model.AnalyticsContributor
import wallcrawl.elopenmike.com.core.model.AnalyticsMetric
import wallcrawl.elopenmike.com.core.model.AnalyticsRange
import wallcrawl.elopenmike.com.core.model.AnalyticsScope
import wallcrawl.elopenmike.com.core.model.AnalyticsSelection
import wallcrawl.elopenmike.com.core.model.AnalyticsSeries
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.PerformanceComparison
import wallcrawl.elopenmike.com.core.model.PerformanceObservation
import wallcrawl.elopenmike.com.core.model.PerformanceSeries
import wallcrawl.elopenmike.com.core.model.PerformanceShape
import wallcrawl.elopenmike.com.core.model.ProgressAnalytics
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.ProgressionProvenance
import wallcrawl.elopenmike.com.core.model.ProgressionReason
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.progress.ProgressAnalyticsCalculator
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/** The "Over time" card: every value is readable as text and leads to the workouts behind it. */
@RunWith(AndroidJUnit4::class)
class ProgressAnalyticsCardTest {
    @get:Rule val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val english = context.localized(Locale.ENGLISH)
    private var opened: String? = null
    private val calls = mutableListOf<String>()

    @Test fun aWeekRowRevealsItsContributorsAndEachOpensItsOwnWorkout() {
        render(loaded())
        scrollTo("this week so far)")
        composeRule.onNode(hasText("this week so far)", substring = true))
            .assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        scrollTo("Thursday push")
        composeRule.onNode(hasText("Thursday push", substring = true)).assertHasClickAction().performClick()
        assertThat(opened).isEqualTo("thursday")
    }

    @Test fun theChartHasATextSummaryAndThePartialWeekIsMarkedInText() {
        render(loaded())
        scrollTo("Bar chart of", contentDescription = true)
        composeRule.onNode(hasContentDescription("The last bar is this week so far", substring = true)).assertExists()
        composeRule.onNode(hasText("including this week so far", substring = true)).assertExists()
    }

    @Test fun aPerformanceObservationOpensExactlyThatWorkoutAndNoPercentageIsShown() {
        render(loaded(scope = AnalyticsScope.Exercise("bench"), performance = listOf(weightedSeries())))
        scrollTo("This is a recorded difference")
        composeRule.onNode(hasText("+5 lb", substring = true)).assertExists()
        composeRule.onNode(hasText("%", substring = true)).assertDoesNotExist()
        scrollTo("Later bench")
        composeRule.onNode(hasText("Later bench", substring = true)).performClick()
        assertThat(opened).isEqualTo("later")
    }

    @Test fun theObservationChartDescribesWorkoutsNotWeeks() {
        render(loaded(scope = AnalyticsScope.Exercise("bench"), performance = listOf(weightedSeries())))
        scrollTo("for each workout", contentDescription = true)
        composeRule.onNode(hasContentDescription("Heaviest work-set load for each workout", substring = true)).assertExists()
    }

    @Test fun noMatchesForAMuscleStillReportsUnclassifiableActivity() {
        render(loaded(scope = AnalyticsScope.Muscle("Chest"), hasMatches = false, unclassifiable = 3))
        scrollTo(english.getString(R.string.analytics_no_matches))
        composeRule.onNode(hasText("3 completed sets are from exercises not in the current catalog", substring = true)).assertExists()
        composeRule.onNode(hasText(english.getString(R.string.analytics_muscle_basis))).assertExists()
    }

    @Test fun muscleReviewedDoseStatesItsOwnCurrentCatalogBasis() {
        metric = AnalyticsMetric.REVIEWED_PRIMARY_DOSE
        render(loaded(scope = AnalyticsScope.Muscle("Chest")))
        scrollTo(english.getString(R.string.analytics_muscle_dose_basis))
    }

    @Test fun longContributorListsArePagedWithAnExplicitRemainingCount() {
        render(loaded(contributorsThisWeek = 25))
        scrollTo("this week so far)")
        composeRule.onNode(hasText("this week so far)", substring = true)).performClick()
        scrollTo("Show more (5 not shown yet)")
        composeRule.onNode(hasText("Extra session 22", substring = true)).assertDoesNotExist()
        composeRule.onNode(hasText("Show more (5 not shown yet)")).performClick()
        scrollTo("Extra session 22")
    }

    @Test fun aDifferenceThatRoundsToZeroCarriesNoSign() {
        val series = weightedSeries().let { it.copy(comparison = it.comparison!!.copy(difference = -0.001)) }
        render(loaded(scope = AnalyticsScope.Exercise("bench"), performance = listOf(series)))
        scrollTo("This is a recorded difference")
        composeRule.onNode(hasText(": 0 lb.", substring = true)).assertExists()
        composeRule.onNode(hasText("−0", substring = true)).assertDoesNotExist()
        composeRule.onNode(hasText("+0", substring = true)).assertDoesNotExist()
    }

    @Test fun eachMeasureExplainsItsOwnNoMatchState() {
        metric = AnalyticsMetric.REVIEWED_PRIMARY_DOSE
        render(loaded(scope = AnalyticsScope.Muscle("Back"), hasMatches = false))
        scrollTo(english.getString(R.string.analytics_no_matches_dose))
        composeRule.onNode(hasText(english.getString(R.string.analytics_no_matches))).assertDoesNotExist()
    }

    @Test fun loadVolumeWithNoMatchesNamesMissingLoadsToo() {
        metric = AnalyticsMetric.EXTERNAL_LOAD_VOLUME
        render(loaded(scope = AnalyticsScope.Exercise("bench"), hasMatches = false))
        scrollTo(english.getString(R.string.analytics_no_matches_volume))
        composeRule.onNode(hasText("missing loads are not load volume", substring = true)).assertExists()
    }

    @Test fun recordedDecisionsRemainVisibleWhenTheMeasureHasNoMatches() {
        val decision = AnalyticsDecision("skipped", "Skipped bench day", WEEKS.last().startEpochMillis + 1,
            ProgressionProvenance("bench", ProgressionReason.HOLD_INCOMPLETE_WORK, null, emptyList(), "a".repeat(64)),
            null, emptyList())
        render(loaded(scope = AnalyticsScope.Exercise("bench"), hasMatches = false, decisions = listOf(decision)))
        scrollTo("Skipped bench day")
        composeRule.onNode(hasText(english.getString(R.string.analytics_no_matches))).assertExists()
    }

    @Test fun moreThan52ObservationsAreListedWithoutAnUnreadableChart() {
        val observations = (0 until 53).map {
            PerformanceObservation("s$it", "Session $it", WEEKS.first().startEpochMillis + it * HOUR, listOf("i$it"), 100.0 + it, 5)
        }
        val series = PerformanceSeries(PerformanceShape.WEIGHTED_REPS, observations,
            PerformanceComparison(observations.first(), observations.last(), 52.0))
        render(loaded(scope = AnalyticsScope.Exercise("bench"), performance = listOf(series)))
        scrollTo(english.getString(R.string.analytics_observation_chart_omitted))
        composeRule.onNode(hasContentDescription("for each workout", substring = true)).assertDoesNotExist()
    }

    @Test fun theExerciseChooserSaysLoadingBeforeAnyOptionsArrive() {
        render(ProgressAnalyticsUiState.Loading)
        scrollTo(english.getString(R.string.analytics_scope_exercise))
        composeRule.onNodeWithText(english.getString(R.string.analytics_scope_exercise)).performClick()
        composeRule.onNode(androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isPopup()) and
            hasText(english.getString(R.string.analytics_loading))).assertExists()
        composeRule.onNode(hasText(english.getString(R.string.analytics_no_exercise_options))).assertDoesNotExist()
    }

    @Test fun aResultForThePreviousFilterIsShownAsLoadingUnderTheNewFilter() {
        selectionOverride = AnalyticsSelection(scope = AnalyticsScope.Muscle("Chest"))
        render(loaded())
        scrollTo(english.getString(R.string.analytics_loading))
        composeRule.onNode(hasText("this week so far)", substring = true)).assertDoesNotExist()
        composeRule.onNode(hasText(english.getString(R.string.analytics_muscle_basis))).assertDoesNotExist()
    }

    @Test fun tooManyRecordedExercisesAreNamedInsteadOfAPartialList() {
        render(loaded().copy(exerciseOptions = null, catalogNames = emptyMap()))
        scrollTo(english.getString(R.string.analytics_scope_exercise))
        composeRule.onNodeWithText(english.getString(R.string.analytics_scope_exercise)).performClick()
        val popup = androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isPopup())
        composeRule.onNode(popup and hasText(english.getString(R.string.analytics_exercise_options_too_many))).assertExists()
    }

    @Test fun tooLargeOffersAShorterRangeOnlyWhenOneExists() {
        selectionOverride = AnalyticsSelection(range = AnalyticsRange.WEEKS_12)
        renderOnce(ProgressAnalyticsUiState.TooLarge)
        scrollTo(english.getString(R.string.analytics_too_large))
        selectionOverride = AnalyticsSelection(range = AnalyticsRange.WEEKS_4)
        renderOnce(ProgressAnalyticsUiState.Loading)
        renderOnce(ProgressAnalyticsUiState.TooLarge)
        scrollTo(english.getString(R.string.analytics_too_large_shortest))
        composeRule.onNode(hasText("shorter range", substring = true)).assertDoesNotExist()
    }

    @Test fun theExerciseChooserExplainsTheCardStateInsteadOfLoadingForever() {
        listOf(
            // The default selection is already the shortest range, so no shorter range is offered.
            ProgressAnalyticsUiState.TooLarge to R.string.analytics_too_large_shortest,
            ProgressAnalyticsUiState.Error to R.string.analytics_error,
            ProgressAnalyticsUiState.NoProfile to R.string.progress_no_profile
        ).forEach { (state, res) ->
            renderOnce(state)
            scrollTo(english.getString(R.string.analytics_scope_exercise))
            composeRule.onNodeWithText(english.getString(R.string.analytics_scope_exercise)).performClick()
            val popup = androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.isPopup())
            composeRule.onNode(popup and hasText(english.getString(res))).assertExists()
            composeRule.onNode(popup and hasText(english.getString(R.string.analytics_loading))).assertDoesNotExist()
            androidx.test.espresso.Espresso.pressBack()
            composeRule.waitForIdle()
        }
    }

    @Test fun aWeekWithNothingApplicableSaysSoInsteadOfZero() {
        val base = loaded()
        val empty = base.analytics.series.getValue(AnalyticsMetric.COMPLETED_SETS).let { series ->
            series.copy(buckets = series.buckets.mapIndexed { index, bucket ->
                if (index == 0) bucket.copy(value = 0.0, contributors = emptyList()) else bucket
            })
        }
        render(base.copy(analytics = base.analytics.copy(series = AnalyticsMetric.entries.associateWith { empty })))
        scrollTo(": nothing recorded")
    }

    @Test fun scopeChipsThatOpenAChooserAreButtonsNotRadioButtons() {
        render(loaded())
        scrollTo(english.getString(R.string.analytics_scope_muscle))
        listOf(R.string.analytics_scope_exercise, R.string.analytics_scope_muscle).forEach { res ->
            composeRule.onNodeWithText(english.getString(res))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                    english.getString(R.string.progress_details_collapsed)))
        }
        composeRule.onNodeWithText(english.getString(R.string.analytics_scope_all))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton))
    }

    @Test fun oneObservationSaysThereIsNoChangeToShow() {
        val single = weightedSeries().let { it.copy(observations = it.observations.take(1), comparison = null) }
        render(loaded(scope = AnalyticsScope.Exercise("bench"), performance = listOf(single)))
        scrollTo(english.getString(R.string.analytics_insufficient))
    }

    @Test fun aRecordedDecisionOpensTheWorkoutThatRecordedIt() {
        val decision = AnalyticsDecision("recorder", "Recorder session", WEEKS.last().startEpochMillis + 1,
            ProgressionProvenance("bench", ProgressionReason.HOLD_TARGET_NOT_MET, null, listOf("cited"), "a".repeat(64)),
            null, emptyList())
        render(loaded(scope = AnalyticsScope.Exercise("bench"), decisions = listOf(decision)))
        scrollTo("Cites 1 earlier workout.")
        composeRule.onNode(hasText("Recorder session", substring = true)).performClick()
        assertThat(opened).isEqualTo("recorder")
    }

    @Test fun loadingTooLargeErrorEmptyAndNoMatchStatesAreDistinct() {
        listOf(
            ProgressAnalyticsUiState.Loading to R.string.analytics_loading,
            // The default selection is already the shortest range, so no shorter range is offered.
            ProgressAnalyticsUiState.TooLarge to R.string.analytics_too_large_shortest,
            ProgressAnalyticsUiState.Error to R.string.analytics_error,
            loaded(hasWorkouts = false) to R.string.analytics_empty_range,
            loaded(hasMatches = false) to R.string.analytics_no_matches
        ).forEach { (state, res) ->
            renderOnce(state)
            scrollTo(english.getString(res))
        }
    }

    @Test fun errorRetryAndFilterChipsCallTheirActionsWithSelectedState() {
        render(ProgressAnalyticsUiState.Error)
        scrollTo(english.getString(R.string.action_try_again))
        composeRule.onNode(hasText(english.getString(R.string.action_try_again))).performClick()
        scrollTo("Last 12 weeks")
        composeRule.onNodeWithText("Last 4 weeks").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNodeWithText("Last 12 weeks").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .performClick()
        assertThat(calls).containsExactly("retry", "range:WEEKS_12").inOrder()
    }

    @Test fun spanishLargeTextAt320DpWrapsUnitsWithoutClippingInDarkTheme() =
        assertSpanishLargeTextWraps(ThemePreference.DARK)

    @Test fun spanishLargeTextAt320DpWrapsUnitsWithoutClippingInLightTheme() =
        assertSpanishLargeTextWraps(ThemePreference.LIGHT)

    /** Rows that carry units, the comparison sentence and the headings all wrap inside 320 dp at 1.8x. */
    private fun assertSpanishLargeTextWraps(theme: ThemePreference) {
        val spanish = context.localized(Locale.forLanguageTag("es-MX"), 1.8f)
        metric = AnalyticsMetric.EXTERNAL_LOAD_VOLUME
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalContext provides spanish,
                LocalConfiguration provides spanish.resources.configuration,
                LocalDensity provides Density(density, 1.8f)) {
                WallCrawlTheme(themePreference = theme) {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        screen(loaded(scope = AnalyticsScope.Exercise("bench"), performance = listOf(weightedSeries())))
                    }
                }
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("(esta semana hasta ahora)", substring = true))
        composeRule.onNode(hasText("(esta semana hasta ahora)", substring = true)).performClick()
        listOf(
            spanish.getString(R.string.analytics_heading),
            spanish.getString(R.string.analytics_scope_note).substringBefore("%1"),
            "(esta semana hasta ahora)", // a week row: dates and "N lb × repeticiones"
            "Thursday push", // a contributor row with its volume
            "Es una diferencia registrada", // the comparison sentence with signed "+5 lb"
            "Later bench" // an observation row: load, unit and reps
        ).forEach { label ->
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label, substring = true))
            val node = composeRule.onNode(hasText(label, substring = true), useUnmergedTree = true)
            node.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertThat(layout.layoutInput.density.fontScale).isEqualTo(1.8f)
            com.google.common.truth.Truth.assertWithMessage(label).that(layout.didOverflowHeight).isFalse()
            // The same wrapping contract as ProgressScreenTest: every line inside the laid-out width.
            repeat(layout.lineCount) { line ->
                com.google.common.truth.Truth.assertWithMessage(label).that(layout.getLineLeft(line)).isAtLeast(0f)
                com.google.common.truth.Truth.assertWithMessage(label).that(layout.getLineRight(line))
                    .isAtMost(layout.size.width.toFloat())
            }
            assertThat(node.fetchSemanticsNode().boundsInRoot.height).isAtLeast(layout.size.height.toFloat() - 1f)
        }
    }

    private fun scrollTo(text: String, contentDescription: Boolean = false) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            if (contentDescription) hasContentDescription(text, substring = true) else hasText(text, substring = true)
        )
    }

    // The rule allows one setContent per test, so the distinct-state test swaps state in place.
    private var rendered = false
    private val current = androidx.compose.runtime.mutableStateOf<ProgressAnalyticsUiState>(ProgressAnalyticsUiState.Loading)
    private fun renderOnce(state: ProgressAnalyticsUiState) {
        current.value = state
        if (!rendered) {
            rendered = true
            composeRule.setContent { localizedScreen { screen(current.value) } }
        }
        composeRule.waitForIdle()
    }

    private fun render(state: ProgressAnalyticsUiState) {
        composeRule.setContent { localizedScreen { screen(state) } }
    }

    @androidx.compose.runtime.Composable
    private fun localizedScreen(content: @androidx.compose.runtime.Composable () -> Unit) {
        CompositionLocalProvider(LocalContext provides english, LocalConfiguration provides english.resources.configuration) {
            WallCrawlTheme(themePreference = ThemePreference.LIGHT) { content() }
        }
    }

    /** When set, the rendered selection differs from the state's own range/scope (a stale result). */
    private var selectionOverride: AnalyticsSelection? = null

    @androidx.compose.runtime.Composable
    private fun screen(state: ProgressAnalyticsUiState) {
        val analytics = (state as? ProgressAnalyticsUiState.Loaded)?.analytics
        val selection = selectionOverride
            ?: analytics?.let { AnalyticsSelection(it.range, it.scope, metric) } ?: AnalyticsSelection()
        ProgressScreen(
            uiState = success(), onRetry = {}, onOpenSession = { opened = it },
            analyticsState = state, analyticsSelection = selection,
            analyticsActions = ProgressAnalyticsActions(
                onSelectRange = { calls += "range:${it.name}" },
                onSelectScope = { calls += "scope" },
                onSelectMetric = { calls += "metric:${it.name}" },
                onReset = { calls += "reset" },
                onRetry = { calls += "retry" }
            )
        )
    }

    private fun Context.localized(locale: Locale, fontScale: Float = 1f): Context {
        val configuration = Configuration(resources.configuration)
        configuration.setLocales(LocaleList(locale))
        configuration.fontScale = fontScale
        return createConfigurationContext(configuration)
    }

    private fun success() = ProgressUiState.Success(
        overview = ProgressOverview(), preferredUnit = WeightUnit.LBS, week = WEEKS.last(),
        reviewedDose = ledger(WEEKS.last()), previousReviewedDose = ledger(WEEKS[2])
    )

    private fun ledger(week: TrainingWeek) = WeeklyDoseLedger(
        LedgerPolicyVersion.PRIMARY_ONLY_V1, week.startEpochDay, "UTC", "test", 1, emptyMap(), emptyMap(), emptyMap()
    )

    /** The metric the rendered selection shows; every metric gets the same fixture series. */
    private var metric = AnalyticsMetric.COMPLETED_SETS

    private fun loaded(
        scope: AnalyticsScope = AnalyticsScope.All,
        performance: List<PerformanceSeries> = emptyList(),
        decisions: List<AnalyticsDecision> = emptyList(),
        hasWorkouts: Boolean = true,
        hasMatches: Boolean = true,
        unclassifiable: Int = 0,
        contributorsThisWeek: Int = 2
    ): ProgressAnalyticsUiState.Loaded {
        val buckets = WEEKS.mapIndexed { index, week ->
            val contributors = if (index == WEEKS.lastIndex) listOf(
                AnalyticsContributor("monday", "Monday pull", week.startEpochMillis + HOUR, 6.0),
                AnalyticsContributor("thursday", "Thursday push", week.startEpochMillis + 3 * DAY, 8.0)
            ) + (2 until contributorsThisWeek).map {
                AnalyticsContributor("extra-$it", "Extra session $it", week.startEpochMillis + 4 * DAY + it, 1.0)
            } else listOf(AnalyticsContributor("w$index", "Week $index session", week.startEpochMillis + HOUR, 4.0 + index))
            AnalyticsBucket(week, index == WEEKS.lastIndex, contributors.sumOf { it.value }, contributors)
        }
        // "No matches" is what the calculator produces: buckets without contributors.
        val series = AnalyticsSeries(if (hasMatches) buckets else buckets.map { it.copy(value = 0.0, contributors = emptyList()) })
        return ProgressAnalyticsUiState.Loaded(
            ProgressAnalytics(AnalyticsRange.WEEKS_4, scope, AnalyticsMetric.entries.associateWith { series }, 3,
                hasWorkouts, unclassifiable, emptyMap(), performance),
            WeightUnit.LBS, listOf("bench"), mapOf("bench" to "Bench Press"), decisions
        )
    }

    private fun weightedSeries(): PerformanceSeries {
        val earlier = PerformanceObservation("earlier", "Earlier bench", WEEKS[1].startEpochMillis + HOUR, listOf("i1"), 100.0, 5)
        val later = PerformanceObservation("later", "Later bench", WEEKS[3].startEpochMillis + HOUR, listOf("i2"), 105.0, 5)
        return PerformanceSeries(PerformanceShape.WEIGHTED_REPS, listOf(earlier, later), PerformanceComparison(earlier, later, 5.0))
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        val WEEKS = ProgressAnalyticsCalculator.rangeWeeks(
            AnalyticsRange.WEEKS_4, Instant.parse("2026-09-05T12:00:00Z"), ZoneId.of("UTC")
        )
    }
}
