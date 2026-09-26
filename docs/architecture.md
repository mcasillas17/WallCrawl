# WallCrawl Architecture

This document describes the application that is currently in the repository.
The historical design and implementation plans under `docs/superpowers/` explain
how individual phases were developed, but they are not the source of truth for
the current architecture.

## Product boundary

WallCrawl is local-first. The exercise catalog, profile, workout templates,
active workout, completed history, and progress data are stored and processed locally.
There is no account requirement, catalog network request, or production LLM in
the current application. Movement-capability answers add no cloud sync,
analytics, Health Connect, Wear OS, network, or model data flow.
The app opts out of implicit Android backup and excludes app data from cloud
backup and device transfer through both legacy and modern rules. The
[privacy policy and storage-boundary diagram](privacy.md) distinguish that
configuration from OEM transfer limitations and previously retained backups.

The application supports two workout entry points that converge on the same
session and history model, both gated behind first-run onboarding:

```text
                 fresh install → onboarding (goal, capability, equipment, constraints)
                                          │
                         ┌─ automatic recommendation
Bundled catalog ─────────┤  profile + history → filter → planner → validator
                         │
                         └─ manually saved template
                            full catalog → editor → template validation
                                          │
                                          ▼
                               frozen workout session
                                          │
                                          ▼
                              type-aware set logging
                                          │
                                          ▼
                              completed local history
                                 ├─ progress metrics
                                 ├─ read-only targets, outcomes and decision detail
                                 └─ future planner context
```

Automatic recommendations and manual templates intentionally share exercise
IDs, prescriptions, active-session persistence, logging, and analytics. This
keeps a future local model behind a replaceable planning boundary instead of
making the rest of the app depend on one inference implementation.

## Source organization

WallCrawl currently ships as one Android application module with package-level
boundaries:

| Package | Responsibility |
| --- | --- |
| `app` | Navigation and application composition |
| `core/model` | Catalog, profile, prescription, workout, template, and analytics models |
| `core/backup` | Versioned local-data archive contract, its codec, and its validation |
| `core/database` | Room entities, DAOs, relations, migrations, and repositories |
| `core/io` | Bounded reading shared by the parsers that accept untrusted input |
| `core/locale` | The app language preference and the platform per-app locale boundary |
| `core/exercise` | Catalog search/filtering and the visual-provider boundary |
| `core/ai` | Context building, workout planning, prescription defaults, and validation |
| `core/progress` | Pure calculations over completed sessions |
| `core/ui` | Theme, reusable Compose components, locale-aware number formatting and parsing, and the read side of the translation overlay |
| `feature/*` | Screen state, ViewModels, and Compose UI for each product area |

`WallCrawlApplication` owns the current dependency container. Features receive
interfaces such as `ExerciseCatalog`, `WorkoutPlanner`, and repositories rather
than loading assets or using DAOs directly. This can move to a dedicated
dependency-injection framework later without changing the domain boundaries.

## Exercise catalog and visuals

The production catalog is a normalized, bundled snapshot of the
[pinned source catalog](../tools/workout-guide/import-config.json):

```text
app/src/main/assets/workout-guide/catalog.json
                    │
                    ▼
        WorkoutGuideCatalogStore
             ├─ BundledExerciseCatalog ── ExerciseCatalog
             └─ WorkoutGuideVisualProvider ── ExerciseVisualProvider
```

Feature code depends on WallCrawl's `Exercise` model and `ExerciseCatalog`
interface. It does not parse upstream JSON or construct raw asset paths.
Likewise, Compose screens request visuals through `ExerciseVisualProvider` and
render them with `ExerciseIllustration`.

