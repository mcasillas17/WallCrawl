package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MuscleDoseAccounting
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.ReviewState

/**
 * The reviewed-only rules and the whole-proposal dose check.
 *
 * `UNCALIBRATED` configures a weekly direct-primary allowance of six sets in
 * `STATE_BASED_DOSE_EFFORT_REST_V1`; the numbers below follow from that versioned product
 * choice and are not physiological limits.
 */
class ProgramValidatorAggregateDoseTest {

    @Test
    fun severalExercisesSharingOnePrimary_spendOneAllowanceBetweenThem() = runTest {
        val first = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val second = syntheticApprovedExercise(id = "press-b", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(
            listOf(
                repetitionPlan(first.id, targetSets = 4),
                repetitionPlan(second.id, targetSets = 4)
            )
        )

        // Per exercise each is under the six-set allowance; together they are not, which is
        // precisely what checking one prescription at a time could never notice.
        val result = validate(
            workout = plan,
            context = validatorContext(listOf(first, second), reviewedPath = true)
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED
        )
    }

    @Test
    fun completedExposurePlusTheProposal_isComparedToOneAllowance() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 4)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(press),
                reviewedPath = true,
                directPrimarySets = mapOf("Chest" to 3)
            )
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED
        )
    }

    @Test
    fun exactlyReachingTheAllowance_isLegal() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(press),
                reviewedPath = true,
                directPrimarySets = mapOf("Chest" to 4)
            )
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
        assertThat(result.acceptedSnapshot.doseAccounting).containsExactly(
            MuscleDoseAccounting(
                muscle = "Chest",
                completedSets = 4,
                proposedSets = 2,
                allowanceSets = 6
            )
        )
    }

    @Test
    fun oneSetOverTheAllowance_isAPolicyMismatchAndNothingMore() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 3)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(press),
                reviewedPath = true,
                directPrimarySets = mapOf("Chest" to 4)
            )
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED
        )
        assertThat(result.codes()).doesNotContain(ProgramViolationCode.MALFORMED_WEEKLY_LEDGER)
        assertThat(result.codes()).doesNotContain(ProgramViolationCode.DOSE_ACCOUNTING_OVERFLOW)
    }

    @Test
    fun differentPrimaryMuscles_keepTheirOwnAllowances() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val row = syntheticApprovedExercise(id = "row-a", directPrimaryMuscle = "Back")
        val plan = validatedWorkout(
            listOf(
                repetitionPlan(press.id, targetSets = 5),
                repetitionPlan(row.id, targetSets = 5)
            )
        )

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(press, row), reviewedPath = true)
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
        assertThat(result.acceptedSnapshot.doseAccounting.map { it.muscle })
            .containsExactly("Back", "Chest")
            .inOrder()
    }

    @Test
    fun descriptiveSecondaryInvolvement_isNeverCountedAsDose() = runTest {
        val press = syntheticApprovedExercise(
            id = "press-a",
            directPrimaryMuscle = "Chest",
            descriptiveSecondaryMuscles = setOf("Triceps", "Shoulders")
        )
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 3)))

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(press), reviewedPath = true)
        )

        assertThat(result.acceptedSnapshot.doseAccounting.map { it.muscle }).containsExactly("Chest")
    }

    @Test
    fun aMalformedLedger_isDistinctFromAFullOne() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(press),
                reviewedPath = true,
                // A zero count is not something the calculator can produce; a ledger that
                // holds one has been damaged, not filled.
                directPrimarySets = mapOf("Chest" to 0)
            )
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.MALFORMED_WEEKLY_LEDGER
        )
    }

    @Test
    fun aLedgerMissingItsOwnCatalogIdentity_isReportedAsUnusable() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(press),
                reviewedPath = true,
                ledgerCatalogVersion = ""
            )
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.MALFORMED_WEEKLY_LEDGER
        )
    }

    @Test
    fun accountingThatCannotBeRepresented_isDistinctFromAnExceededAllowance() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 3)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(press),
                reviewedPath = true,
                directPrimarySets = mapOf("Chest" to Int.MAX_VALUE)
            )
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.DOSE_ACCOUNTING_OVERFLOW
        )
    }

    @Test
    fun aStateThatConfiguresNoDoseGuidance_recordsTheAbsenceWithoutRejecting() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 4)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(press),
                reviewedPath = true,
                adaptationState = AdaptationState.NEEDS_ONBOARDING
            )
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
        assertThat(result.acceptedSnapshot.doseAccounting.single().allowanceSets).isNull()
    }

    @Test
    fun theReviewedPath_recordsEveryPolicyIdentityItUsed() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 2)))

        val snapshot = validate(
            workout = plan,
            context = validatorContext(listOf(press), reviewedPath = true)
        ).acceptedSnapshot

        assertThat(snapshot.reviewedPathEnabled).isTrue()
        assertThat(snapshot.trainingPolicyVersion)
            .isEqualTo(TrainingPolicyVersion.STATE_BASED_DOSE_EFFORT_REST_V1)
        assertThat(snapshot.ledgerPolicyVersion).isEqualTo(LedgerPolicyVersion.PRIMARY_ONLY_V1)
        assertThat(snapshot.adaptationState).isEqualTo(AdaptationState.UNCALIBRATED)
        assertThat(snapshot.weekStartEpochDay).isEqualTo(MONDAY_EPOCH_DAY)
        assertThat(snapshot.timeZoneId).isEqualTo("America/Mexico_City")
    }

    @Test
    fun theSuppliedLedgerIsNeverMutatedByValidation() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 2)))
        val context = validatorContext(
            allowedExercises = listOf(press),
            reviewedPath = true,
            directPrimarySets = mapOf("Chest" to 2)
        )
        val ledgerBefore = context.trainingProgramState!!.weeklyLedger

        validate(plan, context)

        // A proposal is never completed exposure: the derived ledger must read back exactly
        // as it did before anything was proposed.
        assertThat(context.trainingProgramState!!.weeklyLedger).isEqualTo(ledgerBefore)
        assertThat(ledgerBefore.directPrimarySets).containsExactly("Chest", 2)
    }

    @Test
    fun aProgramStateWithoutTheReviewedGate_accountsNothingAndRejectsNothing() = runTest {
        // The two travel together in production. If dose accounting were gated on the
        // program state alone, a context that carried one without the reviewed eligibility
        // result would reject a legacy proposal with a reviewed-only reason.
        val plain = syntheticExerciseWithoutReviewedMetadata("plain-press")
        val plan = validatedWorkout(listOf(repetitionPlan(plain.id, targetSets = 20)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(plain),
                reviewedPath = true,
                directPrimarySets = mapOf("Chest" to 0),
                reviewedEligibilityResult = false
            )
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
        assertThat(result.acceptedSnapshot.doseAccounting).isEmpty()
        assertThat(result.acceptedSnapshot.reviewedPathEnabled).isFalse()
    }

    @Test
    fun anUnapprovedExercise_isRejectedOnTheReviewedPath() = runTest {
        val draft = syntheticDraftExercise(id = "draft-press", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(draft.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(draft), reviewedPath = true)
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.MISSING_APPROVED_METADATA
        )
    }

    @Test
    fun anExerciseWithNoReviewedBlockAtAll_isRejectedOnTheReviewedPath() = runTest {
        val plain = syntheticExerciseWithoutReviewedMetadata("plain-press")
        val plan = validatedWorkout(listOf(repetitionPlan(plain.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(plain), reviewedPath = true)
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.MISSING_APPROVED_METADATA
        )
    }

    @Test
    fun metadataAuthoredUnderAnotherReviewPolicy_isRejected() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(press),
                reviewedPath = true,
                ledgerReviewPolicyVersion = 2
            )
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.REVIEW_POLICY_VERSION_MISMATCH
        )
    }

    @Test
    fun anApprovedShapeThatDoesNotMatchWhatWasPrescribed_isRejected() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val bodyweightShape = press.copy(
            reviewedMetadata = press.reviewedMetadata!!.copy(
                prescriptionShape = PrescriptionShape.BODYWEIGHT_REPS
            )
        )
        val plan = validatedWorkout(listOf(repetitionPlan(bodyweightShape.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(bodyweightShape), reviewedPath = true),
            catalog = InMemoryExerciseCatalog(listOf(bodyweightShape))
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.PRESCRIPTION_SHAPE_MISMATCH
        )
    }

    @Test
    fun anExerciseTheEnabledPathRuledOut_isRejectedEvenWhenItIsACandidate() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(press),
                reviewedPath = true,
                ineligibleExerciseIds = setOf(press.id)
            )
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.EXPLICIT_CONSTRAINT_VIOLATED
        )
    }

    @Test
    fun approvedMetadataMissingHumanProvenance_isRejected() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val unsigned = press.copy(
            reviewedMetadata = press.reviewedMetadata!!.copy(
                reviewState = ReviewState.APPROVED,
                provenance = press.reviewedMetadata!!.provenance.copy(reviewerRole = null)
            )
        )
        val plan = validatedWorkout(listOf(repetitionPlan(unsigned.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(unsigned), reviewedPath = true),
            catalog = InMemoryExerciseCatalog(listOf(unsigned))
        )

        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.MISSING_APPROVED_METADATA
        )
    }

    @Test
    fun aTimedHoldOnTheReviewedPath_isAccountedByItsApprovedPrimary() = runTest {
        val plank = syntheticApprovedExercise(id = "plank", directPrimaryMuscle = "Core")
            .copy(type = ExerciseType.DURATION)
        val timed = plank.copy(
            reviewedMetadata = plank.reviewedMetadata!!.copy(
                prescriptionShape = PrescriptionShape.DURATION
            )
        )
        val plan = validatedWorkout(
            listOf(
                PlannedExercise(
                    exerciseId = timed.id,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.DURATION,
                        targetSets = 3,
                        targetDurationSeconds = 45,
                        restSeconds = 45
                    )
                )
            )
        )

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(timed), reviewedPath = true),
            catalog = InMemoryExerciseCatalog(listOf(timed))
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
        assertThat(result.acceptedSnapshot.doseAccounting.single().proposedSets).isEqualTo(3)
    }
}
