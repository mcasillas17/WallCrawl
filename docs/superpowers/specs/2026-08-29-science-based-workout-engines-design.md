# Science-Based Workout Engines Design

> **Design record:** Current status, priority, and dependency order live in the
> [canonical roadmap](../../../ROADMAP.md). This document preserves the reviewed architecture
> and safety boundaries for the deterministic and optional local-model engines.

This document is the detailed architecture for WallCrawl's two workout-planning
engines: a mandatory on-device **deterministic engine** and an optional,
strictly bounded **local LLM assistant**. It consolidates the four-agent
roundtable (see the appendix at
`docs/research/2026-08-29-roundtable-agent-findings.md`) and the twelve signed v1
decisions recorded in `docs/research/2026-08-29-training-science-evidence-review.md`.
Detailed implementation is specified by
`docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md` and
`docs/superpowers/plans/2026-08-29-science-based-local-llm-engine.md`, and the
body-context inputs are specified in
`docs/superpowers/specs/2026-08-29-body-aware-personalization-design.md` and
`docs/superpowers/plans/2026-08-29-body-aware-personalization.md`.

## Final Roundtable Consensus

After four research and adversarial-review rounds, Claude Opus 4.8, Grok 4.6,
Gemini 3.7 Flash, and GPT-5.6 Terra explicitly signed these twelve v1 decisions:

1. **`PRIMARY_ONLY_V1` weekly ledger.** One completed working set credits one
   designated direct-primary muscle. Secondary involvement is descriptive only;
   fractional credit remains a versioned research backlog.
2. **State-based dose ranges.** Weekly exposure uses editable product-policy
   ranges, never mandatory scientific floors. Session totals constrain duration
   and tolerance rather than defining the evidence-based dose.
3. **Nullable, editable effort guidance.** INITIATE/RETURNING states or a
   relevant `LIMITED` capability default to 2-4 RIR guidance; established
   general/hypertrophy work defaults to 1-3 RIR. Failure is never an automatic
   target.
4. **Editable rest classes.** `SHORT`, `MODERATE`, and `LONG` are resolved by a
   versioned product policy and may reuse user history. No exact duration is
   claimed as universally optimal.
5. **Narrow experience ceiling.** Advanced-complexity automatic work is
   temporarily unavailable only while uncalibrated/returning and lacking
   demonstrated history or a supported regression. Experience is otherwise a
   soft complexity input.
6. **Capability evidence requires confirmation.** Two comparable completed
   sessions plus explicit user confirmation may relax a soft penalty. This is a
   reproducibility policy, not a physiological threshold, and never relaxes a
   hard constraint.
7. **Today remains an RT session.** A program horizon may record or surface
   user-selected aerobic activity and public-health education, but v1 does not
   prescribe obesity treatment, individualized aerobic dose, or Health Connect
   fatigue/recovery.
8. **`DeloadOffer`, never diagnosis.** Reduced-demand options may be offered from
   user request, returning state, or a transparent multi-session pattern. There
   is no fixed calendar, percentage, RIR, volume, or diagnostic threshold.
9. **Categorical reviewed metadata only.** V1 retains actionable categorical
   muscle, pattern, complexity, progression-family, regression/substitution,
   capability, support, impact, equipment, and provenance fields. Numeric
   joint/SFR/axial/fatigue scores, body-mass fractions, and general ROM bonuses
   are excluded.
10. **LLM enablement is evidence-gated.** Reranking remains disabled until the
    deterministic engine is complete, hard-constraint/fallback tests are perfect,
    schemas exclude dose/body/safety, blind expert review is no worse, opt-in
    evaluation shows predefined benefit, and device SLOs are measured.
11. **Body measurements are deferred.** The capability-input milestone stores no
    weight, height, or BMI. Optional measurements are not required by this
    roadmap, and neither deterministic nor LLM engines may consume them.
12. **Reviewed-only automatic planning.** Automatic plans use reviewed
    programming and demand metadata; the complete catalog remains available for
    browsing and manual templates.

