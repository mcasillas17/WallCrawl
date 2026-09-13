package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import java.time.ZoneId
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceScope
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.ai.ProgramViolationCode
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseLink
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.SupportRequirement
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WorkoutSplit

/**
 * Every runtime consumer of reviewed metadata reads one acceptance rule.
 *
 * Before this contract each consumer spelled out its own `reviewState == APPROVED` test, so
 * an exercise could be legal for one gate and invisible to the next. These cases pin the
 * consequence rather than the implementation: an `AI_ACCEPTED` record is planned from exactly
 * where an `APPROVED` one is, and a draft or malformed record is refused in exactly the same
 * places.
 *
 * `AI_ACCEPTED` is not promoted to `APPROVED` anywhere here. The states stay separately
 * recorded and separately provenanced; this is only about what automatic planning may read.
 */
class ReviewedAcceptanceConsumerTest {

    private val eligibilityPolicy = ExerciseEligibilityPolicy()
    private val capabilityEvidencePolicy = CapabilityEvidencePolicy()
    private val rankingPolicy = ExerciseDifficultyRankingPolicy()
    private val trainingPolicy = StateBasedTrainingPolicy()
    private val ledgerCalculator = WeeklyDoseLedgerCalculator()
    private val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZoneId.of("UTC"))

    // region eligibility

    @Test
    fun eligibility_acceptsAiAcceptedSourceAndRefusesDraftAndMalformed() {
        val accepted = aiAcceptedExercise("ai-accepted-source")
        val draft = syntheticDraftExercise("draft-source", StandardMuscles.CHEST)
        val malformed = accepted.copy(
            id = "malformed-source",
            reviewedMetadata = requireNotNull(accepted.reviewedMetadata)
                .copy(aiReviewProvenance = null)
        )

        val decisions = eligibilityPolicy
            .evaluate(listOf(accepted, draft, malformed), bodyweightProfile(), AdaptationState.BUILD)
            .decisions
            .associateBy { it.exerciseId }

        assertThat(decisions.getValue(accepted.id).eligible).isTrue()
        assertThat(decisions.getValue(accepted.id).reasons)
            .containsExactly(EligibilityReason.APPROVED)
        listOf(draft, malformed).forEach { rejected ->
            assertWithMessage(rejected.id).that(decisions.getValue(rejected.id).reasons)
                .contains(EligibilityReason.MISSING_APPROVED_METADATA)
        }
    }

    @Test
    fun eligibility_advancedCeilingOpensOnlyForAnIndependentlyAcceptedRegressionEndpoint() {
        val regressionId = "regression-endpoint"
        val advanced = aiAcceptedExercise(
            id = "advanced-source",
            complexity = ComplexityTier.ADVANCED,
            approvedRegressions = listOf(ReviewedExerciseLink(regressionId))
        )
        val acceptedEndpoint = aiAcceptedExercise(regressionId)
        val draftEndpoint = syntheticDraftExercise(regressionId, StandardMuscles.CHEST)
        val malformedEndpoint = acceptedEndpoint.copy(
            reviewedMetadata = requireNotNull(acceptedEndpoint.reviewedMetadata).copy(
                // An acceptance recorded over some other exercise is not this one's acceptance.
                aiReviewProvenance = syntheticAiProvenance(
                    reviewState = ReviewState.AI_ACCEPTED,
                    reviewedContentId = "a-different-exercise"
                )
            )
        )

        assertWithMessage("accepted endpoint")
            .that(advancedIsEligibleWith(advanced, acceptedEndpoint)).isTrue()
        listOf(
            "draft endpoint" to draftEndpoint,
            "malformed endpoint" to malformedEndpoint
        ).forEach { (label, endpoint) ->
            assertWithMessage(label).that(advancedIsEligibleWith(advanced, endpoint)).isFalse()
        }
        assertWithMessage("missing endpoint")
            .that(advancedIsEligibleWith(advanced, endpoint = null)).isFalse()
        assertWithMessage("excluded endpoint")
            .that(advancedIsEligibleWith(advanced, acceptedEndpoint, excluded = setOf(regressionId)))
            .isFalse()
    }

    @Test
    fun eligibility_anEdgeToAPendingEndpointDoesNotUnacceptTheSource() {
        // The source keeps its own acceptance. Only the exception the edge could have opened
        // is withheld, which for a non-advanced source changes nothing at all.
        val source = aiAcceptedExercise(
            id = "source-with-pending-edge",
            approvedRegressions = listOf(ReviewedExerciseLink("pending-endpoint")),
            approvedSubstitutions = listOf(ReviewedExerciseLink("absent-endpoint"))
        )
        val pending = syntheticDraftExercise("pending-endpoint", StandardMuscles.CHEST)

        val decisions = eligibilityPolicy
            .evaluate(listOf(source, pending), bodyweightProfile(), AdaptationState.BUILD)
            .decisions
            .associateBy { it.exerciseId }

        assertThat(decisions.getValue(source.id).eligible).isTrue()
        assertThat(source.acceptedMetadata()).isNotNull()
        assertThat(decisions.getValue(pending.id).eligible).isFalse()
    }

    @Test
    fun eligibility_emptyClearedConstraintsIsNeitherBlanketClearanceNorAGlobalBan() {
        val exercise = aiAcceptedExercise("cleared-nothing")

        // No relevant joint constraint selected: an empty set has nothing to clear, and the
        // exercise stays legal. Absence of a clearance is not a ban.
        assertThat(isEligible(exercise, bodyweightProfile())).isTrue()

        // Every applicable joint constraint is refused, one at a time: absence is not clearance.
        TrainingConstraint.entries
            .filter { it != TrainingConstraint.LOW_IMPACT_ONLY }
            .forEach { constraint ->
                val decision = eligibilityPolicy
                    .evaluate(
                        listOf(exercise),
                        bodyweightProfile(constraints = setOf(constraint)),
                        AdaptationState.BUILD
                    )
                    .decisions
                    .single()
                assertWithMessage(constraint.name).that(decision.reasons)
                    .containsExactly(EligibilityReason.UNMAPPED_TRAINING_CONSTRAINT)
            }
    }

    @Test
    fun eligibility_lowImpactOnlyIsGovernedByImpactLevelAndNotByClearedConstraints() {
        val lowImpact = aiAcceptedExercise("low-impact", impactLevel = ImpactLevel.LOW)
        val highImpact = aiAcceptedExercise("high-impact", impactLevel = ImpactLevel.HIGH)
        val profile = bodyweightProfile(constraints = setOf(TrainingConstraint.LOW_IMPACT_ONLY))

        // Neither record clears anything, so only impactLevel can be deciding here.
        assertThat(isEligible(lowImpact, profile)).isTrue()
        val highImpactDecision = eligibilityPolicy
            .evaluate(listOf(highImpact), profile, AdaptationState.BUILD)
            .decisions
            .single()
        assertThat(highImpactDecision.reasons)
            .containsExactly(EligibilityReason.HIGH_IMPACT_DISALLOWED)
    }

    // endregion

    // region capability evidence

    @Test
    fun capabilityEvidence_propagatesAcrossAnAcceptedEdgeOnlyWhenBothEndsAreAccepted() {
        val demonstrated = aiAcceptedExercise(
            id = "demonstrated-source",
            approvedRegressions = listOf(ReviewedExerciseLink("evidence-endpoint"))
        )
        val acceptedEndpoint = aiAcceptedExercise("evidence-endpoint")
        val draftEndpoint = syntheticDraftExercise("evidence-endpoint", StandardMuscles.CHEST)

        val propagated = capabilityEvidencePolicy.derive(
            sessions = manageableSessionsFor(demonstrated.id),
            exercises = listOf(demonstrated, acceptedEndpoint)
        )
        val withheld = capabilityEvidencePolicy.derive(
            sessions = manageableSessionsFor(demonstrated.id),
            exercises = listOf(demonstrated, draftEndpoint)
        )

        assertThat(propagated.records.keys)
            .containsExactly(demonstrated.id, acceptedEndpoint.id)
        assertThat(propagated.records.values.map { it.scope })
            .contains(CapabilityEvidenceScope.DIRECT_APPROVED_REGRESSION)
        assertThat(withheld.records.keys).containsExactly(demonstrated.id)
    }

    @Test
    fun capabilityEvidence_readsNoEdgeAtAllFromAPendingSource() {
        val draftSource = syntheticDraftExercise("draft-source", StandardMuscles.CHEST).let {
            it.copy(
                reviewedMetadata = requireNotNull(it.reviewedMetadata).copy(
                    approvedRegressions = listOf(ReviewedExerciseLink("evidence-endpoint"))
                )
            )
        }
        val acceptedEndpoint = aiAcceptedExercise("evidence-endpoint")

        val evidence = capabilityEvidencePolicy.derive(
            sessions = manageableSessionsFor(draftSource.id),
            exercises = listOf(draftSource, acceptedEndpoint)
        )

        assertThat(evidence.records.keys).containsExactly(draftSource.id)
    }

    // endregion

    // region ranking, focus and fillability

    @Test
    fun difficultyRanking_readsAiAcceptedComplexityAndIgnoresDraftAndMalformed() {
        val accepted = aiAcceptedExercise("ranked-accepted", complexity = ComplexityTier.ADVANCED)
        val draft = accepted.copy(
            id = "ranked-draft",
            reviewedMetadata = syntheticReviewedMetadata(
                reviewState = ReviewState.DRAFT,
                directPrimaryMuscle = StandardMuscles.CHEST,
                exerciseId = "ranked-draft"
            ).copy(complexity = ComplexityTier.ADVANCED)
        )
        val malformed = accepted.copy(
            id = "ranked-malformed",
            reviewedMetadata = requireNotNull(accepted.reviewedMetadata)
                .copy(aiReviewProvenance = null)
        )

        assertThat(penaltyFor(accepted)).isEqualTo(2)
        assertThat(penaltyFor(draft)).isEqualTo(0)
        assertThat(penaltyFor(malformed)).isEqualTo(0)
    }

    @Test
    fun workoutFocus_readsAiAcceptedMusclesAndFallsBackToLegacyForPendingRecords() {
        val accepted = aiAcceptedExercise(
            id = "focus-accepted",
            directPrimaryMuscle = StandardMuscles.CHEST,
            descriptiveSecondaryMuscles = setOf(StandardMuscles.TRICEPS)
        )
        val draft = syntheticDraftExercise("focus-draft", StandardMuscles.CHEST)

        assertThat(accepted.focusMuscles()).containsExactly(StandardMuscles.CHEST)
        assertThat(accepted.involvedMuscles()).containsExactly(StandardMuscles.TRICEPS)
        assertThat(WorkoutSplit.PUSH.trainsAsFocus(accepted)).isTrue()
        assertThat(WorkoutSplit.PUSH.canFill(accepted)).isTrue()

        // A draft never becomes a source of production behaviour: the legacy lists still win.
        assertThat(draft.focusMuscles()).isEqualTo(LEGACY_MUSCLES)
        assertThat(draft.involvedMuscles()).isEqualTo(LEGACY_MUSCLES)
    }

    // endregion

    // region prescription, ledger and fingerprint

    @Test
    fun trainingPolicy_prescribesFromAiAcceptedMetadataAndFailsClosedOtherwise() {
        val accepted = aiAcceptedExercise("prescribed-accepted")
        val draft = syntheticDraftExercise("prescribed-draft", StandardMuscles.CHEST)
        val malformed = accepted.copy(
            reviewedMetadata = requireNotNull(accepted.reviewedMetadata)
                .copy(aiReviewProvenance = null)
        )

        val applied = trainingPolicy.evaluate(
            exercise = accepted,
            basePrescription = basePrescription,
            profile = bodyweightProfile(),
            fitnessGoals = setOf(FitnessGoal.BUILD_MUSCLE),
            programState = programState()
        )

        assertThat(applied).isInstanceOf(TrainingPolicyResult.Applied::class.java)
        assertThat(evaluateTrainingPolicy(draft)).isEqualTo(
            TrainingPolicyResult.Failure(TrainingPolicyFailureReason.MISSING_ACCEPTED_METADATA)
        )
        assertThat(evaluateTrainingPolicy(malformed)).isEqualTo(
            TrainingPolicyResult.Failure(TrainingPolicyFailureReason.MALFORMED_ACCEPTED_METADATA)
        )
    }

    @Test
    fun selectedAiAcceptedExerciseIsStillCreditedOnceItsSetsAreCompleted() {
        // The regression this contract exists for: an exercise that passed eligibility and was
        // prescribed must not be quietly uncreditable afterwards. Selection, prescription and
        // weekly attribution all read the same acceptance.
        val accepted = syntheticAiAcceptedExercise(
            id = "selected-and-completed",
            directPrimaryMuscle = StandardMuscles.CHEST,
            descriptiveSecondaryMuscles = setOf(StandardMuscles.TRICEPS)
        )

        assertThat(isEligible(accepted, bodyweightProfile())).isTrue()
        assertThat(evaluateTrainingPolicy(accepted))
            .isInstanceOf(TrainingPolicyResult.Applied::class.java)

        val ledger = ledgerOf(accepted, accepted.id)

        assertThat(ledger.directPrimarySets).containsExactly(StandardMuscles.CHEST, 2)
        assertThat(ledger.secondaryInvolvement).containsExactly(StandardMuscles.TRICEPS, 2)
        assertThat(ledger.unattributedWorkSets).isEmpty()
    }

    @Test
    fun ledger_keepsDraftMissingAndUnknownExercisesAsTypedOmissions() {
        val draft = syntheticDraftExercise("ledger-draft", StandardMuscles.CHEST)
        val withoutMetadata = syntheticExerciseWithoutReviewedMetadata("ledger-no-metadata")
        val malformed = syntheticAiAcceptedExercise("ledger-malformed", StandardMuscles.CHEST).let {
            it.copy(
                reviewedMetadata = requireNotNull(it.reviewedMetadata)
                    .copy(aiReviewProvenance = null)
            )
        }

        assertThat(ledgerOf(draft, draft.id).unattributedWorkSets)
            .containsExactly(LedgerOmissionReason.METADATA_NOT_APPROVED, 2)
        assertThat(ledgerOf(malformed, malformed.id).unattributedWorkSets)
            .containsExactly(LedgerOmissionReason.METADATA_NOT_APPROVED, 2)
        assertThat(ledgerOf(withoutMetadata, withoutMetadata.id).unattributedWorkSets)
            .containsExactly(LedgerOmissionReason.MISSING_REVIEWED_METADATA, 2)
        assertThat(ledgerOf(draft, referencedExerciseId = "not-in-catalog").unattributedWorkSets)
            .containsExactly(LedgerOmissionReason.UNKNOWN_EXERCISE, 2)
    }

    @Test
    fun fingerprint_movesForAcceptanceContentAndPolicyChangesAndKeepsHistory() {
        val draft = syntheticDraftExercise("fingerprinted", StandardMuscles.CHEST)
        val accepted = syntheticAiAcceptedExercise("fingerprinted", StandardMuscles.CHEST)
        val malformed = accepted.copy(
            reviewedMetadata = requireNotNull(accepted.reviewedMetadata)
                .copy(aiReviewProvenance = null)
        )
        val otherContent = syntheticAiAcceptedExercise("fingerprinted", StandardMuscles.BACK)
        val otherPolicy = accepted.copy(
            reviewedMetadata = syntheticReviewedMetadata(
                reviewState = ReviewState.AI_ACCEPTED,
                directPrimaryMuscle = StandardMuscles.CHEST,
                exerciseId = "fingerprinted",
                policyVersion = 2
            )
        )

        val fingerprints = listOf(draft, accepted, malformed, otherContent, otherPolicy)
            .map(::fingerprintOf)

        assertThat(fingerprints.toSet()).hasSize(fingerprints.size)
        assertThat(fingerprintOf(accepted)).isEqualTo(fingerprintOf(accepted))
    }

    // endregion

    // region whole-program validation

    @Test
    fun programValidation_acceptsAnAiAcceptedCandidateAndRejectsAPendingOne() = runTest {
        val accepted = syntheticAiAcceptedExercise("validated-accepted", StandardMuscles.CHEST)
        val draft = syntheticDraftExercise("validated-draft", StandardMuscles.CHEST)

        assertThat(validationCodesFor(accepted)).doesNotContain(
            ProgramViolationCode.MISSING_APPROVED_METADATA
        )
        assertThat(validationCodesFor(draft)).contains(
            ProgramViolationCode.MISSING_APPROVED_METADATA
        )
    }

    // endregion

    private suspend fun validationCodesFor(exercise: Exercise): List<ProgramViolationCode> =
        validate(
            workout = validatedWorkout(
                exercises = listOf(repetitionPlan(exercise.id, targetSets = 2, targetWeight = 60.0)),
                focusMuscles = listOf(StandardMuscles.CHEST)
            ),
            context = validatorContext(allowedExercises = listOf(exercise), reviewedPath = true)
        ).codes()

    private fun fingerprintOf(exercise: Exercise): String = LedgerSourceFingerprint.of(
        sessions = completedSessionsFor(exercise.id),
        exercisesById = mapOf(exercise.id to exercise),
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        week = week,
        catalogVersion = SYNTHETIC_CATALOG_VERSION,
        reviewPolicyVersion = 1
    )

    private fun ledgerOf(exercise: Exercise, referencedExerciseId: String): WeeklyDoseLedger =
        ledgerCalculator.calculate(
            sessions = completedSessionsFor(referencedExerciseId),
            exercisesById = mapOf(exercise.id to exercise),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1
        )

    private fun completedSessionsFor(exerciseId: String) = listOf(
        completedSession(
            id = "session-1",
            completedAtEpochMillis = week.startEpochMillis,
            exercises = listOf(
                exerciseInstance(
                    exerciseId = exerciseId,
                    id = "instance-1",
                    sets = listOf(completedNormalSet(id = "set-1"), completedNormalSet(id = "set-2"))
                )
            )
        )
    )

    private fun manageableSessionsFor(exerciseId: String) = (1..2).map { index ->
        completedSession(
            id = "manageable-session-$index",
            completedAtEpochMillis = week.startEpochMillis + index,
            exercises = listOf(
                exerciseInstance(
                    exerciseId = exerciseId,
                    id = "manageable-instance-$index",
                    sets = listOf(
                        completedNormalSet(id = "manageable-set-$index", feltManageable = true)
                    )
                )
            )
        )
    }

    private fun evaluateTrainingPolicy(exercise: Exercise): TrainingPolicyResult =
        trainingPolicy.evaluate(
            exercise = exercise,
            basePrescription = basePrescription,
            profile = bodyweightProfile(),
            fitnessGoals = setOf(FitnessGoal.BUILD_MUSCLE),
            programState = programState()
        )

    private fun programState(): TrainingProgramState = TrainingProgramState(
        policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
        adaptationState = AdaptationState.BUILD,
        weeklyLedger = WeeklyDoseLedger(
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            weekStartEpochDay = MONDAY_EPOCH_DAY,
            timeZoneId = "UTC",
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1,
            directPrimarySets = emptyMap(),
            secondaryInvolvement = emptyMap(),
            unattributedWorkSets = emptyMap()
        )
    )

    private fun penaltyFor(exercise: Exercise): Int = rankingPolicy.aboveExperiencePenalty(
        exercise = exercise,
        experienceLevel = ExperienceLevel.BEGINNER,
        reviewedEligibilityEnabled = true
    )

    private fun advancedIsEligibleWith(
        advanced: Exercise,
        endpoint: Exercise?,
        excluded: Set<String> = emptySet()
    ): Boolean = eligibilityPolicy
        .evaluate(
            exercises = listOfNotNull(advanced, endpoint),
            profile = bodyweightProfile(excludedExerciseIds = excluded),
            adaptationState = AdaptationState.UNCALIBRATED
        )
        .decisions
        .single { it.exerciseId == advanced.id }
        .eligible

    private fun isEligible(exercise: Exercise, profile: UserProfile): Boolean = eligibilityPolicy
        .evaluate(listOf(exercise), profile, AdaptationState.BUILD)
        .decisions
        .single()
        .eligible

    private fun bodyweightProfile(
        constraints: Set<TrainingConstraint> = emptySet(),
        excludedExerciseIds: Set<String> = emptySet()
    ): UserProfile = UserProfile(
        availableEquipment = listOf("Bodyweight"),
        trainingConstraints = constraints,
        excludedExerciseIds = excludedExerciseIds.toList(),
        goals = setOf(FitnessGoal.BUILD_MUSCLE)
    )

    /** An `AI_ACCEPTED` exercise whose AI provenance was recorded over [id] itself. */
    private fun aiAcceptedExercise(
        id: String,
        directPrimaryMuscle: String = StandardMuscles.CHEST,
        descriptiveSecondaryMuscles: Set<String> = emptySet(),
        complexity: ComplexityTier = ComplexityTier.STANDARD,
        impactLevel: ImpactLevel = ImpactLevel.LOW,
        approvedRegressions: List<ReviewedExerciseLink> = emptyList(),
        approvedSubstitutions: List<ReviewedExerciseLink> = emptyList()
    ): Exercise = syntheticAiAcceptedExercise(
        id = id,
        directPrimaryMuscle = directPrimaryMuscle,
        descriptiveSecondaryMuscles = descriptiveSecondaryMuscles
    ).let { exercise ->
        exercise.copy(
            reviewedMetadata = requireNotNull(exercise.reviewedMetadata).copy(
                complexity = complexity,
                impactLevel = impactLevel,
                supportRequirement = SupportRequirement.SUPPORTED,
                approvedRegressions = approvedRegressions,
                approvedSubstitutions = approvedSubstitutions
            )
        )
    }

    private val basePrescription = ExercisePrescription(
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetSets = 3,
        repRange = RepRange(8, 12),
        targetWeight = 60.0,
        restSeconds = 90
    )
}
