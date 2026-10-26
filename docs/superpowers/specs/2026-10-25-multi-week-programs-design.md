# Package 12: explicit multi-week programs

## Status and authority

**Approved by the user in this chat on 2026-10-26.** The user first selected
completion-driven weeks, then approved this complete design. The product choices below
originated as agent proposals and are now approved for implementation. This document does not claim shipped
behavior, passing feature tests, exercise-metadata approval, or release authorization.

Base: freshly fetched `origin/main`, `f7dec8b2a` (2026-10-25, America/Los_Angeles).
The supplied `76fd4c1` baseline is its parent. The task uses the existing clean managed
worktree, initially detached; create the delivery branch at the workflow's finish stage.
Do not reuse the merged documentation branch. Delivery requires implementation, tests,
material documentation, the configured independent Knights review panel, a separate
final review, and a non-draft PR. No merge, tag, release, or deployment.

Authority is ROADMAP Package 12 and the current architecture, calendar-ledger,
progression/deload, immutable-history, validation, derived-state, research-boundary,
privacy, localization, manual-template and planner-evaluation contracts. The historical
2026-08-28 Task 8 is context only. Package 17 is retired.

Verified baseline: production uses reviewed deterministic planning; Room 14; archive 4
reads 1–4; `PROGRAM_STATE_V2` derives RETURNING, accepted HOLD, or UNCALIBRATED.
`TrainingProgramState` is derived context, not enrollment. The catalog remains 182
AI_ACCEPTED records, zero human APPROVED records, with empty joint clearances.

## Implementation record — 2026-10-26

The implementation and required local validation are complete, and independent
implementation and documented-state reviews converged. The reviewed
schema uses `program_session_associations` (the earlier proposal named it
`workout_program_associations`), with no change to the approved ownership or lifecycle.
Current formats are Room 15, archive 5 reading 1–5, and context identity v5.
The [implementation plan](../plans/2026-10-26-multi-week-programs.md) records delivery gates.

## Approved decisions

The approved lifecycle is:

- Four program weeks in two two-week blocks, with 2–6 slots per week from enrollment frequency.
- Explicit program starts only; ordinary automatic workouts and templates never advance it.
- Confirmed Finish advances once, even with incomplete/stopped sets; Cancel leaves the slot open.
- Pause/resume preserve position; restart/replacement create a new run and preserve history.
- Goals/frequency are captured; changing them requires replacement or restoring those profile values.
- All program management waits until the active workout is finished or cancelled.
- No automatic progression, deload, catch-up volume or eligibility change at week/block boundaries.

## Intent, alternatives, and boundaries

Answer: what program am I following, where am I, and what comes next? Enrollment is
optional. Ordinary Today workouts and manual templates remain available.

The user chose **completion-driven weeks** over calendar-driven weeks. Calendar weeks
would offer dated deadlines but need missed-session rollover/skip rules; they would also
risk confusing program progress with the existing dose accounting week. Completion-driven
weeks keep unfinished work available without deadlines, catch-up volume, or expiry.

Proposed implementation: one small versioned program definition using the existing
planner, plus owned lifecycle state and immutable per-session association snapshots.
A general builder or a separately generated horizon would add rule/configuration state
without a v1 need. Precomputing four weeks of prescriptions would become stale as history,
constraints, and accepted deload change. Only the next session is generated.

No automatic deload, adaptation upgrade, new training methodology, dose floor, recovery
diagnosis, guaranteed outcome, new catalog approval, substitutions, charts, template
editing, dependency change, external integration, model, account, telemetry, or sync.

## Versioned v1 shape

`MULTI_WEEK_PROGRAM_V1` owns scheduling/lifecycle. `PROGRAM_CONFIGURATION_V1` owns the
configuration representation. These are distinct from `PROGRAM_STATE_V2`,
`PRIMARY_ONLY_V1`, `WHOLE_PROGRAM_V2`, and the progression/deload policies.

- One built-in program: **Four-week practice** (Spanish: **Práctica de cuatro semanas**).
- Two blocks, each containing two program weeks; four weeks total.
- Block 1 objective: establish a repeatable workout routine.
- Block 2 objective: continue the routine and record performance.
- The program also displays the fitness goals captured at enrollment. These goals feed
  the existing prescription pipeline; objective text is not a physiological claim.
