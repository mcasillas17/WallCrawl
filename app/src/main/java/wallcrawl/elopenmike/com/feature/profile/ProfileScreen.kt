package wallcrawl.elopenmike.com.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.locale.AppLanguage
import wallcrawl.elopenmike.com.core.locale.AppLanguageController
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.ConditionUiState
import wallcrawl.elopenmike.com.core.ui.components.*
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.*
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite

private enum class SettingsGroup(val title: Int) {
    TRAINING(R.string.guided_training_preferences),
    EQUIPMENT(R.string.guided_equipment),
    MOVEMENT(R.string.guided_movement_restrictions),
    APP(R.string.guided_app_preferences),
    DATA(R.string.guided_your_data),
    CREDITS(R.string.guided_about_credits)
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenCredits: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    localDataSection: @Composable () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val conditions by viewModel.conditions.collectAsState()
    var group by rememberSaveable { mutableStateOf<SettingsGroup?>(null) }
    val profileState = state as? ProfileUiState.Success
    val saving = conditions.saving || profileState?.isSaving == true
    val back = {
        if (!saving) {
            when {
                conditions.draft != null -> viewModel.cancelConditionEditing()
                profileState?.movementCapabilityDraft != null -> viewModel.cancelMovementCapabilityEditing()
                group != null -> group = null
                else -> onBack()
            }
        }
    }
    BackHandler(enabled = group != null || saving, onBack = back)
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = dropUnlessResumed { back() }, enabled = !saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            Text(stringResource(group?.title ?: R.string.guided_profile_settings),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.a11yHeading())
        }
        val overviewScroll = rememberLazyListState()
        when (val current = state) {
            ProfileUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            is ProfileUiState.Error -> Text(stringResource(current.messageRes), Modifier.padding(16.dp).a11yAnnounced(),
                color = MaterialTheme.colorScheme.error)
            is ProfileUiState.Success -> {
                val selected = group
                if (selected == null) {
                    val unknownReports = current.profile.reportedConditions.any { it !in ReportedCondition.ids }
                    LazyColumn(state = overviewScroll, contentPadding = PaddingValues(vertical = 4.dp)) {
                        if (current.profile.unresolvedTrainingConstraintsRaw != null || unknownReports ||
                            !conditions.pending.isNullOrEmpty() || conditions.pendingFailed) item {
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                if (current.profile.unresolvedTrainingConstraintsRaw != null) Text(stringResource(R.string.guided_train_unreadable_restrictions))
                                if (unknownReports) Text(stringResource(R.string.guided_review_legacy))
                                if (!conditions.pending.isNullOrEmpty()) Text(stringResource(R.string.condition_pending_title))
                                if (conditions.pendingFailed) Text(stringResource(R.string.condition_pending_read_error), Modifier.a11yAnnounced())
                                TextButton(onClick = { group = SettingsGroup.MOVEMENT }) {
                                    Text(stringResource(R.string.guided_review_restrictions))
                                }
                            }
                        }
                        items(SettingsGroup.entries) { entry ->
                            ListItem(
                                modifier = Modifier.clickable(role = Role.Button, onClick = dropUnlessResumed {
                                    if (entry == SettingsGroup.CREDITS) onOpenCredits() else group = entry
                                }),
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                                headlineContent = { Text(stringResource(entry.title)) },
                                supportingContent = { Text(groupSummary(entry, current.profile)) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                            )
                            HorizontalDivider()
                        }
                    }
                } else key(selected) {
                    SettingsDetail(selected, current, conditions, viewModel, localDataSection)
                }
            }
        }
    }
}

