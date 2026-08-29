# WallCrawl Local Health and Wear OS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional Health Connect export and a phone-planned, watch-executed Wear OS companion that logs sets, runs timers, displays bounded exercise animation, and works after the phone disconnects without weakening WallCrawl's local-first guarantees.

**Architecture:** The phone remains the canonical planner and Room datastore. A Wear OS app executes a frozen validated session from a local cache, journals user events before acknowledging them, and reconciles them idempotently with the phone through the Wear Data Layer; Health Connect is an optional phone-side export, while Health Services is an optional watch-side sensor source.

**Tech Stack:** Kotlin, Android multi-module Gradle, Jetpack Compose and Compose for Wear OS, Room/DataStore, Coroutines/Flow, Google Play services Wearable Data Layer, Ongoing Activity/notifications, Health Connect, optional Wear Health Services, Coil SVG or benchmark-selected watch image format.

---

## Dependency Versions Verified on 2026-08-28

Add these aliases in `gradle/libs.versions.toml` during the corresponding tasks:

```toml
healthConnect = "1.1.0"
wearCompose = "1.6.2"
wearCore = "1.4.0"
wearOngoing = "1.1.0"
playServicesWearable = "20.0.1"
healthServices = "1.0.0"
```

Use Health Connect 1.1.0 and Health Services 1.0.0 because they are stable. The richer `ExerciseSegment` repetitions, weight, set index, and RPE fields first appeared in Health Connect 1.2.0 alpha; do not make production export depend on those alpha APIs. Re-check official release notes before an execution that occurs after this plan date, and update the plan in a reviewed dependency-only commit rather than silently changing versions during feature work.

## Local-First Requirements

1. Core phone planning, logging, history, and progression work in airplane mode with no account.
2. The watch never runs an LLM and never generates an unvalidated plan.
3. Once a validated plan is mirrored, the watch can finish it without the phone nearby.
4. Every watch mutation is durable locally before the UI reports success.
5. Reconnection is idempotent: no missing or duplicated sets.
6. Health Connect and Health Services are optional; permission denial never blocks workouts.
7. Health Connect is not WallCrawl's source of truth and imported records are never written back as WallCrawl records.
8. Data Layer can route through Google infrastructure when Bluetooth is unavailable. Payloads are end-to-end encrypted by the platform, minimized, and disclosed; the product must not claim Bluetooth-only transfer.
9. Animation and sensors are never required to log a set or run a timer.
10. No synthetic calories, VO2 max, medical recovery score, or inferred RPE is written.

## Dependency on the Adaptive Coach Plan

Before the resilient Wear release, complete these tasks from `2026-08-28-adaptive-coach-product.md`:

- Task 2: remove unsafe starting loads.
- Task 5: whole-program validation.
- Task 6: full type-aware set logging, RPE/RIR, timestamps, rest timer, and finish guard.
- Task 7: validated substitutions.
- Task 11: local privacy and data controls.

Health Connect summary export and the Wear technical spike may begin earlier because each is independently testable.

## Target Module and File Map

```text
:core:model
  Pure Kotlin workout/profile/prescription models shared by phone and watch.

:companion-protocol
  Versioned PlanSnapshot, SessionEvent, SyncAck, codecs, reducers, and tests.

:app
  Phone UI, Room, planner/validator, CompanionCoordinator, Health Connect export.

:wear
  Wear Compose UI, watch event journal, timer service, Ongoing Activity,
  Data Layer adapter, animation cache, and optional Health Services capture.
```

**New phone files**

- `app/src/main/java/wallcrawl/elopenmike/com/core/health/HealthConnectGateway.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/health/HealthConnectRecordFactory.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/health/HealthExportRepository.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/wear/CompanionCoordinator.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/core/wear/PhoneWearDataLayer.kt`
- `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/HealthIntegrationScreen.kt`

**New shared protocol files**

- `companion-protocol/src/main/java/wallcrawl/elopenmike/com/companion/PlanSnapshot.kt`
- `companion-protocol/src/main/java/wallcrawl/elopenmike/com/companion/SessionEvent.kt`
- `companion-protocol/src/main/java/wallcrawl/elopenmike/com/companion/SyncAck.kt`
- `companion-protocol/src/main/java/wallcrawl/elopenmike/com/companion/SessionReducer.kt`

**New watch files**

