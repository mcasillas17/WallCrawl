# Capability-Aware Profile Inputs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collect, validate, and persist seven local movement-capability answers during onboarding and in Profile without changing workout recommendations.

**Architecture:** Add a normalized capability value object to `UserProfile`, encode it as a bounded allowlisted flat JSON object in one additive Room column, and decode corrupt persisted input to all `UNKNOWN`. Onboarding keeps explicit answered state in a `SavedStateHandle`; Profile edits a separate draft and commits the whole current profile through the existing atomic repository save.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room, Coroutines/StateFlow, SavedStateHandle, JUnit 4, Truth, AndroidX Compose UI tests, Android instrumentation.

**Spec:** `docs/superpowers/specs/2026-08-29-body-aware-personalization-design.md`, narrowed by the capability-only milestone requirements in this task.

## Global Constraints

- The only profile inputs added are `IMPACT`, `FLOOR_TRANSITION`, `UNSUPPORTED_SQUAT`, `UPPER_BODY_BODYWEIGHT_PUSH`, `VERTICAL_PULL_OR_HANG`, `BALANCE_WITHOUT_SUPPORT`, and `CONTINUOUS_ACTIVITY`.
- The only levels are `UNKNOWN`, `COMFORTABLE`, `LIMITED`, and `AVOID`; missing or corrupt stored input always resolves to `UNKNOWN`.
- Weight, height, BMI, body composition, age, exercise-demand metadata, eligibility, ranking, substitutions, progression, deload, Health Connect, Wear, analytics, accounts, cloud, network, and model integration are out of scope for this change.
- Planner output must remain invariant when only movement capabilities differ.
- All capability data stays in the existing local Room/profile flow and never enters logs, exceptions, analytics, snapshots, or network/model interfaces.
- The database migration is additive from the actual current version 7 to 8 and every supported 1→8 chain remains registered without destructive fallback.
- Fresh onboarding requires an explicit answer for all seven capabilities; an explicit “Not sure” persists as `UNKNOWN` and remains distinct from unanswered draft state.

---

### Task 1: Normalized capability domain model

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/BodyContext.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/UserProfile.kt`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/model/BodyContextTest.kt`

**Interfaces:**
- Produces: `MovementCapabilityType`, `CapabilityLevel`, and `MovementCapabilities.from(Map)` / `unknown()` / indexed lookup.
- Produces: `UserProfile.movementCapabilities` with an all-`UNKNOWN` default.

- [x] Write a failing `BodyContextTest` proving every known key is present, a partial input normalizes missing keys to `UNKNOWN`, the input map is defensively copied, and `UserProfile()` defaults to all `UNKNOWN`.
- [x] Run `./gradlew testDebugUnitTest --tests '*BodyContextTest' --no-daemon` and confirm compilation fails because the types do not exist.
- [x] Implement the enum names verbatim and a normalized immutable value object whose lookup can never treat absence as favorable.
- [x] Add the conservative `UserProfile` default.
- [x] Re-run the focused test and commit `feat: add movement capability domain model`.

### Task 2: Bounded capability codec and repository mapping

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/MovementCapabilitiesCodec.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/UserProfileRepository.kt`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/database/repository/MovementCapabilitiesCodecTest.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/database/repository/UserProfileRepositoryTest.kt`

**Interfaces:**
- Produces: `MovementCapabilitiesCodec.encode(MovementCapabilities): String` and `decode(String): MovementCapabilities`.
- Produces: `UserProfileEntity.movementCapabilitiesJson: String`.

- [x] Write failing codec tests for complete round-trip, partial maps, malformed JSON, nested/non-string values, oversized input, unknown keys, unknown values, duplicate keys, and deterministic allowlisted output.
- [x] Run the codec test and confirm it fails because the codec is absent.
- [x] Implement a maximum-4096-character flat JSON object parser that catches only its own expected decode exception; make every corrupt case return all `UNKNOWN` without including payloads in errors.
- [x] Write failing repository tests proving round-trip, fresh-repository reload, unrelated-field preservation, and one revision increment.
- [x] Map the entity column through the codec and validate the normalized in-memory field before the existing atomic save.
- [x] Re-run focused codec/repository tests and commit `feat: persist movement capabilities locally`.

### Task 3: Room schema 8 and migration compatibility

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Create: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration7To8Test.kt`
- Create: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/MigrationChainTo8Test.kt`
- Modify: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration3To4Test.kt`
- Modify: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration4To5Test.kt`
- Modify: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration5To6Test.kt`
- Modify: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration6To7Test.kt`

**Interfaces:**
- Produces: `WallCrawlDatabase.MIGRATION_7_8` and schema version 8.

