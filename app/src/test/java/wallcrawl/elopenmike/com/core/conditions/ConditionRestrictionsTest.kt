package wallcrawl.elopenmike.com.core.conditions

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.ExerciseEligibilityPolicy
import wallcrawl.elopenmike.com.core.ai.SYNTHETIC_CATALOG_VERSION
import wallcrawl.elopenmike.com.core.ai.StateBasedTrainingPolicy
import wallcrawl.elopenmike.com.core.ai.TrainingPolicyFailureReason
import wallcrawl.elopenmike.com.core.ai.TrainingPolicyResult
import wallcrawl.elopenmike.com.core.ai.syntheticApprovedExercise
import wallcrawl.elopenmike.com.core.conditions.MovementDemand.CROSS_BODY_HIP_STRETCH
import wallcrawl.elopenmike.com.core.conditions.MovementDemand.HEAVY_EXTERNAL_LOAD
import wallcrawl.elopenmike.com.core.conditions.MovementDemand.PLYOMETRIC_IMPACT
import wallcrawl.elopenmike.com.core.conditions.MovementDemand.PROLONGED_SITTING
import wallcrawl.elopenmike.com.core.conditions.MovementDemand.SINGLE_LEG_HIP_CONTROL
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.AiReviewProvenance
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseLink
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.SupportRequirement
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger

class ConditionRestrictionsTest {

    private val eligibility = ExerciseEligibilityPolicy()

    @Test
    fun noConditionsOrLabelsIsNoneEvenWhenOverlayIsUnavailable() {
        assertThat(restrictionsFor(UserProfile(), ConditionGuidanceResult.Unavailable))
            .isEqualTo(ConditionRestrictions.None)
        // LOW_IMPACT_ONLY is an impact rule, not a joint label.
        assertThat(
            restrictionsFor(
                UserProfile(trainingConstraints = setOf(TrainingConstraint.LOW_IMPACT_ONLY)),
                ConditionGuidanceResult.Unavailable
            )
        ).isEqualTo(ConditionRestrictions.None)
        assertThat(ConditionRestrictions.None.decisionFor("anything")).isNull()
        assertThat(ConditionRestrictions.None.ranksLower("anything")).isFalse()
    }

    @Test
    fun conditionsWithUnavailableOverlayAreUnavailable() {
        val byCondition = restrictionsFor(
            UserProfile(reportedConditions = setOf("c1")),
            ConditionGuidanceResult.Unavailable
        )
        val byLabel = restrictionsFor(
            UserProfile(trainingConstraints = setOf(TrainingConstraint.HIP_SENSITIVE)),
            ConditionGuidanceResult.Unavailable
        )

        assertThat(byCondition).isEqualTo(ConditionRestrictions.Unavailable)
        assertThat(byLabel).isEqualTo(ConditionRestrictions.Unavailable)
        assertThat(byCondition.decisionFor("any"))
            .isEqualTo(EligibilityReason.CONDITION_GUIDANCE_UNAVAILABLE)
        assertThat(byCondition.ranksLower("any")).isFalse()
    }

    @Test
    fun unknownConditionIdIsUnavailableNotIgnored() {
        val result = restrictionsFor(
            UserProfile(reportedConditions = setOf("c1", "not-in-overlay")),
            loaded()
        )

        assertThat(result).isEqualTo(ConditionRestrictions.Unavailable)
    }

    @Test
    fun demandsAreTheUnionAcrossConditionsAndJointLabels() {
        val active = restrictionsFor(
            UserProfile(
                reportedConditions = setOf("c1", "c2"),
                trainingConstraints = setOf(TrainingConstraint.HIP_SENSITIVE)
            ),
            loaded(
                conditions = mapOf(
                    "c1" to record("c1", avoid = setOf(PLYOMETRIC_IMPACT), modify = setOf(HEAVY_EXTERNAL_LOAD)),
                    "c2" to record("c2", avoid = setOf(PROLONGED_SITTING), modify = setOf(CROSS_BODY_HIP_STRETCH))
                ),
                joint = mapOf(TrainingConstraint.HIP_SENSITIVE to setOf(SINGLE_LEG_HIP_CONTROL))
            )
        ) as ConditionRestrictions.Active

        assertThat(active.avoid).containsExactly(PLYOMETRIC_IMPACT, PROLONGED_SITTING)
        assertThat(active.modify)
            .containsExactly(HEAVY_EXTERNAL_LOAD, CROSS_BODY_HIP_STRETCH, SINGLE_LEG_HIP_CONTROL)
    }

