package wallcrawl.elopenmike.com.feature.today

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.res.painterResource
import wallcrawl.elopenmike.com.R
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlWordmark
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.generatedWorkoutRationale
import wallcrawl.elopenmike.com.core.ui.localization.generatedWorkoutTitle
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.localization.unavailableFocusNotice
import wallcrawl.elopenmike.com.core.ui.localization.workoutRankingNotice
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreen
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onStartWorkout: (sessionId: String) -> Unit,
    onResumeWorkout: (sessionId: String) -> Unit,
    onOpenTemplates: () -> Unit,
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
            is TodayUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CrimsonRedPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.today_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is TodayUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // A workout already in progress stays reachable: this card is the only
                    // route back into it.
                    state.activeSession?.let { session ->
                        ActiveSessionBanner(
                            sessionName = session.name,
                            completedSets = session.completedSetsCount,
                            totalSets = session.totalSetsCount,
                            onResume = { onResumeWorkout(session.id) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    WallCrawlCard(borderColor = CrimsonRedPrimary) {
                        Text(
                            stringResource(R.string.today_error_title),
                            fontWeight = FontWeight.Bold,
                            color = CrimsonRedLight,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(state.error.messageRes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        WallCrawlPrimaryButton(
                            text = stringResource(R.string.action_try_again),
                            onClick = { viewModel.regenerateWorkout() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        WallCrawlSecondaryButton(
                            text = stringResource(R.string.today_action_open_templates),
                            onClick = onOpenTemplates
                        )
                    }
                }
            }

            is TodayUiState.Success -> {
                // Written here, where the reader's language is known, and handed to the
                // ViewModel so the session is stored with the wording that was on screen.
                val workoutName = generatedWorkoutTitle(state.suggestedWorkout.title)
                val workoutRationale = generatedWorkoutRationale(
                    spec = state.suggestedWorkout.rationale,
                    unavailableFocusMuscles = state.suggestedWorkout.unavailableFocusMuscles,
                    rankingReasons = state.suggestedWorkout.rankingReasons
                )
                TodayContent(
                    state = state,
                    workoutName = workoutName,
                    onStartWorkout = {
                        viewModel.startWorkout(workoutName, workoutRationale, onStartWorkout)
                    },
                    onResumeWorkout = { state.activeSession?.let { onResumeWorkout(it.id) } },
                    onRegenerate = { viewModel.regenerateWorkout() },
                    onOpenTemplates = onOpenTemplates
                )
            }
        }
    }
}

/**
 * The success body. Visible to the test source set so a screen test can render the real
 * card against a state it supplies, rather than asserting on a composable in isolation.
 */
@Composable
internal fun TodayContent(
    state: TodayUiState.Success,
    workoutName: String,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenTemplates: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            TodayHeader(
                userName = state.userProfile.name,
                completedThisWeek = state.completedThisWeek,
                weeklyGoal = state.userProfile.daysPerWeek
            )
        }

        // Active workout resumption banner if active
        if (state.activeSession != null) {
            item {
                ActiveSessionBanner(
                    sessionName = state.activeSession.name,
                    completedSets = state.activeSession.completedSetsCount,
                    totalSets = state.activeSession.totalSetsCount,
                    onResume = onResumeWorkout
                )
            }
        }

        // Main Recommended Workout Card
        item {
            SuggestedWorkoutCard(
                workout = state.suggestedWorkout,
                workoutName = workoutName,
                isRegenerating = state.isRegenerating,
                onStartWorkout = onStartWorkout,
                onRegenerate = onRegenerate
            )
        }

        item { MyWorkoutsCard(onOpenTemplates) }

        // Planning context pill
        item {
            PlanContextCard(
                goal = stringResource(state.userProfile.primaryGoal.labelRes),
                unit = state.userProfile.preferredUnit.symbol,
                equipmentCount = state.userProfile.availableEquipment.size
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MyWorkoutsCard(onOpenTemplates: () -> Unit) {
    WallCrawlCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CrimsonRedPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = CrimsonRedPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.today_custom_routines_heading),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = CrimsonRedPrimary
                )
                Text(
                    text = stringResource(R.string.today_my_workouts_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = stringResource(R.string.today_my_workouts_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        WallCrawlSecondaryButton(
            text = stringResource(R.string.today_action_open_templates),
            onClick = onOpenTemplates
        )
    }
}

@Composable
private fun TodayHeader(
    userName: String,
    completedThisWeek: Int,
    weeklyGoal: Int
) {
    val locale = LocalConfiguration.current.locales[0]
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                WallCrawlWordmark(fontSize = 20.sp)
                Spacer(modifier = Modifier.height(4.dp))
                val displayName = userName.lineSequence().firstOrNull()?.trim().orEmpty()
                    .ifBlank { stringResource(R.string.default_crawler_name) }
                Text(
                    text = stringResource(R.string.today_greeting, displayName),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, CrimsonRedPrimary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.today_logo_content_description),
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Weekly consistency mini progress
        WallCrawlCard(
            cornerRadius = 12.dp,
            contentPadding = 12.dp
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
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.today_weekly_progress,
                            weeklyGoal,
                            LocaleFormatting.formatCount(completedThisWeek, locale),
                            LocaleFormatting.formatCount(weeklyGoal, locale)
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val remaining = (weeklyGoal - completedThisWeek).coerceAtLeast(0)
                Text(
                    text = if (completedThisWeek >= weeklyGoal) {
                        stringResource(R.string.today_weekly_goal_met)
                    } else {
                        pluralStringResource(
                            R.plurals.today_weekly_goal_remaining,
                            remaining,
                            LocaleFormatting.formatCount(remaining, locale)
                        )
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (completedThisWeek.toFloat() / weeklyGoal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CrimsonRedPrimary,
                trackColor = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ActiveSessionBanner(
    sessionName: String,
    completedSets: Int,
    totalSets: Int,
    onResume: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    WallCrawlCard(
        borderColor = CrimsonRedPrimary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.today_active_session_heading),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonRedLight,
                    letterSpacing = 0.5.sp
                )
                // A session name is user-visible free text written when the workout
                // started. It is never re-translated afterwards.
                Text(
                    text = sessionName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.today_active_session_sets,
                        totalSets,
                        LocaleFormatting.formatCount(completedSets, locale),
                        LocaleFormatting.formatCount(totalSets, locale)
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            ButtonResume(onClick = onResume)
        }
    }
}

@Composable
private fun ButtonResume(onClick: () -> Unit) {
    WallCrawlPrimaryButton(
        text = stringResource(R.string.today_action_resume),
        onClick = onClick,
        modifier = Modifier.width(120.dp)
    )
}

@Composable
private fun SuggestedWorkoutCard(
    workout: GeneratedWorkout,
    workoutName: String,
    isRegenerating: Boolean,
    onStartWorkout: () -> Unit,
    onRegenerate: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    WallCrawlCard(
        cornerRadius = 20.dp,
        contentPadding = 20.dp,
        borderColor = CrimsonRedPrimary.copy(alpha = 0.4f)
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
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null,
                    tint = CrimsonRedLight,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.today_plan_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = CrimsonRedLight
                )
            }

            IconButton(
                onClick = onRegenerate,
                enabled = !isRegenerating,
                modifier = Modifier.size(32.dp)
            ) {
                if (isRegenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CrimsonRedPrimary)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(
                            R.string.today_regenerate_content_description
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = workoutName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = vocabulary.muscles(workout.focusMuscles)
                .joinToString(stringResource(R.string.detail_separator)),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Only when there is something to say. A prioritised muscle nothing available can
        // train is named here, so the alternative above is explained to the reader and not
        // only to the stored session; an ordinary session adds no line at all.
        unavailableFocusNotice(workout.unavailableFocusMuscles)?.let { notice ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notice,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        workoutRankingNotice(workout.rankingReasons)?.let { notice ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notice,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stat Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatBadge(
                label = stringResource(
                    R.string.today_stat_duration,
                    LocaleFormatting.formatCount(workout.estimatedDurationMinutes, locale)
                ),
                icon = Icons.Default.Schedule,
                textColor = MaterialTheme.colorScheme.secondary
            )
            StatBadge(
                label = pluralStringResource(
                    R.plurals.count_exercises,
                    workout.exercises.size,
                    LocaleFormatting.formatCount(workout.exercises.size, locale)
                ),
                icon = Icons.Default.FitnessCenter,
                textColor = MaterialTheme.colorScheme.onSurface
            )
            val totalSets = workout.exercises.sumOf { it.targetSets }
            StatBadge(
                label = pluralStringResource(
                    R.plurals.count_sets,
                    totalSets,
                    LocaleFormatting.formatCount(totalSets, locale)
                ),
                textColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Exercise List Preview
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.today_routine_heading),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            workout.exercises.forEachIndexed { index, exercise ->
                ExercisePreviewRow(index = index + 1, exercise = exercise)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large Start Workout CTA
        WallCrawlPrimaryButton(
            text = stringResource(R.string.today_action_start),
            onClick = onStartWorkout,
            enabled = !isRegenerating,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Action: Generate another workout
        WallCrawlSecondaryButton(
            text = stringResource(R.string.today_action_regenerate),
            onClick = onRegenerate,
            enabled = !isRegenerating
        )
    }
}

@Composable
private fun ExercisePreviewRow(
    index: Int,
    exercise: GeneratedExercise
) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    val name = vocabulary.exerciseName(exercise.exerciseId)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.today_exercise_position,
                    LocaleFormatting.formatCount(index, locale)
                ),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CrimsonRedPrimary,
                modifier = Modifier.width(24.dp)
            )
            Text(
                text = name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = prescriptionSummary(exercise),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The compact "3 × 8–12" style target shown beside a planned exercise. */
@Composable
private fun prescriptionSummary(exercise: GeneratedExercise): String {
    val locale = LocalConfiguration.current.locales[0]
    val prescription = exercise.prescription
    val sets = LocaleFormatting.formatCount(exercise.targetSets, locale)
    return when (prescription.exerciseType) {
        ExerciseType.WEIGHT_REPS,
        ExerciseType.BODYWEIGHT_REPS,
        ExerciseType.ASSISTED_BODYWEIGHT -> {
            val range = prescription.repRange
            val reps = if (range == null) {
                ""
            } else if (range.min == range.max) {
                LocaleFormatting.formatCount(range.min, locale)
            } else {
                stringResource(
                    R.string.prescription_rep_range,
                    LocaleFormatting.formatCount(range.min, locale),
                    LocaleFormatting.formatCount(range.max, locale)
                )
            }
            stringResource(R.string.prescription_sets_by_reps, sets, reps)
        }

        ExerciseType.DURATION -> stringResource(
            R.string.prescription_sets_by_reps,
            sets,
            stringResource(
                R.string.prescription_seconds,
                LocaleFormatting.formatCount(prescription.targetDurationSeconds ?: 0, locale)
            )
        )

        ExerciseType.DISTANCE_DURATION -> prescription.targetDurationSeconds?.let { seconds ->
            stringResource(
                R.string.prescription_minutes,
                LocaleFormatting.formatCount(seconds / 60, locale)
            )
        } ?: stringResource(
            R.string.prescription_meters,
            LocaleFormatting.formatCount(
                prescription.targetDistanceMeters?.toInt() ?: 0,
                locale
            )
        )
    }
}

@Composable
private fun PlanContextCard(
    goal: String,
    unit: String,
    equipmentCount: Int
) {
    val locale = LocalConfiguration.current.locales[0]
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
                    text = stringResource(R.string.today_plan_context_target, goal)
                        .uppercase(locale),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.template_summary,
                        pluralStringResource(
                            R.plurals.count_equipment_available,
                            equipmentCount,
                            LocaleFormatting.formatCount(equipmentCount, locale)
                        ),
                        unit
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.today_plan_context_offline),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
