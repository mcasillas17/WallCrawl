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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.ui.components.MetricHighlight
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteBorder
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurfaceElevated
import wallcrawl.elopenmike.com.core.ui.theme.ObsidianBlack
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreen
import wallcrawl.elopenmike.com.core.ui.theme.TextPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextSecondary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent

@Composable
fun WorkoutSummaryScreen(
    summary: WorkoutSummary,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
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
                        .background(GraphiteSurfaceElevated, CircleShape)
                        .border(2.dp, SuccessGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = SuccessGreen,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "WORKOUT COMPLETE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = CrimsonRedPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = summary.workoutName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite
                )
            }

            // Summary Metrics Grid
            WallCrawlCard(
                cornerRadius = 20.dp,
                contentPadding = 20.dp,
                borderColor = GraphiteBorder,
                backgroundColor = GraphiteSurfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "SESSION SUMMARY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricHighlight(
                        title = "Duration",
                        value = "${summary.durationMinutes} min",
                        valueColor = WebBlueAccent,
                        modifier = Modifier.weight(1f)
                    )

                    MetricHighlight(
                        title = "Sets Logged",
                        value = "${summary.totalSetsCompleted}",
                        valueColor = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val formattedVolume = if (summary.totalVolume > 0) {
                    "%,.0f %s".format(summary.totalVolume, summary.unit.symbol)
                } else {
                    "Bodyweight"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricHighlight(
                        title = "Volume Lifted",
                        value = formattedVolume,
                        valueColor = CrimsonRedLight,
                        modifier = Modifier.weight(1f)
                    )

                    MetricHighlight(
                        title = "PRs Hit",
                        value = "${summary.prCount} Records",
                        valueColor = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Done Action Button
            Column(modifier = Modifier.fillMaxWidth()) {
                WallCrawlPrimaryButton(
                    text = "Done",
                    onClick = onDone
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
