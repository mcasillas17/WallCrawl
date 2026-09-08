# WallCrawl Roadmap

> **Status date:** 2026-09-06
>
> **Evidence baseline:** `6a2f624` (roadmap, #55). Package 1 status additionally
> reflects the checked-in manifest, backup-rule resources, packaged-configuration
> guard, and [privacy policy](docs/privacy.md). Package 2 status reflects the
> checked-in `core/backup` archive contract, local-data DAO and repository,
> `feature/backup` controls, and their Android instrumentation tests. Localization
> status reflects the checked-in `values-es` resources, the `core/locale`,
> `core/ui/format`, `core/ui/localization`, and `core/exercise/localization`
> packages, the bundled translation overlay, and their JVM and Android tests.
> Package 5 status reflects `ProgressRepository`, the calendar-week calculators,
> localized Progress disclosures, and their JVM and Android integration/UI tests.
>
> This is the single source of truth for current project status, priority, dependency
> order, and implementation scope. Status must be derived from repository evidence rather
> than unchecked boxes in older planning records.

WallCrawl is building from a local-first Android workout planner into a deterministic
adaptive coach, followed by optional on-device ranking and optional Health Connect/Wear OS
integrations. Core workouts must remain offline, account-free, and usable without a model,
network connection, or companion device.

## Verified status

| Area | Status | Remaining gap |
| --- | --- | --- |
| Android foundation | Room schema 12 with a continuous migration chain; implicit Android backup disabled with legacy and modern all-domain exclusions; user-owned export, restore, and delete-all shipped | OEM transfer enforcement varies; restore is empty-destination only; `targetSdk` remains 35 while `compileSdk` is 37 |
| Catalog and reviewed content | All 302 entries have per-ID AI evidence records; 906 SVG frames and 131 legacy programming entries unchanged; 211 reviewed metadata drafts | Zero human approvals; 81 content/policy decisions pending, 35 entries outside automatic-strength scope; the band-only inventory still contains no chest work and the equipment-representation gap remains, though the session is now labelled truthfully |
| Onboarding and profile | Shipped as an eight-step flow with seven movement-capability questions, plus export, restore, and delete-all controls | Restore requires a fresh start, so it cannot merge into an installation that already holds data |
| Templates and logging | Shipped with frozen template snapshots, type-aware outcomes, RPE/RIR, typed stops, and a local rest timer | Template targets are only partly editable; unsaved drafts are not restored after process death |
| Localization | English and neutral Latin American Spanish shipped across the whole interface, the 302-exercise catalog, generated workout text, and accessibility labels, selectable from onboarding and Profile through Android's per-app language mechanism | Only two languages; historical session text stays in the language it was written in, by design |
| Progress and history | Calendar-week activity, separately labelled reviewed primary dose, non-additive involvement, records, trends, summaries, and recent history implemented | Workout-summary navigation and history drill-down remain incomplete; production reviewed muscle allocation is unavailable while metadata remains unapproved |
| Deterministic coach | Eligibility, experience ranking, capability evidence, weekly ledger, state-based dose/effort/rest, whole-program validation with recorded recommendation provenance, and a shared advertised-focus contract shipped; reviewed-only rules remain behind a disabled gate | Human approval, release corpus, progression, deload, and rollout gates |
| Planner evaluation | Versioned corpus, replay harness, importer unit tests, real pinned-upstream regeneration check, JVM tests, and Android CI shipped | `concurrent-activity` and policy-specific assertions |
| Optional local model | Not started | Blocked on a stable deterministic release |
| Health Connect and Wear OS | Not started; only `:app` exists | Shared modules, privacy controls, validation, substitutions, protocol, and device evidence |
| Operational quality | Dependabot, SBOM submission, JVM/lint/build CI, API 36 instrumentation, and prerelease automation shipped | Accessibility baseline, target SDK review, Room schema export, signed/minified release posture, and next alpha |

The production path today is the legacy deterministic planner. Reviewed eligibility,
capability-evidence ranking and state-based guidance are
compiled but inactive because `PlannerFeatureFlags.reviewedCapabilityEligibility` is `false`.
Progress reads `PRIMARY_ONLY_V1` for accounting only; that does not enable reviewed planning.
The reviewed cohort is 211 `DRAFT` / 0 `APPROVED`; approval is a human-authored data change,
not a consequence of merging a pull request.

## Recorded decisions

**Android backup policy (Package 1):** disable implicit Android cloud backup and
request exclusion of all app data from device transfer. The manifest sets
`allowBackup="false"` and references exclude-all legacy rules plus separate modern
cloud-backup/device-transfer rules. This preserves local persistence and compatible
in-place upgrades but sacrifices automatic recovery after uninstall, device loss, or
replacement. It does not delete previously uploaded backups or promise universal OEM
transfer enforcement. See the
[privacy and backup policy](docs/privacy.md) for the exact boundary and tradeoffs.

**Restore semantics (Package 2):** recovery is user-driven and empty-destination only.
Export writes one versioned, checksummed JSON archive to a document the user picks;
restore is accepted only when there are no workouts, no templates, and onboarding is
unfinished, so it can never merge into or overwrite existing data. The supported route
is export, then delete-all or reinstall, then restore. Merging and replacement were
rejected for this release because they need conflict rules for identifiers, active
workouts, and units that nothing in the app can currently decide.

**Localization boundary (Spanish support):** language is a device presentation
preference and never an input to a training decision. Identical canonical inputs produce
identical selections, ordering, prescriptions, loads, rest targets, eligibility outcomes,
and policy reasons in either language, and stored measurements are unchanged. Interface
text lives in `values/` and `values-es/`; exercise names, coaching summaries, and the
muscle and equipment vocabulary live in a separate validated overlay keyed by the pinned
catalog's own identifiers, so translating never edits the catalog, its source manifest, or
any reviewed metadata, and never confers programming approval. The language choice is
stored by the platform per-app language mechanism, not in the profile, not in Room, and
not in the export archive; an archive exported under one language restores unchanged under
the other. User-authored text and the title and explanation stored with a past session are
never translated or rewritten, so history keeps the wording it was recorded with. See
[Localization](docs/localization.md).

**Progress weekly meaning (Package 5):** retain logged activity first, with reviewed primary
dose separately labelled and accounting detail behind disclosures. Both use ISO Monday
weeks in the device's explicit time zone. Legacy primary-muscle involvement is descriptive,
overlapping activity, not an alternative dose or unique-set total. Missing reviewed
allocation never erases completed workouts. An unfinished current week preserves a streak
through the previous week; zero-baseline comparisons do not manufacture growth percentages.
See the [metric contract](docs/weekly-dose-ledger.md#progress-metric-contract).

## Open decisions

These decisions must be recorded before the related implementation package closes.

1. **Deadlift direct primary:** a qualified human reviewer must ratify the single
   `directPrimaryMuscle` for `barbell-deadlift`; automation or PR approval cannot decide it.
2. **Initial approval cohort:** the content review now covers all 302 catalog IDs, not
   a sample. Separately decide the human-approved rollout cohort from the 211 draft
   proposals and unresolved entries, with explicit equipment/persona coverage. Neither
   a smaller rollout cohort nor a completed AI review changes the meaning of approval.
3. **Validation persistence:** ~~decided~~. A dedicated `workout_recommendation_records`
   table (Room schema 12) holds one immutable row per started session, written in the same
   transaction as the session, and travels in archive format version 2. Columns beside the
   session were rejected: the record is provenance about a decision rather than part of the
   session, and a separate table keeps it out of every ordinary session read.
4. **Reviewed rollout scope:** decide whether the first reviewed-planning release may remain
   conservatively non-progressing or must wait for progression and user-controlled deload.
   This is a product/release decision, not a technical dependency of eligibility.
5. **Deload experience:** define where an offer appears, how the user accepts or declines it,
   and whether that choice persists. Deload must never be automatic or diagnostic.
6. **Release posture:** decide when to raise `targetSdk`, enable release shrinking, configure
   signing, and move beyond debug prerelease APKs.
7. **Optional sync:** explicitly approve the product/privacy scope before a sync design is
   written; local operation without an account remains non-negotiable.
8. **External-record deletion:** disabling an integration must stop future writes without
    silently deleting external records; users also need a separate, explicit way to delete
    WallCrawl-exported Health Connect records before local export IDs are removed.
9. **Watch ownership:** the first companion release should allow one active execution owner.
    Decide whether additional paired watches are rejected or read-only; multi-watch editing
    must not enter the protocol without an explicit conflict policy.
10. **Validation contract:** ~~decided~~ and recorded in the
    [whole-program validation design](docs/superpowers/specs/2026-09-06-whole-program-validation-design.md).
    The validated unit is one proposed session with no multi-session horizon; duplicate and
    coverage rules are declared per session and only exercise-id uniqueness defaults on;
    `DURATION_ESTIMATOR_V1` is the named estimator with a ±1-minute tolerance and no
    requested-duration rule; the whole proposal is accounted prospectively against one
    configured allowance; a changed context fingerprint refuses a stale start rather than
    repairing it. Recency remains undesigned and no blocking recency rule was added. See the
    [evidence-to-rule mapping](docs/research/2026-08-29-training-science-evidence-review.md#validation-scope-clarification-2026-09-05).

## Dependency map

| Workstream | Sequence | Parallelism and gates |
| --- | --- | --- |
| Privacy and ownership | 1 (complete) -> 2 (complete) | Users can export, restore into a fresh start, and delete every local record |
| Reviewed content | 3 | Human-paced and safe to run beside engineering |
| Deterministic rollout | 4 + 5 + 6 -> 7 | Package 7 also requires package 3; the package 1 release gate is satisfied |
| Adaptive coaching | 7 -> 8 and 17; 4 -> 9; 3 + 4 -> 10; 3 -> 11; 4 + 9 -> 12; 10 -> 13; 5 + 13 -> 14; 3 -> 16 | Package 15 is independently useful but integrates package 10 when it exposes substitutions; package 18 gates only the packages selected for that release |
| Operational quality | 19 + 20 | Parallel unless a release gate says otherwise |
| Integrations | 21 -> 22 + 23; 2 + 4 + 10 + 21 + 23 -> 24; 23 + 24 -> 25; 24 + 25 -> 26 -> 27 | Health export and the protocol spike may proceed independently after shared-module extraction |
| Optional sync | 2 -> 28 | Package 28 is a design package first, not implementation authorization |

Safe parallel work now is package 3, package 5 after its product
decision, package 6, and the audits in packages 19-20. Do not enable package 7 against draft
metadata or a persona cohort that cannot produce valid plans. Do not split adaptation-state
widening from the advanced-complexity ceiling update in package 9.

## Now: make reviewed deterministic planning releasable

### 1. Resolve Android backup and privacy behavior

**Status:** Complete. Implicit backup is disabled and the documented boundary is explicit.

**Decision:** Recorded above; no implicit cloud backup or permitted app-data transfer.

**Implemented:**

1. Set `allowBackup="false"` and reference both legacy `fullBackupContent` and API 31+
   `dataExtractionRules` resources, with separate cloud-backup and device-transfer sections.
2. Exclude all nine documented app-data domains at their roots, including the full Room
   database directory and SQLite sidecars, rather than one guessed filename.
3. Guard the installed flags, target APK's merged manifest, referenced resources, and
   exclusion semantics with `BackupPolicyResourceTest`. The guard rejects the original
   configuration, filename-only database exclusions, and missing device-transfer rules.
4. Align README, architecture, ledger, eligibility, and the shared [privacy policy](docs/privacy.md).

**Surfaces:** `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/`,
`app/src/androidTest/`, `README.md`, `docs/architecture.md`,
`docs/weekly-dose-ledger.md`, `docs/reviewed-capability-eligibility.md`, `docs/privacy.md`.

**Boundary:** Packaged guards cover API 30 and 36; local Backup Manager requests reject the
app on both. These do not establish cloud restore or physical/OEM transfer behavior.
Existing local storage and upgrades are unchanged; no Room migration is needed. Package 2
now adds the user-owned export, restore, and deletion controls on top of it.

### 2. Add user-owned export, import, and deletion

**Status:** Complete.

**Depends on:** Package 1 (complete).

**Implemented:**

1. Archive format versioning in `core/backup`, separate from the Room schema version and
   carrying creation time, app version/code, schema version, and the bundled catalog commit
   as provenance. Package 2 shipped version 1; Package 4 extended the format to version 2
   and kept version 1 readable. It covers the profile and capabilities, templates, and every session with
   its planned targets, performed values, units, feedback, timestamps, stop reasons, and
   status. The derived weekly-ledger cache is deliberately not exported.
2. A SHA-256 checksum over the archive's canonical serialization, plus one validation
   contract — bounds, enums, lengths, duplicate and blank identifiers, parent/child
   references, resource limits, and the domain rules the app already enforces — run
   identically when writing and reading. Unsupported versions and every rejection are
   reported through typed, resource-backed copy.
3. Storage Access Framework export and import with no upload or network path. An export is
   refused before the destination is opened, so a refusal cannot truncate the file the user
   picked; a document a failed or cancelled export did truncate is discarded.
4. Restore into an empty destination only, in one transaction that rechecks eligibility from
   inside, with the ledger cache cleared and rebuilt from restored history.
5. Delete-all-local-data behind a destructive confirmation that names the affected data and
   the active workout, returning the app to fresh onboarding. A shared write gate keeps an
   in-flight profile or template write from landing after the deletion.
6. Archive compatibility, export sensitivity, document-provider behaviour, restore
   prerequisites, deletion scope, and remaining recovery limits documented in
   [privacy](docs/privacy.md), with the surfaces described in
   [architecture](docs/architecture.md).

**Surfaces:** `app/src/main/java/wallcrawl/elopenmike/com/core/backup/`,
`core/database/dao/LocalDataBackupDao.kt`,
`core/database/repository/LocalDataBackupRepository.kt`, `core/io/`, `feature/backup/`,
app navigation, `feature/profile`, `feature/onboarding`, `docs/privacy.md`,
`docs/architecture.md`, and Android instrumentation tests.

**Boundary:** Package 2 needed no Room migration and left the schema of the day, and every
existing backup exclusion, unchanged. Restore is empty-destination only: merging and replacement are deliberately
not implemented, so the supported recovery route is export, then delete or reinstall, then
restore. A checksum detects damage; it is not encryption and not proof of authenticity. The
document picker may offer cloud-backed destinations, and deletion reaches only this device's
copy.

### 3. Complete reviewed metadata coverage and human approval

**Status:** Full-catalog AI review and sign-off preparation cover all 302 IDs:
186 ready for human inspection, 81 pending evidence/policy and 35 outside automatic
strength scope. There are 211 authored `DRAFT` records and zero `APPROVED`.
Package 3 is **not complete**.

**Depends on:** Human decisions for direct-primary classification and first-cohort scope.

**Implementation tasks:**

1. Ratify the `barbell-deadlift` single-primary allocation and resolve the current
   per-ID decisions in the [unsigned sign-off worksheet](docs/reviewed-exercise-metadata-human-signoff.md).
   The historical `READY_AFTER_CORRECTIONS` verdicts did not grant approval.
2. Resolve the separately documented [band-only push and fixed-anchor gaps](docs/reviewed-catalog-coverage.md),
   specialized equipment requirements and selected joint-constraint mappings. No missing
   token is filled with a guessed generic apparatus, and no planner/artwork fix is implied.
   The **mislabelling** half of the band-only finding is closed: the planner no longer
   advertises `PUSH` for a pool whose only push evidence is a descriptive secondary muscle,
   and it reports the unavailable `Chest` priority instead. The **content** half is
   unchanged — there is still no band chest exercise and no anchor vocabulary — so this
   task stays open on exactly the evidence it always concerned.
3. Human-inspect the source-bound [full-catalog evidence ledger](docs/research/2026-09-07-full-exercise-catalog-review.json),
   including directed edges, complexity, support, impact, prescription shape, withheld
   blocks and artwork-reference restrictions. AI judgments and software checks are not
   clinical validation.
4. Record a real reviewer role, review time, provenance, and rationale in authored metadata.
5. Regenerate the bundled catalog and review report deterministically.
6. Re-run availability analysis before changing any production feature flag.

**Likely surfaces:** `tools/workout-guide/reviewed-metadata.json`,
`docs/reviewed-exercise-metadata-human-signoff.md`, importer tests,
`app/src/main/assets/workout-guide/catalog.json`, and the generated review report.

**Done when:** the approved cohort has traceable human provenance, every supported rollout
persona retains a valid candidate set, generated artifacts match authored inputs, and approval
does not silently enable production behavior.

### 4. Add whole-program validation

**Status:** Complete. `ProgramValidator` checks a complete proposal before it is shown and
again before it is started, and `workout_recommendation_records` (Room schema 12, archive
format version 2) records how each started session was decided. Reviewed-only rules stay
inert while `PlannerFeatureFlags.reviewedCapabilityEligibility` is `false`.

**Depends on:** Shipped weekly ledger and state-based policy. Tests use synthetic approved
metadata while package 3 proceeds; no production approval changed.

**Contract:** Follow the [evidence-to-rule mapping](docs/research/2026-08-29-training-science-evidence-review.md#validation-scope-clarification-2026-09-05).
Research-informed principles, versioned product policies, and software invariants are
distinct. Numeric physiological fatigue budgets are excluded from v1; legacy
`programming.fatigueScore` is an ordinal ranking label, not a summable physiological
measurement. Elapsed time alone establishes neither overload nor readiness and supplies no
universal recovery interval. Do not add a blocking recency rule while scheduling policy is
undefined.

**Implemented:**

1. `ProgramViolationCode` names every rule as either a software invariant or a versioned
   product policy: identifiers, candidate membership, prescription type, declared session
   constraints, explicit exclusions, reviewed metadata and provenance, review-policy
   equality, prescription shape, load provenance, duration agreement, ledger integrity,
   accounting overflow, and configured allowance. `GeneratedWorkoutValidator` now reports
   its existing structural checks as those typed values instead of a first-failure message,
   so `ProgramValidator` reuses them rather than restating them. Context freshness is a
   separate concern, decided in `TodayViewModel` by comparing digests rather than by a
   violation code.
2. Duplicate and coverage rules are **declared**, in `SessionProgramConstraints`. Only
   exercise-id uniqueness within one generated session defaults on, and its rationale is
   accounting and identity integrity, not a claim about repeated movement. Progression-family
   uniqueness and required movement patterns are inert unless a caller declares them, so no
   workout is required to cover any pattern and the planner's pattern spreading stays a
   ranking preference. `UNSUPPORTED_WORKOUT_FOCUS` was added later as an always-on software
   invariant of the same class as duration agreement — the advertised split must be one the
   selected exercises train — sharing the planner's
   [focus contract](docs/architecture.md#advertised-focus) rather than adding a competing
   classifier. It is not a pattern-coverage rule.
3. The whole proposal is attributed prospectively to approved `directPrimaryMuscle` values
   under `PRIMARY_ONLY_V1` and compared **once per muscle** to the configured allowance,
   instead of letting each exercise spend the same remainder. Completed credit and proposed
   targets stay separate fields; nothing writes to the ledger. A damaged ledger,
   unrepresentable arithmetic, and a full configured allowance remain three distinct typed
   reasons, and `NEEDS_ONBOARDING` records an absent allowance rather than a violation. No
   weekly minimum and no automatic increase were added.
4. Load provenance keeps a null target null and accepts a value only from a confirmed
   starting load or the last recorded load, optionally plus the shipped legacy 5.0 lb /
   2.5 kg increment. `DURATION_ESTIMATOR_V1` is shared by the planner and the validator with
   a ±1-minute tolerance and deliberately no requested-duration rule.
5. Exactly one deterministic repair pass may reduce sets, in recommendation order, with every
   affected exercise keeping at least one; it recomputes the duration and fails closed rather
   than dropping an exercise. It never weakens a constraint, widens the candidate set,
   invents a load, or leaves reviewed-only eligibility. Repair is disabled at start.
6. `TodayViewModel` validates before display and revalidates at start against a freshly built
   context, comparing `RecommendationContextIdentity`. Profile, history, and week or
   time-zone changes surface typed, resource-backed English and Spanish copy instead of
   starting a stale plan.
7. `workout_recommendation_records` (additive migration `11 → 12`) records the validator,
   estimator, catalog, review, training-policy, ledger, and program-state versions, the
   adaptation state, accounting week and zone, profile revision, context digest, ordered
   reason codes, and per-muscle completed/proposed/allowance counts. It is written in the same
   transaction as the session, so a refused start leaves neither, and it travels in archive
   format version 2 while version 1 archives still restore.

**Surfaces:** `core/ai/ProgramValidator`, `core/ai/ProgramViolation`,
`core/ai/RecommendationSnapshot`, `core/ai/RecommendationContextIdentity`,
`core/ai/WorkoutDurationEstimator`, `core/ai/GeneratedWorkoutValidator`,
`core/model/RecommendationRecord`, `core/database` (schema 12, DAO, mapper, payload),
`core/backup` (archive version 2), `feature/today`, `WallCrawlApplication`, string
resources, and their JVM and instrumentation tests.

**Boundary:** the reviewed path stays production-disabled, all 211 reviewed entries remain
`DRAFT`, and no policy value, metadata approval, or feature flag changed. Passing these
checks demonstrates software-contract conformance, not scientific or clinical validation.
The record explains and detects; it does not promise byte-exact replay, because WallCrawl
keeps only one current profile row.

### 5. Reconcile Progress weekly semantics

**Status:** Implemented. Progress separates logged activity, reviewed primary dose, and
overlapping legacy primary involvement. Progress and Today's weekly count use the same
ISO Monday-to-Monday, explicit-time-zone boundaries.

**Decision:** Logged activity remains useful with the production all-DRAFT catalog.
`PRIMARY_ONLY_V1` is reused unchanged; no metadata approval or reviewed-planner enablement.

**Implemented contract:**

1. Completed workouts/sets (including warm-ups and timed work), applicable reps and external
   load × reps are activity; legacy muscle counts overlap and are never a unique-set total.
2. Reviewed dose credits one approved direct primary per completed non-warm-up work set;
   secondary involvement and typed omissions remain separate and understandable.
3. Current/previous calendar comparisons are explicit; missing baselines read as new activity.
   An empty unfinished current week preserves the prior streak and prior-week detail.
4. One write-gated Room transaction produces a coherent activity/ledger snapshot. Complete
   week ranges and lightweight streak/count inputs are uncapped by the 500-session trend
   window. No-profile, empty, unattributed, loading and error states remain distinct.
5. Invalidation, lifecycle resubscription, clock/zone changes and a week-boundary wakeup keep
   Progress current without query polling or post-delete cache resurrection.
6. English/Spanish resources, actual large text, light/dark rendering, range/DST/zone,
   multi-primary, synthetic approval, completion states, restore/deletion and failure paths
   are covered by focused JVM and Android tests.

**References:** [Metric and repository contract](docs/weekly-dose-ledger.md#progress-metric-contract),
[architecture](docs/architecture.md#feedback-loop-and-progress), and
[Progress screenshots](README.md#progress-activity-and-reviewed-dose).

History drill-down and deeper analytics remain Packages 13 and 14. Schema, archives,
recommendation snapshots, validation policies and planner behavior are unchanged.

### 6. Complete the deterministic corpus and CI release gate

**Status:** Partly shipped. The corpus has eleven manifest fixtures. CI tests importer and
checkout behavior with synthetic repositories and checks the complete committed catalog,
assets, and generated review report against the configured pinned upstream source. The
remaining persona, policy assertions, documentation audit, and version record are still open.

**Depends on:** Packages 4-5 for their final policy assertions.

**Implementation tasks:**

1. Add the missing `concurrent-activity` persona and keep the manifest/count contract explicit.
2. Add direct corpus assertions for `PRIMARY_ONLY_V1` attribution and state-based set caps;
   preserve the shipped no-invented-load, reviewed-only candidate-membership, and typed-failure
   assertions.
3. **Complete:** CI validates `import-config.json`, obtains a clean temporary Workout Guide
   checkout at its exact `sourceCommit`, and runs the real importer in `--check` mode against
   committed assets and the generated review report. Drift and checkout/import failures fail CI.
4. Correct stale schema/count comments and remove unsupported present-tense claims from active
   documentation without rewriting historical records as if they were current status.
   Classify policy assertions using the evidence-to-rule mapping; include negative cases
   against fatigue-score summation, timestamp-only recovery inference, and forced weekly
   minimums. Audit legacy recovery/injury-prevention copy rather than endorsing it.
5. Record the exact policy, catalog, review, fixture, and importer versions used by the gate.

**Likely surfaces:** planner fixtures and evaluator tests, `.github/workflows/ci.yml`, importer
configuration, and active architecture/evaluation documentation.

**Done when:** every supported persona replays deterministically, every policy invariant is
asserted, and CI proves both importer behavior and real pinned-source regeneration parity.
Passing this gate demonstrates software-contract conformance, not scientific or clinical
validation of the complete algorithm.

### 7. Enable reviewed planning deliberately

**Status:** Blocked; the production flag remains `false`.

**Depends on:** Packages 3-6; Package 1's release gate is satisfied. Package 9 is required only if the
initial rollout promises progression/deload rather than a conservative reviewed planner.

**Implementation tasks:**

1. Freeze the approved rollout cohort and rerun candidate availability for every supported
   persona using the real approved metadata, not synthetic test promotion.
2. Prove the reviewed path preserves hard constraints, no-invented-load, deterministic replay,
   and valid plans for every supported persona.
3. Document the initial rollout scope, especially whether progression/deload remains absent.
   Distinguish configured policy limits from medical safety and planned controls from shipped
   UI; metadata approval and passing fixtures do not validate the algorithm clinically.
4. Change `PlannerFeatureFlags.reviewedCapabilityEligibility` in a dedicated, reviewable
   change with a production-default assertion.
5. Retain a typed fail-closed path; do not fall back to an unreviewed candidate when the
   reviewed cohort is insufficient.

**Likely surfaces:** approved bundled metadata, planner fixture corpus,
`WallCrawlApplication`, `PlannerFeatureFlags`, eligibility/context tests, and release notes.

**Done when:** the approved cohort passes the complete release matrix, the rollout boundary is
honest, and the flag change contains no unrelated policy expansion.

## Next: complete the adaptive coach and product workflows

### 8. Complete capability-aware deterministic ranking

**Status:** Partly shipped. Experience ordering, reviewed capability soft-penalty suppression,
primary-before-secondary split ordering, and the shared focus contract that ordering now reads
exist; frequency, recency, and supported-regression preference do not.

**Depends on:** Package 7 for production reviewed behavior.

**Implementation tasks:**

1. **Complete:** the primary-before-secondary comparator is preserved and now reads the same
   [focus contract](docs/architecture.md#advertised-focus) as split selection and validation,
   which resolves to the approved `directPrimaryMuscle` on the reviewed path and to the legacy
   list otherwise. Production ordering is unchanged, because the bundled cohort is all `DRAFT`.
2. Prefer approved supported regressions when capability evidence or a limited preference
   makes them the clearer fit.
3. Define training frequency and recency as bounded, explainable scheduling preferences
   before incorporating them as ordering inputs. Specify attribution, lookback, and
   precedence; timestamps must not become overload/readiness judgments or recovery intervals.
4. Preserve candidate membership and all hard constraints; ranking may only reorder legal
   candidates.
5. Emit structured ranking reasons and lock comparator order with policy and planner tests.

**Likely surfaces:** `FakeWorkoutPlanner`, focused ranking policies,
`WorkoutGenerationContext`, planner fixtures, and architecture docs.

**Done when:** each ranking signal has a bounded precedence, equal inputs replay identically,
and adding a preference cannot introduce an otherwise illegal exercise.

### 9. Add one-variable progression and user-controlled deload

**Status:** Capability evidence shipped; progression, deload offers, and broader state
derivation are not started.

**Depends on:** Package 4. Package 7 depends on this only when progression is part of the first
reviewed rollout.

**Implementation tasks:**

1. Define comparable completed outcomes for load/reps, bodyweight reps, assistance,
   duration, and distance without treating missing effort as favorable.
2. Progress exactly one variable at a time and preserve no-invented-load behavior.
3. Replace or contain the existing simple history bump so two progression systems cannot
   stack.
4. Define a typed, versioned deload offer from an explicit request, returning state, or a
   reviewed multi-session pattern.
5. Add accept/decline UI and persistence only after the deload experience is decided.
6. Widen `AdaptationStatePolicy` and the advanced-complexity ceiling in the same change.
7. Add state-transition tables, mixed-unit cases, missing-feedback cases, and regression
   tests for the ceiling coupling.

**Likely surfaces:** progression/deload policies, prescription factory, adaptation and
eligibility policies, generation context, Today/Profile UI, and optional persistence.

**Done when:** recommendations advance one explainable variable from comparable evidence,
missing inputs remain neutral, deload is always user-controlled, and no newly derivable state
accidentally lifts the complexity ceiling.

### 10. Add validated in-session substitutions

**Status:** Not started; the reviewed substitution graph contains only limited authored edges.

**Depends on:** Packages 3-4 and enough reviewed substitution edges for supported personas.

**Implementation tasks:**

1. Generate candidates only from approved, equipment-compatible, constraint-compatible,
   type-compatible graph edges.
2. Rank candidates within the configured remaining-duration and weekly-dose allowances,
   preserving the intended session/program constraints and emitting structured reasons.
   Follow Package 4's accounting contract for completed versus unperformed work; do not sum
   legacy fatigue labels or infer recovery from recency.
3. Re-run whole-program validation before applying a substitution.
4. Replace only unperformed work and preserve completed sets under the performed exercise.
5. Store planned versus performed IDs and targets in the immutable session snapshot.
6. Add an active-workout selection flow with empty, conflict, stale-session, and persistence
   failure states.

**Likely surfaces:** reviewed metadata graph, a substitution policy, workout models and Room
mapping, active-workout ViewModel/UI, repositories, and migration tests if storage changes.

**Done when:** an active workout can swap only open work for a reviewed legal alternative,
history retains both intent and performance, and replay produces the same candidates/reasons.

### 11. Add reviewed exercise guidance

**Status:** Not started; current coaching summaries are legacy programming text, not reviewed
runtime cues.

**Depends on:** A reviewed content workflow and provenance contract.

**Implementation tasks:**

1. Define bounded setup, execution, common-fault, breathing, and provenance fields.
2. Import upstream or authored prose only as draft evidence until qualified review approves
   the exact shipped content.
3. Add deterministic length/count/character validation and attribution checks.
4. Render concise cues in active workouts and complete guidance in exercise details.
5. Exclude medical, injury-prevention, diagnostic, and unsupported performance claims.

**Likely surfaces:** importer overrides and report, exercise domain/parser, exercise detail,
active workout, content tests, and attribution docs.

**Done when:** every displayed cue is approved, bounded, attributable, accessible, and
distinguishable from unreviewed upstream prose.

### 12. Add explicit multi-week program blocks

**Status:** Not started.

**Depends on:** Packages 4 and 9.

**Implementation tasks:**

1. Define versioned block, week, objective, and transition contracts without mutating completed
   session snapshots.
2. Persist active block state separately from immutable history.
3. Generate each session through the same deterministic eligibility, dose, progression,
   validation, and no-invented-load pipeline.
4. Define pause, resume, restart, completion, and policy-version migration behavior.
5. Add replayable multi-week fixtures and program-level validation.

**Likely surfaces:** new program domain and repository boundaries, generation context, planner,
Today/Progress UI, Room migration, and program fixtures.

**Done when:** a versioned block can survive process death and app upgrade, every session still
passes the single-session gates, and historical recommendations remain reproducible.

### 13. Wire workout summary and immutable history detail

**Status:** Partly shipped. `WorkoutSummaryScreen` renders inside the active-workout state, but
the declared summary route is not registered; recent history has no detail destination.

**Depends on:** Shipped immutable session snapshots; package 10 for substitution detail.

**Implementation tasks:**

1. Decide whether summary remains an active-workout state or becomes the declared
   `workout_summary/{sessionId}` destination; remove the unused alternative.
2. Add `history/{sessionId}` navigation from completed-workout cards.
3. Render stored targets, performed values, unit, timestamps, effort, stop reasons,
   substitutions, notes, guidance, explicit user overrides, and policy reasons from the
   snapshot rather than the current catalog/profile.
4. Handle missing/deleted catalog records with stored IDs and measurements.
5. Show prior-versus-planned-versus-performed comparisons without manufacturing a trend or
   growth percentage from one observation.
6. Add route, state-restoration, and ViewModel tests.

**Likely surfaces:** app routes/NavHost, active workout, workout summary, Progress history cards,
new history feature state/UI, and repository summary/detail queries.

**Done when:** completion has one intentional summary flow and any completed session can be
opened as an honest immutable historical record.

### 14. Deepen progress analytics

**Status:** Overview metrics, records, muscle counts, trends, and recent history shipped.

**Depends on:** Package 5 for weekly semantics and package 13 for drill-down.

**Implementation tasks:**

1. Add time-range and exercise/muscle filters with explicit unit conversion.
2. Add charts only for metrics with enough observations and a defined calculation contract.
3. Explain omissions for duration, distance, bodyweight, mixed-unit, and unreviewed work.
4. Link chart points and progression decisions to immutable history detail.
5. Add boundary, sparse-data, unit, and accessibility tests.

**Likely surfaces:** progress calculator/models, Progress ViewModel/UI, reusable chart
components, history navigation, and calculation docs.

**Done when:** every chart has a documented formula, sparse history never manufactures a
trend, mixed units are converted rather than relabeled, and displayed numbers trace to
completed sessions.

### 15. Complete template prescription editing and draft recovery

**Status:** Templates support catalog selection, ordering, deletion, and set-count changes.

**Depends on:** Existing prescription validation; package 10 if templates expose reviewed
substitution choices.

**Implementation tasks:**

1. Add a typed draft for sets, rep range, confirmed load/assistance, duration, distance, rest,
   effort target, and note fields appropriate to each exercise type.
2. Validate the complete draft before conversion to `ExercisePrescription`.
3. Preserve unsaved edits across configuration and process recreation.
4. Add discard confirmation and recoverable save errors.
5. Prove starting a template still creates an immutable session snapshot unaffected by later
   template edits.

**Likely surfaces:** template editor state/ViewModel/UI, prescription factory/validation,
saved-state or draft persistence, repository tests, and custom-workout docs.

**Done when:** every supported prescription shape is editable without invalid field
combinations, drafts recover predictably, and completed history never changes with a template.

### 16. Expand reviewed content beyond automatic planning

**Status:** All 302 entries now have an individual AI evidence review; 35 are outside
the current automatic-strength contract. Browsing/manual access remains complete.
Human approval, reviewed-guidance presentation and support for excluded prescription
categories remain open; this package is not complete.

**Depends on:** Package 3's repeatable approval process.

**Implementation tasks:**

1. Prioritize coverage from real browse/manual usage and unsupported equipment or movement
   families.
2. Apply the same provenance, review, graph, and importer validation contract as the initial
   cohort.
3. Expose reviewed guidance and compatibility in browsing/manual workflows without hiding
   unreviewed exercises.
4. Track approval coverage and planner reachability as generated reports.

**Likely surfaces:** reviewed metadata and coaching overrides, importer/report tooling, exercise
library/detail, templates, and catalog tests.

**Done when:** coverage can grow incrementally without changing the meaning of approval or
making browsing/manual workouts depend on automatic-planning eligibility.

### 17. Add optional bounded local inference

**Status:** Not started.

**Depends on:** Stable deterministic rollout and release corpus; core workouts must already be
complete without a model.

**Implementation tasks:**

1. Add a deterministic readiness gate and provider-neutral states for unavailable, download
   required, ready, busy, timeout, cancellation, thermal/battery abort, and corrupt model.
2. Parse only bounded non-safety preferences and require explicit confirmation before applying
   them.
3. Serialize exact candidate-slot IDs, allowlisted preferences, reason keys, and policy
   versions; exclude measurements, constraints, notes, and raw history.
4. Accept only exact candidate IDs and structured explanation keys; deterministic code retains
   eligibility, dose, progression, validation, and persistence.
5. Fall back immediately to the unchanged deterministic plan on every runtime, parser, schema,
   or validation failure.
6. Add privacy-safe audit records, adversarial fixtures, physical-device benchmarks, blind
   review, explicit opt-in, removal, and a kill switch.

**Likely surfaces:** a new `core/ai/local` boundary, tiered planner composition, profile/model
controls, evaluation fixtures, benchmark/evaluation docs, and deterministic fallback tests.

**Done when:** model absence or failure cannot block or change core workout behavior, hard
constraints pass every adversarial case, device limits are measured, and a predefined user
benefit beats the deterministic baseline.

### 18. Close the adaptive-coach release

**Status:** Not started.

**Depends on:** Packages 7-17 selected for the release scope. Optional package 17 must pass its
own gates if included; it is not required for deterministic operation.

**Implementation tasks:**

1. Reconcile product, architecture, privacy, planner, content, and evaluation documentation.
2. Keep generated-data and real pinned-source checks in CI.
3. Run the complete Python, JVM, lint, build, migration, instrumentation, persona, and
   adversarial matrix.
4. Configure the chosen signing/minification/versioning posture and verify artifact metadata.
5. Publish release notes that distinguish deterministic, reviewed, experimental, and deferred
   behavior.

**Likely surfaces:** active docs, CI/release workflows, version tooling, proguard/signing
configuration, and release artifacts.

**Done when:** the release artifact matches its documented capability boundary, every included
gate is green at one commit, and optional inference can be removed without affecting core use.

## Keep the foundation healthy

### 19. Complete Android and release maintenance

**Status:** Ongoing; Issue #45 tracks `targetSdk` 35 versus `compileSdk` 37.

**Depends on:** Platform behavior review before changing runtime targets.

**Implementation tasks:**

1. Review Android 16/17 target behavior changes and decide whether to step through 36 or move
   directly to 37.
2. Test permissions, notifications, edge-to-edge, background work, and critical flows on a
   matching emulator/device before raising `targetSdk`.
3. Enable Room schema export and commit schema JSON when the migration workflow is ready to
   treat it as an artifact.
4. Correct stale schema-10 CI comments and keep migration-chain tests aligned with each schema.
5. Decide signing, shrinking, and distribution requirements before a production release.
6. Cut a new alpha when testers should receive the merged #49-#54 behavior, with release notes
   that reviewed features remain disabled.

**Likely surfaces:** Gradle configuration, Room processor options/schema directory, CI/release
workflows, emulator matrix, Issue #45, and release tooling.

**Done when:** runtime target changes have device evidence, Room schemas are reviewable
artifacts, CI describes what it runs, and distributed artifacts have an intentional security
and versioning posture.

### 20. Establish an accessibility regression baseline

**Status:** Not yet audited as a complete product surface; several gym-floor controls already
have explicit target sizes and semantics.

**Depends on:** None. Apply fixes with the feature packages that own each screen.

**Implementation tasks:**

1. Audit TalkBack names, roles, state descriptions, traversal, decorative-image null
   descriptions, touch targets, text scaling, contrast, reduced motion, and error
   announcements across every route.
2. Distinguish intentional decorative `contentDescription = null` from unlabeled interactive
   controls.
3. Add reusable semantics and minimum-target patterns where current components are
   inconsistent.
4. Add Compose accessibility tests for onboarding, profile, templates, active workout,
   progress, exercise detail, credits, and every new integration screen.
5. Record manual screen-reader and large-font evidence for release-critical flows.

**Likely surfaces:** shared UI components, feature screens, string resources, Compose
instrumentation tests, and release verification docs.

**Done when:** every interactive control has an accessible name/role/state, critical flows work
with large text and TalkBack, decorative content remains intentionally silent, and regressions
are caught automatically where Compose supports it.

## Later: optional integrations

### 21. Extract shared phone/watch modules

**Status:** Not started; the repository contains only `:app`.

**Depends on:** Stable domain contracts from the deterministic coach.

**Implementation tasks:**

1. Add dependency-free `:core:model` and `:companion-protocol` modules before adding `:wear`.
2. Move only pure workout/profile/prescription contracts while preserving package names.
3. Keep Room, Android resources, Compose, repositories, and asset providers in phone-specific
   code.
4. Move tests with their contracts and prove no duplicate classes or phone behavior changes.

**Likely surfaces:** `settings.gradle.kts`, version catalog, module build files, current
`core/model`, app dependencies, and JVM tests.

**Done when:** phone behavior is unchanged, shared contracts compile without Android/Room, and
both phone and future watch code consume one model definition.

### 22. Add opt-in Health Connect export and precise set timing

**Status:** Not started.

**Depends on:** Packages 1, 2, and 21 plus re-verification of current Health Connect APIs.

**Implementation tasks:**

1. Define a gateway abstraction for availability, minimum write permissions, and idempotent
   completed-session export.
2. Map committed WallCrawl sessions to supported strength-session records; keep Room
   authoritative and do not import external records as WallCrawl history.
3. Persist export preference, client record ID/version, last exported revision, and visible
   retry state.
4. Capture segment-quality start/completion timing in WallCrawl when the product needs it,
   preserving honest nulls for older history.
5. Make provider absence, denial, revocation, and export failure non-blocking.
6. Add separate controls for disabling future export and deleting WallCrawl-exported Health
   Connect records; integrate delete-all-local-data so it offers external deletion before
   local record IDs are removed and reports when revoked permission prevents cleanup.
7. Document permissions, data use, Play declarations, retention/deletion behavior, and
   unsupported detail.

**Likely surfaces:** version catalog/app dependencies, manifest, a `core/health` boundary,
profile integration UI, session/set persistence, and unit/instrumentation tests.

**Done when:** export is explicit, idempotent, least-privilege, retryable, and cannot roll back
or block a completed local workout; disabling and deleting are distinct, test-covered actions,
and local deletion never falsely claims external records were removed.

### 23. Define the versioned companion protocol and run a Wear spike

**Status:** Not started.

**Depends on:** Package 21 and current dependency/API verification.

**Implementation tasks:**

1. Define compact immutable plan snapshots, append-only session events, acknowledgements,
   version negotiation, and a deterministic reducer.
2. Reject unsupported major versions, duplicate IDs, sequence gaps, stale revisions, and
   conflicting events with structured reasons.
3. Add stable device/node identity and enforce the first-release ownership policy: one active
   execution owner, with extra paired watches explicitly rejected or read-only.
4. Transfer only the active session, previous comparable values, concise approved cues, rest
   targets, and visual hashes.
5. Spike Data Layer delivery, reconnect behavior, foreground/ongoing timers, ambient mode, and
   process restoration on representative watches.
6. Benchmark SVG, PNG, WebP, and static-only visual options before choosing an animation
   format.
7. Record latency, memory, battery, rendering, transport, and accessibility evidence.

**Likely surfaces:** `:companion-protocol`, a minimal `:wear` spike module, Data Layer adapter,
timer service, visual prototype, tests, and spike report.

**Done when:** protocol replay is deterministic, disconnect/reconnect and additional-paired-watch
semantics are proven, and the smallest viable watch path has measured limits rather than
assumed ones.

### 24. Build phone coordination and a durable watch journal

**Status:** Not started.

**Depends on:** Packages 2, 4, 10, 21, and 23.

**Implementation tasks:**

1. Publish a compact validated active-session snapshot from the phone behind a transport
   interface.
2. Persist the mirrored plan, protocol version, pending events, acknowledgements, conflicts,
   and visual-cache metadata on the watch.
3. Journal every watch mutation durably before reporting success in the UI.
4. Reconcile transactionally on the phone, deduplicate event IDs, enforce revision/sequence
   rules, and acknowledge only durable canonical writes.
5. Republish state after start, resume, substitution, and reconciliation without sending timer
   ticks.
6. Expose conflicts and stale-session recovery instead of silently overwriting either device.

**Likely surfaces:** phone `core/wear` coordinator and listener, companion reducer/codecs, watch
session store/journal/repository, Data Layer service, Room/DataStore, and replay tests.

**Done when:** repeated or reordered delivery changes canonical history exactly once, pending
events survive reboot, and every acknowledged event is durable on both sides.

### 25. Build the watch workout experience

**Status:** Not started.

**Depends on:** Packages 23-24.

**Implementation tasks:**

1. Add glanceable active-set, rest, exercise navigation, finish, and conflict states for small
   screens and rotary input.
2. Run watch-owned monotonic timers in an appropriate foreground/ongoing-activity lifecycle.
3. Support all type-aware set shapes without inventing values or requiring sensor data.
4. Add approved substitutions supplied by the phone; the watch never expands eligibility.
5. Cache and render only the visual format that passed the spike, with static fallback.
6. Test ambient mode, process recreation, notification re-entry, touch targets, TalkBack, and
   interruption handling.

**Likely surfaces:** Wear Compose app/session UI, timer/foreground service, ongoing activity,
visual cache, companion events, and watch tests.

**Done when:** a user can complete every supported local session shape from the watch, timers
remain accurate, and animation or sensors are never required to log work.

### 26. Prove disconnected execution and recovery

**Status:** Not started.

**Depends on:** Packages 24-25.

**Implementation tasks:**

1. Complete workouts with the phone unreachable and retain every event locally.
2. Reconnect after process death, watch reboot, phone replacement/restart, duplicated delivery,
   partial acknowledgement, and the presence of an additional paired watch.
3. Reconcile without missing or duplicate sets and expose any irreconcilable conflict.
4. Add resume surfaces on both devices and local privacy controls for mirrored data.
5. Run destructive network/interruption scenarios as automated and manual release fixtures.

**Likely surfaces:** protocol reducer, phone/watch repositories, connection state, resume UI,
privacy controls, and cross-device test harness.

**Done when:** the same event log always reduces to the same canonical session, offline work
survives recovery, and users can inspect and resolve rather than silently lose conflicts.

### 27. Add optional sensors and cross-device release gates

**Status:** Not started.

**Depends on:** Package 26 and current Health Services verification.

**Implementation tasks:**

1. Keep sensor capture behind an optional gateway and least-privilege permission flow.
2. Record only measured supported values; never synthesize calories, VO2 max, readiness,
   recovery, diagnosis, or RPE.
3. Keep sensor absence, denial, and failure independent from set logging and timers.
4. Add protocol-compatibility, phone/watch migration, disconnection, battery, thermal,
   accessibility, and permission matrices to CI/manual release evidence.
5. Publish disclosures that distinguish Bluetooth, Wi-Fi, and platform-routed Data Layer
   behavior.

**Likely surfaces:** watch health gateway, permissions UI, companion protocol extensions,
release workflows, device matrix, and privacy documentation.

**Done when:** the complete phone/watch experience passes disconnected and version-skew tests,
optional sensors add measured context without inventing health data, and denial never blocks a
workout.

### 28. Evaluate optional encrypted sync

**Status:** Not started and not authorized for implementation.

**Depends on:** Package 2 and an approved privacy/conflict-resolution design.

**Implementation tasks:**

1. Define the user problem that explicit export/import and device transfer do not solve.
2. Choose identity, encryption-key ownership/recovery, metadata minimization, deletion,
   retention, and threat-model requirements before selecting infrastructure.
3. Define deterministic conflict resolution for profile, templates, immutable sessions,
   program state, and cross-device events.
4. Prove offline-first operation, account deletion, key loss, rollback, and version-skew
   behavior in a reviewed design.
5. Estimate ongoing storage, compute, support, privacy, and operational cost before approving
   implementation.

**Likely surfaces:** design and threat model first; no network dependency or account code should
be added during evaluation.

**Done when:** a reviewed design demonstrates a user benefit beyond local export/device
transfer, preserves fully offline core operation, and explicitly authorizes or rejects an
implementation phase.

## Supporting implementation evidence

- [Architecture](docs/architecture.md)
- [Custom workout behavior](docs/custom-workouts.md)
- [Reviewed metadata sign-off](docs/reviewed-exercise-metadata-human-signoff.md)
- [Planner evaluation contract](docs/planner-evaluation.md)
- [Reviewed capability eligibility](docs/reviewed-capability-eligibility.md)
- [Weekly dose ledger](docs/weekly-dose-ledger.md)
- [Timed-hold programming](docs/timed-hold-programming.md)

## Updating this roadmap

Update status from repository evidence, not unchecked historical plan steps. When work ships:

1. Update the verified status table and affected package status.
2. Remove or rewrite completed implementation tasks rather than accumulating checked boxes.
3. Link the merged commit or pull request when it materially helps future audits.
4. Preserve completed detailed plans as decision and execution history.
5. Add work only when its dependency order, product decision, privacy impact, and release gate
   are understood.
6. Keep exact filenames out of unresolved designs; use stable package or contract boundaries
   until a reviewed design chooses the persistence/UI shape.
