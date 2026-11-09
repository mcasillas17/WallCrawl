package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.model.IllustrationVariant
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteBorder
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurface
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurfaceElevated
import wallcrawl.elopenmike.com.core.ui.theme.TextSecondary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite

/**
 * Renders provider-owned exercise visuals without exposing upstream asset paths to feature UI.
 * Multi-frame illustrations use a lightweight 1 → 2 → 3 → 2 loop.
 */
@Composable
fun ExerciseIllustration(
    exercise: Exercise?,
    visualProvider: ExerciseVisualProvider,
    modifier: Modifier = Modifier,
    height: Int = 180,
    thumbnail: Boolean = false,
    animationsEnabled: Boolean = rememberAnimationsEnabled(),
    /** Shared by a screen that shows one exercise twice, so a Pause in one holds the other. */
    playback: MutableState<Boolean>? = null,
    /**
     * False inside a larger control, such as a button that opens the full demo: there the counter and Pause
     * would be out of a screen reader's reach, so the caller pairs it with a full demo sharing [playback].
     */
    showControls: Boolean = true
) {
    val variant = LocalIllustrationVariant.current
    // Above the key: pausing holds across the exercises of a workout.
    val ownPlayback = rememberSaveable { mutableStateOf(animationsEnabled) }
    var playing by playback ?: ownPlayback
    // The setting can change while the app is away, and a saved or running state must not outlast that.
    // Only a change moves it: Play, chosen with animations off, survives leaving and re-entering composition,
    // and turning animations back on starts the loop again.
    // A shared playback is owned by its caller, which follows the setting once for every illustration it serves.
    if (playback == null && !thumbnail) FollowAnimationSetting(animationsEnabled, ownPlayback)
    key(exercise?.id, visualProvider, variant) {
        ExerciseIllustrationContent(exercise, visualProvider, variant, modifier, height, thumbnail, showControls, animationsEnabled, playing) {
            playing = !playing
        }
    }
}

/** Only a change of the setting moves [playback]; a reader's own Play or Pause stands otherwise. */
@Composable
fun FollowAnimationSetting(animationsEnabled: Boolean, playback: MutableState<Boolean>) {
    var lastEnabled by rememberSaveable { mutableStateOf(animationsEnabled) }
    LaunchedEffect(animationsEnabled) {
        if (lastEnabled != animationsEnabled) playback.value = animationsEnabled
        lastEnabled = animationsEnabled
    }
}

@Composable
private fun ExerciseIllustrationContent(
    exercise: Exercise?,
    visualProvider: ExerciseVisualProvider,
    variant: IllustrationVariant,
    modifier: Modifier,
    height: Int,
    thumbnail: Boolean,
    showControls: Boolean,
    animationsEnabled: Boolean,
    playing: Boolean,
    onTogglePlaying: () -> Unit
) {
    var useOriginals by remember { mutableStateOf(false) }
    val frames by produceState<List<ExerciseVisual>>(emptyList(), useOriginals) {
        value = exercise?.id?.let { id ->
            if (useOriginals) visualProvider.framesFor(id) else visualProvider.framesFor(id, variant)
        }.orEmpty()
    }
    val playbackSequence = remember(frames) { playbackSequence(frames.size) }
    // The position, not the frame: a frame shown on the way there and back appears twice, and Play
    // has to carry on in the direction Pause stopped it.
    var sequenceIndex by remember(frames) { mutableIntStateOf(0) }
    val frameIndex = playbackSequence.getOrElse(sequenceIndex) { 0 }

    // Turning animations off returns to the first frame; a reader's own Pause keeps where it stopped.
    LaunchedEffect(animationsEnabled) { if (!animationsEnabled) sequenceIndex = 0 }
    LaunchedEffect(frames, playing, thumbnail) {
        if (!playing || thumbnail) return@LaunchedEffect
        while (playbackSequence.size > 1 && currentCoroutineContext().isActive) {
            delay(FRAME_DURATION_MILLIS)
            sequenceIndex = (sequenceIndex + 1) % playbackSequence.size
        }
    }

    val activeFrame = frames.getOrNull(frameIndex)
    var imageFailed by remember(activeFrame?.assetPath) { mutableStateOf(false) }

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
        val vocabulary = LocalExerciseVocabulary.current
        if (activeFrame != null && !imageFailed) {
            AsyncImage(
                model = "$ANDROID_ASSET_URI_PREFIX${activeFrame.assetPath}",
                contentDescription = exercise?.let {
                    stringResource(R.string.illustration_content_description, vocabulary.exerciseName(it))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
                onError = {
                    imageFailed = true
                    useOriginals = true
                }
            )
        } else if (height <= 100 || thumbnail) {
            Icon(Icons.Default.FitnessCenter, stringResource(R.string.illustration_unavailable), tint = TextSecondary)
        } else {
            IllustrationPlaceholder(exercise = exercise)
        }

        if (showControls && frames.size > 1 && height > 100 && !thumbnail) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(GraphiteSurface.copy(alpha = 0.82f), RoundedCornerShape(6.dp))
                    .border(1.dp, GraphiteBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    // A motion picture, not information: reading each frame number would be noise.
                    .semantics { hideFromAccessibility() }
            ) {
                val locale = LocalConfiguration.current.locales[0]
                Text(
                    text = stringResource(
                        R.string.illustration_frame_counter,
                        LocaleFormatting.formatCount(frameIndex + 1, locale),
                        LocaleFormatting.formatCount(frames.size, locale)
                    ),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }
            IconButton(
                onClick = onTogglePlaying,
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.illustration_pause else R.string.illustration_play),
                    tint = TextWhite,
                    modifier = Modifier.background(GraphiteSurface.copy(alpha = 0.82f), RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

@Composable
private fun IllustrationPlaceholder(exercise: Exercise?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
        val vocabulary = LocalExerciseVocabulary.current
        Text(
            text = exercise?.let(vocabulary::exerciseName)
                ?: stringResource(R.string.illustration_unavailable),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = exercise?.programming?.movementPattern?.let { stringResource(it.labelRes) }
                ?: stringResource(R.string.illustration_motion_preview),
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

private const val ANDROID_ASSET_URI_PREFIX = "file:///android_asset/"
/** There and back, without repeating either end: three frames play 0, 1, 2, 1. */
internal fun playbackSequence(frameCount: Int): List<Int> =
    (0 until frameCount).toList() + (frameCount - 2 downTo 1).toList()

private const val FRAME_DURATION_MILLIS = 850L
