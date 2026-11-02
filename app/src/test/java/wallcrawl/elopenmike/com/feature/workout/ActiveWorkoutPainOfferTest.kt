package wallcrawl.elopenmike.com.feature.workout

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.conditions.ConditionRestrictions
import wallcrawl.elopenmike.com.core.conditions.ConditionTestGuidance
import wallcrawl.elopenmike.com.core.conditions.MovementDemand
import wallcrawl.elopenmike.com.core.database.repository.FakeUserProfileDao
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseRole
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutPainOfferTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun painStopOffersExclusionAndChangesNothingUnlessAccepted() = runTest {
        val profiles = profiles()
        val viewModel = viewModel(profiles)
        collect(viewModel)
        advanceUntilIdle()
        assertThat(active(viewModel).painExclusionOffer).isNull()

        // Other stop reasons offer nothing.
        viewModel.skipSet("main-1", SetStopReason.OTHER)
        advanceUntilIdle()
        assertThat(active(viewModel).painExclusionOffer).isNull()

        viewModel.skipSet("main-2", SetStopReason.PAIN_STOP)
        advanceUntilIdle()
        assertThat(active(viewModel).painExclusionOffer).isEqualTo(PainExclusionOffer("main-exercise"))
        assertThat(profiles.getProfileOnce().excludedExerciseIds).isEmpty()

        // Declining closes it and still changes nothing.
        viewModel.dismissPainExclusionOffer()
        advanceUntilIdle()
        assertThat(active(viewModel).painExclusionOffer).isNull()
        assertThat(profiles.getProfileOnce().excludedExerciseIds).isEmpty()
    }

    @Test
    fun repeatedStopsInferNothing() = runTest {
        val profiles = profiles()
        val viewModel = viewModel(profiles)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.skipSet("main-1", SetStopReason.PAIN_STOP)
        viewModel.dismissPainExclusionOffer()
        viewModel.skipSet("main-2", SetStopReason.PAIN_STOP)
        advanceUntilIdle()

        assertThat(profiles.getProfileOnce().excludedExerciseIds).isEmpty()
    }

    @Test
    fun acceptedExclusionUpdatesExcludedExerciseIds() = runTest {
        val profiles = profiles(UserProfile(excludedExerciseIds = listOf("already-out")))
        val viewModel = viewModel(profiles)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.skipSet("main-1", SetStopReason.PAIN_STOP)
        viewModel.acceptPainExclusion()
        advanceUntilIdle()

        assertThat(profiles.getProfileOnce().excludedExerciseIds)
            .containsExactly("already-out", "main-exercise").inOrder()
        assertThat(active(viewModel).painExclusionOffer).isNull()
    }

    @Test
    fun aMobilityExerciseGetsTheSameOffer() = runTest {
        val profiles = profiles()
        val viewModel = viewModel(profiles)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.skipSet("mobility-1", SetStopReason.PAIN_STOP)
        advanceUntilIdle()
        assertThat(active(viewModel).painExclusionOffer).isEqualTo(PainExclusionOffer("bird-dog"))

        viewModel.acceptPainExclusion()
        advanceUntilIdle()
        assertThat(profiles.getProfileOnce().excludedExerciseIds).containsExactly("bird-dog")
    }

    @Test
    fun theWorkoutCarriesRestrictionsForCuesAndSeparatesMobilityRows() = runTest {
        val profiles = profiles(
            UserProfile(reportedConditions = setOf(ConditionTestGuidance.CONDITION_ID))
        )
        val guidance = ConditionTestGuidance.loaded(
            exerciseDemands = mapOf(
                "main-exercise" to setOf(MovementDemand.HEAVY_EXTERNAL_LOAD),
                "bird-dog" to emptySet()
            ),
            modify = setOf(MovementDemand.HEAVY_EXTERNAL_LOAD),
            exerciseCues = mapOf("bird-dog" to mapOf("en" to "Stop if symptoms spread.")),
            demandCues = mapOf(MovementDemand.HEAVY_EXTERNAL_LOAD to mapOf("en" to "Keep the load light."))
        )
        val viewModel = ActiveWorkoutViewModel(
            sessionId = "session",
            workoutRepository = FakeRepository(session()),
            exerciseCatalog = InMemoryExerciseCatalog(),
            workoutHistoryAnalyzer = WorkoutHistoryAnalyzer(),
            userProfileRepository = profiles,
            conditionGuidanceSource = ConditionTestGuidance.source(guidance)
        )
        collect(viewModel)
        advanceUntilIdle()

        val state = active(viewModel)
        assertThat(state.restrictions).isInstanceOf(ConditionRestrictions.Active::class.java)
        assertThat(state.session.exercises.map { it.role })
            .containsExactly(ExerciseRole.MOBILITY, ExerciseRole.MAIN).inOrder()
    }

    private suspend fun profiles(profile: UserProfile = UserProfile()) =
        OfflineUserProfileRepository(FakeUserProfileDao()).also { it.saveUserProfile(profile) }

    private fun viewModel(profiles: OfflineUserProfileRepository) = ActiveWorkoutViewModel(
        sessionId = "session",
        workoutRepository = FakeRepository(session()),
        exerciseCatalog = InMemoryExerciseCatalog(),
        workoutHistoryAnalyzer = WorkoutHistoryAnalyzer(),
        userProfileRepository = profiles
    )

    private fun kotlinx.coroutines.test.TestScope.collect(viewModel: ActiveWorkoutViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun active(viewModel: ActiveWorkoutViewModel) =
        viewModel.uiState.value as ActiveWorkoutUiState.Active

    private fun session() = WorkoutSession(
        id = "session",
        name = "Workout",
        status = SessionStatus.IN_PROGRESS,
        exercises = listOf(
            exercise("mobility-ex", "bird-dog", ExerciseRole.MOBILITY, 0, "mobility-1", 30),
            exercise("main-ex", "main-exercise", ExerciseRole.MAIN, 1, "main-", 0)
        )
    ).let { base ->
        base.copy(exercises = base.exercises.map { workoutExercise ->
            if (workoutExercise.role == ExerciseRole.MAIN) {
                workoutExercise.copy(sets = (1..2).map { n ->
                    WorkoutSet(
                        id = "main-$n",
                        workoutExerciseId = workoutExercise.id,
                        setNumber = n,
                        targetReps = 10,
                        targetWeight = 40.0,
                        isCompleted = false
                    )
                })
            } else workoutExercise
        })
    }

    private fun exercise(
        id: String,
        exerciseId: String,
        role: ExerciseRole,
        order: Int,
        setId: String,
        holdOrZero: Int
    ) = WorkoutExercise(
        id = id,
        sessionId = "session",
        exerciseId = exerciseId,
        orderIndex = order,
        prescription = ExercisePrescription(
            exerciseType = if (role == ExerciseRole.MOBILITY) ExerciseType.BODYWEIGHT_REPS else ExerciseType.WEIGHT_REPS,
            targetSets = if (role == ExerciseRole.MOBILITY) 1 else 2,
            repRange = RepRange(8, 10),
            targetWeight = if (role == ExerciseRole.MOBILITY) null else 40.0,
            holdSeconds = holdOrZero.takeIf { it > 0 }
        ),
        role = role,
        sets = if (role == ExerciseRole.MOBILITY) {
            listOf(
                WorkoutSet(
                    id = setId,
                    workoutExerciseId = id,
                    setNumber = 1,
                    targetReps = 8,
                    isCompleted = false
                )
            )
        } else emptyList()
    )
}
