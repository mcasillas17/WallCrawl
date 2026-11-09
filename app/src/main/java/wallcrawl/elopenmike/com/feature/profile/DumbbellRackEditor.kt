package wallcrawl.elopenmike.com.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.DumbbellRack
import wallcrawl.elopenmike.com.core.model.WeightUnit
import java.text.NumberFormat

internal data class DumbbellRackEditorState(
    val unit: WeightUnit = WeightUnit.KG,
    val weights: List<Double>? = null,
    val original: DumbbellRack? = null,
    val input: String = "",
    val saving: Boolean = false,
    val error: Int? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DumbbellRackEditor(viewModel: ProfileViewModel, rack: DumbbellRack?) {
    val state by viewModel.dumbbellRackEditor.collectAsState()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val number = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 } }
    val weights = state.weights
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.rack_title), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        if (weights == null) {
            val summary = when {
                rack == null -> stringResource(R.string.rack_unset)
                rack.weights.size == 1 -> stringResource(R.string.editor_summary_load,
                    number.format(rack.weights.single()), rack.unit.symbol)
                else -> stringResource(R.string.rack_summary, number.format(rack.weights.size),
                    number.format(rack.weights.first()), number.format(rack.weights.last()), rack.unit.symbol)
            }
            Text(summary)
            TextButton(onClick = viewModel::startDumbbellRackEditing) { Text(stringResource(R.string.rack_edit)) }
        } else {
            Text(stringResource(R.string.rack_hint))
            if (weights.isEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeightUnit.entries.forEach { unit ->
                        FilterChip(selected = state.unit == unit, enabled = !state.saving,
                            onClick = { viewModel.updateDumbbellRackUnit(unit) },
                            label = { Text(unit.symbol) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            } else Text(stringResource(R.string.rack_unit, state.unit.symbol))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weights.forEach { weight ->
                    val remove = stringResource(R.string.rack_remove_weight, number.format(weight), state.unit.symbol)
                    InputChip(selected = false, enabled = !state.saving,
                        onClick = { viewModel.removeDumbbellRackWeight(weight) },
                        label = { Text(number.format(weight) + " " + state.unit.symbol) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = remove })
                }
            }
            OutlinedTextField(value = state.input, onValueChange = viewModel::updateDumbbellRackInput,
                enabled = !state.saving, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.addDumbbellRackWeight() }),
                label = { Text(stringResource(R.string.rack_weight_label, state.unit.symbol)) })
            TextButton(enabled = !state.saving, onClick = { viewModel.addDumbbellRackWeight() }) { Text(stringResource(R.string.rack_add)) }
            state.error?.let { error ->
                val message = if (error == R.string.rack_invalid) stringResource(error,
                    number.format(wallcrawl.elopenmike.com.core.model.WorkoutTemplate.maxLoad(state.unit)), state.unit.symbol)
                    else stringResource(error)
                Text(message, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !state.saving, onClick = viewModel::clearDumbbellRack) { Text(stringResource(R.string.rack_clear)) }
                TextButton(enabled = !state.saving, onClick = viewModel::cancelDumbbellRackEditing) { Text(stringResource(R.string.rack_cancel)) }
                Button(enabled = !state.saving, onClick = viewModel::saveDumbbellRack) { Text(stringResource(R.string.rack_save)) }
            }
        }
    }
}
