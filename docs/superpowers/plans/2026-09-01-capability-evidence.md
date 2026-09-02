# Capability Evidence and Soft-Penalty Relaxation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Derive typed evidence from two distinct comparable manageable completed sessions and use it to relax only reviewed-mode capability preference ranking for the demonstrated exercise and direct approved regressions.

**Architecture:** Add an immutable capability-evidence model and a pure versioned `CapabilityEvidencePolicy` over the existing bounded history view. Precompute evidence in `WorkoutGenerationContextBuilder` only when reviewed eligibility is enabled, then let a separate ranking policy combine eligibility preferences with that evidence before the independent experience penalty in both planner comparators. Hard eligibility, candidate membership, profile values, persistence, bundled metadata, and the production-disabled reviewed feature flag remain unchanged.

**Tech Stack:** Kotlin, Coroutines/Flow, JUnit 4, Truth, Android Gradle Plugin, Python standard-library tests

---

## File map

- Create `app/src/main/java/wallcrawl/elopenmike/com/core/model/CapabilityEvidence.kt`:
  immutable policy version, reason, measurement shape, scope, evidence record, and
  defensively copied evidence collection.
- Create `app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicy.kt`:
  pure history qualification, comparability grouping, session deduplication, and direct
  approved-regression expansion.
- Create `app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicyTest.kt`:
  policy matrix for session state, explicit feedback, malformed outcomes, all exercise
  types, graph scope, determinism, and mutation boundaries.
- Create `app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityPreferenceRankingPolicy.kt`:
  binary reviewed capability-preference penalties computed once per candidate set.
- Create `app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityPreferenceRankingPolicyTest.kt`:
  eligibility preference, evidence suppression, candidate-membership, and immutability
  tests.
- Modify `app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutGenerationContext.kt`:
  carry a default-empty immutable evidence set.
- Modify `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilder.kt`:
  derive evidence once from the existing recent-session query on the reviewed path.
- Modify `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilderTest.kt`:
  reviewed-only derivation, one-query boundary, and non-mutation tests.
- Modify `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`:
  precompute capability penalties and insert them before experience in both comparators.
- Modify `app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt`:
  compound/accessory ordering, exact relaxation, independent difficulty, sole-candidate,
  membership, and legacy invariance tests.
- Modify `README.md`, `docs/architecture.md`,
  `docs/reviewed-capability-eligibility.md`,
  `docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md`, and
  `docs/superpowers/specs/2026-08-29-science-based-workout-engines-design.md`:
  record shipped Task 6A behavior and explicit exclusions.

### Task 1: Add typed immutable capability evidence

**Files:**
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicyTest.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/model/CapabilityEvidence.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicy.kt`

- [ ] **Step 1: Write the first failing exact-evidence tests**

Create fixtures that build structurally valid `WorkoutSession`, `WorkoutExercise`,
`ExercisePrescription`, and `WorkoutSet` snapshots. Add tests with these assertions:

```kotlin
@Test
fun derive_oneQualifyingSessionIsInsufficient() {
    val result = policy.derive(
        sessions = listOf(completedSession("session-1", manageableWeightSet())),
        exercises = emptyList()
    )

    assertThat(result.records).isEmpty()
}

@Test
fun derive_twoDistinctComparableManageableSessionsProduceExactEvidence() {
    val result = policy.derive(
        sessions = listOf(
            completedSession("session-2", manageableWeightSet(weight = 55.0)),
            completedSession("session-1", manageableWeightSet(weight = 45.0))
        ),
        exercises = emptyList()
    )

    assertThat(result["press"]).isEqualTo(
        CapabilityEvidence(
            policyVersion = CapabilityEvidencePolicyVersion
                .TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1,
            reason = CapabilityEvidenceReason
                .TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS,
            appliesToExerciseId = "press",
            demonstratedExerciseId = "press",
            scope = CapabilityEvidenceScope.EXACT_EXERCISE,
            comparableShape = ComparableMovementShape.WEIGHT_REPETITIONS,
            qualifyingSessionIds = listOf("session-1", "session-2")
        )
    )
}

