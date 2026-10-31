# Package 9: progression and user-controlled deload

## Decision status and authority

This is the focused Package 9 contract, based on `origin/main` at
`df4c3765a3e846ca6b29ccc1246cdbdcd8af370e`, fetched on 2026-09-19. Implementation
status remains in ROADMAP.md. This document is a design, not completion evidence.
The user delegated consequential decisions to the agent when unavailable during
the design workflow. No human exercise-metadata approval is implied.

The current production path is reviewed planning against 182 `AI_ACCEPTED`
records, not the disabled rollout described in older design records. The catalog
has 302 exercises and zero human `APPROVED` records. All joint clearances remain
empty. Package 4 is complete; Package 3 is not a dependency of this implementation.
Room is schema 13 and archives are format 3 before this change.

## Alternatives and selected scope

1. **Selected: one automatic workout with one fewer work set per exercise.**
   This has a concrete preview, a finite lifetime, and no calendar or recovery
   inference. Sets never fall below one. Other targets are held at the current
   reference prescription and progression is paused. If the ordinary preview
   proposed an increase, accepting explicitly pauses that pending increase;
   the preview must show the held targets rather than claim that every value
   on the ordinary recommendation stays unchanged. A two-set exercise becoming one is not a percentage
   rule; a three-set exercise becomes two and a one-set exercise remains one.
2. A chosen number of automatic workouts would require a larger lifecycle and
   counter. It is unnecessary for the requested first user-controlled experience.
3. Pausing progression alone would not offer a reduced-work option when ordinary
   planning already holds. It is not the selected meaning of deload.

Today offers explicit request, accept, decline, dismiss, and pre-start cancel.
Automatic suggestions come only from an explicitly reported return after a break.
There is no multi-session fatigue pattern in this version. No diagnostic,
percentage, periodic-deload, or mandatory-recovery claim is introduced.

## Comparable outcomes: `ONE_VARIABLE_PROGRESSION_V1`

This policy evaluates exact catalog exercise IDs, never muscle names, regression
edges, or progression families. Capability confirmation remains a separate policy:
its evidence cannot authorize progression.

The builder supplies the latest eight session snapshots, ordered by start time
and stable ID, including incomplete/cancelled sessions so a more recent
unsuccessful attempt cannot be silently skipped. A reconstruction is bounded by
the existing archive per-session representation limits. Overflow is an explicit
failure, never a truncated successful evidence set.

For an exercise, the two most recent distinct attempts must:

- belong to distinct, completed sessions with positive, ordered session and set
  timestamps, no future completion, and valid parent/child identities;
- contain exactly one instance of that exercise and no duplicate session,
  exercise-instance, set ID, or set-number observations;
- contain the prescribed count of normal, non-warm-up work sets; every such set
  must be completed, unstopped, type-correct, and carry the prescribed targets;
- have the same entire normalized prescription, including work-set count, rep
  range, load versus assistance, duration/distance shape, effort and rest;
- meet the prescribed rep maximum or applicable duration/distance target in
  every work set, with actual external load or assistance equal to its target;
- have explicit `feltManageable == true` on every work set and at least one
  explicit effort measure: RIR >= 2 or RPE <= 8. If both are logged, both must
  qualify. Missing effort, missing manageable feedback, false feedback, failure
  sets, stopped or partial work do not qualify.

The last condition is a fallible-self-report product rule, not a physiological
threshold or an inference between RPE and RIR. Warm-ups are ignored for the
work-set comparison and never establish evidence alone. Unrelated exercises
cannot supply measurements for an exact-ID comparison. An incomplete/cancelled
attempt at the same exercise breaks the comparable pair. A completed session
with unresolved or stopped work is not progression evidence.

