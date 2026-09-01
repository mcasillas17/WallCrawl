package wallcrawl.elopenmike.com.core.model

/** The versioned policy under which a [TrainingProgramState] was composed. */
enum class TrainingProgramStatePolicyVersion {
    PROGRAM_STATE_V1
}

/**
 * The derived state a deterministic plan is built under: how the user is currently adapting,
 * and what this week's training has already contained.
 *
 * Both halves are derived, never accumulated. [weeklyLedger] is reconstructed from immutable
 * completed history, and [adaptationState] is a pure function of the profile, so an identical
 * profile and history always compose an identical state.
 *
 * No policy reads [weeklyLedger] yet. It is carried here so weekly dose targets and
 * recommendation snapshots can consume it without re-deriving it at another point in the
 * flow. Its credited counts are all zero while the bundled catalog carries no `APPROVED`
 * reviewed metadata, and every completed work set is reported as unattributed instead.
 */
data class TrainingProgramState(
    val policyVersion: TrainingProgramStatePolicyVersion,
    val adaptationState: AdaptationState,
    val weeklyLedger: WeeklyDoseLedger
)
