package wallcrawl.elopenmike.com.feature.progress

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.PerformanceSeries
import wallcrawl.elopenmike.com.core.model.PerformanceShape
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting

@Composable
internal fun ExerciseHistoryChart(
    state: ExerciseHistoryUiState.Loaded,
    series: PerformanceSeries,
    dateFormat: DateTimeFormatter,
    locale: Locale
) {
    val points = series.observations
    var selectedSession by rememberSaveable { mutableStateOf<String?>(null) }
    val index = remember(points, selectedSession) {
        points.indexOfFirst { it.sessionId == selectedSession }.takeIf { it >= 0 } ?: points.lastIndex
    }
    val point = points[index]
    val onSelect: (Int) -> Unit = { selectedSession = points[it].sessionId }
    val weighted = series.shape == PerformanceShape.WEIGHTED_REPS
    val fractions = remember(points, state.start, state.end, state.zoneId) {
        val start = state.start.atStartOfDay(state.zoneId).toInstant().toEpochMilli()
        val endExclusive = state.end.plusDays(1).atStartOfDay(state.zoneId).toInstant().toEpochMilli()
        FloatArray(points.size) { i ->
            if (points.size == 1 && state.start == state.end) 0.5f else
                ((points[i].completedAtTimestamp - start).toDouble() / (endExclusive - start)).toFloat().coerceIn(0f, 1f)
        }
    }
    val timestamp = Instant.ofEpochMilli(point.completedAtTimestamp).atZone(state.zoneId)
    val sameDay = remember(points, index, state.zoneId) {
        listOfNotNull(points.getOrNull(index - 1), points.getOrNull(index + 1)).any {
            Instant.ofEpochMilli(it.completedAtTimestamp).atZone(state.zoneId).toLocalDate() == timestamp.toLocalDate()
        }
    }
    val selectedDate = if (sameDay) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).format(timestamp) else dateFormat.format(timestamp)
    val primary = MaterialTheme.colorScheme.primary
    WallCrawlCard(Modifier.fillMaxWidth().testTag("exercise-history-chart"), borderColor = MaterialTheme.colorScheme.outlineVariant) {
        if (series.shape.charted) {
            val metrics = remember(points, series.shape) {
                buildList {
                    add(points.map { it.value })
                    if (weighted) add(points.map { requireNotNull(it.reps).toDouble() })
                }
            }
            val scaleLabels = remember(metrics, locale) {
                metrics.map { LocaleFormatting.formatMeasurement(it.max(), locale) }
            }
            val measure = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelSmall
            val axisWidth = with(LocalDensity.current) {
                scaleLabels.maxOf { measure.measure(it, style).size.width }.toDp() + 6.dp
            }
            metrics.forEachIndexed { axis, values ->
                val (label, readout) = when {
                    weighted && axis == 0 -> stringResource(R.string.history_chart_weight) to
                        stringResource(R.string.progress_volume_value, LocaleFormatting.formatMeasurement(point.value, locale), state.preferredUnit.symbol)
                    weighted || series.shape == PerformanceShape.BODYWEIGHT_REPS -> stringResource(R.string.analytics_metric_reps) to
                        LocaleFormatting.formatCount(values[index].toInt(), locale)
                    else -> stringResource(if (series.shape == PerformanceShape.DISTANCE_ONLY) R.string.history_chart_distance
                        else R.string.summary_metric_duration) to observationValue(series.shape, point, state.preferredUnit)
                }
                Plot(label, readout, selectedDate, values, fractions, index, onSelect,
                    color = if (axis == 0) primary else MaterialTheme.colorScheme.onSurface,
                    scaleLabel = scaleLabels[axis], zero = LocaleFormatting.formatCount(0, locale), axisWidth = axisWidth,
                    height = if (weighted) 112.dp else 210.dp)
            }
            Row(Modifier.fillMaxWidth().padding(start = axisWidth), horizontalArrangement = Arrangement.SpaceBetween) {
                if (state.start == state.end) {
                    AxisText(dateFormat.format(state.start), Modifier.fillMaxWidth(), TextAlign.Center)
                } else {
                    AxisText(dateFormat.format(state.start), Modifier.weight(1f))
                    AxisText(dateFormat.format(state.end), Modifier.weight(1f), TextAlign.End)
                }
            }
        } else {
            Text(stringResource(if (series.shape == PerformanceShape.ASSISTED_REPS) R.string.analytics_shape_assisted
                else R.string.analytics_shape_distance_time), style = MaterialTheme.typography.labelMedium)
            Text(observationValue(series.shape, point, state.preferredUnit), style = MaterialTheme.typography.bodyMedium)
        }
        if (points.size > 1 || state.start != state.end || !series.shape.charted) {
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (points.size > 1) IconButton(onClick = { onSelect(index - 1) }, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.history_chart_previous))
                }
                Text(selectedDate, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                if (points.size > 1) IconButton(onClick = { onSelect(index + 1) }, enabled = index < points.lastIndex) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.history_chart_next))
                }
            }
        }
    }
}

