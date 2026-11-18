package wallcrawl.elopenmike.com.feature.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

/** The web emblem on a 100×100 viewBox (docs/first-launch-intro.md §5). */
internal object EmblemGeometry {
    val CENTER = Offset(46f, 54f)
    const val HUB_RADIUS = 7f
    val RING_TOP_LEFT = Offset(14f, 22f)
    val RING_SIZE = Size(64f, 64f)

    val SPOKES = listOf(
        "M46 47 L46 22", "M50.95 49.05 L68.63 31.37", "M53 54 L78 54", "M50.95 58.95 L68.63 76.63",
        "M46 61 L46 86", "M41.05 58.95 L23.37 76.63", "M39 54 L14 54", "M41.05 49.05 L23.37 31.37",
    )
    val STRANDS = listOf(
        "M46 32 Q50.97 41.99 61.56 38.44", "M61.56 38.44 Q58.01 49.03 68 54",
        "M68 54 Q58.01 58.97 61.56 69.56", "M61.56 69.56 Q50.97 66.01 46 76",
        "M46 76 Q41.03 66.01 30.44 69.56", "M30.44 69.56 Q33.99 58.97 24 54",
        "M24 54 Q33.99 49.03 30.44 38.44", "M30.44 38.44 Q41.03 41.99 46 32",
    )
    val GLINTS = listOf("M51.56 85.51 A32 32 0 0 1 40.44 85.51", "M14.49 59.56 A32 32 0 0 1 14.49 48.44")

    /** Filled triangles with their base (the flick pivot); j = 0 lower, 1 middle, 2 upper. */
    val STREAKS = listOf(
        "M80.63 39.67 L91.02 28.72 L78.44 37.06 Z" to Offset(79.53f, 38.36f),
        "M73.26 29.14 L87.48 14.98 L71.07 26.53 Z" to Offset(72.16f, 27.84f),
        "M62.73 21.77 L73.13 10.82 L60.54 19.16 Z" to Offset(61.64f, 20.47f),
    )
    /** Streak axis, −40°: unit vector (0.766, −0.643). */
    const val STREAK_AXIS_DEGREES = -40f
    val STREAK_AXIS = Offset(0.766f, -0.643f)
    const val STREAK_TRAVEL = 3f
}

/** Every animatable property of the emblem at one instant. */
internal class EmblemFrame(
    val ring: Float = 1f,
    val hubScale: Float = 1f,
    val hubAlpha: Float = 1f,
    val spokes: FloatArray = FloatArray(8) { 1f },
    val strands: FloatArray = FloatArray(8) { 1f },
    /** Red arc, in degrees clockwise from 12 o'clock; drawn from [arcTail] to [arcHead]. */
    val arcHead: Float = 450f,
    val arcTail: Float = 360f,
    val glow: Float = 1f,
    val glint6: Float = 1f,
    val glint9: Float = 1f,
    val streaks: FloatArray = FloatArray(3) { 1f },
    val streakAlphas: FloatArray = FloatArray(3) { 1f },
) {
    companion object {
        /** Fully drawn, arc resting 12 → 3 o'clock, streaks extended. */
        val REST = EmblemFrame()

        fun intro(t: Float) = EmblemFrame(
            ring = WelcomeMotion.ring(t),
            hubScale = WelcomeMotion.hubScale(t),
            hubAlpha = WelcomeMotion.hubAlpha(t),
            spokes = FloatArray(8) { WelcomeMotion.spoke(it, t) },
            strands = FloatArray(8) { WelcomeMotion.strand(it, t) },
            arcHead = WelcomeMotion.arcHead(t),
            arcTail = WelcomeMotion.arcTail(t),
            glow = WelcomeMotion.glow(t),
            glint6 = WelcomeMotion.glint6(t),
            glint9 = WelcomeMotion.glint9(t),
            streaks = FloatArray(3) { WelcomeMotion.streak(it, t) },
            streakAlphas = FloatArray(3) { WelcomeMotion.streakAlpha(it, t) },
        )

        fun about(t: Float) = EmblemFrame(arcHead = WelcomeMotion.aboutArcHead(t), arcTail = WelcomeMotion.aboutArcTail(t))
    }
}

