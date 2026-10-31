package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.ai.TrainingFrequencyRecencyPolicy
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.progress.AnalyticsRangeTooLargeException
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineProgressRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.ProgressAnalyticsSnapshot
import wallcrawl.elopenmike.com.core.database.repository.toEntity
import wallcrawl.elopenmike.com.core.model.AnalyticsMetric
import wallcrawl.elopenmike.com.core.model.AnalyticsRange
import wallcrawl.elopenmike.com.core.model.AnalyticsScope
import wallcrawl.elopenmike.com.core.model.AnalyticsSelection
import wallcrawl.elopenmike.com.core.model.ProgressionProvenance
import wallcrawl.elopenmike.com.core.model.ProgressionReason
import wallcrawl.elopenmike.com.core.model.ProgressionReasonCode
import wallcrawl.elopenmike.com.core.model.RecommendationRecord
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit

/** Real Room reads for range analytics: complete ranges, strict decoding, bounds and no writes. */
@RunWith(AndroidJUnit4::class)
class ProgressAnalyticsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: WallCrawlDatabase
    private val gate = Mutex()
    private val zone = ZoneId.of("UTC")
    private val monday = Instant.parse("2026-08-31T00:00:00Z")
    private val now = Instant.parse("2026-09-02T12:00:00Z")
    private val week = 7 * 24 * 3_600_000L
    private lateinit var catalog: FakeCatalogSource

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        catalog = FakeCatalogSource(syntheticSnapshot(listOf(
            syntheticCatalogExercise("approved", "Chest").copy(primaryMuscles = listOf("Chest")),
            syntheticCatalogExercise("draft", "Chest", reviewState = ReviewState.DRAFT)
        )))
        OfflineUserProfileRepository(db.userProfileDao(), gate).saveProfile(
            UserProfile(onboardingCompleted = true, preferredUnit = WeightUnit.LBS)
        )
    }

    @After fun tearDown() { db.close() }

    @Test fun anOlderSelectedRangeIsNotCrowdedOutByMoreThan500NewerSessions() = runBlocking {
        listOf(30, 40, 50).forEach { weeksAgo -> insert("old-$weeksAgo", monday.toEpochMilli() - weeksAgo * week + 1, "approved") }
        repeat(600) { insert("recent-$it", monday.toEpochMilli() + it, "draft") }

        val scoped = read(AnalyticsSelection(AnalyticsRange.WEEKS_52, AnalyticsScope.Exercise("approved")))
            .series(AnalyticsMetric.WORKOUTS)
        assertThat(scoped.rangeTotal).isEqualTo(3.0)
        assertThat(scoped.buckets.flatMap { it.contributors }.map { it.sessionId })
            .containsExactly("old-50", "old-40", "old-30").inOrder()
        val all = read(AnalyticsSelection(AnalyticsRange.WEEKS_52))
        assertThat(all.series(AnalyticsMetric.COMPLETED_SETS).rangeTotal).isEqualTo(603.0)
        assertThat(all.series(AnalyticsMetric.COMPLETED_SETS).buckets.last().contributors).hasSize(600)
        assertThat(all.exerciseOptions).containsExactly("approved", "draft").inOrder()
    }

    @Test fun rangeMembershipFollowsWeeklyAccountingIncludingClockSkewInsideTheCurrentWeek() = runBlocking {
        insert("first-day", monday.toEpochMilli() - 3 * week, "approved")
        insert("before-range", monday.toEpochMilli() - 3 * week - 1, "approved")
        insert("later-this-week", Instant.parse("2026-09-05T10:00:00Z").toEpochMilli(), "approved")
        insert("next-week", monday.toEpochMilli() + week, "approved")
        insert("cancelled", monday.toEpochMilli() + 1, "approved", status = SessionStatus.CANCELLED)

        val result = read(AnalyticsSelection()).series(AnalyticsMetric.WORKOUTS)
        assertThat(result.buckets.flatMap { it.contributors }.map { it.sessionId })
            .containsExactly("first-day", "later-this-week").inOrder()
    }

    @Test fun performanceUsesTheSharedComparisonEligibilityAndConvertsUnits() = runBlocking {
        insert("kg", monday.toEpochMilli() - week, "approved", unit = WeightUnit.KG, weight = 100.0, extraSets = listOf(
            set("warm", SetType.WARMUP, weight = 150.0),
            set("stopped", SetType.NORMAL, weight = 160.0, stop = SetStopReason.PAIN_STOP)
        ))
        insert("lb", monday.toEpochMilli(), "approved", weight = 225.0)

        val series = read(AnalyticsSelection(scope = AnalyticsScope.Exercise("approved"))).analytics.performance.single()
        assertThat(series.observations.map { it.sessionId }).containsExactly("kg", "lb").inOrder()
        assertThat(series.observations.first().value).isWithin(1e-9).of(100.0 / 0.45359237)
        assertThat(series.comparison!!.change).isWithin(1e-9).of(225.0 - 100.0 / 0.45359237)
    }

    @Test fun recordedDecisionsAreReadOnlyForRangeSessionsContainingTheExercise() = runBlocking {
        insert("decided", monday.toEpochMilli(), "approved")
        insert("other", monday.toEpochMilli() + 1, "draft")
        val codes = ProgressionReasonCode.encode(listOf(ProgressionProvenance(
            "approved", ProgressionReason.HOLD_INSUFFICIENT_HISTORY, null, listOf("missing-source"), "a".repeat(64)
        )))
        db.workoutSessionDao().insertRecommendationRecord(record("decided", codes).toEntity())
        db.workoutSessionDao().insertRecommendationRecord(record("other").toEntity())

        val snapshot = read(AnalyticsSelection(scope = AnalyticsScope.Exercise("approved")))
        assertThat(snapshot.decisions.map { it.sessionId }).containsExactly("decided")
        assertThat(snapshot.decisions.single().record.reasonCodes).isEqualTo(codes)
        assertThat(read(AnalyticsSelection()).decisions).isEmpty()
    }

    @Test fun readsWriteNothing() = runBlocking {
        insert("a", monday.toEpochMilli(), "approved")
        val before = tableContents()
        // Every metric is computed on each read, so one read per scope covers them all.
        listOf(AnalyticsScope.All, AnalyticsScope.Exercise("approved"), AnalyticsScope.Muscle("Chest")).forEach { scope ->
            read(AnalyticsSelection(AnalyticsRange.WEEKS_52, scope))
        }
        assertThat(tableContents()).isEqualTo(before)
        assertThat(tableContents().getValue("weekly_dose_ledger_state")).isEmpty()
    }

    @Test fun everyUnknownStoredTypeOrUnitFailsInsteadOfBeingRelabelled() {
        listOf(
            "UPDATE workout_sets SET exerciseType = 'LASER'",
            "UPDATE workout_sets SET type = 'MEGA'",
            "UPDATE workout_exercises SET exerciseType = 'LASER'",
            "UPDATE workout_exercises SET targetRepMax = NULL",
            "UPDATE workout_sessions SET weightUnit = 'STONE'",
            // A distance/time exercise with neither target cannot be given a measurement shape.
            "UPDATE workout_exercises SET exerciseType = 'DISTANCE_DURATION', targetRepMin = NULL, " +
                "targetRepMax = NULL, targetWeight = NULL, targetDurationSeconds = NULL, targetDistanceMeters = NULL"
        ).forEachIndexed { index, corruption ->
            runBlocking { insert("c$index", monday.toEpochMilli() + index, "approved") }
            // Session, exercise and set IDs of this fixture all start with its session ID.
            db.openHelper.writableDatabase.execSQL("$corruption WHERE id LIKE 'c$index%'")
            assertThrows(corruption, IllegalArgumentException::class.java) { runBlocking { read(AnalyticsSelection()) } }
            db.openHelper.writableDatabase.execSQL("DELETE FROM workout_sessions WHERE id = 'c$index'")
        }
    }

    @Test fun moreThanTheSetBoundInFewSessionsIsReportedNotTruncated() {
        runBlocking { insert("big", monday.toEpochMilli(), "approved") }
        // A recursive CTE writes the rows directly; the app path would take minutes for 100,001 sets.
        db.openHelper.writableDatabase.execSQL("""
            WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 100001)
            INSERT INTO workout_sets (id, workoutExerciseId, setNumber, exerciseType, targetReps, completedReps,
                targetWeight, completedWeight, isCompleted, rpe, rir, type)
            SELECT 'bulk-' || i, 'big-exercise', i, 'WEIGHT_REPS', 5, 5, 20.0, 20.0, 1, NULL, NULL, 'NORMAL' FROM n
        """)
        assertThrows(AnalyticsRangeTooLargeException::class.java) { runBlocking { read(AnalyticsSelection()) } }
    }

    @Test fun moreThanTheExerciseRowBoundIsReportedNotTruncated() {
        runBlocking { insert("wide", monday.toEpochMilli(), "approved") }
        db.openHelper.writableDatabase.execSQL("""
            WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i <
                ${TrainingFrequencyRecencyPolicy.MAX_SESSIONS * LocalDataArchiveLimits.MAX_EXERCISES_PER_SESSION + 1})
            INSERT INTO workout_exercises (id, sessionId, exerciseId, orderIndex, exerciseType, targetSets,
                targetRepMin, targetRepMax, targetWeight, notes, restSeconds)
            SELECT 'row-' || i, 'wide', 'approved', i, 'WEIGHT_REPS', 1, 5, 10, NULL, '', 90 FROM n
        """)
        assertThrows(AnalyticsRangeTooLargeException::class.java) { runBlocking { read(AnalyticsSelection()) } }
    }

    @Test fun anExerciseOptionOverflowListsNoneRatherThanAPartialListAndKeepsTheChart() {
        runBlocking { insert("many", monday.toEpochMilli(), "approved") }
        db.openHelper.writableDatabase.execSQL("""
            WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 1001)
            INSERT INTO workout_exercises (id, sessionId, exerciseId, orderIndex, exerciseType, targetSets,
                targetRepMin, targetRepMax, targetWeight, notes, restSeconds)
            SELECT 'option-' || i, 'many', 'recorded-' || i, i, 'WEIGHT_REPS', 1, 5, 10, NULL, '', 90 FROM n
        """)
        val snapshot = runBlocking { read(AnalyticsSelection()) }
        assertThat(snapshot.exerciseOptions).isNull()
        assertThat(snapshot.catalogNames).isEmpty()
        assertThat(snapshot.series(AnalyticsMetric.WORKOUTS).rangeTotal).isEqualTo(1.0)
        // A selected exercise keeps its catalog name for the chooser label.
        val selected = runBlocking { read(AnalyticsSelection(scope = AnalyticsScope.Exercise("approved"))) }
        assertThat(selected.exerciseOptions).isNull()
        assertThat(selected.catalogNames).containsKey("approved")
    }

    @Test fun aWeekBeyondTheWeeklyLedgerBoundsIsReportedAsTooLarge() {
        // 1,001 sessions in one week passes the 2,000-session range bound but not the weekly ledger's 1,000.
        runBlocking { repeat(1_001) { insert("w-$it", monday.toEpochMilli() - 2 * week + it, "approved") } }
        assertThrows(AnalyticsRangeTooLargeException::class.java) {
            runBlocking { read(AnalyticsSelection(AnalyticsRange.WEEKS_4)) }
        }
    }

    @Test fun anOversizedRangeIsReportedNotTruncated() {
        runBlocking { repeat(2_001) { insert("s-$it", monday.toEpochMilli() + it, "approved") } }
        assertThrows(AnalyticsRangeTooLargeException::class.java) {
            runBlocking { read(AnalyticsSelection(AnalyticsRange.WEEKS_4)) }
        }
    }

    @Test fun aPreferredUnitChangeReExpressesALiveRangeWithoutManufacturingChange() = runBlocking {
        insert("lb", monday.toEpochMilli(), "approved", weight = 100.0)
        val values = Channel<ProgressAnalyticsSnapshot?>(Channel.UNLIMITED)
        val collector = launch {
            repository().observeAnalytics(AnalyticsRange.WEEKS_4, AnalyticsScope.Exercise("approved"), { now }, zone)
                .collect { values.send(it) }
        }
        try {
            val first = withTimeout(10_000) { requireNotNull(values.receive()) }
            assertThat(first.series(AnalyticsMetric.EXTERNAL_LOAD_VOLUME).rangeTotal).isEqualTo(1_000.0)
            val profiles = OfflineUserProfileRepository(db.userProfileDao(), gate)
            profiles.saveProfile(profiles.getProfileOnce().copy(preferredUnit = WeightUnit.KG))
            val converted = withTimeout(10_000) {
                var value = values.receive()
                while (value?.preferredUnit != WeightUnit.KG) value = values.receive()
                requireNotNull(value)
            }
            assertThat(converted.series(AnalyticsMetric.EXTERNAL_LOAD_VOLUME).rangeTotal)
                .isWithin(1e-9).of(1_000.0 * 0.45359237)
            assertThat(converted.analytics.performance.single().observations.single().value)
                .isWithin(1e-9).of(100.0 * 0.45359237)
        } finally {
            collector.cancel()
            values.close()
        }
    }

    @Test fun deletedProfileReadsAsNoProfileAndALiveObserverRefreshes() = runBlocking {
        val values = Channel<ProgressAnalyticsSnapshot?>(Channel.UNLIMITED)
        val collector = launch {
            repository().observeAnalytics(AnalyticsRange.WEEKS_4, AnalyticsScope.All, { now }, zone)
                .collect { values.send(it) }
        }
        try {
            withTimeout(10_000) { assertThat(values.receive()?.series(AnalyticsMetric.WORKOUTS)?.rangeTotal).isEqualTo(0.0) }
            insert("new", monday.toEpochMilli(), "approved")
            withTimeout(10_000) {
                var value = values.receive()
                while (value?.series(AnalyticsMetric.WORKOUTS)?.rangeTotal != 1.0) value = values.receive()
            }
            backup().deleteAllLocalData()
            withTimeout(10_000) {
                var value = values.receive()
                while (value != null) value = values.receive()
            }
        } finally {
            collector.cancel()
            values.close()
        }
    }

    private suspend fun read(selection: AnalyticsSelection) =
        requireNotNull(repository().observeAnalytics(selection.range, selection.scope, { now }, zone).first())

    private fun ProgressAnalyticsSnapshot.series(metric: AnalyticsMetric) = analytics.series.getValue(metric)

    private fun repository() = OfflineProgressRepository(
        db, catalog, OfflineWeeklyDoseLedgerRepository(db.completedWorkoutHistoryDao(), db.weeklyDoseLedgerStateDao(), catalog), gate
    )

    private fun backup() = OfflineLocalDataBackupRepository(
        db.localDataBackupDao(), "test", 1, { SYNTHETIC_CATALOG_COMMIT }, gate
    )

    /** Every row of every business table, so any write (not only an insert) is detected. */
    private fun tableContents(): Map<String, List<List<String?>>> = listOf(
        "user_profiles", "workout_sessions", "workout_exercises", "workout_sets",
        "workout_recommendation_records", "weekly_dose_ledger_state", "program_enrollments", "deload_preferences"
    ).associateWith { table ->
        db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY rowid").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) })
            }
        }
    }

    private fun record(id: String, reasons: List<String> = emptyList()) = RecommendationRecord(
        sessionId = id, validatorVersion = "WHOLE_PROGRAM_V2", durationEstimatorVersion = "DURATION_ESTIMATOR_V1",
        outcome = "VALID", reviewedPathEnabled = true, catalogVersion = null, reviewPolicyVersion = 1,
        trainingPolicyVersion = null, ledgerPolicyVersion = null, programStatePolicyVersion = null,
        adaptationState = null, weekStartEpochDay = null, timeZoneId = null, profileRevision = 0,
        contextIdentity = "recorded-context", reasonCodes = reasons, doseAccounting = emptyList(), recordedAtEpochMillis = 300
    )

    private fun set(id: String, type: SetType, weight: Double, stop: SetStopReason? = null) = WorkoutSetEntity(
        id = id, workoutExerciseId = "", setNumber = 0, targetReps = 5, completedReps = 5, targetWeight = weight,
        completedWeight = weight, isCompleted = stop == null, rpe = null, rir = null, type = type,
        stopReason = stop, stoppedAtTimestamp = stop?.let { 1L }
    )

    private suspend fun insert(
        id: String,
        timestamp: Long,
        exerciseId: String,
        status: SessionStatus = SessionStatus.COMPLETED,
        unit: WeightUnit = WeightUnit.LBS,
        weight: Double = 20.0,
        extraSets: List<WorkoutSetEntity> = emptyList()
    ) {
        db.workoutSessionDao().insertWorkout(
            WorkoutSessionEntity(
                id = id, name = "Session $id", startedAtTimestamp = timestamp - 1,
                completedAtTimestamp = timestamp, targetDurationMinutes = 30,
                actualDurationMinutes = 30, weightUnit = unit, status = status,
                focusMusclesJson = "", notes = ""
            ),
            listOf(WorkoutExerciseEntity(
                id = "$id-exercise", sessionId = id, exerciseId = exerciseId, orderIndex = 0,
                targetSets = 1 + extraSets.size, targetRepMin = 5, targetRepMax = 10, targetWeight = weight, notes = ""
            )),
            listOf(WorkoutSetEntity(
                id = "$id-0", workoutExerciseId = "$id-exercise", setNumber = 1,
                targetReps = 10, completedReps = 10, targetWeight = weight,
                completedWeight = weight, isCompleted = true, rpe = null, rir = null, type = SetType.NORMAL,
                completedAtTimestamp = timestamp
            )) + extraSets.mapIndexed { index, set ->
                set.copy(id = "$id-${set.id}", workoutExerciseId = "$id-exercise", setNumber = index + 2)
            }
        )
    }
}

/** The recorded change between the compared observations, as tests read it. */
private val wallcrawl.elopenmike.com.core.model.PerformanceComparison.change: Double
    get() = latest.value - earliest.value
