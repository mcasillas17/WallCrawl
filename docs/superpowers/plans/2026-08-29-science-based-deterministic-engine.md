# Science-Based Deterministic Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

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

**Status:** Shipped. The ledger is built and cached at schema 10 and documented in
[docs/weekly-dose-ledger.md](../../weekly-dose-ledger.md). Nothing consumes it yet:
planner selection, dose targets, and progression are unchanged. `AdaptationState` landed
with Task 2; `TrainingProgramState` and the first allowed ledger consumption remain Task 4.

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

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/TrainingPolicy.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/EffortTarget.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/ExercisePrescription.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/DefaultExercisePrescriptionFactory.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/TrainingPolicyTest.kt`

- [ ] Write failing tests: INITIATE may choose very small exposure; weekly ledger drives remaining dose; session count only constrains duration/tolerance.
- [ ] Add nullable `EffortTarget(minRir, maxRir)` and `RestClass { SHORT, MODERATE, LONG }`.
- [ ] Use 2-4 RIR guidance in INITIATE/RETURNING or for a relevant `LIMITED` capability and 1-3 for established general/hypertrophy; never auto-default failure.
- [ ] Resolve rest classes through versioned editable product policy and preserve per-exercise user preference.
- [ ] Prove capability values never produce an unconfirmed load; commit `feat: add state based workout dose policy`.

### Task 5: Add Typed Gym-Floor Feedback

> **Shipped.** Typed outcomes are captured, validated, and persisted atomically,
> and the gym-floor logger and rest timer are in the app. Nothing downstream
> consumes the feedback yet; that is Task 6.

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
  the current migration suite validates the complete chain through schema 10.

Effort is recorded on the documented 0-10 RPE scale and 0-10 RIR range. Missing
values stay missing: nothing infers effort, readiness, or a manageable answer
from any other field. `PAIN_STOP` means only that the user chose to stop because
something hurt — never an injury, a symptom report, or a diagnosis — and there
is no free-text stop reason in this milestone.

**Not in this milestone:** capability evidence, progression, deloads, and the
weekly dose ledger do not read this feedback yet.

### Task 6: Add Capability Evidence, Progression, and DeloadOffer

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicy.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ProgressionEngine.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/DeloadOfferPolicy.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicyTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ProgressionEngineTest.kt`

- [ ] Write failing tests: two comparable completed sessions plus explicit confirmation relax only soft penalties.
- [ ] Preserve AVOID, constraints, equipment, and low-impact hard rules regardless of history.
- [ ] Progress one variable at a time from comparable history; missing effort is neutral.
- [ ] Create user-controlled `DeloadOffer` from request, returning state, or versioned multi-session pattern without fixed calendar/percentage.
- [ ] Run tests and commit `feat: add transparent workout adaptation`.

### Task 7: Add Session and Weekly Program Validation

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

**Files:**
- Create: `app/src/test/resources/planner-fixtures/*.json`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixtureTest.kt`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/superpowers/plans/2026-08-28-adaptive-coach-product.md`
- Modify: `docs/superpowers/specs/2026-08-29-body-aware-personalization-design.md`
- Modify: `docs/superpowers/plans/2026-08-29-body-aware-personalization.md`

- [ ] Add novice/bodyweight, band-only, machine-only, full-gym, advanced strength, limited capability, returner, mixed-unit, sparse-history, and concurrent-activity fixtures.
- [ ] Assert deterministic replay, reviewed IDs, hard rules, primary-only ledger, no invented load, and no BMI influence.
- [ ] Remove body-mass ranking/fraction fields and unsupported fixed-dose/deload/fatigue claims from existing plans.
- [ ] Run Python, JVM, lint, build, connected Android, importer drift, and `git diff --check`.
- [ ] Commit `test: gate the deterministic workout engine`.

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
python3 tools/workout-guide/import_catalog.py \
  --source /Users/elopenmike/build/Apps/Workouts/guide/workout-guide \
  --check
./gradlew test lint assembleDebug --stacktrace --no-daemon
./gradlew connectedDebugAndroidTest --no-daemon
git diff --check
```
