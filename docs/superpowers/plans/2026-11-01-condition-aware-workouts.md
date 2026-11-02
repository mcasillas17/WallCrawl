# Condition-Aware Workouts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Research 50 candidate conditions and extend personalized workouts with reviewed condition rules, supportive exercises, and complete new PNG artwork.

**Architecture:** Extend the existing deterministic planner and its validators. Keep research dossiers separate from released support records, ordinary prescriptions separate from supportive prescriptions, and WallCrawl-authored exercises separate from pinned upstream content. Persist one frozen condition context per session, shared by generated and condition-checked manual starts.

**Tech Stack:** Existing Kotlin/Compose/Room app, Android `JsonReader`, Python standard-library tooling, bundled JSON/SVG assets, native image generation for PNG masters. Keep Java 17, minSdk 26 and compile/target SDK 37; add no app dependency.

**Spec:** [Approved design](../specs/2026-11-01-condition-aware-workouts-design.md). Approval recorded on 2026-11-01; it approves the design, not any clinical content.

## Global Constraints

- "The visible condition list must contain only conditions the app actually supports through exercise mappings and planning rules."
- "The research inventory is not a prevalence ranking and is not the app's supported-condition list."
- "A recommendation for one condition cannot override another condition's restriction, equipment requirements, or an independently selected joint limit."
- "Do not use stretching to satisfy a strength split, award strength-set credit, or inherit generic five-second progression."
- "No repair may weaken a condition restriction to fill an empty workout."
- "Clinical review is a content-release gate, not a blocker on gathering research or producing clearly marked drafts."
- "PNGs remain actual retained deliverables, not discarded intermediates."
- "Existing profiles with no selected conditions must retain their existing planning behavior."
- Preserve offline operation, English/Spanish copy, existing measurement validation and user-confirmed starting loads. Unknown clinical facts remain unknown.
- Never fabricate source access, reviewer identity, clinical approval, generation provenance, upstream attribution or a missing artwork workspace.
- Keep the upstream 302-exercise/906-frame pin and historical AI acceptance audit unchanged. A supplement gets its own source and identity.

## Review Focus

- **Withdrawn/unknown condition data:** preserve the selection and refuse interpretation; never restore an unrestricted profile accidentally. Tests: Tasks 4, 8, 9, 15.
- **Same exercise used with different purposes:** supportive work remains logged but cannot drive ordinary strength dose, capability, working loads or progression. Tests: Tasks 7, 13.
- **Conflicting condition rules and insufficient time:** do not average prescriptions, double a shared dose, or trim a required supportive prescription. Tests: Tasks 10–12.
- **Cancellation, stop edits and racing state changes:** cancellation or editing a set cannot erase pending reassessment; stale reassessment/profile edits cannot win a race with a newer stop/start. Tests: Tasks 8, 9, 14.
- **New artwork without old production ledgers:** preserve old bytes/provenance, reject malformed new sequences, and fall back only to the new exercise's own canonical art. Tests: Tasks 5, 6.

---

## Execution order and ownership

One plan is appropriate because the research contract, catalog, prescriptions and
history all share the same exercise identities. Tasks 1–3 produce reviewable
research; Tasks 4–15 implement the contracts; Task 16 verifies and releases only
eligible content. No production clinical values are invented while waiting on
research. Synthetic tests are explicitly fictional and remain test resources.

Use the requested subagents for regional research, with at most three workers and
one coordinator active. Give each worker ownership of its dossier IDs; only the
coordinator edits shared index/request files. Tell workers they are not alone in
the repository and must preserve others' edits. Independently verify their work.
Later catalog/art and engine work may run concurrently only after their shared
interfaces below exist; serialize edits to shared model, database and archive files.

There is currently no named clinical reviewer. Complete research, draft artwork and
infrastructure without fabricating that review. If qualified review is unavailable,
Task 16's content promotion remains explicitly unfulfilled; report the unreleased
condition count instead of calling the condition feature shipped.

### File map and notation

Paths below are repository-relative. For compactness, `M/` expands to
`app/src/main/java/wallcrawl/elopenmike/com/`, `T/` to
`app/src/test/java/wallcrawl/elopenmike/com/`, and `A/` to
`app/src/androidTest/java/wallcrawl/elopenmike/com/`. These are document notation,
not shell variables. Existing large files stay in their existing structure.

| Area | Files and responsibility |
| --- | --- |
| Research | `docs/research/conditions/index.json`, `<condition-id>.json`, `exercise-requests.json`, `review.md`: source extraction, mappings and disposition |
| Research checks | `tools/workout-guide/validate_condition_research.py`, `test_validate_condition_research.py`: bounded factual-record validation, never clinical acceptance |
| Condition domain | `M/core/model/ConditionSupport.kt`, `ConditionSelection.kt`, `ConditionReassessment.kt`, `SessionConditionContext.kt`: reviewed support, selected context, pending reassessment and frozen session facts |
| Runtime content | `M/core/condition/ConditionSupportParser.kt`, `ConditionSupportStore.kt`; `app/src/main/assets/conditions/support.json`: strict released-record reader/store |
| Condition policy | `M/core/ai/ConditionPlanningPolicy.kt`, `ConditionHistory.kt`: deterministic eligibility, prescription checks and stop/schedule evidence |
| Existing prescriptions/planner | `ExercisePrescription.kt`, `WorkoutGenerationContext.kt`, `Workout.kt` under `M/core/model/`; existing factories/planner/validators under `M/core/ai/` |
| Supplemental exercises | `app/src/main/assets/wallcrawl/catalog.json`; existing catalog parser/store/snapshot; `tools/workout-guide/validate_catalog_supplement.py` |
| Artwork | `art/condition-exercises/<exercise-id>/`; `tools/workout-guide/trace_condition_art.mjs`; existing `package_illustrations.py`; existing illustration index plus local provenance |
| Persistence | Existing profile/session/template entities, mappers/repositories/DAOs, database, archive and history; `M/core/database/repository/ConditionContextCodec.kt` for shared bounded canonical JSON; new `ConditionReassessmentPendingEntity.kt` and `ConditionReassessmentDao.kt` in their existing entity/DAO directories |
| User flows | Existing onboarding/profile, Today, templates, active workout and history screens/view models; English/Spanish resources and exercise localization |

