package wallcrawl.elopenmike.com.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.MaterialTheme
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
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.ui.components.ExerciseIllustration
import wallcrawl.elopenmike.com.core.ui.components.PerformanceSetRow
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlOutlinedButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveWorkoutScreen(
    viewModel: ActiveWorkoutViewModel,
    visualProvider: ExerciseVisualProvider,
    onNavigateBack: () -> Unit,
    onWorkoutFinished: (summarySessionId: String) -> Unit,
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
                        Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    visualProvider = visualProvider,
                    onPreviousExercise = { viewModel.previousExercise() },
                    onNextExercise = { viewModel.nextExercise() },
                    onUpdateSet = { setId, performance ->
                        viewModel.updateSet(setId, performance)
                    },
                    onFinishWorkout = { viewModel.finishWorkout() },
                    onDismissSetUpdateError = { viewModel.dismissSetUpdateError() },
                    onClose = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    state: ActiveWorkoutUiState.Active,
    visualProvider: ExerciseVisualProvider,
    onPreviousExercise: () -> Unit,
    onNextExercise: () -> Unit,
    onUpdateSet: (setId: String, performance: SetPerformanceInput) -> Unit,
    onFinishWorkout: () -> Unit,
    onDismissSetUpdateError: () -> Unit,
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.session.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
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
                    .widthIn(min = 76.dp)
                    .height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.setUpdateError != null) {
            SetUpdateErrorBanner(
                message = state.setUpdateError,
                onDismiss = onDismissSetUpdateError
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (currentExercise != null) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ExerciseIllustration(
                        exercise = state.currentCatalogExercise,
                        visualProvider = visualProvider
                    )
                }

                // 2. Exercise Title & Details Card
                item {
                    ExerciseHeaderCard(
                        workoutExercise = currentExercise,
                        exerciseName = state.currentCatalogExercise?.name,
                        preferredUnit = state.weightUnit.symbol
                    )
                }

                // 3. Previous Performance Reference Card
                if (state.previousSets.isNotEmpty()) {
                    item {
                        PreviousPerformanceCard(
                            sets = state.previousSets,
                            completedAtTimestamp = state.previousSessionTimestamp,
                            weightUnit = state.previousWeightUnit.symbol
                        )
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(32.dp)
                        )
                        Text(
                            text = currentExercise.prescription.inputLabel(state.weightUnit.symbol),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "DONE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(38.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // 5. Editable Sets Rows
                items(currentExercise.sets.size) { setIndex ->
                    val set = currentExercise.sets[setIndex]
                    PerformanceSetRow(
                        set = set,
                        weightUnit = state.weightUnit.symbol,
                        onUpdateSet = { performance ->
                            onUpdateSet(set.id, performance)
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
    exerciseName: String?,
    preferredUnit: String
) {
    val displayName = exerciseName ?: workoutExercise.exerciseId
        .split("-")
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    WallCrawlCard(
        cornerRadius = 14.dp,
        contentPadding = 14.dp
    ) {
        Text(
            text = displayName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatBadge(
                label = workoutExercise.prescription.displayTarget(),
                textColor = MaterialTheme.colorScheme.onSurface
            )

            if (workoutExercise.targetWeight != null) {
                StatBadge(
                    label = "Suggested: ${workoutExercise.targetWeight} $preferredUnit",
                    textColor = MaterialTheme.colorScheme.secondary
                )
            }
            workoutExercise.prescription.targetAssistanceWeight?.let { assistance ->
                StatBadge(
                    label = "Assistance: $assistance $preferredUnit",
                    textColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

private fun wallcrawl.elopenmike.com.core.model.ExercisePrescription.inputLabel(unit: String): String =
    when (exerciseType) {
        ExerciseType.WEIGHT_REPS -> "LOAD ($unit) · REPS"
        ExerciseType.BODYWEIGHT_REPS -> "REPS"
        ExerciseType.ASSISTED_BODYWEIGHT -> "ASSIST ($unit) · REPS"
        ExerciseType.DURATION -> "DURATION"
        ExerciseType.DISTANCE_DURATION -> "DISTANCE · DURATION"
    }

private fun wallcrawl.elopenmike.com.core.model.ExercisePrescription.displayTarget(): String =
    when (exerciseType) {
        ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT_REPS,
        ExerciseType.ASSISTED_BODYWEIGHT -> "$targetSets × $repRange"
        ExerciseType.DURATION -> "$targetSets × ${targetDurationSeconds}s"
        ExerciseType.DISTANCE_DURATION -> buildList {
            add("$targetSets set" + if (targetSets == 1) "" else "s")
            targetDistanceMeters?.let { add("${it.toInt()} m") }
            targetDurationSeconds?.let { add("${it}s") }
        }.joinToString(" · ")
    }

/**
 * A rejected set update (e.g. an explicit but invalid completion attempt) is
 * recoverable, not terminal: it surfaces here, dismissible, instead of replacing the
 * active workout with a full-screen error.
 */
@Composable
private fun SetUpdateErrorBanner(message: String, onDismiss: () -> Unit) {
    WallCrawlCard(
        cornerRadius = 12.dp,
        contentPadding = 12.dp,
        borderColor = CrimsonRedPrimary
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                color = CrimsonRedLight,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviousPerformanceCard(
    sets: List<WorkoutSet>,
    completedAtTimestamp: Long?,
    weightUnit: String
) {
    WallCrawlCard(
        cornerRadius = 12.dp,
        contentPadding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "PREVIOUS SESSION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        completedAtTimestamp?.let { timestamp ->
            Text(
                text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        sets.forEach { set ->
            val result = when (set.exerciseType) {
                ExerciseType.WEIGHT_REPS -> {
                    val reps = set.completedReps ?: return@forEach
                    "${set.completedWeight?.let { "$it $weightUnit" } ?: "No load"} × $reps"
                }
                ExerciseType.BODYWEIGHT_REPS ->
                    "Bodyweight × ${set.completedReps ?: return@forEach}"
                ExerciseType.ASSISTED_BODYWEIGHT -> {
                    val assistance = set.completedAssistanceWeight
                        ?.let { "$it $weightUnit assistance" }
                        ?: "Assistance not logged"
                    "$assistance × ${set.completedReps ?: return@forEach}"
                }
                ExerciseType.DURATION -> "${set.completedDurationSeconds ?: return@forEach} seconds"
                ExerciseType.DISTANCE_DURATION -> listOfNotNull(
                    set.completedDistanceMeters?.let { "$it m" },
                    set.completedDurationSeconds?.let { "$it seconds" }
                ).joinToString(" · ").ifBlank { return@forEach }
            }
            Text(
                text = "• $result",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
