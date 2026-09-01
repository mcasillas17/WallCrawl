# Experience-Aware Exercise Difficulty Ranking

## Status and authority

This design implements the signed v1 decision in
`docs/research/2026-08-29-training-science-evidence-review.md` and the
`Calibration, Complexity Ceiling, and Ranking` contract in
`docs/superpowers/specs/2026-08-29-science-based-workout-engines-design.md`.
Those documents are the approved authority for this autonomous roadmap item.

Experience is a soft complexity signal. It is not evidence that an exercise is
unsafe, and self-reported experience must not permanently remove otherwise-legal
work. The existing reviewed-metadata eligibility policy remains the sole owner of
the temporary `ADVANCED` ceiling for `UNCALIBRATED` and `RETURNING` states.

## Decision

Add a small pure `ExerciseDifficultyRankingPolicy` with one public operation:

```kotlin
fun aboveExperiencePenalty(
    exercise: Exercise,
    experienceLevel: ExperienceLevel,
    reviewedEligibilityEnabled: Boolean
): Int
```

The result is always non-negative. Zero means the exercise is not above the
profile's experience tier or has no trusted classification for the active mode.
A larger result means the exercise sits further above the profile tier:

| Profile | Beginner/foundational | Intermediate/standard | Advanced |
| --- | ---: | ---: | ---: |
| `BEGINNER` | 0 | 1 | 2 |
| `INTERMEDIATE` | 0 | 0 | 1 |
| `ADVANCED` | 0 | 0 | 0 |

The policy reads exactly one metadata source per planning mode:

- legacy mode reads `programming.difficulty`;
- reviewed-enabled mode reads `reviewedMetadata.complexity` only when
  `reviewState == APPROVED`;
- missing legacy programming, missing reviewed metadata, and `DRAFT` reviewed
  metadata return zero so unclassified entries retain their existing relative
  ordering rather than being treated as beginner work.

The name `aboveExperiencePenalty` predicts both the direction and range of the
value: it measures only tiers above the profile, and it never returns a negative
bonus.

## Planner integration

`FakeWorkoutPlanner` derives `reviewedEligibilityEnabled` from the presence of
`WorkoutGenerationContext.automaticEligibilityResult`. This is the existing
explicit signal that the context builder used reviewed eligibility rather than
the legacy `ExerciseFilter` path.

The planner inserts the ascending penalty into both existing comparators:

1. Compound ordering: primary-muscle match, experience penalty, fatigue score,
   stable ID.
2. Accessory ordering: primary-muscle match, isolation mechanics, presence of
   legacy programming, experience penalty, fatigue score, stable ID.

Movement-pattern diversity remains a stronger selection rule than comparator
ordering because `chooseCompounds` still fills distinct patterns first. Equipment,
exclusions, capability restrictions, and the temporary reviewed complexity
ceiling remain upstream eligibility decisions. Ranking receives the already
allowed list and neither adds nor removes candidates.

If an advanced exercise is the only fillable legal candidate, its penalty cannot
remove it, so it remains selectable. An advanced profile receives zero penalty
for every classified tier, preserving the current fatigue and stable-ID order.

## Alternatives considered

### Filter by experience

Rejected. Filtering would turn self-reported experience into a permanent legality
gate, conflict with the signed science decision, alter browse/manual semantics if
placed in `ExerciseFilter`, and strand sparse equipment profiles.

### Add experience directly to each planner comparator

Rejected. Duplicated tier conversion and trust-mode branching would make compound
and accessory behavior drift. A pure policy gives both paths one tested contract.

### Chosen: pure penalty policy plus comparator integration

This is the narrowest design that makes experience effective, keeps eligibility
ownership unchanged, exposes trust-mode behavior explicitly, and can later be
reused by a deterministic ranker without changing its meaning.

## Testing

Strict TDD adds policy and planner regressions before production changes:

- beginner compounds order beginner, intermediate, then advanced when stronger
  criteria are equal;
- intermediate profiles demote advanced but do not distinguish beginner from
  intermediate;
- advanced profiles preserve existing fatigue and ID ordering;
- a sole advanced candidate remains selectable;
- accessories use the same experience policy;
- reviewed-enabled mode uses approved complexity despite conflicting legacy
  difficulty;
- legacy mode ignores draft reviewed complexity and uses legacy difficulty;
- penalty computation does not mutate candidate membership, and existing
  eligibility decision tests remain unchanged;
- the beginner and advanced persona fixtures continue to produce legal plans,
  preserve no-invented-load behavior, and assert experience-appropriate ordering
  without claiming that experience is a hard rule.

Focused planner tests are followed by the repository's full Python, JVM,
Android lint/assemble, and connected-device validation.

## Documentation and rollout boundary

The README will say that experience-aware soft ranking has shipped and that the
reviewed path uses only approved complexity. It will no longer claim that
difficulty is unread.

Production still defaults the reviewed capability eligibility feature flag off.
Consequently, the shipped default path ranks from legacy `programming.difficulty`.
Reviewed complexity ranking is implemented and tested but cannot affect production
recommendations until humans approve sufficient categorical metadata and the
reviewed eligibility rollout is enabled. Draft metadata never influences ranking.
