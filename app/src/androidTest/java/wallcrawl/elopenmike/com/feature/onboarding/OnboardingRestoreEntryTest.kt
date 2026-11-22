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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import wallcrawl.elopenmike.com.core.ui.localization.labelRes
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/**
 * How the restore entry sits inside the one-screen basic setup.
 *
 * `LocalDataControlsTest` covers the entry point and its sheet on their own. What only
 * shows up here is the wiring between them and the screen: that a restore in flight blocks
 * the action that would write a profile over it, and that using the entry neither saves
 * nor loses what was chosen.
 *
 * The restore slot is a stand-in rather than the real sheet: this is about the host, and
 * the real flow is exercised where it lives.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingRestoreEntryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun text(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun theActionStaysDisabledUntilAGoalIsChosen() {
        val viewModel = showSetup()

        composeRule.onNodeWithText(text(R.string.onboarding_basic_cta)).assertIsNotEnabled()

        composeRule.onNodeWithText(text(FitnessGoal.STRENGTH.labelRes)).performClick()

        composeRule.onNodeWithText(text(FitnessGoal.STRENGTH.labelRes)).assertIsOn()
        composeRule.onNodeWithText(text(R.string.onboarding_basic_cta)).assertIsEnabled()
        assertThat(viewModel.uiState.value.goals).containsExactly(FitnessGoal.STRENGTH)
    }

    @Test
    fun moreThanOneGoalCanBeChosenAndTappingAgainClearsIt() {
        val viewModel = showSetup()

        composeRule.onNodeWithText(text(FitnessGoal.STRENGTH.labelRes)).performClick()
        composeRule.onNodeWithText(text(FitnessGoal.FAT_LOSS.labelRes)).performClick()
        composeRule.onNodeWithText(text(FitnessGoal.STRENGTH.labelRes)).assertIsOn()
        composeRule.onNodeWithText(text(FitnessGoal.FAT_LOSS.labelRes)).assertIsOn()

        composeRule.onNodeWithText(text(FitnessGoal.STRENGTH.labelRes)).performClick()

        composeRule.onNodeWithText(text(FitnessGoal.STRENGTH.labelRes)).assertIsOff()
        assertThat(viewModel.uiState.value.goals).containsExactly(FitnessGoal.FAT_LOSS)
    }

    @Test
    fun daysAndMinutesChipsMoveTheSelection() {
        val viewModel = showSetup()

        composeRule.onNodeWithText("5").performScrollTo().performClick()
        composeRule.onNodeWithText("45").performScrollTo().performClick()

        composeRule.onNodeWithText("5").assertIsSelected()
        composeRule.onNodeWithText("3").assertIsNotSelected()
        assertThat(viewModel.uiState.value.daysPerWeek).isEqualTo(5)
        assertThat(viewModel.uiState.value.durationMinutes).isEqualTo(45)
    }

    @Test
    fun aRestoreInFlightBlocksTheActionThatWouldOverwriteIt() {
        // Saving writes a whole profile. A restore is about to commit one, so the two must
        // not race: the button says what is happening and refuses.
        val viewModel = showSetup(isRestoreInFlight = true)
        composeRule.runOnIdle { viewModel.toggleGoal(FitnessGoal.STRENGTH) }

        composeRule.onNodeWithText(text(R.string.local_data_restore_progress))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.onboarding_basic_cta)).assertDoesNotExist()
    }

    @Test
    fun theEntryIsShownOnTheSetupScreen() {
        showSetup()

        composeRule.onNodeWithText(RESTORE_SLOT).assertIsDisplayed()
    }

    @Test
    fun openingAndClosingTheEntrySavesNothingAndKeepsTheDraft() {
        // The sheet's open state lives in the slot, not in the screen. Toggling it here is
        // the whole point: the screen is never told, which is why opening or dismissing it
        // cannot save or clear what was chosen.
        var sheetOpen by mutableStateOf(false)
        val repository = FakeUserProfileRepository()
        val viewModel = OnboardingViewModel(repository)
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

        composeRule.onNodeWithText(text(FitnessGoal.FAT_LOSS.labelRes)).performClick()

        sheetOpen = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText(SHEET_OPEN).assertIsDisplayed()

        sheetOpen = false
        composeRule.waitForIdle()

        assertThat(viewModel.uiState.value.goals).containsExactly(FitnessGoal.FAT_LOSS)
        assertThat(viewModel.uiState.value.isComplete).isFalse()
        composeRule.onNodeWithText(text(FitnessGoal.FAT_LOSS.labelRes)).assertIsOn()
        composeRule.onNodeWithText(text(R.string.onboarding_basic_cta)).assertIsEnabled()
    }

    private fun showSetup(
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