    @Test
    fun demandInBothUnionsIsTreatedAsAvoid() {
        val active = restrictionsFor(
            UserProfile(reportedConditions = setOf("c1", "c2")),
            loaded(
                conditions = mapOf(
                    "c1" to record("c1", avoid = setOf(HEAVY_EXTERNAL_LOAD)),
                    "c2" to record("c2", modify = setOf(HEAVY_EXTERNAL_LOAD))
                ),
                exerciseDemands = mapOf("lift" to setOf(HEAVY_EXTERNAL_LOAD))
            )
        ) as ConditionRestrictions.Active

        assertThat(active.avoid).containsExactly(HEAVY_EXTERNAL_LOAD)
        assertThat(active.modify).isEmpty()
        assertThat(active.decisionFor("lift")).isEqualTo(EligibilityReason.CONDITION_RESTRICTED_DEMAND)
        assertThat(active.ranksLower("lift")).isFalse()
    }

    @Test
    fun lowImpactOnlyContributesNoDemands() {
        val with = restrictionsFor(
            UserProfile(
                reportedConditions = setOf("c1"),
                trainingConstraints = setOf(TrainingConstraint.LOW_IMPACT_ONLY)
            ),
            loaded(joint = mapOf(TrainingConstraint.LOW_IMPACT_ONLY to setOf(CROSS_BODY_HIP_STRETCH)))
        ) as ConditionRestrictions.Active

        assertThat(with.avoid).containsExactly(PLYOMETRIC_IMPACT)
        assertThat(with.modify).containsExactly(HEAVY_EXTERNAL_LOAD)
    }

    @Test
    fun avoidDemandIsRefused() {
        val active = activeFor(exerciseDemands = mapOf("jump" to setOf(PLYOMETRIC_IMPACT)))

        assertThat(active.decisionFor("jump")).isEqualTo(EligibilityReason.CONDITION_RESTRICTED_DEMAND)
        assertThat(evaluate(listOf(exercise("jump")), active).decisions.single().reasons)
            .containsExactly(EligibilityReason.CONDITION_RESTRICTED_DEMAND)
    }

    @Test
    fun modifyDemandIsEligibleAndRanksLower() {
        val active = activeFor(
            exerciseDemands = mapOf(
                "heavy" to setOf(HEAVY_EXTERNAL_LOAD),
                "clean" to emptySet()
            )
        )

        assertThat(active.decisionFor("heavy")).isNull()
        assertThat(active.ranksLower("heavy")).isTrue()
        assertThat(active.ranksLower("clean")).isFalse()
        assertThat(evaluate(listOf(exercise("heavy")), active))
            .isInstanceOf(AutomaticEligibilityResult.Candidates::class.java)
    }

    @Test
    fun cuesComeFromModifyDemandsAndSelectedConditionsInTheRequestedLanguage() {
        val active = activeFor(
            exerciseDemands = mapOf("heavy" to setOf(HEAVY_EXTERNAL_LOAD, PLYOMETRIC_IMPACT))
        )

        assertThat(active.cuesFor("heavy", "en"))
            .containsExactly("Keep the load light.", "Slow tempo.").inOrder()
        assertThat(active.cuesFor("heavy", "es"))
            .containsExactly("Usa poca carga.", "Ritmo lento.").inOrder()
        // No demand cue for an unlisted exercise, and none when nothing is restricted.
        assertThat(active.cuesFor("unlisted", "en")).isEmpty()
        assertThat(ConditionRestrictions.None.cuesFor("heavy", "en")).isEmpty()
        assertThat(ConditionRestrictions.Unavailable.cuesFor("heavy", "en")).isEmpty()
    }

    @Test
    fun exerciseWithoutOverlayEntryIsRefusedWhenRestricted() {
        val active = activeFor(exerciseDemands = emptyMap())

        assertThat(active.decisionFor("unlisted"))
            .isEqualTo(EligibilityReason.CONDITION_DEMANDS_UNREVIEWED)
        assertThat(evaluate(listOf(exercise("unlisted")), active).decisions.single().reasons)
            .containsExactly(EligibilityReason.CONDITION_DEMANDS_UNREVIEWED)
    }

