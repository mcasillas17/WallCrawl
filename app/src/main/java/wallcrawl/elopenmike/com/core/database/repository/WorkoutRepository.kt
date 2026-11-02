package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.ai.*
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import wallcrawl.elopenmike.com.core.ai.RecommendationSnapshot
import wallcrawl.elopenmike.com.core.ai.TrainingFrequencyRecencyPolicy
import wallcrawl.elopenmike.com.core.ai.asRecord
import wallcrawl.elopenmike.com.core.database.PERSISTED_LIST_SEPARATOR
import wallcrawl.elopenmike.com.core.database.dao.WorkoutSessionDao
import wallcrawl.elopenmike.com.core.database.dao.WorkoutSetDao
import wallcrawl.elopenmike.com.core.database.entity.toModel
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutSession
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.RecommendationRecord
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits
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
import wallcrawl.elopenmike.com.core.model.SessionConditionContext
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import java.util.UUID
import java.time.ZoneId
import wallcrawl.elopenmike.com.core.model.ConditionSupportCatalog
import wallcrawl.elopenmike.com.core.database.dao.ConditionHistoryDao
import wallcrawl.elopenmike.com.core.database.dao.ConditionContextRead

interface WorkoutRepository {
    fun observePendingConditionReassessments(profileId: String): Flow<List<PendingConditionReassessment>> =
        flow { throw UnsupportedOperationException("Pending condition observation is unavailable.") }

    /** Bounded ownership probe, independent of current selections or optional provenance. */
    suspend fun hasPendingConditionReassessment(profileId: String): Boolean
    suspend fun getConditionContext(profileId: String, support: ConditionSupportCatalog,
        asOf: Long, zone: ZoneId, programMode: Boolean): ConditionContextRead =
        throw UnsupportedOperationException("Coherent condition history is unavailable.")
    fun observeActiveSession(): Flow<WorkoutSession?>
    suspend fun getActiveSessionOnce(): WorkoutSession?
    suspend fun getSessionById(sessionId: String): WorkoutSession?
    fun observeSession(sessionId: String): Flow<WorkoutSession?>
    fun observeCompletedSessions(limit: Int = DEFAULT_OBSERVED_COMPLETED_SESSIONS): Flow<List<WorkoutSession>>
    fun observeCompletedWorkoutCount(): Flow<Int>
    fun observeCompletedWorkoutCountInRange(startTimestamp: Long, endTimestampExclusive: Long): Flow<Int>
    suspend fun getRecentCompletedSessions(limit: Int = 8): List<WorkoutSession>
    /** Latest attempts of every status; an incomplete attempt must not be skipped. */
    suspend fun getRecentSessions(limit: Int = 8): List<WorkoutSession>
    suspend fun getRecommendationRecords(sessionIds: List<String>): List<RecommendationRecord>
    /** Complete canonical range, never the recent-eight loading/capability sample. */
    suspend fun getCompletedSessionsInRange(startTimestamp: Long, endTimestampExclusive: Long): List<WorkoutSession>
    /**
     * Ascending MAX_SESSIONS + 1 probe, not an unrestricted complete range.
     * The consumer filters its sampled inclusive now, then rejects overflow. Keeping the
     * first future sentinel allows a clock tick to detect overflow without another query.
     */
    fun observeCompletedSessionProbeInRange(startTimestamp: Long, endTimestampExclusive: Long): Flow<List<WorkoutSession>> =
        flow {
            emit(getCompletedSessionsInRange(startTimestamp, endTimestampExclusive)
                .sortedWith(compareBy<WorkoutSession> { it.completedAtTimestamp }.thenBy { it.id })
                .take(TrainingFrequencyRecencyPolicy.MAX_SESSIONS + 1))
        }
    /**
     * Starts a session from a generated plan.
     *
     * The screen supplies [displayName] and [displayRationale] already written in the
     * language it is rendering, because [GeneratedWorkout] carries a structured title and
     * explanation rather than a sentence. What is stored is that text, exactly as the user
     * saw it: a completed session keeps the wording it was created with, and no later
     * language change rewrites recorded history.
     *
     * [recommendation] is the whole-program validation evidence for this plan. It is written
     * inside the same transaction as the session, so a start either records both or records
     * nothing. It is null only for a caller that did not validate — every production path
     * supplies one.
     */
    suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        displayName: String,
        displayRationale: String,
        userProfile: UserProfile,
        recommendation: RecommendationSnapshot? = null
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
     * Shares one calculation and strictly-prior baseline with [completeWorkout] so the
     * number cannot differ between finishing a workout and revisiting it.
     */
    suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary?

