package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.ConditionUiState

@Composable
fun ConditionText.localized(): String = if (LocalConfiguration.current.locales[0].language == "es") es else en

@Composable
fun ConditionSelectionControls(
    records: List<ConditionSupportRecord>, selections: List<SelectedCondition>,
    onChange: ((List<SelectedCondition>) -> Unit)?, enabled: Boolean = true
) {
    if (records.isEmpty() && selections.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConditionLabel(stringResource(R.string.condition_title), style = MaterialTheme.typography.titleMedium)
        ConditionLabel(stringResource(R.string.condition_selection_hint))
        selections.forEach { selection ->
            val record = records.singleOrNull { it.conditionId == selection.conditionId && it.contextSchemaVersion == selection.contextSchemaVersion }
            ConditionLabel(record?.label?.localized() ?: stringResource(R.string.condition_unsupported, selection.conditionId, selection.contextSchemaVersion))
            if (record == null) {
                ConditionAnswers(selection.answers)
            } else {
                ConditionLabel(record.instructions.localized())
                if (onChange != null) ConditionInputs(record.inputs, selection.answers, enabled) { answers ->
                    onChange(selections.map { if (it.conditionId == selection.conditionId)
                        SelectedCondition(it.conditionId, it.contextSchemaVersion, answers) else it })
                } else ConditionAnswers(selection.answers, record.inputs + record.responsePolicy.inputs)
            }
            if (onChange != null) TextButton(enabled = enabled, onClick = { onChange(selections.filterNot { it.conditionId == selection.conditionId }) }) {
                ConditionLabel(stringResource(R.string.condition_remove, record?.label?.localized() ?: selection.conditionId))
            }
        }
        if (onChange != null && selections.size < 32) records.filter { record -> selections.none { it.conditionId == record.conditionId } }.forEach { record ->
            TextButton(enabled = enabled, onClick = { onChange(selections + SelectedCondition(record.conditionId, record.contextSchemaVersion, emptyMap())) }) {
                ConditionLabel(stringResource(R.string.condition_add, record.label.localized()))
            }
        }
    }
}

@Composable
fun ConditionInputs(inputs: List<ConditionInputDefinition>, answers: Map<String, String?>, enabled: Boolean,
    onChange: (Map<String, String?>) -> Unit) {
    if (answers.size >= SelectedCondition.MAX_ANSWERS && inputs.any { it.id !in answers }) {
        ConditionLabel(stringResource(R.string.condition_answer_limit), color = MaterialTheme.colorScheme.error)
    }
    inputs.forEach { input ->
        val inputEnabled = enabled && (input.id in answers || answers.size < SelectedCondition.MAX_ANSWERS)
        ConditionLabel(if (input.required) stringResource(R.string.condition_required_prompt, input.prompt.localized()) else input.prompt.localized(), style = MaterialTheme.typography.titleSmall)
        val value = answers[input.id]
        if (value != null && value !in input.choices) ConditionLabel(stringResource(R.string.condition_unknown_answer, input.id, value))
        (listOf<String?>(null) + input.choices.keys).forEach { token ->
            val label = token?.let { input.choices.getValue(it).localized() } ?: stringResource(R.string.condition_unknown)
            Row(Modifier.fillMaxWidth().selectable(selected = value == token, enabled = inputEnabled, role = Role.RadioButton) { onChange(answers + (input.id to token)) }
                .padding(vertical = 4.dp)) {
                RadioButton(selected = value == token, onClick = null, enabled = inputEnabled)
                ConditionLabel(label, Modifier.padding(start = 8.dp, top = 12.dp))
            }
        }
    }
    val unknownKeys = answers.filterKeys { key -> inputs.none { it.id == key } }
    if (unknownKeys.isNotEmpty()) ConditionAnswers(unknownKeys)
}

@Composable
fun ConditionAnswers(answers: Map<String, String?>, definitions: List<ConditionInputDefinition> = emptyList()) {
    answers.toSortedMap().forEach { (key, value) ->
        val definition = definitions.firstOrNull { it.id == key }
        ConditionLabel(stringResource(R.string.condition_recorded_answer, definition?.prompt?.localized() ?: key,
            value?.let { definition?.choices?.get(it)?.localized() ?: it } ?: stringResource(R.string.condition_unknown)))
    }
}

