<p align="center">
  <img src="art/wallcrawl-wordmark-dark-bg.png" width="380" alt="WallCrawl wordmark" />
</p>

# WallCrawl

WallCrawl is an open-source, local-first workout planner and progress tracker
for Android. It is building toward a private on-device coach that chooses
workouts from a constrained exercise catalog, while workout data stays on the
phone.

This repository currently contains the working application around that future
model: first-run onboarding with conservative equipment defaults, profile
constraints and local movement-capability inputs, a complete bundled catalog,
structured workout generation and
validation, reusable custom workout templates, type-aware active set logging
with no fabricated starting loads, Room persistence, workout-history context,
experience-aware exercise ordering, a production-disabled reviewed capability-
evidence soft-penalty relaxation, a production-disabled reviewed state-based
dose/effort/rest policy, and progress calculations. The current
`FakeWorkoutPlanner` is deliberately replaceable; no production local LLM
runtime is integrated yet.

## Screenshots & App Experience

### Dark & Light Theme Modes

WallCrawl supports **Dark Theme** (stealth suit graphite aesthetic), **Light Theme** (high-contrast daylight athletic), and **System Default** across every screen with dynamic insets, high-contrast SVG exercise frames, and a live switcher in Settings.

<p align="center">
  <img src="art/screenshots/today-screen.png" width="24%" alt="Today Screen (Dark)" />
  <img src="art/screenshots/today-screen-light.png" width="24%" alt="Today Screen (Light)" />
  <img src="art/screenshots/exercises-screen.png" width="24%" alt="Exercise Library (Dark)" />
  <img src="art/screenshots/exercises-screen-light.png" width="24%" alt="Exercise Library (Light)" />
</p>
<p align="center">
  <img src="art/screenshots/profile-screen.png" width="24%" alt="Training Profile & Preferences (Dark)" />
  <img src="art/screenshots/profile-screen-light.png" width="24%" alt="Training Profile & Preferences (Light)" />
  <img src="art/screenshots/workout-templates-light.png" width="24%" alt="My Workouts (Light)" />
  <img src="art/screenshots/template-editor-light.png" width="24%" alt="Workout Builder (Light)" />
</p>
<p align="center">
  <img src="art/screenshots/onboarding-capabilities-light.png" width="24%" alt="Onboarding Movement Preferences (Light)" />
  <img src="art/screenshots/onboarding-summary-light.png" width="24%" alt="Onboarding Blueprint Summary (Light)" />
  <img src="art/screenshots/exercise-detail-light.png" width="24%" alt="Exercise Detail & Artwork Frame (Light)" />
  <img src="art/screenshots/workout-summary.png" width="24%" alt="Workout Summary" />
</p>

- **Today Recommendation**: Offline planner-generated routine tailored to equipment and training goals, with instant regeneration and custom routine shortcuts.
- **My Workouts & Templates**: Manage and launch saved custom routines with total sets, exercise counts, and quick-start actions.
- **Custom Workout Builder**: Interactive routine editor with full 302-exercise bottom sheet picker, drag/reorder controls, and type-aware target set steppers.
- **Exercise Library**: Searchable catalog of 302 exercises across all muscle groups and equipment types.
- **Active Workout Session**: Type-aware logging for load/reps, bodyweight reps, assisted reps, duration, and distance/duration, with one-tap set completion, plus/minus and text entry for every value, a local rest countdown, optional RPE/RIR, animated SVG movement previews, and previous performance comparisons.
- **Workout Summary**: Post-workout card displaying session duration, total volume lifted, sets completed, and personal records set against your logged history.
- **Progress Tracking**: Weekly workout streaks, volume and rep totals, per-muscle weekly set counts, strength progression indicators, and historical workout logs.
- **Training Profile & App Preferences**: Full local customization of theme preference (Auto System / Dark Mode / Light Mode) with compact switcher, multi-select fitness goals, preferred weight units (LBS/KG), session duration targets, available gym equipment, return-after-break calibration, muscle priorities, and seven movement preferences.
- **Credits & Licenses**: In-app attribution for the bundled exercise artwork, reachable from the Training Profile screen.

