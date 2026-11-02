# Condition-aware planning design

**Status:** Approved on 2026-11-01. Amended the same day after the owner approved seven changes
from the [research consolidation](../../research/conditions/CONSOLIDATION.md), section 8. Where an
earlier draft differs, this text governs.

**Engine map:** [deterministic engine report](../../engine-map/deterministic-engine-report.html)

## Goal

Let a user report a musculoskeletal condition so that automatic workouts leave out the few movements
the research says to avoid, prefer alternatives to movements it says to modify, and add an optional
mobility routine for it. A user with piriformis syndrome, for example, should still get a useful
automatic workout rather than a refusal.

The research does not support banning most movements: 75 of its 83 restrictions say modify. A
modify exercise can therefore still appear, with a cue, when the plan has nothing better.

## Product position

This is scope B: working around a condition, plus optional mobility routines presented as
general information. It is not treatment.

WallCrawl cannot guarantee that a workout will not make a condition worse. Interface copy says
"chosen to work around" and never says "safe for", "helps heal", "treats" or "prevents".

This decision changes two recorded boundaries, and the implementation must record the change in
`ROADMAP.md` under Recorded decisions:

- Package 11's exclusion of medical and injury-prevention claims stays in force for claims, but
  condition names and condition-linked routines become allowed content.
- The human-only reservation on joint clearance is replaced for this overlay by the sign-off
  rule below. `clearedTrainingConstraints` and `APPROVED` keep their current meaning.

The app stays free of AI, as recorded in the 2026-10-25 product direction. No model runs in the
app, and every decision here comes from deterministic rules over bundled data. Agents assist only
with authoring content during development, and their provenance stays recorded because it is true.

## Current behaviour this replaces

- Any selected joint sensitivity removes every candidate, producing
  `TRAINING_CONSTRAINTS_REMOVED_ALL`, because no reviewed record clears any joint.
- `isStrengthWork()` rejects all 14 catalog stretches, and a generated session has only compound
  and accessory slots.
- `PAIN_STOP` holds progression like any other stopped set and has no other planning effect.

## Content overlay

Health content lives in a new bundled asset, `app/src/main/assets/conditions/condition-guidance.json`,
with a store and parser modelled on `ExerciseLocalizationStore` and `ExerciseLocalizationParser`.

The overlay was chosen over reviewed metadata v4 because the runtime requires exactly schema v3
and digests each accepted record, so a v4 would force a re-audit of all 182 accepted records. It
was chosen over Kotlin tables because content needs provenance and must change without code
changes.

The overlay holds:

| Section | Keyed by | Fields |
| --- | --- | --- |
| Movement demands | Exercise ID | Set of `MovementDemand` values; presence means this exercise was reviewed |
| Conditions | Condition ID | Avoid demands, modify demands, routine, exercise cues, notes, red flags, evidence grade, sources, review provenance |
| Joint labels | `TrainingConstraint` | Modify demands |
| Demand cues | `MovementDemand` | Cue text by language |

`MovementDemand` is a closed, categorical vocabulary of 21 values: the 26 in the consolidation minus
`THUMB_DOWN_ARM_RAISE`, `ARM_RAISE_ABOVE_90`, `BEHIND_NECK_PRESS_OR_PULL`, `NARROW_GRIP_BENCH` and
`FIGURE_FOUR_POSITION`, which become per-exercise cues on their condition. It carries no numeric
risk or joint-stress score, which the evidence review rules out.

Routine entries reference catalog exercise IDs and hold sets, then either a duration or reps with an
optional hold, plus rest. Doses come from a published source and are never invented; an exercise
without a usable dose stays out of the routine. Dose applies per session. The block runs with each
workout, so the app does not follow a trial's weekly schedule and does not claim to.

Notes hold the restrictions that are not exercise properties, such as stopping when leg pain
increases, as plain text by language. The app asks no new medical questions.

Approved routines:

| Condition | Routine |
| --- | --- |
| Piriformis syndrome | Figure-four stretch, lying piriformis stretch, glute bridge |
| Gluteal tendinopathy | Section 5 of the consolidation |
| Lumbar radiculopathy | Cat-cow, bird dog, glute bridge |
| Nonspecific low back pain | Section 5 of the consolidation |
| Rotator cuff-related shoulder pain | Cross-body shoulder stretch |
| Lateral elbow tendinopathy | Wrist extensor stretch |
| Carpal tunnel syndrome | Tendon gliding sequence |
| Patellofemoral pain | Side-lying hip abduction, partial-range squat, straight leg raise |

