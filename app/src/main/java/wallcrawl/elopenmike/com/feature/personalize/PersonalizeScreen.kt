package wallcrawl.elopenmike.com.feature.personalize

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.BreakDurationHelper
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.ui.components.BandSetupControls
import wallcrawl.elopenmike.com.core.ui.components.BreakDurationSelector
import wallcrawl.elopenmike.com.core.ui.components.ConditionSelectionControls
import wallcrawl.elopenmike.com.core.ui.components.MovementCapabilityQuestion
import wallcrawl.elopenmike.com.core.ui.components.ReportedConditionPicker
import wallcrawl.elopenmike.com.core.ui.components.StepHeader
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.localization.messageRes
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite

/**
 * Three-step personalization: equipment, experience and movement, then conditions.
 * Back on the first step ends in [onClose]; Finish saves and ends in [onFinished].
 */
@Composable
fun PersonalizeScreen(
    viewModel: PersonalizeViewModel,
    onClose: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler { if (!state.isSaving && !viewModel.previousStep()) onClose() }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            StepHeader(
                stepNumber = state.currentStep.stepNumber,
                totalSteps = PersonalizeStep.entries.size,
                canGoBack = !state.isSaving,
                onBack = { if (!viewModel.previousStep()) onClose() },
                counterRes = R.string.personalize_step_counter,
                showBack = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (!state.isLoaded) {
                    if (state.conditionLoadFailed) {
                        Text(stringResource(R.string.condition_load_error), color = MaterialTheme.colorScheme.onSurface)
                    }
                } else AnimatedContent(
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
                    label = "PersonalizeStepTransition",
                    modifier = Modifier.fillMaxSize()
                ) { step ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
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
                            PersonalizeStep.EQUIPMENT -> EquipmentStep(state = state, viewModel = viewModel)
                            PersonalizeStep.EXPERIENCE_MOVEMENT -> ExperienceMovementStep(state = state, viewModel = viewModel)
                            PersonalizeStep.CONDITIONS -> SafetyStep(state = state, viewModel = viewModel)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            val error = state.error
            if (error != null) {
                Text(
                    text = stringResource(error.messageRes),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            WallCrawlPrimaryButton(
                text = stringResource(
                    when {
                        state.isSaving -> R.string.onboarding_action_saving
                        state.isLastStep -> R.string.personalize_finish
                        else -> R.string.personalize_next
                    }
                ),
                onClick = { viewModel.nextStep() },
                enabled = state.isLoaded && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentStep(
    state: PersonalizeUiState,
    viewModel: PersonalizeViewModel
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
                    text = stringResource(R.string.onboarding_equipment_quick_heading),
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
                    Text(
                        stringResource(R.string.onboarding_equipment_bodyweight_only),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { viewModel.selectFullGymEquipment() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.onboarding_equipment_full_gym),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = stringResource(R.string.onboarding_equipment_heading),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.onboarding_equipment_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            val vocabulary = LocalExerciseVocabulary.current
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.equipmentOptions.filterNot { it in StandardEquipment.BAND_SETUPS }.forEach { equipment ->
                    val isSelected = equipment in state.equipment
                    FilterChip(
                        selected = isSelected,
                        // The canonical English name stays the stored value and the
                        // filter key; only the chip's label is translated.
                        onClick = { viewModel.toggleEquipment(equipment) },
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
        WallCrawlCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
            BandSetupControls(state.equipment, viewModel::toggleEquipment)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SafetyStep(
    state: PersonalizeUiState,
    viewModel: PersonalizeViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WallCrawlCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
            ReportedConditionPicker(state.reportedConditions, !state.isSaving, viewModel::setReportedCondition)
        }
        if (state.conditionLoadFailed) Text(stringResource(R.string.condition_load_error), color = MaterialTheme.colorScheme.onSurface)
        ConditionSelectionControls(state.conditionRecords, state.conditionDraft ?: state.retainedConditions,
            viewModel::updateConditions, !state.isSaving)
        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = stringResource(R.string.onboarding_safety_heading),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_safety_hint),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.onboarding_safety_none_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.onboarding_safety_none_subtitle),
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
                text = stringResource(R.string.onboarding_safety_tag_prompt),
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
                        label = {
                            Text(
                                text = stringResource(constraint.labelRes),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) TextWhite else MaterialTheme.colorScheme.onSurface
                            )
                        },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExperienceMovementStep(
    state: PersonalizeUiState,
    viewModel: PersonalizeViewModel
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
                text = stringResource(R.string.onboarding_experience_heading),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExperienceLevel.entries.forEach { level ->
                    val isSelected = state.experience == level
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.updateExperience(level) },
                        label = {
                            Text(
                                text = stringResource(level.labelRes),
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
                    text = stringResource(R.string.onboarding_break_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = CrimsonRedLight,
                    modifier = Modifier.weight(1f)
                )
                if (state.returningAfterBreakWeeks > 0) {
                    Text(
                        text = stringResource(R.string.break_reentry_active),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_break_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            BreakDurationSelector(
                weeks = state.returningAfterBreakWeeks,
                onSelectWeeks = { viewModel.updateReturningAfterBreakWeeks(it) }
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(
                    BreakDurationHelper.guidanceFor(state.returningAfterBreakWeeks).messageRes
                ),
                fontSize = 12.sp,
                color = if (state.returningAfterBreakWeeks >= 52) CrimsonRedLight else MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = stringResource(
                R.string.movement_capability_current_use
            ),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
