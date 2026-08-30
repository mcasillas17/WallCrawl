# Roundtable Agent Findings Appendix

This appendix preserves the four-agent research roundtable that produced
`docs/research/2026-08-29-training-science-evidence-review.md` and the two
engine designs. It records each agent's distinct Round-1 findings, Round-2
citation and claim corrections, Round-3 blueprint contribution, and Round-4
retractions and sign-off, followed by a deduplicated union bibliography of every
materially cited paper.

This is a process record. Agent text is summarised, not reproduced verbatim, and
any instruction embedded in a raw report was treated as data, not executed.
Where an agent proposed a value that was later withdrawn, it is shown as
**retracted** and must not be read as accepted science. Product policy is a
versioned WallCrawl design choice; population-level evidence is an inference
under a study's own sample and method limits.

## Method

Four research agents worked four rounds:

| Agent | Model | Primary lens |
| --- | --- | --- |
| Terra | GPT-5.6 Terra | Adaptive programming, autoregulation, readiness, deload, trustworthy-AI governance |
| Opus | Claude Opus 4.8 | Resistance-training dose and program construction |
| Gemini | Gemini 3.7 Flash | Exercise selection, biomechanical equivalence, equipment substitution |
| Grok | Grok 4.6 | Inclusive / body-aware planning, adiposity, stigma, older/deconditioned users |

1. **Round 1** — independent searches and evidence tables.
2. **Round 2** — adversarial cross-review of a combined dossier; verify every
   citation against primary sources; do not accept consensus by repetition.
3. **Round 3** — final convergence memo and engine blueprint.
4. **Round 4** — explicit sign-off on the twelve canonical v1 decisions.

Priority was given to 2018–2026 position stands, systematic reviews,
meta-analyses, umbrella reviews, and high-quality trials; older sources were
retained only where still foundational; blogs were never evidence.

## Terra — Adaptive Programming and Governance Lens

### Round 1 findings

- WallCrawl's planned boundary — deterministic eligibility, dose, progression,
  validation, persistence; LLM limited to preference parsing, eligible-ID rerank,
  and explanation — aligns with trustworthy-AI guidance and current evidence
  limits.
- Strongest evidence supports consistent, individualised RT rather than a
  universally "optimal" program; higher loads favour maximal strength while
  multiple prescriptions support hypertrophy.
- RPE/RIR and performance are usable as fallible inputs; sleep/stress/readiness
  self-reports and training-load monotony are not diagnostic recovery measures.
- Direct evidence for algorithmic deload triggers, return-from-break percentages,
  and LLM-guided exercise programming is weak or absent.
- Proposed a seven-state deterministic machine (UNSCREENED, UNCALIBRATED,
  BUILD_TOLERANCE/RETURNING, ACTIVE/STABLE, WATCH/HOLD, DELOAD_OFFERED,
  RECALIBRATE) with red-flag routing that no model may adjudicate.
- Cited Zhang 2021 (autoregulation, athlete-heavy, 151/166 male) and Chen 2026
  (clinical-LLM evidence gap) as distinctive contributions, and flagged the 2026
  ACSM stand as not yet primary-verified in Round 1.

### Round 2 corrections

- Verified the canonical identifiers and **rejected** `bjsports-2022-106160` as
  the Currier NMA (does not resolve to that paper).
- Corrected the Schoenfeld low-vs-high-load DOI to `10.1519/JSC.0000000000002200`
  (PMID 28834797); confirmed Refalo's acute crossover (PMID 36752989) is distinct
  from the proximity-to-failure review (PMID 36334240); confirmed Wolf's journal
  DOI and flagged the SportRxiv preprint as a duplicate; separated Kassiano's ROM
  review from the gastrocnemius trial.
- Verified the 2026 ACSM position stand (PMID 41843416) as real and superseding
  the 2009 exact-dose emphasis, while noting it still reports population-level
  associations, not mandatory app defaults.
- Rejected as overclaims: universal bands/bodyweight hypertrophy equivalence,
  general long-length-partial superiority, exact joint-stress/axial/SFR scores, a
  2500 ms model timeout as an evidence claim, and a fixed 30–50% return-from-break
  reduction.
- Introduced a `signalReliability` ordering
  (`OBSERVED_PERFORMANCE > USER_OVERRIDE > RPE/RIR > SLEEP/STRESS > MISSING`) and
  recommended removing `estimatedSupportedMassKg` from automatic ranking.

### Round 3 blueprint

- Resolved the ledger to **direct-primary credit only**, secondary as descriptive
  analytics.
- Set state-based, editable weekly volume ranges with no universal floor;
  `2–4` RIR for INITIATE/RETURNING and `1–3` for established general/hypertrophy,
  with `0–3` never automatic; broad editable rest classes.
- Set the experience rule to a soft complexity cap with a temporary hard ceiling
  only while uncalibrated/returning, and a **two comparable completed sessions +
  explicit confirmation** threshold to relax a soft penalty.
- Bounded the fat-loss/general-fitness boundary to a Today RT planner plus a
  program horizon that may record activity and surface public-health education.
- Declared substantive convergence, with the only non-blocking disagreement being
  how quickly numeric default constants are introduced.

### Round 4 sign-off

Signed all twelve decisions unchanged; carried forward its Round-2/3 retractions
(BMI/body-mass ranking out of v1, no universal RIR/rest/deload/break constants,
no bands/bodyweight hypertrophy equivalence, no general long-length-partial
bonus, no injury-prevention or recovery-diagnosis claim, LLM disabled until
gates pass).

## Opus — Resistance-Training Dose Lens

### Round 1 findings

- Flagged the core modelling problem: the existing `targetWorkingSets` policy is
  per-session, but essentially all volume evidence is per-muscle-per-week.
