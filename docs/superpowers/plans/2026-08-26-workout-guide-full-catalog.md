# WallCrawl Full Workout Guide Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vendor the pinned 302-exercise Workout Guide catalog and 906 SVG frames, expose the complete offline catalog in WallCrawl, and keep workout generation limited to 12 reviewed exercises.

**Architecture:** A Python-standard-library importer converts a pinned local Workout Guide checkout into a deterministic Android asset bundle. Android parses that normalized bundle once through a shared store used by `BundledExerciseCatalog` and `WorkoutGuideVisualProvider`; catalog facts remain separate from optional WallCrawl programming metadata so unreviewed exercises are browseable but never planner candidates.

**Tech Stack:** Python 3 standard library, Kotlin, Android `JsonReader`, Coroutines, Flow, Jetpack Compose, Coil SVG, JUnit, Android instrumentation tests

**Spec:** `docs/superpowers/specs/2026-08-26-workout-guide-full-catalog-design.md`

## Global Constraints

- Import only Workout Guide commit `ba0b709cb20430361b2cb33aaadd20998164a916`.
- Bundle all 302 exercises and exactly 906 SVG frames; do not copy PNG counterparts.
- Preserve upstream `LICENSE`, `LICENSE-ASSETS`, `ATTRIBUTION.md`, and full manifest provenance.
- Use no npm, pip, runtime network, or new Android JSON dependency.
- Preserve all 12 existing WallCrawl IDs used by history and exclusions.
- Search/filter all 302 exercises, but expose only entries with reviewed programming metadata to the planner.
- Keep `GeneratedWorkoutValidator` as the post-planner allowed-ID boundary.
- Keep the unrelated untracked `app/.DS_Store` untouched and out of every commit.
- Follow red-green-refactor for Python, domain, catalog, filtering, and UI behavior changes.

---

## File Structure

### Import tooling and generated assets

- Create `tools/workout-guide/import_catalog.py`: validate, normalize, and atomically import a local pinned checkout.
- Create `tools/workout-guide/test_import_catalog.py`: standard-library importer behavior tests.
- Create `tools/workout-guide/import-config.json`: pinned paths/counts and stable WallCrawl ID aliases.
- Create `tools/workout-guide/programming-overrides.json`: reviewed programming metadata for the current 12 exercises.
- Replace `app/src/main/assets/workout-guide/manifest.json` with `catalog.json` and `upstream-manifest.json`.
- Replace the nine proof assets with all 906 generated SVGs under `app/src/main/assets/workout-guide/assets/`.
- Add the upstream root `LICENSE`; refresh the asset license, attribution, and WallCrawl notice.

### Android domain and runtime

- Modify `core/model/Exercise.kt`: separate catalog facts from nullable programming metadata and add source/attribution types.
- Create `core/exercise/workoutguide/WorkoutGuideCatalogSnapshot.kt`: immutable exercises plus ID-to-frame index and source contract.
- Create `core/exercise/workoutguide/WorkoutGuideCatalogParser.kt`: strict Android asset JSON parser.
- Create `core/exercise/workoutguide/WorkoutGuideCatalogStore.kt`: single cached off-main-thread asset load.
- Create `core/exercise/BundledExerciseCatalog.kt`: complete searchable/filterable catalog backed by the shared store.
- Modify `core/exercise/InMemoryExerciseCatalog.kt`: reusable injected test fixture using the new model.
- Modify `core/exercise/visual/ExerciseVisualProvider.kt`: retain normalized visual metadata.
- Modify `core/exercise/visual/WorkoutGuideVisualProvider.kt`: resolve frames from the shared snapshot instead of a hard-coded map.
- Modify `WallCrawlApplication.kt`: wire the bundled catalog/store in production.

### Planner and UI adaptations

- Modify `core/exercise/ExerciseFilter.kt`: reject unreviewed entries and evaluate alternative complete equipment combinations.
- Modify `core/ai/FakeWorkoutPlanner.kt`: consume non-null programming metadata from already-filtered candidates.
- Modify `feature/exercises/ExercisesScreen.kt`: conditionally render reviewed programming badges while showing all upstream facts.
- Modify `feature/exercises/ExercisesViewModel.kt`: surface catalog loading/parsing failure as `ExercisesUiState.Error`.
- Modify `core/ui/components/ExerciseIllustration.kt`: use optional programming movement pattern in placeholder copy.
- Modify `README.md`: document full offline catalog, reproduction command, source pin, and licenses.

### Tests

- Modify existing catalog/filter/planner tests for the new domain shape.
- Create `app/src/androidTest/java/wallcrawl/elopenmike/com/core/exercise/workoutguide/WorkoutGuideCatalogParserTest.kt`: verify the packaged 302/906 asset bundle and preserved IDs.
- Modify visual provider tests to use a fixed shared snapshot.
- Add UI-state/domain assertions for unreviewed exercises.