@Composable
fun ConditionRefusal(reasons: List<ConditionViolation>) {
    reasons.forEach { reason ->
        val message = when (reason.reason) {
            "pending-reassessment", "response-requires-reassessment" -> R.string.condition_pending_hint
            "active-workout" -> R.string.condition_active_refusal
            "stale-event", "stale-profile" -> R.string.condition_stale_refusal
            "changed-condition-facts" -> R.string.condition_workout_changed
            "missing-context", "unknown-answer", "unresolved-case" -> R.string.condition_answers_refusal
            "unsupported-condition", "unsupported-policy-or-context", "support-unavailable", "missing-support" -> R.string.condition_support_unavailable
            else -> R.string.condition_start_refusal
        }
        ConditionLabel(stringResource(message), color = MaterialTheme.colorScheme.error)
        if (reason.conditionIds.isNotEmpty()) ConditionLabel(reason.conditionIds.sorted().joinToString(", "))
        if (reason.exerciseIds.isNotEmpty()) ConditionLabel(reason.exerciseIds.sorted().joinToString(", "))
    }
}

@Composable
fun PendingConditionControls(state: ConditionUiState, selections: List<SelectedCondition>,
    onAnswers: (String, Map<String, String?>) -> Unit, onSubmit: (PendingConditionReassessment) -> Unit) {
    if (state.pendingFailed) ConditionLabel(stringResource(R.string.condition_pending_read_error), color = MaterialTheme.colorScheme.error)
    state.pending.orEmpty().forEach { event ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConditionLabel(stringResource(R.string.condition_pending_title), style = MaterialTheme.typography.titleMedium)
            ConditionLabel(stringResource(R.string.condition_pending_hint))
            ConditionLabel(stringResource(R.string.condition_pending_source, event.conditionId, event.exerciseId))
            val selected = selections.singleOrNull { it.conditionId == event.conditionId }
            val record = state.records.singleOrNull { it.conditionId == event.conditionId &&
                it.responsePolicy.version == event.responsePolicyVersion &&
                (selected == null || selected.contextSchemaVersion == it.contextSchemaVersion) &&
                it.cases.any { case -> case.rules.any { rule -> rule.exerciseIdentity.exerciseId == event.exerciseId } } }
            if (record == null) ConditionLabel(stringResource(R.string.condition_support_unavailable)) else {
                ConditionLabel(record.responsePolicy.escalation.localized())
                val answers = if (state.reassessmentEventId == event.eventId) state.reassessmentAnswers else emptyMap()
                ConditionInputs((record.inputs + record.responsePolicy.inputs).distinctBy { it.id }, answers, !state.saving) {
                    onAnswers(event.eventId, it)
                }
                Button(enabled = !state.saving && state.reassessmentEventId == event.eventId, onClick = { onSubmit(event) }) {
                    ConditionLabel(stringResource(R.string.condition_reassess))
                }
            }
            if (state.reassessmentAnswerLimitEventId == event.eventId) {
                ConditionLabel(stringResource(R.string.condition_answer_limit), color = MaterialTheme.colorScheme.error)
            }
            state.reassessmentRefusal?.takeIf { it.expectedEventId == event.eventId }?.let { ConditionRefusal(listOf(it.reason)) }
        }
    }
    if (state.reassessmentRefusal != null && state.pending.orEmpty().none { it.eventId == state.reassessmentRefusal.expectedEventId }) {
        ConditionRefusal(listOf(state.reassessmentRefusal.reason))
    }
}

@Composable
fun ConditionExecution(context: SessionConditionContext?, exerciseId: String, purpose: PrescriptionPurpose) {
    if (purpose == PrescriptionPurpose.SUPPORTIVE) ConditionLabel(stringResource(R.string.condition_supportive), style = MaterialTheme.typography.labelLarge)
    context?.appliedRules?.filter { it.exerciseIdentity.exerciseId == exerciseId && it.purpose == purpose }?.forEach { rule ->
        ConditionLabel(rule.explanation.localized())
        ConditionLabel(stringResource(R.string.condition_execution_fact, rule.reference.conditionId, rule.reference.supportVersion, rule.executionIdentity, rule.reference.policyVersion))
    }
    context?.selectedConditions?.filter { selection -> context.appliedRules.any {
        it.exerciseIdentity.exerciseId == exerciseId && it.reference.conditionId == selection.conditionId
    } }?.forEach { ConditionAnswers(it.answers) }
}

@Composable
fun RecordedConditions(context: SessionConditionContext) {
    ConditionLabel(stringResource(R.string.condition_recorded_title), style = MaterialTheme.typography.titleMedium)
    ConditionLabel(stringResource(R.string.condition_recorded_hint))
    context.selectedConditions.forEach {
        ConditionLabel(stringResource(R.string.condition_recorded_identity, it.conditionId, it.contextSchemaVersion,
            context.sourceSupportVersions[it.conditionId] ?: stringResource(R.string.condition_unknown)))
        ConditionAnswers(it.answers)
    }
    if (context.formatVersion != 1 || context.policyVersion != 1) ConditionLabel(stringResource(R.string.condition_recorded_version, context.formatVersion, context.policyVersion))
}

@Composable
private fun ConditionLabel(text: String, modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current) {
    Text(text, modifier = modifier, color = color, style = style)
}
