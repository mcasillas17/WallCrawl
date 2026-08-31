package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import wallcrawl.elopenmike.com.core.database.entity.WeeklyDoseLedgerStateEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets
import wallcrawl.elopenmike.com.core.model.SessionStatus

/**
 * Reads the completed history a weekly ledger is reconstructed from.
 *
 * This is separate from the general workout DAO on purpose: reconstruction needs the whole
 * week, so there is no result limit here. A capped "recent sessions" query would silently
 * under-report a busy week, which is exactly the kind of quiet inaccuracy the ledger exists
 * to avoid.
 */
@Dao
interface CompletedWorkoutHistoryDao {

    /**
     * Every completed session whose completion timestamp lies in `[start, end)`, with its
     * exercises and sets loaded in the same transaction.
     *
     * Ordering is deterministic — completion time, then session id as a stable tie-break —
     * so two reads of the same history always produce the same list. Only `COMPLETED`
     * sessions are considered: planned target sets and in-progress or cancelled work are
     * not exposure and are never counted as such.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = :status
          AND completedAtTimestamp IS NOT NULL
          AND completedAtTimestamp >= :startEpochMillis
          AND completedAtTimestamp < :endEpochMillisExclusive
        ORDER BY completedAtTimestamp ASC, id ASC
        """
    )
    suspend fun selectCompletedSessionsInRange(
        startEpochMillis: Long,
        endEpochMillisExclusive: Long,
        status: SessionStatus = SessionStatus.COMPLETED
    ): List<WorkoutSessionWithExercisesAndSets>

    /** Rejects an unusable range before it can quietly return an empty week. */
    suspend fun getCompletedSessionsInRange(
        startEpochMillis: Long,
        endEpochMillisExclusive: Long
    ): List<WorkoutSessionWithExercisesAndSets> {
        require(endEpochMillisExclusive > startEpochMillis) {
            "endEpochMillisExclusive must be greater than startEpochMillis."
        }
        return selectCompletedSessionsInRange(startEpochMillis, endEpochMillisExclusive)
    }
}

/**
 * Reads and writes the derived weekly ledger cache.
 *
 * Every statement is a parameterised Room query. There is no incrementing update: a row is
 * only ever replaced wholesale by a ledger that was just recomputed from completed history.
 */
@Dao
interface WeeklyDoseLedgerStateDao {

    @Query(
        """
        SELECT * FROM weekly_dose_ledger_state
        WHERE profileId = :profileId
          AND weekStartEpochDay = :weekStartEpochDay
          AND timeZoneId = :timeZoneId
          AND policyVersion = :policyVersion
        LIMIT 1
        """
    )
    suspend fun findCachedLedger(
        profileId: String,
        weekStartEpochDay: Long,
        timeZoneId: String,
        policyVersion: String
    ): WeeklyDoseLedgerStateEntity?

    @Upsert
    suspend fun upsertCachedLedger(state: WeeklyDoseLedgerStateEntity)

    @Query("DELETE FROM weekly_dose_ledger_state WHERE profileId = :profileId")
    suspend fun deleteCachedLedgersForProfile(profileId: String): Int
}
