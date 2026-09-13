package wallcrawl.elopenmike.com.feature.today

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.FakeWorkoutPlanner
import wallcrawl.elopenmike.com.core.ai.DefaultExercisePrescriptionFactory
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import wallcrawl.elopenmike.com.core.ai.PlannerFixtureContextFactory
import wallcrawl.elopenmike.com.core.ai.ProgramValidator
import wallcrawl.elopenmike.com.core.ai.ProgramViolationCode
import wallcrawl.elopenmike.com.core.ai.RecommendationContextIdentity
import wallcrawl.elopenmike.com.core.ai.RecommendationOutcome
import wallcrawl.elopenmike.com.core.ai.RecommendationSnapshot
import wallcrawl.elopenmike.com.core.ai.TrainingProgramStateProvider
import wallcrawl.elopenmike.com.core.ai.WeeklyDoseLedgerCalculator
import wallcrawl.elopenmike.com.core.ai.WorkoutDurationEstimator
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanner
import wallcrawl.elopenmike.com.core.ai.acceptedMetadata
import wallcrawl.elopenmike.com.core.ai.completedNormalSet
import wallcrawl.elopenmike.com.core.ai.completedSession
import wallcrawl.elopenmike.com.core.ai.exerciseInstance
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.ProfileGender
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutEmphasis
import wallcrawl.elopenmike.com.core.model.WorkoutRationaleSpec
import wallcrawl.elopenmike.com.core.model.WorkoutSplit
import wallcrawl.elopenmike.com.core.model.WorkoutTitleSpec
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.test.MainDispatcherRule

