# Planner evaluation corpus

## Purpose and staged boundary

The planner evaluation corpus is a JVM-only, test-only harness around the current deterministic `FakeWorkoutPlanner`. It documents representative planner inputs, builds a real `WorkoutGenerationContext`, and replays planner behavior without changing production code.

The boundary is explicit:

- production still owns equipment filtering, split selection, prescription generation, and typed failures;
- legacy corpus fixtures can optionally narrow the legal candidate pool with curated `allowedExerciseIds`;
- reviewed-enabled fixtures exercise the production eligibility and state-based
  prescription policies with explicitly synthetic in-memory approvals while the production
  rollout flag remains disabled; their weekly ledger is reconstructed from the fixture's own
  declared completed sessions by the production `WeeklyDoseLedgerCalculator`, so a fixture
  claiming prior weekly exposure is asserting against accounting the app actually produces;
- capability inputs and `TrainingConstraint` metadata remain inert on legacy fixtures,
  matching the production-disabled rollout, and become policy inputs only on
  reviewed-enabled fixtures.

These fixtures therefore model the planner **inside** a curated legal set. They do not claim that the current planner discovered capability, safety, or persona appropriateness on its own.

## Whole-program assertions

Every persona whose replay produces a proposal is now also validated as a complete program.
Each successful persona is replayed twice, and the proposal from the first replay is validated
twice against the exact context that produced it: once with repair disabled, which is the only
evidence that the planner's own output was correct, and once with the single permitted repair
pass, which is what `TodayViewModel` would be allowed to display. The second replay is not
separately validated — it is already asserted identical to the first in every field but the
UUID id, and the validator is pure, so a second verdict could not differ. A typed no-plan
fixture produces no workout, so it asserts its failure reason instead of a validation verdict.
Both verdicts are recorded, so a repaired proposal can never stand in for a valid one. `expected.wholeProgramOutcome` declares which of
the two a persona expects, and a `REPAIRED` persona must additionally name the exact violation
codes it was repaired from.

