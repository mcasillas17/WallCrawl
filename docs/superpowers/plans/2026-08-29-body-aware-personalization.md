# Capability-Aware Personalization Roadmap

> **Planning record:** Current status, priority, and dependency order live in the
> [canonical roadmap](../../../ROADMAP.md). This document preserves detailed implementation
> context; unchecked boxes are not authoritative project status.

**Goal:** Stage capability-aware planning safely by shipping validated local
movement-capability inputs first, without changing current recommendations.

**Scope decision:** The input milestone is capability-only. It does not store,
calculate, display, analyze, or rank by weight, height, BMI, age, or body
composition. Optional body measurements are deferred and are not a prerequisite
for the capability roadmap.

**Architecture:** Typed enum selections flow through onboarding/Profile drafts
to a normalized `UserProfile`, one atomic repository save, a bounded allowlisted
codec, and Room schema 8. The current planner ignores the new values. Reviewed
exercise-demand metadata and deterministic eligibility were the planned follow-up when this
record was written.

## Milestone 1: Capability-aware profile inputs

### Domain and validation

- [x] Define the seven stable `MovementCapabilityType` identifiers.
- [x] Define `UNKNOWN`, `COMFORTABLE`, `LIMITED`, and `AVOID` levels.
- [x] Normalize missing known keys to `UNKNOWN`, never `COMFORTABLE`.
- [x] Make `MovementCapabilities` immutable and conservatively defaulted on
  `UserProfile`.
- [x] Keep `TrainingConstraint` independent from movement comfort.
- [x] Keep labels/descriptions in Android resources rather than persistence.

### Persistence and compatibility

- [x] Advance Room from schema 7 to 8 with one non-null
  `movementCapabilitiesJson` column.
- [x] Encode only allowlisted enum names in a deterministic flat JSON object.
- [x] Cap persisted input at 4096 characters and validate shape, nesting,
  duplicates, keys, and values at the codec boundary.
- [x] Decode missing, malformed, oversized, or future data conservatively to
  `UNKNOWN`.
- [x] Preserve existing onboarding status, revision, theme, goals, equipment,
  constraints, confirmed loads, templates, sessions, sets, and history.
- [x] Register `7 → 8` in production and in every supported historical migration
  chain without destructive fallback.
- [x] Verify a real schema-7 database migration and `PRAGMA foreign_key_check`.

### Fresh onboarding

- [x] Add Movement preferences immediately after Experience & Units.
- [x] Require an explicit selection for every capability.
- [x] Treat **Not sure** as an explicit persisted `UNKNOWN` answer while keeping
  unanswered draft keys absent.
- [x] Preserve actual answers and the wizard step across navigation,
  configuration change, and process recreation through `SavedStateHandle`.
- [x] Save the completed profile atomically.
- [x] Use resource-backed, neutral, non-medical copy and accessible selection
  semantics in a scrollable layout.

### Existing users and Profile

- [x] Leave migrated users onboarded and initialize every capability to
  `UNKNOWN`.
- [x] Display all seven persisted values in Training Profile.
- [x] Edit through a separate complete draft.
- [x] Save once while preserving unrelated fields and repository revision
  semantics.
- [x] Discard the draft on Cancel or Back without persistence.
- [x] Explain that values prepare future personalization and do not affect
  current recommendations.

### Staged planner and privacy boundary

- [x] Keep `WorkoutGenerationContext` as the single source of profile truth.
- [x] Add no capability field to an LLM or external interface.
- [x] Prove profiles differing only in movement capabilities produce the same
  current recommendation.
- [x] Add no cloud, account, analytics, network, Health Connect, Wear OS, or LLM
  flow.
- [x] Leave Android backup policy unchanged and document the existing
  `allowBackup=true` database behavior.
- [x] Document profile export/delete as a separate data-controls roadmap item;
  the app has no current subsystem to extend.
- [x] Keep raw capability payloads and complete profiles out of logs, displayed
  errors, snapshots, and fixtures.

### Verification and release gate

- [x] Run focused red/green domain, codec, repository, migration, onboarding,
  Profile, accessibility, and planner-invariance tests during implementation.
- [x] Run the complete JVM suite.
- [x] Run lint and assemble the debug APK.
- [x] Run the complete connected-device suite with no skips or zero-test
  filters.
- [x] Run diff, secret, debug-residue, generated-artifact, and changed-file
  audits.
- [ ] Obtain independent Terra and Luna approval on the exact same final commit.
- [ ] Open a verified non-draft pull request targeting `main`.

## Originally planned Milestone 2: Reviewed metadata and deterministic eligibility

This was the immediate next milestone when the plan was authored. None of it was completed as
part of Milestone 1.

- [ ] Define categorical exercise demand for impact, floor transition, balance,
  support, and required movement capabilities.
- [ ] Human-review and version demand metadata for every automatic-planning
  exercise before making it eligible for capability logic.
- [ ] Validate enum values, regression references, graph cycles, provenance, and
  planner-catalog coverage in importer and Android tests.
- [ ] Add deterministic eligibility decisions with typed reason codes.
- [ ] Treat explicit `AVOID` and applicable `TrainingConstraint` values as hard
  requirements only after metadata coverage and persona availability tests pass.
- [ ] Preserve full-catalog browsing and explicit manual-template behavior.

## Later deterministic milestones

- [ ] Capability-aware soft ranking and supported regressions.
- [ ] Capability-aware substitutions and explanations.
- [ ] Conservative prescription/dose changes.
- [ ] History-derived capability evidence with explicit confirmation.
- [ ] Progression and deload behavior.
- [ ] Whole-program replay and persona evaluation.

These later milestones must remain deterministic and explainable. They cannot
use body measurements as capability proxies or invent starting loads. LLM work
remains blocked behind the complete deterministic pipeline and its independent
evaluation gates.

## Data-flow verification map

```text
Compose enum option
  → Onboarding/Profile ViewModel typed draft
  → normalized UserProfile
  → repository validation and one profile write
  → bounded allowlisted JSON codec
  → Room user_profiles
  → conservative decode
  → UserProfile
  → resource-backed Compose display
```

At every boundary, known enums are the only writable values, missing persisted
data becomes `UNKNOWN`, corrupt storage never becomes favorable, and no raw
payload crosses into logs or another system.
