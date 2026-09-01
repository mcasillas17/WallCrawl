package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.SupportRequirement
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FakeWorkoutPlannerTest {

    private lateinit var planner: FakeWorkoutPlanner
    private val allExercises = InMemoryExerciseCatalog.SAMPLE_EXERCISES

    @Before
    fun setup() {
        planner = FakeWorkoutPlanner()
    }

    @Test
    fun generateWorkout_onlySelectsAllowedExerciseCandidates() = runTest {
        val allowedSubset = allExercises.filter {
            it.id in listOf("incline-dumbbell-press", "parallel-bar-dips", "dumbbell-lateral-raise")
        }

        val context = WorkoutGenerationContext(
            userProfile = UserProfile(goals = setOf(FitnessGoal.BUILD_MUSCLE)),
            allowedExercises = allowedSubset
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.exercises).isNotEmpty()
        val allowedIds = allowedSubset.map { it.id }.toSet()
        assertThat(workout.exercises.all { it.exerciseId in allowedIds }).isTrue()
    }

    @Test
    fun generateWorkout_withChestPriority_generatesPushRoutine() = runTest {
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(
                goals = setOf(FitnessGoal.BUILD_MUSCLE),
                musclePriorities = mapOf(
                    StandardMuscles.CHEST to PriorityLevel.HIGH,
                    StandardMuscles.SHOULDERS to PriorityLevel.HIGH
                )
            ),
            allowedExercises = allExercises
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.name).contains("Push")
        assertThat(workout.focusMuscles).contains(StandardMuscles.CHEST)
    }

    @Test
    fun generateWorkout_fallsBackWhenThePreferredSplitCannotBeFilled() = runTest {
        // Calves is one of the priorities that maps to a single split (Legs). With no leg
        // work available, honouring it is impossible — but other splits are trainable, and
        // failing here would leave Today permanently broken since the choice is
        // deterministic: every retry would land on the same empty split.
        val legMuscles = listOf(
            StandardMuscles.QUADS,
            StandardMuscles.HAMSTRINGS,
            StandardMuscles.GLUTES,
            StandardMuscles.CALVES,
            StandardMuscles.ADDUCTORS,
            StandardMuscles.HIPS,
            StandardMuscles.LOWER_BACK
        )
        val upperBodyOnly = allExercises.filter { exercise ->
            (exercise.primaryMuscles + exercise.secondaryMuscles).none { it in legMuscles }
        }
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(
                musclePriorities = mapOf(StandardMuscles.CALVES to PriorityLevel.HIGH)
            ),
            allowedExercises = upperBodyOnly
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.exercises).isNotEmpty()
        assertThat(workout.name).doesNotContain("Legs")
    }

    @Test
    fun generateWorkout_prefersASplitTheHighPriorityMuscleBelongsTo() = runTest {
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(
                musclePriorities = mapOf(StandardMuscles.CALVES to PriorityLevel.HIGH)
            ),
            allowedExercises = allExercises
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.name).contains("Legs")
    }

    @Test
    fun generateWorkout_stillPrescribesLoadedWorkTaggedAsConditioning() = runTest {
        // A kettlebell swing carries a Cardio tag but is loaded work for reps, so sets and
        // reps mean something for it — unlike a treadmill.
        val swing = allExercises.first().copy(
            id = "kettlebell-swing",
            name = "Kettlebell Swing",
            type = ExerciseType.WEIGHT_REPS,
            isStretch = false,
            primaryMuscles = listOf(StandardMuscles.GLUTES),
            secondaryMuscles = listOf(StandardMuscles.HAMSTRINGS, StandardMuscles.CARDIO)
        )
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(
                musclePriorities = mapOf(StandardMuscles.GLUTES to PriorityLevel.HIGH)
            ),
            allowedExercises = listOf(swing)
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.exercises.map { it.exerciseId }).contains(swing.id)
    }

    @Test
    fun generateWorkout_reportsWhenEveryCandidateIsConditioning() = runTest {
        val treadmill = allExercises.first().copy(
            id = "treadmill-run",
            name = "Treadmill Run",
            type = ExerciseType.DISTANCE_DURATION,
            primaryMuscles = listOf(StandardMuscles.QUADS),
            secondaryMuscles = listOf(StandardMuscles.CARDIO)
        )
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(),
            allowedExercises = listOf(treadmill)
        )

        try {
            planner.generateWorkout(context)
            fail("Expected generation to report that nothing available is strength work")
        } catch (e: WorkoutValidationException) {
            assertThat(e.failure).isEqualTo(WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES)
        }
    }

    @Test
    fun generateWorkout_doesNotPrescribeStretchesAsTrainingSlots() = runTest {
        val stretch = allExercises.first().copy(
            id = "hamstring-stretch",
            name = "Hamstring Stretch",
            isStretch = true,
            primaryMuscles = listOf(StandardMuscles.HAMSTRINGS),
            secondaryMuscles = emptyList()
        )
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(),
            allowedExercises = allExercises + stretch
        )

        repeat(SPLIT_ROTATION_PROBE) { index ->
            val workout = planner.generateWorkout(
                context.copy(completedWorkoutCount = index)
            )
            assertThat(workout.exercises.map { it.exerciseId }).doesNotContain(stretch.id)
        }
    }

    @Test
    fun generateWorkout_failsOnlyWhenNoSplitCanBeTrained() = runTest {
        val unmatchable = allExercises.first().copy(
            id = "obscure-movement",
            primaryMuscles = listOf("Serratus"),
            secondaryMuscles = emptyList()
        )
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(),
            allowedExercises = listOf(unmatchable)
        )

        try {
            planner.generateWorkout(context)
            fail("Expected generation to fail when no split can be trained")
        } catch (e: WorkoutValidationException) {
            assertThat(e.failure).isEqualTo(WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT)
        }
    }

    @Test
    fun generateWorkout_selectedExercisesAlwaysTrainTheChosenSplit() = runTest {
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(
                musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
            ),
            allowedExercises = allExercises
        )

        val workout = planner.generateWorkout(context)

        val pushMuscles = listOf(
            StandardMuscles.CHEST,
            StandardMuscles.SHOULDERS,
            StandardMuscles.TRICEPS
        )
        val selected = workout.exercises.map { generated ->
            allExercises.single { it.id == generated.exerciseId }
        }
        assertThat(selected).isNotEmpty()
        selected.forEach { exercise ->
            assertThat(
                (exercise.primaryMuscles + exercise.secondaryMuscles).any { it in pushMuscles }
            ).isTrue()
        }
    }

    @Test
    fun generateWorkout_leadsWithTheMostDemandingCompoundThatTrainsTheSplit() = runTest {
        // Taking compounds in catalog order took them alphabetically, which led a push day
        // with an accessory while the heaviest press in the pool went unused.
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(
                musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
            ),
            allowedExercises = allExercises
        )

        val workout = planner.generateWorkout(context)

        val first = allExercises.single { it.id == workout.exercises.first().exerciseId }
        assertThat(first.programming?.mechanics).isEqualTo(MechanicsType.COMPOUND)
        assertThat(first.primaryMuscles.any { it in listOf(
            StandardMuscles.CHEST, StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS
        ) }).isTrue()
        val fatigueOfFirst = first.programming?.fatigueScore ?: 0
        val heaviestAvailable = allExercises
            .filter { it.programming?.mechanics == MechanicsType.COMPOUND }
            .filter { exercise ->
                exercise.primaryMuscles.any { it in listOf(
                    StandardMuscles.CHEST, StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS
                ) }
            }
            .maxOf { it.programming?.fatigueScore ?: 0 }
        assertThat(fatigueOfFirst).isEqualTo(heaviestAvailable)
    }

    @Test
    fun generateWorkout_spreadsCompoundSlotsAcrossMovementPatterns() = runTest {
        // Pattern variety outranks raw fatigue for the second slot: given two heavy
        // horizontal presses and one lighter vertical press, the vertical press is taken
        // before the second horizontal one, so the session is not the same lift twice.
        val bench = allExercises.single { it.id == "barbell-bench-press" }
        val heavyPress = bench.copy(
            id = "heavy-horizontal-press",
            programming = bench.programming!!.copy(fatigueScore = 5)
        )
        val lighterPress = bench.copy(
            id = "lighter-horizontal-press",
            programming = bench.programming!!.copy(fatigueScore = 4)
        )
        val verticalPress = bench.copy(
            id = "vertical-press",
            programming = bench.programming!!.copy(
                movementPattern = MovementPattern.VERTICAL_PUSH,
                fatigueScore = 3
            )
        )
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(
                preferredDurationMinutes = 60,
                musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
            ),
            allowedExercises = listOf(heavyPress, lighterPress, verticalPress)
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.exercises.map { it.exerciseId })
            .containsExactly("heavy-horizontal-press", "vertical-press", "lighter-horizontal-press")
            .inOrder()
    }

    @Test
    fun generateWorkout_beginnerOrdersOtherwiseEqualCompoundsByDifficulty() = runTest {
        val candidates = listOf(
            rankingExercise("a-advanced-press", Difficulty.ADVANCED),
            rankingExercise("b-intermediate-press", Difficulty.INTERMEDIATE),
            rankingExercise("c-beginner-press", Difficulty.BEGINNER)
        )

        val workout = planner.generateWorkout(
            rankingContext(ExperienceLevel.BEGINNER, candidates)
        )

        assertThat(workout.exercises.map { it.exerciseId })
            .containsExactly(
                "c-beginner-press",
                "b-intermediate-press",
                "a-advanced-press"
            )
            .inOrder()
    }

    @Test
    fun generateWorkout_intermediateDemotesOnlyAdvancedCompounds() = runTest {
        val candidates = listOf(
            rankingExercise("a-advanced-press", Difficulty.ADVANCED),
            rankingExercise("b-intermediate-press", Difficulty.INTERMEDIATE),
            rankingExercise("c-beginner-press", Difficulty.BEGINNER)
        )

        val workout = planner.generateWorkout(
            rankingContext(ExperienceLevel.INTERMEDIATE, candidates)
        )

        assertThat(workout.exercises.map { it.exerciseId })
            .containsExactly(
                "b-intermediate-press",
                "c-beginner-press",
                "a-advanced-press"
            )
            .inOrder()
    }

    @Test
    fun generateWorkout_advancedPreservesFatigueThenIdCompoundOrdering() = runTest {
        val candidates = listOf(
            rankingExercise("a-high-advanced", Difficulty.ADVANCED, fatigueScore = 5),
            rankingExercise("b-low-beginner", Difficulty.BEGINNER, fatigueScore = 1),
            rankingExercise("c-middle-intermediate", Difficulty.INTERMEDIATE, fatigueScore = 3)
        )

        val workout = planner.generateWorkout(
            rankingContext(ExperienceLevel.ADVANCED, candidates)
        )

        assertThat(workout.exercises.map { it.exerciseId })
            .containsExactly(
                "a-high-advanced",
                "c-middle-intermediate",
                "b-low-beginner"
            )
            .inOrder()
    }

    @Test
    fun generateWorkout_beginnerStillSelectsSoleAdvancedCandidate() = runTest {
        val advanced = rankingExercise("sole-advanced-press", Difficulty.ADVANCED)

        val workout = planner.generateWorkout(
            rankingContext(ExperienceLevel.BEGINNER, listOf(advanced))
        )

        assertThat(workout.exercises.map { it.exerciseId })
            .containsExactly(advanced.id)
    }

    @Test
    fun generateWorkout_beginnerUsesDifficultyForAccessoryOrdering() = runTest {
        val compound = rankingExercise(
            id = "compound-press",
            difficulty = Difficulty.BEGINNER,
            fatigueScore = 5
        )
        val candidates = listOf(
            compound,
            rankingExercise(
                id = "a-advanced-accessory",
                difficulty = Difficulty.ADVANCED,
                mechanics = MechanicsType.ISOLATION
            ),
            rankingExercise(
                id = "b-intermediate-accessory",
                difficulty = Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.ISOLATION
            ),
            rankingExercise(
                id = "c-beginner-accessory",
                difficulty = Difficulty.BEGINNER,
                mechanics = MechanicsType.ISOLATION
            )
        )

        val workout = planner.generateWorkout(
            rankingContext(ExperienceLevel.BEGINNER, candidates)
        )

        assertThat(workout.exercises.map { it.exerciseId })
            .containsExactly(
                "compound-press",
                "c-beginner-accessory",
                "b-intermediate-accessory",
                "a-advanced-accessory"
            )
            .inOrder()
    }

    @Test
    fun generateWorkout_reviewedModeUsesApprovedComplexityOverLegacyDifficulty() = runTest {
        val reviewedAdvanced = rankingExercise(
            id = "a-reviewed-advanced",
            difficulty = Difficulty.BEGINNER,
            reviewedState = ReviewState.APPROVED,
            reviewedComplexity = ComplexityTier.ADVANCED
        )
        val reviewedFoundational = rankingExercise(
            id = "z-reviewed-foundational",
            difficulty = Difficulty.ADVANCED,
            reviewedState = ReviewState.APPROVED,
            reviewedComplexity = ComplexityTier.FOUNDATIONAL
        )
        val candidates = listOf(reviewedAdvanced, reviewedFoundational)

        val workout = planner.generateWorkout(
            rankingContext(
                experienceLevel = ExperienceLevel.BEGINNER,
                candidates = candidates,
                reviewedEligibilityEnabled = true
            )
        )

        assertThat(workout.exercises.map { it.exerciseId })
            .containsExactly(reviewedFoundational.id, reviewedAdvanced.id)
            .inOrder()
    }

    @Test
    fun generateWorkout_legacyModeIgnoresDraftComplexity() = runTest {
        val draftFoundational = rankingExercise(
            id = "a-draft-foundational",
            difficulty = Difficulty.ADVANCED,
            reviewedState = ReviewState.DRAFT,
            reviewedComplexity = ComplexityTier.FOUNDATIONAL
        )
        val draftAdvanced = rankingExercise(
            id = "z-draft-advanced",
            difficulty = Difficulty.BEGINNER,
            reviewedState = ReviewState.DRAFT,
            reviewedComplexity = ComplexityTier.ADVANCED
        )

        val workout = planner.generateWorkout(
            rankingContext(
                experienceLevel = ExperienceLevel.BEGINNER,
                candidates = listOf(draftFoundational, draftAdvanced)
            )
        )

        assertThat(workout.exercises.map { it.exerciseId })
            .containsExactly(draftAdvanced.id, draftFoundational.id)
            .inOrder()
    }

    @Test
    fun generateWorkout_softDifficultyRankingPreservesAllowedCandidateMembership() = runTest {
        val candidates = listOf(
            rankingExercise("advanced-compound", Difficulty.ADVANCED),
            rankingExercise(
                "beginner-accessory",
                Difficulty.BEGINNER,
                mechanics = MechanicsType.ISOLATION
            ),
            rankingExercise(
                "intermediate-accessory",
                Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.ISOLATION
            )
        )

        val workout = planner.generateWorkout(
            rankingContext(ExperienceLevel.BEGINNER, candidates)
        )

        assertThat(workout.exercises.map { it.exerciseId })
            .containsExactlyElementsIn(candidates.map { it.id })
    }

    @Test
    fun generateWorkout_withEmptyAllowedCandidates_throwsException() = runTest {
        val emptyContext = WorkoutGenerationContext(
            userProfile = UserProfile(),
            allowedExercises = emptyList()
        )

        try {
            planner.generateWorkout(emptyContext)
            fail("Expected WorkoutValidationException for empty allowed candidates")
        } catch (e: WorkoutValidationException) {
            assertThat(e.message).contains("no allowed candidate exercises")
        }
    }

    @Test
    fun generateWorkout_withReviewedGateNoCandidates_preservesTypedFailureWithoutFallback() = runTest {
        val reviewedFailure = AutomaticEligibilityFailure.NO_APPROVED_METADATA
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(),
            allowedExercises = emptyList(),
            automaticEligibilityResult = AutomaticEligibilityResult.NoCandidates(
                failure = reviewedFailure,
                decisions = emptyList()
            )
        )

        try {
            planner.generateWorkout(context)
            fail("Expected reviewed eligibility to stop planning without a legacy fallback")
        } catch (e: WorkoutValidationException) {
            assertThat(e.failure)
                .isEqualTo(WorkoutPlanningFailure.REVIEWED_ELIGIBILITY_NO_CANDIDATES)
            assertThat(e.automaticEligibilityFailure).isEqualTo(reviewedFailure)
        }
    }

    @Test
    fun generateWorkout_whenRecentSetsReachTopOfRange_increasesPriorWeight() = runTest {
        val inclinePress = allExercises.single { it.id == "incline-dumbbell-press" }
        val recentSets = (1..3).map { setNumber ->
            WorkoutSet(
                id = "set-$setNumber",
                workoutExerciseId = "incline-instance",
                setNumber = setNumber,
                targetReps = 10,
                completedReps = 10,
                targetWeight = 45.0,
                completedWeight = 45.0,
                isCompleted = true
            )
        }
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(goals = setOf(FitnessGoal.BUILD_MUSCLE)),
            exerciseHistory = mapOf(
                inclinePress.id to ExercisePerformanceHistory(
                    exerciseId = inclinePress.id,
                    lastWeight = 45.0,
                    lastReps = 10,
                    bestEstimated1RM = 60.0,
                    recentSets = recentSets
                )
            ),
            allowedExercises = listOf(inclinePress)
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.exercises.single().targetWeight).isEqualTo(50.0)
    }

    @Test
    fun generateWorkout_withUnreviewedCandidate_usesCatalogTypeDefaults() = runTest {
        val unreviewed = allExercises.first().copy(programming = null)
        val context = WorkoutGenerationContext(
            userProfile = UserProfile(),
            allowedExercises = listOf(unreviewed)
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.exercises.single().exerciseId).isEqualTo(unreviewed.id)
        assertThat(workout.exercises.single().prescription.exerciseType).isEqualTo(unreviewed.type)
    }

    @Test
    fun generateWorkout_reviewStateDoesNotChangeCurrentPlannerOutput() = runTest {
        val exercise = allExercises.single { it.id == "incline-dumbbell-press" }
        val withoutReviewedMetadata = exercise.copy(reviewedMetadata = null)
        val draft = exercise.copy(reviewedMetadata = reviewedMetadata(ReviewState.DRAFT))
        val approved = exercise.copy(reviewedMetadata = reviewedMetadata(ReviewState.APPROVED))
        val profile = UserProfile(
            goals = setOf(FitnessGoal.BUILD_MUSCLE),
            musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
        )

        suspend fun generate(candidate: wallcrawl.elopenmike.com.core.model.Exercise) =
            FakeWorkoutPlanner().generateWorkout(
                WorkoutGenerationContext(
                    userProfile = profile,
                    allowedExercises = listOf(candidate)
                )
            )

        val baseline = generate(withoutReviewedMetadata)
        val fromDraft = generate(draft)
        val fromApproved = generate(approved)

        assertThat(fromDraft.copy(id = baseline.id)).isEqualTo(baseline)
        assertThat(fromApproved.copy(id = baseline.id)).isEqualTo(baseline)
    }

    @Test
    fun generateWorkout_reviewedMetadataPreservesRepresentativeEquipmentOutputs() = runTest {
        val bodyweight = allExercises.filter { exercise ->
            exercise.id in setOf("pull-ups", "parallel-bar-dips", "hanging-leg-raise")
        }
        val band = allExercises.map { exercise ->
            exercise.copy(
                id = "band-${exercise.id}",
                listedEquipment = listOf(StandardEquipment.RESISTANCE_BAND),
                programming = exercise.programming?.copy(
                    requiredEquipmentCombinations = listOf(
                        listOf(StandardEquipment.RESISTANCE_BAND)
                    )
                )
            )
        }
        val machine = allExercises.map { exercise ->
            exercise.copy(
                id = "machine-${exercise.id}",
                listedEquipment = listOf(StandardEquipment.MACHINE),
                programming = exercise.programming?.copy(
                    requiredEquipmentCombinations = listOf(listOf(StandardEquipment.MACHINE))
                )
            )
        }
        val contexts = mapOf(
            "bodyweight" to bodyweight,
            "band" to band,
            "machine" to machine,
            "full-gym" to allExercises
        )
        val profile = UserProfile(
            goals = setOf(FitnessGoal.BUILD_MUSCLE),
            musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
        )

        contexts.values.forEach { candidates ->
            val baseline = FakeWorkoutPlanner().generateWorkout(
                WorkoutGenerationContext(
                    userProfile = profile,
                    allowedExercises = candidates.map { it.copy(reviewedMetadata = null) }
                )
            )
            val withDraftMetadata = FakeWorkoutPlanner().generateWorkout(
                WorkoutGenerationContext(
                    userProfile = profile,
                    allowedExercises = candidates.map { exercise ->
                        exercise.copy(reviewedMetadata = reviewedMetadataFor(exercise))
                    }
                )
            )

            assertThat(withDraftMetadata.copy(id = baseline.id)).isEqualTo(baseline)
        }
    }

    @Test
    fun generateWorkout_withMultipleGoals_generatesHybridTitleAndRationale() = runTest {
        val hybridProfile = UserProfile(
            goals = setOf(FitnessGoal.STRENGTH, FitnessGoal.BUILD_MUSCLE),
            musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
        )
        val context = WorkoutGenerationContext(
            userProfile = hybridProfile,
            allowedExercises = allExercises
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.name).contains("Power & Hypertrophy")
        assertThat(workout.rationale).contains("Strength + Build Muscle")
    }

    @Test
    fun generateWorkout_withMultiYearBreak_generatesReentryTitleAndProtectiveRationale() = runTest {
        val reEntryProfile = UserProfile(
            goals = setOf(FitnessGoal.BUILD_MUSCLE),
            returningAfterBreakWeeks = 104, // 2 years off
            musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
        )
        val context = WorkoutGenerationContext(
            userProfile = reEntryProfile,
            allowedExercises = allExercises
        )

        val workout = planner.generateWorkout(context)

        assertThat(workout.name).contains("(Re-entry)")
        assertThat(workout.rationale).contains("Re-entry Ramp-Up Active")
        assertThat(workout.rationale).contains("Volume is capped at 2 sets")
        assertThat(workout.rationale).contains("1–2 Years")
    }

    @Test
    fun generateWorkout_movementCapabilitiesDoNotChangeCurrentRecommendation() = runTest {
        val baselineProfile = UserProfile(
            goals = setOf(FitnessGoal.STRENGTH, FitnessGoal.BUILD_MUSCLE),
            musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
        )
        val comfortable = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith {
                CapabilityLevel.COMFORTABLE
            }
        )
        val avoid = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith {
                CapabilityLevel.AVOID
            }
        )
        val comfortableContext = WorkoutGenerationContext(
            userProfile = baselineProfile.copy(movementCapabilities = comfortable),
            allowedExercises = allExercises
        )
        val avoidContext = comfortableContext.copy(
            userProfile = baselineProfile.copy(movementCapabilities = avoid)
        )

        val comfortableWorkout = FakeWorkoutPlanner().generateWorkout(comfortableContext)
        val avoidWorkout = FakeWorkoutPlanner().generateWorkout(avoidContext)

        assertThat(avoidWorkout.copy(id = comfortableWorkout.id)).isEqualTo(comfortableWorkout)
    }

    private companion object {
        /** Enough generations to walk every split in the rotation. */
        const val SPLIT_ROTATION_PROBE = 6
    }

    private fun rankingContext(
        experienceLevel: ExperienceLevel,
        candidates: List<Exercise>,
        reviewedEligibilityEnabled: Boolean = false
    ): WorkoutGenerationContext {
        val profile = UserProfile(
            experienceLevel = experienceLevel,
            preferredDurationMinutes = 60,
            musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH)
        )
        return WorkoutGenerationContext(
            userProfile = profile,
            allowedExercises = candidates,
            automaticEligibilityResult = if (reviewedEligibilityEnabled) {
                AutomaticEligibilityResult.Candidates(
                    exercises = candidates,
                    decisions = emptyList()
                )
            } else {
                null
            }
        )
    }

    private fun rankingExercise(
        id: String,
        difficulty: Difficulty,
        mechanics: MechanicsType = MechanicsType.COMPOUND,
        fatigueScore: Int = 3,
        reviewedState: ReviewState? = null,
        reviewedComplexity: ComplexityTier = ComplexityTier.STANDARD
    ): Exercise {
        val base = allExercises.single { it.id == "barbell-bench-press" }
        return base.copy(
            id = id,
            name = id,
            primaryMuscles = listOf(StandardMuscles.CHEST),
            secondaryMuscles = emptyList(),
            programming = checkNotNull(base.programming).copy(
                difficulty = difficulty,
                mechanics = mechanics,
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                fatigueScore = fatigueScore
            ),
            reviewedMetadata = reviewedState?.let {
                reviewedMetadata(
                    reviewState = it,
                    complexity = reviewedComplexity
                )
            }
        )
    }

    private fun reviewedMetadata(
        reviewState: ReviewState,
        complexity: ComplexityTier = ComplexityTier.STANDARD
    ): ReviewedExerciseMetadata =
        ReviewedExerciseMetadata(
            reviewState = reviewState,
            directPrimaryMuscle = StandardMuscles.CHEST,
            descriptiveSecondaryMuscles = setOf(StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            complexity = complexity,
            progressionFamily = "dumbbell-horizontal-push",
            prescriptionShape = PrescriptionShape.WEIGHT_REPS,
            approvedRegressions = emptyList(),
            approvedSubstitutions = emptyList(),
            capabilityRequirements = emptySet(),
            supportRequirement = SupportRequirement.SUPPORTED,
            impactLevel = ImpactLevel.NONE,
            equipmentAlternatives = listOf(listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)),
            provenance = ReviewProvenance(
                reviewerRole = if (reviewState == ReviewState.APPROVED) {
                    "Test-only role; not human approval"
                } else {
                    null
                },
                rationaleOrSource = "Planner invariance fixture; not authored catalog approval.",
                reviewedAtEpochMillis = if (reviewState == ReviewState.APPROVED) 1L else null,
                schemaVersion = 1,
                policyVersion = 1
            )
        )

    private fun reviewedMetadataFor(exercise: Exercise): ReviewedExerciseMetadata =
        reviewedMetadata(ReviewState.DRAFT).copy(
            directPrimaryMuscle = exercise.primaryMuscles.first(),
            descriptiveSecondaryMuscles = exercise.secondaryMuscles.toSet(),
            movementPattern = exercise.programming?.movementPattern ?: MovementPattern.OTHER,
            prescriptionShape = when (exercise.type) {
                ExerciseType.WEIGHT_REPS -> PrescriptionShape.WEIGHT_REPS
                ExerciseType.BODYWEIGHT_REPS -> PrescriptionShape.BODYWEIGHT_REPS
                ExerciseType.ASSISTED_BODYWEIGHT -> PrescriptionShape.ASSISTED_BODYWEIGHT
                ExerciseType.DURATION -> PrescriptionShape.DURATION
                ExerciseType.DISTANCE_DURATION -> PrescriptionShape.DURATION
            },
            equipmentAlternatives = exercise.programming?.requiredEquipmentCombinations
                ?: listOf(exercise.listedEquipment.ifEmpty { listOf(StandardEquipment.BODYWEIGHT) })
        )
}
