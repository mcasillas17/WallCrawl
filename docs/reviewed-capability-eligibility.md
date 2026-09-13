# Reviewed capability eligibility

## Status and boundary

WallCrawl ships an implemented reviewed-only automatic-planning path, and
production composition now sets `PlannerFeatureFlags.reviewedCapabilityEligibility`
to `true`: `WallCrawlApplication` injects `PlannerFeatureFlags.PRODUCTION`, and
shipped recommendations are filtered for eligibility through this path instead of the
legacy `ExerciseFilter`. This does **not** replace legacy `programming` metadata:
`fatigueScore`, `mechanics`, `movementPattern`, `coachingSummary`, and
`recommendedRepRange` still drive ranking, coaching text, and the base rep-range/set
prescription for every candidate the reviewed path admits. `reviewedMetadata` supplies
the categorical eligibility gate (accepted-record membership, equipment, capability,
constraint, and complexity rules) and the state-based dose/effort/rest policy layered
on top of that base prescription; it does not itself supply ranking, coaching, or the
base prescription. The flag is local and set in
application composition; there is no remote configuration, analytics event,
automatic activation, or network rollout path.

That same reviewed-only flag gates three local features together:

- `ExerciseEligibilityPolicy`
- `CapabilityEvidencePolicy` and `CapabilityEvidenceSet`
- `TrainingProgramStateProvider` + `StateBasedTrainingPolicy`

