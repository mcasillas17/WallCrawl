# Condition evidence and clinical-review packet

**Release status: AWAITING_CLINICAL_REVIEW for all 50 conditions.** No qualified clinical review has occurred. The dossier `review` fields therefore remain `unreviewed` with no invented reviewer, date or approval. Independent AI source checking and correction rechecks are complete within the access limits below. They are a separate process from clinical approval. Nothing in this packet activates a condition in the app.

Research/access date: 2026-11-01 America/Los_Angeles (2026-11-02 UTC). This is a targeted medical-literature review, not an exhaustive systematic review.

## What the reviewer is evaluating

Use the [inventory and readable evidence reports](summary.md) to locate each condition. Each linked dossier contains the exact scope, source access, study protocol, findings, candidate exercise mappings, proposed ordinary-workout rules and unresolved gaps. The chain is condition → source and location → finding → proposed rule or mapping → applicable population and limitations. The [request register](exercise-requests.json) describes candidate execution, equipment, movement requirements and PNG needs. Trial schedules are factual source extractions, not app defaults.

A qualified review must cover the final exercise execution and illustration, individual applicability and required inputs, exact dose/schedule/progression, symptom monitoring, stopping/reassessment and escalation rules. Approval of a diagnosis label or a bibliography alone cannot establish those decisions. Source and content revisions require a fresh review of affected decisions.

## Independent verification status

Regional verifiers are different agents from the dossier authors. They reopen primary sources and check claims, populations, doses, results, harms, corrections and candidate mappings. All four regional specification and research-quality gates were approved for this research-only deliverable after scoped corrections. Verification remains bounded or partial where primary methods could not be inspected; approval does not erase those gaps. A real citation, passing JSON validation and agreement between agents do not establish clinical approval.

| Region | Conditions | Source-concordance gate |
| --- | ---: | --- |
| Spine and hip/piriformis | 14 | Research gate approved; 59 source records, 20 rules and 22 mappings checked with access limits |
| Knee and systemic | 12 | Research gate approved; 38 source records, 26 rules and 26 mappings checked with access limits |
| Shoulder and elbow/hand | 14 | Research gate approved; 66 source records, 20 rules and 26 mappings checked; source verification remains partial |
| Lower leg/ankle/foot | 10 | Research gate approved; 33 source records, 13 rules and 21 mappings checked with access limits |

## Corrections and remaining source-verification limits

The independent passes corrected a missing/conflicting scoliosis home schedule, a malformed narrative DOI, PFP progression described as a free-choice alternative, Sinaki cointervention allocation, insertional-Achilles supervision duration, stale erratum wording, CTS evidence-quality versus recommendation-strength grading, the Baker primary/CPG endpoint discrepancy, RoCTEx applicability and a reported band-anchor position. Published correction notices are linked in their source records; correction existence alone was not assumed to invalidate or preserve results.

All 78 abstract/partial-access source records received a focused audit for explicitly reported protocol facts. Accessible statements now carry `reported in accessible text:` where appropriate; unseen mechanics, sets, loading or schedules remain unknown. Program duration, clinic visits, device wear and outcome follow-up are kept distinct from isolated exercise doses. The 187 distinct source-list entries are not all randomized trials or independently inspected full papers.

Source access counts in the inventory describe the original extraction. Independent reviewers sometimes had narrower access. Remaining examples include the WHO chapter and 2025 neck guideline, selected Krauss/Bennell hip-OA details, the BESS AC pathway (metadata only in the independent pass), selected 2026 CTS/hand-OA guideline passages and parts of the MOON/LET reports. Primary abstracts or indexed passages were checked where available; missing full methods and original handouts/figures were not inferred. The CTS monitoring passage in the 2026 document appears under its reproduced 2019 recommendation; it is not presented as newly established 2026 evidence.

