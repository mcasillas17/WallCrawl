# WallCrawl Architecture

This document describes the application that is currently in the repository.
The historical design and implementation plans under `docs/superpowers/` explain
how individual phases were developed, but they are not the source of truth for
the current architecture.

## Product boundary

WallCrawl is local-first. The exercise catalog, profile, workout templates,
active workout, completed history, and progress data all live on the device.
There is no account requirement, catalog network request, or production LLM in
the current application.

The application supports two workout entry points that converge on the same
session and history model, both gated behind first-run onboarding:

```text
                 fresh install → onboarding (equipment, goal, constraints)
                                          │
                         ┌─ automatic recommendation
Bundled catalog ─────────┤  profile + history → filter → planner → validator
                         │
                         └─ manually saved template
                            full catalog → editor → template validation
                                          │
                                          ▼
                               frozen workout session
                                          │
                                          ▼
                              type-aware set logging
                                          │
                                          ▼
                              completed local history
                                 ├─ progress metrics
                                 └─ future planner context
```

Automatic recommendations and manual templates intentionally share exercise
IDs, prescriptions, active-session persistence, logging, and analytics. This
keeps a future local model behind a replaceable planning boundary instead of
making the rest of the app depend on one inference implementation.

## Source organization

WallCrawl currently ships as one Android application module with package-level
boundaries:

| Package | Responsibility |
| --- | --- |
| `app` | Navigation and application composition |
| `core/model` | Catalog, profile, prescription, workout, template, and analytics models |
| `core/database` | Room entities, DAOs, relations, migrations, and repositories |
| `core/exercise` | Catalog search/filtering and the visual-provider boundary |
| `core/ai` | Context building, workout planning, prescription defaults, and validation |
| `core/progress` | Pure calculations over completed sessions |
| `core/ui` | Theme and reusable Compose components |
| `feature/*` | Screen state, ViewModels, and Compose UI for each product area |

`WallCrawlApplication` owns the current dependency container. Features receive
interfaces such as `ExerciseCatalog`, `WorkoutPlanner`, and repositories rather
than loading assets or using DAOs directly. This can move to a dedicated
dependency-injection framework later without changing the domain boundaries.

## Exercise catalog and visuals

The production catalog is a normalized, bundled snapshot of Workout Guide:

```text
app/src/main/assets/workout-guide/catalog.json
                    │
                    ▼
        WorkoutGuideCatalogStore
             ├─ BundledExerciseCatalog ── ExerciseCatalog
             └─ WorkoutGuideVisualProvider ── ExerciseVisualProvider
```

Feature code depends on WallCrawl's `Exercise` model and `ExerciseCatalog`
interface. It does not parse upstream JSON or construct raw asset paths.
Likewise, Compose screens request visuals through `ExerciseVisualProvider` and
render them with `ExerciseIllustration`.

The bundled snapshot contains 302 exercises and 906 SVG frames. Search covers
IDs, names, aliases, muscles, and listed equipment. The importer under
`tools/workout-guide/` validates and regenerates this snapshot from a pinned,
clean upstream checkout; the installed application never runs the importer or
contacts Workout Guide.

`WorkoutGuideCatalogParser` is also where upstream muscle names become
WallCrawl's, through `MuscleVocabulary`. Normalizing here rather than in the
asset keeps `catalog.json` byte-identical to the importer's output — so
`import_catalog.py --check` still verifies it — and keeps the vocabulary
decision in Kotlin where unit tests cover it. Two rules matter downstream:

- an upstream name maps to exactly one primary muscle, because weekly set
  counts credit each completed set to every primary;
- the other groups an umbrella name covers become secondary muscles, which
  split matching also reads, so nothing stops being selectable.

`BundledCatalogVocabularyTest` reads the shipped asset directly and fails if a
future catalog introduces a name the vocabulary does not know — the instrumented
parser tests cover the same ground but do not run in CI.

The parser also carries catalog provenance forward as `CatalogAttribution`
instead of validating and discarding it, because the CC BY-SA 4.0 license on the
artwork requires attribution to reach the user. `CreditsScreen` renders it
alongside the bundled notice files.

