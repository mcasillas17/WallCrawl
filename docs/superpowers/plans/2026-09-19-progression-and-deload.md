# Progression and Deload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` or
> `subagent-driven-development` to implement this plan task-by-task. Every
> implementer must independently load `using-superpowers`, `ponytail`, and
> `test-driven-development`. Knights owns the independent review/publication gates.

**Goal:** Ship Package 9 through the actual production planner, local persistence,
Today controls, and immutable recommendation records.

**Architecture:** Reuse the shared factory, eligibility policy, state guidance,
validator and Room start transaction. Pure progression and deload contracts feed
those seams; only user choices require new durable state. Evidence is bounded
and reconstructed, never a mutable training counter.

**Tech Stack:** Kotlin, Compose, Room, coroutines/Flow, JUnit, Android instrumentation,
the existing strict JSON archive codec, Gradle and Python standard-library gates.

**Design:** `docs/superpowers/specs/2026-09-19-progression-and-deload-design.md`.
The user delegated unavailable design decisions to autonomous execution. No human
exercise-metadata sign-off is supplied.

---

## Execution status

Implementation, production wiring and validation are complete at the
implementation-review checkpoint. Round-one findings were repaired; round-two findings received a
mixed-unit comparison repair, removal of an unused hold reason, and clearer
domain-only distance documentation. The user explicitly authorized resuming
after the review-output gate blocked the previous attempt. The fresh complete
round-three implementation panel converged with no findings from any of the
four configured models. The separate final documented-state panel and publication
outcome are recorded with the PR, so publishing does not modify its reviewed
snapshot merely to tick a checklist. No release or merge is authorized.

## Workspace and validation environment

The existing native task worktree is used, on
`mcasillas17-progression-and-deload`; fetched main is `df4c376`. No starting
changes existed. Do not create another checkout, alter catalog acceptance,
upgrade dependencies, or publish before Knights' final gate.

