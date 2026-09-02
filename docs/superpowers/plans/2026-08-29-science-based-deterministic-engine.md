# Science-Based Deterministic Engine Implementation Plan

**Goal:** Build a fully local, auditable multi-week resistance-training engine whose eligibility, weekly dose, effort, progression, and fallbacks follow the signed evidence doctrine.

**Architecture:** A reviewed metadata gate feeds capability/equipment eligibility, a `PRIMARY_ONLY_V1` weekly ledger, calibration-state policy, deterministic ranking and prescription compilation, and session/weekly validation. Immutable snapshots and versioned reason codes make every recommendation replayable.

**Tech Stack:** Kotlin, Room, Coroutines/Flow, Jetpack Compose, Python catalog tooling, JUnit 4, Truth, Turbine, Android instrumentation.

---

## Core Contracts

```kotlin
enum class AdaptationState {
    NEEDS_ONBOARDING,
    UNCALIBRATED,
    INITIATE,
    BUILD,
    DEVELOP,
    HOLD,
    RETURNING,
    DELOAD_OFFERED,
    RECALIBRATE
}
enum class RestClass { SHORT, MODERATE, LONG }

data class EffortTarget(val minRir: Int?, val maxRir: Int?)

data class WeeklyDoseLedger(
    val policyVersion: Int,
    val weekStartEpochDay: Long,
    val directPrimarySets: Map<String, Int>,
    val secondaryInvolvement: Map<String, Int>
)

data class EligibilityDecision(
    val eligible: Boolean,
    val reasons: Set<EligibilityReason>
)

sealed interface ProgramViolation {
    data class UnknownOrUnreviewedId(val exerciseId: String) : ProgramViolation
    data class HardConstraint(val exerciseId: String) : ProgramViolation
    data class DuplicateFamily(val family: String) : ProgramViolation
    data class InvalidDose(val exerciseId: String) : ProgramViolation
    data class UnconfirmedLoad(val exerciseId: String) : ProgramViolation
    data class DurationMismatch(val planned: Int, val requested: Int) : ProgramViolation
    data class WeeklyLedgerOverflow(val muscle: String) : ProgramViolation
}
```

### Task 1: Replace Speculative Metadata with Reviewed Categorical Metadata

**Status:** Metadata/schema/parser foundation complete. The initial 37-entry
cohort remains `DRAFT` pending human review. Task 2's reviewed-only eligibility
path is now implemented but production-disabled, so current planner behavior is
unchanged until human signoff and explicit enablement.

**Files:**

- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Exercise.kt`
- Create: `tools/workout-guide/reviewed-metadata.json`
- Modify: `tools/workout-guide/import_catalog.py`
- Create: `tools/workout-guide/review-schema.json`
- Test: `tools/workout-guide/test_programming_overrides.py`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/exercise/workoutguide/WorkoutGuideCatalogParserTest.kt`

- [x] Write failing tests requiring direct-primary muscle, pattern, complexity, progression family, approved regressions/substitutions, capability requirements, support, impact, equipment, and review provenance.
- [x] Verify tests reject unknown graph IDs, cycles, missing provenance, and numeric joint/SFR/axial/body-mass fields.
- [x] Add categorical types and `ReviewProvenance(reviewerRole, rationaleOrSource, reviewedAtEpochMillis, schemaVersion, policyVersion)`.
- [ ] Review band, machine, supported, bodyweight, and timed-hold families before enabling the automatic gate.
- [x] Add an AI-authored `DRAFT` cohort spanning band, machine, supported, bodyweight, and timed-hold families for later human review.
- [x] Run Python/importer/parser tests and commit the reviewed metadata foundation.

#### Timed programming prerequisite (2026-09-02)

The [timed-hold programming milestone](../../timed-hold-programming.md) adds nullable,
type-validated rep ranges and complete legacy programming for the exact 14 eligible
duration exercises. All 117 rep records remain unchanged; the legacy count is now 131.
This completes the README prerequisite, not the unchecked human-review step above.
The 37 reviewed entries remain DRAFT and production eligibility remains disabled.

### Task 2: Enforce Reviewed-Only Eligibility and Calibration Complexity

