# Launch animation (every app start) — implementation spec

Self-contained brief for the animated logo shown each time WallCrawl starts: the emblem draws
itself on the system splash screen, then zooms through its hub to reveal the app.
Separate from, but connected to, the first-launch intro (`docs/first-launch-intro.md`).

Design source (visual reference only): https://claude.ai/artifact/AQWPWjQsSUV2a22iz14bgh
— board "Every launch · splash animation".

Emblem geometry, colours and easing names are shared with `docs/first-launch-intro.md` §2 and §5.


Shown on **every** cold or warm start (hot starts show no splash). Built on the system splash
screen — Android 12+ always shows one, so this replaces the default launcher-icon splash rather
than adding a screen. It shows from the first frame; it can keep the splash up to ~880 ms on a fast start, plus the 600 ms exit.

### Assets (ready to use)

Saved in `docs/launch-assets/`; move them into `app/src/main/res/`:

| File | Goes to | What |
|---|---|---|
| `avd_splash_emblem.xml` | `res/drawable-v31/` | AnimatedVectorDrawable, 880 ms: draw → red lap → streak flick |
| `splash_emblem_static.xml` | `res/drawable/` | Same emblem at rest, for API 26–30 (core-splashscreen shows a static icon there) |

Both draw the 100 × 100 emblem at 168 dp centred in the 288 dp splash icon canvas (group translate 60, scale 1.68).
Furthest point is 86 dp from centre, inside the 192 dp icon mask. 168 dp at screen centre is
deliberately the first-launch intro's 1260 ms state (`docs/first-launch-intro.md` §7).

Add colors (`values/colors.xml` and `values-night/colors.xml`):

```xml
<!-- values/colors.xml (light) -->
<color name="splash_background">#F8F7F5</color>
<color name="splash_graphite">#54545C</color>
<color name="splash_red">#DE2045</color>
<color name="splash_glow">#33DE2045</color>   <!-- red at 0.20 -->

<!-- values-night/colors.xml (dark) -->
<color name="splash_background">#101113</color>
<color name="splash_graphite">#54545C</color>
<color name="splash_red">#DE2045</color>
<color name="splash_glow">#59DE2045</color>   <!-- red at 0.35 -->
```

### Wiring

1. Add `androidx.core:core-splashscreen` (latest stable) to `gradle/libs.versions.toml` and the app module.
2. Themes (`values/themes.xml`):
   ```xml
   <style name="Theme.WallCrawl.Starting" parent="Theme.SplashScreen">
       <item name="windowSplashScreenBackground">@color/splash_background</item>
       <item name="windowSplashScreenAnimatedIcon">@drawable/splash_emblem_static</item>
       <item name="postSplashScreenTheme">@style/Theme.WallCrawl</item>
   </style>
   ```
   and `values-v31/themes.xml` overrides only the icon:
   `windowSplashScreenAnimatedIcon = @drawable/avd_splash_emblem`,
   `windowSplashScreenAnimationDuration = 880` (used below API 33; 33+ reads the AVD).
   No icon background color, no branding image.
