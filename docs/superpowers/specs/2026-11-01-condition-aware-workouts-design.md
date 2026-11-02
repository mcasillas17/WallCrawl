# Condition-aware personalized workouts

Date: 2026-11-01. Status: approved by the owner in chat on 2026-11-01.
Repository baseline: `da8b24b51`. This document does not approve clinical content.

## Intent and agreed direction

WallCrawl should account for selected injuries and musculoskeletal conditions when
creating an ordinary personalized workout: select exercises compatible with the
documented restrictions, modify prescriptions where supported, and include relevant
supportive exercises or stretches. The goal is to help people continue training
without knowingly prescribing work incompatible with their condition. No system can
guarantee that an exercise will never aggravate an individual's symptoms.

The owner chose condition-aware rules within the existing deterministic planner.
The visible condition list must contain only conditions the app actually supports
through exercise mappings and planning rules. The owner also requires **new PNG
artwork for new exercises**. Research should use multiple agents and cover roughly
50–100 candidate conditions, with piriformis syndrome as the motivating example.

This is a project spanning research, catalog content, artwork, planner behavior,
profile persistence, and validation. It is not a separate rehabilitation app.

## Approaches considered

| Approach | Tradeoff | Decision |
| --- | --- | --- |
| Curated exercise pools for each condition | Straightforward initial audit; limits variety and duplicates work across conditions, equipment, and combinations | Not selected |
| Condition-aware rules in the existing planner | Reuses personalization and validation; requires explicit condition/exercise mappings, prescription rules, and conflict handling | Selected by the owner |

The following defaults were **author proposals included in the approved design**:
start with 50 research records; prepare piriformis first to establish the
dossier format; integrate supportive work into normal sessions; require qualified
clinical review before releasing condition-specific therapeutic prescriptions; and
produce PNG masters in both existing demonstrator variants with SVG delivery.

## Goals and boundaries

- Research every candidate with a reproducible search record; a finding of
  insufficient evidence is a valid result, not permission to invent a protocol.
- Reuse existing exercises when the studied movement and execution actually match.
  Add an exercise only when the catalog cannot represent the needed variation.
- Preserve equipment, goals, session duration, user exclusions, movement limits,
  program behavior, and the app's local, deterministic operation.
- Give each selected condition a real effect on eligibility or prescriptions;
  explain relevant adaptations in plain English and Spanish.
- Support multiple selected conditions only where their combined constraints and
  prescriptions can be resolved without guessing.
- Include complete PNG artwork and app-ready visual assets for every new exercise.

Out of scope: diagnosis from symptoms, medication/procedure advice, autonomous
postoperative rehabilitation, return-to-sport clearance, a general medical
encyclopedia, runtime AI, online health-data collection, and promises of healing or
injury prevention. Research may describe those treatments to establish boundaries,
but they do not become app instructions. The research inventory is not a prevalence
ranking and is not the app's supported-condition list.

## Existing foundations and constraints

The app has 302 catalog exercises, including 14 stretches. Production eligibility
uses accepted reviewed metadata, explicit equipment/exercise exclusions, seven
movement capabilities, six joint sensitivities, and a low-impact restriction.
All 182 currently accepted metadata records have empty joint-clearance lists;
choosing any joint sensitivity therefore removes all automatic candidates today.
That existing refusal must not be bypassed by attaching a condition label.

The catalog parser currently prohibits strength `reviewedMetadata` on stretches;
automatic strength selection excludes them. Manual templates can already represent
duration-based stretches, but their generic defaults are not clinical prescriptions.
Existing AI acceptance is categorical product acceptance, not clinical validation.

The planner already has context assembly, prescription validation, whole-session
validation, stale-recommendation checks, versioned decision records, pain-stop
logging, and progression controls. Manual templates currently bypass automatic
eligibility. These are the integration points, rather than a second workout engine.

References: [reviewed metadata](../../reviewed-exercise-metadata.md),
[illustration workflow](../../exercise-illustration-variants.md),
[privacy and archive contract](../../privacy.md), and
[training evidence boundaries](../../research/2026-08-29-training-science-evidence-review.md).

## Research deliverable and review

Begin with a piriformis syndrome dossier and use its review to stabilize the
extraction format. Then complete the other candidate dossiers in regional batches.
Use up to three research agents concurrently and a coordinator for shared
terminology, deduplication, and synthesis. A separate verification pass checks
citations, population boundaries, doses, and exercise mappings. Agents do not
promote their own findings to clinical approval.

For every condition, preserve:

