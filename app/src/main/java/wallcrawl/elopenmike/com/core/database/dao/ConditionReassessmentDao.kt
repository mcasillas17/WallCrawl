package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import wallcrawl.elopenmike.com.core.database.entity.ConditionReassessmentPendingEntity
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.model.ConditionReassessmentRefused
import wallcrawl.elopenmike.com.core.model.ConditionViolation

data class ConditionReassessmentRead(val profile: UserProfileEntity?, val event: ConditionReassessmentPendingEntity?)

@Dao
interface ConditionReassessmentDao : UserProfileDao {
    @Transaction
    suspend fun readReassessment(profileId: String, conditionId: String, exerciseId: String): ConditionReassessmentRead =
        ConditionReassessmentRead(getProfile(profileId), get(profileId, conditionId, exerciseId))

    @Query("UPDATE user_profiles SET activeConditionsJson = :selectionsJson, revision = revision + 1 WHERE id = :profileId AND revision = :expectedRevision")
    suspend fun updateReassessedSelections(profileId: String, expectedRevision: Long, selectionsJson: String): Int

    @Transaction
    suspend fun applyReassessment(profileId: String, expectedRevision: Long, conditionId: String, exerciseId: String,
        expectedEventId: String, selectionsJson: String, validateCurrentPolicy: suspend () -> Unit) {
        fun refused(reason: String): Nothing = throw ConditionReassessmentRefused(
            ConditionViolation(reason, setOf(conditionId), setOf(exerciseId)), expectedEventId)
        if (getProfile(profileId)?.revision != expectedRevision) refused("stale-profile")
        if (expectedRevision !in 0 until Long.MAX_VALUE) refused("exhausted-profile-revision")
        if (activeWorkoutCount() != 0) refused("active-workout")
        if (get(profileId, conditionId, exerciseId)?.eventId != expectedEventId) refused("stale-event")
        validateCurrentPolicy()
        if (updateReassessedSelections(profileId, expectedRevision, selectionsJson) != 1) refused("stale-profile")
        if (deleteIfEventMatches(profileId, conditionId, exerciseId, expectedEventId) != 1) refused("stale-event")
    }

    @Query("SELECT * FROM condition_reassessment_pending WHERE profileId = :profileId AND conditionId = :conditionId AND exerciseId = :exerciseId LIMIT 1")
    suspend fun get(profileId: String, conditionId: String, exerciseId: String): ConditionReassessmentPendingEntity?

    @Query("SELECT * FROM condition_reassessment_pending WHERE profileId = :profileId ORDER BY conditionId, exerciseId")
    suspend fun forProfile(profileId: String): List<ConditionReassessmentPendingEntity>

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
    suspend fun deleteIfEventMatches(profileId: String, conditionId: String, exerciseId: String, eventId: String): Int
}
