package wallcrawl.elopenmike.com.core.ui.components

import java.text.DecimalFormatSymbols
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetValuesDraft
import wallcrawl.elopenmike.com.core.model.asPerformanceInput
import wallcrawl.elopenmike.com.core.ai.FirstWeightGuidance
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.labelRes

/** One editable measurement of a set, with the step a gym-floor adjustment actually uses. */
enum class SetInputField(
    @StringRes val labelRes: Int,
    val increments: List<Double>,
    val isDecimal: Boolean,
    val maximum: Double
) {
    LOAD(R.string.set_field_load, increments = listOf(2.5, 1.0, 0.1, 0.01), isDecimal = true, maximum = 100_000.0),
    ASSISTANCE(R.string.set_field_assist, increments = listOf(2.5, 1.0, 0.1, 0.01), isDecimal = true, maximum = 100_000.0),
    REPS(R.string.set_field_reps, increments = listOf(1.0), isDecimal = false, maximum = 1_000.0),
    DURATION(R.string.set_field_seconds, increments = listOf(5.0, 1.0), isDecimal = false, maximum = 86_400.0),
    DISTANCE(R.string.set_field_meters, increments = listOf(50.0, 1.0, 0.1, 0.01), isDecimal = true, maximum = 1_000_000.0);

    val stepSize: Double get() = increments.first()

    /**
     * One step up or down from [current], clamped to the range the repository accepts.
     *
     * With no current value the first step starts from [fallback] -- the planned target
     * for this set -- so the control offers what was prescribed instead of inventing a
     * value. With no target either, stepping up starts at one step.
     */
    fun stepped(current: Double?, increase: Boolean, fallback: Double? = null, increment: Double = stepSize): Double {
        if (current == null && fallback != null && increase) return fallback.coerceIn(0.0, maximum)
        val base = current ?: 0.0
        val next = (base.toBigDecimal() + (if (increase) increment else -increment).toBigDecimal()).toDouble()
        return next.coerceIn(0.0, maximum)
    }

    /** The value this field recorded in a previous comparable set, if it completed one. */
    fun previousValue(previousSet: WorkoutSet?): Double? {
        val set = previousSet?.takeIf { it.isCompleted } ?: return null
        return when (this) {
            LOAD -> set.completedWeight
            ASSISTANCE -> set.completedAssistanceWeight
            REPS -> set.completedReps?.toDouble()
            DURATION -> set.completedDurationSeconds?.toDouble()
            DISTANCE -> set.completedDistanceMeters
        }
    }

    companion object {
        /** Only the measurements this exercise type supports, in logging order. */
        fun forType(exerciseType: ExerciseType): List<SetInputField> = when (exerciseType) {
            ExerciseType.WEIGHT_REPS -> listOf(LOAD, REPS)
            ExerciseType.BODYWEIGHT_REPS -> listOf(REPS)
            ExerciseType.ASSISTED_BODYWEIGHT -> listOf(ASSISTANCE, REPS)
            ExerciseType.DURATION -> listOf(DURATION)
            ExerciseType.DISTANCE_DURATION -> listOf(DISTANCE, DURATION)
        }
    }
}

/**
 * Plain, non-diagnostic wording for a typed stop reason.
 *
 * [SetStopReason.PAIN_STOP] says only that the user chose to stop; it is never phrased as
 * a symptom, an injury, or advice, in any language.
 */
@Composable
fun stopReasonLabel(reason: SetStopReason): String = stringResource(reason.labelRes)

/** Remaining rest as minutes and seconds, with the reader's digits. */
@Composable
fun restCountdownLabel(remainingSeconds: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val (minutes, seconds) = LocaleFormatting.countdownParts(remainingSeconds, locale)
    return stringResource(R.string.rest_countdown, minutes, seconds)
}

private fun SetInputField.target(set: WorkoutSet): Double? = when (this) {
    SetInputField.LOAD -> set.targetWeight
    SetInputField.ASSISTANCE -> set.targetAssistanceWeight
    SetInputField.REPS -> set.targetReps?.toDouble()
    SetInputField.DURATION -> set.targetDurationSeconds?.toDouble()
    SetInputField.DISTANCE -> set.targetDistanceMeters
}

private fun SetInputField.recorded(set: WorkoutSet): Double? = when (this) {
    SetInputField.LOAD -> set.completedWeight
    SetInputField.ASSISTANCE -> set.completedAssistanceWeight
    SetInputField.REPS -> set.completedReps?.toDouble()
    SetInputField.DURATION -> set.completedDurationSeconds?.toDouble()
    SetInputField.DISTANCE -> set.completedDistanceMeters
}

