package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.entity.DeloadPreferencesEntity
import wallcrawl.elopenmike.com.core.model.*

internal fun DeloadPreferences.toEntity() = DeloadPreferencesEntity(
    profileId, revision, choice?.offer?.id, choice?.offer?.source?.name,
    choice?.offer?.policyVersion, choice?.status?.name, choice?.decidedAtEpochMillis,
    choice?.sessionId, lastHandledReturnKey
)

/** Corruption is a failure, never an absent choice that could rearm or hide acceptance. */
internal fun DeloadPreferencesEntity.toDeloadPreferences(): DeloadPreferences {
    val choiceFields = listOf(offerId, source, policyVersion, status, decidedAtEpochMillis)
    require(choiceFields.all { it == null } || choiceFields.all { it != null }) {
        "Stored deload choice is incomplete."
    }
    require(offerId != null || sessionId == null) { "Stored deload session has no choice." }
    return DeloadPreferences(profileId, revision, offerId?.let {
        DeloadChoice(
            DeloadOffer(it, DeloadSource.entries.firstOrNull { value -> value.name == source }
                ?: throw IllegalArgumentException("Stored deload source is unsupported."), policyVersion!!),
            DeloadChoiceStatus.entries.firstOrNull { value -> value.name == status }
                ?: throw IllegalArgumentException("Stored deload status is unsupported."),
            decidedAtEpochMillis!!, sessionId
        )
    }, lastHandledReturnKey)
}
