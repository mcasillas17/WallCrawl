package wallcrawl.elopenmike.com.core.model

/**
 * The versioned crediting policy a [WeeklyDoseLedger] was produced under.
 *
 * `PRIMARY_ONLY_V1` credits exactly one approved direct-primary muscle per completed work
 * set. Descriptive secondary muscles receive no dose credit at all; fractional secondary
 * crediting would be a different, separately versioned policy.
 */
enum class LedgerPolicyVersion {
    PRIMARY_ONLY_V1
}

/**
 * Why a completed work set could not be credited to a muscle.
 *
 * A set that cannot be attributed is counted here rather than guessed at, so a week's
 * reported exposure never silently absorbs work whose muscle is unknown.
 */
enum class LedgerOmissionReason {
    /** The exercise instance names an id the current bundled catalog does not contain. */
    UNKNOWN_EXERCISE,

    /** The exercise exists but carries no reviewed metadata block. */
    MISSING_REVIEWED_METADATA,

    /** Reviewed metadata exists but has not been approved by a human reviewer. */
    METADATA_NOT_APPROVED
}

/**
 * Resistance-training exposure for one ISO week, reconstructed from completed history.
 *
 * The ledger is derived, never accumulated: nothing increments it while a workout is
 * generated or a set is logged. Identical versioned inputs always produce an identical
 * ledger, which is what makes a week auditable and replayable.
 *
 * Every map iterates in a stable order — muscles by canonical name, omissions by reason —
 * so equal ledgers also serialize identically.
 */
data class WeeklyDoseLedger(
    val policyVersion: LedgerPolicyVersion,
    val weekStartEpochDay: Long,
    val timeZoneId: String,
    val catalogVersion: String,
    val reviewPolicyVersion: Int,
    val directPrimarySets: Map<String, Int>,
    val secondaryInvolvement: Map<String, Int>,
    val unattributedWorkSets: Map<LedgerOmissionReason, Int>
) {
    /** Completed work sets credited to a direct-primary muscle under this policy. */
    val creditedWorkSets: Int get() = directPrimarySets.values.sum()

    /** Completed work sets that were deliberately left uncredited, with typed reasons. */
    val omittedWorkSets: Int get() = unattributedWorkSets.values.sum()

    /**
     * Every counted unit this ledger holds, across all three maps.
     *
     * This is deliberately not a work-set count: secondary involvement contributes one unit
     * per descriptive secondary muscle, so it grows faster than exposure does. It exists so
     * the producer and the storage codec can bound the same quantity, which is what
     * guarantees a ledger can always be read back from its own payload.
     */
    val totalCountedUnits: Long
        get() = directPrimarySets.values.sumOf(Int::toLong) +
            secondaryInvolvement.values.sumOf(Int::toLong) +
            unattributedWorkSets.values.sumOf(Int::toLong)
}
