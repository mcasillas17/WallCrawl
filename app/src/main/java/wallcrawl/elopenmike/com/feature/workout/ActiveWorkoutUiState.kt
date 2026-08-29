package wallcrawl.elopenmike.com.feature.workout

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary

sealed interface ActiveWorkoutUiState {
    data object Loading : ActiveWorkoutUiState

    data class Active(
        val session: WorkoutSession,
        val currentExerciseIndex: Int = 0,
        val currentCatalogExercise: Exercise? = null,
        val weightUnit: WeightUnit = WeightUnit.LBS,
        val isSaving: Boolean = false,
        val previousSets: List<WorkoutSet> = emptyList(),
        val previousSessionTimestamp: Long? = null,
        val previousWeightUnit: WeightUnit = WeightUnit.LBS,
        // A rejected set update (e.g. a transient invalid edit slipping through, or an
        // explicit but invalid completion attempt) is recoverable: it never replaces the
        // active workout, only surfaces here to be dismissed or cleared by the next
        // successful update.
        val setUpdateError: String? = null
    ) : ActiveWorkoutUiState {
        val currentExercise: WorkoutExercise?
            get() = session.exercises.getOrNull(currentExerciseIndex)

        val isLastExercise: Boolean
            get() = currentExerciseIndex >= session.exercises.size - 1

        val isFirstExercise: Boolean
            get() = currentExerciseIndex <= 0

        val totalExercises: Int
            get() = session.exercises.size
    }

    data class Completed(
        val summary: WorkoutSummary
    ) : ActiveWorkoutUiState

    data class Error(val message: String) : ActiveWorkoutUiState
}
