package wallcrawl.elopenmike.com.core.ai

/**
 * Why a proposed session was rejected, as a stable code rather than a sentence.
 *
 * Names are the persisted identity: they are recorded with a started session, compared in
 * tests, and mapped to resource-backed copy by the screen. Renaming one is a data change.
 *
 * Each code names its rationale as either a **software invariant** — internal integrity,
 * consistency, or persistence correctness — or a **product policy**, a named versioned
 * WallCrawl choice. None of them is a physiological, clinical, or safety claim.
 *
 * Declaration order is the report order, so a rejection lists structural problems before
 * program-design ones and program-design ones before accounting.
 */
enum class ProgramViolationCode {
    /** Software invariant: a recommendation with no exercises is not a session. */
    EMPTY_RECOMMENDATION,

    /** Software invariant: the estimate must be a representable number of minutes. */
    DURATION_OUT_OF_BOUNDS,

    /** Software invariant: an exercise instance must name something. */
    BLANK_EXERCISE_ID,

    /** Software invariant: the id must exist in the bundled catalog, never be guessed at. */
    UNKNOWN_EXERCISE_ID,

    /** Software invariant: output must stay inside the candidate set the context allowed. */
    NOT_IN_CANDIDATE_SET,

    /** Software invariant: the prescription's type must equal the catalog exercise's type. */
    PRESCRIPTION_TYPE_MISMATCH,

    /**
     * Product policy: the declared `uniqueExerciseIds` constraint for this session.
     *
     * It is about accounting and identity inside one generated session, not a claim that
     * repeating a movement is harmful, and it never applies across sessions or weeks.
     */
    DUPLICATE_EXERCISE_IN_SESSION,

    /** Product policy: the declared `uniqueProgressionFamilies` constraint, when declared. */
    DUPLICATE_PROGRESSION_FAMILY,

    /** Product policy: a movement pattern the caller explicitly declared as required. */
    MISSING_REQUIRED_MOVEMENT_PATTERN,

    /** Product policy: an exercise the user excluded, or one the enabled path ruled out. */
    EXPLICIT_CONSTRAINT_VIOLATED,

    /** Software invariant, reviewed path: automatic planning needs `APPROVED` metadata. */
    MISSING_APPROVED_METADATA,

    /** Software invariant, reviewed path: metadata and ledger must share a review policy. */
    REVIEW_POLICY_VERSION_MISMATCH,

    /** Software invariant, reviewed path: the approved shape must match what was prescribed. */
    PRESCRIPTION_SHAPE_MISMATCH,

    /** Software invariant: a prescribed load must trace to a confirmed or recorded source. */
    UNTRACEABLE_LOAD,

    /** Software invariant: the reported estimate must agree with the named estimator. */
    DURATION_ESTIMATE_MISMATCH,

    /** Software invariant: the weekly ledger itself is unusable, not merely full. */
    MALFORMED_WEEKLY_LEDGER,

    /** Software invariant: the accounting could not be represented, not merely exceeded. */
    DOSE_ACCOUNTING_OVERFLOW,

    /**
     * Product policy: a configured weekly allowance was exceeded across the whole proposal.
     *
     * This is a mismatch with a versioned WallCrawl number, never evidence of overload or
     * medical danger.
     */
    WEEKLY_ALLOWANCE_EXCEEDED
}

/**
 * One reported problem, located precisely enough to act on.
 *
 * [detail] is a stable machine token — a muscle name, a pattern name, a pair of minute
 * counts — kept for logs and tests. It is never shown to a user and never persisted, so a
 * recorded reason stays bounded and free of anything personal.
 */
data class ProgramViolation(
    val code: ProgramViolationCode,
    val exerciseId: String? = null,
    val orderIndex: Int? = null,
    val detail: String? = null
) : Comparable<ProgramViolation> {

    /**
     * Total, deterministic ordering.
     *
     * Two runs over identical inputs must report identical reasons in an identical order,
     * because that order is recorded with the session and compared during replay.
     * Program-level problems sort before per-exercise ones within the same code.
     */
    override fun compareTo(other: ProgramViolation): Int =
        compareValuesBy(
            this,
            other,
            { it.code.ordinal },
            { it.orderIndex ?: -1 },
            { it.exerciseId.orEmpty() },
            { it.detail.orEmpty() }
        )
}