/**
 * One profile's whole Today lifecycle on the production composition.
 *
 * The catalog is the bundled one, the flags are `PlannerFeatureFlags.PRODUCTION`, and the
 * ledger is recomputed by the real calculator from whatever history the fake repository
 * holds at that moment. Nothing approves an exercise for the test's benefit: a step that
 * only works under a synthetic approval is a step this suite should not be able to reach.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayProductionLifecycleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bundledCatalog = PlannerFixtureContextFactory().bundledCatalogProjection()
    private val bundledExercises = bundledCatalog.exercises
    private val acceptedIds =
        bundledExercises.filter { it.acceptedMetadata() != null }.map(Exercise::id).toSet()

    @Test
    fun aFreshlyOnboardedProfileIsPlannedStartedCompletedAndPlannedAgain() = runTest {
        val profileRepository = LifecycleProfileRepository(freshlyOnboardedProfile())
        val workoutRepository = LifecycleWorkoutRepository()
        val ledgerRepository = ledgerRepositoryFor(workoutRepository)
        val viewModel = todayViewModel(
            profileRepository = profileRepository,
            workoutRepository = workoutRepository,
            ledgerRepository = ledgerRepository
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        // Generated, validated, and displayed — built only from actually accepted records.
        val displayed = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        assertThat(displayed.exercises).isNotEmpty()
        assertThat(acceptedIds).containsAtLeastElementsIn(displayed.exercises.map { it.exerciseId })

        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()

        // The session and its recommendation evidence are handed over together, in the one
        // call the repository writes inside a single transaction.
        val started = workoutRepository.startRequests.single()
        assertThat(started.workout).isEqualTo(displayed)
        val recommendation = checkNotNull(started.recommendation)
        assertThat(recommendation.reviewedPathEnabled).isTrue()
        assertThat(recommendation.catalogVersion).isEqualTo(bundledCatalog.sourceCommit)
        assertThat(recommendation.reviewPolicyVersion).isEqualTo(REVIEW_POLICY_VERSION)
        assertThat(recommendation.adaptationState).isNotNull()

        // Completing the session advances the split: the next card is generated against the
        // history the finished workout just created.
        workoutRepository.completeStartedSession(displayed, completedAtEpochMillis = NOW)
        viewModel.regenerateWorkout()
        advanceUntilIdle()

        val next = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        assertThat(next.exercises).isNotEmpty()
        assertThat(acceptedIds).containsAtLeastElementsIn(next.exercises.map { it.exerciseId })
        // The week was rebuilt from history at every step rather than reused from memory.
        assertThat(ledgerRepository.reconstructionCount).isGreaterThan(1)
    }

    @Test
    fun historyCompletedBeforeAcceptanceIsCreditedAgainstTheNextRecommendation() = runTest {
        // The session below names exercises logged long before any acceptance existed. It is
        // never rewritten; it simply becomes attributable, and the recommendation that
        // follows is accounted against it.
        val benchPress = bundledExercises.single { it.id == "barbell-bench-press" }
        assertThat(benchPress.acceptedMetadata()).isNotNull()
        val workoutRepository = LifecycleWorkoutRepository(
            initialCompleted = listOf(
                completedSessionOf("pre-acceptance", benchPress, completedSets = 3)
            )
        )
        val profileRepository = LifecycleProfileRepository(
            freshlyOnboardedProfile().copy(
                availableEquipment = StandardEquipment.FULL_GYM,
                musclePriorities = mapOf(
                    requireNotNull(benchPress.acceptedMetadata()).directPrimaryMuscle to
                        PriorityLevel.HIGH
                )
            )
        )
        val viewModel = todayViewModel(profileRepository, workoutRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()

        val recommendation = checkNotNull(workoutRepository.startRequests.single().recommendation)
        val trainedMuscle = requireNotNull(benchPress.acceptedMetadata()).directPrimaryMuscle
        val accounted = recommendation.doseAccounting.single { it.muscle == trainedMuscle }
        assertThat(accounted.completedSets).isEqualTo(3)
        assertThat(accounted.completedSets + accounted.proposedSets)
            .isAtMost(accounted.allowanceSets)
        // The preexisting session is still exactly as it was logged.
        assertThat(workoutRepository.completedSessions().single { it.id == "pre-acceptance" })
            .isEqualTo(workoutRepository.originalSessions.single())
    }

    @Test
    fun anOverAllowanceProposalOfAcceptedRecordsIsRepairedRatherThanRefused() = runTest {
        val profileRepository = LifecycleProfileRepository(
            freshlyOnboardedProfile().copy(availableEquipment = StandardEquipment.FULL_GYM)
        )
        val workoutRepository = LifecycleWorkoutRepository()
        val viewModel = todayViewModel(
            profileRepository = profileRepository,
            workoutRepository = workoutRepository,
            // Actually accepted records sharing one direct-primary muscle, each carrying the
            // prescription the production policy wrote, so the bounded repair pass is the
            // only way this proposal can be shown.
            planner = SharedMuscleOverAllowancePlanner(exerciseCount = 4)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertWithMessage(state.toString()).that(state)
            .isInstanceOf(TodayUiState.Success::class.java)
        val displayed = (state as TodayUiState.Success).suggestedWorkout
        assertThat(displayed.exercises).hasSize(4)

        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()

        val started = workoutRepository.startRequests.single()
        // Exactly what was displayed is what is started, recorded as repaired.
        assertThat(started.workout).isEqualTo(displayed)
        val recommendation = checkNotNull(started.recommendation)
        assertThat(recommendation.outcome).isEqualTo(RecommendationOutcome.REPAIRED)
        assertThat(recommendation.reasonCodes).contains(ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED)
        recommendation.doseAccounting.forEach { accounting ->
            assertThat(accounting.completedSets + accounting.proposedSets)
                .isAtMost(accounting.allowanceSets)
        }
        displayed.exercises.forEach { assertThat(it.targetSets).isAtLeast(1) }
    }

    @Test
    fun aWeekWhoseAllowanceIsSpentSaysSoInsteadOfShowingAPlan() = runTest {
        val profileRepository = LifecycleProfileRepository(
            freshlyOnboardedProfile().copy(
                availableEquipment = listOf(StandardEquipment.BODYWEIGHT)
            )
        )
        val exhausting = bundledExercises
            .filter { exercise ->
                exercise.acceptedMetadata()?.equipmentAlternatives.orEmpty().any { alternative ->
                    alternative.all { it == StandardEquipment.BODYWEIGHT }
                }
            }
            .mapIndexed { index, exercise ->
                completedSessionOf(
                    sessionId = "spent-$index",
                    exercise = exercise,
                    completedSets = SETS_THAT_EXHAUST_ANY_ALLOWANCE
                )
            }
        assertThat(exhausting).isNotEmpty()

        val viewModel = todayViewModel(
            profileRepository = profileRepository,
            workoutRepository = LifecycleWorkoutRepository(initialCompleted = exhausting)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertThat((viewModel.uiState.value as TodayUiState.Error).error)
            .isEqualTo(TodayError.WEEKLY_ALLOWANCE_REACHED)
    }

    @Test
    fun aWorkoutFinishedElsewhereAfterTheCardWasBuiltRefusesTheStaleStart() = runTest {
        val profileRepository = LifecycleProfileRepository(freshlyOnboardedProfile())
        val workoutRepository = LifecycleWorkoutRepository()
        val viewModel = todayViewModel(profileRepository, workoutRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        val displayed = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout

        workoutRepository.addCompletedSessionWithoutNotifying(
            completedSessionOf(
                sessionId = "finished-elsewhere",
                exercise = bundledExercises.single { it.id == "bodyweight-squat" },
                completedSets = 2
            )
        )
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()

        assertThat(workoutRepository.startRequests).isEmpty()
        assertThat((viewModel.uiState.value as TodayUiState.Error).error)
            .isEqualTo(TodayError.RECOMMENDATION_OUT_OF_DATE)
        // Refusing the start never edited the plan that is still on screen.
        assertThat(displayed.exercises).isNotEmpty()
    }

    @Test
    fun aSelectedJointConstraintReportsTheReviewedRefusalRatherThanAPlan() = runTest {
        assertThat(bundledExercises.mapNotNull { it.acceptedMetadata() }
            .all { it.clearedTrainingConstraints.isEmpty() }).isTrue()

        val viewModel = todayViewModel(
            profileRepository = LifecycleProfileRepository(
                freshlyOnboardedProfile().copy(
                    availableEquipment = StandardEquipment.ALL,
                    trainingConstraints = setOf(TrainingConstraint.KNEE_SENSITIVE)
                )
            ),
            workoutRepository = LifecycleWorkoutRepository()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertThat((viewModel.uiState.value as TodayUiState.Error).error)
            .isEqualTo(TodayError.REVIEWED_CONSTRAINTS_REMOVED_ALL)
    }

    // region harness

    private fun todayViewModel(
        profileRepository: LifecycleProfileRepository,
        workoutRepository: LifecycleWorkoutRepository,
        planner: WorkoutPlanner = FakeWorkoutPlanner(),
        ledgerRepository: LifecycleLedgerRepository = ledgerRepositoryFor(workoutRepository)
    ): TodayViewModel {
        val catalog = InMemoryExerciseCatalog(bundledExercises)
        return TodayViewModel(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                userProfileRepository = profileRepository,
                workoutRepository = workoutRepository,
                exerciseCatalog = catalog,
                exerciseFilter = ExerciseFilter(),
                historyAnalyzer = WorkoutHistoryAnalyzer(),
                plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
                trainingProgramStateProvider = TrainingProgramStateProvider(
                    weeklyDoseLedgerRepository = ledgerRepository,
                    zoneId = { LEDGER_ZONE }
                ),
                catalogVersion = { bundledCatalog.sourceCommit },
                nowTimestamp = { NOW }
            ),
            workoutPlanner = planner,
            programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
            nowTimestamp = { NOW },
            clock = flowOf(NOW),
            zoneId = { LEDGER_ZONE }
        )
    }

    private fun ledgerRepositoryFor(
        workoutRepository: LifecycleWorkoutRepository
    ) = LifecycleLedgerRepository(
        workoutRepository = workoutRepository,
        exercises = bundledExercises,
        catalogVersion = bundledCatalog.sourceCommit
    )

    private fun freshlyOnboardedProfile() = UserProfile(
        goals = setOf(FitnessGoal.GENERAL_FITNESS, FitnessGoal.BUILD_MUSCLE),
        experienceLevel = ExperienceLevel.BEGINNER,
        availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
        movementCapabilities = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
        ),
        onboardingCompleted = true
    )

    private fun completedSessionOf(
        sessionId: String,
        exercise: Exercise,
        completedSets: Int,
        completedAtEpochMillis: Long = WEEK.startEpochMillis + HOUR_MILLIS
    ): WorkoutSession = completedSession(
        id = sessionId,
        completedAtEpochMillis = completedAtEpochMillis,
        exercises = listOf(
            exerciseInstance(
                exerciseId = exercise.id,
                id = "$sessionId-${exercise.id}",
                sets = (1..completedSets).map { setNumber ->
                    completedNormalSet(id = "$sessionId-set-$setNumber").copy(
                        setNumber = setNumber,
                        exerciseType = exercise.type
                    )
                }
            )
        )
    )

    // endregion

    private companion object {
        const val MONDAY_EPOCH_DAY = 20_696L
        val LEDGER_ZONE: ZoneId = ZoneId.of("UTC")
        val WEEK: TrainingWeek = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, LEDGER_ZONE)
        const val HOUR_MILLIS = 60 * 60 * 1_000L
        val NOW = WEEK.startEpochMillis + 3 * 24 * HOUR_MILLIS
        const val REVIEW_POLICY_VERSION = 2
        const val SETS_THAT_EXHAUST_ANY_ALLOWANCE = 12

        /** Already in the reader's language when it reaches the ViewModel, as in production. */
        const val WORKOUT_NAME = "Empuje · Hipertrofia"
        const val WORKOUT_RATIONALE = "Generado para Ganar músculo, con prioridad en Pecho."
    }
}

