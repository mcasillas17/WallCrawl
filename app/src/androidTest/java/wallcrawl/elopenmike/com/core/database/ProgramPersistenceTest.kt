package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy
import wallcrawl.elopenmike.com.core.database.entity.*
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ProgramPersistenceTest {
    private lateinit var db: WallCrawlDatabase
    private lateinit var repository: OfflineProgramRepository
    private val run = "11111111-1111-1111-1111-111111111111"
    private val replacement = "22222222-2222-2222-2222-222222222222"
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java).build()
        repository = OfflineProgramRepository(db, Mutex(), Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC))
        db.userProfileDao().insertOrUpdate(profile())
    }
    @After fun close() { db.close() }

    @Test fun frozenMembershipReadDoesNotInterpretCorruptLatestEnrollment() = runBlocking {
        start(); reserve("frozen-read")
        val frozen = repository.observeAssociation("frozen-read").first()!!
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET goals='CORRUPT'")
        assertEquals(frozen, repository.observeAssociation("frozen-read").first())
        assertEquals(run, frozen.programId)
        assertEquals(0, frozen.position.slotOrdinal)
        failure(ProgramFailure.INVALID_CONFIGURATION) { repository.get() }
    }

    @Test fun frozenMembershipReadPreservesUnsupportedFactsAndRefusesLostBinding() = runBlocking {
        start(); reserve("frozen-opaque")
        db.openHelper.writableDatabase.execSQL("UPDATE program_session_associations SET configurationPolicyVersion='FUTURE_POLICY'")
        rebind("frozen-opaque")
        val frozen = repository.observeAssociation("frozen-opaque").first()!!
        assertEquals("FUTURE_POLICY", frozen.facts.configurationPolicyVersion)
        assertNull(frozen.supportedSnapshot)
        db.openHelper.writableDatabase.execSQL("DELETE FROM program_session_associations WHERE sessionId='frozen-opaque'")
        try {
            repository.observeAssociation("frozen-opaque").first()
            fail("Lost membership must refuse, never become ordinary")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun newDatabaseHasNoEnrollmentAndNullLegacyDigest() = runBlocking {
        assertEquals(15, db.openHelper.writableDatabase.version)
        assertEquals(ProgramHeader.absent("default_user"), repository.getHeader())
        assertTrue(repository.get() is ProgramReadState.Empty)
        assertEquals(0, db.programDao().getProgramSlots(run).size)
    }
    @Test fun profileReplacePreservesEnrollmentAndStaleActionsRefuse() = runBlocking {
        start()
        db.userProfileDao().insertOrUpdateWithNextRevision(profile().copy(name = "Edited"))
        assertEquals(run, repository.getHeader().programId)
        failure(ProgramFailure.STALE_STATE) { repository.act(ProgramAction.PAUSE, expected(0, 1)) }
        repository.act(ProgramAction.PAUSE, expected(1, 1))
        failure(ProgramFailure.STALE_STATE) { repository.act(ProgramAction.PAUSE, expected(1, 1)) }
        assertEquals(2, repository.getHeader().revision)
    }
    @Test fun programStartFinishRetryAndCancelAreAtomicAndDoNotInventSets() = runBlocking {
        start()
        val first = reserve("first")
        assertEquals(2, repository.getHeader().revision)
        assertEquals("first", state().progress.activeSessionId)
        failure(ProgramFailure.ACTIVE_WORKOUT) { repository.act(ProgramAction.PAUSE, expected(0, 2)) }
        val summary = db.workoutSessionDao().completeWorkoutAndReadSummary("first", 2_000, 10, ProgressCalculator())
        assertEquals(summary, db.workoutSessionDao().completeWorkoutAndReadSummary("first", 3_000, 20, ProgressCalculator()))
        assertEquals(3, repository.getHeader().revision)
        assertEquals(1, state().progress.completedSlots)
        assertTrue(first.exercisesWithSets.isEmpty())
        reserve("cancelled")
        assertEquals(4, repository.getHeader().revision)
        assertEquals(1, db.workoutSessionDao().deleteActiveSession("cancelled"))
        assertNull(db.programDao().getProgramAssociation("cancelled"))
        assertEquals(0, db.workoutSessionDao().deleteActiveSession("cancelled"))
        assertEquals(5, repository.getHeader().revision)
        assertEquals(1, state().progress.completedSlots)
        repository.act(ProgramAction.RESTART, expected(0, 5), confirmed = true, newProgramId = replacement)
        assertEquals(0, state().progress.completedSlots)
        assertEquals(run, db.programDao().getProgramAssociation("first")!!.programId)
    }
    @Test fun headerFreshnessAndMissingDigestBindingRefuseWithoutWrites() = runBlocking {
        val absent = repository.getHeader()
        start()
        failure(ProgramFailure.STALE_STATE) { ordinary("stale", absent) }
        failure(ProgramFailure.STALE_STATE) { ordinary("missing", null) }
        assertNull(db.workoutSessionDao().getActiveSession())
        val header = repository.getHeader()
        val snapshot = MultiWeekProgramPolicy.snapshot(state().enrollment, state().progress.nextPosition!!)
        try {
            db.workoutSessionDao().insertWorkoutUnlessActive(session("unbound"), emptyList(), emptyList(), "default_user", 0,
                expectedProgramHeader = header, programSession = snapshot)
            fail("Missing recommendation must refuse membership")
        } catch (_: IllegalArgumentException) { }
        assertEquals(header, repository.getHeader())
        assertNull(db.workoutSessionDao().getActiveSession())
    }
    @Test fun existingActiveReturnsBeforeProgramAndDeloadChecks() = runBlocking {
        ordinary("ordinary", null)
        startFailureBecauseActive()
        val result = db.workoutSessionDao().insertWorkoutUnlessActive(session("ignored"), emptyList(), emptyList(), "default_user", 0,
            expectedProgramHeader = ProgramHeader.absent("default_user"), expectedDeloadDecisionRevision = 99)
        assertEquals("ordinary", result.session.id)
        assertTrue(repository.get() is ProgramReadState.Empty)
    }
    @Test fun insertFailureRollsBackReservationRecommendationAndEnrollment() = runBlocking {
        start()
        val header = repository.getHeader()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_membership BEFORE INSERT ON program_session_associations BEGIN SELECT RAISE(ABORT, 'injected association failure'); END")
        try { reserve("failed"); fail("Expected trigger failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(header, repository.getHeader())
        assertNull(db.workoutSessionDao().getActiveSession())
        assertNull(db.workoutSessionDao().getRecommendationRecord("failed"))
    }
    @Test fun manualTemplateRemainsAvailableWithUnsupportedProgram() = runBlocking {
        start()
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET configurationPolicyVersion='FUTURE', primaryGoal='FUTURE_GOAL'")
        val header = repository.getHeader()
        val result = db.workoutSessionDao().insertWorkoutUnlessActive(
            session("manual").copy(origin = WorkoutOrigin.CUSTOM_TEMPLATE, sourceTemplateId = "template"),
            emptyList(), emptyList(), "default_user", 0)
        assertEquals("manual", result.session.id)
        assertEquals(header, repository.getHeader())
        assertNull(db.programDao().getProgramAssociation("manual"))
    }
    @Test fun unsupportedFullReadStillRejectsMalformedAssociationIdentity() = runBlocking {
        start(); reserve("unknown-identity")
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET configurationPolicyVersion='FUTURE'")
        db.openHelper.writableDatabase.execSQL("UPDATE program_session_associations SET profileId=''")
        failure(ProgramFailure.INVALID_IDENTITY) { repository.get() }
    }

    @Test fun unknownConfigurationStillHasHeaderAndOpaqueHistoryAndCanCancel() = runBlocking {
        start(); reserve("unknown")
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET configurationPolicyVersion='FUTURE', primaryGoal='FUTURE_GOAL'")
        db.openHelper.writableDatabase.execSQL("UPDATE program_session_associations SET configurationPolicyVersion='FUTURE', primaryGoal='FUTURE_GOAL'")
        assertEquals(run, repository.getHeader().programId)
        rebind("unknown")
        assertTrue(repository.get() is ProgramReadState.Unsupported)
        val history = OfflineWorkoutHistoryRepository(db, Mutex()).observeDetail("unknown").first()!!.programAssociation!!
        assertEquals("FUTURE_GOAL", history.facts.primaryGoal)
        assertNull(history.supportedSnapshot)
        failure(ProgramFailure.UNSUPPORTED_POLICY) {
            db.workoutSessionDao().completeWorkoutAndReadSummary("unknown", 2_000, 10, ProgressCalculator())
        }
        assertEquals(SessionStatus.IN_PROGRESS, db.workoutSessionDao().getSessionWithDetails("unknown")!!.session.status)
        assertEquals(1, db.workoutSessionDao().deleteActiveSession("unknown"))
        assertEquals(3, repository.getHeader().revision)
    }
    @Test fun malformedKnownConfigurationFailsFullReadButNotHeaderAndCannotReplace() = runBlocking {
        start()
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET goals='BAD_GOAL'")
        assertEquals(run, repository.getHeader().programId)
        failure(ProgramFailure.INVALID_CONFIGURATION) { repository.get() }
        failure(ProgramFailure.INVALID_CONFIGURATION) {
            repository.act(ProgramAction.REPLACE, expected(0, 1), true, replacement)
        }
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET programId='broken'")
        failure(ProgramFailure.INVALID_IDENTITY) { repository.getHeader() }
    }
    @Test fun deletedProfileCannotBeBootstrappedByQueuedAction() = runBlocking {
        start()
        db.openHelper.writableDatabase.execSQL("DELETE FROM user_profiles")
        failure(ProgramFailure.PROFILE_UNAVAILABLE) { repository.act(ProgramAction.PAUSE, expected(0, 1)) }
        assertNull(db.userProfileDao().getProfile("default_user"))
    }
    @Test fun historyRequiresBidirectionalAssociationBinding() = runBlocking {
        start(); reserve("bound")
        val history = OfflineWorkoutHistoryRepository(db, Mutex())
        assertEquals(run, history.observeDetail("bound").first()!!.programAssociation!!.programId)
        db.openHelper.writableDatabase.execSQL("UPDATE workout_recommendation_records SET programAssociationDigest=NULL WHERE sessionId='bound'")
        try { history.observeDetail("bound").first(); fail("Unbound association must fail") } catch (_: IllegalArgumentException) { }
        db.openHelper.writableDatabase.execSQL("DELETE FROM program_session_associations WHERE sessionId='bound'")
        db.openHelper.writableDatabase.execSQL("UPDATE workout_recommendation_records SET programAssociationDigest='" + "a".repeat(64) + "' WHERE sessionId='bound'")
        try { history.observeDetail("bound").first(); fail("Missing association must fail") } catch (_: IllegalArgumentException) { }
    }
    @Test fun finalSlotCompletesExactlyOnceAndReadsDoNotAdvance() = runBlocking {
        start()
        repeat(8) { slot ->
            val before = repository.getHeader()
            assertEquals(before, repository.getHeader())
            assertEquals(slot, state().progress.completedSlots)
            reserve("slot-$slot")
            db.workoutSessionDao().completeWorkoutAndReadSummary("slot-$slot", 2_000, 10, ProgressCalculator())
        }
        val final = state()
        assertEquals(ProgramStatus.COMPLETED, final.enrollment.status)
        assertEquals(17, final.header.revision)
        assertEquals(8, final.progress.completedSlots)
        assertNull(final.progress.nextPosition)
        db.workoutSessionDao().completeWorkoutAndReadSummary("slot-7", 3_000, 20, ProgressCalculator())
        assertEquals(final, state())
    }
    @Test fun partialFinishPreservesSetFactsDespiteChangedProfile() = runBlocking {
        start()
        val exercise = WorkoutExerciseEntity("partial-ex", "partial", "incline-dumbbell-press", 0,
            targetSets = 3, targetRepMin = 8, targetRepMax = 10, targetWeight = 20.0, notes = "")
        val complete = WorkoutSetEntity("done", "partial-ex", 1, targetReps = 10, completedReps = 8,
            targetWeight = 20.0, completedWeight = 20.0, isCompleted = true, rpe = null, rir = null,
            completedAtTimestamp = 1_500, type = SetType.NORMAL)
        val stopped = complete.copy(id = "stopped", setNumber = 2, completedReps = null, completedWeight = null,
            isCompleted = false, completedAtTimestamp = null, stoppedAtTimestamp = 1_600, stopReason = SetStopReason.USER_SKIPPED)
        val unresolved = stopped.copy(id = "unresolved", setNumber = 3, stoppedAtTimestamp = null, stopReason = null)
        reserve("partial", listOf(exercise), listOf(complete, stopped, unresolved))
        db.userProfileDao().insertOrUpdateWithNextRevision(profile().copy(daysPerWeek = 6, primaryGoal = FitnessGoal.FAT_LOSS))
        assertEquals(ProgramFailure.PROFILE_MISMATCH, state().profileFailure)
        val summary = db.workoutSessionDao().completeWorkoutAndReadSummary("partial", 2_000, 10, ProgressCalculator())
        assertEquals(1, summary.totalSetsCompleted)
        assertEquals(listOf(complete, stopped, unresolved), db.workoutSetDao().getSetsForExercise("partial-ex"))
        assertEquals(1, state().progress.completedSlots)
    }
    @Test fun finishAndCancelFailuresRollBackBothLifecycleWrites() = runBlocking {
        start(); reserve("rollback")
        val header = repository.getHeader()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_finish BEFORE UPDATE ON workout_sessions BEGIN SELECT RAISE(ABORT, 'injected finish failure'); END")
        try { db.workoutSessionDao().completeWorkoutAndReadSummary("rollback", 2_000, 10, ProgressCalculator()); fail("Expected failure") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(header, repository.getHeader())
        assertEquals(SessionStatus.IN_PROGRESS, db.workoutSessionDao().getSessionWithDetails("rollback")!!.session.status)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_finish")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_cancel BEFORE DELETE ON workout_sessions BEGIN SELECT RAISE(ABORT, 'injected cancel failure'); END")
        try { db.workoutSessionDao().deleteActiveSession("rollback"); fail("Expected failure") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(header, repository.getHeader())
        assertNotNull(db.programDao().getProgramAssociation("rollback"))
    }
    @Test fun acceptedDeloadConsumptionIsAtomicAndCancellationDoesNotRearmIt() = runBlocking {
        start()
        val choice = DeloadChoice(DeloadOffer("accepted", DeloadSource.EXPLICIT_REQUEST, "DELOAD_ONE_WORKOUT_V1"),
            DeloadChoiceStatus.ACCEPTED, 1_000)
        db.deloadPreferencesDao().upsert(DeloadPreferences(revision = 2, choice = choice).toEntity())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_membership BEFORE INSERT ON program_session_associations BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        try { reserve("failure"); fail("Expected failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(DeloadChoiceStatus.ACCEPTED, db.deloadPreferencesDao().get("default_user")!!.toDeloadPreferences().choice!!.status)
        assertEquals(1, repository.getHeader().revision)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_membership")
        reserve("deload")
        assertEquals(DeloadChoiceStatus.CONSUMED, db.deloadPreferencesDao().get("default_user")!!.toDeloadPreferences().choice!!.status)
        db.workoutSessionDao().deleteActiveSession("deload")
        assertEquals(DeloadChoiceStatus.CONSUMED, db.deloadPreferencesDao().get("default_user")!!.toDeloadPreferences().choice!!.status)
        assertEquals(0, state().progress.completedSlots)
    }
    @Test fun retainedHistoricalRunIdentifierCannotBeReused() = runBlocking {
        start(); reserve("historical")
        db.workoutSessionDao().completeWorkoutAndReadSummary("historical", 2_000, 10, ProgressCalculator())
        repository.act(ProgramAction.RESTART, expected(0, 3), true, replacement)
        failure(ProgramFailure.INVALID_IDENTITY) {
            repository.act(ProgramAction.RESTART, ProgramExpectation("default_user", 0, replacement, 4), true, run)
        }
        assertEquals(replacement, repository.getHeader().programId)
    }
    @Test fun diskReopenRetainsFrozenReservationWithoutAdvancing() = runBlocking {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        val name = "program-reopen.db"
        context.deleteDatabase(name)
        db.close()
        db = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name).build()
        db.userProfileDao().insertOrUpdate(profile())
        repository = OfflineProgramRepository(db, Mutex())
        start(); reserve("reopened")
        val before = state()
        db.close()
        db = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name).build()
        repository = OfflineProgramRepository(db, Mutex())
        assertEquals(before, state())
        assertEquals(0, state().progress.completedSlots)
        db.close(); context.deleteDatabase(name)
        Unit
    }

    @Test fun unsupportedFullReadStillRejectsMissingRecommendationBinding() = runBlocking {
        start(); reserve("unknown-binding")
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET configurationPolicyVersion='FUTURE'")
        db.openHelper.writableDatabase.execSQL("UPDATE workout_recommendation_records SET programAssociationDigest=NULL")
        try { repository.get(); fail("Unknown policy cannot hide missing binding") } catch (_: IllegalArgumentException) { }
    }
    @Test fun unsupportedEnrollmentCannotHideKnownSnapshotDigestMismatch() = runBlocking {
        start(); reserve("known-binding")
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET lifecyclePolicyVersion='FUTURE'")
        db.openHelper.writableDatabase.execSQL("UPDATE program_session_associations SET enrollmentRevision=2")
        try { repository.get(); fail("Opaque enrollment must still verify a known snapshot binding") } catch (_: IllegalArgumentException) { }
    }
    @Test fun sameRevisionRawHeaderChangesInvalidateOrdinaryStartButFreshOpaqueHeaderWorks() = runBlocking {
        start()
        val before = repository.getHeader()
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET goals='BAD_GOAL'")
        val current = repository.getHeader()
        assertEquals(before.revision, current.revision)
        assertNotEquals(before, current)
        failure(ProgramFailure.STALE_STATE) { ordinary("stale-header", before) }
        ordinary("fresh-header", current)
        assertEquals(current, repository.getHeader())
        assertNull(db.programDao().getProgramAssociation("fresh-header"))
    }
    @Test fun programActiveReturnPrecedesInvalidReservationAndPendingDeload() = runBlocking {
        start(); reserve("already-active")
        val before = repository.getHeader()
        val preferences = DeloadPreferences(revision = 2, choice = DeloadChoice(
            DeloadOffer("pending", DeloadSource.EXPLICIT_REQUEST, "DELOAD_ONE_WORKOUT_V1"), DeloadChoiceStatus.ACCEPTED, 1_000))
        db.deloadPreferencesDao().upsert(preferences.toEntity())
        val invalid = state().sessions.single().association.snapshot.copy(programId = "invalid-run")
        val result = db.workoutSessionDao().insertWorkoutUnlessActive(session("ignored-program"), emptyList(), emptyList(), "default_user", 0,
            recommendation("ignored-program", "a".repeat(64)), expectedDeloadDecisionRevision = 100,
            expectedProgramHeader = ProgramHeader.absent("default_user"), programSession = invalid)
        assertEquals("already-active", result.session.id)
        assertEquals(before, repository.getHeader())
        assertEquals(preferences, db.deloadPreferencesDao().get("default_user")!!.toDeloadPreferences())
    }
    @Test fun queuedProgramActionCannotRecreateDeletedProfile() = runBlocking {
        val gate = Mutex()
        repository = OfflineProgramRepository(db, gate)
        start()
        gate.lock()
        val deletion = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            gate.lock()
            try { db.openHelper.writableDatabase.execSQL("DELETE FROM user_profiles") } finally { gate.unlock() }
        }
        val action = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            failure(ProgramFailure.PROFILE_UNAVAILABLE) { repository.act(ProgramAction.PAUSE, expected(0, 1)) }
        }
        gate.unlock()
        deletion.await(); action.await()
        assertNull(db.userProfileDao().getProfile("default_user"))
        assertEquals(1, repository.getHeader().revision)
    }

    @Test fun directLifecycleCallersCannotDetachMembershipOrSkipRevision() = runBlocking {
        start(); reserve("direct")
        val dao = db.workoutSessionDao()
        val before = repository.getHeader()
        val row = dao.getSessionWithDetails("direct")!!.session
        try { dao.updateSession(row.copy(status = SessionStatus.COMPLETED)); fail("Direct update must refuse") }
        catch (_: IllegalArgumentException) { }
        try { dao.insertSession(row); fail("REPLACE must not delete membership") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, repository.getHeader())
        assertNotNull(db.programDao().getProgramAssociation("direct"))
        assertEquals(1, dao.completeSessionIfActive("direct", completedAt = 2_000, actualDuration = 10))
        assertEquals(3, repository.getHeader().revision)
        assertEquals(1, state().progress.completedSlots)
        assertEquals(0, dao.completeSessionIfActive("direct", completedAt = 3_000, actualDuration = 20))
        assertEquals(3, repository.getHeader().revision)
    }
    @Test fun automaticStartRejectsInventedAndMismatchedDigestsBeforeWriting() = runBlocking {
        start()
        val header = repository.getHeader()
        val snapshot = MultiWeekProgramPolicy.snapshot(state().enrollment, state().progress.nextPosition!!)
        for (associated in listOf(false, true)) {
            try {
                db.workoutSessionDao().insertWorkoutUnlessActive(session("bad-digest"), emptyList(), emptyList(), "default_user", 0,
                    recommendation("bad-digest", "a".repeat(64)), expectedProgramHeader = header,
                    programSession = if (associated) snapshot else null)
                fail("A recommendation cannot invent or mismatch membership")
            } catch (_: IllegalArgumentException) { }
            assertEquals(header, repository.getHeader())
            assertNull(db.workoutSessionDao().getSessionWithDetails("bad-digest"))
        }
    }

    @Test fun lifecycleDoesNotCoerceUnknownRawSessionStatusIntoActive() = runBlocking {
        ordinary("bad-status", null)
        db.openHelper.writableDatabase.execSQL("UPDATE workout_sessions SET status='FUTURE'")
        failure(ProgramFailure.INVALID_PROGRESS) {
            db.workoutSessionDao().completeSessionIfActive("bad-status", completedAt = 2_000, actualDuration = 10)
        }
        failure(ProgramFailure.INVALID_PROGRESS) { db.workoutSessionDao().deleteActiveSession("bad-status") }
    }

    @Test fun unsupportedEnrollmentCannotHideMalformedKnownSnapshot() = runBlocking {
        start(); reserve("malformed-known")
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET lifecyclePolicyVersion='FUTURE'")
        db.openHelper.writableDatabase.execSQL("UPDATE program_session_associations SET objective='INVALID'")
        rebind("malformed-known")
        failure(ProgramFailure.INVALID_PROGRESS) { repository.get() }
    }
    @Test fun opaqueSnapshotDigestTamperingFailsBothHistoryAndProgressReads() = runBlocking {
        start(); reserve("opaque-tamper")
        db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET configurationPolicyVersion='FUTURE'")
        db.openHelper.writableDatabase.execSQL("UPDATE program_session_associations SET configurationPolicyVersion='FUTURE', primaryGoal='FUTURE_GOAL'")
        rebind("opaque-tamper")
        assertTrue(repository.get() is ProgramReadState.Unsupported)
        val history = OfflineWorkoutHistoryRepository(db, Mutex())
        assertNull(history.observeDetail("opaque-tamper").first()!!.programAssociation!!.supportedSnapshot)
        db.openHelper.writableDatabase.execSQL("UPDATE program_session_associations SET objective='TAMPERED'")
        try { repository.get(); fail("Progress must reject changed opaque facts") } catch (_: IllegalArgumentException) { }
        try { history.observeDetail("opaque-tamper").first(); fail("History must reject changed opaque facts") } catch (_: IllegalArgumentException) { }
    }

    @Test fun manualTemplateIgnoresMalformedProgramIdentityWhileAutomaticStartRefusesIt() = runBlocking {
        start()
        for ((id, revision) in listOf("broken" to 1L, run to 0L)) {
            db.openHelper.writableDatabase.execSQL("UPDATE program_enrollments SET programId=?, revision=?", arrayOf<Any>(id, revision))
            failure(ProgramFailure.INVALID_IDENTITY) { ordinary("automatic-malformed", null) }
            assertNull(db.workoutSessionDao().getActiveSession())
            val manual = db.workoutSessionDao().insertWorkoutUnlessActive(
                session("manual-malformed").copy(origin = WorkoutOrigin.CUSTOM_TEMPLATE, sourceTemplateId = "template"),
                emptyList(), emptyList(), "default_user", 0)
            assertEquals("manual-malformed", manual.session.id)
            assertNull(db.programDao().getProgramAssociation("manual-malformed"))
            assertNull(db.workoutSessionDao().getRecommendationRecord("manual-malformed"))
            db.openHelper.writableDatabase.query("SELECT programId, revision FROM program_enrollments").use {
                assertTrue(it.moveToFirst()); assertEquals(id, it.getString(0)); assertEquals(revision, it.getLong(1))
            }
            assertEquals(1, db.workoutSessionDao().deleteActiveSession("manual-malformed"))
        }
    }
    @Test fun lostFinalCompletedAssociationRefusesFullReadRepeatedSlotStartAndManagementWithoutWrites() = runBlocking {
        start(); reserve("lost-completed")
        db.workoutSessionDao().completeWorkoutAndReadSummary("lost-completed", 2_000, 10, ProgressCalculator())
        val before = state()
        val repeatedSlot = MultiWeekProgramPolicy.snapshot(before.enrollment, ProgramPosition(0, 1, 1, 1))
        val repeated = ProgramAssociation("repeated-slot", "default_user", repeatedSlot)
        db.openHelper.writableDatabase.execSQL("DELETE FROM program_session_associations WHERE sessionId='lost-completed'")
        assertEquals(before.header, repository.getHeader())
        failure(ProgramFailure.INVALID_PROGRESS) { repository.get() }
        failure(ProgramFailure.INVALID_PROGRESS) {
            db.workoutSessionDao().insertWorkoutUnlessActive(session("repeated-slot"), emptyList(), emptyList(), "default_user", 0,
                recommendation("repeated-slot", MultiWeekProgramPolicy.associationDigest(repeated)),
                expectedProgramHeader = before.header, programSession = repeatedSlot)
        }
        for (action in listOf(ProgramAction.PAUSE, ProgramAction.RESTART, ProgramAction.REPLACE)) {
            failure(ProgramFailure.INVALID_PROGRESS) { repository.act(action, expected(0, 3), true, replacement) }
            assertEquals(before.header, repository.getHeader())
        }
        assertNull(db.workoutSessionDao().getSessionWithDetails("repeated-slot"))
        assertNull(db.workoutSessionDao().getRecommendationRecord("repeated-slot"))
        assertEquals(SessionStatus.COMPLETED, db.workoutSessionDao().getSessionWithDetails("lost-completed")!!.session.status)
        assertNotNull(db.workoutSessionDao().getRecommendationRecord("lost-completed")!!.programAssociationDigest)
        // Header-only ordinary planning and manual entry remain independent of full program corruption.
        ordinary("ordinary-after-loss", before.header)
        assertNull(db.programDao().getProgramAssociation("ordinary-after-loss"))
        assertEquals(before.header, repository.getHeader())
    }
    @Test fun lostActiveAssociationRefusesReadActionsAndLifecycleWhileStartStillResumesWithoutWrites() = runBlocking {
        start(); reserve("lost-active")
        val before = repository.getHeader()
        db.openHelper.writableDatabase.execSQL("DELETE FROM program_session_associations WHERE sessionId='lost-active'")
        failure(ProgramFailure.INVALID_PROGRESS) { repository.get() }
        failure(ProgramFailure.ACTIVE_WORKOUT) { repository.act(ProgramAction.REPLACE, expected(0, 2), true, replacement) }
        val result = db.workoutSessionDao().insertWorkoutUnlessActive(session("ignored-lost"), emptyList(), emptyList(), "default_user", 0,
            expectedProgramHeader = ProgramHeader.absent("default_user"))
        assertEquals("lost-active", result.session.id)
        for (finish in listOf(true, false)) {
            try {
                if (finish) db.workoutSessionDao().completeWorkoutAndReadSummary("lost-active", 2_000, 10, ProgressCalculator())
                else db.workoutSessionDao().deleteActiveSession("lost-active")
                fail("Lost active membership must refuse lifecycle writes")
            } catch (_: IllegalArgumentException) { }
            assertEquals(before, repository.getHeader())
            assertEquals(SessionStatus.IN_PROGRESS, db.workoutSessionDao().getSessionWithDetails("lost-active")!!.session.status)
            assertNotNull(db.workoutSessionDao().getRecommendationRecord("lost-active")!!.programAssociationDigest)
        }
        assertNull(db.workoutSessionDao().getSessionWithDetails("ignored-lost"))
        assertNull(db.programDao().getProgramAssociation("lost-active"))
    }

    @Test fun lostHistoricalAssociationCannotBeHiddenByReplacementOrAbsentEnrollment() = runBlocking {
        start(); reserve("lost-old-run")
        db.workoutSessionDao().completeWorkoutAndReadSummary("lost-old-run", 2_000, 10, ProgressCalculator())
        repository.act(ProgramAction.RESTART, expected(0, 3), true, replacement)
        val before = repository.getHeader()
        db.openHelper.writableDatabase.execSQL("DELETE FROM program_session_associations WHERE sessionId='lost-old-run'")
        failure(ProgramFailure.INVALID_PROGRESS) { repository.get() }
        failure(ProgramFailure.INVALID_PROGRESS) {
            repository.act(ProgramAction.PAUSE, ProgramExpectation("default_user", 0, replacement, 4))
        }
        assertEquals(before, repository.getHeader())
        db.openHelper.writableDatabase.execSQL("DELETE FROM program_enrollments")
        assertEquals(ProgramHeader.absent("default_user"), repository.getHeader())
        failure(ProgramFailure.INVALID_PROGRESS) { repository.get() }
        failure(ProgramFailure.INVALID_PROGRESS) { start() }
        assertNull(db.programDao().getProgramEnrollment("default_user"))
    }

    private suspend fun rebind(sessionId: String) {
        val digest = db.programDao().getProgramAssociation(sessionId)!!.rawAssociationDigest()
        db.openHelper.writableDatabase.execSQL("UPDATE workout_recommendation_records SET programAssociationDigest=? WHERE sessionId=?",
            arrayOf(digest, sessionId))
    }

    private suspend fun start() { repository.act(ProgramAction.START, ProgramExpectation("default_user", 0, null, 0), newProgramId = run) }
    private suspend fun startFailureBecauseActive() { failure(ProgramFailure.ACTIVE_WORKOUT) { start() } }
    private suspend fun state() = repository.get() as ProgramReadState.Supported
    private fun expected(profileRevision: Long, revision: Long) = ProgramExpectation("default_user", profileRevision, run, revision)
    private suspend fun ordinary(id: String, header: ProgramHeader?) = db.workoutSessionDao().insertWorkoutUnlessActive(
        session(id), emptyList(), emptyList(), "default_user", 0, expectedProgramHeader = header)
    private suspend fun reserve(id: String, exercises: List<WorkoutExerciseEntity> = emptyList(), sets: List<WorkoutSetEntity> = emptyList()): wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets {
        val state = state()
        val snapshot = MultiWeekProgramPolicy.snapshot(state.enrollment, state.progress.nextPosition!!)
        val association = ProgramAssociation(id, "default_user", snapshot)
        val deload = db.deloadPreferencesDao().get("default_user")?.toDeloadPreferences()
        return db.workoutSessionDao().insertWorkoutUnlessActive(session(id), exercises, sets, "default_user", 0,
            recommendation(id, MultiWeekProgramPolicy.associationDigest(association)),
            expectedDeloadDecisionRevision = deload?.revision, acceptedDeloadOfferId = deload?.choice?.takeIf { it.status == DeloadChoiceStatus.ACCEPTED }?.offer?.id,
            expectedProgramHeader = state.header, programSession = snapshot)
    }
    private suspend fun failure(reason: ProgramFailure, action: suspend () -> Unit) {
        try { action(); fail("Expected $reason") } catch (e: ProgramException) { assertEquals(reason, e.failure) }
    }
    private fun session(id: String) = WorkoutSessionEntity(id, "Workout", 1_000, null, 30, 0, WeightUnit.LBS,
        SessionStatus.IN_PROGRESS, focusMusclesJson = "", notes = "")
    private fun profile() = UserProfileEntity("default_user", 0, "Crawler", FitnessGoal.BUILD_MUSCLE, ExperienceLevel.INTERMEDIATE,
        50, 2, "dumbbell", WeightUnit.LBS, "", "", onboardingCompleted = true)
    private fun recommendation(id: String, digest: String?) = WorkoutRecommendationRecordEntity(id, "WHOLE_PROGRAM_V2", "DURATION_V1", "VALID",
        false, null, 0, null, null, null, null, null, null, 0, "identity", "", RecommendationDoseAccountingPayload.encode(emptyList()), 1_000, digest)
}
