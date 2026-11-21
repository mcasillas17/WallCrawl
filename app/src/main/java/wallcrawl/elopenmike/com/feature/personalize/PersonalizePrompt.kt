package wallcrawl.elopenmike.com.feature.personalize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard

const val DISMISS_MILLIS = 7L * 24 * 60 * 60 * 1000

/** The Today card waits for a first finished workout and then for a dismissal to age out. */
fun shouldShowTodayCard(personalized: Boolean, completedWorkouts: Int, dismissedAtMillis: Long?, nowMillis: Long): Boolean =
    !personalized && completedWorkouts > 0 &&
        (dismissedAtMillis == null || nowMillis - dismissedAtMillis >= DISMISS_MILLIS)

/** One card for the summary (text "Not now" button) and Today (close icon when [dismissLabel] is null). */
@Composable
fun PersonalizeCard(
    title: String,
    body: String,
    onPersonalize: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String?,
    modifier: Modifier = Modifier
) {
    WallCrawlCard(modifier = modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (dismissLabel == null) IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, stringResource(R.string.personalize_dismiss_a11y))
            }
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPersonalize) { Text(stringResource(R.string.personalize_action)) }
            if (dismissLabel != null) TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    }
}
