# Body-Aware Personalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional local-only body measurements and movement capabilities so v1 planning uses capability/history while measurements remain stored but engine-unused.

**Architecture:** Extend `UserProfile` and reviewed exercise programming with capability and body-mass-demand data. Apply explicit constraints as hard filters, capability and body-mass demand as explainable ranking inputs, demonstrated history as stronger evidence, and whole-session validation after deterministic selection.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Coroutines/Flow, Python standard-library catalog tooling, JUnit 4, Truth, Turbine, Android instrumentation tests.

---

## Scope and Ordering

This plan refines Tasks 3-5 of `2026-08-28-adaptive-coach-product.md`. It does not add Health Connect, accounts, cloud storage, medical recommendations, nutrition, or an LLM.

Implementation order is deliberate:

1. profile types and persistence;
2. onboarding/profile UX;
3. reviewed exercise-demand metadata;
4. hard eligibility;
5. soft ranking and history evidence;
6. prescription scaling and whole-session validation;
7. fixture evaluation and rollout.

## Target File Map

**New files**

- `app/src/main/java/wallcrawl/elopenmike/com/core/model/BodyContext.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/BodyAwareEligibilityPolicy.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/BodyAwareExerciseRanker.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/BodyAwarePrescriptionPolicy.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/BodyAwareProgramValidator.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/BodyContextEditor.kt`
- `tools/workout-guide/body-demand-schema.json`
- `app/src/test/resources/body-aware-personas/*.json`

**Modified files**

- `core/model/UserProfile.kt`, `core/model/Exercise.kt`, `core/model/WorkoutGenerationContext.kt`
- `core/database/entity/Entities.kt`, `WallCrawlDatabase.kt`, `UserProfileRepository.kt`
- onboarding and Profile UI/ViewModels
- Workout Guide importer, programming overrides, parser, and tests
- `WorkoutGenerationContextBuilder.kt`, `ExerciseFilter.kt`, `FakeWorkoutPlanner.kt`
- `DefaultExercisePrescriptionFactory.kt`, `GeneratedWorkoutValidator.kt`
- README, architecture, and adaptive-coach roadmap

---

### Task 1: Define Body Context and Exercise Demand Types

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/BodyContext.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/UserProfile.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Exercise.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/model/BodyContextTest.kt`

- [ ] **Step 1: Write failing body-measurement tests**

```kotlin
@Test
fun measurements_doNotExposeDerivedBmi() {
    assertThat(BodyMeasurements::class.java.declaredMethods.map { it.name })
        .doesNotContain("getBmi")
}

@Test
fun defaultCapabilities_areUnknownRatherThanComfortable() {
    val capabilities = MovementCapabilities.unknown()
    assertThat(capabilities.values.values)
        .containsExactlyElementsIn(List(MovementCapabilityType.entries.size) { CapabilityLevel.UNKNOWN })
}
```

- [ ] **Step 2: Run tests and verify RED**

```bash
./gradlew testDebugUnitTest --tests '*BodyContextTest'
```

Expected: compilation fails because the body-context types do not exist.

- [ ] **Step 3: Add the profile types**

```kotlin
data class BodyMeasurements(
    val weightKg: Double? = null,
    val heightCm: Double? = null
)

enum class MovementCapabilityType {
    IMPACT,
    FLOOR_TRANSITION,
    UNSUPPORTED_SQUAT,
    UPPER_BODY_BODYWEIGHT_PUSH,
    VERTICAL_PULL_OR_HANG,
    BALANCE_WITHOUT_SUPPORT,
    CONTINUOUS_ACTIVITY
}

enum class CapabilityLevel { UNKNOWN, COMFORTABLE, LIMITED, AVOID }

data class MovementCapabilities(
    val values: Map<MovementCapabilityType, CapabilityLevel>
) {
    operator fun get(type: MovementCapabilityType): CapabilityLevel =
        values[type] ?: CapabilityLevel.UNKNOWN

    companion object {
        fun unknown() = MovementCapabilities(
            MovementCapabilityType.entries.associateWith { CapabilityLevel.UNKNOWN }
        )
    }
}
```

Add `bodyMeasurements: BodyMeasurements = BodyMeasurements()` and `movementCapabilities: MovementCapabilities = MovementCapabilities.unknown()` to `UserProfile`.

- [ ] **Step 4: Add reviewed exercise-demand types**

```kotlin
enum class BodyMassDemand { MINIMAL, PARTIAL, SUBSTANTIAL, FULL }
enum class ImpactLevel { NONE, LOW, HIGH }
enum class SupportRequirement { SUPPORTED, OPTIONAL_SUPPORT, UNSUPPORTED }

