package wallcrawl.elopenmike.com.feature.progress

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.StrengthTrend
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.ui.components.MetricHighlight
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreen
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WebBackgroundPattern()

        when (val state = uiState) {
            is ProgressUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrimsonRedPrimary)
                }
            }

            is ProgressUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = CrimsonRedLight)
                }
            }

            is ProgressUiState.Success -> {
                ProgressContent(overview = state.overview, unit = state.preferredUnit.symbol)
            }
        }
    }
}

@Composable
private fun ProgressContent(
    overview: ProgressOverview,
    unit: String
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "PROGRESS",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = CrimsonRedPrimary
            )
            Text(
                text = "Consistency & Performance",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Streak & Weekly Summary
        item {
            StreakAndVolumeCard(overview = overview, unit = unit)
        }

        // Strength Trends Section
        item {
            StrengthTrendsSection(trends = overview.strengthTrends)
        }

        // Muscle Group Distribution
        item {
            MuscleFocusSection(overview = overview)
        }

        // Recent Workout History Header
        item {
            Text(
                text = "WORKOUT HISTORY",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Recent Workout History Items
        if (overview.recentHistory.isEmpty()) {
            item {
                WallCrawlCard {
                    Text("No past workouts logged yet. Complete your first workout to see history here.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        } else {
            items(overview.recentHistory) { session ->
                WorkoutHistoryCard(session = session)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StreakAndVolumeCard(
    overview: ProgressOverview,
    unit: String
) {
    WallCrawlCard(
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0x22E63946), CircleShape)
                        .border(1.dp, CrimsonRedPrimary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = CrimsonRedLight,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${overview.currentStreakWeeks}-Week Streak",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${overview.workoutsThisWeek} workouts logged this week",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            StatBadge(
                label = "Goal: ${overview.weeklyGoal}/wk",
                textColor = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricHighlight(
                title = "Total Workouts",
                value = "${overview.totalWorkoutsLogged}",
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Tonnage alone reads as a flat zero for anyone training without external load,
            // and switching metric on a threshold would make one light set swing the card.
            // Reps are always shown alongside, so every week reports real work.
            val hasLoadedVolume = overview.totalVolumeThisWeek > 0.0
            MetricHighlight(
                title = "Weekly Volume",
                value = if (hasLoadedVolume) {
                    "%,.0f %s".format(overview.totalVolumeThisWeek, unit)
                } else {
                    "%,d reps".format(overview.totalRepsThisWeek)
                },
                subtitle = if (hasLoadedVolume) {
                    "%,d reps".format(overview.totalRepsThisWeek)
                } else {
                    "no weight logged"
                },
                valueColor = CrimsonRedLight,
                modifier = Modifier.weight(1.3f)
            )
        }
    }
}

@Composable
private fun StrengthTrendsSection(
    trends: List<StrengthTrend>
) {
    WallCrawlCard(
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "STRENGTH PROGRESSION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = SuccessGreen
                )
            }
            Text(
                text = "Recent changes",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (trends.isEmpty()) {
            Text(
                text = "Log the same exercise in two workouts to see how your strength is trending.",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }

        trends.forEach { trend ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = trend.exerciseName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${trend.previousMetric} → ${trend.currentMetric}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val trendColor = if (trend.isPositive) SuccessGreen else CrimsonRedLight
                    Icon(
                        imageVector = if (trend.isPositive) {
                            Icons.Default.ArrowUpward
                        } else {
                            Icons.Default.ArrowDownward
                        },
                        contentDescription = null,
                        tint = trendColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${trend.percentageChange}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                }
            }
        }
    }
}

@Composable
private fun MuscleFocusSection(
    overview: ProgressOverview
) {
    WallCrawlCard(
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Text(
            text = "WEEKLY MUSCLE FOCUS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (overview.muscleGroupFocus.isEmpty()) {
            // Reachable for a week of stretching or cardio alone: neither counts as
            // training volume, so there is nothing to attribute sets to.
            Text(
                text = "Complete a strength set this week to see which muscles you're hitting.",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            overview.muscleGroupFocus.forEach { stat ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stat.muscle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${stat.setsThisWeek} sets",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (stat.percentageGrowth > 0) {
                            Text(
                                text = "↑ ${stat.percentageGrowth}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutHistoryCard(
    session: WorkoutSession
) {
    val dateStr = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        .format(Date(session.startedAtTimestamp))

    WallCrawlCard(
        cornerRadius = 12.dp,
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateStr,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StatBadge(
                label = "${session.actualDurationMinutes} min",
                textColor = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "${session.completedSetsCount} sets logged",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (session.totalVolume > 0) {
                Text(
                    text = "%,.0f %s volume".format(
                        session.totalVolume,
                        session.weightUnit.symbol
                    ),
                    fontSize = 12.sp,
                    color = CrimsonRedLight,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