@Composable
private fun groupSummary(group: SettingsGroup, profile: UserProfile): String {
    val locale = LocalConfiguration.current.locales[0]
    val separator = stringResource(R.string.detail_separator)
    fun count(value: Int) = LocaleFormatting.formatCount(value, locale)
    return when (group) {
        SettingsGroup.TRAINING -> listOf(
            pluralStringResource(R.plurals.count_days_per_week, profile.daysPerWeek, count(profile.daysPerWeek)),
            stringResource(R.string.profile_duration_value, count(profile.preferredDurationMinutes))
        ).joinToString(separator)
        SettingsGroup.EQUIPMENT -> if (profile.availableEquipment.size in 1..2) {
            LocalExerciseVocabulary.current.equipment(profile.availableEquipment).joinToString(separator)
        } else pluralStringResource(R.plurals.count_equipment_available, profile.availableEquipment.size, count(profile.availableEquipment.size))
        SettingsGroup.MOVEMENT -> {
            val limits = profile.movementCapabilities.values.values.count { it == CapabilityLevel.AVOID || it == CapabilityLevel.LIMITED } +
                profile.trainingConstraints.size
            val exclusions = profile.excludedExerciseIds.distinct().size
            when {
                profile.unresolvedTrainingConstraintsRaw != null -> stringResource(R.string.guided_saved_restrictions)
                limits == 0 && exclusions == 0 && !profile.hasConditionReports -> stringResource(R.string.guided_no_marked_limits)
                else -> {
                    val reports = profile.reportedConditions + profile.activeConditions.map { it.conditionId }
                    stringResource(R.string.guided_restrictions_summary, count(reports.size), count(limits), count(exclusions))
                }
            }
        }
        SettingsGroup.APP -> listOf(
            stringResource(profile.preferredUnit.shortLabelRes), stringResource(profile.themePreference.shortLabelRes),
            stringResource(AppLanguageController.current().labelRes)
        ).joinToString(separator)
        SettingsGroup.DATA -> stringResource(R.string.guided_data_summary)
        SettingsGroup.CREDITS -> stringResource(R.string.guided_credits_summary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDetail(
    group: SettingsGroup,
    state: ProfileUiState.Success,
    conditions: ConditionUiState,
    viewModel: ProfileViewModel,
    localDataSection: @Composable () -> Unit
) {
    val profile = state.profile
    val vocabulary = LocalExerciseVocabulary.current
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (group) {
            SettingsGroup.TRAINING -> {
                item {
                    SettingsHeading(R.string.guided_goals)
                    // Says why the last goal cannot be cleared before a reader meets it dimmed.
                    Text(stringResource(R.string.onboarding_goals_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FitnessGoal.entries.forEach { goal ->
                        // At least one goal stays: the last one is announced as unavailable and dimmed.
                        val canToggle = !(goal in profile.goals && profile.goals.size == 1)
                        // The text dims; the checkbox already shows Material's disabled look, so it is not dimmed twice.
                        val textAlpha = Modifier.alpha(if (canToggle) 1f else 0.6f)
                        ListItem(
                            headlineContent = { Text(stringResource(goal.labelRes), textAlpha) },
                            supportingContent = { Text(stringResource(goal.descriptionRes), textAlpha) },
                            trailingContent = { Checkbox(checked = goal in profile.goals, onCheckedChange = null, enabled = canToggle) },
                            modifier = Modifier
                                .toggleable(value = goal in profile.goals, enabled = canToggle, role = Role.Checkbox) { viewModel.toggleGoal(goal) }
                        )
                    }
                }
                item {
                    SettingsHeading(R.string.onboarding_summary_label_experience)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExperienceLevel.entries.forEach { level ->
                            FilterChip(selected = profile.experienceLevel == level, onClick = { viewModel.updateExperience(level) },
                                leadingIcon = selectedCheck(profile.experienceLevel == level), label = { Text(stringResource(level.labelRes)) }, modifier = Modifier.radioChip())
                        }
                    }
                }
                item {
                    SettingsHeading(R.string.profile_training_days)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (2..6).forEach { days ->
                            // A bare digit says nothing; the plural says what it counts.
                            val daysLabel = pluralStringResource(R.plurals.a11y_days_per_week, days, LocaleFormatting.formatCount(days, locale))
                            FilterChip(selected = days == profile.daysPerWeek, onClick = { viewModel.updateDaysPerWeek(days) },
                                leadingIcon = selectedCheck(days == profile.daysPerWeek), label = { Text(LocaleFormatting.formatCount(days, locale)) }, modifier = Modifier.radioChip(daysLabel))
                        }
                    }
                }
                item {
                    SettingsHeading(R.string.profile_preferred_duration)
                    Text(stringResource(R.string.profile_duration_value, LocaleFormatting.formatCount(profile.preferredDurationMinutes, locale)))
                    val durationLabel = stringResource(R.string.profile_preferred_duration)
                    val durationValue = stringResource(R.string.summary_duration_value, LocaleFormatting.formatCount(profile.preferredDurationMinutes, locale))
                    // The slider speaks minutes, not a bare fraction.
                    Slider(value = profile.preferredDurationMinutes.toFloat(), onValueChange = { viewModel.updateDuration(it.toInt()) },
                        valueRange = 30f..90f, steps = 5, modifier = Modifier.semantics {
                            contentDescription = durationLabel
                            stateDescription = durationValue
                        })
                }
                item { SettingsHeading(R.string.guided_muscle_priorities) }
                items(state.availableMuscleOptions) { muscle ->
                    Text(vocabulary.muscle(muscle), style = MaterialTheme.typography.titleSmall)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PriorityLevel.entries.forEach { level ->
                            // Each option names its muscle, so one heard out of context still makes sense.
                            val levelLabel = stringResource(R.string.a11y_muscle_priority, vocabulary.muscle(muscle), stringResource(level.labelRes))
                            val chosen = level == (profile.musclePriorities[muscle] ?: PriorityLevel.NORMAL)
                            FilterChip(selected = chosen, leadingIcon = selectedCheck(chosen),
                                onClick = { viewModel.setMusclePriority(muscle, level) }, label = { Text(stringResource(level.labelRes)) },
                                modifier = Modifier.radioChip(levelLabel))
                        }
                    }
                }
            }
            SettingsGroup.EQUIPMENT -> {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.availableEquipmentOptions.filterNot { it in StandardEquipment.BAND_SETUPS }.forEach { equipment ->
                            FilterChip(selected = equipment in profile.availableEquipment, onClick = { viewModel.toggleEquipment(equipment) },
                                leadingIcon = selectedCheck(equipment in profile.availableEquipment), label = { Text(vocabulary.equipment(equipment)) })
                        }
                    }
                }
                item { BandSetupControls(profile.availableEquipment, viewModel::toggleEquipment) }
            }
            SettingsGroup.MOVEMENT -> {
                item { ProfileConditions(profile, conditions, viewModel) }
                if (profile.excludedExerciseIds.isNotEmpty()) {
                    item {
                        SettingsHeading(R.string.profile_excluded_heading)
                        Text(stringResource(R.string.profile_excluded_hint))
                        if (state.exclusionSaveFailed) Text(stringResource(R.string.profile_exclusion_save_error), Modifier.a11yAnnounced(), color = MaterialTheme.colorScheme.error)
                    }
                    items(profile.excludedExerciseIds.distinct()) { id ->
                        val name = vocabulary.exerciseName(id)
                        val remove = stringResource(R.string.profile_excluded_remove_description, name)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f))
                            TextButton(onClick = { viewModel.removeExcludedExercise(id) },
                                modifier = Modifier.semantics { contentDescription = remove }) {
                                Text(stringResource(R.string.profile_excluded_remove))
                            }
                        }
                    }
                }
                item {
                    MovementCapabilityProfileCard(profile.movementCapabilities, state.movementCapabilityDraft,
                        state.movementCapabilityError, state.isSaving, viewModel::startMovementCapabilityEditing,
                        viewModel::updateMovementCapabilityDraft, viewModel::cancelMovementCapabilityEditing,
                        viewModel::saveMovementCapabilities)
                }
                item {
                    SettingsHeading(R.string.guided_sensitivities)
                    Text(stringResource(R.string.profile_safety_description))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.availableConstraintOptions.forEach { constraint ->
                            FilterChip(selected = constraint in profile.trainingConstraints, leadingIcon = selectedCheck(constraint in profile.trainingConstraints),
                                enabled = profile.unresolvedTrainingConstraintsRaw == null, onClick = { viewModel.toggleTrainingConstraint(constraint) }, label = { Text(stringResource(constraint.labelRes)) })
                        }
                    }
                }
                item {
                    SettingsHeading(R.string.profile_break_title)
                    if (profile.returningAfterBreakWeeks > 0) Text(stringResource(R.string.break_reentry_active))
                    Text(stringResource(R.string.profile_break_hint))
                    BreakDurationSelector(profile.returningAfterBreakWeeks, viewModel::updateReturningAfterBreakWeeks)
                    Text(stringResource(BreakDurationHelper.guidanceFor(profile.returningAfterBreakWeeks).messageRes))
                }
            }
            SettingsGroup.APP -> {
                item {
                    SettingsHeading(R.string.profile_preferred_unit)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WeightUnit.entries.forEach { unit ->
                            FilterChip(selected = unit == profile.preferredUnit, onClick = { viewModel.updateUnit(unit) },
                                leadingIcon = selectedCheck(unit == profile.preferredUnit), label = { Text(stringResource(unit.shortLabelRes)) }, modifier = Modifier.radioChip())
                        }
                    }
                }
                item { ProfileGenderSelector(profile.gender, viewModel::updateGender) }
                item { AppPreferencesCard(profile.themePreference, viewModel::updateThemePreference) }
            }
            SettingsGroup.DATA -> item { localDataSection() }
            SettingsGroup.CREDITS -> Unit // Credits already has a graph destination.
        }
    }
}

