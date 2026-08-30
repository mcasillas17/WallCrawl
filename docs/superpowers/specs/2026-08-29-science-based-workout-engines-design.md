# Science-Based Workout Engines Design

## Product Boundary

WallCrawl builds a deterministic multi-week resistance-training program and a valid Today session entirely on-device. A future local LLM is optional and may only parse non-safety preferences, rerank already-eligible IDs, and explain deterministic reasons.

The evidence review at `docs/research/2026-08-29-training-science-evidence-review.md` is the authority for research claims. Product defaults remain versioned policy, not individualized medical truth.

## Deterministic State Machine

```text
NEEDS_ONBOARDING -> INITIATE -> BUILD -> DEVELOP
                         \-> HOLD
                         \-> RETURNING
                         \-> DELOAD_OFFERED
any state -> RECALIBRATE
```

- `INITIATE`: no mandatory weekly floor; simple, feasible exposure.
- `BUILD`: maintain a tolerated weekly direct-primary range.
- `DEVELOP`: progress one variable after repeated comparable success.
- `HOLD`: preserve or reduce demand.
- `RETURNING`: re-enter conservatively after a break.
- `DELOAD_OFFERED`: present user-controlled reduction options.
- `RECALIBRATE`: policy/profile/capability changes or inadequate history.

## Core Model

```kotlin
data class WeeklyDoseLedger(
    val policyVersion: Int,
    val weekStartEpochDay: Long,
    val directPrimarySets: Map<String, Int>,
    val secondaryInvolvement: Map<String, Int>
)

data class TrainingProgramState(
    val policyVersion: Int,
    val adaptationState: AdaptationState,
    val weeklyLedger: WeeklyDoseLedger,
    val deloadOffer: DeloadOffer?
)
```

`PRIMARY_ONLY_V1` credits one designated direct-primary muscle per completed work set. Secondary involvement remains analytics only.

## Decision Pipeline

```text
profile + capability + constraints + equipment + history
  -> reviewed-metadata hard eligibility
  -> weekly ledger and calibration state
  -> deterministic candidate ranking
  -> deterministic prescription compilation
  -> session and weekly validation
  -> one deterministic repair
  -> immutable recommendation snapshot
```

Hard rules cannot be repaired away. Missing inputs become conservative or typed failures, never guessed success.

## Dose and Adaptation

- Weekly state drives exposure; session set totals only constrain time/tolerance.
- RIR is nullable. Guidance is 2-4 for INITIATE/RETURNING/LIMITED and 1-3 for established general/hypertrophy work. Failure is never automatic.
- Rest uses editable classes, not claimed-optimal exact seconds.
- Starting external load comes only from confirmed load or comparable history.
- Experience temporarily blocks uncalibrated advanced-complexity automatic work, then becomes a soft rank.
- Two comparable completed sessions plus explicit confirmation may relax a soft capability penalty.
- Deload is an offer based on user request, returning state, or transparent multi-session patterns.

## Reviewed Metadata

Automatic candidates require categorical, versioned, human-reviewed:

- direct-primary and descriptive-secondary muscles;
- exercise type, equipment alternatives, pattern, complexity;
- progression family and prescription shape;
- approved regressions/substitutions;
- capability requirements, support requirement, impact class;
- reviewer identity/role, rationale/source, review date, schema/policy version.

No numeric joint/SFR/axial/fatigue scores, body-mass fractions, or general ROM bonus enter v1 policy.

## LLM Contract

```kotlin
interface WorkoutCandidateRanker {
    suspend fun rank(
        slots: List<CandidateSlot>,
        eligibleIds: Set<String>,
        preferences: NonSafetyPreferences,
        policyVersion: Int
    ): RankedCandidateResponse
}
```

The prompt contains no raw measurements, capabilities, constraints, notes, full history, Room rows, or dose. Output contains only candidate selections and explanation keys. Deterministic code compiles all prescriptions and validates the result.

Any model failure, malformed output, unsupported field, unknown/duplicate ID, cancellation, or device constraint returns the unchanged deterministic recommendation.

## Evaluation

The deterministic engine requires property/replay tests, persona fixtures, catalog provenance review, and immutable audit reconstruction. The LLM additionally requires strict schema/adversarial tests, blind expert no-worse review, opt-in human benefit, and device-specific latency/battery/thermal testing.

The LLM remains disabled until every gate passes. Offline benchmark accuracy alone is insufficient.

