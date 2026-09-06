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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
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
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.templates_eyebrow),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = CrimsonRedPrimary
                    )
                    Text(
                        text = stringResource(R.string.templates_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                IconButton(onClick = onCreate) {
                    Icon(
                        Icons.Default.Add,
                        stringResource(R.string.templates_create_content_description),
                        tint = CrimsonRedPrimary
                    )
                }
            }

            state.errorMessage?.let { messageRes ->
                Text(
                    stringResource(messageRes),
                    color = CrimsonRedPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrimsonRedPrimary)
                }
                state.templates.isEmpty() -> EmptyTemplates(
                    onCreate = onCreate,
                    catalogSize = state.catalogSize
                )
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
                    // The template's own name is user-authored text and is quoted, never
                    // translated.
                    text = stringResource(R.string.template_delete_title, template.name),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.template_delete_body),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate(template.id)
                    pendingDelete = null
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = CrimsonRedLight,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun EmptyTemplates(onCreate: () -> Unit, catalogSize: Int) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        WallCrawlCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = CrimsonRedPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    stringResource(R.string.templates_empty_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                pluralStringResource(
                    R.plurals.templates_empty_body,
                    catalogSize,
                    LocaleFormatting.formatCount(catalogSize, locale)
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            WallCrawlPrimaryButton(
                stringResource(R.string.templates_action_create),
                onClick = onCreate
            )
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
    val locale = LocalConfiguration.current.locales[0]
    WallCrawlCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val totalSets = template.exercises.sumOf { it.targetSets }
            Column(Modifier.weight(1f)) {
                // A saved routine's name and notes are the user's own words. They are
                // never translated, in either direction.
                Text(
                    template.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(
                        R.string.template_summary,
                        pluralStringResource(
                            R.plurals.count_exercises,
                            template.exercises.size,
                            LocaleFormatting.formatCount(template.exercises.size, locale)
                        ),
                        pluralStringResource(
                            R.plurals.count_sets,
                            totalSets,
                            LocaleFormatting.formatCount(totalSets, locale)
                        )
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (template.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(template.notes, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    stringResource(R.string.action_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    stringResource(R.string.action_delete),
                    tint = CrimsonRedLight.copy(alpha = 0.8f)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        WallCrawlPrimaryButton(
            text = stringResource(
                if (isStarting) R.string.template_action_starting else R.string.template_action_start
            ),
            enabled = !isStarting,
            onClick = onStart,
            leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = TextWhite) }
        )
    }
}
