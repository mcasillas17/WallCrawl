package wallcrawl.elopenmike.com.feature.today

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.graphics.Color
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
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlOutlinedButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.SuccessGreen
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent

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
                        Text("Building today's plan...", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Generation Issue", fontWeight = FontWeight.Bold, color = CrimsonRedLight, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        WallCrawlPrimaryButton(text = "Try Again", onClick = { viewModel.regenerateWorkout() })
                        Spacer(modifier = Modifier.height(8.dp))
                        WallCrawlSecondaryButton(text = "Open My Workouts", onClick = onOpenTemplates)
                    }
                }
            }

            is TodayUiState.Success -> {
                TodayContent(
                    state = state,
                    onStartWorkout = { viewModel.startWorkout(onStartWorkout) },
                    onResumeWorkout = { state.activeSession?.let { onResumeWorkout(it.id) } },
                    onRegenerate = { viewModel.regenerateWorkout() },
                    onOpenTemplates = onOpenTemplates
                )
            }
        }
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState.Success,
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
            TodayHeader(userName = state.userProfile.name, completedThisWeek = state.completedThisWeek, weeklyGoal = state.userProfile.daysPerWeek)
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
                isRegenerating = state.isRegenerating,
                onStartWorkout = onStartWorkout,
                onRegenerate = onRegenerate
            )
        }

        item { MyWorkoutsCard(onOpenTemplates) }

        // Planning context pill
        item {
            PlanContextCard(
                goal = state.userProfile.primaryGoal.displayName,
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
                    text = "CUSTOM ROUTINES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = CrimsonRedPrimary
                )
                Text(
                    text = "My Workouts",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Build, save, and repeat your custom routines.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        WallCrawlSecondaryButton(text = "Open My Workouts", onClick = onOpenTemplates)
    }
}

@Composable
private fun TodayHeader(
    userName: String,
    completedThisWeek: Int,
    weeklyGoal: Int
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                WallCrawlWordmark(fontSize = 20.sp)
                Spacer(modifier = Modifier.height(4.dp))
                val displayName = userName.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Crawler" }
                Text(
                    text = "Ready to train, $displayName",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
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
                    contentDescription = "WallCrawl Logo",
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (weeklyGoal == 1) {
                            "$completedThisWeek of 1 workout completed this week"
                        } else {
                            "$completedThisWeek of $weeklyGoal workouts completed this week"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (completedThisWeek >= weeklyGoal) {
                        "Weekly goal met"
                    } else {
                        val remaining = (weeklyGoal - completedThisWeek).coerceAtLeast(0)
                        if (remaining == 1) "1 to weekly goal" else "$remaining to weekly goal"
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
                    text = "ACTIVE WORKOUT IN PROGRESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonRedLight,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = sessionName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (totalSets == 1) "$completedSets of 1 set logged" else "$completedSets of $totalSets sets logged",
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
        text = "Resume",
        onClick = onClick,
        modifier = Modifier.width(110.dp)
    )
}

@Composable
private fun SuggestedWorkoutCard(
    workout: GeneratedWorkout,
    isRegenerating: Boolean,
    onStartWorkout: () -> Unit,
    onRegenerate: () -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null,
                    tint = CrimsonRedLight,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TODAY'S PLAN",
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
                        contentDescription = "Regenerate Workout",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = workout.name,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = workout.focusMuscles.joinToString(" · "),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Stat Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatBadge(
                label = "~${workout.estimatedDurationMinutes} min",
                icon = Icons.Default.Schedule,
                textColor = MaterialTheme.colorScheme.secondary
            )
            StatBadge(
                label = "${workout.exercises.size} exercises",
                icon = Icons.Default.FitnessCenter,
                textColor = MaterialTheme.colorScheme.onSurface
            )
            val totalSets = workout.exercises.sumOf { it.targetSets }
            StatBadge(
                label = "$totalSets sets",
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
                text = "WORKOUT ROUTINE",
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
            text = "Start Workout",
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
            text = "Generate another workout",
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
    val cleanName = exercise.exerciseId
        .split("-")
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

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
                text = "$index.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CrimsonRedPrimary,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = cleanName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = when (exercise.prescription.exerciseType) {
                ExerciseType.WEIGHT_REPS,
                ExerciseType.BODYWEIGHT_REPS,
                ExerciseType.ASSISTED_BODYWEIGHT ->
                    "${exercise.targetSets} × ${exercise.prescription.repRange}"
                ExerciseType.DURATION ->
                    "${exercise.targetSets} × ${exercise.prescription.targetDurationSeconds}s"
                ExerciseType.DISTANCE_DURATION ->
                    exercise.prescription.targetDurationSeconds?.let { "${it / 60} min" }
                        ?: "${exercise.prescription.targetDistanceMeters?.toInt()} m"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanContextCard(
    goal: String,
    unit: String,
    equipmentCount: Int
) {
    WallCrawlCard(
        cornerRadius = 12.dp,
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TARGETING: $goal".uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$equipmentCount Equipment Types Available · $unit",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Built on your phone",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
