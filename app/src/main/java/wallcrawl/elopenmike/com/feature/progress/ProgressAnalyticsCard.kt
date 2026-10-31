package wallcrawl.elopenmike.com.feature.progress

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.AnalyticsBucket
import wallcrawl.elopenmike.com.core.model.AnalyticsMetric
import wallcrawl.elopenmike.com.core.model.AnalyticsRange
import wallcrawl.elopenmike.com.core.model.AnalyticsScope
import wallcrawl.elopenmike.com.core.model.AnalyticsSelection
import wallcrawl.elopenmike.com.core.model.DeloadSource
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.PerformanceObservation
import wallcrawl.elopenmike.com.core.model.PerformanceSeries
import wallcrawl.elopenmike.com.core.model.PerformanceShape
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.components.AnalyticsBarChart
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.progressionAxisRes
import wallcrawl.elopenmike.com.core.ui.localization.progressionHoldReasonRes
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary

internal const val PROGRESS_ANALYTICS_TEST_TAG = "progress-analytics"

/**
 * The "Over time" card. Its filters change only this card; every value has a list row that
 * reads it out in full and opens the workouts behind it, so the chart is never the only way in.
 */
@Composable
internal fun ProgressAnalyticsCard(
    state: ProgressAnalyticsUiState,
    selection: AnalyticsSelection,
    zoneId: ZoneId,
    actions: ProgressAnalyticsActions,
    onOpenSession: (String) -> Unit
) {
    // Options survive a reload so the chooser stays usable while a new range is read.
    var options by remember { mutableStateOf<Pair<List<String>, Map<String, String>>>(emptyList<String>() to emptyMap()) }
    if (state is ProgressAnalyticsUiState.Loaded) options = state.exerciseOptions to state.catalogNames

    WallCrawlCard(cornerRadius = 16.dp, contentPadding = 16.dp, modifier = Modifier.testTag(PROGRESS_ANALYTICS_TEST_TAG)) {
        Text(
            text = stringResource(R.string.analytics_heading),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Body(stringResource(R.string.analytics_scope_note, zoneId.id))
        Filters(selection, options.first, options.second, actions)
        Spacer(Modifier.height(8.dp))
        when (state) {
            ProgressAnalyticsUiState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp)) {
                CircularProgressIndicator(color = CrimsonRedPrimary, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.analytics_loading), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp, modifier = Modifier.padding(start = 36.dp))
            }
            ProgressAnalyticsUiState.NoProfile -> Body(stringResource(R.string.progress_no_profile))
            ProgressAnalyticsUiState.TooLarge -> Body(stringResource(R.string.analytics_too_large))
            ProgressAnalyticsUiState.Error -> {
                Text(stringResource(R.string.analytics_error), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Button(onClick = actions.onRetry) { Text(stringResource(R.string.action_try_again)) }
            }
            is ProgressAnalyticsUiState.Loaded -> LoadedContent(state, zoneId, onOpenSession)
        }
    }
}

