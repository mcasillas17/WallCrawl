# Training Science Evidence Review for WallCrawl

## Method

Four research agents (Claude Opus 4.8, Grok 4.6, Gemini 3.7 Flash, and GPT-5.6 Terra) completed four rounds:

1. independent searches across resistance-training dose, inclusive/body-aware planning, equipment/exercise selection, and adaptive programming;
2. adversarial citation and claim review;
3. architecture and implementation convergence;
4. explicit sign-off on twelve canonical v1 decisions.

Priority was given to 2018-2026 position stands, systematic reviews, meta-analyses, umbrella reviews, and high-quality trials. Older sources were retained only where still foundational. Blogs were not evidence. Every policy below distinguishes population-level evidence from WallCrawl product policy.

The full per-agent, per-round record — distinct Round-1 findings, Round-2 citation and claim corrections, Round-3 blueprint contributions, Round-4 retractions and sign-off, and the deduplicated union bibliography with VERIFIED/CORRECTED/DUPLICATE/REJECTED/UNVERIFIED labels — is preserved in `docs/research/2026-08-29-roundtable-agent-findings.md`.

## Related Documents

- Detailed engine architecture: `docs/superpowers/specs/2026-08-29-science-based-workout-engines-design.md`.
- Deterministic engine implementation plan: `docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md`.
- Local LLM engine implementation plan: `docs/superpowers/plans/2026-08-29-science-based-local-llm-engine.md`.
- Body-aware personalization design: `docs/superpowers/specs/2026-08-29-body-aware-personalization-design.md`.
- Body-aware personalization plan: `docs/superpowers/plans/2026-08-29-body-aware-personalization.md`.
- Roundtable agent findings appendix: `docs/research/2026-08-29-roundtable-agent-findings.md`.

## Corrected Citation Ledger

The cross-review corrected material citation errors:

- Currier 2023 NMA: DOI `10.1136/bjsports-2023-106807`, PMID `37414459`. The reported `bjsports-2022-106160` identifier is not this paper.
- Schoenfeld low- versus high-load review: DOI `10.1519/JSC.0000000000002200`, PMID `28834797`.
- Refalo acute fatigue trial: DOI `10.1186/s40798-023-00554-y`, PMID `36752989`. It is not the systematic review.
- Refalo review: DOI `10.1007/s40279-022-01784-y`, PMID `36334240`.
- Wolf ROM review: cite peer-reviewed DOI `10.47206/ijsc.v3i1.182`; do not double count a SportRxiv preprint.
- Kassiano ROM review (PMID `36662126`) and gastrocnemius trial are different studies.
- Baz-Valle exercise-variation trial: DOI `10.1371/journal.pone.0226989`, not `...0226981`.

## Consensus Doctrine