Use:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
./gradlew testDebugUnitTest --tests '*Progression*Test' --no-daemon
```

Baseline `test lint assembleDebug` passed. The 156 importer and six release tests
passed. The audit passed. The pinned check initially failed because the
sanitized environment could not find the app-bundled Git HTTPS helper, not
because of catalog drift. The unchanged checker passes with native Git:

```bash
PATH=/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin \
/opt/homebrew/bin/python3 tools/workout-guide/check_pinned_catalog.py
```

## 1. Typed choices and persistence (independent ownership)

**Files:** `core/model/Deload.kt`, `core/ai/DeloadOfferPolicy.kt`,
`core/database/entity/DeloadPreferencesEntity.kt`,
`core/database/dao/DeloadPreferencesDao.kt`,
`core/database/repository/DeloadRepository.kt`,
`core/database/WallCrawlDatabase.kt`, `core/database/dao/Daos.kt`,
`core/database/dao/LocalDataBackupDao.kt`, `core/database/repository/WorkoutRepository.kt`,
`core/database/repository/LocalDataBackupRepository.kt`,
`core/backup/LocalDataArchive.kt`, `core/backup/LocalDataArchiveCodec.kt`,
and their JVM/Android test counterparts. Paths are relative to
`app/src/main/java/wallcrawl/elopenmike/com/`.

- [x] Add failing lifecycle and codec tests before production code.
- [x] Define the shared contracts below, with constructor validation and immutable values.

```kotlin
enum class DeloadSource { EXPLICIT_REQUEST, RETURNING }
enum class DeloadChoiceStatus { OFFERED, ACCEPTED, DECLINED, DISMISSED, CANCELLED, CONSUMED }
enum class DeloadAction { REQUEST, ACCEPT, DECLINE, DISMISS, CANCEL }
data class DeloadOffer(val id: String, val source: DeloadSource, val policyVersion: String)
data class DeloadChoice(
    val offer: DeloadOffer,
    val status: DeloadChoiceStatus,
    val decidedAtEpochMillis: Long,
    val sessionId: String? = null
)
data class DeloadPreferences(
    val profileId: String,
    val revision: Long = 0,
    val choice: DeloadChoice? = null,
    val lastHandledReturnKey: String? = null
)
interface DeloadRepository {
    fun observe(): kotlinx.coroutines.flow.Flow<DeloadPreferences>
    suspend fun get(): DeloadPreferences
    suspend fun decide(
        action: DeloadAction,
        expectedProfileRevision: Long,
        expectedDecisionRevision: Long,
        offerId: String?
    )
}
```

- [x] `DeloadOfferPolicy.offer(profile, preferences)` derives an unanswered returning
  offer or returns a persisted explicit request. `accepted(preferences)` returns
  only an accepted, unconsumed choice. Define stable `VERSION` and return-key
  construction in this policy.
- [x] Implement gated, revision-checked writes; reject stale offer IDs, invalid
  transitions, missing/unonboarded profile and active workouts explicitly.
- [x] Add schema 13 -> 14 and archive 4, retaining formats 1..3. Add preferences to
  transactional export, empty-destination checks, restore and delete-all.
- [x] Extend automatic start with expected decision revision (including the
  no-choice revision zero) and accepted offer ID. Check and consume atomically
  with the session/record; templates neither consume nor require the choice.
- [x] Add bounded repository reads:

```kotlin
suspend fun getRecentSessions(limit: Int = 8): List<WorkoutSession>
suspend fun getRecommendationRecords(sessionIds: List<String>): List<RecommendationRecord>
```

- [x] Update test-only repository implementations explicitly; no production empty
  fallback. Run archive, repository and migration tests. Assert a stale choice
  creates neither session nor record; duplicate start consumes no second choice.

## 2. Comparable outcomes and single-axis policy

**Files:** `core/model/Progression.kt`, `core/ai/ProgressionEngine.kt`,
`core/ai/DefaultExercisePrescriptionFactory.kt`,
`core/model/ExercisePrescription.kt`, `core/model/Workout.kt`,
`core/model/WorkoutGenerationContext.kt`, `core/ai/FakeWorkoutPlanner.kt`;
tests `ProgressionEngineTest`, `DefaultExercisePrescriptionFactoryTest`.

- [x] Write a parameterized shape matrix using genuine `WorkoutSession`,
  `WorkoutExercise`, and `WorkoutSet` snapshots. For every positive case assert:

```kotlin
assertEquals(expectedAxis, decision.axis)
assertEquals(expectedPrescription, decision.prescription)
assertEquals(reference, decision.prescription.withAxisFrom(reference, expectedAxis))
assertEquals(decision, engine.evaluate(exerciseId, reference, sessions, unit, now, digest))
```

- [x] Run the selected test and observe the missing policy failure.
- [x] Implement bounded exact-ID comparison, normalized units, complete targets and
  measurements, duplicate protection, timestamps, effort/manageable requirements,
  latest-attempt barriers and explicit hold reasons from the design.
- [x] Use a `ProgressionDecision` containing exercise ID, policy version, nullable
  axis, hold/advance reason, reference/result prescriptions, at most two source
  IDs and the base digest. Use `ProgressionAxis` values `LOAD`, `REP_RANGE`,
  `ASSISTANCE`, `DURATION`, `DISTANCE`.
- [x] Expose `DefaultExercisePrescriptionFactory.createDecision(exercise, context)`;
  `create` returns its prescription. The reviewed path uses the new engine once,
  the manual/disabled path retains the isolated legacy behavior.
- [x] Carry compatible prior targets through recorded base digests, preserving null
  starting numbers. Store per-exercise decisions on `GeneratedWorkout`.
- [x] Add negative controls for every missing measurement/feedback field, changed
  targets, stopped/partial/warm-up-only work, mixed units, bounds and duplicates.
  Domain-test all distance shapes without making them automatic candidates.
- [x] Prove accepted deload and returning guidance suppress progression; no old
  bump can stack. Run the policy/factory suites.

## 3. State, context, freshness and validation integration

**Files:** `core/ai/AdaptationStatePolicy.kt`, `ExerciseEligibilityPolicy.kt`,
`TrainingProgramStateProvider.kt`, `StateBasedTrainingPolicy.kt`,
`WorkoutGenerationContextBuilder.kt`, `RecommendationContextIdentity.kt`,
`ProgramValidator.kt`, `ProgramViolation.kt`, `RecommendationSnapshot.kt`,
`core/model/TrainingProgramState.kt`, `RecommendationRecord.kt`,
`ProgressionReasonCode.kt`, `WallCrawlApplication.kt`.

- [x] Add RED tests for accepted `HOLD`, unaccepted neutrality, returning precedence,
  and the advanced ceiling in all declared states.
- [x] Add V2 state/training policy versions with unchanged conservative allowances
  for all newly derivable states. Do not derive `DELOAD_OFFERED`.
- [x] Inject `DeloadRepository` through real application composition. The builder
  reads choices and richer recent history once, plus one bounded record batch.
- [x] Extend context identity with all consumed values and policies, including
  normalized continuation provenance and future-evidence classification.
- [x] Replace reviewed provenance's fixed-increment allowlist with exact shared
  policy results; keep load and assistance separate.
- [x] Let the one existing repair reduce sets. For each affected advanced
  prescription, restore its progression axis and replace the decision with
  `HOLD_VALIDATION_REPAIR`, then recompute duration and fully revalidate.
- [x] Encode bounded progression/deload provenance through the existing versioned
  reason-code channel. Validate structured groups and update the calculated token
  ceiling; old records remain readable without fabricated decisions.
- [x] Extend real `ProductionPlannerCompositionTest`, identity, aggregate dose,
  repair, state/provider and fixture suites, using the actual accepted cohort.

## 4. Today controls and lifecycle

**Files:** `feature/today/TodayViewModel.kt`, `TodayScreen.kt`, `TodayUiState.kt`,
`app/WallCrawlApp.kt`, `core/ui/localization/GeneratedWorkoutText.kt`,
`res/values/strings.xml`, `res/values-es/strings.xml`; associated Today JVM/Android
tests. Persistence/domain contracts from tasks 1-3 are prerequisites.

- [x] Add failing request/accept/decline/dismiss/cancel lifecycle tests with a
  revisioned in-memory test repository.
- [x] Observe choice changes, invalidate a displayed recommendation, and reject
  stale starts. Expose write failures without hiding an active session.
- [x] Add the Today deload card and explicit request entry point using existing
  card/button components. Preview one fewer set, minimum one, held reference
  targets and paused pending progression for one automatic workout.
- [x] Show typed progression/hold explanations with localized values and names.
- [x] Preserve the active workout and templates; no action mutates frozen targets.
- [x] Translate every new resource into neutral Latin American Spanish, preserve
  semantic state/names and large-text wrapping, and run resource parity.
- [x] Extend `TodayProductionLifecycleTest` against actual accepted content for
  choice/history/feedback/unit/clock changes and no-partial stale starts.

## 5. Android and full acceptance

- [x] Use a dedicated API 36 emulator and record its AVD, serial, API and ABI.
  Do not reset or erase a shared device.
- [x] Run connected Room/archive/migration tests and new Today UI tests.
- [x] Capture actual English, Spanish, large-text, accepted-restored and
  declined-restored UI evidence. Keep review/run logs outside the repository;
  materially useful README screenshots may be committed with captions.
- [x] Run the exact full gates requested by the user:

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
python3 tools/workout-guide/verify_ai_acceptance_audit.py
python3 tools/workout-guide/check_pinned_catalog.py
./gradlew test lint assembleDebug --stacktrace --no-daemon
./gradlew connectedDebugAndroidTest --stacktrace --no-daemon
git diff --check
```