data class ExerciseDemandMetadata(
    val progressionFamily: String,
    val bodyMassDemand: BodyMassDemand,
    val impactLevel: ImpactLevel,
    val requiresFloorTransition: Boolean,
    val balanceDemand: CapabilityLevel,
    val supportRequirement: SupportRequirement,
    val capabilityRequirements: Set<MovementCapabilityType>,
    val regressionExerciseIds: List<String>
)
```

Add nullable `demand: ExerciseDemandMetadata?` to `ExerciseProgrammingMetadata`.

- [ ] **Step 5: Run tests and commit**

```bash
./gradlew testDebugUnitTest --tests '*BodyContextTest' --tests '*Exercise*'
git add app/src/main app/src/test
git commit -m "feat: define body-aware planning context"
```

---

### Task 2: Persist Body Context with Room Migration 7 to 8

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/UserProfileRepository.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/database/repository/UserProfileRepositoryTest.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration7To8Test.kt`

- [ ] **Step 1: Write failing repository round-trip tests**

```kotlin
@Test
fun profileRoundTrip_preservesCanonicalMeasurementsAndCapabilities() = runTest {
    val profile = UserProfile(
        bodyMeasurements = BodyMeasurements(weightKg = 136.1, heightCm = 182.9),
        movementCapabilities = MovementCapabilities(
            MovementCapabilityType.entries.associateWith {
                if (it == MovementCapabilityType.IMPACT) CapabilityLevel.AVOID
                else CapabilityLevel.COMFORTABLE
            }
        )
    )
    repository.saveProfile(profile)
    assertThat(repository.getProfileOnce().bodyMeasurements).isEqualTo(profile.bodyMeasurements)
    assertThat(repository.getProfileOnce().movementCapabilities)
        .isEqualTo(profile.movementCapabilities)
}
```

- [ ] **Step 2: Write failing migration test**

Create a version-7 profile row, migrate to version 8, and assert null `bodyWeightKg`, null `heightCm`, capability JSON decoding to all `UNKNOWN`, unchanged onboarding/theme/profile revision, and `PRAGMA foreign_key_check = 0`.

- [ ] **Step 3: Add entity columns and migration**

```sql
ALTER TABLE user_profiles ADD COLUMN bodyWeightKg REAL;
ALTER TABLE user_profiles ADD COLUMN heightCm REAL;
ALTER TABLE user_profiles ADD COLUMN movementCapabilitiesJson TEXT NOT NULL DEFAULT '';
```

Bump the database to 8, register `MIGRATION_7_8`, and update older migration tests to chain through it.

- [ ] **Step 4: Add bounded codecs and validation**

Accept only finite `weightKg` in `1.0..500.0` and finite `heightCm` in `50.0..300.0`. Decode unknown capability keys/values to `UNKNOWN`; never decode malformed data as `COMFORTABLE`.

```kotlin
private fun validateBodyMeasurements(value: BodyMeasurements) {
    require(value.weightKg == null || value.weightKg.isFinite() && value.weightKg in 1.0..500.0)
    require(value.heightCm == null || value.heightCm.isFinite() && value.heightCm in 50.0..300.0)
}
```

- [ ] **Step 5: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*UserProfileRepositoryTest'
./gradlew connectedDebugAndroidTest
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: persist local body context"
```

---

### Task 3: Add Body and Movement Onboarding/Profile UX

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingUiState.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/BodyContextEditor.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/ProfileViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/ProfileScreen.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingViewModelTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/feature/profile/ProfileViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel tests**

```kotlin
@Test
fun complete_normalizesOptionalMeasurementsAndPersistsCapabilities() = runTest {
    viewModel.updateWeightInput("300")
    viewModel.updateHeightFeet("6")
    viewModel.updateHeightInches("0")
    viewModel.updateCapability(MovementCapabilityType.IMPACT, CapabilityLevel.LIMITED)
    viewModel.complete()

    val saved = repository.saved.single()
    assertThat(saved.bodyMeasurements.weightKg).isWithin(0.01).of(136.08)
    assertThat(saved.bodyMeasurements.heightCm).isWithin(0.01).of(182.88)
    assertThat(saved.movementCapabilities[MovementCapabilityType.IMPACT])
        .isEqualTo(CapabilityLevel.LIMITED)
}
```

- [ ] **Step 2: Add an eighth onboarding step**

Insert `BODY_MOVEMENT` after `EXPERIENCE_UNIT`. Measurements are optional. Each capability must have an explicit selected value; `UNKNOWN` is a valid selection.

- [ ] **Step 3: Keep display-unit drafts out of the domain**

The ViewModel stores text drafts and converts to kg/cm only during validation/save:

```kotlin
internal fun poundsToKg(value: Double): Double = value * 0.45359237
internal fun feetAndInchesToCm(feet: Int, inches: Double): Double =
    (feet * 12.0 + inches) * 2.54
