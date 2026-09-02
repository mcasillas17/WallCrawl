package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.*

class TimedHoldProgrammingTest {
    private val exercises = PlannerFixtureContextFactory().bundledCatalogProjection().exercises
    private val factory = DefaultExercisePrescriptionFactory()
    private val profile = UserProfile(availableEquipment = StandardEquipment.ALL)
    private val expectedIds = setOf(
        "active-hang", "bear-plank", "cable-pallof-hold", "copenhagen-plank", "crab-walk",
        "dead-hang", "flutter-kick", "hollow-body-hold", "l-sit-hold", "mountain-climber",
        "plank", "side-plank", "superman-hold", "wall-sit"
    )

    @Test
    fun realPlannerDerivesExactlyThePinnedTimedCohort() = runTest {
        val selected = mutableSetOf<String>()
        exercises.filter { it.type == ExerciseType.DURATION || it.type == ExerciseType.DISTANCE_DURATION }
            .forEach { exercise ->
                val context = WorkoutGenerationContext(userProfile = profile, allowedExercises = listOf(exercise))
                try {
                    val workout = FakeWorkoutPlanner().generateWorkout(context)
                    assertThat(workout.exercises.map { it.exerciseId }).containsExactly(exercise.id)
                    selected += exercise.id
                } catch (error: WorkoutValidationException) {
                    assertThat(error.failure).isEqualTo(WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES)
                }
            }
        assertThat(selected).containsExactlyElementsIn(expectedIds)
    }

    @Test
    fun cohortHasCompleteMetadataAndDurationOnlyPrescriptions() {
        val cohort = exercises.filter { it.id in expectedIds }
        assertThat(cohort.map { it.id }).containsExactlyElementsIn(expectedIds)
        cohort.forEach { exercise ->
            val programming = checkNotNull(exercise.programming) { exercise.id }
            assertThat(programming.recommendedRepRange).isNull()
            assertThat(programming.progressionType).isEqualTo(ProgressionType.DURATION)
            assertThat(programming.coachingSummary).isNotEmpty()
            assertThat(programming.requiredEquipmentCombinations).isNotEmpty()
            assertThat(programming.alternativeExerciseIds).isNotEmpty()
            FitnessGoal.entries.forEach { goal ->
                val context = WorkoutGenerationContext(userProfile = profile.copy(goals = setOf(goal)))
                val prescription = factory.create(exercise, context)
                assertThat(prescription).isEqualTo(factory.create(exercise.copy(programming = null), context))
                assertThat(prescription.targetSets).isEqualTo(3)
                assertThat(prescription.targetDurationSeconds).isEqualTo(45)
                assertThat(prescription.restSeconds).isEqualTo(45)
                assertThat(prescription.repRange).isNull()
                assertThat(prescription.targetWeight).isNull()
                assertThat(prescription.targetAssistanceWeight).isNull()
                assertThat(prescription.targetDistanceMeters).isNull()
            }
        }
    }

    @Test
    fun timedMetadataCannotPromoteStretchOrConditioningIntoStrengthSlots() = runTest {
        val programming = exercises.single { it.id == "plank" }.programming!!
        val excluded = exercises.filter {
            it.type == ExerciseType.DISTANCE_DURATION ||
                (it.type == ExerciseType.DURATION &&
                    (it.isStretch || StandardMuscles.CARDIO in it.primaryMuscles + it.secondaryMuscles))
        }
        assertThat(excluded).isNotEmpty()
        excluded.forEach { exercise ->
            val context = WorkoutGenerationContext(
                userProfile = profile,
                allowedExercises = listOf(exercise.copy(programming = programming))
            )
            val failure = runCatching { FakeWorkoutPlanner().generateWorkout(context) }.exceptionOrNull()
            assertThat(failure).isInstanceOf(WorkoutValidationException::class.java)
            assertThat((failure as WorkoutValidationException).failure)
                .isEqualTo(WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES)
        }
    }

    @Test
    fun copyingAnExerciseCannotBypassTheTypeDependentContract() {
        val plank = exercises.single { it.id == "plank" }
        val bench = exercises.single { it.id == "barbell-bench-press" }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            plank.copy(type = ExerciseType.BODYWEIGHT_REPS)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            bench.copy(type = ExerciseType.DURATION)
        }
    }

    @Test
    fun timedCohortReplaysDeterministicallyThroughPersonaSnapshotsWithoutLoads() = runTest {
        val fixture = PlannerFixtureLoader().loadResource("planner-fixtures/full-gym-advanced.json").copy(
            allowedExerciseIds = expectedIds.toList(),
            exerciseHistory = emptyList()
        )
        val evaluation = PlannerFixtureEvaluator().evaluateFixture(fixture) as PlannerFixtureSuccessEvaluation
        assertThat(evaluation.firstWorkout.copy(id = evaluation.secondWorkout.id))
            .isEqualTo(evaluation.secondWorkout)
        assertThat(evaluation.inputAfterFirstAttempt).isEqualTo(evaluation.inputBefore)
        assertThat(evaluation.inputAfterSecondAttempt).isEqualTo(evaluation.inputBefore)
        evaluation.firstWorkout.exercises.forEach { generated ->
            assertThat(generated.exerciseId).isIn(expectedIds)
            assertThat(generated.prescription.targetDurationSeconds).isEqualTo(45)
            assertThat(generated.prescription.repRange).isNull()
            assertThat(generated.prescription.targetWeight).isNull()
            assertThat(generated.prescription.targetAssistanceWeight).isNull()
        }
    }

    @Test
    fun originalRepPrescriptionsMatchBaselineAcrossGoalsBreaksAndUnits() {
        // Recorded before implementation from origin/main d8fc5a4. New timed metadata
        // must not change any rep exercise's prescription, including existing load policy.
        val rows = buildList {
            exercises.filter { it.type !in setOf(ExerciseType.DURATION, ExerciseType.DISTANCE_DURATION) }
                .sortedBy { it.id }.forEach { exercise ->
                    FitnessGoal.entries.forEach { goal ->
                        listOf(0, 4, 52).forEach { breakWeeks ->
                            WeightUnit.entries.forEach { unit ->
                                val context = WorkoutGenerationContext(userProfile = profile.copy(
                                    goals = setOf(goal), returningAfterBreakWeeks = breakWeeks,
                                    preferredUnit = unit
                                ))
                                val prescription = factory.create(exercise, context)
                                assertThat(prescription.targetWeight).isNull()
                                add("${exercise.id}|$goal|$breakWeeks|$unit|$prescription")
                            }
                        }
                    }
                }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(rows.joinToString("\n").toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertThat(digest).isEqualTo("a2fa02d9c58e15b1aa85179b24ca95acf6dd5e822ef1de6eb61c89b5413da4d8")
    }
}
