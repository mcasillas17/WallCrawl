package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.MuscleDoseAccounting
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

/** What whole-program validation concluded. */
sealed interface ProgramValidationResult {

    /**
     * [workout] is what may be shown and started; it differs from the input only if repaired.
     *
     * [snapshot] is the evidence recorded with the session if it is started. Only an
     * accepted proposal carries one, because only an accepted proposal is ever written.
     */
    data class Valid(
        val workout: GeneratedWorkout,
        val snapshot: RecommendationSnapshot
    ) : ProgramValidationResult

    /**
     * Nothing is displayed or persisted.
     *
     * [violations] is complete and deterministically ordered, and is what callers branch on
     * to explain the refusal. A rejection carries no snapshot: nothing is recorded for a
     * plan that was never started, so building one would be evidence nobody can read.
     */
    data class Invalid(
        val violations: List<ProgramViolation>
    ) : ProgramValidationResult
}

/**
 * Validates one complete proposed session against the exact context that produced it.
 *
 * ## Why the whole proposal
 *
 * `StateBasedTrainingPolicy` caps each prescription against the same supplied completed
 * ledger, so two exercises sharing one direct-primary muscle can each spend the same
 * remaining allowance. Checking one prescription at a time cannot see that. This validator
 * aggregates the entire proposal before comparing it to the configured allowance, which is
 * the whole reason it exists.
 *
 * ## Classification
 *
 * Every rule is either a software invariant — internal integrity, consistency, or
 * persistence correctness — or a named versioned product policy. Exact allowances, set
 * caps, RIR bands, and rest seconds belong to `STATE_BASED_DOSE_EFFORT_REST_V1`. Exceeding
 * one is a policy mismatch, never proof of overload or medical danger. There is no numeric
 * fatigue budget here, no summation of the legacy ordinal `programming.fatigueScore`, no
 * timestamp-derived readiness or recovery rule, no weekly minimum, and no automatic volume
 * increase.
 *
 * ## Purity
 *
 * The validator reads its inputs and returns a result. It writes nothing, mutates no
 * ledger, and never credits a proposal as completed work. The only I/O is the catalog
 * lookup [GeneratedWorkoutValidator] already performs.
 */
