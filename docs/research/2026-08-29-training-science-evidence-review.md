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
11. Optional body measurements may be stored locally for display/future research, but v1 deterministic and LLM engines do not consume them.
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
