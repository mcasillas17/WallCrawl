package wallcrawl.elopenmike.com.feature.history

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.StateFlow
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.ProgramViolationCode
import wallcrawl.elopenmike.com.core.database.repository.WorkoutHistoryDetail
import wallcrawl.elopenmike.com.core.database.repository.HistoricalSessionHeader
import wallcrawl.elopenmike.com.core.database.repository.HistoricalSourceKey
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.localization.progressionAxisRes
import wallcrawl.elopenmike.com.core.ui.localization.progressionHoldReasonRes
import wallcrawl.elopenmike.com.core.ui.localization.workoutRankingNotice

@Composable
fun WorkoutHistoryDetailScreen(viewModel: WorkoutHistoryDetailViewModel, onBack: () -> Unit) {
    val state by resumedHistoryState(viewModel.uiState, WorkoutHistoryDetailUiState.Loading)
    WorkoutHistoryDetailScreen(state, viewModel::retry, onBack)
}

@Composable
fun WorkoutHistoryListScreen(
    viewModel: WorkoutHistoryListViewModel, onOpenSession: (String) -> Unit, onBack: () -> Unit
) {
    val state by resumedHistoryState(viewModel.uiState, WorkoutHistoryListUiState.Loading)
    WorkoutHistoryListScreen(state, onOpenSession, onBack, viewModel::retry, viewModel::older, viewModel::newer)
}

@Composable
private fun <T> resumedHistoryState(flow: StateFlow<T>, loading: T): State<T> {
    val owner = LocalLifecycleOwner.current
    return produceState(loading, flow, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                flow.collect { value = it }
            } finally {
                value = loading
            }
        }
    }
}

