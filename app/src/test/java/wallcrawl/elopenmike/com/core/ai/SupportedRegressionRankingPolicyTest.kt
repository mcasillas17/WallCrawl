package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.CapabilityEvidence
import wallcrawl.elopenmike.com.core.model.CapabilityEvidencePolicyVersion
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceReason
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceScope
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.ComparableMovementShape
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.EligibilityDecision
import wallcrawl.elopenmike.com.core.model.EligibilityPreference
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseLink
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.SupportRequirement

class SupportedRegressionRankingPolicyTest {

    private val policy = SupportedRegressionRankingPolicy()

    @Test
    fun preferences_returnsDirectSupportedRegressionForUnresolvedLimitedCapability() {
        val source = exercise(
            id = "source",
            regressions = listOf(ReviewedExerciseLink("target"))
        )
        val target = exercise(
            id = "target",
            support = SupportRequirement.SUPPORTED
        )

        val preferences = policy.preferences(
            candidates = listOf(source, target),
            automaticEligibilityResult = eligibility(
                source to listOf(
                    EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
                ),
                target to emptyList()
            ),
            capabilityEvidence = CapabilityEvidenceSet.empty()
        )

        assertThat(preferences).containsExactly(
            "target",
            listOf(
                SupportedRegressionPreference(
                    preferredExerciseId = "target",
                    sourceExerciseId = "source",
                    capability = MovementCapabilityType.FLOOR_TRANSITION
                )
            )
        )
    }

    @Test
    fun preferences_isStableAcrossCandidateAndEdgeOrder() {
        val sourceA = exercise(
            id = "source-a",
            regressions = listOf(ReviewedExerciseLink("target"))
        )
        val sourceZ = exercise(
            id = "source-z",
            regressions = listOf(ReviewedExerciseLink("target"))
        )
        val target = exercise(id = "target")
        val eligibility = eligibility(
            sourceZ to listOf(
                EligibilityPreference.Limited(MovementCapabilityType.BALANCE_WITHOUT_SUPPORT)
            ),
            target to emptyList(),
            sourceA to listOf(
                EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
            )
        )

        val forward = policy.preferences(
            candidates = listOf(sourceZ, target, sourceA),
            automaticEligibilityResult = eligibility,
            capabilityEvidence = CapabilityEvidenceSet.empty()
        )
        val reversed = policy.preferences(
            candidates = listOf(sourceA, target, sourceZ),
            automaticEligibilityResult = eligibility,
            capabilityEvidence = CapabilityEvidenceSet.empty()
        )

        val expected = listOf(
            SupportedRegressionPreference(
                preferredExerciseId = "target",
                sourceExerciseId = "source-a",
                capability = MovementCapabilityType.FLOOR_TRANSITION
            ),
            SupportedRegressionPreference(
                preferredExerciseId = "target",
                sourceExerciseId = "source-z",
                capability = MovementCapabilityType.BALANCE_WITHOUT_SUPPORT
            )
        )
        assertThat(forward).containsExactly("target", expected)
        assertThat(reversed).containsExactly("target", expected)
    }

    @Test
    fun preferences_doesNotInferFromUnknownUnrelatedOrTargetCapabilityDemand() {
        val unknownSource = exercise(
            id = "unknown-source",
            regressions = listOf(ReviewedExerciseLink("target"))
        )
        val unrelatedLimited = exercise(id = "unrelated-limited")
        val target = exercise(
            id = "target",
            capabilities = setOf(MovementCapabilityType.FLOOR_TRANSITION)
        )

        val preferences = policy.preferences(
            candidates = listOf(unknownSource, unrelatedLimited, target),
            automaticEligibilityResult = eligibility(
                unknownSource to listOf(
                    EligibilityPreference.Unknown(MovementCapabilityType.FLOOR_TRANSITION)
                ),
                unrelatedLimited to listOf(
                    EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
                ),
                target to listOf(
                    EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
                )
            ),
            capabilityEvidence = CapabilityEvidenceSet.empty()
        )

        assertThat(preferences).isEmpty()
    }

