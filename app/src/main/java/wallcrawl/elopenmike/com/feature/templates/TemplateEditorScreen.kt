package wallcrawl.elopenmike.com.feature.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurfaceElevated
import wallcrawl.elopenmike.com.core.ui.theme.ObsidianBlack
import wallcrawl.elopenmike.com.core.ui.theme.TextMuted
import wallcrawl.elopenmike.com.core.ui.theme.TextSecondary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite

@Composable
fun TemplateEditorScreen(
    viewModel: TemplateEditorViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    if (state.isPickerOpen) ExercisePickerDialog(state, viewModel)

    Column(
        modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextWhite)
            }
            Text(
                if (state.templateId == null) "Create Workout" else "Edit Workout",
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CrimsonRedPrimary)
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("Workout name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::updateNotes,
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Exercises (${state.selectedExercises.size})",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::openPicker) {
                        Icon(Icons.Default.Add, null)
                        Text("Add")
                    }
                }
            }
            items(state.selectedExercises.size) { index ->
                val planned = state.selectedExercises[index]
                val exercise = state.catalogExercises.firstOrNull { it.id == planned.exerciseId }
                SelectedExerciseCard(
                    index = index,
                    total = state.selectedExercises.size,
                    planned = planned,
                    exercise = exercise,
                    equipmentWarning = exercise?.hasEquipmentMismatch(state.availableEquipment) == true,
                    onSetsDown = { viewModel.changeSetCount(index, -1) },
                    onSetsUp = { viewModel.changeSetCount(index, 1) },
                    onMoveUp = { viewModel.moveExercise(index, -1) },
                    onMoveDown = { viewModel.moveExercise(index, 1) },
                    onRemove = { viewModel.removeExercise(index) }
                )
            }
            state.errorMessage?.let { message ->
                item { Text(message, color = CrimsonRedPrimary, fontSize = 13.sp) }
            }
            item {
                WallCrawlPrimaryButton(
                    text = if (state.isSaving) "Saving…" else "Save Workout",
                    enabled = !state.isSaving,
                    onClick = { viewModel.save(onSaved) }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SelectedExerciseCard(
    index: Int,
    total: Int,
    planned: PlannedExercise,
    exercise: Exercise?,
    equipmentWarning: Boolean,
    onSetsDown: () -> Unit,
    onSetsUp: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    WallCrawlCard(backgroundColor = GraphiteSurfaceElevated) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exercise?.name ?: planned.exerciseId, color = TextWhite, fontWeight = FontWeight.Bold)
                Text(planned.prescription.summary(), color = TextSecondary, fontSize = 13.sp)
                if (equipmentWarning) {
                    Text("Equipment not in your current profile", color = CrimsonRedPrimary, fontSize = 12.sp)
                }
            }
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(Icons.Default.ArrowUpward, "Move up", tint = TextMuted)
            }
            IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                Icon(Icons.Default.ArrowDownward, "Move down", tint = TextMuted)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove", tint = TextMuted) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sets", color = TextSecondary, modifier = Modifier.weight(1f))
            TextButton(onClick = onSetsDown) { Text("−") }
            Text("${planned.targetSets}", color = TextWhite, fontWeight = FontWeight.Bold)
            TextButton(onClick = onSetsUp) { Text("+") }
        }
    }
}

@Composable
private fun ExercisePickerDialog(state: TemplateEditorUiState, viewModel: TemplateEditorViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closePicker,
        title = { Text("Add Exercise") },
        text = {
            Column(Modifier.height(500.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    label = { Text("Search all 302 exercises") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.filteredExercises, key = Exercise::id) { exercise ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { viewModel.addExercise(exercise) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(exercise.name, fontWeight = FontWeight.Bold)
                            Text(
                                (exercise.primaryMuscles + exercise.listedEquipment).joinToString(" · "),
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::closePicker) { Text("Close") } },
        shape = RoundedCornerShape(20.dp)
    )
}

private fun Exercise.hasEquipmentMismatch(available: Set<String>): Boolean {
    val owned = available.map { it.trim().lowercase(Locale.ROOT) }.toSet()
    val combinations = programming?.requiredEquipmentCombinations
        ?: listOf(listedEquipment.filter(String::isNotBlank))
    return combinations.isNotEmpty() && combinations.none { combination ->
        combination.all { it.trim().lowercase(Locale.ROOT) in owned }
    }
}

private fun ExercisePrescription.summary(): String = when (exerciseType) {
    ExerciseType.WEIGHT_REPS -> "$targetSets sets · ${repRange ?: "reps"}" +
        (targetWeight?.let { " · $it load" } ?: "")
    ExerciseType.BODYWEIGHT_REPS -> "$targetSets sets · ${repRange ?: "reps"}"
    ExerciseType.ASSISTED_BODYWEIGHT -> "$targetSets sets · ${repRange ?: "reps"}" +
        (targetAssistanceWeight?.let { " · $it assistance" } ?: "")
    ExerciseType.DURATION -> "$targetSets sets · ${targetDurationSeconds}s"
    ExerciseType.DISTANCE_DURATION -> buildList {
        add("$targetSets sets")
        targetDistanceMeters?.let { add("${it.toInt()} m") }
        targetDurationSeconds?.let { add("${it}s") }
    }.joinToString(" · ")
}