@Test
fun derive_duplicateExerciseObservationsInsideOneSessionDoNotSatisfyRule() {
    val session = completedSession(
        id = "session-1",
        exercises = listOf(
            manageableExercise("press"),
            manageableExercise("press", workoutExerciseId = "duplicate")
        )
    )

    assertThat(policy.derive(listOf(session), emptyList()).records).isEmpty()
}
```

- [ ] **Step 2: Run the new policy test and preserve the expected red result**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*CapabilityEvidencePolicyTest' \
  --rerun-tasks --no-daemon
```

Expected: FAIL because `CapabilityEvidencePolicy` and evidence model types do not exist.
Save this initial compiler/test output in the session artifacts directory as
`task-6a-initial-red.txt`; do not commit the log.

- [ ] **Step 3: Add the immutable evidence types**

Implement the model with these public contracts:

```kotlin
enum class CapabilityEvidencePolicyVersion {
    TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1
}

enum class CapabilityEvidenceReason {
    TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS
}

enum class CapabilityEvidenceScope {
    EXACT_EXERCISE,
    DIRECT_APPROVED_REGRESSION
}

enum class ComparableMovementShape {
    WEIGHT_REPETITIONS,
    BODYWEIGHT_REPETITIONS,
    ASSISTED_BODYWEIGHT_REPETITIONS,
    TIMED_DURATION,
    DISTANCE_DURATION_DISTANCE_ONLY,
    DISTANCE_DURATION_TIME_ONLY,
    DISTANCE_DURATION_DISTANCE_AND_TIME
}

data class CapabilityEvidence(
    val policyVersion: CapabilityEvidencePolicyVersion,
    val reason: CapabilityEvidenceReason,
    val appliesToExerciseId: String,
    val demonstratedExerciseId: String,
    val scope: CapabilityEvidenceScope,
    val comparableShape: ComparableMovementShape,
    val qualifyingSessionIds: List<String>
)

class CapabilityEvidenceSet private constructor(
    val records: Map<String, CapabilityEvidence>
) {
    operator fun get(exerciseId: String): CapabilityEvidence? = records[exerciseId]
    fun appliesTo(exerciseId: String): Boolean = exerciseId in records

    override fun equals(other: Any?): Boolean =
        this === other || other is CapabilityEvidenceSet && records == other.records

    override fun hashCode(): Int = records.hashCode()

    companion object {
        fun from(records: Iterable<CapabilityEvidence>): CapabilityEvidenceSet
        fun empty(): CapabilityEvidenceSet
    }
}
```

`CapabilityEvidenceSet.from` must validate nonblank IDs, exact record/map-key agreement,
at least two distinct sorted session IDs, and defensively copy the outer map and every
record's session list using `Collections.unmodifiableMap` and
`Collections.unmodifiableList`. Sort records by `appliesToExerciseId`.

- [ ] **Step 4: Implement the minimal exact-evidence derivation**

Add:

```kotlin
class CapabilityEvidencePolicy(
    private val policyVersion: CapabilityEvidencePolicyVersion =
        CapabilityEvidencePolicyVersion.TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1
) {
    fun derive(
        sessions: List<WorkoutSession>,
        exercises: List<Exercise>
    ): CapabilityEvidenceSet
}
```

For each distinct nonblank completed session ID with a positive completion timestamp,
derive at most one observation per exact exercise ID and comparable shape. Group by
`exerciseId` plus `ComparableMovementShape`, sort shape by enum order, sort and deduplicate
session IDs, and emit the first group with at least two sessions. The first two IDs satisfy
the threshold; retain every qualifying distinct sorted ID in the evidence record so the
bounded source remains auditable.

- [ ] **Step 5: Run the policy test**

Run the focused command from Step 2.

Expected: PASS for the first three tests.

- [ ] **Step 6: Commit the exact-evidence slice**

```bash
git add \
  app/src/main/java/wallcrawl/elopenmike/com/core/model/CapabilityEvidence.kt \
  app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicy.kt \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicyTest.kt
git commit -m "feat: derive exact capability evidence" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

### Task 2: Enforce conservative type-aware observation qualification

**Files:**
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicyTest.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicy.kt`

- [ ] **Step 1: Add the failing invalid-history matrix**

