package wallcrawl.elopenmike.com.feature.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
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
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/**
 * Drives the assembled active-workout screen: the rest countdown, its explicit controls,
 * and the confirmations guarding finish and discard.
 */
class ActiveWorkoutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

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
    fun completingASet_showsTheRestCountdownWithItsExplicitControls() {
        showScreen()

        composeRule.onNodeWithContentDescription("Set 1 complete").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Rest remaining 2:00").assertIsDisplayed()

        composeRule.onNodeWithText("+30s").performClick()
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
        composeRule.onNodeWithContentDescription("Set 1 complete").performClick()
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

        composeRule.onNodeWithContentDescription("Set 1 complete").performClick()
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

        composeRule.onNodeWithContentDescription("Close Workout").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Discard this workout?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep workout").performClick()
        composeRule.waitForIdle()
        assertThat(repository.cancelCalls).isEqualTo(0)

        composeRule.onNodeWithContentDescription("Close Workout").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.waitForIdle()

        assertThat(repository.cancelCalls).isEqualTo(1)
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

    private fun hasTextStartingWith(prefix: String) = SemanticsMatcher(
        "text starts with '$prefix'"
    ) { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith(prefix) } == true
    }

    private companion object {
        const val SESSION_ID = "session"
        const val WALL_CLOCK = 1_777_777L
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
    private val session = MutableStateFlow<WorkoutSession?>(initial)
    var completeCalls: Int = 0
        private set
    var cancelCalls: Int = 0
        private set

    override fun observeActiveSession(): Flow<WorkoutSession?> = session
    override suspend fun getActiveSessionOnce(): WorkoutSession? = session.value
    override suspend fun getSessionById(sessionId: String): WorkoutSession? = session.value
    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = session
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> =
        flowOf(emptyList())

    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(0)
    override fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int> = flowOf(0)
    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> = emptyList()

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        userProfile: UserProfile
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
