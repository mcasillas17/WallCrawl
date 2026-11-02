package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.backup.LocalDataArchive
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveCodec
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveException
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFixtures
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFormat
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.model.ExerciseRole

@RunWith(AndroidJUnit4::class)
class ConditionPersistenceTest {
    private lateinit var database: WallCrawlDatabase
    private lateinit var repository: OfflineLocalDataBackupRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java
        ).build()
        repository = OfflineLocalDataBackupRepository(
            backupDao = database.localDataBackupDao(), appVersionName = "test", appVersionCode = 1L,
            catalogCommit = { "test-commit" }, localDataWriteGate = Mutex()
        )
    }

    @After fun tearDown() = database.close()

    private fun archive(conditions: Set<String>, version: Int = LocalDataArchiveFormat.ARCHIVE_VERSION): LocalDataArchive {
        val base = LocalDataArchiveFixtures.archive()
        val current = version == LocalDataArchiveFormat.ARCHIVE_VERSION
        return base.copy(
            metadata = base.metadata.copy(archiveVersion = version),
            snapshot = base.snapshot.copy(
                profile = base.snapshot.profile!!.copy(reportedConditions = conditions),
                recommendationRecords = if (version == 1) emptyList() else base.snapshot.recommendationRecords,
                sessions = if (!current) base.snapshot.sessions else base.snapshot.sessions.map { session ->
                    if (session.id != "session-4") session else session.copy(exercises = session.exercises.map {
                        it.copy(role = ExerciseRole.MOBILITY, prescription = it.prescription.copy(holdSeconds = 30))
                    })
                }
            )
        )
    }

    private fun bytes(archive: LocalDataArchive) =
        ByteArrayOutputStream().also { sink -> LocalDataArchiveCodec.write(archive) { sink } }.toByteArray()

    @Test fun archiveRoundTripKeepsConditionsAndRoles() = runBlocking<Unit> {
        val original = archive(setOf("low-back", "shoulder"))
        assertThat(LocalDataArchiveCodec.read(ByteArrayInputStream(bytes(original)))).isEqualTo(original)

        repository.restoreFrom(ByteArrayInputStream(bytes(original)))
        val profile = OfflineUserProfileRepository(database.userProfileDao()).getProfileOnce()
        assertThat(profile.reportedConditions).containsExactly("low-back", "shoulder")
        val exercise = database.workoutSessionDao().getSessionWithDetails("session-4")!!.exercisesWithSets.single()
        assertThat(exercise.exercise.role).isEqualTo(ExerciseRole.MOBILITY)
        assertThat(exercise.exercise.targetHoldSeconds).isEqualTo(30)
        // A second export of the restored database carries the same fields.
        val again = ByteArrayOutputStream().also { sink -> repository.exportTo { sink } }.toByteArray()
        val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(again))
        assertThat(restored.snapshot.profile!!.reportedConditions).containsExactly("low-back", "shoulder")
        assertThat(restored.snapshot.sessions.single { it.id == "session-4" }.exercises.single().role)
            .isEqualTo(ExerciseRole.MOBILITY)
    }

    @Test fun version6ArchiveRestoresWithEmptyConditionsAndMainRoles() = runBlocking<Unit> {
        val v6 = archive(emptySet(), version = 6)
        val read = LocalDataArchiveCodec.read(ByteArrayInputStream(bytes(v6)))
        assertThat(read.snapshot.profile!!.reportedConditions).isEmpty()
        assertThat(read.snapshot.sessions.flatMap { it.exercises }.map { it.role }.toSet())
            .containsExactly(ExerciseRole.MAIN)
        // A version 6 document may not carry the new field, and the writer refuses to drop it.
        assertThrows(IllegalArgumentException::class.java) { bytes(archive(setOf("low-back"), version = 6)) }
        val smuggled = bytes(v6).decodeToString().replaceFirst("\"returningAfterBreakWeeks\"",
            "\"reportedConditions\":[\"low-back\"],\"returningAfterBreakWeeks\"")
        assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(smuggled.toByteArray()))
        }
        repository.restoreFrom(ByteArrayInputStream(bytes(v6)))
        assertThat(OfflineUserProfileRepository(database.userProfileDao()).getProfileOnce().reportedConditions)
            .isEmpty()
    }

    @Test fun archiveKeepsUnknownConditionId() = runBlocking<Unit> {
        repository.restoreFrom(ByteArrayInputStream(bytes(archive(setOf("from-a-newer-app")))))
        assertThat(OfflineUserProfileRepository(database.userProfileDao()).getProfileOnce().reportedConditions)
            .containsExactly("from-a-newer-app")
    }

    @Test fun oversizedOrUnjoinableConditionListsAreRejected() {
        val many = (1..2_001).map { "c$it" }.toSet()
        assertThrows(Exception::class.java) { bytes(archive(many)) }
        val tooMany = bytes(archive(setOf("a"))).decodeToString().replaceFirst("\"a\"",
            many.joinToString(",") { "\"$it\"" })
        assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(tooMany.toByteArray()))
        }
        assertThrows(Exception::class.java) { bytes(archive(setOf("a|||b"))) }
    }

    @Test fun editingConditionsDoesNotChangeAnActiveSession() = runBlocking<Unit> {
        repository.restoreFrom(ByteArrayInputStream(bytes(archive(emptySet()))))
        val before = database.workoutSessionDao().getSessionWithDetails("session-4")!!
        val profiles = OfflineUserProfileRepository(database.userProfileDao())
        profiles.saveUserProfile(profiles.getProfileOnce().copy(reportedConditions = setOf("low-back")))
        assertThat(profiles.getProfileOnce().reportedConditions).containsExactly("low-back")
        assertThat(database.workoutSessionDao().getSessionWithDetails("session-4")).isEqualTo(before)
    }
}
