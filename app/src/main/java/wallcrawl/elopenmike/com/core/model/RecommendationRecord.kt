package wallcrawl.elopenmike.com.core.model

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

    companion object {
        const val MAX_MUSCLE_LENGTH: Int = 64
    }
}

/**
 * The stored, immutable provenance of one started session's recommendation.
 *
 * ## Why the version fields are text
 *
 * Every version-like field is a string rather than a converted enum, for the same reason
 * the weekly-ledger cache stores its policy version as text: a value written by a future
 * build has to read back as something this build does not recognise, instead of being
 * coerced into a meaning it never had. Nothing in the app branches on these values; they
 * exist to be read back and compared.
 *
 * ## What it deliberately omits
 *
 * The plan's own exercises and prescriptions are not duplicated — a started session already
 * stores them under the same [sessionId]. Nothing personal is present: no name, note, load,
 * repetition count, effort value, body measurement, or free text.
 *
 * ## What it does not claim
 *
 * [contextIdentity] detects that the generation inputs changed; it does not reconstruct
 * them, and WallCrawl keeps only one current profile row, so a byte-exact re-derivation of
 * a past candidate set is not promised. Recording a validation outcome is software
 * provenance, never scientific or clinical validation of the algorithm.
 */
data class RecommendationRecord(
    val sessionId: String,
    val validatorVersion: String,
    val durationEstimatorVersion: String,
    val outcome: String,
    val reviewedPathEnabled: Boolean,
    val catalogVersion: String?,
    val reviewPolicyVersion: Int,
    val trainingPolicyVersion: String?,
    val ledgerPolicyVersion: String?,
    val programStatePolicyVersion: String?,
    val adaptationState: String?,
    val weekStartEpochDay: Long?,
    val timeZoneId: String?,
    val profileRevision: Long,
    val contextIdentity: String,
    /** Ordered, deduplicated reason-code names. Empty when nothing was reported. */
    val reasonCodes: List<String>,
    val doseAccounting: List<MuscleDoseAccounting>,
    val recordedAtEpochMillis: Long
) {
    init {
        requireToken(sessionId, "sessionId")
        requireToken(validatorVersion, "validatorVersion")
        requireToken(durationEstimatorVersion, "durationEstimatorVersion")
        requireToken(outcome, "outcome")
        catalogVersion?.let { requireToken(it, "catalogVersion") }
        trainingPolicyVersion?.let { requireToken(it, "trainingPolicyVersion") }
        ledgerPolicyVersion?.let { requireToken(it, "ledgerPolicyVersion") }
        programStatePolicyVersion?.let { requireToken(it, "programStatePolicyVersion") }
        adaptationState?.let { requireToken(it, "adaptationState") }
        timeZoneId?.let { requireToken(it, "timeZoneId") }
        requireToken(contextIdentity, "contextIdentity")

        require(reviewPolicyVersion >= 0) { "reviewPolicyVersion cannot be negative." }
        require(profileRevision >= 0) { "profileRevision cannot be negative." }
        require(recordedAtEpochMillis >= 0) { "recordedAtEpochMillis cannot be negative." }
        require(reasonCodes.size <= MAX_REASON_CODES) {
            "A record cannot carry more than $MAX_REASON_CODES reason codes."
        }
        require(reasonCodes.size == reasonCodes.distinct().size) {
            "Reason codes are deduplicated before they are recorded."
        }
        reasonCodes.forEach { requireToken(it, "reasonCode") }
        require(doseAccounting.size <= MAX_DOSE_ENTRIES) {
            "A record cannot account for more than $MAX_DOSE_ENTRIES muscles."
        }
        require(doseAccounting.map { it.muscle }.distinct().size == doseAccounting.size) {
            "Each muscle is accounted for exactly once."
        }
        // Canonical order, for the same reason the weekly ledger has one: equal accounting
        // must serialize identically, so a stored row and an exported document round trip
        // to exactly the record they came from rather than to a reordered equivalent.
        require(doseAccounting == doseAccounting.sortedBy { it.muscle }) {
            "Dose accounting is recorded in muscle order."
        }
    }

    private fun requireToken(value: String, label: String) {
        require(value.isNotBlank() && value.length <= MAX_TOKEN_LENGTH) {
            "$label must be non-blank and at most $MAX_TOKEN_LENGTH characters."
        }
        require(value.none(Char::isISOControl)) {
            "$label cannot contain control characters."
        }
    }

    companion object {
        const val MAX_TOKEN_LENGTH: Int = 200
        const val MAX_REASON_CODES: Int = 64
        const val MAX_DOSE_ENTRIES: Int = 64
    }
}
