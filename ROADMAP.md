# WallCrawl Roadmap

> **Status date:** 2026-09-02
>
> This is the single source of truth for current project status, priority, and dependency
> order. Detailed plans under `docs/superpowers/plans/` are historical execution records;
> their unchecked boxes do not override this roadmap.

WallCrawl is building from a local-first Android workout planner into a deterministic adaptive
coach, followed by optional on-device ranking and optional Health Connect/Wear OS integrations.
Core workouts must remain offline, account-free, and usable without a model or companion device.

## Current status

| Area | Status | Remaining gap |
| --- | --- | --- |
| [Android foundation](README.md#current-vertical-slice) | Shipped | Android Auto Backup still conflicts with the documented local-only promise |
| [Catalog, onboarding, templates, and logging](docs/superpowers/plans/2026-08-28-adaptive-coach-product.md) | Shipped | Validated substitutions, complete target editing, and deeper history remain |
| [Deterministic coach](docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md) | Policy foundation shipped behind a disabled gate | Human approval, program validation, progression/deload, Progress semantics, and release gates |
| [Reviewed exercise metadata](docs/reviewed-exercise-metadata-human-signoff.md) | 37 `DRAFT`, 0 `APPROVED` | Timed-hold schema, band/planner coverage, deadlift classification, and human sign-off |
| [Planner evaluation](docs/planner-evaluation.md) | Corpus and CI shipped | One persona, new policy assertions, and importer drift checking |
| [Optional local model](docs/superpowers/plans/2026-08-29-science-based-local-llm-engine.md) | Not started | Blocked on the deterministic coach release gate |
| [Health Connect and Wear OS](docs/superpowers/plans/2026-08-28-local-health-and-wear.md) | Not started; only `:app` exists | Blocked on privacy controls, validation, and substitutions |

The shipped foundation includes Room schema 11 with a continuous migration chain, the complete
302-exercise catalog, first-run onboarding, local movement preferences, custom templates,
type-aware workout logging, RPE/RIR and typed stop reasons, a rest timer, experience-aware
ranking, the `PRIMARY_ONLY_V1` weekly ledger, and state-based dose/effort/rest policy. Reviewed
eligibility and state-based guidance remain production-disabled while every reviewed record is
still draft.

## Now: make deterministic coaching production-ready

Metadata review in item 3 can proceed in parallel with engineering items 4-7. Item 8 cannot
start until approval and all engineering gates are complete.

1. **Stop the backup/privacy contradiction.** Decide the policy, disable or explicitly scope
   Android Auto Backup, add manifest resources and an instrumentation guard, and align the
   documentation.
2. **Finish local data ownership controls.** Add versioned Storage Access Framework
   export/import with checksums and future-schema rejection, plus delete-all-local-data
   controls.
3. **Make reviewed metadata approvable and obtain human sign-off.** Allow timed-hold review
   metadata, resolve the deadlift direct-primary classification, cover band-only and
   planner-reachable gaps, complete the sign-off packet, author approvals, and regenerate the
   catalog and report.
4. **Add whole-program validation.** Validate reviewed IDs, hard constraints, duplicate
   exercise/family, dose, confirmed load, duration, and weekly-ledger overflow; persist
   versions, reason codes, and results with recommendation snapshots.
5. **Resolve Progress weekly semantics.** Decide whether the card reports primary-only dose or
   broader activity, then align its week boundary, metadata behavior, label, empty state,
   tests, and architecture documentation.
6. **Implement capability evidence, progression, and deload offers.** Consume comparable
   completed outcomes without weakening hard rules, progress one variable at a time, keep
   deload user-controlled, and widen adaptation states and their complexity ceiling atomically.
7. **Complete the deterministic release gate.** Add the `concurrent-activity` persona,
   primary-only-ledger and no-invented-load assertions, remove unsupported claims from old
   plans, and run the pinned importer drift check in CI.
8. **Enable reviewed planning deliberately.** Rerun availability/persona review against the
   approved cohort, prove every supported persona retains a valid plan, then enable
   `PlannerFeatureFlags.reviewedCapabilityEligibility` in a separate reviewed change.

Detailed execution:

- [Deterministic engine plan](docs/superpowers/plans/2026-08-29-science-based-deterministic-engine.md)
- [Adaptive coach plan](docs/superpowers/plans/2026-08-28-adaptive-coach-product.md)
- [Reviewed metadata sign-off](docs/reviewed-exercise-metadata-human-signoff.md)
- [Roadmap audit evidence](docs/superpowers/plans/2026-09-02-roadmap-audit.md)

## Next: complete the adaptive coach

9. **Complete capability-aware deterministic ranking.** Add supported-regression preference,
   rank primary-muscle matches above secondary matches, and incorporate training frequency and
   recently trained muscles as bounded, explainable inputs; experience is the only profile
   signal used today.
10. **Add validated in-session substitutions.** Keep substitutions inside reviewed
    compatibility, rerun validation, explain capability-aware choices with structured reasons,
    and preserve planned versus performed values.
11. **Restore reviewed exercise guidance.** Add runtime instruction/form-cue fields and
    reviewed coaching overrides without treating upstream prose as approved programming.
12. **Add explicit multi-week program blocks.** Build periodization only after progression,
    deload, and program validation are stable.
13. **Deepen progress, history, and template workflows.** Add history drill-down, richer
    progress charts/calculations, and complete template target editing.
14. **Continue reviewed-content coverage beyond automatic planning.** Expand approved metadata
    so browsing and manual workouts gain reviewed guidance after the initial cohort unblocks
    production planning.
15. **Integrate optional bounded local inference.** Add capability/download/removal states;
    strictly parse non-safety preferences and require user confirmation; serialize only bounded
    context; rerank exact candidate IDs; validate reason-key explanations and model output; and
    provide deterministic fallback and an experiment gate. The model never owns eligibility,
    dosage, progression, validation, or persistence.
16. **Close the adaptive-coach release.** Reconcile product and architecture documentation,
    retain generated-data checks in CI, run the complete verification matrix, and release only
    after deterministic and local-model gates pass.

Detailed execution:

- [Adaptive coach plan](docs/superpowers/plans/2026-08-28-adaptive-coach-product.md)
- [Capability-aware personalization plan](docs/superpowers/plans/2026-08-29-body-aware-personalization.md)
- [Local LLM engine plan](docs/superpowers/plans/2026-08-29-science-based-local-llm-engine.md)

## Later: integrations

17. **Establish shared phone/watch modules.** Extract pure model contracts without changing
    phone behavior.
18. **Add opt-in Health Connect export and precise set timing.** Keep Room authoritative and
    permission denial non-blocking.
19. **Define the companion protocol and run the Wear technical spike.** Version snapshots,
    events, acknowledgements, and reducers, then benchmark the smallest viable watch path.
20. **Build phone coordination and the durable watch journal.** Make every event durable before
    acknowledgement and reconciliation idempotent. Start only after privacy controls,
    whole-program validation, and substitutions are complete.
21. **Build the watch workout experience.** Add watch-owned timers and ongoing activity,
    glanceable set logging, and benchmark-bounded animation.
22. **Prove disconnected execution and recovery.** Finish offline, reconnect without missing or
    duplicate sets, add resume surfaces, and expose local privacy controls.
23. **Add optional sensors and cross-device release gates.** Health Services remains optional
    and never invents health or effort data; complete phone/watch instrumentation, protocol
    compatibility, and release validation.
24. **Evaluate optional encrypted sync.** Start with a separate privacy and conflict-resolution
    design after explicit local export/import is stable. Core planning and tracking must remain
    fully functional without an account or network.

Detailed execution:

- [Local Health and Wear OS plan](docs/superpowers/plans/2026-08-28-local-health-and-wear.md)

Optional sync does not yet have an implementation plan; write and review one before starting
that item.

## Updating this roadmap

Update status from repository evidence, not unchecked historical plan steps. When work ships:

1. Update the relevant status or remove the completed roadmap item.
2. Link the commit or pull request from the detailed plan when useful.
3. Preserve completed plans as decision and execution history.
4. Add new work here only when its dependency order and release gate are understood.
