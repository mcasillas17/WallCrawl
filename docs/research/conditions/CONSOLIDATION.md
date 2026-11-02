# Condition research consolidation

Source: the eight `*.json` files in this folder, after their verification passes. The JSON files carry no demand tags, so the tag names, tag mappings and routine selections here are this consolidation's judgement. The facts behind them come only from the JSON. Catalog ids are as written in the JSON and were not checked against the catalog itself.

## 1. Scorecard

Conditions are in processing order. "Restrictions" counts `avoidOrModify` items after verification. "Removed" counts `verification.itemsRemoved`.

| Condition | Evidence grade | Restrictions | Routine candidates | Red flags | Removed | New vocabulary |
|---|---|---:|---:|---:|---:|---|
| piriformis_syndrome | very_low | 9 | 7 | 7 | 1 | PROLONGED_SITTING, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, HEAVY_EXTERNAL_LOAD, SINGLE_LEG_HIP_CONTROL, CROSS_BODY_HIP_STRETCH, NERVE_TENSIONING, HAMSTRING_LENGTH_LOAD (8) |
| gluteal_tendinopathy | low | 9 | 11 | 8 | 3 | SIDE_LYING_HIP_COMPRESSION (1) |
| lumbar_radiculopathy | low | 5 | 8 | 7 | 3 | None (0) |
| nonspecific_low_back_pain | moderate | 5 | 9 | 8 | 5 | TRUNK_FLEXION_UNDER_LOAD (1) |
| rotator_cuff_related_shoulder_pain | low | 4 | 4 | 6 | 1 | None (0) |
| lateral_elbow_tendinopathy | low | 5 | 4 | 6 | 2 | WRIST_EXTENSOR_WORK, FOREARM_TWIST_OR_PRONATED_LIFT (2) |
| carpal_tunnel_syndrome | very_low | 5 | 7 | 5 | 1 | WRIST_END_RANGE_LOAD, HAND_GRIP_LOAD (2) |
| patellofemoral_pain | low | 4 | 12 | 6 | 1 | DEEP_KNEE_FLEXION_LOAD, END_RANGE_KNEE_EXTENSION_LOAD (2) |
| **Total** | | **46** | **62** | **53** | **17** | **16** |

What verification removed:

- **piriformis_syndrome:** the caution clause saying the stretch may not suit people with hip impingement.
- **gluteal_tendinopathy:** the supine isometric abduction exercise (dose unsupported), and uncited bridge and squat cues about knee position.
- **lumbar_radiculopathy:** the restriction on nerve tensioners, the supine straight-leg-raise slider, and the source stochkendahl2018.
- **nonspecific_low_back_pain:** a symptom-reproduction restriction, plus bird dog, glute bridge, abdominal drawing-in and repeated prone extension. The bird dog and bridge removals were about dose: the file says the exercises themselves are supported. Three sources were also removed.
- **rotator_cuff_related_shoulder_pain:** the band row, which the trial appendix does not contain.
- **lateral_elbow_tendinopathy:** the restriction on hard or sustained gripping, and the wrist extensor stretch.
- **carpal_tunnel_syndrome:** the uncited source jospt_cpg_2026. No exercise or restriction was removed.
- **patellofemoral_pain:** a diagnosis claim about excluding other causes. No exercise or restriction was removed.

## 2. MovementDemand vocabulary

There are 16 values. Tag an exercise with a value when its normal performance meets the definition. Nothing here is a score.