## Documentation

- [Architecture](docs/architecture.md) explains the catalog, planner, template,
  persistence, logging, and history boundaries in the current application.
- [Custom Workouts](docs/custom-workouts.md) documents the user flow, full-catalog
  selection rules, frozen session snapshots, and current editor limitations.
- [Planner evaluation](docs/planner-evaluation.md) documents the versioned persona
  corpus, strict fixture validation, deterministic replay, and asserted planner
  invariants.
- The phase-specific design and implementation records under
  [`docs/superpowers/`](docs/superpowers/) provide historical decision context.

## Current vertical slice

```text
                fresh install → 8-step onboarding wizard
                                (codename, goals, units/experience,
                                 movement preferences, schedule/break,
                                 gear, safety, summary)
                                           │
                         ┌─ automatic recommendation
Bundled catalog ─────────┤  profile + bounded history
                         │            ↓
                         │  hard filter → WorkoutPlanner → validator
                         │
                         └─ manual template
                            all 302 exercises → local template
                                          │
                                          ▼
                              transactional active session
                                          │
                                          ▼
                              set logging → completed history
                                   ├─ ProgressCalculator
                                   └─ next generation context
```

A fresh install cannot skip onboarding: `UserProfile.onboardingCompleted`
starts `false`, equipment defaults to bodyweight-only rather than assuming a
full gym, and Today does not generate or render until onboarding is complete.
The 8-step wizard collects user codename, multi-select fitness goals, units and
experience level, seven movement preferences, schedule and break duration,
available gear, and sensitive-joint restrictions before compiling the initial
Training Blueprint. Every movement preference requires an explicit answer;
**Not sure** is a valid answer and persists as `UNKNOWN`.

### Movement capability inputs

WallCrawl stores seven local movement preferences: impact tolerance, floor
transitions, unsupported squat, upper-body bodyweight push, vertical pull or
hang, balance without support, and continuous activity. Each is one of
`COMFORTABLE`, `LIMITED`, `AVOID`, or `UNKNOWN` (shown as **Not sure**). They can
be edited and atomically saved from Training Profile; cancel or Back discards
the draft.

Fresh onboarding requires an explicit choice for all seven. Existing users who
upgrade from schema 7 remain onboarded and receive conservative `UNKNOWN`
values, so the upgrade does not send them through onboarding again. The values
are stored in the existing local Room profile. This milestone adds no weight,
height, BMI, age, body composition, cloud sync, analytics, Health Connect,
Wear OS, network, or LLM data flow.

The reviewed-only deterministic path can consume these values for automatic
planning, but production composition deliberately keeps that path disabled while
every reviewed-metadata entry is still `DRAFT`. The current production
recommendation therefore remains unchanged when a movement preference changes.
Tests enable the path only with unmistakably synthetic in-memory approvals and
verify equipment, exclusions, constraints, capability `AVOID`, impact,
reviewed-state, temporary advanced-complexity rules, and capability-evidence
soft-penalty suppression without changing browse or manual-workout access.

The fake planner uses the same `WorkoutPlanner` contract intended for a future
Qwen, Gemma, or LiteRT-backed implementation. It only selects IDs from
`WorkoutGenerationContext.allowedExercises`. The validator rejects unknown or
disallowed IDs and malformed set, rep, weight, rest, name, or duration values
before any workout reaches persistence.

### Reviewed state-based prescription policy

When tests explicitly enable reviewed eligibility, `TrainingProgramState` supplies
`PRIMARY_ONLY_V1` weekly direct-primary exposure to a pure, versioned prescription policy.
The policy can only reduce a valid base prescription: it never raises target sets or
changes/invents a load. Remaining weekly allowance is an upper-cap calculation with no
mandatory floor or automatic increment. The editable WallCrawl v1 product defaults cap
direct-primary exposure at 6/8/12 sets depending on state and cap one exercise at 2 or 4
sets; these are product values, not universal or medically optimal prescriptions.

