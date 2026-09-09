package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.EligibilityDecision
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.SessionProgramConstraints
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.UserRestPreference
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession

/**
 * The one parsed fixture corpus and bundled catalog projection for the planner-fixture
 * suites and `TimedHoldProgrammingTest`.
 *
 * It is deliberately not run-wide: nine other JVM suites build their own
 * `PlannerFixtureContextFactory` and parse the bundled catalog again. Routing those through
 * here would be a change to code outside this corpus's surface, so the scope is stated
 * rather than quietly widened.
 *
 * JUnit builds a fresh instance per test method and neither `PlannerFixtureLoader` nor
 * `PlannerFixtureContextFactory` caches across instances, so without this each suite — and
 * before that each method — re-read the twelve fixture resources and reparsed the 302-entry
 * bundled catalog. Everything held here is immutable parsed data.
 *
 * Only construction is shared. Every replay still goes through
 * [PlannerFixtureEvaluator.evaluateFixture], which builds a fresh [FakeWorkoutPlanner] and
 * therefore a fresh generation counter per attempt, so determinism is unaffected.
 */
internal object SharedPlannerFixtureHarness {
    val loader = PlannerFixtureLoader()
    val contextFactory = PlannerFixtureContextFactory()
    val evaluator = PlannerFixtureEvaluator(contextFactory = contextFactory)
    val corpus: List<PlannerFixture> by lazy { loader.loadCorpus() }
}

internal sealed interface PlannerFixtureEvaluation {
    val built: PlannerFixtureContext
    val inputBefore: PlannerFixtureInputSnapshot
    val inputAfterFirstAttempt: PlannerFixtureInputSnapshot
    val inputAfterSecondAttempt: PlannerFixtureInputSnapshot
}

internal data class PlannerFixtureSuccessEvaluation(
    override val built: PlannerFixtureContext,
    override val inputBefore: PlannerFixtureInputSnapshot,
    override val inputAfterFirstAttempt: PlannerFixtureInputSnapshot,
    override val inputAfterSecondAttempt: PlannerFixtureInputSnapshot,
    val firstWorkout: GeneratedWorkout,
    val secondWorkout: GeneratedWorkout,
    /**
     * Validation of [firstWorkout] only.
     *
     * The second replay is deliberately not validated: `ProgramValidator` is pure, both
     * replays are checked against the same context instance, and the two workouts are
     * already asserted identical in every field but the UUID id. A second verdict would be
     * a pure function of inputs proven equal, so it could not fail.
     */
    val firstValidation: PlannerFixtureValidation
) : PlannerFixtureEvaluation

/**
 * What whole-program validation concluded about one replayed proposal.
 *
 * Both passes are kept because they answer different questions. [raw] is what the planner
 * itself produced, judged with repair disabled — the only evidence that generation was
 * correct. [displayed] is what `TodayViewModel` would actually be allowed to show, where
 * exactly one bounded repair pass is permitted. Collapsing them would let a repaired
 * proposal stand in for a valid one.
 */
internal data class PlannerFixtureValidation(
    val raw: ProgramValidationResult,
    val displayed: ProgramValidationResult
)

internal data class PlannerFixtureFailureEvaluation(
    override val built: PlannerFixtureContext,
    override val inputBefore: PlannerFixtureInputSnapshot,
    override val inputAfterFirstAttempt: PlannerFixtureInputSnapshot,
    override val inputAfterSecondAttempt: PlannerFixtureInputSnapshot,
    val firstFailure: WorkoutPlanningFailure,
    val secondFailure: WorkoutPlanningFailure,
    val firstAutomaticEligibilityFailure: AutomaticEligibilityFailure?,
    val secondAutomaticEligibilityFailure: AutomaticEligibilityFailure?
) : PlannerFixtureEvaluation

private data class CapturedPlannerFailure(
    val failure: WorkoutPlanningFailure,
    val automaticEligibilityFailure: AutomaticEligibilityFailure?
)