| Value | Definition | Conditions restricting it |
|---|---|---|
| PROLONGED_SITTING | The exercise keeps the person seated throughout, including cross-legged or slumped sitting. | piriformis_syndrome, patellofemoral_pain |
| SUSTAINED_LOCOMOTION | The exercise is continuous running, walking, cycling, or hill or stair climbing. | piriformis_syndrome, gluteal_tendinopathy, patellofemoral_pain |
| PLYOMETRIC_IMPACT | The exercise includes jumps or other rapid stretch-shortening of the legs. | piriformis_syndrome, gluteal_tendinopathy, patellofemoral_pain |
| HEAVY_EXTERNAL_LOAD | The exercise is programmed with a heavy barbell, dumbbell, machine, band or carried load. Never apply it to bodyweight-only work. | piriformis_syndrome, gluteal_tendinopathy, nonspecific_low_back_pain, rotator_cuff_related_shoulder_pain, lateral_elbow_tendinopathy |
| SINGLE_LEG_HIP_CONTROL | One leg carries most of the body weight (single-leg squat, step-down, balance hold), or the loaded hip moves toward or across the midline. | piriformis_syndrome, gluteal_tendinopathy |
| CROSS_BODY_HIP_STRETCH | A stretch pulls the hip into adduction, with or without flexion and rotation, such as a knee drawn across the body or an iliotibial band stretch. | piriformis_syndrome, gluteal_tendinopathy |
| NERVE_TENSIONING | A drill or held position lengthens a whole nerve path at once. Rhythmic sliders do not get this tag. | piriformis_syndrome, carpal_tunnel_syndrome |
| HAMSTRING_LENGTH_LOAD | The exercise is a hamstring stretch, a sustained forward trunk bend, or repeated eccentric hamstring loading. | piriformis_syndrome |
| SIDE_LYING_HIP_COMPRESSION | The exercise is done lying on one side, with body weight on the lower hip and the upper hip able to drop into adduction. Clamshells get this tag. | gluteal_tendinopathy |
| TRUNK_FLEXION_UNDER_LOAD | The lower back rounds while lifting or holding an external load. Unloaded curl-ups and cat-cow do not get this tag. | nonspecific_low_back_pain |
| WRIST_EXTENSOR_WORK | The wrist extensors work against resistance, or the wrist or elbow repeatedly flexes and extends as the main task. | lateral_elbow_tendinopathy |
| FOREARM_TWIST_OR_PRONATED_LIFT | The forearm repeatedly twists, or a load is lifted palm-down. | lateral_elbow_tendinopathy |
| WRIST_END_RANGE_LOAD | The wrist holds or bears load near end-range extension or flexion, as in flat-palm push-ups, planks and front-rack holds. | carpal_tunnel_syndrome |
| HAND_GRIP_LOAD | The hand does forceful gripping for many repetitions, hard or sustained pinching or fingertip loading, or holds a vibrating tool or grip. | carpal_tunnel_syndrome |
| DEEP_KNEE_FLEXION_LOAD | A weight-bearing squat pattern goes deeper than about 45 degrees of knee flexion. | patellofemoral_pain |
| END_RANGE_KNEE_EXTENSION_LOAD | Resisted seated knee extension works the arc from about 45 to 0 degrees. | patellofemoral_pain |

## 3. Condition restrictions

The tag applies only where its condition's item says so. Most items are `modify`, and `avoid` is marked below. Ten of the 46 items are not movement properties, so they are listed as rules and need another mechanism, such as a symptom prompt, a safety gate or education. The "Basis" column gives each file's `evidenceStrength`. "Expert" and "biomech." mark evidence that was only expert opinion or biomechanical.

### piriformis_syndrome (7 expert, 1 observational, 1 biomech.)

| Restriction | Demand | Basis |
|---|---|---|
| Prolonged sitting | PROLONGED_SITTING | Expert |
| Cross-legged sitting held long (avoid) | PROLONGED_SITTING | Expert |
| Slumped sacral sitting (avoid) | PROLONGED_SITTING | Expert |
| High-volume running, walking, cycling, jumping when provoking | SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT | Expert |
| Heavy lifting | HEAVY_EXTERNAL_LOAD | Expert |
| Inward knee collapse in single-leg tasks | SINGLE_LEG_HIP_CONTROL | Observational, one case report |
| Forceful end-range hip stretching that reproduces symptoms | CROSS_BODY_HIP_STRETCH | Expert |
| Sciatic nerve tensioning drills | NERVE_TENSIONING | Biomech., secondary reporting |
| Forward flexion, hamstring stretching and eccentric hamstring work with pain near the sit bone | HAMSTRING_LENGTH_LOAD | Expert, drawn from a related condition |

### gluteal_tendinopathy (all 9 expert)

