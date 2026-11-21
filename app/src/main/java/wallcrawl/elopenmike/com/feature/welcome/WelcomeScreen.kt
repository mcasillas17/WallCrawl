package wallcrawl.elopenmike.com.feature.welcome

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurfaceElevated
import wallcrawl.elopenmike.com.core.ui.theme.LightSurfaceCard
import wallcrawl.elopenmike.com.core.ui.theme.ObsidianBlack
import wallcrawl.elopenmike.com.core.ui.theme.SpiderRedDeep
import wallcrawl.elopenmike.com.core.ui.theme.SpiderRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.SpiderRedVibrant
import wallcrawl.elopenmike.com.core.ui.theme.TextDisabled
import wallcrawl.elopenmike.com.feature.welcome.WelcomeMotion as M

const val WELCOME_INTRO_TEST_TAG = "welcome_intro"
const val WELCOME_ABOUT_TEST_TAG = "welcome_about"

private enum class WelcomePhase { INTRO, ABOUT }

private val WordmarkFont = FontFamily(Font(R.font.roboto_condensed_black, FontWeight.Black))

/** Where the shared emblem's slots and the screen are, read every frame by the overlay. */
private class Slots {
    var root: LayoutCoordinates? = null
    var intro: LayoutCoordinates? = null
    var header: LayoutCoordinates? = null
    var introCenter: Offset? = null

    fun centerOf(slot: LayoutCoordinates?): Offset? {
        val r = root ?: return null
        val s = slot?.takeIf { it.isAttached } ?: return null
        return r.localBoundingBoxOf(s).center
    }
}

/** True when the system animator duration scale is 0 ("Remove animations"). */
fun isReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** TalkBack and other touch-exploration services skip the intro animation. */
fun isTouchExploration(context: Context): Boolean =
    context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isReducedMotion(context) }
}

@Composable
private fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    return remember(context) { isTouchExploration(context) }
}

/** How long the intro waits for a launch splash to decide before playing on its own. */
private const val SPLASH_WAIT_MS = 1_500L

/** Real-time animations even when the system scale is 0, so reduced-motion fades are visible. */
private val RealTime = object : MotionDurationScale { override val scaleFactor = 1f }

