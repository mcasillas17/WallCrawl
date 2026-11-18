package wallcrawl.elopenmike.com.feature.welcome

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * First launch: a 2.4 s intro, then "What WallCrawl is about", then onboarding.
 * One route with two phases so the emblem can fly continuously from the intro into the
 * header (docs/first-launch-intro.md §6).
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

@OptIn(ExperimentalLayoutApi::class)
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
    val tileFill = if (dark) GraphiteSurfaceElevated else LightSurfaceCard
    val eyebrowColor = if (dark) SpiderRedVibrant else SpiderRedDeep

    Column(Modifier.fillMaxSize().graphicsLayer { alpha = fade() }.testTag(WELCOME_ABOUT_TEST_TAG)) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EmblemSlot(M.HEADER_SLOT_DP, drawSlot, glowAlpha, paths) { slots.header = it }
            Text(
                stringResource(R.string.welcome_about_eyebrow),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = eyebrowColor,
                modifier = Modifier.padding(top = 8.dp).rise({ M.eyebrow(clock()) }, 12),
            )
            Text(
                stringResource(R.string.welcome_about_headline),
                style = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
                color = colors.onBackground,
                modifier = Modifier.semantics { heading() }.rise({ M.headline(clock()) }, 16),
            )

            Spacer(Modifier.height(20.dp)) // 28 dp above the features, with the 8 dp column gap
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf(
                    Triple(Icons.Outlined.CalendarMonth, R.string.welcome_feature_plan_title, R.string.welcome_feature_plan_body),
                    Triple(Icons.Outlined.FitnessCenter, R.string.welcome_feature_log_title, R.string.welcome_feature_log_body),
                    Triple(Icons.AutoMirrored.Outlined.TrendingUp, R.string.welcome_feature_progress_title, R.string.welcome_feature_progress_body),
                ).forEachIndexed { i, (icon, title, body) ->
                    FeatureRow(icon, stringResource(title), stringResource(body), tileFill, clock, i)
                }
            }

            Spacer(Modifier.height(16.dp)) // 24 dp above the card
            Column(
                Modifier
                    .rise({ M.card(clock()) }, 16)
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.welcome_privacy_title), style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.welcome_privacy_no_account, R.string.welcome_privacy_no_ads, R.string.welcome_privacy_no_tracking)
                        .forEachIndexed { j, label ->
                            Surface(
                                color = tileFill,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colors.outlineVariant),
                                modifier = Modifier.graphicsLayer {
                                    val s = M.chipScale(j, clock()); scaleX = s; scaleY = s; alpha = M.chipAlpha(j, clock())
                                },
                            ) {
                                Box(Modifier.heightIn(min = 28.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.onBackground)
                                }
                            }
                        }
                }
                Text(stringResource(R.string.welcome_privacy_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
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

@Composable
private fun FeatureRow(icon: ImageVector, title: String, body: String, tileFill: Color, clock: () -> Float, i: Int) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top,
        modifier = Modifier.rise({ M.feature(i, clock()) }, 12)) {
        Box(
            Modifier
                .graphicsLayer { val s = M.tileScale(i, clock()); scaleX = s; scaleY = s; alpha = M.tileAlpha(i, clock()) }
                .size(44.dp)
                .background(tileFill, RoundedCornerShape(12.dp))
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onBackground)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