## Task 1: Build the Dependency-Free Importer

**Interfaces:**

```text
import_catalog(source_root: Path, output_root: Path, config_path: Path, overrides_path: Path, check_only: bool) -> ImportSummary
```

```python
@dataclass(frozen=True)
class ImportSummary:
    exercise_count: int
    frame_count: int
    svg_bytes: int
    changed: bool
```

- [ ] Write `test_import_catalog.py` tests that create a one-exercise temporary git checkout and assert: successful SVG-only output, stable ID aliasing, exact normalized metadata/attribution, deterministic second import, drift detection in `--check`, wrong-commit rejection, dirty-imported-path rejection, malformed/duplicate IDs, unsafe paths/symlinks, missing frames/licenses, invalid programming references, and old-output preservation after failure.
- [ ] Run `python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v`; verify RED because `import_catalog.py` does not exist.
- [ ] Implement bounded JSON/type/path validation, git commit/dirty checks, stable output ordering, staged tree comparison, and rollback-safe directory replacement using only `argparse`, `dataclasses`, `hashlib`, `json`, `os`, `pathlib`, `shutil`, `subprocess`, and `tempfile`.
- [ ] Add `import-config.json` with the pinned repository/commit, source manifest/assets/license paths, expected `302`/`906` counts, and these source-to-WallCrawl ID mappings:

```text
exercise-incline-dumbbell-press → incline-dumbbell-press
exercise-bench-press            → barbell-bench-press
exercise-pull-up               → pull-ups
exercise-deadlift              → barbell-deadlift
exercise-squat                 → barbell-back-squat
exercise-seated-dumbbell-press → dumbbell-shoulder-press
exercise-lateral-raise         → dumbbell-lateral-raise
exercise-tricep-pushdown       → cable-triceps-pushdown
exercise-ez-bar-curl           → barbell-bicep-curl
exercise-chest-dip             → parallel-bar-dips
exercise-hanging-leg-raise     → hanging-leg-raise
exercise-romanian-deadlift     → romanian-deadlift
```

- [ ] Add `programming-overrides.json` with the existing movement pattern, difficulty, mechanics, rep range, fatigue score, and coaching summary for all 12 IDs plus reviewed equipment combinations. Use alternatives for RDL (`Barbell` or `Dumbbell`) and require `Dip Bars` for parallel-bar dips.
- [ ] Re-run the importer tests; verify GREEN with every test executed.
- [ ] Commit tooling/config/overrides as `feat: add pinned Workout Guide importer`.

## Task 2: Generate and Verify the Complete Licensed Bundle

**Consumes:** Task 1 CLI and a local checkout at the pinned commit.

**Produces:** `catalog.json`, `upstream-manifest.json`, licenses/notice, and 906 SVGs.

- [ ] Run the importer against the verified local checkout and capture its summary.
- [ ] Run importer `--check` against the generated tree and verify byte-identical output.
- [ ] Independently parse `catalog.json` with Python and assert 302 unique WallCrawl IDs, 302 unique source IDs/slugs, five supported exercise types, 12 programmed exercises, a three-frame SVG visual specification, 906 existing derived `.svg` paths, zero `.png` files, and all 12 historical IDs.
- [ ] Verify `LICENSE`, `LICENSE-ASSETS`, and `ATTRIBUTION.md` match their pinned upstream files byte-for-byte; verify `NOTICE.md` names the source URL and commit.
- [ ] Inspect generated size and largest files with `du`/`find`; confirm no file approaches GitHub's individual-file limit.
- [ ] Commit the mechanically generated bundle separately as `feat: bundle full Workout Guide catalog`.

## Task 3: Separate Catalog Facts from Programming Metadata

**Interfaces:**

```kotlin
data class Exercise(
    val id: String,
    val source: ExerciseSource?,
    val name: String,
    val searchAliases: List<String>,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val listedEquipment: List<String>,
    val type: ExerciseType,
    val isStretch: Boolean,
    val programming: ExerciseProgrammingMetadata?
)

data class ExerciseProgrammingMetadata(
    val requiredEquipmentCombinations: List<List<String>>,
    val movementPattern: MovementPattern,
    val difficulty: Difficulty,
    val mechanics: MechanicsType,
    val recommendedRepRange: RepRange,
    val fatigueScore: Int,
    val progressionType: ProgressionType,
    val alternativeExerciseIds: List<String>,
    val coachingSummary: String
)
```