/** Callbacks grouped so the stateless screen stays readable. */
class ProgressAnalyticsActions(
    val onSelectRange: (AnalyticsRange) -> Unit,
    val onSelectScope: (AnalyticsScope) -> Unit,
    val onSelectMetric: (AnalyticsMetric) -> Unit,
    val onReset: () -> Unit,
    val onRetry: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filters(
    selection: AnalyticsSelection,
    exerciseOptions: List<String>,
    catalogNames: Map<String, String>,
    actions: ProgressAnalyticsActions
) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    var exerciseMenu by remember { mutableStateOf(false) }
    var muscleMenu by remember { mutableStateOf(false) }

    Label(stringResource(R.string.analytics_range_label))
    ChipRow {
        AnalyticsRange.entries.forEach { range ->
            Chip(pluralStringResource(R.plurals.analytics_range_weeks, range.weeks, LocaleFormatting.formatCount(range.weeks, locale)),
                selection.range == range) { actions.onSelectRange(range) }
        }
    }
    Label(stringResource(R.string.analytics_scope_label))
    ChipRow {
        Chip(stringResource(R.string.analytics_scope_all), selection.scope == AnalyticsScope.All) {
            actions.onSelectScope(AnalyticsScope.All)
        }
        Box {
            Chip(stringResource(R.string.analytics_scope_exercise), selection.scope is AnalyticsScope.Exercise) { exerciseMenu = true }
            val labelled = exerciseOptions.map { it to vocabulary.exerciseName(it, catalogNames[it]) }.sortedBy { it.second }
            Menu(exerciseMenu, { exerciseMenu = false }, labelled, (selection.scope as? AnalyticsScope.Exercise)?.exerciseId,
                stringResource(R.string.analytics_no_exercise_options)) { actions.onSelectScope(AnalyticsScope.Exercise(it)) }
        }
        Box {
            Chip(stringResource(R.string.analytics_scope_muscle), selection.scope is AnalyticsScope.Muscle) { muscleMenu = true }
            val labelled = StandardMuscles.TRAINABLE.map { it to vocabulary.muscle(it) }.sortedBy { it.second }
            Menu(muscleMenu, { muscleMenu = false }, labelled, (selection.scope as? AnalyticsScope.Muscle)?.muscle, null) {
                actions.onSelectScope(AnalyticsScope.Muscle(it))
            }
        }
    }
    when (val scope = selection.scope) {
        is AnalyticsScope.Exercise -> OutlinedButton(onClick = { exerciseMenu = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.analytics_choose_exercise,
                vocabulary.exerciseName(scope.exerciseId, catalogNames[scope.exerciseId])), color = MaterialTheme.colorScheme.onSurface)
        }
        is AnalyticsScope.Muscle -> OutlinedButton(onClick = { muscleMenu = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.analytics_choose_muscle, vocabulary.muscle(scope.muscle)),
                color = MaterialTheme.colorScheme.onSurface)
        }
        AnalyticsScope.All -> Unit
    }
    Label(stringResource(R.string.analytics_metric_label))
    ChipRow {
        AnalyticsMetric.entries.forEach { metric ->
            Chip(stringResource(metric.labelRes()), selection.metric == metric) { actions.onSelectMetric(metric) }
        }
    }
    if (selection != AnalyticsSelection()) {
        TextButton(onClick = actions.onReset, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.analytics_reset), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun LoadedContent(state: ProgressAnalyticsUiState.Loaded, zoneId: ZoneId, onOpenSession: (String) -> Unit) {
    val analytics = state.analytics
    val selection = analytics.selection
    val locale = LocalConfiguration.current.locales[0]
    val unit = state.preferredUnit
    when {
        !analytics.hasWorkoutsInRange -> return Body(stringResource(R.string.analytics_empty_range))
        !analytics.hasScopeMatches -> return Body(stringResource(R.string.analytics_no_matches))
    }
    Body(metricDefinition(selection.metric, unit))
    if (selection.scope is AnalyticsScope.Muscle && selection.metric != AnalyticsMetric.REVIEWED_PRIMARY_DOSE) {
        Body(stringResource(R.string.analytics_muscle_basis))
        if (analytics.unclassifiableSets > 0) {
            Body(pluralStringResource(R.plurals.analytics_unclassifiable, analytics.unclassifiableSets,
                LocaleFormatting.formatCount(analytics.unclassifiableSets, locale)))
        }
    }
    if (selection.metric == AnalyticsMetric.REVIEWED_PRIMARY_DOSE) {
        val omitted = analytics.unattributedWorkSets.values.sum()
        if (omitted > 0) {
            Body(pluralStringResource(R.plurals.analytics_unattributed, omitted, LocaleFormatting.formatCount(omitted, locale)))
            analytics.unattributedWorkSets.forEach { (reason, count) ->
                Body("${stringResource(reason.labelRes())}: ${pluralStringResource(R.plurals.count_sets, count,
                    LocaleFormatting.formatCount(count, locale))}")
            }
        }
    }

    val metricName = stringResource(selection.metric.labelRes())
    val dates = rememberDateFormat(zoneId)
    val buckets = analytics.buckets
    val highest = buckets.maxByOrNull { it.value }?.value ?: 0.0
    Text(
        text = stringResource(R.string.analytics_total, metricValue(selection.metric, analytics.rangeTotal, unit)),
        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
    )
    Body(pluralStringResource(R.plurals.analytics_full_weeks, analytics.fullWeeks,
        LocaleFormatting.formatCount(analytics.fullWeeks, locale)))
    Spacer(Modifier.height(8.dp))
    AnalyticsBarChart(
        values = buckets.map { it.value },
        hatched = buckets.indices.filter { buckets[it].partial }.toSet(),
        description = stringResource(R.string.analytics_chart_description, metricName,
            dates.format(Date(buckets.first().week.startEpochMillis)),
            dates.format(Date(buckets.last().week.endEpochMillisExclusive - 1)),
            metricValue(selection.metric, highest, unit))
    )
    Spacer(Modifier.height(8.dp))

    var expandedWeek by rememberSaveable(selection) { mutableStateOf<Long?>(null) }
    buckets.asReversed().forEach { bucket ->
        WeekRow(bucket, selection.metric, unit, dates, bucket.week.startEpochDay == expandedWeek) {
            expandedWeek = bucket.week.startEpochDay.takeUnless { it == expandedWeek }
        }
        if (bucket.week.startEpochDay == expandedWeek) {
            Label(stringResource(R.string.analytics_week_contributors))
            bucket.contributors.forEach { contributor ->
                LinkRow(stringResource(R.string.analytics_contributor_row, contributor.name,
                    dates.format(Date(contributor.completedAtTimestamp)), metricValue(selection.metric, contributor.value, unit))) {
                    onOpenSession(contributor.sessionId)
                }
            }
            if (bucket.contributors.isEmpty()) Body(stringResource(R.string.analytics_no_matches))
        }
    }

    if (selection.scope is AnalyticsScope.Exercise) {
        Section(stringResource(R.string.analytics_performance_heading))
        Body(stringResource(R.string.analytics_performance_note))
        if (analytics.performance.isEmpty()) Body(stringResource(R.string.analytics_performance_empty))
        analytics.performance.forEach { PerformanceSection(it, unit, dates, onOpenSession) }
        DecisionsSection(state.decisions, dates, onOpenSession)
    }
}

