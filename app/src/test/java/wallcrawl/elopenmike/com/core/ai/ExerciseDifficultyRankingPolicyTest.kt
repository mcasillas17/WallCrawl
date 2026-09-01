package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.SupportRequirement

class ExerciseDifficultyRankingPolicyTest {

    private val policy = ExerciseDifficultyRankingPolicy()
    private val base = InMemoryExerciseCatalog.SAMPLE_EXERCISES.first()

    @Test
    fun beginner_penaltyIncreasesForIntermediateAndAdvancedLegacyDifficulty() {
        assertThat(legacyPenalty(Difficulty.BEGINNER, ExperienceLevel.BEGINNER)).isEqualTo(0)
        assertThat(legacyPenalty(Difficulty.INTERMEDIATE, ExperienceLevel.BEGINNER)).isEqualTo(1)
        assertThat(legacyPenalty(Difficulty.ADVANCED, ExperienceLevel.BEGINNER)).isEqualTo(2)
    }

    @Test
    fun intermediate_penalizesOnlyAdvancedLegacyDifficulty() {
        assertThat(legacyPenalty(Difficulty.BEGINNER, ExperienceLevel.INTERMEDIATE)).isEqualTo(0)
        assertThat(legacyPenalty(Difficulty.INTERMEDIATE, ExperienceLevel.INTERMEDIATE)).isEqualTo(0)
        assertThat(legacyPenalty(Difficulty.ADVANCED, ExperienceLevel.INTERMEDIATE)).isEqualTo(1)
    }

    @Test
    fun advanced_neverAddsAnExperiencePenalty() {
        Difficulty.entries.forEach { difficulty ->
            assertThat(legacyPenalty(difficulty, ExperienceLevel.ADVANCED)).isEqualTo(0)
        }
    }

    @Test
    fun reviewedMode_usesApprovedComplexityInsteadOfConflictingLegacyDifficulty() {
        val exercise = exercise(
            legacyDifficulty = Difficulty.BEGINNER,
            reviewedState = ReviewState.APPROVED,
            reviewedComplexity = ComplexityTier.ADVANCED
        )

        assertThat(
            policy.aboveExperiencePenalty(
                exercise = exercise,
                experienceLevel = ExperienceLevel.BEGINNER,
                reviewedEligibilityEnabled = true
            )
        ).isEqualTo(2)
    }

    @Test
    fun reviewedMode_ignoresDraftComplexity() {
        val exercise = exercise(
            legacyDifficulty = Difficulty.BEGINNER,
            reviewedState = ReviewState.DRAFT,
            reviewedComplexity = ComplexityTier.ADVANCED
        )

        assertThat(
            policy.aboveExperiencePenalty(
                exercise = exercise,
                experienceLevel = ExperienceLevel.BEGINNER,
                reviewedEligibilityEnabled = true
            )
        ).isEqualTo(0)
    }

    @Test
    fun legacyMode_ignoresDraftReviewedComplexityAndUsesLegacyDifficulty() {
        val exercise = exercise(
            legacyDifficulty = Difficulty.INTERMEDIATE,
            reviewedState = ReviewState.DRAFT,
            reviewedComplexity = ComplexityTier.ADVANCED
        )

        assertThat(
            policy.aboveExperiencePenalty(
                exercise = exercise,
                experienceLevel = ExperienceLevel.BEGINNER,
                reviewedEligibilityEnabled = false
            )
        ).isEqualTo(1)
    }

    @Test
    fun missingTrustedClassification_hasNoPenalty() {
        val unclassified = base.copy(programming = null, reviewedMetadata = null)

        assertThat(
            policy.aboveExperiencePenalty(
                exercise = unclassified,
                experienceLevel = ExperienceLevel.BEGINNER,
                reviewedEligibilityEnabled = false
            )
        ).isEqualTo(0)
        assertThat(
            policy.aboveExperiencePenalty(
                exercise = unclassified,
                experienceLevel = ExperienceLevel.BEGINNER,
                reviewedEligibilityEnabled = true
            )
        ).isEqualTo(0)
    }

    private fun legacyPenalty(
        difficulty: Difficulty,
        experienceLevel: ExperienceLevel
    ): Int = policy.aboveExperiencePenalty(
        exercise = exercise(legacyDifficulty = difficulty),
        experienceLevel = experienceLevel,
        reviewedEligibilityEnabled = false
    )

    private fun exercise(
        legacyDifficulty: Difficulty,
        reviewedState: ReviewState? = null,
        reviewedComplexity: ComplexityTier = ComplexityTier.STANDARD
    ): Exercise = base.copy(
        programming = checkNotNull(base.programming).copy(difficulty = legacyDifficulty),
        reviewedMetadata = reviewedState?.let {
            ReviewedExerciseMetadata(
                reviewState = it,
                directPrimaryMuscle = StandardMuscles.CHEST,
                descriptiveSecondaryMuscles = setOf(StandardMuscles.SHOULDERS),
                movementPattern = checkNotNull(base.programming).movementPattern,
                complexity = reviewedComplexity,
                progressionFamily = "test-horizontal-push",
                prescriptionShape = PrescriptionShape.WEIGHT_REPS,
                approvedRegressions = emptyList(),
                approvedSubstitutions = emptyList(),
                capabilityRequirements = emptySet(),
                supportRequirement = SupportRequirement.SUPPORTED,
                impactLevel = ImpactLevel.NONE,
                equipmentAlternatives = listOf(
                    listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
                ),
                provenance = ReviewProvenance(
                    reviewerRole = if (it == ReviewState.APPROVED) "Test reviewer" else null,
                    rationaleOrSource = "Synthetic ranking policy fixture.",
                    reviewedAtEpochMillis = if (it == ReviewState.APPROVED) 1L else null,
                    schemaVersion = 1,
                    policyVersion = 1
                )
            )
        }
    )
}
