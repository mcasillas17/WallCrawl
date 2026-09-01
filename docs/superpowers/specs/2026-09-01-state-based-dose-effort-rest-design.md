# State-Based Dose, Effort, and Rest Design

## Status and authority

This design implements deterministic-engine Task 4 under the signed decisions in
`docs/research/2026-08-29-training-science-evidence-review.md` and the architecture in
`docs/superpowers/specs/2026-08-29-science-based-workout-engines-design.md`.

The values in this document are editable, versioned WallCrawl product defaults. They are
not universal physiology, medical, safety, injury-prevention, or optimality claims.

## Scope and rollout boundary

Task 4 adds a small pure policy that can reduce a structurally valid base prescription
using approved reviewed metadata and the already-composed `TrainingProgramState`. It can
also attach nullable effort guidance and a classified rest target.

Production continues to set `PlannerFeatureFlags.reviewedCapabilityEligibility = false`.
The legacy planner therefore builds no `TrainingProgramState`, never calls this policy,
and produces the same prescriptions it does today. Synthetic reviewed-mode tests are the
only enabled consumer until enough metadata receives deliberate human approval and the
separate availability/persona rollout review succeeds.

This milestone does not:

- derive `INITIATE`, `BUILD`, or any other new adaptation state;
- change `AdaptationStatePolicy`, which remains limited to `UNCALIBRATED` and
  `RETURNING`;
- approve metadata or enable the reviewed production path;
- add progression, capability-evidence relaxation, deload offers, substitutions,
  whole-program repair, Health/Wear, analytics, networking, or an LLM;
- rewrite completed history, persisted sessions, manual templates, or active-session
  edits.

## Chosen architecture

### Pure policy

`StateBasedTrainingPolicy` is a pure component with one version,
`STATE_BASED_DOSE_EFFORT_REST_V1`. It receives:

- an `Exercise`;
- a structurally valid base `ExercisePrescription`;
- the current `UserProfile` and active fitness goals;
- a `TrainingProgramState`;
- an optional prior explicit `UserRestPreference`.

It returns one of three typed outcomes:

```kotlin
sealed interface TrainingPolicyResult {
    data class Applied(
        val policyVersion: TrainingPolicyVersion,
        val prescription: ExercisePrescription,
        val reasons: List<TrainingPolicyReason>
    ) : TrainingPolicyResult

    data class NoGuidance(
        val reason: TrainingPolicyNoGuidanceReason
    ) : TrainingPolicyResult

    data class Failure(
        val reason: TrainingPolicyFailureReason
    ) : TrainingPolicyResult
}
```

`Applied` always contains a valid prescription with at least one target set. `NoGuidance`
is an expected, explicit outcome such as an exhausted weekly allowance.
`Failure` identifies malformed or version-incompatible input. Neither outcome is converted
to a zero-set prescription or a success-shaped default.

Reasons are emitted once in declaration order. Stable inputs therefore produce equal
results and stable reason ordering.

### Alternatives rejected

1. **Put policy logic directly in `DefaultExercisePrescriptionFactory`.** This would mix
   legacy base construction, trust/version validation, dose arithmetic, and policy
   defaults in one stateful-looking factory. It would also make typed no-guidance results
   hard to test independently.
2. **Store guidance outside `ExercisePrescription`.** A generated workout would lose the
   fields when it crossed the existing planner → repository → Room → active-session
   boundary, and templates could not preserve an explicit rest choice.
3. **Apply reviewed policy to the legacy path.** This would let unapproved or missing
   reviewed metadata change production behavior and would violate the explicit rollout
   gate.

The chosen pure policy reuses the repository's existing pattern:
`ExerciseEligibilityPolicy` owns reviewed legality, `WeeklyDoseLedgerCalculator` owns
pure exposure reconstruction, and this component owns only prescription guidance.

## Trust and version validation

Reviewed guidance is available only when all of these checks pass:

1. `TrainingProgramState.policyVersion == PROGRAM_STATE_V1`.
2. `weeklyLedger.policyVersion == PRIMARY_ONLY_V1`.
3. The ledger has a nonblank catalog version and time-zone ID, a nonnegative review
   policy version, bounded map sizes, nonblank keys, and nonnegative bounded counts.
4. The exercise has `reviewedMetadata` and its state is `APPROVED`.
5. Approved provenance has a nonblank human reviewer role, a positive review timestamp,
   positive schema and policy versions, and a nonblank rationale/source.