- `wear/src/main/java/wallcrawl/elopenmike/com/wear/WallCrawlWearApp.kt`
- `wear/src/main/java/wallcrawl/elopenmike/com/wear/session/WatchSessionStore.kt`
- `wear/src/main/java/wallcrawl/elopenmike/com/wear/session/WatchEventJournal.kt`
- `wear/src/main/java/wallcrawl/elopenmike/com/wear/sync/WearDataLayerService.kt`
- `wear/src/main/java/wallcrawl/elopenmike/com/wear/timer/RestTimerEngine.kt`
- `wear/src/main/java/wallcrawl/elopenmike/com/wear/service/WorkoutForegroundService.kt`
- `wear/src/main/java/wallcrawl/elopenmike/com/wear/ui/ActiveSetScreen.kt`
- `wear/src/main/java/wallcrawl/elopenmike/com/wear/ui/RestTimerScreen.kt`
- `wear/src/main/java/wallcrawl/elopenmike/com/wear/ui/MovementGuideScreen.kt`
- `wear/src/main/java/wallcrawl/elopenmike/com/wear/visual/WatchVisualCache.kt`
- `wear/src/main/java/wallcrawl/elopenmike/com/wear/health/WearExerciseCapture.kt`

---

### Task 1: Establish Shared Modules Without Changing Behavior

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `core/model/build.gradle.kts`
- Create: `companion-protocol/build.gradle.kts`
- Move: `app/src/main/java/wallcrawl/elopenmike/com/core/model/*.kt` to `core/model/src/main/java/wallcrawl/elopenmike/com/core/model/`
- Modify: `app/build.gradle.kts`
- Test: existing JVM suite

- [ ] **Step 1: Add module compilation tests**

Create `core/model/src/test/java/wallcrawl/elopenmike/com/core/model/ExercisePrescriptionTest.kt` by moving the existing pure model tests. Verify the app depends on the shared module rather than duplicate source.

- [ ] **Step 2: Add modules**

```kotlin
// settings.gradle.kts
include(":app")
include(":core:model")
include(":companion-protocol")
include(":wear")
```

`:core:model` uses the Kotlin JVM plugin only. `:companion-protocol` depends on `:core:model`. `:app` and `:wear` depend on both.

- [ ] **Step 3: Move only dependency-free models**

Move domain files; do not move Room entities, Android resources, repositories, Compose, or asset providers. Preserve package names to avoid widespread import edits.

- [ ] **Step 4: Verify behavior is unchanged**

```bash
./gradlew :core:model:test :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS with no duplicate classes.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts gradle core companion-protocol app
git commit -m "refactor: extract shared workout models"
```

---

### Task 2: Add Opt-In Health Connect Session Export

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/health/HealthConnectGateway.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/health/HealthConnectRecordFactory.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/health/HealthExportRepository.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/HealthIntegrationScreen.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/HealthPermissionRationaleActivity.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/health/HealthConnectRecordFactoryTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/health/HealthExportRepositoryTest.kt`

- [ ] **Step 1: Pin Health Connect 1.1.0 and test the mapping abstraction**

Add `androidx.health.connect:connect-client:1.1.0`. The first production slice uses availability, permissions, `ExerciseSessionRecord`, metadata, and `insertRecords`.

```kotlin
enum class HealthAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

interface HealthConnectGateway {
    suspend fun availability(): HealthAvailability
    suspend fun grantedPermissions(): Set<String>
    suspend fun upsertSession(record: ExerciseSessionRecord)
}
```

- [ ] **Step 2: Write failing record tests**

```kotlin
@Test
fun completedWorkout_mapsToStrengthSessionInLocalZone() {
    val record = factory.sessionRecord(completedSession)
    assertThat(record.exerciseType)
        .isEqualTo(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING)
    assertThat(record.metadata.clientRecordId).isEqualTo(completedSession.id)
    assertThat(record.startTime).isBefore(record.endTime)
}
```

- [ ] **Step 3: Add minimum permissions and rationale**

Request only write access to exercise sessions for this slice. Add the Health Connect package query, permission-rationale activity/alias, and Play Console declaration documentation. Do not request heart rate, sleep, steps, nutrition, or background read.

- [ ] **Step 4: Make export explicit and idempotent**

Store `healthExportEnabled`, `lastExportedRevision`, and the returned record ID in a local settings repository. Use `clientRecordId = session.id` and a monotonically increasing `clientRecordVersion`. Export only after WallCrawl has completed and committed the session.

- [ ] **Step 5: Degrade without affecting the workout**

Unavailable provider, denied/revoked permission, or failed export becomes a visible retry state. It never rolls back Room completion and never blocks future planning.

- [ ] **Step 6: Verify and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*HealthConnect*' :app:lintDebug
git add gradle app
git commit -m "feat: export completed workouts to Health Connect"
```

