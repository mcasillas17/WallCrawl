package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.ReviewState

/** Computes a soft penalty without changing automatic exercise eligibility. */
class ExerciseDifficultyRankingPolicy {

    fun aboveExperiencePenalty(
        exercise: Exercise,
        experienceLevel: ExperienceLevel,
        reviewedEligibilityEnabled: Boolean
    ): Int {
        val exerciseTier = if (reviewedEligibilityEnabled) {
            exercise.reviewedMetadata
                ?.takeIf { it.reviewState == ReviewState.APPROVED }
                ?.complexity
                ?.rankingTier()
        } else {
            exercise.programming?.difficulty?.rankingTier()
        } ?: return 0

        return (exerciseTier - experienceLevel.rankingTier()).coerceAtLeast(0)
    }

    private fun ExperienceLevel.rankingTier(): Int = when (this) {
        ExperienceLevel.BEGINNER -> 0
        ExperienceLevel.INTERMEDIATE -> 1
        ExperienceLevel.ADVANCED -> 2
    }

    private fun Difficulty.rankingTier(): Int = when (this) {
        Difficulty.BEGINNER -> 0
        Difficulty.INTERMEDIATE -> 1
        Difficulty.ADVANCED -> 2
    }

    private fun ComplexityTier.rankingTier(): Int = when (this) {
        ComplexityTier.FOUNDATIONAL -> 0
        ComplexityTier.STANDARD -> 1
        ComplexityTier.ADVANCED -> 2
    }
}
