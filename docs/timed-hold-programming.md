# Timed-hold programming

## Exact cohort and authorship

At Workout Guide `ba0b709cb20430361b2cb33aaadd20998164a916`, the bundled 302-exercise
catalog and `FakeWorkoutPlanner` classify exactly these 14 duration exercises as
strength work. Derivation: `duration`, not `isStretch`, and no `Cardio` in primary or
secondary muscles; the Kotlin tests additionally run the real planner on every timed
candidate alone. Exact-set assertions prevent silent expansion or truncation.

| ID | Required equipment for the authored variant |
| --- | --- |
| `active-hang` | Pull-up Bar |
| `bear-plank` | Bodyweight |
| `cable-pallof-hold` | Cable |
| `copenhagen-plank` | Bodyweight + Bench |
| `crab-walk` | Bodyweight |
| `dead-hang` | Pull-up Bar |
| `flutter-kick` | Bodyweight |
| `hollow-body-hold` | Bodyweight |
| `l-sit-hold` | Bodyweight + Dip Bars |
| `mountain-climber` | Bodyweight |
| `plank` | Bodyweight |
| `side-plank` | Bodyweight |
| `superman-hold` | Bodyweight |
| `wall-sit` | Bodyweight + Wall |

Crab-walk, flutter-kick, and mountain-climber are moving timed drills included by the
existing catalog/planner classification. “Timed holds” is the roadmap shorthand, not
an assertion that every entry is isometric. The authored variants include their support
equipment rather than treating the upstream Bodyweight listing as the entire requirement.

Each record supplies difficulty, mechanics, fatigue, coaching, movement pattern,
progression type (`duration`), equipment combinations, alternatives, and a null rep range.
These are AI-authored legacy programming judgments. Pinned catalog facts/artwork inform
exercise identity, type and setup; the existing
[science evidence review](research/2026-08-29-training-science-evidence-review.md) bounds
claims but does not validate these individual ranking labels or coaching instructions.
The legacy 1–5 fatigue field is an ordinal product ordering input, not a physiological
measurement. Alternative IDs are related catalog links, not automatic substitutions,
guaranteed regressions, or claims of equivalent training outcomes.

## Rep-range contract

The optional `programming` block may remain absent for any catalog exercise. When present:

| Exercise types | `recommendedRepRange` |
| --- | --- |
| `WEIGHT_REPS`, `BODYWEIGHT_REPS`, `ASSISTED_BODYWEIGHT` | Required object, integer notation, `1 <= min <= max <= 1000`; omission/null rejected |
| `DURATION`, `DISTANCE_DURATION` | Omitted or null accepted; all objects and other non-null values rejected |

The importer canonicalizes either accepted timed input to explicit
`"recommendedRepRange": null`. New authored entries use that representation too.
`programming-rep-range-schema.json` contains the rep-based and timed definitions;
the importer selects one using the validated pinned catalog type and additionally
checks ordering. Android's streaming parser validates after reading the complete
exercise. Domain `Exercise` construction/copy and the JVM fixture projection enforce
the same type-dependent rule. Parser and importer errors identify fields with bounded
messages rather than echoing records. Shared fixtures cover all five types, missing,
null, ordered/equal/boundary ranges, wrong shapes, malformed endpoints, and numeric types.

## Changed and preserved behavior

There are 131 legacy programmed exercises: 117 unchanged rep records plus these 14.
New timed metadata affects ranking/coaching and can narrow the equipment filter where
a variant requires a bench, dip bars, or wall. The library shows mechanics, difficulty,
pattern, fatigue and coaching without a fabricated rep badge. The synthetic reviewed
bodyweight persona now pins squat/glute-bridge/plank for its leg split rather than
requiring a push accessory. This is an expected metadata ordering change.

Per-exercise rep prescriptions are pinned against the pre-change baseline across goals,
return durations and weight units. The factory implementation is unchanged. Ordinary
timed strength retains 3 sets × 45 seconds with 45-second rests. Stretches retain
1 set × 30 seconds with 15-second rests; distance/duration retains 1 × 600 seconds
with no rest. No reps are converted into seconds, and no load is invented. Duration
metadata does not make stretches or pure conditioning eligible for strength slots.

All 302 identities, source metadata, 906 frames, licensing, and reviewed records remain
unchanged. Regeneration uses the pinned upstream revision; `--check` detects drift.
The upstream working checkout need not be reset: a disposable local clone at the pinned
commit supplies the importer. No networking, analytics, dependency or Room changes ship.

## Human gate and limits

The separate reviewed cohort remains 37 DRAFT / 0 APPROVED. Human reviewer identities and
timestamps remain absent and the production reviewed-capability flag remains false.
Neither agent code review nor PR approval supplies human exercise-metadata signoff.

This milestone does not implement progression, deloads, substitutions, Health/Wear, or
LLM behavior. `progressionType` is descriptive; timed targets remain existing defaults,
not exercise-specific duration recommendations. Coaching makes no medical, prevention,
optimality, or efficacy claims. Broader classification changes and human field-by-field
review are outside this milestone.

The [design](superpowers/specs/2026-09-02-timed-hold-programming-design.md) and
[implementation plan](superpowers/plans/2026-09-02-timed-hold-programming.md) record
contract decisions and the required RED/GREEN, validation, review and publishing gates.
