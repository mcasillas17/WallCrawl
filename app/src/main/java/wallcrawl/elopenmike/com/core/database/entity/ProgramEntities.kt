package wallcrawl.elopenmike.com.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Profile saves use REPLACE; an FK would delete enrollment during an ordinary profile edit.
@Entity(tableName = "program_enrollments", indices = [Index(value = ["programId"], unique = true)])
data class ProgramEnrollmentEntity(
    @PrimaryKey val profileId: String,
    val programId: String,
    val revision: Long,
    val lifecyclePolicyVersion: String,
    val configurationPolicyVersion: String,
    val primaryGoal: String,
    val goals: String,
    val sessionsPerWeek: Int,
    val status: String,
    val createdAtTimestamp: Long,
    val updatedAtTimestamp: Long
)

@Entity(tableName = "program_session_associations",
    foreignKeys = [ForeignKey(entity = WorkoutSessionEntity::class, parentColumns = ["id"],
        childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["programId", "slotOrdinal"], unique = true), Index(value = ["profileId"])])
data class ProgramSessionAssociationEntity(
    @PrimaryKey val sessionId: String,
    val profileId: String,
    val programId: String,
    val enrollmentRevision: Long,
    val lifecyclePolicyVersion: String,
    val configurationPolicyVersion: String,
    val primaryGoal: String,
    val goals: String,
    val sessionsPerWeek: Int,
    val slotOrdinal: Int,
    val blockNumber: Int,
    val weekNumber: Int,
    val sessionInWeek: Int,
    val objective: String
)
