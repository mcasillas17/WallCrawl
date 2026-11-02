# Condition research consolidation

Source: the eight `*.json` files in this folder, after their deeper literature search and verification passes. Each condition name links to its full record, and every paper links to its DOI or PubMed page. Sources with neither link to their own page. [BIBLIOGRAPHY.md](BIBLIOGRAPHY.md) lists all 654 unique sources. The JSON files carry no demand tags, so the tag names, tag mappings and routine selections here are this consolidation's judgement. The facts behind them come only from the JSON. Catalog ids are as written in the JSON and were not checked against the catalog itself. This is general information, not treatment or diagnosis.

## 1. Scorecard

Conditions are in processing order. "Restrictions" counts `avoidOrModify` items, with `avoid` items in brackets. "Removed" counts `verification.itemsRemoved` in the latest pass. No overall grade changed in the deeper search. "Full / abstract / other" counts records by `accessLevel` in that file; "other" is registry, summary or not reconfirmed.

| Condition | Evidence grade | Restrictions | Routine candidates | Red flags | Removed | Sources | Full / abstract / other | New vocabulary |
|---|---|---:|---:|---:|---:|---:|---|---|
| [piriformis_syndrome](piriformis_syndrome.md) | very_low | 15 (2) | 9 | 9 | 5 | 79 | 32 / 44 / 3 | PROLONGED_SITTING, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, HEAVY_EXTERNAL_LOAD, SINGLE_LEG_HIP_CONTROL, CROSS_BODY_HIP_STRETCH, NERVE_TENSIONING, HAMSTRING_LENGTH_LOAD, FIGURE_FOUR_POSITION, RESISTED_HIP_EXTERNAL_ROTATION, END_RANGE_HIP_EXTENSION (11) |
| [gluteal_tendinopathy](gluteal_tendinopathy.md) | low | 12 (1) | 18 | 10 | 3 | 71 | 27 / 41 / 3 | SIDE_LYING_HIP_COMPRESSION, SIDE_SUPPORT_HOLD (2) |
| [lumbar_radiculopathy](lumbar_radiculopathy.md) | low | 12 (3) | 16 | 9 | 6 | 102 | 29 / 65 / 8 | TRUNK_FLEXION_UNDER_LOAD, LONG_LYING_SIT_UP (2) |
| [nonspecific_low_back_pain](nonspecific_low_back_pain.md) | moderate | 10 (2) | 16 | 8 | 5 | 101 | 36 / 56 / 9 | END_RANGE_SPINE_POSITION (1) |
| [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) | low | 9 (0) | 10 | 9 | 6 | 85 | 24 / 61 / 0 | THUMB_DOWN_ARM_RAISE, ARM_RAISE_ABOVE_90, BEHIND_NECK_PRESS_OR_PULL, NARROW_GRIP_BENCH (4) |
| [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) | low | 9 (0) | 5 | 7 | 4 | 66 | 25 / 41 / 0 | WRIST_EXTENSOR_WORK, FOREARM_TWIST_OR_PRONATED_LIFT, HAND_GRIP_LOAD (3) |
| [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) | very_low | 7 (0) | 7 | 7 | 0 | 86 | 25 / 55 / 6 | WRIST_END_RANGE_LOAD (1) |
| [patellofemoral_pain](patellofemoral_pain.md) | low | 9 (0) | 24 | 9 | 2 | 92 | 32 / 60 / 0 | DEEP_KNEE_FLEXION_LOAD, END_RANGE_KNEE_EXTENSION_LOAD (2) |
| **Total** | | **83 (8)** | **105** | **68** | **31** | **682** | | **26** |

**How much was actually read.** After deduplication there are 654 unique sources: 221 `full_text`, 408 `abstract_only`, 22 `registry_or_summary` and 3 `not_reconfirmed`. So most sources were read only as abstracts, and the files say `full_text` often means targeted extraction. All are listed with links in [BIBLIOGRAPHY.md](BIBLIOGRAPHY.md).

