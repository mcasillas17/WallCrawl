package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveCodec
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveException
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFixtures
import wallcrawl.elopenmike.com.core.backup.LocalDataRestoreRefusedException
import wallcrawl.elopenmike.com.core.database.entity.WeeklyDoseLedgerStateEntity
import wallcrawl.elopenmike.com.core.database.repository.LocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.hasRequiredEquipment

/**
 * Export, restore, and deletion against a real Room database.
 *
 * The transaction, cascade, and foreign-key claims in this feature can only be checked
 * against real SQLite, so these cases run here rather than against a fake.
 */
@RunWith(AndroidJUnit4::class)
class LocalDataBackupRepositoryTest {

    private lateinit var database: WallCrawlDatabase
    private lateinit var repository: LocalDataBackupRepository

    /** The gate production shares between the destructive path and ordinary writers. */
    private val writeGate = Mutex()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WallCrawlDatabase::class.java
        ).build()
        repository = OfflineLocalDataBackupRepository(
            backupDao = database.localDataBackupDao(),
            appVersionName = "test",
            appVersionCode = 1L,
            catalogCommit = { "test-commit" },
            localDataWriteGate = writeGate
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportDeleteImport_reproducesTheSameLogicalState() = runBlocking {
        seedFixtureData()
        val beforeRows = database.localDataBackupDao().readAll()

        val document = ByteArrayOutputStream().also { sink -> repository.exportTo { sink } }.toByteArray()
        repository.deleteAllLocalData()
        assertThat(database.localDataBackupDao().readAll().sessions).isEmpty()

        val result = repository.restoreFrom(ByteArrayInputStream(document))

        assertThat(result.sessionCount).isEqualTo(beforeRows.sessions.size)
        assertThat(result.templateCount).isEqualTo(beforeRows.templates.size)
        assertThat(result.onboardingCompleted).isTrue()
        val afterRows = database.localDataBackupDao().readAll()
        assertThat(afterRows).isEqualTo(beforeRows)
        // Recommendation provenance cannot be rebuilt from history, so the round trip has
        // to carry it rather than quietly dropping it on restore.
        assertThat(afterRows.recommendationRecords.map { it.sessionId })
            .containsExactly("session-1", "session-2")
        assertThat(afterRows.recommendationRecords.single { it.sessionId == "session-2" }.outcome)
            .isEqualTo("REPAIRED")
    }

    @Test
    fun restoreOldVersions_keepsTheNineteenItemInventoryWithoutBandDefaults() = runBlocking {
        listOf(1, 2).forEach { version ->
            val archive = LocalDataArchiveFixtures.archive(
                metadata = LocalDataArchiveFixtures.metadata(archiveVersion = version),
                snapshot = LocalDataArchiveFixtures.snapshot(
                    profile = LocalDataArchiveFixtures.profile().copy(availableEquipment = StandardEquipment.FULL_GYM),
                    recommendationRecords = emptyList()
                )
            )
            val document = ByteArrayOutputStream().also { sink ->
                LocalDataArchiveCodec.write(archive) { sink }
            }.toByteArray()
            repository.restoreFrom(ByteArrayInputStream(document))
            val saved = OfflineUserProfileRepository(database.userProfileDao()).getProfileOnce()
            assertThat(saved.availableEquipment).containsExactlyElementsIn(StandardEquipment.FULL_GYM).inOrder()
            repository.deleteAllLocalData()
        }
    }

    @Test
    fun explicitBandSetupsAndUnknownToken_surviveExportRestoreIntoProfileRepository() = runBlocking {
        val equipment = StandardEquipment.FULL_GYM + StandardEquipment.BAND_SETUPS + "Future band anchor"
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                profile = LocalDataArchiveFixtures.profile().copy(availableEquipment = equipment)
            )
        )
        val initial = ByteArrayOutputStream().also { sink ->
            LocalDataArchiveCodec.write(archive) { sink }
        }.toByteArray()
        repository.restoreFrom(ByteArrayInputStream(initial))
        val exported = ByteArrayOutputStream().also { sink -> repository.exportTo { sink } }.toByteArray()
        assertThat(LocalDataArchiveCodec.read(ByteArrayInputStream(exported)).snapshot.profile?.availableEquipment)
            .containsExactlyElementsIn(equipment).inOrder()
        repository.deleteAllLocalData()
        repository.restoreFrom(ByteArrayInputStream(exported))

        val saved = OfflineUserProfileRepository(database.userProfileDao()).getProfileOnce()
        assertThat(saved.availableEquipment).containsExactlyElementsIn(equipment).inOrder()
        val facePull = Exercise(
            id = "banded-face-pull", name = "Banded Face Pull",
            primaryMuscles = listOf("Back"), listedEquipment = listOf(StandardEquipment.RESISTANCE_BAND),
            type = ExerciseType.WEIGHT_REPS
        )
        assertThat(facePull.hasRequiredEquipment(saved.availableEquipment)).isTrue()
        assertThat(facePull.hasRequiredEquipment(saved.availableEquipment - StandardEquipment.BAND_SETUPS.toSet()))
            .isFalse()
    }

    @Test
    fun export_keepsTheActiveWorkoutAndItsOneActiveInvariant() = runBlocking {
        seedFixtureData()

        val document = ByteArrayOutputStream().also { sink -> repository.exportTo { sink } }.toByteArray()
        val archive = LocalDataArchiveCodec.read(ByteArrayInputStream(document))

        val active = archive.snapshot.sessions.filter { it.status == SessionStatus.IN_PROGRESS }
        assertThat(active).hasSize(1)
        assertThat(active.single().id).isEqualTo("session-4")

        repository.deleteAllLocalData()
        repository.restoreFrom(ByteArrayInputStream(document))
        assertThat(database.workoutSessionDao().getActiveSession()?.session?.id)
            .isEqualTo("session-4")
    }

    @Test
    fun restore_isRefusedWhenTheDestinationStillHoldsData() = runBlocking {
        seedFixtureData()
        val document = ByteArrayOutputStream().also { sink -> repository.exportTo { sink } }.toByteArray()

        assertThat(repository.isRestoreAllowed()).isFalse()
        assertThrows(LocalDataRestoreRefusedException::class.java) {
            runBlocking { repository.restoreFrom(ByteArrayInputStream(document)) }
        }

        // Nothing was replaced or merged: the existing history is untouched.
        assertThat(database.localDataBackupDao().countSessions()).isEqualTo(4)
    }

    @Test
    fun restore_isAllowedOverABootstrapProfileButNotACompletedOne() = runBlocking {
        val profileRepository = OfflineUserProfileRepository(database.userProfileDao())
        // Reading the profile before onboarding finishes creates the bootstrap row.
        profileRepository.getProfileOnce()
        assertThat(repository.isRestoreAllowed()).isTrue()

        profileRepository.saveUserProfile(
            UserProfile(onboardingCompleted = true)
        )
        assertThat(repository.isRestoreAllowed()).isFalse()
    }

    @Test
    fun restore_replacesTheBootstrapProfileRatherThanCollidingWithIt() = runBlocking {
        val document = exportedFixtureDocument()
        OfflineUserProfileRepository(database.userProfileDao()).getProfileOnce()

        repository.restoreFrom(ByteArrayInputStream(document))

        val profiles = database.localDataBackupDao().selectProfiles()
        assertThat(profiles).hasSize(1)
        assertThat(profiles.single().revision).isEqualTo(7L)
        assertThat(profiles.single().name).isEqualTo("Crawler")
    }

    @Test
    fun restore_writesNothingWhenTheArchiveIsRejected() = runBlocking {
        val damaged = String(exportedFixtureDocument(), Charsets.UTF_8)
            .replace("\"completedReps\":8", "\"completedReps\":88")

        assertThrows(LocalDataArchiveException::class.java) {
            runBlocking { repository.restoreFrom(damaged.byteInputStream()) }
        }

        val rows = database.localDataBackupDao().readAll()
        assertThat(rows.profiles).isEmpty()
        assertThat(rows.sessions).isEmpty()
        assertThat(rows.templates).isEmpty()
        assertThat(rows.sets).isEmpty()
    }

    @Test
    fun restore_clearsTheDerivedLedgerCacheInsteadOfTrustingIt() = runBlocking {
        val document = exportedFixtureDocument()
        database.weeklyDoseLedgerStateDao().upsertCachedLedger(staleLedgerRow())

        repository.restoreFrom(ByteArrayInputStream(document))

        val cached = database.weeklyDoseLedgerStateDao().findCachedLedger(
            profileId = UserProfile.DEFAULT_PROFILE_ID,
            weekStartEpochDay = 20_000L,
            timeZoneId = "UTC",
            policyVersion = "PRIMARY_ONLY_V1"
        )
        assertThat(cached).isNull()
    }

    @Test
    fun deleteAll_removesEveryUserOwnedRowAndTheDerivedCache() = runBlocking {
        seedFixtureData()
        database.weeklyDoseLedgerStateDao().upsertCachedLedger(staleLedgerRow())

        repository.deleteAllLocalData()

        val rows = database.localDataBackupDao().readAll()
        assertThat(rows.profiles).isEmpty()
        assertThat(rows.templates).isEmpty()
        assertThat(rows.templateExercises).isEmpty()
        assertThat(rows.sessions).isEmpty()
        assertThat(rows.sessionExercises).isEmpty()
        assertThat(rows.sets).isEmpty()
        assertThat(rows.recommendationRecords).isEmpty()
        assertThat(
            database.weeklyDoseLedgerStateDao().findCachedLedger(
                profileId = UserProfile.DEFAULT_PROFILE_ID,
                weekStartEpochDay = 20_000L,
                timeZoneId = "UTC",
                policyVersion = "PRIMARY_ONLY_V1"
            )
        ).isNull()
        assertThat(repository.isRestoreAllowed()).isTrue()
    }

    @Test
    fun deleteAll_isSafeToRunTwice() = runBlocking {
        seedFixtureData()

        repository.deleteAllLocalData()
        repository.deleteAllLocalData()

        assertThat(database.localDataBackupDao().countSessions()).isEqualTo(0)
    }

    @Test
    fun export_capturesAPointInTimeSnapshotOfParentsAndChildren() = runBlocking<Unit> {
        seedFixtureData()
        val workoutRepository = OfflineWorkoutRepository(
            sessionDao = database.workoutSessionDao(),
            setDao = database.workoutSetDao()
        )

        val document = ByteArrayOutputStream().also { sink -> repository.exportTo { sink } }.toByteArray()
        workoutRepository.logSetCompletion(
            setId = "session-4-set-2",
            performance = SetPerformanceInput(
                reps = 12,
                isCompleted = true,
                completedAtTimestamp = 1_700_500_900_000L
            )
        )

        val archive = LocalDataArchiveCodec.read(ByteArrayInputStream(document))
        val archivedSet = archive.snapshot.sessions
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .single { it.id == "session-4-set-2" }
        // The archive is the state at the moment it was read, not a later re-read.
        assertThat(archivedSet.isCompleted).isFalse()
        assertThat(database.workoutSetDao().getSetById("session-4-set-2")?.isCompleted).isTrue()

        // Every set in the archive still has the exercise it belongs to.
        val exerciseIds = archive.snapshot.sessions.flatMap { it.exercises }.map { it.id }.toSet()
        val parents = archive.snapshot.sessions
            .flatMap { it.exercises }
            .flatMap { it.sets }
            .map { it.workoutExerciseId }
            .toSet()
        assertThat(exerciseIds).containsAtLeastElementsIn(parents)
    }

    @Test
    fun exportDeleteImport_reproducesStateTheAppItselfWrote() = runBlocking {
        // The headline round trip, seeded through the production write paths rather than
        // through the archive, so the encodings only those paths produce are exercised too.
        val profileRepository = OfflineUserProfileRepository(
            userProfileDao = database.userProfileDao(),
            localDataWriteGate = writeGate
        )
        val templateRepository = OfflineWorkoutTemplateRepository(
            templateDao = database.workoutTemplateDao(),
            exerciseCatalog = InMemoryExerciseCatalog(CATALOG_EXERCISES),
            localDataWriteGate = writeGate
        )
        val workoutRepository = OfflineWorkoutRepository(
            sessionDao = database.workoutSessionDao(),
            setDao = database.workoutSetDao()
        )

        profileRepository.saveProfile(appWrittenProfile())
        profileRepository.updateThemePreference(
            wallcrawl.elopenmike.com.core.model.ThemePreference.LIGHT
        )
        val template = appWrittenTemplate()
        templateRepository.saveTemplate(template)
        val session = workoutRepository.startWorkoutFromTemplate(
            template = template,
            userProfile = profileRepository.getProfileOnce()
        )
        workoutRepository.logSetCompletion(
            setId = session.exercises.first().sets.first().id,
            performance = SetPerformanceInput(
                reps = 9,
                weight = 42.5,
                rpe = 8f,
                rir = 2,
                feltManageable = true,
                completedAtTimestamp = 1_700_600_000_000L,
                isCompleted = true
            )
        )

        val beforeRows = database.localDataBackupDao().readAll()
        val document = ByteArrayOutputStream().also { sink -> repository.exportTo { sink } }.toByteArray()
        repository.deleteAllLocalData()
        repository.restoreFrom(ByteArrayInputStream(document))

        assertThat(database.localDataBackupDao().readAll()).isEqualTo(beforeRows)
    }

    @Test
    fun deleteAll_cannotBeUndoneByAProfileWriteThatWasAlreadyInFlight() = runBlocking {
        val profileRepository = OfflineUserProfileRepository(
            userProfileDao = database.userProfileDao(),
            localDataWriteGate = writeGate
        )
        profileRepository.saveProfile(appWrittenProfile())

        // Both operations are launched together. Whichever order the gate grants them, the
        // deletion must be the last word: the shared gate makes each profile update one
        // atomic read-modify-write, so it can never read before a deletion and write after.
        coroutineScope {
            val write = async {
                repeat(20) {
                    profileRepository.updateThemePreference(
                        wallcrawl.elopenmike.com.core.model.ThemePreference.DARK
                    )
                }
            }
            val delete = async { repository.deleteAllLocalData() }
            write.await()
            delete.await()
        }

        // Asserted on exactly what the race left behind, with no second deletion to hide it.
        // A profile write that wins the gate after the deletion finds no row and starts from
        // the bootstrap default, so a row may exist — but the onboarded profile the user
        // erased must never come back, and the app must still accept a restore.
        val survivingProfile = database.localDataBackupDao().selectProfiles().singleOrNull()
        assertThat(survivingProfile?.onboardingCompleted ?: false).isFalse()
        assertThat(survivingProfile?.name ?: UserProfile().name).isEqualTo(UserProfile().name)
        assertThat(database.localDataBackupDao().countSessions()).isEqualTo(0)
        assertThat(database.localDataBackupDao().countTemplates()).isEqualTo(0)
        assertThat(repository.isRestoreAllowed()).isTrue()
    }

    private fun appWrittenProfile() = UserProfile(
        name = "Crawler",
        daysPerWeek = 4,
        preferredDurationMinutes = 55,
        availableEquipment = listOf(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL),
        onboardingCompleted = true,
        confirmedStartingLoads = mapOf("dumbbell-curl" to 12.5),
        movementCapabilities = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
        )
    )

    private fun appWrittenTemplate() = WorkoutTemplate(
        id = "app-written-template",
        name = "Arms",
        notes = "Written through the editor's repository",
        createdAtTimestamp = 1_700_000_000_000L,
        updatedAtTimestamp = 1_700_000_000_000L,
        exercises = listOf(
            PlannedExercise(
                exerciseId = "dumbbell-curl",
                prescription = ExercisePrescription(
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    targetSets = 2,
                    repRange = RepRange(8, 10),
                    targetWeight = 42.5,
                    restSeconds = 90
                ),
                notes = "Slow eccentric"
            )
        )
    )

    private suspend fun seedFixtureData() {
        val document = ByteArrayOutputStream().also {
            LocalDataArchiveCodec.write(LocalDataArchiveFixtures.archive()) { it }
        }.toByteArray()
        repository.restoreFrom(ByteArrayInputStream(document))
    }

    private fun exportedFixtureDocument(): ByteArray = ByteArrayOutputStream().also { sink ->
        LocalDataArchiveCodec.write(LocalDataArchiveFixtures.archive()) { sink }
    }.toByteArray()

    private fun staleLedgerRow() = WeeklyDoseLedgerStateEntity(
        profileId = UserProfile.DEFAULT_PROFILE_ID,
        weekStartEpochDay = 20_000L,
        timeZoneId = "UTC",
        policyVersion = "PRIMARY_ONLY_V1",
        catalogVersion = "stale",
        reviewPolicyVersion = 1,
        ledgerPayload = "{}",
        sourceFingerprint = "stale",
        generatedAtTimestamp = 1L
    )

    private companion object {
        val CATALOG_EXERCISES = listOf(
            Exercise(
                id = "dumbbell-curl",
                name = "Dumbbell Curl",
                primaryMuscles = listOf("Biceps"),
                listedEquipment = listOf("Dumbbell"),
                type = ExerciseType.WEIGHT_REPS
            )
        )
    }
}
