package wallcrawl.elopenmike.com.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting

@Composable
fun WorkoutSummaryScreen(
    summary: WorkoutSummary,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDetails: (() -> Unit)? = null,
    advancedPartialProgram: Boolean = false
) {
    val locale = LocalConfiguration.current.locales[0]
    val volume = if (summary.totalVolume > 0) {
        stringResource(R.string.progress_volume_tonnage,
            LocaleFormatting.formatVolume(summary.totalVolume, locale), summary.unit.symbol)
    } else stringResource(R.string.summary_no_external_volume)
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.summary_success_content_description),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(summary.workoutName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        WallCrawlCard(contentPadding = 12.dp) {
            SummaryMetric(stringResource(R.string.summary_metric_duration), stringResource(R.string.summary_duration_value,
                LocaleFormatting.formatCount(summary.durationMinutes, locale)))
            SummaryMetric(stringResource(R.string.summary_metric_sets), LocaleFormatting.formatCount(summary.totalSetsCompleted, locale))
            SummaryMetric(stringResource(R.string.summary_metric_volume), volume)
            if (summary.prCount > 0) SummaryMetric(stringResource(R.string.summary_metric_prs),
                LocaleFormatting.formatCount(summary.prCount, locale))
        }
        if (advancedPartialProgram) Text(stringResource(R.string.guided_summary_partial),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        WallCrawlPrimaryButton(stringResource(R.string.summary_action_done), onDone)
        if (onOpenDetails != null) TextButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.guided_view_workout), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