    @Test
    fun preferences_requiresTargetToRemoveTheAddressedCapabilityDemand() {
        val source = exercise(
            id = "source",
            regressions = listOf(ReviewedExerciseLink("target")),
            capabilities = setOf(MovementCapabilityType.FLOOR_TRANSITION)
        )
        val target = exercise(
            id = "target",
            capabilities = setOf(MovementCapabilityType.FLOOR_TRANSITION)
        )
        val eligibility = eligibility(
            source to listOf(
                EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
            ),
            target to listOf(
                EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
            )
        )

        val withSharedDemand = policy.preferences(
            candidates = listOf(source, target),
            automaticEligibilityResult = eligibility,
            capabilityEvidence = CapabilityEvidenceSet.empty()
        )
        val withoutTargetDemand = policy.preferences(
            candidates = listOf(source, target.copy(
                reviewedMetadata = target.reviewedMetadata?.copy(
                    capabilityRequirements = emptySet()
                )
            )),
            automaticEligibilityResult = eligibility,
            capabilityEvidence = CapabilityEvidenceSet.empty()
        )

        assertThat(withSharedDemand).isEmpty()
        assertThat(withoutTargetDemand).containsKey(target.id)
    }

    @Test
    fun preferences_doesNotReintroducePreferenceWhenSourceEvidenceSuppressesPenalty() {
        val source = exercise(
            id = "source",
            regressions = listOf(ReviewedExerciseLink("target"))
        )
        val target = exercise(id = "target")

        val preferences = policy.preferences(
            candidates = listOf(source, target),
            automaticEligibilityResult = eligibility(
                source to listOf(
                    EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
                ),
                target to emptyList()
            ),
            capabilityEvidence = evidenceSet("source")
        )

        assertThat(preferences).isEmpty()
    }

    @Test
    fun preferences_requiresApprovedEndpointsAndExplicitSupportedTarget() {
        val target = exercise(id = "target")
        val limited = listOf(
            EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
        )

        val draftSource = exercise(
            id = "draft-source",
            reviewState = ReviewState.DRAFT,
            regressions = listOf(ReviewedExerciseLink("target"))
        )
        val draftTarget = exercise(id = "draft-target", reviewState = ReviewState.DRAFT)
        val sourceToDraft = exercise(
            id = "source-to-draft",
            regressions = listOf(ReviewedExerciseLink("draft-target"))
        )
        val optionalTarget = exercise(
            id = "optional-target",
            support = SupportRequirement.OPTIONAL_SUPPORT
        )
        val sourceToOptional = exercise(
            id = "source-to-optional",
            regressions = listOf(ReviewedExerciseLink("optional-target"))
        )
        val missingMetadataTarget = exercise(id = "missing-target", reviewed = false)
        val sourceToMissing = exercise(
            id = "source-to-missing",
            regressions = listOf(ReviewedExerciseLink("missing-target"))
        )

        val candidates = listOf(
            draftSource,
            sourceToDraft,
            draftTarget,
            sourceToOptional,
            optionalTarget,
            sourceToMissing,
            missingMetadataTarget,
            target
        )
        val preferences = policy.preferences(
            candidates = candidates,
            automaticEligibilityResult = eligibility(
                *candidates.map { candidate ->
                    candidate to if ("source" in candidate.id) limited else emptyList()
                }.toTypedArray()
            ),
            capabilityEvidence = CapabilityEvidenceSet.empty()
        )

        assertThat(preferences).isEmpty()
    }

    @Test
    fun preferences_preservesDirectionAndDoesNotTraverseChainsOrUseNonCandidates() {
        val source = exercise(
            id = "source",
            regressions = listOf(ReviewedExerciseLink("middle"))
        )
        val middle = exercise(
            id = "middle",
            regressions = listOf(ReviewedExerciseLink("target"))
        )
        val target = exercise(
            id = "target",
            regressions = listOf(ReviewedExerciseLink("reversed-source"))
        )
        val reversedSource = exercise(id = "reversed-source")
        val excludedLinkedTarget = exercise(id = "not-a-candidate")
        val sourceToExcluded = exercise(
            id = "source-to-excluded",
            regressions = listOf(ReviewedExerciseLink("not-a-candidate"))
        )

        val candidates = listOf(source, middle, target, reversedSource, sourceToExcluded)
        val preferences = policy.preferences(
            candidates = candidates,
            automaticEligibilityResult = eligibility(
                source to listOf(
                    EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
                ),
                middle to emptyList(),
                target to emptyList(),
                reversedSource to listOf(
                    EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
                ),
                sourceToExcluded to listOf(
                    EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION)
                )
            ),
            capabilityEvidence = CapabilityEvidenceSet.empty()
        )

