package wallcrawl.elopenmike.com.feature.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
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
import androidx.compose.ui.test.performScrollToNode
import wallcrawl.elopenmike.com.core.conditions.ConditionAndroidFixtures
import wallcrawl.elopenmike.com.core.conditions.MovementDemand
import androidx.compose.ui.test.performTextReplacement
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
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
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
        composeRule.onNodeWithContentDescription("Rest remaining 2:30").assertIsDisplayed()

        composeRule.onNodeWithText("Skip rest").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rest finished").assertIsDisplayed()

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
        composeRule.onNodeWithText("Finish anyway").assertContainsColor(CrimsonRedPrimary)
        composeRule.onNodeWithText("Keep going").assertDoesNotContainColor(CrimsonRedPrimary)
        composeRule.onNodeWithText("Keep going").performClick()

        composeRule.onNodeWithContentDescription(
            text(R.string.workout_close_content_description)
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard").assertContainsColor(CrimsonRedPrimary)
        composeRule.onNodeWithText("Keep workout").assertDoesNotContainColor(CrimsonRedPrimary)
    }

    private fun showScreen() {
        composeRule.setContent {
            WallCrawlTheme {
                ActiveWorkoutScreen(
                    viewModel = viewModel,
                    visualProvider = NoVisualProvider,
                    onNavigateBack = {},
                    onWorkoutFinished = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun session(): WorkoutSession {
        val workoutExerciseId = "workout-exercise"
        return WorkoutSession(
            id = SESSION_ID,
            name = "Push",
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

    // --- conditions: mobility section, hold, cues, pain offer ---------------------------

    @Test
    fun activeWorkoutShowsTheMobilitySectionSeparately() {
        showMobilityScreen()

        scrollToText(text(R.string.mobility_section_label))
        composeRule.onNodeWithText(text(R.string.mobility_section_label)).assertIsDisplayed()

        // The main exercise that follows is not labelled as mobility.
        composeRule.onNodeWithText(text(R.string.workout_action_next)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(text(R.string.mobility_section_label)).assertCountEquals(0)
    }

    @Test
    fun activeWorkoutShowsHoldTimeAndCues() {
        showMobilityScreen()

        val hold = text(R.string.mobility_hold).format("5")
        val rest = text(R.string.mobility_rest).format("45")
        val cue = text(R.string.condition_cue).format("Stop if symptoms spread.")
        listOf(hold, rest, cue).forEach { shown ->
            scrollToText(shown)
            composeRule.onNodeWithText(shown).assertIsDisplayed()
        }
        // A held position still logs reps through the ordinary set row.
        scrollToDescription("Set 1 complete")
        composeRule.onNodeWithContentDescription("Set 1 complete").assertIsDisplayed()
    }

    @Test
    fun modifyExerciseShowsItsCueInActiveWorkout() {
        showMobilityScreen()
        composeRule.onNodeWithText(text(R.string.workout_action_next)).performClick()
        composeRule.waitForIdle()

        val cue = text(R.string.condition_cue).format("Keep the load light.")
        scrollToText(cue)
        composeRule.onNodeWithText(cue).assertIsDisplayed()
    }

    @Test
    fun aPainStopOffersExclusionAndDecliningChangesNothing() {
        val (vm, profiles) = showMobilityScreen()
        composeRule.runOnIdle {
            vm.skipSet("mobility-set-1", wallcrawl.elopenmike.com.core.model.SetStopReason.PAIN_STOP)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.pain_offer_body)).assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.pain_offer_decline)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(text(R.string.pain_offer_body)).assertCountEquals(0)
        assertThat(runBlocking { profiles.getProfileOnce().excludedExerciseIds }).isEmpty()

        composeRule.runOnIdle {
            vm.skipSet("mobility-set-1", wallcrawl.elopenmike.com.core.model.SetStopReason.PAIN_STOP)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.pain_offer_accept)).performClick()
        composeRule.waitForIdle()
        assertThat(runBlocking { profiles.getProfileOnce().excludedExerciseIds })
            .containsExactly("bird-dog")
    }

    private fun scrollToText(shown: String) {
        composeRule.onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(androidx.compose.ui.test.hasText(shown))
    }

    private fun scrollToDescription(description: String) {
        composeRule.onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(androidx.compose.ui.test.hasContentDescription(description))
    }

    private fun showMobilityScreen(): Pair<ActiveWorkoutViewModel, wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository> {
        val database = androidx.room.Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            wallcrawl.elopenmike.com.core.database.WallCrawlDatabase::class.java
        ).build()
        val profiles = wallcrawl.elopenmike.com.core.database.repository
            .OfflineUserProfileRepository(database.userProfileDao())
        runBlocking {
            profiles.saveProfile(
                UserProfile(reportedConditions = setOf(ConditionAndroidFixtures.CONDITION_ID))
            )
        }
        val guidance = ConditionAndroidFixtures.loaded(
            exerciseDemands = mapOf(
                "bird-dog" to emptySet(),
                "incline-dumbbell-press" to setOf(MovementDemand.HEAVY_EXTERNAL_LOAD)
            ),
            modify = setOf(MovementDemand.HEAVY_EXTERNAL_LOAD),
            exerciseCues = mapOf("bird-dog" to mapOf("en" to "Stop if symptoms spread.")),
            demandCues = mapOf(MovementDemand.HEAVY_EXTERNAL_LOAD to mapOf("en" to "Keep the load light."))
        )
        val vm = ActiveWorkoutViewModel(
            sessionId = SESSION_ID,
            workoutRepository = ScreenTestRepository(mobilitySession()),
            exerciseCatalog = InMemoryExerciseCatalog(),
            workoutHistoryAnalyzer = WorkoutHistoryAnalyzer(),
            nowMillis = { WALL_CLOCK },
            elapsedRealtimeClock = clock,
            userProfileRepository = profiles,
            conditionGuidanceSource = ConditionAndroidFixtures.source(guidance)
        )
        composeRule.setContent {
            WallCrawlTheme {
                ActiveWorkoutScreen(
                    viewModel = vm,
                    visualProvider = NoVisualProvider,
                    onNavigateBack = {},
                    onWorkoutFinished = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) { vm.uiState.value is ActiveWorkoutUiState.Active &&
            (vm.uiState.value as ActiveWorkoutUiState.Active).restrictions !=
            wallcrawl.elopenmike.com.core.conditions.ConditionRestrictions.None }
        composeRule.waitForIdle()
        return vm to profiles
    }

    private fun mobilitySession(): WorkoutSession {
        val mobilityId = "mobility-exercise"
        val mobility = WorkoutExercise(
            id = mobilityId,
            sessionId = SESSION_ID,
            exerciseId = "bird-dog",
            orderIndex = 0,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                targetSets = 1,
                repRange = RepRange(8, 8),
                restSeconds = 45,
                holdSeconds = 5
            ),
            role = wallcrawl.elopenmike.com.core.model.ExerciseRole.MOBILITY,
            sets = listOf(
                WorkoutSet(
                    id = "mobility-set-1",
                    workoutExerciseId = mobilityId,
                    setNumber = 1,
                    targetReps = 8,
                    isCompleted = false
                )
            )
        )
        val base = session()
        return base.copy(exercises = listOf(mobility) + base.exercises.map { it.copy(orderIndex = 1) })
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