Use the documented environment above; native Git is required for the sanitized
pinned checker in this host. Diagnose regressions, do not weaken checks.

## 6. Knights implementation convergence, docs and publication

- [x] Load `requesting-code-review`, snapshot the complete worktree and dispatch
  all four configured standalone reviewers independently, each loading
  `ponytail-review`: grok-4.6, gemini-3.8-flash, gpt-6-astra, claude-opus-4.8.
- [x] Preserve exact JSON results outside the repo and evaluate with the installed
  runtime. Fix actionable findings using `receiving-code-review`; after each
  edit rerun affected checks and a fresh full panel. No round cap or fallback is
  configured.
- [x] After implementation convergence, update ROADMAP Package 9/open decisions,
  README and affected architecture, eligibility, timed programming, evaluation,
  privacy and localization statements. Correct relevant historical Task 6/design
  status without claiming Package 3 or human review is complete.
Publication requirements (their execution outcome belongs in the PR):

- Run final checks and a separate fresh full panel with phase `final`.
- Re-evaluate `publicationReady` immediately before staging only owned files.
  Commit normally with the required Copilot App trailer, verify hooks did not
  change reviewed content, push without force, and use the native non-draft PR
  tool with the installed Knights template.
- Return the PR URL, actual validation/reviewer evidence and limitations.
  No merge, tag, release, or deployment.
