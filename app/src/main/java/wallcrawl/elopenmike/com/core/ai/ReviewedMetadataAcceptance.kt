package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.AiReviewProvenance
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata

/**
 * The one runtime gate deciding whether reviewed metadata may drive automatic planning.
 *
 * Two states are accepted and they stay different things. Human `APPROVED` is human-only: it
 * requires the human reviewer and review time, and rejects AI provenance outright. Owner-
 * authorized `AI_ACCEPTED` requires its own AI provenance, recorded over this exact exercise,
 * and must leave every human-review field null. Nothing here promotes an acceptance into an
 * approval; it only says both may be planned from.
 *
 * Everything else fails closed. A draft, a record whose provenance does not match its state,
 * an AI acceptance on a pre-AI schema version, and any malformed reviewed fact are all simply
 * not accepted — there is no third branch that falls back to legacy metadata.
 *
 * ## Relationship edges are not endpoint acceptance
 *
 * An accepted record may name `approvedRegressions` or `approvedSubstitutions` targets that
 * are still pending. That authorizes the relationship and nothing else: a consumer that wants
 * to *use* an edge has to run its target through this same gate. Holding such an edge never
 * makes the source's own metadata unaccepted, and nothing here infers or widens a graph link.
 *
 * ## What this is not
 *
 * Not a parser, and not the trust boundary. `WorkoutGuideCatalogParser` already rejects
 * malformed records at import. This is the runtime's own fail-closed re-check, so a record
 * reaching a policy from any source is read by one rule rather than by nine partial ones.
 */

/**
 * The accepted reviewed metadata of [this], or null when automatic planning may not use it.
 *
 * The exercise id is part of the check: an AI acceptance records the content it decided over,
 * and an acceptance describing some other exercise is not an acceptance of this one.
 */
internal fun Exercise.acceptedMetadata(): ReviewedExerciseMetadata? = reviewedMetadata
    ?.takeIf { it.reviewState.isAcceptedReviewState() }
    ?.takeIf { it.isWellFormedAcceptedMetadata(id) }

/**
 * Whether this state is one automatic planning may act on at all, before any well-formedness.
 *
 * Exhaustive on purpose: a review state added later fails compilation here, so it has to be
 * accepted or refused deliberately rather than inheriting either answer.
 */
internal fun ReviewState.isAcceptedReviewState(): Boolean = when (this) {
    ReviewState.APPROVED, ReviewState.AI_ACCEPTED -> true
    ReviewState.DRAFT -> false
}

/**
 * Whether an accepted record carries the provenance and reviewed facts its state requires.
 *
 * Separate from [isAcceptedReviewState] so a caller that reports "not accepted yet" and
 * "accepted but damaged" as different outcomes can still read one implementation of each.
 */
internal fun ReviewedExerciseMetadata.isWellFormedAcceptedMetadata(exerciseId: String): Boolean {
    if (!directPrimaryMuscle.isWellFormedReviewedKey()) return false
    if (descriptiveSecondaryMuscles.any { !it.isWellFormedReviewedKey() }) return false
    if (provenance.rationaleOrSource.isBlank()) return false
    if (provenance.schemaVersion <= 0 || provenance.policyVersion <= 0) return false
    return when (reviewState) {
        ReviewState.APPROVED ->
            provenance.reviewerRole?.isNotBlank() == true &&
                (provenance.reviewedAtEpochMillis ?: 0L) > 0L &&
                aiReviewProvenance == null

        ReviewState.AI_ACCEPTED ->
            provenance.reviewerRole == null &&
                provenance.reviewedAtEpochMillis == null &&
                provenance.schemaVersion == AI_ACCEPTED_SCHEMA_VERSION &&
                aiReviewProvenance.isWellFormedAcceptanceOf(exerciseId)

        ReviewState.DRAFT -> false
    }
}

private fun AiReviewProvenance?.isWellFormedAcceptanceOf(exerciseId: String): Boolean {
    val provenance = this ?: return false
    return provenance.reviewerModelId.isNotBlank() &&
        provenance.reviewedAtEpochMillis > 0L &&
        provenance.reviewedContentId == exerciseId &&
        SHA256_HEX.matches(provenance.reviewedContentSha256) &&
        provenance.sourceReferences.isNotEmpty() &&
        // Authored strings stay on one safe scheme, matching the importer and the parser.
        provenance.sourceReferences.all { it.startsWith("https://") } &&
        provenance.decisionRationale.isNotBlank() &&
        provenance.limitations.isNotBlank() &&
        provenance.schemaVersion == AI_ACCEPTED_SCHEMA_VERSION &&
        provenance.policyVersion > 0
}

private fun String.isWellFormedReviewedKey(): Boolean =
    isNotBlank() && length <= MAX_REVIEWED_MUSCLE_KEY_LENGTH && none(Char::isISOControl)

/**
 * The reviewed schema version that first carried the AI acceptance contract.
 *
 * A floor rather than an equality: a v2 record predates the contract entirely, so an AI
 * feature is refused on one, while a later version that still satisfies the shape stays
 * readable. `WorkoutGuideCatalogParser` pins the exact authored version at the import
 * boundary; this is the runtime's independent refusal.
 */
private const val AI_ACCEPTED_SCHEMA_VERSION = 3

private val SHA256_HEX = Regex("[0-9a-f]{64}")

/**
 * The bound a credited muscle name shares with the weekly ledger key it becomes.
 *
 * One constant because they are one value: accepted metadata whose muscle exceeded the
 * ledger's own key bound would produce a ledger that the same policy then calls malformed.
 */
internal const val MAX_REVIEWED_MUSCLE_KEY_LENGTH = 64
