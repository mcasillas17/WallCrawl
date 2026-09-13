package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseLink
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.StandardMuscles

/**
 * The one runtime gate that decides whether reviewed metadata may drive automatic planning.
 *
 * Human `APPROVED` and owner-authorized `AI_ACCEPTED` are both accepted, and everything else
 * fails closed. The two states stay distinguishable in every other respect: `APPROVED` still
 * requires human provenance and rejects AI provenance, and `AI_ACCEPTED` still requires AI
 * provenance and rejects a human reviewer or review time, so an acceptance can never read as
 * a human sign-off.
 */
class ReviewedMetadataAcceptanceTest {

    @Test
    fun acceptedMetadata_acceptsWellFormedHumanApproval() {
        val exercise = exercise("synthetic-approved", metadata(ReviewState.APPROVED))

        assertThat(exercise.acceptedMetadata()).isSameInstanceAs(exercise.reviewedMetadata)
    }

    @Test
    fun acceptedMetadata_acceptsWellFormedAiAcceptance() {
        val exercise = exercise("synthetic-ai-accepted", metadata(ReviewState.AI_ACCEPTED))

        assertThat(exercise.acceptedMetadata()).isSameInstanceAs(exercise.reviewedMetadata)
    }

    @Test
    fun acceptedMetadata_rejectsDraft() {
        val exercise = exercise("synthetic-draft", metadata(ReviewState.DRAFT))

        assertThat(exercise.acceptedMetadata()).isNull()
    }

    @Test
    fun acceptedMetadata_rejectsMissingReviewedMetadata() {
        assertThat(exercise("synthetic-none", null).acceptedMetadata()).isNull()
    }

    @Test
    fun acceptedReviewStates_areExactlyHumanApprovalAndAiAcceptance() {
        // A state added later has to be classified deliberately, never accepted by default.
        assertThat(ReviewState.entries.filter { it.isAcceptedReviewState() })
            .containsExactly(ReviewState.APPROVED, ReviewState.AI_ACCEPTED)
    }

    @Test
    fun acceptedMetadata_rejectsApprovalWithoutHumanProvenance() {
        val missingReviewer = metadata(ReviewState.APPROVED).copy(
            provenance = syntheticProvenance(ReviewState.APPROVED, reviewerRole = null)
        )
        val blankReviewer = metadata(ReviewState.APPROVED).copy(
            provenance = syntheticProvenance(ReviewState.APPROVED, reviewerRole = "   ")
        )
        val missingReviewTime = metadata(ReviewState.APPROVED).copy(
            provenance = syntheticProvenance(ReviewState.APPROVED, reviewedAtEpochMillis = null)
        )
        val nonPositiveReviewTime = metadata(ReviewState.APPROVED).copy(
            provenance = syntheticProvenance(ReviewState.APPROVED, reviewedAtEpochMillis = 0L)
        )

        listOf(missingReviewer, blankReviewer, missingReviewTime, nonPositiveReviewTime)
            .forEach { reviewed ->
                assertThat(exercise("synthetic-approved", reviewed).acceptedMetadata()).isNull()
            }
    }

    @Test
    fun acceptedMetadata_rejectsApprovalCarryingAiProvenance() {
        // Human approval stays human-only: AI provenance on an APPROVED record is malformed,
        // never a second, softer way to satisfy the same state.
        val reviewed = metadata(ReviewState.APPROVED).copy(
            aiReviewProvenance = syntheticAiProvenance(
                reviewState = ReviewState.AI_ACCEPTED,
                reviewedContentId = "synthetic-approved"
            )
        )

        assertThat(exercise("synthetic-approved", reviewed).acceptedMetadata()).isNull()
    }

    @Test
    fun acceptedMetadata_rejectsAiAcceptanceWithoutAiProvenance() {
        val reviewed = metadata(ReviewState.AI_ACCEPTED).copy(aiReviewProvenance = null)

        assertThat(exercise("synthetic-ai-accepted", reviewed).acceptedMetadata()).isNull()
    }

    @Test
    fun acceptedMetadata_rejectsAiAcceptanceClaimingHumanReview() {
        val claimsReviewer = metadata(ReviewState.AI_ACCEPTED).copy(
            provenance = syntheticProvenance(
                reviewState = ReviewState.AI_ACCEPTED,
                reviewerRole = SYNTHETIC_REVIEWER_ROLE
            )
        )
        val claimsReviewTime = metadata(ReviewState.AI_ACCEPTED).copy(
            provenance = syntheticProvenance(
                reviewState = ReviewState.AI_ACCEPTED,
                reviewedAtEpochMillis = SYNTHETIC_REVIEWED_AT_EPOCH_MILLIS
            )
        )

        listOf(claimsReviewer, claimsReviewTime).forEach { reviewed ->
            assertThat(exercise("synthetic-ai-accepted", reviewed).acceptedMetadata()).isNull()
        }
    }

    @Test
    fun acceptedMetadata_rejectsAiAcceptanceRecordedOverADifferentExercise() {
        val reviewed = metadata(ReviewState.AI_ACCEPTED).copy(
            aiReviewProvenance = syntheticAiProvenance(
                reviewState = ReviewState.AI_ACCEPTED,
                reviewedContentId = "some-other-exercise"
            )
        )

        assertThat(exercise("synthetic-ai-accepted", reviewed).acceptedMetadata()).isNull()
    }

