# WallCrawl Feedback Loop Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make persisted workout performance drive future workout context, active-workout history, and progress analytics, and prove offline Workout Guide SVG rendering behind an abstraction.

**Architecture:** Pure Kotlin analyzers derive history and progress from domain sessions. A context builder owns repository/catalog/filter orchestration, Room groups new sessions in one transaction, and a provider owns all upstream visual paths. Existing feature ViewModels consume these boundaries without broad module restructuring.

**Tech Stack:** Kotlin 2.0.21, coroutines/Flow, Room 2.6.1, Jetpack Compose/Material 3, Coil 3 SVG, JUnit 4, Truth.

**Spec:** `docs/superpowers/specs/2026-08-25-feedback-loop-vertical-slice-design.md`

## Global Constraints

- Keep all core application behavior offline and do not add runtime network permissions.
- The planner may only select IDs in `WorkoutGenerationContext.allowedExercises`.
- Reject invalid generated exercise IDs; never substitute them silently.
- Preserve target and actual set performance separately.
- Pin Workout Guide proof assets to commit `ba0b709cb20430361b2cb33aaadd20998164a916` and retain CC BY-SA 4.0 attribution.
- Do not integrate a production local LLM in this phase.

---

### Task 1: History Analysis and Context Builder

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutHistoryAnalyzer.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilder.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/WallCrawlApplication.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutHistoryAnalyzerTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilderTest.kt`

**Interfaces:**
- Consumes: `UserProfileRepository.getProfileOnce()`, `WorkoutRepository.getRecentCompletedSessions(limit)`, `ExerciseCatalog.getAllExercises()`, `ExerciseFilter.filterCandidates(...)`.
- Produces: `WorkoutHistoryAnalyzer.exerciseHistory(sessions)`, `WorkoutHistoryAnalyzer.recentlyTrainedMuscles(sessions, now)`, and `WorkoutGenerationContextBuilder.build()`.

- [x] **Step 1: Write failing analyzer tests**

```kotlin
@Test fun exerciseHistory_usesLatestCompletedPerformanceAndBestEstimatedOneRepMax() {
    val history = analyzer.exerciseHistory(listOf(newerSession, olderSession))
    assertThat(history.getValue("incline-dumbbell-press").lastWeight).isEqualTo(50.0)
    assertThat(history.getValue("incline-dumbbell-press").recentSets).hasSize(3)
    assertThat(history.getValue("incline-dumbbell-press").bestEstimated1RM).isWithin(0.01).of(66.67)
}

@Test fun recentlyTrainedMuscles_respectsLookbackAndCompletionStatus() {
    assertThat(analyzer.recentlyTrainedMuscles(sessions, now, 72)).containsExactly("Chest")
}
```

- [x] **Step 2: Run analyzer tests and confirm they fail because the analyzer is absent**

Run: `./gradlew testDebugUnitTest --tests '*WorkoutHistoryAnalyzerTest'`

- [x] **Step 3: Implement the pure analyzer and re-run its tests**

```kotlin
class WorkoutHistoryAnalyzer {
    fun exerciseHistory(sessions: List<WorkoutSession>): Map<String, ExercisePerformanceHistory>
    fun recentlyTrainedMuscles(
        sessions: List<WorkoutSession>,
        nowTimestamp: Long,
        lookbackHours: Int = 72
    ): List<String>
}
```

- [x] **Step 4: Write a failing context-builder test**

```kotlin
@Test fun build_filtersCandidatesAndIncludesPersistedHistory() = runTest {
    val context = builder.build()
    assertThat(context.allowedExercises.map(Exercise::id)).doesNotContain("barbell-bench-press")
    assertThat(context.recentWorkoutHistory).containsExactly(completedSession)
    assertThat(context.exerciseHistory).containsKey("incline-dumbbell-press")
}
```

- [x] **Step 5: Add `getRecentCompletedSessions`, implement the builder, wire the container, and re-run both tests**

```kotlin
interface WorkoutRepository {
    suspend fun getRecentCompletedSessions(limit: Int = 8): List<WorkoutSession>
}

class WorkoutGenerationContextBuilder(
    private val userProfileRepository: UserProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val exerciseFilter: ExerciseFilter,
    private val historyAnalyzer: WorkoutHistoryAnalyzer,
    private val nowTimestamp: () -> Long = System::currentTimeMillis
) {
    suspend fun build(): WorkoutGenerationContext
}
```

- [x] **Step 6: Commit the history/context boundary**

```bash
git add app/src/main app/src/test
git commit -m "feat: build workout context from persisted history"
```

### Task 2: Transactional Persistence and Structural Validation

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/dao/Daos.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/GeneratedWorkoutValidator.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutRepositoryTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/GeneratedWorkoutValidatorTest.kt`

