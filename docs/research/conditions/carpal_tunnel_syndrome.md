# Carpal tunnel syndrome: literature review for exercise planning

**Evidence grade for exercise guidance: very low.** This supports workout planning and general mobility information. It is not treatment. The structured version is `carpal_tunnel_syndrome.json`.

## Bottom line

- No exercise has good evidence behind it for carpal tunnel syndrome (CTS). The evidence is low or very low certainty, and reviews of the same trials reach opposite conclusions.
- AAOS/ASSH 2024 says exercise does not improve long-term patient-reported outcomes, at Limited strength ([Shapiro 2025](https://doi.org/10.5435/JAAOS-D-24-01179)). That is not evidence of harm or of no short-term benefit.
- Gliding added to a splint usually shows no added benefit ([Abdolrazaghi 2023](https://doi.org/10.1177/15589447211006857), [Sim 2019](https://doi.org/10.1016/j.jht.2018.01.004), [Arman 2024](https://doi.org/10.23736/S1973-9087.24.08432-6)). A bundle of education, splinting and gliding reduced conversion to surgery ([Lewis 2020](https://doi.org/10.1016/j.jphys.2020.03.007)).
- Harms are essentially unmeasured, not shown to be absent ([Page 2012](https://doi.org/10.1002/14651858.CD009899)).
- No study found measured push-ups, planks, hangs or barbell grip in people with CTS. Every gym-specific restriction here is an inference.

## Scope and search log

**Question:** which exercises help or harm people with CTS, at what dose, and what should a workout planner avoid or modify?

**Method:** three independent searches on 2026-11-01 covered guidelines, systematic reviews, and trials and mechanisms. They ran 41 logged queries or retrievals across PubMed, Europe PMC, Crossref, web search and direct fetches. Results were merged and de-duplicated by DOI and PMID. PMIDs from the searches were checked against PubMed for DOI, authors and journal. An independent check then reopened every source; see Verification. There was no formal risk-of-bias assessment, and this is not a systematic review.

**Could not be read:** the final JOSPT 2019 and 2026 guidelines (HTTP 403), NICE CKS and NG127 (HTTP 403), and the German AWMF S3 guideline (404 or login wall). The last two are not used at all.

| Searcher | Database | Query | Hits |
|---|---|---|---|
| A | PubMed E-utilities | `"Carpal Tunnel Syndrome"[Mesh] AND (guideline[pt] OR "practice guideline"[pt])` | 14 |
| A | PubMed E-utilities | `carpal tunnel syndrome[tiab] AND (clinical practice guideline[tiab] OR consensus[tiab] OR clinical pathway[tiab])` | 158 (first 40 IDs listed; selected records read) |
| A | PubMed E-utilities | `carpal tunnel syndrome[tiab] AND (Clinical Practice Guidelines[ti] OR guideline[ti] OR guidelines[ti] OR consensus[ti] OR Delphi[ti])` | 59 |
| A | PubMed E-utilities | `carpal tunnel syndrome[tiab] AND (exercise[ti] OR rehabilitation[ti] OR therapy[ti]) AND (guideline[ti] OR consensus[ti] OR Delphi[ti] OR recommendations[ti])` | 3 |
| A | Europe PMC REST | `TITLE:"carpal tunnel" AND (TITLE:guideline OR TITLE:guidelines OR TITLE:consensus OR TITLE:pathway)` | 46 |
| A | Europe PMC REST | `DOI:10.2519/jospt.2026.0301 OR DOI:10.2519/jospt.2019.0301 OR DOI:10.1016/j.apmr.2014.06.022` | 3 (all flagged not open access, no PMCID) |
| A | Crossref REST | `query.title=carpal tunnel syndrome guideline (rows=20)` | 987706 relevance-ranked; first 20 inspected, none added beyond the PubMed set |
| A | WebSearch | `carpal tunnel syndrome clinical practice guideline national (Dutch, German AWMF S3, BSSH, NICE, Australia, Italian, Spanish) recommendations exercise splint` | 10 links |
| A | WebSearch | `NICE CKS carpal tunnel syndrome management wrist splint 6 weeks referral; BSSH carpal tunnel guideline commissioning` | 10 links (BOA/RCS commissioning guide opened; NICE CKS itself not returned) |
| A | WebSearch | `AWMF S3 Leitlinie Karpaltunnelsyndrom Diagnostik und Therapie 005-003` | 9 links; AWMF PDFs returned 404/HTML when fetched, so the guideline was not read and is not reported as a paper |
| A | WebSearch | `AWMF 005-003 S3 Leitlinie Karpaltunnelsyndrom 2022 konservative Therapie Schiene Empfehlung Antoniadis Chirurgie 2023` | 10 links; Springer and Thieme copies blocked by login/bot wall, not read |
| A | WebSearch | `Erickson 2019 JOSPT "Hand Pain and Sensory Deficits: Carpal Tunnel Syndrome" recommendations grade orthosis nerve gliding exercise stretching should not` | 10 links; surfaced the 2026 revision |
| A | WebSearch (nice.org.uk, cks.nice.org.uk) | `Carpal tunnel syndrome NICE clinical knowledge summary management "splint" corticosteroid injection refer surgery cks.nice.org.uk scenarios` | 9 links; NICE NG127 page and CKS both returned HTTP 403 when fetched, so neither was read and neither is reported as a paper |
| B | PubMed E-utilities | `"carpal tunnel syndrome"[Title] AND (exercise[Title/Abstract] OR stretching[Title/Abstract] OR mobilization[Title/Abstract] OR mobilisation[Title/Abstract]) AND (systematic[sb] OR meta-analysis[pt])` | 15 |
| B | PubMed E-utilities | `"carpal tunnel syndrome"[Title] AND "Cochrane Database Syst Rev"[Journal]` | 22 |
| B | PubMed E-utilities | `"carpal tunnel syndrome"[Title] AND (splint*[Title/Abstract] OR "activity modification"[Title/Abstract] OR ergonomic*[Title/Abstract] OR keyboard[Title/Abstract]) AND (systematic[sb] OR meta-analysis[pt])` | 59 (first 30 IDs retrieved) |
| B | Europe PMC REST | `TITLE:"carpal tunnel syndrome" AND (exercise OR stretching OR mobilization) AND (PUB_TYPE:"systematic-review" OR PUB_TYPE:"Meta-Analysis" OR TITLE:"systematic review")` | 43 (first 25 listed) |
| B | PubMed E-utilities | `"carpal tunnel syndrome"[Title/Abstract] AND (ergonomic*[Title/Abstract] OR keyboard[Title/Abstract] OR computer[Title/Abstract] OR "work-related"[Title/Abstract] OR occupational[Title/Abstract] OR biomechanical[Title/Abstract]) AND (meta-analysis[pt] OR systematic[sb])` | 54 (first 25 IDs retrieved) |
| B | PubMed E-utilities | `"carpal tunnel syndrome"[Title] AND ("clinical practice guideline"[Title/Abstract] OR guideline[pt] OR guideline[Title]) AND ("physical therapy"[Title/Abstract] OR rehabilitation[Title/Abstract] OR management[Title/Abstract])` | 15 |
| B | PubMed E-utilities | `"carpal tunnel"[Title] AND ("physical activity"[Title/Abstract] OR "resistance training"[Title/Abstract] OR "strengthening"[Title/Abstract] OR "stretching"[Title/Abstract] OR yoga[Title/Abstract] OR "carpal tunnel pressure"[Title/Abstract]) AND randomized controlled trial[pt]` | 9 |
| B | PubMed E-utilities | `"carpal tunnel"[Title] AND ("wrist posture"[Title/Abstract] OR "wrist extension"[Title/Abstract] OR "weight-bearing"[Title/Abstract] OR "weight bearing"[Title/Abstract] OR "pressure"[Title]) AND (pressure[Title/Abstract])` | 100 (first 25 IDs retrieved; titles of 14 reviewed, one abstract opened) |
| B | PubMed E-utilities | `Wolny T[Author] AND "carpal tunnel syndrome"[Title] AND (neurodynamic[Title/Abstract] OR manual[Title/Abstract])` | 8 |
| B | JOSPT website (direct fetch, not a search) | `https://www.jospt.org/doi/10.2519/jospt.2026.0301 and https://www.jospt.org/doi/10.2519/jospt.2019.0302` | Both returned HTTP 403. The graded recommendations in the APTA guideline were not read; the 2019 URL was constructed by the searcher and did not resolve to readable content. |
| B | Europe PMC fullTextXML (retrieval, not a search) | `PMC11536321/fullTextXML` | HTTP 500; PMC efetch returned abstract and plain-language summary only |
| C | PubMed | `"carpal tunnel syndrome"[Title] AND (exercise[Title/Abstract] OR "nerve gliding"[Title/Abstract] OR "tendon gliding"[Title/Abstract]) AND randomized controlled trial[Publication Type]` | 41 |
| C | PubMed | `"carpal tunnel" AND pressure AND ("wrist position" OR "wrist extension" OR "weight-bearing" OR "push-up")` | 39 |
| C | PubMed | `"carpal tunnel syndrome" AND (weightlifting OR "weight lifting" OR "resistance training" OR gym OR crossfit)` | 10 (none measured gym lifts in CTS; the only exercise study was the post-release dual-task crossover, plus case reports and narrative pieces) |
| C | PubMed | `"carpal tunnel syndrome"[Title] AND exercise AND (systematic review[Publication Type] OR meta-analysis[Publication Type])` | 22 |
| C | PubMed | `"carpal tunnel syndrome" AND ("adverse events" OR harms OR worsening) AND (exercise OR gliding OR neurodynamic)` | 21 |
| C | Europe PMC | `"carpal tunnel syndrome" AND ("nerve gliding" OR "tendon gliding" OR "strengthening") AND OPEN_ACCESS:y AND SRC:MED` | 938 (first 25 screened by title) |
| C | PubMed | `"carpal tunnel pressure" AND (fingertip OR pinch OR grip OR "finger" OR load) AND (Rempel[Author] OR Keir[Author])` | 8 |
| C | PubMed | `"carpal tunnel" AND (lumbrical OR "flexor tendon" OR "muscle incursion") AND pressure AND (grip OR fist OR incursion)` | 7 |
| C | PubMed | `(Coppieters MW[Author] OR Echigo[Author]) AND "median nerve" AND (excursion OR sliders OR strain OR gliding)` | 12 |
| C | PubMed | `"carpal tunnel syndrome" AND (forceful OR "hand force" OR vibration OR "repetitive") AND (meta-analysis[Publication Type] OR systematic review[Publication Type]) AND (occupational OR work)` | 16 |
| C | PubMed | `"carpal tunnel" AND ("push-up" OR "push up" OR "weight bearing" OR "weight-bearing" OR cycling OR cyclist OR "hand-bearing" OR climber OR athlete*) AND (median nerve OR pressure)` | 53 (first 10 screened; no push-up or weight-bearing pressure study among them) |
| C | PubMed | `"median nerve" AND exercise AND (acute OR immediate) AND (ultrasound OR "cross-sectional area") AND ("carpal tunnel" OR athletes OR wheelchair OR "repetitive hand")` | 7 |
| C | PubMed | `"carpal tunnel syndrome" AND (clinical practice guideline OR "Clinical Practice Guidelines") AND (physical therapy OR exercise)` | 20 (first 4 screened) |
| C | PubMed | `Wolny T[Author] AND "carpal tunnel syndrome" AND (neurodynamic OR manual therapy) AND randomized` | 8 (first 3 screened) |
| C | PubMed | `"carpal tunnel syndrome" AND yoga AND randomized` | 8 (first 3 screened) |
| C | PubMed | `"carpal tunnel" AND pressure AND (Gelberman[Author] OR Szabo[Author] OR Weiss ND[Author]) AND (wrist OR position)` | 16 (first 4 screened) |
| C | PubMed | `"carpal tunnel" AND (rehabilitation OR "return to work" OR "strengthening" OR "grip") AND (release OR decompression) AND (randomized controlled trial[Publication Type] OR Cochrane Database Syst Rev[Journal]) AND (rehabilitation OR exercise) AND ("early" OR "immobilisation" OR "splint")` | 14 (first 4 screened) |

## Evidence summary

### Guidelines and consensus

- **AAOS/ASSH 2024** ([Shapiro 2025](https://doi.org/10.5435/JAAOS-D-24-01179); secondary commentary in [Sadler 2025](https://www.aafp.org/pubs/afp/issues/2025/0900/practice-guidelines-carpal-tunnel-syndrome.html)). Exercise, manual therapy, massage, kinesiotaping, laser and shockwave, among others, "do not improve long-term patient reported outcomes" (Limited). No significant difference between non-operative techniques (Limited). CTS-6 can diagnose CTS in lieu of routine testing (Strong). Steroid injection gives no long-term improvement (Strong). After release, supervised therapy "should not be routinely prescribed" and immobilization "should not be used" (both Moderate). No association between high keyboard use and CTS (consensus). It gives no return-to-activity restrictions.
- **APTA 2026 revision** ([Erickson 2026](https://doi.org/10.2519/jospt.2026.0301)). The original could not be read. As reported by the [Physiotutors summary of the 2026 guideline](https://www.physiotutors.com/?p=29289) only: Grade B for a forearm-based orthosis near neutral at night; Grade C for a combined orthotic and stretching programme in mild to moderate CTS with no thenar atrophy and normal two-point discrimination; no clear recommendation on neural mobilisation because evidence conflicts; Grade F for explaining the pathology and collaboratively modifying aggravating activities. Check against the original before relying on these grades.
- **APTA 2019** ([Erickson 2019](https://doi.org/10.2519/jospt.2019.0301)). The Guideline Central summary of the final text confirms Grade B for a night orthosis, C for orthosis plus stretching, and D for neurodynamic mobilisation (conflicting evidence).
- **AAOS 2016, superseded** ([Graham 2016](https://doi.org/10.2106/JBJS.16.00719)). Physical activity or exercise was associated with lower risk of developing CTS (Moderate, observational). Immobilization improved outcomes (Strong). No added benefit of routine supervised therapy over home programmes after surgery (Moderate), and no eligible evidence comparing exercise with no exercise after surgery.
- **BOA, RCS and BSSH commissioning guide 2017** ([BOA 2017](https://boa.ac.uk/asset/DEF06E52-94D4-46B9-A482B7C4B9DED7F8)). Night splints in neutral first, "not to be over-relied on". "No more than two modalities of conservative treatment" to avoid delaying surgery. Refer after up to 12 weeks of non-response.
- **European HANDGUIDE** ([Huisstede 2014](https://doi.org/10.1016/j.apmr.2014.06.022)). 35 experts agreed patients "should always be instructed". Exercise is not named among suitable treatments in the abstract.
- **UK Delphi after release** ([Newington 2022](https://doi.org/10.1177/17589983221113870)). 33 panellists gave return times (see below). There was "no agreement over the utility of median nerve glides, sensory retraining, thumb motor control activities or the timing of any home exercises programme".
- **Occupational guidance** ([Franklin 2015](https://doi.org/10.1016/j.pmr.2015.04.003)). Conservative care aims to maintain employment, and surgical decompression can be highly effective, particularly if done early.
- **Diagnosis and severity** ([Rempel 1998](https://doi.org/10.2105/ajph.88.10.1447), [Pelosi 2022](https://doi.org/10.1016/j.clinph.2021.12.012), [Robinson 2026](https://doi.org/10.1002/mus.70384), [Cartwright 2012](https://doi.org/10.1002/mus.23389)). No perfect gold standard exists. Pelosi says electrodiagnosis should always be used to quantify severity. Robinson counts 12 grading systems and recommends the Bland scale, which needs nerve testing and so cannot be used from self-report. Ultrasound area is accurate at Level A. [Padua 2016](https://doi.org/10.1016/s1474-4422(16)30231-9) and the [Physiotutors diagnosis summary](https://www.physiotutors.com/research/carpal-tunnel-syndrome-diagnosis/) add that the best assessment approach is uncertain.
- **How guidelines work in practice.** Finnish release rates rose 7.5% yearly before a 2007 policy and 1.2% after it ([Hytönen 2026](https://doi.org/10.1177/23814683261477058)). Brazilian physical therapists aligned with guidelines only 46.2% of the time (SD 10.7) ([Fernandes 2026](https://doi.org/10.1111/jep.70575)). A critique of the Official Disability Guidelines surveyed 108 surgeons on 15 statements ([Gopalkrishnan 2022](https://doi.org/10.1097/JOM.0000000000002570)).

### Exercise and mobilisation: reviews

| Source | Studies | Result | Certainty |
|---|---|---:|---|
| [Page 2012](https://doi.org/10.1002/14651858.CD009899) | 16, n=741, not pooled | Neurodynamic or carpal mobilisation vs none, improvement RR 15.00 (95% CI 1.02-220.92; n=14). Gliding added to splint and activity modification, normal sensory latency RR 1.26 (0.69-2.30; n=26) | Very low |
| [Paraskevopoulos 2023](https://doi.org/10.1080/09593985.2022.2068097) | 25 articles | Pain SMD -2.36 (-4.31 to -0.41); function SMD -1.27 (-1.60 to -0.94). Authors still conclude no clinical benefits | Low to very low |
| [Núñez de Arenas-Arroyo 2021](https://doi.org/10.2519/jospt.2021.10533) | 22, n=1203 | Pain SMD -0.54 (-0.95 to -0.13); function SMD -0.35 (-0.61 to -0.09). No effect on severity, latency, grip or pinch | Very low to low |
| [Zaheer 2023](https://doi.org/10.3390/jcm12154888) | 12 RCTs, n=1003 | BCTQ SSS MD -1.20 (-1.72 to -0.67); FSS MD -1.06 (-1.53 to -0.60); grip not different | No GRADE reported |
| [Jiménez-Del-Barrio 2022](https://doi.org/10.1007/s00264-021-05272-2) | 6, n=401 | Manual therapy, soft-tissue and neurodynamic. Pain SMD -2.13 (-2.39 to -1.86), I-squared 96%; severity SMD -1.67 (-1.92 to -1.43); function SMD -0.89 (-1.08 to -0.70). Immediate effects | PEDro only |
| [Ballestero-Pérez 2017](https://doi.org/10.1016/j.jmpt.2016.10.004) | 13 trials | 2 favoured standard care and 1 a splint; 3 favoured gliding. "Might be a complementary option" | Narrative |
| [Kim 2015](https://doi.org/10.1589/jpts.27.2645) | 4 RCTs | Gliding plus conventional care beat conventional care alone. Gliding alone untested | Narrative |
| [Lim 2017](https://doi.org/10.1016/j.jht.2017.06.019) | 9 RCTs | SMDs 0.05 to 1.71; inconclusive per technique | No pooling |
| [Piazzini 2007](https://doi.org/10.1177/0269215507077294) | 33 RCTs | "Exercise therapy is not effective" (level 3), search to 2006 | Limited or conflicting |
| [O'Connor 2003](https://doi.org/10.1002/14651858.CD003219) | 21, n=884 | Yoga vs splint pain WMD -1.40 (-2.73 to -0.07; n=51). Exercise or chiropractic no benefit vs control | Single small trials |

Earlier neural gliding reviews and trials ([Medina McKeon 2008](https://doi.org/10.1123/jsr.17.3.324), [Akalin 2002](https://doi.org/10.1097/00002060-200202000-00006)) carry over from the first version of this record and were checked against their abstracts. [Cavalcante 2022](https://doi.org/10.1590/1516-3180.2021.1020.R2.10102022) rated more than 76% of 55 CTS treatment reviews low or very low on AMSTAR, which is a reason for caution with any favourable review. [Kumari 2026](https://doi.org/10.1016/j.jcot.2026.103608) reports low to very low certainty for night splinting with gliding in pregnancy and lactation, with no meta-analysis.

### Exercise: trials

| Trial | n | Comparison | Result |
|---|---:|---|---|
| [Lewis 2020](https://doi.org/10.1016/j.jphys.2020.03.007) | 105 | Group session of education, splinting and gliding vs control, people on a surgical waitlist | Surgery at 24 weeks 59% vs 80% (RD -0.21, -0.38 to -0.03). Improved at 24 weeks 24% vs 10% (RD 0.14, -0.01 to 0.29). No serious adverse effects |
| [Abdolrazaghi 2023](https://doi.org/10.1177/15589447211006857) | 80 | Gliding plus splint vs splint, mild CTS | No between-group differences at 6 weeks |
| [Arman 2024](https://doi.org/10.23736/S1973-9087.24.08432-6) | 33 | VR-supervised vs other arms, all splinted | Night pain better with VR (d=1.23), BCTQ no between-group difference. Compliance 90% |
| [Çağlar 2024](https://doi.org/10.12659/MSM.945704) | 44 | Splint plus exercise vs exercise | No between-group differences. Within-group grip fell with exercise alone |
| [Sim 2019](https://doi.org/10.1016/j.jht.2018.01.004) | 41 (56 wrists) | Orthosis vs orthosis plus gliding and ultrasound | No between-group difference. Within-group symptom change 0.53 (0.23-0.83) vs 0.48 (0.24-0.72) |
| [Sheereen 2022](https://doi.org/10.1155/2022/1975803) | 30 | Neurodynamic vs carpal bone mobilisation, both with tendon gliding | Neurodynamic better for conduction velocity and function. No control group |
| [Hamzeh 2021](https://doi.org/10.1016/j.jht.2020.07.005) | 51 randomised, 41 analysed | Neurodynamics plus exercise vs home exercise | Favoured neurodynamics: pain at 1 month (-1.93), FSS (-0.5) and Short DASH (-12.6) at 6 months. No patient needed surgery at 1 year |
| [Wolny 2019](https://doi.org/10.1177/0269215518805213) | 103 | Therapist neurodynamics vs no treatment | Pain 1.38 vs 5.46; severity 1.08 vs 2.87 (P<0.01). No grip difference. Immediate only |
| [Paquette 2021](https://doi.org/10.1016/j.jht.2019.12.012) | 30 | Home neuromobilization before surgery | Feasible, adherence 94%. Limited benefit alone |
| [Shem 2020](https://doi.org/10.1016/j.jht.2019.12.002) | 83 | Self-stretch vs sham, double-blind | Between-group result inconclusive. Only 36 completed |
| [Garfinkel 1998](https://doi.org/10.1001/jama.280.18.1601) | 42 | Yoga vs control offered a splint | Grip 162 to 187 mmHg (P=.009); pain 5.0 to 2.9 (P=.02). Authors call it preliminary |
| [Mabrouk 2026](https://doi.org/10.3389/fpain.2026.1792933) | 380 | Desensitization vs neurodynamics | Desensitization better, but d=6.11 and d=6.91 are implausibly large. Treat with caution |
| [Keskin 2020](https://doi.org/10.5455/JPMA.1846) | 33 | Same exercises, groups by EMG result, in pregnancy | No untreated comparator, so not efficacy evidence |
| [Schmid 2012](https://doi.org/10.1002/jor.22064) | 20 | Gliding vs night splint, MRI | Nerve signal fell about 11% in both at 1 week (p=0.03) |
| [Núñez-Cortés 2026](https://doi.org/10.1002/msc.70223) | 22 | Crossover, one session of elastic resistance per condition on 2 visits, dual task vs single task, after release | Added 9.7 (5.7-13.8) flexion and 12.0 (7.7-16.2) extension repetitions. Pain fell in both conditions |

### Other treatments, for context

- **Splinting** ([Karjalainen 2023](https://doi.org/10.1002/14651858.CD010003.pub2)): 29 trials, n=1937. "Insufficient evidence to conclude whether splinting benefits people with CTS." Short-term BCTQ SSS 0.37 points better (0.82 better to 0.08 worse; MCID 1). Splinting may add no benefit over exercises (low certainty). Adverse events 7/40 vs 0/40, none serious.
- **Surgery vs non-surgical care** ([Lusa 2024](https://doi.org/10.1002/14651858.CD001552.pub3)): clinical improvement RR 2.10 (1.04-4.24), but BCTQ changes below the MCID. 44% of the splint group were referred to surgery.
- **Ergonomics** ([O'Connor 2012](https://doi.org/10.1002/14651858.CD009600), [Mehta 2026](https://doi.org/10.1002/14651858.CD014644.pub2)): insufficient evidence for keyboards. Across arm, neck and shoulder complaints, ergonomic interventions "may have no meaningful effects" (pain SMD -0.23, -0.54 to 0.08). The second review is not CTS-specific.

### Risk factors

| Source | Finding |
|---|---|
| [Hassan 2022](https://doi.org/10.1002/hsr2.888) | Repetition HR 1.87 (1.42-2.46); force HR 1.84 (1.22-2.79). Pinch and vibration not significant. High computer use HR 0.28 (0.12-0.64) |
| [Barcenilla 2012](https://doi.org/10.1093/rheumatology/ker108) | Vibration OR 5.40 (3.14-9.31); force OR 4.23 (1.53-11.68); repetition OR 2.26 (1.73-2.94); wrist posture OR 4.73 (0.42-53.32), not significant with nerve-conduction-backed cases, though significant under the broader NIOSH definition |
| [Kozak 2015](https://doi.org/10.1186/s12891-015-0685-0) | Repetition and force best supported; vibration moderate; wrist posture low quality |
| [Gerger 2024](https://doi.org/10.1016/j.apergo.2023.104211) | Repetition, velocity and combined exposures raise risk, with no pooled estimate |
| [Palmer 2007](https://doi.org/10.1093/occmed/kql125) | Vibratory tools more than 2-fold. Keyboard work not importantly associated |
| [Nilsson 2017](https://doi.org/10.1371/journal.pone.0180795) | Hand-arm vibration OR 2.9 in the low risk-of-bias analysis |
| [Coenen 2019](https://doi.org/10.1136/oemed-2018-105553) | Screen work RR 1.11 (1.03-1.19) across neck and arm symptoms, not CTS-specific |
| [Huang 2024](https://doi.org/10.3390/diagnostics14192120) | Elite tug-of-war athletes: 33.3% definite and 37.0% subclinical CTS, n=27 analysed (abstract says 29 recruited), no concurrent control |
| [Marriott 2023](https://doi.org/10.1097/PHM.0000000000002167) | Wheelchair athletes: no association between training load and CTS |

### Mechanics

| Source | Population | Finding |
|---|---|---|
| [Gelberman 1981](https://doi.org/10.2106/00004623-198163030-00009) | CTS and controls | Pressure lowest near neutral: 32 mmHg neutral, 94 at 90 degrees flexion, 110 at 90 degrees extension in CTS |
| [Keir 2007](https://doi.org/10.1518/001872007779598127) | 37 healthy | 30 mmHg threshold at 32.7 degrees extension and 48.6 flexion; 25 mmHg at 26.6 and 37.7 |
| [Li and Jordan 2026](https://doi.org/10.1002/jor.70294) | 5 cadaver hands | Baseline set near 30 mmHg. Pressure rose beyond 20 degrees; 160.7 and 172.0 mmHg at 50 degrees flexion and extension. Not clinical evidence of harm |
| [Keir 1998b](https://doi.org/10.1016/S0363-5023(98)80007-5) | 14 healthy | Higher pressure with straight fingers than at 45 degrees of knuckle flexion, unloaded |
| [McGorry 2014](https://doi.org/10.1002/jor.22571) | 14 healthy | Grip raised pressure in extension; flexion often lowered it; little change without gripping |
| [Rempel 1997](https://doi.org/10.1002/jor.1100150315), [Keir 1998a](https://doi.org/10.1002/jor.1100160119) | Healthy | Fingertip load raises pressure. Pinch 49.7 vs press 33.8 mmHg at 15 N |
| [Keir 1997](https://doi.org/10.1016/S0363-5023(97)80119-0) | 8 cadaver wrists | Hydrostatic pressure highest in unloaded extension; contact force on the nerve highest in flexion |
| [Keir and Bach 2000](https://doi.org/10.1016/s0268-0033(99)00092-3), [Nadar 2022](https://doi.org/10.1016/j.jht.2022.03.003), [Cobb 1996](https://doi.org/10.1080/00140139608964437) | Cadaver and healthy | Flexor muscle entry was calculated, not measured. Lumbricals entered the tunnel in 79.4% of fingers at full flexion. Grip size and lumbricals both affected pressure |
| [Vignais 2016](https://doi.org/10.1615/CritRevBiomedEng.2017021073) | Review | Posture and fingertip force drive pressure through hydrostatic pressure and contact stress |
| [Schmid 2015](https://doi.org/10.1016/j.apergo.2014.08.020) | 21 patients | Ergonomic mouse devices changed wrist position but did not lower pressure |
| [Coppieters 2008](https://doi.org/10.1016/j.math.2006.12.008), [Coppieters 2009](https://doi.org/10.2519/jospt.2009.2913) | Cadaver and healthy | Tensioning strained the nerve 6.8% vs 0.8% for sliding. Sliding gave 10.2 mm excursion vs 1.8 mm |
| [Chang 2026](https://doi.org/10.1016/j.arrct.2026.100660) | 79 CTS hands, 88 healthy | Excursion rose with wrist extension and fell slightly with added thumb abduction |
| [Li S 2026](https://doi.org/10.7717/peerj.21724) | 25 healthy | Nerve area fell from 6.80 mm² to about 5.08-5.27 mm² during isometric torque at 50% of maximum, with recovery incomplete at 2 s |
| [Betancourt 2020](https://doi.org/10.1016/j.apmr.2020.05.008) | 32 adults, paraplegia and matched | Circuit training reduced area in larger nerves (-2.0 ± 1.5, P=.002) and did not change it in smaller ones |
| [Altinok 2004](https://doi.org/10.7863/jum.2004.23.10.1301) | 13 patients, 10 controls | Provocative exercise separated dynamic CTS from controls by nerve swelling (P<.0001) |

## What to avoid or modify

Nothing is a blanket "avoid". The [Physiotutors summary of the 2026 guideline](https://www.physiotutors.com/?p=29289) favours individual modification over avoiding hand use. All items are `modify`.

| Movement | Basis | Strength |
|---|---|---|
| Sustained or loaded near end-range wrist extension or flexion. Flat-palm push-ups, planks and front-rack holds are inferred examples | [Gelberman 1981](https://doi.org/10.2106/00004623-198163030-00009), [Keir 2007](https://doi.org/10.1518/001872007779598127), [Keir 1998b](https://doi.org/10.1016/S0363-5023(98)80007-5), [Li and Jordan 2026](https://doi.org/10.1002/jor.70294). Posture link significant only under the broader case definition in [Barcenilla 2012](https://doi.org/10.1093/rheumatology/ker108) | Biomechanical |
| Forceful, high-repetition hand work | [Hassan 2022](https://doi.org/10.1002/hsr2.888), [Barcenilla 2012](https://doi.org/10.1093/rheumatology/ker108), [Gerger 2024](https://doi.org/10.1016/j.apergo.2023.104211), [Kozak 2015](https://doi.org/10.1186/s12891-015-0685-0), plus [NHS](https://www.nhs.uk/conditions/carpal-tunnel-syndrome/) and [OrthoInfo](https://www.orthoinfo.org/en/diseases--conditions/carpal-tunnel-syndrome/) advice | Observational |
| Hard or sustained pinching and fingertip loading | [Rempel 1997](https://doi.org/10.1002/jor.1100150315), [Keir 1998a](https://doi.org/10.1002/jor.1100160119), [McGorry 2014](https://doi.org/10.1002/jor.22571). Pooled pinch not significant in [Hassan 2022](https://doi.org/10.1002/hsr2.888) | Biomechanical |
| Hard active gripping with the wrist bent back (new) | [McGorry 2014](https://doi.org/10.1002/jor.22571), [Keir and Bach 2000](https://doi.org/10.1016/s0268-0033(99)00092-3). Direction disputed by [Keir 1997](https://doi.org/10.1016/S0363-5023(97)80119-0) | Biomechanical |
| Hand-transmitted vibration | [Barcenilla 2012](https://doi.org/10.1093/rheumatology/ker108), [Nilsson 2017](https://doi.org/10.1371/journal.pone.0180795), [Palmer 2007](https://doi.org/10.1093/occmed/kql125). Not significant in [Hassan 2022](https://doi.org/10.1002/hsr2.888) | Observational |
| Strong nerve tensioning, preferring sliding | [Coppieters 2008](https://doi.org/10.1016/j.math.2006.12.008), [Coppieters 2009](https://doi.org/10.2519/jospt.2009.2913), [Sheereen 2022](https://doi.org/10.1155/2022/1975803). No clinical harm shown | Biomechanical |
| After release only: early repeated loading, lifting and vibration (new) | [Newington 2022](https://doi.org/10.1177/17589983221113870), [Shapiro 2025](https://doi.org/10.5435/JAAOS-D-24-01179) | Expert opinion |

**After carpal tunnel release**, expert timescales from [Newington 2022](https://doi.org/10.1177/17589983221113870):

| Activity | Expected return |
|---|---:|
| Driving | 5-14 days |
| Desk-based activities | 4-14 days |
| Exposure to dirt, dust, chemicals or fluids | 14-21 days |
| Repetitive gripping and releasing | 14-28 days |
| Unpredictable hand or wrist contact | 14-35 days |
| Repeated loading of hand and wrist, including sports training | 14-35 days |
| Hand-held vibrating tools | 21-42 days |
| Lifting or stabilising over 10 kg | 21-42 days |

Moving the fingers, thumb and wrist regularly reached 97% agreement. Timescales may lengthen with complications or bilateral surgery.

## Exercises and exact doses

No dose below is proven as a standalone treatment. Primary doses are the JSON `dose`; the others are `alternativeDoses`.

| Exercise | Sets x reps | Hold | Frequency | Source | Result on that dose |
|---|---|---|---|---|---|
| Tendon gliding (primary) | 3 x 5 | 7 s | 3 times a week for 3 weeks, 1 min rest between sets | [Sheereen 2022](https://doi.org/10.1155/2022/1975803) | No control group; with manual therapy |
| Tendon gliding | 1 x 10 | 5 s | 3 sessions a day for 6 weeks | [Abdolrazaghi 2023](https://doi.org/10.1177/15589447211006857) | No added benefit over splint |
| Tendon gliding | 2 x 10 | 7 s | Twice daily for 8 weeks, then at least daily to week 16, 15 s rest | [Arman 2024](https://doi.org/10.23736/S1973-9087.24.08432-6) | No between-group BCTQ difference; all splinted |
| Tendon gliding | 20 reps | Not reported | Twice daily plus supervised 3 days a week | [Çağlar 2024](https://doi.org/10.12659/MSM.945704) | No difference from added splint |
| Tendon gliding | 5 reps, sets not stated | 7 s | 3 times daily for 2 weeks | [Bartkowiak 2019](https://doi.org/10.4103/ortho.ijortho_45_17) | Non-randomised; with laser or ultrasound |
| Median nerve gliding (primary, changed) | 2 x 10 | 7 s | Twice daily for 8 weeks, then at least daily to week 16, 15 s rest | [Arman 2024](https://doi.org/10.23736/S1973-9087.24.08432-6) | No between-group BCTQ difference; all splinted |
| Median nerve gliding | 1 x 5 (sets inferred) | 7 s | 3 times daily for 2 weeks | [Bartkowiak 2019](https://doi.org/10.4103/ortho.ijortho_45_17) | Non-randomised; earlier primary |
| Median nerve gliding | 1 x 10 | 5 s | 3 sessions a day for 6 weeks | [Abdolrazaghi 2023](https://doi.org/10.1177/15589447211006857) | No added benefit over splint |
| Median nerve gliding | 3 x 10 | Not reported | Daily, in pregnancy | [Keskin 2020](https://doi.org/10.5455/JPMA.1846) | No comparator |
| Wrist flexor and extensor stretch | 3 sets | 15-30 s (`holdSeconds` 30) | At least 6-8 weeks; sessions not stated | [CSP](https://www.csp.org.uk/public-patient/rehabilitation-exercises/carpal-tunnel-syndrome) | Patient information, no trial |
| Active wrist bend, forward and back | 3 x 10 | 5 s | At least 6-8 weeks; sessions not stated | [CSP](https://www.csp.org.uk/public-patient/rehabilitation-exercises/carpal-tunnel-syndrome) | Patient information |
| Finger bend at the middle joints | 3 x 10 | 5 s | At least 6-8 weeks; sessions not stated | [CSP](https://www.csp.org.uk/public-patient/rehabilitation-exercises/carpal-tunnel-syndrome) | Patient information |
| Light palm-down wrist extension (`catalog:wrist-extension`) | 3 x 10 | None | At least 6-8 weeks; tin-of-beans load | [CSP](https://www.csp.org.uk/public-patient/rehabilitation-exercises/carpal-tunnel-syndrome) | Patient information |
| Rubber ball squeeze | 3 x 10 | 5 s | At least 6-8 weeks; sessions not stated | [CSP](https://www.csp.org.uk/public-patient/rehabilitation-exercises/carpal-tunnel-syndrome) | Patient information; conflicts with pressure data |

**Therapist-delivered doses, not candidates:**

- Sliding and tensioning neurodynamics: 3 series of 60 repetitions, 15 s between series, twice weekly for 20 sessions ([Wolny 2019](https://doi.org/10.1177/0269215518805213), as tabulated in [Jiménez-Del-Barrio 2022](https://doi.org/10.1007/s00264-021-05272-2)).
- Neurodynamic technique: 2 sets of 5 minutes, 1 min rest, 3 times a week for 3 weeks. Carpal bone mobilisation: 3 sets of 30 repetitions on the same schedule ([Sheereen 2022](https://doi.org/10.1155/2022/1975803)).

**Studied but not adopted as candidates:**

- Self-carpal-ligament stretch, 4 times a day for 6 weeks ([Shem 2020](https://doi.org/10.1016/j.jht.2019.12.002)). The technique is not in the abstract and the between-group result was inconclusive.
- Stretching 6 times a day with a sleep orthosis (Baker trial, as described in the [APTA 2019 public-comment draft, marked "Do Not Cite"](https://www.orthopt.org/uploads/content_files/files/CTS%20CPG%20for%20reviewDRAFT.pdf)). No arm was superior, and 25.5% progressed to surgery at 24 weeks.
- Yoga, 11 postures twice weekly for 8 weeks ([Garfinkel 1998](https://doi.org/10.1001/jama.280.18.1601)). The postures are not specified in the abstract.
- Elastic-band wrist flexion and extension, one set to failure at Borg 3 ([Núñez-Cortés 2026](https://doi.org/10.1002/msc.70223)). This was one session per condition after surgery, with no training effect measured.

**Dosing cautions:**

- Stop or reduce if numbness or tingling increases. One trial adjusted neurodynamic speed and amplitude so that it produced no pain ([Sheereen 2022](https://doi.org/10.1155/2022/1975803)).
- The final thumb-stretch step of nerve gliding may be omitted, per a cross-sectional study with no outcome data ([Chang 2026](https://doi.org/10.1016/j.arrct.2026.100660)).
- A full fist adds tunnel content in healthy hands ([Nadar 2022](https://doi.org/10.1016/j.jht.2022.03.003)). No data link this to symptoms.
- Stretching support applies to mild to moderate CTS with no thenar atrophy and normal two-point discrimination, combined with an orthosis ([Erickson 2019](https://doi.org/10.2519/jospt.2019.0301)).
- A catalog dumbbell needs a grip, and grip raised pressure in extension in healthy wrists at low loads ([McGorry 2014](https://doi.org/10.1002/jor.22571)). This is why wrist extension stays at a very light load.
- CSP's right-angle wrist bends and ball squeezing conflict with pressure data ([Gelberman 1981](https://doi.org/10.2106/00004623-198163030-00009), [McGorry 2014](https://doi.org/10.1002/jor.22571)). A smaller range or softer effort is our untested inference.

The catalog's `wrist-extension` is the only true match. `wrist-curl` is barbell loaded flexion, so it was not used. Hangs and grip-heavy lifts are not candidates.

## Red flags

- **Sudden, rapidly worsening pain and altered sensation, especially after a fracture, bleeding or high-pressure injury:** stop and seek emergency care the same day ([Christodoulou 2025](https://doi.org/10.13107/jocr.2025.v15.i08.5968), [iatroX summary](https://iatrox.com/guidelines/carpal-tunnel-syndrome)).
- **Hand or thumb weakness, or wasting at the base of the thumb:** stop these exercises and get prompt assessment ([CSP](https://www.csp.org.uk/public-patient/rehabilitation-exercises/carpal-tunnel-syndrome), [BOA 2017](https://boa.ac.uk/asset/DEF06E52-94D4-46B9-A482B7C4B9DED7F8), [OrthoInfo](https://www.orthoinfo.org/en/diseases--conditions/carpal-tunnel-syndrome/), [Physiotutors summary of the 2026 guideline](https://www.physiotutors.com/?p=29289), [iatroX summary](https://iatrox.com/guidelines/carpal-tunnel-syndrome)).
- **Constant numbness or progressive sensory loss:** get prompt assessment ([iatroX summary](https://iatrox.com/guidelines/carpal-tunnel-syndrome), [Physiotutors summary of the 2026 guideline](https://www.physiotutors.com/?p=29289)).
- **Several nerve areas affected, or neck pain, walking changes or brisk reflexes:** stop the programme and get medical evaluation ([iatroX summary](https://iatrox.com/guidelines/carpal-tunnel-syndrome)). A UK guide lists neurological disease and cervical nerve root entrapment for urgent referral within 2 weeks ([BOA 2017](https://boa.ac.uk/asset/DEF06E52-94D4-46B9-A482B7C4B9DED7F8)).
- **Tingling or numbness that began after an injury, or a suspected fracture or tumour (new):** stop and get medical assessment ([BOA 2017](https://boa.ac.uk/asset/DEF06E52-94D4-46B9-A482B7C4B9DED7F8)).
- **Hand symptoms with active inflammatory joint disease such as gout or rheumatoid arthritis, or peripheral limb ischaemia (new):** stop and seek urgent referral, within 2 weeks ([BOA 2017](https://boa.ac.uk/asset/DEF06E52-94D4-46B9-A482B7C4B9DED7F8)).
- **Worsening or persistent symptoms, daily symptoms with frequent night waking, or no response to up to 12 weeks of non-surgical treatment:** see a GP or clinician ([NHS](https://www.nhs.uk/conditions/carpal-tunnel-syndrome/), [BOA 2017](https://boa.ac.uk/asset/DEF06E52-94D4-46B9-A482B7C4B9DED7F8)).

## Contradictions and limitations

**Contradictions**

1. **Neurodynamic benefit.** [Zaheer 2023](https://doi.org/10.3390/jcm12154888) and [Jiménez-Del-Barrio 2022](https://doi.org/10.1007/s00264-021-05272-2) (manual therapy, not only neurodynamics) report benefit, [Paraskevopoulos 2023](https://doi.org/10.1080/09593985.2022.2068097) concludes no clinical benefit from statistically positive pooled data, and [Núñez de Arenas-Arroyo 2021](https://doi.org/10.2519/jospt.2021.10533) finds small short-term effects. Pooled effects lean on therapist-delivered trials with no-treatment controls, such as [Wolny 2019](https://doi.org/10.1177/0269215518805213).
2. **Gliding as an add-on.** [Kim 2015](https://doi.org/10.1589/jpts.27.2645) reports benefit over conventional care. [Abdolrazaghi 2023](https://doi.org/10.1177/15589447211006857), [Sim 2019](https://doi.org/10.1016/j.jht.2018.01.004) and [Arman 2024](https://doi.org/10.23736/S1973-9087.24.08432-6) show none over splinting on the main symptom scale.
3. **Neurodynamics vs desensitization.** [Mabrouk 2026](https://doi.org/10.3389/fpain.2026.1792933) favours desensitization, but its effect sizes are implausibly large and its BCTQ values conflict between table and text. It does not change this record.
4. **Splinting.** [Graham 2016](https://doi.org/10.2106/JBJS.16.00719) gave a Strong recommendation and [Erickson 2019](https://doi.org/10.2519/jospt.2019.0301) Grade B, while [Karjalainen 2023](https://doi.org/10.1002/14651858.CD010003.pub2) finds insufficient evidence.
5. **Diagnosis.** [Shapiro 2025](https://doi.org/10.5435/JAAOS-D-24-01179) accepts CTS-6 in lieu of routine testing. [Pelosi 2022](https://doi.org/10.1016/j.clinph.2021.12.012) says electrodiagnosis should always be done to quantify severity.
6. **Keyboards.** [Shapiro 2025](https://doi.org/10.5435/JAAOS-D-24-01179) finds no association, as workgroup consensus from an absence of reliable evidence. [Hassan 2022](https://doi.org/10.1002/hsr2.888) finds HR 0.28 from one study with a possibly high-risk reference group. [Coenen 2019](https://doi.org/10.1136/oemed-2018-105553) finds a small rise across symptoms that are not CTS-specific.
7. **Vibration.** [Barcenilla 2012](https://doi.org/10.1093/rheumatology/ker108), [Nilsson 2017](https://doi.org/10.1371/journal.pone.0180795) and [Palmer 2007](https://doi.org/10.1093/occmed/kql125) find increased risk. [Hassan 2022](https://doi.org/10.1002/hsr2.888) does not.
8. **Wrist direction.** [McGorry 2014](https://doi.org/10.1002/jor.22571) found flexion often lowered in vivo pressure. [Keir 1997](https://doi.org/10.1016/S0363-5023(97)80119-0) and [Gelberman 1981](https://doi.org/10.2106/00004623-198163030-00009) point the other way.
9. **Exercise and nerve swelling.** [Altinok 2004](https://doi.org/10.7863/jum.2004.23.10.1301) found swelling in dynamic CTS. [Betancourt 2020](https://doi.org/10.1016/j.apmr.2020.05.008) found area fell in larger nerves, in a different population.
10. **Patient advice vs pressure data.** [CSP](https://www.csp.org.uk/public-patient/rehabilitation-exercises/carpal-tunnel-syndrome) recommends right-angle bends and squeezing, which the pressure studies caution against.

**Limitations**

- **Guideline access.** The final 2019 guideline is known through a summary and a "Do Not Cite" draft. The 2026 grades are second-hand from a commercial educational site. AAOS 2024 was read as its published summary by keyword extraction, and its 2026 erratum was not read.
- **Paper access.** Most new sources are abstract only. [Page 2012](https://doi.org/10.1002/14651858.CD009899) and [Karjalainen 2023](https://doi.org/10.1002/14651858.CD010003.pub2) were read only in part. Comments published on [Wolny 2019](https://doi.org/10.1177/0269215518805213), [Shem 2020](https://doi.org/10.1016/j.jht.2019.12.002) and [Jiménez-Del-Barrio 2022](https://doi.org/10.1007/s00264-021-05272-2) were not read.
- **Indirectness.** Pressure studies are mostly in healthy volunteers at low loads. Risk data are occupational and may not transfer to training. No study measured gym lifts in CTS.
- **Harms.** Only two Cochrane studies measured adverse effects. Several recent trials do not report them.
- **Red flag sources.** Acute CTS, constant numbness and spinal cord signs still rest on a secondary educational summary and a small case series. The AAOS summary that was read does not supply them, so they need clinician review.
- **Self-identified users.** Most recommendations apply to mild to moderate CTS, and severity cannot be graded from self-report ([Robinson 2026](https://doi.org/10.1002/mus.70384)).
- **Catalog match.** `catalog:wrist-extension` metadata does not state palm-down, and the entry is an unreviewed draft.
- **Stop wording.** Stop-exercise wording in some red flags is a cautious app instruction. The sources call for assessment.

## Verification

An independent checker opened all 86 sources on 2026-11-01, as a page, PubMed record or full text according to each source's access label. Identifiers were compared with PubMed and links were tested.

**Items removed:** none. Every avoid-or-modify item, dose and red flag traced to its cited source.

**Corrected:**

- Barcenilla 2012 wrist posture: was given only as not significant; the abstract also reports a significant association under the NIOSH case definition. Added to the rationale and the .md.
- Hassan 2022 HR 0.28: reference group corrected from 'high-force' to 'high-risk', the paper's wording.
- Zaheer 2023: 'mostly therapist-delivered' corrected, since the full text says a trained physiotherapist applied the techniques in all included studies. No GRADE assessment appears in the full text, not only the abstract.
- Jiménez-Del-Barrio 2022: labelled as manual therapy including soft-tissue techniques, not neurodynamic work alone.
- Núñez-Cortés 2026: 'one session' corrected to a crossover with one session per condition across 2 visits.
- Huang 2024: n=27 kept from the paper's results, with a note that its abstract says 29 recruited.
- Keir and Bach 2000: flexor entry into the tunnel labelled as calculated, not measured.
- Page 2012 RR 1.26 (.md): outcome corrected from general improvement to normal sensory latency, in a trial that also used activity modification.
- Sim 2019 (.md): 0.53 and 0.48 labelled as within-group changes.
- Li and Jordan 2026 (.md): added that baseline pressure was set near 30 mmHg.
- Links: 13 europepmc.org/article URLs returned HTTP 403 when fetched, so their JSON url now points to the matching PubMed record.
- PMCIDs added from PubMed: bartkowiak_2019, kozak_2015, christodoulou_2025.
- Access labels: all 14 not_reconfirmed entries replaced with what was actually read in this check.
- Sadler 2025 (.md): link changed to the AAFP page given in the JSON.

**Could not be verified:**

- JOSPT 2019 and 2026 final guidelines: only PubMed records, Guideline Central, Physiotutors and the 'Do Not Cite' draft could be read. The grades are second-hand.
- AAOS/ASSH 2024: only the published summary was read, and only the title of its 2026 erratum (PMID 42825588), which has no abstract. The erratum's content could change a statement here.
- iatroX red flag wording: iatroX tags it to AAOS, but it does not appear in the AAOS summary read, so it could not be traced to the original.
- Abstract-only sources were checked against the abstract alone; their methods and tables were not read.
- Sadler 2025: free excerpt only, and PubMed has no abstract.
- catalog:wrist-extension match and the stop-exercise wording in some red flags are app decisions, not source claims, so they could not be confirmed from any paper.

## References

1. [Abdolrazaghi HA, Khansari M, Mirshahi M, Ahmadi Pishkuhi M. Effectiveness of Tendon and Nerve Gliding Exercises in the Treatment of Patients With Mild Idiopathic Carpal Tunnel Syndrome: A Randomized Controlled Trial. Hand (N Y). 2023;18:222-229.](https://doi.org/10.1177/15589447211006857) Access: full_text.
2. [Akalin E, El O, Peker O, et al. Treatment of carpal tunnel syndrome with nerve and tendon gliding exercises. Am J Phys Med Rehabil. 2002;81:108-113. Abstract read.](https://doi.org/10.1097/00002060-200202000-00006) Access: abstract_only.
3. [Altinok MT, Baysal O, Karakas HM, Firat AK. Sonographic evaluation of the carpal tunnel after provocative exercises. J Ultrasound Med. 2004;23:1301-1306.](https://doi.org/10.7863/jum.2004.23.10.1301) Access: abstract_only.
4. [Arman S, Menekseoglu AK, Sezgin B, Ozgur B, Capan N, Oral A. The effects of virtual reality-mediated tendon and nerve gliding exercises in the conservative management of carpal tunnel syndrome: a double-blind randomized placebo controlled trial. Eur J Phys Rehabil Med. 2024;60:458-469.](https://doi.org/10.23736/S1973-9087.24.08432-6) Access: full_text. The final 6,673 characters of the page were not read.
5. [Ballestero-Perez R, Plaza-Manzano G, Urraca-Gesto A, et al. Effectiveness of Nerve Gliding Exercises on Carpal Tunnel Syndrome: A Systematic Review. J Manipulative Physiol Ther. 2017;40:50-59. Abstract read.](https://doi.org/10.1016/j.jmpt.2016.10.004) Access: abstract_only.
6. [Barcenilla A, March LM, Chen JS, Sambrook PN. Carpal tunnel syndrome and its relationship to occupation: a meta-analysis. Rheumatology (Oxford). 2012;51:250-261.](https://doi.org/10.1093/rheumatology/ker108) Access: abstract_only.
7. [Bartkowiak Z, Eliks M, Zgorzalewicz-Stachowiak M, Romanowski L. The Effects of Nerve and Tendon Gliding Exercises Combined with Low-level Laser or Ultrasound Therapy in Carpal Tunnel Syndrome. Indian J Orthop. 2019;53:347-352.](https://doi.org/10.4103/ortho.ijortho_45_17) Access: full_text. Abstract and targeted full-text passages on dose and design were read.
8. [Betancourt L, Cowan RE, Chang A, Irwin R. Case-Control Study of Ultrasound Evaluation of Acute Median Nerve Response to Upper Extremity Circuit Training in Spinal Cord Injury. Arch Phys Med Rehabil. 2020;101:1898-1905.](https://doi.org/10.1016/j.apmr.2020.05.008) Access: abstract_only.
9. [British Orthopaedic Association, Royal College of Surgeons of England, BSSH. Commissioning guide: Treatment of carpal and cubital tunnel syndromes. Version 2.1, November 2017; evidence search January 2016.](https://boa.ac.uk/asset/DEF06E52-94D4-46B9-A482B7C4B9DED7F8) Access: full_text. Targeted passages of the extracted text were read, not every page.
10. [Cartwright MS, Hobson-Webb LD, Boon AJ, et al. Evidence-based guideline: neuromuscular ultrasound for the diagnosis of carpal tunnel syndrome. Muscle Nerve. 2012;46(2):287-293.](https://doi.org/10.1002/mus.23389) Access: abstract_only.
11. [Cavalcante MC, Moraes VY, Osés GL, Nakachima LR, Belloti JC. Quality analysis of prior systematic reviews of carpal tunnel syndrome: an overview of the literature. Sao Paulo Med J. 2022;141(5):e20211020.](https://doi.org/10.1590/1516-3180.2021.1020.R2.10102022) Access: abstract_only.
12. [Chang YW, Chen CJ, Wang YW, Chiu V, Horng YS. Thumb Positioning Modulates Median Nerve Mobility and Morphology During Postural Changes in Carpal Tunnel Syndrome: Implications for Nerve Gliding Exercises. Arch Rehabil Res Clin Transl. 2026;8:100660.](https://doi.org/10.1016/j.arrct.2026.100660) Access: full_text.
13. [Christodoulou P, Osarumwense D, Papadopoulos A. Acute Carpal Tunnel Syndrome: Is It Only Wrist Fractures? A Case Series Report and Literature Review. J Orthop Case Rep. 2025;15:275-279.](https://doi.org/10.13107/jocr.2025.v15.i08.5968) Access: full_text. Abstract and targeted full-text passages were read.
14. [Cobb TK, Cooney WP, An KN. Aetiology of work-related carpal tunnel syndrome: the role of lumbrical muscles and tool size on carpal tunnel pressures. Ergonomics. 1996;39:103-107.](https://doi.org/10.1080/00140139608964437) Access: abstract_only.
15. [Coenen P, van der Molen HF, Burdorf A, et al. Associations of screen work with neck and upper extremity symptoms: a systematic review with meta-analysis. Occup Environ Med. 2019;76(7):502-509.](https://doi.org/10.1136/oemed-2018-105553) Access: abstract_only.
16. [Coppieters MW, Butler DS. Do 'sliders' slide and 'tensioners' tension? An analysis of neurodynamic techniques and considerations regarding their application. Man Ther. 2008;13:213-221. Abstract read.](https://doi.org/10.1016/j.math.2006.12.008) Access: abstract_only.
17. [Coppieters MW, Hough AD, Dilley A. Different nerve-gliding exercises induce different magnitudes of median nerve longitudinal excursion: an in vivo study using dynamic ultrasound imaging. J Orthop Sports Phys Ther. 2009;39:164-171.](https://doi.org/10.2519/jospt.2009.2913) Access: abstract_only.
18. [Chartered Society of Physiotherapy. Carpal tunnel syndrome rehabilitation exercises (patient information).](https://www.csp.org.uk/public-patient/rehabilitation-exercises/carpal-tunnel-syndrome) Access: full_text.
19. [Erickson M, Lawrence M, Jansen CWS, Coker D, Amadio P, Cleary C. Hand Pain and Sensory Deficits: Carpal Tunnel Syndrome. J Orthop Sports Phys Ther. 2019;49(5):CPG1-CPG85. The final text returned HTTP 403 and was not read. What was read: the Guideline Central summary of the final text, and the APTA public-comment draft, which is marked 'Do Not Cite'.](https://doi.org/10.2519/jospt.2019.0301) Access: registry_or_summary. Read: [Guideline Central summary](https://www.guidelinecentral.com/guideline/8396) and [public-comment draft, full text, marked "Do Not Cite"](https://www.orthopt.org/uploads/content_files/files/CTS%20CPG%20for%20reviewDRAFT.pdf).
20. [Erickson M, Lawrence M, Lazinski MJ, Scott K, Martin RL. Hand Pain and Sensory Deficits: Carpal Tunnel Syndrome: Revision 2026. J Orthop Sports Phys Ther. 2026;56(4):CPG1-CPG79.](https://doi.org/10.2519/jospt.2026.0301) Access: registry_or_summary. Grades read only through the Physiotutors summary.
21. [Fernandes LFRM, Cardoso BTA, Paranhos DB, et al. Clinical Decision-Making and Guideline Alignment Among Brazilian Physical Therapists: Evidence From Standardized Carpal Tunnel Syndrome Vignettes. J Eval Clin Pract. 2026;32:e70575.](https://doi.org/10.1111/jep.70575) Access: full_text. Results and conclusion passages were read.
22. [Franklin GM, Friedman AS. Work-Related Carpal Tunnel Syndrome: Diagnosis and Treatment Guideline. Phys Med Rehabil Clin N Am. 2015;26(3):523-537.](https://doi.org/10.1016/j.pmr.2015.04.003) Access: abstract_only.
23. [Garfinkel MS, Singhal A, Katz WA, Allan DA, Reshetar R, Schumacher HR Jr. Yoga-based intervention for carpal tunnel syndrome: a randomized trial. JAMA. 1998;280(18):1601-1603.](https://doi.org/10.1001/jama.280.18.1601) Access: abstract_only.
24. [Gelberman RH, Hergenroeder PT, Hargens AR, Lundborg GN, Akeson WH. The carpal tunnel syndrome. A study of carpal canal pressures. J Bone Joint Surg Am. 1981;63:380-383. Abstract read.](https://doi.org/10.2106/00004623-198163030-00009) Access: abstract_only.
25. [Gerger H, Macri EM, Jackson JA, et al. Physical and psychosocial work-related exposures and the incidence of carpal tunnel syndrome: A systematic review of prospective studies. Appl Ergon. 2024;117:104211.](https://doi.org/10.1016/j.apergo.2023.104211) Access: abstract_only.
26. [Gopalkrishnan S, Ramachandran S, Ring D, Melhorn JM, Crijns TJ. Potential Misinformation in the Official Disability Guidelines About the Diagnosis and Treatment of Carpal Tunnel Syndrome. J Occup Environ Med. 2022;64(7):e424-e430.](https://doi.org/10.1097/JOM.0000000000002570) Access: abstract_only.
27. [Graham B, Peljovich AE, Afra R, et al. The American Academy of Orthopaedic Surgeons Evidence-Based Clinical Practice Guideline on: Management of Carpal Tunnel Syndrome. J Bone Joint Surg Am. 2016;98(20):1750-1754. Full guideline PDF from aaos.org. Superseded by the 2024 guideline.](https://doi.org/10.2106/JBJS.16.00719) Access: full_text. The 986-page AAOS PDF was term-searched and its recommendation statements read. The evidence tables were not read. Read: [full AAOS PDF](https://www.aaos.org/globalassets/quality-and-practice-resources/carpal-tunnel/cts_cpg_4-25-19.pdf).
28. [Hamzeh H, Madi M, Alghwiri AA, Hawamdeh Z. The long-term effect of neurodynamics vs exercise therapy on pain and function in people with carpal tunnel syndrome: a randomized parallel-group clinical trial. J Hand Ther. 2021;34:521-530.](https://doi.org/10.1016/j.jht.2020.07.005) Access: abstract_only.
29. [Hassan A, Beumer A, Kuijer PPFM, van der Molen HF. Work-relatedness of carpal tunnel syndrome: systematic review including meta-analysis and GRADE. Health Sci Rep. 2022;5(6):e888.](https://doi.org/10.1002/hsr2.888) Access: full_text.
30. [Huang CH, Liu KC, Cheng JW, Hsu SC, Chen CK. Carpal Tunnel Syndrome in Elite Female Tug-of-War Athletes: Prevalence and Risk Factor Analysis. Diagnostics (Basel). 2024;14.](https://doi.org/10.3390/diagnostics14192120) Access: full_text.
31. [Huisstede BM, Fridén J, Coert JH, Hoogvliet P; European HANDGUIDE Group. Carpal tunnel syndrome: hand surgeons, hand therapists, and physical medicine and rehabilitation physicians agree on a multidisciplinary treatment guideline: results from the European HANDGUIDE Study. Arch Phys Med Rehabil. 2014;95(12):2253-2263.](https://doi.org/10.1016/j.apmr.2014.06.022) Access: abstract_only.
32. [Hytönen M, Savolainen A, Reito A, et al. Guidelines and Policies as Levers in Managing Carpal Tunnel Release Demand. MDM Policy Pract. 2026;11(2):23814683261477058.](https://doi.org/10.1177/23814683261477058) Access: abstract_only.
33. [iatroX educational summary of AAOS, Management of Carpal Tunnel Syndrome: Evidence-Based Clinical Practice Guideline (2024). The original guideline was not read.](https://iatrox.com/guidelines/carpal-tunnel-syndrome) Access: registry_or_summary.
34. [Jiménez-Del-Barrio S, Cadellans-Arróniz A, Ceballos-Laita L, et al. The effectiveness of manual therapy on pain, physical function, and nerve conduction studies in carpal tunnel syndrome patients: a systematic review and meta-analysis. Int Orthop. 2022;46(2):301-312.](https://doi.org/10.1007/s00264-021-05272-2) Access: full_text. Three published comments on the paper were not read.
35. [Karjalainen TV, Lusa V, Page MJ, O'Connor D, Massy-Westropp N, Peters SE. Splinting for carpal tunnel syndrome. Cochrane Database Syst Rev. 2023;2(2):CD010003.](https://doi.org/10.1002/14651858.CD010003.pub2) Access: full_text. Partial: the first 100,000 of about 553,000 characters of the PMC page.
36. [Keir PJ, Wells RP, Ranney DA, Lavery W. The effects of tendon load and posture on carpal tunnel pressure. J Hand Surg Am. 1997;22:628-634.](https://doi.org/10.1016/S0363-5023(97)80119-0) Access: abstract_only.
37. [Keir PJ, Bach JM, Rempel DM. Fingertip loading and carpal tunnel pressure: differences between a pinching and a pressing task. J Orthop Res. 1998;16:112-115. Abstract read.](https://doi.org/10.1002/jor.1100160119) Access: abstract_only.
38. [Keir PJ, Bach JM, Rempel DM. Effects of finger posture on carpal tunnel pressure during wrist motion. J Hand Surg Am. 1998;23:1004-1009.](https://doi.org/10.1016/S0363-5023(98)80007-5) Access: abstract_only.
39. [Keir PJ, Bach JM, Hudes M, Rempel DM. Guidelines for wrist posture based on carpal tunnel pressure thresholds. Hum Factors. 2007;49:88-99. Abstract read.](https://doi.org/10.1518/001872007779598127) Access: abstract_only.
40. [Keir PJ, Bach JM. Flexor muscle incursion into the carpal tunnel: a mechanism for increased carpal tunnel pressure? Clin Biomech (Bristol). 2000;15:301-305.](https://doi.org/10.1016/s0268-0033(99)00092-3) Access: abstract_only.
41. [Keskin Y, Kilic G, Taspinar O, Posul SO, Halac G, Eren F. Effectiveness of home exercise in pregnant women with carpal tunnel syndrome: randomized control trial. J Pak Med Assoc. 2020;70:202-207.](https://doi.org/10.5455/JPMA.1846) Access: abstract_only. Labelled randomised, but the abstract describes groups defined by EMG result and the same exercises in both, so it is typed here as non-randomised.
42. [Kim SD. Efficacy of tendon and nerve gliding exercises for carpal tunnel syndrome: a systematic review of randomized controlled trials. J Phys Ther Sci. 2015;27:2645-2648.](https://doi.org/10.1589/jpts.27.2645) Access: abstract_only.
43. [Kozak A, Schedlbauer G, Wirth T, Euler U, Westermann C, Nienhaus A. Association between work-related biomechanical risk factors and the occurrence of carpal tunnel syndrome: an overview of systematic reviews and a meta-analysis of current research. BMC Musculoskelet Disord. 2015;16:231.](https://doi.org/10.1186/s12891-015-0685-0) Access: abstract_only.
44. [Kumari O, Mishra R, Singh V, Gowda AKS, Harisha R. Conservative management of carpal tunnel syndrome during pregnancy and lactation: A systematic review of mixed study designs. J Clin Orthop Trauma. 2026;82:103608.](https://doi.org/10.1016/j.jcot.2026.103608) Access: abstract_only.
45. [Lewis KJ, Coppieters MW, Ross L, Hughes I, Vicenzino B, Schmid AB. Group education, night splinting and home exercises reduce conversion to surgery for carpal tunnel syndrome: a multicentre randomised trial. J Physiother. 2020;66:97-104.](https://doi.org/10.1016/j.jphys.2020.03.007) Access: abstract_only.
46. [Li ZM, Jordan D. Change in Carpal Tunnel Pressure With Dynamic Wrist Flexion/Extension and Radioulnar Deviation: A Cadaveric Study. J Orthop Res. 2026;44(10):e70294.](https://doi.org/10.1002/jor.70294) Access: abstract_only.
47. [Li S, Kociolek AM, Loh PY. Characterizing median nerve deformation and recovery processes in response to repeated isometric wrist torque exertions. PeerJ. 2026;14:e21724.](https://doi.org/10.7717/peerj.21724) Access: full_text.
48. [Lim YH, Chee DY, Girdler S, Lee HC. Median nerve mobilization techniques in the treatment of carpal tunnel syndrome: A systematic review. J Hand Ther. 2017;30(4):397-406.](https://doi.org/10.1016/j.jht.2017.06.019) Access: abstract_only.
49. [Lusa V, Karjalainen TV, Pääkkönen M, Rajamäki TJ, Jaatinen K. Surgical versus non-surgical treatment for carpal tunnel syndrome. Cochrane Database Syst Rev. 2024;1(1):CD001552.](https://doi.org/10.1002/14651858.CD001552.pub3) Access: abstract_only.
50. [Mabrouk MI, Abdalbary SA, El-Shaarawy EAA, et al. Neurodynamic techniques vs. desensitization maneuvers in the manual therapy management of moderate carpal tunnel syndrome among women: randomized clinical trial with 1-year follow-up. Front Pain Res (Lausanne). 2026;7:1792933.](https://doi.org/10.3389/fpain.2026.1792933) Access: full_text.
51. [Marriott C, Montgomery K, Whelan A. Carpal Tunnel Syndrome in Athletes Who Compete in Wheelchair Sport: A Scoping Review. Am J Phys Med Rehabil. 2023;102:1116-1121.](https://doi.org/10.1097/PHM.0000000000002167) Access: abstract_only.
52. [McGorry RW, Fallentin N, Andersen JH, Keir PJ, Hansen TB, Pransky G, et al. Effect of grip type, wrist motion, and resistance level on pressures within the carpal tunnel of normal wrists. J Orthop Res. 2014;32:524-530.](https://doi.org/10.1002/jor.22571) Access: full_text.
53. [Medina McKeon JM, Yancosek KE. Neural gliding techniques for the treatment of carpal tunnel syndrome: a systematic review. J Sport Rehabil. 2008;17:324-341. Abstract read.](https://doi.org/10.1123/jsr.17.3.324) Access: abstract_only.
54. [Mehta P, Stubbs PW, Tamminga SJ, et al. Ergonomic interventions for treating work-related complaints of the arm, neck or shoulder in adults. Cochrane Database Syst Rev. 2026;10(10):CD014644.](https://doi.org/10.1002/14651858.CD014644.pub2) Access: abstract_only.
55. [Nadar MS, Amr HA, Manee FS, Ali AA. In vivo evidence of lumbricals incursion into the carpal tunnel in healthy hands: an ultrasonographic cross sectional study. J Hand Ther. 2022;35:261-266.](https://doi.org/10.1016/j.jht.2022.03.003) Access: abstract_only.
56. [Newington L, Madan I, Sandford F. Driving, work, wound care and rehabilitation after carpal tunnel release: Consensus recommendations from a UK Delphi study. Hand Ther. 2022;27:71-82.](https://doi.org/10.1177/17589983221113870) Access: full_text.
57. [NHS. Carpal tunnel syndrome (patient information).](https://www.nhs.uk/conditions/carpal-tunnel-syndrome/) Access: full_text.
58. [Nilsson T, Wahlström J, Burström L. Hand-arm vibration and the risk of vascular and neurological diseases: a systematic review and meta-analysis. PLoS One. 2017;12:e0180795.](https://doi.org/10.1371/journal.pone.0180795) Access: abstract_only.
59. [Núñez de Arenas-Arroyo S, Cavero-Redondo I, Torres-Costoso A, Reina-Gutiérrez S, Álvarez-Bueno C, Martínez-Vizcaíno V. Short-term Effects of Neurodynamic Techniques for Treating Carpal Tunnel Syndrome: A Systematic Review With Meta-analysis. J Orthop Sports Phys Ther. 2021;51(12):566-580.](https://doi.org/10.2519/jospt.2021.10533) Access: abstract_only.
60. [Núñez-Cortés R, Cruz-Montecinos C, López-Bueno L, et al. Acute Clinical Effects of Dual-Task Resistance Exercise on Muscle Endurance and Pressure Sensitivity in Patients With Carpal Tunnel Syndrome After Surgery. Musculoskeletal Care. 2026;24:e70223.](https://doi.org/10.1002/msc.70223) Access: full_text.
61. [O'Connor D, Marshall S, Massy-Westropp N. Non-surgical treatment (other than steroid injection) for carpal tunnel syndrome. Cochrane Database Syst Rev. 2003;2003(1):CD003219.](https://doi.org/10.1002/14651858.CD003219) Access: abstract_only.
62. [O'Connor D, Page MJ, Marshall SC, Massy-Westropp N. Ergonomic positioning or equipment for treating carpal tunnel syndrome. Cochrane Database Syst Rev. 2012;1(1):CD009600.](https://doi.org/10.1002/14651858.CD009600) Access: abstract_only.
63. [American Academy of Orthopaedic Surgeons, OrthoInfo. Carpal Tunnel Syndrome (patient information).](https://www.orthoinfo.org/en/diseases--conditions/carpal-tunnel-syndrome/) Access: full_text.
64. [Padua L, Coraci D, Erra C, et al. Carpal tunnel syndrome: clinical features, diagnosis, and management. Lancet Neurol. 2016;15:1273-1284. Abstract read.](https://doi.org/10.1016/s1474-4422(16)30231-9) Access: abstract_only.
65. [Page MJ, O'Connor D, Pitt V, Massy-Westropp N. Exercise and mobilisation interventions for carpal tunnel syndrome. Cochrane Database Syst Rev. 2012;(6):CD009899. The Cochrane page lists 2022 Issue 3, published 1 April 2022, but Crossref and Europe PMC record this DOI as 13 June 2012.](https://doi.org/10.1002/14651858.CD009899) Access: full_text. Partial: the first 100,000 of about 462,000 characters of the PMC page (summary and first comparisons). A second retrieval attempt returned only the abstract and plain-language summary.
66. [Palmer KT, Harris EC, Coggon D. Carpal tunnel syndrome and its relation to occupation: a systematic literature review. Occup Med (Lond). 2007;57(1):57-66.](https://doi.org/10.1093/occmed/kql125) Access: abstract_only.
67. [Paquette P, Higgins J, Danino MA, Harris P, Lamontagne M, Gagnon DH. Effects of a preoperative neuromobilization program offered to individuals with carpal tunnel syndrome awaiting carpal tunnel decompression surgery: a pilot randomized controlled study. J Hand Ther. 2021;34:37-46.](https://doi.org/10.1016/j.jht.2019.12.012) Access: abstract_only.
68. [Paraskevopoulos E, Karanasios S, Gioftsos G, Tatsios P, Koumantakis G, Papandreou M. The effectiveness of neuromobilization exercises in carpal tunnel syndrome: Systematic review and meta-analysis. Physiother Theory Pract. 2023;39(10):2037-2076.](https://doi.org/10.1080/09593985.2022.2068097) Access: abstract_only.
69. [Pelosi L, Arányi Z, Beekman R, et al. Expert consensus on the combined investigation of carpal tunnel syndrome with electrodiagnostic tests and neuromuscular ultrasound. Clin Neurophysiol. 2022;135:107-116.](https://doi.org/10.1016/j.clinph.2021.12.012) Access: abstract_only.
70. [Physiotutors. Carpal tunnel syndrome diagnosis article, Research: Diagnosis & Imaging, 24 April 2026. Secondary narrative summary.](https://www.physiotutors.com/research/carpal-tunnel-syndrome-diagnosis/) Access: registry_or_summary.
71. [Vivanco A. Carpal Tunnel Syndrome: The New 2026 Clinical Practice Guideline for Physiotherapists. Physiotutors, 14 October 2026. Secondary summary of Erickson et al., J Orthop Sports Phys Ther. 2026;56(4):CPG1-CPG79 (doi:10.2519/jospt.2026.0301, PMID 41919928), which was not read.](https://www.physiotutors.com/?p=29289) Access: registry_or_summary. Third-party summary of Erickson 2026, kept because the original could not be read.
72. [Piazzini DB, Aprile I, Ferrara PE, et al. A systematic review of conservative treatment of carpal tunnel syndrome. Clin Rehabil. 2007;21(4):299-314.](https://doi.org/10.1177/0269215507077294) Access: abstract_only.
73. [Rempel D, Keir PJ, Smutz WP, Hargens A. Effects of static fingertip loading on carpal tunnel pressure. J Orthop Res. 1997;15:422-426. Abstract read.](https://doi.org/10.1002/jor.1100150315) Access: abstract_only.
74. [Rempel D, Evanoff B, Amadio PC, et al. Consensus criteria for the classification of carpal tunnel syndrome in epidemiologic studies. Am J Public Health. 1998;88(10):1447-1451.](https://doi.org/10.2105/ajph.88.10.1447) Access: abstract_only.
75. [Robinson LR, Al-Lawati ZJ, Andary M, et al. AANEM Consensus-Based Guideline for Grading the Severity of Carpal Tunnel Syndrome. Muscle Nerve. 2026 Sep 12 (online ahead of print).](https://doi.org/10.1002/mus.70384) Access: abstract_only.
76. [Sadler KP, Torbert DR, Writer JK. Management of Carpal Tunnel Syndrome: Guidelines From the AAOS. Am Fam Physician. 2025;112(3):337-339.](https://www.aafp.org/pubs/afp/issues/2025/0900/practice-guidelines-carpal-tunnel-syndrome.html) Access: registry_or_summary. Free excerpt only.
77. [Schmid AB, Elliott JM, Strudwick MW, Little M, Coppieters MW. Effect of splinting and exercise on intraneural edema of the median nerve in carpal tunnel syndrome: an MRI study to reveal therapeutic mechanisms. J Orthop Res. 2012;30:1343-1350.](https://doi.org/10.1002/jor.22064) Access: abstract_only.
78. [Schmid AB, Kubler PA, Johnston V, Coppieters MW. A vertical mouse and ergonomic mouse pads alter wrist position but do not reduce carpal tunnel pressure in patients with carpal tunnel syndrome. Appl Ergon. 2015;47:151-156.](https://doi.org/10.1016/j.apergo.2014.08.020) Access: abstract_only.
79. [Shapiro LM, Kamal R, Brault J, et al. American Academy of Orthopaedic Surgeons/ASSH Clinical Practice Guideline Summary: Management of Carpal Tunnel Syndrome. J Am Acad Orthop Surg. 2025;33(7):e356-e366. Erratum: 2026;34(20):e2922 (PMID 42825588, record seen, content not read).](https://doi.org/10.5435/JAAOS-D-24-01179) Access: full_text. The published guideline summary, read by keyword extraction and checked in the XML. The full 2024 CPG PDF was not readable.
80. [Sheereen FJ, Sarkar B, Sahay P, et al. Comparison of Two Manual Therapy Programs, including Tendon Gliding Exercises as a Common Adjunct, While Managing the Participants with Chronic Carpal Tunnel Syndrome. Pain Res Manag. 2022;2022:1975803.](https://doi.org/10.1155/2022/1975803) Access: full_text.
81. [Shem K, Wong J, Dirlikov B. Effective self-stretching of carpal ligament for the treatment of carpal tunnel syndrome: A double-blinded randomized controlled study. J Hand Ther. 2020;33(3):272-280.](https://doi.org/10.1016/j.jht.2019.12.002) Access: abstract_only. A published comment was not read.
82. [Sim SE, Gunasagaran J, Goh KJ, Ahmad TS. Short-term clinical outcome of orthosis alone vs combination of orthosis, nerve, and tendon gliding exercises and ultrasound therapy for treatment of carpal tunnel syndrome. J Hand Ther. 2019;32:411-416.](https://doi.org/10.1016/j.jht.2018.01.004) Access: abstract_only.
83. [Vignais N, Weresch J, Keir PJ. Posture and Loading in the Pathomechanics of Carpal Tunnel Syndrome: A Review. Crit Rev Biomed Eng. 2016;44:397-410.](https://doi.org/10.1615/CritRevBiomedEng.2017021073) Access: abstract_only.
84. [Wolny T, Linek P. Is manual therapy based on neurodynamic techniques effective in the treatment of carpal tunnel syndrome? A randomized controlled trial. Clin Rehabil. 2019;33(3):408-417.](https://doi.org/10.1177/0269215518805213) Access: abstract_only. A published comment was not read.
85. [Zaheer SA, Ahmed Z. Neurodynamic Techniques in the Treatment of Mild-to-Moderate Carpal Tunnel Syndrome: A Systematic Review and Meta-Analysis. J Clin Med. 2023;12(15):4888.](https://doi.org/10.3390/jcm12154888) Access: full_text.
86. [Çağlar S, Altun T, Vural M, Mert M. Evaluation of Splint and Exercise Interventions for Carpal Tunnel Syndrome: Insights from Ultrasonographic Measurements. Med Sci Monit. 2024;30:e945704.](https://doi.org/10.12659/MSM.945704) Access: full_text.