| Restriction | Demand | Basis |
|---|---|---|
| Stretches pulling the hip into adduction (avoid) | CROSS_BODY_HIP_STRETCH | Expert |
| Loaded exercise across the midline, or pelvic drop | SINGLE_LEG_HIP_CONTROL | Expert |
| Heavy single-leg squats and lifts | SINGLE_LEG_HIP_CONTROL, HEAVY_EXTERNAL_LOAD | Expert |
| Lying on the painful side | SIDE_LYING_HIP_COMPRESSION | Expert |
| Clamshell | SIDE_LYING_HIP_COMPRESSION | Expert |
| Long, tempo or hill running, plyometrics | SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT | Expert |
| Hill walking, stairs, long walks | SUSTAINED_LOCOMOTION | Expert |
| Prolonged single-leg balancing | SINGLE_LEG_HIP_CONTROL | Expert, extrapolated to workouts |
| Abductor strengthening progressed quickly or through significant pain | Rule: progression and pain limit | Expert |

### lumbar_radiculopathy (1 expert, 3 guideline, 1 systematic review)

This condition restricts no demand tag. Its file says no opened source gave direct evidence for restricting lifting, sitting, loaded flexion or specific gym lifts, and that this absence is not evidence they are safe.

| Restriction | Handling | Basis |
|---|---|---|
| Any activity during which leg pain increases | Rule: symptom response | Expert |
| Replacing normal activity with extended bed rest | Rule: stay-active education | Guideline |
| Neural mobilisation with cauda equina lesions, cord signs or nervous system pathology (avoid) | Rule: safety gate, which also covers sliders | Systematic review |
| Fixed-direction repeated lumbar flexion or extension for everyone | Rule: direction follows symptom response, no blanket ban | Guideline |
| Mechanical traction (avoid) | Rule: clinical treatment. The sources do not assess gym hanging. | Guideline |

### nonspecific_low_back_pain (3 expert, 2 systematic review)

| Restriction | Demand | Basis |
|---|---|---|
| Prolonged bed rest or stopping ordinary activity (avoid) | Rule: stay-active education | Systematic review |
| Lumbar flexion while lifting, only where it provokes symptoms | TRUNK_FLEXION_UNDER_LOAD | Expert. The file says this must not become a blanket ban. |
| Loaded flexion with osteoporosis, long-term corticosteroid use or prior spinal fracture | TRUNK_FLEXION_UNDER_LOAD | Expert, with clinician clearance |
| Heavy spinal loading during a flare | HEAVY_EXTERNAL_LOAD | Expert, no load limit given |
| Stretching as the entire programme | Rule: programme composition | Systematic review |

### rotator_cuff_related_shoulder_pain (2 expert, 1 systematic review, 1 guideline)

| Restriction | Demand | Basis |
|---|---|---|
| Load above 5/10 pain or not recovered by next session | Rule: pain monitoring, one trial's threshold | Expert |
| Fast, high-load, high-repetition resisted work in an irritable shoulder | HEAVY_EXTERNAL_LOAD | Expert |
| Assuming higher load works better | HEAVY_EXTERNAL_LOAD | Systematic review. It does not show harm. |
| Activities that reliably provoke the pain | Rule: symptom response | Guideline, grade known only from a secondary summary |

### lateral_elbow_tendinopathy (1 guideline, 3 observational, 1 expert)

| Restriction | Demand | Basis |
|---|---|---|
| Loaded wrist extensor work at an aggravating load | WRIST_EXTENSOR_WORK | Guideline, resting largely on expert opinion by its own account |
| Repeated wrist or elbow flexion and extension | WRIST_EXTENSOR_WORK | Observational, risk-factor data |
| Repeated forearm twisting | FOREARM_TWIST_OR_PRONATED_LIFT | Observational, risk-factor data |
| Palm-down lifting | FOREARM_TWIST_OR_PRONATED_LIFT | Expert, extrapolated to gym lifts |
| Very heavy loads in the hands | HEAVY_EXTERNAL_LOAD | Observational, occupational and extrapolated |

### carpal_tunnel_syndrome (3 biomech., 2 observational)

| Restriction | Demand | Basis |
|---|---|---|
| Sustained or loaded near-end-range wrist positions | WRIST_END_RANGE_LOAD | Biomech. The exercise examples are an inference. |
| High-volume forceful gripping | HAND_GRIP_LOAD | Observational, occupational |
| Hard or sustained pinching and fingertip loading | HAND_GRIP_LOAD | Biomech., mostly healthy volunteers |
| Hand-transmitted vibration | HAND_GRIP_LOAD | Observational, mixed results |
| Strong nerve tensioning | NERVE_TENSIONING | Biomech., cadaver data |

### patellofemoral_pain (2 biomech., 1 observational, 1 guideline)