Every agent retracted conflicting earlier proposals before sign-off. The
roundtable appendix records the retractions and citation corrections.

## Product Boundary

WallCrawl builds a deterministic, replayable, multi-week resistance-training
(RT) program and a valid Today session entirely on-device, offline-first, with
no network dependency. Deterministic Kotlin/Room code owns every decision that
touches safety, eligibility, dose, effort, progression, substitution legality,
validation, persistence, audit, and fallback.

A future local LLM is optional, opt-in, removable, and disabled by default. When
enabled it may only: parse non-safety free-text preferences into a strict schema
for explicit user confirmation, rerank exercise IDs that the deterministic engine
has already declared eligible, and verbalise deterministic reason codes as user
copy. It never prescribes, never invents identifiers or loads, and never
adjudicates safety. Any model failure returns the unchanged deterministic
recommendation.

The evidence review is the authority for research claims. This document
distinguishes two kinds of statement throughout:

- **Population-level evidence** — a citation supports an inference about a
  studied population under its own method and sample limits. It never licenses a
  universal individual prescription.
- **Product policy** — a versioned, editable WallCrawl default (for example a
  rest-class duration or a default RIR band). Policy is a design choice, labelled
  as such, and must never be presented in code, tests, or UI as an
  evidence-derived or medical threshold.

## Evidence Doctrine

The engine encodes only the consensus doctrine that survived adversarial
cross-review. Confidence labels and canonical citations live in the evidence
review; the engineering consequences are:

1. Consistent, feasible participation precedes optimisation; complexity is not a
   quality metric. Prefer plans a user can actually complete.
2. The scientific dose unit is **weekly exposure per muscle**, not per-session
   set count. Session totals are duration/tolerance guardrails.
3. Frequency mainly distributes weekly dose when volume is equated; schedule
   around availability and never claim frequency independently grows muscle.
4. Heavier, specific loading favours maximal-strength outcomes; hypertrophy is
   achievable across a broad load range given sufficient effort.
5. Training to failure is not required and increases acute fatigue; no single
   RIR is validated as universal. Effort is guidance, never auto-forced.
6. RPE/RIR and performance are fallible adaptation inputs; missing values remain
   missing, and neither self-report nor wearables diagnose recovery.
7. Full, comfortable ROM is the default; long-length partials are muscle-specific
   options, not a general ranking bonus.
8. Machines, free weights, bands, and bodyweight are viable modalities; strength
   is modality-specific and universal hypertrophy equivalence is not claimed.
9. Concurrent aerobic and RT generally preserve hypertrophy/strength; same-session
   work may modestly affect explosive outcomes.
10. BMI/body mass does not determine capability; v1 planning consumes neither.
11. Explicit constraints and capability `AVOID` are hard; `LIMITED`/`UNKNOWN` are
    conservative soft inputs; demonstrated history relaxes only soft penalties.
12. Deloads are user-controlled offers, not diagnoses or calendar laws.
13. Deterministic code owns safety and dose; the LLM remains bounded and optional.

No numeric joint-stress, stimulus-to-fatigue, axial-load, or fatigue score, no
body-mass fraction, and no general ROM bonus enters v1 policy. These were
proposed during the roundtable and explicitly retracted (see the appendix).

## Module and Component Map