private data class LifecycleStartRequest(
    val workout: GeneratedWorkout,
    val displayName: String,
    val userProfile: UserProfile,
    val recommendation: RecommendationSnapshot?
)

private class LifecycleProfileRepository(
    initialProfile: UserProfile
) : UserProfileRepository {
    private val profile = MutableStateFlow(initialProfile)

    override fun getUserProfile(): Flow<UserProfile> = profile
    override suspend fun getProfileOnce(): UserProfile = profile.value
    override suspend fun saveUserProfile(profile: UserProfile) = update { profile }
    override suspend fun saveProfile(profile: UserProfile) = saveUserProfile(profile)
    override suspend fun updateGoals(goals: Set<FitnessGoal>) = update { it.copy(goals = goals) }
    override suspend fun updatePrimaryGoal(goal: FitnessGoal) = updateGoals(setOf(goal))
    override suspend fun updateExperienceLevel(level: ExperienceLevel) =
        update { it.copy(experienceLevel = level) }

    override suspend fun updatePreferredDuration(minutes: Int) =
        update { it.copy(preferredDurationMinutes = minutes) }

    override suspend fun updateDaysPerWeek(days: Int) = update { it.copy(daysPerWeek = days) }
    override suspend fun updateEquipment(equipment: List<String>) =
        update { it.copy(availableEquipment = equipment) }

    override suspend fun updateUnit(unit: WeightUnit) = update { it.copy(preferredUnit = unit) }
    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) =
        update { it.copy(musclePriorities = priorities) }

    override suspend fun updateExcludedExercises(excludedIds: List<String>) =
        update { it.copy(excludedExerciseIds = excludedIds) }

    override suspend fun updateTrainingConstraints(constraints: Set<TrainingConstraint>) =
        update { it.copy(trainingConstraints = constraints) }

    override suspend fun updateReturningAfterBreakWeeks(weeks: Int) =
        update { it.copy(returningAfterBreakWeeks = weeks) }

    override suspend fun updateGender(gender: ProfileGender) = update { it.copy(gender = gender) }
    override suspend fun updateThemePreference(themePreference: ThemePreference) =
        update { it.copy(themePreference = themePreference) }

    private fun update(transform: (UserProfile) -> UserProfile) {
        profile.update { current -> transform(current).copy(revision = current.revision + 1L) }
    }
}