Nerve sliders are in no routine. `HIP_SENSITIVE` and the other joint labels have no routine. Every
routine stretch carries a stop-if-symptoms cue, and no copy claims a benefit from any routine.

The parser rejects unknown exercise IDs, unknown demands, missing provenance and oversized input.
A load failure produces a typed failure and disables condition-aware planning. It never treats a
user's conditions as absent.

## Sign-off

Overlay records carry AI review provenance in the style of `AI_ACCEPTED`: model, time, content
digest, source references, rationale and limitations. The owner authorizes this for alpha use.

A qualified clinician must review the content before any public release. The roadmap records this
as a release gate. This default was recommended in design and has not been separately confirmed;
the owner confirms it by approving this spec.

## Profile and persistence

- `UserProfile` gains `reportedConditions: Set<ConditionId>`, chosen from the shipped list with no
  free text. The user can edit it in Profile and during onboarding.
- Room moves to schema 17 with a migration and the archive format gains the field. Archive restore
  rejects unknown shapes under existing rules, and an unknown condition ID stays opaque rather than
  being dropped.
- `RecommendationContextIdentity` includes conditions and the overlay version, so a change makes a
  displayed plan out of date.
- Conditions stay local, under the existing backup exclusions. `docs/privacy.md` must describe them.

## Engine changes

### Eligibility

`ExerciseEligibilityPolicy` gains one hard rule in the training-constraint stage, using a shared
predicate:

| Case | Reason |
| --- | --- |
| Exercise demands intersect the user's avoid demands | `CONDITION_RESTRICTED_DEMAND` |
| User has a restriction and the exercise has no overlay entry | `CONDITION_DEMANDS_UNREVIEWED` |

Avoid and modify demands are each the union across selected conditions and joint labels. A demand
in both is treated as avoid. Both reasons report through a new aggregate failure,
`CONDITIONS_REMOVED_ALL`, with its own Today copy. A profile with no conditions and no joint labels
behaves exactly as it does today, and its plans do not change.

### Ranking

An eligible exercise with a modify demand ranks after one without, in both compound and accessory
ordering, directly after split focus. The rank is restricted or not, never a count. Each exercise
selected with a modify demand shows that demand's cue. No source tested this mechanism; it is app
design.

Joint labels change meaning: an exercise passes a label when it has an overlay entry, and ranks
lower when that entry has a modify demand. `LOW_IMPACT_ONLY` still uses `impactLevel`. The rule replaces
`clearsJointConstraintsOf` in all its callers, so the supported-regression exception and
`StateBasedTrainingPolicy` read one predicate. The second check returns
`TRAINING_CONSTRAINT_REACHED_POLICY` as it does today.

### Mobility block

`GeneratedWorkout` gains `mobilityBlock: List<PlannedExercise>`, empty unless a selected condition
has a routine.

Selection is deterministic: conditions in stable ID order contribute routine entries in authored
order, rotating by the same seed as the split, up to three items. Duplicates are removed. Entries
are subject to user exclusions, equipment and avoid demands, and the block is what remains. Modify
demands do not filter a routine, because a condition's routines can carry its own modify tags. An
avoid demand from any selected condition does, so a lying piriformis stretch is removed for someone
who also reports gluteal tendinopathy. The block never causes a plan to fail.

The block bypasses `isStrengthWork()` only through this path. Stretches stay out of compound and
accessory selection.

### Session role and accounting

`WorkoutExercise` gains a `role` of `MAIN` or `MOBILITY`, with existing rows migrated to `MAIN`.
Mobility work is logged with the existing set logging and must not count toward:

- the weekly dose ledger or scheduling evidence;
- split focus or focus muscles;
- progression or capability evidence;
- rep and volume analytics in Progress, which show it separately or not at all.

Each of these filters by role at its boundary. This is required because a routine may use accepted
strength exercises such as `clamshell`, which would otherwise earn credit.

### Validation

`ProgramValidator` checks the block: IDs exist, each entry belongs to a selected condition's
routine, no entry has an avoid demand, and the block respects the three-item limit. A violation
is typed and is not repairable. `WorkoutDurationEstimator` includes the block in the estimate.

### Pain feedback

