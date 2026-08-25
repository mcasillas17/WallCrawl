package wallcrawl.elopenmike.com.core.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonRedPrimary,
    onPrimary = TextWhite,
    primaryContainer = CrimsonRedDark,
    onPrimaryContainer = TextWhite,
    secondary = WebBlueAccent,
    onSecondary = ObsidianBlack,
    secondaryContainer = GraphiteSurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = CrimsonRedLight,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = GraphiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = GraphiteSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = GraphiteBorder,
    outlineVariant = GraphiteDivider
)

@Composable
fun WallCrawlTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = ObsidianBlack.toArgb()
                window.navigationBarColor = ObsidianBlack.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = WallCrawlTypography,
        content = content
    )
}
