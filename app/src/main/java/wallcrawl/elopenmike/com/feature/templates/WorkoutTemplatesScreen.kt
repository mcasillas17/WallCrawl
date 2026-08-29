package wallcrawl.elopenmike.com.feature.templates

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent

@Composable
fun WorkoutTemplatesScreen(
    viewModel: WorkoutTemplatesViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onWorkoutStarted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<WorkoutTemplate?>(null) }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WebBackgroundPattern()
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
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
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                IconButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, "Create workout", tint = CrimsonRedPrimary)
                }
            }

            state.errorMessage?.let {
                Text(it, color = CrimsonRedPrimary, modifier = Modifier.padding(bottom = 8.dp))
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrimsonRedPrimary)
                }
                state.templates.isEmpty() -> EmptyTemplates(onCreate)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.templates, key = WorkoutTemplate::id) { template ->
                        TemplateCard(
                            template = template,
                            isStarting = state.startingTemplateId == template.id,
                            onStart = { viewModel.startTemplate(template, onWorkoutStarted) },
                            onEdit = { onEdit(template.id) },
                            onDelete = { pendingDelete = template }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Delete \"${template.name}\"?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "This routine will be removed from your saved workouts. Completed workout history will not be affected.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate(template.id)
                    pendingDelete = null
                }) {
                    Text("Delete", color = CrimsonRedLight, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun EmptyTemplates(onCreate: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WallCrawlCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = CrimsonRedPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Build your own workout", color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Choose from all 302 offline exercises and save custom routines to reuse anytime.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            WallCrawlPrimaryButton("Create Workout", onClick = onCreate)
        }
    }
}

@Composable
private fun TemplateCard(
    template: WorkoutTemplate,
    isStarting: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    WallCrawlCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(template.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${template.exercises.size} exercises · ${template.exercises.sumOf { it.targetSets }} total sets",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (template.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(template.notes, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = CrimsonRedLight.copy(alpha = 0.8f)) }
        }
        Spacer(Modifier.height(12.dp))
        WallCrawlPrimaryButton(
            text = if (isStarting) "Starting Workout…" else "Start Workout",
            enabled = !isStarting,
            onClick = onStart,
            leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = TextWhite) }
        )
    }
}
