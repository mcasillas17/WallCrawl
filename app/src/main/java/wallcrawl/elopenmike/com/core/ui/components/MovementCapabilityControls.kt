package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType

val CapabilityLevel.displayOrder: Int
    get() = when (this) {
        CapabilityLevel.COMFORTABLE -> 0
        CapabilityLevel.LIMITED -> 1
        CapabilityLevel.AVOID -> 2
        CapabilityLevel.UNKNOWN -> 3
    }

@Composable
fun movementCapabilityLabel(type: MovementCapabilityType): String = stringResource(
    when (type) {
        MovementCapabilityType.IMPACT -> R.string.movement_capability_impact_label
        MovementCapabilityType.FLOOR_TRANSITION ->
            R.string.movement_capability_floor_transition_label
        MovementCapabilityType.UNSUPPORTED_SQUAT ->
            R.string.movement_capability_unsupported_squat_label
        MovementCapabilityType.UPPER_BODY_BODYWEIGHT_PUSH ->
            R.string.movement_capability_upper_body_push_label
        MovementCapabilityType.VERTICAL_PULL_OR_HANG ->
            R.string.movement_capability_vertical_pull_label
        MovementCapabilityType.BALANCE_WITHOUT_SUPPORT ->
            R.string.movement_capability_balance_label
        MovementCapabilityType.CONTINUOUS_ACTIVITY ->
            R.string.movement_capability_continuous_activity_label
    }
)

@Composable
fun movementCapabilityDescription(type: MovementCapabilityType): String = stringResource(
    when (type) {
        MovementCapabilityType.IMPACT -> R.string.movement_capability_impact_description
        MovementCapabilityType.FLOOR_TRANSITION ->
            R.string.movement_capability_floor_transition_description
        MovementCapabilityType.UNSUPPORTED_SQUAT ->
            R.string.movement_capability_unsupported_squat_description
        MovementCapabilityType.UPPER_BODY_BODYWEIGHT_PUSH ->
            R.string.movement_capability_upper_body_push_description
        MovementCapabilityType.VERTICAL_PULL_OR_HANG ->
            R.string.movement_capability_vertical_pull_description
        MovementCapabilityType.BALANCE_WITHOUT_SUPPORT ->
            R.string.movement_capability_balance_description
        MovementCapabilityType.CONTINUOUS_ACTIVITY ->
            R.string.movement_capability_continuous_activity_description
    }
)

@Composable
fun capabilityLevelLabel(level: CapabilityLevel): String = stringResource(
    when (level) {
        CapabilityLevel.UNKNOWN -> R.string.capability_level_unknown
        CapabilityLevel.COMFORTABLE -> R.string.capability_level_comfortable
        CapabilityLevel.LIMITED -> R.string.capability_level_limited
        CapabilityLevel.AVOID -> R.string.capability_level_avoid
    }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovementCapabilityQuestion(
    type: MovementCapabilityType,
    selectedLevel: CapabilityLevel?,
    onSelect: (CapabilityLevel) -> Unit,
    modifier: Modifier = Modifier,
    showAnswerRequired: Boolean = false
) {
    val capabilityLabel = movementCapabilityLabel(type)
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
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CapabilityLevel.entries.sortedBy(CapabilityLevel::displayOrder).forEach { level ->
                val optionLabel = capabilityLevelLabel(level)
                val optionContentDescription = stringResource(
                    R.string.movement_capability_option_accessibility,
                    capabilityLabel,
                    optionLabel
                )
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { onSelect(level) },
                    label = { Text(optionLabel) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = optionContentDescription
                            role = Role.RadioButton
                        },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
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
