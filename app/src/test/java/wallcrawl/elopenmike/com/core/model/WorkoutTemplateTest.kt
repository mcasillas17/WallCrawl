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

    private fun loaded(weight: Double?, assistance: Double? = null) = listOf(
        PlannedExercise(
            exerciseId = "bench",
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = weight
            )
        ),
        PlannedExercise(
            exerciseId = "assisted-pull-up",
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetAssistanceWeight = assistance
            )
        )
    )

    @Test
    fun startConversion_roundsToFieldPrecisionButNeverTurnsAPositiveWeightIntoZero() {
        assertThat(convertWeightForEditing(100.0, WeightUnit.LBS, WeightUnit.KG)).isEqualTo(45.36)
        // 0.01 lb is 0.0045 kg: the smallest step, not a stated zero; a real zero stays zero.
        assertThat(convertWeightForEditing(0.01, WeightUnit.LBS, WeightUnit.KG)).isEqualTo(0.01)
        assertThat(convertWeightForEditing(0.0, WeightUnit.LBS, WeightUnit.KG)).isEqualTo(0.0)
        assertThat(roundForEditing(0.004)).isEqualTo(0.01)
        assertThat(roundForEditing(0.005)).isEqualTo(0.01)
        assertThat(roundForEditing(100.125)).isEqualTo(100.13)

        val started = WorkoutTemplate(name = "Tiny", weightUnit = WeightUnit.LBS, exercises = loaded(0.01, 0.01))
            .exercisesIn(WeightUnit.KG)
        assertThat(started[0].prescription.targetWeight).isEqualTo(0.01)
        assertThat(started[1].prescription.targetAssistanceWeight).isEqualTo(0.01)
        val zero = WorkoutTemplate(name = "Zero", weightUnit = WeightUnit.LBS, exercises = loaded(0.0, 0.0))
            .exercisesIn(WeightUnit.KG)
        assertThat(zero[0].prescription.targetWeight).isEqualTo(0.0)
        assertThat(zero[1].prescription.targetAssistanceWeight).isEqualTo(0.0)
    }

    @Test
    fun maxLoad_isTheDomainBoundInPoundsAndItsKilogramEquivalentRoundedDown() {
        assertThat(WorkoutTemplate.maxLoad(WeightUnit.LBS)).isEqualTo(ExercisePrescription.MAX_WEIGHT)
        assertThat(WorkoutTemplate.maxLoad(WeightUnit.KG)).isEqualTo(4_535.92)
        // Every kilogram load the bound admits still fits the domain once converted to pounds.
        assertThat(convertWeight(WorkoutTemplate.maxLoad(WeightUnit.KG), WeightUnit.KG, WeightUnit.LBS))
            .isAtMost(ExercisePrescription.MAX_WEIGHT)
    }

    @Test
    fun kilogramTemplate_rejectsLoadOrAssistanceThatCannotBeExpressedInPounds() {
        WorkoutTemplate(name = "Max", weightUnit = WeightUnit.KG, exercises = loaded(4_535.92, 4_535.92))
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutTemplate(name = "Max", weightUnit = WeightUnit.KG, exercises = loaded(4_535.93))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkoutTemplate(name = "Max", weightUnit = WeightUnit.KG, exercises = loaded(null, 4_536.0))
        }
        WorkoutTemplate(name = "Max", weightUnit = WeightUnit.LBS, exercises = loaded(10_000.0))
    }

    @Test
    fun legacyUnitlessTemplate_isReadableButNeedsItsUnitConfirmedOnlyWhenItCarriesALoad() {
        val legacy = WorkoutTemplate(name = "Old", weightUnit = null, exercises = loaded(100.0))
        assertThat(legacy.requiresUnitConfirmation).isTrue()
        assertThat(WorkoutTemplate(name = "Old", exercises = loaded(null, 0.0)).requiresUnitConfirmation)
            .isTrue()
        assertThat(WorkoutTemplate(name = "Old", exercises = loaded(null)).requiresUnitConfirmation)
            .isFalse()
        assertThat(
            WorkoutTemplate(name = "New", weightUnit = WeightUnit.KG, exercises = loaded(100.0))
                .requiresUnitConfirmation
        ).isFalse()
    }
}