@Composable
private fun SettingsHeading(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.a11yHeading())
}

@Composable
private fun ProfileConditions(profile: UserProfile, conditions: ConditionUiState, viewModel: ProfileViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        profile.unresolvedTrainingConstraintsRaw?.let { raw ->
            Text(stringResource(R.string.guided_train_unreadable_restrictions), Modifier.a11yAnnounced(), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.guided_original_restriction, raw))
        }
        if (conditions.loadFailed) Text(stringResource(R.string.condition_load_error), Modifier.a11yAnnounced(), color = MaterialTheme.colorScheme.error)
        ReportedConditionPicker(profile.reportedConditions, !conditions.saving, viewModel::setReportedCondition)
        if (conditions.reportedSaveFailed) {
            Text(stringResource(R.string.reported_conditions_save_error), Modifier.a11yAnnounced(), color = MaterialTheme.colorScheme.error)
        }
        val selected = conditions.draft ?: profile.activeConditions
        if (conditions.records.isNotEmpty() || selected.isNotEmpty() || conditions.draft != null) {
            ConditionSelectionControls(conditions.records, selected,
                if (conditions.draft != null) viewModel::updateConditionDraft else null, !conditions.saving)
            if (conditions.draft == null) TextButton(onClick = viewModel::startConditionEditing) {
                Text(stringResource(R.string.condition_edit))
            } else {
                TextButton(enabled = !conditions.saving, onClick = viewModel::saveConditions) { Text(stringResource(R.string.condition_save)) }
                TextButton(enabled = !conditions.saving, onClick = viewModel::cancelConditionEditing) { Text(stringResource(R.string.condition_cancel)) }
            }
        }
        if (conditions.saveFailed) Text(stringResource(R.string.condition_save_error), Modifier.a11yAnnounced(), color = MaterialTheme.colorScheme.error)
        PendingConditionControls(conditions, profile.activeConditions, viewModel::updateReassessmentAnswers, viewModel::reassess)
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
            modifier = Modifier.a11yHeading(),
            text = stringResource(wallcrawl.elopenmike.com.R.string.profile_capability_title),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurface
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
                // At large text the level goes below the movement, or the movement name breaks mid-word.
                StackingRow(
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
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.a11yAnnounced()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            StackingRow(
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

/** Theme is a profile setting. Language is device-local. */
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
            modifier = Modifier.a11yHeading(),
            text = stringResource(R.string.profile_app_preferences_heading),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurface
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

/** The full-width control stays reachable with large translated labels. */
@Composable
private fun PreferenceRow(
    title: String,
    description: String,
    control: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** A chip in a single-choice group: a radio with a 48 dp target, optionally with a spoken label. */
private fun Modifier.radioChip(description: String? = null) = heightIn(min = 48.dp).semantics {
    role = Role.RadioButton
    if (description != null) contentDescription = description
}
