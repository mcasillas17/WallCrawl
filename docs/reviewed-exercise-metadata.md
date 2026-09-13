# Reviewed exercise metadata

WallCrawl now carries two separate optional metadata blocks on an exercise. They
have different owners and must not be treated as interchangeable:

- `programming` is the legacy planner contract. Its 131 authored entries include
  equipment combinations, movement pattern, difficulty, mechanics, type-dependent rep range,
  progression type, alternatives, coaching text, and numeric `fatigueScore`.
  The current planner still consumes this block. The original 117 rep records and
  per-exercise prescriptions are preserved; 14 newly authored timed entries can affect
  ranking, equipment filtering, and coaching. Timed records use a null rep range; see
  [the timed programming contract](timed-hold-programming.md). `fatigueScore` is not part of the reviewed scientific contract and
  remains only until the later planner-policy migration.
- `reviewedMetadata` is WallCrawl-owned categorical input for the deterministic
  reviewed capability-eligibility gate. Production composition now sets that gate's
  local feature flag to `true`, and the current planner reads this block for
  production filtering, ranking, dose, progression, substitution, and validation
  wherever a record is **accepted** — `APPROVED` or `AI_ACCEPTED` — through the
  shared `Exercise.acceptedMetadata()` gate. See
  [reviewed capability eligibility](reviewed-capability-eligibility.md).

The complete 302-exercise catalog remains available to browsing and manual
templates whether either optional block is present or absent.

## Review state, AI acceptance, and human approval

`DRAFT` means the metadata has not received acceptance of either kind: no genuine
human review and no owner-authorized AI acceptance. An AI-authored entry defaults
to `DRAFT`, with no fabricated reviewer role or review time.

Two separate states may be **accepted** for automatic planning, and they must never be
conflated:

- `APPROVED` is human-only. It requires a deliberate authored-data change plus a
  non-empty reviewer role, review timestamp, rationale or source, schema version, and
  policy version, and it rejects any AI provenance outright. **Zero records carry this
  state.** Tooling validates the presence and shape of that provenance; it cannot
  authenticate a person's identity or turn pull-request approval, code review, merge,
  or a passing test suite into metadata approval.
- `AI_ACCEPTED` is an owner-authorized alpha categorical acceptance, recorded with its
  own `aiReviewProvenance` block (reviewer model id, review time, a SHA-256 digest of
  the exact content reviewed, HTTPS source references, decision rationale, and stated
  limitations) and with every human-review field left null. It is **not** human
  approval, **not** a clinical validation, and **not** a claim that an exercise is safe
  for everyone. It exists specifically so the two kinds of acceptance stay separately
  provenanced instead of one being represented as the other.

### AI acceptance audit

An owner-authorized audit accepted **182** of the catalog's 211 authored records as
`AI_ACCEPTED`. The remaining catalog resolves as an exhaustive, disjoint partition of
all 302 IDs:

| Group | Count | Meaning |
| --- | ---: | --- |
| `AI_ACCEPTED` | 182 | Accepted for automatic planning; owner-authorized, not human-approved |
| Authored `DRAFT` | 29 | Has a metadata block; not accepted |
| No authored block | 56 | No `reviewedMetadata` at all; not accepted |
| Outside automatic-strength scope | 35 | Stretches, distance-duration, and other timed-conditioning IDs; never proposed as strength work |

182 + 29 + 56 + 35 = 302, with no overlap and no ID left out.

Four IDs the audit could otherwise have accepted were deliberately withheld as
**intrinsic holds** — `cable-kickback`, `cable-standing-hip-abduction`,
`cable-standing-hip-adduction`, and `fire-hydrant` — and remain `DRAFT`. Seven accepted
sources — `archer-push-up`, `dumbbell-bent-over-row`, `hindu-push-up`,
`hip-adduction-machine`, `pistol-squat`, `push-up`, and `seated-row` — keep their
`AI_ACCEPTED` state while still naming a regression or substitution target that is
itself pending (`machine-row`, `cable-standing-hip-adduction`, `assisted-pistol-squat`,
or `knee-push-up`). This is expected: relationship authorization and endpoint
acceptance are independent decisions, so an accepted source's edge to a pending target
authorizes the relationship only — it grants no eligibility exception, no capability
evidence, and no substitution eligibility until the target is independently accepted.
Every one of the 182 accepted records still carries an **empty**
`clearedTrainingConstraints` list: the audit found no basis to record a joint-sensitivity
clearance for any exercise, so a joint-sensitive profile still receives a typed refusal
(see [reviewed capability eligibility](reviewed-capability-eligibility.md)).

