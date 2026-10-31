package wallcrawl.elopenmike.com.core.ai

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import wallcrawl.elopenmike.com.core.model.*

/** Completion order, never a wall clock, defines this four-week schedule. */
object MultiWeekProgramPolicy {
    const val WEEKS = 4
    const val MAX_SLOTS = 24

    fun configurationFromProfile(profile: UserProfile): ProgramConfiguration {
        requireProfile(profile)
        val configuration = ProgramConfiguration(profile.primaryGoal, profile.goals.sortedBy { it.name }, profile.daysPerWeek)
        validateConfiguration(configuration)
        return configuration
    }

    fun validateSupported(enrollment: ProgramEnrollment) {
        validateIdentity(enrollment)
        if (enrollment.lifecyclePolicyVersion != MULTI_WEEK_PROGRAM_VERSION) fail(ProgramFailure.UNSUPPORTED_POLICY)
        validateConfiguration(enrollment.configuration)
    }

    fun validateSupported(snapshot: ProgramSessionSnapshot) {
        requireProgramRunId(snapshot.programId)
        if (snapshot.enrollmentRevision <= 0) fail(ProgramFailure.INVALID_IDENTITY)
        if (snapshot.lifecyclePolicyVersion != MULTI_WEEK_PROGRAM_VERSION) fail(ProgramFailure.UNSUPPORTED_POLICY)
        validateConfiguration(snapshot.configuration)
        if (snapshot.position != position(snapshot.configuration, snapshot.position.slotOrdinal) ||
            snapshot.objective != objective(snapshot.position)) fail(ProgramFailure.INVALID_PROGRESS)
    }

    fun validateProfileMatch(configuration: ProgramConfiguration, profile: UserProfile) {
        requireProfile(profile)
        if (configuration.primaryGoal != profile.primaryGoal || configuration.goals.toSet() != profile.goals ||
            configuration.sessionsPerWeek != profile.daysPerWeek) fail(ProgramFailure.PROFILE_MISMATCH)
    }

    fun position(configuration: ProgramConfiguration, slotOrdinal: Int): ProgramPosition {
        validateConfiguration(configuration)
        if (slotOrdinal !in 0 until WEEKS * configuration.sessionsPerWeek) fail(ProgramFailure.INVALID_PROGRESS)
        val week = slotOrdinal / configuration.sessionsPerWeek
        return ProgramPosition(slotOrdinal, week / 2 + 1, week + 1, slotOrdinal % configuration.sessionsPerWeek + 1)
    }

    fun objective(position: ProgramPosition): ProgramObjective =
        if (position.blockNumber == 1) ProgramObjective.ESTABLISH_ROUTINE else ProgramObjective.CONTINUE_AND_RECORD

    fun snapshot(enrollment: ProgramEnrollment, position: ProgramPosition): ProgramSessionSnapshot {
        validateSupported(enrollment)
        if (position(enrollment.configuration, position.slotOrdinal) != position) fail(ProgramFailure.INVALID_PROGRESS)
        return ProgramSessionSnapshot(enrollment.programId, enrollment.revision,
            enrollment.configuration.copy(goals = enrollment.configuration.goals.sortedBy { it.name }),
            position, objective(position), enrollment.lifecyclePolicyVersion)
    }

