# 2026-08-30 Exercise Metadata Agent Review

This record summarizes the documentation-side review packet for the 37-entry `reviewedMetadata` draft cohort. It documents model review and the authoritative draft corrections already present in `tools/workout-guide/reviewed-metadata.json`; it does **not** approve metadata. These reviewers are model agents, not human experts.

## Scope, authority, and evidence boundary

- Reviewed draft source: `tools/workout-guide/reviewed-metadata.json`
- Generated cohort snapshot read during packet work: `docs/reviewed-exercise-metadata-review.md`
- Review plan read during packet work: `docs/superpowers/plans/2026-08-30-exercise-metadata-review-packet.md`
- Authoritative upstream repository: `https://github.com/bryllim/workout-guide`
- Authoritative upstream commit: `ba0b709cb20430361b2cb33aaadd20998164a916`
- Source correction: the user-provided checkout was newer than WallCrawl's pin, so reviewers read the pinned commit directly without resetting that checkout.
- Round 2 resolved semantics from exact product copy plus pinned manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916`.
- Round 3 ended with all four reviewers approving the final correction resolution.
- Current state remains unchanged: all 37 entries are `reviewState=draft`, `provenance.reviewerRole=null`, and `provenance.reviewedAtEpochMillis=null`.

## Method and lenses

| Agent | Model | Primary lens | Method note |
| --- | --- | --- | --- |
| Opus | Claude Opus 4.8 | Resistance-training dose and program construction | Reviewed all 37 entries independently, then joined convergence and final approval. |
| Grok | Grok 4.6 | Capability, support, impact, and inclusive-access semantics | Reviewed all 37 entries independently, then challenged capability/support assumptions in convergence. |
| Gemini | Gemini 3.7 Flash | Equipment, biomechanics, regressions, and substitutions | Reviewed all 37 entries independently, then confirmed the final correction resolution. |
| Terra | GPT-5.6 Terra | Provenance, deterministic-policy compatibility, and evidence boundaries | Reviewed all 37 entries independently, revised insufficiency calls, then approved the final correction resolution. |

Review sequence:

1. **Round 1**: all four agents independently reviewed all 37 entries.
2. **Round 2**: disagreements were rechecked against exact product copy, pinned manifest/artwork, and WallCrawl product-policy fields.
3. **Round 3**: all four agents approved the final correction resolution now present in the JSON draft.

Approval guardrails held throughout: model consensus, Markdown checkboxes, pull-request approval, and merge do not approve metadata. Human approval requires a later deliberate authored-data change with a real reviewer role, real review time, and updated provenance.

## Round 1 disagreement summary

All four agents reviewed the full 37-entry cohort.

| Agent | Model | Ready as written | Correction required | Insufficient evidence |
| --- | --- | ---: | ---: | ---: |
| Opus | Claude Opus 4.8 | 36 | 1 | 0 |
| Grok | Grok 4.6 | 16 | 21 | 0 |
| Gemini | Gemini 3.7 Flash | 37 | 0 | 0 |
| Terra | GPT-5.6 Terra | 18 | 5 | 14 |

Round-1 disagreement pattern:

- Opus was nearly entirely affirmative and raised only one correction.
- Grok challenged most support, capability, and impact assignments.
- Gemini accepted the cohort as-written in Round 1.
- Terra initially treated several semantics as unresolved rather than approved, then revised those calls during Round 2 once exact product copy and product-policy meanings were pinned down.

## Final semantic definitions used in Round 2 convergence

### Impact and capability semantics

| Capability / concept | Exact product-copy definition | Final review rule |
| --- | --- | --- |
| impact | Movements that include landing, hopping, or other impact. | Only assign a non-`none` impact level when exact product copy/artwork actually shows that property. Under the final correction set, every one of the 37 entries stayed `impactLevel=none`. |
| floor_transition | Moving down to the floor and returning to standing. | Keep only when the authored movement requires that transition. |
| unsupported_squat | Lowering into and rising from a squat without holding a support. | Keep only when normal execution requires an unsupported squat. |
| upper_body_bodyweight_push | Supporting and pushing some of your bodyweight with your arms. | Keep only when the movement itself requires that bodyweight push demand. |
| vertical_pull_or_hang | Hanging from or pulling toward an overhead bar or handles. | Keep only when the movement itself requires hanging from or pulling toward overhead handles/bar. |
| balance_without_support | Standing or moving while balancing without holding a support. | Keep only when the movement requires unsupported standing or balance. |
| continuous_activity | Staying active continuously for several minutes at a comfortable pace. | Present in product policy but unused by this 37-entry cohort. |

### Support semantics

| Support value | Final semantic definition |
| --- | --- |
| supported | The authored movement uses external support or a support surface as part of normal setup or execution (for example machine, bench, wall, chair, or built-in assistance). |
| optional_support | Support can assist but is not required by the authored movement. The schema still permits this value, but none of the 37 final entries retained it after Round 2. |
| unsupported | Normal execution does not require external support or a required support hold. |

Additional convergence rules:

- `draft` is an approval state, **not** an insufficiency verdict.
- Complexity remains an independent WallCrawl policy tier; it was not auto-rewritten from upstream wording.
- No medical, BMI, injury-prevention, impingement, axial-load, SFR, or modality-equivalence claims were accepted into this packet.

## Accepted correction ledger

There are **22** unique entries with categorical changes. Provenance rationale text was corrected for **all 37** entries.

### Impact corrections: 14 entries changed from `low` to `none`

| Exercise ID | Field | Old | New |
| --- | --- | --- | --- |
| assisted-pistol-squat | `impactLevel` | `"low"` | `"none"` |
| banded-squat | `impactLevel` | `"low"` | `"none"` |
| barbell-back-squat | `impactLevel` | `"low"` | `"none"` |
| barbell-deadlift | `impactLevel` | `"low"` | `"none"` |
| bodyweight-squat | `impactLevel` | `"low"` | `"none"` |
| cable-pull-through | `impactLevel` | `"low"` | `"none"` |
| dumbbell-romanian-deadlift | `impactLevel` | `"low"` | `"none"` |
| goblet-squat | `impactLevel` | `"low"` | `"none"` |
| kettlebell-romanian-deadlift | `impactLevel` | `"low"` | `"none"` |
| leg-press | `impactLevel` | `"low"` | `"none"` |
| pistol-squat | `impactLevel` | `"low"` | `"none"` |
| smith-machine-romanian-deadlift | `impactLevel` | `"low"` | `"none"` |
| smith-machine-split-squat | `impactLevel` | `"low"` | `"none"` |
| split-squat | `impactLevel` | `"low"` | `"none"` |

### Capability-array corrections: 7 entries

| Exercise ID | Field | Old | New |
| --- | --- | --- | --- |
| assisted-pistol-squat | `capabilityRequirements` | `["unsupported_squat"]` | `[]` |
| banded-lat-pulldown | `capabilityRequirements` | `["vertical_pull_or_hang"]` | `[]` |
| plank | `capabilityRequirements` | `["floor_transition", "upper_body_bodyweight_push"]` | `["floor_transition"]` |
| push-up | `capabilityRequirements` | `["upper_body_bodyweight_push", "floor_transition", "balance_without_support"]` | `["upper_body_bodyweight_push", "floor_transition"]` |
| side-plank | `capabilityRequirements` | `["floor_transition", "upper_body_bodyweight_push", "balance_without_support"]` | `["floor_transition"]` |
| smith-machine-split-squat | `capabilityRequirements` | `["unsupported_squat"]` | `[]` |
| split-squat | `capabilityRequirements` | `["unsupported_squat", "balance_without_support"]` | `["balance_without_support"]` |

### Support-value corrections: 6 entries

| Exercise ID | Field | Old | New |
| --- | --- | --- | --- |
| band-pull-apart | `supportRequirement` | `"optional_support"` | `"unsupported"` |
| banded-lat-pulldown | `supportRequirement` | `"optional_support"` | `"supported"` |
| banded-pallof-press | `supportRequirement` | `"optional_support"` | `"unsupported"` |
| banded-row | `supportRequirement` | `"optional_support"` | `"unsupported"` |
| cable-pallof-hold | `supportRequirement` | `"optional_support"` | `"unsupported"` |
| cable-pull-through | `supportRequirement` | `"optional_support"` | `"unsupported"` |

### Equipment correction: 1 entry

| Exercise ID | Field | Old | New |
| --- | --- | --- | --- |
| banded-lat-pulldown | `equipmentAlternatives` | `[["Resistance Band"]]` | `[["Resistance Band", "Chair"]]` |

### Provenance-rationale corrections: all 37 entries

Affected exercise IDs: assisted-pistol-squat, assisted-pull-up, band-pull-apart, banded-glute-bridge,
banded-lat-pulldown, banded-pallof-press, banded-row, banded-squat, barbell-back-squat,
barbell-bench-press, barbell-deadlift, bodyweight-squat, cable-pallof-hold,
cable-pull-through, dead-hang, dumbbell-bench-press, dumbbell-bent-over-row,
dumbbell-romanian-deadlift, glute-bridge, goblet-squat, kettlebell-romanian-deadlift,
knee-push-up, leg-press, machine-chest-press, machine-row, negative-pull-up, pistol-squat,
plank, pull-ups, push-up, seated-row, side-plank, smith-machine-romanian-deadlift,
smith-machine-split-squat, split-squat, wall-push-up, wall-sit

Field corrected for every entry: `provenance.rationaleOrSource`.

Final rationale template content now names pinned Workout Guide commit `ba0b709cb20430361b2cb33aaadd20998164a916`, limits source-derived facts to manifest/artwork-supported muscles, exercise type, and listed equipment, attributes pattern/complexity/family/capabilities/support/impact/graph edges to WallCrawl product policy, and states that human field-by-field review is still required.

## Rejected proposals and rationale

- **Complexity remains independent policy.** Round 2 did not treat upstream copy as a mandate to rewrite `complexity`; that tier remains a WallCrawl policy classification.
- **`barbell-bench-press` keeps the rack requirement.** The authored movement begins from a racked bar over the torso, so `equipmentAlternatives=[["Barbell","Bench","Squat Rack"]]` stayed unchanged.
- **Graph edges stayed unchanged.** No exact-copy or pinned-artwork evidence required regression/substitution edits, and the current directed edges remained valid under existing graph rules.
- **`draft` did not become “insufficient evidence.”** `reviewState=draft` only records lack of human approval; it is not a synonym for unresolved semantics.
- **No unsupported scope creep.** Reviewers rejected adding injury-prevention, impingement, axial-load, SFR, modality-equivalence, BMI, or medical claims.

## Exact seated banded-lat-pulldown resolution

For `banded-lat-pulldown`, the final resolved fields are:

- `supportRequirement="supported"`
- `equipmentAlternatives=[["Resistance Band", "Chair"]]`
- `capabilityRequirements=[]`

Round 2 resolved this from exact product copy/artwork as a seated band movement using chair support, not as a vertical-hang or overhead-pull capability gate.

## Round 3 final 37-entry verdict list

| Exercise ID | Final verdict |
| --- | --- |
| assisted-pistol-squat | `READY_AFTER_CORRECTIONS` |
| assisted-pull-up | `READY_AS_WRITTEN` |
| band-pull-apart | `READY_AFTER_CORRECTIONS` |
| banded-glute-bridge | `READY_AS_WRITTEN` |
| banded-lat-pulldown | `READY_AFTER_CORRECTIONS` |
| banded-pallof-press | `READY_AFTER_CORRECTIONS` |
| banded-row | `READY_AFTER_CORRECTIONS` |
| banded-squat | `READY_AFTER_CORRECTIONS` |
| barbell-back-squat | `READY_AFTER_CORRECTIONS` |
| barbell-bench-press | `READY_AS_WRITTEN` |
| barbell-deadlift | `READY_AFTER_CORRECTIONS` |
| bodyweight-squat | `READY_AFTER_CORRECTIONS` |
| cable-pallof-hold | `READY_AFTER_CORRECTIONS` |
| cable-pull-through | `READY_AFTER_CORRECTIONS` |
| dead-hang | `READY_AS_WRITTEN` |
| dumbbell-bench-press | `READY_AS_WRITTEN` |
| dumbbell-bent-over-row | `READY_AS_WRITTEN` |
| dumbbell-romanian-deadlift | `READY_AFTER_CORRECTIONS` |
| glute-bridge | `READY_AS_WRITTEN` |
| goblet-squat | `READY_AFTER_CORRECTIONS` |
| kettlebell-romanian-deadlift | `READY_AFTER_CORRECTIONS` |
| knee-push-up | `READY_AS_WRITTEN` |
| leg-press | `READY_AFTER_CORRECTIONS` |
| machine-chest-press | `READY_AS_WRITTEN` |
| machine-row | `READY_AS_WRITTEN` |
| negative-pull-up | `READY_AS_WRITTEN` |
| pistol-squat | `READY_AFTER_CORRECTIONS` |
| plank | `READY_AFTER_CORRECTIONS` |
| pull-ups | `READY_AS_WRITTEN` |
| push-up | `READY_AFTER_CORRECTIONS` |
| seated-row | `READY_AS_WRITTEN` |
| side-plank | `READY_AFTER_CORRECTIONS` |
| smith-machine-romanian-deadlift | `READY_AFTER_CORRECTIONS` |
| smith-machine-split-squat | `READY_AFTER_CORRECTIONS` |
| split-squat | `READY_AFTER_CORRECTIONS` |
| wall-push-up | `READY_AS_WRITTEN` |
| wall-sit | `READY_AS_WRITTEN` |

Summary only: 22 entries are `READY_AFTER_CORRECTIONS`, 15 are `READY_AS_WRITTEN`, and all 37 are ready for human inspection but **not** approved.

## Sole unresolved human ratification question

- `barbell-deadlift`: the current draft uses `directPrimaryMuscle="Hamstrings"`, while the pinned Workout Guide source names `Posterior Chain`. Because WallCrawl requires a single direct primary, this is the sole explicit human ratification question left open by the packet.

## Human-approval boundary

Human approval must happen later through a deliberate authored-data change that records a real reviewer role, real review time, and final provenance. Until that happens, every entry remains draft metadata regardless of model consensus, Markdown checkbox state, pull-request approval, or merge.