The audit is a **committed, reproducible artifact**, not a one-time claim:
[`docs/research/2026-09-13-ai-acceptance-audit.json`](research/2026-09-13-ai-acceptance-audit.json)
is the original read-only report, preserved as canonical JSON and bound by its own
digest. [`tools/workout-guide/verify_ai_acceptance_audit.py`](../tools/workout-guide/verify_ai_acceptance_audit.py)
reconstructs the exact 302-ID partition from that committed report plus the current
evidence ledger, metadata, and catalog, offline and without any new source retrieval or
illustration inspection:

```bash
python3 tools/workout-guide/verify_ai_acceptance_audit.py
```

`app/src/test/resources/ai-acceptance/partition-v2.json` pins the same partition, the
audit's own SHA-256, and the actual reviewer/timestamp for JVM tests
(`AiAcceptedCorpusTest`, `AiAcceptedCatalogTest`) to assert against, so the fixture, the
committed audit, and the bundled catalog cannot silently drift apart.

Recorded per accepted record: `reviewerModelId` is `gpt-6-astra`; `reviewedAtEpochMillis`
is the audit's own original decision timestamp (not a placeholder and not the current
time); `reviewedContentSha256` binds the exact pre-disposition proposal that was
reviewed; `sourceReferences` are the same recorded evidence URLs the ledger already
cites. Nothing here is a clinical or medical review; it is a categorical product
decision about existing recorded evidence.

The generated [metadata report](reviewed-exercise-metadata-review.md) and
[human sign-off worksheet](reviewed-exercise-metadata-human-signoff.md) retain these
exact distinctions and are produced by tooling from the same sources; do not hand-edit
either without regenerating from `import_catalog.py` / `render_review_packet.py`. The
[37-entry review from August](research/2026-08-30-exercise-metadata-agent-review.md) and
the [September full-catalog review](research/2026-09-07-full-exercise-catalog-review.json)
remain historical evidence about content readiness — the `ready_for_human_review`
disposition they used predates AI acceptance and is not itself an acceptance record.
Later source inspection corrected or withheld drafts where complete equipment or the
depicted variation could not be established; earlier model consensus is not authoritative.

For each `DRAFT` entry, a human reviewer must still inspect:

- the single direct-primary muscle and descriptive-only secondary muscles;
- the joint sensitivities in `clearedTrainingConstraints`, if any;
- movement pattern, complexity, progression family, and prescription shape;
- every directed regression and substitution edge, including exception
  rationales;
- capability requirements, support requirement, impact level, and every
  equipment alternative;
- rationale/source, reviewer role, review time, schema version, and policy
  version.

Approval of a pull request does not rewrite any draft to `APPROVED`, and it does not
retroactively rewrite an `AI_ACCEPTED` record into one either.

## Categorical contract

The reviewed block uses bounded, typed values for review state, canonical muscle
names, `MovementPattern`, `ComplexityTier`, progression-family slugs,
`PrescriptionShape`, `MovementCapabilityType`, `SupportRequirement`,
`ImpactLevel`, `TrainingConstraint`, and `StandardEquipment`. Regression and
substitution edges are directed and may carry a bounded rationale for an explicit
exception.

`clearedTrainingConstraints` arrived with reviewed schema version 2. It is required on every
record and lists the joint sensitivities — shoulder, elbow, wrist, lower back, hip, knee —
that a reviewer explicitly cleared the exercise for. It is a compatibility statement about a
self-reported label, not a diagnosis, an injury rule, or clinical clearance. Absence is not
clearance: a selected sensitivity that a record does not list keeps that record out of
automatic planning. `LOW_IMPACT_ONLY` cannot appear there, because `impactLevel` already
decides it; the importer and the parser both reject a record that lists it. All 302
`reviewedMetadata` entries currently clear nothing — including the 182 `AI_ACCEPTED`
ones — so a joint-sensitive profile receives a typed refusal on the production path.
Nothing in this contract infers a clearance from a name, a muscle, a movement pattern or
equipment.

Version 2 also refreshed every `metadataSha256` in the evidence ledger, because the recorded
digest binds a proposal to the inspection of its exact fields. No human sign-off was voided
by that: `humanSignoff` is `null` for all 302 entries. Nothing is persisted per user and no
archive or Room migration is involved; the bundled catalog is regenerated from the authored
source and the app reads only that one asset.

`PRIMARY_ONLY_V1` has exactly one direct-primary muscle. Secondary muscles are
descriptive only, cannot repeat the direct primary, and receive no fractional
dose credit. The contract contains no numeric joint-stress, injury-risk,
stimulus-to-fatigue, axial-load, fatigue, body-mass-fraction, supported-mass,
BMI, or range-of-motion-superiority score. It also defines no universal
long-length-partial bonus.

