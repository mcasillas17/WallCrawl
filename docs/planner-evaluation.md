# Planner evaluation corpus

## Purpose and staged boundary

The planner evaluation corpus is a JVM-only, test-only harness around the current deterministic `FakeWorkoutPlanner`. It documents representative planner inputs, builds a real `WorkoutGenerationContext`, and replays planner behavior without changing production code.

The boundary is explicit:

- production still owns equipment filtering, split selection, prescription generation, and typed failures;
- legacy corpus fixtures can optionally narrow the legal candidate pool with curated `allowedExerciseIds`;
- reviewed-enabled fixtures exercise the production eligibility and state-based
  prescription policies with explicitly synthetic in-memory approvals and a synthetic
  composed empty weekly ledger while the production rollout flag remains disabled;
- capability inputs and `TrainingConstraint` metadata remain inert on legacy fixtures,
  matching the production-disabled rollout, and become policy inputs only on
  reviewed-enabled fixtures.

These fixtures therefore model the planner **inside** a curated legal set. They do not claim that the current planner discovered capability, safety, or persona appropriateness on its own.

## Fixture location and corpus layout

- Persona fixtures live in `app/src/test/resources/planner-fixtures/*.json`.
- `app/src/test/resources/planner-fixtures/manifest.txt` is the authoritative corpus manifest. `PlannerFixtureLoader.loadCorpus()` reads that manifest instead of enumerating the directory.
- Loader-only malformed/invalid fixtures live beside the corpus resources but stay out of the manifest.

Every corpus fixture uses this root shape:

- `schemaVersion`
- `id`
- `policyVersion`
- `catalogVersion`
- `profile`
- `completedWorkoutCount`
- `exerciseHistory`
- optional `allowedExerciseIds`
- optional `reviewedEligibility` with an adaptation state and bounded list of bundled DRAFT IDs to copy as synthetic in-memory approvals
- `expected`

## Version and reference contract

The version fields are separate and enforced deliberately:

- `schemaVersion` is the fixture wire-format version. The loader accepts only `1`.
- `policyVersion` is the supported corpus expectation contract. `PlannerFixtureContextFactory.create()` accepts only policy version `3` for corpus evaluation.
- `catalogVersion` is the pinned bundled catalog source commit, not a friendly label. The current corpus pins every persona fixture to `ba0b709cb20430361b2cb33aaadd20998164a916`, and context construction rejects mismatches against the bundled catalog root `source.commit`.

The test projection also validates the bundled catalog root fields it relies on:

- `catalog.schemaVersion == 1`
- `catalog.source.commit` is present and non-blank
- exactly 302 unique exercises are available to the harness

Before a context is built, every exercise reference in a fixture is validated against the bundled catalog with field-level errors. This includes:

- `allowedExerciseIds`
- `reviewedEligibility.syntheticApprovedExerciseIds`
- `profile.excludedExerciseIds`
- `profile.confirmedStartingLoads.keys`
- `exerciseHistory.exerciseId`
- `expected.requiredExerciseIds`
- every member of `expected.requiredAnyExerciseIdGroups`
- `expected.expectedTargetWeights.keys`
- `expected.forbiddenExerciseIds`

Failures report the field path and unknown ID only; they never echo the whole fixture payload.

## Loader validation and failure-schema integrity

`PlannerFixtureLoader` treats fixture JSON as untrusted test input and validates it before any planner objects are built.

- Resources are classpath-only lookups with blank paths, `..`, backslashes, and unsafe resource names rejected before loading.
- Fixture files must be valid UTF-8 and no larger than 128 KiB.
- A duplicate-field / nesting-depth prescan rejects duplicate object keys and pathological nesting before object construction.
- Unknown fields are rejected at every object level.
- Strings, numeric ranges, duplicate arrays, duplicate fixture IDs, and contradictory success expectations are bounded and validated.
- For any `expected.outcome` other than `SUCCESS`, success-only assertions are rejected:
  - `requiredExerciseIds`
  - `forbiddenExerciseIds`
  - `requiredAnyExerciseIdGroups`
  - `expectedTargetWeights`
  - `workoutNameContains`
  - `maxTargetSetsPerExercise`

Failure fixtures may therefore assert only the typed outcome they expect from the real planner.

## Bundled catalog projection boundary

`PlannerFixtureContextFactory` does **not** reimplement the full packaged catalog parser. It intentionally maps only the `Exercise`, `ExerciseProgrammingMetadata`, and `ReviewedExerciseMetadata` fields currently consumed by:

- `ExerciseFilter`
- `FakeWorkoutPlanner`
- `DefaultExercisePrescriptionFactory`
- `ExerciseEligibilityPolicy`

That includes exercise identity, canonical muscles, listed equipment, type, stretch flag, and reviewed programming metadata used for filtering, split matching, ordering, and prescriptions. The harness does **not** populate unrelated attribution/source data solely for tests.

The projection validates the same type-dependent legacy rep-range contract as the
Python importer and Android parser, using shared fixtures from
`app/src/androidTest/assets/programming-validation-fixtures.json`. It preserves missing
or explicit-null timed ranges as null, rejects fabricated timed ranges and absent rep
ranges, and deep-copies nullable ranges for replay snapshots. Raw range JSON in the
fixture preserves numeric notation rather than letting a JSON serializer change it.
`TimedHoldProgrammingTest` independently pins all 14 duration strength IDs through
actual single-candidate planner calls, tests excluded timed work even with metadata,
replays a timed-only persona through the same harness, and pins baseline rep prescriptions.

