package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * The single deterministic repair pass.
 *
 * Repair may only reduce sets so aggregate accounting holds. Everything it must not touch
 * is asserted here, because a repair that quietly widened eligibility or invented a load
 * would be far worse than a rejection.
 */
class ProgramValidatorRepairTest {

    @Test
    fun anOverAllowanceProposal_isRepairedByReducingSetsInRecommendationOrder() = runTest {
        val first = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val second = syntheticApprovedExercise(id = "press-b", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(
            listOf(
                repetitionPlan(first.id, targetSets = 4),
                repetitionPlan(second.id, targetSets = 4)
            )
        )

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(first, second), reviewedPath = true),
            allowRepair = true
        )

        val repaired = (result as ProgramValidationResult.Valid).workout
        // Six sets remain, allocated in order: the first exercise keeps four, the second
        // takes what is left rather than being dropped.
        assertThat(repaired.exercises.map { it.targetSets }).containsExactly(4, 2).inOrder()
        assertThat(result.acceptedSnapshot.outcome).isEqualTo(RecommendationOutcome.REPAIRED)
        assertThat(result.acceptedSnapshot.reasonCodes)
            .containsExactly(ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED)
    }

    @Test
    fun repair_recomputesTheDurationItJustChanged() = runTest {
        val first = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val second = syntheticApprovedExercise(id = "press-b", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(
            listOf(
                repetitionPlan(first.id, targetSets = 4),
                repetitionPlan(second.id, targetSets = 4)
            )
        )

        val repaired = (
            validate(
                workout = plan,
                context = validatorContext(listOf(first, second), reviewedPath = true),
                allowRepair = true
            ) as ProgramValidationResult.Valid
            ).workout

        assertThat(repaired.estimatedDurationMinutes)
            .isEqualTo(WorkoutDurationEstimator.estimateMinutes(repaired.exercises))
        assertThat(repaired.estimatedDurationMinutes)
            .isLessThan(plan.estimatedDurationMinutes)
    }

    @Test
    fun repair_neverChangesSelectionLoadsEffortOrRest() = runTest {
        val first = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val second = syntheticApprovedExercise(id = "press-b", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(
            listOf(
                repetitionPlan(first.id, targetSets = 4, targetWeight = 100.0),
                repetitionPlan(second.id, targetSets = 4, restSeconds = 120)
            )
        )
        val context = validatorContext(
            allowedExercises = listOf(first, second),
            reviewedPath = true,
            profile = UserProfile(
                id = "profile-under-test",
                revision = 1,
                confirmedStartingLoads = mapOf(first.id to 100.0)
            )
        )

        val repaired = (
            validate(workout = plan, context = context, allowRepair = true)
                as ProgramValidationResult.Valid
            ).workout

        assertThat(repaired.exercises.map { it.exerciseId })
            .containsExactly("press-a", "press-b").inOrder()
        assertThat(repaired.exercises[0].targetWeight).isEqualTo(100.0)
        assertThat(repaired.exercises[1].targetWeight).isNull()
        assertThat(repaired.exercises[1].prescription.restSeconds).isEqualTo(120)
        assertThat(repaired.exercises.map { it.prescription.repRange })
            .isEqualTo(plan.exercises.map { it.prescription.repRange })
    }

    @Test
    fun repair_failsClosedWhenTheRemainderCannotGiveEveryExerciseOneSet() = runTest {
        val first = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val second = syntheticApprovedExercise(id = "press-b", directPrimaryMuscle = "Chest")
        val third = syntheticApprovedExercise(id = "press-c", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(
            listOf(
                repetitionPlan(first.id, targetSets = 2),
                repetitionPlan(second.id, targetSets = 2),
                repetitionPlan(third.id, targetSets = 2)
            )
        )

        // Only two sets remain for three exercises, so reducing cannot succeed and dropping
        // an exercise is not something repair is allowed to do.
        val result = validate(
            workout = plan,
            context = validatorContext(
                allowedExercises = listOf(first, second, third),
                reviewedPath = true,
                directPrimarySets = mapOf("Chest" to 4)
            ),
            allowRepair = true
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        assertThat(result.codes()).containsExactly(
            ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED
        )
    }

    @Test
    fun repair_isNotAttemptedForAViolationItWouldHaveToWeakenARuleToFix() = runTest {
        val allowed = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val outside = syntheticApprovedExercise(id = "press-b", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(outside.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(allowed), reviewedPath = true),
            catalog = InMemoryExerciseCatalog(listOf(allowed, outside)),
            allowRepair = true
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        assertThat(result.codes()).contains(ProgramViolationCode.NOT_IN_CANDIDATE_SET)
    }

    @Test
    fun repair_isNotAttemptedForAnUntraceableLoad() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(
            listOf(repetitionPlan(press.id, targetSets = 9, targetWeight = 135.0))
        )

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(press), reviewedPath = true),
            allowRepair = true
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        assertThat(result.codes()).contains(ProgramViolationCode.UNTRACEABLE_LOAD)
    }

    @Test
    fun repairIsDisabledAtStart_soADisplayedPlanIsNeverSilentlyReplaced() = runTest {
        val first = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val second = syntheticApprovedExercise(id = "press-b", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(
            listOf(
                repetitionPlan(first.id, targetSets = 4),
                repetitionPlan(second.id, targetSets = 4)
            )
        )

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(first, second), reviewedPath = true),
            allowRepair = false
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Invalid::class.java)
    }

    @Test
    fun aValidProposal_isReturnedUnchangedEvenWhenRepairIsPermitted() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        val plan = validatedWorkout(listOf(repetitionPlan(press.id, targetSets = 2)))

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(press), reviewedPath = true),
            allowRepair = true
        )

        assertThat((result as ProgramValidationResult.Valid).workout).isEqualTo(plan)
        assertThat(result.acceptedSnapshot.outcome).isEqualTo(RecommendationOutcome.VALID)
    }

    @Test
    fun onlyOnePassRuns_soARepairThatIsStillInvalidIsNotRetried() = runTest {
        val press = syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest")
        // Over the allowance and carrying an invented load: repair can address the first but
        // never the second, so the result stays rejected after exactly one pass.
        val plan = validatedWorkout(
            listOf(repetitionPlan(press.id, targetSets = 9, targetWeight = 135.0))
        )

        val result = validate(
            workout = plan,
            context = validatorContext(listOf(press), reviewedPath = true),
            allowRepair = true
        )

        assertThat(result).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        // The load is still untraceable after the one pass, and nothing retried it.
        assertThat(result.codes()).contains(ProgramViolationCode.UNTRACEABLE_LOAD)
    }
}