/**
 * First launch: a 4.7 s intro, then "Built for you", then onboarding.
 * One route with two phases so the emblem can fly continuously from the intro into the
 * header (docs/welcome-screens-v2.md §1).
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    touchExploration: Boolean = rememberTouchExploration(),
    splash: SplashHandoff? = null,
) {
    val reduced = reducedMotion || touchExploration
    var phase by rememberSaveable { mutableStateOf(if (touchExploration) WelcomePhase.ABOUT else WelcomePhase.INTRO) }
    var introStarted by rememberSaveable { mutableStateOf(false) }
    var aboutStarted by rememberSaveable { mutableStateOf(false) }
    var viaSkip by rememberSaveable { mutableStateOf(false) }
    var skipRequested by remember { mutableStateOf(false) }

    val introClock = remember { Animatable(0f) }
    val aboutClock = remember { Animatable(0f) }
    val skipFade = remember { Animatable(1f) }
    val aboutFade = remember { Animatable(if (reduced) 0f else 1f) }
    val slots = remember { Slots() }
    // Where the launch splash left the emblem; the intro continues from there at 1260 ms.
    var splashStart by remember { mutableStateOf<SplashHandoff.State.Ready?>(null) }
    var awaitingSplash by remember { mutableStateOf(false) }

    // A splash this screen will not continue from is removed straight away.
    LaunchedEffect(splash?.state, awaitingSplash) {
        if (splash?.state is SplashHandoff.State.Ready && !awaitingSplash) splash.release()
    }

    LaunchedEffect(phase, skipRequested) {
        withContext(if (reduced) RealTime else kotlin.coroutines.EmptyCoroutineContext) {
            when (phase) {
                WelcomePhase.INTRO -> {
                    when {
                        skipRequested -> {
                            skipFade.animateTo(0f, tween(M.SKIP_FADE_MS, easing = M.ACCEL))
                            viaSkip = true
                        }
                        // Recreated mid-intro: don't restart; land on screen 2 as it finishes.
                        introStarted -> aboutStarted = true
                        else -> {
                            introStarted = true
                            try {
                                val handoff = if (splash != null && !reduced) {
                                    awaitingSplash = true
                                    withTimeoutOrNull(SPLASH_WAIT_MS) {
                                        snapshotFlow { splash.state }.first { it !is SplashHandoff.State.Pending }
                                    } as? SplashHandoff.State.Ready
                                } else null
                                if (handoff != null) {
                                    // The splash ended where the intro is at 1260 ms. Draw that
                                    // frame under it, then lift the splash and carry on.
                                    splashStart = handoff
                                    introClock.snapTo(M.SPLASH_HANDOFF_MS)
                                    withFrameNanos { }
                                    withFrameNanos { }
                                }
                            } finally {
                                // Also on Skip/Back mid-wait: a splash must never be left up.
                                awaitingSplash = false
                                splash?.release()
                            }
                            val end = if (reduced) M.REDUCED_INTRO_END else M.INTRO_END
                            introClock.animateTo(end, tween((end - introClock.value).toInt(), easing = LinearEasing))
                        }
                    }
                    phase = WelcomePhase.ABOUT
                }
                WelcomePhase.ABOUT -> {
                    if (aboutStarted || reduced) {
                        aboutClock.snapTo(M.ABOUT_END)
                        if (aboutStarted) aboutFade.snapTo(1f)
                        else aboutFade.animateTo(1f, tween(M.REDUCED_ABOUT_FADE_MS, easing = LinearEasing))
                    } else {
                        aboutClock.animateTo(M.ABOUT_END, tween(M.ABOUT_END.toInt(), easing = LinearEasing))
                    }
                    aboutStarted = true
                }
            }
        }
    }

    BackHandler(enabled = phase == WelcomePhase.INTRO) { skipRequested = true }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glowAlpha = if (dark) 0.35f else 0.20f
    val paths = rememberEmblemPaths()
    val aboutSettled by remember { derivedStateOf { aboutClock.value >= M.ABOUT_END } }
    // The overlay carries the emblem while it moves; at rest it is drawn in its slot.
    val overlay = !reduced && (phase == WelcomePhase.INTRO || !aboutSettled)

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPlaced { slots.root = it }
    ) {
        when (phase) {
            WelcomePhase.INTRO -> IntroContent(
                reduced = reduced,
                clock = { introClock.value },
                skipFade = { skipFade.value },
                slots = slots,
                drawSlot = !overlay,
                glowAlpha = glowAlpha,
                paths = paths,
                onSkip = { skipRequested = true },
            )
            WelcomePhase.ABOUT -> AboutContent(
                clock = { aboutClock.value },
                fade = { aboutFade.value },
                slots = slots,
                drawSlot = !overlay,
                glowAlpha = glowAlpha,
                paths = paths,
                dark = dark,
                onGetStarted = onGetStarted,
            )
        }

        if (overlay) {
            Spacer(
                Modifier
                    .size(M.INTRO_EMBLEM_DP.dp)
                    .graphicsLayer {
                        val half = M.INTRO_EMBLEM_DP.dp.toPx() / 2f
                        val center: Offset?
                        if (phase == WelcomePhase.INTRO) {
                            val t = introClock.value
                            val fromSplash = splashStart
                            val kick = M.kick(t)
                            // Before the settle the emblem sits at screen centre (kicked), or
                            // exactly where the launch splash drew it.
                            val start = if (fromSplash != null) {
                                slots.root?.windowToLocal(fromSplash.centerInWindow)
                            } else {
                                slots.root?.size?.let { Offset(it.width / 2f, it.height / 2f) }
                                    ?.plus(Offset(M.KICK_X_DP.dp.toPx() * kick, M.KICK_Y_DP.dp.toPx() * kick))
                            }
                            val startScale = if (fromSplash != null) fromSplash.emblemWidthPx / M.INTRO_EMBLEM_DP.dp.toPx()
                            else 1f + 0.03f * kick
                            val slot = slots.centerOf(slots.intro)
                            // Remembered for screen 2, whose flight starts here after the intro leaves.
                            if (slot != null) slots.introCenter = slot
                            val s = M.settle(t)
                            center = when {
                                start == null -> null
                                slot == null -> start
                                else -> Offset(M.lerp(start.x, slot.x, s), M.lerp(start.y, slot.y, s))
                            }
                            scaleX = M.lerp(startScale, M.SETTLED_SCALE, s)
                            alpha = skipFade.value
                        } else {
                            val t = aboutClock.value
                            val f = M.flight(t)
                            val to = slots.centerOf(slots.header)
                            val from = slots.introCenter ?: to
                            center = if (from == null || to == null) null
                            else Offset(M.lerp(from.x, to.x, f), M.lerp(from.y, to.y, f))
                            scaleX = M.lerp(M.INTRO_SLOT_DP, M.HEADER_SLOT_DP, f) / M.INTRO_EMBLEM_DP
                            alpha = if (viaSkip) M.arrivedViaSkipAlpha(t) else 1f
                        }
                        scaleY = scaleX
                        if (center == null) alpha = 0f
                        translationX = (center?.x ?: 0f) - half
                        translationY = (center?.y ?: 0f) - half
                    }
                    .drawBehind {
                        val frame = if (phase == WelcomePhase.INTRO) EmblemFrame.intro(introClock.value)
                        else EmblemFrame.about(aboutClock.value)
                        drawEmblem(frame, paths, TextDisabled, SpiderRedPrimary, glowAlpha)
                    }
            )
        }
    }
}

@Composable
private fun EmblemSlot(sizeDp: Float, draw: Boolean, glowAlpha: Float, paths: EmblemPaths, onPlaced: (LayoutCoordinates) -> Unit) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .onPlaced(onPlaced)
            .then(if (draw) Modifier.drawBehind { drawEmblem(EmblemFrame.REST, paths, TextDisabled, SpiderRedPrimary, glowAlpha) } else Modifier)
    )
}

@Composable
private fun IntroContent(
    reduced: Boolean,
    clock: () -> Float,
    skipFade: () -> Float,
    slots: Slots,
    drawSlot: Boolean,
    glowAlpha: Float,
    paths: EmblemPaths,
    onSkip: () -> Unit,
) {
    // Reduced motion shows the settled frame; the whole screen fades instead.
    val t = { if (reduced) M.INTRO_SETTLED else clock() }
    val chrome = { (if (reduced) M.reducedIntroAlpha(clock()) else M.introChrome(clock())) * skipFade() }
    val density = LocalDensity.current
    // The logo is fixed in dp and ignores the font scale; everything else uses sp.
    val logoStyle = with(density) {
        TextStyle(fontFamily = WordmarkFont, fontWeight = FontWeight.Black, fontSize = 40.dp.toSp(),
            lineHeight = 44.dp.toSp(), letterSpacing = 0.5.sp)
    }

    Box(Modifier.fillMaxSize().testTag(WELCOME_INTRO_TEST_TAG)) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.graphicsLayer { alpha = if (reduced) chrome() else 1f }) {
                EmblemSlot(M.INTRO_SLOT_DP, drawSlot, glowAlpha, paths) { slots.intro = it }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .height(44.dp)
                    .graphicsLayer { alpha = chrome() }
                    .clearAndSetSemantics { contentDescription = "WallCrawl" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "WALL",
                    style = logoStyle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer {
                        val p = M.wall(t()); alpha = p; translationY = 16.dp.toPx() * (1f - p)
                    },
                )
                Box(
                    Modifier
                        .graphicsLayer { val s = M.pillScale(t()); scaleX = s; scaleY = s; alpha = M.pillAlpha(t()) }
                        .height(44.dp)
                        .background(SpiderRedPrimary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("CRAWL", style = logoStyle, color = ObsidianBlack)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.welcome_tagline_line1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val p = M.tagline1(t()); alpha = p * chrome(); translationY = 8.dp.toPx() * (1f - p)
                },
            )
            Text(
                stringResource(R.string.welcome_tagline_line2),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val p = M.tagline2(t()); alpha = p * chrome(); translationY = 8.dp.toPx() * (1f - p)
                },
            )
        }
        TextButton(
            onClick = onSkip,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .graphicsLayer { alpha = chrome() },
        ) {
            Text(
                stringResource(R.string.welcome_skip),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Modifier.rise(progress: () -> Float, dy: Int) = graphicsLayer {
    val p = progress(); alpha = p; translationY = dy.dp.toPx() * (1f - p)
}

private fun Modifier.pop(scale: () -> Float, alpha: () -> Float) = graphicsLayer {
    val s = scale(); scaleX = s; scaleY = s; this.alpha = alpha()
}

const val WELCOME_FLOW_TEST_TAG = "welcome_flow"

/** Screen 2 (docs/welcome-screens-v2.md §5): headline, flow diagram, privacy badges, Get started. */
@Composable
private fun AboutContent(
    clock: () -> Float,
    fade: () -> Float,
    slots: Slots,
    drawSlot: Boolean,
    glowAlpha: Float,
    paths: EmblemPaths,
    dark: Boolean,
    onGetStarted: () -> Unit,
) {
    val scroll = rememberScrollState()
    val colors = MaterialTheme.colorScheme
    val nodeFill = if (dark) GraphiteSurfaceElevated else LightSurfaceCard
    val connectorColor = if (dark) SpiderRedVibrant else SpiderRedDeep

    Column(Modifier.fillMaxSize().graphicsLayer { alpha = fade() }.testTag(WELCOME_ABOUT_TEST_TAG)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            // Header and diagram at the top; the badges at the bottom of the viewport when there
            // is room, otherwise 28 dp under the diagram. A Spacer(weight) can't do this in a scroll.
            Layout(
                content = {
                    Column {
                        EmblemSlot(M.HEADER_SLOT_DP, drawSlot, glowAlpha, paths) { slots.header = it }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.welcome_about_headline),
                            style = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
                            color = colors.onBackground,
                            modifier = Modifier.semantics { heading() }.rise({ M.headline(clock()) }, 16),
                        )
                        Spacer(Modifier.height(32.dp))
                        FlowDiagram(clock, nodeFill, connectorColor)
                    }
                    PrivacyBadges(clock, nodeFill)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 16.dp),
            ) { measurables, constraints ->
                val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                val top = measurables[0].measure(loose)
                val badges = measurables[1].measure(loose)
                val inner = viewport.roundToPx() - 40.dp.roundToPx()
                val badgesY = maxOf(top.height + 28.dp.roundToPx(), inner - badges.height)
                layout(constraints.maxWidth, badgesY + badges.height) {
                    top.place(0, 0)
                    badges.place(0, badgesY)
                }
            }
        }

        Column(Modifier.rise({ M.footer(clock()) }, 24).background(colors.background)) {
            if (scroll.maxValue > 0) HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .padding(top = 12.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.welcome_get_started), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
            }
        }
    }
}

