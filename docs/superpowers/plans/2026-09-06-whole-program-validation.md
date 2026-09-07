# Whole-Program Validation Implementation Plan

> Steps use checkbox (`- [ ]`) syntax for tracking. Execute task-by-task, running the named
> tests between tasks.

**Goal:** Validate a complete generated workout recommendation — identifiers, candidate
membership, reviewed provenance, declared session constraints, prescription structure, load
provenance, duration consistency, and aggregate weekly dose — before it is displayed,
repaired once, revalidated at start, and recorded immutably with the session.

**Architecture:** A new pure `ProgramValidator` in `core/ai` reuses
`GeneratedWorkoutValidator` (refactored to compute structured violations) and a new shared
`WorkoutDurationEstimator`. It returns a `ProgramValidationResult` carrying a
`RecommendationSnapshot`. `TodayViewModel` validates after generation (repair allowed) and
again at start (repair disabled, context identity compared). The snapshot is written into
`workout_recommendation_records` inside the same transaction that starts the session, and
carried through the archive at format version 2.

**Tech Stack:** Kotlin, Room 11 → 12, JUnit 4 + Truth + kotlinx-coroutines-test (JVM),
AndroidX Test + Room `MigrationTestHelper` (instrumentation), Compose for the Today screen.

**Reference:** `docs/superpowers/specs/2026-09-06-whole-program-validation-design.md`

---

## File structure

**Create**

- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutDurationEstimator.kt` — the
  single named duration estimator shared by the planner and the validator.
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ProgramValidator.kt` — the
  whole-program validator, its violation and result types, and the one repair pass.
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/RecommendationContextIdentity.kt` —
  the freshness digest.
- `app/src/main/java/wallcrawl/elopenmike/com/core/model/RecommendationSnapshot.kt` — the
  immutable snapshot domain type and its dose accounting.
- `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/WorkoutRecommendationRecordEntity.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/RecommendationDoseAccountingPayload.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/RecommendationSnapshotMapper.kt`
- Tests: `ProgramValidatorTest.kt`, `ProgramValidatorRepairTest.kt`,
  `ProgramValidatorAggregateDoseTest.kt`, `WorkoutDurationEstimatorTest.kt`,
  `RecommendationContextIdentityTest.kt`, `RecommendationDoseAccountingPayloadTest.kt`,
  `Migration11To12Test.kt`, `RecommendationRecordDaoTest.kt`.

**Modify**

- `core/ai/GeneratedWorkoutValidator.kt` — expose structured violations; keep `validate()`
  and its exact messages.
- `core/ai/FakeWorkoutPlanner.kt` — use `WorkoutDurationEstimator`.
- `core/model/WorkoutGenerationContext.kt` — add `catalogVersion`, `reviewPolicyVersion`,
  `programConstraints`.
- `core/ai/WorkoutGenerationContextBuilder.kt` — populate them.
- `core/database/WallCrawlDatabase.kt` — schema 12, `MIGRATION_11_12`, new entity and DAO.
- `core/database/dao/Daos.kt` — write the record in `insertWorkoutUnlessActive`.
- `core/database/repository/WorkoutRepository.kt` — accept the snapshot.
- `core/database/dao/LocalDataBackupDao.kt`, `repository/LocalDataBackupRepository.kt` —
  export, restore, delete the records.
- `core/backup/LocalDataArchive.kt`, `core/backup/LocalDataArchiveCodec.kt` — archive
  version 2, reader accepts 1 and 2.
- `feature/today/TodayViewModel.kt`, `feature/today/TodayUiState.kt` — validate, revalidate,
  typed failures.
- `WallCrawlApplication.kt` — compose the validator and the catalog-version supplier.
- `res/values/strings.xml`, `res/values-es/strings.xml` — three new typed failures.
- `ROADMAP.md`, `docs/architecture.md`, `docs/planner-evaluation.md`,
  `docs/weekly-dose-ledger.md`, `docs/privacy.md`.

---

### Task 1: Shared duration estimator

**Files:** create `core/ai/WorkoutDurationEstimator.kt`,
`app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutDurationEstimatorTest.kt`;
modify `core/ai/FakeWorkoutPlanner.kt`.

- [ ] **Step 1: Write the failing test.** Assert `estimateMinutes` reproduces the planner's
  existing arithmetic — rest counted once per set, 45 s default execution, truncating
  division, clamped to 1..240 — and that a duration exercise uses its
  `targetDurationSeconds`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*WorkoutDurationEstimatorTest'`
  and see it fail to compile.
- [ ] **Step 3: Implement** `object WorkoutDurationEstimator` with
  `const val VERSION = "DURATION_ESTIMATOR_V1"` and
  `fun estimateMinutes(exercises: List<PlannedExercise>): Int`.
