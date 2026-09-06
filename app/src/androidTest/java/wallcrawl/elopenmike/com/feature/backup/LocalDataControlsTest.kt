package wallcrawl.elopenmike.com.feature.backup

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import android.net.Uri
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.InputStream
import java.io.OutputStream
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.database.repository.LocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.LocalDataExportResult
import wallcrawl.elopenmike.com.core.database.repository.LocalDataRestoreResult
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/**
 * The destructive path and the accessibility surface of the local-data controls.
 *
 * Deleting everything must never be one tap away, restore must explain itself when it is
 * unavailable, and every control has to stay reachable at a large display font scale.
 */
class LocalDataControlsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun deletingEverything_requiresAnExplicitConfirmationThatNamesWhatIsLost() {
        val repository = FakeBackupRepository(restoreAllowed = false)
        val viewModel = showSection(repository)

        composeRule.onNodeWithTag(LOCAL_DATA_DELETE_TEST_TAG)
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        composeRule.onNodeWithText("Delete all local data?").assertIsDisplayed()
        // The confirmation names the active workout and the boundary of the deletion.
        composeRule.onNodeWithText("including one in progress", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Files you already exported are not deleted", substring = true)
            .assertIsDisplayed()
        assertThat(repository.deleteCount).isEqualTo(0)

        composeRule.onNodeWithText("Keep my data").performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { !viewModel.uiState.value.isConfirmingDelete }
        assertThat(repository.deleteCount).isEqualTo(0)
    }

