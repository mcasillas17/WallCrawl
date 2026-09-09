package wallcrawl.elopenmike.com.feature.profile

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.FakeUserProfileDao
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.ai.PlannerFixtureContextFactory
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.illustrationVariant
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @Test
    fun genderEditsPersistAndSelectArtwork() = runTest {
        val repository = repositoryWith(profile())
        val viewModel = ProfileViewModel(repository)
        viewModel.updateGender(wallcrawl.elopenmike.com.core.model.ProfileGender.WOMAN)
        advanceUntilIdle()
        assertThat(repository.getProfileOnce().illustrationVariant)
            .isEqualTo(wallcrawl.elopenmike.com.core.model.IllustrationVariant.FEMALE)
        viewModel.updateGender(wallcrawl.elopenmike.com.core.model.ProfileGender.MAN)
        advanceUntilIdle()
        assertThat(repository.getProfileOnce().illustrationVariant)
            .isEqualTo(wallcrawl.elopenmike.com.core.model.IllustrationVariant.MALE)
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun oldFullGymProfile_doesNotExpandAndEachBandConfirmationPersistsIndependently() = runTest {
        val repository = repositoryWith(profile().copy(availableEquipment = StandardEquipment.FULL_GYM))
        val viewModel = ProfileViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as ProfileUiState.Success).profile.availableEquipment)
            .containsExactlyElementsIn(StandardEquipment.FULL_GYM)

        StandardEquipment.BAND_SETUPS.forEach { setup ->
            viewModel.toggleEquipment(setup)
            advanceUntilIdle()
            assertThat(repository.getProfileOnce().availableEquipment)
                .containsExactlyElementsIn(StandardEquipment.FULL_GYM + setup)
            viewModel.toggleEquipment(setup)
            advanceUntilIdle()
            assertThat(repository.getProfileOnce().availableEquipment)
                .containsExactlyElementsIn(StandardEquipment.FULL_GYM)
        }
    }

    @Test
    fun overlappingSetupToggles_doNotRestoreARevokedAnchorOrLoseTheNewSelection() = runTest {
        val repository = repositoryWith(profile().copy(availableEquipment = listOf(
            StandardEquipment.RESISTANCE_BAND, StandardEquipment.BAND_ANCHOR_LOW
        )))
        val viewModel = ProfileViewModel(withSuspendingReads(repository))

        viewModel.toggleEquipment(StandardEquipment.BAND_ANCHOR_LOW)
        viewModel.toggleEquipment(StandardEquipment.BAND_ANCHOR_OVERHEAD)
        advanceUntilIdle()

        val saved = repository.getProfileOnce()
        assertThat(saved.availableEquipment).containsExactly(
            StandardEquipment.RESISTANCE_BAND, StandardEquipment.BAND_ANCHOR_OVERHEAD
        )
        val woodchop = PlannerFixtureContextFactory().bundledCatalogProjection().exercises
            .single { it.id == "banded-woodchop" }
        assertThat(ExerciseFilter().filterCandidates(listOf(woodchop), saved)).isEmpty()
    }

    @Test
    fun overlappingCapabilitySave_doesNotRestoreARevokedSetupFromItsProfileSnapshot() = runTest {
        val repository = repositoryWith(profile().copy(availableEquipment = listOf(
            StandardEquipment.RESISTANCE_BAND, StandardEquipment.BAND_ANCHOR_LOW
        )))
        val viewModel = ProfileViewModel(withSuspendingReads(repository))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        viewModel.startMovementCapabilityEditing()
        viewModel.updateMovementCapabilityDraft(MovementCapabilityType.IMPACT, CapabilityLevel.LIMITED)

        viewModel.toggleEquipment(StandardEquipment.BAND_ANCHOR_LOW)
        viewModel.saveMovementCapabilities()
        advanceUntilIdle()

        val saved = repository.getProfileOnce()
        assertThat(saved.availableEquipment).containsExactly(StandardEquipment.RESISTANCE_BAND)
        assertThat(saved.movementCapabilities[MovementCapabilityType.IMPACT])
            .isEqualTo(CapabilityLevel.LIMITED)
    }

    private fun withSuspendingReads(repository: UserProfileRepository): UserProfileRepository =
        object : UserProfileRepository by repository {
            override suspend fun getProfileOnce(): UserProfile =
                repository.getProfileOnce().also { yield() }
        }

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
