# Experience Difficulty Ranking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make automatic workout ordering softly prefer exercises at or below the profile's experience tier without changing exercise legality or manual catalog behavior.

**Architecture:** Add a pure `ExerciseDifficultyRankingPolicy` that converts the active mode's trusted classification into a non-negative `aboveExperiencePenalty`. Inject it into `FakeWorkoutPlanner` and apply the penalty in both compound and accessory comparators after stronger role/mechanics criteria and before fatigue/ID tie-breakers. Keep `ExerciseFilter` and `ExerciseEligibilityPolicy` unchanged.

**Tech Stack:** Kotlin, Coroutines, JUnit 4, Truth, Android Gradle Plugin, Python standard library tests

---

## File map

- Create `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ExerciseDifficultyRankingPolicy.kt`: pure trust-mode and penalty mapping.
- Create `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ExerciseDifficultyRankingPolicyTest.kt`: tier matrix, missing metadata, and legacy/reviewed trust tests.
- Modify `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`: use one policy in compound and accessory ordering.
- Modify `app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt`: end-to-end ordering, sole-candidate, accessory, and candidate-membership regressions.
- Modify `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixtureTest.kt`: beginner and advanced persona expectations without load or legality regressions.
- Modify `README.md`: replace stale “difficulty is unread” wording with shipped legacy behavior and the reviewed-metadata approval boundary.

### Task 1: Add failing pure policy tests

**Files:**
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ExerciseDifficultyRankingPolicyTest.kt`

- [ ] **Step 1: Write the failing tier and trust-mode tests**

Use `InMemoryExerciseCatalog.SAMPLE_EXERCISES.first()` as the base exercise and
copy its legacy/reviewed metadata. Cover this exact matrix:

```kotlin
@Test
fun beginner_penaltyIncreasesForIntermediateAndAdvancedLegacyDifficulty() {
    assertThat(penalty(Difficulty.BEGINNER, ExperienceLevel.BEGINNER)).isEqualTo(0)
    assertThat(penalty(Difficulty.INTERMEDIATE, ExperienceLevel.BEGINNER)).isEqualTo(1)
    assertThat(penalty(Difficulty.ADVANCED, ExperienceLevel.BEGINNER)).isEqualTo(2)
}

@Test
fun intermediate_penalizesOnlyAdvancedLegacyDifficulty() {
    assertThat(penalty(Difficulty.BEGINNER, ExperienceLevel.INTERMEDIATE)).isEqualTo(0)
    assertThat(penalty(Difficulty.INTERMEDIATE, ExperienceLevel.INTERMEDIATE)).isEqualTo(0)
    assertThat(penalty(Difficulty.ADVANCED, ExperienceLevel.INTERMEDIATE)).isEqualTo(1)
}

@Test
fun advanced_neverAddsAnExperiencePenalty() {
    Difficulty.entries.forEach { difficulty ->
        assertThat(penalty(difficulty, ExperienceLevel.ADVANCED)).isEqualTo(0)
    }
}

@Test
fun reviewedMode_usesApprovedComplexityInsteadOfConflictingLegacyDifficulty() {
    val exercise = exercise(
        legacyDifficulty = Difficulty.BEGINNER,
        reviewedState = ReviewState.APPROVED,
        reviewedComplexity = ComplexityTier.ADVANCED
    )
    assertThat(policy.aboveExperiencePenalty(exercise, ExperienceLevel.BEGINNER, true))
        .isEqualTo(2)
}

@Test
fun legacyMode_ignoresDraftReviewedComplexity() {
    val exercise = exercise(
        legacyDifficulty = Difficulty.INTERMEDIATE,
        reviewedState = ReviewState.DRAFT,
        reviewedComplexity = ComplexityTier.ADVANCED
    )
    assertThat(policy.aboveExperiencePenalty(exercise, ExperienceLevel.BEGINNER, false))
        .isEqualTo(1)
}

@Test
fun missingTrustedClassification_hasNoPenaltyRatherThanBeginnerClassification() {
    val unclassified = base.copy(programming = null, reviewedMetadata = null)
    assertThat(policy.aboveExperiencePenalty(unclassified, ExperienceLevel.BEGINNER, false))
        .isEqualTo(0)
    assertThat(policy.aboveExperiencePenalty(unclassified, ExperienceLevel.BEGINNER, true))
        .isEqualTo(0)
}
```

- [ ] **Step 2: Run the focused test and record RED**

Run:

```bash
./gradlew testDebugUnitTest --tests '*ExerciseDifficultyRankingPolicyTest' --rerun-tasks --no-daemon
```

Expected: compilation failure because `ExerciseDifficultyRankingPolicy` does not exist.

- [ ] **Step 3: Commit only after the implementation task turns the suite green**

The failing tests stay uncommitted until Task 2 so no branch commit intentionally
breaks the build.

### Task 2: Implement the pure ranking policy

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/ExerciseDifficultyRankingPolicy.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ExerciseDifficultyRankingPolicyTest.kt`

