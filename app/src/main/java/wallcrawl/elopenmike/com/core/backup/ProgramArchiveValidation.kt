package wallcrawl.elopenmike.com.core.backup

import wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy
import wallcrawl.elopenmike.com.core.model.*

/** The archive boundary never turns unsupported or corrupt program rows into absence. */
internal inline fun <T> archivedProgramValue(read: () -> T): T = try {
    read()
} catch (error: ProgramException) {
    throw LocalDataArchiveException(
        if (error.failure == ProgramFailure.UNSUPPORTED_POLICY) ArchiveRejection.UNSUPPORTED_PROGRAM
        else ArchiveRejection.INCONSISTENT,
        "Archived program data violates ${error.failure.name}."
    )
}

internal fun validateArchivedPrograms(snapshot: LocalDataSnapshot) = archivedProgramValue {
    fun inconsistent(): Nothing = throw LocalDataArchiveException(
        ArchiveRejection.INCONSISTENT, "Archived program ownership, membership or progress is inconsistent.")
    val associations = snapshot.programAssociations
    if (associations.size > LocalDataArchiveLimits.MAX_SESSIONS) throw LocalDataArchiveException(
        ArchiveRejection.TOO_LARGE, "Too many archived program associations.")
    val enrollment = snapshot.programEnrollment
    enrollment?.let { MultiWeekProgramPolicy.validateSupported(it) }
    associations.forEach { MultiWeekProgramPolicy.validateSupported(it.snapshot) }
    fun canonical(configuration: ProgramConfiguration) {
        if (configuration.goals != configuration.goals.sortedBy { it.name }) inconsistent()
    }
    enrollment?.let { canonical(it.configuration) }
    associations.forEach { canonical(it.snapshot.configuration) }

    if (enrollment != null || associations.isNotEmpty()) {
        val profile = snapshot.profile ?: inconsistent()
        if (!profile.onboardingCompleted || enrollment == null || enrollment.profileId != profile.id ||
            associations.any { it.profileId != profile.id }) inconsistent()
    }
    if (associations.map { it.sessionId }.distinct().size != associations.size) inconsistent()
    val bySession = associations.associateBy { it.sessionId }
    val records = snapshot.recommendationRecords.associateBy { it.sessionId }
    snapshot.recommendationRecords.forEach { record ->
        if ((record.programAssociationDigest != null) != bySession.containsKey(record.sessionId)) inconsistent()
    }
    val sessions = snapshot.sessions.associateBy { it.id }
    val runs = associations.groupBy { it.snapshot.programId }.mapValues { (_, rows) ->
        rows.map { association ->
            val session = sessions[association.sessionId] ?: inconsistent()
            val record = records[association.sessionId] ?: inconsistent()
            if (session.origin != WorkoutOrigin.PLANNER ||
                record.programAssociationDigest != MultiWeekProgramPolicy.associationDigest(association)) inconsistent()
            ProgramSlotSession(association, session.status, session.completedAtTimestamp)
        }
    }
    if (enrollment != null) {
        val current = runs[enrollment.programId].orEmpty()
        MultiWeekProgramPolicy.reconstruct(enrollment, current)
        // Each retained old run ended before replacement's additional revision increment.
        val historical = runs.filterKeys { it != enrollment.programId }.values
            .sortedBy { rows -> rows.minOf { it.association.snapshot.enrollmentRevision } }
        var previousLastStart: Long? = null
        historical.forEach { rows ->
            if (rows.any { it.status != SessionStatus.COMPLETED }) inconsistent()
            val first = rows.minOf { it.association.snapshot.enrollmentRevision }
            val last = rows.maxOf { it.association.snapshot.enrollmentRevision }
            if (last > Long.MAX_VALUE - 3 ||
                (previousLastStart != null && first - previousLastStart!! < 3)) inconsistent()
            val frozen = rows.first().association.snapshot
            val synthetic = ProgramEnrollment(enrollment.profileId, frozen.programId, last + 2,
                frozen.configuration,
                if (rows.size == MultiWeekProgramPolicy.WEEKS * frozen.configuration.sessionsPerWeek)
                    ProgramStatus.COMPLETED else ProgramStatus.ACTIVE,
                0, 0, frozen.lifecyclePolicyVersion)
            MultiWeekProgramPolicy.reconstruct(synthetic, rows)
            previousLastStart = last
        }
        previousLastStart?.let { last ->
            if (enrollment.revision - last < 3 ||
                current.any { it.association.snapshot.enrollmentRevision - last < 3 }) inconsistent()
        }
    }
}
