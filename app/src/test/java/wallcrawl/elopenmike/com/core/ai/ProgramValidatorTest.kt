package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionProgramConstraints
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutSet

/**
 * The rules that apply on every path, including the production legacy one.
 *
 * The reviewed-only rules and aggregate dose accounting live in
 * [ProgramValidatorAggregateDoseTest]; repair lives in [ProgramValidatorRepairTest].
 */
class ProgramValidatorTest {

    @Test
    fun aValidLegacyProposal_isAcceptedAndRecordsWhatProducedIt() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id)))

        val result = validate(plan, validatorContext(listOf(bench)))

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
        val snapshot = result.snapshot
        assertThat(snapshot.outcome).isEqualTo(RecommendationOutcome.VALID)
        assertThat(snapshot.reasonCodes).isEmpty()
        assertThat(snapshot.validatorVersion).isEqualTo(ProgramValidatorVersion.WHOLE_PROGRAM_V1)
        assertThat(snapshot.durationEstimatorVersion).isEqualTo(WorkoutDurationEstimator.VERSION)
        assertThat(snapshot.catalogVersion).isEqualTo(VALIDATOR_CATALOG_VERSION)
        assertThat(snapshot.reviewPolicyVersion).isEqualTo(1)
        assertThat(snapshot.profileId).isEqualTo("profile-under-test")
        assertThat(snapshot.profileRevision).isEqualTo(1)
    }

    @Test
    fun aLegacyProposal_recordsNoReviewedOnlyIdentityItNeverUsed() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id)))

        val snapshot = validate(plan, validatorContext(listOf(bench))).snapshot

        assertThat(snapshot.reviewedPathEnabled).isFalse()
        assertThat(snapshot.trainingPolicyVersion).isNull()
        assertThat(snapshot.ledgerPolicyVersion).isNull()
        assertThat(snapshot.programStatePolicyVersion).isNull()
        assertThat(snapshot.adaptationState).isNull()
        assertThat(snapshot.weekStartEpochDay).isNull()
        assertThat(snapshot.timeZoneId).isNull()
        assertThat(snapshot.doseAccounting).isEmpty()
    }

    @Test
    fun theDisabledReviewedGate_appliesNoReviewedOnlyRule() = runTest {
        // A draft-metadata exercise is exactly what the shipped 37-entry cohort looks like.
        // On the legacy path it must plan normally rather than being refused for lacking an
        // approval the legacy path never asked for.
        val draft = syntheticDraftExercise(id = "draft-press", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(draft.id, targetSets = 20)))

        val result = validate(plan, validatorContext(listOf(draft)))

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun anExerciseOutsideTheCandidateSet_isRejected() = runTest {
        val allowed = syntheticExerciseWithoutReviewedMetadata("allowed-press")
        val other = syntheticExerciseWithoutReviewedMetadata("other-press")
        val plan = validatedWorkout(listOf(repetitionPlan(other.id)))

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(allowed)),
            catalog = InMemoryExerciseCatalog(listOf(allowed, other))
        )

        assertThat(result.codes()).contains(ProgramViolationCode.NOT_IN_CANDIDATE_SET)
    }

    @Test
    fun anExerciseTheUserExcluded_isRejectedOnTheLegacyPathToo() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(bench),
                profile = UserProfile(
                    id = "profile-under-test",
                    revision = 1,
                    excludedExerciseIds = listOf(bench.id)
                )
            )
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.EXPLICIT_CONSTRAINT_VIOLATED
        )
    }

    @Test
    fun aRepeatedExerciseId_isRejectedWhileTheConstraintIsDeclared() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(
            listOf(repetitionPlan(bench.id), repetitionPlan(bench.id))
        )

        val result = validate(plan, validatorContext(listOf(bench)))

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.DUPLICATE_EXERCISE_IN_SESSION
        )
    }

    @Test
    fun aRepeatedExerciseId_isAcceptedWhenTheConstraintIsNotDeclared() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(
            listOf(repetitionPlan(bench.id), repetitionPlan(bench.id))
        )

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(bench),
                programConstraints = SessionProgramConstraints(uniqueExerciseIds = false)
            )
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun aRepeatedProgressionFamily_isAcceptedUnlessItWasDeclared() = runTest {
        val first = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val second = syntheticApprovedExercise(id = "press-b", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(
            listOf(repetitionPlan(first.id), repetitionPlan(second.id))
        )
        val exercises = listOf(first, second)

        // Both fixtures share "synthetic-family"; repetition is legal by default.
        assertThat(validate(plan, validatorContext(exercises)))
            .isInstanceOf(ProgramValidationResult.Valid::class.java)

        val declared = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = exercises,
                programConstraints = SessionProgramConstraints(uniqueProgressionFamilies = true)
            ),
            catalog = InMemoryExerciseCatalog(exercises)
        )
        assertThat(declared.codes()).containsExactly(
            ProgramViolationCode.DUPLICATE_PROGRESSION_FAMILY
        )
    }

    @Test
    fun movementCoverage_isInertUntilAPatternIsDeclaredRequired() = runTest {
        // The fixture's approved metadata is HORIZONTAL_PUSH, so a required SQUAT is missing.
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id)))
        val exercises = listOf(press)

        assertThat(validate(plan, validatorContext(exercises)))
            .isInstanceOf(ProgramValidationResult.Valid::class.java)

        val declared = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = exercises,
                programConstraints = SessionProgramConstraints(
                    requiredMovementPatterns = setOf(MovementPattern.SQUAT)
                )
            ),
            catalog = InMemoryExerciseCatalog(exercises)
        )
        assertThat(declared.codes()).containsExactly(
            ProgramViolationCode.MISSING_REQUIRED_MOVEMENT_PATTERN
        )
    }

    @Test
    fun aNullLoad_isAlwaysLegalAndIsNeverFilledIn() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id, targetWeight = null)))

        val result = validate(plan, validatorContext(listOf(bench)))

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
        val valid = result as ProgramValidationResult.Valid
        assertThat(valid.workout.exercises.single().targetWeight).isNull()
    }

    @Test
    fun aConfirmedStartingLoad_isATraceableSource() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id, targetWeight = 60.0)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(bench),
                profile = UserProfile(
                    id = "profile-under-test",
                    revision = 1,
                    confirmedStartingLoads = mapOf(bench.id to 60.0)
                )
            )
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun theLastRecordedLoad_isATraceableSource() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id, targetWeight = 100.0)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(bench),
                exerciseHistory = mapOf(bench.id to history(bench.id, lastWeight = 100.0))
            )
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun theDocumentedLegacyIncrement_staysTraceableInPounds() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id, targetWeight = 105.0)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(bench),
                profile = UserProfile(
                    id = "profile-under-test",
                    revision = 1,
                    preferredUnit = WeightUnit.LBS
                ),
                exerciseHistory = mapOf(bench.id to history(bench.id, lastWeight = 100.0))
            )
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun theDocumentedLegacyIncrement_staysTraceableInKilograms() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id, targetWeight = 102.5)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(bench),
                profile = UserProfile(
                    id = "profile-under-test",
                    revision = 1,
                    preferredUnit = WeightUnit.KG
                ),
                exerciseHistory = mapOf(bench.id to history(bench.id, lastWeight = 100.0))
            )
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun theKilogramIncrement_isNotTraceableForAPoundProfile() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id, targetWeight = 102.5)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(bench),
                profile = UserProfile(
                    id = "profile-under-test",
                    revision = 1,
                    preferredUnit = WeightUnit.LBS
                ),
                exerciseHistory = mapOf(bench.id to history(bench.id, lastWeight = 100.0))
            )
        )

        assertThat(result.codes()).containsExactly(ProgramViolationCode.UNTRACEABLE_LOAD)
    }

    @Test
    fun anInventedLoad_isRejected() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id, targetWeight = 135.0)))

        val result = validate(plan, validatorContext(listOf(bench)))

        assertThat(result.codes()).containsExactly(ProgramViolationCode.UNTRACEABLE_LOAD)
    }

    @Test
    fun anInventedAssistanceLoad_isRejected() = runTest {
        val assisted = syntheticExerciseWithoutReviewedMetadata("assisted-pull-up")
            .copy(type = ExerciseType.ASSISTED_BODYWEIGHT)
        val plan = validatedWorkout(
            listOf(
                PlannedExercise(
                    exerciseId = assisted.id,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                        targetSets = 3,
                        repRange = RepRange(6, 10),
                        targetAssistanceWeight = 20.0
                    )
                )
            )
        )

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(assisted)),
            catalog = InMemoryExerciseCatalog(listOf(assisted))
        )

        assertThat(result.codes()).containsExactly(ProgramViolationCode.UNTRACEABLE_LOAD)
    }

    @Test
    fun aDurationThatDisagreesWithTheNamedEstimator_isRejected() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id)))
            .let { it.copy(estimatedDurationMinutes = it.estimatedDurationMinutes + 5) }

        val result = validate(plan, validatorContext(listOf(bench)))

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.DURATION_ESTIMATE_MISMATCH
        )
    }

    @Test
    fun aDurationOffByOneMinute_staysInsideTheDeclaredTolerance() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id)))
            .let { it.copy(estimatedDurationMinutes = it.estimatedDurationMinutes + 1) }

        val result = validate(plan, validatorContext(listOf(bench)))

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun aProposalFarFromTheRequestedDuration_isNotRejectedForThat() = runTest {
        // Version 1 deliberately makes no promise that the estimate matches what the user
        // asked for; only internal arithmetic consistency is enforced.
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id, targetSets = 20)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(bench),
                profile = UserProfile(
                    id = "profile-under-test",
                    revision = 1,
                    preferredDurationMinutes = 20
                )
            )
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun everyReasonIsReportedAtOnceAndInAStableOrder() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(
            listOf(
                repetitionPlan(bench.id, targetWeight = 135.0),
                repetitionPlan(bench.id, targetWeight = 145.0)
            )
        )

        val codes = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(bench),
                profile = UserProfile(
                    id = "profile-under-test",
                    revision = 1,
                    excludedExerciseIds = listOf(bench.id)
                )
            )
        ).codes()

        assertThat(codes).containsExactly(
            ProgramViolationCode.DUPLICATE_EXERCISE_IN_SESSION,
            ProgramViolationCode.EXPLICIT_CONSTRAINT_VIOLATED,
            ProgramViolationCode.UNTRACEABLE_LOAD
        ).inOrder()
    }

    @Test
    fun aRejectedProposal_stillRecordsWhatWasCheckedAndWhy() = runTest {
        val bench = syntheticExerciseWithoutReviewedMetadata("bench-press")
        val plan = validatedWorkout(listOf(repetitionPlan(bench.id, targetWeight = 135.0)))

        val snapshot = validate(plan, validatorContext(listOf(bench))).snapshot

        assertThat(snapshot.outcome).isEqualTo(RecommendationOutcome.REJECTED)
        assertThat(snapshot.reasonCodes).containsExactly(ProgramViolationCode.UNTRACEABLE_LOAD)
        assertThat(snapshot.contextIdentity).isNotEmpty()
    }

    private fun history(exerciseId: String, lastWeight: Double) = ExercisePerformanceHistory(
        exerciseId = exerciseId,
        lastWeight = lastWeight,
        lastReps = 8,
        bestEstimated1RM = null,
        recentSets = emptyList<WorkoutSet>()
    )
}

internal suspend fun validate(
    workout: wallcrawl.elopenmike.com.core.model.GeneratedWorkout,
    context: wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext,
    catalog: InMemoryExerciseCatalog = InMemoryExerciseCatalog(context.allowedExercises),
    allowRepair: Boolean = false
): ProgramValidationResult = ProgramValidator(GeneratedWorkoutValidator(catalog))
    .validate(workout = workout, context = context, allowRepair = allowRepair)

internal fun ProgramValidationResult.codes(): List<ProgramViolationCode> = when (this) {
    is ProgramValidationResult.Valid -> emptyList()
    is ProgramValidationResult.Invalid -> violations.map { it.code }.distinct()
}