```text
core/model
  Exercise, ReviewedExerciseMetadata, ReviewProvenance
  TrainingProgramState, WeeklyDoseLedger, EffortTarget, RestClass
  MovementCapabilities (capability-input spec)
core/ai (deterministic)
  ExerciseEligibilityPolicy      hard gate
  WeeklyDoseLedgerCalculator     PRIMARY_ONLY_V1 crediting
  CalibrationStateResolver       adaptation-state derivation
  TrainingPolicy                 versioned dose/effort/rest defaults
  BodyAwareExerciseRanker        soft ranking
  DefaultExercisePrescriptionFactory  dose compilation
  CapabilityEvidencePolicy       history-based soft relaxation
  ProgressionEngine              one-variable progression
  DeloadOfferPolicy              transparent reduction offer
  ProgramValidator               session + weekly validation, one repair
  FakeWorkoutPlanner / planner   orchestration + immutable snapshot
core/ai/local (optional LLM)
  LlmReadinessGate               deterministic enablement gate
  LocalModelRuntime              provider abstraction, tiers, deadlines
  PreferenceParser               bounded non-safety parsing
  WorkoutCandidateRanker         eligible-ID reranking
  WorkoutExplanationService      reason-key rendering
  TieredWorkoutPlanner           fallback orchestration + audit
core/database
  Entities, Daos, WallCrawlDatabase   immutable history + program state
feature/today, feature/workout, feature/profile
  Today session, active logging, capability UX
tools/workout-guide
  import_catalog.py, review-schema.json, programming-overrides.json
```

Deterministic modules are the trusted computing base. The `core/ai/local`
package is a leaf that can be removed without changing any core workout.

## Domain Schemas

```kotlin
enum class AdaptationState { NEEDS_ONBOARDING, UNCALIBRATED, INITIATE, BUILD, DEVELOP, HOLD, RETURNING, DELOAD_OFFERED, RECALIBRATE }
enum class RestClass { SHORT, MODERATE, LONG }
enum class ComplexityTier { FOUNDATIONAL, STANDARD, ADVANCED }
enum class ImpactLevel { NONE, LOW, HIGH }
enum class SupportRequirement { SUPPORTED, OPTIONAL_SUPPORT, UNSUPPORTED }
enum class ReviewState { DRAFT, APPROVED }
enum class PrescriptionShape { WEIGHT_REPS, BODYWEIGHT_REPS, ASSISTED_BODYWEIGHT, DURATION }

data class EffortTarget(val minRir: Int?, val maxRir: Int?)   // nullable; failure never auto-set

data class ReviewProvenance(
    val reviewerRole: String?,             // null while DRAFT
    val rationaleOrSource: String,
    val reviewedAtEpochMillis: Long?,      // null while DRAFT
    val schemaVersion: Int,
    val policyVersion: Int
)

data class ReviewedExerciseLink(
    val exerciseId: String,
    val rationale: String? = null
)

data class ReviewedExerciseMetadata(
    val reviewState: ReviewState,
    val directPrimaryMuscle: String,
    val descriptiveSecondaryMuscles: Set<String>,
    val movementPattern: MovementPattern,
    val complexity: ComplexityTier,
    val progressionFamily: String,
    val prescriptionShape: PrescriptionShape,
    val approvedRegressions: List<ReviewedExerciseLink>,
    val approvedSubstitutions: List<ReviewedExerciseLink>,
    val capabilityRequirements: Set<MovementCapabilityType>,
    val supportRequirement: SupportRequirement,
    val impactLevel: ImpactLevel,
    val equipmentAlternatives: List<List<String>>,
    val provenance: ReviewProvenance
)

data class WeeklyDoseLedger(
    val policyVersion: Int,
    val weekStartEpochDay: Long,
    val directPrimarySets: Map<String, Int>,      // credited dose
    val secondaryInvolvement: Map<String, Int>     // analytics only, no dose
)

data class TrainingProgramState(
    val policyVersion: Int,
    val adaptationState: AdaptationState,
    val weeklyLedger: WeeklyDoseLedger,
    val recentComparableOutcomes: List<ComparableOutcome>,
    val deloadOffer: DeloadOffer?,
    val returnStatus: ReturnStatus
)

data class RecommendationSnapshot(
    val contextHash: String,
    val catalogVersion: Int,
    val reviewVersion: Int,
    val policyVersion: Int,
    val ledgerVersion: Int,
    val reasonCodes: List<String>,
    val validatorResult: ValidatorResult,
    val prescriptions: List<ExercisePrescription>
)
```

`MovementCapabilities` is defined in the capability-input spec. Body
measurements remain deferred and absent from the profile model.
`ExercisePrescription` is the existing shared core model. Loads are `null`
until confirmed by history or explicit user confirmation.

