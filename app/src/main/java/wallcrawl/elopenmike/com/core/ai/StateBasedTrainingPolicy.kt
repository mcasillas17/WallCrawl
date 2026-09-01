package wallcrawl.elopenmike.com.core.ai

import java.util.Collections
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.UserRestPreference
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger

enum class TrainingPolicyVersion {
    STATE_BASED_DOSE_EFFORT_REST_V1
}

/** Editable WallCrawl product limits for one adaptation state. */
data class StateDoseLimits(
    val maxWeeklyDirectPrimarySets: Int,
    val maxTargetSetsPerExercise: Int
) {
    init {
        require(maxWeeklyDirectPrimarySets in 1..MAX_WEEKLY_DIRECT_PRIMARY_SETS) {
            "Weekly direct-primary set cap must be between 1 and $MAX_WEEKLY_DIRECT_PRIMARY_SETS."
        }
        require(maxTargetSetsPerExercise in 1..MAX_TARGET_SETS_PER_EXERCISE) {
            "Per-exercise set cap must be between 1 and $MAX_TARGET_SETS_PER_EXERCISE."
        }
    }

    private companion object {
        const val MAX_WEEKLY_DIRECT_PRIMARY_SETS = 50_000
        const val MAX_TARGET_SETS_PER_EXERCISE = 20
    }
}

/**
 * Versioned product defaults. Maps are defensively copied so policy behavior cannot be
 * changed by a caller retaining a mutable input.
 */
class StateBasedTrainingPolicyDefaults(
    policyVersion: TrainingPolicyVersion,
    doseLimitsByState: Map<AdaptationState, StateDoseLimits?>,
    productRestSecondsByClass: Map<RestClass, Int>,
    val limitedCapabilityMaxTargetSets: Int,
    val conservativeEffort: EffortTarget,
    val establishedStrengthEffort: EffortTarget,
    val establishedGeneralOrHypertrophyEffort: EffortTarget
) {
    val policyVersion: TrainingPolicyVersion = policyVersion
    val doseLimitsByState: Map<AdaptationState, StateDoseLimits?> =
        Collections.unmodifiableMap(LinkedHashMap(doseLimitsByState))
    val productRestSecondsByClass: Map<RestClass, Int> =
        Collections.unmodifiableMap(LinkedHashMap(productRestSecondsByClass))

    init {
        require(doseLimitsByState.keys == AdaptationState.entries.toSet()) {
            "Dose defaults must define every adaptation state exactly once."
        }
        require(doseLimitsByState.getValue(AdaptationState.NEEDS_ONBOARDING) == null) {
            "NEEDS_ONBOARDING must explicitly provide no dose guidance."
        }
        require(
            AdaptationState.entries
                .filterNot { it == AdaptationState.NEEDS_ONBOARDING }
                .all { doseLimitsByState.getValue(it) != null }
        ) {
            "Every onboarded adaptation state must define dose limits."
        }
        require(productRestSecondsByClass.keys == RestClass.entries.toSet()) {
            "Rest defaults must define every rest class exactly once."
        }
        require(productRestSecondsByClass.values.all { it in 1..MAX_REST_SECONDS }) {
            "Product rest seconds must be between 1 and $MAX_REST_SECONDS."
        }
        require(limitedCapabilityMaxTargetSets in 1..MAX_TARGET_SETS_PER_EXERCISE) {
            "Limited-capability set cap must be between 1 and $MAX_TARGET_SETS_PER_EXERCISE."
        }
    }

    fun copy(
        policyVersion: TrainingPolicyVersion = this.policyVersion,
        doseLimitsByState: Map<AdaptationState, StateDoseLimits?> = this.doseLimitsByState,
        productRestSecondsByClass: Map<RestClass, Int> = this.productRestSecondsByClass,
        limitedCapabilityMaxTargetSets: Int = this.limitedCapabilityMaxTargetSets,
        conservativeEffort: EffortTarget = this.conservativeEffort,
        establishedStrengthEffort: EffortTarget = this.establishedStrengthEffort,
        establishedGeneralOrHypertrophyEffort: EffortTarget =
            this.establishedGeneralOrHypertrophyEffort
    ): StateBasedTrainingPolicyDefaults = StateBasedTrainingPolicyDefaults(
        policyVersion = policyVersion,
        doseLimitsByState = doseLimitsByState,
        productRestSecondsByClass = productRestSecondsByClass,
        limitedCapabilityMaxTargetSets = limitedCapabilityMaxTargetSets,
        conservativeEffort = conservativeEffort,
        establishedStrengthEffort = establishedStrengthEffort,
        establishedGeneralOrHypertrophyEffort = establishedGeneralOrHypertrophyEffort
    )

    companion object {
        val V1: StateBasedTrainingPolicyDefaults = StateBasedTrainingPolicyDefaults(
            policyVersion = TrainingPolicyVersion.STATE_BASED_DOSE_EFFORT_REST_V1,
            doseLimitsByState = linkedMapOf(
                AdaptationState.NEEDS_ONBOARDING to null,
                AdaptationState.UNCALIBRATED to StateDoseLimits(6, 2),
                AdaptationState.INITIATE to StateDoseLimits(6, 2),
                AdaptationState.BUILD to StateDoseLimits(12, 4),
                AdaptationState.DEVELOP to StateDoseLimits(12, 4),
                AdaptationState.HOLD to StateDoseLimits(8, 2),
                AdaptationState.RETURNING to StateDoseLimits(6, 2),
                AdaptationState.DELOAD_OFFERED to StateDoseLimits(6, 2),
                AdaptationState.RECALIBRATE to StateDoseLimits(6, 2)
            ),
            productRestSecondsByClass = linkedMapOf(
                RestClass.SHORT to 60,
                RestClass.MODERATE to 90,
                RestClass.LONG to 180
            ),
            limitedCapabilityMaxTargetSets = 2,
            conservativeEffort = EffortTarget(2, 4),
            establishedStrengthEffort = EffortTarget(1, 2),
            establishedGeneralOrHypertrophyEffort = EffortTarget(1, 3)
        )

        private const val MAX_REST_SECONDS = 1_800
        private const val MAX_TARGET_SETS_PER_EXERCISE = 20
    }
}

