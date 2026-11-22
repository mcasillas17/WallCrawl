# Welcome screens v2: intro + icon-led screen 2. Implementation spec

The complete spec for both first-launch screens, version 2. It is self-contained: flow, layout,
colours, copy (EN + ES), emblem geometry, both timelines, accessibility, tests and acceptance.

**Supersedes** `docs/first-launch-intro.md` §3, §4, §7 and §8, and all of
`docs/welcome-workout-builder.md` (that version was never built; ignore it). Where this file and
those disagree, this file wins. The every-launch splash stays in `docs/launch-animation.md`.

Design reference (visual only): https://claude.ai/artifact/AQWPWjQsSUV2a22iz14bgh, boards
"Intro prototype" and "Screen 2 · icon version".

---

## 0. What changes from the code on `main`

The v1 welcome flow is already implemented in `app/src/main/java/wallcrawl/elopenmike/com/feature/welcome/`
(`WelcomeScreen.kt`, `WelcomeMotion.kt`, `WelcomeEmblem.kt`, `SplashHandoff.kt`) with tests in
`app/src/test/.../feature/welcome/WelcomeMotionTest.kt` and `app/src/androidTest/.../feature/welcome/WelcomeScreenTest.kt`.
Keep the architecture. Change:

| Area | Now (v1) | v2 |
|---|---|---|
| Intro tagline line 2 | "All on your phone." | "Workouts built from your goals, progress and equipment." (§4) |
| Intro length | 2440 ms, 80 ms hold | **3500 ms**: slower tagline and a **580 ms reading hold** (§6) |
| Intro, reduced motion | ends at 1880 ms | ends at **3500 ms** (§6) |
| Screen 2 content | eyebrow, headline, 3 feature rows, privacy card | headline, **flow diagram**, **4 privacy badges** (§5) |
| Screen 2 length | 1280 ms × `ABOUT_PACE` 1.8 | **3000 ms real time**. Delete `ABOUT_PACE` (§7). |
| Strings | see §8 | add, change and delete keys as listed in §8 |

Skip, Back, TalkBack start, the splash handoff and the shared overlay
emblem are unchanged (§3). **Navigation changed:** the welcome screens now open on every launch
until onboarding is completed; the `introSeen` flag is gone (§1).

---

## 1. Flow

```
every cold/warm start ─► system splash (docs/launch-animation.md), held until the profile has loaded
  ├─ !onboardingCompleted ─► WELCOME (phase INTRO → phase ABOUT) ─► ONBOARDING
  └─  onboardingCompleted ─► TODAY
```

- One route (`AppRoutes.WELCOME`), one `WelcomeScreen`, internal `phase` (`INTRO`, `ABOUT`), one overlay emblem.
- **First launch from the splash:** the intro clock starts at `SPLASH_HANDOFF_MS` = 1260 ms, with the emblem starting at the splash icon's on-screen bounds.
- **Skip** (intro only, also Back during intro): freeze element values, fade wordmark, tagline, Skip **and emblem** over 150 ms (ACCEL), then phase → ABOUT with "arrived via Skip".
- **Get started:** navigate to ONBOARDING, popping WELCOME. This lasts only for the running session
  (saved instance state, so rotation keeps the user on onboarding). Quitting before finishing
  onboarding shows the welcome screens again on the next launch. Deleting all data mid-session
  still goes straight to onboarding (`restartAtOnboarding()`); the next launch shows welcome.
  The old `first_launch`/`intro_seen` preference is ignored.
- **Back on ABOUT:** default (exits).
- **TalkBack on:** start directly in ABOUT with the reduced-motion entrance.
- Both phases paint `colorScheme.background` and follow `ThemePreference` (light and dark).

---

## 2. Colours and type (existing tokens only)

