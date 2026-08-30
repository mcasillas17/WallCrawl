package wallcrawl.elopenmike.com.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.model.BreakDurationHelper
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.MovementCapabilityQuestion
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlOutlinedButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent

/**
 * Friendly multi-step onboarding wizard. Only shown during first-time setup
 * until [OnboardingViewModel.complete] persists `onboardingCompleted = true`.
 */
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        WebBackgroundPattern()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Wizard Header & Progress Bar
            OnboardingHeader(
                currentStep = state.currentStep,
                onBack = { viewModel.previousStep() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step Content Area with smooth transition
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = state.currentStep,
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "OnboardingStepTransition",
                    modifier = Modifier.fillMaxSize()
                ) { step ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(step.titleRes),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(step.subtitleRes),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            when (step) {
                                OnboardingStep.WELCOME -> WelcomeStep(state = state, viewModel = viewModel)
                                OnboardingStep.GOALS -> GoalsStep(state = state, viewModel = viewModel)
                                OnboardingStep.EXPERIENCE_UNIT -> ExperienceAndUnitStep(state = state, viewModel = viewModel)
                                OnboardingStep.MOVEMENT_CAPABILITY -> MovementCapabilityStep(
                                    state = state,
                                    viewModel = viewModel
                                )
                                OnboardingStep.SCHEDULE -> ScheduleStep(state = state, viewModel = viewModel)
                                OnboardingStep.EQUIPMENT -> EquipmentStep(state = state, viewModel = viewModel)
                                OnboardingStep.SAFETY -> SafetyStep(state = state, viewModel = viewModel)
                                OnboardingStep.SUMMARY -> SummaryStep(state = state, viewModel = viewModel)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            // Error message if any
            val error = state.error
            if (error != null) {
                Text(
                    text = stringResource(error.messageRes),
                    fontSize = 13.sp,
                    color = CrimsonRedLight,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Bottom Navigation Actions
            OnboardingBottomNav(
                state = state,
                onBack = { viewModel.previousStep() },
                onNext = { viewModel.nextStep() }
            )
        }
    }
}

@Composable
private fun OnboardingHeader(
    currentStep: OnboardingStep,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (!currentStep.isFirst()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }

            Text(
                text = "STEP ${currentStep.stepNumber} OF ${OnboardingStep.totalSteps}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = CrimsonRedPrimary
            )

            Spacer(modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Segmented Step Progress Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OnboardingStep.entries.forEach { step ->
                val isCompletedOrActive = step.ordinal <= currentStep.ordinal
                val isCurrent = step == currentStep
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                isCurrent -> CrimsonRedPrimary
                                isCompletedOrActive -> CrimsonRedLight.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                )
            }
        }
    }
}

private fun OnboardingStep.isFirst(): Boolean = ordinal == 0

