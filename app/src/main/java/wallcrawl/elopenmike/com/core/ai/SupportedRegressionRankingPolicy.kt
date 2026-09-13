package wallcrawl.elopenmike.com.core.ai

import java.util.Collections
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.EligibilityDecision
import wallcrawl.elopenmike.com.core.model.EligibilityPreference
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.SupportRequirement

data class SupportedRegressionPreference(
    val preferredExerciseId: String,
    val sourceExerciseId: String,
    val capability: MovementCapabilityType
)

class SupportedRegressionRankingPolicy {

    fun preferences(
        candidates: List<Exercise>,
        automaticEligibilityResult: AutomaticEligibilityResult?,
        capabilityEvidence: CapabilityEvidenceSet
    ): Map<String, List<SupportedRegressionPreference>> {
        if (candidates.isEmpty() || automaticEligibilityResult == null) {
            return emptyMap()
        }

        val candidatesById = candidates
            .distinctBy(Exercise::id)
            .associateBy(Exercise::id)
        val eligibleDecisionsById = automaticEligibilityResult.decisions
            .asSequence()
            .filter(EligibilityDecision::eligible)
            .distinctBy(EligibilityDecision::exerciseId)
            .filter { it.exerciseId in candidatesById }
            .associateBy(EligibilityDecision::exerciseId)

        val preferences = linkedMapOf<String, MutableList<SupportedRegressionPreference>>()
        candidatesById.values
            .asSequence()
            .sortedBy(Exercise::id)
            .forEach { source ->
                val sourceMetadata = source.reviewedMetadata
                    ?.takeIf { it.reviewState == ReviewState.APPROVED }
                    ?: return@forEach
                val sourceDecision = eligibleDecisionsById[source.id] ?: return@forEach
                if (capabilityEvidence[source.id] != null) return@forEach

                val limitedCapabilities = sourceDecision.preferences
                    .filterIsInstance<EligibilityPreference.Limited>()
                    .map(EligibilityPreference.Limited::capability)
                    .distinct()
                    .sortedBy(MovementCapabilityType::ordinal)
                if (limitedCapabilities.isEmpty()) return@forEach

                sourceMetadata.approvedRegressions
                    .asSequence()
                    .map { it.exerciseId }
                    .filter(String::isNotBlank)
                    .distinct()
                    .sorted()
                    .forEach { targetId ->
                        val target = candidatesById[targetId] ?: return@forEach
                        if (eligibleDecisionsById[targetId] == null) return@forEach
                        val targetMetadata = target.reviewedMetadata
                            ?.takeIf { it.reviewState == ReviewState.APPROVED }
                            ?: return@forEach
                        if (targetMetadata.supportRequirement != SupportRequirement.SUPPORTED) {
                            return@forEach
                        }

                        val addressedCapability = limitedCapabilities.firstOrNull {
                            it !in targetMetadata.capabilityRequirements
                        } ?: return@forEach
                        preferences.getOrPut(targetId, ::mutableListOf).add(
                            SupportedRegressionPreference(
                                preferredExerciseId = targetId,
                                sourceExerciseId = source.id,
                                capability = addressedCapability
                            )
                        )
                    }
            }

        return Collections.unmodifiableMap(
            preferences.mapValues { (_, values) -> values.toList() }
        )
    }
}
