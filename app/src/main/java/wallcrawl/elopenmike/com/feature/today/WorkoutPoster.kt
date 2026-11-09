package wallcrawl.elopenmike.com.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.model.WorkoutTitleSpec
import wallcrawl.elopenmike.com.core.ui.components.ExerciseStill
import wallcrawl.elopenmike.com.core.ui.localization.generatedWorkoutTitle
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurface
import wallcrawl.elopenmike.com.core.ui.theme.SpiderRedDeep
import wallcrawl.elopenmike.com.core.ui.theme.TextPrimary

@Composable
internal fun WorkoutPoster(
    title: WorkoutTitleSpec,
    exerciseId: String?,
    visualProvider: ExerciseVisualProvider?,
    source: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides TextPrimary) {
        Column(Modifier.fillMaxWidth().drawWithCache {
            val field = Brush.horizontalGradient(
                0.22f to GraphiteSurface, 0.67f to Color(0xFF34252D), 1f to SpiderRedDeep
            )
            val stripe = Color(0x22DA627D)
            val stripes = Brush.linearGradient(
                0.57f to Color.Transparent, 0.57f to stripe, 0.64f to stripe, 0.64f to Color.Transparent,
                0.78f to Color.Transparent, 0.78f to stripe, 0.86f to stripe, 0.86f to Color.Transparent,
                end = Offset(size.width, size.width * 0.38f)
            )
            onDrawBehind {
                drawRect(field)
                drawRect(stripes)
            }
        }.padding(horizontal = 16.dp, vertical = 6.dp)) {
            source()
            BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                val split = stringResource(title.split.labelRes).uppercase(LocalConfiguration.current.locales[0])
                val shortScreen = LocalConfiguration.current.screenHeightDp < 680
                val compact = shortScreen || maxWidth < 280.dp
                val style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (compact) 44.sp else 52.sp,
                    lineHeight = if (compact) 42.sp else 50.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = (-1.8).sp,
                    textGeometricTransform = TextGeometricTransform(scaleX = 0.82f)
                )
                // Keep each localized title word intact before assigning space to the figure.
                val words = rememberTextMeasurer().measure(split.replace(' ', '\n'), style)
                val sideSpace = maxWidth - with(LocalDensity.current) { words.size.width.toDp() } - 8.dp
                val art: @Composable (Modifier) -> Unit = { modifier ->
                    if (exerciseId != null && visualProvider != null) ExerciseStill(exerciseId, visualProvider, modifier)
                }
                if (sideSpace < 72.dp) {
                    Column(Modifier.fillMaxWidth()) {
                        PosterTitle(title, split, style, Modifier.fillMaxWidth())
                        art(Modifier.align(Alignment.End).size(112.dp))
                    }
                } else {
                    val artWidth = minOf(maxWidth * 0.48f, sideSpace, 180.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        PosterTitle(title, split, style, Modifier.weight(1f))
                        art(Modifier.width(artWidth).height(artWidth.coerceAtMost(if (shortScreen) 88.dp else 152.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterTitle(title: WorkoutTitleSpec, split: String, style: TextStyle, modifier: Modifier) {
    val spokenTitle = generatedWorkoutTitle(title)
    Column(modifier.clearAndSetSemantics {
        heading()
        text = AnnotatedString(spokenTitle)
    }.padding(vertical = 6.dp)) {
        Text(split, style = style)
        Text(stringResource(title.emphasis.labelRes), Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleSmall)
        if (title.isReEntry) {
            Text(stringResource(R.string.train_reentry_label), color = Color(0xFFDFC4CD),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
