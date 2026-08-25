package wallcrawl.elopenmike.com.feature.progress

import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.WeightUnit

sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data class Success(
        val overview: ProgressOverview,
        val preferredUnit: WeightUnit = WeightUnit.LBS
    ) : ProgressUiState
    data class Error(val message: String) : ProgressUiState
}
