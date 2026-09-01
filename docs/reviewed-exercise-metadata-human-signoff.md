# Reviewed Exercise Metadata Human Sign-off

This worksheet is generated from the current draft values in `tools/workout-guide/reviewed-metadata.json` for the 37-entry reviewed-metadata cohort.

> **Warning:** Checking boxes in this Markdown file is only a review worksheet. It does **not** mutate `reviewState`, does **not** mutate provenance, and does **not** approve metadata. Approval requires a later deliberate authored-data change with a real reviewer role, real review time, and updated provenance.

Packet status today:

- All 37 entries are ready for human inspection, not approved.
- Roundtable verdict counts: 22 `READY_AFTER_CORRECTIONS`, 15 `READY_AS_WRITTEN`.
- Source basis for every section: pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` plus WallCrawl product-policy fields.

## Required sign-off output

This worksheet is the entry-by-entry evidence surface, not the approval mechanism. A
traceable human sign-off is complete only when all of the following happen for each entry
being approved:

1. A human checks **Approve this entry as written** below and records their real reviewer
   role, review date, and any caveat needed to understand that decision. Entries marked
   **Request changes** remain `DRAFT`.
2. A later deliberate change to `tools/workout-guide/reviewed-metadata.json` promotes only
   those approved IDs, sets `provenance.reviewerRole` to the recorded real role, sets
   `reviewedAtEpochMillis` to the real review time, and updates `rationaleOrSource` so the
   decision is traceable to this packet and the entry's source basis.
3. The importer regenerates `app/src/main/assets/workout-guide/catalog.json` and
   `docs/reviewed-exercise-metadata-review.md`; importer and Android parser validation must
   pass without bypassing provenance or graph rules.
4. Production enablement remains a separate decision after approved-catalog availability
   and persona coverage are reviewed. Metadata sign-off does not flip the feature flag.

Do not batch-promote unchecked entries, reuse one timestamp as a stand-in for review that
did not occur, infer approval from a merged pull request, or replace the reviewer role
with a model/agent identity. Until the authored JSON change is present and traceable to
the relevant rows below, the cohort remains unsigned.

## Entry-by-entry sign-off worksheet

### assisted-pistol-squat

- Final draft values:
  - Direct primary: `Quadriceps`
  - Descriptive secondaries: `Glutes`, `Hamstrings`, `Core`
  - Pattern: `squat`
  - Complexity: `standard`
  - Family: `single-leg-squat`
  - Shape: `bodyweight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Bodyweight", "Wall"]`; `["Bodyweight", "Chair"]`
  - Provenance rationale: AI-authored DRAFT for assisted-pistol-squat: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### assisted-pull-up

- Final draft values:
  - Direct primary: `Lats`
  - Descriptive secondaries: `Biceps`
  - Pattern: `vertical_pull`
  - Complexity: `foundational`
  - Family: `vertical-pull`
  - Shape: `assisted_bodyweight`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: `vertical_pull_or_hang`
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Machine"]`
  - Provenance rationale: AI-authored DRAFT for assisted-pull-up: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### band-pull-apart

- Final draft values:
  - Direct primary: `Upper Back`
  - Descriptive secondaries: `Rear Delts`, `Shoulders`
  - Pattern: `horizontal_pull`
  - Complexity: `foundational`
  - Family: `band-horizontal-pull`
  - Shape: `bodyweight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: None
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Resistance Band"]`
  - Provenance rationale: AI-authored DRAFT for band-pull-apart: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### banded-glute-bridge

- Final draft values:
  - Direct primary: `Glutes`
  - Descriptive secondaries: `Hamstrings`, `Core`
  - Pattern: `hinge`
  - Complexity: `standard`
  - Family: `glute-bridge`
  - Shape: `bodyweight_reps`
  - Approved regressions: `glute-bridge`
  - Approved substitutions: None
  - Capabilities: `floor_transition`
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Resistance Band"]`
  - Provenance rationale: AI-authored DRAFT for banded-glute-bridge: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### banded-lat-pulldown

