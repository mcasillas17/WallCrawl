# Capability Evidence and Soft-Penalty Relaxation Design

## Goal

Implement deterministic-engine Task 6A without expanding it into progression,
adaptation-state changes, or deloads. A pure, versioned policy derives auditable
capability evidence from the already bounded completed-workout history. Reviewed-mode
ranking may use that evidence to suppress only the soft penalty attached to an eligible
exercise's `LIMITED` or `UNKNOWN` capability preference.

The rule is a WallCrawl reproducibility policy, not a physiological, medical, readiness,
recovery, or safety threshold.

## Existing boundaries

- `ExerciseEligibilityPolicy` remains the sole reviewed hard-eligibility gate. Evidence
  never changes its candidate membership or reasons.
- `WorkoutGenerationContextBuilder` already reads at most eight recent completed sessions.
  Evidence is derived once from that list only when reviewed eligibility is enabled.
- `FakeWorkoutPlanner` already applies soft experience difficulty after stronger
  split/mechanics criteria. Capability preference ordering belongs immediately before
  that experience penalty in both compound and accessory comparators.
- Production keeps `PlannerFeatureFlags.reviewedCapabilityEligibility` disabled. The
  legacy path neither derives nor consumes capability evidence.
- Completed history, `UserProfile.movementCapabilities`, catalog metadata, and review
  provenance remain immutable.

## Considered approaches

### 1. Rewrite eligibility preferences

The builder could remove `EligibilityPreference` values when evidence exists. This would
make ranking simple, but it would erase the distinction between the user's current answer
and the derived relaxation, reduce auditability, and make the eligibility result depend
on history. Rejected.

### 2. Derive evidence inside planner comparators

Comparators could inspect recent sessions while sorting. This avoids a context field, but
repeatedly scans history, obscures the privacy and performance boundary, and risks
comparison-order-dependent work. Rejected.

### 3. Precompute typed evidence and apply a separate rank penalty

The builder derives an immutable evidence set once and stores it in
`WorkoutGenerationContext`. Ranking reads the existing eligibility preferences and the
precomputed evidence to return a binary unresolved-capability penalty. This preserves
hard eligibility, keeps history processing bounded and pure, retains every typed input for
audit, and leaves the legacy path unchanged. Selected.

## Capability evidence model

Each evidence record contains stable, typed audit data:

- policy version;
- reason `TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS`;
- exercise ID to which the relaxation applies;
- exact demonstrated exercise ID;
- scope: exact exercise or direct approved regression;
- comparable movement shape;
- the two qualifying distinct session IDs.

The result is keyed in stable exercise-ID order and defensively copies all maps and lists.
If several sources could relax the same exercise, exact evidence wins; otherwise the
lexicographically first demonstrated source wins. This makes output independent of input
collection order.

## Qualifying observation

One exercise observation can contribute only when all of these hold:

1. The containing session has a nonblank unique ID, `SessionStatus.COMPLETED`, and a
   positive completion timestamp.
2. The persisted `WorkoutExercise.exerciseId` is nonblank.
3. The prescription is structurally compatible with the persisted exercise and set type.
4. At least one non-warm-up work set exists.
5. Every non-warm-up set is completed, has no stop reason or stop timestamp, has a positive
   completion timestamp, has `feltManageable == true`, and carries valid type-specific
   values.
6. Warm-up sets never contribute. Their presence does not replace the required work set.

Requiring every work set in the observation to qualify prevents a partially completed,
skipped, stopped, pain-stopped, false-confirmed, or unconfirmed exercise from becoming a
successful session observation because one other set happened to be complete.

RPE and RIR do not participate. Session completion or set completion never implies
manageability.

## Comparability contract

Two observations are comparable only when they have the same exact exercise ID and the
same persisted measurement shape. Target and performed magnitudes are validated but are
not compared; Task 6A defines no load, repetitions, duration, distance, effort, readiness,
or progression threshold.

The supported shapes are:

