# Reviewed capability eligibility

## Status and boundary

WallCrawl already ships an implemented reviewed-only automatic-planning path.
Production composition still sets `PlannerFeatureFlags.reviewedCapabilityEligibility`
to `false`, so today's shipped recommendations continue to use the legacy
`ExerciseFilter` and legacy `programming` metadata. The flag is local and set in
application composition; there is no remote configuration, analytics event,
automatic activation, or network rollout path.

That same reviewed-only flag gates three local features together:

- `ExerciseEligibilityPolicy`
- `CapabilityEvidencePolicy` and `CapabilityEvidenceSet`
- `TrainingProgramStateProvider` + `StateBasedTrainingPolicy`

The bundled catalog remains at 302 exercises. Its 37 authored
`reviewedMetadata` entries remain `DRAFT`, with zero authored `APPROVED`
entries. Task 6A did not mutate review state, reviewer identity, timestamps,
provenance, history, catalog assets, or profile capability values.

The typed flow is:

```text
local UserProfile + bounded completed history + bundled catalog
  -> ExerciseEligibilityPolicy
  -> per-exercise EligibilityDecision values
  -> AutomaticEligibilityResult.Candidates or NoCandidates
  -> CapabilityEvidenceSet derivation from the same bounded history read
  -> reviewed soft-capability ranking inside FakeWorkoutPlanner
  -> allowed automatic candidates or a typed planner failure
```

The reviewed policies perform no network access. `CapabilityEvidencePolicy`
performs no persistence, migration, cache write, analytics, or logging.
Nothing here reads body weight, height, age, BMI, body composition, recovery,
or readiness.
The profile/capability answers and source workout history are persisted locally
in Room and covered by the app's explicit backup exclusions. Derived capability
evidence is computed in memory rather than persisted separately. This processing
boundary is not a universal claim about Android/OEM transfer behavior; see
[Privacy and backup](privacy.md) for the configuration, recovery tradeoffs, and
previous-backup limitations.

## Deterministic hard rule order

Rules are evaluated in incoming catalog order. Reasons within a decision use
the order below, and aggregate failure selection replays the same stages. The
aggregate cause is the first stage that exhausts the candidates that survived
earlier stages.

| Stage | Hard decision | Typed reason | Aggregate failure |
| --- | --- | --- | --- |
| 1 | Explicitly excluded exercise | `USER_EXCLUDED` | `USER_EXCLUSIONS_REMOVED_ALL` |
| 2 | No complete reviewed equipment alternative is available | `MISSING_EQUIPMENT` | `EQUIPMENT_REMOVED_ALL` |
| 3 | Metadata is absent or not `APPROVED` | `MISSING_APPROVED_METADATA` | `NO_APPROVED_METADATA` |
| 4 | A required capability is `AVOID` | `CAPABILITY_AVOID` | `CAPABILITIES_REMOVED_ALL` |
| 5 | A joint-sensitive constraint has no reviewed exercise mapping | `UNMAPPED_TRAINING_CONSTRAINT` | `TRAINING_CONSTRAINTS_REMOVED_ALL` |
| 5 | `LOW_IMPACT_ONLY` meets `ImpactLevel.HIGH` | `HIGH_IMPACT_DISALLOWED` | `TRAINING_CONSTRAINTS_REMOVED_ALL` |
| 6 | `ADVANCED` is temporarily above the uncalibrated/returning ceiling | `ADVANCED_WHILE_UNCALIBRATED` or `ADVANCED_WHILE_RETURNING` | `CALIBRATION_COMPLEXITY_REMOVED_ALL` |

An eligible exercise has the hard reason `APPROVED`. `EligibilityPreference`
retains each explicitly required capability that is `LIMITED` or `UNKNOWN`, in
enum order, as a soft input only. Hard rule output defines candidate
membership; evidence never edits these decisions.

## Capability evidence criteria

`WorkoutGenerationContextBuilder` derives `CapabilityEvidenceSet` locally, on
demand, once per reviewed build from the same already-bounded max-eight
completed sessions it already fetched. The legacy path does not derive or
consume evidence.

Exact evidence requires all of the following:

- two distinct `SessionStatus.COMPLETED` sessions;
- the same exact `exerciseId` in both sessions;
- a positive session `completedAtTimestamp` in both sessions;
- at least one non-warm-up work set in each session;
- every non-warm-up work set completed, unstopped, and type-aligned with the
  exercise prescription;
