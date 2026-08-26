package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSet
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

    @Test
    fun generateWorkout_whenRecentSetsReachTopOfRange_increasesPriorWeight() = runTest {
        val inclinePress = allExercises.single { it.id == "incline-dumbbell-press" }
        val recentSets = (1..3).map { setNumber ->
            WorkoutSet(
                id = "set-$setNumber",
                workoutExerciseId = "incline-instance",
                setNumber = setNumber,
                targetReps = 10,
                completedReps = 10,
                targetWeight = 45.0,
                completedWeight = 45.0,
                isCompleted = true
            )
        }
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(primaryGoal = FitnessGoal.BUILD_MUSCLE),
            exerciseHistory = mapOf(
                inclinePress.id to ExercisePerformanceHistory(
                    exerciseId = inclinePress.id,
                    lastWeight = 45.0,
                    lastReps = 10,
                    bestEstimated1RM = 60.0,
                    recentSets = recentSets
                )
            ),
            allowedExercises = listOf(inclinePress)
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.exercises.single().targetWeight).isEqualTo(50.0)
    }

    @Test
    fun generateWorkout_withUnreviewedCandidate_rejectsInvalidContext() = runTest {
        val unreviewed = allExercises.first().copy(programming = null)
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(),
            allowedExercises = listOf(unreviewed)
        )

        try {
            planner.generateWorkout(context)
            fail("Expected WorkoutValidationException for unreviewed candidate")
        } catch (error: WorkoutValidationException) {
            assertThat(error.message).contains("programming metadata")
        }
    }
}
