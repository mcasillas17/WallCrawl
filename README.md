<p align="center">
  <img src="art/wallcrawl-wordmark-dark-bg.png" width="380" alt="WallCrawl wordmark" />
</p>

# WallCrawl

WallCrawl is an open-source, local-first workout planner and progress tracker
for Android. It is building toward a private on-device coach that chooses
workouts from a constrained exercise catalog, while workout data stays on the
phone.

This repository currently contains the working application around that future
model: profile constraints, catalog filtering, structured workout generation
and validation, active set logging, Room persistence, workout-history context,
and progress calculations. The current `FakeWorkoutPlanner` is deliberately
replaceable; no production local LLM runtime is integrated yet.

## Current vertical slice

```text
UserProfile + bounded completed history
             ↓
WorkoutGenerationContextBuilder
             ↓
equipment + exclusions hard filter
             ↓
allowed Exercise IDs
             ↓
WorkoutPlanner (currently FakeWorkoutPlanner)
             ↓
GeneratedWorkoutValidator
             ↓
transactional active workout persistence
             ↓
set logging → completed session
             ↓
ProgressCalculator + next generation context
```

The fake planner uses the same `WorkoutPlanner` contract intended for a future
Qwen, Gemma, or LiteRT-backed implementation. It only selects IDs from
`WorkoutGenerationContext.allowedExercises`. The validator rejects unknown or
disallowed IDs and malformed set, rep, weight, rest, name, or duration values
before any workout reaches persistence.

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
feature/today/          daily recommendation and regeneration
feature/workout/        active workout logging and completion
feature/progress/       history-derived progress UI
feature/exercises/      searchable/filterable catalog browser
feature/profile/        local goals, equipment, units, and preferences
```

The current catalog is a small in-memory set with WallCrawl-owned domain
metadata. Upstream Workout Guide paths are not stored in `Exercise` and are not
visible to feature screens. `ExerciseVisualProvider` owns that integration
boundary.

## Workout Guide visual proof

A small, pinned [Workout Guide](https://github.com/bryllim/workout-guide)
subset proves the offline rendering path:

```text
exerciseId → WorkoutGuideVisualProvider → bundled SVG frames
           → ExerciseIllustration → Compose
```

The proof includes three unmodified frames each for incline dumbbell press,
pull-up, and lateral raise. Frames are bundled under
`app/src/main/assets/workout-guide/` and animate in a lightweight
`1 → 2 → 3 → 2` loop using Coil 3 with SVG support. Unsupported exercise IDs
show a local fallback rather than attempting network access.

The subset is pinned to Workout Guide commit
`ba0b709cb20430361b2cb33aaadd20998164a916`. Workout Guide visual assets are
CC BY-SA 4.0; the upstream `LICENSE-ASSETS` and `ATTRIBUTION.md`, the source
commit, and the WallCrawl ID mapping are preserved with the bundled assets.
WallCrawl source code remains covered by the repository's MIT license; the
third-party visual assets retain their own license.

## Build and test

Requirements:

- JDK 17 or newer (the project compiles to Java 17 bytecode)
- Android SDK 35
- `JAVA_HOME` and `ANDROID_HOME` configured

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The unit suite covers catalog filtering, context construction, bounded history
analysis, planner constraints and load progression, generated-workout
validation, atomic persistence boundaries, progress calculations, Today state,
duration calculation, and visual-provider mapping.

## Product and engineering principles

- Core workout planning and tracking should work offline without an account.
- A future model chooses the workout, but only inside a deterministic legal
  exercise set created from equipment, exclusions, and hard limitations.
- Model output is structured and always validated before persistence or UI.
- Recommendation and performed values are both retained for future progression.
- Each session retains its weight unit; mixed-unit history is converted only for
  planner and analytics calculations, never silently relabeled.
- Analytics are derived from completed local sessions, not sample metrics.
- Database migrations must preserve user history; destructive migration fallback
  is intentionally disabled.

## Next milestones

- Add a reproducible importer for the full pinned Workout Guide manifest/assets.
- Move catalog metadata from the in-memory proof to a bundled parsed catalog.
- Add richer active-workout controls such as rest timers, RPE/RIR editing, and
  exercise substitution.
- Expand progress calculations and charts as more history accumulates.
- Integrate a constrained on-device model only after the surrounding pipeline is
  production-ready.
- Later: Health Connect, Wear OS, programs/periodization, optional sync, and
  model management.
