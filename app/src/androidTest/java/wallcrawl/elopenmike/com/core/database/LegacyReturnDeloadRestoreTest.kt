package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.ai.DeloadOfferPolicy
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import wallcrawl.elopenmike.com.core.ai.TrainingProgramStateProvider
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveCodec
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFixtures
import wallcrawl.elopenmike.com.core.backup.LocalDataSnapshot
import wallcrawl.elopenmike.com.core.database.repository.OfflineDeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.BundledExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationStore
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogStore
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.DeloadAction
import wallcrawl.elopenmike.com.core.model.DeloadChoiceStatus
import wallcrawl.elopenmike.com.core.model.DeloadPreferences
import wallcrawl.elopenmike.com.core.model.DeloadSource

/** Historical archive bounds are a read contract, not permission to widen profile editing. */
@RunWith(AndroidJUnit4::class)
class LegacyReturnDeloadRestoreTest {
    private val gate = Mutex()
    private val clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: WallCrawlDatabase
    private lateinit var backup: OfflineLocalDataBackupRepository
    private lateinit var profiles: OfflineUserProfileRepository
    private lateinit var deload: OfflineDeloadRepository
    private lateinit var builder: WorkoutGenerationContextBuilder

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        profiles = OfflineUserProfileRepository(database.userProfileDao(), gate)
        deload = OfflineDeloadRepository(database.deloadPreferencesDao(), gate, clock)
        backup = OfflineLocalDataBackupRepository(
            database.localDataBackupDao(), "legacy-return-test", 1, { null }, gate, clock::millis
        )
        val catalogStore = WorkoutGuideCatalogStore(context.assets)
        val catalog = BundledExerciseCatalog(catalogStore, ExerciseLocalizationStore(context.assets))
        val ledger = OfflineWeeklyDoseLedgerRepository(
            database.completedWorkoutHistoryDao(), database.weeklyDoseLedgerStateDao(), catalogStore, clock = clock
        )
        builder = WorkoutGenerationContextBuilder(
            userProfileRepository = profiles,
            workoutRepository = OfflineWorkoutRepository(database.workoutSessionDao(), database.workoutSetDao()),
            exerciseCatalog = catalog,
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
            trainingProgramStateProvider = TrainingProgramStateProvider(ledger, zoneId = { clock.zone }),
            catalogVersion = { catalogStore.currentSnapshot()?.catalogAttribution?.commit },
            nowTimestamp = clock::millis,
            zoneId = { clock.zone },
            deloadRepository = deload
        )
    }

    @After fun tearDown() { database.close() }

    @Test fun restored521WeekProfilesCanDeriveAndHandleReturningOffers() = runBlocking {
        verifySupportedRestore(521)
    }

    @Test fun restored5200WeekProfilesCanDeriveAndHandleReturningOffers() = runBlocking {
        verifySupportedRestore(5_200)
    }

    @Test fun interactiveProfileEditsKeepTheirExisting520WeekLimit() = runBlocking<Unit> {
        val profile = LocalDataArchiveFixtures.profile().copy(returningAfterBreakWeeks = 520)
        profiles.saveProfile(profile)
        for (weeks in listOf(521, 5_200)) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { profiles.saveProfile(profile.copy(returningAfterBreakWeeks = weeks)) }
            }
            assertEquals(520, profiles.getProfileOnce().returningAfterBreakWeeks)
        }
    }

    private suspend fun verifySupportedRestore(weeks: Int) {
        for (version in 1..4) {
            backup.deleteAllLocalData()
            val originalProfile = LocalDataArchiveFixtures.profile().copy(returningAfterBreakWeeks = weeks)
            val original = LocalDataArchiveFixtures.archive(
                metadata = LocalDataArchiveFixtures.metadata(archiveVersion = version),
                snapshot = LocalDataSnapshot(originalProfile, emptyList(), emptyList())
            )
            val bytes = ByteArrayOutputStream().also { sink ->
                LocalDataArchiveCodec.write(original) { sink }
            }.toByteArray()
            val decoded = LocalDataArchiveCodec.read(bytes.inputStream())
            assertEquals(original, decoded)
            assertArrayEquals(bytes, ByteArrayOutputStream().also { sink ->
                LocalDataArchiveCodec.write(decoded) { sink }
            }.toByteArray())

            assertTrue(backup.restoreFrom(bytes.inputStream()).onboardingCompleted)
            assertEquals(originalProfile, profiles.getProfileOnce())
            val context = builder.build()
            assertEquals(originalProfile, context.userProfile)
            assertEquals(AdaptationState.RETURNING, context.trainingProgramState!!.adaptationState)
            assertEquals(DeloadPreferences(), context.deloadPreferences)
            val offer = requireNotNull(DeloadOfferPolicy.offer(context.userProfile, context.deloadPreferences!!))
            assertEquals(DeloadSource.RETURNING, offer.source)
            assertEquals("${DeloadOfferPolicy.VERSION}:${originalProfile.id}:$weeks", offer.id)
            assertNull(database.deloadPreferencesDao().get(originalProfile.id))

            deload.decide(DeloadAction.ACCEPT, originalProfile.revision, 0, offer.id)
            val accepted = deload.get()
            assertEquals(DeloadChoiceStatus.ACCEPTED, accepted.choice!!.status)
            assertEquals(offer.id, accepted.lastHandledReturnKey)
            assertEquals(originalProfile, profiles.getProfileOnce())
            val acceptedArchive = ByteArrayOutputStream().also { sink -> backup.exportTo { sink } }.toByteArray()
            backup.deleteAllLocalData()
            backup.restoreFrom(acceptedArchive.inputStream())
            assertEquals(accepted, deload.get())
            val reconstructed = builder.build()
            assertEquals(accepted.choice, DeloadOfferPolicy.accepted(reconstructed.deloadPreferences!!))
            assertNull(DeloadOfferPolicy.offer(reconstructed.userProfile, reconstructed.deloadPreferences))

            deload.decide(DeloadAction.CANCEL, originalProfile.revision, accepted.revision, offer.id)
            val cancelled = deload.get()
            assertEquals(DeloadChoiceStatus.CANCELLED, cancelled.choice!!.status)
            assertNull(DeloadOfferPolicy.offer(profiles.getProfileOnce(), cancelled))
        }
    }
}
