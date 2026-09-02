package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ExercisePrescriptionTest {

    @Test
    fun weightRepetitionPrescription_keepsOnlyApplicableTargets() {
        val prescription = ExercisePrescription(
            exerciseType = ExerciseType.WEIGHT_REPS,
            targetSets = 3,
            repRange = RepRange(8, 10),
            targetWeight = 47.5,
            restSeconds = 90,
            effortTarget = EffortTarget(minRir = 2, maxRir = 4),
            restClass = RestClass.MODERATE,
            restTargetSource = RestTargetSource.PRODUCT_POLICY
        )

        assertThat(prescription.repRange).isEqualTo(RepRange(8, 10))
        assertThat(prescription.targetWeight).isEqualTo(47.5)
        assertThat(prescription.targetDurationSeconds).isNull()
        assertThat(prescription.targetDistanceMeters).isNull()
        assertThat(prescription.targetAssistanceWeight).isNull()
        assertThat(prescription.effortTarget).isEqualTo(EffortTarget(2, 4))
        assertThat(prescription.restClass).isEqualTo(RestClass.MODERATE)
        assertThat(prescription.restTargetSource).isEqualTo(RestTargetSource.PRODUCT_POLICY)
    }

    @Test
    fun durationPrescription_requiresPositiveSecondsAndRejectsRepetitions() {
        assertThrows(IllegalArgumentException::class.java) {
            ExercisePrescription(
                exerciseType = ExerciseType.DURATION,
                targetSets = 3,
                targetDurationSeconds = null
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            ExercisePrescription(
                exerciseType = ExerciseType.DURATION,
                targetSets = 3,
                repRange = RepRange(8, 10),
                targetDurationSeconds = 45
            )
        }
    }

    @Test
    fun distanceDurationPrescription_requiresAtLeastOneMeasurableTarget() {
        assertThrows(IllegalArgumentException::class.java) {
            ExercisePrescription(
                exerciseType = ExerciseType.DISTANCE_DURATION,
                targetSets = 1
            )
        }

        val prescription = ExercisePrescription(
            exerciseType = ExerciseType.DISTANCE_DURATION,
            targetSets = 1,
            targetDurationSeconds = 600,
            targetDistanceMeters = 2_000.0,
            restSeconds = 0
        )

        assertThat(prescription.targetDurationSeconds).isEqualTo(600)
        assertThat(prescription.targetDistanceMeters).isEqualTo(2_000.0)
    }

    @Test
    fun assistedBodyweightPrescription_usesAssistanceInsteadOfLoad() {
        val prescription = ExercisePrescription(
            exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
            targetSets = 3,
            repRange = RepRange(6, 10),
            targetAssistanceWeight = 40.0
        )

        assertThat(prescription.targetAssistanceWeight).isEqualTo(40.0)

        assertThrows(IllegalArgumentException::class.java) {
            prescription.copy(targetWeight = 40.0)
        }
    }

    @Test
    fun prescription_rejectsOutOfRangeSetsRestAndNumericTargets() {
        assertThrows(IllegalArgumentException::class.java) {
            ExercisePrescription(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                targetSets = 0,
                repRange = RepRange(8, 10)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExercisePrescription(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 10),
                restSeconds = 1_801
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 10),
                targetWeight = Double.NaN
            )
        }
    }

    @Test
    fun plannedAndSessionExercises_shareTheSamePrescriptionWithoutFlatteningIt() {
        val prescription = ExercisePrescription(
            exerciseType = ExerciseType.DURATION,
            targetSets = 2,
            targetDurationSeconds = 45,
            restSeconds = 30
        )
        val planned = PlannedExercise(
            exerciseId = "plank",
            prescription = prescription,
            notes = "Keep ribs down"
        )
        val sessionExercise = WorkoutExercise(
            id = "session-exercise",
            sessionId = "session",
            exerciseId = planned.exerciseId,
            orderIndex = 0,
            prescription = planned.prescription,
            notes = planned.notes
        )

        assertThat(planned.prescription).isSameInstanceAs(prescription)
        assertThat(sessionExercise.prescription).isEqualTo(prescription)
        assertThat(sessionExercise.targetSets).isEqualTo(2)
        assertThat(sessionExercise.targetRepRange).isNull()
    }

    @Test
    fun explicitRestPreference_replacesClassSecondsAndSourceTogether() {
        val base = ExercisePrescription(
            exerciseType = ExerciseType.BODYWEIGHT_REPS,
            targetSets = 3,
            repRange = RepRange(8, 12),
            restSeconds = 75
        )
        val preference = UserRestPreference(RestClass.LONG, restSeconds = 240)

        val preferred = base.withUserRestPreference(preference)

        assertThat(preferred.restSeconds).isEqualTo(240)
        assertThat(preferred.restClass).isEqualTo(RestClass.LONG)
        assertThat(preferred.restTargetSource).isEqualTo(RestTargetSource.USER_PREFERENCE)
        assertThat(preferred.userRestPreferenceOrNull()).isEqualTo(preference)
    }

    @Test
    fun productRestGuidance_isNotReportedAsAnExplicitUserPreference() {
        val prescription = ExercisePrescription(
            exerciseType = ExerciseType.BODYWEIGHT_REPS,
            targetSets = 3,
            repRange = RepRange(8, 12),
            restSeconds = 90,
            restClass = RestClass.MODERATE,
            restTargetSource = RestTargetSource.PRODUCT_POLICY
        )

        assertThat(prescription.userRestPreferenceOrNull()).isNull()
    }

    @Test
    fun restClassificationAndSource_mustBothBePresentOrBothBeAbsent() {
        assertThrows(IllegalArgumentException::class.java) {
            ExercisePrescription(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                restClass = RestClass.MODERATE
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExercisePrescription(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                restTargetSource = RestTargetSource.USER_PREFERENCE
            )
        }
    }

    @Test
    fun durationSet_keepsDurationOutcomeWithoutCreatingTrainingVolume() {
        val set = WorkoutSet(
            id = "set",
            workoutExerciseId = "session-exercise",
            setNumber = 1,
            exerciseType = ExerciseType.DURATION,
            targetDurationSeconds = 45,
            completedDurationSeconds = 50,
            isCompleted = true
        )
        val exercise = WorkoutExercise(
            id = "session-exercise",
            sessionId = "session",
            exerciseId = "plank",
            orderIndex = 0,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.DURATION,
                targetSets = 1,
                targetDurationSeconds = 45
            ),
            sets = listOf(set)
        )

        assertThat(set.completedDurationSeconds).isEqualTo(50)
        assertThat(exercise.totalVolume).isEqualTo(0.0)
    }
}
