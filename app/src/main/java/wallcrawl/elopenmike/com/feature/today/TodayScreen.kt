package wallcrawl.elopenmike.com.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.components.ConditionExecution
import wallcrawl.elopenmike.com.core.ui.components.ConditionRefusal
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.*
import wallcrawl.elopenmike.com.feature.program.ProgramPlanningChoice
import wallcrawl.elopenmike.com.feature.program.ProgramUiState
import wallcrawl.elopenmike.com.feature.program.positionText
import wallcrawl.elopenmike.com.feature.program.programFailureRes

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onStartWorkout: (sessionId: String) -> Unit,
    onResumeWorkout: (sessionId: String) -> Unit,
    onOpenTemplates: () -> Unit,
    programState: ProgramUiState,
    onManageProgram: () -> Unit,
    onRetryProgram: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: () -> Unit = {}
) {
    val pending by viewModel.pendingConditions.collectAsState()
    val selected by viewModel.planningIntent.collectAsState()
    val owner = LocalLifecycleOwner.current
    val state by produceState<TodayUiState>(TodayUiState.Loading, viewModel, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.uiState.collect { value = it }
        }
    }
    val ready = state as? TodayUiState.Success
    val active = when (val current = state) {
        is TodayUiState.Success -> current.activeSession
        is TodayUiState.Preparing -> current.activeSession
        is TodayUiState.Error -> current.activeSession
        TodayUiState.Loading -> null
    }
    val workout = ready?.suggestedWorkout
    val name = workout?.let { generatedWorkoutTitle(it.title) }.orEmpty()
    val rationale = workout?.let { generatedWorkoutRationale(it.rationale, it.unavailableFocusMuscles, it.rankingReasons) }.orEmpty()
    TodayContent(
        state = state, workoutName = name, modifier = modifier,
        onStartWorkout = {
            viewModel.startWorkout(name, rationale, workout) { id ->
                // If the user left Train during the write, its next visit offers this saved session.
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onStartWorkout(id)
            }
        },
        onResumeWorkout = { active?.let { onResumeWorkout(it.id) } },
        onRegenerate = viewModel::regenerateWorkout, onOpenTemplates = onOpenTemplates,
        onDeloadAction = viewModel::decideDeload, onOpenProfile = onOpenProfile,
        programState = programState, selected = selected, onSelect = viewModel::selectPlanningIntent,
        onManageProgram = onManageProgram, onRetryProgram = onRetryProgram,
        conditionContent = {
            if (pending.pendingFailed) StatusText(stringResource(R.string.condition_pending_read_error))
            if (!pending.pending.isNullOrEmpty()) {
                Text(stringResource(R.string.condition_pending_hint))
                TextButton(onClick = onOpenProfile) { Text(stringResource(R.string.condition_edit)) }
            }
        }
    )
}

private enum class TrainDialog { SOURCE, PREVIEW, OPTIONS, ADJUSTMENT, REASONS }

