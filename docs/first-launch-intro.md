# First-launch intro + "What WallCrawl is about" — implementation spec

Self-contained brief for implementing the two first-launch screens in WallCrawl.
Everything needed is in this file: flow, layout (dp/sp), colors, copy (EN + ES),
emblem path data, the full timeline, accessibility, and acceptance criteria.

Design source (visual reference only, not needed to implement):
https://claude.ai/artifact/AQWPWjQsSUV2a22iz14bgh

---

## 1. Flow and where it plugs in

```
EVERY cold/warm start ─► system splash: animated emblem (docs/launch-animation.md), held until the profile has loaded
app start ─► profile == null ─► (splash still showing; the existing spinner should no longer be visible)
           ─► !onboardingCompleted && !introSeen ─► WELCOME (phase INTRO → phase ABOUT) ─► ONBOARDING
           ─► !onboardingCompleted &&  introSeen ─► ONBOARDING   (unchanged)
           ─►  onboardingCompleted               ─► TODAY        (unchanged)
```

- Add `AppRoutes.WELCOME = "welcome"` in `app/WallCrawlNavigation.kt`.
- In `WallCrawlApp.kt`, the `startDestination` expression (currently ONBOARDING vs TODAY) gains the WELCOME case above.
- **One route, two phases.** Implement both screens inside a single `WelcomeScreen` composable
  (`feature/welcome/`) with an internal `phase` state (`INTRO`, `ABOUT`). Do **not** use two
  NavHost destinations plus `SharedTransitionLayout`: the emblem must fly continuously from the
  intro into screen 2's header, and a single composable with one overlay emblem is simpler and
  deterministic (see §6).
- **Get started** (screen 2): set `introSeen = true`, then
  `navController.navigate(AppRoutes.ONBOARDING) { popUpTo(AppRoutes.WELCOME) { inclusive = true } }`.
- **`introSeen` storage:** a device setting, not profile data. Use a small SharedPreferences file
  (e.g. `"first_launch"`, key `"intro_seen"`), the same way `TODAY_PREFS` is handled. It is **not**
  part of the export archive, and it is **not** cleared by delete-all-data / restore
  (`restartAtOnboarding()` keeps going straight to ONBOARDING; do not replay the intro there).
- The existing `LaunchedEffect(startDestination, currentRoute)` that forces a return to
  ONBOARDING must treat WELCOME as allowed (don't bounce the user from WELCOME to ONBOARDING).
- **Back:** during INTRO, Back = Skip. During ABOUT, Back = default (exits the app; WELCOME is the start destination).
- **TalkBack on** (`AccessibilityManager.isTouchExplorationEnabled`): start directly in ABOUT using the reduced-motion entrance.
- Insets: the Scaffold's `innerPadding` (already applied to the NavHost, and the WELCOME route has no
  top/bottom bar) covers the system bars. Don't add `statusBarsPadding()` again — verify on device
  that Skip sits 8 dp below the status bar and Get started sits 24 dp above the nav bar.
- Both screens paint `MaterialTheme.colorScheme.background`, same as `OnboardingScreen`, so there
  is no flash in either theme. Support **both** light and dark (follows `ThemePreference`).

---

## 2. Colors and type (existing tokens only)

| Use | Dark | Light |
|---|---|---|
| Background | `ObsidianBlack` #101113 | `LightBackground` #F8F7F5 |
| Card surface | `GraphiteSurface` #17181B | `LightSurface` #FFFFFF |
| Icon tile / chip fill | `GraphiteSurfaceElevated` #202125 | `LightSurfaceCard` #F5F4F2 |
| Card/tile/chip border (1 dp) | `GraphiteDivider` #313238 | `LightDivider` #DEDCDF |
| Primary text | `TextPrimary` #F5F4F2 | `LightTextPrimary` #1B1B1F |
| Secondary text, Skip | `TextSecondary` #B7B6BB | `LightTextSecondary` #54545C |
| Emblem graphite (both themes) | `TextDisabled` #54545C | `TextDisabled` #54545C |
| Emblem red, arc, streaks, wordmark pill | `SpiderRedPrimary` #DE2045 | same |
| Builder connectors (screen 2) | `SpiderRedVibrant` #FF6C87 | `SpiderRedDeep` #602334 |
| Feature/shield icons | #FF6C87 | #DE2045 |
| Get started button | #FF6C87 bg / #101113 text (= `colorScheme.primary/onPrimary`) | #DE2045 / #FFFFFF (same) |
| Arc glow alpha | 0.35 | 0.20 |
| "CRAWL" text in pill | #101113 (4.0:1, large bold) | #101113 |