- [ ] **Step 4: Replace** `FakeWorkoutPlanner.calculateEstimatedDuration` with a call to it.
- [ ] **Step 5: Run** the estimator test plus `*FakeWorkoutPlannerTest` and
  `*PlannerFixture*`; expect PASS with no changed durations.
- [ ] **Step 6: Commit** `feat: extract the named workout duration estimator`.

### Task 2: Structured structural violations

**Files:** modify `core/ai/GeneratedWorkoutValidator.kt`; create `core/ai/ProgramValidator.kt`
(violation types only); modify `app/src/test/.../GeneratedWorkoutValidatorTest.kt`.

- [ ] **Step 1: Write the failing test.** `structuralViolations` returns
  `UNKNOWN_EXERCISE_ID` with the offending index and ID for a hallucinated ID, and an empty
  list for a valid workout; `validate` still throws the same messages the existing tests
  assert.
- [ ] **Step 2: Run** `--tests '*GeneratedWorkoutValidatorTest'`; expect FAIL.
- [ ] **Step 3: Implement** `ProgramViolationCode`, `ProgramViolation(code, exerciseId,
  orderIndex, detail)` and its deterministic `compareTo`, then refactor
  `GeneratedWorkoutValidator` to build the list in its existing order and keep `validate()`
  as a thin throwing wrapper preserving the existing message text.
- [ ] **Step 4: Run** the same tests; expect PASS.
- [ ] **Step 5: Commit** `refactor: report structural workout violations as typed values`.

### Task 3: Context additions

**Files:** modify `core/model/WorkoutGenerationContext.kt`,
`core/ai/WorkoutGenerationContextBuilder.kt`; modify
`app/src/test/.../WorkoutGenerationContextBuilderTest.kt`.

- [ ] **Step 1: Write the failing test.** The builder populates `catalogVersion` from the
  injected supplier and `reviewPolicyVersion` as the maximum authored review-policy version
  in the catalog, defaulting to 0; `programConstraints` defaults to
  `SessionProgramConstraints()`.
- [ ] **Step 2: Run** `--tests '*WorkoutGenerationContextBuilderTest'`; expect FAIL.
- [ ] **Step 3: Implement** `data class SessionProgramConstraints(uniqueExerciseIds: Boolean
  = true, uniqueProgressionFamilies: Boolean = false, requiredMovementPatterns:
  Set<MovementPattern> = emptySet())`, add the three fields to `WorkoutGenerationContext`,
  and add `catalogVersion: () -> String? = { null }` to the builder.
- [ ] **Step 4: Run** the builder tests; expect PASS.
- [ ] **Step 5: Commit** `feat: carry catalog identity and declared session constraints`.

### Task 4: Recommendation snapshot model

**Files:** create `core/model/RecommendationSnapshot.kt`.

- [ ] **Step 1: Write the failing test** in `ProgramValidatorTest` asserting a valid
  legacy-path proposal yields `outcome = VALID`, empty `reasonCodes`, empty
  `doseAccounting`, and null reviewed-only versions.
- [ ] **Step 2: Run**; expect FAIL.
- [ ] **Step 3: Implement** `RecommendationOutcome { VALID, REPAIRED, REJECTED }`,
  `MuscleDoseAccounting(muscle, completedSets, proposedSets, allowanceSets: Int?)`,
  `ProgramValidatorVersion { WHOLE_PROGRAM_V1 }`, and `RecommendationSnapshot` with the
  fields the design lists. Require non-blank muscle, non-negative counts.
- [ ] **Step 4: Run**; expect PASS.
- [ ] **Step 5: Commit** `feat: add the immutable recommendation snapshot model`.

### Task 5: ProgramValidator — legacy-path rules

**Files:** modify `core/ai/ProgramValidator.kt`; create `ProgramValidatorTest.kt`.

- [ ] **Step 1: Write failing tests** for: structural reuse; duplicate exercise ID rejected
  when declared, accepted when the constraint is switched off; user-excluded exercise
  rejected; declared missing movement pattern rejected and inert by default; duplicate
  family inert by default; load traced to a confirmed starting load, to history, and to
  history plus the 5.0 lb / 2.5 kg increment; an untraceable load rejected; a null load
  accepted; duration outside 1..240 rejected; duration disagreeing with the estimator by
  more than one minute rejected; requested duration never enforced.
- [ ] **Step 2: Run** `--tests '*ProgramValidatorTest'`; expect FAIL.
- [ ] **Step 3: Implement** `ProgramValidator.validate(workout, context, allowRepair)`
  returning `ProgramValidationResult.Valid|Invalid`, running the legacy rule set.
- [ ] **Step 4: Run**; expect PASS.
- [ ] **Step 5: Commit** `feat: validate the whole proposal on the legacy path`.

### Task 6: ProgramValidator — reviewed-path rules and aggregate dose

