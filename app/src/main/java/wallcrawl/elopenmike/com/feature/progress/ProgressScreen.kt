package wallcrawl.elopenmike.com.feature.progress

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.MuscleProgressStat
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.StrengthPerformance
import wallcrawl.elopenmike.com.core.model.StrengthTrend
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
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

internal const val PROGRESS_REVIEWED_DOSE_TEST_TAG = "progress-reviewed-dose"

/**
 * Production entry point.
 *
 * State is collected only while the screen is RESUMED, so a backgrounded Progress screen is
 * not recomputing weekly totals. A resume, and a device time or time-zone change, each ask
 * the ViewModel for fresh calendar inputs — the week the numbers describe is a property of
 * the device clock, not of when the screen was first shown.
 */
@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // collectAsStateWithLifecycle is not on the classpath, so this is the same contract by
    // hand: collect on the RESUMED lifecycle and stop when it is not. The initial value is a
    // plain Loading rather than the flow's current value, which both satisfies lint and
    // avoids briefly showing a stale snapshot read off the composition thread.
    val uiState by produceState<ProgressUiState>(
        initialValue = ProgressUiState.Loading,
        viewModel,
        lifecycleOwner
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.uiState.collect { value = it }
        }
    }

    DisposableEffect(viewModel, context) {
        val clockChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.refresh()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            clockChangeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(clockChangeReceiver)
        }
    }

    ProgressScreen(uiState = uiState, onRetry = viewModel::refresh, modifier = modifier)
}

/**
 * Stateless surface, driven by an already-resolved [ProgressUiState].
 *
 * Kept separate from the ViewModel wrapper so an instrumentation test can render any state —
 * loaded, all-unattributed, empty, no-profile, or error — without a database or a clock.
 */
@Composable
fun ProgressScreen(
    uiState: ProgressUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
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

            is ProgressUiState.NoProfile -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.progress_no_profile),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            is ProgressUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(state.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.action_try_again))
                    }
                }
            }

            is ProgressUiState.Success -> ProgressContent(state)
        }
    }
}

