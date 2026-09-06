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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import wallcrawl.elopenmike.com.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import wallcrawl.elopenmike.com.core.model.BreakDurationHelper
import wallcrawl.elopenmike.com.core.model.BreakRange
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.components.BreakDurationSelector
import wallcrawl.elopenmike.com.core.ui.components.LanguageChip
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.MovementCapabilityQuestion
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlOutlinedButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.descriptionRes
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.localization.longLabelRes
import wallcrawl.elopenmike.com.core.ui.localization.messageRes
import wallcrawl.elopenmike.com.core.ui.localization.shortLabelRes
import wallcrawl.elopenmike.com.core.ui.localization.subtitleRes
import wallcrawl.elopenmike.com.core.ui.localization.titleRes
import wallcrawl.elopenmike.com.core.ui.localization.trainingDaysDescriptionRes
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
    modifier: Modifier = Modifier,
    /**
     * Restore entry point offered under the first step's primary action, supplied by the
     * navigation graph.
     *
     * It sits here so a fresh install, or an install that has just deleted everything, can
     * restore an exported file without first being made to build a profile it would discard.
     * Opening it neither saves a profile nor advances the wizard, so the draft survives.
     */
    restoreFromArchive: @Composable () -> Unit = {},
    /**
     * True while a restore is reading the chosen document.
     *
     * The wizard must not advance meanwhile: finishing it writes a whole profile, which
     * would overwrite the one the restore is about to commit.
     */
    isRestoreInFlight: Boolean = false
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
                // The actions sit at the bottom of this column, so it has to end above the
                // navigation bar and above the keyboard rather than behind either.
                .navigationBarsPadding()
                .imePadding()
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
                                OnboardingStep.WELCOME ->
                                    WelcomeStep(state = state, viewModel = viewModel)
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
                isRestoreInFlight = isRestoreInFlight,
                onBack = { viewModel.previousStep() },
                onNext = { viewModel.nextStep() }
            )

            // Under the primary action, and only on the first step: someone who has an
            // archive is restoring instead of filling the wizard in, and everyone else
            // should be reading the name field rather than this.
            if (state.currentStep == OnboardingStep.WELCOME) {
                restoreFromArchive()
            }
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
            // Both side slots are the language chip's width so the step counter stays
            // centred, whether or not this step can go back.
            if (!currentStep.isFirst()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            val locale = LocalConfiguration.current.locales[0]
            Text(
                text = stringResource(
                    R.string.onboarding_step_counter,
                    LocaleFormatting.formatCount(currentStep.stepNumber, locale),
                    LocaleFormatting.formatCount(OnboardingStep.totalSteps, locale)
                ),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = CrimsonRedPrimary
            )

            // Reachable from the first screen, before anything has to be typed, so a
            // Spanish reader who landed on an English install can switch and carry on. It
            // is not a step: the wizard still has the same eight.
            LanguageChip()
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
                text = stringResource(R.string.onboarding_codename_heading),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = CrimsonRedPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_codename_prompt),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                placeholder = {
                    Text(
                        stringResource(R.string.onboarding_codename_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
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
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                text = stringResource(R.string.onboarding_goals_heading),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = CrimsonRedLight
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.onboarding_goals_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FitnessGoal.entries.forEach { goal ->
                SelectableRow(
                    title = stringResource(goal.labelRes),
                    subtitle = stringResource(goal.descriptionRes),
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
                text = stringResource(R.string.onboarding_unit_heading),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = CrimsonRedPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_unit_hint),
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
                                text = stringResource(unit.shortLabelRes),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) TextWhite else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(unit.longLabelRes),
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
                text = stringResource(R.string.onboarding_experience_heading),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            ExperienceLevel.entries.forEach { level ->
                SelectableRow(
                    title = stringResource(level.labelRes),
                    subtitle = stringResource(level.descriptionRes),
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
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = stringResource(R.string.onboarding_schedule_days_heading),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(trainingDaysDescriptionRes(state.daysPerWeek)),
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
                            text = LocaleFormatting.formatCount(days, locale),
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
                    text = stringResource(R.string.onboarding_duration_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.summary_duration_value,
                        LocaleFormatting.formatCount(state.durationMinutes, locale)
                    ),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_duration_hint),
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
                        .clickable { viewModel.selectAllEquipment() }
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
                color = CrimsonRedLight
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
                state.equipmentOptions.forEach { equipment ->
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
                text = stringResource(R.string.onboarding_safety_heading),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = CrimsonRedLight
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
            val locale = LocalConfiguration.current.locales[0]
            val separator = stringResource(R.string.list_separator)
            Text(
                text = stringResource(R.string.onboarding_summary_heading),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = CrimsonRedPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))

            SummaryRow(
                label = stringResource(R.string.onboarding_summary_label_name),
                value = state.name.ifBlank { stringResource(R.string.default_crawler_name) }
            )
            SummaryRow(
                label = stringResource(R.string.onboarding_summary_label_goals),
                value = state.goals
                    .ifEmpty { setOf(FitnessGoal.GENERAL_FITNESS) }
                    .let { goals -> FitnessGoal.entries.filter { it in goals } }
                    .map { stringResource(it.labelRes) }
                    .joinToString(separator)
            )
            SummaryRow(
                label = stringResource(R.string.onboarding_summary_label_experience),
                value = stringResource(state.experience.labelRes)
            )
            SummaryRow(
                label = stringResource(R.string.onboarding_summary_label_schedule),
                value = stringResource(
                    R.string.onboarding_summary_value_schedule,
                    pluralStringResource(
                        R.plurals.count_days_per_week,
                        state.daysPerWeek,
                        LocaleFormatting.formatCount(state.daysPerWeek, locale)
                    ),
                    stringResource(
                        R.string.summary_duration_value,
                        LocaleFormatting.formatCount(state.durationMinutes, locale)
                    )
                )
            )
            SummaryRow(
                label = stringResource(R.string.onboarding_summary_label_units),
                value = stringResource(
                    R.string.onboarding_summary_value_units,
                    stringResource(state.unit.shortLabelRes),
                    state.unit.symbol
                )
            )
            SummaryRow(
                label = stringResource(R.string.onboarding_summary_label_equipment),
                value = pluralStringResource(
                    R.plurals.count_gear_types,
                    state.equipment.size,
                    LocaleFormatting.formatCount(state.equipment.size, locale)
                )
            )
            SummaryRow(
                label = stringResource(R.string.movement_capability_summary_label),
                value = pluralStringResource(
                    R.plurals.count_preferences_answered,
                    state.capabilityAnswers.size,
                    LocaleFormatting.formatCount(state.capabilityAnswers.size, locale)
                )
            )
            SummaryRow(
                label = stringResource(R.string.onboarding_summary_label_sensitive),
                value = if (state.constraints.isEmpty()) {
                    stringResource(R.string.onboarding_summary_value_no_sensitive)
                } else {
                    TrainingConstraint.entries
                        .filter { it in state.constraints }
                        .map { stringResource(it.labelRes) }
                        .joinToString(separator)
                }
            )
            if (state.returningAfterBreakWeeks > 0) {
                val range: BreakRange =
                    BreakDurationHelper.findMatchingRange(state.returningAfterBreakWeeks)
                SummaryRow(
                    label = stringResource(R.string.onboarding_summary_label_break),
                    value = stringResource(
                        R.string.onboarding_summary_value_break,
                        stringResource(range.titleRes),
                        stringResource(range.subtitleRes)
                    )
                )
            }
        }

        WallCrawlCard(
            cornerRadius = 16.dp,
            contentPadding = 16.dp
        ) {
            Text(
                text = stringResource(R.string.onboarding_ready_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.onboarding_ready_body),
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
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Spanish runs longer than English here, so the value gets the room it needs
        // instead of being squeezed onto one line.
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f)
        )
    }
}

@Composable
private fun OnboardingBottomNav(
    state: OnboardingUiState,
    isRestoreInFlight: Boolean,
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
                text = stringResource(R.string.action_back),
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
        }

        WallCrawlPrimaryButton(
            text = stringResource(
                when {
                    state.isSaving -> R.string.onboarding_action_saving
                    isRestoreInFlight -> R.string.local_data_restore_progress
                    state.isLastStep -> R.string.onboarding_action_start
                    else -> R.string.onboarding_action_continue
                }
            ),
            onClick = onNext,
            enabled = !state.isSaving && !isRestoreInFlight,
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
