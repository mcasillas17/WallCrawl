# State-Based Dose, Effort, and Rest Implementation Plan

**Goal:** Add a production-disabled, pure, versioned reviewed-training policy that caps
weekly direct-primary dose, supplies nullable RIR guidance, classifies rest, preserves
explicit rest preferences, and persists every new prescription field.

**Architecture:** `DefaultExercisePrescriptionFactory` continues to build the current
base prescription. When and only when `WorkoutGenerationContext.trainingProgramState`
is present, it passes that base to `StateBasedTrainingPolicy`, which validates approved
metadata and program/ledger versions before returning a typed result. Guidance remains
part of `ExercisePrescription`, so generated workouts, templates, frozen sessions, and
Room all preserve the same value.

**Tech stack:** Kotlin, Room, Coroutines/Flow, JUnit 4, Truth, Android instrumentation,
Gradle, and Python standard-library test suites.

**Design:** `docs/superpowers/specs/2026-09-01-state-based-dose-effort-rest-design.md`

---

## File map

### New production files

- `app/src/main/java/wallcrawl/elopenmike/com/core/model/TrainingGuidance.kt`
  defines `EffortTarget`, `RestClass`, `RestTargetSource`, and
  `UserRestPreference`.
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/StateBasedTrainingPolicy.kt`
  defines versioned product defaults, typed outcomes/reasons, the pure policy, and the
  typed exception used by the prescription factory boundary.

### New test files

- `app/src/test/java/wallcrawl/elopenmike/com/core/model/TrainingGuidanceTest.kt`
  covers guidance-domain bounds.
- `app/src/test/java/wallcrawl/elopenmike/com/core/ai/StateBasedTrainingPolicyTest.kt`
  covers every state, trust/version failures, bounded dose arithmetic, effort, rest,
  load invariance, and deterministic ordering.
- `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration10To11Test.kt`
  proves the additive schema migration.

### Existing production files to modify

- `core/model/ExercisePrescription.kt`: carry nullable effort/rest classification and
  expose explicit-rest conversion helpers.
- `core/model/WorkoutGenerationContext.kt`: carry bounded prior explicit rest
  preferences.
- `core/model/TrainingProgramState.kt`: document the Task 4 consumer.
- `core/ai/WorkoutGenerationContextBuilder.kt`: derive explicit preferences once from
  the existing eight-session history view.
- `core/ai/DefaultExercisePrescriptionFactory.kt`: preserve the exact legacy base path
  and apply policy only when composed state is present.
- `core/database/entity/Entities.kt`: add nullable guidance columns to workout and
  template exercise rows.
- `core/database/relation/WorkoutSessionMapper.kt`: reconstruct the full prescription.
- `core/database/repository/WorkoutRepository.kt`: persist guidance in frozen sessions.
- `core/database/repository/WorkoutTemplateRepository.kt`: round-trip guidance in
  templates.
- `core/database/WallCrawlDatabase.kt`: move to schema 11 and add migration 10 → 11.
- `WallCrawlApplication.kt`: update the stale ledger-consumer comment only; production
  flags remain false.

### Existing tests and fixtures to modify

- `DefaultExercisePrescriptionFactoryTest.kt`
- `ExercisePrescriptionTest.kt`
- `WorkoutGenerationContextBuilderTest.kt`
- `PlannerFixtureContextFactory.kt`
- `PlannerFixtureEvaluator.kt`
- `PlannerFixtureTest.kt`
- `PlannerFixtureCorpusTest.kt`
- `WorkoutTemplateRepositoryTest.kt`
- `WorkoutTemplateSessionTest.kt`
- `LegacyDatabaseFixtures.kt`
- `Migration8To9Test.kt`
- `Migration9To10Test.kt`
- rename `MigrationChainTo10Test.kt` to `MigrationChainTo11Test.kt`

### Documentation to modify

- `README.md`
- `docs/architecture.md`
- `docs/weekly-dose-ledger.md`
- `docs/planner-evaluation.md`
- `docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md`

---

## Task 1: Add bounded guidance domain types

### RED

1. Create `TrainingGuidanceTest.kt`.
2. Extend `ExercisePrescriptionTest.kt`.
3. Add tests with these assertions:

```kotlin
assertThat(EffortTarget(minRir = 2, maxRir = 4))
    .isEqualTo(EffortTarget(2, 4))

