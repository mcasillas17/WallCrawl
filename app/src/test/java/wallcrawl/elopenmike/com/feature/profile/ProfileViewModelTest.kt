package wallcrawl.elopenmike.com.feature.profile

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.FakeUserProfileDao
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun capabilityEditor_loadsPersistedValues() = runTest {
        val repository = repositoryWith(profile())
        val viewModel = ProfileViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value as ProfileUiState.Success
        assertThat(state.profile.movementCapabilities[MovementCapabilityType.IMPACT])
            .isEqualTo(CapabilityLevel.COMFORTABLE)
        assertThat(state.movementCapabilityDraft).isNull()
    }

    @Test
    fun cancelCapabilityEdit_discardsDraftWithoutPersistence() = runTest {
        val repository = repositoryWith(profile())
        val viewModel = ProfileViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.startMovementCapabilityEditing()
        viewModel.updateMovementCapabilityDraft(
            MovementCapabilityType.IMPACT,
            CapabilityLevel.AVOID
        )
        viewModel.cancelMovementCapabilityEditing()
        advanceUntilIdle()

        val state = viewModel.uiState.value as ProfileUiState.Success
        assertThat(state.movementCapabilityDraft).isNull()
        assertThat(repository.getProfileOnce().movementCapabilities[MovementCapabilityType.IMPACT])
            .isEqualTo(CapabilityLevel.COMFORTABLE)
        assertThat(repository.getProfileOnce().revision).isEqualTo(0L)
    }

    @Test
    fun saveCapabilityEdit_updatesAllValuesOnceAndPreservesUnrelatedFields() = runTest {
        val original = profile()
        val repository = repositoryWith(original)
        val viewModel = ProfileViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.startMovementCapabilityEditing()
        MovementCapabilityType.entries.forEachIndexed { index, type ->
            viewModel.updateMovementCapabilityDraft(
                type,
                if (index % 2 == 0) CapabilityLevel.LIMITED else CapabilityLevel.UNKNOWN
            )
        }
        viewModel.saveMovementCapabilities()
        advanceUntilIdle()

        val saved = repository.getProfileOnce()
        MovementCapabilityType.entries.forEachIndexed { index, type ->
            assertThat(saved.movementCapabilities[type]).isEqualTo(
                if (index % 2 == 0) CapabilityLevel.LIMITED else CapabilityLevel.UNKNOWN
            )
        }
        assertThat(saved.name).isEqualTo(original.name)
        assertThat(saved.goals).containsExactlyElementsIn(original.goals)
        assertThat(saved.availableEquipment).containsExactlyElementsIn(original.availableEquipment)
        assertThat(saved.trainingConstraints)
            .containsExactlyElementsIn(original.trainingConstraints)
        assertThat(saved.confirmedStartingLoads)
            .containsExactlyEntriesIn(original.confirmedStartingLoads)
        assertThat(saved.themePreference).isEqualTo(original.themePreference)
        assertThat(saved.onboardingCompleted).isTrue()
        assertThat(saved.revision).isEqualTo(1L)

        val state = viewModel.uiState.value as ProfileUiState.Success
        assertThat(state.movementCapabilityDraft).isNull()
        assertThat(state.isSaving).isFalse()
    }

    private suspend fun repositoryWith(
        profile: UserProfile
    ): OfflineUserProfileRepository = OfflineUserProfileRepository(FakeUserProfileDao()).also {
        it.saveProfile(profile)
    }

    private fun profile(): UserProfile = UserProfile(
        name = "Alex",
        goals = setOf(FitnessGoal.STRENGTH, FitnessGoal.BUILD_MUSCLE),
        availableEquipment = listOf(
            StandardEquipment.BODYWEIGHT,
            StandardEquipment.DUMBBELL
        ),
        trainingConstraints = setOf(TrainingConstraint.KNEE_SENSITIVE),
        confirmedStartingLoads = mapOf("goblet-squat" to 30.0),
        themePreference = ThemePreference.DARK,
        onboardingCompleted = true,
        movementCapabilities = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith {
                CapabilityLevel.COMFORTABLE
            }
        )
    )
}
