package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets
import wallcrawl.elopenmike.com.core.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    fun observeProfile(id: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfile(id: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfileEntity)
}

@Dao
interface WorkoutSessionDao {
    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSessionWithDetails(sessionId: String): Flow<WorkoutSessionWithExercisesAndSets?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionWithDetails(sessionId: String): WorkoutSessionWithExercisesAndSets?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = :status ORDER BY startedAtTimestamp DESC LIMIT 1")
    fun observeActiveSession(status: SessionStatus = SessionStatus.IN_PROGRESS): Flow<WorkoutSessionWithExercisesAndSets?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = :status ORDER BY startedAtTimestamp DESC LIMIT 1")
    suspend fun getActiveSession(status: SessionStatus = SessionStatus.IN_PROGRESS): WorkoutSessionWithExercisesAndSets?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = :status ORDER BY completedAtTimestamp DESC")
    fun observeCompletedSessions(status: SessionStatus = SessionStatus.COMPLETED): Flow<List<WorkoutSessionWithExercisesAndSets>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = :status ORDER BY completedAtTimestamp DESC LIMIT :limit")
    suspend fun getRecentCompletedSessions(
        limit: Int,
        status: SessionStatus = SessionStatus.COMPLETED
    ): List<WorkoutSessionWithExercisesAndSets>

    @Transaction
    @Query("SELECT * FROM workout_sessions ORDER BY startedAtTimestamp DESC")
    fun observeAllSessions(): Flow<List<WorkoutSessionWithExercisesAndSets>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercises(exercises: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>)

    @Transaction
    suspend fun insertWorkout(
        session: WorkoutSessionEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>
    ) {
        insertSession(session)
        insertWorkoutExercises(exercises)
        insertWorkoutSets(sets)
    }

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("UPDATE workout_sessions SET status = :status, completedAtTimestamp = :completedAt, actualDurationMinutes = :actualDuration WHERE id = :sessionId")
    suspend fun completeSession(
        sessionId: String,
        status: SessionStatus = SessionStatus.COMPLETED,
        completedAt: Long = System.currentTimeMillis(),
        actualDuration: Int
    )

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

@Dao
interface WorkoutExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: WorkoutExerciseEntity)

    @Query("SELECT * FROM workout_exercises WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getExercisesForSession(sessionId: String): List<WorkoutExerciseEntity>
}

@Dao
interface WorkoutSetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSet(set: WorkoutSetEntity)

    @Query("UPDATE workout_sets SET completedReps = :reps, completedWeight = :weight, isCompleted = :isCompleted WHERE id = :setId")
    suspend fun updateSetCompletion(
        setId: String,
        reps: Int?,
        weight: Double?,
        isCompleted: Boolean
    ): Int

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber ASC")
    suspend fun getSetsForExercise(workoutExerciseId: String): List<WorkoutSetEntity>
}