- every non-warm-up work set carrying explicit `feltManageable == true`;
- every non-warm-up work set carrying a valid shape-specific persisted payload.

Null `feltManageable`, `feltManageable == false`, completion alone, RPE, and
RIR do not qualify evidence. Warm-up-only sessions do not qualify evidence.
One valid work set plus one invalid work set in the same session also fails the
session.

Comparability is product reproducibility, not physiology. The rule is exact
exercise ID plus type-safe persisted measurement shape:

- `WEIGHT_REPETITIONS`
- `BODYWEIGHT_REPETITIONS`
- `ASSISTED_BODYWEIGHT_REPETITIONS`
- `TIMED_DURATION`
- `DISTANCE_DURATION_DISTANCE_ONLY`
- `DISTANCE_DURATION_TIME_ONLY`
- `DISTANCE_DURATION_DISTANCE_AND_TIME`

The policy validates field presence, bounds, and shape consistency for load,
reps, bodyweight, assistance, duration, and distance. It does not compare
magnitudes, deltas, readiness, recovery, or medical thresholds.

## Scope, provenance, and determinism

Evidence applies only to:

1. the exact demonstrated exercise; or
2. one direct `approvedRegressions` target when both the demonstrated exercise
   metadata and the target metadata are `ReviewState.APPROVED`.

There is no draft, missing-metadata, inferred, substitution, blank-ID,
unrelated-peer, or transitive expansion. If a target has its own exact
evidence, exact evidence wins over inherited evidence. If multiple approved
sources point directly to the same approved target, the derived inherited record
uses the lexicographically first demonstrated exercise ID, so results stay
stable regardless of caller collection order.

Focused tests construct `APPROVED` reviewed metadata only in memory, with
synthetic provenance that clearly says it is test data. No synthetic approval is
written into `tools/workout-guide/reviewed-metadata.json`, the bundled catalog,
or bundled provenance assets.

The implementation defensively copies caller collections, returns an
unmodifiable record map, sorts qualifying session IDs, and does not mutate the
profile, history, catalog exercises, reviewed metadata, or provenance objects it
reads.

## Soft capability penalty semantics

Reviewed-mode unresolved capability preferences are ranked with a binary soft
penalty:

- `1` when an eligible candidate has at least one `EligibilityPreference.Limited`
  or `EligibilityPreference.Unknown` and no matching evidence record;
- `0` otherwise.

Evidence suppresses only that candidate's capability penalty. It never changes
hard eligibility, candidate membership, explicit exclusions, required
equipment, joint constraints, `LOW_IMPACT_ONLY`, the approved-metadata gate, or
the temporary advanced ceiling. A sole eligible candidate is still selected.

Inside `FakeWorkoutPlanner`, that penalty is intentionally weaker than the
structural split ordering and stronger than the later independent tie-breakers:

- compound ordering: split-primary match within the compound pool, then
  capability penalty, then experience penalty, then fatigue, then stable ID;
- accessory ordering: split-primary match, isolation preference, presence of
  programming metadata, then capability penalty, then experience penalty, then
  fatigue, then stable ID.

## Rollout and manual-workout preservation

When the flag is disabled, `WorkoutGenerationContext.automaticEligibilityResult`
is `null`, `WorkoutGenerationContext.capabilityEvidence` is
`CapabilityEvidenceSet.empty()`, `ExerciseFilter` supplies the same ordered
candidate list, and today's production planner remains invariant to capability
changes.

The reviewed gate exists only on automatic context construction. The exercise
library still reads the full catalog, and the manual template editor still reads
all 302 exercises and displays its existing profile-equipment warnings. Missing
or `DRAFT` reviewed metadata does not hide a browse or manual option.

## Deliberately incomplete work

Task 6A shipped behind the production-disabled reviewed flag: deterministic
capability evidence exists and soft capability-penalty suppression is wired
through the reviewed planner path.

Task 6B remains open: there is no `ProgressionEngine.kt`, no one-variable
progression, and no broader derived-state rollout beyond
`AdaptationStatePolicy`'s current `UNCALIBRATED`/`RETURNING` outputs.

Task 6C remains open: there is no `DeloadOfferPolicy.kt`, no user-controlled
`DeloadOffer`, and no multi-session deload state machine.

The next enablement requirement is still deliberate human review and approval of
the metadata, followed by an explicit availability/persona review and a
deliberate production flag change. Approval must not happen automatically as a
side effect of catalog growth, pull-request review, or capability evidence.
