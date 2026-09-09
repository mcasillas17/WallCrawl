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
     * Generates a structured workout recommendation from the bounded generation context.
     *
     * A planner reads only fields of [WorkoutGenerationContext], which is the authoritative
     * list; this contract deliberately does not restate it, because a partial enumeration
     * here goes stale the moment a field is consumed. The reviewed-only inputs are the
     * automatic eligibility result, the capability evidence and the composed training program
     * state with its weekly dose ledger; everything else applies on both paths.
     *
     * There is no recovery state among them. Nothing in WallCrawl derives readiness or a
     * recovery interval, and an implementation must not invent one here.
     */
    suspend fun generateWorkout(
        context: WorkoutGenerationContext
    ): GeneratedWorkout
}