**The moderate grade is narrow.** For [nonspecific_low_back_pain](nonspecific_low_back_pain.md) it covers only short-term pain versus no treatment, usual care or placebo in chronic pain ([Hayden 2021](https://doi.org/10.1002/14651858.CD009790.pub2)), and [Belavy 2026](https://doi.org/10.1136/bmjmed-2025-001908) rates comparisons low to very low. [lumbar_radiculopathy](lumbar_radiculopathy.md) now grades by question: stay-active versus bed rest moderate, supervised neural mobilisation very low, self-directed neural mobilisation and specific gym-lift restrictions not established.

What the latest verification removed:

- **[piriformis_syndrome](piriformis_syndrome.md):** five wording or citation errors, such as [Vij 2021](https://doi.org/10.5812/aapm.112825) as a source for a 3-month reassessment flag and a garbled [Dede 2026](https://doi.org/10.5606/tftrd.2026.16718) table value.
- **[gluteal_tendinopathy](gluteal_tendinopathy.md):** one contradiction row and two citations that did not support their item, [Fearon 2024](https://doi.org/10.1136/bjsports-2023-107150) and [Grimaldi 2026](https://doi.org/10.1016/j.bjpt.2026.101583).
- **[lumbar_radiculopathy](lumbar_radiculopathy.md):** a restriction on neural mobilisation to the pain threshold or before strength work, because [Hitier 2026](https://doi.org/10.1007/s00421-026-06320-8) did not support it. Also unsupported claims about [Swinton 2011](https://doi.org/10.1519/JSC.0b013e3181e73f87) and [Shacklock 2023](https://doi.org/10.1080/09593985.2022.2037797), a trap bar load instruction that belongs to the kettlebell version, and two citations.
- **[nonspecific_low_back_pain](nonspecific_low_back_pain.md):** a citation on heavy loading whose abstract did not support the item, two wrong statements about [Park 2026](https://doi.org/10.3390/healthcare14162550), a misattached low-quality label on [Owen 2020](https://doi.org/10.1136/bjsports-2019-100886), and a rest figure wrongly credited to [Michaelson 2016](https://doi.org/10.2340/16501977-2091).
- **[rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md):** six unsupported qualifiers or inferences, including "or a cardiovascular cause" against [Lewis 2016](https://doi.org/10.1016/j.math.2016.03.009) and "using a dumbbell" against [Holmgren 2012](https://doi.org/10.1136/bmj.e787).
- **[lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md):** four items, including the "hard or sustained" qualifier on gripping, since no source compares grip intensity beyond 15% and 30% of maximum in 11 people ([Manickaraj 2018](https://pubmed.ncbi.nlm.nih.gov/30179209/)).
- **[carpal_tunnel_syndrome](carpal_tunnel_syndrome.md):** nothing removed in this pass.
- **[patellofemoral_pain](patellofemoral_pain.md):** a wall-squat heel cue and a claim that sitting is "low load".

Items an earlier pass removed and the deeper search restored, now with a dose or narrower wording: the supine isometric hold in [gluteal_tendinopathy](gluteal_tendinopathy.md); bird dog and glute bridge in [nonspecific_low_back_pain](nonspecific_low_back_pain.md); the wrist extensor stretch and a narrower gripping restriction in [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md).

## 2. MovementDemand vocabulary

There are 26 values, up from 16 before the deeper search. Tag an exercise with a value when its normal performance meets the definition. Nothing here is a score. The JSON files carry no tags, so the values and mappings are this consolidation's judgement.

| Value | Definition | Conditions restricting it |
|---|---|---|
| PROLONGED_SITTING | The exercise keeps the person seated throughout, including cross-legged or slumped sitting. | [piriformis_syndrome](piriformis_syndrome.md), [patellofemoral_pain](patellofemoral_pain.md) |
| SUSTAINED_LOCOMOTION | The exercise is continuous running, jogging, walking, cycling, or hill or stair climbing. | [piriformis_syndrome](piriformis_syndrome.md), [gluteal_tendinopathy](gluteal_tendinopathy.md), [lumbar_radiculopathy](lumbar_radiculopathy.md), [patellofemoral_pain](patellofemoral_pain.md) |
| PLYOMETRIC_IMPACT | The exercise includes jumps, skipping or other rapid stretch-shortening of the legs. | [piriformis_syndrome](piriformis_syndrome.md), [gluteal_tendinopathy](gluteal_tendinopathy.md), [lumbar_radiculopathy](lumbar_radiculopathy.md), [patellofemoral_pain](patellofemoral_pain.md) |
| HEAVY_EXTERNAL_LOAD | The exercise is programmed with a heavy barbell, dumbbell, machine, band or carried load. Never apply it to bodyweight-only work. | [piriformis_syndrome](piriformis_syndrome.md), [gluteal_tendinopathy](gluteal_tendinopathy.md), [lumbar_radiculopathy](lumbar_radiculopathy.md), [nonspecific_low_back_pain](nonspecific_low_back_pain.md), [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md), [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) |
| SINGLE_LEG_HIP_CONTROL | One leg carries most of the body weight (single-leg squat, step-down, balance hold), or the loaded hip moves toward or across the midline. | [piriformis_syndrome](piriformis_syndrome.md), [gluteal_tendinopathy](gluteal_tendinopathy.md) |
| CROSS_BODY_HIP_STRETCH | A stretch pulls the hip into adduction, with or without flexion and rotation, such as a knee drawn across the body or an iliotibial band stretch. | [piriformis_syndrome](piriformis_syndrome.md), [gluteal_tendinopathy](gluteal_tendinopathy.md) |
| NERVE_TENSIONING | A drill or held position lengthens a whole nerve path at once. Rhythmic sliders do not get this tag. | [piriformis_syndrome](piriformis_syndrome.md), [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) |
| HAMSTRING_LENGTH_LOAD | The exercise is a hamstring stretch, a sustained forward trunk bend, a hip hinge with the ankle dorsiflexed, or repeated eccentric hamstring loading. | [piriformis_syndrome](piriformis_syndrome.md), [lumbar_radiculopathy](lumbar_radiculopathy.md) |
| FIGURE_FOUR_POSITION | A stretch or hold with the ankle resting over the opposite knee, or the heel at the opposite knee. | [piriformis_syndrome](piriformis_syndrome.md) |
| RESISTED_HIP_EXTERNAL_ROTATION | Hip external rotation or abduction works hard against resistance or body weight, such as a single-leg bridge, prone heel squeeze or clamshell. | [piriformis_syndrome](piriformis_syndrome.md) |
| END_RANGE_HIP_EXTENSION | The exercise takes the hip to terminal extension, or uses long strides. | [piriformis_syndrome](piriformis_syndrome.md) |
| SIDE_LYING_HIP_COMPRESSION | The exercise is done lying on one side, with body weight on the lower hip and the upper hip able to drop into adduction. Clamshells get this tag. | [gluteal_tendinopathy](gluteal_tendinopathy.md) |
| SIDE_SUPPORT_HOLD | A side plank or variation holds the body up on one side. | [gluteal_tendinopathy](gluteal_tendinopathy.md) |
| TRUNK_FLEXION_UNDER_LOAD | The lower back rounds while lifting or holding an external load. Unloaded curl-ups and cat-cow do not get this tag. | [lumbar_radiculopathy](lumbar_radiculopathy.md), [nonspecific_low_back_pain](nonspecific_low_back_pain.md) |
| LONG_LYING_SIT_UP | A sit-up or curl-up is done with the legs flat rather than hook-lying. | [lumbar_radiculopathy](lumbar_radiculopathy.md) |
| END_RANGE_SPINE_POSITION | A Pilates or yoga movement takes the spine to end-range flexion or extension. Whether cat-cow qualifies is undecided (see section 8). | [nonspecific_low_back_pain](nonspecific_low_back_pain.md) |
| THUMB_DOWN_ARM_RAISE | An empty-can raise: the arm rises with the thumb pointing down. | [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) |
| ARM_RAISE_ABOVE_90 | A lateral raise or upright row goes above 90 degrees. | [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) |
| BEHIND_NECK_PRESS_OR_PULL | A press or pulldown goes behind the neck, or an overhead press ends in the full "high-five" position. | [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) |
| NARROW_GRIP_BENCH | A bench press uses a narrow (1 bi-acromial width) grip or elbows at 45 degrees of abduction. | [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) |
| WRIST_EXTENSOR_WORK | The wrist extensors work against resistance, or the wrist or elbow repeatedly flexes and extends as the main task. | [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) |
| FOREARM_TWIST_OR_PRONATED_LIFT | The forearm repeatedly twists, or a load is lifted palm-down. | [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) |
| HAND_GRIP_LOAD | Gripping or pinching is a main demand of the exercise, including isometric gripping and fingertip loading, or the hand holds a vibrating tool or grip. | [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md), [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) |
| WRIST_END_RANGE_LOAD | The wrist holds or bears load near end-range extension or flexion, as in flat-palm push-ups, planks and front-rack holds. | [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) |
| DEEP_KNEE_FLEXION_LOAD | A weight-bearing squat, lunge or wall squat goes deeper than about 45 degrees of knee flexion. | [patellofemoral_pain](patellofemoral_pain.md) |
| END_RANGE_KNEE_EXTENSION_LOAD | Resisted seated knee extension works the arc from about 45 to 0 degrees. | [patellofemoral_pain](patellofemoral_pain.md) |

### Approved vocabulary and tag test

The owner approved 21 values on 2026-11-01. THUMB_DOWN_ARM_RAISE, ARM_RAISE_ABOVE_90, BEHIND_NECK_PRESS_OR_PULL, NARROW_GRIP_BENCH and FIGURE_FOUR_POSITION become per-exercise cues.

Two independent agents tagged a fixed random 40 of the 302 catalog exercises. They matched exactly on 34 of 40, with an overall set overlap of 0.87. Both were the same model, so this likely overstates what two people would agree on, and 40 exercises leave some tags with one or two uses. Five of the six disagreements came from PROLONGED_SITTING and END_RANGE_SPINE_POSITION, and the sixth from END_RANGE_HIP_EXTENSION. Both agents also said HEAVY_EXTERNAL_LOAD had no real threshold.

These rules refine the definitions above for authoring:

- **PROLONGED_SITTING:** continuous seated work or seated holds only. Machine seats used for ordinary sets do not count, and neither does cycling, which is SUSTAINED_LOCOMOTION.
- **END_RANGE_SPINE_POSITION:** cat-cow does not get this tag. It is in the lumbar radiculopathy routine, and the source warns about end-range Pilates and yoga positions, not this movement.
- **END_RANGE_HIP_EXTENSION:** active or loaded work only, not stretches.
- **HEAVY_EXTERNAL_LOAD:** barbell, Smith machine, landmine and machine exercises. The catalog holds no load data, so this is equipment standing in for load. Dumbbell, cable and band work do not get it, even though a heavy dumbbell is heavy.
- **SINGLE_LEG_HIP_CONTROL:** includes split squats and alternating single-leg work. Lateral band walks do not count.

## 3. Condition restrictions

The tag applies only where its condition's item says so. 75 of the 83 items are `modify`, and `avoid` is marked below. 29 of the 83 items are not movement properties, so they are listed as rules and need another mechanism, such as a symptom prompt, a safety gate or education. The "Basis" column gives each file's `evidenceStrength`. "Expert" and "Biomech." mark evidence that was only expert opinion or biomechanical. Sources are the item's own `sourceIds`.

### [piriformis_syndrome](piriformis_syndrome.md) (12 expert, 1 observational, 1 biomech., 1 RCT)

| Restriction | Demand | Basis | Sources |
|---|---|---|---|
| Prolonged sitting | PROLONGED_SITTING | Expert | [Guner 2023](https://doi.org/10.7759/cureus.43804), [Vij 2021](https://doi.org/10.5812/aapm.112825), [Sharma 2023](https://doi.org/10.5371/hp.2023.35.1.1), [NHS sciatica](https://www.nhs.uk/conditions/sciatica/), [Kizaki 2020](https://doi.org/10.1007/s00167-020-05966-x), [Hopayian 2018](https://doi.org/10.1007/s00590-017-2031-8), [Yoon 2025](https://doi.org/10.3390/diagnostics15192531), [Külcü 2024](https://doi.org/10.5606/tftrd.2024.14668), [NHS Lothian 2025](https://policyonline.nhslothian.scot/wp-content/uploads/2025/02/Piriformis-Syndrome.pdf) |
| Cross-legged sitting held long (avoid) | PROLONGED_SITTING | Expert | [Guner 2023](https://doi.org/10.7759/cureus.43804), [Külcü 2024](https://doi.org/10.5606/tftrd.2024.14668) |
| Slumped sacral sitting (avoid) | PROLONGED_SITTING | Expert | [Guner 2023](https://doi.org/10.7759/cureus.43804) |
| High-volume running, walking, cycling, jumping when provoking | SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT | Expert | [Guner 2023](https://doi.org/10.7759/cureus.43804), [Siraj 2022](https://doi.org/10.7759/cureus.32952), [Külcü 2024](https://doi.org/10.5606/tftrd.2024.14668), [NHS sciatica](https://www.nhs.uk/conditions/sciatica/), [Vij 2021](https://doi.org/10.5812/aapm.112825), [Park 2020](https://doi.org/10.1302/0301-620X.102B5.BJJ-2019-1212.R1), [Monteleone 2025](https://doi.org/10.1186/s12893-025-03202-2), [Dahm 2010](https://doi.org/10.1002/14651858.CD007612.pub2), [Shiri 2016](https://doi.org/10.1002/ejp.885) |
| Heavy lifting | HEAVY_EXTERNAL_LOAD | Expert | [Guner 2023](https://doi.org/10.7759/cureus.43804), [Neal 2026](https://doi.org/10.3390/sports14030095) |
| Inward knee collapse in single-leg tasks | SINGLE_LEG_HIP_CONTROL | Observational | [Tonley 2010](https://doi.org/10.2519/jospt.2010.3108), [Maniar 2018](https://doi.org/10.1038/s41598-017-19098-9) |
| Forceful end-range hip stretching that reproduces symptoms | CROSS_BODY_HIP_STRETCH | Expert | [Siraj 2022](https://doi.org/10.7759/cureus.32952), [Sharma 2023](https://doi.org/10.5371/hp.2023.35.1.1), [Tonley 2010](https://doi.org/10.2519/jospt.2010.3108), [Itsuda 2024](https://doi.org/10.1123/jsr.2023-0240), [WFNS 2024](https://doi.org/10.1016/j.wnsx.2024.100274), [Hopayian 2018](https://doi.org/10.1007/s00590-017-2031-8), [Martin 2014](https://doi.org/10.1007/s00167-013-2758-7), [Michel 2013b](https://doi.org/10.1016/j.rehab.2013.03.006), [Yoon 2025](https://doi.org/10.3390/diagnostics15192531), [Neal 2026](https://doi.org/10.3390/sports14030095), [Dey 2026](https://doi.org/10.1186/s13063-026-09873-3) |
| Sciatic nerve tensioning drills | NERVE_TENSIONING | Expert | [Siraj 2022](https://doi.org/10.7759/cureus.32952) |
| Forward flexion, hamstring stretching and eccentric hamstring work with pain near the sit bone (from a related condition) | HAMSTRING_LENGTH_LOAD | Expert | [Külcü 2024](https://doi.org/10.5606/tftrd.2024.14668) |
| Figure-four or heel-to-knee position when holding it reproduces symptoms | FIGURE_FOUR_POSITION | Expert | [Michel 2013b](https://doi.org/10.1016/j.rehab.2013.03.006), [Neal 2026](https://doi.org/10.3390/sports14030095), [Tonley 2010](https://doi.org/10.2519/jospt.2010.3108), [Shamsi 2024a](https://doi.org/10.1186/s13102-023-00802-4), [McGovern 2017](https://doi.org/10.26603/ijspt20171068) |
| Squatting and resistance exercise when it reproduces symptoms | Rule: symptom response. The file says nothing supports a ban or load limit. | Expert | [Külcü 2024](https://doi.org/10.5606/tftrd.2024.14668), [Neal 2026](https://doi.org/10.3390/sports14030095), [Meinders 2022a](https://doi.org/10.1016/j.jbiomech.2022.111019), [Meinders 2022b](https://doi.org/10.1016/j.jbiomech.2022.111220) |
| Hard resisted hip external rotation and abduction when contraction reproduces symptoms | RESISTED_HIP_EXTERNAL_ROTATION | Biomech. | [Martin 2014](https://doi.org/10.1007/s00167-013-2758-7), [Giphart 2012](https://doi.org/10.1177/0363546512443812), [Kim 2026](https://doi.org/10.1177/19417381251372979) |
| Prolonged bed rest or complete inactivity | Rule: stay-active education | Expert | [Vij 2021](https://doi.org/10.5812/aapm.112825), [Dahm 2010](https://doi.org/10.1002/14651858.CD007612.pub2), [Park 2020](https://doi.org/10.1302/0301-620X.102B5.BJJ-2019-1212.R1), [NHS Lothian 2025](https://policyonline.nhslothian.scot/wp-content/uploads/2025/02/Piriformis-Syndrome.pdf) |
| Foam-roller or other compression on the tender point | Rule: education. Not an exercise demand. | RCT | [Shamsi 2024a](https://doi.org/10.1186/s13102-023-00802-4), [Shamsi 2024b](https://doi.org/10.1016/j.jbmt.2024.03.073), [Vij 2021](https://doi.org/10.5812/aapm.112825) |
| Long strides and terminal hip extension when provoking (from a related condition) | END_RANGE_HIP_EXTENSION | Expert | [Külcü 2024](https://doi.org/10.5606/tftrd.2024.14668), [Park 2020](https://doi.org/10.1302/0301-620X.102B5.BJJ-2019-1212.R1) |

### [gluteal_tendinopathy](gluteal_tendinopathy.md) (11 expert, 1 biomech.)

| Restriction | Demand | Basis | Sources |
|---|---|---|---|
| Stretches pulling the hip into adduction (avoid) | CROSS_BODY_HIP_STRETCH | Expert | [Grimaldi and Fearon 2015](https://doi.org/10.2519/jospt.2015.5829), [LEAP protocol 2016](https://doi.org/10.1186/s12891-016-1043-6), [NHS Lothian 2025](https://apps.nhslothian.scot/files/sites/2/GTPS-FACT-SHEET-for-Ref-help-1.pdf), [ISHA 2023](https://doi.org/10.1093/jhps/hnac050), [Challoumas 2023](https://doi.org/10.1186/s40798-023-00616-1), [Grimaldi 2024](https://doi.org/10.1093/rap/rkae022), [Nava 2025](https://doi.org/10.1016/j.jsams.2025.04.012), [Nava protocol 2022](https://doi.org/10.1371/journal.pone.0269230), [Rompe 2009](https://doi.org/10.1177/0363546509334374), [French 2020](https://doi.org/10.1016/j.physio.2019.05.002) |
| Loaded exercise across the midline, or pelvic drop | SINGLE_LEG_HIP_CONTROL | Expert | [Grimaldi and Fearon 2015](https://doi.org/10.2519/jospt.2015.5829), [Grimaldi 2015](https://doi.org/10.1007/s40279-015-0336-5), [LEAP protocol 2016](https://doi.org/10.1186/s12891-016-1043-6), [Grimaldi 2024](https://doi.org/10.1093/rap/rkae022) |
| Heavy single-leg squats and lifts | SINGLE_LEG_HIP_CONTROL, HEAVY_EXTERNAL_LOAD | Expert | [Grimaldi and Fearon 2015](https://doi.org/10.2519/jospt.2015.5829), [LEAP protocol 2016](https://doi.org/10.1186/s12891-016-1043-6), [Collings 2023](https://doi.org/10.1249/MSS.0000000000003091) |
| Lying on the painful side | SIDE_LYING_HIP_COMPRESSION | Expert | [Grimaldi and Fearon 2015](https://doi.org/10.2519/jospt.2015.5829), [ISHA 2023](https://doi.org/10.1093/jhps/hnac050), [Grimaldi 2024](https://doi.org/10.1093/rap/rkae022), [Hogsholt 2026](https://doi.org/10.1136/bmjopen-2026-120837) |
| Clamshell | SIDE_LYING_HIP_COMPRESSION | Expert | [NHS Lothian 2025](https://apps.nhslothian.scot/files/sites/2/GTPS-FACT-SHEET-for-Ref-help-1.pdf), [Moore 2019](https://doi.org/10.1123/jsr.2018-0340), [Nava protocol 2022](https://doi.org/10.1371/journal.pone.0269230), [Gonzalez-de-la-Flor 2025](https://doi.org/10.3390/jfmk10030294) |
| Long, tempo or hill running, plyometrics | SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT | Expert | [Grimaldi and Fearon 2015](https://doi.org/10.2519/jospt.2015.5829) |
| Hill walking, stairs, long walks | SUSTAINED_LOCOMOTION | Expert | [Grimaldi and Fearon 2015](https://doi.org/10.2519/jospt.2015.5829), [Allison 2016](https://doi.org/10.1016/j.clinbiomech.2016.10.003), [Allison 2018](https://doi.org/10.1016/j.gaitpost.2018.02.011) |
| Prolonged single-leg balancing | SINGLE_LEG_HIP_CONTROL | Expert | [ISHA 2023](https://doi.org/10.1093/jhps/hnac050), [Almousa 2023](https://doi.org/10.12688/hrbopenres.13796.2), [Nava protocol 2022](https://doi.org/10.1371/journal.pone.0269230), [Grimaldi 2024](https://doi.org/10.1093/rap/rkae022) |
| Abductor strengthening progressed quickly or through significant pain | Rule: progression and pain limit | Expert | [LEAP protocol 2016](https://doi.org/10.1186/s12891-016-1043-6), [Clifford 2019](https://doi.org/10.1136/bmjsem-2019-000558), [Grimaldi and Fearon 2015](https://doi.org/10.2519/jospt.2015.5829), [Almousa 2023](https://doi.org/10.12688/hrbopenres.13796.2), [Escriche-Escuder 2020](https://doi.org/10.1136/bmjopen-2020-041433), [Gonzalez-de-la-Flor 2025](https://doi.org/10.3390/jfmk10030294) |
| Crossed legs or hip hanging into adduction when sitting or standing | Rule: posture education, including during rests | Expert | [ISHA 2023](https://doi.org/10.1093/jhps/hnac050), [Grimaldi 2024](https://doi.org/10.1093/rap/rkae022), [Almousa 2023](https://doi.org/10.12688/hrbopenres.13796.2), [Hogsholt 2026](https://doi.org/10.1136/bmjopen-2026-120837) |
| Side plank as a beginner core exercise | SIDE_SUPPORT_HOLD | Biomech. | [Collings 2023](https://doi.org/10.1249/MSS.0000000000003091) |
| Hard loading soon after a corticosteroid or platelet-rich plasma injection | Rule: post-procedure gate | Expert | [ISHA 2023](https://doi.org/10.1093/jhps/hnac050) |

### [lumbar_radiculopathy](lumbar_radiculopathy.md) (5 biomech., 4 guideline, 2 expert, 1 systematic review)

The earlier version of this document said this condition restricts no demand tag. It now restricts several, all on biomechanical inference. The file says no study tested gym lifts, hamstring stretching, sitting limits or Valsalva in symptomatic people, and that this absence is not evidence those lifts are safe.

| Restriction | Demand | Basis | Sources |
|---|---|---|---|
| Any activity during which leg pain increases | Rule: symptom response | Expert | [Albert 2012](https://doi.org/10.1097/BRS.0b013e31821ace7f), [Fritz 2021](https://doi.org/10.7326/M20-4187), [Albert 2012](https://doi.org/10.1007/s00586-011-2018-9) |
| Replacing normal activity with extended bed rest | Rule: stay-active education | Guideline | [NICE NG59](https://www.nice.org.uk/guidance/ng59/chapter/Recommendations), [Dahm 2010](https://doi.org/10.1002/14651858.CD007612.pub2), [Vroomen 1999](https://doi.org/10.1056/NEJM199902113400602), [Hofstee 2002](https://doi.org/10.3171/spi.2002.96.1.0045) |
| Neural mobilisation with cauda equina lesions, cord signs or nervous system pathology (avoid) | Rule: safety gate | Systematic review | [Lin 2023](https://doi.org/10.3390/life13122255), [Peacock 2023](https://doi.org/10.1080/10669817.2022.2065599), [Danazumi 2023](https://doi.org/10.1080/10669817.2023.2192975) |
| Fixed-direction repeated lumbar flexion or extension for everyone | Rule: direction follows symptom response, no blanket ban | Guideline | [George 2021](https://doi.org/10.2519/jospt.2021.0304), [Delitto 2012](https://doi.org/10.2519/jospt.2012.42.4.A1), [Fritz 2021](https://doi.org/10.7326/M20-4187), [Shacklock 2023](https://doi.org/10.1080/09593985.2022.2037797), [VanGelder 2013](https://pubmed.ncbi.nlm.nih.gov/24175134/), [Albert 2012](https://doi.org/10.1007/s00586-011-2018-9) |
| Mechanical traction (avoid) | Rule: clinical treatment. The sources do not assess gym hanging. | Guideline | [NICE NG59](https://www.nice.org.uk/guidance/ng59/chapter/Recommendations), [George 2021](https://doi.org/10.2519/jospt.2021.0304), [Wegner 2013](https://doi.org/10.1002/14651858.CD003010.pub5), [Thackeray 2016](https://doi.org/10.2519/jospt.2016.6238), [Kreiner 2014](https://doi.org/10.1016/j.spinee.2013.08.003), [Vanti 2021](https://doi.org/10.1093/ptj/pzaa231), [Pangarkar 2019](https://doi.org/10.1007/s11606-019-05086-4), [Delitto 2012](https://doi.org/10.2519/jospt.2012.42.4.A1), [WHO news 2023](https://www.who.int/news/item/07-12-2023-who-releases-guidelines-on-chronic-low-back-pain) |
| Neural mobilisation as the first step in the acute stage | Rule: stage gate | Expert | [Thoomes 2023](https://doi.org/10.1080/09638288.2022.2130448), [George 2021](https://doi.org/10.2519/jospt.2021.0304) |
| Loaded or sustained lumbar flexion, especially round-back straight-knee lifting | TRUNK_FLEXION_UNDER_LOAD | Biomech. | [Wilke 1999](https://doi.org/10.1097/00007632-199904150-00005), [Callaghan 2001](https://doi.org/10.1016/s0268-0033%2800%2900063-2), [Tampier 2007](https://doi.org/10.1097/BRS.0b013e31815b64f5), [Briar 2023](https://doi.org/10.1007/s00586-022-07518-y), [Mundt 1993](https://doi.org/10.1097/00007632-199304000-00012), [VanGelder 2013](https://pubmed.ncbi.nlm.nih.gov/24175134/), [von Arx 2021](https://doi.org/10.3389/fbioe.2021.769117), [Cherni 2026](https://doi.org/10.3390/sports14040151) |
| Heavy bilateral lifting during symptoms | HEAVY_EXTERNAL_LOAD | Biomech. | [Cholewicki 1991](https://pubmed.ncbi.nlm.nih.gov/1758295/), [Ramirez 2022](https://pubmed.ncbi.nlm.nih.gov/34875981/), [Yanagisawa 2021](https://doi.org/10.2463/mrms.mp.2020-0052), [Swinton 2011](https://doi.org/10.1519/JSC.0b013e3181e73f87), [Alshanqity 2025](https://doi.org/10.7759/cureus.100428), [Mundt 1993](https://doi.org/10.1177/036354659302100617), [VanGelder 2013](https://pubmed.ncbi.nlm.nih.gov/24175134/) |
| Leg press treated as automatically spine-sparing | Rule: education. It removes an assumption and sets no limit. | Biomech. | [Sjoberg 2023](https://doi.org/10.1519/JSC.0000000000004163) |
| Hip hinge with dorsiflexed ankle and straight-leg hamstring stretches when leg symptoms respond | HAMSTRING_LENGTH_LOAD | Biomech. | [Ellis 2021](https://doi.org/10.1016/j.ultrasmedbio.2021.05.018), [Coppieters 2006](https://doi.org/10.1002/jor.20210), [Danazumi 2023](https://doi.org/10.1080/10669817.2023.2192975) |
| Long-lying sit-ups, and jogging, skipping or trampoline jumping, when leg symptoms respond | LONG_LYING_SIT_UP, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT | Biomech. | [Johnson 1991](https://doi.org/10.1016/0268-0033%2891%2990006-C), [Rohlmann 2001](https://doi.org/10.1080/00140130120943) |
| Lumbar belts or corsets as treatment (avoid) | Rule: education. Not an exercise demand. | Guideline | [NICE NG59](https://www.nice.org.uk/guidance/ng59/chapter/Recommendations), [WHO news 2023](https://www.who.int/news/item/07-12-2023-who-releases-guidelines-on-chronic-low-back-pain), [Pangarkar 2019](https://doi.org/10.1007/s11606-019-05086-4) |

### [nonspecific_low_back_pain](nonspecific_low_back_pain.md) (4 expert, 2 systematic review, 2 RCT, 1 observational, 1 guideline)

| Restriction | Demand | Basis | Sources |
|---|---|---|---|
| Prolonged bed rest or stopping ordinary activity (avoid) | Rule: stay-active education | Systematic review | [Dahm 2010](https://doi.org/10.1002/14651858.CD007612.pub2), [Hagen 2002](https://doi.org/10.1097/00007632-200208150-00010), [NICE NG59](https://www.nice.org.uk/guidance/ng59/chapter/Recommendations), [George 2021](https://doi.org/10.2519/jospt.2021.0304) |
| Lumbar flexion while lifting, only where it provokes symptoms | TRUNK_FLEXION_UNDER_LOAD | Expert | [Saraceni 2020](https://doi.org/10.2519/jospt.2020.9218), [Swain 2020](https://doi.org/10.1016/j.jbiomech.2019.08.006), [Jahn 2023](https://doi.org/10.5271/sjweh.4114), [von Arx 2021](https://doi.org/10.3389/fbioe.2021.769117), [Nolan 2020](https://doi.org/10.1515/sjpain-2019-0089) |
| Loaded flexion with osteoporosis, long-term corticosteroid use or prior spinal fracture | TRUNK_FLEXION_UNDER_LOAD | Expert | [POGO Physio](https://www.pogophysio.com.au/?p=8582) |
| Heavy spinal loading during a flare | HEAVY_EXTERNAL_LOAD | Observational | [Michaelson 2016](https://doi.org/10.2340/16501977-2091), [Berglund 2015](https://doi.org/10.1519/JSC.0000000000000837), [Ranzani 2025](https://doi.org/10.33393/aop.2025.3533), [Cherni 2026](https://doi.org/10.3390/sports14040151) |
| Stretching as the entire programme | Rule: programme composition | Systematic review | [Owen 2020](https://doi.org/10.1136/bjsports-2019-100886), [George 2021](https://doi.org/10.2519/jospt.2021.0304), [Fernandez-Rodriguez 2022](https://doi.org/10.2519/jospt.2022.10671), [Zhang 2023](https://doi.org/10.1186/s13018-023-03988-y), [Hayden 2021](https://doi.org/10.1016/j.jphys.2021.09.004), [Gou 2024](https://doi.org/10.1177/20503121241252251) |
| Exercise that keeps worsening the pain, or progressing load while pain rises | Rule: symptom response and progression | Expert | [Michaelson 2016](https://doi.org/10.2340/16501977-2091), [Nicol 2023](https://doi.org/10.3390/jcm12041685), [Apeldoorn 2024](https://doi.org/10.23736/S1973-9087.24.08352-7), [Rodriguez-Lopez 2026](https://doi.org/10.3390/sports14100456) |
| High-intensity Pilates when low intensity is available | Rule: clinical Pilates only; intensity undefined in the abstract | RCT | [Coelho 2025](https://doi.org/10.1016/j.jphys.2025.03.002) |
| Deliberate abdominal contraction as a constant cue during Pilates | Rule: cue text | RCT | [Lunkes 2026](https://doi.org/10.1016/j.jphys.2026.03.007) |
| End-range spinal flexion or extension in Pilates or yoga | END_RANGE_SPINE_POSITION | Expert | [Gao 2026](https://doi.org/10.1038/s41413-026-00572-y), [Wieland 2022](https://doi.org/10.1002/14651858.CD010671.pub3) |
| Relying on a lumbar belt, brace or corset as treatment (avoid) | Rule: education. Not an exercise demand. | Guideline | [NICE NG59](https://www.nice.org.uk/guidance/ng59/chapter/Recommendations), [WHO 2023 summary](https://pubmed.ncbi.nlm.nih.gov/38198579/), [Steffens 2016](https://doi.org/10.1001/jamainternmed.2015.7431) |

### [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) (4 expert, 2 biomech., 1 systematic review, 1 guideline, 1 observational)

| Restriction | Demand | Basis | Sources |
|---|---|---|---|
| Load above 5/10 pain or not recovered by next session | Rule: pain monitoring, a protocol rule | Expert | [Holmgren 2012](https://doi.org/10.1136/bmj.e787), [Ullern 2025](https://doi.org/10.1186/s12891-025-08411-7), [Ingwersen 2017](https://doi.org/10.1177/2325967117723292), [Clausen 2021](https://doi.org/10.1177/03635465211016008), [Tran 2025](https://doi.org/10.2519/jospt.2025.13253), [Cavaggion 2024](https://doi.org/10.2147/OAJSM.S483272) |
| Fast, high-load, high-repetition resisted work in an irritable shoulder | HEAVY_EXTERNAL_LOAD | Expert | [Lewis 2016](https://doi.org/10.1016/j.math.2016.03.009) |
| Assuming higher load works better | Rule: progression. The file says this does not show harm. | Systematic review | [Lafrance 2024](https://doi.org/10.2519/jospt.2024.12453), [Zhang 2025](https://doi.org/10.1186/s13018-025-06514-4), [Ingwersen 2017](https://doi.org/10.1177/2325967117723292), [Clausen 2021](https://doi.org/10.1177/03635465211016008), [JOSPT 2025](https://doi.org/10.2519/jospt.2025.13182), [Malliaras 2020](https://doi.org/10.1016/j.apmr.2020.06.013) |
| Activities that reliably provoke the pain | Rule: symptom response | Guideline | [JOSPT 2025](https://doi.org/10.2519/jospt.2025.13182), [GRASP protocol 2017](https://doi.org/10.1136/bmjopen-2017-018004), [Lambers Heerspink 2026a](https://doi.org/10.2340/17453674.2026.45365) |
| Work tasks above shoulder level | Rule: work advice. The file says overhead exercise is a tolerance question, not a ban. | Expert | [Lambers Heerspink 2026a](https://doi.org/10.2340/17453674.2026.45365), [Leong 2019](https://doi.org/10.2340/16501977-2598), [Diener 2024](https://doi.org/10.1055/a-1957-6140), [Park 2020](https://doi.org/10.1038/s41598-020-76704-z), [Cavaggion 2025](https://doi.org/10.1177/10538127251334430), [JOSPT 2025](https://doi.org/10.2519/jospt.2025.13182), [BESS 2025](https://doi.org/10.1177/17585732251374282) |
| Empty-can raises | THUMB_DOWN_ARM_RAISE | Biomech. | [Timmons 2016](https://doi.org/10.1016/j.jse.2015.08.046), [Thigpen 2006](https://doi.org/10.1177/0363546505281797), [Robert-Lachaine 2015](https://doi.org/10.4085/1062-6050-50.9.06) |
| Lateral raises and upright rows above 90 degrees | ARM_RAISE_ABOVE_90 | Observational | [Kolber 2014](https://doi.org/10.1519/JSC.0000000000000250) |
| Behind-the-neck press and pulldown; full "high-five" overhead position | BEHIND_NECK_PRESS_OR_PULL | Expert | [Bonilla 2022](https://doi.org/10.3390/ijerph191912710), [Coratella 2022](https://doi.org/10.3389/fphys.2022.825880), [Padovan 2026](https://doi.org/10.5114/jhk/205466) |
| Bench press with a narrow grip or elbows at 45 degrees | NARROW_GRIP_BENCH | Biomech. | [Noteboom 2024](https://doi.org/10.3389/fphys.2024.1393235) |

### [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) (3 observational, 3 RCT, 2 biomech., 1 guideline)

| Restriction | Demand | Basis | Sources |
|---|---|---|---|
| Loaded wrist extensor work at an aggravating load | WRIST_EXTENSOR_WORK | Guideline | [Lucado 2022](https://doi.org/10.2519/jospt.2022.0302), [Park 2010](https://doi.org/10.4055/cios.2010.2.3.173), [Stasinopoulos 2022a](https://doi.org/10.3390/jcm12010094), [Bateman 2021](https://doi.org/10.1136/bmjopen-2021-053841), [Bateman 2024](https://doi.org/10.1016/j.msksp.2023.102905), [Sveinall 2024](https://doi.org/10.1136/bmjopen-2024-085916), [Ortega-Castillo 2022](https://doi.org/10.1016/j.msksp.2022.102645) |
| Repeated wrist or elbow flexion and extension | WRIST_EXTENSOR_WORK | Observational | [Lucado 2022](https://doi.org/10.2519/jospt.2022.0302), [van Rijn 2009](https://doi.org/10.1093/rheumatology/kep013) |
| Repeated forearm twisting | FOREARM_TWIST_OR_PRONATED_LIFT | Observational | [Lucado 2022](https://doi.org/10.2519/jospt.2022.0302) |
| Palm-down lifting | FOREARM_TWIST_OR_PRONATED_LIFT | Biomech. | [Lucado 2022](https://doi.org/10.2519/jospt.2022.0302), [Ikeda 2025](https://doi.org/10.1186/s13018-024-05363-x), [Mogk 2003](https://doi.org/10.1080/0014013031000107595), [Di Domizio 2010](https://doi.org/10.1080/00140130903389076) |
| Very heavy loads in the hands | HEAVY_EXTERNAL_LOAD | Observational | [Lucado 2022](https://doi.org/10.2519/jospt.2022.0302), [van Rijn 2009](https://doi.org/10.1093/rheumatology/kep013) |
| Isometric wrist extension or gripping above the pain threshold, especially for immediate relief | Rule: pain threshold, no relief promise | RCT | [Coombes 2016](https://doi.org/10.1097/AJP.0000000000000365), [Coombes 2025](https://doi.org/10.1016/j.msksp.2025.103421), [Stasinopoulos 2022a](https://doi.org/10.3390/jcm12010094), [Clifford 2020](https://doi.org/10.1136/bmjsem-2020-000760) |
| Isometric gripping that provokes lateral elbow pain | HAND_GRIP_LOAD | RCT | [Coombes 2025](https://doi.org/10.1016/j.msksp.2025.103421), [Manickaraj 2018](https://pubmed.ncbi.nlm.nih.gov/30179209/), [Mogk 2003](https://doi.org/10.1080/0014013031000107595) |
| Heavy slow resistance wrist extensor programmes as an unsupervised home plan | WRIST_EXTENSOR_WORK, HEAVY_EXTERNAL_LOAD | RCT | [Sveinall 2024](https://doi.org/10.1136/bmjopen-2024-085916), [Divya Mary 2025](https://doi.org/10.1002/hcs2.70004) |
| Fast, forceful wrist extension in extreme radial or ulnar deviation | Rule: technique cue, a lab inference | Biomech. | [Lee 2016](https://doi.org/10.1371/journal.pone.0155379) |

### [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) (4 biomech., 2 observational, 1 expert)

| Restriction | Demand | Basis | Sources |
|---|---|---|---|
| Sustained or loaded near-end-range wrist positions (the exercise examples are an inference) | WRIST_END_RANGE_LOAD | Biomech. | [Gelberman 1981](https://doi.org/10.2106/00004623-198163030-00009), [Keir 2007](https://doi.org/10.1518/001872007779598127), [Kozak 2015](https://doi.org/10.1186/s12891-015-0685-0), [OrthoInfo](https://www.orthoinfo.org/en/diseases--conditions/carpal-tunnel-syndrome/), [Keir 1998](https://doi.org/10.1016/S0363-5023%2898%2980007-5), [Li 2026](https://doi.org/10.1002/jor.70294), [Barcenilla 2012](https://doi.org/10.1093/rheumatology/ker108), [Schmid 2015](https://doi.org/10.1016/j.apergo.2014.08.020), [Vignais 2016](https://doi.org/10.1615/CritRevBiomedEng.2017021073) |
| High-volume forceful gripping | HAND_GRIP_LOAD | Observational | [Hassan 2022](https://doi.org/10.1002/hsr2.888), [Kozak 2015](https://doi.org/10.1186/s12891-015-0685-0), [Physiotutors CPG 2026](https://www.physiotutors.com/?p=29289), [NHS CTS](https://www.nhs.uk/conditions/carpal-tunnel-syndrome/), [Barcenilla 2012](https://doi.org/10.1093/rheumatology/ker108), [Gerger 2024](https://doi.org/10.1016/j.apergo.2023.104211), [Palmer 2007](https://doi.org/10.1093/occmed/kql125), [Huang 2024](https://doi.org/10.3390/diagnostics14192120), [Marriott 2023](https://doi.org/10.1097/PHM.0000000000002167) |
| Hard or sustained pinching and fingertip loading | HAND_GRIP_LOAD | Biomech. | [Rempel 1997](https://doi.org/10.1002/jor.1100150315), [Keir 1998](https://doi.org/10.1002/jor.1100160119), [Hassan 2022](https://doi.org/10.1002/hsr2.888), [McGorry 2014](https://doi.org/10.1002/jor.22571) |
| Hand-transmitted vibration | HAND_GRIP_LOAD | Observational | [Kozak 2015](https://doi.org/10.1186/s12891-015-0685-0), [Hassan 2022](https://doi.org/10.1002/hsr2.888), [NHS CTS](https://www.nhs.uk/conditions/carpal-tunnel-syndrome/), [Barcenilla 2012](https://doi.org/10.1093/rheumatology/ker108), [Nilsson 2017](https://doi.org/10.1371/journal.pone.0180795), [Palmer 2007](https://doi.org/10.1093/occmed/kql125) |
| Strong nerve tensioning | NERVE_TENSIONING | Biomech. | [Coppieters 2008](https://doi.org/10.1016/j.math.2006.12.008), [Sheereen 2022](https://doi.org/10.1155/2022/1975803), [Coppieters 2009](https://doi.org/10.2519/jospt.2009.2913), [Wolny 2019](https://doi.org/10.1177/0269215518805213) |
| Hard active gripping or pinching with the wrist bent back | HAND_GRIP_LOAD, WRIST_END_RANGE_LOAD | Biomech. | [McGorry 2014](https://doi.org/10.1002/jor.22571), [Keir 2000](https://doi.org/10.1016/s0268-0033%2899%2900092-3), [Keir 1997](https://doi.org/10.1016/S0363-5023%2897%2980119-0), [Vignais 2016](https://doi.org/10.1615/CritRevBiomedEng.2017021073) |
| After release only: repeated loading, lifting over 10 kg, vibrating tools during early recovery | Rule: post-procedure gate | Expert | [Newington 2022](https://doi.org/10.1177/17589983221113870), [Shapiro 2025](https://doi.org/10.5435/JAAOS-D-24-01179) |

### [patellofemoral_pain](patellofemoral_pain.md) (6 biomech., 1 observational, 1 guideline, 1 expert)

| Restriction | Demand | Basis | Sources |
|---|---|---|---|
| Squatting beyond about 45 degrees | DEEP_KNEE_FLEXION_LOAD | Biomech. | [Powers 2014](https://doi.org/10.2519/jospt.2014.4936), [Bolgla 2018](https://doi.org/10.4085/1062-6050-231-15), [Zavala 2021](https://doi.org/10.1080/17461391.2020.1806935), [Escamilla 1998](https://doi.org/10.1097/00005768-199804000-00014), [Severo-Silveira 2026](https://doi.org/10.1016/j.bjpt.2026.101599), [Ferber 2015](https://doi.org/10.4085/1062-6050-49.3.70) |
| Resisted seated knee extension from about 45 to 0 degrees | END_RANGE_KNEE_EXTENSION_LOAD | Biomech. | [Powers 2014](https://doi.org/10.2519/jospt.2014.4936), [Steinkamp 1993](https://doi.org/10.1177/036354659302100319), [Bolgla 2018](https://doi.org/10.4085/1062-6050-231-15), [Cochrane 2015](https://doi.org/10.1002/14651858.CD010387.pub2), [Severo-Silveira 2026](https://doi.org/10.1016/j.bjpt.2026.101599) |
| Activities that reproduce the pain | DEEP_KNEE_FLEXION_LOAD, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, PROLONGED_SITTING | Observational | [Gaitonde 2019](https://www.aafp.org/afp/2019/0115/p88), [Marra 2020](https://www.aafp.org/pubs/afp/issues/2020/1001/p442.html), [Rathleff 2019](https://doi.org/10.1177/0363546519843915), [Willy 2019](https://doi.org/10.2519/jospt.2019.0302), [Glaviano 2022](https://doi.org/10.1016/j.jsams.2022.01.004), [Hart 2022](https://doi.org/10.1136/bjsports-2021-104686), [Yang 2025](https://doi.org/10.3389/fmed.2025.1699297) |
| High running load | SUSTAINED_LOCOMOTION | Guideline | [Marra 2020](https://www.aafp.org/pubs/afp/issues/2020/1001/p442.html), [Connell 2020](https://pubmed.ncbi.nlm.nih.gov/33487639/), [Collins 2018](https://doi.org/10.1136/bjsports-2018-099397), [Hart 2022](https://doi.org/10.1136/bjsports-2021-104686), [Esculier 2018](https://doi.org/10.1136/bjsports-2016-096988), [Alexander 2022](https://doi.org/10.1136/bjsports-2022-105553), [Culvenor 2020](https://doi.org/10.1136/bjsports-2020-102973) |
| Deep forward or side lunges, especially on a short step | DEEP_KNEE_FLEXION_LOAD | Biomech. | [Goulette 2021](https://doi.org/10.1016/j.ptsp.2020.12.001), [Escamilla 2022a](https://doi.org/10.26603/001c.31876), [Escamilla 2022b](https://doi.org/10.1123/jab.2021-0313), [Escamilla 2008](https://doi.org/10.2519/jospt.2008.2694), [Hofmann 2017](https://doi.org/10.2519/jospt.2017.6336) |
| One-legged squats taken deep | DEEP_KNEE_FLEXION_LOAD | Biomech. | [Escamilla 2025](https://doi.org/10.26603/001c.128628), [Escamilla 2009](https://doi.org/10.1249/MSS.0b013e31818e7ead) |
| Deep wall squats with the heels close to the wall | DEEP_KNEE_FLEXION_LOAD | Biomech. | [Escamilla 2023](https://doi.org/10.1249/MSS.0000000000003185), [Escamilla 2009](https://doi.org/10.1249/MSS.0b013e31818e7ead) |
| Raising external load quickly in squats or carried-load walking | Rule: progression | Biomech. | [Zavala 2021](https://doi.org/10.1080/17461391.2020.1806935), [Willy 2025](https://doi.org/10.1002/ejsc.70046) |
| Exercising through more than mild knee pain | Rule: pain limit; no validated threshold | Expert | [Bolgla 2018](https://doi.org/10.4085/1062-6050-231-15), [Vasconcelos 2024](https://doi.org/10.1038/s41598-024-78074-2), [Ophey 2025](https://doi.org/10.1002/ksa.12367), [Jellad 2021](https://doi.org/10.1177/2325967121989729), [Severo-Silveira 2026](https://doi.org/10.1016/j.bjpt.2026.101599) |

## 4. Joint label mapping

The six labels are self-reported body regions, not diagnoses. Nobody selecting one has been shown to have the conditions below. A label resting on one condition takes that condition's tags. A label resting on two takes only the tags both restrict, which is a change from the earlier union (see section 8). A label's person may have a different problem, and condition red flags and rules do not transfer automatically.

| Label | Derived from | Restricted demands |
|---|---|---|
| SHOULDER_SENSITIVE | [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) | HEAVY_EXTERNAL_LOAD, THUMB_DOWN_ARM_RAISE, ARM_RAISE_ABOVE_90, BEHIND_NECK_PRESS_OR_PULL, NARROW_GRIP_BENCH |
| ELBOW_SENSITIVE | [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) | HEAVY_EXTERNAL_LOAD, WRIST_EXTENSOR_WORK, FOREARM_TWIST_OR_PRONATED_LIFT, HAND_GRIP_LOAD |
| WRIST_SENSITIVE | [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) | NERVE_TENSIONING, HAND_GRIP_LOAD, WRIST_END_RANGE_LOAD |
| LOWER_BACK_SENSITIVE | [lumbar_radiculopathy](lumbar_radiculopathy.md), [nonspecific_low_back_pain](nonspecific_low_back_pain.md) (shared) | HEAVY_EXTERNAL_LOAD, TRUNK_FLEXION_UNDER_LOAD |
| HIP_SENSITIVE | [piriformis_syndrome](piriformis_syndrome.md), [gluteal_tendinopathy](gluteal_tendinopathy.md) (shared) | SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, HEAVY_EXTERNAL_LOAD, SINGLE_LEG_HIP_CONTROL, CROSS_BODY_HIP_STRETCH |
| KNEE_SENSITIVE | [patellofemoral_pain](patellofemoral_pain.md) | PROLONGED_SITTING, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, DEEP_KNEE_FLEXION_LOAD, END_RANGE_KNEE_EXTENSION_LOAD |

Limits of the derivation:

- **Single sources:** shoulder, elbow, wrist and knee each rest on one condition.
- **Lower back:** both conditions now restrict these two tags, but neither supports a blanket ban. [Saraceni 2020](https://doi.org/10.2519/jospt.2020.9218) found greater lumbar flexion during lifting was not a risk factor (low quality), [Michaelson 2016](https://doi.org/10.2340/16501977-2091) found supervised heavy deadlifting no worse than motor control, and [lumbar_radiculopathy](lumbar_radiculopathy.md) says "do not read this as a flexion ban".
- **Hip:** tags held by only one hip condition are left off: PROLONGED_SITTING, NERVE_TENSIONING, HAMSTRING_LENGTH_LOAD, FIGURE_FOUR_POSITION, RESISTED_HIP_EXTERNAL_ROTATION, END_RANGE_HIP_EXTENSION from [piriformis_syndrome](piriformis_syndrome.md), and SIDE_LYING_HIP_COMPRESSION, SIDE_SUPPORT_HOLD from [gluteal_tendinopathy](gluteal_tendinopathy.md).
- **Shoulder:** the four new tags are all exercise-specific and rest on biomechanical, cross-sectional or review data ([Timmons 2016](https://doi.org/10.1016/j.jse.2015.08.046), [Kolber 2014](https://doi.org/10.1519/JSC.0000000000000250), [Bonilla 2022](https://doi.org/10.3390/ijerph191912710), [Noteboom 2024](https://doi.org/10.3389/fphys.2024.1393235)). None was tested on outcomes.

## 5. Routines

No routine below is a treatment prescription. Doses are copied from the JSON and most come from trial or protocol programmes, not validated stand-alone use. The HIP_SENSITIVE label has no routine (see section 8).

Selection rules:

1. Keep only items with `bodyweightOrHold: true` that are rep or hold exercises. This excludes walking.
2. Require a numeric rep or hold dose, unless a low or moderate grade item was named in a trial, in which case it may be listed with the missing dose marked.
3. Leave out items the file marks as later-stage progressions, or items whose own caution argues against routine use. This now removes the nerve sliders.
4. In a very_low condition, keep only items labelled observational or stronger.
5. Rank by evidence, then prefer catalog entries, up to 5.

Some frequencies are missing and are marked in the tables. The only entry missing a rep or hold number is quadriceps setting, which has no hold time.

### [piriformis_syndrome](piriformis_syndrome.md) (5)

| Exercise | Source | Type | Dose | Dose comes from |
|---|---|---|---|---|
| Figure-four stretch, foot of the other leg against a wall | New | Timed hold | 3 x 120 s, 2 min rest, 3 sessions per week, 10 sessions | [Shamsi 2024a](https://doi.org/10.1186/s13102-023-00802-4), supervised, and no pain benefit over hot pack plus TENS |
| FAIR-position stretch | New | Reps with hold | 3 x 10, 15 s minimum, 3 days per week for 4 weeks, 30 s rest | [NCT05660005](https://clinicaltrials.gov/study/NCT05660005), registry with no posted results and an inconsistent schedule |
| Piriformis stretch, lying, knee toward the opposite shoulder | New | Reps with hold | 10 reps, 30 s, twice daily for 3 weeks; set count unclear in the source | [Guner 2023](https://doi.org/10.7759/cureus.43804), non-randomized; the source does not specify the position |
| Glute bridge | Catalog: glute-bridge | Bodyweight rep | 3 x 15; frequency not stated | [NCT07484373](https://clinicaltrials.gov/study/NCT07484373), results not found |
| Clamshell | Catalog: clamshell | Bodyweight rep | 3 x 15; frequency not stated; variant needs a decision | [NCT07484373](https://clinicaltrials.gov/study/NCT07484373), results not found |

Held out as expert opinion or lacking a dose: pigeon-type stretch, side-lying hip abduction, sciatic nerve glide, hip circumduction.

### [gluteal_tendinopathy](gluteal_tendinopathy.md) (5)

| Exercise | Source | Type | Dose | Dose comes from |
|---|---|---|---|---|
| Side-lying hip abduction, painful side up | Catalog: side-lying-hip-abduction | Bodyweight rep | 3 x 10, 6 s per rep, 60 s rest, daily; catalog lacks the setup cues | [Clifford 2019](https://doi.org/10.1136/bmjsem-2019-000558), pilot |
| Double-leg glute bridge | Catalog: glute-bridge | Bodyweight rep | 1 x 10, daily, 2 s per phase in week 1 | [LEAP protocol 2016](https://doi.org/10.1186/s12891-016-1043-6), not tested alone |
| Bodyweight double-leg squat | Catalog: bodyweight-squat | Bodyweight rep | 1 x 10, daily, 3 s per phase in week 2 | [LEAP protocol 2016](https://doi.org/10.1186/s12891-016-1043-6), not tested alone |
| Side-lying isometric hip abduction hold, painful side up | New | Reps with hold | 1 x 6, 30 s, 60 s rest, daily | [Clifford 2019](https://doi.org/10.1136/bmjsem-2019-000558), pilot, no advantage over isotonic work |
| Standing weight-bearing isometric gluteal contraction | New | Reps with hold | 3 x 10, 6 s, 60 s rest, daily | [Clifford 2019](https://doi.org/10.1136/bmjsem-2019-000558), pilot |

Swap for bilateral symptoms, because they prevent side-lying without compressing one hip: supine isometric hip abduction hold, 1-2 x 10, 5-10 s, "bi-daily" ([LEAP protocol](https://doi.org/10.1186/s12891-016-1043-6); the table is offset in XML, so this row is a reading of its layout). Held out: later-stage offset and single-leg-biased bridges and squats; side-stepping and the abduction slide, by the limit of 5 and because the slide needs a sliding surface; items that need a band, step or ball.

### [lumbar_radiculopathy](lumbar_radiculopathy.md) (3)

| Exercise | Source | Type | Dose | Dose comes from |
|---|---|---|---|---|
| Cat-cow | Catalog: cat-cow-stretch | Bodyweight rep | 3 x 10, one session a day, 10 sessions in 14 days | [Pradhan 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC11151706/), both groups |
| Bird dog | Catalog: bird-dog | Bodyweight rep | 2 x 10; home session frequency not captured | [Shaheen 2025](https://doi.org/10.23736/S1973-9087.24.08541-1), all arms |
| Glute bridge | Catalog: glute-bridge | Bodyweight rep | 2 x 10; home session frequency not captured | [Shaheen 2025](https://doi.org/10.23736/S1973-9087.24.08541-1), all arms |

All three are labelled expert opinion and none had its own effect tested. Choosing them maps onto [George 2021](https://doi.org/10.2519/jospt.2021.0304)'s broad categories. The seated slump and side-lying straight-leg-raise sliders in the earlier version are out under rule 3 (see section 8). Pelvic tilt and core bracing have the same 2 x 10 dose and evidence but no catalog entry. Dead bug and side plank have no dose. Direction-guided movement cannot be replicated by a fixed routine, and [Fritz 2021](https://doi.org/10.7326/M20-4187) reported side effects in 37%.

### [nonspecific_low_back_pain](nonspecific_low_back_pain.md) (5)

| Exercise | Source | Type | Dose | Dose comes from |
|---|---|---|---|---|
| Bird dog | Catalog: bird-dog | Bodyweight rep | 2 x 15, 2 sessions per week for 8 weeks | [Park 2026](https://doi.org/10.3390/healthcare14162550), pilot in 30 men aged 30-39 |
| Dead bug | Catalog: dead-bug | Bodyweight rep | 2 x 15, 2 sessions per week for 8 weeks | [Park 2026](https://doi.org/10.3390/healthcare14162550), same pilot |
| Glute bridge | Catalog: glute-bridge | Bodyweight rep | 3 x 10, 3 sessions per week for 6 weeks | [Kshirsagar 2026](https://doi.org/10.7759/cureus.114958), non-randomised, 36 male runners aged 18-25 |
| Plank, knee-supported | Catalog: plank, which does not match | Timed hold | 2 x 30 s, 3 sessions per week for 6 weeks, progressing to 2 x 60 s | [Kshirsagar 2026](https://doi.org/10.7759/cureus.114958), same trial; holds were knee-supported |
| Hamstring stretch | Catalog: hamstring-stretch | Reps with hold | 5 x 30 s, twice weekly for 6 weeks | [Nagrale 2012 as tabulated by Gou 2024](https://doi.org/10.1177/20503121241252251); complement only |

This mixes doses from three programmes, so no trial tested this combination. Held out: side plank (same dose, but its caution says it may be too hard first); cat-cow (10 to 15 reps, sets not reported); abdominal bracing (20 x 10 s, [Park 2026](https://doi.org/10.3390/healthcare14162550), no catalog entry, and [Lunkes 2026](https://doi.org/10.1016/j.jphys.2026.03.007) leaves the case for bracing open); curl-up, pelvic tilt, trunk rotation and child's pose (no dose).

### [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) (1)

| Exercise | Source | Type | Dose | Dose comes from |
|---|---|---|---|---|
| Posterior shoulder stretch (cross-body) | Catalog: cross-body-shoulder-stretch | Timed hold | 3 x 30 s, twice daily | [Holmgren 2012](https://doi.org/10.1136/bmj.e787), part of a multi-exercise programme |

Held out: the numerically dosed strengthening needs a band or weight, and the arm-supported hold, short-lever flexion and assisted range work are expert opinion with no usable rep dose.

### [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) (2)

| Exercise | Source | Type | Dose | Dose comes from |
|---|---|---|---|---|
| Isometric wrist extension hold | New | Reps with hold | 1 x 5, up to 60 s, once daily; 60 s is a ceiling and load is unresolved | [Bateman 2021](https://doi.org/10.1136/bmjopen-2021-053841), consensus; expert opinion |
| Wrist extensor stretch | New | Reps with hold | Three 30 s holds before and three after loading, with the once-daily programme | [Bateman 2021](https://doi.org/10.1136/bmjopen-2021-053841), chosen by vote for practicality; expert opinion |

Bodyweight is a poor fit: [Vuvan 2020](https://doi.org/10.1249/mss.0000000000002128) used a water container as resistance, though [Park 2010](https://doi.org/10.4055/cios.2010.2.3.173) was equipment-free. Catalog wrist extension and the rubber-bar eccentric exercise carry much of the condition's evidence but are excluded because they use equipment.

### [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) (1)

| Exercise | Source | Type | Dose | Dose comes from |
|---|---|---|---|---|
| Tendon gliding sequence | New | Reps with hold | 3 x 5, 7 s, 3 times per week for 3 weeks | [Sheereen 2022](https://doi.org/10.1155/2022/1975803), alongside manual therapy, no control group |

Held out: median nerve gliding, whose record calls it the lowest-confidence candidate. The wrist stretch, active wrist bend, finger bend and ball squeeze are expert opinion, and the file says pressure data conflict with the bend and squeeze.

### [patellofemoral_pain](patellofemoral_pain.md) (4)

| Exercise | Source | Type | Dose | Dose comes from |
|---|---|---|---|---|
| Side-lying hip abduction | Catalog: side-lying-hip-abduction | Bodyweight rep | 3 x 10, 2 days per week for 6 weeks, then up to 3 x 20 | [Kamel 2024](https://doi.org/10.1186/s13018-024-04688-x), read here as an unloaded start |
| Partial-range bodyweight squat | Catalog: bodyweight-squat, which needs a depth limit | Reps with hold | 3 x 10, 2 s hold at 45 degrees, 2 min rest, 5 days per week for 8 weeks | [Chen 2026](https://doi.org/10.12659/MSM.953994), 30 adults, range not precisely monitored |
| Straight leg raise | New | Bodyweight rep | 3 x 10, 2 days per week for 6 weeks | [Kamel 2024](https://doi.org/10.1186/s13018-024-04688-x) |
| Isometric quadriceps setting | New | Reps with hold | 3 x 10, at least 6 days per week in week 1; hold time not reported | [Ferber 2015](https://doi.org/10.4085/1062-6050-49.3.70) |

Held out as later-stage: shallow wall sit, step-down, single-leg squat. Held out by their own cautions: the [Harvie 2011](https://doi.org/10.2147/JMDH.S24595) stretches (3 x 30 s, "always bundled with strengthening", so the systematic review label describes common prescription), the [Hasan 2025](https://doi.org/10.7717/peerj.19381) items (a weak trial), short foot and prone hip extension (trial populations limited by foot type). Wall mini-squat repeats the partial squat. The rest need a band or machine.

## 6. New exercises

**Figure-four stretch: now in the JSON.** The earlier version said it was absent. [piriformis_syndrome](piriformis_syndrome.md) now records a figure-four candidate from [Shamsi 2024a](https://doi.org/10.1186/s13102-023-00802-4), and a matching restriction when the position reproduces symptoms. Calling it a figure-four is that file's mapping: no paper found uses the term. See section 8.

All 10 exercises needed by the proposed routines are needed by one condition each, so no count separates them.

| # | Exercise | Conditions needing it | Count |
|---:|---|---|---:|
| 1 | Figure-four stretch | [piriformis_syndrome](piriformis_syndrome.md) | 1 |
| 2 | FAIR-position stretch | [piriformis_syndrome](piriformis_syndrome.md) | 1 |
| 3 | Piriformis stretch, lying, knee toward the opposite shoulder | [piriformis_syndrome](piriformis_syndrome.md) | 1 |
| 4 | Side-lying isometric hip abduction hold | [gluteal_tendinopathy](gluteal_tendinopathy.md) | 1 |
| 5 | Standing weight-bearing isometric gluteal contraction | [gluteal_tendinopathy](gluteal_tendinopathy.md) | 1 |
| 6 | Isometric wrist extension hold | [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) | 1 |
| 7 | Wrist extensor stretch | [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) | 1 |
| 8 | Tendon gliding sequence | [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) | 1 |
| 9 | Isometric quadriceps setting | [patellofemoral_pain](patellofemoral_pain.md) | 1 |
| 10 | Straight leg raise | [patellofemoral_pain](patellofemoral_pain.md) | 1 |

The files list 64 `catalog: new` candidates in total. The other 54 are not needed by a proposed routine:

- **[piriformis_syndrome](piriformis_syndrome.md):** Pigeon-type forward-lean stretch; Sciatic nerve glide; Hip circumduction.
- **[gluteal_tendinopathy](gluteal_tendinopathy.md):** Standing isometric hip abduction in slight abduction; Supine isometric hip abduction hold; Standing hip abduction slide; Offset bridge; Offset squat; Bodyweight side-stepping; Band side slide; Single-leg-biased bridge; Single-leg-biased squat; Single-leg stance hold; Hip hike on a step; Wall squat hold with a ball.
- **[lumbar_radiculopathy](lumbar_radiculopathy.md):** Seated slump slider; Side-lying straight-leg-raise slider; Repeated lumbar movement in a direction chosen by symptom response; Self-performed lower-limb neural slider; Side-lying static foramen opening; Pelvic tilt; Core bracing; Hook-lying abdominal hollowing.
- **[nonspecific_low_back_pain](nonspecific_low_back_pain.md):** Abdominal bracing; Curl-up; Thoracic elevation; Pelvic tilt; Trunk rotation.
- **[rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md):** Eccentric-emphasis rotator cuff strengthening with band or light weight; Arm-supported isometric hold in the direction of pain; Short-lever shoulder flexion; Lower-load rotator cuff strengthening; Progressive high-load rotator cuff strengthening; Banded external rotation in about 45 degrees of scaption and abduction to about 45 degrees; Resisted external rotation; Self-paced progressive shoulder programme to moderate effort; Assisted range of motion.
- **[lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md):** Eccentric wrist extension with a rubber bar; Shoulder and scapular stabilizer training.
- **[carpal_tunnel_syndrome](carpal_tunnel_syndrome.md):** Median nerve gliding sequence; Wrist flexor and extensor stretch; Active wrist bend forward and back; Finger bend at the middle joints; Rubber ball hand squeeze.
- **[patellofemoral_pain](patellofemoral_pain.md):** Seated banded hip external rotation; Isometric quadriceps at 60 degrees of knee flexion; Terminal knee extension with band; Prone hip extension; Wall mini-squat to 45 degrees; Single-leg squat; Pillow-squeeze hip adduction; Isometric resisted hip abduction; Short foot exercise; Hip internal rotator stretch.

Catalog entries reused across routines: glute-bridge ([piriformis_syndrome](piriformis_syndrome.md), [gluteal_tendinopathy](gluteal_tendinopathy.md), [lumbar_radiculopathy](lumbar_radiculopathy.md), [nonspecific_low_back_pain](nonspecific_low_back_pain.md)), side-lying-hip-abduction ([gluteal_tendinopathy](gluteal_tendinopathy.md), [patellofemoral_pain](patellofemoral_pain.md)), bodyweight-squat ([gluteal_tendinopathy](gluteal_tendinopathy.md), [patellofemoral_pain](patellofemoral_pain.md)), bird-dog ([lumbar_radiculopathy](lumbar_radiculopathy.md), [nonspecific_low_back_pain](nonspecific_low_back_pain.md)).

## 7. Scale decision input

Criterion: at least six of eight have a verified restriction list, and the last two conditions processed needed little or no new vocabulary.

**Result: FAIL, narrowly.**

| Clause | Numbers | Result |
|---|---|---|
| At least 6 of 8 have a verified restriction list | 8 of 8 have a non-empty list after verification, and all 8 now restrict at least one demand tag. The verification records still list things they could not confirm. | Pass |
| Last two needed little or no new vocabulary | [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) added 1 and [patellofemoral_pain](patellofemoral_pain.md) added 2. That is 3 of 26 values. | Fail |

New values by processing order were 11, 2, 2, 1, 4, 3, 1, 2.

The criterion does not define "little". This consolidation read it as at most 1 new value per condition, and [patellofemoral_pain](patellofemoral_pain.md) misses by one. Merging the two knee tags would pass it. So this result turns on one merge decision and on the order of processing: [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) now introduces HAND_GRIP_LOAD, which is why [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md) needs only 1 where it needed 2 before.

The bigger signal is that depth moved the vocabulary as much as breadth did. On the tagging used here, rereading the same eight conditions took it from 16 to 26. [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) went from 0 new values to 4, because the deeper search found exercise-specific data. The earlier reading, that the first shoulder and back conditions add nothing, no longer holds. New regions may still add more, and eight conditions cannot test that.

## 8. Open questions and weak spots

### Earlier proposals against the new evidence

**1. `avoid` items exclude, while `modify` items rank lower and show a cue. Holds as a default, but it needs a separate gate.** Only 8 of 83 items are `avoid`, and 5 of those are not taggable exercises: the neural mobilisation gate, traction and belts in [lumbar_radiculopathy](lumbar_radiculopathy.md), and bed rest and belts in [nonspecific_low_back_pain](nonspecific_low_back_pain.md). The files often say a `modify` item is not a ban: [flexion in lifting](https://doi.org/10.3390/sports14040151), [overhead work](https://doi.org/10.2340/17453674.2026.45365), [the figure-four](https://doi.org/10.1186/s13102-023-00802-4) and [squatting](https://doi.org/10.5606/tftrd.2024.14668). So excluding them would go beyond the evidence. Three corrections apply. The one exercise-relevant exclusion is only a precaution: [gluteal_tendinopathy](gluteal_tendinopathy.md)'s adduction stretch item rests on [LEAP](https://doi.org/10.1186/s12891-016-1043-6), while [Nava 2025](https://doi.org/10.1016/j.jsams.2025.04.012) and [Rompe 2009](https://doi.org/10.1177/0363546509334374) included such stretches with good results and no trial isolates them. Some `modify` items behave like gates: loaded flexion with osteoporosis or prior fracture needs clinician clearance ([POGO Physio](https://www.pogophysio.com.au/?p=8582)), and work after carpal tunnel release follows the surgeon ([Newington 2022](https://doi.org/10.1177/17589983221113870)). The 29 rule items do nothing under either branch. No source tested ranking or cueing itself, so the mechanism is app design and not an evidence-based finding.

**2. The HIP label uses shared tags with no routine. Holds, more strongly than before.** Section 4 now uses this rule. The diagnoses still overlap: piriformis syndrome is treated as a deep gluteal subgroup ([Park 2020](https://doi.org/10.1302/0301-620X.102B5.BJJ-2019-1212.R1), [Metikala 2022](https://doi.org/10.7759/cureus.23153)), [Kay 2017](https://doi.org/10.1016/j.arthro.2017.06.041) found only 26% of surgical deep gluteal cases were piriformis syndrome, and GTPS is itself an umbrella term. The two conditions give opposing exercise advice. [Grimaldi and Fearon 2015](https://doi.org/10.2519/jospt.2015.5829) and [LEAP protocol 2016](https://doi.org/10.1186/s12891-016-1043-6) exclude knee-across stretches, while [piriformis_syndrome](piriformis_syndrome.md) lists them. [piriformis_syndrome](piriformis_syndrome.md) lists the clamshell, while [NHS Lothian 2025](https://apps.nhslothian.scot/files/sites/2/GTPS-FACT-SHEET-for-Ref-help-1.pdf) and [Moore 2019](https://doi.org/10.1123/jsr.2018-0340) argue for another abductor. Glute bridge is the only obvious shared item, at 3 x 15 and 1 x 10 from programmes that were not testing it alone, and [Chaari 2024](https://doi.org/10.1016/j.jbmt.2024.10.027) found no immediate pain change. Every shared tag rests on expert opinion or one case report. [Kinsella 2024](https://doi.org/10.2519/jospt.2023.11890) shows palpation and resisted testing can separate lateral hip pain, but that needs an examiner.

**3. Lying piriformis stretch versus figure-four. The earlier position is overturned, but neither stretch is shown to work.** The earlier version said figure-four was absent and listed the lying stretch first. Figure-four now has the better-specified dose and the stronger study design: [Shamsi 2024a](https://doi.org/10.1186/s13102-023-00802-4), 3 x 2 minutes in a randomized trial. That same trial is the file's main evidence against it, because stretching did not beat hot pack plus TENS for pain (p=0.264), all arms had those treatments, and adverse events were not reported. A similar position gave the least piriformis length change in cadavers (7.7 and 2.5 mm against 30.7 and 23.7 mm, [McGovern 2017](https://doi.org/10.26603/ijspt20171068)), it is used as a provocation test ([Michel 2013b](https://doi.org/10.1016/j.rehab.2013.03.006)), and [Neal 2026](https://doi.org/10.3390/sports14030095) reports 25.4% of surveyed hockey players had L5-S1 pain with sustained stretching. The lying stretch has only a non-randomized dose ([Guner 2023](https://doi.org/10.7759/cureus.43804)), an unclear set count, and a position borrowed from separate studies. It also draws the knee toward the opposite shoulder, which [gluteal_tendinopathy](gluteal_tendinopathy.md) says to avoid; figure-four does not adduct the hip on this consolidation's reading. So section 5 lists figure-four first on design and dose only. The app should make no benefit claim for either, and [Aali 2026](https://doi.org/10.4081/ejtm.2026.14765)'s positive conclusion is abstract only.

**4. Doses for the low back and elbow routines. Mostly found now, so "dose missing" no longer applies, but none is a validated dose.** Exact doses:

| Routine | Exercise | Dose | Source |
|---|---|---|---|
| [nonspecific_low_back_pain](nonspecific_low_back_pain.md) | Bird dog | 2 x 15, 2 per week for 8 weeks. Alternative: 2 x 30 s rising to 4 x 30 s, 3 per week for 6 weeks | [Park 2026](https://doi.org/10.3390/healthcare14162550); [Kshirsagar 2026](https://doi.org/10.7759/cureus.114958) |
| [nonspecific_low_back_pain](nonspecific_low_back_pain.md) | Dead bug | 2 x 15, 2 per week for 8 weeks | [Park 2026](https://doi.org/10.3390/healthcare14162550) |
| [nonspecific_low_back_pain](nonspecific_low_back_pain.md) | Glute bridge | 3 x 10, 3 per week for 6 weeks | [Kshirsagar 2026](https://doi.org/10.7759/cureus.114958) |
| [nonspecific_low_back_pain](nonspecific_low_back_pain.md) | Plank and side plank, knee-supported | 2 x 30 s rising to 2 x 60 s, 3 per week for 6 weeks | [Kshirsagar 2026](https://doi.org/10.7759/cureus.114958) |
| [nonspecific_low_back_pain](nonspecific_low_back_pain.md) | Abdominal bracing | 20 x 10 s, 2 per week for 8 weeks | [Park 2026](https://doi.org/10.3390/healthcare14162550) |
| [nonspecific_low_back_pain](nonspecific_low_back_pain.md) | Cat-cow | 10 rising to 15 reps, sets not reported, 3 per week for 6 weeks | [Kshirsagar 2026](https://doi.org/10.7759/cureus.114958) |
| [nonspecific_low_back_pain](nonspecific_low_back_pain.md) | Hamstring stretch | 5 x 30 s, 2 per week for 6 weeks | [Gou 2024](https://doi.org/10.1177/20503121241252251) |
| [nonspecific_low_back_pain](nonspecific_low_back_pain.md) | Curl-up, pelvic tilt, trunk rotation, child's pose | Still none | [Kanli 2024](https://doi.org/10.5606/tftrd.2024.13257), [Asik 2025](https://doi.org/10.1007/s11845-025-03939-y) |
| [lumbar_radiculopathy](lumbar_radiculopathy.md) | Bird dog, glute bridge | 2 x 10, frequency not captured | [Shaheen 2025](https://doi.org/10.23736/S1973-9087.24.08541-1) |
| [lumbar_radiculopathy](lumbar_radiculopathy.md) | Cat-cow | 3 x 10, daily, 10 sessions in 14 days | [Pradhan 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC11151706/) |
| [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) | Isometric wrist extension hold | 1 x 5, up to 60 s, once daily | [Bateman 2021](https://doi.org/10.1136/bmjopen-2021-053841) |
| [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) | Lowest-provocation alternative | 10 x 15 s at 20% below pain threshold; single bout, no treatment effect shown | [Coombes 2016](https://doi.org/10.1097/AJP.0000000000000365) |
| [lateral_elbow_tendinopathy](lateral_elbow_tendinopathy.md) | Wrist extensor stretch | Three 30 s holds before and three after loading | [Bateman 2021](https://doi.org/10.1136/bmjopen-2021-053841) |

Limits: the low back file says every dose describes what a trial did. [Arora 2026](https://doi.org/10.1136/bjsports-2026-111988) states there is no single optimal dose, and its model predictions (3 per week, 30 minutes, 8 weeks for short-term pain) are not tested regimens. The `rct` labels on plank, side plank, bridge and cat-cow come from [Kanli 2024](https://doi.org/10.5606/tftrd.2024.13257) and [Asik 2025](https://doi.org/10.1007/s11845-025-03939-y), while their numbers come from the non-randomised [Kshirsagar 2026](https://doi.org/10.7759/cureus.114958). For the elbow hold, 60 s is a ceiling, its attribution to [Barratt 2018](https://doi.org/10.1016/j.physio.2017.09.001) is unconfirmed, and [Vuvan 2020](https://doi.org/10.1249/mss.0000000000002128)'s hold time was never read. Load is still undefined: [Bateman 2021](https://doi.org/10.1136/bmjopen-2021-053841) says maximal resistance, while [Lucado 2022](https://doi.org/10.2519/jospt.2022.0302) starts at 20%-40% MVIC and an app cannot measure either.

**5. Leaving out nerve sliders. Supported for an unsupervised app, and it reverses the earlier lumbar routine.** [lumbar_radiculopathy](lumbar_radiculopathy.md) grades self-directed neural mobilisation "not established" and supervised work very low, with no trial of self-directed mobilisation alone. Contraindications must be identified first ([Lin 2023](https://doi.org/10.3390/life13122255)), which an app cannot do. Benefit conflicts: [Lin 2023](https://doi.org/10.3390/life13122255) pools large effects with 65% of trials at high risk of bias, while [Ferreira 2016](https://doi.org/10.1016/j.jphys.2016.08.007) was null at its primary time point and [Sousa Filho 2022](https://doi.org/10.1016/j.jbmt.2022.01.007), [Plaza-Manzano 2020](https://doi.org/10.1097/PHM.0000000000001295) and [Nahid 2025](https://doi.org/10.1002/hsr2.70872) found no between-group benefit. A Delphi panel puts it after the acute stage ([Thoomes 2023](https://doi.org/10.1080/09638288.2022.2130448)). The only self-performed dose is a single 30-second tolerance session in people with diabetes ([Boyd 2017](https://doi.org/10.1080/10669817.2016.1180772)). In [piriformis_syndrome](piriformis_syndrome.md), no self-performed dose exists and [Usman 2025](https://doi.org/10.53389/JRCRS.2025130403) found no difference from stretching. In [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md), reviews disagree ([Paraskevopoulos 2023](https://doi.org/10.1080/09593985.2022.2068097), [Zaheer 2023](https://doi.org/10.3390/jcm12154888)) and [Arman 2024](https://doi.org/10.23736/S1973-9087.24.08432-6) found no between-group BCTQ difference. This does not show sliders fail under a clinician, and its absence from several guidelines is, in the file's words, a null result and not a recommendation against it.

**6. Reps with holds. Holds, and needs a little more than one new type.** 29 of 105 candidates have both a rep count and a hold, across all eight conditions, so the two existing types do not fit. Routine examples: 3 x 10 with 6 s ([Clifford 2019](https://doi.org/10.1136/bmjsem-2019-000558)), 1 x 5 up to 60 s ([Bateman 2021](https://doi.org/10.1136/bmjopen-2021-053841)), 3 x 5 with 7 s ([Sheereen 2022](https://doi.org/10.1155/2022/1975803)), 3 x 10 with a 2 s hold ([Chen 2026](https://doi.org/10.12659/MSM.953994)). Doses also carry between-rep rest (60 s, [Clifford 2019](https://doi.org/10.1136/bmjsem-2019-000558)), sessions per day or week, hold ceilings and ranges. One source cell cannot be resolved at all: "Hold 15s" with "8-12" ([Nava protocol 2022](https://doi.org/10.1371/journal.pone.0269230)). So the type needs sets, reps, hold, rest and frequency, with dose kept on the routine.

**7. Staying at 8 conditions. Holds.** The criterion still fails (section 7), though narrowly and sensitively. More usefully, the deeper search changed routines, tags and verdicts within these eight while no overall grade moved. Those eight still have open gaps, such as no patient study of squats, lunges, deadlifts or hip thrusts in [piriformis_syndrome](piriformis_syndrome.md), and no restriction trial on gym lifts in [lumbar_radiculopathy](lumbar_radiculopathy.md) or overhead work in [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md). The JSON does not assess adding any other condition, so this is a reading of readiness, not a finding from the papers.

### Decisions needed

1. **Figure-four or lying stretch.** Section 5 lists figure-four first. Confirm it, and decide whether unsupervised 2-minute holds are acceptable, since the trial supervised them.
2. **Remaining dose gaps.** Quadriceps setting has no hold time. Elbow hold load is undefined. Low back programme doses come from different trials with different weekly frequencies, so the routine needs one schedule, and that choice would not be evidence-based.
3. **Gates.** Which `modify` items should act as gates (see proposal 1), and how the app asks about them.
4. **Rules outside the tags.** 29 of 83 restrictions cannot be expressed as demands, including 8 of 12 for [lumbar_radiculopathy](lumbar_radiculopathy.md). They need symptom prompts, safety gates or education text.
5. **Vocabulary size.** Decide whether the 10 new values are worth it. The four shoulder tags and figure-four are narrow, and could be cues instead of tags.

### Conflicts between conditions

6. **The main hip conflict.** [Grimaldi and Fearon 2015](https://doi.org/10.2519/jospt.2015.5829) says to avoid piriformis stretches that draw the knee across the body, while [piriformis_syndrome](piriformis_syndrome.md) proposes them. The diagnoses are hard to separate. Keeping HIP_SENSITIVE routine-free avoids this only at label level.
7. **Clamshell.** [piriformis_syndrome](piriformis_syndrome.md) proposes it, [gluteal_tendinopathy](gluteal_tendinopathy.md) says to prefer another abductor, [Kim 2026](https://doi.org/10.1177/19417381251372979) shows piriformis activity in healthy adults, and [patellofemoral_pain](patellofemoral_pain.md) uses it within [Kamel 2024](https://doi.org/10.1186/s13018-024-04688-x). The arm of [Nava protocol 2022](https://doi.org/10.1371/journal.pone.0269230) that used it was not superior.
8. **Tags against a condition's own routine.** Every [piriformis_syndrome](piriformis_syndrome.md) routine item except the bridge carries a tag that condition restricts. The two [gluteal_tendinopathy](gluteal_tendinopathy.md) side-lying exercises carry SIDE_LYING_HIP_COMPRESSION but are done painful side up. The elbow hold carries WRIST_EXTENSOR_WORK, restricted only at an aggravating load. The engine needs an exception mechanism or finer tags.
9. **Nerve tensioning.** It is a restricted tag for [piriformis_syndrome](piriformis_syndrome.md) and [carpal_tunnel_syndrome](carpal_tunnel_syndrome.md), but [lumbar_radiculopathy](lumbar_radiculopathy.md) has no such restriction. [Lin 2023](https://doi.org/10.3390/life13122255)'s subgroups favouring sliders are not head-to-head, and the [piriformis_syndrome](piriformis_syndrome.md) restriction rests on one narrative review ([Siraj 2022](https://doi.org/10.7759/cureus.32952)).
10. **Flexion.** [Gao 2026](https://doi.org/10.1038/s41413-026-00572-y) says to avoid excessive spinal flexion or extension in Pilates and yoga, which the [nonspecific_low_back_pain](nonspecific_low_back_pain.md) file says sits uneasily with its lifting evidence. Cat-cow is in the [lumbar_radiculopathy](lumbar_radiculopathy.md) routine, and END_RANGE_SPINE_POSITION does not yet settle whether it counts.
11. **Bracing.** [Michaelson 2016](https://doi.org/10.2340/16501977-2091) and [Park 2026](https://doi.org/10.3390/healthcare14162550) used bracing, while [Lunkes 2026](https://doi.org/10.1016/j.jphys.2026.03.007) found relaxed cueing slightly better in Pilates.
12. **Side plank.** It is a candidate in [lumbar_radiculopathy](lumbar_radiculopathy.md) and [nonspecific_low_back_pain](nonspecific_low_back_pain.md), while [Collings 2023](https://doi.org/10.1249/MSS.0000000000003091) places it in a top tier for gluteal force in healthy young footballers.
13. **Night pain.** [Sveinall 2024](https://doi.org/10.1136/bmjopen-2024-085916) treats sleep-disturbing pain after training as a cue to reduce weight, while [NHS Highland 2026](https://www.rightdecisions.scot.nhs.uk/tam-treatments-and-medicines-nhs-highland/adult-therapeutic-guidelines/orthopaedics/musculoskeletal-pathways-guidelines/) lists night pain as a red flag. The file splits these by its own reading.

### Thin spots in the evidence

14. **Reading depth.** 408 of 654 unique sources were read only as abstracts. [Willy 2019](https://doi.org/10.2519/jospt.2019.0302), the JOSPT carpal tunnel guidelines and the full WHO guideline were not read in the original, and several grades are second-hand ([Connell 2020](https://pubmed.ncbi.nlm.nih.gov/33487639/), [Physiotutors CPG 2026](https://www.physiotutors.com/?p=29289), [Guideline Central 2019](https://doi.org/10.2519/jospt.2019.0301)).
15. **Mechanism doubts.** One [piriformis_syndrome](piriformis_syndrome.md) hypothesis is overstretching ([Tonley 2010](https://doi.org/10.2519/jospt.2010.3108), one patient), which would make its stretches unhelpful. [McMillan 2022](https://doi.org/10.1177/03635465211061142) and [Ganderton 2018](https://doi.org/10.1089/jwh.2017.6729) found targeted exercise no better than sham when both arms had education, and [Mellor 2022](https://doi.org/10.2519/jospt.2022.11261) found strength did not mediate benefit. [Saltychev 2018](https://doi.org/10.2340/16501977-2295) reports over 80% of 37 trials showed no clinically significant benefit.
16. **Restrictions are mostly opinion or inference.** All but one [gluteal_tendinopathy](gluteal_tendinopathy.md) item and 12 of 15 [piriformis_syndrome](piriformis_syndrome.md) items are expert opinion. Gym-lift items in [lumbar_radiculopathy](lumbar_radiculopathy.md), [rotator_cuff_related_shoulder_pain](rotator_cuff_related_shoulder_pain.md) and [patellofemoral_pain](patellofemoral_pain.md) come from healthy, cadaveric, modelled or cross-sectional data, and carpal tunnel items from healthy-volunteer, cadaver or occupational data.
17. **Absent restrictions are not clearance.** [JOSPT 2025](https://doi.org/10.2519/jospt.2025.13182) and [BESS 2025](https://doi.org/10.1177/17585732251374282) do not restrict overhead exercise, but nothing tests it as a treatment or restriction variable. [Page 2012](https://doi.org/10.1002/14651858.CD009899) found only two studies measured adverse effects. An empty tag must not read as "safe".
18. **Harms are under-reported.** Adverse events appeared in 28.5% of trials in [Arora 2026](https://doi.org/10.1136/bjsports-2026-111988) and under 31% of reviews in [Comachio 2025](https://doi.org/10.1016/j.jshs.2025.101038). Where measured, flares are common: 37 of 100 in [Fritz 2021](https://doi.org/10.7326/M20-4187) and over 30% in both arms of [Ingwersen 2017](https://doi.org/10.1177/2325967117723292).
19. **Home delivery.** Supervision beat home-only exercise in [Hayden 2005](https://doi.org/10.7326/0003-4819-142-9-200505030-00014), [Nicol 2023](https://doi.org/10.3390/jcm12041685) reports insufficient evidence that home programmes differ from no care, and [Sveinall 2024](https://doi.org/10.1136/bmjopen-2024-085916) judged a heavy home programme unsuitable. Adherence ran 57% to 61% in [Ingwersen 2017](https://doi.org/10.1177/2325967117723292). [GRASP 2021](https://doi.org/10.1016/s0140-6736%2821%2900846-1) found a progressive programme no better than one advice session.
20. **Sparse routines.** Shoulder and carpal tunnel get one entry each, and the bodyweight limit excludes the elbow's and shoulder's main evidence.
21. **Evidence labels can overstate.** Many `rct` labels describe one component inside a programme. Figure-four and FAIR carry `rct` from a null comparison and a registry entry. The median nerve glide is `systematic_review`, though reviews conflict. The elbow load item is `guideline` while the [guideline](https://doi.org/10.2519/jospt.2022.0302) itself notes no study described how load was set.
22. **Grades are judgement.** The very_low grade for [piriformis_syndrome](piriformis_syndrome.md) is explicitly not a formal GRADE assessment.
23. **NICE NG59 varies by file.** [lumbar_radiculopathy](lumbar_radiculopathy.md) marks it `not_reconfirmed` after HTTP 403, [NICE NG59](https://pubmed.ncbi.nlm.nih.gov/33090750/) and [NICE NG59](https://www.nice.org.uk/guidance/ng59/chapter/Recommendations) mark it `full_text` for the recommendations page only, and the July 2026 update date was seen only in a vendor blog per [lumbar_radiculopathy](lumbar_radiculopathy.md).

### Data and safety quality

24. **`bodyweightOrHold` is unreliable.** It is true for the wrist hold (water container in one trial), ball squeeze, abduction slide, figure-four (a wall) and walking. Equipment should be checked before build.
25. **Catalog ambiguity.** The clamshell's pictured variant uses a band while the entry says bodyweight. side-lying-hip-abduction is near-identical to side-lying-leg-raise. The partial squat needs a depth limit, and the plank dose was knee-supported, which the file says does not describe a toes-supported forearm plank.
26. **Doses differ by condition for the same exercise.** Glute bridge is 3 x 15, 1 x 10, 2 x 10 or 3 x 10, so dose must belong to the routine, not the exercise.
27. **Printed errors to avoid.** The files flag values not to quote, including [Bremer 2025](https://doi.org/10.1177/02692155251327298)'s SMD of 5.77, [Zhang 2025](https://doi.org/10.1186/s13018-025-06514-4)'s SMDs, [Pourahmadi 2019](https://doi.org/10.1093/pm/pny208)'s SMD of -8.03, [Mabrouk 2026](https://doi.org/10.3389/fpain.2026.1792933)'s effect sizes and [Usman 2025](https://doi.org/10.53389/JRCRS.2025130403)'s dose arithmetic.
28. **Red flags are not ready for users.** Some use clinician referral wording, UK-specific actions, or review-written actions with no source triage rule. [gluteal_tendinopathy](gluteal_tendinopathy.md)'s action wording was written for this app, and [Finucane 2020](https://doi.org/10.2519/jospt.2020.9971) notes most red flags lack strong diagnostic accuracy evidence. The app cannot screen for serious pathology.
29. **Misleading ids and uneven schema.** Source ids cochrane_2022 and dahm2022 are dated 2012 and 2010 in their citations. Same-paper ids differ between files (jospt2021 and george2021). Files use both `alternativeDoses` and `alternateDoses`. `verification.sourcesChecked` says 101 against 102 sources for [lumbar_radiculopathy](lumbar_radiculopathy.md), and 102 against 101 for [nonspecific_low_back_pain](nonspecific_low_back_pain.md).
30. **Tags are untested.** The 26 values have not been applied to the catalog, and two people tagging independently might disagree. HEAVY_EXTERNAL_LOAD is the least precise, because the sources give no usable load threshold.
31. **Links.** All 614 DOI links were confirmed registered at doi.org on 2026-11-01. Publisher pages, PubMed records and the 29 other URLs were not reopened in that check. Whether each DOI matches its paper rests on the condition files' own verification.
