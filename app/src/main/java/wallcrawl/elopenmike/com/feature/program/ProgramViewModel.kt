package wallcrawl.elopenmike.com.feature.program

import androidx.lifecycle.ViewModel
import android.database.sqlite.SQLiteException
import java.io.IOException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.database.repository.ProgramRepository
import wallcrawl.elopenmike.com.core.model.*

class ProgramViewModel(private val repository: ProgramRepository, activeSessions: Flow<WorkoutSession?>) : ViewModel() {
    private val refresh = MutableStateFlow(0L)
    private val control = MutableStateFlow(ProgramUiState(loading = false))
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val program = refresh.flatMapLatest {
        repository.observe().map { ProgramUiState(loading = false, program = it) }
            .onStart { emit(ProgramUiState()) }
            .catch { error ->
                if (error is CancellationException) throw error
                if (!isExpectedProgramReadFailure(error)) throw error
                emit(ProgramUiState(loading = false, error = R.string.program_read_failed))
            }
    }
    val uiState: StateFlow<ProgramUiState> = combine(program, activeSessions, control) { read, active, actions ->
        read.copy(activeSession = active, saving = actions.saving,
            error = read.error ?: actions.error, confirmation = actions.confirmation)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), ProgramUiState())

    fun act(action: ProgramAction) {
        val state = uiState.value
        if (state.loading || state.saving || state.activeSession != null) return
        val read = state.program ?: return
        val profile = read.profile ?: return
        val expected = ProgramExpectation(profile.id, profile.revision, read.header.programId, read.header.revision)
        if (action == ProgramAction.RESTART || action == ProgramAction.REPLACE) {
            control.value = control.value.copy(confirmation = ProgramConfirmation(action, expected), error = null)
        } else perform(action, expected, false)
    }

    fun confirm() {
        val pending = control.value.confirmation ?: return
        control.value = control.value.copy(confirmation = null)
        perform(pending.action, pending.expected, true)
    }

    fun dismissConfirmation() { control.value = control.value.copy(confirmation = null) }
    fun retry() { control.value = control.value.copy(error = null); refresh.value++ }

    private fun perform(action: ProgramAction, expected: ProgramExpectation, confirmed: Boolean) {
        if (control.value.saving) return
        control.value = control.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                repository.act(action, expected, confirmed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ProgramException) {
                control.value = control.value.copy(error = programFailureRes(error.failure))
            } catch (error: SQLiteException) {
                control.value = control.value.copy(error = R.string.program_write_failed)
            } catch (error: IOException) {
                control.value = control.value.copy(error = R.string.program_write_failed)
            } catch (error: IllegalArgumentException) {
                control.value = control.value.copy(error = R.string.program_write_failed)
            } finally {
                control.value = control.value.copy(saving = false)
            }
        }
    }

    companion object {
        fun provideFactory(repository: ProgramRepository, activeSessions: Flow<WorkoutSession?>): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = ProgramViewModel(repository, activeSessions) as T
            }
    }
}

internal fun programFailureRes(failure: ProgramFailure): Int = when (failure) {
    ProgramFailure.UNSUPPORTED_POLICY -> R.string.program_unsupported
    ProgramFailure.PROFILE_MISMATCH -> R.string.program_profile_mismatch
    ProgramFailure.UNSUPPORTED_CONFIGURATION -> R.string.program_frequency_unsupported
    ProgramFailure.PROFILE_UNAVAILABLE -> R.string.program_profile_unavailable
    ProgramFailure.STALE_STATE -> R.string.program_stale
    ProgramFailure.ACTIVE_WORKOUT -> R.string.program_active_workout
    else -> R.string.program_write_failed
}

// Stored identity and binding validation uses require; programming failures must still propagate.
internal fun isExpectedProgramReadFailure(error: Throwable): Boolean =
    error is IllegalArgumentException || error is SQLiteException || error is IOException
