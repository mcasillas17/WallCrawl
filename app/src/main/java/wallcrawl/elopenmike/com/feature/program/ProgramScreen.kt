package wallcrawl.elopenmike.com.feature.program

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.labelRes

@Composable
fun rememberProgramState(viewModel: ProgramViewModel): ProgramUiState {
    val owner = LocalLifecycleOwner.current
    val state by produceState(ProgramUiState(), viewModel, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.uiState.collect { value = it }
        }
    }
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramScreen(viewModel: ProgramViewModel, onBack: () -> Unit, onEditProfile: () -> Unit,
    onResume: (String) -> Unit, onOpenHistory: (String) -> Unit) {
    ProgramScreen(rememberProgramState(viewModel), viewModel::act, viewModel::confirm,
        viewModel::dismissConfirmation, viewModel::retry, onBack, onEditProfile, onResume, onOpenHistory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgramScreen(state: ProgramUiState, onAction: (ProgramAction) -> Unit, onConfirm: () -> Unit,
    onDismiss: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit, onEditProfile: () -> Unit,
    onResume: (String) -> Unit, onOpenHistory: (String) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.program_title), color = MaterialTheme.colorScheme.onSurface) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(16.dp).testTag("program-management"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.activeSession?.let { session ->
                Text(stringResource(R.string.program_active_workout), color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = { onResume(session.id) }) { Text(stringResource(R.string.today_action_resume), color = MaterialTheme.colorScheme.onPrimary) }
            }
            ProgramSummary(state, onRetry = onRetry)
            Text(stringResource(R.string.program_disclosure), color = MaterialTheme.colorScheme.onSurface)
            val read = state.program
            val enabled = !state.loading && !state.saving && state.activeSession == null && read != null
            when (read) {
                is ProgramReadState.Empty -> {
                    read.profile?.let { profile ->
                        ProgramConfigurationText(profile.primaryGoal.name, profile.goals.map { it.name }.sorted(), profile.daysPerWeek)
                    }
                    Button(onClick = { onAction(ProgramAction.START) }, enabled = enabled) {
                        Text(stringResource(R.string.program_start), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                is ProgramReadState.Supported -> {
                    when (read.enrollment.status) {
                        ProgramStatus.ACTIVE -> Button(onClick = { onAction(ProgramAction.PAUSE) }, enabled = enabled) {
                            Text(stringResource(R.string.program_pause), color = MaterialTheme.colorScheme.onPrimary)
                        }
                        ProgramStatus.PAUSED -> Button(onClick = { onAction(ProgramAction.RESUME) },
                            enabled = enabled && read.profileFailure == null) { Text(stringResource(R.string.program_resume), color = MaterialTheme.colorScheme.onPrimary) }
                        ProgramStatus.COMPLETED -> Text(stringResource(R.string.program_completed_body), color = MaterialTheme.colorScheme.onSurface)
                    }
                    OutlinedButton(onClick = { onAction(ProgramAction.RESTART) }, enabled = enabled && read.profileFailure == null) {
                        Text(stringResource(R.string.program_restart), color = MaterialTheme.colorScheme.onSurface)
                    }
                    ReplaceButton(enabled, onAction)
                    Text(stringResource(R.string.program_completed_history), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    read.sessions.filter { it.status == SessionStatus.COMPLETED }.forEach { slot ->
                        TextButton(onClick = { onOpenHistory(slot.association.sessionId) }) {
                            Text(positionText(slot.association.snapshot.position), color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                is ProgramReadState.Unsupported -> {
                    if (read.status == ProgramStatus.ACTIVE.name) OutlinedButton(
                        onClick = { onAction(ProgramAction.PAUSE) }, enabled = enabled) { Text(stringResource(R.string.program_pause), color = MaterialTheme.colorScheme.onSurface) }
                    ReplaceButton(enabled, onAction)
                }
                null -> Unit
            }
            TextButton(onClick = onEditProfile) { Text(stringResource(R.string.program_edit_profile), color = MaterialTheme.colorScheme.onSurface) }
        }
    }
    state.confirmation?.let { confirmation ->
        val title = stringResource(if (confirmation.action == ProgramAction.RESTART) R.string.program_restart else R.string.program_replace)
        val body = stringResource(R.string.program_restart_confirmation)
        val run = stringResource(R.string.program_run, confirmation.expected.programId.orEmpty())
        val confirm = stringResource(R.string.program_confirm)
        val keep = stringResource(R.string.program_keep)
        AlertDialog(onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(body, color = MaterialTheme.colorScheme.onSurface); Text(run, color = MaterialTheme.colorScheme.onSurface) } },
            confirmButton = { TextButton(onClick = onConfirm, enabled = !state.saving) { Text(confirm, color = MaterialTheme.colorScheme.onSurface) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(keep, color = MaterialTheme.colorScheme.onSurface) } })
    }
}

@Composable
private fun ReplaceButton(enabled: Boolean, onAction: (ProgramAction) -> Unit) {
    OutlinedButton(onClick = { onAction(ProgramAction.REPLACE) }, enabled = enabled) { Text(stringResource(R.string.program_replace), color = MaterialTheme.colorScheme.onSurface) }
}

@Composable
fun ProgramSummary(state: ProgramUiState, onManage: (() -> Unit)? = null, onRetry: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().testTag("program-summary")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.program_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (state.loading) CircularProgressIndicator()
            state.error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again), color = MaterialTheme.colorScheme.onSurface) }
            }
            when (val read = state.program) {
                is ProgramReadState.Empty -> Text(stringResource(R.string.program_optional), color = MaterialTheme.colorScheme.onSurface)
                is ProgramReadState.Unsupported -> {
                    Text(stringResource(R.string.program_unsupported), color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.program_run, read.facts.programId), color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.program_policies, read.facts.lifecyclePolicyVersion, read.facts.configurationPolicyVersion), color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.program_recorded_status, read.status), color = MaterialTheme.colorScheme.onSurface)
                    ProgramConfigurationText(read.facts.primaryGoal, read.facts.goals, read.facts.sessionsPerWeek)
                }
                is ProgramReadState.Supported -> {
                    val enrollment = read.enrollment
                    Text(stringResource(when (enrollment.status) {
                        ProgramStatus.ACTIVE -> R.string.program_active
                        ProgramStatus.PAUSED -> R.string.program_paused
                        ProgramStatus.COMPLETED -> R.string.program_completed
                    }), color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.program_progress, count(read.progress.completedSlots), count(enrollment.configuration.sessionsPerWeek * 4)), color = MaterialTheme.colorScheme.onSurface)
                    read.progress.nextPosition?.let { position ->
                        Text(positionText(position), color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(objectiveRes(if (position.blockNumber == 1) ProgramObjective.ESTABLISH_ROUTINE else ProgramObjective.CONTINUE_AND_RECORD)), color = MaterialTheme.colorScheme.onSurface)
                    }
                    ProgramConfigurationText(enrollment.configuration.primaryGoal.name, enrollment.configuration.goals.map { it.name }, enrollment.configuration.sessionsPerWeek)
                    read.profileFailure?.let { Text(stringResource(programFailureRes(it)), color = MaterialTheme.colorScheme.error) }
                }
                null -> Unit
            }
            onManage?.let { TextButton(onClick = it) { Text(stringResource(R.string.program_manage), color = MaterialTheme.colorScheme.onSurface) } }
        }
    }
}

