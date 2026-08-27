package wallcrawl.elopenmike.com.feature.profile

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.components.StatBadge
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
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

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenCredits: () -> Unit,
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
            is ProfileUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CrimsonRedPrimary)
                }
            }

            is ProfileUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = CrimsonRedLight)
                }
            }

            is ProfileUiState.Success -> {
                ProfileContent(
                    profile = state.profile,
                    availableEquipment = state.availableEquipmentOptions,
                    availableMuscles = state.availableMuscleOptions,
                    onUpdateGoal = { viewModel.updateGoal(it) },
                    onUpdateExperience = { viewModel.updateExperience(it) },
                    onUpdateDuration = { viewModel.updateDuration(it) },
                    onUpdateDaysPerWeek = { viewModel.updateDaysPerWeek(it) },
                    onUpdateUnit = { viewModel.updateUnit(it) },
                    onToggleEquipment = { viewModel.toggleEquipment(it) },
                    onSetMusclePriority = { muscle, priority -> viewModel.setMusclePriority(muscle, priority) },
                    onOpenCredits = onOpenCredits
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(
    profile: UserProfile,
    availableEquipment: List<String>,
    availableMuscles: List<String>,
    onUpdateGoal: (FitnessGoal) -> Unit,
    onUpdateExperience: (ExperienceLevel) -> Unit,
    onUpdateDuration: (Int) -> Unit,
    onUpdateDaysPerWeek: (Int) -> Unit,
    onUpdateUnit: (WeightUnit) -> Unit,
    onToggleEquipment: (String) -> Unit,
    onSetMusclePriority: (String, PriorityLevel) -> Unit,
    onOpenCredits: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "SETTINGS",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = CrimsonRedPrimary
            )
            Text(
                text = "Training Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = TextWhite
            )
        }

        // 1. Primary Goal Selector
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
                    val isSelected = profile.primaryGoal == goal
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
                            .clickable { onUpdateGoal(goal) }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = goal.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TextWhite else TextPrimary
                                )
                                Text(
                                    text = goal.description,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
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
            }
        }

        // 2. Units & Preferences
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

                // Preferred Unit Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Preferred Weight Unit", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeightUnit.entries.forEach { unit ->
                            val isSelected = profile.preferredUnit == unit
                            Box(
                                modifier = Modifier
                                    .background(if (isSelected) CrimsonRedPrimary else GraphiteSurface, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) CrimsonRedPrimary else GraphiteBorder, RoundedCornerShape(8.dp))
                                    .clickable { onUpdateUnit(unit) }
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

                // Target Duration Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Preferred Duration", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(text = "~${profile.preferredDurationMinutes} min", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WebBlueAccent)
                    }
                    Slider(
                        value = profile.preferredDurationMinutes.toFloat(),
                        onValueChange = { onUpdateDuration(it.toInt()) },
                        valueRange = 30f..90f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = CrimsonRedPrimary,
                            activeTrackColor = CrimsonRedPrimary,
                            inactiveTrackColor = GraphiteBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Days per week
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Training Days / Week", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (2..6).forEach { days ->
                            val isSelected = profile.daysPerWeek == days
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(if (isSelected) CrimsonRedPrimary else GraphiteSurface, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) CrimsonRedPrimary else GraphiteBorder, RoundedCornerShape(8.dp))
                                    .clickable { onUpdateDaysPerWeek(days) },
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
            }
        }

        // 3. Available Equipment Selector
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
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableEquipment.forEach { equipment ->
                        val isSelected = equipment in profile.availableEquipment
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleEquipment(equipment) },
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

        // 4. Muscle Priorities Matrix
        item {
            WallCrawlCard(
                cornerRadius = 16.dp,
                contentPadding = 16.dp,
                backgroundColor = GraphiteSurfaceElevated
            ) {
                Text(
                    text = "MUSCLE PRIORITIES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = CrimsonRedLight
                )
                Spacer(modifier = Modifier.height(12.dp))

                availableMuscles.forEach { muscle ->
                    val currentPriority = profile.musclePriorities[muscle] ?: PriorityLevel.NORMAL
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = muscle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PriorityLevel.entries.forEach { level ->
                                val isSelected = currentPriority == level
                                val bgColor = when {
                                    isSelected && level == PriorityLevel.HIGH -> CrimsonRedPrimary
                                    isSelected && level == PriorityLevel.NORMAL -> GraphiteSurfaceElevated
                                    isSelected && level == PriorityLevel.LOW -> GraphiteSurface
                                    else -> GraphiteSurface
                                }
                                Box(
                                    modifier = Modifier
                                        .background(bgColor, RoundedCornerShape(6.dp))
                                        .border(
                                            1.dp,
                                            if (isSelected) CrimsonRedLight else GraphiteBorder,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onSetMusclePriority(muscle, level) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = level.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) TextWhite else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            WallCrawlCard(
                cornerRadius = 16.dp,
                contentPadding = 16.dp,
                backgroundColor = GraphiteSurfaceElevated,
                modifier = Modifier.clickable(onClick = onOpenCredits)
            ) {
                Text(
                    text = "CREDITS & LICENCES",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Who made the exercise illustrations, and the licence they ship under.",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
