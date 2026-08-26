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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.ui.components.ExerciseIllustration
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
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
            .background(ObsidianBlack)
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
                    Text(state.message, color = CrimsonRedLight)
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CATALOG",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = CrimsonRedPrimary
            )
            Text(
                text = "Exercise Library",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = TextWhite
            )
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search exercises, muscles, equipment...", color = TextMuted, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GraphiteSurfaceElevated,
                    unfocusedContainerColor = GraphiteSurface,
                    focusedBorderColor = CrimsonRedPrimary,
                    unfocusedBorderColor = GraphiteBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        }

        // Muscle Group Filter Chips
        item {
            Column {
                Text(
                    text = "TARGET MUSCLE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.availableMuscles) { muscle ->
                        val isSelected = state.selectedMuscle.equals(muscle, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectMuscle(if (isSelected) null else muscle) },
                            label = { Text(muscle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = GraphiteSurface,
                                selectedContainerColor = CrimsonRedPrimary,
                                labelColor = TextSecondary,
                                selectedLabelColor = TextWhite
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = GraphiteBorder,
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
                    text = "EQUIPMENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.availableEquipment) { equipment ->
                        val isSelected = state.selectedEquipment.equals(equipment, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectEquipment(if (isSelected) null else equipment) },
                            label = { Text(equipment, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = GraphiteSurface,
                                selectedContainerColor = WebBlueAccent,
                                labelColor = TextSecondary,
                                selectedLabelColor = ObsidianBlack
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = GraphiteBorder,
                                selectedBorderColor = WebBlueAccent
                            )
                        )
                    }
                }
            }
        }

        // Exercise List Header
        item {
            Text(
                text = "${state.exercises.size} EXERCISES FOUND",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = TextSecondary
            )
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
    WallCrawlCard(
        cornerRadius = 14.dp,
        contentPadding = 14.dp,
        backgroundColor = GraphiteSurfaceElevated,
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
                    text = exercise.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = (exercise.primaryMuscles + exercise.secondaryMuscles).joinToString(" · "),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            exercise.programming?.let { programming ->
                StatBadge(
                    label = programming.mechanics.name,
                    textColor = if (programming.mechanics.name == "COMPOUND") CrimsonRedLight else WebBlueAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (exercise.listedEquipment.isNotEmpty()) {
                StatBadge(
                    label = exercise.listedEquipment.joinToString(", "),
                    icon = Icons.Default.FitnessCenter,
                    textColor = TextMuted
                )
            }
            exercise.programming?.let { programming ->
                StatBadge(
                    label = "${programming.recommendedRepRange.min}–${programming.recommendedRepRange.max} reps",
                    textColor = TextMuted
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GraphiteSurfaceElevated,
        scrimColor = ObsidianBlack.copy(alpha = 0.7f)
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
                text = exercise.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = TextWhite
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = exercise.programming?.coachingSummary
                    ?: "Catalog metadata and illustration are available. Programming review is pending.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Metadata Grid / FlowRow
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBadge(label = "Primary: ${exercise.primaryMuscles.joinToString(", ")}", textColor = CrimsonRedLight)
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    StatBadge(label = "Secondary: ${exercise.secondaryMuscles.joinToString(", ")}", textColor = TextSecondary)
                }
                if (exercise.listedEquipment.isNotEmpty()) {
                    StatBadge(
                        label = "Listed equipment: ${exercise.listedEquipment.joinToString(", ")}",
                        textColor = WebBlueAccent
                    )
                }
                exercise.programming?.let { programming ->
                    StatBadge(
                        label = "Pattern: ${programming.movementPattern.name.replace('_', ' ')}",
                        textColor = TextPrimary
                    )
                    StatBadge(label = "Difficulty: ${programming.difficulty.name}", textColor = TextPrimary)
                    StatBadge(
                        label = "Target: ${programming.recommendedRepRange.min}–${programming.recommendedRepRange.max} reps",
                        textColor = TextPrimary
                    )
                    StatBadge(label = "Fatigue Score: ${programming.fatigueScore}/5", textColor = TextMuted)
                } ?: StatBadge(label = "Programming review pending", textColor = TextMuted)
            }

            Spacer(modifier = Modifier.height(24.dp))

            WallCrawlPrimaryButton(
                text = "Close Details",
                onClick = onDismiss
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
