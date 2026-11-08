package wallcrawl.elopenmike.com.core.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Spider-Man Inspired Athletic Palette
// ==========================================

// 1. Signature Spider Scarlet Red (Primary Action & Branding)
val SpiderRedPrimary = Color(0xFFDE2045)
val SpiderRedVibrant = Color(0xFFFF6C87)
val SpiderRedDeep = Color(0xFF602334)
val SpiderRedGlow = Color(0x33DE2045)

// Aliases for compatibility
val CrimsonRedPrimary = SpiderRedPrimary
val CrimsonRedLight = SpiderRedVibrant
val CrimsonRedDark = SpiderRedDeep
val CrimsonRedGlow = SpiderRedGlow

// 2. Iconic Spider Royal Web Blue & Electric Cyan (Secondary & Accents)
val SpiderBluePrimary = Color(0xFF0066FF)
val SpiderBlueElectric = Color(0xFF38BDF8)
val SpiderBlueDark = Color(0xFF0D1B33)
val SpiderBlueGlow = Color(0x2E0066FF)

// Aliases for compatibility
val WebBlueAccent = SpiderBlueElectric
val WebBlueGlow = SpiderBlueGlow

// 3. Symbiote Obsidian & Dark Suit Graphite (Surfaces & Backgrounds)
val ObsidianBlack = Color(0xFF101113)
val DarkGraphiteBackground = Color(0xFF151618)
val GraphiteSurface = Color(0xFF17181B)
val GraphiteSurfaceElevated = Color(0xFF202125)
val GraphiteBorder = Color(0xFF47494F)
val GraphiteDivider = Color(0xFF313238)

// 4. Functional State Colors
val SuccessGreen = Color(0xFF10B981)

/**
 * Darker success green for filled controls and for success text on light surfaces:
 * white on [SuccessGreen] only reaches about 2.5:1, which is below WCAG AA.
 */
val SuccessGreenDeep = Color(0xFF07704F)
/** Red for text: [SpiderRedPrimary] reads under 4.5:1 on cards in both themes. */
val AccentTextOnDark = Color(0xFFFF7A82)
val AccentTextOnLight = Color(0xFFB91218)
val WarningAmber = Color(0xFFF59E0B)

// 5. Web Silver & Typography Colors
val TextWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF5F4F2)
val TextSecondary = Color(0xFFB7B6BB)

// ==========================================
// Light Theme Palette (Clean Athletic Daylight)
// ==========================================
val LightBackground = Color(0xFFF8F7F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFFFFFFF)
val LightSurfaceCard = Color(0xFFF5F4F2)
val LightBorder = Color(0xFF9B9A9F)
val LightDivider = Color(0xFFDEDCDF)

val LightTextPrimary = Color(0xFF1B1B1F)
val LightTextSecondary = Color(0xFF54545C)

val LightSpiderBluePrimary = Color(0xFF126494)
