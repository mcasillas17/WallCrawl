package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.*

sealed interface ConditionPrescriptionResult {
    class Applied(val prescription: ExercisePrescription, decisions: List<ConditionPrescriptionDecision>) : ConditionPrescriptionResult {
        val decisions = conditionList(decisions)
    }
    class Blocked(reasons: List<ConditionViolation>) : ConditionPrescriptionResult {
        val reasons = conditionList(reasons)
    }
}

/** Explicit reviewed cases only. The support store binds artwork before supplying this catalog. */
class ConditionPlanningPolicy {
    /** After the store's release/content/artwork gate; V1 uses exact execution and ISO-week schedules. */
    fun supports(record: ConditionSupportRecord): Boolean = capabilityViolation(record) == null

    private fun capabilityViolation(record: ConditionSupportRecord): String? = when {
        record.policyVersion != 1 || record.responsePolicy.version != 1 -> "unsupported-policy-or-context"
        (record.inputs + record.responsePolicy.inputs).groupBy { it.id }.values.any { it.distinct().size != 1 } -> "conflicting-input-definitions"
        else -> null
    }

    fun resolve(profile: UserProfile, ordinaryCandidates: List<Exercise>, exercisesById: Map<String, Exercise>,
                support: ConditionSupportCatalog, history: ConditionHistory): ConditionPlanningResult {
        val pending = history.pending.filter { it.profileId == profile.id }
        if (pending.isNotEmpty()) return ConditionPlanningResult.Blocked(pending.take(512).map {
            ConditionViolation("pending-reassessment", setOf(it.conditionId), setOf(it.exerciseId))
        })
        if (profile.activeConditions.isEmpty()) return ConditionPlanningResult.NotApplicable
        val ids = profile.activeConditions.map { it.conditionId }.toSet()
        fun blocked(reason: String, exercises: Set<String> = emptySet()) = ConditionPlanningResult.Blocked(listOf(ConditionViolation(reason, ids, exercises)))
        if (profile.unresolvedTrainingConstraintsRaw != null) return blocked("unresolved-restrictions")
        if (!history.complete) return blocked("incomplete-history")
        val selected = mutableListOf<Pair<ConditionSupportRecord, ConditionApplicabilityCase>>()
        for (selection in profile.activeConditions.sortedBy { it.conditionId }) {
            val record = support.records[selection.conditionId] ?: return blocked("unsupported-condition")
            if (selection.contextSchemaVersion != record.contextSchemaVersion) return blocked("unsupported-policy-or-context")
            capabilityViolation(record)?.let { return blocked(it) }
            val definitions = (record.inputs + record.responsePolicy.inputs).groupBy { it.id }
            if (selection.answers.any { (id, answer) -> definitions[id] == null || answer != null && answer !in definitions.getValue(id).first().choices }) return blocked("unknown-answer")
            if (record.inputs.any { it.required && selection.answers[it.id] == null }) return blocked("missing-context")
            if ((record.responsePolicy.stopWhenAnswers.entries + record.responsePolicy.reassessmentRequiredAnswers.entries).any { (id, answer) -> selection.answers[id] == answer }) return blocked("response-requires-reassessment")
            val case = record.cases.singleOrNull { it.requiredAnswers.all { (id, answer) -> selection.answers[id] == answer } } ?: return blocked("unresolved-case")
            for (rule in case.rules) {
                val exercise = exercisesById[rule.exerciseIdentity.exerciseId] ?: return blocked("missing-exercise")
                if (exercise.type != rule.exerciseIdentity.exerciseType || ConditionExerciseIdentity.contentSha256(exercise) != rule.exerciseIdentity.contentSha256) return blocked("changed-exercise")
            }
            selected += record to case
        }
        fun reference(record: ConditionSupportRecord, case: ConditionApplicabilityCase, rule: ConditionRule) = ConditionRuleReference(record.conditionId, record.contentVersion, case.id, rule.id, record.policyVersion)
        val byExercise = selected.flatMap { (record, case) -> case.rules.map { Triple(record, case, it) } }.groupBy { it.third.exerciseIdentity.exerciseId }
        val ordinary = linkedMapOf<String, ResolvedConditionRule>()
        val supportive = mutableListOf<ConditionSupportivePrescription>()
        val applied = mutableListOf<AppliedConditionRule>()
        val evidence = mutableListOf<ConditionScheduleEvidence>()
        val candidateIds = ordinaryCandidates.map { it.id }.toSet()
        for ((id, entries) in byExercise.toSortedMap()) {
            val rules = entries.map { it.third }
            val required = rules.any { it.effect == ConditionRuleEffect.PRESCRIBE_SUPPORTIVE }
            if (rules.size != selected.size || rules.any { it.effect == ConditionRuleEffect.EXCLUDE }) {
                if (required) return blocked("support-incompatible", setOf(id))
                continue
            }
            val exercise = exercisesById.getValue(id)
            val first = rules.first()
            if (rules.any { it.executionIdentity != first.executionIdentity || it.effect != first.effect || it.exerciseIdentity != first.exerciseIdentity }) return blocked("conflicting-execution", setOf(id))
            val references = entries.map { reference(it.first, it.second, it.third) }.toSet()
            if (required) {
                if (rules.any { it.supportivePrescription != first.supportivePrescription || it.schedule != first.schedule || it.supportiveRequirements != first.supportiveRequirements }) return blocked("conflicting-support", setOf(id))
                if (rules.any { !supportiveEligible(exercise, profile, requireNotNull(it.supportiveRequirements)) }) return blocked("support-restricted", setOf(id))
                val timing = history.schedule(id, requireNotNull(first.schedule))
                if (!timing.complete) return blocked("incomplete-schedule-history", setOf(id))
                references.forEach { evidence += ConditionScheduleEvidence(it, timing.previousPerformedAtTimestamp, history.asOfTimestamp) }
                if (!timing.due) continue
                supportive += ConditionSupportivePrescription(id, requireNotNull(first.supportivePrescription), first.executionIdentity, references, first.schedule)
            } else {
                if (id !in candidateIds || !ordinaryEligible(exercise, profile)) continue
                val bounds = try { rules.map { requireNotNull(it.bounds) }.reduce(::intersect) } catch (_: IllegalArgumentException) { return blocked("conflicting-bounds", setOf(id)) }
                ordinary[id] = ResolvedConditionRule(bounds, first.executionIdentity, rules.all { it.allowOrdinaryProgression }, references, references.map { it.policyVersion }.toSet())
            }
            entries.forEach { (record, case, rule) -> applied += AppliedConditionRule(reference(record, case, rule), rule.exerciseIdentity, rule.executionIdentity,
                if (required) PrescriptionPurpose.SUPPORTIVE else PrescriptionPurpose.ORDINARY, rule.instructions) }
        }
        if (applied.size > 512 || evidence.size > 512 || ordinary.size > 512 || supportive.size > 512) return blocked("planning-bound-exceeded")
        return ConditionPlanningResult.Ready(ordinary, supportive, SessionConditionContext(1, 1, profile.activeConditions,
            selected.associate { it.first.conditionId to it.first.contentVersion }, applied, evidence, emptyList()), profile.preferredUnit)
    }

