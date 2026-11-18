package wallcrawl.elopenmike.com.feature.welcome

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

/**
 * The first-launch timelines from docs/first-launch-intro.md (§7, §8), as pure functions of
 * a millisecond clock. Every animated property is derived from one clock per phase, so a
 * frame is fully determined by its time and the tests can check the spec table directly.
 */
internal object WelcomeMotion {
    val IN_OUT = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    val STANDARD = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EMPH_DEC = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val OVERSHOOT = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val ACCEL = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Eased progress of a segment that starts at [start] ms and lasts [dur] ms. */
    fun seg(t: Float, start: Float, dur: Float, easing: Easing = LinearEasing): Float =
        easing.transform(((t - start) / dur).coerceIn(0f, 1f))

    fun lerp(from: Float, to: Float, f: Float) = from + (to - from) * f

    // ---- Screen 1: intro, 0 → 2440 ms ----
    const val INTRO_END = 2440f
    /** The launch splash ends in the intro's 1260 ms state, so a first launch starts here. */
    const val SPLASH_HANDOFF_MS = 1260f
    /** Every element has reached its final value; only the hold and the fade-out remain. */
    const val INTRO_SETTLED = 2180f
    const val SKIP_FADE_MS = 150
    const val INTRO_EMBLEM_DP = 168f
    const val INTRO_SLOT_DP = 120f
    const val HEADER_SLOT_DP = 40f
    const val SETTLED_SCALE = INTRO_SLOT_DP / INTRO_EMBLEM_DP
    const val KICK_X_DP = 4f
    const val KICK_Y_DP = -10f

    fun ring(t: Float) = seg(t, 0f, 450f, IN_OUT)
    fun hubScale(t: Float) = seg(t, 80f, 260f, OVERSHOOT)
    fun hubAlpha(t: Float) = seg(t, 80f, 100f)
    fun spoke(i: Int, t: Float) = seg(t, 150f + 25f * i, 200f, STANDARD)
    fun strand(i: Int, t: Float) = seg(t, 280f + 25f * i, 200f, STANDARD)
    fun arcHead(t: Float) = 450f * seg(t, 620f, 520f, IN_OUT)
    fun arcTail(t: Float) = 360f * seg(t, 700f, 440f, IN_OUT)
    fun glow(t: Float) = seg(t, 620f, 140f, STANDARD)
    fun glint6(t: Float) = seg(t, 860f, 180f, STANDARD)
    fun glint9(t: Float) = seg(t, 910f, 180f, STANDARD)
    fun streak(j: Int, t: Float) = seg(t, 1120f + 40f * j, 200f, STANDARD)
    fun streakAlpha(j: Int, t: Float) = seg(t, 1120f + 40f * j, 60f)

    fun kick(t: Float) = seg(t, 1100f, 160f, STANDARD)
    fun settle(t: Float) = seg(t, 1260f, 460f, EMPH_DEC)
    /** Emblem scale relative to its 168 dp drawing size. */
    fun introEmblemScale(t: Float) = lerp(1f + 0.03f * kick(t), SETTLED_SCALE, settle(t))

    fun wall(t: Float) = seg(t, 1400f, 300f, EMPH_DEC)
    fun pillScale(t: Float) = lerp(0.5f, 1f, seg(t, 1540f, 380f, OVERSHOOT))
    fun pillAlpha(t: Float) = seg(t, 1540f, 100f)
    fun tagline1(t: Float) = seg(t, 1820f, 280f, STANDARD)
    fun tagline2(t: Float) = seg(t, 1900f, 280f, STANDARD)
    /** Wordmark, tagline and Skip fade out; the emblem stays and flies into screen 2. */
    fun introChrome(t: Float) = 1f - seg(t, 2260f, 180f, ACCEL)

    // Reduced motion: final frame fades in, holds, fades out (emblem included).
    const val REDUCED_INTRO_END = 1880f
    fun reducedIntroAlpha(t: Float) = seg(t, 0f, 300f) * (1f - seg(t, 1700f, 180f, ACCEL))

    // ---- Screen 2: what WallCrawl is about, 0 → 1280 ms ----
    const val ABOUT_END = 1280f
    const val REDUCED_ABOUT_FADE_MS = 200

    fun flight(t: Float) = seg(t, 0f, 560f, EMPH_DEC)
    private fun aboutLap(t: Float) = seg(t, 100f, 540f, IN_OUT)
    fun aboutArcTail(t: Float) = 360f * aboutLap(t)
    fun aboutArcHead(t: Float) = 90f + 360f * aboutLap(t)
    fun arrivedViaSkipAlpha(t: Float) = seg(t, 0f, 150f)

    fun eyebrow(t: Float) = seg(t, 280f, 300f, EMPH_DEC)
    fun headline(t: Float) = seg(t, 340f, 360f, EMPH_DEC)
    fun feature(i: Int, t: Float) = seg(t, 460f + 70f * i, 320f, EMPH_DEC)
    fun tileScale(i: Int, t: Float) = lerp(0.6f, 1f, seg(t, 460f + 70f * i, 360f, OVERSHOOT))
    fun tileAlpha(i: Int, t: Float) = seg(t, 460f + 70f * i, 120f)
    fun card(t: Float) = seg(t, 700f, 360f, EMPH_DEC)
    fun chipScale(j: Int, t: Float) = lerp(0.8f, 1f, seg(t, 820f + 50f * j, 280f, OVERSHOOT))
    fun chipAlpha(j: Int, t: Float) = seg(t, 820f + 50f * j, 100f)
    fun footer(t: Float) = seg(t, 900f, 380f, EMPH_DEC)
}