enum class TrainingPolicyReason {
    STATE_DOSE_CAP,
    RELEVANT_LIMITED_CAPABILITY,
    WEEKLY_DIRECT_PRIMARY_ALLOWANCE,
    CONSERVATIVE_EFFORT,
    ESTABLISHED_STRENGTH_EFFORT,
    ESTABLISHED_GENERAL_OR_HYPERTROPHY_EFFORT,
    USER_REST_PREFERENCE,
    PRODUCT_REST_DEFAULT
}

enum class TrainingPolicyNoGuidanceReason {
    PROFILE_NOT_READY,
    WEEKLY_DIRECT_PRIMARY_ALLOWANCE_EXHAUSTED
}

enum class TrainingPolicyFailureReason {
    UNSUPPORTED_TRAINING_PROGRAM_STATE_POLICY,
    UNSUPPORTED_LEDGER_POLICY,
    MALFORMED_WEEKLY_LEDGER,
    MISSING_APPROVED_METADATA,
    MALFORMED_APPROVED_METADATA,
    REVIEW_POLICY_VERSION_MISMATCH,
    PRESCRIPTION_SHAPE_MISMATCH,
    CAPABILITY_AVOID_REACHED_POLICY
}

sealed interface TrainingPolicyResult {
    data class Applied(
        val policyVersion: TrainingPolicyVersion,
        val prescription: ExercisePrescription,
        val reasons: List<TrainingPolicyReason>
    ) : TrainingPolicyResult

    data class NoGuidance(
        val reason: TrainingPolicyNoGuidanceReason
    ) : TrainingPolicyResult

    data class Failure(
        val reason: TrainingPolicyFailureReason
    ) : TrainingPolicyResult
}

class TrainingPolicyResultException(
    val result: TrainingPolicyResult
) : IllegalStateException(result.failureMessage()) {
    init {
        require(result !is TrainingPolicyResult.Applied) {
            "An applied training policy result must not be thrown as a failure."
        }
    }
}

/**
 * Applies reviewed state-based dose, effort, and rest guidance to a valid base prescription.
 *
 * The policy is pure. It never derives program state, performs I/O, reconstructs a ledger,
 * or invents a load.
 */