    @Test
    fun cleanReviewedExerciseIsAllowedForHipLabel() {
        val profile = UserProfile(
            availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
            trainingConstraints = setOf(TrainingConstraint.HIP_SENSITIVE)
        )
        val restrictions = restrictionsFor(
            profile,
            loaded(
                exerciseDemands = mapOf(
                    "clean" to emptySet(),
                    "single-leg" to setOf(SINGLE_LEG_HIP_CONTROL)
                )
            )
        )

        val result = eligibility.evaluate(
            exercises = listOf(exercise("clean"), exercise("single-leg"), exercise("missing")),
            profile = profile,
            adaptationState = AdaptationState.BUILD,
            restrictions = restrictions
        )

        // A modify demand still passes; only the exercise absent from the overlay is refused.
        assertThat((result as AutomaticEligibilityResult.Candidates).exercises.map { it.id })
            .containsExactly("clean", "single-leg").inOrder()
        assertThat(result.decisions.last().reasons)
            .containsExactly(EligibilityReason.CONDITION_DEMANDS_UNREVIEWED)
    }

    @Test
    fun avoidedRegressionCannotLiftAdvancedCeiling() {
        val regression = exercise("regression")
        val advanced = exercise("advanced").copy(
            reviewedMetadata = metadata(
                complexity = ComplexityTier.ADVANCED,
                approvedRegressions = listOf(ReviewedExerciseLink("regression"))
            )
        )
        val active = activeFor(
            exerciseDemands = mapOf(
                "regression" to setOf(PLYOMETRIC_IMPACT),
                "advanced" to emptySet()
            )
        )

        val result = evaluate(listOf(advanced, regression), active, AdaptationState.UNCALIBRATED)

        assertThat(result).isInstanceOf(AutomaticEligibilityResult.NoCandidates::class.java)
        assertThat(result.decisions.map { it.reasons }).containsExactly(
            listOf(EligibilityReason.ADVANCED_WHILE_UNCALIBRATED),
            listOf(EligibilityReason.CONDITION_RESTRICTED_DEMAND)
        ).inOrder()
    }

    @Test
    fun statePolicyRefusesAvoidedExercise() {
        val exercise = syntheticApprovedExercise(id = "jump", directPrimaryMuscle = StandardMuscles.CHEST)
        val active = activeFor(exerciseDemands = mapOf("jump" to setOf(PLYOMETRIC_IMPACT)))

        val result = StateBasedTrainingPolicy().evaluate(
            exercise = exercise,
            basePrescription = ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                targetWeight = 40.0,
                restSeconds = 75
            ),
            profile = UserProfile(goals = setOf(FitnessGoal.BUILD_MUSCLE)),
            fitnessGoals = setOf(FitnessGoal.BUILD_MUSCLE),
            programState = programState(),
            restrictions = active
        )