## Adaptation State Machine

```text
NEEDS_ONBOARDING
  -> UNCALIBRATED -> INITIATE -> BUILD -> DEVELOP
                          \-> HOLD
                          \-> RETURNING
                          \-> DELOAD_OFFERED
any state -> RECALIBRATE
```

Entry/exit conditions are deterministic and derived from immutable completed
history plus the current profile; they never read BMI or body mass.

- **NEEDS_ONBOARDING** — entry: missing goals, equipment, availability, or the
  required movement-capability answers (`UNKNOWN` is a valid explicit answer).
  Exit: all required inputs present.
- **UNCALIBRATED** — entry: no confirmed load and insufficient comparable
  history. Behaviour: no numeric external starting load; reviewed,
  capability-compatible, non-advanced candidates only; explicit confirm-load
  flow. Exit: enough completed comparable sessions to seed calibration.
- **INITIATE** — entry: calibrated but early. Behaviour: small feasible weekly
  exposure; no mandatory weekly floor. Exit: repeated tolerated completion.
- **BUILD** — behaviour: maintain a tolerated, editable weekly direct-primary
  range. Exit up: repeated comparable success plus user acceptance. Exit down:
  repeated shortfall or user request.
- **DEVELOP** — behaviour: progress exactly one variable after repeated
  comparable success. Exit: shortfall, pain-stop, or user request returns to
  BUILD/HOLD.
- **HOLD** — entry: repeated shortfall, higher-than-usual reported effort at a
  matched task, repeated skips, explicit poor-readiness report, or user
  preference. Behaviour: preserve or reduce demand. Exit: recovery of comparable
  performance or user request.
- **RETURNING** — entry: reported break or long logged gap. Behaviour: re-enter
  INITIATE/BUILD conservatively by history adequacy and user choice; no fixed
  percentage reduction. Exit: repeated tolerated completion.
- **DELOAD_OFFERED** — entry: user request, returning state, or a transparent
  versioned multi-session pattern. Behaviour: present accept/hold/reduce/shorter
  options; user chooses. Exit: user decision recorded.
- **RECALIBRATE** — entry: policy-version change, profile/capability edit,
  measurement deletion, catalog change, or inadequate/conflicting history.
  Behaviour: preserve immutable history, re-establish conservative calibration.

Terminal deterministic paths, not states: `NO_ELIGIBLE_PLAN` (typed
explanation, never drop constraints), `RED_FLAG_ROUTE` (non-diagnostic
seek-support message; the model never adjudicates symptoms),
`VALIDATION_FAILURE` (one repair attempt, then no persistence), and
`MODEL_FAILURE` (irrelevant to safety; deterministic plan proceeds).

## Reviewed Metadata and Provenance

Automatic candidates require `APPROVED`, categorical, versioned, human-reviewed metadata:
direct-primary and descriptive-secondary muscles; exercise type, equipment
alternatives, movement pattern, complexity; progression family and prescription
shape; approved regressions/substitutions; capability requirements, support
requirement, impact level; and full provenance (`reviewerRole`,
`rationaleOrSource`, `reviewedAtEpochMillis`, `schemaVersion`, `policyVersion`).
Tooling may accept AI-authored `DRAFT` entries as candidates for later human
inspection, but they are never auto-approved: drafts omit reviewer role and
review time. Band, machine, supported, bodyweight, and timed-hold families must
be human-reviewed before the automatic gate is enabled, so gating does not
silently strip equipment-limited users of eligible plans. Once that separate
gate is implemented, missing or `DRAFT` metadata makes an exercise unavailable
to automatic planning but leaves it available for browse and manual templates.
Until then, the current planner ignores the new block.

## PRIMARY_ONLY Weekly Ledger