**Files:** modify `core/ai/ProgramValidator.kt`; create
`ProgramValidatorAggregateDoseTest.kt`.

- [ ] **Step 1: Write failing tests** using `syntheticApprovedExercise`: missing approved
  metadata rejected; review-policy mismatch rejected; prescription-shape mismatch rejected;
  an exercise the eligibility decisions marked ineligible rejected; two exercises sharing
  one direct primary summed against one allowance; exactly at the allowance accepted; one
  set over rejected with `WEEKLY_ALLOWANCE_EXCEEDED`; a malformed ledger reported as
  `MALFORMED_WEEKLY_LEDGER`; `Long` sums near `Int.MAX_VALUE` reported as
  `DOSE_ACCOUNTING_OVERFLOW`; `NEEDS_ONBOARDING` records a null allowance without a
  violation; secondary involvement never counted; the ledger never mutated.
- [ ] **Step 2: Run**; expect FAIL.
- [ ] **Step 3: Implement** the reviewed rule set and prospective aggregation keyed by
  approved `directPrimaryMuscle`, comparing `completed + proposed` to the configured
  allowance in `Long` arithmetic.
- [ ] **Step 4: Run**; expect PASS.
- [ ] **Step 5: Commit** `feat: check the whole proposed dose against one weekly allowance`.

### Task 7: One deterministic repair pass

**Files:** modify `core/ai/ProgramValidator.kt`; create `ProgramValidatorRepairTest.kt`.

- [ ] **Step 1: Write failing tests:** an over-allowance proposal is repaired by reducing
  sets in recommendation order with each exercise keeping at least one set, the outcome is
  `REPAIRED`, the reason codes name what was repaired, and the duration is recomputed;
  repair never runs twice; repair fails closed when the remainder cannot give every affected
  exercise one set; repair never changes exercise selection, loads, effort, or rest; repair
  is not attempted when `allowRepair = false`; a candidate-membership or explicit-constraint
  violation is never repaired.
- [ ] **Step 2: Run**; expect FAIL.
- [ ] **Step 3: Implement** the single repair pass and the revalidation that follows it.
- [ ] **Step 4: Run**; expect PASS.
- [ ] **Step 5: Commit** `feat: allow one deterministic dose repair pass`.

### Task 8: Context identity

**Files:** create `core/ai/RecommendationContextIdentity.kt`,
`RecommendationContextIdentityTest.kt`.

- [ ] **Step 1: Write failing tests:** identical contexts produce identical digests; a
  changed profile revision, completed count, candidate set, catalog version, review-policy
  version, adaptation state, week start, or zone changes it; a changed display language or
  exercise name does not.
- [ ] **Step 2: Run**; expect FAIL.
- [ ] **Step 3: Implement** the SHA-256 digest over the canonical field list.
- [ ] **Step 4: Run**; expect PASS.
- [ ] **Step 5: Commit** `feat: fingerprint the generation context for freshness checks`.

### Task 9: Room table, migration, and atomic start

**Files:** create `entity/WorkoutRecommendationRecordEntity.kt`,
`repository/RecommendationDoseAccountingPayload.kt`,
`repository/RecommendationSnapshotMapper.kt`; modify `WallCrawlDatabase.kt`,
`dao/Daos.kt`, `repository/WorkoutRepository.kt`; create
`RecommendationDoseAccountingPayloadTest.kt`,
`app/src/androidTest/.../Migration11To12Test.kt`,
`app/src/androidTest/.../RecommendationRecordDaoTest.kt`.

- [ ] **Step 1: Write the failing payload test:** encode/decode round trip, strict rejection
  of a malformed line, an unknown field count, a blank muscle, a negative count, and a count
  beyond the ledger bound.
- [ ] **Step 2: Write the failing migration test** in the style of `Migration10To11Test`:
  seed schema 11, run `MIGRATION_11_12`, assert every existing row survives unchanged and
  `workout_recommendation_records` exists and is empty.
- [ ] **Step 3: Write the failing DAO test:** starting a workout writes the session, its
  exercises, its sets, and exactly one record in one transaction; a profile-revision
  mismatch writes neither a session nor a record; an existing active session writes neither.
- [ ] **Step 4: Run** `--tests '*RecommendationDoseAccountingPayloadTest'`; expect FAIL.
- [ ] **Step 5: Implement** the payload, the entity, `WALLCRAWL_SCHEMA_VERSION = 12`,
  `MIGRATION_11_12`, the mapper, the DAO insert inside `insertWorkoutUnlessActive`, and the
  `recommendation: RecommendationSnapshot?` parameter on `startWorkoutFromGenerated`.
- [ ] **Step 6: Run** the JVM tests, then
  `./gradlew :app:connectedDebugAndroidTest --tests '*Migration11To12Test' --tests '*RecommendationRecordDaoTest'`
  if a device is available; otherwise record that the instrumentation suite was not run.
