package wallcrawl.elopenmike.com.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.IllustrationVariant
import wallcrawl.elopenmike.com.core.model.ProfileGender

val LocalIllustrationVariant = compositionLocalOf { IllustrationVariant.MALE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileGenderSelector(
    gender: ProfileGender,
    onGenderChange: (ProfileGender) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.profile_gender_title), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileGender.entries.forEach { option ->
                FilterChip(
                    selected = gender == option,
                    onClick = { onGenderChange(option) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary),
                    label = { Text(stringResource(option.labelRes), color = if (gender == option)
                        MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                )
            }
        }
        Text(
            stringResource(R.string.profile_gender_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val ProfileGender.labelRes: Int
    @StringRes get() = when (this) {
        ProfileGender.UNSPECIFIED -> R.string.gender_unspecified
        ProfileGender.WOMAN -> R.string.gender_woman
        ProfileGender.MAN -> R.string.gender_man
        ProfileGender.NON_BINARY -> R.string.gender_non_binary
    }
