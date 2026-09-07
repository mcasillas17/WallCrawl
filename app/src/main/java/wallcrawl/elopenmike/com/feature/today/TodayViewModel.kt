package wallcrawl.elopenmike.com.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanner
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanningFailure
import wallcrawl.elopenmike.com.core.ai.WorkoutValidationException
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.TrainingWeek
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
import kotlinx.coroutines.flow.map
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
    clock: Flow<Long> = minuteClock(nowTimestamp),
    zoneId: () -> ZoneId = ZoneId::systemDefault
) : ViewModel() {

    private val generatedWorkoutFlow = MutableStateFlow<GeneratedWorkout?>(null)
    private val generatedForProfileFlow = MutableStateFlow<UserProfile?>(null)
    private val isRegeneratingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<TodayError?>(null)
    private var generationJob: Job? = null
    private var hasPendingRegeneration = false

    private val completedThisWeekFlow = clock
        .map { TrainingWeek.containing(Instant.ofEpochMilli(it), zoneId()) }
        .distinctUntilChanged()
        .flatMapLatest { week ->
            workoutRepository.observeCompletedWorkoutCountInRange(
                startTimestamp = week.startEpochMillis,
                endTimestampExclusive = week.endEpochMillisExclusive
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
            TodayUiState.Error(error = error, activeSession = sourceState.activeSession)
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
        viewModelScope.launch {
            // Finishing a workout advances the split. This screen outlives the trip to the
            // workout and back, so without this the card still offers the day just finished.
            workoutRepository.observeCompletedWorkoutCount()
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
                    errorFlow.value = userFacingError(e, currentRequestIsRegeneration)
                } finally {
                    isRegeneratingFlow.value = false
                }
                currentRequestIsRegeneration = true
            } while (hasPendingRegeneration)
        }
    }

    /**
     * Turns a planning failure into a typed reason for the Today error card.
     *
     * Exception messages are written for logs, so they are mapped here rather than rendered:
     * the planner should not have to phrase user-facing text in any language, and "no
     * allowed candidate exercises available" is not something to show a person
     * mid-workout-planning.
     */
    private fun userFacingError(error: Exception, isRegeneration: Boolean): TodayError {
        val planningError = error as? WorkoutValidationException
        return when (planningError?.failure) {
            WorkoutPlanningFailure.NO_CANDIDATES -> TodayError.NO_CANDIDATES

            WorkoutPlanningFailure.REVIEWED_ELIGIBILITY_NO_CANDIDATES ->
                automaticEligibilityError(planningError.automaticEligibilityFailure)

            WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES -> TodayError.NO_STRENGTH_CANDIDATES

            WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT ->
                TodayError.NO_CANDIDATES_FOR_ANY_SPLIT

            WorkoutPlanningFailure.INVALID_GENERATED_WORKOUT, null ->
                if (isRegeneration) {
                    TodayError.REGENERATION_FAILED
                } else {
                    TodayError.FIRST_GENERATION_FAILED
                }
        }
    }

    /**
     * Starts the suggested workout.
     *
     * [displayName] and [displayRationale] arrive already written by the screen, which is
     * the only layer that knows what language the reader chose. They are what the session
     * keeps: a workout started in Spanish stays named in Spanish in the history, exactly as
     * it was seen when it was started.
     */
    fun startWorkout(
        displayName: String,
        displayRationale: String,
        onWorkoutStarted: (sessionId: String) -> Unit
    ) {
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
                    displayName = displayName,
                    displayRationale = displayRationale,
                    userProfile = currentContext.userProfile
                )
                onWorkoutStarted(session.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorFlow.value = TodayError.START_FAILED
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

internal fun automaticEligibilityError(failure: AutomaticEligibilityFailure?): TodayError =
    when (failure) {
        AutomaticEligibilityFailure.NO_APPROVED_METADATA ->
            TodayError.REVIEWED_NO_APPROVED_METADATA

        AutomaticEligibilityFailure.USER_EXCLUSIONS_REMOVED_ALL ->
            TodayError.REVIEWED_EXCLUSIONS_REMOVED_ALL

        AutomaticEligibilityFailure.EQUIPMENT_REMOVED_ALL ->
            TodayError.REVIEWED_EQUIPMENT_REMOVED_ALL

        AutomaticEligibilityFailure.CAPABILITIES_REMOVED_ALL ->
            TodayError.REVIEWED_CAPABILITIES_REMOVED_ALL

        AutomaticEligibilityFailure.TRAINING_CONSTRAINTS_REMOVED_ALL ->
            TodayError.REVIEWED_CONSTRAINTS_REMOVED_ALL

        AutomaticEligibilityFailure.CALIBRATION_COMPLEXITY_REMOVED_ALL ->
            TodayError.REVIEWED_CALIBRATION_REMOVED_ALL

        AutomaticEligibilityFailure.NO_ELIGIBLE_CANDIDATES, null ->
            TodayError.REVIEWED_NONE_ELIGIBLE
    }

private fun minuteClock(nowTimestamp: () -> Long): Flow<Long> = flow {
    while (true) {
        emit(nowTimestamp())
        delay(MINUTE_MILLIS)
    }
}

private const val MINUTE_MILLIS = 60_000L
