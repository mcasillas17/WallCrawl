# Roadmap Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish one canonical WallCrawl roadmap while preserving detailed implementation
plans as historical execution records.

**Architecture:** A root `ROADMAP.md` owns current status, priority, and dependency order.
The README and broad historical plans link to it rather than maintaining competing live
backlogs; detailed plans continue to own implementation requirements.

**Tech Stack:** GitHub-flavored Markdown, repository-relative links, Git.

---

### Task 1: Create the Canonical Roadmap

**Files:**
- Create: `ROADMAP.md`
- Reference: `docs/superpowers/plans/2026-09-02-roadmap-audit.md`
- Reference: `docs/superpowers/plans/2026-08-28-adaptive-coach-product.md`
- Reference: `docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md`
- Reference: `docs/superpowers/plans/2026-08-29-science-based-local-llm-engine.md`
- Reference: `docs/superpowers/plans/2026-08-28-local-health-and-wear.md`

- [ ] **Step 1: Add the source-of-truth contract**

Start `ROADMAP.md` with:

```markdown
# WallCrawl Roadmap

> **Status date:** 2026-09-02
>
> This is the single source of truth for current project status, priority, and dependency
> order. Detailed plans under `docs/superpowers/plans/` are historical execution records;
> their unchecked boxes do not override this roadmap.
```

- [ ] **Step 2: Add current status**

Record the shipped local Android foundation, the production-disabled deterministic policy,
37 draft and zero approved reviewed records, and the absence of local-model, Health Connect,
and Wear modules. Link each row to its detailed source plan.

- [ ] **Step 3: Add the dependency-ordered backlog**

Carry forward all 23 ordered work packages from the audited backlog, grouped into:

1. **Now — make deterministic coaching production-ready** (items 1-8)
2. **Next — complete the adaptive coach** (items 9-16)
3. **Later — Health Connect and Wear OS** (items 17-23)

State that metadata review may proceed in parallel with deterministic engineering, but
production enablement waits for approval, validation, Progress semantics, progression/deload,
and release gates.

- [ ] **Step 4: Check roadmap structure**

Run:

```bash
grep -nE '^#|^[0-9]+\.' ROADMAP.md
```

Expected: one document title, three phase headings, and 23 numbered work packages.

### Task 2: Remove the README Backlog Duplicate

**Files:**
- Modify: `README.md:418-443`

- [ ] **Step 1: Replace the milestone list**

Replace `## Next milestones` and its bullet list with:

```markdown
## Roadmap

Current status, dependency order, and upcoming work live in the
[WallCrawl roadmap](ROADMAP.md). Detailed design and implementation records remain under
[`docs/superpowers/`](docs/superpowers/) for execution context.
```

- [ ] **Step 2: Verify the README has one roadmap pointer**

Run:

```bash
grep -nE '^## (Next milestones|Roadmap)|ROADMAP.md' README.md
```

Expected: one `## Roadmap` heading and one `ROADMAP.md` link; no `Next milestones` heading.

### Task 3: Mark Broad Plans as Historical

**Files:**
- Modify: `docs/superpowers/plans/2026-08-29-body-aware-personalization.md`
- Modify: `docs/superpowers/plans/2026-08-28-adaptive-coach-product.md`
- Modify: `docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md`
- Modify: `docs/superpowers/plans/2026-08-29-science-based-local-llm-engine.md`
- Modify: `docs/superpowers/plans/2026-08-28-local-health-and-wear.md`
- Modify: `docs/superpowers/plans/2026-09-02-roadmap-audit.md`

- [ ] **Step 1: Add the historical-plan banner**

Immediately below each title except the dated audit, add:

```markdown
> **Planning record:** Current status, priority, and dependency order live in the
> [canonical roadmap](../../../ROADMAP.md). This document preserves detailed implementation
> context; unchecked boxes are not authoritative project status.
```

Use `../../../ROADMAP.md` for the audit as well; all six documents share the
`docs/superpowers/plans/` directory, three levels below the repository root.

- [ ] **Step 2: Convert the audit's live backlog into a pointer**

Keep the audit method, evidence, and gap findings. Replace `## Current roadmap status` and
`## Complete recommended order` with:

```markdown
## Consolidated roadmap

The audited status and dependency order are now maintained in the
[canonical WallCrawl roadmap](../../../ROADMAP.md). This file remains the dated evidence
snapshot that explains how those priorities were derived.
```

- [ ] **Step 3: Verify every broad plan points to the canonical roadmap**

Run:

```bash
for file in \
  docs/superpowers/plans/2026-08-29-body-aware-personalization.md \
  docs/superpowers/plans/2026-08-28-adaptive-coach-product.md \
  docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md \
  docs/superpowers/plans/2026-08-29-science-based-local-llm-engine.md \
  docs/superpowers/plans/2026-08-28-local-health-and-wear.md \
  docs/superpowers/plans/2026-09-02-roadmap-audit.md; do
  grep -q '../../../ROADMAP.md' "$file" || exit 1
done
```

Expected: exit 0.

### Task 4: Validate and Deliver

**Files:**
- Test: all modified Markdown files

- [ ] **Step 1: Validate local Markdown links**

Run a Python standard-library script that scans each changed Markdown file for relative links,
ignores URL/anchor/mail links, resolves each path from the source file's directory, and exits
nonzero if a target does not exist.

Expected: `Markdown links: PASS`.

- [ ] **Step 2: Validate scope and formatting**

Run:

```bash
git diff --check
git --no-pager diff --stat main...HEAD
git status --short
```

Expected: no whitespace errors; only roadmap documentation is changed.

- [ ] **Step 3: Commit the consolidation**

```bash
git add ROADMAP.md README.md docs/superpowers/plans
git commit -m "docs: consolidate project roadmaps"
```

- [ ] **Step 4: Review, push, and open the pull request**

Run the repository review workflow, address any high-confidence findings, push the branch, and
open a non-draft pull request titled `docs: consolidate project roadmaps` with a body that
summarizes the canonical roadmap, historical-plan links, and validation evidence.