## Authored data and trust boundary

The trust flow is:

```text
pinned source checkout + WallCrawl-authored JSON
  -> Python importer
  -> generated catalog.json
  -> Android streaming JSON parser
  -> typed Exercise.reviewedMetadata
  -> deterministic reviewed eligibility policy (production flag enabled)
```

`tools/workout-guide/reviewed-metadata.json` is the authored data source, at
schema version 2. `tools/workout-guide/review-schema.json` is its strict schema. The importer uses
Python standard-library validation and rejects unknown or duplicate fields,
missing fields, bad types/enums, unsafe or oversized values, non-finite numbers,
excessive depth/count/payload, unknown catalog IDs, catalog/type mismatches,
forbidden numeric pseudo-science fields, and invalid graph edges. Existing
pinned-commit, clean-source, HTTPS, symlink, path-containment, license, and
deterministic-output checks remain in force.

The Android `JsonReader` parser independently enforces the same reviewed-field
shape, enum, size, provenance, catalog/type, and graph rules after all exercise
IDs are known. Shared invalid fixtures exercise importer/parser parity. Parser
errors identify an exercise and field without echoing an entire untrusted
record. Unrelated legacy catalog fields retain their compatibility behavior.

## Graph rules

Regression targets must exist and carry reviewed metadata. Edges cannot repeat,
point to the source, or form a cycle. A target must preserve movement pattern,
direct-primary role, and compatible prescription semantics; it must be
categorically equal or easier in complexity, support demand, and required
capabilities. A cross-family edge needs an explicit bounded rationale.

Substitution targets also must exist, carry reviewed metadata, avoid self or
duplicate edges, and preserve prescription shape. A changed direct-primary or
movement role needs an explicit bounded rationale. Equipment alternatives are
non-empty combinations of canonical equipment values. Substitutions are
directed; a reverse relationship exists only when separately authored and
validated.

## Full-catalog review and current behavior

The deterministic report records 211 authored entries — 182 `AI_ACCEPTED` and 29
`DRAFT` — across bodyweight, bands, machines/cables, dumbbells, barbells, kettlebells
and supported-equipment families. The ledger records exact original source facts, the
three inspected PNG source illustrations and their pinned paths, field-group citations,
categorical reasoning, corrections, confidence, limitations and remaining human
decisions for every ID. Each present proposal is bound to its authored metadata
SHA-256. It separately flags unresolved source/illustration conflicts as unsuitable
references for new artwork until reconciled; the bundled artwork itself is unchanged.

The current code classifies 267 entries as type-supported and excludes 14
stretches, 10 distance-duration entries and 11 other timed-conditioning entries.
The excluded entries still receive individual factual review; they are not given
manufactured direct-primary allocations or repetition prescriptions. Type support,
single-candidate reachability, full-pool availability, AI acceptance and human
approval are distinct, as the [coverage report](reviewed-catalog-coverage.md) explains.

Five of the six fixed-anchor band variants now have explicit runtime equipment
minimums in the [fixed-anchor contract](band-anchor-equipment.md); the row's
off-image anchor remains unresolved. Their full reviewed blocks stay withheld:
this equipment correction does not author or accept their other categorical
fields or promote any graph edge. Specialized fixtures, conflicting movement
depictions and some primary/impact decisions remain unresolved. A generic wall, doorway, bench
or machine is not used to conceal an unrepresented requirement. Broader equipment
categories also do not prove ownership of every machine subtype or bench configuration.
Band-only PUSH coverage remains a specific open gap, not a reason to relabel
back/core movements or imply universal modality equivalence.

The importer produces both the catalog and report deterministically; `--check`
detects drift without writing. Regression tests confirm the catalog holds 302 entries,
that production selection is built only from the actual `AI_ACCEPTED` cohort in
representative bodyweight, band, machine, and full-gym contexts, and that no legacy
fallback exists when the reviewed pool is insufficient. Fixtures prove that absent,
`DRAFT`, and unaccepted blocks cannot enter the reviewed automatic pool and that typed
no-candidate causes are preserved without fallback.

The focused policy, rollout boundary, typed results, constraint gap, and
verification commands are documented in
[Reviewed capability eligibility](reviewed-capability-eligibility.md). The next
integration milestone is genuine human review and approval of the metadata — the
reviewed path is already enabled and already reads the audited `AI_ACCEPTED` cohort.
Approval does not happen automatically when reviewed entries appear.
