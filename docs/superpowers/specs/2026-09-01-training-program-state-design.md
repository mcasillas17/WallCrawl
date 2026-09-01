# Training Program State Design

## Status and boundary

This milestone composes the two independently shipped halves of the deterministic engine —
the `PRIMARY_ONLY_V1` weekly dose ledger and reviewed capability eligibility — into one
derived value, `TrainingProgramState`.

It changes no user-visible behavior. No screen reads the new type, no copy changes, and no
pixel moves. With `PlannerFeatureFlags.reviewedCapabilityEligibility` disabled — its
production value — the generated workout is byte-identical to today's.

It also does not approve metadata. The bundled catalog stays at 302 exercises with 37
`DRAFT` reviewed entries and zero `APPROVED`, so the composed ledger still credits nothing
from real history and reports every completed work set as unattributed.

Task 4 has since added a production-disabled `StateBasedTrainingPolicy` that consumes the
composed ledger for upper-cap-only dose guidance. This document retains the composition
decision and its original milestone boundary; current behavior is documented in
`2026-09-01-state-based-dose-effort-rest-design.md`.

## Why now

`docs/reviewed-capability-eligibility.md` names this work as a blocker for enabling the
reviewed automatic planner:

> The current disabled builder can derive `RETURNING` from a reported break and otherwise
> uses `UNCALIBRATED`; richer adaptation state is a later program-state milestone. That
> limitation is one reason production enablement remains blocked.

That derivation is currently an inline ternary inside `WorkoutGenerationContextBuilder`:

```kotlin
adaptationState = if (profile.returningAfterBreakWeeks > 0) {
    AdaptationState.RETURNING
} else {
    AdaptationState.UNCALIBRATED
}
```

It is untested in isolation, unnamed, and buried in a builder that also does I/O. Extracting
it is the smallest change that turns a hidden expression into a reviewable policy.

## Contract

```kotlin
enum class TrainingProgramStatePolicyVersion { PROGRAM_STATE_V1 }

data class TrainingProgramState(
    val policyVersion: TrainingProgramStatePolicyVersion,
    val adaptationState: AdaptationState,
    val weeklyLedger: WeeklyDoseLedger
)
```

The spec in `2026-08-29-science-based-workout-engines-design.md` also lists
`recentComparableOutcomes`, `deloadOffer`, and `returnStatus`. All three depend on capability
evidence and progression, which are Task 6. Adding them now as empty placeholders would
assert a shape no code produces or consumes, so they are deliberately absent.

## Components

| Unit | Responsibility | Depends on | I/O |
| --- | --- | --- | --- |
| `TrainingProgramState` | Immutable composed value | domain models | none |
| `AdaptationStatePolicy` | `UserProfile` → `AdaptationState` | domain models | none |
| `TrainingProgramStateProvider` | Calls the policy, reads the ledger, composes | both, plus `WeeklyDoseLedgerRepository` | ledger read |
| `WorkoutGenerationContextBuilder` | Calls the provider instead of the inline ternary | provider | existing |

The policy stays pure so the state machine can grow in Task 4 without dragging I/O behind
it. The provider is the only unit that touches the database or the clock.

## Adaptation state scope, and the constraint that sets it

`AdaptationStatePolicy` derives exactly two states — `RETURNING` when
`returningAfterBreakWeeks > 0`, otherwise `UNCALIBRATED`. That is precisely what the ternary
does today, so this milestone changes no eligibility outcome.

Deriving more states is not a free improvement. `ExerciseEligibilityPolicy` applies the
temporary advanced-complexity ceiling with an allow-by-default check:

```kotlin
if (advancedCeilingApplies && adaptationState == AdaptationState.UNCALIBRATED) { … }
if (advancedCeilingApplies && adaptationState == AdaptationState.RETURNING) { … }
```

Any state outside that pair **lifts the ceiling**. `docs/reviewed-capability-eligibility.md`
states this deliberately — "It remains eligible in the other adaptation states" — so it is a
decision, not an oversight, and this milestone will not quietly invert it in a file it does
not own.

