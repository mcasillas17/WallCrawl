package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MuscleDoseAccounting
import wallcrawl.elopenmike.com.core.model.RecommendationRecord
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion

/** The versioned whole-program validation contract a recommendation was checked under. */
enum class ProgramValidatorVersion {
    WHOLE_PROGRAM_V1
}

/**
 * What whole-program validation concluded about a proposal it accepted.
 *
 * There is no rejected value: a rejected proposal is never displayed and never persisted,
 * so no snapshot is built for one and no outcome is ever recorded for it.
 */
enum class RecommendationOutcome {
    /** Valid as proposed. */
    VALID,

    /** Valid after exactly one deterministic repair pass reduced sets. */
    REPAIRED
}

/**
 * The immutable evidence recorded with a validated recommendation.
 *
 * ## What it is for
 *
 * A completed session should be explainable later: which versioned inputs produced it, what
 * state it was built under, which week it was accounted against, what the validator
 * concluded, and why. Every field here exists to answer one of those questions without
 * re-running the planner.
 *
 * ## What it deliberately omits
 *
 * The plan's own exercises and prescriptions are not duplicated: a started session already
 * stores them. Nothing personal is present — no name, note, load, repetition count, effort
 * value, body measurement, or free text — so a recorded snapshot can never become a
 * fingerprint of someone's training log.
 *
 * ## What it does not claim
 *
 * WallCrawl keeps one current profile row, so a byte-exact re-derivation of a past
 * candidate set is not possible and is not promised. [contextIdentity] detects that the
 * inputs changed; it does not reconstruct them. Recording a validation outcome is software
 * provenance and never scientific or clinical validation of the algorithm.
 */
data class RecommendationSnapshot(
    val validatorVersion: ProgramValidatorVersion,
    val durationEstimatorVersion: String,
    val outcome: RecommendationOutcome,
    /** Whether the reviewed-only rule set applied, which is the disabled production gate. */
    val reviewedPathEnabled: Boolean,
    val catalogVersion: String?,
    val reviewPolicyVersion: Int,
    /** Present only on the reviewed path, where a dose policy actually ran. */
    val trainingPolicyVersion: TrainingPolicyVersion?,
    val ledgerPolicyVersion: LedgerPolicyVersion?,
    val programStatePolicyVersion: TrainingProgramStatePolicyVersion?,
    val adaptationState: AdaptationState?,
    val weekStartEpochDay: Long?,
    val timeZoneId: String?,
    /**
     * Which revision of the profile produced the plan.
     *
     * The profile's identity is already inside [contextIdentity]; this is the part a reader
     * of a stored record can compare against the profile in front of them.
     */
    val profileRevision: Long,
    val contextIdentity: String,
    /** Ordered, deduplicated reason codes. Empty when nothing was reported. */
    val reasonCodes: List<ProgramViolationCode>,
    val doseAccounting: List<MuscleDoseAccounting>
) {
    init {
        require(contextIdentity.isNotBlank()) { "A snapshot carries a context identity." }
        require(reasonCodes.size == reasonCodes.distinct().size) {
            "Reason codes are deduplicated before they are recorded."
        }
        require(doseAccounting.map { it.muscle }.distinct().size == doseAccounting.size) {
            "Each muscle is accounted for exactly once."
        }
        require(outcome != RecommendationOutcome.VALID || reasonCodes.isEmpty()) {
            "A recommendation that needed no change carries no reason codes."
        }
    }
}

/**
 * The stored form of this snapshot for one started session.
 *
 * The mapping is deliberately name-based: every version becomes the enum constant's own
 * name, so a stored value never depends on an ordinal that a later build could renumber.
 */
fun RecommendationSnapshot.asRecord(
    sessionId: String,
    recordedAtEpochMillis: Long
): RecommendationRecord = RecommendationRecord(
    sessionId = sessionId,
    validatorVersion = validatorVersion.name,
    durationEstimatorVersion = durationEstimatorVersion,
    outcome = outcome.name,
    reviewedPathEnabled = reviewedPathEnabled,
    catalogVersion = catalogVersion,
    reviewPolicyVersion = reviewPolicyVersion,
    trainingPolicyVersion = trainingPolicyVersion?.name,
    ledgerPolicyVersion = ledgerPolicyVersion?.name,
    programStatePolicyVersion = programStatePolicyVersion?.name,
    adaptationState = adaptationState?.name,
    weekStartEpochDay = weekStartEpochDay,
    timeZoneId = timeZoneId,
    profileRevision = profileRevision,
    contextIdentity = contextIdentity,
    reasonCodes = reasonCodes.map { it.name },
    doseAccounting = doseAccounting,
    recordedAtEpochMillis = recordedAtEpochMillis
)
