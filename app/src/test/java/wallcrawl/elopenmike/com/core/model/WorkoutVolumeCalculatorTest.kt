package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkoutVolumeCalculatorTest {

    @Test
    fun workoutSession_calculatesTotalVolumeAndCompletedSetsCorrectly() {
        val set1 = WorkoutSet(
            workoutExerciseId = "ex1",
            setNumber = 1,
            targetReps = 10,
            completedReps = 10,
            targetWeight = 50.0,
            completedWeight = 50.0,
            isCompleted = true
        )
        val set2 = WorkoutSet(
            workoutExerciseId = "ex1",
            setNumber = 2,
            targetReps = 10,
            completedReps = 8,
            targetWeight = 50.0,
            completedWeight = 50.0,
            isCompleted = true
        )
        val set3 = WorkoutSet(
            workoutExerciseId = "ex1",
            setNumber = 3,
            targetReps = 10,
            completedReps = null,
            targetWeight = 50.0,
            completedWeight = null,
            isCompleted = false // Incomplete
        )

        val exercise1 = WorkoutExercise(
            id = "ex1",
            sessionId = "session1",
            exerciseId = "incline-dumbbell-press",
            orderIndex = 0,
            targetSets = 3,
            targetRepMin = 8,
            targetRepMax = 10,
            sets = listOf(set1, set2, set3)
        )

        val set4 = WorkoutSet(
            workoutExerciseId = "ex2",
            setNumber = 1,
            targetReps = 12,
            completedReps = 12,
            targetWeight = 20.0,
            completedWeight = 20.0,
            isCompleted = true
        )

        val exercise2 = WorkoutExercise(
            id = "ex2",
            sessionId = "session1",
            exerciseId = "dumbbell-lateral-raise",
            orderIndex = 1,
            targetSets = 1,
            targetRepMin = 12,
            targetRepMax = 15,
            sets = listOf(set4)
        )

        val session = WorkoutSession(
            id = "session1",
            name = "Push Day",
            exercises = listOf(exercise1, exercise2)
        )

        // Exercise 1: (50 * 10) + (50 * 8) = 500 + 400 = 900
        assertThat(exercise1.totalVolume).isEqualTo(900.0)

        // Exercise 2: (20 * 12) = 240
        assertThat(exercise2.totalVolume).isEqualTo(240.0)

        // Session total: 900 + 240 = 1140
        assertThat(session.totalVolume).isEqualTo(1140.0)

        // Completed sets: 2 in ex1 + 1 in ex2 = 3
        assertThat(session.completedSetsCount).isEqualTo(3)
        assertThat(session.totalSetsCount).isEqualTo(4)
    }
}