- Capture `primaryGoal`, the canonical goal set, and `daysPerWeek` from the current
  onboarded profile. Supported enrollment frequency is 2–6 sessions per program week.
  Valid restored profiles outside that range can still use existing workflows; enrollment
  returns a typed unsupported-configuration outcome with an Edit profile action.
- Total slots are `4 * sessionsPerWeek`, at most 24. No configurable duration, methodology,
  start date, slot editor, or custom objective in v1.
- Block and week numbering starts at 1 in the UI. The typed internal position uses a
  zero-based slot ordinal. Derive all other indices from it and the immutable configuration.
  Stable block/week/slot identity is the run UUID plus these indices, never a timestamp.
- The final slot yields COMPLETED with no next slot; never render a fifth week.

A program week is a group of slots, not seven days. The enrollment preview and Today
disclosure state this explicitly. Four program weeks can take more or less than four
calendar weeks. Frequency is an organization preference, not a deadline or obligation.
Finishing the next slot is never permission to exceed existing weekly allowances.

## Progress and completion rules

Only a workout explicitly started using **Start program session** is associated with a
program. Ordinary automatic starts and manual templates do not advance it, including
while a program is active or paused. They still contribute to actual history, progression
evidence and calendar-week dose under existing rules. No retroactive assignment.

Starting reserves the current slot through the persisted active session association.
It does not advance it. There is already only one active workout globally, so no separate
reservation table or dose reservation is needed. No future slot is a workout row.

An explicit confirmed Finish advances the associated slot exactly once. Preserve the
current logger's distinction between session completion and individual set completion:
Finish may close a workout with stopped, unresolved, or zero completed sets, after the
existing confirmation. This counts as a finished program session, not as successful
performance or completed dose. The confirmation explains this for program workouts.
Actual set outcomes, progression qualification, and dose credit remain unchanged.

An unfinished workout stays on its current slot until Finish or Cancel. Cancel preserves
existing active-session deletion semantics: delete the session and its association in
one transaction, release the slot, increment the program revision, and do not advance.
An accepted deload already consumed at start remains consumed even after cancellation.
Stopping an individual set alone, reading, regeneration, navigation, app restart and
restore never advance progress. There is no manual skip or early-complete action in v1.

Progress is reconstructed from at most 24 small association/session-header rows for the
current run. Completed slots must form an exact prefix starting at zero. There may be
one active association at the next slot, or none. No stored mutable completed count,
current week, block index, or next-slot counter. Duplicate/gapped/out-of-range slots,
invalid statuses, inconsistent configuration, or extra active associations fail explicitly.

## Lifecycle and concurrency contract

Persist ACTIVE, PAUSED, and COMPLETED. Needs profile update, unsupported policy, loading,
and read failure are typed read/validation states, not silently persisted lifecycle changes.
One owned enrollment row per profile contains the current/latest run. Replaced runs keep
their identity/configuration in immutable session associations; there is no old-run editor
or separate all-programs browser. The completed latest run remains visible until restart
or replacement. Pause is how a user returns to ordinary workouts indefinitely.

Every action supplies expected profile ID/revision, enrollment revision, and expected
run ID (or explicitly absent run). Revision zero represents no enrollment. The revision
is monotonic across replacement/restart, not reset with the new UUID. Increment exactly
once per committed lifecycle/start/completion/cancellation change; reject exhaustion.
Check all preconditions again inside the writing transaction.

| Action | Allowed source | Preconditions | Atomic result |
| --- | --- | --- | --- |
| Start program | No enrollment | Onboarded, supported configuration, no active workout | New UUID, ACTIVE, revision 1, zero completed slots |
| Pause | ACTIVE | Expected revisions; no active workout | PAUSED, revision +1, same config/position |
| Resume | PAUSED | Expected revisions; supported policy; captured goals/frequency match profile; no active workout | ACTIVE, revision +1, same position |
| Restart | ACTIVE, PAUSED, COMPLETED | Confirmation; supported stored config matching profile; no active workout | New UUID with same config, ACTIVE, revision +1, slot zero |
| Replace using current profile | Any existing valid enrollment | Confirmation; current profile config supported; no active workout | New UUID/current config/current supported policy, ACTIVE, revision +1, slot zero |
| Start program session | ACTIVE | Fresh validated proposal; no other active session; current next slot and config match | Session/exercises/sets/recommendation/association and revision +1; deload consumption if accepted |
| Finish associated active session | ACTIVE with this reservation | IN_PROGRESS session and valid matching reservation | Session COMPLETED, revision +1; derive next slot; mark program COMPLETED on last slot |
| Cancel associated active session | ACTIVE with this reservation | IN_PROGRESS session and matching reservation | Delete session/association, revision +1; same next slot |
| Read/regenerate/restore/reopen | Any | Valid input representation | No lifecycle transition |

