package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import wallcrawl.elopenmike.com.core.database.entity.*
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.model.*

/** Bounded headers only: program progress never reads exercise or set graphs. */
data class ProgramSlotRow(
    @Embedded val association: ProgramSessionAssociationEntity,
    val sessionStatus: String,
    val completedAtTimestamp: Long?,
    val recommendationDigest: String?
) {
    internal fun validateRawBinding() {
        association.validateRaw()
        require(recommendationDigest == association.rawAssociationDigest()) {
            "Program membership digest is absent or does not match."
        }
        if (association.supported()) association.toProgramAssociation()
        if (sessionStatus !in listOf(SessionStatus.IN_PROGRESS.name, SessionStatus.COMPLETED.name) ||
            (sessionStatus == SessionStatus.COMPLETED.name) != (completedAtTimestamp != null) ||
            (completedAtTimestamp != null && completedAtTimestamp !in 0..4_102_444_800_000L)) {
            throw ProgramException(ProgramFailure.INVALID_PROGRESS)
        }
    }
    internal fun toSlot(): ProgramSlotSession {
        validateRawBinding()
        val decoded = association.toProgramAssociation()
        return ProgramSlotSession(decoded,
            SessionStatus.entries.firstOrNull { it.name == sessionStatus } ?: throw ProgramException(ProgramFailure.INVALID_PROGRESS),
            completedAtTimestamp)
    }
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM program_enrollments WHERE profileId = :profileId")
    suspend fun getProgramEnrollment(profileId: String): ProgramEnrollmentEntity?

    @Query("SELECT * FROM program_session_associations WHERE sessionId = :sessionId")
    suspend fun getProgramAssociation(sessionId: String): ProgramSessionAssociationEntity?

    @Query("""SELECT a.*, s.status AS sessionStatus, s.completedAtTimestamp,
        r.programAssociationDigest AS recommendationDigest FROM program_session_associations a
        JOIN workout_sessions s ON s.id = a.sessionId
        LEFT JOIN workout_recommendation_records r ON r.sessionId = a.sessionId
        WHERE a.programId = :programId ORDER BY a.slotOrdinal LIMIT 25""")
    suspend fun selectProgramSlots(programId: String): List<ProgramSlotRow>

    @Query("""SELECT EXISTS(SELECT 1 FROM workout_recommendation_records r
        WHERE r.programAssociationDigest IS NOT NULL AND NOT EXISTS
        (SELECT 1 FROM program_session_associations a WHERE a.sessionId = r.sessionId))""")
    suspend fun hasUnboundProgramRecommendations(): Boolean

    suspend fun requireProgramAssociationIntegrity() {
        if (hasUnboundProgramRecommendations()) throw ProgramException(ProgramFailure.INVALID_PROGRESS)
    }

    @Transaction
    suspend fun getProgramSlots(programId: String): List<ProgramSlotRow> {
        // A lost row cannot identify its former run; check retained digest ownership before trusting a prefix.
        requireProgramAssociationIntegrity()
        return selectProgramSlots(programId)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM program_enrollments WHERE programId = :programId UNION ALL SELECT 1 FROM program_session_associations WHERE programId = :programId)")
    suspend fun programIdExists(programId: String): Boolean

    @Query("SELECT * FROM user_profiles WHERE id = :profileId")
    suspend fun getProgramProfile(profileId: String): UserProfileEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM workout_sessions WHERE status = 'IN_PROGRESS')")
    suspend fun hasProgramActiveWorkout(): Boolean

    @Upsert
    suspend fun upsertProgramEnrollment(enrollment: ProgramEnrollmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProgramAssociation(association: ProgramSessionAssociationEntity)

    suspend fun readProgramHeader(profileId: String): ProgramHeader =
        getProgramEnrollment(profileId)?.header() ?: ProgramHeader.absent(profileId)

    suspend fun validateOpaqueProgramSlots(profileId: String, programId: String): List<ProgramSlotRow> {
        val slots = getProgramSlots(programId)
        if (slots.size > wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy.MAX_SLOTS) throw ProgramException(ProgramFailure.INVALID_PROGRESS)
        slots.forEach {
            it.validateRawBinding()
            if (it.association.profileId != profileId) throw ProgramException(ProgramFailure.INVALID_IDENTITY)
        }
        return slots
    }

    @Transaction
    suspend fun readProgramState(profileId: String): ProgramReadState {
        val profile = getProgramProfile(profileId)?.toUserProfile()
        val row = getProgramEnrollment(profileId)
        if (row == null) {
            requireProgramAssociationIntegrity()
            return ProgramReadState.Empty(ProgramHeader.absent(profileId), profile)
        }
        row.validateRaw()
        val header = row.header()
        if (!row.supported()) {
            validateOpaqueProgramSlots(row.profileId, row.programId)
            return ProgramReadState.Unsupported(header, row.facts(), row.status, profile)
        }
        val enrollment = row.toProgramEnrollment()
        val sessions = getProgramSlots(row.programId).map { it.toSlot() }
        return ProgramReadState.Supported(header, enrollment,
            wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy.reconstruct(enrollment, sessions), sessions, profile)
    }
}
