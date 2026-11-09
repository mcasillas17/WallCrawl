package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary

/** A screen title or section label a screen-reader user can jump to. */
fun Modifier.a11yHeading(): Modifier = semantics { heading() }

/**
 * A FilterChip's leading check while it is selected. Material's default selected fill is close to the
 * page colour, so without it only the unselected chips' outline would show which one is chosen.
 */
fun selectedCheck(selected: Boolean): (@Composable () -> Unit)? =
    if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null

/** Text that appears on its own, such as an error, read out without moving the reader's focus. */
fun Modifier.a11yAnnounced(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }

/**
 * True when text is large for the width it has, so rows should stack or wrap: from stock scale 1.3
 * on a 412 dp phone, 1.15 at 360 dp, and about 1.01 at 320 dp, since 412 / 320 is 1.2875.
 */
@Composable
fun isLargeText(): Boolean =
    LocalDensity.current.fontScale * COMMON_PHONE_WIDTH_DP / LocalConfiguration.current.screenWidthDp >= 1.3f

private const val COMMON_PHONE_WIDTH_DP = 412f

/** A plain row until text is large; then each item takes its own line, weights filling that line, and [verticalAlignment] has no effect. */
@Composable
fun StackingRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit
) {
    if (isLargeText()) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 1
        ) { content() }
    } else {
        Row(modifier, horizontalArrangement, verticalAlignment, content)
    }
}

/** A search field's placeholder names it until something is typed, and then it is gone, so this names it instead. */
fun Modifier.a11ySearchField(name: String, query: String): Modifier = semantics {
    if (query.isNotEmpty()) {
        contentDescription = name
        stateDescription = query
    }
}

/** States a disclosure's open or closed state, for controls with no built-in one. */
@Composable
fun Modifier.a11yExpanded(expanded: Boolean): Modifier {
    val state = stringResource(if (expanded) R.string.a11y_expanded else R.string.a11y_collapsed)
    return semantics { stateDescription = state }
}

/** A spinner a screen reader can name; the unnamed Material one is silent. */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.a11y_loading)
    CircularProgressIndicator(
        color = CrimsonRedPrimary,
        modifier = modifier.semantics {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        }
    )
}

/**
 * False when the user turned animations off (Remove animations, or animator scale 0). It reads the
 * composition's [MotionDurationScale], the scale Compose's own animations follow. That scale is snapshot
 * state, so a change, including one made while the app was away, recomposes whatever read it.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val scale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    return (scale?.scaleFactor ?: 1f) > 0f
}