@Composable
internal fun TodayContent(
    state: TodayUiState,
    workoutName: String,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenTemplates: () -> Unit,
    onDeloadAction: (DeloadAction) -> Unit = {},
    conditionContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    programState: ProgramUiState = ProgramUiState(),
    selected: WorkoutPlanningIntent = WorkoutPlanningIntent.ORDINARY,
    onSelect: (WorkoutPlanningIntent) -> Unit = {},
    onManageProgram: () -> Unit = {},
    onRetryProgram: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    var dialog by rememberSaveable { mutableStateOf<TrainDialog?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val ready = state as? TodayUiState.Success
    val error = state as? TodayUiState.Error
    val active = ready?.activeSession ?: error?.activeSession ?: (state as? TodayUiState.Preparing)?.activeSession
    val deload = ready?.deload ?: error?.deload
    val busy = ready?.isRegenerating == true || error?.isBusy == true || deload?.isSaving == true ||
        (ready != null && ready.planningIntent != selected)
    val program = programState.program as? ProgramReadState.Supported
    val manageLabel = stringResource(if (programState.program is ProgramReadState.Empty) R.string.guided_train_explore else R.string.program_manage)
    val programProblem = programState.error ?: when {
        programState.program is ProgramReadState.Unsupported -> R.string.program_unsupported
        else -> program?.profileFailure?.let(::programFailureRes)
    }
    val programSummary = program?.let {
        val status = when (it.enrollment.status) {
            ProgramStatus.ACTIVE -> R.string.program_active
            ProgramStatus.PAUSED -> R.string.program_paused
            ProgramStatus.COMPLETED -> R.string.program_completed
        }
        val progress = stringResource(R.string.program_progress, LocaleFormatting.formatCount(it.progress.completedSlots, locale),
            LocaleFormatting.formatCount(it.enrollment.configuration.sessionsPerWeek * 4, locale))
        "${stringResource(status)} · $progress"
    }
    val chooseSource = { dialog = TrainDialog.SOURCE }
    val settledListState = rememberLazyListState()
    val settled = ready != null || error != null
    LazyColumn(modifier.fillMaxSize().testTag(if (settled) "today-list" else "today-preparing"),
        state = if (settled) settledListState else rememberLazyListState(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (ready != null) item(key = "week") {
            val name = ready.userProfile.name.lineSequence().firstOrNull()?.trim().orEmpty()
            if (name.isNotBlank()) Text(stringResource(R.string.today_greeting, name),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val goal = ready.userProfile.daysPerWeek
            Text(pluralStringResource(R.plurals.today_weekly_progress, goal,
                LocaleFormatting.formatCount(ready.completedThisWeek, locale), LocaleFormatting.formatCount(goal, locale)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp))
        }
        if (active != null) item(key = "resume") {
            WallCrawlCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.guided_train_active), style = MaterialTheme.typography.labelLarge)
                Text(active.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(pluralStringResource(R.plurals.today_active_session_sets, active.totalSetsCount,
                    LocaleFormatting.formatCount(active.completedSetsCount, locale),
                    LocaleFormatting.formatCount(active.totalSetsCount, locale)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                WallCrawlPrimaryButton(stringResource(R.string.guided_train_resume), onResumeWorkout)
            }
        }
        item(key = "conditions") { conditionContent() }
        if (active == null) {
            when {
                ready != null -> item(key = "workout") {
                    WorkoutHero(ready, workoutName, selected, busy, onStartWorkout, chooseSource, onDialog = { dialog = it })
                }
                error != null -> item(key = "error") {
                    WallCrawlCard(Modifier.fillMaxWidth()) {
                        SourceRow(selected, chooseSource, !busy)
                        Text(stringResource(R.string.guided_train_error), style = MaterialTheme.typography.titleLarge)
                        error.conditionRefusal?.let { ConditionRefusal(it.reasons) }
                        StatusText(stringResource(error.error.messageRes))
                        Spacer(Modifier.height(12.dp))
                        WallCrawlPrimaryButton(stringResource(R.string.action_try_again), onRegenerate, enabled = !busy)
                        if (busy) StatusText(stringResource(R.string.guided_train_busy))
                        TextButton(onClick = onOpenProfile) { Text(stringResource(R.string.program_edit_profile)) }
                    }
                }
                else -> item(key = "loading") {
                    Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        StatusText(stringResource(R.string.today_loading))
                    }
                }
            }
            if (deload?.offer != null || (error != null && deload?.acceptedChoice != null)) item(key = "adjustment") {
                TrainLink(stringResource(if (deload?.acceptedChoice != null) R.string.today_deload_accepted
                    else R.string.guided_train_adjustment_review), onClick = { dialog = TrainDialog.ADJUSTMENT })
            }
            deload?.error?.let { item { StatusText(stringResource(it.messageRes)) } }
        }
        item(key = "routines") {
            TrainLink(stringResource(R.string.today_my_workouts_title), onClick = onOpenTemplates)
        }
        item(key = "program") {
            TrainLink(manageLabel, programSummary, onManageProgram)
            if (programProblem != null) {
                Text(stringResource(programProblem), color = MaterialTheme.colorScheme.error)
                if (programState.error != null) TextButton(onClick = onRetryProgram) { Text(stringResource(R.string.action_try_again)) }
            }
        }
        if (active == null && (ready != null || deload != null)) item(key = "options") {
            TrainLink(stringResource(R.string.guided_train_options), onClick = { dialog = TrainDialog.OPTIONS })
        }
    }
    if (active != null) return
    val close = { dialog = null }
    when (dialog) {
        TrainDialog.SOURCE -> TrainDetail(R.string.guided_train_source, close) {
            ProgramPlanningChoice(selected,
                program?.enrollment?.status == ProgramStatus.ACTIVE && program.profileFailure == null && programProblem == null,
                !busy && programState.activeSession == null) { intent -> close(); onSelect(intent) }
            if (programProblem != null) Text(stringResource(programProblem), color = MaterialTheme.colorScheme.error)
            if (programState.error != null) TextButton(onClick = onRetryProgram) { Text(stringResource(R.string.action_try_again)) }
            TrainLink(stringResource(R.string.today_my_workouts_title), onClick = { close(); onOpenTemplates() })
            TrainLink(manageLabel, programSummary, onClick = { close(); onManageProgram() })
        }
        TrainDialog.PREVIEW -> ready?.let {
            TrainDetail(R.string.guided_train_preview, close) { ExercisePreview(it) }
        }
        TrainDialog.REASONS -> ready?.let {
            TrainDetail(R.string.guided_train_why, close) {
                Text(generatedWorkoutRationale(it.suggestedWorkout.rationale, it.suggestedWorkout.unavailableFocusMuscles,
                    it.suggestedWorkout.rankingReasons))
            }
        }
        TrainDialog.OPTIONS -> TrainDetail(R.string.guided_train_options, close) {
            if (ready != null) {
                Text(stringResource(R.string.guided_train_regenerate_hint))
                WallCrawlSecondaryButton(stringResource(R.string.today_regenerate_content_description), { close(); onRegenerate() }, enabled = !busy)
            }
            if (deload != null && deload.acceptedChoice == null) {
                WallCrawlSecondaryButton(stringResource(R.string.today_deload_request), { onDeloadAction(DeloadAction.REQUEST); close() }, enabled = !busy)
            }
        }
        TrainDialog.ADJUSTMENT -> deload?.let {
            TrainDetail(if (it.acceptedChoice != null) R.string.today_deload_accepted else R.string.today_deload_title, close) {
                TodayDeloadCard(it, { action -> onDeloadAction(action); close() },
                    enabled = !busy, workout = ready?.suggestedWorkout, unit = ready?.prescriptionUnit)
            }
        }
        null -> Unit
    }
}

@Composable
private fun WorkoutHero(
    state: TodayUiState.Success, name: String, selected: WorkoutPlanningIntent, busy: Boolean, onStart: () -> Unit,
    onChange: () -> Unit, onDialog: (TrainDialog) -> Unit
) {
    val workout = state.suggestedWorkout
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    WallCrawlCard(Modifier.fillMaxWidth()) {
        SourceRow(selected, onChange, !busy)
        val startLabel = stringResource(if (selected == WorkoutPlanningIntent.PROGRAM) R.string.program_start_session
            else R.string.today_action_start)
        if (state.planningIntent != selected) {
            StatusText(stringResource(R.string.guided_train_busy))
            WallCrawlPrimaryButton(startLabel, onStart, enabled = false)
            return@WallCrawlCard
        }
        state.programSession?.let { Text(positionText(it.position), style = MaterialTheme.typography.labelLarge) }
        Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(vocabulary.muscles(workout.focusMuscles).joinToString(stringResource(R.string.detail_separator)),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val duration = stringResource(R.string.today_stat_duration, LocaleFormatting.formatCount(workout.estimatedDurationMinutes, locale))
        val count = workout.exercises.size
        val exercises = pluralStringResource(R.plurals.count_exercises, count, LocaleFormatting.formatCount(count, locale))
        val sets = workout.exercises.sumOf { it.targetSets }
        val setLabel = pluralStringResource(R.plurals.count_sets, sets, LocaleFormatting.formatCount(sets, locale))
        Text(listOf(duration, exercises, setLabel).joinToString(" · "), Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (workout.unavailableFocusMuscles.isNotEmpty()) {
            Text(stringResource(R.string.guided_train_focus_missing,
                vocabulary.muscles(workout.unavailableFocusMuscles).joinToString(stringResource(R.string.list_separator))))
        }
        if (workout.unavailableFocusMuscles.isNotEmpty() || workout.rankingReasons.isNotEmpty()) {
            TextButton(onClick = { onDialog(TrainDialog.REASONS) }) { Text(stringResource(R.string.guided_train_why)) }
        }
        if (state.deload?.acceptedChoice != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.today_deload_applied_summary), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onDialog(TrainDialog.ADJUSTMENT) }) { Text(stringResource(R.string.today_details)) }
            }
        }
        if (state.conditionContext != null) ExercisePreview(state)
        if (busy) StatusText(stringResource(when {
            state.isStarting -> R.string.guided_train_starting
            state.deload?.isSaving == true -> R.string.today_deload_saving
            else -> R.string.guided_train_busy
        }))
        WallCrawlPrimaryButton(startLabel, onStart, enabled = !busy)
        if (state.conditionContext == null) TextButton(onClick = { onDialog(TrainDialog.PREVIEW) }) {
            Text(stringResource(R.string.today_view_exercises))
        }
    }
}