The doctrine and signed decisions below preserve the original review record. The
[2026-09-05 clarification](#validation-scope-clarification-2026-09-05) separates their
research, product-policy, and software meanings and corrects any implication that planned
editable guidance is already a shipped control.

| Rule | Confidence | Engine consequence |
| --- | --- | --- |
| Consistent, feasible resistance training precedes optimization. | High | Prefer plans users can complete; complexity is not a quality metric. |
| Weekly direct-primary muscle exposure is the dose ledger; session totals are duration/tolerance guardrails. | Moderate-high | Add a multi-session program horizon before sophisticated progression. |
| Frequency mainly distributes weekly dose when volume is equated. | High | Schedule around availability; do not claim frequency independently causes hypertrophy. |
| Heavy, specific loading better serves maximal-strength outcomes; hypertrophy supports broad loads with sufficient effort. | High | Couple dose to goal while preserving equipment/capability alternatives. |
| Failure is not required and increases acute fatigue. | Moderate-high | Never auto-default to failure. Use editable RIR guidance. |
| RPE/RIR and performance are fallible adaptation inputs; missing values remain missing. | Moderate | Log them, but never infer readiness from absence. |
| Full, comfortable ROM is the default; long-length partials are muscle-specific options. | Moderate-low | No universal lengthened-partial ranking bonus. |
| Machines and free weights can produce similar hypertrophy in limited evidence; strength remains modality-specific. Bands have strength evidence, not universal hypertrophy equivalence. | Moderate | Equipment substitutions must be goal- and outcome-specific. |
| Concurrent aerobic and resistance training generally preserves hypertrophy/maximal strength; same-session work may modestly affect explosive outcomes. | High | Today may remain RT, but program-level health copy must not imply RT is complete fitness. |
| BMI/body mass does not determine exercise capability. | High ethical/product decision | v1 planner and LLM do not consume BMI/body mass. |
| Explicit constraints and `AVOID` are hard; `LIMITED`/`UNKNOWN` are conservative soft inputs. | Product-safety policy | Capability-first selection, with history relaxing only soft penalties. |
| Deloads are user-controlled offers, not diagnoses or calendar laws. | Low direct evidence | Use transparent multi-session signals and user acceptance. |
| Deterministic code owns safety, eligibility, dose, progression, validation, persistence, and fallback. | High engineering/governance | LLM remains bounded and optional. |

## Twelve Signed V1 Decisions

Historical record: "editable" includes planned controls, not a claim of shipped settings.
See the [2026-09-05 clarification](#validation-scope-clarification-2026-09-05).

1. `PRIMARY_ONLY_V1`: one completed work set credits one designated direct-primary muscle; secondary involvement is descriptive only.
2. Volume uses state-based, editable policy ranges. There is no mandatory scientific floor or evidence-labeled automatic increment.
3. Guidance defaults to 2-4 RIR in INITIATE/RETURNING or for a relevant `LIMITED` capability; established general/hypertrophy guidance defaults to 1-3 RIR; null remains null; failure is never automatic.
4. Rest uses editable `SHORT`, `MODERATE`, and `LONG` classes resolved by versioned product policy.
5. Experience is ordinarily a soft complexity input. Uncalibrated/returning advanced-complexity work has a temporary ceiling unless demonstrated history or a supported regression exists.
6. Two comparable completed sessions plus explicit user confirmation may relax a soft capability penalty. This is a reproducibility policy, not physiology.
7. Today remains an RT-session planner. Program state may record/surface user-selected aerobic activity and public-health education, but v1 does not prescribe obesity treatment or infer recovery from Health Connect.
8. `DeloadOffer` is user-requested, return-driven, or based on a transparent multi-session pattern; it has no fixed calendar, percentage, RIR, volume, or diagnostic threshold.
9. V1 metadata is categorical and actionable: direct primary, descriptive secondary, pattern, complexity, progression family, approved regressions/substitutions, capability requirements, support, impact, equipment, and provenance.
10. LLM reranking remains disabled until deterministic completion, perfect hard-constraint/fallback tests, strict schema, expert no-worse review, opt-in human benefit, and device-specific benchmarks.
11. Body measurements are deferred and not required by the capability roadmap; the capability-input milestone stores no weight, height, or BMI, and deterministic and LLM engines do not consume them.
12. Automatic plans use reviewed metadata only; browse/manual workflows retain the full catalog.

## Rejected Claims

- exact universal weekly set floors or ceilings;
- fixed set-progression percentages;
- fixed calendar deloads or 50% reductions;
- numeric SFR, axial-load, joint-stress, fatigue, or injury-risk scores;
- universal bands/bodyweight hypertrophy equivalence;
- universal long-length partial superiority;
- BMI/body-mass exercise ranking;
- automatic Health Connect cardio-to-fatigue inference;
- scientific justification for one model timeout or fixture count;
- any claim that WallCrawl prevents injury or diagnoses recovery.

## Canonical Sources

1. Currier BS et al. ACSM Position Stand. *Med Sci Sports Exerc.* 2026;58:851-872. DOI `10.1249/MSS.0000000000003897`; PMID `41843416`. Healthy-adult umbrella evidence.
2. Currier BS et al. *Br J Sports Med.* 2023;57:1211-1220. DOI `10.1136/bjsports-2023-106807`; PMID `37414459`. Supervised healthy adults.
3. Pelland JC et al. *Sports Med.* 2025. DOI `10.1007/s40279-025-02344-w`; PMID `41343037`. Mostly young/male dose-response evidence.
4. Schoenfeld BJ et al. *J Sports Sci.* 2017;35:1073-1082. DOI `10.1080/02640414.2016.1210197`; PMID `27433992`.
5. Schoenfeld BJ et al. *J Sports Sci.* 2019;37:1286-1295. DOI `10.1080/02640414.2018.1555906`; PMID `30558493`.
6. Schoenfeld BJ et al. *J Strength Cond Res.* 2017;31:3508-3523. DOI `10.1519/JSC.0000000000002200`; PMID `28834797`.
7. Hickmott LM et al. *Sports Med Open.* 2022;8:9. DOI `10.1186/s40798-021-00404-9`.
8. Robinson ZP et al. *Sports Med.* 2024;54:2209-2231. DOI `10.1007/s40279-024-02069-2`; PMID `38970765`.
9. Refalo MC et al. *Sports Med Open.* 2023;9:10. DOI `10.1186/s40798-023-00554-y`; PMID `36752989`.
10. Refalo MC et al. *Sports Med.* 2023. DOI `10.1007/s40279-022-01784-y`; PMID `36334240`.
11. Schumann M et al. *Sports Med.* 2022;52:601-612. DOI `10.1007/s40279-021-01587-7`; PMID `34757594`.
12. Haugen ME et al. *BMC Sports Sci Med Rehabil.* 2023;15:103. DOI `10.1186/s13102-023-00713-4`.
13. Lopes JSS et al. *SAGE Open Med.* 2019;7. DOI `10.1177/2050312119831116`; PMID `30815258`.
14. Wolf M et al. *Int J Strength Cond.* 2023. DOI `10.47206/ijsc.v3i1.182`.
15. Kassiano W et al. *J Strength Cond Res.* 2023;37:1135-1144. DOI `10.1519/JSC.0000000000004415`; PMID `36662126`.
16. Bull FC et al. *Br J Sports Med.* 2020;54:1451-1462. DOI `10.1136/bjsports-2020-102955`; PMID `33239350`.
17. Jakicic JM et al. *Med Sci Sports Exerc.* 2024. DOI `10.1249/MSS.0000000000003520`; PMID `39277776`.
18. Lopez P et al. *Obes Rev.* 2022;23:e13428. DOI `10.1111/obr.13428`; PMID `35191588`.
19. Fragala MS et al. *J Strength Cond Res.* 2019;33:2019-2052. DOI `10.1519/JSC.0000000000003230`; PMID `31343601`.
20. Lekadir K et al. FUTURE-AI. *BMJ.* 2025;388:e081554. DOI `10.1136/bmj-2024-081554`. Governance only.
21. NIST AI RMF 1.0 and GenAI Profile. DOI `10.6028/NIST.AI.100-1`; `10.6028/NIST.AI.600-1`. Governance only.

## Research Backlog That Does Not Block V1

- fractional secondary-muscle set accounting;
- validated body-mass mechanics for recommendation ranking;
- direct algorithmic deload and return-from-break trials;
- RIR calibration in unsupervised beginners;
- bodyweight/band hypertrophy by progression family;
- muscle-specific ROM evidence;
- prospective LLM-versus-deterministic workout-planner trials;
- independent expert validation of capability and substitution metadata.

## Timed programming implementation boundary (2026-09-02)

The [timed programming milestone](../timed-hold-programming.md) adds descriptive,
AI-authored legacy metadata for 14 duration exercises. It introduces no new efficacy
evidence or physiological thresholds. Legacy difficulty, mechanics, and fatigue values
are product ranking labels; the numeric fatigue value is not a validated biological
measurement and is not promoted into the reviewed scientific contract. Duration defaults
remain product policy. Coaching describes setup/movement, and alternative IDs are catalog
references without an equivalence claim or automatic substitution behavior. Human review
and production gate enablement remain separate work.

## Validation scope clarification (2026-09-05)

This clarification governs research claims and rule classification for
[ROADMAP Package 4](../../ROADMAP.md#4-add-whole-program-validation) and directly related
ranking, substitution, and evaluation guidance. The roadmap still owns implementation
priorities. It does not turn a proposed rule into scientific evidence. Original research
records above and in the roundtable appendix remain historical; a citation, reviewer vote,
or passing software test does not scientifically or clinically validate WallCrawl's
complete algorithm.

### Verified source scope

Sources were checked on 2026-09-05. PubMed's web pages were unavailable to this retrieval;
identifiers, abstracts, and reported results were confirmed through Europe PMC's indexed
records. Full-text access below identifies the sections additionally inspected; it does
not mean an independent reanalysis of data or supplementary material.

| Key | Citation and access | Supported claim, population, and limits |
| --- | --- | --- |
| R1 | Currier et al. 2023, PMID [37414459](https://europepmc.org/article/MED/37414459), DOI [10.1136/bjsports-2023-106807](https://doi.org/10.1136/bjsports-2023-106807). [Full-text inspection](https://www.ebi.ac.uk/europepmc/webservices/rest/PMC10579494/fullTextXML): eligibility and limitations; results confirmed from abstract. | Network meta-analysis of supervised resistance-training trials in healthy adults, at least six weeks: 178 studies / 5,097 participants for strength and 119 / 3,364 for hypertrophy. Multiple prescriptions improved outcomes versus no exercise; higher loads favored strength and multiple sets featured in higher-ranked hypertrophy prescriptions. Categorical load/set/frequency comparisons, risk-of-bias concerns, and exclusion of unsupervised training, athletes/military populations, and chronic disease limit individual/application-level inference. It does not select one universal individual prescription or WallCrawl's exact allowances, RIR, or rest seconds. |
| R2 | Schoenfeld et al. 2019, PMID [30558493](https://europepmc.org/article/MED/30558493), DOI [10.1080/02640414.2018.1555906](https://doi.org/10.1080/02640414.2018.1555906). **Abstract-only confirmation** via the indexed record; full text not reviewed for this clarification. | Meta-analysis of 25 experimental training-frequency studies found no significant/meaningful hypertrophy difference when volume was equated; the abstract also reports a resistance-trained subgroup. Full participant demographics and study-specific exclusions were not confirmed here. Supports distributing a given volume around preference; does not establish a mandatory frequency, recovery interval, or readiness test. |
| R3 | Refalo et al. 2023, PMID [36752989](https://europepmc.org/article/MED/36752989), DOI [10.1186/s40798-023-00554-y](https://doi.org/10.1186/s40798-023-00554-y). [Full-text inspection](https://www.ebi.ac.uk/europepmc/webservices/rest/PMC9908800/fullTextXML): subjects, conclusions, and limitations; protocol/results also confirmed from abstract. | Randomized crossover in 24 trained adults (12 men, 12 women; eligibility 18-40 years, at least three years of training, no existing musculoskeletal injury/neuromuscular disorder). Six barbell bench-press sets at 75% 1-RM to failure, 1-RIR, or 3-RIR; lifting velocity and perceptual responses were assessed acutely and at 24/48 hours. Closer proximity to failure increased measured acute fatigue. Single-exercise/measurement specificity and uncertain subjective RIR accuracy limit generalization. Neither its recovery observations nor sex comparisons validate a whole-body score, an individual recovery timer, or general gender-based programming rules. It did not test long-term hypertrophy. |

### Evidence-to-rule mapping

**Research-informed principle** means a bounded interpretation of population evidence,
not a numeric validator threshold. **Product policy** means a named, versioned WallCrawl
choice. **Software invariant** means internal integrity, consistency, or persistence
correctness. Population limitations are R1-R3 above where cited; for policy/invariant
rows without a citation, population applicability is **not applicable**: their rationale
is engineering/product design, not a human-study result.

The rows below are the proposed validation contract, not a claim that whole-program
validation is implemented. A hard failure may enforce an explicit invariant or configured
policy, never an inferred biological condition.

| Rule and purpose | Classification and source/rationale | May enforce | Must not infer or claim | Open implementation-contract decision |
| --- | --- | --- | --- | --- |
| Identifier and candidate membership: keep output inside the actual legal input set. | Software invariant; existing `GeneratedWorkoutValidator` and generation context. | Exact catalog IDs, allowed-set membership, nonempty/name/type-valid recommendations. | Catalog existence means approval or clinical appropriateness. | Carry exact generation-context identity through whole-program validation and revalidation at start. |
| Reviewed metadata/provenance: use only the reviewed contract on its enabled path. | Software invariant; importer/parser plus `ExerciseEligibilityPolicy` and `StateBasedTrainingPolicy`. | `APPROVED` status, required provenance, matching schema/policy/type and candidate eligibility in reviewed mode. | A merge, AI authorship, or validator can grant human approval; reviewed rules silently apply to legacy/manual paths. | Define the complete version bundle and mismatch failures; keep the production gate disabled. |
| Explicit constraints and eligibility: honor declared limits. | Product policy; equipment, exclusions, `TrainingConstraint`, capability `AVOID`, low-impact rule, and existing temporary advanced-complexity ceiling. | Recheck the same enabled-path eligibility contract; never weaken it in repair. | A legal candidate prevents injury, diagnoses capability, or is medically safe; experience alone permanently bans complex work. | Reuse eligibility decisions without broadening legacy behavior; widening adaptation states belongs to separate work. |
| Prescription structure: reject malformed targets. | Software invariant; `ExercisePrescription` and typed outcome contracts. | Positive/bounded sets, ordered rep ranges, finite values, type-specific required/forbidden fields, nullable effort/rest source consistency. | Representational bounds are human tolerances; missing load or effort is zero or favorable evidence. | Reuse existing constructor rules and define structured aggregate error categories, not new physiological bounds. |
| Weekly attribution: keep accounting reproducible. | Product policy; `PRIMARY_ONLY_V1` deliberately credits one designated direct primary per completed work set; secondary involvement is descriptive. | Reconstruct completed history using the existing ISO-week/time-zone and omission rules; preserve policy/catalog/review identity. | This is a complete measurement of muscular stimulus, or secondary work has no physiological effect. | Define prospective attribution of proposal targets separately from completed-dose credit; never mutate history or credit unfinished work as completed. |
| Set caps and aggregate weekly allowance: avoid spending the same configured remainder repeatedly. | Product policy; `STATE_BASED_DOSE_EFFORT_REST_V1` contains the exact allowances and per-exercise caps. R1 informs flexible training design, **not these numbers**. | Check the whole proposal against configured remaining allowance, including exercises sharing a direct primary; preserve the policy's no-increase-over-base behavior. | Exceeding an allowance proves overload/medical danger; under-target exposure requires a scientific minimum, warning, or automatic volume increase. | Define proposal/program horizon, already-planned reservations, completed/open work during substitution, and week-boundary behavior before implementation. No mandatory weekly floor. |
| Ledger integrity: prevent corrupt or duplicated accounting. | Software invariant; `WeeklyDoseLedgerCalculator` and its storage codec. | Reject duplicate record identities, invalid counts, incompatible versions, or representational overflow; keep omissions explicit and completed history authoritative. | Storage bounds such as 50,000 work sets are physiological ceilings; omitted attribution means no work occurred. | Distinguish malformed ledger/arithmetic failures from a configured allowance being exceeded in typed reasons. |
| Duplicate exercise/family: honor intentional program structure. | Product policy; avoid accidental repetition only where the session/program contract disallows it. | A declared exercise/family uniqueness rule within its named scope. | Repeated movements or families are inherently harmful, or must be unique across a whole week. | Specify session versus program scope, permitted repetitions, and family identity; distinguish exercise IDs from duplicated persistence record IDs. |
| Movement coverage: satisfy the intended session/program. | Product policy; coverage follows the selected split/program and available eligible pool. | Explicit required patterns within the declared scope, with a defined insufficient-candidate outcome. | Every workout must cover every pattern, or a missing pattern is a medical defect. | Define required/optional patterns, horizon, and feasibility/failure semantics before making coverage blocking. Current pattern spreading is a ranking preference. |
| Load provenance: prevent fabricated starting loads. | Software invariant; confirmation/history-only load construction and unit-aware history. | Trace load/assistance to the applicable source and policy; preserve null when no valid source exists. | Body size, a score, or metadata approval authorizes an invented starting load; provenance proves safety. | Record source, unit, and policy needed for replay. Preserve the existing history-derived load bump on the legacy path; this task does not replace progression or require equality to the last load. |
| Duration consistency: make an estimate agree with the proposal and request contract. | Software invariant for arithmetic/field consistency; product policy for estimator assumptions and allowed deviation from requested time. | Recompute under a named estimator and apply only a defined tolerance; retain structural duration bounds. | Exact completion time is guaranteed, or duration thresholds are physiological limits. | Define execution/rest/transition assumptions, rounding, and tolerance; current estimated minutes are not a promise of requested-duration equality. |
| Effort/rest guidance: preserve configured guidance and explicit preference provenance. | Product policy; exact RIR bands and rest seconds belong to `STATE_BASED_DOSE_EFFORT_REST_V1`, not R1 or R3. | Consistent nullable guidance, no automatic 0-RIR/failure target, and valid stored rest preference precedence. | The exact bands/seconds are universally optimal or clinically protective; missing effort establishes readiness. | Distinguish automatic guidance from logged outcomes/user edits; design future editing controls separately from the existing model/persistence support. |
| Proximity-to-failure interpretation: limit claims made about guidance. | Research-informed principle; R3's measured acute bench-press outcomes, with its population/protocol limits. | Constrain explanation wording; product policy may choose not to default to failure. | Sum `programming.fatigueScore` into a physiological budget; extrapolate to whole-body fatigue, universal recovery time, long-term gains, or gender-specific prescriptions. | No numeric physiological fatigue validator in v1; no substitute score under another name. |
| Recency/frequency: distribute work by an explicit scheduling preference, if designed. | Research-informed principle (R2); any ranking rule/lookback/precedence is a separate product policy. | At most a defined, explainable scheduling preference; existing explicit constraints still apply. | Timestamp-only overload, readiness, a universal recovery interval, or Health Connect fatigue/recovery inference. | No scheduling contract exists yet. Define attribution, lookback, precedence, and sparse-history handling before use; do not invent a blocking recency rule for Package 4. |
| Structured failure and bounded repair: stop invalid output without silently relaxing rules. | Software invariant; deterministic failure/repair boundary. | Typed reasons, at most one deterministic repair, unchanged explicit constraints and legal candidate set; fail closed if still invalid. | A successful repair or failure category is a health assessment; repeated retries may expand eligibility. | Define reason ordering, which policy mismatches are repairable, and typed no-plan propagation through planner/Today. |
| Snapshot/versioning and persistence: make decisions replayable and writes atomic. | Software invariant; immutable recommendation/session boundary. | Record context, validator/catalog/review/policy/ledger versions, reasons and outcome; validate before UI/persistence and prevent partial active sessions. | A hash, test pass, or recorded approval validates the complete algorithm scientifically. | Choose columns versus a dedicated table, sufficient replay inputs, and stale-context revalidation/transaction boundaries without rewriting completed history. |

### Current behavior and scope limits

At `22531bc`, `GeneratedWorkoutValidator` checks IDs, optional allowed membership, catalog
type, name, nonempty exercises, and structural duration bounds; prescription constructors
enforce field structure. It is not an aggregate program validator. `TodayViewModel`
validates before showing a recommendation and again before starting it, but that does not
yet validate the full generation context or aggregate dose.

`StateBasedTrainingPolicy` caps each prescription against the same supplied completed
ledger; it does not reserve allowance across all exercises in a proposal. Its exact
weekly allowances (6/8/12), per-exercise caps (2/4), relevant-`LIMITED` cap (2), RIR bands
(2-4, 1-2, 1-3), and rest defaults (60/90/180 seconds) are
[versioned product choices](../superpowers/specs/2026-09-01-state-based-dose-effort-rest-design.md#dose-policy).
They are configurable through policy construction, **not shipped user-editable policy
settings**. The model and persistence support explicit `UserRestPreference` values; the
current active timer's add/skip actions do not save such a preference. Logging actual
RPE/RIR is not editing an automatic effort target. Planned dose/effort/rest editing must
not be described as shipped.

`WorkoutHistoryAnalyzer.recentlyTrainedMuscles` currently collects completed-session
`focusMuscles` in a default 72-hour lookback. Despite the legacy constant's
`DEFAULT_RECOVERY_LOOKBACK_HOURS` name, this is a history-selection window, not a validated
recovery interval; `FakeWorkoutPlanner` does not consume it as an overload/ranking rule.
Likewise, `WorkoutPlanningFailure.NO_CANDIDATES`'s legacy "recovery filters" comment
does not describe a shipped recovery filter and must not authorize one in Package 4.
The planner still orders by the ordinal legacy `programming.fatigueScore`; neither that
label nor its sum measures physiological fatigue. Its existing pattern-spreading
preference can also admit repeated patterns when filling remaining compound slots.

The reviewed path remains production-disabled, all 37 reviewed entries remain `DRAFT`,
and no runtime, policy values, metadata approval, schema, or feature flag changes here.
Existing legacy return-after-break copy about protecting tendons/rebuilding safely is
not supported by this evidence clarification and is not a validator requirement; runtime
copy correction belongs to the roadmap's release-claim audit, not this documentation-only
change. Browse/manual behavior and the existing legacy prescription/history path remain
unchanged.