@Composable
private fun WelcomeStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 20.dp
        ) {
            Text(
                text = "YOUR CODENAME",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = CrimsonRedPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "What should WallCrawl call you during workouts?",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                placeholder = { Text("e.g. Peter, Gwen, Miles, Alex", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = CrimsonRedPrimary
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
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GoalsStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = "FITNESS GOALS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = CrimsonRedLight
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select all that apply to your current training block:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FitnessGoal.entries.forEach { goal ->
                SelectableRow(
                    title = goal.displayName,
                    subtitle = goal.description,
                    isSelected = goal in state.goals,
                    onClick = { viewModel.toggleGoal(goal) }
                )
            }
        }
    }
}

@Composable
private fun ExperienceAndUnitStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 20.dp
        ) {
            Text(
                text = "PREFERRED WEIGHT UNIT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = CrimsonRedPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Used for weight prescriptions, plates, and logging.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WeightUnit.entries.forEach { unit ->
                    val isSelected = state.unit == unit
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable { viewModel.updateUnit(unit) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = unit.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) TextWhite else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (unit == WeightUnit.LBS) "Pounds (lbs)" else "Kilograms (kg)",
                                fontSize = 11.sp,
                                color = if (isSelected) TextWhite.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = "EXPERIENCE LEVEL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            ExperienceLevel.entries.forEach { level ->
                SelectableRow(
                    title = level.displayName,
                    subtitle = when (level) {
                        ExperienceLevel.BEGINNER -> "New to structured training or building a fresh baseline"
                        ExperienceLevel.INTERMEDIATE -> "Comfortable with main compound lifts and routine consistency"
                        ExperienceLevel.ADVANCED -> "Years of progressive overload and periodized training"
                    },
                    isSelected = state.experience == level,
                    onClick = { viewModel.updateExperience(level) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = "TRAINING DAYS PER WEEK",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when (state.daysPerWeek) {
                    2 -> "2 days • Full body maintenance schedule."
                    3 -> "3 days • Classic full-body progression split (Recommended)."
                    4 -> "4 days • Upper / Lower balanced frequency."
                    5 -> "5 days • Push / Pull / Legs adaptive split."
                    else -> "6 days • High frequency dedicated training."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (2..6).forEach { days ->
                    val isSelected = state.daysPerWeek == days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .clickable { viewModel.updateDaysPerWeek(days) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$days",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) TextWhite else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TARGET DURATION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${state.durationMinutes} min",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Includes warm-up sets, working volume, and rest periods.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Slider(
                value = state.durationMinutes.toFloat(),
                onValueChange = { viewModel.updateDurationMinutes(it.toInt()) },
                valueRange = 20f..120f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = CrimsonRedPrimary,
                    activeTrackColor = CrimsonRedPrimary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RETURNING AFTER A BREAK?",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = CrimsonRedLight
                )
                if (state.returningAfterBreakWeeks > 0) {
                    Text(
                        text = "Re-entry Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pick your break duration so WallCrawl can calibrate safe starting volume and protect your connective tissue.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            BreakDurationDropdownSelector(
                weeks = state.returningAfterBreakWeeks,
                onSelectWeeks = { viewModel.updateReturningAfterBreakWeeks(it) }
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = wallcrawl.elopenmike.com.core.model.BreakDurationHelper.guidanceText(state.returningAfterBreakWeeks),
                fontSize = 12.sp,
                color = if (state.returningAfterBreakWeeks >= 52) CrimsonRedLight else MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BreakDurationDropdownSelector(
    weeks: Int,
    onSelectWeeks: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentRange = wallcrawl.elopenmike.com.core.model.BreakDurationHelper.findMatchingRange(weeks)

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    if (expanded) CrimsonRedPrimary else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(12.dp)
                )
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentRange.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentRange.weeks == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentRange.subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Close range menu" else "Select break range",
                    tint = if (expanded) CrimsonRedPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
        ) {
            wallcrawl.elopenmike.com.core.model.BreakDurationHelper.RANGES.forEach { range ->
                val isSelected = range == currentRange
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = range.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) CrimsonRedLight else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = range.subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    },
                    onClick = {
                        onSelectWeeks(range.weeks)
                        expanded = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) CrimsonRedPrimary.copy(alpha = 0.12f) else Color.Transparent)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GEAR QUICK ACTIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { viewModel.resetEquipmentToBodyweight() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Bodyweight Only", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { viewModel.selectAllEquipment() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Full Gym Access", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = "AVAILABLE GEAR",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = CrimsonRedLight
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select what you have in your home or gym:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

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
                        label = {
                            Text(
                                text = equipment,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SafetyStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = "PROTECT SENSITIVE JOINTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = CrimsonRedLight
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tell us if any joints need conservative exercise selection. WallCrawl will automatically filter or substitute high-stress movements.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            val hasNoConstraints = state.constraints.isEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (hasNoConstraints) Color(0x2210B981) else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (hasNoConstraints) Color(0xFF10B981) else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable { viewModel.clearConstraints() }
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Feeling 100% / No Restrictions",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Full catalog of exercises available without joint filters.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasNoConstraints) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Or tag specific areas to protect:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = CrimsonRedPrimary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@Composable
private fun MovementCapabilityStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = stringResource(wallcrawl.elopenmike.com.R.string.movement_capability_intro),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    wallcrawl.elopenmike.com.R.string.movement_capability_future_use
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        MovementCapabilityType.entries.forEach { type ->
            WallCrawlCard(
                cornerRadius = 16.dp,
                contentPadding = 16.dp
            ) {
                MovementCapabilityQuestion(
                    type = type,
                    selectedLevel = state.capabilityAnswers[type],
                    onSelect = { level ->
                        viewModel.updateMovementCapability(type, level)
                    },
                    showAnswerRequired = state.error != null &&
                        state.unansweredCapability == type
                )
            }
        }
    }
}

@Composable
private fun SummaryStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 20.dp
        ) {
            Text(
                text = "TRAINING BLUEPRINT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = CrimsonRedPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))

            SummaryRow(label = "Crawler Name", value = state.name.ifBlank { "Crawler" })
            SummaryRow(
                label = "Fitness Goals",
                value = if (state.goals.isEmpty()) "General Fitness" else state.goals.joinToString(", ") { it.displayName }
            )
            SummaryRow(label = "Experience", value = state.experience.displayName)
            SummaryRow(label = "Schedule", value = "${state.daysPerWeek} days/wk • ~${state.durationMinutes} min")
            SummaryRow(label = "Units", value = "${state.unit.name} (${state.unit.symbol})")
            SummaryRow(label = "Equipment", value = "${state.equipment.size} gear types selected")
            SummaryRow(
                label = stringResource(
                    wallcrawl.elopenmike.com.R.string.movement_capability_summary_label
                ),
                value = stringResource(
                    wallcrawl.elopenmike.com.R.string.movement_capability_summary_value
                )
            )
            SummaryRow(
                label = "Sensitive Areas",
                value = if (state.constraints.isEmpty()) "None (100% Clear)" else state.constraints.joinToString { it.displayName }
            )
            if (state.returningAfterBreakWeeks > 0) {
                SummaryRow(
                    label = "Break Recovery",
                    value = wallcrawl.elopenmike.com.core.model.BreakDurationHelper.formatDetailedLabel(state.returningAfterBreakWeeks)
                )
            }
        }

        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = "⚡ Ready for Action",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "WallCrawl will now configure your local adaptive planner. You can modify any of these preferences at any time from your Profile.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun OnboardingBottomNav(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!state.isFirstStep) {
            WallCrawlOutlinedButton(
                text = "Back",
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
        }

        WallCrawlPrimaryButton(
            text = when {
                state.isSaving -> "Saving Profile…"
                state.isLastStep -> "Start Training"
                else -> "Continue"
            },
            onClick = onNext,
            enabled = !state.isSaving,
            modifier = Modifier.weight(if (state.isFirstStep) 1f else 2f)
        )
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
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) CrimsonRedPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
            )
            .border(
                1.dp,
                if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.outline,
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
                    color = if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
