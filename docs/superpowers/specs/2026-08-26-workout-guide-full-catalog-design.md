# WallCrawl Full Workout Guide Catalog Design

## Goal

Replace the production 12-exercise sample catalog and three-exercise visual proof with a complete, reproducible, offline Workout Guide import. Every imported exercise must be browseable, searchable, filterable, and connected to its bundled SVG frames. Workout generation must remain restricted to exercises whose WallCrawl-specific programming and required-equipment metadata has been reviewed.

The import is a development-time operation. The installed Android application must not depend on npm, a Workout Guide checkout, a network connection, or an external catalog service.

## Baseline

WallCrawl currently has a useful boundary in `ExerciseCatalog`, a reusable `ExerciseIllustration`, Coil SVG rendering, and a `WorkoutGuideVisualProvider`. Production still uses `InMemoryExerciseCatalog`, which contains 12 exercises with hand-authored programming metadata. The visual provider hard-codes mappings for three exercises and the asset bundle contains nine SVG frames.

The existing `Exercise` type mixes two kinds of information:

- catalog facts used to identify and browse an exercise;
- WallCrawl judgments used to decide whether and how to prescribe it.

That distinction matters for the full import. Workout Guide can authoritatively provide names, muscles, an exercise type, a listed equipment category, stretch status, and visual frames. It does not provide every hard equipment requirement or all of the programming metadata WallCrawl needs. Treating missing judgments as beginner/intermediate defaults would make the UI misleading and could offer unsafe or impossible exercises to a future model.

## Pinned Upstream Source

The first full import uses Workout Guide commit:

```text
ba0b709cb20430361b2cb33aaadd20998164a916
```

At that commit the upstream manifest contains 302 exercises, each with three SVG frames. The importer expects exactly 302 unique source exercise IDs and 906 SVG frame files for this pinned revision. These counts are validation invariants for this import, not permanent constants for every future Workout Guide version.

The importer copies SVG files only. Upstream also contains rendered PNG counterparts, but WallCrawl already renders SVG through Coil and does not need two copies of each illustration. Keeping only SVG preserves resolution independence and avoids roughly doubling the exercise-art bundle.

## Repository Layout

Development tooling and reviewed enrichment live outside the Android asset bundle:

```text
tools/workout-guide/
    import_catalog.py
    import-config.json
    programming-overrides.json
    test_import_catalog.py
```

Generated, app-owned input lives in Android assets:

```text
app/src/main/assets/workout-guide/
    catalog.json
    upstream-manifest.json
    NOTICE.md
    ATTRIBUTION.md
    LICENSE
    LICENSE-ASSETS
    assets/
        <upstream-slug>/
            frame-1.svg
            frame-2.svg
            frame-3.svg
```

`upstream-manifest.json` is the pinned upstream manifest preserved for provenance. `catalog.json` is the deterministic WallCrawl runtime format. Runtime code reads only `catalog.json` and the frame paths it contains; it does not understand the upstream repository layout.

`import-config.json` names the source repository, pinned commit, expected counts, source paths, and stable ID aliases. `programming-overrides.json` contains reviewed WallCrawl enrichment and is intentionally maintained separately from generated upstream facts.

## Development-Time Importer

`import_catalog.py` uses only the Python standard library. It receives a path to a local Workout Guide checkout. It does not clone, install, or execute upstream code, and it never runs npm.

The normal import flow is:

```text
local checkout at pinned commit
        ↓
validate source commit and licenses
        ↓
parse upstream manifest as untrusted input
        ↓
validate IDs, slugs, metadata, and frame paths
        ↓
apply stable-ID aliases and reviewed programming overrides
        ↓
write a temporary complete asset tree
        ↓
validate generated catalog and copied SVGs
        ↓
atomically replace the generated asset tree
```

The source path is explicit, for example:

```text
python3 tools/workout-guide/import_catalog.py \
  --source /path/to/workout-guide
```

The script refuses to import when any of these conditions occurs:

- the checkout does not resolve to the configured commit;
- tracked files under the imported manifest, asset, and license paths differ from that commit;
- a required upstream license or attribution file is missing;
- JSON is malformed or a required field has the wrong shape;
- an exercise ID, WallCrawl ID, or slug is blank, duplicated, or unsafe as a path segment;
- a frame resolves outside the expected source asset root;
- an exercise does not have exactly three readable SVG frames;
- an override points to a missing exercise or a missing alternative;
- generated IDs or frame paths are duplicated;
- generated counts differ from the configured pinned-revision counts.