- [x] Write a failing real-version-7 migration test that seeds every profile field plus template/session/set history, migrates, verifies all values remain unchanged, verifies capability JSON resolves to all `UNKNOWN`, and runs `PRAGMA foreign_key_check`.
- [x] Write failing chain tests that open manual schemas at versions 1 through 7 with the applicable full migration chain and verify schema 8 is reached.
- [x] Bump Room to 8, add one `TEXT NOT NULL DEFAULT '{}'` profile column, register 7→8 in production and all existing test chains, and keep destructive fallback absent.
- [x] Add a disk-backed repository close/reopen test proving capability reload after database recreation.
- [x] Run the focused migration device tests, verify nonzero execution, and commit `feat: migrate profiles to capability schema`.

### Task 4: SavedState-backed onboarding step and accessible controls

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingUiState.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ui/components/MovementCapabilityControls.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/feature/onboarding/OnboardingViewModelTest.kt`
- Create: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/ui/components/MovementCapabilityControlsTest.kt`

**Interfaces:**
- Produces: `OnboardingStep.MOVEMENT_CAPABILITY` directly after `EXPERIENCE_UNIT`.
- Produces: nullable/partial onboarding answer map, plus `updateMovementCapability(type, level)`.

- [x] Write failing ViewModel tests for completion gating, explicit `UNKNOWN`, back/forward retention, and SavedState recreation preserving answered keys without converting unanswered keys to `UNKNOWN`.
- [x] Run the focused ViewModel test and confirm the expected failures.
- [x] Add saved-state serialization using allowlisted enum names, validate the first unanswered capability, and include normalized capability data in the existing one-save onboarding completion.
- [x] Build a scrollable resource-backed step and shared radio-like controls with capability label, description, option label, selected state, minimum touch target, and deterministic focus order semantics.
- [x] Add an instrumentation semantics test that selects explicit “Not sure” and observes its selected state.
- [x] Run focused JVM and connected UI tests and commit `feat: collect movement capabilities in onboarding`.

### Task 5: Atomic Profile editor and planner boundary

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/ProfileUiState.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/ProfileViewModel.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/ProfileScreen.kt`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/feature/profile/ProfileViewModelTest.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt`

**Interfaces:**
- Produces: Profile edit/start/update/cancel/save actions backed by a separate draft.
- Preserves: the existing `WorkoutGenerationContext.userProfile` single source of truth with no planner changes.

- [x] Write failing Profile tests proving persisted values load, edits remain draft-only, cancel does not save, save performs one whole-profile write, unrelated fields survive, and revision semantics remain repository-owned.
- [x] Run the focused Profile test and confirm it fails because editing is absent.
- [x] Implement draft editing, static non-medical copy, BackHandler cancellation, and one atomic save of the newest current profile plus the draft capability value.
- [x] Add a planner regression test using two fresh planner instances and otherwise-identical contexts with opposite capability values.
- [x] Preserve identical workout output without adding any planner branch.
- [x] Run Profile/planner tests and commit `feat: edit movement capabilities from profile`.

### Task 6: Documentation, verification, review convergence, and PR

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/superpowers/specs/2026-08-29-body-aware-personalization-design.md`
- Modify: `docs/superpowers/plans/2026-08-29-body-aware-personalization.md`
- Modify only if needed for consistency: `docs/superpowers/specs/2026-08-29-science-based-workout-engines-design.md`
- Modify only if needed for consistency: `docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md`

- [x] Rewrite the merged body-aware documents so this milestone is capability-only and mark measurements, metadata, eligibility/ranking, substitutions, history evidence, progression, and deload explicitly out of scope.
- [x] Document seven fields, four answers, local Room flow, fresh onboarding, existing-user migration, existing backup inclusion, absent export/delete subsystem as an out-of-scope roadmap item, unchanged planner behavior, and the next reviewed-metadata/eligibility milestone.
- [ ] Run focused suites, full JVM tests, lint/assemble, connected tests, `git diff --check`, migration-chain checks, intended-file review, secret/debug/generated-artifact scans, and test-result counting.
- [ ] Compare the implementation with the repository’s existing profile JSON codecs and existing transactional profile save, remove experiment residue, re-run the full evidence suite, and commit all final changes.
- [ ] Dispatch exactly two read-only reviewers—`gpt-5.6-terra` and `gpt-5.6-luna`—against the same full `origin/main...HEAD` packet; fix valid findings with tests and repeat both reviews until both return `APPROVED` for one identical SHA.
- [ ] After dual approval, re-read the entire diff, push `capability-aware-inputs`, open a non-draft PR to `main`, and verify its state, base, head, files, and final SHA.
