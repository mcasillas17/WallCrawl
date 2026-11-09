package wallcrawl.elopenmike.com.feature.progress

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary

@Composable
internal fun ExerciseHistoryContent(
    state: ExerciseHistoryUiState.Loaded,
    onExercise: (String) -> Unit,
    onPeriod: (ProgressPeriod) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    onOpenTrain: () -> Unit
) {
    if (state.exerciseId == null) {
        Text(stringResource(R.string.history_chart_no_sets))
        Button(onClick = onOpenTrain) { Text(stringResource(R.string.guided_progress_train)) }
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val count = remember(state.performance) {
        state.performance.asSequence().flatMap { it.observations.asSequence() }.map { it.sessionId }.distinct().count()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExercisePicker(state, onExercise)
        PeriodSelector(state.period, onPeriod)
        if (state.period == ProgressPeriod.CUSTOM) {
            CustomDates(state, dateFormat, onCustom)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val range = if (state.start == state.end) dateFormat.format(state.start) else
                stringResource(R.string.history_chart_period_dates, dateFormat.format(state.start), dateFormat.format(state.end))
            Text(range, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(pluralStringResource(R.plurals.history_chart_sessions, count, LocaleFormatting.formatCount(count, locale)),
                Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (count == 0) {
            Text(stringResource(R.string.history_chart_empty), style = MaterialTheme.typography.bodyMedium)
        }
        val customDates = if (state.period == ProgressPeriod.CUSTOM) state.start to state.end else null
        state.performance.filter { it.observations.isNotEmpty() }.forEach { series ->
            key(state.exerciseId, state.period, customDates, series.shape) {
                ExerciseHistoryChart(state, series, dateFormat, locale)
            }
        }
    }
}

@Composable
private fun ExercisePicker(state: ExerciseHistoryUiState.Loaded, onExercise: (String) -> Unit) {
    val vocabulary = LocalExerciseVocabulary.current
    val exerciseId = requireNotNull(state.exerciseId)
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.history_chart_exercise), style = MaterialTheme.typography.labelMedium)
        Box(Modifier.weight(1f)) {
            val name = vocabulary.exerciseName(exerciseId, state.catalogNames[exerciseId])
            val accessibilityLabel = stringResource(R.string.analytics_choose_exercise, name)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).clickable(role = Role.DropdownList) { open = true }
                .semantics { stateDescription = accessibilityLabel }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            if (open) {
                val options = remember(state.exerciseOptions, state.catalogNames, vocabulary) {
                    vocabulary.exerciseIdsByLabel(state.exerciseOptions, state.catalogNames).map {
                        it to vocabulary.exerciseName(it, state.catalogNames[it])
                    }
                }
                ProgressSelectionMenu(true, { open = false }, options, exerciseId, null, onExercise)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodSelector(selected: ProgressPeriod, onSelect: (ProgressPeriod) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
        ProgressPeriod.entries.forEach { period ->
            val label = when (period) {
                ProgressPeriod.WEEK -> R.string.history_chart_week
                ProgressPeriod.MONTH -> R.string.history_chart_month
                ProgressPeriod.YEAR -> R.string.history_chart_year
                ProgressPeriod.ALL -> R.string.history_chart_all
                ProgressPeriod.CUSTOM -> R.string.history_chart_custom
            }
            val active = period == selected
            Column(Modifier.widthIn(min = 48.dp).selectable(selected = active, role = Role.Tab, onClick = { onSelect(period) }),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.heightIn(min = 46.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.widthIn(min = 40.dp).height(2.dp).then(
                    if (active) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier
                ))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomDates(state: ExerciseHistoryUiState.Loaded, dates: DateTimeFormatter, onApply: (LocalDate, LocalDate) -> Unit) {
    var from by rememberSaveable(state.start) { mutableStateOf(state.start.toEpochDay()) }
    var through by rememberSaveable(state.end) { mutableStateOf(state.end.toEpochDay()) }
    var editStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (startField in listOf(true, false)) {
            val label = stringResource(if (startField) R.string.history_chart_from else R.string.history_chart_through)
            OutlinedButton(onClick = { editStart = startField }) {
                Column {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                    Text(dates.format(LocalDate.ofEpochDay(if (startField) from else through)),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        TextButton(onClick = { onApply(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(through)) }, enabled = from <= through) {
            Text(stringResource(R.string.editor_action_apply))
        }
    }
    if (from > through) Text(stringResource(R.string.history_chart_date_order), color = MaterialTheme.colorScheme.error)
    editStart?.let { startField ->
        val context = LocalContext.current
        DisposableEffect(context, startField, state.today, state.zoneId) {
            val date = LocalDate.ofEpochDay(if (startField) from else through)
            val dialog = DatePickerDialog(context, { _, year, month, day ->
                val picked = LocalDate.of(year, month + 1, day).toEpochDay()
                if (startField) from = picked else through = picked
            }, date.year, date.monthValue - 1, date.dayOfMonth)
            dialog.datePicker.maxDate = state.today.atStartOfDay(state.zoneId).toInstant().toEpochMilli()
            dialog.setOnDismissListener { editStart = null }
            dialog.show()
            onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
        }
    }
}