Use parameterized fixture loops or individually named tests proving that no evidence is
created when either observation contains:

```kotlin
listOf(
    SessionStatus.CANCELLED,
    SessionStatus.IN_PROGRESS
)
```

and when any non-warm-up set is incomplete, carries `USER_SKIPPED`, `PAIN_STOP`, another
stop reason, a stop timestamp, null/false `feltManageable`, a null/nonpositive completion
timestamp, a mismatched `exerciseType`, or invalid type-specific values. Add a separate
test showing a session containing only `SetType.WARMUP` never counts, and one showing a
valid warm-up plus fully valid work sets may count.

Add a test where one exercise observation contains one valid manageable work set plus one
incomplete/stopped/unconfirmed work set. Assert that the entire session observation is
rejected.

- [ ] **Step 2: Add failing tests for every persisted measurement shape**

Use two sessions per case and assert these shapes qualify:

```kotlin
ExerciseType.WEIGHT_REPS -> ComparableMovementShape.WEIGHT_REPETITIONS
ExerciseType.BODYWEIGHT_REPS -> ComparableMovementShape.BODYWEIGHT_REPETITIONS
ExerciseType.ASSISTED_BODYWEIGHT ->
    ComparableMovementShape.ASSISTED_BODYWEIGHT_REPETITIONS
ExerciseType.DURATION -> ComparableMovementShape.TIMED_DURATION
ExerciseType.DISTANCE_DURATION with distance only ->
    ComparableMovementShape.DISTANCE_DURATION_DISTANCE_ONLY
ExerciseType.DISTANCE_DURATION with duration only ->
    ComparableMovementShape.DISTANCE_DURATION_TIME_ONLY
ExerciseType.DISTANCE_DURATION with both ->
    ComparableMovementShape.DISTANCE_DURATION_DISTANCE_AND_TIME
```

Add incompatible-pair tests showing that assisted versus bodyweight, duration versus
distance, distance-only versus duration-only, mismatched set/prescription types, and
cross-shape populated values do not combine.

- [ ] **Step 3: Run the focused policy test and confirm red**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*CapabilityEvidencePolicyTest' \
  --rerun-tasks --no-daemon
```

Expected: FAIL on the new qualification and shape cases.

- [ ] **Step 4: Implement one total observation parser**

Create private `ComparableObservation` and measurement-shape helpers. Apply these rules:

```kotlin
session.status == SessionStatus.COMPLETED
session.completedAtTimestamp != null && session.completedAtTimestamp > 0L
exercise.exerciseId.isNotBlank()
workSets.isNotEmpty()
workSets.all(::isQualifyingWorkSet)
```

`isQualifyingWorkSet` must require completion, no stop fields, positive completion time,
explicit `feltManageable == true`, set type matching the prescription type, relevant
positive finite values, and all irrelevant values null. Ignore RPE/RIR.

For `DISTANCE_DURATION`, derive the shape from the prescription's positive target
dimensions and require the completed values to populate exactly those dimensions. Do not
compare target or completed magnitudes between sessions.

- [ ] **Step 5: Run focused policy tests**

Expected: all `CapabilityEvidencePolicyTest` cases PASS.

- [ ] **Step 6: Commit qualification behavior**

```bash
git add \
  app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicy.kt \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicyTest.kt
git commit -m "test: cover capability evidence qualification" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

### Task 3: Expand evidence only through direct approved regressions

**Files:**
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicyTest.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicy.kt`

- [ ] **Step 1: Add failing graph-scope tests**

Build synthetic in-memory `APPROVED` metadata with the existing provenance convention.
Prove:

```kotlin
assertThat(result["demonstrated"]?.scope)
    .isEqualTo(CapabilityEvidenceScope.EXACT_EXERCISE)
assertThat(result["direct-regression"]?.scope)
    .isEqualTo(CapabilityEvidenceScope.DIRECT_APPROVED_REGRESSION)
assertThat(result["direct-regression"]?.demonstratedExerciseId)
    .isEqualTo("demonstrated")
