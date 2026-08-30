package wallcrawl.elopenmike.com.feature.workout

import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary

/** What finishing the workout right now would mean. */
sealed interface FinishDecision {
    /** Every planned set is resolved: finishing needs no further confirmation. */
    data object Complete : FinishDecision

    /** Sets are still open; the user is told how many before anything is persisted. */
    data class ConfirmIncomplete(val openSetCount: Int) : FinishDecision
}

/** Rest-timer state plus the remaining seconds derived from its deadline. */
data class RestTimerUiState(
    val state: RestTimerState = RestTimerState.Idle,
    val remainingSeconds: Int = 0
) {
    val isRunning: Boolean get() = state is RestTimerState.Running
    val isVisible: Boolean get() = state !is RestTimerState.Idle

    companion object {
        val Idle = RestTimerUiState()
    }
}

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
        val setUpdateError: String? = null,
        val restTimer: RestTimerUiState = RestTimerUiState.Idle,
        /** Non-null while the user is being asked to confirm finishing with open sets. */
        val pendingFinish: FinishDecision.ConfirmIncomplete? = null,
        /** True while the user is being asked to confirm discarding this workout. */
        val isConfirmingDiscard: Boolean = false
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

/**
 * Sets that are neither completed nor deliberately skipped or stopped.
 *
 * A skipped set is a resolved decision, so it never triggers the finish confirmation --
 * but it also never counts as completed work.
 */
internal fun WorkoutSession.openSetCount(): Int =
    exercises.sumOf { exercise -> exercise.sets.count { !it.isResolved } }

internal fun WorkoutSession.finishDecision(): FinishDecision {
    val open = openSetCount()
    return if (open == 0) FinishDecision.Complete else FinishDecision.ConfirmIncomplete(open)
}
