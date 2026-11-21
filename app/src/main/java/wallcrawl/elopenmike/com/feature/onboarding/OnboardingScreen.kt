package wallcrawl.elopenmike.com.feature.onboarding

import wallcrawl.elopenmike.com.core.ui.components.ProfileGenderSelector


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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import wallcrawl.elopenmike.com.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.components.*
import wallcrawl.elopenmike.com.core.ui.components.LanguageChip
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.descriptionRes
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.localization.longLabelRes
import wallcrawl.elopenmike.com.core.ui.localization.messageRes
import wallcrawl.elopenmike.com.core.ui.localization.shortLabelRes
import wallcrawl.elopenmike.com.core.ui.localization.subtitleRes
import wallcrawl.elopenmike.com.core.ui.localization.titleRes
import wallcrawl.elopenmike.com.core.ui.localization.trainingDaysDescriptionRes
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

    val canGoBack = !state.isFirstStep && !state.isSaving && !isRestoreInFlight
    BackHandler(enabled = !state.isFirstStep) { if (canGoBack) viewModel.previousStep() }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onCompleted()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
            StepHeader(
                stepNumber = state.currentStep.stepNumber,
                totalSteps = OnboardingStep.totalSteps,
                canGoBack = canGoBack,
                onBack = { viewModel.previousStep() },
                counterRes = R.string.onboarding_step_counter,
                // Reachable from the first screen, before anything has to be typed, so a
                // Spanish reader who landed on an English install can switch and carry on.
                trailing = { LanguageChip() }
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
                        contentAlignment = Alignment.TopStart
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
                                OnboardingStep.SCHEDULE -> ScheduleStep(state = state, viewModel = viewModel)
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
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Bottom Navigation Actions
            OnboardingBottomNav(
                state = state,
                isRestoreInFlight = isRestoreInFlight,
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
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.onboarding_codename_heading)) },
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
            Spacer(modifier = Modifier.height(16.dp))
            ProfileGenderSelector(
                gender = state.gender,
                onGenderChange = viewModel::updateGender,
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
                color = MaterialTheme.colorScheme.onSurface
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.onboarding_unit_hint),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WeightUnit.entries.forEach { unit ->
                    val isSelected = state.unit == unit
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .selectable(selected = isSelected, role = Role.RadioButton) { viewModel.updateUnit(unit) }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(unit.shortLabelRes),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) TextWhite else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(unit.longLabelRes),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) TextWhite.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                color = MaterialTheme.colorScheme.onSurface
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
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            modifier = Modifier.fillMaxWidth()
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