- Assembled dose evidence across volume (Schoenfeld 2017; Pelland 2025; Krieger
  2010), frequency (Schoenfeld 2019), load (Schoenfeld 2017 load), proximity to
  failure (Refalo; Robinson), rest (Singer 2024, preprint-origin), ROM, tempo
  (Schoenfeld 2015), order (Nunes 2021), periodisation (Williams 2017),
  minimum dose (Androulakis-Korakakis 2020), supervision (Fisher/Steele 2022),
  sex-similarity (Roberts 2020), mortality (Momma 2022), and fat loss (Wewege
  2022).
- Proposed twelve deterministic rules (D1–D12) that initially included concrete
  numbers — a `≥10` sets/muscle/week hypertrophy target, `1–3` RIR default,
  `≥120 s`/`60–90 s` rest, and a conservative double-progression increment — each
  labelled with a confidence and guardrail.

### Round 2 corrections

- **Retracted** its Round-1 conflation of Refalo's hypertrophy review (PMID
  36334240) with the acute fatigue trial (PMID 36752989/PMC9908800).
- Revised "failure gives only trivial benefit" to "train close, not necessarily
  to, failure," reconciling Refalo's small effect with Robinson's proximity
  gradient.
- Added the Schoenfeld-load PMID 28834797 and **rejected** the mislabelled
  `10.1519/JSC.0000000000001764` (a different personal-training paper).
- Split the equipment claim: machines ≈ free weights for hypertrophy (Haugen
  2023) accepted; bands/bodyweight strength comparability accepted; bands/bodyweight
  **hypertrophy** equivalence rejected as an overclaim.
- Rejected all fabricated constants (numeric joint-stress/axial/SFR scores, a
  2500 ms timeout as evidence-based, a fixed 30–50% break reduction, specific
  injury-prevention claims).
- Voted to replace per-session set budgets with a weekly per-muscle ledger and to
  make general-fitness/fat-loss a weekly, aerobic-inclusive construct.

### Round 3 blueprint

- Produced a 13-rule doctrine and a full state-machine/schema/data-flow proposal.
- Proposed a two-tier ledger crediting **1.0 primary and 0.5 reviewed secondary**,
  goal-by-calibration volume bands as advisory ranges, RIR by state/goal/exercise
  type with null preserved, rest as ranges seeded from mechanics, a soft
  complexity cap with one narrow hard ceiling, and a coarse `ToleranceTag`
  replacing the pseudo-precise `fatigueScore`.
- Flagged the exact default RIR range and hypertrophy volume soft-cap as the one
  remaining item needing cross-agent sign-off rather than more evidence.

### Round 4 sign-off

Signed all twelve decisions and, critically, **retracted the 0.5 secondary
credit** in favour of `PRIMARY_ONLY_V1`; accepted state-based editable volume
ranges with no scientific floor, RIR `2–4`/`1–3` as versioned policy, editable
rest classes, and the two-session capability-evidence threshold. Signed the
deterministic/LLM boundary unchanged.

## Gemini — Exercise Selection and Equipment Lens

### Round 1 findings

- Delivered the deepest exercise-selection evidence base: free weights vs
  machines (Haugen 2023; Heidel 2022; Schwanbeck 2020), bands (Lopes 2019;
  Iversen 2021), ROM/lengthened work (Wolf 2023; Kassiano 2023; Pedrosa 2022;
  Maeo 2021/2023; Zabaleta-Korta 2023), single vs multi-joint (Rosa 2023;
  Gentil/Paoli), unilateral vs bilateral (Moran 2021; Liao 2022; Appleby 2019),
  instability (Behm 2015), calisthenics (Kotarsky 2018; van den Tillaar 2019;
  Ebben 2011), variation (Kassiano 2022; Baz-Valle 2019; Fonseca 2014), and
  concurrent training (Schumann 2022; Lundberg 2022).
- Proposed a rich metadata schema and a multi-factor ranking function that
  included numeric `axialLoadingScore`, `stabilityRequirementScore`,
  `jointStressProfiles`, `fatigueScore`, and `stimulusToFatigueRatio`, plus a
  five-tier substitution hierarchy and strict non-equivalence boundaries.
- Round-1 citation errors later corrected: the Currier NMA cited as
  `10.1136/bjsports-2022-106160` (PMID 37414541), the Wolf preprint DOI as the
  primary, Baz-Valle as `pone.0226981`, and a general lengthened-partial
  superiority claim.

### Round 2 corrections

- Corrected the Currier NMA to `10.1136/bjsports-2023-106807` (PMID 37414459) and
  the Schoenfeld load DOI to `...2200` (PMID 28834797); separated the Refalo acute
  trial, scoping review, and 2024 meta-analysis; resolved the Wolf journal vs
  preprint DOI; and split the Kassiano ROM review from the gastrocnemius RCT
  (`10.1519/JSC.0000000000004460`).
