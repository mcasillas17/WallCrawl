package wallcrawl.elopenmike.com.core.ai

import java.util.Collections
import java.util.LinkedHashMap
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.EligibilityDecision
import wallcrawl.elopenmike.com.core.model.EligibilityPreference

class CapabilityPreferenceRankingPolicy {

    fun penalties(
        candidateExerciseIds: List<String>,
        automaticEligibilityResult: AutomaticEligibilityResult?,
        capabilityEvidence: CapabilityEvidenceSet
    ): Map<String, Int> {
        val distinctCandidateIds = candidateExerciseIds.asSequence().distinct().toList()
        if (distinctCandidateIds.isEmpty()) {
            return Collections.unmodifiableMap(LinkedHashMap())
        }

        val decisionsByExerciseId = automaticEligibilityResult
            ?.decisions
            ?.let { decisions ->
                LinkedHashMap<String, EligibilityDecision>(decisions.size).apply {
                    decisions.forEach { decision ->
                        putIfAbsent(decision.exerciseId, decision)
                    }
                }
            }
            .orEmpty()

        val penalties = LinkedHashMap<String, Int>(distinctCandidateIds.size)
        for (candidateExerciseId in distinctCandidateIds) {
            val penalty = if (shouldPenalize(
                    candidateExerciseId = candidateExerciseId,
                    decision = decisionsByExerciseId[candidateExerciseId],
                    capabilityEvidence = capabilityEvidence
                )
            ) {
                1
            } else {
                0
            }
            penalties[candidateExerciseId] = penalty
        }

        return Collections.unmodifiableMap(LinkedHashMap(penalties))
    }

    private fun shouldPenalize(
        candidateExerciseId: String,
        decision: EligibilityDecision?,
        capabilityEvidence: CapabilityEvidenceSet
    ): Boolean {
        if (decision == null || !decision.eligible) return false
        if (decision.preferences.none { it.isPreferenceLimitedOrUnknown() }) return false
        return capabilityEvidence[candidateExerciseId] == null
    }

    private fun EligibilityPreference.isPreferenceLimitedOrUnknown(): Boolean =
        when (this) {
            is EligibilityPreference.Limited -> true
            is EligibilityPreference.Unknown -> true
        }
}
