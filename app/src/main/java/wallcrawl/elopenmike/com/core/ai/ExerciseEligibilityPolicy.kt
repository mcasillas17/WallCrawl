package wallcrawl.elopenmike.com.core.ai

import java.util.Locale
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.EligibilityDecision
import wallcrawl.elopenmike.com.core.model.EligibilityPreference
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.SupportRequirement
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * Pure reviewed-only legality policy for automatic planning.
 *
 * It preserves incoming exercise order and returns a decision for every input. Callers
 * choose whether this policy is active; the policy never falls back to legacy metadata.
 */
class ExerciseEligibilityPolicy {

    fun evaluate(
        exercises: List<Exercise>,
        profile: UserProfile,
        adaptationState: AdaptationState,
        demonstratedProgressionFamilies: Set<String> = emptySet()
    ): AutomaticEligibilityResult {
        val ownedEquipment = profile.availableEquipment
            .mapTo(linkedSetOf()) { it.normalizedEquipment() }
        val excludedExerciseIds = profile.excludedExerciseIds.toSet()
        val exercisesById = exercises.associateBy(Exercise::id)
        val decisions = exercises.map { exercise ->
            val metadata = exercise.reviewedMetadata
            val approvedMetadata = metadata?.takeIf {
                it.reviewState == ReviewState.APPROVED
            }
            val reasons = buildList {
                if (exercise.id in excludedExerciseIds) {
                    add(EligibilityReason.USER_EXCLUDED)
                }
                if (
                    approvedMetadata != null &&
                    !approvedMetadata.hasAvailableEquipment(ownedEquipment)
                ) {
                    add(EligibilityReason.MISSING_EQUIPMENT)
                }
                if (approvedMetadata == null) {
                    add(EligibilityReason.MISSING_APPROVED_METADATA)
                }
                if (approvedMetadata != null) {
                    if (approvedMetadata.capabilityRequirements.any { capability ->
                            profile.movementCapabilities[capability] == CapabilityLevel.AVOID
                        }
                    ) {
                        add(EligibilityReason.CAPABILITY_AVOID)
                    }
                    if (profile.trainingConstraints.any {
                            it != TrainingConstraint.LOW_IMPACT_ONLY
                        }
                    ) {
                        add(EligibilityReason.UNMAPPED_TRAINING_CONSTRAINT)
                    }
                    if (
                        TrainingConstraint.LOW_IMPACT_ONLY in profile.trainingConstraints &&
                        approvedMetadata.impactLevel == ImpactLevel.HIGH
                    ) {
                        add(EligibilityReason.HIGH_IMPACT_DISALLOWED)
                    }
                    val advancedCeilingApplies =
                        approvedMetadata.complexity == ComplexityTier.ADVANCED &&
                            approvedMetadata.progressionFamily !in demonstratedProgressionFamilies &&
                            !approvedMetadata.hasAvailableApprovedSupportedRegression(
                                exercisesById = exercisesById,
                                profile = profile,
                                ownedEquipment = ownedEquipment,
                                excludedExerciseIds = excludedExerciseIds,
                                demonstratedProgressionFamilies = demonstratedProgressionFamilies
                            )
                    if (advancedCeilingApplies && adaptationState == AdaptationState.UNCALIBRATED) {
                        add(EligibilityReason.ADVANCED_WHILE_UNCALIBRATED)
                    }
                    if (advancedCeilingApplies && adaptationState == AdaptationState.RETURNING) {
                        add(EligibilityReason.ADVANCED_WHILE_RETURNING)
                    }
                }
            }
            val eligible = reasons.isEmpty()
            val preferences = if (eligible) {
                requireNotNull(approvedMetadata).capabilityRequirements
                    .sortedBy(MovementCapabilityType::ordinal)
                    .mapNotNull { capability ->
                        when (profile.movementCapabilities[capability]) {
                            CapabilityLevel.LIMITED -> EligibilityPreference.Limited(capability)
                            CapabilityLevel.UNKNOWN -> EligibilityPreference.Unknown(capability)
                            CapabilityLevel.COMFORTABLE, CapabilityLevel.AVOID -> null
                        }
                    }
            } else {
                emptyList()
            }
            EligibilityDecision(
                exerciseId = exercise.id,
                eligible = eligible,
                reasons = if (eligible) listOf(EligibilityReason.APPROVED) else reasons,
                preferences = preferences
            )
        }
        val candidates = exercises.filterIndexed { index, _ -> decisions[index].eligible }
        return if (candidates.isEmpty()) {
            AutomaticEligibilityResult.NoCandidates(
                failure = aggregateFailure(decisions),
                decisions = decisions
            )
        } else {
            AutomaticEligibilityResult.Candidates(
                exercises = candidates,
                decisions = decisions
            )
        }
    }

