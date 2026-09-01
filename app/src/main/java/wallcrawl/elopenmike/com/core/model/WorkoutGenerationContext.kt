package wallcrawl.elopenmike.com.core.model

/**
 * Rich contextual payload provided to [wallcrawl.elopenmike.com.core.ai.WorkoutPlanner]
 * so an on-device local model (or fake planner) can select the optimal workout.
 *
 * Notice: [allowedExercises] contains only candidates that passed the active eligibility
 * path. The planner must choose ONLY from these candidate IDs.
 */
data class WorkoutGenerationContext(
    val userProfile: UserProfile,
    val fitnessGoals: Set<FitnessGoal> = userProfile.goals,
    val fitnessGoal: FitnessGoal = fitnessGoals.firstOrNull() ?: userProfile.primaryGoal,
    val experienceLevel: ExperienceLevel = userProfile.experienceLevel,
    val availableEquipment: List<String> = userProfile.availableEquipment,
    val preferredWorkoutDurationMinutes: Int = userProfile.preferredDurationMinutes,
    val trainingFrequencyDaysPerWeek: Int = userProfile.daysPerWeek,
    val musclePriorities: Map<String, PriorityLevel> = userProfile.musclePriorities,
    val recentWorkoutHistory: List<WorkoutSession> = emptyList(),
    /** Lifetime completed workouts, so split rotation survives the process being killed. */
    val completedWorkoutCount: Int = 0,
    val exerciseHistory: Map<String, ExercisePerformanceHistory> = emptyMap(),
    val recentlyTrainedMuscles: List<String> = emptyList(),
    val excludedExerciseIds: List<String> = userProfile.excludedExerciseIds,
    val allowedExercises: List<Exercise> = emptyList(),
    val automaticEligibilityResult: AutomaticEligibilityResult? = null,
    /**
     * The composed program state, present only when reviewed eligibility is enabled.
     *
     * No planner or policy reads it yet. It is carried so weekly dose targets and
     * recommendation snapshots can consume the ledger without re-deriving it, and it stays
     * null on the legacy path so that path reads no history or catalog it did not already
     * read.
     */
    val trainingProgramState: TrainingProgramState? = null,
    val preferredUnits: WeightUnit = userProfile.preferredUnit
)

/**
 * Historical performance summary for a specific exercise to help the planner
 * suggest appropriate starting weights and progression targets.
 */
data class ExercisePerformanceHistory(
    val exerciseId: String,
    val lastWeight: Double?,
    val lastReps: Int?,
    val bestEstimated1RM: Double?,
    val recentSets: List<WorkoutSet> = emptyList()
)