/** Y positions measured from the labels, written by the layout and read when drawing. */
private class FlowGeometry {
    var conStart = 0f
    var centerTop = 0f
    var arrowStart = 0f
    var logTop = 0f
}

/**
 * Goals, equipment and progress build your workout; you log it; the log feeds progress
 * (§5.2, §5.3). X is a fraction of the width, Y follows the measured label heights.
 * TalkBack reads it as one sentence.
 */
@Composable
private fun FlowDiagram(clock: () -> Float, nodeFill: Color, connectorColor: Color) {
    val colors = MaterialTheme.colorScheme
    val geo = remember { FlowGeometry() }
    val a11y = stringResource(R.string.welcome_flow_a11y)
    val inputs = listOf(
        Icons.Outlined.TrackChanges to R.string.welcome_flow_goals,
        Icons.Outlined.FitnessCenter to R.string.welcome_flow_equipment,
        Icons.AutoMirrored.Outlined.TrendingUp to R.string.welcome_flow_progress,
    )

    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // 112 dp, or a third of the width when that is narrower, so neighbouring labels never overlap.
    val inputLabelWidth = minOf(112.dp, maxWidth / 3)
    val smallStyle = fitWords(
        (inputs.map { it.second } + R.string.welcome_flow_log).map { stringResource(it) },
        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), inputLabelWidth)
    val workoutStyle = fitWords(listOf(stringResource(R.string.welcome_flow_workout)),
        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), 140.dp)

    @Composable
    fun Label(id: Int, style: TextStyle, alpha: () -> Float) = Text(
        stringResource(id), style = style, color = colors.onBackground, textAlign = TextAlign.Center,
        modifier = Modifier.graphicsLayer { this.alpha = alpha() },
    )

    Layout(
        content = {
            inputs.forEachIndexed { k, (icon, _) ->
                Node(56, icon, 24, nodeFill, colors.outlineVariant, colors.primary,
                    Modifier.pop({ M.inputScale(k, clock()) }, { M.inputAlpha(k, clock()) }).then(
                        if (k == 2) Modifier.drawWithContent {
                            drawContent()
                            val p = M.ping(clock())
                            if (p > 0f) {
                                val stroke = 2.dp.toPx()
                                drawCircle(SpiderRedPrimary.copy(alpha = 0.7f * (1f - p)),
                                    radius = (size.minDimension - stroke) / 2f * (1f + 0.5f * p), style = Stroke(stroke))
                            }
                        } else Modifier
                    ))
            }
            inputs.forEachIndexed { k, (_, label) -> Label(label, smallStyle) { M.inputAlpha(k, clock()) } }
            Node(88, Icons.AutoMirrored.Outlined.Assignment, 36, nodeFill, null, colors.primary,
                Modifier
                    .pop({ M.workoutScale(clock()) }, { M.workoutAlpha(clock()) })
                    .drawBehind {
                        val glow = M.workoutGlow(clock())
                        if (glow > 0f) drawCircle(SpiderRedPrimary.copy(alpha = glow), radius = 50.dp.toPx())
                    }
                    .drawWithContent {
                        drawContent()
                        val p = M.workoutRing(clock())
                        if (p > 0f) {
                            val r = 43.dp.toPx()
                            drawArc(SpiderRedPrimary, startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                                topLeft = center - Offset(r, r), size = Size(2 * r, 2 * r), style = Stroke(2.dp.toPx()))
                        }
                    })
            Label(R.string.welcome_flow_workout, workoutStyle) { M.workoutAlpha(clock()) }
            Node(56, Icons.Outlined.TaskAlt, 24, nodeFill, colors.outlineVariant, colors.primary,
                Modifier.pop({ M.logScale(clock()) }, { M.logAlpha(clock()) }))
            Label(R.string.welcome_flow_log, smallStyle) { M.logAlpha(clock()) }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WELCOME_FLOW_TEST_TAG)
            .clearAndSetSemantics { contentDescription = a11y }
            .drawWithCache {
                val w = size.width
                val dp = 1.dp.toPx()
                val (conStart, centerTop, arrowStart, logTop) = listOf(geo.conStart, geo.centerTop, geo.arrowStart, geo.logTop)
                fun side(x: Float) = Path().apply {
                    moveTo(x, conStart); cubicTo(x, conStart + 16 * dp, w / 2f, centerTop - 16 * dp, w / 2f, centerTop)
                }
                val connectors = listOf(side(w / 6f), Path().apply { moveTo(w / 2f, conStart); lineTo(w / 2f, centerTop) }, side(5f * w / 6f))
                    .map { PathMeasure().apply { setPath(it, false) } }
                val downEnd = Offset(w / 2f, arrowStart + 22 * dp)
                val down = PathMeasure().apply { setPath(Path().apply { moveTo(w / 2f, arrowStart); lineTo(downEnd.x, downEnd.y) }, false) }
                val loopEnd = Offset(291f / 312f * w, 40 * dp)
                val loopCp2 = Offset(w, conStart + 28 * dp)
                val loop = PathMeasure().apply {
                    setPath(Path().apply {
                        moveTo(184f / 312f * w, logTop + 28 * dp)
                        cubicTo(300f / 312f * w, logTop + 28 * dp, loopCp2.x, loopCp2.y, loopEnd.x, loopEnd.y)
                    }, false)
                }
                val downHead = arrowHead(downEnd, Offset(0f, 1f), 7 * dp)
                val loopHead = arrowHead(loopEnd, loopEnd - loopCp2, 9 * dp)
                val thin = Stroke(1.5f * dp, cap = StrokeCap.Round)
                val thick = Stroke(2 * dp, cap = StrokeCap.Round)
                val drawn = Path()
                fun DrawScope.partial(line: PathMeasure, p: Float, color: Color, stroke: Stroke) {
                    // A zero-length round-capped segment would still leave a dot.
                    if (p <= 0f) return
                    drawn.reset()
                    line.getSegment(0f, line.length * p, drawn, true)
                    drawPath(drawn, color, style = stroke)
                }
                onDrawBehind {
                    val t = clock()
                    connectors.forEachIndexed { k, line -> partial(line, M.connector(k, t), connectorColor, thin) }
                    val d = M.down(t)
                    partial(down, d, connectorColor, thin)
                    if (d > 0.6f) drawPath(downHead, connectorColor)
                    val l = M.loop(t)
                    partial(loop, l, SpiderRedPrimary, thick)
                    if (l >= 0.97f) drawPath(loopHead, SpiderRedPrimary)
                }
            },
    ) { m, constraints ->
        val w = constraints.maxWidth
        fun fixed(dp: Int) = Constraints.fixedWidth(dp.dp.roundToPx())
        val nodes = (0..2).map { m[it].measure(Constraints()) }
        val labels = (3..5).map { m[it].measure(Constraints.fixedWidth(inputLabelWidth.roundToPx())) }
        val workout = m[6].measure(Constraints())
        val workoutLabel = m[7].measure(fixed(140))
        val log = m[8].measure(Constraints())
        val logLabel = m[9].measure(fixed(112))

        fun px(dp: Int) = dp.dp.roundToPx()
        val inLabH = labels.maxOf { it.height }
        val conStart = px(62) + inLabH + px(4)
        val centerTop = conStart + px(24)
        val arrowStart = centerTop + px(94) + workoutLabel.height + px(6)
        val logTop = arrowStart + px(24)
        geo.conStart = conStart.toFloat()
        geo.centerTop = centerTop.toFloat()
        geo.arrowStart = arrowStart.toFloat()
        geo.logTop = logTop.toFloat()

        val xs = listOf(w / 6f, w / 2f, 5f * w / 6f)
        fun Placeable.centredAt(x: Float) = (x - width / 2f).roundToInt()
        layout(w, logTop + px(62) + logLabel.height) {
            nodes.forEachIndexed { k, n -> n.place(n.centredAt(xs[k]), 0) }
            labels.forEachIndexed { k, l -> l.place(l.centredAt(xs[k]), px(62)) }
            workout.place(workout.centredAt(w / 2f), centerTop)
            workoutLabel.place(workoutLabel.centredAt(w / 2f), centerTop + px(94))
            log.place(log.centredAt(w / 2f), logTop)
            logLabel.place(logLabel.centredAt(w / 2f), logTop + px(62))
        }
    }
    }
}