internal fun exactMeasurement(value: Double, decimal: Char): String =
    value.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', decimal)

@Composable
private fun countRange(min: Int, max: Int, locale: java.util.Locale): String {
    val first = LocaleFormatting.formatCount(min, locale)
    return if (min == max) first else stringResource(R.string.prescription_rep_range, first, LocaleFormatting.formatCount(max, locale))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GymFloorSetRow(
    set: WorkoutSet,
    weightUnit: String,
    previousSet: WorkoutSet?,
    onCompletionChanged: (values: SetValuesDraft, completed: Boolean) -> Unit,
    onSkipSet: (SetStopReason, SetValuesDraft) -> Unit,
    onRecordEffort: (rpe: Float?, rir: Int?) -> Unit,
    onRecordFeltManageable: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
    precedingWorkSet: WorkoutSet? = null,
    firstWeightGuidance: FirstWeightGuidance = FirstWeightGuidance(),
    prescription: ExercisePrescription? = null
) {
    val locale = LocalConfiguration.current.locales[0]
    val decimal = DecimalFormatSymbols.getInstance(locale).decimalSeparator.takeIf { it == ',' } ?: '.'
    val fields = SetInputField.forType(set.exerciseType)
    val setNumber = LocaleFormatting.formatCount(set.setNumber, locale)
    // Only overrides are saved. Untouched legacy/null measurements follow the recorded row.
    var edits by rememberSaveable(set.id) { mutableStateOf(hashMapOf<String, String>()) }
    var showStopReasons by rememberSaveable(set.id) { mutableStateOf(false) }
    var showFeedback by rememberSaveable(set.id) { mutableStateOf(false) }
    var useReferenceRack by rememberSaveable(set.id, firstWeightGuidance.reference?.sourceSetId,
        firstWeightGuidance.reference?.weight, firstWeightGuidance.reference?.unit) { mutableStateOf(false) }
    var manualWeight by rememberSaveable(set.id) { mutableStateOf(false) }
    var submitted by rememberSaveable(set.id) { mutableStateOf(false) }

    val carriedLoad = precedingWorkSet?.completedWeight?.takeIf {
        !set.isResolved && set.exerciseType == ExerciseType.WEIGHT_REPS && set.targetWeight == null &&
            it.isFinite() && it > 0.0 && it <= SetInputField.LOAD.maximum
    }
    fun initial(field: SetInputField) = field.recorded(set) ?: if (set.isResolved) null
        else field.target(set) ?: carriedLoad.takeIf { field == SetInputField.LOAD }
    fun textFor(field: SetInputField) = edits[field.name]?.replace('.', decimal)?.replace(',', decimal)
        ?: initial(field)?.let { exactMeasurement(it, decimal) }.orEmpty()
    fun parsed(field: SetInputField): Double? {
        val text = edits[field.name] ?: return initial(field)
        return if (field.isDecimal) LocaleFormatting.parseExactMeasurement(text) else LocaleFormatting.parseIntInput(text)?.toDouble()
    }
    fun edit(field: SetInputField, value: String) {
        submitted = false
        edits = HashMap(edits).apply { put(field.name, value) }
    }
    fun draft(partial: Boolean = false): SetValuesDraft {
        fun number(field: SetInputField): Double? {
            if (field !in fields) return null
            if (partial && field.name !in edits) return field.recorded(set)
            val value = parsed(field)
            if (partial && !textFor(field).isBlank() && (value == null || value !in 0.0..field.maximum)) {
                return field.recorded(set)
            }
            return value?.takeIf { it in 0.0..field.maximum }
        }
        return SetValuesDraft(
            reps = number(SetInputField.REPS)?.toInt(), weight = number(SetInputField.LOAD),
            assistanceWeight = number(SetInputField.ASSISTANCE),
            durationSeconds = number(SetInputField.DURATION)?.toInt(),
            distanceMeters = number(SetInputField.DISTANCE)
        )
    }
    val error = fields.firstNotNullOfOrNull { field ->
        val text = textFor(field)
        val value = parsed(field)
        when {
            text.isBlank() -> null
            value == null -> stringResource(R.string.set_field_incomplete_number)
            value > field.maximum -> stringResource(R.string.guided_set_maximum,
                stringResource(field.labelRes), LocaleFormatting.formatCount(field.maximum.toInt(), locale))
            else -> null
        }
    } ?: if (!draft().asPerformanceInput(true).isSubmittableFor(set.exerciseType)) {
        stringResource(when (set.exerciseType) {
            ExerciseType.WEIGHT_REPS -> R.string.guided_set_need_load_reps
            ExerciseType.BODYWEIGHT_REPS, ExerciseType.ASSISTED_BODYWEIGHT -> R.string.guided_set_need_reps
            ExerciseType.DURATION -> R.string.guided_set_need_duration
            ExerciseType.DISTANCE_DURATION -> R.string.guided_set_need_distance_duration
        })
    } else null

    LaunchedEffect(set, submitted, saving) {
        if (submitted && !saving && set.isCompleted && fields.all { parsed(it) == it.recorded(set) }) {
            edits = hashMapOf()
            submitted = false
        }
    }

    val needsStartingWeight = set.exerciseType == ExerciseType.WEIGHT_REPS && !set.isResolved &&
        textFor(SetInputField.LOAD).isBlank()
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val targetLoad = if (set.exerciseType == ExerciseType.ASSISTED_BODYWEIGHT) set.targetAssistanceWeight else set.targetWeight
        if (!set.isResolved && targetLoad != null) {
            Text(stringResource(if (set.exerciseType == ExerciseType.ASSISTED_BODYWEIGHT) R.string.workout_assistance
                else R.string.guided_planned_weight, exactMeasurement(targetLoad, decimal), weightUnit),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (needsStartingWeight) {
            val title = if (firstWeightGuidance.perDumbbell) R.string.guided_choose_per_dumbbell else R.string.guided_choose_starting_weight
            Text(stringResource(title, weightUnit), style = MaterialTheme.typography.titleMedium)
            val range = prescription?.repRange
            val effort = prescription?.effortTarget
            if (range != null) {
                val reps = countRange(range.min, range.max, locale)
                val cue = if (effort == null) stringResource(R.string.guided_weight_for_reps, reps)
                    else stringResource(R.string.guided_weight_for_effort, reps, countRange(effort.minRir, effort.maxRir, locale))
                Text(cue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            firstWeightGuidance.reference?.let { ref ->
                Text(stringResource(R.string.guided_other_lift_logged,
                    LocalExerciseVocabulary.current.exerciseName(ref.exerciseId),
                    LocaleFormatting.formatMeasurement(ref.weight, locale), ref.unit.symbol,
                    LocaleFormatting.formatCount(ref.reps, locale)), style = MaterialTheme.typography.bodyMedium)
                if (!useReferenceRack && firstWeightGuidance.confirmedReferenceChoices.isNotEmpty()) {
                    TextButton(onClick = { useReferenceRack = true; manualWeight = false }, enabled = !saving) {
                        Text(stringResource(R.string.guided_confirm_per_dumbbell))
                    }
                }
            }
        } else if (carriedLoad != null && SetInputField.LOAD.name !in edits) {
            Text(stringResource(R.string.guided_weight_from_set, LocaleFormatting.formatCount(precedingWorkSet.setNumber, locale)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        set.stopReason?.let { Text(stopReasonLabel(it), style = MaterialTheme.typography.labelLarge) }
        fields.forEach { field ->
            val label = stringResource(field.labelRes)
            val fieldName = when {
                field == SetInputField.LOAD && firstWeightGuidance.perDumbbell && set.targetWeight == null ->
                    stringResource(R.string.rack_weight_label, weightUnit)
                field == SetInputField.LOAD || field == SetInputField.ASSISTANCE ->
                    stringResource(R.string.set_field_with_unit, label, weightUnit)
                else -> label
            }
            val rackChoices = if (useReferenceRack && firstWeightGuidance.confirmedReferenceChoices.isNotEmpty())
                firstWeightGuidance.confirmedReferenceChoices else firstWeightGuidance.rackChoices
            if (field == SetInputField.LOAD && needsStartingWeight && rackChoices.isNotEmpty() && !manualWeight) {
                Text(stringResource(R.string.guided_from_your_rack), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rackChoices.forEach { amount ->
                        val number = exactMeasurement(amount, decimal)
                        val choose = stringResource(R.string.guided_choose_weight, number, weightUnit)
                        Box(Modifier.weight(1f).sizeIn(minWidth = 48.dp, minHeight = MIN_TOUCH_TARGET)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .clickable(enabled = !saving, role = Role.Button) { edit(field, number) }
                            .semantics { contentDescription = choose }, contentAlignment = Alignment.Center) {
                            // The description already says the weight and its unit.
                            Text(number, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    TextButton(onClick = { manualWeight = true }, enabled = !saving) {
                        Text(stringResource(R.string.guided_other_weight))
                    }
                }
                return@forEach
            }
            val description = stringResource(R.string.set_field_value_accessibility, fieldName, setNumber)
            var stepIndex by rememberSaveable(set.id, field) { mutableStateOf(0) }
            val increment = field.increments[stepIndex]

            fun step(increase: Boolean) {
                val current = parsed(field)
                val next = field.stepped(current, increase, field.target(set), increment)
                val optional = field == SetInputField.ASSISTANCE || set.exerciseType == ExerciseType.DISTANCE_DURATION
                val clear = optional && !increase && (current == null || current == 0.0)
                val value = if (clear) "" else exactMeasurement(next, decimal)
                if (value != textFor(field)) edit(field, value)
            }

            val labelAbove = isLargeText()
            @Composable
            fun FieldLabel(labelModifier: Modifier) {
                if (field.increments.size > 1) {
                    val amount = exactMeasurement(increment, decimal)
                    val adjustDescription = stringResource(R.string.guided_change_step, fieldName, amount)
                    Column(
                        labelModifier.heightIn(min = MIN_TOUCH_TARGET)
                            .clickable(enabled = !saving, role = Role.Button) {
                                stepIndex = (stepIndex + 1) % field.increments.size
                            }.semantics { contentDescription = adjustDescription },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(fieldName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("±$amount", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(fieldName, labelModifier, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (labelAbove) FieldLabel(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!labelAbove) FieldLabel(Modifier.weight(1f))
                StepButton(false, stringResource(R.string.set_field_decrease_accessibility, label, setNumber),
                    { step(false) }, enabled = !saving, repeatOnHold = (parsed(field) ?: 0.0) > 0.0)
                val shown = textFor(field).ifBlank { stringResource(R.string.value_not_available) }
                Box(Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET)
                    // The description names the field and replaces the visible text, so the value is spoken through stateDescription.
                    .semantics(mergeDescendants = true) { contentDescription = description; stateDescription = shown },
                    contentAlignment = Alignment.Center) {
                    Text(shown,
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                StepButton(true, stringResource(R.string.set_field_increase_accessibility, label, setNumber),
                    { step(true) }, enabled = !saving, repeatOnHold = parsed(field) != field.maximum)
                field.previousValue(previousSet)?.let { previous ->
                    val copy = stringResource(R.string.set_copy_previous_accessibility, label,
                        exactMeasurement(previous, decimal), setNumber)
                    IconButton(enabled = !saving, onClick = { edit(field, exactMeasurement(previous, decimal)) }) {
                        Icon(Icons.Default.ContentCopy, copy, Modifier.size(18.dp))
                    }
                }
            }
        }
        if (error != null && !needsStartingWeight) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        WallCrawlPrimaryButton(
            text = stringResource(if (set.isCompleted) R.string.guided_save_changes else R.string.guided_log_set),
            onClick = { submitted = true; onCompletionChanged(draft(), true) },
            enabled = !saving && error == null
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(enabled = !saving, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { showStopReasons = true }) {
                Text(stringResource(R.string.set_skip_or_stop))
            }
            if (set.isResolved) {
                TextButton(enabled = !saving, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.a11yExpanded(showFeedback),
                    onClick = { showFeedback = !showFeedback }) {
                    Text(stringResource(if (showFeedback) R.string.set_feedback_hide else R.string.set_feedback_show))
                }
                TextButton(enabled = !saving, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { onCompletionChanged(draft(partial = true), false) }) {
                    Text(stringResource(R.string.guided_mark_unlogged))
                }
            }
        }
        if (set.isResolved && showFeedback) {
            if (set.isCompleted) ManageableConfirmation(setNumber, set.feltManageable, !saving, onRecordFeltManageable)
            EffortControls(setNumber, set.rpe, set.rir, !saving, onRecordEffort)
        }
    }

    if (showStopReasons) {
        AlertDialog(
            onDismissRequest = { showStopReasons = false },
            title = {
                Text(
                    stringResource(R.string.stop_reason_title),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.stop_reason_body),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SetStopReason.entries.forEach { reason ->
                        TextButton(
                            enabled = !saving,
                            onClick = {
                                showStopReasons = false
                                onSkipSet(reason, draft(partial = true))
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MIN_TOUCH_TARGET)
                        ) {
                            Text(
                                text = stopReasonLabel(reason),
                                color = LocalContentColor.current,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStopReasons = false }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepButton(
    increase: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    repeatOnHold: Boolean = false
) {
    val latestClick by rememberUpdatedState(onClick)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
    ) {
        val gesture = if (repeatOnHold && enabled) Modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                coroutineScope {
                    var repeated = false
                    val repeater = launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        repeated = true
                        while (isActive) {
                            latestClick()
                            delay(90L)
                        }
                    }
                    try {
                        if (tryAwaitRelease() && !repeated) latestClick()
                    } finally {
                        repeater.cancel()
                    }
                }
            })
        } else Modifier
        Box(Modifier.size(MIN_TOUCH_TARGET).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).then(gesture), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (increase) Icons.Default.Add else Icons.Default.Remove,
                contentDescription = contentDescription,
                tint = LocalContentColor.current
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManageableConfirmation(
    setNumber: String,
    feltManageable: Boolean?,
    enabled: Boolean,
    onRecord: (Boolean) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.set_felt_manageable),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf(true to R.string.answer_yes, false to R.string.answer_no).forEach { (answer, labelRes) ->
            val label = stringResource(labelRes)
            val description = stringResource(
                R.string.set_manageable_accessibility,
                setNumber,
                label
            )
            FilterChip(
                selected = feltManageable == answer,
                enabled = enabled,
                onClick = { onRecord(answer) },
                colors = setChipColors(),
                label = { Text(label) },
                modifier = Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .semantics {
                        contentDescription = description
                        role = Role.RadioButton
                    }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffortControls(
    setNumber: String,
    rpe: Float?,
    rir: Int?,
    enabled: Boolean,
    onRecordEffort: (rpe: Float?, rir: Int?) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val rpeText = rpe?.let { LocaleFormatting.formatMeasurement(it.toDouble(), locale) }
    val rpeAnnouncement = stringResource(
        R.string.effort_rpe_value_accessibility,
        setNumber,
        rpeText ?: stringResource(R.string.effort_not_recorded)
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.effort_optional),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.effort_rpe),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 36.dp)
            )
            StepButton(
                enabled = enabled,
                increase = false,
                contentDescription = stringResource(
                    R.string.effort_rpe_decrease_accessibility,
                    setNumber
                ),
                onClick = {
                    onRecordEffort(((rpe ?: 0f) - 0.5f).coerceIn(0f, 10f), rir)
                }
            )
            Text(
                text = rpeText ?: stringResource(R.string.value_not_available),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .clearAndSetSemantics { contentDescription = rpeAnnouncement }
            )
            StepButton(
                enabled = enabled,
                increase = true,
                contentDescription = stringResource(
                    R.string.effort_rpe_increase_accessibility,
                    setNumber
                ),
                onClick = {
                    onRecordEffort(((rpe ?: 0f) + 0.5f).coerceIn(0f, 10f), rir)
                }
            )
            TextButton(
                onClick = { onRecordEffort(null, rir) },
                enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text(
                    stringResource(R.string.action_clear),
                    fontSize = 12.sp
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.effort_rir),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(min = 36.dp)
                    .heightIn(min = MIN_TOUCH_TARGET)
            )
            // Checkboxes, not radio buttons: a second tap on the chosen value clears it.
            (0..5).forEach { value ->
                val valueText = LocaleFormatting.formatCount(value, locale)
                val description = stringResource(
                    R.string.effort_rir_accessibility,
                    valueText,
                    setNumber
                )
                FilterChip(
                    selected = rir == value,
                    enabled = enabled,
                    onClick = { onRecordEffort(rpe, if (rir == value) null else value) },
                    colors = setChipColors(),
                    label = { Text(valueText) },
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .semantics { contentDescription = description }
                )
            }
        }
    }
}

/** Material's selected fill is the row's own colour here, so the recorded value needs a stronger one. */
@Composable
internal fun setChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.secondary,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
)

internal val MIN_TOUCH_TARGET = 48.dp
/**
 * Whether this performance update is safe to submit to the repository as-is. A set
 * marked complete must carry valid, positive values for whatever this exercise type
 * requires -- mirroring [wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository]'s
 * completion invariant -- so a mid-edit value (e.g. a field momentarily cleared to
 * retype a number) is never sent as a rejected completion. An incomplete set is always
 * submittable, since a partial edit is allowed to persist regardless of its contents.
 */
internal fun SetPerformanceInput.isSubmittableFor(exerciseType: ExerciseType): Boolean {
    if (!isCompleted) return true
    val hasPositiveReps = (reps ?: 0) > 0
    return when (exerciseType) {
        ExerciseType.WEIGHT_REPS -> hasPositiveReps && (weight ?: 0.0) > 0.0
        ExerciseType.BODYWEIGHT_REPS, ExerciseType.ASSISTED_BODYWEIGHT -> hasPositiveReps
        ExerciseType.DURATION -> (durationSeconds ?: 0) > 0
        ExerciseType.DISTANCE_DURATION ->
            (durationSeconds ?: 0) > 0 || (distanceMeters ?: 0.0) > 0.0
    }
}