    @Test
    fun deleteConfirmation_staysLegibleInTheLightTheme() {
        // The shared typography hard-codes a light colour into the styles an AlertDialog uses
        // for its title and button labels, so without explicit colours the title and the safe
        // way out render near-invisibly on the light dialog surface while the destructive
        // action stays readable. Asserted on rendered pixels: a text-only assertion passes
        // either way.
        val viewModel = LocalDataViewModel(
            context.contentResolver,
            FakeBackupRepository(restoreAllowed = false)
        )
        composeRule.setContent {
            WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LocalDataSection(viewModel = viewModel)
                }
            }
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.restoreAllowed != null }
        composeRule.onNodeWithTag(LOCAL_DATA_DELETE_TEST_TAG).performScrollTo().performClick()

        // Nothing the user must read is painted in the dialog surface's own near-white.
        composeRule.onNodeWithText("Delete all local data?")
            .assertDoesNotRenderOnlyNear(Color.White)
        composeRule.onNodeWithText("Keep my data").assertDoesNotRenderOnlyNear(Color.White)
        composeRule.onNodeWithText("Delete everything").assertDoesNotRenderOnlyNear(Color.White)
    }

    @Test
    fun confirmingDeletion_deletesOnceAndSignalsTheHostScreen() {
        val repository = FakeBackupRepository(restoreAllowed = false)
        var deletedSignals = 0
        showSection(repository, onDeleted = { deletedSignals += 1 })

        composeRule.onNodeWithTag(LOCAL_DATA_DELETE_TEST_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Delete everything").performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) { deletedSignals == 1 }
        assertThat(repository.deleteCount).isEqualTo(1)
    }

    @Test
    fun restore_isDisabledAndExplainedWhileTheAppStillHoldsData() {
        showSection(FakeBackupRepository(restoreAllowed = false))

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_TEST_TAG)
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Restoring needs a fresh start", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun restore_staysOfferedWhenEligibilityCouldNotBeRead() {
        // The restore transaction rechecks eligibility from inside, so offering the control
        // is safe and is the only way a transient read failure recovers.
        val viewModel = LocalDataViewModel(
            context.contentResolver,
            FakeBackupRepository(restoreAllowed = true, failEligibility = true)
        )
        composeRule.setContent {
            ScrollableTestScreen { LocalDataSection(viewModel = viewModel) }
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            viewModel.uiState.value.restoreEligibilityUnknown
        }

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_TEST_TAG)
            .performScrollTo()
            .assertIsEnabled()
        composeRule.onNodeWithText("Couldn't check whether", substring = true).assertIsDisplayed()
    }

    @Test
    fun restore_isOfferedOnceTheAppIsAFreshStart() {
        showSection(FakeBackupRepository(restoreAllowed = true))

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_TEST_TAG)
            .performScrollTo()
            .assertIsEnabled()
            .assertHasClickAction()
    }

    @Test
    fun everyControlStaysOperableAtALargeFontScale() {
        val repository = FakeBackupRepository(restoreAllowed = true)
        val viewModel = LocalDataViewModel(context.contentResolver, repository)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)
            ) {
                ScrollableTestScreen {
                    LocalDataSection(viewModel = viewModel)
                }
            }
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.restoreAllowed != null }

        listOf(
            LOCAL_DATA_EXPORT_TEST_TAG,
            LOCAL_DATA_RESTORE_TEST_TAG,
            LOCAL_DATA_DELETE_TEST_TAG
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }

    @Test
    fun onboardingEntry_keepsTheRestoreFlowBehindAQuietButton() {
        // Onboarding's first screen belongs to the name field. The restore flow is a whole
        // panel of instructions, eligibility text and errors, so it lives in a sheet and
        // only its one-line entry sits on the screen.
        val viewModel = showOnboardingEntry(FakeBackupRepository(restoreAllowed = true))

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.onboarding_restore_action)).assertIsDisplayed()

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_ENTRY_TEST_TAG)
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_TEST_TAG).assertIsEnabled()
        // Opening the flow is not starting it: nothing has been written or picked yet.
        assertThat(viewModel.uiState.value.operation).isEqualTo(LocalDataOperation.IDLE)
    }

    @Test
    fun onboardingSheet_saysWhatIsActuallyRestored() {
        // The entry says "profile" because that is what someone setting up is looking for.
        // Restoring still replaces the whole archive, and the sheet is where that is said.
        showOnboardingEntry(FakeBackupRepository(restoreAllowed = true))

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_ENTRY_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(text(R.string.onboarding_restore_scope)).assertIsDisplayed()
    }

    @Test
    fun onboardingEntry_offersRestoreWithoutTheDestructiveControls() {
        showOnboardingEntry(FakeBackupRepository(restoreAllowed = true))

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_ENTRY_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_TEST_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(LOCAL_DATA_DELETE_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LOCAL_DATA_EXPORT_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun onboardingEntry_staysReachableBeforeAnythingIsTypedAndSaysWhenBlocked() {
        // A fresh install has to reach this with the wizard untouched; an app that already
        // holds data has to be told why it cannot restore.
        showOnboardingEntry(FakeBackupRepository(restoreAllowed = false))

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_ENTRY_TEST_TAG)
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(text(R.string.local_data_restore_blocked)).assertIsDisplayed()
        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun onboardingOutcomeIsReportedEvenWithTheSheetClosed() {
        // The sheet can be dismissed while a restore is still reading the document. If the
        // outcome text lived inside it, a failure arriving afterwards would be reported to
        // nobody and the entry would just re-enable as if nothing had happened.
        val viewModel = showOnboardingEntry(FakeBackupRepository(restoreAllowed = true))

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_SHEET_TEST_TAG).assertDoesNotExist()
        viewModel.restoreFrom(Uri.parse("content://wallcrawl.test.absent/archive.json"))
        composeRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.message != null }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LOCAL_DATA_MESSAGE_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun onboardingOutcomeIsReportedWithTheSheetStillOpen() {
        // The ordinary path: the picker returns to a sheet that never closed, and the
        // restore fails there. A message rendered only behind the sheet would be invisible
        // exactly when it matters most.
        val viewModel = showOnboardingEntry(FakeBackupRepository(restoreAllowed = true))

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_ENTRY_TEST_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_SHEET_TEST_TAG).assertIsDisplayed()

        viewModel.restoreFrom(Uri.parse("content://wallcrawl.test.absent/archive.json"))
        composeRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.message != null }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_SHEET_TEST_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(LOCAL_DATA_MESSAGE_TEST_TAG)
            .filterToOne(hasAnyAncestor(hasTestTag(LOCAL_DATA_RESTORE_SHEET_TEST_TAG)))
            .assertIsDisplayed()
    }

    @Test
    fun theOnboardingSheetStaysOperableAtALargeFontScale() {
        // The sheet is explanation first and action last, so at 2x the action is the part
        // that falls off the bottom. It has to be reachable, not just present.
        val viewModel = LocalDataViewModel(
            context.contentResolver,
            FakeBackupRepository(restoreAllowed = true)
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = LARGE_FONT_SCALE)
            ) {
                RestoreFromArchiveButton(viewModel = viewModel)
            }
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.restoreAllowed != null }

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_ENTRY_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LOCAL_DATA_RESTORE_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    private fun showOnboardingEntry(repository: FakeBackupRepository): LocalDataViewModel {
        val viewModel = LocalDataViewModel(context.contentResolver, repository)
        composeRule.setContent {
            ScrollableTestScreen {
                RestoreFromArchiveButton(viewModel = viewModel)
            }
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.restoreAllowed != null }
        return viewModel
    }

    private fun text(@StringRes id: Int): String = context.getString(id)

    private fun showSection(
        repository: FakeBackupRepository,
        onDeleted: () -> Unit = {}
    ): LocalDataViewModel {
        val viewModel = LocalDataViewModel(context.contentResolver, repository)
        composeRule.setContent {
            ScrollableTestScreen {
                LocalDataOutcomeEffect(viewModel = viewModel, onDeleted = onDeleted)
                LocalDataSection(viewModel = viewModel)
            }
        }
        // The controls describe themselves only once the destination has been inspected.
        composeRule.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.restoreAllowed != null }
        return viewModel
    }

    @Composable
    private fun ScrollableTestScreen(content: @Composable () -> Unit) {
        WallCrawlTheme {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
        }
    }


    /**
     * Fails when every pixel of a node sits within [NEAR_WHITE_TOLERANCE] of [background].
     *
     * Text drawn in the container's own colour leaves nothing but that colour behind, so this
     * catches the invisible case without asserting an exact palette value.
     */
    private fun SemanticsNodeInteraction.assertDoesNotRenderOnlyNear(background: Color) {
        val pixels = captureToImage().toPixelMap()
        var furthest = 0f
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val pixel = pixels[x, y]
                val distance = square(pixel.red - background.red) +
                    square(pixel.green - background.green) +
                    square(pixel.blue - background.blue)
                if (distance > furthest) furthest = distance
            }
        }
        assertThat(furthest).isGreaterThan(NEAR_WHITE_TOLERANCE)
    }

    private fun square(value: Float) = value * value

    /** Records what the controls asked for; no document or database is involved. */
    private class FakeBackupRepository(
        private val restoreAllowed: Boolean,
        private val failEligibility: Boolean = false
    ) : LocalDataBackupRepository {
        var deleteCount: Int = 0
            private set

        override suspend fun isRestoreAllowed(): Boolean {
            if (failEligibility) throw java.io.IOException("the database could not be read")
            return restoreAllowed
        }

        override suspend fun exportTo(openOutput: () -> OutputStream) =
            LocalDataExportResult(sessionCount = 0, templateCount = 0)

        override suspend fun restoreFrom(input: InputStream) = LocalDataRestoreResult(
            onboardingCompleted = true,
            sessionCount = 0,
            templateCount = 0
        )

        override suspend fun deleteAllLocalData() {
            deleteCount += 1
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val LARGE_FONT_SCALE = 2f

        /** Squared RGB distance a pixel must exceed to count as ink rather than background. */
        const val NEAR_WHITE_TOLERANCE = 0.05f
    }
}
