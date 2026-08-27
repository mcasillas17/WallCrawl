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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.exercise.workoutguide.AttributionNotice
import wallcrawl.elopenmike.com.core.exercise.workoutguide.CatalogAttribution
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton
import wallcrawl.elopenmike.com.core.ui.components.WebBackgroundPattern
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedLight
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurface
import wallcrawl.elopenmike.com.core.ui.theme.ObsidianBlack
import wallcrawl.elopenmike.com.core.ui.theme.TextMuted
import wallcrawl.elopenmike.com.core.ui.theme.TextPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextSecondary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite

/**
 * Shows who made the bundled exercise artwork and under what licence.
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

    Box(modifier.fillMaxSize().background(ObsidianBlack)) {
        WebBackgroundPattern()

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextWhite)
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
                        text = "Artwork & Licences",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = TextWhite
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
                        Text(current.message, color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        WallCrawlSecondaryButton(text = "Try Again", onClick = viewModel::load)
                    }
                }

                is CreditsUiState.Success -> {
                    CreditsContent(
                        catalog = current.catalog,
                        notices = current.notices,
                        onOpenUrl = { url -> uriHandler.openUri(url) }
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
                    color = TextWhite
                )
                Text(
                    text = "${catalog.exerciseCount} exercises · " +
                        "${catalog.frameCount} illustration frames, bundled unmodified",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                CreditRow(label = "Licence", value = catalog.attribution.license)
                CreditRow(label = "Source", value = catalog.repository)
                CreditRow(label = "Pinned commit", value = catalog.commit.take(12))

                Spacer(Modifier.height(14.dp))
                WallCrawlSecondaryButton(
                    text = "View licence",
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
            WallCrawlCard(backgroundColor = GraphiteSurface) {
                Text(
                    text = notice.title.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = notice.body,
                    fontSize = 13.sp,
                    color = TextMuted,
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
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }
}
