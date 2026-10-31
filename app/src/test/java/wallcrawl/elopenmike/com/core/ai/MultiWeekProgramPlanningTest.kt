package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.database.repository.ProgramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MultiWeekProgramPlanningTest {
    private val exercises = PlannerFixtureContextFactory().bundledCatalogProjection().exercises
    private val catalog = InMemoryExerciseCatalog(exercises)
    private val profile = UserProfile(onboardingCompleted = true, daysPerWeek = 4)
    private val validator = ProgramValidator(GeneratedWorkoutValidator(catalog))

    @Test
    fun ordinaryAutomaticProposalRecordsExplicitEnrollmentAbsence() = runTest {
        val context = WorkoutGenerationContextBuilder(
            StaticUserProfileRepository(profile), StaticWorkoutRepository(emptyList()),
            catalog, ExerciseFilter(), WorkoutHistoryAnalyzer()
        ).build().copy(regenerationIndex = 0)
        val result = validator.validate(FakeWorkoutPlanner().generateWorkout(context), context) as ProgramValidationResult.Valid
        assertThat(result.snapshot.expectedProgramHeader).isEqualTo(ProgramHeader.absent(profile.id))
        assertThat(result.snapshot.programSession).isNull()
    }
    @Test
    fun programUsesCompletedSlotRatherThanLifetimeCountAndRegenerationNeverAdvancesIt() = runTest {
        val plain = baseContext()
        val first = programContext(plain.copy(completedWorkoutCount = 1), 0)
        val planner = FakeWorkoutPlanner()
        val expected = planner.generateWorkout(plain.copy(completedWorkoutCount = 0))
        assertThat(planner.generateWorkout(first).copy(id = expected.id)).isEqualTo(expected)
        assertThat(planner.generateWorkout(first.copy(completedWorkoutCount = 99)).copy(id = expected.id)).isEqualTo(expected)
        assertThat(planner.generateWorkout(first).copy(id = expected.id)).isEqualTo(expected)
        val varied = planner.generateWorkout(first.copy(regenerationIndex = 1))
        assertThat(varied.title.split).isNotEqualTo(expected.title.split)
        assertThat(first.programInput!!.position.slotOrdinal).isEqualTo(0)
        assertThat(first.programInput.sessions).isEmpty()
        val next = programContext(plain, 1)
        assertThat(planner.generateWorkout(next).title.split).isEqualTo(varied.title.split)
    }

    @Test
    fun implicitProgramVariationIsStableAcrossPassiveReadsAndOrdinaryCounterUse() = runTest {
        val planner = FakeWorkoutPlanner()
        val context = programContext(baseContext(), 0).copy(regenerationIndex = null)
        val first = planner.generateWorkout(context)
        planner.generateWorkout(baseContext().copy(regenerationIndex = null))
        val second = planner.generateWorkout(context)
        assertThat(second.copy(id = first.id)).isEqualTo(first)
        assertThat(first.generationIndex).isEqualTo(0)
    }

    @Test
    fun programValidatorBindsTheExactPreStartSnapshotAndRejectsStructuralTamperingWithoutRepair() = runTest {
        val context = programContext(baseContext(), 1)
        val plan = FakeWorkoutPlanner().generateWorkout(context)
        val result = validator.validate(plan, context) as ProgramValidationResult.Valid
        assertThat(result.snapshot.programSession).isEqualTo(ProgramSessionSnapshot(
            RUN_ID, 3, configuration(), ProgramPosition(1, 1, 1, 2), ProgramObjective.ESTABLISH_ROUTINE
        ))
        assertThat(result.snapshot.expectedProgramHeader).isEqualTo(context.programHeader)
        val input = context.programInput!!
        val changed = listOf(
            context.copy(planningIntent = WorkoutPlanningIntent.ORDINARY),
            context.copy(programInput = null),
            context.copy(programHeader = ProgramHeader.absent(profile.id)),
            context.copy(programHeader = context.programHeader.copy(revision = 4)),
            context.copy(programInput = input.copy(position = ProgramPosition(2, 1, 1, 3))),
            context.copy(programInput = input.copy(objective = ProgramObjective.CONTINUE_AND_RECORD)),
            context.copy(programInput = input.copy(sessions = emptyList())),
            context.copy(programInput = input.copy(enrollment = input.enrollment.copy(status = ProgramStatus.PAUSED))),
            context.copy(programInput = input.copy(enrollment = input.enrollment.copy(lifecyclePolicyVersion = "future"))),
            context.copy(userProfile = profile.copy(daysPerWeek = 3)),
            context.copy(trainingFrequencyDaysPerWeek = 3),
            context.copy(fitnessGoals = setOf(FitnessGoal.STRENGTH))
        )
        for (tampered in changed) {
            val rejected = validator.validate(plan, tampered, allowRepair = true)
            assertThat(rejected).isInstanceOf(ProgramValidationResult.Invalid::class.java)
            assertThat((rejected as ProgramValidationResult.Invalid).violations.map { it.detail }).isNotEmpty()
        }
    }

    @Test
    fun identityIncludesEveryConsumedHeaderAndProgramFactButNotGoalOrderOrVariation() = runTest {
        val original = programContext(baseContext(), 1)
        val input = original.programInput!!
        val enrollment = input.enrollment
        val config = enrollment.configuration
        val changed = listOf(
            original.copy(planningIntent = WorkoutPlanningIntent.ORDINARY),
            original.copy(programHeader = ProgramHeader.absent(profile.id)),
            original.copy(programHeader = original.programHeader.copy(profileId = "other")),
            original.copy(programHeader = original.programHeader.copy(programId = OTHER_RUN_ID)),
            original.copy(programHeader = original.programHeader.copy(revision = 4)),
            original.copy(programHeader = original.programHeader.copy(rawStorageFingerprint = "b".repeat(64))),
            original.copy(programInput = input.copy(enrollment = enrollment.copy(status = ProgramStatus.PAUSED))),
            original.copy(programInput = input.copy(enrollment = enrollment.copy(lifecyclePolicyVersion = "future"))),
            original.copy(programInput = input.copy(enrollment = enrollment.copy(configuration = config.copy(policyVersion = "future")))),
            original.copy(programInput = input.copy(enrollment = enrollment.copy(configuration = config.copy(primaryGoal = FitnessGoal.STRENGTH)))),
            original.copy(programInput = input.copy(enrollment = enrollment.copy(configuration = config.copy(goals = listOf(FitnessGoal.STRENGTH))))),
            original.copy(programInput = input.copy(enrollment = enrollment.copy(configuration = config.copy(sessionsPerWeek = 3)))),
            original.copy(programInput = input.copy(position = ProgramPosition(2, 1, 1, 3))),
            original.copy(programInput = input.copy(objective = ProgramObjective.CONTINUE_AND_RECORD))
        )
        for (change in changed) assertThat(RecommendationContextIdentity.of(change))
            .isNotEqualTo(RecommendationContextIdentity.of(original))
        assertThat(RecommendationContextIdentity.of(original.copy(regenerationIndex = 5)))
            .isEqualTo(RecommendationContextIdentity.of(original))
        val multiGoal = original.copy(programInput = input.copy(enrollment = enrollment.copy(
            configuration = config.copy(goals = listOf(FitnessGoal.BUILD_MUSCLE, FitnessGoal.STRENGTH)))))
        val reordered = multiGoal.copy(programInput = multiGoal.programInput!!.copy(enrollment =
            multiGoal.programInput.enrollment.copy(configuration = multiGoal.programInput.enrollment.configuration.copy(
                goals = listOf(FitnessGoal.STRENGTH, FitnessGoal.BUILD_MUSCLE)))))
        assertThat(RecommendationContextIdentity.of(reordered)).isEqualTo(RecommendationContextIdentity.of(multiGoal))
        val ordinary = baseContext()
        assertThat(RecommendationContextIdentity.of(ordinary.copy(programHeader = original.programHeader)))
            .isNotEqualTo(RecommendationContextIdentity.of(ordinary))
    }

    @Test
    fun ordinaryBuilderReadsOnlyOpaqueHeaderAndPropagatesReadErrors() = runTest {
        val header = ProgramHeader(profile.id, RUN_ID, 7, "c".repeat(64))
        val repository = object : ProgramRepository by ReadOnlyProgramRepository(ProgramReadState.Empty(header)) {
            override suspend fun get(profileId: String): ProgramReadState = error("Ordinary mode must not decode a program")
        }
        val context = builder(repository).build(regenerationIndex = 3)
        assertThat(context.programHeader).isEqualTo(header)
        assertThat(context.programInput).isNull()
        assertThat(context.regenerationIndex).isEqualTo(3)
        val result = validator.validate(FakeWorkoutPlanner().generateWorkout(context), context) as ProgramValidationResult.Valid
        assertThat(result.snapshot.programSession).isNull()
        assertThat(result.snapshot.expectedProgramHeader).isEqualTo(header)
        val broken = object : ProgramRepository by repository {
    override suspend fun getHeader(profileId: String): ProgramHeader = throw java.io.IOException("header unavailable")
        }
        try { builder(broken).build(); org.junit.Assert.fail("Read failure must propagate") }
        catch (expected: java.io.IOException) { assertThat(expected.message).isEqualTo("header unavailable") }
    }

    @Test
    fun programBuilderUsesCoherentProfileAndRejectsMissingUnsupportedMismatchedOrActiveState() = runTest {
        val context = programContext(baseContext(), 0)
        val input = context.programInput!!
        val freshProfile = profile.copy(revision = 8, preferredUnit = WeightUnit.LBS,
            availableEquipment = listOf(StandardEquipment.BODYWEIGHT))
        val state = ProgramReadState.Supported(context.programHeader, input.enrollment,
            ProgramProgress(0, input.position, null), input.sessions, freshProfile)
        val fresh = builder(ReadOnlyProgramRepository(state)).build(WorkoutPlanningIntent.PROGRAM, 4)
        assertThat(fresh.userProfile).isEqualTo(freshProfile)
        assertThat(fresh.availableEquipment).containsExactly(StandardEquipment.BODYWEIGHT)
        assertThat(fresh.preferredUnits).isEqualTo(WeightUnit.LBS)
        assertThat(fresh.programInput).isEqualTo(input)
        assertThat(fresh.regenerationIndex).isEqualTo(4)
        assertProgramFailure(ProgramFailure.MISSING_OR_NOT_ACTIVE) { builder().build(WorkoutPlanningIntent.PROGRAM) }
        assertProgramFailure(ProgramFailure.MISSING_OR_NOT_ACTIVE) {
            builder(ReadOnlyProgramRepository(ProgramReadState.Empty(ProgramHeader.absent(profile.id)))).build(WorkoutPlanningIntent.PROGRAM)
        }
        val unsupported = ProgramReadState.Unsupported(context.programHeader,
            ProgramRecordedFacts(RUN_ID, "future", "future", "future", emptyList(), 99), "ACTIVE", profile)
        assertProgramFailure(ProgramFailure.UNSUPPORTED_POLICY) {
            builder(ReadOnlyProgramRepository(unsupported)).build(WorkoutPlanningIntent.PROGRAM)
        }
        assertProgramFailure(ProgramFailure.PROFILE_MISMATCH) {
            builder(ReadOnlyProgramRepository(state.copy(profile = freshProfile.copy(daysPerWeek = 3))))
                .build(WorkoutPlanningIntent.PROGRAM)
        }
        assertProgramFailure(ProgramFailure.INVALID_PROGRESS) {
            builder(ReadOnlyProgramRepository(state.copy(progress = state.progress.copy(completedSlots = 1))))
                .build(WorkoutPlanningIntent.PROGRAM)
        }
        assertProgramFailure(ProgramFailure.MISSING_OR_NOT_ACTIVE) {
            builder(ReadOnlyProgramRepository(state.copy(enrollment = input.enrollment.copy(status = ProgramStatus.PAUSED))))
                .build(WorkoutPlanningIntent.PROGRAM)
        }
        assertProgramFailure(ProgramFailure.PROFILE_UNAVAILABLE) {
            builder(ReadOnlyProgramRepository(state.copy(profile = null))).build(WorkoutPlanningIntent.PROGRAM)
        }
        val activeSlot = ProgramSlotSession(ProgramAssociation("active", profile.id,
            MultiWeekProgramPolicy.snapshot(input.enrollment, input.position)), SessionStatus.IN_PROGRESS, null)
        val reserved = state.copy(header = state.header.copy(revision = 2),
            enrollment = state.enrollment.copy(revision = 2), sessions = listOf(activeSlot),
            progress = state.progress.copy(activeSessionId = "active"))
        assertProgramFailure(ProgramFailure.ACTIVE_WORKOUT) {
            builder(ReadOnlyProgramRepository(reserved)).build(WorkoutPlanningIntent.PROGRAM)
        }
    }

    private suspend fun assertProgramFailure(expected: ProgramFailure, operation: suspend () -> Unit) {
        try { operation(); org.junit.Assert.fail("Expected $expected") }
        catch (failure: ProgramException) { assertThat(failure.failure).isEqualTo(expected) }
    }

    private fun builder(repository: ProgramRepository? = null) = WorkoutGenerationContextBuilder(
        StaticUserProfileRepository(profile), StaticWorkoutRepository(emptyList()),
        catalog, ExerciseFilter(), WorkoutHistoryAnalyzer(), programRepository = repository
    )

    private suspend fun baseContext() = WorkoutGenerationContextBuilder(
        StaticUserProfileRepository(profile), StaticWorkoutRepository(emptyList()),
        catalog, ExerciseFilter(), WorkoutHistoryAnalyzer()
    ).build().copy(regenerationIndex = 0)

    private fun configuration() = MultiWeekProgramPolicy.configurationFromProfile(profile)

    private fun programContext(base: WorkoutGenerationContext, completed: Int): WorkoutGenerationContext {
        val enrollment = ProgramEnrollment(profile.id, RUN_ID, 2L * completed + 1, configuration(),
            ProgramStatus.ACTIVE, 0, 0)
        val sessions = (0 until completed).map { ordinal -> ProgramSlotSession(
            ProgramAssociation("slot-$ordinal", profile.id, MultiWeekProgramPolicy.snapshot(
                enrollment.copy(revision = 2L * ordinal + 1), MultiWeekProgramPolicy.position(configuration(), ordinal)
            )), SessionStatus.COMPLETED, 0
        ) }
        val position = MultiWeekProgramPolicy.position(configuration(), completed)
        return base.copy(planningIntent = WorkoutPlanningIntent.PROGRAM,
            programHeader = ProgramHeader(profile.id, RUN_ID, enrollment.revision, "a".repeat(64)),
            programInput = ProgramPlanningInput(enrollment, sessions, position, MultiWeekProgramPolicy.objective(position)))
    }

    private companion object {
        const val RUN_ID = "00000000-0000-0000-0000-000000000001"
        const val OTHER_RUN_ID = "00000000-0000-0000-0000-000000000002"
    }
}

internal class ReadOnlyProgramRepository(private val state: ProgramReadState) : ProgramRepository {
    override fun observeAssociation(sessionId: String): Flow<wallcrawl.elopenmike.com.core.model.ProgramHistoryAssociation?> = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun getHeader(profileId: String) = state.header
    override fun observeHeader(profileId: String): Flow<ProgramHeader> = flowOf(state.header)
    override suspend fun get(profileId: String) = state
    override fun observe(profileId: String): Flow<ProgramReadState> = flowOf(state)
    override suspend fun act(action: ProgramAction, expected: ProgramExpectation, confirmed: Boolean, newProgramId: String?) =
        error("Read-only program fixture")
}
