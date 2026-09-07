package wallcrawl.elopenmike.com.feature.progress

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.database.repository.ProgressRepository
import wallcrawl.elopenmike.com.core.database.repository.ProgressSnapshot
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun queryFailureIsAnErrorAndRetryCanRecover() = runTest {
        val repository = FakeProgressRepository().apply { failure = true }
        val viewModel = ProgressViewModel(repository, nowTimestamp = { MONDAY }, zoneId = { UTC })
        assertThat(viewModel.uiState.value).isEqualTo(ProgressUiState.Loading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertThat((viewModel.uiState.value as ProgressUiState.Error).messageRes)
            .isEqualTo(R.string.progress_error)

        repository.failure = false
        viewModel.refresh()
        runCurrent()
        assertThat(viewModel.uiState.value).isInstanceOf(ProgressUiState.Success::class.java)
    }

    @Test
    fun importAndDeletionReplaceTheWholeSnapshot() = runTest {
        val repository = FakeProgressRepository()
        val viewModel = ProgressViewModel(repository, nowTimestamp = { MONDAY }, zoneId = { UTC })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        repository.snapshot.value = snapshot(workouts = 3)
        runCurrent()
        assertThat((viewModel.uiState.value as ProgressUiState.Success).overview.workoutsThisWeek)
            .isEqualTo(3)
        repository.snapshot.value = null
        runCurrent()
        assertThat(viewModel.uiState.value).isEqualTo(ProgressUiState.NoProfile)
    }

    @Test
    fun midnightMondayRefreshesWithoutAWorkoutWrite() = runTest {
        val start = MONDAY - 1_000L
        val repository = FakeProgressRepository()
        val viewModel = ProgressViewModel(
            repository, nowTimestamp = { start + testScheduler.currentTime }, zoneId = { UTC }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertThat(repository.requests).hasSize(1)
        advanceTimeBy(999)
        runCurrent()
        assertThat(repository.requests).hasSize(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(repository.requests.map { it.first.toEpochMilli() })
            .containsExactly(start, MONDAY).inOrder()
    }

    @Test
    fun timeZoneChangeAndResumeRefreshUseTheNewCalendarWeek() = runTest {
        var zone = UTC
        val repository = FakeProgressRepository()
        val viewModel = ProgressViewModel(repository, nowTimestamp = { MONDAY }, zoneId = { zone })
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        runCurrent()
        zone = ZoneId.of("America/Los_Angeles")
        viewModel.refresh()
        runCurrent()
        assertThat(repository.requests.last().second).isEqualTo(zone)
        assertThat(TrainingWeek.containing(repository.requests.last().first, zone).startEpochDay)
            .isLessThan(TrainingWeek.containing(Instant.ofEpochMilli(MONDAY), UTC).startEpochDay)

        collector.cancel()
        runCurrent()
        val requestCount = repository.requests.size
        advanceTimeBy(8 * 86_400_000L)
        runCurrent()
        assertThat(repository.requests).hasSize(requestCount)
        assertThat(viewModel.uiState.value).isEqualTo(ProgressUiState.Loading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertThat(repository.requests).hasSize(requestCount + 1)
    }

    @Test
    fun aFailedRefreshDoesNotKeepOldActivityBesideAnEmptyLedger() = runTest {
        val repository = FakeProgressRepository()
        val viewModel = ProgressViewModel(repository, nowTimestamp = { MONDAY }, zoneId = { UTC })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        assertThat(viewModel.uiState.value).isInstanceOf(ProgressUiState.Success::class.java)
        repository.failure = true
        viewModel.refresh()
        runCurrent()
        assertThat(viewModel.uiState.value).isInstanceOf(ProgressUiState.Error::class.java)
    }

    private class FakeProgressRepository : ProgressRepository {
        val snapshot = MutableStateFlow<ProgressSnapshot?>(snapshot())
        val requests = mutableListOf<Pair<Instant, ZoneId>>()
        var failure = false
        override fun observeProgress(now: () -> Instant, zoneId: ZoneId): Flow<ProgressSnapshot?> = flow {
            requests += now() to zoneId
            check(!failure) { "query or catalog failure" }
            emitAll(snapshot)
        }
    }

    companion object {
        private val UTC = ZoneId.of("UTC")
        private val MONDAY = Instant.parse("2026-09-07T00:00:00Z").toEpochMilli()
        private fun snapshot(workouts: Int = 0): ProgressSnapshot {
            val week = TrainingWeek.containing(Instant.ofEpochMilli(MONDAY), UTC)
            fun ledger(epochDay: Long) = WeeklyDoseLedger(
                LedgerPolicyVersion.PRIMARY_ONLY_V1, epochDay, UTC.id, "test", 1,
                emptyMap(), emptyMap(), emptyMap()
            )
            return ProgressSnapshot(
                overview = ProgressOverview(workoutsThisWeek = workouts),
                preferredUnit = WeightUnit.KG,
                week = week,
                reviewedDose = ledger(week.startEpochDay),
                previousReviewedDose = ledger(week.startEpochDay - 7)
            )
        }
    }
}
