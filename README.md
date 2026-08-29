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
constraints, a complete bundled catalog, structured workout generation and
validation, reusable custom workout templates, type-aware active set logging
with no fabricated starting loads, Room persistence, workout-history context,
and progress calculations. The current `FakeWorkoutPlanner` is deliberately
replaceable; no production local LLM runtime is integrated yet.

## Screenshots & App Experience

<p align="center">
  <img src="art/screenshots/today-screen.png" width="23%" alt="Today Screen" />
  <img src="art/screenshots/workout-templates.png" width="23%" alt="My Workouts Screen" />
  <img src="art/screenshots/template-editor.png" width="23%" alt="Workout Builder Screen" />
  <img src="art/screenshots/exercises-screen.png" width="23%" alt="Exercise Library Screen" />
</p>
<p align="center">
  <img src="art/screenshots/active-workout.png" width="23%" alt="Active Workout Screen" />
  <img src="art/screenshots/workout-summary.png" width="23%" alt="Workout Summary Screen" />
  <img src="art/screenshots/progress-screen.png" width="23%" alt="Progress Screen" />
  <img src="art/screenshots/profile-screen.png" width="23%" alt="Training Profile Screen" />
</p>

- **Today Recommendation**: Offline planner-generated routine tailored to equipment and training goals, with instant regeneration and custom routine shortcuts.
- **My Workouts & Templates**: Manage and launch saved custom routines with total sets, exercise counts, and quick-start actions.
- **Custom Workout Builder**: Interactive routine editor with full 302-exercise bottom sheet picker, drag/reorder controls, and type-aware target set steppers.
- **Exercise Library**: Searchable catalog of 302 exercises across all muscle groups and equipment types.
- **Active Workout Session**: Type-aware logging for load/reps, bodyweight reps, assisted reps, duration, and distance/duration, with animated SVG movement previews and previous performance comparisons.
- **Workout Summary**: Post-workout card displaying session duration, total volume lifted, sets completed, and personal records set against your logged history.
- **Progress Tracking**: Weekly workout streaks, volume and rep totals, per-muscle weekly set counts, strength progression indicators, and historical workout logs.
- **Training Profile**: Full local customization of multi-select fitness goals, preferred weight units (LBS/KG), session duration targets, available gym equipment, return-after-break calibration, and muscle priorities.
- **Credits & Licenses**: In-app attribution for the bundled exercise artwork, reachable from the Training Profile screen.

## Documentation

- [Architecture](docs/architecture.md) explains the catalog, planner, template,
  persistence, logging, and history boundaries in the current application.
- [Custom Workouts](docs/custom-workouts.md) documents the user flow, full-catalog
  selection rules, frozen session snapshots, and current editor limitations.
- The phase-specific design and implementation records under
  [`docs/superpowers/`](docs/superpowers/) provide historical decision context.

## Current vertical slice

```text
                fresh install → 7-step onboarding wizard
                                (codename, goals, units/experience,
                                 schedule/break, gear, safety, summary)
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
The 7-step wizard collects user codename, multi-select fitness goals, units and
experience level, schedule and break duration, available gear, and sensitive joint
restrictions before compiling the initial Training Blueprint.

The fake planner uses the same `WorkoutPlanner` contract intended for a future
Qwen, Gemma, or LiteRT-backed implementation. It only selects IDs from
`WorkoutGenerationContext.allowedExercises`. The validator rejects unknown or
disallowed IDs and malformed set, rep, weight, rest, name, or duration values
before any workout reaches persistence.

Manual templates use the same exercise IDs and type-aware prescriptions but do
not pass through `WorkoutPlanner`. See [WallCrawl Architecture](docs/architecture.md)
for the complete automatic and manual data flows.

## Architecture

The Android app uses Kotlin, Jetpack Compose, Material 3, Navigation Compose,
Room, Coroutines, Flow/StateFlow, and Gradle Kotlin DSL.

```text
app/                    navigation and dependency container
core/model/             catalog, workout, profile, and analytics domain models
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
feature/profile/        local goals, equipment, units, and preferences
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
structurally valid prescription appropriate to its catalog type. Reviewed
`programming` metadata enriches those defaults when available; otherwise
WallCrawl uses conservative fallback targets. 117 of the 302 exercises are
reviewed, covering every muscle group with beginner options throughout. The
planner draws its compound slots from that set and prefers it when filling the
rest, but it still selects from the whole catalog, so an unreviewed exercise can
appear in a plan with fallback targets and no coaching note.

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
licenses, paths, frame counts, and reviewed overrides before atomically
replacing the generated Android asset directory. It copies SVG only; the PNG
counterparts would duplicate the same illustrations without helping Android's
vector rendering path.

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
- Android SDK 35
- `JAVA_HOME` and `ANDROID_HOME` configured

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
```

The importer suites run in CI alongside the Gradle build. One exercises the importer
against synthetic fixtures; the other checks the reviewed metadata that actually ships,
so a bad edit fails there rather than becoming a workout nobody can perform.

The unit suite covers catalog filtering, context construction, bounded history
analysis, planner constraints and type-aware prescriptions, split selection and
its failure reasons, the muscle vocabulary and the shipped catalog's conformance
to it, generated-workout validation, template validation, atomic persistence
boundaries, progress and personal-record calculations, attribution loading,
Today state, duration calculation, and visual-provider mapping.
Android instrumentation also validates database migration and template/session
snapshot behavior, parses the packaged 302-exercise catalog, and opens every one
of its 906 SVG paths.

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
- Analytics are derived from completed local sessions, not sample metrics.
- Database migrations must preserve user history; destructive migration fallback
  is intentionally disabled.

## Next milestones

- Gate exercise difficulty on the profile's experience level. The data now
  exists across every muscle group; nothing reads it yet, so a beginner can still
  be led with a lift marked advanced.
- Make `recommendedRepRange` optional so timed holds can be reviewed. Fourteen
  planner-eligible movements — planks, dead hangs, wall sits — cannot carry
  mechanics, fatigue, or a coaching note today, because the schema demands a rep
  range their prescriptions never read.
- Review the exercises the planner reaches for outside the reviewed set, and add
  band coverage: a band-only profile is served almost entirely by unreviewed
  entries.
- Continue reviewing programming metadata beyond the planner's working set, so
  browsing and custom workouts benefit from it too.
- Add richer active-workout controls such as rest timers, RPE/RIR editing, and
  exercise substitution.
- Expand progress calculations and charts as more history accumulates.
- Integrate a constrained on-device model only after the surrounding pipeline is
  production-ready.
- Later: Health Connect, Wear OS, programs/periodization, optional sync, and
  model management.
