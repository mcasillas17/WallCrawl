# Package 14: progress analytics over time

## Authority and scope

Written against `origin/main` at `ead925f` (#82) and rebased onto `d790077` (#97,
tooling only) on 2026-10-31. ROADMAP.md remains
the status authority. The persistence contract is Room 16 and archive 6 (reads 1–6);
this package changes neither. Design choices below are agent-selected under the
task's autonomous execution mode. They are not human product approval and confer
no exercise-metadata approval.

The question this answers: *am I progressing over time, and which recorded workouts
explain the numbers?* It is a read-only presentation of completed history. It does
not change prescriptions, program advancement, progression or deload policy,
history, the ledger cache, or catalog acceptance. Everything runs locally; no
model, network, telemetry or account is involved.

## Placement

The existing calendar-week Progress cards (logged activity, reviewed primary dose,
legacy involvement, short strength comparisons, recent history, program content)
are unchanged and keep describing **this calendar week**. A new **Over time** card
sits after the strength comparisons and before the recent-history list and its
all-workouts link; program content stays first. Its filters affect only that card,
which says so in its own subtitle. Nothing on the weekly cards changes scope.

## Selection

| Control | Values | Default |
| --- | --- | --- |
| Range | Last 4, 12, 26 or 52 calendar weeks | 4 |
| Scope | All exercises, one exercise (catalog/persisted exercise ID), or one muscle (`StandardMuscles.TRAINABLE` name) | All |
| Metric | Workouts, completed sets, repetitions, external-load volume, reviewed primary dose | Completed sets |

Scope is a single choice, not a combination: an exercise already implies its
muscles, and combining both would create an intersection with two classifications
at once. Selections are canonical IDs/enum names; translated names are only labels.
**Reset** restores the defaults. Exercise options are the distinct exercise IDs
found in completed history (bounded); muscle options are the trainable canonical
muscles. A selected exercise that has no data in the chosen range is a *no matches*
state, not an error, and stays selected.

## Range and buckets

A range of *N* weeks is `[start of the ISO week N−1 weeks before the current one,
end of the current week)`, built from `TrainingWeek.startingOn` in the device's
explicit zone, so DST weeks are 167/169 h and a zone change rebuilds the buckets.
Each bucket is exactly one `TrainingWeek`; at most 52 buckets exist, so the chart
payload is bounded without point reduction. No bucket is merged, dropped or
resampled.

Membership follows the weekly accounting rule: a `COMPLETED` session whose
`completedAtTimestamp` falls in a bucket's `[start, end)`. A completion later in the
current week than "now" (clock skew) counts in the current week exactly as the
weekly overview counts it; anything after the current week's end is outside every
range. The bounded records/trend surface keeps its own `completedAt <= now` rule.

