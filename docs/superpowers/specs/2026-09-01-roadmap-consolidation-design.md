# Roadmap Consolidation Design

**Goal:** Make one document authoritative for WallCrawl's current status, ordered future work,
and dependencies without deleting the detailed implementation history.

## Context

Future work is currently repeated in:

- `README.md` under **Next milestones**
- `docs/superpowers/plans/2026-08-29-body-aware-personalization.md`
- `docs/superpowers/plans/2026-08-28-adaptive-coach-product.md`
- `docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md`
- `docs/superpowers/plans/2026-08-29-science-based-local-llm-engine.md`
- `docs/superpowers/plans/2026-08-28-local-health-and-wear.md`
- `docs/superpowers/plans/2026-09-02-roadmap-audit.md`

The implementation plans contain valuable requirements and test detail, but their unchecked
boxes are not reliable status indicators. The audit already establishes that code evidence
and explicit status notes outrank historical checkbox state.

## Approaches Considered

1. **Canonical roadmap plus historical plans — selected.** Add `ROADMAP.md`, remove duplicate
   priority lists from high-visibility surfaces, and label broad plans as historical detail.
   This provides one current source without losing decisions or implementation guidance.
2. **Archive superseded plans.** Moving plans into an archive would improve directory clarity,
   but it would churn links and imply the unfinished portions are irrelevant.
3. **Delete or replace broad plans.** This would minimize documentation volume, but discard
   exact contracts, validation rules, and dependency rationale still needed for execution.

## Design

`ROADMAP.md` is the only living roadmap. It contains:

- a status date and the evidence rule used to update it;
- a compact table of shipped foundations and remaining gaps;
- an ordered critical path for the deterministic coach;
- subsequent adaptive-coach, local-model, and Health/Wear phases;
- explicit gates and parallel-work notes;
- links to detailed plans, which remain the execution specifications.

The README describes the current product and links to `ROADMAP.md`; it does not maintain a
second milestone list.

Broad roadmap-like documents receive a short banner stating that they are historical planning
records and that current status and priority live in `ROADMAP.md`. The roadmap audit retains
its dated evidence and findings but delegates the live ordered backlog to `ROADMAP.md`.

Completed implementation plans remain untouched. Their checkboxes document the original
execution recipe, not current project status.

## Validation

- Every active item identified by the audit appears exactly once in `ROADMAP.md`.
- Every roadmap phase links to its detailed source plan.
- The README and broad plans link to `ROADMAP.md`.
- Repository Markdown links resolve.
- `git diff --check` reports no whitespace errors.

## Delivery

Commit the design first, then commit the consolidation separately. Push the session branch and
open a non-draft pull request against `main`.
