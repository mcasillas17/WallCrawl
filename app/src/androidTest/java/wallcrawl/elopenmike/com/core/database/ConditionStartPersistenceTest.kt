package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import wallcrawl.elopenmike.com.core.ai.*
import wallcrawl.elopenmike.com.core.database.entity.*
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class ConditionStartPersistenceTest {
    private val fixture = ConditionStartFixtures
    private val catalog = InMemoryExerciseCatalog(listOf(fixture.ordinary, fixture.supportive))
    private val validator = ProgramValidator(GeneratedWorkoutValidator(catalog))
    private lateinit var db: WallCrawlDatabase
    private lateinit var workouts: OfflineWorkoutRepository
    private lateinit var profiles: OfflineUserProfileRepository
    private var now = fixture.now
    private var support = fixture.context().conditionSupport!!
    private var afterValidation: (suspend () -> Unit)? = null
    @Before fun setup() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java).build()
        val gate = Mutex()
        profiles = OfflineUserProfileRepository(db.userProfileDao(), gate)
        profiles.saveUserProfile(fixture.context().userProfile)
        workouts = OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao(), localDataWriteGate = gate,
            conditionSupport = { support }, exerciseCatalog = catalog,
            generationContext = { intent, _ -> context(intent) }, validateGenerated = { workout, context ->
                validator.validate(workout, context).also { afterValidation?.also { afterValidation = null }?.invoke() }
            }, nowTimestamp = { now }, currentZone = { ZoneId.of("UTC") })
    }
    @After fun teardown() = db.close()
    private suspend fun context(intent: WorkoutPlanningIntent = WorkoutPlanningIntent.ORDINARY): WorkoutGenerationContext {
        val read = workouts.getConditionContext(UserProfile.DEFAULT_PROFILE_ID, support, now, ZoneId.of("UTC"), intent == WorkoutPlanningIntent.PROGRAM)
        val all = listOf(fixture.ordinary, fixture.supportive)
        val planning = ConditionPlanningPolicy().resolve(read.profile, all, all.associateBy { it.id }, support, read.history)
        val program = read.program as? ProgramReadState.Supported
        val input = program?.let { state ->
            val position = state.progress.nextPosition!!
            ProgramPlanningInput(state.enrollment, state.sessions, position, MultiWeekProgramPolicy.objective(position))
        }
        return WorkoutGenerationContext(userProfile = read.profile, allowedExercises = all, conditionPlanning = planning,
            conditionSupport = support, conditionHistory = read.history, regenerationIndex = 0,
            planningIntent = intent, programHeader = read.programHeader, programInput = input)
    }
    private suspend fun proposal(intent: WorkoutPlanningIntent = WorkoutPlanningIntent.ORDINARY): ProgramValidationResult.Valid {
        val context = context(intent)
        return validator.validate(FakeWorkoutPlanner().generateWorkout(context), context) as ProgramValidationResult.Valid
    }
    private suspend fun start(plan: ProgramValidationResult.Valid, snapshot: RecommendationSnapshot? = plan.snapshot) =
        workouts.startWorkoutFromGenerated(plan.workout, "FICTIONAL", "TEST", profiles.getProfileOnce(), snapshot)
    private suspend fun refused(block: suspend () -> Unit): ConditionStartRefused {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("Expected condition refusal, got $failure", failure is ConditionStartRefused)
        assertNull(workouts.getActiveSessionOnce())
        return failure as ConditionStartRefused
    }
    @Test fun generatedStartKeepsTheExactEarlierContextAndReceipt() = runBlocking<Unit> {
        val plan = proposal()
        val frozen = plan.snapshot.conditionContext!!
        now += 1_000
        val session = start(plan)
        assertEquals(frozen, session.conditionContext)
        assertEquals(frozen.canonicalDigest, db.workoutSessionDao().getRecommendationRecord(session.id)!!.conditionContextDigest)
        assertEquals(fixture.now, session.conditionContext!!.consumedScheduleEvidence.first().checkedAtTimestamp)
    }
    @Test fun generatedStartRequiresProvenanceAndRefusesEditedDose() = runBlocking<Unit> {
        val plan = proposal()
        refused { start(plan, null) }
        refused { start(plan, plan.snapshot.copy(conditionContext = null)) }
        val changed = plan.workout.copy(exercises = plan.workout.exercises.map {
            it.copy(prescription = it.prescription.copy(targetSets = 1))
        })
        refused { workouts.startWorkoutFromGenerated(changed, "test", "test", profiles.getProfileOnce(), plan.snapshot) }
    }
    @Test fun manualConcretePlanIsAuthorizedWithoutAutomaticReceiptAndInvalidWorkIsRefused() = runBlocking<Unit> {
        val plan = proposal()
        val template = WorkoutTemplate(name = "test", exercises = plan.workout.exercises, weightUnit = WeightUnit.KG)
        for (invalid in listOf(template.copy(exercises = template.exercises.map {
            it.copy(prescription = it.prescription.copy(targetSets = 1))
        }), template.copy(exercises = template.exercises.mapIndexed { index, exercise ->
            if (index == 0) exercise.copy(exerciseId = "unassessed") else exercise
        }))) refused { workouts.startWorkoutFromTemplate(invalid, profiles.getProfileOnce()) }
        val session = workouts.startWorkoutFromTemplate(template, profiles.getProfileOnce())
        assertNotNull(session.conditionContext)
        assertNull(db.workoutSessionDao().getRecommendationRecord(session.id))
        assertEquals(template.exercises.map { it.prescription }, session.exercises.map { it.prescription })
    }
    @Test fun changedSupportAndCalendarRefuseThePublishedPlan() = runBlocking<Unit> {
        val plan = proposal()
        support = fixture.context(supportContentSha256 = "d".repeat(64)).conditionSupport!!
        refused { start(plan) }
        support = fixture.context().conditionSupport!!
        now += 86_400_000
        refused { start(plan) }
    }
    @Test fun profileChangeAfterLastValidationCannotCommit() = runBlocking<Unit> {
        val plan = proposal()
        afterValidation = { profiles.updateThemePreference(ThemePreference.DARK) }
        assertEquals("stale-profile", refused { start(plan) }.reasons.single().reason)
    }
    @Test fun completedManualWorkAfterLastValidationCannotCommit() = runBlocking<Unit> {
        val plan = proposal()
        afterValidation = {
            now += 1_000
            performedSession(pain = false)
        }
        refused { start(plan) }
        assertEquals(SessionStatus.COMPLETED, workouts.getSessionById("intervening")!!.status)
        assertTrue(db.workoutSessionDao().getRecommendationRecords(listOf("intervening")).isEmpty())
    }
    @Test fun painStopAfterLastValidationCannotCommitAndItsMarkerSurvivesFinish() = runBlocking<Unit> {
        val plan = proposal()
        afterValidation = {
            now += 1_000
            performedSession(pain = true, frozen = plan.snapshot.conditionContext)
        }
        assertEquals("stale-profile", refused { start(plan) }.reasons.single().reason)
        assertEquals(1, db.conditionReassessmentDao().forProfile(UserProfile.DEFAULT_PROFILE_ID).size)
    }
    @Test fun changedPerformanceAtEqualTimestampIsStillAChangedSourceFact() = runBlocking<Unit> {
        performedSession(pain = false)
        val plan = proposal()
        afterValidation = {
            db.openHelper.writableDatabase.execSQL("UPDATE workout_sets SET completedDurationSeconds = 21 WHERE id = 'intervening-set'")
        }
        assertEquals("changed-condition-facts", refused { start(plan) }.reasons.single().reason)
    }
    @Test fun programStartKeepsConditionReceiptAndReservesOnlyAnAuthorizedSlot() = runBlocking<Unit> {
        profiles.saveUserProfile(profiles.getProfileOnce().copy(onboardingCompleted = true))
        val programs = OfflineProgramRepository(db, Mutex())
        val profile = profiles.getProfileOnce()
        programs.act(ProgramAction.START, ProgramExpectation(profile.id, profile.revision, null, 0),
            newProgramId = "11111111-1111-1111-1111-111111111111")
        val plan = proposal(WorkoutPlanningIntent.PROGRAM)
        assertNotNull(plan.snapshot.programSession)
        refused { start(plan, null) }
        val session = start(plan)
        assertNotNull(db.programDao().getProgramAssociation(session.id))
        assertEquals(plan.snapshot.conditionContext, session.conditionContext)
        workouts.cancelWorkout(session.id)
        assertEquals(0, (programs.get() as ProgramReadState.Supported).progress.completedSlots)
    }
    @Test fun manualBoundsUseTheConvertedLoadWithoutChangingTheTemplate() = runBlocking<Unit> {
        val weighted = fixture.ordinary.copy(type = ExerciseType.WEIGHT_REPS,
            reviewedMetadata = fixture.ordinary.reviewedMetadata!!.copy(prescriptionShape = PrescriptionShape.WEIGHT_REPS))
        val weightedSupport = fixture.context(ordinaryExercises = listOf(weighted),
            bounds = ConditionPrescriptionBounds(minLoadKg = 10.0, maxLoadKg = 10.0)).conditionSupport!!
        val repository = OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao(),
            conditionSupport = { weightedSupport }, exerciseCatalog = InMemoryExerciseCatalog(listOf(weighted, fixture.supportive)),
            nowTimestamp = { now }, currentZone = { ZoneId.of("UTC") })
        val template = WorkoutTemplate(name = "pounds", weightUnit = WeightUnit.LBS, exercises = listOf(
            PlannedExercise(weighted.id, ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, repRange = RepRange(8, 10), targetWeight = 22.05)),
            PlannedExercise(fixture.supportive.id, fixture.dose)))
        val session = repository.startWorkoutFromTemplate(template, profiles.getProfileOnce())
        assertEquals(10.0, session.exercises.first().prescription.targetWeight!!, 0.00001)
        assertEquals(22.05, template.exercises.first().prescription.targetWeight!!, 0.00001)
        assertNull(db.workoutSessionDao().getRecommendationRecord(session.id))
    }
    @Test fun scheduleBecomingDueAfterValidationRefusesEvenWhenSourceFactsAreUnchanged() = runBlocking<Unit> {
        now -= 23 * 3_600_000L
        performedSession(pain = false)
        now += 23 * 3_600_000L
        val before = context().conditionHistory!!.sourceDigest
        val plan = proposal()
        assertTrue(plan.workout.exercises.none { it.prescription.purpose == PrescriptionPurpose.SUPPORTIVE })
        afterValidation = { now += 3_600_000L }
        assertEquals("changed-condition-facts", refused { start(plan) }.reasons.single().reason)
        assertEquals(before, context().conditionHistory!!.sourceDigest)
    }

    private suspend fun performedSession(pain: Boolean, frozen: SessionConditionContext? = null) {
        db.workoutSessionDao().insertWorkout(WorkoutSessionEntity("intervening", "manual", now - 100, null, 30, 0,
            WeightUnit.KG, SessionStatus.IN_PROGRESS, origin = WorkoutOrigin.CUSTOM_TEMPLATE, focusMusclesJson = "", notes = "",
            conditionContextJson = frozen?.let(ConditionContextCodec::encodeSession)),
            listOf(WorkoutExerciseEntity("intervening-exercise", "intervening", fixture.supportive.id, 0, ExerciseType.DURATION,
                1, null, null, null, targetDurationSeconds = 20, notes = "", purpose = if (pain) PrescriptionPurpose.SUPPORTIVE.name else PrescriptionPurpose.ORDINARY.name)),
            listOf(WorkoutSetEntity("intervening-set", "intervening-exercise", 1, ExerciseType.DURATION,
                null, null, null, null, isCompleted = false, rpe = null, rir = null, type = SetType.NORMAL)))
        workouts.logSetCompletion("intervening-set", if (pain) SetPerformanceInput(isCompleted = false, stoppedAtTimestamp = now, stopReason = SetStopReason.PAIN_STOP)
            else SetPerformanceInput(isCompleted = true, durationSeconds = 20, completedAtTimestamp = now))
        db.workoutSessionDao().completeSessionIfActive("intervening", completedAt = now, actualDuration = 1)
    }
}