| Restriction | Demand | Basis |
|---|---|---|
| Squatting beyond about 45 degrees | DEEP_KNEE_FLEXION_LOAD | Biomech., 10 healthy people, modelled stress |
| Resisted seated knee extension from about 45 to 0 degrees | END_RANGE_KNEE_EXTENSION_LOAD | Biomech., same study |
| Activities that reproduce the pain | DEEP_KNEE_FLEXION_LOAD, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, PROLONGED_SITTING | Observational, adolescent cohort plus reviews |
| High running load | SUSTAINED_LOCOMOTION | Guideline, via a summary, listed as an option |

## 4. Joint label mapping

The six labels are self-reported body regions, not diagnoses. Nobody selecting one has been shown to have the conditions below. Each list is derived by taking the union of the demand tags restricted by the researched conditions for that region, so it inherits their limits. A label's person may have a different problem, and condition red flags and rules do not transfer automatically.

| Label | Derived from | Restricted demands |
|---|---|---|
| SHOULDER_SENSITIVE | rotator_cuff_related_shoulder_pain | HEAVY_EXTERNAL_LOAD |
| ELBOW_SENSITIVE | lateral_elbow_tendinopathy | WRIST_EXTENSOR_WORK, FOREARM_TWIST_OR_PRONATED_LIFT, HEAVY_EXTERNAL_LOAD |
| WRIST_SENSITIVE | carpal_tunnel_syndrome | WRIST_END_RANGE_LOAD, HAND_GRIP_LOAD, NERVE_TENSIONING |
| LOWER_BACK_SENSITIVE | lumbar_radiculopathy, nonspecific_low_back_pain | TRUNK_FLEXION_UNDER_LOAD, HEAVY_EXTERNAL_LOAD |
| HIP_SENSITIVE | piriformis_syndrome, gluteal_tendinopathy | PROLONGED_SITTING, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, HEAVY_EXTERNAL_LOAD, SINGLE_LEG_HIP_CONTROL, CROSS_BODY_HIP_STRETCH, NERVE_TENSIONING, HAMSTRING_LENGTH_LOAD, SIDE_LYING_HIP_COMPRESSION |
| KNEE_SENSITIVE | patellofemoral_pain | PROLONGED_SITTING, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, DEEP_KNEE_FLEXION_LOAD, END_RANGE_KNEE_EXTENSION_LOAD |

Limits of the derivation:

- **Single sources:** shoulder, elbow, wrist and knee each rest on one condition.
- **Lower back:** both tags come from nonspecific_low_back_pain, which rejects a blanket flexion ban. lumbar_radiculopathy adds nothing.
- **Hip:** this list is much the broadest because it is a union. The tags both hip conditions share are SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, HEAVY_EXTERNAL_LOAD, SINGLE_LEG_HIP_CONTROL and CROSS_BODY_HIP_STRETCH.

## 5. Routines

No routine below is a treatment prescription. Doses are copied from the JSON and most come from trial or protocol programmes, not validated stand-alone use.

Selection rules:

1. Keep only items with `bodyweightOrHold: true` that are rep or hold exercises. This excludes walking.
2. Require a numeric rep or hold dose, unless a low or moderate grade item was named in a trial, in which case it may be listed with the missing dose marked.
3. Leave out items the file marks as later-stage progressions, or items whose own caution argues against routine use.
4. In a very_low condition, keep only items labelled observational or stronger.
5. Rank by evidence, then prefer catalog entries, up to 5.

Neither very_low condition came out empty, because each keeps at least one supported item. Items marked "Dose missing" cannot run until the owner decides (see section 8).

### piriformis_syndrome (3)

| Exercise | Source | Type | Dose |
|---|---|---|---|
| Piriformis stretch, lying, knee drawn toward the opposite shoulder | New | Timed hold | 10 reps, 30 s; set count unclear in the source |
| Glute bridge | Catalog: glute-bridge | Bodyweight rep | 3 x 15 |
| Clamshell | Catalog: clamshell | Bodyweight rep | 3 x 15; variant needs a decision (see section 8) |

Held out as expert opinion or lacking a dose: FAIR-position stretch, side-lying hip abduction, sciatic nerve glide, hip circumduction.

### gluteal_tendinopathy (5)