Use `MaterialTheme.colorScheme` where a role exists (background, surface, onBackground,
onSurfaceVariant, primary, onPrimary, outlineVariant); the raw tokens above are for the emblem,
pill, and builder connectors.

Type: `WallCrawlTypography` (FontFamily.SansSerif) except the wordmark.

**Wordmark font:** Roboto Condensed, weight 900 (Black). Bundle it as `res/font/roboto_condensed_black.ttf`
(Google Fonts, SIL OFL 1.1) and add it to the Credits & Licenses screen. If the font file can't be
fetched in your environment, stop and ask rather than substituting.

---

## 3. Screen 1 — Intro (final frame layout)

Full-screen, background color. Content is a centered `Column`, horizontal padding 24 dp,
`horizontalAlignment = CenterHorizontally`, vertically centered in the screen.

| # | Element | Spec |
|---|---|---|
| 1 | Emblem slot | `Box(Modifier.size(120.dp))` — empty placeholder; the emblem itself is drawn by the overlay (§6). Measure its bounds. |
| — | Gap | 24 dp |
| 2 | Wordmark row | Height 44 dp. `Row(verticalAlignment = CenterVertically, horizontalArrangement = spacedBy(8.dp))`. Semantics: `clearAndSetSemantics { contentDescription = "WallCrawl" }`. |
|   | "WALL" | Roboto Condensed Black, **40 dp-equivalent** (`with(LocalDensity.current) { 40.dp.toSp() }` so it ignores font scale), lineHeight 44 dp-equivalent, letterSpacing 0.5 sp, color onBackground. |
|   | "CRAWL" pill | Height 44 dp, horizontal padding 12 dp, `RoundedCornerShape(8.dp)`, bg #DE2045, same text style, color #101113. |
| — | Gap | 20 dp |
| 3 | Tagline line 1 | `titleMedium` (16/22 sp SemiBold), onBackground, centered. |
| 4 | Tagline line 2 | `bodyLarge` (16/24 sp), onSurfaceVariant, centered. Says what WallCrawl does; wraps to 2 lines at 360 dp. |
| 5 | Skip | `TextButton`, aligned TopEnd, padding top 8 dp, end 8 dp. Min 48×48 dp, contentPadding horizontal 12 dp. `labelLarge` (14 sp Bold, +0.5 sp), color onSurfaceVariant. Visible from frame 0. |

Block height at 100% font: 120+24+44+20+22+48 = 278 dp (line 2 wraps) → top ≈261 dp on 360×800, ≈181 dp on 320×640. Measure; don't hard-code.
At 200% only the tagline and Skip grow (tagline may wrap to 5–6 lines; must not clip at 320×640).

Copy (`res/values/strings.xml` / `res/values-es/strings.xml`):

| Key | EN | ES |
|---|---|---|
| `welcome_skip` | Skip | Omitir |
| `welcome_tagline_line1` | Train. Log. Progress. | Entrena. Registra. Progresa. |
| `welcome_tagline_line2` | Workouts built from your goals, progress and equipment. | Rutinas según tus metas, tu progreso y tu equipo. |

("WALL"/"CRAWL" are the logo, not translated, not string resources — or add them as `translatable="false"`.)

---

## 4. Screen 2 — What WallCrawl is about (layout)

`Column(fillMaxSize)`:
- **Scroll area** (`weight(1f)`, `verticalScroll`), padding: top 24 dp, horizontal 24 dp, bottom 16 dp.
- **Footer** (outside the scroll): padding top 12, horizontal 24, bottom 24 dp; bg = background.
  Show a 1 dp `outlineVariant` divider on top of the footer **only when the scroll area can scroll**
  (`scrollState.maxValue > 0`).