assertThrows(IllegalArgumentException::class.java) {
    EffortTarget(minRir = 0, maxRir = 3)
}
assertThrows(IllegalArgumentException::class.java) {
    EffortTarget(minRir = 4, maxRir = 2)
}

val preferred = base.withUserRestPreference(
    UserRestPreference(RestClass.LONG, restSeconds = 240)
)
assertThat(preferred.restClass).isEqualTo(RestClass.LONG)
assertThat(preferred.restTargetSource).isEqualTo(RestTargetSource.USER_PREFERENCE)
assertThat(preferred.restSeconds).isEqualTo(240)
assertThat(preferred.userRestPreferenceOrNull())
    .isEqualTo(UserRestPreference(RestClass.LONG, 240))

assertThrows(IllegalArgumentException::class.java) {
    base.copy(
        restClass = RestClass.MODERATE,
        restTargetSource = null
    )
}
```

4. Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*TrainingGuidanceTest' \
  --tests '*ExercisePrescriptionTest' \
  --rerun-tasks --no-daemon
```

Expected RED: Kotlin compilation fails because the guidance types and prescription
fields do not exist.

### GREEN

5. Add `TrainingGuidance.kt`:

```kotlin
data class EffortTarget(val minRir: Int, val maxRir: Int) {
    init {
        require(minRir in 1..10) { "Minimum RIR must be between 1 and 10." }
        require(maxRir in minRir..10) {
            "Maximum RIR must be between minimum RIR and 10."
        }
    }
}

enum class RestClass { SHORT, MODERATE, LONG }

enum class RestTargetSource { PRODUCT_POLICY, USER_PREFERENCE }

data class UserRestPreference(
    val restClass: RestClass,
    val restSeconds: Int
) {
    init {
        require(restSeconds in 0..1_800) {
            "Rest seconds must be between 0 and 1800."
        }
    }
}
```

6. Extend `ExercisePrescription` after the existing `restSeconds` parameter:

```kotlin
val effortTarget: EffortTarget? = null,
val restClass: RestClass? = null,
val restTargetSource: RestTargetSource? = null
```

7. In its initializer require `restClass` and `restTargetSource` to be both null or
both nonnull.
8. Add:

```kotlin
fun withUserRestPreference(preference: UserRestPreference): ExercisePrescription =
    copy(
        restSeconds = preference.restSeconds,
        restClass = preference.restClass,
        restTargetSource = RestTargetSource.USER_PREFERENCE
    )

fun userRestPreferenceOrNull(): UserRestPreference? =
    if (restTargetSource == RestTargetSource.USER_PREFERENCE) {
        UserRestPreference(
            restClass = requireNotNull(restClass),
            restSeconds = restSeconds
        )
    } else {
        null
    }
```

9. Re-run the focused command. Expected GREEN: both classes pass.
10. Commit the domain slice with both repository-required trailers.

---

## Task 2: Implement the pure versioned policy

### RED

1. Create `StateBasedTrainingPolicyTest.kt` using synthetic approved metadata and real
`TrainingProgramState`/`WeeklyDoseLedger` values.
2. Cover this exact state matrix:

| State | Weekly cap | Per-exercise cap | Conservative 2..4 RIR |
| --- | ---: | ---: | --- |
| `NEEDS_ONBOARDING` | typed no guidance | typed no guidance | n/a |
| `UNCALIBRATED` | 6 | 2 | yes |
| `INITIATE` | 6 | 2 | yes |
| `BUILD` | 12 | 4 | no |
| `DEVELOP` | 12 | 4 | no |
| `HOLD` | 8 | 2 | yes |
| `RETURNING` | 6 | 2 | yes |
| `DELOAD_OFFERED` | 6 | 2 | yes |
| `RECALIBRATE` | 6 | 2 | yes |

