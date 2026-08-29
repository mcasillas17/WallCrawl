package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreen
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite

@Composable
fun SetRow(
    set: WorkoutSet,
    weightUnit: String,
    onUpdateSet: (reps: Int?, weight: Double?, isCompleted: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var weightText by remember(set.id, set.completedWeight, set.targetWeight) {
        val initialVal = set.completedWeight ?: set.targetWeight
        mutableStateOf(if (initialVal != null) {
            if (initialVal % 1.0 == 0.0) initialVal.toInt().toString() else initialVal.toString()
        } else "")
    }

    var repsText by remember(set.id, set.completedReps, set.targetReps) {
        val initialVal = set.completedReps ?: set.targetReps
        mutableStateOf(initialVal?.takeIf { it > 0 }?.toString().orEmpty())
    }

    val isCompleted = set.isCompleted

    val rowBackgroundColor = if (isCompleted) SuccessGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
    val rowBorderColor = if (isCompleted) SuccessGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackgroundColor, RoundedCornerShape(12.dp))
            .border(1.dp, rowBorderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Set number badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${set.setNumber}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Weight Input
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = weightText,
                onValueChange = { newVal ->
                    weightText = newVal
                    val parsedWeight = newVal.toDoubleOrNull()
                    val parsedReps = repsText.toIntOrNull()
                    onUpdateSet(parsedReps, parsedWeight, isCompleted)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(CrimsonRedPrimary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (weightText.isEmpty()) {
                            Text(
                                text = set.targetWeight?.let { "$it" } ?: "0",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = weightUnit,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Reps Input
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = repsText,
                onValueChange = { newVal ->
                    repsText = newVal
                    val parsedWeight = weightText.toDoubleOrNull()
                    val parsedReps = newVal.toIntOrNull()
                    onUpdateSet(parsedReps, parsedWeight, isCompleted)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(CrimsonRedPrimary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (repsText.isEmpty()) {
                            Text(
                                text = "${set.targetReps}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "reps",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Checkmark Completion Toggle Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(10.dp)
                )
                .border(
                    1.dp,
                    if (isCompleted) SuccessGreen else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(10.dp)
                )
                .clickable {
                    val parsedWeight = weightText.toDoubleOrNull() ?: set.targetWeight
                    val parsedReps = repsText.toIntOrNull() ?: set.targetReps
                    onUpdateSet(parsedReps, parsedWeight, !isCompleted)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Complete Set",
                tint = if (isCompleted) TextWhite else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Logger row that exposes only the measurements supported by the catalog exercise type. */
@Composable
fun PerformanceSetRow(
    set: WorkoutSet,
    weightUnit: String,
    onUpdateSet: (SetPerformanceInput) -> Unit,
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
        mutableStateOf((set.completedDurationSeconds ?: set.targetDurationSeconds)?.toString().orEmpty())
    }
    var distance by remember(set.id, set.completedDistanceMeters) {
        mutableStateOf((set.completedDistanceMeters ?: set.targetDistanceMeters)?.compactText().orEmpty())
    }

    fun current(completed: Boolean = set.isCompleted) = SetPerformanceInput(
        reps = reps.toIntOrNull(),
        weight = weight.toDoubleOrNull(),
        assistanceWeight = assistance.toDoubleOrNull(),
        durationSeconds = duration.toIntOrNull(),
        distanceMeters = distance.toDoubleOrNull(),
        isCompleted = completed
    )

    // Field edits (typing) preserve whatever completion state the set already has, so a
    // digit typed while correcting an already-completed set doesn't uncomplete it. But a
    // transient in-progress value -- e.g. the field is momentarily empty between clearing
    // and retyping a number -- must never be submitted as a completion: the repository
    // would reject it and the caller must not treat a mid-edit keystroke as a rejected
    // write. Only the checkbox performs the explicit completion transition, so it always
    // submits regardless of validity.
    fun submitEdit() {
        val performance = current()
        if (performance.isSubmittableFor(set.exerciseType)) {
            onUpdateSet(performance)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (set.isCompleted) SuccessGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (set.isCompleted) SuccessGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Set ${set.setNumber}",
                color = if (set.isCompleted) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("Done", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Checkbox(
                checked = set.isCompleted,
                onCheckedChange = { onUpdateSet(current(completed = it)) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (set.exerciseType) {
                ExerciseType.WEIGHT_REPS -> {
                    CompactSetInput(
                        weight,
                        { weight = it; submitEdit() },
                        weightInputLabel(set.targetWeight, weightUnit),
                        true,
                        Modifier.weight(1f)
                    )
                    CompactSetInput(reps, { reps = it; submitEdit() }, "Reps", false, Modifier.weight(1f))
                }
                ExerciseType.BODYWEIGHT_REPS ->
                    CompactSetInput(reps, { reps = it; submitEdit() }, "Reps", false, Modifier.weight(1f))
                ExerciseType.ASSISTED_BODYWEIGHT -> {
                    CompactSetInput(assistance, { assistance = it; submitEdit() }, "Assist $weightUnit", true, Modifier.weight(1f))
                    CompactSetInput(reps, { reps = it; submitEdit() }, "Reps", false, Modifier.weight(1f))
                }
                ExerciseType.DURATION ->
                    CompactSetInput(duration, { duration = it; submitEdit() }, "Seconds", false, Modifier.weight(1f))
                ExerciseType.DISTANCE_DURATION -> {
                    CompactSetInput(distance, { distance = it; submitEdit() }, "Meters", true, Modifier.weight(1f))
                    CompactSetInput(duration, { duration = it; submitEdit() }, "Seconds", false, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompactSetInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    decimal: Boolean,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.length <= 10) onValueChange(input)
        },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier
    )
}

private fun Double.compactText(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

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
