package wallcrawl.elopenmike.com.feature.today

import com.google.common.truth.Truth.assertThat
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
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import wallcrawl.elopenmike.com.core.ai.ProgramValidator
import wallcrawl.elopenmike.com.core.ai.RecommendationOutcome
import wallcrawl.elopenmike.com.core.ai.TrainingProgramStateProvider
import wallcrawl.elopenmike.com.core.ai.syntheticApprovedExercise
import wallcrawl.elopenmike.com.core.database.repository.WeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MuscleDoseAccounting
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.ai.RecommendationSnapshot
import wallcrawl.elopenmike.com.core.ai.WorkoutDurationEstimator
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanner
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedExercise
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutEmphasis
import wallcrawl.elopenmike.com.core.model.WorkoutRationaleSpec
import wallcrawl.elopenmike.com.core.model.WorkoutSplit
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.model.WorkoutTitleSpec
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun everyAggregateEligibilityFailureMapsToItsOwnTypedReason() {
        // The wording lives in string resources now, and `SafetyCopyTest` holds it to the
        // same non-medical boundary in both languages. What matters here is that every
        // failure still resolves to a distinct, non-null reason rather than falling into a
        // single catch-all the screen would explain wrongly.
        val reasons = AutomaticEligibilityFailure.entries.map(::automaticEligibilityError)

        assertThat(reasons).containsNoDuplicates()
        assertThat(automaticEligibilityError(null))
            .isEqualTo(TodayError.REVIEWED_NONE_ELIGIBLE)
    }

    @Test
    fun uiState_generatesOnceAndCountsCompletedWorkoutsInRollingWeek() = runTest {
        val now = 20 * DAY_MILLIS
        val profileRepository = TodayUserProfileRepository(UserProfile(availableEquipment = StandardEquipment.ALL))
        val workoutRepository = TodayWorkoutRepository(
            completedSessions = listOf(
                completedSession("recent-1", now - DAY_MILLIS),
                completedSession("recent-2", now - (6 * DAY_MILLIS)),
                completedSession("old", now - (8 * DAY_MILLIS))
            )
        )
        val catalog = InMemoryExerciseCatalog()
        val contextBuilder = WorkoutGenerationContextBuilder(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            exerciseCatalog = catalog,
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            nowTimestamp = { now }
        )
        val planner = RecordingWorkoutPlanner()
        val viewModel = TodayViewModel(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            workoutGenerationContextBuilder = contextBuilder,
            workoutPlanner = planner,
            programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
            nowTimestamp = { now },
            clock = flowOf(now)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertThat(planner.generateCalls).isEqualTo(1)
        assertThat(state.completedThisWeek).isEqualTo(2)
        assertThat(state.suggestedWorkout.exercises).isNotEmpty()
    }

    @Test
    fun profileChange_regeneratesWithUpdatedContextAfterInitialGeneration() = runTest {
        val now = 20 * DAY_MILLIS
        val profileRepository = TodayUserProfileRepository(UserProfile(availableEquipment = StandardEquipment.ALL))
        val workoutRepository = TodayWorkoutRepository(emptyList())
        val catalog = InMemoryExerciseCatalog()
        val planner = RecordingWorkoutPlanner()
        val viewModel = TodayViewModel(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                userProfileRepository = profileRepository,
                workoutRepository = workoutRepository,
                exerciseCatalog = catalog,
                exerciseFilter = ExerciseFilter(),
                historyAnalyzer = WorkoutHistoryAnalyzer(),
                nowTimestamp = { now }
            ),
            workoutPlanner = planner,
            programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
            nowTimestamp = { now },
            clock = flowOf(now)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        profileRepository.updatePrimaryGoal(FitnessGoal.STRENGTH)
        advanceUntilIdle()

        assertThat(planner.generateCalls).isEqualTo(2)
        assertThat(planner.contexts.last().fitnessGoal).isEqualTo(FitnessGoal.STRENGTH)
    }

    @Test
    fun startWorkout_afterProfileConstraintChange_neverPersistsStaleExercise() = runTest {
        val now = 20 * DAY_MILLIS
        val profileRepository = TodayUserProfileRepository(UserProfile(availableEquipment = StandardEquipment.ALL))
        val workoutRepository = TodayWorkoutRepository(emptyList())
        val catalog = InMemoryExerciseCatalog()
        val planner = RecordingWorkoutPlanner()
        val viewModel = TodayViewModel(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                userProfileRepository = profileRepository,
                workoutRepository = workoutRepository,
                exerciseCatalog = catalog,
                exerciseFilter = ExerciseFilter(),
                historyAnalyzer = WorkoutHistoryAnalyzer(),
                nowTimestamp = { now }
            ),
            workoutPlanner = planner,
            programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
            nowTimestamp = { now },
            clock = flowOf(now)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        val staleExerciseId = planner.contexts.single().allowedExercises.first().id

        profileRepository.updateExcludedExercises(listOf(staleExerciseId))
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()

        assertThat(workoutRepository.startRequests).isEmpty()
        assertThat(planner.contexts.last().allowedExercises.map { it.id })
            .doesNotContain(staleExerciseId)

        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()

        assertThat(workoutRepository.startRequests).hasSize(1)
        assertThat(workoutRepository.startRequests.single().workout.exercises.map { it.exerciseId })
            .doesNotContain(staleExerciseId)
    }

    @Test
    fun startWorkout_persistsTheUnitUsedToGenerateTargets() = runTest {
        val now = 20 * DAY_MILLIS
        val profileRepository = TodayUserProfileRepository(
            UserProfile(preferredUnit = WeightUnit.KG, availableEquipment = StandardEquipment.ALL)
        )
        val workoutRepository = TodayWorkoutRepository(emptyList())
        val catalog = InMemoryExerciseCatalog()
        val viewModel = TodayViewModel(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                userProfileRepository = profileRepository,
                workoutRepository = workoutRepository,
                exerciseCatalog = catalog,
                exerciseFilter = ExerciseFilter(),
                historyAnalyzer = WorkoutHistoryAnalyzer(),
                nowTimestamp = { now }
            ),
            workoutPlanner = RecordingWorkoutPlanner(),
            programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
            nowTimestamp = { now },
            clock = flowOf(now)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()

        assertThat(workoutRepository.startRequests.single().userProfile.preferredUnit)
            .isEqualTo(WeightUnit.KG)
    }

    @Test
    fun completedThisWeek_updatesForNewCompletionsAndMovingClock() = runTest {
        val now = 20 * DAY_MILLIS
        val clock = MutableStateFlow(now)
        val profileRepository = TodayUserProfileRepository(UserProfile(availableEquipment = StandardEquipment.ALL))
        val workoutRepository = TodayWorkoutRepository(
            listOf(completedSession("recent", now - DAY_MILLIS))
        )
        val catalog = InMemoryExerciseCatalog()
        val viewModel = TodayViewModel(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                userProfileRepository = profileRepository,
                workoutRepository = workoutRepository,
                exerciseCatalog = catalog,
                exerciseFilter = ExerciseFilter(),
                historyAnalyzer = WorkoutHistoryAnalyzer(),
                nowTimestamp = { now }
            ),
            workoutPlanner = RecordingWorkoutPlanner(),
            programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
            nowTimestamp = { clock.value },
            clock = clock
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertThat((viewModel.uiState.value as TodayUiState.Success).completedThisWeek)
            .isEqualTo(1)

        workoutRepository.addCompletedSession(
            completedSession("new", now + 1_000L)
        )
        advanceUntilIdle()

        assertThat((viewModel.uiState.value as TodayUiState.Success).completedThisWeek)
            .isEqualTo(2)

        clock.value = now + (8 * DAY_MILLIS)
        advanceUntilIdle()

        assertThat((viewModel.uiState.value as TodayUiState.Success).completedThisWeek)
            .isEqualTo(0)
    }

    @Test
    fun startWorkout_recordsTheValidationEvidenceWithTheSession() = runTest {
        val now = 20 * DAY_MILLIS
        val profileRepository = TodayUserProfileRepository(
            UserProfile(availableEquipment = StandardEquipment.ALL)
        )
        val workoutRepository = TodayWorkoutRepository(emptyList())
        val catalog = InMemoryExerciseCatalog()
        val viewModel = TodayViewModel(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                userProfileRepository = profileRepository,
                workoutRepository = workoutRepository,
                exerciseCatalog = catalog,
                exerciseFilter = ExerciseFilter(),
                historyAnalyzer = WorkoutHistoryAnalyzer(),
                nowTimestamp = { now },
                catalogVersion = { "catalog-commit-under-test" }
            ),
            workoutPlanner = RecordingWorkoutPlanner(),
            programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
            nowTimestamp = { now },
            clock = flowOf(now)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()

        val recommendation = checkNotNull(workoutRepository.startRequests.single().recommendation)
        assertThat(recommendation.outcome).isEqualTo(RecommendationOutcome.VALID)
        assertThat(recommendation.reasonCodes).isEmpty()
        assertThat(recommendation.catalogVersion).isEqualTo("catalog-commit-under-test")
        // The legacy path runs no reviewed rule, so it records no reviewed identity either.
        assertThat(recommendation.reviewedPathEnabled).isFalse()
        assertThat(recommendation.doseAccounting).isEmpty()
    }

    @Test
    fun startWorkout_withHistoryCompletedSinceGeneration_refusesInsteadOfStartingAStalePlan() =
        runTest {
            val now = 20 * DAY_MILLIS
            val profileRepository = TodayUserProfileRepository(
                UserProfile(availableEquipment = StandardEquipment.ALL)
            )
            val workoutRepository = TodayWorkoutRepository(emptyList())
            val catalog = InMemoryExerciseCatalog()
            val viewModel = TodayViewModel(
                userProfileRepository = profileRepository,
                workoutRepository = workoutRepository,
                workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                    userProfileRepository = profileRepository,
                    workoutRepository = workoutRepository,
                    exerciseCatalog = catalog,
                    exerciseFilter = ExerciseFilter(),
                    historyAnalyzer = WorkoutHistoryAnalyzer(),
                    nowTimestamp = { now }
                ),
                workoutPlanner = RecordingWorkoutPlanner(),
                programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
                nowTimestamp = { now },
                clock = flowOf(now)
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            advanceUntilIdle()

            // A workout finished elsewhere after this card was built. The rebuilt context no
            // longer matches the one the recommendation was validated against.
            workoutRepository.addCompletedSession(completedSession("elsewhere", now - 1_000L))
            viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
            advanceUntilIdle()

            assertThat(workoutRepository.startRequests).isEmpty()
            assertThat((viewModel.uiState.value as TodayUiState.Error).error)
                .isEqualTo(TodayError.RECOMMENDATION_OUT_OF_DATE)
        }

    @Test
    fun aProposalThatCannotFitTheConfiguredWeeklyAllowance_saysSoInsteadOfFailingGenerically() =
        runTest {
            val now = 20 * DAY_MILLIS
            val exercises = listOf(
                syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest"),
                syntheticApprovedExercise(id = "press-b", directPrimaryMuscle = "Chest"),
                syntheticApprovedExercise(id = "press-c", directPrimaryMuscle = "Chest")
            )
            val profileRepository = TodayUserProfileRepository(
                UserProfile(availableEquipment = listOf(StandardEquipment.BODYWEIGHT))
            )
            val workoutRepository = TodayWorkoutRepository(emptyList())
            val catalog = InMemoryExerciseCatalog(exercises)
            val viewModel = TodayViewModel(
                userProfileRepository = profileRepository,
                workoutRepository = workoutRepository,
                workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                    userProfileRepository = profileRepository,
                    workoutRepository = workoutRepository,
                    exerciseCatalog = catalog,
                    exerciseFilter = ExerciseFilter(),
                    historyAnalyzer = WorkoutHistoryAnalyzer(),
                    plannerFeatureFlags = PlannerFeatureFlags(
                        reviewedCapabilityEligibility = true
                    ),
                    trainingProgramStateProvider = TrainingProgramStateProvider(
                        weeklyDoseLedgerRepository = FullChestWeekLedgerRepository()
                    ),
                    nowTimestamp = { now }
                ),
                // Two sets on each of three exercises sharing one direct primary, against a
                // week that has already used four of the configured six.
                workoutPlanner = FixedPlanWorkoutPlanner(setsPerExercise = 2),
                programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
                nowTimestamp = { now },
                clock = flowOf(now)
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            advanceUntilIdle()

            // Two sets remain for three exercises, so the one permitted repair pass cannot
            // leave every exercise a set and the proposal is refused with copy about the
            // configured plan rather than a generic "couldn't build".
            assertThat((viewModel.uiState.value as TodayUiState.Error).error)
                .isEqualTo(TodayError.WEEKLY_ALLOWANCE_REACHED)
        }

    @Test
    fun anOverAllowanceProposalThatCanBeReduced_isRepairedAndRecordedAsRepaired() = runTest {
        val now = 20 * DAY_MILLIS
        val exercises = listOf(
            syntheticApprovedExercise(id = "press-a", directPrimaryMuscle = "Chest"),
            syntheticApprovedExercise(id = "press-b", directPrimaryMuscle = "Chest")
        )
        val profileRepository = TodayUserProfileRepository(
            UserProfile(availableEquipment = listOf(StandardEquipment.BODYWEIGHT))
        )
        val workoutRepository = TodayWorkoutRepository(emptyList())
        val catalog = InMemoryExerciseCatalog(exercises)
        val viewModel = TodayViewModel(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                userProfileRepository = profileRepository,
                workoutRepository = workoutRepository,
                exerciseCatalog = catalog,
                exerciseFilter = ExerciseFilter(),
                historyAnalyzer = WorkoutHistoryAnalyzer(),
                plannerFeatureFlags = PlannerFeatureFlags(reviewedCapabilityEligibility = true),
                trainingProgramStateProvider = TrainingProgramStateProvider(
                    weeklyDoseLedgerRepository = EmptyWeekLedgerRepository()
                ),
                nowTimestamp = { now }
            ),
            workoutPlanner = FixedPlanWorkoutPlanner(setsPerExercise = 4),
            programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
            nowTimestamp = { now },
            clock = flowOf(now)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val shown = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        assertThat(shown.exercises.map { it.targetSets }).containsExactly(4, 2).inOrder()

        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()

        val request = workoutRepository.startRequests.single()
        // What is started is exactly what was displayed, and the record says it was repaired
        // rather than claiming the original proposal was valid.
        assertThat(request.workout).isEqualTo(shown)
        assertThat(checkNotNull(request.recommendation).outcome)
            .isEqualTo(RecommendationOutcome.REPAIRED)
        assertThat(checkNotNull(request.recommendation).doseAccounting.single())
            .isEqualTo(
                MuscleDoseAccounting(
                    muscle = "Chest",
                    completedSets = 0,
                    proposedSets = 6,
                    allowanceSets = 6
                )
            )
    }

    private fun completedSession(id: String, completedAtTimestamp: Long) = WorkoutSession(
        id = id,
        name = "Workout $id",
        completedAtTimestamp = completedAtTimestamp,
        status = SessionStatus.COMPLETED
    )

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L

        /**
         * The text the screen would have written for the reader. The ViewModel takes it as
         * given, which is exactly the boundary these tests exercise: nothing below the
         * screen knows what language the workout was named in.
         */
        const val WORKOUT_NAME = "Empuje · Hipertrofia"
        const val WORKOUT_RATIONALE = "Generado para Ganar músculo, con prioridad en Pecho."
    }
}

