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

The bundled catalog remains at 302 exercises. Its 211 authored
`reviewedMetadata` entries remain `DRAFT`, with zero authored `APPROVED`
entries. The [full-catalog review](research/2026-09-07-full-exercise-catalog-review.json)
distinguishes content readiness, unresolved evidence and excluded categories.
It does not grant human approval or change the rollout flag. The
[coverage report](reviewed-catalog-coverage.md) separates actual zero-approved
results from explicitly synthetic prospective cohorts.

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
| 2 | No complete reviewed equipment alternative or required fixed-anchor setup is available | `MISSING_EQUIPMENT` | `EQUIPMENT_REMOVED_ALL` |
| 3 | Metadata is absent or not `APPROVED` | `MISSING_APPROVED_METADATA` | `NO_APPROVED_METADATA` |
| 4 | A required capability is `AVOID` | `CAPABILITY_AVOID` | `CAPABILITIES_REMOVED_ALL` |
| 5 | A selected joint sensitivity is not in this exercise's `clearedTrainingConstraints` | `UNMAPPED_TRAINING_CONSTRAINT` | `TRAINING_CONSTRAINTS_REMOVED_ALL` |
| 5 | `LOW_IMPACT_ONLY` meets `ImpactLevel.HIGH` | `HIGH_IMPACT_DISALLOWED` | `TRAINING_CONSTRAINTS_REMOVED_ALL` |
| 6 | `ADVANCED` is temporarily above the uncalibrated/returning ceiling | `ADVANCED_WHILE_UNCALIBRATED` or `ADVANCED_WHILE_RETURNING` | `CALIBRATION_COMPLEXITY_REMOVED_ALL` |

Stage 5's joint decision is **per exercise**, not per pool. Each approved record carries
`clearedTrainingConstraints`: the joint sensitivities a reviewer explicitly cleared it for.
A selected sensitivity that the record does not list keeps that one exercise out of the
automatic pool; it no longer removes every candidate on the strength of the profile alone.
`LOW_IMPACT_ONLY` is never listed there, because `impactLevel` already decides it, and both
the importer and the Android parser reject a record that tries to list it.

Absence is not clearance. No clearance is inferred from an exercise's name, muscles,
movement pattern, complexity or equipment, and there is no numeric joint-stress score. Every
bundled record currently clears nothing, so any selected joint sensitivity still produces
`TRAINING_CONSTRAINTS_REMOVED_ALL` — an honest typed refusal, not a blanket policy. A
clearance is a reviewer's product judgement about a self-reported label; it is not a
diagnosis, and reviewer agreement is not clinical validation.

The supported-regression exception applies the same rule. A regression is an alternative
*inside* the user's restrictions: an uncleared regression cannot lift the advanced ceiling,
so a restriction can never be bypassed by substituting an easier movement.

`StateBasedTrainingPolicy` re-checks both training-constraint rules before prescribing — the
same clearance, and `LOW_IMPACT_ONLY` against `ImpactLevel.HIGH` — and refuses either with
`TRAINING_CONSTRAINT_REACHED_POLICY`. That is the second boundary the `AVOID` capability rule
already had. The clearance check is one shared predicate called from both policies, so they
cannot drift into different readings of the same reviewed field.

An eligible exercise has the hard reason `APPROVED`. `EligibilityPreference`
retains each explicitly required capability that is `LIMITED` or `UNKNOWN`, in
enum order, as a soft input only. Hard rule output defines candidate
membership; evidence never edits these decisions.

The [fixed-anchor contract](band-anchor-equipment.md) is shared with the active
legacy filter and manual equipment warnings. It is a source-bound minimum, not a
second source of metadata approval. A supported regression must also meet it.
Neither a DRAFT nor a confirmed setup can pass the separate approval gate, and
the unresolved `banded-row` setup cannot pass even with a synthetic approval.

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

## Proposed initial rollout contract

This section is a **proposal awaiting human sign-off**, not a shipped configuration.
`PlannerFeatureFlags.reviewedCapabilityEligibility` is still `false` in
`WallCrawlApplication`, and the bundled catalog still holds 211 `DRAFT` and 0 `APPROVED`
records, so nothing below is in effect.

### Proposed cohort

