package wallcrawl.elopenmike.com.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import wallcrawl.elopenmike.com.core.model.ThemePreference

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

private val LightColorScheme = lightColorScheme(
    primary = CrimsonRedPrimary,
    onPrimary = TextWhite,
    primaryContainer = CrimsonRedLight.copy(alpha = 0.12f),
    onPrimaryContainer = CrimsonRedDark,
    secondary = LightSpiderBluePrimary,
    onSecondary = TextWhite,
    secondaryContainer = LightSurfaceCard,
    onSecondaryContainer = LightTextPrimary,
    tertiary = CrimsonRedPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightDivider
)

@Composable
fun WallCrawlTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        DisposableEffect(isDark) {
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
            onDispose {}
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WallCrawlTypography,
        content = content
    )
}

