package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary

@Composable
fun BandSetupControls(
    selectedEquipment: Collection<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val vocabulary = LocalExerciseVocabulary.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.band_setup_heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            stringResource(R.string.band_setup_hint, vocabulary.equipment(StandardEquipment.RESISTANCE_BAND)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StandardEquipment.BAND_SETUPS.forEach { setup ->
            val selected = setup in selectedEquipment
            val labelColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
            val description = when (setup) {
                StandardEquipment.BAND_ANCHOR_UPPER_BODY -> R.string.band_setup_upper_body_description
                StandardEquipment.BAND_ANCHOR_OVERHEAD -> R.string.band_setup_overhead_description
                StandardEquipment.BAND_ANCHOR_LOW -> R.string.band_setup_low_description
                StandardEquipment.BAND_KICKBACK_ATTACHMENT_SUPPORT -> R.string.band_setup_kickback_description
                else -> error("Unknown band setup confirmation.")
            }
            FilterChip(
                selected = selected,
                onClick = { onToggle(setup) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                leadingIcon = if (selected) {
                    { Icon(Icons.Default.Check, contentDescription = null, tint = labelColor) }
                } else null,
                label = {
                    Column(
                        Modifier.padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            vocabulary.equipment(setup),
                            style = MaterialTheme.typography.labelLarge,
                            color = labelColor
                        )
                        Text(
                            stringResource(description),
                            style = MaterialTheme.typography.bodySmall,
                            color = labelColor
                        )
                    }
                }
            )
        }
    }
}
