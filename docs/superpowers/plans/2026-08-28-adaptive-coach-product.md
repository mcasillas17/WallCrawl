# WallCrawl Adaptive Coach Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn WallCrawl's current local-first vertical slice into a safe, transparent, deterministic adaptive coach, then add an optional on-device LLM that ranks and explains only within validated boundaries.

**Architecture:** Keep Room as the source of truth and preserve the existing `WorkoutPlanner -> GeneratedWorkoutValidator` seam. Add explicit onboarding, reviewed automatic-planning eligibility, deterministic policy/progression engines, richer workout logging, and a replayable evaluation corpus before introducing a provider-agnostic local model ranker.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Coroutines/Flow, Coil SVG, JUnit 4, Truth, Turbine, Python standard-library catalog importer, optional Android on-device inference provider.

---

## Audit Findings and Required Responses

> **Reconciled 2026-08-29** against `main` at `de87da6`, after PRs #12, #13, #15, #16 and #17.
> Every status below was checked against the code, not against the merge messages.
> Findings that shipped are kept rather than deleted so the reasoning stays readable.

| Finding | Status | Evidence today | Implementation response |
| --- | --- | --- | --- |
| No first-run onboarding | **Shipped** (#15, #16) | `AppRoutes.ONBOARDING` exists; a 7-step wizard collects goals, equipment and confirmed loads | Task 1 complete |
| Unsafe starting-load defaults | **Shipped** (#15) | `sampleStartingWeight()` is gone; `suggestedTargetWeight()` returns history-derived load, else `confirmedStartingLoads`, else null | Task 2 complete |
| Planner ignores experience, frequency, and calculated recovery | **Open** | `FakeWorkoutPlanner` and `ExerciseFilter` reference none of `experienceLevel`, `trainingFrequencyDaysPerWeek`, `recentlyTrainedMuscles` | Tasks 3-4 |
| Only 12/302 exercises have reviewed programming | **Superseded** (#13) | 117/302 reviewed, covering every muscle group with beginner options; the remaining gap is coverage of what the planner reaches for, not volume | Task 3, narrowed |
| Unreviewed exercises can fill plans in catalog order | **Partly shipped** (#13) | Compound slots order by primary-muscle match, then fatigue, spread across movement patterns; accessory slots prefer isolation work that trains the split. Unreviewed exercises can still be selected, but no longer in alphabetical order | Task 4, narrowed |
| Generated validation is structural, not program-level | **Open** | `GeneratedWorkoutValidator` has no duplicate, fatigue, weekly-volume or difficulty checks | Task 5 |
| Gym-floor logging lacks rest timer, RPE/RIR, fast edits, substitutions, and a finish guard | **Open** | `restSeconds` is persisted and never counted down; no RPE/RIR input exists | Tasks 6-7 |
| No deterministic progression, deload, or program state | **Partly shipped** | Double progression lives in `DefaultExercisePrescriptionFactory`; Epley e1RM in `WorkoutHistoryAnalyzer` and `ProgressCalculator`. Deloads and program blocks remain absent | Task 8, narrowed |
| Workout Guide dropped Everkinetic instructions | **Open** | No `instructionSteps` or `formCues` anywhere in the runtime catalog | Task 9 |
| Progress/history and template editing are shallow | **Open** | Progress is a single screen with no drill-down; the template editor still changes set count only | Task 10 |
| Local privacy promise is underspecified | **Open** | `android:allowBackup="true"` still ships; no export, delete or diagnostic policy | Task 11 |
| No systematic planner evaluation corpus | **Open** | Unit tests cover components. CI now runs the Python suites, including a guard on the shipped programming metadata, which is a foundation for this rather than a substitute | Task 12 |
| No production local model and no runtime capability tier | **Open** | `FakeWorkoutPlanner` is still the only `WorkoutPlanner` | Task 13 |
| Static catalog storage is already appropriate | Unchanged | 302 records parsed once from assets | Keep asset/in-memory storage; do not add Room/FTS without measured need |
| Kabi already covers broad tracker features | Unchanged | Its listing advertises nutrition, recovery, backup, and beginner features | Keep WallCrawl focused on transparent local adaptation rather than feature-parity sprawl |

### Findings added since the plan was written

These came out of the reviews behind #12 and #13 and are not yet reflected in any task.

| Finding | Evidence | Where it belongs |
| --- | --- | --- |
| Difficulty is reviewed for 117 exercises and read by nothing | Onboarding now asks the user to declare an experience level that no selection code consults, so a self-declared beginner can be led with a lift marked advanced | Task 3 or 4, and it is now the cheapest real personalization left |
| 14 planner-eligible timed holds cannot be reviewed at all | The schema requires a `recommendedRepRange` that duration prescriptions never read, so planks, dead hangs and wall sits can carry no mechanics, fatigue or coaching note | Task 3, as a schema change before the next authoring batch |
| A band-only profile is served almost entirely by unreviewed entries | 3 of roughly 19 band exercises are reviewed; its Push day contains no pressing movement, because band exercises qualify for Push only through a secondary Shoulders tag | Task 3 coverage, and Task 4 should weight primary-muscle matches above secondary ones |
| `barbell-back-squat` and `barbell-deadlift` are labelled advanced while bench, overhead press and row are intermediate | Two reviewers disagreed on whether that is correct. Under an experience gate, an intermediate lifter would receive bench, press and rows but no squat or deadlift | Decide before Task 3-4 gates on difficulty, not after |

## Product Rules

1. A complete workout must remain available offline without an account or model.
2. Deterministic code owns eligibility, safety limits, dosage, progression, deloads, substitutions, validation, and persistence.
3. Automatic plans may use only approved programming records; all 302 exercises remain browseable and manually selectable.
4. No unconfirmed starting load is prescribed.
5. Recommendation values and performed values remain separate immutable history.
6. The model may rank approved candidates, parse preferences, and explain choices; it may not invent IDs or dosage.
7. Nutrition, fasting, social feeds, cloud-first chat, AI personality, and progress-photo sharing are out of scope.

## Target File Map

**New domain and policy files**

- `app/src/main/java/wallcrawl/elopenmike/com/core/model/TrainingConstraint.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/model/TrainingProgram.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/TrainingPolicy.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ExerciseEligibilityPolicy.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ExerciseRanker.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ProgressionEngine.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ProgramValidator.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/LocalModelRuntime.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/LocalWorkoutRanker.kt`

**New feature files**

- `app/src/main/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingScreen.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingViewModel.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/feature/history/WorkoutHistoryDetailScreen.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/feature/history/WorkoutHistoryDetailViewModel.kt`

**Generated/content files**

- `tools/workout-guide/programming-overrides.json`
- `tools/workout-guide/coaching-overrides.json`
- `tools/workout-guide/review-schema.json`
- `app/src/main/assets/workout-guide/catalog.json`

**Modified integration files**

- `UserProfile.kt`, `WorkoutGenerationContext.kt`, `Exercise.kt`, `Workout.kt`
- `Entities.kt`, `Daos.kt`, `WallCrawlDatabase.kt`, repositories
- `FakeWorkoutPlanner.kt`, `GeneratedWorkoutValidator.kt`, `WorkoutGenerationContextBuilder.kt`
- Today, Profile, Templates, Active Workout, Progress screens/ViewModels
- `WallCrawlApplication.kt`, `WallCrawlApp.kt`, `README.md`, `docs/architecture.md`

---

### Task 1: Add Explicit Onboarding and Training Constraints

> **Shipped in #15 and #16.** Onboarding, `TrainingConstraint`, bodyweight-only defaults,
> `onboardingCompleted` and the 4->5 migration all exist. Re-read the code before running
> any step here; treat remaining steps as verification rather than implementation.

**Status: Done.** Commit `802ba67` (`feat: add safe training onboarding`).

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/TrainingConstraint.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingViewModel.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/UserProfile.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/UserProfileRepository.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/app/WallCrawlApp.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingViewModelTest.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration4To5Test.kt`

- [x] **Step 1: Write failing profile and onboarding tests**

```kotlin
@Test
fun freshProfile_requiresOnboardingAndHasNoAssumedGym() {
    val profile = UserProfile()
    assertThat(profile.onboardingCompleted).isFalse()
    assertThat(profile.availableEquipment).containsExactly(StandardEquipment.BODYWEIGHT)
    assertThat(profile.confirmedStartingLoads).isEmpty()
}

@Test
fun completeOnboarding_persistsAllRequiredPlanningInputs() = runTest {
    viewModel.complete(
        name = "Alex",
        goal = FitnessGoal.STRENGTH,
        experience = ExperienceLevel.BEGINNER,
        daysPerWeek = 3,
        durationMinutes = 45,
        unit = WeightUnit.KG,
        equipment = setOf(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL),
        constraints = setOf(TrainingConstraint.SHOULDER_SENSITIVE)
    )
    assertThat(repository.saved.single().onboardingCompleted).isTrue()
}
```

- [x] **Step 2: Run the tests and verify the missing fields fail compilation**

Run:

```bash
./gradlew testDebugUnitTest --tests '*OnboardingViewModelTest' --tests '*UserProfile*'
```

Expected: FAIL because onboarding and constraint fields do not exist.

- [x] **Step 3: Add conservative profile fields and constraint vocabulary**

Add these properties to the existing `UserProfile` constructor:

```kotlin
enum class TrainingConstraint {
    SHOULDER_SENSITIVE,
    ELBOW_SENSITIVE,
    WRIST_SENSITIVE,
    LOWER_BACK_SENSITIVE,
    HIP_SENSITIVE,
    KNEE_SENSITIVE,
    LOW_IMPACT_ONLY
}

val onboardingCompleted: Boolean = false
val availableEquipment: List<String> = listOf(StandardEquipment.BODYWEIGHT)
val trainingConstraints: Set<TrainingConstraint> = emptySet()
val returningAfterBreakWeeks: Int = 0
val confirmedStartingLoads: Map<String, Double> = emptyMap()
```

- [x] **Step 4: Add Room schema version 5 and migration**

Add `onboardingCompleted`, `trainingConstraintsJson`, `returningAfterBreakWeeks`, and `confirmedStartingLoadsJson` to `UserProfileEntity`. `MIGRATION_4_5` must add non-destructive columns with conservative defaults and must not mark an existing profile as onboarded.

```sql
ALTER TABLE user_profiles ADD COLUMN onboardingCompleted INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_profiles ADD COLUMN trainingConstraintsJson TEXT NOT NULL DEFAULT '';
ALTER TABLE user_profiles ADD COLUMN returningAfterBreakWeeks INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_profiles ADD COLUMN confirmedStartingLoadsJson TEXT NOT NULL DEFAULT '';
```

- [x] **Step 5: Implement one atomic onboarding save**

Add `saveProfile(profile: UserProfile)` to `UserProfileRepository`; do not fire one database revision per onboarding field. Validate days `2..6`, duration `20..120`, break weeks `0..520`, non-empty equipment, and finite non-negative confirmed loads.

- [x] **Step 6: Route fresh installs to onboarding**

Add `AppRoutes.ONBOARDING`; derive start content from the profile flow. Do not render or generate Today until `onboardingCompleted == true`.

- [x] **Step 7: Run focused tests**

```bash
./gradlew testDebugUnitTest --tests '*OnboardingViewModelTest' --tests '*UserProfileRepositoryTest'
```

Expected: PASS.

- [x] **Step 8: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add safe training onboarding"
```

---

### Task 2: Remove Unsafe Starting Loads

> **Shipped in #15.** `sampleStartingWeight()` is deleted. `suggestedTargetWeight()` now
> returns a history-derived load, then a load the user confirmed during onboarding, then
> null. No number is invented.

**Status: Done.** Commit `65ad03c` (`fix: remove unconfirmed starting loads`).

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/DefaultExercisePrescriptionFactory.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutViewModel.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/DefaultExercisePrescriptionFactoryTest.kt`

- [x] **Step 1: Add failing unit-conversion and unknown-baseline tests**

```kotlin
@Test
fun noHistoryAndNoConfirmedBaseline_doesNotPrescribeLoad() {
    val prescription = factory.create(
        benchPress,
        context(profile = UserProfile(preferredUnit = WeightUnit.KG))
    )
    assertThat(prescription.targetWeight).isNull()
}

@Test
fun confirmedBaseline_isUsedInTheProfilesUnit() {
    val profile = UserProfile(
        preferredUnit = WeightUnit.KG,
        confirmedStartingLoads = mapOf("barbell-bench-press" to 40.0)
    )
    assertThat(factory.create(benchPress, context(profile)).targetWeight).isEqualTo(40.0)
}
```

- [x] **Step 2: Verify the old sample loads fail**

```bash
./gradlew testDebugUnitTest --tests '*DefaultExercisePrescriptionFactoryTest'
```

Expected: FAIL because `sampleStartingWeight()` returns hard-coded values.

- [x] **Step 3: Delete `sampleStartingWeight()` and use confirmed/history values only**

```kotlin
private fun suggestedTargetWeight(
    exercise: Exercise,
    context: WorkoutGenerationContext,
    targetRepMaximum: Int
): Double? {
    progressionFromHistory(exercise, context, targetRepMaximum)?.let { return it }
    return context.userProfile.confirmedStartingLoads[exercise.id]
}
```

- [x] **Step 4: Make first-load entry explicit in the logger**

When target load is null, label the field `Choose starting load` and do not prefill a number. Persist the performed value; later progression uses that actual value.

- [x] **Step 5: Run focused tests and commit**

```bash
./gradlew testDebugUnitTest --tests '*DefaultExercisePrescriptionFactoryTest' --tests '*ActiveWorkoutViewModelTest'
git add app/src/main app/src/test
git commit -m "fix: remove unconfirmed starting loads"
```

---

### Task 3: Gate Automatic Planning on Reviewed Metadata

> **Partly shipped in #13.** 117 exercises carry reviewed programming, and
> `tools/workout-guide/test_programming_overrides.py` already enforces several checks this
> task asks for: alternatives resolve, no self-alternatives, equipment names must exist in
> `StandardEquipment`, and the bundled catalog must match the overrides by content.
> Still to do here: the `review` provenance block, `ExerciseEligibilityPolicy`, and the
> decision on whether automatic planning is restricted to reviewed records — today it is
> not, it only prefers them.

**Files:**
- Create: `tools/workout-guide/review-schema.json`
- Modify: `tools/workout-guide/programming-overrides.json`
- Modify: `tools/workout-guide/import_catalog.py`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Exercise.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ExerciseEligibilityPolicy.kt`
- Test: `tools/workout-guide/test_import_catalog.py`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ExerciseEligibilityPolicyTest.kt`

- [ ] **Step 1: Write importer tests for review state**

Require every automatic-planning record to contain:

```json
{
  "review": {
    "status": "approved",
    "reviewedBy": "domain-review",
    "reviewedAt": "2026-08-28",
    "reviewVersion": 1
  },
  "requiredEquipmentCombinations": [["Dumbbell", "Bench"]],
  "movementPattern": "horizontal_push",
  "difficulty": "intermediate",
  "mechanics": "compound",
  "fatigueScore": 3,
  "progressionType": "repetitions_then_load",
  "alternativeExerciseIds": ["barbell-bench-press"]
}
```

Tests must reject missing review metadata, unknown alternatives, self-alternatives, unapproved automatic eligibility, and equipment combinations containing unknown equipment.

- [ ] **Step 2: Run importer tests and verify RED**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
```

Expected: FAIL on the new review assertions.

- [ ] **Step 3: Add explicit review metadata**

```kotlin
data class ExerciseReviewMetadata(
    val status: ExerciseReviewStatus,
    val reviewedBy: String,
    val reviewedAt: String,
    val reviewVersion: Int
)

enum class ExerciseReviewStatus { APPROVED, DRAFT, REJECTED }

// Add to ExerciseProgrammingMetadata.
val review: ExerciseReviewMetadata
val blockedConstraints: Set<TrainingConstraint> = emptySet()
```

- [ ] **Step 4: Implement one eligibility predicate**

```kotlin
class ExerciseEligibilityPolicy {
    fun isAutomaticallyEligible(
        exercise: Exercise,
        profile: UserProfile
    ): Boolean {
        val programming = exercise.programming ?: return false
        if (programming.review.status != ExerciseReviewStatus.APPROVED) return false
        if (programming.difficulty.minimumExperience() > profile.experienceLevel.ordinal) return false
        return programming.blockedConstraints.none(profile.trainingConstraints::contains)
    }
}

private fun Difficulty.minimumExperience(): Int = when (this) {
    Difficulty.BEGINNER -> ExperienceLevel.BEGINNER.ordinal
    Difficulty.INTERMEDIATE -> ExperienceLevel.INTERMEDIATE.ordinal
    Difficulty.ADVANCED -> ExperienceLevel.ADVANCED.ordinal
}
```

- [ ] **Step 5: Preserve browse/custom behavior**

`ExercisesScreen` and manual templates keep all 302 exercises. Only automatic candidate construction applies `ExerciseEligibilityPolicy`.

- [ ] **Step 6: Establish the review cohort**

Mark the existing 12 records approved only after their equipment, difficulty, fatigue, alternatives, progression, and coaching summaries are rechecked. Expand toward 60-80 through review-only commits; CI must print approved coverage by movement pattern and equipment profile. Never generate approval metadata with an LLM.

- [ ] **Step 7: Run tests and commit**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
./gradlew testDebugUnitTest --tests '*ExerciseEligibilityPolicyTest' --tests '*BundledCatalogVocabularyTest'
git add tools app/src/main app/src/test
git commit -m "feat: gate automatic plans on reviewed exercises"
```

---

### Task 4: Replace Catalog-Order Selection with Deterministic Policy Ranking

> **Partly shipped in #13.** Compound slots rank by primary-muscle match, then fatigue,
> and spread across movement patterns; accessory slots prefer isolation work that trains
> the split. What remains is the policy this task describes: experience, frequency and
> recovery inputs, and weighting primary matches above secondary ones.

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/TrainingPolicy.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ExerciseRanker.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutGenerationContext.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ExerciseRankerTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt`

- [ ] **Step 1: Write failing persona tests**

Cover beginner/bodyweight, intermediate/dumbbells, advanced/full-gym, recently trained push muscles, return after eight weeks, and frequencies from two to six days.

```kotlin
@Test
fun beginner_neverReceivesAdvancedExercise() = runTest {
    val workout = planner.generateWorkout(context(experience = BEGINNER))
    assertThat(workout.exercises).allSatisfy { planned ->
        assertThat(catalog[planned.exerciseId]!!.programming!!.difficulty)
            .isEqualTo(Difficulty.BEGINNER)
    }
}
```

- [ ] **Step 2: Define deterministic budgets**

```kotlin
data class TrainingPolicy(
    val targetWorkingSets: IntRange,
    val maxSessionFatigue: Int,
    val recoveryLookbackHours: Int,
    val maxCompoundExercises: Int,
    val preferredSplit: SplitStrategy
)

enum class SplitStrategy { FULL_BODY, UPPER_LOWER, PUSH_PULL_LEGS }

data class SessionBudget(
    val remainingWorkingSets: Int,
    val remainingFatigue: Int,
    val remainingMinutes: Int
)

fun policyFor(profile: UserProfile): TrainingPolicy = when (profile.experienceLevel) {
    BEGINNER -> TrainingPolicy(6..10, 16, 72, 2, FULL_BODY)
    INTERMEDIATE -> TrainingPolicy(10..16, 24, 48, 3, frequencySplit(profile.daysPerWeek))
    ADVANCED -> TrainingPolicy(12..20, 30, 36, 4, frequencySplit(profile.daysPerWeek))
}
```

- [ ] **Step 3: Rank with stable, explainable factors**

Rank by split match, muscle priority, movement-pattern coverage, prior performance, recovery penalty, approved substitution graph, compound/isolation balance, fatigue budget, and stable ID tie-break. Never use source catalog order.

- [ ] **Step 4: Make recovery a penalty and hard block only when policy requires**

Do not strand users with limited equipment. A recently trained muscle lowers rank; acute user constraints remain hard exclusions.

- [ ] **Step 5: Run planner tests and commit**

```bash
./gradlew testDebugUnitTest --tests '*ExerciseRankerTest' --tests '*FakeWorkoutPlannerTest'
git add app/src/main app/src/test
git commit -m "feat: add experience-aware workout ranking"
```

---

### Task 5: Add Whole-Program Validation

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ProgramValidator.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/GeneratedWorkoutValidator.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ProgramValidatorTest.kt`

- [ ] **Step 1: Write failing aggregate-invariant tests**

Test duplicate IDs, excessive working sets, excessive fatigue, advanced movements for beginners, missing movement coverage, implausible estimated duration, and recently trained muscle overload.

- [ ] **Step 2: Implement structured violations**

```kotlin
sealed interface ProgramViolation {
    data class DuplicateExercise(val exerciseId: String) : ProgramViolation
    data class SessionVolumeExceeded(val actual: Int, val maximum: Int) : ProgramViolation
    data class FatigueBudgetExceeded(val actual: Int, val maximum: Int) : ProgramViolation
    data class DifficultyExceeded(val exerciseId: String) : ProgramViolation
    data class DurationMismatch(val estimated: Int, val requested: Int) : ProgramViolation
}
```

`ProgramValidator.validate()` returns violations; the planner may repair deterministic ranking once. Persistence requires zero violations.

- [ ] **Step 3: Chain structural and program validation in Today**

`GeneratedWorkoutValidator` continues catalog/ID/type checks. `ProgramValidator` runs afterward with the exact context used to generate.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*GeneratedWorkoutValidatorTest' --tests '*ProgramValidatorTest' --tests '*TodayViewModelTest'
git add app/src/main app/src/test
git commit -m "feat: validate complete workout programs"
```

---

### Task 6: Make Active Logging Complete and Gym-Friendly

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/ExercisePrescription.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Workout.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/dao/Daos.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutRepository.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/RestTimerState.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ui/components/SetRow.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutViewModelTest.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration5To6Test.kt`

- [ ] **Step 1: Write failing RPE/RIR/timestamp persistence tests**

```kotlin
val input = SetPerformanceInput(
    reps = 10,
    weight = 40.0,
    rpe = 8f,
    rir = 2,
    completedAtTimestamp = 1_777_777L,
    isCompleted = true
)
repository.logSetCompletion(setId, input)
assertThat(repository.getSet(setId).rpe).isEqualTo(8f)
```

- [ ] **Step 2: Add fields and migration 5 -> 6**

Add `rpe`, `rir`, and `completedAtTimestamp` to `SetPerformanceInput`; add `completedAtTimestamp` to `WorkoutSetEntity`. Validate RPE `0f..10f`, RIR `0..10`, and require a timestamp only for completed sets.

- [ ] **Step 3: Update DAO atomically**

Extend `updateSetCompletion()` to persist performance fields and timestamp under the existing active-session guard.

- [ ] **Step 4: Add fast controls**

Replace keyboard-first completion with large plus/minus controls, previous-value copy, optional RPE/RIR sheet, and one-tap completion. Keep text entry accessible as a secondary action.

- [ ] **Step 5: Add a local rest timer state machine**

```kotlin
sealed interface RestTimerState {
    data object Idle : RestTimerState
    data class Running(val setId: String, val deadlineElapsedRealtime: Long) : RestTimerState
    data class Expired(val setId: String) : RestTimerState
}
```

Inject an elapsed-realtime clock for tests. Completing a set starts the prescription's rest duration. Skip, add 30 seconds, and cancel are explicit events.

- [ ] **Step 6: Add finish and cancel safeguards**

Add the decision type and make `finishWorkout()` return `ConfirmIncomplete` when sets remain:

```kotlin
sealed interface FinishDecision {
    data object Complete : FinishDecision
    data class ConfirmIncomplete(val openSetCount: Int) : FinishDecision
}
```

Wire `cancelWorkout()` to an explicit discard confirmation. Empty/incomplete sessions never silently count toward progression.

- [ ] **Step 7: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*WorkoutRepositoryTest' --tests '*ActiveWorkoutViewModelTest'
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: complete active workout logging"
```

---

### Task 7: Add Validated Substitutions and Editable Targets

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/SubstitutionEngine.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/templates/TemplateEditorViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/templates/TemplateEditorScreen.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/SubstitutionEngineTest.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/WorkoutSubstitutionTest.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration6To7Test.kt`

- [ ] **Step 1: Test substitution invariants**

Alternatives must be approved, equipment-compatible, constraint-compatible, type-compatible, and inside the remaining fatigue/time budget.

- [ ] **Step 2: Implement deterministic ranking**

```kotlin
data class SubstitutionCandidate(
    val exercise: Exercise,
    val reasons: List<SubstitutionReason>
)

class SubstitutionEngine {
    fun candidates(
        original: Exercise,
        context: WorkoutGenerationContext,
        remainingBudget: SessionBudget
    ): List<SubstitutionCandidate>
}
```

- [ ] **Step 3: Replace only unperformed work**

Add nullable `substitutedFromExerciseId` to `WorkoutExerciseEntity` through migration 6 -> 7. The repository transaction preserves completed sets under the performed exercise ID and replaces only open sets. Record the source ID in the session snapshot.

- [ ] **Step 4: Add complete prescription editing to templates**

Support sets, rep range, target load/assistance, duration, distance, rest, and notes through a draft type. Convert to `ExercisePrescription` only after validation.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*SubstitutionEngineTest' --tests '*TemplateEditor*'
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add safe exercise substitutions"
```

---

### Task 8: Add Progression, Deloads, and Program Blocks

> **Partly shipped.** Double progression and Epley e1RM already exist. This task is now
> about deloads, program blocks and the state that drives them.

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/TrainingProgram.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ProgressionEngine.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/DeloadPolicy.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilder.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/dao/Daos.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration7To8Test.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ProgressionEngineTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/DeloadPolicyTest.kt`

- [ ] **Step 1: Write progression fixtures**

Cover top-of-range completion, missed reps, high RPE, reduced RIR, return after break, assistance reduction, duration/distance progression, and mixed units.

- [ ] **Step 2: Define versioned decisions**

```kotlin
data class ProgressionDecision(
    val prescription: ExercisePrescription,
    val action: ProgressionAction,
    val reasons: List<String>,
    val policyVersion: Int
)

enum class ProgressionAction {
    HOLD, ADD_REPS, ADD_LOAD, REDUCE_ASSISTANCE, ADD_DURATION, ADD_DISTANCE, DELOAD
}
```

- [ ] **Step 3: Implement conservative double progression**

Increase load only when all completed work reaches the top of range and effort is acceptable. Use unit-aware increments. Missing RPE/RIR must not be interpreted as low effort.

- [ ] **Step 4: Add a minimal program state**

```kotlin
data class TrainingProgramState(
    val blockIndex: Int,
    val weekIndex: Int,
    val policyVersion: Int,
    val deloadState: DeloadState
)

enum class DeloadState { NONE, RECOMMENDED, ACTIVE }
```

Persist program state separately from immutable completed sessions. Trigger deload from repeated underperformance, explicit readiness, or return after a long break; do not infer medical recovery.

Add `TrainingProgramStateEntity` and `TrainingProgramStateDao`; migration 7 -> 8 creates the new table without changing completed history.

- [ ] **Step 5: Verify with coach-reviewed expected outputs and commit**

```bash
./gradlew testDebugUnitTest --tests '*ProgressionEngineTest' --tests '*DeloadPolicyTest'
git add app/src/main app/src/test
git commit -m "feat: add deterministic training progression"
```

---

### Task 9: Add Reviewed Exercise Guidance

**Files:**
- Create: `tools/workout-guide/coaching-overrides.json`
- Modify: `tools/workout-guide/import_catalog.py`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Exercise.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/exercises/ExercisesScreen.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutScreen.kt`
- Test: `tools/workout-guide/test_import_catalog.py`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/exercise/workoutguide/WorkoutGuideCatalogParserTest.kt`

- [ ] **Step 1: Define reviewable coaching content**

```kotlin
data class ExerciseCoaching(
    val setupCues: List<String>,
    val executionCues: List<String>,
    val commonFaults: List<String>,
    val breathingCue: String?,
    val sourceAttribution: ExerciseAttributionSource?,
    val review: ExerciseReviewMetadata
)
```

- [ ] **Step 2: Import Everkinetic text only as draft evidence**

Create a deterministic matching report using source IDs, attribution URLs, and normalized slugs. Never copy draft text into the shipped `coaching` field until a qualified reviewer approves it. Preserve source URL, license, and changes.

- [ ] **Step 3: Add importer bounds and safety checks**

Limit cue length/count, reject HTML/control characters, require approval metadata, and preserve byte-identical output for identical reviewed inputs.

- [ ] **Step 4: Render concise cues**

Exercise detail shows setup, execution, faults, and provenance. Active workout shows at most two concise cues with a route to the full detail; it does not claim injury prevention.

- [ ] **Step 5: Verify and commit**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
./gradlew testDebugUnitTest
git add tools app/src/main app/src/androidTest
git commit -m "feat: add reviewed exercise coaching"
```

---

### Task 10: Deepen History and Template Workflows

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/history/WorkoutHistoryDetailScreen.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/history/WorkoutHistoryDetailViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/progress/ProgressScreen.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/app/WallCrawlApp.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/feature/history/WorkoutHistoryDetailViewModelTest.kt`

- [ ] **Step 1: Test detail state from immutable session snapshots**

Assert that targets, performed values, unit, RPE/RIR, substitutions, notes, and timestamps come from the completed session rather than the current catalog/profile.

- [ ] **Step 2: Add clickable history navigation**

`WorkoutHistoryCard` navigates to `history/{sessionId}`. Missing/deleted catalog records render the stored exercise ID and measurements without substitution.

- [ ] **Step 3: Add comparison and explainability**

Show prior vs performed, progression decision, plan rationale, and explicit user overrides. Do not manufacture growth percentages from one observation.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*WorkoutHistoryDetailViewModelTest' --tests '*ProgressViewModelTest'
git add app/src/main app/src/test
git commit -m "feat: add workout history details"
```

---

### Task 11: Define Local Privacy, Backup, and Deletion

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/backup/LocalBackupService.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/DataControlsScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/app/WallCrawlApp.kt`
- Create: `docs/privacy.md`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/backup/LocalBackupServiceTest.kt`

- [ ] **Step 1: Write round-trip and deletion tests**

Export profile, templates, program state, and history to a user-selected document; import into an empty database; assert semantic equality and duplicate-safe IDs. Test full local deletion.

- [ ] **Step 2: Disable implicit cloud backup**

Set `android:allowBackup="false"` and `android:fullBackupContent="false"` so workout history is not uploaded by Android backup behind WallCrawl's UI. State that device migration requires the explicit local export/import flow.

- [ ] **Step 3: Implement user-controlled local export/import**

Use the Storage Access Framework. The app never uploads the archive. Include schema version, created time, app version, checksums, and source record versions. Reject unknown future schema versions without partial import.

- [ ] **Step 4: Add data controls**

Expose export, import, delete all local data, model removal, and diagnostic opt-in. Raw prompts, notes, and histories never enter diagnostic events.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*LocalBackupServiceTest'
git add app/src/main app/src/test docs
git commit -m "feat: add local data controls"
```

---

### Task 12: Build the Planner Evaluation Corpus

**Files:**
- Create: `app/src/test/resources/planner-fixtures/*.json`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixtureTest.kt`
- Create: `docs/planner-evaluation.md`

- [ ] **Step 1: Add named fixture personas**

Create fixtures for beginner bodyweight, beginner dumbbells, intermediate full gym, advanced strength, return after break, recently trained muscles, constraint-sensitive joints, mixed units, failed sets, missing RPE, no eligible candidates, and adversarial model output.

- [ ] **Step 2: Define invariant assertions**

Each fixture asserts allowed IDs, approved review state, equipment compatibility, difficulty ceiling, set/fatigue/time budgets, deterministic fallback, and expected progression action.

- [ ] **Step 3: Add deterministic replay metadata**

Record catalog schema/hash, policy version, fixture version, and random seed. Do not snapshot prose rationales; assert structured reasons.

- [ ] **Step 4: Run and commit**

```bash
./gradlew testDebugUnitTest --tests '*PlannerFixtureTest'
git add app/src/test docs
git commit -m "test: add workout planner evaluation corpus"
```

---

### Task 13: Add Optional Local-Model Ranking

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/LocalModelRuntime.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/LocalWorkoutRanker.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/TieredWorkoutPlanner.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/WallCrawlApplication.kt`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/TieredWorkoutPlannerTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/local/LocalWorkoutRankerTest.kt`

- [ ] **Step 1: Define a rank-only contract**

```kotlin
data class CandidateSlot(
    val slotId: String,
    val allowedExerciseIds: List<String>
)

data class RankedCandidateResponse(
    val slotSelections: Map<String, String>,
    val explanationKeys: List<String>
)

data class StructuredPreferences(
    val durationMinutes: Int,
    val avoidedExerciseIds: Set<String>,
    val emphasizedMuscles: Set<String>
)

interface WorkoutCandidateRanker {
    suspend fun rank(
        slots: List<CandidateSlot>,
        preferences: StructuredPreferences
    ): RankedCandidateResponse
}
```

- [ ] **Step 2: Test fallback behavior first**

Cover unavailable runtime, unsupported device, timeout, cancellation, invalid schema, unknown ID, duplicate selection, thermal/battery abort, and quota/busy responses. Every case must produce the deterministic plan without corrupting state.

- [ ] **Step 3: Add runtime capability states**

```kotlin
sealed interface LocalModelCapability {
    data object Unavailable : LocalModelCapability
    data class Available(val provider: String, val modelVersion: String) : LocalModelCapability
    data class DownloadRequired(val bytes: Long) : LocalModelCapability
}
```

Model download is explicit, removable, integrity-checked, and never bundled into the base APK without a measured reason.

- [ ] **Step 4: Serialize only bounded context**

Send slot keys, approved candidate IDs, structured profile preferences, compact recent summaries, and policy version. Exclude names, freeform notes, full history, and raw database rows.

- [ ] **Step 5: Validate model output before compilation**

The local ranker never creates `ExercisePrescription`. Resolve selections against the exact candidate set, compile dosage deterministically, run both validators, and persist provider/model/prompt-policy versions with the recommendation.

- [ ] **Step 6: Add an experiment gate**

Keep local ranking behind an opt-in setting until the fixture corpus and physical-device benchmark demonstrate better acceptance/edit/substitution outcomes than `ExerciseRanker`. Remove the feature if it adds latency without measurable product value.

- [ ] **Step 7: Verify and commit**

```bash
./gradlew testDebugUnitTest --tests '*TieredWorkoutPlannerTest' --tests '*LocalWorkoutRankerTest' --tests '*PlannerFixtureTest'
git add app/src/main app/src/test app/build.gradle.kts
git commit -m "feat: add constrained local workout ranking"
```

---

### Task 14: Integrate, Document, and Release

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/custom-workouts.md`
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Update architecture documentation**

Document onboarding, reviewed eligibility, deterministic ranking, progression, program validation, logging, substitutions, data controls, model capability tiers, and the rule that the LLM never owns dosage.

- [ ] **Step 2: Add generated-data checks to CI**

Run importer tests and `import_catalog.py --check` before Gradle tests. Keep unit tests, lint, and debug assembly.

- [ ] **Step 3: Run complete verification**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 tools/workout-guide/import_catalog.py \
  --source /Users/elopenmike/build/Apps/Workouts/guide/workout-guide \
  --check
./gradlew test lint assembleDebug --stacktrace --no-daemon
```

Expected: all Python tests pass, importer reports no drift, Gradle exits 0.

- [ ] **Step 4: Inspect final diff for scope and secrets**

```bash
git --no-pager diff --check
git --no-pager diff --stat main...HEAD
git --no-pager status --short
```

- [ ] **Step 5: Commit documentation and CI**

```bash
git add README.md docs .github/workflows/ci.yml
git commit -m "docs: describe the adaptive coach architecture"
```

## Release Gates

- No plan can contain an unapproved automatic-planning exercise.
- Beginner fixtures cannot receive advanced movements or unconfirmed loads.
- Recently trained muscles, frequency, and constraints materially affect ranking.
- All completed sets retain type, performed value, unit, effort, and completion time.
- Incomplete workouts require explicit user intent.
- Progression and deload outputs are deterministic and versioned.
- The app remains fully useful with the local model removed or unavailable.
- No user workout content leaves the device by default.
