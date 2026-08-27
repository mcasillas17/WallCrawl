package wallcrawl.elopenmike.com.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import wallcrawl.elopenmike.com.core.ai.DefaultExercisePrescriptionFactory
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
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
    val catalogExercises: List<Exercise> = emptyList(),
    val selectedExercises: List<PlannedExercise> = emptyList(),
    val availableEquipment: Set<String> = emptySet(),
    val isPickerOpen: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val filteredExercises: List<Exercise>
        get() {
            val normalized = query.trim()
            return catalogExercises.filter { exercise ->
                normalized.isEmpty() || sequenceOf(exercise.id, exercise.name)
                    .plus(exercise.searchAliases)
                    .plus(exercise.primaryMuscles)
                    .plus(exercise.secondaryMuscles)
                    .plus(exercise.listedEquipment)
                    .any {
                        it.contains(normalized, ignoreCase = true)
                    }
            }
        }
}

class TemplateEditorViewModel(
    private val templateId: String?,
    private val templateRepository: WorkoutTemplateRepository,
    private val userProfileRepository: UserProfileRepository,
    private val exerciseCatalog: ExerciseCatalog,
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
                if (templateId != null && template == null) error("Workout template was not found.")
                profile = loadedProfile
                originalTemplate = template
                mutableState.value = TemplateEditorUiState(
                    isLoading = false,
                    templateId = templateId,
                    name = template?.name.orEmpty(),
                    notes = template?.notes.orEmpty(),
                    catalogExercises = exercises,
                    selectedExercises = template?.exercises.orEmpty(),
                    availableEquipment = loadedProfile.availableEquipment.toSet()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Unable to load the workout editor."
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
            mutableState.value = state.copy(errorMessage = "Give this workout a name.")
            return
        }
        if (state.selectedExercises.isEmpty()) {
            mutableState.value = state.copy(errorMessage = "Add at least one exercise.")
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
                    errorMessage = error.message ?: "Unable to save this workout."
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
            exerciseCatalog: ExerciseCatalog
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TemplateEditorViewModel(
                    templateId,
                    templateRepository,
                    userProfileRepository,
                    exerciseCatalog
                ) as T
        }
    }
}
