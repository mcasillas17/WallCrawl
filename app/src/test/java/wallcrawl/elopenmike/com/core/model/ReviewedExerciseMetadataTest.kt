package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReviewedExerciseMetadataTest {

    @Test
    fun exercise_keepsReviewedMetadataSeparateFromLegacyProgramming() {
        val legacy = ExerciseProgrammingMetadata(
            requiredEquipmentCombinations = listOf(listOf(StandardEquipment.BODYWEIGHT)),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            difficulty = Difficulty.BEGINNER,
            mechanics = MechanicsType.COMPOUND,
            recommendedRepRange = RepRange(8, 12),
            fatigueScore = 2,
            progressionType = ProgressionType.REPETITIONS,
            coachingSummary = "Legacy planner metadata."
        )
        val reviewed = ReviewedExerciseMetadata(
            reviewState = ReviewState.DRAFT,
            directPrimaryMuscle = StandardMuscles.CHEST,
            descriptiveSecondaryMuscles = setOf(StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            complexity = ComplexityTier.FOUNDATIONAL,
            progressionFamily = "bodyweight-horizontal-push",
            prescriptionShape = PrescriptionShape.BODYWEIGHT_REPS,
            approvedRegressions = listOf(ReviewedExerciseLink("wall-push-up")),
            approvedSubstitutions = emptyList(),
            capabilityRequirements = setOf(MovementCapabilityType.UPPER_BODY_BODYWEIGHT_PUSH),
            supportRequirement = SupportRequirement.UNSUPPORTED,
            impactLevel = ImpactLevel.NONE,
            equipmentAlternatives = listOf(listOf(StandardEquipment.BODYWEIGHT)),
            clearedTrainingConstraints = emptySet(),
            provenance = ReviewProvenance(
                reviewerRole = null,
                rationaleOrSource = "Initial draft for later human review.",
                reviewedAtEpochMillis = null,
                schemaVersion = 1,
                policyVersion = 1
            )
        )

        val exercise = Exercise(
            id = "push-up",
            name = "Push-up",
            primaryMuscles = listOf(StandardMuscles.CHEST),
            type = ExerciseType.BODYWEIGHT_REPS,
            programming = legacy,
            reviewedMetadata = reviewed
        )

        assertThat(exercise.programming).isSameInstanceAs(legacy)
        assertThat(exercise.reviewedMetadata).isSameInstanceAs(reviewed)
        assertThat(exercise.reviewedMetadata?.reviewState).isEqualTo(ReviewState.DRAFT)
        assertThat(exercise.reviewedMetadata?.aiReviewProvenance).isNull()
    }

    @Test
    fun aiAcceptedMetadata_recordsAiProvenanceWithoutImplyingHumanReview() {
        val aiProvenance = AiReviewProvenance(
            reviewerModelId = "test-model-1",
            reviewedAtEpochMillis = 1_756_000_000_000L,
            reviewedContentId = "push-up",
            reviewedContentSha256 = "a".repeat(64),
            sourceReferences = listOf("https://example.test/reference"),
            decisionRationale = "Alpha AI acceptance for owner-authorized planning only.",
            limitations = "Not a human review and not clinical clearance.",
            schemaVersion = 3,
            policyVersion = 1
        )

        val reviewed = ReviewedExerciseMetadata(
            reviewState = ReviewState.AI_ACCEPTED,
            directPrimaryMuscle = StandardMuscles.CHEST,
            descriptiveSecondaryMuscles = setOf(StandardMuscles.TRICEPS),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            complexity = ComplexityTier.FOUNDATIONAL,
            progressionFamily = "bodyweight-horizontal-push",
            prescriptionShape = PrescriptionShape.BODYWEIGHT_REPS,
            approvedRegressions = emptyList(),
            approvedSubstitutions = emptyList(),
            capabilityRequirements = emptySet(),
            supportRequirement = SupportRequirement.UNSUPPORTED,
            impactLevel = ImpactLevel.NONE,
            equipmentAlternatives = listOf(listOf(StandardEquipment.BODYWEIGHT)),
            clearedTrainingConstraints = emptySet(),
            provenance = ReviewProvenance(
                reviewerRole = null,
                rationaleOrSource = "AI-accepted alpha entry; never human reviewed.",
                reviewedAtEpochMillis = null,
                schemaVersion = 3,
                policyVersion = 1
            ),
            aiReviewProvenance = aiProvenance
        )

        // AI acceptance is its own state: it must never read as, or back-fill, human review.
        assertThat(reviewed.reviewState).isNotEqualTo(ReviewState.APPROVED)
        assertThat(reviewed.provenance.reviewerRole).isNull()
        assertThat(reviewed.provenance.reviewedAtEpochMillis).isNull()
        assertThat(reviewed.aiReviewProvenance).isSameInstanceAs(aiProvenance)
    }
}
