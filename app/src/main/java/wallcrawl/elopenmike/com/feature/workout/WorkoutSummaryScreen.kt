package wallcrawl.elopenmike.com.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.components.MetricHighlight
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreen

@Composable
fun WorkoutSummaryScreen(
    summary: WorkoutSummary,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WebBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Celebration Icon & Title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(2.dp, SuccessGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(
                            R.string.summary_success_content_description
                        ),
                        tint = SuccessGreen,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.summary_eyebrow),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = CrimsonRedPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                // The name recorded when this workout started, shown as written.
                Text(
                    text = summary.workoutName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Summary Metrics Grid
            WallCrawlCard(
                cornerRadius = 20.dp,
                contentPadding = 20.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.summary_section),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricHighlight(
                        title = stringResource(R.string.summary_metric_duration),
                        value = stringResource(
                            R.string.summary_duration_value,
                            LocaleFormatting.formatCount(summary.durationMinutes, locale)
                        ),
                        valueColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )

                    MetricHighlight(
                        title = stringResource(R.string.summary_metric_sets),
                        value = LocaleFormatting.formatCount(summary.totalSetsCompleted, locale),
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val formattedVolume = if (summary.totalVolume > 0) {
                    stringResource(
                        R.string.progress_volume_value,
                        LocaleFormatting.formatVolume(summary.totalVolume, locale),
                        summary.unit.symbol
                    )
                } else {
                    stringResource(R.string.summary_bodyweight)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricHighlight(
                        title = stringResource(R.string.summary_metric_volume),
                        value = formattedVolume,
                        valueColor = CrimsonRedLight,
                        modifier = Modifier.weight(1f)
                    )

                    MetricHighlight(
                        title = stringResource(R.string.summary_metric_prs),
                        value = if (summary.prCount == 0) {
                            stringResource(R.string.value_not_available)
                        } else {
                            LocaleFormatting.formatCount(summary.prCount, locale)
                        },
                        valueColor = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Done Action Button
            Column(modifier = Modifier.fillMaxWidth()) {
                WallCrawlPrimaryButton(
                    text = stringResource(R.string.summary_action_done),
                    onClick = onDone
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