**Status:** Shipped behind an explicit production-disabled feature flag. The pure policy,
typed decisions/failures, context-builder integration, synthetic enabled fixtures, and
manual/full-catalog regression coverage are present. Production remains on the legacy
path because the bundled cohort is still 37 `DRAFT` / 0 `APPROVED`; enabling the gate
requires explicit human metadata signoff and a separate availability/persona decision.

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ExerciseEligibilityPolicy.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/exercise/ExerciseFilter.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutGenerationContext.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ExerciseEligibilityPolicyTest.kt`

- [x] Write failing tests proving equipment, exclusions, constraints, capability `AVOID`, low-impact policy, and reviewed metadata are hard requirements.
- [x] Add the complete `AdaptationState` enum from Core Contracts.
- [x] Temporarily block advanced-complexity automatic exercises only in UNCALIBRATED/RETURNING when no demonstrated family history or an actually available supported regression exists.
- [x] Preserve full-catalog browse/manual workflows and typed no-plan reasons.
- [x] Run focused tests and commit `feat: enforce reviewed workout eligibility`.

### Task 3: Add the PRIMARY_ONLY_V1 Weekly Ledger

**Status:** Shipped, composed, and consumed on the reviewed-enabled path. The ledger is
built and cached at schema 10 and
documented in [docs/weekly-dose-ledger.md](../../weekly-dose-ledger.md). `AdaptationState`
landed with Task 2, and `TrainingProgramState` now composes it with the ledger and rides on
the generation context when reviewed eligibility is enabled. Task 4 reads direct-primary
counts only to cap future prescriptions; legacy planner selection and progression remain
unchanged.

`AdaptationStatePolicy` deliberately derives only `UNCALIBRATED` and `RETURNING`. Any further
state lifts the advanced-complexity ceiling in `ExerciseEligibilityPolicy`, which is
allow-by-default on exactly those two. Task 4 defines dose behavior for every state but
does not derive another state; comparable-outcome transitions remain Task 6. A regression
test pins that coupling.

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/WeeklyDoseLedger.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/TrainingWeek.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WeeklyDoseLedgerCalculator.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/LedgerSourceFingerprint.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/WeeklyDoseLedgerStateEntity.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/database/dao/WeeklyDoseLedgerDaos.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WeeklyDoseLedgerRepository.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WeeklyDoseLedgerPayload.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WeeklyDoseLedgerCalculatorTest.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration9To10Test.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/WeeklyDoseLedgerRepositoryTest.kt`

- [x] Write failing tests showing one completed work set credits one designated direct-primary muscle once and secondary involvement receives no dose credit.
- [x] Add `WeeklyDoseLedger(policyVersion, weekStartEpochDay, timeZoneId, catalogVersion, reviewPolicyVersion, directPrimarySets, secondaryInvolvement, unattributedWorkSets)`.
- [x] Derive ledgers from immutable completed sessions; do not increment mutable counters during generation.
- [x] Persist only a fingerprinted, reconstructable cache; completed history remains the authority and a deleted or corrupted cache changes no result.
- [x] Count unknown, missing, and `DRAFT` metadata as typed omissions instead of guessing from legacy muscles, names, or programming.
- [x] Resolve ISO weeks through `ZonedDateTime` in an injected zone, including daylight-saving weeks.
- [x] Run unit/migration tests and commit `feat: add weekly training dose ledger`.

### Task 4: Implement State-Based Dose, Effort, and Rest Policy

**Status:** Shipped behind the production-disabled reviewed eligibility path. A pure
`StateBasedTrainingPolicy` returns applied guidance, typed no-guidance, or typed failure;
it validates approved provenance and composed state/ledger versions, caps but never
increases base sets, and never changes a load. `ExercisePrescription` persists nullable
effort and classified rest guidance through Room schema 11. `AdaptationStatePolicy`
continues to derive only `UNCALIBRATED` and `RETURNING`.

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/StateBasedTrainingPolicy.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/TrainingGuidance.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/ExercisePrescription.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/DefaultExercisePrescriptionFactory.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/StateBasedTrainingPolicyTest.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration10To11Test.kt`

- [x] Write failing tests: small conservative exposure; weekly ledger drives remaining dose; session count/frequency never masquerades as dose.
- [x] Add nullable `EffortTarget(minRir, maxRir)` and `RestClass { SHORT, MODERATE, LONG }`.
- [x] Use 2-4 RIR guidance in conservative states or for a relevant `LIMITED` capability, 1-2 for established strength, and 1-3 for established general/hypertrophy; never auto-default failure.
- [x] Resolve rest classes through versioned editable product policy and preserve explicit per-exercise user preferences.
- [x] Return typed exhaustion rather than zero sets, and fail closed on unapproved or version-mismatched metadata/ledger input.
- [x] Prove capability/state never change confirmed/history-only load and keep the legacy path unchanged.
- [x] Persist every new field through templates, frozen sessions, and additive migration 10 → 11.

### Task 5: Add Typed Gym-Floor Feedback

**Status:** Shipped. Typed outcomes are captured, validated, and persisted atomically,
and the gym-floor logger and rest timer are in the app. Verified 2026-09-02: RPE/RIR input
exists in `ActiveWorkoutScreen`/`ActiveWorkoutViewModel`, and `workout_sets` carries
`rpe`, `rir`, `feltManageable`, `completedAtTimestamp`, `stoppedAtTimestamp`, and
`stopReason` since schema 9. Task 6A now consumes a strict reviewed-only subset for
capability evidence; progression and deload logic remain Task 6B/6C.

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Workout.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/SetOutcome.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/dao/Daos.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutScreen.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/RestTimerState.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutViewModelTest.kt`

