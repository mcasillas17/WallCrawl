package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.database.entity.DeloadPreferencesEntity
import wallcrawl.elopenmike.com.core.database.repository.toDeloadPreferences
import wallcrawl.elopenmike.com.core.database.repository.toEntity
import wallcrawl.elopenmike.com.core.model.DeloadChoiceStatus
import wallcrawl.elopenmike.com.core.model.DeloadPreferences
import wallcrawl.elopenmike.com.core.ai.DeloadOfferPolicy
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutRecommendationRecordEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator
import wallcrawl.elopenmike.com.core.database.relation.WorkoutTemplateWithExercises
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
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
interface WorkoutSessionDao : CompletedWorkoutHistoryDao, WorkoutHistoryDao, ProgramDao {
    @Transaction
    suspend fun readWorkoutSummary(sessionId: String, calculator: ProgressCalculator): WorkoutSummary? {
        val session = getBoundedHistorySessions(listOf(sessionId)).singleOrNull()?.toWorkoutSession()
            ?: return null
        if (session.status != SessionStatus.COMPLETED) return null
        validateProgramBinding(getRecommendationRecord(sessionId), getProgramAssociation(sessionId))
        return calculator.summarize(session, getPersonalRecordBaselines(sessionId))
    }

    @Transaction
    suspend fun completeWorkoutAndReadSummary(
        sessionId: String, completedAt: Long, actualDuration: Int, calculator: ProgressCalculator
    ): WorkoutSummary {
        readWorkoutSummary(sessionId, calculator)?.let { return it }
        if (!isLifecycleSessionActive(sessionId)) {
            throw ProgramException(ProgramFailure.MISSING_OR_NOT_ACTIVE)
        }
        check(completeSessionIfActive(sessionId, completedAt = completedAt, actualDuration = actualDuration) == 1) {
            "Workout session '$sessionId' was not found or is not in progress."
        }
        return checkNotNull(readWorkoutSummary(sessionId, calculator)) {
            "Completed workout session '$sessionId' could not be read back."
        }
    }

