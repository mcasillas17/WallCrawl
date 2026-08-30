# Science-Based Local LLM Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional on-device LLM that improves preference handling and explanations without controlling safety, eligibility, exercise identity, dose, progression, or persistence.

**Architecture:** The completed deterministic engine produces exact candidate slots, fallback ranking, dose, and reason codes. A provider-agnostic local runtime may parse non-safety preferences, rerank only those IDs, and verbalize reasons; strict schemas, deterministic compilation/validation, audit records, and immediate fallback enclose every model call.

**Tech Stack:** Kotlin, Coroutines/Flow, provider abstraction for Android local inference, strict JSON/schema parser, Room audit metadata, JUnit/property tests, physical-device benchmarks.

---

## Core Contracts

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
    val selections: Map<String, String>,
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

### Task 1: Add a Deterministic Readiness Gate

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/LlmReadinessGate.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/local/LlmReadinessGateTest.kt`

- [ ] Write failing tests requiring deterministic-engine version, reviewed-catalog version, zero fixture violations, and explicit opt-in.
- [ ] Add `LlmReadiness(disabledReasons, deterministicPolicyVersion, evaluationVersion)`.
- [ ] Default disabled; unavailable model leaves deterministic output unchanged.
- [ ] Exclude body measurements, BMI, capabilities, constraints, notes, and raw history from model eligibility.
- [ ] Commit `feat: gate local workout intelligence`.

### Task 2: Define Provider and Capability Abstractions

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/LocalModelRuntime.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/LocalModelCapability.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/local/LocalModelRuntimeTest.kt`

- [ ] Write failing tests for unavailable, download-required, ready, busy, thermal/battery-aborted, cancelled, timeout, and corrupt-model states.
- [ ] Define suspend inference with cancellation/deadline supplied by device-specific product policy, not science constants.
- [ ] Make model install removable, integrity-checked, and never required for core workouts.
- [ ] Add deterministic fake runtime for tests; no production provider yet.
- [ ] Commit `feat: abstract local model runtime`.

### Task 3: Add Strict Non-Safety Preference Parsing

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/PreferenceProposal.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/PreferenceParser.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/local/PreferenceParserTest.kt`

- [ ] Define allowed fields: session duration, emphasized muscles, avoided eligible IDs, equipment preference, novelty/familiarity preference.
- [ ] Reject safety symptoms, diagnoses, load/reps/sets/RIR/rest, capability changes, and unknown fields.
- [ ] Require explicit user confirmation before applying a parsed proposal.
- [ ] Route ambiguous/safety-like text to clarification or deterministic profile controls; never auto-interpret.
- [ ] Commit `feat: parse bounded workout preferences`.

### Task 4: Add Exact Candidate-Slot Reranking

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/WorkoutCandidateRanker.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/RankedCandidateResponseParser.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/local/WorkoutCandidateRankerTest.kt`

- [ ] Write failing tests for hallucinated, duplicate, cross-slot, omitted-required, malformed, and dosage-bearing output.
- [ ] Prompt only slot keys, exact eligible IDs, non-sensitive preference enums, deterministic reason keys, and policy version.
- [ ] Response may contain only `{slotId, selectedExerciseId, explanationKeys}`.
- [ ] Deterministic code compiles prescriptions and validates final plan; model cannot create substitutions outside provided IDs.
- [ ] Commit `feat: constrain local candidate reranking`.

### Task 5: Add Reason-Key Explanation Rendering

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/WorkoutExplanationService.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/local/WorkoutExplanationServiceTest.kt`

- [ ] Write tests rejecting unsupported/medical/stigmatizing/injury-prevention claims.
- [ ] Generate from structured deterministic reason keys only.
- [ ] Validate that exercise IDs, policy facts, and user choices in prose match the recommendation snapshot.
- [ ] Fall back to deterministic localized templates on any mismatch.
- [ ] Commit `feat: explain deterministic workout choices`.

### Task 6: Add Validation, Fallback, and Audit

**Files:**
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/TieredWorkoutPlanner.kt`
- Create: `app/src/main/java/wallcrawl/elopenmike/com/core/ai/local/LlmAuditRecord.kt`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/WallCrawlApplication.kt`
- Test: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/TieredWorkoutPlannerTest.kt`

- [ ] Write tests for every runtime/parser/validator failure returning the unchanged deterministic plan.
- [ ] Store provider/model version, prompt-policy hash, candidate-set hash, decoding configuration, structured response, validation result, and fallback reason.
- [ ] Never store raw personal notes/measurements/full history in prompt or audit.
- [ ] Ensure model failure cannot block workout start or mutate Room.
- [ ] Commit `feat: add audited local planner fallback`.

### Task 7: Build Offline Adversarial Evaluation

**Files:**
- Create: `app/src/test/resources/llm-fixtures/*.json`
- Create: `app/src/test/java/wallcrawl/elopenmike/com/core/ai/local/LlmFixtureTest.kt`
- Create: `docs/llm-evaluation.md`

- [ ] Cover prompt injection, safety-like free text, unavailable equipment, hard AVOID, duplicate IDs, malformed JSON, dosage injection, timeout, cancellation, and model absence.
- [ ] Require perfect hard-constraint preservation and deterministic fallback.
- [ ] Compare deterministic-only versus reranked output for edit/substitution burden and reason fidelity; do not credit deterministic safety to the model.
- [ ] Version fixtures, candidate sets, prompts, policy, catalog, and model.
- [ ] Commit `test: add local llm adversarial corpus`.

### Task 8: Run Blind Review, Device Benchmarks, and Opt-In Pilot

**Files:**
- Create: `docs/llm-device-benchmark.md`
- Create: `docs/llm-human-evaluation.md`
- Modify: `app/src/main/java/wallcrawl/elopenmike/com/feature/profile/ProfileScreen.kt`
- Modify: `README.md`
- Modify: `docs/architecture.md`

- [ ] Benchmark candidate providers on representative devices for latency, memory, battery, thermal behavior, cancellation, and accessibility; derive device-specific SLOs.
- [ ] Blind-review deterministic versus LLM-ranked plans for no-worse appropriateness and explanation fidelity.
- [ ] Pre-register one user benefit: higher acceptance, fewer edits/substitutions, or clearer comprehension without more unsafe/confusing output.
- [ ] Add explicit opt-in, model removal, local audit export, and immediate kill switch; keep reranking disabled until gates pass.
- [ ] Run all deterministic/LLM/unit/lint/build/connected tests and commit `docs: validate local llm workout assistance`.

## LLM Release Gates

- Deterministic engine is complete and remains the fallback.
- Model input excludes safety/body/dose/private history fields.
- Output schema cannot express dose or new IDs.
- Hard constraints and fallback pass every adversarial fixture.
- Blind expert review is no worse than deterministic baseline.
- An opt-in human evaluation demonstrates predefined benefit.
- Device-specific SLOs are measured, not guessed.
- Removal/unavailability leaves core workouts unchanged.

## Complete Verification

```bash
./gradlew testDebugUnitTest --tests '*local*' --tests '*TieredWorkoutPlannerTest'
./gradlew test lint assembleDebug --stacktrace --no-daemon
./gradlew connectedDebugAndroidTest --no-daemon
git diff --check
```
