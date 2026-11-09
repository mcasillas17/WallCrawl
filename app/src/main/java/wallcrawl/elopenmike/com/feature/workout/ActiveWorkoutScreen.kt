package wallcrawl.elopenmike.com.feature.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import wallcrawl.elopenmike.com.core.ai.FirstWeightGuidance
import wallcrawl.elopenmike.com.core.model.PrescriptionPurpose
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.convertWeightForEditing
import wallcrawl.elopenmike.com.core.ui.components.stopReasonLabel
import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.ui.components.ExerciseIllustration
import wallcrawl.elopenmike.com.core.ui.components.GymFloorSetRow
import wallcrawl.elopenmike.com.core.ui.components.restCountdownLabel
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun ActiveWorkoutScreen(
    viewModel: ActiveWorkoutViewModel,
    visualProvider: ExerciseVisualProvider,
    onNavigateBack: () -> Unit,
    onWorkoutFinished: (summarySessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: ((String) -> Unit)? = null,
    onLeaveForLater: () -> Unit = onNavigateBack
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by produceState<ActiveWorkoutUiState>(ActiveWorkoutUiState.Loading, viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.uiState.collect { value = it }
        }
    }

    val sessionState = rememberSaveableStateHolder()
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground) {

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
                            color = MaterialTheme.colorScheme.error,
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
                            text = stringResource(R.string.action_try_again),
                            onClick = viewModel::retry
                        )
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
                    advancedPartialProgram = state.advancedPartialProgram,
                    onDone = { onWorkoutFinished(state.summary.sessionId) },
                    onOpenDetails = onOpenHistory?.let { open -> { open(state.summary.sessionId) } }
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

                sessionState.SaveableStateProvider(state.session.id) {
                    var leaving by rememberSaveable { mutableStateOf(false) }
                    BackHandler { if (!state.isSaving) leaving = true }
                    ActiveWorkoutContent(state, visualProvider, viewModel, onLeave = { leaving = true })
                    if (leaving) {
                        AlertDialog(
                            onDismissRequest = { leaving = false },
                            title = { Text(stringResource(R.string.guided_leave_workout)) },
                            text = {
                                Column {
                                    TextButton(enabled = !state.isSaving, onClick = {
                                        leaving = false
                                        onLeaveForLater()
                                    }) { Text(stringResource(R.string.guided_resume_later), color = MaterialTheme.colorScheme.onSurface) }
                                    TextButton(enabled = !state.isSaving, onClick = {
                                        leaving = false
                                        viewModel.requestCancel()
                                    }) { Text(stringResource(R.string.guided_discard_workout), color = MaterialTheme.colorScheme.error) }
                                }
                            },
                            confirmButton = { TextButton(onClick = { leaving = false }) {
                                Text(stringResource(R.string.guided_keep_training), color = MaterialTheme.colorScheme.onSurface)
                            } }
                        )
                    }
                }

                state.pendingFinish?.let { pending ->
                    FinishConfirmationDialog(
                        openSetCount = pending.openSetCount,
                        isProgram = state.programAssociation?.supportedSnapshot != null && !state.programFinishUnsupported,
                        onConfirm = { viewModel.confirmFinish() },
                        onDismiss = { viewModel.dismissFinishConfirmation() }
                    )
                }

                state.painExclusionOffer?.let { offer ->
                    val name = LocalExerciseVocabulary.current.exerciseName(offer.exerciseId)
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissPainExclusionOffer(offer.exerciseId) },
                        title = { Text(stringResource(R.string.pain_offer_title, name)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.pain_offer_body))
                                if (offer.saveFailed) Text(
                                    stringResource(R.string.profile_exclusion_save_error),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = !offer.saving, onClick = { viewModel.acceptPainExclusion(offer.exerciseId) }) {
                                Text(stringResource(R.string.pain_offer_accept))
                            }
                        },
                        dismissButton = {
                            TextButton(enabled = !offer.saving, onClick = { viewModel.dismissPainExclusionOffer(offer.exerciseId) }) {
                                Text(stringResource(R.string.pain_offer_decline))
                            }
                        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveWorkoutContent(
    state: ActiveWorkoutUiState.Active,
    visualProvider: ExerciseVisualProvider,
    viewModel: ActiveWorkoutViewModel,
    onLeave: () -> Unit
) {
    val exercise = state.currentExercise ?: return
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    val name = vocabulary.exerciseName(exercise.exerciseId, state.currentCatalogExercise?.name)
    val position = stringResource(R.string.workout_exercise_position,
        LocaleFormatting.formatCount(state.currentExerciseIndex + 1, locale),
        LocaleFormatting.formatCount(state.totalExercises, locale))
    val exerciseState = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLeave, enabled = !state.isSaving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.guided_leave_workout))
            }
            Text(state.session.name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = viewModel::requestFinish, enabled = !state.isSaving) {
                Text(stringResource(R.string.workout_action_finish), color = MaterialTheme.colorScheme.onSurface)
            }
        }

        exerciseState.SaveableStateProvider(exercise.id) {
            val sets = exercise.sets
            val nextOpen = sets.firstOrNull { !it.isResolved }
            var selectedId by rememberSaveable { mutableStateOf(nextOpen?.id) }
            var awaitingResolution by rememberSaveable { mutableStateOf<String?>(null) }
            val selected = sets.firstOrNull { it.id == selectedId }
            val draftState = rememberSaveableStateHolder()
            var expanded by rememberSaveable { mutableStateOf(false) }
            val thumbnailFocus = remember { FocusRequester() }
            var returnFocus by remember { mutableStateOf(false) }
            val scroll = rememberScrollState()

            LaunchedEffect(awaitingResolution, selected?.isResolved, state.isSaving, state.setUpdateError, state.painExclusionOffer) {
                if (awaitingResolution != null && !state.isSaving && state.painExclusionOffer == null) {
                    if (state.setUpdateError != null) {
                        awaitingResolution = null
                    } else if (awaitingResolution == selectedId && selected?.isResolved == true) {
                        selectedId = nextOpen?.id
                        awaitingResolution = null
                    }
                }
            }
            LaunchedEffect(state.setUpdateError) {
                if (state.setUpdateError != null) scroll.scrollTo(0)
            }
            LaunchedEffect(expanded) {
                if (!expanded && returnFocus) {
                    thumbnailFocus.requestFocus()
                    returnFocus = false
                }
            }
            WorkoutPage(Modifier.fillMaxSize().pointerInput(exercise.id, state.isSaving) {
                if (state.isSaving) return@pointerInput
                var distance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { distance = 0f },
                    onHorizontalDrag = { _, delta -> distance += delta },
                    onDragEnd = {
                        val threshold = 48.dp.toPx()
                        if (distance < -threshold) viewModel.nextExercise()
                        if (distance > threshold) viewModel.previousExercise()
                    }
                )
            }.verticalScroll(scroll).padding(bottom = 8.dp), reservedControlsHeight = 344.dp, heading = {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(position, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = "$position, $name"
                        })
                    Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val effort = exercise.prescription.effortTarget?.let {
                        val min = LocaleFormatting.formatCount(it.minRir, locale)
                        val range = if (it.minRir == it.maxRir) min else stringResource(R.string.prescription_rep_range,
                            min, LocaleFormatting.formatCount(it.maxRir, locale))
                        stringResource(R.string.editor_summary_effort, range)
                    }
                    Text(listOfNotNull(prescriptionTarget(exercise.prescription), effort).joinToString("   ·   "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, demonstration = {
                val expandLabel = stringResource(R.string.guided_expand_demo, name)
                Box(Modifier.fillMaxSize().focusRequester(thumbnailFocus)
                    .clickable(role = Role.Button) { expanded = true }
                    .clearAndSetSemantics { contentDescription = expandLabel }) {
                    ExerciseIllustration(state.currentCatalogExercise, visualProvider, Modifier.matchParentSize(), showFrameCounter = false)
                    Icon(Icons.Default.OpenInFull, null, Modifier.align(Alignment.BottomEnd).padding(10.dp)
                        .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(6.dp).size(16.dp),
                        tint = Color.White)
                }
            }) {
                if (exercise.notes.isNotBlank() && exercise.notes != state.currentCatalogExercise?.programming?.coachingSummary) {
                    Text(exercise.notes, style = MaterialTheme.typography.bodyMedium)
                }
                wallcrawl.elopenmike.com.core.ui.components.ConditionExecution(
                    state.session.conditionContext, exercise.exerciseId, exercise.prescription.purpose)
                state.programAssociation?.let { association ->
                    Text(wallcrawl.elopenmike.com.feature.program.positionText(association.position),
                        style = MaterialTheme.typography.labelLarge)
                }
                if (state.programAssociation?.supportedSnapshot != null && !state.programFinishUnsupported) {
                    Text(stringResource(R.string.program_partial_finish), style = MaterialTheme.typography.bodySmall)
                }
                state.programReadError?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.action_try_again), color = MaterialTheme.colorScheme.onSurface) }
                }
                state.setUpdateError?.let { SetUpdateErrorBanner(it, viewModel::dismissSetUpdateError) }
                if (state.restTimer.isVisible) {
                    RestTimerBar(state.restTimer, viewModel::addRestTime, viewModel::skipRest, viewModel::cancelRest)
                }
                FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp).selectableGroup()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    sets.forEach { set ->
                        val number = LocaleFormatting.formatCount(set.setNumber, locale)
                        val setLabel = stringResource(R.string.set_number, number)
                        val result = recordedResult(set, state.weightUnit.symbol)
                        val status = listOfNotNull(when {
                            set.isCompleted -> stringResource(R.string.set_completed)
                            set.stopReason != null -> stopReasonLabel(set.stopReason)
                            else -> stringResource(R.string.guided_unlogged)
                        }, result).joinToString(", ")
                        val isSelected = set.id == selectedId
                        Box(Modifier.weight(1f).sizeIn(minWidth = 64.dp, minHeight = 48.dp)
                            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                RoundedCornerShape(8.dp))
                            .selectable(isSelected, enabled = !state.isSaving, role = Role.RadioButton, onClick = {
                                selectedId = set.id
                                awaitingResolution = null
                            }).semantics { contentDescription = setLabel; stateDescription = status },
                            contentAlignment = Alignment.Center) {
                            Text(when { set.isCompleted -> "✓ $setLabel"; set.isResolved -> "− $setLabel"; else -> setLabel },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                selected?.let { set ->
                    if (set.type == SetType.WARMUP) Text(stringResource(R.string.history_warmup), style = MaterialTheme.typography.labelLarge)
                    draftState.SaveableStateProvider(set.id) {
                        val matchedSet = state.previousSets.firstOrNull {
                            it.setNumber == set.setNumber && it.type == set.type && it.exerciseType == set.exerciseType &&
                                it.isCompleted && it.stopReason == null && it.stoppedAtTimestamp == null
                        }
                        val prior = matchedSet ?: state.previousSets.lastOrNull {
                            set.type == SetType.NORMAL && it.type == SetType.NORMAL && it.exerciseType == set.exerciseType &&
                                it.isCompleted && it.stopReason == null && it.stoppedAtTimestamp == null
                        }
                        val comparable = if (prior != null && state.previousWeightUnit != state.weightUnit) {
                            prior.copy(completedWeight = prior.completedWeight?.let {
                                convertWeightForEditing(it, state.previousWeightUnit, state.weightUnit)
                            }, completedAssistanceWeight = prior.completedAssistanceWeight?.let {
                                convertWeightForEditing(it, state.previousWeightUnit, state.weightUnit)
                            })
                        } else prior
                        comparable?.let { recordedResult(it, state.weightUnit.symbol) }?.let { result ->
                            Text(stringResource(if (matchedSet == null) R.string.guided_last_working_set else R.string.guided_last_time, result), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val precedingWorkSet = if (exercise.prescription.purpose == PrescriptionPurpose.ORDINARY &&
                            !exercise.isLegacyMobility && set.type == SetType.NORMAL) {
                            sets.lastOrNull { it.setNumber < set.setNumber && it.type != SetType.WARMUP }
                                ?.takeIf { it.type == SetType.NORMAL && it.isCompleted && it.stopReason == null &&
                                    it.exerciseType == set.exerciseType && (it.completedReps ?: 0) > 0 }
                        } else null
                        GymFloorSetRow(
                            set = set, weightUnit = state.weightUnit.symbol, previousSet = comparable,
                            precedingWorkSet = precedingWorkSet,
                            firstWeightGuidance = if (set.type == SetType.NORMAL) state.firstWeightGuidance else
                                FirstWeightGuidance(),
                            prescription = exercise.prescription.takeIf { set.type == SetType.NORMAL },
                            onCompletionChanged = { values, completed ->
                                if (!set.isResolved && completed) awaitingResolution = set.id
                                viewModel.setCompletion(set.id, values, completed)
                            },
                            onSkipSet = { reason, partial ->
                                if (!set.isResolved) awaitingResolution = set.id
                                viewModel.skipSet(set.id, reason, partial)
                            },
                            onRecordEffort = { rpe, rir -> viewModel.recordEffort(set.id, rpe, rir) },
                            onRecordFeltManageable = { viewModel.recordFeltManageable(set.id, it) },
                            saving = state.isSaving
                        )
                    }
                }

                if (nextOpen == null) {
                    val nextLabel = stringResource(if (state.isLastExercise) R.string.workout_action_finish_workout
                        else R.string.workout_action_next)
                    WallCrawlPrimaryButton(nextLabel, if (state.isLastExercise) viewModel::requestFinish else viewModel::nextExercise,
                        enabled = !state.isSaving)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!state.isFirstExercise) TextButton(onClick = viewModel::previousExercise, enabled = !state.isSaving) {
                        Text(stringResource(R.string.guided_previous_exercise), color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (!state.isLastExercise && nextOpen != null) TextButton(onClick = viewModel::nextExercise, enabled = !state.isSaving) {
                        Text(stringResource(R.string.workout_action_next), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (state.previousSets.isNotEmpty()) {
                    var showHistory by rememberSaveable { mutableStateOf(false) }
                    TextButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.Default.History, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (showHistory) R.string.guided_hide_previous_workout else R.string.guided_show_previous_workout))
                    }
                    if (showHistory) PreviousPerformanceCard(state.previousSets, state.previousSessionTimestamp, state.previousWeightUnit.symbol)
                }
            }
            if (expanded) {
                val dismiss = { returnFocus = true; expanded = false }
                Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    Surface(Modifier.fillMaxSize()) {
                        WorkoutPage(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp), heading = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                TextButton(onClick = dismiss) { Text(stringResource(R.string.action_close)) }
                            }
                        }, demonstration = {
                            ExerciseIllustration(state.currentCatalogExercise, visualProvider, Modifier.fillMaxSize())
                        }) {
                            Text(state.currentCatalogExercise?.let(vocabulary::coachingSummary)
                                ?: stringResource(R.string.exercise_detail_no_programming))
                            if (state.currentCatalogExercise?.let(vocabulary::hasUntranslatedCoachingSummary) == true)
                                Text(stringResource(R.string.exercise_detail_original_language), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** The scroll viewport supplies its height as a minimum; longer controls can extend the page. */
@Composable
private fun WorkoutPage(
    modifier: Modifier,
    reservedControlsHeight: Dp? = null,
    heading: @Composable () -> Unit,
    demonstration: @Composable () -> Unit,
    controls: @Composable () -> Unit
) {
    Layout(modifier = modifier, content = {
        Box { heading() }
        Box(propagateMinConstraints = true) { demonstration() }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { controls() }
    }) { (headerSlot, demoSlot, controlsSlot), constraints ->
        val natural = constraints.copy(minHeight = 0)
        val header = headerSlot.measure(natural)
        val editor = controlsSlot.measure(natural)
        val gap = 4.dp.roundToPx()
        val controlsHeight = reservedControlsHeight?.roundToPx() ?: editor.height
        val demoHeight = (constraints.minHeight - header.height - controlsHeight - 2 * gap)
            .coerceAtLeast(120.dp.roundToPx())
        val demo = demoSlot.measure(Constraints.fixed(constraints.maxWidth, demoHeight))
        val demoY = header.height + gap
        val editorY = demoY + demo.height + gap
        layout(constraints.maxWidth, maxOf(constraints.minHeight, editorY + editor.height)) {
            header.placeRelative(0, 0)
            demo.placeRelative(0, demoY)
            editor.placeRelative(0, editorY)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestTimerBar(restTimer: RestTimerUiState, onAddRest: () -> Unit, onSkipRest: () -> Unit,
    onCancelRest: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val countdown = restCountdownLabel(restTimer.remainingSeconds)
    val label = if (restTimer.isRunning) stringResource(R.string.rest_running, countdown) else stringResource(R.string.rest_finished)
    val spoken = if (restTimer.isRunning) stringResource(R.string.rest_remaining_accessibility, countdown) else label
    FlowRow(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
        .padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.align(Alignment.CenterVertically).clearAndSetSemantics { contentDescription = spoken }) {
            if (restTimer.isRunning) {
                Text(stringResource(R.string.guided_rest), style = MaterialTheme.typography.labelSmall)
                Text(countdown, style = MaterialTheme.typography.titleSmall)
            } else Text(label, style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onAddRest, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(stringResource(R.string.rest_add_thirty), color = MaterialTheme.colorScheme.onSurface)
        }
        if (restTimer.isRunning) TextButton(onClick = onSkipRest, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(stringResource(R.string.rest_skip), color = MaterialTheme.colorScheme.onSurface)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.guided_rest_options))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.guided_dismiss_timer)) },
                    onClick = { menu = false; onCancelRest() })
            }
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
    isProgram: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val openSets = LocaleFormatting.formatCount(openSetCount, locale)
    val title = pluralStringResource(R.plurals.finish_confirm_title, openSetCount, openSets)
    val body = pluralStringResource(R.plurals.finish_confirm_body, openSetCount, openSets)
    val programExplanation = if (isProgram) stringResource(R.string.program_partial_finish) else null
    val confirmLabel = stringResource(R.string.finish_confirm_action)
    val keepLabel = stringResource(R.string.finish_confirm_keep_going)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                programExplanation?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(keepLabel, color = MaterialTheme.colorScheme.onSurface) }
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
                    color = MaterialTheme.colorScheme.error,
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

/** The compact prescribed target for this exercise, written for the reader. */
@Composable
private fun prescriptionTarget(
    prescription: wallcrawl.elopenmike.com.core.model.ExercisePrescription
): String {
    val locale = LocalConfiguration.current.locales[0]
    val sets = LocaleFormatting.formatCount(prescription.targetSets, locale)
    val target = when (prescription.exerciseType) {
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
                    LocaleFormatting.formatMeasurement(it, locale)
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
    return prescription.holdSeconds?.let { value ->
        stringResource(R.string.workout_target_with_hold, target, LocaleFormatting.formatCount(value, locale))
    } ?: target
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
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Assertive }
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

        sets.forEach { set ->
            val result = recordedResult(set, weightUnit) ?: return@forEach
            Text(
                text = stringResource(R.string.previous_entry, result),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun recordedResult(set: WorkoutSet, weightUnit: String): String? {
    val locale = LocalConfiguration.current.locales[0]
    val detailSeparator = stringResource(R.string.detail_separator)
    return when (set.exerciseType) {
        ExerciseType.WEIGHT_REPS -> {
            val reps = set.completedReps ?: return null
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
            LocaleFormatting.formatCount(set.completedReps ?: return null, locale)
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
                LocaleFormatting.formatCount(set.completedReps ?: return null, locale)
            )
        }
        ExerciseType.DURATION -> stringResource(
            R.string.prescription_seconds,
            LocaleFormatting.formatCount(
                set.completedDurationSeconds ?: return null,
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
        ).joinToString(detailSeparator).ifBlank { return null }
    }
}

/** How often the visible countdown re-derives itself from the rest deadline. */
private const val REST_TICK_MILLIS = 250L
