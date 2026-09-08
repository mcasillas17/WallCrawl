package wallcrawl.elopenmike.com.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.hasUnresolvedEquipmentRequirements
import wallcrawl.elopenmike.com.core.model.missingEquipmentAlternatives
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlOutlinedButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent

@Composable
fun TemplateEditorScreen(
    viewModel: TemplateEditorViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val locale = LocalConfiguration.current.locales[0]

    if (state.isPickerOpen) {
        ExercisePickerSheet(state = state, viewModel = viewModel)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WebBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(
                        if (state.templateId == null) {
                            R.string.editor_title_create
                        } else {
                            R.string.editor_title_edit
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
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

            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::updateName,
                        label = { Text(stringResource(R.string.editor_name_label)) },
                        placeholder = {
                            Text(
                                stringResource(R.string.editor_name_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = CrimsonRedPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = CrimsonRedPrimary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::updateNotes,
                        label = { Text(stringResource(R.string.editor_notes_label)) },
                        placeholder = {
                            Text(
                                stringResource(R.string.editor_notes_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = CrimsonRedPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = CrimsonRedPrimary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.editor_exercises_eyebrow),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = CrimsonRedPrimary
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.count_exercises_selected,
                                    state.selectedExercises.size,
                                    LocaleFormatting.formatCount(
                                        state.selectedExercises.size,
                                        locale
                                    )
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        WallCrawlOutlinedButton(
                            text = stringResource(R.string.editor_action_add),
                            onClick = viewModel::openPicker
                        )
                    }
                }

                if (state.selectedExercises.isEmpty()) {
                    item {
                        WallCrawlCard {
                            Text(
                                text = stringResource(R.string.editor_empty_title),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = pluralStringResource(
                                    R.plurals.editor_empty_body,
                                    state.catalogExercises.size,
                                    LocaleFormatting.formatCount(
                                        state.catalogExercises.size,
                                        locale
                                    )
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    items(state.selectedExercises.size) { index ->
                        val planned = state.selectedExercises[index]
                        val exercise = state.catalogExercises.firstOrNull { it.id == planned.exerciseId }
                        SelectedExerciseCard(
                            index = index,
                            total = state.selectedExercises.size,
                            planned = planned,
                            exercise = exercise,
                            availableEquipment = state.availableEquipment,
                            onSetsDown = { viewModel.changeSetCount(index, -1) },
                            onSetsUp = { viewModel.changeSetCount(index, 1) },
                            onMoveUp = { viewModel.moveExercise(index, -1) },
                            onMoveDown = { viewModel.moveExercise(index, 1) },
                            onRemove = { viewModel.removeExercise(index) }
                        )
                    }
                }

                state.errorMessage?.let { messageRes ->
                    item {
                        WallCrawlCard(borderColor = CrimsonRedPrimary) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = CrimsonRedLight, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    stringResource(messageRes),
                                    color = CrimsonRedLight,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    WallCrawlPrimaryButton(
                        text = stringResource(
                            if (state.isSaving) {
                                R.string.editor_action_saving
                            } else {
                                R.string.editor_action_save
                            }
                        ),
                        enabled = !state.isSaving,
                        onClick = { viewModel.save(onSaved) }
                    )
                    Spacer(Modifier.height(24.dp))
                }
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
    availableEquipment: Set<String>,
    onSetsDown: () -> Unit,
    onSetsUp: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    WallCrawlCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.editor_position,
                    LocaleFormatting.formatCount(index + 1, locale)
                ),
                color = CrimsonRedPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise
                        ?.let(vocabulary::exerciseName)
                        ?: vocabulary.exerciseName(planned.exerciseId),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = prescriptionSummary(planned.prescription),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                exercise?.let { EquipmentWarning(it, availableEquipment) }
            }

            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.editor_move_up),
                    tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = index < total - 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = stringResource(R.string.editor_move_down),
                    tint = if (index < total - 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = CrimsonRedLight.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.editor_target_sets),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(enabled = planned.targetSets > 1, onClick = onSetsDown),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.editor_decrease_sets),
                        tint = if (planned.targetSets > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = LocaleFormatting.formatCount(planned.targetSets, locale),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onSetsUp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.editor_increase_sets),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    state: TemplateEditorUiState,
    viewModel: TemplateEditorViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    val detailSeparator = stringResource(R.string.detail_separator)

    ModalBottomSheet(
        onDismissRequest = viewModel::closePicker,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.picker_eyebrow),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = CrimsonRedPrimary
                    )
                    Text(
                        text = stringResource(R.string.picker_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = viewModel::closePicker) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                placeholder = {
                    Text(
                        pluralStringResource(
                            R.plurals.picker_search_placeholder,
                            state.catalogExercises.size,
                            LocaleFormatting.formatCount(state.catalogExercises.size, locale)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.exercises_search_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = CrimsonRedPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = pluralStringResource(
                    R.plurals.count_exercises_available,
                    state.filteredExercises.size,
                    LocaleFormatting.formatCount(state.filteredExercises.size, locale)
                ).uppercase(locale),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.filteredExercises, key = Exercise::id) { exercise ->
                    WallCrawlCard(
                        cornerRadius = 12.dp,
                        contentPadding = 12.dp,
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.addExercise(exercise) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vocabulary.exerciseName(exercise),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = (
                                        vocabulary.muscles(exercise.primaryMuscles) +
                                            vocabulary.equipment(exercise.listedEquipment)
                                        ).joinToString(detailSeparator),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonRedPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.picker_add_content_description),
                                    tint = CrimsonRedLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentWarning(exercise: Exercise, available: Set<String>) {
    val unresolved = exercise.hasUnresolvedEquipmentRequirements
    val missing = exercise.missingEquipmentAlternatives(available)
    if (!unresolved && missing.isEmpty()) return
    Spacer(modifier = Modifier.height(4.dp))
    if (unresolved) {
        Text(
            stringResource(R.string.editor_equipment_unresolved),
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp
        )
    } else {
        val vocabulary = LocalExerciseVocabulary.current
        val andSeparator = stringResource(R.string.editor_equipment_and_separator)
        val orSeparator = stringResource(R.string.editor_equipment_or_separator)
        val alternatives = missing.joinToString(orSeparator) {
            vocabulary.equipment(it).joinToString(andSeparator, prefix = "(", postfix = ")")
        }
        Text(stringResource(R.string.editor_equipment_warning), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        Text(
            stringResource(R.string.editor_equipment_missing, alternatives),
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp
        )
    }
}

/** The compact "3 series · 8–12" line under a selected exercise. */
@Composable
private fun prescriptionSummary(prescription: ExercisePrescription): String {
    val locale = LocalConfiguration.current.locales[0]
    val separator = stringResource(R.string.detail_separator)
    val sets = pluralStringResource(
        R.plurals.count_sets,
        prescription.targetSets,
        LocaleFormatting.formatCount(prescription.targetSets, locale)
    )
    val reps = prescription.repRange?.let { range ->
        if (range.min == range.max) {
            LocaleFormatting.formatCount(range.min, locale)
        } else {
            stringResource(
                R.string.prescription_rep_range,
                LocaleFormatting.formatCount(range.min, locale),
                LocaleFormatting.formatCount(range.max, locale)
            )
        }
    }
    val parts = when (prescription.exerciseType) {
        ExerciseType.WEIGHT_REPS -> listOfNotNull(
            sets,
            reps,
            prescription.targetWeight?.let {
                LocaleFormatting.formatMeasurement(it, locale)
            }
        )

        ExerciseType.BODYWEIGHT_REPS -> listOfNotNull(sets, reps)

        ExerciseType.ASSISTED_BODYWEIGHT -> listOfNotNull(
            sets,
            reps,
            prescription.targetAssistanceWeight?.let {
                LocaleFormatting.formatMeasurement(it, locale)
            }
        )

        ExerciseType.DURATION -> listOfNotNull(
            sets,
            stringResource(
                R.string.prescription_seconds,
                LocaleFormatting.formatCount(prescription.targetDurationSeconds ?: 0, locale)
            )
        )

        ExerciseType.DISTANCE_DURATION -> listOfNotNull(
            sets,
            prescription.targetDistanceMeters?.let {
                stringResource(
                    R.string.prescription_meters,
                    LocaleFormatting.formatCount(it.toInt(), locale)
                )
            },
            prescription.targetDurationSeconds?.let {
                stringResource(
                    R.string.prescription_seconds,
                    LocaleFormatting.formatCount(it, locale)
                )
            }
        )
    }
    return parts.joinToString(separator)
}
