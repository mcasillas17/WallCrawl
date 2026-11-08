package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.google.common.truth.Truth.assertThat
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
    fun restoringAnUnfinishedDecimalKeepsTheDraftAndDisablesSaveUntilComplete() {
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
        composeRule.onNodeWithContentDescription("Load (kg) for set 1").performClick()
        listOf("4", "7", ".").forEach { composeRule.onNodeWithText(it).performClick() }
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        restore.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("47.").assertIsDisplayed()
        composeRule.onNodeWithText("Enter a complete number").assertIsDisplayed()
        composeRule.onNodeWithText("Save changes").assertIsNotEnabled()
        composeRule.onNodeWithText("5").performClick()
        assertThat(committed).isEmpty()
        composeRule.onNodeWithText("Save changes").performClick()
        assertThat(committed.single().weight).isEqualTo(47.5)
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
    fun exactEntryRejectsAnOutOfBoundsCountAndLogsOnlyApplicableFields() {
        var set by mutableStateOf(weightRepsSet().copy(exerciseType = ExerciseType.BODYWEIGHT_REPS, targetWeight = null))
        val committed = mutableListOf<SetValuesDraft>()
        composeRule.setContent {
            WallCrawlTheme {
                GymFloorSetRow(set, "kg", null, { values, _ -> committed += values },
                    { _, _ -> }, { _, _ -> }, {})
            }
        }
        composeRule.onNodeWithContentDescription("Reps for set 1").performClick()
        listOf("1", "0", "0", "1").forEach { composeRule.onNodeWithText(it).performClick() }
        composeRule.onNodeWithText(".").assertDoesNotExist()
        composeRule.onNodeWithText("Log set").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Delete last digit").performClick()
        composeRule.onNodeWithText("Log set").performClick()
        val base = weightRepsSet().copy(targetWeight = null, targetReps = null)
        set = base.copy(id = "assist", exerciseType = ExerciseType.ASSISTED_BODYWEIGHT, targetReps = 7)
        composeRule.onNodeWithText("Log set").performClick()
        set = base.copy(id = "time", exerciseType = ExerciseType.DURATION, targetDurationSeconds = 12)
        composeRule.onNodeWithText("Log set").performClick()
        set = base.copy(id = "distance", exerciseType = ExerciseType.DISTANCE_DURATION, targetDistanceMeters = 101.125)
        composeRule.onNodeWithText("Log set").performClick()
        assertThat(committed).containsExactly(SetValuesDraft(reps = 100), SetValuesDraft(reps = 7),
            SetValuesDraft(durationSeconds = 12), SetValuesDraft(distanceMeters = 101.125)).inOrder()
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
