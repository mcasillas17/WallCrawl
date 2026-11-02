# Condition research corpus

Working evidence review for personalized workouts. This inventory contains 50 candidate conditions; it is not a list of 50 supported app conditions or a prevalence ranking. All therapeutic mappings remain drafts awaiting independent evidence verification and qualified clinical review.

Each regional evidence report links the actual medical papers and guidelines inspected, distinguishes full-text from abstract-only access, and records study populations, interventions, outcomes, harms, conflicts and limits. A study protocol is not an approved app prescription. Unavailable details stay unknown; no restriction is justified merely because another exercise helped.

Current state: linked source reports cover 50 of 50 conditions; 19 structured dossiers have been reconciled. Independent source verification and clinical review are separate gates. This is a targeted evidence review, not an exhaustive systematic review.

## Inventory and evidence links

| Condition | Detailed evidence | Structured dossier |
| --- | --- | --- |
| Chronic primary low-back pain | [Study extraction](evidence/spine.md) | Pending reconciliation |
| lumbar radicular pain | [Study extraction](evidence/spine.md) | Pending reconciliation |
| lumbar spinal stenosis | [Study extraction](evidence/spine.md) | Pending reconciliation |
| nonspecific neck pain | [Study extraction](evidence/spine.md) | Pending reconciliation |
| cervical radicular pain | [Study extraction](evidence/spine.md) | Pending reconciliation |
| whiplash-associated disorder | [Study extraction](evidence/spine.md) | Pending reconciliation |
| adult degenerative scoliosis | [Study extraction](evidence/spine.md) | Pending reconciliation |
| Rotator cuff tendinopathy | [Study extraction](evidence/shoulder.md) | Pending reconciliation |
| full-thickness rotator cuff tear | [Study extraction](evidence/shoulder.md) | Pending reconciliation |
| adhesive capsulitis | [Study extraction](evidence/shoulder.md) | Pending reconciliation |
| glenohumeral osteoarthritis | [Study extraction](evidence/shoulder.md) | Pending reconciliation |
| shoulder instability | [Study extraction](evidence/shoulder.md) | Pending reconciliation |
| acromioclavicular osteoarthritis | [Study extraction](evidence/shoulder.md) | Pending reconciliation |
| Lateral elbow tendinopathy | [Study extraction](evidence/elbow-hand.md) | Pending reconciliation |
| medial elbow tendinopathy | [Study extraction](evidence/elbow-hand.md) | Pending reconciliation |
| distal biceps tendinopathy | [Study extraction](evidence/elbow-hand.md) | Pending reconciliation |
| carpal tunnel syndrome | [Study extraction](evidence/elbow-hand.md) | Pending reconciliation |
| ulnar neuropathy at the elbow | [Study extraction](evidence/elbow-hand.md) | Pending reconciliation |
| de Quervain tenosynovitis | [Study extraction](evidence/elbow-hand.md) | Pending reconciliation |
| thumb carpometacarpal osteoarthritis | [Study extraction](evidence/elbow-hand.md) | Pending reconciliation |
| finger interphalangeal osteoarthritis | [Study extraction](evidence/elbow-hand.md) | Pending reconciliation |
| Hip osteoarthritis | [Study extraction](evidence/hip.md) | [complete](hip-osteoarthritis.json) |
| gluteal tendinopathy | [Study extraction](evidence/hip.md) | [complete](gluteal-tendinopathy.json) |
| femoroacetabular impingement syndrome | [Study extraction](evidence/hip.md) | [complete](femoroacetabular-impingement-syndrome.json) |
| adductor-related groin pain | [Study extraction](evidence/hip.md) | [complete](adductor-related-groin-pain.json) |
| proximal hamstring tendinopathy | [Study extraction](evidence/hip.md) | [complete](proximal-hamstring-tendinopathy.json) |
| iliopsoas-related groin pain | [Study extraction](evidence/hip.md) | [complete](iliopsoas-related-groin-pain.json) |
| piriformis syndrome | [Study extraction](evidence/piriformis.md) | [complete](piriformis-syndrome.json) |
| Knee osteoarthritis | [Study extraction](evidence/knee.md) | [complete](knee-osteoarthritis.json) |
| patellofemoral pain | [Study extraction](evidence/knee.md) | [complete](patellofemoral-pain.json) |
| patellar tendinopathy | [Study extraction](evidence/knee.md) | [complete](patellar-tendinopathy.json) |
| iliotibial-band-related lateral knee pain | [Study extraction](evidence/knee.md) | [complete](iliotibial-band-related-lateral-knee-pain.json) |
| degenerative meniscal lesion | [Study extraction](evidence/knee.md) | [complete](degenerative-meniscal-lesion.json) |
| anterior cruciate ligament injury | [Study extraction](evidence/knee.md) | [complete](anterior-cruciate-ligament-injury.json) |
| medial collateral ligament sprain | [Study extraction](evidence/knee.md) | [complete](medial-collateral-ligament-sprain.json) |
| Midportion Achilles tendinopathy | [Study extraction](evidence/lower-leg.md) | Pending reconciliation |
| insertional Achilles tendinopathy | [Study extraction](evidence/lower-leg.md) | Pending reconciliation |
| plantar heel pain compatible with plantar fasciitis | [Study extraction](evidence/lower-leg.md) | Pending reconciliation |
| lateral ankle sprain | [Study extraction](evidence/lower-leg.md) | Pending reconciliation |
| chronic ankle instability | [Study extraction](evidence/lower-leg.md) | Pending reconciliation |
| posterior tibial tendinopathy | [Study extraction](evidence/lower-leg.md) | Pending reconciliation |
| peroneal tendinopathy | [Study extraction](evidence/lower-leg.md) | Pending reconciliation |
| medial tibial stress syndrome | [Study extraction](evidence/lower-leg.md) | Pending reconciliation |
| lower-limb bone stress injury | [Study extraction](evidence/lower-leg.md) | Pending reconciliation |
| first metatarsophalangeal osteoarthritis | [Study extraction](evidence/lower-leg.md) | Pending reconciliation |
| Osteoporosis | [Study extraction](evidence/systemic.md) | [complete](osteoporosis.json) |
| rheumatoid arthritis | [Study extraction](evidence/systemic.md) | [complete](rheumatoid-arthritis.json) |
| axial spondyloarthritis | [Study extraction](evidence/systemic.md) | [complete](axial-spondyloarthritis.json) |
| fibromyalgia | [Study extraction](evidence/systemic.md) | [complete](fibromyalgia.json) |
| hypermobility spectrum disorder | [Study extraction](evidence/systemic.md) | [complete](hypermobility-spectrum-disorder.json) |

## Reading the evidence

- Published recommendations, measured study findings and proposed product precautions are separate. Trial exclusions describe who was studied; they do not automatically establish a contraindication.
- Studies of surgical rehabilitation, acute trauma, chronic symptoms, different diagnoses or supervised care do not automatically transfer to ordinary unsupervised workouts.
- Full text is sought through publishers, PubMed/PMC and lawful repositories. An abstract-only source cannot establish an unseen exercise protocol.
- Scientific references include negative and conflicting results. Source correction/version checks are bounded, not a guarantee that no correction exists.
- The exercise-request file tracks exact candidate executions and PNG needs; it does not add clinically supported catalog entries.
- Every proposed restriction needs a source-to-finding-to-rule trail and applicability limits. Evidence gaps can leave a condition research-only.

## Structured records

[`index.json`](index.json) is the authoritative inventory. [`exercise-requests.json`](exercise-requests.json) tracks draft exercise variants. The offline validator checks references, required fields and draft status; it does not establish scientific correctness or clinical approval.
