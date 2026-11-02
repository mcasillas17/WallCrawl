package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.*

/** All records here are FICTIONAL SOFTWARE TESTS, never clinical guidance or approval. */
class ConditionPlanningPolicyTest {
    private val policy = ConditionPlanningPolicy()
    private val text = ConditionText("FICTIONAL TEST ONLY", "SOLO PRUEBA FICTICIA")
    private val exercise = Exercise("fictional", name = "FICTIONAL", primaryMuscles = listOf("Core"), type = ExerciseType.DURATION, isStretch = true)
    private val dose = ExercisePrescription(ExerciseType.DURATION, 2, targetDurationSeconds = 20, purpose = PrescriptionPurpose.SUPPORTIVE)
    private val schedule = ConditionSchedule(2, 24, text)
    private val requirements = ConditionSupportiveRequirements(emptySet(), emptySet(), ImpactLevel.NONE, listOf(emptyList()))
    private val now = Instant.parse("2026-11-04T12:00:00Z").toEpochMilli()
    private fun history(complete: Boolean = true, pending: List<PendingConditionReassessment> = emptyList(), exposures: List<ConditionExposure> = emptyList()) =
        ConditionHistory(now, ZoneId.of("UTC"), now - 8 * 86_400_000L, complete, exposures, pending)
    private fun rule(ex: Exercise = exercise, bounds: ConditionPrescriptionBounds? = null, prescription: ExercisePrescription = dose, timing: ConditionSchedule = schedule, execution: String = "fictional-execution", needs: ConditionSupportiveRequirements = requirements) =
        ConditionRule("rule", ConditionExerciseIdentity.from(ex, "b".repeat(64)), if (bounds == null) ConditionRuleEffect.PRESCRIBE_SUPPORTIVE else ConditionRuleEffect.PERMIT_ORDINARY,
            execution, text, setOf("source"), bounds, false, if (bounds == null) prescription else null, if (bounds == null) timing else null, if (bounds == null) needs else null)
    private fun record(id: String = "fictional-condition", rules: List<ConditionRule> = listOf(rule()), version: Int = 1, inputs: List<ConditionInputDefinition> = emptyList(), answers: Map<String, String> = emptyMap()) = ConditionSupportRecord(
        id, "fictional-v1", 1, version, ConditionPopulation.ADULT_NONOPERATIVE, text, text, inputs,
        listOf(ConditionApplicabilityCase("case", answers, rules)), listOf(ConditionSource("source", "https://example.invalid/fictional", "a".repeat(64))),
        ConditionResponsePolicy(1, listOf(ConditionInputDefinition("response", text, true, mapOf("stop" to text, "okay" to text))), mapOf("response" to "stop"), mapOf("response" to "stop"), text),
        "c".repeat(64), ConditionReleaseReview(ConditionReviewState.RELEASED, "FICTIONAL", "FICTIONAL", 1, "FICTIONAL", "c".repeat(64), setOf("source")))
    private fun resolve(records: List<ConditionSupportRecord> = listOf(record()), profile: UserProfile = UserProfile(activeConditions = records.map { SelectedCondition(it.conditionId, 1, emptyMap()) }), exercises: List<Exercise> = listOf(exercise), history: ConditionHistory = history()) =
        policy.resolve(profile, exercises, exercises.associateBy { it.id }, ConditionSupportCatalog("fictional", records.associateBy { it.conditionId }), history)
    private fun blocked(result: ConditionPlanningResult) { assertThat(result).isInstanceOf(ConditionPlanningResult.Blocked::class.java) }

