package wallcrawl.elopenmike.com.core.model

/** Stable policy reasons and affected identities; presentation supplies localized edit guidance. */
class ConditionStartRefused(reasons: List<ConditionViolation>) : IllegalStateException(
    "Review the condition answers or edit this workout: ${reasons.joinToString { it.reason }}"
) {
    val reasons = conditionList(reasons)
    constructor(reason: String, conditionIds: Set<String> = emptySet(), exerciseIds: Set<String> = emptySet()) :
        this(listOf(ConditionViolation(reason, conditionIds, exerciseIds)))
}

class ConditionReassessmentRefused(
    val reason: ConditionViolation,
    val expectedEventId: String
) : IllegalStateException("Condition reassessment refused: ${reason.reason}")
