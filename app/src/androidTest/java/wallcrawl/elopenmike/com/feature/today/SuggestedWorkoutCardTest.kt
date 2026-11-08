package wallcrawl.elopenmike.com.feature.today

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutEmphasis
import wallcrawl.elopenmike.com.core.model.WorkoutRationaleSpec
import wallcrawl.elopenmike.com.core.model.WorkoutSplit
import wallcrawl.elopenmike.com.core.model.WorkoutTitleSpec
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class SuggestedWorkoutCardTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val regenerate = context.getString(R.string.today_regenerate_content_description)
    private val loading = context.getString(R.string.a11y_loading)

    private fun show(busy: Boolean, controlsBusy: Boolean) = compose.setContent {
        WallCrawlTheme {
            SuggestedWorkoutCard(
                workout = GeneratedWorkout(
                    title = WorkoutTitleSpec(split = WorkoutSplit.FULL_BODY, emphasis = WorkoutEmphasis.HYPERTROPHY),
                    focusMuscles = listOf("Chest"),
                    estimatedDurationMinutes = 45,
                    exercises = emptyList(),
                    rationale = WorkoutRationaleSpec.GoalFocus(goals = emptyList(), focusMuscles = listOf("Chest"))
                ),
                workoutName = "Plan",
                busy = busy,
                controlsBusy = controlsBusy,
                prescriptionUnit = WeightUnit.LBS,
                onStartWorkout = {},
                onRegenerate = {}
            )
        }
    }

    @Test fun readyControlIsNamedAndEnabled() {
        show(busy = false, controlsBusy = false)
        compose.onNodeWithContentDescription(regenerate).assertIsEnabled()
    }

    /** An open session disables it, but nothing is loading, so it keeps its name and does not spin. */
    @Test fun anOpenSessionDisablesItWithoutSayingLoading() {
        show(busy = false, controlsBusy = true)
        compose.onNodeWithContentDescription(regenerate).assertIsNotEnabled()
        compose.onNode(hasContentDescription(loading)).assertDoesNotExist()
    }

    @Test fun aBusyScreenSaysLoading() {
        show(busy = true, controlsBusy = true)
        compose.onNode(hasContentDescription(loading)).assertExists()
    }
}