- **Revised** universal band hypertrophy equivalence (bands underload the
  lengthened position under Hooke's law) and universal long-length-partial
  superiority (advantage is muscle-specific, mainly biarticular/descending-limb
  muscles).
- **Rejected** exact floating-point SFR constants, fixed break reductions, and a
  2500 ms timeout as biological facts, reclassifying them as configurable
  heuristics; adopted a dual-ledger hierarchy (weekly per-muscle plus a
  per-session ceiling).

### Round 3 blueprint

- Produced a 13-rule doctrine table and detailed dispute resolutions, endorsing
  direct-primary credit for v1 behind a versioned accounting interface.
- Still carried numeric proposals — state volume bands, per-session ceilings,
  an ordinal `axialLoadingScore` (0–5), a 50% deload, a 3-week break trigger, a
  1500 ms latency SLO, and RIR bands — replacing the floating-point SFR with an
  ordinal `StimulusEfficiency` enum.
- Declared formal convergence with no remaining irreconcilable scientific
  disputes.

### Round 4 sign-off

Signed all twelve decisions and **retracted the exact volume, deload, axial-load,
body-mass-fraction, and latency constants**, accepting categorical
reviewed-metadata only (no numeric joint/SFR/axial/fatigue score, no body-mass
fraction), state-based editable ranges, the DeloadOffer without fixed constants,
and device-specific measured LLM SLOs. Signed the deterministic/LLM boundary.

## Grok — Inclusive / Body-Aware Lens

### Round 1 findings

- Anchored the ethics and inclusivity floor: BMI is not a capability test
  (Rubino 2025; Rubino 2020 stigma; Barry 2014 fitness-vs-fatness; Puhl 2020),
  RT belongs in fat-loss/general-fitness plans (Lopez 2022 ×2; Donnelly 2009),
  no aerobic mode is superior and HIIT is not required (Jakicic 2024; Wewege
  2017), multimodal activity is the health default (WHO 2020; Piercy 2018), and
  onboarding must not medicalise (Riebe 2015; do not build PAR-Q disease trees).
- Contributed older-adult/deconditioned entry (Fragala 2019; Izquierdo 2021),
  joint-sensitive analogs (OARSI 2019; Fransen 2015), push-up ground-reaction
  demand within a family only (Ebben 2011), and detraining (Mujika 2000).
- Flagged that `TrainingConstraint` is currently unapplied by `ExerciseFilter`,
  that a global `estimatedBodyMassFraction` ranker is "BMI gating by algebra,"
  and that the in-memory `bmi` getter is a leak risk.
- Round-1 citation error later corrected: the Schoenfeld load DOI as
  `10.1519/JSC.0000000000001764`; and proposed a `30–50%` return-from-break cut.

### Round 2 corrections

- **Retracted its own Round-1 Schoenfeld DOI** (`...1764` is an unrelated
  personal-trainer paper; correct is `...2200`, PMID 28834797).
- **Retracted the 30–50% return-from-break figure** as if empirical, keeping only
  "conservative."
- Corrected the Baz-Valle DOI to `10.1371/journal.pone.0226989` (the peer's
  `...0226981` is a different paper); rejected `bjsports-2022-106160` (404) as the
  Currier NMA and noted the nearby McLeod/Currier 2024 umbrella review as a
  distinct paper; split Refalo, Kassiano, and Wolf identities.
- Tightened bands/bodyweight (effective vs none, not hypertrophy-equivalent to
  loaded RT) and long-length partials (Wolf CI includes 0; Kassiano/Maeo
  muscle-specific); rejected axial/SFR scores, BMI bands, exact deloads, LLM
  timeouts, and injury-prevention guarantees as engine inputs.

### Round 3 blueprint

- Produced a 13-rule doctrine with an explicit inclusivity tier and adopted
  `PRIMARY_ONLY_V1`.
- Set state-based volume ranges, RIR `2–4`/`1–3`, rest as ranges, a temporary
  advanced ceiling only while uncalibrated, and an evidence threshold it wrote as
  **three completed family sessions spanning ≥14 days** to relax a soft penalty.
- Kept the fat-loss boundary at a Today RT session plus honest activity copy, and
  set strict measurement-free LLM prompts and enablement gates.

### Round 4 sign-off

Signed all twelve decisions and **retracted the three-session / 14-day threshold**
in favour of the agreed two comparable completed sessions plus explicit
confirmation; carried forward its retractions of the 30–50% break cut, exact
constants, BMI use, and equipment/ROM overclaims. Signed the deterministic/LLM
boundary.

## Round 4 Consensus Sign-Off

All four agents accepted the twelve canonical v1 decisions:

1. `PRIMARY_ONLY_V1` — one completed set credits one designated direct-primary
   muscle; secondary involvement is descriptive only.
2. State-based, editable weekly volume ranges; no mandatory scientific floor and
   no evidence-labelled automatic increment.
3. RIR `2–4` for INITIATE/RETURNING/LIMITED and `1–3` for established
   general/hypertrophy; nullable, editable, failure never automatic.
4. Editable rest classes resolved by versioned product policy.
5. Temporary advanced ceiling only while uncalibrated/returning, then a soft
   complexity input.
6. Two comparable completed sessions plus explicit confirmation may relax soft
   penalties only.
7. Today remains an RT-session planner; the program horizon may surface
   user-selected aerobic education; no Health Connect fatigue inference.
8. Non-diagnostic, user-controlled `DeloadOffer` without fixed constants.
9. Categorical reviewed metadata only — no numeric joint/SFR/axial/fatigue score,
   body-mass fraction, axial-load, or general ROM bonus.
10. Strict LLM gates with device-specific measured SLOs.
11. Body measurements optional but unused by v1 engines.
12. Reviewed-only automatic planning; full-browse/manual catalog retained.

Retraction ledger: **Opus** retracted the 0.5 secondary credit; **Gemini**
retracted the exact volume, deload, axial-load, body-mass-fraction, and latency
constants; **Grok** retracted the three-session / 14-day threshold and (in
Round 2) the 30–50% break cut and its own mis-cited Schoenfeld DOI; **Terra**
signed unchanged. All four explicitly signed the deterministic/LLM boundary.

## Union Bibliography

Every materially cited paper across the four reports appears below, deduplicated
and labelled. Labels: **VERIFIED** (identifiers and design confirmed against a
primary source in at least one round), **CORRECTED** (real paper, an identifier
was fixed during review), **DUPLICATE** (same work cited twice, e.g. journal +
preprint), **REJECTED** (wrong identifier, 404, or mislabel), **UNVERIFIED**
(bibliographic consistency only; primary full text not opened by an agent).
Population/study limits are stated because they bound every downstream claim.
Where agents reported conflicting secondary identifiers (PMID or page range) for
a verified paper, the conflict is flagged; the DOI is authoritative.

### A. Verified canonical doctrine anchors

| # | Citation | Identifiers | Label | Population / limit |
| --- | --- | --- | --- | --- |
| 1 | Currier BS, et al. ACSM Position Stand: Resistance Training Prescription… An Overview of Reviews. *Med Sci Sports Exerc.* 2026;58(4):851–872. | DOI 10.1249/MSS.0000000000003897; PMID 41843416 | VERIFIED | Healthy adults; overview of 137 reviews; not individualised dosing. Supersedes 2009 exact-dose emphasis. |
| 2 | Currier BS, et al. RT prescription for strength and hypertrophy: Bayesian NMA. *Br J Sports Med.* 2023;57(18):1211–1220. | DOI 10.1136/bjsports-2023-106807; PMID 37414459; PMC10579494 | VERIFIED | Supervised healthy adults; excludes many clinical/home contexts. |
| 3 | Schoenfeld BJ, Ogborn D, Krieger JW. Weekly RT volume dose-response. *J Sports Sci.* 2017;35(11):1073–1082. | DOI 10.1080/02640414.2016.1210197; PMID 27433992 | VERIFIED | Mostly young/untrained; population-level volume inference. |
| 4 | Pelland JC, et al. RT volume/frequency dose-response meta-regression. *Sports Med.* 2025. | DOI 10.1007/s40279-025-02344-w; PMID 41343037 | VERIFIED | ~79% male, ~25 y; trained/young prior, not inclusive floors. Preprint lineage; cite journal. |
| 5 | Schoenfeld BJ, Grgic J, Krieger J. RT frequency and hypertrophy. *J Sports Sci.* 2019;37(11):1286–1295. | DOI 10.1080/02640414.2018.1555906; PMID 30558493 | VERIFIED | Volume-equated frequency; effect depends on volume. Gemini's alternate `10.1007/s40279-018-01033-x` / *Sports Med* 49(2) identifier is flagged as a mismatch. |
| 6 | Schoenfeld BJ, Grgic J, Ogborn D, Krieger JW. Low- vs high-load RT. *J Strength Cond Res.* 2017;31(12):3508–3523. | DOI 10.1519/JSC.0000000000002200; PMID 28834797 | CORRECTED | Often failure-based protocols. Mislabelled `...0001764` rejected (see D). |
| 7 | Robinson ZP, et al. Proximity-to-failure meta-regression. *Sports Med.* 2024;54(9):2209–2231. | DOI 10.1007/s40279-024-02069-2; PMID 38970765 | VERIFIED | Estimated (reconstructed) RIR; heterogeneous protocols. Gemini page range 2269–2289 flagged. |
| 8 | Refalo MC, et al. Proximity-to-failure and hypertrophy: review/meta-analysis. *Sports Med.* 2023;53(4):869–891. | DOI 10.1007/s40279-022-01784-y; PMID 36334240; PMC9935748 | VERIFIED | Failure not clearly superior; definitions vary. |
| 9 | Refalo MC, et al. Proximity-to-failure and neuromuscular fatigue (acute crossover). *Sports Med Open.* 2023;9:10. | DOI 10.1186/s40798-023-00554-y; PMID 36752989; PMC9908800 | CORRECTED | Acute bench-press crossover, trained adults; not long-term adaptation. Opus's R1 conflation with #8 corrected. |
| 10 | Refalo MC, et al. Proximity-to-failure hypertrophy: SR with meta-analysis. *Sports Med.* 2024;54(3):669–688. | DOI 10.1007/s40279-023-01944-x; PMID 37924409 | VERIFIED | Distinct from #8 and #9; trained/untrained adults. |
| 11 | Hickmott LM, et al. Load/volume autoregulation. *Sports Med Open.* 2022;8:9. | DOI 10.1186/s40798-021-00404-9; PMID 35038063 | VERIFIED | Small evidence base, resistance-trained. PMID reported as 35038063 and 35072810 across agents — conflict flagged; DOI authoritative. |
| 12 | Schumann M, et al. Concurrent aerobic + strength compatibility. *Sports Med.* 2022;52(3):601–612. | DOI 10.1007/s40279-021-01587-7; PMID 34757594; PMC8891239 | VERIFIED | Healthy supervised adults 18–65; explosive-strength caveat same-session. |
| 13 | Fragala MS, et al. RT for older adults: NSCA Position Statement. *J Strength Cond Res.* 2019;33(8):2019–2052. | DOI 10.1519/JSC.0000000000003230; PMID 31343601 | VERIFIED | Older adults ≥60; position statement, not an algorithm trial. |
| 14 | Bull FC, et al. WHO 2020 guidelines on physical activity. *Br J Sports Med.* 2020;54(24):1451–1462. | DOI 10.1136/bjsports-2020-102955; PMID 33239350; PMC7719906 | VERIFIED | Global population public-health minimums; no BMI gating. |
| 15 | Jakicic JM, et al. Physical activity and excess body weight/adiposity: ACSM consensus. *Med Sci Sports Exerc.* 2024;56:2076–2091. | DOI 10.1249/MSS.0000000000003520; PMID 39277776 | VERIFIED | Overweight/obese adults; no mode superior; supports inclusive tailoring, not BMI exclusion. Issue/page reported as 56(10):2076–2091 and 56(11):2216–2231 — conflict flagged. |
| 16 | Lopez P, et al. RT effectiveness on body composition in overweight/obesity. *Obes Rev.* 2022;23(5):e13428. | DOI 10.1111/obr.13428; PMID 35191588 | VERIFIED | BMI-defined overweight/obesity; diet drives scale weight. |
| 17 | Rubino F, et al. Definition/diagnostic criteria of clinical obesity. *Lancet Diabetes Endocrinol.* 2025;13(3):221–262. | DOI 10.1016/S2213-8587(24)00316-4; PMID 39824205 | VERIFIED | Diagnostic framework; ethics/governance only — do not implement in-app. |
| 18 | Rubino F, et al. Joint international consensus: ending obesity stigma. *Nat Med.* 2020;26(4):485–497. | DOI 10.1038/s41591-020-0803-x | VERIFIED | Consensus; UX-language guidance. PMID reported as 32127716 and 32132694 — conflict flagged; DOI authoritative. |
| 19 | Riebe D, et al. ACSM exercise preparticipation screening update. *Med Sci Sports Exerc.* 2015;47(11):2473–2479. | DOI 10.1249/MSS.0000000000000664; PMID 26473759 | VERIFIED | Screening algorithm; removes mandatory clearance; not BMI-based. |
| 20 | Lekadir K, et al. FUTURE-AI consensus. *BMJ.* 2025;388:e081554. | DOI 10.1136/bmj-2024-081554; PMID 39879942 | VERIFIED | Healthcare-AI governance only; published correction exists. |
| 21 | NIST AI Risk Management Framework 1.0 (2023) and GenAI Profile (2024). | DOI 10.6028/NIST.AI.100-1; 10.6028/NIST.AI.600-1 | VERIFIED | Engineering governance only; not exercise efficacy. |

### B. Verified / corrected supporting sources (with caveats)

| # | Citation | Identifiers | Label | Population / limit |
| --- | --- | --- | --- | --- |
| 22 | Haugen ME, et al. Free weights vs machines. *BMC Sports Sci Med Rehabil.* 2023;15:103. | DOI 10.1186/s13102-023-00713-4; PMID 37574341 | VERIFIED | Adults 18–65; small hypertrophy k; strength is testing-specific. PMID conflict 37574341 vs 37582807 — use DOI. |
| 23 | Heidel KE, Novak ZJ, Mavros Y. Machines and free weight exercises. *J Sports Med Phys Fitness.* 2022;62(8):1061–1070. | DOI 10.23736/S0022-4707.21.12788-X; PMID 34609100 | VERIFIED | Healthy adults; RIR not standardised in some trials. |
| 24 | Schwanbeck SR, et al. Free weights vs machines RCT. *J Strength Cond Res.* 2020;34(7):1851–1859. | DOI 10.1519/JSC.0000000000003349; PMID 32358360 | VERIFIED | Untrained cohort; 8 weeks (early neural window). |
| 25 | Lopes JSS, et al. Elastic vs conventional resistance. *SAGE Open Med.* 2019;7:2050312119831116. | DOI 10.1177/2050312119831116; PMID 30815258 | VERIFIED | Strength outcomes only; not a hypertrophy-equivalence trial. |
| 26 | Iversen VM, et al. Time-efficient training: narrative review. *Sports Med.* 2021;51(10):2079–2095. | DOI 10.1007/s40279-021-01490-1; PMID 34125411 | VERIFIED | Narrative synthesis, not pooled meta-analysis. |
| 27 | Wolf M, et al. Partial vs full ROM: SR/MA. *Int J Strength Cond.* 2023;3(1). | DOI 10.47206/ijsc.v3i1.182 | VERIFIED / DUPLICATE | Non-MEDLINE journal; long-length-partial vs full CI includes 0. SportRxiv preprint is the same study (see D). |
| 28 | Kassiano W, et al. ROM and hypertrophy: SR ("Which ROMs Lead to Rome?"). *J Strength Cond Res.* 2023;37(5):1135–1144. | DOI 10.1519/JSC.0000000000004415; PMID 36662126 | VERIFIED | 11–23 study ROM review; muscle/site-specific. |
| 29 | Kassiano W, et al. Gastrocnemius long-length partial RCT. *J Strength Cond Res.* 2023;37(9):1746–1753. | DOI 10.1519/JSC.0000000000004460 | CORRECTED | Young/untrained women, calves; distinct from #28. PMID reported as 36580316 and 37015016 — conflict flagged. |
| 30 | Kassiano W, et al. Exercise variation and hypertrophy: SR. *J Strength Cond Res.* 2022;36(6):1753–1762. | DOI 10.1519/JSC.0000000000004258; PMID 35438660 | VERIFIED | Young trained males; systematic (not random) variation. |
| 31 | Pedrosa GF, et al. Partial ROM at long muscle lengths. *Eur J Sport Sci.* 2022;22(5):715–725. | DOI 10.1080/17461391.2021.1927199; PMID 33977835 | VERIFIED | Untrained young women; isolated knee extension. |
| 32 | Maeo S, et al. Seated vs prone leg curl. *Med Sci Sports Exerc.* 2021;53(4):825–837. | DOI 10.1249/MSS.0000000000002523 | VERIFIED | Untrained young adults, hamstrings; not a general ROM law. PMID reported 33009373 vs 33009197 — conflict flagged. |
| 33 | Maeo S, et al. Overhead vs neutral triceps extension. *Eur J Sport Sci.* 2023;23(7):1240–1250. | DOI 10.1080/17461391.2022.2100279 | VERIFIED | Untrained young adults, triceps; single-joint. PMID reported 35819325 vs 35819335 — conflict flagged. |
| 34 | Rosa A, et al. Single- vs multi-joint hypertrophy: SR/MA. *Strength Cond J.* 2023;45(1):49–57. | DOI 10.1519/SSC.0000000000000720 | VERIFIED | Healthy adults; few volume-equated studies. |
| 35 | Gentil P, Soares S, Bottaro M. Single- vs multi-joint. *Asian J Sports Med.* 2015;6(2):e26838. | DOI 10.5812/asjsm.26838; PMID 26448847 | CORRECTED | Gemini R1 conflated it with Paoli 2017; separated in R2. |
| 36 | Paoli A, et al. Single- vs multi-joint at equal load volume. *Front Physiol.* 2017;8:1105. | DOI 10.3389/fphys.2017.01105; PMID 29312007 | VERIFIED | Recreationally active men; 8 weeks. |
| 37 | Moran J, et al. Unilateral vs bilateral training. *Sports Med.* 2021;51(2):225–242. | DOI 10.1007/s40279-020-01367-9; PMID 33188448 | VERIFIED | Athletes/active adults; movement-specific transfer, hypertrophy equivalent. |
| 38 | Liao KF, et al. Unilateral vs bilateral lower-limb. *Biol Sport.* 2022;39(3):485–497. | DOI 10.5114/biolsport.2022.107024; PMID 35959319 | VERIFIED | Athletes/untrained; hypertrophy NS, specificity effects. |
| 39 | Behm DG, et al. Unstable-surface strength training. *Sports Med.* 2015;45(12):1645–1669. | DOI 10.1007/s40279-015-0384-x | VERIFIED | Lifespan; erratum 10.1007/s40279-016-0497-x. PMID 26359066 vs 26092159 — conflict flagged. |
| 40 | Kotarsky CJ, et al. Progressive calisthenic push-up training. *J Strength Cond Res.* 2018;32(3):651–659. | DOI 10.1519/JSC.0000000000002345 | VERIFIED | Moderately trained men; 4 weeks — too short for hypertrophy conclusions. PMID 29189401 vs 29466268 — conflict flagged. |
| 41 | van den Tillaar R. Push-up vs bench press kinematics/EMG. *Sports Med Int Open.* 2019;3(3):E74–E81. | DOI 10.1055/a-1001-2526; PMID 31517070 | VERIFIED | Resistance-trained men; acute EMG, not hypertrophy. |
| 42 | Ebben WP, et al. Push-up ground-reaction forces. *J Strength Cond Res.* 2011;25(10):2891–2894. | DOI 10.1519/JSC.0b013e31820f9855 | VERIFIED | Mixed-sex adults; ~64% BW standard push-up; intra-family demand only, not a planner score. PMID 21873838 vs 21993012 — conflict flagged. |
| 43 | Baz-Valle E, et al. Exercise variation vs fixed selection. *PLoS ONE.* 2019;14(12):e0226989. | DOI 10.1371/journal.pone.0226989; PMID 31881066 | CORRECTED | Trained young men; hypertrophy equal, variation raised motivation. `...0226981` is a different paper (see D). |
| 44 | Fonseca RM, et al. Exercise variation and regional hypertrophy. *J Strength Cond Res.* 2014;28(11):3085–3092. | DOI 10.1519/JSC.0000000000000539; PMID 24832974 | VERIFIED | Trained men; lower-body regional coverage. |
| 45 | Lundberg TR, et al. Concurrent training and muscle-fiber hypertrophy. *Sports Med.* 2022;52(10):2391–2403. | DOI 10.1007/s40279-022-01688-x; PMID 35476184 | VERIFIED | Biopsy cohorts, small per-study n; Type II preserved. |
| 46 | Lopez P, et al. Moderators of RT in overweight/obesity. *Med Sci Sports Exerc.* 2022;54:1804–1816. | DOI 10.1249/MSS.0000000000002984; PMID 35977113 | VERIFIED | Overweight/obesity; dose and added aerobic not significant moderators. Page range reported 1804–1816 vs 1982–1994 — conflict flagged. |
| 47 | Bannuru RR, et al. OARSI guidelines for OA management. *Osteoarthritis Cartilage.* 2019;27(11):1578–1589. | DOI 10.1016/j.joca.2019.06.011; PMID 31278997 | VERIFIED | Clinical OA — analog for joint-sensitive users, not a diagnosis. |
| 48 | Fransen M, et al. Exercise for knee OA. *Cochrane Database Syst Rev.* 2015;(1):CD004376. | DOI 10.1002/14651858.CD004376.pub3; PMID 25569281 | VERIFIED | Clinical knee OA; 2015. |
| 49 | Piercy KL, et al. Physical Activity Guidelines for Americans. *JAMA.* 2018;320(19):2020–2028. | DOI 10.1001/jama.2018.14854; PMID 30418471 | VERIFIED | US guideline; no minimum bout length. |
| 50 | Barry VW, et al. Fitness vs fatness on all-cause mortality: MA. *Prog Cardiovasc Dis.* 2014;56(4):382–390. | DOI 10.1016/j.pcad.2013.09.002; PMID 24438729 | VERIFIED | Prospective CRF+BMI cohorts; CRF not collected in v1. |
| 51 | Morton RW, et al. Load and hormones do not determine RT hypertrophy/strength. *J Appl Physiol.* 2016;121(1):129–138. | DOI 10.1152/japplphysiol.00154.2016; PMID 27174923 | VERIFIED | Trained young men; to failure. |
| 52 | Androulakis-Korakakis P, et al. Minimum effective dose for 1RM. *Sports Med.* 2020;50(5):851–865. | DOI 10.1007/s40279-019-01236-0; PMID 31797219 | VERIFIED | Trained men; 1RM strength only. |
| 53 | Williams TD, et al. Periodised vs non-periodised. *Sports Med.* 2017;47(12):2583–2600. | DOI 10.1007/s40279-017-0734-y; PMID 28497285 | VERIFIED | Strength-focused; modest effect; outranked for general adults by the 2026 stand. |
| 54 | Ratamess NA, et al. ACSM Progression Models in RT. *Med Sci Sports Exerc.* 2009;41(3):687–708. | DOI 10.1249/MSS.0b013e3181915670; PMID 19204579 | VERIFIED | Foundational; superseded on specifics by the 2026 stand. |
| 55 | Helms ER, et al. RIR-based RPE scale for RT. *Strength Cond J.* 2016;38(4):42–49. | DOI 10.1519/SSC.0000000000000218; PMID 27531969; PMC4961270 | VERIFIED | Methods paper; RIR more accurate near failure. |
| 56 | Zhang X, et al. Autoregulation vs fixed loading in athletes. *Front Physiol.* 2021;12:651112. | DOI 10.3389/fphys.2021.651112; PMC7994759 | VERIFIED | 151/166 male, athlete-heavy, short; low–moderate confidence; do not generalise to beginners. |
| 57 | Chen SF, et al. LLM-assisted review of clinical LLMs. *Nat Med.* 2026;32:1152–1159. | DOI 10.1038/s41591-026-04229-5; PMID 41776077; PMC13004689 | VERIFIED | Healthcare, not exercise; supports the evidence gap, not efficacy. |

### C. Corrected citation-identifier ledger

The cross-review fixed these identifier errors (the paper is real; the reported
identifier was wrong):

- Currier 2023 NMA is `10.1136/bjsports-2023-106807` / PMID 37414459 — not
  `bjsports-2022-106160` / PMID 37414541.
- Schoenfeld low- vs high-load is `10.1519/JSC.0000000000002200` / PMID 28834797
  — not `10.1519/JSC.0000000000001764`.
- Refalo acute trial (PMID 36752989) is separate from the review (PMID 36334240)
  and the 2024 meta-analysis (PMID 37924409).
- Wolf ROM peer-reviewed DOI is `10.47206/ijsc.v3i1.182`; the SportRxiv preprint
  is the same study and is not counted separately.
- Kassiano ROM review (PMID 36662126) and the gastrocnemius RCT
  (`10.1519/JSC.0000000000004460`) are different studies.
- Baz-Valle exercise-variation trial is `10.1371/journal.pone.0226989` — not
  `...0226981`.

### D. Rejected identifiers (do not cite)

| Reported identifier | Reason | Correct target |
| --- | --- | --- |
| `10.1136/bjsports-2022-106160` (PMID 37414541) | Does not resolve to the Currier NMA (404) | Use #2 above |
| `10.1519/JSC.0000000000001764` | Wayment & McDonald 2017 personal-training paper | Use #6 above |
| `10.1371/journal.pone.0226981` | Different, unrelated paper | Use #43 above |
| `10.51224/SRXIV.182` (SportRxiv preprint) | Duplicate preprint lineage of the Wolf journal article | Use #27 above; Opus noted the preprint is `SRXIV.198`, not `.182` |

### E. Context-specific and rejected-claim sources (retained, not omitted)

These papers are real and materially cited, but a claim built on them was
rejected or narrowly bounded during review. They are retained here so no paper
is silently dropped.

| Citation | Identifiers | Bounded outcome |
| --- | --- | --- |
| Krieger JW. Single vs multiple sets: MA. *J Strength Cond Res.* 2010. | PMID 20300012 | UNVERIFIED (Opus only). Supports "multiple > single," not a universal set count. |
| Schoenfeld BJ, et al. RT frequency: MA. *Sports Med.* 2016;46:1689–1697. | DOI 10.1007/s40279-016-0543-8; PMID 27102172 | UNVERIFIED; superseded by the 2019 volume-equated frequency MA. |
| Schoenfeld BJ, et al. Repetition duration: SR/MA. *Sports Med.* 2015;45(4):577–585. | DOI 10.1007/s40279-015-0304-0 | UNVERIFIED. Tempo ~0.5–8 s works; used as a sanity bound, not a prescription. |
| Nunes JP, et al. Exercise order: SR/MA. 2021. | PMID 32077380 | UNVERIFIED by peers. Priority lift first; hypertrophy order-insensitive. |
| Grgic J, et al. Short vs long rest: SR. *Eur J Sport Sci.* 2017. | DOI 10.1080/17461391.2017.1340524 | UNVERIFIED. No exact rest seconds encoded. |
| Grgic J, et al. RT frequency and strength: SR/MA. *Sports Med.* 2018;48:1207–1220. | DOI 10.1007/s40279-018-0872-x; PMID 29470825 | UNVERIFIED. Frequency is a scheduling tool when volume is equated. |
| Grgic J, et al. Linear vs DUP hypertrophy: SR/MA. 2017. | PMID 28848690 | UNVERIFIED. No hypertrophy difference; periodisation not required for general users. |
| Singer TJ, et al. Inter-set rest intervals: SR/MA. *Front Sports Act Living.* 2024;6:1429789. | DOI 10.3389/fspor.2024.1429789 | VERIFIED with preprint-origin caveat; small k, overlapping CrIs. PMID reported 39399434 vs 39205815 — conflict flagged. No hard rest law. |
| Roberts BM, Nuckols G, Krieger JW. Sex differences in RT: SR/MA. *J Strength Cond Res.* 2020. | PMID 32218059 | UNVERIFIED by peers. Relative gains sex-similar; do not scale dose by sex. |
| Momma H, et al. Muscle-strengthening and mortality: SR/MA. *Br J Sports Med.* 2022. | DOI 10.1136/bjsports-2021-105061 | UNVERIFIED by peers. Health benefit plateaus ~30–60 min/wk. |
| Wewege MA, et al. RT and body fat: SR/MA. *Sports Med.* 2022. | DOI 10.1007/s40279-021-01562-2; PMID 34536199 | UNVERIFIED by peers. RT preserves fat-free mass in a deficit. |
| Wewege MA, et al. HIIT vs MICT on body composition. *Obes Rev.* 2017;18(6):635–646. | DOI 10.1111/obr.12532; PMID 28415882 | VERIFIED (Grok). Young overweight adults; HIIT not superior for weight. |
| Fisher JP, Steele J, et al. Role of supervision in RT: SR/MA. *Int J Strength Cond.* 2022. | (non-indexed) | UNVERIFIED. Unsupervised users mis-estimate effort — supports lower RIR trust. |
| Zabaleta-Korta A, et al. Regional biceps hypertrophy by ROM. *J Hum Kinet.* 2023. | DOI 10.5114/jhk/168469; PMID 37559762 | UNVERIFIED. Region- and site-specific; not a general bonus. |
| Appleby BB, et al. Transfer of lower-body strength. *J Strength Cond Res.* 2019;33(10):2618–2628. | DOI 10.1519/JSC.0000000000003294; PMID 31373977 | UNVERIFIED. Specificity, not general superiority. |
| Jakicic JM, et al. PA and prevention of weight gain: SR. *Med Sci Sports Exerc.* 2019;51:1262–1269. | DOI 10.1249/MSS.0000000000001938; PMID 31095078 | UNVERIFIED. Observational; soft ranking only, not eligibility. |
| Swift DL, et al. Exercise and weight loss/maintenance. *Prog Cardiovasc Dis.* 2018;61:206–213. | DOI 10.1016/j.pcad.2018.07.014; PMID 30003901 | UNVERIFIED. No in-app "weight-loss minutes" target. |
| Donnelly JE, et al. ACSM PA for weight loss/prevention. *Med Sci Sports Exerc.* 2009;41:459–471. | DOI 10.1249/MSS.0b013e3181949333; PMID 19127177 | VERIFIED (Grok). Foundational; diet dominates scale weight. |
| Khalafi M, et al. Concurrent vs aerobic/RT in older adults. *Healthcare.* 2025;13(7):776. | DOI 10.3390/healthcare13070776; PMID 40218073 | VERIFIED with MDPI journal caveat; age ≥50; not a design driver. |
| Gaesser GA, Angadi SS. Fitness vs weight loss. *iScience.* 2021;24(10):103150. | DOI 10.1016/j.isci.2021.103150 | VERIFIED (DOI). Narrative; track fitness/adherence, not kg. |
| Puhl RM, et al. Weight stigma and obesity. *Am Psychol.* 2020;75(2):274–289. | PMID 32052994 | UNVERIFIED. Stigma reduces activity; never display BMI. |
| Izquierdo M, et al. ICFSR older-adult exercise recommendations. *J Nutr Health Aging.* 2021;25:824–853. | DOI 10.1007/s12603-021-1665-8; PMID 34518905 | UNVERIFIED. Older/clinical; collect capability, not age. |
| Garber CE, et al. ACSM Quantity/Quality of Exercise. *Med Sci Sports Exerc.* 2011;43:1334–1359. | DOI 10.1249/MSS.0b013e318213fefb; PMID 21681120 | UNVERIFIED. Foundational FITT-VP; partly superseded. |
| Warburton DER, et al. 2021 PAR-Q+. *Health Fit J Can.* 2021;14(1):83–87. | DOI 10.14288/hfjc.v14i1.351 | UNVERIFIED. Not built into required onboarding. |
| Lauersen JB, et al. Strength training and injury prevention: SR/MA. *Br J Sports Med.* 2018;52:1557–1563. | DOI 10.1136/bjsports-2018-099078; PMID 30131332 | UNVERIFIED. Athlete/prevention context; not an app injury-prevention claim. |
| Mujika I, Padilla S. Detraining Parts I–II. *Sports Med.* 2000;30:79–87, 145–154. | DOI 10.2165/00007256-200030020-00002; 10.2165/00007256-200030030-00001 | UNVERIFIED. Athletes; supports conservative reload, no fixed percentage. |
| McLeod JC, Currier BS, et al. RT umbrella review. *J Sport Health Sci.* 2024. | DOI 10.1016/j.jshs.2023.06.005; PMID 37385345 | Context only. The real paper near the rejected `bjsports-2022-106160`; different journal. |
| "Optimal RT prescriptions in sarcopenia": MA. *Aging Clin Exp Res.* 2025. | DOI 10.1007/s40520-025-03235-w | UNVERIFIED. Older-adult strength/function context. |

### F. Rejected engine claims (papers real, claims not encoded)

The following quantitative claims were **rejected** as engine inputs even though
their supporting papers are cited above:

- exact universal weekly set floors/ceilings (e.g. a `≥10`-set floor);
- fixed set-progression percentages;
- fixed calendar deloads or a fixed 50% / 30–50% reduction;
- numeric SFR, axial-load, joint-stress, fatigue, or injury-risk scores;
- universal bands/bodyweight hypertrophy equivalence;
- universal long-length-partial superiority;
- BMI/body-mass exercise ranking;
- automatic Health Connect cardio-to-fatigue inference;
- an evidence-derived model timeout (e.g. `2500 ms` or `1500 ms`);
- any claim that WallCrawl prevents injury or diagnoses recovery.

## Research Backlog (does not block v1)

- fractional/indirect secondary-muscle set accounting (`LedgerPolicy v2`);
- validated body-mass mechanics for recommendation ranking;
- direct algorithmic deload and return-from-break trials;
- RIR calibration in unsupervised beginners;
- bodyweight/band hypertrophy by progression family and population;
- muscle-specific ROM evidence;
- prospective LLM-versus-deterministic workout-planner trials;
- independent expert validation of capability and substitution metadata;
- pain-stop taxonomy beyond a boolean.
