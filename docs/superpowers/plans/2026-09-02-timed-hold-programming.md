# Timed-hold programming implementation plan

> Execute inline with Superpowers executing-plans and strict TDD. The two requested
> independent code-review agents run after implementation/documentation commits.

**Goal:** Complete the exact 14-entry timed programming milestone.

**Architecture:** Nullable rep metadata, validated with resolved exercise type at each
existing boundary; unchanged type-dispatched prescription factory and strength classifier.

**Tech stack:** Kotlin/Android, Python standard library, existing JUnit/instrumentation.

**Spec:** [Timed-hold design](../specs/2026-09-02-timed-hold-programming-design.md).

## Constraints

Use pinned Workout Guide `ba0b709cb20430361b2cb33aaadd20998164a916` without modifying
the supplied checkout. Keep 302 exercises / 906 frames, all original rep records,
reviewed DRAFT state, production gate false, and local-only application behavior.
No new dependencies, progression, substitutions, Health/Wear, LLM or Room schema changes.

## 1. Contract tests and implementation

Files: `tools/workout-guide/test_import_catalog.py`, new
`app/src/androidTest/assets/programming-validation-fixtures.json`,
`WorkoutGuideCatalogParserTest.kt`, new `ExerciseProgrammingMetadataTest.java`,
`PlannerFixtureContextFactoryTest.kt` (existing package directories).
Also update nullable consumers in `ExercisesScreen.kt` (rep badges) and
`PlannerFixtureEvaluator.kt` (snapshot deep copies), found during integration.

- [x] Build one shared fixture matrix of all five types versus absent/null/object,
  malformed and bounded numeric ranges. Python CLI import and Android parser consume
  the same cases; exercise type occurs both before and after programming on Android.
- [x] Capture RED on timed absence/null and timed-object rejection; domain tests can
  initially test forbidden objects without requiring nullable constructor compilation.
- [x] Add the small authored `tools/workout-guide/programming-rep-range-schema.json`
  definitions and select the rep/timed definition using the known catalog type in
  `import_catalog.py`. Normalize timed output to explicit null.
- [x] Change `Exercise.kt` to nullable metadata; enforce at construction/copy:
  `when (type) { DURATION, DISTANCE_DURATION -> require(range == null);
  else -> require(range != null && range.max <= 1000) }` when programming exists.
- [x] Update `WorkoutGuideCatalogParser.kt` to consume null explicitly, keep absence
  null, validate after resolving exerciseType, and read strict bounded integer endpoints.
- [x] Update `PlannerFixtureContextFactory.kt` to project missing/null as null and
  validate through the same domain contract without accepting malformed objects.
- [x] Capture GREEN for shared parity, projection and domain; retain bounded errors.

## 2. Exact cohort and catalog

Files: `test_programming_overrides.py`, new `TimedHoldProgrammingTest.kt` in the
JVM core/ai tests, `programming-overrides.json`, generated `catalog.json`.

- [x] Pin exact 14 IDs from the design. Derive duration/non-stretch/non-Cardio set
  independently; equality must fail if it shrinks or grows. Verify each can be selected
  by the real planner alone. Verify every excluded duration/distance candidate fails
  with NO_STRENGTH_CANDIDATES even when supplied otherwise valid timed programming.
- [x] Capture RED for absent complete timed programming and canonical null output.
- [x] Pin a canonical SHA-256 of all 117 baseline rep metadata records; snapshot the
  existing per-exercise prescriptions across goals and return states before code/data
  changes. Assert deterministic replay and absence of fabricated load.
- [x] Author all 14 records from pinned catalog/artwork, with complete fields and
  bounded descriptive coaching. Set progressionType duration and canonical range null.
- [x] Regenerate from disposable pinned checkout and run `--check` twice; assert
  authored/generated equality, exact cohort, supports/equipment, source and frame counts.
- [x] Prove each timed prescription equals its pre-metadata prescription, has positive
  targetDurationSeconds, and has no reps/load/assistance/distance target. Capture GREEN.

## 3. Documentation and complete gates

