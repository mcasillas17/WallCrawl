package wallcrawl.elopenmike.com.core.ai

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.DeloadRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.TrainingFrequencyRecencyEvidence
import wallcrawl.elopenmike.com.core.model.UserRestPreference
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

/**
 * Builds the intentionally bounded, structured context supplied to a workout planner.
 * Database and catalog details stop at this boundary so planner implementations stay replaceable.
 */
class WorkoutGenerationContextBuilder(
    private val userProfileRepository: UserProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val exerciseFilter: ExerciseFilter,
    private val historyAnalyzer: WorkoutHistoryAnalyzer,
    private val plannerFeatureFlags: PlannerFeatureFlags = PlannerFeatureFlags(),
    private val reviewedEligibilityPolicy: ExerciseEligibilityPolicy = ExerciseEligibilityPolicy(),
    private val capabilityEvidencePolicy: CapabilityEvidencePolicy = CapabilityEvidencePolicy(),
    private val adaptationStatePolicy: AdaptationStatePolicy = AdaptationStatePolicy(),
    private val trainingProgramStateProvider: TrainingProgramStateProvider? = null,
    /**
     * Commit of the bundled catalog, when a snapshot has already been loaded.
     *
     * Supplied rather than read here so this builder keeps depending on the catalog
     * interface instead of the asset store behind it, and so a test controls the recorded
     * identity. An unavailable snapshot records absence; it never blocks generation.
     */
    private val catalogVersion: () -> String? = { null },
    private val nowTimestamp: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    private val deloadRepository: DeloadRepository? = null
) {
    /**
     * Reuses Today’s clock and Room invalidation, not polling queries. A day/zone change
     * switches the bounded range. Within a date, only changed rows or a future completion
     * becoming nonfuture can alter evidence. Unchanged minute ticks do no reconstruction.
     * Each Room read also remains a freshness signal when the practice dates are equal:
     * other consumed history projections may have changed, even outside this date range.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSchedulingEvidence(clock: Flow<Long>): Flow<TrainingFrequencyRecencyEvidence> {
        if (!plannerFeatureFlags.reviewedCapabilityEligibility) return emptyFlow()
        val policy = TrainingFrequencyRecencyPolicy()
        return clock.map { timestamp ->
            val zone = zoneId()
            Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate() to zone
        }.distinctUntilChanged().flatMapLatest { (date, zone) ->
            combine(
                workoutRepository.observeCompletedSessionProbeInRange(
                    date.minusDays(TrainingFrequencyRecencyEvidence.LOOKBACK_DATES - 1)
                        .atStartOfDay(zone).toInstant().toEpochMilli(),
                    date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                ).withIndex(),
                exerciseCatalog.getAllExercises(),
                clock
            ) { read, exercises, timestamp ->
                val completedByNow = read.value.filter { (it.completedAtTimestamp ?: Long.MAX_VALUE) <= timestamp }
                require(completedByNow.size <= TrainingFrequencyRecencyPolicy.MAX_SESSIONS) {
                    "Scheduling history exceeds its reconstruction bound."
                }
                Triple(
                    read.index to completedByNow,
                    exercises,
                    Instant.ofEpochMilli(timestamp)
                )
            }.distinctUntilChanged { previous, next ->
                previous.first == next.first && previous.second == next.second
            }.map { (read, exercises, now) -> policy.derive(read.second, exercises, now, zone) }
        }
    }

    suspend fun build(): WorkoutGenerationContext {
        val now = Instant.ofEpochMilli(nowTimestamp())
        val zone = zoneId()
        val profile = userProfileRepository.getProfileOnce()
        val recentCompletedSessions = workoutRepository.getRecentCompletedSessions(
            limit = MAX_RECENT_SESSIONS
        )
        val allExercises = exerciseCatalog.getAllExercises().first()
        val completedWorkoutCount = workoutRepository.observeCompletedWorkoutCount().first()
        val exerciseHistory = historyAnalyzer.exerciseHistory(
            sessions = recentCompletedSessions,
            targetWeightUnit = profile.preferredUnit
        )
        val progressionHistory = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            workoutRepository.getRecentSessions(ProgressionEngine.MAX_SESSIONS).also(ProgressionEngine::requireBounded)
        } else emptyList()
        val recentRecommendationRecords = if (progressionHistory.isNotEmpty()) {
            val ids = progressionHistory.map { it.id }
            val records = workoutRepository.getRecommendationRecords(ids)
            require(records.size <= ids.size && records.map { it.sessionId }.distinct().size == records.size &&
                records.all { it.sessionId in ids }
            ) { "Historical recommendation records are duplicated or outside the requested batch." }
            records.associateBy { it.sessionId }
        } else emptyMap()
        val deloadPreferences = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            deloadRepository?.get()
        } else null
        val acceptedDeload = deloadPreferences?.let(DeloadOfferPolicy::accepted) != null
        // Composed only on the reviewed path, so the legacy path reads no extra history.
        val trainingProgramState = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            trainingProgramStateProvider?.stateAt(profile, now, zone, acceptedDeload)
        } else {
            null
        }
        val priorUserRestPreferences =
            if (plannerFeatureFlags.reviewedCapabilityEligibility) {
                priorUserRestPreferences(recentCompletedSessions)
            } else {
                emptyMap()
            }
        val capabilityEvidence = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            capabilityEvidencePolicy.derive(
                sessions = recentCompletedSessions,
                exercises = allExercises
            )
        } else {
            CapabilityEvidenceSet.empty()
        }
        val automaticEligibilityResult = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            val exercisesById = allExercises.associateBy { it.id }
            reviewedEligibilityPolicy.evaluate(
                exercises = allExercises,
                profile = profile,
                // Both branches use the same policy, so the value cannot diverge when no
                // provider is supplied.
                adaptationState = trainingProgramState?.adaptationState
                    ?: adaptationStatePolicy.derive(profile, acceptedDeload),
                demonstratedProgressionFamilies = exerciseHistory.keys.mapNotNullTo(linkedSetOf()) {
                    exerciseId ->
                    exercisesById[exerciseId]
                        ?.acceptedMetadata()
                        ?.progressionFamily
                }
            )
        } else {
            null
        }
        val allowedExercises = when (automaticEligibilityResult) {
            is AutomaticEligibilityResult.Candidates -> automaticEligibilityResult.exercises
            is AutomaticEligibilityResult.NoCandidates -> emptyList()
            null -> exerciseFilter.filterCandidates(
                allExercises = allExercises,
                profile = profile
            )
        }
        val schedulingEvidence = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            val policy = TrainingFrequencyRecencyPolicy()
            policy.derive(
                workoutRepository.getCompletedSessionsInRange(
                    policy.windowStart(now, zone), Math.addExact(now.toEpochMilli(), 1)
                ),
                allExercises, now, zone
            )
        } else null

        return WorkoutGenerationContext(
            userProfile = profile,
            fitnessGoal = profile.primaryGoal,
            experienceLevel = profile.experienceLevel,
            availableEquipment = profile.availableEquipment,
            preferredWorkoutDurationMinutes = profile.preferredDurationMinutes,
            trainingFrequencyDaysPerWeek = profile.daysPerWeek,
            musclePriorities = profile.musclePriorities,
            recentWorkoutHistory = recentCompletedSessions,
            progressionHistory = progressionHistory,
            recentRecommendationRecords = recentRecommendationRecords,
            historyAsOfTimestamp = now.toEpochMilli(),
            deloadPreferences = deloadPreferences,
            completedWorkoutCount = completedWorkoutCount,
            exerciseHistory = exerciseHistory,
            recentlyTrainedMuscles = historyAnalyzer.recentlyTrainedMuscles(
                sessions = recentCompletedSessions,
                nowTimestamp = now.toEpochMilli()
            ),
            excludedExerciseIds = profile.excludedExerciseIds,
            allowedExercises = allowedExercises,
            automaticEligibilityResult = automaticEligibilityResult,
            capabilityEvidence = capabilityEvidence,
            schedulingEvidence = schedulingEvidence,
            trainingProgramState = trainingProgramState,
            priorUserRestPreferences = priorUserRestPreferences,
            preferredUnits = profile.preferredUnit,
            catalogVersion = catalogVersion(),
            reviewPolicyVersion = allExercises.acceptedReviewPolicyVersion()
        )
    }

    private fun priorUserRestPreferences(
        sessions: List<WorkoutSession>
    ): Map<String, UserRestPreference> {
        val preferences = linkedMapOf<String, UserRestPreference>()
        var examinedPrescriptions = 0
        for (session in sessions) {
            for (exercise in session.exercises) {
                if (examinedPrescriptions == MAX_PRIOR_REST_PRESCRIPTIONS) {
                    return preferences
                }
                examinedPrescriptions += 1
                val preference = exercise.prescription.userRestPreferenceOrNull() ?: continue
                preferences.putIfAbsent(exercise.exerciseId, preference)
            }
        }
        return preferences
    }

    private companion object {
        const val MAX_RECENT_SESSIONS = 8
        const val MAX_PRIOR_REST_PRESCRIPTIONS = 512
    }
}