### Shared interface decisions

Freeze these names before workers split. Do not add a general scripting or rule
expression language. Condition input answers are canonical choice tokens, not prose
or expressions. Applicability cases match explicit required answers; unsupported or
ambiguous matching is a blocked result.

```kotlin
enum class PrescriptionPurpose { ORDINARY, SUPPORTIVE }
// ExercisePrescription gains purpose = ORDINARY; ordinary includes legacy manual cardio/stretch work.
data class SelectedCondition(
    val conditionId: String,
    val contextSchemaVersion: Int,
    val answers: Map<String, String?>
)

sealed interface ConditionPlanningResult {
    data object NotApplicable : ConditionPlanningResult
    data class Blocked(val reasons: List<ConditionViolation>) : ConditionPlanningResult
    data class Ready(
        val ordinaryRules: Map<String, ResolvedConditionRule>,
        val supportive: List<ConditionSupportivePrescription>,
        val context: SessionConditionContext
    ) : ConditionPlanningResult
}
```

`ConditionViolation` contains a stable reason token and relevant condition/exercise
IDs. `ResolvedConditionRule` contains the intersected permitted prescription bounds,
execution identity, ordinary-progression permission, source rule references and
policy versions. `ConditionSupportivePrescription` contains `exerciseId`, an exact
`ExercisePrescription(purpose = SUPPORTIVE)`, execution identity, all contributing
rule references and its reviewed schedule. A missing bound means no *additional*
condition bound, never permission to bypass existing constraints.

`ConditionSupportCatalog` owns a content version and records keyed by condition ID.
Each `ConditionSupportRecord` owns applicability/input definitions, explicit
exercise/case mappings, source links, bound exercise-content identities, review
status/provenance and localized labels/instructions. Rules have only these effects:
exclude; permit ordinary work within specified bounds; or prescribe supportive work.
Unmapped exercises are unassessed. Released records bind the exact clinically
reviewed content digest; modifying a mapping invalidates its release eligibility.

`SessionConditionContext` owns a format/policy version, selected context, source
support versions, applied exercise/rule/purpose references and consumed
schedule/reassessment evidence. It has a canonical digest. Keep it once on the
session; generated recommendation records bind that digest rather than duplicate
the medical context. Manual sessions keep no fabricated automatic recommendation.
Historical labels may be displayed from frozen start-time explanations/notes;
unknown identities display as unsupported, not reinterpreted through today's data.

`PendingConditionReassessment` is separate owned state keyed by profile, condition
and exercise. Its fields are `eventId`, `stoppedContextIdentity`,
`responsePolicyVersion`, `stoppedAtTimestamp`, and optional `sourceSetId`. The last
field is provenance, not a foreign key: Cancel deletes sessions/sets, and completion
edits clear their stop fields. Keep the marker until a source-bound explicit
reassessment clears it; removing/re-adding a condition must not erase it. This is
the minimal durable pending state, not a new longitudinal medical-history system.

## Task 1: Establish the research format with the piriformis dossier

**Files:** Create `docs/research/conditions/index.json`, `piriformis-syndrome.json`,
`exercise-requests.json`, `tools/workout-guide/validate_condition_research.py`,
`tools/workout-guide/test_validate_condition_research.py`.

**Interfaces:** `validate_corpus(root: Path, require_complete: bool) -> list[str]`;
CLI `python3 tools/workout-guide/validate_condition_research.py [--require-complete]`.
Produces index schema version 1 with exactly the design's 50 unique IDs, and a
version-1 dossier per completed ID. Each dossier has `condition`, `scope`,
`searchLog`, `sources`, `findings`, `exerciseMappings`, `proposedRules`, `gaps`,
`disposition`, `review`. Each finding/rule references local source IDs and exact
locations; mappings name a catalog ID or an explicit new-exercise request ID.

- [ ] Write validator tests: duplicate IDs/JSON fields, non-finite numbers, broken source/mapping references, absent access status and fabricated review promotion produce errors; an explicit insufficient-evidence dossier is valid. `require_complete=True` rejects 49 dossiers and accepts 50.
- [ ] Run `python3 -m unittest discover -s tools/workout-guide -p 'test_validate_condition_research.py' -v`; verify the new cases fail before implementation.
- [ ] Implement strict bounded JSON validation with existing importer helpers. Pin transport limits, depth and identifier lengths in constants, test at and above each limit, and reject rather than truncate. No generic JSON-schema interpreter or new dependency.
- [ ] Research piriformis using primary sources/guidelines and relevant syntheses. Record search/access dates, exact variant/dose, population, co-interventions, outcomes, uncertainty, harms reporting and supersession. Keep DGS distinctions; retain `not reported` where appropriate. Produce candidates only, with actual reviewer provenance absent.
- [ ] Run the targeted tests and validator; manually inspect every pilot citation and mapping. Commit the format and pilot as `docs: establish condition research corpus and piriformis pilot`.

