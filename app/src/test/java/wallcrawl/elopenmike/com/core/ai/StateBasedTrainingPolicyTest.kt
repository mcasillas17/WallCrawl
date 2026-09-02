package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.UserRestPreference
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger

class StateBasedTrainingPolicyTest {

    private val policy = StateBasedTrainingPolicy()
    private val approvedExercise = syntheticApprovedExercise(
        id = EXERCISE_ID,
        directPrimaryMuscle = DIRECT_PRIMARY,
        legacyPrimaryMuscles = listOf("Legacy muscle must not be used")
    )
    private val basePrescription = ExercisePrescription(
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetSets = 4,
        repRange = RepRange(8, 12),
        targetWeight = 80.0,
        restSeconds = 75
    )

    @Test
    fun v1Defaults_defineEveryDeclaredStateAndRestClass() {
        val defaults = StateBasedTrainingPolicyDefaults.V1

        assertThat(defaults.policyVersion)
            .isEqualTo(TrainingPolicyVersion.STATE_BASED_DOSE_EFFORT_REST_V1)
        assertThat(defaults.doseLimitsByState.keys)
            .containsExactlyElementsIn(AdaptationState.entries)
        assertThat(defaults.productRestSecondsByClass)
            .containsExactly(
                RestClass.SHORT, 60,
                RestClass.MODERATE, 90,
                RestClass.LONG, 180
            )
    }

