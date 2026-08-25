package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteBorder
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurface
import wallcrawl.elopenmike.com.core.ui.theme.TextPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextSecondary

@Composable
fun StatBadge(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badgeColor: Color = GraphiteSurface,
    borderColor: Color = GraphiteBorder,
    textColor: Color = TextPrimary
) {
    Box(
        modifier = modifier
            .background(badgeColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

@Composable
fun MetricHighlight(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueColor: Color = TextPrimary
) {
    Box(
        modifier = modifier
            .background(GraphiteSurface, RoundedCornerShape(12.dp))
            .border(1.dp, GraphiteBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = TextSecondary
                )
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = valueColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