---

### Task 3: Capture Segment-Quality Set Timing

**Files:**
- Modify: `core/model/src/main/java/wallcrawl/elopenmike/com/core/model/Workout.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/entity/Entities.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/dao/Daos.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/WallCrawlDatabase.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/database/repository/WorkoutRepository.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/database/Migration8To9Test.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/health/HealthConnectRecordFactoryTest.kt`

- [ ] **Step 1: Add failing timestamp tests**

Require completed sets to retain `startedAtTimestamp` when the user begins editing/performing and `completedAtTimestamp` when committed. Require `completedAt > startedAt`.

- [ ] **Step 2: Add migration 8 -> 9**

After the adaptive-coach plan's schema version 8, add nullable `startedAtTimestamp` and `completedAtTimestamp` to `workout_sets`. Existing records remain valid summary-only history.

- [ ] **Step 3: Update logging transaction**

Persist timestamps, repetitions, optional weight, set index, and RPE together. Undo clears `completedAtTimestamp` but retains an audit event in the companion protocol once Wear sync exists.

- [ ] **Step 4: Keep detailed sets in WallCrawl on stable Health Connect**

Health Connect 1.1.0 does not expose the richer set fields introduced in 1.2.0 alpha. Export only the completed `ExerciseSessionRecord` in production, retain set detail in Room, and use these timestamps for WallCrawl history and Wear reconciliation. Do not encode set detail into notes and do not add an alpha dependency.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*HealthConnectRecordFactoryTest' :app:connectedDebugAndroidTest
git add core app
git commit -m "feat: retain workout set timing"
```

---

### Task 4: Define the Versioned Companion Protocol

**Files:**
- Create: `companion-protocol/src/main/java/wallcrawl/elopenmike/com/companion/PlanSnapshot.kt`
- Create: `companion-protocol/src/main/java/wallcrawl/elopenmike/com/companion/SessionEvent.kt`
- Create: `companion-protocol/src/main/java/wallcrawl/elopenmike/com/companion/SyncAck.kt`
- Create: `companion-protocol/src/main/java/wallcrawl/elopenmike/com/companion/SessionReducer.kt`
- Test: `companion-protocol/src/test/java/wallcrawl/elopenmike/com/companion/SessionReducerTest.kt`

- [ ] **Step 1: Write protocol compatibility tests**

Test deterministic serialization, unknown future fields, unsupported major version, duplicate event UUID, reordered delivery, missing sequence, stale expected revision, and replay after process death.

- [ ] **Step 2: Define compact immutable snapshots**

```kotlin
data class PlanSnapshot(
    val protocolMajor: Int = 1,
    val protocolMinor: Int = 0,
    val sessionId: String,
    val canonicalRevision: Long,
    val catalogVersion: String,
    val policyVersion: Int,
    val exercises: List<WatchExerciseSnapshot>,
    val approvedSubstitutions: Map<String, List<String>>,
    val visualHashes: Map<String, List<String>>
)

data class WatchExerciseSnapshot(
    val exerciseId: String,
    val displayName: String,
    val orderIndex: Int,
    val prescription: ExercisePrescription,
    val previousPerformance: SetPerformanceSnapshot?,
    val conciseCues: List<String>
)

data class SetPerformanceSnapshot(
    val reps: Int?,
    val weight: Double?,
    val assistanceWeight: Double?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val rpe: Float?,
    val rir: Int?,
    val completedAtTimestamp: Long?
)
```

Include only the active session's IDs, display names, type-aware targets, previous values, rest duration, concise cues, and visual hashes. Exclude profile history, prompts, rationale, model output, and unrelated catalog records.

- [ ] **Step 3: Define append-only events**

```kotlin
sealed interface SessionEvent {
    val eventId: String
    val sessionId: String
    val originNodeId: String
    val sequence: Long
    val expectedRevision: Long
    val occurredAtWallTime: Long
}

