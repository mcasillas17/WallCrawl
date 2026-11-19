# Welcome screens — "Workouts built for you" update

Change spec for the existing first-launch welcome flow. It tells users that WallCrawl builds
their workouts from their **goals**, **logged progress** and **equipment**.

- Base spec: `docs/first-launch-intro.md` (already updated to match this file; this file is the
  self-contained list of what to change in code).
- Design reference: https://claude.ai/artifact/AQWPWjQsSUV2a22iz14bgh. See the "Intro prototype" and
  "Screen 2 · What WallCrawl is about" boards.
- Scope: copy and layout on screen 1, plus a new card, removals and a new 3-second timeline on screen 2.
  Navigation, Skip and the launch splash are unchanged.

---

## 1. Screen 1 (intro): tagline line 2

| Key | EN | ES |
|---|---|---|
| `welcome_tagline_line2` | Workouts built from your goals, progress and equipment. | Rutinas según tus metas, tu progreso y tu equipo. |

The new text replaces "All on your phone." / "Todo en tu teléfono.". Styling and timing are unchanged:
`bodyLarge` 16/24 sp, onSurfaceVariant, centred. Alpha and translationY run at 1900 ms for 280 ms.

Consequences:
- Line 2 now wraps to **2 lines** at 360 dp, so the centred block grows from 254 to ≈278 dp.
  The emblem's settle target (the 120 dp slot) moves up by about 12 dp.
- **Any hard-coded slot offset must go.** ΔY (screen centre − slot centre) is ≈79 dp at 360×800,
  not 67. Measure the slot with `onGloballyPositioned`. This also applies to the first-launch handoff
  from the splash, where the emblem starts at screen centre at 168 dp.
- At 320×640 and 200% font the tagline can reach 5–6 lines. It must not clip.

---

## 2. Screen 2: layout changes

### Remove
- The **eyebrow** ("What WallCrawl does"), with its string `welcome_about_eyebrow` in both locales and its timeline rows.
- The **"Plan your week" feature row**, with `welcome_feature_plan_title` and `welcome_feature_plan_body`.
  The new builder card replaces it.

### Change
- Header: emblem slot 40 dp → **16 dp gap** → headline. The headline style is unchanged:
  28/34 sp Black, −0.5 sp, `heading()`.
- "Log every set" row icon: `FitnessCenter` → **`Icons.Outlined.Checklist`**. The dumbbell now
  means equipment only.
- Privacy body copy is shorter (see §4).

### New order of the scroll content
1. Header (emblem slot, headline)
2. **Workout builder card**, 28 dp below the header
3. Feature rows ×2 (Log every set, Track progress), 24 dp below the card, 20 dp between rows
4. Privacy card, 24 dp below
5. *(footer, outside the scroll)* Get started, unchanged

At 360×800 and 100% font the design is sized to fit without scrolling, but only just: the estimate
leaves a few dp to spare, so verify on device. If it overflows by a little, reduce the gap above the
privacy card from 24 to 16 dp before changing anything else. The footer divider only appears when the
content scrolls.

---

## 3. Workout builder card

```
┌───────────────────────────────────────────┐
│ Workouts built for you                    │  titleMedium
│ ┌─────────┐ ┌─────────┐ ┌─────────┐       │
│ │   (◎)   │ │   (↗)   │ │  (═╪═)  │       │  input tiles
│ │  Goals  │ │Progress │ │Equipment│       │
│ └────┬────┘ └────┬────┘ └────┬────┘       │
│       ╲          │          ╱             │  connectors
│ ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓   │
│ ┃ [emblem] Your next workout          ┃   │  output tile (red border)
│ ┃          Adjusts as you log.        ┃   │
│ ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛   │
└───────────────────────────────────────────┘
```

| Part | Spec |
|---|---|
| Card | `Column`, bg `colorScheme.surface`, 1 dp `outlineVariant` border, `RoundedCornerShape(16.dp)`, padding 16 dp, `spacedBy(12.dp)`. |
| Title | `titleMedium` 16/22 sp SemiBold, onBackground. |
| Input tiles | `Row(spacedBy(8.dp))` with 3 children of `weight(1f)`. Each tile: `Column(CenterHorizontally, Arrangement.spacedBy(4.dp, CenterVertically))`, `heightIn(min = 56.dp)`, padding vertical 8 dp and horizontal 4 dp, `RoundedCornerShape(12.dp)`, bg tile fill, 1 dp `outlineVariant` border. Icon 20 dp in the icon colour, then a label in `labelMedium` 12/16 sp SemiBold, onBackground, centred, `maxLines` unlimited (it wraps at 200%). **Not clickable.** |
| Input icons | Goals `Icons.Outlined.TrackChanges` · Progress `Icons.AutoMirrored.Outlined.TrendingUp` · Equipment `Icons.Outlined.FitnessCenter`. All decorative (`contentDescription = null`). |
| Connectors | The input row, the connector canvas and the output tile sit in an inner `Column` with **no spacing**, so the lines touch both the tiles and the output. The card's 12 dp spacing applies only between the title and that inner column. Use `Canvas(Modifier.fillMaxWidth().height(20.dp))` and draw three strokes, 1.5 dp, `StrokeCap.Round`, in the connector colour (§5). With `w` = canvas width and `h` = 20 dp: left `M(w/6, 0) C(w/6, 0.6h) (w/2, 0.4h) (w/2, h)`, middle `M(w/2, 0) L(w/2, h)`, and right mirrored from `5w/6`. Decorative. |
| Output tile | `Row(spacedBy(12.dp), verticalAlignment = CenterVertically)`, padding vertical 10 dp and horizontal 12 dp, `RoundedCornerShape(12.dp)`, bg tile fill, **1.5 dp border #DE2045** (`SpiderRedPrimary`). Left: the static emblem, 28 dp, drawn at rest with the same `Emblem` drawing as the header (red arc 12→3 o'clock, glints, streaks). Right: `Column` with title `titleSmall` 14/20 sp SemiBold onBackground and subtitle `bodySmall` 12/16 sp onSurfaceVariant. |
| Glow ring | `drawBehind` on the output tile: a rounded rect inflated by 4 dp (corner 16 dp), filled `SpiderRedPrimary.copy(alpha = glowAlpha)`, drawn before the tile background. `glowAlpha` comes from §6 and is 0 at rest. |
| Semantics | `Modifier.semantics(mergeDescendants = true) {}` on the card. TalkBack reads: "Workouts built for you, Goals, Progress, Equipment, Your next workout, Adjusts as you log." |

