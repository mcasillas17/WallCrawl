package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetValuesDraft
import wallcrawl.elopenmike.com.core.model.asPerformanceInput
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreen
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite

/** One editable measurement of a set, with the step a gym-floor adjustment actually uses. */
enum class SetInputField(
    val label: String,
    val stepSize: Double,
    val isDecimal: Boolean,
    private val maximum: Double
) {
    LOAD("Load", stepSize = 2.5, isDecimal = true, maximum = 100_000.0),
    ASSISTANCE("Assist", stepSize = 2.5, isDecimal = true, maximum = 100_000.0),
    REPS("Reps", stepSize = 1.0, isDecimal = false, maximum = 1_000.0),
    DURATION("Seconds", stepSize = 5.0, isDecimal = false, maximum = 86_400.0),
    DISTANCE("Meters", stepSize = 50.0, isDecimal = true, maximum = 1_000_000.0);

    /**
     * One step up or down from [current], clamped to the range the repository accepts.
     *
     * With no current value the first step starts from [fallback] -- the planned target
     * for this set -- so the control offers what was prescribed instead of inventing a
     * value. With no target either, stepping up starts at one step.
     */
    fun stepped(current: Double?, increase: Boolean, fallback: Double? = null): Double {
        if (current == null && fallback != null && increase) return fallback.coerceIn(0.0, maximum)
        val base = current ?: 0.0
        val next = if (increase) base + stepSize else base - stepSize
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
 * a symptom, an injury, or advice.
 */
fun stopReasonLabel(reason: SetStopReason): String = when (reason) {
    SetStopReason.USER_SKIPPED -> "Skipped this set"
    SetStopReason.PAIN_STOP -> "Something hurt, so I stopped"
    SetStopReason.EQUIPMENT_UNAVAILABLE -> "Equipment wasn't available"
    SetStopReason.TIME_CONSTRAINT -> "Ran out of time"
    SetStopReason.OTHER -> "Another reason"
}

/** Remaining rest as minutes and seconds. */
fun restCountdownLabel(remainingSeconds: Int): String {
    val safeSeconds = remainingSeconds.coerceAtLeast(0)
    return "${safeSeconds / 60}:${(safeSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * The gym-floor logger for one set.
 *
 * Completion is a single large tap; every numeric outcome has plus/minus controls with a
 * text field beside them for precise entry; a previous comparable value can be copied in
 * one tap. Effort and the manageable confirmation are optional and never gate completion.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GymFloorSetRow(
    set: WorkoutSet,
    weightUnit: String,
    previousSet: WorkoutSet?,
    onValuesChanged: (SetValuesDraft) -> Unit,
    onCompletionChanged: (values: SetValuesDraft, completed: Boolean) -> Unit,
    onSkipSet: (SetStopReason) -> Unit,
    onRecordEffort: (rpe: Float?, rir: Int?) -> Unit,
    onRecordFeltManageable: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var reps by remember(set.id, set.completedReps) {
        mutableStateOf((set.completedReps ?: set.targetReps)?.toString().orEmpty())
    }
    var weight by remember(set.id, set.completedWeight) {
        mutableStateOf((set.completedWeight ?: set.targetWeight)?.compactText().orEmpty())
    }
    var assistance by remember(set.id, set.completedAssistanceWeight) {
        mutableStateOf(
            (set.completedAssistanceWeight ?: set.targetAssistanceWeight)?.compactText().orEmpty()
        )
    }
    var duration by remember(set.id, set.completedDurationSeconds) {
        mutableStateOf(
            (set.completedDurationSeconds ?: set.targetDurationSeconds)?.toString().orEmpty()
        )
    }
    var distance by remember(set.id, set.completedDistanceMeters) {
        mutableStateOf(
            (set.completedDistanceMeters ?: set.targetDistanceMeters)?.compactText().orEmpty()
        )
    }
    var showStopReasons by remember(set.id) { mutableStateOf(false) }
    var showFeedback by remember(set.id) { mutableStateOf(false) }

    fun draft() = SetValuesDraft(
        reps = reps.toIntOrNull(),
        weight = weight.toDoubleOrNull(),
        assistanceWeight = assistance.toDoubleOrNull(),
        durationSeconds = duration.toIntOrNull(),
        distanceMeters = distance.toDoubleOrNull()
    )

    fun textFor(field: SetInputField) = when (field) {
        SetInputField.LOAD -> weight
        SetInputField.ASSISTANCE -> assistance
        SetInputField.REPS -> reps
        SetInputField.DURATION -> duration
        SetInputField.DISTANCE -> distance
    }

    fun setText(field: SetInputField, value: String) {
        when (field) {
            SetInputField.LOAD -> weight = value
            SetInputField.ASSISTANCE -> assistance = value
            SetInputField.REPS -> reps = value
            SetInputField.DURATION -> duration = value
            SetInputField.DISTANCE -> distance = value
        }
    }

    fun targetFor(field: SetInputField): Double? = when (field) {
        SetInputField.LOAD -> set.targetWeight
        SetInputField.ASSISTANCE -> set.targetAssistanceWeight
        SetInputField.REPS -> set.targetReps?.toDouble()
        SetInputField.DURATION -> set.targetDurationSeconds?.toDouble()
        SetInputField.DISTANCE -> set.targetDistanceMeters
    }

    // Field edits preserve whatever outcome the set already has, so correcting a digit on
    // a completed set does not un-complete it. A transient in-progress value -- a field
    // momentarily cleared to retype a number -- is never submitted as a completion,
    // because the repository would reject it and a keystroke is not a failed write.
    fun submitEdit() {
        if (draft().asPerformanceInput(set.isCompleted).isSubmittableFor(set.exerciseType)) {
            onValuesChanged(draft())
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (set.isCompleted) {
                    SuccessGreen.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (set.isCompleted) {
                    SuccessGreen.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Set ${set.setNumber}",
                color = if (set.isCompleted) {
                    SuccessGreen
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            set.stopReason?.let { reason ->
                Text(
                    text = stopReasonLabel(reason),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SetInputField.forType(set.exerciseType).forEach { field ->
            SetValueRow(
                field = field,
                setNumber = set.setNumber,
                value = textFor(field),
                unitSuffix = if (field == SetInputField.LOAD || field == SetInputField.ASSISTANCE) {
                    weightUnit
                } else {
                    null
                },
                previousValue = field.previousValue(previousSet),
                onValueChange = { updated ->
                    setText(field, updated)
                    submitEdit()
                },
                onStep = { increase ->
                    val stepped = field.stepped(
                        current = textFor(field).toDoubleOrNull(),
                        increase = increase,
                        fallback = targetFor(field)
                    )
                    setText(field, stepped.compactText())
                    submitEdit()
                },
                onCopyPrevious = { previous ->
                    setText(field, previous.compactText())
                    submitEdit()
                }
            )
        }

        CompleteSetButton(
            setNumber = set.setNumber,
            isCompleted = set.isCompleted,
            onToggle = { completed -> onCompletionChanged(draft(), completed) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { showStopReasons = true },
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text("Skip or stop", fontSize = 13.sp)
            }
            TextButton(
                onClick = { showFeedback = !showFeedback },
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text(
                    text = if (showFeedback) "Hide feedback" else "Add feedback (optional)",
                    fontSize = 13.sp
                )
            }
        }

        if (set.isCompleted) {
            ManageableConfirmation(
                setNumber = set.setNumber,
                feltManageable = set.feltManageable,
                onRecord = onRecordFeltManageable
            )
        }

        if (showFeedback) {
            EffortControls(
                setNumber = set.setNumber,
                rpe = set.rpe,
                rir = set.rir,
                onRecordEffort = onRecordEffort
            )
        }
    }

    if (showStopReasons) {
        AlertDialog(
            onDismissRequest = { showStopReasons = false },
            title = { Text("Why are you stopping this set?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "This is only recorded so your log stays accurate.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SetStopReason.entries.forEach { reason ->
                        TextButton(
                            onClick = {
                                showStopReasons = false
                                onSkipSet(reason)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MIN_TOUCH_TARGET)
                        ) {
                            Text(
                                text = stopReasonLabel(reason),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStopReasons = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SetValueRow(
    field: SetInputField,
    setNumber: Int,
    value: String,
    unitSuffix: String?,
    previousValue: Double?,
    onValueChange: (String) -> Unit,
    onStep: (increase: Boolean) -> Unit,
    onCopyPrevious: (Double) -> Unit
) {
    val fieldName = if (unitSuffix != null) "${field.label} $unitSuffix" else field.label
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StepButton(
            increase = false,
            contentDescription = "Decrease ${field.label.lowercase()} for set $setNumber",
            onClick = { onStep(false) }
        )
        OutlinedTextField(
            value = value,
            onValueChange = { input -> if (input.length <= MAX_INPUT_LENGTH) onValueChange(input) },
            label = { Text(fieldName, fontSize = 11.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.isDecimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "$fieldName for set $setNumber" }
        )
        StepButton(
            increase = true,
            contentDescription = "Increase ${field.label.lowercase()} for set $setNumber",
            onClick = { onStep(true) }
        )
        if (previousValue != null) {
            AssistChip(
                onClick = { onCopyPrevious(previousValue) },
                label = { Text(previousValue.compactText(), fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                modifier = Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .semantics {
                        contentDescription =
                            "Use previous ${field.label.lowercase()} " +
                                "${previousValue.compactText()} for set $setNumber"
                    }
            )
        }
    }
}

@Composable
private fun StepButton(
    increase: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
    ) {
        Icon(
            imageVector = if (increase) Icons.Default.Add else Icons.Default.Remove,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CompleteSetButton(
    setNumber: Int,
    isCompleted: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LARGE_TOUCH_TARGET)
            .background(
                if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(12.dp)
            )
            .toggleable(
                value = isCompleted,
                role = Role.Checkbox,
                onValueChange = onToggle
            )
            .semantics { contentDescription = "Set $setNumber complete" },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isCompleted) TextWhite else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCompleted) "Completed" else "Complete set",
                color = if (isCompleted) TextWhite else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ManageableConfirmation(
    setNumber: Int,
    feltManageable: Boolean?,
    onRecord: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Felt manageable?",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        listOf(true to "Yes", false to "No").forEach { (answer, label) ->
            FilterChip(
                selected = feltManageable == answer,
                onClick = { onRecord(answer) },
                label = { Text(label) },
                modifier = Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .semantics {
                        contentDescription = "Set $setNumber felt manageable, $label"
                    }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffortControls(
    setNumber: Int,
    rpe: Float?,
    rir: Int?,
    onRecordEffort: (rpe: Float?, rir: Int?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Effort is optional. Leaving it blank is fine.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("RPE", fontSize = 13.sp, modifier = Modifier.width(36.dp))
            StepButton(
                increase = false,
                contentDescription = "Decrease RPE for set $setNumber",
                onClick = {
                    onRecordEffort(((rpe ?: 0f) - 0.5f).coerceIn(0f, 10f), rir)
                }
            )
            Text(
                text = rpe?.compactText() ?: "—",
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .width(40.dp)
                    .clearAndSetSemantics {
                        contentDescription = "RPE for set $setNumber, ${rpe?.compactText() ?: "not recorded"}"
                    }
            )
            StepButton(
                increase = true,
                contentDescription = "Increase RPE for set $setNumber",
                onClick = {
                    onRecordEffort(((rpe ?: 0f) + 0.5f).coerceIn(0f, 10f), rir)
                }
            )
            TextButton(
                onClick = { onRecordEffort(null, rir) },
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text("Clear", fontSize = 12.sp)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "RIR",
                fontSize = 13.sp,
                modifier = Modifier
                    .width(36.dp)
                    .heightIn(min = MIN_TOUCH_TARGET)
            )
            (0..5).forEach { value ->
                FilterChip(
                    selected = rir == value,
                    onClick = { onRecordEffort(rpe, if (rir == value) null else value) },
                    label = { Text("$value") },
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .semantics {
                            contentDescription = "RIR $value for set $setNumber"
                        }
                )
            }
        }
    }
}

private fun Double.compactText(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun Float.compactText(): String =
    if (this % 1.0f == 0.0f) toInt().toString() else toString()

private const val MAX_INPUT_LENGTH = 10
private val MIN_TOUCH_TARGET = 48.dp
private val LARGE_TOUCH_TARGET = 56.dp

/**
 * Labels the load field. A null target means no confirmed baseline and no usable
 * history exist yet, so the logger must ask the user to choose one rather than
 * silently accepting whatever value happens to be left in the field.
 */
internal fun weightInputLabel(targetWeight: Double?, weightUnit: String): String =
    if (targetWeight == null) "Choose starting load" else "Load $weightUnit"

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