@Composable
private fun PerformanceSection(series: PerformanceSeries, unit: WeightUnit, dates: DateFormat, onOpenSession: (String) -> Unit) {
    Label(stringResource(series.shape.labelRes()))
    when (series.shape) {
        PerformanceShape.ASSISTED_REPS -> Body(stringResource(R.string.analytics_shape_assisted_note))
        PerformanceShape.DISTANCE_AND_TIME -> Body(stringResource(R.string.analytics_shape_distance_time_note))
        else -> {
            val comparison = series.comparison
            if (comparison == null) {
                Body(stringResource(R.string.analytics_insufficient))
            } else {
                Body(stringResource(R.string.analytics_comparison,
                    observationValue(series.shape, comparison.earliest, unit), dates.format(Date(comparison.earliest.completedAtTimestamp)),
                    observationValue(series.shape, comparison.latest, unit), dates.format(Date(comparison.latest.completedAtTimestamp)),
                    signedDifference(series.shape, comparison.difference, unit)))
            }
            if (series.observations.size > 1) {
                AnalyticsBarChart(series.observations.map { it.value },
                    stringResource(R.string.analytics_chart_description, stringResource(series.shape.labelRes()),
                        dates.format(Date(series.observations.first().completedAtTimestamp)),
                        dates.format(Date(series.observations.last().completedAtTimestamp)),
                        observationValue(series.shape, series.observations.maxBy { it.value }, unit)))
            }
        }
    }
    series.observations.asReversed().forEach { observation ->
        LinkRow(stringResource(R.string.analytics_contributor_row, observation.sessionName,
            dates.format(Date(observation.completedAtTimestamp)), observationValue(series.shape, observation, unit))) {
            onOpenSession(observation.sessionId)
        }
    }
}

@Composable
private fun DecisionsSection(decisions: List<AnalyticsDecision>, dates: DateFormat, onOpenSession: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Section(stringResource(R.string.analytics_decisions_heading))
    Body(stringResource(R.string.analytics_decisions_note))
    if (decisions.isEmpty()) Body(stringResource(R.string.analytics_decisions_empty))
    decisions.asReversed().forEach { decision ->
        val lines = buildList {
            decision.progression?.let { reason ->
                add(progressionHoldReasonRes(reason.reason)?.let { stringResource(it) }
                    ?: stringResource(R.string.history_advanced, stringResource(progressionAxisRes(requireNotNull(reason.axis)))))
                add(if (reason.sourceSessionIds.isEmpty()) stringResource(R.string.analytics_decision_no_sources)
                else pluralStringResource(R.plurals.analytics_decision_sources, reason.sourceSessionIds.size,
                    LocaleFormatting.formatCount(reason.sourceSessionIds.size, locale)))
            }
            when (decision.acceptedDeload) {
                DeloadSource.EXPLICIT_REQUEST -> add(stringResource(R.string.history_deload_requested))
                DeloadSource.RETURNING -> add(stringResource(R.string.history_deload_returning))
                null -> Unit
            }
            if (decision.unsupportedIdentities.isNotEmpty()) add(stringResource(R.string.history_unsupported_details))
        }
        LinkRow(stringResource(R.string.analytics_decision_row, decision.sessionName,
            dates.format(Date(decision.completedAtTimestamp))), lines) { onOpenSession(decision.sessionId) }
    }
}

