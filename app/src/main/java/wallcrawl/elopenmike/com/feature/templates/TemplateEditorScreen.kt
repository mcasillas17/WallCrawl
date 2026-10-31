package wallcrawl.elopenmike.com.feature.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.core.ui.localization.longLabelRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.material3.MaterialTheme
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.hasUnresolvedEquipmentRequirements
import wallcrawl.elopenmike.com.core.model.missingEquipmentAlternatives
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlOutlinedButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.MIN_TOUCH_TARGET
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.shortLabelRes
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorScreen(
    viewModel: TemplateEditorViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val locale = LocalConfiguration.current.locales[0]

    // Toolbar back, system back and predictive back all take the same route, so leaving
    // with unsaved edits always asks first. A write in flight closes the route entirely:
    // popping the entry would cancel the coroutine mid-save, and a late callback could
    // pop a second screen.
    // An incomplete draft is discardable whether or not it differs from the baseline:
    // discarding is the only way out the refusal names.
    val canDiscard = state.isDirty || state.isDraftIncomplete
    // One pop per editor: a second tap during the exit transition would otherwise pop the
    // screen beneath it too.
    var hasLeft by rememberSaveable { mutableStateOf(false) }
    val leaveOnce = { leave: () -> Unit ->
        if (!hasLeft) {
            hasLeft = true
            leave()
        }
    }
    // Saved, by this composition or one a rotation replaced: leave once.
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) leaveOnce(onSaved)
    }
    val leaveEditor = {
        when {
            state.isSaving -> Unit
            canDiscard -> viewModel.requestDiscard()
            else -> leaveOnce(onBack)
        }
    }
    // Enabled during a save on purpose: a disabled handler lets the press fall through
    // to the navigation host, which pops the entry and cancels the write. Staying enabled
    // with nothing to do is what swallows it.
    BackHandler(enabled = canDiscard || state.isSaving) {
        if (!state.isSaving) viewModel.requestDiscard()
    }

    if (!state.isLoading) {
        if (state.isPickerOpen) {
            ExercisePickerSheet(state = state, viewModel = viewModel)
        }
        state.editorDraft?.let { draft ->
            ExercisePrescriptionSheet(state = state, draft = draft, viewModel = viewModel)
        }
    }
    if (state.showDiscardConfirmation) {
        DiscardConfirmation(
            onKeepEditing = viewModel::dismissDiscard,
            onDiscard = {
                if (!state.isSaving) {
                    viewModel.confirmDiscard()
                    leaveOnce(onBack)
                }
            }
        )
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
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = leaveEditor, enabled = !state.isSaving) {
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
                        enabled = !state.isSaving,
                        textStyle = editorTextStyle(),
                        // `Typography` sets a colour on every style, and a `Text` prefers its
                        // style's colour over anything `colors` supplies, so a resting label
                        // with no colour of its own paints the dark-theme value — on this
                        // surface, near-white on white, at about 1.1:1.
                        label = {
                            Text(
                                stringResource(R.string.editor_name_label),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        placeholder = {
                            Text(
                                stringResource(R.string.editor_name_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = editorFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = viewModel::updateNotes,
                        enabled = !state.isSaving,
                        textStyle = editorTextStyle(),
                        label = {
                            Text(
                                stringResource(R.string.editor_notes_label),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        placeholder = {
                            Text(
                                stringResource(R.string.editor_notes_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = editorFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.showsRoundedStoredValues) {
                    item {
                        WallCrawlCard {
                            Text(
                                text = stringResource(R.string.editor_rounded_stored_values),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Without a successful load there is nothing to label: a new template would
                // otherwise show the legacy "saved before units" question.
                if (!state.hasLoadFailed) item {
                    WeightUnitSection(
                        weightUnit = state.weightUnit,
                        // A refusal of the chips is shown at the chips, not below the list.
                        refusal = state.unitRefusal,
                        enabled = !state.isSaving,
                        onConvert = viewModel::convertUnit,
                        onConfirm = viewModel::confirmUnit
                    )
                }

                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            // Without a loaded catalog and profile there is nothing to add.
                            enabled = !state.isSaving && !state.isAtExerciseLimit && !state.hasLoadFailed,
                            onClick = viewModel::openPicker
                        )
                        if (state.isAtExerciseLimit) {
                            Text(
                                text = stringResource(R.string.editor_error_exercise_limit),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Its copy points at Add and counts the catalog; after a failed load Add is off
                // and there is no catalog, so the error card speaks alone.
                if (state.selectedExercises.isEmpty()) {
                    if (!state.hasLoadFailed) item {
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
                    itemsIndexed(
                        state.selectedExercises,
                        key = { _, draft -> draft.rowId }
                    ) { index, draft ->
                        SelectedExerciseCard(
                            index = index,
                            total = state.selectedExercises.size,
                            draft = draft,
                            exercise = state.exerciseFor(draft),
                            isMissingFromCatalog = draft.rowId in state.unresolvedRowIds,
                            isSaving = state.isSaving,
                            availableEquipment = state.availableEquipment,
                            weightUnit = state.weightUnit?.let { stringResource(it.shortLabelRes) },
                            onEdit = { viewModel.editExercise(draft.rowId) },
                            onSetsDown = { viewModel.changeSetCount(draft.rowId, -1) },
                            onSetsUp = { viewModel.changeSetCount(draft.rowId, 1) },
                            onMoveUp = { viewModel.moveExercise(draft.rowId, -1) },
                            onMoveDown = { viewModel.moveExercise(draft.rowId, 1) },
                            onRemove = { viewModel.removeExercise(draft.rowId) }
                        )
                    }
                }

                // While a sheet is open it shows the message itself; one announced copy, not two.
                if (state.editorDraft == null) state.errorMessage?.let { messageRes ->
                    item { EditorErrorCard(messageRes) }
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
                        onClick = viewModel::save
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * The unit every load in this workout is written in.
 *
 * With a unit, choosing the other one converts the numbers. Without one — a legacy template
 * whose loads never recorded a unit — choosing one labels the numbers already there, and the
 * copy says so, because that is a statement about the past rather than a conversion.
 */
@Composable
private fun WeightUnitSection(
    weightUnit: WeightUnit?,
    refusal: Int?,
    enabled: Boolean,
    onConvert: (WeightUnit) -> Unit,
    onConfirm: (WeightUnit) -> Unit
) {
    val content: @Composable () -> Unit = {
        if (weightUnit == null) {
            Text(
                text = stringResource(R.string.editor_unit_unconfirmed_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.editor_unit_unconfirmed_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        } else {
            Text(
                text = stringResource(R.string.editor_unit_label),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.editor_unit_conversion_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup()
        ) {
            WeightUnit.entries.forEach { unit ->
                val selected = unit == weightUnit
                FilterChip(
                    selected = selected,
                    enabled = enabled,
                    onClick = { if (weightUnit == null) onConfirm(unit) else onConvert(unit) },
                    label = {
                        Text(
                            text = stringResource(unit.longLabelRes),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            // Named, so it follows the chip's state itself: while a save
                            // disables the chips, Material's muted container sits behind it.
                            color = when {
                                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                selected -> MaterialTheme.colorScheme.onSecondary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        selectedContainerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.semantics { role = Role.RadioButton }
                )
            }
        }
        if (refusal != null) {
            val refusalText = if (refusal == R.string.editor_error_unit_confirm_too_large) {
                // The kilogram limit it refers to; only kilograms can refuse a confirmation.
                stringResource(
                    refusal,
                    LocaleFormatting.formatMeasurement(
                        WorkoutTemplate.maxLoad(WeightUnit.KG),
                        LocalConfiguration.current.locales[0]
                    )
                )
            } else {
                stringResource(refusal)
            }
            Spacer(Modifier.height(6.dp))
            // A polite live region, as the rest of the app announces status (TodayDeloadCard).
            Text(
                text = refusalText,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
    if (weightUnit == null) {
        WallCrawlCard(borderColor = CrimsonRedPrimary) { content() }
    } else {
        Column { content() }
    }
}

@Composable
private fun DiscardConfirmation(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
    titleRes: Int = R.string.editor_discard_title,
    messageRes: Int = R.string.editor_discard_message
) {
    // Every colour is passed to the `Text` itself. `Text` prefers its own `color`, then its
    // style's, and only then `LocalContentColor`; this app's `Typography` sets a colour on
    // every style, so `titleContentColor` and `textContentColor` never reach the glyphs and
    // the title and the keep-editing action painted the dark-theme white on a white
    // container. Fixing that app-wide means taking the colours out of `Typography`, which
    // reaches every screen and is not this package's to change. Semantics cannot see any of
    // it, so the instrumented test measures the contrast of the pixels instead.
    val onContainer = MaterialTheme.colorScheme.onSurface
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = {
            Text(stringResource(titleRes), color = onContainer)
        },
        text = {
            Text(
                stringResource(messageRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.editor_discard_confirm), color = CrimsonRedLight)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(stringResource(R.string.editor_discard_keep), color = onContainer)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun EditorErrorCard(messageRes: Int) {
    WallCrawlCard(borderColor = CrimsonRedPrimary) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = CrimsonRedLight, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(messageRes),
                color = CrimsonRedLight,
                fontSize = 13.sp,
                // Announced: a refused save leaves the tapped button unchanged.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun SelectedExerciseCard(
    index: Int,
    total: Int,
    draft: TemplateExerciseDraft,
    exercise: Exercise?,
    isMissingFromCatalog: Boolean,
    isSaving: Boolean,
    availableEquipment: Set<String>,
    weightUnit: String?,
    onEdit: () -> Unit,
    onSetsDown: () -> Unit,
    onSetsUp: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    val exerciseName = exercise
        ?.let(vocabulary::exerciseName)
        ?: vocabulary.exerciseName(draft.exerciseId)
    val currentSets = LocaleFormatting.parseIntInput(draft.sets) ?: 0
    val canDecreaseSets = currentSets > ExercisePrescription.MIN_TARGET_SETS && !isSaving
    val canIncreaseSets = currentSets < ExercisePrescription.MAX_TARGET_SETS && !isSaving
    WallCrawlCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
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
                    text = exerciseName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = draftSummary(draft, weightUnit),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (isMissingFromCatalog) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.draft_error_missing_exercise),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                exercise?.let { EquipmentWarning(it, availableEquipment) }
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
                        .size(MIN_TOUCH_TARGET)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(enabled = canDecreaseSets, onClick = onSetsDown),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.editor_decrease_sets),
                        tint = if (canDecreaseSets) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    // A read-only count: the locale's digits when it is a number, the
                    // field's text otherwise.
                    text = LocaleFormatting.parseIntInput(draft.sets)
                        ?.let { LocaleFormatting.formatCount(it, locale) } ?: draft.sets,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Box(
                    modifier = Modifier
                        .size(MIN_TOUCH_TARGET)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(enabled = canIncreaseSets, onClick = onSetsUp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.editor_increase_sets),
                        tint = if (canIncreaseSets) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val editDescription =
                stringResource(R.string.editor_edit_accessibility, exerciseName)
            // Each control's state decides both whether it acts and how it is drawn.
            val canEdit = !isMissingFromCatalog && !isSaving
            val canMoveUp = index > 0 && !isSaving
            val canMoveDown = index < total - 1 && !isSaving
            val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            IconButton(
                onClick = onEdit,
                enabled = canEdit,
                modifier = Modifier
                    .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                    .semantics { contentDescription = editDescription }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = if (canEdit) CrimsonRedLight else muted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.editor_move_up),
                    tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant else muted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = stringResource(R.string.editor_move_down),
                    tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant else muted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                enabled = !isSaving,
                modifier = Modifier.sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = if (isSaving) muted else CrimsonRedLight.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * The whole prescription for one exercise, on a surface the keyboard can share.
 *
 * The sheet is only the window; [PrescriptionSheetContent] is the form, which is what
 * layout and localization are checked against directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePrescriptionSheet(
    state: TemplateEditorUiState,
    draft: TemplateExerciseDraft,
    viewModel: TemplateEditorViewModel
) {
    // A swipe, a scrim tap and back all ask to hide the sheet first; with typing in it,
    // that becomes a question rather than a silent loss. Cancel stays the deliberate discard.
    val isModified by rememberUpdatedState(state.isSheetModified)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && isModified) {
                viewModel.dismissSheet()
                false
            } else {
                true
            }
        }
    )
    val vocabulary = LocalExerciseVocabulary.current

    ModalBottomSheet(
        onDismissRequest = viewModel::dismissSheet,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        // In the sheet's own window, so it outranks the sheet's predictive back: that scales the
        // sheet with the gesture and keeps the scale for a hide, which typing here vetoes.
        BackHandler(enabled = isModified) { viewModel.dismissSheet() }
        PrescriptionSheetContent(
            state = state,
            draft = draft,
            exerciseName = state.exerciseFor(draft)
                ?.let(vocabulary::exerciseName)
                ?: vocabulary.exerciseName(draft.exerciseId),
            onFieldChange = viewModel::updateDraftField,
            onNotesChange = viewModel::updateDraftNotes,
            onCancel = viewModel::cancelDraft,
            onApply = viewModel::applyDraft
        )
    }
    // After the sheet, so its window is always added above the sheet's — including when
    // both come back together after a configuration change.
    if (state.showSheetDiscardConfirmation) {
        DiscardConfirmation(
            titleRes = R.string.editor_sheet_discard_title,
            messageRes = R.string.editor_sheet_discard_message,
            onKeepEditing = viewModel::keepEditingSheet,
            onDiscard = viewModel::cancelDraft
        )
    }
}

/**
 * Every field this exercise type supports, plus rest, effort and notes.
 *
 * Values are the draft's own text, so a half-typed decimal stays exactly as typed until
 * Apply reads it. The column scrolls within the insets the sheet applies to its content,
 * the keyboard's included, so the errors and both buttons stay reachable while it is
 * open, on a compact screen and at a large font scale.
 */
@Composable
internal fun PrescriptionSheetContent(
    state: TemplateEditorUiState,
    draft: TemplateExerciseDraft,
    exerciseName: String,
    onFieldChange: (DraftField, String) -> Unit,
    onNotesChange: (String) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(
        // Scrolls within the insets the sheet gives it, the keyboard's among them, so Apply
        // can always be scrolled above the keyboard; the sheet's window applies those insets.
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.editor_action_edit),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = CrimsonRedPrimary
        )
        Text(
            text = exerciseName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        (TemplateExerciseDraft.fieldsFor(draft.exerciseType) + DraftField.REST).forEach { field ->
            DraftNumberField(
                field = field,
                draft = draft,
                state = state,
                exerciseName = exerciseName,
                imeAction = ImeAction.Next,
                onValueChange = { onFieldChange(field, it) },
                onDone = {}
            )
        }

        Text(
            text = stringResource(R.string.editor_effort_eyebrow),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = CrimsonRedPrimary
        )
        Text(
            text = stringResource(R.string.editor_effort_help),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DraftNumberField(
            field = DraftField.EFFORT_MIN,
            draft = draft,
            state = state,
            exerciseName = exerciseName,
            imeAction = ImeAction.Next,
            onValueChange = { onFieldChange(DraftField.EFFORT_MIN, it) },
            onDone = {}
        )
        DraftNumberField(
            field = DraftField.EFFORT_MAX,
            draft = draft,
            state = state,
            exerciseName = exerciseName,
            imeAction = ImeAction.Done,
            onValueChange = { onFieldChange(DraftField.EFFORT_MAX, it) },
            onDone = { focusManager.clearFocus() }
        )

        OutlinedTextField(
            value = draft.notes,
            onValueChange = onNotesChange,
            enabled = !state.isSaving,
            label = {
                Text(
                    stringResource(R.string.editor_field_exercise_notes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            textStyle = editorTextStyle(),
            shape = RoundedCornerShape(12.dp),
            colors = editorFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        state.errorMessage?.let { EditorErrorCard(it) }

        // Stacked, full width: side by side at a large font scale the Spanish labels broke
        // mid-word.
        WallCrawlPrimaryButton(
            text = stringResource(R.string.editor_action_apply),
            onClick = onApply
        )
        WallCrawlOutlinedButton(
            text = stringResource(R.string.action_cancel),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DraftNumberField(
    field: DraftField,
    draft: TemplateExerciseDraft,
    state: TemplateEditorUiState,
    exerciseName: String,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    // The label, the hint and the check all read the draft's one unit, so they agree.
    val rule = TemplateExerciseDraft.rule(field, draft.exerciseType, state.weightUnit)
    val baseLabel = stringResource(field.labelRes)
    val weightUnit = state.weightUnit?.let { stringResource(it.shortLabelRes) }
    // An unconfirmed legacy unit is shown as no unit at all, never as a guessed one.
    val label = if ((field == DraftField.LOAD || field == DraftField.ASSISTANCE) && weightUnit != null) {
        stringResource(R.string.set_field_with_unit, baseLabel, weightUnit)
    } else {
        baseLabel
    }
    val errorRes = state.fieldErrors[field]
    val fieldDescription =
        stringResource(R.string.editor_field_accessibility, label, exerciseName)

    OutlinedTextField(
        value = field.valueIn(draft),
        onValueChange = onValueChange,
        enabled = !state.isSaving,
        label = {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        textStyle = editorTextStyle(),
        isError = errorRes != null,
        supportingText = {
            Text(
                text = when {
                    // An out-of-range value is the one error where the bounds are the
                    // answer, and an optional field never shows them otherwise.
                    errorRes == R.string.draft_error_out_of_range_with_range -> stringResource(
                        errorRes,
                        rule.rangeLabel(locale)
                    )

                    errorRes != null -> stringResource(errorRes)
                    rule.isOptional -> stringResource(R.string.editor_optional_unset_hint)
                    else -> stringResource(
                        R.string.editor_field_range_hint,
                        rule.rangeLabel(locale)
                    )
                },
                fontSize = 11.sp
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = editorFieldColors(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (rule.isDecimal) {
                KeyboardType.Decimal
            } else {
                KeyboardType.Number
            },
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .semantics { contentDescription = fieldDescription }
    )
}

private val DraftField.labelRes: Int
    get() = when (this) {
        DraftField.SETS -> R.string.editor_target_sets
        DraftField.REP_MIN -> R.string.editor_field_rep_min
        DraftField.REP_MAX -> R.string.editor_field_rep_max
        DraftField.LOAD -> R.string.set_field_load
        DraftField.ASSISTANCE -> R.string.set_field_assist
        DraftField.DURATION -> R.string.set_field_seconds
        DraftField.DISTANCE -> R.string.set_field_meters
        DraftField.REST -> R.string.editor_field_rest
        DraftField.EFFORT_MIN -> R.string.editor_field_effort_min
        DraftField.EFFORT_MAX -> R.string.editor_field_effort_max
    }

private fun TemplateExerciseDraft.Companion.FieldRule.rangeLabel(locale: Locale): String =
    LocaleFormatting.formatEditableDecimal(minimum, locale) + "–" +
        LocaleFormatting.formatEditableDecimal(maximum, locale)

private fun DraftField.valueIn(draft: TemplateExerciseDraft): String = when (this) {
    DraftField.SETS -> draft.sets
    DraftField.REP_MIN -> draft.repMin
    DraftField.REP_MAX -> draft.repMax
    DraftField.LOAD -> draft.load
    DraftField.ASSISTANCE -> draft.assistance
    DraftField.DURATION -> draft.durationSeconds
    DraftField.DISTANCE -> draft.distanceMeters
    DraftField.REST -> draft.restSeconds
    DraftField.EFFORT_MIN -> draft.effortMinRir
    DraftField.EFFORT_MAX -> draft.effortMaxRir
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
        // The sheet applies the keyboard inset to its content, as for the prescription sheet.
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
                textStyle = editorTextStyle(),
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
                colors = editorFieldColors(),
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

/**
 * What a field shows as typed. `Typography` sets a colour on every style, and a style's
 * colour wins over `colors`, so the inherited one would draw a typed value in the dark-theme
 * colour, near-white on a light surface. Clearing it hands the colour to
 * [editorFieldColors], including Material's muted colour while a save disables the field.
 * Every editor text field uses it.
 */
@Composable
private fun editorTextStyle() = LocalTextStyle.current.copy(color = Color.Unspecified)

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
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
)

/**
 * The compact "3 sets · 8-12 · 60 KG · rest 90 s" line under a selected exercise.
 *
 * It reads the draft's own text, so it shows exactly what was typed rather than a second
 * rendering of a parsed number.
 */
@Composable
private fun draftSummary(draft: TemplateExerciseDraft, weightUnit: String?): String {
    val separator = stringResource(R.string.detail_separator)
    val sets = pluralStringResource(
        R.plurals.count_sets,
        LocaleFormatting.parseIntInput(draft.sets) ?: 0,
        draft.sets
    )
    val reps = when {
        // A half-typed range is not a target; the row's own validation reports it.
        draft.repMin.isBlank() || draft.repMax.isBlank() -> null
        draft.repMin == draft.repMax -> draft.repMin
        else -> stringResource(R.string.prescription_rep_range, draft.repMin, draft.repMax)
    }
    val seconds = draft.durationSeconds.takeIf { it.isNotBlank() }?.let {
        stringResource(R.string.prescription_seconds, it)
    }
    val meters = draft.distanceMeters.takeIf { it.isNotBlank() }?.let {
        stringResource(R.string.prescription_meters, it)
    }
    // An unconfirmed legacy unit is shown as no unit at all, never as a guessed one.
    @Composable
    fun withUnit(number: String) =
        if (weightUnit == null) number else stringResource(R.string.editor_summary_load, number, weightUnit)
    val load = draft.load.takeIf { it.isNotBlank() }?.let { withUnit(it) }
    // Labelled: assistance takes weight off, the opposite of a load.
    val assistance = draft.assistance.takeIf { it.isNotBlank() }?.let {
        stringResource(R.string.editor_summary_assistance, withUnit(it))
    }
    val targets = when (draft.exerciseType) {
        ExerciseType.WEIGHT_REPS -> listOfNotNull(sets, reps, load)
        ExerciseType.BODYWEIGHT_REPS -> listOfNotNull(sets, reps)
        ExerciseType.ASSISTED_BODYWEIGHT -> listOfNotNull(sets, reps, assistance)
        ExerciseType.DURATION -> listOfNotNull(sets, seconds)
        ExerciseType.DISTANCE_DURATION -> listOfNotNull(sets, meters, seconds)
    }
    val rest = draft.restSeconds.takeIf { it.isNotBlank() }?.let {
        stringResource(
            R.string.editor_summary_rest,
            stringResource(R.string.prescription_seconds, it)
        )
    }
    val effort = if (draft.effortMinRir.isNotBlank() && draft.effortMaxRir.isNotBlank()) {
        stringResource(
            R.string.editor_summary_effort,
            stringResource(
                R.string.prescription_rep_range,
                draft.effortMinRir,
                draft.effortMaxRir
            )
        )
    } else {
        null
    }
    return (targets + listOfNotNull(rest, effort)).joinToString(separator)
}
