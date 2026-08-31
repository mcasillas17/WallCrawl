package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.repository.OfflineWeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogLoadException
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * The repository reconstructs a week from real persisted history and caches the result.
 *
 * These tests run against a real Room database on purpose: the whole point of the ledger is
 * that it can be rebuilt from what is actually stored, which a mocked DAO cannot prove.
 */
@RunWith(AndroidJUnit4::class)
class WeeklyDoseLedgerRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: WallCrawlDatabase
    private lateinit var catalogSource: FakeCatalogSource
    private var currentInstant: Instant = Instant.ofEpochMilli(MONDAY_UTC_MILLIS + HOUR_MILLIS)

    private val zone: ZoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java)
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS)
            .build()
        catalogSource = FakeCatalogSource(
            syntheticSnapshot(
                listOf(
                    syntheticCatalogExercise(
                        id = "synthetic-bench-press",
                        directPrimaryMuscle = "Chest",
                        descriptiveSecondaryMuscles = setOf("Triceps")
                    ),
                    syntheticCatalogExercise(
                        id = "synthetic-draft-squat",
                        directPrimaryMuscle = "Quadriceps",
                        reviewState = ReviewState.DRAFT
                    ),
                    syntheticCatalogExercise(
                        id = "synthetic-unreviewed",
                        directPrimaryMuscle = "Back",
                        reviewState = null
                    )
                )
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theWholeWeekIsReconstructedWithNoLimitOnHowManySessionsItContains(): Unit = runBlocking {
        repeat(12) { index ->
            insertCompletedSession(
                sessionId = "session-$index",
                completedAtEpochMillis = MONDAY_UTC_MILLIS + index * HOUR_MILLIS,
                exerciseId = "synthetic-bench-press",
                completedWorkSets = 2
            )
        }

        val ledger = repository().currentWeeklyLedger(PROFILE_ID, zone)

        assertThat(ledger.directPrimarySets).containsExactly("Chest", 24)
        assertThat(ledger.secondaryInvolvement).containsExactly("Triceps", 24)
        assertThat(ledger.policyVersion).isEqualTo(LedgerPolicyVersion.PRIMARY_ONLY_V1)
        assertThat(ledger.timeZoneId).isEqualTo("UTC")
        assertThat(ledger.catalogVersion).isEqualTo(SYNTHETIC_CATALOG_COMMIT)
        assertThat(ledger.reviewPolicyVersion).isEqualTo(1)
    }

    @Test
    fun onlyCompletedSessionsInsideTheWeekContributeExposure(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "in-week",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 1
        )
        insertCompletedSession(
            sessionId = "previous-week",
            completedAtEpochMillis = MONDAY_UTC_MILLIS - 1L,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 5
        )
        insertCompletedSession(
            sessionId = "next-week",
            completedAtEpochMillis = MONDAY_UTC_MILLIS + WEEK_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 5
        )
        insertCompletedSession(
            sessionId = "in-progress",
            completedAtEpochMillis = null,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 5,
            status = SessionStatus.IN_PROGRESS
        )
        insertCompletedSession(
            sessionId = "cancelled",
            completedAtEpochMillis = MONDAY_UTC_MILLIS + HOUR_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 5,
            status = SessionStatus.CANCELLED
        )

        val ledger = repository().currentWeeklyLedger(PROFILE_ID, zone)

        assertThat(ledger.directPrimarySets).containsExactly("Chest", 1)
    }

    @Test
    fun plannedWarmUpSkippedAndUnfinishedWorkIsNeverCountedAsExposure(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "mixed",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 1,
            warmUpSets = 3,
            unfinishedSets = 4,
            stoppedSets = 2
        )

        val ledger = repository().currentWeeklyLedger(PROFILE_ID, zone)

        assertThat(ledger.directPrimarySets).containsExactly("Chest", 1)
        assertThat(ledger.omittedWorkSets).isEqualTo(0)
    }

    @Test
    fun unknownAndUnapprovedExercisesAreOmittedWithTypedReasons(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "omissions",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-draft-squat",
            completedWorkSets = 3
        )
        insertCompletedSession(
            sessionId = "omissions-2",
            completedAtEpochMillis = MONDAY_UTC_MILLIS + HOUR_MILLIS,
            exerciseId = "synthetic-unreviewed",
            completedWorkSets = 2
        )
        insertCompletedSession(
            sessionId = "omissions-3",
            completedAtEpochMillis = MONDAY_UTC_MILLIS + 2 * HOUR_MILLIS,
            exerciseId = "not-in-the-catalog",
            completedWorkSets = 1
        )

        val ledger = repository().currentWeeklyLedger(PROFILE_ID, zone)

        assertThat(ledger.directPrimarySets).isEmpty()
        assertThat(ledger.secondaryInvolvement).isEmpty()
        assertThat(ledger.unattributedWorkSets).containsExactly(
            LedgerOmissionReason.UNKNOWN_EXERCISE, 1,
            LedgerOmissionReason.MISSING_REVIEWED_METADATA, 2,
            LedgerOmissionReason.METADATA_NOT_APPROVED, 3
        )
    }

    @Test
    fun anUnchangedWeekIsServedFromTheCacheWithoutRewritingIt(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "session-1",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 2
        )
        val repository = repository()

        val first = repository.currentWeeklyLedger(PROFILE_ID, zone)
        val cachedAfterFirst = cachedRow()
        currentInstant = currentInstant.plusMillis(HOUR_MILLIS)
        val second = repository.currentWeeklyLedger(PROFILE_ID, zone)

        assertThat(second).isEqualTo(first)
        assertThat(cachedRow()).isEqualTo(cachedAfterFirst)
        assertThat(checkNotNull(cachedAfterFirst).policyVersion)
            .isEqualTo(LedgerPolicyVersion.PRIMARY_ONLY_V1.name)
        assertThat(cachedAfterFirst.timeZoneId).isEqualTo("UTC")
        assertThat(cachedAfterFirst.sourceFingerprint).matches("[0-9a-f]{64}")
    }

    @Test
    fun newlyCompletedWorkInvalidatesTheCachedWeek(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "session-1",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 2
        )
        val repository = repository()
        val before = repository.currentWeeklyLedger(PROFILE_ID, zone)
        val fingerprintBefore = checkNotNull(cachedRow()).sourceFingerprint

        insertCompletedSession(
            sessionId = "session-2",
            completedAtEpochMillis = MONDAY_UTC_MILLIS + HOUR_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 3
        )
        val after = repository.currentWeeklyLedger(PROFILE_ID, zone)

        assertThat(before.directPrimarySets).containsExactly("Chest", 2)
        assertThat(after.directPrimarySets).containsExactly("Chest", 5)
        assertThat(checkNotNull(cachedRow()).sourceFingerprint).isNotEqualTo(fingerprintBefore)
    }

    @Test
    fun approvingMetadataInvalidatesTheCachedWeekInsteadOfKeepingStaleOmissions(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "session-1",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-draft-squat",
            completedWorkSets = 4
        )
        val repository = repository()
        val whileDraft = repository.currentWeeklyLedger(PROFILE_ID, zone)

        catalogSource.replaceExercises(
            listOf(
                syntheticCatalogExercise(
                    id = "synthetic-draft-squat",
                    directPrimaryMuscle = "Quadriceps",
                    reviewState = ReviewState.APPROVED
                )
            )
        )
        val afterApproval = repository.currentWeeklyLedger(PROFILE_ID, zone)

        assertThat(whileDraft.unattributedWorkSets)
            .containsExactly(LedgerOmissionReason.METADATA_NOT_APPROVED, 4)
        assertThat(afterApproval.directPrimarySets).containsExactly("Quadriceps", 4)
        assertThat(afterApproval.unattributedWorkSets).isEmpty()
    }

    @Test
    fun aNewCatalogVersionInvalidatesTheCachedWeek(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "session-1",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 1
        )
        val repository = repository()
        repository.currentWeeklyLedger(PROFILE_ID, zone)

        catalogSource.replaceExercises(
            exercises = listOf(
                syntheticCatalogExercise(
                    id = "synthetic-bench-press",
                    directPrimaryMuscle = "Chest",
                    descriptiveSecondaryMuscles = setOf("Triceps")
                )
            ),
            commit = "a-different-catalog-commit"
        )
        val afterUpgrade = repository.currentWeeklyLedger(PROFILE_ID, zone)

        assertThat(afterUpgrade.catalogVersion).isEqualTo("a-different-catalog-commit")
        assertThat(checkNotNull(cachedRow()).catalogVersion).isEqualTo("a-different-catalog-commit")
    }

    @Test
    fun readingTheSameWeekInAnotherZoneCreatesItsOwnSnapshot(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "session-1",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 1
        )
        val repository = repository()

        val utc = repository.currentWeeklyLedger(PROFILE_ID, ZoneId.of("UTC"))
        val kolkata = repository.currentWeeklyLedger(PROFILE_ID, ZoneId.of("Asia/Kolkata"))

        assertThat(utc.timeZoneId).isEqualTo("UTC")
        assertThat(kolkata.timeZoneId).isEqualTo("Asia/Kolkata")
        assertThat(cachedRow(timeZoneId = "UTC")).isNotNull()
        assertThat(cachedRow(timeZoneId = "Asia/Kolkata")).isNotNull()
        // The UTC snapshot keeps its own counts rather than being relabelled.
        assertThat(checkNotNull(cachedRow(timeZoneId = "UTC")).timeZoneId).isEqualTo("UTC")
    }

    @Test
    fun aCorruptedOrDeletedCacheRecomputesTheIdenticalLedger(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "session-1",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 3
        )
        val repository = repository()
        val expected = repository.currentWeeklyLedger(PROFILE_ID, zone)

        database.openHelper.writableDatabase.execSQL(
            "UPDATE weekly_dose_ledger_state SET ledgerPayload = 'not a payload'"
        )
        assertThat(repository.currentWeeklyLedger(PROFILE_ID, zone)).isEqualTo(expected)

        database.openHelper.writableDatabase.execSQL("DELETE FROM weekly_dose_ledger_state")
        assertThat(repository.currentWeeklyLedger(PROFILE_ID, zone)).isEqualTo(expected)

        database.openHelper.writableDatabase.execSQL(
            "UPDATE weekly_dose_ledger_state SET sourceFingerprint = 'tampered'"
        )
        assertThat(repository.currentWeeklyLedger(PROFILE_ID, zone)).isEqualTo(expected)
        assertThat(checkNotNull(cachedRow()).sourceFingerprint).isNotEqualTo("tampered")
    }

    @Test
    fun completingWorkNeverIncrementsAPersistedLedgerValue(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "session-1",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 1
        )

        // Logging and completing work writes history only: nothing touches the cache until
        // a caller asks for a ledger, and then it is recomputed rather than incremented.
        assertThat(ledgerRowCount()).isEqualTo(0)
        val repository = repository()
        repository.currentWeeklyLedger(PROFILE_ID, zone)
        assertThat(ledgerRowCount()).isEqualTo(1)

        insertCompletedSession(
            sessionId = "session-2",
            completedAtEpochMillis = MONDAY_UTC_MILLIS + HOUR_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 1
        )
        assertThat(checkNotNull(cachedRow()).ledgerPayload).contains("primary\tChest\t1")
        assertThat(repository.currentWeeklyLedger(PROFILE_ID, zone).directPrimarySets)
            .containsExactly("Chest", 2)
        assertThat(ledgerRowCount()).isEqualTo(1)
    }

    @Test
    fun aGenuinelyEmptyWeekIsAnExplicitEmptyLedger(): Unit = runBlocking {
        val ledger = repository().currentWeeklyLedger(PROFILE_ID, zone)

        assertThat(ledger.directPrimarySets).isEmpty()
        assertThat(ledger.unattributedWorkSets).isEmpty()
        assertThat(ledger.weekStartEpochDay)
            .isEqualTo(TrainingWeek.containing(currentInstant, zone).startEpochDay)
    }

    @Test
    fun aCatalogFailureIsReportedInsteadOfBecomingAnEmptyLedger(): Unit = runBlocking {
        insertCompletedSession(
            sessionId = "session-1",
            completedAtEpochMillis = MONDAY_UTC_MILLIS,
            exerciseId = "synthetic-bench-press",
            completedWorkSets = 1
        )
        catalogSource.failWith(
            WorkoutGuideCatalogLoadException(
                "The bundled Workout Guide catalog is invalid.",
                IllegalStateException("synthetic failure")
            )
        )

        assertThrows(WorkoutGuideCatalogLoadException::class.java) {
            runBlocking { repository().currentWeeklyLedger(PROFILE_ID, zone) }
        }
        assertThat(ledgerRowCount()).isEqualTo(0)
    }

    @Test
    fun aBlankProfileIdIsRejected(): Unit = runBlocking {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository().currentWeeklyLedger("  ", zone) }
        }

        assertThat(error).hasMessageThat().contains("profileId")
    }

    private fun repository() = OfflineWeeklyDoseLedgerRepository(
        historyDao = database.completedWorkoutHistoryDao(),
        ledgerStateDao = database.weeklyDoseLedgerStateDao(),
        catalogSource = catalogSource,
        clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = currentInstant
        }
    )

    private suspend fun insertCompletedSession(
        sessionId: String,
        completedAtEpochMillis: Long?,
        exerciseId: String,
        completedWorkSets: Int,
        warmUpSets: Int = 0,
        unfinishedSets: Int = 0,
        stoppedSets: Int = 0,
        status: SessionStatus = SessionStatus.COMPLETED
    ) {
        val exerciseInstanceId = "$sessionId-exercise"
        val sets = mutableListOf<WorkoutSetEntity>()
        var setNumber = 0
        repeat(warmUpSets) {
            sets += workoutSet(exerciseInstanceId, ++setNumber, SetType.WARMUP, isCompleted = true)
        }
        repeat(completedWorkSets) {
            sets += workoutSet(exerciseInstanceId, ++setNumber, SetType.NORMAL, isCompleted = true)
        }
        repeat(unfinishedSets) {
            sets += workoutSet(exerciseInstanceId, ++setNumber, SetType.NORMAL, isCompleted = false)
        }
        repeat(stoppedSets) {
            sets += workoutSet(
                exerciseInstanceId,
                ++setNumber,
                SetType.NORMAL,
                isCompleted = false,
                stopReason = SetStopReason.USER_SKIPPED
            )
        }

        database.workoutSessionDao().insertWorkout(
            session = WorkoutSessionEntity(
                id = sessionId,
                name = "Synthetic $sessionId",
                startedAtTimestamp = (completedAtEpochMillis ?: MONDAY_UTC_MILLIS) - 1_000L,
                completedAtTimestamp = completedAtEpochMillis,
                targetDurationMinutes = 45,
                actualDurationMinutes = 44,
                status = status,
                focusMusclesJson = "",
                notes = ""
            ),
            exercises = listOf(
                WorkoutExerciseEntity(
                    id = exerciseInstanceId,
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    targetSets = sets.size.coerceAtLeast(1),
                    targetRepMin = 8,
                    targetRepMax = 10,
                    targetWeight = 100.0,
                    notes = ""
                )
            ),
            sets = sets
        )
    }

    private fun workoutSet(
        exerciseInstanceId: String,
        setNumber: Int,
        type: SetType,
        isCompleted: Boolean,
        stopReason: SetStopReason? = null
    ) = WorkoutSetEntity(
        id = "$exerciseInstanceId-set-$setNumber",
        workoutExerciseId = exerciseInstanceId,
        setNumber = setNumber,
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetReps = 10,
        completedReps = if (isCompleted) 10 else null,
        targetWeight = 100.0,
        completedWeight = if (isCompleted) 100.0 else null,
        isCompleted = isCompleted,
        rpe = if (isCompleted) 8f else null,
        rir = if (isCompleted) 2 else null,
        feltManageable = if (isCompleted) true else null,
        completedAtTimestamp = if (isCompleted) MONDAY_UTC_MILLIS else null,
        stoppedAtTimestamp = if (stopReason != null) MONDAY_UTC_MILLIS else null,
        stopReason = stopReason,
        type = type
    )

    private suspend fun cachedRow(timeZoneId: String = "UTC") =
        database.weeklyDoseLedgerStateDao().findCachedLedger(
            profileId = PROFILE_ID,
            weekStartEpochDay = TrainingWeek
                .containing(currentInstant, ZoneId.of(timeZoneId))
                .startEpochDay,
            timeZoneId = timeZoneId,
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1.name
        )

    private fun ledgerRowCount(): Int =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM weekly_dose_ledger_state")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    private companion object {
        const val PROFILE_ID = UserProfile.DEFAULT_PROFILE_ID
        const val HOUR_MILLIS = 3_600_000L
        const val WEEK_MILLIS = 7L * 24L * HOUR_MILLIS

        /** Monday 2026-08-31T00:00:00Z. */
        const val MONDAY_UTC_MILLIS = 1_788_134_400_000L
    }
}