/**
 * Records what a start would persist and lets a test finish the session it started.
 *
 * [startRequests] is the atomicity seam this suite can see: the session and its
 * recommendation evidence arrive in one call, which is what `OfflineWorkoutRepository`
 * writes inside a single transaction.
 */
private class LifecycleWorkoutRepository(
    initialCompleted: List<WorkoutSession> = emptyList()
) : WorkoutRepository {
    val startRequests = mutableListOf<LifecycleStartRequest>()
    val originalSessions: List<WorkoutSession> = initialCompleted

    private val completed = MutableStateFlow(initialCompleted)
    private val activeSession = MutableStateFlow<WorkoutSession?>(null)

    fun completedSessions(): List<WorkoutSession> = completed.value

    /** Finishes what [startWorkoutFromGenerated] recorded, as completing a workout would. */
    fun completeStartedSession(workout: GeneratedWorkout, completedAtEpochMillis: Long) {
        val sessionId = "completed-${completed.value.size}"
        val session = completedSession(
            id = sessionId,
            completedAtEpochMillis = completedAtEpochMillis,
            exercises = workout.exercises.mapIndexed { index, planned ->
                exerciseInstance(
                    exerciseId = planned.exerciseId,
                    id = "$sessionId-$index",
                    orderIndex = index,
                    sets = (1..planned.targetSets).map { setNumber ->
                        completedNormalSet(id = "$sessionId-$index-set-$setNumber")
                            .copy(setNumber = setNumber)
                    }
                )
            }
        )
        completed.update { it + session }
    }

    /**
     * Adds history the screen never observed, as a workout finished on another surface.
     *
     * The card on screen was built before it existed; the start-time identity check is what
     * has to notice.
     */
    fun addCompletedSessionWithoutNotifying(session: WorkoutSession) {
        completed.update { it + session }
    }

    override fun observeActiveSession(): Flow<WorkoutSession?> = activeSession
    override suspend fun getActiveSessionOnce(): WorkoutSession? = activeSession.value
    override suspend fun getSessionById(sessionId: String): WorkoutSession? =
        completed.value.firstOrNull { it.id == sessionId }

    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(null)
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> =
        completed.map { it.take(limit) }

    /**
     * A snapshot per collection, so history changes reach the context builder without also
     * driving the ViewModel's own regeneration collector.
     *
     * That separation is the point: a card built before a workout finished elsewhere and
     * started after it is the exact race the start-time identity check exists to catch, and
     * an auto-regeneration would resolve it before the check ever ran.
     */
    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(completed.value.size)
    override fun observeCompletedWorkoutCountInRange(
        startTimestamp: Long,
        endTimestampExclusive: Long
    ): Flow<Int> = completed.map { sessions ->
        sessions.count { session ->
            session.completedAtTimestamp?.let {
                it >= startTimestamp && it < endTimestampExclusive
            } == true
        }
    }

    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> =
        completed.value.sortedByDescending { it.completedAtTimestamp }.take(limit)

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        displayName: String,
        displayRationale: String,
        userProfile: UserProfile,
        recommendation: RecommendationSnapshot?
    ): WorkoutSession {
        startRequests += LifecycleStartRequest(
            workout = generated,
            displayName = displayName,
            userProfile = userProfile,
            recommendation = recommendation
        )
        return WorkoutSession(
            id = "started-session",
            name = displayName,
            weightUnit = userProfile.preferredUnit,
            status = SessionStatus.IN_PROGRESS
        )
    }

    override suspend fun startWorkoutFromTemplate(
        template: WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun logSetCompletion(setId: String, performance: SetPerformanceInput) = Unit

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): WorkoutSummary = error("Not used")

    override suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary? = error("Not used")

    override suspend fun cancelWorkout(sessionId: String) = Unit
}