`PRIMARY_ONLY_V1` credits one designated direct-primary muscle per completed
work set. Secondary involvement is recorded as descriptive analytics and may
apply a soft recovery rank penalty, but it receives no dose credit and never
inflates the weekly ledger. Ledgers are derived from immutable completed
sessions on a rolling weekly window; generation never increments a mutable
counter. Fractional secondary crediting is deferred to a future
`LedgerPolicy` version after reviewed secondary-muscle mapping. The `0.5`
secondary-credit variant proposed during the roundtable was retracted.

## Eligibility

Hard eligibility runs before any ranking and cannot be repaired away:

- required equipment combination absent;
- explicit exercise exclusion;
- explicit `TrainingConstraint` conflict (for example a protected joint);
- capability `AVOID` on a required movement capability;
- `LOW_IMPACT_ONLY` conflicting with `ImpactLevel.HIGH`;
- required floor transition when floor-transition capability is `AVOID`;
- missing reviewed automatic-planning metadata;
- temporary advanced-complexity ceiling (below) while uncalibrated/returning.

Body weight, height, and BMI are absent from the eligibility input. Missing or
malformed inputs decode conservatively (capabilities to `UNKNOWN`, never
`COMFORTABLE`), never to a favourable assumption.

## Calibration, Complexity Ceiling, and Ranking

Experience is ordinarily a soft complexity input. While `UNCALIBRATED` or
`RETURNING`, exercises tagged `ADVANCED` are temporarily removed from the
automatic legal set unless the user has demonstrated relevant family history or
an approved supported regression exists. Once calibrated, the ceiling lifts and
complexity becomes a soft rank; capability, explicit constraints, equipment, and
history remain stronger. Catalog difficulty is reviewer opinion, not a safety
score, and never a permanent hard gate on self-reported experience.

Soft ranking, applied only among eligible candidates, prefers: supported or
partial-body-mass regressions when a required capability is `LIMITED`; lower
impact when impact is `UNKNOWN`; no-floor alternatives when floor transition is
`LIMITED`; lower balance demand when balance is `LIMITED`; weekly-ledger-deficit
muscles; priority/compound placement earlier in a session; and demonstrated
successful exercises over conservative defaults. Ties break on a stable exercise
identifier so the same versioned inputs reproduce the same order. No body-mass or
BMI value participates in ranking.

## Dose, Rest, and Effort

- Weekly state drives exposure; session set totals only constrain
  time/tolerance. Volume uses state-based, editable policy ranges. There is no
  mandatory scientific floor and no evidence-labelled automatic increment.
- `EffortTarget` is nullable. Product-policy default guidance is `2..4` RIR for
  INITIATE/RETURNING or a relevant `LIMITED` capability and `1..3` RIR for
  established general/hypertrophy work; strength work keeps an editable low-RIR
  target.
  These are versioned product defaults, not universal evidence-derived
  prescriptions, and `0` RIR (failure) is never an automatic default. Missing
  RIR never implies low effort or readiness.
- Rest uses editable `SHORT`/`MODERATE`/`LONG` classes resolved by versioned
  product policy from exercise type, goal, and prior user preference; the app
  claims no exact number of seconds as physiologically optimal and preserves
  per-exercise user edits.
- Starting external load comes only from confirmed load or comparable history;
  body measurements never generate a starting load, and capability may only
  reduce sets/duration/progression, never increase them.

## Progression

`ProgressionEngine` advances exactly one controllable variable at a time
(reps -> load; or reduce assistance; or add duration/distance for non-load
modalities) after repeated comparable completed success at acceptable effort.
Missing effort is treated as neutral. Incomplete or abandoned sessions cannot
progress capability or credit weekly dose. Policy changes affect only future
recommendations; completed history is immutable.

## DeloadOffer

`DeloadOffer` is user-requested, return-driven, or based on a transparent
versioned multi-session pattern (for example repeated inability to complete
intended work, unexpectedly high reported effort at a matched task, declining
comparable performance, a pain-stop on an otherwise eligible family, or repeated
skipped/truncated sessions). It has no fixed calendar, percentage, RIR, volume,
or diagnostic threshold. Missing RPE/RIR/readiness cannot by itself trigger a
deload or progression. The offer presents hold/reduce/shorter/regression
options, records the user's choice and the inspectable reasons, and never claims
to prevent injury or diagnose recovery. If accepted it reduces session density
while preserving movement families and explicit constraints.

