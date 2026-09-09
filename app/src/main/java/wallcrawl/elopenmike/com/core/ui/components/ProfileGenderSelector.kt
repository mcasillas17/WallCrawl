package wallcrawl.elopenmike.com.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.IllustrationVariant
import wallcrawl.elopenmike.com.core.model.ProfileGender

val LocalIllustrationVariant = compositionLocalOf { IllustrationVariant.MALE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileGenderSelector(
    gender: ProfileGender,
    onGenderChange: (ProfileGender) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = stringResource(gender.labelRes),
            onValueChange = {},
            readOnly = true,
            label = {
                Text(
                    stringResource(R.string.profile_gender_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            supportingText = {
                Text(
                    stringResource(R.string.profile_gender_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ProfileGender.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(stringResource(option.labelRes), color = MaterialTheme.colorScheme.onSurface)
                    },
                    onClick = {
                        expanded = false
                        onGenderChange(option)
                    },
                    trailingIcon = {
                        if (gender == option) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.semantics { selected = gender == option }
                )
            }
        }
    }
}

private val ProfileGender.labelRes: Int
    @StringRes get() = when (this) {
        ProfileGender.UNSPECIFIED -> R.string.gender_unspecified
        ProfileGender.WOMAN -> R.string.gender_woman
        ProfileGender.MAN -> R.string.gender_man
        ProfileGender.NON_BINARY -> R.string.gender_non_binary
    }