## Task 2: Complete all 50 research records in regional batches

**Files:** Create the other 49 `docs/research/conditions/<condition-id>.json` files;
update `index.json` and `exercise-requests.json`; create `docs/research/conditions/summary.md`.

**Interfaces:** Consumes Task 1's validated dossier format. Produces 50 complete
research dispositions and a deduplicated list of new exercise execution identities.
The inventory is the approved design's exact list, not a freshly generated ranking.

- [ ] Assign batches: spine (7), shoulder (6), elbow/wrist/hand (8), hip/groin/gluteal excluding the completed pilot (6), knee (7), lower leg/ankle/foot (10), systemic/multiple regions (5). Run at most three batches concurrently and keep the pilot in the same index.
- [ ] Each worker searches and extracts every assigned dossier, records inaccessible sources and weak/absent evidence, and maps ordinary-training implications separately from therapeutic candidates. Stage/surgery/side are attributes, not invented extra diagnoses.
- [ ] The coordinator reconciles synonymous exercise requests using execution, equipment, range and measurement shape; preserve clinically meaningful variations. Add sources/gaps rather than deciding unknown clinical values.
- [ ] Run the validator with `--require-complete`. Confirm all 50 IDs have a disposition and every proposed rule has its exact supporting population/source or is explicitly unresolved. Record the per-condition result in `summary.md`.
- [ ] Commit by completed regional batch, then commit the reconciled corpus. Do not claim an exhaustive systematic review or 50 supported app conditions.

## Task 3: Independently verify evidence and prepare the clinical review packet

**Files:** Update dossier `review`/`disposition`; create `docs/research/conditions/review.md`.

**Interfaces:** Consumes Task 2; produces an independently checked candidate set,
including exact source-derived prescriptions and required context, and a release
status for each record. This is the input to Task 4's authored support data.

- [ ] Assign each dossier to an agent that did not author it. Reopen the cited sources; verify publication status, the specific claim, population, dose, follow-up and reported harms. Abstract-only records cannot supply absent dose details.
- [ ] Resolve citation/mapping errors in the owned dossier and have the independent verifier check corrections. Keep rejected claims and reasons visible; do not silently turn an inference into a source statement.
- [ ] Assemble the qualified-review packet with rules, applicability, monitoring/escalation, exercise identity and content hashes. Record real clinical review only if it actually occurs; otherwise mark `AWAITING_CLINICAL_REVIEW` and continue draft/infrastructure tasks.
- [ ] Identify protocols the app cannot represent faithfully, including required supervision, schedules, per-side accounting or monitoring beyond the supported contract; leave these research-only. Never compress them to fit an ordinary session by guesswork.
- [ ] Run `--require-complete`, reconcile the summary against the review packet, and commit as `docs: verify condition evidence and record release dispositions`.

## Task 4: Add strict condition contracts and a released support store

**Files:** Create the four condition model files in the file map;
`M/core/condition/ConditionSupportParser.kt`, `ConditionSupportStore.kt`;
`app/src/main/assets/conditions/support.json`; `T/core/model/ConditionSupportTest.kt`;
`A/core/condition/ConditionSupportParserTest.kt`; fictional parser fixtures under
`app/src/androidTest/assets/conditions/`. Modify `M/WallCrawlApplication.kt` wiring.

**Interfaces:** `ConditionSupportParser.parse(input: Reader): ConditionSupportCatalog`;
`ConditionSupportStore.load(): ConditionSupportCatalog`. Implement the shared types
above. Ordinary bounds use existing prescription units/types; loads in authored
rules declare their unit and are converted through existing conversion functions.
Match required input tokens exactly; optional unknown answers stay null. No regex
matching of symptoms or arbitrary executable conditions.

- [ ] Add model/parser tests for duplicate IDs/fields, invalid bounds and units, unknown schema, unknown review state, ambiguous cases, digest mismatch, unresolved exercise references, missing bilingual text and a released record lacking actual review provenance. Empty released catalog is valid.
- [ ] Run `./gradlew testDebugUnitTest --tests '*ConditionSupportTest' --no-daemon` and the connected class `wallcrawl.elopenmike.com.core.condition.ConditionSupportParserTest`; confirm failures before implementation.
- [ ] Implement bounded parsing and the shared types; reuse existing `BoundedCharacterReader` and validation patterns. No `catch` may return an empty catalog. Enforce source/exercise/review bindings at load. Tooling checks shape and content integrity, not clinician identity authenticity.
- [ ] Populate only records with completed release review; otherwise ship an empty list and retain drafts under research. Test fixtures use explicitly fictional review identities, never copied into main assets. Run the targeted tests and confirm nonzero execution counts.
- [ ] Commit as `feat: add reviewed condition support contracts`.

## Task 5: Merge authored exercises without changing the upstream source

**Files:** Create `app/src/main/assets/wallcrawl/catalog.json`, notices/license;
`tools/workout-guide/validate_catalog_supplement.py`, corresponding `test_*.py`;
modify `M/core/exercise/workoutguide/{WorkoutGuideCatalogParser,WorkoutGuideCatalogStore,WorkoutGuideCatalogSnapshot,AttributionNoticeReader}.kt`,
`M/core/model/Exercise.kt`, exercise localization/parser/vocabulary, catalog-version
consumers in `M/WallCrawlApplication.kt`, `M/core/database/repository/{WeeklyDoseLedgerRepository,ProgressRepository}.kt`.
Tests: existing parser instrumentation, `BundledExerciseLocalizationTest`,
`BundledCatalogVocabularyTest`, `LocalizedExerciseSearchTest`.

