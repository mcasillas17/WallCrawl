package wallcrawl.elopenmike.com.feature.workout

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import wallcrawl.elopenmike.com.WallCrawlApplication
import wallcrawl.elopenmike.com.core.ui.localization.ExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import java.util.Locale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onRoot
import java.io.File
import wallcrawl.elopenmike.com.core.model.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.test.hasLiveRegion
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.ai.RecommendationSnapshot
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetOutcomeRules
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import androidx.compose.material3.MaterialTheme
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/**
 * Drives the assembled active-workout screen: the rest countdown, its explicit controls,
 * and the confirmations guarding finish and discard.
 */
class ActiveWorkoutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * A label read from resources rather than repeated here. These tests are about the rest
     * timer and the discard confirmation, not about the wording of a button, so a copy edit
     * in either language must not break them.
     */
    private fun text(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WallCrawlApplication
    private var destructive = Color.Unspecified
    private val clock = TestClock()
    private val repository = ScreenTestRepository(session())
    private val viewModel = ActiveWorkoutViewModel(
        sessionId = SESSION_ID,
        workoutRepository = repository,
        exerciseCatalog = app.container.exerciseCatalog,
        workoutHistoryAnalyzer = WorkoutHistoryAnalyzer(),
        nowMillis = { WALL_CLOCK },
        elapsedRealtimeClock = clock
    )

    @Test fun supportiveLoggingShowsFrozenExecutionAndKeepsFinishReachable() {
        val selected = SelectedCondition("withdrawn", 1, mapOf("side" to "recorded-left"))
        val instruction = ConditionText("Original fictional logging instruction", "Instrucción ficticia de registro original")
        val frozen = SessionConditionContext(1, 1, listOf(selected), mapOf("withdrawn" to "old-v1"), listOf(
            AppliedConditionRule(ConditionRuleReference("withdrawn", "old-v1", "case", "rule", 1),
                ConditionExerciseIdentity("incline-dumbbell-press", "a".repeat(64), ExerciseType.WEIGHT_REPS, "b".repeat(64)),
                "original-execution", PrescriptionPurpose.SUPPORTIVE, instruction)), emptyList(), emptyList())
        val initial = session()
        val supportive = initial.copy(conditionContext = frozen, exercises = initial.exercises.map {
            it.copy(prescription = it.prescription.copy(purpose = PrescriptionPurpose.SUPPORTIVE)) })
        val vm = ActiveWorkoutViewModel(SESSION_ID, ScreenTestRepository(supportive), InMemoryExerciseCatalog(), WorkoutHistoryAnalyzer())
        composeRule.setContent { WallCrawlTheme(themePreference = ThemePreference.DARK) { ActiveWorkoutScreen(vm, NoVisualProvider, {}, {}) } }
        composeRule.onNodeWithText(instruction.en).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.condition_supportive)).assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.filesDir
        output.mkdirs()
        File(output, "active-frozen-supportive-dark.png").outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.onNodeWithText("Finish anyway").assertIsDisplayed()
    }

    @Test
    fun adjustedLoadSurvivesSwitchingSetsAndAnUnrelatedPersistedUpdate() {
        showScreen()
        composeRule.onNodeWithContentDescription("Increase Load for set 1").performScrollTo().performClick()
        composeRule.onNodeWithText("42.5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Set 2").performScrollTo().performClick()
        repository.historyReady.value = false
        try {
            composeRule.runOnIdle {
                runBlocking {
                    repository.logSetCompletion("set-2", SetPerformanceInput(
                        reps = 11, weight = 40.0, isCompleted = false
                    ))
                }
            }
            composeRule.onNodeWithContentDescription("Set 1").performScrollTo().performClick()
            composeRule.onNodeWithText("42.5").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Log set").performScrollTo().performClick()
            composeRule.waitForIdle()
            assertThat(runBlocking { repository.getActiveSessionOnce() }!!.exercises.first()
                .sets.first().completedWeight).isEqualTo(42.5)
            assertThat(repository.historySubscriptions).isEqualTo(1)
        } finally {
            repository.historyReady.value = true
        }
    }

    @Test
    fun completingASet_showsTheRestCountdownWithItsExplicitControls() {
        showScreen()

        composeRule.onNodeWithText("Log set").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Rest remaining 2:00").assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.rest_add_thirty)).performClick()
        composeRule.waitForIdle()
        // Never a live region while it counts, or TalkBack would read every tick; once when over.
        composeRule.onNodeWithContentDescription("Rest remaining 2:30").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))

        composeRule.onNodeWithText("Skip rest").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rest finished").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        composeRule.onNodeWithContentDescription("Rest timer options").performClick()
        composeRule.onNodeWithText("Dismiss timer").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasTextStartingWith("Rest")).assertCountEquals(0)
    }

    @Test
    fun spanishFineStepsWithRestKeepLogReachable() {
        val locale = Locale.forLanguageTag("es-ES")
        val localized = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(locale) })
        val vocabulary = ExerciseVocabulary(runBlocking { app.container.exerciseLocalizationSource.localization() }, locale.toLanguageTag())
        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides localized.resources.configuration,
                LocalContext provides localized, LocalResources provides localized.resources,
                LocalExerciseVocabulary provides vocabulary) {
                WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                    ActiveWorkoutScreen(viewModel, app.container.exerciseVisualProvider, {}, {})
                }
            }
        }
        val log = localized.getString(R.string.guided_log_set)
        composeRule.onNodeWithText(log).performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            (viewModel.uiState.value as? ActiveWorkoutUiState.Active)?.restTimer?.isRunning == true
        }
        val load = localized.getString(R.string.set_field_load)
        val withUnit = localized.getString(R.string.set_field_with_unit, load, "lb")
        val increase = localized.getString(R.string.set_field_increase_accessibility, load, "2")
        listOf("2,5", "1").forEach { step ->
            composeRule.onNodeWithContentDescription(localized.getString(R.string.guided_change_step, withUnit, step))
                .performScrollTo().performClick()
        }
        composeRule.onNodeWithContentDescription(increase).performScrollTo().performClick()
        composeRule.onNodeWithText("40,1").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onNodeWithText(log).performScrollTo().assertIsDisplayed()
        capture("guided-steps-es-light")
        composeRule.onNodeWithText(log).performClick()
        composeRule.waitUntil(10_000) {
            runBlocking { repository.getActiveSessionOnce() }!!.exercises.first().sets[1].completedWeight == 40.1
        }
    }

    @Test
    fun rejectedWriteLeavesTheEditorAndCanBeRetried() {
        showScreen()
        capture("guided-active-en")
        repository.rejectWrites = true
        composeRule.onNodeWithContentDescription("Increase Load for set 1").performClick()
        composeRule.onNodeWithText("Log set").performClick()
        composeRule.onNodeWithText(text(R.string.workout_set_update_error)).performScrollTo().assertIsDisplayed()
        capture("guided-save-error-en")
        composeRule.onNodeWithText("42.5").assertExists()
        repository.rejectWrites = false
        composeRule.onNodeWithText("Log set").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Rest remaining 2:00").performScrollTo().assertIsDisplayed()
        capture("guided-rest-en")
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.onNodeWithText("Finish anyway").performClick()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
        capture("guided-summary-en")
    }

    @Test
    fun swipingArtworkChangesExercisesAndTappingOpensGuidance() {
        val original = session()
        val first = original.exercises.single()
        val second = first.copy(id = "second", exerciseId = "lat-pulldown", orderIndex = 1,
            sets = first.sets.map { it.copy(id = "second-${it.id}", workoutExerciseId = "second") })
        val vm = ActiveWorkoutViewModel(SESSION_ID, ScreenTestRepository(original.copy(exercises = listOf(first, second))),
            InMemoryExerciseCatalog(), WorkoutHistoryAnalyzer(), elapsedRealtimeClock = clock)
        composeRule.setContent { WallCrawlTheme { ActiveWorkoutScreen(vm, NoVisualProvider, {}, {}) } }
        val art = composeRule.onNodeWithContentDescription("Expand ", substring = true)
        art.performTouchInput { swipeRight() }
        assertThat((vm.uiState.value as ActiveWorkoutUiState.Active).currentExerciseIndex).isEqualTo(0)
        composeRule.onNodeWithText("Log set").performClick()
        composeRule.waitUntil(10_000) { (vm.uiState.value as? ActiveWorkoutUiState.Active)?.restTimer?.isRunning == true }
        composeRule.onNodeWithContentDescription("Rest remaining 2:00").assertIsDisplayed()
        val deadline = RestTimerState.Running("set-1", 120_000L)
        composeRule.onNodeWithContentDescription("Decrease Load for set 2").performScrollTo().performClick()
        art.performScrollTo().performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Previous exercise").assertExists()
        assertThat((vm.uiState.value as ActiveWorkoutUiState.Active).currentExerciseIndex).isEqualTo(1)
        art.performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Close").assertDoesNotExist()
        art.performClick()
        composeRule.onNodeWithText("Close").assertIsDisplayed().performClick()
        art.performTouchInput { swipeRight() }
        composeRule.onNodeWithText("Next exercise").assertExists()
        composeRule.onNodeWithText("37.5").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Log set").assertIsEnabled()
        assertThat((vm.uiState.value as ActiveWorkoutUiState.Active).restTimer.state).isEqualTo(deadline)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.filesDir
        output.mkdirs()
        File(output, "$name.png").outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun timeSpentInTheBackground_isReflectedWhenTheCountdownIsRefreshed() {
        showScreen()
        composeRule.onNodeWithText("Log set").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // The app was away for ninety seconds and ticked nothing while it was gone.
        clock.advance(90_000L)
        viewModel.onRestTimerTick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Rest remaining 0:30").assertIsDisplayed()
    }

    @Test
    fun theRunningRestTimer_survivesRecomposition() {
        var recompositionKey by mutableStateOf(0)
        composeRule.setContent {
            WallCrawlTheme {
                key(recompositionKey) {
                    ActiveWorkoutScreen(
                        viewModel = viewModel,
                        visualProvider = NoVisualProvider,
                        onNavigateBack = {},
                        onWorkoutFinished = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Log set").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rest remaining 2:00").assertIsDisplayed()

        recompositionKey = 1
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Rest remaining 2:00").assertIsDisplayed()
    }

    @Test
    fun finishingWithOpenSets_confirmsTheCountBeforePersistingAnything() {
        showScreen()

        composeRule.onNodeWithText("Finish").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Finish with 2 unlogged sets?").assertIsDisplayed()
        assertThat(repository.completeCalls).isEqualTo(0)

        composeRule.onNodeWithText("Keep going").performClick()
        composeRule.waitForIdle()
        assertThat(repository.completeCalls).isEqualTo(0)

        composeRule.onNodeWithText("Finish").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Finish anyway").performClick()
        composeRule.waitForIdle()

        assertThat(repository.completeCalls).isEqualTo(1)
    }

    @Test
    fun closingTheWorkout_requiresAnExplicitDiscardConfirmation() {
        showScreen()

        composeRule.onNodeWithText(text(R.string.guided_leave_workout)).performClick()
        composeRule.onNodeWithText(text(R.string.guided_discard_workout)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Discard this workout?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep workout").performClick()
        composeRule.waitForIdle()
        assertThat(repository.cancelCalls).isEqualTo(0)

        composeRule.onNodeWithText(text(R.string.guided_leave_workout)).performClick()
        composeRule.onNodeWithText(text(R.string.guided_discard_workout)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.waitForIdle()

        assertThat(repository.cancelCalls).isEqualTo(1)
    }

    @Test
    fun confirmationDialogs_reserveRedForDestructiveActions() {
        showScreen()

        composeRule.onNodeWithText("Finish").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Finish anyway").assertContainsColor(destructive)
        composeRule.onNodeWithText("Keep going").assertDoesNotContainColor(destructive)
        composeRule.onNodeWithText("Keep going").performClick()

        composeRule.onNodeWithText(text(R.string.guided_leave_workout)).performClick()
        composeRule.onNodeWithText(text(R.string.guided_discard_workout)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard").assertContainsColor(destructive)
        composeRule.onNodeWithText("Keep workout").assertDoesNotContainColor(destructive)
    }

    /** At 320 dp and 1.8x nothing on a logged set or the rest bar is squeezed away. */
    @Test
    fun aLoggedSetAndTheRestBarKeepEveryControlAtLargeText() {
        showScreen(fontScale = 1.8f, narrow = true)
        composeRule.onNodeWithText("Log set").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { (viewModel.uiState.value as? ActiveWorkoutUiState.Active)?.restTimer?.isRunning == true }
        listOf(text(R.string.rest_add_thirty), "Skip rest").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHasClickAction()
        }
        // An icon button draws 40 dp and extends its touch area to 48 dp, so the touch bounds are what count.
        val options = composeRule.onNodeWithContentDescription("Rest timer options").assertHasClickAction().fetchSemanticsNode()
        with(composeRule.density) {
            assertThat(options.touchBoundsInRoot.width.toDp()).isAtLeast(48.dp)
            assertThat(options.touchBoundsInRoot.height.toDp()).isAtLeast(48.dp)
        }
        composeRule.onNodeWithContentDescription("Set 1").performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.set_feedback_show)).performScrollTo().performClick()
        listOf("Yes", "No").forEach {
            composeRule.onNodeWithContentDescription("Set 1 felt manageable, $it").performScrollTo().assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
    }

    /** Returning to a bar that has already finished does not announce it a second time. */
    @Test
    fun aFinishedRestBarIsQuietWhenComposedAgain() {
        var shown by mutableStateOf(true)
        composeRule.setContent {
            WallCrawlTheme {
                if (shown) ActiveWorkoutScreen(viewModel, NoVisualProvider, {}, {})
            }
        }
        composeRule.onNodeWithText("Log set").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { (viewModel.uiState.value as? ActiveWorkoutUiState.Active)?.restTimer?.isRunning == true }
        composeRule.onNodeWithText("Skip rest").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rest finished").assert(hasLiveRegion())
        shown = false
        composeRule.waitForIdle()
        shown = true
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rest finished")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    /** A template name can be 120 characters: at large text it must still leave Log set and Finish reachable. */
    @Test
    fun theLongestSessionNameLeavesLogSetAndFinishReachableAtLargeText() {
        val name = "Routine ".repeat(15).take(WorkoutTemplate.MAX_NAME_LENGTH)
        val longNamed = ActiveWorkoutViewModel(
            sessionId = SESSION_ID,
            workoutRepository = ScreenTestRepository(session(name)),
            exerciseCatalog = InMemoryExerciseCatalog(),
            workoutHistoryAnalyzer = WorkoutHistoryAnalyzer(),
            nowMillis = { WALL_CLOCK },
            elapsedRealtimeClock = clock
        )
        showScreen(fontScale = 1.8f, narrow = true, screenViewModel = longNamed)
        composeRule.onNodeWithText(name).assertExists()
        composeRule.onNodeWithText("Log set").performScrollTo().assertIsDisplayed()
        // assertIsDisplayed also passes for a partly clipped control, so this compares the edges.
        val host = composeRule.onNodeWithTag(HOST_TAG).getBoundsInRoot()
        val finish = composeRule.onNodeWithText(text(R.string.workout_action_finish)).getBoundsInRoot()
        assertThat(finish.bottom).isAtMost(host.bottom)
        assertThat(finish.top).isAtLeast(host.top)
    }

    /** A refused write is announced, and the same refusal again is a new announcement. */
    @Test
    fun aRefusedSetWriteIsAnnouncedEachTime() {
        showScreen()
        repository.rejectWrites = true
        composeRule.onNodeWithText("Log set").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text(R.string.workout_set_update_error)).fetchSemanticsNodes().isNotEmpty()
        }
        val message = composeRule.onNodeWithText(text(R.string.workout_set_update_error), useUnmergedTree = true)
        message.assertIsDisplayed().assert(hasLiveRegion())
        val first = message.fetchSemanticsNode().id
        composeRule.onNodeWithText("Log set").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertThat(message.fetchSemanticsNode().id).isNotEqualTo(first)
    }

    private fun showScreen(
        fontScale: Float = 1f,
        narrow: Boolean = false,
        screenViewModel: ActiveWorkoutViewModel = viewModel
    ) {
        composeRule.setContent {
            val density = LocalDensity.current.density
            val base = LocalConfiguration.current
            val configuration = if (narrow) Configuration(base).apply { screenWidthDp = 320 } else base
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalConfiguration provides configuration
            ) {
                Box(if (narrow) Modifier.requiredSize(320.dp, 640.dp).testTag(HOST_TAG) else Modifier) {
                    WallCrawlTheme {
                        destructive = MaterialTheme.colorScheme.error
                        ActiveWorkoutScreen(
                            viewModel = screenViewModel,
                            visualProvider = app.container.exerciseVisualProvider,
                            onNavigateBack = {},
                            onWorkoutFinished = {},
                            onOpenHistory = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun session(name: String = "Push"): WorkoutSession {
        val workoutExerciseId = "workout-exercise"
        return WorkoutSession(
            id = SESSION_ID,
            name = name,
            status = SessionStatus.IN_PROGRESS,
            exercises = listOf(
                WorkoutExercise(
                    id = workoutExerciseId,
                    sessionId = SESSION_ID,
                    exerciseId = "incline-dumbbell-press",
                    orderIndex = 0,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 2,
                        repRange = RepRange(8, 10),
                        targetWeight = 40.0,
                        restSeconds = 120
                    ),
                    sets = (1..2).map { number ->
                        WorkoutSet(
                            id = "set-$number",
                            workoutExerciseId = workoutExerciseId,
                            setNumber = number,
                            targetReps = 10,
                            targetWeight = 40.0,
                            isCompleted = false
                        )
                    }
                )
            )
        )
    }

    private fun hasTextStartingWith(prefix: String) = SemanticsMatcher(
        "text starts with '$prefix'"
    ) { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith(prefix) } == true
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertContainsColor(
        expected: Color
    ) {
        assertThat(closestColorDistance(expected)).isAtMost(COLOR_MATCH_TOLERANCE)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotContainColor(
        unexpected: Color
    ) {
        assertThat(closestColorDistance(unexpected)).isGreaterThan(COLOR_MATCH_TOLERANCE)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.closestColorDistance(
        target: Color
    ): Float {
        val pixels = captureToImage().toPixelMap()
        var closest = Float.MAX_VALUE
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                val distance =
                    square(color.red - target.red) +
                        square(color.green - target.green) +
                        square(color.blue - target.blue)
                closest = minOf(closest, distance)
            }
        }
        return closest
    }

    private fun square(value: Float): Float = value * value

    private companion object {
        const val SESSION_ID = "session"
        const val WALL_CLOCK = 1_777_777L
        const val COLOR_MATCH_TOLERANCE = 0.01f
    }
}

private class TestClock(private var now: Long = 0L) : ElapsedRealtimeClock {
    override fun elapsedRealtimeMillis(): Long = now
    fun advance(millis: Long) {
        now += millis
    }
}

private object NoVisualProvider : ExerciseVisualProvider {
    override fun framesFor(exerciseId: String) = emptyList<ExerciseVisual>()
}

private class ScreenTestRepository(initial: WorkoutSession) : WorkoutRepository {
    override suspend fun hasPendingConditionReassessment(profileId: String) = false

    override suspend fun getCompletedSessionsInRange(startTimestamp: Long, endTimestampExclusive: Long): List<WorkoutSession> = emptyList()
    private val session = MutableStateFlow<WorkoutSession?>(initial)
    var rejectWrites: Boolean = false
    var completeCalls: Int = 0
        private set
    var cancelCalls: Int = 0
        private set
    val historyReady = MutableStateFlow(true)
    var historySubscriptions = 0
        private set

    override fun observeActiveSession(): Flow<WorkoutSession?> = session
    override suspend fun getActiveSessionOnce(): WorkoutSession? = session.value
    override suspend fun getSessionById(sessionId: String): WorkoutSession? = session.value
    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = session
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> = flow {
        historySubscriptions++
        historyReady.first { it }
        emit(emptyList())
        awaitCancellation()
    }

    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(0)
    override fun observeCompletedWorkoutCountInRange(startTimestamp: Long, endTimestampExclusive: Long): Flow<Int> = flowOf(0)
    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> = emptyList()
    override suspend fun getRecentSessions(limit: Int): List<WorkoutSession> = emptyList()
    override suspend fun getRecommendationRecords(sessionIds: List<String>): List<wallcrawl.elopenmike.com.core.model.RecommendationRecord> = emptyList()

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        displayName: String,
        displayRationale: String,
        userProfile: UserProfile,
        recommendation: RecommendationSnapshot?
    ): WorkoutSession = error("Not used")

    override suspend fun startWorkoutFromTemplate(
        template: WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun logSetCompletion(setId: String, performance: SetPerformanceInput) {
        check(!rejectWrites)
        SetOutcomeRules.requireValidOutcome(performance)
        val current = session.value ?: return
        session.value = current.copy(
            exercises = current.exercises.map { exercise ->
                exercise.copy(
                    sets = exercise.sets.map { set ->
                        if (set.id != setId) {
                            set
                        } else {
                            set.copy(
                                completedReps = performance.reps,
                                completedWeight = performance.weight,
                                rpe = performance.rpe,
                                rir = performance.rir,
                                feltManageable = performance.feltManageable,
                                completedAtTimestamp = performance.completedAtTimestamp,
                                stoppedAtTimestamp = performance.stoppedAtTimestamp,
                                stopReason = performance.stopReason,
                                isCompleted = performance.isCompleted
                            )
                        }
                    }
                )
            }
        )
    }

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): WorkoutSummary {
        completeCalls += 1
        val completed = requireNotNull(session.value).copy(
            status = SessionStatus.COMPLETED,
            completedAtTimestamp = 5_000L,
            actualDurationMinutes = actualDurationMinutes
        )
        session.value = completed
        return summary(completed)
    }

    override suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary? =
        session.value?.takeIf { it.status == SessionStatus.COMPLETED }?.let(::summary)

    override suspend fun cancelWorkout(sessionId: String) {
        cancelCalls += 1
    }

    private fun summary(current: WorkoutSession) = WorkoutSummary(
        sessionId = current.id,
        workoutName = current.name,
        durationMinutes = current.actualDurationMinutes,
        totalSetsCompleted = current.completedSetsCount,
        totalVolume = current.totalVolume,
        unit = current.weightUnit,
        completedAtTimestamp = current.completedAtTimestamp ?: current.startedAtTimestamp
    )
}

private const val HOST_TAG = "narrow-host"