class ProgramValidator(
    private val structuralValidator: GeneratedWorkoutValidator,
    private val defaults: StateBasedTrainingPolicyDefaults = StateBasedTrainingPolicyDefaults.V1
) {

    /**
     * Validates [workout] against [context].
     *
     * [allowRepair] permits exactly one deterministic repair pass, and only when every
     * reported problem is an exceeded configured allowance. It is false at workout start so
     * a displayed plan is never silently replaced by a materially different one.
     */
    suspend fun validate(
        workout: GeneratedWorkout,
        context: WorkoutGenerationContext,
        allowRepair: Boolean = false
    ): ProgramValidationResult {
        val first = evaluate(workout, context)
        if (first.violations.isEmpty()) {
            return ProgramValidationResult.Valid(
                workout = workout,
                snapshot = snapshot(RecommendationOutcome.VALID, emptyList(), first, context)
            )
        }

        val repaired = if (allowRepair) repair(workout, first) else null
        if (repaired == null) {
            return ProgramValidationResult.Invalid(first.violations)
        }

        // Exactly one pass. Whatever the repaired proposal still breaks is final: retrying
        // is how a bounded repair turns into a search that eventually relaxes something.
        val second = evaluate(repaired, context)
        return if (second.violations.isEmpty()) {
            ProgramValidationResult.Valid(
                workout = repaired,
                snapshot = snapshot(
                    RecommendationOutcome.REPAIRED,
                    first.reasonCodes(),
                    second,
                    context
                )
            )
        } else {
            ProgramValidationResult.Invalid(second.violations)
        }
    }

    private suspend fun evaluate(
        workout: GeneratedWorkout,
        context: WorkoutGenerationContext
    ): Evaluation {
        val allowedById = context.allowedExercises.associateBy(Exercise::id)
        val violations = mutableListOf<ProgramViolation>()

        violations += structuralValidator.structuralViolations(workout, allowedById.keys)
        if (violations.any { it.code == ProgramViolationCode.EMPTY_RECOMMENDATION }) {
            return Evaluation(violations.sorted(), emptyList())
        }

        violations += declaredConstraintViolations(workout, context, allowedById)
        workout.exercises.forEachIndexed { index, planned ->
            violations += exerciseViolations(index, planned, context, allowedById)
        }
        violations += durationViolations(workout)

        val accounting = accountProposedDose(workout, context, allowedById)
        violations += accounting.violations

        return Evaluation(
            violations = violations.sorted(),
            doseAccounting = accounting.perMuscle,
            attributionByExerciseId = accounting.attributionByExerciseId
        )
    }

    // region declared program-design constraints

    /**
     * The rules the caller actually declared for this session, and nothing else.
     *
     * Nothing here is a claim that repeating a movement or a family is harmful, and no
     * pattern is required unless the context asked for it.
     */
    private fun declaredConstraintViolations(
        workout: GeneratedWorkout,
        context: WorkoutGenerationContext,
        allowedById: Map<String, Exercise>
    ): List<ProgramViolation> {
        val constraints = context.programConstraints
        val violations = mutableListOf<ProgramViolation>()

        if (constraints.uniqueExerciseIds) {
            val seen = mutableSetOf<String>()
            workout.exercises.forEachIndexed { index, planned ->
                if (!seen.add(planned.exerciseId)) {
                    violations += ProgramViolation(
                        code = ProgramViolationCode.DUPLICATE_EXERCISE_IN_SESSION,
                        exerciseId = planned.exerciseId,
                        orderIndex = index
                    )
                }
            }
        }

        if (constraints.uniqueProgressionFamilies) {
            val seen = mutableSetOf<String>()
            workout.exercises.forEachIndexed { index, planned ->
                // Approved metadata only, matching the constraint's own contract: an
                // unapproved draft record still carries an authored family, and a draft must
                // never be what drives a product-policy rejection.
                val family = allowedById[planned.exerciseId]
                    ?.approvedMetadata()
                    ?.progressionFamily
                    ?: return@forEachIndexed
                if (!seen.add(family)) {
                    violations += ProgramViolation(
                        code = ProgramViolationCode.DUPLICATE_PROGRESSION_FAMILY,
                        exerciseId = planned.exerciseId,
                        orderIndex = index,
                        detail = family
                    )
                }
            }
        }

        if (constraints.requiredMovementPatterns.isNotEmpty()) {
            val covered = workout.exercises.mapNotNullTo(mutableSetOf()) { planned ->
                allowedById[planned.exerciseId]?.movementPattern()
            }
            (constraints.requiredMovementPatterns - covered)
                .sortedBy(MovementPattern::ordinal)
                .forEach { missing ->
                    violations += ProgramViolation(
                        code = ProgramViolationCode.MISSING_REQUIRED_MOVEMENT_PATTERN,
                        detail = missing.name
                    )
                }
        }
        return violations
    }

    /** Reviewed metadata is authoritative when present; the legacy block is the fallback. */
    private fun Exercise.movementPattern(): MovementPattern? =
        reviewedMetadata?.movementPattern ?: programming?.movementPattern

    // endregion

    // region per-exercise rules

    private fun exerciseViolations(
        index: Int,
        planned: PlannedExercise,
        context: WorkoutGenerationContext,
        allowedById: Map<String, Exercise>
    ): List<ProgramViolation> {
        val violations = mutableListOf<ProgramViolation>()

        if (planned.exerciseId in context.userProfile.excludedExerciseIds) {
            violations += ProgramViolation(
                code = ProgramViolationCode.EXPLICIT_CONSTRAINT_VIOLATED,
                exerciseId = planned.exerciseId,
                orderIndex = index,
                detail = "USER_EXCLUDED"
            )
        }

        violations += loadProvenanceViolations(index, planned, context)

        // An exercise that is not a legal candidate has already been reported as such.
        // Piling reviewed-metadata reasons on top of that would describe a plan nobody
        // proposed, so the remaining per-exercise rules need a resolved candidate.
        val candidate = allowedById[planned.exerciseId] ?: return violations
        if (context.automaticEligibilityResult != null) {
            violations += reviewedViolations(index, planned, candidate, context)
        }
        return violations
    }

    /**
     * Rules that exist only on the reviewed-only path, which stays disabled in production.
     *
     * They mirror the trust checks `StateBasedTrainingPolicy` already applies, so a
     * proposal can never reach a user through a path that policy would have refused.
     */
    private fun reviewedViolations(
        index: Int,
        planned: PlannedExercise,
        candidate: Exercise,
        context: WorkoutGenerationContext
    ): List<ProgramViolation> {
        val violations = mutableListOf<ProgramViolation>()

        val decision = context.automaticEligibilityResult
            ?.decisions
            ?.firstOrNull { it.exerciseId == candidate.id }
        if (decision == null || !decision.eligible) {
            violations += ProgramViolation(
                code = ProgramViolationCode.EXPLICIT_CONSTRAINT_VIOLATED,
                exerciseId = candidate.id,
                orderIndex = index,
                detail = decision?.reasons?.firstOrNull()?.name ?: "NO_ELIGIBILITY_DECISION"
            )
        }

        val approved = candidate.approvedMetadata()
        if (approved == null) {
            violations += ProgramViolation(
                code = ProgramViolationCode.MISSING_APPROVED_METADATA,
                exerciseId = candidate.id,
                orderIndex = index
            )
            return violations
        }

        val ledger = context.trainingProgramState?.weeklyLedger
        if (ledger != null && approved.provenance.policyVersion != ledger.reviewPolicyVersion) {
            violations += ProgramViolation(
                code = ProgramViolationCode.REVIEW_POLICY_VERSION_MISMATCH,
                exerciseId = candidate.id,
                orderIndex = index
            )
        }
        if (!approved.matches(candidate.type, planned.prescription.exerciseType)) {
            violations += ProgramViolation(
                code = ProgramViolationCode.PRESCRIPTION_SHAPE_MISMATCH,
                exerciseId = candidate.id,
                orderIndex = index,
                detail = approved.prescriptionShape.name
            )
        }
        return violations
    }

    /** Approved metadata that also carries the human provenance approval requires. */
    private fun Exercise.approvedMetadata(): ReviewedExerciseMetadata? = reviewedMetadata
        ?.takeIf { it.reviewState == ReviewState.APPROVED }
        ?.takeIf { it.isWellFormedApprovedMetadata() }

    /**
     * A prescribed load must trace to something the user confirmed or actually lifted.
     *
     * A null load stays null: no body measurement, score, or metadata approval authorizes
     * inventing a starting number. Equality to the last recorded load is deliberately not
     * required, because the shipped legacy path may add its documented unit-aware increment
     * once the top of a rep range has been reached. This task neither replaces progression
     * nor introduces one, and provenance proves origin, never safety.
     */
    private fun loadProvenanceViolations(
        index: Int,
        planned: PlannedExercise,
        context: WorkoutGenerationContext
    ): List<ProgramViolation> {
        val prescribed = listOfNotNull(
            planned.prescription.targetWeight,
            planned.prescription.targetAssistanceWeight
        )
        if (prescribed.isEmpty()) return emptyList()

        val traceable = traceableLoads(planned.exerciseId, context)
        return prescribed
            .filterNot { value -> traceable.any { source -> closeEnough(source, value) } }
            .map {
                ProgramViolation(
                    code = ProgramViolationCode.UNTRACEABLE_LOAD,
                    exerciseId = planned.exerciseId,
                    orderIndex = index
                )
            }
            .distinct()
    }

    private fun traceableLoads(
        exerciseId: String,
        context: WorkoutGenerationContext
    ): List<Double> = buildList {
        context.userProfile.confirmedStartingLoads[exerciseId]
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let(::add)
        context.exerciseHistory[exerciseId]
            ?.lastWeight
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { lastWeight ->
                add(lastWeight)
                add(lastWeight + context.preferredUnits.historyIncrement())
            }
    }

    /** The increment the shipped legacy prescription factory applies, in the same unit. */
    private fun WeightUnit.historyIncrement(): Double = when (this) {
        WeightUnit.LBS -> 5.0
        WeightUnit.KG -> 2.5
    }

    private fun closeEnough(source: Double, prescribed: Double): Boolean =
        kotlin.math.abs(source - prescribed) < LOAD_EQUALITY_TOLERANCE

    // endregion

    // region duration

    /**
     * The reported estimate must be representable and must agree with the named estimator.
     *
     * Version 1 deliberately enforces no relationship to the user's requested duration: an
     * estimate is not a completion-time promise, and the bounds are structural rather than
     * physiological.
     */
    private fun durationViolations(workout: GeneratedWorkout): List<ProgramViolation> {
        val expected = WorkoutDurationEstimator.estimateMinutes(workout.exercises)
        val deviation = kotlin.math.abs(workout.estimatedDurationMinutes - expected)
        if (deviation <= WorkoutDurationEstimator.TOLERANCE_MINUTES) return emptyList()
        return listOf(
            ProgramViolation(
                code = ProgramViolationCode.DURATION_ESTIMATE_MISMATCH,
                detail = "${workout.estimatedDurationMinutes}!=$expected"
            )
        )
    }

    // endregion

    // region aggregate weekly dose

    /**
     * Attributes the whole proposal prospectively, then compares it to one allowance.
     *
     * Prospective targets and completed credit stay in separate fields of the result:
     * nothing here writes to the ledger, and a proposed or rejected workout never becomes
     * completed exposure. `PRIMARY_ONLY_V1` is preserved exactly — one designated direct
     * primary per set, with descriptive secondary muscles credited nothing at all.
     */
    private fun accountProposedDose(
        workout: GeneratedWorkout,
        context: WorkoutGenerationContext,
        allowedById: Map<String, Exercise>
    ): DoseAccountingResult {
        // Reviewed path only, gated on the same eligibility result every other reviewed rule
        // reads rather than on the program state alone. The two coincide today because the
        // context builder composes a program state only behind the same flag, but a context
        // carrying one without an eligibility result must not reject a legacy proposal with
        // a reviewed-only reason.
        if (context.automaticEligibilityResult == null) {
            return DoseAccountingResult(emptyList(), emptyList(), emptyMap())
        }
        val programState = context.trainingProgramState
            ?: return DoseAccountingResult(emptyList(), emptyList(), emptyMap())

        unusableStateViolation(programState)?.let { violation ->
            // A damaged or unrecognised ledger is a different problem from a full one, and
            // reporting counts read out of it would be inventing accounting.
            return DoseAccountingResult(emptyList(), listOf(violation), emptyMap())
        }

        val ledger = programState.weeklyLedger
        val allowance = defaults.doseLimitsByState[programState.adaptationState]
            ?.maxWeeklyDirectPrimarySets
        val proposedByMuscle = linkedMapOf<String, Long>()
        val attribution = linkedMapOf<String, String>()
        workout.exercises.forEach { planned ->
            val muscle = allowedById[planned.exerciseId]
                ?.approvedMetadata()
                ?.directPrimaryMuscle
                ?: return@forEach
            attribution[planned.exerciseId] = muscle
            proposedByMuscle[muscle] =
                (proposedByMuscle[muscle] ?: 0L) + planned.targetSets.toLong()
        }

        val perMuscle = mutableListOf<MuscleDoseAccounting>()
        val violations = mutableListOf<ProgramViolation>()
        proposedByMuscle.keys.sorted().forEach { muscle ->
            val proposed = proposedByMuscle.getValue(muscle)
            val completed = ledger.directPrimarySets[muscle]?.toLong() ?: 0L
            val total = completed + proposed
            if (total > Int.MAX_VALUE) {
                violations += ProgramViolation(
                    code = ProgramViolationCode.DOSE_ACCOUNTING_OVERFLOW,
                    detail = muscle
                )
                return@forEach
            }
            perMuscle += MuscleDoseAccounting(
                muscle = muscle,
                completedSets = completed.toInt(),
                proposedSets = proposed.toInt(),
                allowanceSets = allowance
            )
            if (allowance != null && total > allowance.toLong()) {
                violations += ProgramViolation(
                    code = ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED,
                    detail = muscle
                )
            }
        }
        return DoseAccountingResult(perMuscle, violations, attribution)
    }

    /**
     * Whether the supplied program state can be used for accounting at all.
     *
     * An unsupported program-state or ledger policy version and a malformed ledger are all
     * "this accounting is unusable", which is a different kind of problem from a configured
     * allowance being full. The specific cause travels in the violation's detail.
     */
    private fun unusableStateViolation(state: TrainingProgramState): ProgramViolation? = when {
        state.policyVersion != TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1 ->
            ProgramViolation(
                code = ProgramViolationCode.MALFORMED_WEEKLY_LEDGER,
                detail = "UNSUPPORTED_TRAINING_PROGRAM_STATE_POLICY"
            )

        state.weeklyLedger.policyVersion != LedgerPolicyVersion.PRIMARY_ONLY_V1 ->
            ProgramViolation(
                code = ProgramViolationCode.MALFORMED_WEEKLY_LEDGER,
                detail = "UNSUPPORTED_LEDGER_POLICY"
            )

        !state.weeklyLedger.isWellFormed() ->
            ProgramViolation(
                code = ProgramViolationCode.MALFORMED_WEEKLY_LEDGER,
                detail = "MALFORMED_WEEKLY_LEDGER"
            )

        else -> null
    }

    // endregion

    // region repair

    /**
     * The one permitted repair: reduce sets until aggregate accounting holds.
     *
     * It runs only when every reported problem is an exceeded configured allowance, so a
     * repair can never be the thing that resolves a candidate-membership, eligibility,
     * provenance, or load problem. Within a muscle the remaining allowance is handed out in
     * recommendation order and every affected exercise keeps at least one set; when that is
     * impossible the repair fails rather than dropping an exercise, because dropping one
     * changes the displayed plan materially.
     *
     * Returns null when nothing may be repaired, which the caller reports as a rejection.
     */
    private fun repair(workout: GeneratedWorkout, evaluation: Evaluation): GeneratedWorkout? {
        if (evaluation.violations.isEmpty()) return null
        if (
            evaluation.violations.any {
                it.code != ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED
            }
        ) {
            return null
        }

        val overMuscles = evaluation.violations.mapNotNull { it.detail }.toSet()
        val setsByIndex = workout.exercises.map { it.targetSets }.toMutableList()

        overMuscles.forEach { muscle ->
            val accounting = evaluation.doseAccounting.firstOrNull { it.muscle == muscle }
                ?: return null
            val allowance = accounting.allowanceSets ?: return null
            val remaining = allowance - accounting.completedSets
            val affected = workout.exercises
                .mapIndexedNotNull { index, planned ->
                    index.takeIf { muscleOf(planned, evaluation, muscle) }
                }
            if (affected.isEmpty()) return null
            // Every affected exercise has to survive with at least one set, or this is not a
            // reduction any more.
            if (remaining < affected.size) return null

            var budget = remaining
            affected.forEachIndexed { position, index ->
                val exercisesAfterThis = affected.size - position - 1
                val granted = minOf(setsByIndex[index], budget - exercisesAfterThis)
                setsByIndex[index] = granted
                budget -= granted
            }
        }

        val exercises = workout.exercises.mapIndexed { index, planned ->
            if (setsByIndex[index] == planned.targetSets) {
                planned
            } else {
                planned.copy(
                    prescription = planned.prescription.copy(targetSets = setsByIndex[index])
                )
            }
        }
        if (exercises == workout.exercises) return null

        // The estimate has to follow the plan it describes, so it is recomputed under the
        // same named estimator the agreement rule checks.
        return workout.copy(
            exercises = exercises,
            estimatedDurationMinutes = WorkoutDurationEstimator.estimateMinutes(exercises)
        )
    }

    /**
     * Whether [planned] is one of the exercises that spent [muscle]'s allowance.
     *
     * Attribution is re-read from the same evaluation that produced the violation rather
     * than recomputed, so repair can never redistribute sets for a muscle the accounting
     * did not actually charge.
     */
    private fun muscleOf(
        planned: PlannedExercise,
        evaluation: Evaluation,
        muscle: String
    ): Boolean = evaluation.attributionByExerciseId[planned.exerciseId] == muscle

    // endregion

    private fun snapshot(
        outcome: RecommendationOutcome,
        reasonCodes: List<ProgramViolationCode>,
        evaluation: Evaluation,
        context: WorkoutGenerationContext
    ): RecommendationSnapshot {
        val reviewedPathEnabled = context.automaticEligibilityResult != null
        // Gated on the same predicate the reviewed rules use, not on the state's mere
        // presence. A context carrying a program state without an eligibility result runs no
        // dose policy and no accounting, so naming one in the record would be provenance for
        // a decision that never happened.
        val programState = context.trainingProgramState?.takeIf { reviewedPathEnabled }
        return RecommendationSnapshot(
            validatorVersion = ProgramValidatorVersion.WHOLE_PROGRAM_V1,
            durationEstimatorVersion = WorkoutDurationEstimator.VERSION,
            outcome = outcome,
            reviewedPathEnabled = reviewedPathEnabled,
            catalogVersion = context.catalogVersion,
            reviewPolicyVersion = context.reviewPolicyVersion,
            trainingPolicyVersion = defaults.policyVersion.takeIf { programState != null },
            ledgerPolicyVersion = programState?.weeklyLedger?.policyVersion,
            programStatePolicyVersion = programState?.policyVersion,
            adaptationState = programState?.adaptationState,
            weekStartEpochDay = programState?.weeklyLedger?.weekStartEpochDay,
            timeZoneId = programState?.weeklyLedger?.timeZoneId,
            profileId = context.userProfile.id,
            profileRevision = context.userProfile.revision,
            contextIdentity = RecommendationContextIdentity.of(context),
            reasonCodes = reasonCodes,
            doseAccounting = evaluation.doseAccounting
        )
    }

    private data class Evaluation(
        val violations: List<ProgramViolation>,
        val doseAccounting: List<MuscleDoseAccounting>,
        val attributionByExerciseId: Map<String, String> = emptyMap()
    ) {
        fun reasonCodes(): List<ProgramViolationCode> = violations.map { it.code }.distinct()
    }

    private data class DoseAccountingResult(
        val perMuscle: List<MuscleDoseAccounting>,
        val violations: List<ProgramViolation>,
        val attributionByExerciseId: Map<String, String>
    )

    private companion object {
        /** Loads are compared as recorded values; this only absorbs representation noise. */
        const val LOAD_EQUALITY_TOLERANCE = 1e-9
    }
}