- [ ] **Step 1: Add the minimal policy**

```kotlin
class ExerciseDifficultyRankingPolicy {
    fun aboveExperiencePenalty(
        exercise: Exercise,
        experienceLevel: ExperienceLevel,
        reviewedEligibilityEnabled: Boolean
    ): Int {
        val exerciseTier = if (reviewedEligibilityEnabled) {
            exercise.reviewedMetadata
                ?.takeIf { it.reviewState == ReviewState.APPROVED }
                ?.complexity
                ?.experienceTier
        } else {
            exercise.programming?.difficulty?.experienceTier
        } ?: return 0

        return (exerciseTier - experienceLevel.ordinal).coerceAtLeast(0)
    }

    private val Difficulty.experienceTier: Int
        get() = ordinal

    private val ComplexityTier.experienceTier: Int
        get() = ordinal
}
```

- [ ] **Step 2: Run the focused test and verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --tests '*ExerciseDifficultyRankingPolicyTest' --rerun-tasks --no-daemon
```

Expected: all policy tests pass.

- [ ] **Step 3: Commit the policy and tests**

```bash
git add app/src/main/java/wallcrawl/elopenmike/com/core/ai/ExerciseDifficultyRankingPolicy.kt \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/ExerciseDifficultyRankingPolicyTest.kt
git commit -m "feat: add experience difficulty policy"
```

Include the required Copilot co-author trailer.

### Task 3: Add failing planner integration tests

**Files:**
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt`

- [ ] **Step 1: Add otherwise-equal compound ordering tests**

Build three same-pattern, same-fatigue compound copies with legacy difficulties
`BEGINNER`, `INTERMEDIATE`, and `ADVANCED`. Give a beginner profile a three-slot
workout and assert the exact order. Repeat with an intermediate profile and IDs
chosen so beginner/intermediate retain ID order while advanced comes last.

```kotlin
assertThat(beginnerWorkout.exercises.map { it.exerciseId })
    .containsExactly("beginner-press", "intermediate-press", "advanced-press")
    .inOrder()
assertThat(intermediateWorkout.exercises.map { it.exerciseId })
    .containsExactly("a-intermediate-press", "b-beginner-press", "c-advanced-press")
    .inOrder()
```

- [ ] **Step 2: Add advanced-order and sole-candidate tests**

For an advanced profile, use equal-role compounds with different fatigue values
and assert descending fatigue then ID, matching the pre-change comparator. Give a
beginner profile only one advanced compound and assert it is selected.

- [ ] **Step 3: Add accessory and reviewed trust-mode tests**

Provide one compound plus otherwise-equal isolation accessories at beginner,
intermediate, and advanced difficulty and assert the beginner profile's accessory
order. Then pass an `AutomaticEligibilityResult.Candidates` context containing
approved reviewed metadata that conflicts with legacy difficulty and assert
approved complexity controls order. In a legacy context, attach conflicting
`DRAFT` metadata and assert legacy difficulty controls order.

- [ ] **Step 4: Add membership invariance**

Generate from a mixed-difficulty legal pool and assert every supplied candidate
still appears when the duration permits every slot. Keep existing
`ExerciseEligibilityPolicyTest` assertions untouched so legality remains owned by
that policy.

- [ ] **Step 5: Run the focused planner tests and record RED**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*ExerciseDifficultyRankingPolicyTest' \
  --tests '*FakeWorkoutPlannerTest' \
  --tests '*ExerciseEligibilityPolicyTest' \
  --rerun-tasks --no-daemon
```

Expected: ordering assertions fail because the planner still sorts by fatigue and
ID without the experience penalty.

### Task 4: Integrate the policy into both planner comparators

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt`

- [ ] **Step 1: Inject the pure policy**

```kotlin
class FakeWorkoutPlanner(
    private val prescriptionFactory: DefaultExercisePrescriptionFactory =
        DefaultExercisePrescriptionFactory(),
    private val difficultyRankingPolicy: ExerciseDifficultyRankingPolicy =
        ExerciseDifficultyRankingPolicy()
) : WorkoutPlanner
```