**Interfaces:** `WorkoutGuideCatalogParser.parseSupplement(input: Reader, upstream: WorkoutGuideCatalogSnapshot): WorkoutGuideCatalogSnapshot`;
snapshot gains `catalogVersion: String` and separate supplement attribution.
Preserve the old catalog identity when no local entries are present. A nonempty
supplement contributes its canonical content hash; upstream attribution stays exact.
Add neutral `Exercise.executionGuidance: String? = null` for local supportive entries
without strength programming; its localization uses the existing summary field.

- [ ] Add tests for empty/valid supplement, cross-source duplicate ID/reference, traversal or wrong visual path, incorrect source/version, unknown vocabulary, absent canonical art, and changed supplement invalidating content identity. Preserve upstream-only 302/906 assertions.
- [ ] Run Python supplement tests, parser instrumentation and targeted catalog/localization JVM tests to observe failures.
- [ ] Reuse parser internals with explicit source handling; canonical local frames use `exercise-illustrations/male/<id>/frame-N.svg`. Preserve the pinned importer output byte-for-byte. Add only settled execution identities from Task 3; entries can remain outside released condition support.
- [ ] Merge localization coverage over upstream plus supplement, preserving existing name/summary/alias limits and all languages. Use neutral execution guidance without fabricating strength metadata. Add local attribution documents to the existing Credits reader and localized titles.
- [ ] Run targeted suites plus `python3 tools/workout-guide/check_pinned_catalog.py`; compare upstream hashes; commit as `feat: add source-aware exercise catalog supplement`.

## Task 6: Produce PNG masters and package complete local illustration sequences

**Files:** Create `art/condition-exercises/<id>/{male,female}/masters/frame-N.png`,
`derived/frame-N.svg`, per-exercise provenance and previews;
`tools/workout-guide/trace_condition_art.mjs`, its assert-based self-check;
`tools/workout-guide/test_package_illustrations.py`. Modify `package_illustrations.py`,
`app/src/main/assets/exercise-illustrations/index.json`; add local provenance under
`app/src/main/assets/wallcrawl/`. Tests: `IllustrationCatalogTest`,
`WorkoutGuideVisualProviderTest`, `A/core/ui/IllustrationSelectionUiTest.kt`.

**Interfaces:** `collect_supplement(ledger_path: Path) -> dict[str, bytes]`;
`validate_bundled_illustrations(destination: Path) -> None` in the packager.
Add `--supplement-ledger <path>` and clone-safe `--check-bundled` modes; existing
full-old-workspace packaging remains distinct. The tracing CLI takes input PNG and
output SVG paths; it records versions/options and supports `--self-test`.

- [ ] Write packager tests for unavailable old ledgers, unchanged old SVG/provenance hashes, incomplete variants, incorrect source/master/derived hashes, invalid path-only SVG, stale unexpected files, and check-only mode writing nothing. Run them to observe failures.
- [ ] Establish the reproducible authoring toolchain. Prior Sharp/Potrace/SVGO processing scripts are absent here; locate retained tools first, otherwise use isolated, version-pinned authoring dependencies after checking their official APIs. Do not add these to Android dependencies or fabricate old histories. Add a small deterministic shape/alpha trace self-check and record the exact command/versions.
- [ ] For every new settled exercise identity, use the native image-generation tool and imagegen skill to create actual PNG masters, transparent white-line style, both demonstrator variants. Inspect reference artwork first. Copy selected outputs into the retained workspace and preserve real prompts/reference/generation provenance. Do not substitute hand-authored SVG placeholders for requested PNGs.
- [ ] Inspect every pose and the 1–2–3–2 sequence at the real 850-ms frame interval: movement, anatomy, equipment, support, camera and continuity. A static hold may reuse an unchanged pose across slots; do not depict bouncing. Use image generation for pose corrections, then re-check derived vectors.
- [ ] Derive 512×512 path-only SVGs and implement additive packaging, validating all inputs before writes. Use the local male sequence as canonical fallback; never substitute an upstream exercise. Preserve old assets and historical provenance exactly. Keep all source masters available; retain the existing policy of committing app-ready assets/compact provenance rather than the large production workspace.
- [ ] Run packager tests, tracing self-check, `python3 tools/workout-guide/package_illustrations.py --check-bundled`, targeted illustration tests and real app visual inspection. Record PNG locations, six frame slots per new ID, reused held slots, bytes/APK delta and remaining limitations. Commit app-ready additions as `feat: add condition exercise artwork and reproducible packaging`.

## Task 7: Carry prescription purpose through the domain and draft editor

**Files:** Modify `M/core/model/{ExercisePrescription,Workout,WorkoutGenerationContext}.kt`,
`M/feature/templates/TemplateExerciseDraft.kt` and its saved-state conversion;
tests `T/core/model/ExercisePrescriptionTest.kt`,
`T/feature/templates/TemplateExerciseDraftTest.kt`.

**Interfaces:** Add `purpose: PrescriptionPurpose = ORDINARY` to the existing
prescription. Keep `PlannedExercise`/`GeneratedExercise` and `WorkoutExercise` using
that type. Context gains `conditionPlanning = NotApplicable`; generated workout
gains `conditionDecisions: List<ConditionPrescriptionDecision> = emptyList()`.
Each decision identifies the exercise, purpose, execution and applied rule refs.

