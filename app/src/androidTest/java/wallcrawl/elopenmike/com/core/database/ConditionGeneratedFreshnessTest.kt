package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.ZoneId
import wallcrawl.elopenmike.com.core.ai.*
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.exercise.*
import wallcrawl.elopenmike.com.core.exercise.workoutguide.*
import wallcrawl.elopenmike.com.core.model.*

/** Fictional content; the ordinary completion deliberately misses every scheduled support ID. */
class ConditionGeneratedFreshnessTest {
    private val f = ConditionStartFixtures
    private val gate = Mutex()
    private val assessed = f.ordinary.copy(id = "push-up")
    private val exercises = listOf(f.ordinary, f.supportive, assessed)
    private val catalog = InMemoryExerciseCatalog(exercises)
    private val validator = ProgramValidator(GeneratedWorkoutValidator(catalog))
    private lateinit var db: WallCrawlDatabase
    private lateinit var profiles: OfflineUserProfileRepository
    private lateinit var workouts: OfflineWorkoutRepository
    private lateinit var programs: OfflineProgramRepository
    private lateinit var builder: WorkoutGenerationContextBuilder
    private var now = f.now
    private var afterValidation: (suspend () -> Unit)? = null

    @Before fun setup() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java).build()
        profiles = OfflineUserProfileRepository(db.userProfileDao(), gate)
        profiles.saveUserProfile(f.context().userProfile.copy(onboardingCompleted = true))
        programs = OfflineProgramRepository(db, gate)
        workouts = OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao(), localDataWriteGate = gate,
            conditionSupport = { f.context().conditionSupport!! }, exerciseCatalog = catalog,
            generationContext = { intent, index -> builder.build(intent, index) },
            generationContextInTransaction = { intent, index, read, support -> builder.buildInTransaction(intent, index, read, support) },
            validateGenerated = { workout, context -> validator.validate(workout, context).also {
                afterValidation?.also { afterValidation = null }?.invoke()
            } }, nowTimestamp = { now }, currentZone = { ZoneId.of("UTC") })
        val snapshot = WorkoutGuideCatalogSnapshot(exercises, emptyMap(),
            CatalogAttribution("fictional", "fixture-v1", "fictional", ExerciseAttribution("fictional", "", "", ""), 2, 0))
        val source = object : WorkoutGuideCatalogSource {
            override suspend fun snapshot() = snapshot
            override fun currentSnapshot() = snapshot
        }
        builder = WorkoutGenerationContextBuilder(profiles, workouts, catalog, ExerciseFilter(), WorkoutHistoryAnalyzer(),
            plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
            trainingProgramStateProvider = TrainingProgramStateProvider(OfflineWeeklyDoseLedgerRepository(
                db.completedWorkoutHistoryDao(), db.weeklyDoseLedgerStateDao(), source)),
            catalogVersion = { snapshot.catalogVersion }, nowTimestamp = { now }, zoneId = { ZoneId.of("UTC") },
            deloadRepository = OfflineDeloadRepository(db.deloadPreferencesDao(), gate), programRepository = programs,
            conditionSupport = { f.context().conditionSupport!! })
    }
    @After fun close() = db.close()

    @Test fun ordinaryCompletionAfterValidationRefusesGeneratedStartAtomically() = runBlocking { interleave(WorkoutPlanningIntent.ORDINARY) }
    @Test fun ordinaryCompletionAfterValidationRefusesProgramStartWithoutReservingSlot() = runBlocking { interleave(WorkoutPlanningIntent.PROGRAM) }

    @Test fun clockOnlyChangeWithRealHistoryReadersKeepsOriginalReceipt() = runBlocking<Unit> {
        val context = builder.build(regenerationIndex = 0)
        val plan = validator.validate(FakeWorkoutPlanner().generateWorkout(context), context) as ProgramValidationResult.Valid
        now += 1_000
        val session = workouts.startWorkoutFromGenerated(plan.workout, "A", "A", profiles.getProfileOnce(), plan.snapshot)
        assertEquals(plan.snapshot.conditionContext, session.conditionContext)
        assertEquals(f.now, session.conditionContext!!.consumedScheduleEvidence.first().checkedAtTimestamp)
        assertEquals(plan.snapshot.conditionContext!!.canonicalDigest,
            db.workoutSessionDao().getRecommendationRecord(session.id)!!.conditionContextDigest)
    }

    @Test fun reportedConditionAllowsOrdinaryManualAndProgramStartsWithTransactionalChecks() = runBlocking<Unit> {
        profiles.saveUserProfile(profiles.getProfileOnce().copy(activeConditions = emptyList(),
            reportedConditions = setOf("piriformis_syndrome"), trainingConstraints = setOf(TrainingConstraint.LOWER_BACK_SENSITIVE)))
        val profile = profiles.getProfileOnce()
        val planned = PlannedExercise(assessed.id, ExercisePrescription(assessed.type, 2, repRange = RepRange(8, 10)))
        val template = WorkoutTemplate(name = "manual", exercises = listOf(planned))
        val unassessed = template.copy(exercises = listOf(planned.copy(exerciseId = f.ordinary.id)))
        for (supplied in listOf(profile, profile.copy(reportedConditions = emptySet(), trainingConstraints = emptySet()))) {
            val failure = runCatching { workouts.startWorkoutFromTemplate(unassessed, supplied) }.exceptionOrNull()
            assertTrue(failure is ConditionStartRefused)
            assertNull(workouts.getActiveSessionOnce())
        }
        val manual = workouts.startWorkoutFromTemplate(template, profile)
        assertNull(manual.conditionContext)
        workouts.cancelWorkout(manual.id)
        for (intent in WorkoutPlanningIntent.entries) {
            if (intent == WorkoutPlanningIntent.PROGRAM) {
                programs.act(ProgramAction.START, ProgramExpectation(profile.id, profile.revision, null, 0),
                    newProgramId = "11111111-1111-1111-1111-111111111111")
            }
            val context = builder.build(intent, 0)
            val plan = validator.validate(FakeWorkoutPlanner().generateWorkout(context), context) as ProgramValidationResult.Valid
            assertEquals(listOf(assessed.id), plan.workout.exercises.map { it.exerciseId })
            assertNull(plan.snapshot.conditionContext)
            val session = workouts.startWorkoutFromGenerated(plan.workout, "A", "A", profile, plan.snapshot)
            assertEquals(listOf(assessed.id), session.exercises.map { it.exerciseId })
            assertEquals(intent == WorkoutPlanningIntent.PROGRAM, db.programDao().getProgramAssociation(session.id) != null)
            workouts.cancelWorkout(session.id)
        }
    }

    private suspend fun interleave(intent: WorkoutPlanningIntent) {
        // First satisfy support through the same public manual start/log/finish operations.
        val support = workouts.startWorkoutFromTemplate(WorkoutTemplate(name = "support", weightUnit = WeightUnit.KG,
            exercises = listOf(PlannedExercise(f.supportive.id, f.dose))), profiles.getProfileOnce())
        support.exercises.single().sets.forEach {
            workouts.logSetCompletion(it.id, SetPerformanceInput(isCompleted = true, durationSeconds = 20, completedAtTimestamp = now))
        }
        workouts.completeWorkout(support.id, 1)
        now += 1_000
        if (intent == WorkoutPlanningIntent.PROGRAM) {
            val profile = profiles.getProfileOnce()
            programs.act(ProgramAction.START, ProgramExpectation(profile.id, profile.revision, null, 0),
                newProgramId = "11111111-1111-1111-1111-111111111111")
        }
        val before = builder.build(intent, 0)
        assertTrue((before.conditionPlanning as ConditionPlanningResult.Ready).supportive.isEmpty())
        val validation = validator.validate(FakeWorkoutPlanner().generateWorkout(before), before)
        assertTrue("Valid fixture required: $validation", validation is ProgramValidationResult.Valid)
        val plan = validation as ProgramValidationResult.Valid
        var interveningId: String? = null
        val header = programs.getHeader()
        afterValidation = {
            val manual = workouts.startWorkoutFromTemplate(WorkoutTemplate(name = "ordinary", weightUnit = WeightUnit.KG,
                exercises = listOf(PlannedExercise(f.ordinary.id,
                    ExercisePrescription(ExerciseType.BODYWEIGHT_REPS, 2, repRange = RepRange(8, 10))))), profiles.getProfileOnce())
            interveningId = manual.id
            manual.exercises.single().sets.forEach {
                workouts.logSetCompletion(it.id, SetPerformanceInput(isCompleted = true, reps = 10, completedAtTimestamp = now))
            }
            workouts.completeWorkout(manual.id, 1)
            val after = builder.build(intent, 0)
            assertEquals(before.conditionHistory!!.sourceDigest, after.conditionHistory!!.sourceDigest)
            assertNotEquals(RecommendationContextIdentity.of(before), RecommendationContextIdentity.of(after))
            assertTrue(after.trainingProgramState!!.weeklyLedger.directPrimarySets.getValue(StandardMuscles.CHEST) >
                (before.trainingProgramState!!.weeklyLedger.directPrimarySets[StandardMuscles.CHEST] ?: 0))
        }
        val failure = runCatching { workouts.startWorkoutFromGenerated(plan.workout, "A", "A", profiles.getProfileOnce(), plan.snapshot) }.exceptionOrNull()
        assertTrue("Expected typed freshness refusal; got $failure", failure is ConditionStartRefused)
        assertEquals("changed-condition-facts", (failure as ConditionStartRefused).reasons.single().reason)
        assertNull(workouts.getActiveSessionOnce())
        assertEquals(SessionStatus.COMPLETED, workouts.getSessionById(interveningId!!)!!.status)
        assertEquals(2, db.localDataBackupDao().selectSessions().size)
        assertTrue(db.localDataBackupDao().selectRecommendationRecords().isEmpty())
        assertEquals(header, programs.getHeader())
        if (intent == WorkoutPlanningIntent.PROGRAM) {
            val state = programs.get() as ProgramReadState.Supported
            assertTrue(state.sessions.isEmpty())
            assertEquals(0, state.progress.completedSlots)
        }
    }
}
