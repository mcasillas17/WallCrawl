package wallcrawl.elopenmike.com.core.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetValuesDraft
import wallcrawl.elopenmike.com.core.model.asPerformanceInput
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreen
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreenDeep
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite

/** One editable measurement of a set, with the step a gym-floor adjustment actually uses. */
enum class SetInputField(
    @StringRes val labelRes: Int,
    val stepSize: Double,
    val isDecimal: Boolean,
    private val maximum: Double
) {
    LOAD(R.string.set_field_load, stepSize = 2.5, isDecimal = true, maximum = 100_000.0),
    ASSISTANCE(R.string.set_field_assist, stepSize = 2.5, isDecimal = true, maximum = 100_000.0),
    REPS(R.string.set_field_reps, stepSize = 1.0, isDecimal = false, maximum = 1_000.0),
    DURATION(R.string.set_field_seconds, stepSize = 5.0, isDecimal = false, maximum = 86_400.0),
    DISTANCE(R.string.set_field_meters, stepSize = 50.0, isDecimal = true, maximum = 1_000_000.0);

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
 * a symptom, an injury, or advice, in any language.
 */
@Composable
fun stopReasonLabel(reason: SetStopReason): String = stringResource(reason.labelRes)

/**
 * Success accent that stays readable on whichever surface the current theme paints.
 * [SuccessGreen] has enough contrast on a dark card but not on a light one.
 */
@Composable
private fun completedAccent(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) SuccessGreenDeep else SuccessGreen

/** Remaining rest as minutes and seconds, with the reader's digits. */
@Composable
fun restCountdownLabel(remainingSeconds: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val (minutes, seconds) = LocaleFormatting.countdownParts(remainingSeconds, locale)
    return stringResource(R.string.rest_countdown, minutes, seconds)
}

/**
 * The gym-floor logger for one set.
 *
 * Completion is a single large tap; every numeric outcome has plus/minus controls with a
 * text field beside them for precise entry; a previous comparable value can be copied in
 * one tap. Effort and the manageable confirmation are optional and never gate completion.
 *
 * Values are shown with the reader's decimal mark and read back through
 * [LocaleFormatting.parseDecimalInput], so a load typed as "2,5" is stored as 2.5 rather
 * than 25 and the stored number is identical whichever language the app is in.
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
    val locale = LocalConfiguration.current.locales[0]
    var reps by remember(set.id, set.completedReps, locale) {
        mutableStateOf(
            (set.completedReps ?: set.targetReps)
                ?.let { LocaleFormatting.formatEditableInt(it, locale) }
                .orEmpty()
        )
    }
    var weight by remember(set.id, set.completedWeight, locale) {
        mutableStateOf(
            (set.completedWeight ?: set.targetWeight)
                ?.let { LocaleFormatting.formatEditableDecimal(it, locale) }
                .orEmpty()
        )
    }
    var assistance by remember(set.id, set.completedAssistanceWeight, locale) {
        mutableStateOf(
            (set.completedAssistanceWeight ?: set.targetAssistanceWeight)
                ?.let { LocaleFormatting.formatEditableDecimal(it, locale) }
                .orEmpty()
        )
    }
    var duration by remember(set.id, set.completedDurationSeconds, locale) {
        mutableStateOf(
            (set.completedDurationSeconds ?: set.targetDurationSeconds)
                ?.let { LocaleFormatting.formatEditableInt(it, locale) }
                .orEmpty()
        )
    }
    var distance by remember(set.id, set.completedDistanceMeters, locale) {
        mutableStateOf(
            (set.completedDistanceMeters ?: set.targetDistanceMeters)
                ?.let { LocaleFormatting.formatEditableDecimal(it, locale) }
                .orEmpty()
        )
    }
    var showStopReasons by remember(set.id) { mutableStateOf(false) }
    var showFeedback by remember(set.id) { mutableStateOf(false) }

    fun draft() = SetValuesDraft(
        reps = LocaleFormatting.parseIntInput(reps),
        weight = LocaleFormatting.parseDecimalInput(weight),
        assistanceWeight = LocaleFormatting.parseDecimalInput(assistance),
        durationSeconds = LocaleFormatting.parseIntInput(duration),
        distanceMeters = LocaleFormatting.parseDecimalInput(distance)
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

    /**
     * Whether some field holds text that is on its way to a number but is not one yet.
     *
     * "47." is what a reader types halfway through "47.5", and "47," halfway through
     * "47,5". [LocaleFormatting] refuses both rather than guessing at them, which is
     * correct for a finished value and wrong for a keystroke: submitting it would store a
     * null, and the stored null flows back and clears the field mid-word. A blank field
     * is not in this state -- clearing a value really does clear it.
     */
    fun hasUnfinishedInput(): Boolean =
        listOf(weight, assistance, distance).any {
            it.isNotBlank() && LocaleFormatting.parseDecimalInput(it) == null
        } || listOf(reps, duration).any {
            it.isNotBlank() && LocaleFormatting.parseIntInput(it) == null
        }

    // Field edits preserve whatever outcome the set already has, so correcting a digit on
    // a completed set does not un-complete it. A transient in-progress value -- a field
    // momentarily cleared to retype a number, or one holding a half-typed decimal -- is
    // never submitted, because the repository would reject it or store a null and a
    // keystroke is neither a failed write nor a cleared value.
    fun submitEdit() {
        if (hasUnfinishedInput()) return
        if (draft().asPerformanceInput(set.isCompleted).isSubmittableFor(set.exerciseType)) {
            onValuesChanged(draft())
        }
    }

    val accent = completedAccent()
    val setNumber = LocaleFormatting.formatEditableInt(set.setNumber, locale)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (set.isCompleted) {
                    accent.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (set.isCompleted) {
                    accent.copy(alpha = 0.5f)
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
                stringResource(R.string.set_number, setNumber),
                color = if (set.isCompleted) {
                    accent
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
                setNumber = setNumber,
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
                        current = LocaleFormatting.parseDecimalInput(textFor(field)),
                        increase = increase,
                        fallback = targetFor(field)
                    )
                    setText(field, LocaleFormatting.formatEditableDecimal(stepped, locale))
                    submitEdit()
                },
                onCopyPrevious = { previous ->
                    setText(field, LocaleFormatting.formatEditableDecimal(previous, locale))
                    submitEdit()
                }
            )
        }

        CompleteSetButton(
            setNumber = setNumber,
            isCompleted = set.isCompleted,
            // Same guard as submitEdit: a half-typed number would reach the repository as a
            // null and come back as a failed completion. The field that holds it shows its
            // own error, so this is explained on screen rather than silently ignored.
            onToggle = { completed ->
                if (!hasUnfinishedInput()) onCompletionChanged(draft(), completed)
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // These secondary actions use the surface content colour rather than the
            // brand accent: at 13sp the accent red does not reach a comfortable contrast
            // ratio on either theme's card background.
            TextButton(
                onClick = { showStopReasons = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text(
                    stringResource(R.string.set_skip_or_stop),
                    fontSize = 13.sp,
                    color = LocalContentColor.current
                )
            }
            TextButton(
                onClick = { showFeedback = !showFeedback },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text(
                    text = stringResource(
                        if (showFeedback) R.string.set_feedback_hide else R.string.set_feedback_show
                    ),
                    fontSize = 13.sp,
                    color = LocalContentColor.current
                )
            }
        }

        if (set.isCompleted) {
            ManageableConfirmation(
                setNumber = setNumber,
                feltManageable = set.feltManageable,
                onRecord = onRecordFeltManageable
            )
        }

        if (showFeedback) {
            EffortControls(
                setNumber = setNumber,
                rpe = set.rpe,
                rir = set.rir,
                onRecordEffort = onRecordEffort
            )
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.stop_reason_body),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SetStopReason.entries.forEach { reason ->
                        TextButton(
                            onClick = {
                                showStopReasons = false
                                onSkipSet(reason)
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
private fun SetValueRow(
    field: SetInputField,
    setNumber: String,
    value: String,
    unitSuffix: String?,
    previousValue: Double?,
    onValueChange: (String) -> Unit,
    onStep: (increase: Boolean) -> Unit,
    onCopyPrevious: (Double) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val fieldLabel = stringResource(field.labelRes)
    val fieldName = if (unitSuffix != null) {
        stringResource(R.string.set_field_with_unit, fieldLabel, unitSuffix)
    } else {
        fieldLabel
    }
    // The label goes into every announcement exactly as written. It used to be lower-cased
    // to read naturally inside an English sentence, which would mangle a language whose
    // nouns do not change case mid-sentence, so the announcement strings carry the
    // sentence and the label stays a value.
    val fieldValueDescription = stringResource(
        R.string.set_field_value_accessibility,
        fieldName,
        setNumber
    )
    // "47." on the way to "47.5" is not a number yet. Saying so beats a Complete button
    // that appears to do nothing.
    val isUnfinished = value.isNotBlank() && if (field.isDecimal) {
        LocaleFormatting.parseDecimalInput(value) == null
    } else {
        LocaleFormatting.parseIntInput(value) == null
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StepButton(
            increase = false,
            contentDescription = stringResource(
                R.string.set_field_decrease_accessibility,
                fieldLabel,
                setNumber
            ),
            onClick = { onStep(false) }
        )
        OutlinedTextField(
            value = value,
            onValueChange = { input -> if (input.length <= MAX_INPUT_LENGTH) onValueChange(input) },
            isError = isUnfinished,
            supportingText = if (isUnfinished) {
                { Text(stringResource(R.string.set_field_incomplete_number), fontSize = 11.sp) }
            } else {
                null
            },
            label = {
                Text(
                    fieldName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            textStyle = LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.isDecimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = fieldValueDescription
                }
        )
        StepButton(
            increase = true,
            contentDescription = stringResource(
                R.string.set_field_increase_accessibility,
                fieldLabel,
                setNumber
            ),
            onClick = { onStep(true) }
        )
        if (previousValue != null) {
            val previousText = LocaleFormatting.formatEditableDecimal(previousValue, locale)
            val copyDescription = stringResource(
                R.string.set_copy_previous_accessibility,
                fieldLabel,
                previousText,
                setNumber
            )
            AssistChip(
                onClick = { onCopyPrevious(previousValue) },
                label = {
                    Text(
                        previousText,
                        fontSize = 12.sp,
                        color = LocalContentColor.current
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                modifier = Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .semantics { contentDescription = copyDescription }
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
    setNumber: String,
    isCompleted: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val description = stringResource(R.string.set_complete_accessibility, setNumber)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LARGE_TOUCH_TARGET)
            .background(
                // Filled with the deep green in both themes: white on the lighter green
                // sits at about 2.2:1, well under WCAG AA.
                if (isCompleted) SuccessGreenDeep else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (isCompleted) SuccessGreenDeep else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(12.dp)
            )
            .toggleable(
                value = isCompleted,
                role = Role.Checkbox,
                onValueChange = onToggle
            )
            .semantics { contentDescription = description },
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
                text = stringResource(
                    if (isCompleted) R.string.set_completed else R.string.set_complete
                ),
                color = if (isCompleted) TextWhite else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ManageableConfirmation(
    setNumber: String,
    feltManageable: Boolean?,
    onRecord: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                onClick = { onRecord(answer) },
                label = { Text(label, color = LocalContentColor.current) },
                modifier = Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .semantics { contentDescription = description }
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
    onRecordEffort: (rpe: Float?, rir: Int?) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val rpeText = rpe?.let { LocaleFormatting.formatEditableDecimal(it.toDouble(), locale) }
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.effort_rpe),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(36.dp)
            )
            StepButton(
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
                    .width(40.dp)
                    .clearAndSetSemantics { contentDescription = rpeAnnouncement }
            )
            StepButton(
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
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text(
                    stringResource(R.string.action_clear),
                    fontSize = 12.sp,
                    color = LocalContentColor.current
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
                    .width(36.dp)
                    .heightIn(min = MIN_TOUCH_TARGET)
            )
            (0..5).forEach { value ->
                val valueText = LocaleFormatting.formatEditableInt(value, locale)
                val description = stringResource(
                    R.string.effort_rir_accessibility,
                    valueText,
                    setNumber
                )
                FilterChip(
                    selected = rir == value,
                    onClick = { onRecordEffort(rpe, if (rir == value) null else value) },
                    label = { Text(valueText, color = LocalContentColor.current) },
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH_TARGET)
                        .semantics { contentDescription = description }
                )
            }
        }
    }
}

private const val MAX_INPUT_LENGTH = LocaleFormatting.MAX_INPUT_LENGTH
private val MIN_TOUCH_TARGET = 48.dp
private val LARGE_TOUCH_TARGET = 56.dp

/**
 * Labels the load field. A null target means no confirmed baseline and no usable
 * history exist yet, so the logger must ask the user to choose one rather than
 * silently accepting whatever value happens to be left in the field.
 */
@Composable
internal fun weightInputLabel(targetWeight: Double?, weightUnit: String): String =
    if (targetWeight == null) {
        stringResource(R.string.set_choose_starting_load)
    } else {
        stringResource(
            R.string.set_field_with_unit,
            stringResource(R.string.set_field_load),
            weightUnit
        )
    }

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
