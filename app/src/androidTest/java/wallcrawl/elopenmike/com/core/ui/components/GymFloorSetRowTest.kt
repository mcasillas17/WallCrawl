package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
    fun completionControl_isALargeToggleWithSpokenState() {
        var completed by mutableStateOf(false)
        composeRule.setContent {
            WallCrawlTheme {
                GymFloorSetRow(
                    set = weightRepsSet(isCompleted = completed),
                    weightUnit = "kg",
                    previousSet = null,
                    onValuesChanged = {},
                    onCompletionChanged = { _, isCompleted -> completed = isCompleted },
                    onSkipSet = {},
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
            }
        }

        val toggle = composeRule.onNodeWithContentDescription("Set 1 complete")
        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        toggle.assertHeightIsAtLeast(48.dp)
        toggle.assertIsOff()

        toggle.performClick()
        composeRule.waitForIdle()

        assertThat(completed).isTrue()
        composeRule.onNodeWithContentDescription("Set 1 complete").assertIsOn()
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
                    onValuesChanged = { values += it },
                    onCompletionChanged = { _, _ -> },
                    onSkipSet = {},
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
            }
        }

        val increaseReps = composeRule.onNodeWithContentDescription("Increase reps for set 1")
        increaseReps.assertHeightIsAtLeast(48.dp)
        increaseReps.assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Decrease load for set 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Load kg for set 1").assertIsDisplayed()

        increaseReps.performClick()
        composeRule.waitForIdle()

        assertThat(values.last().reps).isEqualTo(9)
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
                    onValuesChanged = { values += it },
                    onCompletionChanged = { _, _ -> },
                    onSkipSet = {},
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
            }
        }

        composeRule.onAllNodes(
            hasContentDescriptionStartingWith("Use previous")
        ).assertCountEquals(0)

        previous = weightRepsSet(isCompleted = true).copy(
            id = "previous",
            completedWeight = 47.5,
            completedReps = 12
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Use previous load 47.5 for set 1").performClick()
        composeRule.waitForIdle()

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
                    onValuesChanged = {},
                    onCompletionChanged = { _, isCompleted -> completed = isCompleted },
                    onSkipSet = {},
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

        composeRule.onNodeWithContentDescription("Set 1 complete").performClick()
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
                    onValuesChanged = {},
                    onCompletionChanged = { _, _ -> },
                    onSkipSet = { reasons += it },
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
                    onValuesChanged = {},
                    onCompletionChanged = { _, _ -> },
                    onSkipSet = {},
                    onRecordEffort = { rpe, rir -> effort += rpe to rir },
                    onRecordFeltManageable = {}
                )
            }
        }

        // Completion is already recorded before any effort control is even visible.
        composeRule.onNodeWithContentDescription("Set 1 complete").assertIsOn()

        composeRule.onNodeWithText("Add feedback (optional)").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("RIR 2 for set 1").performClick()
        composeRule.waitForIdle()

        assertThat(effort.last().second).isEqualTo(2)
        assertThat(effort.last().first).isNull()
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
                    onValuesChanged = {},
                    onCompletionChanged = { _, _ -> },
                    onSkipSet = {},
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
