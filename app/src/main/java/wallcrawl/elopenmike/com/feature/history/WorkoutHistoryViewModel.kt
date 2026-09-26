package wallcrawl.elopenmike.com.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import wallcrawl.elopenmike.com.core.database.repository.WorkoutHistoryDetail
import wallcrawl.elopenmike.com.core.database.repository.WorkoutHistoryRepository
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutHistoryEntry

sealed interface WorkoutHistoryDetailUiState {
    data object Loading : WorkoutHistoryDetailUiState
    data class Loaded(val detail: WorkoutHistoryDetail, val reasons: HistoryReasons?) : WorkoutHistoryDetailUiState
    data object Missing : WorkoutHistoryDetailUiState
    data class Unsupported(val session: WorkoutSession) : WorkoutHistoryDetailUiState
    data object ReadError : WorkoutHistoryDetailUiState
}

sealed interface WorkoutHistoryListUiState {
    data object Loading : WorkoutHistoryListUiState
    data class Loaded(val page: Int, val sessions: List<WorkoutHistoryEntry>, val hasOlder: Boolean) : WorkoutHistoryListUiState
    data class ReadError(val page: Int) : WorkoutHistoryListUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryDetailViewModel(sessionId: String, repository: WorkoutHistoryRepository) : ViewModel() {
    private val retries = MutableStateFlow(0L)
    val uiState = retries.flatMapLatest {
        flow { emitAll(repository.observeDetail(sessionId)) }
            .map<WorkoutHistoryDetail?, WorkoutHistoryDetailUiState> { detail ->
                when {
                    detail == null -> WorkoutHistoryDetailUiState.Missing
                    detail.session.status != SessionStatus.COMPLETED ->
                        WorkoutHistoryDetailUiState.Unsupported(detail.session)
                    else -> {
                        requireNotNull(detail.summary) { "Completed history requires its coherent summary." }
                        WorkoutHistoryDetailUiState.Loaded(detail, detail.recommendation?.let(::decodeHistoryReasons))
                    }
                }
            }
            .onStart { emit(WorkoutHistoryDetailUiState.Loading) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(WorkoutHistoryDetailUiState.ReadError)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), WorkoutHistoryDetailUiState.Loading)

    fun retry() { retries.update { it + 1 } }

    companion object {
        fun provideFactory(sessionId: String, repository: WorkoutHistoryRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { WorkoutHistoryDetailViewModel(sessionId, repository) } }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryListViewModel(
    repository: WorkoutHistoryRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    init {
        if ((savedStateHandle.get<Int>(PAGE_KEY) ?: 0) < 0) savedStateHandle[PAGE_KEY] = 0
    }
    private val page = savedStateHandle.getStateFlow(PAGE_KEY, 0)
    private val retries = MutableStateFlow(0L)
    val uiState = combine(page, retries) { index, _ -> index }.flatMapLatest { index ->
        flow { emitAll(repository.observeHistory(index)) }
            .map { result ->
                val state: WorkoutHistoryListUiState = WorkoutHistoryListUiState.Loaded(index, result.sessions, result.hasOlder)
                state
            }
            .onStart { emit(WorkoutHistoryListUiState.Loading) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(WorkoutHistoryListUiState.ReadError(index))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), WorkoutHistoryListUiState.Loading)

    fun older() {
        val loaded = uiState.value as? WorkoutHistoryListUiState.Loaded ?: return
        if (loaded.hasOlder && loaded.page < Int.MAX_VALUE) savedStateHandle[PAGE_KEY] = loaded.page + 1
    }
    fun newer() { if (page.value > 0) savedStateHandle[PAGE_KEY] = page.value - 1 }
    fun retry() { retries.update { it + 1 } }

    companion object {
        private const val PAGE_KEY = "history_page"
        fun provideFactory(repository: WorkoutHistoryRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { WorkoutHistoryListViewModel(repository, createSavedStateHandle()) }
        }
    }
}