Scroll content, top to bottom:

| # | Element | Spec |
|---|---|---|
| 1 | Header emblem slot | `Box(Modifier.size(40.dp))` placeholder (emblem drawn by the overlay; measure its bounds). |
|   | Gap | 16 dp |
|   | Headline | 28/34 sp, `FontWeight.Black`, letterSpacing −0.5 sp, onBackground, `semantics { heading() }`. |
| 2 | Workout builder card | 28 dp above. bg surface, 1 dp border, `RoundedCornerShape(16.dp)`, padding 16 dp, inner gap 12 dp. Shows that WallCrawl builds each workout from the user's goals, logged progress and equipment. |
|   | Card title | `titleMedium` 16/22 SemiBold, onBackground. |
|   | Input tiles | 3 equal columns (`Row` + `weight(1f)`), gap 8 dp. Each: min height 56 dp, padding 8/4 dp, `RoundedCornerShape(12.dp)`, tile fill + 1 dp border; icon 20 dp in icon color over `labelMedium` 12 sp SemiBold, 4 dp gap, centred. Icons: `Icons.Outlined.TrackChanges` (goals), `Icons.AutoMirrored.Outlined.TrendingUp` (progress), `Icons.Outlined.FitnessCenter` (equipment). Not clickable. |
|   | Connectors | See `docs/welcome-workout-builder.md` §3: inputs, a 20 dp connector canvas and the output tile sit in an inner `Column` with no spacing; three 1.5 dp round-cap strokes in the connector colour converge from each input column's centre to the middle. Animate with `PathMeasure` stroke progress. Decorative. |
|   | Output tile | `Row(spacedBy(12.dp), CenterVertically)`, padding 10/12 dp, `RoundedCornerShape(12.dp)`, tile fill, **1.5 dp #DE2045 border**. Static emblem 28 dp (§5, at rest) + title `titleSmall` 14/20 SemiBold onBackground + subtitle `bodySmall` 12/16 onSurfaceVariant. Glow: a 4 dp ring outside the border, #DE2045 at the animated alpha (§8), drawn with `drawBehind`. |
|   | Semantics | Merge the card: `semantics(mergeDescendants = true)` so TalkBack reads title, the three inputs, then the output. |
| 3 | Features | 24 dp below the card. 2 rows (log, progress), 20 dp between rows. Row: `Row(spacedBy(14.dp), verticalAlignment = Top)`. |
|   | Icon tile | 44×44 dp, `RoundedCornerShape(12.dp)`, tile fill, 1 dp border, icon 22 dp stroke-style in icon color. Icons: `Icons.Outlined.Checklist` (log), `Icons.AutoMirrored.Outlined.TrendingUp` (progress) (material-icons-extended is already a dependency). Decorative (`contentDescription = null`). |
|   | Text | Title `titleMedium` 16/22 SemiBold onBackground; body `bodyMedium` 14/20 onSurfaceVariant; 2 dp gap. |
| 4 | Privacy card | 24 dp above. bg surface, 1 dp border, `RoundedCornerShape(16.dp)`, padding 16 dp, inner gap 10 dp. |
|   | Card title row | `Icons.Outlined.VerifiedUser` (or `Shield`) 22 dp in icon color + 10 dp + `titleMedium` title. |
|   | Chips | `FlowRow(spacedBy(8.dp))`. Each: min height 28 dp, horizontal padding 10 dp, `RoundedCornerShape(14.dp)`, tile fill + 1 dp border, `labelMedium` 12 sp SemiBold, onBackground. **Not clickable** (plain `Surface`/`Box`, no ripple). |
|   | Body | `bodyMedium` 14/20 onSurfaceVariant. |
| 5 | Get started | In footer. M3 `Button`, `fillMaxWidth`, min height 56 dp, pill shape (default), text 16 sp Bold, +0.3 sp. Colors default `primary/onPrimary`. |

Copy:

| Key | EN | ES |
|---|---|---|
| `welcome_about_headline` | Your training, start to finish. | Tu entrenamiento, de principio a fin. |
| `welcome_builder_title` | Workouts built for you | Rutinas hechas para ti |
| `welcome_builder_goals` | Goals | Metas |
| `welcome_builder_progress` | Progress | Progreso |
| `welcome_builder_equipment` | Equipment | Equipo |
| `welcome_builder_output_title` | Your next workout | Tu próxima rutina |
| `welcome_builder_output_body` | Adjusts as you log. | Se ajusta con lo que registras. |
| `welcome_feature_log_title` | Log every set | Registra cada serie |
| `welcome_feature_log_body` | Reps and weight in a tap or two. | Peso y repeticiones en pocos toques. |
| `welcome_feature_progress_title` | Track progress | Mide tu progreso |
| `welcome_feature_progress_body` | See each lift improve over time. | Mira cómo avanza cada ejercicio. |
| `welcome_privacy_title` | Private by design | Privado por diseño |
| `welcome_privacy_no_account` | No account | Sin cuenta |
| `welcome_privacy_no_ads` | No ads | Sin anuncios |
| `welcome_privacy_no_tracking` | No tracking | Sin rastreo |
| `welcome_privacy_body` | Stays on this phone, even offline. | En tu teléfono, incluso sin conexión. |
| `welcome_get_started` | Get started | Comenzar |

Follow `docs/localization.md`: add every key to both files.

---

## 5. Emblem geometry (100 × 100 viewBox)

Draw with a `Canvas`, scaling the viewBox to the emblem's current size
(`scale(size.width / 100f, pivot = Offset.Zero)`). Parse paths once with
`PathParser().parsePathString(d).toPath()` and keep them in `remember`.

```kotlin
object EmblemGeometry {
    val CENTER = Offset(46f, 54f)
    const val RING_RADIUS = 32f      // stroke 4, graphite, butt cap
    const val HUB_RADIUS = 7f        // stroke 3.5, graphite
    // Ring as a path: starts at 12 o'clock, clockwise. Length 201.06.
    const val RING = "M46 22 A32 32 0 1 1 46 86 A32 32 0 1 1 46 22"

    // stroke 3, round cap, graphite. Each 25 long. i = 0 at 12 o'clock, clockwise. Drawn hub → rim.
    val SPOKES = listOf(
        "M46 47 L46 22", "M50.95 49.05 L68.63 31.37", "M53 54 L78 54", "M50.95 58.95 L68.63 76.63",
        "M46 61 L46 86", "M41.05 58.95 L23.37 76.63", "M39 54 L14 54", "M41.05 49.05 L23.37 31.37",
    )
    // stroke 3, round cap, graphite. Each 18.77 long. Bow toward the hub. i = 0 starts at 12 o'clock.
    val STRANDS = listOf(
        "M46 32 Q50.97 41.99 61.56 38.44", "M61.56 38.44 Q58.01 49.03 68 54",
        "M68 54 Q58.01 58.97 61.56 69.56", "M61.56 69.56 Q50.97 66.01 46 76",
        "M46 76 Q41.03 66.01 30.44 69.56", "M30.44 69.56 Q33.99 58.97 24 54",
        "M24 54 Q33.99 49.03 30.44 38.44", "M30.44 38.44 Q41.03 41.99 46 32",
    )
    // Red arc: drawArc on the ring's bounds (14,22)-(78,86), stroke 4, round cap.
    // Resting state: startAngle -90°, sweep 90° (12 → 3 o'clock).
    // Glow: same arc, stroke 9, red at glow alpha (0.35 dark / 0.20 light), drawn UNDER the ring.

    // Glints: stroke 4, round cap, red. 6 o'clock (170°–190°) and 9 o'clock (260°–280°).
    val GLINTS = listOf("M51.56 85.51 A32 32 0 0 1 40.44 85.51", "M14.49 59.56 A32 32 0 0 1 14.49 48.44")

    // Streaks: filled red triangles. j = 0 lower (65°), 1 middle (45°), 2 upper (25°).
    // Axis direction (0.766, -0.643) = -40°. Base = pivot for the flick scale.
    val STREAKS = listOf(
        "M80.63 39.67 L91.02 28.72 L78.44 37.06 Z" to Offset(79.53f, 38.36f),
        "M73.26 29.14 L87.48 14.98 L71.07 26.53 Z" to Offset(72.16f, 27.84f),
        "M62.73 21.77 L73.13 10.82 L60.54 19.16 Z" to Offset(61.64f, 20.47f),
    )
}
```