## Session and Weekly Validation

`ProgramValidator` runs after prescription compilation and returns structured
violations:

```kotlin
sealed interface ProgramViolation {
    data class UnknownOrUnreviewedId(val exerciseId: String) : ProgramViolation
    data class HardConstraint(val exerciseId: String) : ProgramViolation
    data class DuplicateFamily(val family: String) : ProgramViolation
    data class InvalidDose(val exerciseId: String) : ProgramViolation
    data class UnconfirmedLoad(val exerciseId: String) : ProgramViolation
    data class DurationMismatch(val planned: Int, val requested: Int) : ProgramViolation
    data class WeeklyLedgerOverflow(val muscle: String) : ProgramViolation
}
```

Validation covers unknown/unreviewed IDs, hard-rule violations, duplicate
exercise/family, invalid dose, unconfirmed load, duration mismatch, and gross
weekly-ledger overflow. Under-target weekly volume is a warning, not a blocking
error, because bands are advisory.

## Deterministic Repair

On violations, the engine attempts exactly one deterministic repair pass that
relaxes soft preferences only. It can never remove an explicit constraint,
capability `AVOID`, equipment requirement, or low-impact rule, and never invents
a load. If repair still yields no valid plan, the engine fails closed with a
typed `WorkoutPlanningFailure`; it never persists an invalid plan.

## Recommendation Snapshots

Every recommendation persists an immutable `RecommendationSnapshot` carrying the
context hash, catalog version, review version, policy version, ledger version,
ordered reason codes, and validator result. The same versioned inputs reproduce
the same snapshot, and any recommendation is replayable and auditable from
immutable history without re-running the LLM.

## Active Logging and Feedback

The active workout screen captures nullable RPE/RIR, timestamps, a
user-confirmed "felt manageable" flag, a skip/pain-stop reason, and editable
rest, persisted atomically with null preserved as null. Fast RIR/rest controls
are offered but never required to complete a valid set. Completed history is the
reconstructable authority; the derived ledger, capability evidence, and
progression update from it. `CapabilityEvidencePolicy` may relax a soft penalty
only after two comparable completed sessions plus explicit user confirmation
that the movement felt manageable, and only for that exercise and documented
equal-or-easier regressions; it never relaxes a hard exclusion and never
auto-writes `COMFORTABLE` into the profile. This is a reproducibility policy, not
physiology.

## Today vs Program Horizon

**Today** produces a single legal RT session. The **program horizon** owns
multi-session weekly structure and may record and surface user-selected aerobic
activity and public-health education. v1 does not prescribe individualised
aerobic dose, generate cardio sessions, prescribe obesity treatment, make
weight-loss or calorie claims, or infer recovery/fatigue from Health Connect.
Copy states honestly that Today is strength/muscle work, not a complete activity
programme. For power-priority users the program horizon may note that
same-session aerobic work can affect explosive outcomes.

## Deterministic Data Flow

```text
Profile + capability + constraints + equipment + history
  -> reviewed-metadata hard eligibility
  -> weekly ledger and calibration-state assessment
  -> state-aware deterministic candidate ranking
  -> deterministic prescription compilation (no invented load)
  -> session and weekly validation
  -> one deterministic repair attempt
  -> immutable recommendation snapshot
  -> active-session logging
  -> immutable completed history
  -> ledger / capability-evidence / progression / DeloadOffer update
```

Hard rules cannot be repaired away. Missing inputs become conservative or typed
failures, never guessed success.

## Local LLM Input/Output Schemas

