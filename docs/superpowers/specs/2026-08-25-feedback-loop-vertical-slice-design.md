# WallCrawl Feedback Loop Vertical Slice Design

## Goal

Turn the existing generated-workout demo into a real local feedback loop while proving that bundled Workout Guide SVGs can render behind a provider abstraction.

## Baseline

The existing app already supports profile persistence, hard exercise filtering, ID-only fake planning, generated-workout validation, active workout persistence, set logging, workout completion, exercise search, and the four-tab Compose UI. The debug APK and current JVM tests pass when Gradle is given the locally installed JDK and Android SDK.

The main remaining demo seams are hard-coded workout history in the active logger, sample progress and PR data, a context assembled inside `TodayViewModel` without completed-session history, non-atomic session creation, and an exercise-visual placeholder that reads frame-like paths from the domain model.

## Chosen Approach

Build the feedback loop before broad module extraction. Add small pure domain services for history summarization and progress calculations, a dedicated `WorkoutGenerationContextBuilder`, and repository query/write boundaries. Keep the current manual application container because changing dependency-injection frameworks would not improve this vertical slice.

Two alternatives were rejected for this pass:

- Catalog-first import of all 302 upstream exercises would add a large mapping and asset surface before workout history can influence recommendations.
- Multi-module extraction would move existing files without fixing product behavior and would make this phase harder to verify.

## Architecture

The generation path becomes:

```text
UserProfileRepository + WorkoutRepository + ExerciseCatalog
                         ↓
          WorkoutGenerationContextBuilder
                         ↓
      ExerciseFilter + WorkoutHistoryAnalyzer
                         ↓
             allowed IDs + recent history
                         ↓
                   WorkoutPlanner
                         ↓
             GeneratedWorkoutValidator
                         ↓
                     Today UI
```

`WorkoutHistoryAnalyzer` is pure Kotlin. It summarizes recent completed sessions into per-exercise last performance, best estimated one-rep max, recent completed sets, and recently trained muscles. `WorkoutGenerationContextBuilder` performs repository/catalog reads and deliberately caps history before passing it to the planner.

`ProgressCalculator` is also pure Kotlin. It calculates weekly workout count, weekly volume, weekly muscle sets, workout streak, recent records, and strength trends from completed sessions plus catalog metadata. Empty history produces zeros and empty collections, never sample values.

The active workout screen receives the most recent completed sets for the current exercise from persisted history. Presentation formatting stays in the UI.

## Persistence

Room session, exercise, and set inserts are grouped behind one `@Transaction` DAO operation. Repository methods validate structurally invalid logging input before it reaches Room. Completing an unknown or already-completed session is rejected rather than fabricating a summary.

The stored schema already retains target and actual reps/weight, RPE/RIR, completion state, exercise IDs, order, timestamps, focus muscles, and duration. No schema migration is required for this slice.

## Workout Guide Visual Proof

Use upstream Workout Guide commit `ba0b709cb20430361b2cb33aaadd20998164a916` as the pinned source. Bundle three animation frames for a small representative mapping:

```text
WallCrawl incline-dumbbell-press → Workout Guide incline-dumbbell-press
WallCrawl pull-ups                → Workout Guide pull-up
WallCrawl dumbbell-lateral-raise  → Workout Guide lateral-raise
```

`ExerciseVisualProvider` returns domain-neutral visual frame descriptors. `WorkoutGuideVisualProvider` owns all asset paths and WallCrawl-to-upstream ID mapping. Screens pass exercise IDs to a reusable `ExerciseIllustration` composable; they never construct upstream paths. Coil 3 with its SVG decoder renders local Android assets, cycling frames in the sequence `1 → 2 → 3 → 2` with a restrained interval and a static fallback when no frames exist.

The bundled subset includes the upstream asset license and attribution plus a local provenance notice naming the pinned commit and unmodified files. Workout Guide repository text is treated as reference data; no upstream scripts or npm runtime are executed or included.

## State and Lifecycle

`TodayViewModel` performs initial generation from `init` with a guarded coroutine instead of launching work inside a Flow transform. Profile changes trigger a deliberate regeneration after the previous build completes. Existing active sessions remain the repository source of truth and continue surviving navigation/process recreation through Room.

`ActiveWorkoutViewModel` combines the observed session, profile, catalog lookup, and completed-session history. Exercise index is clamped when persisted session contents change. Set writes remain asynchronous but Room observations refresh the displayed session.

## Errors and Boundaries

- An empty allowed-candidate list remains a generation error.
- Planner output is rejected for blank/unknown/disallowed IDs, invalid or excessive set/rep/rest values, non-finite or negative weights, empty titles, and invalid durations.
- Completed sets require positive reps; incomplete edits may temporarily contain null values.
- Visual lookup returns an empty list for unknown IDs, allowing the existing polished placeholder to remain the offline fallback.
- Analytics ignores incomplete sets and malformed non-finite volume inputs.

## Testing

JVM tests cover history summarization, context construction, planner use of prior performance, validator numeric boundaries, real progress calculations, repository validation, and provider mappings. Existing tests remain green. Gradle verification includes the focused test classes, all debug unit tests, a debug APK build, and lint where the existing project supports it.

The visual renderer itself is verified by compilation and bundled-asset inspection in this pass; pixel/UI instrumentation tests are deferred until the project has an Android test harness.

## Out of Scope

- Production Qwen/Gemma/LiteRT inference
- Importing the complete Workout Guide catalog
- Hilt or multi-module migration
- Health Connect, timers, Wear OS, cloud sync, and accounts
- Model downloads or network access at application runtime
