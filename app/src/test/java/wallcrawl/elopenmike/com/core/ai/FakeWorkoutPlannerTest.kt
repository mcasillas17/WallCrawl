package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FakeWorkoutPlannerTest {

    private lateinit var planner: FakeWorkoutPlanner
    private val allExercises = InMemoryExerciseCatalog.SAMPLE_EXERCISES

    @Before
    fun setup() {
        planner = FakeWorkoutPlanner()
    }

    @Test
    fun generateWorkout_onlySelectsAllowedExerciseCandidates() = runTest {
        val allowedSubset = allExercises.filter {
            it.id in listOf("incline-dumbbell-press", "parallel-bar-dips", "dumbbell-lateral-raise")
        }

        val context = WorkoutGenerationContext(
            userProfile = UserProfile(primaryGoal = FitnessGoal.BUILD_MUSCLE),
            allowedExercises = allowedSubset
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.exercises).isNotEmpty()
        val allowedIds = allowedSubset.map { it.id }.toSet()
        assertThat(workout.exercises.all { it.exerciseId in allowedIds }).isTrue()
    }

    @Test
    fun generateWorkout_withChestPriority_generatesPushRoutine() = runTest {
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(
                primaryGoal = FitnessGoal.BUILD_MUSCLE,
                musclePriorities = mapOf(
                    StandardMuscles.CHEST to PriorityLevel.HIGH,
                    StandardMuscles.SHOULDERS to PriorityLevel.HIGH
                )
            ),
            allowedExercises = allExercises
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.name).contains("Push")
        assertThat(workout.focusMuscles).contains(StandardMuscles.CHEST)
    }

    @Test
    fun generateWorkout_withEmptyAllowedCandidates_throwsException() = runTest {
        val emptyContext = WorkoutGenerationContext(
            userProfile = UserProfile(),
            allowedExercises = emptyList()
        )

        try {
            planner.generateWorkout(emptyContext)
            fail("Expected WorkoutValidationException for empty allowed candidates")
        } catch (e: WorkoutValidationException) {
            assertThat(e.message).contains("no allowed candidate exercises")
        }
    }
}
