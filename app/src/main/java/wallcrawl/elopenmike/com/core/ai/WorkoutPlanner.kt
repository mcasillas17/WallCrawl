package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

/**
 * Clean abstraction for on-device workout planning.
 * Implementations can include [FakeWorkoutPlanner], LiteRT / Mediapipe GenAI,
 * on-device Gemma / Qwen LLMs, or rule-based adaptive engines.
 */
interface WorkoutPlanner {
    /**
     * Generates a structured workout recommendation given user goals, equipment,
     * recovery state, and pre-filtered allowed candidate exercises.
     */
    suspend fun generateWorkout(
        context: WorkoutGenerationContext
    ): GeneratedWorkout
}
