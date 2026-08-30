package wallcrawl.elopenmike.com.core.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Spider-Man Inspired Athletic Palette
// ==========================================

// 1. Signature Spider Scarlet Red (Primary Action & Branding)
val SpiderRedPrimary = Color(0xFFE81A21)
val SpiderRedVibrant = Color(0xFFFF2A3A)
val SpiderRedDeep = Color(0xFF9E0012)
val SpiderRedGlow = Color(0x33E81A21)

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
val ObsidianBlack = Color(0xFF08090C)
val DarkGraphiteBackground = Color(0xFF0D1017)
val GraphiteSurface = Color(0xFF131722)
val GraphiteSurfaceElevated = Color(0xFF1A2030)
val GraphiteBorder = Color(0xFF273248)
val GraphiteDivider = Color(0xFF1D2536)

// 4. Functional State Colors
val SuccessGreen = Color(0xFF10B981)

/**
 * Darker success green for filled controls and for success text on light surfaces:
 * white on [SuccessGreen] only reaches about 2.2:1, which is below WCAG AA.
 */
val SuccessGreenDeep = Color(0xFF07704F)
val WarningAmber = Color(0xFFF59E0B)

// 5. Web Silver & Typography Colors
val TextWhite = Color(0xFFFFFFFF)
val TextWebSilver = Color(0xFFE2E8F0)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val TextDisabled = Color(0xFF475569)

// ==========================================
// Light Theme Palette (Clean Athletic Daylight)
// ==========================================
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFFFFFFF)
val LightSurfaceCard = Color(0xFFF1F5F9)
val LightBorder = Color(0xFFE2E8F0)
val LightDivider = Color(0xFFE2E8F0)

val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextMuted = Color(0xFF64748B)
val LightTextDisabled = Color(0xFF94A3B8)

val LightSpiderBluePrimary = Color(0xFF0284C7)
val LightSpiderBlueGlow = Color(0x1A0284C7)
