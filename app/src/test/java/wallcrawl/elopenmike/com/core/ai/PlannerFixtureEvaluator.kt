package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata
import wallcrawl.elopenmike.com.core.model.ExerciseSource
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSet

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
    val secondWorkout: GeneratedWorkout
) : PlannerFixtureEvaluation

internal data class PlannerFixtureFailureEvaluation(
    override val built: PlannerFixtureContext,
    override val inputBefore: PlannerFixtureInputSnapshot,
    override val inputAfterFirstAttempt: PlannerFixtureInputSnapshot,
    override val inputAfterSecondAttempt: PlannerFixtureInputSnapshot,
    val firstFailure: WorkoutPlanningFailure,
    val secondFailure: WorkoutPlanningFailure
) : PlannerFixtureEvaluation

internal data class PlannerFixtureInputSnapshot(
    val userProfile: UserProfile,
    val completedWorkoutCount: Int,
    val exerciseHistory: Map<String, ExercisePerformanceHistory>,
    val excludedExerciseIds: List<String>,
    val allowedExercises: List<Exercise>
)

internal class PlannerFixtureEvaluator(
    private val loader: PlannerFixtureLoader = PlannerFixtureLoader(),
    private val contextFactory: PlannerFixtureContextFactory = PlannerFixtureContextFactory()
) {

    suspend fun evaluateCorpus(): List<PlannerFixtureEvaluation> =
        loader.loadCorpus().map { fixture ->
            evaluateFixture(fixture)
        }

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
                    secondWorkout = secondWorkout
                )
            }

            else -> {
                val expectedFailure = fixture.expected.outcome.toPlanningFailure()
                val firstFailure = captureFailure(built.context)
                val inputAfterFirst = built.context.snapshot()
                val secondFailure = captureFailure(built.context)
                val inputAfterSecond = built.context.snapshot()
                check(firstFailure == expectedFailure) {
                    "Expected ${fixture.id} to fail with $expectedFailure, but got $firstFailure."
                }
                check(secondFailure == expectedFailure) {
                    "Expected ${fixture.id} to fail with $expectedFailure, but got $secondFailure."
                }
                PlannerFixtureFailureEvaluation(
                    built = built,
                    inputBefore = inputBefore,
                    inputAfterFirstAttempt = inputAfterFirst,
                    inputAfterSecondAttempt = inputAfterSecond,
                    firstFailure = firstFailure,
                    secondFailure = secondFailure
                )
            }
        }
    }

    private suspend fun captureFailure(context: WorkoutGenerationContext): WorkoutPlanningFailure =
        try {
            FakeWorkoutPlanner().generateWorkout(context)
            error("Expected planner generation to fail for fixture context.")
        } catch (exception: WorkoutValidationException) {
            exception.failure
        }

    private fun PlannerFixtureOutcome.toPlanningFailure(): WorkoutPlanningFailure = when (this) {
        PlannerFixtureOutcome.SUCCESS -> error("Successful fixtures do not map to planning failures.")
        PlannerFixtureOutcome.NO_CANDIDATES -> WorkoutPlanningFailure.NO_CANDIDATES
        PlannerFixtureOutcome.NO_STRENGTH_CANDIDATES -> WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES
        PlannerFixtureOutcome.NO_CANDIDATES_FOR_ANY_SPLIT ->
            WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT
    }
}

internal fun GeneratedWorkout.normalizedPlannerFixtureWorkout(): GeneratedWorkout =
    copy(id = "normalized-generated-workout")

private fun WorkoutGenerationContext.snapshot(): PlannerFixtureInputSnapshot =
    PlannerFixtureInputSnapshot(
        userProfile = userProfile.deepCopy(),
        completedWorkoutCount = completedWorkoutCount,
        exerciseHistory = exerciseHistory.mapValues { (_, history) -> history.deepCopy() },
        excludedExerciseIds = excludedExerciseIds.toList(),
        allowedExercises = allowedExercises.map(Exercise::deepCopy)
    )

private fun UserProfile.deepCopy(): UserProfile = copy(
    goals = goals.toSet(),
    availableEquipment = availableEquipment.toList(),
    musclePriorities = LinkedHashMap(musclePriorities),
    excludedExerciseIds = excludedExerciseIds.toList(),
    trainingConstraints = trainingConstraints.toSet(),
    confirmedStartingLoads = LinkedHashMap(confirmedStartingLoads),
    movementCapabilities = MovementCapabilities.from(movementCapabilities.values)
)

private fun ExercisePerformanceHistory.deepCopy(): ExercisePerformanceHistory = copy(
    recentSets = recentSets.map(WorkoutSet::copy)
)

private fun Exercise.deepCopy(): Exercise = copy(
    source = source?.deepCopy(),
    searchAliases = searchAliases.toList(),
    primaryMuscles = primaryMuscles.toList(),
    secondaryMuscles = secondaryMuscles.toList(),
    listedEquipment = listedEquipment.toList(),
    programming = programming?.deepCopy()
)

private fun ExerciseSource.deepCopy(): ExerciseSource = copy(attribution = attribution.copy(source = attribution.source?.copy()))

private fun ExerciseProgrammingMetadata.deepCopy(): ExerciseProgrammingMetadata = copy(
    requiredEquipmentCombinations = requiredEquipmentCombinations.map(List<String>::toList),
    recommendedRepRange = RepRange(recommendedRepRange.min, recommendedRepRange.max),
    alternativeExerciseIds = alternativeExerciseIds.toList()
)
