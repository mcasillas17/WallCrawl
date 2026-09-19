package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import wallcrawl.elopenmike.com.core.model.SetType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.ai.FakeWorkoutPlanner
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import wallcrawl.elopenmike.com.core.ai.ProgramValidationResult
import wallcrawl.elopenmike.com.core.ai.ProgramValidator
import wallcrawl.elopenmike.com.core.ai.TrainingPolicyNoGuidanceReason
import wallcrawl.elopenmike.com.core.ai.TrainingPolicyResult
import wallcrawl.elopenmike.com.core.ai.TrainingPolicyResultException
import wallcrawl.elopenmike.com.core.ai.TrainingProgramStateProvider
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.ai.acceptedMetadata
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineDeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.BundledExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationStore
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogStore
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

/**
 * The enabled production composition against real SQLite and the real bundled assets.
 *
 * The JVM lifecycle suite proves what the ViewModel decides; it cannot prove that anything
 * was written, because its repositories only record the arguments they were handed. These
 * cases run the same composition over `WallCrawlDatabase` and the shipped
 * `workout-guide/catalog.json`, so onboarding, the paired session-and-recommendation write,
 * completion, the next generation's credit, and export/restore are all read back out of the
 * database rather than asserted against a fake.
 */
@RunWith(AndroidJUnit4::class)
class ProductionPlannerPersistenceTest {
    @Test
    fun corruptRecommendationFailsHistoryAndExportBeforeOpeningTheDestination() = runBlocking<Unit> {
        profileRepository.saveProfile(onboardedProfile())
        val profile = profileRepository.getProfileOnce()
        val proposal = validatedPlan(contextBuilder.build())
        val session = workoutRepository.startWorkoutFromGenerated(
            proposal.workout, DISPLAY_NAME, DISPLAY_RATIONALE, profile, proposal.snapshot
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workout_recommendation_records SET doseAccounting = ? WHERE sessionId = ?",
            arrayOf("malformed", session.id)
        )
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { workoutRepository.getRecommendationRecords(listOf(session.id)) }
        }
        val backup = OfflineLocalDataBackupRepository(
            database.localDataBackupDao(), "test", 1L, catalogCommit = { null },
            localDataWriteGate = writeGate
        )
        var opened = false
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { backup.exportTo { opened = true; ByteArrayOutputStream() } }
        }
        assertThat(opened).isFalse()
        assertThat(database.workoutSessionDao().getRecommendationRecord(session.id)).isNotNull()
    }

    @Test
    fun defaultProductionCompositionRecordsRevisionZeroAndRefusesAConcurrentFirstRequest() = runBlocking<Unit> {
        profileRepository.saveProfile(onboardedProfile())
        val storedProfile = profileRepository.getProfileOnce()
        val generationContext = contextBuilder.build()
        assertThat(generationContext.deloadPreferences)
            .isEqualTo(wallcrawl.elopenmike.com.core.model.DeloadPreferences())
        val accepted = validatedPlan(generationContext)
        assertThat(accepted.snapshot.deloadDecisionRevision).isEqualTo(0L)
        assertThat(accepted.snapshot.acceptedDeloadOfferId).isNull()
        wallcrawl.elopenmike.com.core.database.repository.OfflineDeloadRepository(
            database.deloadPreferencesDao(), writeGate
        ).decide(wallcrawl.elopenmike.com.core.model.DeloadAction.REQUEST,
            storedProfile.revision, 0, null)

        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            runBlocking {
                workoutRepository.startWorkoutFromGenerated(
                    accepted.workout, DISPLAY_NAME, DISPLAY_RATIONALE, storedProfile, accepted.snapshot
                )
            }
        }
        assertThat(database.workoutSessionDao().getActiveSession()).isNull()
        assertThat(database.localDataBackupDao().selectRecommendationRecords()).isEmpty()
    }

    @Test
    fun schedulingReadsBeyondRecentEightAndSurvivesRoomArchiveAndFreshReconstruction() = runBlocking {
        val all = catalog.getAllExercises().first()
        val ids = setOf("dumbbell-bench-press", "dumbbell-shoulder-press", "cable-fly", "cable-lateral-raise")
        val profile = onboardedProfile().copy(
            daysPerWeek = 6, preferredDurationMinutes = 30,
            musclePriorities = mapOf(
                wallcrawl.elopenmike.com.core.model.StandardMuscles.SHOULDERS to
                    wallcrawl.elopenmike.com.core.model.PriorityLevel.HIGH
            ),
            excludedExerciseIds = all.map { it.id }.filterNot { it in ids }
        )
        profileRepository.saveProfile(profile)
        val today = fixedInstant.atZone(zone).toLocalDate()
        repeat(11) { index ->
            val id = "practice-$index"
            val completedAt = today.minusDays(if (index < 2) 4L - index else 0L)
                .atStartOfDay(zone).toInstant().toEpochMilli()
            val exerciseId = "$id-exercise"
            database.workoutSessionDao().insertWorkout(
                session = wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity(
                    id = id, name = "Practice", startedAtTimestamp = completedAt - 1,
                    completedAtTimestamp = completedAt, targetDurationMinutes = 30,
                    actualDurationMinutes = 1, status = SessionStatus.COMPLETED,
                    focusMusclesJson = "", notes = ""
                ),
                exercises = if (index >= 2) emptyList() else listOf(
                    wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity(
                        id = exerciseId, sessionId = id, exerciseId = "dumbbell-shoulder-press",
                        orderIndex = 0, exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 1, targetRepMin = 8, targetRepMax = 10, targetWeight = null, notes = ""
                    )
                ),
                sets = if (index >= 2) emptyList() else listOf(WorkoutSetEntity(
                    id = "$id-set", workoutExerciseId = exerciseId, setNumber = 1,
                    exerciseType = ExerciseType.WEIGHT_REPS, targetReps = 10, completedReps = null,
                    targetWeight = null, completedWeight = null, isCompleted = true,
                    rpe = null, rir = null, completedAtTimestamp = null, type = SetType.NORMAL
                ))
            )
        }
        fun freshBuilder() = WorkoutGenerationContextBuilder(
            profileRepository, workoutRepository, catalog, ExerciseFilter(), WorkoutHistoryAnalyzer(),
            plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
            trainingProgramStateProvider = TrainingProgramStateProvider(ledgerRepository),
            catalogVersion = { runBlocking { catalogStore.snapshot() }.catalogAttribution.commit },
            nowTimestamp = { fixedInstant.toEpochMilli() }, zoneId = { zone },
            deloadRepository = OfflineDeloadRepository(database.deloadPreferencesDao(), writeGate)
        )
        val before = freshBuilder().build()
        assertThat(before.recentWorkoutHistory).hasSize(8)
        assertThat(before.exerciseHistory).isEmpty()
        assertThat(before.schedulingEvidence?.practiceDates?.get("Shoulders")).hasSize(2)
        val accepted = validatedPlan(before)
        assertThat(accepted.workout.exercises.map { it.exerciseId }).containsExactly(
            "dumbbell-shoulder-press", "dumbbell-bench-press", "cable-lateral-raise"
        ).inOrder()
        assertThat(accepted.workout.rankingReasons).hasSize(2)
        workoutRepository.startWorkoutFromGenerated(accepted.workout, DISPLAY_NAME, DISPLAY_RATIONALE,
            profileRepository.getProfileOnce(), accepted.snapshot)
        // The new active attempt is a progression input. Compare the state actually
        // exported, not the earlier context from before this session existed.
        val beforeExport = freshBuilder().build()
        assertThat(wallcrawl.elopenmike.com.core.ai.RecommendationContextIdentity.of(beforeExport))
            .isNotEqualTo(wallcrawl.elopenmike.com.core.ai.RecommendationContextIdentity.of(before))
        val planBeforeExport = FakeWorkoutPlanner().generateWorkout(beforeExport)
        val backup = OfflineLocalDataBackupRepository(
            database.localDataBackupDao(), "test", 1L,
            catalogCommit = { runBlocking { catalogStore.snapshot() }.catalogAttribution.commit },
            localDataWriteGate = writeGate
        )
        val originalRows = database.localDataBackupDao().readAll()
        val bytes = ByteArrayOutputStream().also { backup.exportTo { it } }.toByteArray()
        backup.deleteAllLocalData() // This fixture owns only its in-memory database.
        backup.restoreFrom(ByteArrayInputStream(bytes))
        assertThat(database.localDataBackupDao().readAll()).isEqualTo(originalRows)
        val rebuilt = freshBuilder().build()
        assertThat(rebuilt.schedulingEvidence).isEqualTo(before.schedulingEvidence)
        assertThat(wallcrawl.elopenmike.com.core.ai.RecommendationContextIdentity.of(rebuilt))
            .isEqualTo(wallcrawl.elopenmike.com.core.ai.RecommendationContextIdentity.of(beforeExport))
        assertThat(FakeWorkoutPlanner().generateWorkout(rebuilt).copy(id = planBeforeExport.id))
            .isEqualTo(planBeforeExport)
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val writeGate = Mutex()
    private val zone: ZoneId = ZoneId.of("UTC")
    private val fixedInstant: Instant = Instant.now()

    private lateinit var database: WallCrawlDatabase
    private lateinit var catalogStore: WorkoutGuideCatalogStore
    private lateinit var catalog: BundledExerciseCatalog
    private lateinit var profileRepository: OfflineUserProfileRepository
    private lateinit var workoutRepository: OfflineWorkoutRepository
    private lateinit var ledgerRepository: OfflineWeeklyDoseLedgerRepository
    private lateinit var contextBuilder: WorkoutGenerationContextBuilder
    private lateinit var validator: ProgramValidator

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java)
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS)
            .build()
        catalogStore = WorkoutGuideCatalogStore(context.assets)
        catalog = BundledExerciseCatalog(catalogStore, ExerciseLocalizationStore(context.assets))
        profileRepository = OfflineUserProfileRepository(
            userProfileDao = database.userProfileDao(),
            localDataWriteGate = writeGate
        )
        workoutRepository = OfflineWorkoutRepository(
            sessionDao = database.workoutSessionDao(),
            setDao = database.workoutSetDao()
        )
        ledgerRepository = OfflineWeeklyDoseLedgerRepository(
            historyDao = database.completedWorkoutHistoryDao(),
            ledgerStateDao = database.weeklyDoseLedgerStateDao(),
            catalogSource = catalogStore,
            clock = Clock.fixed(fixedInstant, zone)
        )
        contextBuilder = WorkoutGenerationContextBuilder(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            exerciseCatalog = catalog,
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            // The production constant, as `DefaultAppContainer` injects it.
            plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
            trainingProgramStateProvider = TrainingProgramStateProvider(
                weeklyDoseLedgerRepository = ledgerRepository,
                zoneId = { zone }
            ),
            catalogVersion = { runBlocking { catalogStore.snapshot() }.catalogAttribution.commit },
            deloadRepository = OfflineDeloadRepository(database.deloadPreferencesDao(), writeGate)
        )
        validator = ProgramValidator(GeneratedWorkoutValidator(catalog))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun onboardingGenerationStartCompletionAndTheNextGenerationAreAllPersisted() = runBlocking {
        // Fresh install: nothing stored, and the bundled catalog is the audited one.
        assertThat(database.localDataBackupDao().isEmptyDestination()).isTrue()
        val acceptedIds = catalog.getAllExercises().first()
            .filter { it.acceptedMetadata() != null }
            .map(Exercise::id)
            .toSet()
        assertThat(acceptedIds).hasSize(182)
        assertThat(
            catalog.getAllExercises().first()
                .any { it.reviewedMetadata?.reviewState == ReviewState.APPROVED }
        ).isFalse()

        // Onboarding writes the profile, and it is read back from the database.
        profileRepository.saveProfile(onboardedProfile())
        val storedProfile = profileRepository.getProfileOnce()
        assertThat(storedProfile.onboardingCompleted).isTrue()
        assertThat(database.userProfileDao().observeProfile(UserProfile.DEFAULT_PROFILE_ID).first())
            .isNotNull()

        // Generation runs on the reviewed path over the stored profile.
        val generationContext = contextBuilder.build()
        assertThat(generationContext.automaticEligibilityResult).isNotNull()
        assertThat(generationContext.allowedExercises).isNotEmpty()
        assertThat(acceptedIds)
            .containsAtLeastElementsIn(generationContext.allowedExercises.map(Exercise::id))
        val accepted = validatedPlan(generationContext)

        // Start writes the session and its recommendation record in one transaction.
        val session = workoutRepository.startWorkoutFromGenerated(
            generated = accepted.workout,
            displayName = DISPLAY_NAME,
            displayRationale = DISPLAY_RATIONALE,
            userProfile = storedProfile,
            recommendation = accepted.snapshot
        )
        val storedRows = database.localDataBackupDao().readAll()
        assertThat(storedRows.sessions.map { it.id }).containsExactly(session.id)
        val storedRecord = storedRows.recommendationRecords.single()
        assertThat(storedRecord.sessionId).isEqualTo(session.id)
        assertThat(storedRecord.reviewedPathEnabled).isTrue()
        assertThat(storedRecord.trainingPolicyVersion).isEqualTo("STATE_BASED_DOSE_EFFORT_REST_V2")
        assertThat(storedRecord.ledgerPolicyVersion).isEqualTo("PRIMARY_ONLY_V1")
        assertThat(storedRecord.adaptationState).isNotNull()
        assertThat(storedRows.sets).hasSize(accepted.workout.exercises.sumOf { it.targetSets })

        // Completion is persisted, not simulated: every planned set is logged and the
        // session is closed through the repository.
        // Completion is persisted, not simulated. One set is logged — a session the user cut
        // short — so the week keeps allowance and the next generation is still expected to
        // plan. `aFullyCompletedSessionSpendsTheWeek` covers the opposite end.
        val firstSet = storedRows.sets.minBy { it.setNumber }
        val firstExercise = storedRows.sessionExercises.single { it.id == firstSet.workoutExerciseId }
        logSetAsCompleted(firstSet)
        workoutRepository.completeWorkout(sessionId = session.id, actualDurationMinutes = 30)
        val completed = checkNotNull(workoutRepository.getSessionById(session.id))
        assertThat(completed.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(completed.completedAtTimestamp).isNotNull()
        assertThat(workoutRepository.observeCompletedWorkoutCount().first()).isEqualTo(1)

        // The next generation reads that persisted history: the week now carries the set
        // just completed, credited to the accepted record that was trained.
        val nextContext = contextBuilder.build()
        assertThat(nextContext.completedWorkoutCount).isEqualTo(1)
        val ledger = checkNotNull(nextContext.trainingProgramState).weeklyLedger
        assertThat(ledger.creditedWorkSets).isEqualTo(1)
        assertThat(ledger.unattributedWorkSets).isEmpty()
        val trainedMuscle = requireNotNull(
            generationContext.allowedExercises
                .single { it.id == firstExercise.exerciseId }
                .acceptedMetadata()
        ).directPrimaryMuscle
        assertThat(ledger.directPrimarySets).containsExactly(trainedMuscle, 1)
        // And a second plan is produced against that partly spent week.
        assertThat(validatedPlan(nextContext).workout.exercises).isNotEmpty()
    }

    @Test
    fun aFullyCompletedSessionSpendsTheWeekAndTheNextGenerationSaysSo() = runBlocking {
        profileRepository.saveProfile(onboardedProfile())
        val generationContext = contextBuilder.build()
        val accepted = validatedPlan(generationContext)
        val session = workoutRepository.startWorkoutFromGenerated(
            generated = accepted.workout,
            displayName = DISPLAY_NAME,
            displayRationale = DISPLAY_RATIONALE,
            userProfile = profileRepository.getProfileOnce(),
            recommendation = accepted.snapshot
        )
        logEveryPlannedSetAsCompleted()
        workoutRepository.completeWorkout(sessionId = session.id, actualDurationMinutes = 45)

        val nextContext = contextBuilder.build()
        val ledger = checkNotNull(nextContext.trainingProgramState).weeklyLedger
        assertThat(ledger.creditedWorkSets)
            .isEqualTo(accepted.workout.exercises.sumOf { it.targetSets })

        // The refusal comes from the prescription policy reading that persisted week, and it
        // is the typed one `TodayViewModel` maps to the configured-allowance copy — not a
        // generic failure and not a plan written over a spent allowance.
        val refusal = runCatching { validatedPlan(nextContext) }.exceptionOrNull()
        assertThat(refusal).isInstanceOf(TrainingPolicyResultException::class.java)
        assertThat((refusal as TrainingPolicyResultException).result).isEqualTo(
            TrainingPolicyResult.NoGuidance(
                TrainingPolicyNoGuidanceReason.WEEKLY_DIRECT_PRIMARY_ALLOWANCE_EXHAUSTED
            )
        )
    }

    @Test
    fun exportAndRestorePreserveTheSessionAndItsRecommendationAndRebuildTheLedgerCache() =
        runBlocking {
            profileRepository.saveProfile(onboardedProfile())
            val generationContext = contextBuilder.build()
            val accepted = validatedPlan(generationContext)
            val session = workoutRepository.startWorkoutFromGenerated(
                generated = accepted.workout,
                displayName = DISPLAY_NAME,
                displayRationale = DISPLAY_RATIONALE,
                userProfile = profileRepository.getProfileOnce(),
                recommendation = accepted.snapshot
            )
            logEveryPlannedSetAsCompleted()
            workoutRepository.completeWorkout(session.id, actualDurationMinutes = 30)

            // Reading the week caches it, so the cache is genuinely populated before export.
            val beforeLedger = ledgerRepository.currentWeeklyLedger(
                profileId = UserProfile.DEFAULT_PROFILE_ID,
                zoneId = zone
            )
            assertThat(beforeLedger.creditedWorkSets).isGreaterThan(0)
            assertThat(cachedLedgerRowCount()).isEqualTo(1)

            val backupRepository = OfflineLocalDataBackupRepository(
                backupDao = database.localDataBackupDao(),
                appVersionName = "test",
                appVersionCode = 1L,
                catalogCommit = {
                    runBlocking { catalogStore.snapshot() }.catalogAttribution.commit
                },
                localDataWriteGate = writeGate
            )
            val beforeRows = database.localDataBackupDao().readAll()
            val document = ByteArrayOutputStream()
                .also { sink -> backupRepository.exportTo { sink } }
                .toByteArray()

            backupRepository.deleteAllLocalData()
            assertThat(database.localDataBackupDao().readAll().sessions).isEmpty()
            assertThat(cachedLedgerRowCount()).isEqualTo(0)

            val result = backupRepository.restoreFrom(ByteArrayInputStream(document))

            assertThat(result.sessionCount).isEqualTo(1)
            val afterRows = database.localDataBackupDao().readAll()
            assertThat(afterRows).isEqualTo(beforeRows)
            // Recommendation provenance cannot be rebuilt from history, so it has to survive.
            val restoredRecord = afterRows.recommendationRecords.single()
            assertThat(restoredRecord.sessionId).isEqualTo(session.id)
            assertThat(restoredRecord.reviewedPathEnabled).isTrue()
            assertThat(restoredRecord.contextIdentity)
                .isEqualTo(accepted.snapshot.contextIdentity)
            assertThat(restoredRecord.catalogVersion)
                .isEqualTo(accepted.snapshot.catalogVersion)

            // The derived ledger is not in the archive and is not restored; it is recomputed
            // from the restored history and matches what it was before the round trip.
            assertThat(cachedLedgerRowCount()).isEqualTo(0)
            val rebuilt = ledgerRepository.currentWeeklyLedger(
                profileId = UserProfile.DEFAULT_PROFILE_ID,
                zoneId = zone
            )
            assertThat(rebuilt).isEqualTo(beforeLedger)
            assertThat(cachedLedgerRowCount()).isEqualTo(1)
        }

    /**
     * Logs every persisted planned set exactly as the workout screen would.
     *
     * The outcome carries the completion timestamp the repository requires; a set marked
     * complete without one is refused, which is the contract this exercises rather than
     * works around.
     */
    private suspend fun logEveryPlannedSetAsCompleted() {
        database.localDataBackupDao().readAll().sets.forEach { set -> logSetAsCompleted(set) }
    }

    /**
     * Logs one persisted set exactly as the workout screen would.
     *
     * Each shape carries only the fields its own type accepts, and a weighted set carries only the fields its own type accepts, and a weighted set
     * carries the load the user would have entered: the planner prescribes none for a
     * profile with no confirmed starting load and no history, and the repository refuses a
     * completed weighted set without one.
     */
    private suspend fun logSetAsCompleted(set: WorkoutSetEntity) {
        val outcome = when (set.exerciseType) {
            ExerciseType.WEIGHT_REPS -> SetPerformanceInput(
                reps = set.targetReps ?: DEFAULT_LOGGED_REPS,
                weight = set.targetWeight ?: USER_ENTERED_LOAD,
                completedAtTimestamp = fixedInstant.toEpochMilli(),
                isCompleted = true
            )

            ExerciseType.BODYWEIGHT_REPS -> SetPerformanceInput(
                reps = set.targetReps ?: DEFAULT_LOGGED_REPS,
                completedAtTimestamp = fixedInstant.toEpochMilli(),
                isCompleted = true
            )

            ExerciseType.ASSISTED_BODYWEIGHT -> SetPerformanceInput(
                reps = set.targetReps ?: DEFAULT_LOGGED_REPS,
                assistanceWeight = set.targetAssistanceWeight ?: USER_ENTERED_LOAD,
                completedAtTimestamp = fixedInstant.toEpochMilli(),
                isCompleted = true
            )

            ExerciseType.DURATION -> SetPerformanceInput(
                durationSeconds = set.targetDurationSeconds ?: DEFAULT_LOGGED_SECONDS,
                completedAtTimestamp = fixedInstant.toEpochMilli(),
                isCompleted = true
            )

            ExerciseType.DISTANCE_DURATION -> SetPerformanceInput(
                durationSeconds = set.targetDurationSeconds ?: DEFAULT_LOGGED_SECONDS,
                distanceMeters = set.targetDistanceMeters ?: DEFAULT_LOGGED_METERS,
                completedAtTimestamp = fixedInstant.toEpochMilli(),
                isCompleted = true
            )
        }
        workoutRepository.logSetCompletion(setId = set.id, performance = outcome)
    }

    private suspend fun validatedPlan(
        generationContext: WorkoutGenerationContext
    ): ProgramValidationResult.Valid {
        val proposal = FakeWorkoutPlanner().generateWorkout(generationContext)
        val result = validator.validate(
            workout = proposal,
            context = generationContext,
            allowRepair = true
        )
        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
        return result as ProgramValidationResult.Valid
    }

    private suspend fun cachedLedgerRowCount(): Int {
        val week = TrainingWeek.containing(fixedInstant, zone)
        return database.weeklyDoseLedgerStateDao().findCachedLedger(
            profileId = UserProfile.DEFAULT_PROFILE_ID,
            policyVersion = CACHED_LEDGER_POLICY.name,
            weekStartEpochDay = week.startEpochDay,
            timeZoneId = zone.id
        )?.let { 1 } ?: 0
    }

    private fun onboardedProfile() = UserProfile(
        availableEquipment = StandardEquipment.FULL_GYM,
        movementCapabilities = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
        ),
        onboardingCompleted = true
    )

    private companion object {
        /** Already written in the reader's language when it reaches the repository. */
        const val DISPLAY_NAME = "Empuje · Hipertrofia"
        const val DISPLAY_RATIONALE = "Generado para Ganar músculo, con prioridad en Pecho."
        const val DEFAULT_LOGGED_REPS = 8
        const val DEFAULT_LOGGED_SECONDS = 45
        const val DEFAULT_LOGGED_METERS = 400.0

        /** What a user types into the load field; the planner prescribes none here. */
        const val USER_ENTERED_LOAD = 20.0
        val CACHED_LEDGER_POLICY = LedgerPolicyVersion.PRIMARY_ONLY_V1
    }
}
