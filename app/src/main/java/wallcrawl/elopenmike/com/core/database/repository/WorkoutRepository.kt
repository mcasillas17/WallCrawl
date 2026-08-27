package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.dao.WorkoutSessionDao
import wallcrawl.elopenmike.com.core.database.dao.WorkoutSetDao
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.model.WorkoutOrigin
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
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
    suspend fun startWorkoutFromTemplate(
        template: WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession
    suspend fun logSetCompletion(setId: String, reps: Int?, weight: Double?, isCompleted: Boolean)
    suspend fun logSetCompletion(setId: String, performance: SetPerformanceInput) {
        require(
            performance.assistanceWeight == null &&
                performance.durationSeconds == null &&
                performance.distanceMeters == null
        ) { "This repository only supports repetition and weight outcomes." }
        logSetCompletion(
            setId = setId,
            reps = performance.reps,
            weight = performance.weight,
            isCompleted = performance.isCompleted
        )
    }
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
    ): WorkoutSession = startWorkout(
        name = generated.name,
        notes = generated.rationale,
        focusMuscles = generated.focusMuscles,
        estimatedDurationMinutes = generated.estimatedDurationMinutes,
        exercises = generated.exercises,
        origin = WorkoutOrigin.PLANNER,
        sourceTemplateId = null,
        userProfile = userProfile
    )

    override suspend fun startWorkoutFromTemplate(
        template: WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = startWorkout(
        name = template.name,
        notes = template.notes,
        focusMuscles = emptyList(),
        estimatedDurationMinutes = estimateDurationMinutes(template.exercises),
        exercises = template.exercises,
        origin = WorkoutOrigin.CUSTOM_TEMPLATE,
        sourceTemplateId = template.id,
        userProfile = userProfile
    )

    private suspend fun startWorkout(
        name: String,
        notes: String,
        focusMuscles: List<String>,
        estimatedDurationMinutes: Int,
        exercises: List<PlannedExercise>,
        origin: WorkoutOrigin,
        sourceTemplateId: String?,
        userProfile: UserProfile
    ): WorkoutSession {
        val sessionId = UUID.randomUUID().toString()
        val sessionEntity = WorkoutSessionEntity(
            id = sessionId,
            name = name,
            startedAtTimestamp = System.currentTimeMillis(),
            completedAtTimestamp = null,
            targetDurationMinutes = estimatedDurationMinutes,
            actualDurationMinutes = 0,
            weightUnit = userProfile.preferredUnit,
            status = SessionStatus.IN_PROGRESS,
            origin = origin,
            sourceTemplateId = sourceTemplateId,
            focusMusclesJson = focusMuscles.joinToString("|||"),
            notes = notes
        )
        val exerciseEntities = mutableListOf<WorkoutExerciseEntity>()
        val setEntities = mutableListOf<WorkoutSetEntity>()

        exercises.forEachIndexed { exIndex, genEx ->
            val exerciseInstanceId = UUID.randomUUID().toString()
            exerciseEntities.add(
                WorkoutExerciseEntity(
                    id = exerciseInstanceId,
                    sessionId = sessionId,
                    exerciseId = genEx.exerciseId,
                    orderIndex = exIndex,
                    exerciseType = genEx.prescription.exerciseType,
                    targetSets = genEx.targetSets,
                    targetRepMin = genEx.prescription.repRange?.min,
                    targetRepMax = genEx.prescription.repRange?.max,
                    targetWeight = genEx.targetWeight,
                    targetAssistanceWeight = genEx.prescription.targetAssistanceWeight,
                    targetDurationSeconds = genEx.prescription.targetDurationSeconds,
                    targetDistanceMeters = genEx.prescription.targetDistanceMeters,
                    restSeconds = genEx.prescription.restSeconds,
                    notes = genEx.notes
                )
            )

            for (setNum in 1..genEx.targetSets) {
                setEntities.add(
                    WorkoutSetEntity(
                        id = UUID.randomUUID().toString(),
                        workoutExerciseId = exerciseInstanceId,
                        setNumber = setNum,
                        exerciseType = genEx.prescription.exerciseType,
                        targetReps = genEx.prescription.repRange?.max,
                        completedReps = null,
                        targetWeight = genEx.targetWeight,
                        completedWeight = null,
                        targetAssistanceWeight = genEx.prescription.targetAssistanceWeight,
                        completedAssistanceWeight = null,
                        targetDurationSeconds = genEx.prescription.targetDurationSeconds,
                        completedDurationSeconds = null,
                        targetDistanceMeters = genEx.prescription.targetDistanceMeters,
                        completedDistanceMeters = null,
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
    ) = logSetCompletion(
        setId = setId,
        performance = SetPerformanceInput(
            reps = reps,
            weight = weight,
            isCompleted = isCompleted
        )
    )

    override suspend fun logSetCompletion(
        setId: String,
        performance: SetPerformanceInput
    ) {
        require(setId.isNotBlank()) { "setId must not be blank." }
        require(performance.reps == null || performance.reps in 0..MAX_LOGGED_REPS) {
            "reps must be between zero and $MAX_LOGGED_REPS."
        }
        requireValidWeight(performance.weight, "weight")
        requireValidWeight(performance.assistanceWeight, "assistanceWeight")
        require(
            performance.durationSeconds == null ||
                performance.durationSeconds in 0..MAX_LOGGED_DURATION_SECONDS
        ) {
            "durationSeconds must be between zero and $MAX_LOGGED_DURATION_SECONDS."
        }
        require(
            performance.distanceMeters == null ||
                performance.distanceMeters.isFinite() &&
                performance.distanceMeters in 0.0..MAX_LOGGED_DISTANCE_METERS
        ) {
            "distanceMeters must be finite and between zero and $MAX_LOGGED_DISTANCE_METERS."
        }

        val persistedSet = checkNotNull(setDao.getSetById(setId)) {
            "Workout set '$setId' was not found."
        }
        validatePerformanceForType(persistedSet.exerciseType, performance)

        val affectedRows = setDao.updateSetCompletion(
            setId = setId,
            reps = performance.reps,
            weight = performance.weight,
            assistanceWeight = performance.assistanceWeight,
            durationSeconds = performance.durationSeconds,
            distanceMeters = performance.distanceMeters,
            isCompleted = performance.isCompleted
        )
        check(affectedRows == 1) {
            "Workout set '$setId' was not found or its session is not in progress."
        }
    }

    private fun requireValidWeight(value: Double?, label: String) {
        require(value == null || (value.isFinite() && value in 0.0..MAX_LOGGED_WEIGHT)) {
            "$label must be finite and between zero and $MAX_LOGGED_WEIGHT."
        }
    }

    private fun validatePerformanceForType(
        exerciseType: ExerciseType,
        performance: SetPerformanceInput
    ) {
        when (exerciseType) {
            ExerciseType.WEIGHT_REPS -> {
                require(performance.assistanceWeight == null) {
                    "Weight and repetition sets cannot record assistance weight."
                }
                requireNoDurationOrDistance(performance, "Weight and repetition")
                requirePositiveRepetitionsWhenCompleted(performance)
            }

            ExerciseType.BODYWEIGHT_REPS -> {
                require(performance.weight == null && performance.assistanceWeight == null) {
                    "Bodyweight sets cannot record load or assistance weight."
                }
                requireNoDurationOrDistance(performance, "Bodyweight")
                requirePositiveRepetitionsWhenCompleted(performance)
            }

            ExerciseType.ASSISTED_BODYWEIGHT -> {
                require(performance.weight == null) {
                    "Assisted bodyweight sets use assistance weight instead of load."
                }
                requireNoDurationOrDistance(performance, "Assisted bodyweight")
                requirePositiveRepetitionsWhenCompleted(performance)
            }

            ExerciseType.DURATION -> {
                require(
                    performance.reps == null &&
                        performance.weight == null &&
                        performance.assistanceWeight == null &&
                        performance.distanceMeters == null
                ) { "Duration sets can only record duration." }
                require(!performance.isCompleted || (performance.durationSeconds ?: 0) > 0) {
                    "A completed duration set must have positive durationSeconds."
                }
            }

            ExerciseType.DISTANCE_DURATION -> {
                require(
                    performance.reps == null &&
                        performance.weight == null &&
                        performance.assistanceWeight == null
                ) { "Distance and duration sets cannot record repetitions or load." }
                require(
                    !performance.isCompleted ||
                        (performance.durationSeconds ?: 0) > 0 ||
                        (performance.distanceMeters ?: 0.0) > 0.0
                ) {
                    "A completed distance and duration set needs positive distance or duration."
                }
            }
        }
    }

    private fun requireNoDurationOrDistance(
        performance: SetPerformanceInput,
        label: String
    ) {
        require(performance.durationSeconds == null && performance.distanceMeters == null) {
            "$label sets cannot record duration or distance."
        }
    }

    private fun requirePositiveRepetitionsWhenCompleted(performance: SetPerformanceInput) {
        require(!performance.isCompleted || (performance.reps ?: 0) > 0) {
            "A completed repetition set must have positive reps."
        }
    }

    private fun estimateDurationMinutes(exercises: List<PlannedExercise>): Int {
        val totalSeconds = exercises.sumOf { exercise ->
            val prescription = exercise.prescription
            val secondsPerSet = prescription.targetDurationSeconds
                ?: if (prescription.targetDistanceMeters != null) 600 else 45
            val executionSeconds = prescription.targetSets * secondsPerSet
            val restSeconds = (prescription.targetSets - 1).coerceAtLeast(0) *
                prescription.restSeconds
            executionSeconds + restSeconds
        }
        return ((totalSeconds + 59) / 60).coerceIn(1, 240)
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
                            exerciseType = setEntity.exerciseType,
                            targetReps = setEntity.targetReps,
                            completedReps = setEntity.completedReps,
                            targetWeight = setEntity.targetWeight,
                            completedWeight = setEntity.completedWeight,
                            targetAssistanceWeight = setEntity.targetAssistanceWeight,
                            completedAssistanceWeight = setEntity.completedAssistanceWeight,
                            targetDurationSeconds = setEntity.targetDurationSeconds,
                            completedDurationSeconds = setEntity.completedDurationSeconds,
                            targetDistanceMeters = setEntity.targetDistanceMeters,
                            completedDistanceMeters = setEntity.completedDistanceMeters,
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
                    prescription = ExercisePrescription(
                        exerciseType = exWithSets.exercise.exerciseType,
                        targetSets = exWithSets.exercise.targetSets,
                        repRange = exWithSets.exercise.targetRepMin?.let { minimum ->
                            RepRange(
                                min = minimum,
                                max = checkNotNull(exWithSets.exercise.targetRepMax) {
                                    "Persisted repetition target is missing its maximum."
                                }
                            )
                        },
                        targetWeight = exWithSets.exercise.targetWeight,
                        targetAssistanceWeight = exWithSets.exercise.targetAssistanceWeight,
                        targetDurationSeconds = exWithSets.exercise.targetDurationSeconds,
                        targetDistanceMeters = exWithSets.exercise.targetDistanceMeters,
                        restSeconds = exWithSets.exercise.restSeconds
                    ),
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
            origin = session.origin,
            sourceTemplateId = session.sourceTemplateId,
            focusMuscles = focusMusclesList,
            exercises = domainExercises,
            notes = session.notes
        )
    }

    private companion object {
        const val MAX_LOGGED_REPS = 1_000
        const val MAX_LOGGED_WEIGHT = 100_000.0
        const val MAX_LOGGED_DURATION_SECONDS = 86_400
        const val MAX_LOGGED_DISTANCE_METERS = 1_000_000.0
    }
}

const val DEFAULT_OBSERVED_COMPLETED_SESSIONS = 500
