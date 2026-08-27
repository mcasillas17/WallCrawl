# Custom Workout Templates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reusable local workout templates, type-aware logging, and full-catalog eligibility for manual and automated workout creation.

**Architecture:** Introduce one shared `ExercisePrescription` model used by planner output, saved templates, and frozen session snapshots. Persist templates in dedicated Room tables and extend session records for duration/distance/assistance outcomes. Expose template management from Today through pushed Compose routes while keeping the current active-workout and history flow.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Room, Coroutines, Flow/StateFlow, JUnit, AndroidX instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-08-26-custom-workout-templates-design.md`

## Global Constraints

- Use JDK 17 and the existing Gradle Kotlin DSL build.
- Keep all template/catalog/session data local and available offline.
- Manual selection exposes all 302 catalog exercises and warns about equipment mismatches.
- Automatic planning starts from all 302 exercises, then enforces equipment and user exclusions.
- Never invent or silently substitute an exercise ID.
- Starting a template creates a frozen session snapshot; later template edits/deletion cannot mutate history.
- Do not add a production local LLM or a fifth bottom-navigation item.
- Preserve the pre-existing untracked `app/.DS_Store` without modifying or committing it.

---

### Task 1: Shared Type-Aware Exercise Prescriptions

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/ExercisePrescription.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/Workout.kt`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/model/ExercisePrescriptionTest.kt`
- Modify: existing tests constructing `GeneratedExercise`, `WorkoutExercise`, and `WorkoutSet`

**Interfaces:**
- Produces: `ExercisePrescription`, `PlannedExercise`, `SetPerformanceInput`, `WorkoutOrigin`.
- Consumed by: validator, planner, template repository, workout repository, active logger.

- [ ] **Step 1: Write failing tests for each exercise type and invalid target combination**

```kotlin
assertThat(
    ExercisePrescription(
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetSets = 3,
        repRange = RepRange(8, 10),
        targetWeight = 45.0
    ).validate()
).isNotNull()

assertFailsWith<IllegalArgumentException> {
    ExercisePrescription(
        exerciseType = ExerciseType.DURATION,
        targetSets = 3,
        repRange = RepRange(8, 10),
        targetDurationSeconds = null
    ).validate()
}
```

- [ ] **Step 2: Run `ExercisePrescriptionTest` and verify RED because the model does not exist**

Run: `./gradlew --no-daemon testDebugUnitTest --tests '*ExercisePrescriptionTest'`

- [ ] **Step 3: Implement the shared prescription and performance input**

```kotlin
data class ExercisePrescription(
    val exerciseType: ExerciseType,
    val targetSets: Int,
    val repRange: RepRange? = null,
    val targetWeight: Double? = null,
    val targetAssistanceWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val restSeconds: Int = 90
) {
    fun validate(): ExercisePrescription
}

data class SetPerformanceInput(
    val reps: Int? = null,
    val weight: Double? = null,
    val assistanceWeight: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val isCompleted: Boolean
)

enum class WorkoutOrigin { PLANNER, CUSTOM_TEMPLATE }

data class PlannedExercise(
    val exerciseId: String,
    val prescription: ExercisePrescription,
    val notes: String = ""
)

typealias GeneratedExercise = PlannedExercise
```

Change `GeneratedExercise` and `WorkoutExercise` to contain `prescription`; extend `WorkoutSet` with type-specific target/outcome fields while retaining weight × reps volume behavior.

- [ ] **Step 4: Update model consumers only enough to compile, run focused model tests, and verify GREEN**

- [ ] **Step 5: Commit as `refactor: add type-aware exercise prescriptions`**

### Task 2: Full-Catalog Filtering, Planning, and Validation

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/DefaultExercisePrescriptionFactory.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/exercise/ExerciseFilter.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/GeneratedWorkoutValidator.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/exercise/ExerciseFilterTest.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/GeneratedWorkoutValidatorTest.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilderTest.kt`

**Interfaces:**
- Consumes: `Exercise`, `ExercisePrescription`, `WorkoutGenerationContext`.
- Produces: `DefaultExercisePrescriptionFactory.create(exercise, context): ExercisePrescription`.

- [ ] **Step 1: Add failing tests proving unreviewed exercises survive filtering when equipment matches**

```kotlin
val allowed = filter.filterCandidates(
    allExercises = listOf(unreviewedBodyweightExercise),
    profile = UserProfile(availableEquipment = listOf("Bodyweight"))
)
assertThat(allowed.map { it.id }).containsExactly(unreviewedBodyweightExercise.id)
```

