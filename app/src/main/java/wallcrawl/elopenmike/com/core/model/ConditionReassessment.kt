package wallcrawl.elopenmike.com.core.model

/** Owned independently of sessions; sourceSetId is provenance, never a foreign key. */
data class PendingConditionReassessment(
    val profileId: String, val conditionId: String, val exerciseId: String,
    val eventId: String, val stoppedContextIdentity: String, val responsePolicyVersion: Int,
    val stoppedAtTimestamp: Long, val sourceSetId: String? = null
) {
    init {
        require(stoppedAtTimestamp > 0 && responsePolicyVersion > 0)
        require(profileId.isNotBlank())
        listOf(conditionId, exerciseId, eventId).forEach(::requireConditionToken)
        requireConditionDigest(stoppedContextIdentity); require(sourceSetId == null || sourceSetId.isNotBlank())
    }
}
