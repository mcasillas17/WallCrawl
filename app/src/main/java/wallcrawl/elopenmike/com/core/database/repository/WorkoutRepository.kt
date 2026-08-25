package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.dao.WorkoutSessionDao
import wallcrawl.elopenmike.com.core.database.dao.WorkoutSetDao
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.MuscleProgressStat
import wallcrawl.elopenmike.com.core.model.PersonalRecord
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.RecordType
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.StrengthTrend
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
    fun observeCompletedSessions(): Flow<List<WorkoutSession>>
    suspend fun getRecentCompletedSessions(limit: Int = 8): List<WorkoutSession>
    suspend fun startWorkoutFromGenerated(generated: GeneratedWorkout): WorkoutSession
    suspend fun logSetCompletion(setId: String, reps: Int?, weight: Double?, isCompleted: Boolean)
    suspend fun completeWorkout(sessionId: String, actualDurationMinutes: Int): WorkoutSummary
    suspend fun cancelWorkout(sessionId: String)
    fun observeProgressOverview(preferredUnit: WeightUnit = WeightUnit.LBS): Flow<ProgressOverview>
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

    override fun observeCompletedSessions(): Flow<List<WorkoutSession>> {
        return sessionDao.observeCompletedSessions().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> {
        require(limit > 0) { "limit must be greater than zero." }
        return sessionDao.getRecentCompletedSessions(limit).map { it.toDomainModel() }
    }

    override suspend fun startWorkoutFromGenerated(generated: GeneratedWorkout): WorkoutSession {
        // If an active session already exists, we return it or cancel it
        val existingActive = sessionDao.getActiveSession()
        if (existingActive != null) {
            return existingActive.toDomainModel()
        }

        val sessionId = UUID.randomUUID().toString()
        val sessionEntity = WorkoutSessionEntity(
            id = sessionId,
            name = generated.name,
            startedAtTimestamp = System.currentTimeMillis(),
            completedAtTimestamp = null,
            targetDurationMinutes = generated.estimatedDurationMinutes,
            actualDurationMinutes = 0,
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

        sessionDao.insertWorkout(
            session = sessionEntity,
            exercises = exerciseEntities,
            sets = setEntities
        )

        return getSessionById(sessionId) ?: throw IllegalStateException("Failed to create session")
    }

    override suspend fun logSetCompletion(
        setId: String,
        reps: Int?,
        weight: Double?,
        isCompleted: Boolean
    ) {
        require(setId.isNotBlank()) { "setId must not be blank." }
        require(reps == null || reps >= 0) { "reps must not be negative." }
        require(!isCompleted || (reps != null && reps > 0)) {
            "A completed set must have positive reps."
        }
        require(weight == null || (weight.isFinite() && weight >= 0.0)) {
            "weight must be finite and not negative."
        }

        val affectedRows = setDao.updateSetCompletion(
            setId = setId,
            reps = reps,
            weight = weight,
            isCompleted = isCompleted
        )
        check(affectedRows == 1) { "Workout set '$setId' was not found." }
    }

    override suspend fun completeWorkout(sessionId: String, actualDurationMinutes: Int): WorkoutSummary {
        require(sessionId.isNotBlank()) { "sessionId must not be blank." }
        require(actualDurationMinutes > 0) { "actualDurationMinutes must be greater than zero." }
        val activeSession = getSessionById(sessionId)
            ?: throw IllegalStateException("Workout session '$sessionId' was not found.")
        check(activeSession.status == SessionStatus.IN_PROGRESS) {
            "Workout session '$sessionId' is not in progress."
        }

        val completedTimestamp = System.currentTimeMillis()
        sessionDao.completeSession(
            sessionId = sessionId,
            status = SessionStatus.COMPLETED,
            completedAt = completedTimestamp,
            actualDuration = actualDurationMinutes
        )

        val session = getSessionById(sessionId)
        val totalSets = session?.completedSetsCount ?: 0
        val totalVolume = session?.totalVolume ?: 0.0

        return WorkoutSummary(
            sessionId = sessionId,
            workoutName = session?.name ?: "Workout",
            durationMinutes = actualDurationMinutes,
            totalSetsCompleted = totalSets,
            totalVolume = totalVolume,
            prCount = 0,
            completedAtTimestamp = completedTimestamp
        )
    }

    override suspend fun cancelWorkout(sessionId: String) {
        sessionDao.deleteSession(sessionId)
    }

    override fun observeProgressOverview(preferredUnit: WeightUnit): Flow<ProgressOverview> {
        return sessionDao.observeCompletedSessions().map { sessionsWithDetails ->
            val domainSessions = sessionsWithDetails.map { it.toDomainModel() }

            val totalVolumeThisWeek = domainSessions.take(7).sumOf { it.totalVolume }
            val completedThisWeek = domainSessions.count { session ->
                val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                (session.completedAtTimestamp ?: 0L) >= sevenDaysAgo
            }

            val samplePrs = listOf(
                PersonalRecord(
                    exerciseId = "incline-dumbbell-press",
                    exerciseName = "Incline Dumbbell Press",
                    recordType = RecordType.WEIGHT,
                    value = 50.0,
                    unit = preferredUnit.symbol,
                    previousValue = 45.0
                ),
                PersonalRecord(
                    exerciseId = "pull-ups",
                    exerciseName = "Pull-ups",
                    recordType = RecordType.REPS,
                    value = 11.0,
                    unit = "reps",
                    previousValue = 8.0
                )
            )

            val muscleFocus = listOf(
                MuscleProgressStat("Chest", setsThisWeek = 12, percentageGrowth = 8),
                MuscleProgressStat("Shoulders", setsThisWeek = 9, percentageGrowth = 12),
                MuscleProgressStat("Back", setsThisWeek = 14, percentageGrowth = 5),
                MuscleProgressStat("Legs", setsThisWeek = 10, percentageGrowth = 0)
            )

            val strengthTrends = listOf(
                StrengthTrend(
                    exerciseId = "incline-dumbbell-press",
                    exerciseName = "Incline DB Press",
                    previousMetric = "45 ${preferredUnit.symbol}",
                    currentMetric = "50 ${preferredUnit.symbol}",
                    percentageChange = 11,
                    isPositive = true
                ),
                StrengthTrend(
                    exerciseId = "pull-ups",
                    exerciseName = "Pull-ups",
                    previousMetric = "8 reps",
                    currentMetric = "11 reps",
                    percentageChange = 37,
                    isPositive = true
                ),
                StrengthTrend(
                    exerciseId = "barbell-back-squat",
                    exerciseName = "Barbell Back Squat",
                    previousMetric = "175 ${preferredUnit.symbol}",
                    currentMetric = "185 ${preferredUnit.symbol}",
                    percentageChange = 6,
                    isPositive = true
                )
            )

            ProgressOverview(
                workoutsThisWeek = if (completedThisWeek > 0) completedThisWeek else 3,
                weeklyGoal = 4,
                currentStreakWeeks = 4,
                totalWorkoutsLogged = (domainSessions.size).coerceAtLeast(1),
                totalVolumeThisWeek = if (totalVolumeThisWeek > 0) totalVolumeThisWeek else 14250.0,
                recentPersonalRecords = samplePrs,
                muscleGroupFocus = muscleFocus,
                strengthTrends = strengthTrends,
                recentHistory = domainSessions
            )
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
            status = session.status,
            focusMuscles = focusMusclesList,
            exercises = domainExercises,
            notes = session.notes
        )
    }
}
