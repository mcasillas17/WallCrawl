# WallCrawl Architecture

This document describes the application that is currently in the repository.
The historical design and implementation plans under `docs/superpowers/` explain
how individual phases were developed, but they are not the source of truth for
the current architecture.

## Product boundary

WallCrawl is local-first. The exercise catalog, profile, workout templates,
active workout, completed history, and progress data all live on the device.
There is no account requirement, catalog network request, or production LLM in
the current application. Movement-capability answers add no cloud sync,
analytics, Health Connect, Wear OS, network, or model data flow.

The application supports two workout entry points that converge on the same
session and history model, both gated behind first-run onboarding:

```text
                 fresh install → onboarding (goal, capability, equipment, constraints)
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

### Type-dependent legacy programming

`ExerciseProgrammingMetadata.recommendedRepRange` is nullable. When the optional
programming block exists, `Exercise` construction/copy requires a positive ordered
integer range up to 1000 for the three rep-based types, and no range for `DURATION`
or `DISTANCE_DURATION`. The Python importer selects the corresponding authored
rep-range schema definition using the pinned catalog type. Android validates after
resolving type, so JSON member order does not affect acceptance. The test-only catalog
projection uses the same domain validation; replay copies preserve null.

Timed inputs may omit the field or use null; generated timed records always carry
explicit null. Invalid shapes/types fail with bounded field-specific errors. The
exercise list and details omit rep badges when the range is null while displaying
available programming and coaching. The unchanged prescription factory uses duration
branches, never a rep-to-time conversion. See [Timed-hold programming](timed-hold-programming.md)
for the exact 14-entry cohort, support equipment, and AI-authorship limits.

## Onboarding and profile safety defaults

`UserProfile` never assumes gym access or training history it has not been
told about. A fresh profile defaults to `onboardingCompleted = false` and
`availableEquipment = [BODYWEIGHT]` — not the prior intermediate/full-gym
assumption — and `confirmedStartingLoads` and `trainingConstraints` start
empty. Every `MovementCapabilityType` resolves to `UNKNOWN` unless an explicit
stored value says otherwise. `WallCrawlApp` reads the onboarding flag to pick
the nav-graph start
destination: Today is never rendered or generated for a profile that has not
completed onboarding, so a fresh install cannot reach automatic planning
before the user has stated equipment, goal, experience, schedule, unit, all
seven movement preferences, and any `TrainingConstraint`s
(shoulder/elbow/wrist/lower-back/hip/knee sensitivity, low-impact-only).

The movement-preference step follows Experience & Units. Its seven questions
cover impact, floor transitions, unsupported squat, upper-body bodyweight push,
vertical pull or hang, balance without support, and continuous activity. Each
question requires an explicit `COMFORTABLE`, `LIMITED`, `AVOID`, or `UNKNOWN`
answer; the UI displays `UNKNOWN` as **Not sure**. A separate answered-key draft
prevents the conservative domain default from making an unanswered question
look complete. `SavedStateHandle` preserves that draft and the current wizard
step across process recreation.

`OnboardingViewModel.complete()` and `UserProfileRepository.saveProfile()`
persist onboarding as one atomic revision rather than one write per field, and
validate every planning-relevant input before it reaches Room: days per week
(2–6), session duration (20–120 minutes), return-after-break weeks (0–520),
non-empty and recognized equipment, and finite non-negative confirmed
starting loads. Training constraints and return-after-break weeks stay
editable from the Profile screen after onboarding; the onboarding flow itself
does not collect confirmed starting loads — see the next section for where
those come from. Movement capabilities are edited in a separate Profile draft:
Save persists the complete profile in one revision, while Cancel or Back drops
the draft without writing.

`TrainingConstraint` remains separate from movement capability. A capability
describes current movement comfort; a constraint is an explicit protected-joint
or low-impact preference. Weight, height, BMI, age, and body composition are not
part of the current profile model.

## Automatic workout generation

`WorkoutGenerationContextBuilder` deliberately collects a bounded view of local
state:

- the current profile and preferences;
- at most eight recent completed sessions;
- normalized exercise history and recently trained muscles;
- the full bundled catalog after hard filtering.

Production currently uses `ExerciseFilter` to remove explicit exclusions and
exercises whose required equipment is unavailable. Legacy programming equipment
combinations take precedence; otherwise, the upstream listed equipment is treated as
the known minimum. Filtering defines the legal search space but does not choose the
workout.

An implemented, dependency-injected `ExerciseEligibilityPolicy` is the reviewed-only
automatic legality path. When explicitly enabled it accepts only `APPROVED` reviewed
metadata, requires one complete reviewed equipment alternative, preserves explicit
exclusions, rejects required capabilities marked `AVOID`, fails closed for joint-
sensitive constraints that lack reviewed mappings, enforces `LOW_IMPACT_ONLY`, and
temporarily blocks undemonstrated `ADVANCED` work while uncalibrated or returning. A
supported regression lifts that ceiling only when the regression itself is below the
advanced ceiling or its family has demonstrated history. `LIMITED` and `UNKNOWN`
capability requirements remain typed soft preferences rather than becoming favorable
assumptions.

On that same reviewed-only path, `WorkoutGenerationContextBuilder` derives
`CapabilityEvidenceSet` locally and on demand from the same bounded max-eight completed
sessions it already read. `CapabilityEvidencePolicy` accepts only two distinct
`SessionStatus.COMPLETED` sessions for the same exercise ID whose non-warm-up work sets
all completed, all logged explicit `feltManageable == true`, and all carry valid
shape-specific persisted values. Comparability is exact exercise ID plus the persisted
measurement shape: `WEIGHT_REPETITIONS`, `BODYWEIGHT_REPETITIONS`,
`ASSISTED_BODYWEIGHT_REPETITIONS`, `TIMED_DURATION`,
`DISTANCE_DURATION_DISTANCE_ONLY`, `DISTANCE_DURATION_TIME_ONLY`, or
`DISTANCE_DURATION_DISTANCE_AND_TIME`. The policy validates field presence, bounds, and
shape consistency but compares no magnitudes or thresholds. This is product
reproducibility, not physiology, readiness, recovery, or medical inference. Evidence
applies only to the demonstrated exercise or one direct `approvedRegressions` target
when both source and target metadata are `APPROVED`; there is no draft, missing,
inferred, substitution, blank-ID, or transitive expansion.

`WorkoutPlanner` receives only structured `WorkoutGenerationContext`. The current
`FakeWorkoutPlanner` chooses exercises exclusively from `allowedExercises` and returns
catalog IDs with structured prescriptions. `GeneratedWorkoutValidator` then verifies
that every ID exists, remains in the allowed set, matches the catalog exercise type,
and belongs to a structurally valid workout. Unknown IDs are rejected, never silently
substituted.

`WorkoutGenerationContext` already carries the complete `UserProfile`, so no second
capability field exists. Production composition sets
`PlannerFeatureFlags.reviewedCapabilityEligibility = false` because the bundled cohort
contains 37 `DRAFT` entries and zero `APPROVED` entries. The current production
recommendation therefore still follows the legacy filter and remains invariant to
capability changes. Tests enable the gate only with synthetic in-memory approvals; a
reviewed no-candidate result reaches `TodayViewModel` as a typed reason and never falls
back to an unreviewed exercise. Enabling production requires explicit human metadata
signoff plus a deliberate availability/persona review and flag change.

On that reviewed-enabled path only, `StateBasedTrainingPolicy` consumes the composed
`TrainingProgramState`. It validates `PROGRAM_STATE_V1`, `PRIMARY_ONLY_V1`, approved
provenance, review-policy equality, and prescription shape before using the approved
direct-primary muscle. It caps a base prescription by the remaining editable weekly
product allowance and never increases it. Exact/over-cap exposure returns typed
no-guidance instead of a zero-set or over-cap prescription; malformed or version-mismatched
input returns a typed failure with no legacy fallback.

The same pure result carries nullable RIR guidance and a classified rest target. Product
defaults are 2-4 RIR for conservative states or a relevant approved `LIMITED` capability,
1-2 for established strength, and 1-3 for established general/hypertrophy; automatic
guidance cannot contain 0 RIR. `SHORT`, `MODERATE`, and `LONG` currently map to editable
60/90/180-second product defaults. These values are product policy, not physiology,
safety, or optimality claims. Explicit valid user rest preferences win. State and
capability can reduce sets but cannot change or invent a load.

Within a split, compound slots are chosen first by split-primary match inside the
compound pool, then by the reviewed capability soft-penalty bit, then by experience,
fatigue, and stable ID, while still spreading work across movement patterns so a
session is not the same lift three times. Remaining accessory slots prefer exercises
that train the split directly, then isolation work, then the presence of programming
metadata, then that same capability penalty, experience penalty, fatigue, and stable
ID. Evidence suppresses only the penalized candidate's one-bit capability penalty; it
never adds candidates, never removes candidates, and never outweighs the harder split
or mechanics ordering that already happened before it.

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
workouts. Rep work and non-stretch duration work without a Cardio muscle tag can
fill strength slots; metadata presence does not change this classification.

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

Domain constructors reject malformed prescriptions before persistence. The shared
prescription also carries nullable `EffortTarget`, `RestClass`, and `RestTargetSource`.
A classified rest target must carry its source; an explicit `USER_PREFERENCE` can be
reused by later reviewed recommendations, while a generated `PRODUCT_POLICY` target
cannot promote itself into a preference. Manual templates stay outside the automatic
policy and preserve whichever valid values they already contain.
`WorkoutRepository.logSetCompletion` validates recorded fields against the
persisted exercise type and prevents updates to sets whose session is no longer
active. It is the only way a set outcome is written.

### Typed set outcome

A logged set carries a typed outcome alongside its type-specific values:
nullable `rpe` (0-10) and `rir` (0-10), a nullable user-confirmed
`feltManageable`, a `completedAtTimestamp`, a `stoppedAtTimestamp`, and a
nullable `SetStopReason` (`USER_SKIPPED`, `PAIN_STOP`, `EQUIPMENT_UNAVAILABLE`,
`TIME_CONSTRAINT`, `OTHER`). `SetOutcomeRules` enforces the cross-field
invariants at the repository boundary before anything is persisted:

- null means unknown and stays null; no feedback is inferred from any other
  field, and a missing RPE or RIR is never replaced by an assumed effort;
- a completed set requires a positive `completedAtTimestamp` and cannot also
  carry a stop reason or a stop timestamp;
- a skipped or stopped set requires a typed reason and a positive
  `stoppedAtTimestamp`, and cannot carry `feltManageable`;
- an untouched set carries no timestamp, no feedback, and no stop reason;
- `feltManageable` is recorded only for completed work;
- rejection messages name the offending field and never echo entered values.

`SetOutcome` (`NotRecorded`, `Completed`, `Stopped`) is the derived read model,
so later adaptation can never confuse work that was never started with work the
user deliberately stopped. `PAIN_STOP` records only that the user chose to stop
because something hurt: it is not a symptom report, an injury, or a diagnosis,
and no surface presents it as one. There is no free-text stop reason.

Reviewed capability evidence already consumes a narrow, deterministic subset of this
feedback behind the reviewed-only flag: only non-warm-up work from two distinct
completed sessions for the same exercise ID, and every qualifying set must have
`feltManageable == true` plus a valid shape-specific logged payload. Null or false
manageable answers, completion alone, RPE, and RIR do not qualify evidence.
`ProgressionEngine` and `DeloadOfferPolicy` do not exist yet. Only completed work
counts toward volume, history, and progress today.

`DefaultExercisePrescriptionFactory` never invents a `WEIGHT_REPS` starting
load. It suggests a weight only when either applies, in that priority order:

1. bounded exercise history exists for that catalog ID, converted to the
   profile's current unit — the existing weight, or a unit-appropriate
   increment (+5 lb / +2.5 kg) once every recent completed set reached the
   top of the target rep range; or
2. the user has explicitly confirmed a baseline in
   `UserProfile.confirmedStartingLoads` for that ID.

With neither, `targetWeight` is `null` and stays null through to the session
snapshot and the active-workout UI: the load field is empty rather than
pre-filled with a fabricated number, and its first plus-press starts from the
planned target only when one exists. Once the user logs a real value, ordinary set
completion and the history analyzer take over for future sessions — there is
no separate write path that copies a logged value back into
`confirmedStartingLoads`.

## Room persistence and invariants

`WallCrawlDatabase` is currently schema version 11. Its tables store:

- the user profile, including onboarding status, multi-select fitness goals,
  training constraints, return-after-break weeks, confirmed starting loads,
  theme preference (`SYSTEM`, `DARK`, `LIGHT`), and movement capabilities;
- reusable workout templates and their ordered exercises;
- workout sessions and their ordered exercise snapshots;
- nullable effort targets, rest classes/sources, and exact rest seconds on template and
  workout exercise prescriptions;
- target and completed values for every set, plus its typed outcome:
  `rpe`, `rir`, `feltManageable`, `completedAtTimestamp`, `stoppedAtTimestamp`,
  and `stopReason`;
- a fingerprinted, reconstructable `PRIMARY_ONLY_V1` weekly-ledger cache whose
  authority remains immutable completed history.

Migration `3 → 4` adds template storage, session provenance, and type-aware
target/outcome columns while converting older repetition-based history to
`WEIGHT_REPS`. Migration `4 → 5` is additive-only: it adds
`onboardingCompleted`, `trainingConstraintsJson`, `returningAfterBreakWeeks`,
and `confirmedStartingLoadsJson` with conservative defaults, and explicitly
sets `onboardingCompleted = 0` for every existing row — a profile created
before onboarding existed was never reviewed against these safety-relevant
fields, so it must not be grandfathered in as already onboarded. Migration
`5 → 6` adds `fitnessGoalsJson` supporting multiple concurrent fitness goals
(e.g., hybrid hypertrophy and strength), initializing existing rows from
`primaryGoal`. Migration `6 → 7` adds `themePreference` with a default of
`SYSTEM`, enabling dynamic theme switching between System Default, Dark Mode,
and Light Mode. Migration `8 → 9` is additive-only: it adds the four nullable set-outcome
columns (`feltManageable`, `completedAtTimestamp`, `stoppedAtTimestamp`,
`stopReason`) with no SQL default, so history written before typed outcomes
existed reads back as an honestly unrecorded outcome instead of gaining a
fabricated completion timestamp or an assumed manageable answer. There is no
destructive migration fallback on any construction path, and the migration
tests exercise every supported starting schema through to version 11. Migration
`7 → 8` adds one non-null `movementCapabilitiesJson` column. Existing rows receive `{}`, which the codec
normalizes to all `UNKNOWN`; their onboarding status, revision, theme, goals,
equipment, constraints, confirmed loads, templates, sessions, sets, and history
remain intact. Every supported migration chain registers the new step, and
destructive migration fallback is disabled.

Migration `9 → 10` is additive-only: it creates the weekly-ledger cache table without
rewriting or dropping profile, template, workout, exercise, set, or outcome data. The
cache is accepted only when its deterministic source fingerprint matches current
completed history, catalog/review versions, policy version, week, and zone; a missing,
stale, corrupt, or deleted row is reconstructed rather than treated as authority.

Migration `10 → 11` adds nullable `effortMinRir`, `effortMaxRir`, `restClass`, and
`restTargetSource` columns to both template and workout exercises. Existing rows retain
their exact `restSeconds` and receive null guidance, so old manual templates, active
sessions, and completed history are not reinterpreted. New values round-trip through
template storage and frozen session snapshots; partial effort or rest pairs fail loudly
when mapped back into the domain.

Capability JSON is a bounded persistence detail, not a UI model. The codec
accepts at most 4096 characters, validates the flat object shape, allowlists
known enum keys and values, ignores unknown future keys, and resolves missing,
unknown, malformed, or oversized input conservatively to all `UNKNOWN`. Encoding
emits only stable enum names. Raw payloads and complete profiles are not placed
in logs or user-visible errors.

Android backup policy was not broadened for this milestone. The existing
manifest has `allowBackup=true` and no capability-specific backup rule, so the
Room database remains covered by the platform's pre-existing app-data backup
behavior when device settings permit it. The app currently has no profile
export or delete-data subsystem; consistent local export/delete controls remain
a separate roadmap item rather than a new subsystem in this change.

The persistence layer enforces several important invariants:

- only one workout session may be active;
- session creation inserts the session, exercises, and sets atomically;
- starting a workout uses the current profile revision and weight unit;
- completed or canceled sessions cannot accept additional set updates;
- template deletion cascades only to template exercise rows, never history;
- recommendation targets and performed outcomes remain separate.

Room-backed `Flow` streams make the active workout and completed history
observable after navigation or process recreation. Onboarding capability draft
answers are restored through `SavedStateHandle` without inventing answers;
Profile capability drafts remain in memory and are discarded on Cancel or Back.
Unsaved template-editor
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

Finishing a workout with sets that are neither completed nor stopped raises a
typed `FinishDecision.ConfirmIncomplete` carrying the open-set count, and
nothing is persisted until the user confirms; discarding an active workout needs
the same explicit confirmation. Backing out of either dialog does nothing, and
repeated taps stay idempotent. Skipped sets stay distinguishable from sets that
were never started, and neither contributes volume, history, or progress.

`WorkoutSummary` is built only by `WorkoutRepository`, from one history window,
whether a workout has just been completed or is being revisited. Personal
records use the same rules as the Progress screen's record list — a heavier top
set for loaded work, more reps for bodyweight work, and no record without prior
history to beat — so the two surfaces cannot disagree.

`WeeklyDoseLedgerRepository` reconstructs a `PRIMARY_ONLY_V1` ledger from completed history
and approved direct-primary metadata. Missing and `DRAFT` metadata become typed unattributed
work sets rather than guessed muscle credit.

`TrainingProgramStateProvider` composes that ledger with the adaptation state derived by
`AdaptationStatePolicy` into a `TrainingProgramState`, which rides on
`WorkoutGenerationContext` whenever reviewed capability eligibility is enabled. The provider
is the only unit in that composition performing I/O. On that same reviewed-only path,
`CapabilityEvidencePolicy` derives `CapabilityEvidenceSet` once from the already-bounded
history read; it adds no query, cache, migration, network, analytics, or logging path.
`StateBasedTrainingPolicy` reads the ledger's direct-primary counts to cap reviewed
prescriptions, and `CapabilityPreferenceRankingPolicy` reads the evidence set only to
suppress a soft capability penalty for the matching candidate. Progression and state
transitions still do not consume the ledger. On the legacy path the state is absent,
`capabilityEvidence` is empty, and the existing prescription path is returned unchanged.

The adaptation policy derives only `UNCALIBRATED` and `RETURNING`. `ExerciseEligibilityPolicy`
withholds advanced-complexity work on exactly those two states, so a third derived state
would lift that ceiling; a regression test couples them so widening the policy cannot happen
by accident.

## Lifecycle and failure handling

Feature ViewModels expose immutable `StateFlow` state to Compose. Repository and
catalog failures become actionable screen states, while coroutine cancellation
is rethrown. A failed template save keeps the draft available, and a failed
workout start cannot leave a partial session.

The active session is persisted immediately, so it can be resumed after normal
navigation or process recreation. The template editor does not yet persist an
unsaved draft or prompt before leaving with unsaved changes.

The rest timer is in-memory ViewModel state driven by an injected monotonic
elapsed-realtime clock. Remaining time is always derived from a deadline rather
than decremented, so backgrounding, a paused UI, or a missed tick cannot make it
drift, and a device clock change cannot lengthen or shorten a rest period in
progress. It survives recomposition and configuration changes, and it is
deliberately **not** restored after process death: a deadline captured against a
previous process's elapsed-realtime baseline would restore as a misleading
countdown, so the timer resets to `Idle` while the session itself is resumed
intact. This milestone adds no foreground service, notification, alarm, or Wear
behaviour.

## Dynamic Theming and Visual Contrast

WallCrawl supports dynamic theme adaptation across all features and shared
components via Jetpack Compose and Material 3:

```text
UserProfile.themePreference (SYSTEM | DARK | LIGHT)
               │
               ▼
        WallCrawlTheme
         ├─ LightColorScheme / DarkColorScheme
         ├─ WindowCompat (status & nav insets)
         └─ Dynamic Tokens (Surfaces, Borders, Typography)
               │
               ├→ WallCrawlCard / WebBackgroundPattern
               ├→ WallCrawlWordmark (Dynamic high-contrast brand)
               └→ ExerciseIllustration (Elevated container)
