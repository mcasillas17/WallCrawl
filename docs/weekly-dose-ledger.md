# Weekly dose ledger

WallCrawl reconstructs how much resistance training a week actually contained from
completed workout history. The reconstruction is deterministic, versioned, and derived:
nothing increments a stored counter while a workout is generated or a set is logged.

**Nothing consumes the ledger yet.** Planner selection, weekly dose targets, progression,
deloads, and substitutions are unchanged by this milestone. The ledger is built and stored
so a later milestone can compose it with eligibility and program state.

## `PRIMARY_ONLY_V1`

The policy is named and versioned as `LedgerPolicyVersion.PRIMARY_ONLY_V1`. It credits
exactly one muscle per completed work set:

1. One completed work set credits exactly one approved `directPrimaryMuscle`.
2. Descriptive secondary muscles receive **no dose credit**. They accumulate separately in
   `secondaryInvolvement` for analytics and are never merged into `directPrimarySets`.
3. Fractional secondary crediting is not implemented. It would be a different policy under
   a different version, after reviewed secondary-muscle mapping exists.
4. Body weight, height, BMI, age, RPE, RIR, the "felt manageable" confirmation, readiness,
   and any Health data change nothing about set credit.

## What counts as a completed work set

A set is one completed work set when it is marked completed **and** its type is not
`WARMUP`:

| Set type | Completed | Credit |
| --- | --- | --- |
| `NORMAL`, `DROPSET`, `MYOREP`, `FAILURE` | yes | one work set |
| `WARMUP` | yes | none — warm-ups are preparation, not exposure |
| any type | no | none |

`FAILURE` is historical data about how a set went. It is never an automatic target and
carries no extra weight in the ledger. The classification is an exhaustive `when` over
`SetType`, so adding a set type later fails compilation until it is deliberately credited
or excluded.

Work that was never finished earns nothing: incomplete sets, sets stopped with a typed
`SetStopReason` (including `USER_SKIPPED` and `PAIN_STOP`), cancelled sessions, and
in-progress sessions all contribute zero. Planned target sets and prescriptions are not
exposure and are never counted as such.

## Missing, unknown, and `DRAFT` metadata

A set is credited only when all of the following hold:

- the exercise id resolves **exactly** in the current bundled catalog;
- that exercise carries a `reviewedMetadata` block;
- its `reviewState` is `APPROVED`;
- its `directPrimaryMuscle` is present in the parsed reviewed contract.

Otherwise the work set is counted in `unattributedWorkSets` under a typed reason:

| Reason | Meaning |
| --- | --- |
| `UNKNOWN_EXERCISE` | the id is not in the current bundled catalog |
| `MISSING_REVIEWED_METADATA` | the exercise exists but has no reviewed block |
| `METADATA_NOT_APPROVED` | reviewed metadata exists but is not `APPROVED` |

Nothing is guessed. There is no fallback to legacy `primaryMuscles`, to exercise names, to
the legacy `programming` block, or to an inferred movement pattern. The attribution branch
is a sealed `LedgerAttribution` with exactly two outcomes — credited, or omitted with a
reason — so there is no third path that could invent a muscle.

The bundled catalog currently ships 302 exercises with 37 reviewed entries, **all `DRAFT`
and none `APPROVED`**. Today the ledger therefore credits nothing from real history and
reports every completed work set as `METADATA_NOT_APPROVED` or
`MISSING_REVIEWED_METADATA`. `BundledCatalogLedgerAttributionTest` fails the build if that
changes without deliberate human approval. Tests that need approved metadata build their
own clearly labelled synthetic entries; those fixtures live only in test sources and are
never shipped.

## Week boundary and time zone

A week is one ISO week in a specific zone: Monday at local midnight through the following
Monday at local midnight, exclusive.

- Bounds come from `ZonedDateTime`, never from adding a fixed 168 hours, so a week
  containing a daylight-saving transition spans the 167 or 169 hours that actually
  elapsed.
- `LocalDate.atStartOfDay(zone)` is used deliberately: on a day whose local midnight does
  not exist because the clocks jumped forward, it resolves to the first valid local
  instant of that day.
- Membership uses the session's `completedAtTimestamp` against `[weekStart, nextWeekStart)`
  in epoch milliseconds. The exact start instant is included; the next week's start is
  excluded.
- The ledger records both `weekStartEpochDay` and the exact zone id.
- The zone is part of a ledger's identity. Reading the same calendar week in another zone
  produces a separately reconstructed ledger and its own cache row; an existing snapshot is
  never relabelled.