@Composable
fun ProgramAssociationText(association: ProgramHistoryAssociation) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.program_recorded_heading), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.program_run, association.facts.programId), color = MaterialTheme.colorScheme.onSurface)
        Text(positionText(association.position), color = MaterialTheme.colorScheme.onSurface)
        ProgramConfigurationText(association.facts.primaryGoal, association.facts.goals, association.facts.sessionsPerWeek)
        val known = association.supportedSnapshot
        Text(if (known != null) stringResource(objectiveRes(known.objective)) else stringResource(R.string.program_recorded_objective, association.objective), color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.program_policies, association.facts.lifecyclePolicyVersion, association.facts.configurationPolicyVersion), color = MaterialTheme.colorScheme.onSurface)
        if (known == null) Text(stringResource(R.string.program_unsupported), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ProgramConfigurationText(primary: String, goals: List<String>, frequency: Int) {
    Text(stringResource(R.string.program_configuration, goalText(primary), goals.map { goalText(it) }.joinToString(), count(frequency)), color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun goalText(name: String): String = FitnessGoal.entries.firstOrNull { it.name == name }?.let { stringResource(it.labelRes) } ?: name

@Composable
internal fun positionText(position: ProgramPosition): String = stringResource(R.string.program_position,
    count(position.blockNumber), count(position.weekNumber), count(position.sessionInWeek))

@Composable
private fun count(number: Int) = LocaleFormatting.formatCount(number, LocalConfiguration.current.locales[0])
private fun objectiveRes(objective: ProgramObjective) = when (objective) {
    ProgramObjective.ESTABLISH_ROUTINE -> R.string.program_objective_first
    ProgramObjective.CONTINUE_AND_RECORD -> R.string.program_objective_second
}

@Composable
fun ProgramPlanningChoice(selected: WorkoutPlanningIntent, programAvailable: Boolean, enabled: Boolean,
    onSelect: (WorkoutPlanningIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.program_choice), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        WorkoutPlanningIntent.entries.forEach { intent ->
            val available = enabled && (intent == WorkoutPlanningIntent.ORDINARY || programAvailable)
            Row(Modifier.fillMaxWidth().testTag("planning-${intent.name}")
                .selectable(selected = selected == intent, enabled = available,
                    role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { onSelect(intent) })
                .padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = selected == intent, onClick = null, enabled = available)
                Text(stringResource(if (intent == WorkoutPlanningIntent.PROGRAM) R.string.program_session_choice else R.string.program_ordinary_choice),
                    Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