    fun applyOrdinary(exercise: Exercise, base: ExercisePrescription, planning: ConditionPlanningResult.Ready): ConditionPrescriptionResult {
        val rules = planning.context.appliedRules.filter { it.exerciseIdentity.exerciseId == exercise.id && it.purpose == PrescriptionPurpose.ORDINARY }
        fun blocked(reason: String) = ConditionPrescriptionResult.Blocked(listOf(ConditionViolation(reason, planning.context.selectedConditions.map { it.conditionId }.toSet(), setOf(exercise.id))))
        val resolved = planning.ordinaryRules[exercise.id] ?: return blocked("unassessed-exercise")
        if (rules.isEmpty() || base.purpose != PrescriptionPurpose.ORDINARY || base.exerciseType != exercise.type || rules.any { it.exerciseIdentity.contentSha256 != ConditionExerciseIdentity.contentSha256(exercise) }) return blocked("changed-exercise-or-purpose")
        val bounds = resolved.bounds
        if (base.targetWeight == null && bounds.minLoadKg != null || base.targetAssistanceWeight == null && bounds.minAssistanceKg != null) return blocked("unconfirmed-load")
        val rest = clamp(base.restSeconds, bounds.minRestSeconds, bounds.maxRestSeconds)
        val adjusted = base.copy(
            targetSets = clamp(base.targetSets, bounds.minSets, bounds.maxSets),
            repRange = base.repRange?.let { RepRange(clamp(it.min, bounds.minReps, bounds.maxReps), clamp(it.max, bounds.minReps, bounds.maxReps)) },
            targetWeight = base.targetWeight?.let { load(it, bounds.minLoadKg, bounds.maxLoadKg, planning.weightUnit) },
            targetAssistanceWeight = base.targetAssistanceWeight?.let { load(it, bounds.minAssistanceKg, bounds.maxAssistanceKg, planning.weightUnit) },
            targetDurationSeconds = base.targetDurationSeconds?.let { clamp(it, bounds.minDurationSeconds, bounds.maxDurationSeconds) } ?: bounds.minDurationSeconds,
            targetDistanceMeters = base.targetDistanceMeters?.let { clamp(it, bounds.minDistanceMeters, bounds.maxDistanceMeters) } ?: bounds.minDistanceMeters,
            restSeconds = rest,
            // A constrained rest target is no longer the user's original preference.
            restClass = base.restClass.takeIf { rest == base.restSeconds },
            restTargetSource = base.restTargetSource.takeIf { rest == base.restSeconds })
        return ConditionPrescriptionResult.Applied(adjusted, listOf(ConditionPrescriptionDecision(
            rules.first().exerciseIdentity, PrescriptionPurpose.ORDINARY, resolved.executionIdentity, resolved.sourceRuleReferences)))
    }

