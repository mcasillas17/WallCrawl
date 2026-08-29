# Body-Aware Workout Personalization Design

## Goal

Extend WallCrawl's deterministic planner so two people with the same equipment and stated experience can receive different, appropriate exercise progressions when their body context and current movement capability differ.

The feature must remain local-first, respectful, explainable, and non-diagnostic. It must never treat body weight or BMI as a proxy for effort, health, discipline, or ability.

## Current State

WallCrawl currently stores goals, experience, schedule, equipment, units, muscle priorities, explicit training constraints, return-after-break duration, confirmed starting loads, and theme preference. It stores no body weight, height, BMI, age, body composition, or movement-capability profile.

The generation context already carries experience, frequency, constraints, bounded history, and recently trained muscles. The current rule planner does not consume experience, frequency, recovery, or body context during exercise selection. Exercise programming metadata contains movement pattern, difficulty, mechanics, fatigue, equipment, progression type, alternatives, and a coaching summary, but no body-mass demand, impact, floor-transition, balance, or support requirements.

## Approaches Considered

### BMI-only gating

Collect height and weight, calculate BMI, and exclude exercises by BMI category.

Rejected. A single derived ratio cannot establish movement capability, pain, balance, conditioning, or relative strength. It would create opaque and potentially stigmatizing exclusions.

### Capability-only planning

Ask users what movements they can currently perform and ignore measurements.

This is safe and directly actionable, but it loses useful context for ranking exercises whose external demand scales with body mass. It also prevents future relative-strength and bodyweight-progression analysis.

### Hybrid capability-first planning

Collect optional height and body weight, require a short movement-capability profile, and combine those values with exercise-demand metadata and observed workout history.

Chosen. Capability and explicit constraints determine hard eligibility. Measurements can only apply a soft ranking preference among otherwise eligible exercises. Successful demonstrated performance overrides conservative assumptions.

## Terminology and UX

The product uses neutral terms:

- **Body measurements**: optional height and body weight.
- **Movement capability**: what currently feels comfortable or should be avoided.
- **Support need**: whether an exercise needs external support, assistance, or a regression.
- **Body-mass demand**: how much of the user's body mass the movement requires them to support or move.

The UI must not label a person as overweight, obese, unhealthy, unfit, or high risk. It must not claim medical clearance or injury prevention.

## Profile Data

Measurements are optional and stored canonically:

```kotlin
data class BodyMeasurements(
    val weightKg: Double? = null,
    val heightCm: Double? = null
) {
    val bmi: Double?
        get() {
            val weight = weightKg ?: return null
            val heightMeters = (heightCm ?: return null) / 100.0
            return (weight / (heightMeters * heightMeters))
                .takeIf { it.isFinite() && it > 0.0 }
        }
}
```

BMI is derived in memory and is not persisted as a separate value. The initial deterministic policy does not use BMI bands or diagnostic categories.

Movement capability is required during onboarding, with `UNKNOWN` available when the user genuinely cannot answer:

```kotlin
enum class CapabilityLevel {
    UNKNOWN,
    COMFORTABLE,
    LIMITED,
    AVOID
}

data class MovementCapabilities(
    val impact: CapabilityLevel,
    val floorTransition: CapabilityLevel,
    val unsupportedSquat: CapabilityLevel,
    val upperBodyBodyweightPush: CapabilityLevel,
    val verticalPullOrHang: CapabilityLevel,
    val balanceWithoutSupport: CapabilityLevel,
    val continuousActivity: CapabilityLevel
)
```

`UserProfile` gains `bodyMeasurements` and `movementCapabilities`. Existing `TrainingConstraint` values remain separate because a user may be comfortable with a movement pattern while still choosing to protect a sensitive joint.

## Onboarding and Profile Experience

Add one **Body & Movement** onboarding step after Experience & Units:

1. Explain that measurements are optional, remain on-device, and help estimate the demand of bodyweight movements.
2. Accept weight in the selected profile unit and height in centimeters or feet/inches; normalize to kg/cm before persistence.
3. Ask capability questions as simple cards with Comfortable, Limited, Avoid, and Not sure.
4. Explain that answers can be edited later and will be corrected by demonstrated workout history.

Skipping measurements is always allowed. Completing the capability questions is required, but `UNKNOWN` is a valid explicit answer.

The Profile screen exposes the same values under **Body & Movement**, with a delete-measurements action that leaves capability answers intact.

## Exercise Demand Metadata

Reviewed programming entries gain:

```kotlin
enum class BodyMassDemand {
    MINIMAL,
    PARTIAL,
    SUBSTANTIAL,
    FULL
}

enum class ImpactLevel {
    NONE,
    LOW,
    HIGH
}

enum class SupportRequirement {
    SUPPORTED,
    OPTIONAL_SUPPORT,
    UNSUPPORTED
}

data class ExerciseDemandMetadata(
    val bodyMassDemand: BodyMassDemand,
    val estimatedBodyMassFraction: Double?,
    val impactLevel: ImpactLevel,
    val requiresFloorTransition: Boolean,
    val balanceDemand: CapabilityLevel,
    val supportRequirement: SupportRequirement,
    val capabilityRequirements: Set<MovementCapabilityType>,
    val regressionExerciseIds: List<String>
)
```

`estimatedBodyMassFraction` is optional, bounded to `0.0..1.5`, and only populated after review. It is used for relative ranking, never as an automatic load prescription.

Demand metadata is human-reviewed and versioned with programming overrides. It is never inferred and auto-approved by an LLM.

## Deterministic Policy

