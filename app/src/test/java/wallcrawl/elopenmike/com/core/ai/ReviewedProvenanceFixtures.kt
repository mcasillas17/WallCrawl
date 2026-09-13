package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.AiReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewState

/**
 * The state/provenance pairing every synthetic reviewed block in the unit tests is built from.
 *
 * One definition, because the pairing is the contract under test: `APPROVED` carries human
 * review fields and no AI provenance, `AI_ACCEPTED` carries AI provenance and no human review
 * fields, and `DRAFT` carries neither. Spelled out per test file it would be tightened in some
 * and not others, which is exactly the drift the runtime acceptance seam exists to prevent.
 *
 * Every value here is synthetic and says so in its own text. Nothing in `src/main` can read
 * this file, so a synthetic approval or acceptance can never reach the bundled catalog.
 */
const val SYNTHETIC_REVIEWER_ROLE: String = "SYNTHETIC_TEST_REVIEWER_NOT_A_HUMAN"

const val SYNTHETIC_REVIEW_RATIONALE: String =
    "SYNTHETIC TEST FIXTURE. Not a human review and never shipped in the bundled catalog."

const val SYNTHETIC_AI_REVIEWER_MODEL_ID: String = "synthetic-test-model-not-a-human-reviewer"

/** The schema version an AI acceptance requires; v2 records predate the AI contract. */
const val SYNTHETIC_AI_SCHEMA_VERSION: Int = 3

const val SYNTHETIC_REVIEWED_AT_EPOCH_MILLIS: Long = 1_756_000_000_000L

/** A syntactically valid SHA-256 hex digest; it digests nothing real. */
const val SYNTHETIC_CONTENT_SHA256: String =
    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

/**
 * Human-review provenance for [reviewState], with the human fields present only for `APPROVED`.
 *
 * [schemaVersion] defaults per state: an AI acceptance needs v3, while a v2-authored human
 * approval stays exactly what it was authored as.
 */
fun syntheticProvenance(
    reviewState: ReviewState,
    policyVersion: Int = 1,
    schemaVersion: Int = if (reviewState == ReviewState.AI_ACCEPTED) {
        SYNTHETIC_AI_SCHEMA_VERSION
    } else {
        2
    },
    reviewerRole: String? = if (reviewState == ReviewState.APPROVED) SYNTHETIC_REVIEWER_ROLE else null,
    reviewedAtEpochMillis: Long? = if (reviewState == ReviewState.APPROVED) {
        SYNTHETIC_REVIEWED_AT_EPOCH_MILLIS
    } else {
        null
    }
): ReviewProvenance = ReviewProvenance(
    reviewerRole = reviewerRole,
    rationaleOrSource = SYNTHETIC_REVIEW_RATIONALE,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
    schemaVersion = schemaVersion,
    policyVersion = policyVersion
)

/**
 * AI provenance for [reviewState], which is null for every state but `AI_ACCEPTED`.
 *
 * [reviewedContentId] is the id of the exercise the acceptance was recorded over. Passing a
 * different id is how a test builds an acceptance that describes some other exercise.
 */
fun syntheticAiProvenance(
    reviewState: ReviewState,
    reviewedContentId: String,
    policyVersion: Int = 1,
    schemaVersion: Int = SYNTHETIC_AI_SCHEMA_VERSION,
    sourceReferences: List<String> = listOf("https://example.test/synthetic-reference")
): AiReviewProvenance? = if (reviewState != ReviewState.AI_ACCEPTED) {
    null
} else {
    AiReviewProvenance(
        reviewerModelId = SYNTHETIC_AI_REVIEWER_MODEL_ID,
        reviewedAtEpochMillis = SYNTHETIC_REVIEWED_AT_EPOCH_MILLIS,
        reviewedContentId = reviewedContentId,
        reviewedContentSha256 = SYNTHETIC_CONTENT_SHA256,
        sourceReferences = sourceReferences,
        decisionRationale =
            "SYNTHETIC TEST FIXTURE. Owner-authorized alpha acceptance, never a human review.",
        limitations = "SYNTHETIC TEST FIXTURE. Not a human review and not clinical clearance.",
        schemaVersion = schemaVersion,
        policyVersion = policyVersion
    )
}