        assertThat(preferences).containsExactly(
            "middle",
            listOf(
                SupportedRegressionPreference(
                    preferredExerciseId = "middle",
                    sourceExerciseId = "source",
                    capability = MovementCapabilityType.FLOOR_TRANSITION
                )
            )
        )
        assertThat(preferences).doesNotContainKey("target")
        assertThat(preferences).doesNotContainKey("reversed-source")
        assertThat(preferences).doesNotContainKey("not-a-candidate")
    }

    @Test
    fun preferences_requiresEligibleDecisionsForBothEndpoints() {
        val source = exercise(
            id = "source",
            regressions = listOf(ReviewedExerciseLink("target"))
        )
        val target = exercise(id = "target")

        val preferences = policy.preferences(
            candidates = listOf(source, target),
            automaticEligibilityResult = AutomaticEligibilityResult.Candidates(
                exercises = listOf(source, target),
                decisions = listOf(
                    decision(
                        source,
                        preferences = listOf(
                            EligibilityPreference.Limited(
                                MovementCapabilityType.FLOOR_TRANSITION
                            )
                        )
                    ),
                    decision(target, eligible = false)
                )
            ),
            capabilityEvidence = CapabilityEvidenceSet.empty()
        )

        assertThat(preferences).isEmpty()
    }

    private fun eligibility(
        vararg preferences: Pair<Exercise, List<EligibilityPreference>>
    ): AutomaticEligibilityResult =
        AutomaticEligibilityResult.Candidates(
            exercises = preferences.map(Pair<Exercise, *>::first),
            decisions = preferences.map { (exercise, exercisePreferences) ->
                decision(exercise, preferences = exercisePreferences)
            }
        )

    private fun decision(
        exercise: Exercise,
        eligible: Boolean = true,
        preferences: List<EligibilityPreference> = emptyList()
    ): EligibilityDecision =
        EligibilityDecision(
            exerciseId = exercise.id,
            eligible = eligible,
            reasons = if (eligible) {
                listOf(EligibilityReason.APPROVED)
            } else {
                listOf(EligibilityReason.CAPABILITY_AVOID)
            },
            preferences = preferences
        )

    private fun exercise(
        id: String,
        reviewState: ReviewState = ReviewState.APPROVED,
        support: SupportRequirement = SupportRequirement.SUPPORTED,
        capabilities: Set<MovementCapabilityType> = emptySet(),
        regressions: List<ReviewedExerciseLink> = emptyList(),
        reviewed: Boolean = true
    ): Exercise =
        Exercise(
            id = id,
            name = id,
            primaryMuscles = listOf("Chest"),
            listedEquipment = listOf("Bodyweight"),
            type = ExerciseType.BODYWEIGHT_REPS,
            reviewedMetadata = if (reviewed) {
                ReviewedExerciseMetadata(
                    reviewState = reviewState,
                    directPrimaryMuscle = "Chest",
                    descriptiveSecondaryMuscles = emptySet(),
                    movementPattern = MovementPattern.HORIZONTAL_PUSH,
                    complexity = ComplexityTier.FOUNDATIONAL,
                    progressionFamily = "family-$id",
                    prescriptionShape = PrescriptionShape.BODYWEIGHT_REPS,
                    approvedRegressions = regressions,
                    approvedSubstitutions = emptyList(),
                    capabilityRequirements = capabilities,
                    supportRequirement = support,
                    impactLevel = ImpactLevel.NONE,
                    equipmentAlternatives = listOf(listOf("Bodyweight")),
                    clearedTrainingConstraints = emptySet(),
                    provenance = ReviewProvenance(
                        reviewerRole = "synthetic-test",
                        rationaleOrSource = "synthetic approved fixture",
                        reviewedAtEpochMillis = 1L,
                        schemaVersion = 2,
                        policyVersion = 1
                    )
                )
            } else {
                null
            }
        )

    private fun evidenceSet(exerciseId: String): CapabilityEvidenceSet =
        CapabilityEvidenceSet.from(
            mapOf(
                exerciseId to CapabilityEvidence(
                    policyVersion =
                        CapabilityEvidencePolicyVersion.TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1,
                    reason =
                        CapabilityEvidenceReason.TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS,
                    appliesToExerciseId = exerciseId,
                    demonstratedExerciseId = exerciseId,
                    scope = CapabilityEvidenceScope.EXACT_EXERCISE,
                    comparableShape = ComparableMovementShape.BODYWEIGHT_REPETITIONS,
                    qualifyingSessionIds = listOf("session-a", "session-b")
                )
            )
        )
}