Weights are compared through `convertWeight`; assistance has its own field and
is never an external load. Exact physical comparisons use a named floating-point
tolerance. The existing logger renders and parses two decimal places, so a target
also matches its two-decimal entry representation: a converted 93.696... lb
target must accept the 93.70 lb the actual logger supplies. Mixed-unit comparisons
allow that representation after conversion in either direction. Different
displayed values in the same unit do not match. This is a software representation
rule, not a physiological tolerance; `MeasurementPrecision` shares the digit
count with `LocaleFormatting`. Decimal distance follows the same entry-precision
rule in meters. RED regressions reproduced the real formatter/parser path,
including binary rounding ties, before the comparisons were changed.
The two historical prescriptions must also match each other in their original
units, not only the current reference. This prevents a unit preference change
from collapsing distinct pound targets into the same displayed kilogram value
and manufacturing progression evidence.
Distance is stored and compared in meters, time in seconds. Duplicate or
conflicting observations produce a hold reason, never extra evidence.

Changed prescriptions start a new comparison pair. One observation, old
untyped feedback, unmatched types, absent targets, or missing values produce
typed holds. Out-of-scope exercise shapes stay outside automatic eligibility.

### Exactly one axis

| Shape | Domain-engine change (not automatic eligibility) | Fields that do not change |
| --- | --- | --- |
| External load + reps | +2.5 kg, converted to the selected unit | reps, sets, rest, effort |
| Bodyweight reps | both ends of the rep range +1; the range is one axis | sets, rest, effort; load stays absent |
| Assistance + reps | assistance -2.5 kg, only when the full step remains nonnegative | external load absent; reps, sets, rest, effort |
| Timed duration | duration +5 seconds | sets, rest, effort |
| Distance only | distance +25 meters; domain-tested, not automatically planned | duration absent; all other fields |
| Duration-only distance/time | duration +5 seconds; domain-tested, not automatically planned | distance absent; all other fields |
| Distance and duration | distance +25 meters; domain-tested, not automatically planned | duration and all other fields |

These increments are versioned product choices, not universally optimal amounts
or claims that equipment supports a particular increment. Existing prescription
and logging bounds apply. A full step that exceeds a bound returns
`HOLD_AT_BOUND`; no clamping, wraparound, partial step, or second-axis reset.
Unknown loads and assistance remain unknown.
Recorded or confirmed values that cannot be represented as targets after
conversion remain in history but produce a null target and `HOLD_AT_BOUND`;
they are never clamped or replaced with a lower fallback. An accepted deload
still holds unknown values and applies only its chosen set reduction.

The result is typed: policy version, exact exercise ID, changed axis or explicit
hold reason, reference prescription, resulting prescription, and source session
IDs. Equal inputs must give equal results.

### Target continuity and pipeline precedence

The current factory still owns goal-specific base prescriptions. The reviewed
path stops using its old +5 lb / +2.5 kg bump; the unchanged manual/disabled path
keeps that legacy default explicitly isolated.

Latest valid completed, non-warm-up external-load/assistance work can seed a
target, preserving the distinction between the two. This reads the existing
eight-completed-session view and excludes future work, stopped sets, wrong
measurement types and warm-up-only values. It can preserve genuinely recorded
older loads without inventing missing feedback; the stricter eight-attempt
comparison independently decides whether a step is allowed. Confirmed starting
loads remain the fallback for external load only. Neither a manageable answer nor
metadata acceptance supplies a starting number.

For rep/duration continuity, new recommendation provenance stores a per-exercise
base-configuration digest. A latest automatic target can be carried forward only
when that digest matches the current ordinary base configuration. This digest
includes goals, reported return, ordinary rep/duration/distance shape, catalog,
review and policy identities, not display language, theme, gender, or load-unit
labels. Transient set caps and rest/effort guidance are deliberately excluded
from this continuity key: changing them suppresses advancement through the full
prescription comparison, but must not also erase an earned rep/duration target.
An older session with no
digest can qualify only when its prescription exactly matches the ordinary base.
This avoids both resetting each earned step to a hard-coded default and retaining
an old goal's target after a relevant configuration change.

Order:

1. Hard eligibility and existing Package 8 ranking remain authoritative.
2. Build ordinary goals/return guidance without the legacy increment.
3. Apply state, relevant-capability, user-rest, and weekly-ledger guidance.
4. Carry a compatible historical target, then evaluate progression.
5. Accepted deload wins over progression and removes one set, minimum one.
6. The existing single whole-program repair may further reduce sets. If it
   changes a progressed exercise, revert that exercise's progression axis to its
   reference value and record `HOLD_VALIDATION_REPAIR`. Recompute duration.