All other action/state combinations return typed invalid-action or stale-state failures.
Repeated pause/resume/restart taps using an old revision cannot apply twice. Completion
retry for an already-completed session returns the persisted summary without another
revision/progress write; absent or cancelled sessions return a typed missing/not-active
outcome. The association, not the currently displayed program, identifies a completion.
Duplicate cancellation returns missing/not-active without modifying program state.

All program management actions are refused while **any** workout is active, including
ordinary/manual workouts. Show Resume workout; do not mutate its prescription, association,
or deload. A race where a workout starts before an action commits returns ActiveWorkout.
Profile editing remains allowed under existing rules; frozen workouts finish normally.

Restart/replacement retains every old workout and its old run/position/policy facts. No
archive, history, ledger, progression evidence, or deload choice is reset or rearmed.
Freshly restarting the program can still meet a full calendar allowance and be refused.

## Profile changes, time, and upgrades

Captured goals/primary goal/frequency define the run. Changing them does not rewrite it:
the management screen shows the mismatch, and program generation/resume/start refuses
with **Replace using current profile** or **Edit profile**. Restoring the original values
makes the same run usable at the same position. Pause and replacement remain available.
Ordinary automatic workouts use current profile values even during this mismatch.
Restart uses the original config; replacement adopts current values. Both require explicit
confirmation, showing the retained history and new slot-zero position.

Equipment, exclusions, constraints, capabilities, muscle priorities, experience, reported
return, confirmed loads, desired duration, explicit rest choices and units are live inputs
to the existing planner. Their changes invalidate recommendations and can make the next
program session infeasible. Never retain old permissive values. Units convert through
existing helpers; they never relabel an existing recommendation or frozen workout.
Language/theme/gender stay presentation-only. Profile revision checking remains in force.

No elapsed time advances, expires, pauses, resumes or completes a program. Missed days,
long absences, DST, time-zone changes and clock rollback leave position unchanged. The
existing planner still recomputes its date-sensitive history and calendar ledger. A
long absence does not set RETURNING; the user's reported break remains authoritative.
Lifecycle timestamps are diagnostic; revision and slot order define chronology. Do not
reject a valid lifecycle transition because its timestamp precedes an earlier action.
Existing historical chronological-comparison exclusions remain unchanged.

Known V1 programs retain V1 lifecycle/configuration through app upgrades. The prescription
engine may use the app's current supported planning policies, which are explicitly
fingerprinted and recorded at each start; this does not rewrite old snapshots or promise
to pin an obsolete engine forever. Any future incompatible lifecycle needs a separately
specified migration, never silent reinterpretation.

Unknown lifecycle/configuration policy versions surface Unsupported program and cannot
generate/resume/restart. A structurally valid opaque enrollment can be explicitly replaced
with V1 if there is no active workout; its opaque identity must remain visible. Known
malformed data is an error with Retry, not absence or a default program. Unknown versions
in immutable history remain opaque with exact recorded identifiers. Export and restore
reject unknown program versions in **either** enrollment or associations because this
build cannot validate their coherence; export refuses before opening its destination.
Preserve the data in Room, explain that a supporting app version is required, and never
partially import or silently omit it. This is not a forward archive-compatibility promise.

An active known-V1 program workout can Finish or Cancel despite current profile mismatch
or planner failure; neither action replans it. An active workout with an unsupported
program policy remains resumable/readable and can retain logged sets, but Finish returns
Unsupported program without changing session or program progress. The UI explains the
need for a supporting version and offers the existing explicit Cancel workout action.
Cancellation needs only the structurally valid run/session identity and revision: delete
the active session and association and increment revision, without interpreting or
advancing the unknown schedule. No deload is rearmed. A malformed identity or failed
database read still refuses the transaction. No unavailable transition is guessed.

## Existing planner integration and invariants

Add an optional typed program planning input to `WorkoutGenerationContext`, distinct from
`trainingProgramState`. It contains the validated run/configuration/revision/current
position and block objective. A separate explicit ordinary/program planning intent makes
association a deliberate choice; a manual template never enters program compilation.

