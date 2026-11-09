package wallcrawl.elopenmike.com.feature.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.ui.components.ExerciseIllustration
import wallcrawl.elopenmike.com.core.ui.components.LoadingIndicator
import wallcrawl.elopenmike.com.core.ui.components.isLargeText
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.a11yAnnounced
import wallcrawl.elopenmike.com.core.ui.components.a11yExpanded
import wallcrawl.elopenmike.com.core.ui.components.a11yHeading
import wallcrawl.elopenmike.com.core.ui.components.radioChip
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.feature.templates.WorkoutTemplatesScreen
import wallcrawl.elopenmike.com.feature.templates.WorkoutTemplatesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    viewModel: ExercisesViewModel,
    visualProvider: ExerciseVisualProvider,
    modifier: Modifier = Modifier,
    onOpenConditionReference: (() -> Unit)? = null,
    templatesViewModel: WorkoutTemplatesViewModel? = null,
    onCreateTemplate: () -> Unit = {},
    onEditTemplate: (String) -> Unit = {},
    onWorkoutStarted: (String) -> Unit = {}
) {
    var showWorkouts by rememberSaveable { mutableStateOf(false) }
    val tabState = rememberSaveableStateHolder()
    Column(modifier.fillMaxSize()) {
        if (templatesViewModel != null) {
            val locked by templatesViewModel.navigationLocked.collectAsState()
            PrimaryTabRow(selectedTabIndex = if (showWorkouts) 1 else 0, containerColor = MaterialTheme.colorScheme.background) {
                listOf(R.string.guided_exercises, R.string.templates_title).forEachIndexed { index, label ->
                    Tab(
                        selected = showWorkouts == (index == 1), enabled = !locked,
                        selectedContentColor = MaterialTheme.colorScheme.tertiary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = dropUnlessResumed { if (!templatesViewModel.navigationLocked.value) showWorkouts = index == 1 },
                        text = { Text(stringResource(label)) }
                    )
                }
            }
        }
        tabState.SaveableStateProvider(showWorkouts) {
            if (showWorkouts && templatesViewModel != null) {
                WorkoutTemplatesScreen(templatesViewModel, onBack = null, onCreate = onCreateTemplate,
                    onEdit = onEditTemplate, onWorkoutStarted = onWorkoutStarted)
            } else {
                when (val state = viewModel.uiState.collectAsState().value) {
                    ExercisesUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                    is ExercisesUiState.Error -> Column(Modifier.padding(16.dp)) {
                        Text(stringResource(state.messageRes), color = MaterialTheme.colorScheme.error, modifier = Modifier.a11yAnnounced())
                        TextButton(onClick = viewModel::retryLoad) { Text(stringResource(R.string.history_retry)) }
                    }
                    is ExercisesUiState.Success -> {
                        ExerciseList(state, viewModel, visualProvider, onOpenConditionReference)
                        state.selectedExerciseDetail?.let { exercise ->
                            ExerciseDetailSheet(exercise, visualProvider, viewModel::closeExerciseDetail,
                                onOpenConditionReference = onOpenConditionReference)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExerciseList(
    state: ExercisesUiState.Success,
    viewModel: ExercisesViewModel,
    visualProvider: ExerciseVisualProvider,
    onOpenConditionReference: (() -> Unit)?
) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val vocabulary = LocalExerciseVocabulary.current
    val locale = LocalConfiguration.current.locales[0]
    val activeFilters = listOfNotNull(state.selectedMuscle?.let(vocabulary::muscle), state.selectedEquipment?.let(vocabulary::equipment))
    val largeText = isLargeText()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            OutlinedTextField(
                value = state.query, onValueChange = viewModel::onQueryChanged, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text(stringResource(R.string.guided_search_exercises)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.onQueryChanged("") }) {
                        Icon(Icons.Default.Close, stringResource(R.string.exercises_search_clear))
                    }
                }
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { filtersOpen = true }, colors = ButtonDefaults.textButtonColors(
                    contentColor = if (activeFilters.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary
                )) {
                    Icon(Icons.Default.FilterList, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (activeFilters.isEmpty()) stringResource(R.string.guided_filters) else
                        stringResource(R.string.guided_filter_count, LocaleFormatting.formatCount(activeFilters.size, locale)))
                }
                if (onOpenConditionReference != null) TextButton(onClick = dropUnlessResumed { onOpenConditionReference() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Icon(Icons.AutoMirrored.Filled.LibraryBooks, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.guided_condition_guide))
                }
            }
            if (activeFilters.isNotEmpty()) Text(
                activeFilters.joinToString(stringResource(R.string.detail_separator)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                pluralStringResource(R.plurals.count_exercises_found, state.exercises.size,
                    LocaleFormatting.formatCount(state.exercises.size, locale)),
                Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.exercises.isEmpty()) item { Text(stringResource(R.string.exercises_empty)) }
        items(state.exercises, key = Exercise::id) { exercise ->
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) { viewModel.openExerciseDetail(exercise) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                // The thumbnail is decorative; at large text the name needs its width.
                leadingContent = if (largeText) null else {
                    {
                        ExerciseIllustration(exercise, visualProvider, Modifier.size(64.dp).clearAndSetSemantics {},
                            height = 64, thumbnail = true)
                    }
                },
                headlineContent = { Text(vocabulary.exerciseName(exercise)) },
                supportingContent = {
                    val equipment = vocabulary.equipment(exercise.listedEquipment)
                    Text(equipment.ifEmpty { vocabulary.muscles(exercise.primaryMuscles) }
                        .joinToString(stringResource(R.string.list_separator)))
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
            )
            HorizontalDivider()
        }
    }
    if (filtersOpen) {
        ModalBottomSheet(onDismissRequest = { filtersOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.guided_filters), Modifier.weight(1f).a11yHeading(), style = MaterialTheme.typography.titleLarge)
                    TextButton(enabled = activeFilters.isNotEmpty(), onClick = {
                        viewModel.selectMuscle(null)
                        viewModel.selectEquipment(null)
                    }) { Text(stringResource(R.string.guided_clear_filters)) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterOptions(stringResource(R.string.guided_muscles), vocabulary.musclesByLabel(state.availableMuscles),
                        state.selectedMuscle, vocabulary::muscle, viewModel::selectMuscle)
                    FilterOptions(stringResource(R.string.guided_equipment), vocabulary.equipmentByLabel(state.availableEquipment),
                        state.selectedEquipment, vocabulary::equipment, viewModel::selectEquipment)
                }
                Button(onClick = { filtersOpen = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.guided_show_results))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterOptions(
    title: String, keys: List<String>, selected: String?, label: (String) -> String, onSelect: (String) -> Unit
) {
    Text(title, Modifier.a11yHeading(), style = MaterialTheme.typography.titleMedium)
    // One value per group, so the chips are radios, not FilterChip's default checkboxes.
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { key ->
            FilterChip(selected = key == selected, onClick = { onSelect(key) }, label = { Text(label(key)) },
                modifier = Modifier.radioChip())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseDetailSheet(
    exercise: Exercise,
    visualProvider: ExerciseVisualProvider,
    onDismiss: () -> Unit,
    onOpenConditionReference: (() -> Unit)? = null
) {
    var moreDetails by rememberSaveable(exercise.id) { mutableStateOf(false) }
    val vocabulary = LocalExerciseVocabulary.current
    val listSeparator = stringResource(R.string.list_separator)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(vocabulary.exerciseName(exercise), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.a11yHeading())
            ExerciseIllustration(exercise, visualProvider, height = 200)
            vocabulary.coachingSummary(exercise)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (vocabulary.hasUntranslatedCoachingSummary(exercise)) Text(stringResource(R.string.exercise_detail_original_language),
                style = MaterialTheme.typography.bodySmall)
            if (exercise.listedEquipment.isNotEmpty()) Text(stringResource(R.string.exercise_detail_equipment,
                vocabulary.equipment(exercise.listedEquipment).joinToString(listSeparator)))
            TextButton(onClick = { moreDetails = !moreDetails }, modifier = Modifier.a11yExpanded(moreDetails)) {
                Text(stringResource(if (moreDetails) R.string.today_hide_details else R.string.guided_more_details))
            }
            if (moreDetails) {
                val locale = LocalConfiguration.current.locales[0]
                Text(stringResource(R.string.exercise_detail_primary, vocabulary.muscles(exercise.primaryMuscles).joinToString(listSeparator)))
                if (exercise.secondaryMuscles.isNotEmpty()) Text(stringResource(R.string.exercise_detail_secondary,
                    vocabulary.muscles(exercise.secondaryMuscles).joinToString(listSeparator)))
                val programming = exercise.programming
                if (programming != null) {
                    Text(stringResource(programming.mechanics.labelRes))
                    Text(stringResource(R.string.exercise_detail_pattern, stringResource(programming.movementPattern.labelRes)))
                    Text(stringResource(R.string.exercise_detail_difficulty, stringResource(programming.difficulty.labelRes)))
                    programming.recommendedRepRange?.let { range ->
                        Text(pluralStringResource(R.plurals.count_target_reps, range.max, stringResource(
                            R.string.prescription_rep_range, LocaleFormatting.formatCount(range.min, locale),
                            LocaleFormatting.formatCount(range.max, locale))))
                    }
                    Text(stringResource(R.string.exercise_detail_fatigue, LocaleFormatting.formatCount(programming.fatigueScore, locale)))
                } else Text(stringResource(R.string.exercise_detail_review_pending))
                exercise.source?.attribution?.let { attribution ->
                    val uriHandler = LocalUriHandler.current
                    var failedUrl by remember { mutableStateOf<String?>(null) }
                    val source = attribution.source
                    val links = listOf(attribution.creator to attribution.creatorUrl, attribution.license to attribution.licenseUrl) +
                        listOfNotNull(source?.let { it.name to it.url })
                    links.distinct().forEach { (title, url) ->
                        TextButton(onClick = {
                            failedUrl = runCatching { uriHandler.openUri(url) }.exceptionOrNull()?.let { url }
                        }) { Text(title) }
                    }
                    failedUrl?.let {
                        Text(stringResource(R.string.credits_link_failed, it), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.a11yAnnounced())
                    }
                }
                if (onOpenConditionReference != null) TextButton(onClick = dropUnlessResumed { onOpenConditionReference() }) {
                    Text(stringResource(R.string.guided_condition_guide))
                }
            }
            WallCrawlPrimaryButton(stringResource(R.string.exercise_detail_close), onClick = onDismiss)
        }
    }
}
