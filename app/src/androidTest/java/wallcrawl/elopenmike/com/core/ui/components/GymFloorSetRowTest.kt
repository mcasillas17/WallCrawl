package wallcrawl.elopenmike.com.core.ui.components

import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import wallcrawl.elopenmike.com.R
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetValuesDraft
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class GymFloorSetRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loggingChangesTheActionToSaveChangesAndOffersExplicitUndo() {
        var completed by mutableStateOf(false)
        composeRule.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = weightRepsSet(isCompleted = completed),
                    weightUnit = "kg",
                    previousSet = null,
                    onCompletionChanged = { _, isCompleted -> completed = isCompleted },
                    onSkipSet = { _, _ -> },
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
            }
        }

        composeRule.onNodeWithText("Log set").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Save changes").assertIsDisplayed()
        assertThat(completed).isTrue()
        composeRule.onNodeWithText("Mark unlogged").performClick()
        assertThat(completed).isFalse()
    }

    @Test
    fun numericControls_haveLabelledStepButtonsWithUsableTouchTargets() {
        val values = mutableListOf<SetValuesDraft>()
        composeRule.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = weightRepsSet(),
                    weightUnit = "kg",
                    previousSet = null,
                    onCompletionChanged = { draft, _ -> values += draft },
                    onSkipSet = { _, _ -> },
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
            }
        }

        val increaseReps = composeRule.onNodeWithContentDescription("Increase Reps for set 1")
        increaseReps.assertHeightIsAtLeast(48.dp)
        increaseReps.assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Decrease Load for set 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Load (kg) for set 1").assertIsDisplayed()

        increaseReps.performClick()
        composeRule.waitForIdle()

        assertThat(values).isEmpty()
        composeRule.onNodeWithText("Log set").performClick()
        assertThat(values.last().reps).isEqualTo(9)
    }

    @Test
    fun fineAdjustedLoadRestoresAndIsCommittedOnlyWhenSaved() {
        val committed = mutableListOf<SetValuesDraft>()
        val restore = StateRestorationTester(composeRule)
        restore.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = weightRepsSet(isCompleted = true), weightUnit = "kg", previousSet = null,
                    onCompletionChanged = { values, _ -> committed += values },
                    onSkipSet = { _, _ -> }, onRecordEffort = { _, _ -> }, onRecordFeltManageable = {}
                )
            }
        }
        val save = composeRule.onNodeWithText("Save changes")
        val initialBounds = save.getUnclippedBoundsInRoot()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf("2.5", "1", "0.1").forEach { step ->
            composeRule.onNodeWithContentDescription(context.getString(R.string.guided_change_step, "Load (kg)", step))
                .performClick()
        }
        composeRule.onNodeWithContentDescription("Increase Load for set 1").performClick()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        assertThat(save.getUnclippedBoundsInRoot()).isEqualTo(initialBounds)
        restore.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("40.01").assertIsDisplayed()
        assertThat(committed).isEmpty()
        composeRule.onNodeWithText("Save changes").performClick()
        assertThat(committed.single().weight).isEqualTo(40.01)
    }

    @Test
    fun holdingChangesRepsUntilReleaseAndDraggingScrollsWithoutEditing() {
        val scroll = ScrollState(0)
        val committed = mutableListOf<SetValuesDraft>()
        var longPressMillis = 0L
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            longPressMillis = LocalViewConfiguration.current.longPressTimeoutMillis
            WallCrawlTheme {
                Column(Modifier.verticalScroll(scroll)) {
                    GymFloorSetRow(weightRepsSet(), "kg", null, { values, _ -> committed += values },
                        { _, _ -> }, { _, _ -> }, {})
                    Spacer(Modifier.height(1000.dp))
                }
            }
        }
        val value = composeRule.onNodeWithContentDescription("Reps for set 1")
        val plus = composeRule.onNodeWithContentDescription("Increase Reps for set 1")
        val minus = composeRule.onNodeWithContentDescription("Decrease Reps for set 1")
        fun displayedReps() = value.fetchSemanticsNode().config[SemanticsProperties.Text].single().text.toInt()
        value.assertHasNoClickAction().performTouchInput { click() }
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.runOnIdle {
            assertThat(checkNotNull(ViewCompat.getRootWindowInsets(view)).isVisible(WindowInsetsCompat.Type.ime())).isFalse()
        }

        plus.performTouchInput { click() }
        assertThat(displayedReps()).isEqualTo(9)
        composeRule.mainClock.autoAdvance = false
        try {
            plus.performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(longPressMillis + 310L)
            val heldReps = displayedReps()
            assertThat(heldReps).isAtLeast(12)
            plus.performTouchInput { up() }
            composeRule.mainClock.advanceTimeBy(400L)
            assertThat(displayedReps()).isEqualTo(heldReps)

            minus.performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(longPressMillis + 200L)
            val reducedReps = displayedReps()
            assertThat(reducedReps).isLessThan(heldReps - 1)
            minus.performTouchInput { cancel() }
            composeRule.mainClock.advanceTimeBy(400L)
            assertThat(displayedReps()).isEqualTo(reducedReps)

            plus.performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(longPressMillis + 200L)
            val repsBeforeLeavingButton = displayedReps()
            assertThat(repsBeforeLeavingButton).isGreaterThan(reducedReps)
            plus.performTouchInput { moveBy(Offset(-width.toFloat() * 2, 0f)) }
            composeRule.mainClock.advanceTimeBy(400L)
            plus.performTouchInput { up() }
            composeRule.mainClock.advanceTimeByFrame()
            assertThat(displayedReps()).isEqualTo(repsBeforeLeavingButton)

            plus.performTouchInput {
                down(center)
                moveBy(Offset(0f, -35.dp.toPx()))
                moveBy(Offset(0f, -35.dp.toPx()))
                up()
            }
            composeRule.mainClock.advanceTimeBy(longPressMillis + 400L)
            assertThat(scroll.value).isGreaterThan(0)
            assertThat(displayedReps()).isEqualTo(repsBeforeLeavingButton)
            assertThat(committed).isEmpty()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun copyPreviousValue_appearsOnlyWhenAComparableCompletedValueExists() {
        var previous by mutableStateOf<WorkoutSet?>(null)
        val values = mutableListOf<SetValuesDraft>()
        composeRule.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = weightRepsSet(),
                    weightUnit = "kg",
                    previousSet = previous,
                    onCompletionChanged = { draft, _ -> values += draft },
                    onSkipSet = { _, _ -> },
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
            }
        }

        composeRule.onAllNodes(
            hasContentDescriptionStartingWith("Use the previous")
        ).assertCountEquals(0)

        previous = weightRepsSet(isCompleted = true).copy(
            id = "previous",
            completedWeight = 47.5,
            completedReps = 12
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Use the previous Load of 47.5 for set 1").performClick()
        composeRule.waitForIdle()

        assertThat(values).isEmpty()
        composeRule.onNodeWithText("Log set").performClick()
        assertThat(values.last().weight).isEqualTo(47.5)
    }

    @Test
    fun manageableConfirmation_isOfferedOnlyAfterCompletionAndReportsItsSelection() {
        val answers = mutableListOf<Boolean>()
        var completed by mutableStateOf(false)
        var recordedManageable by mutableStateOf<Boolean?>(null)
        composeRule.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = weightRepsSet(isCompleted = completed).copy(
                        feltManageable = recordedManageable
                    ),
                    weightUnit = "kg",
                    previousSet = null,
                    onCompletionChanged = { _, isCompleted -> completed = isCompleted },
                    onSkipSet = { _, _ -> },
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {
                        answers += it
                        recordedManageable = it
                    }
                )
            }
        }

        composeRule.onAllNodes(
            hasContentDescriptionStartingWith("Set 1 felt manageable")
        ).assertCountEquals(0)

        composeRule.onNodeWithText("Log set").performClick()
        composeRule.onNodeWithText("Add feedback (optional)").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Set 1 felt manageable, Yes").performClick()
        composeRule.waitForIdle()

        assertThat(answers).containsExactly(true)
        composeRule.onNodeWithContentDescription("Set 1 felt manageable, Yes").assertIsSelected()
    }

    @Test
    fun stopReasons_arePlainlyWordedAndReportTheTypedChoice() {
        val reasons = mutableListOf<SetStopReason>()
        composeRule.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = weightRepsSet(),
                    weightUnit = "kg",
                    previousSet = null,
                    onCompletionChanged = { _, _ -> },
                    onSkipSet = { reason, _ -> reasons += reason },
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
            }
        }

        composeRule.onNodeWithText("Skip or stop").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Something hurt, so I stopped").performClick()
        composeRule.waitForIdle()

        assertThat(reasons).containsExactly(SetStopReason.PAIN_STOP)
    }

    @Test
    fun stopReasonDialog_doesNotStyleCancelAsDestructive() {
        showWeightRepsRow()

        composeRule.onNodeWithText("Skip or stop").performClick()
        composeRule.waitForIdle()

        assertThat(composeRule.onNodeWithText("Cancel").closestColorDistance(CrimsonRedPrimary))
            .isGreaterThan(COLOR_MATCH_TOLERANCE)
    }

    @Test
    fun effortControls_areOptionalAndNeverRequiredToCompleteASet() {
        val effort = mutableListOf<Pair<Float?, Int?>>()
        composeRule.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = weightRepsSet(isCompleted = true),
                    weightUnit = "kg",
                    previousSet = null,
                    onCompletionChanged = { _, _ -> },
                    onSkipSet = { _, _ -> },
                    onRecordEffort = { rpe, rir -> effort += rpe to rir },
                    onRecordFeltManageable = {}
                )
            }
        }

        // Completion is already recorded before any effort control is even visible.
        composeRule.onNodeWithText("Save changes").assertIsDisplayed()

        composeRule.onNodeWithText("Add feedback (optional)").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("RIR 2 for set 1").performClick()
        composeRule.waitForIdle()

        assertThat(effort.last().second).isEqualTo(2)
        assertThat(effort.last().first).isNull()
    }

    @Test
    fun steppersStopAtTheBoundsAndLogOnlyApplicableFields() {
        val base = weightRepsSet().copy(targetWeight = null, targetReps = null)
        var set by mutableStateOf(base.copy(exerciseType = ExerciseType.BODYWEIGHT_REPS, targetReps = 999))
        val committed = mutableListOf<SetValuesDraft>()
        var longPressMillis = 0L
        composeRule.setContent {
            longPressMillis = LocalViewConfiguration.current.longPressTimeoutMillis
            WallCrawlTheme {
                GymFloorSetRow(set, "kg", null, { values, _ -> committed += values },
                    { _, _ -> }, { _, _ -> }, {})
            }
        }
        val increaseReps = composeRule.onNodeWithContentDescription("Increase Reps for set 1")
        increaseReps.performTouchInput { click() }
        increaseReps.performTouchInput { click() }
        composeRule.onNodeWithText("Log set").performClick()

        set = base.copy(id = "assist", exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
            targetReps = 7, targetAssistanceWeight = 5.0)
        val decreaseAssist = composeRule.onNodeWithContentDescription("Decrease Assist for set 1")
        decreaseAssist.assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        try {
            decreaseAssist.performTouchInput { down(center) }
            composeRule.mainClock.advanceTimeBy(longPressMillis + 500L)
            composeRule.onNodeWithText("0").assertIsDisplayed()
            decreaseAssist.performTouchInput { up() }
            composeRule.mainClock.advanceTimeBy(200L)
            composeRule.onNodeWithText("0").assertIsDisplayed()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.onNodeWithText("Log set").performClick()
        decreaseAssist.performTouchInput { click() }
        composeRule.onNodeWithText("Log set").performClick()

        set = base.copy(id = "time", exerciseType = ExerciseType.DURATION, targetDurationSeconds = 12)
        composeRule.onNodeWithContentDescription("Increase Seconds for set 1").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(context.getString(R.string.guided_change_step, "Seconds", "5")).performClick()
        composeRule.onNodeWithContentDescription("Decrease Seconds for set 1").performClick()
        composeRule.onNodeWithText("Log set").performClick()

        set = base.copy(id = "distance", exerciseType = ExerciseType.DISTANCE_DURATION, targetDistanceMeters = 101.123456789)
        listOf("50", "1", "0.1").forEach { step ->
            composeRule.onNodeWithContentDescription(context.getString(R.string.guided_change_step, "Meters", step)).performClick()
        }
        composeRule.onNodeWithContentDescription("Increase Meters for set 1").performClick()
        composeRule.onNodeWithText("Log set").performClick()
        assertThat(committed).containsExactly(
            SetValuesDraft(reps = 1000),
            SetValuesDraft(reps = 7, assistanceWeight = 0.0),
            SetValuesDraft(reps = 7),
            SetValuesDraft(durationSeconds = 16),
            SetValuesDraft(distanceMeters = 101.133456789)
        ).inOrder()
    }

    private fun weightRepsSet(isCompleted: Boolean = false) = WorkoutSet(
        id = "set-1",
        workoutExerciseId = "exercise",
        setNumber = 1,
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetReps = 8,
        targetWeight = 40.0,
        completedReps = if (isCompleted) 8 else null,
        completedWeight = if (isCompleted) 40.0 else null,
        completedAtTimestamp = if (isCompleted) 1_777_777L else null,
        isCompleted = isCompleted
    )

    private fun showWeightRepsRow() {
        composeRule.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = weightRepsSet(),
                    weightUnit = "kg",
                    previousSet = null,
                    onCompletionChanged = { _, _ -> },
                    onSkipSet = { _, _ -> },
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
            }
        }
        composeRule.waitForIdle()
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

    private fun hasContentDescriptionStartingWith(prefix: String) = SemanticsMatcher(
        "content description starts with '$prefix'"
    ) { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.any { it.startsWith(prefix) } == true
    }

    private companion object {
        const val COLOR_MATCH_TOLERANCE = 0.01f
    }
}