Returning guidance or an otherwise changed conservative prescription suppresses
progression. Compared with the held reference, accepting a deload alters no load, assistance,
reps, duration, effort, or rest. A pending progression suggestion is not applied.
Equipment, exclusions, and candidate membership never change.

The validator reuses the same prescription decision calculation for exact
load/assistance provenance and progression conformance. It does not add arbitrary
numbers to an allowlist or accept assistance from external-load history. Repair
is still one pass; start-time repair remains disabled.

## Deload lifecycle: `DELOAD_ONE_WORKOUT_V1`

A typed offer has an ID, policy version and source (`EXPLICIT_REQUEST` or
`RETURNING`). Today shows its reason and exact effect above the recommendation,
with normal buttons, resource-backed names, live error feedback and wrapping
text. When a recommendation exists, the offer shows its exact proposed held
targets with one fewer set (minimum one), including the cancellation of any
pending progression suggestion. Controls are unavailable while a workout is active; the active-session
resume path remains available even when recommendation generation fails.

| Event | Durable state / visible effect |
| --- | --- |
| Returning profile, no handled matching return key | Derived offer; no database write and no prescription change |
| Explicit request | Persist a new `OFFERED` choice; may supersede an unanswered offer, never an accepted choice |
| Accept current offer | Persist `ACCEPTED`; invalidate/regenerate the next automatic recommendation |
| Decline | Persist `DECLINED`; keep ordinary planning and suppress this return key |
| Dismiss | Persist `DISMISSED`; same training effect as decline, distinct decision |
| Cancel before start | Persist `CANCELLED`; restore ordinary planning and suppress this return key |
| Start accepted recommendation | Atomically persist `CONSUMED` with the new session ID alongside session and recommendation record |
| Finish or cancel started workout | The frozen session follows existing behavior; consumed choice never rearms |
| Navigate, process death, restart | Reconstruct offer/choice from the persisted decision |
| Profile edit | Preserve accepted choice, regenerate against new legal inputs; an unanswered returning offer follows the new reported break |
| Export / empty-destination restore | Preserve the exact owned choice, revision and suppression key |
| Delete all | Remove choices together with all other user-owned data |

The return key is version + profile ID + explicitly reported break weeks.
Derivation and key validation accept the complete persisted/archive range,
0..5,200 weeks, without changing restored values. Interactive profile editing
retains its separate 0..520-week bound; the narrower editing rule cannot make a
valid older archive crash Today.
The last handled return key remains suppressed while that same key is current.
A different reported break is new evidence; revisiting an older key after handling
a different one may offer again. An explicit request is available when no
workout or accepted choice is active and creates a new ID. While accepted,
the request button is absent and the repository rejects a request until explicit
cancellation; it does not diagnose a new condition.
There is no wall-clock expiry or periodic reoffer.

Requesting again may supersede an unanswered offer. Displaying, declining
or dismissing never accepts. An accepted one-workout choice persists until
cancelled or atomically consumed by an automatic start; templates never consume it.
Existing returning-user guidance remains in force independently of this choice.
Cancelled active workouts follow existing deletion semantics; a consumed
decision's session ID is audit linkage, not a requirement to retain that workout.

## Adaptation states and advanced-complexity ceiling

Only these states are derived in this version:

| Precedence | Entry | Exit | State |
| --- | --- | --- | --- |
| 1 | profile explicitly reports break weeks > 0 | break cleared | `RETURNING` |
| 2 | no reported break; accepted, unconsumed deload choice | cancel or consume | `HOLD` |
| 3 | otherwise | return report or accepted deload | `UNCALIBRATED` |

An offer is a separate value, never `DELOAD_OFFERED`. Acceptance is separately
available to prescription compilation, so returning users can accept without
losing their returning state. `BUILD`, `DEVELOP`, `INITIATE`, `RECALIBRATE`,
`NEEDS_ONBOARDING`, and `DELOAD_OFFERED` are not derived for completeness.