6. Metadata provenance policy version equals
   `weeklyLedger.reviewPolicyVersion`.
7. The approved `prescriptionShape`, catalog `ExerciseType`, and base prescription type
   agree.
8. The approved direct-primary muscle is nonblank.

Missing or `DRAFT` metadata never falls back to the exercise name, legacy
`primaryMuscles`, legacy `programming`, or an inferred capability requirement. The
policy reads direct-primary and capability requirements only from the approved block.
The reviewed movement pattern supplies the v1 isolation/non-isolation rest distinction;
legacy `programming.mechanics` is not trusted in reviewed mode.

The catalog importer and Android parser remain the authored-data trust boundary.
The policy repeats the checks it depends on because tests and future callers can construct
domain values directly, and a corrupt persisted ledger must not become a favorable
recommendation.

## Dose policy

Weekly direct-primary exposure is the only dose input. `daysPerWeek`,
`completedWorkoutCount`, and recent-session count do not contribute set credit and cannot
masquerade as weekly dose. Session duration continues to limit how many exercises the
planner selects; it does not raise a per-muscle allowance.

The v1 product defaults are:

| Adaptation state | Weekly direct-primary upper allowance | Per-exercise set cap |
| --- | ---: | ---: |
| `NEEDS_ONBOARDING` | no guidance | no guidance |
| `UNCALIBRATED` | 6 | 2 |
| `INITIATE` | 6 | 2 |
| `BUILD` | 12 | 4 |
| `DEVELOP` | 12 | 4 |
| `HOLD` | 8 | 2 |
| `RETURNING` | 6 | 2 |
| `DELOAD_OFFERED` | 6 | 2 |
| `RECALIBRATE` | 6 | 2 |

These numbers are named, versioned WallCrawl defaults. They are not evidence-labelled
minimums or maximums for a person.

For the approved direct-primary muscle:

```text
remaining = weekly upper allowance - already credited direct-primary sets
target sets = min(base target sets, state per-exercise cap, remaining)
```

Arithmetic uses `Long` after validating nonnegative bounded ledger counts. Exact-cap,
over-cap, and overflow-sized existing counts return
`NoGuidance(WEEKLY_DIRECT_PRIMARY_ALLOWANCE_EXHAUSTED)`. A positive remainder can reduce
the base to one valid set. The policy never increases base target sets and has no weekly
minimum or under-target increment.

A relevant `LIMITED` capability means an approved capability requirement whose profile
answer is `LIMITED`. It applies an additional two-set per-exercise cap. `UNKNOWN` is
neutral rather than favorable, and `AVOID` remains the upstream eligibility policy's hard
exclusion.

## Effort policy

`EffortTarget` is nullable. A nonnull target has ordered `minRir` and `maxRir` values in
`1..10`; zero RIR/failure cannot be represented as automatic guidance.

Resolution order is deterministic:

1. `INITIATE`, `RETURNING`, `UNCALIBRATED`, `HOLD`, `DELOAD_OFFERED`, or
   `RECALIBRATE`, or a relevant approved `LIMITED` capability: `2..4` RIR.
2. Established `BUILD`/`DEVELOP` strength work: editable product default `1..2` RIR.
3. Established `BUILD`/`DEVELOP` general-fitness or hypertrophy work: editable product
   default `1..3` RIR.
4. Other goal/state combinations: `null`.

The conservative first rule wins for mixed goals. Missing guidance remains null and does
not imply readiness, low effort, or permission to train to failure.

## Rest policy and explicit preference

Automatic rest guidance uses:

| Rest class | V1 product seconds |
| --- | ---: |
| `SHORT` | 60 |
| `MODERATE` | 90 |
| `LONG` | 180 |

Without an explicit preference:

1. approved duration work resolves to `SHORT`;
2. strength or athletic non-isolation work resolves to `LONG`;
3. approved isolation work resolves to `SHORT`;
4. other reviewed work resolves to `MODERATE`.

An explicit valid `UserRestPreference(restClass, restSeconds)` always wins, including its
exact seconds. Valid user seconds use the existing prescription range `0..1800`; the app
does not relabel a user's seconds as a physiologically optimal value.

`ExercisePrescription` stores `restClass` and `restTargetSource` beside the existing
`restSeconds`. `PRODUCT_POLICY` identifies a generated default;
`USER_PREFERENCE` identifies a value that may be reused. The context builder extracts at
most 512 explicit preferences from its already-bounded eight-session history view,
newest session first, and the factory performs an O(1) lookup per selected exercise.
Generated defaults are never promoted into user preferences.

