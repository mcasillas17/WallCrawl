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
     * Reviewed-only capability evidence derived from the bounded completed-history snapshot.
     *
     * Empty on the legacy path.
     */
    val capabilityEvidence: CapabilityEvidenceSet = CapabilityEvidenceSet.empty(),
    /**
     * The composed program state, present only when reviewed eligibility is enabled.
     *
     * The reviewed-enabled state-based policy reads its ledger to cap direct-primary sets.
     * It stays null on the legacy path so that path reads no history or catalog it did not
     * already read.
     */
    val trainingProgramState: TrainingProgramState? = null,
    /**
     * Newest explicit per-exercise rest choices from the bounded history view.
     *
     * Product-policy defaults are excluded, so generated guidance never promotes itself into
     * a user preference.
     */
    val priorUserRestPreferences: Map<String, UserRestPreference> = emptyMap(),
    val preferredUnits: WeightUnit = userProfile.preferredUnit,
    /**
     * Commit of the bundled catalog these candidates came from, when it is known.
     *
     * It is recorded with a validated recommendation so a past decision names the content
     * it was made against. Null means the catalog snapshot was not loaded, which is
     * reported as absent rather than filled in with a guess.
     */
    val catalogVersion: String? = null,
    /**
     * The highest review-policy version authored in that catalog's reviewed metadata.
     *
     * Read from the catalog for the same reason the weekly ledger reads it there: shipping
     * metadata authored under a new review policy must invalidate old identity rather than
     * silently reinterpret it. A catalog with no reviewed metadata reports 0.
     */
    val reviewPolicyVersion: Int = 0,
    /** The program-design rules this session was asked to satisfy. */
    val programConstraints: SessionProgramConstraints = SessionProgramConstraints()
)

/**
 * The program-design constraints one proposed session must satisfy.
 *
 * These are **declared**, never universal. Whole-program validation enforces exactly what
 * is switched on here and nothing else, which is what keeps a scoped product rule from
 * quietly becoming a claim that repeating a movement is harmful or that every session must
 * cover every pattern.
 *
 * The scope is one generated session. Nothing here applies across sessions or across a
 * week, and nothing here applies to manual templates, which are explicit user choices.
 */
data class SessionProgramConstraints(
    /**
     * Whether one catalog exercise id may appear only once in a session.
     *
     * On by default because two instances of one id inside a single generated session
     * cannot be told apart in its recommendation record and would be counted twice by
     * prospective dose accounting. The rationale is accounting and identity integrity, not
     * a judgement about repeated movements.
     */
    val uniqueExerciseIds: Boolean = true,
    /**
     * Whether one approved progression family may appear only once in a session.
     *
     * Off by default: no product decision has declared it, and repeated families are not
     * inherently a defect.
     */
    val uniqueProgressionFamilies: Boolean = false,
    /**
     * Movement patterns this session must contain.
     *
     * Empty by default, so no workout is required to cover any pattern. The planner's
     * pattern spreading stays a ranking preference with an explicit fallback to repeated
     * patterns; it is not promoted to a rule here.
     */
    val requiredMovementPatterns: Set<MovementPattern> = emptySet()
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
