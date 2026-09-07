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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GeneratedWorkoutValidatorTest {

    private lateinit var catalog: InMemoryExerciseCatalog
    private lateinit var validator: GeneratedWorkoutValidator

    @Before
    fun setup() {
        catalog = InMemoryExerciseCatalog()
        validator = GeneratedWorkoutValidator(catalog)
    }

    @Test
    fun validate_validWorkout_passesSuccessfully() = runTest {
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

        val result = validator.validate(validWorkout)
        assertThat(result).isEqualTo(validWorkout)
    }

    @Test
    fun validate_hallucinatedExerciseId_throwsException() = runTest {
        val hallucinatedWorkout = GeneratedWorkout(
            title = testTitle(),
            rationale = testRationale(),
            focusMuscles = listOf("Chest"),
            estimatedDurationMinutes = 45,
            exercises = listOf(
                GeneratedExercise(
                    exerciseId = "spider-man-web-pull-press", // non-existent ID
                    targetSets = 3,
                    repMin = 10,
                    repMax = 12
                )
            )
        )

        try {
            validator.validate(hallucinatedWorkout)
            fail("Expected WorkoutValidationException for hallucinated exercise ID")
        } catch (e: WorkoutValidationException) {
            assertThat(e.message).contains("Hallucinated or invalid exercise ID")
        }
    }

    @Test
    fun validate_exerciseNotInAllowedCandidates_throwsException() = runTest {
        val workout = GeneratedWorkout(
            title = testTitle(),
            rationale = testRationale(),
            focusMuscles = listOf("Chest"),
            estimatedDurationMinutes = 45,
            exercises = listOf(
                GeneratedExercise(
                    exerciseId = "barbell-bench-press",
                    targetSets = 3,
                    repMin = 8,
                    repMax = 10
                )
            )
        )

        val allowedOnlyDumbbells = setOf("incline-dumbbell-press", "dumbbell-lateral-raise")

        try {
            validator.validate(workout, allowedOnlyDumbbells)
            fail("Expected WorkoutValidationException for exercise not in allowed candidate list")
        } catch (e: WorkoutValidationException) {
            assertThat(e.message).contains("not in the allowed candidate list")
        }
    }

    @Test
    fun validate_prescriptionTypeDoesNotMatchCatalog_throwsException() = runTest {
        val workout = GeneratedWorkout(
            title = testTitle(),
            rationale = testRationale(),
            focusMuscles = listOf("Chest"),
            estimatedDurationMinutes = 30,
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

        assertValidationFailure(workout, "type")
    }

    @Test
    fun prescription_invalidRepRange_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedWorkout(
                title = testTitle(),
                rationale = testRationale(),
                focusMuscles = listOf("Chest"),
                estimatedDurationMinutes = 45,
                exercises = listOf(
                GeneratedExercise(
                    exerciseId = "incline-dumbbell-press",
                    targetSets = 3,
                    repMin = 12,
                    repMax = 8 // repMax < repMin
                )
            )
            )
        }
    }

    // A blank workout name is no longer representable: the title is a split plus an
    // emphasis, both of which a planner has to choose. The check it replaced lives in the
    // type system now.

    @Test
    fun validate_outOfRangeWorkoutDuration_throwsException() = runTest {
        assertValidationFailure(
            workout = validGeneratedWorkout().copy(estimatedDurationMinutes = 0),
            expectedMessage = "duration"
        )
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
                    prescription = exercise.prescription.copy(
                        repRange = wallcrawl.elopenmike.com.core.model.RepRange(1, 1_001)
                    )
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

    @Test
    fun structuralViolations_validWorkout_reportsNothing() = runTest {
        assertThat(validator.structuralViolations(validGeneratedWorkout(), null)).isEmpty()
    }

    @Test
    fun structuralViolations_hallucinatedId_namesTheCodeIdAndPosition() = runTest {
        val workout = validGeneratedWorkout().withOnlyExercise { exercise ->
            exercise.copy(exerciseId = "spider-man-web-pull-press")
        }

        val violation = validator.structuralViolations(workout, null).single()

        assertThat(violation.code).isEqualTo(ProgramViolationCode.UNKNOWN_EXERCISE_ID)
        assertThat(violation.exerciseId).isEqualTo("spider-man-web-pull-press")
        assertThat(violation.orderIndex).isEqualTo(0)
    }

    @Test
    fun structuralViolations_outsideCandidateSet_reportsCandidateMembership() = runTest {
        val violation = validator
            .structuralViolations(validGeneratedWorkout(), setOf("barbell-bench-press"))
            .single()

        assertThat(violation.code).isEqualTo(ProgramViolationCode.NOT_IN_CANDIDATE_SET)
        assertThat(violation.exerciseId).isEqualTo("incline-dumbbell-press")
    }

    @Test
    fun structuralViolations_typeMismatch_reportsEveryOffendingExercise() = runTest {
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

        // Unlike `validate`, which stops at the first problem it can name, the structured
        // report is complete: whole-program validation has to show every reason at once.
        assertThat(validator.structuralViolations(workout, null).map { it.code })
            .containsExactly(
                ProgramViolationCode.PRESCRIPTION_TYPE_MISMATCH,
                ProgramViolationCode.UNKNOWN_EXERCISE_ID
            )
            .inOrder()
    }

    @Test
    fun structuralViolations_emptyRecommendation_reportsIt() = runTest {
        val workout = validGeneratedWorkout().copy(exercises = emptyList())

        assertThat(validator.structuralViolations(workout, null).map { it.code })
            .containsExactly(ProgramViolationCode.EMPTY_RECOMMENDATION)
    }

    @Test
    fun structuralViolations_durationOutsideBounds_reportsIt() = runTest {
        val workout = validGeneratedWorkout().copy(estimatedDurationMinutes = 0)

        assertThat(validator.structuralViolations(workout, null).map { it.code })
            .containsExactly(ProgramViolationCode.DURATION_OUT_OF_BOUNDS)
    }

    @Test
    fun structuralViolations_blankExerciseId_reportsItWithoutClaimingTheCatalogWasChecked() =
        runTest {
            val workout = validGeneratedWorkout().withOnlyExercise { exercise ->
                exercise.copy(exerciseId = "  ")
            }

            assertThat(validator.structuralViolations(workout, null).map { it.code })
                .containsExactly(ProgramViolationCode.BLANK_EXERCISE_ID)
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

    private suspend fun assertValidationFailure(
        workout: GeneratedWorkout,
        expectedMessage: String
    ) {
        try {
            validator.validate(workout)
            fail("Expected WorkoutValidationException containing '$expectedMessage'")
        } catch (exception: WorkoutValidationException) {
            assertThat(exception.message).contains(expectedMessage)
        }
    }
}