- [x] Nullable RPE/RIR, outcome timestamps, the user-confirmed manageable flag,
  and a typed skip/pain-stop reason are validated by `SetOutcomeRules` and
  covered by unit, DAO, and migration tests.
- [x] Typed outcomes persist atomically in one guarded `updateSetOutcome`
  statement, with null preserved as null and no partial or contradictory write.
- [x] Fast RIR and rest controls exist; effort input never blocks completing a
  valid set, and the rest timer runs off the exercise's persisted `restSeconds`.
- [x] Incomplete, skipped, cancelled, and abandoned work stays distinguishable
  from completed work, so it cannot look completed to future adaptation.
- [x] Focused JVM and connected Android tests originally shipped at schema 9;
  the current migration suite validates the complete chain through schema 11.

Effort is recorded on the documented 0-10 RPE scale and 0-10 RIR range. Missing
values stay missing: nothing infers effort, readiness, or a manageable answer
from any other field. `PAIN_STOP` means only that the user chose to stop because
something hurt — never an injury, a symptom report, or a diagnosis — and there
is no free-text stop reason in this milestone.

**Not in this milestone:** one-variable progression and user-controlled deloads do
not read this feedback yet. Capability evidence now does, but only behind the
reviewed-only production-disabled flag and only for strictly qualifying completed
work.

### Task 6: Add Capability Evidence, Progression, and DeloadOffer

**Status:** Task 6A shipped, verified 2026-09-02. `CapabilityEvidencePolicy.kt`,
`CapabilityPreferenceRankingPolicy.kt`, focused unit tests, and reviewed-context wiring are in
place behind the production-disabled reviewed flag. They derive deterministic capability
evidence and suppress only the matching candidate's soft capability penalty. Production remains
on the legacy path because `PlannerFeatureFlags.reviewedCapabilityEligibility = false` and the
bundled reviewed cohort remains 37 `DRAFT` / 0 `APPROVED`. Task 6B and Task 6C remain not
started: `ProgressionEngine.kt` and `DeloadOfferPolicy.kt` are absent.

Task 6A evidence requires two distinct `SessionStatus.COMPLETED` sessions for the same exact
exercise ID, fully qualifying non-warm-up work, and explicit per-set
`feltManageable == true`. Null/false manageable answers, completion alone, RPE, and RIR do not
qualify.

**Prerequisite discovered during Task 3/4 review:** Task 6A landed without widening derived
adaptation states. Task 6B/6C are still the first work that wants states beyond
`UNCALIBRATED` and `RETURNING`. `ExerciseEligibilityPolicy` applies the temporary advanced-
complexity ceiling with an allow-by-default check on exactly those two states, so **any
additional derived state lifts that ceiling**. `AdaptationStatePolicy` therefore still emits
only those two today, and `AdaptationStatePolicyTest.everyDerivableStateIsOneTheAdvancedCeilingCovers`
fails if that changes. Widening the state machine and updating the ceiling must happen in one
change, not two.

**Files shipped in 6A:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicy.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/CapabilityEvidence.kt`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicyTest.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityPreferenceRankingPolicy.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilder.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutGenerationContext.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityPreferenceRankingPolicyTest.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilderTest.kt`

