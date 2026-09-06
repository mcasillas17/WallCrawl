package wallcrawl.elopenmike.com.feature.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.ui.components.ExerciseIllustration
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.ExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    viewModel: ExercisesViewModel,
    visualProvider: ExerciseVisualProvider,
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
            is ExercisesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrimsonRedPrimary)
                }
            }

            is ExercisesUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(state.messageRes), color = CrimsonRedLight)
                }
            }

            is ExercisesUiState.Success -> {
                ExercisesContent(
                    state = state,
                    onQueryChanged = { viewModel.onQueryChanged(it) },
                    onSelectMuscle = { viewModel.selectMuscle(it) },
                    onSelectEquipment = { viewModel.selectEquipment(it) },
                    onOpenDetail = { viewModel.openExerciseDetail(it) }
                )

                // Exercise Detail Modal Bottom Sheet
                if (state.selectedExerciseDetail != null) {
                    ExerciseDetailSheet(
                        exercise = state.selectedExerciseDetail,
                        visualProvider = visualProvider,
                        onDismiss = { viewModel.closeExerciseDetail() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExercisesContent(
    state: ExercisesUiState.Success,
    onQueryChanged: (String) -> Unit,
    onSelectMuscle: (String?) -> Unit,
    onSelectEquipment: (String?) -> Unit,
    onOpenDetail: (Exercise) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.exercises_eyebrow),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = CrimsonRedPrimary
            )
            Text(
                text = stringResource(R.string.exercises_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(R.string.exercises_search_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
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
                )
            )
        }

        // Muscle Group Filter Chips
        item {
            Column {
                Text(
                    text = stringResource(R.string.exercises_filter_muscle),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vocabulary.musclesByLabel(state.availableMuscles)) { muscle ->
                        val isSelected = state.selectedMuscle.equals(muscle, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            // The canonical English name is the filter key; the chip only
                            // shows the reader's word for it.
                            onClick = { onSelectMuscle(if (isSelected) null else muscle) },
                            label = {
                                Text(
                                    text = vocabulary.muscle(muscle),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) TextWhite else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                selectedContainerColor = CrimsonRedPrimary,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedLabelColor = TextWhite
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = CrimsonRedPrimary
                            )
                        )
                    }
                }
            }
        }

        // Equipment Filter Chips
        item {
            Column {
                Text(
                    text = stringResource(R.string.exercises_filter_equipment),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vocabulary.equipmentByLabel(state.availableEquipment)) { equipment ->
                        val isSelected = state.selectedEquipment.equals(equipment, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectEquipment(if (isSelected) null else equipment) },
                            label = {
                                Text(
                                    text = vocabulary.equipment(equipment),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
            }
        }

        // Exercise List Header
        item {
            Text(
                text = pluralStringResource(
                    R.plurals.count_exercises_found,
                    state.exercises.size,
                    LocaleFormatting.formatCount(state.exercises.size, locale)
                ).uppercase(locale),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.exercises.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.exercises_empty),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Exercise Items
        items(state.exercises) { exercise ->
            ExerciseListItem(exercise = exercise, onClick = { onOpenDetail(exercise) })
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExerciseListItem(
    exercise: Exercise,
    onClick: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    val separator = stringResource(R.string.detail_separator)
    WallCrawlCard(
        cornerRadius = 14.dp,
        contentPadding = 14.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vocabulary.exerciseName(exercise),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = vocabulary
                        .muscles(exercise.primaryMuscles + exercise.secondaryMuscles)
                        .joinToString(separator),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            exercise.programming?.let { programming ->
                StatBadge(
                    label = stringResource(programming.mechanics.labelRes),
                    textColor = if (programming.mechanics == MechanicsType.COMPOUND) {
                        CrimsonRedLight
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val listSeparator = stringResource(R.string.list_separator)
            if (exercise.listedEquipment.isNotEmpty()) {
                StatBadge(
                    label = vocabulary.equipment(exercise.listedEquipment)
                        .joinToString(listSeparator),
                    icon = Icons.Default.FitnessCenter,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            exercise.programming?.recommendedRepRange?.let { repRange ->
                StatBadge(
                    label = pluralStringResource(
                        R.plurals.count_reps,
                        repRange.max,
                        stringResource(
                            R.string.prescription_rep_range,
                            LocaleFormatting.formatCount(repRange.min, locale),
                            LocaleFormatting.formatCount(repRange.max, locale)
                        )
                    ),
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExerciseDetailSheet(
    exercise: Exercise,
    visualProvider: ExerciseVisualProvider,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    val listSeparator = stringResource(R.string.list_separator)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Illustration Component
            ExerciseIllustration(
                exercise = exercise,
                visualProvider = visualProvider,
                height = 200
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = vocabulary.exerciseName(exercise),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = vocabulary.coachingSummary(exercise)
                    ?: exercise.programming?.coachingSummary
                    ?: stringResource(R.string.exercise_detail_no_programming),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Said plainly rather than left for the reader to work out: this one summary
            // has no Spanish yet, so it is shown in the language it was written in.
            if (vocabulary.hasUntranslatedCoachingSummary(exercise)) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.exercise_detail_original_language),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metadata Grid / FlowRow
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBadge(
                    label = stringResource(
                        R.string.exercise_detail_primary,
                        vocabulary.muscles(exercise.primaryMuscles).joinToString(listSeparator)
                    ),
                    textColor = CrimsonRedLight
                )
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    StatBadge(
                        label = stringResource(
                            R.string.exercise_detail_secondary,
                            vocabulary.muscles(exercise.secondaryMuscles)
                                .joinToString(listSeparator)
                        ),
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (exercise.listedEquipment.isNotEmpty()) {
                    StatBadge(
                        label = stringResource(
                            R.string.exercise_detail_equipment,
                            vocabulary.equipment(exercise.listedEquipment)
                                .joinToString(listSeparator)
                        ),
                        textColor = MaterialTheme.colorScheme.secondary
                    )
                }
                exercise.programming?.let { programming ->
                    StatBadge(
                        label = stringResource(
                            R.string.exercise_detail_pattern,
                            stringResource(programming.movementPattern.labelRes)
                        ),
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                    StatBadge(
                        label = stringResource(
                            R.string.exercise_detail_difficulty,
                            stringResource(programming.difficulty.labelRes)
                        ),
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                    programming.recommendedRepRange?.let { repRange ->
                        StatBadge(
                            label = pluralStringResource(
                                R.plurals.count_target_reps,
                                repRange.max,
                                stringResource(
                                    R.string.prescription_rep_range,
                                    LocaleFormatting.formatCount(repRange.min, locale),
                                    LocaleFormatting.formatCount(repRange.max, locale)
                                )
                            ),
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    StatBadge(
                        label = stringResource(
                            R.string.exercise_detail_fatigue,
                            LocaleFormatting.formatCount(programming.fatigueScore, locale)
                        ),
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: StatBadge(
                    label = stringResource(R.string.exercise_detail_review_pending),
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            WallCrawlPrimaryButton(
                text = stringResource(R.string.exercise_detail_close),
                onClick = onDismiss
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
