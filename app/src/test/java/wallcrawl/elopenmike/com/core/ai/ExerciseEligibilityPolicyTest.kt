package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.EligibilityDecision
import wallcrawl.elopenmike.com.core.model.EligibilityPreference
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseLink
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.SupportRequirement
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile

class ExerciseEligibilityPolicyTest {

    private val policy = ExerciseEligibilityPolicy()

    @Test
    fun evaluate_rejectsExerciseWithoutApprovedMetadata() {
        val exercise = exercise(id = "missing-reviewed-metadata")

        val result = policy.evaluate(
            exercises = listOf(exercise),
            profile = UserProfile(availableEquipment = listOf(StandardEquipment.BODYWEIGHT)),
            adaptationState = AdaptationState.UNCALIBRATED
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.NoCandidates(
                failure = AutomaticEligibilityFailure.NO_APPROVED_METADATA,
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.MISSING_APPROVED_METADATA)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_rejectsDraftMetadata() {
        val exercise = exercise(id = "draft").copy(
            reviewedMetadata = reviewedMetadata(reviewState = ReviewState.DRAFT)
        )

        val result = policy.evaluate(
            exercises = listOf(exercise),
            profile = UserProfile(availableEquipment = listOf(StandardEquipment.BODYWEIGHT)),
            adaptationState = AdaptationState.UNCALIBRATED
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.NoCandidates(
                failure = AutomaticEligibilityFailure.NO_APPROVED_METADATA,
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.MISSING_APPROVED_METADATA)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_requiresOneCompleteReviewedEquipmentAlternative() {
        val exercise = exercise(id = "equipment-alternatives").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                equipmentAlternatives = listOf(
                    listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
                    listOf(StandardEquipment.KETTLEBELL)
                )
            )
        )

        val incompleteAlternative = policy.evaluate(
            exercises = listOf(exercise),
            profile = UserProfile(availableEquipment = listOf(StandardEquipment.DUMBBELL)),
            adaptationState = AdaptationState.BUILD
        )
        val completeAlternative = policy.evaluate(
            exercises = listOf(exercise),
            profile = UserProfile(availableEquipment = listOf(StandardEquipment.KETTLEBELL)),
            adaptationState = AdaptationState.BUILD
        )

        assertThat(incompleteAlternative).isEqualTo(
            AutomaticEligibilityResult.NoCandidates(
                failure = AutomaticEligibilityFailure.EQUIPMENT_REMOVED_ALL,
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.MISSING_EQUIPMENT)
                    )
                )
            )
        )
        assertThat(completeAlternative).isEqualTo(
            AutomaticEligibilityResult.Candidates(
                exercises = listOf(exercise),
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = true,
                        reasons = listOf(EligibilityReason.APPROVED)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_rejectsExplicitlyExcludedExerciseEvenWhenApproved() {
        val exercise = exercise(id = "excluded").copy(
            reviewedMetadata = reviewedMetadata(reviewState = ReviewState.APPROVED)
        )

        val result = policy.evaluate(
            exercises = listOf(exercise),
            profile = UserProfile(
                availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
                excludedExerciseIds = listOf(exercise.id)
            ),
            adaptationState = AdaptationState.BUILD
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.NoCandidates(
                failure = AutomaticEligibilityFailure.USER_EXCLUSIONS_REMOVED_ALL,
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.USER_EXCLUDED)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_rejectsAvoidOnlyWhenCapabilityIsExplicitlyRequired() {
        val required = exercise(id = "requires-floor-transition").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                capabilityRequirements = setOf(MovementCapabilityType.FLOOR_TRANSITION)
            )
        )
        val unrelated = exercise(id = "does-not-require-floor-transition").copy(
            reviewedMetadata = reviewedMetadata(reviewState = ReviewState.APPROVED)
        )
        val profile = UserProfile(
            availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
            movementCapabilities = MovementCapabilities.from(
                mapOf(MovementCapabilityType.FLOOR_TRANSITION to CapabilityLevel.AVOID)
            )
        )

        val result = policy.evaluate(
            exercises = listOf(required, unrelated),
            profile = profile,
            adaptationState = AdaptationState.BUILD
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.Candidates(
                exercises = listOf(unrelated),
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = required.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.CAPABILITY_AVOID)
                    ),
                    EligibilityDecision(
                        exerciseId = unrelated.id,
                        eligible = true,
                        reasons = listOf(EligibilityReason.APPROVED)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_retainsLimitedAndUnknownRequirementsAsOrderedSoftPreferences() {
        val exercise = exercise(id = "soft-capabilities").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                capabilityRequirements = linkedSetOf(
                    MovementCapabilityType.BALANCE_WITHOUT_SUPPORT,
                    MovementCapabilityType.FLOOR_TRANSITION
                )
            )
        )
        val profile = UserProfile(
            availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
            movementCapabilities = MovementCapabilities.from(
                mapOf(MovementCapabilityType.FLOOR_TRANSITION to CapabilityLevel.LIMITED)
            )
        )

        val result = policy.evaluate(
            exercises = listOf(exercise),
            profile = profile,
            adaptationState = AdaptationState.BUILD
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.Candidates(
                exercises = listOf(exercise),
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = true,
                        reasons = listOf(EligibilityReason.APPROVED),
                        preferences = listOf(
                            EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION),
                            EligibilityPreference.Unknown(
                                MovementCapabilityType.BALANCE_WITHOUT_SUPPORT
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_lowImpactOnlyRejectsHighImpactMetadata() {
        val highImpact = exercise(id = "high-impact").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                impactLevel = ImpactLevel.HIGH
            )
        )
        val lowImpact = exercise(id = "low-impact").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                impactLevel = ImpactLevel.LOW
            )
        )

        val result = policy.evaluate(
            exercises = listOf(highImpact, lowImpact),
            profile = UserProfile(
                availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
                trainingConstraints = setOf(TrainingConstraint.LOW_IMPACT_ONLY)
            ),
            adaptationState = AdaptationState.BUILD
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.Candidates(
                exercises = listOf(lowImpact),
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = highImpact.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.HIGH_IMPACT_DISALLOWED)
                    ),
                    EligibilityDecision(
                        exerciseId = lowImpact.id,
                        eligible = true,
                        reasons = listOf(EligibilityReason.APPROVED)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_jointSensitiveConstraintFailsClosedWithoutReviewedMapping() {
        val exercise = exercise(id = "approved-with-unmapped-constraint").copy(
            reviewedMetadata = reviewedMetadata(reviewState = ReviewState.APPROVED)
        )

        val result = policy.evaluate(
            exercises = listOf(exercise),
            profile = UserProfile(
                availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
                trainingConstraints = setOf(TrainingConstraint.SHOULDER_SENSITIVE)
            ),
            adaptationState = AdaptationState.BUILD
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.NoCandidates(
                failure = AutomaticEligibilityFailure.TRAINING_CONSTRAINTS_REMOVED_ALL,
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.UNMAPPED_TRAINING_CONSTRAINT)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_blocksAdvancedOnlyWhileUncalibratedOrReturning() {
        val exercise = exercise(id = "advanced").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                complexity = ComplexityTier.ADVANCED
            )
        )
        val profile = UserProfile(availableEquipment = listOf(StandardEquipment.BODYWEIGHT))

        val uncalibrated = policy.evaluate(
            exercises = listOf(exercise),
            profile = profile,
            adaptationState = AdaptationState.UNCALIBRATED
        )
        val returning = policy.evaluate(
            exercises = listOf(exercise),
            profile = profile,
            adaptationState = AdaptationState.RETURNING
        )
        val build = policy.evaluate(
            exercises = listOf(exercise),
            profile = profile,
            adaptationState = AdaptationState.BUILD
        )

        assertThat(uncalibrated).isEqualTo(
            AutomaticEligibilityResult.NoCandidates(
                failure = AutomaticEligibilityFailure.CALIBRATION_COMPLEXITY_REMOVED_ALL,
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.ADVANCED_WHILE_UNCALIBRATED)
                    )
                )
            )
        )
        assertThat(returning).isEqualTo(
            AutomaticEligibilityResult.NoCandidates(
                failure = AutomaticEligibilityFailure.CALIBRATION_COMPLEXITY_REMOVED_ALL,
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.ADVANCED_WHILE_RETURNING)
                    )
                )
            )
        )
        assertThat(build).isEqualTo(
            AutomaticEligibilityResult.Candidates(
                exercises = listOf(exercise),
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = true,
                        reasons = listOf(EligibilityReason.APPROVED)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_demonstratedProgressionFamilyLiftsTemporaryAdvancedCeiling() {
        val exercise = exercise(id = "advanced-with-history").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                complexity = ComplexityTier.ADVANCED,
                progressionFamily = "demonstrated-family"
            )
        )

        val result = policy.evaluate(
            exercises = listOf(exercise),
            profile = UserProfile(availableEquipment = listOf(StandardEquipment.BODYWEIGHT)),
            adaptationState = AdaptationState.RETURNING,
            demonstratedProgressionFamilies = setOf("demonstrated-family")
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.Candidates(
                exercises = listOf(exercise),
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = true,
                        reasons = listOf(EligibilityReason.APPROVED)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_availableApprovedSupportedRegressionLiftsTemporaryAdvancedCeiling() {
        val regression = exercise(id = "supported-regression").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                supportRequirement = SupportRequirement.SUPPORTED
            )
        )
        val advanced = exercise(id = "advanced-with-regression").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                complexity = ComplexityTier.ADVANCED,
                approvedRegressions = listOf(ReviewedExerciseLink(regression.id))
            )
        )

        val result = policy.evaluate(
            exercises = listOf(advanced, regression),
            profile = UserProfile(availableEquipment = listOf(StandardEquipment.BODYWEIGHT)),
            adaptationState = AdaptationState.UNCALIBRATED
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.Candidates(
                exercises = listOf(advanced, regression),
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = advanced.id,
                        eligible = true,
                        reasons = listOf(EligibilityReason.APPROVED)
                    ),
                    EligibilityDecision(
                        exerciseId = regression.id,
                        eligible = true,
                        reasons = listOf(EligibilityReason.APPROVED)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_reportsCapabilitiesRemovedAllWithoutStringMatching() {
        val exercise = exercise(id = "capability-blocked").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                capabilityRequirements = setOf(MovementCapabilityType.UNSUPPORTED_SQUAT)
            )
        )

        val result = policy.evaluate(
            exercises = listOf(exercise),
            profile = UserProfile(
                availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
                movementCapabilities = MovementCapabilities.from(
                    mapOf(MovementCapabilityType.UNSUPPORTED_SQUAT to CapabilityLevel.AVOID)
                )
            ),
            adaptationState = AdaptationState.BUILD
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.NoCandidates(
                failure = AutomaticEligibilityFailure.CAPABILITIES_REMOVED_ALL,
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = false,
                        reasons = listOf(EligibilityReason.CAPABILITY_AVOID)
                    )
                )
            )
        )
    }

    @Test
    fun evaluate_preservesEveryApplicableReasonInPolicyOrder() {
        val exercise = exercise(id = "multiple-hard-reasons").copy(
            reviewedMetadata = reviewedMetadata(
                reviewState = ReviewState.APPROVED,
                equipmentAlternatives = listOf(listOf(StandardEquipment.BARBELL)),
                capabilityRequirements = setOf(MovementCapabilityType.IMPACT),
                impactLevel = ImpactLevel.HIGH,
                complexity = ComplexityTier.ADVANCED
            )
        )

        val result = policy.evaluate(
            exercises = listOf(exercise),
            profile = UserProfile(
                availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
                excludedExerciseIds = listOf(exercise.id),
                trainingConstraints = setOf(TrainingConstraint.LOW_IMPACT_ONLY),
                movementCapabilities = MovementCapabilities.from(
                    mapOf(MovementCapabilityType.IMPACT to CapabilityLevel.AVOID)
                )
            ),
            adaptationState = AdaptationState.UNCALIBRATED
        )

        assertThat(result).isEqualTo(
            AutomaticEligibilityResult.NoCandidates(
                failure = AutomaticEligibilityFailure.USER_EXCLUSIONS_REMOVED_ALL,
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = exercise.id,
                        eligible = false,
                        reasons = listOf(
                            EligibilityReason.USER_EXCLUDED,
                            EligibilityReason.MISSING_EQUIPMENT,
                            EligibilityReason.CAPABILITY_AVOID,
                            EligibilityReason.HIGH_IMPACT_DISALLOWED,
                            EligibilityReason.ADVANCED_WHILE_UNCALIBRATED
                        )
                    )
                )
            )
        )
    }

    private fun exercise(id: String): Exercise = Exercise(
        id = id,
        name = id,
        primaryMuscles = listOf(StandardMuscles.CHEST),
        listedEquipment = listOf(StandardEquipment.BODYWEIGHT),
        type = ExerciseType.BODYWEIGHT_REPS
    )

    private fun reviewedMetadata(
        reviewState: ReviewState,
        equipmentAlternatives: List<List<String>> =
            listOf(listOf(StandardEquipment.BODYWEIGHT)),
        capabilityRequirements: Set<MovementCapabilityType> = emptySet(),
        impactLevel: ImpactLevel = ImpactLevel.NONE,
        complexity: ComplexityTier = ComplexityTier.FOUNDATIONAL,
        progressionFamily: String = "synthetic-test-family",
        supportRequirement: SupportRequirement = SupportRequirement.SUPPORTED,
        approvedRegressions: List<ReviewedExerciseLink> = emptyList()
    ): ReviewedExerciseMetadata =
        ReviewedExerciseMetadata(
            reviewState = reviewState,
            directPrimaryMuscle = StandardMuscles.CHEST,
            descriptiveSecondaryMuscles = emptySet(),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            complexity = complexity,
            progressionFamily = progressionFamily,
            prescriptionShape = PrescriptionShape.BODYWEIGHT_REPS,
            approvedRegressions = approvedRegressions,
            approvedSubstitutions = emptyList(),
            capabilityRequirements = capabilityRequirements,
            supportRequirement = supportRequirement,
            impactLevel = impactLevel,
            equipmentAlternatives = equipmentAlternatives,
            provenance = ReviewProvenance(
                reviewerRole = if (reviewState == ReviewState.APPROVED) {
                    "Synthetic test-only reviewer"
                } else {
                    null
                },
                rationaleOrSource = "SYNTHETIC TEST DATA — never bundled in production assets.",
                reviewedAtEpochMillis = if (reviewState == ReviewState.APPROVED) 1L else null,
                schemaVersion = 1,
                policyVersion = 1
            )
        )
}