@Composable
private fun WeekRow(bucket: AnalyticsBucket, metric: AnalyticsMetric, unit: WeightUnit, dates: DateFormat, expanded: Boolean, onToggle: () -> Unit) {
    val value = metricValue(metric, bucket.value, unit)
    val start = dates.format(Date(bucket.week.startEpochMillis))
    val end = dates.format(Date(bucket.week.endEpochMillisExclusive - 1))
    val text = stringResource(if (bucket.partial) R.string.analytics_week_row_partial else R.string.analytics_week_row, start, end, value)
    val stateText = stringResource(if (expanded) R.string.progress_details_expanded else R.string.progress_details_collapsed)
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = if (expanded) FontWeight.Bold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.analytics_select_week), onClick = onToggle)
            .semantics { stateDescription = stateText }
            .padding(vertical = 12.dp)
    )
}

@Composable
private fun LinkRow(title: String, lines: List<String> = emptyList(), onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.history_open_workout), onClick = onClick)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        lines.forEach { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { content() }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface) },
        modifier = Modifier.heightIn(min = 48.dp).semantics { this.selected = selected; role = Role.RadioButton },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = CrimsonRedPrimary,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun Menu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<Pair<String, String>>,
    selectedId: String?,
    emptyText: String?,
    onSelect: (String) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        if (options.isEmpty() && emptyText != null) {
            DropdownMenuItem(text = { Text(emptyText, color = MaterialTheme.colorScheme.onSurface) }, onClick = onDismiss)
        }
        options.forEach { (id, label) ->
            DropdownMenuItem(
                text = {
                    Text(label, color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (id == selectedId) FontWeight.Bold else FontWeight.Normal)
                },
                onClick = { onDismiss(); onSelect(id) },
                modifier = Modifier.semantics { selected = id == selectedId; role = Role.RadioButton }
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun rememberDateFormat(zoneId: ZoneId): DateFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(zoneId, locale) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = TimeZone.getTimeZone(zoneId) }
    }
}

@Composable
private fun metricDefinition(metric: AnalyticsMetric, unit: WeightUnit): String = when (metric) {
    AnalyticsMetric.WORKOUTS -> stringResource(R.string.analytics_def_workouts)
    AnalyticsMetric.COMPLETED_SETS -> stringResource(R.string.progress_metric_sets_def)
    AnalyticsMetric.REPS -> stringResource(R.string.progress_metric_reps_def)
    AnalyticsMetric.EXTERNAL_LOAD_VOLUME -> stringResource(R.string.progress_metric_volume_def) + " " +
        stringResource(R.string.analytics_def_volume_units, unit.symbol)
    AnalyticsMetric.REVIEWED_PRIMARY_DOSE -> stringResource(R.string.progress_dose_policy)
}

/** Counts are whole numbers by construction; volume is rounded only here, for display. */
@Composable
private fun metricValue(metric: AnalyticsMetric, value: Double, unit: WeightUnit): String {
    val locale = LocalConfiguration.current.locales[0]
    val count = value.toInt()
    return when (metric) {
        AnalyticsMetric.WORKOUTS -> pluralStringResource(R.plurals.count_workouts, count, LocaleFormatting.formatCount(count, locale))
        AnalyticsMetric.COMPLETED_SETS, AnalyticsMetric.REVIEWED_PRIMARY_DOSE ->
            pluralStringResource(R.plurals.count_sets, count, LocaleFormatting.formatCount(count, locale))
        AnalyticsMetric.REPS -> pluralStringResource(R.plurals.count_reps, count, LocaleFormatting.formatCount(count, locale))
        AnalyticsMetric.EXTERNAL_LOAD_VOLUME ->
            stringResource(R.string.progress_volume_tonnage, LocaleFormatting.formatVolume(value, locale), unit.symbol)
    }
}

