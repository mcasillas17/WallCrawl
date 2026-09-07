package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineProgressRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.ProgressSnapshot
import wallcrawl.elopenmike.com.core.database.repository.WeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WeightUnit

@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: WallCrawlDatabase
    private val gate = Mutex()
    private val zone = ZoneId.of("UTC")
    private val monday = Instant.parse("2026-08-31T00:00:00Z")
    private val now = Instant.parse("2026-09-06T20:00:00Z")
    private val approved = syntheticCatalogExercise("approved", "Chest", setOf("Triceps"))
        .copy(primaryMuscles = listOf("Chest", "Shoulders"))
    private lateinit var catalog: FakeCatalogSource

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        catalog = FakeCatalogSource(syntheticSnapshot(listOf(
            approved,
            syntheticCatalogExercise("draft", "Chest", reviewState = ReviewState.DRAFT),
            syntheticCatalogExercise("missing", "Chest", reviewState = null)
        )))
        OfflineUserProfileRepository(db.userProfileDao(), gate).saveProfile(
            UserProfile(onboardingCompleted = true, preferredUnit = WeightUnit.KG)
        )
    }

    @After fun tearDown() { db.close() }

    @Test fun currentAndPreviousWeeksAreNotTruncatedToTheRecent500() = runBlocking {
        repeat(501) { index -> insert("current-$index", monday.toEpochMilli() + index, "approved") }
        insert("previous", monday.toEpochMilli() - 1, "approved")
        val snapshot = requireNotNull(repository().observeProgress({ now }, zone).first())
        assertThat(snapshot.overview.workoutsThisWeek).isEqualTo(501)
        assertThat(snapshot.overview.totalWorkoutsLogged).isEqualTo(502)
        assertThat(snapshot.overview.recentHistory).hasSize(10)
        assertThat(snapshot.overview.currentStreakWeeks).isEqualTo(2)
        assertThat(snapshot.reviewedDose.creditedWorkSets).isEqualTo(501)
        assertThat(snapshot.previousReviewedDose.creditedWorkSets).isEqualTo(1)
        assertThat(snapshot.overview.legacyPrimaryActivity.single { it.muscle == "Chest" }.setsPreviousWeek)
            .isEqualTo(1)
    }

    @Test fun activityAndLedgerReconcileWithoutAddingSecondaryInvolvement() = runBlocking {
        insert("approved", monday.toEpochMilli(), "approved", warmup = true)
        insert("draft", monday.toEpochMilli() + 1, "draft")
        insert("missing", monday.toEpochMilli() + 2, "missing")
        insert("unknown", monday.toEpochMilli() + 3, "unknown")
        insert("active", monday.toEpochMilli() + 4, "approved", SessionStatus.IN_PROGRESS)
        insert("cancelled", monday.toEpochMilli() + 5, "approved", SessionStatus.CANCELLED)
        val snapshot = requireNotNull(repository().observeProgress({ now }, zone).first())
        assertThat(snapshot.overview.workoutsThisWeek).isEqualTo(4)
        assertThat(snapshot.overview.completedSetsThisWeek).isEqualTo(5)
        assertThat(snapshot.overview.warmupSetsThisWeek).isEqualTo(1)
        assertThat(snapshot.overview.totalRepsThisWeek).isEqualTo(50)
        assertThat(snapshot.overview.totalVolumeThisWeek).isEqualTo(1_000.0)
        val ledger = snapshot.reviewedDose
        assertThat(ledger.directPrimarySets).containsExactly("Chest", 1)
        assertThat(ledger.secondaryInvolvement).containsExactly("Triceps", 1)
        assertThat(ledger.unattributedWorkSets).containsExactly(
            LedgerOmissionReason.METADATA_NOT_APPROVED, 1,
            LedgerOmissionReason.MISSING_REVIEWED_METADATA, 1,
            LedgerOmissionReason.UNKNOWN_EXERCISE, 1
        )
        assertThat(ledger.creditedWorkSets + ledger.omittedWorkSets)
            .isEqualTo(snapshot.overview.completedSetsThisWeek - snapshot.overview.warmupSetsThisWeek)
    }

    @Test fun emptyHistoryAndDraftActivityAreDifferent() = runBlocking {
        val repository = repository()
        val empty = requireNotNull(repository.observeProgress({ now }, zone).first())
        assertThat(empty.overview.workoutsThisWeek).isEqualTo(0)
        assertThat(empty.reviewedDose.omittedWorkSets).isEqualTo(0)
        insert("draft", monday.toEpochMilli(), "draft")
        val draft = requireNotNull(repository.observeProgress({ now }, zone).first())
        assertThat(draft.overview.workoutsThisWeek).isEqualTo(1)
        assertThat(draft.reviewedDose.directPrimarySets).isEmpty()
        assertThat(draft.reviewedDose.omittedWorkSets).isEqualTo(1)
    }

    @Test fun aNewZoneReconstructsTheWeekInsteadOfRelabellingIt() = runBlocking {
        val boundary = Instant.parse("2026-09-07T00:00:00Z")
        insert("sunday", boundary.toEpochMilli() - 3_600_000, "approved")
        val repository = repository()
        val utc = requireNotNull(repository.observeProgress({ boundary }, zone).first())
        val losAngeles = requireNotNull(repository.observeProgress(
            { boundary }, ZoneId.of("America/Los_Angeles")
        ).first())
        assertThat(utc.overview.workoutsThisWeek).isEqualTo(0)
        assertThat(utc.reviewedDose.creditedWorkSets).isEqualTo(0)
        assertThat(utc.previousReviewedDose.creditedWorkSets).isEqualTo(1)
        assertThat(losAngeles.overview.workoutsThisWeek).isEqualTo(1)
        assertThat(losAngeles.reviewedDose.creditedWorkSets).isEqualTo(1)
        assertThat(losAngeles.week.startEpochDay).isEqualTo(utc.week.startEpochDay - 7)
        assertThat(losAngeles.reviewedDose.timeZoneId).isEqualTo("America/Los_Angeles")
    }

    @Test fun aChangedMeasurementRefreshesActivityEvenWhenLedgerFingerprintIsUnchanged() = runBlocking {
        insert("measured", monday.toEpochMilli(), "approved")
        val repository = repository()
        val first = requireNotNull(repository.observeProgress({ now }, zone).first())
        val set = requireNotNull(db.workoutSessionDao().getSessionWithDetails("measured"))
            .exercisesWithSets.single().sets.single()
        db.workoutSessionDao().insertWorkoutSets(listOf(set.copy(completedReps = 11, completedWeight = 30.0)))
        val second = requireNotNull(repository.observeProgress({ now }, zone).first())
        assertThat(first.reviewedDose).isEqualTo(second.reviewedDose)
        assertThat(first.overview.totalVolumeThisWeek).isEqualTo(200.0)
        assertThat(second.overview.totalVolumeThisWeek).isEqualTo(330.0)
        assertThat(second.overview.totalRepsThisWeek).isEqualTo(11)
    }

    @Test fun aLongDstWeekAndItsComparisonUseTheSameCalendarBounds() = runBlocking {
        val newYork = ZoneId.of("America/New_York")
        val instant = Instant.parse("2026-11-02T04:45:00Z")
        val week = wallcrawl.elopenmike.com.core.model.TrainingWeek.containing(instant, newYork)
        insert("start", week.startEpochMillis, "approved")
        insert("late-sunday", Instant.parse("2026-11-02T04:30:00Z").toEpochMilli(), "approved")
        insert("prior-week", week.startEpochMillis - 1, "approved")
        insert("next-monday", week.endEpochMillisExclusive, "approved")

        val snapshot = requireNotNull(repository().observeProgress({ instant }, newYork).first())
        assertThat(snapshot.week.elapsedMillis).isEqualTo(169 * 3_600_000L)
        assertThat(snapshot.overview.workoutsThisWeek).isEqualTo(2)
        assertThat(snapshot.reviewedDose.creditedWorkSets).isEqualTo(2)
        assertThat(snapshot.previousReviewedDose.creditedWorkSets).isEqualTo(1)
        val chest = snapshot.overview.legacyPrimaryActivity.single { it.muscle == "Chest" }
        assertThat(chest.setsThisWeek).isEqualTo(2)
        assertThat(chest.setsPreviousWeek).isEqualTo(1)
        assertThat(chest.percentageChange).isEqualTo(100)
    }

    @Test fun databaseAndCatalogFailuresPropagateInsteadOfBecomingEmptyHistory() {
        catalog.failWith(IllegalStateException("catalog unavailable"))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository().observeProgress({ now }, zone).first() }
        }
        catalog = FakeCatalogSource(syntheticSnapshot(listOf(approved)))
        db.close()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository().observeProgress({ now }, zone).first() }
        }
    }

    @Test fun aLiveObserverRefreshesAfterCompletionDeletionAndRestore() = runBlocking {
        val repository = repository()
        val backup = backup()
        val output = ByteArrayOutputStream()
        insert("restorable", monday.toEpochMilli(), "approved")
        backup.exportTo { output }
        val values = Channel<ProgressSnapshot?>(Channel.UNLIMITED)
        var readInstant = monday.plusSeconds(30)
        val collector = launch { repository.observeProgress({ readInstant }, zone).collect { values.send(it) } }
        suspend fun awaitWorkouts(count: Int?): ProgressSnapshot? = withTimeout(10_000) {
            var value = values.receive()
            while (value?.overview?.workoutsThisWeek != count) value = values.receive()
            value
        }
        try {
            awaitWorkouts(1)
            readInstant = monday.plusSeconds(60)
            insert("newly-completed", readInstant.toEpochMilli(), "draft")
            val updated = requireNotNull(awaitWorkouts(2))
            assertThat(updated.reviewedDose.omittedWorkSets).isEqualTo(1)
            assertThat(updated.overview.recentHistory.first().id).isEqualTo("newly-completed")
            backup.deleteAllLocalData()
            assertThat(awaitWorkouts(null)).isNull()
            assertThat(db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)).isNull()
            assertThat(cacheCount()).isEqualTo(0)
            backup.restoreFrom(ByteArrayInputStream(output.toByteArray()))
            assertThat(awaitWorkouts(1)?.reviewedDose?.creditedWorkSets).isEqualTo(1)
        } finally {
            collector.cancel()
            values.close()
        }
    }

    @Test fun deletionCannotRaceTheReadAndResurrectACacheOrMixHistory() = runBlocking {
        insert("old", monday.toEpochMilli(), "approved")
        val enteredLedger = CompletableDeferred<Unit>()
        val releaseLedger = CompletableDeferred<Unit>()
        val delegate = ledger()
        val blocking = object : WeeklyDoseLedgerRepository {
            override suspend fun weeklyLedgerAt(profileId: String, instant: Instant, zoneId: ZoneId): WeeklyDoseLedger {
                val result = delegate.weeklyLedgerAt(profileId, instant, zoneId)
                enteredLedger.complete(Unit)
                releaseLedger.await()
                return result
            }
            override suspend fun currentWeeklyLedger(profileId: String, zoneId: ZoneId) =
                weeklyLedgerAt(profileId, now, zoneId)
        }
        val read = async { repository(blocking).observeProgress({ now }, zone).first() }
        withTimeout(10_000) { enteredLedger.await() }
        val deletion = async { backup().deleteAllLocalData() }
        releaseLedger.complete(Unit)
        val snapshot = requireNotNull(read.await())
        assertThat(snapshot.overview.workoutsThisWeek).isEqualTo(snapshot.reviewedDose.creditedWorkSets)
        deletion.await()
        assertThat(repository().observeProgress({ now }, zone).first()).isNull()
        assertThat(cacheCount()).isEqualTo(0)
    }

    private fun ledger() = OfflineWeeklyDoseLedgerRepository(
        db.completedWorkoutHistoryDao(), db.weeklyDoseLedgerStateDao(), catalog
    )
    private fun repository(ledger: WeeklyDoseLedgerRepository = ledger()) =
        OfflineProgressRepository(db, catalog, ledger, gate)
    private fun backup() = OfflineLocalDataBackupRepository(
        db.localDataBackupDao(), "test", 1, { SYNTHETIC_CATALOG_COMMIT }, gate
    )
    private fun cacheCount(): Int = db.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM weekly_dose_ledger_state").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private suspend fun insert(
        id: String,
        timestamp: Long,
        exerciseId: String,
        status: SessionStatus = SessionStatus.COMPLETED,
        warmup: Boolean = false
    ) {
        val types = if (warmup) listOf(SetType.NORMAL, SetType.WARMUP) else listOf(SetType.NORMAL)
        db.workoutSessionDao().insertWorkout(
            WorkoutSessionEntity(
                id = id, name = "Disposable test", startedAtTimestamp = timestamp - 1,
                completedAtTimestamp = timestamp, targetDurationMinutes = 30,
                actualDurationMinutes = 30, weightUnit = WeightUnit.KG, status = status,
                focusMusclesJson = "", notes = ""
            ),
            listOf(WorkoutExerciseEntity(
                id = "$id-exercise", sessionId = id, exerciseId = exerciseId, orderIndex = 0,
                targetSets = types.size, targetRepMin = 8, targetRepMax = 10, targetWeight = 20.0,
                notes = ""
            )),
            types.mapIndexed { index, type -> WorkoutSetEntity(
                id = "$id-$index", workoutExerciseId = "$id-exercise", setNumber = index + 1,
                targetReps = 10, completedReps = 10, targetWeight = 20.0,
                completedWeight = 20.0, isCompleted = true, rpe = null, rir = null, type = type,
                completedAtTimestamp = timestamp
            ) }
        )
    }
}