The same reviewed path adds nullable effort guidance: conservative states or a relevant
approved `LIMITED` capability use 2-4 RIR, established strength uses the editable 1-2 RIR
product target, and established general/hypertrophy uses 1-3 RIR. Automatic guidance
never targets 0 RIR/failure. Rest is classified as `SHORT`, `MODERATE`, or `LONG` and
mapped to editable 60/90/180-second defaults. A valid explicit per-exercise user rest
choice wins and keeps its exact seconds.

Guidance is persisted with templates and frozen session snapshots in Room schema 11.
The active timer still reads the persisted exact seconds; add-time, skip, and dismiss are
one-off timer actions rather than durable preference changes. Production keeps reviewed
eligibility disabled because the bundled cohort remains 37 `DRAFT` / 0 `APPROVED`, so
today's legacy automatic prescriptions and manual template defaults are unchanged.

Manual templates use the same exercise IDs and type-aware prescriptions but do
not pass through `WorkoutPlanner`. See [WallCrawl Architecture](docs/architecture.md)
for the complete automatic and manual data flows.

## Gym-floor logging and rest timer

Logging a set is meant to survive a noisy gym floor, so the active workout screen
keeps every control large, explicit, and local.

- **One-tap completion.** Each set has a full-width completion control with
  checkbox semantics, a screen-reader label, and a 56 dp target.
- **Plus/minus plus text.** Every numeric outcome — load, assistance, reps,
  seconds, metres — has 48 dp decrease and increase controls beside a text field
  for precise values. The first press of a plus control starts from the planned
  target when one exists; it never invents a load that was never confirmed.
- **Copy previous.** When a comparable previous set actually recorded a value, a
  one-tap chip copies it in.
- **Optional effort.** RPE (0-10) and RIR (0-10) sit behind an "Add feedback
  (optional)" toggle, and a simple *felt manageable?* yes/no appears once a set
  is completed. All three are nullable: leaving them blank is a first-class
  answer, and nothing about completing a set requires them.
- **Typed skip or stop.** A set can be skipped or stopped with one of five typed
  reasons, including a plainly worded "Something hurt, so I stopped". That
  reason records only the user's decision — it is never a symptom report, an
  injury, or a diagnosis, and there is no free-text field.
- **Rest countdown.** Completing a set starts a countdown from that exercise's
  own persisted `restSeconds`, with add-30-seconds, skip, and dismiss. It is
  driven by a monotonic elapsed-realtime deadline, so backgrounding the app or
  changing the device clock cannot make it drift. It survives recomposition and
  rotation, and resets to idle after process death rather than restoring a
  deadline that no longer means anything. No foreground service, notification,
  or alarm is involved.
- **Safe finishing.** Finishing with unlogged sets asks first and says how many;
  discarding a workout asks first too. Only completed sets count toward volume,
  history, and progress — skipped, incomplete, and discarded work never looks
  finished.

Everything above stays on the device. Reviewed capability evidence now reads a
strict subset of that history behind the production-disabled reviewed gate: two
distinct `SessionStatus.COMPLETED` sessions for the same exercise ID, with only
qualifying non-warm-up work and explicit `feltManageable == true`. Completion
and stop fields only disqualify invalid observations; they do not create
evidence on their own. Null/false manageable answers, completion alone, RPE,
and RIR do not qualify. RPE/RIR remain stored for logging and are unused by
capability evidence. Progression and deload logic still do not consume the
feedback.

## Architecture

The Android app uses Kotlin, Jetpack Compose, Material 3, Navigation Compose,
Room, Coroutines, Flow/StateFlow, and Gradle Kotlin DSL.

