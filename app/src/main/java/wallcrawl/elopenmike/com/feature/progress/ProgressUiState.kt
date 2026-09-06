package wallcrawl.elopenmike.com.feature.progress

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.WeightUnit

sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data class Success(
        val overview: ProgressOverview,
        val preferredUnit: WeightUnit = WeightUnit.LBS
    ) : ProgressUiState
    data class Error(@StringRes val messageRes: Int = R.string.progress_error) : ProgressUiState
}