The added work is bounded: one pass over at most eight recent sessions, at most 512
examined exercise prescriptions retained as preferences, and one policy call for each of
the planner's three to six selected exercises. The existing weekly ledger is consumed
once from `TrainingProgramState`; it is never reconstructed by the policy or factory.

## Prescription integration and load invariant

`DefaultExercisePrescriptionFactory` first builds the exact current base prescription.
If `trainingProgramState == null`, it returns that base unchanged. This is the legacy and
manual-template path.

If state is present, the factory calls `StateBasedTrainingPolicy`. `Applied` returns the
updated prescription. `NoGuidance` or `Failure` becomes a typed
`TrainingPolicyResultException`; it is never converted to a base prescription, zero sets,
or an over-cap prescription.

The policy may copy the base with fewer target sets and new effort/rest fields. It never
changes:

- `targetWeight`;
- `targetAssistanceWeight`;
- rep, duration, or distance targets;
- exercise type.

Confirmed starting loads and bounded history remain the only existing sources of
external load, in their current priority order. Capability and adaptation state cannot
increase or invent a load.

## Persistence and migration

`ExercisePrescription` gains:

- `effortTarget: EffortTarget?`;
- `restClass: RestClass?`;
- `restTargetSource: RestTargetSource?`.

Room schema 11 adds nullable `effortMinRir`, `effortMaxRir`, `restClass`, and
`restTargetSource` columns to both `workout_exercises` and
`workout_template_exercises`. Existing rows receive null for all four columns, while the
existing `restSeconds` value remains byte-for-byte unchanged.

Writers and readers in `WorkoutRepository`, `WorkoutSessionMapper`, and
`WorkoutTemplateRepository` map every field. Starting a template copies those values into
the frozen session snapshot. Editing or deleting the source template cannot rewrite that
session, and the migration does not update any existing workout, template, set, or
completed-history row.

The active rest timer continues to read the persisted `restSeconds`. Add-time, skip, and
dismiss remain one-off timer actions; they are not silently reclassified as durable
per-exercise preferences.

## Failure handling

| Boundary | Failure | Result |
| --- | --- | --- |
| policy/config input | unsupported version or invalid product defaults | construction failure or typed `Failure` |
| reviewed metadata | missing, draft, malformed, version mismatch, shape mismatch | typed `Failure`; no legacy fallback |
| weekly ledger | malformed fields/counts or unsupported policy | typed `Failure` |
| remaining dose | exact or exceeded weekly allowance | typed `NoGuidance`; no zero-set prescription |
| persistence read | partial effort pair or partial rest classification/source | `ExercisePrescription` rejects the row loudly |
| Room migration | prior rows have no new guidance | explicit nulls; existing seconds/history unchanged |

No broad catch, retry, silent empty ledger, or success-shaped fallback is added.

## Local-first privacy boundary

All inputs already live in memory or local Room/bundled assets. The policy performs no
I/O and adds no network call, analytics event, log statement, account data, Health/Wear
access, body measurement, BMI, note/free-text processing, or LLM input.

Validation errors name only the field or typed reason. They do not echo profile values,
loads, capability answers, history, or user-entered text. Room continues to use generated
parameterized DAO statements; this change adds no dynamic SQL.

## Testing

Strict TDD covers:

- policy/default/version validation and exhaustive behavior for all adaptation states;
- approved-only metadata, provenance/review-policy matching, prescription-shape matching,
  and fail-closed missing/DRAFT cases;
- empty, partial, exact-cap, over-cap, negative, and overflow-sized ledgers;
- no weekly minimum, no increase over the base, and typed dose exhaustion;
- effort precedence, null cases, and the invariant that automatic guidance never contains
  zero RIR;
- rest-class resolution, bounded product seconds, explicit preference precedence, and
  source preservation;
- unchanged load/assistance/duration/distance targets across every state and capability;
- exact legacy factory behavior when no program state is present;
- synthetic reviewed persona replay with a composed empty `PRIMARY_ONLY_V1` ledger,
  deterministic output, and unchanged hard eligibility;
- repository and template/session round trips for every new field;
- additive migration 10 → 11 and complete migration chains from every supported schema;
- active timer behavior continuing to use persisted exact seconds;
- stable `TrainingPolicyResult` and reason ordering.

Focused policy, factory, context, persona, repository, and migration tests are followed by
both Python suites, all Gradle unit tests, lint, assembly, and connected Android tests.
