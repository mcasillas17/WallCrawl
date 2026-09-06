package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.localization.shortLabelRes
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary

/**
 * A compact language picker for the onboarding wizard's header.
 *
 * Onboarding is where someone on the wrong language most needs the control and least wants
 * a panel of settings, so this is one chip reading `EN` or `ES` rather than a card. It
 * shows the language actually being read: under [AppLanguage.SYSTEM] that is whatever the
 * device resolved to, which is the honest answer to "what am I looking at". Opening it
 * still offers all three options, so System default stays reachable.
 *
 * Profile offers the same preference as a compact segmented control, where the row has
 * space for a label beside it. Both write through the one [AppLanguageController].
 */
@Composable
fun LanguageChip(
    modifier: Modifier = Modifier,
    current: AppLanguage = AppLanguageController.current(),
    onSelect: (AppLanguage) -> Unit = AppLanguageController::apply
) {
    val locale = LocalConfiguration.current.locales[0]
    // Whichever shipped language the resources actually resolved to, read through the one
    // place that maps a tag to a language. Anything unshipped reads as English, which is
    // what the resources themselves fall back to.
    val effective = AppLanguage.fromLanguageTag(locale.language)
        .takeIf { it != AppLanguage.SYSTEM }
        ?: AppLanguage.ENGLISH
    val description = stringResource(
        R.string.language_option_accessibility,
        stringResource(effective.labelRes)
    )
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // The visible circle is small enough to sit beside the header's back button; the
        // thing you tap is not, which is why the target is its own box around it.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { expanded = true }
                .semantics {
                    contentDescription = description
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CrimsonRedPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(effective.shortLabelRes),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Color.White
                )
            }
        }

        // The menu paints its own surface and text colour: the default container is tinted
        // from the primary red, which leaves the label barely legible on it.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            AppLanguage.entries.forEach { language ->
                val label = stringResource(language.labelRes)
                val isSelected = language == current
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(language)
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = CrimsonRedPrimary
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics {
                        contentDescription = label
                        role = Role.RadioButton
                        selected = isSelected
                    }
                )
            }
        }
    }
}
