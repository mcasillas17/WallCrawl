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
        val rules = ordinaryExercises.map { exercise -> ConditionRule("rule-${exercise.id}",
            ConditionExerciseIdentity.from(exercise, "b".repeat(64)), ConditionRuleEffect.PERMIT_ORDINARY,
            execution, text, setOf("source"), bounds, allowProgression, null, null) } +
            ConditionRule("support-rule", ConditionExerciseIdentity.from(supportExercise, "b".repeat(64)),
                ConditionRuleEffect.PRESCRIBE_SUPPORTIVE, "support-execution", text, setOf("source"), null, false,
                supportiveDose, schedule, ConditionSupportiveRequirements(emptySet(), emptySet(), ImpactLevel.NONE, listOf(emptyList())))
        val record = ConditionSupportRecord("fictional-condition", "fictional-v1", 1, 1,
            ConditionPopulation.ADULT_NONOPERATIVE, text, text, emptyList(),
            listOf(ConditionApplicabilityCase("case", emptyMap(), rules)),
            listOf(ConditionSource("source", "https://example.invalid/fictional", "a".repeat(64))),
            ConditionResponsePolicy(1, listOf(ConditionInputDefinition("response", text, true, mapOf("stop" to text))), mapOf("response" to "stop"), mapOf("response" to "stop"), text), supportContentSha256,
            ConditionReleaseReview(ConditionReviewState.RELEASED, "FICTIONAL", "FICTIONAL", 1, "FICTIONAL", supportContentSha256, setOf("source")))
        val support = ConditionSupportCatalog("fictional-v1", mapOf(record.conditionId to record))
        val history = ConditionHistory(now, ZoneId.of("UTC"), now - 8 * 86_400_000L, true, exposures, emptyList())
        val all = ordinaryExercises + supportExercise
        val ready = ConditionPlanningPolicy().resolve(profile, all, all.associateBy { it.id }, support, history)
        check(ready is ConditionPlanningResult.Ready)
        return WorkoutGenerationContext(userProfile = profile, allowedExercises = all, conditionPlanning = ready,
            conditionSupport = support, conditionHistory = history, regenerationIndex = 0)
    }
}
