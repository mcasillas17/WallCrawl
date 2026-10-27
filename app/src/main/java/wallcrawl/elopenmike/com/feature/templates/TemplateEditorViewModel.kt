package wallcrawl.elopenmike.com.feature.templates

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.DefaultExercisePrescriptionFactory
import wallcrawl.elopenmike.com.core.database.repository.MissingTemplateException
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseSearchIndex
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalization
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationSource
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.core.model.convertWeightForEditing
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting

data class TemplateEditorUiState(
    val isLoading: Boolean = true,
    val templateId: String? = null,
    val name: String = "",
    val notes: String = "",
    val query: String = "",
    val selectedExercises: List<TemplateExerciseDraft> = emptyList(),
    val availableEquipment: Set<String> = emptySet(),
    /**
     * The unit every load and assistance value in this draft is written in. Null only for a
     * legacy template whose loads never recorded one, until the user says which it was.
     */
    val weightUnit: WeightUnit? = null,
    val isPickerOpen: Boolean = false,
    val isSaving: Boolean = false,
    /**
     * The write committed. The screen leaves on this rather than on a callback the save was
     * handed, so a rotation during the write cannot strand it behind a stale navigator.
     */
    val isSaved: Boolean = false,
    /**
     * Why the last tap on a unit choice was refused, shown beside those choices. Separate
     * from [errorMessage], so a standing refusal stays where it is.
     */
    @StringRes val unitRefusal: Int? = null,
    /**
     * Loading the template, catalog or profile failed. Saving and adding exercises are
     * refused; the name, notes and existing rows stay editable, so nothing typed is lost
     * while the user decides to discard or try again.
     */
    val hasLoadFailed: Boolean = false,
    @StringRes val errorMessage: Int? = null,
    /** The sheet's working copy of one row, or null when no row is being edited. */
    val editorDraft: TemplateExerciseDraft? = null,
    val fieldErrors: Map<DraftField, Int> = emptyMap(),
    val isDirty: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    /** The edit sheet holds typing and the user tried to close it some way other than Cancel. */
    val showSheetDiscardConfirmation: Boolean = false,
    /**
     * The opened template stored a load, assistance or distance with more decimals than a
     * field shows — an older editor saved unit-converted starting loads unrounded. Fields
     * show two, and saving writes two, so the screen says so.
     */
    val showsRoundedStoredValues: Boolean = false,
    /**
     * The rows this catalog cannot resolve, by row identity. Keyed by exercise id these
     * would also condemn a freshly added, valid row of the same exercise, which is exactly
     * what the flagged row's remedy asks the user to do.
     */
    val unresolvedRowIds: Set<String> = emptySet(),
    /**
     * A saved row could not be read back, so this draft cannot be saved. Discarding is the
     * way out, which means the discard route has to be open even when nothing is dirty.
     */
    val isDraftIncomplete: Boolean = false,
    /**
     * The picker's search, so typing "sentadilla" finds the same exercises as typing
     * "squat". Empty until the catalog and overlay have loaded, which only means the
     * picker lists nothing until then.
     */
    val searchIndex: ExerciseSearchIndex = ExerciseSearchIndex.EMPTY
) {
    /** The whole catalog the picker offers, which is what the index was built over. */
    val catalogExercises: List<Exercise> get() = searchIndex.all

    /**
     * Resolved once per state and only if something reads it: the picker reads it three
     * times in one composition, and an initializer would re-run the catalog search on every
     * copy of this state — twice per edit, with the picker shut.
     */
    val filteredExercises: List<Exercise> by lazy(LazyThreadSafetyMode.NONE) { searchIndex.matching(query) }

    /**
     * Resolved on first use rather than per row: the selected list can hold fifty rows and
     * each one would otherwise scan the whole catalog on every recomposition.
     */
    private val exercisesById: Map<String, Exercise> by
        lazy(LazyThreadSafetyMode.NONE) { searchIndex.all.associateBy(Exercise::id) }

    fun exerciseFor(draft: TemplateExerciseDraft): Exercise? = exercisesById[draft.exerciseId]

    /** Whether the open sheet's working copy differs from the row it edits. */
    val isSheetModified: Boolean
        get() = editorDraft != null && selectedExercises.none {
            // Same row, same content; remembered pre-switch values are bookkeeping.
            it.rowId == editorDraft.rowId && it.comparable() == editorDraft.comparable()
        }

    /** Whether any row, or the open sheet, holds load or assistance text. */
    val hasLoads: Boolean
        get() = (selectedExercises + listOfNotNull(editorDraft)).any {
            it.load.isNotBlank() || it.assistance.isNotBlank()
        }

    /** Whether the template already holds as many exercises as the domain model allows. */
    val isAtExerciseLimit: Boolean get() = selectedExercises.size >= WorkoutTemplate.MAX_EXERCISES
}