```

Blank measurement fields produce null values. Invalid non-blank input blocks completion with actionable copy.

- [ ] **Step 4: Build respectful capability controls**

Use cards labeled Comfortable, Limited, Avoid for now, and Not sure. Add semantics with selected state and a clear reason: "Used to choose supported progressions." Do not display BMI or body labels.

- [ ] **Step 5: Add Profile editing and deletion**

`BodyContextEditor` supports updating measurements/capabilities and a `Remove measurements` action. Save through one operation:

```kotlin
userProfileRepository.saveProfile(
    current.copy(
        bodyMeasurements = editedMeasurements,
        movementCapabilities = editedCapabilities
    )
)
```

- [ ] **Step 6: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*OnboardingViewModelTest' --tests '*ProfileViewModelTest'
./gradlew lintDebug assembleDebug
git add app/src/main app/src/test
git commit -m "feat: collect optional body and movement context"
```

---

### Task 4: Add Reviewed Exercise-Demand Metadata

**Files:**
- Create: `tools/workout-guide/body-demand-schema.json`
- Modify: `tools/workout-guide/programming-overrides.json`
- Modify: `tools/workout-guide/import_catalog.py`
- Modify: `tools/workout-guide/test_import_catalog.py`
- Modify: `tools/workout-guide/test_programming_overrides.py`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/exercise/workoutguide/WorkoutGuideCatalogParser.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/exercise/workoutguide/WorkoutGuideCatalogParserTest.kt`

- [ ] **Step 1: Write failing schema/graph tests**

Reject unknown enums, body fractions outside `0.0..1.5`, blank progression families, unknown regressions, self-regressions, regression cycles, and regressions with incompatible movement patterns.

- [ ] **Step 2: Define deterministic JSON shape**

```json
{
  "demand": {
    "progressionFamily": "horizontal-push",
    "bodyMassDemand": "partial",
    "impactLevel": "none",
    "requiresFloorTransition": true,
    "balanceDemand": "comfortable",
    "supportRequirement": "unsupported",
    "capabilityRequirements": ["upper_body_bodyweight_push", "floor_transition"],
    "regressionExerciseIds": ["incline-push-up"]
  }
}
```

- [ ] **Step 3: Update importer and Android parser**

Keep `demand` optional for browsing/manual templates. Automatic eligibility introduced in Task 5 requires it.

- [ ] **Step 4: Review the automatic-planning cohort**

Review demand metadata for every exercise currently eligible/preferred in automatic plans, prioritizing bodyweight, impact, floor, hanging, squat/lunge, and band progression families. Each reviewed family must contain a supported or lower-demand regression before the policy can hard-filter its harder members.

- [ ] **Step 5: Verify generated catalog integrity and commit**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 tools/workout-guide/import_catalog.py \
  --source /Users/elopenmike/build/Apps/Workouts/guide/workout-guide \
  --check
./gradlew connectedDebugAndroidTest
git add tools app/src/main app/src/androidTest
git commit -m "feat: add reviewed exercise demand metadata"
```

---

### Task 5: Implement Body-Aware Hard Eligibility

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/BodyAwareEligibilityPolicy.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/exercise/ExerciseFilter.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilder.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/BodyAwareEligibilityPolicyTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/exercise/ExerciseFilterTest.kt`

- [ ] **Step 1: Write failing eligibility tests**

```kotlin
@Test
fun explicitAvoid_blocksRequiredCapability_butWeightAloneDoesNot() {
    val avoidPush = profile(
        weightKg = 180.0,
        capability = UPPER_BODY_BODYWEIGHT_PUSH to AVOID
    )
    assertThat(policy.isEligible(pushUp, avoidPush)).isFalse()

    val unknownPush = profile(
        weightKg = 180.0,
        capability = UPPER_BODY_BODYWEIGHT_PUSH to UNKNOWN
    )
    assertThat(policy.isEligible(pushUp, unknownPush)).isTrue()
}
```

- [ ] **Step 2: Implement structured decisions**

```kotlin
data class EligibilityDecision(
    val eligible: Boolean,
    val reasons: Set<EligibilityReason>
)