    @Test
    fun acceptedMetadata_rejectsAiAcceptanceOnAPreAiSchemaVersion() {
        // v2 predates the AI contract, so an AI feature must be refused on a v2 record.
        val reviewed = metadata(ReviewState.AI_ACCEPTED).copy(
            provenance = syntheticProvenance(ReviewState.AI_ACCEPTED, schemaVersion = 2),
            aiReviewProvenance = syntheticAiProvenance(
                reviewState = ReviewState.AI_ACCEPTED,
                reviewedContentId = "synthetic-ai-accepted",
                schemaVersion = 2
            )
        )

        assertThat(exercise("synthetic-ai-accepted", reviewed).acceptedMetadata()).isNull()
    }

    @Test
    fun acceptedMetadata_rejectsMalformedAiProvenanceFacts() {
        val aiProvenance = requireNotNull(
            syntheticAiProvenance(ReviewState.AI_ACCEPTED, "synthetic-ai-accepted")
        )
        val malformed = listOf(
            aiProvenance.copy(reviewerModelId = " "),
            aiProvenance.copy(reviewedAtEpochMillis = 0L),
            aiProvenance.copy(reviewedContentSha256 = "not-a-digest"),
            aiProvenance.copy(reviewedContentSha256 = aiProvenance.reviewedContentSha256.uppercase()),
            aiProvenance.copy(sourceReferences = emptyList()),
            aiProvenance.copy(sourceReferences = listOf("http://example.test/insecure")),
            aiProvenance.copy(decisionRationale = " "),
            aiProvenance.copy(limitations = " "),
            aiProvenance.copy(policyVersion = 0)
        )

        malformed.forEach { provenance ->
            val reviewed = metadata(ReviewState.AI_ACCEPTED).copy(aiReviewProvenance = provenance)
            assertThat(exercise("synthetic-ai-accepted", reviewed).acceptedMetadata()).isNull()
        }
    }

    @Test
    fun acceptedMetadata_rejectsMalformedReviewedFactsInEitherAcceptedState() {
        ReviewState.entries.filter { it.isAcceptedReviewState() }.forEach { state ->
            val wellFormed = metadata(state)
            val malformed = listOf(
                wellFormed.copy(directPrimaryMuscle = "   "),
                wellFormed.copy(directPrimaryMuscle = "a".repeat(65)),
                wellFormed.copy(directPrimaryMuscle = "Chest\u0007"),
                wellFormed.copy(
                    provenance = wellFormed.provenance.copy(rationaleOrSource = " ")
                ),
                wellFormed.copy(provenance = wellFormed.provenance.copy(schemaVersion = 0)),
                wellFormed.copy(provenance = wellFormed.provenance.copy(policyVersion = 0))
            )

            malformed.forEach { reviewed ->
                assertThat(exercise(idFor(state), reviewed).acceptedMetadata()).isNull()
            }
        }
    }

    @Test
    fun acceptedMetadata_rejectsMalformedSecondaryMuscleKeys() {
        listOf("", " ".repeat(2), "x".repeat(MAX_REVIEWED_MUSCLE_KEY_LENGTH + 1), "Chest\u0000")
            .forEach { invalidKey ->
                val reviewed = metadata(ReviewState.AI_ACCEPTED).copy(
                    descriptiveSecondaryMuscles = setOf(invalidKey)
                )

                assertThat(exercise("synthetic-ai-accepted", reviewed).acceptedMetadata()).isNull()
            }
    }

    @Test
    fun acceptedMetadata_isUnaffectedByAnEdgeToAPendingEndpoint() {
        // Relationship authorization and endpoint acceptance are separate concerns. A source
        // that points at a draft is still an accepted source; what the edge may be used for is
        // decided by the endpoint's own acceptance, at each consumer.
        val reviewed = metadata(ReviewState.AI_ACCEPTED).copy(
            approvedRegressions = listOf(ReviewedExerciseLink("synthetic-draft-endpoint")),
            approvedSubstitutions = listOf(ReviewedExerciseLink("synthetic-missing-endpoint"))
        )

        assertThat(exercise("synthetic-ai-accepted", reviewed).acceptedMetadata())
            .isSameInstanceAs(reviewed)
    }

    /** Metadata whose AI acceptance, when it has one, was recorded over [exerciseId] itself. */
    private fun metadata(
        reviewState: ReviewState,
        exerciseId: String = idFor(reviewState)
    ): ReviewedExerciseMetadata = syntheticReviewedMetadata(
        reviewState = reviewState,
        directPrimaryMuscle = StandardMuscles.CHEST,
        exerciseId = exerciseId
    )

    private fun idFor(reviewState: ReviewState): String = when (reviewState) {
        ReviewState.APPROVED -> "synthetic-approved"
        ReviewState.AI_ACCEPTED -> "synthetic-ai-accepted"
        ReviewState.DRAFT -> "synthetic-draft"
    }

    private fun exercise(id: String, reviewed: ReviewedExerciseMetadata?): Exercise = Exercise(
        id = id,
        name = "Synthetic $id",
        primaryMuscles = listOf(StandardMuscles.CHEST),
        type = ExerciseType.WEIGHT_REPS,
        reviewedMetadata = reviewed
    )
}
