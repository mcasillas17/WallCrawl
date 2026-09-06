package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.locale.AppLanguage
import wallcrawl.elopenmike.com.core.locale.AppLanguageController
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary

/**
 * The app-language control itself, without a card or a section heading around it.
 *
 * There is one of these on purpose. Every place that offers the choice reads and writes the
 * same preference through [AppLanguageController], so a change made in one is already
 * reflected in the others and none of them holds a copy that could drift. Choosing a
 * language recreates the activity, and that is why every screen keeps its in-progress input
 * in a ViewModel or a SavedStateHandle.
 *
 * It is not a wizard step and never blocks anything: someone can switch to Spanish before
 * typing a single answer, and the wizard still has the same eight steps either way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageOptions(
    modifier: Modifier = Modifier,
    current: AppLanguage = AppLanguageController.current(),
    onSelect: (AppLanguage) -> Unit = AppLanguageController::apply
) {
    // Read so the chips recompose after a locale change, before the recreation completes.
    LocalConfiguration.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.language_description),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLanguage.entries.forEach { language ->
                val label = stringResource(language.labelRes)
                val optionDescription = stringResource(
                    R.string.language_option_accessibility,
                    label
                )
                val isSelected = language == current
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(language) },
                    label = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(vertical = 2.dp)
                        .semantics {
                            contentDescription = optionDescription
                            role = Role.RadioButton
                        },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = CrimsonRedPrimary,
                        selectedLabelColor = Color.White
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

/**
 * The language control in a card of its own, for the onboarding Welcome step.
 *
 * Profile shows the same control inside its App preferences card instead, so the choice
 * lives under the heading the documentation sends people to rather than beside it.
 */
@Composable
fun LanguageSelector(
    modifier: Modifier = Modifier,
    current: AppLanguage = AppLanguageController.current(),
    onSelect: (AppLanguage) -> Unit = AppLanguageController::apply
) {
    WallCrawlCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        contentPadding = 16.dp
    ) {
        Text(
            text = stringResource(R.string.language_title),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = CrimsonRedPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        LanguageOptions(current = current, onSelect = onSelect)
    }
}
