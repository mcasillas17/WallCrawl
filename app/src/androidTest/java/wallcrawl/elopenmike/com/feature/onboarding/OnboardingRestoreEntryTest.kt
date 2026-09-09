package wallcrawl.elopenmike.com.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.ProfileGender
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/**
 * How the restore entry sits inside the wizard.
 *
 * `LocalDataControlsTest` covers the entry point and its sheet on their own. What only
 * shows up here is the wiring between them and the wizard: that a restore in flight blocks
 * the step that would write a profile over it, that the entry belongs to the first step
 * alone, and that using it neither advances the wizard nor loses what was typed.
 *
 * The restore slot is a stand-in rather than the real sheet — this is about the host, and
 * the real flow is exercised where it lives.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingRestoreEntryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun text(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun genderDropdownKeepsTheNameAndDraftWhenReturningToWelcome() {
        val viewModel = showWizard()
        composeRule.onNodeWithText(text(R.string.onboarding_codename_placeholder))
            .performTextInput("Alex")
        composeRule.onNodeWithText(text(R.string.profile_gender_title)).performClick()
        composeRule.onNodeWithText(text(R.string.gender_woman)).performClick()
        composeRule.onNodeWithText("Alex").assertIsDisplayed()
        assertThat(viewModel.uiState.value.gender).isEqualTo(ProfileGender.WOMAN)

        composeRule.onNodeWithText(text(R.string.onboarding_action_continue)).performClick()
        composeRule.runOnIdle { viewModel.previousStep() }

        composeRule.onNodeWithText(text(R.string.gender_woman)).assertIsDisplayed()
        composeRule.onNodeWithText("Alex").assertIsDisplayed()
        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.WELCOME)
    }

    @Test
    fun aRestoreInFlightBlocksTheStepThatWouldOverwriteIt() {
        // Finishing the wizard writes a whole profile. A restore is about to commit one, so
        // the two must not race: Continue says what is happening and refuses.
        showWizard(isRestoreInFlight = true)

        composeRule.onNodeWithText(text(R.string.local_data_restore_progress))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.onboarding_action_continue)).assertDoesNotExist()
    }

    @Test
    fun theEntryBelongsToTheFirstStepOnly() {
        val viewModel = showWizard()

        composeRule.onNodeWithText(RESTORE_SLOT).assertIsDisplayed()

        // The first step will not advance without a name, so give it one.
        composeRule.onNodeWithText(text(R.string.onboarding_codename_placeholder))
            .performTextInput("Alex")
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.onboarding_action_continue)).performClick()
        composeRule.waitForIdle()

        assertThat(viewModel.uiState.value.currentStep).isNotEqualTo(OnboardingStep.WELCOME)
        composeRule.onNodeWithText(RESTORE_SLOT).assertDoesNotExist()
    }

    @Test
    fun openingAndClosingTheEntryAdvancesNothingAndKeepsTheDraft() {
        // The sheet's open state lives in the slot, not in the wizard. Toggling it here is
        // the whole point: the wizard is never told, which is why opening or dismissing it
        // cannot move the step or clear what was typed.
        var sheetOpen by mutableStateOf(false)
        val viewModel = OnboardingViewModel(FakeUserProfileRepository())
        composeRule.setContent {
            WallCrawlTheme {
                OnboardingScreen(
                    viewModel = viewModel,
                    onCompleted = {},
                    restoreFromArchive = {
                        Text(if (sheetOpen) SHEET_OPEN else RESTORE_SLOT)
                    }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(text(R.string.onboarding_codename_placeholder))
            .performTextInput("Gabriela")
        composeRule.waitForIdle()

        sheetOpen = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText(SHEET_OPEN).assertIsDisplayed()

        sheetOpen = false
        composeRule.waitForIdle()

        assertThat(viewModel.uiState.value.currentStep).isEqualTo(OnboardingStep.WELCOME)
        assertThat(viewModel.uiState.value.name).isEqualTo("Gabriela")
        composeRule.onNodeWithText("Gabriela").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.onboarding_action_continue)).assertIsEnabled()
    }

    private fun showWizard(
        isRestoreInFlight: Boolean = false,
        restoreSlot: @Composable () -> Unit = { Text(RESTORE_SLOT) }
    ): OnboardingViewModel {
        val viewModel = OnboardingViewModel(FakeUserProfileRepository())
        composeRule.setContent {
            WallCrawlTheme {
                OnboardingScreen(
                    viewModel = viewModel,
                    onCompleted = {},
                    restoreFromArchive = restoreSlot,
                    isRestoreInFlight = isRestoreInFlight
                )
            }
        }
        composeRule.waitForIdle()
        return viewModel
    }

    private companion object {
        const val RESTORE_SLOT = "restore-slot"
        const val SHEET_OPEN = "restore-sheet-open"
    }
}

private class FakeUserProfileRepository : UserProfileRepository {
    private val profile = MutableStateFlow(UserProfile())

    override fun getUserProfile(): Flow<UserProfile> = profile
    override suspend fun getProfileOnce(): UserProfile = profile.value
    override suspend fun saveUserProfile(profile: UserProfile) {
        this.profile.value = profile
    }

    override suspend fun saveProfile(profile: UserProfile) {
        this.profile.value = profile
    }

    override suspend fun updateGoals(goals: Set<FitnessGoal>) = error("Not used")
    override suspend fun updatePrimaryGoal(goal: FitnessGoal) = error("Not used")
    override suspend fun updateExperienceLevel(level: ExperienceLevel) = error("Not used")
    override suspend fun updatePreferredDuration(minutes: Int) = error("Not used")
    override suspend fun updateDaysPerWeek(days: Int) = error("Not used")
    override suspend fun updateEquipment(equipment: List<String>) = error("Not used")
    override suspend fun updateUnit(unit: WeightUnit) = error("Not used")
    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) =
        error("Not used")

    override suspend fun updateExcludedExercises(excludedIds: List<String>) = error("Not used")
    override suspend fun updateTrainingConstraints(constraints: Set<TrainingConstraint>) =
        error("Not used")

    override suspend fun updateReturningAfterBreakWeeks(weeks: Int) = error("Not used")
    override suspend fun updateGender(gender: wallcrawl.elopenmike.com.core.model.ProfileGender) = saveProfile(getProfileOnce().copy(gender = gender))
    override suspend fun updateThemePreference(themePreference: ThemePreference) =
        error("Not used")
}
