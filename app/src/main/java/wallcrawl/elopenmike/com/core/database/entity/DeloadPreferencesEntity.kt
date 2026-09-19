package wallcrawl.elopenmike.com.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// No profile FK: profile saves use REPLACE. No session FK: cancelled workouts are deleted.
@Entity(tableName = "deload_preferences")
data class DeloadPreferencesEntity(
    @PrimaryKey val profileId: String,
    val revision: Long,
    val offerId: String? = null,
    val source: String? = null,
    val policyVersion: String? = null,
    val status: String? = null,
    val decidedAtEpochMillis: Long? = null,
    val sessionId: String? = null,
    val lastHandledReturnKey: String? = null
)