- [ ] Test default ordinary legacy prescriptions; supportive duration/bodyweight prescriptions keep all existing measurement validation; copying/rest-edit/draft-save paths retain purpose; malformed purpose tokens never become ordinary.
- [ ] Run the targeted JVM tests and confirm the new cases fail.
- [ ] Add the fields and pass them through existing copies/drafts, without creating another exercise hierarchy. The manual editor cannot manufacture a clinically authorized supportive role; condition planning supplies that authorization.
- [ ] Run targeted tests; verify old fixture defaults remain ordinary. Commit as `feat: distinguish supportive prescription instances`.

## Task 8: Persist selections, purpose and frozen context with Room 17

**Files:** Modify `M/core/model/{UserProfile,Workout,RecommendationRecord}.kt`,
`M/core/database/{WallCrawlDatabase,converter/RoomTypeConverters}.kt`,
`entity/{Entities,WorkoutRecommendationRecordEntity}.kt`, `dao/Daos.kt`,
`relation/{WorkoutSessionMapper,WorkoutTemplateMapper}.kt`,
`repository/{UserProfileMapper,UserProfileRepository,RecommendationRecordMapper,WorkoutRepository,LocalDataBackupRepository}.kt`.
Create `repository/ConditionContextCodec.kt`, `A/core/database/Migration16To17Test.kt`.
Create `M/core/database/entity/ConditionReassessmentPendingEntity.kt`,
`M/core/database/dao/ConditionReassessmentDao.kt` and
`A/core/database/ConditionReassessmentPersistenceTest.kt`.
Extend profile/recommendation/template persistence tests and explicit migration lists
in `Migration14To15Test` and `Migration15To16Test`.

**Interfaces:** `UserProfile.activeConditions: List<SelectedCondition> = emptyList()`;
session gains `conditionContext: SessionConditionContext? = null`; recommendation
record gains `conditionContextDigest: String? = null`.
`ConditionContextCodec.encodeSelections(values): String`,
`decodeSelections(json: String): List<SelectedCondition>`,
`encodeSession(value: SessionConditionContext): String`,
`decodeSession(json: String): SessionConditionContext` share strict canonical JSON.
`UserProfileRepository.updateActiveConditions(List<SelectedCondition>)` follows the
existing atomic update style; add no new repository. Add only the pending-marker
table, with a composite profile/condition/exercise primary key and no profile or
session foreign key, following the existing deload state rationale. Profile saves
use REPLACE and cancellation deletes sessions. Delete-all explicitly owns this table.

- [ ] Add migration/round-trip tests: old selections `[]`, old purposes `ORDINARY`, old session context/receipt digest null, no pending markers; no historical validation backfilled. Unknown selection IDs/answer tokens survive; malformed payload/purpose fails closed. Test upper transport bounds and duplicate fields. A pending marker survives profile REPLACE, session cancellation and condition removal/reselection.
- [ ] Add a DAO race test: two interleavings of changing condition context and starting a workout cannot both commit an incompatible state. Condition changes while active are refused, ordinary non-condition preference updates retain existing behavior, and finish/cancel/logging still work.
- [ ] Run targeted JVM/profile and connected migration/persistence tests to establish failures.
- [ ] Add additive 16→17 columns/table and `ALL_MIGRATIONS` registration. Persist purpose on both workout and template exercise rows. Guard condition changes within `UserProfileDao.insertOrUpdateWithNextRevision`'s database transaction, comparing persisted versus requested condition data and checking active sessions; UI checks alone are insufficient. Malformed legacy constraint values must also refuse planning instead of becoming an empty restriction set.
- [ ] Implement canonical codecs/mappers and bind the generated receipt digest to the session context. Manual sessions may carry condition context while recommendation remains null. Run targeted suites and commit as `feat: persist condition-aware workout context`.

## Task 9: Preserve archive compatibility and sensitive-data ownership

**Files:** Modify `M/core/backup/{LocalDataArchive,LocalDataArchiveCodec}.kt`,
`M/core/database/repository/LocalDataBackupRepository.kt`,
`M/core/database/dao/LocalDataBackupDao.kt`, backup row mapping;
create `A/core/backup/LocalDataArchiveConditionTest.kt`; extend
`LocalDataArchiveRecommendationTest`, `LocalDataArchiveTemplateUnitTest`,
`A/core/database/LocalDataBackupRepositoryTest.kt`; update `docs/privacy.md` and
English/Spanish export/delete copy.

**Interfaces:** Write archive version 7, read 1–7. Version 7 owns profile selections,
session condition context, receipt binding, prescription purpose and the pending
reassessment table as its own root collection. Versions 1–6
retain exact canonical bytes/checksums and their original interpretation.

- [ ] Add tests for v7 full round-trip including unsupported-but-preserved selection tokens, frozen context, both purposes and pending markers whose source set was legitimately deleted; 1–6 defaults; illegal new fields in old formats; digest/reference mismatch; malformed/oversized condition JSON; delete-all and fresh-start restore. Unknown response policies stay pending; delete-all removes markers, while restore never invents old pain events.
- [ ] Pin downgrade refusal before destination opening/truncation. Fix the template-unit test's `1 until ARCHIVE_VERSION` to the unit feature threshold `1 until 6`; update the explicit current-version assertion while keeping v6 unit support.
- [ ] Run connected archive/backup tests to confirm failures, then version-gate new readers/writers and reuse Task 8's codec/validation. Unknown historical condition versions remain opaque/preserved where round-trip is supported; they never imply current planning permission. Refuse unsupported export safely if lossless round-trip is impossible.
- [ ] Run targeted tests and verify representative v1–v6 canonical checksum fixtures are unchanged. Document that diagnoses/context enter user-selected readable exports and remain local. Commit as `feat: archive condition context without rewriting old formats`.

