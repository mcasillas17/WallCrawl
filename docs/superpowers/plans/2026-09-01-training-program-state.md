# Training Program State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compose the `PRIMARY_ONLY_V1` weekly dose ledger and the reviewed adaptation state into one derived `TrainingProgramState`, replacing an untested inline ternary with a named pure policy, without changing any user-visible behavior.

**Architecture:** A pure `AdaptationStatePolicy` maps `UserProfile` to `AdaptationState`. A `TrainingProgramStateProvider` calls that policy, reads `WeeklyDoseLedgerRepository`, and composes both into `TrainingProgramState`. `WorkoutGenerationContextBuilder` uses the provider on its existing flag-on path and carries the state on `WorkoutGenerationContext`. The production flag is `false`, so the live path is untouched.

**Tech Stack:** Kotlin, Coroutines, JUnit 4, Truth, Room (via the existing ledger repository).

Design: `docs/superpowers/specs/2026-09-01-training-program-state-design.md`

---

## File Structure

| File | Responsibility |
| --- | --- |
| `core/model/TrainingProgramState.kt` (create) | Immutable composed value plus its policy-version enum |
| `core/ai/AdaptationStatePolicy.kt` (create) | Pure `UserProfile` → `AdaptationState` |
| `core/ai/TrainingProgramStateProvider.kt` (create) | Composes the policy with the ledger; the only unit doing I/O |
| `core/model/WorkoutGenerationContext.kt` (modify) | Carries the optional composed state |
| `core/ai/WorkoutGenerationContextBuilder.kt` (modify) | Uses the provider instead of the inline ternary |
| `WallCrawlApplication.kt` (modify) | Wires the provider with `Clock`/`ZoneId` |

---

### Task 1: Add the adaptation state policy

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/AdaptationStatePolicy.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/AdaptationStatePolicyTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.UserProfile

class AdaptationStatePolicyTest {

    private val policy = AdaptationStatePolicy()

    @Test
    fun aReportedBreakDerivesReturning() {
        val profile = UserProfile(name = "Crawler", returningAfterBreakWeeks = 3)

        assertThat(policy.derive(profile)).isEqualTo(AdaptationState.RETURNING)
    }

    @Test
    fun noReportedBreakDerivesUncalibrated() {
        val profile = UserProfile(name = "Crawler", returningAfterBreakWeeks = 0)

        assertThat(policy.derive(profile)).isEqualTo(AdaptationState.UNCALIBRATED)
    }

