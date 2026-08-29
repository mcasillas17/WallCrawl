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
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteBorder
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurface
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurfaceElevated
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreen
import wallcrawl.elopenmike.com.core.ui.theme.TextDisabled
import wallcrawl.elopenmike.com.core.ui.theme.TextMuted
import wallcrawl.elopenmike.com.core.ui.theme.TextPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextSecondary
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

    val rowBackgroundColor = if (isCompleted) Color(0x1810B981) else GraphiteSurfaceElevated
    val rowBorderColor = if (isCompleted) SuccessGreen.copy(alpha = 0.5f) else GraphiteBorder

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
                .background(GraphiteSurface, RoundedCornerShape(8.dp))
                .border(1.dp, GraphiteBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${set.setNumber}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCompleted) SuccessGreen else TextSecondary
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
                    color = TextPrimary,
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
                            .background(GraphiteSurface, RoundedCornerShape(8.dp))
                            .border(1.dp, GraphiteBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (weightText.isEmpty()) {
                            Text(
                                text = set.targetWeight?.let { "$it" } ?: "0",
                                color = TextDisabled,
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
                color = TextMuted
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
                    color = TextPrimary,
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
                            .background(GraphiteSurface, RoundedCornerShape(8.dp))
                            .border(1.dp, GraphiteBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (repsText.isEmpty()) {
                            Text(
                                text = "${set.targetReps}",
                                color = TextDisabled,
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
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Checkmark Completion Toggle Button
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    if (isCompleted) SuccessGreen else GraphiteSurface,
                    RoundedCornerShape(10.dp)
                )
                .border(
                    1.dp,
                    if (isCompleted) SuccessGreen else GraphiteBorder,
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
                tint = if (isCompleted) TextWhite else TextDisabled,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (set.isCompleted) Color(0x1810B981) else GraphiteSurfaceElevated,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (set.isCompleted) SuccessGreen.copy(alpha = 0.5f) else GraphiteBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Set ${set.setNumber}",
                color = if (set.isCompleted) SuccessGreen else TextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("Done", color = TextMuted, fontSize = 12.sp)
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
                        { weight = it; onUpdateSet(current()) },
                        weightInputLabel(set.targetWeight, weightUnit),
                        true,
                        Modifier.weight(1f)
                    )
                    CompactSetInput(reps, { reps = it; onUpdateSet(current()) }, "Reps", false, Modifier.weight(1f))
                }
                ExerciseType.BODYWEIGHT_REPS ->
                    CompactSetInput(reps, { reps = it; onUpdateSet(current()) }, "Reps", false, Modifier.weight(1f))
                ExerciseType.ASSISTED_BODYWEIGHT -> {
                    CompactSetInput(assistance, { assistance = it; onUpdateSet(current()) }, "Assist $weightUnit", true, Modifier.weight(1f))
                    CompactSetInput(reps, { reps = it; onUpdateSet(current()) }, "Reps", false, Modifier.weight(1f))
                }
                ExerciseType.DURATION ->
                    CompactSetInput(duration, { duration = it; onUpdateSet(current()) }, "Seconds", false, Modifier.weight(1f))
                ExerciseType.DISTANCE_DURATION -> {
                    CompactSetInput(distance, { distance = it; onUpdateSet(current()) }, "Meters", true, Modifier.weight(1f))
                    CompactSetInput(duration, { duration = it; onUpdateSet(current()) }, "Seconds", false, Modifier.weight(1f))
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
