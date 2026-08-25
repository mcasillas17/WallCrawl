package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteBorder
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurface
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurfaceElevated
import wallcrawl.elopenmike.com.core.ui.theme.TextMuted
import wallcrawl.elopenmike.com.core.ui.theme.TextSecondary

/**
 * Reusable exercise illustration component.
 * Currently renders a polished visual placeholder with movement pattern metadata,
 * designed to be seamlessly swapped with Workout Guide SVG frame animations.
 */
@Composable
fun ExerciseVisual(
    exercise: Exercise?,
    modifier: Modifier = Modifier,
    height: Int = 180
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(GraphiteSurfaceElevated, GraphiteSurface)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Subtle background grid accent
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0x22E63946), RoundedCornerShape(28.dp))
                    .border(1.dp, CrimsonRedPrimary.copy(alpha = 0.5f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = CrimsonRedPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = exercise?.name ?: "Exercise Illustration",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Workout Guide SVG · ${exercise?.movementPattern?.name?.replace('_', ' ') ?: "Motion"}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        // Frame indicator pill in top right corner
        if (exercise?.imageFrames?.isNotEmpty() == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(GraphiteSurface.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .border(1.dp, GraphiteBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${exercise.imageFrames.size} Frames",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
            }
        }
    }
}
