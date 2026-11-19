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

    // ---- Screen 2: what WallCrawl is about, 0 → 3000 ms real time ----
    const val ABOUT_END = 3000f
    const val REDUCED_ABOUT_FADE_MS = 200

    fun flight(t: Float) = seg(t, 0f, 900f, EMPH_DEC)
    private fun aboutLap(t: Float) = seg(t, 150f, 900f, IN_OUT)
    fun aboutArcTail(t: Float) = 360f * aboutLap(t)
    fun aboutArcHead(t: Float) = 90f + 360f * aboutLap(t)
    fun arrivedViaSkipAlpha(t: Float) = seg(t, 0f, 250f)

    fun headline(t: Float) = seg(t, 450f, 600f, EMPH_DEC)

    // Workout builder card: inputs pop left to right, the lines draw and meet, the output pops and glows.
    fun builderCard(t: Float) = seg(t, 650f, 600f, EMPH_DEC)
    fun inputScale(k: Int, t: Float) = lerp(0.6f, 1f, seg(t, 850f + 120f * k, 600f, OVERSHOOT))
    fun inputAlpha(k: Int, t: Float) = seg(t, 850f + 120f * k, 200f)
    fun connector(k: Int, t: Float) = seg(t, 1250f + 60f * k, 500f, STANDARD)
    fun outputScale(t: Float) = lerp(0.85f, 1f, seg(t, 1650f, 650f, OVERSHOOT))
    fun outputAlpha(t: Float) = seg(t, 1650f, 200f)
    fun outputGlow(t: Float) = 0.45f * seg(t, 1650f, 240f, STANDARD) * (1f - seg(t, 1950f, 600f, STANDARD))

    fun feature(i: Int, t: Float) = seg(t, 1500f + 140f * i, 550f, EMPH_DEC)
    fun tileScale(i: Int, t: Float) = lerp(0.6f, 1f, seg(t, 1500f + 140f * i, 600f, OVERSHOOT))
    fun tileAlpha(i: Int, t: Float) = seg(t, 1500f + 140f * i, 200f)
    fun privacyCard(t: Float) = seg(t, 1950f, 600f, EMPH_DEC)
    fun chipScale(j: Int, t: Float) = lerp(0.8f, 1f, seg(t, 2150f + 100f * j, 450f, OVERSHOOT))
    fun chipAlpha(j: Int, t: Float) = seg(t, 2150f + 100f * j, 150f)
    fun footer(t: Float) = seg(t, 2350f, 650f, EMPH_DEC)
}