```kotlin
data class CandidateSlot(
    val slotId: String,
    val eligibleExerciseIds: List<String>,
    val deterministicSelectionId: String
)

data class NonSafetyPreferences(
    val durationMinutes: Int?,
    val emphasizedMuscles: Set<String>,
    val avoidedEligibleIds: Set<String>,
    val equipmentPreference: Set<String>,
    val familiarity: FamiliarityPreference?
)

data class RankedCandidateResponse(
    val selections: Map<String, String>,      // slotId -> selected eligible id
    val explanationKeys: List<String>
)

interface WorkoutCandidateRanker {
    suspend fun rank(
        slots: List<CandidateSlot>,
        eligibleIds: Set<String>,
        preferences: NonSafetyPreferences,
        policyVersion: Int
    ): RankedCandidateResponse
}
```

The prompt contains only slot keys, exact eligible IDs, non-sensitive preference
enums, compact deterministic reason keys, and the policy version. It never
contains raw measurements, BMI, capabilities, constraints, notes, full history,
Room rows, or any dose. The response may contain only slot selections drawn from
the provided IDs plus explanation keys. Deterministic code compiles every
prescription and re-validates the result; the model cannot express a dose or a
new identifier.

## Preference Confirmation

`PreferenceParser` maps non-safety free text to the bounded
`NonSafetyPreferences` schema. Allowed fields are session duration, emphasised
muscles, avoided eligible IDs, equipment preference, and novelty/familiarity.
It rejects safety symptoms, diagnoses, load/reps/sets/RIR/rest, capability
changes, and unknown fields. Every parsed proposal requires explicit user
confirmation before it applies. Ambiguous or safety-like text is routed to
clarification or to deterministic profile controls; it is never auto-interpreted.

## Candidate Reranking

`WorkoutCandidateRanker` reranks only the already-eligible IDs per slot.
Hallucinated, duplicate, cross-slot, omitted-required, malformed, or
dosage-bearing output is rejected by `RankedCandidateResponseParser`, and the
deterministic selection stands. The model can never introduce a disallowed or
failing identifier, because the validator re-checks the compiled plan regardless
of model output.

## Explanation Generation

`WorkoutExplanationService` renders user copy from structured deterministic
reason keys only, using capability language rather than body labels. It rejects
unsupported, medical, stigmatising, or injury-prevention claims, validates that
exercise IDs, policy facts, and user choices in the prose match the
recommendation snapshot, and falls back to deterministic localised templates on
any mismatch.

## Audit

`TieredWorkoutPlanner` orchestrates the optional model and writes an
`LlmAuditRecord` for each invocation: provider/model version, prompt-policy
hash, candidate-set hash, decoding configuration where available, the structured
response, the validation result, and the fallback reason. It stores no raw
personal notes or full history in prompt or audit. Model failure
cannot block workout start or mutate Room, and the final recommendation is
reconstructable from the audit record plus immutable history.

## Privacy

Capability answers remain in local Room storage. No account, network call,
analytics event, model prompt, Wear payload, or Health Connect permission is
added by these engines. Weight, height, and BMI are neither stored nor derived
by the capability milestone; they must not be displayed, persisted, prompted,
or ranked. Local export/delete controls include capabilities when local data
controls ship.

## Failure and Fallback

Every runtime, parser, or validator failure — model unavailable,
download-required, busy, thermal/battery-aborted, cancelled, timed out, corrupt
model, malformed output, unsupported field, unknown/duplicate ID, or opt-out —
returns the unchanged deterministic recommendation. Ledger data missing is
treated as zero credit (conservative). A completed session is immutable; policy
changes affect only future recommendations. Offline benchmark accuracy alone
never enables the model.

## On-Device Runtime Tiers

`LocalModelRuntime` abstracts a provider-agnostic on-device inference stack and
models these states: unavailable, download-required, ready, busy,
thermal/battery-aborted, cancelled, timeout, corrupt-model. Inference is a
cancellable suspend call whose deadline is supplied by **device-specific,
measured** product policy, not a science constant. The install is removable,
integrity-checked, and never required for core workouts. Device-specific SLOs
for latency, memory, battery, thermal behaviour, cancellation, and accessibility
are derived from benchmarking representative devices before launch; no single
timeout value is claimed as evidence-based. A deterministic fake runtime backs
tests, and there is no production provider until the gates below pass.

