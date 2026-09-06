package wallcrawl.elopenmike.com.feature.templates

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.R
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wallcrawl.elopenmike.com.core.ai.DefaultExercisePrescriptionFactory
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseSearchIndex
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalization
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationSource
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

data class TemplateEditorUiState(
    val isLoading: Boolean = true,
    val templateId: String? = null,
    val name: String = "",
    val notes: String = "",
    val query: String = "",
    val selectedExercises: List<PlannedExercise> = emptyList(),
    val availableEquipment: Set<String> = emptySet(),
    val isPickerOpen: Boolean = false,
    val isSaving: Boolean = false,
    @StringRes val errorMessage: Int? = null,
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
     * Resolved once per state, not once per read: the picker reads it three times in a
     * single composition.
     */
    val filteredExercises: List<Exercise> = searchIndex.matching(query)
}

class TemplateEditorViewModel(
    private val templateId: String?,
    private val templateRepository: WorkoutTemplateRepository,
    private val userProfileRepository: UserProfileRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val localizationSource: ExerciseLocalizationSource? = null,
    private val prescriptionFactory: DefaultExercisePrescriptionFactory =
        DefaultExercisePrescriptionFactory(),
    private val nowTimestamp: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val mutableState = MutableStateFlow(TemplateEditorUiState(templateId = templateId))
    val uiState: StateFlow<TemplateEditorUiState> = mutableState.asStateFlow()
    private var originalTemplate: WorkoutTemplate? = null
    private var profile: UserProfile? = null

    init {
        viewModelScope.launch {
            try {
                val loadedProfile = userProfileRepository.getProfileOnce()
                val exercises = exerciseCatalog.getAllExercises().first()
                val template = templateId?.let { templateRepository.getTemplate(it) }
                if (templateId != null && template == null) {
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        errorMessage = R.string.template_error_missing
                    )
                    return@launch
                }
                profile = loadedProfile
                originalTemplate = template
                val localization =
                    localizationSource?.localization() ?: ExerciseLocalization.EMPTY
                mutableState.value = TemplateEditorUiState(
                    isLoading = false,
                    templateId = templateId,
                    name = template?.name.orEmpty(),
                    notes = template?.notes.orEmpty(),
                    selectedExercises = template?.exercises.orEmpty(),
                    availableEquipment = loadedProfile.availableEquipment.toSet(),
                    // Folding every catalog and overlay term is the one expensive step
                    // here, so it does not run on the thread drawing the loading state.
                    searchIndex = withContext(Dispatchers.Default) {
                        ExerciseSearchIndex(exercises, localization)
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    errorMessage = R.string.editor_error_load_failed
                )
            }
        }
    }

    fun updateName(value: String) {
        mutableState.value = mutableState.value.copy(name = value.take(120), errorMessage = null)
    }

    fun updateNotes(value: String) {
        mutableState.value = mutableState.value.copy(notes = value.take(2_000), errorMessage = null)
    }

    fun updateQuery(value: String) {
        mutableState.value = mutableState.value.copy(query = value.take(100))
    }

    fun openPicker() {
        mutableState.value = mutableState.value.copy(isPickerOpen = true, query = "")
    }

    fun closePicker() {
        mutableState.value = mutableState.value.copy(isPickerOpen = false, query = "")
    }

    fun addExercise(exercise: Exercise) {
        val loadedProfile = profile ?: return
        val planned = PlannedExercise(
            exerciseId = exercise.id,
            prescription = prescriptionFactory.create(
                exercise,
                WorkoutGenerationContext(userProfile = loadedProfile)
            )
        )
        mutableState.value = mutableState.value.copy(
            selectedExercises = mutableState.value.selectedExercises + planned,
            isPickerOpen = false,
            query = ""
        )
    }

    fun removeExercise(index: Int) = updateSelected { exercises ->
        exercises.filterIndexed { current, _ -> current != index }
    }

    fun moveExercise(index: Int, direction: Int) = updateSelected { exercises ->
        val destination = index + direction
        if (index !in exercises.indices || destination !in exercises.indices) return@updateSelected exercises
        exercises.toMutableList().apply {
            val moved = removeAt(index)
            add(destination, moved)
        }
    }

    fun changeSetCount(index: Int, delta: Int) = updateSelected { exercises ->
        exercises.mapIndexed { current, exercise ->
            if (current != index) exercise else exercise.copy(
                prescription = exercise.prescription.copy(
                    targetSets = (exercise.prescription.targetSets + delta).coerceIn(1, 20)
                )
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = mutableState.value
        if (state.isSaving) return
        if (state.name.isBlank()) {
            mutableState.value = state.copy(errorMessage = R.string.editor_error_name_required)
            return
        }
        if (state.selectedExercises.isEmpty()) {
            mutableState.value = state.copy(errorMessage = R.string.editor_error_exercise_required)
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, errorMessage = null)
            try {
                val timestamp = nowTimestamp()
                templateRepository.saveTemplate(
                    WorkoutTemplate(
                        id = originalTemplate?.id ?: UUID.randomUUID().toString(),
                        name = state.name.trim(),
                        notes = state.notes.trim(),
                        createdAtTimestamp = originalTemplate?.createdAtTimestamp ?: timestamp,
                        updatedAtTimestamp = timestamp,
                        exercises = state.selectedExercises
                    )
                )
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    errorMessage = R.string.editor_error_save_failed
                )
            }
        }
    }

    private fun updateSelected(transform: (List<PlannedExercise>) -> List<PlannedExercise>) {
        mutableState.value = mutableState.value.copy(
            selectedExercises = transform(mutableState.value.selectedExercises),
            errorMessage = null
        )
    }

    companion object {
        fun provideFactory(
            templateId: String?,
            templateRepository: WorkoutTemplateRepository,
            userProfileRepository: UserProfileRepository,
            exerciseCatalog: ExerciseCatalog,
            localizationSource: ExerciseLocalizationSource? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TemplateEditorViewModel(
                    templateId,
                    templateRepository,
                    userProfileRepository,
                    exerciseCatalog,
                    localizationSource
                ) as T
        }
    }
}