```

Also prove no expansion to a draft regression, from draft demonstrated metadata, to a
missing target ID, to `approvedSubstitutions`, to a same-family peer, to an unrelated
exercise, or through `demonstrated -> direct -> transitive`.

Add order tests with mutable caller lists/maps: mutate the session list, exercise list,
review links, and returned collection after derivation attempts. Assert the result stays
unchanged and mutation of the returned map/session IDs throws
`UnsupportedOperationException`.

- [ ] **Step 2: Run the focused test and confirm red**

Expected: direct-regression assertions FAIL because only exact evidence exists.

- [ ] **Step 3: Implement deterministic one-edge expansion**

Index the supplied catalog by ID without mutating it. Iterate exact records sorted by
demonstrated ID. Expand only when both the demonstrated and target records have
`ReviewState.APPROVED` and the target is a direct `approvedRegressions` link. Copy the
exact record's policy, reason, shape, and session IDs; change only:

```kotlin
appliesToExerciseId = target.id
scope = CapabilityEvidenceScope.DIRECT_APPROVED_REGRESSION
```

Insert exact records first and use `putIfAbsent` for expansions. Never recurse into a
newly expanded record.

- [ ] **Step 4: Run focused policy tests**

Expected: all policy, graph-scope, determinism, and immutability tests PASS.

- [ ] **Step 5: Commit regression scope**

```bash
git add \
  app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicy.kt \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityEvidencePolicyTest.kt
git commit -m "feat: scope evidence to approved regressions" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

### Task 4: Precompute reviewed capability penalties

**Files:**
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityPreferenceRankingPolicyTest.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityPreferenceRankingPolicy.kt`

- [ ] **Step 1: Write failing ranking-policy tests**

Cover eligible decisions with no preferences, `Limited`, `Unknown`, both, exact evidence,
direct-regression evidence, ineligible decisions, a sole candidate, and a decision for an
ID outside the candidate list. Assert:

```kotlin
assertThat(
    policy.penalties(
        candidateExerciseIds = listOf("limited", "unknown", "comfortable"),
        automaticEligibilityResult = result,
        capabilityEvidence = CapabilityEvidenceSet.empty()
    )
).containsExactly(
    "limited", 1,
    "unknown", 1,
    "comfortable", 0
).inOrder()
```

With evidence for `"limited"`, assert only that entry changes to `0`. Confirm adding
evidence does not change the supplied candidate IDs or eligibility result.

- [ ] **Step 2: Run the ranking-policy test and confirm red**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*CapabilityPreferenceRankingPolicyTest' \
  --rerun-tasks --no-daemon
```

Expected: FAIL because `CapabilityPreferenceRankingPolicy` does not exist.

- [ ] **Step 3: Implement binary penalties**

Add:

```kotlin
class CapabilityPreferenceRankingPolicy {
    fun penalties(
        candidateExerciseIds: List<String>,
        automaticEligibilityResult: AutomaticEligibilityResult?,
        capabilityEvidence: CapabilityEvidenceSet
    ): Map<String, Int>
}
```

Index decisions once by ID. For each distinct candidate ID in incoming order return `1`
only when the corresponding decision is eligible, has at least one
`EligibilityPreference`, and evidence does not apply. Return `0` for legacy null results,
missing decisions, ineligible decisions, or evidenced candidates. Defensively copy the
ordered result with `Collections.unmodifiableMap`.

- [ ] **Step 4: Run the ranking-policy test**

Expected: PASS.

- [ ] **Step 5: Commit the ranking policy**

```bash
git add \
  app/src/main/java/wallcrawl/elopenmike/com/core/ai/CapabilityPreferenceRankingPolicy.kt \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/CapabilityPreferenceRankingPolicyTest.kt
git commit -m "feat: rank unresolved capability preferences" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

### Task 5: Derive evidence at the context boundary

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutGenerationContext.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilder.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilderTest.kt`

- [ ] **Step 1: Add failing reviewed-path integration tests**

Add repository call counters to `StubWorkoutRepository` and use qualifying reviewed-path
history so derivation is observable through result state. Prove:

```kotlin
assertThat(context.capabilityEvidence["demonstrated"]).isNotNull()
assertThat(workoutRepository.recentCompletedSessionReadCount).isEqualTo(1)
```

Add a disabled-path test using the same qualifying history and assert:

