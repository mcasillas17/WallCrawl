package wallcrawl.elopenmike.com.feature.workout

import androidx.annotation.StringRes
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
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
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

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
                        Text(
                            stringResource(R.string.workout_error_title),
                            color = CrimsonRedLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(state.messageRes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        WallCrawlSecondaryButton(
                            text = stringResource(R.string.workout_action_go_back),
                            onClick = onNavigateBack
                        )
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
    val locale = LocalConfiguration.current.locales[0]

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
                    contentDescription = stringResource(R.string.workout_close_content_description),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // The session name is free text written when the workout started, in the
                // language that was on screen then. It is shown as recorded.
                Text(
                    text = state.session.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.workout_exercise_position,
                        LocaleFormatting.formatCount(state.currentExerciseIndex + 1, locale),
                        LocaleFormatting.formatCount(state.totalExercises, locale)
                    ),
                    fontSize = 12.sp,
                    color = CrimsonRedLight,
                    fontWeight = FontWeight.SemiBold
                )
            }

            WallCrawlOutlinedButton(
                text = stringResource(R.string.workout_action_finish),
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
                messageRes = state.setUpdateError,
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
                        catalogExercise = state.currentCatalogExercise,
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
                    text = stringResource(R.string.workout_action_previous),
                    onClick = onPreviousExercise,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!state.isLastExercise) {
                WallCrawlPrimaryButton(
                    text = stringResource(R.string.workout_action_next),
                    onClick = onNextExercise,
                    modifier = Modifier.weight(if (state.isFirstExercise) 2f else 1f)
                )
            } else {
                WallCrawlPrimaryButton(
                    text = stringResource(R.string.workout_action_finish_workout),
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
            val restLabel = if (isRunning) {
                stringResource(R.string.rest_running, countdown)
            } else {
                stringResource(R.string.rest_finished)
            }
            val restAnnouncement = if (isRunning) {
                stringResource(R.string.rest_remaining_accessibility, countdown)
            } else {
                stringResource(R.string.rest_finished)
            }
            Text(
                text = restLabel,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = restAnnouncement }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onAddRest,
                label = {
                    Text(stringResource(R.string.rest_add_thirty), fontWeight = FontWeight.SemiBold)
                }
            )
            if (isRunning) {
                AssistChip(
                    onClick = onSkipRest,
                    label = {
                        Text(stringResource(R.string.rest_skip), fontWeight = FontWeight.SemiBold)
                    }
                )
            }
            AssistChip(
                onClick = onCancelRest,
                label = {
                    Text(stringResource(R.string.rest_dismiss), fontWeight = FontWeight.SemiBold)
                }
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
    val locale = LocalConfiguration.current.locales[0]
    val openSets = LocaleFormatting.formatCount(openSetCount, locale)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                pluralStringResource(R.plurals.finish_confirm_title, openSetCount, openSets),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                pluralStringResource(R.plurals.finish_confirm_body, openSetCount, openSets),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.finish_confirm_action),
                    color = CrimsonRedPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.finish_confirm_keep_going),
                    color = MaterialTheme.colorScheme.onSurface
                )
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
            Text(
                stringResource(R.string.discard_confirm_title),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                stringResource(R.string.discard_confirm_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.discard_confirm_action),
                    color = CrimsonRedPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.discard_confirm_keep),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ExerciseHeaderCard(
    workoutExercise: WorkoutExercise,
    catalogExercise: wallcrawl.elopenmike.com.core.model.Exercise?,
    preferredUnit: String
) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    val displayName = catalogExercise
        ?.let(vocabulary::exerciseName)
        ?: vocabulary.exerciseName(workoutExercise.exerciseId)

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
                label = prescriptionTarget(workoutExercise.prescription),
                textColor = MaterialTheme.colorScheme.onSurface
            )

            workoutExercise.targetWeight?.let { target ->
                StatBadge(
                    label = stringResource(
                        R.string.workout_suggested_load,
                        LocaleFormatting.formatMeasurement(target, locale),
                        preferredUnit
                    ),
                    textColor = MaterialTheme.colorScheme.secondary
                )
            }
            workoutExercise.prescription.targetAssistanceWeight?.let { assistance ->
                StatBadge(
                    label = stringResource(
                        R.string.workout_assistance,
                        LocaleFormatting.formatMeasurement(assistance, locale),
                        preferredUnit
                    ),
                    textColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/** The compact prescribed target for this exercise, written for the reader. */
@Composable
private fun prescriptionTarget(
    prescription: wallcrawl.elopenmike.com.core.model.ExercisePrescription
): String {
    val locale = LocalConfiguration.current.locales[0]
    val sets = LocaleFormatting.formatCount(prescription.targetSets, locale)
    return when (prescription.exerciseType) {
        ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT_REPS,
        ExerciseType.ASSISTED_BODYWEIGHT -> {
            val range = prescription.repRange
            val reps = when {
                range == null -> ""
                range.min == range.max -> LocaleFormatting.formatCount(range.min, locale)
                else -> stringResource(
                    R.string.prescription_rep_range,
                    LocaleFormatting.formatCount(range.min, locale),
                    LocaleFormatting.formatCount(range.max, locale)
                )
            }
            stringResource(R.string.prescription_sets_by_reps, sets, reps)
        }

        ExerciseType.DURATION -> stringResource(
            R.string.prescription_sets_by_reps,
            sets,
            stringResource(
                R.string.prescription_seconds,
                LocaleFormatting.formatCount(prescription.targetDurationSeconds ?: 0, locale)
            )
        )

        ExerciseType.DISTANCE_DURATION -> listOfNotNull(
            pluralStringResource(R.plurals.count_sets, prescription.targetSets, sets),
            prescription.targetDistanceMeters?.let {
                stringResource(
                    R.string.prescription_meters,
                    LocaleFormatting.formatCount(it.toInt(), locale)
                )
            },
            prescription.targetDurationSeconds?.let {
                stringResource(
                    R.string.prescription_seconds,
                    LocaleFormatting.formatCount(it, locale)
                )
            }
        ).joinToString(stringResource(R.string.detail_separator))
    }
}

/**
 * A rejected set update (e.g. an explicit but invalid completion attempt) is
 * recoverable, not terminal: it surfaces here, dismissible, instead of replacing the
 * active workout with a full-screen error.
 */
@Composable
private fun SetUpdateErrorBanner(@StringRes messageRes: Int, onDismiss: () -> Unit) {
    WallCrawlCard(
        cornerRadius = 12.dp,
        contentPadding = 12.dp,
        borderColor = CrimsonRedPrimary
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(messageRes),
                color = CrimsonRedLight,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.workout_dismiss_error),
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
    val locale = LocalConfiguration.current.locales[0]
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
                text = stringResource(R.string.workout_previous_session),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        completedAtTimestamp?.let { timestamp ->
            Text(
                text = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
                    .format(Date(timestamp)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val detailSeparator = stringResource(R.string.detail_separator)
        sets.forEach { set ->
            val result = when (set.exerciseType) {
                ExerciseType.WEIGHT_REPS -> {
                    val reps = set.completedReps ?: return@forEach
                    stringResource(
                        R.string.previous_weight_reps,
                        set.completedWeight?.let {
                            stringResource(
                                R.string.previous_load,
                                LocaleFormatting.formatMeasurement(it, locale),
                                weightUnit
                            )
                        } ?: stringResource(R.string.previous_no_load),
                        LocaleFormatting.formatCount(reps, locale)
                    )
                }
                ExerciseType.BODYWEIGHT_REPS -> stringResource(
                    R.string.previous_bodyweight_reps,
                    LocaleFormatting.formatCount(set.completedReps ?: return@forEach, locale)
                )
                ExerciseType.ASSISTED_BODYWEIGHT -> {
                    val assistance = set.completedAssistanceWeight?.let {
                        stringResource(
                            R.string.previous_assistance,
                            LocaleFormatting.formatMeasurement(it, locale),
                            weightUnit
                        )
                    } ?: stringResource(R.string.previous_assistance_missing)
                    stringResource(
                        R.string.previous_weight_reps,
                        assistance,
                        LocaleFormatting.formatCount(set.completedReps ?: return@forEach, locale)
                    )
                }
                ExerciseType.DURATION -> stringResource(
                    R.string.prescription_seconds,
                    LocaleFormatting.formatCount(
                        set.completedDurationSeconds ?: return@forEach,
                        locale
                    )
                )
                ExerciseType.DISTANCE_DURATION -> listOfNotNull(
                    set.completedDistanceMeters?.let {
                        stringResource(
                            R.string.prescription_meters,
                            LocaleFormatting.formatMeasurement(it, locale)
                        )
                    },
                    set.completedDurationSeconds?.let {
                        stringResource(
                            R.string.prescription_seconds,
                            LocaleFormatting.formatCount(it, locale)
                        )
                    }
                ).joinToString(detailSeparator).ifBlank { return@forEach }
            }
            Text(
                text = stringResource(R.string.previous_entry, result),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** How often the visible countdown re-derives itself from the rest deadline. */
private const val REST_TICK_MILLIS = 250L