    suspend fun cancelWorkout(sessionId: String)
}

class OfflineWorkoutRepository(
    private val sessionDao: WorkoutSessionDao,
    private val setDao: WorkoutSetDao,
    private val progressCalculator: ProgressCalculator = ProgressCalculator(),
    private val localDataWriteGate: Mutex = Mutex(),
    private val conditionSupport: (suspend () -> ConditionSupportCatalog)? = null,
    private val exerciseCatalog: ExerciseCatalog? = null,
    private val generationContext: (suspend (WorkoutPlanningIntent, Int?) -> WorkoutGenerationContext)? = null,
    private val validateGenerated: (suspend (GeneratedWorkout, WorkoutGenerationContext) -> ProgramValidationResult)? = null,
    private val nowTimestamp: () -> Long = System::currentTimeMillis,
    private val currentZone: () -> ZoneId = ZoneId::systemDefault,
    // Trusted composition only: must rebuild all inputs without reacquiring localDataWriteGate.
    private val generationContextInTransaction: (suspend (WorkoutPlanningIntent, Int?, ConditionContextRead, ConditionSupportCatalog) -> WorkoutGenerationContext)? = null
) : WorkoutRepository {
    override fun observePendingConditionReassessments(profileId: String): Flow<List<PendingConditionReassessment>> =
        sessionDao.observeConditionPendingProbe(profileId, ConditionHistoryDao.MAX_CONDITION_MARKERS + 1).map { rows ->
            check(rows.size <= ConditionHistoryDao.MAX_CONDITION_MARKERS) { "Pending condition observation exceeds its bound." }
            rows.map { it.toModel() }
        }

    override suspend fun hasPendingConditionReassessment(profileId: String) = localDataWriteGate.withLock {
        sessionDao.conditionPendingProbe(profileId, 1).isNotEmpty()
    }

    override suspend fun getConditionContext(profileId: String, support: ConditionSupportCatalog,
        asOf: Long, zone: ZoneId, programMode: Boolean) = localDataWriteGate.withLock { sessionDao.readConditionContext(profileId, support, asOf, zone, programMode) }

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

    override fun observeCompletedWorkoutCountInRange(
        startTimestamp: Long,
        endTimestampExclusive: Long
    ): Flow<Int> {
        require(endTimestampExclusive > startTimestamp) { "The completed-workout range must be nonempty." }
        return sessionDao.observeCompletedSessionCountInRange(startTimestamp, endTimestampExclusive)
    }

    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> {
        require(limit > 0) { "limit must be greater than zero." }
        return sessionDao.getRecentCompletedSessions(limit).map { it.toWorkoutSession() }
    }

    override suspend fun getRecentSessions(limit: Int): List<WorkoutSession> {
        require(limit in 1..8) { "Recent attempt history is bounded to eight sessions." }
        return sessionDao.getRecentSessions(limit).map { row ->
            check(row.exercisesWithSets.size <= LocalDataArchiveLimits.MAX_EXERCISES_PER_SESSION &&
                row.exercisesWithSets.all { it.sets.size <= LocalDataArchiveLimits.MAX_SETS_PER_EXERCISE }) {
                "Recent attempt history exceeds supported per-session bounds."
            }
            row.toWorkoutSession()
        }
    }

    override suspend fun getRecommendationRecords(sessionIds: List<String>): List<RecommendationRecord> {
        require(sessionIds.size <= 8 && sessionIds.distinct().size == sessionIds.size &&
            sessionIds.all { it.isNotBlank() && it.length <= 200 && it.none(Char::isISOControl) }) {
            "Recommendation history requires at most eight distinct bounded session identifiers."
        }
        if (sessionIds.isEmpty()) return emptyList()
        return sessionDao.getRecommendationRecords(sessionIds).map { it.toRecommendationRecord() }
    }

    override suspend fun getCompletedSessionsInRange(
        startTimestamp: Long,
        endTimestampExclusive: Long
    ): List<WorkoutSession> = sessionDao.getCompletedSessionsInRange(
        startTimestamp, endTimestampExclusive,
        maximumSessions = TrainingFrequencyRecencyPolicy.MAX_SESSIONS
    ).map { it.toWorkoutSession() }

    override fun observeCompletedSessionProbeInRange(
        startTimestamp: Long,
        endTimestampExclusive: Long
    ): Flow<List<WorkoutSession>> {
        require(endTimestampExclusive > startTimestamp) { "The history range must be nonempty." }
        val bound = TrainingFrequencyRecencyPolicy.MAX_SESSIONS
        return sessionDao.observeCompletedSessionsInRange(startTimestamp, endTimestampExclusive, bound + 1)
            .map { rows -> rows.map { it.toWorkoutSession() } }
    }

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        displayName: String,
        displayRationale: String,
        userProfile: UserProfile,
        recommendation: RecommendationSnapshot?
    ): WorkoutSession = startWorkout(
        name = displayName,
        notes = displayRationale,
        focusMuscles = generated.focusMuscles,
        estimatedDurationMinutes = generated.estimatedDurationMinutes,
        exercises = generated.exercises,
        origin = WorkoutOrigin.PLANNER,
        sourceTemplateId = null,
        userProfile = userProfile,
        recommendation = recommendation,
        generated = generated
    )

    override suspend fun startWorkoutFromTemplate(
        template: WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession {
        // A workout already in progress is resumed rather than competed with, as for any
        // template, and resuming copies nothing from this one, so an unconfirmed unit is no
        // obstacle to it. Any other start still goes through the atomic insert below.
        if (template.requiresUnitConfirmation) getActiveSessionOnce()?.let { return it }
        return startWorkout(
            name = template.name,
            notes = template.notes,
            focusMuscles = emptyList(),
            estimatedDurationMinutes = estimateDurationMinutes(template.exercises),
            // The session is recorded in the profile's unit, so a template saved in the other
            // one is converted rather than relabelled; an unconfirmed legacy unit is refused.
            exercises = template.exercisesIn(userProfile.preferredUnit),
            origin = WorkoutOrigin.CUSTOM_TEMPLATE,
            sourceTemplateId = template.id,
            userProfile = userProfile,
            // A manual template is an explicit user choice, not an automatic recommendation, so
            // there is no automatic recommendation receipt to record for it.
            recommendation = null
        )
    }

    private suspend fun startWorkout(
        name: String,
        notes: String,
        focusMuscles: List<String>,
        estimatedDurationMinutes: Int,
        exercises: List<PlannedExercise>,
        origin: WorkoutOrigin,
        sourceTemplateId: String?,
        userProfile: UserProfile,
        recommendation: RecommendationSnapshot?,
        generated: GeneratedWorkout? = null
    ): WorkoutSession {
        getActiveSessionOnce()?.let { return it }
        val pending = localDataWriteGate.withLock { sessionDao.conditionPendingProbe(userProfile.id, 1) }
        if (pending.isNotEmpty()) throw ConditionStartRefused(pending.map {
            ConditionViolation("pending-reassessment", setOf(it.conditionId), setOf(it.exerciseId))
        })
        val needsConditions = userProfile.activeConditions.isNotEmpty() || recommendation?.conditionContext != null ||
            exercises.any { it.prescription.purpose != PrescriptionPurpose.ORDINARY }
        if (generated != null && needsConditions) {
            if (recommendation?.conditionContext == null) throw ConditionStartRefused("missing-condition-context")
            val build = generationContext ?: throw ConditionStartRefused("condition-validation-unavailable")
            val rebuilt = try {
                build(if (recommendation.programSession == null) WorkoutPlanningIntent.ORDINARY else WorkoutPlanningIntent.PROGRAM,
                    generated.generationIndex)
            } catch (error: CancellationException) { throw error
            } catch (error: ConditionStartRefused) { throw error
            } catch (error: Exception) {
                throw ConditionStartRefused("condition-context-unavailable").also { it.initCause(error) }
            }
            validateCurrentGenerated(generated, recommendation, rebuilt)
        }
        val sessionId = UUID.randomUUID().toString()
        val sessionEntity = WorkoutSessionEntity(
            id = sessionId,
            name = name,
            startedAtTimestamp = nowTimestamp(),
            completedAtTimestamp = null,
            targetDurationMinutes = estimatedDurationMinutes,
            actualDurationMinutes = 0,
            weightUnit = userProfile.preferredUnit,
            status = SessionStatus.IN_PROGRESS,
            origin = origin,
            sourceTemplateId = sourceTemplateId,
            focusMusclesJson = focusMuscles.joinToString(PERSISTED_LIST_SEPARATOR),
            notes = notes,
            conditionContextJson = recommendation?.conditionContext?.let(ConditionContextCodec::encodeSession)
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
                    notes = genEx.notes,
                    effortMinRir = genEx.prescription.effortTarget?.minRir,
                    effortMaxRir = genEx.prescription.effortTarget?.maxRir,
                    restClass = genEx.prescription.restClass,
                    restTargetSource = genEx.prescription.restTargetSource,
                    purpose = genEx.prescription.purpose.name
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

        return localDataWriteGate.withLock { sessionDao.insertWorkoutUnlessActive(
            session = sessionEntity,
            exercises = exerciseEntities,
            sets = setEntities,
            expectedProfileId = userProfile.id,
            expectedProfileRevision = userProfile.revision,
            recommendation = recommendation
                ?.asRecord(
                    sessionId = sessionId,
                    recordedAtEpochMillis = sessionEntity.startedAtTimestamp
                )
                ?.toEntity(),
            expectedDeloadDecisionRevision = recommendation?.deloadDecisionRevision,
            acceptedDeloadOfferId = recommendation?.acceptedDeloadOfferId,
            expectedProgramHeader = recommendation?.expectedProgramHeader,
            programSession = recommendation?.programSession,
            conditionCheck = if (needsConditions) {
                {
                    val support = try { conditionSupport?.invoke() ?: throw ConditionStartRefused("missing-support")
                    } catch (error: CancellationException) { throw error
                    } catch (error: ConditionStartRefused) { throw error
                    } catch (error: Exception) { throw ConditionStartRefused("support-unavailable").also { it.initCause(error) } }
                    val catalog = exerciseCatalog?.getAllExercises()?.first() ?: throw ConditionStartRefused("missing-exercise")
                    val read = readConditionContext(userProfile.id, support, nowTimestamp(), currentZone(), recommendation?.programSession != null)
                    val catalogById = catalog.associateBy { it.id }
                    val planning = ConditionPlanningPolicy().resolve(read.profile, catalog, catalogById, support, read.history)
                    when (planning) {
                        is ConditionPlanningResult.Blocked -> throw ConditionStartRefused(planning.reasons)
                        ConditionPlanningResult.NotApplicable -> throw ConditionStartRefused("missing-condition-context", exerciseIds = exercises.map { it.exerciseId }.toSet())
                        is ConditionPlanningResult.Ready -> {
                            if (generated != null) {
                                val rebuild = generationContextInTransaction ?: throw ConditionStartRefused("condition-validation-unavailable")
                                val checked = try {
                                    rebuild(if (recommendation?.programSession == null) WorkoutPlanningIntent.ORDINARY else WorkoutPlanningIntent.PROGRAM,
                                        generated.generationIndex, read, support)
                                } catch (error: CancellationException) { throw error
                                } catch (error: ConditionStartRefused) { throw error
                                } catch (error: Exception) {
                                    throw ConditionStartRefused("condition-context-unavailable").also { it.initCause(error) }
                                }
                                validateCurrentGenerated(generated, requireNotNull(recommendation), checked)
                                // Only the original receipt's exact checked times may be frozen on this session.
                                recommendation.conditionContext
                            } else {
                                val violations = exercises.flatMap { ConditionPlanningPolicy().validate(it, planning) }.toMutableList()
                                planning.supportive.forEach { required ->
                                    if (exercises.count { it.exerciseId == required.exerciseId && it.prescription.purpose == PrescriptionPurpose.SUPPORTIVE } != 1)
                                        violations += ConditionViolation("missing-required-support", read.profile.activeConditions.map { it.conditionId }.toSet(), setOf(required.exerciseId))
                                }
                                if (exercises.map { it.exerciseId }.distinct().size != exercises.size)
                                    violations += ConditionViolation("duplicate-exercise", emptySet(), exercises.map { it.exerciseId }.toSet())
                                if (violations.isNotEmpty()) throw ConditionStartRefused(violations)
                                planning.context
                            }
                        }
                    }
                }
            } else null
        ).toWorkoutSession() }
    }

    private suspend fun validateCurrentGenerated(generated: GeneratedWorkout, recommendation: RecommendationSnapshot,
        context: WorkoutGenerationContext) {
        val planning = context.conditionPlanning
        if (planning is ConditionPlanningResult.Blocked) throw ConditionStartRefused(planning.reasons)
        if (recommendation.profileRevision != context.userProfile.revision ||
            RecommendationContextIdentity.of(context) != recommendation.contextIdentity)
            throw ConditionStartRefused("changed-condition-facts", context.userProfile.activeConditions.map { it.conditionId }.toSet())
        val frozen = recommendation.conditionContext ?: throw ConditionStartRefused("missing-condition-context")
        val current = (planning as? ConditionPlanningResult.Ready)?.context ?: throw ConditionStartRefused("missing-condition-context")
        if (frozen.formatVersion != current.formatVersion || frozen.policyVersion != current.policyVersion ||
            frozen.selectedConditions != current.selectedConditions || frozen.sourceSupportVersions != current.sourceSupportVersions ||
            frozen.appliedRules != current.appliedRules || frozen.consumedReassessmentEventIds != current.consumedReassessmentEventIds ||
            frozen.consumedScheduleEvidence.map { it.reference to it.previousPerformedAtTimestamp } !=
                current.consumedScheduleEvidence.map { it.reference to it.previousPerformedAtTimestamp })
            throw ConditionStartRefused("changed-condition-facts")
        val validate = validateGenerated ?: throw ConditionStartRefused("condition-validation-unavailable")
        val result = validate(generated, context)
        if (result is ProgramValidationResult.Invalid) throw ConditionStartRefused(result.violations.map {
            ConditionViolation(it.detail?.takeIf { detail -> detail.startsWith("CONDITION:") }?.removePrefix("CONDITION:") ?: "invalid-workout",
                context.userProfile.activeConditions.map { selection -> selection.conditionId }.toSet(), setOfNotNull(it.exerciseId))
        })
    }

    override suspend fun logSetCompletion(
        setId: String,
        performance: SetPerformanceInput
    ) {
        require(setId.isNotBlank()) { "setId must not be blank." }
        val affectedRows = localDataWriteGate.withLock { setDao.recordOutcomeAndConditionStops(setId, performance) }
        check(affectedRows == 1) {
            "Workout set '$setId' was not found or its session is not in progress."
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
        return localDataWriteGate.withLock { sessionDao.completeWorkoutAndReadSummary(
            sessionId = sessionId,
            completedAt = System.currentTimeMillis(),
            actualDuration = actualDurationMinutes,
            calculator = progressCalculator
        ) }
    }

    override suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary? {
        require(sessionId.isNotBlank()) { "sessionId must not be blank." }
        return sessionDao.readWorkoutSummary(sessionId, progressCalculator)
    }

    override suspend fun cancelWorkout(sessionId: String) {
        require(sessionId.isNotBlank()) { "sessionId must not be blank." }
        if (localDataWriteGate.withLock { sessionDao.deleteActiveSession(sessionId) } != 1) {
            throw wallcrawl.elopenmike.com.core.model.ProgramException(wallcrawl.elopenmike.com.core.model.ProgramFailure.MISSING_OR_NOT_ACTIVE)
        }
    }

}

const val DEFAULT_OBSERVED_COMPLETED_SESSIONS = 500

internal fun validateSetPerformance(performance: SetPerformanceInput) {
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

}
private fun requireValidWeight(value: Double?, label: String) {
    require(value == null || (value.isFinite() && value in 0.0..MAX_LOGGED_WEIGHT)) {
        "$label must be finite and between zero and $MAX_LOGGED_WEIGHT."
    }
}

internal fun validatePerformanceForType(
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


private const val MAX_LOGGED_REPS = 1_000
private const val MAX_LOGGED_WEIGHT = 100_000.0
private const val MAX_LOGGED_DURATION_SECONDS = 86_400
private const val MAX_LOGGED_DISTANCE_METERS = 1_000_000.0
