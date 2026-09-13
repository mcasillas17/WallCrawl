# Exercise Metadata Human Sign-off

This unsigned worksheet is generated from the [full per-exercise evidence ledger](research/2026-09-07-full-exercise-catalog-review.json). It records AI recommendations for human inspection, **not human approval or clinical validation**.

- Catalog entries examined: **302**
- AI-ready for human inspection: **0**
- AI-accepted categorical metadata: **182**
- Pending evidence or policy decisions: **85**
- Outside automatic-strength scope: **35**
- Authored reviewed metadata: **211** (AI_ACCEPTED: **182**, DRAFT: **29**)
- Human-approved metadata: **0**
- Pinned source: `ba0b709cb20430361b2cb33aaadd20998164a916`
- Canonical AI audit: [complete report, criteria, IDs and reasons](research/2026-09-13-ai-acceptance-audit.json)
- Audit artifact SHA-256: `840a70b0881f6511a56040cd316fc455c2f8d3921d2a3ff38dc9858dbd87ec4b`
- Recorded AI reviewer: `gpt-6-astra`; original decision timestamp: `1789278919309` epoch milliseconds
- Reproduce the partition and proposal bindings offline: `python3 tools/workout-guide/verify_ai_acceptance_audit.py`
- Current programming SHA-256 (committed bytes): `8a3ea988d0f32954fd4a03e2f58a2e4b65bce6db6b638dd0475a1f0994b92c24`
- Independent source verification reconstructs the inspected schema-v2 ledger, metadata and catalog before hashing, and reads/hashes current programming bytes. Copied historical hashes alone are not verification; no unavailable original audit-file byte identity is asserted.

`AI_ACCEPTED` records an owner-authorized AI categorical decision, not human sign-off. `DRAFT` remains pending and ineligible for reviewed planning. Automatic-strength classification describes the current importer/planner boundary. Source acceptance, endpoint acceptance, type scope and human approval are separate judgments; none establishes suitability for every user.

## What human sign-off covers

For each ID, inspect the cited source and illustrations, the exact proposed metadata, its corrections and limitations, and every directed regression/substitution. The ledger's `metadataSha256` binds the exact current metadata, while `auditedProposalSha256` and AI `reviewedContentSha256` identify the exact pre-disposition proposal inspected by the corpus auditor, not a self-referential hash of the final record. Changed categorical proposals need renewed inspection. The fields named `approvedRegressions` and `approvedSubstitutions` preserve directed relationship history: they remain proposals on DRAFT sources and authorizations on accepted sources, never acceptance of a pending endpoint. Runtime independently requires relevant accepted endpoints and all other eligibility conditions before using a link.

Schema version 3 adds dedicated `aiReviewProvenance` without filling human provenance. The committed audit preserves the complete original report and its final source-file mtime as the acceptance timestamp, not the archival copy's mtime or a new per-source fetch or illustration inspection. AI policy version 2 applies to accepted records; schema-only refreshes of pending drafts retain their original policy version and do not imply renewed acceptance.

Reviewed schema version 2 adds `clearedTrainingConstraints`, so sign-off now also covers which selected joint sensitivities — shoulder, elbow, wrist, lower back, hip, knee — the exercise is explicitly cleared for. No record below lists any. An empty list is the fail-closed value: the exercise stays out of automatic planning for a user who selected that sensitivity, and no clearance is inferred from its name, muscles or movement pattern. `LOW_IMPACT_ONLY` is deliberately not part of that list because `impactLevel` already decides it. A clearance is a reviewer's product judgement about a self-reported label, never a diagnosis or clinical clearance.

A human decision must identify the reviewed ID and fields, actual reviewer role, actual review time, rationale and remaining caveats. Only that explicit decision can support a later authored change to `reviewState=approved` with truthful provenance. A checklist, model consensus, software check, pull-request approval or merge is not that decision. No sign-off has been supplied for any row below.

All 302 exercises remain available for browsing and manual workouts. Excluded categories receive no manufactured strength allocation. Production reviewed planning remains disabled; metadata acceptance, human approval, equipment/profile availability and rollout are separate gates.

## Per-ID sign-off register

The ledger contains the field-group evidence and complete remaining decisions for each ID. `Not allocated` means no reviewed direct-primary block, not absence of muscle involvement.

