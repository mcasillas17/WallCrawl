# Whole-Program Validation Design

## Status and authority

This design is the validation contract required before
[ROADMAP Package 4](../../../ROADMAP.md#4-add-whole-program-validation) is implemented. It
resolves the open contract decisions the roadmap listed: proposal scope, duplicate and
family rules, movement coverage, the duration estimator and its tolerance, prospective
weekly accounting, repairability, stale-context handling, and the persistence shape.

The [evidence-to-rule mapping](../../research/2026-08-29-training-science-evidence-review.md#validation-scope-clarification-2026-09-05)
governs classification. Every rule below names its rationale as one of:

- **software invariant** — internal integrity, consistency, or persistence correctness;
- **product policy** — a named, versioned WallCrawl choice.

No rule here is a physiological, clinical, or safety claim. Exceeding a configured
allowance is a policy mismatch, never proof of overload or medical danger. There is no
numeric fatigue budget, no summation of the legacy ordinal `programming.fatigueScore`, no
timestamp-only readiness or overload rule, no universal recovery interval, no mandatory
weekly minimum, and no automatic volume increase.

## Scope

### The validated unit

The validated unit is **one proposed session**: a single `GeneratedWorkout` evaluated
against the exact `WorkoutGenerationContext` that produced it.

There is no multi-session program horizon in version 1. No future session is planned,
reserved, or accounted for, so nothing here reserves allowance against work that has not
been proposed. When substitution work later replaces open work inside an active session, it
re-enters through this same single-session contract.

### Where it runs

`ProgramValidator` runs on every automatic recommendation:

1. after generation, before the recommendation is shown or persisted, with one
   deterministic repair pass permitted;
2. again at workout start, against a freshly built context, with repair **not** permitted.

Manual templates and the browse catalog do not pass through it. They are explicit user
choices with their own catalog-existence and type-agreement rules, and this validator's
program-design and dose rules would change that behavior.

### Reviewed versus legacy path

The reviewed path is the one where `WorkoutGenerationContext.automaticEligibilityResult`
is present, which happens only when `PlannerFeatureFlags.reviewedCapabilityEligibility` is
enabled. It remains `false` in production.

| Rule | Legacy path | Reviewed path |
| --- | --- | --- |
| Identifiers, candidate membership, catalog type | yes | yes |
| Prescription structure | yes | yes |
| Declared session constraints | yes | yes |
| Load provenance | yes | yes |
| Duration consistency | yes | yes |
| Explicit exclusions | yes | yes |
| Approved metadata, provenance, review-policy equality | no | yes |
| Reviewed eligibility decision agreement | no | yes |
| Aggregate weekly dose accounting | no | yes |

Reviewed-only rules never fire on the legacy path. That is what keeps shipped legacy
behavior unchanged while the gate is disabled.

## Rules

### Identifier and candidate membership — software invariant

Every recommended exercise ID must be non-blank, exist in the bundled catalog, be a member
of the candidate set the generation context allowed, and carry a prescription whose
`exerciseType` equals the catalog type. Membership is always checked; there is no mode that
skips it. These are the checks `GeneratedWorkoutValidator`
already performs; whole-program validation reuses that unit rather than restating it, and
`GeneratedWorkoutValidator` now reports them as structured violations rather than as a
first-failure message, so a rejection can name every reason at once.

Catalog existence is not approval and not a clinical judgement.

### Declared session constraints — product policy

Program-design constraints are **declared**, not universal. They live on
`SessionProgramConstraints`, carried by the generation context:

| Constraint | Default | Meaning |
| --- | --- | --- |
| `uniqueExerciseIds` | `true` | The same catalog exercise ID must not appear twice in one proposed session. |
| `uniqueProgressionFamilies` | `false` | Inert unless a caller declares it. |
| `requiredMovementPatterns` | empty | Inert unless a caller declares it. |

`uniqueExerciseIds` defaults on because two instances of one exercise ID inside a single
generated session cannot be told apart in the recommendation record and would be counted
twice by prospective dose accounting. The rationale is accounting and identity integrity
within one generated session. It is **not** a claim that repeating a movement is harmful,
it does not apply across sessions or weeks, it does not apply to progression families, and
it does not apply to manual templates.

`requiredMovementPatterns` is empty by default, so no workout is required to cover any
pattern. When a caller declares required patterns and the eligible pool cannot supply one,
the result is a typed violation naming the missing pattern; it is not repairable, because
repair may not widen the legal candidate set.

Both declared constraints read **approved** metadata only, falling back for coverage to the
legacy authored `programming.movementPattern` so the legacy path can still satisfy a
declared requirement. An unapproved draft record still carries an authored family and an
authored pattern, and neither may be what drives a product-policy rejection.

The planner's existing pattern-spreading behavior in `chooseCompounds` stays a ranking
preference with an explicit fallback to repeated patterns. This design does not promote it
to a rule.

### Explicit constraints and eligibility — product policy

On both paths, no recommended exercise may be in `UserProfile.excludedExerciseIds`.

On the reviewed path, each recommended exercise must additionally carry an
`EligibilityDecision` marked eligible in `automaticEligibilityResult.decisions`. The
validator re-reads the decision the enabled path already produced rather than re-deriving
legality, so the two can never disagree. Repair may never relax either check.

A legal candidate is not a promise of safety, and experience alone is not a permanent ban
on complex work.

### Reviewed metadata and provenance — software invariant

On the reviewed path only, every recommended exercise must carry `APPROVED`
`ReviewedExerciseMetadata` whose provenance is well formed, whose
`provenance.policyVersion` equals the ledger's `reviewPolicyVersion`, and whose
`prescriptionShape` agrees with the catalog exercise type and the prescribed type. These
mirror `StateBasedTrainingPolicy`'s own trust checks, so a proposal cannot reach the user
through a path the prescription policy would have rejected.

Neither a merge, nor this validator, nor a passing test grants human approval.

### Prescription structure — software invariant

`ExercisePrescription`'s constructor already enforces positive bounded sets, ordered rep
ranges, finite values, type-specific required and forbidden fields, and paired
`restClass`/`restTargetSource`. Whole-program validation does not restate those bounds and
adds no new numeric limits. Representational bounds are not human tolerances.

No effort rule is added either, and there is no violation code for one. Automatic guidance
cannot target zero reps in reserve because `EffortTarget` forbids a minimum below 1, and
that invariant lives in the prescription types and `StateBasedTrainingPolicy`, which
predate this change. Whole-program validation relies on it rather than restating it.

### Load provenance — software invariant

A `null` target load is always legal and is never repaired into a number.

A non-null `targetWeight` must trace to one of:

- the confirmed starting load the user entered for that exercise
  (`UserProfile.confirmedStartingLoads`), or
- the last recorded load for that exercise in `WorkoutGenerationContext.exerciseHistory`,
  optionally plus the documented legacy history increment of 5.0 lb or 2.5 kg in the
  context's preferred unit.

The increment is the existing legacy behavior in `DefaultExercisePrescriptionFactory` and
is preserved deliberately. A valid target is **not** required to equal the last recorded
load, and this task neither replaces progression nor introduces one.

`targetAssistanceWeight` follows the same rule against the same sources.

Comparison is by exact unit-consistent value in the context's preferred unit, because
`WorkoutHistoryAnalyzer` already normalizes history into that unit. Provenance proves where
a number came from; it proves nothing about safety.

### Duration consistency — software invariant plus product policy

The estimator is named `DURATION_ESTIMATOR_V1` and lives in `WorkoutDurationEstimator`. Its
assumptions are:

- execution seconds per set are `targetDurationSeconds` when present, otherwise 45;
- rest seconds are counted once per set, treating the rest after an exercise's final set as
  the transition into the next exercise;
- total seconds are divided by 60 with truncation and clamped to 1..240 minutes.

This is exactly the arithmetic `FakeWorkoutPlanner` already used. Extracting it gives the
planner and the validator one implementation instead of two that can drift; no generated
duration changes. `OfflineWorkoutRepository`'s separate template estimator is left alone —
it serves the manual-template path, which this validator does not run on.

Two rules follow:

- **Structural (software invariant):** `estimatedDurationMinutes` must be within 1..240.
- **Agreement (product policy):** it must be within **±1 minute** of
  `DURATION_ESTIMATOR_V1` applied to the proposal. The tolerance exists because the
  `WorkoutPlanner` interface admits other implementations that may round differently; a
  planner sharing the estimator deviates by zero.

Version 1 deliberately enforces **no** relationship between the estimate and
`preferredWorkoutDurationMinutes`. The estimate is not a promise of requested-duration
equality and not a completion-time guarantee. Duration thresholds are not physiological
limits.

### Aggregate weekly dose — product policy, reviewed path only

`StateBasedTrainingPolicy` caps each prescription against the same supplied completed
ledger, so several exercises sharing one direct-primary muscle can each spend the same
remainder. Whole-program validation closes that by checking the **whole proposal at once**.

For each recommended exercise with `APPROVED` metadata, its `targetSets` are attributed
prospectively to its single approved `directPrimaryMuscle`, preserving `PRIMARY_ONLY_V1`:
one designated direct primary per set, and descriptive secondary muscles credited nothing.

For each attributed muscle:

```
completedSets   = ledger.directPrimarySets[muscle] ?: 0     // completed history only
proposedSets    = sum of targetSets over the proposal        // prospective, never history
allowanceSets   = doseLimitsByState[adaptationState].maxWeeklyDirectPrimarySets
```

`completedSets + proposedSets` must not exceed `allowanceSets`. Equality is legal; only a
strict excess is a violation.

Three outcomes are kept distinct, because they are different kinds of problem:

| Reason code | Kind | Meaning |
| --- | --- | --- |
| `MALFORMED_WEEKLY_LEDGER` | software invariant | The ledger is not well formed, or its policy version is unsupported. |
| `DOSE_ACCOUNTING_OVERFLOW` | software invariant | Arithmetic could not be represented. Sums are computed in `Long`. |
| `WEEKLY_ALLOWANCE_EXCEEDED` | product policy | A configured allowance was exceeded. |

`AdaptationState.NEEDS_ONBOARDING` configures no dose guidance. There is then no allowance
to compare against, which is not a violation; the accounting records the absent allowance
explicitly.

Explicit omissions are preserved rather than guessed at. An exercise on the reviewed path
without approved metadata is already a `MISSING_APPROVED_METADATA` violation, so it never
becomes silently uncounted dose — the proposal is rejected rather than partly counted.

The ledger's own `unattributedWorkSets` reasons stay in the ledger. The record carries per
muscle only the completed, proposed, and configured allowance counts; the omission reasons
belong to the ledger, which is reconstructable from completed history at any time, so
copying them into an immutable record would duplicate derived data without making anything
more explainable.

Nothing here writes to the ledger. The ledger is reconstructed from completed history only,
and a proposed or rejected workout never becomes completed exposure. Prospective targets
and completed credit are separate fields in the record and are never summed into one
number.

The exact allowances (6/8/12), per-exercise caps (2/4), the relevant-`LIMITED` cap (2), the
RIR bands, and the rest seconds are `STATE_BASED_DOSE_EFFORT_REST_V1` product choices, not
medical limits. No weekly floor and no automatic increase is added.

## Deterministic repair

At most **one** repair pass runs, and only at generation time.

Repair may do exactly one thing: **reduce** `targetSets` so that aggregate weekly
accounting holds, then recompute `estimatedDurationMinutes` with `DURATION_ESTIMATOR_V1`.

Allocation is deterministic. For each over-allowance muscle, the remaining allowance is
distributed across that muscle's proposed exercises in recommendation order, each keeping
at least one set. If the remaining allowance cannot give every affected exercise at least
one set, repair fails rather than dropping an exercise, because dropping one changes the
displayed plan materially.

Repair may never: weaken or remove an explicit constraint, widen the legal candidate set,
add or substitute an exercise, invent or change a load, alter effort or rest guidance, or
fall back from reviewed-only eligibility to unreviewed content.

After the pass, the full validation runs once more. If the proposal is still invalid, it
fails closed with typed reasons. Repeated retries never expand eligibility, and a
successful repair is not a health assessment.

At workout start, repair is disabled outright, so a displayed workout is never silently
replaced by a materially different plan while it is being started.

## Context freshness and start

`RecommendationContextIdentity` is a digest over exactly the inputs that decide a
recommendation's continued validity:

- profile ID and profile revision;
- lifetime completed workout count;
- the ordered allowed-candidate ID set;
- catalog version and review-policy version;
- whether the reviewed path is enabled;
- derived adaptation state;
- the ledger's week start day and time-zone ID, when a program state is present.

At start, `TodayViewModel` rebuilds the context, recomputes the identity, and compares it
to the identity recorded when the recommendation was produced. A mismatch — a profile edit,
newly completed history, or a crossed week or time-zone boundary — surfaces a typed,
resource-backed "this recommendation is out of date" failure. Nothing is started, nothing
is repaired, and the user regenerates deliberately.

When the identity matches, the recommendation is fully revalidated against the current
context with repair disabled before anything is written.

## Recommendation record and persistence

### What is recorded

One immutable record per **started** session, written in the same transaction as the
session. A recommendation that is only displayed writes nothing.

| Field | Purpose |
| --- | --- |
| `sessionId` | Primary key; foreign key to `workout_sessions` with cascade delete. |
| `validatorVersion` | `WHOLE_PROGRAM_V1`. |
| `durationEstimatorVersion` | `DURATION_ESTIMATOR_V1`; which estimator the duration agreement rule ran under. |
| `outcome` | `VALID` or `REPAIRED`. |
| `reviewedPathEnabled` | Which rule set applied. |
| `catalogVersion`, `reviewPolicyVersion` | Content identity. |
| `trainingPolicyVersion`, `ledgerPolicyVersion`, `programStatePolicyVersion` | Policy identity; absent on the legacy path. |
| `adaptationState` | The state the plan was built under. |
| `weekStartEpochDay`, `timeZoneId` | The exact accounting week. |
| `profileRevision` | Which profile revision produced it. |
| `contextIdentity` | The digest above, for mismatch detection. |
| `reasonCodes` | Ordered stable enum names; empty when nothing was reported. They are persisted into a `"|||"`-joined column, so the archive rejects a code containing that sequence exactly as it does for every other joined value. |
| `doseAccounting` | Per muscle: completed, proposed, and configured allowance. |
| `recordedAtTimestamp` | Diagnostics only. |

Version-like columns are stored as text, not converted enums, so a value written by a
future build reads back as unrecognized rather than being coerced into a meaning this build
implements. This matches the weekly-ledger cache's existing storage decision.

The plan's own exercises and prescriptions are **not** duplicated: `workout_exercises`
already holds them for the started session, keyed by the same session. The record adds only
what is otherwise unrecoverable.

### What is and is not replayable

The record preserves the versioned inputs, the derived state, the accounting, and the
ordered reasons, so a completed session can be explained and any input mismatch detected
without re-running the planner. It deliberately does **not** retain the full historical
profile: WallCrawl keeps one current profile row, so a byte-exact re-derivation of a past
candidate set is not possible and this design does not claim it is. That limit is recorded
rather than papered over with a hash.

Nothing personal is added. There is no name, note, load, repetition, effort value, body
measurement, or free text in the record.

### Room

Schema 11 → 12, additive only. `MIGRATION_11_12` creates
`workout_recommendation_records` and touches no existing table, column, or row. The table
starts empty; sessions completed before it existed keep an honestly absent record rather
than a fabricated one, exactly as the typed set-outcome migration did.

The record is written by the same `insertWorkoutUnlessActive` transaction that writes the
session, its exercises, and its sets. A failure anywhere in that transaction leaves no
session and no record, so there is never a partial active session and never a session whose
provenance was silently lost.

### Archive

`ARCHIVE_VERSION` becomes 2, and the reader accepts versions 1 and 2. A version 1 archive
restores exactly as it does today and simply carries no recommendation records; a version 2
archive carries them in a `recommendations` array beside `sessions`, each referencing an
existing session exactly once. The dose-accounting payload is validated on read by the same
strict decoder Room uses, so an edited archive cannot smuggle an unreadable value through.

Restore remains empty-destination only, in one transaction, with the derived ledger cache
cleared and rebuilt. Deletion removes recommendation records with everything else.

## Failure surfacing

`ProgramValidator` returns a result rather than throwing; `TodayViewModel` maps it to typed,
resource-backed copy in the language the reader chose. Reason codes are stable enum names
and never localized, so language cannot change a decision.

New typed failures:

- **recommendation out of date** — the generation context changed before start;
- **start validation failed** — revalidation rejected the plan at start;
- **weekly plan complete** — every remaining violation was `WEEKLY_ALLOWANCE_EXCEEDED`.
  The copy says this week's configured plan is already covered. It is a product-policy
  statement, not a medical one, and it offers building a workout manually.

Existing failures keep their existing copy and mapping.

## Language invariance

Validation reads no locale. Identical canonical inputs produce identical decisions,
candidate order, prescribed values, reason codes, dose accounting, and context identity in
either language. Only the rendered message differs.

## Testing

- Pure policy tests for every rule and every violation code, valid and invalid.
- Several exercises sharing one direct primary; exactly-at-allowance and over-allowance.
- Malformed ledger and arithmetic overflow kept distinct from an exceeded allowance.
- Null, confirmed, and history-derived loads, including the legacy increment and mixed
  units.
- Declared and undeclared duplicate, family, and coverage constraints.
- One repair pass only; repair that cannot succeed fails closed; repair disabled at start.
- Stale context at start: profile revision, completed count, and week or zone boundary.
- English and Spanish equivalence of decisions and reason codes.
- The disabled reviewed path retains legacy behavior, asserted directly.
- Room migration 11 → 12, the start transaction, no partial session, and archive round
  trips for both version 1 and version 2.

Tests use synthetic approved metadata only. Nothing in `src/main` can read it, so a test
approval can never reach the bundled catalog. Passing these tests establishes software
conformance, not scientific or clinical validation.

## Out of scope

Production flag enablement, human metadata approval, progression, deload, Progress weekly
semantics and UI, substitutions, new physiological scores, and any recency or scheduling
rule. Recency remains an undesigned scheduling preference; no blocking recency rule is
added here.