```kotlin
assertThat(context.capabilityEvidence).isEqualTo(CapabilityEvidenceSet.empty())
assertThat(context.automaticEligibilityResult).isNull()
```

Assert both paths still call `getRecentCompletedSessions` exactly once because that read
already supplies legacy exercise history; Task 6A adds no second query. Snapshot the
profile, sessions, catalog exercises, reviewed metadata, and provenance before `build()`
and assert equality afterward.

- [ ] **Step 2: Run context tests and confirm red**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*WorkoutGenerationContextBuilderTest' \
  --rerun-tasks --no-daemon
```

Expected: FAIL because context does not carry or derive capability evidence.

- [ ] **Step 3: Add the context field and builder dependency**

Add to `WorkoutGenerationContext`:

```kotlin
val capabilityEvidence: CapabilityEvidenceSet = CapabilityEvidenceSet.empty()
```

Inject into `WorkoutGenerationContextBuilder`:

```kotlin
private val capabilityEvidencePolicy: CapabilityEvidencePolicy =
    CapabilityEvidencePolicy()
```

Inside the existing reviewed-enabled branch, call:

```kotlin
val capabilityEvidence = if (plannerFeatureFlags.reviewedCapabilityEligibility) {
    capabilityEvidencePolicy.derive(
        sessions = recentCompletedSessions,
        exercises = allExercises
    )
} else {
    CapabilityEvidenceSet.empty()
}
```

Pass the value into the context. Do not add repository calls, persistence, logging,
analytics, or mutable caches.

- [ ] **Step 4: Run context and policy tests**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*CapabilityEvidencePolicyTest' \
  --tests '*WorkoutGenerationContextBuilderTest' \
  --rerun-tasks --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit context integration**

```bash
git add \
  app/src/main/java/wallcrawl/elopenmike/com/core/model/WorkoutGenerationContext.kt \
  app/src/main/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilder.kt \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/WorkoutGenerationContextBuilderTest.kt
git commit -m "feat: precompute capability evidence context" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

### Task 6: Apply capability ordering in compound and accessory selection

**Files:**
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt`
- Modify: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/ExerciseEligibilityPolicyTest.kt`

- [ ] **Step 1: Add failing reviewed compound tests**

Create otherwise equal approved candidates whose eligibility decisions contain
`Limited`/`Unknown` versus no preferences. Assert the unpenalized compound comes first.
Add evidence for only the penalized candidate and assert the ID tie-breaker becomes
visible again.

Add a three-candidate case proving order precedence:

```text
stronger primary split match
then unresolved capability penalty
then experience difficulty
then fatigue
then ID
```

Assert an evidenced advanced candidate still remains behind an otherwise equal
foundational candidate for a beginner, proving experience remains independent.

- [ ] **Step 2: Add failing reviewed accessory tests**

Use one compound anchor plus otherwise equal isolation candidates. Assert capability
preference ordering applies after primary/isolation/programming criteria and before
experience/fatigue/ID. Add direct-regression evidence and assert only that target's
capability penalty is removed.

- [ ] **Step 3: Add membership, hard-boundary, and legacy invariance tests**

Prove:

- a sole capability-penalized candidate is selected;
- the generated exercise IDs remain exactly the supplied allowed-candidate IDs when the
  workout has enough slots;
- `AVOID`, explicit exclusions, missing equipment, constraints, low-impact,
  missing/draft metadata, and the temporary advanced ceiling remain rejected by
  `ExerciseEligibilityPolicy` even if an evidence object exists elsewhere;
- two legacy contexts that differ only in capability evidence and completed capability
  history generate equal recommendations after normalizing generated IDs.

- [ ] **Step 4: Run planner and eligibility tests and confirm red**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*FakeWorkoutPlannerTest' \
  --tests '*ExerciseEligibilityPolicyTest' \
  --rerun-tasks --no-daemon
