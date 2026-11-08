package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.ReportedCondition
import wallcrawl.elopenmike.com.core.ui.localization.labelRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportedConditionPicker(selected: Set<String>, enabled: Boolean, onChange: (String, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.reported_conditions_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.a11yHeading())
        Text(stringResource(R.string.reported_conditions_hint), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReportedCondition.entries.forEach { condition ->
                val checked = condition.id in selected
                FilterChip(
                    selected = checked,
                    onClick = { onChange(condition.id, !checked) },
                    enabled = enabled,
                    label = { Text(stringResource(condition.labelRes)) },
                    leadingIcon = if (checked) {
                        { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    modifier = Modifier.testTag("reported-condition-${condition.id}")
                )
            }
        }
        val unknown = selected - ReportedCondition.ids
        if (unknown.isNotEmpty()) {
            Text(stringResource(R.string.condition_legacy_refusal))
            unknown.sorted().forEach { id ->
                TextButton(enabled = enabled, onClick = { onChange(id, false) }) {
                    Text(stringResource(R.string.condition_legacy_remove, id))
                }
            }
        }
    }
}