@Composable
private fun SourceRow(intent: WorkoutPlanningIntent, onChange: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (intent == WorkoutPlanningIntent.PROGRAM) R.string.program_session_choice
            else R.string.guided_train_ordinary), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = onChange, enabled = enabled) { Text(stringResource(R.string.guided_train_change)) }
    }
}

@Composable
private fun ExercisePreview(state: TodayUiState.Success) {
    val workout = state.suggestedWorkout
    val vocabulary = LocalExerciseVocabulary.current
    val locale = LocalConfiguration.current.locales[0]
    workout.exercises.forEachIndexed { index, exercise ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${LocaleFormatting.formatCount(index + 1, locale)}. ${vocabulary.exerciseName(exercise.exerciseId)}",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            PrescriptionPreview(exercise, state.prescriptionUnit)
            if (exercise.notes.isNotBlank()) Text(exercise.notes)
            ConditionExecution(state.conditionContext, exercise.exerciseId, exercise.prescription.purpose)
            workout.progressionDecisions.firstOrNull { it.exerciseId == exercise.exerciseId }?.let {
                Text(progressionNotice(it, state.prescriptionUnit, state.prescriptionUnit), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TrainLink(label: String, summary: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 48.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusText(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun TrainDetail(title: Int, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title)) },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } })
}
