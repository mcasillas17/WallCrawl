package wallcrawl.elopenmike.com.core.model

const val MULTI_WEEK_PROGRAM_VERSION = "MULTI_WEEK_PROGRAM_V1"
const val PROGRAM_CONFIGURATION_VERSION = "PROGRAM_CONFIGURATION_V1"

/** Versions are data; support is checked by policy, independently of raw identity reads. */
data class ProgramConfiguration(
    val primaryGoal: FitnessGoal,
    val goals: List<FitnessGoal>,
    val sessionsPerWeek: Int,
    val policyVersion: String = PROGRAM_CONFIGURATION_VERSION
)

enum class ProgramStatus { ACTIVE, PAUSED, COMPLETED }
enum class ProgramAction { START, PAUSE, RESUME, RESTART, REPLACE, START_SESSION, FINISH_SESSION, CANCEL_SESSION }
enum class ProgramObjective { ESTABLISH_ROUTINE, CONTINUE_AND_RECORD }

data class ProgramEnrollment(
    val profileId: String,
    val programId: String,
    val revision: Long,
    val configuration: ProgramConfiguration,
    val status: ProgramStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lifecyclePolicyVersion: String = MULTI_WEEK_PROGRAM_VERSION
)

/** Ordinal is zero based; the other indices are one based and validated against it. */
data class ProgramPosition(val slotOrdinal: Int, val blockNumber: Int, val weekNumber: Int, val sessionInWeek: Int)

data class ProgramSessionSnapshot(
    val programId: String,
    val enrollmentRevision: Long,
    val configuration: ProgramConfiguration,
    val position: ProgramPosition,
    val objective: ProgramObjective,
    val lifecyclePolicyVersion: String = MULTI_WEEK_PROGRAM_VERSION
)

data class ProgramAssociation(val sessionId: String, val profileId: String, val snapshot: ProgramSessionSnapshot)

/** Session completion is independent of completed sets and historical evidence chronology. */
data class ProgramSlotSession(
    val association: ProgramAssociation,
    val status: SessionStatus,
    val completedAtEpochMillis: Long?
)

data class ProgramProgress(val completedSlots: Int, val nextPosition: ProgramPosition?, val activeSessionId: String?)

data class ProgramExpectation(
    val profileId: String,
    val profileRevision: Long,
    val programId: String?,
    val enrollmentRevision: Long
)

/** Raw storage fingerprint covers every stored field; constructing this never decodes configuration. */
data class ProgramHeader(
    val profileId: String,
    val programId: String?,
    val revision: Long,
    val rawStorageFingerprint: String?
) {
    init {
        requireProgramIdentityToken(profileId)
        if (programId == null) {
            if (revision != 0L || rawStorageFingerprint != null) throw ProgramException(ProgramFailure.INVALID_IDENTITY)
        } else {
            requireProgramRunId(programId)
            if (revision <= 0 || rawStorageFingerprint?.matches(Regex("[0-9a-f]{64}")) != true) {
                throw ProgramException(ProgramFailure.INVALID_IDENTITY)
            }
        }
    }

    companion object {
        fun absent(profileId: String) = ProgramHeader(profileId, null, 0, null)
    }
}

enum class ProgramFailure {
    UNSUPPORTED_POLICY, UNSUPPORTED_CONFIGURATION, INVALID_CONFIGURATION, INVALID_IDENTITY,
    INVALID_PROGRESS, INVALID_ACTION, STALE_STATE, PROFILE_MISMATCH, PROFILE_UNAVAILABLE,
    ACTIVE_WORKOUT, CONFIRMATION_REQUIRED, REVISION_EXHAUSTED, MISSING_OR_NOT_ACTIVE
}

class ProgramException(val failure: ProgramFailure) : IllegalArgumentException(failure.name)

internal fun requireProgramIdentityToken(value: String) {
    if (value.isBlank() || value.length > 200 || value.any(Char::isISOControl) || "|||" in value) {
        throw ProgramException(ProgramFailure.INVALID_IDENTITY)
    }
}

internal fun requireProgramRunId(value: String) {
    if (!value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) {
        throw ProgramException(ProgramFailure.INVALID_IDENTITY)
    }
}