data class SetCompleted(
    override val eventId: String,
    override val sessionId: String,
    override val originNodeId: String,
    override val sequence: Long,
    override val expectedRevision: Long,
    override val occurredAtWallTime: Long,
    val setId: String,
    val performance: SetPerformanceSnapshot
) : SessionEvent
```

Add `SetUndone`, `SetSkipped`, `ExerciseAdvanced`, `RestStarted`, `RestSkipped`, `SubstitutionSelected`, `WorkoutFinished`, and `WorkoutDiscardRequested`.

- [ ] **Step 4: Implement deterministic reduction**

The reducer applies unseen IDs in per-node sequence order, rejects gaps/stale revisions with structured reasons, and never uses wall-clock last-write-wins.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew :companion-protocol:test
git add companion-protocol
git commit -m "feat: define the wearable session protocol"
```

---

### Task 5: Run a Wear OS Technical Spike

**Files:**
- Create: `wear/build.gradle.kts`
- Create: `wear/src/main/AndroidManifest.xml`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/MainActivity.kt`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/sync/WearDataLayerService.kt`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/ui/SpikeScreen.kt`
- Create: `docs/wear-spike-results.md`

- [ ] **Step 1: Add a non-standalone Wear module with pinned libraries**

Declare `android.hardware.type.watch`, matching application/package identity and signing, and `com.google.android.wearable.standalone=false`. Add Compose for Wear OS 1.6.2, `androidx.wear:wear:1.4.0`, `androidx.wear:wear-ongoing:1.1.0`, and `com.google.android.gms:play-services-wearable:20.0.1`.

- [ ] **Step 2: Prove transport semantics**

Send one `PlanSnapshot` as a `DataItem`, one live ping as a `MessageClient` message, and three visual frames as Data Layer `Asset`s. Verify disconnected `DataItem` delivery after reconnect.

- [ ] **Step 3: Prove an Ongoing Activity timer**

Run a ten-minute elapsed-realtime timer in a foreground service, expose an Ongoing Activity, leave the app, enter ambient mode, and confirm restoration after process recreation.

- [ ] **Step 4: Benchmark visual formats on physical watches**

Compare the same three frames as current SVG, PNG, and pre-rasterized WebP on at least one low-end and one modern watch. Record:

- transferred bytes;
- cold/warm decode and first-render latency;
- peak process memory;
- dropped frames for `1 -> 2 -> 3 -> 2`;
- battery delta during 30 minutes interactive/ambient;
- legibility at actual watch size.

Choose the smallest format that meets: first frame under 250 ms after cache hit, no visible jank, and less than 3 percentage points additional battery drain per hour versus the static control. If no animated format meets the threshold, ship static-only until optimized.

- [ ] **Step 5: Commit the measured decision**

```bash
git add wear settings.gradle.kts gradle docs/wear-spike-results.md
git commit -m "test: validate the Wear OS companion architecture"
```

---

### Task 6: Add Phone Companion Coordination

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/wear/CompanionCoordinator.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/wear/PhoneWearDataLayer.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/wear/PhoneWearListenerService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/WallCrawlApplication.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/wear/CompanionCoordinatorTest.kt`

- [ ] **Step 1: Test snapshot creation and event replay**

Assert `PlanSnapshot` is under 100 KB, contains only the active session, and excludes notes/prompts/history. Replaying an event twice changes Room once.

- [ ] **Step 2: Implement transport behind an interface**

```kotlin
interface PhoneWearDataLayer {
    suspend fun publishPlan(snapshot: PlanSnapshot)
    suspend fun publishAck(ack: SyncAck)
    suspend fun publishVisual(exerciseId: String, frameIndex: Int, bytes: ByteArray)
    val incomingEvents: Flow<List<SessionEvent>>
}
```

- [ ] **Step 3: Reconcile transactionally**

For each event batch: verify protocol/session/revision, apply valid set/substitution/finish events through repository methods, store processed event IDs, increment canonical revision, and publish an ack. A conflict becomes an explicit watch-visible state, not silent overwrite.

- [ ] **Step 4: Mirror active-session changes**

