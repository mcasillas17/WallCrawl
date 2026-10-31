package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

const val ANALYTICS_CHART_TEST_TAG = "analytics-bar-chart"

/**
 * A minimal bar chart: one bar per value, scaled to the largest, with [hatched] bars drawn as
 * an outline with diagonal lines so a partial week is not marked by colour alone.
 *
 * It is a picture with a text [description], not an interactive control: every value, and every
 * drill-down, lives in the data list beside it. Nothing is resampled, so the caller bounds the
 * number of values (at most 52 weekly buckets).
 */
@Composable
fun AnalyticsBarChart(
    values: List<Double>,
    description: String,
    modifier: Modifier = Modifier,
    hatched: Set<Int> = emptySet()
) {
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline
    val maximum = values.filter(Double::isFinite).maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .testTag(ANALYTICS_CHART_TEST_TAG)
            .semantics { contentDescription = description }
    ) {
        val slot = size.width / values.size.coerceAtLeast(1)
        val gap = (slot * 0.2f).coerceAtMost(8.dp.toPx())
        val stroke = 1.5.dp.toPx()
        values.forEachIndexed { index, value ->
            val height = (value.takeIf(Double::isFinite) ?: 0.0).div(maximum).toFloat() * size.height
            val left = index * slot + gap / 2
            val top = size.height - height
            val barSize = Size(slot - gap, height)
            if (index in hatched) {
                drawRect(barColor, Offset(left, top), barSize, style = Stroke(stroke))
                clipRect(left, top, left + barSize.width, size.height) {
                    var x = left - size.height
                    while (x < left + barSize.width) {
                        drawLine(barColor, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
                        x += 6.dp.toPx()
                    }
                }
            } else {
                drawRect(barColor, Offset(left, top), barSize)
            }
        }
        drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), stroke)
    }
}
