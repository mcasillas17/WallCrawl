package wallcrawl.elopenmike.com.feature.credits

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.exercise.workoutguide.AttributionNotice
import wallcrawl.elopenmike.com.core.exercise.workoutguide.CatalogAttribution

sealed interface CreditsUiState {
    data object Loading : CreditsUiState

    data class Error(@StringRes val messageRes: Int = R.string.credits_error_body) : CreditsUiState

    data class Success(
        val catalog: CatalogAttribution,
        val notices: List<AttributionNotice>
    ) : CreditsUiState
}
