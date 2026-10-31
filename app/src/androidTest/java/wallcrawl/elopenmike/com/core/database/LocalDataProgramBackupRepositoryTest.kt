package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy
import wallcrawl.elopenmike.com.core.backup.*
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class LocalDataProgramBackupRepositoryTest {
    private lateinit var database: WallCrawlDatabase
    private lateinit var repository: OfflineLocalDataBackupRepository
    private val gate = Mutex()
    private val configuration = ProgramConfiguration(FitnessGoal.STRENGTH, listOf(FitnessGoal.STRENGTH), 2)
    private val oldId = "11111111-1111-1111-1111-111111111111"
    private val currentId = "22222222-2222-2222-2222-222222222222"

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java).build()
        repository = OfflineLocalDataBackupRepository(database.localDataBackupDao(), "test", 1, { "test" }, gate)
    }
    @After fun tearDown() { database.close() }

    @Test fun exportDeleteRestoreRetainsActiveReservationAndReplacedRunHistory() = runBlocking {
        seedPrograms()
        val before = database.localDataBackupDao().readAll()
        val document = export()
        val archive = LocalDataArchiveCodec.read(ByteArrayInputStream(document))
        val archivedEnrollment = requireNotNull(archive.snapshot.programEnrollment)
        val archivedProfile = requireNotNull(archive.snapshot.profile)
        assertEquals(currentId, archivedEnrollment.programId)
        assertEquals(5, archivedProfile.daysPerWeek)
        assertEquals(2, archivedEnrollment.configuration.sessionsPerWeek)
        assertEquals(setOf(FitnessGoal.STRENGTH, FitnessGoal.BUILD_MUSCLE), archivedProfile.goals)
        assertEquals(listOf(FitnessGoal.STRENGTH), archivedEnrollment.configuration.goals)
        assertEquals(setOf(oldId, currentId), archive.snapshot.programAssociations.map { it.snapshot.programId }.toSet())
        assertEquals(2, archive.snapshot.programAssociations.size)
        assertTrue(archive.snapshot.recommendationRecords.filter { it.sessionId in listOf("session-1", "session-4") }
            .all { it.programAssociationDigest != null })
        repository.deleteAllLocalData()
        assertNull(database.programDao().getProgramEnrollment(UserProfile.DEFAULT_PROFILE_ID))
        assertTrue(database.localDataBackupDao().readAll().programAssociations.isEmpty())
        assertTrue(repository.isRestoreAllowed())
        repository.restoreFrom(ByteArrayInputStream(document))
        assertEquals(before, database.localDataBackupDao().readAll())
        val state = OfflineProgramRepository(database, gate).get() as ProgramReadState.Supported
        assertEquals("session-4", state.progress.activeSessionId)
        assertEquals(0, state.progress.completedSlots)
        assertEquals(5, state.profile!!.daysPerWeek)
        assertEquals(configuration, state.enrollment.configuration)
        assertEquals(ProgramFailure.PROFILE_MISMATCH, state.profileFailure)
    }

    @Test fun managedEnrollmentSurvivesExportDeleteRestoreBeforeItsFirstWorkout() = runBlocking {
        val profile = LocalDataArchiveFixtures.profile()
        database.localDataBackupDao().insertProfile(profile.toUserProfileEntity())
        val programs = OfflineProgramRepository(database, gate)
        programs.act(ProgramAction.START, ProgramExpectation(profile.id, profile.revision, null, 0), newProgramId = currentId)
        val before = database.localDataBackupDao().readAll()
        val document = export()
        val snapshot = LocalDataArchiveCodec.read(ByteArrayInputStream(document)).snapshot
        assertEquals(currentId, snapshot.programEnrollment!!.programId)
        assertTrue(snapshot.sessions.isEmpty())
        assertTrue(snapshot.programAssociations.isEmpty())
        repository.deleteAllLocalData()
        repository.restoreFrom(ByteArrayInputStream(document))
        assertEquals(before, database.localDataBackupDao().readAll())
        val restored = programs.get() as ProgramReadState.Supported
        assertEquals(0, restored.progress.completedSlots)
        assertEquals(0, restored.progress.nextPosition!!.slotOrdinal)
    }

    @Test fun unsupportedRawEnrollmentAndHistoricalPolicyRefuseBeforeDestinationOpen() = runBlocking {
        seedPrograms()
        val dao = database.programDao()
        val known = dao.getProgramEnrollment(UserProfile.DEFAULT_PROFILE_ID)!!
        dao.upsertProgramEnrollment(known.copy(configurationPolicyVersion = "future"))
        assertEquals(ArchiveRejection.UNSUPPORTED_PROGRAM, refusedExport().rejection)
        assertEquals("future", dao.getProgramEnrollment(known.profileId)!!.configurationPolicyVersion)
        dao.upsertProgramEnrollment(known)
        val historical = dao.getProgramAssociation("session-1")!!
        database.localDataBackupDao().deleteProgramAssociations()
        dao.insertProgramAssociation(historical.copy(lifecyclePolicyVersion = "future"))
        assertEquals(ArchiveRejection.UNSUPPORTED_PROGRAM, refusedExport().rejection)
    }

    @Test fun malformedStoredProgramAndMissingBindingCannotExportAsEmptyHistory() = runBlocking {
        seedPrograms()
        val dao = database.programDao()
        val known = dao.getProgramEnrollment(UserProfile.DEFAULT_PROFILE_ID)!!
        dao.upsertProgramEnrollment(known.copy(goals = "bad|||goal"))
        assertEquals(ArchiveRejection.INCONSISTENT, refusedExport().rejection)
        dao.upsertProgramEnrollment(known)
        database.localDataBackupDao().deleteProgramAssociations()
        assertEquals(ArchiveRejection.INCONSISTENT, refusedExport().rejection)
    }

    @Test fun unknownRawStatusOfAssociatedSessionRefusesBeforeOpeningDestination() = runBlocking {
        seedPrograms()
        val sql = database.openHelper.writableDatabase
        sql.execSQL("UPDATE workout_sessions SET status = 'unknown' WHERE id = 'session-4'")
        assertEquals(ArchiveRejection.INCONSISTENT, refusedExport().rejection)
    }

    @Test fun unknownRawOriginOfAssociatedSessionRefusesBeforeOpeningDestination() = runBlocking {
        seedPrograms()
        val sql = database.openHelper.writableDatabase
        sql.execSQL("UPDATE workout_sessions SET origin = 'unknown' WHERE id = 'session-4'")
        assertEquals(ArchiveRejection.INCONSISTENT, refusedExport().rejection)
        sql.query("SELECT origin FROM workout_sessions WHERE id = 'session-4'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("unknown", cursor.getString(0))
        }
    }

    @Test fun orphanOwnedEnrollmentBlocksRestoreAndDeleteExplicitlyRemovesIt() = runBlocking {
        val dao = database.localDataBackupDao()
        database.programDao().upsertProgramEnrollment(enrollment().toEntity())
        assertFalse(repository.isRestoreAllowed())
        val empty = LocalDataArchiveFixtures.archive(snapshot = LocalDataSnapshot(null, emptyList(), emptyList()))
        val document = bytes(empty)
        val before = dao.readAll()
        try { repository.restoreFrom(ByteArrayInputStream(document)); fail("Must refuse nonempty destination") }
        catch (_: LocalDataRestoreRefusedException) { }
        assertEquals(before, dao.readAll())
        repository.deleteAllLocalData()
        assertTrue(repository.isRestoreAllowed())
        assertTrue(dao.readAll().programEnrollments.isEmpty())
    }

    @Test fun earlierArchivesNeverFabricateProgramRows() = runBlocking {
        (1..4).forEach { version ->
            val original = LocalDataArchiveFixtures.archive(metadata = LocalDataArchiveFixtures.metadata(archiveVersion = version),
                snapshot = LocalDataArchiveFixtures.snapshot(recommendationRecords = emptyList()))
            repository.restoreFrom(ByteArrayInputStream(bytes(original)))
            val rows = database.localDataBackupDao().readAll()
            assertTrue(rows.programEnrollments.isEmpty())
            assertTrue(rows.programAssociations.isEmpty())
            assertTrue(rows.recommendationRecords.all { it.programAssociationDigest == null })
            repository.deleteAllLocalData()
        }
    }

    @Test fun invalidProgramDocumentAndChecksumLeaveBootstrapDestinationUntouched() = runBlocking {
        seedPrograms()
        val valid = export().decodeToString()
        repository.deleteAllLocalData()
        database.localDataBackupDao().insertProfile(LocalDataArchiveFixtures.profile().copy(onboardingCompleted = false).toUserProfileEntity())
        val before = database.localDataBackupDao().readAll()
        listOf(
            valid.replace("\"slotOrdinal\":0", "\"slotOrdinal\":1"),
            valid.replace("\"blockNumber\":1", "\"blockNumber\":2"),
            valid.replace("\"sessionsPerWeek\":2", "\"sessionsPerWeek\":3"),
            valid.replace("\"programAssociationDigest\":\"", "\"programAssociationDigest\":\"a"),
            valid.replace("\"programAssociations\":", "\"lostProgramAssociations\":"),
            valid.replace("\"appVersionName\":\"test\"", "\"appVersionName\":\"changed\"")
        ).forEach { changed ->
            assertNotEquals(valid, changed)
            try { repository.restoreFrom(ByteArrayInputStream(changed.encodeToByteArray())); fail("Must refuse invalid document") }
            catch (_: LocalDataArchiveException) { }
            assertEquals(before, database.localDataBackupDao().readAll())
        }
    }

    @Test fun missingExtraWrongRunAndWrongDigestRollbackBeforeChecksum() = runBlocking {
        seedPrograms()
        val valid = export().decodeToString()
        repository.deleteAllLocalData()
        val before = database.localDataBackupDao().readAll()
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { data -> data.getJSONArray("programAssociations").remove(0) },
            { data -> data.remove("programEnrollment") },
            { data -> data.getJSONArray("programAssociations").getJSONObject(0).put("programId", "33333333-3333-3333-3333-333333333333") },
            { data -> data.getJSONArray("recommendations").getJSONObject(0).put("programAssociationDigest", "b".repeat(64)) },
            { data ->
                val rows = data.getJSONArray("programAssociations")
                rows.put(JSONObject(rows.getJSONObject(0).toString()).put("sessionId", "session-2"))
            }
        )
        mutations.forEach { mutate ->
            val json = JSONObject(valid)
            mutate(json.getJSONObject("data"))
            try { repository.restoreFrom(ByteArrayInputStream(json.toString().encodeToByteArray())); fail("Must refuse broken relationship") }
            catch (error: LocalDataArchiveException) { assertEquals(ArchiveRejection.INCONSISTENT, error.rejection) }
            assertEquals(before, database.localDataBackupDao().readAll())
        }
    }

    @Test fun deletingDuringQueuedProgramManagementNeverResurrectsEnrollment() = runBlocking {
        val profile = LocalDataArchiveFixtures.profile()
        val programs = OfflineProgramRepository(database, gate)
        val expected = ProgramExpectation(profile.id, profile.revision, null, 0)
        listOf(true, false).forEach { deleteFirst ->
            database.localDataBackupDao().insertProfile(profile.toUserProfileEntity())
            suspend fun action(): ProgramFailure? = try {
                programs.act(ProgramAction.START, expected); null
            } catch (error: ProgramException) { error.failure }
            gate.lock()
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                if (deleteFirst) { repository.deleteAllLocalData(); null } else action()
            }
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                if (deleteFirst) action() else { repository.deleteAllLocalData(); null }
            }
            gate.unlock()
            val firstResult = first.await()
            val secondResult = second.await()
            assertEquals(if (deleteFirst) ProgramFailure.PROFILE_UNAVAILABLE else null,
                if (deleteFirst) secondResult else firstResult)
            assertTrue(database.localDataBackupDao().readAll().programEnrollments.isEmpty())
            assertTrue(database.localDataBackupDao().readAll().profiles.isEmpty())
        }
    }

    @Test fun databaseFailureAtAssociationInsertRollsBackEnrollmentAndAllEarlierRows() = runBlocking {
        seedPrograms()
        val dao = database.localDataBackupDao()
        val source = dao.readAll()
        repository.deleteAllLocalData()
        dao.insertProfile(LocalDataArchiveFixtures.profile().copy(onboardingCompleted = false).toUserProfileEntity())
        val before = dao.readAll()
        val badRows = source.copy(programAssociations = source.programAssociations.mapIndexed { index, row ->
            if (index == 1) row.copy(sessionId = "missing-parent") else row
        })
        try { dao.restoreIntoEmptyDestination(badRows); fail("Must roll back foreign key failure") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(before, dao.readAll())
    }

    @Test fun exportAndRestoreDocumentIoNeverHoldTheLocalDataGate() = runBlocking {
        seedPrograms()
        val sink = ByteArrayOutputStream()
        repository.exportTo { assertTrue(gate.tryLock()); gate.unlock(); sink }
        repository.deleteAllLocalData()
        val input = object : ByteArrayInputStream(sink.toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                assertTrue(gate.tryLock()); gate.unlock()
                return super.read(b, off, len)
            }
        }
        repository.restoreFrom(input)
        Unit
    }

    private fun enrollment() = ProgramEnrollment(UserProfile.DEFAULT_PROFILE_ID, currentId, 5, configuration,
        ProgramStatus.ACTIVE, 1000, 500)

    private suspend fun seedPrograms() {
        repository.restoreFrom(ByteArrayInputStream(bytes(LocalDataArchiveFixtures.archive())))
        val dao = database.localDataBackupDao()
        val old = ProgramAssociation("session-1", UserProfile.DEFAULT_PROFILE_ID, ProgramSessionSnapshot(oldId, 1,
            configuration, ProgramPosition(0, 1, 1, 1), ProgramObjective.ESTABLISH_ROUTINE))
        val active = old.copy(sessionId = "session-4", snapshot = old.snapshot.copy(programId = currentId, enrollmentRevision = 4))
        val records = dao.selectRecommendationRecords().map { it.toRecommendationRecord() }.toMutableList()
        records[0] = records[0].copy(programAssociationDigest = MultiWeekProgramPolicy.associationDigest(old))
        records += records[0].copy(sessionId = "session-4", programAssociationDigest = MultiWeekProgramPolicy.associationDigest(active))
        dao.deleteRecommendationRecords()
        dao.insertRecommendationRecords(records.map { it.toEntity() })
        database.programDao().upsertProgramEnrollment(enrollment().toEntity())
        database.programDao().insertProgramAssociation(old.toEntity())
        database.programDao().insertProgramAssociation(active.toEntity())
    }
    private suspend fun export() = ByteArrayOutputStream().also { repository.exportTo { it } }.toByteArray()
    private suspend fun refusedExport(): LocalDataArchiveException {
        var opened = false
        try { repository.exportTo { opened = true; ByteArrayOutputStream() }; fail("Must refuse export") }
        catch (error: LocalDataArchiveException) { assertFalse(opened); return error }
        error("unreachable")
    }
    private fun bytes(archive: LocalDataArchive) = ByteArrayOutputStream().also { LocalDataArchiveCodec.write(archive) { it } }.toByteArray()
}