```

- **Theme Preferences**: `ThemePreference.SYSTEM` follows Android's system-wide
  dark mode setting via `isSystemInDarkTheme()`, while `DARK` and `LIGHT` enforce
  the respective color palette across the app.
- **System Inset Controller**: `WallCrawlTheme` updates `WindowInsetsController`
  to dynamically switch light and dark system status bar and navigation bar icon
  contrast.
- **Visual Contrast & Exercise Art**: Vector SVG illustrations use a dedicated
  dark graphite elevation backing so anatomical illustration lines remain crisp
  and visible against light and dark background themes alike. Light theme card
  and field borders use Slate 300 (`0xFFCBD5E1`) for sharp structural definition.
- **Wordmark & Typography**: Brand elements like `WallCrawlWordmark` dynamically
  render primary brand lettering in theme-aware typography tokens (`onSurface`),
  preserving high contrast on all screen densities.
- **Gym-Floor Ergonomics**: Active workout sets incorporate tactile Material 3
  `AssistChip` rest-timer controls, bottom padding clearance above sticky
  actions, and prominent constructive vs destructive confirmation dialog
  hierarchies.

## Verification boundaries

The JVM suite covers pure domain rules, filtering, context construction,
capability normalization and codec behavior, planner invariance, validation,
repository mapping, progress calculations, and ViewModel state. Instrumentation
tests cover every supported Room migration chain through schema 11, real 7 → 8,
9 → 10, and 10 → 11 preservation, foreign-key integrity, guidance round trips, the
weekly-ledger repository, capability
accessibility semantics, packaged catalog parsing, all bundled visual paths, template
snapshots, and session persistence. Pull-request/main CI and tag-release publication run
that connected suite on an API 36 emulator. The importer has a separate
Python-standard-library test suite.

See [Build and test](../README.md#build-and-test) for the commands contributors
should run.