3. Add separate tests proving:

- empty, partial, exact-cap, and over-cap ledgers;
- `Int.MAX_VALUE` is handled without overflow;
- negative, oversized, or blank-key ledger entries return
  `Failure(MALFORMED_WEEKLY_LEDGER)`;
- a base target is never increased and no weekly floor is forced;
- exact/over cap returns
  `NoGuidance(WEEKLY_DIRECT_PRIMARY_ALLOWANCE_EXHAUSTED)`;
- missing/DRAFT metadata returns `Failure(MISSING_APPROVED_METADATA)`;
- malformed approved provenance returns `Failure(MALFORMED_APPROVED_METADATA)`;
- review-policy mismatch returns `Failure(REVIEW_POLICY_VERSION_MISMATCH)`;
- prescription-shape mismatch returns `Failure(PRESCRIPTION_SHAPE_MISMATCH)`;
- only approved `directPrimaryMuscle` and `capabilityRequirements` are read;
- relevant `LIMITED` caps sets at two and resolves 2..4 RIR;
- `UNKNOWN` does not receive a favorable capability assumption;
- established strength resolves 1..2 RIR;
- established general/hypertrophy resolves 1..3 RIR;
- unsupported effort combinations remain null;
- no applied result contains zero RIR;
- duration/isolation resolves `SHORT`, ordinary reviewed work `MODERATE`, and
  strength/athletic non-isolation `LONG`;
- product seconds are exactly 60/90/180 and remain within prescription bounds;
- an explicit user preference wins class and seconds;
- target load, assistance, reps, duration, and distance are unchanged;
- changing `daysPerWeek` or completed-session count does not change dose;
- two equal requests return equal results with equal reason order.

4. Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*StateBasedTrainingPolicyTest' \
  --rerun-tasks --no-daemon
```

Expected RED: Kotlin compilation fails because `StateBasedTrainingPolicy` and its typed
contracts do not exist.

### GREEN

5. Add the contracts in `StateBasedTrainingPolicy.kt`:

```kotlin
enum class TrainingPolicyVersion {
    STATE_BASED_DOSE_EFFORT_REST_V1
}

data class StateDoseLimits(
    val maxWeeklyDirectPrimarySets: Int,
    val maxTargetSetsPerExercise: Int
)

data class StateBasedTrainingPolicyDefaults(
    val policyVersion: TrainingPolicyVersion,
    val doseLimitsByState: Map<AdaptationState, StateDoseLimits>,
    val productRestSecondsByClass: Map<RestClass, Int>,
    val conservativeEffort: EffortTarget,
    val establishedStrengthEffort: EffortTarget,
    val establishedGeneralOrHypertrophyEffort: EffortTarget
)
```

6. Validate defaults on construction:

- state keys exactly equal `AdaptationState.entries`;
- rest keys exactly equal `RestClass.entries`;
- every cap is positive and within `ExercisePrescription` bounds;
- every product rest value is in `1..1800`;
- each effort target is already bounded by its domain type.

7. Define the typed result/reason enums from the design. Keep `TrainingPolicyReason`
declaration order aligned to the dose, effort, then rest evaluation stages.
8. Implement:

```kotlin
fun evaluate(
    exercise: Exercise,
    basePrescription: ExercisePrescription,
    profile: UserProfile,
    fitnessGoals: Set<FitnessGoal>,
    programState: TrainingProgramState,
    priorUserRestPreference: UserRestPreference? = null
): TrainingPolicyResult
```

9. Validate versions, the entire ledger shape/count bounds, approved provenance,
review-policy equality, direct primary, and prescription shape before reading dose.
10. Compute remaining dose using `Long`. Return typed no-guidance before attempting to
copy a zero-set prescription.
11. Compute target sets only with `minOf`; never use `maxOf` against the base.
12. Resolve effort and rest in the precedence order from the design.
13. Build reasons in fixed stage order, then de-duplicate while retaining that order.
14. Return a copied prescription changing only target sets and guidance/rest fields.
15. Add `TrainingPolicyResultException`, whose message names only the typed reason and
does not echo user values.
16. Re-run the focused test. Expected GREEN: all policy tests pass.
17. Commit the pure policy slice with both repository-required trailers.

---

## Task 3: Integrate policy without changing the legacy path

### RED

1. Extend `DefaultExercisePrescriptionFactoryTest.kt` with:

```kotlin
@Test
fun noProgramState_returnsTheExactLegacyPrescription() {
    val exercise = exercise(ExerciseType.WEIGHT_REPS).copy(id = "legacy-press")
    val actual = factory.create(
        exercise,
        WorkoutGenerationContext(
            userProfile = UserProfile(goals = setOf(FitnessGoal.GENERAL_FITNESS))
        )
    )

    assertThat(actual).isEqualTo(
        ExercisePrescription(
            exerciseType = ExerciseType.WEIGHT_REPS,
            targetSets = 3,
            repRange = RepRange(10, 12),
            targetWeight = null,
            restSeconds = 90
        )
    )
}