Draw order (back → front): glow arc, ring, spokes, strands, hub, red arc, glints, streaks.

Trim ("stroke progress" p): `PathMeasure().apply { setPath(path, false) }` then
`getSegment(0f, length * p, dst, true)`; skip drawing when p == 0 (round caps would leave a dot).
Ring progress can use `drawArc(startAngle = -90f, sweepAngle = 360f * p)` instead.

---

## 6. Animation architecture

- **One clock per phase**: `val clock = remember { Animatable(0f) }`, animated linearly in ms:
  `clock.animateTo(endMs, tween(endMs.toInt(), easing = LinearEasing))`. Every property is a pure
  function of `clock.value` via:

```kotlin
fun seg(t: Float, start: Float, dur: Float, easing: Easing = LinearEasing): Float =
    easing.transform(((t - start) / dur).coerceIn(0f, 1f))

val IN_OUT    = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
val STANDARD  = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EMPH_DEC  = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val OVERSHOOT = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)   // = Compose EaseOutBack
val ACCEL     = CubicBezierEasing(0.3f, 0f, 1f, 1f)
```

- Read `clock.value` only inside draw/`graphicsLayer {}` lambdas so frames don't recompose.
- **Overlay emblem**: one `Emblem` composable in a `Box` layer above both phases, positioned with
  `Modifier.offset { }` + `graphicsLayer { scaleX/scaleY/alpha }`. Its rest positions come from the
  two measured slots (`onGloballyPositioned { it.boundsInRoot() }` on the 120 dp intro slot and the
  40 dp header slot). Read the header slot's bounds live every frame so it stays correct if the
  user scrolls. Base drawing size: 168 dp in INTRO (scale 1 → 0.714 to settle at 120 dp); in ABOUT
  it flies from the 120 dp slot to the 40 dp slot.
- After screen 2's entrance finishes, the overlay may stay (it tracks the slot) or swap to drawing
  inside the header slot — either is fine, but there must be no visible jump.
- **Reduced motion** = `Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f`.
  With the system scale at 0, Compose finishes tweens instantly, so run the reduced-motion fades
  inside `withContext(object : MotionDurationScale { override val scaleFactor = 1f }) { ... }`.
- **Skip** cancels the clock job and runs a 150 ms exit (§7), then switches phase.
- Rotation (config change): keep `phase` in `rememberSaveable`; on recreation mid-intro, show the
  current phase's final frame (no restart).

---

## 7. Timeline — Screen 1 (INTRO), 0 → 2440 ms

Emblem units are viewBox units (at 168 dp, 1 unit = 1.68 dp). Times absolute from first frame.
"Kick"/"settle" translations are dp, applied to the overlay emblem.

