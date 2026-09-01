package wallcrawl.elopenmike.com.core.ai

import kotlinx.coroutines.flow.first
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.ReviewState
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
    private val adaptationStatePolicy: AdaptationStatePolicy = AdaptationStatePolicy(),
    private val trainingProgramStateProvider: TrainingProgramStateProvider? = null,
    private val nowTimestamp: () -> Long = System::currentTimeMillis
) {

    suspend fun build(): WorkoutGenerationContext {
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
        // Composed only on the reviewed path, so the legacy path reads no extra history.
        val trainingProgramState = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            trainingProgramStateProvider?.currentState(profile)
        } else {
            null
        }
        val priorUserRestPreferences =
            if (plannerFeatureFlags.reviewedCapabilityEligibility) {
                priorUserRestPreferences(recentCompletedSessions)
            } else {
                emptyMap()
            }
        val automaticEligibilityResult = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            val exercisesById = allExercises.associateBy { it.id }
            reviewedEligibilityPolicy.evaluate(
                exercises = allExercises,
                profile = profile,
                // Both branches use the same policy, so the value cannot diverge when no
                // provider is supplied.
                adaptationState = trainingProgramState?.adaptationState
                    ?: adaptationStatePolicy.derive(profile),
                demonstratedProgressionFamilies = exerciseHistory.keys.mapNotNullTo(linkedSetOf()) {
                    exerciseId ->
                    exercisesById[exerciseId]
                        ?.reviewedMetadata
                        ?.takeIf { it.reviewState == ReviewState.APPROVED }
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

        return WorkoutGenerationContext(
            userProfile = profile,
            fitnessGoal = profile.primaryGoal,
            experienceLevel = profile.experienceLevel,
            availableEquipment = profile.availableEquipment,
            preferredWorkoutDurationMinutes = profile.preferredDurationMinutes,
            trainingFrequencyDaysPerWeek = profile.daysPerWeek,
            musclePriorities = profile.musclePriorities,
            recentWorkoutHistory = recentCompletedSessions,
            completedWorkoutCount = completedWorkoutCount,
            exerciseHistory = exerciseHistory,
            recentlyTrainedMuscles = historyAnalyzer.recentlyTrainedMuscles(
                sessions = recentCompletedSessions,
                nowTimestamp = nowTimestamp()
            ),
            excludedExerciseIds = profile.excludedExerciseIds,
            allowedExercises = allowedExercises,
            automaticEligibilityResult = automaticEligibilityResult,
            trainingProgramState = trainingProgramState,
            priorUserRestPreferences = priorUserRestPreferences,
            preferredUnits = profile.preferredUnit
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