The current instant comes from an injected `java.time.Clock`, so tests and future callers
control it rather than reading the wall clock implicitly.

## Reconstruction and cache invalidation

```text
local completed history + bundled approved metadata
  -> weekly range query (whole week, no result limit)
  -> pure WeeklyDoseLedgerCalculator
  -> local reconstructable cache (weekly_dose_ledger_state)
  -> local consumers
```

`WeeklyDoseLedgerCalculator` is pure: no I/O, no clock, no state. It rejects malformed
input loudly and specifically — a session that is not `COMPLETED`, a completion timestamp
outside the requested week, duplicate session, exercise-instance, or set ids, a blank
catalog version, or a negative review-policy version — rather than dropping it and
returning a success-shaped short week. A genuinely empty week returns an explicit empty
ledger. A database or catalog failure propagates; it never becomes an empty week.

`weekly_dose_ledger_state` (added by migration 9 → 10) is a cache, not an authority. It is
keyed by `(profileId, weekStartEpochDay, timeZoneId, policyVersion)` and stores the policy,
catalog, and review-policy versions, the deterministic ledger payload, a source fingerprint,
and a generation timestamp used only for diagnostics.

A cached row is served only when its fingerprint still matches the fingerprint of the
current inputs. `LedgerSourceFingerprint` is a SHA-256 digest, computed with the JDK's
`MessageDigest`, over exactly the inputs that can change credit:

- the included completed session ids and their completion timestamps;
- exercise-instance ids and the catalog exercise ids they reference;
- completed work-set ids, types, and completion state;
- for every referenced exercise: whether it resolves, its review state, and — when
  approved — its direct primary, its descriptive secondaries, and its provenance policy
  version;
- the policy version, catalog version, review-policy version, week start, and zone id.

Everything is canonically ordered before hashing, so the same history read back in a
different order can never look like different history. Newly completed work, an approved
entry, a new catalog, a new review policy, a different week, and a different zone all
invalidate the cache and force a recomputation.

Deleting or corrupting the cache cannot change a result. A row that does not decode exactly
reads back as "no usable cache" and the ledger is recomputed from history; a tampered
fingerprint simply fails to match. The cache is replaced wholesale by a freshly computed
ledger and is never incremented, so two concurrent readers of the same week compute the
same ledger and either write leaves the row in the same state.

Migration 9 → 10 is additive. It creates one empty table and reads, rewrites, or drops
nothing: every profile, capability, template, workout, exercise, set, and typed set outcome
keeps the value it already had, and `PRAGMA foreign_key_check` stays clean from every
historically supported schema version.

## Privacy boundary

Everything here is local. No analytics event, network call, cloud sync, model prompt, Wear
payload, or Health Connect permission is involved.

The cache and the fingerprint deliberately exclude everything the policy cannot read:
no notes, no free text, no session or exercise names, no RPE or RIR, no "felt manageable"
answer, no loads, repetitions, durations, or distances, no capability answers, and no
profile or body values. The fingerprint is a cache-validity check, not a security control.

Validation messages name the offending field, category, or identifier — never a value the
user typed. Nothing is logged.

## Test commands

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew testDebugUnitTest --tests '*WeeklyDoseLedger*' \
                            --tests '*TrainingWeekTest*' \
                            --tests '*LedgerSourceFingerprintTest*' \
                            --tests '*BundledCatalogLedgerAttributionTest*' --no-daemon

JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=\
wallcrawl.elopenmike.com.core.database.Migration9To10Test,\
wallcrawl.elopenmike.com.core.database.MigrationChainTo10Test,\
wallcrawl.elopenmike.com.core.database.CompletedWorkoutHistoryDaoTest,\
wallcrawl.elopenmike.com.core.database.WeeklyDoseLedgerRepositoryTest
```

The full suites are the usual project commands:

```bash
python3 -m unittest discover -s tools/workout-guide -p 'test_*.py' -v
./gradlew testDebugUnitTest --rerun-tasks --no-daemon
./gradlew lintDebug assembleDebug --stacktrace --no-daemon
./gradlew connectedDebugAndroidTest --no-daemon
git diff --check
```

## Not in this milestone

- Capability eligibility, `ExerciseFilter` changes, and reviewed-only gating.
- Metadata approval of any kind.
- Adaptation-state transitions and state-based dose targets.
- Progression, deloads, substitutions, and program blocks.
- Any change to planner selection or prescription.
- Fractional secondary-muscle credit.
- Body measurements, BMI, Health Connect, Wear OS, LLM, analytics, or networking.