| Element | Property | Start ms | Dur ms | Easing | From → to |
|---|---|---|---|---|---|
| Outer ring | stroke progress, cw from 12 o'clock | 0 | 450 | IN_OUT | 0 → 1 |
| Hub ring | scale (pivot 46,54) | 80 | 260 | OVERSHOOT | 0 → 1 |
| Hub ring | alpha | 80 | 100 | linear | 0 → 1 |
| Spoke i (0..7) | stroke progress hub→rim | 150 + 25·i | 200 | STANDARD | 0 → 1 |
| Strand i (0..7) | stroke progress | 280 + 25·i | 200 | STANDARD | 0 → 1 |
| Red arc head | sweep end angle from 12 o'clock | 620 | 520 | IN_OUT | 0° → 450° |
| Red arc tail | sweep start angle | 700 | 440 | IN_OUT | 0° → 360° |
| Glow arc | alpha (follows arc geometry) | 620 | 140 | STANDARD | 0 → glow alpha |
| Glint 6 o'clock | alpha | 860 | 180 | STANDARD | 0 → 1 |
| Glint 9 o'clock | alpha | 910 | 180 | STANDARD | 0 → 1 |
| Emblem | translation (kick) | 1100 | 160 | STANDARD | (0,0) → (+4,−10) dp |
| Emblem | scale (kick) | 1100 | 160 | STANDARD | 1 → 1.03 |
| Streak j (0..2) | scale along −40° axis, pivot = base | 1120 + 40·j | 200 | STANDARD | 0 → 1 |
| Streak j | translation along axis | 1120 + 40·j | 200 | STANDARD | 0 → 3 units (+2.3, −1.93) |
| Streak j | alpha | 1120 + 40·j | 60 | linear | 0 → 1 |
| Emblem | scale (settle) | 1260 | 460 | EMPH_DEC | 1.03 → 0.714 (168 → 120 dp) |
| Emblem | translation (settle) | 1260 | 460 | EMPH_DEC | (+4,−10) → slot centre. Starts at screen centre (ΔY = screen centre − slot centre, measured; ≈ 79 dp at 360×800) |
| "WALL" | alpha / translationY | 1400 | 300 | EMPH_DEC | 0 → 1 / +16 → 0 dp |
| "CRAWL" pill | scale (pivot centre) | 1540 | 380 | OVERSHOOT | 0.5 → 1 (peaks ≈1.05) |
| "CRAWL" pill | alpha | 1540 | 100 | linear | 0 → 1 |
| Tagline line 1 | alpha / translationY | 1820 | 280 | STANDARD | 0 → 1 / +8 → 0 dp |
| Tagline line 2 | alpha / translationY | 1900 | 280 | STANDARD | 0 → 1 / +8 → 0 dp |
| Hold | — | 2180 | 80 | — | — |
| Wordmark, tagline, Skip | alpha | 2260 | 180 | ACCEL | 1 → 0 |
| **Emblem** | — | — | — | — | **Stays fully visible** (shared into screen 2) |
| Phase → ABOUT | — | 2440 | — | — | — |

Energy arc drawing: `drawArc(startAngle = -90f + tail, sweepAngle = head - tail, useCenter = false)`.
Final resting arc = 12 → 3 o'clock.

Before 1260 ms the emblem is centered on the **screen** centre; the settle moves it to the slot.

**On a real first launch the intro clock starts at 1260 ms, not 0.** The launch splash (`docs/launch-animation.md`) has already
drawn the emblem, run the red lap and flicked the streaks, and it ends with the emblem at screen
centre at 168 dp — exactly the intro's state at 1260 ms. So the visible intro after the splash is
1260 → 2440 ms (1.18 s). Use the splash icon view's on-screen bounds (from the exit listener) as the
emblem's start rect so there is no jump. The 0–1260 ms rows above remain valid for previews/tests
and as the fallback if no splash icon bounds are available.

**Skip (tap at time t):** freeze all element values at t; wordmark, tagline, Skip **and emblem**
alpha current → 0 over 150 ms, ACCEL; then phase → ABOUT (with "arrived via Skip", §8).

**Reduced motion:** all elements at final values (arc resting, streaks extended, emblem at slot,
120 dp). Whole intro alpha 0 → 1 over 300 ms linear; hold; at 1700 ms everything (emblem included)
alpha 1 → 0 over 180 ms ACCEL; phase → ABOUT at 1880 ms.

---

## 8. Timeline — Screen 2 (ABOUT), 0 → 3000 ms

**Total: 3000 ms real time.** These are real milliseconds: remove `WelcomeMotion.ABOUT_PACE` (or set it to 1f). The durations were lengthened rather than uniformly slowed, so the overshoot pops stay snappy.

