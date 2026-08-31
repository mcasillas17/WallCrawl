package wallcrawl.elopenmike.com.core.ai

import kotlinx.coroutines.flow.first
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.ReviewState
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
        val automaticEligibilityResult = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            val exercisesById = allExercises.associateBy { it.id }
            reviewedEligibilityPolicy.evaluate(
                exercises = allExercises,
                profile = profile,
                adaptationState = if (profile.returningAfterBreakWeeks > 0) {
                    AdaptationState.RETURNING
                } else {
                    AdaptationState.UNCALIBRATED
                },
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
            preferredUnits = profile.preferredUnit
        )
    }

    private companion object {
        const val MAX_RECENT_SESSIONS = 8
    }
}
