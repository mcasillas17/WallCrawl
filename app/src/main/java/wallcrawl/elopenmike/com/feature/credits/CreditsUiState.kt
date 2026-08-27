package wallcrawl.elopenmike.com.feature.credits

import wallcrawl.elopenmike.com.core.exercise.workoutguide.AttributionNotice
import wallcrawl.elopenmike.com.core.exercise.workoutguide.CatalogAttribution

sealed interface CreditsUiState {
    data object Loading : CreditsUiState

    data class Error(val message: String) : CreditsUiState

    data class Success(
        val catalog: CatalogAttribution,
        val notices: List<AttributionNotice>
    ) : CreditsUiState
}
