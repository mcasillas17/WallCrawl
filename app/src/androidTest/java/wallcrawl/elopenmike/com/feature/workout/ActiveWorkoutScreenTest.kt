package wallcrawl.elopenmike.com.feature.workout

import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Box
import wallcrawl.elopenmike.com.test.hasLiveRegion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
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
import wallcrawl.elopenmike.com.core.ui.theme.AccentTextOnDark
import wallcrawl.elopenmike.com.core.ui.theme.AccentTextOnLight
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

    private val clock = TestClock()
    private val repository = ScreenTestRepository(session())
    private val viewModel = ActiveWorkoutViewModel(
        sessionId = SESSION_ID,
        workoutRepository = repository,
        exerciseCatalog = InMemoryExerciseCatalog(),
        workoutHistoryAnalyzer = WorkoutHistoryAnalyzer(),
        nowMillis = { WALL_CLOCK },
        elapsedRealtimeClock = clock
    )

    @Test
    fun persistedSetUpdateKeepsFocusedPartialInputAndFeedbackOpen() {
        showScreen()
        composeRule.onAllNodesWithText(text(R.string.set_feedback_show))[0].performScrollTo().performClick()
        val load = composeRule.onNodeWithContentDescription("Load (lb) for set 1")
        load.performScrollTo().performClick().performTextReplacement("47.")
        load.assertIsFocused()
        repository.historyReady.value = false
        try {
            composeRule.runOnIdle {
                runBlocking {
                    repository.logSetCompletion("set-2", SetPerformanceInput(
                        reps = 11, weight = 40.0, isCompleted = false
                    ))
                }
            }
            composeRule.waitForIdle()
            load.assertTextContains("47.").assertIsFocused()
            composeRule.onNodeWithText(text(R.string.set_feedback_hide)).assertExists()
            assertThat(repository.historySubscriptions).isEqualTo(1)
        } finally {
            repository.historyReady.value = true
        }
    }

    @Test
    fun completingASet_showsTheRestCountdownWithItsExplicitControls() {
        showScreen()

        composeRule.onNodeWithContentDescription("Set 1 complete").performScrollTo().assertIsDisplayed().performClick()
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

        composeRule.onNodeWithText("Dismiss").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasTextStartingWith("Rest")).assertCountEquals(0)
    }

    @Test
    fun timeSpentInTheBackground_isReflectedWhenTheCountdownIsRefreshed() {
        showScreen()
        composeRule.onNodeWithContentDescription("Set 1 complete").performScrollTo().assertIsDisplayed().performClick()
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

        composeRule.onNodeWithContentDescription("Set 1 complete").performScrollTo().assertIsDisplayed().performClick()
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

        composeRule.onNodeWithContentDescription(
            text(R.string.workout_close_content_description)
        ).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Discard this workout?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep workout").performClick()
        composeRule.waitForIdle()
        assertThat(repository.cancelCalls).isEqualTo(0)

        composeRule.onNodeWithContentDescription(
            text(R.string.workout_close_content_description)
        ).performClick()
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

        composeRule.onNodeWithContentDescription(
            text(R.string.workout_close_content_description)
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard").assertContainsColor(destructive)
        composeRule.onNodeWithText("Keep workout").assertDoesNotContainColor(destructive)
    }

    /** At 320 dp and 1.8x nothing on a completed set or the rest bar is squeezed away. */
    @Test
    fun aCompletedSetAndTheRestBarKeepEveryControlAtLargeText() {
        showScreen(fontScale = 1.8f, narrow = true)
        activateFromList("Set 1 complete")
        composeRule.waitForIdle()
        listOf("+30 s", "Skip rest", "Dismiss").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHasClickAction()
        }
        scrollListTo("Set 1 felt manageable, Yes").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton))
        scrollListTo("Set 1 felt manageable, No").assertIsDisplayed().assertWidthIsAtLeast(48.dp)
    }

    /** On CI's 320x640 dp screen at 1.8x, a refusal and a running rest still leave the actions on screen. */
    @Test
    fun aRefusalAndTheRestBarLeaveTheBottomActionsOnScreenAtLargeText() {
        showScreen(fontScale = 1.8f, narrow = true)
        activateFromList("Set 1 complete")
        composeRule.waitForIdle()
        repository.failSetWrites = true
        activateFromList("Set 2 complete")
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(text(R.string.workout_set_update_error)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(text(R.string.workout_set_update_error)).assertIsDisplayed()
        composeRule.onNodeWithText("Skip rest").assertIsDisplayed()

        assertFinishIsWhollyInsideTheScreen()
        composeRule.onNodeWithText(text(R.string.workout_action_finish_workout)).assertHasClickAction()
    }

    /** Returning to a bar that has already finished does not announce it a second time. */
    @Test
    fun aFinishedRestBarIsQuietWhenComposedAgain() {
        var shown by mutableStateOf(true)
        composeRule.setContent {
            WallCrawlTheme {
                if (shown) {
                    ActiveWorkoutScreen(
                        viewModel = viewModel,
                        visualProvider = NoVisualProvider,
                        onNavigateBack = {},
                        onWorkoutFinished = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        activateFromList("Set 1 complete")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Skip rest").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rest finished").assert(hasLiveRegion())
        shown = false
        composeRule.waitForIdle()
        shown = true
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rest finished")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    /** A template name can be 120 characters: at large text it scrolls away and leaves the list its height. */
    @Test
    fun theLongestSessionNameLeavesTheListAndActionsAtLargeText() {
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
        val list = composeRule.onNode(hasScrollToIndexAction()).getBoundsInRoot()
        assertThat(list.bottom - list.top).isAtLeast(350.dp)
        scrollListTo("Set 1 complete").assertIsDisplayed()
        assertFinishIsWhollyInsideTheScreen()
    }

    /** assertIsDisplayed also passes for a partly clipped control, so this compares the edges. */
    private fun assertFinishIsWhollyInsideTheScreen() {
        val host = composeRule.onNodeWithTag(HOST_TAG).getBoundsInRoot()
        val finish = composeRule.onNodeWithText(text(R.string.workout_action_finish_workout)).getBoundsInRoot()
        assertThat(finish.bottom).isAtMost(host.bottom)
        assertThat(finish.top).isAtLeast(host.top)
    }

    /** A lazy list composes only what it shows, and on a short screen a row can start off it. */
    private fun scrollListTo(description: String): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description))
        return composeRule.onNodeWithContentDescription(description)
    }

    /** The action, as TalkBack activates it: in a short list the row's centre can sit under the actions. */
    private fun activateFromList(description: String) {
        scrollListTo(description).performSemanticsAction(SemanticsActions.OnClick)
    }

    /** The refusal sits above the list, so it is seen and announced wherever the reader had scrolled to. */
    @Test
    fun aRefusedSetWriteIsAnnouncedAboveTheList() {
        repository.failSetWrites = true
        showScreen()
        activateFromList("Set 1 complete")
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.workout_set_update_error))
            .assertIsDisplayed().assert(hasLiveRegion())
        val banner = composeRule.onNodeWithText(text(R.string.workout_set_update_error)).getBoundsInRoot()
        val list = composeRule.onNode(hasScrollToIndexAction()).getBoundsInRoot()
        assertThat(banner.bottom).isAtMost(list.top)

        // The same refusal again has to be a new announcement, so it has to be a new live-region node.
        val message = composeRule.onNodeWithText(text(R.string.workout_set_update_error), useUnmergedTree = true)
        val first = message.fetchSemanticsNode().id
        // As TalkBack does: on a short screen the banner has pushed the row out from under a finger.
        activateFromList("Set 1 complete")
        composeRule.waitForIdle()
        assertThat(message.fetchSemanticsNode().id).isNotEqualTo(first)
    }

    /** The theme's readable red; [showScreen] follows the device's light or dark setting. */
    private val destructive
        get() = if (ApplicationProvider.getApplicationContext<android.content.Context>().resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        ) AccentTextOnDark else AccentTextOnLight

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
                ActiveWorkoutScreen(
                    viewModel = screenViewModel,
                    visualProvider = NoVisualProvider,
                    onNavigateBack = {},
                    onWorkoutFinished = {}
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
    override suspend fun getCompletedSessionsInRange(startTimestamp: Long, endTimestampExclusive: Long): List<WorkoutSession> = emptyList()
    private val session = MutableStateFlow<WorkoutSession?>(initial)
    var completeCalls: Int = 0
        private set
    var failSetWrites = false
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
        if (failSetWrites) error("refused")
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
