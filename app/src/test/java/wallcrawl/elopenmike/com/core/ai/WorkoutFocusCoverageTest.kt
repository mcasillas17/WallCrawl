package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSplit

/**
 * The advertised split has to be one the selected exercises actually train.
 *
 * These replay real profiles through the real bundled catalog, the real `ExerciseFilter`,
 * the real `FakeWorkoutPlanner` and the real `ProgramValidator`. Every assertion goes
 * through the shipped [trainsAsFocus] contract rather than a classifier written for the
 * test, so the tests and the planner cannot drift apart.
 *
 * Nothing here is a claim that a session must cover every pattern or that a supported
 * session is medically appropriate. It is one rule: a label must be true.
 */
class WorkoutFocusCoverageTest {

    private val factory = PlannerFixtureContextFactory()
    private val catalog = factory.bundledCatalogProjection().exercises

    // region the committed band-only reproduction

    @Test
    fun bandOnlyChestPriority_advertisesASplitItsSelectionActuallyTrains() = runTest {
        val context = bandOnlyContext()
        val workout = FakeWorkoutPlanner().generateWorkout(context)
        val selected = context.resolve(workout.exercises.map { it.exerciseId })

        assertWithMessage(
            "advertised ${workout.title.split} with ${selected.map(Exercise::id)}"
        ).that(selected.any { workout.title.split.trainsAsFocus(it) }).isTrue()
        assertThat(workout.title.split).isNotEqualTo(WorkoutSplit.PUSH)
    }

    @Test
    fun bandOnlyChestPriority_reportsTheUnavailablePriorityRatherThanConcealingIt() = runTest {
        val workout = FakeWorkoutPlanner().generateWorkout(bandOnlyContext())

        assertThat(workout.unavailableFocusMuscles).containsExactly(StandardMuscles.CHEST)
    }

    @Test
    fun bandOnlyChestPriority_staysInsideTheBandOnlyCandidateSet() = runTest {
        val context = bandOnlyContext()
        val workout = FakeWorkoutPlanner().generateWorkout(context)
        val selected = context.resolve(workout.exercises.map { it.exerciseId })

        assertThat(workout.exercises.map { it.exerciseId })
            .containsNoneIn(listOf("push-up", "barbell-bench-press", "dumbbell-bench-press"))
        selected.forEach { exercise ->
            assertWithMessage("${exercise.id} equipment")
                .that(exercise.listedEquipment)
                .containsExactly(StandardEquipment.RESISTANCE_BAND)
        }
    }

