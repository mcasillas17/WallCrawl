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
    data class HardConstraint(val exerciseId: String) : ProgramViolation
    data class DuplicateFamily(val family: String) : ProgramViolation
    data class InvalidDose(val exerciseId: String) : ProgramViolation
    data class DurationMismatch(val planned: Int, val requested: Int) : ProgramViolation
}
```

### Task 1: Replace Speculative Metadata with Reviewed Categorical Metadata

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Exercise.kt`
- Modify: `tools/workout-guide/programming-overrides.json`
- Modify: `tools/workout-guide/import_catalog.py`
- Create: `tools/workout-guide/review-schema.json`
- Test: `tools/workout-guide/test_programming_overrides.py`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/exercise/workoutguide/WorkoutGuideCatalogParserTest.kt`

- [ ] Write failing tests requiring direct-primary muscle, pattern, complexity, progression family, approved regressions/substitutions, capability requirements, support, impact, equipment, and review provenance.
- [ ] Verify tests reject unknown graph IDs, cycles, missing provenance, and numeric joint/SFR/axial/body-mass fields.
- [ ] Add categorical types and `ReviewProvenance(reviewerRole, rationaleOrSource, reviewedAt, schemaVersion, policyVersion)`.
- [ ] Review band, machine, supported, bodyweight, and timed-hold families before enabling the automatic gate.
- [ ] Run Python/importer/parser tests and commit `feat: add reviewed deterministic planning metadata`.

### Task 2: Enforce Reviewed-Only Eligibility and Calibration Complexity

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ExerciseEligibilityPolicy.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/exercise/ExerciseFilter.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutGenerationContext.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ExerciseEligibilityPolicyTest.kt`

- [ ] Write failing tests proving equipment, exclusions, constraints, capability `AVOID`, low-impact policy, and reviewed metadata are hard requirements.
- [ ] Add the complete `AdaptationState` enum from Core Contracts.
- [ ] Temporarily block advanced-complexity automatic exercises only in INITIATE/RETURNING when no demonstrated family history or supported regression exists.
- [ ] Preserve full-catalog browse/manual workflows and typed no-plan reasons.
- [ ] Run focused tests and commit `feat: enforce reviewed workout eligibility`.

### Task 3: Add the PRIMARY_ONLY_V1 Weekly Ledger

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/TrainingProgramState.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WeeklyDoseLedgerCalculator.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/dao/Daos.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WeeklyDoseLedgerCalculatorTest.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/TrainingProgramStateMigrationTest.kt`

- [ ] Write failing tests showing one completed work set credits one designated direct-primary muscle once and secondary involvement receives no dose credit.
- [ ] Add `WeeklyDoseLedger(policyVersion, weekStartEpochDay, directPrimarySets, secondaryInvolvement)`.
- [ ] Derive ledgers from immutable completed sessions; do not increment mutable counters during generation.
- [ ] Persist only program state/accepted offers; completed history remains the reconstructable authority.
- [ ] Run unit/migration tests and commit `feat: add weekly training dose ledger`.

### Task 4: Implement State-Based Dose, Effort, and Rest Policy

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/TrainingPolicy.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/EffortTarget.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/ExercisePrescription.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/DefaultExercisePrescriptionFactory.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/TrainingPolicyTest.kt`

- [ ] Write failing tests: INITIATE may choose very small exposure; weekly ledger drives remaining dose; session count only constrains duration/tolerance.
- [ ] Add nullable `EffortTarget(minRir, maxRir)` and `RestClass { SHORT, MODERATE, LONG }`.
- [ ] Use 2-4 RIR guidance for INITIATE/RETURNING/LIMITED and 1-3 for established general/hypertrophy; never auto-default failure.
- [ ] Resolve rest classes through versioned editable product policy and preserve per-exercise user preference.
- [ ] Prove body measurements never produce load; commit `feat: add state based workout dose policy`.

### Task 5: Add Typed Gym-Floor Feedback

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Workout.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/dao/Daos.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutScreen.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutViewModelTest.kt`

- [ ] Write failing tests for nullable RPE/RIR, timestamps, user-confirmed manageable flag, skip/pain-stop reason, and editable rest.
- [ ] Persist typed outcomes atomically while preserving null as null.
- [ ] Add fast RIR/rest controls; never require effort input to complete a valid set.
- [ ] Ensure incomplete/abandoned sessions cannot progress capability or weekly dose.
- [ ] Run focused and connected tests; commit `feat: capture adaptive workout feedback`.

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

- [ ] Add novice/bodyweight, band-only, machine-only, full-gym, advanced strength, limited capability, returner, mixed-unit, sparse-history, measurement-deleted, and concurrent-activity fixtures.
- [ ] Assert deterministic replay, reviewed IDs, hard rules, primary-only ledger, no invented load, no BMI influence, and valid plan after measurement deletion.
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
