# Workout focus coverage design (2026-09-07)

## Problem

`FakeWorkoutPlanner` decides which split a session advertises with a predicate that
accepts a match in **primary or secondary** muscles. Shoulder involvement in a row or an
anti-rotation drill therefore makes `PUSH` look fillable. A band-only profile with
`Chest = HIGH` receives a session titled Push containing `band-pull-apart`,
`banded-dead-bug`, `banded-face-pull`, `banded-pallof-press` and `banded-woodchop` — no
push work at all. The proposal passes `ProgramValidator` because the default session
contract requires no anatomical coverage, so schema validity is not evidence the bug is
absent.

Ranking primary matches ahead of secondary ones does not fix it: none of the 19 band-only
candidates trains a push muscle as a primary, so there is nothing to rank up.

## Contract: `SPLIT_FOCUS`

A split's advertised focus is **genuinely supported** by an exercise when both hold:

1. the exercise is strength work — the existing classification that already excludes
   stretches, distance work and purely timed conditioning; and
2. one of the muscles the exercise trains **as its own purpose** is in the split's
   `targetMuscles`.

"As its own purpose" resolves to the approved `directPrimaryMuscle` when the reviewed
contract applies to that exercise — `APPROVED` state plus well-formed human provenance —
and otherwise to the catalog's legacy `primaryMuscles`. A `DRAFT` record is never read,
so this cannot become a silent source of production eligibility.

Descriptive secondary muscles are excluded. They may still justify an accessory slot;
they can never be the evidence for the advertised focus.

### What the contract deliberately is not

- It is **not** a movement-pattern rule. Only 131 of 302 catalog entries carry legacy
  `programming`, and 25 genuine push anchors — `archer-push-up`, `decline-bench-press`,
  `handstand-push-up` among them — carry no pattern. Requiring one would invent an
  absence. Every push anchor whose known pattern is not a push pattern is legitimate
  isolation (`pec-deck`, `dumbbell-fly`, `skull-crusher`).
- It is **not** a claim that a session must cover every pattern, that repeated movements
  are harmful, or that a supported session is medically appropriate. It is a
  truthfulness rule about one advertised label.
- It is **not** split-specific. The same predicate reads each split's own
  `targetMuscles`, so `PULL`, `LEGS`, `UPPER_BODY` and `FULL_BODY` get identical
  treatment without hard-coding the band-only example.

## Where the contract applies

One predicate, `WorkoutSplit.trainsAsFocus(exercise)`, in `core/ai`. It replaces every
place the planner previously asked "does this train the split as a primary?" and adds two
new callers:

| Stage | Rule |
| --- | --- |
| Split fillability | A split enters the rotation only if some candidate supports its focus. |
| Compound and accessory ordering | Focus support is the first ordering key, as split-primary already was. |
| Selection guarantee | The chosen set always contains at least one supporting exercise. |
| Whole-program validation | `UNSUPPORTED_WORKOUT_FOCUS` if the advertised split is supported by no selected exercise. |
| Coverage and planner tests | The same predicate, not a test-local classifier. |

The accessory pool stays broad on purpose: an exercise that only brushes the split is
still legal work once the focus is established.

## Unavailable preference

Hard constraints — equipment, exclusions, capabilities — are unchanged. Muscle priorities
stay soft. When a `HIGH`-priority muscle has no candidate that trains it as its own
purpose, the planner records it in `GeneratedWorkout.unavailableFocusMuscles`, sorted by
canonical name so the list never depends on map iteration order or locale.

The session itself is then an accurately labelled alternative chosen by the existing
deterministic rotation over the splits that *are* supported. The suggested-workout card
adds one localized sentence naming those muscles, in English and Spanish, **only when the
list is non-empty** — an ordinary session's card is unchanged, so the committed Today
screenshots stay accurate and no separate product decision to surface the whole explanation
is made here. The same sentence is appended to the explanation a started session stores.
Nothing is relabelled "Full body" to hide the mismatch, no equipment is borrowed, and no
exercise is invented.

> Revised during review. The first implementation rendered the whole explanation on every
> card, which was both wider than this task and enough to stale every Today screenshot.

When no split is supported at all, the existing typed
`WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT` outcome and its resource-backed
Today copy already apply. It becomes reachable for pools that previously produced a
misleading session.

## Validation boundary

`UNSUPPORTED_WORKOUT_FOCUS` is a **software invariant**: the advertised split must agree
with the exercises the proposal actually contains, exactly as `DURATION_ESTIMATE_MISMATCH`
requires the reported estimate to agree with the plan it describes. What counts as
support is the product policy above. It is always on rather than declared in
`SessionProgramConstraints`, because a caller cannot reasonably ask for a session whose
title contradicts its content, and because the declared constraints are program-design
preferences rather than internal-consistency rules.

Bounded repair is untouched. It only reduces `targetSets`, never drops an exercise, so it
cannot destroy focus coverage; and because it runs only when every reported problem is an
exceeded allowance, a focus violation blocks it rather than being repaired away.
Revalidation at workout start, with repair disabled, checks the same rule.

## Compatibility

No persisted reason code is renamed and no historical record is rewritten.
`reviewedCapabilityEligibility` stays `false`, all production review states stay `DRAFT`,
no catalog fact or label changes, and load provenance, prescription types, unit
conversion and the archive format are untouched. `GeneratedWorkout` gains one defaulted
field that is rendered, never serialized as a code.

## Testing

A failing regression comes first: the exact committed band-only reproduction, driven
through the real `FakeWorkoutPlanner` and `ProgramValidator`, asserting the advertised
split is supported by a selected exercise. Additional coverage: secondary-only pools,
empty pools, other splits, deterministic regeneration and completed-session rotation,
locale invariance of the new field, English/Spanish rendering of the new sentence, and
the reviewed-mode synthetic cohorts, which remain clearly labelled test-only approvals.
