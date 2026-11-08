package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.test.hasContentDescription
import wallcrawl.elopenmike.com.test.hasLiveRegion
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import wallcrawl.elopenmike.com.core.model.BreakDurationHelper
import wallcrawl.elopenmike.com.core.ui.localization.titleRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.Role
import wallcrawl.elopenmike.com.core.locale.AppLanguage
import androidx.test.platform.app.InstrumentationRegistry
import wallcrawl.elopenmike.com.WallCrawlApplication
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.width
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.app.AppRoutes
import wallcrawl.elopenmike.com.app.WallCrawlBottomBar
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class AccessibilityPatternsTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The style's colour used to beat the field's: typed text was white on white in light theme. */
    @Test fun textTypedIntoAFieldIsReadableInBothThemes() {
        var theme by mutableStateOf(ThemePreference.LIGHT)
        compose.setContent {
            WallCrawlTheme(themePreference = theme) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    OutlinedTextField(value = "Alex", onValueChange = {})
                }
            }
        }
        assertThat(bestContrastOfField()).isAtLeast(4.5)
        theme = ThemePreference.DARK
        compose.waitForIdle()
        assertThat(bestContrastOfField()).isAtLeast(4.5)
    }

    /** Every label stays one whole word at common widths and scales, spoken once with its selection. */
    @Test fun spanishBottomBarKeepsWholeLabelsAtEveryWidthAndScale() {
        var width by mutableStateOf(320.dp)
        var scale by mutableStateOf(1.8f)
        val density = context.resources.displayMetrics.density
        val base = Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag("es-MX"))) }
        val localized = context.createConfigurationContext(base)
        val labels = listOf(R.string.nav_today, R.string.nav_progress, R.string.nav_exercises).map(localized::getString)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides base,
                LocalDensity provides Density(density, scale)
            ) {
                WallCrawlTheme { Box(Modifier.requiredWidth(width)) { WallCrawlBottomBar(AppRoutes.EXERCISES, {}) } }
            }
        }
        for (w in listOf(320.dp, 360.dp, 412.dp)) for (s in listOf(1f, 1.3f, 1.5f, 1.8f, 2f)) {
            width = w
            scale = s
            compose.waitForIdle()
            labels.forEach { label ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertWithMessage("$label at $w, $s").that(layouts.single().lineCount).isEqualTo(1)
                assertThat(layouts.single().isLineEllipsized(0)).isFalse()
            }
        }
        fun top(label: String) = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        width = 412.dp
        scale = 1f
        compose.waitForIdle()
        assertWithMessage("one row when the labels fit").that(top(labels.last())).isEqualTo(top(labels.first()))
        val selected = compose.onNodeWithText(labels.last())
        selected.assertIsSelected()
        val spoken = selected.fetchSemanticsNode().config
        assertThat(spoken.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).isEmpty()
        assertThat(spoken.getOrNull(SemanticsProperties.Text)?.map { it.text }).containsExactly(labels.last())
    }

    /** The frame counter is hidden from screen readers, so one never hears it advance. */
    @Test fun animatedIllustrationIsQuietAndPauseHoldsTheFrame() {
        showIllustration(animationsEnabled = true)
        val counter = context.getString(R.string.illustration_frame_counter, "1", "2")
        // Shown, but hidden from screen readers by the badge around it.
        val hidden = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
        compose.onNode(hasText(counter) and (hidden or hasAnyAncestor(hidden)), useUnmergedTree = true).assertExists()
        val first = counterPixels()
        compose.mainClock.advanceTimeBy(850)
        compose.waitForIdle()
        assertThat(counterPixels()).isNotEqualTo(first)

        compose.onNodeWithContentDescription(context.getString(R.string.illustration_pause)).performClick()
        // Past the click's own ripple, which would otherwise differ between the two captures.
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val held = counterPixels()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(counterPixels()).isEqualTo(held)

        compose.onNodeWithContentDescription(context.getString(R.string.illustration_play)).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertThat(counterPixels()).isNotEqualTo(held)
    }

    /** With animations off, the loop waits to be asked for. */
    @Test fun illustrationStartsStillWhenAnimationsAreOff() {
        showIllustration(animationsEnabled = false)
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_play)).assertExists()
        val still = counterPixels()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(counterPixels()).isEqualTo(still)
    }

    /** One frame has nothing to play, so there is no control that would do nothing. */
    @Test fun aSingleFrameOffersNoPauseOrPlay() {
        val oneFrame = object : ExerciseVisualProvider {
            override fun framesFor(exerciseId: String) = listOf(ExerciseVisual("a.webp"))
        }
        compose.setContent {
            WallCrawlTheme {
                ExerciseIllustration(
                    exercise = Exercise(id = "push-up", name = "Push-up", primaryMuscles = listOf("Chest"), type = ExerciseType.BODYWEIGHT_REPS),
                    visualProvider = oneFrame,
                    animationsEnabled = true
                )
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_pause)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_play)).assertDoesNotExist()
    }

    /** Paused on the way back through a frame, Play carries on backwards, not forwards. */
    @Test fun playResumesInTheDirectionPauseStopped() {
        val threeFrames = object : ExerciseVisualProvider {
            override fun framesFor(exerciseId: String) = listOf(ExerciseVisual("a.webp"), ExerciseVisual("b.webp"), ExerciseVisual("c.webp"))
        }
        compose.setContent {
            WallCrawlTheme {
                ExerciseIllustration(
                    exercise = Exercise(id = "push-up", name = "Push-up", primaryMuscles = listOf("Chest"), type = ExerciseType.BODYWEIGHT_REPS),
                    visualProvider = threeFrames,
                    animationsEnabled = true
                )
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        val first = counterPixels()
        repeat(3) {
            compose.mainClock.advanceTimeBy(850)
            compose.waitForIdle()
        }
        // The fourth position shows the middle frame on its way back, so the next is the first frame.
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_pause)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_play)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(900)
        compose.waitForIdle()
        assertThat(counterPixels()).isEqualTo(first)
    }

    /** A pause is the reader's choice, so moving to the next exercise does not undo it. */
    @Test fun pauseHoldsAcrossExercises() {
        var id by mutableStateOf("push-up")
        compose.setContent {
            Illustration(true, id)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_pause)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_play)).assertExists()
        id = "squat"
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_play)).assertExists()
    }

    /** A compact demo has no control of its own, so a Pause on the full-size one sharing its playback holds it too. */
    @Test fun aSharedPauseHoldsTheCompactDemo() {
        val app = context.applicationContext as WallCrawlApplication
        val exercise = runBlocking { app.container.exerciseCatalog.getExerciseById("incline-dumbbell-press")!! }
        val playback = mutableStateOf(true)
        compose.setContent {
            WallCrawlTheme {
                Column {
                    ExerciseIllustration(exercise, app.container.exerciseVisualProvider, Modifier.width(88.dp).testTag("compact"),
                        height = 88, animationsEnabled = true, playback = playback)
                    ExerciseIllustration(exercise, app.container.exerciseVisualProvider, height = 210,
                        animationsEnabled = true, playback = playback)
                }
            }
        }
        fun compact() = compose.onNodeWithTag("compact").captureToImage().asAndroidBitmap()
        // The compact demo moves while the shared playback runs.
        compose.waitUntil(15_000) { compact().let { a -> compose.mainClock.advanceTimeBy(900); !a.sameAs(compact()) } }
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_pause)).performClick()
        compose.mainClock.advanceTimeBy(1_500)
        val held = compact()
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(compact().sameAs(held)).isTrue()
        assertThat(playback.value).isFalse()
    }

    /** Turning animations off while away stops a loop that had been playing. */
    @Test fun illustrationStopsWhenAnimationsTurnOff() {
        var enabled by mutableStateOf(true)
        compose.setContent {
            Illustration(enabled)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_pause)).assertExists()
        enabled = false
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_play)).assertExists()
        val still = counterPixels()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertThat(counterPixels()).isEqualTo(still)
    }

    /** A playing state saved before animations were turned off does not restart the loop. */
    @Test fun aRestoredPlayingStateDoesNotOutlastTheSetting() {
        // Not state: changing it recomposes nothing, so the save holds the playing state and only the
        // restored composition sees the setting off, as when it changes while the app is away.
        var enabled = true
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Illustration(enabled)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_pause)).assertExists()
        enabled = false
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_play)).assertExists()
    }

    /** Play, chosen with animations off, survives a restore: only turning the setting off stops it. */
    @Test fun playChosenWithAnimationsOffSurvivesARestore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            Illustration(false)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_play)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_pause)).assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.illustration_pause)).assertExists()
    }

    private val twoFrames = object : ExerciseVisualProvider {
        override fun framesFor(exerciseId: String) = listOf(ExerciseVisual("a.webp"), ExerciseVisual("b.webp"))
    }

    @Composable
    private fun Illustration(animationsEnabled: Boolean, id: String = "push-up") {
        WallCrawlTheme {
            ExerciseIllustration(
                exercise = Exercise(id = id, name = id, primaryMuscles = listOf("Chest"), type = ExerciseType.BODYWEIGHT_REPS),
                visualProvider = twoFrames,
                animationsEnabled = animationsEnabled
            )
        }
    }

    private fun showIllustration(animationsEnabled: Boolean) {
        compose.setContent {
            Illustration(animationsEnabled)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    /** The top-end corner holds the frame counter; fake images load and fail elsewhere in real time. */
    private fun counterPixels(): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val corner = (pixels.width * 3 / 5 until pixels.width).flatMap { x ->
            (0 until pixels.height / 4).map { y -> pixels[x, y].toArgb() }
        }
        return corner.hashCode()
    }

    /** The return-after-break menu says which range is current, not just by colour. */
    @Test fun breakRangeMenuExposesTheSelectedRange() {
        compose.setContent { WallCrawlTheme { BreakDurationSelector(weeks = 0, onSelectWeeks = {}) } }
        compose.onNode(hasClickAction()).performClick()
        compose.waitForIdle()
        val selected = compose.onAllNodes(isSelected()).fetchSemanticsNodes()
        assertThat(selected).hasSize(1)
        val current = context.getString(BreakDurationHelper.findMatchingRange(0).titleRes)
        assertThat(selected.single().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }).contains(current)
    }

    /** Rows stack by text size for the width: normal sizes keep the row exactly as it was. */
    @Test fun stackingRowStacksByTextSizeForTheWidth() {
        var scale by mutableStateOf(1f)
        var width by mutableStateOf(412)
        val density = context.resources.displayMetrics.density
        val base = Configuration(context.resources.configuration)
        compose.setContent {
            val configuration = Configuration(base).apply { screenWidthDp = width }
            CompositionLocalProvider(
                LocalDensity provides Density(density, scale),
                LocalConfiguration provides configuration
            ) {
                WallCrawlTheme {
                    StackingRow(Modifier.requiredWidth(280.dp)) {
                        Text("First", Modifier.weight(1f))
                        Text("Second")
                    }
                }
            }
        }
        fun stacked(w: Int, s: Float): Boolean {
            width = w
            scale = s
            compose.waitForIdle()
            val top = { text: String -> compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top }
            return top("Second") > top("First")
        }
        assertThat(stacked(412, 1f)).isFalse()
        assertThat(stacked(412, 1.15f)).isFalse()
        assertThat(stacked(412, 1.3f)).isTrue()
        assertThat(stacked(360, 1.15f)).isTrue()
        assertThat(stacked(320, 1f)).isFalse()
        assertThat(stacked(320, 1.15f)).isTrue()
    }

    /** The value a set records has to stand out from the row it sits on, in both themes. */
    @Test fun aSelectedSetChipStandsOutFromTheRow() {
        var theme by mutableStateOf(ThemePreference.LIGHT)
        var row = Color.Unspecified
        compose.setContent {
            WallCrawlTheme(themePreference = theme) {
                row = MaterialTheme.colorScheme.surfaceVariant
                Box(Modifier.background(row).padding(16.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("5") }, colors = setChipColors(), modifier = Modifier.testTag("chip"))
                }
            }
        }
        listOf(ThemePreference.LIGHT, ThemePreference.DARK).forEach { next ->
            theme = next
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("chip").captureToImage().toPixelMap()
            // The tagged node is Material's 48 dp touch box, wider than the chip. The fill is the commonest colour
            // that is not the row: it covers far more pixels than the label, whatever the font, density or width.
            val rowArgb = row.toArgb()
            val counts = (0 until pixels.width).flatMap { x -> (0 until pixels.height).map { y -> pixels[x, y].toArgb() } }
                .filter { it != rowArgb }.groupingBy { it }.eachCount()
            val (fill, area) = counts.maxBy { it.value }
            // A fill that matched the row would leave only the label here, so the fill must be a real area.
            assertThat(area).isAtLeast(pixels.width * pixels.height / 4)
            assertThat(ColorUtils.calculateContrast(fill, rowArgb)).isAtLeast(3.0)
        }
    }

    /** The placeholder names an empty search field; once typed in, this names it and keeps the query audible. */
    @Test fun aSearchFieldKeepsItsNameAndQueryOnceTypedIn() {
        var query by mutableStateOf("")
        compose.setContent {
            WallCrawlTheme {
                OutlinedTextField(value = query, onValueChange = {}, modifier = Modifier.testTag("search").a11ySearchField("Search", query))
            }
        }
        compose.onNodeWithTag("search")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        query = "squat"
        compose.waitForIdle()
        compose.onNodeWithTag("search")
            .assert(hasContentDescription("Search"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "squat"))
    }

    /** A menu anchor says it opens a list and whether the list is open. */
    @Test fun breakRangeAnchorIsADropdownThatSaysWhetherItIsOpen() {
        compose.setContent { WallCrawlTheme { BreakDurationSelector(weeks = 0, onSelectWeeks = {}) } }
        assertDropdownStates(compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)))
    }

    @Test fun languageChipIsADropdownThatSaysWhetherItIsOpen() {
        compose.setContent { WallCrawlTheme { LanguageChip(current = AppLanguage.SYSTEM, onSelect = {}) } }
        assertDropdownStates(compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)))
    }

    private fun assertDropdownStates(anchor: SemanticsNodeInteraction) {
        anchor.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.a11y_collapsed)))
            .performClick()
        compose.waitForIdle()
        anchor.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.a11y_expanded)))
    }

    /** Showing a set's feedback is a disclosure, so it says whether it is open. */
    @Test fun setFeedbackDisclosureSaysWhetherItIsOpen() {
        compose.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = WorkoutSet(id = "s", workoutExerciseId = "e", setNumber = 1, exerciseType = ExerciseType.WEIGHT_REPS, targetReps = 8, targetWeight = 40.0,
                        completedReps = 8, completedWeight = 40.0, isCompleted = true),
                    weightUnit = "kg", previousSet = null,
                    onCompletionChanged = { _, _ -> }, onSkipSet = { _, _ -> }, onRecordEffort = { _, _ -> }, onRecordFeltManageable = {}
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.set_feedback_show))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.a11y_collapsed)))
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.set_feedback_hide))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.a11y_expanded)))
    }

    /** A spinner has no text of its own, so it says once that something is loading. */
    @Test fun loadingIndicatorIsNamedAndAnnounced() {
        compose.setContent { WallCrawlTheme { LoadingIndicator() } }
        compose.onNode(
            hasContentDescription(context.getString(R.string.a11y_loading)).and(hasLiveRegion())
        ).assertExists()
    }

    /** A stacked button takes the whole line, as the weight it was given in a row asks. */
    @Test fun stackedWeightedButtonsFillTheirLine() {
        val density = context.resources.displayMetrics.density
        val configuration = Configuration(context.resources.configuration).apply { screenWidthDp = 320 }
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density, 1.8f),
                LocalConfiguration provides configuration
            ) {
                WallCrawlTheme {
                    StackingRow(Modifier.requiredWidth(280.dp)) {
                        WallCrawlOutlinedButton("Back", {}, Modifier.weight(1f).testTag("back"))
                        WallCrawlPrimaryButton("Next", {}, Modifier.weight(1f).testTag("next"))
                    }
                }
            }
        }
        compose.onNodeWithTag("back").assertWidthIsEqualTo(280.dp)
        compose.onNodeWithTag("next").assertWidthIsEqualTo(280.dp)
    }

    /** A description replaces what the value box speaks, so it states the value itself, and follows each step. */
    @Test fun aSetValueSpeaksItsValueAsItSteps() {
        compose.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = WorkoutSet(id = "s", workoutExerciseId = "e", setNumber = 1, exerciseType = ExerciseType.WEIGHT_REPS, targetReps = 8, targetWeight = 40.0),
                    weightUnit = "kg", previousSet = null,
                    onCompletionChanged = { _, _ -> }, onSkipSet = { _, _ -> }, onRecordEffort = { _, _ -> }, onRecordFeltManageable = {}
                )
            }
        }
        val load = compose.onNodeWithContentDescription(context.getString(R.string.set_field_value_accessibility, context.getString(R.string.set_field_with_unit, context.getString(R.string.set_field_load), "kg"), "1"))
        load.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "40"))
        compose.onNodeWithContentDescription(context.getString(R.string.set_field_increase_accessibility, context.getString(R.string.set_field_load), "1")).performClick()
        compose.waitForIdle()
        val stepped = load.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertThat(stepped).isNotEqualTo("40")
        assertThat(stepped.toDouble()).isGreaterThan(40.0)
    }

    /** The last stop reason stays reachable, so the ones above it can be scrolled to. A dialog is its own window: see Limitations for its font scale. */
    @Test fun theLastStopReasonIsReachable() {
        val density = context.resources.displayMetrics.density
        var last = ""
        compose.setContent {
            last = stopReasonLabel(SetStopReason.entries.last())
            CompositionLocalProvider(LocalDensity provides Density(density, 1.8f)) {
                WallCrawlTheme {
                    GymFloorSetRow(
                        set = WorkoutSet(id = "s", workoutExerciseId = "e", setNumber = 1, exerciseType = ExerciseType.WEIGHT_REPS, targetReps = 8, targetWeight = 40.0),
                        weightUnit = "kg", previousSet = null,
                        onCompletionChanged = { _, _ -> }, onSkipSet = { _, _ -> }, onRecordEffort = { _, _ -> }, onRecordFeltManageable = {}
                    )
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.set_skip_or_stop)).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(last))
        compose.onNodeWithText(last).assertIsDisplayed().assertHasClickAction()
    }

    private fun bestContrastOfField(): Double {
        val pixels = compose.onNode(hasSetTextAction()).captureToImage().toPixelMap()
        val background = pixels[pixels.width / 2, pixels.height / 2].toArgb()
        var best = 1.0
        // The inner text area only: the outline and label are not what this is about.
        for (x in pixels.width / 12 until pixels.width / 3) for (y in pixels.height / 4 until pixels.height * 3 / 4) {
            best = maxOf(best, ColorUtils.calculateContrast(pixels[x, y].toArgb(), background))
        }
        return best
    }
}
