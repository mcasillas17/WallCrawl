# Planner evaluation corpus

## Purpose and staged boundary

The planner evaluation corpus is a JVM-only, test-only harness around the current deterministic `FakeWorkoutPlanner`. It documents and replays representative planner inputs without changing production behavior. Its boundary is intentionally staged: JSON fixtures describe only user/profile/history inputs and invariant expectations, the test harness converts those fixtures into a real `WorkoutGenerationContext`, and the planner still operates only on the filtered candidate pool it already receives in production.

## Fixture location and corpus layout

- Persona fixtures live in `app/src/test/resources/planner-fixtures/*.json`.
- `app/src/test/resources/planner-fixtures/manifest.txt` is the authoritative corpus manifest. `PlannerFixtureLoader.loadCorpus()` reads that manifest instead of enumerating the directory.
- Negative fixtures such as malformed, oversized, duplicate, and unknown-field cases live beside the persona files but are exercised by loader tests rather than the corpus manifest.

Every corpus fixture has this root shape:

- `schemaVersion`
- `id`
- `policyVersion`
- `catalogVersion`
- `profile`
- `completedWorkoutCount`
- `exerciseHistory`
- optional `allowedExerciseIds`
- `expected`

The version fields are deliberately separate:

- `schemaVersion` gates the wire format. The loader currently accepts only `1`.
- `policyVersion` is required corpus metadata for the expectation/policy revision the fixture assumes. The loader enforces only a bounded positive integer (`1..10000`); the current corpus uses `3`, and the harness does not branch on it yet.
- `catalogVersion` is required corpus metadata naming the bundled catalog snapshot the fixture was authored against. The current corpus uses `workout-guide-bundled-302`. The harness validates that the field is present and non-blank, but it still loads the actual bundled asset at evaluation time.

## Persona coverage

The manifest currently contains nine personas:

1. `bodyweight-beginner` — bodyweight-only beginner coverage with a restricted allowed list and an any-of push movement expectation.
2. `band-only` — resistance-band-only back-focused coverage proving a band row can be selected while cable-based pull work such as `lat-pulldown` is excluded.
3. `machine-only` — machine-only strength coverage with a confirmed machine press load.
4. `full-gym-advanced` — broad full-gym strength-plus-hypertrophy coverage against the full bundled candidate pool.
5. `returning-user` — long-break re-entry coverage proving the re-entry workout name, preserved confirmed load, and two-set cap.
6. `limited-capability` — constrained low-impact profile coverage with exclusions and capability metadata present.
7. `mixed-unit-history` — kilogram history coverage proving prior KG history is honored and the existing load is preserved when recent sets do not justify an increase.
8. `sparse-history` — minimal pull-up history coverage proving sparse/bodyweight history remains valid.
9. `no-strength-candidates` — failure coverage where filtering and optional allowed-ID restriction leave only non-strength candidates, yielding a typed planning failure.

## Loader validation and security bounds

`PlannerFixtureLoader` treats fixture JSON as untrusted test input and validates it before any planner objects are built.

- Resources are classpath-only lookups with blank paths, `..`, backslashes, and unsafe resource names rejected before loading.
- Fixture files must be valid UTF-8 and no larger than `128 * 1024` bytes.
- A pre-parse duplicate-field scanner rejects duplicate object keys and JSON nesting deeper than 32 levels before object construction.
- Unknown fields are rejected at every object level.
- IDs must match `[a-z0-9]+(?:-[a-z0-9]+)*` and are capped at 80 characters.
- Strings are capped at 256 characters.
- Arrays and objects are capped at 100 entries; `exerciseHistory` is capped at 8 items.
- Numeric fields reject non-integers where integers are required, non-finite doubles, negatives where disallowed, and out-of-range values.
- Enums, equipment, muscles, and capability keys must map to known app constants.
- Duplicate list entries, duplicate manifest entries, duplicate fixture IDs, contradictory required/forbidden expectations, and invalid expected target-weight maps are rejected.

The loader validates structure only. It does not log fixture bodies or recreate planner behavior.

## Bundled catalog and filtering boundary