internal data class PlannerFixtureInputSnapshot(
    val userProfile: UserProfile,
    val fitnessGoals: Set<FitnessGoal>,
    val fitnessGoal: FitnessGoal,
    val experienceLevel: ExperienceLevel,
    val availableEquipment: List<String>,
    val preferredWorkoutDurationMinutes: Int,
    val trainingFrequencyDaysPerWeek: Int,
    val musclePriorities: Map<String, PriorityLevel>,
    val recentWorkoutHistory: List<WorkoutSession>,
    val completedWorkoutCount: Int,
    val exerciseHistory: Map<String, ExercisePerformanceHistory>,
    val recentlyTrainedMuscles: List<String>,
    val excludedExerciseIds: List<String>,
    val allowedExercises: List<Exercise>,
    val automaticEligibilityResult: AutomaticEligibilityResult?,
    /**
     * Deeply immutable, so the snapshot holds the instance directly.
     */
    val capabilityEvidence: CapabilityEvidenceSet,
    /**
     * Deep-copied like every other mutable branch.
     *
     * `WeeklyDoseLedger` declares its three count maps as `Map` and stores the caller's
     * references without a defensive copy, and `WeeklyDoseLedgerCalculator` supplies a
     * `TreeMap` and a `LinkedHashMap`. Holding the state by reference would make the
     * non-mutation assertion trivially true for the one input this corpus exists to
     * reconstruct.
     */
    val trainingProgramState: TrainingProgramState?,
    val priorUserRestPreferences: Map<String, UserRestPreference>,
    val preferredUnits: WeightUnit,
    val catalogVersion: String?,
    val reviewPolicyVersion: Int,
    /**
     * Copied, with its one collection re-materialised.
     *
     * `requiredMovementPatterns` is declared `Set<MovementPattern>` and can hold a caller's
     * mutable set; the two booleans beside it need nothing.
     */
    val programConstraints: SessionProgramConstraints
)

/**
 * [contextFactory] is required rather than defaulted on purpose. A default would construct a
 * second factory and silently reparse the 302-entry bundled catalog — the exact trap round 11
 * found in a suite that built one factory in a field and another inside this constructor.
 */