/** Parsed paths, built once per composition and reused every frame. */
internal class EmblemPaths {
    private fun parse(d: String) = PathParser().parsePathString(d).toPath()
    private fun measured(d: String) = parse(d).let { path -> path to PathMeasure().apply { setPath(path, false) }.length }

    val spokes = EmblemGeometry.SPOKES.map(::measured)
    val strands = EmblemGeometry.STRANDS.map(::measured)
    val glints = EmblemGeometry.GLINTS.map(::parse)
    val streaks = EmblemGeometry.STREAKS.map { (d, base) -> parse(d) to base }
    val measure = PathMeasure()
    val scratch = Path()
}

@Composable
internal fun rememberEmblemPaths() = remember { EmblemPaths() }

internal fun DrawScope.drawEmblem(
    frame: EmblemFrame,
    paths: EmblemPaths,
    graphite: Color,
    red: Color,
    glowAlpha: Float,
) = scale(size.width / 100f, pivot = Offset.Zero) {
    val g = EmblemGeometry
    val arcSweep = frame.arcHead - frame.arcTail
    val arcStart = -90f + frame.arcTail

    if (arcSweep > 0f && frame.glow > 0f) {
        drawArc(red.copy(alpha = glowAlpha * frame.glow), arcStart, arcSweep, false, g.RING_TOP_LEFT, g.RING_SIZE,
            style = Stroke(9f, cap = StrokeCap.Round))
    }
    if (frame.ring > 0f) {
        drawArc(graphite, -90f, 360f * frame.ring, false, g.RING_TOP_LEFT, g.RING_SIZE, style = Stroke(4f, cap = StrokeCap.Butt))
    }
    val thin = Stroke(3f, cap = StrokeCap.Round)
    paths.spokes.forEachIndexed { i, (path, length) -> drawTrimmed(paths, path, length, frame.spokes[i], graphite, thin) }
    paths.strands.forEachIndexed { i, (path, length) -> drawTrimmed(paths, path, length, frame.strands[i], graphite, thin) }
    if (frame.hubAlpha > 0f && frame.hubScale > 0f) {
        scale(frame.hubScale, pivot = g.CENTER) {
            drawCircle(graphite.copy(alpha = graphite.alpha * frame.hubAlpha), g.HUB_RADIUS, g.CENTER, style = Stroke(3.5f))
        }
    }
    val bold = Stroke(4f, cap = StrokeCap.Round)
    if (arcSweep > 0f) drawArc(red, arcStart, arcSweep, false, g.RING_TOP_LEFT, g.RING_SIZE, style = bold)
    if (frame.glint6 > 0f) drawPath(paths.glints[0], red.copy(alpha = frame.glint6), style = bold)
    if (frame.glint9 > 0f) drawPath(paths.glints[1], red.copy(alpha = frame.glint9), style = bold)
    paths.streaks.forEachIndexed { j, (path, base) ->
        val p = frame.streaks[j]
        if (p <= 0f || frame.streakAlphas[j] <= 0f) return@forEachIndexed
        val shift = g.STREAK_AXIS * (g.STREAK_TRAVEL * p)
        // Stretch along the streak axis only, pivoting on the base, then slide along it.
        withTransform({
            translate(shift.x, shift.y)
            rotate(g.STREAK_AXIS_DEGREES, pivot = base)
            scale(p, 1f, pivot = base)
            rotate(-g.STREAK_AXIS_DEGREES, pivot = base)
        }) { drawPath(path, red.copy(alpha = frame.streakAlphas[j])) }
    }
}

private fun DrawScope.drawTrimmed(paths: EmblemPaths, path: Path, length: Float, progress: Float, color: Color, stroke: Stroke) {
    // Nothing at 0: a zero-length segment with round caps would still leave a dot.
    if (progress <= 0f) return
    if (progress >= 1f) return drawPath(path, color, style = stroke)
    paths.measure.setPath(path, false)
    paths.scratch.reset()
    paths.measure.getSegment(0f, length * progress, paths.scratch, true)
    drawPath(paths.scratch, color, style = stroke)
}
