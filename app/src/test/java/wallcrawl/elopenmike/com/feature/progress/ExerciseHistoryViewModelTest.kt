package wallcrawl.elopenmike.com.feature.progress

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.ExerciseHistorySnapshot
import wallcrawl.elopenmike.com.core.database.repository.ProgressAnalyticsSnapshot
import wallcrawl.elopenmike.com.core.database.repository.ProgressRepository
import wallcrawl.elopenmike.com.core.database.repository.ProgressSnapshot
import wallcrawl.elopenmike.com.core.model.AnalyticsRange
import wallcrawl.elopenmike.com.core.model.AnalyticsScope
import wallcrawl.elopenmike.com.core.model.PerformanceObservation
import wallcrawl.elopenmike.com.core.model.PerformanceSeries
import wallcrawl.elopenmike.com.core.model.PerformanceShape
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseHistoryViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val zone = ZoneId.of("America/Los_Angeles")
    private val nov2 = LocalDate.of(2026, 11, 2)

    private fun observation(id: String, iso: String) =
        PerformanceObservation(id, id, Instant.parse(iso).toEpochMilli(), listOf(id), 15.0, 8)

    private val points = listOf(
        observation("older", "2023-11-02T02:00:00Z"),
        observation("oct", "2026-11-01T06:59:59Z"),
        observation("first-1am", "2026-11-01T08:15:00Z"),
        observation("second-1am", "2026-11-01T09:15:00Z"),
        observation("monday", "2026-11-02T08:00:00Z")
    )
    private val snapshot = ExerciseHistorySnapshot(
        exerciseOptions = listOf("press"), catalogNames = mapOf("press" to "Press"), exerciseId = "press",
        preferredUnit = WeightUnit.LBS, performance = listOf(PerformanceSeries(PerformanceShape.WEIGHTED_REPS, points, null))
    )
    private inner class Repository : ProgressRepository {
        val reads = mutableListOf<String?>()
        val history = MutableStateFlow<ExerciseHistorySnapshot?>(snapshot)
        override fun observeExerciseHistory(exerciseId: String?, now: () -> Instant): Flow<ExerciseHistorySnapshot?> {
            reads += exerciseId
            return history
        }
        override fun observeProgress(now: () -> Instant, zoneId: ZoneId): Flow<ProgressSnapshot?> = emptyFlow()
        override fun observeAnalytics(range: AnalyticsRange, scope: AnalyticsScope, now: () -> Instant, zoneId: ZoneId): Flow<ProgressAnalyticsSnapshot?> = emptyFlow()
    }

    @Test fun inclusiveLocalDatesAcrossDstAndAllTimeUseTheSameHistoryRead() = runTest {
        val repo = Repository()
        val saved = SavedStateHandle()
        val vm = ProgressViewModel(repo, saved, { Instant.parse("2026-11-02T22:00:00Z").toEpochMilli() }, { zone }, kotlinx.coroutines.Dispatchers.Main)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.exerciseHistoryState.collect {} }
        fun state(): ExerciseHistoryUiState.Loaded {
            runCurrent()
            return vm.exerciseHistoryState.value as ExerciseHistoryUiState.Loaded
        }
        fun ids() = state().performance.flatMap { s -> s.observations.map { it.sessionId } }
        assertThat(state().period).isEqualTo(ProgressPeriod.MONTH)
        assertThat(ids()).containsExactly("first-1am", "second-1am", "monday").inOrder()
        vm.selectHistoryPeriod(ProgressPeriod.WEEK)
        assertThat(ids()).containsExactly("monday")
        vm.selectHistoryPeriod(ProgressPeriod.YEAR)
        assertThat(ids()).hasSize(4)
        vm.selectHistoryPeriod(ProgressPeriod.ALL)
        assertThat(ids()).hasSize(5)
        vm.selectHistoryCustom(nov2.minusDays(1), nov2.minusDays(1))
        assertThat(ids()).containsExactly("first-1am", "second-1am").inOrder()
        vm.selectHistoryCustom(nov2, nov2.minusDays(1))
        vm.selectHistoryCustom(nov2, nov2.plusDays(1))
        assertThat(ids()).hasSize(2)
        assertThat(repo.reads).containsExactly(null)
        vm.selectHistoryCustom(nov2.minusDays(4), nov2.minusDays(3))
        assertThat(ids()).isEmpty()
        vm.selectHistoryExercise("press")
        assertThat(state().exerciseId).isEqualTo("press")
        assertThat(repo.reads.last()).isEqualTo("press")
        val restored = ProgressViewModel(repo, saved, { Instant.parse("2026-11-02T22:00:00Z").toEpochMilli() }, { zone }, kotlinx.coroutines.Dispatchers.Main)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.exerciseHistoryState.collect {} }
        runCurrent()
        assertThat(restored.exerciseHistoryState.value).isEqualTo(state())
        repo.history.value = null
        runCurrent()
        assertThat(vm.exerciseHistoryState.value).isEqualTo(ExerciseHistoryUiState.NoProfile)
    }

    @Test fun monthChangesAtLocalMidnightWithoutAWorkoutWrite() = runTest {
        val repo = Repository()
        val initial = Instant.parse("2026-11-01T06:59:59Z").toEpochMilli()
        val vm = ProgressViewModel(repo, nowTimestamp = { initial + testScheduler.currentTime }, zoneId = { zone }, analyticsDispatcher = kotlinx.coroutines.Dispatchers.Main)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.exerciseHistoryState.collect {} }
        runCurrent()
        assertThat((vm.exerciseHistoryState.value as ExerciseHistoryUiState.Loaded).start).isEqualTo(LocalDate.of(2026,10,1))
        advanceTimeBy(1000)
        runCurrent()
        assertThat((vm.exerciseHistoryState.value as ExerciseHistoryUiState.Loaded).start).isEqualTo(LocalDate.of(2026,11,1))
    }
}
