package wallcrawl.elopenmike.com.feature.credits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wallcrawl.elopenmike.com.core.exercise.workoutguide.AttributionNoticeSource
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource

/**
 * Supplies the credits screen with the provenance of the bundled exercise artwork.
 * The artwork ships under CC BY-SA 4.0, which requires the creator, licence, and changes
 * to be visible to the person using the app.
 */
class CreditsViewModel(
    private val catalogSource: WorkoutGuideCatalogSource,
    private val noticeSource: AttributionNoticeSource
) : ViewModel() {

    private val stateFlow = MutableStateFlow<CreditsUiState>(CreditsUiState.Loading)
    val uiState: StateFlow<CreditsUiState> = stateFlow.asStateFlow()

    init {
        load()
    }

    fun load() {
        stateFlow.value = CreditsUiState.Loading
        viewModelScope.launch {
            try {
                val snapshot = catalogSource.snapshot()
                stateFlow.value = CreditsUiState.Success(
                    catalog = snapshot.catalogAttribution,
                    notices = noticeSource.notices()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                stateFlow.value = CreditsUiState.Error(
                    error.message ?: "Unable to load attribution details."
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            catalogSource: WorkoutGuideCatalogSource,
            noticeSource: AttributionNoticeSource
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CreditsViewModel(catalogSource, noticeSource) as T
        }
    }
}
