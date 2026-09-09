# Reviewed catalog equipment and profile coverage

## Interpretation

This report separates catalog classification, content readiness, metadata approval,
candidate availability, selected movements and whole-proposal validation. None is a
substitute for the others, and none establishes clinical suitability.

The pinned catalog contains 302 entries. The current importer and
`FakeWorkoutPlanner` classify 267 as type-supported automatic strength work and
exclude 35: 14 stretches, 10 distance-duration entries and 11 other timed
conditioning entries. That does **not** mean 267 entries have complete evidence,
approved metadata, compatible equipment or a place in every generated workout.

Production `PlannerFeatureFlags.reviewedCapabilityEligibility` remains `false`.
No human metadata approvals have been supplied. Prospective tests copy DRAFT
records to explicitly synthetic approvals **in memory only**. They are a structural
upper-bound experiment, not the actual approved cohort or a content-readiness claim.

## Reproducible band-only PUSH gap

> **Status (2026-09-08):** two of the three problems recorded below are now fixed, in two
> separate changes. The anchored band variations no longer pass the equipment filter
> ([fixed-anchor enforcement](#fixed-anchor-enforcement)), and the session is no longer
> mislabelled `PUSH` ([post-fix result](#post-fix-result-2026-09-08)). The third is
> unchanged: the band-only inventory still contains **no chest or push work at all**, and
> nothing here created any. Only the [post-fix result](#post-fix-result-2026-09-08) table
> describes current behavior; every other table in this section is a dated baseline,
> including the fixed-anchor replay, whose split and validation columns predate the focus
> fix.

The following baseline was captured before this review expanded or corrected the
37-record DRAFT cohort, on catalog commit
`ba0b709cb20430361b2cb33aaadd20998164a916` and application baseline `0f79810`.
The test-only profile is:

```json
{
  "goals": ["BUILD_MUSCLE"],
  "experienceLevel": "BEGINNER",
  "preferredDurationMinutes": 40,
  "daysPerWeek": 4,
  "availableEquipment": ["Resistance Band"],
  "preferredUnit": "LBS",
  "musclePriorities": {"Chest": "HIGH"},
  "excludedExerciseIds": [],
  "trainingConstraints": [],
  "returningAfterBreakWeeks": 0,
  "confirmedStartingLoads": {},
  "movementCapabilities": {
    "IMPACT": "COMFORTABLE",
    "FLOOR_TRANSITION": "COMFORTABLE",
    "UNSUPPORTED_SQUAT": "COMFORTABLE",
    "UPPER_BODY_BODYWEIGHT_PUSH": "COMFORTABLE",
    "VERTICAL_PULL_OR_HANG": "COMFORTABLE",
    "BALANCE_WITHOUT_SUPPORT": "COMFORTABLE",
    "CONTINUOUS_ACTIVITY": "COMFORTABLE"
  }
}
```

There is no exercise history or completed session history. `completedWorkoutCount`
is 0, each comparison starts a fresh planner with generation index 0, and the
declared `requiredMovementPatterns` set is empty. The synthetic reviewed experiment
uses `BUILD`, an empty `PRIMARY_ONLY_V1` ledger and review-policy version 1.
The legacy experiment has no reviewed eligibility result or program state.
Bodyweight access is not silently added to the explicitly band-only inventory.

### Pre-fix baseline: pre-expansion results (2026-09-05)

| Mode | Candidate count | Synthetic approvals | Actual split | Selected IDs | Genuine direct-primary push selected |
| --- | ---: | ---: | --- | --- | ---: |
| Legacy, production-disabled reviewed path | 19 | 0 | `PUSH` | `band-pull-apart`, `banded-dead-bug`, `banded-face-pull`, `banded-pallof-press`, `banded-woodchop` | 0 |
| Synthetic reviewed structural upper bound | 5 | 37 | `PUSH` | `band-pull-apart`, `banded-pallof-press` | 0 |

The five synthetic-reviewed candidates were `band-pull-apart`,
`banded-glute-bridge`, `banded-pallof-press`, `banded-row` and `banded-squat`.
The 19 legacy candidates were those five plus `banded-clamshell`, `banded-dead-bug`,
`banded-donkey-kick`, `banded-face-pull`, `banded-fire-hydrant`, `banded-frog-pump`,
`banded-hip-thrust`, `banded-kickback`, `banded-lat-pulldown`,
`banded-lateral-walk`, `banded-monster-walk`, `banded-seated-hip-abduction`,
`banded-standing-hip-abduction` and `banded-woodchop`.

Both proposals passed the existing `ProgramValidator`. Its default session
contract does not require anatomical push coverage. The initial targeted test
requiring a genuine selected push failed, correctly distinguishing this gap from
schema validity. No direct-primary or movement labels were changed to manufacture
a push.

### Two separate follow-ups

**Split/goal coverage — resolved 2026-09-07, see the [post-fix result](#post-fix-result-2026-09-08):**
the legacy planner could label a session `PUSH` without selecting genuine push work.
Split membership considered descriptive secondary muscles; shoulder involvement in a row
or anti-rotation movement therefore made that split fillable. This was a concrete
planner/product follow-up, not a claim that schema-valid proposals satisfy the requested
movement goal. No planner or validation policy was changed by this catalog review itself;
the fix landed separately and changed no catalog fact.

**Fixed-anchor equipment:** some band variations require a fixed anchor that a
generic `Resistance Band` inventory does not establish. An incomplete equipment
alternative can overstate availability even if the movement label is correct.
The per-ID review must retain an explicit pending disposition wherever the
equipment vocabulary cannot truthfully describe the demonstrated setup.
Neither `Wall` nor `Doorway` is a substitute for an unspecified load-bearing
anchor. The lack of an anchor token must not be hidden by relabeling the exercise
or reporting the old synthetic candidate count as usable band-only coverage.

Resolving an anchor representation would not create a missing band chest push.
Likewise, changing split selection would not supply missing equipment. These are
separate decisions, and production human-approved reviewed coverage remains zero.

### Pre-fix baseline: post-expansion results (2026-09-07, before the focus fix)

The same band-only profile and 0/0 completion/generation state were replayed
against the expanded 211-DRAFT catalog, including a separate experiment that
synthetically approves only the 186 AI-ready IDs. Known pending proposals are
excluded from that second experiment; **neither experiment creates human approval**.

| Mode | Candidate count | Synthetic approvals | Actual split | Selected IDs | Genuine direct-primary push selected |
| --- | ---: | ---: | --- | --- | ---: |
| Legacy, reviewed feature disabled | 19 | 0 | `PUSH` | `band-pull-apart`, `banded-dead-bug`, `banded-face-pull`, `banded-pallof-press`, `banded-woodchop` | 0 |
| All-DRAFT structural upper bound | 12 | 211 | `PUSH` | `band-pull-apart`, `banded-dead-bug` | 0 |
| AI-ready synthetic subset | 11 | 186 | `PUSH` | `band-pull-apart`, `banded-dead-bug` | 0 |

All proposals were structurally valid, but the explicit push-coverage result is
**false**. Removing incomplete fixed-anchor drafts did not remove valid push
coverage: there was no source-backed band push to begin with. The six withheld
anchor-dependent IDs are `banded-face-pull`, `banded-kickback`,
`banded-lat-pulldown`, `banded-pallof-press`, `banded-row` and `banded-woodchop`.
They remain in the catalog and ledger, not in the proposed reviewed metadata pool.

### Fixed-anchor enforcement

The earlier tables are historical observations before the equipment correction.
At application baseline `933c927`, new regressions reproduced all six IDs passing
the real legacy filter with Band + Wall + Doorway + Chair. The real context-builder
and planner selected `banded-face-pull`, `banded-pallof-press` and
`banded-woodchop` from a band-only profile.

The active filter now applies the [source-bound setup contract](band-anchor-equipment.md).
The same band-only inventory has **13 candidates rather than 19**:
`band-pull-apart`, `banded-clamshell`, `banded-dead-bug`, `banded-donkey-kick`,
`banded-fire-hydrant`, `banded-frog-pump`, `banded-glute-bridge`,
`banded-hip-thrust`, `banded-lateral-walk`, `banded-monster-walk`,
`banded-seated-hip-abduction`, `banded-squat` and
`banded-standing-hip-abduction`. None of the six anchored variations is selected.
This is only the fixed-anchor correction, not a new audit of the other setups.

The original 40-minute, Chest HIGH, beginner band-only profile at completion/
generation state 0/0 was also replayed through the real planner and validator.
**Both rows below are dated observations at `86053e67`, before the focus fix; the
`Split` and `Structural validation` columns no longer describe current behavior —
see the [post-fix result](#post-fix-result-2026-09-08).**

| Legacy replay (at `86053e67`) | Candidates | Selected IDs | Split | Structural validation | Genuine direct-primary push |
| --- | ---: | --- | --- | --- | ---: |
| Before correction | 19 | `band-pull-apart`, `banded-dead-bug`, `banded-face-pull`, `banded-pallof-press`, `banded-woodchop` | `PUSH` | Valid | 0 |
| After correction | 13 | `band-pull-apart`, `banded-dead-bug` | `PUSH` | Valid, no repair | 0 |

The after result contains no invented load and does not expand the inventory.
The unchanged `PUSH` label was evidence of the separate workout-focus bug, not a
claim that this is a successful chest-push workout. That label is what the
[post-fix result](#post-fix-result-2026-09-08) corrects: the same profile now
advertises `UPPER_BODY`, and a `PUSH`-labelled proposal built from those two ids is
rejected with `UNSUPPORTED_WORKOUT_FOCUS`. The candidate count is unaffected.

The following outcomes concern only the six anchor-dependent IDs. The matrix
uses the real filter and separately labelled synthetic equipment-policy approvals,
not production approvals; all other constraints are absent.

| Explicit inventory | Legacy anchored candidates | Synthetic-reviewed anchored candidates |
| --- | --- | --- |
| Resistance Band only | None | None |
| Band + Wall + Doorway + Chair, or generic Full gym | None | None |
| Anchor setups without Resistance Band | None | None |
| Band + upper-body anchor | `banded-face-pull`, `banded-pallof-press` | Same |
| Band + overhead anchor, without Chair | None | None |
| Band + overhead anchor + Chair | `banded-lat-pulldown` | Same |
| Band + low anchor | `banded-woodchop` | Same |
| Band + low anchor + kickback attachment/support | `banded-kickback`, `banded-woodchop` | Same |
| Band + all four setup confirmations + Chair | All five above; never `banded-row` | Same |

The upper-body confirmation explicitly covers adjustable chest-through-face
height and forward/lateral positioning. Kickback additionally needs the ankle
connection and reachable handhold on the same low-anchor assembly. The row's
anchor is outside every source frame; it remains unresolved and automatically
unavailable even with all equipment selected. It is still browseable and manually
selectable with an unresolved-setup warning.

No full reviewed blocks or graph edges were added. The five representable runtime
minimums do not approve their other categorical fields, and the held/rejected
relationships retain their separate movement/shape/evidence decisions. The
production reviewed pool remains zero, with 211 DRAFT records and the feature
flag disabled. Synthetic approval tests also reject incomplete Band-only
alternatives rather than letting them bypass the source-bound minimum.

The workout-focus bug remains separate: removing anchored candidates does not
create a genuine band chest push or change split selection, titles or fallback
focus rules. Candidate legality is not proof of anatomical push coverage.

### Post-fix result (2026-09-08)

The `PUSH` label the section above deliberately left in place is now corrected. Split
selection, exercise selection, the focus-muscle line and whole-program validation share
one rule: a split is genuinely trained when a strength exercise's **own-purpose** muscle —
the approved `directPrimaryMuscle` where the reviewed contract applies, the legacy
`primaryMuscles` otherwise — is one of the split's target muscles. Descriptive secondary
muscles no longer establish a split.

The identical profile, catalog and 0/0 completion/generation state were replayed with both
corrections in place. The candidate counts are the fixed-anchor result above, unchanged by
this fix:

| Mode | Candidate count | Synthetic approvals | Actual split | Selected IDs | Genuine direct-primary push selected | Reported unavailable priority |
| --- | ---: | ---: | --- | --- | ---: | --- |
| Legacy, reviewed feature disabled | 13 | 0 | `UPPER_BODY` | `band-pull-apart`, `banded-dead-bug` | 0 | `Chest` |
| All-DRAFT structural upper bound | 12 | 211 | `UPPER_BODY` | `band-pull-apart`, `banded-dead-bug` | 0 | `Chest` |
| AI-ready synthetic subset | 11 | 186 | `UPPER_BODY` | `band-pull-apart`, `banded-dead-bug` | 0 | `Chest` |

All three cohorts now converge on the same selection, because the anchored pulls the legacy
pool used to add are gone. Reported focus muscles are `Upper Back` and `Core` — the work the
session actually contains.

Neither correction created anything. No band chest exercise exists, no anchor token was
invented, Bodyweight was not added to the explicitly band-only inventory, and the eligible
pool is exactly what the fixed-anchor contract admits. What changed is that the session is
labelled with a split its own exercises train, and `Chest` is reported as an unavailable
priority so the reader is told why the emphasis they asked for is missing.
`ProgramValidator` rejects the old proposal with `UNSUPPORTED_WORKOUT_FOCUS`, so it can no
longer reach display or persistence.

The two gaps this report has always separated are now in different states. **Mislabelling:
fixed.** **Missing band chest work: still open**, and unaffected — correcting a label
supplies no exercise, exactly as correcting the anchors supplied none.

## Full-pool prospective profiles

These are 16 declared test profiles, not an exhaustive capability/equipment
Cartesian product or a rollout approval. The first two candidate columns report
actual eligibility from the entire catalog with the indicated synthetic cohort.
The selected column is the **AI-ready synthetic** result. Every success below
passed `ProgramValidator` without repair; typed no-plan outcomes remained explicit.

| Profile | All-DRAFT candidates | AI-ready candidates | AI-ready selected IDs or outcome |
| --- | ---: | ---: | --- |
| Bodyweight, uncalibrated push | 38 | 34 | `diamond-push-up`, `push-up`, `wide-push-up` |
| Band-only push gap | 12 | 11 | `band-pull-apart`, `banded-dead-bug`; no genuine push, so the session is labelled `UPPER_BODY` and reports `Chest` unavailable |
| Dumbbells + bench | 38 | 37 | `arnold-press`, `dumbbell-bench-press`, `dumbbell-shoulder-press`, `dumbbell-fly`, `dumbbell-lateral-raise` |
| Machines | 28 | 26 | `machine-chest-press`, `machine-shoulder-press`, `pec-deck`, `machine-lateral-raise`, `captains-chair-knee-raise` |
| Full gym | 211 | 186 | `barbell-bench-press`, `overhead-press`, `incline-bench-press`, `cable-fly`, `cable-lateral-raise`, `cable-triceps-pushdown` |
| LIMITED bodyweight push | 46 | 41 | `diamond-push-up`, `pike-push-up`, `push-up`; relevant policy set cap retained |
| AVOID bodyweight push | 34 | 31 | `prone-y-raise`, `side-plank`, `bird-dog`; avoided demand excluded |
| AVOID standing balance, 35 minutes | 133 | 117 | `barbell-bench-press`, `dumbbell-shoulder-press`, `dumbbell-fly`; wrist variants and inchworm excluded |
| AVOID floor transition, 35 minutes | 152 | 136 | `barbell-bench-press`, `overhead-press`, `cable-fly`; wall-handstand-push-up excluded |
| Returning | 79 | 73 | `arnold-press`, `dumbbell-bench-press`, `dumbbell-shoulder-press`, `dumbbell-fly`, `dumbbell-lateral-raise`; returner caps retained |
| Mixed-unit history | 88 | 81 | `arnold-press`, `dumbbell-bench-press`, `dumbbell-shoulder-press`, `dumbbell-fly`, `dumbbell-lateral-raise` |
| Sparse history | 58 | 52 | `band-pull-apart`, `prone-y-raise`, `reverse-snow-angel` |
| Uncalibrated full gym | 185 | 167 | `barbell-bench-press`, `overhead-press`, `arnold-press`, `cable-fly`, `cable-lateral-raise`, `cable-triceps-pushdown` |
| Selected joint constraint | 0 | 0 | `REVIEWED_ELIGIBILITY_NO_CANDIDATES` / `TRAINING_CONSTRAINTS_REMOVED_ALL` |
| Empty equipment inventory | 0 | 0 | `REVIEWED_ELIGIBILITY_NO_CANDIDATES` / `NO_APPROVED_METADATA` |
| Unmodified catalog, no synthetic approvals | 0 | 0 | `REVIEWED_ELIGIBILITY_NO_CANDIDATES` / `NO_APPROVED_METADATA` |

For empty equipment, every synthetically approved record carries
`MISSING_EQUIPMENT`. The aggregate is nevertheless `NO_APPROVED_METADATA` under
the existing staged algorithm because the remaining unapproved catalog records
survive the equipment stage before being removed at the approval stage. This
report preserves the observed reason rather than relabeling it; no fallback
workout is produced.

The mixed-unit case uses explicit completed test sessions in pounds and kilograms:
the most recent 27.5 kg dumbbell bench press stays 27.5 kg, two completed work sets
are credited to its synthetic approved primary, and no new starting load is
invented. Sparse history retains a null load. Input snapshots and normalized
replays remain unchanged. Sixteen separate disabled-mode comparisons preserve
legacy recommendations when reviewed metadata is stripped from the same catalog.
The new standing-balance avoidance case has an explicitly declared 35-minute
duration. A separate 70-minute exploration produced an over-allowance raw proposal
that depended on the existing validator repair; that longer case is **not**
claimed to satisfy unrepaired validity. The focused case proves the observed
standing demands are respected, including wrist-curl and wrist-extension; it
does not turn the matrix into a guarantee for every duration or inventory.

## Directed relationship review

All 21 pre-integration edges and every explicit candidate relationship mentioned
in the per-ID assessments received an endpoint-specific decision: 265 decisions
in total, currently comprising 66 additions, 19 retained proposals, 77 holds and 103
rejections. The metadata contains **85 DRAFT relationships: 35 regressions and
50 substitutions**. The ledger records each decision, its rationale, comparison
and current source/target metadata fingerprints. These are not human-approved
relationships despite the existing schema's `approvedRegressions` and
`approvedSubstitutions` field names.

The barbell-deadlift to dumbbell-RDL relation stays held for the unresolved
single-primary and conventional-versus-RDL decision. Two previous relations were
withheld rather than carried forward mechanically: pull-ups to negative-pull-up
(eccentric-only execution/top-position access unresolved) and side-plank to plank
(mixed full/knee-support illustrations). Later image reconciliation also withheld
commando-pull-up to pull-ups and skater-squat to pistol-squat: the pictures did
not establish the grip/stance premises used by those relationships.
The single-arm-cable-row to seated-row regression was also held after its mixed
held/unheld support frames invalidated the stated premise. Current adjacency
sentences are generated from emitted edges; individual semantic rationales and
comparisons remain in `graphDecisions`, with prior narrative versions explicitly historical.
No new edge targets a pending record,
and matching categorical graph predicates never establishes equal muscle stimulus
or modality outcomes.

## Remaining enablement decisions

Human field-by-field approval is still required for every proposed record.
The 81 pending entries identify additional issues, including unsupported fixtures,
uncertain depicted variants, single-primary allocations and unresolved impact
categories; 56 have no proposed metadata block. The 35 artwork-reference
restrictions are recorded per ID with exact pinned paths, not fixed by swapping
images or names.

The current enum also cannot express every machine subtype, bench adjustment,
attachment or conditional support arrangement. A generic category match is not
proof that a particular apparatus is available. Resolving those representation
and persona requirements, the band-only push gap, selected joint mappings,
human sign-off and the remaining release corpus are separate gates. Neither
302 completed AI reviews nor 48 passing software cases closes Roadmap Package 3
or enables the reviewed planner.

## Reproducibility boundary

`ReviewedCatalogCoverageTest` uses the existing fixture loader and catalog
projection, the real eligibility policy, planner, prescription policy, ledger and
whole-program validator. Its report files record profile settings, candidate IDs,
selected IDs, split, prescriptions, typed validation results, synthetic approval
IDs and input non-mutation. Comparisons use fresh planners and normalize only
the generated workout ID.

The existing corpus's curated subsets are not substituted for full-pool
availability. Disabled-mode comparisons use the same catalog with only
`reviewedMetadata` stripped, preserving all original catalog facts and legacy
programming values. Passing that comparison establishes recommendation invariance
under draft metadata changes; it does not approve the draft content.

## Single-candidate reachability is not cohort approval

A separate baseline probe invoked the real legacy `FakeWorkoutPlanner` once for
each of the 302 parsed exercises, supplying that exercise as the **sole candidate**.
The declared synthetic profile owns all 19 existing equipment categories, is
advanced, has strength/build-muscle goals, a 70-minute preference, five days per
week and Chest HIGH, with all capabilities comfortable and no constraints. Each
call starts at completed count/generation index 0/0. The profile has explicit
test-only starting loads for barbell squat and bench press; these are not
inferred defaults. Reviewed eligibility/program state are absent.

| Actual planner outcome | Count |
| --- | ---: |
| Sole candidate selected | 267 |
| `NO_STRENGTH_CANDIDATES` | 35 |
| `NO_CANDIDATES_FOR_ANY_SPLIT` | 0 |

This establishes that the type-supported set can reach a legacy strength slot
when deliberately isolated. It does not establish selection from a full pool,
complete equipment representation, movement-goal coverage, reviewed readiness or
human approval. The per-ID evidence ledger retains the observed outcome and
selected split rather than replacing them with an inferred classifier result.