## Task 10: Resolve condition eligibility and prescription conflicts

**Files:** Create `M/core/ai/ConditionPlanningPolicy.kt`, `ConditionHistory.kt`,
`T/core/ai/ConditionPlanningPolicyTest.kt`; modify
`WorkoutGenerationContextBuilder.kt` and `WorkoutGenerationContext.kt`.

**Interfaces:**
`ConditionPlanningPolicy.resolve(profile: UserProfile, ordinaryCandidates: List<Exercise>, exercisesById: Map<String, Exercise>, support: ConditionSupportCatalog, history: ConditionHistory): ConditionPlanningResult`;
`applyOrdinary(exercise: Exercise, base: ExercisePrescription, planning: Ready): ConditionPrescriptionResult`;
`validate(planned: PlannedExercise, planning: Ready): List<ConditionViolation>`.
`ConditionPrescriptionResult` is Applied(prescription, decisions) or Blocked(reasons).
`ConditionHistory` owns bounded performed-work/schedule facts and a coherent snapshot
of pending reassessment markers, as-of timestamp and a source digest. The history
view is derived; only the explicitly owned pending markers are mutable state.

- [ ] Test no selections→NotApplicable with unchanged candidates; unknown/withdrawn record, missing required context, ambiguous case and changed content hash→Blocked; compatible cases intersect; exclusions/capability/joint/equipment restrictions always win; unassessed exercises cannot enter.
- [ ] Test conflicting required doses/ranges/execution/schedules→Blocked, same supportive exercise with identical rules→one prescription carrying both sources, and an unconfirmed load is never filled from a generic default. Test after-stop evidence and calendar/time-zone boundary scheduling without a universal pain threshold.
- [ ] Run targeted JVM tests to observe failure. Implement explicit-case resolution and bounds using reviewed data, preserving source units and existing user confirmations; never evaluate source prose or executable strings. Share existing eligibility logic where possible; supportive metadata must independently establish capability/joint/impact/equipment checks because stretches have no strength metadata.
- [ ] Build condition history in the same coherent bounded context read; include pending markers from supportive and manual work, even though Task 13 excludes those attempts from strength history. Marker lookup uses condition/exercise IDs, not only today's context digest, so edited context cannot hide it. Incomplete required history is blocked, not silently absent.
- [ ] Run targeted tests plus existing `ExerciseEligibilityPolicyTest` and context-builder tests. Commit as `feat: resolve reviewed condition constraints deterministically`.

## Task 11: Integrate supportive work into normal generation and duration

**Files:** Modify `M/core/ai/{FakeWorkoutPlanner,DefaultExercisePrescriptionFactory,WorkoutDurationEstimator}.kt`,
`M/core/model/Workout.kt`; extend corresponding JVM tests and `WorkoutFocusCoverageTest`.

**Interfaces:** Preserve `generateWorkout(context): GeneratedWorkout` and the
ordinary factory's `createDecision(exercise, context): ProgressionDecision`.
Add `WorkoutDurationEstimator.estimateSeconds(exercises: List<PlannedExercise>): Long`
as the shared unclamped calculation; existing minutes formatting delegates without
changing legacy results. Duration budget for condition plans is the existing
preferred minutes × 60, with no arbitrary new minimum or stretch dose.

- [ ] Add tests for mixed ordinary/supportive workout, ordinary-only focus, reserved supportive IDs preventing double selection, zero feasible strength focus, whole-session duration overflow and no-condition fixture equivalence. Test a supportive-only recovery session is not silently substituted for the requested ordinary workout.
- [ ] Run targeted tests to observe failure. Reserve required supportive time/IDs before ordinary selection, apply Task 10 to ordinary prescriptions after ordinary progression, and include compatible supportive work only when its reviewed schedule applies. Preserve a usable advertised strength focus; report insufficiency rather than quietly changing the promise.
- [ ] Keep supportive targets exact and independently authorized; they never call generic duration/strength progression or inherit user rest overrides outside approved bounds. Include condition/rule/execution identity in ordinary base-configuration digest so changed clinical context cannot reuse progression continuity.
- [ ] Estimate all prescribed work using shared seconds; reduce optional ordinary work within existing rules to fit, never below condition-required bounds. If required work cannot fit, return the existing generation-failure flow with typed condition reasons.
- [ ] Run targeted tests and no-condition planner corpus; commit as `feat: include reviewed supportive work in personalized sessions`.

## Task 12: Validate whole sessions and bind recommendation identity

**Files:** Modify `M/core/ai/{ProgramValidator,GeneratedWorkoutValidator,RecommendationContextIdentity,RecommendationSnapshot}.kt`,
`M/core/model/RecommendationRecord.kt`; extend validator, identity and snapshot tests.

**Interfaces:** Preserve `ProgramValidator.validate(workout, context, allowRepair=false)`;
structural validation sees the union of authorized IDs, then purpose-specific checks
enforce their membership. Snapshot carries `conditionContext: SessionConditionContext?`
in memory; `asRecord` stores only its digest. Start persists the same frozen context
on the session. Increment the existing v5 context identity format for these inputs.