private class RecordingWorkoutPlanner : WorkoutPlanner {
    var generateCalls: Int = 0
        private set
    val contexts = mutableListOf<WorkoutGenerationContext>()

    override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
        generateCalls += 1
        contexts += context
        val exercise = context.allowedExercises.first()
        val exercises = listOf(
            GeneratedExercise(
                exerciseId = exercise.id,
                targetSets = 3,
                repMin = 8,
                repMax = 10
            )
        )
        return GeneratedWorkout(
            title = WorkoutTitleSpec(
                split = WorkoutSplit.PUSH,
                emphasis = WorkoutEmphasis.HYPERTROPHY
            ),
            rationale = WorkoutRationaleSpec.GoalFocus(
                goals = emptyList(),
                focusMuscles = exercise.primaryMuscles
            ),
            focusMuscles = exercise.primaryMuscles,
            estimatedDurationMinutes = WorkoutDurationEstimator.estimateMinutes(exercises),
            exercises = exercises
        )
    }
}

private class TodayUserProfileRepository(
    initialProfile: UserProfile
) : UserProfileRepository {
    private val profile = MutableStateFlow(initialProfile)

    override fun getUserProfile(): Flow<UserProfile> = profile
    override suspend fun getProfileOnce(): UserProfile = profile.value
    override suspend fun saveUserProfile(profile: UserProfile) {
        this.profile.update { current -> profile.copy(revision = current.revision + 1L) }
    }

    override suspend fun saveProfile(profile: UserProfile) = saveUserProfile(profile)

    override suspend fun updateGoals(goals: Set<FitnessGoal>) =
        updateProfile { it.copy(goals = goals) }

    override suspend fun updatePrimaryGoal(goal: FitnessGoal) =
        updateGoals(setOf(goal))

    override suspend fun updateExperienceLevel(level: ExperienceLevel) =
        updateProfile { it.copy(experienceLevel = level) }

    override suspend fun updatePreferredDuration(minutes: Int) =
        updateProfile { it.copy(preferredDurationMinutes = minutes) }

    override suspend fun updateDaysPerWeek(days: Int) =
        updateProfile { it.copy(daysPerWeek = days) }

    override suspend fun updateEquipment(equipment: List<String>) =
        updateProfile { it.copy(availableEquipment = equipment) }

    override suspend fun updateUnit(unit: WeightUnit) =
        updateProfile { it.copy(preferredUnit = unit) }

    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) =
        updateProfile { it.copy(musclePriorities = priorities) }

    override suspend fun updateExcludedExercises(excludedIds: List<String>) =
        updateProfile { it.copy(excludedExerciseIds = excludedIds) }

    override suspend fun updateTrainingConstraints(
        constraints: Set<wallcrawl.elopenmike.com.core.model.TrainingConstraint>
    ) = updateProfile { it.copy(trainingConstraints = constraints) }

    override suspend fun updateReturningAfterBreakWeeks(weeks: Int) =
        updateProfile { it.copy(returningAfterBreakWeeks = weeks) }

    override suspend fun updateThemePreference(themePreference: wallcrawl.elopenmike.com.core.model.ThemePreference) =
        updateProfile { it.copy(themePreference = themePreference) }

    private fun updateProfile(transform: (UserProfile) -> UserProfile) {
        profile.update { current ->
            transform(current).copy(revision = current.revision + 1L)
        }
    }
}