Also cover reviewed alternative equipment combinations, fallback `listedEquipment`, exclusions, and a mismatched fallback requirement.

- [ ] **Step 2: Add failing planner/validator tests for all five `ExerciseType` values**

Use contexts whose only allowed exercise has no reviewed programming metadata. Verify the planner returns that ID with a type-correct prescription and the validator rejects mismatched type/targets.

- [ ] **Step 3: Run focused tests and verify RED at the old programming eligibility gate**

- [ ] **Step 4: Remove the programming-null gate and implement type-aware conservative defaults**

```kotlin
fun create(exercise: Exercise, context: WorkoutGenerationContext): ExercisePrescription =
    when (exercise.type) {
        ExerciseType.WEIGHT_REPS -> repetitions(exercise, context, 8, 12)
        ExerciseType.BODYWEIGHT_REPS -> repetitions(exercise, context, 8, 15)
        ExerciseType.ASSISTED_BODYWEIGHT -> assisted(exercise, context, 6, 10)
        ExerciseType.DURATION -> duration(exercise, seconds = if (exercise.isStretch) 30 else 45)
        ExerciseType.DISTANCE_DURATION -> distanceDuration(exercise, seconds = 600)
    }
```

Use reviewed equipment combinations when available; otherwise require every nonblank `listedEquipment` entry.

- [ ] **Step 5: Make `GeneratedWorkoutValidator` compare catalog type with prescription type and call `prescription.validate()`**

- [ ] **Step 6: Run all affected JVM tests and verify GREEN**

- [ ] **Step 7: Commit as `feat: enable full-catalog workout planning`**

### Task 3: Room Schema 4 and Template Repository

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/dao/Daos.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/database/relation/WorkoutTemplateWithExercises.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutTemplate.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutTemplateRepository.kt`
- Create: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/WorkoutTemplateDaoTest.kt`
- Create: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration3To4Test.kt`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutTemplateRepositoryTest.kt`

**Interfaces:**
- Produces: `WorkoutTemplateRepository.observeTemplates()`, `observeTemplate(id)`, `getTemplate(id)`, `saveTemplate(template)`, `deleteTemplate(id)`.
- Consumed by: Today and template feature ViewModels; workout session start.

- [ ] **Step 1: Write failing DAO tests for ordered CRUD and atomic exercise replacement**

- [ ] **Step 2: Write a failing 3→4 migration test that inserts a profile, session, exercise, and set into schema 3 and verifies all survive opening schema 4**

- [ ] **Step 3: Add template entities and flattened prescription columns**

```kotlin
@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String,
    val createdAtTimestamp: Long,
    val updatedAtTimestamp: Long
)
```

`WorkoutTemplateExerciseEntity` has a cascading `templateId` foreign key, unique `(templateId, orderIndex)` index, catalog `exerciseId`, `exerciseType`, and every nullable target field from `ExercisePrescription`.

- [ ] **Step 4: Implement `MIGRATION_3_4`, rebuilding workout exercise/set tables where nullable repetition columns change and adding session origin/source fields**

- [ ] **Step 5: Implement repository validation and transactional save/delete mapping**

- [ ] **Step 6: Run repository JVM tests and Room instrumentation tests; verify GREEN**

- [ ] **Step 7: Commit as `feat: persist custom workout templates`**

### Task 4: Frozen Template Sessions and Type-Aware Set Logging

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutRepository.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/dao/Daos.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/relation/WorkoutSessionWithExercisesAndSets.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutRepositoryTest.kt`
- Modify: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/WorkoutLifecycleDaoTest.kt`

**Interfaces:**
- Produces: `startWorkoutFromTemplate(template, userProfile)` and `logSetCompletion(setId, SetPerformanceInput)`.
- Consumed by: template library and active workout ViewModels.

- [ ] **Step 1: Add failing tests proving template start snapshots every field and later template changes do not alter the session**

- [ ] **Step 2: Add failing tests for repetition, duration, distance, and assistance performance validation**

- [ ] **Step 3: Generalize session creation through one private atomic mapper used by planner and template starts**

```kotlin
private suspend fun startWorkout(
    name: String,
    notes: String,
    focusMuscles: List<String>,
    exercises: List<PlannedExercise>,
    origin: WorkoutOrigin,
    sourceTemplateId: String?,
    userProfile: UserProfile
): WorkoutSession
```