Starting/resuming/updating a workout republishes compact state. Do not send a tick every second; timers are derived locally.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew :app:testDebugUnitTest --tests '*CompanionCoordinatorTest'
git add app
git commit -m "feat: coordinate phone and Wear workouts"
```

---

### Task 7: Add a Durable Watch Event Journal

**Files:**
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/session/WatchSessionStore.kt`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/session/WatchEventJournal.kt`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/session/WatchSessionRepository.kt`
- Test: `wear/src/test/java/wallcrawl/elopenmike/com/wear/session/WatchEventJournalTest.kt`

- [ ] **Step 1: Test offline durability**

Append five events, recreate the repository, ack the first three, and assert only events four and five remain pending. Reject sequence reuse.

- [ ] **Step 2: Store the mirrored plan and journal locally**

Use a small Room database when queries/transactions are needed; otherwise use Proto DataStore. The storage must survive process death and watch reboot. Persist protocol version, last acked sequence, active plan, events, conflicts, and visual cache metadata.

- [ ] **Step 3: Journal before UI success**

Every Complete/Undo/Skip/Finish action appends and fsyncs through the storage API before changing the UI to success. Transport runs afterward and may fail independently.

- [ ] **Step 4: Apply acknowledgements**

Delete only explicitly accepted event IDs. Permanent rejection remains visible with a recovery action; retryable rejection stays queued with bounded backoff.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew :wear:testDebugUnitTest --tests '*WatchEventJournalTest'
git add wear
git commit -m "feat: persist Wear workout events"
```

---

### Task 8: Implement Watch-Owned Timers and Ongoing Activity

**Files:**
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/timer/RestTimerEngine.kt`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/service/WorkoutForegroundService.kt`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/notification/WorkoutOngoingActivity.kt`
- Modify: `wear/src/main/AndroidManifest.xml`
- Test: `wear/src/test/java/wallcrawl/elopenmike/com/wear/timer/RestTimerEngineTest.kt`

- [ ] **Step 1: Write monotonic timer tests**

Inject wall and elapsed clocks. Changing wall time/time zone must not alter a running timer. Process recreation on the same boot restores from the monotonic deadline; reboot recovery uses stored duration/wall audit and resolves expired/ambiguous timers conservatively.

- [ ] **Step 2: Implement source-owned timer state**

```kotlin
data class RestTimer(
    val timerId: String,
    val setId: String,
    val sourceNodeId: String,
    val durationMillis: Long,
    val startedAtElapsedRealtime: Long,
    val deadlineElapsedRealtime: Long,
    val startedAtWallTime: Long
)
```

Never compare phone and watch elapsed-realtime values. The source device owns the expiry haptic; the other device displays a mirrored best-effort status.

- [ ] **Step 3: Add foreground service and Ongoing Activity**

Start for an active workout, publish category `WORKOUT`, show current exercise/set/rest status, and provide a one-tap return intent. Stop only after completion/discard is durably journaled.

- [ ] **Step 4: Handle ambient mode**

Stop animation. Use a static black layout and system-supported timer/chronometer display; never claim stale heart rate is live.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew :wear:testDebugUnitTest --tests '*RestTimerEngineTest' :wear:assembleDebug
git add wear
git commit -m "feat: run workout timers on Wear OS"
```

---

### Task 9: Build the Glanceable Watch Logger

**Files:**
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/ui/ActiveSetScreen.kt`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/ui/RestTimerScreen.kt`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/ui/FinishWorkoutScreen.kt`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/ui/WearWorkoutViewModel.kt`
- Test: `wear/src/test/java/wallcrawl/elopenmike/com/wear/ui/WearWorkoutViewModelTest.kt`
- Test: `wear/src/androidTest/java/wallcrawl/elopenmike/com/wear/ui/WearWorkoutFlowTest.kt`

- [ ] **Step 1: Test the full state machine**

`Ready -> ActiveSet -> Rest -> ActiveSet -> FinishConfirmation -> FinishedPendingSync`. Include undo, skip, disconnect, process death, stale revision, and missing visual.

- [ ] **Step 2: Implement the primary screen**

Show exercise name, set `n/N`, previous/target/actual, large Complete, plus/minus/rotary controls, and rest status. Use black background, minimum 48dp targets, scalable fonts, round-safe insets, TalkBack labels, and explicit close/finish.

- [ ] **Step 3: Start rest only after durable completion**

Complete appends `SetCompleted`, then creates `RestStarted`, then transitions UI. A failed journal write shows an error and does not pretend the set completed.

- [ ] **Step 4: Confirm incomplete finish**

Show completed/open/skipped counts. Finishing with open sets requires explicit skip or return. Discard requests remain pending until acknowledged by the phone.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew :wear:testDebugUnitTest :wear:connectedDebugAndroidTest
git add wear
git commit -m "feat: add the Wear OS workout logger"
```