enum class EligibilityReason {
    MISSING_REVIEWED_DEMAND,
    EXPLICIT_CAPABILITY_AVOID,
    HIGH_IMPACT_DISALLOWED,
    TRAINING_CONSTRAINT,
    MISSING_EQUIPMENT
}
```

- [ ] **Step 3: Apply only true hard filters**

Reject missing reviewed demand for automatic plans, explicit `AVOID`, high impact under `LOW_IMPACT_ONLY`, existing constraints, and equipment mismatch. Do not inspect BMI.

- [ ] **Step 4: Preserve manual behavior and fallback errors**

Manual templates still expose all 302 exercises with warnings. If automatic candidates become empty, return a reason identifying the profile field that can be reviewed; never silently drop explicit constraints.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*BodyAwareEligibilityPolicyTest' --tests '*ExerciseFilterTest'
git add app/src/main app/src/test
git commit -m "feat: enforce movement capability eligibility"
```

---

### Task 6: Implement Capability-Aware Ranking and History Evidence

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/BodyAwareExerciseRanker.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutHistoryAnalyzer.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutGenerationContext.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/BodyAwareExerciseRankerTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutHistoryAnalyzerTest.kt`

- [ ] **Step 1: Write failing ranking tests**

Test Limited floor transition preferring standing alternatives, Limited push preferring supported regressions, Unknown impact preferring low impact, Comfortable capability preserving normal rank, and deletion of measurements producing a valid deterministic order.

- [ ] **Step 2: Define explainable scores**

```kotlin
data class RankedExercise(
    val exercise: Exercise,
    val score: Int,
    val reasons: List<RankingReason>
)

enum class RankingReason {
    DEMONSTRATED_SUCCESS,
    SUPPORTED_REGRESSION,
    LOWER_IMPACT,
    NO_FLOOR_TRANSITION,
    LOWER_BALANCE_DEMAND,
    PRIMARY_MUSCLE_MATCH
}
```

Use integer weights in one versioned policy object; stable exercise ID is the final tie-break.

- [ ] **Step 3: Add demonstrated-capability evidence**

An exercise is demonstrated only after at least two completed sessions where all started prescribed sets have valid outcomes. Evidence removes soft penalties for that exercise and easier members of the same progression family; it never overrides explicit `AVOID` or a `TrainingConstraint`.

- [ ] **Step 4: Prove measurements are excluded from v1 ranking**

```kotlin
@Test
fun otherwiseEqualProfiles_getIdenticalRankingWhenMeasurementsDiffer() {
    val light = profile(bodyMeasurements = BodyMeasurements(weightKg = 60.0, heightCm = 170.0))
    val heavy = profile(bodyMeasurements = BodyMeasurements(weightKg = 140.0, heightCm = 170.0))
    assertThat(ranker.rank(candidates, light)).isEqualTo(ranker.rank(candidates, heavy))
}
```

Keep `BodyMeasurements` out of `BodyAwareExerciseRanker` and every local-LLM context. Capability, reviewed metadata, and demonstrated history drive ranking.

- [ ] **Step 5: Replace planner ordering**

Feed hard-filtered split candidates through `BodyAwareExerciseRanker`, then retain movement-pattern diversity and session slot rules.

- [ ] **Step 6: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*BodyAwareExerciseRankerTest' \
  --tests '*WorkoutHistoryAnalyzerTest' --tests '*FakeWorkoutPlannerTest'
git add app/src/main app/src/test
git commit -m "feat: rank exercises by demonstrated capability"
```

---

### Task 7: Scale Prescriptions Conservatively and Validate the Session

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/BodyAwarePrescriptionPolicy.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/BodyAwareProgramValidator.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/DefaultExercisePrescriptionFactory.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/GeneratedWorkoutValidator.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/today/TodayViewModel.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/BodyAwarePrescriptionPolicyTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/BodyAwareProgramValidatorTest.kt`

- [ ] **Step 1: Write failing precedence tests**

Assert explicit Avoid blocks, demonstrated history can relax capability soft ranking only, Limited can reduce sets/progression, return-after-break and Limited choose the more conservative set count, and measurements never affect target weight.

- [ ] **Step 2: Implement conservative scaling**

```kotlin
data class PrescriptionAdjustment(
    val maximumSets: Int?,
    val progressionStepsDown: Int,
    val reasons: Set<PrescriptionReason>
)

