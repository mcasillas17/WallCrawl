package wallcrawl.elopenmike.com.feature.today

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
import wallcrawl.elopenmike.com.core.database.repository.DeloadRepository
import wallcrawl.elopenmike.com.core.ai.DeloadOfferPolicy
import wallcrawl.elopenmike.com.core.model.DeloadAction
import wallcrawl.elopenmike.com.core.model.DeloadPreferences
import wallcrawl.elopenmike.com.core.model.DeloadChoice
import wallcrawl.elopenmike.com.core.model.DeloadChoiceStatus
import wallcrawl.elopenmike.com.core.model.DeloadOffer
import wallcrawl.elopenmike.com.core.model.DeloadSource
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
    @Test
    fun delayedUnitRegenerationKeepsTheOldProposalBoundToItsGenerationUnit() = runTest {
        val exercise = bundledExercises.single { it.id == "overhead-press" }
        val history = completedSessionOf("old-load", exercise, 1, NOW - 20 * 24 * HOUR_MILLIS).let { session ->
            session.copy(weightUnit = WeightUnit.LBS, exercises = session.exercises.map {
                it.copy(sets = it.sets.map { set -> set.copy(completedWeight = 40.0, completedReps = 8) })
            })
        }
        val profile = LifecycleProfileRepository(freshlyOnboardedProfile().copy(
            preferredUnit = WeightUnit.LBS, availableEquipment = StandardEquipment.ALL,
            excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it == exercise.id }
        ))
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val delegate = FakeWorkoutPlanner()
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                if (context.preferredUnits == WeightUnit.KG) {
                    started.complete(Unit)
                    release.await()
                }
                return delegate.generateWorkout(context)
            }
        }
        val viewModel = todayViewModel(profile, LifecycleWorkoutRepository(listOf(history)), planner)
        val states = mutableListOf<TodayUiState.Success>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { if (it is TodayUiState.Success) states += it }
        }
        advanceUntilIdle()
        val original = viewModel.uiState.value as TodayUiState.Success
        profile.updateUnit(WeightUnit.KG)
        advanceUntilIdle()
        assertThat(started.isCompleted).isTrue()
        val interim = viewModel.uiState.value as TodayUiState.Success
        assertThat(interim.userProfile.preferredUnit).isEqualTo(WeightUnit.KG)
        assertThat(interim.isRegenerating).isTrue()
        assertThat(interim.suggestedWorkout).isEqualTo(original.suggestedWorkout)
        assertThat(interim.prescriptionUnit).isEqualTo(WeightUnit.LBS)
        assertThat(interim.suggestedWorkout.exercises.single().targetWeight).isEqualTo(40.0)
        release.complete(Unit)
        advanceUntilIdle()
        val fresh = viewModel.uiState.value as TodayUiState.Success
        assertThat(fresh.prescriptionUnit).isEqualTo(WeightUnit.KG)
        assertThat(fresh.suggestedWorkout.exercises.single().targetWeight).isWithin(0.000001).of(
            wallcrawl.elopenmike.com.core.model.convertWeight(40.0, WeightUnit.LBS, WeightUnit.KG)
        )
        states.forEach { state ->
            val pounds = wallcrawl.elopenmike.com.core.model.convertWeight(
                requireNotNull(state.suggestedWorkout.exercises.single().targetWeight),
                state.prescriptionUnit, WeightUnit.LBS
            )
            assertThat(pounds).isWithin(0.000001).of(40.0)
        }
    }

    @Test
    fun defaultProductionCompositionRecordsTheRevisionZeroDecisionGuard() = runTest {
        val repository = LifecycleWorkoutRepository()
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()
        val snapshot = requireNotNull(repository.startRequests.single().recommendation)
        assertThat(snapshot.deloadDecisionRevision).isEqualTo(0L)
        assertThat(snapshot.acceptedDeloadOfferId).isNull()
    }

    @Test
    fun initialGenerationCannotHideAnActiveWorkoutBehindLoading() = runTest {
        val repository = LifecycleWorkoutRepository()
        val active = WorkoutSession(id = "active", name = "Existing session", status = SessionStatus.IN_PROGRESS)
        repository.setActive(active)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        repository.recentReadGate = gate
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isNotEqualTo(TodayUiState.Loading)
        assertThat((viewModel.uiState.value as TodayUiState.Preparing).activeSession).isEqualTo(active)
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as TodayUiState.Success).activeSession).isEqualTo(active)
    }

    @Test
    fun passivePresentationEditsKeepSelectionAndTargetsButRefreshRecordedRevision() = runTest {
        val profile = LifecycleProfileRepository(freshlyOnboardedProfile())
        val repository = LifecycleWorkoutRepository()
        val viewModel = todayViewModel(profile, repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val original = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        profile.updateThemePreference(ThemePreference.LIGHT)
        advanceUntilIdle()
        val themed = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        assertThat(themed.generationIndex).isEqualTo(original.generationIndex)
        assertThat(themed.title).isEqualTo(original.title)
        assertThat(themed.exercises).isEqualTo(original.exercises)
        profile.updateGender(ProfileGender.WOMAN)
        advanceUntilIdle()
        val gendered = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        assertThat(gendered.exercises).isEqualTo(original.exercises)
        assertThat(gendered.progressionDecisions).isEqualTo(original.progressionDecisions)
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()
        assertThat(repository.startRequests.single().recommendation!!.profileRevision)
            .isEqualTo(profile.getProfileOnce().revision)
        viewModel.regenerateWorkout()
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as TodayUiState.Success).suggestedWorkout.generationIndex)
            .isEqualTo(requireNotNull(original.generationIndex) + 1)
    }

    @Test
    fun passiveUnitEditConvertsHistoricalLoadWithoutSpendingVariant() = runTest {
        val exercise = bundledExercises.single { it.id == "overhead-press" }
        val session = completedSessionOf("old-load", exercise, 1, NOW - 20 * 24 * HOUR_MILLIS).let { logged ->
            logged.copy(weightUnit = WeightUnit.LBS, exercises = logged.exercises.map {
                it.copy(sets = it.sets.map { set -> set.copy(completedWeight = 40.0, completedReps = 8) })
            })
        }
        val profile = LifecycleProfileRepository(freshlyOnboardedProfile().copy(
            preferredUnit = WeightUnit.LBS,
            availableEquipment = StandardEquipment.ALL,
            excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it == exercise.id }
        ))
        val repository = LifecycleWorkoutRepository(listOf(session))
        val viewModel = todayViewModel(profile, repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val pounds = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        profile.updateUnit(WeightUnit.KG)
        advanceUntilIdle()
        val kilograms = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        assertThat(kilograms.generationIndex).isEqualTo(pounds.generationIndex)
        assertThat(kilograms.exercises.single().prescription).isEqualTo(
            pounds.exercises.single().prescription.copy(targetWeight =
                wallcrawl.elopenmike.com.core.model.convertWeight(40.0, WeightUnit.LBS, WeightUnit.KG))
        )
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()
        assertThat(repository.startRequests.single().userProfile.preferredUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun startRebuildRejectsAnUnobservedDecisionRevisionWithoutCreatingAnything() = runTest {
        val repository = LifecycleWorkoutRepository()
        val deload = LifecycleDeloadRepository()
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository,
            deloadRepository = deload)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        deload.unobservedPreferences = DeloadPreferences(revision = 1, choice = DeloadChoice(
            DeloadOffer("new-offer", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION),
            DeloadChoiceStatus.OFFERED, 1
        ))
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) { error("stale start") }
        advanceUntilIdle()
        assertThat(repository.startRequests).isEmpty()
        assertThat((viewModel.uiState.value as TodayUiState.Error).error)
            .isEqualTo(TodayError.RECOMMENDATION_OUT_OF_DATE)
    }

    @Test
    fun acceptedChoiceSurvivesProfileAndUnitEditsAndStartRecordsCurrentIdentity() = runTest {
        val profile = LifecycleProfileRepository(freshlyOnboardedProfile())
        val repository = LifecycleWorkoutRepository()
        val deload = LifecycleDeloadRepository()
        val viewModel = todayViewModel(profile, repository, deloadRepository = deload)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.decideDeload(DeloadAction.REQUEST)
        advanceUntilIdle()
        viewModel.decideDeload(DeloadAction.ACCEPT)
        advanceUntilIdle()
        val accepted = deload.get()
        profile.updateUnit(WeightUnit.KG)
        profile.updateGoals(setOf(FitnessGoal.BUILD_MUSCLE))
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayUiState.Success
        assertThat(state.deload!!.acceptedChoice).isEqualTo(accepted.choice)
        assertThat(state.suggestedWorkout.progressionDecisions.map { it.reason }.toSet())
            .containsExactly(wallcrawl.elopenmike.com.core.model.ProgressionReason.HOLD_ACCEPTED_DELOAD)
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()
        val request = repository.startRequests.single()
        assertThat(request.userProfile.preferredUnit).isEqualTo(WeightUnit.KG)
        assertThat(request.recommendation!!.deloadDecisionRevision).isEqualTo(accepted.revision)
        assertThat(request.recommendation.acceptedDeloadOfferId).isEqualTo(accepted.choice!!.offer.id)
    }

    @Test
    fun cancellationDoesNotBecomeDecisionFailureOrAcceptance() = runTest {
        val repository = LifecycleWorkoutRepository()
        val deload = LifecycleDeloadRepository()
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository,
            deloadRepository = deload)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        deload.cancelWrite = true
        viewModel.decideDeload(DeloadAction.REQUEST)
        advanceUntilIdle()
        val state = (viewModel.uiState.value as TodayUiState.Success).deload!!
        assertThat(state.error).isNull()
        assertThat(state.isSaving).isFalse()
        assertThat(deload.get().revision).isEqualTo(0)
    }

    @Test
    fun deloadControlsPersistOnlyExplicitDecisionsAndRestoreAfterNavigation() = runTest {
        val profile = LifecycleProfileRepository(freshlyOnboardedProfile())
        val repository = LifecycleWorkoutRepository()
        val deload = LifecycleDeloadRepository()
        val viewModel = todayViewModel(profile, repository, deloadRepository = deload)
        val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as TodayUiState.Success).deload!!.offer).isNull()
        assertThat(deload.get().revision).isEqualTo(0)
        viewModel.decideDeload(DeloadAction.REQUEST)
        advanceUntilIdle()
        val offer = (viewModel.uiState.value as TodayUiState.Success).deload!!.offer!!
        assertThat(offer.source).isEqualTo(DeloadSource.EXPLICIT_REQUEST)
        assertThat(deload.get().choice!!.status).isEqualTo(DeloadChoiceStatus.OFFERED)
        viewModel.decideDeload(DeloadAction.ACCEPT)
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as TodayUiState.Success).deload!!.acceptedChoice!!.offer).isEqualTo(offer)
        subscription.cancel()
        runCurrent()
        val restored = todayViewModel(profile, repository, deloadRepository = deload)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect {} }
        advanceUntilIdle()
        assertThat((restored.uiState.value as TodayUiState.Success).deload!!.acceptedChoice!!.offer).isEqualTo(offer)
        restored.decideDeload(DeloadAction.CANCEL)
        advanceUntilIdle()
        assertThat(deload.get().choice!!.status).isEqualTo(DeloadChoiceStatus.CANCELLED)
        assertThat((restored.uiState.value as TodayUiState.Success).deload!!.acceptedChoice).isNull()
        for ((action, status) in listOf(
            DeloadAction.DECLINE to DeloadChoiceStatus.DECLINED,
            DeloadAction.DISMISS to DeloadChoiceStatus.DISMISSED
        )) {
            restored.decideDeload(DeloadAction.REQUEST)
            advanceUntilIdle()
            restored.decideDeload(action)
            advanceUntilIdle()
            assertThat(deload.get().choice!!.status).isEqualTo(status)
            assertThat((restored.uiState.value as TodayUiState.Success).deload!!.offer).isNull()
            assertThat((restored.uiState.value as TodayUiState.Success).suggestedWorkout.exercises.map { it.targetSets })
                .containsNoneOf(0, 1)
        }
    }

    @Test
    fun returningOfferIsNotAcceptanceAndDeclineIsRestored() = runTest {
        val profile = LifecycleProfileRepository(freshlyOnboardedProfile().copy(returningAfterBreakWeeks = 4))
        val repository = LifecycleWorkoutRepository()
        val deload = LifecycleDeloadRepository()
        val viewModel = todayViewModel(profile, repository, deloadRepository = deload)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val before = viewModel.uiState.value as TodayUiState.Success
        assertThat(before.deload!!.offer!!.source).isEqualTo(DeloadSource.RETURNING)
        assertThat(before.deload.acceptedChoice).isNull()
        assertThat(deload.get().revision).isEqualTo(0)
        viewModel.decideDeload(DeloadAction.DECLINE)
        advanceUntilIdle()
        val restored = todayViewModel(profile, repository, deloadRepository = deload)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.uiState.collect {} }
        advanceUntilIdle()
        assertThat((restored.uiState.value as TodayUiState.Success).deload!!.offer).isNull()
        assertThat((restored.uiState.value as TodayUiState.Success).suggestedWorkout.exercises.map { it.targetSets })
            .containsNoneOf(0, 1)
    }

    @Test
    fun decisionWriteInFlightBlocksStartAndFailureRetainsOfferWithTypedError() = runTest {
        val profile = LifecycleProfileRepository(freshlyOnboardedProfile())
        val repository = LifecycleWorkoutRepository()
        val deload = LifecycleDeloadRepository()
        val viewModel = todayViewModel(profile, repository, deloadRepository = deload)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.decideDeload(DeloadAction.REQUEST)
        advanceUntilIdle()
        deload.writeGate = kotlinx.coroutines.CompletableDeferred()
        deload.writeFailure = true
        viewModel.decideDeload(DeloadAction.ACCEPT)
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) { error("stale start") }
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as TodayUiState.Success).deload!!.isSaving).isTrue()
        assertThat(repository.startRequests).isEmpty()
        deload.writeGate!!.complete(Unit)
        advanceUntilIdle()
        val failed = (viewModel.uiState.value as TodayUiState.Success).deload!!
        assertThat(failed.isSaving).isFalse()
        assertThat(failed.error).isEqualTo(TodayError.DELOAD_WRITE_FAILED)
        assertThat(failed.offer).isNotNull()
        assertThat(deload.get().choice!!.status).isEqualTo(DeloadChoiceStatus.OFFERED)
        deload.writeFailure = false
        viewModel.decideDeload(DeloadAction.ACCEPT)
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as TodayUiState.Success).deload!!.error).isNull()
        assertThat(deload.get().choice!!.status).isEqualTo(DeloadChoiceStatus.ACCEPTED)
    }

    @Test
    fun deloadReadFailureKeepsActiveResumeAndRetryReconnects() = runTest {
        val repository = LifecycleWorkoutRepository()
        val active = WorkoutSession(id = "active", name = "In progress", status = SessionStatus.IN_PROGRESS)
        repository.setActive(active)
        val deload = LifecycleDeloadRepository().apply { readFailure = true }
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository,
            deloadRepository = deload)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val failed = viewModel.uiState.value as TodayUiState.Error
        assertThat(failed.error).isEqualTo(TodayError.DELOAD_READ_FAILED)
        assertThat(failed.activeSession).isEqualTo(active)
        assertThat(failed.deload).isNull()
        viewModel.decideDeload(DeloadAction.REQUEST)
        advanceUntilIdle()
        deload.readFailure = false
        assertThat(deload.get().revision).isEqualTo(0)
        viewModel.regenerateWorkout()
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as TodayUiState.Success).activeSession).isEqualTo(active)
        assertThat((viewModel.uiState.value as TodayUiState.Success).deload).isNull()
    }

    @Test
    fun generationFailureStillAllowsExplicitRequestWithoutActiveSession() = runTest {
        val deload = LifecycleDeloadRepository()
        val viewModel = todayViewModel(
            LifecycleProfileRepository(freshlyOnboardedProfile()), LifecycleWorkoutRepository(),
            planner = object : WorkoutPlanner {
                override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout =
                    throw java.io.IOException("test planner failure")
            }, deloadRepository = deload
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as TodayUiState.Error).deload).isNotNull()
        viewModel.decideDeload(DeloadAction.REQUEST)
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as TodayUiState.Error).deload!!.offer).isNotNull()
        assertThat(deload.get().choice!!.status).isEqualTo(DeloadChoiceStatus.OFFERED)
    }

    @Test
    fun acceptedAndConsumedChoiceRefreshVisibleProductionRecommendation() = runTest {
        val profile = LifecycleProfileRepository(freshlyOnboardedProfile().copy(
            excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it == "push-up" }
        ))
        val repository = LifecycleWorkoutRepository()
        val deload = LifecycleDeloadRepository()
        val viewModel = todayViewModel(profile, repository, deloadRepository = deload)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        val ordinary = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        deload.decide(DeloadAction.REQUEST, 0, 0, null)
        advanceUntilIdle()
        val offered = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        assertThat(offered.exercises).isEqualTo(ordinary.exercises)
        deload.decide(DeloadAction.ACCEPT, 0, 1, deload.get().choice!!.offer.id)
        advanceUntilIdle()
        val accepted = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        assertThat(accepted.exercises.map { it.prescription }).containsExactlyElementsIn(
            ordinary.exercises.map { it.prescription.copy(targetSets = (it.targetSets - 1).coerceAtLeast(1)) }
        ).inOrder()
        deload.consume("automatic-session")
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as TodayUiState.Success).suggestedWorkout.exercises)
            .isEqualTo(ordinary.exercises)
    }

    @Test
    fun recentLoadAndRestChangesOutsideSchedulingWindowStillRefreshThePlan() = runTest {
        val exercise = bundledExercises.single { it.id == "overhead-press" }
        val oldSession = completedSessionOf("old-practice", exercise, 1, NOW - 20 * 24 * HOUR_MILLIS).let { session ->
            session.copy(exercises = session.exercises.map { logged ->
                logged.copy(
                    prescription = logged.prescription.copy(
                        restClass = wallcrawl.elopenmike.com.core.model.RestClass.MODERATE,
                        restSeconds = 90,
                        restTargetSource = wallcrawl.elopenmike.com.core.model.RestTargetSource.USER_PREFERENCE
                    ),
                    sets = logged.sets.map { it.copy(completedWeight = 40.0, completedReps = 8) }
                )
            })
        }
        val repository = LifecycleWorkoutRepository(listOf(oldSession), observeHistoryChanges = true)
        val profile = freshlyOnboardedProfile().copy(
            experienceLevel = ExperienceLevel.ADVANCED,
            availableEquipment = StandardEquipment.ALL,
            excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it == exercise.id }
        )
        val contexts = mutableListOf<WorkoutGenerationContext>()
        val plans = mutableListOf<GeneratedWorkout>()
        val delegate = FakeWorkoutPlanner()
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                contexts += context
                return delegate.generateWorkout(context).also { plans += it }
            }
        }
        val viewModel = todayViewModel(LifecycleProfileRepository(profile), repository, planner)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(plans.size).isEqualTo(1)
        assertThat(contexts.single().schedulingEvidence?.practiceDates).isEmpty()
        assertThat(contexts.single().trainingProgramState?.weeklyLedger?.creditedWorkSets).isEqualTo(0)
        repository.replaceCompleted(listOf(oldSession.copy(exercises = oldSession.exercises.map { logged ->
            logged.copy(
                prescription = logged.prescription.copy(
                    restClass = wallcrawl.elopenmike.com.core.model.RestClass.LONG, restSeconds = 180
                ),
                sets = logged.sets.map { it.copy(completedWeight = 60.0, completedReps = 4) }
            )
        })))
        advanceUntilIdle()
        assertThat(plans.size).isEqualTo(2)
        assertThat(contexts.last().completedWorkoutCount).isEqualTo(contexts.first().completedWorkoutCount)
        assertThat(contexts.last().schedulingEvidence).isEqualTo(contexts.first().schedulingEvidence)
        assertThat(contexts.last().trainingProgramState).isEqualTo(contexts.first().trainingProgramState)
        assertThat(plans.last().exercises.single().targetWeight).isEqualTo(60.0)
        assertThat(plans.last().exercises.single().restSeconds).isEqualTo(180)
        assertThat((viewModel.uiState.value as TodayUiState.Success).suggestedWorkout).isEqualTo(plans.last())
    }
    @Test
    fun sameDayManageableCompletionCorrectsAMixedRecentHistoryAndCountRead() = runTest {
        val sourceId = "overhead-press"
        val targetId = "machine-shoulder-press"
        val source = bundledExercises.single { it.id == sourceId }
        fun manageableSession(id: String, completedAt: Long) =
            completedSessionOf(id, source, 1, completedAt).let { session ->
                session.copy(exercises = session.exercises.map { exercise ->
                    exercise.copy(
                        prescription = exercise.prescription.copy(targetWeight = 40.0),
                        sets = exercise.sets.map { set -> set.copy(
                            targetWeight = 40.0, completedWeight = 40.0,
                            targetReps = 8, completedReps = 8,
                            feltManageable = true, completedAtTimestamp = completedAt
                        ) }
                    )
                })
            }
        val sampledNow = NOW + 3 * HOUR_MILLIS
        val first = manageableSession("first", sampledNow - HOUR_MILLIS)
        val second = manageableSession("second", sampledNow)
        val repository = LifecycleWorkoutRepository(listOf(first), observeHistoryChanges = true, manualNotifications = true)
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        repository.recentReadEntered = entered
        repository.recentReadGate = gate
        val ids = setOf(sourceId, targetId, "dumbbell-bench-press", "cable-fly")
        val profile = freshlyOnboardedProfile().copy(
            experienceLevel = ExperienceLevel.ADVANCED,
            preferredDurationMinutes = 30,
            availableEquipment = StandardEquipment.ALL,
            musclePriorities = mapOf("Shoulders" to PriorityLevel.HIGH),
            excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it in ids },
            movementCapabilities = MovementCapabilities.from(
                MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE } +
                    (MovementCapabilityType.BALANCE_WITHOUT_SUPPORT to CapabilityLevel.LIMITED)
            )
        )
        val contexts = mutableListOf<WorkoutGenerationContext>()
        val plans = mutableListOf<GeneratedWorkout>()
        val delegate = FakeWorkoutPlanner()
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                contexts += context
                return delegate.generateWorkout(context).also { plans += it }
            }
        }
        val viewModel = todayViewModel(LifecycleProfileRepository(profile), repository, planner,
            now = { sampledNow }, clock = flowOf(sampledNow))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(entered.isCompleted).isTrue()
        assertThat(contexts).isEmpty()
        repository.replaceCompleted(listOf(first, second))
        repository.publishCount()
        repository.publishHistory()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(contexts.size).isEqualTo(2)
        val mixed = contexts.first()
        val fresh = contexts.last()
        assertThat(mixed.completedWorkoutCount).isEqualTo(2)
        assertThat(fresh.completedWorkoutCount).isEqualTo(2)
        assertThat(fresh.schedulingEvidence).isEqualTo(mixed.schedulingEvidence)
        assertThat(fresh.allowedExercises).isEqualTo(mixed.allowedExercises)
        assertThat(fresh.trainingProgramState).isEqualTo(mixed.trainingProgramState)
        assertThat(fresh.exerciseHistory.getValue(sourceId).lastWeight)
            .isEqualTo(mixed.exerciseHistory.getValue(sourceId).lastWeight)
        assertThat(fresh.exerciseHistory.getValue(sourceId).recentSets.map { it.completedReps })
            .isEqualTo(mixed.exerciseHistory.getValue(sourceId).recentSets.map { it.completedReps })
        val penalty = wallcrawl.elopenmike.com.core.ai.CapabilityPreferenceRankingPolicy()
        assertThat(penalty.penalties(ids.toList(), mixed.automaticEligibilityResult, mixed.capabilityEvidence)[sourceId])
            .isEqualTo(1)
        assertThat(penalty.penalties(ids.toList(), fresh.automaticEligibilityResult, fresh.capabilityEvidence)[sourceId])
            .isEqualTo(0)
        val regressions = wallcrawl.elopenmike.com.core.ai.SupportedRegressionRankingPolicy()
        assertThat(regressions.preferences(mixed.allowedExercises, mixed.automaticEligibilityResult, mixed.capabilityEvidence))
            .containsKey(targetId)
        assertThat(regressions.preferences(fresh.allowedExercises, fresh.automaticEligibilityResult, fresh.capabilityEvidence))
            .isEmpty()
        assertThat(plans.first().exercises.map { it.exerciseId })
            .containsExactly(targetId, "dumbbell-bench-press", "cable-fly").inOrder()
        assertThat(plans.last().exercises.map { it.exerciseId })
            .containsExactly(sourceId, "dumbbell-bench-press", "cable-fly").inOrder()
        assertThat(plans.last().rankingReasons).isEmpty()
        assertThat((viewModel.uiState.value as TodayUiState.Success).suggestedWorkout).isEqualTo(plans.last())
        viewModel.refreshRecommendation()
        advanceUntilIdle()
        assertThat(contexts.size).isEqualTo(2)
        val unconfirmed = second.copy(exercises = second.exercises.map { exercise ->
            exercise.copy(sets = exercise.sets.map { it.copy(feltManageable = false) })
        })
        repository.replaceCompleted(listOf(first, unconfirmed))
        repository.publishHistory()
        advanceUntilIdle()
        assertThat(contexts.size).isEqualTo(3)
        assertThat(contexts.last().completedWorkoutCount).isEqualTo(2)
        assertThat(contexts.last().schedulingEvidence).isEqualTo(fresh.schedulingEvidence)
        assertThat(contexts.last().capabilityEvidence[sourceId]).isNull()
        assertThat(plans.last().exercises.first().exerciseId).isEqualTo(targetId)
        // A raw display-only history edit may notify, but must not spend another ordinal.
        repository.replaceCompleted(listOf(first.copy(name = "Other history label"), unconfirmed))
        repository.publishHistory()
        advanceUntilIdle()
        assertThat(contexts.size).isEqualTo(3)
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()
        assertThat(repository.startRequests.single().recommendation?.contextIdentity)
            .isEqualTo(RecommendationContextIdentity.of(contexts.last()))
    }
    @Test
    fun cancelledGenerationDoesNotSuppressTheSameContextOnResume() = runTest {
        val clock = MutableStateFlow(NOW)
        val repository = LifecycleWorkoutRepository(observeHistoryChanges = true)
        var generations = 0
        val delegate = FakeWorkoutPlanner()
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                if (++generations == 2) throw kotlinx.coroutines.CancellationException("test cancellation")
                return delegate.generateWorkout(context)
            }
        }
        val viewModel = todayViewModel(
            LifecycleProfileRepository(freshlyOnboardedProfile()), repository, planner,
            now = { clock.value }, clock = clock
        )
        var collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        clock.value += 24 * HOUR_MILLIS
        advanceUntilIdle()
        assertThat(generations).isEqualTo(2)
        collector.cancel()
        advanceUntilIdle()
        collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(generations).isEqualTo(3)
        assertThat((viewModel.uiState.value as TodayUiState.Success).suggestedWorkout.generationIndex).isEqualTo(0)
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()
        assertThat(repository.startRequests).hasSize(1)
        collector.cancel()
        advanceUntilIdle()
    }
    @Test
    fun hidingDuringANewDayContextReadDoesNotDiscardOrSuppressTheCurrentPlan() = runTest {
        for (resumeBeforeReadCompletes in listOf(false, true)) {
            val clock = MutableStateFlow(NOW)
            val repository = LifecycleWorkoutRepository(observeHistoryChanges = true)
            val contexts = mutableListOf<WorkoutGenerationContext>()
            val plans = mutableListOf<GeneratedWorkout>()
            val delegate = FakeWorkoutPlanner()
            val planner = object : WorkoutPlanner {
                override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                    contexts += context
                    return delegate.generateWorkout(context).also { plans += it }
                }
            }
            val viewModel = todayViewModel(
                LifecycleProfileRepository(freshlyOnboardedProfile()), repository, planner,
                now = { clock.value }, clock = clock
            )
            var collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertThat(plans).hasSize(1)
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            repository.rangeReadEntered = entered
            repository.rangeReadGate = gate
            clock.value += 24 * HOUR_MILLIS
            advanceUntilIdle()
            assertThat(entered.isCompleted).isTrue()
            assertThat(plans).hasSize(1)

            collector.cancel()
            advanceUntilIdle()
            assertThat(repository.activeObservations).isEqualTo(0)
            if (resumeBeforeReadCompletes) {
                collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
                advanceUntilIdle()
            }
            gate.complete(Unit)
            advanceUntilIdle()
            if (!resumeBeforeReadCompletes) {
                collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
                advanceUntilIdle()
            }
            assertThat(plans).hasSize(2)
            assertThat((viewModel.uiState.value as TodayUiState.Success).suggestedWorkout).isEqualTo(plans.last())
            assertThat(plans.last().generationIndex).isEqualTo(0)
            viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
            advanceUntilIdle()
            assertThat(repository.startRequests).hasSize(1)
            assertThat(repository.startRequests.single().recommendation?.contextIdentity)
                .isEqualTo(RecommendationContextIdentity.of(contexts.last()))
            collector.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun genuinelyDiscardedContextCanBeRebuiltAfterObservationFailureAndResume() = runTest {
        val exercise = bundledExercises.single { it.id == "push-up" }
        val historyA = listOf(completedSessionOf("practice", exercise, 1, NOW - 24 * HOUR_MILLIS))
        val historyB = listOf(completedSessionOf("practice", exercise, 1, NOW - 2 * 24 * HOUR_MILLIS))
        val historyC = listOf(completedSessionOf("practice", exercise, 1, NOW - 3 * 24 * HOUR_MILLIS))
        val repository = LifecycleWorkoutRepository(historyA, observeHistoryChanges = true, manualNotifications = true)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val contexts = mutableListOf<WorkoutGenerationContext>()
        val delegate = FakeWorkoutPlanner()
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                contexts += context
                if (contexts.size == 2) gate.await()
                return delegate.generateWorkout(context)
            }
        }
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository, planner)
        var collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        repository.replaceCompleted(historyB)
        repository.publishHistory()
        advanceUntilIdle()
        assertThat(contexts).hasSize(2)
        repository.replaceCompleted(historyC)
        repository.publishHistory()
        advanceUntilIdle()
        repository.observationFailure.value = true
        advanceUntilIdle()
        assertThat(repository.activeObservations).isEqualTo(0)
        // Canonical history changes back while the failed observer cannot deliver that update.
        repository.replaceCompleted(historyB)
        repository.publishHistory()
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(contexts).hasSize(3)
        assertThat(RecommendationContextIdentity.of(contexts[2]))
            .isEqualTo(RecommendationContextIdentity.of(contexts[1]))
        assertThat(viewModel.uiState.value).isInstanceOf(TodayUiState.Error::class.java)
        collector.cancel()
        advanceUntilIdle()
        repository.observationFailure.value = false
        collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(contexts).hasSize(3)
        assertThat(viewModel.uiState.value).isInstanceOf(TodayUiState.Success::class.java)
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()
        assertThat(repository.startRequests).hasSize(1)
        assertThat(repository.startRequests.single().recommendation?.contextIdentity)
            .isEqualTo(RecommendationContextIdentity.of(contexts.last()))
        collector.cancel()
        advanceUntilIdle()
    }
    @Test
    fun duplicateInvalidationDoesNotRetryAFailedPlanUntilExplicitRetry() = runTest {
        val repository = LifecycleWorkoutRepository(observeHistoryChanges = true, manualNotifications = true)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var generations = 0
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                if (++generations == 2) {
                    gate.await()
                    throw IllegalStateException("test planning failure")
                }
                return FakeWorkoutPlanner().generateWorkout(context)
            }
        }
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository, planner)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        repository.replaceCompleted(listOf(completedSessionOf("completed", bundledExercises.single { it.id == "push-up" }, 1, NOW)))
        repository.publishCount()
        advanceUntilIdle()
        repository.publishHistory()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(generations).isEqualTo(2)
        assertThat(viewModel.uiState.value).isInstanceOf(TodayUiState.Error::class.java)
        viewModel.regenerateWorkout()
        advanceUntilIdle()
        assertThat(generations).isEqualTo(3)
        assertThat(viewModel.uiState.value).isInstanceOf(TodayUiState.Success::class.java)
    }
    @Test
    fun countAndHistoryNotificationsCoalesceWithoutExtraRegenerationOrdinal() = runTest {
        val finalPlans = mutableListOf<GeneratedWorkout>()
        for (countFirst in listOf(true, false)) {
            val repository = LifecycleWorkoutRepository(observeHistoryChanges = true, manualNotifications = true)
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val contexts = mutableListOf<WorkoutGenerationContext>()
            val delegate = FakeWorkoutPlanner()
            val planner = object : WorkoutPlanner {
                override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                    contexts += context
                    if (contexts.size == 2) gate.await()
                    return delegate.generateWorkout(context)
                }
            }
            val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository, planner)
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            advanceUntilIdle()
            repository.replaceCompleted(listOf(completedSessionOf("completed", bundledExercises.single { it.id == "push-up" }, 1, NOW)))
            if (countFirst) repository.publishCount() else repository.publishHistory()
            advanceUntilIdle()
            assertThat(contexts).hasSize(2)
            assertThat(contexts.last().completedWorkoutCount).isEqualTo(1)
            if (countFirst) repository.publishHistory() else repository.publishCount()
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()
            assertThat(contexts).hasSize(2)
            val plan = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
            assertThat(plan.generationIndex).isEqualTo(0)
            finalPlans += plan
            viewModel.regenerateWorkout()
            advanceUntilIdle()
            assertThat(contexts).hasSize(3)
            assertThat((viewModel.uiState.value as TodayUiState.Success).suggestedWorkout.generationIndex).isEqualTo(1)
            collector.cancel()
            advanceUntilIdle()
        }
        assertThat(finalPlans.last().copy(id = finalPlans.first().id)).isEqualTo(finalPlans.first())
    }

    @Test
    fun explicitRegenerationDuringFreshnessGenerationIsNotDiscarded() = runTest {
        val repository = LifecycleWorkoutRepository(observeHistoryChanges = true, manualNotifications = true)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var generations = 0
        val delegate = FakeWorkoutPlanner()
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                if (++generations == 2) gate.await()
                return delegate.generateWorkout(context)
            }
        }
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository, planner)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        repository.replaceCompleted(listOf(completedSessionOf("completed", bundledExercises.single { it.id == "push-up" }, 1, NOW)))
        repository.publishCount()
        advanceUntilIdle()
        repository.publishHistory()
        viewModel.regenerateWorkout()
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(generations).isEqualTo(3)
        assertThat((viewModel.uiState.value as TodayUiState.Success).suggestedWorkout.generationIndex).isEqualTo(1)
    }

    @Test
    fun retryAndResumeReconnectFailedObservationBeforeLaterHistoryChanges() = runTest {
        for (resume in listOf(false, true)) {
            val repository = LifecycleWorkoutRepository(observeHistoryChanges = true)
            val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository)
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            advanceUntilIdle()
            repository.observationFailure.value = true
            advanceUntilIdle()
            assertThat(viewModel.uiState.value).isInstanceOf(TodayUiState.Error::class.java)
            repository.observationFailure.value = false
            if (resume) viewModel.refreshRecommendation() else viewModel.regenerateWorkout()
            advanceUntilIdle()
            assertThat(repository.observationSubscriptions).isEqualTo(2)
            assertThat(repository.activeObservations).isEqualTo(1)
            assertThat(viewModel.uiState.value).isInstanceOf(TodayUiState.Success::class.java)
            val prior = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
            repository.replaceCompleted(listOf(completedSessionOf("new", bundledExercises.single { it.id == "push-up" }, 1, NOW)))
            advanceUntilIdle()
            assertThat((viewModel.uiState.value as TodayUiState.Success).suggestedWorkout).isNotEqualTo(prior)
            collector.cancel()
            advanceUntilIdle()
        }
    }

    @Test
    fun inFlightGenerationCannotHideAStillFailedObservation() = runTest {
        val repository = LifecycleWorkoutRepository(observeHistoryChanges = true)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var generations = 0
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                if (++generations == 2) gate.await()
                return FakeWorkoutPlanner().generateWorkout(context)
            }
        }
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository, planner)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.regenerateWorkout()
        advanceUntilIdle()
        repository.observationFailure.value = true
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(TodayUiState.Error::class.java)
        viewModel.regenerateWorkout()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(TodayUiState.Error::class.java)
        repository.observationFailure.value = false
        viewModel.regenerateWorkout()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(TodayUiState.Success::class.java)
    }

    @Test
    fun hiddenTodayStopsRangeReadsAndResumeReconstructsCurrentHistory() = runTest {
        val repository = LifecycleWorkoutRepository(observeHistoryChanges = true)
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(repository.activeObservations).isEqualTo(1)
        val original = (viewModel.uiState.value as TodayUiState.Success).suggestedWorkout
        collector.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertThat(repository.activeObservations).isEqualTo(0)
        val reads = repository.observedRangeReads
        repository.simulateActiveSetWrite()
        repository.replaceCompleted(listOf(completedSessionOf("hidden", bundledExercises.single { it.id == "push-up" }, 1, NOW)))
        advanceUntilIdle()
        assertThat(repository.observedRangeReads).isEqualTo(reads)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(repository.activeObservations).isEqualTo(1)
        assertThat((viewModel.uiState.value as TodayUiState.Success).suggestedWorkout).isNotEqualTo(original)
    }
    @Test
    fun aFutureCompletionBecomesCurrentOnAClockTickWithoutAHistoryWrite() = runTest {
        val clock = MutableStateFlow(NOW)
        val exercise = bundledExercises.single { it.id == "push-up" }
        val repository = LifecycleWorkoutRepository(listOf(
            completedSessionOf("past", exercise, 1, NOW - 3 * 24 * HOUR_MILLIS),
            completedSessionOf("future", exercise, 1, NOW + 60_000)
        ), observeHistoryChanges = true)
        val contexts = mutableListOf<WorkoutGenerationContext>()
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                contexts += context
                return FakeWorkoutPlanner().generateWorkout(context)
            }
        }
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()),
            repository, planner, now = { clock.value }, clock = clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(contexts).hasSize(1)
        assertThat(contexts.last().schedulingEvidence?.practiceDates?.get("Chest")).hasSize(1)
        clock.value += 30_000
        advanceUntilIdle()
        assertThat(contexts).hasSize(1)
        clock.value += 30_000
        advanceUntilIdle()
        assertThat(contexts).hasSize(2)
        assertThat(contexts.last().schedulingEvidence?.practiceDates?.get("Chest")).hasSize(2)
    }
    @Test
    fun firstDelayedHistoryEmissionCannotPublishAStaleInitialRecommendation() = runTest {
        val history = listOf(1L, 2L).map { day ->
            completedSessionOf("initial-$day", bundledExercises.single { it.id == "push-up" }, 1,
                NOW - day * HOUR_MILLIS * 24)
        }
        val observationGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val plannerGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repository = LifecycleWorkoutRepository(history, observeHistoryChanges = true)
        repository.observationGate = observationGate
        val contexts = mutableListOf<WorkoutGenerationContext>()
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                contexts += context
                if (contexts.size == 1) plannerGate.await()
                return FakeWorkoutPlanner().generateWorkout(context)
            }
        }
        val viewModel = todayViewModel(LifecycleProfileRepository(freshlyOnboardedProfile()), repository, planner)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(contexts).hasSize(1)
        repository.replaceCompleted(history.mapIndexed { index, session ->
            if (index == 0) session.copy(completedAtTimestamp = NOW) else session
        })
        observationGate.complete(Unit)
        advanceUntilIdle()
        plannerGate.complete(Unit)
        advanceUntilIdle()
        assertThat(contexts).hasSize(2)
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()
        assertThat(repository.startRequests).hasSize(1)
    }
    @Test
    fun resumingRefreshesUnobservedEqualCountHistoryBeforeTheUserStarts() = runTest {
        val history = listOf(1L, 2L).map { day ->
            completedSessionOf("practice-$day", bundledExercises.single { it.id == "push-up" }, 1,
                NOW - day * HOUR_MILLIS * 24)
        }
        val repository = LifecycleWorkoutRepository(history)
        val profile = LifecycleProfileRepository(freshlyOnboardedProfile())
        val contexts = mutableListOf<WorkoutGenerationContext>()
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                contexts += context
                return FakeWorkoutPlanner().generateWorkout(context)
            }
        }
        val viewModel = todayViewModel(profile, repository, planner)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        repository.replaceCompleted(history.mapIndexed { index, session ->
            if (index == 0) session.copy(completedAtTimestamp = NOW) else session
        })
        viewModel.refreshRecommendation()
        advanceUntilIdle()
        assertThat(contexts).hasSize(2)
        assertThat(contexts.last().completedWorkoutCount).isEqualTo(2)
        viewModel.startWorkout(WORKOUT_NAME, WORKOUT_RATIONALE) {}
        advanceUntilIdle()
        assertThat(repository.startRequests).hasSize(1)
        assertThat(repository.startRequests.single().recommendation?.contextIdentity)
            .isEqualTo(RecommendationContextIdentity.of(contexts.last()))
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val bundledCatalog = PlannerFixtureContextFactory().bundledCatalogProjection()
    private val bundledExercises = bundledCatalog.exercises
    private val acceptedIds =
        bundledExercises.filter { it.acceptedMetadata() != null }.map(Exercise::id).toSet()

    @Test
    fun schedulingClockAndEqualCountHistoryChangesRefreshWithoutPerMinuteRegeneration() = runTest {
        val clock = MutableStateFlow(NOW)
        var zone = LEDGER_ZONE
        val profileRepository = LifecycleProfileRepository(freshlyOnboardedProfile())
        val history = listOf(1L, 2L).map { day ->
            completedSessionOf("practice-$day", bundledExercises.single { it.id == "push-up" }, 1,
                NOW - day * 24 * HOUR_MILLIS)
        }
        val repository = LifecycleWorkoutRepository(history, observeHistoryChanges = true)
        val contexts = mutableListOf<WorkoutGenerationContext>()
        val delegate = FakeWorkoutPlanner()
        val planner = object : WorkoutPlanner {
            override suspend fun generateWorkout(context: WorkoutGenerationContext): GeneratedWorkout {
                contexts += context
                return delegate.generateWorkout(context)
            }
        }
        val viewModel = todayViewModel(profileRepository, repository, planner,
            now = { clock.value }, clock = clock, zone = { zone })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertThat(contexts).hasSize(1)
        clock.value += 60_000
        advanceUntilIdle()
        assertThat(contexts).hasSize(1)
        clock.value += 24 * HOUR_MILLIS
        advanceUntilIdle()
        assertThat(contexts).hasSize(2)
        repository.replaceCompleted(history.mapIndexed { index, session ->
            if (index == 0) session.copy(completedAtTimestamp = NOW) else session
        })
        advanceUntilIdle()
        assertThat(contexts).hasSize(3)
        assertThat(contexts.last().completedWorkoutCount).isEqualTo(2)
        zone = ZoneId.of("Pacific/Honolulu")
        clock.value += 60_000
        advanceUntilIdle()
        assertThat(contexts).hasSize(4)
        assertThat(contexts.last().schedulingEvidence?.timeZoneId).isEqualTo(zone.id)
        profileRepository.updateDaysPerWeek(2)
        advanceUntilIdle()
        assertThat(contexts).hasSize(5)
        assertThat(contexts.last().trainingFrequencyDaysPerWeek).isEqualTo(2)
    }

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
        ledgerRepository: LifecycleLedgerRepository = ledgerRepositoryFor(workoutRepository),
        now: () -> Long = { NOW },
        clock: Flow<Long> = flowOf(NOW),
        zone: () -> ZoneId = { LEDGER_ZONE },
        deloadRepository: DeloadRepository? = LifecycleDeloadRepository()
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
                nowTimestamp = now,
                zoneId = zone,
                deloadRepository = deloadRepository
            ),
            workoutPlanner = planner,
            programValidator = ProgramValidator(GeneratedWorkoutValidator(catalog)),
            nowTimestamp = now,
            clock = clock,
            zoneId = zone,
            deloadRepository = deloadRepository
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

