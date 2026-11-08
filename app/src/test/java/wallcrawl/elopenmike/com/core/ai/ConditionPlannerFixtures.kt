package wallcrawl.elopenmike.com.core.ai

import java.time.Instant
import java.time.ZoneId
import wallcrawl.elopenmike.com.core.model.*

/** FICTIONAL SOFTWARE TEST DATA ONLY; no clinical guidance or release. */
internal object ConditionPlannerFixtures {
    val text = ConditionText("FICTIONAL TEST", "PRUEBA FICTICIA")
    val ordinary = Exercise("ordinary", name = "FICTIONAL", primaryMuscles = listOf(StandardMuscles.CHEST),
        type = ExerciseType.BODYWEIGHT_REPS, reviewedMetadata = ReviewedExerciseMetadata(
            ReviewState.APPROVED, StandardMuscles.CHEST, emptySet(), MovementPattern.HORIZONTAL_PUSH,
            ComplexityTier.FOUNDATIONAL, "fictional", PrescriptionShape.BODYWEIGHT_REPS,
            emptyList(), emptyList(), emptySet(), SupportRequirement.SUPPORTED, ImpactLevel.NONE,
            listOf(emptyList()), ReviewProvenance("FICTIONAL", "FICTIONAL TEST ONLY", 1, 1, 1), emptySet()))
    val supportive = Exercise("supportive", name = "FICTIONAL", primaryMuscles = listOf(StandardMuscles.CALVES),
        type = ExerciseType.DURATION, isStretch = true)
    val dose = ExercisePrescription(ExerciseType.DURATION, 2, targetDurationSeconds = 20,
        restSeconds = 17, purpose = PrescriptionPurpose.SUPPORTIVE)
    val now = Instant.parse("2026-11-04T12:00:00Z").toEpochMilli()

    fun context(
        ordinaryExercises: List<Exercise> = listOf(ordinary),
        supportExercise: Exercise = supportive,
        supportiveDose: ExercisePrescription = dose,
        bounds: ConditionPrescriptionBounds = ConditionPrescriptionBounds(minSets = 2, maxSets = 3),
        schedule: ConditionSchedule = ConditionSchedule(2, 24, text),
        profile: UserProfile = UserProfile(preferredUnit = WeightUnit.KG,
            activeConditions = listOf(SelectedCondition("fictional-condition", 1, emptyMap()))),
        exposures: List<ConditionExposure> = emptyList(),
        execution: String = "ordinary-execution",
        allowProgression: Boolean = false,
        supportContentSha256: String = "c".repeat(64)
    ): WorkoutGenerationContext {
        val record = record(ordinaryExercises, supportExercise, supportiveDose, bounds, schedule,
            execution, allowProgression, supportContentSha256)
        val support = ConditionSupportCatalog("fictional-v1", mapOf(record.conditionId to record))
        val history = ConditionHistory(now, ZoneId.of("UTC"), now - 8 * 86_400_000L, true, exposures, emptyList())
        val all = ordinaryExercises + supportExercise
        val ready = ConditionPlanningPolicy().resolve(profile, all, all.associateBy { it.id }, support, history)
        check(ready is ConditionPlanningResult.Ready)
        return WorkoutGenerationContext(userProfile = profile, allowedExercises = all, conditionPlanning = ready,
            conditionSupport = support, conditionHistory = history, regenerationIndex = 0)
    }

