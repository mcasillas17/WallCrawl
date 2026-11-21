package wallcrawl.elopenmike.com.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.ui.components.LanguageChip
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.localization.messageRes
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary

private val DAY_OPTIONS = (2..6).toList()
private val MINUTE_OPTIONS = listOf(20, 30, 45, 60)

private val FitnessGoal.icon: ImageVector
    get() = when (this) {
        FitnessGoal.BUILD_MUSCLE -> Icons.Default.FitnessCenter
        FitnessGoal.STRENGTH -> Icons.Default.Bolt
        FitnessGoal.GENERAL_FITNESS -> Icons.Default.SelfImprovement
        FitnessGoal.FAT_LOSS -> Icons.Default.LocalFireDepartment
        FitnessGoal.ATHLETIC_PERFORMANCE -> Icons.Default.AutoAwesome
    }

/**
 * One-screen basic setup: goal, days per week and session length. Only shown during
 * first-time setup until [OnboardingViewModel.save] persists `onboardingCompleted = true`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Restore entry point offered under the primary action, supplied by the navigation graph.
     *
     * It sits here so a fresh install, or an install that has just deleted everything, can
     * restore an exported file without first being made to build a profile it would discard.
     * Opening it neither saves a profile nor changes the draft.
     */
    restoreFromArchive: @Composable () -> Unit = {},
    /**
     * True while a restore is reading the chosen document.
     *
     * Saving writes a whole profile, which would overwrite the one the restore is about to
     * commit, so the action is blocked meanwhile.
     */
    isRestoreInFlight: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val locale = LocalConfiguration.current.locales[0]

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onCompleted()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The action sits at the bottom of this column, so it has to end above the
            // navigation bar and above the keyboard rather than behind either.
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            // Reachable before anything is answered, so a Spanish reader who landed on an
            // English install can switch and carry on.
            LanguageChip()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_basic_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            SectionLabel(stringResource(R.string.onboarding_basic_goal))
            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2
                ) {
                    FitnessGoal.entries.forEach { goal ->
                        GoalTile(
                            goal = goal,
                            isSelected = state.goal == goal,
                            onClick = { viewModel.updateGoal(goal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.onboarding_basic_days))
            ChipRow(
                options = DAY_OPTIONS,
                selected = state.daysPerWeek,
                label = { LocaleFormatting.formatCount(it, locale) },
                onSelect = viewModel::updateDaysPerWeek
            )

            SectionLabel(stringResource(R.string.onboarding_basic_minutes))
            ChipRow(
                options = MINUTE_OPTIONS,
                selected = state.durationMinutes,
                label = { LocaleFormatting.formatCount(it, locale) },
                onSelect = viewModel::updateDurationMinutes
            )
        }

        val error = state.error
        if (error != null) {
            Text(
                text = stringResource(error.messageRes),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        WallCrawlPrimaryButton(
            text = stringResource(
                when {
                    state.isSaving -> R.string.onboarding_action_saving
                    isRestoreInFlight -> R.string.local_data_restore_progress
                    else -> R.string.onboarding_basic_cta
                }
            ),
            onClick = { viewModel.save(deviceRegion()) },
            enabled = state.canSave && !isRestoreInFlight,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        restoreFromArchive()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GoalTile(
    goal: FitnessGoal,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .heightIn(min = 88.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) CrimsonRedPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(goal.icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(goal.labelRes),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ChipRow(
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (isSelected) CrimsonRedPrimary else MaterialTheme.colorScheme.outline)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label(option),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * The device's region. The system resources ignore per-app locales, which the language chip
 * sets as a bare "en"/"es" with no country, so this still answers "US" after a switch.
 */
internal fun deviceRegion(): String =
    android.content.res.Resources.getSystem().configuration.locales[0].country
