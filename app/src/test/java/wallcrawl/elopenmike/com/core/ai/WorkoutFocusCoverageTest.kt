package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSplit

/**
 * The advertised split has to be one the selected exercises actually train.
 *
 * This replays the committed band-only reproduction from
 * `docs/reviewed-catalog-coverage.md` through the real bundled catalog, the real
 * `ExerciseFilter`, the real `FakeWorkoutPlanner` and the real `ProgramValidator`. It
 * asserts through the shipped [trainsAsFocus] contract rather than a classifier written
 * for the test, so the test and the planner cannot drift apart.
 */
class WorkoutFocusCoverageTest {

    private val factory = PlannerFixtureContextFactory()

    @Test
    fun bandOnlyChestPriority_advertisesASplitItsSelectionActuallyTrains() = runTest {
        val context = bandOnlyContext()
        val workout = FakeWorkoutPlanner().generateWorkout(context)
        val selected = context.resolve(workout.exercises.map { it.exerciseId })

        assertWithMessage(
            "advertised ${workout.title.split} with ${selected.map(Exercise::id)}"
        ).that(selected.any { workout.title.split.trainsAsFocus(it) }).isTrue()
    }

    /**
     * The committed reproduction: BEGINNER, BUILD_MUSCLE, 40 minutes, four days a week,
     * Resistance Band only, Chest HIGH, every movement capability COMFORTABLE, no
     * constraints, exclusions, confirmed loads or history, and no declared required
     * pattern. Bodyweight is never added to the explicitly band-only inventory.
     */
    private fun bandOnlyContext(): WorkoutGenerationContext {
        val source = PlannerFixtureLoader().loadResource("planner-fixtures/band-only.json")
        val fixture = source.copy(
            profile = source.profile.copy(
                availableEquipment = listOf(StandardEquipment.RESISTANCE_BAND),
                musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH),
                movementCapabilities = MovementCapabilities.from(
                    MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
                )
            ),
            completedWorkoutCount = 0,
            exerciseHistory = emptyList(),
            allowedExerciseIds = emptyList(),
            reviewedEligibility = null,
            expected = PlannerFixtureExpected(
                outcome = PlannerFixtureOutcome.SUCCESS,
                requiredExerciseIds = emptySet(),
                forbiddenExerciseIds = emptySet()
            )
        )
        val built = factory.create(fixture)
        assertThat(built.context.automaticEligibilityResult).isNull()
        assertThat(built.context.availableEquipment)
            .containsExactly(StandardEquipment.RESISTANCE_BAND)
        assertThat(built.context.allowedExercises).isNotEmpty()
        return built.context
    }

    private fun WorkoutGenerationContext.resolve(ids: List<String>): List<Exercise> {
        val byId = allowedExercises.associateBy(Exercise::id)
        return ids.map { byId.getValue(it) }
    }
}
