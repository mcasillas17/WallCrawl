# Reviewed capability eligibility

## Status and boundary

WallCrawl has a deterministic eligibility layer for a future reviewed automatic
planner. Production composition sets `PlannerFeatureFlags.reviewedCapabilityEligibility`
to `false`, so today's automatic recommendations continue to use the legacy
`ExerciseFilter` and `programming` metadata. The flag is local and controlled by
application composition; there is no remote configuration, analytics, or
automatic activation when an entry becomes approved.

The bundled catalog remains at 302 exercises. Its 37 `reviewedMetadata` entries
remain `DRAFT`, with zero `APPROVED` entries. A merge or model review is not human
metadata approval. This milestone does not change authored review state,
reviewer identity, timestamps, or provenance.

The typed flow is:

```text
local UserProfile + bundled catalog + parser-validated reviewed metadata
  -> ExerciseEligibilityPolicy
  -> per-exercise EligibilityDecision values
  -> AutomaticEligibilityResult.Candidates or NoCandidates
  -> allowed automatic candidates or a typed planner failure
```

The policy performs no I/O, logging, persistence, analytics, or network access.
It does not read body weight, height, age, BMI, or body composition, and it does
not make safety, medical-clearance, or injury-prevention claims.

## Deterministic rule order

Rules are evaluated in incoming catalog order. Reasons within a decision use
the order below, and aggregate failure selection replays the same stages. The
aggregate cause is the first stage that exhausts the candidates that survived
earlier stages.

| Stage | Hard decision | Typed reason | Aggregate failure |
| --- | --- | --- | --- |
| 1 | Explicitly excluded exercise | `USER_EXCLUDED` | `USER_EXCLUSIONS_REMOVED_ALL` |
| 2 | No complete reviewed equipment alternative is available | `MISSING_EQUIPMENT` | `EQUIPMENT_REMOVED_ALL` |
| 3 | Metadata is absent or not `APPROVED` | `MISSING_APPROVED_METADATA` | `NO_APPROVED_METADATA` |
| 4 | A required capability is `AVOID` | `CAPABILITY_AVOID` | `CAPABILITIES_REMOVED_ALL` |
| 5 | A joint-sensitive constraint has no reviewed exercise mapping | `UNMAPPED_TRAINING_CONSTRAINT` | `TRAINING_CONSTRAINTS_REMOVED_ALL` |
| 5 | `LOW_IMPACT_ONLY` meets `ImpactLevel.HIGH` | `HIGH_IMPACT_DISALLOWED` | `TRAINING_CONSTRAINTS_REMOVED_ALL` |
| 6 | `ADVANCED` is temporarily above the uncalibrated/returning ceiling | `ADVANCED_WHILE_UNCALIBRATED` or `ADVANCED_WHILE_RETURNING` | `CALIBRATION_COMPLEXITY_REMOVED_ALL` |

An empty input or a combination not attributable to one of those exhaustion
stages uses `NO_ELIGIBLE_CANDIDATES`. Every result retains every per-exercise
decision. Callers do not derive causes from exception text.

An eligible exercise has the hard reason `APPROVED`. `EligibilityPreference`
is a separate type for soft inputs: each explicitly required capability that is
`LIMITED` or `UNKNOWN` is retained in enum order. Those values do not reject the
exercise and cannot be confused with a hard reason by a later ranker.

## Reviewed equipment and capability semantics

`reviewedMetadata.equipmentAlternatives` is an OR-of-AND contract. At least one
inner equipment list must be a complete subset of the profile's available
equipment. The policy normalizes equipment names for comparison and does not
mutate either list. `listedEquipment` and legacy
`programming.requiredEquipmentCombinations` remain compatibility data; they do
not weaken an approved reviewed requirement.

`CapabilityLevel.AVOID` is a hard exclusion only when approved metadata names
that exact `MovementCapabilityType`. `LIMITED` and `UNKNOWN` are soft inputs.
History does not rewrite capability answers, and the policy does not infer them
from exercise names, muscles, equipment, experience, or measurements.

## Calibration complexity

`ComplexityTier.ADVANCED` is temporarily unavailable only in `UNCALIBRATED` and
`RETURNING`. It remains eligible in the other adaptation states. The temporary
ceiling is lifted when the user has demonstrated that approved progression
family or when the catalog contains an available, approved, supported regression
that passes the same exclusion, reviewed-equipment, capability, and training-
constraint rules. The regression must itself be below the temporary advanced
ceiling, unless its own progression family has demonstrated history. A DRAFT,
undemonstrated advanced, or otherwise unavailable regression cannot lift the ceiling.
Experience level alone is not a permanent exclusion.

The current disabled builder can derive `RETURNING` from a reported break and
otherwise uses `UNCALIBRATED`; richer adaptation state is a later program-state
milestone. That limitation is one reason production enablement remains blocked.

## Constraint metadata gap

The reviewed contract can enforce `LOW_IMPACT_ONLY` because `impactLevel` is
reviewed. It has no reviewed per-exercise mapping for shoulder, elbow, wrist,
lower-back, hip, or knee sensitivity. The policy therefore fails closed with
`UNMAPPED_TRAINING_CONSTRAINT` instead of guessing from names, muscles, or broad
movement patterns.

A future human-reviewed categorical compatibility extension is required before
profiles with those active constraints can use the reviewed automatic gate.
Adding that field would change the review scope and is not part of this work.

## Rollout and manual-workout preservation

When the flag is disabled, `WorkoutGenerationContext.automaticEligibilityResult`
is `null`, `ExerciseFilter` supplies the same ordered candidate list, and the
current planner receives the same domain inputs and produces the same output for
representative personas. When enabled in tests, the reviewed policy alone
supplies `allowedExercises`; an empty result is surfaced as a typed reviewed-
eligibility failure. There is no fallback to an unreviewed exercise and no rule
is relaxed to make a plan succeed.

The gate exists only on automatic context construction. The exercise library
continues to read the full catalog, and the manual template editor continues to
read all 302 exercises and display its existing profile-equipment warnings.
Missing or DRAFT reviewed metadata does not hide a browse or manual option.

## Test-only approvals and verification

Focused unit tests construct `APPROVED` metadata only in memory, with provenance
that says `Synthetic test-only reviewer` and a source beginning with `SYNTHETIC`.
The enabled planner fixtures copy selected bundled DRAFT records in memory and
replace provenance with `SYNTHETIC PLANNER FIXTURE — never bundled in production
assets.` Fixture resources list IDs only; no synthetic approval is written to
the production catalog or authored metadata JSON.

Focused verification:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME=/Users/elopenmike/Library/Android/sdk \
./gradlew testDebugUnitTest \
  --tests '*ExerciseEligibilityPolicyTest' \
  --tests '*WorkoutGenerationContextBuilderTest' \
  --tests '*FakeWorkoutPlannerTest' \
  --tests '*PlannerFixture*' \
  --rerun-tasks --no-daemon
```

Full repository verification also runs the Workout Guide Python tests, all JVM
unit tests, Android lint and assembly, connected Android tests, and
`git diff --check`.

## Deliberately incomplete work

This milestone does not approve metadata, enable production rollout, rank soft
preferences, add a weekly dose ledger or Room migration, infer capability
evidence, implement progression/RPE/RIR/deload/program blocks, substitute an
active workout, integrate an LLM, or add remote services.

The next enablement requirement is deliberate human review and approval of the
metadata, followed by an explicit availability/persona review and a deliberate
production flag change. Approval must not happen automatically as a side effect
of catalog growth or pull-request review.