Contrary and null results, mixed diagnoses, bundled-treatment effects, imperfect harms reporting and unresolved primary-versus-guideline accounts remain in the dossiers. No independent pass claimed exhaustive retrieval, a registry-wide absence of corrections, or clinical safety for an individual. Every current dossier hash below was matched to its assigned verifier's final recorded hashes after corrections.

## Applicability and execution limits

- Supervised programs such as LIFTMOR, high-intensity cycling/treadmill interventions, HSD shoulder loading and person-centered fibromyalgia strength work cannot inherit ordinary unsupervised workout defaults. Tested strength, heart-rate or voluntary-contraction targets require the actual measurements and supervision used by the source or a separately reviewed adaptation.
- Calendar-based stages, next-day symptom checks, per-side accounting, assessment-dependent movement direction and return-to-sport criteria need explicit supported inputs. A workout cannot guess those facts from the diagnosis or compress a complete rehabilitation program into an available session slot.
- Acute trauma, operative protocols, active bone stress injury and neurological changes have distinct applicability boundaries. A trial exclusion is not a universal contraindication; a diagnosis alone also cannot authorize a safe substitute. Unknown stage, clearance or procedure remains unknown.
- Bundled interventions and active-comparator trials do not isolate the effects of individual stretches. Missing harms reporting does not establish safety. Internal protocol discrepancies, null outcomes, limited follow-up and indirect populations stay visible in the source records.
- Program-level requests and variants with missing handouts, figures, support, anchor position or range are research tasks. They are not finished exercise identities or a count of PNG sequences to generate. No new PNG artwork has been accepted by this packet.
- Restriction-only drafts and conditions without an exact rule or mapping are called out in the [summary](summary.md). None becomes supported simply because its evidence dossier is present.

## Frozen candidate record manifest

Hashes below bind the current dossier bytes for review; they are not clinical signatures. A changed hash invalidates any claim that an earlier check covered the changed text. Every row retains release status `AWAITING_CLINICAL_REVIEW`. Rule and mapping counts describe candidates only.

