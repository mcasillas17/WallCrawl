package wallcrawl.elopenmike.com.feature.workout

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary

/** The user stopped a set for pain; [exerciseId] may be left out of automatic plans if they agree. */
data class PainExclusionOffer(val exerciseId: String)

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
        @StringRes val setUpdateError: Int? = null,
        val restTimer: RestTimerUiState = RestTimerUiState.Idle,
        /** Non-null while the user is being asked to confirm finishing with open sets. */
        val pendingFinish: FinishDecision.ConfirmIncomplete? = null,
        /** True while the user is being asked to confirm discarding this workout. */
        val isConfirmingDiscard: Boolean = false,
        val programAssociation: wallcrawl.elopenmike.com.core.model.ProgramHistoryAssociation? = null,
        @StringRes val programReadError: Int? = null,
        val programFinishUnsupported: Boolean = false,
        /** Display only: cues for exercises with a modify demand or a routine cue. */
        val restrictions: wallcrawl.elopenmike.com.core.conditions.ConditionRestrictions =
            wallcrawl.elopenmike.com.core.conditions.ConditionRestrictions.None,
        val painExclusionOffer: PainExclusionOffer? = null
    ) : ActiveWorkoutUiState {
        val currentExercise: WorkoutExercise?
            get() = session.exercises.getOrNull(currentExerciseIndex)

        val isLastExercise: Boolean
            get() = currentExerciseIndex >= session.exercises.size - 1

        val isFirstExercise: Boolean
            get() = currentExerciseIndex <= 0

        val totalExercises: Int
            get() = session.exercises.size

        /** 1-based position among rows of the current row's role, and how many such rows exist. */
        val positionInRole: Pair<Int, Int>
            get() {
                val role = currentExercise?.role ?: return currentExerciseIndex + 1 to totalExercises
                val sameRole = session.exercises.withIndex().filter { it.value.role == role }
                return (sameRole.indexOfFirst { it.index == currentExerciseIndex } + 1) to sameRole.size
            }
    }

    data class Completed(
        val summary: WorkoutSummary
    ) : ActiveWorkoutUiState

    data class Error(@StringRes val messageRes: Int) : ActiveWorkoutUiState
}

/**
 * Sets that are neither completed nor deliberately skipped or stopped.
 *
 * A skipped set is a resolved decision, so it never triggers the finish confirmation --
 * but it also never counts as completed work.
 */
internal fun WorkoutSession.openSetCount(): Int =
    // Mobility rows are optional general information; leaving them open never blocks finishing.
    exercises.filter { it.role == wallcrawl.elopenmike.com.core.model.ExerciseRole.MAIN }.sumOf { exercise -> exercise.sets.count { !it.isResolved } }

internal fun WorkoutSession.finishDecision(): FinishDecision {
    val open = openSetCount()
    return if (open == 0) FinishDecision.Complete else FinishDecision.ConfirmIncomplete(open)
}
