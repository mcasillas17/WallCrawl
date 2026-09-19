package wallcrawl.elopenmike.com.core.model

enum class DeloadSource { EXPLICIT_REQUEST, RETURNING }
enum class DeloadChoiceStatus { OFFERED, ACCEPTED, DECLINED, DISMISSED, CANCELLED, CONSUMED }
enum class DeloadAction { REQUEST, ACCEPT, DECLINE, DISMISS, CANCEL }

data class DeloadOffer(val id: String, val source: DeloadSource, val policyVersion: String) {
    init {
        requireDeloadToken(id)
        require(policyVersion == DELOAD_POLICY_VERSION) { "Unsupported deload policy version." }
    }
}

data class DeloadChoice(
    val offer: DeloadOffer,
    val status: DeloadChoiceStatus,
    val decidedAtEpochMillis: Long,
    val sessionId: String? = null
) {
    init {
        require(decidedAtEpochMillis in 0..4_102_444_800_000L) { "Deload decision time is out of range." }
        require((status == DeloadChoiceStatus.CONSUMED) == (sessionId != null)) {
            "Only a consumed deload choice has a session."
        }
        sessionId?.let { requireDeloadToken(it, 200) }
        require(status != DeloadChoiceStatus.OFFERED || offer.source == DeloadSource.EXPLICIT_REQUEST) {
            "Returning offers are derived, not persisted as unanswered choices."
        }
    }
}

data class DeloadPreferences(
    val profileId: String = UserProfile.DEFAULT_PROFILE_ID,
    val revision: Long = 0,
    val choice: DeloadChoice? = null,
    val lastHandledReturnKey: String? = null
) {
    init {
        requireDeloadToken(profileId, 200)
        require(revision >= 0) { "Deload revision must not be negative." }
        require(revision != 0L || (choice == null && lastHandledReturnKey == null)) {
            "Revision zero cannot contain a deload decision."
        }
        lastHandledReturnKey?.let { requireReturnKey(it, profileId) }
        choice?.offer?.takeIf { it.source == DeloadSource.RETURNING }?.let {
            requireReturnKey(it.id, profileId)
        }
    }
}

internal const val DELOAD_POLICY_VERSION = "DELOAD_ONE_WORKOUT_V1"

internal fun requireDeloadToken(value: String, maximumLength: Int = 256) {
    require(value.isNotBlank() && value.length <= maximumLength && value.none { it.isISOControl() }) {
        "Deload identifier is blank, too long, or contains control characters."
    }
    require("|||" !in value) { "Deload identifiers cannot contain the persisted list separator." }
}

private fun requireReturnKey(value: String, profileId: String) {
    requireDeloadToken(value)
    val prefix = "$DELOAD_POLICY_VERSION:$profileId:"
    val weeks = value.removePrefix(prefix).toIntOrNull()
    require(value.startsWith(prefix) && weeks != null && weeks in 1..UserProfile.MAX_PERSISTED_BREAK_WEEKS &&
        value == "$prefix$weeks") { "Deload return key is invalid for this profile." }
}