| Use | Dark | Light |
|---|---|---|
| Background | `ObsidianBlack` #101113 | `LightBackground` #F8F7F5 |
| Node / badge fill | `GraphiteSurfaceElevated` #202125 | `LightSurfaceCard` #F5F4F2 |
| Node / badge border (1 dp) | `GraphiteDivider` #313238 | `LightDivider` #DEDCDF |
| Primary text | `TextPrimary` #F5F4F2 | `LightTextPrimary` #1B1B1F |
| Secondary text, Skip, badge labels and icons | `TextSecondary` #B7B6BB | `LightTextSecondary` #54545C |
| Flow icons (inputs, workout, log) | `SpiderRedVibrant` #FF6C87 | `SpiderRedPrimary` #DE2045 |
| Flow connectors and down arrow | #FF6C87 | `SpiderRedDeep` #602334 |
| Workout ring, glow, loop arrow, Progress ping | `SpiderRedPrimary` #DE2045 | same |
| Emblem graphite | `TextDisabled` #54545C | same |
| Emblem red, wordmark pill | #DE2045 | same |
| Emblem arc glow alpha | 0.35 | 0.20 |
| Get started | `colorScheme.primary` / `onPrimary` (#FF6C87 / #101113) | (#DE2045 / #FFFFFF) |

Type: `WallCrawlTypography`. The wordmark uses Roboto Condensed Black (`res/font`), already bundled for v1.

Contrast: primary text 16–17:1; secondary 9.4:1 dark and 7.0:1 light; #FF6C87 on #101113 7.0:1.
Icons are decorative (3:1 for graphics is met by every pairing above).

---

## 3. Shared emblem (unchanged)

The geometry stays in `EmblemGeometry`/`WelcomeEmblem.kt` (100 × 100 viewBox, ring centre (46, 54)).
Draw order: glow arc, ring, spokes, strands, hub, red arc, glints, streaks. One overlay emblem
spans both phases. Its rest positions are the measured 120 dp intro slot and the 40 dp header slot
on screen 2. Read the header slot bounds every frame. Reduced motion detection, `MotionDurationScale`
override, `rememberSaveable` phase and the final-frame-on-recreation rule are all unchanged.

---

## 4. Screen 1: intro (final frame)

Centred `Column`, horizontal padding 24 dp, vertically centred.

| # | Element | Spec |
|---|---|---|
| 1 | Emblem slot | `Box(size = 120.dp)`, measured. The emblem is drawn by the overlay. |
| | Gap | 24 dp |
| 2 | Wordmark row | 44 dp tall, `spacedBy(8.dp)`, `clearAndSetSemantics { contentDescription = "WallCrawl" }`. "WALL": Roboto Condensed Black, 40 dp-equivalent (ignores font scale), +0.5 sp, onBackground. "CRAWL" pill: 44 dp high, 12 dp side padding, radius 8 dp, #DE2045, text #101113. |
| | Gap | 20 dp |
| 3 | Tagline line 1 | `titleMedium` 16/22 sp SemiBold, onBackground, centred. |
| 4 | Tagline line 2 | `bodyLarge` 16/24 sp, onSurfaceVariant, centred. **Wraps to 2 lines** at 360 dp. |
| 5 | Skip | `TextButton` top-end, 8 dp from top and end, min 48 × 48 dp, `labelLarge`, onSurfaceVariant, visible from frame 0. |

Block height at 100% ≈ 278 dp, so the top is ≈ 261 dp on 360 × 800. The emblem's settle target
moves accordingly: **measure the slot, never hard-code ΔY** (≈ 79 dp at 360 × 800). At 200% the
tagline can reach 5–6 lines and must not clip at 320 × 640. The wordmark and emblem do not scale with font size.

---

## 5. Screen 2: icon-led "what WallCrawl does"

### 5.1 Structure

```
Column(fillMaxSize)
├─ scroll area (weight 1, verticalScroll), padding top 24, sides 24, bottom 16
│   ├─ header: emblem slot 40 dp ─ 16 dp ─ headline
│   ├─ 32 dp
│   ├─ FLOW DIAGRAM (custom Layout, §5.3)
│   └─ PRIVACY BADGES, bottom-aligned (§5.4)
└─ footer (outside scroll): padding top 12, sides 24, bottom 24 — Get started
```

- Headline: 28/34 sp, `FontWeight.Black`, −0.5 sp, onBackground, `heading()`. "Built for you."
- Footer: M3 `Button`, `fillMaxWidth`, min 56 dp, pill, 16 sp Bold, +0.3 sp. A 1 dp `outlineVariant`
  divider shows above it only when the scroll area can scroll.
- **Bottom-aligned badges:** when there's room, the badge row sits at the bottom of the scroll
  viewport. Otherwise it follows the diagram with a 28 dp gap. Implement with `BoxWithConstraints`
  for the viewport height and a small custom `Layout` that places header and diagram at the top and
  the badges at `y = max(diagramBottom + 28.dp, viewportHeight − badgesHeight)`. A `Spacer(weight)`
  does not work inside `verticalScroll`.

### 5.2 The diagram, in words

```
   (◎)          (═╪═)          (↗) ◄─────┐
  Goals       Equipment      Progress    │  red loop arrow
     ╲            │            ╱         │
      ╲           │           ╱          │
        ┌──────────────────┐             │
        │   ◯  [clipboard] │  red ring   │
        └──────────────────┘             │
            Your workout                 │
                 │                       │
                 ▼                       │
               (✓) ──────────────────────┘
              Log it
```

Goals, equipment and progress build your workout. You log it. What you log feeds progress, and the
loop closes. The input order is fixed (Goals, Equipment, Progress) so the loop arrow can reach Progress on the right.

### 5.3 Diagram geometry

A custom `Layout` (full width `W`, height computed). **X is a fraction of `W`; Y is in dp.** Measure
the labels first so the Y positions follow the real label heights (font scale and wrapping).

Horizontal anchors: left `W/6`, centre `W/2`, right `5W/6` (the 312-unit design grid: 52, 156, 260).

| Element | Size | Position (centre x, top y) |
|---|---|---|
| Input nodes ×3 | circle 56 dp, fill + 1 dp border, icon 24 dp in flow-icon colour | x = W/6, W/2, 5W/6; y = 0 |
| Input labels ×3 | width 112 dp, centred, `labelMedium` 12/16 sp SemiBold, onBackground, may wrap | top = 62 |
| `inLabH` | max measured input label height | — |
| `conStart` | `62 + inLabH + 4` | — |
| Workout node | circle 88 dp, fill, **no border**; red ring (below); icon 36 dp | x = W/2; top `centerTop = conStart + 24` |
| Workout label | width 140 dp, `titleSmall` 14/18 sp **Bold**, onBackground | top `centerTop + 94` |
| `arrowStart` | `centerTop + 94 + cLabH + 6` (cLabH = measured) | — |
| Down arrow | 1.5 dp line, length 22 dp, + filled head (7 dp, half-angle 0.55 rad) | x = W/2, from `arrowStart` to `arrowStart + 22` |
| Log node | circle 56 dp, fill + 1 dp border, icon 24 dp | x = W/2; top `logTop = arrowStart + 24` |
| Log label | width 112 dp, `labelMedium` 12/16 sp SemiBold | top `logTop + 62` |
| Diagram height | `logTop + 62 + logLabH` | ≈ 326 dp at 100% |

**Connectors** (Canvas, 1.5 dp, round cap, connector colour), from `conStart` to `centerTop`:
- left: `M(W/6, conStart) C(W/6, conStart+16) (W/2, centerTop−16) (W/2, centerTop)`
- centre: straight `M(W/2, conStart) L(W/2, centerTop)`
- right: mirror of left from `5W/6`

**Workout ring:** stroke 2 dp, #DE2045, radius 43 dp on the 88 dp node, drawn clockwise from 12 o'clock
(`drawArc(startAngle = −90f, sweepAngle = 360f * p)`). **Glow:** `drawBehind` circle radius 50 dp
(node + 6 dp), #DE2045 at `glowAlpha`, 0 at rest.

**Loop arrow** (Canvas, 2 dp, round cap, #DE2045), with x as a fraction of W and y in dp:
`M(184/312·W, logTop+28) C(300/312·W, logTop+28) (312/312·W, conStart+28) (291/312·W, 40)`,
plus a filled head (9 dp) at (291/312·W, 40) pointing along the last tangent (from the second
control point to the end). It ends just right of the Progress node. At 320 dp width the curve stays
inside the content box.

**Progress ping:** a 2 dp #DE2045 ring the size of the Progress node (56 dp), centred on it,
scaled and faded per §7.

Icons (material-icons-extended, all decorative, `contentDescription = null`):

| Node | Icon |
|---|---|
| Goals | `Icons.Outlined.TrackChanges` |
| Equipment | `Icons.Outlined.FitnessCenter` |
| Progress | `Icons.AutoMirrored.Outlined.TrendingUp` |
| Your workout | `Icons.AutoMirrored.Outlined.Assignment` |
| Log it | `Icons.Outlined.TaskAlt` |

**Semantics:** the whole diagram is one node:
`Modifier.clearAndSetSemantics { contentDescription = stringResource(R.string.welcome_flow_a11y) }`.
TalkBack reads the full sentence (§8) even though the screen shows only one-word labels.

### 5.4 Privacy badges

`Row` of 4 equal columns (`weight(1f)`), gap 8 dp, `semantics(mergeDescendants = true)` per badge.
Each: 44 dp circle (fill + 1 dp border) with a 22 dp icon in secondary colour, 6 dp gap, label
`labelMedium` 12/16 sp SemiBold, onSurfaceVariant, centred, may wrap.

| Badge | Icon |
|---|---|
| No account | `Icons.Outlined.PersonOff` |
| No ads | `Icons.Outlined.Block` |
| No tracking | `Icons.Outlined.VisibilityOff` |
| Offline | `Icons.Outlined.WifiOff` |

Not clickable, no ripple.

### 5.5 Fit

At 360 × 800 and 100% everything fits with no scroll. The badges sit about 80 dp below the diagram.
At 320 × 640 and 200%, labels wrap, the diagram grows (all Y values derive from measured labels),
the screen scrolls, the divider shows, and the button stays pinned. Nothing may clip or overlap.

As built: no label breaks mid-word. Input labels are `min(112 dp, W/3)` wide so neighbours can't
overlap. When the widest word in a group (the input and Log it labels, the workout label, the
badge labels) can't fit its column on one line, the whole group's text shrinks just enough to fit.
It is unchanged at normal sizes.

---

## 6. Timeline: screen 1 (INTRO), 0 → 3500 ms

Easings: `IN_OUT` (0.65, 0, 0.35, 1) · `STANDARD` (0.2, 0, 0, 1) · `EMPH_DEC` (0.05, 0.7, 0.1, 1) ·
`OVERSHOOT` (0.34, 1.56, 0.64, 1) · `ACCEL` (0.3, 0, 1, 1).

0–1720 ms is unchanged from v1. Rows marked **★** are new or changed.

| Element | Property | Start | Dur | Easing | From → to |
|---|---|---|---|---|---|
| Outer ring | stroke progress, cw from 12 o'clock | 0 | 450 | IN_OUT | 0 → 1 |
| Hub ring | scale (pivot 46, 54) / alpha | 80 | 260 / 100 | OVERSHOOT / linear | 0 → 1 |
| Spoke i | stroke progress | 150 + 25·i | 200 | STANDARD | 0 → 1 |
| Strand i | stroke progress | 280 + 25·i | 200 | STANDARD | 0 → 1 |
| Red arc head / tail | sweep angles | 620 / 700 | 520 / 440 | IN_OUT | 0→450° / 0→360° |
| Arc glow | alpha | 620 | 140 | STANDARD | 0 → glow alpha |
| Glints 6 / 9 o'clock | alpha | 860 / 910 | 180 | STANDARD | 0 → 1 |
| Emblem kick | translation / scale | 1100 | 160 | STANDARD | (0,0)→(+4,−10) dp / 1→1.03 |
| Streak j | axis scale / translate / alpha | 1120 + 40·j | 200 / 200 / 60 | STANDARD / STANDARD / linear | 0→1 / 0→3 u / 0→1 |
| Emblem settle | scale / translation | 1260 | 460 | EMPH_DEC | 1.03→0.714 / → measured slot centre |
| "WALL" | alpha / Y | 1400 | 300 | EMPH_DEC | 0→1 / +16→0 dp |
| "CRAWL" pill | scale / alpha | 1540 | 380 / 100 | OVERSHOOT / linear | 0.5→1 / 0→1 |
| ★ Tagline line 1 | alpha / Y | 1820 | **500** | STANDARD | 0→1 / +8→0 dp |
| ★ Tagline line 2 | alpha / Y | **2120** | **500** | STANDARD | 0→1 / +8→0 dp |
| ★ **Hold (reading time)** | nothing moves | **2620** | **580** | — | final frame fully visible |
| ★ Wordmark, tagline, Skip | alpha | **3200** | **300** | ACCEL | 1 → 0 |
| Emblem | stays fully visible (shared into screen 2) | — | — | — | — |
| ★ Phase → ABOUT | | **3500** | | | |

- First launch from the splash: clock starts at 1260, so the visible intro is **3.44 s**.
- Skip at time t: 150 ms ACCEL exit of everything including the emblem, then ABOUT (arrived via Skip).
- ★ **Reduced motion:** final frame (emblem at slot, 120 dp, arc resting, streaks out), alpha 0→1
  over 300 ms linear, hold, at **3200** ms everything (emblem included) 1→0 over **300** ms ACCEL,
  phase → ABOUT at **3500** ms.

`WelcomeMotion` changes: `INTRO_END = 3500f`, `INTRO_SETTLED = 2620f`,
`tagline1 = seg(t, 1820, 500, STANDARD)`, `tagline2 = seg(t, 2120, 500, STANDARD)`,
`introChrome = 1 − seg(t, 3200, 300, ACCEL)`, `REDUCED_INTRO_END = 3500f`,
`reducedIntroAlpha = seg(t, 0, 300) * (1 − seg(t, 3200, 300, ACCEL))`.

---

## 7. Timeline: screen 2 (ABOUT), 0 → 3000 ms, real time

**No pace multiplier.** Delete `ABOUT_PACE`. `ABOUT_END = 3000f`.

| Element | Property | Start | Dur | Easing | From → to |
|---|---|---|---|---|---|
| Emblem | position + size | 0 | 900 | EMPH_DEC | intro slot centre, 120 dp → header slot centre, 40 dp |
| Red arc | one lap: tail 0→360°, head 90→450° | 150 | 900 | IN_OUT | rests 12 → 3 o'clock |
| Headline | alpha / Y | 450 | 600 | EMPH_DEC | 0→1 / +16→0 dp |
| Input node k (0..2) | scale / alpha (label follows alpha) | 700 + 120·k | 600 / 200 | OVERSHOOT / linear | 0.6→1 / 0→1 |
| Connector k | stroke progress | 1100 + 60·k | 450 | STANDARD | 0 → 1 |
| Workout node | scale / alpha (label follows alpha) | 1450 | 650 / 200 | OVERSHOOT / linear | 0.7→1 / 0→1 |
| Workout ring | stroke progress | 1450 | 550 | IN_OUT | 0 → 1 |
| Workout glow | alpha | 1450, then 1750 | 240, then 600 | STANDARD | 0 → 0.45 → 0 |
| Down arrow | stroke progress (head appears at p > 0.6) | 1850 | 300 | STANDARD | 0 → 1 |
| Log node | scale / alpha (label follows alpha) | 2000 | 550 / 200 | OVERSHOOT / linear | 0.6→1 / 0→1 |
| Loop arrow | stroke progress (head appears at p ≥ 0.97) | 2150 | 500 | IN_OUT | 0 → 1 |
| Progress ping | scale / alpha | 2650 | 350 | STANDARD | 1→1.5 / 0.7→0 |
| Badge j (0..3) | scale / alpha | 2300 + 80·j | 450 / 150 | OVERSHOOT / linear | 0.8→1 / 0→1 |
| Footer (Get started) | alpha / Y | 2350 | 650 | EMPH_DEC | 0→1 / +24→0 dp |

```kotlin
fun workoutGlow(t: Float) = 0.45f * seg(t, 1450f, 240f, STANDARD) * (1f - seg(t, 1750f, 600f, STANDARD))
fun ping(t: Float) = seg(t, 2650f, 350f, STANDARD)      // ring scale 1 + 0.5p, alpha 0.7(1 − p), hidden at p == 0
```

- Stroke progress: `PathMeasure.getSegment(0f, length * p, dst, true)`; skip drawing at p == 0.
- **Arrived via Skip:** same table, plus emblem alpha 0→1 over 0–250 ms, linear.
- **Reduced motion / TalkBack start:** everything at rest (lines drawn, glow 0, ping hidden), emblem in the header, whole screen alpha 0→1 over 200 ms linear.
- Get started is enabled from frame 0.

Delete the v1 functions `eyebrow`, `feature`, `tileScale`, `tileAlpha`, `card`, `chipScale` and
`chipAlpha`, and replace them with the rows above (`input`, `connector`, `workout*`, `down`, `log*`,
`loop`, `ping`, `badge*`).

---

## 8. Strings (`values/strings.xml` and `values-es/strings.xml`)

Follow `docs/localization.md`. Final list of `welcome_*` keys:

| Key | EN | ES | Status |
|---|---|---|---|
| `welcome_skip` | Skip | Omitir | keep |
| `welcome_tagline_line1` | Train. Log. Progress. | Entrena. Registra. Progresa. | keep |
| `welcome_tagline_line2` | Workouts built from your goals, progress and equipment. | Rutinas según tus metas, tu progreso y tu equipo. | **change** |
| `welcome_about_headline` | Built for you. | Hecho para ti. | **change** |
| `welcome_flow_goals` | Goals | Metas | add |
| `welcome_flow_equipment` | Equipment | Equipo | add |
| `welcome_flow_progress` | Progress | Progreso | add |
| `welcome_flow_workout` | Your workout | Tu rutina | add |
| `welcome_flow_log` | Log it | Regístrala | add |
| `welcome_flow_a11y` | WallCrawl builds your workout from your goals, equipment and progress. You log it, and what you log shapes the next one. | WallCrawl arma tu rutina con tus metas, tu equipo y tu progreso. La registras y lo que registras define la siguiente. | add |
| `welcome_privacy_no_account` | No account | Sin cuenta | keep |
| `welcome_privacy_no_ads` | No ads | Sin anuncios | keep |
| `welcome_privacy_no_tracking` | No tracking | Sin rastreo | keep |
| `welcome_privacy_offline` | Offline | Sin internet | add |
| `welcome_get_started` | Get started | Comenzar | keep |

**Delete** these: `welcome_about_eyebrow`, `welcome_feature_plan_title`, `welcome_feature_plan_body`,
`welcome_feature_log_title`, `welcome_feature_log_body`, `welcome_feature_progress_title`,
`welcome_feature_progress_body`, `welcome_privacy_title`, `welcome_privacy_body`.

The copy is grounded in the README and `docs/privacy.md`: rules-based planning from goals, equipment
and recorded history, no account, offline, no analytics upload. **No sparkle or "AI" icons or
wording.** The planner is rules-based.

---

## 9. Accessibility

- Emblem: decorative. Intro wordmark: "WallCrawl". Skip and Get started are real buttons.
- Screen 2 reading order: headline → diagram (one node, `welcome_flow_a11y`) → 4 badges (each merged: icon + label) → Get started.
- Logo (emblem, wordmark) is fixed dp. All text is sp and must work at 200% (§5.5).
- Reduced motion and TalkBack paths as in §6 and §7.

---

## 10. Tests

**`WelcomeMotionTest` (JVM)**, updated to:
- Intro: `INTRO_END == 3500`. Tagline 2 is 0 at 2120 and 1 at ≥ 2620. Chrome alpha is 1 throughout 2620–3200 (the hold), 0 at 3500. Reduced: alpha 1 at 1000, 0 at 3500.
- Screen 2: `ABOUT_END == 3000` and `ABOUT_PACE` is gone. Flight is complete at ≥ 900. Connector k is 1 at ≥ 1550 + 60·k. Workout glow is 0 at 0, peaks near 1750, and is 0 at ≥ 2350. Loop is 1 at ≥ 2650. Ping is 0 before 2650 and 1 at 3000. Footer is 1 at 3000.

**`WelcomeScreenTest` (instrumented)**:
- The diagram node's content description equals `welcome_flow_a11y`. The texts "Goals", "Equipment", "Progress", "Your workout" and "Log it" are displayed. The 4 badges are displayed.
- The removed texts ("What WallCrawl does", "Plan your week", "Private by design") do not exist.
- Existing flow tests still pass: Skip, Back, Get started → ONBOARDING, welcome again on relaunch until onboarding is done, reduced motion and TalkBack start.
- Use `mainClock.autoAdvance = false` and `advanceTimeBy` for the timing checks.

Run the full suite. Nothing outside `feature/welcome`, the strings and the tests should change.

---

## 11. Acceptance

1. Fresh install: splash → intro (from 1260 ms) → the tagline eases in and the finished frame holds about 1.8 s → screen 2 → Get started → onboarding step 1.
2. The emblem never disappears between the intro and screen 2's header on a natural finish, and it settles exactly in its slot (no jump) with the 2-line tagline at 360 × 800 and 320 × 640.
3. Screen 2 shows only the headline, the diagram, 4 badges and the button, with about 20 visible words. The animation reads in order: inputs → lines → workout (ring and glow) → arrow → Log it → red loop back to Progress (ping) → badges → button. Total 3.0 s.
4. 360 × 800 at 100%: no scroll, badges bottom-aligned. 320 × 640 at 200%: scrolls, labels wrap, no clipping or overlap.
5. Light and dark match §2. English and Spanish are complete, and the deleted keys are gone from both.
6. Animator duration scale 0: the intro fades in, holds until 3.5 s, and fades out. Screen 2 fades in over 200 ms with everything at rest.
7. TalkBack reads the full flow sentence for the diagram.