No global calibration condition is established here. The advanced ceiling is
explicitly conservative for every state unless the existing demonstrated-family
or legal supported-regression exception applies. There is no unmatched enum
branch that lifts it. Capability evidence retains its existing narrower meaning.

`PROGRAM_STATE_V2` and `STATE_BASED_DOSE_EFFORT_REST_V2` record this derivation.
The derivable states use the existing conservative 6-set weekly allowance,
two-set per-exercise cap, and 2..4 RIR guidance before optional accepted deload.
`HOLD` must not activate the old 8-set allowance as a side effect of acceptance.
Historical version names and stored snapshots are not rewritten.

## Persistence, freshness, and failures

Persist user decisions, not reconstructed evidence or displayed returning offers.
Use one additive `deload_preferences` row per profile: monotonic revision, latest
typed choice, and last handled return key. Keep this outside the profile row so
whole-profile saves cannot overwrite a newer acceptance. Writes use the existing
local-data gate, transactionally check the expected profile/choice revisions,
and cannot recreate state after deletion. Do not hold the gate over document I/O.

Room migration 13 -> 14 creates only the new table; old rows remain untouched.
Archive format 4 includes the owned preferences; formats 1..3 retain their exact
read/checksum behavior and supply no fabricated choice. Strict bounds, enum,
version, identifier and cross-field checks run on export and restore. A nonempty
choice table makes a destination nonempty. Delete and restore include it in the
existing transaction.

Recommendation reason codes retain bounded per-exercise progression provenance
(policy, outcome, axis, source IDs and base digest) and accepted deload identity.
Deload provenance also retains its typed source, rather than relying on a
subsequently edited profile to explain the reason. Current validation is
`WHOLE_PROGRAM_V2`; `PROGRAM_STATE_V2` recommendations require a complete ordered
decision record for each selected exercise. Version 1 historical records are
still read without inventing those records. Source identifiers containing the
persisted `|||` separator produce a typed non-progressing outcome; they are not
written into a corrupt reason list. The reason capacity is 228 tokens and each
token is bounded to 320 characters.
Older records remain readable. Derivable history is not stored as a counter.
Read recent recommendation records in a bounded batch, not one query per candidate.
Recommendation records now supply target continuity rather than being diagnostic
only. A malformed persisted record therefore fails both the planning read and
export; it is not silently dropped as though no historical record existed.
Unknown future reason-version tokens remain opaque and readable. Export fails
before opening its destination when the stored provenance cannot be decoded.

Context identity includes every consumed richer history measurement, target,
feedback, status, source ID/timestamp, units, policy identity, base digest, and
deload revision/choice. Clock freshness depends on whether an observation is
future, not a new digest on every minute tick. Existing date/week/zone behavior
remains. Changes trigger Today invalidation; start rebuilds and compares before
writing. The start transaction also checks the decision revision and consumes an
accepted choice with the session/record or writes none of them.

Read/write failures become existing typed error states or bounded exceptions;
no empty-success fallback, swallowed corruption, or successful partial start.
All storage remains local; no account, network, telemetry, or model inference.

## Verification and release boundary

Use TDD for every changed behavior. Extend the existing progression/factory,
state/eligibility, validator, identity, lifecycle, production-composition,
corpus, Room/migration and archive suites. Assert exact changed and unchanged
fields for all seven measurement shapes, negative evidence, units, bounds,
repeatability, no stacked legacy increment, and repair precedence.

Run the complete requested Python/importer/acceptance/pinned-catalog, Gradle
test/lint/build, and connected Android gates. New controls require English,
neutral Latin American Spanish, large-text and acceptance/decline restoration
evidence on an identified Android device. Language/theme/gender remain
presentation-only. No human metadata sign-off, eligibility expansion,
substitution, template-editor expansion, multi-week blocks, dependency upgrade,
release, merge, tag or deployment is part of this package.

Knights' complete configured independent implementation panel, repair rounds,
material documentation updates and a separate fresh final full-panel review are
required before the non-draft pull request. This design does not waive those gates.