    fun reconstruct(enrollment: ProgramEnrollment, sessions: List<ProgramSlotSession>): ProgramProgress {
        validateSupported(enrollment)
        val total = WEEKS * enrollment.configuration.sessionsPerWeek
        if (sessions.size > total) fail(ProgramFailure.INVALID_PROGRESS)
        val ordered = sessions.sortedBy { it.association.snapshot.position.slotOrdinal }
        val sessionIds = mutableSetOf<String>()
        var completed = 0
        var active: String? = null
        var previousStartRevision = 0L
        ordered.forEachIndexed { index, row ->
            val association = row.association
            val snapshot = association.snapshot
            requireProgramIdentityToken(association.sessionId)
            validateSupported(snapshot)
            if (!sessionIds.add(association.sessionId) || association.profileId != enrollment.profileId ||
                snapshot.programId != enrollment.programId || snapshot.enrollmentRevision <= previousStartRevision || snapshot.enrollmentRevision >= enrollment.revision ||
                snapshot.lifecyclePolicyVersion != enrollment.lifecyclePolicyVersion ||
                !sameConfiguration(snapshot.configuration, enrollment.configuration) ||
                snapshot.position != position(enrollment.configuration, index) ||
                snapshot.objective != objective(snapshot.position)) fail(ProgramFailure.INVALID_PROGRESS)
            if (index > 0 && snapshot.enrollmentRevision - previousStartRevision < 2) fail(ProgramFailure.INVALID_PROGRESS)
            previousStartRevision = snapshot.enrollmentRevision
            when (row.status) {
                SessionStatus.COMPLETED -> {
                    if (active != null || row.completedAtEpochMillis == null || row.completedAtEpochMillis !in 0..4_102_444_800_000L ||
                        enrollment.revision - snapshot.enrollmentRevision < 2) fail(ProgramFailure.INVALID_PROGRESS)
                    completed++
                }
                SessionStatus.IN_PROGRESS -> {
                    if (active != null || index != ordered.lastIndex || row.completedAtEpochMillis != null ||
                        snapshot.enrollmentRevision != enrollment.revision - 1) fail(ProgramFailure.INVALID_PROGRESS)
                    active = association.sessionId
                }
                SessionStatus.CANCELLED -> fail(ProgramFailure.INVALID_PROGRESS)
            }
        }
        if ((enrollment.status == ProgramStatus.COMPLETED) != (completed == total) ||
            (enrollment.status == ProgramStatus.PAUSED && active != null)) fail(ProgramFailure.INVALID_PROGRESS)
        return ProgramProgress(completed, if (completed == total) null else position(enrollment.configuration, completed), active)
    }

    /** Transactions call this again using their fresh profile and raw header before writing. */
    fun checkExpected(profile: UserProfile?, header: ProgramHeader, expected: ProgramExpectation) =
        checkExpected(profile, header.profileId, header.programId, header.revision, expected)

    fun nextRevision(revision: Long): Long {
        if (revision < 0) fail(ProgramFailure.INVALID_IDENTITY)
        if (revision == Long.MAX_VALUE) fail(ProgramFailure.REVISION_EXHAUSTED)
        return revision + 1
    }

    /** Opaque enrollments can be replaced using only their validated identity. */
    fun replace(profile: UserProfile?, header: ProgramHeader, expected: ProgramExpectation,
                newProgramId: String, atEpochMillis: Long, hasActiveWorkout: Boolean, confirmed: Boolean): ProgramEnrollment {
        checkExpected(profile, header, expected)
        if (hasActiveWorkout) fail(ProgramFailure.ACTIVE_WORKOUT)
        if (header.programId == null) fail(ProgramFailure.INVALID_ACTION)
        if (!confirmed) fail(ProgramFailure.CONFIRMATION_REQUIRED)
        requireNewRunId(newProgramId, header.programId)
        requireTimestamp(atEpochMillis)
        return ProgramEnrollment(header.profileId, newProgramId, nextRevision(header.revision),
            configurationFromProfile(requireNotNull(profile)), ProgramStatus.ACTIVE, atEpochMillis, atEpochMillis)
    }

