package wallcrawl.elopenmike.com.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFormat
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlOutlinedButton
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary

const val LOCAL_DATA_EXPORT_TEST_TAG = "local_data_export"
const val LOCAL_DATA_RESTORE_TEST_TAG = "local_data_restore"
const val LOCAL_DATA_DELETE_TEST_TAG = "local_data_delete"
const val LOCAL_DATA_MESSAGE_TEST_TAG = "local_data_message"
const val LOCAL_DATA_DELETE_CONFIRM_TEST_TAG = "local_data_delete_confirm"

/**
 * The full export / restore / delete-all card shown on the Training Profile screen.
 *
 * Completion is reported by [LocalDataOutcomeEffect], which the host places outside the
 * scrolling list, so scrolling this card off-screen cannot lose the outcome.
 */
@Composable
fun LocalDataSection(
    viewModel: LocalDataViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    WallCrawlCard(modifier = modifier.fillMaxWidth(), cornerRadius = 16.dp, contentPadding = 16.dp) {
        SectionHeading(stringResource(R.string.local_data_title))
        Spacer(modifier = Modifier.height(4.dp))
        BodyText(stringResource(R.string.local_data_description))
        Spacer(modifier = Modifier.height(12.dp))

        ExportControl(viewModel = viewModel, state = state)

        Spacer(modifier = Modifier.height(16.dp))
        RestoreControl(viewModel = viewModel, state = state)

        Spacer(modifier = Modifier.height(16.dp))
        BodyText(stringResource(R.string.local_data_delete_hint))
        Spacer(modifier = Modifier.height(8.dp))
        WallCrawlOutlinedButton(
            text = stringResource(
                if (state.operation == LocalDataOperation.DELETING) {
                    R.string.local_data_delete_progress
                } else {
                    R.string.local_data_delete
                }
            ),
            onClick = viewModel::requestDeleteConfirmation,
            enabled = !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LOCAL_DATA_DELETE_TEST_TAG)
        )

        LocalDataMessageText(state)
    }

    if (state.isConfirmingDelete) {
        DeleteEverythingDialog(
            onConfirm = viewModel::confirmDeleteAllLocalData,
            onDismiss = viewModel::cancelDeleteConfirmation
        )
    }
}

/**
 * Reports a finished restore or deletion, from somewhere that stays composed.
 *
 * Deliberately not inside the cards below. [RestoreFromArchiveCard] exists only on
 * onboarding's first step, and [LocalDataSection] is one item in a scrolling list that
 * Compose disposes once it leaves the viewport — either way an effect living with the card
 * can be gone when the operation it was watching finishes. Host screens place this above
 * the step switch and above the list, where it outlives both.
 */
@Composable
fun LocalDataOutcomeEffect(
    viewModel: LocalDataViewModel,
    onRestored: (onboardingCompleted: Boolean) -> Unit = {},
    onDeleted: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.restoredOnboardingCompleted, state.deletionCompleted) {
        val restored = state.restoredOnboardingCompleted
        val deleted = state.deletionCompleted
        if (restored == null && !deleted) return@LaunchedEffect

        viewModel.consumeNavigation()
        if (restored != null) onRestored(restored) else onDeleted()
    }
}

/**
 * Restore on its own, for the first onboarding step.
 *
 * A user who has just reinstalled, or who has just deleted everything, can restore before
 * being asked to build a profile they would immediately discard. Completion is reported by
 * [LocalDataOutcomeEffect], not by this card, so leaving the step cannot lose the result.
 */
@Composable
fun RestoreFromArchiveCard(
    viewModel: LocalDataViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    WallCrawlCard(modifier = modifier.fillMaxWidth(), cornerRadius = 16.dp, contentPadding = 16.dp) {
        SectionHeading(stringResource(R.string.onboarding_restore_title))
        Spacer(modifier = Modifier.height(4.dp))
        BodyText(stringResource(R.string.onboarding_restore_description))
        Spacer(modifier = Modifier.height(12.dp))
        RestoreControl(viewModel = viewModel, state = state)
        LocalDataMessageText(state)
    }
}

