package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.*

class ConditionRecommendationValidationTest {
    private val fixture = ConditionPlannerFixtures
    private fun context() = fixture.context().let { c ->
        val reviewed = validatorContext(listOf(fixture.ordinary), profile = c.userProfile, reviewedPath = true)
        c.copy(allowedExercises = listOf(fixture.ordinary), automaticEligibilityResult = reviewed.automaticEligibilityResult,
            trainingProgramState = reviewed.trainingProgramState!!.copy(policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V2))
    }
    private suspend fun validate(plan: GeneratedWorkout, context: WorkoutGenerationContext, repair: Boolean = false,
        catalog: List<Exercise> = listOf(fixture.ordinary, fixture.supportive)) =
        ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(catalog)))
            .validate(plan, context, repair)
    private fun changed(plan: GeneratedWorkout, exercises: List<PlannedExercise>) = plan.copy(exercises = exercises,
        estimatedDurationMinutes = WorkoutDurationEstimator.estimateMinutes(exercises))

    @Test fun separatePurposeReceiptsAndFrozenSnapshotAreAccepted() = runTest {
        val c = context()
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        val result = validate(plan, c)
        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
        val snapshot = (result as ProgramValidationResult.Valid).snapshot
        assertThat(snapshot.conditionContext).isSameInstanceAs((c.conditionPlanning as ConditionPlanningResult.Ready).context)
        assertThat(snapshot.asRecord("session", fixture.now).conditionContextDigest).isEqualTo(snapshot.conditionContext!!.canonicalDigest)
        assertThat(snapshot.doseAccounting.map { it.muscle }).containsExactly(StandardMuscles.CHEST)
        assertThat(snapshot.progression.map { it.exerciseId }).containsExactly(fixture.ordinary.id)
    }

    @Test fun missingSupportAndMissingOrForgedDecisionsRefuse() = runTest {
        val c = fixture.context()
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        val missing = changed(plan, plan.exercises.filter { it.prescription.purpose == PrescriptionPurpose.ORDINARY })
            .copy(conditionDecisions = plan.conditionDecisions.filter { it.purpose == PrescriptionPurpose.ORDINARY })
        val forged = plan.conditionDecisions.map { ConditionPrescriptionDecision(it.exerciseIdentity, it.purpose,
            "forged-execution", it.sourceRuleReferences) }
        for (bad in listOf(missing, plan.copy(conditionDecisions = emptyList()), plan.copy(conditionDecisions = forged))) {
            assertThat(validate(bad, c, true)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        }
    }

    @Test fun changedPurposeCannotEvadeMembershipOrReview() = runTest {
        val c = fixture.context()
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        for (index in plan.exercises.indices) {
            val bad = changed(plan, plan.exercises.mapIndexed { i, p -> if (i == index) p.copy(prescription = p.prescription.copy(
                purpose = if (p.prescription.purpose == PrescriptionPurpose.ORDINARY) PrescriptionPurpose.SUPPORTIVE else PrescriptionPurpose.ORDINARY)) else p })
            assertThat(validate(bad, c)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        }
    }

    @Test fun doseBoundsAndUnclampedSecondsBudgetRefuseEvenWithRepair() = runTest {
        val c = fixture.context()
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        for (index in plan.exercises.indices) {
            val bad = changed(plan, plan.exercises.mapIndexed { i, p -> if (i == index) p.copy(prescription = p.prescription.copy(targetSets = 1)) else p })
            assertThat(validate(bad, c, true)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        }
        assertThat(validate(plan, c.copy(preferredWorkoutDurationMinutes = 1), true)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        val truncatedMinutes = (WorkoutDurationEstimator.estimateSeconds(plan.exercises) / 60L).toInt()
        assertThat(WorkoutDurationEstimator.estimateSeconds(plan.exercises)).isGreaterThan(truncatedMinutes * 60L)
        assertThat(validate(plan, c.copy(preferredWorkoutDurationMinutes = truncatedMinutes), true)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
    }

    @Test fun duplicateIdsRefuseEvenWhenConstraintIsDisabled() = runTest {
        val c = fixture.context().copy(programConstraints = SessionProgramConstraints(uniqueExerciseIds = false))
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        assertThat(validate(changed(plan, plan.exercises + plan.exercises.last()), c)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
    }

    @Test fun staleScheduleAndMalformedRuleReferencesRefuse() = runTest {
        val c = fixture.context()
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        val ready = c.conditionPlanning as ConditionPlanningResult.Ready
        val frozen = ready.context
        val malformed = SessionConditionContext(1, 1, frozen.selectedConditions, frozen.sourceSupportVersions,
            frozen.appliedRules.map { it.copy(reference = it.reference.copy(ruleId = "missing-rule")) },
            frozen.consumedScheduleEvidence, emptyList())
        val changed = c.copy(conditionPlanning = ConditionPlanningResult.Ready(ready.ordinaryRules, ready.supportive, malformed))
        assertThat(validate(plan, changed)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        val history = c.conditionHistory!!
        val performed = ConditionHistory(history.asOfTimestamp, history.zoneId, history.windowStartTimestamp, true,
            listOf(ConditionExposure("new-session", fixture.supportive.id, fixture.now - 1)), emptyList())
        assertThat(validate(plan, c.copy(conditionHistory = performed))).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        assertThat(validate(plan, c.copy(trainingFrequencyDaysPerWeek = 1))).isInstanceOf(ProgramValidationResult.Invalid::class.java)
    }

    @Test fun pendingAfterConditionRemovalStillBlocks() = runTest {
        val c = fixture.context()
        val history = c.conditionHistory!!
        val marker = PendingConditionReassessment(c.userProfile.id, "fictional-condition", fixture.supportive.id,
            "pain-event", "a".repeat(64), 1, fixture.now - 1)
        val removed = c.copy(userProfile = c.userProfile.copy(activeConditions = emptyList()),
            conditionPlanning = ConditionPlanningResult.NotApplicable,
            conditionHistory = ConditionHistory(fixture.now, history.zoneId, history.windowStartTimestamp, true, emptyList(), listOf(marker)))
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        assertThat(validate(plan, removed)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
    }
    @Test fun newlyInfeasibleConditionMinimumReturnsInvalidInsteadOfThrowing() = runTest {
        val c = context()
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        val state = c.trainingProgramState!!
        val exhausted = c.copy(trainingProgramState = state.copy(weeklyLedger = state.weeklyLedger.copy(directPrimarySets = mapOf(StandardMuscles.CHEST to 5))))
        assertThat(validate(plan, exhausted, true)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
    }

    @Test fun supportiveStrengthMetadataNeverSuppliesFocusPatternsFamiliesOrCredit() = runTest {
        val support = fixture.ordinary.copy(id = "supportive", primaryMuscles = listOf(StandardMuscles.CALVES),
            reviewedMetadata = fixture.ordinary.reviewedMetadata!!.copy(directPrimaryMuscle = StandardMuscles.CALVES,
                movementPattern = MovementPattern.VERTICAL_PULL))
        val dose = ExercisePrescription(ExerciseType.BODYWEIGHT_REPS, 2, RepRange(4, 6), purpose = PrescriptionPurpose.SUPPORTIVE)
        val c = fixture.context(supportExercise = support, supportiveDose = dose).let { original ->
            val reviewed = validatorContext(listOf(fixture.ordinary, support), profile = original.userProfile, reviewedPath = true)
            original.copy(automaticEligibilityResult = reviewed.automaticEligibilityResult, trainingProgramState = reviewed.trainingProgramState)
        }
        val catalog = listOf(fixture.ordinary, support)
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        val accepted = validate(plan, c, catalog = catalog) as ProgramValidationResult.Valid
        assertThat(accepted.snapshot.doseAccounting.map { it.muscle }).containsExactly(StandardMuscles.CHEST)
        val familyConstraint = c.copy(programConstraints = SessionProgramConstraints(uniqueProgressionFamilies = true))
        assertThat(validate(plan, familyConstraint, catalog = catalog)).isInstanceOf(ProgramValidationResult.Valid::class.java)
        val patternConstraint = c.copy(programConstraints = SessionProgramConstraints(requiredMovementPatterns = setOf(MovementPattern.VERTICAL_PULL)))
        val patternResult = validate(plan, patternConstraint, catalog = catalog) as ProgramValidationResult.Invalid
        assertThat(patternResult.violations.map { it.code }).contains(ProgramViolationCode.MISSING_REQUIRED_MOVEMENT_PATTERN)
        val focusResult = validate(plan.copy(focusMuscles = listOf(StandardMuscles.CALVES)), c, catalog = catalog) as ProgramValidationResult.Invalid
        assertThat(focusResult.violations.map { it.code }).contains(ProgramViolationCode.UNSUPPORTED_WORKOUT_FOCUS)
    }

    @Test fun allowanceRepairHonorsConditionMinimaAndLeavesSupportExact() = runTest {
        val second = fixture.ordinary.copy(id = "second")
        val catalog = listOf(fixture.ordinary, second, fixture.supportive)
        for (minimum in listOf(1, 2)) {
            val original = fixture.context(ordinaryExercises = listOf(fixture.ordinary, second), bounds = ConditionPrescriptionBounds(minSets = minimum))
            val reviewed = validatorContext(listOf(fixture.ordinary, second), profile = original.userProfile,
                reviewedPath = true, directPrimarySets = mapOf(StandardMuscles.CHEST to 3))
            val c = original.copy(automaticEligibilityResult = reviewed.automaticEligibilityResult,
                trainingProgramState = reviewed.trainingProgramState!!.copy(policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V2))
            val plan = FakeWorkoutPlanner().generateWorkout(c)
            assertThat(plan.exercises.filter { it.prescription.purpose == PrescriptionPurpose.ORDINARY }).hasSize(2)
            val result = validate(plan, c, true, catalog)
            if (minimum == 2) assertThat(result).isInstanceOf(ProgramValidationResult.Invalid::class.java) else {
                val accepted = result as ProgramValidationResult.Valid
                assertThat(accepted.snapshot.outcome).isEqualTo(RecommendationOutcome.REPAIRED)
                assertThat(accepted.workout.exercises.last()).isEqualTo(plan.exercises.last())
                assertThat(accepted.workout.exercises.take(2).map { it.targetSets }).containsExactly(2, 1).inOrder()
                assertThat(validate(accepted.workout, c, catalog = catalog)).isInstanceOf(ProgramValidationResult.Valid::class.java)
            }
        }
    }

    @Test fun durationReducedAdvancedReceiptUsesFinalAuthorizedPrescription() = runTest {
        val exercise = syntheticApprovedExercise(id = "ordinary", directPrimaryMuscle = StandardMuscles.CHEST)
        val original = fixture.context(ordinaryExercises = listOf(exercise), allowProgression = true,
            bounds = ConditionPrescriptionBounds(minSets = 1),
            profile = UserProfile(preferredUnit = WeightUnit.KG, confirmedStartingLoads = mapOf(exercise.id to 10.0),
                activeConditions = listOf(SelectedCondition("fictional-condition", 1, emptyMap()))))
        val reviewed = validatorContext(listOf(exercise), profile = original.userProfile, reviewedPath = true)
        val base = original.copy(automaticEligibilityResult = reviewed.automaticEligibilityResult,
            trainingProgramState = reviewed.trainingProgramState!!.copy(policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V2))
        val factory = DefaultExercisePrescriptionFactory()
        val initial = factory.createDecision(exercise, base)
        val c = base.copy(progressionHistory = listOf(progressionSession("one", exercise.id, initial.prescription, 10_000),
            progressionSession("two", exercise.id, initial.prescription, 20_000)), historyAsOfTimestamp = 30_000)
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        val authorized = plan.progressionDecisions.single()
        assertThat(authorized.reason).isEqualTo(ProgressionReason.ADVANCED)
        val reduced = authorized.copy(reason = ProgressionReason.HOLD_VALIDATION_REPAIR, axis = null,
            referencePrescription = authorized.prescription, prescription = authorized.prescription.copy(targetSets = 1), sourceSessionIds = emptyList())
        val repaired = changed(plan, plan.exercises.map { if (it.exerciseId == exercise.id) it.copy(prescription = reduced.prescription) else it })
            .copy(progressionDecisions = listOf(reduced))
        val catalog = listOf(exercise, fixture.supportive)
        assertThat(validate(repaired, c, catalog = catalog)).isInstanceOf(ProgramValidationResult.Valid::class.java)
        for (forged in listOf(reduced.copy(prescription = authorized.prescription), reduced.copy(sourceSessionIds = listOf("one")), reduced.copy(
            referencePrescription = authorized.referencePrescription, prescription = authorized.referencePrescription.copy(targetSets = 1)))) {
            val bad = changed(plan, plan.exercises.map { if (it.exerciseId == exercise.id) it.copy(prescription = forged.prescription) else it })
                .copy(progressionDecisions = listOf(forged))
            assertThat(validate(bad, c, catalog = catalog)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        }
    }

    @Test fun staleAllowedCatalogContentCannotSupplyFocusOrReview() = runTest {
        val c = fixture.context()
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        val changed = c.copy(allowedExercises = c.allowedExercises.map {
            if (it.id == fixture.ordinary.id) it.copy(name = "CHANGED CATALOG CONTENT") else it
        })
        assertThat(validate(plan, changed)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
    }

}