private class TodayWorkoutRepository(
    completedSessions: List<WorkoutSession>
) : WorkoutRepository {
    val startRequests = mutableListOf<StartWorkoutRequest>()
    private val activeSession = MutableStateFlow<WorkoutSession?>(null)
    private val completed = MutableStateFlow(completedSessions)

    override fun observeActiveSession(): Flow<WorkoutSession?> = activeSession
    override suspend fun getActiveSessionOnce(): WorkoutSession? = activeSession.value
    override suspend fun getSessionById(sessionId: String): WorkoutSession? =
        activeSession.value?.takeIf { it.id == sessionId } ?: completed.value.firstOrNull { it.id == sessionId }

    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(null)
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> = completed

    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(completed.value.size)

    override fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int> =
        completed.map { sessions ->
            sessions.count { session ->
                session.completedAtTimestamp?.let { it >= startTimestamp } == true
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
        startRequests += StartWorkoutRequest(generated, displayName, userProfile, recommendation)
        return WorkoutSession(
            id = "started-session",
            name = displayName,
            weightUnit = userProfile.preferredUnit
        )
    }

    override suspend fun startWorkoutFromTemplate(
        template: wallcrawl.elopenmike.com.core.model.WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    fun addCompletedSession(session: WorkoutSession) {
        completed.update { it + session }
    }

    override suspend fun logSetCompletion(
        setId: String,
        performance: SetPerformanceInput
    ) = Unit

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): WorkoutSummary = error("Not used")

    override suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary? = error("Not used")

    override suspend fun cancelWorkout(sessionId: String) = Unit
}

private data class StartWorkoutRequest(
    val workout: GeneratedWorkout,
    val displayName: String,
    val userProfile: UserProfile,
    val recommendation: RecommendationSnapshot?
)

/**
 * A planner that always proposes every candidate with the same set count.
 *
 * It exists so a test can put the whole-proposal dose rule under pressure directly, without
 * depending on how the real planner happens to fill a split today.
 */
private class FixedPlanWorkoutPlanner(
    private val setsPerExercise: Int
) : WorkoutPlanner {
    override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
        val exercises = context.allowedExercises.map { exercise ->
            GeneratedExercise(
                exerciseId = exercise.id,
                targetSets = setsPerExercise,
                repMin = 8,
                repMax = 10
            )
        }
        return GeneratedWorkout(
            title = WorkoutTitleSpec(
                split = WorkoutSplit.PUSH,
                emphasis = WorkoutEmphasis.HYPERTROPHY
            ),
            rationale = WorkoutRationaleSpec.GoalFocus(
                goals = emptyList(),
                focusMuscles = emptyList()
            ),
            focusMuscles = listOf("Chest"),
            estimatedDurationMinutes = WorkoutDurationEstimator.estimateMinutes(exercises),
            exercises = exercises
        )
    }
}