    /** Per-exercise validation; required insertion and whole-session validation belong to Tasks 11/12. */
    fun validate(planned: PlannedExercise, planning: ConditionPlanningResult.Ready): List<ConditionViolation> {
        val ids = planning.context.selectedConditions.map { it.conditionId }.toSet()
        fun violation(reason: String) = listOf(ConditionViolation(reason, ids, setOf(planned.exerciseId)))
        if (planned.prescription.purpose == PrescriptionPurpose.SUPPORTIVE) {
            val required = planning.supportive.singleOrNull { it.exerciseId == planned.exerciseId } ?: return violation("support-not-due-or-unassessed")
            val expected = required.prescription.copy(
                targetWeight = required.prescription.targetWeight?.let { convertWeight(it, WeightUnit.KG, planning.weightUnit) },
                targetAssistanceWeight = required.prescription.targetAssistanceWeight?.let { convertWeight(it, WeightUnit.KG, planning.weightUnit) })
            return if (planned.prescription == expected) emptyList() else violation("support-dose-mismatch")
        }
        val bounds = planning.ordinaryRules[planned.exerciseId]?.bounds ?: return violation("unassessed-exercise")
        val identity = planning.context.appliedRules.firstOrNull { it.exerciseIdentity.exerciseId == planned.exerciseId }?.exerciseIdentity ?: return violation("missing-exercise-identity")
        val p = planned.prescription
        if (p.exerciseType != identity.exerciseType) return violation("wrong-exercise-type")
        fun <T : Comparable<T>> within(value: T?, min: T?, max: T?) = if (value == null) min == null else (min == null || value >= min) && (max == null || value <= max)
        fun inProfileUnit(value: Double?) = value?.let { convertWeight(it, WeightUnit.KG, planning.weightUnit) }
        return if (within(p.targetSets, bounds.minSets, bounds.maxSets) && within(p.repRange?.min, bounds.minReps, bounds.maxReps) && within(p.repRange?.max, bounds.minReps, bounds.maxReps) &&
            within(p.targetWeight, inProfileUnit(bounds.minLoadKg), inProfileUnit(bounds.maxLoadKg)) &&
            within(p.targetAssistanceWeight, inProfileUnit(bounds.minAssistanceKg), inProfileUnit(bounds.maxAssistanceKg)) &&
            within(p.targetDurationSeconds, bounds.minDurationSeconds, bounds.maxDurationSeconds) && within(p.targetDistanceMeters, bounds.minDistanceMeters, bounds.maxDistanceMeters) &&
            within(p.restSeconds, bounds.minRestSeconds, bounds.maxRestSeconds)) emptyList() else violation("outside-condition-bounds")
    }

