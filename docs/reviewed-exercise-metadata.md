# Reviewed exercise metadata

WallCrawl now carries two separate optional metadata blocks on an exercise. They
have different owners and must not be treated as interchangeable:

- `programming` is the legacy planner contract. Its 117 authored entries include
  equipment combinations, movement pattern, difficulty, mechanics, rep range,
  progression type, alternatives, coaching text, and numeric `fatigueScore`.
  The current planner still consumes this block so existing workout behavior is
  preserved. `fatigueScore` is not part of the reviewed scientific contract and
  remains only until the later planner-policy migration.
- `reviewedMetadata` is WallCrawl-owned categorical input for a future
  deterministic capability-eligibility gate. It is optional, and no current
  filtering, ranking, dose, progression, substitution, or validation behavior
  reads it.

The complete 302-exercise catalog remains available to browsing and manual
templates whether either optional block is present or absent.

## Review state and human approval

`DRAFT` means the metadata has not received the required human review. An
AI-authored entry must remain `DRAFT`, with no fabricated reviewer role or review
time. `APPROVED` requires a deliberate authored-data change plus a non-empty
reviewer role, review timestamp, rationale or source, schema version, and policy
version. Tooling validates the presence and shape of that provenance; it cannot
authenticate a person's identity or turn pull-request approval into metadata
approval.

The initial 37-entry cohort is entirely AI-authored and `DRAFT`. It is therefore
not eligible for the future reviewed-only gate until a human inspects each entry
and deliberately approves it. The generated
[review report](reviewed-exercise-metadata-review.md) lists every pending entry,
coverage counts, and any required regression gap.

For each draft, a human reviewer must inspect:

- the single direct-primary muscle and descriptive-only secondary muscles;
- movement pattern, complexity, progression family, and prescription shape;
- every directed regression and substitution edge, including exception
  rationales;
- capability requirements, support requirement, impact level, and every
  equipment alternative;
- rationale/source, reviewer role, review time, schema version, and policy
  version.

Approval of this pull request does not rewrite any draft to `APPROVED`.

## Categorical contract

The reviewed block uses bounded, typed values for review state, canonical muscle
names, `MovementPattern`, `ComplexityTier`, progression-family slugs,
`PrescriptionShape`, `MovementCapabilityType`, `SupportRequirement`,
`ImpactLevel`, and `StandardEquipment`. Regression and substitution edges are
directed and may carry a bounded rationale for an explicit exception.

`PRIMARY_ONLY_V1` has exactly one direct-primary muscle. Secondary muscles are
descriptive only, cannot repeat the direct primary, and receive no fractional
dose credit. The contract contains no numeric joint-stress, injury-risk,
stimulus-to-fatigue, axial-load, fatigue, body-mass-fraction, supported-mass,
BMI, or range-of-motion-superiority score. It also defines no universal
long-length-partial bonus.

## Authored data and trust boundary

The trust flow is:

```text
pinned Workout Guide checkout + WallCrawl-authored JSON
  -> Python importer
  -> generated catalog.json
  -> Android streaming JSON parser
  -> typed Exercise.reviewedMetadata
  -> future deterministic planner policy
```

`tools/workout-guide/reviewed-metadata.json` is the authored data source.
`tools/workout-guide/review-schema.json` is its strict schema. The importer uses
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

## Initial cohort and current behavior

The deterministic report currently records 37 draft entries across bodyweight,
bands, machines/cables, dumbbells, barbells, kettlebells, and supported-equipment
families. It includes bodyweight and horizontal pushes, supported and unsupported
squats/lunges, vertical pulls/hangs and assisted pull-ups, horizontal pulls,
hinges, timed core holds, low-impact choices, supported regressions, and
assisted-bodyweight progressions.

The importer produces both the catalog and report deterministically; `--check`
detects drift without writing. Regression tests hold the catalog at 302 entries
and prove absent, `DRAFT`, and `APPROVED` reviewed blocks produce the same current
planner output in representative bodyweight, band, machine, and full-gym
contexts. Movement-capability values likewise remain unused by the planner.

The next integration milestone is human review and deliberate approval of the
cohort, followed by a separate reviewed-only capability-eligibility policy. That
policy is not implemented here.