- [ ] **Step 4: Update the set DAO/repository to persist all type-aware completion fields in one guarded update**

- [ ] **Step 5: Run affected JVM and instrumentation tests; verify GREEN**

- [ ] **Step 6: Commit as `feat: start and log template workouts`**

### Task 5: Template Library, Editor, and Today Entry Point

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/templates/WorkoutTemplatesUiState.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/templates/WorkoutTemplatesViewModel.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/templates/WorkoutTemplatesScreen.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/templates/TemplateEditorUiState.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/templates/TemplateEditorViewModel.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/templates/TemplateEditorScreen.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/today/TodayUiState.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/today/TodayViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/today/TodayScreen.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/app/WallCrawlApp.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/WallCrawlApplication.kt`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/feature/templates/WorkoutTemplatesViewModelTest.kt`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/feature/templates/TemplateEditorViewModelTest.kt`
- Modify/Create: Compose navigation tests under `app/src/androidTest`

**Interfaces:**
- Consumes: template repository, workout repository, user profile, exercise catalog/visual provider.
- Produces: routes `templates`, `template/new`, `template/{templateId}` and Today callbacks.

- [ ] **Step 1: Write failing ViewModel tests for load, create, edit, reorder, remove, save failure retention, delete, and start**

- [ ] **Step 2: Implement draft state with bounded string inputs and type-specific target setters**

- [ ] **Step 3: Implement searchable picker using all catalog exercises and `equipmentWarning` derived from the current profile**

- [ ] **Step 4: Build the template library/editor Compose screens with accessible labels, move controls, confirmation dialogs, loading states, and error copy**

- [ ] **Step 5: Add Today’s My Workouts preview and wire pushed routes without changing bottom navigation**

- [ ] **Step 6: Run ViewModel and Compose tests; verify GREEN**

- [ ] **Step 7: Commit as `feat: add custom workout template editor`**

### Task 6: Type-Aware Active Workout UI

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutUiState.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutScreen.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/feature/workout/ActiveWorkoutViewModelTest.kt`
- Modify/Create: active workout Compose tests under `app/src/androidTest`

**Interfaces:**
- Consumes: session-snapshotted `ExercisePrescription` and `SetPerformanceInput`.
- Produces: type-appropriate set logging UI and persisted outcomes.

- [ ] **Step 1: Add failing ViewModel tests for duration, distance-duration, and assisted completion input**

- [ ] **Step 2: Change `updateSet` to accept `SetPerformanceInput` and keep cancellation/error behavior**

- [ ] **Step 3: Render fields by `ExerciseType`**

```kotlin
when (prescription.exerciseType) {
    ExerciseType.WEIGHT_REPS -> RepsWeightInputs(...)
    ExerciseType.BODYWEIGHT_REPS -> RepsInput(...)
    ExerciseType.ASSISTED_BODYWEIGHT -> RepsAssistanceInputs(...)
    ExerciseType.DURATION -> DurationInput(...)
    ExerciseType.DISTANCE_DURATION -> DistanceDurationInputs(...)
}
```

- [ ] **Step 4: Keep previous-performance display type-aware and omit irrelevant volume/weight copy**

- [ ] **Step 5: Run active-workout JVM and Compose tests; verify GREEN**

- [ ] **Step 6: Commit as `feat: log every catalog exercise type`**

### Task 7: Documentation and End-to-End Verification

**Files:**
- Modify: `README.md`
- Modify: relevant tests discovered during the full run

**Interfaces:**
- Produces: verified feature branch ready for review.

- [ ] **Step 1: Document local templates, full-catalog eligibility, equipment policy, frozen sessions, and future advisor boundary**

- [ ] **Step 2: Run `python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v` and importer `--check`**

- [ ] **Step 3: Run `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug --rerun-tasks` under JDK 17**

- [ ] **Step 4: Run `./gradlew --no-daemon connectedDebugAndroidTest` on the Android emulator**

- [ ] **Step 5: Install and manually smoke-test create → save → reopen/edit → start → log → finish → history → restart template, including duration/distance and an equipment warning**

- [ ] **Step 6: Run diff, secret, debug-residue, migration, asset-count, and licensing checks; confirm no CI/CD files changed**

- [ ] **Step 7: Request code review, address Critical/Important findings, push the branch, and open a PR without merging it**