| Condition dossier | Research disposition | Rules / mappings | SHA-256 |
| --- | --- | ---: | --- |
| [Chronic primary low-back pain](chronic-primary-low-back-pain.json) | unresolved | 1 / 1 | `6156bf6b9c7977829549f2661907500b77f65e82d5988b73e4cb53db32338611` |
| [lumbar radicular pain](lumbar-radicular-pain.json) | unresolved | 2 / 1 | `3ec92316dfc3285c7ca0bdf26c0c006722ee045dc907dd4efa7ffb44082c0af6` |
| [lumbar spinal stenosis](lumbar-spinal-stenosis.json) | unresolved | 1 / 1 | `2c9d1a5ffd78e34b8155a88486d939bb4f6a2a13f1e294a1e7fd3ea8d0e21965` |
| [nonspecific neck pain](nonspecific-neck-pain.json) | unresolved | 0 / 2 | `1d78e1cf2ad4dcc382cc43fb1db76e0792d824627bb4c6fdf9a45a7e8abada30` |
| [cervical radicular pain](cervical-radicular-pain.json) | unresolved | 1 / 1 | `f2c630b6ea5921b732aa43a2fe5ee96e59a754abf80bb763f94998a8287545f1` |
| [whiplash-associated disorder](whiplash-associated-disorder.json) | unresolved | 2 / 3 | `7d4285861f62208e4d893f66c7ed8ebe307ef977925ba008d73c0375e0dba438` |
| [adult degenerative scoliosis](adult-degenerative-scoliosis.json) | unresolved | 0 / 0 | `bf0496494265db7d4f4fa0ca0b10bd4e63015cef309bd150f227f03480eb0407` |
| [Rotator cuff tendinopathy](rotator-cuff-tendinopathy.json) | research-only | 2 / 4 | `de3d711a088d3376620f7f174676ed2799ea4feb016ac6604513fa61202da3ac` |
| [full-thickness rotator cuff tear](full-thickness-rotator-cuff-tear.json) | research-only | 2 / 1 | `201c0a46507ff456749b76ce08acd2a4436568da3ad2e551b50d3d9e64d3be70` |
| [adhesive capsulitis](adhesive-capsulitis.json) | research-only | 2 / 1 | `4c4946bc6b2d2dd4feaf107ade92fa4c27394d10201d8b45e6aa20486e4e275f` |
| [glenohumeral osteoarthritis](glenohumeral-osteoarthritis.json) | insufficient-evidence | 0 / 0 | `1971861601bf5386666d678934ea499aedc6d50190d62f570763db22d9d74b30` |
| [shoulder instability](shoulder-instability.json) | research-only | 2 / 2 | `a0c8ccc1a0d0181794c2553ef805d752b4e3f90d0e232d512bc19adf74ba4ebe` |
| [acromioclavicular osteoarthritis](acromioclavicular-osteoarthritis.json) | research-only | 2 / 2 | `a4534403957fe339ce574a6d80900000670984ea2ca3fa1d3643712a9eaebd27` |
| [Lateral elbow tendinopathy](lateral-elbow-tendinopathy.json) | unresolved | 1 / 1 | `d25a61970efcae68da8bf3b973d3f1e88e8f1799d585bf84f3bc4f48347c6c93` |
| [medial elbow tendinopathy](medial-elbow-tendinopathy.json) | unresolved | 1 / 1 | `799ff9c0146efac20855ea472568f8fc6cc243f534d1c89862b455531c3e6dba` |
| [distal biceps tendinopathy](distal-biceps-tendinopathy.json) | unresolved | 1 / 1 | `5bf916edb176df31870e81a230bf78b1764b1b730a42e5e6dcfca2ea215280a1` |
| [carpal tunnel syndrome](carpal-tunnel-syndrome.json) | unresolved | 2 / 3 | `d20290436436d6584c9f353b1cdf7d8d58d180644668b18586fb2822f2eab6d8` |
| [ulnar neuropathy at the elbow](ulnar-neuropathy-at-the-elbow.json) | unresolved | 1 / 1 | `28010d2cd00bc449fedcb23554a609a505a29397916e5ec5880bc7aa4946ad9e` |
| [de Quervain tenosynovitis](de-quervain-tenosynovitis.json) | unresolved | 2 / 1 | `818c29b4f3df6a0cd45dc170405346156d8a4408c4f9f989155bde46e1b6749a` |
| [thumb carpometacarpal osteoarthritis](thumb-carpometacarpal-osteoarthritis.json) | unresolved | 1 / 5 | `90b9fd6b69987623f31b78f24ebd59940dabec48dfefc85840b71a281f522805` |
| [finger interphalangeal osteoarthritis](finger-interphalangeal-osteoarthritis.json) | unresolved | 1 / 3 | `d3d5bf4c91f9e7e1973977ab412f228136467710b59fecdcc89de4e575a8a647` |
| [Hip osteoarthritis](hip-osteoarthritis.json) | research-only | 2 / 2 | `19f8c0cfd67bb6db2163261c166556b9f890576bee961364aa6d8f06ef44f67e` |
| [gluteal tendinopathy](gluteal-tendinopathy.json) | research-only | 2 / 2 | `537a9591d2f9400299d3db223487c7993a4f72e8b55887338f74bb093d432b55` |
| [femoroacetabular impingement syndrome](femoroacetabular-impingement-syndrome.json) | research-only | 2 / 0 | `cb6c6111fdfb566276ecd8b8bbc938b96a7c0cf171cf894b98ee05376ea46cbe` |
| [adductor-related groin pain](adductor-related-groin-pain.json) | research-only | 1 / 2 | `239f779ac8eee1c2d5d4acc5b81457830ae85eddd7b991d23dbe089d46b66151` |
| [proximal hamstring tendinopathy](proximal-hamstring-tendinopathy.json) | research-only | 3 / 2 | `786d93018d55ceff769b48db546b8c4b173383bae764e32561fbf5297f54d2c8` |
| [iliopsoas-related groin pain](iliopsoas-related-groin-pain.json) | research-only | 1 / 1 | `821e810aa3649486190c1ad370044ef779013d8da2647b740022055a526f0479` |
| [piriformis syndrome](piriformis-syndrome.json) | research-only | 2 / 4 | `59c24572779c19b179f1fe2bee22c3c238c7a30cf073370d978264323c6a634e` |
| [Knee osteoarthritis](knee-osteoarthritis.json) | research-only | 1 / 1 | `01ad707ef663f7ffffa77bdca0cc9a1172e4eb7dc5dce6ab272f65c013879e02` |
| [patellofemoral pain](patellofemoral-pain.json) | research-only | 2 / 1 | `05a5bd40d288ec98f330164734165ab997b40d4ba589948f1edd7d97ec9b3932` |
| [patellar tendinopathy](patellar-tendinopathy.json) | research-only | 2 / 2 | `06a8ec2dcdc18d3ad755a319dfa5dd62c45f524aaf177aeb44531cce2dda2b89` |
| [iliotibial-band-related lateral knee pain](iliotibial-band-related-lateral-knee-pain.json) | research-only | 2 / 2 | `2041add8cbae319679caadf13b30d403c5645da9823fa8f5ed8539f0d5df4b07` |
| [degenerative meniscal lesion](degenerative-meniscal-lesion.json) | research-only | 2 / 1 | `9bf771b7a26227ca0e9f671b09b45b42dfbe52a651cb6a59ae4fc8ae1b33f1fe` |
| [anterior cruciate ligament injury](anterior-cruciate-ligament-injury.json) | research-only | 2 / 1 | `c2911a9a9538d3db6b2c30b84d0553c15d83f7d23948db389250c279271d2e1d` |
| [medial collateral ligament sprain](medial-collateral-ligament-sprain.json) | research-only | 2 / 2 | `7e8131534344c9c9da3a7273f9517873793ca16026f0232621ee0d9f6cbabce9` |
| [Midportion Achilles tendinopathy](midportion-achilles-tendinopathy.json) | eligible-for-independent-content-review | 2 / 5 | `9b0dcccb2a0be56ba5dbce5fba08094525eb917a960fc7a4889fe73a14bf474c` |
| [insertional Achilles tendinopathy](insertional-achilles-tendinopathy.json) | eligible-for-independent-content-review | 2 / 3 | `e3a599e700ec024e2cca1cee951b6a786e5d0725485b95038d64aad52c0884b4` |
| [plantar heel pain compatible with plantar fasciitis](plantar-heel-pain-compatible-with-plantar-fasciitis.json) | eligible-for-independent-content-review | 1 / 3 | `2df166288920b4e7661ab9e88755cdcbe63977806efab5f7366def18b7359806` |
| [lateral ankle sprain](lateral-ankle-sprain.json) | unresolved | 2 / 1 | `379c38de47c47868dc02de82ead57031b1e65f18698a6bcd5fa939272b468184` |
| [chronic ankle instability](chronic-ankle-instability.json) | eligible-for-independent-content-review | 1 / 4 | `f8a1cb790865f43f63cdc414c01d2344f25bd3906b95b8870a3fd85f3a8c8475` |
| [posterior tibial tendinopathy](posterior-tibial-tendinopathy.json) | unresolved | 1 / 1 | `a38cd30f7930496f503d34dfb40569492c21db7b548732327592499863cb824e` |
| [peroneal tendinopathy](peroneal-tendinopathy.json) | unresolved | 1 / 1 | `72e0e8489b64a893057dcd6dcaa020fd7da2f2e733d22f5b81d6bb66459226a4` |
| [medial tibial stress syndrome](medial-tibial-stress-syndrome.json) | research-only | 1 / 2 | `3733f457ecee7727615bc5e37afd08c4bc74270db0b0b50b1bd4e1ec4746a17d` |
| [lower-limb bone stress injury](lower-limb-bone-stress-injury.json) | research-only | 1 / 0 | `de4e7da373b9b7d4d067c43dbd1f7c2ee81bf6201991dae7a747d989cb8442dd` |
| [first metatarsophalangeal osteoarthritis](first-metatarsophalangeal-osteoarthritis.json) | unresolved | 1 / 1 | `6c8c3e74a26dba07b0992c4a84fe870a0c4ea64155d5d567583685616863d09e` |
| [Osteoporosis](osteoporosis.json) | research-only | 3 / 4 | `cf6939c6445d367e71b168764c3a3f11365be1393f6bb93ac36d608561f2b493` |
| [rheumatoid arthritis](rheumatoid-arthritis.json) | research-only | 3 / 1 | `9b2edf76b49ca725d860dfd523ec1a6b48d14e86b4d7fe2a75815dab3fe427f6` |
| [axial spondyloarthritis](axial-spondyloarthritis.json) | research-only | 2 / 2 | `43d42fd0a9d87fe32546bc0bb9f1478b8ca3d76bd93996bd28395e78525e7725` |
| [fibromyalgia](fibromyalgia.json) | research-only | 3 / 4 | `908f02c7d13e788ce9c9a4f2f7fe1fa59354e8a5a6d6cac33f8d4d6e10c293c4` |
| [hypermobility spectrum disorder](hypermobility-spectrum-disorder.json) | research-only | 2 / 5 | `3d49a29aa6038550e3221912f9f0c4fc5507bfff4244e1ad698ea5a93b84887c` |

