# Exercise Metadata Review Packet Implementation Plan

**Goal:** Produce a four-agent consensus review and human sign-off packet for all 37 exercise-metadata drafts, correcting the drafts without granting AI-authored approval.

**Architecture:** Opus, Grok, Gemini, and Terra independently review every entry against the bundled catalog, source material, and signed evidence doctrine. A convergence pass resolves disagreements into one auditable verdict per entry; accepted corrections remain `DRAFT`, regenerate catalog artifacts, and feed a human-only sign-off checklist.

**Tech Stack:** JSON, Python standard-library importer/tests, generated Markdown reports, Android catalog parser tests, GitHub pull-request review.

---

### Task 1: Run Independent Full-Cohort Reviews

**Files:**
- Read: `tools/workout-guide/reviewed-metadata.json`
- Read: `tools/workout-guide/review-schema.json`
- Read: `app/src/main/assets/workout-guide/catalog.json`
- Read: `docs/research/2026-08-29-training-science-evidence-review.md`
- Read: `docs/research/2026-08-29-roundtable-agent-findings.md`
- Create: `docs/research/2026-08-30-exercise-metadata-agent-review.md`

- [ ] **Step 1: Give every reviewer the complete 37-entry cohort**

Each reviewer returns one row per exercise with:

```text
exerciseId
verdict: READY_AS_WRITTEN | CORRECTION_REQUIRED | INSUFFICIENT_EVIDENCE
fieldFindings
proposedValues
evidence
confidence
```

- [ ] **Step 2: Require lens-specific verification**

Opus owns program construction; Grok owns capability, impact, support, and inclusivity; Gemini owns equipment, biomechanics, regressions, and substitutions; Terra owns provenance, deterministic-policy compatibility, and evidence boundaries. Every reviewer still evaluates every field.

- [ ] **Step 3: Preserve the independent record**

Write a compact per-agent findings section that distinguishes original findings from later consensus. Never represent model review as human approval.

- [ ] **Step 4: Commit the independent review record**

```bash
git add docs/research/2026-08-30-exercise-metadata-agent-review.md
git commit -m "docs: record exercise metadata agent review"
```

---

### Task 2: Converge and Apply Draft Corrections

**Files:**
- Modify: `tools/workout-guide/reviewed-metadata.json`
- Modify: `app/src/main/assets/workout-guide/catalog.json`
- Modify: `docs/reviewed-exercise-metadata-review.md`
- Test: `tools/workout-guide/test_import_catalog.py`
- Test: `tools/workout-guide/test_programming_overrides.py`
- Test: `app/src/androidTest/java/wallcrawl/elopenmike/com/core/exercise/workoutguide/WorkoutGuideCatalogParserTest.kt`

- [ ] **Step 1: Cross-review every disagreement**

Share the complete dossier with all four reviewers. Resolve each field by source evidence and signed policy, not majority vote.

- [ ] **Step 2: Define the accepted correction set**

For each correction, record old value, new value, evidence, agreeing agents, and why alternatives were rejected. Entries with unresolved evidence remain unchanged and receive `INSUFFICIENT_EVIDENCE`.

- [ ] **Step 3: Apply corrections without approval**

Keep every authored entry:

```json
{
  "reviewState": "draft",
  "provenance": {
    "reviewerRole": null,
    "reviewedAtEpochMillis": null
  }
}
```

Update only evidence-supported categorical fields, directed graph edges, and AI-draft rationale text.

- [ ] **Step 4: Regenerate deterministic artifacts**

```bash
python3 tools/workout-guide/import_catalog.py \
  --source /path/to/pinned/workout-guide
```

- [ ] **Step 5: Run metadata tests**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 tools/workout-guide/import_catalog.py \
  --source /path/to/pinned/workout-guide \
  --check
```

Expected: all tests pass, the importer reports no drift, the catalog remains 302 exercises, and review-state counts remain 37 draft / 0 approved.

- [ ] **Step 6: Commit corrections**

```bash
git add tools/workout-guide/reviewed-metadata.json \
  app/src/main/assets/workout-guide/catalog.json \
  docs/reviewed-exercise-metadata-review.md \
  tools/workout-guide app/src/androidTest
git commit -m "data: refine reviewed exercise metadata drafts"
```

---

### Task 3: Publish the Human Sign-Off Packet

**Files:**
- Create: `docs/reviewed-exercise-metadata-human-signoff.md`
- Modify: `docs/reviewed-exercise-metadata.md`
- Modify: `README.md`
- Modify: `docs/research/2026-08-30-exercise-metadata-agent-review.md`

- [ ] **Step 1: Write one human decision row per entry**

Each row includes final draft fields, agent consensus, evidence, unresolved caveats, and unchecked human choices:

```text
[ ] Approve as written
[ ] Request changes
Human reviewer role:
Review date:
Notes:
```

- [ ] **Step 2: State the approval boundary**

The packet must say that merging the PR, checking a Markdown box, or model consensus does not mutate `reviewState`. A later deliberate authored-data change with real human provenance performs approval.

- [ ] **Step 3: Link the packet**

Add concise links from the reviewed-metadata documentation and README without claiming the cohort is approved or production-enabled.

- [ ] **Step 4: Run complete verification**

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 tools/workout-guide/import_catalog.py \
  --source /path/to/pinned/workout-guide \
  --check
./gradlew test lint assembleDebug --stacktrace --no-daemon
./gradlew connectedDebugAndroidTest --no-daemon
git diff --check
```

- [ ] **Step 5: Run four-agent implementation review**

Opus 4.8, Grok 4.6, Gemini 3.7 Flash, and GPT-5.6 Terra review the complete diff independently. Fix valid findings, rerun verification, and repeat until all four report no remaining findings on the same SHA.

- [ ] **Step 6: Commit documentation**

```bash
git add README.md docs
git commit -m "docs: add exercise metadata human sign-off packet"
```
