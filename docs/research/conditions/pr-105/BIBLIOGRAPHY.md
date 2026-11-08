# Condition research bibliography

Every source in the eight condition `*.json` files, deduplicated across files. Generated from the JSON; nothing here was added by hand. Overall synthesis is in [CONSOLIDATION.md](CONSOLIDATION.md).

## How to read this

- **Grouping:** each source appears once, under the first condition in processing order that cites it, then by that file's study type. "Also cited by" names the other conditions, so a condition's full list is in its own `.md` file.
- **Deduplication:** sources merge when they share a DOI, PMID or URL.
- **Links:** doi.org first, then PubMed, then the source page for the 29 records with neither identifier (guideline PDFs, NHS pages, registries, summaries).
- **Access level** is how far the source was actually read, per the JSON. When files disagree, the deepest level is shown and the others are noted. `full_text` often means targeted extraction, not reading end to end; see each condition file's `verification.unverifiable`.

## Totals

| Access level | Unique sources | Meaning |
|---|---:|---|
| `full_text` | 221 | Full text opened, often by targeted extraction |
| `abstract_only` | 408 | Abstract only; methods and tables not read |
| `registry_or_summary` | 22 | Registry entry, summary or secondary page |
| `not_reconfirmed` | 3 | Could not be reopened in the check |
| **Total** | **654** | From 682 records across the 8 files, 24 of them cited by more than one condition |

| Condition | Records in file | Listed here |
|---|---:|---:|
| [piriformis_syndrome](piriformis_syndrome.md) | 79 | 79 |
| [gluteal_tendinopathy](gluteal_tendinopathy.md) | 71 | 70 |
| [lumbar_radiculopathy](lumbar_radiculopathy.md) | 102 | 95 |
| [nonspecific_low_back_pain](nonspecific_low_back_pain.md) | 101 | 86 |
| [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) | 85 | 83 |
| [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) | 66 | 63 |
| [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) | 86 | 86 |
| [patellofemoral_pain](patellofemoral_pain.md) | 92 | 92 |

## Piriformis syndrome

Condition file: [piriformis_syndrome](piriformis_syndrome.md)

### Clinical practice guideline (2)