@Test
fun reviewedProgramState_appliesGuidanceWithoutChangingConfirmedOrHistoricalLoad() {
    val actual = factory.create(approvedExercise, reviewedContext)
    assertThat(actual.targetWeight).isEqualTo(expectedExistingLoad)
    assertThat(actual.targetSets).isAtMost(base.targetSets)
    assertThat(actual.effortTarget).isEqualTo(EffortTarget(2, 4))
}

@Test
fun exhaustedDose_throwsTheTypedPolicyResultRatherThanReturningZeroSets() {
    val error = assertThrows(TrainingPolicyResultException::class.java) {
        factory.create(approvedExercise, exhaustedContext)
    }
    assertThat(error.result).isInstanceOf(TrainingPolicyResult.NoGuidance::class.java)
}
```

2. Extend `WorkoutGenerationContextBuilderTest.kt` with newest-explicit-preference
precedence, product-policy values ignored as user preferences, a 512-prescription bound,
and legacy flag-off behavior.
3. Extend the planner fixture context so every reviewed-enabled fixture carries:

```kotlin
TrainingProgramState(
    policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
    adaptationState = reviewedEligibility.adaptationState,
    weeklyLedger = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = 20_696L,
        timeZoneId = "UTC",
        catalogVersion = fixture.catalogVersion,
        reviewPolicyVersion = 1,
        directPrimarySets = emptyMap(),
        secondaryInvolvement = emptyMap(),
        unattributedWorkSets = emptyMap()
    )
)
```

4. Update fixture snapshot coverage for the prior-preference map.
5. Add persona assertions that reviewed-enabled prescriptions have policy guidance,
all selected IDs remain upstream-approved/eligible, loads remain confirmed/history-only,
and two fresh replays are identical.
6. Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*DefaultExercisePrescriptionFactoryTest' \
  --tests '*WorkoutGenerationContextBuilderTest' \
  --tests '*PlannerFixture*' \
  --tests '*FakeWorkoutPlannerTest' \
  --tests '*ExerciseEligibilityPolicyTest' \
  --rerun-tasks --no-daemon
```

Expected RED: factory assertions lack state policy, context lacks prior preferences, and
reviewed fixtures lack composed state.

### GREEN

7. Add to `WorkoutGenerationContext`:

```kotlin
val priorUserRestPreferences: Map<String, UserRestPreference> = emptyMap()
```

8. In `WorkoutGenerationContextBuilder`, derive the map once from recent completed
sessions in repository order (newest first):

- inspect no more than 512 exercise prescriptions;
- accept only `RestTargetSource.USER_PREFERENCE`;
- retain the first preference per exact exercise ID;
- do not convert product-policy guidance into a preference.

9. Refactor `DefaultExercisePrescriptionFactory.create` into:

```text
build current base prescription
  -> no program state: return base byte-for-byte
  -> program state: evaluate StateBasedTrainingPolicy
       -> Applied: return applied prescription
       -> NoGuidance/Failure: throw TrainingPolicyResultException
```

