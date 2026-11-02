package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import wallcrawl.elopenmike.com.core.database.entity.ConditionReassessmentPendingEntity

@Dao
interface ConditionReassessmentDao {
    @Query("SELECT * FROM condition_reassessment_pending WHERE profileId = :profileId AND conditionId = :conditionId AND exerciseId = :exerciseId LIMIT 1")
    suspend fun get(profileId: Long, conditionId: String, exerciseId: String): ConditionReassessmentPendingEntity?

    @Query("SELECT * FROM condition_reassessment_pending WHERE profileId = :profileId ORDER BY conditionId, exerciseId")
    suspend fun forProfile(profileId: Long): List<ConditionReassessmentPendingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(marker: ConditionReassessmentPendingEntity)

    @Transaction
    suspend fun upsertIfNewer(marker: ConditionReassessmentPendingEntity): Boolean {
        val previous = get(marker.profileId, marker.conditionId, marker.exerciseId)
        if (previous != null && (marker.stoppedAtTimestamp < previous.stoppedAtTimestamp ||
                marker.stoppedAtTimestamp == previous.stoppedAtTimestamp && marker.eventId <= previous.eventId)) return false
        replace(marker)
        return true
    }

    @Query("DELETE FROM condition_reassessment_pending WHERE profileId = :profileId AND conditionId = :conditionId AND exerciseId = :exerciseId AND eventId = :eventId")
    suspend fun deleteIfEventMatches(profileId: Long, conditionId: String, exerciseId: String, eventId: String): Int
}
