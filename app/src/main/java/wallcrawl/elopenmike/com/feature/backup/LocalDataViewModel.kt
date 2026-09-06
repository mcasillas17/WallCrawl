package wallcrawl.elopenmike.com.feature.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wallcrawl.elopenmike.com.core.backup.ArchiveRejection
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveException
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFormat
import wallcrawl.elopenmike.com.core.backup.LocalDataRestoreRefusedException
import wallcrawl.elopenmike.com.core.database.repository.LocalDataBackupRepository

/**
 * Drives the export, restore, and delete-all controls.
 *
 * Document selection stays in the UI, where the Storage Access Framework contracts live;
 * this receives the chosen [Uri] and owns everything after it. Cancelling the picker never
 * reaches here, so a cancelled selection leaves the screen exactly as it was.
 *
 * One operation runs at a time. A repeated tap while [LocalDataUiState.isBusy] is ignored
 * rather than queued, and the repository serialises across screens as well.
 */
class LocalDataViewModel(
    private val contentResolver: ContentResolver,
    private val repository: LocalDataBackupRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    /**
     * Removes a document an export created but did not finish writing.
     *
     * Injectable because the production call only works for a Storage Access Framework
     * document, which a test cannot create; the behaviour it guards — never leaving a
     * truncated file that looks like a backup — is worth proving either way.
     */
    private val deleteIncompleteDocument: (Uri) -> Unit = { uri ->
        DocumentsContract.deleteDocument(contentResolver, uri)
    }
) : ViewModel() {

    private val state = MutableStateFlow(LocalDataUiState())
    val uiState: StateFlow<LocalDataUiState> = state.asStateFlow()

    init {
        refreshRestoreEligibility()
    }

    /** Filename offered to the document picker; the user is free to change it. */
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(currentTimeMillis()))
        return "wallcrawl-$stamp${LocalDataArchiveFormat.FILE_EXTENSION}"
    }

    fun refreshRestoreEligibility() {
        viewModelScope.launch {
            val allowed = try {
                repository.isRestoreAllowed()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Reported, not swallowed. Leaving the control disabled and still claiming
                // to be "checking" would strand the user on the one screen where a restore
                // is allowed, with no error and no way to retry. The control's own hint
                // carries the explanation, so no separate message repeats it.
                state.update { current ->
                    current.copy(restoreAllowed = null, restoreEligibilityUnknown = true)
                }
                return@launch
            }
            state.update { current ->
                current.copy(restoreAllowed = allowed, restoreEligibilityUnknown = false)
            }
        }
    }

    fun exportTo(destination: Uri) {
        if (state.value.isBusy) return
        state.update { it.copy(operation = LocalDataOperation.EXPORTING, message = null) }

        viewModelScope.launch {
            // Only a document this export actually opened may be discarded on failure.
            // Opening with "wt" is what truncates it; before that the user's chosen
            // destination is untouched, and it may well be a previous export that must
            // survive a failed attempt intact. The archive is fully checked before the
            // stream is opened, so a refused export never reaches this flag at all.
            var truncatedDocument = false
            try {
                val result = withContext(ioDispatcher) {
                    repository.exportTo {
                        // Reached only once the archive has passed every check, so a refusal
                        // that can never succeed does not truncate the destination — which
                        // may be the user's previous export.
                        //
                        // "wt" truncates, so re-exporting over an older, longer archive
                        // cannot leave its tail behind.
                        val output = contentResolver.openOutputStream(destination, "wt")
                            ?: throw IOException(
                                "The chosen document could not be opened for writing."
                            )
                        truncatedDocument = true
                        output.buffered()
                    }
                }
                state.update {
                    it.copy(
                        operation = LocalDataOperation.IDLE,
                        message = LocalDataMessage.ExportSucceeded(
                            sessionCount = result.sessionCount,
                            templateCount = result.templateCount
                        )
                    )
                }
            } catch (error: CancellationException) {
                // Leaving the screen mid-export cancels this coroutine, and the document
                // opened by "wt" is already truncated. NonCancellable keeps the cleanup from
                // being cancelled before it runs, and the controls stop reporting progress
                // that has ended.
                if (truncatedDocument) {
                    withContext(NonCancellable + ioDispatcher) {
                        discardIncompleteDocument(destination)
                    }
                }
                state.update { it.copy(operation = LocalDataOperation.IDLE) }
                throw error
            } catch (error: Exception) {
                if (truncatedDocument) {
                    withContext(ioDispatcher) { discardIncompleteDocument(destination) }
                }
                state.update {
                    it.copy(
                        operation = LocalDataOperation.IDLE,
                        message = exportFailureMessage(error)
                    )
                }
            }
        }
    }

    /**
     * Tells a failure worth retrying apart from one that never will be.
     *
     * Writing runs the same document contract the reader applies, so an export can be
     * refused because the stored history cannot be expressed as one readable archive. Saying
     * "try again" for that would send the user around a loop that cannot end.
     */
    private fun exportFailureMessage(error: Exception): LocalDataMessage = when {
        error !is LocalDataArchiveException -> LocalDataMessage.exportFailed
        error.rejection == ArchiveRejection.TOO_LARGE -> LocalDataMessage.exportTooLarge
        else -> LocalDataMessage.exportRefused
    }

    fun restoreFrom(source: Uri) {
        if (state.value.isBusy) return
        state.update { it.copy(operation = LocalDataOperation.RESTORING, message = null) }

        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    val input = contentResolver.openInputStream(source)
                        ?: throw IOException("The chosen document could not be opened for reading.")
                    input.buffered().use { stream -> repository.restoreFrom(stream) }
                }
                state.update {
                    it.copy(
                        operation = LocalDataOperation.IDLE,
                        restoreAllowed = false,
                        restoredOnboardingCompleted = result.onboardingCompleted,
                        message = LocalDataMessage.RestoreSucceeded(
                            sessionCount = result.sessionCount,
                            templateCount = result.templateCount
                        )
                    )
                }
            } catch (error: CancellationException) {
                // A cancelled restore wrote nothing: the transaction rolls back and no
                // document was created. Only the progress state needs clearing.
                state.update { it.copy(operation = LocalDataOperation.IDLE) }
                throw error
            } catch (error: Exception) {
                val message = when (error) {
                    // The codec's own message stays out of the UI; only its typed reason
                    // decides the copy, so no archived value can be echoed back to the user.
                    is LocalDataArchiveException -> rejectionMessage(error.rejection)
                    is LocalDataRestoreRefusedException -> LocalDataMessage.restoreNeedsEmptyApp
                    // Only a failure to open or read the chosen document should send the
                    // user back to the file picker. Anything else — a database failure, or
                    // the document ending mid-read — is reported as a restore that failed
                    // and changed nothing, so the advice matches what actually went wrong.
                    is FileNotFoundException, is SecurityException ->
                        LocalDataMessage.documentUnavailable

                    else -> LocalDataMessage.restoreFailed
                }
                state.update { it.copy(operation = LocalDataOperation.IDLE, message = message) }
                refreshRestoreEligibility()
            }
        }
    }

    fun requestDeleteConfirmation() {
        if (state.value.isBusy) return
        state.update { it.copy(isConfirmingDelete = true, message = null) }
    }

    fun cancelDeleteConfirmation() {
        state.update { it.copy(isConfirmingDelete = false) }
    }

    fun confirmDeleteAllLocalData() {
        if (state.value.isBusy) return
        state.update {
            it.copy(operation = LocalDataOperation.DELETING, isConfirmingDelete = false)
        }

        viewModelScope.launch {
            try {
                repository.deleteAllLocalData()
                state.update {
                    it.copy(
                        operation = LocalDataOperation.IDLE,
                        restoreAllowed = true,
                        deletionCompleted = true
                    )
                }
            } catch (error: CancellationException) {
                state.update { it.copy(operation = LocalDataOperation.IDLE) }
                throw error
            } catch (error: Exception) {
                state.update {
                    it.copy(
                        operation = LocalDataOperation.IDLE,
                        message = LocalDataMessage.deleteFailed
                    )
                }
            }
        }
    }

    /**
     * Drops a document this export truncated but never finished writing.
     *
     * The call is a cross-process request to the document provider, which may be backed by
     * a network, so callers run it off the main thread. Some providers refuse deletion,
     * which is why no message promises the file is gone: the export copy tells the user to
     * delete it if one was left behind.
     */
    private fun discardIncompleteDocument(destination: Uri) {
        runCatching { deleteIncompleteDocument(destination) }
    }

    /**
     * Clears the one-shot navigation signals once the host screen has acted on them, so a
     * recomposition or a configuration change cannot navigate a second time.
     */
    fun consumeNavigation() {
        state.update { it.copy(restoredOnboardingCompleted = null, deletionCompleted = false) }
    }

    companion object {
        fun provideFactory(
            contentResolver: ContentResolver,
            repository: LocalDataBackupRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LocalDataViewModel(contentResolver, repository) as T
            }
        }
    }
}
