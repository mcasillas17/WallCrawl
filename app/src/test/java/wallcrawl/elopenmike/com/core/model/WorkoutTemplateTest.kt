package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkoutTemplateTest {

    @Test
    fun template_preservesExerciseOrderAndTypeSpecificTargets() {
        val template = WorkoutTemplate(
            id = "template",
            name = "Conditioning",
            notes = "Ten minute finisher",
            createdAtTimestamp = 1_000L,
            updatedAtTimestamp = 2_000L,
            exercises = listOf(
                PlannedExercise(
                    exerciseId = "plank",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.DURATION,
                        targetSets = 3,
                        targetDurationSeconds = 45
                    )
                ),
                PlannedExercise(
                    exerciseId = "walking",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.DISTANCE_DURATION,
                        targetSets = 1,
                        targetDistanceMeters = 1_000.0,
                        restSeconds = 0
                    )
                )
            )
        )

        assertThat(template.exercises.map { it.exerciseId })
            .containsExactly("plank", "walking")
            .inOrder()
    }

    @Test
    fun template_rejectsBlankNameEmptyExercisesAndBackwardsTimestamps() {
        val exercise = PlannedExercise(
            exerciseId = "push-up",
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12)
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            WorkoutTemplate(name = " ", exercises = listOf(exercise))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutTemplate(name = "Push", exercises = emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutTemplate(
                name = "Push",
                createdAtTimestamp = 2_000L,
                updatedAtTimestamp = 1_000L,
                exercises = listOf(exercise)
            )
        }
    }
}