- Final draft values:
  - Direct primary: `Lats`
  - Descriptive secondaries: `Biceps`, `Core`
  - Pattern: `vertical_pull`
  - Complexity: `foundational`
  - Family: `band-vertical-pull`
  - Shape: `bodyweight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Resistance Band", "Chair"]`
  - Provenance rationale: AI-authored DRAFT for banded-lat-pulldown: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### banded-pallof-press

- Final draft values:
  - Direct primary: `Core`
  - Descriptive secondaries: `Glutes`, `Shoulders`
  - Pattern: `core`
  - Complexity: `foundational`
  - Family: `anti-rotation-core`
  - Shape: `bodyweight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Resistance Band"]`
  - Provenance rationale: AI-authored DRAFT for banded-pallof-press: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### banded-row

- Final draft values:
  - Direct primary: `Back`
  - Descriptive secondaries: `Biceps`, `Upper Back`
  - Pattern: `horizontal_pull`
  - Complexity: `foundational`
  - Family: `band-horizontal-row`
  - Shape: `bodyweight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: None
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Resistance Band"]`
  - Provenance rationale: AI-authored DRAFT for banded-row: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### banded-squat

- Final draft values:
  - Direct primary: `Quadriceps`
  - Descriptive secondaries: `Glutes`, `Hamstrings`, `Core`
  - Pattern: `squat`
  - Complexity: `foundational`
  - Family: `bodyweight-squat`
  - Shape: `bodyweight_reps`
  - Approved regressions: None
  - Approved substitutions: `bodyweight-squat`
  - Capabilities: `unsupported_squat`, `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Resistance Band"]`
  - Provenance rationale: AI-authored DRAFT for banded-squat: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### barbell-back-squat

- Final draft values:
  - Direct primary: `Quadriceps`
  - Descriptive secondaries: `Glutes`, `Core`
  - Pattern: `squat`
  - Complexity: `advanced`
  - Family: `loaded-squat`
  - Shape: `weight_reps`
  - Approved regressions: `goblet-squat`
  - Approved substitutions: None
  - Capabilities: `unsupported_squat`, `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Barbell", "Squat Rack"]`
  - Provenance rationale: AI-authored DRAFT for barbell-back-squat: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### barbell-bench-press

- Final draft values:
  - Direct primary: `Chest`
  - Descriptive secondaries: `Triceps`, `Shoulders`
  - Pattern: `horizontal_push`
  - Complexity: `advanced`
  - Family: `loaded-horizontal-push`
  - Shape: `weight_reps`
  - Approved regressions: `dumbbell-bench-press`
  - Approved substitutions: None
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Barbell", "Bench", "Squat Rack"]`
  - Provenance rationale: AI-authored DRAFT for barbell-bench-press: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### barbell-deadlift

- Final draft values:
  - Direct primary: `Hamstrings`
  - Descriptive secondaries: `Glutes`, `Lower Back`, `Back`, `Forearms`
  - Pattern: `hinge`
  - Complexity: `advanced`
  - Family: `loaded-hinge`
  - Shape: `weight_reps`
  - Approved regressions: `dumbbell-romanian-deadlift`
  - Approved substitutions: None
  - Capabilities: `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Barbell"]`
  - Provenance rationale: AI-authored DRAFT for barbell-deadlift: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: Human ratification required on `directPrimaryMuscle=Hamstrings`; the pinned source says `Posterior Chain`.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### bodyweight-squat

- Final draft values:
  - Direct primary: `Quadriceps`
  - Descriptive secondaries: `Glutes`, `Hamstrings`, `Core`
  - Pattern: `squat`
  - Complexity: `foundational`
  - Family: `bodyweight-squat`
  - Shape: `bodyweight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: `unsupported_squat`, `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Bodyweight"]`
  - Provenance rationale: AI-authored DRAFT for bodyweight-squat: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### cable-pallof-hold

- Final draft values:
  - Direct primary: `Core`
  - Descriptive secondaries: `Glutes`, `Shoulders`
  - Pattern: `core`
  - Complexity: `foundational`
  - Family: `anti-rotation-hold`
  - Shape: `duration`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Cable"]`
  - Provenance rationale: AI-authored DRAFT for cable-pallof-hold: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### cable-pull-through

