package wallcrawl.elopenmike.com.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.CapabilityEvidence
import wallcrawl.elopenmike.com.core.model.CapabilityEvidencePolicyVersion
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceReason
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceScope
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.EligibilityDecision
import wallcrawl.elopenmike.com.core.model.EligibilityPreference
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseLink
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.SupportRequirement

class CapabilityPreferenceRankingPolicyTest {

    private val policy = CapabilityPreferenceRankingPolicy()

    @Test
    fun penalties_ranksLimitedUnknownOrMixedPreferencesAsOne() {
        val candidateIds = listOf("limited", "unknown", "mixed")
        val result = policy.penalties(
            candidateExerciseIds = candidateIds,
            automaticEligibilityResult = result(
                decisions = listOf(
                    decision(
                        id = "limited",
                        preferences = listOf(EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION))
                    ),
                    decision(
                        id = "unknown",
                        preferences = listOf(EligibilityPreference.Unknown(MovementCapabilityType.BALANCE_WITHOUT_SUPPORT))
                    ),
                    decision(
                        id = "mixed",
                        preferences = listOf(
                            EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION),
                            EligibilityPreference.Unknown(MovementCapabilityType.BALANCE_WITHOUT_SUPPORT)
                        )
                    )
                )
            ),
            capabilityEvidence = evidenceSet()
        )

        assertEquals(listOf("limited" to 1, "unknown" to 1, "mixed" to 1), result.toList())
    }

    @Test
    fun penalties_keepsFirstDecisionForDuplicateExerciseIds() {
        val result = policy.penalties(
            candidateExerciseIds = listOf("duplicate"),
            automaticEligibilityResult = result(
                decisions = listOf(
                    decision(
                        id = "duplicate",
                        preferences = listOf(EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION))
                    ),
                    decision(
                        id = "duplicate",
                        eligible = false
                    )
                )
            ),
            capabilityEvidence = evidenceSet()
        )

        assertEquals(listOf("duplicate" to 1), result.toList())
    }

    @Test
    fun penalties_returnsZeroWhenNoPreferencesArePresent() {
        val result = policy.penalties(
            candidateExerciseIds = listOf("candidate"),
            automaticEligibilityResult = result(
                decisions = listOf(decision(id = "candidate"))
            ),
            capabilityEvidence = evidenceSet()
        )

        assertEquals(listOf("candidate" to 0), result.toList())
    }

    @Test
    fun penalties_suppressesOnlyMatchingExactEvidence() {
        val result = policy.penalties(
            candidateExerciseIds = listOf("exact", "other"),
            automaticEligibilityResult = result(
                decisions = listOf(
                    decision(
                        id = "exact",
                        preferences = listOf(EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION))
                    ),
                    decision(
                        id = "other",
                        preferences = listOf(EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION))
                    )
                )
            ),
            capabilityEvidence = evidenceSet(
                "exact" to evidence(
                    appliesToExerciseId = "exact",
                    demonstratedExerciseId = "exact",
                    scope = CapabilityEvidenceScope.EXACT_EXERCISE
                )
            )
        )

        assertEquals(listOf("exact" to 0, "other" to 1), result.toList())
    }

    @Test
    fun penalties_suppressesMatchingDirectRegressionEvidenceByTargetId() {
        val result = policy.penalties(
            candidateExerciseIds = listOf("target"),
            automaticEligibilityResult = result(
                decisions = listOf(
                    decision(
                        id = "target",
                        preferences = listOf(EligibilityPreference.Unknown(MovementCapabilityType.BALANCE_WITHOUT_SUPPORT))
                    )
                )
            ),
            capabilityEvidence = evidenceSet(
                "target" to evidence(
                    appliesToExerciseId = "target",
                    demonstratedExerciseId = "source",
                    scope = CapabilityEvidenceScope.DIRECT_APPROVED_REGRESSION
                )
            )
        )

        assertEquals(listOf("target" to 0), result.toList())
    }

    @Test
    fun penalties_ignoresEvidenceForOtherIds() {
        val result = policy.penalties(
            candidateExerciseIds = listOf("candidate"),
            automaticEligibilityResult = result(
                decisions = listOf(
                    decision(
                        id = "candidate",
                        preferences = listOf(EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION))
                    )
                )
            ),
            capabilityEvidence = evidenceSet(
                "other" to evidence(
                    appliesToExerciseId = "other",
                    demonstratedExerciseId = "source",
                    scope = CapabilityEvidenceScope.DIRECT_APPROVED_REGRESSION
                )
            )
        )

        assertEquals(listOf("candidate" to 1), result.toList())
    }

    @Test
    fun penalties_returnsZeroForMissingIneligibleOrLegacyNullEligibilityResult() {
        val missingDecision = policy.penalties(
            candidateExerciseIds = listOf("missing"),
            automaticEligibilityResult = result(
                decisions = emptyList()
            ),
            capabilityEvidence = evidenceSet()
        )
        val ineligibleDecision = policy.penalties(
            candidateExerciseIds = listOf("ineligible"),
            automaticEligibilityResult = result(
                decisions = listOf(
                    decision(
                        id = "ineligible",
                        eligible = false,
                        preferences = listOf(EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION))
                    )
                )
            ),
            capabilityEvidence = evidenceSet()
        )
        val legacyNull = policy.penalties(
            candidateExerciseIds = listOf("legacy"),
            automaticEligibilityResult = null,
            capabilityEvidence = evidenceSet()
        )

        assertEquals(listOf("missing" to 0), missingDecision.toList())
        assertEquals(listOf("ineligible" to 0), ineligibleDecision.toList())
        assertEquals(listOf("legacy" to 0), legacyNull.toList())
    }

    @Test
    fun penalties_preservesSoleCandidateAndDeduplicatesRepeatedIdsInOrder() {
        val result = policy.penalties(
            candidateExerciseIds = listOf("beta", "alpha", "beta", "gamma", "alpha"),
            automaticEligibilityResult = result(
                decisions = listOf(
                    decision(
                        id = "beta",
                        preferences = listOf(EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION))
                    ),
                    decision(
                        id = "alpha",
                        preferences = listOf(EligibilityPreference.Unknown(MovementCapabilityType.BALANCE_WITHOUT_SUPPORT))
                    ),
                    decision(
                        id = "gamma",
                        preferences = emptyList()
                    )
                )
            ),
            capabilityEvidence = evidenceSet(
                "alpha" to evidence(
                    appliesToExerciseId = "alpha",
                    demonstratedExerciseId = "source",
                    scope = CapabilityEvidenceScope.DIRECT_APPROVED_REGRESSION
                )
            )
        )

        assertEquals(listOf("beta", "alpha", "gamma"), result.keys.toList())
        assertEquals(1, result["beta"])
        assertEquals(0, result["alpha"])
        assertEquals(0, result["gamma"])
    }

    @Test
    fun penalties_returnsUnmodifiableMapAndLeavesInputsUntouched() {
        val candidateExerciseIds = mutableListOf("candidate-a", "candidate-b", "candidate-a")
        val exercises = mutableListOf(
            exercise("candidate-a"),
            exercise("candidate-b")
        )
        val decisions = mutableListOf(
            decision(
                id = "candidate-a",
                preferences = listOf(EligibilityPreference.Limited(MovementCapabilityType.FLOOR_TRANSITION))
            ),
            decision(
                id = "candidate-b",
                eligible = false
            )
        )
        val eligibilityResult = AutomaticEligibilityResult.Candidates(
            exercises = exercises,
            decisions = decisions
        )
        val candidateSnapshot = candidateExerciseIds.toList()
        val exercisesSnapshot = exercises.toList()
        val decisionsSnapshot = decisions.toList()
        val evidence = evidenceSet(
            "candidate-a" to evidence(
                appliesToExerciseId = "candidate-a",
                demonstratedExerciseId = "source",
                scope = CapabilityEvidenceScope.DIRECT_APPROVED_REGRESSION
            )
        )

        val result = policy.penalties(candidateExerciseIds, eligibilityResult, evidence)

        assertEquals(candidateSnapshot, candidateExerciseIds)
        assertEquals(exercisesSnapshot, exercises)
        assertEquals(decisionsSnapshot, decisions)

        try {
            @Suppress("UNCHECKED_CAST")
            (result as MutableMap<String, Int>)["new-id"] = 1
            fail("result should be unmodifiable")
        } catch (_: UnsupportedOperationException) {
            // expected
        }
    }

    private fun result(decisions: List<EligibilityDecision>): AutomaticEligibilityResult =
        AutomaticEligibilityResult.Candidates(
            exercises = decisions.map { exercise(it.exerciseId) },
            decisions = decisions
        )

    private fun decision(
        id: String,
        eligible: Boolean = true,
        preferences: List<EligibilityPreference> = emptyList()
    ): EligibilityDecision =
        EligibilityDecision(
            exerciseId = id,
            eligible = eligible,
            reasons = if (eligible) listOf(EligibilityReason.APPROVED) else listOf(EligibilityReason.MISSING_APPROVED_METADATA),
            preferences = preferences
        )

    private fun evidenceSet(vararg records: Pair<String, CapabilityEvidence>): CapabilityEvidenceSet =
        CapabilityEvidenceSet.from(records.toMap())

    private fun evidence(
        appliesToExerciseId: String,
        demonstratedExerciseId: String,
        scope: CapabilityEvidenceScope
    ): CapabilityEvidence =
        CapabilityEvidence(
            policyVersion = CapabilityEvidencePolicyVersion.TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1,
            reason = CapabilityEvidenceReason.TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS,
            appliesToExerciseId = appliesToExerciseId,
            demonstratedExerciseId = demonstratedExerciseId,
            scope = scope,
            comparableShape = wallcrawl.elopenmike.com.core.model.ComparableMovementShape.WEIGHT_REPETITIONS,
            qualifyingSessionIds = listOf("session-a", "session-b")
        )

    private fun exercise(id: String): Exercise =
        Exercise(
            id = id,
            name = id,
            primaryMuscles = listOf("chest"),
            listedEquipment = listOf("bodyweight"),
            type = ExerciseType.BODYWEIGHT_REPS,
            reviewedMetadata = reviewedMetadata(id)
        )

    private fun reviewedMetadata(id: String): ReviewedExerciseMetadata =
        ReviewedExerciseMetadata(
            reviewState = ReviewState.APPROVED,
            directPrimaryMuscle = "chest",
            descriptiveSecondaryMuscles = emptySet(),
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            complexity = ComplexityTier.FOUNDATIONAL,
            progressionFamily = "family-$id",
            prescriptionShape = PrescriptionShape.BODYWEIGHT_REPS,
            approvedRegressions = emptyList(),
            approvedSubstitutions = emptyList(),
            capabilityRequirements = emptySet(),
            supportRequirement = SupportRequirement.SUPPORTED,
            impactLevel = ImpactLevel.NONE,
            equipmentAlternatives = listOf(listOf("bodyweight")),
            provenance = ReviewProvenance(
                reviewerRole = "tester",
                rationaleOrSource = "test",
                reviewedAtEpochMillis = 1L,
                schemaVersion = 1,
                policyVersion = 1
            )
        )
}