Parsing rejects unexpected value types and bounds text/list sizes so a corrupt source cannot cause uncontrolled memory or output growth. Copied assets are regular SVG files resolved beneath the source root; symlink/path traversal is rejected. Import happens in a temporary sibling directory. The checked-in output is replaced only after the entire staged tree validates, so a failed import leaves the previous bundle intact.

`--check` builds and validates the expected output in a temporary directory, byte-compares it with the checked-in bundle, reports drift, and makes no repository changes. Normal and check modes print exercise count, frame count, and total copied SVG bytes.

Generated JSON uses stable key ordering, sorted exercises, normalized line endings, and a final newline. Re-running the importer against the same source and overrides must produce byte-identical output.

## Runtime Catalog Schema

`catalog.json` has a schema version, provenance, and exercises. Conceptually:

```json
{
  "schemaVersion": 1,
  "source": {
    "repository": "https://github.com/bryllim/workout-guide",
    "commit": "ba0b709cb20430361b2cb33aaadd20998164a916",
    "assetLicense": "CC-BY-SA-4.0"
  },
  "exercises": [
    {
      "id": "barbell-bench-press",
      "sourceId": "exercise-bench-press",
      "sourceSlug": "bench-press",
      "name": "Bench Press",
      "searchAliases": ["Barbell Bench Press"],
      "exerciseType": "weight_reps",
      "listedEquipment": ["Barbell"],
      "primaryMuscles": ["Chest"],
      "secondaryMuscles": ["Triceps", "Shoulders"],
      "isStretch": false,
      "frames": [
        {
          "index": 1,
          "assetPath": "workout-guide/assets/bench-press/frame-1.svg",
          "widthPx": 512,
          "heightPx": 512,
          "format": "svg",
          "attribution": {
            "creator": "Bryl Lim",
            "creatorUrl": "https://bryllim.com",
            "license": "CC BY-SA 4.0",
            "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
            "source": {
              "name": "Everkinetic",
              "url": "https://github.com/everkinetic/data/blob/main/dist/svg/0042-tension.svg",
              "license": "CC BY-SA 4.0",
              "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
              "changes": "Rasterized on a transparent 512 × 512 canvas, recolored for monochrome display, and vector-traced."
            }
          }
        }
      ],
      "attribution": {
        "creator": "Bryl Lim",
        "creatorUrl": "https://bryllim.com",
        "license": "CC BY-SA 4.0",
        "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
        "source": {
          "name": "Everkinetic",
          "url": "https://github.com/everkinetic/data/blob/main/dist/svg/0042-tension.svg",
          "license": "CC BY-SA 4.0",
          "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
          "changes": "Rasterized on a transparent 512 × 512 canvas, recolored for monochrome display, and vector-traced."
        }
      },
      "programming": {
        "requiredEquipmentCombinations": [["Barbell", "Bench"]],
        "movementPattern": "horizontal_push",
        "difficulty": "intermediate",
        "mechanics": "compound",
        "recommendedRepRange": { "min": 5, "max": 8 },
        "fatigueScore": 4,
        "progressionType": "repetitions_then_load",
        "alternativeExerciseIds": [],
        "coachingSummary": "Foundational horizontal press building chest, shoulder, and triceps strength."
      }
    }
  ]
}
```

The example abbreviates the repeated frame records and attribution objects rather than freezing their exact text; generated values come from the pinned manifest and reviewed overrides. The importer preserves every upstream exercise-level field (`id`, `slug`, `name`, `exerciseType`, `equipment`, `primaryMuscle`, `secondaryMuscles`, `isStretch`, `frames`, and `attribution`). Frame records retain their index, dimensions, format, and complete attribution while replacing the upstream-relative path with a validated Android asset path.

Field names deliberately state their authority:

- `id` is the stable WallCrawl identifier stored in workout history and exclusions.
- `sourceId` and `sourceSlug` preserve upstream identity and asset provenance.
- `listedEquipment` is searchable catalog metadata. It is not claimed to be a complete performance requirement.
- `requiredEquipmentCombinations` is reviewed WallCrawl data used by hard filtering. Every inner list is one complete set of simultaneously required equipment; the exercise is legal when the user satisfies at least one combination. An empty outer list means no external equipment is required.
- `searchAliases` preserves existing names and useful variants without changing identity.
- `programming` is absent when the exercise has not been reviewed for workout generation.

