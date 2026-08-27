package wallcrawl.elopenmike.com.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanner
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanningFailure
import wallcrawl.elopenmike.com.core.ai.WorkoutValidationException
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val workoutGenerationContextBuilder: WorkoutGenerationContextBuilder,
    private val workoutPlanner: WorkoutPlanner,
    private val workoutValidator: GeneratedWorkoutValidator,
    nowTimestamp: () -> Long = System::currentTimeMillis,
    clock: Flow<Long> = minuteClock(nowTimestamp)
) : ViewModel() {

    private val generatedWorkoutFlow = MutableStateFlow<GeneratedWorkout?>(null)
    private val generatedForProfileFlow = MutableStateFlow<UserProfile?>(null)
    private val isRegeneratingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)
    private var generationJob: Job? = null
    private var hasPendingRegeneration = false

    private val completedThisWeekFlow = clock.flatMapLatest { currentTimestamp ->
        workoutRepository.observeCompletedWorkoutCountSince(
            startTimestamp = currentTimestamp - WEEK_MILLIS
        )
    }

    private val sourceStateFlow = combine(
        userProfileRepository.getUserProfile(),
        workoutRepository.observeActiveSession(),
        completedThisWeekFlow
    ) { profile, activeSession, completedThisWeek ->
        TodaySourceState(
            userProfile = profile,
            activeSession = activeSession,
            completedThisWeek = completedThisWeek
        )
    }

    val uiState: StateFlow<TodayUiState> = combine(
        sourceStateFlow,
        generatedWorkoutFlow,
        isRegeneratingFlow,
        errorFlow
    ) { sourceState, generatedWorkout, isRegenerating, error ->
        if (error != null) {
            TodayUiState.Error(message = error, activeSession = sourceState.activeSession)
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
                    val generatedResult = buildAndValidateWorkout()
                    generatedWorkoutFlow.value = generatedResult.workout
                    generatedForProfileFlow.value = generatedResult.profile
                    errorFlow.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorFlow.value = userFacingMessage(e, currentRequestIsRegeneration)
                } finally {
                    isRegeneratingFlow.value = false
                }
                currentRequestIsRegeneration = true
            } while (hasPendingRegeneration)
        }
    }

    /**
     * Turns a planning failure into copy for the Today error card.
     *
     * Exception messages are written for logs, so they are mapped here rather than rendered:
     * the planner should not have to phrase user-facing text, and "no allowed candidate
     * exercises available" is not something to show a person mid-workout-planning.
     */
    private fun userFacingMessage(error: Exception, isRegeneration: Boolean): String =
        when ((error as? WorkoutValidationException)?.failure) {
            WorkoutPlanningFailure.NO_CANDIDATES ->
                "No exercises match your equipment and exclusions yet. " +
                    "Add equipment or clear an exclusion in Profile."

            WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT ->
                "Your available equipment can't cover a full training day yet. " +
                    "Add equipment in Profile, or start one of your own workouts."

            WorkoutPlanningFailure.INVALID_GENERATED_WORKOUT, null ->
                if (isRegeneration) {
                    "Couldn't build another workout. Try again."
                } else {
                    "Couldn't build today's workout. Try again."
                }
        }

    fun startWorkout(onWorkoutStarted: (sessionId: String) -> Unit) {
        if (generationJob?.isActive == true) return
        viewModelScope.launch {
            val currentWorkout = generatedWorkoutFlow.value ?: return@launch
            try {
                val currentContext = workoutGenerationContextBuilder.build()
                check(generatedForProfileFlow.value == currentContext.userProfile) {
                    "Workout recommendation is being updated for the current profile."
                }
                val allowedIds = currentContext.allowedExercises.map { it.id }.toSet()
                workoutValidator.validate(currentWorkout, allowedIds)
                val session = workoutRepository.startWorkoutFromGenerated(
                    generated = currentWorkout,
                    userProfile = currentContext.userProfile
                )
                onWorkoutStarted(session.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorFlow.value = "Failed to start workout session: ${e.message}"
            }
        }
    }

    private suspend fun buildAndValidateWorkout(): GeneratedWorkoutResult {
        val context = workoutGenerationContextBuilder.build()
        val generated = workoutPlanner.generateWorkout(context)
        val allowedIds = context.allowedExercises.map { it.id }.toSet()
        return GeneratedWorkoutResult(
            workout = workoutValidator.validate(generated, allowedIds),
            profile = context.userProfile
        )
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

    private data class GeneratedWorkoutResult(
        val workout: GeneratedWorkout,
        val profile: UserProfile
    )
}

private fun minuteClock(nowTimestamp: () -> Long): Flow<Long> = flow {
    while (true) {
        emit(nowTimestamp())
        delay(MINUTE_MILLIS)
    }
}

private const val MINUTE_MILLIS = 60_000L