    private fun ordinaryEligible(exercise: Exercise, profile: UserProfile): Boolean {
        val metadata = exercise.acceptedMetadata() ?: return false
        return exercise.id !in profile.excludedExerciseIds && exercise.hasRequiredEquipment(profile.availableEquipment) &&
            metadata.equipmentAlternatives.isEquipmentSatisfiedBy(profile.availableEquipment.normalizedEquipmentSet()) &&
            metadata.capabilityRequirements.none { profile.movementCapabilities[it] == CapabilityLevel.AVOID } &&
            metadata.clearsJointConstraintsOf(profile) && !metadata.violatesImpactRestrictionOf(profile)
    }
    private fun supportiveEligible(exercise: Exercise, profile: UserProfile, requirements: ConditionSupportiveRequirements): Boolean =
        exercise.id !in profile.excludedExerciseIds && exercise.hasRequiredEquipment(profile.availableEquipment) &&
            requirements.equipmentAlternatives.isEquipmentSatisfiedBy(profile.availableEquipment.normalizedEquipmentSet()) &&
            requirements.capabilities.none { profile.movementCapabilities[it] == CapabilityLevel.AVOID } &&
            profile.trainingConstraints.none { it != TrainingConstraint.LOW_IMPACT_ONLY && it !in requirements.clearedConstraints } &&
            !(TrainingConstraint.LOW_IMPACT_ONLY in profile.trainingConstraints && requirements.impact == ImpactLevel.HIGH)

    private fun <T : Comparable<T>> clamp(value: T, min: T?, max: T?): T = value.let { if (min != null && it < min) min else it }.let { if (max != null && it > max) max else it }
    private fun load(value: Double, min: Double?, max: Double?, unit: WeightUnit) = clamp(value, min?.let { convertWeight(it, WeightUnit.KG, unit) }, max?.let { convertWeight(it, WeightUnit.KG, unit) })
    private fun <T : Comparable<T>> lower(a: T?, b: T?): T? = if (a == null) b else if (b == null) a else maxOf(a, b)
    private fun <T : Comparable<T>> upper(a: T?, b: T?): T? = if (a == null) b else if (b == null) a else minOf(a, b)
    private fun intersect(a: ConditionPrescriptionBounds, b: ConditionPrescriptionBounds) = ConditionPrescriptionBounds(
        lower(a.minSets,b.minSets), upper(a.maxSets,b.maxSets), lower(a.minReps,b.minReps), upper(a.maxReps,b.maxReps),
        lower(a.minLoadKg,b.minLoadKg), upper(a.maxLoadKg,b.maxLoadKg), lower(a.minAssistanceKg,b.minAssistanceKg), upper(a.maxAssistanceKg,b.maxAssistanceKg),
        lower(a.minDurationSeconds,b.minDurationSeconds), upper(a.maxDurationSeconds,b.maxDurationSeconds), lower(a.minDistanceMeters,b.minDistanceMeters), upper(a.maxDistanceMeters,b.maxDistanceMeters),
        lower(a.minRestSeconds,b.minRestSeconds), upper(a.maxRestSeconds,b.maxRestSeconds))
}
