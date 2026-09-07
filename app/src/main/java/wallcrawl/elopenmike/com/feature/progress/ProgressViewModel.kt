package wallcrawl.elopenmike.com.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import wallcrawl.elopenmike.com.core.database.repository.ProgressRepository
import wallcrawl.elopenmike.com.core.model.TrainingWeek

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    private val progressRepository: ProgressRepository,
    private val nowTimestamp: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {
    private val refreshRequests = MutableStateFlow(0L)

    val uiState: StateFlow<ProgressUiState> = refreshRequests.flatMapLatest {
        flow {
            while (true) {
                val instant = Instant.ofEpochMilli(nowTimestamp())
                val zone = zoneId()
                val week = TrainingWeek.containing(instant, zone)
                emit(zone)
                // One boundary wakeup while subscribed, not a timer that repeatedly queries.
                delay((week.endEpochMillisExclusive - nowTimestamp()).coerceAtLeast(1L))
            }
        }
    }.flatMapLatest { zone ->
        flow {
            emitAll(progressRepository.observeProgress({ Instant.ofEpochMilli(nowTimestamp()) }, zone))
        }.map { snapshot ->
            val state: ProgressUiState = if (snapshot == null) {
                ProgressUiState.NoProfile
            } else {
                ProgressUiState.Success(
                    overview = snapshot.overview,
                    preferredUnit = snapshot.preferredUnit,
                    week = snapshot.week,
                    reviewedDose = snapshot.reviewedDose,
                    previousReviewedDose = snapshot.previousReviewedDose
                )
            }
            state
        }.onStart {
            emit(ProgressUiState.Loading)
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(ProgressUiState.Error())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        initialValue = ProgressUiState.Loading
    )

    /** Resume, a clock/zone change, and explicit Retry all request fresh calendar inputs. */
    fun refresh() {
        refreshRequests.update { it + 1 }
    }

    companion object {
        fun provideFactory(progressRepository: ProgressRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProgressViewModel(progressRepository) as T
            }
    }
}