@Composable
private fun observationValue(shape: PerformanceShape, observation: PerformanceObservation, unit: WeightUnit): String {
    val locale = LocalConfiguration.current.locales[0]
    fun reps(value: Int) = LocaleFormatting.formatCount(value, locale)
    return when (shape) {
        PerformanceShape.WEIGHTED_REPS -> stringResource(R.string.progress_performance_weight_reps,
            LocaleFormatting.formatMeasurement(observation.value, locale), unit.symbol, reps(requireNotNull(observation.reps)))
        PerformanceShape.BODYWEIGHT_REPS -> pluralStringResource(R.plurals.count_reps, observation.value.toInt(), reps(observation.value.toInt()))
        PerformanceShape.ASSISTED_REPS -> {
            val repsText = pluralStringResource(R.plurals.count_reps, observation.value.toInt(), reps(observation.value.toInt()))
            observation.secondaryValue?.let {
                stringResource(R.string.analytics_value_assisted, repsText, LocaleFormatting.formatMeasurement(it, locale), unit.symbol)
            } ?: repsText
        }
        PerformanceShape.HOLD_DURATION, PerformanceShape.TIME_ONLY ->
            stringResource(R.string.prescription_seconds, LocaleFormatting.formatCount(observation.value.toInt(), locale))
        PerformanceShape.DISTANCE_ONLY -> stringResource(R.string.prescription_meters, LocaleFormatting.formatMeasurement(observation.value, locale))
        PerformanceShape.DISTANCE_AND_TIME -> stringResource(R.string.analytics_value_distance_time,
            stringResource(R.string.prescription_meters, LocaleFormatting.formatMeasurement(observation.value, locale)),
            stringResource(R.string.prescription_seconds, LocaleFormatting.formatCount(requireNotNull(observation.secondaryValue).toInt(), locale)))
    }
}

/** The recorded difference in the shape's own unit, with an explicit sign and no percentage. */
@Composable
private fun signedDifference(shape: PerformanceShape, difference: Double, unit: WeightUnit): String {
    val locale = LocalConfiguration.current.locales[0]
    val sign = when {
        difference > 0 -> "+"
        difference < 0 -> "−"
        else -> ""
    }
    val magnitude = kotlin.math.abs(difference)
    val text = when (shape) {
        PerformanceShape.WEIGHTED_REPS -> stringResource(R.string.progress_volume_value, LocaleFormatting.formatMeasurement(magnitude, locale), unit.symbol)
        PerformanceShape.BODYWEIGHT_REPS, PerformanceShape.ASSISTED_REPS ->
            pluralStringResource(R.plurals.count_reps, magnitude.toInt(), LocaleFormatting.formatCount(magnitude.toInt(), locale))
        PerformanceShape.HOLD_DURATION, PerformanceShape.TIME_ONLY ->
            stringResource(R.string.prescription_seconds, LocaleFormatting.formatCount(magnitude.toInt(), locale))
        PerformanceShape.DISTANCE_ONLY, PerformanceShape.DISTANCE_AND_TIME ->
            stringResource(R.string.prescription_meters, LocaleFormatting.formatMeasurement(magnitude, locale))
    }
    return sign + text
}

private fun AnalyticsMetric.labelRes(): Int = when (this) {
    AnalyticsMetric.WORKOUTS -> R.string.analytics_metric_workouts
    AnalyticsMetric.COMPLETED_SETS -> R.string.analytics_metric_sets
    AnalyticsMetric.REPS -> R.string.analytics_metric_reps
    AnalyticsMetric.EXTERNAL_LOAD_VOLUME -> R.string.analytics_metric_volume
    AnalyticsMetric.REVIEWED_PRIMARY_DOSE -> R.string.analytics_metric_dose
}

private fun PerformanceShape.labelRes(): Int = when (this) {
    PerformanceShape.WEIGHTED_REPS -> R.string.analytics_shape_weighted
    PerformanceShape.BODYWEIGHT_REPS -> R.string.analytics_shape_bodyweight
    PerformanceShape.ASSISTED_REPS -> R.string.analytics_shape_assisted
    PerformanceShape.HOLD_DURATION -> R.string.analytics_shape_hold
    PerformanceShape.DISTANCE_ONLY -> R.string.analytics_shape_distance
    PerformanceShape.TIME_ONLY -> R.string.analytics_shape_time
    PerformanceShape.DISTANCE_AND_TIME -> R.string.analytics_shape_distance_time
}
