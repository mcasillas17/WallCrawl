package wallcrawl.elopenmike.com.feature.conditions

import wallcrawl.elopenmike.com.core.model.Exercise
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.feature.exercises.ExerciseDetailSheet
import wallcrawl.elopenmike.com.core.ui.components.LoadingIndicator
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.a11yAnnounced
import wallcrawl.elopenmike.com.core.ui.components.a11yExpanded
import wallcrawl.elopenmike.com.core.ui.components.a11yHeading

@Composable
private fun <T> readOffline(vararg keys: Any?, load: suspend () -> T): Result<T>? {
    return produceState<Result<T>?>(null, *keys) {
        value = null
        value = withContext(Dispatchers.IO) {
            try {
                Result.success(load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }.value
}

@Composable
internal fun ConditionReferenceScreen(
    exerciseCatalog: ExerciseCatalog,
    visualProvider: ExerciseVisualProvider,
    onBack: () -> Unit
) {
    val assets = LocalContext.current.assets
    var attempt by remember { mutableIntStateOf(0) }
    val library = readOffline(assets, attempt) {
        assets.open("condition-reference/library.json").bufferedReader().use { readConditionReference(it.readText()) }
    }
    val spanish = LocalConfiguration.current.locales[0].language == "es"
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val back = { if (selectedId != null) selectedId = null else onBack() }
    BackHandler(enabled = selectedId != null) { selectedId = null }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                Text(stringResource(R.string.guided_condition_guide), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.a11yHeading())
            }
            val result = library
            val groups = result?.getOrNull()
            when {
                result == null -> LoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
                groups == null -> Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.condition_reference_unavailable), Modifier.a11yAnnounced(), color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.history_retry), color = MaterialTheme.colorScheme.onSurface) }
                }
                else -> {
                    val group = groups.find { it.entries.any { entry -> entry.id == selectedId } }
                    val selected = group?.entries?.find { it.id == selectedId }
                    if (group != null && selected != null) {
                        ReferenceDetail(selected, group.names, spanish, exerciseCatalog, visualProvider)
                    } else {
                        val matches = remember(groups, query) { groups.search(query) }
                        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item { Text(stringResource(R.string.guided_research_about), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item {
                                OutlinedTextField(value = query, onValueChange = { query = it.take(256) }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    label = { Text(stringResource(R.string.condition_reference_search), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = {
                                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                            Icon(Icons.Default.Close, stringResource(R.string.exercises_search_clear))
                                        }
                                    })
                            }
                            if (matches.isEmpty()) item { Text(stringResource(R.string.condition_reference_empty), color = MaterialTheme.colorScheme.onSurface) }
                            matches.forEach { region ->
                                item(key = region.names.en) {
                                    RegionHeading(region.names, spanish)
                                }
                                items(region.entries, key = { it.id }) { entry ->
                                    WallCrawlCard(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { selectedId = entry.id }) {
                                        Text(entry.names.display(spanish), style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionHeading(region: ReferenceNames, spanish: Boolean) {
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ConditionRegionIcon(region.en)
        Text(region.display(spanish), modifier = Modifier.a11yHeading(),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ReferenceDetail(
    condition: ReferenceCondition,
    region: ReferenceNames,
    spanish: Boolean,
    exerciseCatalog: ExerciseCatalog,
    visualProvider: ExerciseVisualProvider
) {
    val ids = remember(condition) { (condition.sources.flatMap { it.exerciseIds } + condition.relatedReading.flatMap { it.exerciseIds }).distinct() }
    var attempt by remember(condition.id) { mutableIntStateOf(0) }
    val movements = if (ids.isEmpty()) null else readOffline(condition.id, exerciseCatalog, attempt) {
        ids.associateWith { id -> requireNotNull(exerciseCatalog.getExerciseById(id)) }
    }
    var selectedExerciseId by rememberSaveable(condition.id) { mutableStateOf<String?>(null) }

    val uriHandler = LocalUriHandler.current
    var expandedId by rememberSaveable(condition.id) { mutableStateOf<String?>(null) }
    var failedId by remember(condition.id) { mutableStateOf<String?>(null) }
    var showRelated by rememberSaveable(condition.id) { mutableStateOf(false) }
    fun openPublication(id: String, url: String) {
        failedId = try {
            uriHandler.openUri(url)
            null
        } catch (e: IllegalArgumentException) {
            id
        } catch (e: SecurityException) {
            id
        } catch (e: ActivityNotFoundException) {
            id
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            RegionHeading(region, spanish)
            Text(condition.names.display(spanish), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.a11yHeading())
        }
        item { Text(stringResource(R.string.condition_reference_access_note), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text(stringResource(R.string.condition_reference_publications), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.a11yHeading()) }
        items(condition.sources, key = { it.id }) { source ->
            WallCrawlCard(Modifier.fillMaxWidth()) {
                val color = MaterialTheme.colorScheme.onSurface
                Text(source.title, color = color, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (source.fullText) R.string.condition_reference_full else R.string.condition_reference_partial),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                source.executionNote?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                ReferenceExercises(source.exerciseIds, movements, { attempt++ }) { selectedExerciseId = it }
                val expanded = expandedId == source.id
                val notesLabel = stringResource(if (expanded) R.string.condition_reference_hide_notes else R.string.condition_reference_show_notes)
                TextButton(onClick = { expandedId = if (expanded) null else source.id },
                    modifier = Modifier.semantics { contentDescription = "$notesLabel. ${source.title}" }.a11yExpanded(expanded)) {
                    Text(notesLabel, color = color)
                }
                if (expanded) {
                    ReferenceNote(R.string.condition_reference_population, source.population)
                    ReferenceNote(R.string.condition_reference_findings, source.findings)
                    ReferenceNote(R.string.condition_reference_limits, source.limitations)
                }
                val openLabel = stringResource(R.string.condition_reference_open)
                OutlinedButton(modifier = Modifier.semantics { contentDescription = "$openLabel. ${source.title}" }, onClick = {
                    openPublication(source.id, source.url)
                }) { Text(openLabel, color = color) }
                if (failedId == source.id) Text(stringResource(R.string.condition_reference_link_error), Modifier.a11yAnnounced(),
                    color = MaterialTheme.colorScheme.error)
            }
        }
        if (condition.relatedReading.isNotEmpty()) {
            item {
                TextButton(onClick = { showRelated = !showRelated }, modifier = Modifier.a11yExpanded(showRelated)) {
                    Text(stringResource(if (showRelated) R.string.condition_related_hide else R.string.condition_related_show,
                        condition.relatedReading.size), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (showRelated) {
                item { Text(stringResource(R.string.condition_related_note), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(condition.relatedReading, key = { it.url }) { ref ->
                    WallCrawlCard(Modifier.fillMaxWidth()) {
                        Text(ref.title, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(when (ref.access) {
                            "full_text" -> R.string.condition_reference_full
                            "abstract_only" -> R.string.condition_reference_partial
                            else -> R.string.condition_related_registry
                        }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ReferenceExercises(ref.exerciseIds, movements, { attempt++ }) { selectedExerciseId = it }
                        TextButton(onClick = { openPublication(ref.url, ref.url) }) {
                            Text(stringResource(R.string.condition_reference_open), color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (failedId == ref.url) Text(stringResource(R.string.condition_reference_link_error),
                            Modifier.a11yAnnounced(), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    selectedExerciseId?.let { movements?.getOrNull()?.get(it) }?.let { exercise ->
        ExerciseDetailSheet(exercise, visualProvider, onDismiss = { selectedExerciseId = null })
    }
}

@Composable
private fun ReferenceExercises(
    exerciseIds: List<String>,
    movements: Result<Map<String, Exercise>>?,
    retry: () -> Unit,
    select: (String) -> Unit
) {
    if (exerciseIds.isEmpty()) return
    val color = MaterialTheme.colorScheme.onSurface
    val vocabulary = LocalExerciseVocabulary.current
    Text(stringResource(R.string.condition_reference_illustrations), modifier = Modifier.padding(top = 8.dp),
        color = color, style = MaterialTheme.typography.labelLarge)
    when {
        movements == null -> LoadingIndicator()
        movements.isFailure -> {
            Text(stringResource(R.string.exercises_catalog_error), Modifier.a11yAnnounced(), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = retry) { Text(stringResource(R.string.history_retry), color = color) }
        }
        else -> exerciseIds.forEach { id ->
            TextButton(onClick = { select(id) }) { Text(vocabulary.exerciseName(movements.getOrThrow().getValue(id)), color = color) }
        }
    }
}

@Composable
private fun ReferenceNote(@StringRes label: Int, value: String) {
    Text(stringResource(label), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, color = MaterialTheme.colorScheme.onSurface)
}
