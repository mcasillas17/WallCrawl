# Multi-week Programs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Ship optional completion-driven four-week programs through the existing deterministic planner, durable lifecycle, recovery and immutable history.

**Architecture:** One owned latest enrollment per profile and frozen associations per program session; reconstruct progress from bounded association/header reads. Reuse the single-session generation and validation pipeline, with explicit planning intent and transactional revision checks. Room 15 and archive 5 are additive to the existing contracts.

**Tech Stack:** Kotlin, Room, Compose, coroutines/Flow, existing JVM and Android tests; no new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-25-multi-week-programs-design.md`, approved by the user on 2026-10-26.

## Global Constraints

- Four weeks, two blocks of two weeks, captured primary goal/goals/frequency; frequency 2–6, at most 24 slots.
- Only explicit program starts associate. Confirmed Finish advances once; cancellation deletes the active session/association without advancing.
- No read, regeneration, calendar change or restart of the app advances progress.
- Program actions require no active workout. Restart/replacement creates a new UUID without rewriting history.
- Preserve PROGRAM_STATE_V2, PRIMARY_ONLY_V1, one-variable progression, accepted one-workout deload, advanced ceiling and all eligibility/validation rules.
- No metadata/provenance changes: 182 AI_ACCEPTED, zero APPROVED, empty joint clearances.
- English and neutral Latin American Spanish, 320 dp and font scale 1.8, accessible controls and active-session resume independent of program failures.
- Room 14→15; archive 5 reads 1–5 with unchanged older canonical checksums. Unknown/corrupt program data never becomes an empty/default program.
- Use the existing local-data gate and transactions; no gate across document I/O; bounded reads and no polling.
- Keep scratch, logs, task briefs, review artifacts and execution ledger outside the repository in `/tmp/wallcrawl-package12-4785` (user requirement overrides SDD scratch location).
- No commit/push until the required Knights current final panel authorizes publication; no force push, merge, tag, release or deployment.

## Review Focus

- Lost association must fail archive validation: a nullable recommendation digest binds membership in both directions (Tasks 2/4).
- Profile REPLACE must not cascade owned enrollment away; delete/write races must not recreate it (Task 2).
- Ordinary mode must remain usable with unsupported program configuration; corrupt identity/DB reads must still surface errors (Tasks 2/3/5).
- Partial Finish counts as a finished slot but never manufactures completed sets or progression evidence (Tasks 2/3/5).
- Stale UI state restored after replacement must neither target the new run nor relabel an old recommendation (Tasks 3/5).

## File map

Paths below are relative to `app/src/main/java/wallcrawl/elopenmike/com/` unless prefixed otherwise. Tests mirror package paths in `app/src/test/java` and `app/src/androidTest/java`.

- `core/model/MultiWeekProgram.kt`, `core/ai/MultiWeekProgramPolicy.kt`: supported domain values, explicit failures, deterministic transitions and bounded reconstruction/digests.
- `core/database/entity/ProgramEntities.kt`, `dao/ProgramDao.kt`, `repository/ProgramRepository.kt`, `repository/ProgramMapper.kt`: owned program reads/actions and frozen association persistence.
- `core/database/dao/Daos.kt`, `repository/WorkoutRepository.kt`, `WallCrawlDatabase.kt`: atomic starts/completion/cancellation and migration.
- `core/model/RecommendationRecord.kt`, recommendation entity/mapper, `core/ai/RecommendationSnapshot.kt`: membership binding and start snapshot.
- Existing generation context/builder/identity, planner rotation, validator, Today lifecycle: explicit program intent through normal validation.
- `core/backup/LocalDataArchive.kt`, codec and existing backup DAO/repository: versioned ownership and relationship validation.
- `feature/program/`, Today, Progress, history, active workout, app navigation and `WallCrawlApplication.kt`: focused management and production wiring.
- Paired strings and materially affected documentation named in the spec.

## Task 1: Pure domain, lifecycle and reconstruction

**Files:** create the model/policy files above and `MultiWeekProgramPolicyTest.kt`.

**Interfaces:** produce `ProgramConfiguration`, `ProgramEnrollment`, `ProgramStatus`, `ProgramAction`, `ProgramPosition`, `ProgramSessionSnapshot`, `ProgramAssociation`, `ProgramSlotSession`, `ProgramProgress`, typed `ProgramFailure`/exception and `ProgramHeader`. Use version strings, not ordinal storage. Pure policy exposes configuration-from-profile, action transition, prefix reconstruction, supported-policy validation, profile-match validation and canonical association digest. Header accepts identity/revision plus raw storage fingerprint without interpreting policy. Exact constructor details are builder-owned and must be recorded in the task report for later consumers.

- [x] Write failing tests for 2/3/6-session weeks; week/block/final positions; all action/status pairs; revision/profile/run guards; unsupported frequency/version; repeated actions and exhausted revisions.
- [x] Run `./gradlew testDebugUnitTest --tests '*MultiWeekProgramPolicyTest' --no-daemon`; record RED before implementation.
- [x] Implement pure transitions and exact completed-prefix/optional-active-slot validation. Use no clock reads; accept explicit timestamps, IDs and snapshots. Restart/replacement increments revision across new IDs; config equality compares canonical goals.
- [x] Add tests for zero/partial completed session progress, cancellation-equivalent removal, duplicate/gap/mismatched snapshots, timestamp rollback independence, digest framing and no input mutation.
- [x] Run focused tests and the JVM suite; record GREEN and interface inventory. Leave changes uncommitted for review.

## Task 2: Room lifecycle, atomic session writes and history membership

**Files:** new entity/DAO/repository/mapper; database; existing session DAO/repository; recommendation model/entity/mapper/snapshot; `WorkoutHistoryRepository`/history model; Room migration and persistence tests, affected JVM repository tests.

**Consumes:** Task 1 types/policy. **Produces:** `ProgramRepository` with header read, coherent bounded progress read/observation, revision-guarded actions; optional association on history detail; nullable `programAssociationDigest` on records; start snapshot carrying expected header and optional program session snapshot. Repository actions include expected profile revision/run/header and never bootstrap profiles.

- [x] Add failing real Room tests for migration 14→15, empty legacy tables/null digest, profile save preserving enrollment, no-active action guard and stale/repeated action refusal.
- [x] Extend `insertWorkoutUnlessActive` tests for atomic program start, ordinary header freshness, existing-active no-op, injected insert failure rollback and deload consumption.
- [x] Implement the two tables and nullable digest column, all migration registrations, bounded projections and local-data-gated management operations. Keep raw header decoding independent of program config.
- [x] Extend Finish transaction to idempotent completed-summary reads and exactly-once revision/final status; cancel session/association atomically with revision. Known V1 completion ignores later profile mismatch; unsupported Finish fails without writes; structurally valid cancellation works without interpreting schedule.
- [x] Add failure-rollback, partial/zero-set Finish, cancel/retry, restart immutable-history, malformed/unknown identity/version, disk reopen and delete-race tests. Ensure direct existing DAO callers cannot bypass association integrity.
- [x] Run focused JVM/Room tests and migration chain on the task-owned emulator; record actual results. Leave uncommitted for review.

## Task 3: Normal planner integration, freshness and replay

**Files:** generation context/builder, `FakeWorkoutPlanner`, context identity/snapshot, `ProgramValidator`, applicable fixture/production/lifecycle JVM tests.

**Consumes:** program repository/header/progress and snapshot interfaces. **Produces:** `build` supports explicit ordinary/program intent while defaulting to existing ordinary behavior. Program input remains separate from derived `TrainingProgramState`; snapshot validates and carries exact consumed header/position.

- [x] Add failing tests for same inputs/same output, program rotation base from slot rather than lifetime count, no position advancement on regeneration and policy/config/header freshness.
- [x] Feed validated current program configuration/position through existing builder/pipeline; keep all single-session rules/repair. Add program structural invariants by reuse of Task 1 policy.
- [x] Bump context identity v5, covering intent/header/configuration/version/position; ordinary mode reads header only. Populate recommendation membership snapshot only for explicit program intent.
- [x] Add multi-week replay with real bundled accepted production catalog, typed unsupported personas, calendar-boundary/DST/zone/rollback cases and no ledger credit/reset/catch-up/advanced bypass.
- [x] Verify progression/deload both inside/outside program, live constraint/equipment/unit changes, captured goal/frequency mismatch and unchanged ordinary/manual behavior.
- [x] Run focused existing and new planner suites, then JVM suite; record evidence. Leave uncommitted for review.

## Task 4: Archive 5, restore and deletion

**Files:** archive model/codec, local-data DAO/repository, archive and backup instrumentation tests, affected migration/local-data fixtures.

**Consumes:** enrollment/association entities, mapping and digest from Tasks 1/2. **Produces:** snapshot includes owned enrollment and all associations; v5 record serialization includes nullable digest; readers 1–4 unchanged.

- [x] Write failing format-5 round-trip and old-format checksum tests. Test missing/extra association, wrong digest/run/config/prefix, active/paused/completed contradictions, unsupported program policy and representation limits.
- [x] Add bounded strict serialization/deserialization, bidirectional record binding, coherent snapshot validation and unknown-program refusal before opening export destination.
- [x] Add DAO export/restore/delete table participation, dependency order and destination-empty checks; ensure local-data gate never covers document I/O.
- [x] Test real repository export/delete/restore, active reservation restoration, replaced-run history, old archives with no fabricated program, failure rollback and in-flight writes versus deletion.
- [x] Run archive/local-data/migration suites and relevant JVM checks; record results. Leave uncommitted for review.

## Task 5: Production navigation and localized program experience

**Files:** `feature/program/ProgramViewModel.kt`, `ProgramScreen.kt`, `ProgramUiState.kt`, Today state/VM/screen, Progress screen, history presentation/screen, active Finish confirmation, app routes/NavHost, application container, English/Spanish resources and corresponding tests.

**Consumes:** program repository and explicit planner intent. **Produces:** actual application-owned repository wiring; optional program/ordinary Today intent; Program management from Today/Progress; immutable association detail.

- [x] Add failing ViewModel/navigation tests for start/pause/resume/restart/replace/completion, errors/retry, explicit ordinary/program choice and active-resume precedence.
- [x] Wire production dependencies and lifecycle-aware observation. Publish plan/unit/snapshot/program facts coherently; preserve/reset saved variation per run/slot as specified. Clear stale protected navigation on local-data reset.
- [x] Build scrollable management/current-program summaries, bounded completed-slot history links, confirmations and captured-profile mismatch actions. Program failures must not hide manual workflows or active resume.
- [x] Add program-aware partial Finish explanation and unsupported Finish error, without changing frozen prescriptions or inferred completion semantics.
- [x] Add paired resources, semantics, 320 dp/1.8 tests, locale invariance, saved-state/process recreation and stale confirmation tests. Capture actual task-owned emulator screens for materially affected docs.
- [x] Run focused JVM/instrumentation/navigation suites and document actual process-death checks/device identity. Leave uncommitted for review.

## Task 6: Final evidence, documentation and PR delivery

**Files:** ROADMAP, README, architecture, weekly-dose-ledger, privacy, localization, custom-workouts, planner-evaluation, history design supersession note, this plan/design status and purposeful screenshots.

- [x] Audit approved spec coverage against implementation and tests; close all gaps without unrelated features.
- [x] Obtain converged full Knights implementation review (astra/sol/luna, all six dimensions); fix findings and rerun affected checks/full panel as required.
- [x] Update affected documentation with actual behavior, versions, recovery/unknown-data boundaries and test limits; mark Package 12 complete only with evidence.
- [x] Run requested importer/release Python suites, acceptance audit, pinned catalog check, `./gradlew test lint assembleDebug --stacktrace --no-daemon`, `./gradlew connectedDebugAndroidTest --stacktrace --no-daemon`, and `git diff --check` with documented JDK/SDK and task-owned API 36 device. Store exact logs outside Git.
- [x] Obtain separate fresh full Knights final review and machine `publicationReady`. No reviewer output is human metadata approval.

**Publication handoff:** Re-evaluate the final snapshot before staging. Create the dedicated
task branch, stage only task-owned files, commit reviewed content, verify hooks, push without
force, and open a non-draft PR against verified main. Attach it using Codex tools and return
its URL. The PR records the publication result; do not merge, release or deploy.

## Validation record — 2026-10-26

The complete implementation and material documentation passed these local gates:

| Command | Actual result |
| --- | --- |
| `python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v` | 156 tests, OK |
| `python3 -m unittest discover -s tools/release -p 'test_*.py' -v` | 6 tests, OK |
| `python3 tools/workout-guide/verify_ai_acceptance_audit.py` | Exit 0; 182 accepted IDs, existing audit/provenance preserved |
| `python3 tools/workout-guide/check_pinned_catalog.py` | Exit 0; pinned catalog unchanged, 302 exercises / 906 frames |
| `./gradlew test lint assembleDebug --stacktrace --no-daemon --rerun-tasks --no-build-cache` | BUILD SUCCESSFUL; all 56 tasks executed; 1,126 JVM tests across 102 suites, no failures/errors/skips; lint has no errors |
| `./gradlew connectedDebugAndroidTest --stacktrace --no-daemon` | BUILD SUCCESSFUL; 473 tests, no failures/errors/skips |
| `git diff --check` and focused documentation consistency checks | Exit 0; new links, screenshot references, suite names and paired program resource formats verified |

Environment: OpenJDK 17.0.20.1, Python 3.14.7, the documented Android SDK and a
task-owned Pixel 7 API 36 Google APIs arm64 emulator (`WallCrawl_Package12_4785`,
`emulator-5556`). Fingerprint:
`google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`.
Normal device settings were restored to 1080×2400, density 420 and font scale 1.0.
Large-text checks also used actual emulator 320 dp/font 1.8 settings for native dialogs;
ordinary Compose content-wrapper checks are identified separately in localization docs.

Lint reported 30 warnings and one hint, including dependency/target/asset notices,
existing test API warnings and the test-compatible `SavedStateHandle` constructor
default; no warning suppression or dependency upgrade was added. Production Today
receives the navigation entry's real saved-state handle. Warnings are not represented
as a warning-free build. Physical-device and manual TalkBack certification were not run.

Independent implementation review converged in round 2 after fixing an obsolete
generation failure cache race and large-text Progress error recovery. Each of the
three configured reviewers covered correctness, tests, security, documentation,
architecture and performance. Final documented-state review converged in round 4 after
clarifying README workout counts for zero-set finishes. Status bookkeeping receives a fresh
full panel and publication rechecks the current snapshot before staging. No review confers
human exercise-metadata approval. Detailed logs, XML, review
records and intermediate captures are retained outside the repository.

## Execution rulings

The user's approval authorizes implementation of the complete design; no second approval request is needed for this mechanical plan. Execute sequential task implementers/reviewers using Codex-compatible delegation. Adapt SDD's commit-range review packages to current working-tree snapshots because Knights requires review before commit. All reviews and final gates remain required; no phase completion is inferred from this plan.
