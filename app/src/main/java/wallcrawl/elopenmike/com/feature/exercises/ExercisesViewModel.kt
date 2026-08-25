package wallcrawl.elopenmike.com.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ExercisesViewModel(
    private val exerciseCatalog: ExerciseCatalog
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val selectedMuscleFlow = MutableStateFlow<String?>(null)
    private val selectedEquipmentFlow = MutableStateFlow<String?>(null)
    private val selectedExerciseDetailFlow = MutableStateFlow<Exercise?>(null)

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

    val uiState: StateFlow<ExercisesUiState> = combine(
        searchResultsFlow,
        queryFlow,
        selectedMuscleFlow,
        selectedEquipmentFlow,
        selectedExerciseDetailFlow
    ) { exercises, query, muscle, equipment, detail ->
        val muscles = exerciseCatalog.getMuscleGroups()
        val eqTypes = exerciseCatalog.getEquipmentTypes()

        ExercisesUiState.Success(
            exercises = exercises,
            query = query,
            selectedMuscle = muscle,
            selectedEquipment = equipment,
            availableMuscles = muscles,
            availableEquipment = eqTypes,
            selectedExerciseDetail = detail
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExercisesUiState.Loading
    )

    fun onQueryChanged(newQuery: String) {
        queryFlow.value = newQuery
    }

    fun selectMuscle(muscle: String?) {
        selectedMuscleFlow.value = if (selectedMuscleFlow.value == muscle) null else muscle
    }

    fun selectEquipment(equipment: String?) {
        selectedEquipmentFlow.value = if (selectedEquipmentFlow.value == equipment) null else equipment
    }

    fun openExerciseDetail(exercise: Exercise) {
        selectedExerciseDetailFlow.value = exercise
    }

    fun closeExerciseDetail() {
        selectedExerciseDetailFlow.value = null
    }

    companion object {
        fun provideFactory(
            exerciseCatalog: ExerciseCatalog
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ExercisesViewModel(exerciseCatalog) as T
            }
        }
    }
}
