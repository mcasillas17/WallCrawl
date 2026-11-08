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
    fun removingAnExclusionKeepsOtherExercisesAndProfileSettings() = runTest {
        val repository = repositoryWith(profile().copy(excludedExerciseIds = listOf("one", "two")))
        val before = repository.getProfileOnce()
        val viewModel = ProfileViewModel(repository)
        viewModel.removeExcludedExercise("one")
        viewModel.removeExcludedExercise("one")
        advanceUntilIdle()
        assertThat(repository.getProfileOnce()).isEqualTo(before.copy(
            excludedExerciseIds = listOf("two"), revision = before.revision + 1
        ))
    }

    @Test
    fun refusedConditionSaveRetainsDraftAndDoesNotReplaceProfile() = runTest {
        val underlying = repositoryWith(profile())
        var writes = 0
        val repository = object : UserProfileRepository by underlying {
            override suspend fun updateActiveConditions(conditions: List<wallcrawl.elopenmike.com.core.model.SelectedCondition>) {
                writes++
                throw IllegalStateException("Active workout")
            }
            override suspend fun saveProfile(profile: UserProfile) = error("Must not replace a stale profile")
        }
        val vm = ProfileViewModel(repository, conditionSupport = { error("Unavailable support") })
        val draft = listOf(wallcrawl.elopenmike.com.core.model.SelectedCondition("withdrawn", 9, mapOf("response" to "future", "side" to null)))
        vm.updateConditionDraft(draft)
        vm.saveConditions()
        advanceUntilIdle()
        assertThat(writes).isEqualTo(1)
        assertThat(vm.conditions.value.draft).isEqualTo(draft)
        assertThat(vm.conditions.value.saveFailed).isTrue()
        assertThat(vm.conditions.value.loadFailed).isTrue()
        vm.cancelConditionEditing()
        assertThat(vm.conditions.value.draft).isNull()
    }

    @Test
    fun reassessmentRefusalKeepsExactEventAndSubmittedAnswers() = runTest {
        val underlying = repositoryWith(profile())
        var submitted: String? = null
        val event = wallcrawl.elopenmike.com.core.model.PendingConditionReassessment(
            UserProfile.DEFAULT_PROFILE_ID, "withdrawn", "exercise", "event-old", "a".repeat(64), 1, 100, null)
        val refusal = wallcrawl.elopenmike.com.core.model.ConditionReassessmentRefused(
            wallcrawl.elopenmike.com.core.model.ConditionViolation("stale-event", setOf("withdrawn")), event.eventId)
        val repository = object : UserProfileRepository by underlying {
            override suspend fun reassessCondition(conditionId: String, exerciseId: String, expectedEventId: String, answers: Map<String, String?>) {
                submitted = expectedEventId
                throw refusal
            }
        }
        val saved = androidx.lifecycle.SavedStateHandle()
        val vm = ProfileViewModel(repository, saved)
        vm.updateReassessmentAnswers(event.eventId, mapOf("response" to "answer", "unknown" to null))
        vm.reassess(event)
        advanceUntilIdle()
        assertThat(submitted).isEqualTo(event.eventId)
        assertThat(vm.conditions.value.reassessmentRefusal).isSameInstanceAs(refusal)
        val restored = ProfileViewModel(repository, saved)
        assertThat(restored.conditions.value.reassessmentEventId).isEqualTo(event.eventId)
        assertThat(restored.conditions.value.reassessmentAnswers).containsExactly("response", "answer", "unknown", null)
    }

    @Test
    fun reassessmentAnswerOverflowRefusesWithoutChangingSavedEventOrFacts() = runTest {
        val repository = repositoryWith(profile())
        val saved = androidx.lifecycle.SavedStateHandle()
        val answers = (1..32).associate { "unknown-$it" to if (it % 2 == 0) null else "future" }
        val vm = ProfileViewModel(repository, saved)
        vm.updateReassessmentAnswers("exact-event", answers)
        vm.updateReassessmentAnswers("replacement-event", answers + ("extra" to "answer"))
        assertThat(vm.conditions.value.reassessmentAnswerLimitEventId).isEqualTo("replacement-event")
        assertThat(vm.conditions.value.reassessmentEventId).isEqualTo("exact-event")
        assertThat(vm.conditions.value.reassessmentAnswers).isEqualTo(answers)
        val restored = ProfileViewModel(repository, saved)
        assertThat(restored.conditions.value.reassessmentEventId).isEqualTo("exact-event")
        assertThat(restored.conditions.value.reassessmentAnswers).isEqualTo(answers)
        vm.updateReassessmentAnswers("exact-event", answers + ("unknown-1" to null))
        assertThat(vm.conditions.value.reassessmentAnswers).isEqualTo(answers + ("unknown-1" to null))
        assertThat(vm.conditions.value.reassessmentAnswerLimitEventId).isNull()
    }

    @Test
    fun fullRetainedAnswerMapsSurviveEditorAndExactEventDraftRecreation() = runTest {
        val repository = repositoryWith(profile())
        val saved = androidx.lifecycle.SavedStateHandle()
        val answers = (1..32).associate { "unknown-$it" to if (it % 2 == 0) null else "future" }
        val selection = wallcrawl.elopenmike.com.core.model.SelectedCondition("retained", 8, answers)
        val vm = ProfileViewModel(repository, saved)
        vm.updateConditionDraft(listOf(selection))
        vm.updateReassessmentAnswers("exact-event", answers)
        val restored = ProfileViewModel(repository, saved)
        assertThat(restored.conditions.value.draft).containsExactly(selection)
        assertThat(restored.conditions.value.reassessmentEventId).isEqualTo("exact-event")
        assertThat(restored.conditions.value.reassessmentAnswers).isEqualTo(answers)
    }

    @Test
    fun conditionDraftKeepsUnknownAnswersAndExplicitEmptyAcrossRecreation() = runTest {
        val repository = repositoryWith(profile())
        val saved = androidx.lifecycle.SavedStateHandle()
        val unknown = wallcrawl.elopenmike.com.core.model.SelectedCondition("retained", 8,
            mapOf("side" to null, "future" to "token"))
        val first = ProfileViewModel(repository, savedStateHandle = saved)
        first.updateConditionDraft(listOf(unknown))
        val second = ProfileViewModel(repository, savedStateHandle = saved)
        assertThat(second.conditions.value.draft).containsExactly(unknown)
        second.updateConditionDraft(emptyList())
        val third = ProfileViewModel(repository, savedStateHandle = saved)
        assertThat(third.conditions.value.draft).isEmpty()
    }

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
    fun togglingSensitivityCannotOverwriteAnUnrecognizedStoredRestriction() = runTest {
        val raw = "KNEE_SENSITIVE|||LEGACY"
        val repository = repositoryWith(profile().copy(unresolvedTrainingConstraintsRaw = raw))
        ProfileViewModel(repository).toggleTrainingConstraint(TrainingConstraint.KNEE_SENSITIVE)
        advanceUntilIdle()
        assertThat(repository.getProfileOnce().unresolvedTrainingConstraintsRaw).isEqualTo(raw)
        assertThat(repository.getProfileOnce().trainingConstraints).containsExactly(TrainingConstraint.KNEE_SENSITIVE)
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
        viewModel.saveMovementCapabilities()
        viewModel.cancelMovementCapabilityEditing()
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