---

### Task 10: Add Bounded Exercise Animation

**Files:**
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/visual/WatchVisualCache.kt`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/ui/MovementGuideScreen.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/wear/CompanionCoordinator.kt`
- Modify: `tools/workout-guide/import_catalog.py`
- Test: `wear/src/test/java/wallcrawl/elopenmike/com/wear/visual/WatchVisualCacheTest.kt`
- Test: `wear/src/androidTest/java/wallcrawl/elopenmike/com/wear/ui/MovementGuideScreenTest.kt`

- [ ] **Step 1: Implement the measured format from Task 5**

Generate deterministic watch-sized frames at development/import time; never perform SVG-to-raster conversion during a workout. Store content hashes in the plan snapshot.

- [ ] **Step 2: Prefetch only session visuals**

Use Data Layer `Asset`s, not the 100 KB `DataItem` payload. Prefetch active and next exercise first, then the remaining session. Cache by content hash with a bounded LRU and verify checksums before display.

- [ ] **Step 3: Implement the reconciled animation policy**

- Primary logger: static frame.
- Exercise transition: one `1 -> 2 -> 3 -> 2` cycle.
- Dedicated swipe page: loop only while visible and interactive; auto-pause after three cycles.
- Ambient/rest: static frame or glyph.
- Missing/corrupt frame: immediate glyph fallback.

- [ ] **Step 4: Verify animation never blocks the workout**

Tests must complete and time a set with zero visual assets, corrupt assets, transfer delay, and cache eviction.

- [ ] **Step 5: Re-run physical power checks and commit**

```bash
./gradlew :wear:testDebugUnitTest :wear:connectedDebugAndroidTest
git add wear app tools
git commit -m "feat: show exercise motion on Wear OS"
```

---

### Task 11: Add Resilient Disconnected Execution

**Files:**
- Modify: `wear/src/main/java/wallcrawl/elopenmike/com/wear/session/WatchSessionRepository.kt`
- Modify: `wear/src/main/java/wallcrawl/elopenmike/com/wear/sync/WearDataLayerService.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/wear/CompanionCoordinator.kt`
- Test: `companion-protocol/src/test/java/wallcrawl/elopenmike/com/companion/DisconnectedWorkoutTest.kt`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/wear/WearReconciliationTest.kt`

- [ ] **Step 1: Write the locker test**

Start on phone, mirror plan, disconnect, complete all sets/timers on watch, finish, reconnect, and assert exactly one completed phone session with every performed value.

- [ ] **Step 2: Prevent split-brain editing**

When watch takes execution control, phone defaults to subscriber/read-only mode. Explicit `Take over on phone` creates a revisioned handoff event. Conflicting edits are surfaced; neither side silently wins.

- [ ] **Step 3: Reconcile events and completion**

Phone applies unseen events transactionally. Adaptation/progression runs only after `WorkoutFinished` is accepted and all preceding sequences are present.

- [ ] **Step 4: Handle failure cases**

Cover no paired watch, app missing on peer, Bluetooth/network loss, Data Layer cloud relay, watch reboot, phone process death, multiple watches, protocol mismatch, cache miss, stale plan, and user deletion.

- [ ] **Step 5: Verify and commit**

```bash
./gradlew :companion-protocol:test :app:testDebugUnitTest :wear:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest :wear:connectedDebugAndroidTest
git add app wear companion-protocol
git commit -m "feat: reconcile offline Wear workouts"
```

---

### Task 12: Add Optional Wear Health Services

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `wear/build.gradle.kts`
- Modify: `wear/src/main/AndroidManifest.xml`
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/health/WearExerciseCapture.kt`
- Modify: `wear/src/main/java/wallcrawl/elopenmike/com/wear/service/WorkoutForegroundService.kt`
- Test: `wear/src/test/java/wallcrawl/elopenmike/com/wear/health/WearExerciseCaptureTest.kt`

- [ ] **Step 1: Test capability and permission degradation**

