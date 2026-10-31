package wallcrawl.elopenmike.com.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits
import wallcrawl.elopenmike.com.core.database.repository.AnalyticsRangeTooLargeException
import wallcrawl.elopenmike.com.core.database.repository.ProgressAnalyticsSnapshot
import wallcrawl.elopenmike.com.core.database.repository.ProgressRepository
import wallcrawl.elopenmike.com.core.model.AnalyticsMetric
import wallcrawl.elopenmike.com.core.model.AnalyticsRange
import wallcrawl.elopenmike.com.core.model.AnalyticsScope
import wallcrawl.elopenmike.com.core.model.AnalyticsSelection
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.feature.history.decodeHistoryReasons

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    private val progressRepository: ProgressRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val nowTimestamp: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    /** Decoding recorded decisions is off the main thread; tests pass their own dispatcher. */
    private val analyticsDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val refreshRequests = MutableStateFlow(0L)
    // The card's Retry re-reads only the card; the weekly overview keeps its state and scroll.
    private val analyticsRetries = MutableStateFlow(0L)

    /** Restored from four short saved strings; anything invalid falls back to the defaults. */
    private val selection = MutableStateFlow(restoreSelection())
    val analyticsSelection: StateFlow<AnalyticsSelection> = selection

    /** Calendar inputs: resume, clock/zone change, Retry and one boundary wakeup while subscribed. */
    private val calendarZones: Flow<ZoneId> = refreshRequests.flatMapLatest {
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
    }

    val uiState: StateFlow<ProgressUiState> = calendarZones.flatMapLatest { zone ->
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

    /**
     * Only range and scope decide the read; the metric is chosen from the already computed series.
     * flatMapLatest cancels an obsolete read, so a slow earlier filter cannot overwrite a newer one.
     */
    val analyticsState: StateFlow<ProgressAnalyticsUiState> = combine(
        selection.map { it.range to it.scope }.distinctUntilChanged(), calendarZones, analyticsRetries
    ) { read, zone, _ -> read to zone }.flatMapLatest { (read, zone) ->
            val (range, scope) = read
            flow {
                emitAll(progressRepository.observeAnalytics(range, scope, { Instant.ofEpochMilli(nowTimestamp()) }, zone))
            }.map { snapshot -> snapshot?.toUiState(scope) ?: ProgressAnalyticsUiState.NoProfile }
                .flowOn(analyticsDispatcher)
                .onStart { emit(ProgressAnalyticsUiState.Loading) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(if (error is AnalyticsRangeTooLargeException) {
                        ProgressAnalyticsUiState.TooLarge
                    } else ProgressAnalyticsUiState.Error)
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
            initialValue = ProgressAnalyticsUiState.Loading
        )

    /** Resume, a clock/zone change, and explicit Retry all request fresh calendar inputs. */
    fun refresh() {
        refreshRequests.update { it + 1 }
    }

    fun retryAnalytics() {
        analyticsRetries.update { it + 1 }
    }

    fun selectAnalyticsRange(range: AnalyticsRange) = save(selection.value.copy(range = range))
    fun selectAnalyticsScope(scope: AnalyticsScope) = save(selection.value.copy(scope = scope))
    fun selectAnalyticsMetric(metric: AnalyticsMetric) = save(selection.value.copy(metric = metric))
    fun resetAnalytics() = save(AnalyticsSelection())

    private fun save(value: AnalyticsSelection) {
        val valid = value.takeIf { it.scope.isValid() } ?: value.copy(scope = AnalyticsScope.All)
        savedStateHandle[RANGE_KEY] = valid.range.name
        savedStateHandle[SCOPE_KIND_KEY] = when (valid.scope) {
            AnalyticsScope.All -> SCOPE_ALL
            is AnalyticsScope.Exercise -> SCOPE_EXERCISE
            is AnalyticsScope.Muscle -> SCOPE_MUSCLE
        }
        savedStateHandle[SCOPE_ID_KEY] = when (val scope = valid.scope) {
            AnalyticsScope.All -> null
            is AnalyticsScope.Exercise -> scope.exerciseId
            is AnalyticsScope.Muscle -> scope.muscle
        }
        savedStateHandle[METRIC_KEY] = valid.metric.name
        selection.value = valid
    }

    private fun restoreSelection(): AnalyticsSelection {
        val id = savedStateHandle.get<String>(SCOPE_ID_KEY)
        val scope = when (savedStateHandle.get<String>(SCOPE_KIND_KEY)) {
            SCOPE_EXERCISE -> id?.let(AnalyticsScope::Exercise)
            SCOPE_MUSCLE -> id?.let(AnalyticsScope::Muscle)
            else -> null
        }?.takeIf { it.isValid() } ?: AnalyticsScope.All
        return AnalyticsSelection(
            range = AnalyticsRange.entries.firstOrNull { it.name == savedStateHandle.get<String>(RANGE_KEY) }
                ?: AnalyticsRange.WEEKS_4,
            scope = scope,
            metric = AnalyticsMetric.entries.firstOrNull { it.name == savedStateHandle.get<String>(METRIC_KEY) }
                ?: AnalyticsMetric.COMPLETED_SETS
        )
    }

    private fun AnalyticsScope.isValid(): Boolean = when (this) {
        AnalyticsScope.All -> true
        is AnalyticsScope.Exercise -> exerciseId.isNotBlank() && exerciseId.length <= LocalDataArchiveLimits.MAX_ID_LENGTH &&
            exerciseId.none(Char::isISOControl)
        is AnalyticsScope.Muscle -> muscle in StandardMuscles.TRAINABLE
    }

    /** Decoding uses the history presenter, so a known malformed group fails like history detail. */
    private fun ProgressAnalyticsSnapshot.toUiState(scope: AnalyticsScope): ProgressAnalyticsUiState {
        val exerciseId = (scope as? AnalyticsScope.Exercise)?.exerciseId
        return ProgressAnalyticsUiState.Loaded(
            analytics = analytics,
            preferredUnit = preferredUnit,
            exerciseOptions = exerciseOptions,
            catalogNames = catalogNames,
            decisions = decisions.mapNotNull { decision ->
                val reasons = decodeHistoryReasons(decision.record)
                val progression = reasons.progression.firstOrNull { it.exerciseId == exerciseId }
                // The provenance type guarantees a source exactly when an offer was accepted.
                val deload = reasons.deload?.source
                if (progression == null && deload == null && reasons.unsupportedIdentities.isEmpty()) return@mapNotNull null
                AnalyticsDecision(
                    decision.sessionId, decision.sessionName, decision.completedAtTimestamp,
                    progression, deload, reasons.unsupportedIdentities
                )
            }
        )
    }

    companion object {
        private const val RANGE_KEY = "analytics_range"
        private const val SCOPE_KIND_KEY = "analytics_scope_kind"
        private const val SCOPE_ID_KEY = "analytics_scope_id"
        private const val METRIC_KEY = "analytics_metric"
        private const val SCOPE_ALL = "ALL"
        private const val SCOPE_EXERCISE = "EXERCISE"
        private const val SCOPE_MUSCLE = "MUSCLE"

        fun provideFactory(progressRepository: ProgressRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProgressViewModel(progressRepository, createSavedStateHandle()) }
        }
    }
}
