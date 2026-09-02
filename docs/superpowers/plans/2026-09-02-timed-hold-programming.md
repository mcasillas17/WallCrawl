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
`WorkoutGuideCatalogParserTest.kt`, new `ExerciseProgrammingMetadataTest.kt`,
`PlannerFixtureContextFactoryTest.kt` (existing package directories).

- [ ] Build one shared fixture matrix of all five types versus absent/null/object,
  malformed and bounded numeric ranges. Python CLI import and Android parser consume
  the same cases; exercise type occurs both before and after programming on Android.
- [ ] Capture RED on timed absence/null and timed-object rejection; domain tests can
  initially test forbidden objects without requiring nullable constructor compilation.
- [ ] Add the small authored `tools/workout-guide/programming-rep-range-schema.json`
  definitions and select the rep/timed definition using the known catalog type in
  `import_catalog.py`. Normalize timed output to explicit null.
- [ ] Change `Exercise.kt` to nullable metadata; enforce at construction/copy:
  `when (type) { DURATION, DISTANCE_DURATION -> require(range == null);
  else -> require(range != null && range.max <= 1000) }` when programming exists.
- [ ] Update `WorkoutGuideCatalogParser.kt` to consume null explicitly, keep absence
  null, validate after resolving exerciseType, and read strict bounded integer endpoints.
- [ ] Update `PlannerFixtureContextFactory.kt` to project missing/null as null and
  validate through the same domain contract without accepting malformed objects.
- [ ] Capture GREEN for shared parity, projection and domain; retain bounded errors.

## 2. Exact cohort and catalog

Files: `test_programming_overrides.py`, new `TimedHoldProgrammingTest.kt` in the
JVM core/ai tests, `programming-overrides.json`, generated `catalog.json`.

- [ ] Pin exact 14 IDs from the design. Derive duration/non-stretch/non-Cardio set
  independently; equality must fail if it shrinks or grows. Verify each can be selected
  by the real planner alone. Verify every excluded duration/distance candidate fails
  with NO_STRENGTH_CANDIDATES even when supplied otherwise valid timed programming.
- [ ] Capture RED for absent complete timed programming and canonical null output.
- [ ] Pin a canonical SHA-256 of all 117 baseline rep metadata records; snapshot the
  existing per-exercise prescriptions across goals and return states before code/data
  changes. Assert deterministic replay and absence of fabricated load.
- [ ] Author all 14 records from pinned catalog/artwork, with complete fields and
  bounded descriptive coaching. Set progressionType duration and canonical range null.
- [ ] Regenerate from disposable pinned checkout and run `--check` twice; assert
  authored/generated equality, exact cohort, supports/equipment, source and frame counts.
- [ ] Prove each timed prescription equals its pre-metadata prescription, has positive
  targetDurationSeconds, and has no reps/load/assistance/distance target. Capture GREEN.

## 3. Documentation and complete gates

- [ ] Update README milestone and programming count, architecture contract,
  deterministic-engine plan status, planner-evaluation projection documentation,
  and a focused `docs/timed-hold-programming.md` reference with the exact cohort,
  equipment, evidence/provenance limits, changed behavior and remaining limits.
- [ ] Run `python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v`
  and the same command for `tools/release`.
- [ ] Run pinned `import_catalog.py --source <disposable-pinned-checkout>` then
  `--check`; no additional generated file outside catalog may change.
- [ ] Run focused unit tests and parser instrumentation, then
  `./gradlew test lint assembleDebug --rerun-tasks --stacktrace --no-daemon` and
  `./gradlew connectedDebugAndroidTest --rerun-tasks --stacktrace --no-daemon`.
- [ ] Configure API 36 Google APIs emulator with animations disabled, matching CI
  except host-native ARM64 architecture. Record actual configuration and test counts.
- [ ] Run diff whitespace, secret/debug/local-path/residue scans. Keep evidence logs
  outside Git and commit a concise validation record with RED/GREEN results.
- [ ] Commit code/documentation with both required trailers.

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