`PlannerFixtureContextFactory` builds contexts from the bundled planner catalog asset, not from fixture-embedded exercise definitions. It loads `workout-guide/catalog.json` first and falls back to `assets/workout-guide/catalog.json` if needed, parses the exercise fields needed by the deterministic planner, and enforces exactly 302 unique exercises.

The factory then applies the real `ExerciseFilter` before any optional `allowedExerciseIds` narrowing. This keeps equipment and explicit exclusions on the same side of the boundary as production. The `no-strength-candidates` persona demonstrates that ordering: `lat-pulldown` can appear in `allowedExerciseIds`, but it is removed by the real filter because the persona lacks the required equipment.

## Replay semantics

Each replay attempt uses a fresh `FakeWorkoutPlanner`. That is intentional: the planner keeps an in-memory `generationCounter`, and reusing one planner instance would rotate the split between attempts. Replay comparisons normalize only `GeneratedWorkout.id`, which is UUID-backed; every other generated field must remain identical.

## Invariants asserted by the corpus

`PlannerFixtureTest` and related tests assert these categories:

- deterministic output equality across two fresh replays, normalized only for the generated workout ID;
- typed fixture-failure evaluation, with the corpus manifest currently exercising `NO_STRENGTH_CANDIDATES` directly and the harness mapping the additional `NO_CANDIDATES` and `NO_CANDIDATES_FOR_ANY_SPLIT` outcomes for fixture use;
- legality of every selected exercise against the bundled catalog, the real filter result, and any optional allowed-ID restriction;
- exclusion enforcement for fixture-level forbidden exercise IDs and profile exclusions;
- type-valid prescriptions for weight, bodyweight, assisted, duration, and distance-duration exercises;
- independent no-invented-load behavior, where weight-based prescriptions stay `null` unless history or a confirmed starting load justifies a value;
- complete deep input non-mutation by snapshotting every `WorkoutGenerationContext` field before and after each attempt;
- capability invariance for the current pre-eligibility milestone by proving `limited-capability` matches an all-`COMFORTABLE` control;
- stable behavioral expectations expressed as focused invariants instead of full workout snapshots.

The stable behavioral expectations currently include:

- `requiredAnyExerciseIdGroups` for `bodyweight-beginner` (`push-up`, `pike-push-up`, or `bench-dip`) and `sparse-history` (`pull-ups`);
- exact expected target loads of `27.5` for `mixed-unit-history` (`incline-dumbbell-press`) and `40.0` for `returning-user` (`incline-dumbbell-press`);
- the `"(Re-entry)"` workout-name fragment for `returning-user`;
- `maxTargetSetsPerExercise = 2` for `returning-user`.

## Test entry points

The focused planner corpus command is:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk ./gradlew testDebugUnitTest --tests '*PlannerFixture*' --tests '*FakeWorkoutPlannerTest' --tests '*WorkoutGenerationContextBuilderTest' --rerun-tasks --no-daemon
```

Repository verification for this documentation change still uses the broader task-level commands:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk ./gradlew testDebugUnitTest --rerun-tasks --no-daemon
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk ./gradlew lintDebug assembleDebug --stacktrace --no-daemon
git diff --check
```

## Limitations and non-goals

This corpus does not claim scientific optimality. It does not add reviewed-only eligibility, a weekly dose ledger, progression or deload logic, LLM evaluation, Health or Wear integrations, production planner behavior changes, or a complete replacement for the packaged catalog parser. The lightweight bundled-catalog reader exists only to feed the deterministic planner tests; catalog-specific tests such as `WorkoutGuideCatalogParserTest` remain authoritative for packaged asset integrity.

## Maintenance expectations

Maintaining the corpus means updating `planner-fixtures/manifest.txt` whenever a new persona fixture is added, keeping the exact persona roster/count assertions in `PlannerFixtureTest` and `PlannerFixtureCorpusTest` aligned with that manifest, preserving the strict schema and validation rules, and preferring invariant assertions over full ordered workout snapshots. The 302-exercise baseline is also deliberate: if the bundled catalog snapshot changes, update the catalog-version metadata and the exact-count guards together. `schemaVersion`, `policyVersion`, and `catalogVersion` are meant to move deliberately: bump them only when the fixture format, expectation policy, or bundled catalog baseline truly changes.
