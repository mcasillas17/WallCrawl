package wallcrawl.elopenmike.com.feature.templates

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.database.repository.FakeUserProfileDao
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.feature.workout.FakeRepository
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutTemplatesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun template(unit: WeightUnit?, load: Double?) = WorkoutTemplate(
        id = "template",
        name = "Legs",
        weightUnit = unit,
        exercises = listOf(
            PlannedExercise(
                "barbell-back-squat",
                ExercisePrescription(
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    targetSets = 3,
                    repRange = RepRange(5, 8),
                    targetWeight = load
                )
            )
        )
    )

    private fun viewModel(
        template: WorkoutTemplate,
        values: MutableStateFlow<List<WorkoutTemplate>> = MutableStateFlow(listOf(template)),
        workoutRepository: WorkoutRepository = refusingStart()
    ) = WorkoutTemplatesViewModel(
        templateRepository = object : WorkoutTemplateRepository {
            override fun observeTemplates() = values
            override fun observeTemplate(templateId: String) =
                values.map { list -> list.firstOrNull { it.id == templateId } }
            override suspend fun getTemplate(templateId: String) =
                values.value.firstOrNull { it.id == templateId }
            override suspend fun saveTemplate(template: WorkoutTemplate, mustExist: Boolean) = Unit
            override suspend fun deleteTemplate(templateId: String) = Unit
        },
        // By default its template start is "not used": reaching it reports a start failure.
        workoutRepository = workoutRepository,
        userProfileRepository = OfflineUserProfileRepository(FakeUserProfileDao()),
        exerciseCatalog = InMemoryExerciseCatalog()
    )

    /**
     * A start that converts the template as OfflineWorkoutRepository does, so an unconfirmed
     * unit refuses, and otherwise fails as "not used".
     */
    private fun refusingStart() = object : WorkoutRepository by FakeRepository(null) {
        override suspend fun startWorkoutFromTemplate(
            template: WorkoutTemplate,
            userProfile: UserProfile
        ): WorkoutSession {
            template.exercisesIn(userProfile.preferredUnit)
            error("Not used")
        }
    }

    /** A start that waits, so the state between "attempt begun" and "result" is visible. */
    private fun gatedStart(release: CompletableDeferred<Unit>, onEnter: () -> Unit = {}) =
        object : WorkoutRepository by FakeRepository(null) {
            override suspend fun startWorkoutFromTemplate(
                template: WorkoutTemplate,
                userProfile: UserProfile
            ): WorkoutSession {
                // Refuses an unconfirmed unit before waiting, as the real conversion does.
                template.exercisesIn(userProfile.preferredUnit)
                onEnter()
                release.await()
                return WorkoutSession(id = "session", name = template.name)
            }
        }

    @Test
    fun aLegacyTemplateWithAnUnrecordedLoadUnitIsNotStartedUnderAGuessedUnit() = runTest {
        val viewModel = viewModel(template(unit = null, load = 100.0))
        backgroundScope.launch { viewModel.uiState.collect {} }
        val legacy = viewModel.uiState.first { it.templates.isNotEmpty() }.templates.single()

        viewModel.startTemplate(legacy)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.startedSessionId).isNull()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.template_error_unit_unconfirmed)
    }

    @Test
    fun aLegacyTemplateStartedDuringAWorkoutResumesThatWorkoutInsteadOfRefusing() = runTest {
        val active = WorkoutSession(id = "in-progress", name = "Already going")
        // The repository resumes the workout in progress, as OfflineWorkoutRepository does.
        val resuming = object : WorkoutRepository by FakeRepository(active) {
            override suspend fun startWorkoutFromTemplate(template: WorkoutTemplate, userProfile: UserProfile) = active
        }
        val viewModel = viewModel(template(unit = null, load = 100.0), workoutRepository = resuming)
        backgroundScope.launch { viewModel.uiState.collect {} }
        val legacy = viewModel.uiState.first { it.templates.isNotEmpty() }.templates.single()

        viewModel.startTemplate(legacy)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.startedSessionId).isEqualTo("in-progress")
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun aTemplateWithARecordedUnitOrNoLoadReachesTheWorkoutRepository() = runTest {
        listOf(template(WeightUnit.KG, 100.0), template(null, null)).forEach { candidate ->
            val reached = mutableListOf<String>()
            val viewModel = viewModel(
                candidate,
                workoutRepository = gatedStart(CompletableDeferred(Unit)) { reached += candidate.id }
            )
            backgroundScope.launch { viewModel.uiState.collect {} }
            val loaded = viewModel.uiState.first { it.templates.isNotEmpty() }.templates.single()
            viewModel.startTemplate(loaded)
            advanceUntilIdle()
            assertThat(reached).containsExactly(candidate.id)
            assertThat(viewModel.uiState.value.startedSessionId).isEqualTo("session")
            assertThat(viewModel.uiState.value.errorMessage).isNull()
        }
    }

    @Test
    fun aStartTheRepositoryFailsIsReportedAndNavigatesNowhere() = runTest {
        // The fake's start is unimplemented, so reaching it fails.
        val viewModel = viewModel(template(WeightUnit.KG, 100.0))
        backgroundScope.launch { viewModel.uiState.collect {} }
        val loaded = viewModel.uiState.first { it.templates.isNotEmpty() }.templates.single()
        viewModel.startTemplate(loaded)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(R.string.template_error_start_failed)
        assertThat(viewModel.uiState.value.startedSessionId).isNull()
    }

    @Test
    fun aTemplateDeletedBeforeItsStartIsReportedAndNothingStarts() = runTest {
        val values = MutableStateFlow(listOf(template(WeightUnit.KG, 100.0)))
        val viewModel = viewModel(values.value.single(), values)
        backgroundScope.launch { viewModel.uiState.collect {} }
        val shown = viewModel.uiState.first { it.templates.isNotEmpty() }.templates.single()
        values.value = emptyList()

        viewModel.startTemplate(shown)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(R.string.template_error_missing)
        assertThat(viewModel.uiState.value.startedSessionId).isNull()
    }

    @Test
    fun aSecondStartWhileTheFirstRunsIsIgnoredAndTheFirstNavigatesOnce() = runTest {
        val release = CompletableDeferred<Unit>()
        var entered = 0
        val viewModel = viewModel(
            template(WeightUnit.KG, 100.0),
            workoutRepository = gatedStart(release) { entered++ }
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        val shown = viewModel.uiState.first { it.templates.isNotEmpty() }.templates.single()

        // Back to back, as a double tap lands: the second call must see the first's lock
        // before either coroutine has run.
        viewModel.startTemplate(shown)
        viewModel.startTemplate(shown)
        advanceUntilIdle()
        assertThat(entered).isEqualTo(1)
        assertThat(viewModel.uiState.value.startedSessionId).isNull()

        release.complete(Unit)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.startedSessionId).isEqualTo("session")
        assertThat(viewModel.uiState.value.startingTemplateId).isNull()

        // Held until the screen has navigated, then gone, so it opens the workout once.
        viewModel.onStartHandled()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.startedSessionId).isNull()
    }

    @Test
    fun aNewAttemptClearsTheLastRefusalBeforeItRuns() = runTest {
        val legacy = template(unit = null, load = 100.0)
        val recorded = template(unit = WeightUnit.KG, load = 100.0).copy(id = "recorded")
        val release = CompletableDeferred<Unit>()
        val viewModel = viewModel(legacy, MutableStateFlow(listOf(legacy, recorded)), gatedStart(release))
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.templates.size == 2 }
        viewModel.startTemplate(legacy)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(R.string.template_error_unit_unconfirmed)

        viewModel.startTemplate(recorded)
        advanceUntilIdle()
        // Gone as soon as the new attempt begins, before its own result.
        assertThat(viewModel.uiState.value.errorMessage).isNull()
        release.complete(Unit)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.startedSessionId).isEqualTo("session")
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun clearErrorRemovesAShownRefusal() = runTest {
        val viewModel = viewModel(template(unit = null, load = 100.0))
        backgroundScope.launch { viewModel.uiState.collect {} }
        val legacy = viewModel.uiState.first { it.templates.isNotEmpty() }.templates.single()
        viewModel.startTemplate(legacy)
        advanceUntilIdle()
        viewModel.clearError()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }
}
