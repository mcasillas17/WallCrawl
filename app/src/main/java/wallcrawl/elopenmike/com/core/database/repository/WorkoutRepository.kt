package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.dao.WorkoutSessionDao
import wallcrawl.elopenmike.com.core.database.dao.WorkoutSetDao
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutSession
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetOutcomeRules
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.model.WorkoutOrigin
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator
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
    /**
     * Persists one set's complete typed outcome: the type-specific performance values,
     * optional RPE/RIR, the optional manageable confirmation, the outcome timestamp, and
     * any typed stop reason. This is the only way a set outcome is written, so validation
     * and the active-session guard cannot be bypassed.
     */
    suspend fun logSetCompletion(setId: String, performance: SetPerformanceInput)
    suspend fun completeWorkout(sessionId: String, actualDurationMinutes: Int): WorkoutSummary

    /**
     * Summary of an already completed session, including its personal-record count.
     * Shares one implementation and one history window with [completeWorkout] so the
     * number cannot differ between finishing a workout and revisiting it.
     */
    suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary?

    suspend fun cancelWorkout(sessionId: String)
}

class OfflineWorkoutRepository(
    private val sessionDao: WorkoutSessionDao,
    private val setDao: WorkoutSetDao,
    private val progressCalculator: ProgressCalculator = ProgressCalculator()
) : WorkoutRepository {

    override fun observeActiveSession(): Flow<WorkoutSession?> {
        return sessionDao.observeActiveSession().map { it?.toWorkoutSession() }
    }

    override suspend fun getActiveSessionOnce(): WorkoutSession? {
        return sessionDao.getActiveSession()?.toWorkoutSession()
    }

    override suspend fun getSessionById(sessionId: String): WorkoutSession? {
        return sessionDao.getSessionWithDetails(sessionId)?.toWorkoutSession()
    }

    override fun observeSession(sessionId: String): Flow<WorkoutSession?> {
        return sessionDao.observeSessionWithDetails(sessionId).map { it?.toWorkoutSession() }
    }

    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> {
        require(limit > 0) { "limit must be greater than zero." }
        return sessionDao.observeRecentCompletedSessions(limit).map { list ->
            list.map { it.toWorkoutSession() }
        }
    }

    override fun observeCompletedWorkoutCount(): Flow<Int> =
        sessionDao.observeCompletedSessionCount()

    override fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int> =
        sessionDao.observeCompletedSessionCountSince(startTimestamp)

    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> {
        require(limit > 0) { "limit must be greater than zero." }
        return sessionDao.getRecentCompletedSessions(limit).map { it.toWorkoutSession() }
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
        ).toWorkoutSession()
    }

    override suspend fun logSetCompletion(
        setId: String,
        performance: SetPerformanceInput
    ) {
        require(setId.isNotBlank()) { "setId must not be blank." }
        SetOutcomeRules.requireValidOutcome(performance)
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

        // One statement writes performance, feedback, timestamps, stop reason, and
        // completion together, so clearing completion also clears completion-only
        // feedback and no intermediate contradictory row can ever be observed.
        val affectedRows = setDao.updateSetOutcome(
            setId = setId,
            reps = performance.reps,
            weight = performance.weight,
            assistanceWeight = performance.assistanceWeight,
            durationSeconds = performance.durationSeconds,
            distanceMeters = performance.distanceMeters,
            rpe = performance.rpe,
            rir = performance.rir,
            feltManageable = performance.feltManageable,
            completedAtTimestamp = performance.completedAtTimestamp,
            stoppedAtTimestamp = performance.stoppedAtTimestamp,
            stopReason = performance.stopReason,
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
                // With no fabricated default weight (Task 2), a set could otherwise be
                // marked complete while silently recording no load at all.
                require(!performance.isCompleted || (performance.weight ?: 0.0) > 0.0) {
                    "A completed weight and repetition set must have a positive load."
                }
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
        return session.toSummary(durationMinutes = actualDurationMinutes)
    }

    override suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary? {
        require(sessionId.isNotBlank()) { "sessionId must not be blank." }
        val session = getSessionById(sessionId) ?: return null
        if (session.status != SessionStatus.COMPLETED) return null
        return session.toSummary(durationMinutes = session.actualDurationMinutes)
    }

    private suspend fun WorkoutSession.toSummary(durationMinutes: Int): WorkoutSummary =
        WorkoutSummary(
            sessionId = id,
            workoutName = name,
            durationMinutes = durationMinutes,
            totalSetsCompleted = completedSetsCount,
            totalVolume = totalVolume,
            prCount = progressCalculator.countPersonalRecords(
                session = this,
                priorCompletedSessions = getRecentCompletedSessions(
                    limit = PERSONAL_RECORD_HISTORY_SESSIONS
                )
            ),
            unit = weightUnit,
            completedAtTimestamp = completedAtTimestamp ?: startedAtTimestamp
        )

    override suspend fun cancelWorkout(sessionId: String) {
        require(sessionId.isNotBlank()) { "sessionId must not be blank." }
        check(sessionDao.deleteActiveSession(sessionId) == 1) {
            "Workout session '$sessionId' was not found or is not in progress."
        }
    }

    private companion object {
        const val MAX_LOGGED_REPS = 1_000
        const val MAX_LOGGED_WEIGHT = 100_000.0
        const val MAX_LOGGED_DURATION_SECONDS = 86_400
        const val MAX_LOGGED_DISTANCE_METERS = 1_000_000.0
        const val PERSONAL_RECORD_HISTORY_SESSIONS = 200
    }
}

const val DEFAULT_OBSERVED_COMPLETED_SESSIONS = 500
