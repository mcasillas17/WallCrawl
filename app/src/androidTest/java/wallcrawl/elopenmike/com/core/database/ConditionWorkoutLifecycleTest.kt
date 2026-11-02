package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.entity.*
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class ConditionWorkoutLifecycleTest {
    private lateinit var db: WallCrawlDatabase
    private lateinit var repository: OfflineWorkoutRepository
    private val text = ConditionText("FICTIONAL TEST", "PRUEBA FICTICIA")
    private val selected = listOf(SelectedCondition("condition-a", 1, emptyMap()), SelectedCondition("condition-b", 1, emptyMap()))
    private val frozen = SessionConditionContext(1, 1, selected, selected.associate { it.conditionId to "v1" }, selected.map {
        AppliedConditionRule(ConditionRuleReference(it.conditionId, "v1", "case", "rule", 1),
            ConditionExerciseIdentity("exercise", "a".repeat(64), ExerciseType.DURATION, "b".repeat(64)),
            "execution", PrescriptionPurpose.SUPPORTIVE, text)
    }, emptyList(), emptyList())
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java).build()
        db.userProfileDao().insertOrUpdate(UserProfile(activeConditions = selected).toUserProfileEntity())
        repository = OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao())
        db.workoutSessionDao().insertWorkout(WorkoutSessionEntity("session", "test", 1, null, 30, 0,
            WeightUnit.KG, SessionStatus.IN_PROGRESS, focusMusclesJson = "", notes = "",
            conditionContextJson = ConditionContextCodec.encodeSession(frozen)),
            listOf(WorkoutExerciseEntity("instance", "session", "exercise", 0, ExerciseType.DURATION,
                2, null, null, null, notes = "", purpose = PrescriptionPurpose.SUPPORTIVE.name)),
            listOf("set-a", "set-b").mapIndexed { index, id -> WorkoutSetEntity(id, "instance", index + 1,
                ExerciseType.DURATION, null, null, null, null, isCompleted = false, rpe = null, rir = null, type = SetType.NORMAL) })
    }
    @After fun teardown() = db.close()

    @Test fun painStopsFanOutAreIdempotentAndSurviveEditsAndCancellation() = runBlocking {
        val stop = SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100)
        repository.logSetCompletion("set-a", stop)
        val first = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID)
        assertEquals(2, first.size)
        assertEquals(1L, db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)!!.revision)
        assertTrue(first.all { it.sourceSetId == "set-a" && it.stoppedContextIdentity == frozen.canonicalDigest })
        repository.logSetCompletion("set-a", stop)
        repository.logSetCompletion("set-a", stop.copy(durationSeconds = 10))
        assertEquals(first, db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID))
        repository.logSetCompletion("set-a", SetPerformanceInput(durationSeconds = 20, isCompleted = true, completedAtTimestamp = 101))
        assertEquals(first, db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID))
        repository.cancelWorkout("session")
        assertEquals(first, db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID))
        assertNull(db.workoutSetDao().getSetById("set-a"))
    }

    @Test fun distinctStopsAtTheSameMillisecondReplaceEveryAffectedEvent() = runBlocking {
        val stop = SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100)
        repository.logSetCompletion("set-a", stop)
        val first = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID)
        repository.logSetCompletion("set-b", stop)
        val second = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID)
        assertEquals(2, second.size)
        assertTrue(second.all { it.sourceSetId == "set-b" && first.none { old -> old.eventId == it.eventId } })
        assertEquals(2L, db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)!!.revision)
    }
    @Test fun reassessmentRestoresRemovedSelectionAndClearsOnlyExactDeletedSourceEvent() = runBlocking {
        repository.logSetCompletion("set-a", SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100))
        val pending = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).first()
        repository.cancelWorkout("session")
        val profiles = profiles()
        profiles.updateActiveConditions(emptyList())
        val revision = profiles.getProfileOnce().revision
        profiles.reassessCondition(pending.conditionId, pending.exerciseId, pending.eventId, mapOf("response" to "continue"))
        assertEquals(listOf(SelectedCondition(pending.conditionId, 1, mapOf("response" to "continue"))), profiles.getProfileOnce().activeConditions)
        assertEquals(revision + 1, profiles.getProfileOnce().revision)
        assertEquals(1, db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).size)
        assertNull(db.conditionReassessmentDao().get(pending.profileId, pending.conditionId, pending.exerciseId))
        assertTrue(runCatching { profiles.reassessCondition(pending.conditionId, pending.exerciseId, pending.eventId,
            mapOf("response" to "continue")) }.exceptionOrNull() is ConditionReassessmentRefused)
    }

    @Test fun reassessmentRejectsActiveStaleUnknownPolicyAndMissingOrStopAnswers() = runBlocking {
        repository.logSetCompletion("set-a", SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100))
        val pending = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).first()
        val profiles = profiles()
        assertTrue(runCatching { profiles.reassessCondition(pending.conditionId, pending.exerciseId, pending.eventId,
            mapOf("response" to "continue")) }.exceptionOrNull() is ConditionReassessmentRefused)
        repository.cancelWorkout("session")
        for (answers in listOf(emptyMap(), mapOf("response" to null), mapOf("response" to "stop"), mapOf("response" to "unknown"))) {
            assertTrue(runCatching { profiles.reassessCondition(pending.conditionId, pending.exerciseId, pending.eventId, answers) }.exceptionOrNull() is ConditionReassessmentRefused)
        }
        db.conditionReassessmentDao().replace(pending.copy(eventId = "new-event"))
        assertTrue(runCatching { profiles.reassessCondition(pending.conditionId, pending.exerciseId, pending.eventId,
            mapOf("response" to "continue")) }.exceptionOrNull() is ConditionReassessmentRefused)
        db.conditionReassessmentDao().replace(pending.copy(responsePolicyVersion = 2))
        assertTrue(runCatching { profiles.reassessCondition(pending.conditionId, pending.exerciseId, pending.eventId,
            mapOf("response" to "continue")) }.exceptionOrNull() is ConditionReassessmentRefused)
        assertEquals(2, db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).size)
    }

    @Test fun failedConditionalOutcomeRollsBackAndDoesNotBootstrapMissingProfile() = runBlocking<Unit> {
        val stop = SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_marker BEFORE INSERT ON condition_reassessment_pending BEGIN SELECT RAISE(ABORT, 'injected marker failure'); END")
        assertTrue(runCatching { repository.logSetCompletion("set-a", stop) }.isFailure)
        assertNull(db.workoutSetDao().getSetById("set-a")!!.stopReason)
        assertEquals(0L, db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)!!.revision)
        assertTrue(db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_marker")
        db.openHelper.writableDatabase.execSQL("DELETE FROM user_profiles")
        assertTrue(runCatching { repository.logSetCompletion("set-a", stop) }.isFailure)
        assertNull(db.workoutSetDao().getSetById("set-a")!!.stopReason)
        assertNull(db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID))
        assertTrue(db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).isEmpty())
    }
    @Test fun corruptContextRefusesButLegacyContextStillLogsAndInactiveWritesMakeNoMarker() = runBlocking<Unit> {
        val stop = SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100)
        db.openHelper.writableDatabase.execSQL("UPDATE workout_sessions SET conditionContextJson='broken'")
        assertTrue(runCatching { repository.logSetCompletion("set-a", stop) }.isFailure)
        assertNull(db.workoutSetDao().getSetById("set-a")!!.stopReason)
        db.openHelper.writableDatabase.execSQL("UPDATE workout_sessions SET conditionContextJson=NULL")
        repository.logSetCompletion("set-a", stop)
        assertEquals(SetStopReason.PAIN_STOP, db.workoutSetDao().getSetById("set-a")!!.stopReason)
        assertTrue(db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).isEmpty())
        db.workoutSessionDao().completeSessionIfActive("session", completedAt = 101, actualDuration = 1)
        assertTrue(runCatching { repository.logSetCompletion("set-b", stop) }.isFailure)
        assertTrue(db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).isEmpty())
    }
    @Test fun frozenConditionsOwnStopsAndRevisionBumpPreservesEveryOtherProfileByte() = runBlocking<Unit> {
        val original = db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)!!.copy(
            activeConditionsJson = "[]", trainingConstraintsJson = "FUTURE_CONSTRAINT", name = "Preserved profile")
        db.userProfileDao().insertOrUpdate(original)
        repository.logSetCompletion("set-a", SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100))
        assertEquals(original.copy(revision = 1), db.userProfileDao().getProfile(original.id))
        assertEquals(selected.map { it.conditionId }.toSet(), db.conditionReassessmentDao().forProfile(original.id).map { it.conditionId }.toSet())
    }
    @Test fun concurrentIdenticalPainRetriesCreateOneEventAndRevision() = runBlocking<Unit> {
        val stop = SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100)
        val first = async(kotlinx.coroutines.Dispatchers.Default) { repository.logSetCompletion("set-a", stop) }
        val second = async(kotlinx.coroutines.Dispatchers.Default) { repository.logSetCompletion("set-a", stop) }
        first.await(); second.await()
        assertEquals(2, db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).size)
        assertEquals(1L, db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)!!.revision)
    }
    @Test fun profileOrEventChangeDuringReassessmentValidationWinsOverCapturedAnswers() = runBlocking<Unit> {
        repository.logSetCompletion("set-a", SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100))
        val pending = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).first()
        repository.cancelWorkout("session")
        val gate = kotlinx.coroutines.sync.Mutex()
        val edits = OfflineUserProfileRepository(db.userProfileDao(), gate)
        var first = true
        val racing = OfflineUserProfileRepository(db.userProfileDao(), gate, db.conditionReassessmentDao(), conditionSupport = {
            if (first) { first = false; edits.updateThemePreference(ThemePreference.DARK) }
            support()
        }, exerciseCatalog = wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog(listOf(exercise)))
        val failure = runCatching { racing.reassessCondition(pending.conditionId, pending.exerciseId, pending.eventId,
            mapOf("response" to "continue")) }.exceptionOrNull() as ConditionReassessmentRefused
        assertEquals("stale-profile", failure.reason.reason)
        assertEquals(ThemePreference.DARK, edits.getProfileOnce().themePreference)
        assertEquals(pending, db.conditionReassessmentDao().get(pending.profileId, pending.conditionId, pending.exerciseId))
        first = true
        val newer = OfflineUserProfileRepository(db.userProfileDao(), gate, db.conditionReassessmentDao(), conditionSupport = {
            if (first) { first = false; db.conditionReassessmentDao().replace(pending.copy(eventId = "new-event")) }
            support()
        }, exerciseCatalog = wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog(listOf(exercise)))
        val stale = runCatching { newer.reassessCondition(pending.conditionId, pending.exerciseId, pending.eventId,
            mapOf("response" to "continue")) }.exceptionOrNull() as ConditionReassessmentRefused
        assertEquals("stale-event", stale.reason.reason)
        assertEquals("new-event", db.conditionReassessmentDao().get(pending.profileId, pending.conditionId, pending.exerciseId)!!.eventId)
    }

    @Test fun newTransitionOrChangedStopTimeCreatesANewEventWithoutChangingItsTimestamp() = runBlocking<Unit> {
        val stop = SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100)
        repository.logSetCompletion("set-a", stop)
        val first = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID)
        repository.logSetCompletion("set-a", SetPerformanceInput(isCompleted = false))
        assertEquals(first, db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID))
        repository.logSetCompletion("set-a", stop)
        val second = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID)
        assertTrue(second.all { row -> first.none { it.eventId == row.eventId } })
        repository.logSetCompletion("set-a", stop.copy(stoppedAtTimestamp = 99))
        val third = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID)
        assertTrue(third.all { row -> row.stoppedAtTimestamp == 99L && second.none { it.eventId == row.eventId } })
        assertEquals(3L, db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)!!.revision)
    }
    @Test fun unknownCurrentPolicyAndPolicyChangeDuringValidationRemainPending() = runBlocking<Unit> {
        repository.logSetCompletion("set-a", SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100))
        val pending = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).first()
        repository.cancelWorkout("session")
        var calls = 0
        for (version in listOf(2, 1)) {
            calls = 0
            val changing = OfflineUserProfileRepository(db.userProfileDao(), conditionReassessmentDao = db.conditionReassessmentDao(),
                conditionSupport = { calls++; support(responseVersion = if (calls == 1) version else 2) },
                exerciseCatalog = wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog(listOf(exercise)))
            val error = runCatching { changing.reassessCondition(pending.conditionId, pending.exerciseId, pending.eventId,
                mapOf("response" to "continue")) }.exceptionOrNull() as ConditionReassessmentRefused
            assertEquals(if (version == 2) "unsupported-policy-or-context" else "changed-condition-facts", error.reason.reason)
            assertEquals(pending, db.conditionReassessmentDao().get(pending.profileId, pending.conditionId, pending.exerciseId))
        }
    }

    @Test fun mismatchedStoredSetTypeCannotRecordAConditionalPainStop() = runBlocking<Unit> {
        db.openHelper.writableDatabase.execSQL("UPDATE workout_sets SET exerciseType='WEIGHT_REPS' WHERE id='set-a'")
        val failure = runCatching { repository.logSetCompletion("set-a",
            SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100)) }.exceptionOrNull()
        assertNotNull(failure)
        assertNull(db.workoutSetDao().getSetById("set-a")!!.stopReason)
        assertTrue(db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).isEmpty())
        assertEquals(0L, db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)!!.revision)
    }

    @Test fun reassessmentClearFailureRollsBackSelectionsAndRevision() = runBlocking<Unit> {
        repository.logSetCompletion("set-a", SetPerformanceInput(isCompleted = false, stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = 100))
        val pending = db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).first()
        repository.cancelWorkout("session")
        val original = db.userProfileDao().getProfile(pending.profileId)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_clear BEFORE DELETE ON condition_reassessment_pending BEGIN SELECT RAISE(ABORT, 'injected clear failure'); END")
        assertTrue(runCatching { profiles().reassessCondition(pending.conditionId, pending.exerciseId, pending.eventId,
            mapOf("response" to "continue")) }.isFailure)
        assertEquals(original, db.userProfileDao().getProfile(pending.profileId))
        assertEquals(pending, db.conditionReassessmentDao().get(pending.profileId, pending.conditionId, pending.exerciseId))
    }

    private val exercise = Exercise("exercise", name = "FICTIONAL", primaryMuscles = listOf(StandardMuscles.CALVES), type = ExerciseType.DURATION, isStretch = true)
    private fun support(responseVersion: Int = 1) = ConditionSupportCatalog("test", selected.associate { selection ->
        val hash = "c".repeat(64)
        selection.conditionId to ConditionSupportRecord(selection.conditionId, "v1", 1, 1,
            ConditionPopulation.ADULT_NONOPERATIVE, text, text, emptyList(), listOf(ConditionApplicabilityCase("case", emptyMap(),
                listOf(ConditionRule("rule", ConditionExerciseIdentity.from(exercise, "b".repeat(64)),
                    ConditionRuleEffect.PRESCRIBE_SUPPORTIVE, "execution", text, setOf("source"), null, false,
                    ExercisePrescription(ExerciseType.DURATION, 2, targetDurationSeconds = 20, purpose = PrescriptionPurpose.SUPPORTIVE),
                    ConditionSchedule(2, 24, text), ConditionSupportiveRequirements(emptySet(), emptySet(), ImpactLevel.NONE, listOf(emptyList())))))),
            listOf(ConditionSource("source", "https://example.invalid/test", "a".repeat(64))),
            ConditionResponsePolicy(responseVersion, listOf(ConditionInputDefinition("response", text, true,
                mapOf("continue" to text, "stop" to text))), mapOf("response" to "stop"), mapOf("response" to "stop"), text), hash,
            ConditionReleaseReview(ConditionReviewState.RELEASED, "FICTIONAL", "FICTIONAL", 1, "FICTIONAL", hash, setOf("source")))
    })
    private fun profiles() = OfflineUserProfileRepository(db.userProfileDao(),
        conditionReassessmentDao = db.conditionReassessmentDao(), conditionSupport = { support() },
        exerciseCatalog = wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog(listOf(exercise)))

}
