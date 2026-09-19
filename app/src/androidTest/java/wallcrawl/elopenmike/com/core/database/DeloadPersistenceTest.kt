package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFixtures
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class DeloadPersistenceTest {
    private lateinit var db: WallCrawlDatabase
    private val gate = Mutex()
    private lateinit var deload: OfflineDeloadRepository
    private lateinit var backup: OfflineLocalDataBackupRepository
    private val profile = UserProfile(onboardingCompleted = true, returningAfterBreakWeeks = 12)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java).build()
        deload = OfflineDeloadRepository(db.deloadPreferencesDao(), gate,
            Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC))
        backup = OfflineLocalDataBackupRepository(db.localDataBackupDao(), "test", 1, { null }, gate)
        runBlocking { db.userProfileDao().insertOrUpdate(profile.toUserProfileEntity()) }
    }
    @After fun tearDown() { db.close() }

    @Test fun profileReplaceAndExportDeleteRestorePreserveAcceptance() = runBlocking {
        accept()
        val before = deload.get()
        db.userProfileDao().insertOrUpdate(profile.copy(revision = 1, name = "Edited").toUserProfileEntity())
        assertEquals(before, deload.get())
        val bytes = ByteArrayOutputStream().also { sink -> backup.exportTo { sink } }.toByteArray()
        backup.deleteAllLocalData()
        assertEquals(DeloadPreferences(), deload.get())
        backup.restoreFrom(ByteArrayInputStream(bytes))
        assertEquals(before, deload.get())
    }

    @Test fun preferenceOnlyDestinationBlocksRestoreAndQueuedWriteCannotResurrectAfterDelete() = runBlocking {
        accept()
        db.userProfileDao().insertOrUpdate(profile.copy(onboardingCompleted = false).toUserProfileEntity())
        assertFalse(backup.isRestoreAllowed())
        gate.lock()
        val deletion = async(start = CoroutineStart.UNDISPATCHED) { backup.deleteAllLocalData() }
        val write = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { deload.decide(DeloadAction.REQUEST, 0, 2, null) }
        }
        gate.unlock()
        deletion.await()
        assertTrue(write.await().isFailure)
        assertNull(db.deloadPreferencesDao().get(profile.id))
        assertTrue(backup.isRestoreAllowed())
    }

    @Test fun staleZeroRevisionAndWrongAcceptedIdentityLeaveAllTablesUntouched() = runBlocking {
        accept()
        for ((revision, id) in listOf(0L to null, 2L to "wrong", 2L to null)) {
            assertThrows(IllegalStateException::class.java) {
                runBlocking { start("stale", revision, id) }
            }
            assertNull(db.workoutSessionDao().getSessionWithDetails("stale"))
            assertNull(db.workoutSessionDao().getRecommendationRecord("stale"))
            assertEquals(DeloadChoiceStatus.ACCEPTED, deload.get().choice!!.status)
        }
    }

    @Test fun absentPreferencesAreExactlyRevisionZeroAndOrdinaryStartDoesNotCreateARow() = runBlocking {
        assertThrows(IllegalStateException::class.java) { runBlocking { start("wrong-revision", 1, null) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { start("wrong-id", 0, "missing") } }
        assertNull(db.workoutSessionDao().getActiveSession())
        start("ordinary", 0, null)
        assertNotNull(db.workoutSessionDao().getRecommendationRecord("ordinary"))
        assertNull(db.deloadPreferencesDao().get(profile.id))
    }

    @Test fun activeWorkoutBlocksDecisionsAndDuplicateStartCannotConsumeANewChoice() = runBlocking {
        start("active", 0, null)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { deload.decide(DeloadAction.REQUEST, 0, 0, null) }
        }
        val preferences = DeloadPreferences(revision = 2, choice = DeloadChoice(
            DeloadOffer("pending", DeloadSource.EXPLICIT_REQUEST, "DELOAD_ONE_WORKOUT_V1"),
            DeloadChoiceStatus.ACCEPTED, 1_000))
        // Simulates persisted state from a manual start, which leaves acceptance pending.
        db.deloadPreferencesDao().upsert(preferences.toEntity())
        start("duplicate", 2, "pending")
        assertEquals(preferences, deload.get())
        assertNull(db.workoutSessionDao().getSessionWithDetails("duplicate"))
    }

    @Test fun automaticStartConsumesExactlyOnceAndCancellationNeverRearms() = runBlocking {
        accept()
        val accepted = deload.get()
        start("started", accepted.revision, accepted.choice!!.offer.id)
        assertEquals(DeloadChoiceStatus.CONSUMED, deload.get().choice!!.status)
        assertEquals("started", deload.get().choice!!.sessionId)
        assertEquals(3L, deload.get().revision)
        start("duplicate", accepted.revision, accepted.choice.offer.id)
        assertNull(db.workoutSessionDao().getSessionWithDetails("duplicate"))
        assertEquals(3L, deload.get().revision)
        db.workoutSessionDao().deleteActiveSession("started")
        assertEquals(DeloadChoiceStatus.CONSUMED, deload.get().choice!!.status)
        val bytes = ByteArrayOutputStream().also { sink -> backup.exportTo { sink } }.toByteArray()
        backup.deleteAllLocalData()
        backup.restoreFrom(ByteArrayInputStream(bytes))
        assertEquals(DeloadChoiceStatus.CONSUMED, deload.get().choice!!.status)
    }

    @Test fun failedRecordInsertRollsBackConsumptionAndManualStartDoesNotConsume() = runBlocking {
        accept()
        val accepted = deload.get()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                db.workoutSessionDao().insertWorkoutUnlessActive(
                    session("failed"), emptyList(), emptyList(), profile.id, 0,
                    record("different"), accepted.revision, accepted.choice!!.offer.id)
            }
        }
        assertNull(db.workoutSessionDao().getSessionWithDetails("failed"))
        assertEquals(accepted, deload.get())
        db.workoutSessionDao().insertWorkoutUnlessActive(
            session("manual").copy(origin = WorkoutOrigin.CUSTOM_TEMPLATE), emptyList(), emptyList(),
            profile.id, 0, null, 999, "wrong")
        assertEquals(accepted, deload.get())
    }

    @Test fun recentHistoryIncludesEveryStatusWithDeterministicTiesAndOneBoundedRecordBatch() = runBlocking<Unit> {
        val repository = OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao())
        listOf("b", "a", "c").forEachIndexed { index, id ->
            db.workoutSessionDao().insertSession(session(id).copy(status = SessionStatus.entries[index]))
            db.workoutSessionDao().insertRecommendationRecord(record(id))
        }
        assertEquals(listOf("a", "b", "c"), repository.getRecentSessions().map { it.id })
        assertEquals(setOf("a", "c"), repository.getRecommendationRecords(listOf("c", "a")).map { it.sessionId }.toSet())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.getRecentSessions(9) } }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.getRecommendationRecords((1..9).map { "id-$it" }) }
        }
    }

    private suspend fun accept() {
        deload.decide(DeloadAction.REQUEST, 0, 0, null)
        deload.decide(DeloadAction.ACCEPT, 0, 1, deload.get().choice!!.offer.id)
    }
    private suspend fun start(id: String, revision: Long, offerId: String?) =
        db.workoutSessionDao().insertWorkoutUnlessActive(
            session(id), emptyList(), emptyList(), profile.id, 0, record(id), revision, offerId)
    private fun record(id: String) = LocalDataArchiveFixtures.recommendationRecords().first()
        .copy(sessionId = id).toEntity()
    private fun session(id: String) = WorkoutSessionEntity(
        id = id, name = "Test", startedAtTimestamp = 2_000, completedAtTimestamp = null,
        targetDurationMinutes = 30, actualDurationMinutes = 0, status = SessionStatus.IN_PROGRESS,
        focusMusclesJson = "", notes = ""
    )
}