The 186 IDs the [evidence ledger](research/2026-09-07-full-exercise-catalog-review.json)
dispositions as `ready_for_human_review`. Each has an authored metadata block bound to a
`metadataSha256`, and no unresolved content decision other than field-by-field sign-off.
Deliberately outside the cohort, and unavailable to automatic planning until separately
resolved:

- the 81 `pending_evidence_or_policy` IDs, including `banded-row`'s unresolved anchor,
  `barbell-deadlift`'s and `sumo-deadlift`'s unratified single primary, `trap-bar-deadlift`'s
  unrepresented implement, and the 56 IDs with no authored block at all;
- the 35 IDs outside automatic-strength scope (stretches, distance-duration and other timed
  conditioning work).

All 302 remain browseable and manually selectable. Exclusion from the cohort removes an
exercise from automatic planning only.

### Supported profiles

Candidate counts below come from `ReviewedCatalogCoverageTest` against the full 302-entry
catalog, using **explicitly synthetic in-memory approvals** of the proposed cohort. They
measure what the cohort would make available; they are not approval, and not a claim that
the content is correct. Every listed success passed `ProgramValidator` with repair disabled.

| Profile | Cohort candidates | Outcome |
| --- | ---: | --- |
| Bodyweight, uncalibrated | 34 | Valid `PUSH` proposal |
| Dumbbells + bench | 37 | Valid `PUSH` proposal |
| Machines | 26 | Valid `PUSH` proposal |
| Full gym | 186 | Valid `PUSH` proposal |
| Full gym, uncalibrated | 167 | Valid `PUSH` proposal |
| Returning after a break | 73 | Valid `PUSH` proposal, returner caps retained |
| Mixed-unit history | 81 | Valid `PUSH` proposal, no invented load |
| Sparse history | 52 | Valid `PULL` proposal, null load retained |
| `LIMITED` bodyweight push | 41 | Valid `PUSH` proposal |
| `AVOID` bodyweight push | 31 | Valid `UPPER_BODY` proposal, avoided demand excluded |
| `AVOID` standing balance, 35 min | 117 | Valid `PUSH` proposal |
| `AVOID` floor transition, 35 min | 136 | Valid `PUSH` proposal |
| Band-only | 11 | Valid `UPPER_BODY` proposal; `Chest` reported unavailable |
| Any selected joint sensitivity | 0 | `TRAINING_CONSTRAINTS_REMOVED_ALL` |
| Empty equipment inventory | 0 | `NO_APPROVED_METADATA` (staged aggregate; see the coverage report) |

### Not supported at this rollout

- **Any selected joint sensitivity.** No record clears one, so the reviewed path refuses.
  This is missing reviewed content, not a contradictory user request: the same profile with a
  synthetic knee clearance yields 186 candidates and a raw-valid proposal, recorded as the
  `joint-constraint-cleared` case. Populating `clearedTrainingConstraints` is part of the same
  human sign-off pass.
- **Band-only chest or push work.** No band chest exercise exists in the pinned source. The
  session is labelled by what it actually trains and reports `Chest` as an unavailable
  priority. See the [coverage report](reviewed-catalog-coverage.md).
- **The six fixed-anchor band variants** without their explicit setup confirmations, and
  `banded-row` under any inventory.
- **Progression and deload.** The rollout is deliberately conservative: dose, effort and rest
  come from the existing `STATE_BASED_DOSE_EFFORT_REST_V1` policy and the current
  `UNCALIBRATED`/`RETURNING`/`BUILD` states only. There is no one-variable progression and no
  deload offer; those remain Roadmap Package 9.

### Remaining sign-off request

Approval is a human decision that names the IDs and fields reviewed, a real reviewer role, a
real review time and a rationale, per the
[sign-off worksheet](reviewed-exercise-metadata-human-signoff.md). No such decision has been
supplied for any ID. AI review, model consensus, pull-request approval, merge and passing
tests are not that decision and cannot substitute for it.

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

Selected joint sensitivities are mapped but unpopulated: the
`clearedTrainingConstraints` contract exists in the schema, the importer, the Android parser
and the eligibility policy, and every bundled record clears nothing. Filling it in is part of
the same human sign-off pass, not separate work.

The next enablement requirement is still deliberate human review and approval of
the metadata, followed by an explicit availability/persona review and a
deliberate production flag change. Approval must not happen automatically as a
side effect of catalog growth, pull-request review, or capability evidence.
