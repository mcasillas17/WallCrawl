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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.ui.components.ExerciseVisual
import wallcrawl.elopenmike.com.core.ui.components.SetRow
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlOutlinedButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteBorder
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurface
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurfaceElevated
import wallcrawl.elopenmike.com.core.ui.theme.ObsidianBlack
import wallcrawl.elopenmike.com.core.ui.theme.TextMuted
import wallcrawl.elopenmike.com.core.ui.theme.TextPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextSecondary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent

@Composable
fun ActiveWorkoutScreen(
    viewModel: ActiveWorkoutViewModel,
    onNavigateBack: () -> Unit,
    onWorkoutFinished: (summarySessionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        WebBackgroundPattern()

        when (val state = uiState) {
            is ActiveWorkoutUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrimsonRedPrimary)
                }
            }

            is ActiveWorkoutUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WallCrawlCard(borderColor = CrimsonRedPrimary) {
                        Text("Workout Error", color = CrimsonRedLight, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message, color = TextSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        WallCrawlSecondaryButton(text = "Go Back", onClick = onNavigateBack)
                    }
                }
            }

            is ActiveWorkoutUiState.Completed -> {
                WorkoutSummaryScreen(
                    summary = state.summary,
                    onDone = { onWorkoutFinished(state.summary.sessionId) }
                )
            }

            is ActiveWorkoutUiState.Active -> {
                ActiveWorkoutContent(
                    state = state,
                    onPreviousExercise = { viewModel.previousExercise() },
                    onNextExercise = { viewModel.nextExercise() },
                    onUpdateSet = { setId, reps, weight, isCompleted ->
                        viewModel.updateSet(setId, reps, weight, isCompleted)
                    },
                    onFinishWorkout = { viewModel.finishWorkout() },
                    onClose = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    state: ActiveWorkoutUiState.Active,
    onPreviousExercise: () -> Unit,
    onNextExercise: () -> Unit,
    onUpdateSet: (setId: String, reps: Int?, weight: Double?, isCompleted: Boolean) -> Unit,
    onFinishWorkout: () -> Unit,
    onClose: () -> Unit
) {
    val currentExercise = state.currentExercise

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Workout",
                    tint = TextSecondary
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.session.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Text(
                    text = "Exercise ${state.currentExerciseIndex + 1} of ${state.totalExercises}",
                    fontSize = 12.sp,
                    color = CrimsonRedLight,
                    fontWeight = FontWeight.SemiBold
                )
            }

            WallCrawlOutlinedButton(
                text = "Finish",
                onClick = onFinishWorkout,
                modifier = Modifier
                    .width(80.dp)
                    .height(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (currentExercise != null) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Exercise Visual Placeholder (Workout Guide integration target)
                item {
                    ExerciseVisual(exercise = state.currentCatalogExercise)
                }

                // 2. Exercise Title & Details Card
                item {
                    ExerciseHeaderCard(
                        workoutExercise = currentExercise,
                        preferredUnit = state.preferredUnit.symbol
                    )
                }

                // 3. Previous Performance Reference Card
                if (state.previousHistory.isNotEmpty()) {
                    item {
                        PreviousPerformanceCard(history = state.previousHistory)
                    }
                }

                // 4. Sets Logging Table Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SET",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.width(32.dp)
                        )
                        Text(
                            text = "WEIGHT (${state.preferredUnit.symbol.uppercase()})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "REPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "DONE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.width(38.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // 5. Editable Sets Rows
                items(currentExercise.sets.size) { setIndex ->
                    val set = currentExercise.sets[setIndex]
                    SetRow(
                        set = set,
                        weightUnit = state.preferredUnit.symbol,
                        onUpdateSet = { reps, weight, isCompleted ->
                            onUpdateSet(set.id, reps, weight, isCompleted)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Bottom Navigation Buttons (Previous / Next / Finish)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!state.isFirstExercise) {
                WallCrawlSecondaryButton(
                    text = "Previous",
                    onClick = onPreviousExercise,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!state.isLastExercise) {
                WallCrawlPrimaryButton(
                    text = "Next Exercise",
                    onClick = onNextExercise,
                    modifier = Modifier.weight(if (state.isFirstExercise) 2f else 1f)
                )
            } else {
                WallCrawlPrimaryButton(
                    text = "Finish Workout",
                    onClick = onFinishWorkout,
                    modifier = Modifier.weight(if (state.isFirstExercise) 2f else 1f)
                )
            }
        }
    }
}

@Composable
private fun ExerciseHeaderCard(
    workoutExercise: WorkoutExercise,
    preferredUnit: String
) {
    val cleanName = workoutExercise.exerciseId
        .split("-")
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    WallCrawlCard(
        cornerRadius = 14.dp,
        contentPadding = 14.dp,
        backgroundColor = GraphiteSurfaceElevated
    ) {
        Text(
            text = cleanName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = TextWhite
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatBadge(
                label = "${workoutExercise.targetSets} × ${workoutExercise.targetRepMin}–${workoutExercise.targetRepMax}",
                textColor = TextPrimary
            )

            if (workoutExercise.targetWeight != null) {
                StatBadge(
                    label = "Suggested: ${workoutExercise.targetWeight} $preferredUnit",
                    textColor = WebBlueAccent
                )
            }
        }
    }
}

@Composable
private fun PreviousPerformanceCard(
    history: List<String>
) {
    WallCrawlCard(
        cornerRadius = 12.dp,
        contentPadding = 12.dp,
        backgroundColor = GraphiteSurface
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = WebBlueAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "PREVIOUS SESSION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = WebBlueAccent
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        history.forEach { entry ->
            Text(
                text = "• $entry",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}