Unsupported strength exercise, missing heart-rate capability, denied/revoked permission, and another app owning an exercise must leave manual logging and timers operational.

- [ ] **Step 2: Add opt-in sensor capture**

Add `androidx.health:health-services-client:1.0.0`. Query `ExerciseClient` capabilities before requesting data. Request only metrics shown or exported. Start/stop through the foreground service and treat Health Services callbacks as the sensor-state source of truth.

- [ ] **Step 3: Journal measured samples separately**

Batch actual samples with source/device metadata. Never infer RPE, readiness, calories, or deload decisions from heart rate alone.

- [ ] **Step 4: Sync to phone and optionally Health Connect**

Phone validates timestamps/session membership and writes first-party measured records only when the user enabled the corresponding Health Connect permission.

- [ ] **Step 5: Verify with synthetic Health Services data and commit**

```bash
./gradlew :wear:testDebugUnitTest --tests '*WearExerciseCaptureTest' :wear:connectedDebugAndroidTest
git add gradle wear
git commit -m "feat: capture optional Wear health metrics"
```

---

### Task 13: Add Resume Surfaces and Local Privacy Controls

**Files:**
- Create: `wear/src/main/java/wallcrawl/elopenmike/com/wear/tile/WorkoutTileService.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/HealthIntegrationScreen.kt`
- Modify: `docs/privacy.md`
- Test: `wear/src/androidTest/java/wallcrawl/elopenmike/com/wear/tile/WorkoutTileServiceTest.kt`

- [ ] **Step 1: Add a minimal tile**

Idle: `Start on phone`. Active: exercise, set, and Resume. Tiles do not run timers, animation, or background fetch.

- [ ] **Step 2: Add transparent controls**

Expose paired-watch status, remove watch data, Health Connect permissions/export state, sensor permissions, delete exported WallCrawl Health Connect records, and disable companion sync.

- [ ] **Step 3: Document transport accurately**

State that core workouts remain local, Wear Data Layer traffic may traverse Google infrastructure when direct Bluetooth is unavailable, platform transport is end-to-end encrypted, and no WallCrawl-operated server receives workout data.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew :wear:connectedDebugAndroidTest :app:testDebugUnitTest
git add wear app docs
git commit -m "feat: add wearable data controls"
```

---

### Task 14: Add Cross-Device CI and Release Gates

**Files:**
- Modify: `.github/workflows/ci.yml`
- Create: `.github/workflows/wear-integration.yml`
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Create: `docs/wear-os.md`
- Create: `docs/health-connect.md`

- [ ] **Step 1: Keep fast PR checks**

```bash
./gradlew :core:model:test :companion-protocol:test \
  :app:testDebugUnitTest :wear:testDebugUnitTest \
  :app:lintDebug :wear:lintDebug \
  :app:assembleDebug :wear:assembleDebug \
  --stacktrace --no-daemon
```

- [ ] **Step 2: Add scheduled paired-device verification**

Run phone and Wear emulators for install, pairing, Data Layer snapshot, set logging, disconnect/reconnect, Ongoing Activity, ambient mode, and accessibility. Keep physical-watch power tests as a release checklist because emulator battery numbers are not meaningful.

- [ ] **Step 3: Verify Play requirements**

Check separate Wear APK versioning, watch feature declaration, standalone flag, matching package/signature, 64-bit support, round layouts, Wear screenshots, Ongoing Activity, and Health Connect declarations/privacy policy.

- [ ] **Step 4: Run full verification**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
./gradlew test lint assembleDebug --stacktrace --no-daemon
git --no-pager diff --check
```

- [ ] **Step 5: Commit**

```bash
git add .github README.md docs
git commit -m "docs: add local health and Wear architecture"
```

## Release Gates

- Health Connect denial/unavailability never affects Room completion.
- Health exports use first-party provenance and idempotent client record versions.
- A mirrored workout remains executable after phone disconnection.
- Every watch event is durable before success UI and idempotent after replay.
- Phone/watch conflicts are explicit; wall-clock last-write-wins is prohibited.
- Rest timers survive navigation/process recreation and use source-local monotonic time.
- Animation stops in ambient mode and never blocks logging.
- No full-catalog visual bundle is shipped to watch without measured justification.
- Sensor capture is optional, capability-gated, and never fabricates health metrics.
- No local LLM, prompt, or full history is sent to the watch.
