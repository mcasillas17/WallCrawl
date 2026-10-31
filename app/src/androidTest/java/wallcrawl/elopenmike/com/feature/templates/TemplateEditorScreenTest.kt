package wallcrawl.elopenmike.com.feature.templates

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.ui.localization.longLabelRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationParser
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationStore
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity
import androidx.compose.ui.test.assertCountEquals
import wallcrawl.elopenmike.com.core.ui.localization.ExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.test.hasLiveRegion

/**
 * The editor in a real runtime: typed values, both shipped languages, and a compact screen
 * at the largest font scale the platform offers.
 *
 * Values go in through `performTextReplacement`, which focuses the field and may raise the
 * soft keyboard, but most cases neither wait for it nor ask where it covers. The
 * `withTheKeyboardOpen…` cases tap a field, wait until the window insets report the
 * keyboard, and check that what the user needs ends above its top edge.
 *
 * Everything here uses an in-memory database and the app's own external files directory,
 * so a shared device keeps whatever else is on it.
 */
class TemplateEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** WCAG AA for large text. Body copy clears it comfortably; unreadable text is near 1:1. */
    private val MIN_READABLE_CONTRAST = 3.0

    /**
     * A control that is deliberately inactive is drawn muted, and WCAG exempts it from the
     * minimum above; Material's disabled alpha lands near 2.6:1. It still has to say what it
     * is, and the defects this catches sit at 1.0-1.1:1.
     */
    private val MIN_DISABLED_CONTRAST = 2.0

    /** The repository's local-data write gate; holding it keeps a save in flight. */
    private val writeGate = kotlinx.coroutines.sync.Mutex()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var profiles: OfflineUserProfileRepository

    private val squat = InMemoryExerciseCatalog.SAMPLE_EXERCISES
        .single { it.id == "barbell-back-squat" }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        profiles = OfflineUserProfileRepository(database.userProfileDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun typingALoadThroughTheSheetReachesTheRowSummaryInEnglish() {
        val editor = showEditor(language = "en")

        openSheetFor(editor)
        editor.resources.fieldNode(R.string.set_field_load, withUnit = true)
            .performTextReplacement("60")
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_action_apply))
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            val row = editor.viewModel.uiState.value.selectedExercises.single()
            assertThat(row.load).isEqualTo("60")
            assertThat(
                (row.validate(WeightUnit.LBS) as DraftValidation.Valid).exercise.prescription.targetWeight
            ).isEqualTo(60.0)
        }
        composeRule.onNode(hasText(substring = true, text = "60")).assertIsDisplayed()
        capture("editor-row-en")
    }


    @Test
    fun anInvertedRepRangeShowsItsErrorAndLeavesApplyReachable() {
        val editor = showEditor(language = "en")

        openSheetFor(editor)
        editor.resources.fieldNode(R.string.editor_field_rep_min).performTextReplacement("12")
        editor.resources.fieldNode(R.string.editor_field_rep_max).performTextReplacement("2")
        val apply = composeRule.onNodeWithText(
            editor.resources.getString(R.string.editor_action_apply)
        )
        apply.performScrollTo().performClick()

        val error = editor.resources.getString(R.string.draft_error_range_inverted)
        composeRule.onNodeWithText(error).performScrollTo().assertIsDisplayed()
        // While it is the thing on screen: scrolling to the apply action can move it off.
        assertPainted(error)
        // Apply leaves the sheet as it was, so the refusal is announced rather than only shown.
        // The list's own error card is hidden while a sheet is open so it is not announced
        // twice; this layout never composes that card behind the sheet, so the count below
        // does not test the guard, which is checked by inspection only.
        composeRule.onAllNodes(
            hasText(editor.resources.getString(R.string.editor_error_invalid_fields)).and(hasLiveRegion())
        ).assertCountEquals(1)
        apply.performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            // The row keeps its original values; only the sheet's working copy is wrong.
            assertThat(editor.viewModel.uiState.value.editorDraft).isNotNull()
            assertThat(editor.viewModel.uiState.value.selectedExercises.single().repMax)
                .isNotEqualTo("2")
        }
        capture("editor-sheet-error-en")
    }


    @Test
    fun aDecimalCommaTypedIntoTheSheetIsStoredAsTheSameNumber() {
        val editor = showEditor(language = "en")

        openSheetFor(editor)
        editor.resources.fieldNode(R.string.set_field_load, withUnit = true)
            .performTextReplacement("2,5")
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_action_apply))
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            val row = editor.viewModel.uiState.value.selectedExercises.single()
            assertThat(row.load).isEqualTo("2,5")
            assertThat(
                (row.validate(WeightUnit.LBS) as DraftValidation.Valid).exercise.prescription.targetWeight
            ).isEqualTo(2.5)
        }
    }

    @Test
    fun theEditorScreenRendersInSpanish() {
        val editor = showEditor(language = "es")

        listOf(
            R.string.editor_title_create,
            R.string.editor_name_label,
            R.string.editor_target_sets
        ).forEach { label ->
            // The title sits above the list, so these are asserted where they are drawn
            // rather than scrolled to.
            composeRule.onNodeWithText(editor.resources.getString(label)).assertIsDisplayed()
        }
        val edit = editor.resources.getString(R.string.editor_edit_accessibility, editor.exerciseName)
        // The list is lazy: on a short screen the row is composed only once scrolled to.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(edit))
        composeRule.onNodeWithContentDescription(edit).assertIsDisplayed()
        capture("editor-screen-es")
    }

    /**
     * With the real sheet open and the keyboard up, the field error and Apply are reached
     * above the keyboard, not merely laid out behind it. A tapped field takes focus and
     * raises the IME; the test waits once for the window insets to report it, and each
     * check then fails if the keyboard has closed.
     */
    @Test
    fun withTheKeyboardOpenTheSheetStillReachesItsFieldErrorAndApply() {
        val editor = showEditor(language = "en")
        openSheetFor(editor)
        val repMax = editor.resources.fieldNode(R.string.editor_field_rep_max)
        repMax.performScrollTo().performClick()
        composeRule.waitUntil(10_000) { imeTop() != null }
        editor.resources.fieldNode(R.string.editor_field_rep_min).performTextReplacement("12")
        repMax.performTextReplacement("2")

        val apply = composeRule.onNodeWithText(editor.resources.getString(R.string.editor_action_apply))
        apply.performScrollTo()
        assertAboveKeyboard(apply)
        apply.performClick()
        val error = composeRule.onNodeWithText(editor.resources.getString(R.string.draft_error_range_inverted))
        error.performScrollTo()
        assertAboveKeyboard(error)
    }

    /** The exercise picker is a sheet too: its search and results stay above the keyboard. */
    @Test
    fun withTheKeyboardOpenThePickerSearchAndItsResultStayAboveIt() {
        val editor = showEditor(language = "en")
        // The editor already lists this exercise; removed, so the only row naming it is the
        // picker's own result rather than the editor's card behind the sheet.
        composeRule.runOnIdle {
            editor.viewModel.removeExercise(editor.viewModel.uiState.value.selectedExercises.single().rowId)
        }
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_action_add)).performClick()
        val search = composeRule.onNode(hasSetTextAction() and hasText("Search", substring = true))
        search.performClick()
        composeRule.waitUntil(10_000) { imeTop() != null }
        assertAboveKeyboard(search)
        search.performTextInput("squat")
        // The picker's result is the one tappable row naming the exercise.
        val matches = hasClickAction() and hasText(editor.exerciseName, substring = true, ignoreCase = true)
        composeRule.onAllNodes(matches).assertCountEquals(1)
        val result = composeRule.onNode(matches)
        assertAboveKeyboard(result)
    }

    /**
     * The compact case: the sheet's form at 320 dp and the largest font scale in Spanish,
     * rendered directly for the reason the compact reachability test gives, inside the same
     * window insets `ModalBottomSheet` applies to its content (which include the keyboard).
     * With the keyboard up, the form still scrolls Apply above it.
     */
    @Test
    fun withTheKeyboardOpenTheCompactSpanishSheetFormStillReachesApply() {
        val localized = showCompactSpanishSheetForm(withSheetInsets = true)
        localized.fieldNode(R.string.editor_field_rep_min).performScrollTo().performClick()
        composeRule.waitUntil(10_000) { imeTop() != null }
        val apply = composeRule.onNodeWithText(localized.getString(R.string.editor_action_apply))
        apply.performScrollTo()
        assertAboveKeyboard(apply)
    }

    /**
     * The sheet's form on a 320 dp screen at the largest font scale, in Spanish, rendered
     * directly. [withSheetInsets] wraps it in the insets `ModalBottomSheet` gives its content.
     */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    private fun showCompactSpanishSheetForm(withSheetInsets: Boolean = false): Context {
        // 2.0f is the maximum the platform Font size accessibility setting reaches.
        val localized = configurationContext("es", fontScale = 2.0f, screenWidthDp = 320)
        val draft = TemplateExerciseDraft.from(
            exercise = PlannedExercise(
                exerciseId = squat.id,
                prescription = ExercisePrescription(
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    targetSets = 3,
                    repRange = RepRange(8, 12),
                    targetWeight = 60.0
                )
            ),
            locale = Locale.forLanguageTag("es"),
            rowId = "row-1"
        )
        composeRule.setContent {
            InConfiguration(localized = localized, fontScale = 2.0f, widthDp = 320) {
                val insets = if (withSheetInsets) {
                    Modifier.windowInsetsPadding(androidx.compose.material3.BottomSheetDefaults.windowInsets)
                } else {
                    Modifier
                }
                Box(insets) {
                    PrescriptionSheetContent(
                        state = TemplateEditorUiState(
                            isLoading = false,
                            editorDraft = draft,
                            weightUnit = WeightUnit.LBS
                        ),
                        draft = draft,
                        exerciseName = "Sentadilla con barra",
                        onFieldChange = { _, _ -> },
                        onNotesChange = {},
                        onCancel = {},
                        onApply = {}
                    )
                }
            }
        }
        return localized
    }

    /** The keyboard's top edge on screen, in pixels, or null while no keyboard is shown. */
    private fun imeTop(): Float? {
        var top: Float? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
            resumed.forEach { activity ->
                val decor = activity.window.decorView
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor) ?: return@forEach
                if (!insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())) return@forEach
                val origin = IntArray(2).also(decor::getLocationOnScreen)
                val keyboard = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                top = (origin[1] + decor.height - keyboard).toFloat()
            }
        }
        return top
    }

    /** [node]'s bottom edge on screen is above the keyboard, so it can be seen and tapped. */
    private fun assertAboveKeyboard(node: SemanticsNodeInteraction) {
        val keyboardTop = checkNotNull(imeTop()) { "The keyboard closed before the check" }
        val semantics = node.fetchSemanticsNode()
        val bottom = semantics.positionOnScreen.y + semantics.size.height
        assertWithMessage("bottom at $bottom px, keyboard top at $keyboardTop px")
            .that(bottom).isAtMost(keyboardTop)
    }

    /**
     * The sheet's form, on a 320 dp screen at the largest font scale, in the language with
     * the longer labels. It renders [PrescriptionSheetContent] directly rather than through
     * `ModalBottomSheet`: the sheet's dialog window takes the device configuration, so a
     * composition-local language and font-scale override would not reach inside it.
     */
    @Test
    fun theSheetFormStaysReachableOnACompactScreenAtTheLargestFontScaleInSpanish() {
        val localized = showCompactSpanishSheetForm()

        listOf(
            R.string.editor_target_sets,
            R.string.editor_field_rep_min,
            R.string.editor_field_rep_max,
            R.string.editor_field_rest,
            R.string.editor_field_effort_min,
            R.string.editor_field_effort_max,
            R.string.editor_field_exercise_notes
        ).forEach { label ->
            composeRule.onNodeWithText(localized.getString(label), substring = true)
                .performScrollTo()
                .assertIsDisplayed()
        }
        localized.fieldNode(R.string.set_field_load, withUnit = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(localized.getString(R.string.editor_action_apply))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(localized.getString(R.string.action_cancel))
            .performScrollTo()
            .assertIsDisplayed()
        capture("editor-sheet-compact-large-text-es")
    }

    @Test
    fun leavingWithUnsavedEditsAsksBeforeDiscarding() {
        val editor = showEditor(language = "en")

        composeRule.onNodeWithContentDescription(
            editor.resources.getString(R.string.action_back)
        ).performClick()

        composeRule
            .onNodeWithText(editor.resources.getString(R.string.editor_discard_title))
            .assertIsDisplayed()
        assertThat(editor.backCount()).isEqualTo(0)
        // The dialog owns its own window, so capturing the main root would leave out the
        // title and the keep-editing action and show a confirmation with no way out.
        capture("editor-discard-en", composeRule.onNode(isDialog()))
        assertPainted(
            editor.resources.getString(R.string.editor_discard_title),
            editor.resources.getString(R.string.editor_discard_message),
            editor.resources.getString(R.string.editor_discard_keep),
            editor.resources.getString(R.string.editor_discard_confirm)
        )

        composeRule
            .onNodeWithText(editor.resources.getString(R.string.editor_discard_keep))
            .performClick()
        composeRule.runOnIdle {
            assertThat(editor.viewModel.uiState.value.showDiscardConfirmation).isFalse()
            assertThat(editor.backCount()).isEqualTo(0)
        }

        composeRule.onNodeWithContentDescription(
            editor.resources.getString(R.string.action_back)
        ).performClick()
        composeRule
            .onNodeWithText(editor.resources.getString(R.string.editor_discard_confirm))
            .performClick()
        composeRule.runOnIdle { assertThat(editor.backCount()).isEqualTo(1) }
    }

    @Test
    fun everyRestingFieldLabelIsReadableAgainstItsField() {
        // An empty field shows its label inside the box, where the only thing identifying
        // the field is that word. These painted the dark-theme colour on a light surface —
        // about 1.1:1 — so the editor opened on two boxes with nothing legible in them, and
        // every semantics assertion passed the whole time.
        val editor = showEditor(language = "en")

        assertPainted(
            editor.resources.getString(R.string.editor_name_label),
            editor.resources.getString(R.string.editor_notes_label),
            // Shared component. Naming a colour is what keeps this label on screen, and
            // the round that made its disabled state visible took the label with it.
            editor.resources.getString(R.string.editor_action_add)
        )

        openSheetFor(editor)
        listOf(R.string.editor_field_rep_min, R.string.editor_field_exercise_notes)
            .map(editor.resources::getString)
            .forEach { label ->
                // A node scrolled out of the sheet has no pixels to measure.
                composeRule.onNodeWithText(label).performScrollTo()
                assertPainted(label)
            }
        // What the user types takes the same inherited colour unless the field names one.
        // Its pixels cannot be told apart from the label and cursor drawn around it, so the
        // colour it is laid out in is compared with the field's own surface.
        val note = "Pause at the bottom"
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_field_exercise_notes))
            .performTextInput(note)
        assertLaidOutReadable(note)
    }

    @Test
    fun aSearchTypedIntoThePickerIsReadable() {
        val editor = showEditor(language = "en")
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_action_add)).performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Search", substring = true))
            .performTextInput("squat")
        assertLaidOutReadable("squat")
    }

    @Test
    fun theExerciseCapIsStatedBesideTheControlItDisables() {
        // The rows go in one at a time through the picker, which is what `addExercise`
        // models; nothing else here reaches past the screen. The cap has to be stated on
        // this surface: adding the last row closes the picker, and the control that would
        // reopen it is disabled from that moment, so anything written inside the sheet
        // cannot be read again. A unit test asserting the view model sets a message says
        // nothing about whether the message has anywhere to appear.
        val editor = showEditor(language = "en")
        composeRule.runOnIdle {
            repeat(WorkoutTemplate.MAX_EXERCISES - 1) { editor.viewModel.addExercise(squat) }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertThat(editor.viewModel.uiState.value.isAtExerciseLimit).isTrue()
            assertThat(editor.viewModel.uiState.value.isPickerOpen).isFalse()
        }

        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_action_add))
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule
            .onNodeWithText(editor.resources.getString(R.string.editor_error_exercise_limit))
            .performScrollTo()
            .assertIsDisplayed()
        assertPainted(editor.resources.getString(R.string.editor_error_exercise_limit))
        // A disabled control is drawn muted on purpose, and still has to say what it is.
        assertPainted(
            editor.resources.getString(R.string.editor_action_add),
            minimum = MIN_DISABLED_CONTRAST
        )
    }

    @Test
    fun systemBackWithUnsavedEditsAlsoAsksBeforeDiscarding() {
        // The toolbar arrow and system back are two code paths: one is the leaveEditor
        // lambda, the other the BackHandler's enabled gate.
        val editor = showEditor(language = "en")

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(editor.resources.getString(R.string.editor_discard_title))
            .assertIsDisplayed()
        assertThat(editor.backCount()).isEqualTo(0)
    }

    @Test
    fun systemBackClosesAnOpenPickerOrSheetBeforeItLeavesOrAsks() {
        val editor = showEditor(language = "en")

        composeRule.runOnIdle { editor.viewModel.openPicker() }
        composeRule.waitForIdle()
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertThat(editor.viewModel.uiState.value.isPickerOpen).isFalse()
            assertThat(editor.viewModel.uiState.value.showDiscardConfirmation).isFalse()
        }

        // An untouched sheet simply closes.
        openSheetFor(editor)
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertThat(editor.viewModel.uiState.value.editorDraft).isNull() }

        // A sheet holding typing asks first; keeping editing keeps the typing.
        openSheetFor(editor)
        editor.resources.fieldNode(R.string.editor_field_rep_min).performTextReplacement("4")
        // Typing focused the field and raised the keyboard; the platform's first back
        // hides that, which is not the editor's to change.
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        composeRule.waitForIdle()
        val sheetTitle = editor.resources.getString(R.string.editor_sheet_discard_title)
        composeRule.onNodeWithText(sheetTitle).assertIsDisplayed()
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_discard_keep))
            .performClick()
        composeRule.runOnIdle {
            assertThat(editor.viewModel.uiState.value.editorDraft?.repMin).isEqualTo("4")
        }
        // Still on screen, not merely still in state.
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_action_apply))
            .performScrollTo()
            .assertIsDisplayed()

        // Discarding drops only the sheet's working copy, and leaves the editor open.
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_discard_confirm))
            .performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertThat(editor.viewModel.uiState.value.editorDraft).isNull()
            assertThat(editor.viewModel.uiState.value.showDiscardConfirmation).isFalse()
            assertThat(editor.viewModel.uiState.value.selectedExercises.single().repMin)
                .isNotEqualTo("4")
        }
        assertThat(editor.backCount()).isEqualTo(0)

        // With nothing open, back is the leave request again.
        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_discard_title))
            .assertIsDisplayed()
    }

    @Test
    fun aBackGestureOnAnEditedSheetAsksFirstAndLeavesTheSheetFullSize() {
        val editor = showEditor(language = "en")
        openSheetFor(editor)
        val repMin = editor.resources.fieldNode(R.string.editor_field_rep_min)
        val restingWidth = repMin.fetchSemanticsNode().boundsInRoot.width
        assertThat(restingWidth).isGreaterThan(0f)
        repMin.performTextReplacement("4")
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()

        // What a predictive back gesture delivers once the app targets API 36 or higher:
        // progress the sheet can draw, then a commit.
        val sheetBack = sheetBackDispatcher()
        composeRule.runOnUiThread {
            sheetBack.dispatchOnBackStarted(BackEventCompat(0f, 1200f, 0f, BackEventCompat.EDGE_LEFT))
            sheetBack.dispatchOnBackProgressed(BackEventCompat(600f, 1200f, 1f, BackEventCompat.EDGE_LEFT))
            sheetBack.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_sheet_discard_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_discard_keep))
            .performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertThat(editor.viewModel.uiState.value.editorDraft?.repMin).isEqualTo("4")
        }
        // Keeping the edits must not leave the sheet shrunk to the gesture's last frame.
        assertThat(repMin.fetchSemanticsNode().boundsInRoot.width).isWithin(0.5f).of(restingWidth)
    }

    @Test
    fun aBackGestureOnAnUneditedSheetKeepsMaterialsPredictivePreview() {
        val editor = showEditor(language = "en")
        openSheetFor(editor)
        val repMin = editor.resources.fieldNode(R.string.editor_field_rep_min)
        val restingWidth = repMin.fetchSemanticsNode().boundsInRoot.width
        assertThat(restingWidth).isGreaterThan(0f)

        val sheetBack = sheetBackDispatcher()
        composeRule.runOnUiThread {
            sheetBack.dispatchOnBackStarted(BackEventCompat(0f, 1200f, 0f, BackEventCompat.EDGE_LEFT))
            sheetBack.dispatchOnBackProgressed(BackEventCompat(600f, 1200f, 1f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.waitForIdle()
        assertWithMessage("With nothing to protect, the sheet should still preview its dismissal")
            .that(repMin.fetchSemanticsNode().boundsInRoot.width).isLessThan(restingWidth)

        composeRule.runOnUiThread { sheetBack.dispatchOnBackCancelled() }
        composeRule.waitForIdle()
        assertThat(repMin.fetchSemanticsNode().boundsInRoot.width).isWithin(0.5f).of(restingWidth)
        composeRule.runOnIdle { assertThat(editor.viewModel.uiState.value.editorDraft).isNotNull() }
    }

    @Test
    fun choosingTheOtherUnitConvertsTheLoadsShownInTheLightTheme() =
        assertUnitSwitchConverts(ThemePreference.LIGHT)

    @Test
    fun choosingTheOtherUnitConvertsTheLoadsShownInTheDarkTheme() =
        assertUnitSwitchConverts(ThemePreference.DARK)

    private fun assertUnitSwitchConverts(theme: ThemePreference) {
        val editor = showEditor(language = "en", theme = theme)
        composeRule.runOnIdle {
            val row = editor.viewModel.uiState.value.selectedExercises.single()
            editor.viewModel.editExercise(row.rowId)
            editor.viewModel.updateDraftField(DraftField.LOAD, "100")
            editor.viewModel.applyDraft()
        }
        composeRule.onNode(hasText(substring = true, text = "100 LB")).assertIsDisplayed()
        val kilograms = editor.resources.getString(R.string.weight_unit_kg_long)
        assertPainted(editor.resources.getString(R.string.editor_unit_label), kilograms)

        composeRule.onNodeWithText(kilograms).performClick()

        composeRule.onNode(hasText(substring = true, text = "45.36 KG")).assertIsDisplayed()
        composeRule.runOnIdle {
            assertThat(editor.viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.KG)
        }
        capture("editor-unit-${theme.name.lowercase()}-en")
    }

    @Test
    fun aKilogramLoadStatesTheKilogramBoundItIsCheckedAgainst() {
        val editor = showEditor(language = "en")
        composeRule.runOnIdle { editor.viewModel.convertUnit(WeightUnit.KG) }
        openSheetFor(editor)
        composeRule.onNode(
            hasContentDescription(
                editor.resources.getString(
                    R.string.set_field_with_unit,
                    editor.resources.getString(R.string.set_field_load),
                    editor.resources.getString(R.string.weight_unit_kg_short)
                ),
                substring = true
            )
        ).performTextReplacement("5000")
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_action_apply))
            .performScrollTo()
            .performClick()
        composeRule.onNode(hasText(substring = true, text = "4535.92")).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("10000", substring = true).assertCountEquals(0)
    }

    @Test
    fun aLegacyTemplateAsksWhichUnitItsLoadsWereEnteredInAndLabelsWithoutConverting() {
        val legacy = WorkoutTemplate(
            id = "legacy",
            name = "Old legs",
            createdAtTimestamp = 1_000L,
            updatedAtTimestamp = 1_000L,
            exercises = listOf(
                PlannedExercise(
                    squat.id,
                    ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 3,
                        repRange = RepRange(5, 8),
                        targetWeight = 100.0
                    )
                )
            )
        )
        val editor = showEditor(language = "es", legacyTemplate = legacy)
        val title = editor.resources.getString(R.string.editor_unit_unconfirmed_title)
        // Its stored 100 has no extra decimals, so the rounding note stays away.
        composeRule.onAllNodesWithText(editor.resources.getString(R.string.editor_rounded_stored_values))
            .assertCountEquals(0)

        composeRule.onNodeWithText(title).assertIsDisplayed()
        assertPainted(title, editor.resources.getString(R.string.editor_unit_unconfirmed_message))
        // The number is shown with no unit at all rather than a guessed one.
        composeRule.onNode(hasText(substring = true, text = "100")).assertIsDisplayed()
        listOf("100 LB", "100 KG").forEach { guessed ->
            composeRule.onAllNodesWithText(guessed, substring = true).assertCountEquals(0)
        }
        capture("editor-legacy-unit-es")

        composeRule.onNodeWithText(editor.resources.getString(R.string.weight_unit_lbs_long))
            .performClick()

        composeRule.onAllNodesWithText(title).assertCountEquals(0)
        composeRule.onNode(hasText(substring = true, text = "100 LB")).assertIsDisplayed()
    }

    @Test
    fun aLegacyDraftWithNoLoadsLeftTakesTheProfileUnit() {
        // Once nothing stored without a unit is left, the question goes with the loads and
        // the draft takes the profile unit, so the next load is entered in a stated unit.
        val legacy = WorkoutTemplate(
            id = "legacy-emptied",
            name = "Old legs",
            createdAtTimestamp = 1_000L,
            updatedAtTimestamp = 1_000L,
            exercises = listOf(
                PlannedExercise(
                    squat.id,
                    ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 3,
                        repRange = RepRange(5, 8),
                        targetWeight = 100.0
                    )
                )
            )
        )
        val editor = showEditor(language = "en", legacyTemplate = legacy)
        val title = editor.resources.getString(R.string.editor_unit_unconfirmed_title)
        composeRule.onNodeWithText(title).assertIsDisplayed()

        composeRule.runOnIdle {
            editor.viewModel.removeExercise(editor.viewModel.uiState.value.selectedExercises.single().rowId)
        }
        composeRule.onAllNodesWithText(title).assertCountEquals(0)
        composeRule.runOnIdle {
            assertThat(editor.viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.LBS)
        }
        composeRule.onNodeWithText(editor.resources.getString(R.string.editor_unit_label)).assertIsDisplayed()
    }

    @Test
    fun aTemplateStoredWithExtraDecimalsSaysTheEditorRoundedThem() {
        val stored = WorkoutTemplate(
            id = "precise",
            name = "Precise",
            createdAtTimestamp = 1_000L,
            updatedAtTimestamp = 1_000L,
            exercises = listOf(
                PlannedExercise(
                    squat.id,
                    ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 3,
                        repRange = RepRange(5, 8),
                        targetWeight = 61.23496995
                    )
                )
            )
        )
        val editor = showEditor(language = "es", fontScale = 1.8f, screenWidthDp = 320, legacyTemplate = stored)
        val note = editor.resources.getString(R.string.editor_rounded_stored_values)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(note))
        composeRule.onNodeWithText(note).assertIsDisplayed()
        assertPainted(note)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("61,23", substring = true))
        composeRule.onNode(hasText(substring = true, text = "61,23")).assertIsDisplayed()
    }

    @Test
    fun aSaveLeavesFromTheSavedStateOnceWhoeverStartedIt() {
        // Started from the view model rather than this composition's button: what a save
        // begun before a rotation looks like to the composition that replaced it.
        val editor = showEditor(language = "en")
        composeRule.runOnIdle {
            editor.viewModel.updateName("Saved from state")
            editor.viewModel.save()
        }
        composeRule.waitUntil(10_000) { editor.savedCount() > 0 }
        composeRule.waitForIdle()
        assertThat(editor.savedCount()).isEqualTo(1)
        assertThat(editor.backCount()).isEqualTo(0)
    }

    @Test
    fun aCompositionReplacedWhileTheWriteIsInFlightStillLeavesExactlyOnce() {
        runBlocking { profiles.saveProfile(UserProfile()) }
        val catalog = InMemoryExerciseCatalog(listOf(squat))
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val stored = OfflineWorkoutTemplateRepository(database.workoutTemplateDao(), catalog)
        val gated = object : wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository by stored {
            override suspend fun saveTemplate(template: WorkoutTemplate, mustExist: Boolean) {
                release.await()
                stored.saveTemplate(template, mustExist)
            }
        }
        val viewModel = TemplateEditorViewModel(
            templateId = null,
            templateRepository = gated,
            userProfileRepository = profiles,
            exerciseCatalog = catalog,
            locale = { Locale.US }
        )
        var savedCount = 0
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(composeRule)
        restoration.setContent {
            WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                TemplateEditorScreen(viewModel = viewModel, onBack = {}, onSaved = { savedCount += 1 })
            }
        }
        composeRule.waitUntil(10_000) { !viewModel.uiState.value.isLoading }
        composeRule.runOnIdle {
            viewModel.addExercise(squat)
            viewModel.updateName("Rotated mid-save")
            viewModel.save()
        }
        composeRule.runOnIdle { assertThat(viewModel.uiState.value.isSaving).isTrue() }

        // The old composition goes away and a new one, with fresh remembered state, comes up
        // while the write is still waiting; the view model outlives both, as it does a rotation.
        restoration.emulateSavedInstanceStateRestore()
        release.complete(Unit)

        composeRule.waitUntil(10_000) { savedCount > 0 }
        composeRule.waitForIdle()
        assertThat(savedCount).isEqualTo(1)

        // Recreated again after it left, as a rotation during the exit would: the restored
        // composition knows it already left and does not pop a second entry.
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertThat(savedCount).isEqualTo(1)
    }

    @Test
    fun aRefusedUnitConfirmationStatesTheKilogramLimit() {
        val legacy = WorkoutTemplate(
            id = "heavy",
            name = "Heavy",
            createdAtTimestamp = 1_000L,
            updatedAtTimestamp = 1_000L,
            // Enough rows that the end of the list is off screen.
            exercises = List(6) {
                PlannedExercise(
                    squat.id,
                    ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 3,
                        repRange = RepRange(5, 8),
                        targetWeight = 5_000.0
                    )
                )
            }
        )
        val editor = showEditor(language = "en", legacyTemplate = legacy)
        composeRule.onNodeWithText(editor.resources.getString(R.string.weight_unit_kg_long)).performClick()
        composeRule.waitForIdle()

        // Beside the chips that were tapped, with no scrolling.
        composeRule.onNode(hasText(substring = true, text = "4,535.92")).assertIsDisplayed()
        composeRule.onNode(
            hasText(substring = true, text = "4,535.92").and(hasLiveRegion())
        ).assertExists()
        // Still unconfirmed, with the numbers labelled by no unit.
        composeRule.runOnIdle { assertThat(editor.viewModel.uiState.value.weightUnit).isNull() }
    }

    @Test
    fun anAssistedRowSaysItsWeightIsAssistanceNotALoad() {
        runBlocking { profiles.saveProfile(UserProfile()) }
        val assisted = wallcrawl.elopenmike.com.core.model.Exercise(
            id = "assisted-pull-up",
            name = "Assisted Pull-up",
            primaryMuscles = listOf("Lats"),
            listedEquipment = listOf("Bodyweight"),
            type = ExerciseType.ASSISTED_BODYWEIGHT
        )
        val catalog = InMemoryExerciseCatalog(listOf(assisted))
        val viewModel = TemplateEditorViewModel(
            templateId = null,
            templateRepository = OfflineWorkoutTemplateRepository(database.workoutTemplateDao(), catalog),
            userProfileRepository = profiles,
            exerciseCatalog = catalog,
            locale = { Locale.US }
        )
        composeRule.setContent {
            WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                TemplateEditorScreen(viewModel = viewModel, onBack = {}, onSaved = {})
            }
        }
        composeRule.waitUntil(10_000) { !viewModel.uiState.value.isLoading }
        composeRule.runOnIdle {
            viewModel.addExercise(assisted)
            viewModel.editExercise(viewModel.uiState.value.selectedExercises.single().rowId)
            viewModel.updateDraftField(DraftField.ASSISTANCE, "20")
            viewModel.applyDraft()
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("assistance 20 LB", substring = true))
        composeRule.onNode(hasText(substring = true, text = "assistance 20 LB")).assertIsDisplayed()
    }

    @Test
    fun aSaveInFlightCannotBeLeftByEitherBack() {
        // Leaving would cancel a write that may already have committed; both backs wait.
        // An unchanged template, so nothing but the save itself holds system back.
        val stored = WorkoutTemplate(
            id = "unchanged",
            name = "Legs",
            createdAtTimestamp = 1_000L,
            updatedAtTimestamp = 1_000L,
            exercises = listOf(
                PlannedExercise(
                    squat.id,
                    ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 3,
                        repRange = RepRange(5, 8)
                    )
                )
            )
        )
        val editor = showEditor(language = "en", legacyTemplate = stored)
        composeRule.runOnIdle { assertThat(editor.viewModel.uiState.value.isDirty).isFalse() }
        runBlocking { writeGate.lock() }
        try {
            composeRule.runOnIdle { editor.viewModel.save() }
            composeRule.waitUntil(5_000) { editor.viewModel.uiState.value.isSaving }
            composeRule.onNodeWithContentDescription(editor.resources.getString(R.string.action_back))
                .assertIsNotEnabled()
            Espresso.pressBack()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText(editor.resources.getString(R.string.editor_discard_title))
                .assertCountEquals(0)
            composeRule.runOnIdle { assertThat(editor.backCount()).isEqualTo(0) }
        } finally {
            writeGate.unlock()
        }
        composeRule.waitUntil(10_000) { editor.savedCount() > 0 }
        composeRule.waitForIdle()
        assertThat(editor.savedCount()).isEqualTo(1)
        assertThat(editor.backCount()).isEqualTo(0)
    }

    @Test
    fun aSaveInFlightMutesItsControlsWithoutLosingThemInTheLightTheme() =
        assertSaveInFlightLegible(ThemePreference.LIGHT)

    @Test
    fun aSaveInFlightMutesItsControlsWithoutLosingThemInTheDarkTheme() =
        assertSaveInFlightLegible(ThemePreference.DARK)

    /**
     * Every control is disabled while the write runs. The selected unit is still what the
     * template will be saved in, and "Saving…" is the only sign the tap did anything, so both
     * are muted like the rest but stay legible.
     */
    private fun assertSaveInFlightLegible(theme: ThemePreference) {
        val editor = showEditor(language = "en", theme = theme)
        composeRule.runOnIdle {
            editor.viewModel.updateName("Legs")
            editor.viewModel.addExercise(squat)
        }
        runBlocking { writeGate.lock() }
        try {
            // Save sits below the row in a lazy list; how it is reached is not the point.
            composeRule.runOnIdle { editor.viewModel.save() }
            composeRule.waitUntil(5_000) { editor.viewModel.uiState.value.isSaving }
            val unit = editor.resources.getString(
                editor.viewModel.uiState.value.weightUnit!!.longLabelRes
            )
            composeRule.onNodeWithText(unit).performScrollTo()
            assertPainted(unit, minimum = MIN_DISABLED_CONTRAST)
            val saving = editor.resources.getString(R.string.editor_action_saving)
            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(saving))
            // By the colour it is laid out in, blended over the button behind it; a pixel scan
            // over the button would also meet its edge against the card.
            assertLaidOutReadable(saving, minimum = MIN_DISABLED_CONTRAST)
        } finally {
            writeGate.unlock()
        }
        // The write finishes before the test does, so the database is never closed under it.
        composeRule.waitUntil(10_000) { editor.savedCount() > 0 }
        composeRule.waitForIdle()
    }

    @Test
    fun aRefusedSaveIsAnnounced() {
        // Save stays where it was when refused, so the reason has to be spoken for anyone
        // not looking at the card it appears in.
        val editor = showEditor(language = "en")
        val save = editor.resources.getString(R.string.editor_action_save)
        // The list is lazy: on a short screen Save is composed only once scrolled to.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(save))
        composeRule.onNodeWithText(save).performClick()
        val message = editor.resources.getString(R.string.editor_error_name_required)
        composeRule.onNode(
            hasText(message).and(hasLiveRegion())
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aRefusedUnitSwitchIsAnnouncedBesideTheChips() {
        runBlocking { profiles.saveProfile(UserProfile()) }
        val catalog = InMemoryExerciseCatalog(listOf(squat))
        // A recovered row whose load is not a number yet, which no unit switch can convert.
        val unfinished = TemplateExerciseDraft(
            rowId = "row-1",
            exerciseId = squat.id,
            exerciseType = squat.type,
            sets = "3",
            repMin = "5",
            repMax = "8",
            load = "12.",
            restSeconds = "90",
            originalRestSeconds = "90"
        )
        val handle = androidx.lifecycle.SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Recovered",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf(unfinished.encode()),
                "templateEditor.weightUnit" to "LBS"
            )
        )
        val viewModel = TemplateEditorViewModel(
            templateId = null,
            templateRepository = OfflineWorkoutTemplateRepository(database.workoutTemplateDao(), catalog),
            userProfileRepository = profiles,
            exerciseCatalog = catalog,
            savedStateHandle = handle,
            locale = { Locale.US }
        )
        composeRule.setContent {
            WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                TemplateEditorScreen(viewModel = viewModel, onBack = {}, onSaved = {})
            }
        }
        composeRule.waitUntil(10_000) { !viewModel.uiState.value.isLoading }
        val resources = context.resources
        composeRule.onNodeWithText(resources.getString(R.string.weight_unit_kg_long)).performClick()

        val message = resources.getString(R.string.editor_error_unit_convert_invalid)
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNode(
            hasText(message).and(hasLiveRegion())
        ).assertExists()

        // The next change the user makes answers it, and it goes.
        composeRule.runOnIdle { viewModel.updateName("Renamed") }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(message).assertCountEquals(0)
        composeRule.onAllNodes(
            hasLiveRegion()
        ).assertCountEquals(0)
    }

    @Test
    fun aFailedLoadShowsItsErrorAndNoUnitQuestion() {
        runBlocking { profiles.saveProfile(UserProfile()) }
        val failing = object : wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog by InMemoryExerciseCatalog(listOf(squat)) {
            override fun getAllExercises() =
                kotlinx.coroutines.flow.flow<List<wallcrawl.elopenmike.com.core.model.Exercise>> {
                    throw IllegalStateException("catalog unavailable")
                }
        }
        val viewModel = TemplateEditorViewModel(
            templateId = null,
            templateRepository = OfflineWorkoutTemplateRepository(database.workoutTemplateDao(), failing),
            userProfileRepository = profiles,
            exerciseCatalog = failing,
            locale = { Locale.US }
        )
        composeRule.setContent {
            WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                TemplateEditorScreen(viewModel = viewModel, onBack = {}, onSaved = {})
            }
        }
        composeRule.waitUntil(10_000) { !viewModel.uiState.value.isLoading }
        val resources = context.resources
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(resources.getString(R.string.editor_error_load_failed)))
        composeRule.onNodeWithText(resources.getString(R.string.editor_error_load_failed)).assertIsDisplayed()
        // Nothing can be added without a loaded catalog, so the control says so by being off.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(resources.getString(R.string.editor_action_add)))
        composeRule.onNodeWithText(resources.getString(R.string.editor_action_add)).assertIsNotEnabled()
        // Nor is the user told to tap it, or offered a catalog that did not load.
        composeRule.onAllNodesWithText(resources.getString(R.string.editor_empty_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(resources.getString(R.string.editor_unit_unconfirmed_title))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(resources.getString(R.string.editor_unit_label))
            .assertCountEquals(0)
    }

    @Test
    fun theUnitControlStaysReachableInSpanishAtFontScaleOnePointEightOnACompactScreen() {
        val editor = showEditor(language = "es", fontScale = 1.8f, screenWidthDp = 320)
        val list = composeRule.onNode(hasScrollAction())
        listOf(
            R.string.editor_unit_label,
            R.string.weight_unit_kg_long,
            R.string.weight_unit_lbs_long,
            R.string.editor_action_save
        ).forEach { label ->
            val text = editor.resources.getString(label)
            list.performScrollToNode(hasText(text, substring = true))
            composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
        }
        capture("editor-unit-compact-1_8-es")
    }

    /**
     * The editor's own list surface, not the sheet, in the language with the longer labels
     * at the platform's largest font scale on a 320 dp screen. Its rows put unweighted
     * children on one line -- the exercise count beside the add control, the target-sets
     * label beside a two-button stepper -- which is exactly where a compact width and a
     * doubled font scale collide.
     */
    @Test
    fun theEditorListStaysReachableOnACompactScreenAtTheLargestFontScaleInSpanish() {
        val editor = showEditor(language = "es", fontScale = 2.0f, screenWidthDp = 320)
        val list = composeRule.onNode(hasScrollAction())

        listOf(
            R.string.editor_name_label,
            R.string.editor_action_add,
            R.string.editor_target_sets,
            R.string.editor_action_save
        ).forEach { label ->
            val label_ = editor.resources.getString(label)
            list.performScrollToNode(hasText(label_, substring = true))
            composeRule.onNodeWithText(label_, substring = true).assertIsDisplayed()
        }

        listOf(
            editor.resources.getString(
                R.string.editor_edit_accessibility,
                editor.exerciseName
            ),
            editor.resources.getString(R.string.editor_increase_sets)
        ).forEach { description ->
            list.performScrollToNode(hasContentDescription(description))
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        capture("editor-list-compact-large-text-es")
    }

    // --- harness -----------------------------------------------------------------------

    private class Editor(
        val viewModel: TemplateEditorViewModel,
        val resources: Context,
        val exerciseName: String,
        private val backCounter: () -> Int,
        private val savedCounter: () -> Int
    ) {
        fun backCount(): Int = backCounter()
        fun savedCount(): Int = savedCounter()
    }

    private fun Context.fieldNode(labelRes: Int, withUnit: Boolean = false) =
        composeRule.onNode(
            hasContentDescription(
                if (withUnit) {
                    getString(
                        R.string.set_field_with_unit,
                        getString(labelRes),
                        getString(R.string.weight_unit_lbs_short)
                    )
                } else {
                    getString(labelRes)
                },
                substring = true
            )
        )


    private fun openSheetFor(editor: Editor) {
        composeRule.onNodeWithContentDescription(
            editor.resources.getString(R.string.editor_edit_accessibility, editor.exerciseName)
        ).performClick()
        composeRule.waitUntil(10_000) { editor.viewModel.uiState.value.editorDraft != null }
        // That only says the view model opened the sheet. The sheet animates into a window
        // of its own, and its fields enter the semantics tree before they can take a value,
        // so a replacement typed at that point is silently dropped.
        val apply = editor.resources.getString(R.string.editor_action_apply)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(apply).fetchSemanticsNodes().size == 1
        }
        composeRule.waitForIdle()
    }

    /** The open sheet's own back dispatcher: the sheet is a dialog window, not the activity. */
    private fun sheetBackDispatcher(): OnBackPressedDispatcher {
        var dispatcher: OnBackPressedDispatcher? = null
        Espresso.onView(ViewMatchers.isRoot()).inRoot(RootMatchers.isDialog()).perform(
            object : ViewAction {
                override fun getConstraints(): Matcher<View> = ViewMatchers.isRoot()
                override fun getDescription() = "read the sheet window's back dispatcher"
                override fun perform(uiController: UiController, view: View) {
                    dispatcher = view.findViewTreeOnBackPressedDispatcherOwner()
                        ?.onBackPressedDispatcher
                }
            }
        )
        return checkNotNull(dispatcher)
    }

    private fun showEditor(
        language: String,
        fontScale: Float = 1f,
        screenWidthDp: Int? = null,
        theme: ThemePreference = ThemePreference.LIGHT,
        legacyTemplate: WorkoutTemplate? = null
    ): Editor {
        runBlocking {
            profiles.saveProfile(UserProfile())
            // Written beneath the repository, which refuses a new unitless load: this is
            // the shape a template saved by an earlier build has on disk.
            legacyTemplate?.let { template ->
                database.workoutTemplateDao().replaceTemplate(
                    WorkoutTemplateEntity(
                        id = template.id,
                        name = template.name,
                        notes = template.notes,
                        createdAtTimestamp = template.createdAtTimestamp,
                        updatedAtTimestamp = template.updatedAtTimestamp,
                        weightUnit = null
                    ),
                    template.exercises.mapIndexed { index, exercise ->
                        WorkoutTemplateExerciseEntity(
                            templateId = template.id,
                            orderIndex = index,
                            exerciseId = exercise.exerciseId,
                            exerciseType = exercise.prescription.exerciseType,
                            targetSets = exercise.prescription.targetSets,
                            targetRepMin = exercise.prescription.repRange?.min,
                            targetRepMax = exercise.prescription.repRange?.max,
                            targetWeight = exercise.prescription.targetWeight,
                            targetAssistanceWeight = null,
                            targetDurationSeconds = null,
                            targetDistanceMeters = null,
                            restSeconds = exercise.prescription.restSeconds,
                            notes = exercise.notes
                        )
                    }
                )
            }
        }
        val catalog = InMemoryExerciseCatalog(listOf(squat))
        val viewModel = TemplateEditorViewModel(
            templateId = legacyTemplate?.id,
            templateRepository = OfflineWorkoutTemplateRepository(
                database.workoutTemplateDao(),
                catalog,
                writeGate
            ),
            userProfileRepository = profiles,
            exerciseCatalog = catalog,
            locale = { Locale.forLanguageTag(language) }
        )
        var backCount = 0
        var savedCount = 0
        val localized = configurationContext(language, fontScale, screenWidthDp)
        val overlay = localized.assets.open(ExerciseLocalizationStore.LOCALIZATION_ASSET_PATH)
            .bufferedReader().use(ExerciseLocalizationParser()::parse)
        val vocabulary = ExerciseVocabulary(overlay, language)

        composeRule.setContent {
            InConfiguration(
                localized = localized,
                vocabulary = vocabulary,
                fontScale = fontScale,
                widthDp = screenWidthDp,
                theme = theme
            ) {
                TemplateEditorScreen(
                    viewModel = viewModel,
                    onBack = { backCount += 1 },
                    onSaved = { savedCount += 1 }
                )
            }
        }
        composeRule.waitUntil(10_000) { !viewModel.uiState.value.isLoading }
        if (legacyTemplate == null) {
            composeRule.runOnIdle { viewModel.addExercise(squat) }
            composeRule.waitForIdle()
        }
        return Editor(
            viewModel = viewModel,
            resources = localized,
            exerciseName = vocabulary.exerciseName(squat),
            backCounter = { backCount },
            savedCounter = { savedCount }
        )
    }

    private fun configurationContext(
        language: String,
        fontScale: Float = 1f,
        screenWidthDp: Int? = null
    ): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(language)))
            this.fontScale = fontScale
            screenWidthDp?.let {
                this.screenWidthDp = it
                this.smallestScreenWidthDp = it
            }
        }
    )

    /**
     * Renders [content] as the reader would see it.
     *
     * [fontScale] has to reach `LocalDensity`: Compose sizes `sp` from the density, not from
     * the configuration, so providing a scaled `Configuration` alone changes nothing. A
     * compact width likewise has to be a real measure constraint, because layout never
     * consults `Configuration.screenWidthDp`.
     */
    @Composable
    private fun InConfiguration(
        localized: Context,
        vocabulary: ExerciseVocabulary? = null,
        fontScale: Float = 1f,
        widthDp: Int? = null,
        theme: ThemePreference = ThemePreference.LIGHT,
        content: @Composable () -> Unit
    ) {
        val deviceDensity = LocalDensity.current.density
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            LocalExerciseVocabulary provides (vocabulary ?: LocalExerciseVocabulary.current),
            LocalDensity provides Density(deviceDensity, fontScale)
        ) {
            WallCrawlTheme(themePreference = theme) {
                if (widthDp == null) {
                    content()
                } else {
                    Box(Modifier.width(widthDp.dp)) { content() }
                }
            }
        }
    }

    /**
     * `assertIsDisplayed` checks bounds, not paint, and a string can be laid out, tappable
     * and unreadable. `Text` prefers its style's colour over `LocalContentColor`, and this
     * app's `Typography` sets one on every style, so any `Text` that does not name a colour
     * paints the dark-theme value — near-white on a light surface. Counting distinct pixels
     * does not catch that: anti-aliased near-white glyphs produce plenty of distinct values
     * at about 1.1:1. This measures WCAG contrast against the background instead.
     */
    private fun assertPainted(
        vararg labels: String,
        minimum: Double = MIN_READABLE_CONTRAST
    ) {
        labels.forEach { label ->
            val image = composeRule.onNodeWithText(label, useUnmergedTree = true)
                .captureToImage()
                .asAndroidBitmap()
            val colours = image.pixelCounts()
            val background = colours.maxBy { it.value }.key
            val best = colours.keys.maxOf { ColorUtils.calculateContrast(it, background) }
            assertWithMessage(
                "\"$label\" reaches only ${"%.2f".format(best)}:1 against the " +
                    "${image.width}x${image.height} px behind it, so it is on screen but " +
                    "cannot be read"
            ).that(best).isAtLeast(minimum)
        }
    }

    /**
     * Compares the colour [text] is laid out in, not its pixels, with the commonest pixel
     * behind it. A text's box can also catch a floated label, a cursor or a container's edge,
     * any of which passes a pixel check on its own.
     */
    private fun assertLaidOutReadable(text: String, minimum: Double = MIN_READABLE_CONTRAST) {
        val node = composeRule.onNodeWithText(text)
        val layouts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val textColor = layouts.single().layoutInput.style.color.toArgb()
        val surface = node.captureToImage().asAndroidBitmap().pixelCounts().maxBy { it.value }.key
        // calculateContrast draws a translucent text colour over the opaque surface first.
        val ratio = ColorUtils.calculateContrast(textColor, surface)
        assertWithMessage("\"$text\" is laid out at ${"%.2f".format(ratio)}:1 against what is behind it")
            .that(ratio).isAtLeast(minimum)
    }

    /** How often each colour occurs; the commonest is the surface behind what a node draws. */
    private fun android.graphics.Bitmap.pixelCounts(): Map<Int, Int> {
        val counts = mutableMapOf<Int, Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = getPixel(x, y)
                counts[pixel] = (counts[pixel] ?: 0) + 1
            }
        }
        return counts
    }

    /** Evidence for the pull request; written only inside the app's own files directory. */
    private fun capture(name: String, node: SemanticsNodeInteraction = composeRule.onRoot()) {
        val directory = File(context.getExternalFilesDir(null), "template-editor-screenshots")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { output ->
            check(
                node.captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, output)
            )
        }
    }
}
