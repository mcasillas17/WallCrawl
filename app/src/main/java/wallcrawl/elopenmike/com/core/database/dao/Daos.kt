package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets
import wallcrawl.elopenmike.com.core.database.relation.WorkoutTemplateWithExercises
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

    @Transaction
    suspend fun insertOrUpdateWithNextRevision(profile: UserProfileEntity): UserProfileEntity {
        val nextRevision = (getProfile(profile.id)?.revision ?: -1L) + 1L
        return profile.copy(revision = nextRevision).also { revisedProfile ->
            insertOrUpdate(revisedProfile)
        }
    }
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

    @Query("SELECT revision FROM user_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfileRevision(profileId: String): Long?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = :status ORDER BY completedAtTimestamp DESC LIMIT :limit")
    fun observeRecentCompletedSessions(
        limit: Int,
        status: SessionStatus = SessionStatus.COMPLETED
    ): Flow<List<WorkoutSessionWithExercisesAndSets>>

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE status = :status")
    fun observeCompletedSessionCount(
        status: SessionStatus = SessionStatus.COMPLETED
    ): Flow<Int>

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE status = :status AND completedAtTimestamp >= :startTimestamp")
    fun observeCompletedSessionCountSince(
        startTimestamp: Long,
        status: SessionStatus = SessionStatus.COMPLETED
    ): Flow<Int>

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

    @Transaction
    suspend fun insertWorkoutUnlessActive(
        session: WorkoutSessionEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
        expectedProfileId: String,
        expectedProfileRevision: Long
    ): WorkoutSessionWithExercisesAndSets {
        check(getProfileRevision(expectedProfileId) == expectedProfileRevision) {
            "User profile changed while the workout recommendation was being started."
        }
        val existingActiveSession = getActiveSession()
        if (existingActiveSession != null) return existingActiveSession

        insertWorkout(session, exercises, sets)
        return checkNotNull(getSessionWithDetails(session.id)) {
            "Inserted workout session '${session.id}' could not be read back."
        }
    }

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("UPDATE workout_sessions SET status = :completedStatus, completedAtTimestamp = :completedAt, actualDurationMinutes = :actualDuration WHERE id = :sessionId AND status = :requiredStatus")
    suspend fun completeSessionIfActive(
        sessionId: String,
        completedStatus: SessionStatus = SessionStatus.COMPLETED,
        requiredStatus: SessionStatus = SessionStatus.IN_PROGRESS,
        completedAt: Long = System.currentTimeMillis(),
        actualDuration: Int
    ): Int

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId AND status = :requiredStatus")
    suspend fun deleteActiveSession(
        sessionId: String,
        requiredStatus: SessionStatus = SessionStatus.IN_PROGRESS
    ): Int
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

    @Query("SELECT * FROM workout_sets WHERE id = :setId LIMIT 1")
    suspend fun getSetById(setId: String): WorkoutSetEntity?

    @Query(
        """
        UPDATE workout_sets
        SET completedReps = :reps,
            completedWeight = :weight,
            completedAssistanceWeight = :assistanceWeight,
            completedDurationSeconds = :durationSeconds,
            completedDistanceMeters = :distanceMeters,
            isCompleted = :isCompleted
        WHERE id = :setId
          AND EXISTS (
              SELECT 1
              FROM workout_exercises
              INNER JOIN workout_sessions
                  ON workout_sessions.id = workout_exercises.sessionId
              WHERE workout_exercises.id = workout_sets.workoutExerciseId
                AND workout_sessions.status = :requiredStatus
          )
        """
    )
    suspend fun updateSetCompletion(
        setId: String,
        reps: Int?,
        weight: Double?,
        assistanceWeight: Double?,
        durationSeconds: Int?,
        distanceMeters: Double?,
        isCompleted: Boolean,
        requiredStatus: SessionStatus = SessionStatus.IN_PROGRESS
    ): Int

    suspend fun updateSetCompletion(
        setId: String,
        reps: Int?,
        weight: Double?,
        isCompleted: Boolean,
        requiredStatus: SessionStatus = SessionStatus.IN_PROGRESS
    ): Int = updateSetCompletion(
        setId = setId,
        reps = reps,
        weight = weight,
        assistanceWeight = null,
        durationSeconds = null,
        distanceMeters = null,
        isCompleted = isCompleted,
        requiredStatus = requiredStatus
    )

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber ASC")
    suspend fun getSetsForExercise(workoutExerciseId: String): List<WorkoutSetEntity>
}

@Dao
interface WorkoutTemplateDao {
    @Transaction
    @Query("SELECT * FROM workout_templates ORDER BY updatedAtTimestamp DESC, name COLLATE NOCASE ASC")
    fun observeTemplatesWithExercises(): Flow<List<WorkoutTemplateWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :templateId LIMIT 1")
    fun observeTemplateWithExercises(templateId: String): Flow<WorkoutTemplateWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :templateId LIMIT 1")
    suspend fun getTemplateWithExercises(templateId: String): WorkoutTemplateWithExercises?

    @Query(
        "SELECT * FROM workout_template_exercises " +
            "WHERE templateId = :templateId ORDER BY orderIndex ASC"
    )
    suspend fun getExercisesForTemplate(templateId: String): List<WorkoutTemplateExerciseEntity>

    @Upsert
    suspend fun upsertTemplate(template: WorkoutTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTemplateExercises(exercises: List<WorkoutTemplateExerciseEntity>)

    @Query("DELETE FROM workout_template_exercises WHERE templateId = :templateId")
    suspend fun deleteExercisesForTemplate(templateId: String)

    @Query("DELETE FROM workout_templates WHERE id = :templateId")
    suspend fun deleteTemplateById(templateId: String): Int

    @Transaction
    suspend fun replaceTemplate(
        template: WorkoutTemplateEntity,
        exercises: List<WorkoutTemplateExerciseEntity>
    ) {
        upsertTemplate(template)
        deleteExercisesForTemplate(template.id)
        if (exercises.isNotEmpty()) insertTemplateExercises(exercises)
    }
}