See the [README](../README.md#offline-workout-guide-catalog) for import commands,
the pinned commit, and licensing details.

## Onboarding and profile safety defaults

`UserProfile` never assumes gym access or training history it has not been
told about. A fresh profile defaults to `onboardingCompleted = false` and
`availableEquipment = [BODYWEIGHT]` — not the prior intermediate/full-gym
assumption — and `confirmedStartingLoads` and `trainingConstraints` start
empty. `WallCrawlApp` reads this flag to pick the nav-graph start
destination: Today is never rendered or generated for a profile that has not
completed onboarding, so a fresh install cannot reach automatic planning
before the user has stated equipment, goal, experience, schedule, unit, and
any `TrainingConstraint`s (shoulder/elbow/wrist/lower-back/hip/knee
sensitivity, low-impact-only).

`OnboardingViewModel.complete()` and `UserProfileRepository.saveProfile()`
persist onboarding as one atomic revision rather than one write per field, and
validate every planning-relevant input before it reaches Room: days per week
(2–6), session duration (20–120 minutes), return-after-break weeks (0–520),
non-empty and recognized equipment, and finite non-negative confirmed
starting loads. Training constraints and return-after-break weeks stay
editable from the Profile screen after onboarding; the onboarding flow itself
does not collect confirmed starting loads — see the next section for where
those come from.

## Automatic workout generation

`WorkoutGenerationContextBuilder` deliberately collects a bounded view of local
state:

- the current profile and preferences;
- at most eight recent completed sessions;
- normalized exercise history and recently trained muscles;
- the full bundled catalog after hard filtering.

`ExerciseFilter` removes explicit exclusions and exercises whose required
equipment is unavailable. Reviewed equipment combinations take precedence;
otherwise, the upstream listed equipment is treated as the known minimum.
Filtering defines the legal search space but does not choose the workout.

`WorkoutPlanner` receives only structured `WorkoutGenerationContext`. The
current `FakeWorkoutPlanner` chooses exercises exclusively from
`allowedExercises` and returns catalog IDs with structured prescriptions.
`GeneratedWorkoutValidator` then verifies that every ID exists, remains in the
allowed set, matches the catalog exercise type, and belongs to a structurally
valid workout. Unknown IDs are rejected, never silently substituted.

Within a split, the compound slots are chosen by what the exercise trains and how
much it demands — primary-muscle match, then fatigue — and spread across movement
patterns so a session is not the same lift three times. The remaining slots prefer
isolation work that trains the split directly, since the heavy work is already
chosen. Before this ordering existed, candidates were taken in catalog order,
which is alphabetical: a push day led with an Arnold press and a bench dip while
the bench press sat unused.

Split selection is deliberate about failure. High-priority muscles propose a
rotation; splits the candidate pool cannot fill are dropped from it, and if none
of the preferred splits survive, the full rotation is used instead. An exercise
that trains none of a split's muscles is never substituted in — that is what
produced push days padded with unrelated work — but the planner only fails when
nothing at all is trainable, so failure never depends on what the user
prioritized. Rotation is seeded from completed-workout count so it advances
across process death, not just within a session.

Cardio machines, distance work, and stretches are excluded from automatic
selection while remaining fully available in the catalog and in custom
workouts. The test is whether a sets-and-reps prescription is meaningful, not
whether conditioning is involved.

Planning failures carry a `WorkoutPlanningFailure` reason rather than
user-facing text; `TodayViewModel` maps reasons to copy. A future planner chain
branches on the same reason to decide between repairing, falling back to another
tier, and surfacing the failure — string matching on messages could not support
that.

A future local LLM should implement the same `WorkoutPlanner` interface. Model
integration does not remove the hard filter or validator; constrained decoding
and schema enforcement would be additional defenses at the inference boundary.

## Manual workout templates

Manual templates intentionally bypass automatic equipment filtering because the
user is making an explicit choice. The editor searches all 302 exercises and
shows equipment mismatches as warnings. Catalog existence and exercise-type
agreement remain hard requirements.

`WorkoutTemplateRepository` owns template CRUD and maps between the domain model
and Room. Saving a template transactionally replaces its ordered exercise rows.
Starting one goes through `WorkoutRepository.startWorkoutFromTemplate`, which
creates a standalone session snapshot tagged with:

- `origin = CUSTOM_TEMPLATE`;
- the informational `sourceTemplateId`;
- the profile's current weight unit;
- copied exercise order, notes, prescriptions, and set targets.

The source template ID is not a foreign key. Editing or deleting the template
therefore cannot alter an active or completed session. See
[Custom Workouts](custom-workouts.md) for product behavior and current editor
limits.

## Shared prescription and logging model

`ExercisePrescription` is the common contract for planner output, templates,
and session snapshots. It prevents incompatible target combinations for the five
catalog exercise types:

| Exercise type | Prescription and logged outcome |
| --- | --- |
| `WEIGHT_REPS` | Repetition range and optional load; actual reps and load |
| `BODYWEIGHT_REPS` | Repetition range; actual reps |
| `ASSISTED_BODYWEIGHT` | Repetition range and optional assistance; actual reps and assistance |
| `DURATION` | Target seconds; actual seconds |
| `DISTANCE_DURATION` | Target distance, duration, or both; actual distance and/or duration |

Domain constructors reject malformed prescriptions before persistence.
`WorkoutRepository.logSetCompletion` validates recorded fields against the
persisted exercise type and prevents updates to sets whose session is no longer
active.

`DefaultExercisePrescriptionFactory` never invents a `WEIGHT_REPS` starting
load. It suggests a weight only when either applies, in that priority order:

1. bounded exercise history exists for that catalog ID, converted to the
   profile's current unit — the existing weight, or a unit-appropriate
   increment (+5 lb / +2.5 kg) once every recent completed set reached the
   top of the target rep range; or
2. the user has explicitly confirmed a baseline in
   `UserProfile.confirmedStartingLoads` for that ID.

With neither, `targetWeight` is `null` and stays null through to the session
snapshot and the active-workout UI: `PerformanceSetRow`'s load field shows
"Choose starting load" instead of "Load `<unit>`" and is never pre-filled
with a fabricated number. Once the user logs a real value, ordinary set
completion and the history analyzer take over for future sessions — there is
no separate write path that copies a logged value back into
`confirmedStartingLoads`.

## Room persistence and invariants

`WallCrawlDatabase` is currently schema version 5. Its tables store:

- the user profile, including onboarding status, training constraints,
  return-after-break weeks, and confirmed starting loads;
- reusable workout templates and their ordered exercises;
- workout sessions and their ordered exercise snapshots;
- target and completed values for every set.

Migration `3 → 4` adds template storage, session provenance, and type-aware
target/outcome columns while converting older repetition-based history to
`WEIGHT_REPS`. Migration `4 → 5` is additive-only: it adds
`onboardingCompleted`, `trainingConstraintsJson`, `returningAfterBreakWeeks`,
and `confirmedStartingLoadsJson` with conservative defaults, and explicitly
sets `onboardingCompleted = 0` for every existing row — a profile created
before onboarding existed was never reviewed against these safety-relevant
fields, so it must not be grandfathered in as already onboarded. Destructive
migration fallback is disabled.

The persistence layer enforces several important invariants:

- only one workout session may be active;
- session creation inserts the session, exercises, and sets atomically;
- starting a workout uses the current profile revision and weight unit;
- completed or canceled sessions cannot accept additional set updates;
- template deletion cascades only to template exercise rows, never history;
- recommendation targets and performed outcomes remain separate.

Room-backed `Flow` streams make the active workout and completed history
observable after navigation or process recreation. Unsaved template-editor
drafts are in-memory state and are not yet restored after process death.

## Feedback loop and progress

Completing a session preserves its type-aware set outcomes. `ProgressCalculator`
derives current progress from completed sessions rather than sample metrics, and
`WorkoutHistoryAnalyzer` converts bounded history into structured input for the
next recommendation.

Weight-based volume only uses completed load-and-repetition work. Duration and
distance outcomes are retained for future analytics instead of being forced into
an invalid volume calculation. Completed reps are totalled alongside tonnage so
a week of bodyweight training reports the work it actually did. Each session
keeps the weight unit used when it was created; cross-unit planner and analytics
calculations convert values rather than relabeling stored history.

Weekly per-muscle set counts credit a set to each of an exercise's primary
muscles. Conditioning tags are not muscles and are excluded, so a week of
mobility work reports an empty focus card rather than "Mobility — 6 sets".

`WorkoutSummary` is built only by `WorkoutRepository`, from one history window,
whether a workout has just been completed or is being revisited. Personal
records use the same rules as the Progress screen's record list — a heavier top
set for loaded work, more reps for bodyweight work, and no record without prior
history to beat — so the two surfaces cannot disagree.

## Lifecycle and failure handling

Feature ViewModels expose immutable `StateFlow` state to Compose. Repository and
catalog failures become actionable screen states, while coroutine cancellation
is rethrown. A failed template save keeps the draft available, and a failed
workout start cannot leave a partial session.

The active session is persisted immediately, so it can be resumed after normal
navigation or process recreation. The template editor does not yet persist an
unsaved draft or prompt before leaving with unsaved changes.

## Verification boundaries

The JVM suite covers pure domain rules, filtering, context construction,
planning, validation, repository mapping, progress calculations, and ViewModel
state. Instrumentation tests cover Room transactions and migration, packaged
catalog parsing, all bundled visual paths, template snapshots, and session
persistence. The importer has a separate Python-standard-library test suite.

See [Build and test](../README.md#build-and-test) for the commands contributors
should run.