10. Pass `context.priorUserRestPreferences[exercise.id]` to the policy.
11. Do not change existing load selection, rep ranges, duration/distance, or manual
template construction.
12. Compose synthetic fixture state exactly as specified in RED and update deep-copy
snapshots for the new context field.
13. Re-run the focused command. Expected GREEN: all listed suites pass.
14. Commit integration and persona coverage with both repository-required trailers.

---

## Task 4: Persist every new prescription field in schema 11

### RED

1. Extend `WorkoutTemplateRepositoryTest.kt` so save and read assertions cover:

```kotlin
effortTarget = EffortTarget(2, 4)
restClass = RestClass.LONG
restTargetSource = RestTargetSource.USER_PREFERENCE
restSeconds = 240
```

2. Extend `WorkoutTemplateSessionTest.kt` to prove the full guidance survives template
save, frozen session creation, source-template edit, and source-template deletion.
3. Add malformed-row unit tests proving partial effort pairs and partial rest
class/source pairs fail loudly on repository mapping.
4. Add `Migration10To11Test.kt`. Create a real schema-10 database containing:

- one completed workout exercise with nondefault `restSeconds`;
- one template exercise with nondefault `restSeconds`;
- one ledger cache row.

Open current Room schema and assert:

- version is 11;
- the four new columns exist on both exercise tables and are null;
- every old value and cache row is unchanged;
- `PRAGMA foreign_key_check` is empty.

5. Rename/update the migration-chain test to start at each schema in `1..10` and finish
at 11. Assert all new columns are present/null for prior rows.
6. Run JVM mapping tests:

```bash
./gradlew testDebugUnitTest \
  --tests '*WorkoutTemplateRepositoryTest' \
  --tests '*ExercisePrescriptionTest' \
  --rerun-tasks --no-daemon
```

Expected RED: entity/mapping fields are absent.

7. With a configured emulator, run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
wallcrawl.elopenmike.com.core.database.Migration10To11Test,\
wallcrawl.elopenmike.com.core.database.MigrationChainTo11Test,\
wallcrawl.elopenmike.com.core.database.WorkoutTemplateSessionTest \
  --rerun-tasks --no-daemon