| Exercise type | Comparable persisted shape |
| --- | --- |
| `WEIGHT_REPS` | positive finite external load plus positive repetitions |
| `BODYWEIGHT_REPS` | positive repetitions, with no load/assistance/time/distance |
| `ASSISTED_BODYWEIGHT` | finite nonnegative assistance plus positive repetitions |
| `DURATION` | positive duration only |
| `DISTANCE_DURATION` | positive distance only, positive duration only, or both, exactly matching the prescription's target dimensions |

Every work set must report the prescription's `ExerciseType`, use the same measurement
shape, and omit values belonging to other shapes. The policy groups observations by exact
exercise ID and shape, deduplicates by session ID, and emits evidence only for a group
with at least two distinct sessions. This is the narrowest deterministic contract the
current persisted prescription and set snapshots support without inventing equivalence
thresholds.

## Approved-regression scope

Exact evidence always applies to the demonstrated exercise. It extends one edge only when:

- the demonstrated exercise has human-`APPROVED` reviewed metadata;
- that metadata directly lists the target ID in `approvedRegressions`;
- the target exists in the supplied catalog; and
- the target also has `APPROVED` reviewed metadata.

No names, muscles, equipment, complexity, experience, progression-family equality, or
substitution links create evidence. Missing IDs and `DRAFT`/missing metadata do not
expand it. Links are not traversed transitively. Tests may construct unmistakably
synthetic approvals in memory; bundled metadata and provenance are untouched.

## Ranking integration

Reviewed candidates retain their original `EligibilityDecision.preferences`. A separate
pure ranking policy returns:

- `1` when an eligible decision has at least one unresolved `Limited` or `Unknown`
  preference and no evidence applies to that exact candidate ID;
- `0` otherwise.

The binary penalty avoids inventing unsupported relative severity between `LIMITED` and
`UNKNOWN` or between exercises that require different numbers of capabilities. Evidence
suppresses the candidate's capability-preference penalty only. It does not alter the
independent experience penalty.

Comparator order becomes:

- compounds: primary split match, capability penalty, experience penalty, fatigue, ID;
- accessories: primary split match, isolation, programming presence, capability penalty,
  experience penalty, fatigue, ID.

The planner computes the per-exercise capability penalties once before sorting. Candidate
membership is never filtered, so a sole candidate remains selectable.

## Error and malformed-history handling

The policy is total over persisted history. A malformed session, exercise observation, or
set simply cannot qualify; it does not make unrelated valid history disappear and does
not produce a success-shaped fallback. Blank IDs, contradictory completion/stop fields,
non-finite or out-of-range values, mismatched exercise types, and cross-shape fields are
invalid observations.

No exception text, logging, analytics, network call, mutable counter, cache, migration,
or additional repository query is introduced.

## Test strategy

Strict red-green-refactor tests cover:

- one versus two distinct comparable sessions and duplicate observations in one session;
- explicit true, false, and null confirmation;
- cancelled, in-progress, incomplete, stopped, skipped, pain-stopped, malformed, and
  warm-up-only data;
- every current exercise type and incompatible shapes;
- exact, direct-approved-regression, draft, missing, unrelated, family-peer, and
  non-transitive scope;
- defensive copying and deterministic output;
- unchanged hard eligibility and candidate membership;
- reviewed compound/accessory ordering, evidence suppression, and independent experience
  ordering;
- unchanged legacy recommendations and no legacy evidence derivation;
- one bounded history query and no input/profile/history/catalog/provenance mutation.

Focused tests run during development. Final validation follows the repository-wide JVM,
Python, lint, assembly, connected Android, diff, artifact, provenance, migration, and
commit-trailer checks required by the Task 6A handoff.

## Explicit exclusions

Task 6A does not change `AdaptationStatePolicy`, derive new adaptation states, progress any
prescription variable, implement `DeloadOffer`, alter weekly dose/rest/effort policy,
approve metadata, enable reviewed eligibility in production, add persistence, or modify
the concurrent `recommendedRepRange` schema work.
