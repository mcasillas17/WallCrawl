package wallcrawl.elopenmike.com.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class ExercisesViewModel(
    private val exerciseCatalog: ExerciseCatalog,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val queryFlow = savedState.getStateFlow("query", "")
    private val selectedMuscleFlow = savedState.getStateFlow<String?>("muscle", null)
    private val selectedEquipmentFlow = savedState.getStateFlow<String?>("equipment", null)
    private val selectedExerciseDetailFlow = savedState.getStateFlow<String?>("exerciseId", null)
        .mapLatest { id -> id?.let { exerciseCatalog.getExerciseById(it) } }

    private val filterParamsFlow = combine(
        queryFlow,
        selectedMuscleFlow,
        selectedEquipmentFlow
    ) { query, muscle, equipment ->
        Triple(query, muscle, equipment)
    }

    private val searchResultsFlow = filterParamsFlow.flatMapLatest { (query, muscle, equipment) ->
        exerciseCatalog.searchExercises(query = query, muscle = muscle, equipment = equipment)
    }

    private val results = combine(
        searchResultsFlow,
        queryFlow,
        selectedMuscleFlow,
        selectedEquipmentFlow,
        selectedExerciseDetailFlow
    ) { exercises, query, muscle, equipment, detail ->
        val muscles = exerciseCatalog.getMuscleGroups()
        val eqTypes = exerciseCatalog.getEquipmentTypes()

        val state: ExercisesUiState = ExercisesUiState.Success(
            exercises = exercises,
            query = query,
            selectedMuscle = muscle,
            selectedEquipment = equipment,
            availableMuscles = muscles,
            availableEquipment = eqTypes,
            selectedExerciseDetail = detail
        )
        state
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(ExercisesUiState.Error())
    }
    private val loadAttempt = MutableStateFlow(0)
    val uiState: StateFlow<ExercisesUiState> = loadAttempt.flatMapLatest {
        results.onStart { emit(ExercisesUiState.Loading) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExercisesUiState.Loading
    )

    fun retryLoad() { loadAttempt.value++ }

    fun onQueryChanged(newQuery: String) {
        savedState["query"] = newQuery.take(256)
    }

    fun selectMuscle(muscle: String?) {
        savedState["muscle"] = if (selectedMuscleFlow.value == muscle) null else muscle
    }

    fun selectEquipment(equipment: String?) {
        savedState["equipment"] = if (selectedEquipmentFlow.value == equipment) null else equipment
    }

    fun openExerciseDetail(exercise: Exercise) {
        savedState["exerciseId"] = exercise.id
    }

    fun closeExerciseDetail() {
        savedState["exerciseId"] = null
    }

    companion object {
        fun provideFactory(
            exerciseCatalog: ExerciseCatalog
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ExercisesViewModel(exerciseCatalog, createSavedStateHandle()) }
        }
    }
}