    @Test
    fun defaults_rejectMissingStateAndOutOfRangeRestSeconds() {
        val defaults = StateBasedTrainingPolicyDefaults.V1

        assertThrows(IllegalArgumentException::class.java) {
            defaults.copy(
                doseLimitsByState =
                    defaults.doseLimitsByState - AdaptationState.RECALIBRATE
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            defaults.copy(
                productRestSecondsByClass =
                    defaults.productRestSecondsByClass + (RestClass.LONG to 1_801)
            )
        }
    }

    @Test
    fun everyState_hasTheDeclaredDoseAndEffortBehavior() {
        val expected = mapOf(
            AdaptationState.UNCALIBRATED to ExpectedStateGuidance(2, EffortTarget(2, 4)),
            AdaptationState.INITIATE to ExpectedStateGuidance(2, EffortTarget(2, 4)),
            AdaptationState.BUILD to ExpectedStateGuidance(4, EffortTarget(1, 3)),
            AdaptationState.DEVELOP to ExpectedStateGuidance(4, EffortTarget(1, 3)),
            AdaptationState.HOLD to ExpectedStateGuidance(2, EffortTarget(2, 4)),
            AdaptationState.RETURNING to ExpectedStateGuidance(2, EffortTarget(2, 4)),
            AdaptationState.DELOAD_OFFERED to ExpectedStateGuidance(2, EffortTarget(2, 4)),
            AdaptationState.RECALIBRATE to ExpectedStateGuidance(2, EffortTarget(2, 4))
        )

        expected.forEach { (state, expectedGuidance) ->
            val applied = evaluate(adaptationState = state).requireApplied()

            assertWithMessage("target sets for %s", state)
                .that(applied.prescription.targetSets)
                .isEqualTo(expectedGuidance.targetSets)
            assertWithMessage("effort for %s", state)
                .that(applied.prescription.effortTarget)
                .isEqualTo(expectedGuidance.effortTarget)
        }

        assertThat(evaluate(adaptationState = AdaptationState.NEEDS_ONBOARDING))
            .isEqualTo(
                TrainingPolicyResult.NoGuidance(
                    TrainingPolicyNoGuidanceReason.PROFILE_NOT_READY
                )
            )
    }

    @Test
    fun emptyAndPartialLedgers_capOnlyByRemainingDirectPrimaryAllowance() {
        val empty = evaluate(
            adaptationState = AdaptationState.BUILD,
            directPrimarySets = emptyMap()
        ).requireApplied()
        val partial = evaluate(
            adaptationState = AdaptationState.BUILD,
            directPrimarySets = mapOf(DIRECT_PRIMARY to 10)
        ).requireApplied()

        assertThat(empty.prescription.targetSets).isEqualTo(4)
        assertThat(partial.prescription.targetSets).isEqualTo(2)
        assertThat(partial.reasons)
            .contains(TrainingPolicyReason.WEEKLY_DIRECT_PRIMARY_ALLOWANCE)
    }

    @Test
    fun exactOverAndOverflowSizedExposure_returnTypedExhaustionWithoutArithmeticOverflow() {
        listOf(12, 13, Int.MAX_VALUE).forEach { existingSets ->
            assertWithMessage("existing direct-primary sets: %s", existingSets)
                .that(
                    evaluate(
                        adaptationState = AdaptationState.BUILD,
                        directPrimarySets = mapOf(DIRECT_PRIMARY to existingSets)
                    )
                )
                .isEqualTo(
                    TrainingPolicyResult.NoGuidance(
                        TrainingPolicyNoGuidanceReason
                            .WEEKLY_DIRECT_PRIMARY_ALLOWANCE_EXHAUSTED
                    )
                )
        }
    }

    @Test
    fun malformedLedger_returnsTypedFailure() {
        val negativePrimary = evaluate(
            directPrimarySets = mapOf(DIRECT_PRIMARY to -1)
        )
        val tooManyPrimaryKeys = evaluate(
            directPrimarySets = (0..64).associate { "Muscle $it" to 1 }
        )
        val negativeSecondary = evaluate(
            ledgerTransform = {
                it.copy(secondaryInvolvement = mapOf("Secondary" to -1))
            }
        )

        listOf(negativePrimary, tooManyPrimaryKeys, negativeSecondary).forEach { result ->
            assertThat(result).isEqualTo(
                TrainingPolicyResult.Failure(
                    TrainingPolicyFailureReason.MALFORMED_WEEKLY_LEDGER
                )
            )
        }
    }

    @Test
    fun noWeeklyMinimumAndNoAutomaticIncrement_leaveASmallerBaseUnchanged() {
        val oneSetBase = basePrescription.copy(targetSets = 1)

        val applied = evaluate(
            adaptationState = AdaptationState.BUILD,
            base = oneSetBase,
            directPrimarySets = emptyMap()
        ).requireApplied()

        assertThat(applied.prescription.targetSets).isEqualTo(1)
    }

    @Test
    fun onlyApprovedDirectPrimaryMetadata_participatesInDose() {
        val applied = evaluate(
            adaptationState = AdaptationState.BUILD,
            directPrimarySets = mapOf(
                DIRECT_PRIMARY to 11,
                "Legacy muscle must not be used" to Int.MAX_VALUE
            )
        ).requireApplied()

        assertThat(applied.prescription.targetSets).isEqualTo(1)
    }

    @Test
    fun missingAndDraftMetadata_failClosedWithoutLegacyFallback() {
        val approvedMetadata = requireNotNull(approvedExercise.reviewedMetadata)
        val missing = evaluate(exercise = approvedExercise.copy(reviewedMetadata = null))
        val draft = evaluate(
            exercise = approvedExercise.copy(
                reviewedMetadata = approvedMetadata.copy(
                    reviewState = ReviewState.DRAFT,
                    provenance = approvedMetadata.provenance.copy(
                        reviewerRole = null,
                        reviewedAtEpochMillis = null
                    )
                )
            )
        )

        listOf(missing, draft).forEach { result ->
            assertThat(result).isEqualTo(
                TrainingPolicyResult.Failure(
                    TrainingPolicyFailureReason.MISSING_APPROVED_METADATA
                )
            )
        }
    }

    @Test
    fun malformedApprovedProvenance_failsClosed() {
        val malformed = approvedExercise.withMetadata {
            copy(provenance = provenance.copy(reviewerRole = " "))
        }

        assertThat(evaluate(exercise = malformed)).isEqualTo(
            TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.MALFORMED_APPROVED_METADATA
            )
        )
    }