        assertThat(result).isEqualTo(
            TrainingPolicyResult.Failure(TrainingPolicyFailureReason.TRAINING_CONSTRAINT_REACHED_POLICY)
        )
    }

    @Test
    fun aggregateFailureIsConditionsRemovedAll() {
        val active = activeFor(exerciseDemands = mapOf("a" to setOf(PLYOMETRIC_IMPACT)))

        val removed = evaluate(listOf(exercise("a"), exercise("unlisted")), active)
        val unavailable = evaluate(listOf(exercise("a")), ConditionRestrictions.Unavailable)

        assertThat((removed as AutomaticEligibilityResult.NoCandidates).failure)
            .isEqualTo(AutomaticEligibilityFailure.CONDITIONS_REMOVED_ALL)
        assertThat((unavailable as AutomaticEligibilityResult.NoCandidates).failure)
            .isEqualTo(AutomaticEligibilityFailure.CONDITION_GUIDANCE_UNAVAILABLE)
    }

    private fun evaluate(
        exercises: List<Exercise>,
        restrictions: ConditionRestrictions,
        state: AdaptationState = AdaptationState.BUILD
    ): AutomaticEligibilityResult = eligibility.evaluate(
        exercises = exercises,
        profile = UserProfile(availableEquipment = listOf(StandardEquipment.BODYWEIGHT)),
        adaptationState = state,
        restrictions = restrictions
    )

    private fun activeFor(
        exerciseDemands: Map<String, Set<MovementDemand>>
    ): ConditionRestrictions.Active = restrictionsFor(
        UserProfile(reportedConditions = setOf("c1")),
        loaded(exerciseDemands = exerciseDemands)
    ) as ConditionRestrictions.Active

    /** Default overlay: c1 avoids PLYOMETRIC_IMPACT and modifies HEAVY_EXTERNAL_LOAD. */
    private fun loaded(
        conditions: Map<String, ConditionRecord> = mapOf(
            "c1" to record(
                "c1",
                avoid = setOf(PLYOMETRIC_IMPACT),
                modify = setOf(HEAVY_EXTERNAL_LOAD)
            )
        ),
        joint: Map<TrainingConstraint, Set<MovementDemand>> = emptyMap(),
        exerciseDemands: Map<String, Set<MovementDemand>> = emptyMap()
    ) = ConditionGuidanceResult.Loaded(
        ConditionGuidance(
            version = "test",
            exerciseDemands = exerciseDemands,
            conditions = conditions,
            jointLabelDemands = joint,
            demandCues = mapOf(
                HEAVY_EXTERNAL_LOAD to mapOf(
                    "en" to "Keep the load light.",
                    "es" to "Usa poca carga."
                )
            )
        )
    )

    private fun record(
        id: String,
        avoid: Set<MovementDemand> = emptySet(),
        modify: Set<MovementDemand> = emptySet()
    ) = ConditionRecord(
        id = id,
        avoidDemands = avoid,
        modifyDemands = modify,
        routine = emptyList(),
        exerciseCues = mapOf("heavy" to mapOf("en" to "Slow tempo.", "es" to "Ritmo lento.")),
        notes = emptyMap(),
        redFlags = emptyMap(),
        evidenceGrade = "C",
        sources = emptyList(),
        provenance = AiReviewProvenance(
            reviewerModelId = "test",
            reviewedAtEpochMillis = 1L,
            reviewedContentId = "x",
            reviewedContentSha256 = "0",
            sourceReferences = emptyList(),
            decisionRationale = "t",
            limitations = "t",
            schemaVersion = 1,
            policyVersion = 1
        )
    )

    private fun exercise(id: String) = Exercise(
        id = id,
        name = id,
        primaryMuscles = listOf(StandardMuscles.CHEST),
        listedEquipment = listOf(StandardEquipment.BODYWEIGHT),
        type = ExerciseType.BODYWEIGHT_REPS,
        reviewedMetadata = metadata()
    )

    private fun metadata(
        complexity: ComplexityTier = ComplexityTier.FOUNDATIONAL,
        approvedRegressions: List<ReviewedExerciseLink> = emptyList()
    ) = ReviewedExerciseMetadata(
        reviewState = ReviewState.APPROVED,
        directPrimaryMuscle = StandardMuscles.CHEST,
        descriptiveSecondaryMuscles = emptySet(),
        movementPattern = MovementPattern.HORIZONTAL_PUSH,
        complexity = complexity,
        progressionFamily = "synthetic-test-family",
        prescriptionShape = PrescriptionShape.BODYWEIGHT_REPS,
        approvedRegressions = approvedRegressions,
        approvedSubstitutions = emptyList(),
        capabilityRequirements = emptySet(),
        supportRequirement = SupportRequirement.SUPPORTED,
        impactLevel = ImpactLevel.NONE,
        equipmentAlternatives = listOf(listOf(StandardEquipment.BODYWEIGHT)),
        clearedTrainingConstraints = emptySet(),
        provenance = ReviewProvenance(
            reviewerRole = "Synthetic test-only reviewer",
            rationaleOrSource = "SYNTHETIC TEST DATA - never bundled in production assets.",
            reviewedAtEpochMillis = 1L,
            schemaVersion = 1,
            policyVersion = 1
        )
    )

    private fun programState() = TrainingProgramState(
        policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
        adaptationState = AdaptationState.BUILD,
        weeklyLedger = WeeklyDoseLedger(
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            weekStartEpochDay = 20_696L,
            timeZoneId = "UTC",
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1,
            directPrimarySets = emptyMap(),
            secondaryInvolvement = emptyMap(),
            unattributedWorkSets = emptyMap()
        )
    )
}