The policy has three layers.

### Hard filters

Hard filters remove an exercise only when:

- an explicit training constraint blocks it;
- the user explicitly selected `AVOID` for a required capability;
- `LOW_IMPACT_ONLY` conflicts with `ImpactLevel.HIGH`;
- required equipment is absent;
- the exercise lacks reviewed metadata required for automatic planning.

Body weight, height, or BMI alone never hard-filter an exercise.

### Soft ranking

Among eligible candidates:

- prefer supported or partial-body-mass regressions when a capability is `LIMITED`;
- prefer lower impact when impact is `UNKNOWN`;
- prefer no-floor alternatives when floor transition is `LIMITED`;
- prefer lower balance demand when balance is `LIMITED`;
- when weight and `estimatedBodyMassFraction` are both available, calculate an estimated supported mass only to order exercises within the same progression family;
- prefer demonstrated successful exercises over conservative defaults.

No universal body-weight or BMI threshold is used.

### Prescription scaling

Body measurements never generate a starting external load. Existing confirmed-load and history rules remain authoritative.

Capability may conservatively reduce sets, duration, or exercise progression level. It must not increase volume or load. Return-after-break scaling still applies, and the most conservative applicable result wins.

## Precedence

From strongest to weakest:

1. Explicit `AVOID` capability and `TrainingConstraint`.
2. Completed-session evidence and current user overrides.
3. Explicit `LIMITED` or `COMFORTABLE` capability.
4. Return-after-break policy.
5. Optional body measurements combined with reviewed demand metadata.
6. Conservative `UNKNOWN` defaults.

One successful set does not permanently upgrade capability. A versioned evidence policy requires repeated successful sessions before relaxing a soft penalty. Explicit user changes apply immediately.

## Data Flow

```text
Onboarding/Profile
  -> BodyMeasurements + MovementCapabilities
  -> Room UserProfile
  -> WorkoutGenerationContextBuilder
  -> ExerciseEligibilityPolicy hard filters
  -> BodyAwareExerciseRanker soft ranking
  -> deterministic prescription scaling
  -> ProgramValidator
  -> Today recommendation
  -> completed history
  -> capability evidence for later recommendations
```

The future local LLM receives only candidates that survive these deterministic boundaries. Measurements are omitted from prompts unless a separate, explicit design proves they are necessary.

## Privacy

- Measurements and capability answers remain in local Room storage.
- No account, network call, analytics event, model prompt, Wear payload, or Health Connect permission is added.
- Export and delete controls must include these fields when local data controls ship.
- Logs and crash messages must not contain raw measurements.
- Removing measurements immediately removes their ranking influence.

## Migration and Compatibility

Advance Room from schema 7 to 8 with nullable `bodyWeightKg`, nullable `heightCm`, and a non-null capability JSON field.

Existing users receive null measurements and every capability set to `UNKNOWN`. They remain onboarded and may continue using the app; show a dismissible profile-completion card rather than forcing onboarding again.

The migration is additive and preserves profile revision, workout history, templates, and theme preference.

## Errors and Fallbacks

- Reject non-finite or non-positive measurements and implausible input bounds before persistence.
- Unit conversion occurs only at the UI/repository boundary; Room stores kg/cm.
- Unknown or malformed capability values decode to `UNKNOWN`, never `COMFORTABLE`.
- Missing exercise-demand metadata makes an exercise unavailable for automatic planning but not browsing or manual templates.
- If body-aware filtering leaves no candidates, retry without measurement-based soft preferences; never remove explicit hard constraints.
- Explain recommendation changes with capability language, not body labels.

## Testing and Evaluation

Add persona fixtures:

- same equipment/experience, different body weight, unknown capability;
- same body weight, Comfortable versus Limited floor transition;
- bodyweight beginner with Limited push capability;
- band-only user with Avoid impact;
- advanced user with demonstrated pull-up history;
- returning user with break scaling plus Limited balance;
- measurements omitted;
- malformed stored measurements/capabilities;
- unit changes with canonical measurement persistence.

Required invariants:

- BMI alone never changes hard eligibility.
- Explicit Avoid always wins.
- Demonstrated capability can remove a soft penalty but not a hard constraint.
- Body measurements never produce an external starting load.
- The same inputs and policy version produce the same plan.
- Every recommendation remains valid when measurements are deleted.

## Rollout

1. Ship profile storage and capability UX without changing planner output.
2. Add reviewed demand metadata for the curated automatic-planning cohort.
3. Enable hard capability constraints and measure plan availability across persona fixtures.
4. Enable soft ranking behind a local feature flag.
5. Enable history-derived capability evidence only after replay tests pass.

## Acceptance Criteria

- Users can optionally store/delete height and weight locally.
- Every user explicitly records movement capabilities or Unknown.
- Automatic planning uses reviewed exercise-demand metadata.
- No body measurement or BMI is a sole exclusion.
- Limited capability selects appropriate supported regressions when available.
- Existing users migrate without data loss or forced re-onboarding.
- Recommendations explain capability-driven choices respectfully.
- Unit, migration, planner, and persona tests pass offline.

## Roadmap Integration

This design narrows Adaptive Coach Tasks 3-5:

- Task 3 becomes reviewed exercise eligibility plus demand metadata.
- Task 4 becomes experience-, frequency-, recovery-, capability-, and body-aware ranking.
- Task 5 validates whole-session difficulty, demand, fatigue, volume, duration, and duplicates.

It must land before local-model ranking. Rest timers and RPE/RIR remain the next feedback-loop work after deterministic selection is trustworthy.