```

Expected: new reviewed ordering tests FAIL; existing hard and legacy tests PASS.

- [ ] **Step 5: Precompute and insert capability penalties**

Inject `CapabilityPreferenceRankingPolicy` into `FakeWorkoutPlanner`. In
`selectExercisesForSplit`, compute once:

```kotlin
val capabilityPenalties = capabilityPreferenceRankingPolicy.penalties(
    candidateExerciseIds = matchingCandidates.map(Exercise::id),
    automaticEligibilityResult = context.automaticEligibilityResult,
    capabilityEvidence = context.capabilityEvidence
)
```

Pass the immutable map to both comparator builders and insert:

```kotlin
.thenBy { capabilityPenalties.getValue(it.id) }
.thenBy {
    difficultyRankingPolicy.aboveExperiencePenalty(
        exercise = it,
        experienceLevel = context.experienceLevel,
        reviewedEligibilityEnabled = reviewedEligibilityEnabled
    )
}
```

Do not filter candidates and do not change `ExerciseEligibilityPolicy`.

- [ ] **Step 6: Run all focused Task 6A JVM tests**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests '*CapabilityEvidencePolicyTest' \
  --tests '*CapabilityPreferenceRankingPolicyTest' \
  --tests '*ExerciseEligibilityPolicyTest' \
  --tests '*ExerciseDifficultyRankingPolicyTest' \
  --tests '*WorkoutGenerationContextBuilderTest' \
  --tests '*FakeWorkoutPlannerTest' \
  --tests '*PlannerFixture*' \
  --rerun-tasks --no-daemon
```

Expected: PASS with a nonzero test count.

- [ ] **Step 7: Commit planner integration**

```bash
git add \
  app/src/main/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlanner.kt \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/FakeWorkoutPlannerTest.kt \
  app/src/test/java/wallcrawl/elopenmike/com/core/ai/ExerciseEligibilityPolicyTest.kt
git commit -m "feat: relax reviewed capability ranking" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

### Task 7: Document shipped Task 6A boundaries

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture.md`
- Modify: `docs/reviewed-capability-eligibility.md`
- Modify: `docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md`
- Modify: `docs/superpowers/specs/2026-08-29-science-based-workout-engines-design.md`

- [ ] **Step 1: Update current-product documentation**

Document all of the following in README, architecture, and reviewed eligibility docs:

- two distinct comparable completed sessions;
- exact exercise ID and explicit `feltManageable == true`;
- conservative type/measurement-shape comparability;
- exact exercise plus one direct approved-regression edge only;
- immutable hard rules and unchanged candidate membership;
- no profile capability rewrite;
- local derivation from the existing eight-session read;
- reviewed-enabled ranking only and production flag still disabled;
- no medical, readiness, recovery, or physiological claim.

- [ ] **Step 2: Update roadmap and architecture status**

Split deterministic roadmap Task 6 into:

```text
Task 6A capability evidence and soft-penalty relaxation — shipped
Task 6B one-variable progression and adaptation-state expansion — unimplemented
Task 6C user-controlled DeloadOffer — unimplemented
```

Update the signed architecture only where its previously future-tense capability evidence
contract is now implemented. Preserve progression and deload language as future work.

- [ ] **Step 3: Check documentation for drift**

Run:

```bash
rg -n "capability evidence|Task 6|felt manageable|DeloadOffer|progression" \
  README.md docs/architecture.md docs/reviewed-capability-eligibility.md \
  docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md \
  docs/superpowers/specs/2026-08-29-science-based-workout-engines-design.md
git diff --check
```

Expected: no statement says Task 6A is absent or unimplemented; progression, adaptation
expansion, and `DeloadOffer` remain explicitly unimplemented; diff check exits 0.

- [ ] **Step 4: Commit documentation**

```bash
git add \
  README.md \
  docs/architecture.md \
  docs/reviewed-capability-eligibility.md \
  docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md \
  docs/superpowers/specs/2026-08-29-science-based-workout-engines-design.md
git commit -m "docs: record capability evidence behavior" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

### Task 8: Reconcile main and run fresh full validation

**Files:**
- Inspect: complete repository and `origin/main...HEAD` diff
- Do not create generated or machine-local artifacts in the repository

- [ ] **Step 1: Fetch and reconcile current main**

```bash
git fetch origin --prune
git merge-base --is-ancestor origin/main HEAD
```

If current `origin/main` is not an ancestor, merge it without rewriting history. Resolve
only real Task 6A conflicts, especially concurrent optional-`recommendedRepRange` work.
Do not duplicate schema/importer/catalog changes. Rerun focused tests after any merge.

- [ ] **Step 2: Run both Python suites**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
```