The dedicated rule-by-rule coverage stays outside this corpus, in
`ProgramValidatorTest`, `ProgramValidatorAggregateDoseTest`, `ProgramValidatorRepairTest`,
`RecommendationContextIdentityTest`, `WorkoutDurationEstimatorTest`,
`PlannerLocaleInvarianceTest`, `TodayViewModelTest`, and the Room and archive
instrumentation suites. Those follow the
[evidence-to-rule mapping](research/2026-08-29-training-science-evidence-review.md#validation-scope-clarification-2026-09-05)
and cover ID and candidate membership, reviewed provenance on the enabled path, explicit
constraints, prescription shape, load provenance, deterministic failure and the single
repair pass, snapshot versioning, stale-context refusal at start, and the no-partial-start
guarantee. The aggregate weekly assertions test the configured product allowance across the
entire proposal — including several exercises sharing one direct primary, an exactly
reached allowance, and one set over it — rather than each prescription against the same
completed ledger.

Advertised focus is asserted separately, in `WorkoutFocusCoverageTest`, and against the
shipped predicate rather than a classifier written for the test: the committed band-only
reproduction, secondary-only pools for every split, genuine bodyweight/dumbbell/machine
/full-gym push days, every prioritised muscle across every split, rotation and
regeneration over a full cycle, and the original misleading proposal being refused with
`UNSUPPORTED_WORKOUT_FOCUS`. That rule is a software invariant about one label — the
[focus contract](architecture.md#advertised-focus) — and not a coverage requirement over
patterns. `ReviewedCatalogCoverageTest` asserts the same predicate on every reviewed case,
keeping its stricter reviewed pattern premise as a recorded observation rather than a
product rule.

Duplicate exercise/family and movement-pattern coverage are asserted as **declared**
session constraints, both in their declared and their default-inert form; duration is asserted
against the named `DURATION_ESTIMATOR_V1` and its ±1-minute tolerance, with an explicit
case proving a proposal far from the requested duration is not rejected for that.
`PRIMARY_ONLY_V1`, set caps, RIR bands, and rest seconds are product-policy expectations,
not medical thresholds; a malformed ledger and unrepresentable arithmetic are asserted as
distinct from an exceeded allowance. No fixture encodes a numeric physiological fatigue
budget, timestamp-only overload or readiness inference, a mandatory weekly minimum, or an
automatic volume increase, and no recency rule exists to assert.

Passing fixtures/CI establishes software conformance, not scientific or clinical
validation of WallCrawl's complete algorithm. Approval provenance and expert review do
not change that distinction.

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
- optional `completedSessions`, the fixture's own completed history for its accounting week
- `expected`

A reviewed-enabled fixture always composes a program state; one that declares no
`completedSessions` composes a genuinely empty week through the same calculator. Legacy
fixtures compose none at all, which is exactly what production does while the reviewed gate
is disabled.

`completedSessions` is the ledger input and is deliberately separate from `exerciseHistory`,
which is the planner's per-exercise load view and credits nothing. Each entry names an `id`,
a `completedDayOffset` in `0..6`, and exercises whose `sets` each spell out a `SetType` and
`isCompleted`. Timestamps are derived from the corpus week's Monday rather than written into
a fixture, so a declared day can never drift outside the `TrainingWeek` being reconstructed.
At most eight sessions are accepted — a harness bound matched to the fixture history limit
and deliberately far below the calculator's own 1,000-session ceiling, whose bounds
`WeeklyDoseLedgerCalculatorTest` owns. The field
requires `reviewedEligibility`, because the legacy path composes no weekly ledger at all.

`expected` additionally accepts `wholeProgramOutcome`, which reuses the production
`RecommendationOutcome` vocabulary (`VALID`, the default, or `REPAIRED`),
and `wholeProgramRepairReasonCodes`. The codes are required for `REPAIRED` and rejected
otherwise, and `REPAIRED` itself requires `reviewedEligibility`, because the only repairable
violation — an exceeded configured weekly allowance — can only arise where aggregate dose
accounting runs.

## Version and reference contract

The version fields are separate and enforced deliberately:

- `schemaVersion` is the fixture wire-format version. The loader accepts only `1`.
- `policyVersion` is the supported corpus expectation contract. `PlannerFixtureContextFactory.create()` accepts only policy version `4` for corpus evaluation. Version 4 is what added the reconstructed weekly ledger for reviewed-enabled personas and whole-program validation of every persona that produces a proposal, along with the two `expected.wholeProgram*` fields; a fixture authored against version 3 was never evaluated that way and is refused rather than reinterpreted.
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
- `completedSessions[].exercises[].exerciseId`
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
  - `titleIdentityContains`
  - `maxTargetSetsPerExercise`
  - `wholeProgramOutcome`
  - `wholeProgramRepairReasonCodes`

Failure fixtures may therefore assert only the typed outcome they expect from the real planner.

## Bundled catalog projection boundary

`PlannerFixtureContextFactory` does **not** reimplement the full packaged catalog parser. It intentionally maps only the `Exercise`, `ExerciseProgrammingMetadata`, and `ReviewedExerciseMetadata` fields currently consumed by:

- `ExerciseFilter`
- `FakeWorkoutPlanner`
- `DefaultExercisePrescriptionFactory`
- `ExerciseEligibilityPolicy`

That includes exercise identity, canonical muscles, listed equipment, type, stretch flag, and reviewed programming metadata used for filtering, split matching, ordering, and prescriptions, including the authored `clearedTrainingConstraints` the eligibility policy reads. The harness does **not** populate unrelated attribution/source data solely for tests.

The projection validates the same type-dependent legacy rep-range contract as the
Python importer and Android parser, using shared fixtures from
`app/src/androidTest/assets/programming-validation-fixtures.json`. It preserves missing
or explicit-null timed ranges as null and rejects fabricated timed ranges and absent rep
ranges. Raw range JSON in the fixture preserves numeric notation rather than letting a JSON
serializer change it. Replay snapshots re-materialise only the branches that can hold a
caller's mutable collection; value types such as `RepRange` are deliberately carried by
reference, and the snapshot probe pins each of those decisions.
`TimedHoldProgrammingTest` independently pins all 14 duration strength IDs through
actual single-candidate planner calls, tests excluded timed work even with metadata,
replays a timed-only persona through the same harness, and pins baseline rep prescriptions.

Full packaged catalog validity remains the responsibility of the dedicated importer/instrumentation tests, especially `WorkoutGuideCatalogParserTest`.

## Persona coverage

The manifest currently contains fourteen fixtures:

1. `bodyweight-beginner` — conservative curated bodyweight beginner subset (`push-up`, `knee-push-up`, `bodyweight-squat`, `dead-bug`) requiring at least one beginner push variant.
2. `band-only` — resistance-band-only coverage after the fixed-anchor gate: `banded-dead-bug` is required, and cable-only pull work is excluded by the real filter alongside every anchor-dependent band variation, `banded-row` included.
3. `machine-only` — machine-only strength coverage with a confirmed machine press load.
4. `full-gym-advanced` — broad full-gym strength-plus-hypertrophy coverage against the full bundled candidate pool.
5. `returning-user` — curated lower-demand full-body subset for re-entry (`incline-dumbbell-press`, `one-arm-dumbbell-row`, `goblet-squat`, `glute-bridge`, `dead-bug`), preserving the `RE_ENTRY` title identity (`WorkoutTitleSpec.isReEntry`), a max-two-set cap, the confirmed incline press load, and keeping `ab-wheel` / `single-leg-romanian-deadlift` out of the curated pool.
6. `limited-capability` — curated dumbbell/bench push subset (`dumbbell-bench-press`, `dumbbell-shoulder-press`, `incline-dumbbell-press`, `dumbbell-lateral-raise`) that keeps capability metadata present but inert for planner eligibility and asserts the shoulder-press target load from history / confirmed data.
7. `mixed-unit-history` — kilogram history coverage proving prior KG history is honored and the existing load is preserved when recent sets do not justify an increase.
8. `sparse-history` — curated regression-friendly upper-body subset of `inverted-row` and `prone-y-raise` so sparse history does not freeze a limited-hang profile to pull-ups, with `banded-lat-pulldown` held out as the forbidden control now that its anchor is unconfirmed.
9. `no-strength-candidates` — harness-only typed-failure case restricted to the cardio-only `walking` entry so the real planner returns `NO_STRENGTH_CANDIDATES`.
10. `reviewed-enabled-bodyweight` — copies six real bundled DRAFT records to unmistakably synthetic in-memory approvals, composes `BUILD` with an empty `PRIMARY_ONLY_V1` ledger, and proves eligibility plus dose/effort/rest guidance stay inside that reviewed bodyweight pool.
   Its expected selection is `bodyweight-squat`, `glute-bridge`, and `plank`: adding
   legacy timed programming makes plank rank ahead of the prior push accessory for this
   leg split. This is synthetic test approval only; production metadata stays DRAFT.
11. `reviewed-enabled-no-approved` — leaves every bundled record DRAFT and proves the enabled policy returns `REVIEWED_ELIGIBILITY_NO_CANDIDATES` with `NO_APPROVED_METADATA` and no legacy fallback.
12. `reviewed-enabled-uncleared-joint-constraint` — the `reviewed-enabled-bodyweight` pool with
    `SHOULDER_SENSITIVE` selected. The fixture declares no
    `syntheticClearedTrainingConstraints`, so the synthetic approvals clear nothing — matching
    the bundled records, which clear nothing either — and the enabled policy returns
    `REVIEWED_ELIGIBILITY_NO_CANDIDATES` with `TRAINING_CONSTRAINTS_REMOVED_ALL` and no legacy
    fallback. This is the shipped behavior for a joint-sensitive profile.
13. `reviewed-enabled-cleared-joint-constraints` — `KNEE_SENSITIVE` and `WRIST_SENSITIVE`
    selected together against a core-priority bodyweight pool whose synthetic approval **sets**
    `clearedTrainingConstraints` to exactly those two, via the fixture's
    `syntheticClearedTrainingConstraints`; it replaces the authored value rather than copying it. It proves the
    combined-restriction path reaches a raw-valid proposal when — and only when — every
    selected sensitivity is explicitly cleared. The clearance is synthetic test data in the
    same sense as the approval itself; no bundled record clears anything.
14. `concurrent-activity` — a dumbbell/bench/cardio owner in `BUILD` whose accounting week
    already holds both logged resistance work and an aerobic session. Its declared history is
    a Tuesday session of twelve logged chest sets (one warm-up and one unfinished set among
    them) and a Thursday cycling session of two completed sets. The reconstructed ledger
    therefore credits `Chest` exactly 10, records `Shoulders` and `Triceps` involvement
    separately at 10 each, and books the cycling work as 2 `MISSING_REVIEWED_METADATA` sets
    rather than guessing at the legs it obviously involves; its logged shape follows the
    catalog entry, so aerobic work is never recorded as a weighted set of repetitions.
    The two remaining chest sets are
    what makes this the corpus's `REPAIRED` case: two proposed chest exercises each spend the
    same remainder, the raw proposal is refused with `WEEKLY_ALLOWANCE_EXCEEDED`, and the one
    permitted repair pass reduces each to a single set. `cycling` survives the real equipment
    filter — the persona owns cardio equipment and can still browse or template it — but it
    has no approved metadata, so the reviewed gate keeps it out of the candidate set and no
    distance work reaches an automatic strength slot.

## Replay semantics and asserted invariants

Each replay attempt uses a fresh `FakeWorkoutPlanner`. That is intentional: the planner keeps an in-memory `generationCounter`, and reusing one instance would rotate the split between attempts. Replay comparisons normalize only `GeneratedWorkout.id`, which is UUID-backed; every other generated field must remain identical.

The corpus suite asserts:

- deterministic output equality across two fresh replays, normalized only for the generated workout ID;
- fixture schema and evaluator support all current typed planner failures (`NO_CANDIDATES`, `NO_STRENGTH_CANDIDATES`, `NO_CANDIDATES_FOR_ANY_SPLIT`, `REVIEWED_ELIGIBILITY_NO_CANDIDATES`) and the reviewed failure's typed aggregate cause;
- the committed fourteen-fixture manifest exercises `NO_STRENGTH_CANDIDATES` and `REVIEWED_ELIGIBILITY_NO_CANDIDATES`; focused planner tests cover `NO_CANDIDATES` and `NO_CANDIDATES_FOR_ANY_SPLIT`;
- legality of every selected exercise against the bundled catalog, the real filter result, and any curated allowed-ID subset;
- non-mutation of the full `WorkoutGenerationContext` input;
- type-valid prescriptions and no-invented-load behavior through the real prescription factory;
- reviewed-enabled prescriptions consume composed program state, attach deterministic
  effort/rest guidance, never increase base sets, and preserve no-invented-load behavior;
- capability invariance for the current production legacy path by comparing
  `limited-capability` with an all-`COMFORTABLE` control;
- parity checks that the lightweight catalog projection preserves planner-consumed fields for representative entries without broadening into full parser duplication;
- whole-program validation of every successful persona's proposal, raw and repair-permitted, with a
  repair that only ever reduced sets and never below one.
  A typed no-plan fixture produces no proposal, so it asserts its failure reason instead.

`PlannerFixtureCorpusTest` avoids a second inaccurate strength classifier. It checks fixture-construction premises and curated candidate subsets, while typed strength/failure behavior is left to the real planner evaluator.

## Full-catalog content-review coverage

`ReviewedCatalogCoverageTest` supplements, rather than silently enlarges, the
fourteen-fixture manifest. Its 17 declared profiles each exercise an all-DRAFT
structural upper bound, a separate AI-ready subset (both using explicitly synthetic
in-memory approvals), and disabled-mode invariance with reviewed metadata stripped.
The [coverage report](reviewed-catalog-coverage.md) records actual candidate counts,
selected IDs, mode/state, raw whole-program validation and typed no-plan outcomes.

This suite reads the per-ID evidence ledger for content readiness; readiness is not
human approval. Known pending records are not promoted in the AI-ready experiment.
A separate real-planner sole-candidate probe covers every catalog ID, without
equating that result to full-pool selection. The band-only case remains an explicit
negative coverage regression: there is still no genuine band push, and a schema-valid
proposal never proved otherwise. Since the [focus contract](architecture.md#advertised-focus)
landed, that case additionally asserts a truthful non-`PUSH` label and a reported
unavailable `Chest` priority. The missing push and fixed-anchor representation remain
open — correcting the label supplied neither — and selected joint restrictions continue
to fail closed without a fallback.

## Release-gate traceability

Each release requirement maps to the suite that actually asserts it. Suites already covering
a requirement are referenced rather than duplicated, and nothing below is a claim about
physiology: a policy row names a versioned WallCrawl choice, an invariant row names internal
consistency, and passing either demonstrates software conformance only.

| Release requirement | Classification | Asserted by |
| --- | --- | --- |
| Concurrent activity coexists with resistance work without becoming muscle dose | Product policy (`PRIMARY_ONLY_V1`) | `PlannerFixtureTest.concurrentActivityPersona_creditsResistanceWorkAndTypesTheAerobicWorkItCannotCredit` |
| Unsupported cardio/distance work stays out of automatic strength slots | Software invariant (`Exercise.isStrengthWork`, reviewed gate) | `PlannerFixtureTest.concurrentActivityPersona_keepsUnsupportedActivityOutOfAutomaticStrengthSlots`, `no-strength-candidates`, `WorkoutFocusCoverageTest` |
| Approved direct-primary attribution, descriptive secondary involvement, typed omissions | Product policy | `WeeklyDoseLedgerCalculatorTest`, `BundledCatalogLedgerAttributionTest`, the `concurrent-activity` persona |
| Completed work versus warm-ups, open/stopped/skipped sets, active/cancelled sessions | Product policy + software invariant | `WeeklyDoseLedgerCalculatorTest`, and the warm-up and unfinished set inside `concurrent-activity` |
| Several proposed exercises sharing one muscle's remaining allowance | Product policy (`STATE_BASED_DOSE_EFFORT_REST_V1`) | `ProgramValidatorAggregateDoseTest`, the `concurrent-activity` `REPAIRED` case |
| Below, exactly at, above, and an already-exhausted allowance | Product policy | `ProgramValidatorAggregateDoseTest`, `StateBasedTrainingPolicyTest`, `PlannerFixtureTest.aProposalWellUnderTheConfiguredAllowanceIsAcceptedWithoutAWeeklyMinimum` |
| Per-exercise and relevant-capability set caps | Product policy | `StateBasedTrainingPolicyTest`, `returning-user`'s `maxTargetSetsPerExercise` |
| Corrupt ledger data and arithmetic overflow distinct from a full allowance | Software invariant | `ProgramValidatorAggregateDoseTest` |
| ISO-week and time-zone boundaries | Software invariant (`TrainingWeek`) | `TrainingWeekTest`, `WeeklyDoseLedgerCalculatorTest` |
| Raw-valid, repaired-valid and expected-failure stay distinguishable | Software invariant | `PlannerFixtureTest.evaluateCorpus_validatesTheWholeProposalAgainstTheContextThatProducedIt`, `ProgramValidatorRepairTest` |
| One bounded repair pass preserving candidates and hard constraints; none at start | Software invariant | `ProgramValidatorRepairTest`, `TodayViewModelTest` |
| No invented load; valid history/confirmed-load provenance; mixed units | Software invariant | `PlannerFixtureTest`, `mixed-unit-history`, `ProgramValidatorTest` |
| Candidate membership, explicit exclusions, reviewed provenance | Software invariant | `ProgramValidatorTest`, `ProgramValidatorAggregateDoseTest`, corpus legality assertions |
| Deterministic replay and input non-mutation | Software invariant | `PlannerFixtureTest.evaluateCorpus_enforcesDeterminismAndPlannerInvariants` |
| Band-only chest priority produces no misleading `PUSH` label | Software invariant (focus contract) | `WorkoutFocusCoverageTest`, `ReviewedCatalogCoverageTest`, `band-only` |
| Fixed anchors require explicit confirmation; `banded-row` stays unresolved | Product policy | `FixedAnchorBandEligibilityTest`, `band-only`, `sparse-history` |
| Typed no-plan outcomes preserved, including zero approved metadata | Software invariant | `reviewed-enabled-no-approved`, `ReviewedCatalogCoverageTest` |
| Stale context refused at start; no partial start | Software invariant | `RecommendationContextIdentityTest`, `TodayViewModelTest` |
| Weekly dose is counted in sets, never derived from the legacy `fatigueScore` | Rejected inference | `PlannerFixtureTest.weeklyDoseIsCountedInSetsRatherThanDerivedFromFatigueScores` recomputes completed and proposed exposure from set counts alone and requires equality; `PlannerFixtureTest.prospectiveDoseAccountingIgnoresTheLegacyFatigueScore` adds the ordinal-invariance half. The ordinal's real ranking role is deliberately not asserted against. |
| Readiness is never inferred from elapsed time alone | Rejected inference | `PlannerFixtureTest.concurrentActivityPersona_readsTheWeekRatherThanTheTimeInsideIt` |
| No forced weekly minimum and no automatic volume increase | Rejected inference | `PlannerFixtureTest.aProposalWellUnderTheConfiguredAllowanceIsAcceptedWithoutAWeeklyMinimum`, the repair-only-reduces assertion |
| A capability answer is not a record of activity | Rejected inference | `PlannerFixtureTest.theContinuousActivityAnswerIsNotARecordOfAerobicActivity`, with `aCapabilityTheReviewedGateDoesReadChangesTheProposal` as its sensitivity control |
| Locale and gender independence of canonical training decisions | Software invariant | `PlannerLocaleInvarianceTest` |

The `concurrent-activity` assertions carry their own sensitivity control:
`concurrentActivityPersona_isChangedWhenTheResistanceSessionIsRemoved` removes the logged
resistance work at a constant lifetime workout counter and shows the proposal really does
change, so the aerobic-work control above is not passing vacuously.

Each rejected-inference row is falsifiable in its own way, and the differences are worth
stating rather than glossing:

- The dose row needs no paired control: it recomputes completed and proposed exposure from
  set counts and requires equality, so any arithmetic that mixed a fatigue value into dose
  breaks it.
- The under-target row asserts an outcome directly — a proposal well below the configured
  allowance is accepted and gains no sets — so there is nothing for it to compare with itself.
- The capability-answer row is an invariance comparison, and its ledger half is invariant
  **by construction**: `composeWeeklyLedger` reads the declared sessions and the catalog and
  never the profile, so no capability edit could change it. Its plan half is not, and
  `aCapabilityTheReviewedGateDoesReadChangesTheProposal` is its sensitivity control: avoiding
  `BALANCE_WITHOUT_SUPPORT`, which `dumbbell-lateral-raise` actually requires, removes that
  candidate and changes the plan. Without that control the invariance could be misread as
  proof that capability answers are inert.
- The readiness-from-time row is also an invariance comparison. Moving a session inside the
  week does produce different timestamps, and `TrainingWeek.contains` is what accepts them —
  a session moved outside the week is rejected by the calculator, which
  `WeeklyDoseLedgerCalculatorTest` asserts directly and the loader's `0..6` bound prevents a
  fixture from expressing. Nothing downstream reads the timestamp, which is the point.

Three limits are recorded rather than worked around.

The corpus has no way to declare an active or cancelled session, because the calculator
rejects one by contract and that rejection is asserted directly in
`WeeklyDoseLedgerCalculatorTest`. It also has no gender field, so gender independence stays
where it is already asserted, against a real profile, in `PlannerLocaleInvarianceTest`.

Third, declared `completedSessions` are a **ledger input only**. Production's
`WorkoutGenerationContextBuilder` derives four further context fields from completed history —
`recentWorkoutHistory`, `recentlyTrainedMuscles`, capability evidence and prior rest
preferences — and the harness deliberately leaves all four at their empty defaults. That is
safe today and not merely convenient: no planner in `src/main` reads the first two as a
ranking or blocking input, deriving `recentlyTrainedMuscles` would pull a clock into a corpus
whose whole purpose is determinism, and `CapabilityEvidencePolicy` requires `feltManageable`
plus two comparable sessions per exercise, neither of which these bounded synthetic sets can
express. Before this contract version the reviewed fixtures declared no completed history at
all, so the empty values were trivially consistent; now they are a stated divergence, and any
future rule that reads those fields has to close it.

## Versions this gate runs under

| Identity | Value | Where it is enforced |
| --- | --- | --- |
| Fixture wire format | `schemaVersion` 1 | `PlannerFixtureLoader` |
| Corpus expectation contract | `policyVersion` 4 | `PlannerFixtureContextFactory.SUPPORTED_CORPUS_POLICY_VERSION` |
| Bundled catalog | commit `ba0b709cb20430361b2cb33aaadd20998164a916`, `schemaVersion` 1, 302 exercises | fixture `catalogVersion` versus the catalog's `source.commit` |
| Reviewed metadata | review policy version 1; 211 `DRAFT`, 0 `APPROVED` | `PlannerFixtureCorpusTest`, `ReviewedExerciseMetadataTest` |
| Training policy | `STATE_BASED_DOSE_EFFORT_REST_V1` | `StateBasedTrainingPolicyDefaults.V1` |
| Ledger policy | `PRIMARY_ONLY_V1` | `WeeklyDoseLedgerCalculator` |
| Program state policy | `PROGRAM_STATE_V1` | `TrainingProgramState` |
| Validator | `WHOLE_PROGRAM_V1` with `DURATION_ESTIMATOR_V1` | `ProgramValidator` |
| Importer pin | `tools/workout-guide/import-config.json` `sourceCommit` | `tools/workout-guide/check_pinned_catalog.py` |

These are separate from the Room schema and archive format versions, which this corpus does
not touch.

## Where the gate runs

| Check | Pull request and `main` | Tag release |
| --- | --- | --- |
| Pinned-upstream catalog regeneration (`check_pinned_catalog.py`) | yes | no |
| Catalog importer tests (`tools/workout-guide`) | yes | no |
| Release tooling tests (`tools/release`) | yes | yes |
| JVM unit tests including this corpus, lint, debug build | yes | yes |
| API 36 instrumentation suite | yes | yes |
| Release version validation and prerelease publication | no | yes |

The catalog regeneration check and the importer suite run on pull requests and `main` only;
a tag build does not re-run them. That means a release carries their result only when the
tagged commit is one that already passed CI on `main`. Nothing in the workflows enforces
that: `release.yml` triggers on any `v*` tag with no ancestry requirement, so a tag cut from
a commit that never reached `main` would publish without the pinned-upstream regeneration
check having run at all. This is a description of current behaviour, not a guarantee; making
it one would need tag protection or an explicit ancestry check, which this package did not
add.

## Test entry points

Focused contract / corpus coverage:

```bash
./gradlew testDebugUnitTest \
  --tests '*PlannerFixture*' \
  --tests '*FakeWorkoutPlannerTest' \
  --tests '*WorkoutFocusCoverageTest' \
  --tests '*ExerciseFilterTest' \
  --tests '*ProgramValidator*' \
  --tests '*WeeklyDoseLedgerCalculatorTest' \
  --tests '*TrainingWeekTest' \
  --rerun-tasks --no-daemon
```

Repository hygiene for this work still includes:

```bash
git diff --check
```

## Maintenance expectations

When the bundled planner catalog changes, update the pinned corpus `catalogVersion` commit and keep the exact persona roster/count assertions aligned with `manifest.txt`. Bump `schemaVersion` or `policyVersion` only when the fixture wire format or supported expectation contract truly changes.

Adding an optional field has so far been an expectation-contract change rather than a wire
change, which is why `reviewedEligibility`, `requiredAnyExerciseIdGroups`,
`automaticEligibilityFailure`, `completedSessions` and the two `wholeProgram*` fields all
arrived under `schemaVersion` 1. Both fixture versions are independent of the runtime
training, ledger, catalog, reviewed-metadata, Room and archive versions; changing one of
those is never a reason to move either of these.
