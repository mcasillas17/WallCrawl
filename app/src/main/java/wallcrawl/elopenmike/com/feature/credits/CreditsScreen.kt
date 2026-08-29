package wallcrawl.elopenmike.com.feature.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.exercise.workoutguide.AttributionNotice
import wallcrawl.elopenmike.com.core.exercise.workoutguide.CatalogAttribution
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary

/**
 * Shows who made the bundled exercise artwork and under what license.
 * CC BY-SA 4.0 requires the credit to reach the person using the app, so this screen is
 * the compliance surface for every illustration rendered elsewhere in WallCrawl.
 */
@Composable
fun CreditsScreen(
    viewModel: CreditsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WebBackgroundPattern()

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column {
                    Text(
                        text = "CREDITS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = CrimsonRedPrimary
                    )
                    Text(
                        text = "Artwork & Licenses",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            when (val current = state) {
                is CreditsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CrimsonRedPrimary)
                    }
                }

                is CreditsUiState.Error -> {
                    WallCrawlCard(borderColor = CrimsonRedPrimary) {
                        Text(
                            text = "Attribution unavailable",
                            fontWeight = FontWeight.Bold,
                            color = CrimsonRedLight,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(current.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        WallCrawlSecondaryButton(text = "Try Again", onClick = viewModel::load)
                    }
                }

                is CreditsUiState.Success -> {
                    linkError?.let { message ->
                        Text(
                            text = message,
                            fontSize = 13.sp,
                            color = CrimsonRedLight,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    CreditsContent(
                        catalog = current.catalog,
                        notices = current.notices,
                        // A device with no browser (work profile, kiosk build) throws here.
                        // Crashing on the license screen would be the worst place to crash.
                        onOpenUrl = { url ->
                            linkError = runCatching { uriHandler.openUri(url) }
                                .exceptionOrNull()
                                ?.let { "No app available to open $url" }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditsContent(
    catalog: CatalogAttribution,
    notices: List<AttributionNotice>,
    onOpenUrl: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            WallCrawlCard {
                Text(
                    text = "EXERCISE ILLUSTRATIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = CrimsonRedLight
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = catalog.attribution.creator,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Scoped to the WallCrawl -> workout-guide boundary on purpose: some frames
                // upstream are themselves traced adaptations of Everkinetic originals, which
                // the Attribution notice below spells out.
                Text(
                    text = "${catalog.exerciseCount} exercises · ${catalog.frameCount} " +
                        "illustration frames, imported unmodified",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                CreditRow(label = "License", value = catalog.attribution.license)
                CreditRow(label = "Source", value = catalog.repository)
                CreditRow(label = "Pinned commit", value = catalog.commit.take(12))

                Spacer(Modifier.height(14.dp))
                WallCrawlSecondaryButton(
                    text = "View license",
                    onClick = { onOpenUrl(catalog.attribution.licenseUrl) }
                )
                Spacer(Modifier.height(8.dp))
                WallCrawlSecondaryButton(
                    text = "Visit creator",
                    onClick = { onOpenUrl(catalog.attribution.creatorUrl) }
                )
            }
        }

        items(notices) { notice ->
            WallCrawlCard {
                Text(
                    text = notice.title.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = rememberNoticeText(notice.body, onOpenUrl),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CreditRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            // Source URLs are longer than the label leaves room for on a 360dp screen.
            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp)
        )
    }
}

/**
 * Renders the bundled Markdown notices as readable text.
 *
 * The notices credit Everkinetic, the original CC BY-SA licensor, as a Markdown link.
 * Shown raw it reads as `[Everkinetic](https://…)` — the one credit that most needs to be
 * legible would be the least legible on the screen that exists to carry it.
 */
@Composable
private fun rememberNoticeText(
    body: String,
    onOpenUrl: (String) -> Unit
): AnnotatedString {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = CrimsonRedLight, textDecoration = TextDecoration.Underline)
    )
    // Routed through the same handler as the buttons so a device with no browser reports
    // it here too, rather than relying on the framework's default swallowing the failure.
    val linkListener = LinkInteractionListener { link ->
        (link as? LinkAnnotation.Url)?.let { onOpenUrl(it.url) }
    }
    return remember(body, onOpenUrl) {
        buildAnnotatedString {
            var cursor = 0
            // The source files are hand-wrapped at ~75 columns. Kept as-is every line wraps
            // again on a phone, so paragraphs are rejoined and blank lines kept as breaks.
            val text = body
                .split(PARAGRAPH_BREAK)
                .joinToString("\n\n") { paragraph ->
                    paragraph.lineSequence()
                        .map { line -> line.trimStart('#').trim() }
                        .filter(String::isNotEmpty)
                        .joinToString(" ")
                }
            while (cursor < text.length) {
                val match = MARKDOWN_LINK.find(text, cursor)
                if (match == null) {
                    append(text.substring(cursor))
                    break
                }
                append(text.substring(cursor, match.range.first))
                val label = match.groupValues[1]
                val url = match.groupValues[2]
                if (url.startsWith("https://")) {
                    withLink(LinkAnnotation.Url(url, linkStyles, linkListener)) { append(label) }
                } else {
                    append(label)
                }
                cursor = match.range.last + 1
            }
        }
    }
}

private val MARKDOWN_LINK = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")
private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
