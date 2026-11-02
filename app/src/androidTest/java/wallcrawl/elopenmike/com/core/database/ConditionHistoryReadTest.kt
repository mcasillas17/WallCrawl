package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.dao.ConditionHistoryDao
import wallcrawl.elopenmike.com.core.database.entity.*
import wallcrawl.elopenmike.com.core.database.repository.toUserProfileEntity
import wallcrawl.elopenmike.com.core.database.repository.toEntity
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class ConditionHistoryReadTest {
    private lateinit var db: WallCrawlDatabase
    private val now = Instant.parse("2026-11-04T12:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("UTC")
    private val profile = UserProfile(activeConditions = listOf(SelectedCondition("fictional", 1, emptyMap())))
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java).build()
        db.userProfileDao().insertOrUpdate(profile.toUserProfileEntity())
    }
    @After fun close() = db.close()
    private suspend fun read(program: Boolean = false) = db.workoutSessionDao().readConditionContext(profile.id, support(), now, zone, program)

    @Test fun manualSupportiveAndStoppedWorkCountsRegardlessOfParentStatus() = runBlocking {
        SessionStatus.entries.forEachIndexed { index, status -> insert("session-$index", status, now - 1000, stop = index == 0) }
        val history = read().history
        assertTrue(history.complete)
        assertEquals(SessionStatus.entries.size, history.exposures.size)
        assertEquals(SessionStatus.entries.size, history.schedule("fictional-exercise", ConditionSchedule(7, 0, text)).weeklyExposures)
    }
    @Test fun missingLegacyTimestampAndFutureOutcomesAreIncomplete() = runBlocking {
        insert("legacy", SessionStatus.COMPLETED, null)
        assertFalse(read().history.complete)
        db.clearAllTables()
        db.userProfileDao().insertOrUpdate(profile.toUserProfileEntity())
        insert("future", SessionStatus.IN_PROGRESS, now + 1)
        assertFalse(read().history.complete)
    }
    @Test fun outcomeBeforeItsSessionStartedIsIncomplete() = runBlocking {
        insert("bad-order", SessionStatus.COMPLETED, now - 1000, started = now - 500)
        assertFalse(read().history.complete)
    }
    @Test fun markersWithMissingSourceRemainIndependentAndBounded() = runBlocking {
        val marker = marker(0)
        db.conditionReassessmentDao().replace(marker)
        assertEquals("deleted-set", read().history.pending.single().sourceSetId)
        assertEquals(1, db.workoutSessionDao().conditionPendingProbe(profile.id, 1).size)
        db.withTransaction {
            repeat(ConditionHistoryDao.MAX_CONDITION_MARKERS) { db.conditionReassessmentDao().replace(marker.copy(exerciseId = "other-$it")) }
        }
        assertFalse(read().history.complete)
        assertEquals(ConditionHistoryDao.MAX_CONDITION_MARKERS, read().history.pending.size)
    }
    @Test fun closedOldLegacyOutcomeIsProvablyOutsideButOpenUnknownStillBlocks() = runBlocking {
        insert("old", SessionStatus.COMPLETED, now - 30 * 86_400_000L, started = now - 31 * 86_400_000L)
        assertTrue(read().history.complete)
        assertTrue(read().history.exposures.isEmpty())
        insert("unknown-old", SessionStatus.COMPLETED, null, started = now - 31 * 86_400_000L, completed = now - 30 * 86_400_000L)
        assertTrue(read().history.complete)
        assertTrue(read().history.exposures.isEmpty())
        insert("unknown-open", SessionStatus.IN_PROGRESS, null, started = now - 31 * 86_400_000L)
        assertFalse(read().history.complete)
    }
    @Test fun boundedProbeAndSnapshotRetainMarkersAfterLastSelectionRemoval() = runBlocking {
        val repository = OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao())
        db.conditionReassessmentDao().replace(marker(0))
        db.conditionReassessmentDao().replace(marker(1).copy(profileId = "other-profile"))
        db.userProfileDao().insertOrUpdate(profile.copy(activeConditions = emptyList()).toUserProfileEntity())
        assertTrue(repository.hasPendingConditionReassessment(profile.id))
        val removed = read()
        assertTrue(removed.profile.activeConditions.isEmpty())
        assertEquals(listOf("event-0"), removed.history.pending.map { it.eventId })
        assertTrue(removed.history.exposures.isEmpty())
        db.userProfileDao().insertOrUpdate(profile.copy(activeConditions = listOf(SelectedCondition("other-condition", 1, emptyMap()))).toUserProfileEntity())
        assertEquals(listOf("event-0"), read().history.pending.map { it.eventId })
        db.conditionReassessmentDao().deleteIfEventMatches(profile.id, "fictional", "fictional-exercise", "event-0")
        assertFalse(repository.hasPendingConditionReassessment(profile.id))
    }
    @Test fun oldClosedParentDoesNotExcuseMalformedExplicitChronology() = runBlocking {
        insert("old-bad", SessionStatus.COMPLETED, now - 32 * 86_400_000L,
            started = now - 31 * 86_400_000L, completed = now - 30 * 86_400_000L)
        assertFalse(read().history.complete)
    }
    @Test fun outcomeProbeIsBoundedBeforeAnyGraphLoadingAndOverflowIsIncomplete() = runBlocking {
        insert("many", SessionStatus.COMPLETED, now - 1)
        db.openHelper.writableDatabase.execSQL("""WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x < ${ConditionHistoryDao.MAX_CONDITION_OUTCOMES})
            INSERT INTO workout_sets (id, workoutExerciseId, setNumber, exerciseType, isCompleted, completedAtTimestamp, type)
            SELECT 'set-' || x, 'exercise-many', 1, 'DURATION', 1, $now, 'NORMAL' FROM n""")
        assertEquals(2, db.workoutSessionDao().conditionOutcomeProbe(listOf("fictional-exercise"), now - 100, 2).size)
        assertFalse(read().history.complete)
    }
    @Test fun profileRevisionProgramProjectionAndPendingRowsShareOneTransaction() = runBlocking {
        db.conditionReassessmentDao().replace(marker(0))
        db.programDao().upsertProgramEnrollment(ProgramEnrollment(profile.id, "718ad628-b783-4c7c-bf75-a116c65d3062", 1,
            ProgramConfiguration(FitnessGoal.STRENGTH, listOf(FitnessGoal.STRENGTH), 2), ProgramStatus.ACTIVE, now - 1000, now - 1000).toEntity())
        val writer = async(Dispatchers.IO) {
            repeat(20) { index -> db.withTransaction {
                db.userProfileDao().insertOrUpdate(profile.copy(revision = index + 1L).toUserProfileEntity())
                db.conditionReassessmentDao().replace(marker(index + 1))
                val enrollment = requireNotNull(db.programDao().getProgramEnrollment(profile.id))
                db.programDao().upsertProgramEnrollment(enrollment.copy(revision = index + 2L))
            } }
        }
        repeat(20) {
            val snapshot = read(program = true)
            assertEquals(snapshot.profile, (snapshot.program as ProgramReadState.Supported).profile)
            assertEquals("event-${snapshot.profile.revision}", snapshot.history.pending.single().eventId)
            assertEquals(snapshot.program.header, snapshot.programHeader)
            assertEquals(snapshot.profile.revision + 1, snapshot.programHeader.revision)
        }
        writer.await()
    }
    private fun marker(revision: Int) = ConditionReassessmentPendingEntity(profile.id, "fictional", "fictional-exercise", "event-$revision", "a".repeat(64), 1, now - 1, "deleted-set")
    private suspend fun insert(id: String, status: SessionStatus, outcome: Long?, stop: Boolean = false, started: Long = now - 86_400_000, completed: Long? = if (status == SessionStatus.COMPLETED) now else null) {
        db.workoutSessionDao().insertWorkout(
            WorkoutSessionEntity(id, "FICTIONAL TEST", started, completed,
                20, 20, status = status, origin = WorkoutOrigin.CUSTOM_TEMPLATE, focusMusclesJson = "", notes = ""),
            listOf(WorkoutExerciseEntity("exercise-$id", id, "fictional-exercise", 0, ExerciseType.DURATION, 1, null, null, null,
                targetDurationSeconds = 20, notes = "", purpose = "SUPPORTIVE")),
            listOf(WorkoutSetEntity("outcome-$id", "exercise-$id", 1, ExerciseType.DURATION, null, null, null, null,
                isCompleted = !stop, rpe = null, rir = null, completedAtTimestamp = if (stop) null else outcome,
                stoppedAtTimestamp = if (stop) outcome else null, stopReason = if (stop) SetStopReason.PAIN_STOP else null, type = SetType.NORMAL)))
    }
    private val text = ConditionText("FICTIONAL TEST ONLY", "SOLO PRUEBA FICTICIA")
    private fun support(): ConditionSupportCatalog {
        val exercise = Exercise("fictional-exercise", name = "FICTIONAL", primaryMuscles = emptyList(), type = ExerciseType.DURATION, isStretch = true)
        val rule = ConditionRule("rule", ConditionExerciseIdentity.from(exercise, "b".repeat(64)), ConditionRuleEffect.PRESCRIBE_SUPPORTIVE, "fictional-execution", text, setOf("source"), null, false,
            ExercisePrescription(ExerciseType.DURATION, 1, targetDurationSeconds = 20, purpose = PrescriptionPurpose.SUPPORTIVE), ConditionSchedule(2, 24, text),
            ConditionSupportiveRequirements(emptySet(), emptySet(), ImpactLevel.NONE, listOf(emptyList())))
        val record = ConditionSupportRecord("fictional", "v1", 1, 1, ConditionPopulation.ADULT_NONOPERATIVE, text, text, emptyList(), listOf(ConditionApplicabilityCase("case", emptyMap(), listOf(rule))),
            listOf(ConditionSource("source", "https://example.invalid/fictional", "c".repeat(64))),
            ConditionResponsePolicy(1, listOf(ConditionInputDefinition("response", text, true, mapOf("stop" to text))), mapOf("response" to "stop"), mapOf("response" to "stop"), text),
            "a".repeat(64), ConditionReleaseReview(ConditionReviewState.RELEASED, "FICTIONAL", "FICTIONAL", 1, "FICTIONAL", "a".repeat(64), setOf("source")))
        return ConditionSupportCatalog("fictional", mapOf("fictional" to record))
    }
}
