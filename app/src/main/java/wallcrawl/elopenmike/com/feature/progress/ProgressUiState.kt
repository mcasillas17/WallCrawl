package wallcrawl.elopenmike.com.feature.progress

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.DeloadSource
import wallcrawl.elopenmike.com.core.model.ProgressAnalytics
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.ProgressionProvenance
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WeightUnit

sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data object NoProfile : ProgressUiState
    data class Success(
        val overview: ProgressOverview,
        val preferredUnit: WeightUnit,
        val week: TrainingWeek,
        val reviewedDose: WeeklyDoseLedger,
        val previousReviewedDose: WeeklyDoseLedger
    ) : ProgressUiState
    data class Error(@StringRes val messageRes: Int = R.string.progress_error) : ProgressUiState
}

/** The "Over time" card's own state, separate so a filter change never reloads the week. */
sealed interface ProgressAnalyticsUiState {
    data object Loading : ProgressAnalyticsUiState
    data object NoProfile : ProgressAnalyticsUiState
    /** The range exceeds the representation bound; nothing was truncated to fit. */
    data object TooLarge : ProgressAnalyticsUiState
    data object Error : ProgressAnalyticsUiState
    data class Loaded(
        val analytics: ProgressAnalytics,
        val preferredUnit: WeightUnit,
        /** Null when there are too many recorded exercises to list. */
        val exerciseOptions: List<String>?,
        val catalogNames: Map<String, String>,
        val decisions: List<AnalyticsDecision>
    ) : ProgressAnalyticsUiState
}

/**
 * What one session recorded about the selected exercise: its own progression decision, an
 * accepted one-workout deload, and any identities this build cannot interpret. Never re-derived.
 */
data class AnalyticsDecision(
    val sessionId: String,
    val sessionName: String,
    val completedAtTimestamp: Long,
    val progression: ProgressionProvenance?,
    val acceptedDeload: DeloadSource?,
    val unsupportedIdentities: List<String>
)
