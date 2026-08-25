package wallcrawl.elopenmike.com.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanner
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodayViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val workoutGenerationContextBuilder: WorkoutGenerationContextBuilder,
    private val workoutPlanner: WorkoutPlanner,
    private val workoutValidator: GeneratedWorkoutValidator,
    private val nowTimestamp: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val generatedWorkoutFlow = MutableStateFlow<GeneratedWorkout?>(null)
    private val isRegeneratingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private var generationJob: Job? = null
    private var hasPendingRegeneration = false

    private val sourceStateFlow = combine(
        userProfileRepository.getUserProfile(),
        workoutRepository.observeActiveSession(),
        workoutRepository.observeCompletedSessions()
    ) { profile, activeSession, completedSessions ->
        TodaySourceState(
            userProfile = profile,
            activeSession = activeSession,
            completedThisWeek = completedSessions.count { session ->
                val completedAt = session.completedAtTimestamp
                val age = completedAt?.let { nowTimestamp() - it }
                session.status == SessionStatus.COMPLETED && age != null && age in 0 until WEEK_MILLIS
            }
        )
    }

    val uiState: StateFlow<TodayUiState> = combine(
        sourceStateFlow,
        generatedWorkoutFlow,
        isRegeneratingFlow,
        errorFlow
    ) { sourceState, generatedWorkout, isRegenerating, error ->
        if (error != null) {
            TodayUiState.Error(error)
        } else if (generatedWorkout == null) {
            TodayUiState.Loading
        } else {
            TodayUiState.Success(
                userProfile = sourceState.userProfile,
                suggestedWorkout = generatedWorkout,
                activeSession = sourceState.activeSession,
                isRegenerating = isRegenerating,
                completedThisWeek = sourceState.completedThisWeek
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState.Loading
    )

    init {
        viewModelScope.launch {
            userProfileRepository.getUserProfile()
                .distinctUntilChanged()
                .drop(1)
                .collect { requestWorkoutGeneration(isRegeneration = true) }
        }
        requestWorkoutGeneration(isRegeneration = false)
    }

    fun regenerateWorkout() {
        requestWorkoutGeneration(isRegeneration = true)
    }

    private fun requestWorkoutGeneration(isRegeneration: Boolean) {
        if (generationJob?.isActive == true) {
            hasPendingRegeneration = hasPendingRegeneration || isRegeneration
            return
        }

        generationJob = viewModelScope.launch {
            var currentRequestIsRegeneration = isRegeneration
            do {
                hasPendingRegeneration = false
                isRegeneratingFlow.value = currentRequestIsRegeneration
                try {
                    val newWorkout = buildAndValidateWorkout()
                    generatedWorkoutFlow.value = newWorkout
                    errorFlow.value = null
                } catch (e: Exception) {
                    errorFlow.value = e.message ?: if (currentRequestIsRegeneration) {
                        "Failed to regenerate workout."
                    } else {
                        "Failed to generate workout recommendation."
                    }
                } finally {
                    isRegeneratingFlow.value = false
                }
                currentRequestIsRegeneration = true
            } while (hasPendingRegeneration)
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

    private suspend fun buildAndValidateWorkout(): GeneratedWorkout {
        val context = workoutGenerationContextBuilder.build()
        val generated = workoutPlanner.generateWorkout(context)
        val allowedIds = context.allowedExercises.map { it.id }.toSet()
        return workoutValidator.validate(generated, allowedIds)
    }

    companion object {
        private const val WEEK_MILLIS = 7 * 24 * 60 * 60 * 1_000L

        fun provideFactory(
            userProfileRepository: UserProfileRepository,
            workoutRepository: WorkoutRepository,
            workoutGenerationContextBuilder: WorkoutGenerationContextBuilder,
            workoutPlanner: WorkoutPlanner,
            workoutValidator: GeneratedWorkoutValidator
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TodayViewModel(
                    userProfileRepository,
                    workoutRepository,
                    workoutGenerationContextBuilder,
                    workoutPlanner,
                    workoutValidator
                ) as T
            }
        }
    }

    private data class TodaySourceState(
        val userProfile: UserProfile,
        val activeSession: WorkoutSession?,
        val completedThisWeek: Int
    )
}
