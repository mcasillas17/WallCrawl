# Package 13: immutable workout history detail

## Authority and scope

Based on `origin/main` at `91a0938`, fetched on 2026-09-21. ROADMAP.md remains
the status authority. The current persistence contract is Room 14 and archive 4,
reading formats 1-4. Existing Package 4 and 9 decisions are reused, not replaced.
Design choices below are agent-selected under the task's autonomous execution
mode; they are not human exercise-metadata approval.

This package is a read-only presentation of recorded history. Substitutions
remain dependent on Package 10; analytics filters/charts and template editing
remain Packages 14 and 15. The 182 AI_ACCEPTED / zero APPROVED distinction,
joint-sensitive refusals, progression rules and deload lifecycle are unchanged.

## Navigation alternatives and decision

1. **Selected: retain the completion summary inside ActiveWorkoutScreen.** This
   preserves Finish, incomplete-work confirmation, Back and Done. Remove the
   unused `workout_summary/{sessionId}` route and builder. Add `history/{sessionId}`
   for read-only details and `history` for browsing all completed sessions.
2. Redirect completion to the declared summary destination: valid but adds
   back-stack replacement and another lifecycle owner without improving this task.
3. Reuse active workout as history: rejected because catalog loading, timer and
   logging state must not be prerequisites for reading a completed snapshot.

Progress cards open the persisted session ID. A separate history list shows 20
entries at a time with older/newer controls; it replaces a page rather than
accumulating an unbounded collection. Route arguments and saved paging position,
not serialized workouts, survive recreation. Detail Back returns to its caller;
completion Done retains its current transition to Progress. A completion summary
can link to the same read-only detail.
The browsing query returns only session ID, original name, completion time and
recorded duration, with one extra scalar row as the page sentinel. It observes
session-table changes only; exercises/sets/provenance are loaded by detail, not
by a page that displays no child measurements.

## Persisted-field inventory

| Snapshot | Available facts |
| --- | --- |
| Session | ID, original name, start and nullable completion timestamps, estimated and actual minutes, weight unit, status, origin, optional template ID, focus labels, original notes/rationale |
| Exercise | Instance ID, session ID, exercise ID, order, stored exercise type and full prescription, original notes |
| Prescription | Work-set count, rep range, nullable external load or assistance, duration/distance targets, rest seconds, nullable effort range, rest class and rest source |
| Set | ID, number, stored type, individual planned targets, nullable performed reps/load/assistance/duration/distance, classification, completion flag, optional RPE/RIR/manageable response, stop reason, completion/stop timestamps |
| Recommendation | Session ID, ordered reason tokens, validator/outcome and policy identities, context digest, profile revision, recording time, accounting week/zone and per-muscle completed/proposed/allowance counts |
| Progression provenance | Exercise ID, supported reason, optional axis, up to two source-session IDs, base-configuration digest |
| Deload provenance | Versioned accepted offer identity, source and decision revision |

No stored general override flag, substitution history, frozen historical catalog
name, full historical profile or before/after progression pair exists. A stored
USER_PREFERENCE rest source is explicit rest provenance; a performed value
differing from a target is not proof of an override. No new snapshots or migration
are needed. Do not backfill older rows from current policy, profile or catalog.

Exercise names may be optional decoration only; raw stored IDs always work.
Historical free text remains byte-for-byte as recorded, regardless of language.

## Rendering and missing data

Show session facts and shared summary metrics first, then ordered exercises with
exercise-level prescriptions, ordered per-set planned and performed measurements,
classification and outcome. A rep range is not the same fact as the per-set
target reps. Work, warm-up, drop, myo and failure classifications remain distinct.
Completed, stopped/skipped and unresolved outcomes remain distinct.

Only applicable measurement fields appear. Unknown applicable values read as
not recorded, never zero; bodyweight has no external load field, assistance has
its own label, and distance/time shapes preserve which dimensions were planned
or performed. Typed stop reasons describe a recorded choice, not a diagnosis.
Missing old feedback or outcome times are explicitly unavailable. Retain existing
activity, volume and record semantics rather than adding physiological scores.

Loading, loaded, missing/deleted, unsupported status and read error are separate
states. Error replaces stale detail and has Retry; missing is not a spinner.
No edit/delete/repeat/replan controls exist. Content scrolls and wraps at 320 dp
and 1.8 font scale; disclosures have expanded/collapsed semantics.

## Historical chronology and comparisons

An eligible prior session is a distinct COMPLETED session with a positive start,
an ordered non-null completion, and completion **strictly less than the viewed
session's start**. Equal boundary timestamps and overlapping sessions do not
establish prior evidence. Invalid or absent chronology supplies no baseline.
Among eligible observations, choose latest completion, then latest start, then
lexicographically ascending session ID and exercise order/instance ID. This
ordering is deterministic; the ID tie-break orders candidates, never creates
earlier time at the boundary.