    /** Returns the enrollment write; callers atomically insert/update/delete session rows themselves. */
    fun transition(profile: UserProfile?, enrollment: ProgramEnrollment?, sessions: List<ProgramSlotSession>,
                   action: ProgramAction, expected: ProgramExpectation, atEpochMillis: Long,
                   hasActiveWorkout: Boolean = false, newProgramId: String? = null,
                   confirmed: Boolean = false, sessionId: String? = null): ProgramEnrollment {
        enrollment?.let(::validateIdentity)
        checkExpected(profile, enrollment?.profileId ?: expected.profileId, enrollment?.programId, enrollment?.revision ?: 0, expected)
        requireTimestamp(atEpochMillis)
        val sessionAction = action in listOf(ProgramAction.FINISH_SESSION, ProgramAction.CANCEL_SESSION)
        if (!sessionAction && (hasActiveWorkout || sessions.any { it.status == SessionStatus.IN_PROGRESS })) fail(ProgramFailure.ACTIVE_WORKOUT)
        val allowed = when (action) {
            ProgramAction.START -> enrollment == null
            ProgramAction.PAUSE, ProgramAction.START_SESSION, ProgramAction.FINISH_SESSION, ProgramAction.CANCEL_SESSION -> enrollment?.status == ProgramStatus.ACTIVE
            ProgramAction.RESUME -> enrollment?.status == ProgramStatus.PAUSED
            ProgramAction.RESTART, ProgramAction.REPLACE -> enrollment != null
        }
        if (!allowed) fail(ProgramFailure.INVALID_ACTION)
        if (action in listOf(ProgramAction.RESTART, ProgramAction.REPLACE) && !confirmed) fail(ProgramFailure.CONFIRMATION_REQUIRED)
        val revision = nextRevision(enrollment?.revision ?: 0)
        if (action in listOf(ProgramAction.START, ProgramAction.RESTART, ProgramAction.REPLACE)) {
            val id = newProgramId ?: fail(ProgramFailure.INVALID_IDENTITY)
            requireNewRunId(id, enrollment?.programId)
            val configuration = if (action == ProgramAction.RESTART) {
                val current = requireNotNull(enrollment)
                validateSupported(current)
                reconstruct(current, sessions)
                validateProfileMatch(current.configuration, requireNotNull(profile))
                current.configuration.copy(goals = current.configuration.goals.sortedBy { it.name })
            } else {
                if (enrollment != null && isSupported(enrollment)) reconstruct(enrollment, sessions)
                configurationFromProfile(requireNotNull(profile))
            }
            return ProgramEnrollment(requireNotNull(profile).id, id, revision, configuration, ProgramStatus.ACTIVE, atEpochMillis, atEpochMillis)
        }
        val current = requireNotNull(enrollment)
        if (action == ProgramAction.PAUSE) {
            if (isSupported(current)) reconstruct(current, sessions)
            return current.copy(status = ProgramStatus.PAUSED, revision = revision, updatedAtEpochMillis = atEpochMillis)
        }
        if (action == ProgramAction.CANCEL_SESSION && !isSupported(current)) {
            // Unknown schedules cannot advance; cancellation needs only the matching active identity.
            val reservation = sessions.singleOrNull { it.status == SessionStatus.IN_PROGRESS }
                ?: fail(ProgramFailure.MISSING_OR_NOT_ACTIVE)
            validateReservationIdentity(current, reservation, sessionId)
            return current.copy(revision = revision, updatedAtEpochMillis = atEpochMillis)
        }
        validateSupported(current)
        val progress = reconstruct(current, sessions)
        if (action == ProgramAction.RESUME || action == ProgramAction.START_SESSION) {
            validateProfileMatch(current.configuration, requireNotNull(profile))
        }
        if (sessionAction) {
            val reservation = sessions.singleOrNull { it.status == SessionStatus.IN_PROGRESS }
                ?: fail(ProgramFailure.MISSING_OR_NOT_ACTIVE)
            validateReservationIdentity(current, reservation, sessionId)
        }
        val status = when {
            action == ProgramAction.RESUME -> ProgramStatus.ACTIVE
            action == ProgramAction.FINISH_SESSION && progress.completedSlots + 1 == WEEKS * current.configuration.sessionsPerWeek -> ProgramStatus.COMPLETED
            else -> current.status
        }
        return current.copy(status = status, revision = revision, updatedAtEpochMillis = atEpochMillis)
    }