For a program proposal, use the captured goals/frequency after checking they match the
current profile. Replace only the planner's split-rotation **base** (currently lifetime
completed-workout count) with the program slot ordinal. Keep the explicit regeneration
variation as the other input. Ordinary/manual behavior retains its existing base. Reset
the UI variation to zero on a new slot/run; passive refresh keeps it. Save it through
configuration/process state restoration for the same run/slot. Regeneration changes a
proposal, never a slot. The next-session card identifies the next slot; it does not promise
that future exercises or a split will stay fixed under changed legal inputs.

Reuse the normal builder, eligibility, ranking, prescription factory, progression/deload
and `ProgramValidator`. Only the next session is proposed and prospectively accounted.
Program structure validation checks version/config bounds, exact prefix, legal lifecycle,
slot ownership, objective/position agreement and intent. Single-session validation remains
one shared implementation, including its one permitted set-reduction repair before display
and no repair at start. No uniqueness rule applies across program slots/weeks.

Position never changes adaptation state or establishes calibration. RETURNING, accepted
HOLD and UNCALIBRATED remain the only derived states. No BUILD/DEVELOP, advanced-ceiling
exception, load step, set increase, new movement requirement, or block-boundary deload.

Program weeks and ISO calendar weeks are separate. `PRIMARY_ONLY_V1` continues to read
completed session timestamps in the explicit current device zone. Starting/pausing/
restarting/replacing a run never clears or relabels ledger history. Program slots reserve
neither future dose nor separate allowances. Every proposal shares the whole-session
aggregation already in `ProgramValidator`; future slot cards contain no prescriptions.

If planning fails, retain program position and show the existing typed failure with Retry,
Edit profile, and access to ordinary/manual workflows as applicable. Do not automatically
switch the button's meaning to ordinary, broaden eligibility or hide the program failure.
Resume active workout remains independently available even if program reads fail.

## Freshness and atomic persistence

Bump recommendation context identity to v5. In addition to all existing consumed inputs,
include explicit ordinary/program intent, enrollment revision/run/status (including absence),
configuration and lifecycle policy versions, canonical consumed configuration, and current
position/objective when applicable. Ordinary recommendations also observe lifecycle changes
so a displayed pre-enrollment proposal cannot silently acquire membership.

Publish recommendation/unit/program projection/snapshot together. Observe relevant Room
invalidations while RESUMED and refresh on resume/retry; no timer polling. The bounded
program projection is read coherently under the local-data gate in a Room transaction.
Before start, rebuild context, compare identity and run normal validation without repair.

Ordinary automatic planning reads only an enrollment identity header: profile/run ID,
revision and a canonical digest of the stored enrollment fields. It does not interpret
program configuration, reconstruct slots or require a supported program policy. Unknown
policy or malformed program configuration therefore does not block ordinary workouts;
the program card separately displays its error. Absence is an explicit header state.
Unparseable identity/revision or database failure is still an error, never treated as
absence. Manual templates and active-workout resume do not require this header.
Program mode additionally consumes the full validated projection above. Changes to any
stored enrollment fields invalidate either mode through the header digest.

Inside `WorkoutSessionDao.insertWorkoutUnlessActive`:

1. Keep the current profile-revision guard and existing-active-session return path.
   If that path returns a session, write nothing: no association, reservation, revision or
   deload consumption. The caller resumes the returned session and its actual provenance.
2. For a new automatic start, compare expected enrollment absence or identity header,
   and the deload revision. Program mode also checks supported configuration/policy,
   ACTIVE status and next-slot identity. Ordinary mode does not interpret program policy
   and must explicitly carry no program association.
3. Insert session/exercises/sets, normal recommendation and immutable program association
   when applicable. Increment the enrollment revision and consume accepted deload together.
   Any failed check/write rolls the entire transaction back.

Completion extends the existing `completeWorkoutAndReadSummary` transaction. Guard the
IN_PROGRESS→COMPLETED change, validate association/current run, advance revision/status and
read the summary atomically. Any failed summary/progress write rolls completion back.
Concurrent Finish, cancel, program actions, restore and deletion serialize through Room;
program management additionally shares the existing local-data gate. Workout writes keep
their existing transaction guards rather than acquiring the gate across UI work.

## Storage, history and archive

Additive Room **14→15** (implementation baseline rechecked against main):

