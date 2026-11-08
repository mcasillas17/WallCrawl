package wallcrawl.elopenmike.com.core.database.entity

import androidx.room.Entity
import wallcrawl.elopenmike.com.core.model.PendingConditionReassessment

/** No foreign keys: profile REPLACE and session deletion must preserve the marker. */
@Entity(tableName = "condition_reassessment_pending", primaryKeys = ["profileId", "conditionId", "exerciseId"])
data class ConditionReassessmentPendingEntity(
    val profileId: String,
    val conditionId: String,
    val exerciseId: String,
    val eventId: String,
    val stoppedContextIdentity: String,
    val responsePolicyVersion: Int,
    val stoppedAtTimestamp: Long,
    val sourceSetId: String?
)

fun PendingConditionReassessment.toEntity() = ConditionReassessmentPendingEntity(
    profileId, conditionId, exerciseId, eventId, stoppedContextIdentity,
    responsePolicyVersion, stoppedAtTimestamp, sourceSetId
)

fun ConditionReassessmentPendingEntity.toModel() = PendingConditionReassessment(
    profileId, conditionId, exerciseId, eventId, stoppedContextIdentity,
    responsePolicyVersion, stoppedAtTimestamp, sourceSetId
)