- Final draft values:
  - Direct primary: `Glutes`
  - Descriptive secondaries: `Hamstrings`, `Lower Back`
  - Pattern: `hinge`
  - Complexity: `foundational`
  - Family: `cable-hinge`
  - Shape: `weight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Cable"]`
  - Provenance rationale: AI-authored DRAFT for cable-pull-through: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### dead-hang

- Final draft values:
  - Direct primary: `Forearms`
  - Descriptive secondaries: `Lats`, `Shoulders`, `Core`
  - Pattern: `vertical_pull`
  - Complexity: `foundational`
  - Family: `vertical-hang`
  - Shape: `duration`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: `vertical_pull_or_hang`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Pull-up Bar"]`
  - Provenance rationale: AI-authored DRAFT for dead-hang: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### dumbbell-bench-press

- Final draft values:
  - Direct primary: `Chest`
  - Descriptive secondaries: `Triceps`, `Shoulders`
  - Pattern: `horizontal_push`
  - Complexity: `standard`
  - Family: `loaded-horizontal-push`
  - Shape: `weight_reps`
  - Approved regressions: `machine-chest-press`
  - Approved substitutions: None
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Dumbbell", "Bench"]`
  - Provenance rationale: AI-authored DRAFT for dumbbell-bench-press: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### dumbbell-bent-over-row

- Final draft values:
  - Direct primary: `Back`
  - Descriptive secondaries: `Biceps`, `Rear Delts`
  - Pattern: `horizontal_pull`
  - Complexity: `standard`
  - Family: `loaded-horizontal-row`
  - Shape: `weight_reps`
  - Approved regressions: `machine-row`
  - Approved substitutions: None
  - Capabilities: `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Dumbbell"]`
  - Provenance rationale: AI-authored DRAFT for dumbbell-bent-over-row: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### dumbbell-romanian-deadlift

- Final draft values:
  - Direct primary: `Hamstrings`
  - Descriptive secondaries: `Glutes`, `Lower Back`
  - Pattern: `hinge`
  - Complexity: `standard`
  - Family: `loaded-hinge`
  - Shape: `weight_reps`
  - Approved regressions: `smith-machine-romanian-deadlift`
  - Approved substitutions: None
  - Capabilities: `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Dumbbell"]`
  - Provenance rationale: AI-authored DRAFT for dumbbell-romanian-deadlift: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### glute-bridge

- Final draft values:
  - Direct primary: `Glutes`
  - Descriptive secondaries: `Hamstrings`
  - Pattern: `hinge`
  - Complexity: `foundational`
  - Family: `glute-bridge`
  - Shape: `bodyweight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: `floor_transition`
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Bodyweight"]`
  - Provenance rationale: AI-authored DRAFT for glute-bridge: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### goblet-squat

- Final draft values:
  - Direct primary: `Quadriceps`
  - Descriptive secondaries: `Glutes`, `Core`
  - Pattern: `squat`
  - Complexity: `standard`
  - Family: `loaded-squat`
  - Shape: `weight_reps`
  - Approved regressions: `leg-press`
  - Approved substitutions: None
  - Capabilities: `unsupported_squat`, `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Dumbbell"]`; `["Kettlebell"]`
  - Provenance rationale: AI-authored DRAFT for goblet-squat: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### kettlebell-romanian-deadlift

- Final draft values:
  - Direct primary: `Hamstrings`
  - Descriptive secondaries: `Glutes`, `Lower Back`
  - Pattern: `hinge`
  - Complexity: `standard`
  - Family: `loaded-hinge`
  - Shape: `weight_reps`
  - Approved regressions: `smith-machine-romanian-deadlift`
  - Approved substitutions: None
  - Capabilities: `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Kettlebell"]`
  - Provenance rationale: AI-authored DRAFT for kettlebell-romanian-deadlift: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### knee-push-up