| Exercise | Source | Type | Dose |
|---|---|---|---|
| Side-lying isometric hip abduction hold, painful side up | New | Timed hold | 1 x 6, 30 s, daily |
| Standing weight-bearing isometric gluteal contraction | New | Timed hold | 3 x 10, 6 s, daily |
| Side-lying hip abduction, painful side up | Catalog: side-lying-hip-abduction | Bodyweight rep | 3 x 10, daily; catalog lacks the setup cues |
| Double-leg glute bridge | Catalog: glute-bridge | Bodyweight rep | 1 x 10, daily |
| Bodyweight double-leg squat | Catalog: bodyweight-squat | Bodyweight rep | 1 x 10, daily |

Held out: standing isometric hip abduction (only the hold time is known), standing hip abduction slide, bodyweight side-stepping, and the later-stage offset bridge and offset squat. Band side slide needs a band.

### lumbar_radiculopathy (2)

| Exercise | Source | Type | Dose |
|---|---|---|---|
| Seated slump slider | New | Bodyweight rep | 3 x 15, 3 days per week |
| Side-lying straight-leg-raise slider | New | Bodyweight rep | 3 x 10, 3 days per week |

The file says self-directed use without clinician screening is not established, and the neural mobilisation safety gate must apply. Bird dog, side plank, dead bug and cat-cow are catalog items with no dose and no opened source naming them. Walking has no dose and is not a rep or hold exercise. Repeated direction-guided lumbar movement cannot be replicated by a fixed routine.

### nonspecific_low_back_pain (5, all dose missing)

| Exercise | Source | Type | Dose |
|---|---|---|---|
| Forearm plank | Catalog: plank | Timed hold | Dose missing; hold time not reported |
| Side plank | Catalog: side-plank | Timed hold | Dose missing; may be too hard as a first choice |
| Curl-up | New | Bodyweight rep | Dose missing; repetitions not reported |
| Cat-cow | Catalog: cat-cow-stretch | Bodyweight rep | Dose missing; repetitions not reported |
| Hamstring stretch | Catalog: hamstring-stretch | Timed hold | Dose missing; complement only, per file |

Trial sessions ran 3 times per week. Held out by the limit of 5: child's pose, pelvic tilt, trunk rotation.

### rotator_cuff_related_shoulder_pain (1)

| Exercise | Source | Type | Dose |
|---|---|---|---|
| Posterior shoulder stretch (cross-body) | Catalog: cross-body-shoulder-stretch | Timed hold | 3 x 30 s, twice daily |

Held out: the numerically dosed eccentric cuff work needs a band or weight, and the two other items are expert opinion with no dose.

### lateral_elbow_tendinopathy (1, dose missing)

| Exercise | Source | Type | Dose |
|---|---|---|---|
| Isometric wrist extension hold | New | Timed hold | 3 x 15 suggested; hold time missing, and the file says not to invent one |

The trial used a water container with a handle as resistance, so this is not truly no-equipment. Catalog wrist extension and the rubber-bar eccentric exercise are excluded because they use equipment, and they carry the condition's main evidence.

### carpal_tunnel_syndrome (1)

| Exercise | Source | Type | Dose |
|---|---|---|---|
| Tendon gliding sequence | New | Timed hold | 3 x 5, 7 s, 3 times per week |

Held out: median nerve gliding, because its own record calls it the lowest-confidence candidate with no routine recommendation. The wrist stretch, active wrist bend, finger bend and ball squeeze are expert opinion, and the file says pressure data conflict with the bend and squeeze.

### patellofemoral_pain (4)

| Exercise | Source | Type | Dose |
|---|---|---|---|
| Side-lying hip abduction | Catalog: side-lying-hip-abduction | Bodyweight rep | 3 x 10; the bodyweight version was not itself tested |
| Isometric quadriceps setting | New | Bodyweight rep | 3 x 10; hold time not reported |
| Straight leg raise | New | Bodyweight rep | 3 x 10 |
| Partial-range bodyweight squat | Catalog: bodyweight-squat | Bodyweight rep | 3 x 10 at one-quarter depth; catalog entry is a full squat |

Held out as later-stage: shallow wall sit and step-down. Hamstring stretch has no per-hold dose. The remaining items need a band or machine.

## 6. New exercises

**Figure-four stretch: not in the JSON.** No file contains that name, in the JSON or the Markdown, and no verification pass mentions removing it. It cannot be placed first as a surviving item. The piriformis stretch is listed first as the nearest surviving item, but this document does not claim that the two are the same movement. The owner should decide (see section 8).