/** A week that has already used four of the configured six direct-primary chest sets. */
private class FullChestWeekLedgerRepository : WeeklyDoseLedgerRepository {
    override suspend fun weeklyLedgerAt(
        profileId: String,
        instant: java.time.Instant,
        zoneId: java.time.ZoneId
    ): WeeklyDoseLedger = ledger(mapOf("Chest" to 4))

    override suspend fun currentWeeklyLedger(
        profileId: String,
        zoneId: java.time.ZoneId
    ): WeeklyDoseLedger = ledger(mapOf("Chest" to 4))
}

/** A week with no completed work at all. */
private class EmptyWeekLedgerRepository : WeeklyDoseLedgerRepository {
    override suspend fun weeklyLedgerAt(
        profileId: String,
        instant: java.time.Instant,
        zoneId: java.time.ZoneId
    ): WeeklyDoseLedger = ledger(emptyMap())

    override suspend fun currentWeeklyLedger(
        profileId: String,
        zoneId: java.time.ZoneId
    ): WeeklyDoseLedger = ledger(emptyMap())
}

private fun ledger(directPrimarySets: Map<String, Int>) = WeeklyDoseLedger(
    policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
    weekStartEpochDay = 20_696L,
    timeZoneId = "UTC",
    catalogVersion = "catalog-commit-under-test",
    reviewPolicyVersion = 1,
    directPrimarySets = directPrimarySets,
    secondaryInvolvement = emptyMap(),
    unattributedWorkSets = emptyMap()
)
