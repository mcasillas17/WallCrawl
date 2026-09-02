# 2026-09-02 Roadmap Audit

> **Audit record:** Current status, priority, and dependency order live in the
> [canonical roadmap](../../../ROADMAP.md). This document preserves the evidence behind that
> roadmap.

This records an audit of the existing plans against the code at `d8fc5a4`. Nothing was
removed. Statuses were corrected, verified steps were ticked, and gaps that no plan tracked
were added as new tasks.

## Method, and why checkboxes were not trusted

Completion was determined from the code, not from checkbox state. Checkbox counts are
actively misleading in this repository: several plans show zero ticked steps for work that
demonstrably shipped.

| Plan | Ticked steps | Actually shipped |
| --- | ---: | --- |
| `2026-08-30-dependabot-enablement.md` | 0 | Yes — `.github/dependabot.yml` exists, and #35–#44 were its output |
| `2026-08-30-deterministic-planner-evaluation-corpus.md` | 0 | Yes — 33 fixtures, manifest, loader, evaluator, corpus test |
| `2026-09-01-experience-difficulty-ranking.md` | 0 | Yes — `ExerciseDifficultyRankingPolicy.kt` shipped in #48 |
| `2026-09-01-training-program-state.md` | 0 | Yes — shipped in #49 |
| `2026-08-26-custom-workout-templates.md` | 0 | Yes — template editor and repository exist |

Anyone reading checkbox totals would conclude roughly half the shipped product is unbuilt.
Ticking every historical box was out of scope for this audit, so **treat a plan's `Status:`
header as authoritative and its checkboxes as unreliable** until each plan is reconciled.

## Verified state of the deterministic engine

Checked by file existence under `core/ai/` and by reading the implementations.

| Task | Verified state |
| --- | --- |
| 1 — Reviewed metadata | Foundation complete. 302 exercises, 37 `reviewedMetadata` entries, **all `DRAFT`, zero `APPROVED`** |
| 2 — Reviewed eligibility | Shipped. `ExerciseEligibilityPolicy.kt`, production flag `false` |
| 3 — Weekly dose ledger | Shipped, composed, and consumed. `WeeklyDoseLedgerCalculator.kt`, `LedgerSourceFingerprint.kt`, `TrainingProgramStateProvider.kt` |
| 4 — State-based dose/effort/rest | Shipped. `StateBasedTrainingPolicy.kt`, reads `weeklyLedger.directPrimarySets` |
| 5 — Typed gym-floor feedback | Shipped. RPE/RIR, typed stop reasons, rest timer, schema 9 columns |
| 6 — Capability evidence, progression, deload | **Not started.** None of the three files exist |
| 7 — Session and weekly validation | **Not started.** `ProgramValidator.kt` does not exist |
| 8 — Persona evaluation and release gate | **Partly shipped.** Corpus, harness, and CI exist; one persona and several gates missing |

Room schema is at **version 11** with a continuous `MIGRATION_1_2` … `MIGRATION_10_11` chain.

## Gaps added by this audit

Each was previously untracked by any plan.

### 1. The Progress card contradicts `PRIMARY_ONLY_V1` — engine plan, Task 9

The Progress screen already answers "how many sets for this muscle this week", and it answers
differently from the ledger on crediting, week boundary, and metadata gating. The conflict is
invisible today only because no entry is `APPROVED`; approving metadata makes it visible.

### 2. Approval is an undocumented dependency of Tasks 2–4 — engine plan, Task 10

Three shipped tasks are inert because they all require `APPROVED` metadata. That dependency
previously existed only as one unticked step inside Task 1, which understated it: **no further
engine task changes the product until approval happens.** Task 10 now states the whole path,
including the single open ratification question (`barbell-deadlift`).

### 3. Widening adaptation state is coupled to a safety ceiling — noted in engine Task 6

`ExerciseEligibilityPolicy` applies the advanced-complexity ceiling with an allow-by-default
check on exactly `UNCALIBRATED` and `RETURNING`, so **any additional derived adaptation state
lifts it**. Task 6 is the first task that will want more states, so the constraint is recorded
there. A regression test already fails if the policy widens on its own.

### 4. Shipped behavior contradicts a documented privacy promise — product plan, Task 11

`AndroidManifest.xml:6` ships `android:allowBackup="true"` with no `dataExtractionRules` and
no `fullBackupContent`, so Android Auto Backup is eligible to copy the workout database off
the device. `docs/weekly-dose-ledger.md` and `docs/reviewed-capability-eligibility.md` both
claim unconditional local-only behavior.

This is the **only** identified gap where currently shipping behavior conflicts with a
documented promise; every other open item is inert behind a disabled flag.

### 5. Missing coverage found inside Task 8

- The `concurrent-activity` persona fixture is the one of ten never added.
- Corpus assertions predate the ledger and dose policy, so they do not yet assert the
  primary-only ledger or no-invented-load gates that Task 8 lists.
- The importer drift check cannot run in CI: `import_catalog.py --check` needs a Workout Guide
  checkout pinned at the catalog's `source.commit`, and no CI step provides one.

## Corrections to the product plan's audit table

Four rows of the audit table in `2026-08-28-adaptive-coach-product.md` were out of date. The
original text was left in place and corrected in a dated section beneath it, so the record of
what was believed at the time survives. Two rows were confirmed still accurate.

## Consolidated roadmap

The audited status and dependency order are now maintained in the
[canonical WallCrawl roadmap](../../../ROADMAP.md). This file remains the dated evidence
snapshot that explains how those priorities were derived.