@Composable
private fun AxisText(label: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(label, modifier, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align)
}

@Composable
private fun Plot(
    label: String, readout: String, date: String,
    values: List<Double>, fractions: FloatArray, selected: Int, onSelect: (Int) -> Unit,
    color: Color, scaleLabel: String, zero: String, axisWidth: Dp, height: Dp
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(readout, style = MaterialTheme.typography.labelLarge)
    }
    val selectedDescription = stringResource(R.string.history_chart_selected_point, date, readout)
    val seriesMax = remember(values) { max(values.max(), 0.000001) }
    val latestSelection by rememberUpdatedState(onSelect)
    val selectedIndex = rememberUpdatedState(selected)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val inset = with(LocalDensity.current) { 6.dp.toPx() }
    fun position(i: Int, size: Size) = Offset(
        inset + fractions[i] * (size.width - 2 * inset),
        size.height - inset - (values[i] / seriesMax).toFloat() * (size.height - 2 * inset)
    )
    Row(Modifier.fillMaxWidth().height(height).padding(vertical = 6.dp)) {
        Column(Modifier.width(axisWidth).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            AxisText(scaleLabel)
            AxisText(zero)
        }
        Box(Modifier.weight(1f).fillMaxHeight()
            .semantics {
                contentDescription = label
                stateDescription = selectedDescription
                if (values.size > 1) {
                    progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..values.lastIndex.toFloat(), values.size - 2)
                    setProgress { position ->
                        latestSelection(position.roundToInt().coerceIn(values.indices))
                        true
                    }
                }
            }
            .pointerInput(fractions, values, inset) {
                detectTapGestures { tap ->
                    val bounds = size.toSize()
                    latestSelection(fractions.indices.minBy { (position(it, bounds) - tap).getDistanceSquared() })
                }
            }
            .drawWithCache {
                val bottom = size.height - inset
                val line = Path().apply {
                    val first = position(0, size)
                    moveTo(first.x, first.y)
                    for (i in 1..values.lastIndex) {
                        val next = position(i, size)
                        lineTo(next.x, next.y)
                    }
                }
                onDrawBehind {
                    for (y in listOf(inset, bottom)) drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                    val current = position(selectedIndex.value, size)
                    drawLine(grid, Offset(current.x, inset), Offset(current.x, bottom), 1.dp.toPx())
                    drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
                    if (values.size <= size.width / 10.dp.toPx()) {
                        for (i in values.indices) {
                            val center = position(i, size)
                            drawCircle(color, 2.75.dp.toPx(), center)
                            drawCircle(surface, 1.25.dp.toPx(), center)
                        }
                    }
                    drawCircle(color, 5.dp.toPx(), current)
                }
            })
    }
}