internal class PlannerFixtureEvaluator(
    private val contextFactory: PlannerFixtureContextFactory
) {

    suspend fun evaluateFixture(fixture: PlannerFixture): PlannerFixtureEvaluation {
        val built = contextFactory.create(fixture)
        val inputBefore = built.context.snapshot()

        return when (fixture.expected.outcome) {
            PlannerFixtureOutcome.SUCCESS -> {
                val firstWorkout = FakeWorkoutPlanner().generateWorkout(built.context)
                val inputAfterFirst = built.context.snapshot()
                val secondWorkout = FakeWorkoutPlanner().generateWorkout(built.context)
                val inputAfterSecond = built.context.snapshot()
                PlannerFixtureSuccessEvaluation(
                    built = built,
                    inputBefore = inputBefore,
                    inputAfterFirstAttempt = inputAfterFirst,
                    inputAfterSecondAttempt = inputAfterSecond,
                    firstWorkout = firstWorkout,
                    secondWorkout = secondWorkout,
                    firstValidation = validateWholeProgram(firstWorkout, built)
                )
            }

            else -> {
                val expectedFailure = fixture.expected.outcome.toPlanningFailure()
                val firstFailure = captureFailure(built.context)
                val inputAfterFirst = built.context.snapshot()
                val secondFailure = captureFailure(built.context)
                val inputAfterSecond = built.context.snapshot()
                check(firstFailure.failure == expectedFailure) {
                    "Expected ${fixture.id} to fail with $expectedFailure, but got ${firstFailure.failure}."
                }
                check(secondFailure.failure == expectedFailure) {
                    "Expected ${fixture.id} to fail with $expectedFailure, but got ${secondFailure.failure}."
                }
                check(
                    firstFailure.automaticEligibilityFailure ==
                        fixture.expected.automaticEligibilityFailure
                ) {
                    "Expected ${fixture.id} to preserve automatic eligibility failure " +
                        "${fixture.expected.automaticEligibilityFailure}, but got " +
                        "${firstFailure.automaticEligibilityFailure}."
                }
                check(
                    secondFailure.automaticEligibilityFailure ==
                        fixture.expected.automaticEligibilityFailure
                ) {
                    "Expected ${fixture.id} to preserve automatic eligibility failure " +
                        "${fixture.expected.automaticEligibilityFailure}, but got " +
                        "${secondFailure.automaticEligibilityFailure}."
                }
                PlannerFixtureFailureEvaluation(
                    built = built,
                    inputBefore = inputBefore,
                    inputAfterFirstAttempt = inputAfterFirst,
                    inputAfterSecondAttempt = inputAfterSecond,
                    firstFailure = firstFailure.failure,
                    secondFailure = secondFailure.failure,
                    firstAutomaticEligibilityFailure =
                        firstFailure.automaticEligibilityFailure,
                    secondAutomaticEligibilityFailure =
                        secondFailure.automaticEligibilityFailure
                )
            }
        }
    }

    /**
     * Runs the production [ProgramValidator] over a replayed proposal, both ways.
     *
     * The validator is built from the same bundled projection the context was built from,
     * including any synthetic approvals, so the corpus never validates against a catalog
     * the planner did not see. Nothing here writes to a ledger: validation accounts a
     * proposal prospectively and a proposed workout is never completed exposure.
     */
    private suspend fun validateWholeProgram(
        workout: GeneratedWorkout,
        built: PlannerFixtureContext
    ): PlannerFixtureValidation {
        val validator = ProgramValidator(
            GeneratedWorkoutValidator(InMemoryExerciseCatalog(built.catalogExercises))
        )
        return PlannerFixtureValidation(
            raw = validator.validate(
                workout = workout,
                context = built.context,
                allowRepair = false
            ),
            displayed = validator.validate(
                workout = workout,
                context = built.context,
                allowRepair = true
            )
        )
    }

    private suspend fun captureFailure(context: WorkoutGenerationContext): CapturedPlannerFailure =
        try {
            FakeWorkoutPlanner().generateWorkout(context)
            error("Expected planner generation to fail for fixture context.")
        } catch (exception: WorkoutValidationException) {
            CapturedPlannerFailure(
                failure = exception.failure,
                automaticEligibilityFailure = exception.automaticEligibilityFailure
            )
        }

    private fun PlannerFixtureOutcome.toPlanningFailure(): WorkoutPlanningFailure = when (this) {
        PlannerFixtureOutcome.SUCCESS -> error("Successful fixtures do not map to planning failures.")
        PlannerFixtureOutcome.NO_CANDIDATES -> WorkoutPlanningFailure.NO_CANDIDATES
        PlannerFixtureOutcome.NO_STRENGTH_CANDIDATES -> WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES
        PlannerFixtureOutcome.NO_CANDIDATES_FOR_ANY_SPLIT ->
            WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT
        PlannerFixtureOutcome.REVIEWED_ELIGIBILITY_NO_CANDIDATES ->
            WorkoutPlanningFailure.REVIEWED_ELIGIBILITY_NO_CANDIDATES
    }
}

internal fun GeneratedWorkout.normalizedPlannerFixtureWorkout(): GeneratedWorkout =
    copy(id = "normalized-generated-workout")

private fun WorkoutGenerationContext.snapshot(): PlannerFixtureInputSnapshot =
    PlannerFixtureInputSnapshot(
        userProfile = userProfile.deepCopy(),
        fitnessGoals = fitnessGoals.toSet(),
        fitnessGoal = fitnessGoal,
        experienceLevel = experienceLevel,
        availableEquipment = availableEquipment.toList(),
        preferredWorkoutDurationMinutes = preferredWorkoutDurationMinutes,
        trainingFrequencyDaysPerWeek = trainingFrequencyDaysPerWeek,
        musclePriorities = LinkedHashMap(musclePriorities),
        recentWorkoutHistory = recentWorkoutHistory.map(WorkoutSession::deepCopy),
        completedWorkoutCount = completedWorkoutCount,
        exerciseHistory = exerciseHistory.entries.associate { (exerciseId, history) ->
            exerciseId to history.deepCopy()
        },
        recentlyTrainedMuscles = recentlyTrainedMuscles.toList(),
        excludedExerciseIds = excludedExerciseIds.toList(),
        allowedExercises = allowedExercises.map(Exercise::deepCopy),
        automaticEligibilityResult = automaticEligibilityResult?.deepCopy(),
        capabilityEvidence = capabilityEvidence,
        trainingProgramState = trainingProgramState?.deepCopy(),
        priorUserRestPreferences = LinkedHashMap(priorUserRestPreferences),
        preferredUnits = preferredUnits,
        catalogVersion = catalogVersion,
        reviewPolicyVersion = reviewPolicyVersion,
        programConstraints = programConstraints.copy(
            requiredMovementPatterns = programConstraints.requiredMovementPatterns.toSet()
        )
    )