@Composable
private fun ProgressContent(state: ProgressUiState.Success) {
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

        item { LoggedActivityCard(state) }

        item { ReviewedDoseCard(ledger = state.reviewedDose, previousLedger = state.previousReviewedDose) }

        item { MuscleInvolvementCard(stats = state.overview.legacyPrimaryActivity) }

        item {
            StrengthTrendsSection(
                trends = state.overview.strengthTrends,
                unit = state.preferredUnit.symbol
            )
        }

        item {
            Text(
                text = stringResource(R.string.progress_history_heading),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.overview.recentHistory.isEmpty()) {
            item {
                WallCrawlCard {
                    Text(
                        stringResource(R.string.progress_history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(state.overview.recentHistory) { session ->
                WorkoutHistoryCard(session = session)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/** Real logged activity, stated first and never as a reviewed dose. */
@Composable
private fun LoggedActivityCard(state: ProgressUiState.Success) {
    val overview = state.overview
    val unit = state.preferredUnit.symbol
    val locale = LocalConfiguration.current.locales[0]
    val weekLabels = rememberWeekLabels(state.week)
    val largeText = LocalDensity.current.fontScale > 1.3f
    val goalText = stringResource(R.string.progress_goal, LocaleFormatting.formatCount(overview.weeklyGoal, locale))
    var activityDetailsExpanded by remember { mutableStateOf(false) }

    WallCrawlCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
        Text(
            text = stringResource(R.string.progress_activity_heading),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Compact Monday-week range (it starts Monday) plus the zone the dates are read in.
        Text(
            text = stringResource(R.string.progress_activity_range, weekLabels.monday, weekLabels.sunday),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.progress_activity_timezone, state.week.zoneId.id),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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

            if (!largeText) {
                Spacer(modifier = Modifier.width(8.dp))
                StatBadge(label = goalText, textColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (largeText) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = goalText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (overview.workoutsThisWeek == 0) {
            Text(
                text = stringResource(R.string.progress_activity_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompletedSetsHighlight(overview, Modifier.fillMaxWidth())
                    WeeklyVolumeHighlight(overview, unit, Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompletedSetsHighlight(overview, Modifier.weight(1f))
                    WeeklyVolumeHighlight(overview, unit, Modifier.weight(1.3f))
                }
            }

            val workSets = overview.completedSetsThisWeek - overview.warmupSetsThisWeek
            if (overview.completedSetsThisWeek > 0 && workSets == 0 && overview.warmupSetsThisWeek > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.progress_activity_warmups_only),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = pluralStringResource(
                R.plurals.progress_all_time_workouts,
                overview.totalWorkoutsLogged,
                LocaleFormatting.formatCount(overview.totalWorkoutsLogged, locale)
            ),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DetailsToggle(
            label = stringResource(R.string.progress_activity_details),
            expanded = activityDetailsExpanded,
            onToggle = { activityDetailsExpanded = !activityDetailsExpanded }
        )
        if (activityDetailsExpanded) {
            ActivityMetricDefinition(stringResource(R.string.progress_metric_sets_def))
            ActivityMetricDefinition(stringResource(R.string.progress_metric_reps_def))
            ActivityMetricDefinition(stringResource(R.string.progress_metric_volume_def))
            ActivityMetricDefinition(stringResource(R.string.progress_streak_grace_note))
        }
    }
}

@Composable
private fun ActivityMetricDefinition(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun CompletedSetsHighlight(overview: ProgressOverview, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    MetricHighlight(
        title = stringResource(R.string.progress_completed_sets),
        value = LocaleFormatting.formatCount(overview.completedSetsThisWeek, locale),
        subtitle = if (overview.warmupSetsThisWeek > 0) {
            pluralStringResource(
                R.plurals.progress_warmup_count, overview.warmupSetsThisWeek,
                LocaleFormatting.formatCount(overview.warmupSetsThisWeek, locale)
            )
        } else null,
        valueColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
private fun WeeklyVolumeHighlight(
    overview: ProgressOverview,
    unit: String,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    // Tonnage alone reads as a flat zero for anyone training without external load, so reps
    // are always shown alongside and every week reports real work.
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
                R.string.progress_volume_tonnage,
                LocaleFormatting.formatVolume(overview.totalVolumeThisWeek, locale),
                unit
            )
        } else {
            repsText
        },
        subtitle = if (hasLoadedVolume) repsText else stringResource(R.string.progress_no_weight_logged),
        valueColor = CrimsonRedLight,
        modifier = modifier
    )
}

/**
 * Reviewed primary dose, kept visibly apart from broad activity. When nothing is credited —
 * the current production state, with no approved metadata — the card says the work is logged
 * but needs approved data, rather than showing zero as if nothing happened.
 */
@Composable
private fun ReviewedDoseCard(ledger: WeeklyDoseLedger, previousLedger: WeeklyDoseLedger) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    var expanded by remember { mutableStateOf(false) }

    val credited = ledger.creditedWorkSets
    val omitted = ledger.omittedWorkSets

    WallCrawlCard(
        modifier = Modifier.testTag(PROGRESS_REVIEWED_DOSE_TEST_TAG),
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Text(
            text = stringResource(R.string.progress_dose_heading),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (credited + omitted == 0) {
            Text(
                text = stringResource(R.string.progress_dose_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        if (credited > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.progress_dose_credited,
                    credited,
                    LocaleFormatting.formatCount(credited, locale)
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (omitted > 0) {
            if (credited > 0) Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.progress_dose_unattributed),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DetailsToggle(
            label = stringResource(R.string.progress_dose_details),
            expanded = expanded,
            onToggle = { expanded = !expanded }
        )

        if (expanded) {
            Text(
                text = pluralStringResource(
                    R.plurals.progress_dose_previous_credited,
                    previousLedger.creditedWorkSets,
                    LocaleFormatting.formatCount(previousLedger.creditedWorkSets, locale)
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.progress_involvement_partial_note),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.progress_dose_policy),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (ledger.directPrimarySets.isNotEmpty()) {
                DetailHeading(stringResource(R.string.progress_dose_direct_heading))
                ledger.directPrimarySets.forEach { (muscle, count) ->
                    LabelledCountRow(label = vocabulary.muscle(muscle), count = count)
                }
            }

            if (ledger.secondaryInvolvement.isNotEmpty()) {
                DetailHeading(stringResource(R.string.progress_dose_secondary_heading))
                Text(
                    text = stringResource(R.string.progress_dose_secondary_note),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ledger.secondaryInvolvement.forEach { (muscle, count) ->
                    LabelledCountRow(label = vocabulary.muscle(muscle), count = count)
                }
            }

            if (ledger.unattributedWorkSets.isNotEmpty()) {
                DetailHeading(stringResource(R.string.progress_dose_omissions_heading))
                ledger.unattributedWorkSets.forEach { (reason, count) ->
                    LabelledCountRow(label = stringResource(reason.labelRes()), count = count)
                }
            }
        }
    }
}

/** Broad legacy-primary involvement, labelled apart from dose and flagged non-additive. */
@Composable
private fun MuscleInvolvementCard(stats: List<MuscleProgressStat>) {
    val vocabulary = LocalExerciseVocabulary.current
    var expanded by remember { mutableStateOf(false) }

    WallCrawlCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
        Text(
            text = stringResource(R.string.progress_involvement_heading),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.progress_involvement_note),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (stats.none { it.setsThisWeek > 0 }) {
            Text(
                text = stringResource(R.string.progress_involvement_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        if (stats.isEmpty()) return@WallCrawlCard

        // Collapsed shows what was actually trained this week; the full union, including any
        // reductions from last week, lives in the disclosure.
        stats.filter { it.setsThisWeek > 0 }.forEach { stat ->
            LabelledCountRow(label = vocabulary.muscle(stat.muscle), count = stat.setsThisWeek)
        }

        DetailsToggle(
            label = stringResource(R.string.progress_involvement_details),
            expanded = expanded,
            onToggle = { expanded = !expanded }
        )

        if (expanded) {
            Text(
                text = stringResource(R.string.progress_involvement_partial_note),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            stats.forEach { stat -> InvolvementDetailRow(stat = stat) }
        }
    }
}

@Composable
private fun InvolvementDetailRow(stat: MuscleProgressStat) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = vocabulary.muscle(stat.muscle),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pluralStringResource(
                    R.plurals.count_sets,
                    stat.setsThisWeek,
                    LocaleFormatting.formatCount(stat.setsThisWeek, locale)
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val change = stat.percentageChange
        if (change == null) {
            Text(
                text = stringResource(R.string.progress_involvement_new),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Neutral, theme-aware colour; the signed number already carries the direction,
            // so this stays legible on both light and dark surfaces.
            val signed = (if (change >= 0) "+" else "") + LocaleFormatting.formatCount(change, locale)
            Text(
                text = stringResource(R.string.progress_involvement_change, signed),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailHeading(text: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LabelledCountRow(label: String, count: Int) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = pluralStringResource(
                R.plurals.count_sets,
                count,
                LocaleFormatting.formatCount(count, locale)
            ),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailsToggle(label: String, expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(
        if (expanded) R.string.progress_details_expanded else R.string.progress_details_collapsed
    )
    // A native button so the touch target is at least 48dp; the section-specific label plus a
    // resource-backed expanded/collapsed state description make each disclosure distinct and
    // announceable rather than three anonymous "Show details" rows.
    TextButton(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Button
                stateDescription = state
            }
    ) {
        // The shared typography specifies white, so an explicit colour is needed on light cards.
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun StrengthTrendsSection(
    trends: List<StrengthTrend>,
    unit: String
) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    WallCrawlCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun WorkoutHistoryCard(session: WorkoutSession) {
    val locale = LocalConfiguration.current.locales[0]
    // Medium date plus short time, resolved by the platform for this locale rather than
    // pinned to an English "MMM d, yyyy" pattern.
    val dateStr = remember(session.startedAtTimestamp, locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            .format(Date(session.startedAtTimestamp))
    }

    WallCrawlCard(cornerRadius = 12.dp, contentPadding = 12.dp) {
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

private class WeekLabels(val monday: String, val sunday: String)

/** Monday and Sunday of [week], formatted in the week's own zone and the reader's locale. */
@Composable
private fun rememberWeekLabels(week: TrainingWeek): WeekLabels {
    val locale = LocalConfiguration.current.locales[0]
    return remember(week, locale) {
        val format = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply {
            timeZone = TimeZone.getTimeZone(week.zoneId)
        }
        WeekLabels(
            monday = format.format(Date(week.startEpochMillis)),
            sunday = format.format(Date(week.endEpochMillisExclusive - 1))
        )
    }
}

private fun LedgerOmissionReason.labelRes(): Int = when (this) {
    LedgerOmissionReason.UNKNOWN_EXERCISE -> R.string.progress_omission_unknown_exercise
    LedgerOmissionReason.MISSING_REVIEWED_METADATA -> R.string.progress_omission_missing_metadata
    LedgerOmissionReason.METADATA_NOT_APPROVED -> R.string.progress_omission_not_approved
}
