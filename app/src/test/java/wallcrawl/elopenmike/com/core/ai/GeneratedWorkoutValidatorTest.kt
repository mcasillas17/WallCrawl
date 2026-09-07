package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.WorkoutEmphasis
import wallcrawl.elopenmike.com.core.model.WorkoutRationaleSpec
import wallcrawl.elopenmike.com.core.model.WorkoutSplit
import wallcrawl.elopenmike.com.core.model.WorkoutTitleSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * The structural half of validation: catalog existence, candidate membership, and the
 * catalog exercise type.
 *
 * Several rules this suite used to reach through the validator are enforced by
 * [ExercisePrescription]'s own constructor instead, so a malformed prescription is not
 * representable in the first place. Those cases stay here because they are the reason the
 * validator does not restate them.
 */
class GeneratedWorkoutValidatorTest {

    private lateinit var catalog: InMemoryExerciseCatalog
    private lateinit var validator: GeneratedWorkoutValidator

    @Before
    fun setup() {
        catalog = InMemoryExerciseCatalog()
        validator = GeneratedWorkoutValidator(catalog)
    }

    @Test
    fun aValidWorkout_reportsNothing() = runTest {
        val validWorkout = GeneratedWorkout(
            title = testTitle(),
            rationale = testRationale(),
            focusMuscles = listOf("Chest", "Shoulders", "Triceps"),
            estimatedDurationMinutes = 50,
            exercises = listOf(
                GeneratedExercise(
                    exerciseId = "incline-dumbbell-press",
                    targetSets = 3,
                    repMin = 8,
                    repMax = 10,
                    targetWeight = 47.5
                ),
                PlannedExercise(
                    exerciseId = "parallel-bar-dips",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.BODYWEIGHT_REPS,
                        targetSets = 3,
                        repRange = RepRange(8, 12)
                    )
                )
            )
        )

