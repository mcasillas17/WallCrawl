package wallcrawl.elopenmike.com.core.model

data class AppliedConditionRule(val reference: ConditionRuleReference, val exerciseIdentity: ConditionExerciseIdentity, val executionIdentity: String, val purpose: PrescriptionPurpose, val explanation: ConditionText) {
    init { requireConditionToken(executionIdentity) }
}

data class ConditionScheduleEvidence(val reference: ConditionRuleReference, val previousPerformedAtTimestamp: Long?, val checkedAtTimestamp: Long) {
    init { require(checkedAtTimestamp > 0 && (previousPerformedAtTimestamp == null || previousPerformedAtTimestamp in 1..checkedAtTimestamp)) }
}

/** One frozen session fact set; recommendations bind canonicalDigest rather than copying it. */
class SessionConditionContext(
    val formatVersion: Int, val policyVersion: Int,
    selectedConditions: List<SelectedCondition>, sourceSupportVersions: Map<String, String>,
    appliedRules: List<AppliedConditionRule>, consumedScheduleEvidence: List<ConditionScheduleEvidence>,
    consumedReassessmentEventIds: List<String>
) {
    val selectedConditions = conditionList(selectedConditions)
    val sourceSupportVersions = conditionMap(sourceSupportVersions)
    val appliedRules = conditionList(appliedRules)
    val consumedScheduleEvidence = conditionList(consumedScheduleEvidence)
    val consumedReassessmentEventIds = conditionList(consumedReassessmentEventIds)
    init {
        require(formatVersion == 1 && policyVersion > 0)
        require(selectedConditions.size <= 32 && selectedConditions.map { it.conditionId }.distinct().size == selectedConditions.size)
        require(sourceSupportVersions.keys == selectedConditions.map { it.conditionId }.toSet())
        sourceSupportVersions.values.forEach(::requireConditionToken)
        require(appliedRules.size <= 512 && consumedScheduleEvidence.size <= 512 && consumedReassessmentEventIds.size <= 512)
        consumedReassessmentEventIds.forEach(::requireConditionToken)
        (appliedRules.map { it.reference } + consumedScheduleEvidence.map { it.reference }).forEach {
            require(sourceSupportVersions[it.conditionId] == it.supportVersion)
        }
    }
    val canonicalDigest: String get() = conditionDigest(mapOf(
        "formatVersion" to formatVersion, "policyVersion" to policyVersion,
        "selectedConditions" to selectedConditions.sortedBy { it.conditionId }.map { listOf(it.conditionId, it.contextSchemaVersion, it.answers) },
        "sourceSupportVersions" to sourceSupportVersions,
        "appliedRules" to appliedRules.map { listOf(referenceValues(it.reference), it.exerciseIdentity.exerciseId, it.exerciseIdentity.contentSha256, it.exerciseIdentity.exerciseType.name, it.exerciseIdentity.artworkSha256, it.executionIdentity, it.purpose.name, it.explanation.en, it.explanation.es) },
        "consumedScheduleEvidence" to consumedScheduleEvidence.map { listOf(referenceValues(it.reference), it.previousPerformedAtTimestamp, it.checkedAtTimestamp) },
        "consumedReassessmentEventIds" to consumedReassessmentEventIds.sorted()
    ))
    private fun referenceValues(reference: ConditionRuleReference) = listOf(reference.conditionId, reference.supportVersion, reference.caseId, reference.ruleId, reference.policyVersion)

    private fun valueFields(): List<Any?> = listOf(formatVersion, policyVersion, selectedConditions, sourceSupportVersions, appliedRules, consumedScheduleEvidence, consumedReassessmentEventIds)
    override fun equals(other: Any?): Boolean = other is SessionConditionContext && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}