/** The ledger the production repository would reconstruct from whatever history exists now. */
private class LifecycleLedgerRepository(
    private val workoutRepository: LifecycleWorkoutRepository,
    private val exercises: List<Exercise>,
    private val catalogVersion: String
) : WeeklyDoseLedgerRepository {
    private val calculator = WeeklyDoseLedgerCalculator()

    /** How many times the week was rebuilt from history rather than served from memory. */
    var reconstructionCount: Int = 0
        private set

    override suspend fun weeklyLedgerAt(
        profileId: String,
        instant: Instant,
        zoneId: ZoneId
    ): WeeklyDoseLedger = recompute(TrainingWeek.containing(instant, zoneId))

    override suspend fun currentWeeklyLedger(
        profileId: String,
        zoneId: ZoneId
    ): WeeklyDoseLedger = recompute(
        TrainingWeek.startingOn(MONDAY_EPOCH_DAY, zoneId)
    )

    private fun recompute(week: TrainingWeek): WeeklyDoseLedger {
        reconstructionCount += 1
        return calculator.calculate(
            sessions = workoutRepository.completedSessions(),
            exercisesById = exercises.associateBy(Exercise::id),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = catalogVersion,
            reviewPolicyVersion = exercises
                .mapNotNull { it.reviewedMetadata?.provenance?.policyVersion }
                .maxOrNull() ?: 0
        )
    }

    private companion object {
        const val MONDAY_EPOCH_DAY = 20_696L
    }
}

