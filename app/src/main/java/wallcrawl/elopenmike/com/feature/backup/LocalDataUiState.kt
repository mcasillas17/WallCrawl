package wallcrawl.elopenmike.com.feature.backup

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.backup.ArchiveRejection

/** The long-running local-data operation currently in flight, if any. */
enum class LocalDataOperation {
    IDLE,
    EXPORTING,
    RESTORING,
    DELETING
}

/**
 * A result to show the user.
 *
 * Every variant is resource-backed, including the reasons an archive was refused: the codec
 * reports a typed [ArchiveRejection] and [rejectionMessage] turns it into copy, the same way
 * typed planning failures are turned into copy elsewhere in this app. No parser text ever
 * reaches the screen, so a rejection cannot carry an archived value out of the document.
 */
sealed interface LocalDataMessage {
    val isError: Boolean

    data class ExportSucceeded(val sessionCount: Int, val templateCount: Int) : LocalDataMessage {
        override val isError: Boolean get() = false
    }

    data class RestoreSucceeded(val sessionCount: Int, val templateCount: Int) : LocalDataMessage {
        override val isError: Boolean get() = false
    }

    data class Failure(@StringRes val messageRes: Int) : LocalDataMessage {
        override val isError: Boolean get() = true
    }

    companion object {
        val exportFailed = Failure(R.string.local_data_export_failed)
        val exportTooLarge = Failure(R.string.local_data_export_too_large)
        val exportRefused = Failure(R.string.local_data_export_refused)
        val documentUnavailable = Failure(R.string.local_data_document_unavailable)
        val restoreNeedsEmptyApp = Failure(R.string.local_data_restore_needs_empty_app)
        val restoreFailed = Failure(R.string.local_data_restore_failed)
        val deleteFailed = Failure(R.string.local_data_delete_failed)
    }
}

/** Copy for each way an archive can be refused. */
fun rejectionMessage(rejection: ArchiveRejection): LocalDataMessage.Failure =
    LocalDataMessage.Failure(
        when (rejection) {
            ArchiveRejection.UNSUPPORTED_VERSION -> R.string.local_data_rejected_version
            ArchiveRejection.CHECKSUM_MISMATCH -> R.string.local_data_rejected_checksum
            ArchiveRejection.TOO_LARGE -> R.string.local_data_rejected_too_large
            ArchiveRejection.MALFORMED -> R.string.local_data_rejected_malformed
            ArchiveRejection.INVALID_VALUE -> R.string.local_data_rejected_invalid_value
            ArchiveRejection.INCONSISTENT -> R.string.local_data_rejected_inconsistent
        }
    )

/**
 * State of the export, restore, and delete-all controls.
 *
 * [restoreAllowed] is null until the destination has been inspected, so the control can say
 * "checking" rather than promise or refuse a restore it has not verified. If that inspection
 * fails, [restoreEligibilityUnknown] says so instead of leaving "checking" on screen forever.
 */
data class LocalDataUiState(
    val operation: LocalDataOperation = LocalDataOperation.IDLE,
    val restoreAllowed: Boolean? = null,
    /**
     * True when the eligibility read itself failed.
     *
     * The control is then offered rather than left disabled: the restore transaction
     * rechecks eligibility from inside anyway, so attempting one is safe and is the only
     * way out of a transient read failure.
     */
    val restoreEligibilityUnknown: Boolean = false,
    val message: LocalDataMessage? = null,
    val isConfirmingDelete: Boolean = false,
    /** Set once after a restore, so the host screen can move to the right destination. */
    val restoredOnboardingCompleted: Boolean? = null,
    /** Set once after a successful deletion, so the host screen can return to onboarding. */
    val deletionCompleted: Boolean = false
) {
    val isBusy: Boolean get() = operation != LocalDataOperation.IDLE
}