Expected: all discovered tests PASS with nonzero counts.

- [ ] **Step 3: Run the fresh full Gradle suite**

```bash
./gradlew test lint assemble --rerun-tasks --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run the full connected Android suite**

Boot or select an API 36 `google_apis` emulator matching
`.github/workflows/ci.yml`, then run:

```bash
./gradlew connectedDebugAndroidTest --rerun-tasks --stacktrace --no-daemon
```

Inspect `app/build/outputs/androidTest-results/connected/` and assert a nonzero test count,
zero failures, and zero unexpected skips.

- [ ] **Step 5: Run hygiene checks**

```bash
git diff --check origin/main...HEAD
git status --short
git --no-pager diff --stat origin/main...HEAD
git --no-pager diff --name-only origin/main...HEAD
git --no-pager log --format='%H%n%B%n---' origin/main..HEAD
```

Inspect the complete diff for generated artifacts, credentials/secrets, debug residue,
machine-local paths, accidental feature-flag changes, bundled metadata/provenance changes,
migrations, concurrency duplication, and missing Copilot trailers. The worktree must be
clean before review.

### Task 9: Converge three independent reviewers on one SHA

**Files:**
- Review only: complete `origin/main...HEAD` diff and required architecture documents

- [ ] **Step 1: Record the candidate identity**

```bash
BASE_SHA="$(git rev-parse origin/main)"
HEAD_SHA="$(git rev-parse HEAD)"
printf 'base=%s\nhead=%s\n' "$BASE_SHA" "$HEAD_SHA"
```

- [ ] **Step 2: Dispatch the same read-only review to all three models**

Use three independent `code-review` agents with:

```text
claude-opus-4.8
grok-4.6
gemini-3.7-flash
```

Give each the identical base SHA, head SHA, Task 6A requirements, design/spec paths, and
complete diff. Require review of correctness and determinism; hard-versus-soft
boundaries; local privacy/security and review provenance; bounded-history performance;
all prescription shapes and malformed history; tests, persistence/migrations, feature
flags, docs; and latest-main compatibility. Reviewers must not edit files.

- [ ] **Step 3: Triage findings technically**

For every valid behavior finding, first add a failing regression test and preserve its
red result, then implement the minimal fix. Reject findings that contradict repository
requirements with a concrete code/spec explanation. Commit every fix with the required
Copilot trailer.

- [ ] **Step 4: Revalidate after fixes**

Rerun affected focused tests, both Python suites, the full Gradle command, connected
Android tests, and hygiene checks after the final code or documentation change.

- [ ] **Step 5: Repeat all three reviews after every SHA change**

Record the new SHA and rerun Opus, Grok, and Gemini against that identical SHA, including
models that previously had no findings. Stop only when all three independently return no
findings on one shared final SHA.

### Task 10: Push and open the non-draft pull request

**Files:**
- Inspect: final branch, commits, and pull-request metadata

- [ ] **Step 1: Reconcile latest main one final time**

Fetch `origin/main`. If base or head changes, rerun full validation and all three reviewers
on the new identical SHA before proceeding.

- [ ] **Step 2: Push the reviewed branch**

```bash
git push --set-upstream origin mcasillas17-capability-evidence
```

- [ ] **Step 3: Open a non-draft PR to main**

Create a precise PR containing:

- shipped evidence and ranking behavior;
- hard eligibility, privacy, provenance, persistence, and production-flag boundaries;
- explicit progression/adaptation/deload exclusions;
- focused, Python, Gradle, and connected test counts;
- base and final SHA;
- same-SHA no-findings verdict from each reviewer.

Do not merge the PR.

- [ ] **Step 4: Verify PR state**

Confirm the PR is open, non-draft, targets `main`, points to the reviewed final SHA,
contains only intentional files, and report checks/mergeability without overstating
pending results.

- [ ] **Step 5: Prepare the handoff**

Report the PR URL, final and base SHAs, commits, changed files, shipped behavior, explicit
exclusions, complete validation counts, and each reviewer's final no-findings verdict on
the same SHA.