```text
app/                    navigation and dependency container
core/model/             catalog, workout, profile/capability, and analytics domain models
core/database/          Room entities, DAOs, relations, and offline repositories
core/exercise/          catalog, hard filters, and visual-provider boundary
core/ai/                planner, context builder, history analysis, validation
core/progress/          pure progress calculations over completed sessions
core/ui/                theme and reusable Compose components
feature/onboarding/     first-run onboarding and conservative planning defaults
feature/today/          daily recommendation and regeneration
feature/templates/      local custom-workout library and editor
feature/workout/        active workout logging and completion
feature/progress/       history-derived progress UI
feature/exercises/      searchable/filterable catalog browser
feature/profile/        local goals, equipment, units, and movement preferences
```

Production uses a bundled Workout Guide catalog behind WallCrawl-owned domain
and provider interfaces. Upstream paths are not stored in `Exercise` and are
not visible to feature screens. `ExerciseVisualProvider` owns that integration
boundary, while `InMemoryExerciseCatalog` remains an injectable test fixture.

## Offline Workout Guide catalog

WallCrawl bundles the complete catalog from pinned
[Workout Guide](https://github.com/bryllim/workout-guide) commit
`ba0b709cb20430361b2cb33aaadd20998164a916`: 302 exercises and 906 SVG frames.
The installed app does not use npm, fetch catalog data, or require the upstream
repository.

```text
bundled catalog.json → WorkoutGuideCatalogStore
                     ├→ BundledExerciseCatalog → search and filters
                     └→ WorkoutGuideVisualProvider → SVG frames
                                                ↓
                                     ExerciseIllustration → Compose
```

All catalog facts are browseable and searchable by name, alias, muscle, and
listed equipment. Every bundled exercise can enter workout planning with a
structurally valid prescription appropriate to its catalog type. Legacy
`programming` metadata enriches those defaults when available; otherwise
WallCrawl uses conservative fallback targets. Its 117 authored entries cover
every muscle group with beginner options throughout. The planner draws its
compound slots from that set, softly demotes work above the profile's
experience level, and prefers authored entries when filling the rest. Difficulty
never removes an otherwise-legal candidate: an exercise without legacy
programming can still appear in a plan with fallback targets and no coaching
note, and a higher-difficulty exercise remains selectable when it is the only
fillable option.

A separate optional `reviewedMetadata` block defines categorical input for the
production-disabled deterministic eligibility gate. The initial 37-entry cohort is
entirely `DRAFT`, including its AI-authored rationale: it is not human-approved and does
not affect current workouts. `APPROVED` requires an explicit human-review role,
timestamp, and provenance change; pull-request approval does not change review state.
Missing or draft reviewed metadata never hides an exercise from browsing or manual
templates. See [Reviewed exercise metadata](docs/reviewed-exercise-metadata.md), its
generated [review report](docs/reviewed-exercise-metadata-review.md), the
[eligibility boundary](docs/reviewed-capability-eligibility.md), and the
[human sign-off packet](docs/reviewed-exercise-metadata-human-signoff.md).

Equipment requirements are alternatives: a goblet squat resolves with either a
dumbbell or a kettlebell. Where they are stricter than the upstream listing it is
deliberate — a lift that begins with a loaded bar held over the torso requires a
rack, while a lift cleaned from the floor does not, which is why the bench press
demands one and the overhead press does not. Planner-generated workouts still
apply the user's equipment hard filter. A user building a custom workout may
explicitly select any catalog exercise, with an equipment mismatch shown as a
warning rather than silently hiding the exercise.

Upstream muscle names enter the domain through `MuscleVocabulary`, so the
planner, muscle priorities, and weekly volume all share one set of names.
It resolves upstream spellings (`Quads` → `Quadriceps`) and umbrella terms that
name several groups at once: `Posterior Chain` becomes a `Hamstrings` primary
with `Glutes` and `Lower Back` secondary. Primary muscles stay one per upstream
name because weekly set counts credit a set to every primary — expanding them
in place would report one set of lunges as three. `catalog.json` itself is left
byte-identical to what the importer produces, so `--check` still verifies it.

Cardio machines, distance work, and stretches stay browseable and usable in
custom workouts, but are not prescribed as automatic training slots. The test
is whether sets and reps mean something for the movement: a kettlebell swing is
loaded work for reps that happens to involve conditioning, and a plank is a
timed hold that does not — both are planned; treadmills and jump rope are not.

Custom workout templates are stored locally in Room. Starting a template
creates a frozen active-session snapshot, so later template edits or deletion
do not rewrite workout history. Completed measurements retain their exercise
type and feed the same history and progress pipeline used by planned workouts.
Detailed target editing and unsaved-draft process restoration are intentionally
out of scope for this phase; the editor currently saves exercise order and set
count with conservative, type-specific targets.

Each exercise resolves to three bundled frames that animate in a lightweight
`1 → 2 → 3 → 2` loop using Coil 3 with SVG support. The compact normalized
`catalog.json` derives those paths from validated source slugs and a catalog
visual specification. The exact pinned upstream metadata and per-frame
attribution remain unmodified in `upstream-manifest.json` beside the SVGs.

The importer is a Python-standard-library development tool. Point it at a clean
local Workout Guide checkout at the pinned commit:

```bash
python3 tools/workout-guide/import_catalog.py \
  --source /path/to/workout-guide

python3 tools/workout-guide/import_catalog.py \
  --source /path/to/workout-guide \
  --check
```

The import validates the exact commit, source cleanliness, IDs, metadata,
licenses, paths, frame counts, legacy programming overrides, strict reviewed
metadata schema, and reviewed graphs before atomically replacing the generated
Android asset directory. It also regenerates the deterministic human-review
report; `--check` detects catalog or report drift without writing. It copies SVG
only; the PNG counterparts would duplicate the same illustrations without
helping Android's vector rendering path.

Workout Guide visual assets are CC BY-SA 4.0. Its `LICENSE`,
`LICENSE-ASSETS`, `ATTRIBUTION.md`, full `upstream-manifest.json`, pinned commit,
and WallCrawl notice are preserved under
`app/src/main/assets/workout-guide/`. WallCrawl source code remains covered by
the repository's MIT license; third-party assets retain their own terms.

The license also requires the credit to reach the person using the app, not
only someone reading this repository. **Training Profile → Credits & Licenses**
shows the creator, the license and a link to it, the pinned upstream commit,
and the bundled notices — including the Everkinetic provenance of the original
artwork. Catalog provenance is carried through `CatalogAttribution` rather than
discarded at parse, so that screen renders what actually shipped.

## Build and test

Requirements:

- JDK 17 (the project compiles to Java 17 bytecode)
- Android SDK platform 37, which the app compiles against; it still targets API 35
- `JAVA_HOME` and `ANDROID_HOME` configured

Gradle, the Android Gradle Plugin, and Kotlin come from the wrapper and the version
catalog, so no local install of those is needed. `connectedDebugAndroidTest` needs a
running emulator or device; it fails with "No compatible devices connected" rather than
skipping when none is attached.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
```

The importer suites run in CI alongside the Gradle build. One exercises the importer
against synthetic fixtures; the other checks the reviewed metadata that actually ships,
so a bad edit fails there rather than becoming a workout nobody can perform.

## Release versioning

Tagged builds derive Android package metadata automatically. The release workflow uses
its monotonically increasing GitHub Actions run number as `versionCode` and the tag
without its leading `v` as `versionName`, then passes both to Gradle. It verifies the
generated APK metadata before publishing. Local builds default to `versionCode` 1 and
`versionName` `0.1.0-dev`; they are not release identifiers.

GitHub numbers runs per workflow. If `.github/workflows/release.yml` is replaced rather
than edited in place, preserve a `versionCode` greater than the latest distributed build
before publishing from the replacement workflow.

GitHub prereleases are currently debug-signed and intentionally require uninstalling
the previous CI build. Supporting in-place upgrades also requires a stable release
signing key; version metadata alone cannot make differently signed APKs compatible.

The unit suite covers catalog filtering, context construction, capability
normalization and persistence, onboarding/Profile drafts, planner invariance,
bounded history
analysis, planner constraints and type-aware prescriptions, split selection and
its failure reasons, the muscle vocabulary and the shipped catalog's conformance
to it, generated-workout validation, template validation, atomic persistence
boundaries, progress and personal-record calculations, attribution loading,
Today state, duration calculation, and visual-provider mapping.
Android instrumentation also validates every supported database migration chain through
schema 11 without destructive fallback, guidance persistence, the weekly-ledger DAO/repository,
capability-control semantics, and template/session snapshot behavior. It parses the
packaged 302-exercise catalog and opens every one of its 906 SVG paths. Pull-request/main
CI and tagged-release publication both run this connected suite on an API 36 emulator;
a tag cannot publish its prerelease unless instrumentation succeeds.

## Product and engineering principles

- Core workout planning and tracking should work offline without an account.
- A fresh install must complete explicit onboarding before it can reach
  automatic planning; nothing about equipment, experience, or gym access is
  assumed on its behalf.
- A future model chooses the workout, but only inside a deterministic legal
  exercise set created from equipment, exclusions, and hard limitations.
- Model output is structured and always validated before persistence or UI.
- No unconfirmed starting load is ever prescribed: a `WEIGHT_REPS` target
  weight comes only from bounded history or a load the user explicitly
  confirmed, never a sample or catalog default.
- Recommendation and performed values are both retained for future progression.
- Each session retains its weight unit; mixed-unit history is converted only for
  planner and analytics calculations, never silently relabeled.
- Profile experience softly ranks otherwise-comparable automatic candidates; it
  is not a permanent legality gate. The default legacy path reads
  `programming.difficulty`. The reviewed-enabled path reads only human-approved
  `reviewedMetadata.complexity`, never draft metadata.
- Analytics are derived from completed local sessions, not sample metrics.
- Movement-capability values already drive the reviewed-only automatic path's
  hard eligibility, soft preferences, evidence-backed soft-capability penalty
  suppression, and relevant limited-capability effort guidance, but production
  keeps that path disabled until human-approved metadata and an explicit
  enablement review exist. They do not affect today's production filtering,
  ranking, substitutions, dose, effort, or rest guidance.
- Database migrations must preserve user history; destructive migration fallback
  is intentionally disabled.

## Next milestones

- Complete human review of the 37-entry categorical draft cohort, then perform an
  explicit availability/persona review before switching production to the implemented
  reviewed-only capability gate. Its experience ranker reads only `APPROVED`
  complexity; model or pull-request review is not metadata approval, and the same
  flag would also turn on reviewed capability evidence and reviewed state-based
  prescription guidance.
- Make `recommendedRepRange` optional so timed holds can be reviewed. Fourteen
  planner-eligible movements — planks, dead hangs, wall sits — cannot carry
  mechanics, fatigue, or a coaching note today, because the schema demands a rep
  range their prescriptions never read.
- Review the exercises the planner reaches for outside the reviewed set, and add
  band coverage: a band-only profile is served almost entirely by unreviewed
  entries.
- Continue reviewing programming metadata beyond the planner's working set, so
  browsing and custom workouts benefit from it too.
- Task 6A shipped behind the production-disabled reviewed gate: capability
  evidence now requires two comparable completed sessions for the same exercise
  ID plus explicit `feltManageable == true`, and suppresses only that
  candidate's soft capability penalty.
- Task 6B remains one-variable progression.
- Task 6C remains user-controlled `DeloadOffer`s. Logged RPE/RIR, manageable
  confirmation, and typed stop reasons are captured today; only the strict
  capability-evidence rule consumes them.
- Add exercise substitution to the active workout.
- Expand progress calculations and charts as more history accumulates.
- Integrate a constrained on-device model only after the surrounding pipeline is
  production-ready.
- Later: Health Connect, Wear OS, programs/periodization, optional sync, and
  model management.
