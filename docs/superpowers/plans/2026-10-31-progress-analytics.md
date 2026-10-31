# Progress Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Execution record (2026-10-31):** executed in full. This plan is preserved as written; the
> shipped design is the [spec](../specs/2026-10-31-progress-analytics-design.md), ROADMAP Package 14
> and the code. Review rounds changed the shape: the range read reuses the bounded relation query
> after a size/strict-type pre-check (2,000 sessions, 200,000 exercise rows, 100,000 sets) instead of a
> flat projection; every measure is computed in one pass and the read is keyed on range and scope;
> names are `selectAnalyticsRange/Scope/Metric`, `RecordedSessionDecision`, `AnalyticsBarChart` and
> `ProgressAnalyticsRepositoryTest`; app navigation coverage lives in `WorkoutHistoryNavigationTest`.
> Unchecked boxes are not status.

**Goal:** Add a filterable, range-scoped "Over time" Progress card whose every number traces to completed sessions.

**Architecture:** One flat Room projection per read feeds a pure `ProgressAnalyticsCalculator`; the ViewModel keeps a bounded saved selection and a separate analytics state; a small Compose bar chart plus an accessible data list drives drill-down into existing history routes.

**Tech Stack:** Kotlin, Room, Coroutines/Flow, Jetpack Compose, JUnit4 + Truth, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-10-31-progress-analytics-design.md`

## Global Constraints

- No Room schema or archive change (Room 16, archive 6 reading 1–6 stay).
- No new Gradle dependency.
- Ranges: 4, 12, 26, 52 ISO weeks; default 4. Scope: all / exercise ID / trainable muscle; default all. Metric default completed sets.
- Range read bounds: 2,000 sessions, 100,000 sets; probe one extra row; overflow fails explicitly.
- Saved state: four strings; scope ID ≤ 200 characters.
- Strings in `values/` and `values-es/` with parity.
- No writes from any analytics read.

## Review Focus

- A selected exercise with data only beyond the latest 500 sessions still appears (repository test with 600 unrelated newer sessions).
- Switching preferred unit converts every load; values never relabel (calculator test lb/kg mix).
- Fast scope changes: an older slow read never replaces a newer one (ViewModel test with a gated fake).
- Unknown stored exercise/set type text fails the read instead of disappearing (repository test with raw SQL insert).
- Partial current week never compared with a full one (calculator asserts no change field; UI test sees "so far").

---

### Task 1: Pure analytics calculator

**Files:**
- Modify: `core/model/ProgressAnalytics.kt` (append models)
- Modify: `core/progress/ProgressCalculator.kt` (expose weekly reps/volume/eligibility helpers as `internal` top-level functions; behaviour unchanged)
- Create: `core/progress/ProgressAnalyticsCalculator.kt`
- Test: `app/src/test/.../core/progress/ProgressAnalyticsCalculatorTest.kt`

**Interfaces — Produces:**
- `enum class AnalyticsRange(val weeks: Int) { WEEKS_4(4), WEEKS_12(12), WEEKS_26(26), WEEKS_52(52) }`
- `sealed interface AnalyticsScope { All; Exercise(exerciseId); Muscle(muscle) }`
- `enum class AnalyticsMetric { WORKOUTS, COMPLETED_SETS, REPS, EXTERNAL_LOAD_VOLUME, REVIEWED_PRIMARY_DOSE }`
- `data class AnalyticsSelection(range, scope, metric)`
- `data class AnalyticsSetRow(...)` / `AnalyticsSessionRow` as the projection input, with a `comparable: Boolean` flag.
- `ProgressAnalyticsCalculator.calculate(sessions: List<WorkoutSession>, comparableSetIds: Set<String>, selection, catalog: List<Exercise>, catalogVersion: String, reviewPolicyVersion: Int, preferredUnit: WeightUnit, nowEpochMillis: Long, zone: ZoneId): ProgressAnalytics`
- `ProgressAnalytics(range weeks, buckets: List<AnalyticsBucket>, total, fullWeeks, performance: List<PerformanceSeries>, unclassifiableSets, unattributed: Map<LedgerOmissionReason, Int>, hasAnyWorkout)`
- `AnalyticsBucket(week: TrainingWeek, partial: Boolean, value: Double, contributors: List<AnalyticsContributor>)`
- `AnalyticsContributor(sessionId, name, completedAt, value)`
- `PerformanceSeries(shape: PerformanceShape, unit: PerformanceUnit, charted: Boolean, observations: List<PerformanceObservation>)`, `PerformanceObservation(sessionId, exerciseInstanceIds, completedAt, value, reps?, secondary?)`

- [ ] Step 1: Tests — bucket construction (4/52 buckets, DST week 167 h, zone change, partial flag), each metric formula per scope with warm-up, stopped, open, timed, distance, assisted, mixed lb/kg (volume converted before sum), unknown exercise in muscle scope → unclassifiable, zero-set Finish counts as workout only in All scope, future-dated completion in current week counted and after week excluded, reviewed dose equals `WeeklyDoseLedgerCalculator` output, performance: duplicate instances → one observation, warm-up/stopped excluded via comparable flag, one observation → no comparison, assisted/combined not charted, lb/kg points converted.
- [ ] Step 2: Run `./gradlew testDebugUnitTest --tests '*ProgressAnalyticsCalculatorTest*'` → FAIL.
- [ ] Step 3: Implement.
- [ ] Step 4: Re-run and existing `*ProgressCalculatorTest*` → PASS.

### Task 2: Room projection and repository

**Files:**
- Modify: `core/database/dao/WorkoutHistoryDao.kt` (projection query reusing `ELIGIBLE_PREVIOUS_SET`; distinct exercise IDs; recommendation rows for sessions)
- Modify: `core/database/repository/ProgressRepository.kt` (`observeAnalytics(selection, now, zone): Flow<ProgressAnalyticsSnapshot?>`)
- Test: `app/src/androidTest/.../core/database/ProgressRepositoryTest.kt`

**Interfaces — Produces:** `ProgressAnalyticsSnapshot(selection, analytics: ProgressAnalytics, preferredUnit, exerciseOptions: List<String>, catalogNames: Map<String,String>, decisions: List<RecommendationRecord>)`; `AnalyticsRangeTooLarge` exception type.

- [ ] Step 1: Tests — 600 newer unrelated sessions plus selected older sessions all counted; no-profile → null; unknown type text → error; overflow → `AnalyticsRangeTooLarge`; read writes nothing (table row counts and ledger cache unchanged); delete invalidates.
- [ ] Step 2–4: implement, run connected class, pass.

### Task 3: ViewModel

**Files:** `feature/progress/ProgressViewModel.kt`, `ProgressUiState.kt`; Test `ProgressViewModelTest.kt`.

**Produces:** `analyticsState: StateFlow<ProgressAnalyticsUiState>` (Loading, NoProfile, Loaded(snapshot, decisions decoded), TooLarge, Error); `selectRange`, `selectScope`, `selectMetric`, `resetAnalytics`, `retryAnalytics`; factory with `createSavedStateHandle()`.

- [ ] Tests: default/restore/invalid restore, scope ID >200 rejected, stale read cancelled, retry, error not zero.
- [ ] Implement, pass.

### Task 4: UI and strings

**Files:** Create `feature/progress/ProgressAnalyticsCard.kt`, `core/ui/components/WeeklyBarChart.kt`; modify `ProgressScreen.kt`, `values/strings.xml`, `values-es/strings.xml`; Test `ProgressScreenTest.kt`.

- [ ] Tests: chips selected semantics, partial "so far" text, rows open contributors, observation opens session, decisions open recording session, distinct empty/insufficient/no-match/too-large/error states, Spanish 1.8×.
- [ ] Implement, pass; parity test passes.

### Task 5: App wiring and navigation

**Files:** `app/WallCrawlApp.kt`; Test `app/src/androidTest/.../app/ProgressAnalyticsNavigationTest.kt` (real Room, real NavHost).

- [ ] Test: select exercise → tap observation → history detail of that session; tap week → contributor → detail; Back restores selection.

### Task 6: Documentation and screenshots

ROADMAP Package 14 + status table, `docs/weekly-dose-ledger.md` contract rows, `docs/architecture.md`, `docs/localization.md`, `docs/privacy.md`, `docs/planner-evaluation.md` (unchanged-planner note), README screenshots from the emulator.