- Final draft values:
  - Direct primary: `Chest`
  - Descriptive secondaries: `Triceps`, `Shoulders`, `Core`
  - Pattern: `horizontal_push`
  - Complexity: `foundational`
  - Family: `bodyweight-horizontal-push`
  - Shape: `bodyweight_reps`
  - Approved regressions: `wall-push-up`
  - Approved substitutions: None
  - Capabilities: `upper_body_bodyweight_push`, `floor_transition`
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Bodyweight"]`
  - Provenance rationale: AI-authored DRAFT for knee-push-up: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### leg-press

- Final draft values:
  - Direct primary: `Quadriceps`
  - Descriptive secondaries: `Glutes`, `Hamstrings`
  - Pattern: `squat`
  - Complexity: `foundational`
  - Family: `loaded-squat`
  - Shape: `weight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Machine"]`
  - Provenance rationale: AI-authored DRAFT for leg-press: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### machine-chest-press

- Final draft values:
  - Direct primary: `Chest`
  - Descriptive secondaries: `Triceps`, `Shoulders`
  - Pattern: `horizontal_push`
  - Complexity: `foundational`
  - Family: `loaded-horizontal-push`
  - Shape: `weight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Machine"]`
  - Provenance rationale: AI-authored DRAFT for machine-chest-press: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### machine-row

- Final draft values:
  - Direct primary: `Back`
  - Descriptive secondaries: `Biceps`
  - Pattern: `horizontal_pull`
  - Complexity: `foundational`
  - Family: `loaded-horizontal-row`
  - Shape: `weight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Machine"]`
  - Provenance rationale: AI-authored DRAFT for machine-row: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### negative-pull-up

- Final draft values:
  - Direct primary: `Lats`
  - Descriptive secondaries: `Biceps`, `Upper Back`, `Forearms`
  - Pattern: `vertical_pull`
  - Complexity: `foundational`
  - Family: `vertical-pull`
  - Shape: `bodyweight_reps`
  - Approved regressions: `assisted-pull-up`
  - Approved substitutions: None
  - Capabilities: `vertical_pull_or_hang`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Pull-up Bar"]`
  - Provenance rationale: AI-authored DRAFT for negative-pull-up: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### pistol-squat

- Final draft values:
  - Direct primary: `Quadriceps`
  - Descriptive secondaries: `Glutes`, `Hamstrings`, `Core`
  - Pattern: `squat`
  - Complexity: `advanced`
  - Family: `single-leg-squat`
  - Shape: `bodyweight_reps`
  - Approved regressions: `assisted-pistol-squat`
  - Approved substitutions: None
  - Capabilities: `unsupported_squat`, `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Bodyweight"]`
  - Provenance rationale: AI-authored DRAFT for pistol-squat: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### plank

- Final draft values:
  - Direct primary: `Core`
  - Descriptive secondaries: `Shoulders`
  - Pattern: `core`
  - Complexity: `foundational`
  - Family: `plank-hold`
  - Shape: `duration`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: `floor_transition`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Bodyweight"]`
  - Provenance rationale: AI-authored DRAFT for plank: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### pull-ups

- Final draft values:
  - Direct primary: `Lats`
  - Descriptive secondaries: `Biceps`, `Core`
  - Pattern: `vertical_pull`
  - Complexity: `standard`
  - Family: `vertical-pull`
  - Shape: `bodyweight_reps`
  - Approved regressions: `negative-pull-up`
  - Approved substitutions: None
  - Capabilities: `vertical_pull_or_hang`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Pull-up Bar"]`
  - Provenance rationale: AI-authored DRAFT for pull-ups: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### push-up

- Final draft values:
  - Direct primary: `Chest`
  - Descriptive secondaries: `Triceps`, `Core`
  - Pattern: `horizontal_push`
  - Complexity: `standard`
  - Family: `bodyweight-horizontal-push`
  - Shape: `bodyweight_reps`
  - Approved regressions: `knee-push-up`
  - Approved substitutions: None
  - Capabilities: `upper_body_bodyweight_push`, `floor_transition`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Bodyweight"]`
  - Provenance rationale: AI-authored DRAFT for push-up: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### seated-row