- [x] Update README milestone and programming count, architecture contract,
  deterministic-engine plan status, planner-evaluation projection documentation,
  and a focused `docs/timed-hold-programming.md` reference with the exact cohort,
  equipment, evidence/provenance limits, changed behavior and remaining limits.
- [x] Run `python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v`
  and the same command for `tools/release`.
- [x] Run pinned `import_catalog.py --source <disposable-pinned-checkout>` then
  `--check`; no additional generated file outside catalog may change.
- [x] Run focused unit tests and parser instrumentation, then
  `./gradlew test lint assembleDebug --rerun-tasks --stacktrace --no-daemon` and
  `./gradlew connectedDebugAndroidTest --rerun-tasks --stacktrace --no-daemon`.
- [x] Configure API 36 Google APIs emulator with animations disabled, matching CI
  except host-native ARM64 architecture. Record actual configuration and test counts.
- [x] Run diff whitespace, secret/debug/local-path/residue scans. Keep evidence logs
  outside Git and commit a concise validation record with RED/GREEN results.
- [x] Commit code/documentation with both required trailers.

## 4. Review and publish

- [ ] Dispatch exactly two independent code-review agents, Luna and Terra, against
  `git diff <base> <same-HEAD>`, scopes as specified in the design and user request.
- [ ] Fix valid findings with RED/GREEN regressions, rerun targeted/full validation,
  commit with trailers, and repeat both reviewers until no findings on one SHA.
- [ ] Fetch origin/main. If advanced, merge without history rewrite, repeat validation
  and both reviewers against the integrated SHA. Otherwise retain reviewed SHA.
- [ ] Push and create a non-draft PR stating cohort, contract, changes/preserved
  behavior, validation/reviewer outcomes, human gate and limitations.
- [ ] Verify clean worktree plus exact local HEAD, remote branch and PR head equality.

Required trailers on every commit:

```
Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>
Copilot-Session: 2e969d34-d3d3-40c2-8a45-545b905fc7e3
```


## Execution evidence before independent review

- Baseline: `origin/main` d8fc5a4; design/plan committed as 9d6afb0 before code.
- RED: 11 Python assertion failures captured for absent timed metadata and the old
  timed-range contract. Kotlin domain tests rejected the new nullable expectation and
  failed to reject fabricated timed ranges; projection and actual Android shared-fixture
  tests exposed numeric contract drift. The raw fixture format preserves 1.0 notation.
- GREEN: 63 Workout Guide Python tests and 6 release Python tests; the shared matrix
  covers 90 cases in Python, JVM projection, and Android (both JSON member orders).
- Focused importer/domain/prescription/planner/persona tests passed. Baseline rep
  metadata and prescription digests match. Exact source facts, frame/license artifacts,
  reviewed records and source commit match; pinned import and repeat --check passed.
- Full `test lint assembleDebug --rerun-tasks --stacktrace --no-daemon`: 463 unit
  tests, zero failures/skips; lint zero errors, 24 warnings, one hint; build successful.
- Full `connectedDebugAndroidTest --rerun-tasks --stacktrace --no-daemon`: 92 tests,
  zero failures/skips, API 36 Google APIs ARM64, three animation scales set to zero.
- Aggregate `assemble --rerun-tasks --stacktrace --no-daemon`: debug and release
  assembled successfully. An extra `testReleaseUnitTest` probe found no such configured
  task; the repository's aggregate `test` gate above executed its complete unit suite.
- Local validation uses Android Studio's bundled JBR 25.0.2 and host-native ARM64; CI uses
  JDK 17 and x86_64. API level, Google APIs image and disabled animations match CI.
- Diff whitespace, credential-pattern, machine-path, and debug/residue scans: no hits.
  Build/RED logs and the disposable pinned clone remain outside the repository.
- Supplied Workout Guide checkout remains clean at its original aac5992 revision.
- The app exposes no native branch-rename tool in this session; Git's native branch
  rename was used after creating the fresh worktree from fetched origin/main.

Independent review and publication results belong to the final PR, so their checkboxes
above describe required release gates rather than claiming an unperformed review here.