1. `program_enrollments`: profile key, unique current run UUID, immutable configuration,
   lifecycle/config versions, status, monotonic revision and diagnostic timestamps. Keep
   it separate from profile replacement so saving a profile cannot delete enrollment.
2. `program_session_associations`: session primary key/FK with cascade deletion; run UUID,
   recorded pre-start revision, configuration/policies, position and objective snapshot.
   Index `(programId, slotOrdinal)` uniquely and index the run lookup. No FK to the mutable
   enrollment row: restart/replacement must not redirect or delete historical links.

Add one nullable `programAssociationDigest` column to `workout_recommendation_records`,
with null for every existing row. A program start sets it to SHA-256 of the canonical
complete association (including session ID, run, start revision, configuration, versions,
position and objective). `RecommendationSnapshot` carries the typed association input;
`asRecord` binds it to the newly allocated session ID. Normal record and association
are written in the same transaction. Ordinary automatic records keep null. This is
integrity/cross-field binding, not authentication or a recoverable full-profile snapshot.

The repeated small configuration in associations is intentional frozen provenance so past
run identity survives replacement without another mutable run registry. Store no exercise
or performed-value duplicates; those already belong to the session. Program observations
are joined with bounded headers, never full workout graphs. At most 24 slots plus one
overflow sentinel are read for a run. A maximum of 20,000 associations shares the existing
archive session ceiling; the archive's overall 16-million-character bound still applies.
Use existing identifier/text bounds, strict enums, safe numeric arithmetic, finite
representation limits, and canonical goal ordering. Never truncate successful data.

Migrate old installations to empty new tables. No automatic enrollment or historical
backfill. Immutable history detail reads the association in its existing coherent
transaction and shows recorded program identity, block/week/session, captured objective
and policy facts. It never joins to today's mutable enrollment to explain a past workout.
Old/manual/unassociated sessions have no program label, legitimately. Program management
links its completed slots to the existing history route, using bounded header projections.
Unknown historical versions display opaque facts; known corrupt snapshots show an error.

Archive **5** writes enrollment, session associations and the nullable recommendation
association digest; reads 1–5. Formats 1–4 retain
their exact canonical serialization/checksums and restore with no invented enrollment or
association. A format-1–4 document containing new fields is rejected. Format-5 export and
restore run the same bounded validation; export completes validation before document open.

Every association must reference one existing archived automatic session and its normal
recommendation, whose non-null digest must equal the canonical association digest. Every
non-null recommendation association digest must have exactly one association. Thus losing
an association is a validation error, not an ordinary workout; adding an association to an
ordinary record is also rejected. This agreement check does not prove file authenticity.
One session has at most one
association; one run/slot has at most one retained session. All associations for the same
run must agree on immutable configuration and use ordered positions. An association to an
older replaced run does not require the current enrollment to name that run. Retained older
runs must have a completed prefix and cannot have an active session. The current run must
satisfy its lifecycle/prefix/reservation invariants, including a full prefix for COMPLETED
and fewer than all slots for ACTIVE/PAUSED. PAUSED cannot have a program active session.

Missing association parents are corruption and fail restore. Cancellation deletes both
active session and association, leaving no dangling reservation. Existing deload audit
links may still reference a deleted cancelled session under their existing contract.
There is no completed-workout deletion UI; do not invent missing completed memberships or
reconstruct them from today's profile. Deletion of all local data removes enrollment and
all associations, so the next read cannot recreate either. Program writes require the
expected existing onboarded profile, share the local-data gate, and never bootstrap it.

Restore considers either new table nonempty, inserts all rows transactionally in dependency
order, validates the relationship, and clears only derived caches. It does not complete
or reserve a slot afresh. Do not hold the local-data gate across document I/O. Database
errors, policy incompatibility and corrupt representations remain distinct typed failures.

## User experience and localization

Add a focused Program management destination from Today and Progress. No new primary tab.
The empty state describes the four-week/two-block shape, captured goals/frequency,
completion-driven meaning and optional participation, with Start program. It is not a
wizard or a training-method selector.

Today keeps Resume active workout first. For an enrollment, show program name/status,
block/week/session, block objective, progress as finished slots and next slot. ACTIVE
supports an explicit program/ordinary recommendation choice with distinct Start labels;
PAUSED and COMPLETED default to ordinary. Never reinterpret an existing displayed plan.
Program failure leaves management, ordinary choice and template entry accessible. Program
state errors do not hide active workout resume. The existing deload card applies to
automatic starts in either mode, and manual templates still do not consume it.