private fun TrainingProgramState.deepCopy(): TrainingProgramState = copy(
    weeklyLedger = weeklyLedger.copy(
        directPrimarySets = LinkedHashMap(weeklyLedger.directPrimarySets),
        secondaryInvolvement = LinkedHashMap(weeklyLedger.secondaryInvolvement),
        unattributedWorkSets = LinkedHashMap(weeklyLedger.unattributedWorkSets)
    )
)

private fun UserProfile.deepCopy(): UserProfile = copy(
    goals = goals.toSet(),
    availableEquipment = availableEquipment.toList(),
    musclePriorities = LinkedHashMap(musclePriorities),
    excludedExerciseIds = excludedExerciseIds.toList(),
    trainingConstraints = trainingConstraints.toSet(),
    confirmedStartingLoads = LinkedHashMap(confirmedStartingLoads)
    // `movementCapabilities` is carried across as-is: `MovementCapabilities` has a private
    // constructor and its only factory wraps a freshly built map in `unmodifiableMap`, so
    // every reachable instance is deeply immutable — the same reason `capabilityEvidence` is
    // held directly in the snapshot itself.
)

private fun ExercisePerformanceHistory.deepCopy(): ExercisePerformanceHistory = copy(
    // `WorkoutSet` is scalars and enums throughout, so only the list needs re-materialising.
    recentSets = recentSets.toList()
)

private fun WorkoutSession.deepCopy(): WorkoutSession = copy(
    focusMuscles = focusMuscles.toList(),
    exercises = exercises.map(WorkoutExercise::deepCopy)
)

private fun WorkoutExercise.deepCopy(): WorkoutExercise = copy(
    // `ExercisePrescription` and `WorkoutSet` hold nothing mutable, so the prescription is
    // carried across as-is and only the set list is re-materialised.
    sets = sets.toList()
)

private fun Exercise.deepCopy(): Exercise = copy(
    // `source` is a tree of `String`s with no collection anywhere in it, so it is carried
    // across as-is, like `capabilityEvidence` in the snapshot itself.
    searchAliases = searchAliases.toList(),
    primaryMuscles = primaryMuscles.toList(),
    secondaryMuscles = secondaryMuscles.toList(),
    listedEquipment = listedEquipment.toList(),
    programming = programming?.deepCopy(),
    reviewedMetadata = reviewedMetadata?.deepCopy()
)

private fun AutomaticEligibilityResult.deepCopy(): AutomaticEligibilityResult = when (this) {
    is AutomaticEligibilityResult.Candidates -> copy(
        exercises = exercises.map(Exercise::deepCopy),
        decisions = decisions.map(EligibilityDecision::deepCopy)
    )

    is AutomaticEligibilityResult.NoCandidates -> copy(
        decisions = decisions.map(EligibilityDecision::deepCopy)
    )
}

private fun EligibilityDecision.deepCopy(): EligibilityDecision = copy(
    reasons = reasons.toList(),
    preferences = preferences.toList()
)

private fun ReviewedExerciseMetadata.deepCopy(): ReviewedExerciseMetadata = copy(
    descriptiveSecondaryMuscles = descriptiveSecondaryMuscles.toSet(),
    // `ReviewedExerciseLink` is two strings, so only the lists need re-materialising.
    approvedRegressions = approvedRegressions.toList(),
    approvedSubstitutions = approvedSubstitutions.toList(),
    capabilityRequirements = capabilityRequirements.toSet(),
    equipmentAlternatives = equipmentAlternatives.map(List<String>::toList)
)

private fun ExerciseProgrammingMetadata.deepCopy(): ExerciseProgrammingMetadata = copy(
    requiredEquipmentCombinations = requiredEquipmentCombinations.map(List<String>::toList),
    alternativeExerciseIds = alternativeExerciseIds.toList()
)
