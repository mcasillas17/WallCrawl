# Workout Focus Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A generated workout's advertised split is always trained as a primary purpose by at least one selected exercise, and an unsupportable muscle priority is explained instead of concealed.

**Architecture:** One shared predicate, `WorkoutSplit.trainsAsFocus(exercise)` in `core/ai/WorkoutFocus.kt`, replaces the planner's private primary/secondary matching in split fillability and ordering, backs a new always-on `ProgramValidator` invariant, and is what tests assert against. `FakeWorkoutPlanner` additionally reports the `HIGH`-priority muscles no candidate trains, and the localization layer renders that as one extra sentence.

**Tech Stack:** Kotlin, Android Gradle, JUnit4 + Truth, Compose UI tests, Robolectric-free JVM unit tests.

Design: [`docs/superpowers/specs/2026-09-07-workout-focus-coverage-design.md`](../specs/2026-09-07-workout-focus-coverage-design.md).

---

### Task 1: Failing regression for the band-only reproduction

**Files:**
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutFocusCoverageTest.kt` (create)

- [ ] **Step 1: Write the failing test**

A new JVM test that builds the exact committed band-only profile from
`docs/reviewed-catalog-coverage.md` against the real bundled catalog through
`PlannerFixtureContextFactory`, runs the real `FakeWorkoutPlanner`, and requires that
some selected exercise trains the advertised split as its own purpose. It must not use a
test-local classifier: assert through `WorkoutSplit.trainsAsFocus`.

```kotlin
@Test
fun bandOnlyChestPriority_advertisesASplitItsSelectionActuallyTrains() = runTest {
    val context = bandOnlyContext()
    val workout = FakeWorkoutPlanner().generateWorkout(context)
    val byId = context.allowedExercises.associateBy(Exercise::id)
    val selected = workout.exercises.map { byId.getValue(it.exerciseId) }

    assertWithMessage("advertised ${workout.title.split} with ${selected.map(Exercise::id)}")
        .that(selected.any { workout.title.split.trainsAsFocus(it) })
        .isTrue()
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkoutFocusCoverageTest*'`
Expected: FAIL — unresolved reference `trainsAsFocus` first, then, once Task 2 lands the
predicate, an assertion failure reporting `PUSH` with only band pull/core work.

---

### Task 2: The shared focus predicate

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutFocus.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`

- [ ] **Step 1: Create the predicate**

Move `isStrengthWork`/`isConditioning` out of `FakeWorkoutPlanner` so the planner, the
validator and the tests share one classification, and add the focus rule beside them.

```kotlin
internal fun Exercise.focusMuscles(): List<String> =
    reviewedMetadata
        ?.takeIf { it.reviewState == ReviewState.APPROVED }
        ?.takeIf { it.isWellFormedApprovedMetadata() }
        ?.let { listOf(it.directPrimaryMuscle) }
        ?: primaryMuscles

internal fun WorkoutSplit.trainsAsFocus(exercise: Exercise): Boolean =
    exercise.isStrengthWork() && exercise.focusMuscles().any { it in targetMuscles }
```

- [ ] **Step 2: Use it for fillability and ordering**

In `FakeWorkoutPlanner`, `fillable()` becomes
`filter { split -> candidates.any { split.trainsAsFocus(it) } }`, and both comparators'
`compareByDescending { it.trainsAsPrimary(split) }` key becomes
`compareByDescending { split.trainsAsFocus(it) }`. Delete the now-unused private
`trainsAsPrimary`. Keep `matchingCandidates` on the broad `trains(split)` predicate so a
secondary match can still fill an accessory slot.

- [ ] **Step 3: Guarantee the selection, not just the pool**

At the end of `selectExercisesForSplit`:

```kotlin
check(result.any { split.trainsAsFocus(it) }) {
    "Split ${split.name} was selected without any exercise that trains it as a focus."
}
```

- [ ] **Step 4: Run the regression and the planner suite**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkoutFocusCoverageTest*' --tests '*FakeWorkoutPlannerTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "fix: require a genuine primary match before advertising a split"
```

---

### Task 3: Explain an unavailable priority

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Workout.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ui/localization/GeneratedWorkoutText.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/today/TodayScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-es/strings.xml`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutFocusCoverageTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun bandOnlyChestPriority_reportsChestAsUnavailableRatherThanFakingIt() = runTest {
    val workout = FakeWorkoutPlanner().generateWorkout(bandOnlyContext())
    assertThat(workout.unavailableFocusMuscles).containsExactly(StandardMuscles.CHEST)
}
```

- [ ] **Step 2: Add the field**

`GeneratedWorkout` gains `val unavailableFocusMuscles: List<String> = emptyList()`.

- [ ] **Step 3: Populate it in the planner**

```kotlin
private fun unavailableFocusMuscles(
    context: WorkoutGenerationContext,
    candidates: List<Exercise>
): List<String> = context.musclePriorities
    .filterValues { it == PriorityLevel.HIGH }
    .keys
    .filterNot { muscle -> candidates.any { muscle in it.focusMuscles() && it.isStrengthWork() } }
    .sorted()
```

Sorted by canonical name so the list never depends on map iteration order or locale.

- [ ] **Step 4: Render it**

`unavailableFocusNotice(muscles)` returns `R.string.generated_rationale_focus_unavailable`
filled with the muscles joined by `R.string.list_separator` through
`LocalExerciseVocabulary`, or null when the list is empty.
`generatedWorkoutRationale(spec, unavailableFocusMuscles)` appends it for the text a started
session stores, and `SuggestedWorkoutCard` renders it — and only it — when it is non-null.

English: `Nothing you have available trains %1$s as its main target, so this session focuses elsewhere.`
Spanish: `Nada de lo que tienes disponible entrena %1$s como objetivo principal, así que esta sesión se enfoca en otra cosa.`

> Revised during review. The first attempt rendered the whole explanation on the card
> unconditionally, which exceeded this task's scope and staled the Today screenshots.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*WorkoutFocusCoverageTest*' --tests '*StringResourceParityTest*' --tests '*PlannerLocaleInvarianceTest*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: explain an unavailable muscle priority instead of hiding it"
```

---

### Task 4: Validation boundary

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ProgramViolation.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ProgramValidator.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ProgramValidatorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun rejectsAWorkoutWhoseAdvertisedSplitNoSelectedExerciseTrains() = runTest {
    val row = exercise(id = "row", primary = listOf(StandardMuscles.UPPER_BACK),
        secondary = listOf(StandardMuscles.SHOULDERS))
    val workout = workout(split = WorkoutSplit.PUSH, exercises = listOf(row))

    val result = validator.validate(workout, context(listOf(row)))

    assertThat((result as ProgramValidationResult.Invalid).violations.map { it.code })
        .contains(ProgramViolationCode.UNSUPPORTED_WORKOUT_FOCUS)
}
```

- [ ] **Step 2: Add the code and the rule**

New `ProgramViolationCode.UNSUPPORTED_WORKOUT_FOCUS` declared after
`PRESCRIPTION_TYPE_MISMATCH`, documented as a software invariant. `ProgramValidator.evaluate`
adds, after the structural checks and before declared constraints:

```kotlin
private fun focusViolations(
    workout: GeneratedWorkout,
    allowedById: Map<String, Exercise>
): List<ProgramViolation> {
    val split = workout.title.split
    val supported = workout.exercises.any { planned ->
        allowedById[planned.exerciseId]?.let { split.trainsAsFocus(it) } == true
    }
    return if (supported) {
        emptyList()
    } else {
        listOf(ProgramViolation(code = ProgramViolationCode.UNSUPPORTED_WORKOUT_FOCUS,
            detail = split.name))
    }
}
```

Repair needs no change: it runs only when every violation is `WEEKLY_ALLOWANCE_EXCEEDED`,
so a focus violation blocks it, and it only reduces sets, so it can never remove coverage.

- [ ] **Step 3: Run the validator suites**

Run: `./gradlew :app:testDebugUnitTest --tests '*ProgramValidator*' --tests '*TodayViewModelTest*'`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: reject a recommendation whose split its exercises do not train"
```

---

### Task 5: Extend coverage to the other splits and the edge pools

**Files:**
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutFocusCoverageTest.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ReviewedCatalogCoverageTest.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerLocaleInvarianceTest.kt`
- Test (UI): `app/src/androidTest/java/wallcrawl/elopenmike/com/feature/today/TodayFocusNoticeTest.kt` (create)

- [ ] **Step 1: Add the cases**

- A secondary-only pool for every `WorkoutSplit` value fails with
  `WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT` rather than advertising that split.
- Genuine push pools — bodyweight, dumbbell, machine and full gym subsets of the bundled
  catalog — still produce a `PUSH` session containing a selected chest/shoulder/triceps
  primary.
- Regenerating and advancing `completedWorkoutCount` over a full rotation never advertises
  an unsupported split, and the same state replays identically.
- `PlannerLocaleInvarianceTest` asserts `unavailableFocusMuscles` is locale-invariant.
- `ReviewedCatalogCoverageTest` uses `trainsAsFocus` in place of its local
  pattern + `directPrimaryMuscle` classifier, and its `band-only-push-gap` case now
  asserts a truthful non-`PUSH` split plus `Chest` in `unavailableFocusMuscles`. Synthetic
  approvals stay clearly labelled test-only.
- A Compose test renders the real `TodayContent` success state with a non-empty
  `unavailableFocusMuscles` and asserts the explanatory sentence appears in English and in
  Spanish, and that a supported session adds no line at all.

- [ ] **Step 2: Run the JVM suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test app/src/androidTest
git commit -m "test: cover focus contract across splits, pools, rotation and locales"
```

---

### Task 6: Documentation

**Files:**
- Modify: `docs/reviewed-catalog-coverage.md`
- Modify: `docs/architecture.md`
- Modify: `docs/planner-evaluation.md`
- Modify: `ROADMAP.md`

- [ ] **Step 1: Record the post-fix result**

In `docs/reviewed-catalog-coverage.md`, keep the existing tables as dated baselines and
add the new observed result for the identical profile and 0/0 generation state. State
explicitly that the fixed-anchor equipment gap is untouched and that no band chest
exercise was created.

- [ ] **Step 2: Document the contract**

Add the focus contract and the fallback behaviour to `docs/architecture.md`'s planner and
whole-program-validation sections, and to `docs/planner-evaluation.md`. Update
`ROADMAP.md`'s Package 3 coverage finding and the verified-status row without claiming the
reviewed rollout is complete.

- [ ] **Step 3: Run lint and the full JVM suite**

Run: `./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add docs ROADMAP.md
git commit -m "docs: record the focus contract and the post-fix band-only result"
```
