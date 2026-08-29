package wallcrawl.elopenmike.com.feature.onboarding

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteBorder
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurface
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurfaceElevated
import wallcrawl.elopenmike.com.core.ui.theme.ObsidianBlack
import wallcrawl.elopenmike.com.core.ui.theme.TextPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextSecondary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent

/**
 * First-run onboarding. Today must not generate or render a workout until this screen's
 * [OnboardingViewModel.complete] persists an `onboardingCompleted = true` profile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onCompleted()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        WebBackgroundPattern()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "WELCOME",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = CrimsonRedPrimary
                )
                Text(
                    text = "Let's set up your training",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TextWhite
                )
                Text(
                    text = "A few conservative questions so nothing is assumed about " +
                        "your gym access or recovery.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            item {
                WallCrawlCard(
                    cornerRadius = 16.dp,
                    contentPadding = 16.dp,
                    backgroundColor = GraphiteSurfaceElevated
                ) {
                    Text(
                        text = "YOUR NAME",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::updateName,
                        label = { Text("Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GraphiteSurface,
                            unfocusedContainerColor = GraphiteSurface,
                            focusedBorderColor = CrimsonRedPrimary,
                            unfocusedBorderColor = GraphiteBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = CrimsonRedPrimary,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                WallCrawlCard(
                    cornerRadius = 16.dp,
                    contentPadding = 16.dp,
                    backgroundColor = GraphiteSurfaceElevated
                ) {
                    Text(
                        text = "PRIMARY FITNESS GOAL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = CrimsonRedLight
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FitnessGoal.entries.forEach { goal ->
                        SelectableRow(
                            title = goal.displayName,
                            subtitle = goal.description,
                            isSelected = state.goal == goal,
                            onClick = { viewModel.updateGoal(goal) }
                        )
                    }
                }
            }

            item {
                WallCrawlCard(
                    cornerRadius = 16.dp,
                    contentPadding = 16.dp,
                    backgroundColor = GraphiteSurfaceElevated
                ) {
                    Text(
                        text = "EXPERIENCE LEVEL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ExperienceLevel.entries.forEach { level ->
                        SelectableRow(
                            title = level.displayName,
                            subtitle = null,
                            isSelected = state.experience == level,
                            onClick = { viewModel.updateExperience(level) }
                        )
                    }
                }
            }

            item {
                WallCrawlCard(
                    cornerRadius = 16.dp,
                    contentPadding = 16.dp,
                    backgroundColor = GraphiteSurfaceElevated
                ) {
                    Text(
                        text = "WORKOUT PREFERENCES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Preferred Weight Unit", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WeightUnit.entries.forEach { unit ->
                                val isSelected = state.unit == unit
                                Box(
                                    modifier = Modifier
                                        .background(if (isSelected) CrimsonRedPrimary else GraphiteSurface, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (isSelected) CrimsonRedPrimary else GraphiteBorder, RoundedCornerShape(8.dp))
                                        .clickable { viewModel.updateUnit(unit) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = unit.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) TextWhite else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Preferred Duration", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "~${state.durationMinutes} min", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WebBlueAccent)
                        }
                        Slider(
                            value = state.durationMinutes.toFloat(),
                            onValueChange = { viewModel.updateDurationMinutes(it.toInt()) },
                            valueRange = 20f..120f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = CrimsonRedPrimary,
                                activeTrackColor = CrimsonRedPrimary,
                                inactiveTrackColor = GraphiteBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Training Days / Week", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (2..6).forEach { days ->
                                val isSelected = state.daysPerWeek == days
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(if (isSelected) CrimsonRedPrimary else GraphiteSurface, RoundedCornerShape(8.dp))
                                        .border(1.dp, if (isSelected) CrimsonRedPrimary else GraphiteBorder, RoundedCornerShape(8.dp))
                                        .clickable { viewModel.updateDaysPerWeek(days) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$days",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) TextWhite else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Returning After a Break", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            text = if (state.returningAfterBreakWeeks == 0) {
                                "No recent break"
                            } else {
                                "${state.returningAfterBreakWeeks} wk off"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = WebBlueAccent
                        )
                    }
                    Slider(
                        value = state.returningAfterBreakWeeks.toFloat(),
                        onValueChange = { viewModel.updateReturningAfterBreakWeeks(it.toInt()) },
                        valueRange = 0f..52f,
                        colors = SliderDefaults.colors(
                            thumbColor = CrimsonRedPrimary,
                            activeTrackColor = CrimsonRedPrimary,
                            inactiveTrackColor = GraphiteBorder
                        )
                    )
                }
            }

            item {
                WallCrawlCard(
                    cornerRadius = 16.dp,
                    contentPadding = 16.dp,
                    backgroundColor = GraphiteSurfaceElevated
                ) {
                    Text(
                        text = "AVAILABLE EQUIPMENT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Bodyweight is selected by default. Add only what you actually have.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.equipmentOptions.forEach { equipment ->
                            val isSelected = equipment in state.equipment
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleEquipment(equipment) },
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

            item {
                WallCrawlCard(
                    cornerRadius = 16.dp,
                    contentPadding = 16.dp,
                    backgroundColor = GraphiteSurfaceElevated
                ) {
                    Text(
                        text = "ANY SENSITIVE AREAS?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = CrimsonRedLight
                    )
                    Text(
                        text = "Optional. We'll avoid or substitute exercises that stress these.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.constraintOptions.forEach { constraint ->
                            val isSelected = constraint in state.constraints
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleConstraint(constraint) },
                                label = { Text(constraint.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
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

            item {
                if (state.error != null) {
                    Text(text = state.error.orEmpty(), fontSize = 13.sp, color = CrimsonRedLight)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                WallCrawlPrimaryButton(
                    text = if (state.isSaving) "Saving…" else "Complete Setup",
                    onClick = { viewModel.complete() },
                    enabled = !state.isSaving && state.name.isNotBlank() && state.equipment.isNotEmpty()
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (isSelected) Color(0x22E63946) else GraphiteSurface,
                RoundedCornerShape(10.dp)
            )
            .border(
                1.dp,
                if (isSelected) CrimsonRedPrimary else GraphiteBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) TextWhite else TextPrimary
                )
                if (subtitle != null) {
                    Text(text = subtitle, fontSize = 12.sp, color = TextSecondary)
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = CrimsonRedPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
