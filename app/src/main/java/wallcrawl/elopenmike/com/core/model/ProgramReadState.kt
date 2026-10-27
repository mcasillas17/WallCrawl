package wallcrawl.elopenmike.com.core.model

/** Opaque persisted facts are displayable without interpreting an unsupported schedule. */
data class ProgramRecordedFacts(
    val programId: String,
    val lifecyclePolicyVersion: String,
    val configurationPolicyVersion: String,
    val primaryGoal: String,
    val goals: List<String>,
    val sessionsPerWeek: Int
)

data class ProgramHistoryAssociation(
    val sessionId: String,
    val profileId: String,
    val facts: ProgramRecordedFacts,
    val enrollmentRevision: Long,
    val position: ProgramPosition,
    val objective: String,
    val supportedSnapshot: ProgramSessionSnapshot?
) {
    val programId: String get() = facts.programId
}

sealed interface ProgramReadState {
    val header: ProgramHeader
    val profile: UserProfile?
    data class Empty(override val header: ProgramHeader, override val profile: UserProfile? = null) : ProgramReadState
    data class Supported(override val header: ProgramHeader, val enrollment: ProgramEnrollment,
        val progress: ProgramProgress, val sessions: List<ProgramSlotSession>, override val profile: UserProfile? = null) : ProgramReadState {
        val profileFailure: ProgramFailure? get() = when {
            profile == null || !profile.onboardingCompleted -> ProgramFailure.PROFILE_UNAVAILABLE
            profile.primaryGoal != enrollment.configuration.primaryGoal || profile.goals != enrollment.configuration.goals.toSet() ||
                profile.daysPerWeek != enrollment.configuration.sessionsPerWeek -> ProgramFailure.PROFILE_MISMATCH
            else -> null
        }
    }
    data class Unsupported(override val header: ProgramHeader, val facts: ProgramRecordedFacts,
        val status: String, override val profile: UserProfile? = null) : ProgramReadState
}