class StateBasedTrainingPolicy(
    private val defaults: StateBasedTrainingPolicyDefaults =
        StateBasedTrainingPolicyDefaults.V1
) {

    fun evaluate(
        exercise: Exercise,
        basePrescription: ExercisePrescription,
        profile: UserProfile,
        fitnessGoals: Set<FitnessGoal>,
        programState: TrainingProgramState,
        priorUserRestPreference: UserRestPreference? = null
    ): TrainingPolicyResult {
        if (
            programState.policyVersion !=
            TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1
        ) {
            return TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.UNSUPPORTED_TRAINING_PROGRAM_STATE_POLICY
            )
        }
        val ledger = programState.weeklyLedger
        if (ledger.policyVersion != LedgerPolicyVersion.PRIMARY_ONLY_V1) {
            return TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.UNSUPPORTED_LEDGER_POLICY
            )
        }
        if (!ledger.isWellFormed()) {
            return TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.MALFORMED_WEEKLY_LEDGER
            )
        }

        val metadata = exercise.reviewedMetadata
            ?.takeIf { it.reviewState == ReviewState.APPROVED }
            ?: return TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.MISSING_APPROVED_METADATA
            )
        if (!metadata.isWellFormedApprovedMetadata()) {
            return TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.MALFORMED_APPROVED_METADATA
            )
        }
        if (metadata.provenance.policyVersion != ledger.reviewPolicyVersion) {
            return TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.REVIEW_POLICY_VERSION_MISMATCH
            )
        }
        if (!metadata.matches(exercise.type, basePrescription.exerciseType)) {
            return TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.PRESCRIPTION_SHAPE_MISMATCH
            )
        }
        if (metadata.capabilityRequirements.any {
                profile.movementCapabilities[it] == CapabilityLevel.AVOID
            }
        ) {
            return TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.CAPABILITY_AVOID_REACHED_POLICY
            )
        }

        val doseLimits = defaults.doseLimitsByState.getValue(programState.adaptationState)
            ?: return TrainingPolicyResult.NoGuidance(
                TrainingPolicyNoGuidanceReason.PROFILE_NOT_READY
            )
        val existingDirectPrimarySets =
            ledger.directPrimarySets[metadata.directPrimaryMuscle]?.toLong() ?: 0L
        val remainingDirectPrimarySets =
            doseLimits.maxWeeklyDirectPrimarySets.toLong() - existingDirectPrimarySets
        if (remainingDirectPrimarySets <= 0L) {
            return TrainingPolicyResult.NoGuidance(
                TrainingPolicyNoGuidanceReason.WEEKLY_DIRECT_PRIMARY_ALLOWANCE_EXHAUSTED
            )
        }

        val reasons = mutableListOf<TrainingPolicyReason>()
        var targetSets = basePrescription.targetSets
        if (targetSets > doseLimits.maxTargetSetsPerExercise) {
            targetSets = doseLimits.maxTargetSetsPerExercise
            reasons += TrainingPolicyReason.STATE_DOSE_CAP
        }

        val hasRelevantLimitedCapability = metadata.capabilityRequirements.any {
            profile.movementCapabilities[it] == CapabilityLevel.LIMITED
        }
        if (hasRelevantLimitedCapability) {
            targetSets = minOf(targetSets, defaults.limitedCapabilityMaxTargetSets)
            reasons += TrainingPolicyReason.RELEVANT_LIMITED_CAPABILITY
        }
        if (remainingDirectPrimarySets < targetSets.toLong()) {
            targetSets = remainingDirectPrimarySets.toInt()
            reasons += TrainingPolicyReason.WEEKLY_DIRECT_PRIMARY_ALLOWANCE
        }

        val effortTarget = resolveEffort(
            state = programState.adaptationState,
            fitnessGoals = fitnessGoals,
            hasRelevantLimitedCapability = hasRelevantLimitedCapability,
            reasons = reasons
        )
        val restTarget = resolveRest(
            metadata = metadata,
            fitnessGoals = fitnessGoals,
            priorUserRestPreference = priorUserRestPreference,
            reasons = reasons
        )

        return TrainingPolicyResult.Applied(
            policyVersion = defaults.policyVersion,
            prescription = basePrescription.copy(
                targetSets = targetSets,
                effortTarget = effortTarget,
                restSeconds = restTarget.seconds,
                restClass = restTarget.restClass,
                restTargetSource = restTarget.source
            ),
            reasons = reasons.distinct()
        )
    }

    private fun resolveEffort(
        state: AdaptationState,
        fitnessGoals: Set<FitnessGoal>,
        hasRelevantLimitedCapability: Boolean,
        reasons: MutableList<TrainingPolicyReason>
    ): EffortTarget? = when {
        hasRelevantLimitedCapability || state in CONSERVATIVE_EFFORT_STATES -> {
            reasons += TrainingPolicyReason.CONSERVATIVE_EFFORT
            defaults.conservativeEffort
        }

        state in ESTABLISHED_STATES && FitnessGoal.STRENGTH in fitnessGoals -> {
            reasons += TrainingPolicyReason.ESTABLISHED_STRENGTH_EFFORT
            defaults.establishedStrengthEffort
        }

        state in ESTABLISHED_STATES &&
            fitnessGoals.any {
                it == FitnessGoal.GENERAL_FITNESS || it == FitnessGoal.BUILD_MUSCLE
            } -> {
            reasons += TrainingPolicyReason.ESTABLISHED_GENERAL_OR_HYPERTROPHY_EFFORT
            defaults.establishedGeneralOrHypertrophyEffort
        }

        else -> null
    }

    private fun resolveRest(
        metadata: ReviewedExerciseMetadata,
        fitnessGoals: Set<FitnessGoal>,
        priorUserRestPreference: UserRestPreference?,
        reasons: MutableList<TrainingPolicyReason>
    ): ResolvedRestTarget {
        if (priorUserRestPreference != null) {
            reasons += TrainingPolicyReason.USER_REST_PREFERENCE
            return ResolvedRestTarget(
                restClass = priorUserRestPreference.restClass,
                seconds = priorUserRestPreference.restSeconds,
                source = RestTargetSource.USER_PREFERENCE
            )
        }

        val restClass = when {
            metadata.prescriptionShape == PrescriptionShape.DURATION -> RestClass.SHORT
            (
                FitnessGoal.STRENGTH in fitnessGoals ||
                    FitnessGoal.ATHLETIC_PERFORMANCE in fitnessGoals
                ) && metadata.movementPattern != MovementPattern.ISOLATION -> RestClass.LONG

            metadata.movementPattern == MovementPattern.ISOLATION -> RestClass.SHORT
            else -> RestClass.MODERATE
        }
        reasons += TrainingPolicyReason.PRODUCT_REST_DEFAULT
        return ResolvedRestTarget(
            restClass = restClass,
            seconds = defaults.productRestSecondsByClass.getValue(restClass),
            source = RestTargetSource.PRODUCT_POLICY
        )
    }

    private data class ResolvedRestTarget(
        val restClass: RestClass,
        val seconds: Int,
        val source: RestTargetSource
    )

    private companion object {
        val CONSERVATIVE_EFFORT_STATES = setOf(
            AdaptationState.UNCALIBRATED,
            AdaptationState.INITIATE,
            AdaptationState.HOLD,
            AdaptationState.RETURNING,
            AdaptationState.DELOAD_OFFERED,
            AdaptationState.RECALIBRATE
        )
        val ESTABLISHED_STATES = setOf(
            AdaptationState.BUILD,
            AdaptationState.DEVELOP
        )
    }
}