All 10 exercises needed by the proposed routines are needed by one condition each, so no count separates them.

| # | Exercise | Conditions needing it | Count |
|---:|---|---|---:|
| 1 | Piriformis stretch, lying, knee toward the opposite shoulder | piriformis_syndrome | 1 |
| 2 | Side-lying isometric hip abduction hold | gluteal_tendinopathy | 1 |
| 3 | Standing weight-bearing isometric gluteal contraction | gluteal_tendinopathy | 1 |
| 4 | Seated slump slider | lumbar_radiculopathy | 1 |
| 5 | Side-lying straight-leg-raise slider | lumbar_radiculopathy | 1 |
| 6 | Curl-up | nonspecific_low_back_pain | 1 |
| 7 | Isometric wrist extension hold | lateral_elbow_tendinopathy | 1 |
| 8 | Tendon gliding sequence | carpal_tunnel_syndrome | 1 |
| 9 | Isometric quadriceps setting | patellofemoral_pain | 1 |
| 10 | Straight leg raise | patellofemoral_pain | 1 |

The files list 34 `catalog: new` candidates in total. The other 24 are not needed by a proposed routine:

- **piriformis_syndrome:** FAIR-position stretch, sciatic nerve glide, hip circumduction.
- **gluteal_tendinopathy:** standing isometric hip abduction, standing hip abduction slide, offset bridge, offset squat, bodyweight side-stepping, band side slide.
- **lumbar_radiculopathy:** repeated direction-guided lumbar movement.
- **nonspecific_low_back_pain:** pelvic tilt, trunk rotation.
- **rotator_cuff_related_shoulder_pain:** eccentric cuff strengthening, arm-supported isometric hold, short-lever shoulder flexion.
- **lateral_elbow_tendinopathy:** eccentric wrist extension with rubber bar, shoulder and scapular stabilizer training.
- **carpal_tunnel_syndrome:** median nerve gliding, wrist flexor and extensor stretch, active wrist bend, finger bend, rubber ball squeeze.
- **patellofemoral_pain:** seated banded hip external rotation, terminal knee extension with band.

Catalog entries reused across routines: side-lying-hip-abduction (gluteal_tendinopathy, patellofemoral_pain), glute-bridge (piriformis_syndrome, gluteal_tendinopathy), bodyweight-squat (gluteal_tendinopathy, patellofemoral_pain).

## 7. Scale decision input

Criterion: at least six of eight have a verified restriction list, and the last two conditions processed needed little or no new vocabulary.

**Result: FAIL.**

| Clause | Numbers | Result |
|---|---|---|
| At least 6 of 8 have a verified restriction list | 8 of 8 have a non-empty list after verification. 7 of 8 restrict at least one demand tag; lumbar_radiculopathy restricts none. | Pass |
| Last two needed little or no new vocabulary | carpal_tunnel_syndrome added 2 and patellofemoral_pain added 2. That is 4 of 16 values (25%). The conditions before them were not trending to zero either. | Fail |

New values by processing order were 8, 1, 0, 1, 0, 2, 2, 2.

The criterion does not define "little". This consolidation read it as at most 1 new value per condition. The result is sensitive to that reading and to merging: merging the two knee tags would give patellofemoral_pain 1, but carpal_tunnel_syndrome would still need 2 unless wrist position and grip were also merged, which would blur two different mechanisms.

Vocabulary growth looks tied to body region and to how much a file restricts. The elbow, wrist and knee each added 2 on first visit. The first shoulder and first back conditions added 0, because their files support few or no movement restrictions. A new region may therefore add more values, but eight conditions cannot test that.

## 8. Open questions and weak spots

**Decisions needed**

1. **Figure-four stretch.** It is absent from the research. Should the piriformis stretch or FAIR-position stretch stand in for it, or does it need its own research?
2. **Missing doses.** Six proposed entries lack a dose or hold time: all 5 for nonspecific_low_back_pain and the elbow hold. The moderate-grade condition has no runnable routine. Quadriceps setting also lacks a hold time. Someone must set these or drop them, and that choice would not be evidence-based.
3. **How `modify` works.** 40 of the 46 items are `modify`, and many apply only when symptoms are provoked. Treating them as hard exclusions would ban far more than the sources support. Treating them as cues would make many tags have no planning effect.
4. **Repeated holds.** Several doses are reps with a hold, such as 3 x 10 with 6 s. The two allowed types fit these poorly, so the routine model needs a decision.
5. **Rules outside the tags.** 10 of 46 restrictions cannot be expressed as demands, including all 5 for lumbar_radiculopathy. They need symptom prompts, safety gates or education text.

