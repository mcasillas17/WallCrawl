package wallcrawl.elopenmike.com.core.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.WorkoutSet

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

    @Test
    fun stepSize_matchesWhatTheFieldIsActuallyAdjustedByInAGym() {
        assertThat(SetInputField.REPS.stepSize).isEqualTo(1.0)
        assertThat(SetInputField.LOAD.stepSize).isEqualTo(2.5)
        assertThat(SetInputField.ASSISTANCE.stepSize).isEqualTo(2.5)
        assertThat(SetInputField.DURATION.stepSize).isEqualTo(5.0)
        assertThat(SetInputField.DISTANCE.stepSize).isEqualTo(50.0)
    }

    @Test
    fun stepping_movesByOneStepAndNeverBelowZero() {
        assertThat(SetInputField.REPS.stepped(current = 9.0, increase = true)).isEqualTo(10.0)
        assertThat(SetInputField.REPS.stepped(current = 1.0, increase = false)).isEqualTo(0.0)
        assertThat(SetInputField.REPS.stepped(current = 0.0, increase = false)).isEqualTo(0.0)
        assertThat(SetInputField.LOAD.stepped(current = 40.0, increase = true)).isEqualTo(42.5)
        assertThat(SetInputField.LOAD.stepped(current = 1.0, increase = false)).isEqualTo(0.0)
    }

    @Test
    fun steppingFromAnEmptyField_startsFromTheFallbackTargetRatherThanInventingALoad() {
        // A null target means no confirmed baseline exists, so the first press must not
        // conjure a load out of nowhere; it starts at one step above nothing.
        assertThat(SetInputField.LOAD.stepped(current = null, increase = true, fallback = 20.0))
            .isEqualTo(20.0)
        assertThat(SetInputField.LOAD.stepped(current = null, increase = true, fallback = null))
            .isEqualTo(2.5)
        assertThat(SetInputField.LOAD.stepped(current = null, increase = false, fallback = null))
            .isEqualTo(0.0)
    }

    @Test
    fun stepping_staysWithinTheLoggableRange() {
        assertThat(SetInputField.REPS.stepped(current = 1_000.0, increase = true))
            .isEqualTo(1_000.0)
        assertThat(SetInputField.LOAD.stepped(current = 100_000.0, increase = true))
            .isEqualTo(100_000.0)
    }

    @Test
    fun previousComparableValue_isOfferedOnlyWhenThePreviousSetActuallyRecordedIt() {
        val previous = WorkoutSet(
            id = "previous",
            workoutExerciseId = "exercise",
            setNumber = 1,
            exerciseType = ExerciseType.WEIGHT_REPS,
            completedReps = 10,
            completedWeight = 40.0,
            isCompleted = true
        )

        assertThat(SetInputField.LOAD.previousValue(previous)).isEqualTo(40.0)
        assertThat(SetInputField.REPS.previousValue(previous)).isEqualTo(10.0)
        assertThat(SetInputField.DURATION.previousValue(previous)).isNull()
        assertThat(SetInputField.LOAD.previousValue(null)).isNull()
    }

    @Test
    fun previousComparableValue_ignoresASetThatWasSkippedOrNeverCompleted() {
        val skipped = WorkoutSet(
            id = "previous",
            workoutExerciseId = "exercise",
            setNumber = 1,
            exerciseType = ExerciseType.WEIGHT_REPS,
            completedReps = 5,
            completedWeight = 40.0,
            stopReason = SetStopReason.USER_SKIPPED,
            stoppedAtTimestamp = 10L,
            isCompleted = false
        )

        assertThat(SetInputField.LOAD.previousValue(skipped)).isNull()
        assertThat(SetInputField.REPS.previousValue(skipped)).isNull()
    }

    @Test
    fun fieldsForExerciseType_exposeOnlyTheMeasurementsThatTypeSupports() {
        assertThat(SetInputField.forType(ExerciseType.WEIGHT_REPS))
            .containsExactly(SetInputField.LOAD, SetInputField.REPS).inOrder()
        assertThat(SetInputField.forType(ExerciseType.BODYWEIGHT_REPS))
            .containsExactly(SetInputField.REPS)
        assertThat(SetInputField.forType(ExerciseType.ASSISTED_BODYWEIGHT))
            .containsExactly(SetInputField.ASSISTANCE, SetInputField.REPS).inOrder()
        assertThat(SetInputField.forType(ExerciseType.DURATION))
            .containsExactly(SetInputField.DURATION)
        assertThat(SetInputField.forType(ExerciseType.DISTANCE_DURATION))
            .containsExactly(SetInputField.DISTANCE, SetInputField.DURATION).inOrder()
    }

    @Test
    fun stopReasonLabels_stayPlainAndNeverDiagnostic() {
        val labels = SetStopReason.entries.map { stopReasonLabel(it) }
        val forbidden = listOf(
            "injur", "pain level", "diagnos", "symptom", "medical", "condition",
            "hurt yourself", "damage", "risk"
        )

        labels.forEach { label ->
            assertThat(label).isNotEmpty()
            forbidden.forEach { word ->
                assertThat(label.lowercase()).doesNotContain(word)
            }
        }
        assertThat(stopReasonLabel(SetStopReason.PAIN_STOP)).isEqualTo("Something hurt, so I stopped")
    }

    @Test
    fun restCountdownLabel_readsAsMinutesAndSeconds() {
        assertThat(restCountdownLabel(0)).isEqualTo("0:00")
        assertThat(restCountdownLabel(9)).isEqualTo("0:09")
        assertThat(restCountdownLabel(90)).isEqualTo("1:30")
        assertThat(restCountdownLabel(600)).isEqualTo("10:00")
    }
}
