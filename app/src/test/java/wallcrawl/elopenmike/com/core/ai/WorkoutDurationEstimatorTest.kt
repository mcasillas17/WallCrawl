package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange

/**
 * Locks `DURATION_ESTIMATOR_V1`.
 *
 * The numbers here are the arithmetic the planner already used before the estimator was
 * extracted, so this suite is also the regression guard that extracting it changed no
 * generated duration.
 */
class WorkoutDurationEstimatorTest {

    @Test
    fun version_isTheNamedEstimator() {
        assertThat(WorkoutDurationEstimator.VERSION).isEqualTo("DURATION_ESTIMATOR_V1")
    }

    @Test
    fun repetitionWork_usesFortyFiveSecondsPerSetAndRestAfterEverySet() {
        // 3 sets * (45 s work + 90 s rest) = 405 s = 6.75 minutes, truncated to 6.
        val minutes = WorkoutDurationEstimator.estimateMinutes(
            listOf(repetitionExercise(targetSets = 3, restSeconds = 90))
        )

        assertThat(minutes).isEqualTo(6)
    }

    @Test
    fun durationWork_usesItsOwnTargetSeconds() {
        // 2 sets * (60 s hold + 45 s rest) = 210 s = 3.5 minutes, truncated to 3.
        val minutes = WorkoutDurationEstimator.estimateMinutes(
            listOf(
                PlannedExercise(
                    exerciseId = "plank",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.DURATION,
                        targetSets = 2,
                        targetDurationSeconds = 60,
                        restSeconds = 45
                    )
                )
            )
        )

        assertThat(minutes).isEqualTo(3)
    }

    @Test
    fun severalExercises_sumTheirOwnWorkAndRest() {
        val minutes = WorkoutDurationEstimator.estimateMinutes(
            listOf(
                repetitionExercise(targetSets = 3, restSeconds = 90),
                repetitionExercise(targetSets = 4, restSeconds = 120, exerciseId = "row")
            )
        )

        // 405 s + 4 * 165 s = 1065 s = 17.75 minutes, truncated to 17.
        assertThat(minutes).isEqualTo(17)
    }

    @Test
    fun aProposalTooShortToReachOneMinute_stillReportsOneMinute() {
        val minutes = WorkoutDurationEstimator.estimateMinutes(
            listOf(repetitionExercise(targetSets = 1, restSeconds = 0))
        )

        assertThat(minutes).isEqualTo(1)
    }

    @Test
    fun anEmptyProposal_reportsOneMinuteRatherThanZero() {
        assertThat(WorkoutDurationEstimator.estimateMinutes(emptyList())).isEqualTo(1)
    }

    @Test
    fun anImplausiblyLongProposal_isClampedToTheStructuralCeiling() {
        val minutes = WorkoutDurationEstimator.estimateMinutes(
            List(20) { index ->
                repetitionExercise(
                    targetSets = 20,
                    restSeconds = 1_800,
                    exerciseId = "exercise-$index"
                )
            }
        )

        assertThat(minutes).isEqualTo(240)
    }

    private fun repetitionExercise(
        targetSets: Int,
        restSeconds: Int,
        exerciseId: String = "bench-press"
    ): PlannedExercise = PlannedExercise(
        exerciseId = exerciseId,
        prescription = ExercisePrescription(
            exerciseType = ExerciseType.WEIGHT_REPS,
            targetSets = targetSets,
            repRange = RepRange(8, 10),
            restSeconds = restSeconds
        )
    )
}
