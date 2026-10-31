package wallcrawl.elopenmike.com.core.model

/** The versioned policy under which a [TrainingProgramState] was composed. */
enum class TrainingProgramStatePolicyVersion {
    PROGRAM_STATE_V1,
    PROGRAM_STATE_V2
}

/**
 * The derived state a deterministic plan is built under: how the user is currently adapting,
 * and what this week's training has already contained.
 *
 * Both halves are derived, never accumulated. [weeklyLedger] is reconstructed from immutable
 * completed history, and [adaptationState] is a pure function of the profile, so an identical
 * profile, accepted choice and history always compose an identical state.
 *
 * [wallcrawl.elopenmike.com.core.ai.StateBasedTrainingPolicy] reads the ledger only on the
 * reviewed-enabled path to cap future automatic sets by remaining direct-primary allowance.
 * The current production catalog attributes its accepted cohort under PRIMARY_ONLY_V1.
 */
data class TrainingProgramState(
    val policyVersion: TrainingProgramStatePolicyVersion,
    val adaptationState: AdaptationState,
    val weeklyLedger: WeeklyDoseLedger
)