Ordinary previous performance compares exact exercise IDs, persisted exercise
types and compatible measurement dimensions (distance-only, time-only, and both
are distinct). It uses completed, unstopped, type-valid observations, not
unresolved or stopped measurements. Show the chosen earlier performance beside
the viewed plan and performed values without invented growth percentages.
Display original prior units or explicitly convert through `convertWeight`;
never relabel numbers. Meters and seconds remain their stored dimensions.
Ordinary and cited performance use the archive's historical measurement bounds,
not today's tighter prescription or logger limits. A recorded outcome may exceed
a planned target. Both paths share the same SQL chronology, shape and eligible-set
predicates; presentation consumes those projections rather than applying a second
eligibility policy.

Personal-record counts use the same strict historical cutoff in both the pure
calculator and repository. Preserve heavier-top-load / higher-reps rules and
existing warm-up treatment; first observation is not a record. A grouped SQL
aggregate over eligible earlier data supplies maxima for the viewed exercise
IDs and compatible types/units without materializing all sessions or applying a
global recent-200/500 cap. Finish and reopened summaries share that calculation.
Future workouts cannot change an older session's apparent achievement.

Lookup previous performance in bounded batches at the exercise/session level,
not one query per set. Paging must be bounded and must not silently discard
needed older evidence; aggregation may scan relevant history in SQL but returns
bounded summaries. Oversized or malformed persisted representations fail
explicitly rather than truncating successful detail.
Comparisons load only selected exercise rows, their eligible sets and source
session headers, never unrelated prior workout graphs. Ordinary and cited
lookups share this bounded projection; citations are keyed by source-session ID
and viewed exercise-instance ID. Headers are explicitly not partial
`WorkoutSession` values. Limits are checked on the actual required projection
before its sets are materialized.

## Stored explanations and evidence limits

Use `ProgressionReasonCode`, `DeloadReasonCode` and `WorkoutRankingReasonCode`.
Validation names are interpreted only under explicitly supported validator
versions. Known malformed groups are read errors. Unknown versions/tokens retain
their identity with unsupported-detail copy; they are not current policy.

Useful localized reasons lead. Technical versions, digests, ordered raw tokens
and accounting live in a disclosure. Manual/older sessions with no recommendation
say that it was not recorded, not that automatic validation succeeded.
Accepted deload comes from this session's record, never current preferences.

Progression sources are a separately labelled evidence section, distinct from
ordinary previous performance. Only existing, eligible, type-compatible source
sessions can supply their recorded targets/results. Missing/incompatible/future
sources remain unavailable. Do not construct a fictitious ProgressionDecision
or reference prescription, and do not rerun progression to claim an old decision
was justified. A digest is an identity, not a reversible prescription.

## Read consistency and lifecycle

History observes relevant Room invalidations and reads session, recommendation,
comparisons and summary inside one transaction under the existing local-data
gate. It never bootstraps a profile, refreshes a ledger cache or consumes deload.
Collection is lifecycle-aware and replay clears while hidden. Retry resubscribes;
no polling. Deletion/restore invalidates the complete projection, not individual
independently combined pieces. App-level onboarding/reset navigation remains
authoritative and must not restore an obsolete detail back stack.

Active completion continues to call completeWorkout only on confirmed Finish.
Restoration of a completed active route reads its summary, without repeating
completion, logging or timer startup. Missing and read-error handling must also
terminate loading on that path.
While a workout remains active, one history subscription survives set updates;
logging must not remove the editor, its partial input, focus or feedback controls.
A short subscription grace preserves the logger during rotation; replay clears
after it stops. Completion deliberately reads the observed summary in addition
to the atomic completion transaction's returned summary. This is at most one
extra bounded aggregate on Finish, not polling: a returned result must not replace
newer observed history if delete/restore occurs before the completion call resumes.

## Verification and documentation

TDD covers chronological boundaries, caps, shape/null/units/outcomes, provenance
versions/corruption/source absence, no writes, Room coherence, state transitions,
navigation and recreation, deletion/restore, English/Spanish parity and narrow
large-text semantics. Use a task-owned API 36 emulator for connected tests and
actual documentation screenshots. Claims distinguish recreation/process restart
from actual process-death testing and automated semantics from TalkBack use.

Run the requested Python importer/release suites, acceptance audit, pinned-source
check, Gradle test/lint/assembleDebug and connectedDebugAndroidTest. All configured
Knights reviewers must converge, then materially affected docs and screenshots
receive a separate fresh full-panel final review. No commit/push/PR precedes
publicationReady; no merge, release, tag or deployment is authorized.
