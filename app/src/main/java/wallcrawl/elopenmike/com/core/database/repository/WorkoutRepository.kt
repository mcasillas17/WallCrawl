package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.dao.WorkoutSessionDao
import wallcrawl.elopenmike.com.core.database.dao.WorkoutSetDao
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface WorkoutRepository {
    fun observeActiveSession(): Flow<WorkoutSession?>
    suspend fun getActiveSessionOnce(): WorkoutSession?
    suspend fun getSessionById(sessionId: String): WorkoutSession?
    fun observeSession(sessionId: String): Flow<WorkoutSession?>
    fun observeCompletedSessions(limit: Int = DEFAULT_OBSERVED_COMPLETED_SESSIONS): Flow<List<WorkoutSession>>
    fun observeCompletedWorkoutCount(): Flow<Int>
    fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int>
    suspend fun getRecentCompletedSessions(limit: Int = 8): List<WorkoutSession>
    suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        userProfile: UserProfile
    ): WorkoutSession
    suspend fun logSetCompletion(setId: String, reps: Int?, weight: Double?, isCompleted: Boolean)
    suspend fun completeWorkout(sessionId: String, actualDurationMinutes: Int): WorkoutSummary
    suspend fun cancelWorkout(sessionId: String)
}

class OfflineWorkoutRepository(
    private val sessionDao: WorkoutSessionDao,
    private val setDao: WorkoutSetDao
) : WorkoutRepository {

    override fun observeActiveSession(): Flow<WorkoutSession?> {
        return sessionDao.observeActiveSession().map { it?.toDomainModel() }
    }

    override suspend fun getActiveSessionOnce(): WorkoutSession? {
        return sessionDao.getActiveSession()?.toDomainModel()
    }

    override suspend fun getSessionById(sessionId: String): WorkoutSession? {
        return sessionDao.getSessionWithDetails(sessionId)?.toDomainModel()
    }

    override fun observeSession(sessionId: String): Flow<WorkoutSession?> {
        return sessionDao.observeSessionWithDetails(sessionId).map { it?.toDomainModel() }
    }

    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> {
        require(limit > 0) { "limit must be greater than zero." }
        return sessionDao.observeRecentCompletedSessions(limit).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override fun observeCompletedWorkoutCount(): Flow<Int> =
        sessionDao.observeCompletedSessionCount()

    override fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int> =
        sessionDao.observeCompletedSessionCountSince(startTimestamp)

    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> {
        require(limit > 0) { "limit must be greater than zero." }
        return sessionDao.getRecentCompletedSessions(limit).map { it.toDomainModel() }
    }

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        userProfile: UserProfile
    ): WorkoutSession {
        val sessionId = UUID.randomUUID().toString()
        val sessionEntity = WorkoutSessionEntity(
            id = sessionId,
            name = generated.name,
            startedAtTimestamp = System.currentTimeMillis(),
            completedAtTimestamp = null,
            targetDurationMinutes = generated.estimatedDurationMinutes,
            actualDurationMinutes = 0,
            weightUnit = userProfile.preferredUnit,
            status = SessionStatus.IN_PROGRESS,
            focusMusclesJson = generated.focusMuscles.joinToString("|||"),
            notes = generated.rationale
        )
        val exerciseEntities = mutableListOf<WorkoutExerciseEntity>()
        val setEntities = mutableListOf<WorkoutSetEntity>()

        generated.exercises.forEachIndexed { exIndex, genEx ->
            val exerciseInstanceId = UUID.randomUUID().toString()
            exerciseEntities.add(
                WorkoutExerciseEntity(
                    id = exerciseInstanceId,
                    sessionId = sessionId,
                    exerciseId = genEx.exerciseId,
                    orderIndex = exIndex,
                    targetSets = genEx.targetSets,
                    targetRepMin = genEx.repMin,
                    targetRepMax = genEx.repMax,
                    targetWeight = genEx.targetWeight,
                    notes = genEx.notes
                )
            )

            for (setNum in 1..genEx.targetSets) {
                setEntities.add(
                    WorkoutSetEntity(
                        id = UUID.randomUUID().toString(),
                        workoutExerciseId = exerciseInstanceId,
                        setNumber = setNum,
                        targetReps = genEx.repMax,
                        completedReps = null,
                        targetWeight = genEx.targetWeight,
                        completedWeight = null,
                        isCompleted = false,
                        rpe = null,
                        rir = null,
                        type = SetType.NORMAL
                    )
                )
            }
        }

        return sessionDao.insertWorkoutUnlessActive(
            session = sessionEntity,
            exercises = exerciseEntities,
            sets = setEntities,
            expectedProfileId = userProfile.id,
            expectedProfileRevision = userProfile.revision
        ).toDomainModel()
    }

    override suspend fun logSetCompletion(
        setId: String,
        reps: Int?,
        weight: Double?,
        isCompleted: Boolean
    ) {
        require(setId.isNotBlank()) { "setId must not be blank." }
        require(reps == null || reps in 0..MAX_LOGGED_REPS) {
            "reps must be between zero and $MAX_LOGGED_REPS."
        }
        require(!isCompleted || (reps != null && reps > 0)) {
            "A completed set must have positive reps."
        }
        require(weight == null || (weight.isFinite() && weight in 0.0..MAX_LOGGED_WEIGHT)) {
            "weight must be finite and between zero and $MAX_LOGGED_WEIGHT."
        }

        val affectedRows = setDao.updateSetCompletion(
            setId = setId,
            reps = reps,
            weight = weight,
            isCompleted = isCompleted
        )
        check(affectedRows == 1) {
            "Workout set '$setId' was not found or its session is not in progress."
        }
    }

    override suspend fun completeWorkout(sessionId: String, actualDurationMinutes: Int): WorkoutSummary {
        require(sessionId.isNotBlank()) { "sessionId must not be blank." }
        require(actualDurationMinutes > 0) { "actualDurationMinutes must be greater than zero." }
        val completedTimestamp = System.currentTimeMillis()
        val affectedRows = sessionDao.completeSessionIfActive(
            sessionId = sessionId,
            completedAt = completedTimestamp,
            actualDuration = actualDurationMinutes
        )
        check(affectedRows == 1) {
            "Workout session '$sessionId' was not found or is not in progress."
        }

        val session = checkNotNull(getSessionById(sessionId)) {
            "Completed workout session '$sessionId' could not be read back."
        }
        val totalSets = session.completedSetsCount
        val totalVolume = session.totalVolume

        return WorkoutSummary(
            sessionId = sessionId,
            workoutName = session.name,
            durationMinutes = actualDurationMinutes,
            totalSetsCompleted = totalSets,
            totalVolume = totalVolume,
            prCount = 0,
            unit = session.weightUnit,
            completedAtTimestamp = completedTimestamp
        )
    }

    override suspend fun cancelWorkout(sessionId: String) {
        require(sessionId.isNotBlank()) { "sessionId must not be blank." }
        check(sessionDao.deleteActiveSession(sessionId) == 1) {
            "Workout session '$sessionId' was not found or is not in progress."
        }
    }

    private fun WorkoutSessionWithExercisesAndSets.toDomainModel(): WorkoutSession {
        val domainExercises = exercisesWithSets
            .sortedBy { it.exercise.orderIndex }
            .map { exWithSets ->
                val domainSets = exWithSets.sets
                    .sortedBy { it.setNumber }
                    .map { setEntity ->
                        WorkoutSet(
                            id = setEntity.id,
                            workoutExerciseId = setEntity.workoutExerciseId,
                            setNumber = setEntity.setNumber,
                            targetReps = setEntity.targetReps,
                            completedReps = setEntity.completedReps,
                            targetWeight = setEntity.targetWeight,
                            completedWeight = setEntity.completedWeight,
                            isCompleted = setEntity.isCompleted,
                            rpe = setEntity.rpe,
                            rir = setEntity.rir,
                            type = setEntity.type
                        )
                    }

                WorkoutExercise(
                    id = exWithSets.exercise.id,
                    sessionId = exWithSets.exercise.sessionId,
                    exerciseId = exWithSets.exercise.exerciseId,
                    orderIndex = exWithSets.exercise.orderIndex,
                    targetSets = exWithSets.exercise.targetSets,
                    targetRepMin = exWithSets.exercise.targetRepMin,
                    targetRepMax = exWithSets.exercise.targetRepMax,
                    targetWeight = exWithSets.exercise.targetWeight,
                    notes = exWithSets.exercise.notes,
                    sets = domainSets
                )
            }

        val focusMusclesList = if (session.focusMusclesJson.isBlank()) {
            emptyList()
        } else {
            session.focusMusclesJson.split("|||").filter { it.isNotBlank() }
        }

        return WorkoutSession(
            id = session.id,
            name = session.name,
            startedAtTimestamp = session.startedAtTimestamp,
            completedAtTimestamp = session.completedAtTimestamp,
            targetDurationMinutes = session.targetDurationMinutes,
            actualDurationMinutes = session.actualDurationMinutes,
            weightUnit = session.weightUnit,
            status = session.status,
            focusMuscles = focusMusclesList,
            exercises = domainExercises,
            notes = session.notes
        )
    }

    private companion object {
        const val MAX_LOGGED_REPS = 1_000
        const val MAX_LOGGED_WEIGHT = 100_000.0
    }
}

const val DEFAULT_OBSERVED_COMPLETED_SESSIONS = 500