- [ ] Update focused catalog/filter/planner tests first to construct a reviewed and unreviewed exercise, and verify RED for missing new fields/behavior.
- [ ] Implement `ExerciseSource`, attribution types, the five upstream-aligned `ExerciseType` values, `ProgressionType`, nullable programming metadata, and `StandardEquipment.DIP_BARS`.
- [ ] Convert the 12 `InMemoryExerciseCatalog` fixtures to the new model and allow constructor injection of an arbitrary list.
- [ ] Update search to include aliases and `listedEquipment`; update distinct equipment values accordingly.
- [ ] Implement filtering so programming must exist and at least one required-equipment combination is fully available; an empty combination list means no external equipment.
- [ ] Update `FakeWorkoutPlanner` to require reviewed programming from its legal candidates and use nested mechanics/rep range/coaching summary.
- [ ] Run all affected JVM tests and verify GREEN, then commit as `refactor: separate exercise catalog and programming metadata`.

## Task 4: Load the Bundled Catalog and Share Its Visual Index

**Interfaces:**

```kotlin
interface WorkoutGuideCatalogSource {
    suspend fun snapshot(): WorkoutGuideCatalogSnapshot
    fun currentSnapshot(): WorkoutGuideCatalogSnapshot?
}

data class WorkoutGuideCatalogSnapshot(
    val exercises: List<Exercise>,
    val framesByExerciseId: Map<String, List<ExerciseVisual>>
)
```

- [ ] Add JVM tests for `BundledExerciseCatalog` using a fixed `WorkoutGuideCatalogSource`: list/order, case-insensitive ID lookup, name/alias/muscle/equipment search, exact filters, and source failure propagation. Verify RED before implementation.
- [ ] Add Android parser tests using the real packaged `catalog.json`: exact 302/906 counts, unique IDs, three frames per item, direct/aliased historical lookups, 12 programmed entries, and parser rejection of malformed fixtures. Verify RED before parser/store implementation.
- [ ] Implement `WorkoutGuideCatalogParser` with `android.util.JsonReader`, explicit enum mapping, bounded collections/strings, duplicate rejection, schema/version checks, safe source-slug and visual-spec validation, derived frame descriptors, optional programming parsing, and unknown-field skipping.
- [ ] Implement `WorkoutGuideCatalogStore` with an injected I/O dispatcher, `Mutex`, one cached immutable snapshot, and a synchronous `currentSnapshot()` that never performs I/O.
- [ ] Implement `BundledExerciseCatalog` Flow/suspend operations over the shared snapshot.
- [ ] Extend `ExerciseVisual` with `widthPx`, `heightPx`, and normalized attribution, and change `WorkoutGuideVisualProvider` to read `framesByExerciseId` from the shared source.
- [ ] Run JVM and instrumentation tests; verify GREEN and commit as `feat: load bundled Workout Guide catalog`.

## Task 5: Wire Production, Planner Safety, and Catalog UI

- [ ] Add/adjust failing tests showing: production-style unreviewed entries are absent from `WorkoutGenerationContext.allowedExercises`; validator rejects an unreviewed/disallowed ID; Exercises state emits an error when catalog loading fails; unreviewed UI data does not require fake mechanics/rep ranges.
- [ ] Wire one `WorkoutGuideCatalogStore(context.assets)` into both `BundledExerciseCatalog` and `WorkoutGuideVisualProvider` in `DefaultAppContainer`; remove production use of `InMemoryExerciseCatalog`.
- [ ] Add Flow error handling in `ExercisesViewModel` so parser/I/O failures become `ExercisesUiState.Error`.
- [ ] Render `listedEquipment` for all catalog entries. Render mechanics, rep range, movement pattern, difficulty, fatigue, and coaching summary only when `programming != null`; show a neutral “Programming review pending” detail message otherwise.
- [ ] Update `ExerciseIllustration` placeholder labeling for nullable programming metadata.
- [ ] Run affected JVM tests and debug compilation after each red-green cycle; commit as `feat: expose full offline exercise catalog`.

## Task 6: Document and Verify the Complete Vertical Slice

- [ ] Update README catalog documentation with the pinned commit, Python-only local-checkout import command, `--check`, generated asset layout, 302/906 counts, SVG-only choice, runtime architecture, planner eligibility rule, and licensing paths.
- [ ] Run importer tests and `--check` against the pinned checkout.
- [ ] Run a clean `./gradlew testDebugUnitTest lintDebug assembleDebug --rerun-tasks` under the configured JDK 17/Android SDK.
- [ ] Run `./gradlew connectedDebugAndroidTest` on the available emulator and record test counts/API level.
- [ ] Install/launch the debug APK and smoke-test: full count, text search, muscle/equipment filters, direct and aliased visual rendering, Today generation from reviewed IDs, active workout start, and no bottom-nav regression.
- [ ] Run asset/license/count checks, `git diff --check`, secret/debug-residue scans, and confirm only the pre-existing `.DS_Store` is untracked.
- [ ] Apply completion, comparison, cleanup, and handoff gates; push `codex/workout-guide-catalog` and open a PR with exact test/build/emulator evidence. Do not merge it or publish a release.
