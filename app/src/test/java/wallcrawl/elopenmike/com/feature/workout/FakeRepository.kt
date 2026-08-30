package wallcrawl.elopenmike.com.feature.workout

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetOutcomeRules
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

/**
 * In-memory stand-in for [WorkoutRepository] that applies set outcomes to its session
 * exactly as the real repository does, so ViewModel tests observe the same state
 * transitions the app does -- including the same rejections.
 */
internal class FakeRepository(initialSession: WorkoutSession) : WorkoutRepository {
    private val session = MutableStateFlow<WorkoutSession?>(initialSession)

    var completeCalls: Int = 0
        private set
    var cancelCalls: Int = 0
        private set
    var failSetUpdates: Boolean = false
    val persistedInputs = mutableListOf<SetPerformanceInput>()

    val lastPersistedWeight: Double? get() = persistedInputs.lastOrNull()?.weight
    val lastPersistedReps: Int? get() = persistedInputs.lastOrNull()?.reps

    override fun observeActiveSession(): Flow<WorkoutSession?> = session
    override suspend fun getActiveSessionOnce(): WorkoutSession? = session.value
    override suspend fun getSessionById(sessionId: String): WorkoutSession? = session.value
    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = session
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> =
        flowOf(emptyList())

    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(0)
    override fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int> = flowOf(0)

    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> = emptyList()

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun startWorkoutFromTemplate(
        template: WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun logSetCompletion(setId: String, performance: SetPerformanceInput) {
        if (failSetUpdates) error("Session is already complete")
        // Mirrors OfflineWorkoutRepository's guards so this fake rejects exactly what
        // production rejects: the typed outcome invariants plus the completed
        // weight-and-reps requirement of a positive load and positive reps.
        SetOutcomeRules.requireValidOutcome(performance)
        require(
            !performance.isCompleted ||
                ((performance.reps ?: 0) > 0 && (performance.weight ?: 0.0) > 0.0)
        ) {
            "A completed weight and repetition set must have a positive load."
        }
        persistedInputs += performance

        val current = session.value ?: return
        session.value = current.copy(
            exercises = current.exercises.map { exercise ->
                exercise.copy(
                    sets = exercise.sets.map { set ->
                        if (set.id != setId) {
                            set
                        } else {
                            set.copy(
                                completedReps = performance.reps,
                                completedWeight = performance.weight,
                                completedAssistanceWeight = performance.assistanceWeight,
                                completedDurationSeconds = performance.durationSeconds,
                                completedDistanceMeters = performance.distanceMeters,
                                rpe = performance.rpe,
                                rir = performance.rir,
                                feltManageable = performance.feltManageable,
                                completedAtTimestamp = performance.completedAtTimestamp,
                                stoppedAtTimestamp = performance.stoppedAtTimestamp,
                                stopReason = performance.stopReason,
                                isCompleted = performance.isCompleted
                            )
                        }
                    }
                )
            }
        )
    }

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): WorkoutSummary {
        completeCalls += 1
        val completed = requireNotNull(session.value).copy(
            status = SessionStatus.COMPLETED,
            completedAtTimestamp = 5_000L,
            actualDurationMinutes = actualDurationMinutes
        )
        session.value = completed
        return completed.toSummary()
    }

    override suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary? {
        val current = session.value ?: return null
        if (current.status != SessionStatus.COMPLETED) return null
        return current.toSummary()
    }

    override suspend fun cancelWorkout(sessionId: String) {
        cancelCalls += 1
    }

    private fun WorkoutSession.toSummary() = WorkoutSummary(
        sessionId = id,
        workoutName = name,
        durationMinutes = actualDurationMinutes,
        totalSetsCompleted = completedSetsCount,
        totalVolume = totalVolume,
        unit = weightUnit,
        completedAtTimestamp = completedAtTimestamp ?: startedAtTimestamp
    )
}
