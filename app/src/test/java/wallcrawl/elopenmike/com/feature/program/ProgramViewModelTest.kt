package wallcrawl.elopenmike.com.feature.program

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy
import wallcrawl.elopenmike.com.core.database.repository.ProgramRepository
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val profile = UserProfile(onboardingCompleted = true, daysPerWeek = 2)
    private val run1 = "11111111-1111-1111-1111-111111111111"
    private val run2 = "22222222-2222-2222-2222-222222222222"

    @Test fun startPauseResumeAndRestartUseDisplayedExpectedRevisions() = runTest {
        val repo = Repository()
        val vm = ProgramViewModel(repo, flowOf(null))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertThat(vm.uiState.value.program).isInstanceOf(ProgramReadState.Empty::class.java)
        vm.act(ProgramAction.START); advanceUntilIdle()
        assertThat(repo.state.value.header.revision).isEqualTo(1)
        vm.act(ProgramAction.PAUSE); advanceUntilIdle()
        assertThat((repo.state.value as ProgramReadState.Supported).enrollment.status).isEqualTo(ProgramStatus.PAUSED)
        vm.act(ProgramAction.RESUME); advanceUntilIdle()
        assertThat((repo.state.value as ProgramReadState.Supported).enrollment.status).isEqualTo(ProgramStatus.ACTIVE)
        vm.act(ProgramAction.RESTART); advanceUntilIdle()
        assertThat(vm.uiState.value.confirmation?.expected?.programId).isEqualTo(run1)
        assertThat(repo.state.value.header.programId).isEqualTo(run1)
        vm.confirm(); advanceUntilIdle()
        assertThat(repo.state.value.header.programId).isEqualTo(run2)
        assertThat(repo.state.value.header.revision).isEqualTo(4)
    }

    @Test fun staleReplaceConfirmationCannotTargetANewRun() = runTest {
        val repo = Repository()
        val vm = ProgramViewModel(repo, flowOf(null))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle(); vm.act(ProgramAction.START); advanceUntilIdle()
        vm.act(ProgramAction.REPLACE); advanceUntilIdle()
        val pending = vm.uiState.value.confirmation
        assertThat(pending).isNotNull()
        repo.act(ProgramAction.RESTART, ProgramExpectation(profile.id, profile.revision, run1, 1), true)
        advanceUntilIdle(); vm.confirm(); advanceUntilIdle()
        assertThat(repo.state.value.header.programId).isEqualTo(run2)
        assertThat(repo.state.value.header.revision).isEqualTo(2)
        assertThat(vm.uiState.value.error).isNotNull()
    }

    @Test fun readFailureAndRetryAreExplicit() = runTest {
        val repo = Repository()
        repo.failRead = true
        val vm = ProgramViewModel(repo, flowOf(null))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        assertThat(vm.uiState.value.error).isNotNull()
        assertThat(vm.uiState.value.program).isNull()
        repo.failRead = false; vm.retry(); advanceUntilIdle()
        assertThat(vm.uiState.value.error).isNull()
        assertThat(vm.uiState.value.program).isInstanceOf(ProgramReadState.Empty::class.java)
    }

    @Test fun unexpectedReadFailuresPropagateWithoutBecomingAnOrdinaryReadError() {
        val failure = IllegalStateException("unexpected programming failure")
        val states = mutableListOf<ProgramUiState>()
        val thrown = org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            runTest {
                val repository = object : ProgramRepository by Repository() {
                    override fun observe(profileId: String): Flow<ProgramReadState> = flow { throw failure }
                }
                val vm = ProgramViewModel(repository, flowOf(null))
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { states += it } }
                advanceUntilIdle()
            }
        }
        assertThat(thrown).hasMessageThat().isEqualTo(failure.message)
        assertThat(states.none { it.error != null }).isTrue()
    }

    @Test fun activeWorkoutBlocksManagementWithoutLosingResume() = runTest {
        val repo = Repository()
        val active = WorkoutSession(name = "Frozen", startedAtTimestamp = 1)
        val vm = ProgramViewModel(repo, flowOf(active))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle(); vm.act(ProgramAction.START); advanceUntilIdle()
        assertThat(vm.uiState.value.activeSession).isEqualTo(active)
        assertThat(repo.state.value).isInstanceOf(ProgramReadState.Empty::class.java)
    }

    private inner class Repository : ProgramRepository {
        val state = MutableStateFlow<ProgramReadState>(ProgramReadState.Empty(ProgramHeader.absent(profile.id), profile))
        var failRead = false
        override fun observeAssociation(sessionId: String): Flow<wallcrawl.elopenmike.com.core.model.ProgramHistoryAssociation?> = kotlinx.coroutines.flow.flowOf(null)
        override suspend fun getHeader(profileId: String) = state.value.header
        override fun observeHeader(profileId: String) = state.map { it.header }
        override suspend fun get(profileId: String) = state.value
        override fun observe(profileId: String): Flow<ProgramReadState> = flow {
            if (failRead) throw ProgramException(ProgramFailure.INVALID_PROGRESS)
            emitAll(state)
        }
        override suspend fun act(action: ProgramAction, expected: ProgramExpectation, confirmed: Boolean, newProgramId: String?) {
            val old = state.value as? ProgramReadState.Supported
            val enrollment = MultiWeekProgramPolicy.transition(profile, old?.enrollment, old?.sessions.orEmpty(),
                action, expected, 100, newProgramId = if (old == null) run1 else run2, confirmed = confirmed)
            state.value = ProgramReadState.Supported(ProgramHeader(profile.id, enrollment.programId, enrollment.revision, "a".repeat(64)),
                enrollment, MultiWeekProgramPolicy.reconstruct(enrollment, emptyList()), emptyList(), profile)
        }
    }
}