## Evaluation

The deterministic engine requires property/replay tests, persona fixtures,
catalog-provenance review, and immutable audit reconstruction. Persona fixtures
cover novice/bodyweight, band-only, machine-only, full-gym, advanced strength,
limited-capability, returner, mixed-unit, sparse-history,
constraint-sensitive, and concurrent-activity cases. Invariants asserted:
reviewed IDs only; hard rules always win; explicit `AVOID` always wins;
primary-only crediting; no invented load; no BMI/body-mass influence; demonstrated
history relaxes only soft penalties after the confirmed threshold; identical
versioned inputs reproduce an identical plan.

The LLM additionally requires strict schema/adversarial tests (prompt injection,
safety-like free text, unavailable equipment, hard `AVOID`, duplicate IDs,
malformed JSON, dosage injection, timeout, cancellation, model absence), perfect
hard-constraint preservation and deterministic fallback, blind expert no-worse
review of appropriateness and explanation fidelity, an opt-in human evaluation
demonstrating a pre-registered benefit (higher acceptance, fewer
edits/substitutions, or clearer comprehension without more unsafe or confusing
output), and device-specific measured SLOs. Deterministic safety is never
credited to the model.

## Rollout

1. Ship profile/capability storage without changing planner output.
2. Add reviewed demand metadata and provenance.
3. Add the PRIMARY_ONLY weekly ledger and calibration-state derivation.
4. Enable reviewed-only eligibility, the temporary advanced ceiling, and hard
   capability constraints; measure plan availability across persona fixtures.
5. Enable state-based dose/effort/rest policy and soft ranking behind a local
   flag.
6. Enable history-derived capability evidence, progression, and `DeloadOffer`
   only after replay tests pass.
7. Keep LLM reranking disabled until every deterministic gate and every LLM gate
   passes; ship it opt-in, removable, with an immediate kill switch.

## Observability

Local, privacy-preserving signals only: deterministic replay/property test
results in CI, catalog/importer provenance drift checks, persona-fixture pass
rates, and — when the LLM is enabled — the local audit trail with fallback
reasons and (opt-in) blind-review and device-benchmark artefacts. No remote
telemetry, analytics event, or measurement value leaves the device.

## Non-Goals (v1)

Cardio session generation; individualised aerobic dose; obesity treatment;
weight-loss/calorie prognosis; body-mass or BMI load scaling; numeric
joint/SFR/axial/fatigue scoring; general long-length-partial bonus; periodisation
blocks beyond simple progression and `DeloadOffer`; injury-prevention or recovery
diagnosis; Health Connect fatigue inference; proving LLM efficacy by offline
accuracy alone.

## Risks and Mitigations

- **False precision** — retract numeric scores and label every default as
  versioned policy.
- **Equity gap** — review band/machine/supported regressions before enabling the
  automatic gate so equipment-limited users retain eligible plans.
- **Stigma** — capability language only; BMI never displayed, stored, or
  prompted.
- **Silent dose inflation** — PRIMARY_ONLY crediting from immutable history.
- **Model overreach** — strict schemas, deterministic compilation/validation,
  and immediate fallback around every model call.
- **Unconfirmed load** — validator rejects any synthesised starting weight.

## Acceptance Criteria

- Every automatic candidate has reviewed provenance.
- Hard constraints have zero violations across persona and adversarial fixtures.
- The same versioned inputs reproduce the same recommendation.
- The weekly ledger is reconstructable from immutable history with primary-only
  crediting.
- Missing inputs never become favourable assumptions and no load is invented.
- BMI/body mass never affect v1 planning; they are not profile inputs.
- `DeloadOffer` is an offer, never a diagnosis or a calendar law.
- The LLM remains disabled until deterministic completion, perfect
  hard-constraint/fallback tests, strict schema, expert no-worse review, opt-in
  human benefit, and device-specific measured SLOs are all met; removal or
  unavailability leaves core workouts unchanged.
