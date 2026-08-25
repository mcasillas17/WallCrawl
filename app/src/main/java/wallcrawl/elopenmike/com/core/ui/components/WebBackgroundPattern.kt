package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Renders a subtle geometric web background motif
 * providing an agile, Spider-Man-inspired atmosphere without any copyrighted assets.
 */
@Composable
fun WebBackgroundPattern(
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0x1238BDF8) // subtle electric web strand glow
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Origin at top-right corner
        val originX = width * 0.88f
        val originY = height * 0.08f

        // Draw subtle radial web strands
        val numRays = 7
        val maxRadius = width.coerceAtLeast(height) * 0.85f

        val rayAngles = listOf(105f, 130f, 155f, 180f, 205f, 230f, 255f)
        val rayEndpoints = mutableListOf<Offset>()

        for (angle in rayAngles) {
            val rad = Math.toRadians(angle.toDouble())
            val endX = originX + (maxRadius * Math.cos(rad)).toFloat()
            val endY = originY + (maxRadius * Math.sin(rad)).toFloat()
            rayEndpoints.add(Offset(endX, endY))

            drawLine(
                color = lineColor,
                start = Offset(originX, originY),
                end = Offset(endX, endY),
                strokeWidth = 1.2f
            )
        }

        // Draw concentric connecting web polygons
        val rings = 6
        for (r in 1..rings) {
            val fraction = (r.toFloat() / rings) * (r.toFloat() / rings) // ease out
            val path = Path()

            for (i in rayEndpoints.indices) {
                val end = rayEndpoints[i]
                val pointX = originX + (end.x - originX) * fraction
                val pointY = originY + (end.y - originY) * fraction

                if (i == 0) {
                    path.moveTo(pointX, pointY)
                } else {
                    // slight arc between rays
                    val prevEnd = rayEndpoints[i - 1]
                    val prevX = originX + (prevEnd.x - originX) * fraction
                    val prevY = originY + (prevEnd.y - originY) * fraction
                    val midX = (prevX + pointX) / 2 + (originX - (prevX + pointX) / 2) * 0.08f
                    val midY = (prevY + pointY) / 2 + (originY - (prevY + pointY) / 2) * 0.08f

                    path.quadraticTo(midX, midY, pointX, pointY)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 1.0f)
            )
        }
    }
}
