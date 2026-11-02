package wallcrawl.elopenmike.com.core.model

/** Owned independently of sessions; sourceSetId is provenance, never a foreign key. */
data class PendingConditionReassessment(
    val profileId: Long, val conditionId: String, val exerciseId: String,
    val eventId: String, val stoppedContextIdentity: String, val responsePolicyVersion: Int,
    val stoppedAtTimestamp: Long, val sourceSetId: Long? = null
) {
    init {
        require(profileId > 0 && stoppedAtTimestamp > 0 && responsePolicyVersion > 0)
        listOf(conditionId, exerciseId, eventId).forEach(::requireConditionToken)
        requireConditionDigest(stoppedContextIdentity); require(sourceSetId == null || sourceSetId > 0)
    }
}
