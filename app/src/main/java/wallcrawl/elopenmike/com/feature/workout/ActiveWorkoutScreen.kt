package wallcrawl.elopenmike.com.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetValuesDraft
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.ui.components.ExerciseIllustration
import wallcrawl.elopenmike.com.core.ui.components.GymFloorSetRow
import wallcrawl.elopenmike.com.core.ui.components.restCountdownLabel
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlOutlinedButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveWorkoutScreen(
    viewModel: ActiveWorkoutViewModel,
    visualProvider: ExerciseVisualProvider,
    onNavigateBack: () -> Unit,
    onWorkoutFinished: (summarySessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WebBackgroundPattern()

        when (val state = uiState) {
            is ActiveWorkoutUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrimsonRedPrimary)
                }
            }

            is ActiveWorkoutUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WallCrawlCard(borderColor = CrimsonRedPrimary) {
                        Text("Workout Error", color = CrimsonRedLight, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        WallCrawlSecondaryButton(text = "Go Back", onClick = onNavigateBack)
                    }
                }
            }

            is ActiveWorkoutUiState.Completed -> {
                WorkoutSummaryScreen(
                    summary = state.summary,
                    onDone = { onWorkoutFinished(state.summary.sessionId) }
                )
            }

            is ActiveWorkoutUiState.Active -> {
                // The countdown is derived from the rest deadline, so the UI only has to
                // ask the ViewModel to re-evaluate it; nothing here counts time itself.
                LaunchedEffect(state.restTimer.state) {
                    while (state.restTimer.state is RestTimerState.Running) {
                        delay(REST_TICK_MILLIS)
                        viewModel.onRestTimerTick()
                    }
                }

                ActiveWorkoutContent(
                    state = state,
                    visualProvider = visualProvider,
                    onPreviousExercise = { viewModel.previousExercise() },
                    onNextExercise = { viewModel.nextExercise() },
                    onValuesChanged = { setId, values ->
                        viewModel.updateSetValues(setId, values)
                    },
                    onCompletionChanged = { setId, values, completed ->
                        viewModel.setCompletion(setId, values, completed)
                    },
                    onSkipSet = { setId, reason -> viewModel.skipSet(setId, reason) },
                    onRecordEffort = { setId, rpe, rir -> viewModel.recordEffort(setId, rpe, rir) },
                    onRecordFeltManageable = { setId, manageable ->
                        viewModel.recordFeltManageable(setId, manageable)
                    },
                    onRequestFinish = { viewModel.requestFinish() },
                    onDismissSetUpdateError = { viewModel.dismissSetUpdateError() },
                    onAddRest = { viewModel.addRestTime() },
                    onSkipRest = { viewModel.skipRest() },
                    onCancelRest = { viewModel.cancelRest() },
                    onClose = { viewModel.requestCancel() }
                )

                state.pendingFinish?.let { pending ->
                    FinishConfirmationDialog(
                        openSetCount = pending.openSetCount,
                        onConfirm = { viewModel.confirmFinish() },
                        onDismiss = { viewModel.dismissFinishConfirmation() }
                    )
                }

                if (state.isConfirmingDiscard) {
                    DiscardConfirmationDialog(
                        onConfirm = { viewModel.confirmCancel(onNavigateBack) },
                        onDismiss = { viewModel.dismissCancelConfirmation() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    state: ActiveWorkoutUiState.Active,
    visualProvider: ExerciseVisualProvider,
    onPreviousExercise: () -> Unit,
    onNextExercise: () -> Unit,
    onValuesChanged: (setId: String, values: SetValuesDraft) -> Unit,
    onCompletionChanged: (setId: String, values: SetValuesDraft, completed: Boolean) -> Unit,
    onSkipSet: (setId: String, reason: SetStopReason) -> Unit,
    onRecordEffort: (setId: String, rpe: Float?, rir: Int?) -> Unit,
    onRecordFeltManageable: (setId: String, feltManageable: Boolean) -> Unit,
    onRequestFinish: () -> Unit,
    onDismissSetUpdateError: () -> Unit,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
    onCancelRest: () -> Unit,
    onClose: () -> Unit
) {
    val currentExercise = state.currentExercise

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Workout",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.session.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Exercise ${state.currentExerciseIndex + 1} of ${state.totalExercises}",
                    fontSize = 12.sp,
                    color = CrimsonRedLight,
                    fontWeight = FontWeight.SemiBold
                )
            }

            WallCrawlOutlinedButton(
                text = "Finish",
                onClick = onRequestFinish,
                modifier = Modifier
                    .widthIn(min = 76.dp)
                    .height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.setUpdateError != null) {
            SetUpdateErrorBanner(
                message = state.setUpdateError,
                onDismiss = onDismissSetUpdateError
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (state.restTimer.isVisible) {
            RestTimerBar(
                restTimer = state.restTimer,
                onAddRest = onAddRest,
                onSkipRest = onSkipRest,
                onCancelRest = onCancelRest
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (currentExercise != null) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ExerciseIllustration(
                        exercise = state.currentCatalogExercise,
                        visualProvider = visualProvider
                    )
                }

                // 2. Exercise Title & Details Card
                item {
                    ExerciseHeaderCard(
                        workoutExercise = currentExercise,
                        exerciseName = state.currentCatalogExercise?.name,
                        preferredUnit = state.weightUnit.symbol
                    )
                }

                // 3. Previous Performance Reference Card
                if (state.previousSets.isNotEmpty()) {
                    item {
                        PreviousPerformanceCard(
                            sets = state.previousSets,
                            completedAtTimestamp = state.previousSessionTimestamp,
                            weightUnit = state.previousWeightUnit.symbol
                        )
                    }
                }

                // 4. Editable Sets Rows
                items(currentExercise.sets.size) { setIndex ->
                    val set = currentExercise.sets[setIndex]
                    GymFloorSetRow(
                        set = set,
                        weightUnit = state.weightUnit.symbol,
                        previousSet = state.previousSets.getOrNull(setIndex),
                        onValuesChanged = { values -> onValuesChanged(set.id, values) },
                        onCompletionChanged = { values, completed ->
                            onCompletionChanged(set.id, values, completed)
                        },
                        onSkipSet = { reason -> onSkipSet(set.id, reason) },
                        onRecordEffort = { rpe, rir -> onRecordEffort(set.id, rpe, rir) },
                        onRecordFeltManageable = { manageable ->
                            onRecordFeltManageable(set.id, manageable)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Bottom Navigation Buttons (Previous / Next / Finish)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.isFirstExercise) {
                WallCrawlSecondaryButton(
                    text = "Previous",
                    onClick = onPreviousExercise,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!state.isLastExercise) {
                WallCrawlPrimaryButton(
                    text = "Next Exercise",
                    onClick = onNextExercise,
                    modifier = Modifier.weight(if (state.isFirstExercise) 2f else 1f)
                )
            } else {
                WallCrawlPrimaryButton(
                    text = "Finish Workout",
                    onClick = onRequestFinish,
                    modifier = Modifier.weight(if (state.isFirstExercise) 2f else 1f)
                )
            }
        }
    }
}

/**
 * The visible rest countdown, with the three explicit controls the state machine exposes.
 */
@Composable
private fun RestTimerBar(
    restTimer: RestTimerUiState,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
    onCancelRest: () -> Unit
) {
    val isRunning = restTimer.isRunning
    val countdown = restCountdownLabel(restTimer.remainingSeconds)
    WallCrawlCard(cornerRadius = 12.dp, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRunning) "Rest $countdown" else "Rest finished",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = if (isRunning) {
                            "Rest remaining $countdown"
                        } else {
                            "Rest finished"
                        }
                    }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onAddRest,
                label = { Text("+30s", fontWeight = FontWeight.SemiBold) }
            )
            if (isRunning) {
                AssistChip(
                    onClick = onSkipRest,
                    label = { Text("Skip rest", fontWeight = FontWeight.SemiBold) }
                )
            }
            AssistChip(
                onClick = onCancelRest,
                label = { Text("Dismiss", fontWeight = FontWeight.SemiBold) }
            )
        }
    }
}

/**
 * Finishing with work still open is confirmed first, and the confirmation says exactly
 * how much is unlogged so nothing incomplete is silently recorded as a finished session.
 */
@Composable
private fun FinishConfirmationDialog(
    openSetCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val setWord = if (openSetCount == 1) "set" else "sets"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Finish with $openSetCount unlogged $setWord?",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                "$openSetCount $setWord will stay unlogged. Only completed sets count " +
                    "toward your history.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Finish anyway", color = CrimsonRedPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep going", color = MaterialTheme.colorScheme.onSurface)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun DiscardConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Discard this workout?", color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            Text(
                "Everything logged in this session will be deleted.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Discard", color = CrimsonRedPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep workout", color = MaterialTheme.colorScheme.onSurface)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ExerciseHeaderCard(
    workoutExercise: WorkoutExercise,
    exerciseName: String?,
    preferredUnit: String
) {
    val displayName = exerciseName ?: workoutExercise.exerciseId
        .split("-")
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    WallCrawlCard(
        cornerRadius = 14.dp,
        contentPadding = 14.dp
    ) {
        Text(
            text = displayName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatBadge(
                label = workoutExercise.prescription.displayTarget(),
                textColor = MaterialTheme.colorScheme.onSurface
            )

            if (workoutExercise.targetWeight != null) {
                StatBadge(
                    label = "Suggested: ${workoutExercise.targetWeight} $preferredUnit",
                    textColor = MaterialTheme.colorScheme.secondary
                )
            }
            workoutExercise.prescription.targetAssistanceWeight?.let { assistance ->
                StatBadge(
                    label = "Assistance: $assistance $preferredUnit",
                    textColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

private fun wallcrawl.elopenmike.com.core.model.ExercisePrescription.inputLabel(unit: String): String =
    when (exerciseType) {
        ExerciseType.WEIGHT_REPS -> "LOAD ($unit) · REPS"
        ExerciseType.BODYWEIGHT_REPS -> "REPS"
        ExerciseType.ASSISTED_BODYWEIGHT -> "ASSIST ($unit) · REPS"
        ExerciseType.DURATION -> "DURATION"
        ExerciseType.DISTANCE_DURATION -> "DISTANCE · DURATION"
    }

private fun wallcrawl.elopenmike.com.core.model.ExercisePrescription.displayTarget(): String =
    when (exerciseType) {
        ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT_REPS,
        ExerciseType.ASSISTED_BODYWEIGHT -> "$targetSets × $repRange"
        ExerciseType.DURATION -> "$targetSets × ${targetDurationSeconds}s"
        ExerciseType.DISTANCE_DURATION -> buildList {
            add("$targetSets set" + if (targetSets == 1) "" else "s")
            targetDistanceMeters?.let { add("${it.toInt()} m") }
            targetDurationSeconds?.let { add("${it}s") }
        }.joinToString(" · ")
    }

/**
 * A rejected set update (e.g. an explicit but invalid completion attempt) is
 * recoverable, not terminal: it surfaces here, dismissible, instead of replacing the
 * active workout with a full-screen error.
 */
@Composable
private fun SetUpdateErrorBanner(message: String, onDismiss: () -> Unit) {
    WallCrawlCard(
        cornerRadius = 12.dp,
        contentPadding = 12.dp,
        borderColor = CrimsonRedPrimary
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                color = CrimsonRedLight,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviousPerformanceCard(
    sets: List<WorkoutSet>,
    completedAtTimestamp: Long?,
    weightUnit: String
) {
    WallCrawlCard(
        cornerRadius = 12.dp,
        contentPadding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "PREVIOUS SESSION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        completedAtTimestamp?.let { timestamp ->
            Text(
                text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        sets.forEach { set ->
            val result = when (set.exerciseType) {
                ExerciseType.WEIGHT_REPS -> {
                    val reps = set.completedReps ?: return@forEach
                    "${set.completedWeight?.let { "$it $weightUnit" } ?: "No load"} × $reps"
                }
                ExerciseType.BODYWEIGHT_REPS ->
                    "Bodyweight × ${set.completedReps ?: return@forEach}"
                ExerciseType.ASSISTED_BODYWEIGHT -> {
                    val assistance = set.completedAssistanceWeight
                        ?.let { "$it $weightUnit assistance" }
                        ?: "Assistance not logged"
                    "$assistance × ${set.completedReps ?: return@forEach}"
                }
                ExerciseType.DURATION -> "${set.completedDurationSeconds ?: return@forEach} seconds"
                ExerciseType.DISTANCE_DURATION -> listOfNotNull(
                    set.completedDistanceMeters?.let { "$it m" },
                    set.completedDurationSeconds?.let { "$it seconds" }
                ).joinToString(" · ").ifBlank { return@forEach }
            }
            Text(
                text = "• $result",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** How often the visible countdown re-derives itself from the rest deadline. */
private const val REST_TICK_MILLIS = 250L