- [ ] **Step 2: Thread explicit mode and experience into ordering**

In `selectExercisesForSplit`, derive:

```kotlin
val reviewedEligibilityEnabled = context.automaticEligibilityResult != null
```

Pass `context.experienceLevel` and this mode to `chooseCompounds` and
`accessoryOrder`. Insert:

```kotlin
.thenBy {
    difficultyRankingPolicy.aboveExperiencePenalty(
        exercise = it,
        experienceLevel = experienceLevel,
        reviewedEligibilityEnabled = reviewedEligibilityEnabled
    )
}
```

after primary role for compounds, after role/mechanics/programming-presence for
accessories, and before fatigue/ID for both.

- [ ] **Step 3: Run focused planner tests and verify GREEN**

Run the Task 3 command. Expected: all selected suites pass.

- [ ] **Step 4: Commit planner integration**

```bash
git add app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt
git commit -m "feat: rank exercises by profile experience"
```

Include the required Copilot co-author trailer.

### Task 5: Lock persona behavior and reconcile documentation

**Files:**
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixtureTest.kt`
- Modify: `README.md`

- [ ] **Step 1: Add beginner and advanced persona assertions**

For `bodyweight-beginner`, inspect selected classified exercises and assert their
legacy difficulties are nondecreasing after stronger planner criteria are
accounted for; continue asserting every prescribed load is null unless fixture
history or confirmed loads supply it. For `full-gym-advanced`, compare the
generated workout with a copy of the profile at `ADVANCED` and assert existing
fatigue/ID ordering remains stable for otherwise-equal candidates.

- [ ] **Step 2: Run corpus tests**

```bash
./gradlew testDebugUnitTest \
  --tests '*PlannerFixture*' \
  --tests '*FakeWorkoutPlannerTest' \
  --tests '*ExerciseDifficultyRankingPolicyTest' \
  --tests '*ExerciseEligibilityPolicyTest' \
  --tests '*ExerciseFilterTest' \
  --rerun-tasks --no-daemon
```

Expected: all fixture and focused planner tests pass; no fixture expects an
invented starting load or treats difficulty as a hard exclusion.

- [ ] **Step 3: Update README shipped and blocked states**

State that:

- legacy automatic planning now softly demotes exercises above profile experience;
- higher-tier work remains eligible when otherwise legal;
- reviewed-enabled planning uses only `APPROVED` complexity;
- the production reviewed gate remains disabled pending human approval coverage;
- `DRAFT` metadata cannot influence reviewed-mode ranking.

Remove the stale next milestone saying no code reads difficulty.

- [ ] **Step 4: Commit persona and documentation updates**

```bash
git add app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixtureTest.kt README.md
git commit -m "docs: record experience ranking rollout"
```

Include the required Copilot co-author trailer.

### Task 6: Validate, review, and close

**Files:**
- Review: complete diff against `main`

- [ ] **Step 1: Run focused tests**

Run the Task 5 test command and retain the exact task/test summary.

- [ ] **Step 2: Run full Python tooling**

Run both standard-library suites used by CI:

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
```

Expected: both suites report `OK`.

- [ ] **Step 3: Run full Gradle unit, lint, and assemble**

```bash
./gradlew test lint assemble --rerun-tasks --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run connected Android tests**

With a booted configured emulator/device:

```bash
./gradlew connectedDebugAndroidTest --rerun-tasks --no-daemon
```

Expected: `BUILD SUCCESSFUL` and zero failed instrumentation tests.

- [ ] **Step 5: Run diff hygiene and commit validation fixes**

```bash
git diff --check
git status --short
git --no-pager diff main...HEAD
```

The worktree must contain no unintended generated files or uncommitted changes.

- [ ] **Step 6: Run the mandated two-reviewer loop**

At one committed SHA, dispatch exactly two `code-review` agents against the full
`main...HEAD` diff: Claude Opus 4.8 and Grok 4.6. Fix every valid finding, rerun
validation, commit, and dispatch the same two reviewers again on the new same SHA.
Stop only when both report no findings on one SHA.

- [ ] **Step 7: Report to the creating session**

Send the final SHA, commit list, exact focused/Python/Gradle/connected-test
summaries, diff hygiene result, and both same-SHA clean reviewer results to project
session `4cc6a667-f22a-4485-81b1-c23e33514b7b`. Do not push or open a pull request.