## Shared input manifest

| Input | SHA-256 |
| --- | --- |
| [index.json](index.json) | `4d7299edfe3558435cae85556a64828c0e3f63cbc6e35c908604b99a3c0333d5` |
| [exercise-requests.json](exercise-requests.json) | `a7b233ae808e5941f41129c3f17ce6ee5b74fa47a37e3b4941b06188c22fcb35` |
| [evidence/elbow-hand.md](evidence/elbow-hand.md) | `45320db6acb1a926ded0a7754ae74d5028b0c2e2270b17e0675361e95ce56dc0` |
| [evidence/hip.md](evidence/hip.md) | `0fb98d54a3f6711f05724b78058c4bcfeb3ec8de7608f673afde2abfae81c135` |
| [evidence/knee.md](evidence/knee.md) | `dac93b6e2b12233ba25a3c74d92ea68a2ce1b3e9008b9463e5ba2fea0f8525ae` |
| [evidence/lower-leg.md](evidence/lower-leg.md) | `facafc75fddf879934158213c48c28c965a323cb4e5d6b178c0f56b5658c151b` |
| [evidence/piriformis.md](evidence/piriformis.md) | `2e37a27546edb8180299dca906d95938e74f07e83461c2898b19165773ce6c7f` |
| [evidence/shoulder.md](evidence/shoulder.md) | `c743218e7421f8a33162850c842d7fa8e84ff808646a7e0d2ececac24e983652` |
| [evidence/spine.md](evidence/spine.md) | `2debe8fff4d736fba214094b6ccd7c228994904033e825f974c73f285e8892a6` |
| [evidence/systemic.md](evidence/systemic.md) | `6757a4c224b7fdae87a2bbd055617066232f58f015c18ce8f24c53619fae70ef` |

## Validation and remaining gates

The offline `validate_condition_research.py --require-complete` checks exact inventory coverage, required fields, source/finding/request references and absent clinical promotion. It does not validate medical truth. At commit `f47bd0af0`, the complete corpus check passed and the full workout-guide Python suite ran 180 tests in 31.637 seconds with no failures. The shared-register/document reconciliation still requires its final scoped review; these test results are not a scientific or clinical sign-off.

Before release, the candidate content must pass independent source-concordance checks, identity/artwork review, applicable runtime contract checks and authentic qualified clinical review. With no clinician identified, research and infrastructure may continue while production therapeutic content remains withheld. No external reviewer has been contacted.