    @Query("SELECT status FROM workout_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getLifecycleSessionStatus(sessionId: String): String?

    suspend fun isLifecycleSessionActive(sessionId: String): Boolean {
        val status = getLifecycleSessionStatus(sessionId) ?: return false
        if (SessionStatus.entries.none { it.name == status }) throw ProgramException(ProgramFailure.INVALID_PROGRESS)
        return status == SessionStatus.IN_PROGRESS.name
    }

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

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE status = :status AND completedAtTimestamp >= :startTimestamp AND completedAtTimestamp < :endTimestampExclusive")
    fun observeCompletedSessionCountInRange(
        startTimestamp: Long,
        endTimestampExclusive: Long,
        status: SessionStatus = SessionStatus.COMPLETED
    ): Flow<Int>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = :status ORDER BY completedAtTimestamp DESC LIMIT :limit")
    suspend fun getRecentCompletedSessions(
        limit: Int,
        status: SessionStatus = SessionStatus.COMPLETED
    ): List<WorkoutSessionWithExercisesAndSets>

    @Transaction
    @Query("SELECT * FROM workout_sessions ORDER BY startedAtTimestamp DESC, id ASC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<WorkoutSessionWithExercisesAndSets>

    @Transaction
    @Query("SELECT * FROM workout_sessions ORDER BY startedAtTimestamp DESC")
    fun observeAllSessions(): Flow<List<WorkoutSessionWithExercisesAndSets>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecommendationRecord(record: WorkoutRecommendationRecordEntity)

    @Query("SELECT * FROM workout_recommendation_records WHERE sessionId = :sessionId")
    suspend fun getRecommendationRecord(sessionId: String): WorkoutRecommendationRecordEntity?

    @Query("SELECT * FROM workout_recommendation_records WHERE sessionId IN (:sessionIds) ORDER BY sessionId ASC")
    suspend fun getRecommendationRecords(sessionIds: List<String>): List<WorkoutRecommendationRecordEntity>

    @Query("SELECT * FROM deload_preferences WHERE profileId = :profileId")
    suspend fun getDeloadPreferences(profileId: String): DeloadPreferencesEntity?

    @Upsert
    suspend fun upsertDeloadPreferences(preferences: DeloadPreferencesEntity)

    /**
     * Starts a workout, or changes nothing at all.
     *
     * [recommendation] is written here rather than afterwards so a started session and the
     * provenance of the plan it came from land together. Anything that refuses the start —
     * a profile edited underneath it, an already-active session, or a failed insert — leaves
     * neither a session nor a record, so there is never a partial active session and never a
     * session whose recommendation evidence was silently lost.
     */
    @Transaction
    suspend fun insertWorkoutUnlessActive(
        session: WorkoutSessionEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
        expectedProfileId: String,
        expectedProfileRevision: Long,
        recommendation: WorkoutRecommendationRecordEntity? = null,
        expectedDeloadDecisionRevision: Long? = null,
        acceptedDeloadOfferId: String? = null,
        expectedProgramHeader: ProgramHeader? = null,
        programSession: ProgramSessionSnapshot? = null
    ): WorkoutSessionWithExercisesAndSets {
        check(getProfileRevision(expectedProfileId) == expectedProfileRevision) {
            "User profile changed while the workout recommendation was being started."
        }
        val existingActiveSession = getActiveSession()
        if (existingActiveSession != null) return existingActiveSession

        val header = if (session.origin == WorkoutOrigin.PLANNER) readProgramHeader(expectedProfileId) else null
        if (header != null && expectedProgramHeader != header &&
            (expectedProgramHeader != null || header.programId != null)) {
            throw ProgramException(ProgramFailure.STALE_STATE)
        }
        val association = programSession?.let { ProgramAssociation(session.id, expectedProfileId, it) }
        require((recommendation?.programAssociationDigest != null) == (association != null)) {
            "Program membership requires a bound recommendation and association."
        }
        val enrollmentUpdate = association?.let {
            require(session.status == SessionStatus.IN_PROGRESS && session.origin == WorkoutOrigin.PLANNER)
            require(recommendation?.programAssociationDigest == MultiWeekProgramPolicy.associationDigest(it))
            val enrollment = getProgramEnrollment(expectedProfileId)?.toProgramEnrollment()
                ?: throw ProgramException(ProgramFailure.STALE_STATE)
            val slots = getProgramSlots(enrollment.programId).map { row -> row.toSlot() }
            val progress = MultiWeekProgramPolicy.reconstruct(enrollment, slots)
            val expectedSnapshot = MultiWeekProgramPolicy.snapshot(enrollment,
                progress.nextPosition ?: throw ProgramException(ProgramFailure.INVALID_ACTION))
            if (it.snapshot != expectedSnapshot) throw ProgramException(ProgramFailure.STALE_STATE)
            MultiWeekProgramPolicy.transition(getProgramProfile(expectedProfileId)?.toUserProfile(), enrollment, slots,
                ProgramAction.START_SESSION, ProgramExpectation(expectedProfileId, expectedProfileRevision, enrollment.programId, enrollment.revision),
                session.startedAtTimestamp)
        }

        // Null is legacy revision zero, never permission to skip the freshness check.
        // Manual templates have no recommendation and neither read nor consume a choice.
        val preferences = if (recommendation != null) {
            (getDeloadPreferences(expectedProfileId)?.toDeloadPreferences()
                ?: DeloadPreferences(expectedProfileId)).also {
                check(it.revision == (expectedDeloadDecisionRevision ?: 0L)) {
                    "The deload decision changed while the workout was being started."
                }
                check(DeloadOfferPolicy.accepted(it)?.offer?.id == acceptedDeloadOfferId) {
                    "The accepted deload offer does not match the recommendation."
                }
            }
        } else null

        insertWorkout(session, exercises, sets)
        recommendation?.let { record ->
            check(record.sessionId == session.id) {
                "A recommendation record must belong to the session it is written with."
            }
            insertRecommendationRecord(record)
        }
        association?.let { insertProgramAssociation(it.toEntity()) }
        enrollmentUpdate?.let { upsertProgramEnrollment(it.toEntity()) }
        preferences?.let { current ->
            DeloadOfferPolicy.accepted(current)?.let { accepted ->
                check(current.revision < Long.MAX_VALUE) { "Deload revision is exhausted." }
                upsertDeloadPreferences(current.copy(
                    revision = current.revision + 1,
                    choice = accepted.copy(status = DeloadChoiceStatus.CONSUMED,
                        decidedAtEpochMillis = session.startedAtTimestamp, sessionId = session.id)
                ).toEntity())
            }
        }
        return checkNotNull(getSessionWithDetails(session.id)) {
            "Inserted workout session '${session.id}' could not be read back."
        }
    }

    @Update
    suspend fun updateSessionRow(session: WorkoutSessionEntity)

    @Transaction
    suspend fun updateSession(session: WorkoutSessionEntity) {
        require(getProgramAssociation(session.id) == null && getRecommendationRecord(session.id)?.programAssociationDigest == null) {
            "Program sessions must use the atomic lifecycle operations."
        }
        updateSessionRow(session)
    }

    @Query("UPDATE workout_sessions SET status = :completedStatus, completedAtTimestamp = :completedAt, actualDurationMinutes = :actualDuration WHERE id = :sessionId AND status = :requiredStatus")
    suspend fun completeSessionRow(
        sessionId: String, completedStatus: SessionStatus, requiredStatus: SessionStatus,
        completedAt: Long, actualDuration: Int
    ): Int

    @Transaction
    suspend fun completeSessionIfActive(
        sessionId: String,
        completedStatus: SessionStatus = SessionStatus.COMPLETED,
        requiredStatus: SessionStatus = SessionStatus.IN_PROGRESS,
        completedAt: Long = System.currentTimeMillis(),
        actualDuration: Int
    ): Int {
        require(completedStatus == SessionStatus.COMPLETED && requiredStatus == SessionStatus.IN_PROGRESS)
        if (!isLifecycleSessionActive(sessionId)) return 0
        transitionAssociatedSession(sessionId, true, completedAt)
        return completeSessionRow(sessionId, completedStatus, requiredStatus, completedAt, actualDuration)
    }

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId AND status = :requiredStatus")
    suspend fun deleteActiveSessionRow(sessionId: String, requiredStatus: SessionStatus): Int

    @Transaction
    suspend fun deleteActiveSession(
        sessionId: String,
        requiredStatus: SessionStatus = SessionStatus.IN_PROGRESS
    ): Int {
        require(requiredStatus == SessionStatus.IN_PROGRESS)
        if (!isLifecycleSessionActive(sessionId)) return 0
        transitionAssociatedSession(sessionId, false, System.currentTimeMillis())
        return deleteActiveSessionRow(sessionId, requiredStatus)
    }

    suspend fun transitionAssociatedSession(sessionId: String, finish: Boolean, at: Long) {
        val association = getProgramAssociation(sessionId)
        validateProgramBinding(getRecommendationRecord(sessionId), association)
        association ?: return
        val row = getProgramEnrollment(association.profileId) ?: throw ProgramException(ProgramFailure.INVALID_IDENTITY)
        row.validateRaw()
        if (row.programId != association.programId || row.profileId != association.profileId ||
            row.status != ProgramStatus.ACTIVE.name || association.enrollmentRevision != row.revision - 1) {
            throw ProgramException(ProgramFailure.INVALID_PROGRESS)
        }
        val profile = getProgramProfile(row.profileId)?.toUserProfile()
        val expected = ProgramExpectation(row.profileId, profile?.revision ?: 0, row.programId, row.revision)
        if (!row.supported() || !association.supported()) {
            if (finish) throw ProgramException(ProgramFailure.UNSUPPORTED_POLICY)
            MultiWeekProgramPolicy.checkExpected(profile, row.header(), expected)
            val active = validateOpaqueProgramSlots(row.profileId, row.programId).filter { it.sessionStatus == SessionStatus.IN_PROGRESS.name }
            if (active.singleOrNull()?.association?.sessionId != sessionId) throw ProgramException(ProgramFailure.INVALID_PROGRESS)
            if (at !in 0..4_102_444_800_000L) throw ProgramException(ProgramFailure.INVALID_IDENTITY)
            upsertProgramEnrollment(row.copy(revision = MultiWeekProgramPolicy.nextRevision(row.revision), updatedAtTimestamp = at))
        } else {
            val update = MultiWeekProgramPolicy.transition(profile, row.toProgramEnrollment(),
                getProgramSlots(row.programId).map { it.toSlot() },
                if (finish) ProgramAction.FINISH_SESSION else ProgramAction.CANCEL_SESSION, expected, at, sessionId = sessionId)
            upsertProgramEnrollment(update.toEntity())
        }
    }

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

    /**
     * Writes one set's complete typed outcome in a single guarded statement.
     *
     * Every performance, feedback, timestamp, and stop-reason column is written together,
     * so no partial write can leave a row claiming completion without its timestamp, or
     * keep completion-only feedback after completion was cleared. The EXISTS clause keeps
     * the pre-existing rule that only a set belonging to an in-progress session can
     * change, which is what makes completed history immutable. All values are bound
     * parameters; nothing is ever interpolated into the statement.
     */
    @Query(
        """
        UPDATE workout_sets
        SET completedReps = :reps,
            completedWeight = :weight,
            completedAssistanceWeight = :assistanceWeight,
            completedDurationSeconds = :durationSeconds,
            completedDistanceMeters = :distanceMeters,
            rpe = :rpe,
            rir = :rir,
            feltManageable = :feltManageable,
            completedAtTimestamp = :completedAtTimestamp,
            stoppedAtTimestamp = :stoppedAtTimestamp,
            stopReason = :stopReason,
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
    suspend fun updateSetOutcome(
        setId: String,
        reps: Int?,
        weight: Double?,
        assistanceWeight: Double?,
        durationSeconds: Int?,
        distanceMeters: Double?,
        rpe: Float?,
        rir: Int?,
        feltManageable: Boolean?,
        completedAtTimestamp: Long?,
        stoppedAtTimestamp: Long?,
        stopReason: SetStopReason?,
        isCompleted: Boolean,
        requiredStatus: SessionStatus = SessionStatus.IN_PROGRESS
    ): Int

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

    @Query("SELECT EXISTS(SELECT 1 FROM workout_templates WHERE id = :templateId)")
    suspend fun templateExists(templateId: String): Boolean

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