**Conflicts between conditions**

6. **The main hip conflict.** gluteal_tendinopathy says to avoid piriformis stretches that draw the knee across the body, while piriformis_syndrome proposes such a stretch. The diagnoses are hard to separate, and both feed HIP_SENSITIVE.
7. **Clamshell.** piriformis_syndrome proposes it; gluteal_tendinopathy says to prefer another abductor exercise. The piriformis_syndrome file separately warns that it may reproduce symptoms.
8. **Tags against a condition's own routine.** The piriformis stretch carries CROSS_BODY_HIP_STRETCH. The two gluteal side-lying exercises carry SIDE_LYING_HIP_COMPRESSION but are done painful side up, and bilateral symptoms prevent this. The engine needs an exception mechanism or finer tags.
9. **Nerve tensioning.** It is a restricted tag for piriformis_syndrome and carpal_tunnel_syndrome, yet lumbar_radiculopathy verification removed the same restriction because its source does not advise it.

**Thin spots in the evidence**

10. **Mechanism doubts.** One piriformis_syndrome hypothesis is overstretching, which would make its stretch unhelpful. McMillan 2022 found tendon-specific exercise no better than sham when both groups received education, so the gluteal routine's exercises may not be the active part.
11. **Restrictions are mostly opinion.** Every gluteal_tendinopathy restriction and 7 of 9 for piriformis_syndrome are expert opinion. Carpal tunnel restrictions are healthy-volunteer, cadaver or occupational data. Those exercise examples are inference.
12. **Absent restrictions are not clearance.** SHOULDER_SENSITIVE restricts only heavy load, because no source supported an overhead ban. LOWER_BACK_SENSITIVE is similarly narrow. An empty tag must not read as "safe".
13. **Sparse routines.** Rotator cuff, elbow and carpal tunnel get one entry each, and the elbow entry cannot run. The bodyweight limit excludes the elbow's main evidence.
14. **Evidence labels can overstate.** Many `rct` labels describe one component inside a programme. The median nerve glide is labelled `systematic_review`, but that review's confidence interval includes no effect. The elbow load restriction is labelled `guideline`, but its rationale says it rests largely on expert opinion.
15. **Grades are judgement.** The very_low grade for piriformis_syndrome is explicitly not a formal GRADE assessment.
16. **Secondary sources.** Guideline content for carpal tunnel, shoulder and part of low back rests on summaries such as Physiotutors, iatroX and POGO Physio. These need checking against originals.
17. **NICE NG59 reads inconsistently.** lumbar_radiculopathy cites it directly with a 29 July 2026 update, while the piriformis_syndrome and nonspecific_low_back_pain files say NICE returned errors and the update is unverified.

**Data and safety quality**

18. **`bodyweightOrHold` is unreliable.** It is true for the wrist hold (water container), ball squeeze (ball) and abduction slide (sliding surface), and for walking. Equipment should be checked before build.
19. **Catalog ambiguity.** The clamshell's pictured variant uses a band while the entry says bodyweight. side-lying-hip-abduction is near-identical to side-lying-leg-raise. wrist-extension is an unreviewed draft. Partial squat needs a depth limit that the catalog does not carry.
20. **Doses differ by condition for the same exercise.** Glute bridge is 3 x 15 or 1 x 10, so dose must belong to the routine, not the exercise.
21. **Red flags are not ready for users.** Some use clinician referral wording, UK-specific actions such as 999, or review-written actions with no source triage rule. Some, such as suspected infection, are not something a person can self-identify. Several conditions' flag lists are not validated screening tools, and the app cannot screen for serious pathology.
22. **Misleading ids.** Source ids cochrane_2022 and dahm2022 are dated 2012 and 2010 in their citations. The older review may miss later trials.
23. **Tags are untested.** The 16 values have not been applied to the catalog, and two people tagging independently might disagree. A trial tagging pass would test the definitions. HEAVY_EXTERNAL_LOAD is the least precise, because the sources give no usable load threshold, so the owner needs a catalog rule for it.