    /** All frozen association fields are UTF-8 length framed; goal order is canonical. */
    fun associationDigest(association: ProgramAssociation): String {
        val snapshot = association.snapshot
        val configuration = snapshot.configuration
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            fun field(value: String) {
                val encoded = value.toByteArray(Charsets.UTF_8)
                output.writeInt(encoded.size)
                output.write(encoded)
            }
            field("wallcrawl-program-association-v1")
            field(association.sessionId)
            field(association.profileId)
            field(snapshot.programId)
            field(snapshot.enrollmentRevision.toString())
            field(snapshot.lifecyclePolicyVersion)
            field(configuration.policyVersion)
            field(configuration.primaryGoal.name)
            field(configuration.goals.size.toString())
            configuration.goals.sortedBy { it.name }.forEach { field(it.name) }
            field(configuration.sessionsPerWeek.toString())
            field(snapshot.position.slotOrdinal.toString())
            field(snapshot.position.blockNumber.toString())
            field(snapshot.position.weekNumber.toString())
            field(snapshot.position.sessionInWeek.toString())
            field(snapshot.objective.name)
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun validateConfiguration(configuration: ProgramConfiguration) {
        if (configuration.policyVersion != PROGRAM_CONFIGURATION_VERSION) fail(ProgramFailure.UNSUPPORTED_POLICY)
        if (configuration.sessionsPerWeek !in 2..6) fail(ProgramFailure.UNSUPPORTED_CONFIGURATION)
        if (configuration.goals.isEmpty() || configuration.goals.size != configuration.goals.distinct().size ||
            configuration.primaryGoal !in configuration.goals) fail(ProgramFailure.INVALID_CONFIGURATION)
    }

    private fun sameConfiguration(first: ProgramConfiguration, second: ProgramConfiguration): Boolean =
        first.policyVersion == second.policyVersion && first.primaryGoal == second.primaryGoal &&
            first.sessionsPerWeek == second.sessionsPerWeek && first.goals.size == second.goals.size && first.goals.toSet() == second.goals.toSet()

    private fun isSupported(enrollment: ProgramEnrollment) = enrollment.lifecyclePolicyVersion == MULTI_WEEK_PROGRAM_VERSION &&
        enrollment.configuration.policyVersion == PROGRAM_CONFIGURATION_VERSION

    private fun validateIdentity(enrollment: ProgramEnrollment) {
        requireProgramIdentityToken(enrollment.profileId)
        requireProgramRunId(enrollment.programId)
        if (enrollment.revision <= 0) fail(ProgramFailure.INVALID_IDENTITY)
        requireTimestamp(enrollment.createdAtEpochMillis)
        requireTimestamp(enrollment.updatedAtEpochMillis)
    }

    private fun checkExpected(profile: UserProfile?, profileId: String, programId: String?, revision: Long,
                              expected: ProgramExpectation) {
        requireProfile(profile)
        if (profile!!.id != profileId || profile.id != expected.profileId || profile.revision != expected.profileRevision ||
            programId != expected.programId || revision != expected.enrollmentRevision) fail(ProgramFailure.STALE_STATE)
    }

    private fun requireProfile(profile: UserProfile?) {
        if (profile == null || !profile.onboardingCompleted) fail(ProgramFailure.PROFILE_UNAVAILABLE)
        requireProgramIdentityToken(profile.id)
        if (profile.revision < 0) fail(ProgramFailure.INVALID_IDENTITY)
    }

    private fun validateReservationIdentity(enrollment: ProgramEnrollment, row: ProgramSlotSession, sessionId: String?) {
        requireProgramIdentityToken(row.association.sessionId)
        if (sessionId == null || sessionId != row.association.sessionId) fail(ProgramFailure.MISSING_OR_NOT_ACTIVE)
        if (row.association.profileId != enrollment.profileId || row.association.snapshot.programId != enrollment.programId ||
            row.association.snapshot.enrollmentRevision !in 1..enrollment.revision || row.completedAtEpochMillis != null) fail(ProgramFailure.INVALID_PROGRESS)
    }

    private fun requireNewRunId(newProgramId: String, oldProgramId: String?) {
        requireProgramRunId(newProgramId)
        if (newProgramId == oldProgramId) fail(ProgramFailure.INVALID_IDENTITY)
    }

    private fun requireTimestamp(timestamp: Long) {
        if (timestamp !in 0..4_102_444_800_000L) fail(ProgramFailure.INVALID_IDENTITY)
    }

    private fun fail(failure: ProgramFailure): Nothing = throw ProgramException(failure)
}