enum class PrescriptionReason {
    LIMITED_CAPABILITY,
    UNKNOWN_CAPABILITY,
    RETURN_AFTER_BREAK,
    DEMAND_REGRESSION
}
```

Adjust only set count, duration, or progression-family member. Never increase load/volume and never derive external load from body measurements.

- [ ] **Step 3: Add whole-session violations**

```kotlin
sealed interface BodyAwareProgramViolation {
    data class CapabilityMismatch(val exerciseId: String) : BodyAwareProgramViolation
    data class MissingRegression(val exerciseId: String) : BodyAwareProgramViolation
    data class ExcessiveHighDemandCount(val count: Int) : BodyAwareProgramViolation
    data class DuplicateProgressionFamily(val family: String) : BodyAwareProgramViolation
}
```

Combine these with duplicate, difficulty, fatigue, volume, and duration validation from Adaptive Coach Task 5.

- [ ] **Step 4: Validate before Today and persistence**

Run structural validation, body-aware program validation, and one deterministic repair attempt. If repair fails, surface a typed planning failure.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*BodyAwarePrescriptionPolicyTest' \
  --tests '*BodyAwareProgramValidatorTest' --tests '*TodayViewModelTest'
git add app/src/main app/src/test
git commit -m "feat: validate body-aware workout plans"
```

---

### Task 8: Add Persona Fixtures and Staged Rollout

**Files:**
- Create: `app/src/test/resources/body-aware-personas/beginner-limited-push.json`
- Create: `app/src/test/resources/body-aware-personas/band-low-impact.json`
- Create: `app/src/test/resources/body-aware-personas/advanced-demonstrated-pull.json`
- Create: `app/src/test/resources/body-aware-personas/returning-limited-balance.json`
- Create: `app/src/test/resources/body-aware-personas/no-measurements.json`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/BodyAwarePersonaTest.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/PlannerFeatureFlags.kt`

- [ ] **Step 1: Add deterministic fixture loader**

Each fixture records profile, capabilities, optional measurements, equipment, history, expected allowed/blocked IDs, expected ranking reasons, and policy version.

- [ ] **Step 2: Assert invariants across every fixture**

Measurements never change v1 eligibility/ranking/dose; Avoid always wins; measurements never produce load; every plan fits equipment; every selected exercise has reviewed demand; deleting measurements still produces the same legal plan.

- [ ] **Step 3: Add local rollout flag**

```kotlin
data class PlannerFeatureFlags(
    val bodyAwareEligibility: Boolean = true,
    val bodyAwareRanking: Boolean = false,
    val capabilityEvidence: Boolean = false
)
```

Eligibility ships first. Enable ranking after all fixtures pass. Enable evidence only after replaying mixed-unit and incomplete-session history.

- [ ] **Step 4: Turn on ranking and evidence in separate commits**

Run the full fixture suite before each flag change. A flag changes deterministic policy only; no remote configuration or analytics is added.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*BodyAwarePersonaTest'
git add app/src/main app/src/test
git commit -m "test: add body-aware planner personas"
```

---

### Task 9: Document Privacy, Explanations, and Full Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/superpowers/plans/2026-08-28-adaptive-coach-product.md`
- Create: `docs/body-aware-personalization.md`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Document current behavior**

Explain optional measurements, required capability choices, local-only storage, no BMI gating, precedence, reviewed demand metadata, measurement deletion, and recommendation reasons.

- [ ] **Step 2: Reconcile roadmap Tasks 3-5**

Mark body-demand review, capability eligibility, deterministic ranking, and whole-session validation as the next personalization milestone. Keep LLM work blocked behind this milestone.

- [ ] **Step 3: Add catalog/persona checks to CI**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
./gradlew test lint assembleDebug --stacktrace --no-daemon
```

CI must execute the body-demand metadata checks and persona fixture tests through these suites.

- [ ] **Step 4: Run complete verification**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 tools/workout-guide/import_catalog.py \
  --source /Users/elopenmike/build/Apps/Workouts/guide/workout-guide \
  --check
./gradlew test lint assembleDebug --stacktrace --no-daemon
./gradlew connectedDebugAndroidTest --no-daemon
git diff --check
```

Expected: all Python, JVM, lint, build, and Android tests pass; importer reports no drift.

- [ ] **Step 5: Commit**

```bash
git add README.md docs .github/workflows/ci.yml
git commit -m "docs: explain body-aware personalization"
```

## Release Gates

- Existing users migrate from schema 7 to 8 without re-onboarding or data loss.
- Optional measurements can be omitted or deleted.
- BMI is not implemented, and measurements are not consumed by v1 planning.
- Explicit capability Avoid and training constraints always win.
- Every automatic exercise has reviewed demand metadata.
- Limited/Unknown capability prefers regressions without stranding equipment profiles.
- Demonstrated success overrides only soft penalties.
- Measurements never create an external starting load.
- Every decision is deterministic and carries structured reasons.
- All profile, importer, planner, persona, migration, lint, build, and connected tests pass offline.