private class LifecycleDeloadRepository : DeloadRepository {
    val preferences = MutableStateFlow(DeloadPreferences())
    var writeFailure = false
    var readFailure = false
    var cancelWrite = false
    var unobservedPreferences: DeloadPreferences? = null
    var writeGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    override fun observe(): Flow<DeloadPreferences> = preferences.map {
        if (readFailure) throw java.io.IOException("test deload read failure")
        it
    }
    override suspend fun get(): DeloadPreferences {
        if (readFailure) throw java.io.IOException("test deload read failure")
        return unobservedPreferences ?: preferences.value
    }
    override suspend fun decide(
        action: DeloadAction,
        expectedProfileRevision: Long,
        expectedDecisionRevision: Long,
        offerId: String?
    ) {
        writeGate?.await()
        if (cancelWrite) throw kotlinx.coroutines.CancellationException("test cancellation")
        if (writeFailure) throw java.io.IOException("test deload write failure")
        val current = preferences.value
        check(current.revision == expectedDecisionRevision)
        val offer = if (action == DeloadAction.REQUEST)
            DeloadOffer("request-${current.revision}", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        else current.choice?.offer ?: DeloadOffer(requireNotNull(offerId), DeloadSource.RETURNING, DeloadOfferPolicy.VERSION)
        if (action != DeloadAction.REQUEST) check(offer.id == offerId)
        preferences.value = current.copy(
            revision = current.revision + 1,
            choice = DeloadChoice(offer, when (action) {
                DeloadAction.REQUEST -> DeloadChoiceStatus.OFFERED
                DeloadAction.ACCEPT -> DeloadChoiceStatus.ACCEPTED
                DeloadAction.DECLINE -> DeloadChoiceStatus.DECLINED
                DeloadAction.DISMISS -> DeloadChoiceStatus.DISMISSED
                DeloadAction.CANCEL -> DeloadChoiceStatus.CANCELLED
            }, 1),
            lastHandledReturnKey = offer.id.takeIf { offer.source == DeloadSource.RETURNING }
                ?: current.lastHandledReturnKey
        )
    }
    fun consume(sessionId: String) {
        preferences.update { it.copy(
            revision = it.revision + 1,
            choice = requireNotNull(it.choice).copy(status = DeloadChoiceStatus.CONSUMED, sessionId = sessionId)
        ) }
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
    initialCompleted: List<WorkoutSession> = emptyList(),
    private val observeHistoryChanges: Boolean = false,
    private val manualNotifications: Boolean = false
) : WorkoutRepository {
    val startRequests = mutableListOf<LifecycleStartRequest>()
    val originalSessions: List<WorkoutSession> = initialCompleted

    private val completed = MutableStateFlow(initialCompleted)
    private val notifiedHistory = MutableStateFlow(initialCompleted)
    private val notifiedCount = MutableStateFlow(initialCompleted.size)
    private val activeSetWrites = MutableStateFlow(0)
    val observationFailure = MutableStateFlow(false)
    var observationSubscriptions = 0
    var activeObservations = 0
    var observedRangeReads = 0
    private val activeSession = MutableStateFlow<WorkoutSession?>(null)
    fun setActive(session: WorkoutSession?) { activeSession.value = session }

    fun completedSessions(): List<WorkoutSession> = completed.value
    fun replaceCompleted(sessions: List<WorkoutSession>) { completed.value = sessions }
    fun publishCount() { notifiedCount.value = completed.value.size }
    fun publishHistory() { notifiedHistory.value = completed.value }
    fun simulateActiveSetWrite() { activeSetWrites.value += 1 }
    var observationGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var rangeReadEntered: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var rangeReadGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var recentReadEntered: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var recentReadGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override fun observeCompletedSessionProbeInRange(startTimestamp: Long, endTimestampExclusive: Long): Flow<List<WorkoutSession>> =
        if (observeHistoryChanges) kotlinx.coroutines.flow.flow {
            observationSubscriptions++
            activeObservations++
            try {
                observationGate?.await()
                kotlinx.coroutines.flow.combine(
                    if (manualNotifications) notifiedHistory else completed,
                    activeSetWrites,
                    observationFailure
                ) { sessions, _, failed ->
                    if (failed) throw java.io.IOException("test observation failure")
                    observedRangeReads++
                    sessions.filter { it.completedAtTimestamp?.let { time ->
                        time >= startTimestamp && time < endTimestampExclusive
                    } == true }
                }.collect { emit(it) }
            } finally {
                activeObservations--
            }
        } else super.observeCompletedSessionProbeInRange(startTimestamp, endTimestampExclusive)

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
    override fun observeCompletedWorkoutCount(): Flow<Int> = kotlinx.coroutines.flow.flow {
        emit(completed.value.size)
        if (manualNotifications) notifiedCount.drop(1).collect { emit(it) }
    }
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

    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> {
        val snapshot = completed.value.sortedByDescending { it.completedAtTimestamp }.take(limit)
        recentReadEntered?.complete(Unit)
        recentReadGate?.await()
        return snapshot
    }

    override suspend fun getRecentSessions(limit: Int): List<WorkoutSession> =
        completed.value.sortedWith(compareByDescending<WorkoutSession> { it.startedAtTimestamp }.thenBy { it.id }).take(limit)

    override suspend fun getRecommendationRecords(sessionIds: List<String>): List<wallcrawl.elopenmike.com.core.model.RecommendationRecord> =
        emptyList()

    override suspend fun getCompletedSessionsInRange(startTimestamp: Long, endTimestampExclusive: Long): List<WorkoutSession> {
        rangeReadEntered?.complete(Unit)
        rangeReadGate?.await()
        return completed.value.filter { it.completedAtTimestamp?.let { time ->
            time >= startTimestamp && time < endTimestampExclusive
        } == true }
    }

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
            sessions = workoutRepository.completedSessions().filter {
                it.completedAtTimestamp?.let(week::contains) == true
            },
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
        val decisions = byMuscle.getValue(muscle).take(exerciseCount).map { exercise ->
            prescriptionFactory.createDecision(exercise, context)
        }
        val planned = decisions.map { decision ->
            PlannedExercise(
                exerciseId = decision.exerciseId,
                prescription = decision.prescription
            )
        }
        return GeneratedWorkout(
            title = WorkoutTitleSpec(split = split, emphasis = WorkoutEmphasis.HYPERTROPHY),
            focusMuscles = listOf(muscle),
            estimatedDurationMinutes = WorkoutDurationEstimator.estimateMinutes(planned),
            exercises = planned,
            progressionDecisions = decisions,
            rationale = WorkoutRationaleSpec.GoalFocus(
                goals = emptyList(),
                focusMuscles = listOf(muscle)
            )
        )
    }
}