- [Enseki K, Harris-Hayes M, White DM, et al. Nonarthritic hip joint pain. J Orthop Sports Phys Ther. 2014;44(6):A1-32](https://doi.org/10.2519/jospt.2014.0302). Access: `full_text`. Type: `clinical_practice_guideline`.
- [Enseki KR, Bloom NJ, Harris-Hayes M, et al. Hip Pain and Movement Dysfunction Associated With Nonarthritic Hip Joint Pain: A Revision. J Orthop Sports Phys Ther. 2023;53(7):CPG1-CPG70](https://doi.org/10.2519/jospt.2023.0302). Access: `full_text`. Type: `clinical_practice_guideline`. Other files record: `abstract_only` in [piriformis_syndrome](piriformis_syndrome.md). Also cited by: [gluteal_tendinopathy](gluteal_tendinopathy.md).

### Guideline (3)

- [Aldera M, Alturkistany A, Al Rayes H, et al. Saudi Clinical Practice Guideline for the Assessment and Management of Low Back Pain and Sciatica in Adults. J Clin Med. 2026;15(2)](https://doi.org/10.3390/jcm15020528). Access: `full_text`. Type: `guideline`. Also cited by: [lumbar_radiculopathy](lumbar_radiculopathy.md), [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [NICE guideline NG59. Low back pain and sciatica in over 16s: assessment and management. Published 30 November 2016; last updated 29 July 2026. Recommendations page only](https://pubmed.ncbi.nlm.nih.gov/33090750/). Access: `full_text`. Type: `guideline`. Other files record: `not_reconfirmed` in [lumbar_radiculopathy](lumbar_radiculopathy.md). Also cited by: [lumbar_radiculopathy](lumbar_radiculopathy.md), [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [WHO guideline for non-surgical management of chronic primary low back pain in adults in primary and community care settings. WHO; 2023. Publication page and a published commentary (PMC12232859) only](https://www.who.int/publications/i/item/9789240081789). Access: `registry_or_summary`. Type: `guideline`. No DOI or PMID recorded; links to the source page.

### Consensus statement (2)

- [Sconfienza LM, Adriaensen M, Albano D, et al. Clinical indications for image-guided interventional procedures in the musculoskeletal system: a Delphi-based consensus paper from the ESSR, part VII, nerves of the lower limb. Eur Radiol. 2022;32(3):1456-1464](https://doi.org/10.1007/s00330-021-08283-0). Access: `full_text`. Type: `consensus_statement`.
- [Vaishya S, Pojskic M, Bedi MS, et al. Cauda equina, conus medullaris and syndromes mimicking sciatic pain: WFNS spine committee recommendations. World Neurosurg X. 2024;22:100274](https://doi.org/10.1016/j.wnsx.2024.100274). Access: `full_text`. Type: `consensus_statement`.

### Systematic review (18)

- [Aali S, Rezazadeh F, Imani F, Moradzadeh N, Prisacaru C, Badicu G. Effectiveness of stretching techniques in piriformis syndrome management: a systematic review. Eur J Transl Myol. 2026 Oct 29, online ahead of print. Abstract only read](https://doi.org/10.4081/ejtm.2026.14765). Access: `abstract_only`. Type: `systematic_review`.
- [Bisciotti GN, Bisciotti A, Zini R, Bisciotti A, Volpi P, Di Pietto F. The diagnosis of deep gluteal syndrome: a systematic review of the current literature. Muscles Ligaments Tendons J. 2026;16. Abstract only read](https://doi.org/10.32098/mltj_2026_6435). Access: `abstract_only`. Type: `systematic_review`.
- [Chiddarwar VV, Kelly DK, DelBiondo GM, et al. Rehabilitative and Conditioning Exercises That Selectively Activate Individual Lower Extremity Muscles: A Systematic Review. Sports Health. 2026, online ahead of print](https://doi.org/10.1177/19417381261480238). Access: `full_text`. Type: `systematic_review`.
- [Dahm KT, Brurberg KG, Jamtvedt G, Hagen KB. Advice to rest in bed versus advice to stay active for acute low-back pain and sciatica. Cochrane Database Syst Rev. 2010;(6):CD007612](https://doi.org/10.1002/14651858.CD007612.pub2). Access: `abstract_only`. Type: `systematic_review`. Also cited by: [lumbar_radiculopathy](lumbar_radiculopathy.md), [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [Dove L, Jones G, Kelsey LA, Cairns MC, Schmid AB. How effective are physiotherapy interventions in treating people with sciatica? A systematic review and meta-analysis. Eur Spine J. 2023;32(2):517-533](https://doi.org/10.1007/s00586-022-07356-y). Access: `full_text`. Type: `systematic_review`. Also cited by: [lumbar_radiculopathy](lumbar_radiculopathy.md).
- [Fernandez M, Hartvigsen J, Ferreira ML, et al. Advice to Stay Active or Structured Exercise in the Management of Sciatica: A Systematic Review and Meta-analysis. Spine. 2015;40(18):1457-1466](https://doi.org/10.1097/BRS.0000000000001036). Access: `abstract_only`. Type: `systematic_review`. Also cited by: [lumbar_radiculopathy](lumbar_radiculopathy.md).
- [Franceschi G, Scotto I, Maselli F, Mourad F, Gallotti M. Hands-On Versus Hands-Off Treatment of Hip-Related Nonspecific Musculoskeletal Diseases: A Systematic Review. J Funct Morphol Kinesiol. 2024;9(4)](https://doi.org/10.3390/jfmk9040262). Access: `full_text`. Type: `systematic_review`.
- [Hilal FM, Bashawyah A, Allam AE, et al. Efficacy of Botulinum Toxin, Local Anesthetics, and Corticosteroids in Patients With Piriformis Syndrome: A Systematic Review and Meta-analysis. Pain Physician. 2022;25(5):325-337](https://pubmed.ncbi.nlm.nih.gov/35901473/). Access: `abstract_only`. Type: `systematic_review`.
- [Hopayian K, Danielyan A. Four symptoms define the piriformis syndrome: an updated systematic review of its clinical features. Eur J Orthop Surg Traumatol. 2018;28(2):155-164](https://doi.org/10.1007/s00590-017-2031-8). Access: `abstract_only`. Type: `systematic_review`.
- [Hopayian K, Mirzaei M, Shamsi M, Arab-Zozani M. A systematic review of conservative and surgical treatments for deep gluteal syndrome. J Bodyw Mov Ther. 2023;36:244-250. Abstract only read](https://doi.org/10.1016/j.jbmt.2022.12.003). Access: `abstract_only`. Type: `systematic_review`.
- [Kay J, de Sa D, Morrison L, et al. Surgical Management of Deep Gluteal Syndrome Causing Sciatic Nerve Entrapment: A Systematic Review. Arthroscopy. 2017;33(12):2263-2278](https://doi.org/10.1016/j.arthro.2017.06.041). Access: `abstract_only`. Type: `systematic_review`.
- [Kizaki K, Uchida S, Shanmugaraj A, et al. Deep gluteal syndrome is defined as a non-discogenic sciatic nerve disorder with entrapment in the deep gluteal space: a systematic review. Knee Surg Sports Traumatol Arthrosc. 2020;28(10):3354-3364](https://doi.org/10.1007/s00167-020-05966-x). Access: `abstract_only`. Type: `systematic_review`.
- [Metikala S, Sharma V. Endoscopic Sciatic Neurolysis for Deep Gluteal Syndrome: A Systematic Review. Cureus. 2022;14(3):e23153. Surgical review; conservative care described as background](https://doi.org/10.7759/cureus.23153). Access: `full_text`. Type: `systematic_review`.
- [Monteleone G, Stevanato G, Alimandi M, Cappa E, Sorge R. Piriformis syndrome: a systematic review of case reports. BMC Surg. 2025;25(1):468](https://doi.org/10.1186/s12893-025-03202-2). Access: `full_text`. Type: `systematic_review`.
- [Shiri R, Falah-Hassani K, Viikari-Juntura E, Coggon D. Leisure-time physical activity and sciatica: A systematic review and meta-analysis. Eur J Pain. 2016;20(10):1563-1572](https://doi.org/10.1002/ejp.885). Access: `abstract_only`. Type: `systematic_review`. Also cited by: [lumbar_radiculopathy](lumbar_radiculopathy.md).
- [Siddiq MAB, Rasker JJ. Piriformis pyomyositis, a cause of piriformis syndrome: a systematic search and review. Clin Rheumatol. 2019;38(7):1811-1821](https://doi.org/10.1007/s10067-019-04552-y). Access: `abstract_only`. Type: `systematic_review`.
- [Smoll NR. Variations of the piriformis and sciatic nerve with clinical consequence: a review. Clin Anat. 2010;23(1):8-17](https://doi.org/10.1002/ca.20893). Access: `abstract_only`. Type: `systematic_review`.
- [Waseem Z, Boulias C, Gordon A, Ismail F, Sheean G, Furlan AD. Botulinum toxin injections for low-back pain and sciatica. Cochrane Database Syst Rev. 2011;(1):CD008257](https://doi.org/10.1002/14651858.CD008257.pub2). Access: `abstract_only`. Type: `systematic_review`.

### Scoping review (2)

- [Gallotti M. FP13.5 Time to reconsider conservative treatment recommendations for Deep Gluteal Syndrome: A scoping review. J Hip Preserv Surg. 2026;13(Suppl 1):i44. Conference abstract](https://doi.org/10.1093/jhps/hnag043.101). Access: `abstract_only`. Type: `scoping_review`.
- [Siddiq MAB, Clegg D, Hasan SA, Rasker JJ. Extra-spinal sciatica and sciatica mimics: a scoping review. Korean J Pain. 2020;33(4):305-317](https://doi.org/10.3344/kjp.2020.33.4.305). Access: `full_text`. Type: `scoping_review`.

### RCT (16)

- [Ahadi T, Yousefi A, Sajadi S, Yousefi N, Babaei-Ghazani A. Comparing radial extracorporeal shockwave therapy and corticosteroid injection in the treatment of piriformis syndrome: A randomized clinical trial. J Bodyw Mov Ther. 2023;33:182-188](https://doi.org/10.1016/j.jbmt.2022.09.020). Access: `abstract_only`. Type: `rct`.
- [Byeon GJ, Kim KH. Piriformis Syndrome in Knee Osteoarthritis Patients after Wearing Rocker Bottom Shoes. Korean J Pain. 2011;24(2):93-99](https://doi.org/10.3344/kjp.2011.24.2.93). Access: `full_text`. Type: `rct`.
- [Danazumi MS, Yakasai AM, Ibrahim AA, Shehu UT, Ibrahim SU. Effect of integrated neuromuscular inhibition technique compared with positional release technique in the management of piriformis syndrome. J Osteopath Med. 2021;121(8):693-703](https://doi.org/10.1515/jom-2020-0327). Access: `abstract_only`. Type: `rct`.
- [Dede BT, Toktaş M, Kurnaz S, Oğuz M, Bağcıer F, Aytekin E. Extracorporeal shock wave therapy versus dry needling for piriformis syndrome: A randomized clinical trial. Turk J Phys Med Rehabil. 2026;72(1):39-47](https://doi.org/10.5606/tftrd.2026.16718). Access: `full_text`. Type: `rct`.
- [Fishman LM, Anderson C, Rosner B. BOTOX and physical therapy in the treatment of piriformis syndrome. Am J Phys Med Rehabil. 2002;81(12):936-942](https://doi.org/10.1097/00002060-200212000-00009). Access: `abstract_only`. Type: `rct`.
- [Fishman LM, Wilkins AN, Rosner B. Electrophysiologically identified piriformis syndrome is successfully treated with incobotulinum toxin a and physical therapy. Muscle Nerve. 2017;56(2):258-263](https://doi.org/10.1002/mus.25504). Access: `abstract_only`. Type: `rct`.
- [Fu YS, Shih KS, Lin YT, Hsieh LF, Liu YF, Chen YR. Efficacy of ultrasound-guided piriformis muscle corticosteroid injection versus extracorporeal shockwave therapy in patients with piriformis syndrome: A randomized controlled trial. J Formos Med Assoc. 2026;125(4):391-397](https://doi.org/10.1016/j.jfma.2025.01.020). Access: `abstract_only`. Type: `rct`.
- [Keilman BM, Hanney WJ, Kolber MJ, et al. The Short-Term Effect of Kettlebell Swings on Lumbopelvic Pressure Pain Thresholds: A Randomized Controlled Trial. J Strength Cond Res. 2017;31(11):3001-3009](https://doi.org/10.1519/JSC.0000000000001743). Access: `abstract_only`. Type: `rct`.
- [Nazlıkul H, Ural FG, Öztürk GT, Öztürk ADT. Evaluation of neural therapy effect in patients with piriformis syndrome. J Back Musculoskelet Rehabil. 2018;31(6):1105-1110](https://doi.org/10.3233/BMR-170980). Access: `abstract_only`. Type: `rct`.
- [Sadiq A, Khalid Z, Kiran T, Hassan W. Additional effects of piriformis stretch in the management of sacroiliac joint dysfunction: A randomized control trial. J Bodyw Mov Ther. 2025;44:149-154. Indirect population](https://doi.org/10.1016/j.jbmt.2025.05.044). Access: `abstract_only`. Type: `rct`.
- [Shaheen HM, Belgen Kaygisiz B. Comparison of different treatment positions of nerve slider technique for patients with low back pain: a randomized control trial. Eur J Phys Rehabil Med. 2025;61(1):82-92. Indirect population; piriformis syndrome excluded](https://doi.org/10.23736/S1973-9087.24.08541-1). Access: `full_text`. Type: `rct`. Also cited by: [lumbar_radiculopathy](lumbar_radiculopathy.md).
- [Shahzad M, Rafique N, Shakil-Ur-Rehman S, Ali Hussain S. Effects of ELDOA and post-facilitation stretching technique on pain and functional performance in patients with piriformis syndrome: A randomized controlled trial. J Back Musculoskelet Rehabil. 2020;33(6):983-988](https://doi.org/10.3233/BMR-181290). Access: `abstract_only`. Type: `rct`.
- [Shamsi M, Akbari M, Mirzaei M, Minobes Molina E. Comparing the effect of stretching and compression exercises on flexion relaxation ratio of back muscles in patients having deep gluteal syndrome: A randomized trial. J Bodyw Mov Ther. 2024;40:66-71. Appears to be the same cohort as Shamsi 2024a](https://doi.org/10.1016/j.jbmt.2024.03.073). Access: `abstract_only`. Type: `rct`.
- [Shamsi M, Mirzaei M, Hopayian K. A controlled clinical trial investigating the effects of stretching and compression exercises on electromyography of calf muscles in chronic LBP patients with a deep gluteal syndrome. BMC Sports Sci Med Rehabil. 2024;16(1):12](https://doi.org/10.1186/s13102-023-00802-4). Access: `full_text`. Type: `rct`.
- [Tabatabaiee A, Takamjani IE, Sarrafzadeh J, Salehi R, Ahmadi M. Ultrasound-guided dry needling decreases pain in patients with piriformis syndrome. Muscle Nerve. 2019;60(5):558-565](https://doi.org/10.1002/mus.26671). Access: `abstract_only`. Type: `rct`.
- [Usman M, Khan R, Riaz SA, Shahid G, Ali ST, Arif A. Comparative Effects of Neural Mobilization of Sciatic Nerve Versus Stretching Among Patients with Piriformis Syndrome: A Randomized Clinical Trial. J Riphah Coll Rehabil Sci. 2025;13(4):203-209. NCT06808659](https://doi.org/10.53389/JRCRS.2025130403). Access: `full_text`. Type: `rct`.

### Observational (8)

- [Chaari F, Jardak M, Bouchaala F, Harrabi MA, Rebai H, Sahli S. Immediate effect of hip exercises-inducing motor unit recruitment on static and dynamic postural balance in patients with piriformis muscle syndrome. J Bodyw Mov Ther. 2024;40:1738-1743. Abstract only read](https://doi.org/10.1016/j.jbmt.2024.10.027). Access: `abstract_only`. Type: `observational`.
- [Ertem U, Özçakır S, İrdesel FJ, Günay SM. YouTube as a source of information on piriformis syndrome exercises. Turk J Phys Med Rehabil. 2023;69(1):15-22](https://doi.org/10.5606/tftrd.2022.10459). Access: `full_text`. Type: `observational`.
- [Fishman LM, Dombi GW, Michaelsen C, et al. Piriformis syndrome: diagnosis, treatment, and outcome--a 10-year study. Arch Phys Med Rehabil. 2002;83(3):295-301](https://doi.org/10.1053/apmr.2002.30622). Access: `abstract_only`. Type: `observational`.
- [Fishman LM, Konnoth C, Rozner B. Botulinum neurotoxin type B and physical therapy in the treatment of piriformis syndrome: a dose-finding study. Am J Phys Med Rehabil. 2004;83(1):42-50](https://doi.org/10.1097/01.PHM.0000104669.86076.30). Access: `abstract_only`. Type: `observational`.
- [Guner D, Ozcete ZA. Evaluation of the Efficacy of Ultrasound-Guided Dry Needling Therapy and Exercise in Piriformis Muscle Syndrome. Cureus. 2023;15(8):e43804. Non-randomized: groups formed by whether patients accepted needling](https://doi.org/10.7759/cureus.43804). Access: `full_text`. Type: `observational`.
- [Meknas K, Kartus J, Letto JI, Flaten M, Johansen O. A 5-year prospective study of non-surgical treatment of retro-trochanteric pain. Knee Surg Sports Traumatol Arthrosc. 2009;17(8):996-1002](https://doi.org/10.1007/s00167-009-0750-z). Access: `abstract_only`. Type: `observational`.
- [Michel F, Decavel P, Toussirot E, et al. Piriformis muscle syndrome: diagnostic criteria and treatment of a monocentric series of 250 patients. Ann Phys Rehabil Med. 2013;56(5):371-383](https://doi.org/10.1016/j.rehab.2013.04.003). Access: `abstract_only`. Type: `observational`.
- [Neal C, Gelatt T, Toma M. Prevalence and Clinical Patterns of Piriformis Syndrome Among Actively Competing and Retired Elite Hockey Players. Sports (Basel). 2026;14(3):95](https://doi.org/10.3390/sports14030095). Access: `full_text`. Type: `observational`.

### Diagnostic accuracy (1)

- [Martin HD, Kivlan BR, Palmer IJ, Martin RL. Diagnostic accuracy of clinical tests for sciatic nerve entrapment in the gluteal region. Knee Surg Sports Traumatol Arthrosc. 2014;22(4):882-888](https://doi.org/10.1007/s00167-013-2758-7). Access: `abstract_only`. Type: `diagnostic_accuracy`.

### Case report (1)

- [Tonley JC, Yun SM, Kochevar RJ, Dye JA, Farrokhi S, Powers CM. Treatment of an individual with piriformis syndrome focusing on hip muscle strengthening and movement reeducation: a case report. J Orthop Sports Phys Ther. 2010;40(2):103-111. Abstract only read](https://doi.org/10.2519/jospt.2010.3108). Access: `abstract_only`. Type: `case_report`.

### Biomechanical (10)

- [Giphart JE, Stull JD, LaPrade RF, Wahoff MS, Philippon MJ. Recruitment and activity of the pectineus and piriformis muscles during hip rehabilitation exercises: an electromyography study. Am J Sports Med. 2012;40(7):1654-1663](https://doi.org/10.1177/0363546512443812). Access: `abstract_only`. Type: `biomechanical`.
- [Gulledge BM, Marcellin-Little DJ, Levine D, et al. Comparison of two stretching methods and optimization of stretching protocol for the piriformis muscle. Med Eng Phys. 2014;36(2):212-218. 7 subjects plus computational model. Abstract only read](https://doi.org/10.1016/j.medengphy.2013.10.016). Access: `abstract_only`. Type: `biomechanical`.
- [Ito Y, Suzuki D, Kizawa F, et al. Comparison of External Rotation Torque of Short External Rotator Muscles in Hip Flexion Position. J Orthop Res. 2025;43(9):1655-1665](https://doi.org/10.1002/jor.70005). Access: `full_text`. Type: `biomechanical`.
- [Itsuda H, Yagi M, Yanase K, Umehara J, Mukai H, Ichihashi N. Effective Stretching Positions of the Piriformis Muscle Evaluated Using Shear Wave Elastography. J Sport Rehabil. 2024;33(4):282-288. 21 healthy men. Abstract only read](https://doi.org/10.1123/jsr.2023-0240). Access: `abstract_only`. Type: `biomechanical`.
- [Kim S, Burland JP, Morgan KD, DiStefano LJ, Boling MC, Glaviano NR. Superficial and Deep Gluteal Muscle Activation During Common Therapeutic Exercises: A Muscle Functional Magnetic Resonance Imaging Analysis. Sports Health. 2026;18(3):582-591](https://doi.org/10.1177/19417381251372979). Access: `full_text`. Type: `biomechanical`.
- [Maniar N, Schache AG, Sritharan P, Opar DA. Non-knee-spanning muscles contribute to tibiofemoral shear as well as valgus and rotational joint reaction moments during unanticipated sidestep cutting. Sci Rep. 2018;8:2501](https://doi.org/10.1038/s41598-017-19098-9). Access: `abstract_only`. Type: `biomechanical`.
- [McGovern RP, Kivlan BR, Martin RL. Length change of the short external rotators of the hip in common stretch positions: a cadaveric study. Int J Sports Phys Ther. 2017;12(7):1068-1077](https://doi.org/10.26603/ijspt20171068). Access: `full_text`. Type: `biomechanical`.
- [Meinders E, Pizzolato C, Gonçalves B, Lloyd DG, Saxby DJ, Diamond LE. Activation of the deep hip muscles can change the direction of loading at the hip. J Biomech. 2022;135:111019](https://doi.org/10.1016/j.jbiomech.2022.111019). Access: `abstract_only`. Type: `biomechanical`.
- [Meinders E, Pizzolato C, Gonçalves BAM, Lloyd DG, Saxby DJ, Diamond LE. Electromyography measurements of the deep hip muscles do not improve estimates of hip contact force. J Biomech. 2022;141:111220. Read only as a companion-paper note](https://doi.org/10.1016/j.jbiomech.2022.111220). Access: `abstract_only`. Type: `biomechanical`.
- [Uebayashi K, Okubo Y, Akasaka K, et al. Muscle activity of deep hip muscles across joint positions and elevation phases during side-lying hip abduction. PeerJ. 2026;14:e21536](https://doi.org/10.7717/peerj.21536). Access: `full_text`. Type: `biomechanical`.

### RCT protocol (1)

- [Dey G, Hossain MA, Halder P, Huda MN, Adhikari M, Alam F. Effectiveness of abdominal hollowing and piriformis stretching exercises in spondylolisthesis patients: a study protocol for a randomized controlled trial. Trials. 2026;27:556. Protocol; no results; indirect population](https://doi.org/10.1186/s13063-026-09873-3). Access: `full_text`. Type: `rct_protocol`.

### Systematic review protocol (1)

- [Deshmukh N. Impact of Physiotherapy Intervention on Pain, Quality of Life, and Function in Low Back Pain Associated With Piriformis Syndrome: Protocol for Systematic Review. JMIR Res Protoc. 2026;15:e72350. Protocol; no results](https://doi.org/10.2196/72350). Access: `abstract_only`. Type: `systematic_review_protocol`.

### Trial registry (2)

- [Effects of Hip Abductor Strengthening Exercises in Patients With Piriformis Syndrome. ClinicalTrials.gov NCT07484373. Completed, 48 enrolled. Registry record read; no results posted](https://clinicaltrials.gov/study/NCT07484373). Access: `registry_or_summary`. Type: `trial_registry`. No DOI or PMID recorded; links to the source page.
- [Self Myofascial Release in Individuals With Piriformis Syndrome. ClinicalTrials.gov NCT05660005. Completed, 63 enrolled. Registry record read; no results posted](https://clinicaltrials.gov/study/NCT05660005). Access: `registry_or_summary`. Type: `trial_registry`. No DOI or PMID recorded; links to the source page.

### Narrative review (10)

- [Ahmad Siraj S, Dadgal R. Physiotherapy for Piriformis Syndrome Using Sciatic Nerve Mobilization and Piriformis Release. Cureus. 2022;14(12):e32952](https://doi.org/10.7759/cureus.32952). Access: `full_text`. Type: `narrative_review`.
- [Geler Külcü D. Deep Gluteal syndrome: An underestimated cause of posterior hip pain. Turk J Phys Med Rehabil. 2024;70(1):4-16](https://doi.org/10.5606/tftrd.2024.14668). Access: `full_text`. Type: `narrative_review`.
- [Halpin RJ, Ganju A. Piriformis syndrome: a real pain in the buttock? Neurosurgery. 2009;65(4 Suppl):A197-202](https://doi.org/10.1227/01.NEU.0000335788.45495.0C). Access: `abstract_only`. Type: `narrative_review`.
- [Martin HD, Reddy M, Gómez-Hoyos J. Deep gluteal syndrome. J Hip Preserv Surg. 2015;2(2):99-107](https://doi.org/10.1093/jhps/hnv029). Access: `full_text`. Type: `narrative_review`.
- [Michel F, Decavel P, Toussirot E, et al. The piriformis muscle syndrome: an exploration of anatomical context, pathophysiological hypotheses and diagnostic criteria. Ann Phys Rehabil Med. 2013;56(4):300-311](https://doi.org/10.1016/j.rehab.2013.03.006). Access: `abstract_only`. Type: `narrative_review`.
- [Miller TA, White KP, Ross DC. The diagnosis and management of Piriformis Syndrome: myths and facts. Can J Neurol Sci. 2012. Abstract only read](https://doi.org/10.1017/s0317167100015298). Access: `abstract_only`. Type: `narrative_review`.
- [Park JW, Lee YK, Lee YJ, Shin S, Kang Y, Koo KH. Deep gluteal syndrome as a cause of posterior hip pain and sciatica-like pain. Bone Joint J. 2020;102-B(5):556-567. Abstract only read](https://doi.org/10.1302/0301-620X.102B5.BJJ-2019-1212.R1). Access: `abstract_only`. Type: `narrative_review`.
- [Sharma S, Kaur H, Verma N, Adhya B. Looking beyond Piriformis Syndrome: Is It Really the Piriformis? Hip Pelvis. 2023;35(1):1-5](https://doi.org/10.5371/hp.2023.35.1.1). Access: `full_text`. Type: `narrative_review`.
- [Vij N, Kiernan H, Bisht R, et al. Surgical and Non-surgical Treatment Options for Piriformis Syndrome: A Literature Review. Anesth Pain Med. 2021;11(1):e112825](https://doi.org/10.5812/aapm.112825). Access: `full_text`. Type: `narrative_review`.
- [Yoon YH, Hwang JH, Lee HW, et al. Beyond Nerve Entrapment: A Narrative Review of Muscle-Tendon Pathologies in Deep Gluteal Syndrome. Diagnostics (Basel). 2025;15(19):2531](https://doi.org/10.3390/diagnostics15192531). Access: `full_text`. Type: `narrative_review`.

### Patient guidance (1)

- [NHS. Sciatica. Page last reviewed 3 December 2024. General sciatica advice, not specific to piriformis syndrome](https://www.nhs.uk/conditions/sciatica/). Access: `full_text`. Type: `patient_guidance`. No DOI or PMID recorded; links to the source page.

### Patient information (1)

- [NHS Lothian. Piriformis Syndrome: Patient Information Sheet. v1.0 approved Jan 2025, review Jan 2028](https://policyonline.nhslothian.scot/wp-content/uploads/2025/02/Piriformis-Syndrome.pdf). Access: `full_text`. Type: `patient_information`. No DOI or PMID recorded; links to the source page.

## Gluteal tendinopathy (greater trochanteric pain syndrome)

Condition file: [gluteal_tendinopathy](gluteal_tendinopathy.md)

### Guideline (2)

- [Expert Panel on Musculoskeletal Imaging; Jawetz ST, Fox MG, Blankenbaker DG, et al. ACR Appropriateness Criteria Chronic Hip Pain: 2022 Update. J Am Coll Radiol. 2023;20(5S):S33-S48](https://doi.org/10.1016/j.jacr.2023.02.019). Access: `full_text`. Type: `guideline`.
- [NICE. Extracorporeal shockwave therapy for refractory greater trochanteric pain syndrome. Interventional procedures guidance IPG376, published 26 January 2011; migrated to HealthTech guidance HTG248 with content unchanged](https://www.nice.org.uk/guidance/htg248/chapter/1-Recommendations). Access: `full_text`. Type: `guideline`. No DOI or PMID recorded; links to the source page.

### Local clinical pathway (1)

- [Humber and North Yorkshire Health and Care Partnership. Greater trochanteric pain syndrome (GTPS) pathway (York). Review date 31/07/2019, page marked as under review; modified 20/08/2025](https://hnyppr.org.uk/web/york/w/greater-trochanteric-pain-syndrome-pathway). Access: `registry_or_summary`. Type: `local_clinical_pathway`. No DOI or PMID recorded; links to the source page.

### Consensus statement (4)

- [D'Souza RS, Her YF, Hussain N, et al. Evidence-Based Clinical Practice Guidelines on Regenerative Medicine Treatment for Chronic Pain: A Consensus Report from a Multispecialty Working Group. J Pain Res. 2024;17:2951-3001](https://doi.org/10.2147/JPR.S480559). Access: `full_text`. Type: `consensus_statement`.
- [Disantis A, Andrade AJ, Baillou A, et al. The 2022 International Society for Hip Preservation (ISHA) physiotherapy agreement on assessment and treatment of greater trochanteric pain syndrome (GTPS): an international consensus statement. J Hip Preserv Surg. 2023;10(1):48-56](https://doi.org/10.1093/jhps/hnac050). Access: `full_text`. Type: `consensus_statement`.
- [Fearon AM, Grimaldi A, Mellor R, et al; COS-GT consensus group; Vicenzino B. ICON 2020-International Scientific Tendinopathy Symposium Consensus: the development of a core outcome set for gluteal tendinopathy. Br J Sports Med. 2024;58(5):245-254](https://doi.org/10.1136/bjsports-2023-107150). Access: `abstract_only`. Type: `consensus_statement`.
- [Sconfienza LM, Adriaensen M, Alcala-Galiano A, et al. Clinical indications for image-guided interventional procedures in the musculoskeletal system: a Delphi-based consensus paper from the European Society of Musculoskeletal Radiology (ESSR)-part hip. Eur Radiol. 2022;32(1):551-560](https://doi.org/10.1007/s00330-021-07997-5). Access: `full_text`. Type: `consensus_statement`.

### Network meta analysis (3)

- [Challoumas D, Crosbie G, O'Neill S, Pedret C, Millar NL. Effectiveness of Exercise Treatments with or without Adjuncts for Common Lower Limb Tendinopathies: A Living Systematic Review and Network Meta-analysis. Sports Med Open. 2023;9(1):71](https://doi.org/10.1186/s40798-023-00616-1). Access: `full_text`. Type: `network_meta_analysis`.
- [Gazendam A, Ekhtiari S, Axelrod D, et al. Comparative Efficacy of Nonoperative Treatments for Greater Trochanteric Pain Syndrome: A Systematic Review and Network Meta-Analysis of Randomized Controlled Trials. Clin J Sport Med. 2022;32(4):427-432](https://doi.org/10.1097/JSM.0000000000000924). Access: `abstract_only`. Type: `network_meta_analysis`.
- [He Y, Lin Y, He X, Li C, Lu Q, He J. The conservative management for improving Visual Analog Scale (VAS) pain scoring in greater trochanteric pain syndrome: a Bayesian analysis. BMC Musculoskelet Disord. 2023;24(1):423](https://doi.org/10.1186/s12891-023-06443-5). Access: `abstract_only`. Type: `network_meta_analysis`.

### Systematic review (16)

- [Barratt PA, Brookes N, Newson A. Conservative treatments for greater trochanteric pain syndrome: a systematic review. Br J Sports Med. 2017;51(2):97-104](https://doi.org/10.1136/bjsports-2015-095858). Access: `abstract_only`. Type: `systematic_review`.
- [Bremer T, Nicklen P, Fearon A, Morrissey D. The efficacy of gluteal tendinopathy treatments: A systematic review. Clin Rehabil. 2025;39(5):600-617](https://doi.org/10.1177/02692155251327298). Access: `full_text`. Type: `systematic_review`.
- [Clifford C, Challoumas D, Paul L, Syme G, Millar NL. Effectiveness of isometric exercise in the management of tendinopathy: a systematic review and meta-analysis of randomised trials. BMJ Open Sport Exerc Med. 2020;6(1):e000760](https://doi.org/10.1136/bmjsem-2020-000760). Access: `full_text`. Type: `systematic_review`. Other files record: `abstract_only` in [gluteal_tendinopathy](gluteal_tendinopathy.md), `abstract_only` in [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md). Also cited by: [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md), [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md).
- [Ebert JR, Edwards PK, Fick DP, Janes GC. A Systematic Review of Rehabilitation Exercises to Progressively Load the Gluteus Medius. J Sport Rehabil. 2017;26(5):418-436](https://doi.org/10.1123/jsr.2016-0088). Access: `abstract_only`. Type: `systematic_review`.
- [Escriche-Escuder A, Casana J, Cuesta-Vargas AI. Load progression criteria in exercise programmes in lower limb tendinopathy: a systematic review. BMJ Open. 2020;10(11):e041433](https://doi.org/10.1136/bmjopen-2020-041433). Access: `abstract_only`. Type: `systematic_review`.
- [Harding D, Cameron L, Monga A, Winter S. Is shockwave therapy effective in the management of greater trochanteric pain syndrome? A systematic review and meta-analysis. Musculoskeletal Care. 2024;22(2):e1892](https://doi.org/10.1002/msc.1892). Access: `abstract_only`. Type: `systematic_review`.
- [Kjeldsen T, Hvidt KJ, Bohn MB, et al. Exercise compared to a control condition or other conservative treatment options in patients with Greater Trochanteric Pain Syndrome: a systematic review and meta-analysis of randomized controlled trials. Physiotherapy. 2024;123:69-80](https://doi.org/10.1016/j.physio.2024.01.001). Access: `abstract_only`. Type: `systematic_review`.
- [Ladurner A, Fitzpatrick J, O'Donnell JM. Treatment of Gluteal Tendinopathy: A Systematic Review and Stage-Adjusted Treatment Recommendation. Orthop J Sports Med. 2021;9(7):23259671211016850](https://doi.org/10.1177/23259671211016850). Access: `full_text`. Type: `systematic_review`.
- [Moore D, Semciw AI, Pizzari T. A systematic review and meta-analysis of common therapeutic exercises that generate highest muscle activity in the gluteus medius and gluteus minimus segments. Int J Sports Phys Ther. 2020;15(6):856-881](https://doi.org/10.26603/ijspt20200856). Access: `abstract_only`. Type: `systematic_review`.
- [Patricio Cordeiro TT, Rocha EAB, Scattone Silva R. Effects of exercise-based interventions on gluteal tendinopathy. Systematic review with meta-analysis. Sci Rep. 2024;14:3343](https://doi.org/10.1038/s41598-024-53283-x). Access: `full_text`. Type: `systematic_review`.
- [Plinsinga ML, Ross MH, Coombes BK, Vicenzino B. Physical findings differ between individuals with greater trochanteric pain syndrome and healthy controls: A systematic review with meta-analysis. Musculoskelet Sci Pract. 2019;43:83-90](https://doi.org/10.1016/j.msksp.2019.07.009). Access: `abstract_only`. Type: `systematic_review`.
- [Rhim HC, Shin J, Beling A, et al. Extracorporeal Shockwave Therapy for Greater Trochanteric Pain Syndrome: A Systematic Review with Meta-Analysis of Randomized Clinical Trials. JBJS Rev. 2024;12(8)](https://doi.org/10.2106/JBJS.RVW.24.00091). Access: `abstract_only`. Type: `systematic_review`.
- [Slawaska-Eng D, Veilleux A, Thebaud A, et al. The limited impact of randomized controlled trials on the management of greater trochanteric pain syndrome as demonstrated by fragility Indices: A citation analysis. J ISAKOS. 2025;11:100846](https://doi.org/10.1016/j.jisako.2025.100846). Access: `abstract_only`. Type: `systematic_review`.
- [Spencer T, Nasser A, Brown NAT, et al. Low quality evidence supports surgery for gluteal tendon tears, no non-surgical evidence was identified: a systematic review. BMC Musculoskelet Disord. 2026;27(1):191](https://doi.org/10.1186/s12891-026-09519-0). Access: `abstract_only`. Type: `systematic_review`.
- [Swinton PA, Shim JSC, Pavlova AV, et al. What are small, medium and large effect sizes for exercise treatments of tendinopathy? A systematic review and meta-analysis. BMJ Open Sport Exerc Med. 2023;9(1):e001389](https://doi.org/10.1136/bmjsem-2022-001389). Access: `abstract_only`. Type: `systematic_review`. Also cited by: [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md).
- [Wang SQ, Guo NY, Liu W, et al. Effect of conservative treatment on greater trochanteric pain syndrome: a systematic review and network meta-analysis of randomized controlled trials. J Orthop Surg Res. 2025;20:126](https://doi.org/10.1186/s13018-025-05477-w). Access: `full_text`. Type: `systematic_review`.

### Cochrane record (1)

- [Bierma-Zeinstra et al. Interventions for lateral hip pain (tendinopathy or bursitis). Cochrane Database Syst Rev. 2011;CD008924. Title and DOI metadata only: the Cochrane page returned HTTP 403, so its content, and whether it is a full review or a protocol, is unknown](https://doi.org/10.1002/14651858.CD008924). Access: `registry_or_summary`. Type: `cochrane_record`.

### RCT (12)

- [Brennan KL, Allen BC, Maldonado YM. Dry Needling Versus Cortisone Injection in the Treatment of Greater Trochanteric Pain Syndrome. J Orthop Sports Phys Ther. 2017;47(4):232-239](https://doi.org/10.2519/jospt.2017.6994). Access: `abstract_only`. Type: `rct`.
- [Clifford C, Paul L, Syme G, Millar NL. Isometric versus isotonic exercise for greater trochanteric pain syndrome: a randomised controlled pilot study. BMJ Open Sport Exerc Med. 2019;5(1):e000558](https://doi.org/10.1136/bmjsem-2019-000558). Access: `full_text`. Type: `rct`.
- [French HP, Almousa S, Mellor R, Grimaldi A, et al. EDucation and eXercise for gluteal tendinopathy in an Irish context (EDX-Ireland): findings from the LEAP-Ireland feasibility randomised controlled trial. Musculoskelet Sci Pract. 2026;85:103607](https://doi.org/10.1016/j.msksp.2026.103607). Access: `abstract_only`. Type: `rct`.
- [Ganderton C, Semciw A, Cook J, Moreira E, Pizzari T. Gluteal Loading Versus Sham Exercises to Improve Pain and Dysfunction in Postmenopausal Women with Greater Trochanteric Pain Syndrome: A Randomized Controlled Trial. J Womens Health (Larchmt). 2018;27(6):815-829](https://doi.org/10.1089/jwh.2017.6729). Access: `abstract_only`. Type: `rct`.
- [Heaver C, Pinches M, Kuiper JH, et al. Greater trochanteric pain syndrome: focused shockwave therapy versus an ultrasound guided injection: a randomised control trial. Hip Int. 2023;33(3):490-499](https://doi.org/10.1177/11207000211060396). Access: `abstract_only`. Type: `rct`.
- [McMillan RM, Ganderton CL, Cook J, Semciw AI, Long DM, Pizzari T. Does Menopausal Hormone Therapy, Exercise, or Both Improve Pain and Function in Postmenopausal Women With Greater Trochanteric Pain Syndrome? A 2 x 2 Factorial Randomized Clinical Trial. Am J Sports Med. 2022;50(2):515-525](https://doi.org/10.1177/03635465211061142). Access: `abstract_only`. Type: `rct`.
- [Mellor R, Bennell K, Grimaldi A, et al. Education plus exercise versus corticosteroid injection use versus a wait and see approach on global outcome and pain from gluteal tendinopathy: prospective, single blinded, randomised clinical trial. BMJ. 2018;361:k1662](https://doi.org/10.1136/bmj.k1662). Access: `full_text`. Type: `rct`.
- [Mellor R, Kasza J, Grimaldi A, Hodges P, Bennell K, Vicenzino B. Mediators and Moderators of Education Plus Exercise on Perceived Improvement in Individuals With Gluteal Tendinopathy. J Orthop Sports Phys Ther. 2022;52(12):826-836](https://doi.org/10.2519/jospt.2022.11261). Access: `abstract_only`. Type: `rct`.
- [Nissen MJ, Brulhart L, Faundez A, Finckh A, Courvoisier DS, Genevay S. Glucocorticoid injections for greater trochanteric pain syndrome: a randomised double-blind placebo-controlled (GLUTEAL) trial. Clin Rheumatol. 2019;38(3):647-655](https://doi.org/10.1007/s10067-018-4309-6). Access: `abstract_only`. Type: `rct`.
- [Thomaz de Aquino Nava G, Prudencio CB, Mellor R, et al. Motor control exercises were not superior to general exercises for women with greater trochanteric pain syndrome: A randomized controlled trial. J Sci Med Sport. 2025;28(10):806-812](https://doi.org/10.1016/j.jsams.2025.04.012). Access: `abstract_only`. Type: `rct`.
- [Wheeler PC, Dudson C, Calver R, et al. Three Sessions of Radial Extracorporeal Shockwave Therapy Gives No Additional Benefit Over 'Minimal-Dose' Radial Extracorporeal Shockwave Therapy for Patients With Chronic Greater Trochanteric Pain Syndrome. Clin J Sport Med. 2022;32(1):e7-e18](https://doi.org/10.1097/JSM.0000000000000880). Access: `abstract_only`. Type: `rct`.
- [Wilson R, Abbott JH, Mellor R, Grimaldi A, Bennell K, Vicenzino B. Education plus exercise for persistent gluteal tendinopathy improves quality of life and is cost-effective compared with corticosteroid injection and wait and see. J Physiother. 2023;69(1):35-41](https://doi.org/10.1016/j.jphys.2022.11.007). Access: `abstract_only`. Type: `rct`.

### Non randomised trial (1)

- [Rompe JD, Segal NA, Cacchio A, Furia JP, Morral A, Maffulli N. Home training, local corticosteroid injection, or radial shock wave therapy for greater trochanter pain syndrome. Am J Sports Med. 2009;37(10):1981-90](https://doi.org/10.1177/0363546509334374). Access: `abstract_only`. Type: `non_randomised_trial`.

### Observational (5)

- [Carney G, Fitzpatrick J. Is there structural change on MRI in gluteal tendinopathy after treatment? Single outcome measure extension of an RCT. BMC Med Imaging. 2023;23:179](https://doi.org/10.1186/s12880-023-01150-y). Access: `full_text`. Type: `observational`.
- [French HP, Woodley SJ, Fearon A, O'Connor L, Grimaldi A. Physiotherapy management of greater trochanteric pain syndrome (GTPS): an international survey of current physiotherapy practice. Physiotherapy. 2020;109:111-120](https://doi.org/10.1016/j.physio.2019.05.002). Access: `abstract_only`. Type: `observational`.
- [Grimaldi A, Nasser A, Mellor R, Vicenzino B. Functional impact of gluteal tendinopathy: A secondary cross-sectional analysis of baseline data from the LEAP randomised clinical trial. Braz J Phys Ther. 2026;30(3):101583](https://doi.org/10.1016/j.bjpt.2026.101583). Access: `full_text`. Type: `observational`.
- [Hogsholt M, Kierkegaard-Brochner S, Lange J, et al. Physiotherapist-led education and exercise for patients with MRI-verified hip abductor tendon pathology: a prospective cohort study. BMJ Open. 2026;16(9):e120837](https://doi.org/10.1136/bmjopen-2026-120837). Access: `full_text`. Type: `observational`.
- [Plinsinga ML, Coombes BK, Mellor R, et al. Psychological factors not strength deficits are associated with severity of gluteal tendinopathy: A cross-sectional study. Eur J Pain. 2018;22(6):1124-1133](https://doi.org/10.1002/ejp.1199). Access: `abstract_only`. Type: `observational`.

### Diagnostic accuracy (2)

- [Ganderton C, Semciw A, Cook J, Pizzari T. Demystifying the Clinical Diagnosis of Greater Trochanteric Pain Syndrome in Women. J Womens Health (Larchmt). 2017;26(6):633-643](https://doi.org/10.1089/jwh.2016.5889). Access: `abstract_only`. Type: `diagnostic_accuracy`.
- [Kinsella R, Semciw AI, Hawke LJ, Stoney J, Choong PFM, Dowsey MM. Diagnostic Accuracy of Clinical Tests for Assessing Greater Trochanteric Pain Syndrome: A Systematic Review With Meta-analysis. J Orthop Sports Phys Ther. 2024;54(1):26-49](https://doi.org/10.2519/jospt.2023.11890). Access: `abstract_only`. Type: `diagnostic_accuracy`.

### Biomechanical (9)

- [Allison K, Hall M, Hodges PW, et al. Gluteal tendinopathy and hip osteoarthritis: Different pathologies, different hip biomechanics. Gait Posture. 2018;61:459-465](https://doi.org/10.1016/j.gaitpost.2018.02.011). Access: `abstract_only`. Type: `biomechanical`.
- [Allison K, Salomoni SE, Bennell KL, et al. Hip abductor muscle activity during walking in individuals with gluteal tendinopathy. Scand J Med Sci Sports. 2018;28(2):686-695](https://doi.org/10.1111/sms.12942). Access: `abstract_only`. Type: `biomechanical`.
- [Allison K, Vicenzino B, Bennell KL, Wrigley TV, Grimaldi A, Hodges PW. Kinematics and kinetics during stair ascent in individuals with Gluteal Tendinopathy. Clin Biomech (Bristol). 2016;40:37-44](https://doi.org/10.1016/j.clinbiomech.2016.10.003). Access: `abstract_only`. Type: `biomechanical`.
- [Collings TJ, Bourne MN, Barrett RS, et al. Gluteal Muscle Forces during Hip-Focused Injury Prevention and Rehabilitation Exercises. Med Sci Sports Exerc. 2023;55(4):650-660](https://doi.org/10.1249/MSS.0000000000003091). Access: `abstract_only`. Type: `biomechanical`.
- [Collings TJ, Bourne MN, Barrett RS, et al. Reconsidering Exercise Selection with EMG: Poor Agreement between Ranking Hip Exercises with Gluteal EMG and Muscle Force. Med Sci Sports Exerc. 2025;57(9):1829-1837](https://doi.org/10.1249/MSS.0000000000003733). Access: `abstract_only`. Type: `biomechanical`.
- [Derksen A, Wicke C, Jakubowitz E, et al. Load Management After Gluteal Tendon Repair: A Controlled Laboratory Study. J Orthop Res. 2026;44(1):e70107](https://doi.org/10.1002/jor.70107). Access: `full_text`. Type: `biomechanical`.
- [Ganderton C, Pizzari T, Harle T, Cook J, Semciw A. A comparison of gluteus medius, gluteus minimus and tensor facia latae muscle activation during gait in post-menopausal women with and without greater trochanteric pain syndrome. J Electromyogr Kinesiol. 2017;33:39-47](https://doi.org/10.1016/j.jelekin.2017.01.004). Access: `abstract_only`. Type: `biomechanical`.
- [Moore D, Pizzari T, McClelland J, Semciw AI. Rehabilitation Exercises for the Gluteus Medius Muscle Segments: An Electromyography Study. J Sport Rehabil. 2019;28(8)](https://doi.org/10.1123/jsr.2018-0340). Access: `abstract_only`. Type: `biomechanical`.
- [Wang S, Serbin R, Bonvillain K, et al. Influence of hip morphology on gluteal muscle biomechanics: a computational modeling study. BMC Musculoskelet Disord. 2025;26(1):35](https://doi.org/10.1186/s12891-024-08136-z). Access: `full_text`. Type: `biomechanical`.

### RCT protocol (4)

- [Almousa S, Vicenzino B, Mellor R, et al. An EDucation and eXercise intervention for gluteal tendinopathy in an Irish setting: a protocol for a feasibility randomised clinical trial (LEAP-Ireland RCT). HRB Open Res. 2023;6:76](https://doi.org/10.12688/hrbopenres.13796.2). Access: `full_text`. Type: `rct_protocol`.
- [Mellor R, Grimaldi A, Wajswelner H, et al. Exercise and load modification versus corticosteroid injection versus 'wait and see' for persistent gluteus medius/minimus tendinopathy (the LEAP trial): a protocol for a randomised clinical trial. BMC Musculoskelet Disord. 2016;17:196](https://doi.org/10.1186/s12891-016-1043-6). Access: `full_text`. Type: `rct_protocol`.
- [Morin Melas T, Bjorneboe J, Juel NG, et al. Self-management versus usual care for greater trochanteric pain syndrome (the HIPS trial): study protocol for a randomised controlled trial. BMJ Open. 2025;15(4):e090688](https://doi.org/10.1136/bmjopen-2024-090688). Access: `full_text`. Type: `rct_protocol`.
- [Thomaz de Aquino Nava G, Baldini Prudencio C, Krasic Alaiti R, et al. Motor control exercises versus general exercises for greater trochanteric pain syndrome: A protocol of a randomized controlled trial. PLoS One. 2022;17(6):e0269230](https://doi.org/10.1371/journal.pone.0269230). Access: `full_text`. Type: `rct_protocol`.

### Trial registry (1)

- [COMET Initiative record 1468. Development of a core outcome set for clinical trials in Greater Trochanteric Pain Syndrome (Gluteal Tendinopathy)](https://comet-initiative.org/Studies/Details/1468). Access: `registry_or_summary`. Type: `trial_registry`. No DOI or PMID recorded; links to the source page.

### Narrative review (3)

- [Gonzalez-de-la-Flor A. Optimizing Hip Abductor Strengthening for Lower Extremity Rehabilitation: A Narrative Review on the Role of Monster Walk and Lateral Band Walk. J Funct Morphol Kinesiol. 2025;10(3)](https://doi.org/10.3390/jfmk10030294). Access: `full_text`. Type: `narrative_review`.
- [Grimaldi A, Mellor R, Hodges P, Bennell K, Wajswelner H, Vicenzino B. Gluteal Tendinopathy: A Review of Mechanisms, Assessment and Management. Sports Med. 2015;45(8):1107-1119](https://doi.org/10.1007/s40279-015-0336-5). Access: `abstract_only`. Type: `narrative_review`.
- [Grimaldi A, Mellor R, Nasser A, Vicenzino B, Hunter DJ. Current and future advances in practice: tendinopathies of the hip. Rheumatol Adv Pract. 2024;8(2):rkae022](https://doi.org/10.1093/rap/rkae022). Access: `full_text`. Type: `narrative_review`.

### Review (1)

- [Ebert JR, Fearon AM, Smith PN, Janes GC. Recommendations in the rehabilitation of patients undergoing hip abductor tendon repair: a systematic literature search and evidence based rehabilitation protocol. Arch Orthop Trauma Surg. 2022;142(11):3165-3182](https://doi.org/10.1007/s00402-021-03952-7). Access: `abstract_only`. Type: `review`.

### Clinical review (1)

- [Wilson JJ, Furukawa M. Evaluation of the patient with hip pain. Am Fam Physician. 2014;89(1):27-34](https://pubmed.ncbi.nlm.nih.gov/24444505/). Access: `full_text`. Type: `clinical_review`.

### Expert review (1)

- [Grimaldi A, Ganderton C, Nasser A. Gluteal tendinopathy masterclass: Refuting the myths and engaging with the evidence. Musculoskelet Sci Pract. 2025;76:103253](https://doi.org/10.1016/j.msksp.2025.103253). Access: `abstract_only`. Type: `expert_review`.

### Clinical commentary (1)

- [Grimaldi A, Fearon A. Gluteal Tendinopathy: Integrating Pathomechanics and Clinical Features in Its Management. J Orthop Sports Phys Ther. 2015;45(11):910-922](https://doi.org/10.2519/jospt.2015.5829). Access: `full_text`. Type: `clinical_commentary`.

### Expert opinion (1)

- [Bremer T, Lack S, Fearon A, Morrissey D. Best practice for patients with gluteal tendinopathy: A qualitative exploration of expert clinical reasoning and management. Physiotherapy. 2026;132:101871](https://doi.org/10.1016/j.physio.2025.101871). Access: `abstract_only`. Type: `expert_opinion`.

### Clinician fact sheet (1)

- [Gold J, Murray I, Macpherson G. Greater Trochanteric Pain Syndrome: Fact Sheet and Advice for GPs and referrers to use with patients. NHS Lothian RefHelp. December 2025](https://apps.nhslothian.scot/files/sites/2/GTPS-FACT-SHEET-for-Ref-help-1.pdf). Access: `full_text`. Type: `clinician_fact_sheet`. No DOI or PMID recorded; links to the source page.

## Lumbar radiculopathy (sciatica)

Condition file: [lumbar_radiculopathy](lumbar_radiculopathy.md)

### Guideline (6)

- [Delitto A, George SZ, Van Dillen L, et al. Low back pain. J Orthop Sports Phys Ther. 2012;42(4):A1-A57](https://doi.org/10.2519/jospt.2012.42.4.A1). Access: `full_text`. Type: `guideline`.
- [George SZ, Fritz JM, Silfies SP, et al. Interventions for the Management of Acute and Chronic Low Back Pain: Revision 2021. J Orthop Sports Phys Ther. 2021;51(11):CPG1-CPG60](https://doi.org/10.2519/jospt.2021.0304). Access: `full_text`. Type: `guideline`. Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [Kreiner DS, Hwang SW, Easa JE, et al; North American Spine Society. An evidence-based clinical guideline for the diagnosis and treatment of lumbar disc herniation with radiculopathy. Spine J. 2014;14(1):180-191](https://doi.org/10.1016/j.spinee.2013.08.003). Access: `registry_or_summary`. Type: `guideline`.
- [Luites JWH, Kuijer PPFM, Hulshof CTJ, et al. The Dutch Multidisciplinary Occupational Health Guideline to Enhance Work Participation Among Low Back Pain and Lumbosacral Radicular Syndrome Patients. J Occup Rehabil. 2022;32(3):337-352](https://doi.org/10.1007/s10926-021-09993-4). Access: `full_text`. Type: `guideline`.
- [Skelly AC, Kansagara D, Oswald J, Chou R; Acute Low Back Pain Guideline Project. Assessment and Treatment of Acute Low Back Pain: A Multidisciplinary Clinical Practice Guideline. Pain Med. 2026 Sep 2:pnag110](https://doi.org/10.1093/pm/pnag110). Access: `abstract_only`. Type: `guideline`. Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [Stochkendahl MJ, Kjaer P, Hartvigsen J, et al. National Clinical Guidelines for non-surgical treatment of patients with recent onset low back pain or lumbar radiculopathy. Eur Spine J. 2018;27(1):60-75](https://doi.org/10.1007/s00586-017-5099-2). Access: `abstract_only`. Type: `guideline`. Other files record: `registry_or_summary` in [lumbar_radiculopathy](lumbar_radiculopathy.md). Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).

### Guideline summary (1)

- [Pangarkar SS, Kang DG, Sandbrink F, et al. VA/DoD Clinical Practice Guideline: Diagnosis and Treatment of Low Back Pain. J Gen Intern Med. 2019;34(11):2620-2629](https://doi.org/10.1007/s11606-019-05086-4). Access: `not_reconfirmed`. Type: `guideline_summary`.

### Secondary guideline summary (4)

- [Briggs AM, Sumi Y, Banerjee A. The World Health Organization guideline for non-surgical management of chronic primary low back pain in adults: implications for equitable care and strengthening health systems globally. Glob Health Res Policy. 2025;10(1):26](https://doi.org/10.1186/s41256-025-00426-w). Access: `full_text`. Type: `secondary_guideline_summary`. Other files record: `registry_or_summary` in [lumbar_radiculopathy](lumbar_radiculopathy.md). Also typed: `guideline_summary`. Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [Swart N, Apeldoorn A, Conijn D, Meerhoff G, Ostelo R. Revised Dutch guideline physical therapy low back pain and lumbosacral radicular syndrome. World Physiotherapy Congress abstract, 2023](https://world.physio/congress-proceeding/revised-dutch-guideline-physical-therapy-low-back-pain-and-lumbosacral). Access: `registry_or_summary`. Type: `secondary_guideline_summary`. No DOI or PMID recorded; links to the source page.
- [van Wambeke P, Desomer A, Jonckheer P, Depreitere B. The Belgian national guideline on low back pain and radicular pain: key roles for rehabilitation, assessment of rehabilitation potential and the PRM specialist. Eur J Phys Rehabil Med. 2020;56(2):220-227](https://doi.org/10.23736/S1973-9087.19.05983-5). Access: `abstract_only`. Type: `secondary_guideline_summary`.
- [World Health Organization. WHO releases guidelines on chronic low back pain. News release, 7 December 2023](https://www.who.int/news/item/07-12-2023-who-releases-guidelines-on-chronic-low-back-pain). Access: `full_text`. Type: `secondary_guideline_summary`. Other files record: `registry_or_summary` in [nonspecific_low_back_pain](nonspecific_low_back_pain.md). Also typed: `guideline_summary`. Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md). No DOI or PMID recorded; links to the source page.

### Local clinical pathway (1)

- [Humber and North Yorkshire Health and Care Partnership. Back pain (cauda equina syndrome) pathway, York. Last modified 19 November 2025; the page states the pathway is under review](https://hnyppr.org.uk/web/york/w/back-pain-cauda-equina-syndrome-pathway). Access: `full_text`. Type: `local_clinical_pathway`. No DOI or PMID recorded; links to the source page.

### Consensus statement (3)

- [Lee J, Gupta S, Price C, Baranowski AP; British Pain Society. Low back and radicular pain: a pathway for care developed by the British Pain Society. Br J Anaesth. 2013;111(1):112-120](https://doi.org/10.1093/bja/aet172). Access: `abstract_only`. Type: `consensus_statement`.
- [Thoomes E, Falla D, Cleland JA, Fernandez-de-Las-Penas C, Gallina A, de Graaf M. Conservative management for lumbar radiculopathy based on the stage of the disorder: a Delphi study. Disabil Rehabil. 2023;45(21):3539-3548](https://doi.org/10.1080/09638288.2022.2130448). Access: `abstract_only`. Type: `consensus_statement`.
- [Zhang YH, et al. Exercise for Neuropathic Pain: A Systematic Review and Expert Consensus. Front Med (Lausanne). 2021](https://doi.org/10.3389/fmed.2021.756940). Access: `full_text`. Type: `consensus_statement`.

### Systematic review (22)

- [Arslan S, et al. The effect of exercise in the treatment of lumbar disc herniation: a systematic review. Acta Neurol Belg. 2025;125(5):1209-1224](https://doi.org/10.1007/s13760-025-02767-2). Access: `abstract_only`. Type: `systematic_review`.
- [Baptista FM, et al. Effectiveness of Neural Mobilisation on Pain Intensity, Functional Status, and Physical Performance in Adults with Musculoskeletal Pain - A Systematic Review with Meta-Analysis. Clin Rehabil. 2024;38(2):145-183](https://doi.org/10.1177/02692155231215216). Access: `full_text`. Type: `systematic_review`.
- [Basson A, Olivier B, Ellis R, Coppieters M, et al. The Effectiveness of Neural Mobilization for Neuromusculoskeletal Conditions: A Systematic Review and Meta-analysis. J Orthop Sports Phys Ther. 2017;47(9):593-615](https://doi.org/10.2519/jospt.2017.7117). Access: `abstract_only`. Type: `systematic_review`.
- [Chiu CC, et al. The probability of spontaneous regression of lumbar herniated disc: a systematic review. Clin Rehabil. 2015;29(2):184-95](https://doi.org/10.1177/0269215514540919). Access: `abstract_only`. Type: `systematic_review`.
- [Hayden JA, et al. Exercise therapy for chronic low back pain. Cochrane Database Syst Rev. 2021;9(9):CD009790](https://doi.org/10.1002/14651858.CD009790.pub2). Access: `full_text`. Type: `systematic_review`. Other files record: `abstract_only` in [lumbar_radiculopathy](lumbar_radiculopathy.md). Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [Hennemann V, et al. The McKenzie Method delivered by credentialed therapists for chronic low back pain with directional preference: systematic review with meta-analysis. J Man Manip Ther. 2025;33(2):96-111](https://doi.org/10.1080/10669817.2024.2408084). Access: `abstract_only`. Type: `systematic_review`. Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [Khorami AK, Oliveira CB, Maher CG, et al. Recommendations for Diagnosis and Treatment of Lumbosacral Radicular Pain: A Systematic Review of Clinical Practice Guidelines. J Clin Med. 2021;10(11):2482](https://doi.org/10.3390/jcm10112482). Access: `full_text`. Type: `systematic_review`.
- [Lewis RA, et al. Comparative clinical effectiveness of management strategies for sciatica: systematic review and network meta-analyses. Spine J. 2015;15(6):1461-77](https://doi.org/10.1016/j.spinee.2013.08.049). Access: `abstract_only`. Type: `systematic_review`.
- [Lin LH, Lin TY, Chang KV, Wu WT, Ozcakar L. Neural Mobilization for Reducing Pain and Disability in Patients with Lumbar Radiculopathy: A Systematic Review and Meta-Analysis. Life (Basel). 2023;13(12):2255](https://doi.org/10.3390/life13122255). Access: `full_text`. Type: `systematic_review`.
- [Liu C, et al. Surgical versus non-surgical treatment for sciatica: systematic review and meta-analysis of randomised controlled trials. BMJ. 2023;381:e070730](https://doi.org/10.1136/bmj-2022-070730). Access: `full_text`. Type: `systematic_review`.
- [Luijsterburg PA, et al. Effectiveness of conservative treatments for the lumbosacral radicular syndrome: a systematic review. Eur Spine J. 2007;16(7):881-99](https://doi.org/10.1007/s00586-007-0367-1). Access: `full_text`. Type: `systematic_review`.
- [Neto T, et al. Effects of lower body quadrant neural mobilization in healthy and low back pain populations: A systematic review and meta-analysis. Musculoskelet Sci Pract. 2017;27:14-22](https://doi.org/10.1016/j.msksp.2016.11.014). Access: `abstract_only`. Type: `systematic_review`.
- [Peacock M, Douglas S, Nair P. Neural mobilization in low back and radicular pain: a systematic review. J Man Manip Ther. 2023;31(1):4-12](https://doi.org/10.1080/10669817.2022.2065599). Access: `not_reconfirmed`. Type: `systematic_review`.
- [Pourahmadi M, et al. Effectiveness of Slump Stretching on Low Back Pain: A Systematic Review and Meta-analysis. Pain Med. 2019;20(2):378-396](https://doi.org/10.1093/pm/pny208). Access: `abstract_only`. Type: `systematic_review`. Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [Price MR, Mead KE, Cowell DM, et al. Medication recommendations for treatment of lumbosacral radiculopathy: a systematic review of clinical practice guidelines. PM R. 2024;16(10):1128-1142](https://doi.org/10.1002/pmrj.13142). Access: `abstract_only`. Type: `systematic_review`.
- [Shinde AP, et al. Evidence-Based Conservative Treatment Strategies for Lumbar Radiculopathy: A Systematic Review. Cureus. 2026;18(6):e110554](https://doi.org/10.7759/cureus.110554). Access: `abstract_only`. Type: `systematic_review`.
- [van der Windt DA, Simons E, Riphagen II, et al. Physical examination for lumbar radiculopathy due to disc herniation in patients with low-back pain. Cochrane Database Syst Rev. 2010](https://doi.org/10.1002/14651858.CD007431.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Vanti C, et al. Effectiveness of Mechanical Traction for Lumbar Radiculopathy: A Systematic Review and Meta-Analysis. Phys Ther. 2021;101(3):pzaa231](https://doi.org/10.1093/ptj/pzaa231). Access: `abstract_only`. Type: `systematic_review`.
- [Wegner I, Widyahening IS, van Tulder MW, Blomberg SE, et al. Traction for low-back pain with or without sciatica. Cochrane Database Syst Rev. 2013;(8):CD003010](https://doi.org/10.1002/14651858.CD003010.pub5). Access: `abstract_only`. Type: `systematic_review`.
- [Zaina F, Cote P, Cancelliere C, et al. A Systematic Review of Clinical Practice Guidelines for Persons With Non-specific Low Back Pain With and Without Radiculopathy: Identification of Best Evidence for Rehabilitation to Develop the WHO's Package of Interventions for Rehabilitation. Arch Phys Med Rehabil. 2023;104(11):1913-1927](https://doi.org/10.1016/j.apmr.2023.02.022). Access: `abstract_only`. Type: `systematic_review`.
- [Zhu Z, et al. Effectiveness of Nonsurgical Interventions for Patients With Acute and Subacute Sciatica: A Systematic Review With Network Meta-Analysis. J Orthop Sports Phys Ther. 2025;55(6):1-12](https://doi.org/10.2519/jospt.2025.13068). Access: `abstract_only`. Type: `systematic_review`.
- [Zhu Z, Schouten T, Strijkers R, Koes B, Gerger H, Chiarotto A. Effectiveness of non-surgical interventions for patients with chronic sciatica: A systematic review with network meta-analysis. J Pain. 2025](https://doi.org/10.1016/j.jpain.2025.105431). Access: `abstract_only`. Type: `systematic_review`.

### RCT (19)

- [Albert HB, Manniche C. The efficacy of systematic active conservative treatment for patients with severe sciatica: a single-blind, randomized, clinical, controlled trial. Spine. 2012](https://doi.org/10.1097/BRS.0b013e31821ace7f). Access: `abstract_only`. Type: `rct`.
- [Bailey CS, Rasoulinejad P, Taylor D, Sequeira K, et al. Surgery versus Conservative Care for Persistent Sciatica Lasting 4 to 12 Months. N Engl J Med. 2020;382(12):1093-1102](https://doi.org/10.1056/NEJMoa1912658). Access: `abstract_only`. Type: `rct`.
- [Danazumi MS, Nuhu JM, Ibrahim SU, Falke MA, et al. Effects of spinal manipulation or mobilization as an adjunct to neurodynamic mobilization for lumbar disc herniation with radiculopathy: a randomized clinical trial. J Man Manip Ther. 2023;31(6):408-420](https://doi.org/10.1080/10669817.2023.2192975). Access: `not_reconfirmed`. Type: `rct`.
- [Ferreira G, Stieven F, Araujo F, Wiebusch M, et al. Neurodynamic treatment did not improve pain and disability at two weeks in patients with chronic nerve-related leg pain: a randomised trial. J Physiother. 2016;62(4):197-202](https://doi.org/10.1016/j.jphys.2016.08.007). Access: `abstract_only`. Type: `rct`.
- [Foster NE, Konstantinou K, Lewis M, Ogollah R, et al. Stratified versus usual care for the management of primary care patients with sciatica: the SCOPiC RCT. Health Technol Assess. 2020;24(49):1-130](https://doi.org/10.3310/hta24490). Access: `abstract_only`. Type: `rct`.
- [Fritz JM, Lane E, McFadden M, Brennan G, et al. Physical Therapy Referral From Primary Care for Acute Back Pain With Sciatica: A Randomized Controlled Trial. Ann Intern Med. 2021;174(1):8-17](https://doi.org/10.7326/M20-4187). Access: `full_text`. Type: `rct`.
- [Gunaydin G, Karaduman MN, Pala OO, Hazar Z, Citaker S. Neurodynamic mobilization for the management of patients with lumbar disc herniation: A randomized controlled study. J Back Musculoskelet Rehabil. 2026](https://doi.org/10.1177/10538127261480256). Access: `abstract_only`. Type: `rct`.
- [Hahne AJ, Ford JJ, Hinman RS, et al. Individualized functional restoration as an adjunct to advice for lumbar disc herniation with associated radiculopathy. A preplanned subgroup analysis of a randomized controlled trial. Spine J. 2017](https://doi.org/10.1016/j.spinee.2016.10.004). Access: `abstract_only`. Type: `rct`.
- [Hofstee DJ, Gijtenbeek JMM, Hoogland PH, van Houwelingen HC, et al. Westeinde sciatica trial: randomized controlled study of bed rest and physiotherapy for acute sciatica. J Neurosurg. 2002;96(1 Suppl):45-9](https://doi.org/10.3171/spi.2002.96.1.0045). Access: `abstract_only`. Type: `rct`.
- [Kilpikoski S, Hakkinen AH, Repo JP, et al. The McKenzie Method versus guideline-based advice in the treatment of sciatica: 24-month outcomes of a randomised clinical trial. Clin Rehabil. 2024](https://doi.org/10.1177/02692155231196393). Access: `abstract_only`. Type: `rct`.
- [Luijsterburg PA, Verhagen AP, Ostelo RW, et al. Physical therapy plus general practitioners' care versus general practitioners' care alone for sciatica: a randomised clinical trial with a 12-month follow-up. Eur Spine J. 2008](https://doi.org/10.1007/s00586-007-0569-6). Access: `full_text`. Type: `rct`.
- [Nahid ZBS, Ahmed F, Islam MF, Rahman Z, et al. Effectiveness of Shacklock's Neural Mobilization for Acute and Sub-Acute Lumbar Disc Prolapsed: A Randomized Controlled Trial. Health Sci Rep. 2025](https://doi.org/10.1002/hsr2.70872). Access: `full_text`. Type: `rct`.
- [Plaza-Manzano G, Cancela-Cilleruelo I, Fernandez-de-Las-Penas C, Cleland JA, et al. Effects of Adding a Neurodynamic Mobilization to Motor Control Training in Patients With Lumbar Radiculopathy Due to Disc Herniation: A Randomized Clinical Trial. Am J Phys Med Rehabil. 2020;99(2):124-132](https://doi.org/10.1097/PHM.0000000000001295). Access: `abstract_only`. Type: `rct`.
- [Pradhan A, J M. Effects of Neurodynamics Along With Conventional Exercises on Sciatica Patients: A Single-Blinded Randomized Clinical Trial. Cureus. 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC11151706/). Access: `full_text`. Type: `rct`. No DOI or PMID recorded; links to the source page.
- [Shacklock M, Rade M, Poznic S, Marcinko A, et al. Treatment of Sciatica and Lumbar Radiculopathy with an Intervertebral Foramen Opening Protocol: Pilot Study in a Hospital Emergency and In-patient Setting. Physiother Theory Pract. 2023;39(6):1178-1188](https://doi.org/10.1080/09593985.2022.2037797). Access: `abstract_only`. Type: `rct`.
- [Snook SH, Webster BS, McGorry RW, Fogleman MT, McCann KB. The reduction of chronic nonspecific low back pain through the control of early morning lumbar flexion. A randomized controlled trial. Spine. 1998;23(23):2601-7](https://doi.org/10.1097/00007632-199812010-00015). Access: `abstract_only`. Type: `rct`.
- [Sousa Filho LF, et al. Neurodynamic exercises provide no additional benefit to extension-oriented exercises in people with chronic low back-related leg pain and a directional preference: A randomized clinical trial. J Bodyw Mov Ther. 2022;30:140-147](https://doi.org/10.1016/j.jbmt.2022.01.007). Access: `abstract_only`. Type: `rct`.
- [Thackeray A, et al. The Effectiveness of Mechanical Traction Among Subgroups of Patients With Low Back Pain and Leg Pain: A Randomized Trial. J Orthop Sports Phys Ther. 2016;46(3):144-54](https://doi.org/10.2519/jospt.2016.6238). Access: `abstract_only`. Type: `rct`.
- [Vroomen PC, de Krom MC, Wilmink JT, Kester AD, et al. Lack of effectiveness of bed rest for sciatica. N Engl J Med. 1999;340(6):418-23](https://doi.org/10.1056/NEJM199902113400602). Access: `abstract_only`. Type: `rct`.

### Non randomised trial (1)

- [Boyd BS, Nee RJ, Smoot B. Safety of lower extremity neurodynamic exercises in adults with diabetes mellitus: a feasibility study. J Man Manip Ther. 2017;25(1):30-38](https://doi.org/10.1080/10669817.2016.1180772). Access: `full_text`. Type: `non_randomised_trial`.

### Observational (8)

- [Albert HB, Hauge E, Manniche C. Centralization in patients with sciatica: are pain responses to repeated movement and positioning associated with outcome or types of disc lesions? Eur Spine J. 2012;21(4):630-6](https://doi.org/10.1007/s00586-011-2018-9). Access: `full_text`. Type: `observational`.
- [Hahne AJ, Ford JJ, Hinman RS, Taylor NF, et al. Outcomes and adverse events from physiotherapy functional restoration for lumbar disc herniation with associated radiculopathy. Disabil Rehabil. 2011;33(17-18):1537-47](https://doi.org/10.3109/09638288.2010.533814). Access: `abstract_only`. Type: `observational`.
- [Mundt DJ, Kelsey JL, Golden AL, Panjabi MM, et al. An epidemiologic study of sports and weight lifting as possible risk factors for herniated lumbar and cervical discs. Am J Sports Med. 1993;21(6):854-60](https://doi.org/10.1177/036354659302100617). Access: `abstract_only`. Type: `observational`.
- [Mundt DJ, Kelsey JL, Golden AL, Pastides H, et al. An epidemiologic study of non-occupational lifting as a risk factor for herniated lumbar intervertebral disc. Spine. 1993;18(5):595-602](https://doi.org/10.1097/00007632-199304000-00012). Access: `abstract_only`. Type: `observational`.
- [Nordberg CL, Boesen M, Fournier GL, Bliddal H, et al. Positional changes in lumbar disc herniation during standing or lumbar extension: a cross-sectional weight-bearing MRI study. Eur Radiol. 2021;31(2):804-812](https://doi.org/10.1007/s00330-020-07132-w). Access: `abstract_only`. Type: `observational`.
- [Saal JA, Saal JS. Nonoperative treatment of herniated lumbar intervertebral disc with radiculopathy. An outcome study. Spine. 1989;14(4):431-7](https://doi.org/10.1097/00007632-198904000-00018). Access: `abstract_only`. Type: `observational`.
- [Stynes S, Konstantinou K, Ogollah R, Hay EM, Dunn KM. Clinical diagnostic model for sciatica developed in primary care patients with low back-related leg pain. PLoS One. 2018](https://doi.org/10.1371/journal.pone.0191852). Access: `abstract_only`. Type: `observational`.
- [Toribe A, et al. Dynamics of the Tibial Nerve During Straight Leg Raise Test: A Study of Individuals With Lumbar Disc Herniation. J Manipulative Physiol Ther. 2025;48(6-9):967-976](https://doi.org/10.1016/j.jmpt.2025.11.001). Access: `abstract_only`. Type: `observational`.

### Case report (1)

- [Alshanqity M, et al. Massive Lumbar Disc Extrusion and Cauda Equina Syndrome in a Female Athlete Following Deadlift Training. Cureus. 2025](https://doi.org/10.7759/cureus.100428). Access: `full_text`. Type: `case_report`. Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).

### Biomechanical (21)

- [Boyd BS, Topp KS, Coppieters MW. Impact of movement sequencing on sciatic and tibial nerve strain and excursion during the straight leg raise test in embalmed cadavers. J Orthop Sports Phys Ther. 2013;43(6):398-403](https://doi.org/10.2519/jospt.2013.4413). Access: `abstract_only`. Type: `biomechanical`.
- [Briar KJ, Gregory DE. Combined flexion and compression negatively impact the mechanical integrity of the annulus fibrosus. Eur Spine J. 2023;32(3):831-838](https://doi.org/10.1007/s00586-022-07518-y). Access: `abstract_only`. Type: `biomechanical`.
- [Callaghan JP, McGill SM. Intervertebral disc herniation: studies on a porcine model exposed to highly repetitive flexion/extension motion with compressive force. Clin Biomech. 2001;16(1):28-37](https://doi.org/10.1016/s0268-0033%2800%2900063-2). Access: `abstract_only`. Type: `biomechanical`.
- [Cholewicki J, McGill SM, Norman RW. Lumbar spine loads during the lifting of extremely heavy weights. Med Sci Sports Exerc. 1991;23(10):1179-86](https://pubmed.ncbi.nlm.nih.gov/1758295/). Access: `abstract_only`. Type: `biomechanical`.
- [Coppieters MW, Alshami AM, Babri AS, Souvlis T, et al. Strain and excursion of the sciatic, tibial, and plantar nerves during a modified straight leg raising test. J Orthop Res. 2006;24(9):1883-9](https://doi.org/10.1002/jor.20210). Access: `abstract_only`. Type: `biomechanical`.
- [Coppieters MW, Andersen LS, Johansen R, Giskegjerde PK, et al. Excursion of the Sciatic Nerve During Nerve Mobilization Exercises: An In Vivo Cross-sectional Study Using Dynamic Ultrasound Imaging. J Orthop Sports Phys Ther. 2015;45(10):731-7](https://doi.org/10.2519/jospt.2015.5743). Access: `abstract_only`. Type: `biomechanical`.
- [Ellis R, et al. Assessing Sciatic Nerve Excursion and Strain with Ultrasound Imaging during Forward Bending. Ultrasound Med Biol. 2021;47(9):2560-2569](https://doi.org/10.1016/j.ultrasmedbio.2021.05.018). Access: `abstract_only`. Type: `biomechanical`.
- [Ellis R, Osborne S, Whitfield J, Parmar P, et al. The effect of spinal position on sciatic nerve excursion during seated neural mobilisation exercises: an in vivo study using ultrasound imaging. J Man Manip Ther. 2017;25(2):98-105](https://doi.org/10.1179/2042618615Y.0000000020). Access: `full_text`. Type: `biomechanical`.
- [Ellis RF, Hing WA, McNair PJ. Comparison of longitudinal sciatic nerve movement with different mobilization exercises: an in vivo study utilizing ultrasound imaging. J Orthop Sports Phys Ther. 2012;42(8):667-75](https://doi.org/10.2519/jospt.2012.3854). Access: `abstract_only`. Type: `biomechanical`.
- [Gilbert KK, Smith MP, Sobczak S, James CR, et al. Effects of lower limb neurodynamic mobilization on intraneural fluid dispersion of the fourth lumbar nerve root: an unembalmed cadaveric investigation. J Man Manip Ther. 2015;23(5):239-45](https://doi.org/10.1179/2042618615Y.0000000009). Access: `abstract_only`. Type: `biomechanical`.
- [Hitier M, Vieira DCL, Cometti C, Durigan JLQ, Babault N. Technique-specific reduction of sciatic nerve stiffness through neurodynamic stretches at submaximal and maximal intensity in asymptomatic individuals. Eur J Appl Physiol. 2026;126(10):5409-5420](https://doi.org/10.1007/s00421-026-06320-8). Access: `abstract_only`. Type: `biomechanical`.
- [Johnson C, Reid JG. Lumbar compressive and shear forces during various trunk curl-up exercises. Clin Biomech. 1991;6(2):97-104](https://doi.org/10.1016/0268-0033%2891%2990006-C). Access: `abstract_only`. Type: `biomechanical`.
- [Rohlmann A, Claes LE, Bergmann G, Graichen F, et al. Comparison of intradiscal pressures and spinal fixator loads for different body positions and exercises. Ergonomics. 2001;44(8):781-94](https://doi.org/10.1080/00140130120943). Access: `abstract_only`. Type: `biomechanical`.
- [Sato K, Kikuchi S, Yonezawa T. In vivo intradiscal pressure measurement in healthy individuals and in patients with ongoing back problems. Spine. 1999;24(23):2468-74](https://doi.org/10.1097/00007632-199912010-00008). Access: `abstract_only`. Type: `biomechanical`.
- [Sayers MGL, Bachem C, Schutz P, Taylor WR, et al. The effect of elevating the heels on spinal kinematics and kinetics during the back squat in trained and novice weight trainers. J Sports Sci. 2020;38(9):1000-1008](https://doi.org/10.1080/02640414.2020.1738675). Access: `abstract_only`. Type: `biomechanical`.
- [Sjoberg M, Eiken O, Norrbrand L, Berg HE, Gutierrez-Farewik EM. Lumbar Loads and Muscle Activity During Flywheel and Barbell Leg Exercises. J Strength Cond Res. 2023;37(1):27-34](https://doi.org/10.1519/JSC.0000000000004163). Access: `abstract_only`. Type: `biomechanical`.
- [Swinton PA, Stewart A, Agouris I, Keogh JWL, et al. A biomechanical analysis of straight and hexagonal barbell deadlifts using submaximal loads. J Strength Cond Res. 2011;25(7):2000-9](https://doi.org/10.1519/JSC.0b013e3181e73f87). Access: `abstract_only`. Type: `biomechanical`.
- [Tampier C, Drake JDM, Callaghan JP, McGill SM. Progressive disc herniation: an investigation of the mechanism using radiologic, histochemical, and microscopic dissection techniques on a porcine model. Spine. 2007;32(25):2869-74](https://doi.org/10.1097/BRS.0b013e31815b64f5). Access: `abstract_only`. Type: `biomechanical`.
- [von Arx M, et al. From Stoop to Squat: A Comprehensive Analysis of Lumbar Loading Among Different Lifting Styles. Front Bioeng Biotechnol. 2021](https://doi.org/10.3389/fbioe.2021.769117). Access: `full_text`. Type: `biomechanical`. Also typed: `biomechanical_study`. Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [Wilke HJ, Neef P, Caimi M, Hoogland T, Claes LE. New in vivo measurements of pressures in the intervertebral disc in daily life. Spine. 1999;24(8):755-62](https://doi.org/10.1097/00007632-199904150-00005). Access: `abstract_only`. Type: `biomechanical`.
- [Yanagisawa O, Oshikawa T, Matsunaga N, Adachi G, et al. Acute Physiological Response of Lumbar Intervertebral Discs to High-load Deadlift Exercise. Magn Reson Med Sci. 2021;20(3):290-294](https://doi.org/10.2463/mrms.mp.2020-0052). Access: `abstract_only`. Type: `biomechanical`. Also typed: `observational_study`. Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).

### RCT protocol (3)

- [Correa LA, Bittencourt JV, Mainenti Pagnez MA, Mathieson S, et al. Neural management plus advice to stay active on clinical measures and sciatic neurodynamic for patients with chronic sciatica: Study protocol for a controlled randomised clinical trial. PLoS One. 2022;17(2):e0263152](https://doi.org/10.1371/journal.pone.0263152). Access: `full_text`. Type: `rct_protocol`.
- [Ferreira GE, Stieven F, Araujo F, Wiebusch M, et al. Neurodynamic treatment for patients with nerve-related leg pain: Protocol for a randomized controlled trial. J Bodyw Mov Ther. 2016;20(4):870-878](https://doi.org/10.1016/j.jbmt.2016.02.012). Access: `abstract_only`. Type: `rct_protocol`.
- [Hahne AJ, et al. Specific treatment of problems of the spine (STOPS): design of a randomised controlled trial comparing specific physiotherapy versus advice. BMC Musculoskelet Disord. 2011](https://doi.org/10.1186/1471-2474-12-104). Access: `full_text`. Type: `rct_protocol`.

### Narrative review (2)

- [Cherni B, et al. Beyond the Neutral Spine: A Narrative Review and Modern Framework for Low Back Injury Prevention in Deadlifting. Sports (Basel). 2026](https://doi.org/10.3390/sports14040151). Access: `full_text`. Type: `narrative_review`. Also cited by: [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
- [Ramirez VJ, et al. Low Back Biomechanics during Repetitive Deadlifts: A Narrative Review. IISE Trans Occup Ergon Hum Factors. 2022](https://pubmed.ncbi.nlm.nih.gov/34875981/). Access: `full_text`. Type: `narrative_review`.

### Review (1)

- [Konstantinou K, Dunn KM. Sciatica: review of epidemiological studies and prevalence estimates. Spine. 2008;33:2464-2472](https://doi.org/10.1097/BRS.0b013e318183a4a2). Access: `abstract_only`. Type: `review`.

### Expert review (1)

- [Todd NV, Kearsley N, Wright-Todd A. Assessment of cauda equina syndrome: new national guidelines and implications for primary care. Br J Gen Pract. 2025;75(757):381-383](https://doi.org/10.3399/BJGP.2025.0122). Access: `full_text`. Type: `expert_review`.

### Clinical commentary (1)

- [VanGelder LH, Hoogenboom BJ, Vaughn DW. A phased rehabilitation protocol for athletes with lumbar intervertebral disc herniation. Int J Sports Phys Ther. 2013](https://pubmed.ncbi.nlm.nih.gov/24175134/). Access: `full_text`. Type: `clinical_commentary`.

## Nonspecific low back pain

Condition file: [nonspecific_low_back_pain](nonspecific_low_back_pain.md)

### Guideline (2)

- [Apeldoorn AT, Swart NM, Conijn D, Meerhoff GA, et al. Management of low back pain and lumbosacral radicular syndrome: the Guideline of the Royal Dutch Society for Physical Therapy (KNGF). Eur J Phys Rehabil Med. 2024;60:292-318](https://doi.org/10.23736/S1973-9087.24.08352-7). Access: `full_text`. Type: `guideline`.
- [Qaseem A, Wilt TJ, McLean RM, Forciea MA; Clinical Guidelines Committee of the American College of Physicians. Noninvasive Treatments for Acute, Subacute, and Chronic Low Back Pain: A Clinical Practice Guideline From the American College of Physicians. Ann Intern Med. 2017;166(7):514-530](https://doi.org/10.7326/M16-2367). Access: `abstract_only`. Type: `guideline`.

### Guideline summary (4)

- [American Family Physician. Practice guidelines: low back pain. Summary of the VA/DoD guideline. 2023](https://www.aafp.org/afp/2023/0400/practice-guidelines-low-back-pain). Access: `registry_or_summary`. Type: `guideline_summary`. No DOI or PMID recorded; links to the source page.
- [iatroX. Educational summary of the VA/DoD 2022 low back pain guideline, source check 20 August 2026. Secondary and not an official guideline](https://iatrox.com/guidelines/low-back-pain-and-sciatica). Access: `registry_or_summary`. Type: `guideline_summary`. No DOI or PMID recorded; links to the source page.
- [Macedo F, Annaswamy T, Coller R, Buelt A, et al. Diagnosis and Treatment of Low Back Pain: Synopsis of the 2021 US Department of Veterans Affairs and US Department of Defense Clinical Practice Guideline. Am J Phys Med Rehabil. 2024;103:350-355](https://doi.org/10.1097/PHM.0000000000002356). Access: `registry_or_summary`. Type: `guideline_summary`.
- [World Health Organization. WHO guideline for non-surgical management of chronic primary low back pain in adults in primary and community care settings: summary brochure. Geneva: WHO; 2023](https://pubmed.ncbi.nlm.nih.gov/38198579/). Access: `registry_or_summary`. Type: `guideline_summary`.

### Guideline comparison (3)

- [Corp N, et al. Evidence-based treatment recommendations for neck and low back pain across Europe: A systematic review of guidelines. Eur J Pain. 2021](https://doi.org/10.1002/ejp.1679). Access: `abstract_only`. Type: `guideline_comparison`.
- [Oliveira CB, Koes BW, Pinto RZ, et al. Towards global clinical practice guidelines for the management of non-specific low back pain in primary care: a review of current guideline recommendations and how they have changed over the last 30 years. Lancet Rheumatol. 2026;8(6):e470-e485](https://doi.org/10.1016/S2665-9913%2826%2900077-9). Access: `abstract_only`. Type: `guideline_comparison`.
- [Zhou T, et al. Recent clinical practice guidelines for the management of low back pain: a global comparison. BMC Musculoskelet Disord. 2024](https://doi.org/10.1186/s12891-024-07468-0). Access: `full_text`. Type: `guideline_comparison`.

### Consensus statement (1)

- [Gao B, Chai R, Wang G, et al; Chinese Society for the Study of the Lumbar Spine, Chinese Association of Spine. Multidisciplinary expert consensus on clinical diagnosis and therapies for low back pain. Bone Res. 2026;14(1):92](https://doi.org/10.1038/s41413-026-00572-y). Access: `full_text`. Type: `consensus_statement`.

### Consensus framework (1)

- [Finucane LM, Downie A, Mercer C, et al. International Framework for Red Flags for Potential Serious Spinal Pathologies. J Orthop Sports Phys Ther. 2020;50(7):350-372. Abstract only read](https://doi.org/10.2519/jospt.2020.9971). Access: `abstract_only`. Type: `consensus_framework`.

### Umbrella review (3)

- [Comachio J, Beckenkamp PR, Ho EK, et al. Benefits and harms of exercise therapy and physical activity for low back pain: an umbrella review. J Sport Health Sci. 2025;14:101038](https://doi.org/10.1016/j.jshs.2025.101038). Access: `full_text`. Type: `umbrella_review`.
- [Grooten WJA, et al. Summarizing the effects of different exercise types in chronic low back pain - a systematic review of systematic reviews. BMC Musculoskelet Disord. 2022;23(1):801](https://doi.org/10.1186/s12891-022-05722-x). Access: `abstract_only`. Type: `umbrella_review`.
- [Swain CTV, Pan F, Owen PJ, Schmidt H, Belavy DL. No consensus on causality of spine postures or physical exposure and low back pain: a systematic review of systematic reviews. J Biomech. 2020;102:109312](https://doi.org/10.1016/j.jbiomech.2019.08.006). Access: `abstract_only`. Type: `umbrella_review`.

### Network meta analysis (9)

- [Arora NK, Saueressig T, Schleimer T, et al. Optimal exercise dose in adults with chronic low back pain disorders: systematic review with dose-response network meta-analysis. Br J Sports Med. 2026; online ahead of print](https://doi.org/10.1136/bjsports-2026-111988). Access: `abstract_only`. Type: `network_meta_analysis`.
- [Belavy DL, Saueressig T, Arora NK, et al. Conservative treatments for chronic non-specific low back pain: time course network meta-analysis. BMJ Med. 2026;5(1):e001908](https://doi.org/10.1136/bmjmed-2025-001908). Access: `abstract_only`. Type: `network_meta_analysis`.
- [Fernandez-Rodriguez R, et al. Best Exercise Options for Reducing Pain and Disability in Adults With Chronic Low Back Pain: Pilates, Strength, Core-Based, and Mind-Body. A Network Meta-analysis. J Orthop Sports Phys Ther. 2022;52(8):505-521](https://doi.org/10.2519/jospt.2022.10671). Access: `abstract_only`. Type: `network_meta_analysis`.
- [Gianola S, et al. Effectiveness of treatments for acute and subacute mechanical non-specific low back pain: a systematic review with network meta-analysis. Br J Sports Med. 2022;56(1):41-50](https://doi.org/10.1136/bjsports-2020-103596). Access: `abstract_only`. Type: `network_meta_analysis`.
- [Hayden JA, et al. Some types of exercise are more effective than others in people with chronic low back pain: a network meta-analysis. J Physiother. 2021;67(4):252-262](https://doi.org/10.1016/j.jphys.2021.09.004). Access: `abstract_only`. Type: `network_meta_analysis`.
- [Li Y, et al. Exercise intervention for patients with chronic low back pain: a systematic review and network meta-analysis. Front Public Health. 2023;11:1155225](https://doi.org/10.3389/fpubh.2023.1155225). Access: `abstract_only`. Type: `network_meta_analysis`.
- [Liang Z, et al. The Best Exercise Modality and Dose for Reducing Pain in Adults With Low Back Pain: A Systematic Review With Model-Based Bayesian Network Meta-analysis. J Orthop Sports Phys Ther. 2024;54(5):315-327](https://doi.org/10.2519/jospt.2024.12153). Access: `abstract_only`. Type: `network_meta_analysis`.
- [Owen PJ, Miller CT, Mundell NL, et al. Which specific modes of exercise training are most effective for treating low back pain? Network meta-analysis. Br J Sports Med. 2020;54(21):1279-1287](https://doi.org/10.1136/bjsports-2019-100886). Access: `full_text`. Type: `network_meta_analysis`.
- [Wang L, Zhou C, Zhai C. Comparative effects of exercise modalities and dose parameters on chronic low back pain in adults: a systematic review and network meta-analysis. Front Physiol. 2026;17:1836061](https://doi.org/10.3389/fphys.2026.1836061). Access: `full_text`. Type: `network_meta_analysis`.

### Systematic review (30)

- [Almeida MO, et al. The McKenzie method for (sub)acute non-specific low back pain. Cochrane Database Syst Rev. 2023;4(4):CD009711](https://doi.org/10.1002/14651858.CD009711.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Ceballos-Laita L, et al. The effectiveness of hip interventions in patients with low-back pain: A systematic review and meta-analysis. Braz J Phys Ther. 2023;27(2):100502](https://doi.org/10.1016/j.bjpt.2023.100502). Access: `abstract_only`. Type: `systematic_review`.
- [Chou R, et al. Nonpharmacologic Therapies for Low Back Pain: A Systematic Review for an American College of Physicians Clinical Practice Guideline. Ann Intern Med. 2017;166(7):493-505](https://doi.org/10.7326/M16-2459). Access: `abstract_only`. Type: `systematic_review`.
- [Gou Y, et al. The effects of hamstring stretching exercises on pain intensity and function in low back pain patients: A systematic review with meta-analysis of randomized controlled trials. SAGE Open Med. 2024;12:20503121241252251](https://doi.org/10.1177/20503121241252251). Access: `full_text`. Type: `systematic_review`.
- [Hagen KB, et al. The Cochrane review of advice to stay active as a single treatment for low back pain and sciatica. Spine. 2002;27(16):1736-41](https://doi.org/10.1097/00007632-200208150-00010). Access: `abstract_only`. Type: `systematic_review`.
- [Hayden JA, et al. Exercise therapy for treatment of non-specific low back pain. Cochrane Database Syst Rev. 2005;2005(3):CD000335](https://doi.org/10.1002/14651858.CD000335.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Hayden JA, et al. Systematic review: strategies for using exercise therapy to improve outcomes in chronic low back pain. Ann Intern Med. 2005;142(9):776-85](https://doi.org/10.7326/0003-4819-142-9-200505030-00014). Access: `abstract_only`. Type: `systematic_review`.
- [IJzelenberg W, Oosterhuis T, Hayden JA, et al. Exercise therapy for treatment of acute non-specific low back pain. Cochrane Database Syst Rev. 2023;8(8):CD009365](https://doi.org/10.1002/14651858.CD009365.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [IJzelenberg W, Oosterhuis T, Hayden JA, et al. Exercise Therapy for Treatment of Acute Non-specific Low Back Pain: A Cochrane Systematic Review and Meta-analysis. Arch Phys Med Rehabil. 2024;105(8):1571-1584](https://doi.org/10.1016/j.apmr.2024.02.732). Access: `abstract_only`. Type: `systematic_review`.
- [Jahn A, et al. Occupational mechanical exposures as risk factor for chronic low-back pain: a systematic review and meta-analysis. Scand J Work Environ Health. 2023;49(7):453-465](https://doi.org/10.5271/sjweh.4114). Access: `abstract_only`. Type: `systematic_review`.
- [Karlsson M, Bergenheim A, Larsson MEH, et al. Effects of exercise therapy in patients with acute low back pain: a systematic review of systematic reviews. Syst Rev. 2020;9:182](https://doi.org/10.1186/s13643-020-01412-8). Access: `abstract_only`. Type: `systematic_review`.
- [Macedo LG, et al. Motor control exercise for acute non-specific low back pain. Cochrane Database Syst Rev. 2016;2(2):CD012085](https://doi.org/10.1002/14651858.CD012085). Access: `abstract_only`. Type: `systematic_review`.
- [Nolan D, O'Sullivan K, Newton C, Singh G, Smith BE. Are there differences in lifting technique between those with and without low back pain? A systematic review. Scand J Pain. 2020;20(2):215-227](https://doi.org/10.1515/sjpain-2019-0089). Access: `abstract_only`. Type: `systematic_review`.
- [Quentin C, et al. Effect of Home Exercise Training in Patients with Nonspecific Low-Back Pain: A Systematic Review and Meta-Analysis. Int J Environ Res Public Health. 2021;18(16):8430](https://doi.org/10.3390/ijerph18168430). Access: `abstract_only`. Type: `systematic_review`.
- [Ranzani M, Pozzi A, Fornasari D, Ristori D, Testa M. Is resistance training with external loads superior to unloaded exercise in the management of chronic low back pain? A systematic review and meta-analysis. Arch Physiother. 2025;15:297-313](https://doi.org/10.33393/aop.2025.3533). Access: `full_text`. Type: `systematic_review`.
- [Rodriguez-Dominguez AJ, et al. Does resistance training improve pain intensity, quality of life, and disability in people with chronic nonspecific low back pain? A systematic review and meta-analysis. Disabil Rehabil. 2026;48(6):1532-1545](https://doi.org/10.1080/09638288.2025.2566275). Access: `abstract_only`. Type: `systematic_review`.
- [Saraceni N, Kent P, Ng L, Campbell A, Straker L, O'Sullivan P. To Flex or Not to Flex? J Orthop Sports Phys Ther. 2020;50(3):121-130](https://doi.org/10.2519/jospt.2020.9218). Access: `abstract_only`. Type: `systematic_review`.
- [Saragiotto BT, Maher CG, Yamato TP, et al. Motor control exercise for chronic non-specific low-back pain. Cochrane Database Syst Rev. 2016;(1):CD012004](https://doi.org/10.1002/14651858.CD012004). Access: `abstract_only`. Type: `systematic_review`.
- [Searle A, et al. Exercise interventions for the treatment of chronic low back pain: a systematic review and meta-analysis of randomised controlled trials. Clin Rehabil. 2015;29(12):1155-67](https://doi.org/10.1177/0269215515570379). Access: `abstract_only`. Type: `systematic_review`.
- [Shiri R, Coggon D, Falah-Hassani K. Exercise for the Prevention of Low Back Pain: Systematic Review and Meta-Analysis of Controlled Trials. Am J Epidemiol. 2018;187(5):1093-1101](https://doi.org/10.1093/aje/kwx337). Access: `abstract_only`. Type: `systematic_review`.
- [Steffens D, et al. Prevention of Low Back Pain: A Systematic Review and Meta-analysis. JAMA Intern Med. 2016;176(2):199-208](https://doi.org/10.1001/jamainternmed.2015.7431). Access: `abstract_only`. Type: `systematic_review`.
- [Tan T, Tan X, Xie J, et al. Effects of exercise dose based on the ACSM recommendations on pain and disability in non-specific low back pain patients: a systematic review and meta-analysis of randomized controlled trials. Front Physiol. 2026;17:1725132](https://doi.org/10.3389/fphys.2026.1725132). Access: `full_text`. Type: `systematic_review`.
- [Tataryn N, et al. Posterior-Chain Resistance Training Compared to General Exercise and Walking Programmes for the Treatment of Chronic Low Back Pain in the General Population: A Systematic Review and Meta-Analysis. Sports Med Open. 2021;7(1):17](https://doi.org/10.1186/s40798-021-00306-w). Access: `full_text`. Type: `systematic_review`.
- [Trybulski R, Michal W, Malgorzata S, et al. Impact of isolated lumbar extension strength training on reducing nonspecific low back pain, disability, and improving function: a systematic review and meta-analysis. Sci Rep. 2025;15:6426](https://doi.org/10.1038/s41598-025-90699-5). Access: `full_text`. Type: `systematic_review`.
- [Vanti C, et al. The effectiveness of walking versus exercise on pain and function in chronic low back pain: a systematic review and meta-analysis of randomized trials. Disabil Rehabil. 2019;41(6):622-632](https://doi.org/10.1080/09638288.2017.1410730). Access: `abstract_only`. Type: `systematic_review`.
- [Verville L, Ogilvie R, Hincapie CA, et al. Systematic review to inform a World Health Organization (WHO) clinical practice guideline: benefits and harms of structured exercise programs for chronic primary low back pain in adults. J Occup Rehabil. 2023;33(4):636-650](https://doi.org/10.1007/s10926-023-10124-4). Access: `full_text`. Type: `systematic_review`.
- [Wang XQ, Wang YL, Witchalls J, et al. Physical therapy for acute and sub-acute low back pain: A systematic review and expert consensus. Clin Rehabil. 2024;38(6):715-731](https://doi.org/10.1177/02692155241229398). Access: `abstract_only`. Type: `systematic_review`.
- [Wieland LS, et al. Yoga for chronic non-specific low back pain. Cochrane Database Syst Rev. 2022;11(11):CD010671](https://doi.org/10.1002/14651858.CD010671.pub3). Access: `full_text`. Type: `systematic_review`.
- [Yamato TP, et al. Pilates for low back pain. Cochrane Database Syst Rev. 2015;2015(7):CD010265](https://doi.org/10.1002/14651858.CD010265.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Zhang SK, et al. Effects of exercise therapy on disability, mobility, and quality of life in the elderly with chronic low back pain: a systematic review and meta-analysis of randomized controlled trials. J Orthop Surg Res. 2023;18(1):513](https://doi.org/10.1186/s13018-023-03988-y). Access: `full_text`. Type: `systematic_review`.

### RCT (15)

- [Aasa B, Berglund L, Michaelson P, Aasa U. Individualized low-load motor control exercises and education versus a high-load lifting exercise and education to improve activity, pain intensity, and physical performance in patients with low back pain: a randomized controlled trial. J Orthop Sports Phys Ther. 2015;45(2):77-85](https://doi.org/10.2519/jospt.2015.5021). Access: `abstract_only`. Type: `rct`.
- [Asik HK, Sahbaz T. Preventing chronic low back pain: investigating the role of Pilates in subacute management, a randomized controlled trial. Ir J Med Sci. 2025;194(3):949-956](https://doi.org/10.1007/s11845-025-03939-y). Access: `full_text`. Type: `rct`.
- [Coelho ACS, Dourado JF, Lima POP. High-intensity and low-intensity Pilates have similar effects on pain and disability in people with chronic non-specific low back pain: a randomised trial. J Physiother. 2025;71(2):100-107](https://doi.org/10.1016/j.jphys.2025.03.002). Access: `abstract_only`. Type: `rct`.
- [Farragher JB, Pranata A, Williams GP, et al. Neuromuscular control and resistance training for people with chronic low back pain: a randomized controlled trial. J Orthop Sports Phys Ther. 2024;54(5):350-359](https://doi.org/10.2519/jospt.2024.12349). Access: `abstract_only`. Type: `rct`.
- [Halliday MH, Pappas E, Hancock MJ, et al. A randomized controlled trial comparing the McKenzie method to motor control exercises in people with chronic low back pain and a directional preference. J Orthop Sports Phys Ther. 2016;46(7):514-522](https://doi.org/10.2519/jospt.2016.6379). Access: `abstract_only`. Type: `rct`.
- [Kanli K, Hare Yigitoglu P, Ozgul A. Investigation of the effects of different treatment approaches on lumbar stabilizer muscles and diaphragm motility in individuals with chronic low back pain. Turk J Phys Med Rehabil. 2024;70(3):358-369](https://doi.org/10.5606/tftrd.2024.13257). Access: `full_text`. Type: `rct`.
- [Kell RT, Asmundson GJ. A comparison of two forms of periodized exercise rehabilitation programs in the management of chronic nonspecific low-back pain. J Strength Cond Res. 2009;23(2):513-523](https://doi.org/10.1519/JSC.0b013e3181918a6e). Access: `abstract_only`. Type: `rct`.
- [Kell RT, Risi AD, Barden JM. The response of persons with chronic nonspecific low back pain to three different volumes of periodized musculoskeletal rehabilitation. J Strength Cond Res. 2011;25(4):1052-1064](https://doi.org/10.1519/JSC.0b013e3181d09df7). Access: `abstract_only`. Type: `rct`.
- [Lunkes LC, Dias Neto MA, Barra LF, de Castro LR, de Sa Ferreira A, Meziat-Filho N. Keeping the abdomen relaxed versus contracted during Pilates improves disability slightly and may improve other outcomes in chronic non-specific low back pain: a randomised trial. J Physiother. 2026;72(2):116-121](https://doi.org/10.1016/j.jphys.2026.03.007). Access: `abstract_only`. Type: `rct`.
- [Michaelson P, Holmberg D, Aasa B, Aasa U. High load lifting exercise and low load motor control exercises as interventions for patients with mechanical low back pain: a randomized controlled trial with 24-month follow-up. J Rehabil Med. 2016;48(5):456-463](https://doi.org/10.2340/16501977-2091). Access: `full_text`. Type: `rct`.
- [Park JW, So WY, Oh JK, Lim DC. Effects of adding MedX lumbar extension or deadlift exercises to a core stabilization program on extensor strength, disability, and pain in men with chronic low back pain: a randomized pilot trial. Healthcare (Basel). 2026;14(16):2550](https://doi.org/10.3390/healthcare14162550). Access: `full_text`. Type: `rct`.
- [Pocovi NC, Lin CC, French SD, et al. Effectiveness and cost-effectiveness of an individualised, progressive walking and education intervention for the prevention of low back pain recurrence in Australia (WalkBack): a randomised controlled trial. Lancet. 2024;404(10448):134-144](https://doi.org/10.1016/S0140-6736%2824%2900755-4). Access: `abstract_only`. Type: `rct`.
- [Rodriguez-Lopez M, Losada-Baz L, Arias-Gomez FA, Cigarroa I, Marzetti E, Arroyo-Fernandez R, Bravo-Esteban E. Effects of a combined lumbar motor control and hip strengthening program on pain and function in chronic non-specific low back pain: a randomized clinical trial. Sports (Basel). 2026;14(10):456](https://doi.org/10.3390/sports14100456). Access: `full_text`. Type: `rct`.
- [Tomschi F, Meder K, Hilberg T. Effects of whole-body strength training on pain and strength in chronic low back pain. A randomized controlled trial. J Back Musculoskelet Rehabil. 2026;39(6):2109-2120](https://doi.org/10.1177/10538127261448967). Access: `full_text`. Type: `rct`.
- [Tottoli CR, Ben AJ, da Silva EN, Bosmans JE, van Tulder M, Carregaro RL. Effectiveness of Pilates compared with home-based exercises in individuals with chronic non-specific low back pain: randomised controlled trial. Clin Rehabil. 2024;38(11):1495-1505](https://doi.org/10.1177/02692155241277041). Access: `full_text`. Type: `rct`.

### Secondary analysis (2)

- [Berglund L, Aasa B, Hellqvist J, Michaelson P, Aasa U. Which patients with low back pain benefit from deadlift training? J Strength Cond Res. 2015;29(7):1803-1811](https://doi.org/10.1519/JSC.0000000000000837). Access: `abstract_only`. Type: `secondary_analysis`.
- [Berglund L, Aasa B, Michaelson P, Aasa U. Sagittal lumbopelvic alignment in patients with low back pain and the effects of a high-load lifting exercise and individualized low-load motor control exercises: a randomized controlled trial. Spine J. 2018;18(3):399-406](https://doi.org/10.1016/j.spinee.2017.07.178). Access: `abstract_only`. Type: `secondary_analysis`.

### Non randomised trial (2)

- [Jackson JK, Shepherd TR, Kell RT. The influence of periodized resistance training on recreationally active males with chronic nonspecific low back pain. J Strength Cond Res. 2011;25(1):242-251](https://doi.org/10.1519/JSC.0b013e3181b2c83d). Access: `abstract_only`. Type: `non_randomised_trial`.
- [Kshirsagar SS, Patil SC. Effectiveness of posterior chain resistance exercises and spinal stabilization exercises on pain, strength, and disability in runners with non-specific low back pain. Cureus. 2026;18(8):e114958](https://doi.org/10.7759/cureus.114958). Access: `full_text`. Type: `non_randomised_trial`.

### Cross sectional (2)

- [McGowen JM, Albin SR, Hoppes CW, Forsse JS, Abt J, Koppenhaver SL. Physically active adults with low back pain do not demonstrate altered deadlift mechanics: a novel application of myotonometry to estimate inter-muscular load sharing. Int J Sports Phys Ther. 2024;19(1):1462-1472](https://doi.org/10.26603/001c.90707). Access: `full_text`. Type: `cross_sectional`.
- [Stock MS, Bodden ME, Bloch JM, Starnes KL, Rodriguez G, Girts RM. Acute, non-specific low back pain does not impair isometric deadlift force or electromyographic excitation: a cross-sectional study. Sports (Basel). 2022;10(11):168](https://doi.org/10.3390/sports10110168). Access: `full_text`. Type: `cross_sectional`.

### Biomechanical study (3)

- [Armstrong DP, Davidson JB, Fischer SL. Determining whether biomechanical variables that describe common 'safe lifting' cues are associated with low back loads. J Electromyogr Kinesiol. 2024;75:102867](https://doi.org/10.1016/j.jelekin.2024.102867). Access: `abstract_only`. Type: `biomechanical_study`.
- [McGill SM, Marshall LW. Kettlebell swing, snatch, and bottoms-up carry: back and hip muscle activation, motion, and low back loads. J Strength Cond Res. 2012;26(1):16-27](https://doi.org/10.1519/JSC.0b013e31823a4063). Access: `abstract_only`. Type: `biomechanical_study`.
- [Shoji K, Nakayama K, Shiouchi M, Manabe Y. Load-dependent increase in lumbar kyphosis is associated with posterior pelvic tilt during deadlift. Front Sports Act Living. 2025;7:1682991](https://doi.org/10.3389/fspor.2025.1682991). Access: `full_text`. Type: `biomechanical_study`.

### Trial protocol (1)

- [Pocovi NC, et al. Effectiveness and cost-effectiveness of a progressive, individualised walking and education programme for prevention of low back pain recurrence in adults: study protocol for the WalkBack randomised controlled trial. BMJ Open. 2020](https://doi.org/10.1136/bmjopen-2020-037149). Access: `full_text`. Type: `trial_protocol`.

### Review protocol (1)

- [de Zoete A, et al. Aerobic exercise therapy for chronic low back pain. Cochrane Database Syst Rev. 2024;6(6):CD015503 (protocol)](https://doi.org/10.1002/14651858.CD015503). Access: `registry_or_summary`. Type: `review_protocol`.

### Meta epidemiological (1)

- [Innocenti T, et al. Bias in the measurement of the outcome is associated with effect sizes in randomized clinical trials on exercise therapy for chronic low back pain: a meta-epidemiological study. J Clin Epidemiol. 2023;162:145-155](https://doi.org/10.1016/j.jclinepi.2023.09.001). Access: `abstract_only`. Type: `meta_epidemiological`.

### Narrative review (2)

- [Nicol V, et al. Chronic Low Back Pain: A Narrative Review of Recent International Guidelines for Diagnosis and Conservative Treatment. J Clin Med. 2023](https://doi.org/10.3390/jcm12041685). Access: `full_text`. Type: `narrative_review`.
- [Rezaei A, Cheng CH, Nikkhoo M, Dashtdar B, Lu L, Kaufman K. From intradiscal pressure to multimodal estimation of lumbar spinal loads. Biomed J. 2026;49(5):101012](https://doi.org/10.1016/j.bj.2026.101012). Access: `full_text`. Type: `narrative_review`.

### Patient information (1)

- [Somerset NHS Foundation Trust. Cauda Equina Syndrome. Patient information page](https://www.somersetft.nhs.uk/trauma-and-orthopaedics/trauma-and-orthopaedics/spinal-surgery-service/cauda-equina-syndrome/). Access: `registry_or_summary`. Type: `patient_information`. No DOI or PMID recorded; links to the source page.

### Secondary summary (1)

- [POGO Physio. Red Flags for Spinal Pathology. Secondary summary attributing only part of its content, including fracture risk factors, to Finucane et al. 2020](https://www.pogophysio.com.au/?p=8582). Access: `registry_or_summary`. Type: `secondary_summary`. No DOI or PMID recorded; links to the source page.

### Editorial notice (1)

- [Expression of concern for Owen et al., Br J Sports Med 2020. PubMed record states it was rescinded in an editorial dated 27 July 2020](https://doi.org/10.1136/bjsports-2019-100886eoc1). Access: `abstract_only`. Type: `editorial_notice`.

### News report (1)

- [Managed Healthcare Executive. New acute low back pain guideline steers clinicians away from routine imaging and opioids. 8 October 2026. Trade news report on Skelly 2026](https://www.managedhealthcareexecutive.com/view/new-acute-low-back-pain-guideline-steers-clinicians-away-from-routine-imaging-and-opioids). Access: `registry_or_summary`. Type: `news_report`. No DOI or PMID recorded; links to the source page.

## Rotator cuff-related shoulder pain

Condition file: [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md)

### Guideline (10)

- [American Academy of Orthopaedic Surgeons. Management of Rotator Cuff Injuries Evidence-Based Clinical Practice Guideline. Published 08/18/2025. Aaos.org/rccpg2025](https://www.aaos.org/globalassets/quality-and-practice-resources/rotator-cuff/rotator-cuff-2025/rotator-cuff-cpg.pdf). Access: `full_text`. Type: `guideline`. No DOI or PMID recorded; links to the source page.
- [Desmeules F, Roy JS, Lafrance S, Charron M, Dube MO, Dupuis F, Beneciuk JM, Grimes J, Kim HM, Lamontagne M, McCreesh K, Shanley E, Vukobrat T, Michener LA. Rotator Cuff Tendinopathy Diagnosis, Nonsurgical Medical Care, and Rehabilitation: A Clinical Practice Guideline. J Orthop Sports Phys Ther. 2025;55(4):235-274](https://doi.org/10.2519/jospt.2025.13182). Access: `full_text`. Type: `guideline`.
- [Diercks R, Bron C, Dorrestijn O, et al. Guideline for diagnosis and treatment of subacromial pain syndrome: a multidisciplinary review by the Dutch Orthopaedic Association. Acta Orthop. 2014;85:314-322](https://doi.org/10.3109/17453674.2014.920991). Access: `full_text`. Type: `guideline`.
- [Dyer JO, Doiron-Cadrin P, Lafrance S, et al. Diagnosing, Managing, and Supporting Return to Work of Adults With Rotator Cuff Disorders: Clinical Practice Guideline Methods. J Orthop Sports Phys Ther. 2022;52(10):665-674](https://doi.org/10.2519/jospt.2022.11307). Access: `abstract_only`. Type: `guideline`.
- [Lafrance S, Charron M, Roy JS, et al. Diagnosing, Managing, and Supporting Return to Work of Adults With Rotator Cuff Disorders: A Clinical Practice Guideline. J Orthop Sports Phys Ther. 2022;52(10):647-664](https://doi.org/10.2519/jospt.2022.11306). Access: `abstract_only`. Type: `guideline`.
- [Lambers Heerspink FO, Veen EJD, Dorrestijn O, et al. Update of guideline for diagnosis and treatment of subacromial pain syndrome, Part 1: preventive measures, diagnostics, non-surgical treatment. Acta Orthop. 2026;97:91-98](https://doi.org/10.2340/17453674.2026.45365). Access: `full_text`. Type: `guideline`.
- [Lambers Heerspink FO, Veen EJD, Dorrestijn O, et al. Update of guideline for diagnosis and treatment of subacromial pain syndrome, Part 2: operative considerations. Acta Orthop. 2026;97:99-104](https://doi.org/10.2340/17453674.2026.45410). Access: `full_text`. Type: `guideline`.
- [Pandey RA, Singh HP; BESS Subacromial Pain Working Group. BESS Patient Care Pathway: Subacromial Pain. Shoulder & Elbow. 2025;17(6):713-724](https://doi.org/10.1177/17585732251374282). Access: `full_text`. Type: `guideline`.
- [Rees JL, Kulkarni R, Rangan A, et al. Shoulder Pain Diagnosis, Treatment and Referral Guidelines for Primary, Community and Intermediate Care. Shoulder Elbow. 2021;13(1):5-11](https://doi.org/10.1177/1758573220984471). Access: `full_text`. Type: `guideline`.
- [Vandvik PO, Lahdeoja T, Ardern C, et al. Subacromial decompression surgery for adults with shoulder pain: a clinical practice guideline. BMJ. 2019;364:l294](https://doi.org/10.1136/bmj.l294). Access: `abstract_only`. Type: `guideline`.

### Consensus statement (3)

- [Eubank BH, Mohtadi NG, Lafave MR, Wiley JP, Bois AJ, Boorman RS, Sheps DM. Using the modified Delphi method to establish clinical consensus for the diagnosis and treatment of patients with rotator cuff pathology. BMC Med Res Methodol. 2016;16:56](https://doi.org/10.1186/s12874-016-0165-8). Access: `full_text`. Type: `consensus_statement`.
- [Hurley ET, Droz LG, Lorentz SG, et al.; Rotator Cuff Tear International Consensus Group. Rotator Cuff Tears Part I: Diagnosis, Nonoperative Management, and Repair of Posterosuperior Tears: An International Expert Consensus Statement. Arthroscopy. 2026 (online ahead of print)](https://doi.org/10.1002/arj.70365). Access: `abstract_only`. Type: `consensus_statement`.
- [Hurley ET, Glover MA, Lorentz SG, et al.; Rotator Cuff Tear International Consensus Group. Rotator Cuff Consensus Part III: Reverse Total Shoulder Arthroplasty, Revisions, Rehabilitation, and Follow-Up: An International Consensus Expert Statement. Arthroscopy. 2026 (online ahead of print)](https://doi.org/10.1002/arj.70367). Access: `abstract_only`. Type: `consensus_statement`.

### Systematic review (27)

- [Aasa U, Svartholm I, Andersson F, Berglund L. Injuries among weightlifters and powerlifters: a systematic review. Br J Sports Med. 2017;51(4):211-219](https://doi.org/10.1136/bjsports-2016-096037). Access: `abstract_only`. Type: `systematic_review`.
- [Cooper K, Alexander L, Brandie D, et al. Exercise therapy for tendinopathy: a mixed-methods evidence synthesis exploring feasibility, acceptability and effectiveness. Health Technol Assess. 2023;27(24):1-389](https://doi.org/10.3310/TFWS2748). Access: `abstract_only`. Type: `systematic_review`. Also cited by: [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md).
- [Diener GL, Spahn G, Hofmann GO. Occupational Stress as a Possible Risk Factor for Rotator Cuff Damage - Systematic Review and Meta-analysis. Z Orthop Unfall. 2024;162(3):289-295](https://doi.org/10.1055/a-1957-6140). Access: `abstract_only`. Type: `systematic_review`.
- [Guo R, Luo Y, Xu Y, Lan K, Zhao Y. Efficacy of modified posterior shoulder stretching exercises on shoulder function in subacromial impingement syndrome: A comprehensive meta-analysis. Medicine (Baltimore). 2025;104(2):e41117](https://doi.org/10.1097/MD.0000000000041117). Access: `abstract_only`. Type: `systematic_review`.
- [Hernandez-Sanchez S, Segura-Heras JV, Andreu-Conde P, et al. Effect of home exercise programs in patients with non-tears rotator cuff-related shoulder pain: a systematic review and meta-analysis. Eur J Phys Rehabil Med. 2025;61(4):655-668](https://doi.org/10.23736/S1973-9087.25.08607-1). Access: `abstract_only`. Type: `systematic_review`.
- [Karjalainen TV, Jain NB, Heikkinen J, et al. Surgery for rotator cuff tears. Cochrane Database Syst Rev. 2019;12(12):CD013502](https://doi.org/10.1002/14651858.CD013502). Access: `abstract_only`. Type: `systematic_review`.
- [Lafrance S, Charron M, Dube MO, et al. The Efficacy of Exercise Therapy for Rotator Cuff-Related Shoulder Pain According to the FITT Principle: A Systematic Review With Meta-analyses. J Orthop Sports Phys Ther. 2024;54(8):499-512. Abstract only](https://doi.org/10.2519/jospt.2024.12453). Access: `abstract_only`. Type: `systematic_review`.
- [Larsson R, Bernhardsson S, Nordeman L. Effects of eccentric exercise in patients with subacromial impingement syndrome: a systematic review and meta-analysis. BMC Musculoskelet Disord. 2019;20(1):446](https://doi.org/10.1186/s12891-019-2796-5). Access: `abstract_only`. Type: `systematic_review`.
- [Leong HT, Fu SC, He X, Oh JH, Yamamoto N, Hang S. Risk factors for rotator cuff tendinopathy: A systematic review and meta-analysis. J Rehabil Med. 2019;51(9):627-637](https://doi.org/10.2340/16501977-2598). Access: `abstract_only`. Type: `systematic_review`.
- [Liu J, Tang D, Hu R, et al. Effectiveness of exercise therapy versus passive conservative treatments for rotator cuff-related shoulder pain: a systematic review and meta-analysis of randomized controlled trials. Musculoskelet Sci Pract. 2026;83:103556](https://doi.org/10.1016/j.msksp.2026.103556). Access: `abstract_only`. Type: `systematic_review`.
- [Malliaras P, Johnston R, Street G, et al. The Efficacy of Higher Versus Lower Dose Exercise in Rotator Cuff Tendinopathy: A Systematic Review of Randomized Controlled Trials. Arch Phys Med Rehabil. 2020;101(10):1822-1834](https://doi.org/10.1016/j.apmr.2020.06.013). Access: `abstract_only`. Type: `systematic_review`.
- [Naunton J, Street G, Littlewood C, Haines T, Malliaras P. Effectiveness of progressive and resisted and non-progressive or non-resisted exercise in rotator cuff related shoulder pain: a systematic review and meta-analysis of randomized controlled trials. Clin Rehabil. 2020;34(9):1198-1216. Abstract only](https://doi.org/10.1177/0269215520934147). Access: `abstract_only`. Type: `systematic_review`.
- [Ortega-Castillo M, Cuesta-Vargas A, Luque-Teba A, Trinidad-Fernandez M. The role of progressive, therapeutic exercise in the management of upper limb tendinopathies: A systematic review and meta-analysis. Musculoskelet Sci Pract. 2022;62:102645](https://doi.org/10.1016/j.msksp.2022.102645). Access: `abstract_only`. Type: `systematic_review`. Also cited by: [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md).
- [Page MJ, Green S, McBain B, et al. Manual therapy and exercise for rotator cuff disease. Cochrane Database Syst Rev. 2016;(6):CD012224](https://doi.org/10.1002/14651858.CD012224). Access: `full_text`. Type: `systematic_review`.
- [Park SW, Chen YT, Thompson L, et al. No relationship between the acromiohumeral distance and pain in adults with subacromial pain syndrome: a systematic review and meta-analysis. Sci Rep. 2020;10(1):20611](https://doi.org/10.1038/s41598-020-76704-z). Access: `abstract_only`. Type: `systematic_review`.
- [Pavlova AV, Shim JSC, Moss R, et al. Effect of resistance exercise dose components for tendinopathy management: a systematic review with meta-analysis. Br J Sports Med. 2023;57(20):1327-1334](https://doi.org/10.1136/bjsports-2022-105754). Access: `full_text`. Type: `systematic_review`.
- [Pieters L, Lewis J, Kuppens K, et al. An Update of Systematic Reviews Examining the Effectiveness of Conservative Physical Therapy Interventions for Subacromial Shoulder Pain. J Orthop Sports Phys Ther. 2020;50(3):131-141](https://doi.org/10.2519/jospt.2020.8498). Access: `abstract_only`. Type: `systematic_review`.
- [Silveira A, Lima C, Beaupre L, Chepeha J, Jones A. Shoulder specific exercise therapy is effective in reducing chronic shoulder pain: A network meta-analysis. PLoS One. 2024;19(4):e0294014](https://doi.org/10.1371/journal.pone.0294014). Access: `abstract_only`. Type: `systematic_review`.
- [Smith BE, Hendrick P, Smith TO, et al. Should exercises be painful in the management of chronic musculoskeletal pain? A systematic review and meta-analysis. Br J Sports Med. 2017;51(23):1679-1687](https://doi.org/10.1136/bjsports-2016-097383). Access: `abstract_only`. Type: `systematic_review`.
- [Steuri R, Sattelmayer M, Elsig S, et al. Effectiveness of conservative interventions including exercise, manual therapy and medical management in adults with shoulder impingement: a systematic review and meta-analysis of RCTs. Br J Sports Med. 2017;51(18):1340-1347](https://doi.org/10.1136/bjsports-2016-096515). Access: `abstract_only`. Type: `systematic_review`.
- [Tena-Portilla A, Martin-San Agustin R, Gacto-Sanchez M, Escriche-Escuder A. Effectiveness of exercise in rotator cuff-related shoulder pain: a systematic review with meta-analysis. Physiother Theory Pract. 2026](https://doi.org/10.1080/09593985.2026.2717392). Access: `abstract_only`. Type: `systematic_review`.
- [Tooth C, Gofflot A, Schwartz C, et al. Risk Factors of Overuse Shoulder Injuries in Overhead Athletes: A Systematic Review. Sports Health. 2020;12(5):478-487](https://doi.org/10.1177/1941738120931764). Access: `abstract_only`. Type: `systematic_review`.
- [Tran I, Gibbs MT, Yu N, Powell JK, Smith BE, Jones MD. Effectiveness of Painful Versus Nonpainful Exercise on Pain Intensity, Disability, and Other Patient-Reported Outcomes in Adults With Chronic Musculoskeletal Pain: An Updated Systematic Review With Meta-Analysis. J Orthop Sports Phys Ther. 2025;55(8):1-11](https://doi.org/10.2519/jospt.2025.13253). Access: `abstract_only`. Type: `systematic_review`.
- [Wu D, Wen Z, Ke H, et al. Specific modes of exercise to improve rotator cuff-related shoulder pain: systematic review and meta-analysis. Front Bioeng Biotechnol. 2025;13:1560597](https://doi.org/10.3389/fbioe.2025.1560597). Access: `full_text`. Type: `systematic_review`.
- [Zhang B, Raguzzi IA, Dupuis F, et al. Addressing Shoulder Weakness in Individuals With Rotator Cuff-Related Shoulder Pain: A Systematic Review With Meta-analysis. J Orthop Sports Phys Ther. 2026;56(2):67-84](https://doi.org/10.2519/jospt.2025.13445). Access: `abstract_only`. Type: `systematic_review`.
- [Zhang W, Du M, Xia L, Hao F, Tian M. Effects of seven types of exercise in the treatment of rotator cuff-related shoulder pain (RCRSP): a systematic review and Bayesian network meta-analysis. J Orthop Surg Res. 2025;20:1075](https://doi.org/10.1186/s13018-025-06514-4). Access: `full_text`. Type: `systematic_review`.
- [Zhong Z, Zang W, Tang Z, Pan Q, Yang Z, Chen B. Effect of scapular stabilization exercises on subacromial pain (impingement) syndrome: a systematic review and meta-analysis of randomized controlled trials. Front Neurol. 2024;15:1357763](https://doi.org/10.3389/fneur.2024.1357763). Access: `abstract_only`. Type: `systematic_review`.

### Scoping review (2)

- [Daher M, Jabre S, Casey JC, Fares MY, Boufadel P, Lopez R, et al. Shouldering the load: A scoping review of incidence, types, and risk factors of shoulder injuries in weight-lifting athletes. Shoulder Elbow. 2025;17:254-263](https://doi.org/10.1177/17585732241258743). Access: `abstract_only`. Type: `scoping_review`.
- [Ullern KR, Richardsen M, Weerasekara I, Bogen BE. Painful considerations in exercise-management for rotator cuff related shoulder pain: a scoping review on pain-related prescription parameters. BMC Musculoskelet Disord. 2025;26:180](https://doi.org/10.1186/s12891-025-08411-7). Access: `abstract_only`. Type: `scoping_review`.

### RCT (18)

- [Axman S, Svensson RB, Cullum C, Magnusson SP, Kjaer BH. Adherence to Exercise Intervention Including Pain Threshold, Exercise and Physiotherapy Sessions for Patients With Chronic Rotator Cuff Tendinopathy. Arch Phys Med Rehabil. 2026 (online ahead of print)](https://doi.org/10.1016/j.apmr.2026.07.009). Access: `abstract_only`. Type: `rct`.
- [Cavaggion C, Luque-Suarez A, Voogt L, Juul-Kristensen B, Wollants G, Beke L, et al. Exercise into Pain in Chronic Rotator Cuff-Related Shoulder Pain: A Randomized Controlled Trial with 6-Month Follow-Up. Open Access J Sports Med. 2024;15:181-196](https://doi.org/10.2147/OAJSM.S483272). Access: `full_text`. Type: `rct`.
- [Cavaggion C, Navarro-Ledesma S, Juul-Kristensen B, Luque-Suarez A, Voogt L, et al. The effect of painful exercise on ultrasonographic outcomes in rotator cuff-related shoulder pain: Secondary analysis of a randomized controlled trial. J Back Musculoskelet Rehabil. 2025;38:1379-1388](https://doi.org/10.1177/10538127251334430). Access: `abstract_only`. Type: `rct`.
- [Clausen MB, Holmich P, Rathleff M, Bandholm T, Christensen KB, Zebis MK, et al. Effectiveness of Adding a Large Dose of Shoulder Strengthening to Current Nonoperative Care for Subacromial Impingement. Am J Sports Med. 2021;49:3040-3049](https://doi.org/10.1177/03635465211016008). Access: `full_text`. Type: `rct`.
- [Dejaco B, Habets B, van Loon C, van Grinsven S, van Cingel R. Eccentric versus conventional exercise therapy in patients with rotator cuff tendinopathy. Knee Surg Sports Traumatol Arthrosc. 2017;25:2051-2059](https://doi.org/10.1007/s00167-016-4223-x). Access: `abstract_only`. Type: `rct`.
- [Dube MO, Desmeules F, Lewis JS, Roy JS. Does the addition of motor control or strengthening exercises to education result in better outcomes for rotator cuff-related shoulder pain? Br J Sports Med. 2023;57:457-463](https://doi.org/10.1136/bjsports-2021-105027). Access: `abstract_only`. Type: `rct`.
- [Gutierrez-Espinoza H, Araya-Quintanilla F, Zavala-Gonzalez J, Valenzuela-Fuenzalida, et al. Supervised versus home exercise program in subacromial impingement syndrome: a randomized controlled trial with 1-year of follow-up. Ann Phys Rehabil Med. 2026;69:102106](https://doi.org/10.1016/j.rehab.2026.102106). Access: `abstract_only`. Type: `rct`.
- [Heron SR, Woby SR, Thompson DP. Comparison of three types of exercise in the treatment of rotator cuff tendinopathy/shoulder impingement syndrome. Physiotherapy. 2017;103:167-173](https://doi.org/10.1016/j.physio.2016.09.001). Access: `abstract_only`. Type: `rct`.
- [Holmgren T, Bjornsson Hallgren H, Oberg B, Adolfsson L, Johansson K. Effect of specific exercise strategy on need for surgery in patients with subacromial impingement syndrome: randomised controlled study. BMJ. 2012;344:e787, with its appendix (details of specific exercise programme)](https://doi.org/10.1136/bmj.e787). Access: `full_text`. Type: `rct`.
- [Hopewell S, Keene DJ, Marian IR, et al. Progressive exercise compared with best practice advice, with or without corticosteroid injection, for the treatment of patients with rotator cuff disorders (GRASP): a multicentre, pragmatic, 2 x 2 factorial, randomised controlled trial. Lancet. 2021;398(10298):416-428](https://doi.org/10.1016/s0140-6736%2821%2900846-1). Access: `full_text`. Type: `rct`.
- [Ingwersen KG, et al. Three Months of Progressive High-Load Versus Traditional Low-Load Strength Training Among Patients With Rotator Cuff Tendinopathy. Orthop J Sports Med. 2017;5:2325967117723292](https://doi.org/10.1177/2325967117723292). Access: `full_text`. Type: `rct`.
- [Kara D, Ozcakar L, Demirci S, Huri G, Duzgun I. Blood Flow Restriction Training in Patients With Rotator Cuff Tendinopathy. Clin J Sport Med. 2024;34:10-16](https://doi.org/10.1097/JSM.0000000000001191). Access: `abstract_only`. Type: `rct`.
- [Karabay D, Yesilyaprak SS, Erduran M, Ozcan C. Effects of Eccentric Versus Concentric Strengthening in Patients With Subacromial Pain Syndrome. Sports Health. 2025;17:322-331](https://doi.org/10.1177/19417381241236817). Access: `abstract_only`. Type: `rct`.
- [Liaghat B, Juul-Kristensen B, Christensen FH, Nissen SE, Skou ST, Sogaard K, et al. Pain trajectories and exercise-induced pain during 16 weeks of high-load or low-load shoulder exercise in patients with hypermobile shoulders. Scand J Pain. 2025;25](https://doi.org/10.1515/sjpain-2024-0072). Access: `abstract_only`. Type: `rct`.
- [Littlewood C, Bateman M, Brown K, Bury J, Mawson S, May S, Walters SJ. A self-managed single exercise programme versus usual physiotherapy treatment for rotator cuff tendinopathy (SELF). Clin Rehabil. 2016;30:686-696](https://doi.org/10.1177/0269215515593784). Access: `abstract_only`. Type: `rct`.
- [Naunton J, Kidgell D, Bennell K, Haines T, Malliaras P. The efficacy of high load-volume exercise versus low load-volume exercise for rotator cuff tendinopathy: pilot and feasibility trial. Musculoskelet Sci Pract. 2025;75:103218](https://doi.org/10.1016/j.msksp.2024.103218). Access: `abstract_only`. Type: `rct`.
- [Petersson AH, Bjornsson Hallgren HC, Adolfsson LE, Holmgren TM. No need for subacromial decompression in responders to specific exercise treatment: a 10-year follow-up of a randomized controlled trial. J Shoulder Elbow Surg. 2025;34:e477-e487](https://doi.org/10.1016/j.jse.2024.10.027). Access: `abstract_only`. Type: `rct`.
- [Taghizadeh Delkhoush C, Arzani P, Paknazar F, Pakdaman M, Safavi Farokhi Z. Optimizing Shoulder Exercise Selection: Arm Rotation Versus Abduction Exercises for Increasing Acromiohumeral Distance in Secondary Subacromial Impingement. J Sport Rehabil. 2026](https://doi.org/10.1123/jsr.2025-0060). Access: `abstract_only`. Type: `rct`.

### Observational (6)

- [Cavaggion C, Juul-Kristensen B, Luque-Suarez A, Voogt L, Wollants G, et al. Exercise into pain in chronic rotator cuff related shoulder pain: a prospective single-group feasibility study. BMJ Open. 2023;13:e070698](https://doi.org/10.1136/bmjopen-2022-070698). Access: `abstract_only`. Type: `observational`.
- [Chepeha J, Silveira A, Sheps D, Luciak-Corea C, Styles-Tripp F, Beaupre L. A standardized criteria-based progressive shoulder exercise program is effective in managing rotator cuff-related shoulder pain. PLoS One. 2025;20:e0328728](https://doi.org/10.1371/journal.pone.0328728). Access: `full_text`. Type: `observational`.
- [Kolber MJ, Cheatham SW, Salamh PA, Hanney WJ. Characteristics of shoulder impingement in the recreational weight-training population. J Strength Cond Res. 2014;28:1081-1089](https://doi.org/10.1519/JSC.0000000000000250). Access: `abstract_only`. Type: `observational`.
- [Kolber MJ, Hanney WJ, Cheatham SW, Salamh PA, Masaracchio M, Liu X. Shoulder Joint and Muscle Characteristics Among Weight-Training Participants With and Without Impingement Syndrome. J Strength Cond Res. 2017;31:1024-1032](https://doi.org/10.1519/JSC.0000000000001554). Access: `abstract_only`. Type: `observational`.
- [Montoya-Minano JJ, Garcia-de-Pereda-Notario CM, Palomeque-Del-Cerro L, et al. Ultrasound-Based Assessment of Shoulder Soft Tissue Alterations in Young Adults Performing Upper Limb Weight Training. J Funct Morphol Kinesiol. 2026;11:23](https://doi.org/10.3390/jfmk11010023). Access: `abstract_only`. Type: `observational`.
- [Pirruccio K, Kelly JD. Weightlifting Shoulder Injuries Presenting to U.S. Emergency Departments: 2000-2030. Int J Sports Med. 2019;40:528-534](https://doi.org/10.1055/a-0927-6818). Access: `abstract_only`. Type: `observational`.

### Biomechanical (10)

- [Coratella G, Tornatore G, Longo S, Esposito F, Ce E. Front vs Back and Barbell vs Machine Overhead Press: An Electromyographic Analysis. Front Physiol. 2022;13:825880](https://doi.org/10.3389/fphys.2022.825880). Access: `full_text`. Type: `biomechanical`.
- [Eraslan L, Cools A, Yar O, Akkaya S, Duzgun I. Acromiohumeral distance quantification during a variety of shoulder external and internal rotational exercises in recreationally overhead athletes. Res Sports Med. 2023;31:818-830](https://doi.org/10.1080/15438627.2022.2052068). Access: `abstract_only`. Type: `biomechanical`.
- [Gundersen AH, Krosshaug T, Mausehund L, van den Tillaar R, Larsen S. The impact of grip width on kinetics and kinematics in the shoulder press among resistance-trained men. Sports Biomech. 2026;25:841-854](https://doi.org/10.1080/14763141.2025.2590028). Access: `abstract_only`. Type: `biomechanical`.
- [Motlagh JG, Lipps DB. Impact of muscular fatigue and sex on supraspinatus occupation ratio during bench press in competitive powerlifters. J Sports Sci. 2026;44:1453-1463](https://doi.org/10.1080/02640414.2026.2639894). Access: `abstract_only`. Type: `biomechanical`.
- [Noteboom L, Belli I, Hoozemans MJM, Seth A, Veeger HEJ, Van Der Helm FCT. Effects of bench press technique variations on musculoskeletal shoulder loads and potential injury risk. Front Physiol. 2024;15:1393235](https://doi.org/10.3389/fphys.2024.1393235). Access: `full_text`. Type: `biomechanical`.
- [Padovan R, Toninelli N, Longo S, Tornatore G, Esposito F, Ce E, Coratella G. High-Density Surface Electromyography Excitation in Front vs. Back Overhead Press Prime Movers. J Hum Kinet. 2026;103:81-96](https://doi.org/10.5114/jhk/205466). Access: `abstract_only`. Type: `biomechanical`.
- [Robert-Lachaine X, Allard P, Gobout V, Begon M. Shoulder Coordination During Full-Can and Empty-Can Rehabilitation Exercises. J Athl Train. 2015;50:1117-1125](https://doi.org/10.4085/1062-6050-50.9.06). Access: `abstract_only`. Type: `biomechanical`.
- [Schutz P, Zimmer P, Zeidler F, Pluss M, Oberhofer K, List R, Lorenzetti SR. Chest Exercises: Movement and Loading of Shoulder, Elbow and Wrist Joints. Sports (Basel). 2022;10:19](https://doi.org/10.3390/sports10020019). Access: `abstract_only`. Type: `biomechanical`.
- [Thigpen CA, Padua DA, Morgan N, Kreps C, Karas SG. Scapular kinematics during supraspinatus rehabilitation exercise: a comparison of full-can versus empty-can techniques. Am J Sports Med. 2006;34:644-652](https://doi.org/10.1177/0363546505281797). Access: `abstract_only`. Type: `biomechanical`.
- [Timmons MK, Ericksen JJ, Yesilyaprak SS, Michener LA. Empty can exercise provokes more pain and has undesirable biomechanics compared with the full can exercise. J Shoulder Elbow Surg. 2016;25:548-556](https://doi.org/10.1016/j.jse.2015.08.046). Access: `abstract_only`. Type: `biomechanical`.

### RCT protocol (3)

- [Hopewell S, Keene DJ, Maia Schlussel M, et al. Clinical and cost-effectiveness of progressive exercise compared with best practice advice, with or without corticosteroid injection, for the treatment of rotator cuff disorders: protocol for a 2x2 factorial randomised controlled trial (the GRASP trial). BMJ Open. 2017;7(7):e018004](https://doi.org/10.1136/bmjopen-2017-018004). Access: `full_text`. Type: `rct_protocol`.
- [Ingwersen KG, et al. Progressive high-load strength training compared with general low-load exercises in patients with rotator cuff tendinopathy: study protocol for a randomised controlled trial. Trials. 2015;16:27](https://doi.org/10.1186/s13063-014-0544-6). Access: `full_text`. Type: `rct_protocol`.
- [Kjaer BH, Cools AM, Johannsen FE, Trostrup J, Bieler T, Siersma V, Magnusson PS. To allow or avoid pain during shoulder rehabilitation exercises for patients with chronic rotator cuff tendinopathy: protocol (PASE trial). Trials. 2024;25:135](https://doi.org/10.1186/s13063-024-07973-6). Access: `abstract_only`. Type: `rct_protocol`.

### Narrative review (4)

- [Bonilla DA, Cardozo LA, Velez-Gutierrez JM, Arevalo-Rodriguez A, Vargas-Molina S, et al. Exercise Selection and Common Injuries in Fitness Centers: A Systematic Integrative Review and Practical Recommendations. Int J Environ Res Public Health. 2022;19:12710](https://doi.org/10.3390/ijerph191912710). Access: `full_text`. Type: `narrative_review`.
- [Kolber MJ, Beekhuizen KS, Cheng MS, Hellman MA. Shoulder injuries attributed to resistance training: a brief review. J Strength Cond Res. 2010;24:1696-1704](https://doi.org/10.1519/JSC.0b013e3181dc4330). Access: `abstract_only`. Type: `narrative_review`.
- [Lewis J. Rotator cuff related shoulder pain: Assessment, management and uncertainties. Man Ther. 2016;23:57-68. Accepted manuscript](https://doi.org/10.1016/j.math.2016.03.009). Access: `full_text`. Type: `narrative_review`.
- [Motlagh JG, Lipps DB. The Contribution of Muscular Fatigue and Shoulder Biomechanics to Shoulder Injury Incidence During the Bench Press Exercise: A Narrative Review. J Strength Cond Res. 2024;38:2147-2163](https://doi.org/10.1519/JSC.0000000000004973). Access: `abstract_only`. Type: `narrative_review`.

## Lateral elbow tendinopathy

Condition file: [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md)

### Clinical practice guideline (4)

- [Amako M, Arai T, Iba K, Ikeda M, Ikegami H, Imada H, et al. Japanese Orthopaedic Association (JOA) clinical practice guidelines on the management of lateral epicondylitis of the humerus - Secondary publication. J Orthop Sci. 2022;27(3):514-532](https://doi.org/10.1016/j.jos.2021.09.003). Access: `abstract_only`. Type: `clinical_practice_guideline`.
- [Hegmann KT, Hoffman HE, Belcourt RM, Byrne K, Glass L, Melhorn JM, et al. ACOEM practice guidelines: elbow disorders. J Occup Environ Med. 2013;55(11):1365-74](https://doi.org/10.1097/JOM.0b013e3182a0d7ec). Access: `abstract_only`. Type: `clinical_practice_guideline`.
- [Lucado AM, Day JM, Vincent JI, MacDermid JC, Fedorczyk J, Grewal R, Martin RL. Lateral Elbow Pain and Muscle Function Impairments. J Orthop Sports Phys Ther. 2022;52(12):CPG1-CPG111](https://doi.org/10.2519/jospt.2022.0302). Access: `full_text`. Type: `clinical_practice_guideline`.
- [Singh HP, Watts AC. BESS patient care pathway: Tennis elbow. Shoulder Elbow. 2023;15(4):348-359](https://doi.org/10.1177/17585732231170793). Access: `full_text`. Type: `clinical_practice_guideline`.

### Guideline summary (1)

- [Leschinger T, Tischer T, Doepfer AK, Glanzmann M, Hackl M, Lehmann L, et al. Epicondylopathia humeri radialis. Z Orthop Unfall. 2022;160(3):329-340](https://doi.org/10.1055/a-1340-0931). Access: `abstract_only`. Type: `guideline_summary`.

### Local clinical guidance (1)

- [NHS Highland TAM. Musculoskeletal pathways guidelines, elbow pathway red flags. Right Decision Service. Accessed 2026-11-01](https://www.rightdecisions.scot.nhs.uk/tam-treatments-and-medicines-nhs-highland/adult-therapeutic-guidelines/orthopaedics/musculoskeletal-pathways-guidelines/). Access: `full_text`. Type: `local_clinical_guidance`. No DOI or PMID recorded; links to the source page.

### Position statement (1)

- [Lapner P, Alfonso A, Herbert-Davies J, Pollock JW, Marsh J, King G, et al. Position statement: nonoperative management of lateral epicondylitis in adults. Can J Surg. 2022;65(5):E625-E629](https://doi.org/10.1503/cjs.019221). Access: `full_text`. Type: `position_statement`.

### Consensus statement (1)

- [Bateman M, Saunders B, Littlewood C, Hill JC. Development of an optimised physiotherapist-led treatment protocol for lateral elbow tendinopathy: a consensus study using an online nominal group technique. BMJ Open. 2021;11(12):e053841](https://doi.org/10.1136/bmjopen-2021-053841). Access: `full_text`. Type: `consensus_statement`.

### Umbrella review (1)

- [Kinney WR, Anderson BR. Nonoperative Management of Lateral Epicondyle Tendinopathy: An Umbrella Review. J Chiropr Med. 2023;22(3):204-211](https://doi.org/10.1016/j.jcm.2023.04.004). Access: `full_text`. Type: `umbrella_review`.

### Network meta analysis (2)

- [Lowdon H, Chong HH, Dhingra M, Gomaa AR, Teece L, Booth S, et al. Comparison of Interventions for Lateral Elbow Tendinopathy: A Systematic Review and Network Meta-Analysis for Patient-Rated Tennis Elbow Evaluation Pain Outcome. J Hand Surg Am. 2024;49(7):639-648](https://doi.org/10.1016/j.jhsa.2024.03.007). Access: `abstract_only`. Type: `network_meta_analysis`.
- [Ouyang C, You Q, Yang C, Yu D. Time-dependent comparative efficacy of non-surgical treatments for pain relief in lateral epicondylitis: a systematic review and network meta-analysis. Front Physiol. 2026;17:1782562](https://doi.org/10.3389/fphys.2026.1782562). Access: `abstract_only`. Type: `network_meta_analysis`.

### Systematic review (16)

- [Chen Q, Shen P, Zhang B, Chen Y, Zheng C. Long-term effectiveness of conservative management for lateral epicondylitis: a meta-analysis. J Plast Surg Hand Surg. 2023;58:67-73](https://doi.org/10.2340/jphs.v58.12333). Access: `abstract_only`. Type: `systematic_review`.
- [Chen Z, Baker NA. Effectiveness of eccentric strengthening in the treatment of lateral elbow tendinopathy: A systematic review with meta-analysis. J Hand Ther. 2021;34(1):18-28](https://doi.org/10.1016/j.jht.2020.02.002). Access: `abstract_only`. Type: `systematic_review`.
- [Dion S, Wong JJ, Côté P, Yu H, Sutton D, Randhawa K, et al. Are Passive Physical Modalities Effective for the Management of Common Soft Tissue Injuries of the Elbow?: A Systematic Review by the Ontario Protocol for Traffic Injury Management (OPTIMa) Collaboration. Clin J Pain. 2017;33(1):71-86](https://doi.org/10.1097/AJP.0000000000000368). Access: `abstract_only`. Type: `systematic_review`.
- [Hoogvliet P, Randsdorp MS, Dingemanse R, Koes BW, Huisstede BM. Does effectiveness of exercise therapy and mobilisation techniques offer guidance for the treatment of lateral and medial epicondylitis? A systematic review. Br J Sports Med. 2013;47(17):1112-9](https://doi.org/10.1136/bjsports-2012-091990). Access: `abstract_only`. Type: `systematic_review`.
- [Karanasios S, Korakakis V, Whiteley R, Vasilogeorgis I, Woodbridge S, Gioftsos G. Exercise interventions in lateral elbow tendinopathy have better outcomes than passive interventions, but the effects are small: a systematic review and meta-analysis of 2123 subjects in 30 trials. Br J Sports Med. 2021;55(9):477-485. Abstract only was read](https://doi.org/10.1136/bjsports-2020-102525). Access: `abstract_only`. Type: `systematic_review`.
- [Karjalainen TV, Silagy M, O'Bryan E, Johnston RV, Cyril S, Buchbinder R. Autologous blood and platelet-rich plasma injection therapy for lateral elbow pain. Cochrane Database Syst Rev. 2021;9(9):CD010951](https://doi.org/10.1002/14651858.CD010951.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Loew LM, Brosseau L, Tugwell P, Wells GA, Welch V, Shea B, et al. Deep transverse friction massage for treating lateral elbow or lateral knee tendinitis. Cochrane Database Syst Rev. 2014;2014(11):CD003528](https://doi.org/10.1002/14651858.CD003528.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Menta R, Randhawa K, Côté P, Wong JJ, Yu H, Sutton D, et al. The effectiveness of exercise for the management of musculoskeletal disorders and injuries of the elbow, forearm, wrist, and hand: a systematic review by the Ontario Protocol for Traffic Injury Management (OPTIMa) collaboration. J Manipulative Physiol Ther. 2015;38(7):507-20](https://doi.org/10.1016/j.jmpt.2015.06.002). Access: `abstract_only`. Type: `systematic_review`.
- [Olaussen M, Holmedal O, Lindbaek M, Brage S, Solvang H. Treating lateral epicondylitis with corticosteroid injections or non-electrotherapeutical physiotherapy: a systematic review. BMJ Open. 2013;3(10):e003564](https://doi.org/10.1136/bmjopen-2013-003564). Access: `full_text`. Type: `systematic_review`.
- [Raman J, MacDermid JC, Grewal R. Effectiveness of different methods of resistance exercises in lateral epicondylosis--a systematic review. J Hand Ther. 2012;25(1):5-25; quiz 26](https://doi.org/10.1016/j.jht.2011.09.001). Access: `abstract_only`. Type: `systematic_review`.
- [Shahabi S, Bagheri Lankarani K, Heydari ST, Jalali M, Ghahramani S, Kamyab M, et al. The effects of counterforce brace on pain in subjects with lateral elbow tendinopathy: A systematic review and meta-analysis of randomized controlled trials. Prosthet Orthot Int. 2020;44(5):341-354](https://doi.org/10.1177/0309364620930618). Access: `abstract_only`. Type: `systematic_review`.
- [Soliman EFF, Nagra D, Goh SL, Akingbade E, Nouri B, Amin S, et al. Isometric Exercises for Tendinopathies: A Systematic Literature Review. Clin Ther. 2026;48(10):952-957](https://doi.org/10.1016/j.clinthera.2026.04.027). Access: `abstract_only`. Type: `systematic_review`.
- [Struijs PA, Smidt N, Arola H, Dijk vC, Buchbinder R, Assendelft WJ. Orthotic devices for the treatment of tennis elbow. Cochrane Database Syst Rev. 2002;(1):CD001821](https://doi.org/10.1002/14651858.CD001821). Access: `abstract_only`. Type: `systematic_review`.
- [van Rijn RM, Huisstede BM, Koes BW, Burdorf A. Associations between work-related factors and specific disorders at the elbow: a systematic literature review. Rheumatology (Oxford). 2009;48(5):528-36](https://doi.org/10.1093/rheumatology/kep013). Access: `abstract_only`. Type: `systematic_review`.
- [Wallis JA, Bourne AM, Jessup RL, Johnston RV, Frydman A, Cyril S, et al. Manual therapy and exercise for lateral elbow pain. Cochrane Database Syst Rev. 2024;5(5):CD013042](https://doi.org/10.1002/14651858.CD013042.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Yoon SY, Kim YW, Shin IS, Kang S, Moon HI, Lee SC. The Beneficial Effects of Eccentric Exercise in the Management of Lateral Elbow Tendinopathy: A Systematic Review and Meta-Analysis. J Clin Med. 2021;10(17):3968](https://doi.org/10.3390/jcm10173968). Access: `full_text`. Type: `systematic_review`.

### Scoping review (1)

- [Taylor L, Wiebusch M, Bisset LM, Coombes BK. Adherence to exercise in lateral elbow tendinopathy, a scoping review. Musculoskelet Sci Pract. 2024;72:102978](https://doi.org/10.1016/j.msksp.2024.102978). Access: `abstract_only`. Type: `scoping_review`.

### RCT (20)

- [Bateman M, Skeggs A, Whitby E, Fletcher-Barrett V, Stephens G, Dawes M, et al. Optimising physiotherapy for people with lateral elbow tendinopathy - Results of a mixed-methods pilot and feasibility randomised controlled trial (OPTimisE). Musculoskelet Sci Pract. 2024;69:102905](https://doi.org/10.1016/j.msksp.2023.102905). Access: `full_text`. Type: `rct`.
- [Bisset L, Beller E, Jull G, Brooks P, Darnell R, Vicenzino B. Mobilisation with movement and exercise, corticosteroid injection, or wait and see for tennis elbow: randomised trial. BMJ. 2006;333(7575):939](https://doi.org/10.1136/bmj.38961.584653.AE). Access: `full_text`. Type: `rct`.
- [Coombes BK, Bisset L, Brooks P, Khan A, Vicenzino B. Effect of corticosteroid injection, physiotherapy, or both on clinical outcomes in patients with unilateral lateral epicondylalgia: a randomized controlled trial. JAMA. 2013;309(5):461-9](https://doi.org/10.1001/jama.2013.129). Access: `abstract_only`. Type: `rct`.
- [Coombes BK, Hams A, Tenbrink R, Love A, Bisset LM. Mobilisation-with-movement induces analgesia during exercise but exercise alone is not analgesic in people with lateral elbow tendinopathy: An assessor blinded, randomised crossover trial. Musculoskelet Sci Pract. 2025;80:103421](https://doi.org/10.1016/j.msksp.2025.103421). Access: `abstract_only`. Type: `rct`.
- [Divya Mary SM, Paul J, Hema Suresh V, Senthil P. Impact of Heavy Slow Resistance vs. Convectional Exercise in Lateral Epicondylitis-A Pilot Study. Health Care Sci. 2025;4(2):71-81](https://doi.org/10.1002/hcs2.70004). Access: `full_text`. Type: `rct`.
- [El Melhat AM, Saab IM, Abbas RL, El Khatib A, Mohamed RA, Hijazi B, et al. Addition of low-load blood flow restriction training to conventional physiotherapy improves handgrip strength and function in patients with lateral epicondylitis at the short-term: A single-blind randomized clinical trial. J Back Musculoskelet Rehabil. 2026;:10538127261494053](https://doi.org/10.1177/10538127261494053). Access: `abstract_only`. Type: `rct`.
- [Karanasios S, Korakakis V, Moutzouri M, Xergia SA, Tsepis E, Gioftsos G. Low-Load Resistance Training With Blood Flow Restriction Is Effective for Managing Lateral Elbow Tendinopathy: A Randomized, Sham-Controlled Trial. J Orthop Sports Phys Ther. 2022;52(12):803-825](https://doi.org/10.2519/jospt.2022.11211). Access: `abstract_only`. Type: `rct`.
- [McQueen KS, Powell RK, Keener T, Whalley R, Calfee RP. Role of strengthening during nonoperative treatment of lateral epicondyle tendinopathy. J Hand Ther. 2021;34(4):619-626](https://doi.org/10.1016/j.jht.2020.10.009). Access: `abstract_only`. Type: `rct`.
- [Nishizuka T, Iwatsuki K, Kurimoto S, Yamamoto M, Hirata H. Efficacy of a forearm band in addition to exercises compared with exercises alone for lateral epicondylitis: A multicenter, randomized, controlled trial. J Orthop Sci. 2017;22(2):289-294](https://doi.org/10.1016/j.jos.2016.11.011). Access: `abstract_only`. Type: `rct`.
- [Park JH, Kim H, Lee MM. Effects of Blood Flow Restriction Training Combined With Conventional Physical Therapy on Pain, Muscle Architecture, Strength, Function, and Psychosocial Outcomes in Chronic Lateral Epicondylitis: A Pilot Randomized Controlled Trial. Med Sci Monit. 2026;32:e951969](https://doi.org/10.12659/MSM.951969). Access: `full_text`. Type: `rct`.
- [Park JY, Park HK, Choi JH, Moon ES, Kim BS, Kim WS, et al. Prospective evaluation of the effectiveness of a home-based program of isometric strengthening exercises: 12-month follow-up. Clin Orthop Surg. 2010;2(3):173-8](https://doi.org/10.4055/cios.2010.2.3.173). Access: `full_text`. Type: `rct`.
- [Peterson M, Butler S, Eriksson M, Svärdsudd K. A randomized controlled trial of eccentric vs. concentric graded exercise in chronic tennis elbow (lateral elbow tendinopathy). Clin Rehabil. 2014;28(9):862-72](https://doi.org/10.1177/0269215514527595). Access: `abstract_only`. Type: `rct`.
- [Peterson M, Butler S, Eriksson M, Svärdsudd K. A randomized controlled trial of exercise versus wait-list in chronic tennis elbow (lateral epicondylosis). Ups J Med Sci. 2011;116(4):269-79](https://doi.org/10.3109/03009734.2011.600476). Access: `full_text`. Type: `rct`.
- [Sevier TL, Stegink-Jansen CW. Astym treatment vs. eccentric exercise for lateral elbow tendinopathy: a randomized controlled clinical trial. PeerJ. 2015;3:e967](https://doi.org/10.7717/peerj.967). Access: `full_text`. Type: `rct`.
- [Smidt N, van der Windt DA, Assendelft WJ, Devillé WL, Korthals-de Bos IB, Bouter LM. Corticosteroid injections, physiotherapy, or a wait-and-see policy for lateral epicondylitis: a randomised controlled trial. Lancet. 2002;359(9307):657-62](https://doi.org/10.1016/S0140-6736%2802%2907811-X). Access: `abstract_only`. Type: `rct`.
- [Stasinopoulos D, Stasinopoulos I. Comparison of effects of eccentric training, eccentric-concentric training, and eccentric-concentric training combined with isometric contraction in the treatment of lateral elbow tendinopathy. J Hand Ther. 2017;30(1):13-19](https://doi.org/10.1016/j.jht.2016.09.001). Access: `abstract_only`. Type: `rct`.
- [Sveinall H, Brox JI, Engebretsen KB, Hoksrud AF, Røe C, Johnsen MB. Heavy slow resistance training, radial extracorporeal shock wave therapy or advice for patients with tennis elbow in the Norwegian secondary care: a randomised controlled feasibility trial. BMJ Open. 2024;14(12):e085916](https://doi.org/10.1136/bmjopen-2024-085916). Access: `full_text`. Type: `rct`.
- [Tyler TF, Thomas GC, Nicholas SJ, McHugh MP. Addition of isolated wrist extensor eccentric exercise to standard treatment for chronic lateral epicondylosis: a prospective randomized trial. J Shoulder Elbow Surg. 2010;19(6):917-22](https://doi.org/10.1016/j.jse.2010.04.041). Access: `abstract_only`. Type: `rct`.
- [Vuvan V, Vicenzino B, Mellor R, Heales LJ, Coombes BK. Unsupervised Isometric Exercise versus Wait-and-See for Lateral Elbow Tendinopathy. Med Sci Sports Exerc. 2020;52(2):287-295. Abstract only was read, plus the guideline's evidence table](https://doi.org/10.1249/mss.0000000000002128). Access: `abstract_only`. Type: `rct`.
- [Wen DY, Schultz BJ, Schaal B, Graham ST, Kim BS. Eccentric strengthening for chronic lateral epicondylosis: a prospective randomized study. Sports Health. 2011;3(6):500-3](https://doi.org/10.1177/1941738111409690). Access: `full_text`. Type: `rct`.

### Non randomised trial (1)

- [Stasinopoulos D, Stasinopoulos I, Pantelis M, Stasinopoulou K. Comparison of effects of a home exercise programme and a supervised exercise programme for the management of lateral elbow tendinopathy. Br J Sports Med. 2010;44(8):579-83](https://doi.org/10.1136/bjsm.2008.049759). Access: `abstract_only`. Type: `non_randomised_trial`.

### Experimental study (1)

- [Coombes BK, Wiebusch M, Heales L, Stephenson A, Vicenzino B. Isometric Exercise Above but not Below an Individual's Pain Threshold Influences Pain Perception in People With Lateral Epicondylalgia. Clin J Pain. 2016;32(12):1069-1075](https://doi.org/10.1097/AJP.0000000000000365). Access: `abstract_only`. Type: `experimental_study`.

### Case report with review (1)

- [Cha J, York B, Tawfik J. Posterior Interosseous Nerve Compression. Eplasty. 2014;14:ic4](https://pubmed.ncbi.nlm.nih.gov/24570771/). Access: `full_text`. Type: `case_report_with_review`.

### Biomechanical (5)

- [Di Domizio J, Keir PJ. Forearm posture and grip effects during push and pull tasks. Ergonomics. 2010;53(3):336-43](https://doi.org/10.1080/00140130903389076). Access: `abstract_only`. Type: `biomechanical`.
- [Ikeda K, Kaneoka K, Matsunaga N, Ikumi A, Yamazaki M, Yoshii Y. Effects of forearm rotation on wrist flexor and extensor muscle activities. J Orthop Surg Res. 2025;20(1):53](https://doi.org/10.1186/s13018-024-05363-x). Access: `full_text`. Type: `biomechanical`.
- [Lee SY, Chieh HF, Lin CJ, Jou IM, Kuo LC, Su FC. The Potential Risk Factors Relevant to Lateral Epicondylitis by Wrist Coupling Posture. PLoS One. 2016;11(5):e0155379](https://doi.org/10.1371/journal.pone.0155379). Access: `full_text`. Type: `biomechanical`.
- [Manickaraj N, Bisset LM, Kavanagh JJ. Lateral epicondylalgia exhibits adaptive muscle activation strategies based on wrist posture and levels of grip force: a case-control study. J Musculoskelet Neuronal Interact. 2018;18(3):323-332](https://pubmed.ncbi.nlm.nih.gov/30179209/). Access: `full_text`. Type: `biomechanical`.
- [Mogk JP, Keir PJ. The effects of posture on forearm muscle loading during gripping. Ergonomics. 2003;46(9):956-75](https://doi.org/10.1080/0014013031000107595). Access: `abstract_only`. Type: `biomechanical`.

### RCT protocol (1)

- [Bateman M, Saunders B, Littlewood C, Davis D, Beckhelling J, Cooper K, et al. Comparing an optimised physiotherapy treatment package with usual physiotherapy care for people with tennis elbow - protocol for the OPTimisE pilot and feasibility randomised controlled trial. Pilot Feasibility Stud. 2022;8(1):178](https://doi.org/10.1186/s40814-022-01132-x). Access: `full_text`. Type: `rct_protocol`.

### Economic evaluation (1)

- [Coombes BK, Connelly L, Bisset L, Vicenzino B. Economic evaluation favours physiotherapy but not corticosteroid injection as a first-line intervention for chronic lateral epicondylalgia: evidence from a randomised clinical trial. Br J Sports Med. 2016;50(22):1400-1405](https://doi.org/10.1136/bjsports-2015-094729). Access: `abstract_only`. Type: `economic_evaluation`.

### Service audit (1)

- [Barratt PA, Selfe J. A service evaluation and improvement project: a three year systematic audit cycle of the physiotherapy treatment for Lateral Epicondylalgia. Physiotherapy. 2018;104(2):209-216](https://doi.org/10.1016/j.physio.2017.09.001). Access: `abstract_only`. Type: `service_audit`.

### Methodological review (1)

- [Shah R, Yu A, Kelley MG, Yendluri A, Bienstock D, Nietsch K, et al. Randomized controlled trial outcomes for nonoperative management of lateral epicondylitis of the elbow are statistically fragile: a systematic review. JSES Rev Rep Tech. 2025;5(4):798-804](https://doi.org/10.1016/j.xrrt.2025.03.008). Access: `abstract_only`. Type: `methodological_review`.

### Clinical commentary (3)

- [Stasinopoulos D, Stasinopoulou K, Johnson MI. An exercise programme for the management of lateral elbow tendinopathy. Br J Sports Med. 2005;39(12):944-7](https://doi.org/10.1136/bjsm.2005.019836). Access: `abstract_only`. Type: `clinical_commentary`.
- [Stasinopoulos D. Isometric Exercise for the Management of Lateral Elbow Tendinopathy. J Clin Med. 2022;12(1)](https://doi.org/10.3390/jcm12010094). Access: `full_text`. Type: `clinical_commentary`.
- [Stasinopoulos D. Stop Using the Eccentric Exercises as the Gold Standard Treatment for the Management of Lateral Elbow Tendinopathy. J Clin Med. 2022;11(5)](https://doi.org/10.3390/jcm11051325). Access: `full_text`. Type: `clinical_commentary`.

## Carpal tunnel syndrome

Condition file: [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md)

### Clinical practice guideline (4)

- [Cartwright MS, Hobson-Webb LD, Boon AJ, et al. Evidence-based guideline: neuromuscular ultrasound for the diagnosis of carpal tunnel syndrome. Muscle Nerve. 2012;46(2):287-293](https://doi.org/10.1002/mus.23389). Access: `abstract_only`. Type: `clinical_practice_guideline`.
- [Erickson M, Lawrence M, Lazinski MJ, Scott K, Martin RL. Hand Pain and Sensory Deficits: Carpal Tunnel Syndrome: Revision 2026. J Orthop Sports Phys Ther. 2026;56(4):CPG1-CPG79](https://doi.org/10.2519/jospt.2026.0301). Access: `registry_or_summary`. Type: `clinical_practice_guideline`.
- [Graham B, Peljovich AE, Afra R, et al. The American Academy of Orthopaedic Surgeons Evidence-Based Clinical Practice Guideline on: Management of Carpal Tunnel Syndrome. J Bone Joint Surg Am. 2016;98(20):1750-1754. Full guideline PDF from aaos.org. Superseded by the 2024 guideline](https://doi.org/10.2106/JBJS.16.00719). Access: `full_text`. Type: `clinical_practice_guideline`.
- [Shapiro LM, Kamal R, Brault J, et al. American Academy of Orthopaedic Surgeons/ASSH Clinical Practice Guideline Summary: Management of Carpal Tunnel Syndrome. J Am Acad Orthop Surg. 2025;33(7):e356-e366. Erratum: 2026;34(20):e2922 (PMID 42825588, record seen, content not read)](https://doi.org/10.5435/JAAOS-D-24-01179). Access: `full_text`. Type: `clinical_practice_guideline`.

### Guideline (2)

- [British Orthopaedic Association, Royal College of Surgeons of England, BSSH. Commissioning guide: Treatment of carpal and cubital tunnel syndromes. Version 2.1, November 2017; evidence search January 2016](https://boa.ac.uk/asset/DEF06E52-94D4-46B9-A482B7C4B9DED7F8). Access: `full_text`. Type: `guideline`. No DOI or PMID recorded; links to the source page.
- [Franklin GM, Friedman AS. Work-Related Carpal Tunnel Syndrome: Diagnosis and Treatment Guideline. Phys Med Rehabil Clin N Am. 2015;26(3):523-537](https://doi.org/10.1016/j.pmr.2015.04.003). Access: `abstract_only`. Type: `guideline`.

### Secondary guideline summary (4)

- [Erickson M, Lawrence M, Jansen CWS, Coker D, Amadio P, Cleary C. Hand Pain and Sensory Deficits: Carpal Tunnel Syndrome. J Orthop Sports Phys Ther. 2019;49(5):CPG1-CPG85. The final text returned HTTP 403 and was not read. What was read: the Guideline Central summary of the final text, and the APTA public-comment draft, which is marked 'Do Not Cite'](https://doi.org/10.2519/jospt.2019.0301). Access: `registry_or_summary`. Type: `secondary_guideline_summary`.
- [iatroX educational summary of AAOS, Management of Carpal Tunnel Syndrome: Evidence-Based Clinical Practice Guideline (2024). The original guideline was not read](https://iatrox.com/guidelines/carpal-tunnel-syndrome). Access: `registry_or_summary`. Type: `secondary_guideline_summary`. No DOI or PMID recorded; links to the source page.
- [Sadler KP, Torbert DR, Writer JK. Management of Carpal Tunnel Syndrome: Guidelines From the AAOS. Am Fam Physician. 2025;112(3):337-339](https://pubmed.ncbi.nlm.nih.gov/40961320/). Access: `registry_or_summary`. Type: `secondary_guideline_summary`.
- [Vivanco A. Carpal Tunnel Syndrome: The New 2026 Clinical Practice Guideline for Physiotherapists. Physiotutors, 14 October 2026. Secondary summary of Erickson et al., J Orthop Sports Phys Ther. 2026;56(4):CPG1-CPG79 (doi:10.2519/jospt.2026.0301, PMID 41919928), which was not read](https://www.physiotutors.com/?p=29289). Access: `registry_or_summary`. Type: `secondary_guideline_summary`. No DOI or PMID recorded; links to the source page.

### Consensus statement (5)

- [Huisstede BM, Fridén J, Coert JH, Hoogvliet P; European HANDGUIDE Group. Carpal tunnel syndrome: hand surgeons, hand therapists, and physical medicine and rehabilitation physicians agree on a multidisciplinary treatment guideline: results from the European HANDGUIDE Study. Arch Phys Med Rehabil. 2014;95(12):2253-2263](https://doi.org/10.1016/j.apmr.2014.06.022). Access: `abstract_only`. Type: `consensus_statement`.
- [Newington L, Madan I, Sandford F. Driving, work, wound care and rehabilitation after carpal tunnel release: Consensus recommendations from a UK Delphi study. Hand Ther. 2022;27:71-82](https://doi.org/10.1177/17589983221113870). Access: `full_text`. Type: `consensus_statement`.
- [Pelosi L, Arányi Z, Beekman R, et al. Expert consensus on the combined investigation of carpal tunnel syndrome with electrodiagnostic tests and neuromuscular ultrasound. Clin Neurophysiol. 2022;135:107-116](https://doi.org/10.1016/j.clinph.2021.12.012). Access: `abstract_only`. Type: `consensus_statement`.
- [Rempel D, Evanoff B, Amadio PC, et al. Consensus criteria for the classification of carpal tunnel syndrome in epidemiologic studies. Am J Public Health. 1998;88(10):1447-1451](https://doi.org/10.2105/ajph.88.10.1447). Access: `abstract_only`. Type: `consensus_statement`.
- [Robinson LR, Al-Lawati ZJ, Andary M, et al. AANEM Consensus-Based Guideline for Grading the Severity of Carpal Tunnel Syndrome. Muscle Nerve. 2026 Sep 12 (online ahead of print)](https://doi.org/10.1002/mus.70384). Access: `abstract_only`. Type: `consensus_statement`.

### Systematic review (24)

- [Ballestero-Perez R, Plaza-Manzano G, Urraca-Gesto A, et al. Effectiveness of Nerve Gliding Exercises on Carpal Tunnel Syndrome: A Systematic Review. J Manipulative Physiol Ther. 2017;40:50-59. Abstract read](https://doi.org/10.1016/j.jmpt.2016.10.004). Access: `abstract_only`. Type: `systematic_review`.
- [Barcenilla A, March LM, Chen JS, Sambrook PN. Carpal tunnel syndrome and its relationship to occupation: a meta-analysis. Rheumatology (Oxford). 2012;51:250-261](https://doi.org/10.1093/rheumatology/ker108). Access: `abstract_only`. Type: `systematic_review`.
- [Cavalcante MC, Moraes VY, Osés GL, Nakachima LR, Belloti JC. Quality analysis of prior systematic reviews of carpal tunnel syndrome: an overview of the literature. Sao Paulo Med J. 2022;141(5):e20211020](https://doi.org/10.1590/1516-3180.2021.1020.R2.10102022). Access: `abstract_only`. Type: `systematic_review`.
- [Coenen P, van der Molen HF, Burdorf A, et al. Associations of screen work with neck and upper extremity symptoms: a systematic review with meta-analysis. Occup Environ Med. 2019;76(7):502-509](https://doi.org/10.1136/oemed-2018-105553). Access: `abstract_only`. Type: `systematic_review`.
- [Gerger H, Macri EM, Jackson JA, et al. Physical and psychosocial work-related exposures and the incidence of carpal tunnel syndrome: A systematic review of prospective studies. Appl Ergon. 2024;117:104211](https://doi.org/10.1016/j.apergo.2023.104211). Access: `abstract_only`. Type: `systematic_review`.
- [Hassan A, Beumer A, Kuijer PPFM, van der Molen HF. Work-relatedness of carpal tunnel syndrome: systematic review including meta-analysis and GRADE. Health Sci Rep. 2022;5(6):e888](https://doi.org/10.1002/hsr2.888). Access: `full_text`. Type: `systematic_review`.
- [Jiménez-Del-Barrio S, Cadellans-Arróniz A, Ceballos-Laita L, et al. The effectiveness of manual therapy on pain, physical function, and nerve conduction studies in carpal tunnel syndrome patients: a systematic review and meta-analysis. Int Orthop. 2022;46(2):301-312](https://doi.org/10.1007/s00264-021-05272-2). Access: `full_text`. Type: `systematic_review`.
- [Karjalainen TV, Lusa V, Page MJ, O'Connor D, Massy-Westropp N, Peters SE. Splinting for carpal tunnel syndrome. Cochrane Database Syst Rev. 2023;2(2):CD010003](https://doi.org/10.1002/14651858.CD010003.pub2). Access: `full_text`. Type: `systematic_review`.
- [Kim SD. Efficacy of tendon and nerve gliding exercises for carpal tunnel syndrome: a systematic review of randomized controlled trials. J Phys Ther Sci. 2015;27:2645-2648](https://doi.org/10.1589/jpts.27.2645). Access: `abstract_only`. Type: `systematic_review`.
- [Kozak A, Schedlbauer G, Wirth T, Euler U, Westermann C, Nienhaus A. Association between work-related biomechanical risk factors and the occurrence of carpal tunnel syndrome: an overview of systematic reviews and a meta-analysis of current research. BMC Musculoskelet Disord. 2015;16:231](https://doi.org/10.1186/s12891-015-0685-0). Access: `abstract_only`. Type: `systematic_review`.
- [Kumari O, Mishra R, Singh V, Gowda AKS, Harisha R. Conservative management of carpal tunnel syndrome during pregnancy and lactation: A systematic review of mixed study designs. J Clin Orthop Trauma. 2026;82:103608](https://doi.org/10.1016/j.jcot.2026.103608). Access: `abstract_only`. Type: `systematic_review`.
- [Lim YH, Chee DY, Girdler S, Lee HC. Median nerve mobilization techniques in the treatment of carpal tunnel syndrome: A systematic review. J Hand Ther. 2017;30(4):397-406](https://doi.org/10.1016/j.jht.2017.06.019). Access: `abstract_only`. Type: `systematic_review`.
- [Lusa V, Karjalainen TV, Pääkkönen M, Rajamäki TJ, Jaatinen K. Surgical versus non-surgical treatment for carpal tunnel syndrome. Cochrane Database Syst Rev. 2024;1(1):CD001552](https://doi.org/10.1002/14651858.CD001552.pub3). Access: `abstract_only`. Type: `systematic_review`.
- [Medina McKeon JM, Yancosek KE. Neural gliding techniques for the treatment of carpal tunnel syndrome: a systematic review. J Sport Rehabil. 2008;17:324-341. Abstract read](https://doi.org/10.1123/jsr.17.3.324). Access: `abstract_only`. Type: `systematic_review`.
- [Mehta P, Stubbs PW, Tamminga SJ, et al. Ergonomic interventions for treating work-related complaints of the arm, neck or shoulder in adults. Cochrane Database Syst Rev. 2026;10(10):CD014644](https://doi.org/10.1002/14651858.CD014644.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Nilsson T, Wahlström J, Burström L. Hand-arm vibration and the risk of vascular and neurological diseases: a systematic review and meta-analysis. PLoS One. 2017;12:e0180795](https://doi.org/10.1371/journal.pone.0180795). Access: `abstract_only`. Type: `systematic_review`.
- [Núñez de Arenas-Arroyo S, Cavero-Redondo I, Torres-Costoso A, Reina-Gutiérrez S, Álvarez-Bueno C, Martínez-Vizcaíno V. Short-term Effects of Neurodynamic Techniques for Treating Carpal Tunnel Syndrome: A Systematic Review With Meta-analysis. J Orthop Sports Phys Ther. 2021;51(12):566-580](https://doi.org/10.2519/jospt.2021.10533). Access: `abstract_only`. Type: `systematic_review`.
- [O'Connor D, Marshall S, Massy-Westropp N. Non-surgical treatment (other than steroid injection) for carpal tunnel syndrome. Cochrane Database Syst Rev. 2003;2003(1):CD003219](https://doi.org/10.1002/14651858.CD003219). Access: `abstract_only`. Type: `systematic_review`.
- [O'Connor D, Page MJ, Marshall SC, Massy-Westropp N. Ergonomic positioning or equipment for treating carpal tunnel syndrome. Cochrane Database Syst Rev. 2012;1(1):CD009600](https://doi.org/10.1002/14651858.CD009600). Access: `abstract_only`. Type: `systematic_review`.
- [Page MJ, O'Connor D, Pitt V, Massy-Westropp N. Exercise and mobilisation interventions for carpal tunnel syndrome. Cochrane Database Syst Rev. 2012;(6):CD009899. The Cochrane page lists 2022 Issue 3, published 1 April 2022, but Crossref and Europe PMC record this DOI as 13 June 2012](https://doi.org/10.1002/14651858.CD009899). Access: `full_text`. Type: `systematic_review`.
- [Palmer KT, Harris EC, Coggon D. Carpal tunnel syndrome and its relation to occupation: a systematic literature review. Occup Med (Lond). 2007;57(1):57-66](https://doi.org/10.1093/occmed/kql125). Access: `abstract_only`. Type: `systematic_review`.
- [Paraskevopoulos E, Karanasios S, Gioftsos G, Tatsios P, Koumantakis G, Papandreou M. The effectiveness of neuromobilization exercises in carpal tunnel syndrome: Systematic review and meta-analysis. Physiother Theory Pract. 2023;39(10):2037-2076](https://doi.org/10.1080/09593985.2022.2068097). Access: `abstract_only`. Type: `systematic_review`.
- [Piazzini DB, Aprile I, Ferrara PE, et al. A systematic review of conservative treatment of carpal tunnel syndrome. Clin Rehabil. 2007;21(4):299-314](https://doi.org/10.1177/0269215507077294). Access: `abstract_only`. Type: `systematic_review`.
- [Zaheer SA, Ahmed Z. Neurodynamic Techniques in the Treatment of Mild-to-Moderate Carpal Tunnel Syndrome: A Systematic Review and Meta-Analysis. J Clin Med. 2023;12(15):4888](https://doi.org/10.3390/jcm12154888). Access: `full_text`. Type: `systematic_review`.

### Scoping review (1)

- [Marriott C, Montgomery K, Whelan A. Carpal Tunnel Syndrome in Athletes Who Compete in Wheelchair Sport: A Scoping Review. Am J Phys Med Rehabil. 2023;102:1116-1121](https://doi.org/10.1097/PHM.0000000000002167). Access: `abstract_only`. Type: `scoping_review`.

### RCT (15)

- [Abdolrazaghi HA, Khansari M, Mirshahi M, Ahmadi Pishkuhi M. Effectiveness of Tendon and Nerve Gliding Exercises in the Treatment of Patients With Mild Idiopathic Carpal Tunnel Syndrome: A Randomized Controlled Trial. Hand (N Y). 2023;18:222-229](https://doi.org/10.1177/15589447211006857). Access: `full_text`. Type: `rct`.
- [Akalin E, El O, Peker O, et al. Treatment of carpal tunnel syndrome with nerve and tendon gliding exercises. Am J Phys Med Rehabil. 2002;81:108-113. Abstract read](https://doi.org/10.1097/00002060-200202000-00006). Access: `abstract_only`. Type: `rct`.
- [Arman S, Menekseoglu AK, Sezgin B, Ozgur B, Capan N, Oral A. The effects of virtual reality-mediated tendon and nerve gliding exercises in the conservative management of carpal tunnel syndrome: a double-blind randomized placebo controlled trial. Eur J Phys Rehabil Med. 2024;60:458-469](https://doi.org/10.23736/S1973-9087.24.08432-6). Access: `full_text`. Type: `rct`.
- [Garfinkel MS, Singhal A, Katz WA, Allan DA, Reshetar R, Schumacher HR Jr. Yoga-based intervention for carpal tunnel syndrome: a randomized trial. JAMA. 1998;280(18):1601-1603](https://doi.org/10.1001/jama.280.18.1601). Access: `abstract_only`. Type: `rct`.
- [Hamzeh H, Madi M, Alghwiri AA, Hawamdeh Z. The long-term effect of neurodynamics vs exercise therapy on pain and function in people with carpal tunnel syndrome: a randomized parallel-group clinical trial. J Hand Ther. 2021;34:521-530](https://doi.org/10.1016/j.jht.2020.07.005). Access: `abstract_only`. Type: `rct`.
- [Lewis KJ, Coppieters MW, Ross L, Hughes I, Vicenzino B, Schmid AB. Group education, night splinting and home exercises reduce conversion to surgery for carpal tunnel syndrome: a multicentre randomised trial. J Physiother. 2020;66:97-104](https://doi.org/10.1016/j.jphys.2020.03.007). Access: `abstract_only`. Type: `rct`.
- [Mabrouk MI, Abdalbary SA, El-Shaarawy EAA, et al. Neurodynamic techniques vs. desensitization maneuvers in the manual therapy management of moderate carpal tunnel syndrome among women: randomized clinical trial with 1-year follow-up. Front Pain Res (Lausanne). 2026;7:1792933](https://doi.org/10.3389/fpain.2026.1792933). Access: `full_text`. Type: `rct`.
- [Núñez-Cortés R, Cruz-Montecinos C, López-Bueno L, et al. Acute Clinical Effects of Dual-Task Resistance Exercise on Muscle Endurance and Pressure Sensitivity in Patients With Carpal Tunnel Syndrome After Surgery. Musculoskeletal Care. 2026;24:e70223](https://doi.org/10.1002/msc.70223). Access: `full_text`. Type: `rct`.
- [Paquette P, Higgins J, Danino MA, Harris P, Lamontagne M, Gagnon DH. Effects of a preoperative neuromobilization program offered to individuals with carpal tunnel syndrome awaiting carpal tunnel decompression surgery: a pilot randomized controlled study. J Hand Ther. 2021;34:37-46](https://doi.org/10.1016/j.jht.2019.12.012). Access: `abstract_only`. Type: `rct`.
- [Schmid AB, Elliott JM, Strudwick MW, Little M, Coppieters MW. Effect of splinting and exercise on intraneural edema of the median nerve in carpal tunnel syndrome: an MRI study to reveal therapeutic mechanisms. J Orthop Res. 2012;30:1343-1350](https://doi.org/10.1002/jor.22064). Access: `abstract_only`. Type: `rct`.
- [Sheereen FJ, Sarkar B, Sahay P, et al. Comparison of Two Manual Therapy Programs, including Tendon Gliding Exercises as a Common Adjunct, While Managing the Participants with Chronic Carpal Tunnel Syndrome. Pain Res Manag. 2022;2022:1975803](https://doi.org/10.1155/2022/1975803). Access: `full_text`. Type: `rct`.
- [Shem K, Wong J, Dirlikov B. Effective self-stretching of carpal ligament for the treatment of carpal tunnel syndrome: A double-blinded randomized controlled study. J Hand Ther. 2020;33(3):272-280](https://doi.org/10.1016/j.jht.2019.12.002). Access: `abstract_only`. Type: `rct`.
- [Sim SE, Gunasagaran J, Goh KJ, Ahmad TS. Short-term clinical outcome of orthosis alone vs combination of orthosis, nerve, and tendon gliding exercises and ultrasound therapy for treatment of carpal tunnel syndrome. J Hand Ther. 2019;32:411-416](https://doi.org/10.1016/j.jht.2018.01.004). Access: `abstract_only`. Type: `rct`.
- [Wolny T, Linek P. Is manual therapy based on neurodynamic techniques effective in the treatment of carpal tunnel syndrome? A randomized controlled trial. Clin Rehabil. 2019;33(3):408-417](https://doi.org/10.1177/0269215518805213). Access: `abstract_only`. Type: `rct`.
- [Çağlar S, Altun T, Vural M, Mert M. Evaluation of Splint and Exercise Interventions for Carpal Tunnel Syndrome: Insights from Ultrasonographic Measurements. Med Sci Monit. 2024;30:e945704](https://doi.org/10.12659/MSM.945704). Access: `full_text`. Type: `rct`.

### Non randomised trial (2)

- [Bartkowiak Z, Eliks M, Zgorzalewicz-Stachowiak M, Romanowski L. The Effects of Nerve and Tendon Gliding Exercises Combined with Low-level Laser or Ultrasound Therapy in Carpal Tunnel Syndrome. Indian J Orthop. 2019;53:347-352](https://doi.org/10.4103/ortho.ijortho_45_17). Access: `full_text`. Type: `non_randomised_trial`.
- [Keskin Y, Kilic G, Taspinar O, Posul SO, Halac G, Eren F. Effectiveness of home exercise in pregnant women with carpal tunnel syndrome: randomized control trial. J Pak Med Assoc. 2020;70:202-207](https://doi.org/10.5455/JPMA.1846). Access: `abstract_only`. Type: `non_randomised_trial`.

### Observational (7)

- [Betancourt L, Cowan RE, Chang A, Irwin R. Case-Control Study of Ultrasound Evaluation of Acute Median Nerve Response to Upper Extremity Circuit Training in Spinal Cord Injury. Arch Phys Med Rehabil. 2020;101:1898-1905](https://doi.org/10.1016/j.apmr.2020.05.008). Access: `abstract_only`. Type: `observational`.
- [Chang YW, Chen CJ, Wang YW, Chiu V, Horng YS. Thumb Positioning Modulates Median Nerve Mobility and Morphology During Postural Changes in Carpal Tunnel Syndrome: Implications for Nerve Gliding Exercises. Arch Rehabil Res Clin Transl. 2026;8:100660](https://doi.org/10.1016/j.arrct.2026.100660). Access: `full_text`. Type: `observational`.
- [Christodoulou P, Osarumwense D, Papadopoulos A. Acute Carpal Tunnel Syndrome: Is It Only Wrist Fractures? A Case Series Report and Literature Review. J Orthop Case Rep. 2025;15:275-279](https://doi.org/10.13107/jocr.2025.v15.i08.5968). Access: `full_text`. Type: `observational`.
- [Fernandes LFRM, Cardoso BTA, Paranhos DB, et al. Clinical Decision-Making and Guideline Alignment Among Brazilian Physical Therapists: Evidence From Standardized Carpal Tunnel Syndrome Vignettes. J Eval Clin Pract. 2026;32:e70575](https://doi.org/10.1111/jep.70575). Access: `full_text`. Type: `observational`.
- [Gopalkrishnan S, Ramachandran S, Ring D, Melhorn JM, Crijns TJ. Potential Misinformation in the Official Disability Guidelines About the Diagnosis and Treatment of Carpal Tunnel Syndrome. J Occup Environ Med. 2022;64(7):e424-e430](https://doi.org/10.1097/JOM.0000000000002570). Access: `abstract_only`. Type: `observational`.
- [Huang CH, Liu KC, Cheng JW, Hsu SC, Chen CK. Carpal Tunnel Syndrome in Elite Female Tug-of-War Athletes: Prevalence and Risk Factor Analysis. Diagnostics (Basel). 2024;14](https://doi.org/10.3390/diagnostics14192120). Access: `full_text`. Type: `observational`.
- [Hytönen M, Savolainen A, Reito A, et al. Guidelines and Policies as Levers in Managing Carpal Tunnel Release Demand. MDM Policy Pract. 2026;11(2):23814683261477058](https://doi.org/10.1177/23814683261477058). Access: `abstract_only`. Type: `observational`.

### Diagnostic accuracy (1)

- [Altinok MT, Baysal O, Karakas HM, Firat AK. Sonographic evaluation of the carpal tunnel after provocative exercises. J Ultrasound Med. 2004;23:1301-1306](https://doi.org/10.7863/jum.2004.23.10.1301). Access: `abstract_only`. Type: `diagnostic_accuracy`.

### Biomechanical (15)

- [Cobb TK, Cooney WP, An KN. Aetiology of work-related carpal tunnel syndrome: the role of lumbrical muscles and tool size on carpal tunnel pressures. Ergonomics. 1996;39:103-107](https://doi.org/10.1080/00140139608964437). Access: `abstract_only`. Type: `biomechanical`.
- [Coppieters MW, Butler DS. Do 'sliders' slide and 'tensioners' tension? An analysis of neurodynamic techniques and considerations regarding their application. Man Ther. 2008;13:213-221. Abstract read](https://doi.org/10.1016/j.math.2006.12.008). Access: `abstract_only`. Type: `biomechanical`.
- [Coppieters MW, Hough AD, Dilley A. Different nerve-gliding exercises induce different magnitudes of median nerve longitudinal excursion: an in vivo study using dynamic ultrasound imaging. J Orthop Sports Phys Ther. 2009;39:164-171](https://doi.org/10.2519/jospt.2009.2913). Access: `abstract_only`. Type: `biomechanical`.
- [Gelberman RH, Hergenroeder PT, Hargens AR, Lundborg GN, Akeson WH. The carpal tunnel syndrome. A study of carpal canal pressures. J Bone Joint Surg Am. 1981;63:380-383. Abstract read](https://doi.org/10.2106/00004623-198163030-00009). Access: `abstract_only`. Type: `biomechanical`.
- [Keir PJ, Bach JM, Hudes M, Rempel DM. Guidelines for wrist posture based on carpal tunnel pressure thresholds. Hum Factors. 2007;49:88-99. Abstract read](https://doi.org/10.1518/001872007779598127). Access: `abstract_only`. Type: `biomechanical`.
- [Keir PJ, Bach JM, Rempel DM. Effects of finger posture on carpal tunnel pressure during wrist motion. J Hand Surg Am. 1998;23:1004-1009](https://doi.org/10.1016/S0363-5023%2898%2980007-5). Access: `abstract_only`. Type: `biomechanical`.
- [Keir PJ, Bach JM, Rempel DM. Fingertip loading and carpal tunnel pressure: differences between a pinching and a pressing task. J Orthop Res. 1998;16:112-115. Abstract read](https://doi.org/10.1002/jor.1100160119). Access: `abstract_only`. Type: `biomechanical`.
- [Keir PJ, Bach JM. Flexor muscle incursion into the carpal tunnel: a mechanism for increased carpal tunnel pressure? Clin Biomech (Bristol). 2000;15:301-305](https://doi.org/10.1016/s0268-0033%2899%2900092-3). Access: `abstract_only`. Type: `biomechanical`.
- [Keir PJ, Wells RP, Ranney DA, Lavery W. The effects of tendon load and posture on carpal tunnel pressure. J Hand Surg Am. 1997;22:628-634](https://doi.org/10.1016/S0363-5023%2897%2980119-0). Access: `abstract_only`. Type: `biomechanical`.
- [Li S, Kociolek AM, Loh PY. Characterizing median nerve deformation and recovery processes in response to repeated isometric wrist torque exertions. PeerJ. 2026;14:e21724](https://doi.org/10.7717/peerj.21724). Access: `full_text`. Type: `biomechanical`.
- [Li ZM, Jordan D. Change in Carpal Tunnel Pressure With Dynamic Wrist Flexion/Extension and Radioulnar Deviation: A Cadaveric Study. J Orthop Res. 2026;44(10):e70294](https://doi.org/10.1002/jor.70294). Access: `abstract_only`. Type: `biomechanical`.
- [McGorry RW, Fallentin N, Andersen JH, Keir PJ, Hansen TB, Pransky G, et al. Effect of grip type, wrist motion, and resistance level on pressures within the carpal tunnel of normal wrists. J Orthop Res. 2014;32:524-530](https://doi.org/10.1002/jor.22571). Access: `full_text`. Type: `biomechanical`.
- [Nadar MS, Amr HA, Manee FS, Ali AA. In vivo evidence of lumbricals incursion into the carpal tunnel in healthy hands: an ultrasonographic cross sectional study. J Hand Ther. 2022;35:261-266](https://doi.org/10.1016/j.jht.2022.03.003). Access: `abstract_only`. Type: `biomechanical`.
- [Rempel D, Keir PJ, Smutz WP, Hargens A. Effects of static fingertip loading on carpal tunnel pressure. J Orthop Res. 1997;15:422-426. Abstract read](https://doi.org/10.1002/jor.1100150315). Access: `abstract_only`. Type: `biomechanical`.
- [Schmid AB, Kubler PA, Johnston V, Coppieters MW. A vertical mouse and ergonomic mouse pads alter wrist position but do not reduce carpal tunnel pressure in patients with carpal tunnel syndrome. Appl Ergon. 2015;47:151-156](https://doi.org/10.1016/j.apergo.2014.08.020). Access: `abstract_only`. Type: `biomechanical`.

### Narrative review (2)

- [Padua L, Coraci D, Erra C, et al. Carpal tunnel syndrome: clinical features, diagnosis, and management. Lancet Neurol. 2016;15:1273-1284. Abstract read](https://doi.org/10.1016/s1474-4422%2816%2930231-9). Access: `abstract_only`. Type: `narrative_review`.
- [Vignais N, Weresch J, Keir PJ. Posture and Loading in the Pathomechanics of Carpal Tunnel Syndrome: A Review. Crit Rev Biomed Eng. 2016;44:397-410](https://doi.org/10.1615/CritRevBiomedEng.2017021073). Access: `abstract_only`. Type: `narrative_review`.

### Expert opinion (1)

- [Chartered Society of Physiotherapy. Carpal tunnel syndrome rehabilitation exercises (patient information)](https://www.csp.org.uk/public-patient/rehabilitation-exercises/carpal-tunnel-syndrome). Access: `full_text`. Type: `expert_opinion`. No DOI or PMID recorded; links to the source page.

### Patient information (2)

- [American Academy of Orthopaedic Surgeons, OrthoInfo. Carpal Tunnel Syndrome (patient information)](https://www.orthoinfo.org/en/diseases--conditions/carpal-tunnel-syndrome/). Access: `full_text`. Type: `patient_information`. No DOI or PMID recorded; links to the source page.
- [NHS. Carpal tunnel syndrome (patient information)](https://www.nhs.uk/conditions/carpal-tunnel-syndrome/). Access: `full_text`. Type: `patient_information`. No DOI or PMID recorded; links to the source page.

### Secondary summary (1)

- [Physiotutors. Carpal tunnel syndrome diagnosis article, Research: Diagnosis & Imaging, 24 April 2026. Secondary narrative summary](https://www.physiotutors.com/research/carpal-tunnel-syndrome-diagnosis/). Access: `registry_or_summary`. Type: `secondary_summary`. No DOI or PMID recorded; links to the source page.

## Patellofemoral pain

Condition file: [patellofemoral_pain](patellofemoral_pain.md)

### Clinical practice guideline (2)

- [Ophey M, Koeter S, van Ooijen L, et al. Dutch multidisciplinary guideline on anterior knee pain: Patellofemoral pain and patellar tendinopathy. Knee Surg Sports Traumatol Arthrosc. 2025;33(2):457-469](https://doi.org/10.1002/ksa.12367). Access: `full_text`. Type: `clinical_practice_guideline`.
- [Willy RW, Hoglund LT, Barton CJ, et al. Patellofemoral Pain. J Orthop Sports Phys Ther. 2019;49(9):CPG1-CPG95. PubMed abstract opened; publisher full text returned HTTP 403 and was not read](https://doi.org/10.2519/jospt.2019.0302). Access: `abstract_only`. Type: `clinical_practice_guideline`.

### Guideline summary (1)

- [Marra J. Patellofemoral pain: guidelines from the American Physical Therapy Association. Am Fam Physician. 2020;102(7):442-443](https://www.aafp.org/pubs/afp/issues/2020/1001/p442.html). Access: `full_text`. Type: `guideline_summary`. No DOI or PMID recorded; links to the source page.

### Secondary guideline summary (1)

- [Connell G, To D, Ashraf M, Verville L. Appraisal and summary of patellofemoral pain clinical practice guideline. J Can Chiropr Assoc. 2020;64(3):180-186](https://pubmed.ncbi.nlm.nih.gov/33487639/). Access: `full_text`. Type: `secondary_guideline_summary`.

### Best practice guide (2)

- [Barton CJ, Lack S, Hemmings S, Tufail S, Morrissey D. The 'Best Practice Guide to Conservative Management of Patellofemoral Pain': incorporating level 1 evidence with expert clinical reasoning. Br J Sports Med. 2015;49(14):923-934](https://doi.org/10.1136/bjsports-2014-093637). Access: `abstract_only`. Type: `best_practice_guide`.
- [Neal BS, Lack SD, Bartholomew C, Morrissey D. Best practice guide for patellofemoral pain based on synthesis of a systematic review, the patient voice and expert clinical reasoning. Br J Sports Med. 2024;58(24):1486-1495](https://doi.org/10.1136/bjsports-2024-108110). Access: `abstract_only`. Type: `best_practice_guide`.

### Position statement (1)

- [Bolgla LA, Boling MC, Mace KL, DiStefano MJ, Fithian DC, Powers CM. National Athletic Trainers' Association Position Statement: Management of Individuals With Patellofemoral Pain. J Athl Train. 2018;53(9):820-836](https://doi.org/10.4085/1062-6050-231-15). Access: `full_text`. Type: `position_statement`.

### Consensus statement (4)

- [Collins NJ, Barton CJ, van Middelkoop M, et al. 2018 Consensus statement on exercise therapy and physical interventions (orthoses, taping and manual therapy) to treat patellofemoral pain. Br J Sports Med. 2018;52(18):1170-1178. Abstract read; publisher full text returned HTTP 403](https://doi.org/10.1136/bjsports-2018-099397). Access: `abstract_only`. Type: `consensus_statement`.
- [Crossley KM, Stefanik JJ, Selfe J, et al. 2016 Patellofemoral pain consensus statement from the 4th International Patellofemoral Pain Research Retreat, Manchester. Part 1. Br J Sports Med. 2016;50(14):839-843](https://doi.org/10.1136/bjsports-2016-096384). Access: `full_text`. Type: `consensus_statement`.
- [Crossley KM, van Middelkoop M, Callaghan MJ, Collins NJ, Rathleff MS, Barton CJ. 2016 Patellofemoral pain consensus statement from the 4th International Patellofemoral Pain Research Retreat, Manchester. Part 2: recommended physical interventions (exercise, taping, bracing, foot orthoses and combined interventions). Br J Sports Med. 2016;50(14):844-852](https://doi.org/10.1136/bjsports-2016-096268). Access: `full_text`. Type: `consensus_statement`.
- [Vicenzino BT, Rathleff MS, Holden S, Maclachlan L, Smith BE, de Oliveira Silva D, van Middelkoop M. Developing Clinical and Research Priorities for Pain and Psychological Features in People With Patellofemoral Pain: An International Consensus Process With Health Care Professionals. J Orthop Sports Phys Ther. 2022;52(1):29-39](https://doi.org/10.2519/jospt.2022.10647). Access: `abstract_only`. Type: `consensus_statement`.

### Systematic review (34)

- [Alexander JLN, Culvenor AG, Johnston RRT, Ezzat AM, et al. Strategies to prevent and manage running-related knee injuries: a systematic review of randomised controlled trials. Br J Sports Med. 2022;56(22):1307-1319](https://doi.org/10.1136/bjsports-2022-105553). Access: `abstract_only`. Type: `systematic_review`.
- [Briani RV, Hoglund L, Waiteman MC, Arin-Bal G, et al. Bridging the Gap: A Systematic Evidence and Gap Map of Risk Factors and Preventive Strategies for Patellofemoral Pain. J Orthop Sports Phys Ther. 2026;56(2):85-97](https://doi.org/10.2519/jospt.2025.13489). Access: `abstract_only`. Type: `systematic_review`.
- [Callaghan MJ, Selfe J. Patellar taping for patellofemoral pain syndrome in adults. Cochrane Database Syst Rev. 2012;4:CD006717](https://doi.org/10.1002/14651858.CD006717.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Contreras-Mora C, Cerrolaza-Tudanca S, Amor-Salamanca MS, Mendoza-Moron PA, et al. Effectiveness of Hip Muscle Strengthening Compared with Knee Strengthening or No Intervention in Individuals with Patellofemoral Pain Syndrome: A Systematic Review. Muscles. 2026;5(3)](https://doi.org/10.3390/muscles5030066). Access: `abstract_only`. Type: `systematic_review`.
- [Culvenor AG, van Middelkoop M, Macri EM, Crossley KM. Is patellofemoral pain preventable? A systematic review and meta-analysis of randomised controlled trials. Br J Sports Med. 2020](https://doi.org/10.1136/bjsports-2020-102973). Access: `abstract_only`. Type: `systematic_review`.
- [de Oliveira NT, Lopez P, Severo-Silveira L, Almeida GPL, et al. Dose-response effect of lower limb resistance training volume on pain and function of women with patellofemoral pain: A systematic review and meta-regression. Phys Ther Sport. 2023;63:95-103](https://doi.org/10.1016/j.ptsp.2023.07.006). Access: `abstract_only`. Type: `systematic_review`.
- [de Oliveira Silva D, Pazzinatto MF, Rathleff MS, Holden S, et al. Patient Education for Patellofemoral Pain: A Systematic Review. J Orthop Sports Phys Ther. 2020;50(7):388-396](https://doi.org/10.2519/jospt.2020.9400). Access: `abstract_only`. Type: `systematic_review`.
- [Dischiavi SL, Wright AA, Tarara DT, Bleakley CM. Do exercises for patellofemoral pain reflect common injury mechanisms? A systematic review. J Sci Med Sport. 2021;24(3):229-240](https://doi.org/10.1016/j.jsams.2020.09.001). Access: `abstract_only`. Type: `systematic_review`.
- [Glaviano NR, Bazett-Jones DM, Boling MC. Pain severity during functional activities in individuals with patellofemoral pain: A systematic review with meta-analysis. J Sci Med Sport. 2022;25(5):399-406](https://doi.org/10.1016/j.jsams.2022.01.004). Access: `abstract_only`. Type: `systematic_review`.
- [Gunhamn T, Pojskic H, Augustsson SR. Knee extensor training in patients with patellofemoral pain: a systematic review and synthesis. Front Rehabil Sci. 2025;6:1641054](https://doi.org/10.3389/fresc.2025.1641054). Access: `full_text`. Type: `systematic_review`.
- [Halabi MH, Alturkistani BA, Abuhadi RH, Garout AN, et al. The Efficacy of Hip and Knee Muscles Strengthening Versus Knee Muscle Strengthening Alone in Managing Patellofemoral Pain Syndrome: A Systematic Review and Meta-Analysis. Musculoskeletal Care. 2025;23(1):e70059](https://doi.org/10.1002/msc.70059). Access: `abstract_only`. Type: `systematic_review`.
- [Hart HF, Patterson BE, Crossley KM, Culvenor AG, et al. May the force be with you: understanding how patellofemoral joint reaction force compares across different activities and physical interventions-a systematic review and meta-analysis. Br J Sports Med. 2022;56(9):521-530](https://doi.org/10.1136/bjsports-2021-104686). Access: `abstract_only`. Type: `systematic_review`.
- [Harvie D, O'Leary T, Kumar S. A systematic review of randomized controlled trials on exercise parameters in the treatment of patellofemoral pain: what works? J Multidiscip Healthc. 2011;4:383-392](https://doi.org/10.2147/JMDH.S24595). Access: `full_text`. Type: `systematic_review`.
- [Holden S, Rathleff MS, Jensen MB, Barton CJ. How can we implement exercise therapy for patellofemoral pain if we don't know what was prescribed? A systematic review. Br J Sports Med. 2018;52(6):385](https://doi.org/10.1136/bjsports-2017-097547). Access: `abstract_only`. Type: `systematic_review`.
- [Hossain M, Alexander P, Burls A, Jobanputra P. Foot orthoses for patellofemoral pain in adults. Cochrane Database Syst Rev. 2011;1:CD008402](https://doi.org/10.1002/14651858.CD008402.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Hou C, Liu Z, Wang C, Wang J, et al. Effects of blood flow restriction exercise interventions on patellofemoral pain syndrome: a systematic review and meta-analysis. Front Physiol. 2026;17:1859305](https://doi.org/10.3389/fphys.2026.1859305). Access: `abstract_only`. Type: `systematic_review`.
- [Lopes HS, Waiteman MC, Priore LB, Glaviano NR, et al. There is more to the knee joint than just the quadriceps: A systematic review with meta-analysis and evidence gap map of hamstring strength, flexibility, and morphology in individuals with gradual-onset knee disorders. J Sport Health Sci. 2024;13(4):521-536](https://doi.org/10.1016/j.jshs.2023.08.004). Access: `full_text`. Type: `systematic_review`.
- [Martimbianco ALC, Torloni MR, Andriolo BN, Porfirio GJ, et al. Neuromuscular electrical stimulation (NMES) for patellofemoral pain syndrome. Cochrane Database Syst Rev. 2017;12:CD011289](https://doi.org/10.1002/14651858.CD011289.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Nascimento LR, Teixeira-Salmela LF, Souza RB, Resende RA. Hip and Knee Strengthening Is More Effective Than Knee Strengthening Alone for Reducing Pain and Improving Activity in Individuals With Patellofemoral Pain: A Systematic Review With Meta-analysis. J Orthop Sports Phys Ther. 2018;48(1):19-31](https://doi.org/10.2519/jospt.2018.7365). Access: `abstract_only`. Type: `systematic_review`.
- [Neal BS, Bartholomew C, Barton CJ, Morrissey D, Lack SD. Six Treatments Have Positive Effects at 3 Months for People With Patellofemoral Pain: A Systematic Review With Meta-analysis. J Orthop Sports Phys Ther. 2022;52(11):750-768](https://doi.org/10.2519/jospt.2022.11359). Access: `abstract_only`. Type: `systematic_review`.
- [Neal BS, Bolgla LA, Lack SD, Bazett-Jones DM, et al. Prognosis of Patellofemoral Pain: A Systematic Review With Evidence- and Gap-Map. J Orthop Sports Phys Ther. 2025;55(10):661-670](https://doi.org/10.2519/jospt.2025.13491). Access: `abstract_only`. Type: `systematic_review`.
- [Neal BS, Lack SD, Lankhorst NE, Raye A, et al. Risk factors for patellofemoral pain: a systematic review and meta-analysis. Br J Sports Med. 2019;53(5):270-281](https://doi.org/10.1136/bjsports-2017-098890). Access: `abstract_only`. Type: `systematic_review`.
- [Nunes GS, de Oliveira J, Iacob GS, Signori LU, et al. Effectiveness of Interventions Aimed at Changing Movement Patterns in People With Patellofemoral Pain: A Systematic Review With Network Meta-analysis. J Orthop Sports Phys Ther. 2023;53(12):748-760](https://doi.org/10.2519/jospt.2023.11956). Access: `abstract_only`. Type: `systematic_review`.
- [Nunes GS, Solner MW, de Oliveira J, Wageck B, Dangui AJM. A Systematic Review with Meta-analysis of the Association between Changes in Muscle Strength and Clinical Outcome Changes in Patellofemoral Pain. Sports Med. 2026;56(11):2717-2731](https://doi.org/10.1007/s40279-026-02473-w). Access: `full_text`. Type: `systematic_review`.
- [Pazzinatto MF, Mazzella N, Souto LR, Collins NJ, et al. Is education effective for pain and function in patellofemoral pain or patellofemoral osteoarthritis? A systematic review with meta-analysis. Ann Phys Rehabil Med. 2026;69(7):102157](https://doi.org/10.1016/j.rehab.2026.102157). Access: `abstract_only`. Type: `systematic_review`.
- [Rogan S, Haehni M, Luijckx E, Dealer J, et al. Effects of Hip Abductor Muscles Exercises on Pain and Function in Patients With Patellofemoral Pain: A Systematic Review and Meta-Analysis. J Strength Cond Res. 2019;33(11):3174-3187](https://doi.org/10.1519/JSC.0000000000002658). Access: `abstract_only`. Type: `systematic_review`.
- [Saltychev M, Dutton RA, Laimi K, Beaupre GS, et al. Effectiveness of conservative treatment for patellofemoral pain syndrome: A systematic review and meta-analysis. J Rehabil Med. 2018;50(5):393-401](https://doi.org/10.2340/16501977-2295). Access: `abstract_only`. Type: `systematic_review`.
- [Smith TO, Drew BT, Meek TH, Clark AB. Knee orthoses for treating patellofemoral pain syndrome. Cochrane Database Syst Rev. 2015;12:CD010513](https://doi.org/10.1002/14651858.CD010513.pub2). Access: `abstract_only`. Type: `systematic_review`.
- [Souto LR, De Oliveira Silva D, Pazzinatto MF, Siqueira MS, et al. Are adjunct treatments effective in improving pain and function when added to exercise therapy in people with patellofemoral pain? Br J Sports Med. 2024;58(14):792-804](https://doi.org/10.1136/bjsports-2024-108145). Access: `abstract_only`. Type: `systematic_review`.
- [van der Heijden RA, Lankhorst NE, van Linschoten R, Bierma-Zeinstra SM, van Middelkoop M. Exercise for treating patellofemoral pain syndrome. Cochrane Database Syst Rev. 2015;(1):CD010387](https://doi.org/10.1002/14651858.CD010387.pub2). Access: `full_text`. Type: `systematic_review`.
- [Wallis JA, Roddy L, Bottrell J, Parslow S, Taylor NF. A Systematic Review of Clinical Practice Guidelines for Physical Therapist Management of Patellofemoral Pain. Phys Ther. 2021;101(3):pzab021](https://doi.org/10.1093/ptj/pzab021). Access: `abstract_only`. Type: `systematic_review`.
- [Wang Y, Li H, Zhang D, Zhao T, et al. Core Training for Pain Management and Functional Improvement in Patients With Patellofemoral Pain Syndrome: A Systematic Review and Meta-analysis. Am J Phys Med Rehabil. 2024;103(12):1094-1103](https://doi.org/10.1097/PHM.0000000000002513). Access: `abstract_only`. Type: `systematic_review`.
- [Winters M, Holden S, Lura CB, et al. Comparative effectiveness of treatments for patellofemoral pain: a living systematic review with network meta-analysis. Br J Sports Med. 2021;55(7):369-377](https://doi.org/10.1136/bjsports-2020-102819). Access: `full_text`. Type: `systematic_review`.
- [Young JL, Rhon DI, Cleland JA, Snodgrass SJ. The Influence of Exercise Dosing on Outcomes in Patients With Knee Disorders: A Systematic Review. J Orthop Sports Phys Ther. 2018;48(3):146-161](https://doi.org/10.2519/jospt.2018.7637). Access: `abstract_only`. Type: `systematic_review`.

### Scoping review (1)

- [Blazey P, Scott A, Ardern CL, Davis JC, Whittaker JL, Losciale JM, Khan KM. Consensus methods in patellofemoral pain: how rigorous are they? A scoping review. Br J Sports Med. 2024;58(13):733-744](https://doi.org/10.1136/bjsports-2023-107552). Access: `full_text`. Type: `scoping_review`.

### RCT (23)

- [Chen CL, Lo SF, Chiu MC, Chang CC, Hong WH. Refining Home-Based Squat Training: The Role of Hip Adduction in Improving Neuromuscular Control for Clinically Diagnosed Patellofemoral Pain Syndrome. Med Sci Monit. 2026;32:e953994](https://doi.org/10.12659/MSM.953994). Access: `full_text`. Type: `rct`.
- [Esculier JF, Bouyer LJ, Dubois B, Fremont P, et al. Is combining gait retraining or an exercise programme with education better than education alone in treating runners with patellofemoral pain? A randomised clinical trial. Br J Sports Med. 2018;52(10):659-666](https://doi.org/10.1136/bjsports-2016-096988). Access: `abstract_only`. Type: `rct`.
- [Ferber R, Bolgla L, Earl-Boehm JE, Emery C, Hamstra-Wright K. Strengthening of the Hip and Core Versus Knee Muscles for the Treatment of Patellofemoral Pain: A Multicenter Randomized Controlled Trial. J Athl Train. 2015;50(4):366-377](https://doi.org/10.4085/1062-6050-49.3.70). Access: `full_text`. Type: `rct`.
- [Fukuda TY, Melo WP, Zaffalon BM, Rossetto FM, Magalhaes E, Bryk FF, Martin RL. Hip posterolateral musculature strengthening in sedentary women with patellofemoral pain syndrome: a randomized controlled clinical trial with 1-year follow-up. J Orthop Sports Phys Ther. 2012;42(10):823-830](https://doi.org/10.2519/jospt.2012.4184). Access: `abstract_only`. Type: `rct`.
- [Giles L, Webster KE, McClelland J, Cook JL. Quadriceps strengthening with and without blood flow restriction in the treatment of patellofemoral pain: a double-blind randomised trial. Br J Sports Med. 2017;51(23):1688-1694](https://doi.org/10.1136/bjsports-2016-096329). Access: `abstract_only`. Type: `rct`.
- [Hansen R, Brushoj C, Rathleff MS, Magnusson SP, Henriksen M. Quadriceps or hip exercises for patellofemoral pain? A randomised controlled equivalence trial. Br J Sports Med. 2023;57(20):1287-1294](https://doi.org/10.1136/bjsports-2022-106197). Access: `abstract_only`. Type: `rct`.
- [Hasan S. The effect of patellar taping combined with isometric strength training on pain, muscle strength, and functional performance in patients with patellofemoral pain syndrome: a randomized comparative study. PeerJ. 2025;13:e19381](https://doi.org/10.7717/peerj.19381). Access: `full_text`. Type: `rct`.
- [Hott A, Brox JI, Pripp AH, Juel NG, Liavaag S. Patellofemoral pain: One year results of a randomized trial comparing hip exercise, knee exercise, or free activity. Scand J Med Sci Sports. 2020;30(4):741-753](https://doi.org/10.1111/sms.13613). Access: `abstract_only`. Type: `rct`.
- [Hott A, Brox JI, Pripp AH, Juel NG, Paulsen G, Liavaag S. Effectiveness of Isolated Hip Exercise, Knee Exercise, or Free Physical Activity for Patellofemoral Pain: A Randomized Controlled Trial. Am J Sports Med. 2019;47(6):1312-1322](https://doi.org/10.1177/0363546519830644). Access: `abstract_only`. Type: `rct`.
- [Jellad A, Kalai A, Guedria M, Jguirim M, et al. Combined Hip Abductor and External Rotator Strengthening and Hip Internal Rotator Stretching Improves Pain and Function in Patients With Patellofemoral Pain Syndrome: A Randomized Controlled Trial With Crossover Design. Orthop J Sports Med. 2021;9(4):2325967121989729](https://doi.org/10.1177/2325967121989729). Access: `full_text`. Type: `rct`.
- [Kamel AM, Ghuiba K, Abd Allah DS, Fayaz NA, Abdelkader NA. Effect of adding short foot exercise to hip and knee focused exercises in treatment of patients with patellofemoral pain syndrome: a randomized controlled trial. J Orthop Surg Res. 2024;19:207](https://doi.org/10.1186/s13018-024-04688-x). Access: `full_text`. Type: `rct`.
- [Kong W, Wang Z, Zhou Q, Yan X, Li J, Zhao Y, Wang X, Wang H, Ni G. Comparing the Impact of Intermittent Blood Flow Restriction Training and High-Load Resistance Training in Individuals With Patellofemoral Pain: A Randomized Controlled Trial. Arch Phys Med Rehabil. 2025;106(12):1789-1797](https://doi.org/10.1016/j.apmr.2025.06.003). Access: `abstract_only`. Type: `rct`.
- [Lee J, Kwon H, Lee Y. Low-Intensity Blood Flow-Restricted Multi-Joint Exercise Improves Muscle Function in Patients With Patellofemoral Pain Syndrome: A Randomized Trial. Med Sci Monit. 2026;32:e950516](https://doi.org/10.12659/MSM.950516). Access: `full_text`. Type: `rct`.
- [Lee JH, Jang KM, Kim E, Rhim HC, et al. Static and Dynamic Quadriceps Stretching Exercises in Patients With Patellofemoral Pain: A Randomized Controlled Trial. Sports Health. 2021;13(5):482-489](https://doi.org/10.1177/1941738121993777). Access: `abstract_only`. Type: `rct`.
- [Park JY, Lee S, Kim JM, Lee SH, et al. Digital therapeutic combining exercise and cognitive-behavioural therapy reduces pain in patients with chronic patellofemoral pain: a multicentre randomised controlled trial. Br J Sports Med. 2026;60(20):1551-1559](https://doi.org/10.1136/bjsports-2025-110863). Access: `full_text`. Type: `rct`.
- [Peng HT, Song CY. Effect of leg press training on patellar realignment in patients with patellofemoral pain. J Phys Ther Sci. 2015;27(12):3873-3878](https://doi.org/10.1589/jpts.27.3873). Access: `full_text`. Type: `rct`.
- [Rabelo NDDA, Costa LOP, Lima BM, Dos Reis AC, Bley AS, Fukuda TY, Lucareli PRG. Adding motor control training to muscle strengthening did not substantially improve the effects on clinical or kinematic outcomes in women with patellofemoral pain: A randomised controlled trial. Gait Posture. 2017;58:280-286](https://doi.org/10.1016/j.gaitpost.2017.08.018). Access: `abstract_only`. Type: `rct`.
- [Raju A, Jayaraman K, Nuhmani S, Sebastian S, Khan M, Alghadir AH. Effects of hip abductor with external rotator strengthening versus proprioceptive training on pain and functions in patients with patellofemoral pain syndrome: A randomized controlled trial. Medicine (Baltimore). 2024;103(7):e37102](https://doi.org/10.1097/MD.0000000000037102). Access: `full_text`. Type: `rct`.
- [Severo-Silveira L, Garcia E, Viana-Dias JD, Roberti LS, Serrao FV, Baroni BM. Single-set versus three-set resistance training in the management of women with patellofemoral pain: A randomized clinical trial. Braz J Phys Ther. 2026;30(4):101599](https://doi.org/10.1016/j.bjpt.2026.101599). Access: `full_text`. Type: `rct`.
- [Song CY, Lin YF, Wei TC, Lin DH, Yen TY, Jan MH. Surplus value of hip adduction in leg-press exercise in patients with patellofemoral pain syndrome: a randomized controlled trial. Phys Ther. 2009;89(5):409-418](https://doi.org/10.2522/ptj.20080195). Access: `abstract_only`. Type: `rct`.
- [Vasconcelos GS, Nunes GS, Munhoz RF, da Silva MECB, Pisani GK, Luz BC, Serrao FV. Is strength and power training targeting hip and knee muscles superior to strength training in individuals with patellofemoral pain? Proof of concept study. Sci Rep. 2024;14:27450](https://doi.org/10.1038/s41598-024-78074-2). Access: `full_text`. Type: `rct`.
- [Xiao Y, Xiong Z. Effects of foot core training combined with hip and knee strengthening on patients with patellofemoral pain: a randomized controlled trial. BMC Musculoskelet Disord. 2026;27:536](https://doi.org/10.1186/s12891-026-09914-7). Access: `full_text`. Type: `rct`.
- [Xiong Z, Zheng W, Wang H, Gao Y, Wang C. Effects of functional strength training on pain, function, and lower extremity biomechanics in patients with patellofemoral pain syndrome: a randomized clinical trial. J Orthop Surg Res. 2025;20:50](https://doi.org/10.1186/s13018-025-05482-z). Access: `full_text`. Type: `rct`.

### RCT secondary analysis (2)

- [Hansen R, Rathleff MS, Brushoj C, Magnusson SP, Henriksen M. Differential Effects of Quadriceps and Hip Muscle Exercises for Patellofemoral Pain: A Secondary Effect Modifier Analysis of a Randomized Trial. J Orthop Sports Phys Ther. 2024;54(11):732-742](https://doi.org/10.2519/jospt.2024.12503). Access: `abstract_only`. Type: `rct_secondary_analysis`.
- [Holden S, Matthews M, Rathleff MS, Kasza J, Vicenzino B. How Do Hip Exercises Improve Pain in Individuals With Patellofemoral Pain? Secondary Mediation Analysis of Strength and Psychological Factors as Mechanisms. J Orthop Sports Phys Ther. 2021;51(12):602-610](https://doi.org/10.2519/jospt.2021.10674). Access: `abstract_only`. Type: `rct_secondary_analysis`.

### Controlled trial (1)

- [Khayambashi K, Fallah A, Movahedi A, Bagwell J, Powers C. Posterolateral hip muscle strengthening versus quadriceps strengthening for patellofemoral pain: a comparative control trial. Arch Phys Med Rehabil. 2014;95(5):900-907](https://doi.org/10.1016/j.apmr.2013.12.022). Access: `abstract_only`. Type: `controlled_trial`.

### Observational (4)

- [Farrokhi S, Gorczynski S, Beisheim-Ryan E, Piva SR, Rhon DI, Willy RW, Pontillo M. Differences in content of care and outcomes between a clinical practice guideline adherent program and usual care for patellofemoral pain: A retrospective pilot study. PM R. 2025;17(5):485-495](https://doi.org/10.1002/pmrj.13298). Access: `abstract_only`. Type: `observational`.
- [Rathleff MS, Graven-Nielsen T, Holmich P, et al. Activity Modification and Load Management of Adolescents With Patellofemoral Pain: A Prospective Intervention Study Including 151 Adolescents. Am J Sports Med. 2019;47(7):1629-1637. Abstract read via Europe PMC](https://doi.org/10.1177/0363546519843915). Access: `abstract_only`. Type: `observational`.
- [Willy RW, Hoglund LT, Glaviano NR, Bolgla LA, Bazett-Jones DM. Survey of confidence and knowledge to manage patellofemoral pain in readers versus nonreaders of the physical therapy clinical practice guideline. Phys Ther Sport. 2022;55:218-228](https://doi.org/10.1016/j.ptsp.2022.04.009). Access: `abstract_only`. Type: `observational`.
- [Yang Y, Zhao Z, Zhang H, Zhou F, Liu X. Impact of stair climbing volume on patellofemoral cartilage: a dose-response analysis from the osteoarthritis initiative reveals elevated risk at middle levels. Front Med. 2025;12:1699297](https://doi.org/10.3389/fmed.2025.1699297). Access: `full_text`. Type: `observational`.

### Biomechanical (14)

- [Escamilla R, Zheng N, MacLeod TD, Imamura R, Wilk KE, Wang S, Asuncion R, Thompson IS, Aguinaldo AL, Fleisig GS. Patellofemoral Joint Loading During Bodyweight One-Legged and Two-Legged BOSU and Floor Squats. Int J Sports Phys Ther. 2025;20(2):199-209](https://doi.org/10.26603/001c.128628). Access: `full_text`. Type: `biomechanical`.
- [Escamilla R, Zheng N, MacLeod TD, Imamura R, Wilk KE, Wang S, Rubenstein I, Yamashiro K, Fleisig GS. Patellofemoral Joint Loading During the Performance of the Forward and Side Lunge with Step Height Variations. Int J Sports Phys Ther. 2022;17(2):174-184](https://doi.org/10.26603/001c.31876). Access: `full_text`. Type: `biomechanical`.
- [Escamilla RF, Fleisig GS, Zheng N, Barrentine SW, Wilk KE, Andrews JR. Biomechanics of the knee during closed kinetic chain and open kinetic chain exercises. Med Sci Sports Exerc. 1998;30(4):556-569](https://doi.org/10.1097/00005768-199804000-00014). Access: `abstract_only`. Type: `biomechanical`.
- [Escamilla RF, Zheng N, Macleod TD, Edwards WB, Hreljac A, Fleisig GS, Wilk KE, Moorman CT 3rd, Imamura R, Andrews JR. Patellofemoral joint force and stress between a short- and long-step forward lunge. J Orthop Sports Phys Ther. 2008;38(11):681-690](https://doi.org/10.2519/jospt.2008.2694). Access: `abstract_only`. Type: `biomechanical`.
- [Escamilla RF, Zheng N, Macleod TD, Edwards WB, Imamura R, Hreljac A, Fleisig GS, Wilk KE, Moorman CT 3rd, Andrews JR. Patellofemoral joint force and stress during the wall squat and one-leg squat. Med Sci Sports Exerc. 2009;41(4):879-888](https://doi.org/10.1249/MSS.0b013e31818e7ead). Access: `abstract_only`. Type: `biomechanical`.
- [Escamilla RF, Zheng N, MacLeod TD, Imamura R, Wang S, Wilk KE, Yamashiro K, Fleisig GS. Patellofemoral Joint Loading in Forward Lunge With Step Length and Height Variations. J Appl Biomech. 2022;38(4):210-220](https://doi.org/10.1123/jab.2021-0313). Access: `abstract_only`. Type: `biomechanical`.
- [Escamilla RF, Zheng N, Macleod TD, Imamura R, Wilk KE, Wang S, Yamashiro K, Escamilla IM, Fleisig GS. Patellofemoral Joint Loading during the Performance of the Wall Squat and Ball Squat with Heel-to-Wall-Distance Variations. Med Sci Sports Exerc. 2023;55(9):1592-1600](https://doi.org/10.1249/MSS.0000000000003185). Access: `abstract_only`. Type: `biomechanical`.
- [Goulette D, Griffith P, Schiller M, Rutherford D, Kernozek TW. Patellofemoral joint loading during the forward and backward lunge. Phys Ther Sport. 2021;47:178-184](https://doi.org/10.1016/j.ptsp.2020.12.001). Access: `abstract_only`. Type: `biomechanical`.
- [Hofmann CL, Holyoak DT, Juris PM. Trunk and Shank Position Influences Patellofemoral Joint Stress in the Lead and Trail Limbs During the Forward Lunge Exercise. J Orthop Sports Phys Ther. 2017;47(1):31-40](https://doi.org/10.2519/jospt.2017.6336). Access: `abstract_only`. Type: `biomechanical`.
- [Ishida T, Ueno R, Kitamura Y, Yamakawa Y, Samukawa M, Tohyama H. The Effect of Real-Time Feedback Regarding the Center-of-Pressure Position on Patellofemoral Joint Loading During Double-leg Squatting. Orthop J Sports Med. 2025;13(3):23259671251319526](https://doi.org/10.1177/23259671251319526). Access: `full_text`. Type: `biomechanical`.
- [Powers CM, Ho KY, Chen YJ, Souza RB, Farrokhi S. Patellofemoral joint stress during weight-bearing and non-weight-bearing quadriceps exercises. J Orthop Sports Phys Ther. 2014;44(5):320-327](https://doi.org/10.2519/jospt.2014.4936). Access: `abstract_only`. Type: `biomechanical`.
- [Steinkamp LA, Dillingham MF, Markel MD, Hill JA, Kaufman KR. Biomechanical considerations in patellofemoral joint rehabilitation. Am J Sports Med. 1993;21(3):438-444](https://doi.org/10.1177/036354659302100319). Access: `abstract_only`. Type: `biomechanical`.
- [Willy RW, Simon JE, Hanser B, Plemmons M, Christensen K, Klawitter L, Doutt A, Ruby BC. Females exhibit greater peak and cumulative patellofemoral joint stress with moderate and heavy load carriage compared with males. Eur J Sport Sci. 2025;25(10):e70046](https://doi.org/10.1002/ejsc.70046). Access: `full_text`. Type: `biomechanical`.
- [Zavala L, Flores V, Cotter JA, Becker J. Patellofemoral joint kinetics in females when using different depths and loads during the barbell back squat. Eur J Sport Sci. 2021;21(7):976-984](https://doi.org/10.1080/17461391.2020.1806935). Access: `abstract_only`. Type: `biomechanical`.

### RCT protocol (1)

- [Beisheim-Ryan EH, Mauntel TC, Rhon DI, et al. Evaluating the Effectiveness of Clinical Practice Guideline Adherence for Patellofemoral Pain (knEE-CAPP): Protocol for a Multisite, Parallel-Arm Randomized Clinical Trial in the Military Health System. Phys Ther. 2025;105(12):pzaf138](https://doi.org/10.1093/ptj/pzaf138). Access: `abstract_only`. Type: `rct_protocol`.

### Narrative review (1)

- [Gaitonde DY, Ericksen A, Robbins RC. Patellofemoral Pain Syndrome. Am Fam Physician. 2019;99(2):88-94](https://www.aafp.org/afp/2019/0115/p88). Access: `full_text`. Type: `narrative_review`. No DOI or PMID recorded; links to the source page.
