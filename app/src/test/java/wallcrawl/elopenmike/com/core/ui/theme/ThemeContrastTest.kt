package wallcrawl.elopenmike.com.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 1.4.3 (4.5:1 text) for the palette's text colours on the surfaces they sit on. */
class ThemeContrastTest {
    private fun ratio(foreground: Color, background: Color): Double {
        val a = foreground.compositeOver(background).luminance().toDouble()
        val b = background.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun assertText(name: String, foreground: Color, backgrounds: List<Color>) {
        backgrounds.forEach {
            val measured = ratio(foreground, it)
            assertTrue("$name on $it is %.2f:1".format(measured), measured >= 4.5)
        }
    }

    @Test fun lightPalette() {
        val surfaces = listOf(LightBackground, LightSurface, LightSurfaceCard)
        assertText("primary text", LightTextPrimary, surfaces)
        assertText("secondary text", LightTextSecondary, surfaces)
        assertText("accent", AccentTextOnLight, surfaces)
        assertText("accent on a selected row", AccentTextOnLight, listOf(SpiderRedPrimary.copy(alpha = 0.12f).compositeOver(LightSurfaceCard)))
        assertText("blue", LightSpiderBluePrimary, surfaces)
        assertText("success", SuccessGreenDeep, surfaces)
    }

    @Test fun darkPalette() {
        val surfaces = listOf(ObsidianBlack, GraphiteSurface, GraphiteSurfaceElevated)
        assertText("primary text", TextPrimary, surfaces)
        assertText("secondary text", TextSecondary, surfaces)
        assertText("accent", AccentTextOnDark, surfaces)
        assertText("blue", WebBlueAccent, surfaces)
        assertText("success", SuccessGreen, surfaces)
    }

    @Test fun textOnFilledControls() {
        assertText("label on red", TextWhite, listOf(SpiderRedPrimary))
    }

    /** Material draws text and outlined button labels in primary, so each scheme's primary is text. */
    @Test fun schemePrimaryReadsAsText() {
        assertText("light primary", LightColorScheme.primary, listOf(LightBackground, LightSurface, LightSurfaceCard))
        assertText("dark primary", DarkColorScheme.primary, listOf(ObsidianBlack, GraphiteSurface, GraphiteSurfaceElevated))
        assertText("light label on primary", LightColorScheme.onPrimary, listOf(LightColorScheme.primary))
        assertText("dark label on primary", DarkColorScheme.onPrimary, listOf(DarkColorScheme.primary))
    }
}