1. Exact clinical label, aliases and distinctions from neighboring diagnoses;
   covered population, stage, operative status, exclusions, and known uncertainty.
2. Search date, queries/sources, inclusion decisions, and access limitations.
3. Source title, DOI/PMID when available, direct URL, publication/version status,
   exact supporting section, and correction/supersession/retraction checks.
4. Guideline recommendation or study design, population, comparator,
   co-interventions, outcomes, follow-up, uncertainty, harms reporting, and limits.
5. Exact studied/recommended exercise execution, dose, frequency, supervision,
   progression and stopping rules. Missing information stays **not reported**.
6. Ordinary-training implications, supportive exercise candidates, and evidence
   linking each proposed rule to its applicable population. Distinguish source
   statements from app-policy interpretations and indirect extrapolation.
7. Existing catalog IDs or a specific new-variation request, including equipment,
   movement/position requirements and the artwork needed to communicate it.
8. Disposition: research-only, unresolved, eligible for independent content review,
   or accepted under the explicitly recorded review policy.

Prioritize current final clinical guidelines, systematic reviews, and relevant
primary studies. Retrieve accessible full texts and supplements where needed;
abstract-only access cannot establish an unreported exercise protocol. Preserve
links and factual extraction rather than republishing copyrighted articles.
Do not mistake trial exclusion criteria for universal contraindications, lack of
reported harms for established safety, or agent agreement for clinical validation.

The approved release gate requires clinical review for the mapping, prescription, symptom-response, and
escalation rules before those are released as condition-specific therapeutic
guidance. No reviewer is currently identified. Research and draft content can be
completed without that reviewer; unreviewed therapeutic mappings remain unreleased.
Owner approval of this design or a PR is not a substitute for that content review.

### Piriformis pilot evidence boundary

Preserve piriformis syndrome as the selected label; it is not interchangeable with
every cause of deep gluteal pain or all deep gluteal syndrome. A diagnosis alone does
not establish the suitable stretch, dose, or range of motion for an individual.