Unknown JSON fields are ignored to permit additive schema evolution. Missing or invalid required fields fail the whole catalog load instead of silently dropping individual exercises.

## Domain Model

The domain model continues to be owned by WallCrawl and contains no Workout Guide DTOs. Upstream parsing types stay inside the importer; Android parses the normalized runtime schema into domain types.

`Exercise` retains identity and browseable catalog facts. Programming judgments move into an optional `ExerciseProgrammingMetadata` value:

```kotlin
data class Exercise(
    val id: String,
    val source: ExerciseSource,
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

`ExerciseSource` holds the source catalog name, source ID, source slug, and normalized attribution. Validated frame records remain in the shared catalog snapshot used by `ExerciseVisualProvider`; raw asset paths do not enter feature UI or the general exercise domain model.

This removes misleading default programming values. Catalog filters use `listedEquipment`; generation hard filters use `programming.requiredEquipmentCombinations` and reject exercises with no programming metadata. The combination shape correctly distinguishes “bench and dumbbells” from “barbell or dumbbells”; a flat list cannot represent both cases honestly.

All current WallCrawl exercise IDs remain stable. Where the upstream source uses a different ID or slug, `import-config.json` maps the upstream record to the existing WallCrawl ID and adds the upstream/common wording as search aliases. This protects persisted workout history, progress aggregation, and user exclusions. Every other initial WallCrawl ID defaults to its upstream slug after collision validation.

The current 12 exercises receive complete reviewed programming overrides during this phase. The remaining imported exercises are fully visible and searchable but are not planner-eligible until their programming data is deliberately reviewed in later work. This is represented by data, not by a special hard-coded list in Kotlin.

## Runtime Loading and Catalog Behavior

Production creates one `WorkoutGuideCatalogStore` from Android `AssetManager`. A small `WorkoutGuideCatalogParser` reads `catalog.json` with Android platform JSON APIs; no JSON library is added. The store parses once on an injected I/O coroutine dispatcher, caches an immutable `WorkoutGuideCatalogSnapshot`, and indexes it by lower-cased WallCrawl ID. `BundledExerciseCatalog` and `WorkoutGuideVisualProvider` share that store, ensuring browsing and visual lookup cannot disagree about IDs or frames.

Catalog Flow and suspend operations wait for the shared load to finish. Once an exercise has been emitted to the UI, the same snapshot is synchronously available to `ExerciseVisualProvider.framesFor`. A visual lookup before initialization completes returns an empty list and therefore the existing placeholder; it never triggers main-thread asset I/O.

If asset I/O or parsing fails, the catalog exposes a typed initialization failure. Screens show an actionable catalog error and workout generation remains unavailable. Production does not silently fall back to the 12 samples, because that would hide a broken release bundle and present a different catalog than the installed assets. `InMemoryExerciseCatalog` remains as an injectable test fixture and may accept an explicit list for focused tests.

`ExerciseCatalog` continues to support:

- observing the complete ordered exercise list;
- case-insensitive lookup by stable WallCrawl ID;
- query search across name, search aliases, primary muscles, secondary muscles, and listed equipment;
- exact case-insensitive filtering by muscle;
- exact case-insensitive filtering by listed equipment;
- distinct sorted muscle and equipment filter values.

Search uses normalized case and whitespace but remains a simple in-memory substring search for 302 records. A database or full-text index would add complexity without a meaningful scale benefit.

## Visual Resolution

`WorkoutGuideVisualProvider` loads its ID-to-frame index from the same parsed catalog result as `BundledExerciseCatalog`. It no longer contains a three-entry hard-coded map and it does not construct paths from user-controlled IDs. `framesFor(exerciseId)` returns only validated frame paths recorded in `catalog.json`; an unknown ID returns an empty list so `ExerciseIllustration` keeps its existing placeholder behavior.

The UI remains unaware of Workout Guide slugs and raw asset paths:

```text
WallCrawl exercise ID
        ↓
ExerciseVisualProvider
        ↓
validated bundled SVG frame descriptors
        ↓