/** FICTIONAL SOFTWARE TEST DATA ONLY; no clinical guidance or release. */
internal object ConditionStartFixtures {
    val text = ConditionText("FICTIONAL TEST", "PRUEBA FICTICIA")
    val ordinary = Exercise("ordinary", name = "FICTIONAL", primaryMuscles = listOf(StandardMuscles.CHEST),
        type = ExerciseType.BODYWEIGHT_REPS, reviewedMetadata = ReviewedExerciseMetadata(
            ReviewState.APPROVED, StandardMuscles.CHEST, emptySet(), MovementPattern.HORIZONTAL_PUSH,
            ComplexityTier.FOUNDATIONAL, "fictional", PrescriptionShape.BODYWEIGHT_REPS,
            emptyList(), emptyList(), emptySet(), SupportRequirement.SUPPORTED, ImpactLevel.NONE,
            listOf(emptyList()), ReviewProvenance("FICTIONAL", "FICTIONAL TEST ONLY", 1, 1, 1), emptySet()))
    val supportive = Exercise("supportive", name = "FICTIONAL", primaryMuscles = listOf(StandardMuscles.CALVES),
        type = ExerciseType.DURATION, isStretch = true)
    val dose = ExercisePrescription(ExerciseType.DURATION, 2, targetDurationSeconds = 20,
        restSeconds = 17, purpose = PrescriptionPurpose.SUPPORTIVE)
    val now = Instant.parse("2026-11-04T12:00:00Z").toEpochMilli()