```

Expected RED: schema 11 and migration 10 → 11 do not exist.

### GREEN

8. Add nullable entity columns to `WorkoutExerciseEntity` and
`WorkoutTemplateExerciseEntity`:

```kotlin
val effortMinRir: Int? = null
val effortMaxRir: Int? = null
val restClass: RestClass? = null
val restTargetSource: RestTargetSource? = null
```

9. Map all four fields in both repository writers.
10. Add one shared internal mapping helper in the database package for reconstructing
`EffortTarget?`:

- both null → null;
- both present → bounded `EffortTarget`;
- only one present → loud `IllegalStateException` naming the missing field.

11. Require persisted `restClass` and `restTargetSource` to be both null or both
present; let `ExercisePrescription` enforce the final domain invariant.
12. Use the helpers in `WorkoutSessionMapper` and `WorkoutTemplateRepository`.
13. Change `WallCrawlDatabase` to version 11 and add:

```sql
ALTER TABLE workout_exercises ADD COLUMN effortMinRir INTEGER;
ALTER TABLE workout_exercises ADD COLUMN effortMaxRir INTEGER;
ALTER TABLE workout_exercises ADD COLUMN restClass TEXT;
ALTER TABLE workout_exercises ADD COLUMN restTargetSource TEXT;
ALTER TABLE workout_template_exercises ADD COLUMN effortMinRir INTEGER;
ALTER TABLE workout_template_exercises ADD COLUMN effortMaxRir INTEGER;
ALTER TABLE workout_template_exercises ADD COLUMN restClass TEXT;
ALTER TABLE workout_template_exercises ADD COLUMN restTargetSource TEXT;
```

14. Register `MIGRATION_10_11` in `ALL_MIGRATIONS`.
15. Extend `LegacyDatabaseFixtures` through schema 10, including the schema-10 ledger
cache table.
16. Update migration 8/9 tests that intentionally open the whole chain so their current
schema assertion is 11.
17. Re-run the JVM and connected focused commands. Expected GREEN: both succeed.
18. Commit persistence and migration coverage with both repository-required trailers.

---

## Task 5: Reconcile shipped and deferred documentation

1. Update `README.md`:

- state that Task 4 policy exists only on the reviewed-enabled path;
- describe direct-primary remaining-dose caps as product defaults, not science;
- describe nullable RIR and classified rest;
- state production remains legacy/disabled and manual templates are unchanged;
- replace Task 4 in “Next milestones” with the remaining human-approval/enablement and
Task 6 work.

2. Update `docs/architecture.md`:

- add the pure policy after `TrainingProgramState`;
- document typed no-guidance/failure behavior;
- document schema 11 and the four nullable columns;
- document explicit user-rest preference persistence and unchanged active timer;
- preserve local-first/privacy boundaries.

3. Update `docs/weekly-dose-ledger.md`:

- replace “no policy reads counts” with the exact Task 4 consumer;
- state only the production-disabled reviewed path reads them;
- document upper-cap-only arithmetic and no floor.

4. Update `docs/planner-evaluation.md`:

- state reviewed fixtures now carry synthetic composed program state;
- add dose/effort/rest replay invariants;
- keep synthetic approval warnings.

5. Update deterministic roadmap Task 4:

- mark it shipped behind the existing production-disabled reviewed gate;
- record actual file/type names and typed results;
- mark every Task 4 checkbox complete;
- retain Task 6/7 deferrals.

6. Search changed docs for placeholder tokens, unfinished markers, unsupported
medical/safety/optimality claims,
agent-directed prompts, and machine-local paths. Remove any match introduced by this
change.
7. Run `git diff --check`.
8. Commit documentation with both repository-required trailers.

---

## Task 6: Complete validation and hygiene

1. Run focused policy/integration/persistence JVM suites in one invocation.
2. Run both Python suites:

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
```

3. Run the complete Gradle verification with tasks forced:

```bash
./gradlew test lint assemble --rerun-tasks --stacktrace --no-daemon
```

4. Boot an API-36 `google_apis` emulator matching CI, disable window/transition/animator
scales, and run:

```bash
./gradlew connectedDebugAndroidTest --rerun-tasks --stacktrace --no-daemon
```

5. Run hygiene:

```bash
git diff --check
git status --short
git --no-pager diff --stat origin/main...HEAD
```

6. Search the complete diff for secret-like keys/tokens/passwords, debug prints,
temporary files, broad catches, silent fallbacks, production flag changes, network/
analytics/Health/Wear/LLM additions, and machine-local paths.
7. Commit any validation fixes with both repository-required trailers, then rerun the
smallest affected suite and the complete validation command whose evidence became stale.

---

## Task 7: Independent same-SHA review loop and delivery

1. Commit every implementation and documentation change.
2. Record the current head SHA.
3. Against the complete `origin/main...HEAD` diff at that exact SHA, obtain three
independent code reviews using:

- Claude Opus 4.8;
- Gemini 3.7 Flash;
- Grok 4.6.

Each review covers correctness/code gaps, local-first security/privacy/trust boundaries,
performance/cost, tests, and documentation.

4. Fix every valid finding, run affected and complete validation, and commit.
5. Repeat all three reviews against the new same SHA. Continue until all three report no
findings on one SHA.
6. Immediately before delivery, fetch `origin/main`.
7. If `origin/main` advanced, merge it without rewriting history, resolve deliberately,
rerun complete validation, commit if needed, and repeat all three reviews on the
integrated SHA until all are clean together.
8. Push the branch.
9. Open a non-draft pull request against `main`.
10. Verify:

- remote head SHA equals local `HEAD`;
- base is `main`;
- PR file/commit scope matches this task;
- mergeability is not blocked by a conflict;
- available checks are attached to the exact head SHA.
11. Report the PR URL, exact SHA, validation evidence, three-reviewer clean SHA, and
production-disabled rollout boundary to the creating session.