    private fun aggregateFailure(
        decisions: List<EligibilityDecision>
    ): AutomaticEligibilityFailure {
        if (decisions.isEmpty()) return AutomaticEligibilityFailure.NO_ELIGIBLE_CANDIDATES

        var remaining = decisions
        for ((stageReasons, failure) in AGGREGATE_FAILURE_STAGES) {
            remaining = remaining.filterNot { decision ->
                decision.reasons.any { it in stageReasons }
            }
            if (remaining.isEmpty()) return failure
        }
        return AutomaticEligibilityFailure.NO_ELIGIBLE_CANDIDATES
    }

    private fun ReviewedExerciseMetadata.hasAvailableApprovedSupportedRegression(
        exercisesById: Map<String, Exercise>,
        profile: UserProfile,
        ownedEquipment: Set<String>,
        excludedExerciseIds: Set<String>,
        demonstratedProgressionFamilies: Set<String>
    ): Boolean = approvedRegressions.any { link ->
        val regression = exercisesById[link.exerciseId] ?: return@any false
        val regressionMetadata = regression.reviewedMetadata ?: return@any false
        regressionMetadata.reviewState == ReviewState.APPROVED &&
            regressionMetadata.supportRequirement == SupportRequirement.SUPPORTED &&
            (
                regressionMetadata.complexity != ComplexityTier.ADVANCED ||
                    regressionMetadata.progressionFamily in demonstratedProgressionFamilies
                ) &&
            regression.id !in excludedExerciseIds &&
            regressionMetadata.hasAvailableEquipment(ownedEquipment) &&
            regressionMetadata.capabilityRequirements.none { capability ->
                profile.movementCapabilities[capability] == CapabilityLevel.AVOID
            } &&
            profile.trainingConstraints.none { it != TrainingConstraint.LOW_IMPACT_ONLY } &&
            !(
                TrainingConstraint.LOW_IMPACT_ONLY in profile.trainingConstraints &&
                    regressionMetadata.impactLevel == ImpactLevel.HIGH
                )
    }

    private fun ReviewedExerciseMetadata.hasAvailableEquipment(
        ownedEquipment: Set<String>
    ): Boolean = equipmentAlternatives.any { alternative ->
        alternative.all { it.normalizedEquipment() in ownedEquipment }
    }

    private fun String.normalizedEquipment(): String = trim().lowercase(Locale.ROOT)

    private companion object {
        val AGGREGATE_FAILURE_STAGES = listOf(
            setOf(EligibilityReason.USER_EXCLUDED) to
                AutomaticEligibilityFailure.USER_EXCLUSIONS_REMOVED_ALL,
            setOf(EligibilityReason.MISSING_EQUIPMENT) to
                AutomaticEligibilityFailure.EQUIPMENT_REMOVED_ALL,
            setOf(EligibilityReason.MISSING_APPROVED_METADATA) to
                AutomaticEligibilityFailure.NO_APPROVED_METADATA,
            setOf(EligibilityReason.CAPABILITY_AVOID) to
                AutomaticEligibilityFailure.CAPABILITIES_REMOVED_ALL,
            setOf(
                EligibilityReason.HIGH_IMPACT_DISALLOWED,
                EligibilityReason.UNMAPPED_TRAINING_CONSTRAINT
            ) to AutomaticEligibilityFailure.TRAINING_CONSTRAINTS_REMOVED_ALL,
            setOf(
                EligibilityReason.ADVANCED_WHILE_UNCALIBRATED,
                EligibilityReason.ADVANCED_WHILE_RETURNING
            ) to AutomaticEligibilityFailure.CALIBRATION_COMPLEXITY_REMOVED_ALL
        )
    }
}
