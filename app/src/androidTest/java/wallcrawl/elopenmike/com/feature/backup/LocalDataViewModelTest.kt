package wallcrawl.elopenmike.com.feature.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveCodec
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFixtures
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.LocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.LocalDataExportResult
import wallcrawl.elopenmike.com.core.database.repository.LocalDataRestoreResult
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.backup.ArchiveRejection
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveException
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * The document half of the feature, driven through a real [android.content.ContentResolver].
 *
 * File URIs stand in for the Storage Access Framework's document URIs: the resolver, the
 * streams, and the open/flush/close sequence are the real ones, which is what the "not
 * successful until the document is closed" claim depends on.
 */
@RunWith(AndroidJUnit4::class)
class LocalDataViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: WallCrawlDatabase
    private lateinit var viewModel: LocalDataViewModel
    private lateinit var documentDirectory: File

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        documentDirectory = File(context.cacheDir, "local-data-test").apply {
            deleteRecursively()
            mkdirs()
        }
        viewModel = LocalDataViewModel(
            contentResolver = context.contentResolver,
            repository = OfflineLocalDataBackupRepository(
                backupDao = database.localDataBackupDao(),
                appVersionName = "test",
                appVersionCode = 1L,
                catalogCommit = { null }
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
        documentDirectory.deleteRecursively()
    }

    @Test
    fun exportThenDeleteThenRestore_returnsTheSameStateAndReportsEachStep() = runBlocking {
        seedFixtureData()
        val destination = documentUri("backup.wallcrawl.json")

        viewModel.exportTo(destination)
        val exported = awaitState { it.message != null }
        assertThat(exported.message).isInstanceOf(LocalDataMessage.ExportSucceeded::class.java)
        val exportMessage = exported.message as LocalDataMessage.ExportSucceeded
        assertThat(exportMessage.sessionCount).isEqualTo(4)
        assertThat(exportMessage.templateCount).isEqualTo(2)

        viewModel.confirmDeleteAllLocalData()
        val deleted = awaitState { it.deletionCompleted }
        assertThat(deleted.deletionCompleted).isTrue()
        assertThat(deleted.restoreAllowed).isTrue()
        assertThat(database.localDataBackupDao().countSessions()).isEqualTo(0)
        viewModel.consumeNavigation()

        viewModel.restoreFrom(destination)
        val restored = awaitState { it.message != null }
        assertThat(restored.message).isInstanceOf(LocalDataMessage.RestoreSucceeded::class.java)
        assertThat(restored.restoredOnboardingCompleted).isTrue()
        assertThat(database.localDataBackupDao().countSessions()).isEqualTo(4)
        assertThat(database.localDataBackupDao().countTemplates()).isEqualTo(2)
    }

    @Test
    fun restore_reportsWhyAnUnusableDocumentWasRefusedWithoutWriting() = runBlocking {
        val destination = documentUri("not-an-archive.json")
        File(destination.path!!).writeText("{\"hello\":\"world\"}")

        viewModel.restoreFrom(destination)
        val state = awaitState { it.message != null }

        // Resource-backed copy, chosen by the codec's typed reason. The parser's own text
        // never reaches the screen, so it cannot echo anything the document contained.
        assertThat(state.message)
            .isEqualTo(LocalDataMessage.Failure(R.string.local_data_rejected_malformed))
        assertThat(database.localDataBackupDao().readAll().sessions).isEmpty()
    }

    @Test
    fun restore_refusesWhenTheAppStillHoldsDataAndSaysWhatToDoFirst() = runBlocking {
        seedFixtureData()
        val destination = documentUri("backup.wallcrawl.json")
        viewModel.exportTo(destination)
        awaitState { it.message is LocalDataMessage.ExportSucceeded }

        viewModel.restoreFrom(destination)
        val state = awaitState { it.message !is LocalDataMessage.ExportSucceeded }

        assertThat(state.message).isEqualTo(LocalDataMessage.restoreNeedsEmptyApp)
        assertThat(state.restoreAllowed).isFalse()
        assertThat(database.localDataBackupDao().countSessions()).isEqualTo(4)
    }

    @Test
    fun export_reportsFailureForADestinationItCannotOpen() = runBlocking {
        seedFixtureData()

        viewModel.exportTo(Uri.parse("file:///does/not/exist/backup.json"))
        val state = awaitState { it.message != null }

        assertThat(state.message).isEqualTo(LocalDataMessage.exportFailed)
    }

    @Test
    fun repeatedTaps_areIgnoredWhileAnOperationIsRunning() = runBlocking {
        seedFixtureData()
        val first = documentUri("first.wallcrawl.json")
        val second = documentUri("second.wallcrawl.json")

        viewModel.exportTo(first)
        // The second request arrives before the first settles and must not start a
        // competing export.
        viewModel.exportTo(second)
        awaitState { it.message != null }

        assertThat(File(second.path!!).exists()).isFalse()
        assertThat(File(first.path!!).length()).isGreaterThan(0L)
    }

    @Test
    fun cancelledExport_discardsTheIncompleteDocumentItCreated() = runBlocking {
        // Leaving the screen mid-export cancels the coroutine. The document opened for
        // writing is already truncated, so it must not be left looking like a backup.
        val discarded = mutableListOf<Uri>()
        val started = CompletableDeferred<Unit>()
        val cancellingViewModel = LocalDataViewModel(
            contentResolver = context.contentResolver,
            repository = object : LocalDataBackupRepository {
                override suspend fun isRestoreAllowed() = false

                override suspend fun exportTo(
                    openOutput: () -> OutputStream
                ): LocalDataExportResult {
                    openOutput()
                    started.complete(Unit)
                    throw kotlinx.coroutines.CancellationException("screen left")
                }

                override suspend fun restoreFrom(input: InputStream) =
                    LocalDataRestoreResult(false, 0, 0)

                override suspend fun deleteAllLocalData() = Unit
            },
            deleteIncompleteDocument = { uri -> discarded.add(uri) }
        )
        val destination = documentUri("cancelled.wallcrawl.json")

        cancellingViewModel.exportTo(destination)
        started.await()
        withTimeout(SETTLE_TIMEOUT_MILLIS) {
            while (discarded.isEmpty()) delay(POLL_INTERVAL_MILLIS)
        }

        assertThat(discarded).containsExactly(destination)
        // The controls stop reporting progress that has ended.
        withTimeout(SETTLE_TIMEOUT_MILLIS) {
            while (cancellingViewModel.uiState.value.isBusy) delay(POLL_INTERVAL_MILLIS)
        }
    }

    @Test
    fun failedExport_discardsTheIncompleteDocumentItCreated() = runBlocking {
        seedFixtureData()
        val discarded = mutableListOf<Uri>()
        val failingViewModel = LocalDataViewModel(
            contentResolver = context.contentResolver,
            repository = object : LocalDataBackupRepository {
                override suspend fun isRestoreAllowed() = false

                override suspend fun exportTo(
                    openOutput: () -> OutputStream
                ): LocalDataExportResult {
                    openOutput()
                    throw java.io.IOException("the provider ran out of space")
                }

                override suspend fun restoreFrom(input: InputStream) =
                    LocalDataRestoreResult(false, 0, 0)

                override suspend fun deleteAllLocalData() = Unit
            },
            deleteIncompleteDocument = { uri -> discarded.add(uri) }
        )
        val destination = documentUri("failed.wallcrawl.json")

        failingViewModel.exportTo(destination)
        withTimeout(SETTLE_TIMEOUT_MILLIS) {
            while (failingViewModel.uiState.value.message == null) delay(POLL_INTERVAL_MILLIS)
        }

        assertThat(discarded).containsExactly(destination)
        assertThat(failingViewModel.uiState.value.message)
            .isEqualTo(LocalDataMessage.exportFailed)
    }

    @Test
    fun export_doesNotDeleteADestinationItCouldNotOpen() = runBlocking {
        // The destination may be a previous export. A failure to open never truncated it,
        // so WallCrawl must not delete a file the user still has.
        seedFixtureData()
        val discarded = mutableListOf<Uri>()
        val guardedViewModel = LocalDataViewModel(
            contentResolver = context.contentResolver,
            repository = OfflineLocalDataBackupRepository(
                backupDao = database.localDataBackupDao(),
                appVersionName = "test",
                appVersionCode = 1L,
                catalogCommit = { null }
            ),
            deleteIncompleteDocument = { uri -> discarded.add(uri) }
        )

        guardedViewModel.exportTo(Uri.parse("file:///does/not/exist/backup.json"))
        withTimeout(SETTLE_TIMEOUT_MILLIS) {
            while (guardedViewModel.uiState.value.message == null) delay(POLL_INTERVAL_MILLIS)
        }

        assertThat(discarded).isEmpty()
        assertThat(guardedViewModel.uiState.value.message)
            .isEqualTo(LocalDataMessage.exportFailed)
    }

    @Test
    fun export_refusedByTheArchiveContract_doesNotTellTheUserToRetry() = runBlocking {
        val refusingViewModel = LocalDataViewModel(
            contentResolver = context.contentResolver,
            repository = object : LocalDataBackupRepository {
                override suspend fun isRestoreAllowed() = false

                // Refused before the destination is opened, exactly as the codec does.
                override suspend fun exportTo(
                    openOutput: () -> OutputStream
                ): LocalDataExportResult = throw LocalDataArchiveException(
                    ArchiveRejection.TOO_LARGE,
                    "The archive would exceed the character limit."
                )

                override suspend fun restoreFrom(input: InputStream) =
                    LocalDataRestoreResult(false, 0, 0)

                override suspend fun deleteAllLocalData() = Unit
            },
            deleteIncompleteDocument = {}
        )

        refusingViewModel.exportTo(documentUri("too-large.wallcrawl.json"))
        withTimeout(SETTLE_TIMEOUT_MILLIS) {
            while (refusingViewModel.uiState.value.message == null) delay(POLL_INTERVAL_MILLIS)
        }

        assertThat(refusingViewModel.uiState.value.message)
            .isEqualTo(LocalDataMessage.exportTooLarge)
    }

    @Test
    fun restore_reportsATransportFailureAsRetryableRatherThanABadFile() = runBlocking {
        val failingViewModel = LocalDataViewModel(
            contentResolver = context.contentResolver,
            repository = object : LocalDataBackupRepository {
                override suspend fun isRestoreAllowed() = true

                override suspend fun exportTo(openOutput: () -> OutputStream) =
                    LocalDataExportResult(0, 0)

                override suspend fun restoreFrom(input: InputStream): LocalDataRestoreResult =
                    throw java.io.IOException("connection dropped")

                override suspend fun deleteAllLocalData() = Unit
            },
            deleteIncompleteDocument = {}
        )
        val source = documentUri("interrupted.wallcrawl.json")
        File(source.path!!).writeText("{}")

        failingViewModel.restoreFrom(source)
        withTimeout(SETTLE_TIMEOUT_MILLIS) {
            while (failingViewModel.uiState.value.message == null) delay(POLL_INTERVAL_MILLIS)
        }

        // Truthful and retryable, not "this isn't a WallCrawl export file".
        assertThat(failingViewModel.uiState.value.message)
            .isEqualTo(LocalDataMessage.restoreFailed)
    }

    @Test
    fun eligibilityCheckFailure_isReportedAndLeavesRestoreAttemptable() = runBlocking {
        // A one-off read failure must not strand the user on the screen where a restore is
        // allowed, with a control disabled forever and copy still claiming to be checking.
        val unreadableViewModel = LocalDataViewModel(
            contentResolver = context.contentResolver,
            repository = object : LocalDataBackupRepository {
                override suspend fun isRestoreAllowed(): Boolean =
                    throw java.io.IOException("the database could not be read")

                override suspend fun exportTo(openOutput: () -> OutputStream) =
                    LocalDataExportResult(0, 0)

                override suspend fun restoreFrom(input: InputStream) =
                    LocalDataRestoreResult(false, 0, 0)

                override suspend fun deleteAllLocalData() = Unit
            },
            deleteIncompleteDocument = {}
        )

        val state = withTimeout(SETTLE_TIMEOUT_MILLIS) {
            while (!unreadableViewModel.uiState.value.restoreEligibilityUnknown) {
                delay(POLL_INTERVAL_MILLIS)
            }
            unreadableViewModel.uiState.value
        }

        // Reported through the control's own hint rather than a second message repeating it.
        assertThat(state.restoreAllowed).isNull()
        assertThat(state.message).isNull()
    }

    @Test
    fun suggestedFileName_usesTheArchiveExtension() {
        assertThat(viewModel.suggestedFileName()).endsWith(".wallcrawl.json")
    }

    private suspend fun seedFixtureData() {
        val document = ByteArrayOutputStream().also {
            LocalDataArchiveCodec.write(LocalDataArchiveFixtures.archive()) { it }
        }.toByteArray()
        OfflineLocalDataBackupRepository(
            backupDao = database.localDataBackupDao(),
            appVersionName = "test",
            appVersionCode = 1L,
            catalogCommit = { null }
        ).restoreFrom(ByteArrayInputStream(document))
        assertThat(database.localDataBackupDao().selectProfiles().single().id)
            .isEqualTo(UserProfile.DEFAULT_PROFILE_ID)
        viewModel.refreshRestoreEligibility()
        awaitState { it.restoreAllowed == false }
    }

    private fun documentUri(name: String): Uri = Uri.fromFile(File(documentDirectory, name))

    /**
     * Waits for the state the assertion needs instead of guessing at a fixed delay.
     *
     * The view model's work runs on the main dispatcher while the test polls from the
     * instrumentation thread, so a condition, not a sleep, is what makes this reliable.
     */
    private suspend fun awaitState(
        condition: (LocalDataUiState) -> Boolean
    ): LocalDataUiState = withTimeout(SETTLE_TIMEOUT_MILLIS) {
        while (true) {
            val current = viewModel.uiState.value
            if (!current.isBusy && condition(current)) return@withTimeout current
            delay(POLL_INTERVAL_MILLIS)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private companion object {
        const val SETTLE_TIMEOUT_MILLIS = 20_000L
        const val POLL_INTERVAL_MILLIS = 25L
    }
}
