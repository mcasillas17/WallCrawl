# Deterministic Planner Evaluation Corpus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a versioned, data-driven JVM evaluation corpus that proves the current deterministic planner's safety and reproducibility invariants across representative user profiles.

**Architecture:** Strict JSON fixtures describe profiles, bounded history, and invariant expectations without duplicating catalog exercises. A test-only loader validates every field before constructing `WorkoutGenerationContext`; parameterized tests run a fresh planner per generation so replay checks are not polluted by its in-memory regeneration counter.

**Tech Stack:** Kotlin/JVM, JUnit 4, Truth, coroutines-test, test-only `org.json`, existing WallCrawl domain/planner classes.

---

### Task 1: Add Strict Fixture Contracts and Loader

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixture.kt`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixtureLoaderTest.kt`
- Create: `app/src/test/resources/planner-fixtures/invalid-unknown-field.json`

- [ ] **Step 1: Write a failing strict-loader test**

```kotlin
@Test
fun load_rejectsUnknownFields() {
    val error = assertThrows<PlannerFixtureFormatException> {
        PlannerFixtureLoader().loadResource("planner-fixtures/invalid-unknown-field.json")
    }
    assertThat(error).hasMessageThat().contains("unexpected")
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
./gradlew testDebugUnitTest --tests '*PlannerFixtureLoaderTest' --rerun-tasks --no-daemon
```

Expected: compilation fails because `PlannerFixtureLoader` and its exception do not exist.

- [ ] **Step 3: Add the test-only JSON dependency**

Add version `20240303` and alias `json` to `gradle/libs.versions.toml`, then add:

```kotlin
testImplementation(libs.json)
```

to `app/build.gradle.kts`. Maven Central metadata confirms this published artifact version.

- [ ] **Step 4: Implement strict fixture types and parsing**

Define focused test-only models:

```kotlin
data class PlannerFixture(
    val schemaVersion: Int,
    val id: String,
    val policyVersion: Int,
    val catalogVersion: String,
    val profile: FixtureProfile,
    val completedWorkoutCount: Int,
    val history: List<FixtureHistory>,
    val expected: FixtureExpectations
)

data class FixtureExpectations(
    val outcome: ExpectedOutcome,
    val requiredExerciseIds: Set<String> = emptySet(),
    val forbiddenExerciseIds: Set<String> = emptySet()
)

enum class ExpectedOutcome {
    SUCCESS,
    NO_CANDIDATES,
    NO_STRENGTH_CANDIDATES,
    NO_CANDIDATES_FOR_ANY_SPLIT
}
```

The loader must reject unknown fields, unsafe IDs, unsupported schema versions, unknown enum values, duplicate IDs, non-finite/out-of-range numbers, oversized strings/collections, and contradictory expected ID sets. It must not log fixture contents.

- [ ] **Step 5: Run the loader tests and verify GREEN**

Run the focused command from Step 2.

Expected: all `PlannerFixtureLoaderTest` tests pass.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixture.kt \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixtureLoaderTest.kt \
  app/src/test/resources/planner-fixtures
git commit -m "test: add strict planner fixture loader"
```

---

### Task 2: Add Representative Persona Fixtures

**Files:**
- Create: `app/src/test/resources/planner-fixtures/bodyweight-beginner.json`
- Create: `app/src/test/resources/planner-fixtures/band-only.json`
- Create: `app/src/test/resources/planner-fixtures/machine-only.json`
- Create: `app/src/test/resources/planner-fixtures/full-gym-advanced.json`
- Create: `app/src/test/resources/planner-fixtures/returning-user.json`
- Create: `app/src/test/resources/planner-fixtures/limited-capability.json`
- Create: `app/src/test/resources/planner-fixtures/mixed-unit-history.json`
- Create: `app/src/test/resources/planner-fixtures/sparse-history.json`
- Create: `app/src/test/resources/planner-fixtures/no-strength-candidates.json`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixtureCorpusTest.kt`

- [ ] **Step 1: Write a failing corpus-completeness test**