private fun WeeklyDoseLedger.isWellFormed(): Boolean {
    if (
        catalogVersion.isBlank() ||
        catalogVersion.length > WeeklyDoseLedgerCalculator.MAX_VERSION_LENGTH ||
        timeZoneId.isBlank() ||
        timeZoneId.length > WeeklyDoseLedgerCalculator.MAX_VERSION_LENGTH ||
        reviewPolicyVersion < 0
    ) {
        return false
    }
    if (
        directPrimarySets.size > WeeklyDoseLedgerCalculator.MAX_DISTINCT_MUSCLES ||
        secondaryInvolvement.size > WeeklyDoseLedgerCalculator.MAX_DISTINCT_MUSCLES
    ) {
        return false
    }
    if (!directPrimarySets.hasWellFormedCounts() || !secondaryInvolvement.hasWellFormedCounts()) {
        return false
    }
    return unattributedWorkSets.values.all { it > 0 }
}

private fun Map<String, Int>.hasWellFormedCounts(): Boolean = all { (key, count) ->
    key.isNotBlank() &&
        key.length <= MAX_LEDGER_KEY_LENGTH &&
        key.none(Char::isISOControl) &&
        count > 0
}

private fun ReviewedExerciseMetadata.isWellFormedApprovedMetadata(): Boolean =
    directPrimaryMuscle.isNotBlank() &&
        directPrimaryMuscle.length <= MAX_LEDGER_KEY_LENGTH &&
        directPrimaryMuscle.none(Char::isISOControl) &&
        provenance.reviewerRole?.isNotBlank() == true &&
        provenance.rationaleOrSource.isNotBlank() &&
        (provenance.reviewedAtEpochMillis ?: 0L) > 0L &&
        provenance.schemaVersion > 0 &&
        provenance.policyVersion > 0

private fun ReviewedExerciseMetadata.matches(
    exerciseType: ExerciseType,
    basePrescriptionType: ExerciseType
): Boolean {
    if (exerciseType != basePrescriptionType) return false
    return when (prescriptionShape) {
        PrescriptionShape.WEIGHT_REPS -> exerciseType == ExerciseType.WEIGHT_REPS
        PrescriptionShape.BODYWEIGHT_REPS -> exerciseType == ExerciseType.BODYWEIGHT_REPS
        PrescriptionShape.ASSISTED_BODYWEIGHT ->
            exerciseType == ExerciseType.ASSISTED_BODYWEIGHT

        PrescriptionShape.DURATION -> exerciseType == ExerciseType.DURATION
    }
}

private const val MAX_LEDGER_KEY_LENGTH = 64

private fun TrainingPolicyResult.failureMessage(): String = when (this) {
    is TrainingPolicyResult.NoGuidance ->
        "Training policy produced no prescription: $reason."

    is TrainingPolicyResult.Failure ->
        "Training policy rejected its input: $reason."

    is TrainingPolicyResult.Applied ->
        "An applied training policy result is not a failure."
}
