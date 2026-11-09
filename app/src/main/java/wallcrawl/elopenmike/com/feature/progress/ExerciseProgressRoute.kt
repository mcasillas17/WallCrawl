package wallcrawl.elopenmike.com.feature.progress

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlWordmark
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting

@Composable
internal fun ExerciseProgressRoute(
    viewModel: ProgressViewModel,
    onOpenProfile: () -> Unit,
    onOpenTrain: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenMuscles: () -> Unit,
    onOpenActivity: () -> Unit
) {
    RefreshProgressOnClockChange(viewModel)
    val history by viewModel.exerciseHistoryState.collectWhileResumed(ExerciseHistoryUiState.Loading)
    val chartState = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WallCrawlWordmark(fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Box {
                var expanded by remember { mutableStateOf(false) }
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.progress_more_options))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    // The weekly ledger is read on demand while the options are visible.
                    val weekly by viewModel.uiState.collectWhileResumed(ProgressUiState.Loading)
                    val overview = (weekly as? ProgressUiState.Success)?.overview
                    DropdownMenuItem(text = {
                        Column {
                            Text(stringResource(R.string.progress_training_activity))
                            if (overview != null) {
                                val locale = LocalConfiguration.current.locales[0]
                                Text(pluralStringResource(
                                    R.plurals.today_weekly_progress, overview.weeklyGoal,
                                    LocaleFormatting.formatCount(overview.workoutsThisWeek, locale),
                                    LocaleFormatting.formatCount(overview.weeklyGoal, locale)
                                ), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }, onClick = { expanded = false; onOpenActivity() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.history_title)) },
                        onClick = { expanded = false; onOpenHistory() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.guided_progress_trends)) },
                        onClick = { expanded = false; onOpenTrends() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.guided_progress_muscles)) },
                        onClick = { expanded = false; onOpenMuscles() })
                }
            }
            IconButton(onClick = onOpenProfile) {
                Icon(Icons.Outlined.AccountCircle, stringResource(R.string.guided_profile_settings))
            }
        }
        when (val state = history) {
            ExerciseHistoryUiState.Loading -> ProgressLoading()
            ExerciseHistoryUiState.NoProfile -> ProgressAction(R.string.progress_no_profile, R.string.guided_progress_train, onOpenTrain)
            ExerciseHistoryUiState.Error -> ProgressAction(R.string.progress_error, R.string.action_try_again, viewModel::refresh, isError = true)
            is ExerciseHistoryUiState.Loaded -> chartState.SaveableStateProvider("chart") {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ExerciseHistoryContent(state, viewModel::selectHistoryExercise, viewModel::selectHistoryPeriod,
                        viewModel::selectHistoryCustom, onOpenTrain)
                }
            }
        }
    }
}

@Composable
internal fun ProgressLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun ProgressAction(
    @StringRes message: Int,
    @StringRes action: Int,
    onClick: () -> Unit,
    isError: Boolean = false
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(message),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onClick) { Text(stringResource(action)) }
    }
}

@Composable
internal fun <T> StateFlow<T>.collectWhileResumed(initial: T): State<T> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(initial, this, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { collect { value = it } }
    }
}

@Composable
internal fun RefreshProgressOnClockChange(viewModel: ProgressViewModel) {
    val context = LocalContext.current
    DisposableEffect(viewModel, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = viewModel.refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
}