A [2024 randomized trial](https://link.springer.com/article/10.1186/s13102-023-00802-4)
included 45 adults with low-back pain and clinically assessed deep gluteal syndrome.
All groups received heat/TENS; added stretching improved disability versus
compression, without a between-group pain benefit. A
[2023 observational study](https://pmc.ncbi.nlm.nih.gov/articles/PMC10508642/)
combined stretching, bridging, another hip exercise and activity advice; it cannot
establish the independent benefit of each component. These are candidate-content
sources, not a validated default WallCrawl routine. Exact variants must be checked
before equating a study exercise with an existing catalog entry.

## Supported-condition contract

The research library and runtime supported-condition catalog are distinct.
A condition becomes selectable only when it has a versioned, reviewed support
record: applicable population/stage, required user inputs, compatible exercise
mappings, any supported prescription modifications, required restrictions,
symptom-response rules, and sufficient released content/artwork to produce a useful
workout under the advertised scope.

Not every supported condition needs a stretch. A stretch is included only when its
specific role and prescription are supported. A catalog exercise's mere existence
never makes a condition supported, and exercise acceptance for ordinary strength
training does not establish condition-specific suitability.

Selection records the condition and only the structured context its support record
requires, such as affected side, applicable stage, known aggravating movements and
documented restrictions. Preserve unknown answers. Do not infer a diagnosis,
recovery stage, clinician clearance, or machine-readable restriction from free text.
The exact prompts follow the research findings, rather than a universal medical
questionnaire. The initial supported population is adults in nonoperative care;
acute/surgical cases remain outside executable support until separately reviewed.

Users can select, update, and remove active conditions. Changes advance profile
revision and invalidate generated recommendations. An update that withdraws a
previously supported record preserves the user's stored selection and explains
that current generation cannot interpret it; it must not silently drop the constraint.

## Planner behavior

1. Build the normal context plus active condition support versions and required
   structured context. Incomplete or unsupported required context produces an
   explanatory unavailable result instead of an invented default.
2. Apply existing explicit exclusions and condition restrictions before ranking.
   A recommendation for one condition cannot override another condition's
   restriction, equipment requirements, or an independently selected joint limit.
   Selecting a condition does not automatically set or clear legacy joint flags.
3. Select ordinary strength exercises from candidates whose relevant condition
   requirements are established. Do not infer compatibility only from muscle names
   or from an exercise's absence on a prohibited list.
4. Apply only reviewed condition-specific prescription modifications. Intersect
   compatible limits; if required doses or execution instructions conflict, report
   the unresolved combination. Do not average conflicting clinical prescriptions.
5. Include appropriate supportive exercises/stretches within the normal session and
   its duration budget. Store their purpose and prescription separately from
   strength focus and dose accounting. Do not use stretching to satisfy a strength
   split, award strength-set credit, or inherit generic five-second progression.
6. Account for repeated supportive exercises across conditions once, with all
   relevant provenance. Only reuse a prescription when the actual instructions and
   schedules are compatible. Do not combine duplicate doses mechanically.
7. Validate the full workout again at start using current profile and policy
   versions. No repair may weaken a condition restriction to fill an empty workout.

Condition-targeted work uses its own reviewed dose and schedule. If an intervention
requires a frequency, supervision, or monitoring the normal-workout product cannot
provide, it remains research-only; the planner does not claim that occasional
insertion delivers that protocol. Regular strength progression continues only
within established condition limits. Supportive-work progression requires an
explicit reviewed rule; otherwise its prescription does not automatically increase.

Preserve existing pain-stop logging and prevention of automatic advancement.
Condition-specific response monitoring must use reviewed prompts and rules; no
universal pain threshold or automatic recovery date is introduced. A stopped item
is not automatically offered again or advanced until its required reassessment
inputs are satisfied. Escalation copy must come from reviewed guidance.

Apply this contract to generated ordinary and program sessions. The approved approach for manual
templates: retain editing and saved content, but check active condition compatibility
before starting; known conflicts or unassessed items require modification before
the app presents the session as condition-aware. Do not silently rewrite templates.
The implementation plan must make the bypass behavior explicit rather than claim
that an unchecked manual workout has passed these rules.

Profile changes do not rewrite an active session or historical prescription. With
an active session, changing condition context requires ending/cancelling that
session before starting a newly generated one; recording already-performed work
and finishing/cancelling remain available. Historical context stays frozen.

## Catalog and PNG artwork

Keep the pinned upstream catalog/provenance intact. New WallCrawl-authored exercises
need a source-aware supplement integrated into the existing catalog and validators;
do not attribute new IDs or images to the old upstream snapshot. Reuse the existing
exercise types, localized descriptions, equipment model, and rendering components.
Supportive metadata is a distinct contract from strength reviewed metadata.

Each new exercise or genuinely different execution variant delivers:

- Original PNG masters matching the current transparent, white-line illustration
  style, with three coherent frame slots for each male/female demonstrator variant.
- Correct setup, support/contact points, equipment, side and intended motion. Keep
  camera, body proportions, equipment and framing consistent across frames.
- Faithful representation of static holds: do not invent bouncing or a repeated
  range of motion to make a stretch look animated. Reusing a held pose across frame
  slots is acceptable where the prescribed execution requires it.
- Derived path-only SVGs with a `0 0 512 512` viewBox for the existing app pipeline;
  PNGs remain actual retained deliverables, not discarded intermediates.
- A contact sheet and playback preview, visual review findings, real generation
  provenance, source/master/derived hashes, and accurate attribution/licensing.

The existing app ships SVGs and plays frames 1–2–3–2. This proposal preserves that
runtime rather than adding a PNG renderer. New local exercises need their own
complete canonical visual sequence and fallback; they cannot rely on a nonexistent
upstream original. Extend source-aware packaging without fabricating old pilot
ledgers or treating AI visual review as qualified movement review.

Only create images after the exact exercise variation and movement instructions
are settled. Use the existing image-generation workflow at that stage, then inspect
every image and the sequence. Prompts and masters stay in the retained artwork
workspace; app-ready assets and compact provenance remain available in a normal
clone. Locate the retained production workspace before trying to regenerate old
bundles. If unavailable, create a reproducible path for new assets that preserves
the committed old bundles and their provenance. Record output sizes and APK growth.

## Persistence, privacy and affected areas

Keep diagnosis selections and symptom/restriction context local. They are sensitive
profile data and must be covered explicitly by export/restore/delete behavior and
the readable-export notice. Add versioned Room/archive migrations that preserve
older backups without inventing condition history. Unknown future constraint values
must never be dropped silently into unrestricted planning.

Recommendation identities/receipts include the condition support and prescription
policy versions and required context identity. Historical views show what governed
the original workout without re-evaluating it under today's rules. Store only the
structured context necessary to explain/validate decisions, not duplicated free-text
medical histories.

The implementation plan must reach profile/onboarding, domain records, Room and
archive codecs, catalog supplement/import/parser, localization, illustration
provider/packager, context assembly, eligibility, prescriptions, strength/supportive
accounting, progression, whole-session validation, start freshness, program and
manual-template entry points, and historical decision explanations.

## Acceptance and sequencing

1. **Research:** coordinator completes the piriformis dossier and shared format,
   then regional agents complete the initial 50 records. Deliver the source index,
   evidence gaps, exercise reuse/addition list and proposed rule mappings. Expand
   toward 100 only when additional distinct candidates justify the work.
2. **Content review:** independent evidence verification and the proposed qualified
   clinical review determine which mappings are releasable. Unsupported records
   stay outside the selectable list. Clinical review is a content-release gate,
   not a blocker on gathering research or producing clearly marked drafts.
3. **Implementation:** after this design is approved, prepare an implementation
   plan. Research must settle executable prescriptions and required inputs before
   writing their production rules. Integrate the planner and persistence contracts.
4. **Artwork:** after an exercise variation is settled, generate and inspect its
   PNGs, derive app assets and verify packaging/provenance. Every new released
   exercise must have complete artwork and English/Spanish guidance.
5. **Validation:** test representative supported, conflicting, missing-context,
   no-candidate, pain-stop, stale-plan, and withdrawn-support scenarios. Verify
   manual/program entry points, progression boundaries, duplicate supportive doses,
   duration budgets, migration/archive round trips and history preservation.
   Inspect artwork at actual app sizes in both themes and both variants, including
   static-hold playback and missing/invalid-asset fallback.

No injury-free guarantee is an acceptance criterion. Demonstrable adherence to the
reviewed restrictions, honest explanations, reproducibility, and refusal to invent
missing clinical rules are acceptance criteria. Existing profiles with no selected
conditions must retain their existing planning behavior.

The builder chooses exact classes/files, record serialization and test allocation
within these contracts, and may reduce implementation detail where reuse is enough.
Condition content and artwork cannot be substituted by generic placeholders at
release. Owner approval covers this design, including the clinical-review gate and
the PNG-master/SVG-delivery interpretation; it does not approve clinical content.

## Initial research inventory: 50 candidates, none approved by this list

These are distinct research records, not a promise that all will become supported.
Symptom presentations require careful definition; surgery, severity, side and stage
are attributes, not extra records used to inflate the count.

| Region | Candidate records |
| --- | --- |
| Spine (7) | Chronic primary low-back pain; lumbar radicular pain; lumbar spinal stenosis; nonspecific neck pain; cervical radicular pain; whiplash-associated disorder; adult degenerative scoliosis |
| Shoulder (6) | Rotator cuff tendinopathy; full-thickness rotator cuff tear; adhesive capsulitis; glenohumeral osteoarthritis; shoulder instability; acromioclavicular osteoarthritis |
| Elbow, wrist, hand (8) | Lateral elbow tendinopathy; medial elbow tendinopathy; distal biceps tendinopathy; carpal tunnel syndrome; ulnar neuropathy at the elbow; de Quervain tenosynovitis; thumb carpometacarpal osteoarthritis; finger interphalangeal osteoarthritis |
| Hip, groin, gluteal (7) | Hip osteoarthritis; gluteal tendinopathy; femoroacetabular impingement syndrome; adductor-related groin pain; proximal hamstring tendinopathy; iliopsoas-related groin pain; piriformis syndrome |
| Knee (7) | Knee osteoarthritis; patellofemoral pain; patellar tendinopathy; iliotibial-band-related lateral knee pain; degenerative meniscal lesion; anterior cruciate ligament injury; medial collateral ligament sprain |
| Lower leg, ankle, foot (10) | Midportion Achilles tendinopathy; insertional Achilles tendinopathy; plantar heel pain compatible with plantar fasciitis; lateral ankle sprain; chronic ankle instability; posterior tibial tendinopathy; peroneal tendinopathy; medial tibial stress syndrome; lower-limb bone stress injury; first metatarsophalangeal osteoarthritis |
| Systemic or multiple regions (5) | Osteoporosis; rheumatoid arthritis; axial spondyloarthritis; fibromyalgia; hypermobility spectrum disorder |

Evidence-rich starting anchors identified during reconnaissance include the
[WHO low-back pain guideline](https://www.who.int/publications/i/item/9789240081789),
[NICE osteoarthritis recommendations](https://www.nice.org.uk/guidance/NG226/chapter/recommendations),
[2024 Achilles guideline record](https://pubmed.ncbi.nlm.nih.gov/39611662/), and
[gluteal tendinopathy trial](https://pubmed.ncbi.nlm.nih.gov/29720374/).
These links start the dossier work; they do not verify all 50 candidates or supply
executable protocols without further extraction and applicability review.