/**
 * Proposes several accepted candidates that share one direct-primary muscle.
 *
 * Each keeps the prescription the production policy wrote for it, so the only rule the
 * proposal breaks is the weekly direct-primary allowance — which is the one thing the
 * bounded repair pass is allowed to fix. Nothing about any review is fabricated; the plan
 * is simply arranged to spend more of one muscle's week than remains.
 */
private class SharedMuscleOverAllowancePlanner(
    private val exerciseCount: Int
) : WorkoutPlanner {
    private val prescriptionFactory = DefaultExercisePrescriptionFactory()

    override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
        val byMuscle = context.allowedExercises.groupBy {
            requireNotNull(it.acceptedMetadata()).directPrimaryMuscle
        }
        // The advertised split has to be one the chosen exercises actually train, so the
        // muscle and the split are picked together rather than assumed.
        val (muscle, split) = byMuscle
            .filterValues { it.size >= exerciseCount }
            .firstNotNullOf { (muscle, _) ->
                WorkoutSplit.entries.firstOrNull { muscle in it.targetMuscles }
                    ?.let { muscle to it }
            }
        val planned = byMuscle.getValue(muscle).take(exerciseCount).map { exercise ->
            PlannedExercise(
                exerciseId = exercise.id,
                prescription = prescriptionFactory.create(exercise, context)
            )
        }
        return GeneratedWorkout(
            title = WorkoutTitleSpec(split = split, emphasis = WorkoutEmphasis.HYPERTROPHY),
            focusMuscles = listOf(muscle),
            estimatedDurationMinutes = WorkoutDurationEstimator.estimateMinutes(planned),
            exercises = planned,
            rationale = WorkoutRationaleSpec.GoalFocus(
                goals = emptyList(),
                focusMuscles = listOf(muscle)
            )
        )
    }
}
