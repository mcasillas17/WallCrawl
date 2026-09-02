package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseAttribution
import wallcrawl.elopenmike.com.core.model.ExerciseAttributionSource
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata
import wallcrawl.elopenmike.com.core.model.ExerciseSource
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.ProgressionType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.UserRestPreference
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

class PlannerFixtureTest {

    private val loader = PlannerFixtureLoader()
    private val evaluator = PlannerFixtureEvaluator(loader = loader)
    private val prescriptionFactory = DefaultExercisePrescriptionFactory()

    @Test
    fun evaluateCorpus_runsEveryPlannerFixture() = runTest {
        val evaluations = evaluator.evaluateCorpus()

        assertThat(evaluations.map { it.built.fixture.id }).containsExactly(
            "bodyweight-beginner",
            "band-only",
            "machine-only",
            "full-gym-advanced",
            "returning-user",
            "limited-capability",
            "mixed-unit-history",
            "sparse-history",
            "no-strength-candidates",
            "reviewed-enabled-bodyweight",
            "reviewed-enabled-no-approved"
        )
    }

    @Test
    fun evaluateCorpus_enforcesDeterminismAndPlannerInvariants() = runTest {
        val evaluations = evaluator.evaluateCorpus()

        evaluations.forEach { evaluation ->
            assertThat(evaluation.inputAfterFirstAttempt).isEqualTo(evaluation.inputBefore)
            assertThat(evaluation.inputAfterSecondAttempt).isEqualTo(evaluation.inputBefore)

            when (evaluation) {
                is PlannerFixtureSuccessEvaluation -> assertSuccessfulFixture(evaluation)
                is PlannerFixtureFailureEvaluation -> assertFailureFixture(evaluation)
            }
        }
    }

    @Test
    fun bodyweightBeginnerPersona_keepsExperienceRankingSoftAndInventsNoLoad() = runTest {
        val fixture = loader.loadCorpus().single { it.id == "bodyweight-beginner" }
        val evaluation = evaluator.evaluateFixture(fixture) as PlannerFixtureSuccessEvaluation
        val catalogById = evaluation.built.catalogExercises.associateBy(Exercise::id)
        val selectedIds = evaluation.firstWorkout.exercises.map { it.exerciseId }
        val allowedIds = evaluation.built.context.allowedExercises.map(Exercise::id).toSet()
        val difficultyPolicy = ExerciseDifficultyRankingPolicy()

        assertThat(evaluation.built.userProfile.experienceLevel)
            .isEqualTo(ExperienceLevel.BEGINNER)
        assertThat(allowedIds.containsAll(selectedIds)).isTrue()
        evaluation.firstWorkout.exercises.forEach { generated ->
            val exercise = checkNotNull(catalogById[generated.exerciseId])
            assertThat(
                difficultyPolicy.aboveExperiencePenalty(
                    exercise = exercise,
                    experienceLevel = ExperienceLevel.BEGINNER,
                    reviewedEligibilityEnabled = false
                )
            ).isEqualTo(0)
            assertThat(generated.prescription.targetWeight).isNull()
        }
    }

    @Test
    fun fullGymAdvancedPersona_preservesLegalPoolAndExistingLoadRules() = runTest {
        val fixture = loader.loadCorpus().single { it.id == "full-gym-advanced" }
        val evaluation = evaluator.evaluateFixture(fixture) as PlannerFixtureSuccessEvaluation
        val catalogById = evaluation.built.catalogExercises.associateBy(Exercise::id)
        val selectedIds = evaluation.firstWorkout.exercises.map { it.exerciseId }
        val allowedIds = evaluation.built.context.allowedExercises.map(Exercise::id).toSet()
        val difficultyPolicy = ExerciseDifficultyRankingPolicy()

        assertThat(evaluation.built.userProfile.experienceLevel)
            .isEqualTo(ExperienceLevel.ADVANCED)
        assertThat(allowedIds.containsAll(selectedIds)).isTrue()
        assertThat(
            evaluation.built.context.allowedExercises.any {
                it.programming?.difficulty == Difficulty.ADVANCED
            }
        ).isTrue()
        evaluation.firstWorkout.exercises.forEach { generated ->
            val exercise = checkNotNull(catalogById[generated.exerciseId])
            assertThat(
                difficultyPolicy.aboveExperiencePenalty(
                    exercise = exercise,
                    experienceLevel = ExperienceLevel.ADVANCED,
                    reviewedEligibilityEnabled = false
                )
            ).isEqualTo(0)
            if (
                exercise.type == ExerciseType.WEIGHT_REPS &&
                exercise.id !in evaluation.built.context.exerciseHistory &&
                exercise.id !in evaluation.built.userProfile.confirmedStartingLoads
            ) {
                assertThat(generated.prescription.targetWeight).isNull()
            }
        }
        assertThat(
            evaluation.firstWorkout.exercises.single {
                it.exerciseId == "barbell-bench-press"
            }.prescription.targetWeight
        ).isEqualTo(185.0)
    }

