package wallcrawl.elopenmike.com.feature.templates

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.PlannerFixtureContextFactory
import wallcrawl.elopenmike.com.core.database.repository.FakeUserProfileDao
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.MissingTemplateException
import wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.core.model.hasUnresolvedEquipmentRequirements
import wallcrawl.elopenmike.com.core.model.missingEquipmentAlternatives
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val squat = InMemoryExerciseCatalog.SAMPLE_EXERCISES.single { it.id == "barbell-back-squat" }
    private val pullUps = InMemoryExerciseCatalog.SAMPLE_EXERCISES.single { it.id == "pull-ups" }

    @Test
    fun manualAddAndSave_allowMissingAndUnresolvedSetupsWithoutFilteringCatalog() = runTest {
        val exercises = PlannerFixtureContextFactory().bundledCatalogProjection().exercises
        val profiles = OfflineUserProfileRepository(FakeUserProfileDao())
        profiles.saveProfile(
            UserProfile(availableEquipment = listOf(StandardEquipment.RESISTANCE_BAND))
        )
        val templates = RecordingTemplateRepository()
        val viewModel = editor(
            templates = templates,
            profiles = profiles,
            catalog = InMemoryExerciseCatalog(exercises)
        )
        val loaded = viewModel.uiState.first { !it.isLoading }
        assertThat(loaded.errorMessage).isNull()
        assertThat(loaded.catalogExercises).hasSize(302)
        val pulldown = exercises.single { it.id == "banded-lat-pulldown" }
        val row = exercises.single { it.id == "banded-row" }
        assertThat(pulldown.missingEquipmentAlternatives(loaded.availableEquipment))
            .containsExactly(listOf(StandardEquipment.BAND_ANCHOR_OVERHEAD, StandardEquipment.CHAIR))
        assertThat(row.hasUnresolvedEquipmentRequirements).isTrue()
        assertThat(row.missingEquipmentAlternatives(StandardEquipment.ALL)).isEmpty()

        viewModel.openPicker()
        viewModel.updateQuery("banded-lat-pulldown")
        assertThat(viewModel.uiState.value.filteredExercises).containsExactly(pulldown)
        viewModel.addExercise(pulldown)
        viewModel.addExercise(row)
        viewModel.updateName("Manual band choices")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(templates.values.value.single().exercises.map { it.exerciseId })
            .containsExactly(pulldown.id, row.id).inOrder()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    // --- row identity ------------------------------------------------------------------

    @Test
    fun editingOneOfTwoRowsOfTheSameExerciseLeavesTheOtherUntouched() = runTest {
        val viewModel = loadedEditor()
        viewModel.addExercise(squat)
        viewModel.addExercise(squat)
        val (first, second) = viewModel.rows()
        assertThat(first.rowId).isNotEqualTo(second.rowId)

        viewModel.editExercise(second.rowId)
        viewModel.updateDraftField(DraftField.LOAD, "60")
        viewModel.applyDraft()

        assertThat(viewModel.rows()[0]).isEqualTo(first)
        assertThat(viewModel.rows()[1].load).isEqualTo("60")
    }

    @Test
    fun reorderAndRemoveActOnRowIdentityRatherThanPosition() = runTest {
        val viewModel = loadedEditor()
        viewModel.addExercise(squat)
        viewModel.addExercise(pullUps)
        viewModel.addExercise(squat)
        val ids = viewModel.rows().map { it.rowId }

        viewModel.moveExercise(ids[2], -1)
        assertThat(viewModel.rows().map { it.rowId })
            .containsExactly(ids[0], ids[2], ids[1]).inOrder()

        viewModel.removeExercise(ids[0])
        assertThat(viewModel.rows().map { it.rowId }).containsExactly(ids[2], ids[1]).inOrder()
    }

    @Test
    fun steppingSetsChangesOnlyTheAddressedRow() = runTest {
        val viewModel = loadedEditor()
        viewModel.addExercise(squat)
        viewModel.addExercise(squat)
        val ids = viewModel.rows().map { it.rowId }
        val untouched = viewModel.rows()[0].sets

        viewModel.changeSetCount(ids[1], 1)

        assertThat(viewModel.rows()[0].sets).isEqualTo(untouched)
        assertThat(viewModel.rows()[1].sets).isNotEqualTo(untouched)
    }

    @Test
    fun aSetCountThatIsNotANumberYetStepsToTheMinimumNotPastIt() = runTest {
        listOf("", "2,").forEach { typed ->
            listOf(1, -1).forEach { delta ->
                val handle = SavedStateHandle(
                    mapOf(
                        "templateEditor.hasDraft" to true,
                        "templateEditor.name" to "Legs",
                        "templateEditor.notes" to "",
                        "templateEditor.rows" to arrayListOf(
                            TemplateExerciseDraft(
                                rowId = "row-1",
                                exerciseId = "barbell-back-squat",
                                exerciseType = ExerciseType.WEIGHT_REPS,
                                sets = typed,
                                repMin = "8",
                                repMax = "12",
                                restSeconds = "90"
                            ).encode()
                        )
                    )
                )
                val viewModel = loadedEditor(handle = handle)
                viewModel.changeSetCount(viewModel.rows().single().rowId, delta)
                assertThat(viewModel.rows().single().sets).isEqualTo("1")
            }
        }
    }

    @Test
    fun theSetStepperStopsAtBothEndsOfWhatTheDomainAccepts() = runTest {
        val viewModel = loadedEditor()
        viewModel.addExercise(squat)
        val rowId = viewModel.rows().single().rowId

        repeat(40) { viewModel.changeSetCount(rowId, 1) }
        assertThat(viewModel.rows().single().sets).isEqualTo("20")

        repeat(40) { viewModel.changeSetCount(rowId, -1) }
        assertThat(viewModel.rows().single().sets).isEqualTo("1")

        // The row still converts, so the clamp produced a value the domain accepts.
        assertThat(
            (viewModel.rows().single().validate(WeightUnit.LBS) as DraftValidation.Valid)
                .exercise.prescription.targetSets
        ).isEqualTo(1)
    }

    // --- applying and saving an edit ------------------------------------------------------

    @Test
    fun applyRejectsAnInvalidDraftKeepsTheSheetOpenAndChangesNoRow() = runTest {
        val viewModel = loadedEditor()
        viewModel.addExercise(squat)
        val original = viewModel.rows().single()

        viewModel.editExercise(original.rowId)
        viewModel.updateDraftField(DraftField.REP_MAX, "2")
        viewModel.updateDraftField(DraftField.REP_MIN, "12")
        viewModel.applyDraft()

        assertThat(viewModel.uiState.value.editorDraft).isNotNull()
        assertThat(viewModel.uiState.value.fieldErrors)
            .containsExactly(DraftField.REP_MAX, R.string.draft_error_range_inverted)
        assertThat(viewModel.rows().single()).isEqualTo(original)
    }

    @Test
    fun aRestoredRowThatIsNotAValidPrescriptionIsReportedRatherThanSaved() = runTest {
        // Applying an edit already validates, so a row can only be invalid if it came back
        // from a saved payload. Saving revalidates rather than trusting what it restored.
        val incomplete = TemplateExerciseDraft(
            rowId = "row-1",
            exerciseId = "barbell-back-squat",
            exerciseType = ExerciseType.WEIGHT_REPS,
            sets = "",
            repMin = "8",
            repMax = "12",
            restSeconds = "90"
        )
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf(incomplete.encode())
            )
        )
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates, handle = handle)
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(templates.values.value).isEmpty()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_invalid_fields)
        assertThat(viewModel.uiState.value.editorDraft?.rowId).isEqualTo("row-1")
        assertThat(viewModel.uiState.value.fieldErrors.keys).containsExactly(DraftField.SETS)
        assertThat(viewModel.rows().single()).isEqualTo(incomplete)
    }

    @Test
    fun everyEditedFieldReachesTheSavedTemplate() = runTest {
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates)
        viewModel.addExercise(squat)
        viewModel.updateName("Leg day")
        val rowId = viewModel.rows().single().rowId

        viewModel.editExercise(rowId)
        viewModel.updateDraftField(DraftField.SETS, "5")
        viewModel.updateDraftField(DraftField.REP_MIN, "3")
        viewModel.updateDraftField(DraftField.REP_MAX, "5")
        viewModel.updateDraftField(DraftField.LOAD, "100")
        viewModel.updateDraftField(DraftField.REST, "180")
        viewModel.updateDraftField(DraftField.EFFORT_MIN, "1")
        viewModel.updateDraftField(DraftField.EFFORT_MAX, "2")
        viewModel.updateDraftNotes("belt on top sets")
        viewModel.applyDraft()
        viewModel.save()
        advanceUntilIdle()

        val saved = templates.values.value.single().exercises.single()
        assertThat(saved.notes).isEqualTo("belt on top sets")
        assertThat(saved.prescription.targetSets).isEqualTo(5)
        assertThat(saved.prescription.repRange).isEqualTo(RepRange(3, 5))
        assertThat(saved.prescription.targetWeight).isEqualTo(100.0)
        assertThat(saved.prescription.restSeconds).isEqualTo(180)
        assertThat(saved.prescription.effortTarget?.minRir).isEqualTo(1)
        assertThat(saved.prescription.restTargetSource)
            .isEqualTo(RestTargetSource.USER_PREFERENCE)
        assertThat(saved.prescription.restClass).isEqualTo(RestClass.LONG)
    }

    @Test
    fun anUntouchedRestValueKeepsTheProvenanceItWasSavedWith() = runTest {
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                restSeconds = 90,
                restClass = RestClass.MODERATE,
                restTargetSource = RestTargetSource.PRODUCT_POLICY
            )
        )
        templates.saveTemplate(template)
        val viewModel = loadedEditor(templateId = template.id, templates = templates)

        viewModel.updateName("Renamed only")
        viewModel.save()
        advanceUntilIdle()

        val saved = templates.values.value.single().exercises.single().prescription
        assertThat(saved.restClass).isEqualTo(RestClass.MODERATE)
        assertThat(saved.restTargetSource).isEqualTo(RestTargetSource.PRODUCT_POLICY)
    }

    // --- dirty state, discard, save guards ------------------------------------------------

    @Test
    fun dirtyBecomesFalseAgainWhenEveryEditIsReversed() = runTest {
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12)
            ),
            name = "Leg day"
        )
        templates.saveTemplate(template)
        val viewModel = loadedEditor(templateId = template.id, templates = templates)
        assertThat(viewModel.uiState.value.isDirty).isFalse()

        viewModel.updateName("Leg day rebuilt")
        assertThat(viewModel.uiState.value.isDirty).isTrue()

        viewModel.updateName("Leg day")
        assertThat(viewModel.uiState.value.isDirty).isFalse()

        val rowId = viewModel.rows().single().rowId
        viewModel.changeSetCount(rowId, 1)
        assertThat(viewModel.uiState.value.isDirty).isTrue()
        viewModel.changeSetCount(rowId, -1)
        assertThat(viewModel.uiState.value.isDirty).isFalse()
    }

    @Test
    fun discardIsConfirmedOnlyWhileThereAreUnsavedChanges() = runTest {
        val viewModel = loadedEditor()
        viewModel.requestDiscard()
        assertThat(viewModel.uiState.value.showDiscardConfirmation).isFalse()

        viewModel.addExercise(squat)
        viewModel.requestDiscard()
        assertThat(viewModel.uiState.value.showDiscardConfirmation).isTrue()

        viewModel.dismissDiscard()
        assertThat(viewModel.uiState.value.showDiscardConfirmation).isFalse()
        assertThat(viewModel.rows()).hasSize(1)
    }

    @Test
    fun aFailedSaveRetainsEveryFieldAndTheRetrySucceeds() = runTest {
        val templates = RecordingTemplateRepository(failNextSave = true)
        val viewModel = loadedEditor(templates = templates)
        viewModel.addExercise(squat)
        viewModel.updateName("Leg day")
        val rowId = viewModel.rows().single().rowId
        viewModel.editExercise(rowId)
        viewModel.updateDraftField(DraftField.LOAD, "60")
        viewModel.applyDraft()
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(viewModel.uiState.value.isSaving).isFalse()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(R.string.editor_error_save_failed)
        assertThat(viewModel.uiState.value.name).isEqualTo("Leg day")
        assertThat(viewModel.rows().single().load).isEqualTo("60")

        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(templates.values.value.single().exercises.single().prescription.targetWeight)
            .isEqualTo(60.0)
    }

    @Test
    fun aSecondSaveNeverCreatesASecondTemplateOrNavigatesTwice() = runTest {
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates)
        viewModel.addExercise(squat)
        viewModel.updateName("Leg day")
        viewModel.save()
        viewModel.save()
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(templates.values.value).hasSize(1)
        assertThat(templates.saveCount).isEqualTo(1)
    }

    @Test
    fun aSuccessfulSaveClearsTheSavedDraftAndAConfirmedDiscardDoesToo() = runTest {
        val handle = SavedStateHandle()
        val viewModel = loadedEditor(handle = handle)
        viewModel.addExercise(squat)
        viewModel.updateName("Leg day")
        assertThat(handle.keys()).isNotEmpty()

        viewModel.save()
        advanceUntilIdle()
        assertThat(restoredRows(handle)).isEmpty()

        val second = loadedEditor(handle = handle)
        second.addExercise(squat)
        assertThat(restoredRows(handle)).hasSize(1)
        second.requestDiscard()
        second.confirmDiscard()
        assertThat(restoredRows(handle)).isEmpty()
    }

    @Test
    fun restEditedAndTypedBackToItsOriginalValueIsNotAUserPreference() = runTest {
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                restSeconds = 90,
                restClass = RestClass.MODERATE,
                restTargetSource = RestTargetSource.PRODUCT_POLICY
            )
        )
        templates.saveTemplate(template)
        val viewModel = loadedEditor(templateId = template.id, templates = templates)
        val rowId = viewModel.rows().single().rowId

        viewModel.editExercise(rowId)
        viewModel.updateDraftField(DraftField.REST, "45")
        viewModel.updateDraftField(DraftField.REST, "90")
        viewModel.applyDraft()
        viewModel.updateName("Leg day renamed")
        viewModel.save()
        advanceUntilIdle()

        val saved = templates.values.value.single().exercises.single().prescription
        assertThat(saved.restSeconds).isEqualTo(90)
        assertThat(saved.restClass).isEqualTo(RestClass.MODERATE)
        assertThat(saved.restTargetSource).isEqualTo(RestTargetSource.PRODUCT_POLICY)
    }

    @Test
    fun aRowThatAlreadyCarriesAUserRestPreferenceKeepsItThroughALaterEdit() = runTest {
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates)
        viewModel.addExercise(squat)
        viewModel.updateName("Leg day")
        val rowId = viewModel.rows().single().rowId

        viewModel.editExercise(rowId)
        viewModel.updateDraftField(DraftField.REST, "45")
        viewModel.applyDraft()
        // Reopening and leaving rest alone must not undo the preference already recorded.
        viewModel.editExercise(rowId)
        viewModel.updateDraftField(DraftField.REP_MAX, "10")
        viewModel.applyDraft()
        viewModel.save()
        advanceUntilIdle()

        val saved = templates.values.value.single().exercises.single().prescription
        assertThat(saved.restSeconds).isEqualTo(45)
        assertThat(saved.restTargetSource).isEqualTo(RestTargetSource.USER_PREFERENCE)
    }

    @Test
    fun theFiftyFirstExerciseIsRefusedHereRatherThanInsideTheWrite() = runTest {
        // What the screen shows about the cap is the screen's business, and asserting a
        // message here would only say the view model set one; whether anything can display
        // it is what `theExerciseCapIsStatedBesideTheControlItDisables` establishes.
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates)
        repeat(50) { viewModel.addExercise(squat) }
        assertThat(viewModel.uiState.value.isAtExerciseLimit).isTrue()

        viewModel.addExercise(squat)

        assertThat(viewModel.rows()).hasSize(50)

        // The template that does fit still saves, so the cap is not a dead end.
        viewModel.updateName("Fifty exercises")
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(templates.values.value.single().exercises).hasSize(50)
    }

    @Test
    fun editsAreIgnoredWhileASaveIsInFlightSoTheWrittenTemplateIsWhatWasOnScreen() = runTest {
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates)
        viewModel.addExercise(squat)
        viewModel.updateName("Leg day")

        viewModel.save()
        // isSaving is set synchronously, so this lands while the write is in flight.
        assertThat(viewModel.uiState.value.isSaving).isTrue()
        viewModel.updateName("Renamed mid-save")
        viewModel.addExercise(pullUps)
        advanceUntilIdle()

        val written = templates.values.value.single()
        assertThat(written.name).isEqualTo("Leg day")
        assertThat(written.exercises).hasSize(1)
        assertThat(viewModel.uiState.value.name).isEqualTo("Leg day")
    }

    @Test
    fun aRecoveredDraftStillAsksBeforeLeavingWhenItsSourceTemplateIsGone() = runTest {
        val orphaned = TemplateExerciseDraft(
            rowId = "row-1",
            exerciseId = "barbell-back-squat",
            exerciseType = ExerciseType.WEIGHT_REPS,
            sets = "3",
            repMin = "8",
            repMax = "12",
            restSeconds = "90"
        )
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day rebuilt",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf(orphaned.encode())
            )
        )
        val viewModel = editor(templateId = "gone", handle = handle)
        viewModel.uiState.first { !it.isLoading }

        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(R.string.template_error_missing)
        assertThat(viewModel.uiState.value.isDirty).isTrue()

        viewModel.requestDiscard()
        assertThat(viewModel.uiState.value.showDiscardConfirmation).isTrue()
        assertThat(viewModel.rows().single()).isEqualTo(orphaned)
    }

    @Test
    fun everyRowControlIsIgnoredWhileASaveIsInFlight() = runTest {
        // The screen disables these controls; this is the half that proves the ViewModel
        // would ignore them anyway, so the two cannot drift apart.
        val viewModel = loadedEditor()
        viewModel.addExercise(squat)
        viewModel.addExercise(pullUps)
        viewModel.updateName("Leg day")
        val before = viewModel.rows()

        viewModel.save()
        assertThat(viewModel.uiState.value.isSaving).isTrue()

        viewModel.changeSetCount(before[0].rowId, 1)
        viewModel.moveExercise(before[0].rowId, 1)
        viewModel.removeExercise(before[1].rowId)
        viewModel.editExercise(before[0].rowId)

        assertThat(viewModel.rows()).isEqualTo(before)
        assertThat(viewModel.uiState.value.editorDraft).isNull()
        advanceUntilIdle()
    }

    @Test
    fun aRecoveredDraftIsDirtyBeforeTheLoadEvenFinishes() = runTest {
        // The toolbar's back arrow is drawn before the loading state returns, so a draft
        // that is not yet marked dirty would be popped without asking.
        val handle = SavedStateHandle()
        val first = loadedEditor(handle = handle)
        first.addExercise(squat)
        first.updateName("Leg day")

        val recreated = editor(handle = handle)

        assertThat(recreated.uiState.value.isLoading).isTrue()
        assertThat(recreated.uiState.value.isDirty).isTrue()
        recreated.uiState.first { !it.isLoading }
    }

    @Test
    fun aRecoveredDraftThatEmptiedTheTemplateIsStillDirtyDuringTheLoadWindow() = runTest {
        // Clearing the name and removing every row makes the draft look exactly like an
        // untouched new template. It is still unsaved work, and the back arrow is already
        // on screen while the catalog and the template are being read.
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12)
            ),
            name = "Leg day"
        )
        templates.saveTemplate(template)
        val handle = SavedStateHandle()
        val first = loadedEditor(
            templateId = template.id,
            templates = templates,
            handle = handle
        )
        first.updateName("")
        first.removeExercise(first.rows().single().rowId)
        assertThat(first.rows()).isEmpty()

        val recreated = editor(templateId = template.id, templates = templates, handle = handle)

        assertThat(recreated.uiState.value.isLoading).isTrue()
        assertThat(recreated.uiState.value.isDirty).isTrue()
        recreated.uiState.first { !it.isLoading }
        assertThat(recreated.uiState.value.isDirty).isTrue()
    }

    @Test
    fun aFreshEditorIsNotDirtyBeforeItsLoadFinishes() = runTest {
        val viewModel = editor()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
        assertThat(viewModel.uiState.value.isDirty).isFalse()
        viewModel.uiState.first { !it.isLoading }
    }

    @Test
    fun theDiscardDialogStillClosesWhileASaveIsInFlight() = runTest {
        val viewModel = loadedEditor()
        viewModel.addExercise(squat)
        viewModel.updateName("Leg day")
        viewModel.requestDiscard()
        assertThat(viewModel.uiState.value.showDiscardConfirmation).isTrue()

        viewModel.save()
        assertThat(viewModel.uiState.value.isSaving).isTrue()
        // Dismissing a modal is not a draft edit, so the saving guard must not swallow it.
        viewModel.dismissDiscard()

        assertThat(viewModel.uiState.value.showDiscardConfirmation).isFalse()
        advanceUntilIdle()
    }

    @Test
    fun discardingIsRefusedWhileASaveIsInFlightSoTheWriteIsNeverAbandoned() = runTest {
        val templates = RecordingTemplateRepository()
        val handle = SavedStateHandle()
        val viewModel = loadedEditor(templates = templates, handle = handle)
        viewModel.addExercise(squat)
        viewModel.updateName("Leg day")
        viewModel.save()
        assertThat(viewModel.uiState.value.isSaving).isTrue()
        viewModel.confirmDiscard()

        // The rows are still there, so the write that is running is still the truth.
        assertThat(viewModel.rows()).hasSize(1)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(templates.values.value.single().name).isEqualTo("Leg day")
        assertThat(restoredRows(handle)).isEmpty()
    }

    @Test
    fun discardPutsTheLoadedValuesBackSoNothingCanWriteTheEditsOutAgain() = runTest {
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12)
            ),
            name = "Leg day"
        )
        templates.saveTemplate(template)
        val handle = SavedStateHandle()
        val viewModel = loadedEditor(
            templateId = template.id,
            templates = templates,
            handle = handle
        )
        viewModel.updateName("Leg day rebuilt")
        viewModel.addExercise(pullUps)

        viewModel.requestDiscard()
        viewModel.confirmDiscard()

        assertThat(viewModel.uiState.value.name).isEqualTo("Leg day")
        assertThat(viewModel.rows().map { it.exerciseId }).containsExactly("barbell-back-squat")
        assertThat(viewModel.uiState.value.isDirty).isFalse()
        assertThat(restoredRows(handle)).isEmpty()
    }

    @Test
    fun everySupportedExerciseTypeIsEditedAndSavedThroughTheRepository() = runTest {
        // The draft round-trips every type on its own; this is the other half the design
        // record claimed -- each type edited in the editor and written out as a template.
        val catalog = InMemoryExerciseCatalog(
            ExerciseType.entries.map { type ->
                // Programming metadata is dropped: the domain refuses a recommended rep
                // range on a timed type, which is that invariant doing its job.
                squat.copy(
                    id = "exercise-" + type.name,
                    name = type.name,
                    type = type,
                    programming = null
                )
            }
        )
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates, catalog = catalog)
        viewModel.updateName("One of each")

        catalog.getAllExercises().first().forEach { exercise ->
            viewModel.addExercise(exercise)
            val row = viewModel.rows().last()
            viewModel.editExercise(row.rowId)
            // One field that type actually supports, so the edit has to survive the save.
            when (exercise.type) {
                ExerciseType.WEIGHT_REPS ->
                    viewModel.updateDraftField(DraftField.LOAD, "60")

                ExerciseType.ASSISTED_BODYWEIGHT ->
                    viewModel.updateDraftField(DraftField.ASSISTANCE, "20")

                ExerciseType.BODYWEIGHT_REPS ->
                    viewModel.updateDraftField(DraftField.REP_MAX, "15")

                ExerciseType.DURATION ->
                    viewModel.updateDraftField(DraftField.DURATION, "45")

                ExerciseType.DISTANCE_DURATION ->
                    viewModel.updateDraftField(DraftField.DISTANCE, "5000")
            }
            viewModel.updateDraftField(DraftField.SETS, "4")
            viewModel.applyDraft()
            assertThat(viewModel.uiState.value.fieldErrors).isEmpty()
        }
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isTrue()
        val written = templates.values.value.single().exercises
        assertThat(written.map { it.prescription.exerciseType })
            .containsExactlyElementsIn(ExerciseType.entries)
        assertThat(written.map { it.prescription.targetSets }.toSet()).containsExactly(4)
        written.forEach { exercise ->
            when (exercise.prescription.exerciseType) {
                ExerciseType.WEIGHT_REPS ->
                    assertThat(exercise.prescription.targetWeight).isEqualTo(60.0)

                ExerciseType.ASSISTED_BODYWEIGHT ->
                    assertThat(exercise.prescription.targetAssistanceWeight).isEqualTo(20.0)

                ExerciseType.BODYWEIGHT_REPS ->
                    assertThat(exercise.prescription.repRange?.max).isEqualTo(15)

                ExerciseType.DURATION ->
                    assertThat(exercise.prescription.targetDurationSeconds).isEqualTo(45)

                ExerciseType.DISTANCE_DURATION ->
                    assertThat(exercise.prescription.targetDistanceMeters).isEqualTo(5000.0)
            }
        }
    }

    @Test
    fun aSavedRowThatCannotBeReadBackIsDisclosedRatherThanDroppedFromTheTemplate() = runTest {
        // A payload another build wrote can hold a record this one cannot decode. Keeping
        // the rest quietly would save a template shorter than the one being edited.
        val readable = TemplateExerciseDraft(
            rowId = "row-1",
            exerciseId = "barbell-back-squat",
            exerciseType = ExerciseType.WEIGHT_REPS,
            sets = "3",
            repMin = "8",
            repMax = "12",
            restSeconds = "90"
        )
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf(readable.encode(), "not a record")
            )
        )
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates, handle = handle)

        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_draft_incomplete)
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(templates.values.value).isEmpty()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_draft_incomplete)
        assertThat(viewModel.rows().single()).isEqualTo(readable)
    }

    @Test
    fun anIncompleteDraftCanBeDiscardedEvenWhenNothingLooksDirty() = runTest {
        // Every row was lost and the name was never typed, so the draft compares equal to
        // an untouched new template. The refusal says to discard it, so that has to be
        // reachable — otherwise the editor is stuck with no way to obey its own message.
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf("not a record")
            )
        )
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates, handle = handle)

        assertThat(viewModel.uiState.value.isDirty).isFalse()
        assertThat(viewModel.uiState.value.isDraftIncomplete).isTrue()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_draft_incomplete)

        viewModel.requestDiscard()
        assertThat(viewModel.uiState.value.showDiscardConfirmation).isTrue()
        viewModel.confirmDiscard()
        assertThat(viewModel.uiState.value.isDraftIncomplete).isFalse()

        // And the editor works again rather than staying refused for good.
        viewModel.addExercise(squat)
        viewModel.updateName("Rebuilt")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(templates.values.value.single().name).isEqualTo("Rebuilt")
    }

    @Test
    fun discardingARecoveredOrphanRowLetsTheStoredTemplateSaveAgain() = runTest {
        // The flagged row is gone once the baseline is back, and the baseline came from the
        // repository, which validates catalog references. A refusal naming rows that are no
        // longer on screen would be unfixable from the editor.
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12)
            ),
            name = "Leg day"
        )
        templates.saveTemplate(template)
        val orphan = TemplateExerciseDraft(
            rowId = "row-orphan",
            exerciseId = "retired-exercise",
            exerciseType = ExerciseType.WEIGHT_REPS,
            sets = "3",
            repMin = "8",
            repMax = "12",
            restSeconds = "90"
        )
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day edited",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf(orphan.encode())
            )
        )
        val viewModel = loadedEditor(
            templateId = template.id,
            templates = templates,
            handle = handle
        )
        assertThat(viewModel.uiState.value.unresolvedRowIds).containsExactly("row-orphan")

        viewModel.requestDiscard()
        viewModel.confirmDiscard()

        assertThat(viewModel.uiState.value.unresolvedRowIds).isEmpty()
        assertThat(viewModel.rows().map { it.exerciseId })
            .containsExactly("barbell-back-squat")

        viewModel.updateName("Leg day restored")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(templates.values.value.single().name).isEqualTo("Leg day restored")
    }

    @Test
    fun aRecoveredRowWhoseExerciseChangedTypeIsFlaggedRatherThanFailingTheWrite() = runTest {
        // The repository enforces both rules: the exercise exists, and its type matches the
        // prescription. Checking only the id here would push a type change into the write,
        // where it surfaces as "try again" and no retry or edit can satisfy it.
        val retyped = TemplateExerciseDraft(
            rowId = "row-1",
            exerciseId = "barbell-back-squat",
            exerciseType = ExerciseType.DURATION,
            sets = "3",
            durationSeconds = "45",
            restSeconds = "90"
        )
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf(retyped.encode())
            )
        )
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates, handle = handle)

        // barbell-back-squat is WEIGHT_REPS in the catalog, so this row cannot be resolved.
        assertThat(viewModel.uiState.value.unresolvedRowIds).containsExactly("row-1")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(templates.values.value).isEmpty()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_missing_exercise)
        assertThat(viewModel.rows().single()).isEqualTo(retyped)
    }

    @Test
    fun searchingThePickerDoesNotMarkAnUntouchedEditorAsHoldingADraft() = runTest {
        // Chrome, not drafted content: persisting it set the has-draft marker on an editor
        // nobody had typed into, and re-encoded every row on each keystroke.
        val handle = SavedStateHandle()
        val viewModel = loadedEditor(handle = handle)

        viewModel.openPicker()
        viewModel.updateQuery("squat")
        viewModel.closePicker()

        assertThat(handle.get<Boolean>("templateEditor.hasDraft")).isNull()
        assertThat(viewModel.uiState.value.isDirty).isFalse()

        // A real change still persists.
        viewModel.addExercise(squat)
        assertThat(handle.get<Boolean>("templateEditor.hasDraft")).isTrue()
        assertThat(restoredRows(handle)).hasSize(1)
    }

    @Test
    fun anIncompleteDraftStaysRefusedAcrossFurtherRecreations() = runTest {
        // The first edit overwrites STATE_ROWS with only the rows that decoded, so without
        // carrying the fact forward the next recreation would look clean and save short.
        val readable = TemplateExerciseDraft(
            rowId = "row-1",
            exerciseId = "barbell-back-squat",
            exerciseType = ExerciseType.WEIGHT_REPS,
            sets = "3",
            repMin = "8",
            repMax = "12",
            restSeconds = "90"
        )
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf(readable.encode(), "not a record")
            )
        )
        val templates = RecordingTemplateRepository()
        val first = loadedEditor(templates = templates, handle = handle)
        first.updateName("Leg day edited")
        assertThat(restoredRows(handle)).hasSize(1)

        val second = loadedEditor(templates = templates, handle = handle)

        assertThat(second.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_draft_incomplete)
        second.save()
        advanceUntilIdle()

        assertThat(second.uiState.value.isSaved).isFalse()
        assertThat(templates.values.value).isEmpty()
    }

    @Test
    fun discardingAnIncompleteDraftClearsTheRefusalAndTheNextSaveWorks() = runTest {
        // Discard puts the stored template back, so the shortened draft and the reason it
        // could not be saved are both gone; the editor must not stay dead.
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12)
            ),
            name = "Leg day"
        )
        templates.saveTemplate(template)
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day edited",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf("not a record")
            )
        )
        val viewModel = loadedEditor(
            templateId = template.id,
            templates = templates,
            handle = handle
        )
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_draft_incomplete)

        viewModel.requestDiscard()
        viewModel.confirmDiscard()

        assertThat(viewModel.uiState.value.name).isEqualTo("Leg day")
        assertThat(viewModel.rows().map { it.exerciseId }).containsExactly("barbell-back-squat")

        viewModel.updateName("Leg day again")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(templates.values.value.single().name).isEqualTo("Leg day again")
        assertThat(templates.values.value.single().exercises).hasSize(1)
    }

    @Test
    fun aSeparatorPastedIntoANumericFieldCannotCorruptItsSavedRow() = runTest {
        val handle = SavedStateHandle()
        val viewModel = loadedEditor(handle = handle)
        viewModel.addExercise(squat)
        val rowId = viewModel.rows().single().rowId

        viewModel.editExercise(rowId)
        viewModel.updateDraftField(DraftField.LOAD, "6\u001F0")
        viewModel.applyDraft()

        assertThat(viewModel.rows().single().load).isEqualTo("60")
        val encoded = restoredRows(handle).single()
        assertThat(TemplateExerciseDraft.decode(encoded)).isEqualTo(viewModel.rows().single())
    }

    // --- recovery across process recreation -------------------------------------------------

    @Test
    fun aSecondViewModelOnTheSameSavedStateHandleRecoversTheWholeDraft() = runTest {
        val handle = SavedStateHandle()
        val first = loadedEditor(handle = handle)
        first.addExercise(squat)
        first.addExercise(squat)
        first.updateName("Leg day")
        first.updateNotes("belt on top sets")
        val secondRowId = first.rows()[1].rowId
        first.editExercise(secondRowId)
        first.updateDraftField(DraftField.LOAD, "60,")
        first.updateDraftNotes("still typing")

        // A new ViewModel on the same handle is the shape system-initiated process death
        // takes: the object is gone, the saved bundle is not.
        val recreated = editor(handle = handle)

        assertThat(recreated.uiState.value.name).isEqualTo("Leg day")
        assertThat(recreated.uiState.value.notes).isEqualTo("belt on top sets")
        assertThat(recreated.rows().map { it.rowId }).isEqualTo(first.rows().map { it.rowId })
        assertThat(recreated.uiState.value.editorDraft?.rowId).isEqualTo(secondRowId)
        assertThat(recreated.uiState.value.editorDraft?.load).isEqualTo("60,")
        assertThat(recreated.uiState.value.editorDraft?.notes).isEqualTo("still typing")

        // Let the load finish inside the test, so nothing resumes after the rule resets Main.
        recreated.uiState.first { !it.isLoading }
    }

    @Test
    fun theCatalogAndProfileLoadDoesNotOverwriteARecoveredDraft() = runTest {
        val handle = SavedStateHandle()
        val first = loadedEditor(handle = handle)
        first.addExercise(squat)
        first.updateName("Leg day")

        val recreated = editor(handle = handle)
        val beforeLoad = recreated.uiState.value
        val afterLoad = recreated.uiState.first { !it.isLoading }

        assertThat(afterLoad.name).isEqualTo(beforeLoad.name)
        assertThat(afterLoad.notes).isEqualTo(beforeLoad.notes)
        assertThat(afterLoad.selectedExercises).isEqualTo(beforeLoad.selectedExercises)
        assertThat(afterLoad.isLoading).isFalse()
        assertThat(afterLoad.catalogExercises).isNotEmpty()
    }

    @Test
    fun anExistingTemplateDraftRecoversWithoutBeingReplacedByTheStoredTemplate() = runTest {
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12)
            ),
            name = "Leg day"
        )
        templates.saveTemplate(template)
        val handle = SavedStateHandle()
        val first = loadedEditor(templateId = template.id, templates = templates, handle = handle)
        first.updateName("Leg day rebuilt")
        first.changeSetCount(first.rows().single().rowId, 2)

        val recreated = loadedEditor(
            templateId = template.id,
            templates = templates,
            handle = handle
        )

        assertThat(recreated.uiState.value.name).isEqualTo("Leg day rebuilt")
        assertThat(recreated.rows().single().sets).isEqualTo("5")
        assertThat(recreated.uiState.value.isDirty).isTrue()
    }

    @Test
    fun theSavedPayloadCarriesNoCatalogOrExerciseObjects() = runTest {
        val handle = SavedStateHandle()
        val viewModel = loadedEditor(handle = handle)
        viewModel.addExercise(squat)

        handle.keys().mapNotNull { handle.get<Any>(it) }.forEach { value ->
            assertThat(value is String || value is Boolean || value is ArrayList<*>).isTrue()
            if (value is ArrayList<*>) {
                value.forEach { assertThat(it).isInstanceOf(String::class.java) }
            }
        }
        assertThat(restoredRows(handle).single()).contains(squat.id)
    }

    // --- missing source data ------------------------------------------------------------------

    @Test
    fun aMissingSourceTemplateIsReportedAndNeverSavedAsANewTemplate() = runTest {
        val templates = RecordingTemplateRepository()
        val viewModel = editor(templateId = "gone", templates = templates)
        val loaded = viewModel.uiState.first { !it.isLoading }

        assertThat(loaded.errorMessage).isEqualTo(R.string.template_error_missing)

        viewModel.updateName("Recovered")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(templates.values.value).isEmpty()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(R.string.template_error_missing)
    }

    @Test
    fun aRecoveredRowWhoseExerciseLeftTheCatalogIsFlaggedAndBlocksSaveWithoutLosingInput() =
        runTest {
            val orphan = TemplateExerciseDraft(
                rowId = "row-orphan",
                exerciseId = "retired-exercise",
                exerciseType = ExerciseType.WEIGHT_REPS,
                sets = "3",
                repMin = "8",
                repMax = "12",
                restSeconds = "90"
            )
            val handle = SavedStateHandle(
                mapOf(
                    "templateEditor.hasDraft" to true,
                    "templateEditor.name" to "Leg day",
                    "templateEditor.notes" to "",
                    "templateEditor.rows" to arrayListOf(orphan.encode())
                )
            )
            val templates = RecordingTemplateRepository()
            val viewModel = loadedEditor(templates = templates, handle = handle)

            assertThat(viewModel.uiState.value.unresolvedRowIds).containsExactly("row-orphan")
            viewModel.save()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isSaved).isFalse()
            assertThat(templates.values.value).isEmpty()
            assertThat(viewModel.uiState.value.errorMessage)
                .isEqualTo(R.string.editor_error_missing_exercise)
            assertThat(viewModel.rows().single()).isEqualTo(orphan)
        }

    @Test
    fun aLoadThatFailedIsNotReportedAsADeletedTemplate() = runTest {
        // The repository throws when a stored exercise id is no longer in the catalog. That
        // template still exists, so saying it does not would be false.
        val templates = RecordingTemplateRepository(failReads = true)
        val viewModel = editor(templateId = "template-1", templates = templates)
        viewModel.uiState.first { !it.isLoading }

        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_load_failed)

        viewModel.updateName("Leg day")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(templates.values.value).isEmpty()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_load_failed)
        assertThat(viewModel.uiState.value.name).isEqualTo("Leg day")
    }

    @Test
    fun aNewTemplateDraftIsAlsoRefusedWhenItsLoadFailed() = runTest {
        // The edit-mode guard does not cover this: originalTemplate is null for a new
        // template whether the load worked or not, and the catalog check that flags a
        // retired exercise never ran.
        val restored = TemplateExerciseDraft(
            rowId = "row-1",
            exerciseId = "barbell-back-squat",
            exerciseType = ExerciseType.WEIGHT_REPS,
            sets = "3",
            repMin = "8",
            repMax = "12",
            restSeconds = "90"
        )
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf(restored.encode())
            )
        )
        val templates = RecordingTemplateRepository()
        val viewModel = editor(
            templates = templates,
            catalog = ThrowingExerciseCatalog(),
            handle = handle
        )
        viewModel.uiState.first { !it.isLoading }

        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_load_failed)
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(templates.values.value).isEmpty()
        assertThat(viewModel.rows().single()).isEqualTo(restored)
    }

    @Test
    fun aMissingSourceTemplateStillShowsTheReadersUnitAndTheRealCatalog() = runTest {
        val profiles = OfflineUserProfileRepository(FakeUserProfileDao())
        profiles.saveProfile(UserProfile(preferredUnit = WeightUnit.KG))
        val viewModel = editor(templateId = "gone", profiles = profiles)
        val loaded = viewModel.uiState.first { !it.isLoading }

        assertThat(loaded.errorMessage).isEqualTo(R.string.template_error_missing)
        // The profile and catalog were already read before the template turned out to be
        // gone; discarding them would label a kilogram value "LB" and claim no exercises.
        assertThat(loaded.weightUnit).isEqualTo(WeightUnit.KG)
        assertThat(loaded.catalogExercises).isNotEmpty()
    }

    @Test
    fun aDeletedTemplateAndARetiredExerciseAreBothDisclosedTogether() = runTest {
        // Each state is covered on its own; together, the missing-template branch used to
        // return before working out which rows the catalog can no longer resolve.
        val orphan = TemplateExerciseDraft(
            rowId = "row-orphan",
            exerciseId = "retired-exercise",
            exerciseType = ExerciseType.WEIGHT_REPS,
            sets = "3",
            repMin = "8",
            repMax = "12",
            restSeconds = "90"
        )
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf(orphan.encode())
            )
        )
        val viewModel = editor(templateId = "gone", handle = handle)
        val loaded = viewModel.uiState.first { !it.isLoading }

        assertThat(loaded.errorMessage).isEqualTo(R.string.template_error_missing)
        assertThat(loaded.unresolvedRowIds).containsExactly("row-orphan")
        assertThat(viewModel.rows().single()).isEqualTo(orphan)
    }

    @Test
    fun addingTheExerciseBackAfterAStaleRowIsFlaggedProducesARowThatSaves() = runTest {
        // The flagged row's remedy is to add the exercise again and remove the stale one.
        // Flagging by exercise id condemned the replacement too, so following the on-screen
        // instruction led nowhere: the new row read as unusable and the save stayed refused.
        val retyped = TemplateExerciseDraft(
            rowId = "row-stale",
            exerciseId = "barbell-back-squat",
            exerciseType = ExerciseType.DURATION,
            sets = "3",
            durationSeconds = "45",
            restSeconds = "90"
        )
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf(retyped.encode())
            )
        )
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates, handle = handle)
        assertThat(viewModel.uiState.value.unresolvedRowIds).containsExactly("row-stale")

        viewModel.addExercise(squat)
        val replacement = viewModel.rows().single { it.rowId != "row-stale" }

        // The replacement took its type from the catalog, so only the stale row is flagged.
        assertThat(viewModel.uiState.value.unresolvedRowIds).containsExactly("row-stale")
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSaved).isFalse()

        viewModel.removeExercise("row-stale")

        assertThat(viewModel.uiState.value.unresolvedRowIds).isEmpty()
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(templates.values.value.single().exercises.map { it.exerciseId })
            .containsExactly(replacement.exerciseId)
    }

    @Test
    fun aRecoveredDraftOfADeletedTemplateStaysDirtyBecauseNoBaselineEverArrives() = runTest {
        // Nothing the load returns can prove this draft was saved, so it is unsaved work
        // until it is discarded. Recomputing against the placeholder baseline called an
        // emptied draft unchanged, and back would then pop it with no confirmation.
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf<String>()
            )
        )
        val viewModel = editor(templateId = "gone", handle = handle)
        val loaded = viewModel.uiState.first { !it.isLoading }

        assertThat(loaded.errorMessage).isEqualTo(R.string.template_error_missing)
        assertThat(loaded.isDirty).isTrue()

        // Screen chrome and edits both recompute dirty state; neither may lower it.
        viewModel.requestDiscard()
        assertThat(viewModel.uiState.value.showDiscardConfirmation).isTrue()
        viewModel.dismissDiscard()
        assertThat(viewModel.uiState.value.isDirty).isTrue()

        viewModel.addExercise(squat)
        viewModel.removeExercise(viewModel.rows().single().rowId)
        assertThat(viewModel.uiState.value.isDirty).isTrue()

        // Discarding is what ends it, so the prompt does not come back.
        viewModel.requestDiscard()
        viewModel.confirmDiscard()
        assertThat(viewModel.uiState.value.isDirty).isFalse()
        assertThat(viewModel.uiState.value.showDiscardConfirmation).isFalse()
    }

    @Test
    fun anIncompleteDraftKeepsSayingSoWhileTheUserEditsAroundIt() = runTest {
        // The copy asks the user to discard and start again. Any other move — rebuilding the
        // row it names, renaming the template — cleared `errorMessage`, so the warning left
        // the screen while `save` went on refusing for the same reason. Nothing else on
        // screen carries this state.
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Leg day",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf("not-a-row")
            )
        )
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates, handle = handle)
        assertThat(viewModel.uiState.value.isDraftIncomplete).isTrue()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_draft_incomplete)

        viewModel.addExercise(squat)
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_draft_incomplete)

        viewModel.updateName("Leg day rebuilt")
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_draft_incomplete)
        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(templates.values.value).isEmpty()

        // Discarding is the way out the message names, and it ends the disclosure too.
        viewModel.requestDiscard()
        viewModel.confirmDiscard()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    @Test
    fun aDeletedTemplateAndAFailedLoadKeepSayingSoWhileTheUserEditsAroundThem() = runTest {
        // Same shape, two other refusals that outlast any edit: neither has a surface of its
        // own, and both still refuse the save.
        val deleted = editor(templateId = "gone")
        deleted.uiState.first { !it.isLoading }
        deleted.updateName("Renamed anyway")
        assertThat(deleted.uiState.value.errorMessage)
            .isEqualTo(R.string.template_error_missing)

        val broken = editor(catalog = ThrowingExerciseCatalog())
        broken.uiState.first { !it.isLoading }
        broken.updateName("Renamed anyway")
        assertThat(broken.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_load_failed)
    }

    @Test
    fun aRefusedSaveDoesNotLeaveAnUntouchedEditorMarkedAsHoldingADraft() = runTest {
        // A refusal changes no drafted content, but it used to persist one, and
        // `persistDraft` marks the handle unconditionally. Tapping Save on an editor nobody
        // had typed into made the next recreation restore an empty draft and ask whether to
        // discard it — the same false prompt the picker chrome used to raise.
        val handle = SavedStateHandle()
        val viewModel = loadedEditor(handle = handle)
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(viewModel.uiState.value.errorMessage)
            .isEqualTo(R.string.editor_error_name_required)
        assertThat(handle.get<Boolean>("templateEditor.hasDraft")).isNull()

        // The next process recreation therefore has nothing to restore and nothing to ask
        // about, which is the part the user would have seen.
        val recreated = editor(handle = handle)
        assertThat(recreated.uiState.value.isDirty).isFalse()
        assertThat(recreated.uiState.first { !it.isLoading }.isDirty).isFalse()

        // A real edit still persists, so this did not turn the marker off entirely.
        viewModel.updateName("Leg day")
        assertThat(handle.get<Boolean>("templateEditor.hasDraft")).isTrue()
    }

    @Test
    fun startingAWriteDoesNotMarkTheHandleForATemplateNobodyChanged() = runTest {
        // The save button is not gated on dirty state, so re-saving an untouched template
        // as-is is one tap. Marking the handle there put a draft behind a window only a
        // process death can land in: between the write starting and `clearDraft`.
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12)
            )
        )
        templates.saveTemplate(template)
        val handle = SavedStateHandle()
        val viewModel = loadedEditor(
            templateId = template.id,
            templates = templates,
            handle = handle
        )
        assertThat(viewModel.uiState.value.isDirty).isFalse()
        viewModel.save()

        // The write has not run yet, which is exactly the window in question.
        assertThat(viewModel.uiState.value.isSaving).isTrue()
        assertThat(handle.get<Boolean>("templateEditor.hasDraft")).isNull()

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(templates.saveCount).isEqualTo(2)
    }

    @Test
    fun aClockThatMovedBackwardsDoesNotMakeAStoredTemplateUnsaveable() = runTest {
        // Every other domain rule on this path is checked before the write, because one
        // reaching the constructor inside the save coroutine arrives as "try again" and no
        // retry or edit can satisfy it. `updatedAtTimestamp >= createdAtTimestamp` was the
        // last one left, and a manual time change or an NTP correction trips it.
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12)
            )
        )
        templates.saveTemplate(template)
        val viewModel = editor(
            templateId = template.id,
            templates = templates,
            now = { template.createdAtTimestamp - 60_000L }
        )
        viewModel.uiState.first { !it.isLoading }
        viewModel.updateName("Leg day renamed")
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isTrue()
        assertThat(viewModel.uiState.value.errorMessage).isNull()
        val stored = templates.values.value.single()
        assertThat(stored.name).isEqualTo("Leg day renamed")
        // The created stamp is history the user did not touch, so it stays put.
        assertThat(stored.createdAtTimestamp).isEqualTo(template.createdAtTimestamp)
        assertThat(stored.updatedAtTimestamp).isAtLeast(stored.createdAtTimestamp)
    }

    // --- units -------------------------------------------------------------------------------

    private suspend fun profilesWith(profile: UserProfile) =
        OfflineUserProfileRepository(FakeUserProfileDao()).also { it.saveProfile(profile) }

    private fun TemplateEditorViewModel.addSquatWithLoad(load: String) {
        addExercise(squat)
        editExercise(rows().last().rowId)
        updateDraftField(DraftField.LOAD, load)
        applyDraft()
    }

    @Test
    fun aNewTemplateIsWrittenInTheProfileUnitAndSavesThatUnit() = runTest {
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(
            templates = templates,
            profiles = profilesWith(UserProfile(preferredUnit = WeightUnit.KG))
        )
        assertThat(viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.KG)
        viewModel.addSquatWithLoad("60")
        viewModel.updateName("Legs")
        viewModel.save()
        advanceUntilIdle()

        val stored = templates.values.value.single()
        assertThat(stored.weightUnit).isEqualTo(WeightUnit.KG)
        assertThat(stored.exercises.single().prescription.targetWeight).isEqualTo(60.0)
    }

    @Test
    fun switchingUnitsConvertsEveryLoadIncludingTheOpenSheetRatherThanRelabelling() = runTest {
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates)
        viewModel.addSquatWithLoad("100")
        viewModel.addSquatWithLoad("")
        viewModel.addExercise(squat)
        viewModel.editExercise(viewModel.rows().last().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "225")

        viewModel.convertUnit(WeightUnit.KG)

        val state = viewModel.uiState.value
        assertThat(state.weightUnit).isEqualTo(WeightUnit.KG)
        assertThat(viewModel.rows().map { it.load }).containsExactly("45.36", "", "").inOrder()
        assertThat(state.editorDraft?.load).isEqualTo("102.06")

        // And back: the round trip lands on the original number at this precision.
        viewModel.convertUnit(WeightUnit.LBS)
        assertThat(viewModel.rows()[0].load).isEqualTo("100")
    }

    @Test
    fun switchingUnitsIsRefusedWhileALoadIsNotANumberYet() = runTest {
        val viewModel = loadedEditor()
        viewModel.addSquatWithLoad("100")
        viewModel.addExercise(squat)
        viewModel.editExercise(viewModel.rows().last().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "12.")

        viewModel.convertUnit(WeightUnit.KG)

        val state = viewModel.uiState.value
        assertThat(state.weightUnit).isEqualTo(WeightUnit.LBS)
        assertThat(state.unitRefusal).isEqualTo(R.string.editor_error_unit_convert_invalid)
        assertThat(viewModel.rows()[0].load).isEqualTo("100")
        assertThat(state.editorDraft?.load).isEqualTo("12.")
    }

    @Test
    fun aDecimalCommaIsConvertedAsTheSameNumberAndWrittenBackInTheSameLocale() = runTest {
        // A comma-decimal locale; es-MX itself writes a point.
        val viewModel = editor(locale = Locale.forLanguageTag("es-ES"))
            .also { it.uiState.first { state -> !state.isLoading } }
        viewModel.addSquatWithLoad("22,5")
        viewModel.convertUnit(WeightUnit.KG)
        // 22.5 lb = 10.2058 kg.
        assertThat(viewModel.rows().single().load).isEqualTo("10,21")
    }

    @Test
    fun aProfileUnitChangeWhileADraftIsOpenDoesNotReinterpretItsNumbers() = runTest {
        val handle = SavedStateHandle()
        val profiles = profilesWith(UserProfile(preferredUnit = WeightUnit.LBS))
        val first = loadedEditor(profiles = profiles, handle = handle)
        first.addSquatWithLoad("100")

        profiles.updateUnit(WeightUnit.KG)
        val recreated = loadedEditor(profiles = profiles, handle = handle)

        assertThat(recreated.uiState.value.weightUnit).isEqualTo(WeightUnit.LBS)
        assertThat(recreated.rows().single().load).isEqualTo("100")
    }

    @Test
    fun aFactoryStartingLoadIsConvertedIntoTheDraftsUnit() = runTest {
        val viewModel = loadedEditor(
            profiles = profilesWith(
                UserProfile(
                    preferredUnit = WeightUnit.LBS,
                    confirmedStartingLoads = mapOf(squat.id to 100.0)
                )
            )
        )
        viewModel.addExercise(squat)
        assertThat(viewModel.rows().single().load).isEqualTo("100")

        viewModel.convertUnit(WeightUnit.KG)
        viewModel.addExercise(squat)
        assertThat(viewModel.rows().map { it.load }).containsExactly("45.36", "45.36").inOrder()
    }

    @Test
    fun aLegacyTemplatesLoadsStayUnitlessUntilConfirmedAndConfirmingDoesNotConvert() = runTest {
        val legacy = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = 100.0
            )
        )
        val templates = RecordingTemplateRepository().apply { values.value = listOf(legacy) }
        val viewModel = loadedEditor(
            templateId = legacy.id,
            templates = templates,
            profiles = profilesWith(UserProfile(preferredUnit = WeightUnit.KG))
        )
        assertThat(viewModel.uiState.value.weightUnit).isNull()
        assertThat(viewModel.uiState.value.isDirty).isFalse()

        viewModel.save()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(R.string.editor_error_unit_unconfirmed)
        assertThat(templates.saveCount).isEqualTo(0)

        // Converting needs a unit to convert from, so it does nothing here.
        viewModel.convertUnit(WeightUnit.KG)
        assertThat(viewModel.uiState.value.weightUnit).isNull()

        viewModel.confirmUnit(WeightUnit.LBS)
        assertThat(viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.LBS)
        assertThat(viewModel.rows().single().load).isEqualTo("100")
        assertThat(viewModel.uiState.value.isDirty).isTrue()

        viewModel.save()
        advanceUntilIdle()
        val stored = templates.values.value.single()
        assertThat(stored.weightUnit).isEqualTo(WeightUnit.LBS)
        assertThat(stored.exercises.single().prescription.targetWeight).isEqualTo(100.0)
    }

    @Test
    fun aLegacyTemplateWithNoLoadAdoptsTheProfileUnitWithoutLookingEdited() = runTest {
        val legacy = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8)
            )
        )
        val templates = RecordingTemplateRepository().apply { values.value = listOf(legacy) }
        val viewModel = loadedEditor(
            templateId = legacy.id,
            templates = templates,
            profiles = profilesWith(UserProfile(preferredUnit = WeightUnit.KG))
        )
        assertThat(viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.KG)
        assertThat(viewModel.uiState.value.isDirty).isFalse()
    }

    @Test
    fun aLegacyDraftWhoseLoadsAreAllRemovedTakesTheProfileUnit() = runTest {
        val legacy = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = 100.0
            )
        )
        val templates = RecordingTemplateRepository().apply { values.value = listOf(legacy) }
        val viewModel = loadedEditor(
            templateId = legacy.id,
            templates = templates,
            profiles = profilesWith(UserProfile(preferredUnit = WeightUnit.KG))
        )
        assertThat(viewModel.uiState.value.weightUnit).isNull()

        // Clearing the only stored load leaves nothing whose unit is unknown.
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "")
        // The row still holds its stored load until the sheet is applied.
        assertThat(viewModel.uiState.value.weightUnit).isNull()
        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.KG)

        // A load typed next is in that stated unit, not mistaken for a stored one.
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "100")
        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.KG)
        viewModel.save()
        advanceUntilIdle()
        assertThat(templates.values.value.single().weightUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun anUnconfirmedLegacyDraftDropsAFactoryLoadItCouldNotLabel() = runTest {
        val legacy = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = 100.0
            )
        )
        val templates = RecordingTemplateRepository().apply { values.value = listOf(legacy) }
        val viewModel = loadedEditor(
            templateId = legacy.id,
            templates = templates,
            profiles = profilesWith(UserProfile(confirmedStartingLoads = mapOf(squat.id to 80.0)))
        )
        viewModel.addExercise(squat)
        assertThat(viewModel.rows().last().load).isEmpty()
    }

    @Test
    fun aKilogramLoadThatCouldNotBeStartedInPoundsIsAFieldError() = runTest {
        val viewModel = loadedEditor(profiles = profilesWith(UserProfile(preferredUnit = WeightUnit.KG)))
        viewModel.addExercise(squat)
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "5000")
        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.fieldErrors[DraftField.LOAD])
            .isEqualTo(R.string.draft_error_out_of_range_with_range)
    }

    @Test
    fun theDraftUnitSurvivesRecreationAndDiscardRestoresTheSavedOne() = runTest {
        val handle = SavedStateHandle()
        val first = loadedEditor(handle = handle)
        first.addSquatWithLoad("100")
        first.convertUnit(WeightUnit.KG)

        val recreated = loadedEditor(handle = handle)
        assertThat(recreated.uiState.value.weightUnit).isEqualTo(WeightUnit.KG)
        assertThat(recreated.rows().single().load).isEqualTo("45.36")

        recreated.confirmDiscard()
        assertThat(recreated.uiState.value.weightUnit).isEqualTo(WeightUnit.LBS)
    }

    @Test
    fun anUnreadableSavedUnitIsTreatedAsAnIncompleteDraftRatherThanGuessed() = runTest {
        val handle = SavedStateHandle()
        loadedEditor(handle = handle).addSquatWithLoad("100")
        handle["templateEditor.weightUnit"] = "STONE"

        val recreated = loadedEditor(handle = handle)
        assertThat(recreated.uiState.value.isDraftIncomplete).isTrue()
        assertThat(recreated.uiState.value.weightUnit).isNull()
    }

    @Test
    fun switchingToTheOtherUnitAndBackRestoresTheExactNumbersAndLeavesNothingEdited() = runTest {
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(templates = templates)
        viewModel.addSquatWithLoad("135")
        viewModel.addSquatWithLoad("2.5")
        viewModel.updateName("Legs")
        viewModel.save()
        advanceUntilIdle()
        val stored = templates.values.value.single()
        val reopened = loadedEditor(templateId = stored.id, templates = templates)

        reopened.convertUnit(WeightUnit.KG)
        // 135 lb = 61.235 kg and 2.5 lb = 1.134 kg; converting those back would give 134.99
        // and 2.49.
        assertThat(reopened.rows().map { it.load }).containsExactly("61.23", "1.13").inOrder()
        reopened.convertUnit(WeightUnit.LBS)

        assertThat(reopened.rows().map { it.load }).containsExactly("135", "2.5").inOrder()
        assertThat(reopened.uiState.value.isDirty).isFalse()
    }

    @Test
    fun aLoadTypedInTheOtherUnitIsTheNewSourceAndIsConvertedNotRestored() = runTest {
        val viewModel = loadedEditor()
        viewModel.addSquatWithLoad("135")
        viewModel.convertUnit(WeightUnit.KG)
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "60")
        viewModel.applyDraft()

        viewModel.convertUnit(WeightUnit.LBS)

        // 60 kg = 132.277 lb, not the 135 the row held before it was retyped.
        assertThat(viewModel.rows().single().load).isEqualTo("132.28")
    }

    @Test
    fun aFreshlyOpenedEditOfADeletedTemplateDoesNotClaimUnsavedChanges() = runTest {
        val viewModel = loadedEditor(templateId = "gone")
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(R.string.template_error_missing)
        assertThat(viewModel.uiState.value.isDirty).isFalse()
    }

    @Test
    fun textAtItsLengthLimitIsNotCutThroughTheMiddleOfAnEmoji() = runTest {
        val viewModel = loadedEditor()
        val emoji = "\uD83D\uDCAA"
        viewModel.updateName("a" + emoji.repeat(WorkoutTemplate.MAX_NAME_LENGTH))
        val name = viewModel.uiState.value.name
        assertThat(name.length).isEqualTo(WorkoutTemplate.MAX_NAME_LENGTH - 1)
        assertThat(name.last().isLowSurrogate()).isTrue()
    }

    @Test
    fun takingNoCharactersGivesEmptyTextRatherThanFailing() {
        assertThat("abc".takeWhole(0)).isEmpty()
        assertThat("".takeWhole(0)).isEmpty()
    }

    @Test
    fun closingAModifiedSheetAsksFirstAndAnUntouchedOneSimplyCloses() = runTest {
        val viewModel = loadedEditor()
        viewModel.addExercise(squat)
        val rowId = viewModel.rows().single().rowId

        viewModel.editExercise(rowId)
        viewModel.dismissSheet()
        assertThat(viewModel.uiState.value.editorDraft).isNull()
        assertThat(viewModel.uiState.value.showSheetDiscardConfirmation).isFalse()

        viewModel.editExercise(rowId)
        viewModel.updateDraftField(DraftField.LOAD, "60")
        viewModel.dismissSheet()
        assertThat(viewModel.uiState.value.showSheetDiscardConfirmation).isTrue()
        viewModel.keepEditingSheet()
        assertThat(viewModel.uiState.value.editorDraft?.load).isEqualTo("60")

        viewModel.dismissSheet()
        viewModel.cancelDraft()
        assertThat(viewModel.uiState.value.editorDraft).isNull()
        assertThat(viewModel.uiState.value.showSheetDiscardConfirmation).isFalse()
        assertThat(viewModel.rows().single().load).isEmpty()
    }

    @Test
    fun retypingTheConvertedNumberKeepsTheRoundTripExact() = runTest {
        val viewModel = loadedEditor()
        viewModel.addSquatWithLoad("135")
        viewModel.convertUnit(WeightUnit.KG)
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "61.2")
        viewModel.updateDraftField(DraftField.LOAD, "61.23")
        assertThat(viewModel.uiState.value.isSheetModified).isFalse()
        viewModel.applyDraft()

        viewModel.convertUnit(WeightUnit.LBS)

        assertThat(viewModel.rows().single().load).isEqualTo("135")
    }

    @Test
    fun assistanceAndAnOpenSheetRoundTripExactlyAndATypedAssistanceIsConverted() = runTest {
        val viewModel = loadedEditor()
        viewModel.addExercise(pullUps.copy(id = "assisted", type = ExerciseType.ASSISTED_BODYWEIGHT))
        // Only the row's text matters here, so it is set directly through the sheet.
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.ASSISTANCE, "35")
        viewModel.applyDraft()
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.ASSISTANCE, "101")

        viewModel.convertUnit(WeightUnit.KG)
        assertThat(viewModel.rows().single().assistance).isEqualTo("15.88")
        assertThat(viewModel.uiState.value.editorDraft?.assistance).isEqualTo("45.81")
        viewModel.convertUnit(WeightUnit.LBS)
        assertThat(viewModel.rows().single().assistance).isEqualTo("35")
        assertThat(viewModel.uiState.value.editorDraft?.assistance).isEqualTo("101")

        viewModel.convertUnit(WeightUnit.KG)
        viewModel.updateDraftField(DraftField.ASSISTANCE, "20")
        viewModel.convertUnit(WeightUnit.LBS)
        // 20 kg = 44.092 lb: the typed number is the source now, not the 101 it replaced.
        assertThat(viewModel.uiState.value.editorDraft?.assistance).isEqualTo("44.09")
    }

    @Test
    fun aFactoryLoadAddedInTheOtherUnitSwitchesBackToTheProfilesExactNumber() = runTest {
        val viewModel = loadedEditor(
            profiles = profilesWith(UserProfile(confirmedStartingLoads = mapOf(squat.id to 135.0)))
        )
        viewModel.convertUnit(WeightUnit.KG)
        viewModel.addExercise(squat)
        assertThat(viewModel.rows().single().load).isEqualTo("61.23")
        viewModel.convertUnit(WeightUnit.LBS)
        assertThat(viewModel.rows().single().load).isEqualTo("135")
    }

    @Test
    fun aStoredLoadWithMorePrecisionThanAFieldShowsIsDisclosedAndSavesRounded() = runTest {
        val stored = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = 100.125
            )
        ).copy(weightUnit = WeightUnit.LBS)
        val templates = RecordingTemplateRepository().apply { values.value = listOf(stored) }
        val viewModel = loadedEditor(templateId = stored.id, templates = templates)
        assertThat(viewModel.uiState.value.showsRoundedStoredValues).isTrue()
        assertThat(viewModel.rows().single().load).isEqualTo("100.13")
        assertThat(viewModel.uiState.value.isDirty).isFalse()

        viewModel.updateName("Renamed")
        viewModel.save()
        advanceUntilIdle()

        assertThat(templates.values.value.single().exercises.single().prescription.targetWeight)
            .isEqualTo(100.13)
    }

    @Test
    fun anUnroundedProfileStartingLoadIsSuggestedAtFieldPrecisionInEitherUnit() = runTest {
        // What UserProfileRepository.updateUnit stores for 135 lb after switching to kg.
        val converted = wallcrawl.elopenmike.com.core.model.convertWeight(135.0, WeightUnit.LBS, WeightUnit.KG)
        val templates = RecordingTemplateRepository()
        val viewModel = loadedEditor(
            templates = templates,
            profiles = profilesWith(
                UserProfile(preferredUnit = WeightUnit.KG, confirmedStartingLoads = mapOf(squat.id to converted))
            )
        )
        viewModel.addExercise(squat)
        assertThat(viewModel.rows().single().load).isEqualTo("61.23")
        viewModel.convertUnit(WeightUnit.LBS)
        viewModel.addExercise(squat)
        // The first row's 61.23 kg was what the field showed, so it converts as 134.99 lb. The
        // second is converted from the stored number itself, which is 135 lb.
        assertThat(viewModel.rows().map { it.load }).containsExactly("134.99", "135").inOrder()
        viewModel.convertUnit(WeightUnit.KG)
        assertThat(viewModel.rows().map { it.load }).containsExactly("61.23", "61.23").inOrder()

        viewModel.updateName("Legs")
        viewModel.save()
        advanceUntilIdle()
        assertThat(templates.values.value).hasSize(1)
    }

    @Test
    fun aSheetMatchingItsRowIsUnmodifiedWhateverItRemembersFromAUnitSwitch() = runTest {
        val viewModel = loadedEditor()
        viewModel.addSquatWithLoad("135")
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "70")
        viewModel.convertUnit(WeightUnit.KG)
        // Row 61.23 remembers 135; the sheet 31.75 remembers 70.
        viewModel.updateDraftField(DraftField.LOAD, "61.23")
        assertThat(viewModel.uiState.value.isSheetModified).isFalse()
    }

    @Test
    fun aLoadTooSmallToShowInTheOtherUnitBecomesItsSmallestStepRatherThanZero() = runTest {
        val viewModel = loadedEditor()
        viewModel.addSquatWithLoad("0.01")
        // 0.01 lb is 0.0045 kg, which two decimals would write as an explicit 0.
        viewModel.convertUnit(WeightUnit.KG)
        assertThat(viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.KG)
        assertThat(viewModel.rows().single().load).isEqualTo("0.01")
        // And back again exactly, through what the switch remembered.
        viewModel.convertUnit(WeightUnit.LBS)
        assertThat(viewModel.rows().single().load).isEqualTo("0.01")
    }

    @Test
    fun switchingUnitsIsRefusedWhileTheSheetHoldsMoreDecimalsThanItCanSave() = runTest {
        val viewModel = loadedEditor()
        viewModel.addSquatWithLoad("100")
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "12.345")
        viewModel.convertUnit(WeightUnit.KG)
        assertThat(viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.LBS)
        assertThat(viewModel.uiState.value.unitRefusal).isEqualTo(R.string.editor_error_unit_convert_invalid)
    }

    @Test
    fun anUnreadableOpenSheetMarksTheRecoveredDraftIncomplete() = runTest {
        val handle = SavedStateHandle()
        val first = loadedEditor(handle = handle)
        first.addExercise(squat)
        first.editExercise(first.rows().single().rowId)
        first.updateDraftField(DraftField.LOAD, "60")
        handle["templateEditor.editingRow"] = "not a row"

        val recreated = loadedEditor(handle = handle)

        assertThat(recreated.uiState.value.isDraftIncomplete).isTrue()
        assertThat(recreated.uiState.value.errorMessage).isEqualTo(R.string.editor_error_draft_incomplete)
    }

    @Test
    fun aRecoveredSheetThatDoesNotMatchItsRowMarksTheDraftIncomplete() = runTest {
        val handle = SavedStateHandle()
        val first = loadedEditor(handle = handle)
        first.addExercise(squat)
        val row = first.rows().single()
        // Readable, but naming another exercise than the row it claims to edit; Apply would
        // otherwise swap the exercise without the catalog check rows get.
        handle["templateEditor.editingRow"] = row.copy(exerciseId = "push-up").encode()

        val recreated = loadedEditor(handle = handle)

        assertThat(recreated.uiState.value.isDraftIncomplete).isTrue()
        assertThat(recreated.uiState.value.errorMessage).isEqualTo(R.string.editor_error_draft_incomplete)
    }

    @Test
    fun recoveredRowsSharingOneIdentityMarkTheDraftIncomplete() = runTest {
        val handle = SavedStateHandle()
        loadedEditor(handle = handle).addExercise(squat)
        val row = handle.get<ArrayList<String>>("templateEditor.rows")!!.single()
        // Two readable rows with one rowId would be edited, removed and keyed as one.
        handle["templateEditor.rows"] = arrayListOf(row, row)

        val recreated = loadedEditor(handle = handle)

        assertThat(recreated.uiState.value.isDraftIncomplete).isTrue()
        // One row reaches the list, so no key is used twice when it is drawn.
        assertThat(recreated.rows()).hasSize(1)
    }

    @Test
    fun typingInTheSheetDoesNotRewriteTheSavedRows() = runTest {
        val handle = SavedStateHandle()
        val viewModel = loadedEditor(handle = handle)
        viewModel.addExercise(squat)
        val saved = handle.get<ArrayList<String>>("templateEditor.rows")
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "6")
        viewModel.updateDraftField(DraftField.LOAD, "60")
        // Only the sheet changed, so the encoded rows are the very same list.
        assertThat(handle.get<ArrayList<String>>("templateEditor.rows")).isSameInstanceAs(saved)
        viewModel.applyDraft()
        assertThat(handle.get<ArrayList<String>>("templateEditor.rows")).isNotSameInstanceAs(saved)
    }

    @Test
    fun aDraftWhoseFirstEditIsInTheSheetStillRecoversItsRows() = runTest {
        // An existing template's rows come from the load, not an edit, so the first sheet
        // keystroke is what first writes them.
        val stored = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = 100.0
            )
        ).copy(weightUnit = WeightUnit.LBS)
        val templates = RecordingTemplateRepository().apply { values.value = listOf(stored) }
        val handle = SavedStateHandle()
        val first = loadedEditor(templateId = stored.id, templates = templates, handle = handle)
        first.editExercise(first.rows().single().rowId)
        first.updateDraftField(DraftField.LOAD, "105")

        val recreated = loadedEditor(templateId = stored.id, templates = templates, handle = handle)
        assertThat(recreated.rows()).hasSize(1)
        assertThat(recreated.uiState.value.editorDraft?.load).isEqualTo("105")
        assertThat(recreated.uiState.value.isDraftIncomplete).isFalse()
    }

    @Test
    fun theSheetQuestionGoesAwayWithTheSheetItAskedAbout() = runTest {
        val viewModel = loadedEditor()
        viewModel.addExercise(squat)
        viewModel.addExercise(squat)
        val (first, second) = viewModel.rows()
        viewModel.editExercise(first.rowId)
        viewModel.updateDraftField(DraftField.LOAD, "60")
        viewModel.dismissSheet()
        assertThat(viewModel.uiState.value.showSheetDiscardConfirmation).isTrue()

        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.showSheetDiscardConfirmation).isFalse()
        viewModel.editExercise(second.rowId)
        assertThat(viewModel.uiState.value.showSheetDiscardConfirmation).isFalse()
    }

    @Test
    fun aSuggestedLoadTheDraftsUnitCannotHoldIsLeftBlank() = runTest {
        val viewModel = loadedEditor(
            profiles = profilesWith(
                UserProfile(preferredUnit = WeightUnit.KG, confirmedStartingLoads = mapOf(squat.id to 9_000.0))
            )
        )
        viewModel.convertUnit(WeightUnit.LBS)
        viewModel.addExercise(squat)
        // 9000 kg is about 19 842 lb, beyond what a pound template may hold.
        assertThat(viewModel.rows().single().load).isEmpty()
    }

    @Test
    fun aRestChangedInOneSheetAndTypedBackInALaterOneKeepsItsOriginalClassification() = runTest {
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                restSeconds = 90,
                restClass = RestClass.MODERATE,
                restTargetSource = RestTargetSource.PRODUCT_POLICY
            )
        )
        templates.saveTemplate(template)
        val viewModel = loadedEditor(templateId = template.id, templates = templates)
        val rowId = viewModel.rows().single().rowId

        viewModel.editExercise(rowId)
        viewModel.updateDraftField(DraftField.REST, "45")
        viewModel.applyDraft()
        viewModel.editExercise(rowId)
        viewModel.updateDraftField(DraftField.REST, "90")
        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.isDirty).isFalse()
        viewModel.updateName("Leg day renamed")
        viewModel.save()
        advanceUntilIdle()

        val saved = templates.values.value.single().exercises.single().prescription
        assertThat(saved.restClass).isEqualTo(RestClass.MODERATE)
        assertThat(saved.restTargetSource).isEqualTo(RestTargetSource.PRODUCT_POLICY)
    }

    @Test
    fun theRoundedValuesNoteAppearsOnlyForATemplateThatStoresThem() = runTest {
        fun template(load: Double, unit: WeightUnit?) = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = load
            )
        ).copy(weightUnit = unit)
        suspend fun noteFor(stored: WorkoutTemplate?, handle: SavedStateHandle = SavedStateHandle()): Boolean {
            val templates = RecordingTemplateRepository().apply { values.value = listOfNotNull(stored) }
            return loadedEditor(templateId = stored?.id, templates = templates, handle = handle)
                .uiState.value.showsRoundedStoredValues
        }
        assertThat(noteFor(null)).isFalse()
        assertThat(noteFor(template(100.5, WeightUnit.LBS))).isFalse()
        assertThat(noteFor(template(100.125, WeightUnit.LBS))).isTrue()
        // A legacy template with no recorded unit is disclosed the same way.
        assertThat(noteFor(template(100.125, null))).isTrue()

        // A recovered draft over such a template still says so.
        val rounded = template(100.125, WeightUnit.LBS)
        val handle = SavedStateHandle()
        val templates = RecordingTemplateRepository().apply { values.value = listOf(rounded) }
        loadedEditor(templateId = rounded.id, templates = templates, handle = handle).updateName("Edited")
        assertThat(
            loadedEditor(templateId = rounded.id, templates = templates, handle = handle)
                .uiState.value.showsRoundedStoredValues
        ).isTrue()
    }

    @Test
    fun aSuggestedLoadAboveTheBoundIsLeftBlankInTheSameUnitToo() = runTest {
        val viewModel = loadedEditor(
            profiles = profilesWith(
                UserProfile(preferredUnit = WeightUnit.KG, confirmedStartingLoads = mapOf(squat.id to 5_000.0))
            )
        )
        viewModel.addExercise(squat)
        assertThat(viewModel.rows().single().load).isEmpty()
    }

    @Test
    fun aRestTypedBackAfterRecreationStillKeepsItsOriginalClassification() = runTest {
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                restSeconds = 90,
                restClass = RestClass.MODERATE,
                restTargetSource = RestTargetSource.PRODUCT_POLICY
            )
        )
        templates.saveTemplate(template)
        val handle = SavedStateHandle()
        val first = loadedEditor(templateId = template.id, templates = templates, handle = handle)
        first.editExercise(first.rows().single().rowId)
        first.updateDraftField(DraftField.REST, "45")
        first.applyDraft()

        val recreated = loadedEditor(templateId = template.id, templates = templates, handle = handle)
        recreated.editExercise(recreated.rows().single().rowId)
        recreated.updateDraftField(DraftField.REST, "90")
        recreated.applyDraft()
        assertThat(recreated.uiState.value.isDirty).isFalse()
        recreated.updateName("Renamed")
        recreated.save()
        advanceUntilIdle()

        val saved = templates.values.value.single().exercises.single().prescription
        assertThat(saved.restTargetSource).isEqualTo(RestTargetSource.PRODUCT_POLICY)
        assertThat(saved.restClass).isEqualTo(RestClass.MODERATE)
    }

    @Test
    fun theSameNumberWrittenDifferentlyIsNoChange() = runTest {
        val templates = RecordingTemplateRepository()
        val template = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                targetWeight = 60.0,
                restSeconds = 90
            )
        ).copy(weightUnit = WeightUnit.LBS)
        templates.saveTemplate(template)
        val viewModel = loadedEditor(templateId = template.id, templates = templates)
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "60.0")
        viewModel.updateDraftField(DraftField.REST, "090")
        viewModel.updateDraftNotes("  ")
        assertThat(viewModel.uiState.value.isSheetModified).isFalse()
        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.isDirty).isFalse()
    }

    @Test
    fun aUnitThatCannotHoldTheNumbersIsRefusedWhenConfirmedOrSwitchedTo() = runTest {
        val legacy = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = 5_000.0
            )
        )
        val templates = RecordingTemplateRepository().apply { values.value = listOf(legacy) }
        val viewModel = loadedEditor(templateId = legacy.id, templates = templates)

        // 5000 kg is beyond what a kilogram template may hold, so it was not entered in kg.
        viewModel.confirmUnit(WeightUnit.KG)
        assertThat(viewModel.uiState.value.weightUnit).isNull()
        assertThat(viewModel.uiState.value.unitRefusal).isEqualTo(R.string.editor_error_unit_confirm_too_large)
        // Nothing is opened or changed by the refusal.
        assertThat(viewModel.uiState.value.editorDraft).isNull()
        assertThat(viewModel.rows().single().load).isEqualTo("5000")

        viewModel.confirmUnit(WeightUnit.LBS)
        viewModel.convertUnit(WeightUnit.KG)
        assertThat(viewModel.rows().single().load).isEqualTo("2267.96")

        // A sheet holding a kilogram number pounds cannot hold refuses the switch.
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "5000")
        viewModel.convertUnit(WeightUnit.LBS)
        assertThat(viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.KG)
        assertThat(viewModel.uiState.value.unitRefusal).isEqualTo(R.string.editor_error_unit_convert_invalid)
    }

    @Test
    fun aFailedLoadSaysSoWithoutAskingAboutUnits() = runTest {
        val viewModel = loadedEditor(catalog = ThrowingExerciseCatalog())
        assertThat(viewModel.uiState.value.hasLoadFailed).isTrue()
        assertThat(loadedEditor().uiState.value.hasLoadFailed).isFalse()
    }

    @Test
    fun confirmingAUnitIgnoresTextThatIsNotANumberYet() = runTest {
        val legacy = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = 100.0
            )
        )
        val templates = RecordingTemplateRepository().apply { values.value = listOf(legacy) }
        val viewModel = loadedEditor(templateId = legacy.id, templates = templates)
        viewModel.editExercise(viewModel.rows().single().rowId)
        viewModel.updateDraftField(DraftField.LOAD, "6a")
        viewModel.confirmUnit(WeightUnit.KG)
        // Accepted: only a number can be too large; the text is left for validation to report.
        assertThat(viewModel.uiState.value.weightUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun discardingARecoveredDraftOfADeletedTemplateDoesNotAskAboutLegacyUnits() = runTest {
        val handle = SavedStateHandle()
        // A legacy template with a load, so its own unit is unknown.
        val stored = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = 100.0
            )
        )
        val templates = RecordingTemplateRepository().apply { values.value = listOf(stored) }
        loadedEditor(templateId = stored.id, templates = templates, handle = handle).updateName("Edited")
        templates.deleteTemplate(stored.id)

        val recovered = loadedEditor(templateId = stored.id, templates = templates, handle = handle)
        assertThat(recovered.uiState.value.weightUnit).isNull()
        recovered.confirmDiscard()

        assertThat(recovered.uiState.value.weightUnit).isEqualTo(WeightUnit.LBS)
        assertThat(recovered.rows()).isEmpty()
    }

    @Test
    fun aRecoveredDraftOfALegacyTemplateWithNoLoadsLeftTakesTheProfileUnit() = runTest {
        // The stored template still has its unitless load; the recovered draft removed it.
        val stored = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = 100.0
            )
        )
        val templates = RecordingTemplateRepository().apply { values.value = listOf(stored) }
        val handle = SavedStateHandle(
            mapOf(
                "templateEditor.hasDraft" to true,
                "templateEditor.name" to "Emptied",
                "templateEditor.notes" to "",
                "templateEditor.rows" to arrayListOf<String>()
            )
        )
        val recovered = loadedEditor(
            templateId = stored.id,
            templates = templates,
            profiles = profilesWith(UserProfile(preferredUnit = WeightUnit.KG)),
            handle = handle
        )
        assertThat(recovered.uiState.value.weightUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun aRefusedUnitTapLeavesAStandingRefusalInPlaceAndTheNextEditClearsIt() = runTest {
        val handle = SavedStateHandle()
        val stored = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8),
                targetWeight = 5_000.0
            )
        )
        val templates = RecordingTemplateRepository().apply { values.value = listOf(stored) }
        loadedEditor(templateId = stored.id, templates = templates, handle = handle).updateName("Edited")
        templates.deleteTemplate(stored.id)
        val recovered = loadedEditor(templateId = stored.id, templates = templates, handle = handle)
        assertThat(recovered.uiState.value.errorMessage).isEqualTo(R.string.template_error_missing)

        recovered.confirmUnit(WeightUnit.KG)
        assertThat(recovered.uiState.value.unitRefusal).isEqualTo(R.string.editor_error_unit_confirm_too_large)
        assertThat(recovered.uiState.value.errorMessage).isEqualTo(R.string.template_error_missing)

        recovered.updateNotes("next edit")
        assertThat(recovered.uiState.value.unitRefusal).isNull()
    }

    @Test
    fun anOverLongNumberIsRefusedOnItsFieldRatherThanCutIntoAnotherNumber() = runTest {
        val viewModel = loadedEditor()
        viewModel.addExercise(squat)
        viewModel.editExercise(viewModel.rows().single().rowId)
        // Cut at ten characters this would be "0000000000", a valid rest of 0.
        viewModel.updateDraftField(DraftField.REST, "00000000001")
        viewModel.updateDraftField(DraftField.LOAD, "1".repeat(40))
        assertThat(viewModel.uiState.value.editorDraft?.restSeconds).isEqualTo("00000000001")
        assertThat(viewModel.uiState.value.editorDraft?.load?.length).isEqualTo(11)
        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.fieldErrors[DraftField.REST])
            .isEqualTo(R.string.draft_error_out_of_range_with_range)
        assertThat(viewModel.uiState.value.fieldErrors).containsKey(DraftField.LOAD)

        // Cut mid-number by the field's cap, it is still reported as out of range rather
        // than as an unfinished number.
        viewModel.updateDraftField(DraftField.LOAD, "1234567890.5")
        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.fieldErrors[DraftField.LOAD])
            .isEqualTo(R.string.draft_error_out_of_range_with_range)

        // A decimal in a whole-number field is unfinished for it, however long.
        viewModel.updateDraftField(DraftField.SETS, "1234567890,")
        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.fieldErrors[DraftField.SETS])
            .isEqualTo(R.string.set_field_incomplete_number)
        viewModel.updateDraftField(DraftField.SETS, "3")

        // Not shaped like a number, it stays unfinished whatever its length.
        viewModel.updateDraftField(DraftField.LOAD, "1.2.3.4.5.6")
        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.fieldErrors[DraftField.LOAD])
            .isEqualTo(R.string.set_field_incomplete_number)

        // Too precise and too long at once: the precision is what the user can act on.
        viewModel.updateDraftField(DraftField.LOAD, "100.1234567")
        viewModel.applyDraft()
        assertThat(viewModel.uiState.value.fieldErrors[DraftField.LOAD])
            .isEqualTo(R.string.draft_error_too_precise)
    }

    // --- save identity and deletion -------------------------------------------------------------

    @Test
    fun aNewTemplateKeepsOneIdentityAcrossAFailedSaveRecreationAndARepeatedWrite() = runTest {
        val handle = SavedStateHandle()
        val templates = RecordingTemplateRepository(failNextSave = true)
        val first = loadedEditor(templates = templates, handle = handle)
        first.addSquatWithLoad("100")
        first.updateName("Legs")
        first.save()
        advanceUntilIdle()
        assertThat(first.uiState.value.errorMessage).isEqualTo(R.string.editor_error_save_failed)

        // Process death after a write committed but before its cleared state was saved:
        // the recovered draft writes the same row again instead of a second template.
        val beforeSave = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val recreated = loadedEditor(templates = templates, handle = handle)
        recreated.save()
        recreated.save()
        advanceUntilIdle()
        assertThat(recreated.uiState.value.isSaved).isTrue()

        loadedEditor(templates = templates, handle = beforeSave).save()
        advanceUntilIdle()
        assertThat(templates.values.value).hasSize(1)
    }

    @Test
    fun anEditOfATemplateDeletedAfterItLoadedIsRefusedAndNeverWrittenBack() = runTest {
        val stored = templateWith(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(5, 8)
            )
        )
        val templates = RecordingTemplateRepository().apply { values.value = listOf(stored) }
        val viewModel = loadedEditor(templateId = stored.id, templates = templates)
        viewModel.updateName("Renamed")

        templates.deleteTemplate(stored.id)
        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isSaved).isFalse()
        assertThat(templates.values.value).isEmpty()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(R.string.template_error_missing)
        assertThat(viewModel.uiState.value.name).isEqualTo("Renamed")
        // The refusal stands while the user keeps editing.
        viewModel.updateNotes("still here")
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo(R.string.template_error_missing)
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun TemplateEditorViewModel.rows(): List<TemplateExerciseDraft> =
        uiState.value.selectedExercises

    private fun restoredRows(handle: SavedStateHandle): List<String> =
        handle.get<ArrayList<String>>("templateEditor.rows").orEmpty()

    private fun editor(
        templateId: String? = null,
        templates: WorkoutTemplateRepository = RecordingTemplateRepository(),
        profiles: UserProfileRepository = OfflineUserProfileRepository(FakeUserProfileDao()),
        catalog: ExerciseCatalog = InMemoryExerciseCatalog(),
        handle: SavedStateHandle = SavedStateHandle(),
        locale: Locale = Locale.US,
        now: () -> Long = System::currentTimeMillis
    ): TemplateEditorViewModel = TemplateEditorViewModel(
        templateId = templateId,
        templateRepository = templates,
        userProfileRepository = profiles,
        exerciseCatalog = catalog,
        localizationSource = null,
        savedStateHandle = handle,
        nowTimestamp = now,
        locale = { locale }
    )

    private suspend fun loadedEditor(
        templateId: String? = null,
        templates: WorkoutTemplateRepository = RecordingTemplateRepository(),
        profiles: UserProfileRepository = OfflineUserProfileRepository(FakeUserProfileDao()),
        catalog: ExerciseCatalog = InMemoryExerciseCatalog(),
        handle: SavedStateHandle = SavedStateHandle()
    ): TemplateEditorViewModel = editor(templateId, templates, profiles, catalog, handle)
        .also { it.uiState.first { state -> !state.isLoading } }

    private fun templateWith(
        prescription: ExercisePrescription,
        name: String = "Leg day"
    ): WorkoutTemplate = WorkoutTemplate(
        id = "template-1",
        name = name,
        notes = "",
        createdAtTimestamp = 1_000L,
        updatedAtTimestamp = 1_000L,
        exercises = listOf(PlannedExercise("barbell-back-squat", prescription))
    )

    /** A catalog whose read fails, which is what makes the editor's load throw. */
    private class ThrowingExerciseCatalog : ExerciseCatalog {
        override fun getAllExercises(): Flow<List<Exercise>> =
            flow { throw IllegalStateException("catalog unavailable") }

        override suspend fun getExerciseById(id: String): Exercise? = null

        override fun searchExercises(
            query: String,
            muscle: String?,
            equipment: String?
        ): Flow<List<Exercise>> = flow { emit(emptyList()) }

        override suspend fun getMuscleGroups(): List<String> = emptyList()

        override suspend fun getEquipmentTypes(): List<String> = emptyList()
    }

    private class RecordingTemplateRepository(
        private var failNextSave: Boolean = false,
        private val failReads: Boolean = false
    ) : WorkoutTemplateRepository {
        val values = MutableStateFlow<List<WorkoutTemplate>>(emptyList())
        var saveCount = 0
            private set

        override fun observeTemplates() = values
        override fun observeTemplate(templateId: String) = values.map { list ->
            list.firstOrNull { it.id == templateId }
        }

        override suspend fun getTemplate(templateId: String): WorkoutTemplate? {
            // What OfflineWorkoutTemplateRepository does when a stored exercise id is not
            // in the current catalog: it throws rather than returning null.
            if (failReads) throw IllegalArgumentException("catalog reference rejected")
            return values.value.firstOrNull { it.id == templateId }
        }

        override suspend fun saveTemplate(template: WorkoutTemplate, mustExist: Boolean) {
            // The two rules OfflineWorkoutTemplateRepository enforces before writing.
            require(!template.requiresUnitConfirmation) { "unit required" }
            if (mustExist && values.value.none { it.id == template.id }) {
                throw MissingTemplateException(template.id)
            }
            if (failNextSave) {
                failNextSave = false
                throw IllegalStateException("write refused")
            }
            saveCount += 1
            values.value = values.value.filterNot { it.id == template.id } + template
        }

        override suspend fun deleteTemplate(templateId: String) {
            values.value = values.value.filterNot { it.id == templateId }
        }
    }
}