ExerciseIllustration
```

The current lightweight `1 → 2 → 3 → 2` animation remains unchanged.

## Workout Generation Boundary

`WorkoutGenerationContextBuilder` first obtains the full catalog, then asks `ExerciseFilter` for legal candidates. `ExerciseFilter` applies these hard requirements in order:

1. programming metadata exists;
2. the exercise is not in the user's exclusion set;
3. the user satisfies at least one complete `requiredEquipmentCombinations` option, or the reviewed exercise requires no external equipment;
4. an optional muscle target matches primary or secondary muscles.

The fake planner and future local LLM receive only the resulting candidates. `GeneratedWorkoutValidator` still verifies every output ID against both the catalog and the exact allowed-ID set. Browseable-but-unreviewed exercises can therefore never leak into generation, even if a future planner attempts to select one.

Descriptions, rep ranges, mechanics, fatigue, and progression rules shown or consumed by workout features come only from present programming metadata. The Exercises screen conditionally shows programming badges/details. It does not invent a rep range, difficulty, or compound/isolation classification for unreviewed exercises.

## Licensing and Provenance

The generated bundle preserves the upstream repository license, asset license, and attribution without modification. `NOTICE.md` states the source repository, pinned commit, imported counts, and that SVG files were copied without artistic modification. `LICENSE`, `LICENSE-ASSETS`, `ATTRIBUTION.md`, and `upstream-manifest.json` remain beside the generated catalog so the applicable terms and per-frame provenance are retained. Omitting a wall-clock import date keeps repeated output deterministic; the pinned commit is the provenance identity.

The repository README documents how to reproduce and verify the import, makes clear that the upstream checkout is only a development input, and credits Workout Guide and its underlying artwork according to the preserved license files.

## Testing and Verification

Importer tests use Python's standard `unittest` and temporary directories. They cover malformed JSON, wrong commits, duplicate/unsafe IDs, missing frames, traversal/symlinks, invalid overrides, missing licenses, deterministic output, no-write `--check`, and preservation of the old bundle after failure.

Android tests cover:

- parsing a representative normalized manifest into domain models;
- rejecting malformed or structurally invalid catalogs;
- loading exactly 302 unique exercises from the bundled release asset;
- confirming every exercise has three existing SVG frames;
- preserving all 12 existing WallCrawl IDs;
- search by name, alias, muscle, and listed equipment;
- muscle and equipment filters;
- visual lookup for aliased and direct IDs;
- planner eligibility only for reviewed exercises;
- hard filtering against reviewed required equipment;
- generated-workout rejection for browseable but disallowed IDs;
- conditional UI state for exercises without programming metadata.

Repository verification runs the importer tests, importer `--check`, Android unit tests, Android instrumentation tests, debug assembly, and lint. Emulator smoke testing opens the catalog, searches and filters the full list, opens direct-ID and aliased exercise details, renders animated SVGs, and confirms Today still generates and starts a workout from the reviewed subset.

## Acceptance Criteria

- The APK contains `catalog.json`, all 302 imported exercises, and exactly three bundled SVG frames per exercise.
- No Workout Guide PNG files, npm package, runtime network call, or new JSON runtime dependency is introduced.
- The import can be reproduced from a local checkout at the pinned commit using Python 3 alone.
- `--check` detects any generated catalog or asset drift without modifying files.
- All 302 exercises can be listed, searched, filtered, opened, and illustrated offline.
- Existing exercise IDs continue to resolve so stored history and exclusions remain valid.
- The reviewed current 12 remain available to workout generation with their existing programming behavior.
- Unreviewed exercises are absent from allowed planner candidates and rejected by post-generation validation.
- Licensing and per-asset provenance remain in the repository and packaged app.
- Existing build, test, lint, and emulator verification remain green.

## Out of Scope

- Production Qwen, Gemma, LiteRT, or any other local model runtime
- Automatically inferring programming metadata for all 302 exercises
- Planner eligibility for unreviewed catalog entries
- Bundling PNG duplicates or video assets
- Downloading or updating catalog content at application runtime
- Storing the immutable catalog in Room
- npm, pip, or an upstream package dependency
- Custom exercises, catalog updates in the UI, or cloud sync
- Broad module extraction, Hilt migration, or unrelated UI redesign

## Delivery Shape

Implementation should remain a single coherent catalog pull request with small commits: importer tests/tooling, generated licensed bundle, domain/runtime catalog changes, visual provider integration, planner-safety/UI adaptations, and documentation/verification. The generated asset commit will be large but mechanically reproducible and isolated from hand-written runtime changes.