3. Manifest: the `MainActivity` `<activity>` uses `@style/Theme.WallCrawl.Starting`.
4. `MainActivity.onCreate`: call `installSplashScreen()` **before** `super.onCreate()`.
   - `setKeepOnScreenCondition { profileFlow.value == null }` — hoist the profile into a `StateFlow`
     (e.g. `stateIn` in the activity's lifecycle scope) so the condition can read it; this removes the
     `CircularProgressIndicator` flash in `WallCrawlApp` (keep that branch as a fallback).
   - `setOnExitAnimationListener { provider -> ... }`:
     - Wait for the icon animation to finish: `remaining = provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis - SystemClock.uptimeMillis()`; if > 0, post the exit after `remaining`.
     - **Every launch once onboarded:** zoom-through exit below, then `provider.remove()`.
     - **Every launch before onboarding is completed (WELCOME):** no exit animation. Record `provider.iconView` bounds on screen, hand them to `WelcomeScreen` as the emblem start rect, start the intro clock at 1260 ms, then `provider.remove()` once the first intro frame is drawn.
5. Theme mismatch: the splash follows the **system** night mode, but the app follows the in-app
   `ThemePreference`. On API 31+, call `UiModeManager.setApplicationNightMode(...)` whenever the
   preference changes (MODE_NIGHT_YES / NO / AUTO for SYSTEM) so the next splash matches. On 26–30
   there is no equivalent; the first frame may differ for users who override the system theme.

### Timeline (AVD, 0 → 880 ms)

| Element | Property | Start ms | Dur ms | Easing | From → to |
|---|---|---|---|---|---|
| Outer ring | trimPathEnd | 0 | 300 | (0.65, 0, 0.35, 1) | 0 → 1 |
| Hub ring | scaleX/Y (pivot 46, 54) | 50 | 180 | (0.34, 1.56, 0.64, 1) | 0 → 1 |
| Spoke i | trimPathEnd | 100 + 15·i | 150 | (0.2, 0, 0, 1) | 0 → 1 |
| Strand i | trimPathEnd | 190 + 15·i | 150 | (0.2, 0, 0, 1) | 0 → 1 |
| Red arc + glow | trimPathEnd | 420 | 100 | (0.2, 0, 0, 1) | 0 → 0.25 |
| Red arc + glow | trimPathOffset (one lap) | 420 | 400 | (0.65, 0, 0.35, 1) | 0 → 1 (rests 12 → 3 o'clock) |
| Glint i | strokeAlpha | 560 + 40·i | 150 | (0.2, 0, 0, 1) | 0 → 1 |
| Streak j | scaleX inside ±40° rotation groups (pivot = base) | 640 + 40·j | 160 | (0.2, 0, 0, 1) | 0 → 1 |
| Streak j | fillAlpha | 640 + 40·j | 50 | linear | 0 → 1 |

### Exit: zoom-through (every launch, once the splash may leave)

The splash may leave at `max(profile ready, AVD end)`. Times below are relative to that moment.
Modelled on X's (formerly Twitter's) zoom-through launch: the logo makes a small anticipation dip,
then grows past the edges of the screen while the app is revealed underneath. Here the emblem zooms
**through its hub ring**, so it reads as flying through the centre of the web.

| Element | Property | Start ms | Dur ms | Easing | From → to |
|---|---|---|---|---|---|
| `provider.iconView` | scaleX/Y, pivot = hub | 0 | 120 | (0.4, 0, 0.6, 1) | 1 → 0.9 (anticipation) |
| `provider.iconView` | scaleX/Y, pivot = hub | 120 | 400 | (0.7, 0, 0.84, 0) | 0.9 → 10 |
| `provider.iconView` | alpha | 300 | 220 | linear | 1 → 0 |
| `provider.view` (splash background) | alpha | 200 | 220 | (0.3, 0, 1, 1) | 1 → 0 |
| App content (`android.R.id.content`) | alpha | 200 | 180 | linear | 0 → 1 |
| App content | scaleX/Y (centre) | 200 | 400 | (0.05, 0.7, 0.1, 1) | 1.06 → 1 |
| Done: `provider.remove()` | — | 600 | — | — | — |

- **Hub pivot:** `iconView.pivotX = iconView.width * 0.4767f`, `pivotY = iconView.height * 0.5233f`
  (hub centre (46, 54) in the 100-unit emblem → (137.28, 150.72) in the 288-unit icon canvas).
  If `iconView` is not the full 288 dp canvas on a given API level, compute the pivot from its
  actual bounds the same way.
- Build it as one `AnimatorSet` of `ObjectAnimator`s with `PathInterpolator`s (values above); the
  scale is two sequential animators (dip, then zoom).
- The content view must start at alpha 0 and scale 1.06 *before* the exit begins (set them in
  `setOnExitAnimationListener` before starting the set), and be reset to 1/1 in `onAnimationEnd`.
- Scale 10 is enough to clear a 360 × 800 dp screen from a 168 dp emblem; the emblem is fully
  transparent before it fills the screen, so larger screens need no different value.
- **First launch:** no zoom. The emblem stays and is handed to the intro (see Wiring, step 4).
- **Animations off:** all animators complete instantly → the app simply appears. Acceptable.

### Behaviour

- Android 8–11: static emblem; splash leaves as soon as the profile is ready, with the same zoom-through exit.
- Animator duration scale 0: the system shows the AVD's end state and exit animators complete instantly — acceptable; no workaround needed.
- Total on a fast device: ~880 ms icon animation + 600 ms exit, on cold/warm starts only (hot starts show no splash).

### Acceptance

1. Cold start (onboarded): emblem draws, red lap, streaks flick, then dips and zooms through its hub while Today fades and settles in. No spinner flash.
2. Cold start (first launch): splash emblem continues into the intro without a jump or flash; intro wordmark appears ≈1.18 s after the splash ends.
3. Android 8–11 emulator: static emblem, correct background per system theme.
4. Light and dark system themes both correct; with API 31+ and an in-app override, the next launch's splash matches the override.
