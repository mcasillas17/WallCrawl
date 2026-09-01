package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSet

class DefaultExercisePrescriptionFactoryTest {

    private val factory = DefaultExercisePrescriptionFactory()
    private val context = WorkoutGenerationContext(
        userProfile = UserProfile(goals = setOf(FitnessGoal.GENERAL_FITNESS))
    )

    @Test
    fun noHistoryAndNoConfirmedBaseline_doesNotPrescribeLoad_inLbs() {
        // Regression guard: barbell-bench-press used to fabricate 135.0 (lbs) here.
        val benchPress = exercise(ExerciseType.WEIGHT_REPS).copy(id = "barbell-bench-press")
        val profile = UserProfile(preferredUnit = WeightUnit.LBS)

        val prescription = factory.create(
            benchPress,
            WorkoutGenerationContext(userProfile = profile)
        )

        assertThat(prescription.targetWeight).isNull()
    }

    @Test
    fun noHistoryAndNoConfirmedBaseline_doesNotPrescribeLoad_inKg() {
        // Regression guard: the old fabricated 135.0 must not resurface unit-mislabeled
        // as 135 kg either -- the unsafe default must be gone in every unit.
        val benchPress = exercise(ExerciseType.WEIGHT_REPS).copy(id = "barbell-bench-press")
        val profile = UserProfile(preferredUnit = WeightUnit.KG)

        val prescription = factory.create(
            benchPress,
            WorkoutGenerationContext(userProfile = profile)
        )

        assertThat(prescription.targetWeight).isNull()
    }