- Final draft values:
  - Direct primary: `Back`
  - Descriptive secondaries: `Biceps`, `Rear Delts`
  - Pattern: `horizontal_pull`
  - Complexity: `foundational`
  - Family: `loaded-horizontal-row`
  - Shape: `weight_reps`
  - Approved regressions: None
  - Approved substitutions: `machine-row`
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Cable"]`
  - Provenance rationale: AI-authored DRAFT for seated-row: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### side-plank

- Final draft values:
  - Direct primary: `Core`
  - Descriptive secondaries: `Shoulders`
  - Pattern: `core`
  - Complexity: `standard`
  - Family: `plank-hold`
  - Shape: `duration`
  - Approved regressions: `plank`
  - Approved substitutions: None
  - Capabilities: `floor_transition`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Bodyweight"]`
  - Provenance rationale: AI-authored DRAFT for side-plank: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### smith-machine-romanian-deadlift

- Final draft values:
  - Direct primary: `Hamstrings`
  - Descriptive secondaries: `Glutes`, `Lower Back`
  - Pattern: `hinge`
  - Complexity: `foundational`
  - Family: `loaded-hinge`
  - Shape: `weight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Machine"]`
  - Provenance rationale: AI-authored DRAFT for smith-machine-romanian-deadlift: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### smith-machine-split-squat

- Final draft values:
  - Direct primary: `Quadriceps`
  - Descriptive secondaries: `Glutes`, `Core`
  - Pattern: `lunge`
  - Complexity: `foundational`
  - Family: `loaded-split-squat`
  - Shape: `weight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Machine"]`
  - Provenance rationale: AI-authored DRAFT for smith-machine-split-squat: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### split-squat

- Final draft values:
  - Direct primary: `Quadriceps`
  - Descriptive secondaries: `Glutes`, `Core`
  - Pattern: `lunge`
  - Complexity: `standard`
  - Family: `loaded-split-squat`
  - Shape: `weight_reps`
  - Approved regressions: `smith-machine-split-squat`
  - Approved substitutions: None
  - Capabilities: `balance_without_support`
  - Support: `unsupported`
  - Impact: `none`
  - Equipment: `["Dumbbell"]`
  - Provenance rationale: AI-authored DRAFT for split-squat: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AFTER_CORRECTIONS`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### wall-push-up

- Final draft values:
  - Direct primary: `Chest`
  - Descriptive secondaries: `Triceps`, `Shoulders`
  - Pattern: `horizontal_push`
  - Complexity: `foundational`
  - Family: `bodyweight-horizontal-push`
  - Shape: `bodyweight_reps`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: `upper_body_bodyweight_push`
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Wall"]`
  - Provenance rationale: AI-authored DRAFT for wall-push-up: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________

### wall-sit

- Final draft values:
  - Direct primary: `Quadriceps`
  - Descriptive secondaries: `Glutes`, `Core`
  - Pattern: `squat`
  - Complexity: `foundational`
  - Family: `supported-squat-hold`
  - Shape: `duration`
  - Approved regressions: None
  - Approved substitutions: None
  - Capabilities: None
  - Support: `supported`
  - Impact: `none`
  - Equipment: `["Bodyweight", "Wall"]`
  - Provenance rationale: AI-authored DRAFT for wall-sit: pinned Workout Guide ba0b709cb20430361b2cb33aaadd20998164a916 manifest/artwork supports muscles, prescription shape, and equipment; WallCrawl policy supplies pattern, complexity, family, capabilities, support, impact, and graph edges. Human field-by-field review required.
- Roundtable verdict: `READY_AS_WRITTEN`
- Source basis: Pinned Workout Guide manifest/artwork at `ba0b709cb20430361b2cb33aaadd20998164a916` for source-derived muscles, prescription shape mapped from exercise type, and listed equipment; WallCrawl product-policy fields for movement pattern, complexity, progression family, capability requirements, support requirement, impact level, and directed graph edges.
- Unresolved caveat: None.
- [ ] Approve this entry as written
- [ ] Request changes
- Human reviewer role: __________________
- Review date: __________________
- Notes: __________________