- [ ] **Step 7: Commit** `feat: record recommendation provenance with the started session`.

### Task 10: Archive version 2

**Files:** modify `core/backup/LocalDataArchive.kt`, `core/backup/LocalDataArchiveCodec.kt`,
`dao/LocalDataBackupDao.kt`, `repository/LocalDataBackupRepository.kt`; modify
`app/src/androidTest/.../LocalDataArchiveCodecTest.kt`,
`LocalDataArchiveFixtures.kt`, `LocalDataBackupRepositoryTest.kt`.

- [ ] **Step 1: Write failing tests:** a version 2 archive round trips with recommendation
  records; a version 1 archive still reads and restores; a record naming an unknown session
  is `INCONSISTENT`; two records for one session are `INCONSISTENT`; a malformed dose
  payload is `INVALID_VALUE`; a version 3 archive is `UNSUPPORTED_VERSION`; deletion removes
  the records.
- [ ] **Step 2: Run**; expect FAIL.
- [ ] **Step 3: Implement** `ARCHIVE_VERSION = 2` with `SUPPORTED_ARCHIVE_VERSIONS = 1..2`,
  the `recommendations` array in `writeSnapshot`/`readSnapshot`, the new
  `LocalDataSnapshot.recommendationRecords` and `LocalDataRows.recommendationRecords`, and
  the DAO select/insert/delete.
- [ ] **Step 4: Run** the archive tests; expect PASS.
- [ ] **Step 5: Commit** `feat: carry recommendation records through export and restore`.

### Task 11: Today integration

**Files:** modify `feature/today/TodayViewModel.kt`, `feature/today/TodayUiState.kt`,
`WallCrawlApplication.kt`, `res/values/strings.xml`, `res/values-es/strings.xml`; modify
`app/src/test/.../TodayViewModelTest.kt`.

- [ ] **Step 1: Write failing tests:** generation validates through `ProgramValidator` and a
  rejection surfaces a typed error without showing a workout; an all-allowance rejection
  surfaces the weekly-plan-complete error; starting with a changed profile revision,
  completed count, or week boundary surfaces the out-of-date error and starts nothing;
  starting revalidates with repair disabled; a start-time rejection surfaces the
  start-validation error and starts nothing; a successful start passes the snapshot to the
  repository.
- [ ] **Step 2: Run** `--tests '*TodayViewModelTest'`; expect FAIL.
- [ ] **Step 3: Implement** the two call sites, the three new `TodayError` values, the
  English and Spanish strings, and the composition wiring.
- [ ] **Step 4: Run** `--tests '*TodayViewModelTest' --tests '*StringResourceParityTest'`;
  expect PASS.
- [ ] **Step 5: Commit** `feat: validate recommendations before display and at start`.

### Task 12: Locale invariance and legacy-path regression

**Files:** modify `app/src/test/.../PlannerLocaleInvarianceTest.kt`; create assertions in
`ProgramValidatorTest`.

- [ ] **Step 1: Write failing tests:** identical canonical inputs produce identical
  decisions, reason codes, dose accounting, and context identity under English and Spanish
  locales; with the reviewed gate disabled the validator applies no reviewed-only rule and
  accepts the exact plan the legacy planner produces from the bundled catalog.
- [ ] **Step 2: Run**; expect FAIL.
- [ ] **Step 3: Implement** any adjustment the tests reveal.
- [ ] **Step 4: Run**; expect PASS.
- [ ] **Step 5: Commit** `test: prove language and the disabled reviewed gate change nothing`.

### Task 13: Documentation

**Files:** modify `ROADMAP.md`, `docs/architecture.md`, `docs/planner-evaluation.md`,
`docs/weekly-dose-ledger.md`, `docs/privacy.md`.

- [ ] **Step 1:** Update ROADMAP Package 4 to shipped, record the resolved contract
  decisions, close open decision 4, and leave Packages 3, 5, 6, and 7 untouched.
- [ ] **Step 2:** Replace the "whole-program validation is planned, not shipped" paragraph
  in `docs/architecture.md` with the shipped contract and the data flow through Room 12.
- [ ] **Step 3:** Convert `docs/planner-evaluation.md`'s "Planned whole-program assertions"
  into what is asserted, keeping the software-conformance boundary.
- [ ] **Step 4:** Note prospective-versus-completed accounting in
  `docs/weekly-dose-ledger.md`.
- [ ] **Step 5:** Document archive version 2, what the record contains, and what it does not,
  in `docs/privacy.md`.
- [ ] **Step 6: Run** the full JVM suite and lint.
- [ ] **Step 7: Commit** `docs: describe shipped whole-program validation`.

---

## Verification

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest   # requires a device or emulator
```