The bundled snapshot contains 302 exercises and 906 original SVG frames.
[Everkinetic](https://github.com/everkinetic/data) is the original pose-artwork
reference, with additional exercises and animation frames by Bryl Lim. WallCrawl
also bundles 1,812 female and male adaptation frames; see
[illustration variants](exercise-illustration-variants.md) for selection, playback,
and attribution. Internal source identifiers and original notices are preserved.
Search covers
IDs, names, aliases, muscles, and listed equipment in every shipped language at
once, accent-insensitively, and always resolves to the same catalog IDs. The
Spanish names, coaching summaries, and muscle and equipment vocabulary come from
a separate overlay asset keyed by those same IDs and by the canonical English
vocabulary, so a translation is never a filter key and never edits the pinned
snapshot:

```text
app/src/main/assets/localization/exercise-localization.json
                    │
                    ▼
        ExerciseLocalizationStore ── ExerciseLocalizationSource
             ├─ BundledExerciseCatalog   (search terms)
             └─ ExerciseVocabulary       (display text, via LocalExerciseVocabulary)
```

A failure to load the overlay is not fatal: lookups fall back to the catalog's
English. See [Localization](localization.md). The importer under
`tools/workout-guide/` validates and regenerates this snapshot from a pinned,
clean upstream checkout; the installed application never runs the importer or
contacts the source repository.

Pull-request/main CI runs `tools/workout-guide/check_pinned_catalog.py` before
Java/Gradle setup. It validates `import-config.json`, fetches its exact source pin
into an isolated temporary checkout, and invokes the importer in `--check` mode
against the complete committed bundle and generated review report. Drift or an
unavailable upstream fails the build job without rewriting checked output. See the
[catalog instructions](../README.md#offline-exercise-catalog) for reproduction
and failure diagnostics.

`WorkoutGuideCatalogParser` is also where upstream muscle names become
WallCrawl's, through `MuscleVocabulary`. Normalizing here rather than in the
asset keeps `catalog.json` byte-identical to the importer's output — so
`import_catalog.py --check` still verifies it — and keeps the vocabulary
decision in Kotlin where unit tests cover it. Two rules matter downstream:

- an upstream name maps to exactly one legacy primary muscle; Progress describes
  involvement for every listed primary and explicitly marks those counts as overlapping;
- the other groups an umbrella name covers become secondary muscles, so nothing stops being
  selectable: they keep the exercise eligible for those splits' accessory slots. They do not
  establish a split's [advertised focus](#advertised-focus), which reads primary muscles only.

`BundledCatalogVocabularyTest` reads the shipped asset directly and fails if a
future catalog introduces a name the vocabulary does not know. The instrumented
parser tests also cover the packaged catalog in CI.

The parser also carries catalog provenance forward as `CatalogAttribution`
instead of validating and discarding it, because the CC BY-SA 4.0 license on the
artwork requires attribution to reach the user. `CreditsScreen` renders it
alongside the bundled notice files.

See the [README](../README.md#offline-exercise-catalog) for import commands,
the pinned commit, and licensing details.

### Type-dependent legacy programming

`ExerciseProgrammingMetadata.recommendedRepRange` is nullable. When the optional
programming block exists, `Exercise` construction/copy requires a positive ordered
integer range up to 1000 for the three rep-based types, and no range for `DURATION`
or `DISTANCE_DURATION`. The Python importer selects the corresponding authored
rep-range schema definition using the pinned catalog type. Android validates after
resolving type, so JSON member order does not affect acceptance. The test-only catalog
projection uses the same domain validation; replay copies preserve null.

Timed inputs may omit the field or use null; generated timed records always carry
explicit null. Invalid shapes/types fail with bounded field-specific errors. The
exercise list and details omit rep badges when the range is null while displaying
available programming and coaching. The unchanged prescription factory uses duration
branches, never a rep-to-time conversion. See [Timed-hold programming](timed-hold-programming.md)
for the exact 14-entry cohort, support equipment, and AI-authorship limits.

## Onboarding and profile safety defaults

`UserProfile` never assumes gym access or training history it has not been
told about. A fresh profile defaults to `onboardingCompleted = false` and
`availableEquipment = [BODYWEIGHT]` — not the prior intermediate/full-gym
assumption — and `confirmedStartingLoads` and `trainingConstraints` start
empty. Every `MovementCapabilityType` resolves to `UNKNOWN` unless an explicit
stored value says otherwise. `WallCrawlApp` reads the onboarding flag to pick
the nav-graph start
destination: Today is never rendered or generated for a profile that has not
completed onboarding, so a fresh install cannot reach automatic planning
before the user has stated equipment, goal, experience, schedule, unit, all
seven movement preferences, and any `TrainingConstraint`s
(shoulder/elbow/wrist/lower-back/hip/knee sensitivity, low-impact-only).

The movement-preference step follows Experience & Units. Its seven questions
cover impact, floor transitions, unsupported squat, upper-body bodyweight push,
vertical pull or hang, balance without support, and continuous activity. Each
question requires an explicit `COMFORTABLE`, `LIMITED`, `AVOID`, or `UNKNOWN`
answer; the UI displays `UNKNOWN` as **Not sure**. A separate answered-key draft
prevents the conservative domain default from making an unanswered question
look complete. `SavedStateHandle` preserves that draft and the current wizard
step across process recreation.

`OnboardingViewModel.complete()` and `UserProfileRepository.saveProfile()`
persist onboarding as one atomic revision rather than one write per field, and
validate every planning-relevant input before it reaches Room: days per week
(2–6), session duration (20–120 minutes), return-after-break weeks (0–520),
non-empty and recognized equipment, and finite non-negative confirmed
starting loads. Training constraints and return-after-break weeks stay
editable from the Profile screen after onboarding; the onboarding flow itself
does not collect confirmed starting loads — see the next section for where
those come from. Movement capabilities are edited in a separate Profile draft:
Save persists the complete profile in one revision, while Cancel or Back drops
the draft without writing.

`TrainingConstraint` remains separate from movement capability. A capability
describes current movement comfort; a constraint is an explicit protected-joint
or low-impact preference. Weight, height, BMI, age, and body composition are not
part of the current profile model.

## Automatic workout generation

`WorkoutGenerationContextBuilder` deliberately collects a bounded view of local
state:

- the current profile and preferences;
- at most eight recent completed sessions;
- at most eight recent attempts of every status, plus a bounded batch of their
  recommendation records and the current durable deload choice;
- normalized exercise history and recently trained muscles;
- a separate bounded fourteen-local-date completed-work range for scheduling;
- the full bundled catalog after hard filtering.

Production filtering is `ExerciseEligibilityPolicy` — the reviewed eligibility path,
described just below — because production composition has
`PlannerFeatureFlags.reviewedCapabilityEligibility` enabled. `ExerciseFilter`, which
removes explicit exclusions and exercises whose required equipment is unavailable using
only legacy `programming` equipment combinations and the upstream listed equipment, is
the disabled-path fallback `WorkoutGenerationContextBuilder` uses only when that flag is
off (as tests can still compose). The six-ID application-owned fixed-anchor correction
in `ExerciseEquipmentRequirements.kt` takes precedence for both paths, then legacy
programming equipment combinations or the reviewed equipment alternatives depending on
which path is active, then the upstream listed equipment. Five band setups require
explicit matching confirmations; the cropped anchor in `banded-row` is unresolved and
has no automatic eligibility on either path. Manual templates use the same requirements
for warnings, not for hiding entries. Filtering defines the legal search space but does
not choose the workout. See the
[canonical equipment and compatibility contract](band-anchor-equipment.md).

An implemented, dependency-injected `ExerciseEligibilityPolicy` is the reviewed-only
automatic legality path, and production composition has it enabled. It accepts only
metadata that passes `Exercise.acceptedMetadata()` — `APPROVED` (human-only, and
currently held by zero bundled records) or `AI_ACCEPTED` (owner-authorized, currently
held by 182 bundled records) — requires one complete reviewed equipment alternative and
the source-bound fixed-anchor minimum (also for supported-regression availability),
preserves explicit exclusions, rejects required capabilities marked `AVOID`, fails
closed for joint-sensitive constraints that lack reviewed mappings, enforces
`LOW_IMPACT_ONLY`, and blocks undemonstrated `ADVANCED` work in every state. A supported regression lifts that ceiling only when the
regression itself is below the advanced ceiling or its family has demonstrated history.
`LIMITED` and `UNKNOWN` capability requirements remain typed soft preferences rather
than becoming favorable assumptions.

On that same reviewed-only path, `WorkoutGenerationContextBuilder` derives
`CapabilityEvidenceSet` locally and on demand from the same bounded max-eight completed
sessions it already read. `CapabilityEvidencePolicy` accepts only two distinct
`SessionStatus.COMPLETED` sessions for the same exercise ID whose non-warm-up work sets
all completed, all logged explicit `feltManageable == true`, and all carry valid
shape-specific persisted values. Comparability is exact exercise ID plus the persisted
measurement shape: `WEIGHT_REPETITIONS`, `BODYWEIGHT_REPETITIONS`,
`ASSISTED_BODYWEIGHT_REPETITIONS`, `TIMED_DURATION`,
`DISTANCE_DURATION_DISTANCE_ONLY`, `DISTANCE_DURATION_TIME_ONLY`, or
`DISTANCE_DURATION_DISTANCE_AND_TIME`. The policy validates field presence, bounds, and
shape consistency but compares no magnitudes or thresholds. This is product
reproducibility, not physiology, readiness, recovery, or medical inference. Evidence
applies only to the demonstrated exercise or one direct `approvedRegressions` target
when both source and target metadata independently pass `Exercise.acceptedMetadata()`
(`APPROVED` or `AI_ACCEPTED`); there is no draft, missing,
inferred, substitution, blank-ID, or transitive expansion.

`WorkoutPlanner` receives only structured `WorkoutGenerationContext`. The current
`FakeWorkoutPlanner` chooses exercises exclusively from `allowedExercises` and returns
catalog IDs with structured prescriptions. Its title and rationale are structured too
(`WorkoutTitleSpec`, `WorkoutRationaleSpec`) rather than rendered sentences, so the
planner holds no display text and produces the same plan in every language; the screen
renders them for the reader, and it is that rendered text that a started session stores
and keeps. Applied ranking explanations remain structured as `WorkoutRankingReason`
values. The supported-regression reason records the selected target, the directly linked
source, and the exact `LIMITED` capability that triggered the preference; English and
Spanish rendering happens only at the screen boundary.
Scheduling reasons additionally name the preferred exercise, its counterfactual alternative,
the accepted direct primary, preferred weekly frequency, and elapsed calendar dates.

The supported-regression policy runs only on the planner's existing legal candidate set.
It precomputes direct reviewed relationships before either comparator runs: both endpoints
must independently pass `Exercise.acceptedMetadata()` — `APPROVED` or `AI_ACCEPTED`,
through the same shared seam every other consumer reads — the source must directly list
the target as an accepted regression, the
target must be explicitly `SUPPORTED`, and the source must have an unresolved
exercise-specific `LIMITED` preference for a capability the target does not require.
Evidence for the source suppresses this signal along with the existing capability penalty.
There is no name, muscle, family, reverse-edge, substitution, optional-support, chain, or
unreviewed inference. Focus and accessory-mechanics ordering remain stronger; experience,
scheduling, fatigue, and stable ID remain weaker. Compound and accessory selection share the same
precomputed map, so comparison is total and performs no graph traversal.

`RecommendationSnapshot` retains applied supported-regression reasons and encodes each into four bounded
versioned `reasonCodes`: an indexed `SUPPORTED_REGRESSION_PREFERENCE_V1` marker plus
`PREFERRED`, `SOURCE`, and `CAPABILITY` fields. Restore rejects incomplete, duplicate,
noncontiguous, unknown-field, or identical-endpoint structured groups. An otherwise
well-formed reason naming a capability introduced by a future build remains opaque and is
not rendered by an older build, matching the recommendation record's text-version contract.
Reusing the existing reason-code channel avoids a Room migration or archive format change
while preserving the decision through session creation, export, and restore.

Each scheduling reason uses six bounded `TRAINING_FREQUENCY_RECENCY_V1.<index>` tokens:
the marker plus preferred ID, alternative ID, direct primary, weekly frequency and elapsed
calendar dates. Current structured reason types share a contiguous index space; malformed
current groups are rejected rather than partially rendered.

The record accepts at most 228 reason-code tokens. The current planner selects at most six
exercises; comparing that result with its six-exercise no-preference baseline can therefore
attribute at most 36 source-target inversions. Four tokens per inversion plus the single
repair code uses 145 tokens. At most six scheduling reasons use six tokens each, and
scheduling-policy and generation-index provenance use two more: `144 + 1 + 36 + 2 = 183`.
Package 9 adds six seven-token progression groups and three deload tokens:
`183 + 42 + 3 = 228`. Reason tokens allow 320 characters to retain source IDs
with their prefixes; other identity/version tokens retain the 200-character limit.
This preserves every applied supported-regression source without truncation and bounds
scheduling explanations to one witness per selected exercise. The validation-only increase
reads older 64- and 160-token records unchanged; older readers may reject new records beyond
their own limit. Package 9 separately adds Room schema 14 and archive format 4
for owned choices, not forward-compatible promises to older readers.
Unknown future reason-version tokens remain opaque.

`GeneratedWorkoutValidator` verifies that every ID exists, remains in the
allowed set, matches the catalog exercise type, and belongs to a structurally valid
workout. Unknown IDs are rejected, never silently substituted. It reports those checks
as typed `ProgramViolation` values rather than as a first-failure message, because a
rejection has to be explainable completely. `ProgramValidator` is its only caller.

### Whole-program validation

`ProgramValidator` checks one complete proposed session against the exact
`WorkoutGenerationContext` that produced it, following the
[evidence-to-rule contract](research/2026-08-29-training-science-evidence-review.md#validation-scope-clarification-2026-09-05).
It reuses `GeneratedWorkoutValidator` rather than restating its checks, and adds declared
session constraints, explicit exclusions, reviewed provenance on the enabled path, load
provenance, duration agreement, and aggregate weekly dose. It is pure: it writes nothing,
mutates no ledger, and never credits a proposal as completed work.

The unit is one session. There is no multi-session horizon in version 1, so nothing
reserves allowance against work that has not been proposed.

Two rules are internal consistency rather than program design, so they are always on:
duration agreement, and `UNSUPPORTED_WORKOUT_FOCUS` — every claim the card makes about what
a session trains must hold, under the same [focus contract](#advertised-focus) the planner
uses. That covers both the advertised split and each name on the focus-muscle line beneath
it, because both are displayed and both are copied onto the started session. A caller cannot
reasonably ask for a session whose title contradicts its content. Neither rule is a claim
about pattern coverage, and neither is a safety judgement.

Program-design constraints are **declared**, never universal. `SessionProgramConstraints`
carries them: `uniqueExerciseIds` defaults on, because two instances of one id inside a
generated session cannot be told apart in its record and would be counted twice by
prospective dose accounting — an accounting and identity rule, not a claim that repeating
a movement is harmful, and never applied across sessions, weeks, families, or manual
templates. `uniqueProgressionFamilies` and `requiredMovementPatterns` are inert unless a
caller declares them, so no workout is required to cover any pattern. Both read approved
metadata only — coverage falling back to the legacy authored pattern — so an unapproved
draft record can never drive a product-policy rejection. The planner's pattern spreading
stays a ranking preference with an explicit fallback to repeated patterns.

Reviewed load/assistance provenance reuses the shared prescription decision.
A number must match its held source or exact authorized progression. External
load and assistance have separate sources; external-load confirmation cannot
authorize assistance. Warm-up-only/future work supplies no reviewed starting
load. Null remains null. Only manual/disabled defaults retain the history-only
5 lb / 2.5 kg increment; it cannot stack with reviewed progression.

`DURATION_ESTIMATOR_V1` in `WorkoutDurationEstimator` is the single named estimator the
planner and the validator share: `targetDurationSeconds` or 45 seconds of work per set,
rest counted once per set, truncated to minutes and clamped to 1..240. The reported
estimate must be in bounds and within ±1 minute of it. Version 1 enforces **no**
relationship to `preferredWorkoutDurationMinutes`: an estimate is not a completion-time
promise, and the bounds are structural, not physiological.

On the reviewed path only, the whole proposal is attributed prospectively to approved
`directPrimaryMuscle` values, preserving `PRIMARY_ONLY_V1`, and
`completed + proposed ≤ configured allowance` is checked once per muscle rather than once
per exercise. A damaged ledger (`MALFORMED_WEEKLY_LEDGER`), unrepresentable arithmetic
(`DOSE_ACCOUNTING_OVERFLOW`), and a full configured allowance
(`WEEKLY_ALLOWANCE_EXCEEDED`) stay three distinct reasons. `NEEDS_ONBOARDING` configures
no allowance, which is recorded as absent rather than treated as a violation. Exceeding an
allowance is a mismatch with a versioned WallCrawl number, never proof of overload or
medical danger, and no weekly minimum or automatic set-count increase exists.

At most **one** deterministic repair pass runs, and only at generation time. It may only
reduce `targetSets` so aggregate accounting holds — allocated in recommendation order with
every affected exercise keeping at least one set — and then recompute the duration under
the same estimator. If an affected exercise had a pending progression, repair first
restores its reference axis and records `HOLD_VALIDATION_REPAIR`.
It never weakens a constraint, widens the candidate set, changes
selection, invents a load, alters effort or rest, or falls back off the reviewed path; when
the remainder cannot leave every affected exercise a set it fails closed rather than
dropping one. Repair is disabled at workout start, so a displayed plan is never silently
replaced. It also cannot destroy or conceal focus coverage: reducing sets never removes an
exercise, and a focus violation is not an exceeded allowance, so its presence blocks repair
outright.

There is no numeric physiological fatigue budget here, no summation of the legacy ordinal
`programming.fatigueScore`, and no timestamp-derived readiness, overload, or blocking
recency rule. The separate [scheduling preference](#frequency-and-recency-scheduling)
only changes ordering. `WorkoutHistoryAnalyzer`'s default 72-hour `focusMuscles` lookback
remains a history summary; the new preference does not consume those labels.

`WorkoutGenerationContext` already carries the complete `UserProfile`, so no second
capability field exists. Production composition sets
`PlannerFeatureFlags.reviewedCapabilityEligibility = true` because an owner-authorized
audit accepted 182 of the bundled cohort's 211 authored entries as `AI_ACCEPTED` (0 are
human `APPROVED`; 29 stay `DRAFT`; 56 carry no authored block; 35 sit outside
automatic-strength scope). The current production recommendation therefore follows the
reviewed eligibility gate and responds to capability changes. Tests can still compose the
disabled legacy path with a locally built `PlannerFeatureFlags(reviewedCapabilityEligibility
= false)`; a reviewed no-candidate result reaches `TodayViewModel` as a typed reason and
never falls back to an unreviewed exercise. Genuine human `APPROVED` review of the
metadata remains the outstanding gate before any record can carry that state instead.

Selected joint sensitivities are decided per exercise on that path, against the reviewed
`clearedTrainingConstraints` field, and the supported-regression exception applies the same
rule so a restriction cannot be routed around. No bundled record clears any sensitivity, so
a joint-sensitive profile currently receives a typed `TRAINING_CONSTRAINTS_REMOVED_ALL`
refusal. `LOW_IMPACT_ONLY` is decided by `impactLevel` alone. See the
[eligibility boundary](reviewed-capability-eligibility.md).

On that reviewed-enabled path only, `StateBasedTrainingPolicy` consumes the composed
`TrainingProgramState`. Production emits `PROGRAM_STATE_V2` (V1 contexts remain
compatible at the policy boundary). It validates `PRIMARY_ONLY_V1`, accepted
provenance, review-policy equality, and prescription shape before using the approved
direct-primary muscle. It independently re-checks the capability and training-constraint
rules the eligibility gate already applied, and returns a typed failure rather than
prescribing: `CAPABILITY_AVOID_REACHED_POLICY` for an `AVOID` capability requirement, and
`TRAINING_CONSTRAINT_REACHED_POLICY` for either training-constraint rule — a selected joint
sensitivity the exercise does not clear, or `LOW_IMPACT_ONLY` meeting `ImpactLevel.HIGH`.
Those names all say the same thing: eligibility should have removed the exercise, so for those
rules a gap in one gate cannot let a user's restriction through. Other gate rules, such as
explicit exercise exclusions, equipment availability and the temporary advanced-complexity
ceiling, have no second boundary here and are enforced by `ExerciseEligibilityPolicy` alone.

It caps a base prescription by the remaining configured weekly product allowance and never
increases it. Exact/over-cap exposure returns typed
no-guidance instead of a zero-set or over-cap prescription; malformed or version-mismatched
input returns a typed failure with no legacy fallback.
Each exercise reads the same completed ledger; `ProgramValidator` checks the
aggregate proposal without writing reservations to history.
Exceeding an allowance is not proof of overload or medical danger.

The same pure result carries nullable RIR guidance and a classified rest target. Product
defaults are 2-4 RIR for conservative states or a relevant approved `LIMITED` capability,
1-2 for established strength, and 1-3 for established general/hypertrophy; automatic
guidance cannot contain 0 RIR. `SHORT`, `MODERATE`, and `LONG` currently map to configured
60/90/180-second product defaults. These values are product policy, not physiology,
safety, or optimality claims. Policy construction is configurable; the app does not ship
settings for editing these allowances, RIR bands, or rest-class defaults. Explicit valid
stored user rest preferences win, but timer add/skip controls do not persist preferences.
State and capability can reduce sets but cannot change or invent a load.

Within a split, compound slots are chosen first by genuine focus support inside the
compound pool, then by the reviewed capability soft-penalty bit, supported-regression
preference, experience, scheduling, fatigue, and stable ID, while still spreading work across
movement patterns so a session is not the same lift three times. Remaining accessory
slots prefer exercises that train the split directly, then isolation work, then the
presence of programming metadata, that same capability penalty, supported-regression
preference, experience penalty, scheduling, fatigue, and stable ID. Because focus support is the
first key in both passes and at least one accessory
slot always remains, a supporting exercise is always reachable; the planner states that
as an invariant rather than leaving it to be re-derived from the two comparators.
Qualifying source evidence suppresses both the penalized candidate's one-bit capability
penalty and its supported-regression signal; evidence never adds candidates, never
removes candidates, and never outweighs the harder split or mechanics ordering that
already happened before it.
The legacy `programming.fatigueScore` is an ordinal product ranking label, not a measured
physiological quantity or a budget to sum. Pattern spreading is a preference with a
fallback to repeated patterns, not a universal uniqueness or all-patterns coverage rule.

Split selection is deliberate about failure. High-priority muscles propose a
rotation; splits the candidate pool cannot fill are dropped from it, and if none
of the preferred splits survive, the full rotation is used instead. An exercise
that trains none of a split's muscles is never substituted in — that is what
produced push days padded with unrelated work — but the planner only fails when
nothing at all is trainable, so failure never depends on what the user
prioritized. Rotation is seeded from completed-workout count so it advances
across process death, not just within a session.

### Progression and user-controlled deload

`ONE_VARIABLE_PROGRESSION_V1` compares exact-ID completed attempts, full
prescriptions, typed measurements, units, timestamps and explicit feedback.
Two latest comparable attempts must fully complete their prescribed normal work,
confirm manageability and report qualifying RIR or RPE. Missing feedback, stopped
work, duplicates and changed targets produce typed holds, not readiness guesses.
Capability evidence stays separate. Defaults propose one axis only: external
load +2.5 kg, bodyweight rep range +1, assistance -2.5 kg, or duration +5 seconds.
Distance shapes are domain-tested, not admitted to automatic strength planning.
These are versioned product rules, not physiology.

The factory retains compatible earned targets using stored base-configuration
digests. Dynamic set/rest/effort changes can suppress progression without resetting
that earned axis. Comparison respects the actual logger's two-decimal
representation, including conversion and rounding; stored history is unchanged.
`WHOLE_PROGRAM_V2` requires complete ordered decision provenance for production
`PROGRAM_STATE_V2` proposals and recomputes the same policy for validation.

`DELOAD_ONE_WORKOUT_V1` is an explicit request or returning-user offer on Today.
It previews held reference targets with one fewer work set per exercise, minimum
one; any pending progression increase is paused. Displaying/declining/dismissing
does not accept. Acceptance persists until cancellation or one automatic start;
templates do not consume it and frozen workouts never change.
There is no calendar law, fatigue diagnosis or multi-session block.

State precedence is reported `RETURNING`, then accepted `HOLD`, otherwise
`UNCALIBRATED`. No `DELOAD_OFFERED` state is derived. Training policy V2 keeps
all three at 6 weekly primary sets, two sets per exercise and conservative effort
before deload. Every state retains the advanced ceiling unless the existing
demonstrated-family or legal supported-regression exception applies.

Context identity v4 includes richer outcomes/feedback, continuity provenance,
working-load sources, choices/revisions and relevant policy/time inputs. Today
publishes proposal/unit/snapshot together, preserves the variation index during
passive refresh, and refuses stale starts. The Room start transaction independently
checks the choice revision, including zero, before writing session, record and
consumption together. The [focused contract](superpowers/specs/2026-09-19-progression-and-deload-design.md)
contains the full comparison, state and lifecycle tables.

### Frequency and recency scheduling

`TRAINING_FREQUENCY_RECENCY_V1` is one positive, binary **repeat-practice preference**.
The profile's `daysPerWeek` still means total preferred training days, not a per-muscle
quota. It sets a product revisit band of `ceil(7 / daysPerWeek)`:

| Preferred weekly days | Revisit band in local calendar dates |
| --- | --- |
| 2 | 4 |
| 3 | 3 |
| 4, 5, or 6 | 2 |

These bands, the fourteen-date lookback, and the two-date familiarity condition are named,
versioned product choices. Research informs distributing practice around preference, not
these constants. None is a recovery interval, physiological score, dose floor, obligation
to train, or automatic progression rule.

`WorkoutGenerationContextBuilder` preserves the recent-eight view for loading, capability
evidence and rest choices. Separately, it reads the canonical completed scheduling range
through the existing history DAO/repository: local today minus thirteen at start-of-day,
through the sampled instant **inclusive**. One explicit instant and zone also determine
the program-state week. The range spans fourteen local calendar dates, respects DST and
midnight transitions, and does not reset on Monday. Future completion timestamps contribute
nothing. Neither comparators nor the pure scheduling policy read the system clock.

A `COMPLETED` session contributes a primary-muscle practice date only when an exercise
instance contains completed non-warm-up work. The policy reuses `isCreditableWorkSet` and
`SetOutcomeRules`: completed work in a partially performed completed session counts, while
unfinished/stopped/skipped sets, planned or abandoned sessions, and warm-up-only work do
not. Legacy missing set timestamps remain missing; the date comes from the canonical
session completion timestamp. No load, reps, effort, or manageable threshold is introduced.

Attribution is exact catalog ID through `Exercise.acceptedMetadata().directPrimaryMuscle`.
There is no session-focus, legacy-muscle, secondary-involvement, name, or movement-family
fallback. Unknown and unaccepted work supplies no attributed date. Multiple sets, exercise
instances, or sessions on one local date count as **one** date per primary. Two distinct
dates inside the window establish familiarity. A familiar primary receives the preference
when its latest practice is at least the revisit band old; additional age earns no extra
weight. Unseen, once-seen, stale, future-only, and more-recent primaries stay neutral.
Every exercise sharing that primary receives the same key, so changing an exercise ID
merely for novelty earns nothing.

Frequency sets the band and recency tests membership: they are not two additive scores.
The precomputed key sits after experience and before the legacy fatigue ordinal and stable
ID in both selection passes. Focus, accessory mechanics/programming presence, capability
evidence, and supported regressions remain stronger. Eligibility, compound slots, pattern
spreading/fill, weekly allowances, prescriptions and whole-program validation are unchanged.

Applied reasons compare the actual selection with the same selection with **only**
scheduling disabled. A same-pass, stronger-tier-equivalent alternative must actually lose
position or selection; signal presence alone emits nothing. The supported-regression
counterfactual conversely retains scheduling. Scheduling reasons render equivalent English
and Spanish copy at the UI boundary; stored values never depend on language or gender.

One complete range is bounded to 2,000 sessions, 100,000 examined sets and 64 primary keys.
Malformed input or overflow fails explicitly, never as a successfully truncated history.
The observable day-range query retains an ascending 2,001-row probe; filtering to `<= now`
precedes the 2,000-session check. Thus future rows cannot create false overflow, while the
first future sentinel detects real overflow once it becomes current. Candidate keys are
precomputed once; comparators perform no database calls, history scans or graph traversal.
Selection needs at most three traces: actual, supported-regression-off, and scheduling-off.

**Freshness and replay.** Context identity format `wallcrawl-recommendation-context-v3`
includes both frequency fields, scheduling policy, canonical practice dates, local date and
zone, even when scheduling is neutral. It also fingerprints the derived history projections
the planner actually consumes: candidate capability-evidence membership, usable load sources
and units, the shared completed-rep comparison, explicit rest choices, and ledger direct
counts/provenance/integrity. Same-day history changes cannot disappear behind an unchanged
date set or lifetime count. It excludes language, gender, raw session/set identifiers and
unused performance metrics; the digest is local change detection, not anonymization or a
promise of transactional reads.

Today observes while **RESUMED**, releases its expensive range subscription immediately
when hidden, and rechecks on resume. Room changes trigger context checks even if practice
dates are unchanged; unchanged clock ticks do not poll the database. Retry/resume reconnect
failed observation, whose error cannot be hidden by an unrelated successful generation.
Passive invalidations coalesce only when their consumed context is already settled; explicit
regeneration remains separate. Superseded or cancelled attempts cannot settle an identity,
and lifecycle teardown is not treated as new history. Start still revalidates the captured
recommendation against a fresh context with no repair.

Equal-input replay includes the generation ordinal as well as the same canonical context.
`regenerationIndex` supplies it explicitly; null retains the existing in-memory counter and
completed-workout-count split rotation. `PLANNER_GENERATION_V1:<index>` and the scheduling
policy travel in the existing record reason-code channel, including neutral scheduling.
The ordinal is separate from freshness so advancing a counter cannot invalidate its own
displayed recommendation. Restart/restore reconstructs evidence from immutable completed
history; replaying an old recommendation still requires its original profile/context, which
the archive does not promise to reconstruct from a digest.

**Production example.** `ProductionPlannerCompositionTest` controls an otherwise comparable
four-candidate PUSH pool of real bundled `AI_ACCEPTED` records. With the same two completed
shoulder-practice dates aged three and two dates, completed count, and generation ordinal,
two days/week orders `dumbbell-bench-press` before `dumbbell-shoulder-press` and selects
`cable-fly`; six days/week orders shoulder press first and selects `cable-lateral-raise`.
Moving that history to yesterday/today at six days/week restores neutral ordering. Both
passes change without changing the legal set. Exact typed witnesses survive validator,
record and codec round trips; full-cohort and constrained-profile tests separately preserve
valid plans and honest typed refusals. This is software conformance, not clinical validation.

### Advertised focus

A split is a promise about what a session trains, and `WorkoutFocus.kt` holds the one
rule that decides whether the promise is true. A split is **genuinely trained** by an
exercise when that exercise is strength work and one of the muscles it trains as its own
purpose is in the split's `targetMuscles`. Own-purpose means the accepted
`directPrimaryMuscle` where the reviewed contract applies — `Exercise.acceptedMetadata()`
returning non-null, whether that record is human `APPROVED` or owner-authorized
`AI_ACCEPTED` — and the legacy `primaryMuscles` otherwise, so a `DRAFT` or unaccepted
record drives nothing here either. Descriptive secondary muscles are excluded.

Split fillability, both ordering passes, the selection invariant, the muscle line the card
shows and whole-program validation all read that one predicate, so a split the planner
considered fillable and a split the validator accepts cannot mean different things. Slot
eligibility is a deliberate **superset** — `canFill` — so an exercise that only brushes the
split is still legal accessory work once the focus is established, while the candidate that
made a split fillable can never be dropped from the pool that fills it. Both halves of an
exercise's muscle description resolve the same way, so the reviewed path never mixes an
approved `directPrimaryMuscle` with a legacy secondary list.

The rule is muscle-based rather than pattern-based because only 131 of the 302 bundled
entries carry a movement pattern and genuine pushes such as `archer-push-up` and
`handstand-push-up` carry none; requiring one would invent an absence. It is a truthfulness
rule about one label, not a claim that a session must cover every pattern, that repeated
movement is harmful, or that a supported session is medically appropriate.

Because focus evidence is primary muscles only, an exercise whose upstream name is an
umbrella term carries its `MuscleVocabulary` representative — `Legs` → Quadriceps,
`Posterior Chain` → Hamstrings, `Full Body` → Core. The groups demoted to secondary keep it
eligible for those splits' accessory slots but do not establish a split or clear a `HIGH`
priority.

Muscle priorities stay soft while equipment, exclusions and capabilities stay hard. When
a `HIGH`-priority muscle has no candidate that trains it as its own purpose, the planner
reports it in `GeneratedWorkout.unavailableFocusMuscles` — canonical names, sorted, so the
list never depends on map iteration or on the device language, and capped at the same three
entries the focus-muscle line carries — and the session is the accurately labelled
alternative the ordinary rotation already chose. The suggested-workout card adds one
localized sentence naming those muscles, **only when the list is non-empty**, so an
ordinary session's card is unchanged; the same sentence is appended to the explanation a
started session stores. Surfacing the whole explanation on every session would be a
separate product decision, and is not made here. The cap is not cosmetic: a restored archive may carry
up to 2,000 profile-supplied priority keys, and an uncapped sentence would push the stored
notes past the archive's own length limit and break the user's next export. Nothing is
relabelled generically to hide the mismatch, no equipment is borrowed, and no exercise is
invented. When no split is supported at all, the existing typed
`NO_CANDIDATES_FOR_ANY_SPLIT` outcome and its resource-backed Today copy apply instead.

Cardio machines, distance work, and stretches are excluded from automatic
selection while remaining fully available in the catalog and in custom
workouts. Rep work and non-stretch duration work without a Cardio muscle tag can
fill strength slots; metadata presence does not change this classification.

Planning failures carry a `WorkoutPlanningFailure` reason rather than
user-facing text; `TodayViewModel` maps reasons to copy. A future planner chain
branches on the same reason to decide between repairing, falling back to another
tier, and surfacing the failure — string matching on messages could not support
that.

`TodayViewModel` validates in two places. After generation it runs `ProgramValidator`
with repair permitted, and shows nothing when the result is a rejection; a rejection
whose every reason is an exceeded allowance gets its own copy about the configured plan
rather than a generic failure. At start it rebuilds the context, compares
`RecommendationContextIdentity` — a digest over profile identity and revision, completed
workouts, the ordered candidate ids, catalog and review-policy identity, the reviewed-path
flag, adaptation state, declared constraints, and the accounting week and zone — and
revalidates with repair disabled. An edited profile, newly completed history, or a crossed
week or time-zone boundary is reported as an out-of-date recommendation, not started
quietly. The digest reads no locale and no display text, so language cannot move it.

A future local LLM should implement the same `WorkoutPlanner` interface. Model
integration does not remove the hard filter or validator; constrained decoding
and schema enforcement would be additional defenses at the inference boundary.

## Manual workout templates

Manual templates intentionally bypass automatic equipment filtering because the
user is making an explicit choice. The editor searches all 302 exercises and
shows equipment mismatches as warnings. Catalog existence and exercise-type
agreement remain hard requirements.

`WorkoutTemplateRepository` owns template CRUD and maps between the domain model
and Room. Saving a template transactionally replaces its ordered exercise rows.
Starting one goes through `WorkoutRepository.startWorkoutFromTemplate`, which
creates a standalone session snapshot tagged with:

- `origin = CUSTOM_TEMPLATE`;
- the informational `sourceTemplateId`;
- the profile's current weight unit;
- copied exercise order, notes, prescriptions, and set targets.

The source template ID is not a foreign key. Editing or deleting the template
therefore cannot alter an active or completed session. See
[Custom Workouts](custom-workouts.md) for product behavior and current editor
limits.

## Shared prescription and logging model

`ExercisePrescription` is the common contract for planner output, templates,
and session snapshots. It prevents incompatible target combinations for the five
catalog exercise types:

| Exercise type | Prescription and logged outcome |
| --- | --- |
| `WEIGHT_REPS` | Repetition range and optional load; actual reps and load |
| `BODYWEIGHT_REPS` | Repetition range; actual reps |
| `ASSISTED_BODYWEIGHT` | Repetition range and optional assistance; actual reps and assistance |
| `DURATION` | Target seconds; actual seconds |
| `DISTANCE_DURATION` | Target distance, duration, or both; actual distance and/or duration |

Domain constructors reject malformed prescriptions before persistence. The shared
prescription also carries nullable `EffortTarget`, `RestClass`, and `RestTargetSource`.
A classified rest target must carry its source; an explicit `USER_PREFERENCE` can be
reused by later reviewed recommendations, while a generated `PRODUCT_POLICY` target
cannot promote itself into a preference. Manual templates stay outside the automatic
policy and preserve whichever valid values they already contain.
`WorkoutRepository.logSetCompletion` validates recorded fields against the
persisted exercise type and prevents updates to sets whose session is no longer
active. It is the only way a set outcome is written.

### Typed set outcome

A logged set carries a typed outcome alongside its type-specific values:
nullable `rpe` (0-10) and `rir` (0-10), a nullable user-confirmed
`feltManageable`, a `completedAtTimestamp`, a `stoppedAtTimestamp`, and a
nullable `SetStopReason` (`USER_SKIPPED`, `PAIN_STOP`, `EQUIPMENT_UNAVAILABLE`,
`TIME_CONSTRAINT`, `OTHER`). `SetOutcomeRules` enforces the cross-field
invariants at the repository boundary before anything is persisted:

- null means unknown and stays null; no feedback is inferred from any other
  field, and a missing RPE or RIR is never replaced by an assumed effort;
- a completed set requires a positive `completedAtTimestamp` and cannot also
  carry a stop reason or a stop timestamp;
- a skipped or stopped set requires a typed reason and a positive
  `stoppedAtTimestamp`, and cannot carry `feltManageable`;
- an untouched set carries no timestamp, no feedback, and no stop reason;
- `feltManageable` is recorded only for completed work;
- rejection messages name the offending field and never echo entered values.

`SetOutcome` (`NotRecorded`, `Completed`, `Stopped`) is the derived read model,
so later adaptation can never confuse work that was never started with work the
user deliberately stopped. `PAIN_STOP` records only that the user chose to stop
because something hurt: it is not a symptom report, an injury, or a diagnosis,
and no surface presents it as one. There is no free-text stop reason.

Reviewed capability evidence already consumes a narrow, deterministic subset of this
feedback behind the reviewed-only flag: only non-warm-up work from two distinct
completed sessions for the same exercise ID, and every qualifying set must have
`feltManageable == true` plus a valid shape-specific logged payload. Null or false
manageable answers, completion alone, RPE, and RIR do not qualify evidence.
`ProgressionEngine` additionally consumes explicit effort and matching targets.
`DeloadOfferPolicy` consumes explicit request/return status, not an invented
multi-session fatigue signal. Only completed work earns ledger credit.

`DefaultExercisePrescriptionFactory` never invents a `WEIGHT_REPS` starting
load. It suggests a weight only when either applies, in that priority order:

1. bounded exercise history exists for that catalog ID, converted to the
   profile's current unit — the existing weight, or a unit-appropriate
   increment only on the isolated manual/disabled path; reviewed progression
   requires the stricter comparable-outcome policy; or
2. the user has explicitly confirmed a baseline in
   `UserProfile.confirmedStartingLoads` for that ID.

With neither, `targetWeight` is `null` and stays null through to the session
snapshot and the active-workout UI: the load field is empty rather than
pre-filled with a fabricated number, and its first plus-press starts from the
planned target only when one exists. Once the user logs a real value, ordinary set
completion and the history analyzer take over for future sessions — there is
no separate write path that copies a logged value back into
`confirmedStartingLoads`.

## Room persistence and invariants

`WallCrawlDatabase` is currently schema version 14. Its tables store:

- the user profile, including onboarding status, multi-select fitness goals,
  training constraints, return-after-break weeks, confirmed starting loads,
  theme preference (`SYSTEM`, `DARK`, `LIGHT`), and movement capabilities;
- reusable workout templates and their ordered exercises;
- workout sessions and their ordered exercise snapshots;
- nullable effort targets, rest classes/sources, and exact rest seconds on template and
  workout exercise prescriptions;
- target and completed values for every set, plus its typed outcome:
  `rpe`, `rir`, `feltManageable`, `completedAtTimestamp`, `stoppedAtTimestamp`,
  and `stopReason`;
- a fingerprinted, reconstructable `PRIMARY_ONLY_V1` weekly-ledger cache whose
  authority remains immutable completed history;
- one immutable whole-program validation record per session started from a
  recommendation, keyed by that session's own id;
- one versioned deload-preferences row per profile, containing its latest choice,
  handled return key and revision, without persisting derived evidence.

Migration `13 -> 14` creates only `deload_preferences`; old rows are untouched.
Choices are separate from replaceable profile rows and survive profile edits.
There is no session foreign key because cancelled workouts are deleted;
consumption stays terminal even when its audit-linked session is absent.

Migration `3 → 4` adds template storage, session provenance, and type-aware
target/outcome columns while converting older repetition-based history to
`WEIGHT_REPS`. Migration `4 → 5` is additive-only: it adds
`onboardingCompleted`, `trainingConstraintsJson`, `returningAfterBreakWeeks`,
and `confirmedStartingLoadsJson` with conservative defaults, and explicitly
sets `onboardingCompleted = 0` for every existing row — a profile created
before onboarding existed was never reviewed against these safety-relevant
fields, so it must not be grandfathered in as already onboarded. Migration
`5 → 6` adds `fitnessGoalsJson` supporting multiple concurrent fitness goals
(e.g., hybrid hypertrophy and strength), initializing existing rows from
`primaryGoal`. Migration `6 → 7` adds `themePreference` with a default of
`SYSTEM`, enabling dynamic theme switching between System Default, Dark Mode,
and Light Mode. Migration `8 → 9` is additive-only: it adds the four nullable set-outcome
columns (`feltManageable`, `completedAtTimestamp`, `stoppedAtTimestamp`,
`stopReason`) with no SQL default, so history written before typed outcomes
existed reads back as an honestly unrecorded outcome instead of gaining a
fabricated completion timestamp or an assumed manageable answer. There is no
destructive migration fallback on any construction path, and the migration
tests exercise every supported starting schema through to version 14. Migration
`7 → 8` adds one non-null `movementCapabilitiesJson` column. Existing rows receive `{}`, which the codec
normalizes to all `UNKNOWN`; their onboarding status, revision, theme, goals,
equipment, constraints, confirmed loads, templates, sessions, sets, and history
remain intact. Every supported migration chain registers the new step, and
destructive migration fallback is disabled.

Migration `9 → 10` is additive-only: it creates the weekly-ledger cache table without
rewriting or dropping profile, template, workout, exercise, set, or outcome data. The
cache is accepted only when its deterministic source fingerprint matches current
completed history, catalog/review versions, policy version, week, and zone; a missing,
stale, corrupt, or deleted row is reconstructed rather than treated as authority.

Migration `10 → 11` adds nullable `effortMinRir`, `effortMaxRir`, `restClass`, and
`restTargetSource` columns to both template and workout exercises. Existing rows retain
their exact `restSeconds` and receive null guidance, so old manual templates, active
sessions, and completed history are not reinterpreted. New values round-trip through
template storage and frozen session snapshots; partial effort or rest pairs fail loudly
when mapped back into the domain.

Migration `11 → 12` is additive-only: it creates `workout_recommendation_records` and
touches no existing table, column, or row. The table starts empty, so a session recorded
before whole-program validation existed keeps an honestly absent record instead of a
fabricated validation outcome. A row holds the validator, estimator, catalog, review,
training-policy, ledger, and program-state versions, the adaptation state, the accounting
week and zone, the profile revision, the context digest, ordered reason codes, and per
muscle the completed, proposed, and configured allowance counts. Version-like columns are
text rather than converted enums, exactly as the ledger cache stores its policy version, so
a value written by a future build reads back as unrecognised rather than coerced. The plan
itself is not duplicated — `workout_exercises` already holds it under the same session id —
and no raw name, note, load, repetition, effort value, or body measurement field is stored.
The context digest nevertheless derives from consumed planning inputs for freshness; it
is not an anonymization guarantee. The row
is written inside the same transaction that inserts the session, its exercises, and its
sets, and cascades away with the session.

The record makes a past decision explainable and any input mismatch detectable without
re-running the planner. It does not promise byte-exact replay: WallCrawl keeps one current
profile row, so a past candidate set cannot be re-derived, and that limit is recorded
rather than papered over with a digest.

Capability JSON is a bounded persistence detail, not a UI model. The codec
accepts at most 4096 characters, validates the flat object shape, allowlists
known enum keys and values, ignores unknown future keys, and resolves missing,
unknown, malformed, or oversized input conservatively to all `UNKNOWN`. Encoding
emits only stable enum names. Raw payloads and complete profiles are not placed
in logs or user-visible errors.

`WallCrawlDatabase.getInstance()` stores `wallcrawl.db` in the application
context's credential-protected database directory. The manifest now sets
`allowBackup="false"` and references `res/xml/backup_rules.xml` for API 26-30
and `res/xml/data_extraction_rules.xml` for API 31+. Both exclude whole data
domains, including database sidecars, rather than only the named database.
Modern cloud-backup and device-transfer sections each carry the full exclusions.
The rules change no database path, schema, migration, local write, or planner gate.
Compatible in-place upgrades retain data. Uninstall or device loss is recoverable
only from a user-owned export taken beforehand, described in the section below and in
[Privacy and backup](privacy.md#restore-prerequisites).
See [Privacy and backup](privacy.md) for domain coverage, the platform-boundary
diagram, OEM limitations, and the lack of any previous-backup erasure guarantee.

### User-owned export, restore, and deletion

`core/backup` writes archive format **version 4** and reads versions 1, 2, 3, and 4: one JSON
document holding the profile, templates, every session with its exercises and sets, and
the whole-program validation record for each session started from a recommendation. That
version is independent of the Room schema version, which travels alongside the app
version, the creation time, and the bundled catalog commit as provenance only.

Version 2 added the `recommendations` array. A version 1 document restores exactly as it
always did and simply carries none; a version 1 document that uses a version 2 field is
refused rather than quietly upgraded. Versions newer than 4 are refused as
unsupported. Each record must name a session the same document carries, exactly once.
Version 4 adds owned deload preferences; older checksums and missing-choice
semantics are preserved. A choice row also makes a restore destination nonempty.

```text
Room (profile, templates, sessions, exercises, sets, recommendation records)
        │  LocalDataBackupDao.readAll()  ── one @Transaction
        ▼
  LocalDataSnapshot  ──►  validate  ──►  checksum  ──►  open document  ──►  write
        ▲                                                                     │
        │                                                                     ▼
  restoreIntoEmptyDestination  ◄── validate ◄── parse ◄── read  one JSON archive
        │  one @Transaction, eligibility rechecked inside                (SAF document)
        ▼
Room, or nothing at all
```

The same `validateSnapshot`/`validateMetadata` contract runs on both sides, so an
export cannot produce a document the reader would refuse. The checksum covers the
archive's canonical serialization and is computed before the destination is opened —
opening with `"wt"` truncates, and a refusal must not destroy a file the user picked.
The reader treats a document as untrusted: bounded input, exactly the known fields,
strict enum names, numeric and length bounds, non-blank identifiers, parent/child
agreement, the one-active-session rule, and the domain invariants
`ExercisePrescription`, `RepRange`, `WorkoutTemplate` and `SetOutcomeRules` already
enforce. Rejections carry a typed `ArchiveRejection` that the UI maps to string
resources, so no parser text — which can quote an offending value — reaches the
screen.

Restore requires an **empty destination**: no sessions, no templates, and onboarding
unfinished. The bootstrap profile row an app creates for itself does not count and is
replaced. Eligibility is rechecked inside the restore transaction, so a concurrent
write cannot slip past the check a caller made earlier, and a refused or failed
restore leaves nothing behind.

`DefaultAppContainer` owns one `Mutex` that the backup, profile, and template
repositories share, so an ordinary write already in flight cannot land after a
deletion and resurrect erased data. Workout writes deliberately stay outside it:
creating a session already fails inside its own transaction when the profile revision
it was started against is gone, and set and completion writes are `UPDATE`s that match
nothing once history is deleted. The gate is never held across document I/O.

The weekly-ledger cache is never exported and never restored; deletion and restore
both clear it, and it is rebuilt from restored history on the next read. Recommendation
records are the opposite case — nothing can rebuild how a past plan was validated from the
history it produced — so they are exported and restored with the sessions they belong to,
and removed by deletion with everything else. A record this build cannot read back is
reported as an error rather than dropped: it now supplies progression continuity.
Export fails before opening its destination. Known progression sources and
exercise membership are validated by the archive. Deload writes share the
existing local-data gate; restore/delete include choices in their transaction.

The persistence layer enforces several important invariants:

- only one workout session may be active;
- session creation inserts the session, exercises, and sets atomically;
- starting a workout uses the current profile revision and weight unit;
- completed or canceled sessions cannot accept additional set updates;
- template deletion cascades only to template exercise rows, never history;
- recommendation targets and performed outcomes remain separate;
- a started session and its validation record are written together or not at all.

Restore and deletion outcomes are observed by `LocalDataOutcomeEffect`, which the
navigation graph places above the onboarding step switch and above the profile's
`LazyColumn`. Neither host keeps its card composed for the whole operation — the
onboarding card exists only on the first step, and the profile card is a list item
Compose disposes once it scrolls out of view — so an effect living with the card could
miss the result it was waiting for. The onboarding wizard also refuses to advance while
a restore is in flight, because finishing it writes a whole profile over the one the
restore is about to commit.

Room-backed `Flow` streams make the active workout and completed history
observable after navigation or process recreation. Onboarding capability draft
answers are restored through `SavedStateHandle` without inventing answers;
Profile capability drafts remain in memory and are discarded on Cancel or Back.
Unsaved template-editor
drafts are in-memory state and are not yet restored after process death.

## Feedback loop and progress

Completing a session preserves its type-aware set outcomes. `ProgressCalculator`
derives current progress from completed sessions rather than sample metrics, and
`WorkoutHistoryAnalyzer` converts bounded history into structured input for the
next recommendation.

Progress answers two different questions without combining their totals:

- **Logged activity:** completed workouts and sets, including warm-ups and timed work;
  positive repetitions from rep-based sets; external-load volume from completed
  `WEIGHT_REPS` measurements, converted to the preferred unit and expressed as load × reps.
  Assistance, body mass, duration and distance are not converted into tonnage.
- **Reviewed primary dose:** the existing `PRIMARY_ONLY_V1` ledger, with one accepted direct
  primary per completed non-warm-up set — credited for both `APPROVED` and `AI_ACCEPTED`
  metadata alike. Descriptive secondaries and typed unattributed work stay separate. The
  bundled cohort's 182 `AI_ACCEPTED` records (0 `APPROVED`) mean reviewed allocation now
  covers most, but not all, logged exercises: the 29 `DRAFT` and 91 unaccepted/out-of-scope
  entries still contribute typed unattributed work instead of dose credit.

Legacy primary-muscle involvement is a distinct descriptive view: a completed set can
appear under multiple muscles, so those counts must not be summed as unique sets. Warm-ups
and timed sets now appear in this view as activity; unknown catalog IDs and conditioning
tags have no muscle mapping. Current and previous muscle keys are retained for comparisons,
including reductions. Percentages describe logged involvement, not physiological growth;
no previous baseline yields a neutral new-activity state.

All weekly activity and dose use `TrainingWeek`: Monday local start-of-day inclusive to
the following Monday exclusive, in the device's explicit `ZoneId`. Calendar dates, not
168-hour durations, handle DST. Membership uses the session completion timestamp, including
clock-skewed completed records anywhere in that selected week. A comparison is the
unfinished current week against the complete previous week. A streak counts consecutive
weeks with at least one completed workout, ending this week if occupied or last week
otherwise; an empty unfinished current week has grace. `TrainingWeek.startEpochDayContaining`
shares the Monday-key calculation without constructing unnecessary instant bounds for every
streak timestamp. Language never selects boundaries or changes canonical numbers.

```text
Room invalidations / RESUMED subscription / clock or zone change / next Monday
                                |
                    OfflineProgressRepository
           shared local-data write gate -> one Room transaction
                                |
           +--------------------------------+-------------------------+
           |                                |                         |
    profile (no bootstrap)         two-week range, all times    existing ledger repo
           |                        + recent 500 sessions       current + previous
           +------- ProgressCalculator -----+                    + derived cache
                                |                                     |
                                +--------- ProgressSnapshot ----------+
                                               |
                                              UI
```

Every source read and both ledger/cache reads share that transaction, so a completed
workout, import, or deletion cannot split activity and dose into different history
revisions. The fingerprint is not used as an activity revision: it deliberately excludes
loads and reps. The shared write gate covers cache rebuilding and a missing profile returns
`NoProfile` without recreating a row. Errors propagate to a retryable screen state, never
to zero activity or an empty ledger. Cache-table invalidations are not observed, avoiding
a self-triggered rebuild loop.

Only the heavy record/trend window remains bounded at 500 sessions (10 shown in recent
history). Complete current/previous week queries have no 500-session limit; all-time counts
and streaks use an unordered, lightweight completion-timestamp query. Existing ledger
representational bounds still fail explicitly rather than truncating counts.

`ProgressScreen` collects only while RESUMED; `WhileSubscribed(0, 0)` stops upstream reads
and clears replay when hidden, then samples fresh time/zone on resubscription. A single
delay to the next calendar boundary handles rollover without a workout write. Protected
device clock/time-zone broadcasts and Retry also refresh. Today uses the same bounded
calendar range for its workout counter; its shared clock feed is reduced to distinct weeks
before subscribing to the count query. Its separate visibility-bound scheduling observation
and resume checks follow the [freshness contract](#frequency-and-recency-scheduling).

The complete [metric table](weekly-dose-ledger.md#progress-metric-contract) specifies
measurement omissions and the preserved independent personal-record/strength rules.
Neither accounting view measures complete physiological stimulus or diagnoses readiness.

Finishing a workout with sets that are neither completed nor stopped raises a
typed `FinishDecision.ConfirmIncomplete` carrying the open-set count, and
nothing is persisted until the user confirms; discarding an active workout needs
the same explicit confirmation. Backing out of either dialog does nothing, and
repeated taps stay idempotent. Skipped sets stay distinguishable from sets that
were never started, and neither contributes volume, history, or progress.

`WorkoutSummary` uses one shared `ProgressCalculator.summarize` calculation for
the completion transaction, reopened summaries and history detail. Its baseline
is a grouped SQL aggregate over all eligible earlier observations for the viewed
exercise IDs and stored types, in their recorded units before explicit load
conversion. It is not a global recent-200/500-session sample. The established
record types, warm-up treatment and volume semantics remain; an initial
observation is not a record. The Progress overview's separate record/trend
window remains bounded at 500 sessions and is not the authority for an older
workout's achievement.

### Immutable history detail

Completion remains `ActiveWorkoutScreen`'s completed state. The unused summary
route is removed. Progress cards and the completion summary open
`history/{sessionId}`; `history` browses completed sessions in fixed pages of 20
with older/newer controls. The route carries an encoded persisted ID, not a
serialized workout. The direct lookup is independent of the overview's recent
ten. Back returns to the caller, and completion Done retains its Progress flow.

The detail read in `OfflineWorkoutHistoryRepository` observes session, exercise,
set and recommendation-table invalidations. Under the existing local-data gate, one Room
transaction reads the session, its recommendation, bounded earlier/cited
exercise projections and its summary. Child cardinalities are checked before relation loads;
overflow and malformed known data fail explicitly rather than producing
truncated success. Previous-performance selection and summary maxima are
database queries, not unbounded in-memory history or queries per set.
No read bootstraps a profile, writes history, rebuilds a ledger cache or consumes
a deload choice.

Both ordinary and cited comparisons select only the needed exercise instances,
eligible sets and `HistoricalSessionHeader` metadata. They share SQL chronology,
measurement-shape and eligible-set predicates and the existing exercise/set
mapper. Required projection sizes are checked before sets are materialized;
unrelated exercises in a large prior workout are neither loaded nor counted
against comparison limits. A citation is keyed by source session and viewed
exercise instance, so repeated instances cannot overwrite one another. The
viewed workout stays a complete snapshot; headers never masquerade as partial
`WorkoutSession` values.

Browsing observes only the session table and selects at most 21 scalar
`WorkoutHistoryEntry` rows (ID, original name, completion time and recorded
duration), returning 20 plus an older-page sentinel. It does not load exercise,
set or provenance object graphs. The size of unrelated workouts therefore cannot
make an otherwise valid history page exceed a detail/archive batch ceiling.

The detail renders the stored session origin, times, units, duration and original
text; ordered exercise prescriptions and notes; and each set's own planned
targets, performed measurements, classification, outcome, feedback and times.
Exercise-level rep ranges and per-set rep targets remain separate facts.
Assistance is not external load, bodyweight has no invented load field, and null
applicable values stay unrecorded. Exercise IDs are the display fallback without
any catalog dependency. Current profile, template, catalog programming and policy
outputs never fill historical gaps. `USER_PREFERENCE` rest provenance can be
shown because it exists; value differences do not imply an override. Substitution
history remains unavailable until Package 10 creates those snapshots.

Prior sessions must be distinct, completed, have positive ordered session times,
and finish **strictly before the viewed start**. Equal boundary times and overlaps
are excluded. Latest completion, then latest start, then ascending session ID,
exercise order and instance ID determine ordinary comparison ties. Exact
exercise ID and persisted measurement shape must match; distance-only,
duration-only and combined shapes differ. Ordinary/cited comparison sets exclude
warm-ups, stopped/unresolved or invalid measurements and recorded outcome times
outside their session. Legacy null outcome times remain null. Performed-value
bounds follow archive compatibility, not current target ceilings. Earlier values
keep their original units; no growth percentage is fabricated.

Stored `ProgressionReasonCode`, `DeloadReasonCode` and `WorkoutRankingReasonCode`
explain the recorded decision, while validation reasons are interpreted only
under supported validator versions. Useful reasons precede an expandable
technical/accounting disclosure. Unknown versions retain their identities
without adopting today's meaning; malformed known groups produce Error with
Retry. Manual/older sessions without a recommendation report that absence.
Accepted deload is read from the session's record, not today's mutable choice.
Progression source IDs are distinct from ordinary previous performance. A source
must exist and be compatible and genuinely earlier to supply measurements;
the stored reason/axis does not contain a frozen before/after prescription or
enough inputs to rerun the historical planner.

`WeeklyDoseLedgerRepository` reconstructs a `PRIMARY_ONLY_V1` ledger from completed history
and accepted direct-primary metadata (`APPROVED` or `AI_ACCEPTED`). Missing, `DRAFT`, and
otherwise-unaccepted metadata become typed unattributed work sets rather than guessed
muscle credit.

`TrainingProgramStateProvider` composes that ledger with the adaptation state derived by
`AdaptationStatePolicy` into a `TrainingProgramState`, which rides on
`WorkoutGenerationContext` whenever reviewed capability eligibility is enabled. The provider
is the only unit in that composition performing I/O. On that same reviewed-only path,
`CapabilityEvidencePolicy` derives `CapabilityEvidenceSet` once from the already-bounded
history read; it adds no query, cache, migration, network, analytics, or logging path.
`StateBasedTrainingPolicy` reads the ledger's direct-primary counts to cap reviewed
prescriptions, and `CapabilityPreferenceRankingPolicy` reads the evidence set only to
suppress a soft capability penalty for the matching candidate. Progression and state
transitions still do not consume the ledger. On the legacy path the state is absent,
`capabilityEvidence` is empty, and the existing prescription path is returned unchanged.

The adaptation policy derives reported `RETURNING`, accepted-deload `HOLD`, otherwise
`UNCALIBRATED`. Every state keeps the advanced-complexity ceiling unless the existing
demonstrated-family or legal supported-regression exception applies; historical state
identities in a recommendation are not recomputed from today's profile.

## Lifecycle and failure handling

Feature ViewModels expose immutable `StateFlow` state to Compose. Repository and
catalog failures become actionable screen states, while coroutine cancellation
is rethrown. A failed template save keeps the draft available, and a failed
workout start cannot leave a partial session.

The active session is persisted immediately, so it can be resumed after normal
navigation or process recreation. The template editor does not yet persist an
unsaved draft or prompt before leaving with unsaved changes.

The rest timer is in-memory ViewModel state driven by an injected monotonic
elapsed-realtime clock. Remaining time is always derived from a deadline rather
than decremented, so backgrounding, a paused UI, or a missed tick cannot make it
drift, and a device clock change cannot lengthen or shorten a rest period in
progress. It survives recomposition and configuration changes, and it is
deliberately **not** restored after process death: a deadline captured against a
previous process's elapsed-realtime baseline would restore as a misleading
countdown, so the timer resets to `Idle` while the session itself is resumed
intact. This milestone adds no foreground service, notification, alarm, or Wear
behaviour.

History ViewModels collect only while RESUMED and clear replay when hidden.
Loading, loaded, missing/deleted session, unsupported status and recoverable read
error are distinct; Retry resubscribes instead of returning an empty success.
Deletion/restore invalidates the whole projection. A profile reset clears
protected destinations and saved tab back stacks, preserving the onboarding
contract even when a history screen is open.

The active logger keeps its history subscription stable while sets change, so
partial input, focus and feedback controls survive persistence emissions.
A short subscription grace preserves rotation without restoring a rest timer
after process death. Completed-state restoration only reads its summary.
Finish deliberately performs one observed summary read after its atomic
completion transaction: a delayed returned summary must not overwrite newer
history after deletion/restore.

## Dynamic Theming and Visual Contrast

WallCrawl supports dynamic theme adaptation across all features and shared
components via Jetpack Compose and Material 3:

```text
UserProfile.themePreference (SYSTEM | DARK | LIGHT)
               │
               ▼
        WallCrawlTheme
         ├─ LightColorScheme / DarkColorScheme
         ├─ WindowCompat (status & nav insets)
         └─ Dynamic Tokens (Surfaces, Borders, Typography)
               │
               ├→ WallCrawlCard / WebBackgroundPattern
               ├→ WallCrawlWordmark (Dynamic high-contrast brand)
               └→ ExerciseIllustration (Elevated container)
```

- **Theme Preferences**: `ThemePreference.SYSTEM` follows Android's system-wide
  dark mode setting via `isSystemInDarkTheme()`, while `DARK` and `LIGHT` enforce
  the respective color palette across the app.
- **System Inset Controller**: `WallCrawlTheme` updates `WindowInsetsController`
  to dynamically switch light and dark system status bar and navigation bar icon
  contrast.
- **Visual Contrast & Exercise Art**: Vector SVG illustrations use a dedicated
  dark graphite elevation backing so anatomical illustration lines remain crisp
  and visible against light and dark background themes alike. Light theme card
  and field borders use Slate 300 (`0xFFCBD5E1`) for sharp structural definition.
- **Wordmark & Typography**: Brand elements like `WallCrawlWordmark` dynamically
  render primary brand lettering in theme-aware typography tokens (`onSurface`),
  preserving high contrast on all screen densities.
- **Gym-Floor Ergonomics**: Active workout sets incorporate tactile Material 3
  `AssistChip` rest-timer controls, bottom padding clearance above sticky
  actions, and prominent constructive vs destructive confirmation dialog
  hierarchies.

## Verification boundaries

The JVM suite covers pure domain rules, filtering, context construction,
capability normalization and codec behavior, planner invariance, validation,
repository mapping, progress calculations, and ViewModel state. Its fourteen-persona
[planner evaluation corpus](planner-evaluation.md) composes the reviewed-enabled personas'
weeks with the real `WeeklyDoseLedgerCalculator` and validates every proposal it produces
with `ProgramValidator`, keeping raw-valid, repaired-valid and typed no-plan outcomes
distinct. Instrumentation
tests cover every supported Room migration chain through schema 14, real 7 → 8,
9 → 10, 10 → 11, 11 → 12 and 12 → 13 preservation, foreign-key integrity, guidance round trips,
the atomic start of a session with its validation record, the weekly-ledger repository,
capability
accessibility semantics, packaged catalog parsing, all bundled visual paths, template
snapshots, session persistence, the local-data archive (round trip from app-written
state, every rejection path, transactional restore and deletion, a profile write racing
a deletion, and the destructive confirmation at a large font scale), and the
[packaged backup-policy configuration](privacy.md#verification-boundary)
(merged manifest flags, resolved XML references, and exclusion semantics).
Pull-request/main CI and tag-release publication run that connected suite on an
API 36 emulator. The importer has a separate Python-standard-library test suite, which runs
together with the pinned-upstream catalog regeneration check on pull requests and `main` only;
a tag build does not re-run them, so a release carries their result only when the tagged
commit already passed CI on `main`, which no workflow currently enforces. The full split is
recorded in [planner evaluation](planner-evaluation.md#where-the-gate-runs).

See [Build and test](../README.md#build-and-test) for the commands contributors
should run.

### Exercise illustration variants

`UserProfile.gender` is optional and controls artwork directly: Female for WOMAN,
Male otherwise. Its selector appears only in onboarding and Profile. There is no
separate illustration override. The first preview's `illustrationPreference` field
is retained only for database/archive compatibility and cannot affect artwork.
Room migration 12 → 13 preserves old rows with conservative defaults. Archive
version 3 preserves the legacy field; versions 1 and 2 retain their checksums.

The app supplies `LocalIllustrationVariant` from the observed profile to every
`ExerciseIllustration`. `WorkoutGuideVisualProvider` reads the separate variant index
off the main thread and returns complete three-frame sequences. A missing variant or
image-load error falls back to the complete pinned original sequence. A change of
exercise or variant resets playback and cancels the old load. No frame-by-frame mixing
occurs. Current selected drafts play 1–2–3–2, including documented continuity issues.
See [illustration variants](exercise-illustration-variants.md) for packaging and review status.