@Composable
fun WorkoutHistoryListScreen(
    state: WorkoutHistoryListUiState, onOpenSession: (String) -> Unit, onBack: () -> Unit,
    onRetry: () -> Unit, onOlder: () -> Unit, onNewer: () -> Unit
) {
    HistoryScaffold(R.string.history_title, onBack) {
        LazyColumn(Modifier.fillMaxSize().testTag("history-list"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                WorkoutHistoryListUiState.Loading -> message(R.string.history_loading)
                is WorkoutHistoryListUiState.ReadError -> {
                    message(R.string.history_error)
                    item { Button(onClick = onRetry) { HistoryText(stringResource(R.string.history_retry)) } }
                    if (state.page > 0) item {
                        Button(onClick = onNewer) { HistoryText(stringResource(R.string.history_newer)) }
                    }
                }
                is WorkoutHistoryListUiState.Loaded -> {
                    item { HistoryText(stringResource(R.string.history_page, count(state.page + 1))) }
                    if (state.sessions.isEmpty()) message(R.string.history_empty)
                    state.sessions.forEach { session ->
                        item(key = session.id) {
                            Card(
                                onClick = { onOpenSession(session.id) },
                                modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HistoryText(session.name, style = MaterialTheme.typography.titleMedium)
                                    HistoryField(R.string.history_completed_at, date(session.completedAtTimestamp))
                                    HistoryField(R.string.history_actual_duration, minutes(session.actualDurationMinutes))
                                }
                            }
                        }
                    }
                    if (state.page > 0) item {
                        Button(onClick = onNewer) { HistoryText(stringResource(R.string.history_newer)) }
                    }
                    if (state.hasOlder) item {
                        Button(onClick = onOlder) { HistoryText(stringResource(R.string.history_older)) }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutHistoryDetailScreen(
    state: WorkoutHistoryDetailUiState, onRetry: () -> Unit, onBack: () -> Unit
) {
    var technicalExpanded by rememberSaveable((state as? WorkoutHistoryDetailUiState.Loaded)?.detail?.session?.id) {
        mutableStateOf(false)
    }
    HistoryScaffold(R.string.history_detail_title, onBack) {
        LazyColumn(Modifier.fillMaxSize().testTag("history-detail"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                WorkoutHistoryDetailUiState.Loading -> message(R.string.history_loading)
                WorkoutHistoryDetailUiState.Missing -> message(R.string.history_missing)
                WorkoutHistoryDetailUiState.ReadError -> {
                    message(R.string.history_error)
                    item { Button(onClick = onRetry) { HistoryText(stringResource(R.string.history_retry)) } }
                }
                is WorkoutHistoryDetailUiState.Unsupported -> {
                    message(R.string.history_unsupported)
                    item { HistoryText(state.session.name) }
                    field(R.string.history_status) { stringResource(sessionStatusRes(state.session.status)) }
                }
                is WorkoutHistoryDetailUiState.Loaded -> {
                    sessionFacts(state.detail)
                    recordedReasons(state.reasons)
                    state.detail.session.exercises.sortedWith(compareBy({ it.orderIndex }, { it.id })).forEach { exercise ->
                        item { HistoryHeading(exercise.exerciseId) }
                        field(R.string.history_exercise_type) { stringResource(exerciseTypeRes(exercise.prescription.exerciseType)) }
                        if (exercise.notes.isNotEmpty()) field(R.string.history_notes) { exercise.notes }
                        heading(R.string.history_previous)
                        message(R.string.history_comparison_note)
                        // Core supplies the SQL-filtered comparison exercise, not the intact
                        // session's exercise. Do not re-select or reconstruct its eligibility here.
                        val previous = state.detail.previousPerformances[exercise.id]
                        if (previous == null) message(R.string.history_previous_unavailable)
                        else evidence(previous.session, previous.exercise)
                        heading(R.string.history_prescription)
                        prescription(exercise.prescription, state.detail.session.weightUnit)
                        exercise.sets.sortedWith(compareBy({ it.setNumber }, { it.id })).forEach { set ->
                            item { HistoryHeading(stringResource(R.string.set_number, count(set.setNumber))) }
                            field(R.string.history_set_type) { stringResource(setTypeRes(set.type)) }
                            field(R.string.history_status) { stringResource(setStatusRes(set)) }
                            setMeasurements(set, state.detail.session.weightUnit)
                            field(R.string.effort_rpe) { set.rpe?.let { measurement(it.toDouble()) } ?: notRecorded() }
                            field(R.string.effort_rir) { set.rir?.let { count(it) } ?: notRecorded() }
                            field(R.string.set_felt_manageable) { answer(set.feltManageable) }
                            field(R.string.history_stop_reason) { set.stopReason?.let { stringResource(it.labelRes) } ?: notRecorded() }
                            field(R.string.history_completed_at) { date(set.completedAtTimestamp) }
                            field(R.string.history_stopped_at) { date(set.stoppedAtTimestamp) }
                        }
                        heading(R.string.history_sources)
                        val provenance = state.reasons?.progression?.firstOrNull { it.exerciseId == exercise.exerciseId }
                        if (provenance == null || provenance.sourceSessionIds.isEmpty()) message(R.string.history_no_sources)
                        provenance?.sourceSessionIds?.forEach { id ->
                            field(R.string.history_source_id) { id }
                            val source = state.detail.progressionSources[HistoricalSourceKey(id, exercise.id)]
                            if (source == null) message(R.string.history_source_unavailable)
                            else evidence(source.session, source.exercise)
                        }
                    }
                    item {
                        val description = stringResource(if (technicalExpanded) R.string.progress_details_expanded
                            else R.string.progress_details_collapsed)
                        OutlinedButton(
                            onClick = { technicalExpanded = !technicalExpanded },
                            modifier = Modifier.fillMaxWidth().semantics { stateDescription = description }
                        ) { HistoryText(stringResource(R.string.history_technical)) }
                    }
                    if (technicalExpanded) technicalDetails(state.detail, state.reasons)
                }
            }
        }
    }
}

@Composable
private fun HistoryScaffold(@StringRes title: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column {
            TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
                HistoryText(stringResource(R.string.action_back))
            }
            HistoryText(stringResource(title), Modifier.padding(horizontal = 16.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

private fun LazyListScope.sessionFacts(detail: WorkoutHistoryDetail) {
    val session = detail.session
    item { HistoryHeading(session.name) }
    message(R.string.history_read_only)
    if (session.notes.isNotEmpty()) field(R.string.history_notes) { session.notes }
    field(R.string.history_origin) { stringResource(if (session.origin == WorkoutOrigin.PLANNER)
        R.string.history_origin_planner else R.string.history_origin_template) }
    session.sourceTemplateId?.let { field(R.string.history_template) { it } }
    field(R.string.history_started) { date(session.startedAtTimestamp) }
    field(R.string.history_completed_at) { date(session.completedAtTimestamp) }
    field(R.string.history_target_duration) { minutes(session.targetDurationMinutes) }
    field(R.string.history_actual_duration) { minutes(session.actualDurationMinutes) }
    field(R.string.history_unit) { session.weightUnit.symbol }
    if (session.focusMuscles.isNotEmpty()) field(R.string.history_focus) {
        val vocabulary = LocalExerciseVocabulary.current
        session.focusMuscles.joinToString(stringResource(R.string.list_separator)) { vocabulary.muscle(it) }
    }
    detail.summary?.let { summary ->
        field(R.string.summary_metric_sets) {
            pluralStringResource(R.plurals.count_sets_logged, summary.totalSetsCompleted, count(summary.totalSetsCompleted))
        }
        field(R.string.summary_metric_volume) {
            if (summary.totalVolume == 0.0) stringResource(R.string.summary_no_external_volume)
            else stringResource(R.string.progress_volume_tonnage, measurement(summary.totalVolume), summary.unit.symbol)
        }
        field(R.string.summary_metric_prs) { count(summary.prCount) }
    }
    message(R.string.history_summary_semantics)
}

private fun LazyListScope.prescription(prescription: ExercisePrescription, unit: WeightUnit) {
    field(R.string.history_target_sets) {
        pluralStringResource(R.plurals.count_sets, prescription.targetSets, count(prescription.targetSets))
    }
    prescription.repRange?.let { range -> field(R.string.history_rep_range) {
        stringResource(R.string.prescription_rep_range, count(range.min), count(range.max))
    } }
    if (prescription.exerciseType == ExerciseType.WEIGHT_REPS)
        field(R.string.set_field_load) { weight(prescription.targetWeight, unit) }
    if (prescription.exerciseType == ExerciseType.ASSISTED_BODYWEIGHT)
        field(R.string.history_assistance) { weight(prescription.targetAssistanceWeight, unit) }
    prescription.targetDurationSeconds?.let { field(R.string.set_field_seconds) { seconds(it) } }
    prescription.targetDistanceMeters?.let { field(R.string.set_field_meters) { meters(it) } }
    field(R.string.history_effort_target) {
        prescription.effortTarget?.let {
            stringResource(R.string.prescription_rep_range, count(it.minRir), count(it.maxRir))
        } ?: notRecorded()
    }
    field(R.string.history_rest_seconds) { seconds(prescription.restSeconds) }
    field(R.string.history_rest_class) {
        prescription.restClass?.let { stringResource(when (it) {
            RestClass.SHORT -> R.string.history_rest_short
            RestClass.MODERATE -> R.string.history_rest_moderate
            RestClass.LONG -> R.string.history_rest_long
        }) } ?: notRecorded()
    }
    field(R.string.history_rest_source) {
        prescription.restTargetSource?.let { stringResource(if (it == RestTargetSource.USER_PREFERENCE)
            R.string.history_rest_preference else R.string.history_rest_policy) } ?: notRecorded()
    }
}

private fun LazyListScope.setMeasurements(set: WorkoutSet, unit: WeightUnit) {
    listOf(false, true).forEach { performed ->
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryText(stringResource(if (performed) R.string.history_performed else R.string.history_planned),
                        style = MaterialTheme.typography.titleSmall)
                    val measurements = historyMeasurements(set, performed)
                    if (measurements.isEmpty()) HistoryText(notRecorded())
                    measurements.forEach { field ->
                        HistoryField(when (field.kind) {
                            HistoryMeasurementKind.REPS -> R.string.set_field_reps
                            HistoryMeasurementKind.LOAD -> R.string.set_field_load
                            HistoryMeasurementKind.ASSISTANCE -> R.string.history_assistance
                            HistoryMeasurementKind.SECONDS -> R.string.set_field_seconds
                            HistoryMeasurementKind.METERS -> R.string.set_field_meters
                        }, field.value?.let { value -> when (field.kind) {
                            HistoryMeasurementKind.REPS -> pluralStringResource(R.plurals.count_reps, value.toInt(), count(value.toInt()))
                            HistoryMeasurementKind.LOAD, HistoryMeasurementKind.ASSISTANCE -> weight(value, unit)
                            HistoryMeasurementKind.SECONDS -> seconds(value.toInt())
                            HistoryMeasurementKind.METERS -> meters(value)
                        } } ?: notRecorded())
                    }
                }
            }
        }
    }
}

private fun LazyListScope.evidence(session: HistoricalSessionHeader, exercise: WorkoutExercise) {
    item { HistoryText(session.name, style = MaterialTheme.typography.titleMedium) }
    field(R.string.history_completed_at) { date(session.completedAtTimestamp) }
    field(R.string.history_unit) { session.weightUnit.symbol }
    message(R.string.history_evidence_note)
    exercise.sets.sortedWith(compareBy({ it.setNumber }, { it.id })).forEach { set ->
        item { HistoryText(stringResource(R.string.set_number, count(set.setNumber))) }
        setMeasurements(set, session.weightUnit)
    }
}

private fun LazyListScope.recordedReasons(reasons: HistoryReasons?) {
    heading(R.string.history_explanations)
    if (reasons == null) {
        message(R.string.history_no_recommendation)
        return
    }
    message(R.string.history_provenance_limit)
    reasons.outcome?.let { message(if (it == "VALID") R.string.history_validation_valid else R.string.history_validation_repaired) }
    reasons.violations.forEach { message(violationRes(it)) }
    reasons.progression.forEach { reason ->
        item {
            HistoryText(reason.exerciseId, style = MaterialTheme.typography.titleSmall)
            HistoryText(progressionHoldReasonRes(reason.reason)?.let { stringResource(it) }
                ?: stringResource(R.string.history_advanced, stringResource(progressionAxisRes(requireNotNull(reason.axis)))))
        }
    }
    reasons.deload?.let {
        message(when (it.source) {
            DeloadSource.EXPLICIT_REQUEST -> R.string.history_deload_requested
            DeloadSource.RETURNING -> R.string.history_deload_returning
            null -> R.string.history_deload_not_accepted
        })
    }
    reasons.ranking.forEach { reason ->
        item { workoutRankingNotice(listOf(reason))?.let { HistoryText(it) } }
    }
    if (reasons.unsupportedIdentities.isNotEmpty()) message(R.string.history_unsupported_details)
}

private fun LazyListScope.technicalDetails(detail: WorkoutHistoryDetail, reasons: HistoryReasons?) {
    reasons?.unsupportedIdentities?.forEach { identity ->
        item { HistoryText(stringResource(R.string.history_unsupported_identity, identity)) }
    }
    field(R.string.history_session_id) { detail.session.id }
    field(R.string.history_status) { stringResource(sessionStatusRes(detail.session.status)) }
    detail.session.exercises.sortedWith(compareBy({ it.orderIndex }, { it.id })).forEach { exercise ->
        field(R.string.history_exercise_id) { exercise.exerciseId }
        field(R.string.history_instance_id) { exercise.id }
        field(R.string.history_session_id) { exercise.sessionId }
        field(R.string.history_order) { count(exercise.orderIndex) }
        exercise.sets.sortedWith(compareBy({ it.setNumber }, { it.id })).forEach { set ->
            field(R.string.history_set_id) { set.id }
            field(R.string.history_instance_id) { set.workoutExerciseId }
            field(R.string.history_exercise_type) { stringResource(exerciseTypeRes(set.exerciseType)) }
        }
    }
    val record = detail.recommendation ?: return
    listOf(
        R.string.history_session_id to record.sessionId,
        R.string.history_validator to record.validatorVersion,
        R.string.history_estimator to record.durationEstimatorVersion,
        R.string.history_outcome to record.outcome,
        R.string.history_catalog to record.catalogVersion,
        R.string.history_review_policy to record.reviewPolicyVersion.toString(),
        R.string.history_training_policy to record.trainingPolicyVersion,
        R.string.history_ledger_policy to record.ledgerPolicyVersion,
        R.string.history_program_policy to record.programStatePolicyVersion,
        R.string.history_adaptation to record.adaptationState,
        R.string.history_week to record.weekStartEpochDay?.toString(),
        R.string.history_zone to record.timeZoneId,
        R.string.history_profile_revision to record.profileRevision.toString(),
        R.string.history_context to record.contextIdentity
    ).forEach { (label, value) -> field(label) { value ?: notRecorded() } }
    field(R.string.history_reviewed_path) { answer(record.reviewedPathEnabled) }
    field(R.string.history_recorded_at) { date(record.recordedAtEpochMillis) }
    record.reasonCodes.forEach { code -> field(R.string.history_raw_reason) { code } }
    reasons?.progression?.forEach { provenance -> field(R.string.history_digest) { provenance.baseConfigurationDigest } }
    reasons?.deload?.let { deload ->
        field(R.string.history_deload_revision) { deload.revision.toString() }
        field(R.string.history_deload_offer) { deload.acceptedOfferId ?: notRecorded() }
    }
    record.doseAccounting.forEach { dose ->
        item { HistoryHeading(stringResource(R.string.history_accounting, LocalExerciseVocabulary.current.muscle(dose.muscle))) }
        field(R.string.history_accounting_completed) { count(dose.completedSets) }
        field(R.string.history_accounting_proposed) { count(dose.proposedSets) }
        field(R.string.history_accounting_allowance) { dose.allowanceSets?.let { count(it) } ?: notRecorded() }
    }
}

private fun LazyListScope.field(@StringRes label: Int, value: @Composable () -> String) {
    item { HistoryField(label, value()) }
}
private fun LazyListScope.message(@StringRes text: Int) { item { HistoryText(stringResource(text)) } }
private fun LazyListScope.heading(@StringRes title: Int) { item { HistoryHeading(stringResource(title)) } }

@Composable private fun HistoryHeading(text: String) {
    HistoryText(text, Modifier.fillMaxWidth().padding(top = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleLarge)
}
@Composable private fun HistoryField(@StringRes label: Int, value: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        HistoryText(stringResource(label), Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge)
        HistoryText(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable private fun HistoryText(
    text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current
) {
    // App typography carries fixed dark-theme colors; respect the actual surface/button instead.
    Text(text, modifier, color = LocalContentColor.current, style = style)
}
@Composable private fun count(value: Int) = LocaleFormatting.formatCount(value, LocalConfiguration.current.locales[0])
@Composable private fun measurement(value: Double) = LocaleFormatting.formatMeasurement(value, LocalConfiguration.current.locales[0])
@Composable private fun notRecorded() = stringResource(R.string.effort_not_recorded)
@Composable private fun minutes(value: Int) = stringResource(R.string.prescription_minutes, count(value))
@Composable private fun seconds(value: Int) = stringResource(R.string.prescription_seconds, count(value))
@Composable private fun meters(value: Double) = stringResource(R.string.prescription_meters, measurement(value))
@Composable private fun weight(value: Double?, unit: WeightUnit): String =
    value?.let { stringResource(R.string.previous_load, measurement(it), unit.symbol) } ?: notRecorded()
@Composable private fun answer(value: Boolean?) = when (value) {
    true -> stringResource(R.string.answer_yes)
    false -> stringResource(R.string.answer_no)
    null -> notRecorded()
}
@Composable private fun date(timestamp: Long?): String = timestamp?.let {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, LocalConfiguration.current.locales[0]).format(Date(it))
} ?: notRecorded()

private fun sessionStatusRes(status: SessionStatus) = when (status) {
    SessionStatus.COMPLETED -> R.string.set_completed
    SessionStatus.CANCELLED -> R.string.history_cancelled
    SessionStatus.IN_PROGRESS -> R.string.history_in_progress
}
private fun exerciseTypeRes(type: ExerciseType) = when (type) {
    ExerciseType.WEIGHT_REPS -> R.string.history_weight_reps
    ExerciseType.BODYWEIGHT_REPS -> R.string.history_bodyweight
    ExerciseType.ASSISTED_BODYWEIGHT -> R.string.history_assisted
    ExerciseType.DURATION -> R.string.history_duration
    ExerciseType.DISTANCE_DURATION -> R.string.history_distance_duration
}
private fun setTypeRes(type: SetType) = when (type) {
    SetType.WARMUP -> R.string.history_warmup
    SetType.NORMAL -> R.string.history_normal
    SetType.DROPSET -> R.string.history_dropset
    SetType.MYOREP -> R.string.history_myorep
    SetType.FAILURE -> R.string.history_failure
}
private fun setStatusRes(set: WorkoutSet) = when {
    set.isCompleted -> R.string.set_completed
    set.stopReason == SetStopReason.USER_SKIPPED -> R.string.history_skipped
    set.stopReason != null -> R.string.history_stopped
    else -> R.string.history_unresolved
}
private fun violationRes(code: ProgramViolationCode) = when (code) {
    ProgramViolationCode.EMPTY_RECOMMENDATION -> R.string.history_violation_empty
    ProgramViolationCode.DURATION_OUT_OF_BOUNDS -> R.string.history_violation_duration_bounds
    ProgramViolationCode.BLANK_EXERCISE_ID, ProgramViolationCode.UNKNOWN_EXERCISE_ID -> R.string.history_violation_id
    ProgramViolationCode.NOT_IN_CANDIDATE_SET -> R.string.history_violation_candidate
    ProgramViolationCode.PRESCRIPTION_TYPE_MISMATCH, ProgramViolationCode.PRESCRIPTION_SHAPE_MISMATCH -> R.string.history_violation_type
    ProgramViolationCode.UNSUPPORTED_WORKOUT_FOCUS -> R.string.history_violation_focus
    ProgramViolationCode.DUPLICATE_EXERCISE_IN_SESSION, ProgramViolationCode.DUPLICATE_PROGRESSION_FAMILY -> R.string.history_violation_duplicate
    ProgramViolationCode.MISSING_REQUIRED_MOVEMENT_PATTERN -> R.string.history_violation_pattern
    ProgramViolationCode.EXPLICIT_CONSTRAINT_VIOLATED -> R.string.history_violation_constraint
    ProgramViolationCode.MISSING_APPROVED_METADATA -> R.string.history_violation_metadata
    ProgramViolationCode.REVIEW_POLICY_VERSION_MISMATCH -> R.string.history_violation_review
    ProgramViolationCode.UNTRACEABLE_LOAD -> R.string.history_violation_load
    ProgramViolationCode.PROGRESSION_POLICY_MISMATCH -> R.string.history_violation_progression
    ProgramViolationCode.DURATION_ESTIMATE_MISMATCH -> R.string.history_violation_estimate
    ProgramViolationCode.MALFORMED_WEEKLY_LEDGER, ProgramViolationCode.DOSE_ACCOUNTING_OVERFLOW -> R.string.history_violation_ledger
    ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED -> R.string.history_violation_allowance
}
