package wallcrawl.elopenmike.com.feature.history

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.WorkoutHistoryDetail
import wallcrawl.elopenmike.com.core.database.repository.WorkoutHistoryPage
import wallcrawl.elopenmike.com.core.database.repository.WorkoutHistoryRepository
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun detailReadsOnlyIdAndPreservesOriginalTextAcrossDeletion() = runTest {
        val repository = FakeHistoryRepository()
        val vm = WorkoutHistoryDetailViewModel("viewed", repository)
        assertThat(vm.uiState.value).isEqualTo(WorkoutHistoryDetailUiState.Loading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        val loaded = vm.uiState.value as WorkoutHistoryDetailUiState.Loaded
        assertThat(loaded.detail.session.name).isEqualTo("Original nombre • 그대로")
        assertThat(loaded.detail.session.notes).isEqualTo("Do not translate\n  original notes")
        assertThat(repository.detailIds).containsExactly("viewed")
        repository.detail.value = null
        runCurrent()
        assertThat(vm.uiState.value).isEqualTo(WorkoutHistoryDetailUiState.Missing)
    }

    @Test fun noncompletedDetailIsUnsupportedNotLoadingOrMissing() = runTest {
        val repository = FakeHistoryRepository().apply {
            detail.value = detail.value!!.copy(session = historySession().copy(status = SessionStatus.CANCELLED), summary = null)
        }
        val vm = WorkoutHistoryDetailViewModel("viewed", repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value).isInstanceOf(WorkoutHistoryDetailUiState.Unsupported::class.java)
    }

    @Test fun refreshFailureClearsLoadedDetailAndRetryResubscribes() = runTest {
        val repository = FakeHistoryRepository()
        val vm = WorkoutHistoryDetailViewModel("viewed", repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value).isInstanceOf(WorkoutHistoryDetailUiState.Loaded::class.java)
        repository.failure = true
        vm.retry()
        runCurrent()
        assertThat(vm.uiState.value).isEqualTo(WorkoutHistoryDetailUiState.ReadError)
        repository.failure = false
        vm.retry()
        runCurrent()
        assertThat(vm.uiState.value).isInstanceOf(WorkoutHistoryDetailUiState.Loaded::class.java)
    }

    @Test fun stoppedCollectionClearsReplayAndResumeReadsAgain() = runTest {
        val repository = FakeHistoryRepository()
        val vm = WorkoutHistoryDetailViewModel("viewed", repository)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        collector.cancel()
        runCurrent()
        assertThat(vm.uiState.value).isEqualTo(WorkoutHistoryDetailUiState.Loading)
        repository.detail.value = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value).isEqualTo(WorkoutHistoryDetailUiState.Missing)
        assertThat(repository.detailIds).hasSize(2)
    }

    @Test fun malformedKnownRecordBecomesVisibleReadError() = runTest {
        val repository = FakeHistoryRepository().apply { detailFailure = IllegalArgumentException("Malformed known provenance") }
        val vm = WorkoutHistoryDetailViewModel("viewed", repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value).isEqualTo(WorkoutHistoryDetailUiState.ReadError)
    }

    @Test fun cancellationDoesNotMasqueradeAsAReadError() = runTest {
        val repository = FakeHistoryRepository().apply { detailFailure = CancellationException("Collection stopped") }
        val vm = WorkoutHistoryDetailViewModel("viewed", repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value).isEqualTo(WorkoutHistoryDetailUiState.Loading)
    }

    @Test fun pagesReplaceRatherThanAppendAndSavedPageSurvivesRecreation() = runTest {
        val repository = FakeHistoryRepository()
        val saved = SavedStateHandle()
        val vm = WorkoutHistoryListViewModel(repository, saved)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        vm.older()
        runCurrent()
        val older = vm.uiState.value as WorkoutHistoryListUiState.Loaded
        assertThat(older.page).isEqualTo(1)
        assertThat(older.sessions.map { it.id }).containsExactly("page-1")
        assertThat(saved.get<Int>("history_page")).isEqualTo(1)
        val recreated = WorkoutHistoryListViewModel(repository, SavedStateHandle(mapOf("history_page" to 1)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { recreated.uiState.collect {} }
        runCurrent()
        assertThat((recreated.uiState.value as WorkoutHistoryListUiState.Loaded).page).isEqualTo(1)
        recreated.newer()
        runCurrent()
        recreated.newer()
        runCurrent()
        assertThat((recreated.uiState.value as WorkoutHistoryListUiState.Loaded).page).isEqualTo(0)
    }

    @Test fun listReadErrorClearsRowsAndRetryUsesSamePage() = runTest {
        val repository = FakeHistoryRepository()
        val vm = WorkoutHistoryListViewModel(repository, SavedStateHandle(mapOf("history_page" to 3)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        repository.failure = true
        vm.retry()
        runCurrent()
        assertThat(vm.uiState.value).isEqualTo(WorkoutHistoryListUiState.ReadError(3))
        repository.failure = false
        vm.retry()
        runCurrent()
        assertThat((vm.uiState.value as WorkoutHistoryListUiState.Loaded).page).isEqualTo(3)
        assertThat(repository.pages).containsExactly(3, 3, 3).inOrder()
    }

    @Test fun deletionCanLeaveEmptyPageWithWorkingNewerControl() = runTest {
        val repository = FakeHistoryRepository().apply { emptyPages = true }
        val vm = WorkoutHistoryListViewModel(repository, SavedStateHandle(mapOf("history_page" to 2)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        assertThat((vm.uiState.value as WorkoutHistoryListUiState.Loaded).sessions).isEmpty()
        vm.older()
        runCurrent()
        assertThat(repository.pages).containsExactly(2)
        vm.newer()
        runCurrent()
        assertThat((vm.uiState.value as WorkoutHistoryListUiState.Loaded).page).isEqualTo(1)
    }
}

internal class FakeHistoryRepository : WorkoutHistoryRepository {
    var failure = false
    var detailFailure: Exception? = null
    var emptyPages = false
    val detailIds = mutableListOf<String>()
    val pages = mutableListOf<Int>()
    val detail = MutableStateFlow<WorkoutHistoryDetail?>(historyDetail())
    override fun observeDetail(sessionId: String): Flow<WorkoutHistoryDetail?> = flow {
        detailIds += sessionId
        detailFailure?.let { throw it }
        check(!failure) { "Unreadable stored record" }
        emitAll(detail)
    }
    override fun observeHistory(page: Int): Flow<WorkoutHistoryPage> = flow {
        pages += page
        check(!failure) { "Unreadable stored page" }
        val session = historySession()
        val entry = wallcrawl.elopenmike.com.core.model.WorkoutHistoryEntry(
            "page-$page", session.name, session.completedAtTimestamp, session.actualDurationMinutes
        )
        emit(WorkoutHistoryPage(if (emptyPages) emptyList() else listOf(entry), !emptyPages))
    }
}

internal fun historySession() = WorkoutSession(
    id = "viewed", name = "Original nombre • 그대로", notes = "Do not translate\n  original notes",
    startedAtTimestamp = 1_000, completedAtTimestamp = 2_000, status = SessionStatus.COMPLETED
)

internal fun historyDetail() = WorkoutHistoryDetail(
    historySession(), WorkoutSummary("viewed", "Original nombre • 그대로", 1, 0, 0.0),
    null, emptyMap(), emptyMap()
)