Management exposes Pause/Resume, Restart, and Replace using current profile as allowed by
the transition table, plus completed-slot links. Restart/replacement confirmation says
position starts over, history stays, and accepted/consumed deload is unchanged. COMPLETED
says the chosen sequence is finished, with no guaranteed-outcome language. No automatic
reenrollment. Progress displays the same compact program summary separately from its
calendar-week activity/dose metrics and links to management/history.

Use paired English/neutral Latin American Spanish resources, existing domain labels,
plurals, units, formatting and accessible semantics. Structured program labels translate;
historical free text remains as recorded. Run/position IDs and decisions ignore locale.
Stack/wrap controls in scrollable layouts at 320 dp and font scale 1.8; no ellipsis hiding
required meaning, forced single-line labels, or suppressed font scaling. Persist only
navigation/selection/variation state needed for restoration, and recheck revisions after
recreation. Pending confirmations cannot apply a stale action to a replacement program.

## Implementation surfaces and validation contract

Domain/policy: new focused program model/transition/validation types; generation context,
builder, planner rotation seam, recommendation snapshot/identity and single-session
validator integration. Persistence: entities, dedicated DAO/repository, session start/
finish/cancel DAO, mapping, database migration registration, backup snapshot/codec/DAO,
history projection and application wiring. UI: Today, Progress, program destination,
history detail, active Finish confirmation, navigation and both string resources.

TDD extends current tests and adds focused coverage:

- Every transition/state pair, stale revision/run/profile, repeated actions, overflow,
  read-only reconstruction, prefix corruption, restart/replacement and unsupported config.
- Equal-input multi-week replay through real production composition and bundled accepted
  catalog; include supported full equipment and typed unsupported personas. Normalize
  generated UUID only. Every proposed session goes through the existing validator.
- Missed sessions, long absence, pause/resume, week/block/final boundaries, DST, zones,
  clock rollback; calendar dose unchanged by actions and counted once from actual work.
- Progression/deload inside/outside enrollment, no complexity bypass, ordinary/manual
  non-membership, profile change after recommendation and during frozen active workout.
- Real Room start/reservation/completion/cancellation, existing-active return, duplicate
  Finish, failure rollback, process restart, deletion/write races, coherent invalidation.
- Migration 14→15 and every supported chain; format-5 round trip, all old readers/checksums,
  strict malformed/unknown fields and missing parents, active/restored programs and no
  invented enrollment, nonempty-destination refusal and delete-all completeness.
- Navigation, process/state restoration, missing/error/retry, English/Spanish parity,
  320 dp/1.8 text, reachable controls and preserved active-workout resume during failures.

Final gates (record exact results/device details, not merely intended commands):

```sh
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
python3 -m unittest discover -s tools/release -p 'test_*.py' -v
python3 tools/workout-guide/verify_ai_acceptance_audit.py
python3 tools/workout-guide/check_pinned_catalog.py
./gradlew test lint assembleDebug --stacktrace --no-daemon
./gradlew connectedDebugAndroidTest --stacktrace --no-daemon
git diff --check
```

Use documented JDK 17 and Android SDK 37 with a task-owned API 36 emulator; record actual
AVD, serial, API, ABI and system-image identity. No destructive use of another task's device.
Physical-device behavior and manual TalkBack certification are not implied by emulator tests.

Update ROADMAP Package 12 and affected README, architecture, weekly-dose-ledger, privacy,
localization, custom-workout, planner-evaluation and history-contract documentation.
Clarify roadmap reproducibility as recorded program facts plus equal-input replay; a hash
cannot recover historical profile/catalog inputs never stored. Add the required focused
implementation plan only after design approval. Keep logs and review records outside Git.

The builder may choose file splits, DAO helper placement, resource names, screen layout
details and test organization. Changing lifecycle, program size, counting semantics,
configuration capture or historical/restore guarantees requires an explicit design update.

## Design review record

A fresh read-only builder check identified three missing boundaries: recommendation-to-
association binding, ordinary-mode independence from program semantics, and unsupported
program export/active-workout handling. The proposed resolutions above add the nullable
association digest, an ordinary-mode identity-header read, and explicit unsupported
export/restore/Finish behavior with cancellation available. These are agent proposals
approved with the complete document on 2026-10-26. This is product-design approval,
not human exercise-metadata approval.