- [ ] Test ordinary progression receipts match ordinary IDs only; supportive decisions independently cover supportive IDs; changing purpose cannot evade review, focus, limits or dose accounting; supportive exercises never satisfy movement/family/focus constraints. Exact duplicate exercise IDs remain invalid across purposes.
- [ ] Test actual duration fits condition budget; unsupported schedule/reassessment blocks; allowance repair cannot change supportive dose, weaken restrictions or cross ordinary condition bounds. Malformed rule references and a changed support file invalidate freshness even without profile edits.
- [ ] Run the targeted validator/identity tests to observe failure. Implement purpose dispatch, condition validation and frozen-context binding; include consumed support, prescription, schedule/reassessment and catalog identities in the generation digest.
- [ ] Run targeted tests and `ProductionPlannerCompositionTest`, including existing joint-sensitive refusals. Commit as `feat: validate and record condition-aware recommendations`.

## Task 13: Keep supportive history out of strength progression and credit

**Files:** Modify `M/core/ai/{WeeklyDoseLedgerCalculator,LedgerSourceFingerprint,TrainingFrequencyRecencyPolicy,CapabilityEvidencePolicy,WorkoutHistoryAnalyzer,ProgressionEngine,DefaultExercisePrescriptionFactory}.kt`,
recorded-workload lookup in `WorkoutGenerationContext.kt`, relevant projections in
`M/core/progress/{ProgressCalculator,ProgressAnalyticsCalculator}.kt` and repository
SQL filters. Extend matching existing tests, including progress repository tests.

**Interfaces:** Existing APIs remain. Ignore SUPPORTIVE before strength classification;
update ledger policy/fingerprint identity so cached old results cannot be reused.
Generic ordinary progression cannot consume supportive attempts. Activity totals,
completed sessions and supportive history remain visible.

- [ ] Use the same accepted exercise ID ordinarily in one session and supportively in another. Assert only ordinary sets earn strength dose/recency/capability/working-load/progression credit, while both remain in logged activity/history. Cover null legacy purpose default versus unknown stored purpose failure.
- [ ] Assert supportive pain stops still prevent advancement under existing whole-session stop guards and remain available to Task 10's reassessment logic. Test changed purpose changes ledger fingerprint and invalidates persisted cache.
- [ ] Run targeted ledger/history/progression/progress tests to observe failure. Apply per-instance purpose filters before accepted strength metadata, preserve whole-session stop handling, and keep measurement-based activity views honest about what they include.
- [ ] Run the targeted suites plus disk-persistence ledger tests; commit as `fix: isolate supportive work from strength evidence`.

## Task 14: Enforce condition context at every session start and reassessment

**Files:** Modify `M/feature/today/TodayViewModel.kt`,
`M/core/database/repository/WorkoutRepository.kt`, `M/core/database/dao/Daos.kt`,
`M/core/database/dao/ConditionReassessmentDao.kt`,
`M/core/database/repository/UserProfileRepository.kt`,
`M/feature/templates/WorkoutTemplatesViewModel.kt`, context/history repository reads;
extend `TodayProductionLifecycleTest`, `TodayViewModelTest`,
`WorkoutTemplatesViewModelTest`, `A/core/database/{ProductionPlannerPersistenceTest,WorkoutTemplateSessionTest,ProgramPersistenceTest}.kt`.

**Interfaces:** Keep `startWorkoutFromGenerated(...)` and `startWorkoutFromTemplate(...)`.
Generated starts with active conditions require a validated snapshot/context;
optional `recommendation = null` cannot bypass checks. Manual starts evaluate the
converted concrete prescription using the same policy and persist session context,
but no automatic recommendation. Use a typed `ConditionStartRefused` error.
Add `WorkoutSetDao.recordOutcomeAndConditionStops(setId: String, performance: SetPerformanceInput): Int`
as a transaction wrapping the existing guarded outcome write and pending-marker
updates. Add `UserProfileRepository.reassessCondition(conditionId: String, exerciseId: String, expectedEventId: String, answers: Map<String, String?>)`;
it validates reviewed response inputs before atomically applying them and clearing
only the matching pending event through `ConditionReassessmentDao`.

- [ ] Test ordinary/program/manual starts with valid, conflicting, unassessed and stale conditions. For active conditions, manual conflict or unassessed work is refused with edit guidance; there is no unchecked-start bypass. Existing active-session resume and confirmed-unit behavior remain intact.
- [ ] Test profile/support/history changes between preview and start, including an intervening pain stop. Final DAO profile revision and active-session checks must reject the race; profile condition edits are guarded as in Task 8. Reject generated starts lacking condition provenance. Test cancellation and completion edits cannot erase a marker, an identical outcome retry is idempotent, and a newer stop defeats stale reassessment.
- [ ] Run the targeted JVM/device tests to observe failure. Rebuild context and revalidate at starts; manual check occurs after unit conversion. Insert frozen context and digest binding atomically with session/exercises/sets. Never rewrite stored templates or completed history.
- [ ] On a newly persisted PAIN_STOP, derive applicable conditions from the session's frozen context and upsert fresh pending-event identities in the same transaction as the guarded set update; advance profile revision so previews become stale. Failed outcome writes create no marker. Identical retries create no new event. Ordinary feedback/completion edits and cancellation never clear markers.
- [ ] Implement source-bound reassessment: validate reviewed answers and current supported response policy, then in one transaction require the expected profile revision, no active workout and exact pending event ID, update selections, delete only that event and advance revision. The deleted source set need not exist. Unknown policy/context stays pending; a newer event or editing/removing/re-adding a condition cannot bypass it.
- [ ] Run targeted lifecycle/race tests and commit as `feat: enforce condition checks across workout entry points`.

