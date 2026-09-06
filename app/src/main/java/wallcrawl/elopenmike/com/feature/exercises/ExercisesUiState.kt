package wallcrawl.elopenmike.com.feature.exercises

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.Exercise

sealed interface ExercisesUiState {
    data object Loading : ExercisesUiState

    data class Success(
        val exercises: List<Exercise>,
        val query: String = "",
        val selectedMuscle: String? = null,
        val selectedEquipment: String? = null,
        val availableMuscles: List<String> = emptyList(),
        val availableEquipment: List<String> = emptyList(),
        val selectedExerciseDetail: Exercise? = null
    ) : ExercisesUiState

    data class Error(
        @StringRes val messageRes: Int = R.string.exercises_catalog_error
    ) : ExercisesUiState
}