@Composable
private fun ExportControl(viewModel: LocalDataViewModel, state: LocalDataUiState) {
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(LocalDataArchiveFormat.MIME_TYPE)
    ) { destination ->
        // A null result means the user backed out of the picker. Nothing was created and
        // nothing needs undoing, so the screen is left exactly as it was.
        if (destination != null) viewModel.exportTo(destination)
    }

    BodyText(stringResource(R.string.local_data_export_hint))
    Spacer(modifier = Modifier.height(8.dp))
    WallCrawlOutlinedButton(
        text = stringResource(
            if (state.operation == LocalDataOperation.EXPORTING) {
                R.string.local_data_export_progress
            } else {
                R.string.local_data_export
            }
        ),
        onClick = { createDocument.launch(viewModel.suggestedFileName()) },
        enabled = !state.isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LOCAL_DATA_EXPORT_TEST_TAG)
    )
    BusyIndicator(visible = state.operation == LocalDataOperation.EXPORTING)
}

@Composable
private fun RestoreControl(viewModel: LocalDataViewModel, state: LocalDataUiState) {
    // Providers disagree about the type they report for a saved .json document, and every
    // archive is fully validated after it is opened, so the picker does not filter by type.
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source ->
        if (source != null) viewModel.restoreFrom(source)
    }

    BodyText(
        stringResource(
            when {
                state.restoreAllowed == true -> R.string.local_data_restore_hint
                state.restoreAllowed == false -> R.string.local_data_restore_blocked
                state.restoreEligibilityUnknown -> R.string.local_data_restore_check_failed
                else -> R.string.local_data_restore_checking
            }
        )
    )
    Spacer(modifier = Modifier.height(8.dp))
    WallCrawlOutlinedButton(
        text = stringResource(
            if (state.operation == LocalDataOperation.RESTORING) {
                R.string.local_data_restore_progress
            } else {
                R.string.local_data_restore
            }
        ),
        onClick = { openDocument.launch(arrayOf("*/*")) },
        // Offered when eligibility could not be read: the restore transaction rechecks it
        // from inside, so an attempt is safe and is how a transient failure recovers.
        enabled = !state.isBusy &&
            (state.restoreAllowed == true || state.restoreEligibilityUnknown),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LOCAL_DATA_RESTORE_TEST_TAG)
    )
    BusyIndicator(visible = state.operation == LocalDataOperation.RESTORING)
}

/**
 * The confirmation for the one irreversible action in this feature.
 *
 * Every text colour is explicit. The shared typography hard-codes a light colour into
 * `headlineSmall` and `labelLarge` — the styles an `AlertDialog` applies to its title and its
 * button labels — and an explicit colour inside a `TextStyle` wins over the content colour the
 * dialog provides. Without these the title and the safe way out would render near-invisibly on
 * the light theme's dialog surface while the destructive action stayed perfectly readable,
 * which is the opposite of what a destructive confirmation owes the user. The other dialogs in
 * this app set the same colours for the same reason.
 */
@Composable
private fun DeleteEverythingDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(LOCAL_DATA_DELETE_CONFIRM_TEST_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = stringResource(R.string.local_data_delete_confirm_title),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = stringResource(R.string.local_data_delete_confirm_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.local_data_delete_confirm_action),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.local_data_delete_confirm_cancel),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}

@Composable
private fun LocalDataMessageText(state: LocalDataUiState) {
    val message = state.message ?: return
    val text = when (message) {
        is LocalDataMessage.ExportSucceeded -> stringResource(
            R.string.local_data_export_success,
            workoutCount(message.sessionCount),
            templateCount(message.templateCount)
        )

        is LocalDataMessage.RestoreSucceeded -> stringResource(
            R.string.local_data_restore_success,
            workoutCount(message.sessionCount),
            templateCount(message.templateCount)
        )

        is LocalDataMessage.Failure -> stringResource(message.messageRes)
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = text,
        fontSize = 13.sp,
        color = if (message.isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LOCAL_DATA_MESSAGE_TEST_TAG)
            // Announced when it appears: the outcome of an export or a restore is the whole
            // point of the interaction, and it is not attached to the control that was used.
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
private fun BusyIndicator(visible: Boolean) {
    if (!visible) return
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = CrimsonRedPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.local_data_busy),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The counted halves of an export or restore result.
 *
 * Built as separate plurals and substituted into the sentence, rather than the sentence
 * being assembled from fragments: "1 workout and 3 routines" and "1 entrenamiento y
 * 3 rutinas" agree differently, and only the translation can decide how.
 */
@Composable
private fun workoutCount(count: Int): String = pluralStringResource(
    R.plurals.count_workouts,
    count,
    LocaleFormatting.formatCount(count, LocalConfiguration.current.locales[0])
)

@Composable
private fun templateCount(count: Int): String = pluralStringResource(
    R.plurals.count_templates,
    count,
    LocaleFormatting.formatCount(count, LocalConfiguration.current.locales[0])
)

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = CrimsonRedPrimary
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
