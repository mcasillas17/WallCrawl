package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.DELOAD_POLICY_VERSION
import wallcrawl.elopenmike.com.core.model.DeloadChoice
import wallcrawl.elopenmike.com.core.model.DeloadChoiceStatus
import wallcrawl.elopenmike.com.core.model.DeloadOffer
import wallcrawl.elopenmike.com.core.model.DeloadPreferences
import wallcrawl.elopenmike.com.core.model.DeloadSource
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.requireDeloadToken

/** Suggestions are values, not adaptation states or durable acceptance. */
object DeloadOfferPolicy {
    const val VERSION = DELOAD_POLICY_VERSION

    fun offer(profile: UserProfile, preferences: DeloadPreferences): DeloadOffer? {
        require(profile.id == preferences.profileId) { "Deload preferences belong to another profile." }
        if (!profile.onboardingCompleted || accepted(preferences) != null) return null
        preferences.choice?.takeIf { it.status == DeloadChoiceStatus.OFFERED }?.let { return it.offer }
        val key = returnKey(profile) ?: return null
        return if (key == preferences.lastHandledReturnKey) null
        else DeloadOffer(key, DeloadSource.RETURNING, VERSION)
    }

    fun accepted(preferences: DeloadPreferences): DeloadChoice? =
        preferences.choice?.takeIf { it.status == DeloadChoiceStatus.ACCEPTED }

    fun returnKey(profile: UserProfile): String? {
        requireDeloadToken(profile.id, 200)
        require(profile.returningAfterBreakWeeks in 0..UserProfile.MAX_PERSISTED_BREAK_WEEKS) {
            "Reported break is out of range."
        }
        return if (profile.returningAfterBreakWeeks == 0) null
        else "$VERSION:${profile.id}:${profile.returningAfterBreakWeeks}"
    }
}