**Interfaces:**
- Consumes: existing Room entities and DAOs.
- Produces: `WorkoutSessionDao.insertWorkout(...)` transaction and repository validation guarantees.

- [x] **Step 1: Add failing validator tests for non-finite/negative weight, excessive sets/reps, invalid rest, blank title, and duration**

```kotlin
@Test fun validate_negativeTargetWeight_throwsException() = runTest {
    val invalid = validWorkout.copy(exercises = listOf(validExercise.copy(targetWeight = -1.0)))
    assertFailsWith<WorkoutValidationException> { validator.validate(invalid) }
}
```

- [x] **Step 2: Run the validator test and confirm the new cases fail**

Run: `./gradlew testDebugUnitTest --tests '*GeneratedWorkoutValidatorTest'`

- [x] **Step 3: Implement the minimal numeric/title/duration checks and re-run the validator tests**

```kotlin
requireValidation(workout.name.isNotBlank(), "Generated workout has a blank name.")
requireValidation(workout.estimatedDurationMinutes in 1..240, "Invalid workout duration.")
requireValidation(exercise.targetSets in 1..20, "Invalid target sets.")
requireValidation(exercise.repMin in 1..1000 && exercise.repMax in exercise.repMin..1000, "Invalid rep range.")
requireValidation(exercise.targetWeight == null || (exercise.targetWeight.isFinite() && exercise.targetWeight >= 0), "Invalid target weight.")
requireValidation(exercise.restSeconds in 0..1800, "Invalid rest period.")
```

- [x] **Step 4: Add failing repository tests for invalid set input and unknown completion**

```kotlin
@Test fun logSetCompletion_completedSetWithoutPositiveReps_isRejected() = runTest {
    assertFailsWith<IllegalArgumentException> {
        repository.logSetCompletion("set", reps = 0, weight = 20.0, isCompleted = true)
    }
}
```

- [x] **Step 5: Add the transaction DAO method and repository guards, then re-run repository tests**

```kotlin
@Transaction
suspend fun insertWorkout(
    session: WorkoutSessionEntity,
    exercises: List<WorkoutExerciseEntity>,
    sets: List<WorkoutSetEntity>
) {
    insertSession(session)
    insertExercises(exercises)
    insertSets(sets)
}
```

- [x] **Step 6: Commit persistence hardening**

```bash
git add app/src/main app/src/test
git commit -m "fix: make workout persistence atomic and validated"
```

### Task 3: Real Progress and Previous Performance

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/progress/ProgressCalculator.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/ProgressAnalytics.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/progress/ProgressViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutUiState.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutScreen.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/progress/ProgressCalculatorTest.kt`

**Interfaces:**
- Consumes: completed `WorkoutSession` values, `UserProfile`, and catalog exercises.
- Produces: real `ProgressOverview` and structured previous completed sets in active state.

- [x] **Step 1: Write failing progress tests for empty history, weekly counts/volume/muscle sets, streaks, and trends**

```kotlin
@Test fun calculate_emptyHistory_returnsZeroOverview() {
    val result = calculator.calculate(emptyList(), profile, exercises, now)
    assertThat(result.workoutsThisWeek).isEqualTo(0)
    assertThat(result.totalVolumeThisWeek).isEqualTo(0.0)
    assertThat(result.recentPersonalRecords).isEmpty()
}
```

- [x] **Step 2: Run and confirm failure because `ProgressCalculator` is absent**

Run: `./gradlew testDebugUnitTest --tests '*ProgressCalculatorTest'`

- [x] **Step 3: Implement calculations and re-run focused tests**

```kotlin
class ProgressCalculator {
    fun calculate(
        completedSessions: List<WorkoutSession>,
        profile: UserProfile,
        exercises: List<Exercise>,
        nowTimestamp: Long
    ): ProgressOverview
}
```

- [x] **Step 4: Wire `ProgressViewModel` to completed sessions/profile/catalog and remove repository sample analytics**

Run: `./gradlew testDebugUnitTest`

- [x] **Step 5: Change active state to carry `previousSets: List<WorkoutSet>` and derive the latest prior exercise from completed sessions**

```kotlin
data class Active(
    val session: WorkoutSession,
    val currentExerciseIndex: Int = 0,
    val currentCatalogExercise: Exercise? = null,
    val preferredUnit: WeightUnit = WeightUnit.LBS,
    val isSaving: Boolean = false,
    val previousSets: List<WorkoutSet> = emptyList(),
    val previousSessionTimestamp: Long? = null
)
```

- [x] **Step 6: Render previous actual values from structured sets and commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: derive progress and exercise history from workouts"
```

### Task 4: Today State and History-Aware Fake Planning

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/today/TodayViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/today/TodayUiState.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/app/WallCrawlApp.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/feature/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `WorkoutGenerationContextBuilder.build()` and completed-session Flow.
- Produces: one guarded generation at a time, real weekly count, and history-aware target load.

