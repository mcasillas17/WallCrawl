package wallcrawl.elopenmike.com.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanner
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodayViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseCatalog: ExerciseCatalog,
    private val exerciseFilter: ExerciseFilter,
    private val workoutPlanner: WorkoutPlanner,
    private val workoutValidator: GeneratedWorkoutValidator
) : ViewModel() {

    private val generatedWorkoutFlow = MutableStateFlow<GeneratedWorkout?>(null)
    private val isRegeneratingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TodayUiState> = combine(
        userProfileRepository.getUserProfile(),
        workoutRepository.observeActiveSession(),
        generatedWorkoutFlow,
        isRegeneratingFlow,
        errorFlow
    ) { profile, activeSession, generatedWorkout, isRegenerating, error ->
        if (error != null) {
            TodayUiState.Error(error)
        } else if (generatedWorkout == null) {
            // Trigger first generation
            generateInitialWorkout(profile)
            TodayUiState.Loading
        } else {
            TodayUiState.Success(
                userProfile = profile,
                suggestedWorkout = generatedWorkout,
                activeSession = activeSession,
                isRegenerating = isRegenerating,
                completedThisWeek = 3
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState.Loading
    )

    private fun generateInitialWorkout(profile: UserProfile) {
        viewModelScope.launch {
            try {
                val workout = buildAndValidateWorkout(profile)
                generatedWorkoutFlow.value = workout
            } catch (e: Exception) {
                errorFlow.value = e.message ?: "Failed to generate workout recommendation."
            }
        }
    }

    fun regenerateWorkout() {
        viewModelScope.launch {
            isRegeneratingFlow.value = true
            try {
                val profile = userProfileRepository.getProfileOnce()
                val newWorkout = buildAndValidateWorkout(profile)
                generatedWorkoutFlow.value = newWorkout
                errorFlow.value = null
            } catch (e: Exception) {
                errorFlow.value = e.message ?: "Failed to regenerate workout."
            } finally {
                isRegeneratingFlow.value = false
            }
        }
    }

    fun startWorkout(onWorkoutStarted: (sessionId: String) -> Unit) {
        viewModelScope.launch {
            val currentWorkout = generatedWorkoutFlow.value ?: return@launch
            try {
                val session = workoutRepository.startWorkoutFromGenerated(currentWorkout)
                onWorkoutStarted(session.id)
            } catch (e: Exception) {
                errorFlow.value = "Failed to start workout session: ${e.message}"
            }
        }
    }

    private suspend fun buildAndValidateWorkout(profile: UserProfile): GeneratedWorkout {
        val allExercises = exerciseCatalog.getAllExercises().first()
        val allowedCandidates = exerciseFilter.filterCandidates(
            allExercises = allExercises,
            profile = profile
        )

        val context = WorkoutGenerationContext(
            userProfile = profile,
            availableEquipment = profile.availableEquipment,
            preferredWorkoutDurationMinutes = profile.preferredDurationMinutes,
            musclePriorities = profile.musclePriorities,
            excludedExerciseIds = profile.excludedExerciseIds,
            allowedExercises = allowedCandidates,
            preferredUnits = profile.preferredUnit
        )

        val generated = workoutPlanner.generateWorkout(context)
        val allowedIds = allowedCandidates.map { it.id }.toSet()
        return workoutValidator.validate(generated, allowedIds)
    }

    companion object {
        fun provideFactory(
            userProfileRepository: UserProfileRepository,
            workoutRepository: WorkoutRepository,
            exerciseCatalog: ExerciseCatalog,
            exerciseFilter: ExerciseFilter,
            workoutPlanner: WorkoutPlanner,
            workoutValidator: GeneratedWorkoutValidator
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TodayViewModel(
                    userProfileRepository,
                    workoutRepository,
                    exerciseCatalog,
                    exerciseFilter,
                    workoutPlanner,
                    workoutValidator
                ) as T
            }
        }
    }
}