    /**
     * `ExerciseEligibilityPolicy` applies the temporary advanced-complexity ceiling with an
     * allow-by-default check on exactly UNCALIBRATED and RETURNING. Emitting any other state
     * lifts that ceiling, so a user who has not calibrated could be offered advanced work.
     * If this policy learns a third state, update the ceiling deliberately and together.
     */
    @Test
    fun everyDerivableStateIsOneTheAdvancedCeilingCovers() {
        val derivable = (0..12).map { weeks ->
            policy.derive(UserProfile(name = "Crawler", returningAfterBreakWeeks = weeks))
        }.toSet()

        assertThat(derivable)
            .containsNoneIn(
                AdaptationState.entries - AdaptationStatePolicy.CEILING_COVERED_STATES
            )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
  ./gradlew testDebugUnitTest --tests '*AdaptationStatePolicyTest' --no-daemon
```
Expected: FAIL — `Unresolved reference 'AdaptationStatePolicy'`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * Derives the adaptation state a deterministic plan is built under.
 *
 * The policy is pure and total: it reads only the profile, performs no I/O, and cannot fail.
 *
 * It deliberately derives just two of the nine declared [AdaptationState] values, which is
 * exactly what the planner did before this policy existed. The remaining states have entry
 * and exit conditions defined in terms of weekly dose targets and comparable outcomes that
 * do not exist yet, so deriving them here would be invention rather than policy.
 *
 * Widening the output is a safety-relevant change, not an improvement in isolation:
 * `ExerciseEligibilityPolicy` withholds advanced-complexity exercises only while the state is
 * one of [CEILING_COVERED_STATES], so any additional state lifts that ceiling.
 */
class AdaptationStatePolicy {

    fun derive(profile: UserProfile): AdaptationState =
        if (profile.returningAfterBreakWeeks > 0) {
            AdaptationState.RETURNING
        } else {
            AdaptationState.UNCALIBRATED
        }

    companion object {
        /** The states for which the temporary advanced-complexity ceiling applies. */
        val CEILING_COVERED_STATES: Set<AdaptationState> =
            setOf(AdaptationState.UNCALIBRATED, AdaptationState.RETURNING)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same command as Step 2. Expected: `BUILD SUCCESSFUL`, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/wallcrawl/elopenmike/com/core/ai/AdaptationStatePolicy.kt \
        app/src/test/java/wallcrawl/elopenmike/com/core/ai/AdaptationStatePolicyTest.kt
git commit -m "feat: extract the adaptation state policy"
```

---

### Task 2: Add the composed training program state

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/TrainingProgramState.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/model/TrainingProgramStateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrainingProgramStateTest {

    private val ledger = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = 20_696L,
        timeZoneId = "UTC",
        catalogVersion = "catalog-commit",
        reviewPolicyVersion = 1,
        directPrimarySets = mapOf("Chest" to 4),
        secondaryInvolvement = mapOf("Triceps" to 4),
        unattributedWorkSets = mapOf(LedgerOmissionReason.METADATA_NOT_APPROVED to 2)
    )

    @Test
    fun theStateCarriesItsAdaptationStateAndLedgerUnchanged() {
        val state = TrainingProgramState(
            policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
            adaptationState = AdaptationState.RETURNING,
            weeklyLedger = ledger
        )

        assertThat(state.adaptationState).isEqualTo(AdaptationState.RETURNING)
        assertThat(state.weeklyLedger).isEqualTo(ledger)
        assertThat(state.weeklyLedger.directPrimarySets).containsExactly("Chest", 4)
    }

    @Test
    fun statesWithIdenticalInputsAreEqual() {
        fun build() = TrainingProgramState(
            policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
            adaptationState = AdaptationState.UNCALIBRATED,
            weeklyLedger = ledger
        )

        assertThat(build()).isEqualTo(build())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
  ./gradlew testDebugUnitTest --tests '*TrainingProgramStateTest' --no-daemon
```
Expected: FAIL — `Unresolved reference 'TrainingProgramState'`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package wallcrawl.elopenmike.com.core.model

/** The versioned policy under which a [TrainingProgramState] was composed. */
enum class TrainingProgramStatePolicyVersion {
    PROGRAM_STATE_V1
}

/**
 * The derived state a deterministic plan is built under: how the user is currently adapting,
 * and what this week's training has already contained.
 *
 * Both halves are derived, never accumulated. [weeklyLedger] is reconstructed from immutable
 * completed history, and [adaptationState] is a pure function of the profile, so an identical
 * profile and history always compose an identical state.
 *
 * No policy reads [weeklyLedger] yet. It is carried here so weekly dose targets and
 * recommendation snapshots can consume it without re-deriving it at another point in the
 * flow. Its counts are all zero while the bundled catalog has no `APPROVED` metadata.
 */
data class TrainingProgramState(
    val policyVersion: TrainingProgramStatePolicyVersion,
    val adaptationState: AdaptationState,
    val weeklyLedger: WeeklyDoseLedger
)
```

- [ ] **Step 4: Run test to verify it passes**

Run the same command as Step 2. Expected: `BUILD SUCCESSFUL`, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/wallcrawl/elopenmike/com/core/model/TrainingProgramState.kt \
        app/src/test/java/wallcrawl/elopenmike/com/core/model/TrainingProgramStateTest.kt
git commit -m "feat: add the composed training program state"
```

---

### Task 3: Add the provider that composes state from history

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/TrainingProgramStateProvider.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/TrainingProgramStateProviderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.WeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger

class TrainingProgramStateProviderTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private val ledger = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = 20_696L,
        timeZoneId = zone.id,
        catalogVersion = "catalog-commit",
        reviewPolicyVersion = 1,
        directPrimarySets = emptyMap(),
        secondaryInvolvement = emptyMap(),
        unattributedWorkSets = mapOf(LedgerOmissionReason.METADATA_NOT_APPROVED to 6)
    )

    private class RecordingLedgerRepository(
        private val ledger: WeeklyDoseLedger
    ) : WeeklyDoseLedgerRepository {
        var requestedProfileId: String? = null
        var requestedZone: ZoneId? = null

        override suspend fun weeklyLedgerAt(
            profileId: String,
            instant: Instant,
            zoneId: ZoneId
        ): WeeklyDoseLedger = ledger

        override suspend fun currentWeeklyLedger(
            profileId: String,
            zoneId: ZoneId
        ): WeeklyDoseLedger {
            requestedProfileId = profileId
            requestedZone = zoneId
            return ledger
        }
    }

    @Test
    fun theProviderComposesTheDerivedStateWithThisWeeksLedger() = runTest {
        val repository = RecordingLedgerRepository(ledger)
        val provider = TrainingProgramStateProvider(
            weeklyDoseLedgerRepository = repository,
            zoneId = { zone }
        )
        val profile = UserProfile(name = "Crawler", returningAfterBreakWeeks = 2)

        val state = provider.currentState(profile)

        assertThat(state.policyVersion)
            .isEqualTo(TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1)
        assertThat(state.adaptationState).isEqualTo(AdaptationState.RETURNING)
        assertThat(state.weeklyLedger).isEqualTo(ledger)
    }

    @Test
    fun theLedgerIsReadForThatProfileInTheInjectedZone() = runTest {
        val repository = RecordingLedgerRepository(ledger)
        val provider = TrainingProgramStateProvider(
            weeklyDoseLedgerRepository = repository,
            zoneId = { zone }
        )
        val profile = UserProfile(name = "Crawler")

        provider.currentState(profile)

        assertThat(repository.requestedProfileId).isEqualTo(profile.id)
        assertThat(repository.requestedZone).isEqualTo(zone)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
  ./gradlew testDebugUnitTest --tests '*TrainingProgramStateProviderTest' --no-daemon
```
Expected: FAIL — `Unresolved reference 'TrainingProgramStateProvider'`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package wallcrawl.elopenmike.com.core.ai

import java.time.ZoneId
import wallcrawl.elopenmike.com.core.database.repository.WeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * Composes the current [TrainingProgramState] for one profile.
 *
 * This is the only unit in the composition that performs I/O. The adaptation policy stays
 * pure, and the weekly ledger is read through its repository, which reconstructs it from
 * completed history and caches nothing that can drift from that history.
 *
 * The zone is supplied rather than read here, so a caller in a test controls the week
 * boundary and the application controls it in production. Reading the same calendar week in
 * a different zone produces a separately reconstructed ledger, which is the ledger's own
 * documented behavior.
 *
 * Failures propagate. An unreadable catalog or database is not an empty training week, and
 * reporting one as the other would under-report work the user actually did.
 */
class TrainingProgramStateProvider(
    private val weeklyDoseLedgerRepository: WeeklyDoseLedgerRepository,
    private val adaptationStatePolicy: AdaptationStatePolicy = AdaptationStatePolicy(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault
) {

    suspend fun currentState(profile: UserProfile): TrainingProgramState =
        TrainingProgramState(
            policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
            adaptationState = adaptationStatePolicy.derive(profile),
            weeklyLedger = weeklyDoseLedgerRepository.currentWeeklyLedger(
                profileId = profile.id,
                zoneId = zoneId()
            )
        )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same command as Step 2. Expected: `BUILD SUCCESSFUL`, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/wallcrawl/elopenmike/com/core/ai/TrainingProgramStateProvider.kt \
        app/src/test/java/wallcrawl/elopenmike/com/core/ai/TrainingProgramStateProviderTest.kt
git commit -m "feat: compose training program state from history"
```

---

### Task 4: Use the provider in the context builder

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutGenerationContext.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilder.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/WallCrawlApplication.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

Extend the existing private helper `reviewedEligibilityBuilder` (currently at
`WorkoutGenerationContextBuilderTest.kt:318`) with two new parameters, then append the two
tests below to the same class.

```kotlin
    private fun reviewedEligibilityBuilder(
        exercises: List<wallcrawl.elopenmike.com.core.model.Exercise>,
        completedSessions: List<WorkoutSession>,
        returningAfterBreakWeeks: Int = 0,
        reviewedCapabilityEligibility: Boolean = true,
        trainingProgramStateProvider: TrainingProgramStateProvider? = null
    ): WorkoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
        userProfileRepository = StubUserProfileRepository(
            UserProfile(
                availableEquipment = listOf(
                    StandardEquipment.DUMBBELL,
                    StandardEquipment.BENCH
                ),
                returningAfterBreakWeeks = returningAfterBreakWeeks
            )
        ),
        workoutRepository = StubWorkoutRepository(completedSessions),
        exerciseCatalog = InMemoryExerciseCatalog(exercises),
        exerciseFilter = ExerciseFilter(),
        historyAnalyzer = WorkoutHistoryAnalyzer(),
        plannerFeatureFlags = PlannerFeatureFlags(
            reviewedCapabilityEligibility = reviewedCapabilityEligibility
        ),
        reviewedEligibilityPolicy = ExerciseEligibilityPolicy(),
        trainingProgramStateProvider = trainingProgramStateProvider
    )
```

Add these two test-only ledger repositories to the same file:

```kotlin
    private class StubWeeklyDoseLedgerRepository(
        private val ledger: WeeklyDoseLedger
    ) : WeeklyDoseLedgerRepository {
        override suspend fun weeklyLedgerAt(
            profileId: String,
            instant: java.time.Instant,
            zoneId: ZoneId
        ): WeeklyDoseLedger = ledger

        override suspend fun currentWeeklyLedger(
            profileId: String,
            zoneId: ZoneId
        ): WeeklyDoseLedger = ledger
    }

    /** Fails the test if the disabled path reads the ledger at all. */
    private object ThrowingWeeklyDoseLedgerRepository : WeeklyDoseLedgerRepository {
        override suspend fun weeklyLedgerAt(
            profileId: String,
            instant: java.time.Instant,
            zoneId: ZoneId
        ): WeeklyDoseLedger = error("The disabled path must not read the weekly ledger.")

        override suspend fun currentWeeklyLedger(
            profileId: String,
            zoneId: ZoneId
        ): WeeklyDoseLedger = error("The disabled path must not read the weekly ledger.")
    }

    private val emptyLedger = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = 20_696L,
        timeZoneId = "UTC",
        catalogVersion = "catalog-commit",
        reviewPolicyVersion = 1,
        directPrimarySets = emptyMap(),
        secondaryInvolvement = emptyMap(),
        unattributedWorkSets = emptyMap()
    )
```

```kotlin
    @Test
    fun contextCarriesTheProgramStateWhenReviewedEligibilityIsEnabled() = runTest {
        val ledgerRepository = StubWeeklyDoseLedgerRepository(emptyLedger)
        val builder = reviewedEligibilityBuilder(
            exercises = InMemoryExerciseCatalog.SAMPLE_EXERCISES,
            completedSessions = emptyList(),
            trainingProgramStateProvider = TrainingProgramStateProvider(
                weeklyDoseLedgerRepository = ledgerRepository,
                zoneId = { ZoneId.of("UTC") }
            )
        )

        val context = builder.build()

        assertThat(context.trainingProgramState).isNotNull()
        assertThat(context.trainingProgramState?.adaptationState)
            .isEqualTo(AdaptationState.UNCALIBRATED)
        assertThat(context.trainingProgramState?.weeklyLedger).isEqualTo(emptyLedger)
    }

    @Test
    fun contextCarriesNoProgramStateWhileReviewedEligibilityIsDisabled() = runTest {
        val builder = reviewedEligibilityBuilder(
            exercises = InMemoryExerciseCatalog.SAMPLE_EXERCISES,
            completedSessions = emptyList(),
            reviewedCapabilityEligibility = false,
            trainingProgramStateProvider = TrainingProgramStateProvider(
                weeklyDoseLedgerRepository = ThrowingWeeklyDoseLedgerRepository,
                zoneId = { ZoneId.of("UTC") }
            )
        )

        val context = builder.build()

        // The disabled path must not read the ledger at all: the stub throws if it does.
        assertThat(context.trainingProgramState).isNull()
        assertThat(context.automaticEligibilityResult).isNull()
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
  ./gradlew testDebugUnitTest --tests '*WorkoutGenerationContextBuilderTest' --no-daemon
```
Expected: FAIL — `Unresolved reference 'trainingProgramState'`.

- [ ] **Step 3: Write minimal implementation**

In `WorkoutGenerationContext.kt`, add the field after `automaticEligibilityResult`:

```kotlin
    val automaticEligibilityResult: AutomaticEligibilityResult? = null,
    /**
     * The composed program state, present only when reviewed eligibility is enabled.
     *
     * No planner or policy reads it yet. It is carried so weekly dose targets can consume the
     * ledger without re-deriving it, and it is null on the legacy path so that path allocates
     * nothing and reads no history it did not already read.
     */
    val trainingProgramState: TrainingProgramState? = null,
    val preferredUnits: WeightUnit = userProfile.preferredUnit
```

In `WorkoutGenerationContextBuilder.kt`, add the constructor parameter:

```kotlin
    private val plannerFeatureFlags: PlannerFeatureFlags = PlannerFeatureFlags(),
    private val reviewedEligibilityPolicy: ExerciseEligibilityPolicy = ExerciseEligibilityPolicy(),
    private val trainingProgramStateProvider: TrainingProgramStateProvider? = null,
    private val nowTimestamp: () -> Long = System::currentTimeMillis
```

Replace the inline ternary. Compose the state once, before the eligibility call, and pass its
adaptation state through:

```kotlin
        val trainingProgramState = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            trainingProgramStateProvider?.currentState(profile)
        } else {
            null
        }
        val automaticEligibilityResult = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
            val exercisesById = allExercises.associateBy { it.id }
            reviewedEligibilityPolicy.evaluate(
                exercises = allExercises,
                profile = profile,
                adaptationState = trainingProgramState?.adaptationState
                    ?: adaptationStatePolicy.derive(profile),
                demonstratedProgressionFamilies = exerciseHistory.keys.mapNotNullTo(linkedSetOf()) {
                    exerciseId ->
                    exercisesById[exerciseId]
                        ?.reviewedMetadata
                        ?.takeIf { it.reviewState == ReviewState.APPROVED }
                        ?.progressionFamily
                }
            )
        } else {
            null
        }
```

Add the policy field so the fallback uses the same rule as the provider:

```kotlin
    private val adaptationStatePolicy: AdaptationStatePolicy = AdaptationStatePolicy(),
```

Pass the state into the returned context:

```kotlin
            automaticEligibilityResult = automaticEligibilityResult,
            trainingProgramState = trainingProgramState,
```

In `WallCrawlApplication.kt`, wire the provider into the builder:

```kotlin
    override val workoutGenerationContextBuilder: WorkoutGenerationContextBuilder by lazy {
        WorkoutGenerationContextBuilder(
            userProfileRepository = userProfileRepository,
            workoutRepository = workoutRepository,
            exerciseCatalog = exerciseCatalog,
            exerciseFilter = exerciseFilter,
            historyAnalyzer = workoutHistoryAnalyzer,
            plannerFeatureFlags = PlannerFeatureFlags(
                reviewedCapabilityEligibility = false
            ),
            trainingProgramStateProvider = TrainingProgramStateProvider(
                weeklyDoseLedgerRepository = weeklyDoseLedgerRepository
            )
        )
    }
```

Add the ledger repository to the container beside the other repositories, using the existing
database and catalog:

```kotlin
    val weeklyDoseLedgerRepository: WeeklyDoseLedgerRepository by lazy {
        OfflineWeeklyDoseLedgerRepository(
            historyDao = database.completedWorkoutHistoryDao(),
            ledgerStateDao = database.weeklyDoseLedgerStateDao(),
            catalogSource = workoutGuideCatalogSource
        )
    }
```

`database` and `workoutGuideCatalogSource` are existing container properties
(`WallCrawlApplication.kt:33` and `:45`); declare `weeklyDoseLedgerRepository` on the
container interface alongside the other repositories so the builder can reach it.

- [ ] **Step 4: Run test to verify it passes**

Run the same command as Step 2. Expected: `BUILD SUCCESSFUL`, with the existing tests in that
class still passing.

- [ ] **Step 5: Run the planner corpus to prove selection is unchanged**

Run:
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
  ./gradlew testDebugUnitTest --tests '*PlannerFixture*' --tests '*FakeWorkoutPlannerTest' \
  --rerun-tasks --no-daemon
```
Expected: `BUILD SUCCESSFUL`, no fixture expectation changed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutGenerationContext.kt \
        app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilder.kt \
        app/src/main/java/wallcrawl/elopenmike/com/WallCrawlApplication.kt \
        app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilderTest.kt
git commit -m "feat: carry training program state on the generation context"
```

---

### Task 5: Document the composition

**Files:**
- Modify: `docs/weekly-dose-ledger.md`
- Modify: `docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md`

- [ ] **Step 1: Update the ledger doc's consumer statement**

`docs/weekly-dose-ledger.md` currently says nothing consumes the ledger. Replace that
paragraph's second sentence so it states what is now true: the ledger is composed into
`TrainingProgramState` and carried on the generation context when reviewed eligibility is
enabled, and no policy reads its counts yet, so planner selection, dose targets, progression,
and deloads are still unchanged.

- [ ] **Step 2: Mark the plan's composition milestone**

In `docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md`, update the Task 3
status note to record that the ledger is now composed with the adaptation state, and that
state-based dose targets remain Task 4.

- [ ] **Step 3: Commit**

```bash
git add docs/weekly-dose-ledger.md \
        docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md
git commit -m "docs: record the training program state composition"
```

---

## Full verification

Run before opening a pull request:

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v

JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
  ./gradlew testDebugUnitTest --rerun-tasks --no-daemon

JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
  ./gradlew lintDebug assembleDebug --stacktrace --no-daemon

JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
  ./gradlew connectedDebugAndroidTest --no-daemon

git diff --check
```

Also confirm: the catalog still has 302 exercises with 37 `DRAFT` and 0 `APPROVED` entries,
`reviewedCapabilityEligibility` is still `false` in `WallCrawlApplication`, and no screen,
string resource, or Compose file appears in `git diff --name-only origin/main...HEAD`.