- [x] **Step 1: Write a failing planner test showing that completed top-range reps increase the prior load**

```kotlin
@Test fun generateWorkout_whenLastPerformanceHitTopRange_increasesTargetWeight() = runTest {
    val workout = planner.generateWorkout(contextWithLastSet(weight = 45.0, reps = 10))
    assertThat(workout.exercises.first { it.exerciseId == "incline-dumbbell-press" }.targetWeight)
        .isEqualTo(50.0)
}
```

- [x] **Step 2: Run the test, implement the minimal unit-aware progression fallback, and re-run it**

- [x] **Step 3: Write a failing Today ViewModel test asserting one initial build and actual weekly count**

```kotlin
@Test fun uiState_generatesOnceAndUsesCompletedSessionsThisWeek() = runTest {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    assertThat(builder.buildCalls).isEqualTo(1)
    assertThat((viewModel.uiState.value as TodayUiState.Success).completedThisWeek).isEqualTo(2)
}
```

- [x] **Step 4: Refactor generation out of the Flow transform, inject the builder, and re-run focused tests**

- [x] **Step 5: Run all unit tests and commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: feed workout history into daily planning"
```

### Task 5: Workout Guide Visual Provider and Bundled SVG Proof

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/exercise/visual/ExerciseVisualProvider.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/exercise/visual/WorkoutGuideVisualProvider.kt`
- Replace: `app/src/main/java/wallcrawl/elopenmike/com/core/ui/components/ExerciseVisual.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Exercise.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/exercise/InMemoryExerciseCatalog.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/WallCrawlApplication.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/app/WallCrawlApp.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/assets/workout-guide/NOTICE.md`
- Create: `app/src/main/assets/workout-guide/LICENSE-ASSETS`
- Create: `app/src/main/assets/workout-guide/ATTRIBUTION.md`
- Create: nine SVG files under `app/src/main/assets/workout-guide/assets/`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/exercise/visual/WorkoutGuideVisualProviderTest.kt`

**Interfaces:**
- Produces: `ExerciseVisualProvider.framesFor(exerciseId): List<ExerciseVisual>` and `ExerciseIllustration(exerciseId, exerciseName, visualProvider, modifier)`.

- [x] **Step 1: Write failing provider mapping tests**

```kotlin
@Test fun framesFor_mapsWallCrawlIdToPinnedWorkoutGuideAssets() {
    assertThat(provider.framesFor("pull-ups").map(ExerciseVisual::assetPath))
        .containsExactly(
            "workout-guide/assets/pull-up/frame-1.svg",
            "workout-guide/assets/pull-up/frame-2.svg",
            "workout-guide/assets/pull-up/frame-3.svg"
        ).inOrder()
}
```

- [x] **Step 2: Run the provider test and confirm it fails because the abstraction is absent**

- [x] **Step 3: Implement provider descriptors/mappings and re-run the test**

- [x] **Step 4: Add verified Coil Compose/SVG dependencies and implement tasteful `1 → 2 → 3 → 2` local-asset rendering with fallback**

```kotlin
interface ExerciseVisualProvider {
    fun framesFor(exerciseId: String): List<ExerciseVisual>
}

data class ExerciseVisual(val assetPath: String)
```

- [x] **Step 5: Copy the nine pinned upstream SVGs without modification and add license, attribution, and provenance notice**

- [x] **Step 6: Remove raw `imageFrames` from the exercise domain model, wire the provider through screens, build the APK, and commit**

```bash
git add app/src/main app/src/test app/build.gradle.kts gradle/libs.versions.toml
git commit -m "feat: render bundled Workout Guide exercise frames"
```

### Task 6: Documentation and Full Verification

**Files:**
- Modify: `README.md`
- Inspect: all changed files and generated test reports.

**Interfaces:**
- Documents the completed feedback loop, visual abstraction, pinned upstream subset, and local build environment.

- [x] **Step 1: Update README claims so implemented and roadmap features are separated accurately**

- [x] **Step 2: Run focused tests for all new domain boundaries**

Run: `./gradlew testDebugUnitTest --tests '*WorkoutHistoryAnalyzerTest' --tests '*WorkoutGenerationContextBuilderTest' --tests '*ProgressCalculatorTest' --tests '*WorkoutGuideVisualProviderTest'`

- [x] **Step 3: Run the complete JVM suite and build the debug APK**

Run: `./gradlew testDebugUnitTest assembleDebug`

- [x] **Step 4: Run lint and inspect any failures/warnings**

Run: `./gradlew lintDebug`

- [x] **Step 5: Verify bundled assets/provenance and inspect the final diff**

```bash
find app/src/main/assets/workout-guide -type f | sort
git diff --check
git status --short
```

- [x] **Step 6: Commit documentation and verification-safe cleanup**

```bash
git add README.md
git commit -m "docs: describe the real workout feedback loop"
```