## Task 15: Add supported-condition selection and truthful workout/history copy

**Files:** Modify `M/feature/profile/{ProfileScreen,ProfileUiState,ProfileViewModel}.kt`,
`M/feature/onboarding/{OnboardingScreen,OnboardingUiState,OnboardingViewModel}.kt`,
`M/feature/today/{TodayScreen,TodayViewModel}.kt`,
`M/feature/templates/{WorkoutTemplatesScreen,WorkoutTemplatesViewModel}.kt`,
`M/feature/workout/{ActiveWorkoutScreen,ActiveWorkoutUiState,ActiveWorkoutViewModel}.kt`,
`M/feature/history/{WorkoutHistoryScreen,HistoryPresentation}.kt`;
wire through `M/app/WallCrawlApp.kt`, `M/WallCrawlApplication.kt`.
Modify `app/src/main/res/{values,values-es}/strings.xml`, exercise localization and
`M/core/ui/localization/ExerciseVocabulary.kt` guidance fallback.
Create `A/core/ui/ConditionControlsTest.kt`; extend profile/onboarding/history tests.

**Interfaces:** Reuse current draft/save/cancel profile controls. Options come only
from released support records; selected withdrawn IDs remain visible as unsupported.
Required prompts/choices are localized reviewed content, canonical tokens are engine
input. UI saves through `updateActiveConditions`; history reads frozen session
context, not today's support catalog. Add no diagnosis search engine or free-text
medical interpreter. No condition picker is offered when there are no released
records and no retained selections.

- [ ] Test add/update/remove, unknown answers, withdrawn selection, empty support store, active-session refusal, process recreation, large text, accessible labels and English/Spanish switching. Locale changes must not change planning decisions or erase drafts.
- [ ] Test supportive work clearly identified in preview/logging/history; affected-side/execution instructions use the reviewed rule. If a protocol's required execution cannot be represented unambiguously, do not release its mapping. Display existing generic activity totals without presenting them as recovery scores.
- [ ] Run targeted JVM and UI tests to observe failure. Implement controls and start/refusal explanations, source-bound post-stop prompts and frozen-history rendering. Allow finishing/cancelling an existing session; never rewrite it after a profile/content update.
- [ ] Test history after changing/removing a condition and withdrawing support; show original recorded facts and unsupported versions honestly. Run localization/copy tests, update any medical-promise checker narrowly to permit factual qualified condition labels without weakening its unsupported-promise protections.
- [ ] Inspect actual app captures in both themes/languages with large text, then commit as `feat: expose reviewed condition support in workout flows`.

## Task 16: Verify the integrated feature and promote only reviewed content

**Files:** Update `docs/condition-aware-workouts.md`, `docs/privacy.md`,
`docs/planner-evaluation.md`, `docs/exercise-illustration-variants.md`, `README.md`,
`ROADMAP.md`; update support release data only with genuine completed review.
Add scenario fixtures to existing planner corpus and `app/build.gradle.kts` test
inputs if tests read new non-asset files. Add clone-safe supplement/art validation
to `.github/workflows/ci.yml`; keep its current SDK matrix.

**Interfaces:** Release manifest binds exact support, exercise, clinical review and
artwork identities. No source or pose change inherits an old approval. Existing
strength metadata acceptance and clinical review remain separately identified.

- [ ] Add end-to-end fixtures: no selected condition regression, one released synthetic condition, two compatible conditions, incompatible combination, missing context, withdrawn record, insufficient duration/equipment, pain stop, stale preview, manual and program paths. Every fixture states its expected typed outcome and purpose-specific dose.
- [ ] Run `python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v`, both corpus validators, `check_pinned_catalog.py` and `package_illustrations.py --check-bundled`. All must exit 0 with actual executed cases; confirm unchanged upstream assets and old provenance.
- [ ] Run `./gradlew testDebugUnitTest lint assembleDebug --no-daemon`; then the full connected suite on API 36 and 37, and the existing API 26 app boundary suite. Confirm instrumentation reports contain nonzero tests; do not accept a wrapper exit 0 without reports. Use disposable device data, never erase a shared device.
- [ ] Review complete new PNG/SVG coverage and real playback at app sizes, record per-exercise limitations, verify attributable local sources and readable exports, and measure packaged asset/APK growth.
- [ ] If qualified clinical review is available, verify it covers exact final rules/execution, record authentic provenance and promote only those records. Re-run affected content/planner tests after any promotion change. If unavailable, keep release data empty/withheld as appropriate and report the clinical gate as outstanding; do not falsify completion or trigger an unsolicited external contact.
- [ ] Update documentation with actual supported counts, source review dates, remaining research-only conditions, PNG master locations, verification outputs and limitations. Run `git diff --check`; commit the verified integration. Perform a whole-change review before any separately authorized delivery/merge.

## Plan self-review and handoff

Coverage: evidence and 50-record inventory→1–3; support/selection semantics→4,10,15;
catalog/provenance and PNGs→5–6; prescription purpose→7,11–13;
Room/archive/privacy/history→8–9,12,15; all entry points and races→14;
locale/accessibility/no-condition behavior→15–16; clinical promotion→3,16.
All five Review Focus risks have named tests above. No executable clinical numbers
are introduced by this plan. Conditions requiring unsupported schedules/monitoring
remain research-only rather than extending scope silently.

The user's requested subagent research method is preserved. Review this plan before
implementation under the writing-plans workflow; the design itself is already
approved and does not need another approval round. A real clinical content review
remains a separate release dependency, not something a code reviewer or agent can
grant.