    @Test
    fun mismatchedReviewPolicyVersion_failsClosed() {
        assertThat(
            evaluate(ledgerTransform = { it.copy(reviewPolicyVersion = 2) })
        ).isEqualTo(
            TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.REVIEW_POLICY_VERSION_MISMATCH
            )
        )
    }

    @Test
    fun mismatchedExerciseAndReviewedPrescriptionShape_failsClosed() {
        val mismatched = approvedExercise.withMetadata {
            copy(prescriptionShape = PrescriptionShape.DURATION)
        }

        assertThat(evaluate(exercise = mismatched)).isEqualTo(
            TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.PRESCRIPTION_SHAPE_MISMATCH
            )
        )
    }

    @Test
    fun relevantLimitedCapability_capsSetsAndUsesConservativeEffort() {
        val limitedExercise = approvedExercise.withMetadata {
            copy(capabilityRequirements = setOf(MovementCapabilityType.IMPACT))
        }
        val limitedProfile = UserProfile(
            goals = setOf(FitnessGoal.BUILD_MUSCLE),
            movementCapabilities = MovementCapabilities.from(
                mapOf(MovementCapabilityType.IMPACT to CapabilityLevel.LIMITED)
            )
        )

        val applied = evaluate(
            exercise = limitedExercise,
            profile = limitedProfile,
            adaptationState = AdaptationState.BUILD
        ).requireApplied()

        assertThat(applied.prescription.targetSets).isEqualTo(2)
        assertThat(applied.prescription.effortTarget).isEqualTo(EffortTarget(2, 4))
        assertThat(applied.reasons)
            .contains(TrainingPolicyReason.RELEVANT_LIMITED_CAPABILITY)
    }

    @Test
    fun relevantAvoidCapability_failsIfACallerBypassesHardEligibility() {
        val avoidedExercise = approvedExercise.withMetadata {
            copy(capabilityRequirements = setOf(MovementCapabilityType.IMPACT))
        }
        val avoidedProfile = UserProfile(
            goals = setOf(FitnessGoal.BUILD_MUSCLE),
            movementCapabilities = MovementCapabilities.from(
                mapOf(MovementCapabilityType.IMPACT to CapabilityLevel.AVOID)
            )
        )

        assertThat(
            evaluate(
                exercise = avoidedExercise,
                profile = avoidedProfile,
                adaptationState = AdaptationState.BUILD
            )
        ).isEqualTo(
            TrainingPolicyResult.Failure(
                TrainingPolicyFailureReason.CAPABILITY_AVOID_REACHED_POLICY
            )
        )
    }

    @Test
    fun unknownCapability_doesNotMasqueradeAsLimited() {
        val capabilityExercise = approvedExercise.withMetadata {
            copy(capabilityRequirements = setOf(MovementCapabilityType.IMPACT))
        }

        val applied = evaluate(
            exercise = capabilityExercise,
            profile = UserProfile(goals = setOf(FitnessGoal.BUILD_MUSCLE)),
            adaptationState = AdaptationState.BUILD
        ).requireApplied()

        assertThat(applied.prescription.targetSets).isEqualTo(4)
        assertThat(applied.prescription.effortTarget).isEqualTo(EffortTarget(1, 3))
        assertThat(applied.reasons)
            .doesNotContain(TrainingPolicyReason.RELEVANT_LIMITED_CAPABILITY)
    }

    @Test
    fun establishedStrength_usesEditableLowRirProductDefault() {
        val applied = evaluate(
            profile = UserProfile(goals = setOf(FitnessGoal.STRENGTH)),
            adaptationState = AdaptationState.BUILD
        ).requireApplied()

        assertThat(applied.prescription.effortTarget).isEqualTo(EffortTarget(1, 2))
        assertThat(applied.reasons)
            .contains(TrainingPolicyReason.ESTABLISHED_STRENGTH_EFFORT)
    }

    @Test
    fun unsupportedEstablishedEffortCombination_remainsNull() {
        val applied = evaluate(
            profile = UserProfile(goals = setOf(FitnessGoal.FAT_LOSS)),
            adaptationState = AdaptationState.BUILD
        ).requireApplied()

        assertThat(applied.prescription.effortTarget).isNull()
    }

    @Test
    fun automaticEffortGuidance_neverContainsZeroRir() {
        AdaptationState.entries
            .filterNot { it == AdaptationState.NEEDS_ONBOARDING }
            .forEach { state ->
                val effort = evaluate(adaptationState = state)
                    .requireApplied()
                    .prescription
                    .effortTarget
                effort?.let {
                    assertThat(it.minRir).isGreaterThan(0)
                    assertThat(it.maxRir).isGreaterThan(0)
                }
            }
    }

    @Test
    fun restClassesResolveFromApprovedShapePatternAndGoals() {
        val durationExercise = approvedExercise.copy(type = ExerciseType.DURATION)
            .withMetadata {
                copy(
                    movementPattern = MovementPattern.CORE,
                    prescriptionShape = PrescriptionShape.DURATION
                )
            }
        val durationBase = ExercisePrescription(
            exerciseType = ExerciseType.DURATION,
            targetSets = 3,
            targetDurationSeconds = 45
        )
        val isolation = approvedExercise.withMetadata {
            copy(movementPattern = MovementPattern.ISOLATION)
        }

        val duration = evaluate(
            exercise = durationExercise,
            base = durationBase
        ).requireApplied().prescription
        val isolationResult = evaluate(exercise = isolation)
            .requireApplied().prescription
        val moderate = evaluate().requireApplied().prescription
        val long = evaluate(
            profile = UserProfile(goals = setOf(FitnessGoal.STRENGTH))
        ).requireApplied().prescription

        assertRest(duration, RestClass.SHORT, 60)
        assertRest(isolationResult, RestClass.SHORT, 60)
        assertRest(moderate, RestClass.MODERATE, 90)
        assertRest(long, RestClass.LONG, 180)
    }

    @Test
    fun explicitUserRestPreference_winsClassAndExactSeconds() {
        val preference = UserRestPreference(RestClass.LONG, restSeconds = 240)

        val applied = evaluate(priorUserRestPreference = preference).requireApplied()

        assertRest(
            prescription = applied.prescription,
            expectedClass = RestClass.LONG,
            expectedSeconds = 240,
            expectedSource = RestTargetSource.USER_PREFERENCE
        )
        assertThat(applied.reasons)
            .contains(TrainingPolicyReason.USER_REST_PREFERENCE)
        assertThat(applied.reasons)
            .doesNotContain(TrainingPolicyReason.PRODUCT_REST_DEFAULT)
    }

    @Test
    fun policyNeverChangesLoadRepDurationDistanceOrExerciseType() {
        AdaptationState.entries
            .filterNot { it == AdaptationState.NEEDS_ONBOARDING }
            .forEach { state ->
                val applied = evaluate(adaptationState = state).requireApplied().prescription

                assertThat(applied.exerciseType).isEqualTo(basePrescription.exerciseType)
                assertThat(applied.repRange).isEqualTo(basePrescription.repRange)
                assertThat(applied.targetWeight).isEqualTo(basePrescription.targetWeight)
                assertThat(applied.targetAssistanceWeight)
                    .isEqualTo(basePrescription.targetAssistanceWeight)
                assertThat(applied.targetDurationSeconds)
                    .isEqualTo(basePrescription.targetDurationSeconds)
                assertThat(applied.targetDistanceMeters)
                    .isEqualTo(basePrescription.targetDistanceMeters)
            }
    }

    @Test
    fun trainingFrequency_doesNotChangeDose() {
        val twiceWeekly = evaluate(
            profile = UserProfile(
                goals = setOf(FitnessGoal.BUILD_MUSCLE),
                daysPerWeek = 2
            )
        )
        val sixTimesWeekly = evaluate(
            profile = UserProfile(
                goals = setOf(FitnessGoal.BUILD_MUSCLE),
                daysPerWeek = 6
            )
        )

        assertThat(twiceWeekly).isEqualTo(sixTimesWeekly)
    }

    @Test
    fun equalInputs_replayEqualResultsWithStableReasonOrdering() {
        val exercise = approvedExercise.withMetadata {
            copy(capabilityRequirements = setOf(MovementCapabilityType.IMPACT))
        }
        val profile = UserProfile(
            goals = setOf(FitnessGoal.BUILD_MUSCLE),
            movementCapabilities = MovementCapabilities.from(
                mapOf(MovementCapabilityType.IMPACT to CapabilityLevel.LIMITED)
            )
        )
        val preference = UserRestPreference(RestClass.LONG, 240)

        fun result() = evaluate(
            exercise = exercise,
            profile = profile,
            adaptationState = AdaptationState.RETURNING,
            directPrimarySets = mapOf(DIRECT_PRIMARY to 5),
            priorUserRestPreference = preference
        )

        assertThat(result()).isEqualTo(result())
        assertThat(result().requireApplied().reasons).containsExactly(
            TrainingPolicyReason.STATE_DOSE_CAP,
            TrainingPolicyReason.RELEVANT_LIMITED_CAPABILITY,
            TrainingPolicyReason.WEEKLY_DIRECT_PRIMARY_ALLOWANCE,
            TrainingPolicyReason.CONSERVATIVE_EFFORT,
            TrainingPolicyReason.USER_REST_PREFERENCE
        ).inOrder()
    }

    private fun evaluate(
        exercise: Exercise = approvedExercise,
        base: ExercisePrescription = basePrescription,
        profile: UserProfile = UserProfile(goals = setOf(FitnessGoal.BUILD_MUSCLE)),
        adaptationState: AdaptationState = AdaptationState.BUILD,
        directPrimarySets: Map<String, Int> = emptyMap(),
        priorUserRestPreference: UserRestPreference? = null,
        ledgerTransform: (WeeklyDoseLedger) -> WeeklyDoseLedger = { it }
    ): TrainingPolicyResult {
        val ledger = ledgerTransform(
            WeeklyDoseLedger(
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
        return policy.evaluate(
            exercise = exercise,
            basePrescription = base,
            profile = profile,
            fitnessGoals = profile.goals,
            programState = TrainingProgramState(
                policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
                adaptationState = adaptationState,
                weeklyLedger = ledger
            ),
            priorUserRestPreference = priorUserRestPreference
        )
    }

    private fun TrainingPolicyResult.requireApplied(): TrainingPolicyResult.Applied {
        assertThat(this).isInstanceOf(TrainingPolicyResult.Applied::class.java)
        return this as TrainingPolicyResult.Applied
    }

    private fun Exercise.withMetadata(
        transform: wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata.() ->
            wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
    ): Exercise = copy(reviewedMetadata = requireNotNull(reviewedMetadata).transform())

    private fun assertRest(
        prescription: ExercisePrescription,
        expectedClass: RestClass,
        expectedSeconds: Int,
        expectedSource: RestTargetSource = RestTargetSource.PRODUCT_POLICY
    ) {
        assertThat(prescription.restClass).isEqualTo(expectedClass)
        assertThat(prescription.restSeconds).isEqualTo(expectedSeconds)
        assertThat(prescription.restTargetSource)
            .isEqualTo(expectedSource)
    }

    private data class ExpectedStateGuidance(
        val targetSets: Int,
        val effortTarget: EffortTarget
    )

    private companion object {
        const val EXERCISE_ID = "synthetic-bench-press"
        const val DIRECT_PRIMARY = "Chest"
    }
}
