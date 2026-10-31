package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import wallcrawl.elopenmike.com.core.ai.DeloadOfferPolicy
import wallcrawl.elopenmike.com.core.database.entity.DeloadPreferencesEntity
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.database.repository.toDeloadPreferences
import wallcrawl.elopenmike.com.core.database.repository.toEntity
import wallcrawl.elopenmike.com.core.database.repository.toUserProfile
import wallcrawl.elopenmike.com.core.model.*

@Dao
interface DeloadPreferencesDao {
    @Query("SELECT * FROM deload_preferences WHERE profileId = :profileId")
    fun observe(profileId: String): Flow<DeloadPreferencesEntity?>

    @Query("SELECT * FROM deload_preferences WHERE profileId = :profileId")
    suspend fun get(profileId: String): DeloadPreferencesEntity?

    @Query("SELECT * FROM user_profiles WHERE id = :profileId")
    suspend fun getProfile(profileId: String): UserProfileEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM workout_sessions WHERE status = 'IN_PROGRESS')")
    suspend fun hasActiveWorkout(): Boolean

    @Upsert
    suspend fun upsert(preferences: DeloadPreferencesEntity)

    @Transaction
    suspend fun decide(
        action: DeloadAction,
        expectedProfileRevision: Long,
        expectedDecisionRevision: Long,
        offerId: String?,
        decidedAtEpochMillis: Long,
        requestId: String?
    ) {
        val profile = checkNotNull(getProfile(UserProfile.DEFAULT_PROFILE_ID)) { "A profile is required." }
            .toUserProfile()
        check(profile.onboardingCompleted) { "Onboarding must be completed." }
        check(profile.revision == expectedProfileRevision) { "The profile changed." }
        check(!hasActiveWorkout()) { "A workout is already active." }
        val preferences = get(profile.id)?.toDeloadPreferences() ?: DeloadPreferences(profile.id)
        check(preferences.revision == expectedDecisionRevision) { "The deload decision changed." }
        check(preferences.revision < Long.MAX_VALUE) { "Deload revision is exhausted." }
        val choice = when (action) {
            DeloadAction.REQUEST -> {
                check(DeloadOfferPolicy.accepted(preferences) == null) {
                    "Cancel the accepted deload before requesting another."
                }
                check(offerId == null) { "A new request cannot target an old offer." }
                val id = checkNotNull(requestId) { "A new request needs an identifier." }
                check(id != preferences.choice?.offer?.id) { "A new request needs a fresh identifier." }
                DeloadChoice(DeloadOffer(id, DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION),
                    DeloadChoiceStatus.OFFERED, decidedAtEpochMillis)
            }
            DeloadAction.CANCEL -> {
                val accepted = checkNotNull(DeloadOfferPolicy.accepted(preferences)) { "No accepted deload is pending." }
                check(accepted.offer.id == offerId) { "The deload offer changed." }
                accepted.copy(status = DeloadChoiceStatus.CANCELLED, decidedAtEpochMillis = decidedAtEpochMillis)
            }
            else -> {
                val visible = checkNotNull(DeloadOfferPolicy.offer(profile, preferences)) { "No deload offer is visible." }
                check(visible.id == offerId) { "The deload offer changed." }
                val status = when (action) {
                    DeloadAction.ACCEPT -> DeloadChoiceStatus.ACCEPTED
                    DeloadAction.DECLINE -> DeloadChoiceStatus.DECLINED
                    DeloadAction.DISMISS -> DeloadChoiceStatus.DISMISSED
                }
                DeloadChoice(visible, status, decidedAtEpochMillis)
            }
        }
        upsert(preferences.copy(revision = preferences.revision + 1, choice = choice,
            lastHandledReturnKey = if (action == DeloadAction.REQUEST) preferences.lastHandledReturnKey
                else DeloadOfferPolicy.returnKey(profile) ?: preferences.lastHandledReturnKey).toEntity())
    }
}