/** A filled triangle with its tip at [tip], pointing along [direction]; half-angle 0.55 rad. */
private fun arrowHead(tip: Offset, direction: Offset, length: Float): Path {
    val angle = atan2(direction.y, direction.x)
    fun back(sign: Int) = tip - Offset(cos(angle + sign * 0.55f), sin(angle + sign * 0.55f)) * length
    return Path().apply {
        moveTo(tip.x, tip.y)
        back(1).let { lineTo(it.x, it.y) }
        back(-1).let { lineTo(it.x, it.y) }
        close()
    }
}

@Composable
private fun Node(sizeDp: Int, icon: ImageVector, iconDp: Int, fill: Color, border: Color?, tint: Color, modifier: Modifier) {
    Box(
        modifier
            .size(sizeDp.dp)
            .background(fill, CircleShape)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconDp.dp))
    }
}

/** No account, no ads, no tracking, offline (§5.4). Each reads as one node; none is clickable. */
@Composable
private fun PrivacyBadges(clock: () -> Float, fill: Color) {
    val colors = MaterialTheme.colorScheme
    val badges = listOf(
        Icons.Outlined.PersonOff to R.string.welcome_privacy_no_account,
        Icons.Outlined.Block to R.string.welcome_privacy_no_ads,
        Icons.Outlined.VisibilityOff to R.string.welcome_privacy_no_tracking,
        Icons.Outlined.WifiOff to R.string.welcome_privacy_offline,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val labelStyle = fitWords(badges.map { stringResource(it.second) },
        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), (maxWidth - 24.dp) / 4)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        badges.forEachIndexed { j, (icon, label) ->
            Column(
                Modifier
                    .weight(1f)
                    .pop({ M.badgeScale(j, clock()) }, { M.badgeAlpha(j, clock()) })
                    .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Node(44, icon, 22, fill, colors.outlineVariant, colors.onSurfaceVariant, Modifier)
                Text(stringResource(label), style = labelStyle, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
    }
}


/**
 * [style] shrunk just enough that the widest word in [texts] fits [width] on one line, so no
 * label breaks mid-word on a narrow screen with large text. Unchanged when everything fits.
 * One scale per group keeps neighbouring labels the same size.
 */
@Composable
private fun fitWords(texts: List<String>, style: TextStyle, width: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val px = with(LocalDensity.current) { width.toPx() }
    val widest = remember(texts, style, measurer) {
        texts.flatMap { it.split(' ') }.maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }
    }
    // A little slack: measured and laid-out widths can differ by a pixel after scaling.
    val scale = (px / widest * 0.97f).coerceAtMost(1f)
    return if (scale >= 1f) style else style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale)
}
