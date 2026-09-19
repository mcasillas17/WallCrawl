package wallcrawl.elopenmike.com.feature.today

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.DeloadChoice
import wallcrawl.elopenmike.com.core.model.DeloadOffer
import wallcrawl.elopenmike.com.core.model.DeloadPreferences
import wallcrawl.elopenmike.com.core.model.WeightUnit

/**
 * Why Today has nothing to show.
 *
 * Resource-backed rather than a sentence, for the same reason the planner's typed failures
 * are: an exception message is written for a log, and the screen has to be able to say the
 * same thing in whichever language the reader chose.
 */
enum class TodayError(@StringRes val messageRes: Int) {
    NO_CANDIDATES(R.string.today_error_no_candidates),
    NO_STRENGTH_CANDIDATES(R.string.today_error_no_strength_candidates),
    NO_CANDIDATES_FOR_ANY_SPLIT(R.string.today_error_no_split),
    REGENERATION_FAILED(R.string.today_error_regenerate_failed),
    FIRST_GENERATION_FAILED(R.string.today_error_first_failed),
    START_FAILED(R.string.today_error_start_failed),
    DELOAD_READ_FAILED(R.string.today_error_deload_read),
    DELOAD_WRITE_FAILED(R.string.today_error_deload_write),

    /** The generation context changed after this recommendation was produced. */
    RECOMMENDATION_OUT_OF_DATE(R.string.today_error_recommendation_out_of_date),

    /** Revalidation at start rejected the plan, so nothing was written. */
    START_VALIDATION_FAILED(R.string.today_error_start_validation_failed),

    /**
     * The whole proposal would exceed this week's configured direct-primary allowance.
     *
     * A configured WallCrawl policy limit, not a medical judgement: the copy says the
     * planned week is already covered, never that training more would be unsafe.
     */
    WEEKLY_ALLOWANCE_REACHED(R.string.today_error_weekly_allowance_reached),
    REVIEWED_NO_APPROVED_METADATA(R.string.today_error_reviewed_no_approved_metadata),
    REVIEWED_EXCLUSIONS_REMOVED_ALL(R.string.today_error_reviewed_exclusions),
    REVIEWED_EQUIPMENT_REMOVED_ALL(R.string.today_error_reviewed_equipment),
    REVIEWED_CAPABILITIES_REMOVED_ALL(R.string.today_error_reviewed_capabilities),
    REVIEWED_CONSTRAINTS_REMOVED_ALL(R.string.today_error_reviewed_constraints),
    REVIEWED_CALIBRATION_REMOVED_ALL(R.string.today_error_reviewed_calibration),
    REVIEWED_NONE_ELIGIBLE(R.string.today_error_reviewed_none_eligible)
}

/** Durable decision plus transient write feedback; displaying an offer never accepts it. */
data class TodayDeloadState(
    val profileRevision: Long,
    val preferences: DeloadPreferences,
    val offer: DeloadOffer? = null,
    val acceptedChoice: DeloadChoice? = null,
    val isSaving: Boolean = false,
    val error: TodayError? = null
)

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Preparing(val activeSession: WorkoutSession) : TodayUiState

    data class Success(
        val userProfile: UserProfile,
        val suggestedWorkout: GeneratedWorkout,
        val activeSession: WorkoutSession? = null,
        val isRegenerating: Boolean = false,
        val completedThisWeek: Int = 0,
        val deload: TodayDeloadState? = null,
        /** Published with the workout; profile preferences may already be newer during regeneration. */
        val prescriptionUnit: WeightUnit = userProfile.preferredUnit
    ) : TodayUiState

    /**
     * Generation failed. [activeSession] rides along because the Today banner is the only
     * route back into a workout already in progress — losing it here would strand a
     * half-logged session behind an error card.
     */
    data class Error(
        val error: TodayError,
        val activeSession: WorkoutSession? = null,
        val deload: TodayDeloadState? = null
    ) : TodayUiState
}
