package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.*

class WorkoutPhasePlanningTest {
    private val pushUp = Exercise("push-up", name = "Push-Up", primaryMuscles = listOf("Chest"),
        secondaryMuscles = listOf("Shoulders"), listedEquipment = listOf("Bodyweight"),
        type = ExerciseType.BODYWEIGHT_REPS)
    private val arms = Exercise("arm-circles", name = "Arm Circles", primaryMuscles = listOf("Shoulders"),
        listedEquipment = listOf("Bodyweight"), type = ExerciseType.DURATION, isStretch = true)
    private val stretch = arms.copy(id = "cross-body-shoulder-stretch", name = "Cross-Body Shoulder Stretch")
    private val chestStretch = stretch.copy(id = "doorway-chest-stretch", primaryMuscles = listOf("Chest"),
        listedEquipment = listOf("Doorway"))
    private val bandPull = pushUp.copy(id = "band-pull-apart", primaryMuscles = listOf("Shoulders"),
        listedEquipment = listOf("Resistance Band"), secondaryMuscles = emptyList())

    @Test fun placesBothPhasesAroundWorkAndFitsTheirTime() = runTest {
        val context = context().copy(preferredWorkoutDurationMinutes = 5)
        val plan = FakeWorkoutPlanner().generateWorkout(context)

        assertThat(plan.exercises.map { it.exerciseId }).containsExactly(arms.id, pushUp.id, stretch.id).inOrder()
        assertThat(plan.exercises.map { it.prescription.purpose.name }).containsExactly("WARMUP", "ORDINARY", "COOLDOWN").inOrder()
        assertThat(WorkoutDurationEstimator.estimateSeconds(plan.exercises)).isAtMost(300)
        assertThat(plan.estimatedDurationMinutes).isEqualTo(WorkoutDurationEstimator.estimateMinutes(plan.exercises))
        assertThat(validator().validate(plan, context)).isInstanceOf(ProgramValidationResult.Valid::class.java)
        assertThat(validator().validate(plan.copy(exercises = plan.exercises.reversed()), context))
            .isInstanceOf(ProgramValidationResult.Invalid::class.java)
    }

    @Test fun considersGoalsAndEquipmentWhenChoosingAmongRelevantMovements() = runTest {
        val equipment = listOf("Bodyweight", "Resistance Band", "Doorway")
        val planner = FakeWorkoutPlanner()
        val strength = planner.generateWorkout(context(UserProfile(goals = setOf(FitnessGoal.STRENGTH), availableEquipment = equipment)))
        val athletic = planner.generateWorkout(context(UserProfile(goals = setOf(FitnessGoal.ATHLETIC_PERFORMANCE), availableEquipment = equipment)))
        assertThat(strength.exercises.first().exerciseId).isEqualTo(bandPull.id)
        assertThat(athletic.exercises.first().exerciseId).isEqualTo(arms.id)
        assertThat(strength.exercises.last().exerciseId).isEqualTo(chestStretch.id)
        assertThat(planner.generateWorkout(context()).exercises.last().exerciseId).isEqualTo(stretch.id)
    }

    @Test fun skipsExcludedAndRestrictedPhases() = runTest {
        val planner = FakeWorkoutPlanner()
        val excluded = context(UserProfile(excludedExerciseIds = listOf(arms.id)))
        assertThat(planner.generateWorkout(excluded).exercises.map { it.exerciseId })
            .containsExactly(pushUp.id, stretch.id).inOrder()
        val sensitive = context(UserProfile(trainingConstraints = setOf(TrainingConstraint.SHOULDER_SENSITIVE)))
        assertThat(planner.generateWorkout(sensitive).exercises.map { it.exerciseId }).containsExactly(pushUp.id)
    }

    @Test fun choosesRelevantMovementsAndHonorsReportedConditionsAndFloorAvoidance() = runTest {
        val exercises = PlannerFixtureContextFactory().bundledCatalogProjection().exercises
        val lower = exercises.single { it.id == "bodyweight-squat" }
        val context = context().copy(
            allowedExercises = listOf(lower),
            phaseExercises = exercises.filter(WorkoutPhasePolicy::isCatalogCandidate)
        )
        val plan = FakeWorkoutPlanner().generateWorkout(context)
        assertThat(plan.exercises.map { it.exerciseId }).containsExactly(
            "dead-bug", lower.id, "hamstring-stretch"
        ).inOrder()
        val restricted = context.copy(userProfile = UserProfile(
            reportedConditions = setOf("piriformis_syndrome"),
            movementCapabilities = MovementCapabilities.from(mapOf(MovementCapabilityType.FLOOR_TRANSITION to CapabilityLevel.AVOID))
        ))
        val filteredPlan = FakeWorkoutPlanner().generateWorkout(restricted.copy(
            automaticEligibilityResult = ExerciseEligibilityPolicy().evaluate(listOf(lower), restricted.userProfile, AdaptationState.UNCALIBRATED)
        ))
        assertThat(filteredPlan.exercises.map { it.exerciseId }).containsExactly(lower.id)
    }

    @Test fun preservesTheExactReleasedConditionPlan() = runTest {
        val context = ConditionPlannerFixtures.context().copy(phaseExercises = listOf(arms, stretch))
        val plan = FakeWorkoutPlanner().generateWorkout(context)
        assertThat(plan.exercises.map { it.prescription.purpose })
            .containsExactly(PrescriptionPurpose.ORDINARY, PrescriptionPurpose.SUPPORTIVE).inOrder()
    }

    @Test fun skipsOptionalPhaseWhenItCannotFitEvenAfterReducingWorkSets() = runTest {
        val context = context().copy(preferredWorkoutDurationMinutes = 2)
        val plan = FakeWorkoutPlanner().generateWorkout(context)
        assertThat(plan.exercises.count { it.prescription.purpose.name == "ORDINARY" }).isEqualTo(1)
        assertThat(plan.exercises.size).isLessThan(3)
        assertThat(WorkoutDurationEstimator.estimateSeconds(plan.exercises)).isAtMost(120)
    }

    private fun context(profile: UserProfile = UserProfile()) = WorkoutGenerationContext(
        userProfile = profile,
        allowedExercises = listOf(pushUp),
        phaseExercises = listOf(arms, stretch, chestStretch, bandPull),
        regenerationIndex = 0
    )

    private fun validator() = ProgramValidator(
        GeneratedWorkoutValidator(InMemoryExerciseCatalog(listOf(pushUp, arms, stretch, chestStretch, bandPull)))
    )
}