| Element | Property | Start ms | Dur ms | Easing | From → to |
|---|---|---|---|---|---|
| Emblem | position + size | 0 | 900 | EMPH_DEC | intro slot centre, 120 dp → header slot centre, 40 dp |
| Red arc | one lap: tail 0 → 360°, head 90° → 450° | 150 | 900 | IN_OUT | rests at 12 → 3 o'clock (glow follows) |
| Headline | alpha / translationY | 450 | 600 | EMPH_DEC | 0 → 1 / +16 → 0 dp |
| Builder card | alpha / translationY | 650 | 600 | EMPH_DEC | 0 → 1 / +16 → 0 dp |
| Input tile k (0..2) | scale (centre) | 850 + 120·k | 600 | OVERSHOOT | 0.6 → 1 |
| Input tile k | alpha | 850 + 120·k | 200 | linear | 0 → 1 |
| Connector k (0..2) | stroke progress, top → converge | 1250 + 60·k | 500 | STANDARD | 0 → 1 |
| Output tile | scale (centre) | 1650 | 650 | OVERSHOOT | 0.85 → 1 |
| Output tile | alpha | 1650 | 200 | linear | 0 → 1 |
| Output glow | alpha | 1650 | 240 | STANDARD | 0 → 0.45 |
| Output glow | alpha | 1950 | 600 | STANDARD | 0.45 → 0 |
| Feature row i (0..1) | alpha / translationY | 1500 + 140·i | 550 | EMPH_DEC | 0 → 1 / +12 → 0 dp |
| Feature icon tile i | scale | 1500 + 140·i | 600 | OVERSHOOT | 0.6 → 1 |
| Feature icon tile i | alpha | 1500 + 140·i | 200 | linear | 0 → 1 |
| Privacy card | alpha / translationY | 1950 | 600 | EMPH_DEC | 0 → 1 / +16 → 0 dp |
| Chip j (0..2) | scale / alpha | 2150 + 100·j | 450 / 150 | OVERSHOOT / linear | 0.8 → 1 / 0 → 1 |
| Footer (Get started) | alpha / translationY | 2350 | 650 | EMPH_DEC | 0 → 1 / +24 → 0 dp (ends at 3000) |

- **Arrived via Skip:** same table, plus emblem alpha 0 → 1 over 0–250 ms, linear.
- **Reduced motion:** everything at rest, emblem already at the header slot; whole screen alpha 0 → 1 over 200 ms linear.
- Get started is enabled from frame 0 (tapping mid-entrance is fine).

---

## 9. Accessibility

- Emblem: decorative, no semantics.
- Intro: wordmark row → `contentDescription = "WallCrawl"`; taglines are normal Text; Skip is a real button.
- Screen 2: headline `heading()`; chips are text, not buttons; icons `contentDescription = null`.
- Text at 200% font scale: nothing truncates or overlaps at 320×640 (screen 2 scrolls; divider appears; button stays pinned).
- Logo (emblem + wordmark) uses fixed dp sizes and does not scale with font size; everything else uses sp.

---

## 10. Acceptance criteria

1. Fresh install (no profile, `introSeen` unset): intro plays → screen 2 → Get started → onboarding step 1. Total intro 2.44 s.
2. Relaunch before finishing onboarding (after Get started): goes straight to onboarding. Killing the app during intro or screen 2 shows the intro again.
3. Skip at any point during the intro goes to screen 2 within 150 ms. Back during the intro does the same.
4. Natural finish: the emblem never disappears between the intro and screen 2's header.
5. Animator duration scale 0: intro fades in (300 ms, visibly — not instant), holds, fades out; screen 2 fades in over 200 ms.
6. Light and dark themes both match §2; no background flash into onboarding.
7. English and Spanish strings present in both `values` and `values-es`.
8. 320×640 at 200% font: intro tagline fully visible; screen 2 scrollable with pinned button.
9. Existing flows unchanged: onboarded users go to Today; delete-all-data and restore still land on onboarding (no intro).

## 11. Tests

- Unit (JVM): `seg()` + the timeline functions — e.g. ring progress 0 at 0 ms, 1 at 450 ms; settle scale 0.714 at ≥1720 ms; pill scale > 1 near 1700 ms (overshoot); screen-2 emblem at header bounds at ≥900 ms.
- Compose UI tests (androidTest): Skip → screen 2 visible; Get started → `introSeen` true and ONBOARDING route; Back during intro → screen 2; reduced-motion path reaches screen 2; start destination is ONBOARDING when `introSeen` is true. Use `mainClock.autoAdvance = false` / `advanceTimeBy` for deterministic timing.
- Run the existing test suite; nothing else should change.

