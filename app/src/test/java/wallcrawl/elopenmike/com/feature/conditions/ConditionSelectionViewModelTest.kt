package wallcrawl.elopenmike.com.feature.conditions

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.Rule
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import org.junit.Test
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidanceResult
import wallcrawl.elopenmike.com.core.conditions.ConditionTestGuidance
import wallcrawl.elopenmike.com.core.conditions.textIn
import wallcrawl.elopenmike.com.core.database.repository.FakeUserProfileDao
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingError
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingStep
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingViewModel
import wallcrawl.elopenmike.com.feature.profile.ProfileUiState
import wallcrawl.elopenmike.com.feature.profile.ProfileViewModel
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ConditionSelectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val id = ConditionTestGuidance.CONDITION_ID

    private suspend fun repository(profile: UserProfile = UserProfile()) =
        OfflineUserProfileRepository(FakeUserProfileDao()).also { it.saveUserProfile(profile) }

    @Test
    fun addingAConditionRequiresRedFlagAcknowledgementBeforeSaving() = runTest {
        val repository = repository()
        val viewModel = ProfileViewModel(
            repository, ConditionTestGuidance.source(ConditionTestGuidance.loaded())
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectCondition(id)
        advanceUntilIdle()
        assertThat(state(viewModel).pendingCondition?.id).isEqualTo(id)

        // Confirming without acknowledging saves nothing.
        viewModel.confirmPendingCondition()
        advanceUntilIdle()
        assertThat(repository.getProfileOnce().reportedConditions).isEmpty()
        assertThat(state(viewModel).pendingCondition?.id).isEqualTo(id)

        viewModel.setConditionAcknowledged(true)
        viewModel.confirmPendingCondition()
        advanceUntilIdle()
        assertThat(repository.getProfileOnce().reportedConditions).containsExactly(id)
        assertThat(state(viewModel).pendingCondition).isNull()
    }

    @Test
    fun cancellingTheAcknowledgementSavesNothing() = runTest {
        val repository = repository()
        val viewModel = ProfileViewModel(
            repository, ConditionTestGuidance.source(ConditionTestGuidance.loaded())
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectCondition(id)
        viewModel.setConditionAcknowledged(true)
        viewModel.cancelPendingCondition()
        viewModel.confirmPendingCondition()
        advanceUntilIdle()

        assertThat(repository.getProfileOnce().reportedConditions).isEmpty()
    }

    @Test
    fun addingAConditionShowsItsNotes() = runTest {
        val viewModel = ProfileViewModel(
            repository(), ConditionTestGuidance.source(ConditionTestGuidance.loaded())
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectCondition(id)
        advanceUntilIdle()

        val state = state(viewModel)
        val record = (state.conditionGuidance as ConditionGuidanceResult.Loaded)
            .guidance.conditions.getValue(state.pendingCondition!!.id)
        // The overlay's own text, in each language, is what the screen renders.
        assertThat(record.notes.textIn("en")).containsExactly("Ease off if symptoms start.")
        assertThat(record.notes.textIn("es"))
            .containsExactly("Baja la intensidad si empiezan los síntomas.")
        assertThat(record.redFlags.textIn("en")).isNotEmpty()
        assertThat(record.redFlags.textIn("fr")).isEqualTo(record.redFlags.textIn("en"))
    }

    @Test
    fun anUnavailableOverlayBlocksAddingButKeepsExistingConditionsRemovable() = runTest {
        val repository = repository(UserProfile(reportedConditions = setOf(id)))
        val viewModel = ProfileViewModel(
            repository, ConditionTestGuidance.source(ConditionGuidanceResult.Unavailable)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectCondition("another")
        viewModel.selectCondition(id)
        advanceUntilIdle()
        val state = state(viewModel)
        assertThat(state.conditionGuidance).isEqualTo(ConditionGuidanceResult.Unavailable)
        assertThat(state.pendingCondition).isNull()
        assertThat(state.profile.reportedConditions).containsExactly(id)

        viewModel.removeCondition(id)
        advanceUntilIdle()
        assertThat(repository.getProfileOnce().reportedConditions).isEmpty()
    }

    @Test
    fun anUnknownRestoredConditionStaysListedAndCanBeRemovedWithoutAcknowledgement() = runTest {
        val repository = repository(UserProfile(reportedConditions = setOf("from_a_newer_build")))
        val viewModel = ProfileViewModel(
            repository, ConditionTestGuidance.source(ConditionTestGuidance.loaded())
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertThat(state(viewModel).profile.reportedConditions).containsExactly("from_a_newer_build")
        assertThat(conditionNameRes("from_a_newer_build"))
            .isEqualTo(wallcrawl.elopenmike.com.R.string.condition_name_unknown)

        viewModel.removeCondition("from_a_newer_build")
        advanceUntilIdle()
        assertThat(repository.getProfileOnce().reportedConditions).isEmpty()
    }

    @Test
    fun onboardingNeedsNoConditionAndSavesOneOnlyAfterAcknowledgement() = runTest {
        val repository = repository()
        val viewModel = OnboardingViewModel(
            repository,
            conditionGuidanceSource = ConditionTestGuidance.source(ConditionTestGuidance.loaded())
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // No condition: the safety step can be passed and no step was added.
        val noCondition = viewModel.uiState.value.copy(currentStep = OnboardingStep.SAFETY)
        assertThat(noCondition.canProceedCurrentStep).isTrue()
        assertThat(OnboardingStep.entries.map { it.name }).doesNotContain("CONDITIONS")

        viewModel.selectCondition(id)
        assertThat(viewModel.uiState.value.reportedConditions).isEmpty()
        viewModel.confirmPendingCondition()
        assertThat(viewModel.uiState.value.reportedConditions).isEmpty()

        viewModel.setConditionAcknowledged(true)
        viewModel.confirmPendingCondition()
        assertThat(viewModel.uiState.value.reportedConditions).containsExactly(id)

        viewModel.complete(
            name = "Alex",
            capabilityAnswers = MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
        )
        advanceUntilIdle()
        assertThat(repository.getProfileOnce().reportedConditions).containsExactly(id)
    }

    @Test
    fun removingAnExcludedExerciseAllowsItAgainAndLeavesTheRest() = runTest {
        val repository = repository(UserProfile(excludedExerciseIds = listOf("a", "b")))
        val viewModel = ProfileViewModel(repository, ConditionTestGuidance.source(ConditionTestGuidance.loaded()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.removeExcludedExercise("a")
        advanceUntilIdle()

        assertThat(repository.getProfileOnce().excludedExerciseIds).containsExactly("b")
        assertThat(state(viewModel).safetyErrorRes).isNull()
    }

    @Test
    fun aFailedConditionSaveIsSurfacedAndKeepsThePromptOpen() = runTest {
        val real = repository()
        val failing = object : UserProfileRepository by real {
            override suspend fun editReportedConditions(change: (Set<String>) -> Set<String>) {
                throw java.io.IOException("disk full")
            }
        }
        val viewModel = ProfileViewModel(failing, ConditionTestGuidance.source(ConditionTestGuidance.loaded()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectCondition(id)
        viewModel.setConditionAcknowledged(true)
        viewModel.confirmPendingCondition()
        advanceUntilIdle()

        assertThat(state(viewModel).safetyErrorRes)
            .isEqualTo(wallcrawl.elopenmike.com.R.string.profile_condition_save_error)
        assertThat(state(viewModel).pendingCondition?.id).isEqualTo(id)
        assertThat(real.getProfileOnce().reportedConditions).isEmpty()
    }

    @Test
    fun concurrentExclusionAndConditionEditsDoNotOverwriteEachOther() = runTest {
        val repository = repository()
        val edits = (1..40).flatMap { n ->
            listOf(
                async(Dispatchers.Default) { repository.editExcludedExercises { it + "ex-$n" } },
                async(Dispatchers.Default) { repository.editReportedConditions { it + "c-$n" } }
            )
        }
        edits.awaitAll()

        val saved = repository.getProfileOnce()
        assertThat(saved.excludedExerciseIds).hasSize(40)
        assertThat(saved.reportedConditions).hasSize(40)
    }

    @Test
    fun theSafetyStepBlocksNextWhileAConditionIsPendingUntilItIsAddedOrCancelled() = runTest {
        val viewModel = OnboardingViewModel(
            repository(),
            conditionGuidanceSource = ConditionTestGuidance.source(ConditionTestGuidance.loaded())
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.goToStep(OnboardingStep.SAFETY)

        viewModel.selectCondition(id)
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isFalse()
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SAFETY)
        assertThat(viewModel.uiState.value.error).isEqualTo(OnboardingError.CONDITION_PENDING)

        viewModel.cancelPendingCondition()
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isTrue()

        viewModel.selectCondition(id)
        viewModel.setConditionAcknowledged(true)
        viewModel.confirmPendingCondition()
        assertThat(viewModel.uiState.value.canProceedCurrentStep).isTrue()
        viewModel.nextStep()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.SUMMARY)
    }

    private fun state(viewModel: ProfileViewModel) = viewModel.uiState.value as ProfileUiState.Success
}