The trap is concrete. `NEEDS_ONBOARDING` is derivable today from `UserProfile`, and onboarding
is gated only at navigation (`WallCrawlApp.kt`), not inside the planner. A policy that emitted
`NEEDS_ONBOARDING` would make advanced exercises eligible for a user who has not onboarded.
With the flag disabled no test would fail; the regression would sit latent until the flag
flipped. PR #47 has since *tightened* this same ceiling, so bypassing it would undo current
work.

A regression test therefore pins the coupling: the set of states the policy can emit must be
a subset of the states the ceiling covers. Teaching the policy a third state fails that test
and points the author at the ceiling rule.

Richer states belong to Task 4, where weekly dose targets exist to define entry and exit.

## Data flow

```text
UserProfile ──► AdaptationStatePolicy.derive() ──► AdaptationState        (pure)
                                                        │
Clock + ZoneId (container) ─► WeeklyDoseLedgerRepository ┤                (I/O)
                              .currentWeeklyLedger()     │
                                                         ▼
                                    TrainingProgramStateProvider
                                                         │
                                                         ▼
                                          TrainingProgramState
                              WorkoutGenerationContextBuilder (flag-on path only)
                                   • .adaptationState → ExerciseEligibilityPolicy
                                   • state carried on WorkoutGenerationContext
```

The clock and zone come from the application container as `Clock.systemDefaultZone()` and
`ZoneId.systemDefault()`, matching how `WeeklyDoseLedgerRepository` is already constructed. A
device zone change reconstructs a new ledger rather than relabelling an old one, which is the
ledger's existing behavior.

The ternary already sits inside the `if (reviewedCapabilityEligibility)` block, so the
flag-off path structurally cannot reach the provider. Zero added I/O in production is a
property of where the code lives, not something this design has to enforce.

### Carrying an unread ledger

`TrainingProgramState` was placed on `WorkoutGenerationContext` before a policy read
`weeklyLedger`. The alternative — composing only `adaptationState` and adding the ledger
when a consumer existed — would have avoided computing a temporarily unread value.

Carrying it won because the cost landed only on the flag-on path and Task 4 needed the
ledger at exactly this point in the flow. Task 4 now consumes it there, so the temporary
unread cost has ended. The recommendation snapshot remains deferred.

## Error handling

The provider performs no error translation. `WeeklyDoseLedgerRepository` already propagates
catalog and database failures rather than returning an empty ledger, and an unreadable catalog
is not an empty training week. `AdaptationStatePolicy` is total over `UserProfile` and cannot
fail.

## Testing

| Test | Proves |
| --- | --- |
| `AdaptationStatePolicyTest` | Both states, including the `returningAfterBreakWeeks == 0` boundary |
| Coupling guard | Emittable states ⊆ the states the advanced ceiling covers |
| `TrainingProgramStateProviderTest` | Composition, against a stub repository returning a real `WeeklyDoseLedger` |
| `WorkoutGenerationContextBuilderTest` | Flag-off output unchanged; flag-on context carries the state |
| Existing planner fixture corpus | No planner selection change |

Tests use real domain models. The ledger stub returns a genuine `WeeklyDoseLedger` rather
than asserting on mock interactions.

## Deliberately not included

- Any UI, copy, or screen change.
- Metadata approval, or any change to review state or provenance.
- Richer adaptation states, and any state transition logic.
- Weekly dose targets, progression, deloads, substitutions, or program blocks.
- Reconciling the Progress screen's weekly per-muscle card with ledger semantics. That card
  credits every legacy `primaryMuscles` entry over a rolling 168-hour window, which
  contradicts `PRIMARY_ONLY_V1` on crediting, week boundary, and metadata gating. Switching
  it today would empty it, because zero entries are `APPROVED`. Recorded here as a known
  divergence; it is its own milestone.
- Enabling `reviewedCapabilityEligibility` in production.

## Verification

```bash
./gradlew testDebugUnitTest \
  --tests '*AdaptationStatePolicyTest' \
  --tests '*TrainingProgramStateProviderTest' \
  --tests '*WorkoutGenerationContextBuilderTest' \
  --tests '*PlannerFixture*' \
  --rerun-tasks --no-daemon
```

Full verification also runs the Workout Guide Python tests, all JVM unit tests, Android lint
and assembly, connected Android tests, and `git diff --check`.