```kotlin
@Test
fun corpus_containsEveryRequiredPersona() {
    val ids = PlannerFixtureLoader().loadCorpus().map { it.id }.toSet()
    assertThat(ids).containsAtLeast(
        "bodyweight-beginner",
        "band-only",
        "machine-only",
        "full-gym-advanced",
        "returning-user",
        "limited-capability",
        "mixed-unit-history",
        "sparse-history",
        "no-strength-candidates"
    )
}
```

- [ ] **Step 2: Run and verify RED**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
./gradlew testDebugUnitTest --tests '*PlannerFixtureCorpusTest' --rerun-tasks --no-daemon
```

Expected: failure listing the missing persona fixture IDs.

- [ ] **Step 3: Author the nine fixtures**

Use only canonical enum names and `StandardEquipment`/`StandardMuscles` values. Keep expected assertions invariant-based: success/failure type and required/forbidden IDs only where they express a safety boundary. Do not encode a complete ordered workout snapshot.

- [ ] **Step 4: Add fixture construction**

Build profiles, history, and allowed exercises from existing WallCrawl models and `InMemoryExerciseCatalog`. Apply the real `ExerciseFilter`; never reproduce planner/filter logic in the test loader.

- [ ] **Step 5: Run and verify GREEN**

Run the focused command from Step 2.

Expected: all corpus-completeness and fixture-construction tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/resources/planner-fixtures \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai
git commit -m "test: add deterministic planner personas"
```

---

### Task 3: Assert Planner Invariants and Replay

**Files:**
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixtureTest.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/PlannerFixture.kt`

- [ ] **Step 1: Write the failing replay and safety tests**

For every fixture, assert:

```kotlin
val first = FakeWorkoutPlanner().generateWorkout(context)
val replay = FakeWorkoutPlanner().generateWorkout(context)

assertThat(replay.copy(id = first.id)).isEqualTo(first)
assertThat(first.exercises.map { it.exerciseId })
    .containsNoneIn(fixture.expected.forbiddenExerciseIds)
assertThat(first.exercises.all { it.exerciseId in context.allowedExercises.map(Exercise::id) })
    .isTrue()
assertThat(first.exercises.all { it.prescription.targetWeight != null || it.prescription.exerciseType != ExerciseType.WEIGHT_REPS })
    .isFalse()
```

Use a helper for the no-invented-load assertion so a fixture may contain confirmed/history-backed loads while every unconfirmed loaded exercise remains null.

- [ ] **Step 2: Run and verify RED**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
./gradlew testDebugUnitTest --tests '*PlannerFixtureTest' --rerun-tasks --no-daemon
```

Expected: failure until the complete invariant harness is implemented.

- [ ] **Step 3: Implement invariant evaluation**

Assert stable typed failures, equipment/exclusion legality through the real filter, allowed-ID-only selection, type-valid prescriptions, no fabricated loads, fresh-planner replay equality, and requested required/forbidden IDs. Verify a capability-variant context produces the same workout in this pre-eligibility milestone.

- [ ] **Step 4: Run planner tests and verify GREEN**

Run:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
./gradlew testDebugUnitTest \
  --tests '*PlannerFixture*' \
  --tests '*FakeWorkoutPlannerTest' \
  --tests '*WorkoutGenerationContextBuilderTest' \
  --rerun-tasks --no-daemon
```

Expected: all selected tests pass and the output reports non-zero executed tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/wallcrawl/elopenmike/com/core/ai
git commit -m "test: enforce deterministic planner invariants"
```

---

### Task 4: Document and Validate the Corpus

**Files:**
- Create: `docs/planner-evaluation.md`

- [ ] **Step 1: Document the fixture schema and guarantees**

Explain schema/policy/catalog version fields, persona coverage, fresh-planner replay semantics, invariant assertions, the test command, and explicit limitations: reviewed-only eligibility, weekly dose, progression, and LLM evaluation are not implemented here.

- [ ] **Step 2: Run full verification**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
./gradlew testDebugUnitTest --rerun-tasks --no-daemon
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
./gradlew lintDebug assembleDebug --stacktrace --no-daemon
git diff --check
```

Expected: all unit tests pass, lint/build succeed, and `git diff --check` exits zero.

- [ ] **Step 3: Commit**

```bash
git add docs/planner-evaluation.md
git commit -m "docs: explain planner evaluation corpus"
```