The bundled catalog remains at 302 exercises. An owner-authorized audit accepted 182 of
its 211 authored `reviewedMetadata` entries as `AI_ACCEPTED`; the remaining 29 authored
entries stay `DRAFT`, 56 catalog entries carry no authored block at all, and 35 entries
are outside automatic-strength scope. **Zero entries are `APPROVED`: no genuine human
reviewer has signed off on any record.** `AI_ACCEPTED` is a separate, owner-authorized
alpha categorical acceptance — it is not human approval and not a clinical validation or
safety-for-everyone claim. See [the exact partition and audit](reviewed-exercise-metadata.md#ai-acceptance-audit)
for the four additional intrinsic holds, the seven sources that keep acceptance while
pointing at a still-pending endpoint, and why the audit found no way to record a
non-empty `clearedTrainingConstraints` list for any record. The
[full-catalog review](research/2026-09-07-full-exercise-catalog-review.json)
distinguishes content readiness, unresolved evidence and excluded categories; it is
historical evidence for the audit, not a live approval mechanism. The
[coverage report](reviewed-catalog-coverage.md) separates the real accepted-cohort
results now in production from the earlier, explicitly synthetic prospective
experiments that preceded acceptance.

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

## What "accepted" means at runtime

Every runtime consumer of reviewed metadata reads one gate,
`Exercise.acceptedMetadata()` in `core/ai/ReviewedMetadataAcceptance.kt`. It accepts two
review states and nothing else:

- `APPROVED` — human-only. It requires the human reviewer role and review time, and a record
  in this state carrying `aiReviewProvenance` is malformed, not a softer approval.
- `AI_ACCEPTED` — owner-authorized alpha. It requires `aiReviewProvenance` recorded over this
  exact exercise id, on exactly reviewed schema v3 (both `provenance.schemaVersion` and the
  AI provenance's own `schemaVersion` must equal 3; a v2 record is refused outright, and the
  runtime check is an equality, not a floor), with a SHA-256 content digest and HTTPS
  source references, and every human-review field left null.

`DRAFT`, an absent block, and any record whose provenance does not match its state are
refused. Acceptance never promotes an AI acceptance into a human approval; the two states
stay separately recorded and separately provenanced, and only the question "may automatic
planning read this?" is answered in one place.

A record's `approvedRegressions` and `approvedSubstitutions` edges authorize a relationship
and nothing more. A consumer that wants to use an edge runs its target through this same
gate, so an accepted source pointing at a pending target opens no capability exception,
propagates no evidence, and ranks as no supported regression. Holding such an edge does not
make the source's own metadata unaccepted, and no graph link is ever inferred or widened.
The parser's structural graph validation at import is a separate contract.

## Deterministic hard rule order

Rules are evaluated in incoming catalog order. Reasons within a decision use
the order below, and aggregate failure selection replays the same stages. The
aggregate cause is the first stage that exhausts the candidates that survived
earlier stages.

| Stage | Hard decision | Typed reason | Aggregate failure |
| --- | --- | --- | --- |
| 1 | Explicitly excluded exercise | `USER_EXCLUDED` | `USER_EXCLUSIONS_REMOVED_ALL` |
| 2 | No complete reviewed equipment alternative or required fixed-anchor setup is available | `MISSING_EQUIPMENT` | `EQUIPMENT_REMOVED_ALL` |
| 3 | Metadata is absent or not accepted | `MISSING_APPROVED_METADATA` | `NO_APPROVED_METADATA` |
| 4 | A required capability is `AVOID` | `CAPABILITY_AVOID` | `CAPABILITIES_REMOVED_ALL` |
| 5 | A selected joint sensitivity is not in this exercise's `clearedTrainingConstraints` | `UNMAPPED_TRAINING_CONSTRAINT` | `TRAINING_CONSTRAINTS_REMOVED_ALL` |
| 5 | `LOW_IMPACT_ONLY` meets `ImpactLevel.HIGH` | `HIGH_IMPACT_DISALLOWED` | `TRAINING_CONSTRAINTS_REMOVED_ALL` |
| 6 | `ADVANCED` is temporarily above the uncalibrated/returning ceiling | `ADVANCED_WHILE_UNCALIBRATED` or `ADVANCED_WHILE_RETURNING` | `CALIBRATION_COMPLEXITY_REMOVED_ALL` |

Stage 5's joint decision is **per exercise**, not per pool. Each accepted record carries
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

An eligible exercise has the hard reason `APPROVED`, whose name is frozen and means
accepted. `EligibilityPreference`
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
2. one direct `approvedRegressions` target when the demonstrated exercise metadata and
   the target metadata are **each independently accepted**. The edge authorizes the
   relationship only; it never carries the source's acceptance to the target.

There is no draft, missing-metadata, inferred, substitution, blank-ID,
unrelated-peer, or transitive expansion. If a target has its own exact
evidence, exact evidence wins over inherited evidence. If multiple accepted
sources point directly to the same accepted target, the derived inherited record
uses the lexicographically first demonstrated exercise ID, so results stay
stable regardless of caller collection order.

Focused tests construct accepted reviewed metadata only in memory, with
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

An additional reviewed ranking signal recognizes one narrower case. An eligible
source must have an exercise-specific `LIMITED` preference with no matching
source evidence, both endpoints must be independently accepted (`APPROVED` or
`AI_ACCEPTED`, through the same `Exercise.acceptedMetadata()` gate everything
else reads), the source must name the target in its direct `approvedRegressions`,
the target must be explicitly `SupportRequirement.SUPPORTED`, and the target
must not require the addressed capability. An accepted source naming a still-
pending target opens no exception here either: the target's own metadata must
independently pass the same gate. `UNKNOWN` is not treated as proven inability.
Missing feedback is
not evidence, but it also supplies no new meaning beyond the existing unresolved
preference. `OPTIONAL_SUPPORT`, a shared family, legacy alternatives, names,
muscles, reverse edges, transitive chains, substitutions, and unreviewed
intermediates never create this preference.

The policy receives the already legal candidate set and computes a bounded map
before sorting. It cannot introduce a linked target or modify eligibility.
Equipment, fixed-anchor requirements, explicit exclusions, `AVOID`, joint
restrictions, impact rules, approval, and complexity apply to the target before
ranking. Qualifying source evidence suppresses both the existing capability
penalty and this supported-regression preference, so evidence never loses under
a renamed penalty. Target evidence neither creates nor broadens the signal.

Inside `FakeWorkoutPlanner`, both capability signals are intentionally weaker
than structural session ordering and stronger than later independent
tie-breakers:

- compound ordering: split-primary match within the compound pool, then
  capability penalty, supported-regression preference, experience penalty,
  fatigue, then stable ID;
- accessory ordering: split-primary match, isolation preference, presence of
  programming metadata, capability penalty, supported-regression preference,
  experience penalty, fatigue, then stable ID.

This is an explicit product ordering choice, not a diagnosis, safety promise,
progression decision, or claim that supported variations are universally
superior. A structured reason is emitted only when a selected target actually
outranks a legal source competitor in the same stronger-precedence tier. It
retains target exercise, source exercise, and capability references.

## Enabled rollout contract

This section describes the **shipped configuration**, not a proposal.
`PlannerFeatureFlags.reviewedCapabilityEligibility` is `true` in the composition
`WallCrawlApplication` injects, and the bundled catalog carries 182 `AI_ACCEPTED`
records, 0 `APPROVED` records, 29 `DRAFT` records, 56 catalog entries with no
authored block, and 35 entries outside automatic-strength scope.
`ProductionPlannerCompositionTest` and `TodayProductionLifecycleTest` exercise
this exact composition end to end; the counts and outcomes below are read from
those suites, not from a synthetic promotion.

### Accepted cohort

The 182 `AI_ACCEPTED` IDs named in
[the exact partition](reviewed-exercise-metadata.md#ai-acceptance-audit). Deliberately
outside it, and unavailable to automatic planning until separately resolved:

- the 29 authored `DRAFT` IDs and 56 IDs with no authored block at all — 85 pending IDs
  in total — including `banded-row`'s unresolved anchor, `barbell-deadlift`'s and
  `sumo-deadlift`'s unratified single primary, `trap-bar-deadlift`'s unrepresented
  implement, and the four additional intrinsic holds the audit withheld
  (`cable-kickback`, `cable-standing-hip-abduction`, `cable-standing-hip-adduction`,
  `fire-hydrant`);
- the 35 IDs outside automatic-strength scope (stretches, distance-duration and other
  timed conditioning work).

All 302 remain browseable and manually selectable. Exclusion from the cohort removes an
exercise from automatic planning only.

### Supported profiles

Candidate counts below come from `ProductionPlannerCompositionTest` against the actual
bundled catalog, with `PlannerFeatureFlags.PRODUCTION` and no synthetic approval of any
kind. Every listed profile reaches a plan built only from real `AI_ACCEPTED` records and
passes `ProgramValidator` with repair disabled or repaired, as recorded.

| Profile | Candidates | Outcome |
| --- | ---: | --- |
| Bodyweight only | 33 | Served with a valid proposal |
| Dumbbells + bench | 72 | Served with a valid proposal |
| Machines + cable + bench | 51 | Served with a valid proposal |
| Full gym | 163 | Served with a valid proposal |
| Full gym, every capability `LIMITED` | 163 | Served; soft capability penalty applies, no candidate removed |
| Returning after a 26-week break | 163 | Served; returner caps retained |
| Mixed-unit history (logged in lb, prefers kg) | 164 | Served; existing load preserved, no invented load |
| Sparse history (one prior session) | 163 | Served; null load retained where evidence is absent |
| Band-only (`Resistance Band` only) | 11 | Served; no genuine `Chest` push exists, so the session is labelled by what it actually trains |
| Inventory matching no accepted record (`Cardio` only) | 0 | `NO_APPROVED_METADATA` — the remaining 120 pending/outside-scope records are the last ones standing, not the equipment |
| Every catalog exercise excluded by the user | 0 | `USER_EXCLUSIONS_REMOVED_ALL` |
| Any selected joint sensitivity | 0 | `TRAINING_CONSTRAINTS_REMOVED_ALL` |

Avoiding one capability removes exactly the accepted exercises that require it without
stopping the plan; avoiding **every** capability still leaves a plan, because accepted
records that declare no capability requirement are unaffected by any capability answer —
`CAPABILITIES_REMOVED_ALL` is not reachable from this cohort, and the corpus does not
assert it as an unsupported case.

### Not supported at this rollout

- **Any selected joint sensitivity.** The audit found no way to record a non-empty
  `clearedTrainingConstraints` list for any of the 182 accepted records (see
  [the exact partition](reviewed-exercise-metadata.md#ai-acceptance-audit)), so selecting
  one still produces the typed `TRAINING_CONSTRAINTS_REMOVED_ALL` refusal above. This is
  missing reviewed content, not a contradictory user request, and there is no legacy
  fallback.
- **Band-only chest or push work.** No band chest exercise exists in the pinned source.
  The band-only session above is labelled by what it actually trains and reports `Chest`
  as an unavailable priority. See the [coverage report](reviewed-catalog-coverage.md).
- **The six fixed-anchor band variants** without their explicit setup confirmations, and
  `banded-row` under any inventory (its own anchor is unresolved and stays pending).
- **Progression and deload.** The rollout is deliberately conservative: dose, effort and
  rest come from the existing `STATE_BASED_DOSE_EFFORT_REST_V1` policy and the current
  `UNCALIBRATED`/`RETURNING`/`BUILD` states only. There is no one-variable progression and
  no deload offer; those remain Roadmap Package 9.
- **Genuine human `APPROVED` review.** Zero records carry it. `AI_ACCEPTED` is an
  owner-authorized categorical acceptance, not a clinical or safety-for-everyone claim,
  and nothing here promotes one into the other.

## Rollout and manual-workout preservation

`WorkoutGenerationContext.automaticEligibilityResult` and
`WorkoutGenerationContext.capabilityEvidence` are populated on every production build.
A prior test-only build that disabled the flag left `automaticEligibilityResult` `null`,
`capabilityEvidence` equal to `CapabilityEvidenceSet.empty()`, and `ExerciseFilter`
supplying the legacy candidate list unchanged; that configuration remains available to
tests but is no longer what `WallCrawlApplication` composes.

The reviewed gate exists only on automatic context construction. The exercise
library still reads the full catalog, and the manual template editor still reads
all 302 exercises and displays its existing profile-equipment warnings. Missing,
`DRAFT`, or unaccepted reviewed metadata does not hide a browse or manual option.

## Deliberately incomplete work

Deterministic capability evidence and soft capability-penalty suppression are live on
the production reviewed path today. Package 8's supported-regression ranking slice is
also live there: `SupportedRegressionRankingPolicy` reads `Exercise.acceptedMetadata()`
on both the source and the target, so a source's `LIMITED` preference can prefer an
accepted `SUPPORTED` regression whether either record is `APPROVED` or `AI_ACCEPTED`
(#77). No genuine human approval record exists for any of it.

Task 6B remains open: there is no `ProgressionEngine.kt`, no one-variable
progression, and no broader derived-state rollout beyond
`AdaptationStatePolicy`'s current `UNCALIBRATED`/`RETURNING` outputs.

Task 6C remains open: there is no `DeloadOfferPolicy.kt`, no user-controlled
`DeloadOffer`, and no multi-session deload state machine.

Selected joint sensitivities are mapped but unpopulated: the
`clearedTrainingConstraints` contract exists in the schema, the importer, the Android parser
and the eligibility policy, and every bundled record clears nothing. Filling it in is part of
the same human sign-off pass, not separate work.

The next enablement requirement is genuine human `APPROVED` review of the metadata —
not another flag change, since the reviewed path is already enabled and already reads
the audited `AI_ACCEPTED` cohort. Human review would let those records (or a wider
cohort) carry `APPROVED` instead, and would be the first point at which
`clearedTrainingConstraints` could honestly become non-empty for any of them. Approval
must not happen automatically as a side effect of catalog growth, pull-request review,
or capability evidence.