    @Test
    fun limitedCapabilityFixture_matchesAllComfortableCapabilitiesControl() = runTest {
        val limitedFixture = loader.loadCorpus().single { it.id == "limited-capability" }
        val comfortableControl = limitedFixture.copy(
            profile = limitedFixture.profile.copy(
                movementCapabilities = MovementCapabilities.from(
                    MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
                )
            )
        )

        val limited = evaluator.evaluateFixture(limitedFixture) as PlannerFixtureSuccessEvaluation
        val control = evaluator.evaluateFixture(comfortableControl) as PlannerFixtureSuccessEvaluation

        assertThat(limited.firstWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(control.firstWorkout.normalizedPlannerFixtureWorkout())
        assertThat(limited.secondWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(control.secondWorkout.normalizedPlannerFixtureWorkout())
    }

    @Test
    fun reviewedEnabledPersona_consumesProgramStateWithoutHardRuleOrLoadRegression() = runTest {
        val fixture = loader.loadCorpus().single { it.id == "reviewed-enabled-bodyweight" }
        val evaluation = evaluator.evaluateFixture(fixture) as PlannerFixtureSuccessEvaluation
        val context = evaluation.built.context
        val allowedIds = context.allowedExercises.map(Exercise::id).toSet()

        assertThat(context.trainingProgramState).isNotNull()
        assertThat(
            context.trainingProgramState?.weeklyLedger?.directPrimarySets
        ).isEmpty()
        evaluation.firstWorkout.exercises.forEach { generated ->
            assertThat(allowedIds).contains(generated.exerciseId)
            assertThat(generated.prescription.effortTarget).isEqualTo(EffortTarget(1, 3))
            assertThat(generated.prescription.restClass).isNotNull()
            assertThat(generated.prescription.restTargetSource)
                .isEqualTo(RestTargetSource.PRODUCT_POLICY)
            assertThat(generated.prescription.targetWeight).isNull()
        }
        assertThat(evaluation.firstWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(evaluation.secondWorkout.normalizedPlannerFixtureWorkout())
    }

    @Test
    fun corpusMetadata_usesSupportedVersionsAndBoundedHistory() {
        val fixtures = loader.loadCorpus()

        assertThat(fixtures).hasSize(11)
        fixtures.forEach { fixture ->
            assertThat(fixture.schemaVersion).isEqualTo(1)
            assertThat(fixture.policyVersion).isEqualTo(3)
            assertThat(fixture.catalogVersion)
                .isEqualTo("ba0b709cb20430361b2cb33aaadd20998164a916")
            assertThat(fixture.exerciseHistory.size).isAtMost(8)
        }
    }

    @Test
    fun plannerFixtureInputSnapshot_capturesEveryWorkoutGenerationContextField() {
        val snapshotFieldNames = PlannerFixtureInputSnapshot::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
        val contextFieldNames = WorkoutGenerationContext::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }

        assertThat(snapshotFieldNames).containsExactlyElementsIn(contextFieldNames)
    }

    @Test
    fun plannerFixtureInputSnapshot_deepCopiesEveryMutableContextBranch() {
        val context = snapshotProbeContext()

        val snapshot = snapshotOf(context)
        val snapshotUserProfile = readField(snapshot, "userProfile")
        val snapshotRecentWorkoutHistory = readField(snapshot, "recentWorkoutHistory") as List<*>
        val snapshotExerciseHistory = readField(snapshot, "exerciseHistory") as Map<*, *>
        val snapshotAllowedExercises = readField(snapshot, "allowedExercises") as List<*>
        val snapshotRestPreferences =
            readField(snapshot, "priorUserRestPreferences") as Map<*, *>

        assertThat(snapshotUserProfile).isEqualTo(context.userProfile)
        assertThat(snapshotUserProfile).isNotSameInstanceAs(context.userProfile)
        assertDistinctNestedUserProfileCollections(snapshotUserProfile, context.userProfile)

        assertThat(readField(snapshot, "fitnessGoals")).isEqualTo(context.fitnessGoals)
        assertThat(readField(snapshot, "fitnessGoals")).isNotSameInstanceAs(context.fitnessGoals)
        assertThat(readField(snapshot, "availableEquipment")).isEqualTo(context.availableEquipment)
        assertThat(readField(snapshot, "availableEquipment")).isNotSameInstanceAs(context.availableEquipment)
        assertThat(readField(snapshot, "musclePriorities")).isEqualTo(context.musclePriorities)
        assertThat(readField(snapshot, "musclePriorities")).isNotSameInstanceAs(context.musclePriorities)
        assertThat(snapshotRecentWorkoutHistory).isEqualTo(context.recentWorkoutHistory)
        assertThat(snapshotRecentWorkoutHistory).isNotSameInstanceAs(context.recentWorkoutHistory)
        assertThat(snapshotExerciseHistory).isEqualTo(context.exerciseHistory)
        assertThat(snapshotExerciseHistory).isNotSameInstanceAs(context.exerciseHistory)
        assertThat(readField(snapshot, "recentlyTrainedMuscles")).isEqualTo(context.recentlyTrainedMuscles)
        assertThat(readField(snapshot, "recentlyTrainedMuscles")).isNotSameInstanceAs(context.recentlyTrainedMuscles)
        assertThat(readField(snapshot, "excludedExerciseIds")).isEqualTo(context.excludedExerciseIds)
        assertThat(readField(snapshot, "excludedExerciseIds")).isNotSameInstanceAs(context.excludedExerciseIds)
        assertThat(snapshotAllowedExercises).isEqualTo(context.allowedExercises)
        assertThat(snapshotAllowedExercises).isNotSameInstanceAs(context.allowedExercises)
        assertThat(snapshotRestPreferences).isEqualTo(context.priorUserRestPreferences)
        assertThat(snapshotRestPreferences)
            .isNotSameInstanceAs(context.priorUserRestPreferences)

        val snapshotSession = snapshotRecentWorkoutHistory.single() as WorkoutSession
        val sourceSession = context.recentWorkoutHistory.single()
        assertThat(snapshotSession).isNotSameInstanceAs(sourceSession)
        assertThat(snapshotSession.focusMuscles).isNotSameInstanceAs(sourceSession.focusMuscles)
        assertThat(snapshotSession.exercises).isNotSameInstanceAs(sourceSession.exercises)
        assertThat(snapshotSession.exercises.single()).isNotSameInstanceAs(sourceSession.exercises.single())
        assertThat(snapshotSession.exercises.single().sets).isNotSameInstanceAs(sourceSession.exercises.single().sets)
        assertThat(snapshotSession.exercises.single().sets.single())
            .isNotSameInstanceAs(sourceSession.exercises.single().sets.single())

        @Suppress("UNCHECKED_CAST")
        val snapshotHistory = (snapshotExerciseHistory as Map<String, *>)
            .getValue("incline-dumbbell-press") as ExercisePerformanceHistory
        val sourceHistory = context.exerciseHistory.getValue("incline-dumbbell-press")
        assertThat(snapshotHistory).isNotSameInstanceAs(sourceHistory)
        assertThat(snapshotHistory.recentSets).isNotSameInstanceAs(sourceHistory.recentSets)
        assertThat(snapshotHistory.recentSets.single()).isNotSameInstanceAs(sourceHistory.recentSets.single())

        val snapshotExercise = snapshotAllowedExercises.single() as Exercise
        val sourceExercise = context.allowedExercises.single()
        assertThat(snapshotExercise).isNotSameInstanceAs(sourceExercise)
        assertThat(snapshotExercise.source).isNotSameInstanceAs(sourceExercise.source)
        assertThat(snapshotExercise.source!!.attribution).isNotSameInstanceAs(sourceExercise.source!!.attribution)
        assertThat(snapshotExercise.source!!.attribution.source)
            .isNotSameInstanceAs(sourceExercise.source!!.attribution.source)
        assertThat(snapshotExercise.searchAliases).isNotSameInstanceAs(sourceExercise.searchAliases)
        assertThat(snapshotExercise.primaryMuscles).isNotSameInstanceAs(sourceExercise.primaryMuscles)
        assertThat(snapshotExercise.secondaryMuscles).isNotSameInstanceAs(sourceExercise.secondaryMuscles)
        assertThat(snapshotExercise.listedEquipment).isNotSameInstanceAs(sourceExercise.listedEquipment)
        assertThat(snapshotExercise.programming).isNotSameInstanceAs(sourceExercise.programming)
        assertThat(snapshotExercise.programming!!.requiredEquipmentCombinations)
            .isNotSameInstanceAs(sourceExercise.programming!!.requiredEquipmentCombinations)
        assertThat(snapshotExercise.programming!!.requiredEquipmentCombinations.single())
            .isNotSameInstanceAs(sourceExercise.programming!!.requiredEquipmentCombinations.single())
        assertThat(snapshotExercise.programming!!.alternativeExerciseIds)
            .isNotSameInstanceAs(sourceExercise.programming!!.alternativeExerciseIds)
    }

    private fun assertSuccessfulFixture(evaluation: PlannerFixtureSuccessEvaluation) {
        val normalizedFirst = evaluation.firstWorkout.normalizedPlannerFixtureWorkout()
        val normalizedSecond = evaluation.secondWorkout.normalizedPlannerFixtureWorkout()
        assertThat(normalizedFirst).isEqualTo(normalizedSecond)

        val selectedIds = normalizedFirst.exercises.map { it.exerciseId }
        val allowedIds = evaluation.built.context.allowedExercises.map(Exercise::id).toSet()
        val filteredIds = evaluation.built.filteredExercises.map(Exercise::id).toSet()
        val catalogById = evaluation.built.catalogExercises.associateBy(Exercise::id)
        val excludedIds = evaluation.built.userProfile.excludedExerciseIds.toSet()

        assertThat(selectedIds).isNotEmpty()
        assertThat(allowedIds.containsAll(selectedIds)).isTrue()
        assertThat(filteredIds.containsAll(selectedIds)).isTrue()
        assertThat(excludedIds.intersect(selectedIds.toSet())).isEmpty()

        if (evaluation.built.fixture.expected.requiredExerciseIds.isNotEmpty()) {
            evaluation.built.fixture.expected.requiredExerciseIds.forEach { requiredId ->
                assertThat(selectedIds).contains(requiredId)
            }
        }
        if (evaluation.built.fixture.expected.forbiddenExerciseIds.isNotEmpty()) {
            evaluation.built.fixture.expected.forbiddenExerciseIds.forEach { forbiddenId ->
                assertThat(selectedIds).doesNotContain(forbiddenId)
            }
        }
        readRequiredAnyExerciseIdGroups(evaluation.built.fixture.expected).forEach { group ->
            assertThat(selectedIds.any { it in group }).isTrue()
        }

        val expectedTargetWeights = readExpectedTargetWeights(evaluation.built.fixture.expected)
        expectedTargetWeights.forEach { (exerciseId, expectedWeight) ->
            val generated = normalizedFirst.exercises.singleOrNull { it.exerciseId == exerciseId }
            assertThat(generated).isNotNull()
            assertThat(generated!!.prescription.targetWeight).isEqualTo(expectedWeight)
        }
        readWorkoutNameContains(evaluation.built.fixture.expected)?.let { expectedFragment ->
            assertThat(normalizedFirst.name).contains(expectedFragment)
        }
        readMaxTargetSetsPerExercise(evaluation.built.fixture.expected)?.let { maxTargetSets ->
            normalizedFirst.exercises.forEach { generated ->
                assertThat(generated.prescription.targetSets).isAtMost(maxTargetSets)
            }
        }
        if (evaluation.built.fixture.id == "bodyweight-beginner") {
            normalizedFirst.exercises.forEach { generated ->
                val catalogExercise = checkNotNull(catalogById[generated.exerciseId])
                assertThat(hasSatisfiedEquipment(catalogExercise, listOf(StandardEquipment.BODYWEIGHT))).isTrue()
            }
        }

        normalizedFirst.exercises.forEach { generated ->
            val catalogExercise = checkNotNull(catalogById[generated.exerciseId])
            val expectedPrescription = prescriptionFactory.create(catalogExercise, evaluation.built.context)

            assertThat(generated.prescription.exerciseType).isEqualTo(catalogExercise.type)
            assertThat(
                generated.prescription.copy(targetWeight = null)
            ).isEqualTo(
                expectedPrescription.copy(targetWeight = null)
            )

            when (catalogExercise.type) {
                ExerciseType.WEIGHT_REPS -> {
                    val history = evaluation.built.context.exerciseHistory[catalogExercise.id]
                    val confirmedLoad = evaluation.built.userProfile.confirmedStartingLoads[catalogExercise.id]
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                    if (history == null && confirmedLoad == null) {
                        assertThat(generated.prescription.targetWeight).isNull()
                    }
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.BODYWEIGHT_REPS -> {
                    assertThat(generated.prescription.repRange).isNotNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.ASSISTED_BODYWEIGHT -> {
                    assertThat(generated.prescription.repRange).isNotNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.DURATION -> {
                    assertThat(generated.prescription.repRange).isNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNotNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.DISTANCE_DURATION -> {
                    assertThat(generated.prescription.repRange).isNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(
                        generated.prescription.targetDurationSeconds != null ||
                            generated.prescription.targetDistanceMeters != null
                    ).isTrue()
                }
            }
        }
    }

    private fun assertFailureFixture(evaluation: PlannerFixtureFailureEvaluation) {
        val expectedFailure = when (evaluation.built.fixture.expected.outcome) {
            PlannerFixtureOutcome.SUCCESS -> error("Expected failure fixture but found success")
            PlannerFixtureOutcome.NO_CANDIDATES -> WorkoutPlanningFailure.NO_CANDIDATES
            PlannerFixtureOutcome.NO_STRENGTH_CANDIDATES ->
                WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES
            PlannerFixtureOutcome.NO_CANDIDATES_FOR_ANY_SPLIT ->
                WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT
            PlannerFixtureOutcome.REVIEWED_ELIGIBILITY_NO_CANDIDATES ->
                WorkoutPlanningFailure.REVIEWED_ELIGIBILITY_NO_CANDIDATES
        }

        assertThat(evaluation.firstFailure).isEqualTo(expectedFailure)
        assertThat(evaluation.secondFailure).isEqualTo(expectedFailure)
        assertThat(evaluation.firstAutomaticEligibilityFailure)
            .isEqualTo(evaluation.built.fixture.expected.automaticEligibilityFailure)
        assertThat(evaluation.secondAutomaticEligibilityFailure)
            .isEqualTo(evaluation.built.fixture.expected.automaticEligibilityFailure)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readExpectedTargetWeights(expected: Any): Map<String, Double> {
        val getter = expected.javaClass.methods.singleOrNull {
            it.name == "getExpectedTargetWeights" && it.parameterCount == 0
        } ?: error("PlannerFixtureExpected must expose expectedTargetWeights.")
        val value = getter.invoke(expected) as Map<*, *>
        return value.mapKeys { it.key as String }.mapValues { (_, weight) -> (weight as Number).toDouble() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readRequiredAnyExerciseIdGroups(expected: Any): List<Set<String>> {
        val getter = expected.javaClass.methods.singleOrNull {
            it.name == "getRequiredAnyExerciseIdGroups" && it.parameterCount == 0
        } ?: error("PlannerFixtureExpected must expose requiredAnyExerciseIdGroups.")
        val value = getter.invoke(expected) as List<*>
        return value.map { group ->
            (group as Set<*>).map { it as String }.toSet()
        }
    }

    private fun readWorkoutNameContains(expected: Any): String? {
        val getter = expected.javaClass.methods.singleOrNull {
            it.name == "getWorkoutNameContains" && it.parameterCount == 0
        } ?: error("PlannerFixtureExpected must expose workoutNameContains.")
        return getter.invoke(expected) as String?
    }

    private fun readMaxTargetSetsPerExercise(expected: Any): Int? {
        val getter = expected.javaClass.methods.singleOrNull {
            it.name == "getMaxTargetSetsPerExercise" && it.parameterCount == 0
        } ?: error("PlannerFixtureExpected must expose maxTargetSetsPerExercise.")
        return getter.invoke(expected) as Int?
    }

    private fun hasSatisfiedEquipment(exercise: Exercise, ownedEquipment: List<String>): Boolean {
        val owned = ownedEquipment.toSet()
        val combinations = exercise.programming?.requiredEquipmentCombinations
            ?: listOf(exercise.listedEquipment.filter(String::isNotBlank))
        return combinations.isEmpty() || combinations.any { combination ->
            combination.all { it in owned }
        }
    }

    private fun snapshotOf(context: WorkoutGenerationContext): Any {
        val method = Class.forName("wallcrawl.elopenmike.com.core.ai.PlannerFixtureEvaluatorKt")
            .getDeclaredMethod("snapshot", WorkoutGenerationContext::class.java)
        method.isAccessible = true
        return checkNotNull(method.invoke(null, context))
    }

    private fun readField(instance: Any, name: String): Any {
        val field = instance.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return checkNotNull(field.get(instance))
    }

    private fun assertDistinctNestedUserProfileCollections(snapshot: Any, source: UserProfile) {
        assertThat(readField(snapshot, "goals")).isNotSameInstanceAs(source.goals)
        assertThat(readField(snapshot, "availableEquipment")).isNotSameInstanceAs(source.availableEquipment)
        assertThat(readField(snapshot, "musclePriorities")).isNotSameInstanceAs(source.musclePriorities)
        assertThat(readField(snapshot, "excludedExerciseIds")).isNotSameInstanceAs(source.excludedExerciseIds)
        assertThat(readField(snapshot, "trainingConstraints")).isNotSameInstanceAs(source.trainingConstraints)
        assertThat(readField(snapshot, "confirmedStartingLoads")).isNotSameInstanceAs(source.confirmedStartingLoads)
        val snapshotCapabilities = readField(snapshot, "movementCapabilities")
        assertThat(snapshotCapabilities).isNotSameInstanceAs(source.movementCapabilities)
        assertThat(readField(snapshotCapabilities, "values")).isNotSameInstanceAs(source.movementCapabilities.values)
    }

    private fun snapshotProbeContext(): WorkoutGenerationContext {
        val workingSet = WorkoutSet(
            id = "history-set-1",
            workoutExerciseId = "history-exercise-1",
            setNumber = 1,
            exerciseType = ExerciseType.WEIGHT_REPS,
            targetReps = 10,
            completedReps = 10,
            targetWeight = 27.5,
            completedWeight = 27.5,
            isCompleted = true
        )
        val sessionExercise = WorkoutExercise(
            id = "session-exercise-1",
            sessionId = "session-1",
            exerciseId = "incline-dumbbell-press",
            orderIndex = 0,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                targetWeight = 27.5,
                restSeconds = 90
            ),
            sets = listOf(workingSet)
        )
        val recentSession = WorkoutSession(
            id = "session-1",
            name = "Upper Body · Hypertrophy",
            status = SessionStatus.COMPLETED,
            completedAtTimestamp = 1234L,
            focusMuscles = listOf(StandardMuscles.CHEST, StandardMuscles.BACK),
            exercises = listOf(sessionExercise)
        )
        val allowedExercise = Exercise(
            id = "incline-dumbbell-press",
            source = ExerciseSource(
                catalogId = UUID.randomUUID().toString(),
                sourceId = "exercise-incline-dumbbell-press",
                sourceSlug = "incline-dumbbell-press",
                attribution = ExerciseAttribution(
                    creator = "Fixture Creator",
                    creatorUrl = "https://example.com/creator",
                    license = "CC BY",
                    licenseUrl = "https://example.com/license",
                    source = ExerciseAttributionSource(
                        name = "Fixture Catalog",
                        url = "https://example.com/source",
                        license = "CC BY",
                        licenseUrl = "https://example.com/source-license",
                        changes = "Normalized for tests"
                    )
                )
            ),
            name = "Incline Dumbbell Press",
            searchAliases = listOf("incline press"),
            primaryMuscles = listOf(StandardMuscles.CHEST),
            secondaryMuscles = listOf(StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS),
            listedEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
            type = ExerciseType.WEIGHT_REPS,
            programming = ExerciseProgrammingMetadata(
                requiredEquipmentCombinations = listOf(
                    listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
                ),
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.COMPOUND,
                recommendedRepRange = RepRange(8, 12),
                fatigueScore = 3,
                progressionType = ProgressionType.REPETITIONS_THEN_LOAD,
                alternativeExerciseIds = listOf("dumbbell-bench-press"),
                coachingSummary = "Drive through the upper chest."
            )
        )
        val profile = UserProfile(
            goals = linkedSetOf(FitnessGoal.GENERAL_FITNESS, FitnessGoal.BUILD_MUSCLE),
            experienceLevel = wallcrawl.elopenmike.com.core.model.ExperienceLevel.BEGINNER,
            preferredDurationMinutes = 45,
            daysPerWeek = 3,
            availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
            preferredUnit = WeightUnit.KG,
            musclePriorities = linkedMapOf(
                StandardMuscles.CHEST to PriorityLevel.HIGH,
                StandardMuscles.BACK to PriorityLevel.NORMAL
            ),
            excludedExerciseIds = listOf("barbell-bench-press"),
            onboardingCompleted = true,
            trainingConstraints = linkedSetOf(TrainingConstraint.LOW_IMPACT_ONLY),
            returningAfterBreakWeeks = 6,
            confirmedStartingLoads = linkedMapOf("incline-dumbbell-press" to 27.5),
            movementCapabilities = MovementCapabilities.from(
                linkedMapOf(
                    MovementCapabilityType.IMPACT to CapabilityLevel.LIMITED,
                    MovementCapabilityType.FLOOR_TRANSITION to CapabilityLevel.COMFORTABLE
                )
            )
        )
        return WorkoutGenerationContext(
            userProfile = profile,
            fitnessGoals = linkedSetOf(FitnessGoal.GENERAL_FITNESS, FitnessGoal.BUILD_MUSCLE),
            fitnessGoal = FitnessGoal.GENERAL_FITNESS,
            experienceLevel = wallcrawl.elopenmike.com.core.model.ExperienceLevel.BEGINNER,
            availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
            preferredWorkoutDurationMinutes = 45,
            trainingFrequencyDaysPerWeek = 3,
            musclePriorities = linkedMapOf(
                StandardMuscles.CHEST to PriorityLevel.HIGH,
                StandardMuscles.BACK to PriorityLevel.NORMAL
            ),
            recentWorkoutHistory = listOf(recentSession),
            completedWorkoutCount = 9,
            exerciseHistory = linkedMapOf(
                allowedExercise.id to ExercisePerformanceHistory(
                    exerciseId = allowedExercise.id,
                    lastWeight = 27.5,
                    lastReps = 10,
                    bestEstimated1RM = 34.0,
                    recentSets = listOf(workingSet)
                )
            ),
            recentlyTrainedMuscles = listOf(StandardMuscles.CHEST, StandardMuscles.TRICEPS),
            excludedExerciseIds = listOf("barbell-bench-press"),
            allowedExercises = listOf(allowedExercise),
            priorUserRestPreferences = linkedMapOf(
                allowedExercise.id to UserRestPreference(
                    restClass = wallcrawl.elopenmike.com.core.model.RestClass.LONG,
                    restSeconds = 240
                )
            ),
            preferredUnits = WeightUnit.KG
        )
    }
}