**Files still open for 6B/6C:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ProgressionEngine.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/DeloadOfferPolicy.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ProgressionEngineTest.kt`

- [x] Write failing tests proving two distinct comparable completed sessions plus explicit per-set manageable confirmation relax only soft capability penalties.
- [x] Preserve `AVOID`, exclusions, equipment, all training constraints, `LOW_IMPACT_ONLY`, the approved-metadata gate, and the temporary advanced ceiling regardless of history.
- [x] Scope evidence to the exact demonstrated exercise plus direct approved regressions only when both source and target metadata are `APPROVED`.
- [x] Keep candidate membership unchanged and suppress only the matching candidate's reviewed soft capability penalty.
- [ ] Task 6B: Progress one variable at a time from comparable history; missing effort is neutral.
- [ ] Task 6C: Create user-controlled `DeloadOffer` from request, returning state, or versioned multi-session pattern without fixed calendar/percentage.
- [ ] Widen adaptation-state derivation beyond `UNCALIBRATED` and `RETURNING` together with the advanced-ceiling update.

### Task 7: Add Session and Weekly Program Validation

**Status:** Not started, verified 2026-09-02. `ProgramValidator.kt` does not exist.
`GeneratedWorkoutValidator` still performs only structural per-exercise validation
(`validate` and `validateExercise`); it has no duplicate-family, weekly-volume, or
ledger-overflow check.

`WeeklyLedgerOverflow` became meaningful only once Task 4 started reading
`weeklyLedger.directPrimarySets`, so this task is now unblocked and is the cheapest
remaining engine step: it is pure policy, testable with synthetic approved metadata.

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ProgramValidator.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/today/TodayViewModel.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ProgramValidatorTest.kt`

- [ ] Write failing tests for unknown/unreviewed IDs, hard-rule violations, duplicate exercise/family, invalid dose, unconfirmed load, duration mismatch, and weekly ledger overflow.
- [ ] Return structured violations and allow one deterministic repair that never removes explicit constraints.
- [ ] Persist context/catalog/review/policy/ledger versions, reason codes, and validator result with recommendation snapshots.
- [ ] Add honest RT-session copy for fat-loss/general-fitness and optional user-selected activity education at program level.
- [ ] Run focused tests and commit `feat: validate weekly workout programs`.

### Task 8: Add Replayable Persona Evaluation and Release Gate

**Status:** Partly shipped, verified 2026-09-02. The corpus and harness exist:
33 fixture resources under `app/src/test/resources/planner-fixtures/`, a manifest,
`PlannerFixtureTest`, `PlannerFixtureCorpusTest`, `PlannerFixtureEvaluator`, and
`PlannerFixtureContextFactory`. CI runs the Python suites, the JVM suite, lint, the debug
build, and — since #46 — `connectedDebugAndroidTest` on an emulator.

Nine of the ten personas this task named exist: `bodyweight-beginner`, `band-only`,
`machine-only`, `full-gym-advanced`, `limited-capability`, `returning-user`,
`mixed-unit-history`, `sparse-history`, plus `reviewed-enabled-bodyweight` and
`reviewed-enabled-no-approved` for the reviewed path. **`concurrent-activity` is the one
missing persona.** The remaining doc-cleanup steps below are also still open.

**Files:**
- Create: `app/src/test/resources/planner-fixtures/*.json`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixtureTest.kt`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/superpowers/plans/2026-08-28-adaptive-coach-product.md`
- Modify: `docs/superpowers/specs/2026-08-29-body-aware-personalization-design.md`
- Modify: `docs/superpowers/plans/2026-08-29-body-aware-personalization.md`

- [x] Add novice/bodyweight, band-only, machine-only, full-gym, advanced strength, limited capability, returner, mixed-unit, and sparse-history fixtures.
- [ ] Add the remaining `concurrent-activity` persona fixture, the only one of the ten still missing.
- [x] Assert deterministic replay, reviewed IDs, and hard rules through `PlannerFixtureCorpusTest` and `PlannerFixtureTest`.
- [ ] Extend corpus assertions to the primary-only ledger and no-invented-load gates now that Task 4 reads the ledger; the corpus predates both.
- [ ] Remove body-mass ranking/fraction fields and unsupported fixed-dose/deload/fatigue claims from existing plans.
- [x] Run Python, JVM, lint, build, and connected Android in CI; `connectedDebugAndroidTest` runs on an emulator since #46.
- [ ] Add the importer drift check to CI. It currently cannot run unattended: `import_catalog.py --check` needs the Workout Guide checkout pinned at the catalog's `source.commit`, and no CI step provides one.
- [ ] Commit `test: gate the deterministic workout engine`.