        assertThat(violations(validWorkout)).isEmpty()
    }

    @Test
    fun aHallucinatedExerciseId_namesTheCodeIdAndPosition() = runTest {
        val workout = validGeneratedWorkout().withOnlyExercise { exercise ->
            exercise.copy(exerciseId = "spider-man-web-pull-press")
        }

        val violation = violations(workout).single()

        assertThat(violation.code).isEqualTo(ProgramViolationCode.UNKNOWN_EXERCISE_ID)
        assertThat(violation.exerciseId).isEqualTo("spider-man-web-pull-press")
        assertThat(violation.orderIndex).isEqualTo(0)
    }

    @Test
    fun anExerciseOutsideTheAllowedSet_reportsCandidateMembership() = runTest {
        val workout = validGeneratedWorkout().withOnlyExercise { exercise ->
            exercise.copy(exerciseId = "barbell-bench-press")
        }
        val allowedOnlyDumbbells = setOf("incline-dumbbell-press", "dumbbell-lateral-raise")

        val violation = violations(workout, allowedOnlyDumbbells).single()

        assertThat(violation.code).isEqualTo(ProgramViolationCode.NOT_IN_CANDIDATE_SET)
        assertThat(violation.exerciseId).isEqualTo("barbell-bench-press")
    }

    @Test
    fun anExerciseInsideTheAllowedSet_passesMembership() = runTest {
        // Membership is always checked, so the passing case has to be pinned too: otherwise
        // a rule that rejected everything would look identical to a correct one.
        val workout = validGeneratedWorkout().withOnlyExercise { exercise ->
            exercise.copy(exerciseId = "barbell-bench-press")
        }

        assertThat(violations(workout, setOf("barbell-bench-press"))).isEmpty()
    }

    @Test
    fun aPrescriptionTypeThatDoesNotMatchTheCatalog_isReported() = runTest {
        val workout = validGeneratedWorkout().copy(
            exercises = listOf(
                PlannedExercise(
                    exerciseId = "parallel-bar-dips",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.DURATION,
                        targetSets = 3,
                        targetDurationSeconds = 45
                    )
                )
            )
        )

        val violation = violations(workout).single()

        assertThat(violation.code).isEqualTo(ProgramViolationCode.PRESCRIPTION_TYPE_MISMATCH)
        assertThat(violation.detail).isEqualTo("DURATION!=BODYWEIGHT_REPS")
    }

    @Test
    fun everyOffendingExerciseIsReported_notJustTheFirst() = runTest {
        val workout = validGeneratedWorkout().copy(
            exercises = listOf(
                PlannedExercise(
                    exerciseId = "parallel-bar-dips",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.DURATION,
                        targetSets = 3,
                        targetDurationSeconds = 45
                    )
                ),
                GeneratedExercise(
                    exerciseId = "spider-man-web-pull-press",
                    targetSets = 3,
                    repMin = 8,
                    repMax = 10
                )
            )
        )

        assertThat(violations(workout).map { it.code })
            .containsExactly(
                ProgramViolationCode.PRESCRIPTION_TYPE_MISMATCH,
                ProgramViolationCode.UNKNOWN_EXERCISE_ID
            )
            .inOrder()
    }

    @Test
    fun anEmptyRecommendation_isReportedAndStopsThePerExerciseChecks() = runTest {
        val workout = validGeneratedWorkout().copy(exercises = emptyList())

        assertThat(violations(workout).map { it.code })
            .containsExactly(ProgramViolationCode.EMPTY_RECOMMENDATION)
    }

    @Test
    fun aDurationOutsideTheRepresentableBounds_isReported() = runTest {
        val workout = validGeneratedWorkout().copy(estimatedDurationMinutes = 0)

        val violation = violations(workout).single()

        assertThat(violation.code).isEqualTo(ProgramViolationCode.DURATION_OUT_OF_BOUNDS)
        assertThat(violation.detail).isEqualTo("0")
    }

    @Test
    fun aBlankExerciseId_isReportedWithoutClaimingTheCatalogWasChecked() = runTest {
        val workout = validGeneratedWorkout().withOnlyExercise { exercise ->
            exercise.copy(exerciseId = "  ")
        }

        assertThat(violations(workout).map { it.code })
            .containsExactly(ProgramViolationCode.BLANK_EXERCISE_ID)
    }

    // A blank workout name is no longer representable: the title is a split plus an
    // emphasis, both of which a planner has to choose. The check it replaced lives in the
    // type system now.

    @Test
    fun prescription_invalidRepRange_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedExercise(
                exerciseId = "incline-dumbbell-press",
                targetSets = 3,
                repMin = 12,
                repMax = 8 // repMax < repMin
            )
        }
    }

    @Test
    fun prescription_excessiveTargetSets_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            validGeneratedWorkout().exercises.single().let { exercise ->
                exercise.copy(prescription = exercise.prescription.copy(targetSets = 21))
            }
        }
    }

    @Test
    fun prescription_excessiveRepMaximum_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            validGeneratedWorkout().exercises.single().let { exercise ->
                exercise.copy(
                    prescription = exercise.prescription.copy(repRange = RepRange(1, 1_001))
                )
            }
        }
    }

    @Test
    fun prescription_negativeOrNonFiniteTargetWeight_throwsException() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalidWeight ->
            assertThrows(IllegalArgumentException::class.java) {
                validGeneratedWorkout().exercises.single().let { exercise ->
                    exercise.copy(
                        prescription = exercise.prescription.copy(targetWeight = invalidWeight)
                    )
                }
            }
        }
    }

    @Test
    fun prescription_outOfRangeRestPeriod_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            validGeneratedWorkout().exercises.single().let { exercise ->
                exercise.copy(prescription = exercise.prescription.copy(restSeconds = 1_801))
            }
        }
    }

    private fun validGeneratedWorkout() = GeneratedWorkout(
        title = testTitle(),
        rationale = testRationale(),
        focusMuscles = listOf("Chest"),
        estimatedDurationMinutes = 45,
        exercises = listOf(
            GeneratedExercise(
                exerciseId = "incline-dumbbell-press",
                targetSets = 3,
                repMin = 8,
                repMax = 10,
                targetWeight = 45.0,
                restSeconds = 90
            )
        )
    )

    private fun testTitle() = WorkoutTitleSpec(
        split = WorkoutSplit.PUSH,
        emphasis = WorkoutEmphasis.HYPERTROPHY
    )

    private fun testRationale() = WorkoutRationaleSpec.GoalFocus(
        goals = emptyList(),
        focusMuscles = listOf("Chest")
    )

    private fun GeneratedWorkout.withOnlyExercise(
        transform: (GeneratedExercise) -> GeneratedExercise
    ): GeneratedWorkout = copy(exercises = listOf(transform(exercises.single())))

    /**
     * Runs the checks with every id this suite names already allowed.
     *
     * Candidate membership is always enforced, so a case that is about something else has
     * to say so by allowing what it names rather than by switching the rule off.
     */
    private suspend fun violations(
        workout: GeneratedWorkout,
        allowedExerciseIds: Set<String> = EVERY_ID_THIS_SUITE_NAMES
    ) = validator.structuralViolations(workout, allowedExerciseIds)

    private companion object {
        val EVERY_ID_THIS_SUITE_NAMES = setOf(
            "incline-dumbbell-press",
            "parallel-bar-dips",
            "barbell-bench-press",
            "spider-man-web-pull-press"
        )
    }
}