Tile fill is `GraphiteSurfaceElevated` #202125 in dark and `LightSurfaceCard` #F5F4F2 in light.
That is the same token the feature-row icon tiles and privacy chips already use.

---

## 4. Strings

Add these to `res/values/strings.xml` and `res/values-es/strings.xml`. Follow `docs/localization.md`.

| Key | EN | ES |
|---|---|---|
| `welcome_tagline_line2` *(changed)* | Workouts built from your goals, progress and equipment. | Rutinas según tus metas, tu progreso y tu equipo. |
| `welcome_builder_title` | Workouts built for you | Rutinas hechas para ti |
| `welcome_builder_goals` | Goals | Metas |
| `welcome_builder_progress` | Progress | Progreso |
| `welcome_builder_equipment` | Equipment | Equipo |
| `welcome_builder_output_title` | Your next workout | Tu próxima rutina |
| `welcome_builder_output_body` | Adjusts as you log. | Se ajusta con lo que registras. |
| `welcome_privacy_body` *(changed)* | Stays on this phone, even offline. | Se queda en tu teléfono, incluso sin conexión. |

Delete these: `welcome_about_eyebrow`, `welcome_feature_plan_title`, `welcome_feature_plan_body`.

The copy is grounded in the README. Recommendations use explicit rules and recorded training history,
and the workout changes when history or settings change. **Do not** use sparkle or "AI" iconography
or wording. The planner is rules-based, and the README says so.

---

## 5. Colours

| Use | Dark | Light |
|---|---|---|
| Connector strokes | `SpiderRedVibrant` #FF6C87 | `SpiderRedDeep` #602334 |
| Input and feature icons | #FF6C87 | `SpiderRedPrimary` #DE2045 |
| Output border and glow | #DE2045 | #DE2045 |
| Card bg / border | surface / outlineVariant | surface / outlineVariant |

The old "eyebrow" colour row is gone, and its tokens are reused for the connectors.

---

## 6. Screen 2 timeline (replaces the old one), 0 → 3000 ms

**Total: 3000 ms real time.** These are real milliseconds: remove `WelcomeMotion.ABOUT_PACE` (or set it to 1f). The durations were lengthened rather than uniformly slowed, so the overshoot pops stay snappy.
Easings: `EMPH_DEC` = (0.05, 0.7, 0.1, 1), `STANDARD` = (0.2, 0, 0, 1), `OVERSHOOT` = (0.34, 1.56, 0.64, 1),
`IN_OUT` = (0.65, 0, 0.35, 1).

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

Glow: `glow = 0.45f * seg(1650, 240, STANDARD) * (1f - seg(1950, 600, STANDARD))`.

Connector stroke progress: `PathMeasure().setPath(path, false)`, then `getSegment(0f, length * p, dst, true)`.
Skip drawing when `p == 0`.

Arrived via Skip: emblem alpha 0 → 1 over 0–250 ms, linear. Reduced motion (200 ms fade) and "Get started enabled from frame 0" are unchanged.
Under reduced motion the card shows at rest: connectors fully drawn, glow 0.

---

## 7. Acceptance

1. Intro line 2 shows the new copy in EN and ES. The emblem settles exactly into its slot with no jump
   or overlap with the wordmark at 360×800, 320×640, and at 100% and 200% font.
2. Screen 2 order: header → builder card → 2 feature rows → privacy card. No eyebrow and no "Plan your week".
3. At 360×800 and 100% the content fits without scrolling (no footer divider), with the §2 fallback applied if needed. At 320×640 and 200% it scrolls,
   the input labels wrap inside their tiles, and nothing clips.
4. Entrance: input tiles pop left to right, the three lines draw and meet, then the output tile pops with
   a brief red glow, then the feature rows, privacy card and button follow.
5. TalkBack reads the builder card as one merged node in the order in §3.
6. Removed string keys are gone from both locales, and the build has no unused-resource warnings for them.
7. Light and dark match §5.

## 8. Tests

- Update the screen-2 timeline unit tests to the new values: headline starts at 450 ms, card at 650 ms; output
  glow 0 at 0 ms, peak near 1950 ms, 0 again at ≥2550 ms; connector progress 1 at ≥1870 ms; everything at rest at 3000 ms.
- Compose UI test: the builder card exists with merged content description containing all three inputs and
  the output title. The "Plan your week" text does not exist.
- Screenshot or golden tests, if any exist for the welcome screens, need regenerating.