    private fun record(
        ordinaryExercises: List<Exercise>, supportExercise: Exercise,
        supportiveDose: ExercisePrescription = dose,
        bounds: ConditionPrescriptionBounds = ConditionPrescriptionBounds(minSets = 2, maxSets = 3),
        schedule: ConditionSchedule = ConditionSchedule(2, 24, text),
        execution: String = "ordinary-execution", allowProgression: Boolean = false,
        supportContentSha256: String = "c".repeat(64), conditionId: String = "fictional-condition",
        inputs: List<ConditionInputDefinition> = emptyList(), requiredAnswers: Map<String, String> = emptyMap(),
        requirements: ConditionSupportiveRequirements = ConditionSupportiveRequirements(emptySet(), emptySet(), ImpactLevel.NONE, listOf(emptyList()))
    ): ConditionSupportRecord {
        val rules = ordinaryExercises.map { exercise -> ConditionRule("rule-${exercise.id}",
            ConditionExerciseIdentity.from(exercise, "b".repeat(64)), ConditionRuleEffect.PERMIT_ORDINARY,
            execution, text, setOf("source"), bounds, allowProgression, null, null) } +
            ConditionRule("support-rule", ConditionExerciseIdentity.from(supportExercise, "b".repeat(64)),
                ConditionRuleEffect.PRESCRIBE_SUPPORTIVE, "support-execution", text, setOf("source"), null, false,
                supportiveDose, schedule, requirements)
        return ConditionSupportRecord(conditionId, "fictional-v1", 1, 1,
            ConditionPopulation.ADULT_NONOPERATIVE, text, text, inputs,
            listOf(ConditionApplicabilityCase("case", requiredAnswers, rules)),
            listOf(ConditionSource("source", "https://example.invalid/fictional", "a".repeat(64))),
            ConditionResponsePolicy(1, listOf(ConditionInputDefinition("response", text, true, mapOf("stop" to text))), mapOf("response" to "stop"), mapOf("response" to "stop"), text), supportContentSha256,
            ConditionReleaseReview(ConditionReviewState.RELEASED, "FICTIONAL", "FICTIONAL", 1, "FICTIONAL", supportContentSha256, setOf("source")))
    }

    fun corpusContext(base: WorkoutGenerationContext, scenario: PlannerFixtureConditions,
                      catalog: List<Exercise>, now: Long): WorkoutGenerationContext {
        val profile = base.userProfile.copy(activeConditions = scenario.selections)
        val supportExercise = catalog.single { it.id == "butterfly-stretch" }
        val records = if (scenario.supportVariant == FixtureSupportVariant.WITHDRAWN) emptyList() else
            listOf("fictional-condition", "fictional-second").map { id ->
                record(base.allowedExercises, supportExercise,
                    supportiveDose = when {
                        scenario.supportVariant == FixtureSupportVariant.LONG -> dose.copy(targetDurationSeconds = 1800)
                        scenario.supportVariant == FixtureSupportVariant.CONFLICT && id == "fictional-second" -> dose.copy(targetDurationSeconds = 21)
                        else -> dose
                    },
                    bounds = ConditionPrescriptionBounds(minSets = 2, maxSets = 2, minReps = 8, maxReps = 8,
                        minRestSeconds = 30, maxRestSeconds = 30),
                    conditionId = id,
                    inputs = listOf(ConditionInputDefinition("stage", text, true, mapOf("stable" to text))),
                    requiredAnswers = mapOf("stage" to "stable"),
                    requirements = ConditionSupportiveRequirements(emptySet(), emptySet(), ImpactLevel.NONE,
                        if (scenario.supportVariant == FixtureSupportVariant.REQUIRES_EQUIPMENT)
                            listOf(listOf(StandardEquipment.DUMBBELL)) else listOf(emptyList())))
            }
        val support = ConditionSupportCatalog("fictional-v1", records.associateBy { it.conditionId })
        val history = ConditionHistory(now, ZoneId.of("UTC"), now - 8 * 86_400_000L, true, emptyList(),
            if (scenario.pending) listOf(PendingConditionReassessment(profile.id, "fictional-condition",
                supportExercise.id, "fictional-event", "a".repeat(64), 1, now - 1, null)) else emptyList())
        return base.copy(userProfile = profile, conditionSupport = support, conditionHistory = history,
            conditionPlanning = ConditionPlanningPolicy().resolve(profile, base.allowedExercises,
                catalog.associateBy { it.id }, support, history))
    }
}
