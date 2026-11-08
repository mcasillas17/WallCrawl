package wallcrawl.elopenmike.com.feature.conditions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R

@Composable
internal fun ConditionRegionIcon(region: String) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    Box(Modifier.size(32.dp, 48.dp)) {
        Icon(painterResource(R.drawable.ic_reference_body), contentDescription = null,
            modifier = Modifier.matchParentSize(),
            tint = if (region == "Systemic or multiple regions") accent else colors.onSurfaceVariant.copy(alpha = 0.48f))
        Canvas(Modifier.matchParentSize()) {
            withTransform({ scale(size.width / 32f, size.height / 48f, Offset.Zero) }) {
                fun mark(fromX: Float, fromY: Float, toX: Float, toY: Float) =
                    drawLine(accent, Offset(fromX, fromY), Offset(toX, toY), 3f, StrokeCap.Round)
                when (region) {
                    "Spine" -> mark(16f, 9f, 16f, 23f)
                    "Shoulder" -> {
                        drawCircle(accent, 2.6f, Offset(11f, 11f))
                        drawCircle(accent, 2.6f, Offset(21f, 11f))
                    }
                    "Elbow, wrist, hand" -> {
                        mark(8f, 17f, 5f, 23f)
                        mark(24f, 17f, 27f, 23f)
                    }
                    "Hip, groin, gluteal" -> mark(12f, 23f, 20f, 23f)
                    "Knee" -> {
                        drawCircle(accent, 2.4f, Offset(13.1f, 33f))
                        drawCircle(accent, 2.4f, Offset(18.9f, 33f))
                    }
                    "Lower leg, ankle, foot" -> {
                        mark(13f, 36f, 12f, 44f)
                        mark(19f, 36f, 20f, 44f)
                    }
                    else -> Unit
                }
            }
        }
    }
}