### Task 9: Reconcile the Progress Weekly Card with `PRIMARY_ONLY_V1`

**Status:** Not started, and not previously tracked by any plan. Added by the 2026-09-02
roadmap audit.

The Progress screen already shows weekly per-muscle set counts, and it computes them in a way
that contradicts the shipped ledger on three separate axes:

| | Progress screen today | `PRIMARY_ONLY_V1` ledger |
| --- | --- | --- |
| Crediting | credits **every** legacy `primaryMuscles` entry per completed set | credits **exactly one** approved `directPrimaryMuscle` |
| Week boundary | rolling `age in 0 until WEEK_MILLIS` — a fixed 168 hours from now, DST-blind | ISO Monday to Monday in a zone, 167 or 169 hours across a transition |
| Metadata gate | any catalog entry, no reviewed metadata required | `APPROVED` reviewed metadata only |

Evidence: `ProgressCalculator.completedSetsByPrimaryMuscle` and the `thisWeek` filter in
`ProgressCalculator`.

Nobody sees the conflict yet because no screen renders the ledger and no entry is `APPROVED`.
**It becomes user-visible the moment metadata is approved**, when the screen will report
inflated per-muscle counts beside an engine that disagrees. Doing this work *before* approval
is cheaper than explaining the discrepancy afterwards.

- [ ] Decide the product answer first: does the Progress card report *dose* (ledger semantics)
  or *activity* (every muscle an exercise names)? They are different questions and both are
  defensible; only one can own the phrase "sets this week".
- [ ] If it reports dose, note that switching today empties the card, because zero entries are
  `APPROVED`. Design the empty state and the omission copy before switching, not after.
- [ ] If it keeps activity semantics, rename it in the UI so it cannot be read as dose, and
  record the divergence in `docs/architecture.md` as deliberate.
- [ ] Either way, do not leave two unlabelled answers to "how many sets did I do for Chest".

### Task 10: Close the Loop Between Approval and Enablement

**Status:** Blocking prerequisite for Tasks 2, 3, and 4 to have any user-visible effect.
Added by the 2026-09-02 roadmap audit to make an implicit dependency explicit.

Tasks 2, 3, and 4 are all built, tested, and inert. Every one of them requires `APPROVED`
reviewed metadata, and all 37 entries are `DRAFT` with zero approved. The engine currently
computes a ledger that credits nothing and dose targets that gate on nothing, behind a
production-disabled flag.

This is deliberate and correct — approval is a human act and nothing in the pipeline may
manufacture it — but it means **further engine tasks do not move the product until approval
happens**. That dependency was previously implied only by a single unticked step in Task 1.

- [ ] Resolve the one open ratification question recorded in the metadata packet:
  `barbell-deadlift` uses `directPrimaryMuscle = "Hamstrings"` while the pinned upstream source
  names `Posterior Chain`. WallCrawl requires a single direct primary, so a human must choose.
  This value is a direct ledger input; `LedgerSourceFingerprint` hashes it, so a change
  correctly invalidates cached ledgers.
- [ ] Complete the human sign-off worksheet at `docs/reviewed-exercise-metadata-human-signoff.md`
  with a real reviewer role and review time.
- [ ] Apply approval as a deliberate authored-data change to
  `tools/workout-guide/reviewed-metadata.json`, then regenerate the catalog and report.
- [ ] Re-run the availability and persona review before any flag change: with reviewed
  eligibility enabled, confirm no persona loses its plan.
- [ ] Only then consider flipping `PlannerFeatureFlags.reviewedCapabilityEligibility`, as a
  separate, reviewable change.

## Deterministic Release Gates

- Every automatic candidate has reviewed provenance.
- Hard constraints have zero violations.
- Same versioned inputs reproduce the same recommendation.
- Weekly ledger is reconstructable from immutable history.
- Missing inputs never become favorable assumptions.
- BMI/body mass do not affect v1 planning.
- No load is invented.
- Deload is an offer, never diagnosis.

## Complete Verification

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
./gradlew test lint assembleDebug --stacktrace --no-daemon
./gradlew connectedDebugAndroidTest --no-daemon
git diff --check
```
