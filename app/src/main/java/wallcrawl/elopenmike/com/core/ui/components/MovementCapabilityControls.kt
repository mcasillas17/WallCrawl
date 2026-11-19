package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.ui.localization.descriptionRes
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary

@Composable
fun movementCapabilityLabel(type: MovementCapabilityType): String =
    stringResource(type.labelRes)

@Composable
fun movementCapabilityDescription(type: MovementCapabilityType): String =
    stringResource(type.descriptionRes)

@Composable
fun capabilityLevelLabel(level: CapabilityLevel): String = stringResource(level.labelRes)

/**
 * Comfortable, Limited and Avoid read as one three-step scale; Not sure sits under it as its own
 * choice because it is not a point on that scale. All four stay one radio group.
 */
@Composable
fun MovementCapabilityQuestion(
    type: MovementCapabilityType,
    selectedLevel: CapabilityLevel?,
    onSelect: (CapabilityLevel) -> Unit,
    modifier: Modifier = Modifier,
    showAnswerRequired: Boolean = false
) {
    val capabilityLabel = movementCapabilityLabel(type)
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(10.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = capabilityLabel,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = movementCapabilityDescription(type),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(shape)
                    .border(1.dp, outline, shape)
            ) {
                listOf(CapabilityLevel.COMFORTABLE, CapabilityLevel.LIMITED, CapabilityLevel.AVOID)
                    .forEachIndexed { index, level ->
                        if (index > 0) VerticalDivider(color = outline)
                        val isSelected = selectedLevel == level
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .background(if (isSelected) CrimsonRedPrimary else Color.Transparent)
                                .capabilityOption(capabilityLabel, level, isSelected, onSelect)
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = capabilityLevelLabel(level),
                                textAlign = TextAlign.Center,
                                // Narrow phones and large font scales shrink the label rather than break a word.
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
            }
            val unsure = selectedLevel == CapabilityLevel.UNKNOWN
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .capabilityOption(capabilityLabel, CapabilityLevel.UNKNOWN, unsure, onSelect)
                    .padding(end = 8.dp)
            ) {
                RadioButton(
                    selected = unsure,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = CrimsonRedPrimary)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = capabilityLevelLabel(CapabilityLevel.UNKNOWN),
                    fontSize = 14.sp,
                    color = if (unsure) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unsure) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        if (showAnswerRequired) {
            Text(
                text = stringResource(R.string.movement_capability_answer_required),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun Modifier.capabilityOption(
    capabilityLabel: String,
    level: CapabilityLevel,
    selected: Boolean,
    onSelect: (CapabilityLevel) -> Unit
): Modifier {
    val description = stringResource(R.string.movement_capability_option_accessibility, capabilityLabel,
        capabilityLevelLabel(level))
    return selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(level) })
        .semantics { contentDescription = description }
}