Full packaged catalog validity remains the responsibility of the dedicated importer/instrumentation tests, especially `WorkoutGuideCatalogParserTest`.

## Persona coverage

The manifest currently contains eleven fixtures:

1. `bodyweight-beginner` — conservative curated bodyweight beginner subset (`push-up`, `knee-push-up`, `bodyweight-squat`, `dead-bug`) requiring at least one beginner push variant.
2. `band-only` — resistance-band-only back-focused coverage proving a band row can be selected while cable-only pull work is excluded by the real filter.
3. `machine-only` — machine-only strength coverage with a confirmed machine press load.
4. `full-gym-advanced` — broad full-gym strength-plus-hypertrophy coverage against the full bundled candidate pool.
5. `returning-user` — curated lower-demand full-body subset for re-entry (`incline-dumbbell-press`, `one-arm-dumbbell-row`, `goblet-squat`, `glute-bridge`, `dead-bug`), preserving the `"(Re-entry)"` title fragment, a max-two-set cap, the confirmed incline press load, and keeping `ab-wheel` / `single-leg-romanian-deadlift` out of the curated pool.
6. `limited-capability` — curated dumbbell/bench push subset (`dumbbell-bench-press`, `dumbbell-shoulder-press`, `incline-dumbbell-press`, `dumbbell-lateral-raise`) that keeps capability metadata present but inert for planner eligibility and asserts the shoulder-press target load from history / confirmed data.
7. `mixed-unit-history` — kilogram history coverage proving prior KG history is honored and the existing load is preserved when recent sets do not justify an increase.
8. `sparse-history` — curated regression-friendly upper-body subset using `inverted-row`, `banded-lat-pulldown`, and `prone-y-raise` so sparse history does not freeze a limited-hang profile to pull-ups.
9. `no-strength-candidates` — harness-only typed-failure case restricted to the cardio-only `walking` entry so the real planner returns `NO_STRENGTH_CANDIDATES`.
10. `reviewed-enabled-bodyweight` — copies six real bundled DRAFT records to unmistakably synthetic in-memory approvals, composes `BUILD` with an empty `PRIMARY_ONLY_V1` ledger, and proves eligibility plus dose/effort/rest guidance stay inside that reviewed bodyweight pool.
   Its expected selection is `bodyweight-squat`, `glute-bridge`, and `plank`: adding
   legacy timed programming makes plank rank ahead of the prior push accessory for this
   leg split. This is synthetic test approval only; production metadata stays DRAFT.
11. `reviewed-enabled-no-approved` — leaves every bundled record DRAFT and proves the enabled policy returns `REVIEWED_ELIGIBILITY_NO_CANDIDATES` with `NO_APPROVED_METADATA` and no legacy fallback.

## Replay semantics and asserted invariants

Each replay attempt uses a fresh `FakeWorkoutPlanner`. That is intentional: the planner keeps an in-memory `generationCounter`, and reusing one instance would rotate the split between attempts. Replay comparisons normalize only `GeneratedWorkout.id`, which is UUID-backed; every other generated field must remain identical.

The corpus suite asserts:

- deterministic output equality across two fresh replays, normalized only for the generated workout ID;
- fixture schema and evaluator support all current typed planner failures (`NO_CANDIDATES`, `NO_STRENGTH_CANDIDATES`, `NO_CANDIDATES_FOR_ANY_SPLIT`, `REVIEWED_ELIGIBILITY_NO_CANDIDATES`) and the reviewed failure's typed aggregate cause;
- the committed eleven-fixture manifest exercises `NO_STRENGTH_CANDIDATES` and `REVIEWED_ELIGIBILITY_NO_CANDIDATES`; focused planner tests cover `NO_CANDIDATES` and `NO_CANDIDATES_FOR_ANY_SPLIT`;
- legality of every selected exercise against the bundled catalog, the real filter result, and any curated allowed-ID subset;
- non-mutation of the full `WorkoutGenerationContext` input;
- type-valid prescriptions and no-invented-load behavior through the real prescription factory;
- reviewed-enabled prescriptions consume composed program state, attach deterministic
  effort/rest guidance, never increase base sets, and preserve no-invented-load behavior;
- capability invariance for the current production legacy path by comparing
  `limited-capability` with an all-`COMFORTABLE` control;
- parity checks that the lightweight catalog projection preserves planner-consumed fields for representative entries without broadening into full parser duplication.

`PlannerFixtureCorpusTest` avoids a second inaccurate strength classifier. It checks fixture-construction premises and curated candidate subsets, while typed strength/failure behavior is left to the real planner evaluator.

## Test entry points

Focused contract / corpus coverage:

```bash
./gradlew testDebugUnitTest \
  --tests '*PlannerFixture*' \
  --tests '*FakeWorkoutPlannerTest' \
  --tests '*ExerciseFilterTest' \
  --rerun-tasks --no-daemon
```

Repository hygiene for this work still includes:

```bash
git diff --check
```

## Maintenance expectations

When the bundled planner catalog changes, update the pinned corpus `catalogVersion` commit and keep the exact persona roster/count assertions aligned with `manifest.txt`. Bump `schemaVersion` or `policyVersion` only when the fixture wire format or supported expectation contract truly changes.
