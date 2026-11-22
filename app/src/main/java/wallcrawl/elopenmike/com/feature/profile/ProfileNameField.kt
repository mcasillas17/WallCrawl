package wallcrawl.elopenmike.com.feature.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.UserProfile

const val PROFILE_NAME_FIELD_TEST_TAG = "profile-name-field"

/**
 * Edits the name locally and saves it on Done or when focus leaves, so typing never
 * round-trips through the database. Leaving the screen mid-edit also saves. A blank entry
 * keeps the saved name.
 */
@Composable
fun ProfileNameField(name: String, onSave: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable(name) { mutableStateOf(name) }
    var focused by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // Saves once per distinct entry: losing focus and leaving the screen can both fire.
    var lastSaved by rememberSaveable(name) { mutableStateOf(name) }
    fun commit() {
        if (text.isBlank()) text = name
        else if (text.trim() != lastSaved.trim()) { onSave(text); lastSaved = text }
    }
    val commitOnLeave by rememberUpdatedState(::commit)
    DisposableEffect(Unit) { onDispose { commitOnLeave() } }
    OutlinedTextField(
        value = text,
        onValueChange = { text = UserProfile.nameInput(it) },
        label = { Text(stringResource(R.string.profile_name_title)) },
        supportingText = { Text(stringResource(R.string.personalize_name_supporting)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(PROFILE_NAME_FIELD_TEST_TAG)
            .onFocusChanged {
                if (focused && !it.isFocused) commit()
                focused = it.isFocused
            }
    )
}
