package wallcrawl.elopenmike.com.core.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput

class SetRowTest {

    @Test
    fun weightInputLabel_withNullTarget_asksUserToChooseStartingLoad() {
        assertThat(weightInputLabel(targetWeight = null, weightUnit = "kg"))
            .isEqualTo("Choose starting load")
    }

    @Test
    fun weightInputLabel_withKnownTarget_showsLoadAndUnit() {
        assertThat(weightInputLabel(targetWeight = 40.0, weightUnit = "kg"))
            .isEqualTo("Load kg")
    }

    @Test
    fun isSubmittableFor_incompleteSet_isAlwaysSubmittableRegardlessOfPartialData() {
        // Partial progress toward an incomplete set is always safe to persist -- the
        // repository already allows a partial edit as long as isCompleted stays false.
        val performance = SetPerformanceInput(reps = null, weight = null, isCompleted = false)

        assertThat(performance.isSubmittableFor(ExerciseType.WEIGHT_REPS)).isTrue()
    }

    @Test
    fun isSubmittableFor_completedWeightRepsWithNullWeight_isNotSubmittable() {
        // Reproduces clearing the load field on an already-completed set to retype it:
        // this transient value must not be sent to the repository as a completion.
        val performance = SetPerformanceInput(reps = 10, weight = null, isCompleted = true)

        assertThat(performance.isSubmittableFor(ExerciseType.WEIGHT_REPS)).isFalse()
    }

    @Test
    fun isSubmittableFor_completedWeightRepsWithZeroWeight_isNotSubmittable() {
        val performance = SetPerformanceInput(reps = 10, weight = 0.0, isCompleted = true)

        assertThat(performance.isSubmittableFor(ExerciseType.WEIGHT_REPS)).isFalse()
    }

    @Test
    fun isSubmittableFor_completedWeightRepsWithNullOrZeroReps_isNotSubmittable() {
        // Reproduces the same latent problem for the reps field.
        assertThat(
            SetPerformanceInput(reps = null, weight = 20.0, isCompleted = true)
                .isSubmittableFor(ExerciseType.WEIGHT_REPS)
        ).isFalse()
        assertThat(
            SetPerformanceInput(reps = 0, weight = 20.0, isCompleted = true)
                .isSubmittableFor(ExerciseType.WEIGHT_REPS)
        ).isFalse()
    }

    @Test
    fun isSubmittableFor_completedWeightRepsWithValidData_isSubmittable() {
        val performance = SetPerformanceInput(reps = 10, weight = 20.0, isCompleted = true)

        assertThat(performance.isSubmittableFor(ExerciseType.WEIGHT_REPS)).isTrue()
    }

    @Test
    fun isSubmittableFor_completedBodyweightRepsRequiresOnlyPositiveReps() {
        assertThat(
            SetPerformanceInput(reps = 10, isCompleted = true)
                .isSubmittableFor(ExerciseType.BODYWEIGHT_REPS)
        ).isTrue()
        assertThat(
            SetPerformanceInput(reps = null, isCompleted = true)
                .isSubmittableFor(ExerciseType.BODYWEIGHT_REPS)
        ).isFalse()
    }

    @Test
    fun isSubmittableFor_completedDurationSetRequiresPositiveDuration() {
        assertThat(
            SetPerformanceInput(durationSeconds = 45, isCompleted = true)
                .isSubmittableFor(ExerciseType.DURATION)
        ).isTrue()
        assertThat(
            SetPerformanceInput(durationSeconds = null, isCompleted = true)
                .isSubmittableFor(ExerciseType.DURATION)
        ).isFalse()
    }

    @Test
    fun isSubmittableFor_completedDistanceDurationSetAcceptsEitherDistanceOrDuration() {
        assertThat(
            SetPerformanceInput(distanceMeters = 100.0, isCompleted = true)
                .isSubmittableFor(ExerciseType.DISTANCE_DURATION)
        ).isTrue()
        assertThat(
            SetPerformanceInput(durationSeconds = 60, isCompleted = true)
                .isSubmittableFor(ExerciseType.DISTANCE_DURATION)
        ).isTrue()
        assertThat(
            SetPerformanceInput(isCompleted = true)
                .isSubmittableFor(ExerciseType.DISTANCE_DURATION)
        ).isFalse()
    }
}
