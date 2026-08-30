package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType

class PlannerFixtureTest {

    private val loader = PlannerFixtureLoader()
    private val evaluator = PlannerFixtureEvaluator(loader = loader)
    private val prescriptionFactory = DefaultExercisePrescriptionFactory()

    @Test
    fun evaluateCorpus_runsEveryPlannerFixture() = runTest {
        val evaluations = evaluator.evaluateCorpus()

        assertThat(evaluations.map { it.built.fixture.id }).containsExactly(
            "bodyweight-beginner",
            "band-only",
            "machine-only",
            "full-gym-advanced",
            "returning-user",
            "limited-capability",
            "mixed-unit-history",
            "sparse-history",
            "no-strength-candidates"
        )
    }

    @Test
    fun evaluateCorpus_enforcesDeterminismAndPlannerInvariants() = runTest {
        val evaluations = evaluator.evaluateCorpus()

        evaluations.forEach { evaluation ->
            assertThat(evaluation.inputAfterFirstAttempt).isEqualTo(evaluation.inputBefore)
            assertThat(evaluation.inputAfterSecondAttempt).isEqualTo(evaluation.inputBefore)

            when (evaluation) {
                is PlannerFixtureSuccessEvaluation -> assertSuccessfulFixture(evaluation)
                is PlannerFixtureFailureEvaluation -> assertFailureFixture(evaluation)
            }
        }
    }

    @Test
    fun limitedCapabilityFixture_matchesAllComfortableCapabilitiesControl() = runTest {
        val limitedFixture = loader.loadCorpus().single { it.id == "limited-capability" }
        val comfortableControl = limitedFixture.copy(
            profile = limitedFixture.profile.copy(
                movementCapabilities = MovementCapabilities.from(
                    MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
                )
            )
        )

        val limited = evaluator.evaluateFixture(limitedFixture) as PlannerFixtureSuccessEvaluation
        val control = evaluator.evaluateFixture(comfortableControl) as PlannerFixtureSuccessEvaluation

        assertThat(limited.firstWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(control.firstWorkout.normalizedPlannerFixtureWorkout())
        assertThat(limited.secondWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(control.secondWorkout.normalizedPlannerFixtureWorkout())
    }

    @Test
    fun corpusMetadata_usesSupportedVersionsAndBoundedHistory() {
        val fixtures = loader.loadCorpus()

        assertThat(fixtures).hasSize(9)
        fixtures.forEach { fixture ->
            assertThat(fixture.schemaVersion).isEqualTo(1)
            assertThat(fixture.policyVersion).isGreaterThan(0)
            assertThat(fixture.catalogVersion).isNotEmpty()
            assertThat(fixture.exerciseHistory.size).isAtMost(8)
        }
    }

    private fun assertSuccessfulFixture(evaluation: PlannerFixtureSuccessEvaluation) {
        val normalizedFirst = evaluation.firstWorkout.normalizedPlannerFixtureWorkout()
        val normalizedSecond = evaluation.secondWorkout.normalizedPlannerFixtureWorkout()
        assertThat(normalizedFirst).isEqualTo(normalizedSecond)

        val selectedIds = normalizedFirst.exercises.map { it.exerciseId }
        val allowedIds = evaluation.built.context.allowedExercises.map(Exercise::id).toSet()
        val filteredIds = evaluation.built.filteredExercises.map(Exercise::id).toSet()
        val catalogById = evaluation.built.catalogExercises.associateBy(Exercise::id)
        val excludedIds = evaluation.built.userProfile.excludedExerciseIds.toSet()

        assertThat(selectedIds).isNotEmpty()
        assertThat(allowedIds.containsAll(selectedIds)).isTrue()
        assertThat(filteredIds.containsAll(selectedIds)).isTrue()
        assertThat(excludedIds.intersect(selectedIds.toSet())).isEmpty()

        if (evaluation.built.fixture.expected.requiredExerciseIds.isNotEmpty()) {
            evaluation.built.fixture.expected.requiredExerciseIds.forEach { requiredId ->
                assertThat(selectedIds).contains(requiredId)
            }
        }
        if (evaluation.built.fixture.expected.forbiddenExerciseIds.isNotEmpty()) {
            evaluation.built.fixture.expected.forbiddenExerciseIds.forEach { forbiddenId ->
                assertThat(selectedIds).doesNotContain(forbiddenId)
            }
        }

        normalizedFirst.exercises.forEach { generated ->
            val catalogExercise = checkNotNull(catalogById[generated.exerciseId])
            val expectedPrescription = prescriptionFactory.create(catalogExercise, evaluation.built.context)

            assertThat(generated.prescription.exerciseType).isEqualTo(catalogExercise.type)
            assertThat(generated.prescription).isEqualTo(expectedPrescription)

            when (catalogExercise.type) {
                ExerciseType.WEIGHT_REPS -> {
                    val history = evaluation.built.context.exerciseHistory[catalogExercise.id]
                    val confirmedLoad = evaluation.built.userProfile.confirmedStartingLoads[catalogExercise.id]
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                    if (history == null && confirmedLoad == null) {
                        assertThat(generated.prescription.targetWeight).isNull()
                    } else {
                        assertThat(generated.prescription.targetWeight)
                            .isEqualTo(expectedPrescription.targetWeight)
                    }
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.BODYWEIGHT_REPS -> {
                    assertThat(generated.prescription.repRange).isNotNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.ASSISTED_BODYWEIGHT -> {
                    assertThat(generated.prescription.repRange).isNotNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.DURATION -> {
                    assertThat(generated.prescription.repRange).isNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNotNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.DISTANCE_DURATION -> {
                    assertThat(generated.prescription.repRange).isNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(
                        generated.prescription.targetDurationSeconds != null ||
                            generated.prescription.targetDistanceMeters != null
                    ).isTrue()
                }
            }
        }
    }

    private fun assertFailureFixture(evaluation: PlannerFixtureFailureEvaluation) {
        val expectedFailure = when (evaluation.built.fixture.expected.outcome) {
            PlannerFixtureOutcome.SUCCESS -> error("Expected failure fixture but found success")
            PlannerFixtureOutcome.NO_CANDIDATES -> WorkoutPlanningFailure.NO_CANDIDATES
            PlannerFixtureOutcome.NO_STRENGTH_CANDIDATES ->
                WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES
            PlannerFixtureOutcome.NO_CANDIDATES_FOR_ANY_SPLIT ->
                WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT
        }

        assertThat(evaluation.firstFailure).isEqualTo(expectedFailure)
        assertThat(evaluation.secondFailure).isEqualTo(expectedFailure)
    }
}
