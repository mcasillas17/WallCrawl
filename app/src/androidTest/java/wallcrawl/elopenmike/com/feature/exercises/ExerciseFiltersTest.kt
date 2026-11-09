package wallcrawl.elopenmike.com.feature.exercises

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class ExerciseFiltersTest {
    @get:Rule val compose = createComposeRule()

    @Test fun closingAndClearingFiltersKeepsTheSearch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exercises = InMemoryExerciseCatalog.SAMPLE_EXERCISES.take(2)
        val vm = ExercisesViewModel(InMemoryExerciseCatalog(exercises))
        val visuals = object : ExerciseVisualProvider {
            override fun framesFor(exerciseId: String): List<ExerciseVisual> = emptyList()
        }
        compose.setContent { WallCrawlTheme { ExercisesScreen(vm, visuals) } }
        compose.waitUntil { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("Incline")
        compose.onNodeWithText(context.getString(R.string.guided_filters)).performClick()
        compose.onNodeWithText(StandardMuscles.CHEST).performClick()
        compose.onNodeWithText(StandardEquipment.BARBELL).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.guided_show_results)).performClick()
        compose.onNodeWithText(context.getString(R.string.exercises_empty)).assertIsDisplayed()
        val applied = context.getString(R.string.guided_filter_count, "2")
        compose.onNodeWithText(applied).assertIsDisplayed()
        compose.onNodeWithText("Chest · Barbell").assertIsDisplayed()
        compose.onNodeWithText(applied).performClick()
        compose.onNodeWithText(context.getString(R.string.guided_clear_filters)).performClick()
        compose.onNodeWithText(context.getString(R.string.guided_show_results)).performClick()
        compose.onNodeWithText(exercises.first().name).assertIsDisplayed()
        compose.onNodeWithText(exercises.last().name).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextContains("Incline")
    }

    /** Each filter group keeps one value, so its chips are radios in a group, not checkboxes. */
    @Test fun filterChipsAreRadiosInAGroup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = ExercisesViewModel(InMemoryExerciseCatalog(InMemoryExerciseCatalog.SAMPLE_EXERCISES.take(2)))
        val visuals = object : ExerciseVisualProvider {
            override fun framesFor(exerciseId: String): List<ExerciseVisual> = emptyList()
        }
        compose.setContent { WallCrawlTheme { ExercisesScreen(vm, visuals) } }
        compose.waitUntil { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(context.getString(R.string.guided_filters)).performClick()
        val chest = compose.onNode(hasText(StandardMuscles.CHEST) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        chest.assert(hasParent(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))).assertIsNotSelected()
        chest.performClick().assertIsSelected()
        compose.onNode(hasText(StandardEquipment.BARBELL) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assert(hasParent(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)))
    }
}