    @Test fun noSelectionsAreNotApplicableEvenWithoutHistory() { assertThat(resolve(profile = UserProfile(), history = history(false))).isEqualTo(ConditionPlanningResult.NotApplicable) }
    @Test fun unknownAndUnknownPolicyAreBlocked() {
        blocked(resolve(records = emptyList(), profile = UserProfile(activeConditions = listOf(SelectedCondition("withdrawn", 1, emptyMap())))))
        blocked(resolve(listOf(record(version = 2))))
    }
    @Test fun requiredContextAndUnknownResponsesAreBlockedButResponseOnlyIsOptional() {
        val input = ConditionInputDefinition("stage", text, true, mapOf("known" to text))
        blocked(resolve(listOf(record(inputs = listOf(input), answers = mapOf("stage" to "known")))))
        for (answers in listOf(mapOf("response" to "stop"), mapOf("response" to "invented"), mapOf("unknown" to null))) {
            blocked(resolve(profile = UserProfile(activeConditions = listOf(SelectedCondition("fictional-condition", 1, answers)))))
        }
        assertThat(resolve()).isInstanceOf(ConditionPlanningResult.Ready::class.java)
    }
    @Test fun contentChangeAndIncompleteHistoryBlock() {
        blocked(resolve(exercises = listOf(exercise.copy(name = "CHANGED"))))
        blocked(resolve(history = history(false)))
    }
    @Test fun sharedSupportDeduplicatesAndRetainsBothSources() {
        val ready = resolve(listOf(record(), record("second"))) as ConditionPlanningResult.Ready
        assertThat(ready.supportive).hasSize(1)
        assertThat(ready.supportive.single().sourceRuleReferences).hasSize(2)
        assertThat(ready.context.appliedRules).hasSize(2)
        assertThat(ready.context.consumedScheduleEvidence).hasSize(2)
        assertThat(exercise.reviewedMetadata).isNull()
    }
    @Test fun sharedSupportConflictingDoseExecutionOrScheduleBlocks() {
        listOf(rule(prescription = dose.copy(targetSets = 3)), rule(execution = "different"), rule(timing = ConditionSchedule(3, 24, text))).forEach {
            blocked(resolve(listOf(record(), record("second", listOf(it)))))
        }
    }
    @Test fun positiveCompatibilityRequiredForEveryCondition() {
        val other = exercise.copy(id = "other")
        blocked(resolve(listOf(record(), record("second", listOf(rule(other)))), exercises = listOf(exercise, other)))
    }
    @Test fun explicitExclusionEquipmentJointImpactAndCapabilityWin() {
        blocked(resolve(profile = UserProfile(activeConditions = listOf(SelectedCondition("fictional-condition", 1, emptyMap())), excludedExerciseIds = listOf(exercise.id))))
        val wall = exercise.copy(listedEquipment = listOf(StandardEquipment.WALL))
        blocked(resolve(listOf(record(rules = listOf(rule(wall)))), exercises = listOf(wall)))
        val needs = ConditionSupportiveRequirements(setOf(MovementCapabilityType.entries.first()), emptySet(), ImpactLevel.HIGH, listOf(emptyList()))
        val base = UserProfile(activeConditions = listOf(SelectedCondition("fictional-condition", 1, emptyMap())))
        blocked(resolve(listOf(record(rules = listOf(rule(needs = needs)))), base.copy(trainingConstraints = setOf(TrainingConstraint.LOW_IMPACT_ONLY))))
        blocked(resolve(profile = base.copy(trainingConstraints = setOf(TrainingConstraint.entries.first { it != TrainingConstraint.LOW_IMPACT_ONLY }))))
        blocked(resolve(listOf(record(rules = listOf(rule(needs = needs)))), base.copy(movementCapabilities = MovementCapabilities.from(MovementCapabilityType.entries.associateWith { CapabilityLevel.AVOID }))))
    }
    @Test fun pendingBlocksDespiteChangedContextAndDeletedProvenance() {
        val marker = PendingConditionReassessment("default_user", "fictional-condition", "fictional", "event", "a".repeat(64), 1, now - 1, null)
        blocked(resolve(history = history(pending = listOf(marker))))
        blocked(resolve(profile = UserProfile(activeConditions = listOf(SelectedCondition("fictional-condition", 1, mapOf("response" to "okay")))), history = history(pending = listOf(marker))))
    }
    @Test fun scheduleNotDueOmitsSupportButKeepsEvidence() {
        val ready = resolve(history = history(exposures = listOf(ConditionExposure("manual", exercise.id, now - 1)))) as ConditionPlanningResult.Ready
        assertThat(ready.supportive).isEmpty()
        assertThat(ready.context.consumedScheduleEvidence).hasSize(1)
    }
    @Test fun ordinaryIntersectionAppliesAndValidationRejectsOutsideBounds() {
        val ex = ordinary()
        val records = listOf(record(rules = listOf(rule(ex, ConditionPrescriptionBounds(minSets = 2, maxSets = 4, maxReps = 10)))), record("second", listOf(rule(ex, ConditionPrescriptionBounds(maxSets = 3, minReps = 8)))))
        val ready = resolve(records, exercises = listOf(ex)) as ConditionPlanningResult.Ready
        val base = ExercisePrescription(ExerciseType.WEIGHT_REPS, 5, RepRange(6, 12))
        val applied = policy.applyOrdinary(ex, base, ready) as ConditionPrescriptionResult.Applied
        assertThat(applied.prescription.targetSets).isEqualTo(3)
        assertThat(applied.decisions).hasSize(1)
        assertThat(applied.decisions.single().sourceRuleReferences).hasSize(2)
        assertThat(applied.prescription.repRange).isEqualTo(RepRange(8, 10))
        assertThat(applied.prescription.targetWeight).isNull()
        assertThat(policy.validate(PlannedExercise(ex.id, base), ready)).isNotEmpty()
        assertThat(policy.validate(PlannedExercise(ex.id, applied.prescription), ready)).isEmpty()
    }
    @Test fun conflictingOrdinaryBoundsBlockAndUnassessedOrdinaryCannotEnter() {
        val ex = ordinary()
        blocked(resolve(listOf(record(rules = listOf(rule(ex, ConditionPrescriptionBounds(minSets = 4)))), record("second", listOf(rule(ex, ConditionPrescriptionBounds(maxSets = 3))))), exercises = listOf(ex)))
        val ready = resolve(listOf(record(rules = listOf(rule(ex, ConditionPrescriptionBounds())))), exercises = listOf(ex, ex.copy(id = "unassessed"))) as ConditionPlanningResult.Ready
        assertThat(ready.ordinaryRules.keys).containsExactly(ex.id)
        assertThat(policy.applyOrdinary(ex.copy(id = "unassessed"), ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, RepRange(8, 10)), ready)).isInstanceOf(ConditionPrescriptionResult.Blocked::class.java)
    }
    @Test fun requiredLoadNeverFillsUnconfirmedValue() {
        val ex = ordinary()
        val ready = resolve(listOf(record(rules = listOf(rule(ex, ConditionPrescriptionBounds(minLoadKg = 2.0))))), exercises = listOf(ex)) as ConditionPlanningResult.Ready
        assertThat(policy.applyOrdinary(ex, ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, RepRange(8, 10)), ready)).isInstanceOf(ConditionPrescriptionResult.Blocked::class.java)
    }
    @Test fun conflictingDefinitionsAndAmbiguousCasesCannotAuthorizePlanning() {
        val input = ConditionInputDefinition("response", text, true, mapOf("different" to text))
        blocked(resolve(listOf(record(inputs = listOf(input), answers = mapOf("response" to "different")))))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            val record = record()
            ConditionSupportRecord(record.conditionId, record.contentVersion, 1, 1, record.population, text, text, emptyList(),
                listOf(ConditionApplicabilityCase("one", emptyMap(), listOf(rule())), ConditionApplicabilityCase("two", emptyMap(), listOf(rule()))),
                record.sources, record.responsePolicy, record.contentSha256, record.review)
        }
    }
    @Test fun explicitConditionExclusionOverridesAnotherSupportiveRule() {
        val excluded = ConditionRule("excluded", ConditionExerciseIdentity.from(exercise, "b".repeat(64)), ConditionRuleEffect.EXCLUDE,
            "fictional-execution", text, setOf("source"), null, false, null, null)
        blocked(resolve(listOf(record(), record("second", listOf(excluded)))))
    }
    @Test fun supportiveValidationRequiresExactDoseAndDueState() {
        val ready = resolve() as ConditionPlanningResult.Ready
        assertThat(policy.validate(PlannedExercise(exercise.id, dose), ready)).isEmpty()
        assertThat(policy.validate(PlannedExercise(exercise.id, dose.copy(targetDurationSeconds = 21)), ready)).isNotEmpty()
        val notDue = resolve(history = history(exposures = listOf(ConditionExposure("manual", exercise.id, now - 1)))) as ConditionPlanningResult.Ready
        assertThat(policy.validate(PlannedExercise(exercise.id, dose), notDue)).isNotEmpty()
    }
    @Test fun ordinaryEligibilityDoesNotBorrowConditionApproval() {
        val unreviewed = ordinary().copy(reviewedMetadata = null)
        val ready = resolve(listOf(record(rules = listOf(rule(unreviewed, ConditionPrescriptionBounds())))), exercises = listOf(unreviewed)) as ConditionPlanningResult.Ready
        assertThat(ready.ordinaryRules).isEmpty()
    }
    @Test fun ordinaryLoadsRespectProfileUnitsAndKeepUnboundedConfirmationExact() {
        val ex = ordinary()
        val profile = UserProfile(activeConditions = listOf(SelectedCondition("fictional-condition", 1, emptyMap())), preferredUnit = WeightUnit.LBS)
        val ready = resolve(listOf(record(rules = listOf(rule(ex, ConditionPrescriptionBounds(maxLoadKg = 5.0))))), profile, listOf(ex)) as ConditionPlanningResult.Ready
        val base = ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, RepRange(8, 10), targetWeight = 20.0)
        val applied = policy.applyOrdinary(ex, base, ready) as ConditionPrescriptionResult.Applied
        assertThat(applied.prescription.targetWeight).isWithin(0.00001).of(convertWeight(5.0, WeightUnit.KG, WeightUnit.LBS))
        assertThat(policy.validate(PlannedExercise(ex.id, applied.prescription), ready)).isEmpty()
        val precise = base.copy(targetWeight = 3.141592653589793)
        assertThat((policy.applyOrdinary(ex, precise, ready) as ConditionPrescriptionResult.Applied).prescription.targetWeight).isEqualTo(precise.targetWeight)
    }
    @Test fun constrainedRestDoesNotBecomeANewUserPreference() {
        val ex = ordinary()
        val ready = resolve(listOf(record(rules = listOf(rule(ex, ConditionPrescriptionBounds(minRestSeconds = 60))))), exercises = listOf(ex)) as ConditionPlanningResult.Ready
        val base = ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, RepRange(8, 10), restSeconds = 30,
            restClass = RestClass.entries.first(), restTargetSource = RestTargetSource.USER_PREFERENCE)
        val applied = policy.applyOrdinary(ex, base, ready) as ConditionPrescriptionResult.Applied
        assertThat(applied.prescription.restSeconds).isEqualTo(60)
        assertThat(applied.prescription.userRestPreferenceOrNull()).isNull()
    }
    @Test fun unresolvedFixedAnchorCannotBeWaivedBySupportiveRequirements() {
        val ex = exercise.copy(id = "banded-row")
        blocked(resolve(listOf(record(rules = listOf(rule(ex)))), exercises = listOf(ex)))
    }
    @Test fun pendingCannotBeHiddenByRemovingLastConditionOrSelectingAnother() {
        val marker = PendingConditionReassessment("default_user", "removed", "fictional", "event", "a".repeat(64), 1, now - 1, null)
        blocked(resolve(profile = UserProfile(), history = history(pending = listOf(marker))))
        blocked(resolve(history = history(pending = listOf(marker))))
    }
    private fun ordinary() = exercise.copy(type = ExerciseType.WEIGHT_REPS, isStretch = false, reviewedMetadata = ReviewedExerciseMetadata(
        ReviewState.APPROVED, "Core", emptySet(), MovementPattern.CORE, ComplexityTier.FOUNDATIONAL, "fictional", PrescriptionShape.WEIGHT_REPS,
        emptyList(), emptyList(), emptySet(), SupportRequirement.SUPPORTED, ImpactLevel.NONE, listOf(emptyList()),
        ReviewProvenance("FICTIONAL", "FICTIONAL TEST ONLY", 1, 1, 1), emptySet()))
}