| Exercise ID | AI content disposition | Proposed direct primary | Human decision |
| --- | --- | --- | --- |
| `ab-wheel` | pending_evidence_or_policy | Not allocated | Pending |
| `active-hang` | ai_accepted | Lats | Pending |
| `archer-push-up` | ai_accepted | Chest | Pending |
| `arm-circles` | outside_automatic_strength_scope | Not allocated | N/A |
| `arnold-press` | ai_accepted | Shoulders | Pending |
| `assault-bike` | outside_automatic_strength_scope | Not allocated | N/A |
| `assisted-chin-up` | ai_accepted | Biceps | Pending |
| `assisted-dip` | pending_evidence_or_policy | Not allocated | Pending |
| `assisted-pistol-squat` | pending_evidence_or_policy | Quadriceps | Pending |
| `assisted-pull-up` | ai_accepted | Lats | Pending |
| `back-extension` | pending_evidence_or_policy | Not allocated | Pending |
| `band-pull-apart` | ai_accepted | Upper Back | Pending |
| `banded-clamshell` | ai_accepted | Glutes | Pending |
| `banded-dead-bug` | ai_accepted | Core | Pending |
| `banded-donkey-kick` | ai_accepted | Glutes | Pending |
| `banded-face-pull` | pending_evidence_or_policy | Not allocated | Pending |
| `banded-fire-hydrant` | ai_accepted | Glutes | Pending |
| `banded-frog-pump` | pending_evidence_or_policy | Glutes | Pending |
| `banded-glute-bridge` | ai_accepted | Glutes | Pending |
| `banded-hip-thrust` | pending_evidence_or_policy | Not allocated | Pending |
| `banded-kickback` | pending_evidence_or_policy | Not allocated | Pending |
| `banded-lat-pulldown` | pending_evidence_or_policy | Not allocated | Pending |
| `banded-lateral-walk` | ai_accepted | Glutes | Pending |
| `banded-monster-walk` | ai_accepted | Glutes | Pending |
| `banded-pallof-press` | pending_evidence_or_policy | Not allocated | Pending |
| `banded-row` | pending_evidence_or_policy | Not allocated | Pending |
| `banded-seated-hip-abduction` | ai_accepted | Glutes | Pending |
| `banded-squat` | ai_accepted | Quadriceps | Pending |
| `banded-standing-hip-abduction` | ai_accepted | Glutes | Pending |
| `banded-woodchop` | pending_evidence_or_policy | Not allocated | Pending |
| `barbell-back-squat` | ai_accepted | Quadriceps | Pending |
| `barbell-bench-press` | ai_accepted | Chest | Pending |
| `barbell-bicep-curl` | ai_accepted | Biceps | Pending |
| `barbell-deadlift` | pending_evidence_or_policy | Hamstrings | Pending |
| `barbell-glute-bridge` | ai_accepted | Glutes | Pending |
| `barbell-row` | ai_accepted | Back | Pending |
| `battle-ropes` | outside_automatic_strength_scope | Not allocated | N/A |
| `bear-crawl` | outside_automatic_strength_scope | Not allocated | N/A |
| `bear-plank` | pending_evidence_or_policy | Core | Pending |
| `belt-squat` | pending_evidence_or_policy | Quadriceps | Pending |
| `bench-dip` | ai_accepted | Triceps | Pending |
| `bent-over-rear-delt-raise` | ai_accepted | Rear Delts | Pending |
| `bicep-curl` | ai_accepted | Biceps | Pending |
| `bicycle-crunch` | ai_accepted | Core | Pending |
| `bird-dog` | ai_accepted | Core | Pending |
| `bodyweight-squat` | ai_accepted | Quadriceps | Pending |
| `bulgarian-split-squat` | ai_accepted | Quadriceps | Pending |
| `burpee` | pending_evidence_or_policy | Quadriceps | Pending |
| `butterfly-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `cable-crunch` | pending_evidence_or_policy | Core | Pending |
| `cable-curl` | ai_accepted | Biceps | Pending |
| `cable-fly` | ai_accepted | Chest | Pending |
| `cable-front-raise` | ai_accepted | Shoulders | Pending |
| `cable-kickback` | pending_evidence_or_policy | Glutes | Pending |
| `cable-lateral-raise` | ai_accepted | Shoulders | Pending |
| `cable-pallof-hold` | ai_accepted | Core | Pending |
| `cable-pull-through` | ai_accepted | Glutes | Pending |
| `cable-rear-delt-fly` | ai_accepted | Rear Delts | Pending |
| `cable-standing-hip-abduction` | pending_evidence_or_policy | Glutes | Pending |
| `cable-standing-hip-adduction` | pending_evidence_or_policy | Adductors | Pending |
| `cable-triceps-pushdown` | ai_accepted | Triceps | Pending |
| `cable-woodchop` | ai_accepted | Core | Pending |
| `calf-raise` | pending_evidence_or_policy | Not allocated | Pending |
| `captains-chair-knee-raise` | ai_accepted | Core | Pending |
| `cat-cow-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `chair-dip` | ai_accepted | Triceps | Pending |
| `chest-supported-row` | ai_accepted | Back | Pending |
| `childs-pose` | outside_automatic_strength_scope | Not allocated | N/A |
| `chin-up` | ai_accepted | Biceps | Pending |
| `clamshell` | ai_accepted | Glutes | Pending |
| `close-grip-bench-press` | ai_accepted | Triceps | Pending |
| `close-grip-lat-pulldown` | ai_accepted | Lats | Pending |
| `commando-pull-up` | pending_evidence_or_policy | Not allocated | Pending |
| `concentration-curl` | pending_evidence_or_policy | Not allocated | Pending |
| `copenhagen-plank` | pending_evidence_or_policy | Core | Pending |
| `cossack-squat` | ai_accepted | Quadriceps | Pending |
| `crab-walk` | pending_evidence_or_policy | Triceps | Pending |
| `cross-body-shoulder-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `crunch` | ai_accepted | Core | Pending |
| `curtsy-lunge` | ai_accepted | Glutes | Pending |
| `cycling` | outside_automatic_strength_scope | Not allocated | N/A |
| `dead-bug` | ai_accepted | Core | Pending |
| `dead-hang` | ai_accepted | Forearms | Pending |
| `decline-bench-press` | ai_accepted | Chest | Pending |
| `decline-dumbbell-press` | ai_accepted | Chest | Pending |
| `decline-push-up` | ai_accepted | Chest | Pending |
| `decline-sit-up` | ai_accepted | Core | Pending |
| `deficit-reverse-lunge` | ai_accepted | Glutes | Pending |
| `diamond-push-up` | ai_accepted | Triceps | Pending |
| `dip` | pending_evidence_or_policy | Triceps | Pending |
| `donkey-calf-raise` | pending_evidence_or_policy | Not allocated | Pending |
| `donkey-kick` | ai_accepted | Glutes | Pending |
| `doorway-chest-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `doorway-row` | ai_accepted | Back | Pending |
| `drag-curl` | ai_accepted | Biceps | Pending |
| `dragon-flag` | pending_evidence_or_policy | Not allocated | Pending |
| `dumbbell-bench-press` | ai_accepted | Chest | Pending |
| `dumbbell-bent-over-row` | ai_accepted | Back | Pending |
| `dumbbell-curtsy-lunge` | ai_accepted | Glutes | Pending |
| `dumbbell-fly` | ai_accepted | Chest | Pending |
| `dumbbell-glute-bridge` | ai_accepted | Glutes | Pending |
| `dumbbell-hip-thrust` | ai_accepted | Glutes | Pending |
| `dumbbell-lateral-lunge` | ai_accepted | Quadriceps | Pending |
| `dumbbell-lateral-raise` | ai_accepted | Shoulders | Pending |
| `dumbbell-overhead-tricep-extension` | pending_evidence_or_policy | Not allocated | Pending |
| `dumbbell-romanian-deadlift` | ai_accepted | Hamstrings | Pending |
| `dumbbell-shoulder-press` | ai_accepted | Shoulders | Pending |
| `dumbbell-shrug` | ai_accepted | Upper Back | Pending |
| `dumbbell-side-bend` | ai_accepted | Core | Pending |
| `dumbbell-skull-crusher` | ai_accepted | Triceps | Pending |
| `dumbbell-sumo-deadlift` | pending_evidence_or_policy | Not allocated | Pending |
| `dumbbell-sumo-squat` | ai_accepted | Glutes | Pending |
| `elliptical` | outside_automatic_strength_scope | Not allocated | N/A |
| `explosive-push-up` | pending_evidence_or_policy | Not allocated | Pending |
| `face-pull` | ai_accepted | Upper Back | Pending |
| `farmer-carry` | outside_automatic_strength_scope | Not allocated | N/A |
| `fast-feet` | outside_automatic_strength_scope | Not allocated | N/A |
| `feet-elevated-pike-push-up` | pending_evidence_or_policy | Shoulders | Pending |
| `fire-hydrant` | pending_evidence_or_policy | Glutes | Pending |
| `flutter-kick` | ai_accepted | Core | Pending |
| `forward-lunge` | ai_accepted | Quadriceps | Pending |
| `frog-pump` | ai_accepted | Glutes | Pending |
| `front-foot-elevated-split-squat` | pending_evidence_or_policy | Quadriceps | Pending |
| `front-raise` | ai_accepted | Shoulders | Pending |
| `front-squat` | ai_accepted | Quadriceps | Pending |
| `glute-bridge` | ai_accepted | Glutes | Pending |
| `glute-bridge-march` | ai_accepted | Glutes | Pending |
| `glute-focused-back-extension` | pending_evidence_or_policy | Not allocated | Pending |
| `goblet-squat` | ai_accepted | Quadriceps | Pending |
| `good-morning` | pending_evidence_or_policy | Not allocated | Pending |
| `hack-squat` | ai_accepted | Quadriceps | Pending |
| `half-burpee` | pending_evidence_or_policy | Not allocated | Pending |
| `half-kneeling-pallof-press` | ai_accepted | Core | Pending |
| `hammer-curl` | ai_accepted | Biceps | Pending |
| `hamstring-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `handstand-push-up` | pending_evidence_or_policy | Not allocated | Pending |
| `hanging-knee-raise` | ai_accepted | Core | Pending |
| `hanging-leg-raise` | pending_evidence_or_policy | Core | Pending |
| `heel-elevated-goblet-squat` | pending_evidence_or_policy | Not allocated | Pending |
| `heel-tap` | ai_accepted | Core | Pending |
| `high-knees` | outside_automatic_strength_scope | Not allocated | N/A |
| `hiking` | outside_automatic_strength_scope | Not allocated | N/A |
| `hindu-push-up` | ai_accepted | Chest | Pending |
| `hip-abduction-machine` | ai_accepted | Glutes | Pending |
| `hip-adduction-machine` | ai_accepted | Adductors | Pending |
| `hip-airplane` | pending_evidence_or_policy | Not allocated | Pending |
| `hip-thrust` | ai_accepted | Glutes | Pending |
| `hollow-body-hold` | pending_evidence_or_policy | Not allocated | Pending |
| `hollow-rock` | ai_accepted | Core | Pending |
| `inchworm` | ai_accepted | Core | Pending |
| `incline-bench-press` | ai_accepted | Chest | Pending |
| `incline-cable-fly` | ai_accepted | Chest | Pending |
| `incline-dumbbell-curl` | ai_accepted | Biceps | Pending |
| `incline-dumbbell-press` | ai_accepted | Chest | Pending |
| `incline-push-up` | ai_accepted | Chest | Pending |
| `inverted-row` | pending_evidence_or_policy | Not allocated | Pending |
| `jump-rope` | outside_automatic_strength_scope | Not allocated | N/A |
| `jump-squat` | ai_accepted | Quadriceps | Pending |
| `jumping-jack` | outside_automatic_strength_scope | Not allocated | N/A |
| `kettlebell-romanian-deadlift` | ai_accepted | Hamstrings | Pending |
| `kettlebell-swing` | ai_accepted | Glutes | Pending |
| `knee-push-up` | pending_evidence_or_policy | Chest | Pending |
| `kneeling-hip-flexor-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `l-sit-hold` | pending_evidence_or_policy | Core | Pending |
| `l-sit-pull-up` | ai_accepted | Lats | Pending |
| `landmine-press` | pending_evidence_or_policy | Not allocated | Pending |
| `landmine-romanian-deadlift` | pending_evidence_or_policy | Not allocated | Pending |
| `landmine-squat` | pending_evidence_or_policy | Not allocated | Pending |
| `lat-pulldown` | ai_accepted | Lats | Pending |
| `lateral-lunge` | ai_accepted | Quadriceps | Pending |
| `lateral-shuffle` | outside_automatic_strength_scope | Not allocated | N/A |
| `leg-curl` | ai_accepted | Hamstrings | Pending |
| `leg-extension` | ai_accepted | Quadriceps | Pending |
| `leg-press` | ai_accepted | Quadriceps | Pending |
| `leg-press-calf-raise` | ai_accepted | Calves | Pending |
| `leg-swings-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `lying-hamstring-walkout` | pending_evidence_or_policy | Not allocated | Pending |
| `lying-leg-curl` | ai_accepted | Hamstrings | Pending |
| `lying-leg-raise` | ai_accepted | Core | Pending |
| `machine-chest-press` | ai_accepted | Chest | Pending |
| `machine-glute-kickback` | pending_evidence_or_policy | Not allocated | Pending |
| `machine-lateral-raise` | ai_accepted | Shoulders | Pending |
| `machine-row` | pending_evidence_or_policy | Back | Pending |
| `machine-shoulder-press` | ai_accepted | Shoulders | Pending |
| `meadows-row` | pending_evidence_or_policy | Not allocated | Pending |
| `mountain-climber` | pending_evidence_or_policy | Not allocated | Pending |
| `negative-pull-up` | pending_evidence_or_policy | Lats | Pending |
| `neutral-grip-pull-up` | pending_evidence_or_policy | Lats | Pending |
| `nordic-hamstring-curl` | pending_evidence_or_policy | Not allocated | Pending |
| `one-arm-dumbbell-row` | ai_accepted | Back | Pending |
| `overhead-press` | ai_accepted | Shoulders | Pending |
| `overhead-tricep-extension` | ai_accepted | Triceps | Pending |
| `pallof-press` | ai_accepted | Core | Pending |
| `parallel-bar-dips` | pending_evidence_or_policy | Chest | Pending |
| `pec-deck` | ai_accepted | Chest | Pending |
| `pendlay-row` | pending_evidence_or_policy | Back | Pending |
| `pike-push-up` | ai_accepted | Shoulders | Pending |
| `pistol-squat` | ai_accepted | Quadriceps | Pending |
| `plank` | pending_evidence_or_policy | Core | Pending |
| `plank-jack` | outside_automatic_strength_scope | Not allocated | N/A |
| `plank-shoulder-tap` | ai_accepted | Core | Pending |
| `plate-front-raise` | ai_accepted | Shoulders | Pending |
| `preacher-curl` | ai_accepted | Biceps | Pending |
| `prone-t-raise` | pending_evidence_or_policy | Not allocated | Pending |
| `prone-y-raise` | ai_accepted | Upper Back | Pending |
| `pull-ups` | ai_accepted | Lats | Pending |
| `push-press` | ai_accepted | Shoulders | Pending |
| `push-up` | ai_accepted | Chest | Pending |
| `push-up-shoulder-tap` | ai_accepted | Core | Pending |
| `rack-pull` | pending_evidence_or_policy | Not allocated | Pending |
| `rear-delt-fly` | ai_accepted | Rear Delts | Pending |
| `reverse-crunch` | ai_accepted | Core | Pending |
| `reverse-curl` | ai_accepted | Forearms | Pending |
| `reverse-hyperextension` | pending_evidence_or_policy | Not allocated | Pending |
| `reverse-lunge` | ai_accepted | Quadriceps | Pending |
| `reverse-pec-deck` | ai_accepted | Rear Delts | Pending |
| `reverse-snow-angel` | ai_accepted | Upper Back | Pending |
| `romanian-deadlift` | ai_accepted | Hamstrings | Pending |
| `rope-hammer-curl` | ai_accepted | Biceps | Pending |
| `rope-tricep-pushdown` | ai_accepted | Triceps | Pending |
| `rowing` | outside_automatic_strength_scope | Not allocated | N/A |
| `running` | outside_automatic_strength_scope | Not allocated | N/A |
| `russian-twist` | pending_evidence_or_policy | Not allocated | Pending |
| `scapular-pull-up` | ai_accepted | Lats | Pending |
| `scapular-push-up` | ai_accepted | Upper Back | Pending |
| `seal-jack` | outside_automatic_strength_scope | Not allocated | N/A |
| `seated-calf-raise` | ai_accepted | Calves | Pending |
| `seated-forward-fold-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `seated-knee-tuck` | pending_evidence_or_policy | Not allocated | Pending |
| `seated-leg-curl` | ai_accepted | Hamstrings | Pending |
| `seated-row` | ai_accepted | Back | Pending |
| `shrimp-squat` | pending_evidence_or_policy | Not allocated | Pending |
| `shrug` | ai_accepted | Upper Back | Pending |
| `side-lying-hip-abduction` | ai_accepted | Glutes | Pending |
| `side-lying-leg-raise` | ai_accepted | Glutes | Pending |
| `side-plank` | ai_accepted | Core | Pending |
| `side-plank-hip-dip` | ai_accepted | Core | Pending |
| `single-arm-cable-row` | pending_evidence_or_policy | Not allocated | Pending |
| `single-arm-dumbbell-tricep-extension` | ai_accepted | Triceps | Pending |
| `single-dumbbell-skullcrusher` | ai_accepted | Triceps | Pending |
| `single-leg-box-squat` | pending_evidence_or_policy | Quadriceps | Pending |
| `single-leg-calf-raise` | pending_evidence_or_policy | Not allocated | Pending |
| `single-leg-glute-bridge` | ai_accepted | Glutes | Pending |
| `single-leg-romanian-deadlift` | ai_accepted | Hamstrings | Pending |
| `sissy-squat` | pending_evidence_or_policy | Not allocated | Pending |
| `skater-hop` | pending_evidence_or_policy | Not allocated | Pending |
| `skater-squat` | pending_evidence_or_policy | Not allocated | Pending |
| `skierg` | outside_automatic_strength_scope | Not allocated | N/A |
| `skull-crusher` | ai_accepted | Triceps | Pending |
| `smith-machine-bench-press` | ai_accepted | Chest | Pending |
| `smith-machine-bulgarian-split-squat` | ai_accepted | Quadriceps | Pending |
| `smith-machine-hip-thrust` | ai_accepted | Glutes | Pending |
| `smith-machine-reverse-lunge` | ai_accepted | Quadriceps | Pending |
| `smith-machine-romanian-deadlift` | ai_accepted | Hamstrings | Pending |
| `smith-machine-split-squat` | ai_accepted | Quadriceps | Pending |
| `smith-machine-squat` | ai_accepted | Quadriceps | Pending |
| `spider-curl` | pending_evidence_or_policy | Not allocated | Pending |
| `split-squat` | ai_accepted | Quadriceps | Pending |
| `sprawl` | pending_evidence_or_policy | Not allocated | Pending |
| `squat-thrust` | pending_evidence_or_policy | Not allocated | Pending |
| `stability-ball-hamstring-curl` | pending_evidence_or_policy | Not allocated | Pending |
| `stair-climber` | outside_automatic_strength_scope | Not allocated | N/A |
| `standing-calf-raise` | ai_accepted | Calves | Pending |
| `standing-dumbbell-press` | ai_accepted | Shoulders | Pending |
| `standing-quad-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `step-down` | ai_accepted | Quadriceps | Pending |
| `step-up` | ai_accepted | Quadriceps | Pending |
| `straight-arm-pulldown` | ai_accepted | Lats | Pending |
| `sumo-deadlift` | pending_evidence_or_policy | Hamstrings | Pending |
| `superman` | ai_accepted | Lower Back | Pending |
| `superman-hold` | ai_accepted | Lower Back | Pending |
| `swimming` | outside_automatic_strength_scope | Not allocated | N/A |
| `t-bar-row` | ai_accepted | Back | Pending |
| `toe-touch` | pending_evidence_or_policy | Not allocated | Pending |
| `torso-twist-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `towel-hamstring-curl` | pending_evidence_or_policy | Not allocated | Pending |
| `towel-pull-up` | ai_accepted | Lats | Pending |
| `towel-row` | pending_evidence_or_policy | Not allocated | Pending |
| `trap-bar-deadlift` | pending_evidence_or_policy | Not allocated | Pending |
| `treadmill-incline-walk` | outside_automatic_strength_scope | Not allocated | N/A |
| `tricep-kickback` | ai_accepted | Triceps | Pending |
| `typewriter-push-up` | ai_accepted | Chest | Pending |
| `upright-row` | ai_accepted | Shoulders | Pending |
| `v-up` | ai_accepted | Core | Pending |
| `walking` | outside_automatic_strength_scope | Not allocated | N/A |
| `walking-lunge` | ai_accepted | Quadriceps | Pending |
| `wall-calf-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `wall-handstand-push-up` | ai_accepted | Shoulders | Pending |
| `wall-push-up` | ai_accepted | Chest | Pending |
| `wall-sit` | ai_accepted | Quadriceps | Pending |
| `wall-walk` | ai_accepted | Shoulders | Pending |
| `weighted-chin-up` | pending_evidence_or_policy | Not allocated | Pending |
| `weighted-crunch` | ai_accepted | Core | Pending |
| `weighted-dip` | pending_evidence_or_policy | Not allocated | Pending |
| `weighted-pull-up` | pending_evidence_or_policy | Not allocated | Pending |
| `weighted-push-up` | ai_accepted | Chest | Pending |
| `weighted-russian-twist` | ai_accepted | Core | Pending |
| `wide-grip-lat-pulldown` | ai_accepted | Lats | Pending |
| `wide-push-up` | ai_accepted | Chest | Pending |
| `worlds-greatest-stretch` | outside_automatic_strength_scope | Not allocated | N/A |
| `wrist-curl` | pending_evidence_or_policy | Forearms | Pending |
| `wrist-extension` | pending_evidence_or_policy | Forearms | Pending |

## Additional evidence and policy decisions

Human approval still requires field-by-field human inspection. These rows additionally have unresolved content or representation decisions; they are not ready recommendations.

| Exercise ID | Remaining decisions |
| --- | --- |
| `ab-wheel` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Choose one rollout variant and reconcile source frames. Add an explicit wheel equipment concept or an intentionally reviewed barbell-only variant before admission. |
| `assisted-dip` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Owner must reconcile assisted_bodyweight/assisted-dip identity with the depicted seated machine triceps press before a complete block or graph can be authored. |
| `assisted-pistol-squat` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Ratify parallel-rail/Dip Bars mapping or specify a distinct support-rail requirement. Human ratify the actual parallel-rail/Dip Bars mapping. The existing incoming pistol-squat regression has been rechecked against held support and reduced unheld demands; no Wall/Chair alternative is justified. |
| `back-extension` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Represent the specialized thigh-pad/ankle-retaining apparatus explicitly before authoring a complete block. Confirm hinge versus other/isolation product grouping if lumbar-only technique is intended. |
| `banded-face-pull` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Equipment representation is resolved for the runtime minimum. Separately author and field-review the full categorical DRAFT before reviewed admission; retain the held face-pull versus straight-arm-opening relationship decision. |
| `banded-frog-pump` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Decide whether this is a frog-position bridge or seated resisted abduction and reconcile pictures; ratify hinge versus isolation. |
| `banded-hip-thrust` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Confirm short thigh-loop versus additional long anchored hip-band/bar setup and whether floor entry is the authored method before a complete block. |
| `banded-kickback` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Equipment representation is resolved for the runtime minimum. Separately author and field-review the full categorical DRAFT before reviewed admission, retaining the observed supported stance rather than assigning unsupported balance. |
| `banded-lat-pulldown` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Equipment representation is resolved for the runtime minimum. Separately author and field-review the full categorical DRAFT before reviewed admission; retain the held seated elastic pull versus suspended pull-up relationship decision. |
| `banded-pallof-press` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Equipment representation is resolved for the runtime minimum. Separately author and field-review the full categorical DRAFT before reviewed admission. A new chest-press variation remains separate catalog work; retain the rejected rep-versus-duration cable-hold relationship. |
| `banded-row` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Establish the exact missing forward anchor height/configuration from source-bound evidence before replacing the unresolved automatic block or authoring a full DRAFT. Do not assign the comparable guide's waist-height anchor, an adjustable upper-body anchor, or a self-anchored variation to this ID. |
| `banded-woodchop` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Equipment representation is resolved for the runtime minimum. Separately author and field-review the full categorical DRAFT before reviewed admission; retain the held rotation-versus-anti-rotation relationship and the unresolved amount of spinal versus hip rotation. |
| `barbell-deadlift` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. A real qualified human must ratify Hamstrings versus another represented primary for the conventional deadlift allocation. If primary changes, parent must revalidate all hinge graph edges and planner coverage before approval. |
| `bear-plank` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Confirm stationary bear hold and reconcile first frame before readiness. |
| `belt-squat` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Ratify supportRequirement for a belt-loaded but unheld squat; keep actual balance/squat demands unless a held-support variant is separately authored. |
| `burpee` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Ratify a single-primary allocation for this composite movement. Confirm whether this catalog variant includes a push-up; if yes, add the bodyweight-push capability and reconcile artwork before readiness. |
| `cable-crunch` | Human field-by-field metadata sign-off; no reviewer role or review timestamp has been supplied. Owner must choose kneeling or standing/crouched cable crunch, reconcile frames, and ratify floor-transition/support/balance fields. |
| `cable-kickback` | Human field-by-field sign-off on the draft (muscles, pattern, complexity, family, capabilities, support, impact, equipment, and any graph edges) before reviewState changes. Confirm movementPattern (isolation vs hinge) and the balance_without_support capability. Establish an explicit source-bound attachment requirement or keep withheld. Do not silently reuse a band-specific attachment token or assume Cable includes a cuff. |
| `cable-standing-hip-abduction` | Human field-by-field sign-off on the draft (muscles, pattern, complexity, family, capabilities, support, impact, equipment, and any graph edges) before reviewState changes. Confirm supported vs unsupported given the hand-on-frame is a balance aid a user could release. Establish an explicit source-bound attachment requirement or keep withheld. Do not silently reuse a band-specific attachment token or assume Cable includes a cuff. |
| `cable-standing-hip-adduction` | Human field-by-field sign-off on the draft (muscles, pattern, complexity, family, capabilities, support, impact, equipment, and any graph edges) before reviewState changes. Confirm supported vs unsupported. Establish an explicit source-bound attachment requirement or keep withheld. Do not silently reuse a band-specific attachment token or assume Cable includes a cuff. |
| `calf-raise` | Withhold the incomplete Bodyweight-only block: both hands use separate vertical rails in the exact source frames. Supported plus no balance demand cannot be combined with absent rail availability. The current vocabulary does not establish this rail setup; a free-standing variant would need separate evidence and balance_without_support. Human must resolve the complete named setup before authoring and approving metadata. |
| `commando-pull-up` | Human must reconcile the exact named variation, complete setup and appropriate source illustrations before authoring and approving its metadata. |
| `concentration-curl` | Human must establish the exact pinned variation and complete apparatus/support/capability contract before authoring and approving metadata. |
| `copenhagen-plank` | Human field-by-field sign-off on the draft (muscles, pattern, complexity, family, capabilities, support, impact, equipment, and any graph edges) before reviewState changes. Decide whether Adductors should be added as a muscle for this movement; confirm supported (bench-borne leg) and the completed bench equipment. |
| `crab-walk` | Human field-by-field sign-off on the draft (muscles, pattern, complexity, family, capabilities, support, impact, equipment, and any graph edges) before reviewState changes. Decide movementPattern and primary muscle for a travelling crab-walk; consider whether continuous_activity applies. |
| `dip` | Human field-by-field sign-off on the draft (muscles, pattern, complexity, family, capabilities, support, impact, equipment, and any graph edges) before reviewState changes. Confirm the completed Dip Bars equipment and vertical_push classification. |
| `donkey-calf-raise` | Human must resolve intended machine versus partner-loaded variation and its full support/loading requirements before authoring and approving metadata. |
| `dragon-flag` | Human field-by-field sign-off on the draft (muscles, pattern, complexity, family, capabilities, support, impact, equipment, and any graph edges) before reviewState changes. Decide how (or whether) to represent the required overhead grip post/anchor before any metadata block is emitted; confirm advanced complexity. |
| `dumbbell-overhead-tricep-extension` | Human must identify Chair versus Bench support before equipmentAlternatives and the complete required metadata block can be authored. Metadata omitted because: The exact pictured backed-seat support cannot be distinguished as Chair or Bench from available evidence. |
| `dumbbell-sumo-deadlift` | Human must ratify the movement-pattern policy and choose one canonical primary from Posterior Chain; the artwork should not be treated as a proven identity mismatch. Metadata omitted because: The unresolved issue is movement-pattern and single-primary policy, not a proven artwork identity error. |
| `explosive-push-up` | Human must define or ratify the LOW/HIGH impact category for explosive push-ups before a required impactLevel can be authored. Metadata omitted because: Impact is material and the schema requires LOW or HIGH, but no sourced product-policy threshold distinguishes them for this upper-body landing. |
| `feet-elevated-pike-push-up` | Human must confirm the box-supported illustration is canonical for this entry and ratify supported semantics. |
| `fire-hydrant` | Human must sign off fire-hydrant: direct primary Glutes, isolation pattern, foundational complexity, setup/support, capabilities, impact, and the empty directed graph arrays. Resolve the support convention explicitly. This is a policy-consistency hold, not proof of different anatomy or a prescription to choose a value automatically. |
| `front-foot-elevated-split-squat` | Human must confirm Box as the intended schema label and supported semantics for a foot-elevation surface. |
| `glute-focused-back-extension` | Human must establish the exact pinned variation and complete apparatus/support/capability contract before authoring and approving metadata. |
| `good-morning` | Withhold the incomplete back-bar setup proposal. The pictures begin with the bar already across the upper back and do not establish how it is positioned. ExRx's BBGoodMorning preparation, retrieved on 2026-09-07, also only says to position the bar on the shoulders; it does not settle rack or self-placement requirements. Neither a free rack nor a new loading skill is assumed; a human must define and substantiate the intended setup. Human must resolve the complete named setup before authoring and approving metadata. |
| `half-burpee` | Human must confirm the half-burpee endpoints and whether this catalog variation hops or steps before impact capability/level and a complete metadata block can be authored. Metadata omitted because: The exact half-burpee foot transition is unresolved, so neither impact capability nor required impactLevel can be truthfully assigned. |
| `handstand-push-up` | Human must add an exact Parallettes term, replace the artwork, or explicitly redefine the catalog entry before metadata can be authored. Metadata omitted because: The pictured variation requires parallettes, which are absent from the schema; Bodyweight-only and Dip Bars would each misstate the exact setup. |
| `hanging-leg-raise` | Human must ratify the Pull-up Bar correction and decide whether the original manifest should eventually be fixed upstream. |
| `heel-elevated-goblet-squat` | Human must identify the heel fixture or add a precise equipment token, then ratify supported semantics and the impact/capability fields. Metadata omitted because: Required heel-elevation equipment cannot be represented exactly without guessing Plate, Box, or another generic support. |
| `hip-airplane` | Human must resolve the illustration identity and add an exact support term or replace the frames before metadata drafting. Metadata omitted because: Supported by automatic type rules, but unrepresentable until the name/artwork and fixed-post support vocabulary are reconciled. Concurrent female-artwork authoring must not use this reference until the exact source/artwork conflict is reconciled by a human. |
| `hollow-body-hold` | Human must replace/confirm the illustrations before approving movement complexity or duration-hold metadata. Metadata omitted because: The source name/product copy and the V-sit/tuck artwork represent materially different movements. Concurrent female-artwork authoring must not use this reference until the exact source/artwork conflict is reconciled by a human. |
| `inverted-row` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human must decide whether this ID means a true feet-supported row or the pictured overhead hang. Then choose truthful low-bar/rack equipment vocabulary, support and floor/balance/hang capabilities; current draft metadata is omitted. |
| `knee-push-up` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human explicitly decide whether knees-on-floor leverage counts as supported or whether only required external aids count; proposed unsupported must be coordinated with the full-push-up policy. Human ratify unchanged Chest and wall regression. |
| `l-sit-hold` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Choose straight-leg L-sit or tucked variant as intended content while artwork remains unchanged in this task. Ratify supported and upper_body_bodyweight_push semantics for static straight-arm support; no numeric hold time is authored. |
| `landmine-press` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human decide an explicit landmine capability/equipment vocabulary or keep this supported-by-type entry pending and omitted. Then ratify oblique press pattern, standing balance and exact setup without improvising a wall anchor. |
| `landmine-romanian-deadlift` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human resolve landmine/pivot vocabulary first, then determine standard versus foundational hinge tier and unheld standing balance. Keep source type supported but disposition pending until setup can be represented. |
| `landmine-squat` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human decide landmine fixture representation and whether this bar-holding squat counts as unsupported_squat. Keep the leg-press proposal held until the source pivot fixture and support contract are representable; the target has been inspected and cannot resolve those source omissions. |
| `lying-hamstring-walkout` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human decide whether to retain floor-walkout identity or suspended-curl identity in future content work. If suspended, add exact suspension-anchor representation before metadata; if floor walkout, require coherent source instructions/artwork first. |
| `machine-glute-kickback` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human define a truthful required ankle-attachment capability/token or leave this supported-by-type entry omitted. Do not solve the missing fixture by assigning a generic Machine, Wall, Doorway or Bench; depicted cable identity itself is not contradictory. |
| `machine-row` | Human ratify the exact rigid-arm, torso-pad apparatus and each directed relationship's distinct source/target support and handle mechanics. |
| `meadows-row` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human confirm secured-pivot Meadows-row identity and add a truthful landmine requirement before authoring full metadata. Then ratify self-bracing versus support semantics and appropriate complexity. |
| `mountain-climber` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human confirm intended hopping versus explicitly modified stepping execution against the pinned variation before approving HIGH/LOW/NONE and the impact capability. Until that decision, keep metadata omitted; type-supported is not low-impact-eligible, ready or approved. |
| `negative-pull-up` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human specify an eccentric-only sequence and a truthful starting-position method before approving the foundational negative progression. If a step/box is required, deliberately update its equipment contract later; current evidence author does not add one. |
| `neutral-grip-pull-up` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human resolve intended grip and require parallel handles or correct the intended variation through a separate content decision. Ratify whether Pull-up Bar with explicit neutral-handle qualification is acceptable schema fidelity before approval. |
| `nordic-hamstring-curl` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human choose an explicit secured ankle-restraint/Nordic apparatus requirement or leave this entry omitted. Then decide support, floor-entry and advanced knee-flexion policies from the chosen exact setup, not an improvised partner/barbell version. |
| `parallel-bar-dips` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human verify intended parallel-bar apparatus and ratify the unsupported/full-body-dip versus supported/L-sit distinction, or choose a consistent alternative. Approve advanced tier and Chest allocation only after that support-policy decision. |
| `pendlay-row` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human decide whether to retain a dead-stop Pendlay identity and supply exact coherent instructions in future content work, or interpret the picture as a different row. Ratify advanced tier and family only after resolving that identity. |
| `plank` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human choose full forearm plank versus knee modification as the authored timed variation and ratify the unchanged floor-access-only capability contract. |
| `prone-t-raise` | Human field-by-field metadata sign-off is still required; AI drafting, consensus, code review, tests and merge are not human approval. Human decide whether to retain an unloaded bodyweight T identity or the shown dumbbell-bench version in a separate content decision. Only after coherent source shape/equipment is established may a draft block be authored; current importer cannot accept weight_reps against this bodyweight_reps catalog entry. |
| `rack-pull` | Human must reconcile the exact named variation, complete setup and appropriate source illustrations before authoring and approving its metadata. |
| `reverse-hyperextension` | Human must reconcile the exact named variation, complete setup and appropriate source illustrations before authoring and approving its metadata. |
| `russian-twist` | Human must establish the exact pinned variation and complete apparatus/support/capability contract before authoring and approving metadata. |
| `seated-knee-tuck` | Human decision: identify the apparatus and choose Bench vs Machine (or a floor-seated Bodyweight variant), then author the withheld metadata block. |
| `shrimp-squat` | Human decision: define the shrimp squat as unsupported vs pole-assisted; if assisted, decide how (or whether) to represent the held pole, then author the block. |
| `single-arm-cable-row` | Human must establish the exact pinned variation and complete apparatus/support/capability contract before authoring and approving metadata. |
| `single-leg-box-squat` | Human decision: confirm supported + balance_without_support for a box target (vs a lighter tap-target reading); the Box equipment token itself is source-supported and settled. |
| `single-leg-calf-raise` | Human decision: add a support-surface token (or accept 'Wall'/'Machine') to encode the supported variant, OR define the exercise as unsupported (adding balance_without_support); then author the withheld block. |
| `sissy-squat` | Human decision: represent or accept the foot-anchor (equipment/support), or define a canonical unanchored variant, then author the withheld block. |
| `skater-hop` | Human decisions: (1) versioned product-policy impactLevel (low vs high) for plyometric bounds, applied consistently across all hop/jump drafts; (2) single directPrimary for 'Legs'; (3) confirm movementPattern=other and the 'impact' capability, then author the withheld block. |
| `skater-squat` | Human must reconcile the exact named variation, complete setup and appropriate source illustrations before authoring and approving its metadata. |
| `spider-curl` | Human resolution of the stated identity/schema gap before any metadata can be reviewed. Human must decide whether the record name/equipment or artwork is authoritative and whether artwork/catalog correction is required. |
| `sprawl` | Human resolution of the stated identity/schema gap before any metadata can be reviewed. Human must define WallCrawl's exact sprawl variant and decide whether it is distinct from squat-thrust. |
| `squat-thrust` | Human resolution of the stated identity/schema gap before any metadata can be reviewed. Human must resolve the sprawl/squat-thrust duplication and confirm whether floor_transition should remain. |
| `stability-ball-hamstring-curl` | Human resolution of the stated identity/schema gap before any metadata can be reviewed. Human must decide whether to replace/reassign artwork or rename/reclassify the record. |
| `sumo-deadlift` | Human field-by-field metadata sign-off; keep reviewState=draft, reviewerRole=null and reviewedAtEpochMillis=null until then. Human must ratify Hamstrings versus Glutes or Lower Back as direct primary before review can be ready. |
| `toe-touch` | Human resolution of the stated identity/schema gap before any metadata can be reviewed. Human must choose whether to correct artwork or change type, muscles and isStretch; only then can metadata be authored. |
| `towel-hamstring-curl` | Human resolution of the stated identity/schema gap before any metadata can be reviewed. Human must decide whether this record is a slider curl or a stretch and correct art/type accordingly. |
| `towel-row` | Human resolution of the stated identity/schema gap before any metadata can be reviewed. Human must define the intended anchor/self-resistance method and then choose schema equipment or add a schema token. |
| `trap-bar-deadlift` | Human must decide an exact Trap Bar equipment representation before authoring and approving metadata. No Barbell equivalence or approval is inferred. |
| `weighted-chin-up` | Human resolution of the stated identity/schema gap before any metadata can be reviewed. Human must ratify Biceps rather than Lats as direct primary and the incomplete belt representation. |
| `weighted-dip` | Human resolution of the stated identity/schema gap before any metadata can be reviewed. Human must ratify incomplete belt representation and vertical_push classification. |
| `weighted-pull-up` | Human resolution of the stated identity/schema gap before any metadata can be reviewed. Human must decide whether schema needs a Weight Belt token and how weighted-to-unweighted progression should be modeled across shapes. |
| `wrist-curl` | Human field-by-field metadata sign-off; keep reviewState=draft, reviewerRole=null and reviewedAtEpochMillis=null until then. Human must confirm the artwork depicts wrist flexion and approve foundational complexity. |
| `wrist-extension` | Human field-by-field metadata sign-off; keep reviewState=draft, reviewerRole=null and reviewedAtEpochMillis=null until then. Human must verify the intended wrist-extension motion and whether artwork needs replacement. |

## Artwork reference restrictions

**Unsuitable as references for new illustrations until reconciled:** the IDs below have source identity or setup conflicts. Do not use their current frames as references for female variants or other replacement illustrations before resolving those conflicts. The ledger retains each canonical ID, exact pinned source PNG paths, bundled SVG paths, citations and observations. No images or identities have been changed.

| Exercise ID | Observed reference conflict |
| --- | --- |
| `ab-wheel` | The source sequence mixes barbell/plate rollers with a single ab wheel and mixes kneeling with straight-leg/standing rollout positions. Choosing only one frame would silently select a different implement and leverage variant. Unsuitable as a reference for concurrent female-artwork authoring until the identity/setup conflict is reconciled; this evidence record grants no permission to change or swap artwork. |
| `assisted-dip` | Re-enlarged PNGs and original attributed record 0171 establish a seated moving-handle triceps machine press, not the previously asserted bench dip. Machine is consistent; the actual unresolved conflict is Workout Guide's assisted_bodyweight/assisted-dip identity versus the seated externally resisted press. Do not redraw a counterweighted body-lifting dip to fit the name. Unsuitable as a reference for concurrent female-artwork authoring until the identity/setup conflict is reconciled; this evidence record grants no permission to change or swap artwork. |
| `banded-frog-pump` | The PNGs predominantly show rear-hand-supported reclining/seated knee opening with separated feet, while the named frog-pump reference uses a supine glute bridge with soles together and elbows grounded. The inconsistent foot/trunk positions do not establish one coherent frog-bridge sequence. Unsuitable as a reference for concurrent female-artwork authoring until the identity/setup conflict is reconciled; this evidence record grants no permission to change or swap artwork. |
| `banded-hip-thrust` | The bench and thigh loop are consistent, but the parallel lines held across the lap do not establish whether extra resistance is a bar, a long band, or merely stylized detail. Redrawing a specific loading or anchoring setup would decide an unresolved source fact. Unsuitable as a reference for concurrent female-artwork authoring until the identity/setup conflict is reconciled; this evidence record grants no permission to change or swap artwork. |
| `bear-plank` | Frames 2/3 show a stationary bent-knee hover, while frame 1 extends one leg and advances the other knee in a crawl/mountain-climber-like posture. The first frame must not be treated as a proven phase of the same static timed hold. Unsuitable as a reference for concurrent female-artwork authoring until the identity/setup conflict is reconciled; this evidence record grants no permission to change or swap artwork. |
| `burpee` | The three PNGs show a straight-arm plank, crouch and jump, but no bent-elbow push-up. The exact burpee variant remains unresolved; artwork must not insert or omit a push-up based on the generic name before the content owner chooses the intended sequence. Unsuitable as a reference for concurrent female-artwork authoring until the identity/setup conflict is reconciled; this evidence record grants no permission to change or swap artwork. |
| `cable-crunch` | Frame 2 clearly kneels beneath a high pulley, whereas frames 1/3 show a feet-supported crouched/standing posture. Those variants change support, floor transition and balance demands; selecting one as the female reference would silently resolve the source contradiction. Unsuitable as a reference for concurrent female-artwork authoring until the identity/setup conflict is reconciled; this evidence record grants no permission to change or swap artwork. |
| `commando-pull-up` | All three full-resolution frames show separated hands on a transverse bar. They do not show the previously claimed close lengthwise grip or alternating head-side clearance. A conventional-looking pull cannot establish the named commando variation or its proposed coordination regression. Do not use these frames as an exact-variation reference for new artwork until the defining setup/movement is reconciled. |
| `donkey-calf-raise` | The source equipment label is Machine, but all three enlarged PNGs depict a human training partner seated over the lifter's hips, a foot block and held support. This is a partner-loaded donkey variant, not a complete machine setup. Do not use it as the reference for the app's Machine-labelled variant until equipment/variation identity is reconciled. |
| `hiking` | The Hiking identity conflicts with pinned PNG frames showing an indoor pedal-and-handle cardio machine rather than hiking. |
| `hip-airplane` | The Hip Airplane identity conflicts with pinned PNG frames showing a post-supported upright side-leg movement rather than a hinged pelvic rotation. |
| `hollow-body-hold` | The Hollow Body Hold identity conflicts with pinned PNG frames showing materially different V-sit and tuck positions. |
| `inverted-row` | The PNGs show an overhead bar and suspended feet/body rather than the named heels-on-floor row under a low bar. This is unsuitable as a reference for concurrent female-artwork authoring until row versus pull-up identity and the bar/foot support setup are reconciled. |
| `l-sit-hold` | The sequence mixes a straight-leg L-sit with bent-leg/tucked positions on the same bars rather than one stable hold variation. It is unsuitable for concurrent female-artwork authoring until the intended lever position and hold identity are reconciled. |
| `lying-hamstring-walkout` | The PNG contact sheet shows heels in suspended straps with curl/bridge motion, not alternating heel steps on the floor implied by lying hamstring walkout. This is unsuitable for concurrent female-artwork authoring until floor-walkout versus suspension-curl identity and anchor equipment are reconciled. |
| `negative-pull-up` | Displayed frame order ascends from hang to bar height and provides no eccentric-only cue or way to reach the starting top position. It is unsuitable for concurrent female-artwork authoring until lowering direction, rep identity and entry setup are reconciled; do not depict an ordinary ascending pull-up as a confirmed negative. |
| `neutral-grip-pull-up` | The PNGs show a single straight transverse bar with no visible parallel neutral-grip handles, conflicting with the named palms-facing-each-other variation. This is unsuitable for concurrent female-artwork authoring until grip identity and required handle setup are reconciled. |
| `pendlay-row` | The PNGs show a bent-over barbell row without a clear floor reset between reps or clearly horizontal torso, so the defining dead-stop Pendlay variation is unconfirmed. This is unsuitable for concurrent female-artwork authoring until exact reset/posture identity is reconciled; do not silently copy a generic row. |
| `plank` | Frames 1 and 2 show a forearm-and-toe plank, while frame 3 lowers both knees, mixing different lever/support variants under one timed hold. This is unsuitable for concurrent female-artwork authoring until the intended full-versus-knee plank identity is reconciled. |
| `plank-jack` | The excluded entry's first frame brings a knee forward and the remaining frames do not clearly show lateral feet-out/feet-in jacks; the sequence resembles a mountain climber. It is unsuitable for concurrent female-artwork authoring until jack-versus-climber identity is reconciled, regardless of its strength exclusion. |
| `prone-t-raise` | All three frames show a prone bench setup with dumbbells, contradicting source Bodyweight/bodyweight_reps for an unloaded T-raise. These are unsuitable references for concurrent female-artwork authoring until loaded-bench versus unloaded-bodyweight identity and prescription shape are reconciled; do not silently remove the weights or swap variants. |
| `rack-pull` | The three frames show a barbell near the thighs/knees and an unheld standing hinge, but no rack, pins, blocks or other elevated-start support. They do not establish the defining supported start of the named rack pull. Do not use these frames as an exact-variation reference for new artwork until the defining setup/movement is reconciled. |
| `reverse-hyperextension` | The frames show an inclined padded apparatus with the lower legs at rear rollers and the hands gripping front handles. The legs do not swing from hanging down to horizontal as previously claimed. The exact reverse-hyper identity and moving segment remain unresolved. Do not use these frames as an exact-variation reference for new artwork until the defining setup/movement is reconciled. |
| `seal-jack` | Artwork identity conflict: the manifest names a standing seal jack (Chest, Bodyweight, arms clapping in front) but all three frames show a subject seated on a legs-spread abduction-style machine. The illustration does not depict the named exercise and must not be used until reconciled upstream. |
| `single-arm-cable-row` | Frame1 shows the non-working hand holding the cable tower; later frames place that hand near the torso with different handle positioning. The sequence does not establish one coherent held-versus-unheld source setup for the proposed standing-to-seated regression. Do not use this mixed support sequence as an exact variation reference until reconciled. |
| `skater-squat` | Frame1 shows a unilateral bent-leg stance; frame2 shows both feet planted in a bilateral squat; frame3 shows a wide lateral stance with the other foot down. The sequence does not establish one consistent single-leg skater-squat variation or the proposed pistol-squat substitution. Do not use these frames as an exact-variation reference for new artwork until the defining setup/movement is reconciled. |
| `spider-curl` | The source names a dumbbell spider curl, while the frames show an upright preacher-pad curl with a plated curved/EZ-style bar. These pinned frames are unsuitable reference for concurrent female-artwork authoring until the catalog/artwork conflict is reconciled by a human. |
| `sprawl` | The frames stop at plank/crouch and do not show the chest/hips-to-floor or standing finish needed to distinguish this identity from squat-thrust. These pinned frames are unsuitable reference for concurrent female-artwork authoring until the catalog/artwork conflict is reconciled by a human. |
| `squat-thrust` | The frames stop at plank/crouch without an upright start or finish and are near-duplicates of sprawl, leaving the pictured identity unresolved. These pinned frames are unsuitable reference for concurrent female-artwork authoring until the catalog/artwork conflict is reconciled by a human. |
| `stability-ball-hamstring-curl` | The source names a supine heel-on-ball hamstring curl, while the frames show a prone hands-on-floor ball knee-tuck/pike. These pinned frames are unsuitable reference for concurrent female-artwork authoring until the catalog/artwork conflict is reconciled by a human. |
| `swimming` | The source names aquatic swimming with Cardio equipment, while the frames show prone work on an unidentified padded rail-based apparatus. These pinned frames are unsuitable reference for concurrent female-artwork authoring until the catalog/artwork conflict is reconciled by a human. |
| `toe-touch` | The source is a non-stretch Core repetition, while the frames show a standing forward-fold hamstring stretch. These pinned frames are unsuitable reference for concurrent female-artwork authoring until the catalog/artwork conflict is reconciled by a human. |
| `torso-twist-stretch` | The source is a Bodyweight stretch, while the frames show a seated weighted Russian twist holding a plate. These pinned frames are unsuitable reference for concurrent female-artwork authoring until the catalog/artwork conflict is reconciled by a human. |
| `towel-hamstring-curl` | The source names a towel hamstring curl repetition, while the frames show a towel-assisted supine straight-leg hamstring stretch. These pinned frames are unsuitable reference for concurrent female-artwork authoring until the catalog/artwork conflict is reconciled by a human. |
| `towel-row` | The frames show no anchor, partner, foot loop or other resistance source, so the pictured setup cannot substantiate the named row. These pinned frames are unsuitable reference for concurrent female-artwork authoring until the catalog/artwork conflict is reconciled by a human. |