    @Test
    fun confirmedBaseline_withNonFiniteOrNegativeValue_isIgnoredRatherThanPropagated() {
        // Task 1's repository validation already rejects these before they can be
        // persisted, but the factory must not trust a WorkoutGenerationContext built
        // directly (e.g. by another caller or a test) and propagate NaN/negative math
        // into a prescription.
        val benchPress = exercise(ExerciseType.WEIGHT_REPS).copy(id = "barbell-bench-press")

        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -10.0).forEach { invalid ->
            val profile = UserProfile(
                preferredUnit = WeightUnit.LBS,
                confirmedStartingLoads = mapOf("barbell-bench-press" to invalid)
            )

            val prescription = factory.create(
                benchPress,
                WorkoutGenerationContext(userProfile = profile)
            )

            assertThat(prescription.targetWeight).isNull()
        }
    }

    @Test
    fun confirmedBaseline_isUsedInTheProfilesUnit() {
        val benchPress = exercise(ExerciseType.WEIGHT_REPS).copy(id = "barbell-bench-press")
        val profile = UserProfile(
            preferredUnit = WeightUnit.KG,
            confirmedStartingLoads = mapOf("barbell-bench-press" to 40.0)
        )

        val prescription = factory.create(
            benchPress,
            WorkoutGenerationContext(userProfile = profile)
        )

        assertThat(prescription.targetWeight).isEqualTo(40.0)
    }

    @Test
    fun priorHistory_takesPrecedenceOverConfirmedBaseline_andHoldsWeightWithNoCompletedSets() {
        val benchPress = exercise(ExerciseType.WEIGHT_REPS).copy(id = "barbell-bench-press")
        val profile = UserProfile(
            goals = setOf(FitnessGoal.GENERAL_FITNESS),
            preferredUnit = WeightUnit.KG,
            confirmedStartingLoads = mapOf("barbell-bench-press" to 40.0)
        )
        val historyContext = WorkoutGenerationContext(
            userProfile = profile,
            exerciseHistory = mapOf(
                "barbell-bench-press" to ExercisePerformanceHistory(
                    exerciseId = "barbell-bench-press",
                    lastWeight = 60.0,
                    lastReps = 8,
                    bestEstimated1RM = 75.0,
                    recentSets = emptyList()
                )
            )
        )

        val prescription = factory.create(benchPress, historyContext)

        assertThat(prescription.targetWeight).isEqualTo(60.0)
    }

    @Test
    fun historyProgression_incrementsWeightByFivePoundsWhenTopOfRepRangeWasReached_inLbs() {
        val benchPress = exercise(ExerciseType.WEIGHT_REPS).copy(id = "barbell-bench-press")
        val profile = UserProfile(
            goals = setOf(FitnessGoal.GENERAL_FITNESS),
            preferredUnit = WeightUnit.LBS
        )
        val historyContext = WorkoutGenerationContext(
            userProfile = profile,
            exerciseHistory = mapOf(
                "barbell-bench-press" to completedTopOfRangeHistory(lastWeight = 100.0)
            )
        )

        val prescription = factory.create(benchPress, historyContext)

        assertThat(prescription.targetWeight).isEqualTo(105.0)
    }

    @Test
    fun historyProgression_incrementsWeightByTwoPointFiveKilogramsWhenTopOfRepRangeWasReached_inKg() {
        val benchPress = exercise(ExerciseType.WEIGHT_REPS).copy(id = "barbell-bench-press")
        val profile = UserProfile(
            goals = setOf(FitnessGoal.GENERAL_FITNESS),
            preferredUnit = WeightUnit.KG
        )
        val historyContext = WorkoutGenerationContext(
            userProfile = profile,
            exerciseHistory = mapOf(
                "barbell-bench-press" to completedTopOfRangeHistory(lastWeight = 40.0)
            )
        )

        val prescription = factory.create(benchPress, historyContext)

        assertThat(prescription.targetWeight).isEqualTo(42.5)
    }

    /**
     * Recent completed sets that all reached the top of the general-fitness rep range
     * (12 reps) so the factory's progression logic increments rather than holds.
     */
    private fun completedTopOfRangeHistory(lastWeight: Double) = ExercisePerformanceHistory(
        exerciseId = "barbell-bench-press",
        lastWeight = lastWeight,
        lastReps = 12,
        bestEstimated1RM = lastWeight * 1.3,
        recentSets = listOf(
            WorkoutSet(
                workoutExerciseId = "workout-exercise",
                setNumber = 1,
                exerciseType = ExerciseType.WEIGHT_REPS,
                completedReps = 12,
                completedWeight = lastWeight,
                isCompleted = true
            )
        )
    )

    @Test
    fun create_returnsStructurallyValidDefaultsForEveryCatalogExerciseType() {
        val prescriptions = ExerciseType.entries.associateWith { type ->
            factory.create(exercise(type), context)
        }

        assertThat(prescriptions.getValue(ExerciseType.WEIGHT_REPS).repRange).isNotNull()
        assertThat(prescriptions.getValue(ExerciseType.BODYWEIGHT_REPS).repRange).isNotNull()
        assertThat(prescriptions.getValue(ExerciseType.ASSISTED_BODYWEIGHT).repRange).isNotNull()
        assertThat(prescriptions.getValue(ExerciseType.DURATION).targetDurationSeconds)
            .isEqualTo(45)
        assertThat(prescriptions.getValue(ExerciseType.DISTANCE_DURATION).targetDurationSeconds)
            .isEqualTo(600)
        assertThat(prescriptions.values.map { it.exerciseType })
            .containsExactlyElementsIn(ExerciseType.entries)
    }

    @Test
    fun create_usesShorterDurationAndOneSetForStretch() {
        val stretch = exercise(ExerciseType.DURATION).copy(isStretch = true)

        val prescription = factory.create(stretch, context)

        assertThat(prescription.targetSets).isEqualTo(1)
        assertThat(prescription.targetDurationSeconds).isEqualTo(30)
    }

    @Test
    fun create_hybridGoals_prescribesStrengthForCompoundsAndHypertrophyForAccessories() {
        val compoundExercise = exercise(ExerciseType.WEIGHT_REPS).copy(
            id = "barbell-bench-press",
            programming = wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata(
                requiredEquipmentCombinations = listOf(listOf(wallcrawl.elopenmike.com.core.model.StandardEquipment.BARBELL)),
                movementPattern = wallcrawl.elopenmike.com.core.model.MovementPattern.HORIZONTAL_PUSH,
                difficulty = wallcrawl.elopenmike.com.core.model.Difficulty.INTERMEDIATE,
                mechanics = wallcrawl.elopenmike.com.core.model.MechanicsType.COMPOUND,
                recommendedRepRange = wallcrawl.elopenmike.com.core.model.RepRange(4, 6),
                fatigueScore = 8,
                progressionType = wallcrawl.elopenmike.com.core.model.ProgressionType.LOAD,
                coachingSummary = "Keep bar path controlled."
            )
        )
        val isolationExercise = exercise(ExerciseType.WEIGHT_REPS).copy(
            id = "dumbbell-fly",
            programming = wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata(
                requiredEquipmentCombinations = listOf(listOf(wallcrawl.elopenmike.com.core.model.StandardEquipment.DUMBBELL)),
                movementPattern = wallcrawl.elopenmike.com.core.model.MovementPattern.HORIZONTAL_PUSH,
                difficulty = wallcrawl.elopenmike.com.core.model.Difficulty.BEGINNER,
                mechanics = wallcrawl.elopenmike.com.core.model.MechanicsType.ISOLATION,
                recommendedRepRange = wallcrawl.elopenmike.com.core.model.RepRange(8, 12),
                fatigueScore = 3,
                progressionType = wallcrawl.elopenmike.com.core.model.ProgressionType.REPETITIONS_THEN_LOAD,
                coachingSummary = "Control the stretch."
            )
        )
        val hybridProfile = UserProfile(
            goals = setOf(FitnessGoal.STRENGTH, FitnessGoal.BUILD_MUSCLE)
        )
        val hybridContext = WorkoutGenerationContext(userProfile = hybridProfile)

        val compoundPrescription = factory.create(compoundExercise, hybridContext)
        val isolationPrescription = factory.create(isolationExercise, hybridContext)

        // Compound gets heavy Strength sets & rep range
        assertThat(compoundPrescription.targetSets).isEqualTo(4)
        assertThat(compoundPrescription.repRange?.min).isEqualTo(4)
        assertThat(compoundPrescription.repRange?.max).isEqualTo(6)
        assertThat(compoundPrescription.restSeconds).isEqualTo(120)

        // Isolation gets Hypertrophy sets & rep range
        assertThat(isolationPrescription.targetSets).isEqualTo(3)
        assertThat(isolationPrescription.repRange?.min).isEqualTo(8)
        assertThat(isolationPrescription.repRange?.max).isEqualTo(12)
        assertThat(isolationPrescription.restSeconds).isEqualTo(90)
    }

    @Test
    fun create_extendedBreakOverOneYear_capsSetsToTwoAndProtectsTendons() {
        val compoundExercise = exercise(ExerciseType.WEIGHT_REPS).copy(
            id = "barbell-bench-press",
            programming = wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata(
                requiredEquipmentCombinations = listOf(listOf(wallcrawl.elopenmike.com.core.model.StandardEquipment.BARBELL)),
                movementPattern = wallcrawl.elopenmike.com.core.model.MovementPattern.HORIZONTAL_PUSH,
                difficulty = wallcrawl.elopenmike.com.core.model.Difficulty.INTERMEDIATE,
                mechanics = wallcrawl.elopenmike.com.core.model.MechanicsType.COMPOUND,
                recommendedRepRange = wallcrawl.elopenmike.com.core.model.RepRange(4, 6),
                fatigueScore = 8,
                progressionType = wallcrawl.elopenmike.com.core.model.ProgressionType.LOAD,
                coachingSummary = "Keep bar path controlled."
            )
        )
        val multiYearBreakProfile = UserProfile(
            goals = setOf(FitnessGoal.STRENGTH),
            returningAfterBreakWeeks = 104 // 2 years off
        )
        val context = WorkoutGenerationContext(userProfile = multiYearBreakProfile)

        val prescription = factory.create(compoundExercise, context)

        // Strict 2 working sets to prevent severe DOMS and protect tendons
        assertThat(prescription.targetSets).isEqualTo(2)
        // Rep range uses introductory 6–8 reps rather than heavy 4–6 grinders
        assertThat(prescription.repRange?.min).isEqualTo(6)
        assertThat(prescription.repRange?.max).isEqualTo(8)
    }

    @Test
    fun create_moderateBreak_scalesSetsConservatively() {
        val compoundExercise = exercise(ExerciseType.WEIGHT_REPS).copy(
            id = "barbell-bench-press",
            programming = wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata(
                requiredEquipmentCombinations = listOf(listOf(wallcrawl.elopenmike.com.core.model.StandardEquipment.BARBELL)),
                movementPattern = wallcrawl.elopenmike.com.core.model.MovementPattern.HORIZONTAL_PUSH,
                difficulty = wallcrawl.elopenmike.com.core.model.Difficulty.INTERMEDIATE,
                mechanics = wallcrawl.elopenmike.com.core.model.MechanicsType.COMPOUND,
                recommendedRepRange = wallcrawl.elopenmike.com.core.model.RepRange(4, 6),
                fatigueScore = 8,
                progressionType = wallcrawl.elopenmike.com.core.model.ProgressionType.LOAD,
                coachingSummary = "Keep bar path controlled."
            )
        )
        val moderateBreakProfile = UserProfile(
            goals = setOf(FitnessGoal.STRENGTH),
            returningAfterBreakWeeks = 12 // ~3 months off
        )
        val context = WorkoutGenerationContext(userProfile = moderateBreakProfile)

        val prescription = factory.create(compoundExercise, context)

        // Capped to 3 sets instead of full 4
        assertThat(prescription.targetSets).isEqualTo(3)
    }

    @Test
    fun noProgramState_returnsTheExactLegacyPrescriptionWithoutGuidance() {
        val legacy = exercise(ExerciseType.WEIGHT_REPS).copy(id = "legacy-press")

        val prescription = factory.create(
            legacy,
            WorkoutGenerationContext(
                userProfile = UserProfile(goals = setOf(FitnessGoal.GENERAL_FITNESS))
            )
        )

        assertThat(prescription.targetSets).isEqualTo(3)
        assertThat(prescription.repRange).isEqualTo(wallcrawl.elopenmike.com.core.model.RepRange(10, 12))
        assertThat(prescription.targetWeight).isNull()
        assertThat(prescription.restSeconds).isEqualTo(90)
        assertThat(prescription.effortTarget).isNull()
        assertThat(prescription.restClass).isNull()
        assertThat(prescription.restTargetSource).isNull()
    }

    @Test
    fun reviewedProgramState_appliesGuidanceWithoutChangingConfirmedLoad() {
        val exercise = syntheticApprovedExercise(
            id = "reviewed-press",
            directPrimaryMuscle = "Chest"
        )
        val profile = UserProfile(
            goals = setOf(FitnessGoal.BUILD_MUSCLE),
            confirmedStartingLoads = mapOf(exercise.id to 40.0)
        )

        val prescription = factory.create(
            exercise,
            WorkoutGenerationContext(
                userProfile = profile,
                trainingProgramState = programState(AdaptationState.RETURNING)
            )
        )

        assertThat(prescription.targetSets).isEqualTo(2)
        assertThat(prescription.targetWeight).isEqualTo(40.0)
        assertThat(prescription.effortTarget).isEqualTo(EffortTarget(2, 4))
        assertThat(prescription.restClass).isEqualTo(RestClass.MODERATE)
        assertThat(prescription.restTargetSource).isEqualTo(RestTargetSource.PRODUCT_POLICY)
    }

    @Test
    fun exhaustedReviewedDose_throwsTypedResultInsteadOfReturningZeroSets() {
        val exercise = syntheticApprovedExercise(
            id = "reviewed-press",
            directPrimaryMuscle = "Chest"
        )
        val profile = UserProfile(goals = setOf(FitnessGoal.BUILD_MUSCLE))
        val context = WorkoutGenerationContext(
            userProfile = profile,
            trainingProgramState = programState(
                adaptationState = AdaptationState.RETURNING,
                directPrimarySets = mapOf("Chest" to 6)
            )
        )

        val error = assertThrows(TrainingPolicyResultException::class.java) {
            factory.create(exercise, context)
        }

        assertThat(error.result).isEqualTo(
            TrainingPolicyResult.NoGuidance(
                TrainingPolicyNoGuidanceReason.WEEKLY_DIRECT_PRIMARY_ALLOWANCE_EXHAUSTED
            )
        )
    }

    private fun programState(
        adaptationState: AdaptationState,
        directPrimarySets: Map<String, Int> = emptyMap()
    ) = TrainingProgramState(
        policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
        adaptationState = adaptationState,
        weeklyLedger = WeeklyDoseLedger(
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            weekStartEpochDay = MONDAY_EPOCH_DAY,
            timeZoneId = "UTC",
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1,
            directPrimarySets = directPrimarySets,
            secondaryInvolvement = emptyMap(),
            unattributedWorkSets = emptyMap()
        )
    )

    private fun exercise(type: ExerciseType) = Exercise(
        id = "unreviewed-${type.name.lowercase()}",
        name = type.name,
        primaryMuscles = listOf("Core"),
        listedEquipment = listOf("Bodyweight"),
        type = type,
        programming = null
    )
}
