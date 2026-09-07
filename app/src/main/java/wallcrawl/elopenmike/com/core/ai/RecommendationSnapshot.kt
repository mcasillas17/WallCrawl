package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion

/** The versioned whole-program validation contract a recommendation was checked under. */
enum class ProgramValidatorVersion {
    WHOLE_PROGRAM_V1
}

/** What whole-program validation concluded about one proposed session. */
enum class RecommendationOutcome {
    /** Valid as proposed. */
    VALID,

    /** Valid after exactly one deterministic repair pass reduced sets. */
    REPAIRED,

    /** Rejected. Nothing is displayed or persisted from a rejected proposal. */
    REJECTED
}

/**
 * Prospective and completed exposure for one approved direct-primary muscle.
 *
 * The two counts stay separate on purpose. [completedSets] is reconstructed from completed
 * history by `PRIMARY_ONLY_V1`; [proposedSets] is what a not-yet-performed plan would add.
 * Summing them into one stored number would let a proposal that was never performed look
 * like work the user did, which is the thing weekly accounting must never do.
 *
 * [allowanceSets] is the configured product allowance for the adaptation state the plan was
 * built under, or null when that state configures no dose guidance. It is a versioned
 * WallCrawl choice, not a physiological ceiling.
 */
data class MuscleDoseAccounting(
    val muscle: String,
    val completedSets: Int,
    val proposedSets: Int,
    val allowanceSets: Int?
) {
    init {
        require(muscle.isNotBlank() && muscle.length <= MAX_MUSCLE_LENGTH) {
            "Dose accounting needs a non-blank muscle of at most $MAX_MUSCLE_LENGTH characters."
        }
        require(muscle.none(Char::isISOControl)) {
            "A dose accounting muscle cannot contain control characters."
        }
        require(completedSets >= 0 && proposedSets >= 0) {
            "Dose accounting counts cannot be negative."
        }
        require(allowanceSets == null || allowanceSets > 0) {
            "A configured allowance is positive when it exists at all."
        }
    }

    private companion object {
        const val MAX_MUSCLE_LENGTH = 64
    }
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
    val profileId: String,
    val profileRevision: Long,
    val contextIdentity: String,
    /** Ordered, deduplicated reason codes. Empty when nothing was reported. */
    val reasonCodes: List<ProgramViolationCode>,
    val doseAccounting: List<MuscleDoseAccounting>
) {
    init {
        require(profileId.isNotBlank()) { "A snapshot names the profile it was built for." }
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