/**
 * Editing state for one template.
 *
 * Rows are [TemplateExerciseDraft]s rather than `PlannedExercise`es because
 * `ExercisePrescription` validates in its constructor and a field mid-keystroke is not a
 * valid prescription yet. Text becomes a domain object at exactly two points: applying one
 * row's edits, and saving the template.
 *
 * The whole draft is mirrored into [savedStateHandle] on every change, so a configuration
 * change or system-initiated process death brings back the name, the notes, the ordered
 * rows with their identities, and the incomplete text in the field being typed. It is
 * `onSaveInstanceState`, not storage: uninstall, cleared app data, a force-stop and task
 * removal all discard it, and nothing is written to the database.
 */
class TemplateEditorViewModel(
    private val templateId: String?,
    private val templateRepository: WorkoutTemplateRepository,
    private val userProfileRepository: UserProfileRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val localizationSource: ExerciseLocalizationSource? = null,
    private val prescriptionFactory: DefaultExercisePrescriptionFactory =
        DefaultExercisePrescriptionFactory(),
    private val nowTimestamp: () -> Long = System::currentTimeMillis,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val locale: () -> Locale = Locale::getDefault
) : ViewModel() {
    // Restored before the load starts, so the asynchronous load can see that a draft is
    // already on screen and fill in only the reference data around it.
    private var restoredDraft: RestoredDraft? = restoreDraft(savedStateHandle)

    /** What the editor would have to look like to count as unchanged. */
    private var baseline: EditorBaseline = EditorBaseline("", "", emptyList(), null)

    /**
     * The id this draft saves under. Fixed for the life of the draft, and kept with it, so a
     * retried or recovered save of a new template overwrites one row instead of adding two.
     */
    private val saveId: String = templateId
        ?: savedStateHandle.get<String>(STATE_TEMPLATE_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[STATE_TEMPLATE_ID] = it }

    /** False while `baseline` is still the placeholder, which matches no saved template. */
    private var baselineLoaded = false

    private val mutableState = MutableStateFlow(
        TemplateEditorUiState(
            templateId = templateId,
            name = restoredDraft?.name.orEmpty(),
            notes = restoredDraft?.notes.orEmpty(),
            selectedExercises = restoredDraft?.rows.orEmpty(),
            editorDraft = restoredDraft?.editorDraft,
            weightUnit = restoredDraft?.weightUnit,
            // Restoring a draft at all means there were unsaved changes. Comparing against
            // the placeholder baseline would call an emptied draft unchanged, and the back
            // arrow is on screen before the load finishes.
            isDirty = restoredDraft != null,
            isDraftIncomplete = restoredDraft?.rowsWereDropped == true,
            errorMessage = if (restoredDraft?.rowsWereDropped == true) {
                R.string.editor_error_draft_incomplete
            } else {
                null
            }
        )
    )
    val uiState: StateFlow<TemplateEditorUiState> = mutableState.asStateFlow()

    private var originalTemplate: WorkoutTemplate? = null

    private var profile: UserProfile? = null

    init {
        viewModelScope.launch {
            try {
                val loadedProfile = userProfileRepository.getProfileOnce()
                val exercises = exerciseCatalog.getAllExercises().first()
                val template = templateId?.let { templateRepository.getTemplate(it) }
                val localization =
                    localizationSource?.localization() ?: ExerciseLocalization.EMPTY
                // Folding every catalog and overlay term is the one expensive step here, so
                // it does not run on the thread drawing the loading state.
                val searchIndex = withContext(Dispatchers.Default) {
                    ExerciseSearchIndex(exercises, localization)
                }
                profile = loadedProfile
                val catalogById = exercises.associateBy { it.id }
                if (templateId != null && template == null) {
                    // Nothing was loaded, so a freshly opened editor's adopted unit is its
                    // baseline too; otherwise it would count as an edit nobody made.
                    // Discarding a recovered draft lands here too, so it must not come back
                    // asking which unit a template that no longer exists was saved in. A
                    // recovered draft stays dirty regardless (no baseline ever loaded).
                    baseline = baseline.copy(weightUnit = loadedProfile.preferredUnit)
                    // An edit of something that is gone is never quietly turned into a new
                    // template; the draft stays on screen and saving is refused. The reader
                    // still gets their own unit and the real catalog, and dirty state is
                    // computed, or a recovered draft would leave without asking.
                    settle {
                        it.copy(
                            isLoading = false,
                            availableEquipment = loadedProfile.availableEquipment.toSet(),
                            searchIndex = searchIndex,
                            unresolvedRowIds = it.unresolvedRows(catalogById),
                            errorMessage = R.string.template_error_missing
                        ).withUnitIfUnweighed(loadedProfile.preferredUnit)
                    }
                    return@launch
                }
                originalTemplate = template
                val roundsStoredValues = template?.exercises.orEmpty().any { exercise ->
                    listOfNotNull(
                        exercise.prescription.targetWeight,
                        exercise.prescription.targetAssistanceWeight,
                        exercise.prescription.targetDistanceMeters
                    ).any(TemplateExerciseDraft::isRoundedForEditing)
                }
                val savedRows = template?.exercises
                    ?.map { TemplateExerciseDraft.from(it, locale()) }
                    .orEmpty()
                // The template's own unit. A new template, or a legacy one with nothing
                // weighed, takes the profile's ([withUnitIfUnweighed]).
                val fresh = TemplateEditorUiState(
                    isLoading = false,
                    templateId = templateId,
                    name = template?.name.orEmpty(),
                    notes = template?.notes.orEmpty(),
                    selectedExercises = savedRows,
                    availableEquipment = loadedProfile.availableEquipment.toSet(),
                    weightUnit = template?.weightUnit,
                    searchIndex = searchIndex
                ).withUnitIfUnweighed(loadedProfile.preferredUnit)
                baseline = EditorBaseline(
                    name = fresh.name,
                    notes = fresh.notes,
                    rows = fresh.selectedExercises,
                    weightUnit = fresh.weightUnit
                )
                baselineLoaded = true
                val loaded = if (restoredDraft != null) {
                    // Reference data only. Overwriting the name, notes, rows or a restored unit
                    // here would throw away the edits this screen just restored. The one
                    // addition is a unit for a draft with no unit and nothing weighed
                    // ([withUnitIfUnweighed]).
                    mutableState.value.copy(
                        isLoading = false,
                        availableEquipment = loadedProfile.availableEquipment.toSet(),
                        searchIndex = searchIndex
                    ).withUnitIfUnweighed(loadedProfile.preferredUnit)
                } else {
                    fresh
                }
                mutableState.value = loaded.copy(
                    showsRoundedStoredValues = roundsStoredValues,
                    isDirty = isDirty(loaded),
                    unresolvedRowIds = loaded.unresolvedRows(catalogById)
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                settle {
                    it.copy(
                        isLoading = false,
                        hasLoadFailed = true,
                        errorMessage = R.string.editor_error_load_failed
                    )
                }
            }
        }
    }

    fun updateName(value: String) = edit {
        it.copy(name = value.takeWhole(WorkoutTemplate.MAX_NAME_LENGTH), errorMessage = null)
    }

    fun updateNotes(value: String) = edit {
        it.copy(notes = value.takeWhole(WorkoutTemplate.MAX_NOTES_LENGTH), errorMessage = null)
    }

    // Search text and the picker's open state are screen chrome, not part of the draft, so
    // these recompute without rewriting every encoded row on each keystroke — and without
    // marking an editor nobody has typed into as holding one.
    fun updateQuery(value: String) = settle { it.copy(query = value.takeWhole(MAX_QUERY_LENGTH)) }

    fun openPicker() = settle { it.copy(isPickerOpen = true, query = "") }

    fun closePicker() = settle { it.copy(isPickerOpen = false, query = "") }

    fun addExercise(exercise: Exercise) {
        val loadedProfile = profile ?: return
        if (mutableState.value.isSaving) return
        // Refused here rather than by the domain constructor inside the save coroutine,
        // where it would surface as a retryable error that could never succeed. No message:
        // the screen states the cap beside the control it disables, before the tap.
        if (mutableState.value.isAtExerciseLimit) return
        val planned = PlannedExercise(
            exerciseId = exercise.id,
            prescription = prescriptionFactory.create(
                exercise,
                WorkoutGenerationContext(userProfile = loadedProfile)
            )
        )
        val row = suggestedRow(planned, loadedProfile.preferredUnit, mutableState.value.weightUnit)
        edit {
            it.copy(
                selectedExercises = it.selectedExercises + row,
                isPickerOpen = false,
                query = "",
                errorMessage = null
            )
        }
    }

    /**
     * The factory's suggestion as a row in the draft's unit. Its confirmed starting load is
     * in the profile's unit and may be stored unrounded after a unit change, so the number
     * itself is converted, once, and the profile-unit text is remembered for switching back.
     * A suggestion the draft's unit cannot hold, or one in a draft whose unit is still
     * unknown, is left blank rather than claimed.
     */
    private fun suggestedRow(
        planned: PlannedExercise,
        profileUnit: WeightUnit,
        draftUnit: WeightUnit?
    ): TemplateExerciseDraft {
        val inProfileUnit = TemplateExerciseDraft.from(planned, locale())
        if (draftUnit == null) return inProfileUnit.copy(load = "", assistance = "")
        // One rule for both paths: converted (or not), then bounded by the draft's unit.
        fun Double?.inDraftUnit(): Double? = this
            ?.let { convertWeightForEditing(it, profileUnit, draftUnit) }
            ?.takeIf { it <= TemplateExerciseDraft.maxLoad(draftUnit) }
        val prescription = planned.prescription
        val converted = TemplateExerciseDraft.from(
            planned.copy(
                prescription = prescription.copy(
                    targetWeight = prescription.targetWeight.inDraftUnit(),
                    targetAssistanceWeight = prescription.targetAssistanceWeight.inDraftUnit()
                )
            ),
            locale(),
            rowId = inProfileUnit.rowId
        )
        if (draftUnit == profileUnit) return converted
        return converted.copy(
            loadBeforeConversion = inProfileUnit.load.takeIf { converted.load.isNotEmpty() },
            assistanceBeforeConversion = inProfileUnit.assistance.takeIf { converted.assistance.isNotEmpty() }
        )
    }

    fun removeExercise(rowId: String) = edit { state ->
        val remaining = state.selectedExercises.filterNot { it.rowId == rowId }
        state.copy(
            selectedExercises = remaining,
            editorDraft = state.editorDraft?.takeIf { it.rowId != rowId },
            unresolvedRowIds = state.unresolvedRowIds
                .intersect(remaining.map { it.rowId }.toSet()),
            errorMessage = null
        )
    }

    fun moveExercise(rowId: String, direction: Int) = edit { state ->
        val index = state.selectedExercises.indexOfFirst { it.rowId == rowId }
        val destination = index + direction
        if (index < 0 || destination !in state.selectedExercises.indices) {
            return@edit state
        }
        state.copy(
            selectedExercises = state.selectedExercises.toMutableList().apply {
                add(destination, removeAt(index))
            },
            errorMessage = null
        )
    }

    fun changeSetCount(rowId: String, delta: Int) = edit { state ->
        state.copy(
            selectedExercises = state.selectedExercises.map { row ->
                if (row.rowId != rowId) return@map row
                // Text that is not a number yet steps to the minimum rather than past it.
                val next = LocaleFormatting.parseIntInput(row.sets)?.plus(delta)
                    ?: ExercisePrescription.MIN_TARGET_SETS
                row.copy(
                    sets = LocaleFormatting.formatEditableInt(
                        next.coerceIn(
                            ExercisePrescription.MIN_TARGET_SETS,
                            ExercisePrescription.MAX_TARGET_SETS
                        ),
                        locale()
                    )
                )
            },
            errorMessage = null
        )
    }

    /** Opens the editing sheet on one row, with its current values as the working copy. */
    fun editExercise(rowId: String) = edit { state ->
        state.copy(
            editorDraft = state.selectedExercises.firstOrNull { it.rowId == rowId },
            fieldErrors = emptyMap(),
            errorMessage = null
        )
    }

    fun updateDraftField(field: DraftField, text: String) = edit { state ->
        val draft = state.editorDraft ?: return@edit state
        // Kept to one past what the parser accepts, so an over-long entry stays over-long and
        // is refused on the field instead of being cut into a shorter, valid number. The
        // bound keeps saved state bounded.
        val value = TemplateExerciseDraft.sanitize(text).takeWhole(LocaleFormatting.MAX_INPUT_LENGTH + 1)
        val updated = when (field) {
            DraftField.SETS -> draft.copy(sets = value)
            DraftField.REP_MIN -> draft.copy(repMin = value)
            DraftField.REP_MAX -> draft.copy(repMax = value)
            // A remembered pre-switch value is checked against the field when the unit next
            // changes, so retyping the same number keeps it and a different one replaces it.
            DraftField.LOAD -> draft.copy(load = value)
            DraftField.ASSISTANCE -> draft.copy(assistance = value)
            DraftField.DURATION -> draft.copy(durationSeconds = value)
            DraftField.DISTANCE -> draft.copy(distanceMeters = value)
            DraftField.EFFORT_MIN -> draft.copy(effortMinRir = value)
            DraftField.EFFORT_MAX -> draft.copy(effortMaxRir = value)
            // Provenance follows the value, not the keystroke: a rest number typed back to
            // what it was is not a user preference, and a row already carrying one keeps it.
            DraftField.REST -> {
                draft.copy(
                    restSeconds = value,
                    restWasEdited = LocaleFormatting.parseIntInput(value) !=
                        LocaleFormatting.parseIntInput(draft.originalRestSeconds)
                )
            }
        }
        state.copy(
            editorDraft = updated,
            fieldErrors = state.fieldErrors - field,
            errorMessage = null
        )
    }

    fun updateDraftNotes(text: String) = edit { state ->
        val draft = state.editorDraft ?: return@edit state
        state.copy(
            editorDraft = draft.copy(
                notes = TemplateExerciseDraft.sanitize(text)
                    .takeWhole(WorkoutTemplate.MAX_EXERCISE_NOTES_LENGTH)
            ),
            errorMessage = null
        )
    }

    /** Validates the working copy and, only if it is a prescription, commits it to its row. */
    fun applyDraft() {
        val draft = mutableState.value.editorDraft ?: return
        when (val result = draft.validate(mutableState.value.weightUnit)) {
            is DraftValidation.Valid -> edit { state ->
                state.copy(
                    selectedExercises = state.selectedExercises.map { row ->
                        if (row.rowId == draft.rowId) draft else row
                    },
                    editorDraft = null,
                    fieldErrors = emptyMap(),
                    errorMessage = null
                )
            }

            // The summary message is what is announced: a refused Apply leaves the sheet and
            // its button as they were, as a refused save does.
            is DraftValidation.Invalid -> edit {
                it.copy(
                    fieldErrors = result.errors,
                    errorMessage = result.message ?: R.string.editor_error_invalid_fields
                )
            }
        }
    }

    fun cancelDraft() = edit {
        // Any message about the sheet goes with it; a standing refusal is re-derived.
        it.copy(
            editorDraft = null,
            fieldErrors = emptyMap(),
            showSheetDiscardConfirmation = false,
            errorMessage = null
        )
    }

    /**
     * Back, a swipe or a scrim tap on the edit sheet. Typing in it is unsaved work, so that
     * asks first; an untouched sheet just closes.
     */
    fun dismissSheet() {
        if (mutableState.value.isSheetModified) {
            settle { it.copy(showSheetDiscardConfirmation = true) }
        } else {
            cancelDraft()
        }
    }

    fun keepEditingSheet() = settle { it.copy(showSheetDiscardConfirmation = false) }

    /**
     * Expresses every load and assistance value in [unit]. A conversion, not a relabel:
     * 100 lb becomes 45,36 kg. Refused while any of those fields holds text that is not a
     * number yet, since there is nothing to convert it from.
     */
    fun convertUnit(unit: WeightUnit) {
        val state = mutableState.value
        val from = state.weightUnit ?: return
        if (from == unit) return
        val rows = state.selectedExercises.map { it.convertedLoads(from, unit, locale()) }
        val sheet = state.editorDraft?.let { it.convertedLoads(from, unit, locale()) }
        if (rows.any { it == null } || (state.editorDraft != null && sheet == null) ||
            (rows + sheet).any { it != null && it.exceedsLoadBound(unit) }
        ) {
            settle { it.copy(unitRefusal = R.string.editor_error_unit_convert_invalid) }
            return
        }
        edit {
            it.copy(
                weightUnit = unit,
                selectedExercises = rows.map { row -> row!! },
                editorDraft = sheet,
                errorMessage = null
            )
        }
    }

    /**
     * Says which unit a legacy template's loads were entered in. The numbers are not
     * changed: this records what they already meant.
     */
    fun confirmUnit(unit: WeightUnit) {
        val state = mutableState.value
        if (state.weightUnit != null) return
        // A number that cannot be a load in that unit cannot have been entered in it. Only
        // kilograms can refuse: the pound limit is the domain's own, which no stored load
        // exceeds. The message states that limit; nothing changes until the user acts.
        if ((state.selectedExercises + listOfNotNull(state.editorDraft)).any { it.exceedsLoadBound(unit) }) {
            settle { it.copy(unitRefusal = R.string.editor_error_unit_confirm_too_large) }
            return
        }
        edit { it.copy(weightUnit = unit, errorMessage = null) }
    }

    fun requestDiscard() = settle {
        it.copy(showDiscardConfirmation = it.isDirty || it.isDraftIncomplete)
    }

    fun dismissDiscard() = settle { it.copy(showDiscardConfirmation = false) }

    fun confirmDiscard() {
        // The draft is being thrown away, so it is no longer unsaved work. Without this the
        // no-baseline rule below would keep asking on every subsequent back press.
        restoredDraft = null
        if (mutableState.value.isSaving) return
        // Discard restores the stored template wholesale, so the shortened draft — and the
        // reason it could not be saved — are both gone.
        clearDraft()
        settle { state ->
            state.copy(
                name = baseline.name,
                notes = baseline.notes,
                selectedExercises = baseline.rows,
                weightUnit = baseline.weightUnit,
                // The baseline came from the repository, which validates catalog
                // references, so nothing in it is missing. Carrying the old set forward
                // would refuse every save while flagging no row on screen.
                unresolvedRowIds = emptySet(),
                isDraftIncomplete = false,
                editorDraft = null,
                fieldErrors = emptyMap(),
                showDiscardConfirmation = false,
                errorMessage = null
            )
        }
    }

    fun save() {
        val state = mutableState.value
        // isSaving is set synchronously below and stays set after a write succeeds, so
        // neither a double tap nor a tap after success can start a second write.
        if (state.isLoading || state.isSaving) return
        // Every refusal below settles rather than updates. A refusal changes no drafted
        // content, and `persistDraft` marks the handle as holding a draft unconditionally,
        // so refusing a save on an editor nobody had typed into would leave the next
        // recreation restoring an empty draft and asking whether to discard it.
        if (state.hasLoadFailed) {
            // Nothing downstream is trustworthy: the catalog check that flags a retired
            // exercise never ran, so this refuses for a new template as well as an edit.
            settle { it.copy(errorMessage = R.string.editor_error_load_failed) }
            return
        }
        if (templateId != null && originalTemplate == null) {
            settle { it.copy(errorMessage = R.string.template_error_missing) }
            return
        }
        if (state.name.isBlank()) {
            settle { it.copy(errorMessage = R.string.editor_error_name_required) }
            return
        }
        if (state.selectedExercises.isEmpty()) {
            settle { it.copy(errorMessage = R.string.editor_error_exercise_required) }
            return
        }
        if (state.unresolvedRowIds.isNotEmpty()) {
            settle { it.copy(errorMessage = R.string.editor_error_missing_exercise) }
            return
        }
        if (state.isDraftIncomplete) {
            // Saving would write a template shorter than the one being edited, and no edit
            // can prove the missing row back. Discarding is the way out, and says so.
            settle { it.copy(errorMessage = R.string.editor_error_draft_incomplete) }
            return
        }
        // Every path that leaves a draft without loads gives it the profile unit, so a missing
        // unit here is a legacy one that still holds loads.
        val unit = state.weightUnit
        if (unit == null) {
            settle { it.copy(errorMessage = R.string.editor_error_unit_unconfirmed) }
            return
        }

        val validated = state.selectedExercises.map { it to it.validate(unit) }
        val invalid = validated.firstOrNull { it.second is DraftValidation.Invalid }
        if (invalid != null) {
            val failure = invalid.second as DraftValidation.Invalid
            // An update, not a settle: this opens the sheet on the offending row, and which
            // row is being edited is drafted state that recovery restores.
            update {
                it.copy(
                    editorDraft = invalid.first,
                    fieldErrors = failure.errors,
                    errorMessage = failure.message ?: R.string.editor_error_invalid_fields
                )
            }
            return
        }
        val exercises = validated.map { (it.second as DraftValidation.Valid).exercise }

        // Settles for the same reason the refusals above do. Starting a write drafts
        // nothing, and the save button is not gated on dirty state, so an untouched
        // template re-saved as-is would otherwise mark the handle — and a process death
        // before `clearDraft` would bring it back asking about changes nobody made.
        settle { it.copy(isSaving = true, errorMessage = null) }
        val snapshot = mutableState.value
        viewModelScope.launch {
            try {
                val timestamp = nowTimestamp().coerceAtLeast(0L)
                val created = originalTemplate?.createdAtTimestamp ?: timestamp
                templateRepository.saveTemplate(
                    WorkoutTemplate(
                        id = saveId,
                        name = snapshot.name.trim(),
                        notes = snapshot.notes.trim(),
                        createdAtTimestamp = created,
                        // A backwards clock must not make the edit unsaveable, and moving
                        // the created stamp instead would rewrite history the user did not
                        // touch. The domain rule stays the last word either way.
                        updatedAtTimestamp = timestamp.coerceAtLeast(created),
                        exercises = exercises,
                        weightUnit = unit
                    ),
                    // An edit must not write a deleted template back into existence.
                    mustExist = originalTemplate != null
                )
                // Editing is closed while the write is in flight, so what was saved is what
                // is on screen and the draft can go. Editing stays closed (isSaving) while the
                // screen leaves on isSaved.
                clearDraft()
                settle { it.copy(isSaved = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: MissingTemplateException) {
                // No retry can succeed, so this becomes the standing refusal; every field
                // stays on screen.
                originalTemplate = null
                settle { it.copy(isSaving = false, errorMessage = R.string.template_error_missing) }
            } catch (error: Exception) {
                // Every field stays exactly as typed, so the retry is one more tap.
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    errorMessage = R.string.editor_error_save_failed
                )
            }
        }
    }

    /**
     * A change the user made. Ignored while a save is in flight: the write already holds a
     * snapshot, and accepting an edit it cannot include would leave the two disagreeing.
     * The screen disables its inputs to match.
     */
    private fun edit(transform: (TemplateEditorUiState) -> TemplateEditorUiState) {
        if (mutableState.value.isSaving) return
        update { state ->
            // Any change the user makes answers a refused unit tap.
            val next = transform(state).copy(unitRefusal = null)
            // A legacy draft whose loads are all gone takes the profile unit, so a load
            // typed next is labelled, never mistaken for a stored one ([withUnitIfUnweighed]).
            next.withUnitIfUnweighed(profile?.preferredUnit)
        }
    }

    /**
     * The one rule for a draft without a unit: with nothing weighed there is nothing to
     * misread, so it takes [unit]; with loads it stays unknown until the user says which.
     */
    private fun TemplateEditorUiState.withUnitIfUnweighed(unit: WeightUnit?): TemplateEditorUiState =
        if (weightUnit == null && !hasLoads) copy(weightUnit = unit) else this

    private fun update(transform: (TemplateEditorUiState) -> TemplateEditorUiState) {
        settle(transform)
        persistDraft(mutableState.value)
    }

    /**
     * The refusal still in force whatever the user just typed. Clearing `errorMessage` on
     * every edit is right for a failed save and wrong for these three: the condition
     * outlasts the edit, nothing else on screen carries it, and the copy explaining it asks
     * for the very edit that would wipe it. A row the catalog cannot resolve is missing
     * from this list on purpose — its own row already says so and keeps saying so.
     */
    private fun TemplateEditorUiState.standingRefusal(): Int? = when {
        hasLoadFailed -> R.string.editor_error_load_failed
        templateId != null && originalTemplate == null && !isLoading ->
            R.string.template_error_missing
        isDraftIncomplete -> R.string.editor_error_draft_incomplete
        else -> null
    }

    /**
     * The rows this catalog cannot resolve: the exercise is gone, or it is still there
     * under a different type. `WorkoutTemplateRepository` enforces both, and a row that
     * fails there surfaces as a retryable write error no retry can satisfy.
     */
    private fun TemplateEditorUiState.unresolvedRows(
        catalogById: Map<String, Exercise>
    ): Set<String> = selectedExercises
        .filter { catalogById[it.exerciseId]?.type != it.exerciseType }
        .map { it.rowId }
        .toSet()

    /** Applies a load outcome or screen chrome, recomputing dirty state without writing a draft. */
    private fun settle(transform: (TemplateEditorUiState) -> TemplateEditorUiState) {
        val next = transform(mutableState.value)
        mutableState.value = next.copy(
            isDirty = isDirty(next),
            // Tied to the sheet it asks about: once that sheet is applied, cancelled or
            // removed, or no longer differs from its row, the question is gone too.
            showSheetDiscardConfirmation = next.showSheetDiscardConfirmation && next.isSheetModified,
            errorMessage = next.errorMessage ?: next.standingRefusal()
        )
    }

    private fun isDirty(state: TemplateEditorUiState): Boolean {
        if (state.isSheetModified) return true
        // A restored draft stays unsaved work until it can be compared against a real saved
        // baseline. When none ever arrives — the template was deleted, or the load failed —
        // the placeholder would call an emptied draft unchanged and back would pop it with
        // no confirmation.
        if (!baselineLoaded && restoredDraft != null) return true
        return !isUnchanged(state)
    }

    private fun isUnchanged(state: TemplateEditorUiState): Boolean =
        state.name.trim() == baseline.name.trim() &&
            state.notes.trim() == baseline.notes.trim() &&
            state.weightUnit == baseline.weightUnit &&
            state.selectedExercises.map { it.comparable() } == baseline.comparableRows

    /** The row list last written to saved state, so typing in the sheet does not re-encode it. */
    private var persistedRows: List<TemplateExerciseDraft>? = null

    private fun persistDraft(state: TemplateEditorUiState) {
        savedStateHandle[STATE_HAS_DRAFT] = true
        savedStateHandle[STATE_NAME] = state.name
        savedStateHandle[STATE_NOTES] = state.notes
        // Rows change by replacing the list, so the same list means the same encoding; up to
        // fifty rows of long notes are rebuilt only when one of them changed.
        if (state.selectedExercises !== persistedRows || !savedStateHandle.contains(STATE_ROWS)) {
            savedStateHandle[STATE_ROWS] = ArrayList(state.selectedExercises.map { it.encode() })
            persistedRows = state.selectedExercises
        }
        savedStateHandle[STATE_ROWS_DROPPED] = state.isDraftIncomplete
        savedStateHandle[STATE_UNIT] = state.weightUnit?.name
        if (templateId == null) savedStateHandle[STATE_TEMPLATE_ID] = saveId
        savedStateHandle[STATE_EDITING_ROW] = state.editorDraft?.encode()
    }

    private fun clearDraft() {
        persistedRows = null
        listOf(
            STATE_HAS_DRAFT,
            STATE_NAME,
            STATE_NOTES,
            STATE_ROWS,
            STATE_EDITING_ROW,
            STATE_ROWS_DROPPED,
            STATE_UNIT,
            STATE_TEMPLATE_ID
        ).forEach { savedStateHandle.remove<Any>(it) }
    }

    private data class EditorBaseline(
        val name: String,
        val notes: String,
        val rows: List<TemplateExerciseDraft>,
        val weightUnit: WeightUnit?
    ) {
        /**
         * Row identity is regenerated whenever a template is reloaded, so it is what keeps
         * two rows of the same exercise apart on screen and no part of "has this changed?".
         */
        val comparableRows: List<TemplateExerciseDraft> = rows.map { it.comparable() }
    }

    private class RestoredDraft(
        val name: String,
        val notes: String,
        val rows: List<TemplateExerciseDraft>,
        val editorDraft: TemplateExerciseDraft?,
        val weightUnit: WeightUnit?,
        /** Some saved row could not be read back — for instance, written by another build. */
        val rowsWereDropped: Boolean
    )

    companion object {
        private const val MAX_QUERY_LENGTH = 100

        private const val STATE_HAS_DRAFT = "templateEditor.hasDraft"
        private const val STATE_NAME = "templateEditor.name"
        private const val STATE_NOTES = "templateEditor.notes"
        private const val STATE_ROWS = "templateEditor.rows"
        private const val STATE_EDITING_ROW = "templateEditor.editingRow"
        private const val STATE_ROWS_DROPPED = "templateEditor.rowsDropped"
        private const val STATE_UNIT = "templateEditor.weightUnit"
        private const val STATE_TEMPLATE_ID = "templateEditor.templateId"

        private fun restoreDraft(savedStateHandle: SavedStateHandle): RestoredDraft? {
            if (savedStateHandle.get<Boolean>(STATE_HAS_DRAFT) != true) return null
            val encodedRows = savedStateHandle.get<ArrayList<String>>(STATE_ROWS).orEmpty()
            // Two rows with one identity would be keyed, edited and removed as one, so a
            // repeated id keeps its first row only and the shortfall marks the draft incomplete.
            val rows = encodedRows.mapNotNull(TemplateExerciseDraft::decode).distinctBy { it.rowId }
            val unitName = savedStateHandle.get<String>(STATE_UNIT)
            val encodedSheet = savedStateHandle.get<String>(STATE_EDITING_ROW)
            val sheet = encodedSheet?.let(TemplateExerciseDraft::decode)
            val unit = unitName?.let { enumOrNull<WeightUnit>(it) }
            // A sheet only ever edits a row already in the list. One naming no such row, or
            // another exercise than its row, would drop the typing or swap the exercise on
            // Apply, so it is treated like an unreadable one.
            val sheetMatchesItsRow = sheet == null || rows.any {
                it.rowId == sheet.rowId &&
                    it.exerciseId == sheet.exerciseId &&
                    it.exerciseType == sheet.exerciseType
            }
            return RestoredDraft(
                name = savedStateHandle.get<String>(STATE_NAME).orEmpty(),
                notes = savedStateHandle.get<String>(STATE_NOTES).orEmpty(),
                rows = rows,
                editorDraft = sheet,
                weightUnit = unit,
                // Either this payload is short, or an earlier instance already found the
                // same problem and has since overwritten the evidence with a shorter list.
                // An unreadable unit cannot be guessed at either: the draft's numbers would
                // mean something else, so it is treated like an unreadable row.
                rowsWereDropped = rows.size != encodedRows.size ||
                    (unitName != null && unit == null) ||
                    (encodedSheet != null && sheet == null) ||
                    !sheetMatchesItsRow ||
                    savedStateHandle.get<Boolean>(STATE_ROWS_DROPPED) == true
            )
        }

        fun provideFactory(
            templateId: String?,
            templateRepository: WorkoutTemplateRepository,
            userProfileRepository: UserProfileRepository,
            exerciseCatalog: ExerciseCatalog,
            localizationSource: ExerciseLocalizationSource? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TemplateEditorViewModel(
                    templateId = templateId,
                    templateRepository = templateRepository,
                    userProfileRepository = userProfileRepository,
                    exerciseCatalog = exerciseCatalog,
                    localizationSource = localizationSource,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}

/**
 * [take], without leaving half of a surrogate pair at the cut: an emoji straddling the
 * limit is dropped whole rather than stored as a lone surrogate.
 */
internal fun String.takeWhole(length: Int): String =
    take(length).let { if (it.length == length && it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