    fun context(
        ordinaryExercises: List<Exercise> = listOf(ordinary),
        supportExercise: Exercise = supportive,
        supportiveDose: ExercisePrescription = dose,
        bounds: ConditionPrescriptionBounds = ConditionPrescriptionBounds(minSets = 2, maxSets = 3),
        schedule: ConditionSchedule = ConditionSchedule(2, 24, text),
        profile: UserProfile = UserProfile(preferredUnit = WeightUnit.KG,
            activeConditions = listOf(SelectedCondition("fictional-condition", 1, emptyMap()))),
        exposures: List<ConditionExposure> = emptyList(),
        execution: String = "ordinary-execution",
        allowProgression: Boolean = false,
        supportContentSha256: String = "c".repeat(64)
    ): WorkoutGenerationContext {
        val rules = ordinaryExercises.map { exercise -> ConditionRule("rule-${exercise.id}",
            ConditionExerciseIdentity.from(exercise, "b".repeat(64)), ConditionRuleEffect.PERMIT_ORDINARY,
            execution, text, setOf("source"), bounds, allowProgression, null, null) } +
            ConditionRule("support-rule", ConditionExerciseIdentity.from(supportExercise, "b".repeat(64)),
                ConditionRuleEffect.PRESCRIBE_SUPPORTIVE, "support-execution", text, setOf("source"), null, false,
                supportiveDose, schedule, ConditionSupportiveRequirements(emptySet(), emptySet(), ImpactLevel.NONE, listOf(emptyList())))
        val record = ConditionSupportRecord("fictional-condition", "fictional-v1", 1, 1,
            ConditionPopulation.ADULT_NONOPERATIVE, text, text, emptyList(),
            listOf(ConditionApplicabilityCase("case", emptyMap(), rules)),
            listOf(ConditionSource("source", "https://example.invalid/fictional", "a".repeat(64))),
            ConditionResponsePolicy(1, listOf(ConditionInputDefinition("response", text, true, mapOf("stop" to text))), mapOf("response" to "stop"), mapOf("response" to "stop"), text), supportContentSha256,
            ConditionReleaseReview(ConditionReviewState.RELEASED, "FICTIONAL", "FICTIONAL", 1, "FICTIONAL", supportContentSha256, setOf("source")))
        val support = ConditionSupportCatalog("fictional-v1", mapOf(record.conditionId to record))
        val history = ConditionHistory(now, ZoneId.of("UTC"), now - 8 * 86_400_000L, true, exposures, emptyList())
        val all = ordinaryExercises + supportExercise
        val ready = ConditionPlanningPolicy().resolve(profile, all, all.associateBy { it.id }, support, history)
        check(ready is ConditionPlanningResult.Ready)
        return WorkoutGenerationContext(userProfile = profile, allowedExercises = all, conditionPlanning = ready,
            conditionSupport = support, conditionHistory = history, regenerationIndex = 0)
    }
}
