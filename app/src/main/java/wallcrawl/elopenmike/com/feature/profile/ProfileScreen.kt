package wallcrawl.elopenmike.com.feature.profile

import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.ui.components.BandSetupControls

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.locale.AppLanguage
import wallcrawl.elopenmike.com.core.locale.AppLanguageController
import wallcrawl.elopenmike.com.core.model.BreakDurationHelper
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.ProfileGender
import wallcrawl.elopenmike.com.core.ui.components.ProfileGenderSelector
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.components.BreakDurationSelector
import wallcrawl.elopenmike.com.core.ui.components.MovementCapabilityQuestion
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlOutlinedButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.components.capabilityLevelLabel
import wallcrawl.elopenmike.com.core.ui.components.movementCapabilityLabel
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.descriptionRes
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.localization.messageRes
import wallcrawl.elopenmike.com.core.ui.localization.shortLabelRes
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.core.ui.theme.WebBlueAccent

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenCredits: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Export, restore, and delete-all controls, supplied by the navigation graph so this
     * screen stays unaware of document handling and of the backup repository.
     */
    localDataSection: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    Text(stringResource(state.messageRes), color = CrimsonRedLight)
                }
            }

            is ProfileUiState.Success -> {
                BackHandler(enabled = state.movementCapabilityDraft != null) {
                    viewModel.cancelMovementCapabilityEditing()
                }
                ProfileContent(
                    profile = state.profile,
                    movementCapabilityDraft = state.movementCapabilityDraft,
                    movementCapabilityError = state.movementCapabilityError,
                    isSavingCapabilities = state.isSaving,
                    availableEquipment = state.availableEquipmentOptions,
                    availableMuscles = state.availableMuscleOptions,
                    availableConstraints = state.availableConstraintOptions,
                    onToggleGoal = { viewModel.toggleGoal(it) },
                    onUpdateExperience = { viewModel.updateExperience(it) },
                    onUpdateDuration = { viewModel.updateDuration(it) },
                    onUpdateDaysPerWeek = { viewModel.updateDaysPerWeek(it) },
                    onUpdateUnit = { viewModel.updateUnit(it) },
                    onToggleEquipment = { viewModel.toggleEquipment(it) },
                    onSetMusclePriority = { muscle, priority -> viewModel.setMusclePriority(muscle, priority) },
                    onToggleConstraint = { viewModel.toggleTrainingConstraint(it) },
                    onUpdateReturningAfterBreakWeeks = { viewModel.updateReturningAfterBreakWeeks(it) },
                    onUpdateThemePreference = { viewModel.updateThemePreference(it) },
                    onUpdateGender = viewModel::updateGender,
                    onStartCapabilityEdit = viewModel::startMovementCapabilityEditing,
                    onUpdateCapability = viewModel::updateMovementCapabilityDraft,
                    onCancelCapabilityEdit = viewModel::cancelMovementCapabilityEditing,
                    onSaveCapabilities = viewModel::saveMovementCapabilities,
                    onOpenCredits = onOpenCredits,
                    localDataSection = localDataSection
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(
    profile: UserProfile,
    movementCapabilityDraft: MovementCapabilities?,
    movementCapabilityError: ProfileCapabilityError?,
    isSavingCapabilities: Boolean,
    availableEquipment: List<String>,
    availableMuscles: List<String>,
    availableConstraints: List<TrainingConstraint>,
    onToggleGoal: (FitnessGoal) -> Unit,
    onUpdateExperience: (ExperienceLevel) -> Unit,
    onUpdateDuration: (Int) -> Unit,
    onUpdateDaysPerWeek: (Int) -> Unit,
    onUpdateUnit: (WeightUnit) -> Unit,
    onToggleEquipment: (String) -> Unit,
    onSetMusclePriority: (String, PriorityLevel) -> Unit,
    onToggleConstraint: (TrainingConstraint) -> Unit,
    onUpdateReturningAfterBreakWeeks: (Int) -> Unit,
    onUpdateThemePreference: (ThemePreference) -> Unit,
    onUpdateGender: (ProfileGender) -> Unit,
    onStartCapabilityEdit: () -> Unit,
    onUpdateCapability: (
        MovementCapabilityType,
        CapabilityLevel
    ) -> Unit,
    onCancelCapabilityEdit: () -> Unit,
    onSaveCapabilities: () -> Unit,
    onOpenCredits: () -> Unit,
    localDataSection: @Composable () -> Unit
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
                text = stringResource(R.string.profile_eyebrow),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = CrimsonRedPrimary
            )
            Text(
                text = stringResource(R.string.profile_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 1. Fitness Goals Selector
        item {
            WallCrawlCard(
                cornerRadius = 16.dp,
                contentPadding = 16.dp
            ) {
                Text(
                    text = stringResource(R.string.profile_goals_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = CrimsonRedPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.profile_goals_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                FitnessGoal.entries.forEach { goal ->
                    val isSelected = goal in profile.goals
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                if (isSelected) CrimsonRedPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onToggleGoal(goal) }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(goal.labelRes),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(goal.descriptionRes),
                                    fontSize = 12.sp,
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
                    }
                }
            }
        }

        // 3. Units & Preferences
        item {
            WallCrawlCard(
                cornerRadius = 16.dp,
                contentPadding = 16.dp
            ) {
                val locale = LocalConfiguration.current.locales[0]
                Text(
                    text = stringResource(R.string.profile_preferences_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = CrimsonRedPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Preferred Unit Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.profile_preferred_unit),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeightUnit.entries.forEach { unit ->
                            val isSelected = profile.preferredUnit == unit
                            Box(
                                modifier = Modifier
                                    .background(if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .clickable { onUpdateUnit(unit) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(unit.shortLabelRes),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TextWhite else MaterialTheme.colorScheme.onSurface
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
                        Text(
                            text = stringResource(R.string.profile_preferred_duration),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(
                                R.string.profile_duration_value,
                                LocaleFormatting.formatCount(profile.preferredDurationMinutes, locale)
                            ),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = profile.preferredDurationMinutes.toFloat(),
                        onValueChange = { onUpdateDuration(it.toInt()) },
                        valueRange = 30f..90f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = CrimsonRedPrimary,
                            activeTrackColor = CrimsonRedPrimary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
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
                    Text(
                        text = stringResource(R.string.profile_training_days),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (2..6).forEach { days ->
                            val isSelected = profile.daysPerWeek == days
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .clickable { onUpdateDaysPerWeek(days) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = LocaleFormatting.formatCount(days, locale),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) TextWhite else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            MovementCapabilityProfileCard(
                persistedCapabilities = profile.movementCapabilities,
                draft = movementCapabilityDraft,
                error = movementCapabilityError,
                isSaving = isSavingCapabilities,
                onStartEditing = onStartCapabilityEdit,
                onUpdate = onUpdateCapability,
                onCancel = onCancelCapabilityEdit,
                onSave = onSaveCapabilities
            )
        }

        // 3. Available Equipment Selector
        item {
            WallCrawlCard(
                cornerRadius = 16.dp,
                contentPadding = 16.dp
            ) {
                val vocabulary = LocalExerciseVocabulary.current
                Text(
                    text = stringResource(R.string.profile_equipment_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = CrimsonRedPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableEquipment.filterNot { it in StandardEquipment.BAND_SETUPS }.forEach { equipment ->
                        val isSelected = equipment in profile.availableEquipment
                        FilterChip(
                            selected = isSelected,
                            // The canonical English name is what is stored and matched on;
                            // only the label is translated.
                            onClick = { onToggleEquipment(equipment) },
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

        item {
            WallCrawlCard(cornerRadius = 16.dp, contentPadding = 16.dp) {
                BandSetupControls(profile.availableEquipment, onToggleEquipment)
            }
        }

        // 4. Muscle Priorities Matrix
        item {
            WallCrawlCard(
                cornerRadius = 16.dp,
                contentPadding = 16.dp
            ) {
                val muscleVocabulary = LocalExerciseVocabulary.current
                Text(
                    text = stringResource(R.string.profile_muscle_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = CrimsonRedPrimary
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
                            // The map key stays the canonical English name; the row label
                            // is the reader's word for the same muscle.
                            text = muscleVocabulary.muscle(muscle),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PriorityLevel.entries.forEach { level ->
                                val isSelected = currentPriority == level
                                val bgColor = when {
                                    isSelected && level == PriorityLevel.HIGH -> CrimsonRedPrimary
                                    isSelected && level == PriorityLevel.NORMAL -> MaterialTheme.colorScheme.secondary
                                    isSelected && level == PriorityLevel.LOW -> MaterialTheme.colorScheme.outline
                                    else -> MaterialTheme.colorScheme.surface
                                }
                                val textColor = when {
                                    isSelected && (level == PriorityLevel.HIGH || level == PriorityLevel.NORMAL) -> TextWhite
                                    isSelected && level == PriorityLevel.LOW -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Box(
                                    modifier = Modifier
                                        .background(bgColor, RoundedCornerShape(6.dp))
                                        .border(
                                            1.dp,
                                            if (isSelected) bgColor else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onSetMusclePriority(muscle, level) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(level.labelRes),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Training constraints and return-after-break weeks
        item {
            WallCrawlCard(
                cornerRadius = 16.dp,
                contentPadding = 16.dp
            ) {
                Text(
                    text = stringResource(R.string.profile_safety_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = CrimsonRedPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Same honesty note the movement-capability card already carries: this input is
                // saved but inert until reviewed planning ships.
                Text(
                    text = stringResource(R.string.profile_safety_description),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableConstraints.forEach { constraint ->
                        val isSelected = constraint in profile.trainingConstraints
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleConstraint(constraint) },
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

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.profile_break_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (profile.returningAfterBreakWeeks > 0) {
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
                    text = stringResource(R.string.profile_break_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                BreakDurationSelector(
                    weeks = profile.returningAfterBreakWeeks,
                    onSelectWeeks = { onUpdateReturningAfterBreakWeeks(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        BreakDurationHelper.guidanceFor(profile.returningAfterBreakWeeks).messageRes
                    ),
                    fontSize = 12.sp,
                    color = if (profile.returningAfterBreakWeeks >= 52) CrimsonRedPrimary else MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        item {
            WallCrawlCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, contentPadding = 16.dp) {
                ProfileGenderSelector(
                    gender = profile.gender,
                    onGenderChange = onUpdateGender,
                )
            }
        }

        // 6. App preferences: theme and language, in one card under one heading.
        item {
            AppPreferencesCard(
                currentTheme = profile.themePreference,
                onSelectTheme = onUpdateThemePreference
            )
        }

        // 7. Local data: export, restore, and delete-all
        item {
            localDataSection()
        }

        // 8. Credits and Licenses
        item {
            WallCrawlCard(
                cornerRadius = 16.dp,
                contentPadding = 16.dp,
                modifier = Modifier
                    .clickable(onClick = onOpenCredits)
                    .semantics { role = Role.Button }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.profile_credits_heading),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = CrimsonRedPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.profile_credits_body),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MovementCapabilityProfileCard(
    persistedCapabilities: MovementCapabilities,
    draft: MovementCapabilities?,
    error: ProfileCapabilityError?,
    isSaving: Boolean,
    onStartEditing: () -> Unit,
    onUpdate: (
        MovementCapabilityType,
        CapabilityLevel
    ) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    WallCrawlCard(
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Text(
            text = stringResource(wallcrawl.elopenmike.com.R.string.profile_capability_title),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = CrimsonRedPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(
                wallcrawl.elopenmike.com.R.string.profile_capability_description
            ),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (draft == null) {
            MovementCapabilityType.entries.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = movementCapabilityLabel(type),
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = capabilityLevelLabel(persistedCapabilities[type]),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            WallCrawlOutlinedButton(
                text = stringResource(
                    wallcrawl.elopenmike.com.R.string.profile_capability_edit
                ),
                onClick = onStartEditing,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            MovementCapabilityType.entries.forEachIndexed { index, type ->
                if (index > 0) Spacer(modifier = Modifier.height(16.dp))
                MovementCapabilityQuestion(
                    type = type,
                    selectedLevel = draft[type],
                    onSelect = { level -> onUpdate(type, level) }
                )
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(error.messageRes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WallCrawlOutlinedButton(
                    text = stringResource(
                        wallcrawl.elopenmike.com.R.string.profile_capability_cancel
                    ),
                    onClick = onCancel,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                )
                WallCrawlPrimaryButton(
                    text = stringResource(
                        if (isSaving) {
                            wallcrawl.elopenmike.com.R.string.profile_capability_saving
                        } else {
                            wallcrawl.elopenmike.com.R.string.profile_capability_save
                        }
                    ),
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Interface theme and app language, the two settings that are about the device rather than
 * about training. Neither is part of the training profile or the export archive.
 */
@Composable
internal fun AppPreferencesCard(
    currentTheme: ThemePreference,
    onSelectTheme: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
    currentLanguage: AppLanguage = AppLanguageController.current(),
    onSelectLanguage: (AppLanguage) -> Unit = AppLanguageController::apply
) {
    // Read so the pill repaints after a language change, before the recreation completes.
    LocalConfiguration.current

    WallCrawlCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Text(
            text = stringResource(R.string.profile_app_preferences_heading),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = CrimsonRedPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))

        PreferenceRow(
            title = stringResource(R.string.profile_theme_title),
            description = stringResource(currentTheme.descriptionRes)
        ) {
            SegmentedPill(
                options = ThemePreference.entries,
                selectedOption = currentTheme,
                label = { stringResource(it.shortLabelRes) },
                accessibilityLabel = { stringResource(it.labelRes) },
                onSelect = onSelectTheme
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // The same preference the onboarding header's chip writes, through the one
        // AppLanguageController, rather than a second copy of it. Abbreviated to Auto/EN/ES
        // so it takes one row like the theme control above it.
        PreferenceRow(
            title = stringResource(R.string.language_setting_title),
            description = stringResource(R.string.language_description)
        ) {
            SegmentedPill(
                options = AppLanguage.entries,
                selectedOption = currentLanguage,
                label = { stringResource(it.shortLabelRes) },
                accessibilityLabel = {
                    stringResource(
                        R.string.language_option_accessibility,
                        stringResource(it.labelRes)
                    )
                },
                onSelect = onSelectLanguage
            )
        }
    }
}

/** A titled setting with its control on the right. */
@Composable
private fun PreferenceRow(
    title: String,
    description: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        control()
    }
}

/**
 * The compact segmented control both app preferences use.
 *
 * [label] is abbreviated to keep the pill on one row; [accessibilityLabel] is the full name,
 * because a screen reader announcing "ES" helps nobody.
 */
@Composable
private fun <T> SegmentedPill(
    options: List<T>,
    selectedOption: T,
    label: @Composable (T) -> String,
    accessibilityLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            val optionDescription = accessibilityLabel(option)
            Box(
                modifier = Modifier
                    // Abbreviated labels make a segment narrow enough to be hard to hit;
                    // the label shrinks, the target does not.
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) CrimsonRedPrimary else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics {
                        contentDescription = optionDescription
                        role = Role.RadioButton
                        selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) TextWhite else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
