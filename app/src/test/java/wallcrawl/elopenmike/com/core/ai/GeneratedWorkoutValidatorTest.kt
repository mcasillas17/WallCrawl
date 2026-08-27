package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
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
            name = "Push Hypertrophy",
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
                GeneratedExercise(
                    exerciseId = "parallel-bar-dips",
                    targetSets = 3,
                    repMin = 8,
                    repMax = 12
                )
            )
        )

        val result = validator.validate(validWorkout)
        assertThat(result).isEqualTo(validWorkout)
    }

    @Test
    fun validate_hallucinatedExerciseId_throwsException() = runTest {
        val hallucinatedWorkout = GeneratedWorkout(
            name = "Fake Routine",
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
            name = "Routine",
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
    fun prescription_invalidRepRange_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedWorkout(
                name = "Routine",
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

    @Test
    fun validate_blankWorkoutName_throwsException() = runTest {
        assertValidationFailure(
            workout = validGeneratedWorkout().copy(name = "   "),
            expectedMessage = "blank name"
        )
    }

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

    private fun validGeneratedWorkout() = GeneratedWorkout(
        name = "Valid Workout",
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