The current week is **partial**. Its bucket is flagged and labelled "so far" in
text (not by colour alone). The card shows the range total ("including this week
so far") and the number of full weeks; it never presents a week-over-week change
or compares the partial week with a full one. Program weeks are completion
positions and play no part in bucketing.

## Weekly metrics

All metrics come from completed sets (`isCompleted`) in sessions in the bucket.
Formulas reuse the Progress metric contract; scope narrows the sets considered.

| Metric | Per-bucket value | Scope behaviour |
| --- | --- | --- |
| Workouts | Distinct sessions. All scope: every completed session, including zero-set Finishes. Exercise/muscle scope: sessions with ≥1 completed in-scope set | — |
| Completed sets | Count of completed sets, warm-ups and timed work included | In-scope sets |
| Repetitions | Sum of positive `completedReps` on completed rep-based sets | In-scope sets; duration/distance never become reps |
| External-load volume | Σ finite non-negative load × positive reps over completed `WEIGHT_REPS` sets, each session's load converted to the preferred unit before summing, unrounded until display | In-scope sets; body mass, assistance, duration and distance are excluded |
| Reviewed primary dose | `PRIMARY_ONLY_V1` credited work sets from `WeeklyDoseLedgerCalculator` run on the bucket's sessions | All: `creditedWorkSets` (every workout listed). Muscle: `directPrimarySets[muscle]`. Exercise: credited sets after restricting sessions to that exercise ID. In muscle and exercise scope only credited sessions contribute; uncreditable work is an omission, not a zero |

Scope classification:

- **Exercise:** exact persisted exercise ID; every instance of it in a session.
- **Muscle (activity metrics):** the *current* bundled catalog's legacy
  `primaryMuscles` through `MuscleVocabulary`, the same descriptive, overlapping
  rule as the weekly involvement card. A set of an exercise with two primaries counts
  for both muscles. History never snapshotted muscle classifications; the card
  says the classification is today's catalog. Sets of exercise IDs the current
  catalog does not know are counted and reported as *not classifiable*, not erased.
- **Muscle (reviewed dose):** the accepted `directPrimaryMuscle` via the existing
  ledger, which may be a legacy *secondary* muscle. A session contributes exactly
  when the ledger credits that muscle, independently of the legacy-primary rule
  above. It is also today's reviewed data, not a snapshot, and the card says so.
  Work sets without accepted metadata are reported range-wide by typed omission
  reason, because they cannot be assigned to any muscle.

Reps and load volume count only work they apply to: a session contributes reps only
with a completed rep-based in-scope set, and load volume only with a completed
weighted set that has a valid recorded load. Timed, distance or body-mass work is never a recorded zero of either.

No new classifier, dose policy or record definition is introduced.

## Exercise performance

Shown only in exercise scope. One **observation** is one session × one persisted
measurement shape: `WEIGHT_REPS`, `BODYWEIGHT_REPS`, `ASSISTED_BODYWEIGHT`,
`DURATION`, and `DISTANCE_DURATION` split into distance-only, time-only and combined
by the exercise's stored target dimensions (the existing history comparison rule).
Eligible sets are exactly the SQL `ELIGIBLE_PREVIOUS_SET` predicate used by history
comparisons: completed, non-warm-up, unstopped, type-matching, within archived value
bounds, and with any set timestamp inside its session. Repeated instances of the
exercise in one session pool their eligible sets into the same observation, so
duplicates and multiple sets never count as several sessions.

| Shape | Charted value | Notes |
| --- | --- | --- |
| Weighted reps | Heaviest eligible load, converted to the preferred unit, with the reps of that set | Not an estimated 1RM or "strength" |
| Bodyweight reps | Most reps in one eligible set | Body mass is never load |
| Distance only | Longest distance, metres | — |
| Time only / duration | Longest duration, seconds | — |
| Assisted bodyweight | Not charted. Listed with reps and assistance | Reps at different assistance are not comparable; less assistance is not claimed to be better |
| Distance + time | Not charted. Listed with both values | No invented pace |

A comparison needs **two observations from distinct sessions** in the range. It
reports the earliest and latest observed values and their signed difference in
the shape's own unit, rounded to the displayed precision before its sign is chosen,
so a unit conversion that rounds to no change shows no sign. No percentage,
regression line, projection, score or readiness claim. One observation is *insufficient comparable observations*.
Missing values are not zero; a session with no eligible set for a shape produces
no observation for it. Loads convert through `convertWeight`; stored values and
units are never edited, so changing the preferred unit re-expresses every point
and cannot create a change by itself.

## Recorded decisions

In exercise scope the card lists recorded `ONE_VARIABLE_PROGRESSION_V1` decisions
for that exercise from recommendation records of sessions in the range, decoded by
the existing `decodeHistoryReasons` path: reason, axis when advanced, and the number
of cited source sessions. Each row opens **the session that recorded it**; that
history detail already separates cited sources from ordinary previous performance
and shows missing sources as unavailable. Decisions are not re-evaluated, no
before/after prescription is synthesised, and a cited source is never treated as
"the previous workout". Accepted one-workout deloads recorded by those sessions are
listed the same way. A decision row with identities this build cannot interpret is
marked unsupported and opens the workout, whose history detail shows them; a known
malformed group fails the read. Decisions stay listed even when the selected measure
has no matches.

## Drill-down

- A performance observation is one session: it opens `history/{sessionId}` and
  carries the contributing exercise-instance IDs.
- A weekly bucket aggregates several sessions: selecting it shows its contributor
  list (name as recorded, completion date, that session's contribution in the
  selected metric), each opening its own history detail. The bucket never sends the
  user to one arbitrary workout.
- A deleted target shows history detail's existing *missing* state.

## Reads, bounds and lifecycle

`OfflineProgressRepository.observeAnalytics` follows the existing Progress read:
invalidations of profile, session, exercise, set and recommendation tables; the
shared local-data write gate; one Room transaction; profile read without bootstrap
(null → *no profile*). Inside it:

1. One aggregate query counts the range's sessions, exercise rows and sets and
   checks every stored session unit, exercise and set exercise type, set type
   (warm-up, normal, drop, myo, failure), rep-range pair and distance/time target
   presence as text; stop reasons are decoded by the strict Room converter. It runs before any graph is loaded, because Room's
   converters would otherwise relabel an unknown value; an invalid value fails the
   read.
2. The existing bounded range query (`getCompletedSessionsInRange`, the weekly
   ledger's own read) loads that range's sessions with their exercises and sets in
   Room's batched relation queries — never a query per session, point or exercise.
   Zero-set sessions are included.
3. In exercise scope only: the IDs of that exercise's sets in the range that satisfy
   the shared `ELIGIBLE_PREVIOUS_SET` predicate, and the recommendation records of
   range sessions containing the exercise.
4. The distinct recorded exercise IDs for the filter options, at most 1,000; beyond that the
   chooser says there are too many to list instead of showing a partial list, and the chart is
   unaffected.

The range is bounded by 2,000 sessions, 200,000 exercise rows (2,000 × the archive's
100 per session) and 100,000 sets, the scheduling range's representational bounds.
Each week is also held to the existing weekly ledger's own bounds (1,000 sessions and 50,000
creditable work sets). Any overflow is an explicit *range too large* state, never a truncated
chart or a retryable read error. A distance/time
exercise row with neither target is invalid stored data and fails the read. The pure
calculation runs after the gate and transaction are released, so writers wait only
for the reads. These are software
limits, not training limits. Unrelated recent sessions cannot crowd a selected
range because there is no global latest-N sample. The ledger is computed with the
pure calculator; analytics never writes its cache or any other row.

The ViewModel keeps the selection in `SavedStateHandle` as four short strings
(range, scope kind, scope ID capped at the archive's 200-character ID bound, metric), validated on restore;
no history or chart data is saved. Range and scope (not the metric), clock/zone
refresh and the existing week-boundary wakeup feed a `flatMapLatest`, so a slow
previous read is cancelled and cannot overwrite a newer selection. Every metric is
computed in one pass, so changing the metric selects another series without a read. The overview state is separate, so changing
a filter does not reload the weekly cards. The card's Retry has its own trigger, so it
re-reads only the card; resume and clock/zone changes refresh both. Recorded decisions
are decoded off the main thread. Collection is RESUMED-only with
`WhileSubscribed(0, 0)`; there is no polling.

## States and accessibility

Loading, no profile, no workouts in range, no matches for the filter, an empty week,
insufficient comparable observations, range too large, and read error with Retry are
distinct. Classification and omission notes appear before "no matches" so they can
explain it. Each of the five measures has its own no-matches copy. Until a read has supplied
options, the exercise chooser repeats the card's own state: loading, range too large,
no profile, or read error. Option labels use the shared locale collation.
Errors never render as zero.

The chart is a small Compose canvas bar chart with no new dependency. It draws at most
52 bars: every weekly series fits, and a performance series with more observations is
listed without a chart rather than resampled. It has a
text summary as its content description and is decorative beyond that; the
accessible interface is the data list beside it. Each week row is a ≥48 dp toggle
button with a full spoken label (date range, value with unit or "nothing recorded",
partial marker) that reveals its contributors. Contributor, observation and decision
rows are ≥48 dp buttons that open that workout's history detail. Contributor,
observation and decision lists show 20 rows at a time with a "Show more (N not shown
yet)" control, so nothing is hidden silently and a long range is not composed at once. The partial
week uses a hatched bar plus the text marker. Selection chips expose selected
state. English and neutral Latin American Spanish strings use existing plural,
number, date and unit formatting; historical names stay as recorded. Layout wraps
at 320 dp and 1.8× font scale in light and dark themes.

## Verification

TDD: pure calculator cases for every formula, scope, shape, unit, boundary, DST,
zone, partial week, future-dated completion, duplicate instances, warm-up/stopped
sets, zero-baseline and one-observation; ViewModel saved state, cancellation,
retry and refresh; real Room repository coverage with more than 500 unrelated
sessions, no-write reads, deletion/restore, bounds and malformed rows; Compose
semantics/state tests; app navigation from an observation and a contributor into
history detail. Actual emulator screenshots document the card.