After a `PAIN_STOP`, the logger offers to exclude that exercise from automatic plans through the
existing `excludedExerciseIds`. Nothing changes unless the user accepts, and nothing is inferred
from repeated stops. The planner still does not read `PAIN_STOP`.

### Manual templates

Templates still allow every exercise. The editor shows a warning next to any exercise with an
avoid or modify demand, and also when the exercise has no overlay entry. Nothing is hidden.

## Interface copy

- Adding a condition shows its red flags, in plain language with "see a clinician" guidance, and its
  notes, and requires acknowledgement. It does not block use.
- A reported condition never implies that an exercise without a restriction is safe.
- The Today card names the condition the mobility block relates to, as general information.
- All new copy exists in English and Spanish. Overlay text uses the same localization validation.
- `SafetyCopyTest` gains an explicit allowlist for condition names. Claim vocabulary stays
  forbidden, including treat, heal, cure, prevent and safe for. Onboarding safety copy is rewritten
  to describe the new behaviour accurately.

## Research pilot

The pilot runs first, because its output defines `MovementDemand` and the overlay's content. It
covers eight conditions:

1. Piriformis syndrome
2. Gluteal tendinopathy
3. Lumbar radiculopathy
4. Nonspecific low back pain
5. Rotator cuff-related shoulder pain
6. Lateral elbow tendinopathy
7. Carpal tunnel syndrome
8. Patellofemoral pain

Each condition gets one researcher and one independent citation checker, for 16 agents. Each
researcher produces `docs/research/conditions/<id>.md` and a matching JSON record with:

- evidence grade and how contested the diagnosis is;
- movements to avoid or modify, with a source per item;
- candidate exercises and stretches with dose, each marked in-catalog or new;
- red flags that need a clinician;
- every source with its DOI or PMID.

The checker opens each source and confirms that it says what the record claims. Any unsupported item
is removed, not softened. Research output is treated as untrusted data.

A consolidation step then merges demands into one vocabulary and lists the new exercises required.

Scaling past eight conditions is a separate decision. The criterion: at least six of the eight
produce a verified restriction list, and the shared vocabulary needs little or no new vocabulary
for the final two conditions processed.

## New catalog entries

New stretches need illustrations, Spanish text, a licensed source and attribution. The pinned
upstream catalog cannot be edited, so owned additions need their own source path through the
importer, and the attribution notices must cover them. The approved routines need seven: figure-four
stretch, lying piriformis stretch, side-lying isometric hip abduction hold, standing isometric
gluteal contraction, wrist extensor stretch, tendon gliding sequence and straight leg raise.

## Testing

- **Eligibility:** avoid, unreviewed and clean cases; union across conditions; regression bypass
  refused; aggregate failure stage; second check in the state policy.
- **Ranking:** a modify exercise ranks after a clean one, is still chosen when nothing else fits, and
  shows its cue.
- **Regression guard:** a full-cohort test proves plans are unchanged for a profile with no
  conditions and no joint labels.
- **Mobility block:** deterministic selection, rotation, limit, exclusion and equipment filtering,
  and an empty block never failing a plan.
- **Accounting:** a logged mobility `clamshell` earns no ledger, scheduling, progression, evidence
  or focus credit.
- **Validator:** each new violation, plus the estimate including the block.
- **Persistence:** migration 16 to 17, archive round trip, and context digest changes.
- **Overlay:** malformed, unknown-ID and missing-provenance fixtures, added to the bundled-asset
  tests, and overlay failure staying typed.
- **Corpus:** planner fixtures for a piriformis profile, a joint-label profile and a condition that
  removes everything.
- **Localization and copy:** `SafetyCopyTest` in both languages, plus planner locale invariance.
- **Device:** rerun instrumentation at the CI screen size of 320x640 dp.

## Out of scope

Staged recovery plans, automatic pain inference, side-specific restrictions, condition end dates,
free-text conditions, severity levels, numeric risk scores and diagnosis.

## Delivery order

1. Run the research pilot and consolidate its vocabulary.
2. Add the overlay, its parser and the eligibility rule, which also resolves the joint-label refusal.
3. Add profile conditions, the Room migration, the archive change and the digest change.
4. Add the mobility block, session role and accounting filters.
5. Add pain feedback, manual warnings, copy and new catalog entries.
6. Decide whether to scale research beyond the pilot.
