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
import androidx.compose.ui.graphics.Color
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.ui.localization.descriptionRes
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary

val CapabilityLevel.displayOrder: Int
    get() = when (this) {
        CapabilityLevel.COMFORTABLE -> 0
        CapabilityLevel.LIMITED -> 1
        CapabilityLevel.AVOID -> 2
        CapabilityLevel.UNKNOWN -> 3
    }

@Composable
fun movementCapabilityLabel(type: MovementCapabilityType): String =
    stringResource(type.labelRes)

@Composable
fun movementCapabilityDescription(type: MovementCapabilityType): String =
    stringResource(type.descriptionRes)

@Composable
fun capabilityLevelLabel(level: CapabilityLevel): String = stringResource(level.labelRes)

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
                val isSelected = selectedLevel == level
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(level) },
                    label = {
                        Text(
                            text = optionLabel,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = optionContentDescription
                            role = Role.RadioButton
                        },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = CrimsonRedPrimary,
                        selectedLabelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = CrimsonRedPrimary
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
