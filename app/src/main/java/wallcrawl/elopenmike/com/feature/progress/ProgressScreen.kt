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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.StrengthPerformance
import wallcrawl.elopenmike.com.core.model.StrengthTrend
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.ui.components.MetricHighlight
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreen
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent
import java.text.DateFormat
import java.util.Date

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
                    Text(stringResource(state.messageRes), color = CrimsonRedLight)
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
                text = stringResource(R.string.progress_eyebrow),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = CrimsonRedPrimary
            )
            Text(
                text = stringResource(R.string.progress_title),
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
            StrengthTrendsSection(trends = overview.strengthTrends, unit = unit)
        }

        // Muscle Group Distribution
        item {
            MuscleFocusSection(overview = overview)
        }

        // Recent Workout History Header
        item {
            Text(
                text = stringResource(R.string.progress_history_heading),
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
                    Text(
                        stringResource(R.string.progress_history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
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
    val locale = LocalConfiguration.current.locales[0]
    WallCrawlCard(
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
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
                        text = pluralStringResource(
                            R.plurals.count_weeks_streak,
                            overview.currentStreakWeeks,
                            LocaleFormatting.formatCount(overview.currentStreakWeeks, locale)
                        ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.progress_workouts_this_week,
                            overview.workoutsThisWeek,
                            LocaleFormatting.formatCount(overview.workoutsThisWeek, locale)
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            StatBadge(
                label = stringResource(
                    R.string.progress_goal,
                    LocaleFormatting.formatCount(overview.weeklyGoal, locale)
                ),
                textColor = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricHighlight(
                title = stringResource(R.string.progress_total_workouts),
                value = LocaleFormatting.formatCount(overview.totalWorkoutsLogged, locale),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Tonnage alone reads as a flat zero for anyone training without external load,
            // and switching metric on a threshold would make one light set swing the card.
            // Reps are always shown alongside, so every week reports real work.
            val hasLoadedVolume = overview.totalVolumeThisWeek > 0.0
            val repsText = pluralStringResource(
                R.plurals.count_reps,
                overview.totalRepsThisWeek,
                LocaleFormatting.formatCount(overview.totalRepsThisWeek, locale)
            )
            MetricHighlight(
                title = stringResource(R.string.progress_weekly_volume),
                value = if (hasLoadedVolume) {
                    stringResource(
                        R.string.progress_volume_value,
                        LocaleFormatting.formatVolume(overview.totalVolumeThisWeek, locale),
                        unit
                    )
                } else {
                    repsText
                },
                subtitle = if (hasLoadedVolume) {
                    repsText
                } else {
                    stringResource(R.string.progress_no_weight_logged)
                },
                valueColor = CrimsonRedLight,
                modifier = Modifier.weight(1.3f)
            )
        }
    }
}

@Composable
private fun StrengthTrendsSection(
    trends: List<StrengthTrend>,
    unit: String
) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
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
                    text = stringResource(R.string.progress_strength_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = SuccessGreen
                )
            }
            Text(
                text = stringResource(R.string.progress_recent_changes),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (trends.isEmpty()) {
            Text(
                text = stringResource(R.string.progress_strength_empty),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vocabulary.exerciseName(trend.exerciseId, trend.exerciseName),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.progress_trend_change,
                            performanceLabel(trend.previous, unit),
                            performanceLabel(trend.current, unit)
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
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
                        text = stringResource(
                            R.string.progress_percentage,
                            LocaleFormatting.formatCount(trend.percentageChange, locale)
                        ),
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
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    WallCrawlCard(
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Text(
            text = stringResource(R.string.progress_muscle_heading),
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
                text = stringResource(R.string.progress_muscle_empty),
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
                            text = vocabulary.muscle(stat.muscle),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.count_sets,
                                stat.setsThisWeek,
                                LocaleFormatting.formatCount(stat.setsThisWeek, locale)
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (stat.percentageGrowth > 0) {
                            Text(
                                text = stringResource(
                                    R.string.progress_percentage_growth,
                                    LocaleFormatting.formatCount(stat.percentageGrowth, locale)
                                ),
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
    val locale = LocalConfiguration.current.locales[0]
    // Medium date plus short time, resolved by the platform for this locale rather than
    // pinned to an English "MMM d, yyyy" pattern.
    val dateStr = remember(session.startedAtTimestamp, locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            .format(Date(session.startedAtTimestamp))
    }

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

            Spacer(modifier = Modifier.width(8.dp))
            StatBadge(
                label = stringResource(
                    R.string.progress_history_duration,
                    LocaleFormatting.formatCount(session.actualDurationMinutes, locale)
                ),
                textColor = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.count_sets_logged,
                    session.completedSetsCount,
                    LocaleFormatting.formatCount(session.completedSetsCount, locale)
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (session.totalVolume > 0) {
                Text(
                    text = stringResource(
                        R.string.progress_history_volume,
                        LocaleFormatting.formatVolume(session.totalVolume, locale),
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

/**
 * One comparable set, written for the reader.
 *
 * Bodyweight work has no load, so it reads as a rep count rather than "0 kg × 8"; loaded
 * work reads with the locale's decimal mark.
 */
@Composable
private fun performanceLabel(performance: StrengthPerformance, unit: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val reps = LocaleFormatting.formatCount(performance.reps, locale)
    val weight = performance.weight
        ?: return pluralStringResource(R.plurals.count_reps, performance.reps, reps)
    return stringResource(
        R.string.progress_performance_weight_reps,
        LocaleFormatting.formatMeasurement(weight, locale),
        unit,
        reps
    )
}