    @Test
    fun bandOnlyChestPriority_producesAProposalWholeProgramValidationAccepts() = runTest {
        val context = bandOnlyContext()
        val workout = FakeWorkoutPlanner().generateWorkout(context)

        val result = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(catalog)))
            .validate(workout, context, allowRepair = false)

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun theOriginalMisleadingProposal_isRefusedByWholeProgramValidation() = runTest {
        // The exact plan the bug produced, handed to the validator directly. Structural
        // validity was never the question: this is what must now be refused before it can
        // be displayed or started.
        val context = bandOnlyContext()
        val misleading = validatedWorkout(
            exercises = listOf(
                "band-pull-apart",
                "banded-dead-bug",
                "banded-face-pull",
                "banded-pallof-press",
                "banded-woodchop"
            ).map { bodyweightRepPlan(it) },
            split = WorkoutSplit.PUSH
        )

        val result = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(catalog)))
            .validate(misleading, context, allowRepair = true)

        assertThat(result.codes()).contains(ProgramViolationCode.UNSUPPORTED_WORKOUT_FOCUS)
    }

    // endregion

    // region genuine push coverage is preserved

    @Test
    fun aBodyweightChestPriority_stillGetsAGenuinePushDay() = runTest {
        assertGenuinePushDay(
            resource = "bodyweight-beginner",
            equipment = listOf(StandardEquipment.BODYWEIGHT)
        )
    }

    @Test
    fun aDumbbellChestPriority_stillGetsAGenuinePushDay() = runTest {
        assertGenuinePushDay(
            resource = "mixed-unit-history",
            equipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
        )
    }

    @Test
    fun aMachineChestPriority_stillGetsAGenuinePushDay() = runTest {
        assertGenuinePushDay(
            resource = "machine-only",
            equipment = listOf(StandardEquipment.MACHINE)
        )
    }

    @Test
    fun aFullGymChestPriority_stillGetsAGenuinePushDay() = runTest {
        assertGenuinePushDay(resource = "full-gym-advanced", equipment = null)
    }

    private suspend fun assertGenuinePushDay(resource: String, equipment: List<String>?) {
        val context = legacyContext(
            resource = resource,
            equipment = equipment,
            priorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
        )
        val workout = FakeWorkoutPlanner().generateWorkout(context)
        val selected = context.resolve(workout.exercises.map { it.exerciseId })

        assertThat(workout.title.split).isEqualTo(WorkoutSplit.PUSH)
        assertWithMessage("$resource selected ${selected.map(Exercise::id)}")
            .that(selected.filter { WorkoutSplit.PUSH.trainsAsFocus(it) })
            .isNotEmpty()
        assertThat(workout.unavailableFocusMuscles).isEmpty()
    }

    // endregion

    // region every split, not only Push

    @Test
    fun aPoolThatOnlyBrushesASplit_cannotEstablishThatSplitForAnyOfThem() = runTest {
        WorkoutSplit.entries.forEach { split ->
            val brushing = catalog.first { it.id == "push-up" }.copy(
                id = "brushing-${split.name.lowercase()}",
                // Serratus belongs to no split, so nothing here trains anything as a focus.
                primaryMuscles = listOf("Serratus"),
                secondaryMuscles = split.targetMuscles
            )
            val context = WorkoutGenerationContext(
                userProfile = factory.create(fixture("bodyweight-beginner")).userProfile,
                allowedExercises = listOf(brushing)
            )

            try {
                val workout = FakeWorkoutPlanner().generateWorkout(context)
                fail("$split was advertised from secondary involvement alone: $workout")
            } catch (e: WorkoutValidationException) {
                assertWithMessage("$split")
                    .that(e.failure)
                    .isEqualTo(WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT)
            }
        }
    }

    @Test
    fun everyPriorityAcrossEverySplit_advertisesOnlyWhatItTrains() = runTest {
        val context = legacyContext(resource = "full-gym-advanced", equipment = null)
        WorkoutSplit.entries.flatMap(WorkoutSplit::targetMuscles).distinct().forEach { muscle ->
            val prioritised = context.copy(
                musclePriorities = mapOf(muscle to PriorityLevel.HIGH)
            )
            val workout = FakeWorkoutPlanner().generateWorkout(prioritised)
            val selected = prioritised.resolve(workout.exercises.map { it.exerciseId })

            assertWithMessage("$muscle produced ${workout.title.split}")
                .that(selected.any { workout.title.split.trainsAsFocus(it) })
                .isTrue()
        }
    }

    @Test
    fun anApprovedDirectPrimaryAbsentFromTheLegacyLists_stillFillsItsSplit() = runTest {
        // Reviewed metadata exists partly to correct legacy misclassification, so an
        // approved direct primary need not appear in the legacy lists at all. Fillability
        // reads the approved value; if slot eligibility read only the legacy lists, the one
        // candidate that made the split fillable would be dropped and the run would die on
        // an internal invariant instead of planning.
        val corrected = catalog.first { it.id == "push-up" }.copy(
            id = "corrected-primary",
            primaryMuscles = listOf("Serratus"),
            secondaryMuscles = listOf("Serratus"),
            reviewedMetadata = syntheticReviewedMetadata(
                reviewState = ReviewState.APPROVED,
                directPrimaryMuscle = StandardMuscles.CHEST,
                descriptiveSecondaryMuscles = setOf(StandardMuscles.TRICEPS)
            )
        )
        val context = WorkoutGenerationContext(
            userProfile = factory.create(fixture("bodyweight-beginner")).userProfile,
            allowedExercises = listOf(corrected)
        )

        val workout = FakeWorkoutPlanner().generateWorkout(context)

        assertThat(workout.title.split).isEqualTo(WorkoutSplit.PUSH)
        assertThat(workout.exercises.map { it.exerciseId }).containsExactly(corrected.id)
        assertThat(workout.focusMuscles).containsExactly(StandardMuscles.CHEST)
    }

    @Test
    fun anOversizedPriorityMap_reportsABoundedExplanation() = runTest {
        // A restored archive may carry up to 2,000 profile-supplied priority keys. The
        // rendered sentence becomes the started session's notes, which the archive itself
        // caps, so what is reported has to stay bounded.
        val context = bandOnlyContext()
        val flooded = context.copy(
            musclePriorities = (1..2_000).associate { "Unknown muscle $it" to PriorityLevel.HIGH }
        )

        val workout = FakeWorkoutPlanner().generateWorkout(flooded)

        assertThat(workout.unavailableFocusMuscles).hasSize(3)
        assertThat(workout.unavailableFocusMuscles).isInOrder()
    }

    // endregion

    // region rotation and regeneration

    @Test
    fun rotationAndRegeneration_neverReintroduceAMisleadingSplit() = runTest {
        val context = bandOnlyContext()
        repeat(WorkoutSplit.entries.size * 2) { completed ->
            val planner = FakeWorkoutPlanner()
            repeat(3) {
                val workout = planner.generateWorkout(
                    context.copy(completedWorkoutCount = completed)
                )
                val selected = context.resolve(workout.exercises.map { it.exerciseId })
                assertWithMessage("completed=$completed advertised ${workout.title.split}")
                    .that(selected.any { workout.title.split.trainsAsFocus(it) })
                    .isTrue()
            }
        }
    }

    @Test
    fun theSameStateReplaysIdentically() = runTest {
        val context = bandOnlyContext()

        val first = FakeWorkoutPlanner().generateWorkout(context)
        val second = FakeWorkoutPlanner().generateWorkout(context)

        assertThat(second.title).isEqualTo(first.title)
        assertThat(second.exercises.map { it.exerciseId })
            .isEqualTo(first.exercises.map { it.exerciseId })
        assertThat(second.unavailableFocusMuscles).isEqualTo(first.unavailableFocusMuscles)
    }

    // endregion

    private fun fixture(
        resource: String,
        equipment: List<String>? = null,
        priorities: Map<String, PriorityLevel>? = null
    ): PlannerFixture {
        val source = PlannerFixtureLoader().loadResource("planner-fixtures/$resource.json")
        return source.copy(
            profile = source.profile.copy(
                availableEquipment = equipment ?: source.profile.availableEquipment,
                musclePriorities = priorities ?: source.profile.musclePriorities,
                movementCapabilities = MovementCapabilities.from(
                    MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
                )
            ),
            completedWorkoutCount = 0,
            exerciseHistory = emptyList(),
            allowedExerciseIds = emptyList(),
            reviewedEligibility = null,
            expected = PlannerFixtureExpected(
                outcome = PlannerFixtureOutcome.SUCCESS,
                requiredExerciseIds = emptySet(),
                forbiddenExerciseIds = emptySet()
            )
        )
    }

    private fun legacyContext(
        resource: String,
        equipment: List<String>?,
        priorities: Map<String, PriorityLevel>? = null
    ): WorkoutGenerationContext {
        val built = factory.create(fixture(resource, equipment, priorities))
        assertThat(built.context.automaticEligibilityResult).isNull()
        assertThat(built.context.allowedExercises).isNotEmpty()
        return built.context
    }

    /**
     * The committed reproduction: BEGINNER, BUILD_MUSCLE, 40 minutes, four days a week,
     * Resistance Band only, Chest HIGH, every movement capability COMFORTABLE, no
     * constraints, exclusions, confirmed loads or history, and no declared required
     * pattern. Bodyweight is never added to the explicitly band-only inventory.
     */
    private fun bandOnlyContext(): WorkoutGenerationContext {
        val context = legacyContext(
            resource = "band-only",
            equipment = listOf(StandardEquipment.RESISTANCE_BAND),
            priorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
        )
        assertThat(context.availableEquipment)
            .containsExactly(StandardEquipment.RESISTANCE_BAND)
        return context
    }

    private fun WorkoutGenerationContext.resolve(ids: List<String>): List<Exercise> {
        val byId = allowedExercises.associateBy(Exercise::id)
        return ids.map { byId.getValue(it) }
    }

    private fun bodyweightRepPlan(exerciseId: String) = PlannedExercise(
        exerciseId = exerciseId,
        prescription = ExercisePrescription(
            exerciseType = ExerciseType.BODYWEIGHT_REPS,
            targetSets = 3,
            repRange = RepRange(8, 12),
            restSeconds = 75
        )
    )
}
