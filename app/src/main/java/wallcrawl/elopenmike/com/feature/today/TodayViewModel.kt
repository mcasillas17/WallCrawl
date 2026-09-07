package wallcrawl.elopenmike.com.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.ai.ProgramValidationResult
import wallcrawl.elopenmike.com.core.ai.ProgramValidator
import wallcrawl.elopenmike.com.core.ai.ProgramViolation
import wallcrawl.elopenmike.com.core.ai.ProgramViolationCode
import wallcrawl.elopenmike.com.core.ai.RecommendationContextIdentity
import wallcrawl.elopenmike.com.core.ai.RecommendationSnapshot
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
    private val programValidator: ProgramValidator,
    nowTimestamp: () -> Long = System::currentTimeMillis,
    clock: Flow<Long> = minuteClock(nowTimestamp)
) : ViewModel() {

    private val generatedWorkoutFlow = MutableStateFlow<GeneratedWorkout?>(null)

    /**
     * The validation evidence for the workout currently on screen.
     *
     * It carries its own context fingerprint, which is what makes "is this still the right
     * plan?" answerable at start. Starting captures this reference alongside the workout and
     * then compares that captured copy, never the field: a regeneration finishing while the
     * start is suspended replaces the field, and answering the freshness question from it
     * would check one recommendation's identity while starting another's plan.
     *
     * A rejected regeneration deliberately leaves the previous value in place, because the
     * plan it belongs to is still the one on screen.
     */
    private var generatedSnapshot: RecommendationSnapshot? = null
    private val isRegeneratingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<TodayError?>(null)
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
                    generateValidatedWorkout(currentRequestIsRegeneration)
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
     * Generates a recommendation and validates the whole proposal before anything is shown.
     *
     * One deterministic repair pass is permitted here and only here. Nothing reaches the
     * screen or the database until whole-program validation has accepted the complete plan
     * against the exact context that produced it.
     */
    private suspend fun generateValidatedWorkout(isRegeneration: Boolean) {
        val context = workoutGenerationContextBuilder.build()
        val generated = workoutPlanner.generateWorkout(context)
        when (
            val result = programValidator.validate(
                workout = generated,
                context = context,
                allowRepair = true
            )
        ) {
            is ProgramValidationResult.Valid -> {
                generatedWorkoutFlow.value = result.workout
                generatedSnapshot = result.snapshot
                errorFlow.value = null
            }

            is ProgramValidationResult.Invalid -> {
                // Fail closed. A rejected proposal is never shown, and the previously
                // displayed workout is left alone rather than replaced by an invalid one.
                errorFlow.value = validationError(result.violations, isRegeneration)
            }
        }
    }

    /**
     * Turns a whole-program rejection into a typed reason for the Today error card.
     *
     * A rejection whose every reason is an exceeded configured allowance is the one case a
     * user can act on directly, so it gets its own copy. It reports that this week's planned
     * volume is already covered under WallCrawl's configured policy — not that more training
     * would be unsafe.
     */
    private fun validationError(
        violations: List<ProgramViolation>,
        isRegeneration: Boolean
    ): TodayError = when {
        violations.isNotEmpty() &&
            violations.all { it.code == ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED } ->
            TodayError.WEEKLY_ALLOWANCE_REACHED

        isRegeneration -> TodayError.REGENERATION_FAILED
        else -> TodayError.FIRST_GENERATION_FAILED
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

            null ->
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
     *
     * The recommendation is revalidated first, against a freshly built context and with
     * repair switched off. An edited profile, newly completed history, or a crossed week or
     * time-zone boundary changes the context fingerprint, and that is reported as an
     * out-of-date recommendation rather than started quietly. Nothing is repaired here
     * either, so a displayed plan is never swapped for a materially different one while it
     * is being started.
     */
    fun startWorkout(
        displayName: String,
        displayRationale: String,
        onWorkoutStarted: (sessionId: String) -> Unit
    ) {
        if (generationJob?.isActive == true) return
        viewModelScope.launch {
            val currentWorkout = generatedWorkoutFlow.value ?: return@launch
            val recommendation = generatedSnapshot ?: return@launch
            try {
                val currentContext = workoutGenerationContextBuilder.build()
                if (
                    RecommendationContextIdentity.of(currentContext) !=
                    recommendation.contextIdentity
                ) {
                    errorFlow.value = TodayError.RECOMMENDATION_OUT_OF_DATE
                    return@launch
                }
                val result = programValidator.validate(
                    workout = currentWorkout,
                    context = currentContext,
                    allowRepair = false
                )
                if (result is ProgramValidationResult.Invalid) {
                    errorFlow.value = TodayError.START_VALIDATION_FAILED
                    return@launch
                }
                // The generation-time evidence is what is recorded: it describes the decision
                // that produced the plan the user accepted, and the identity check above has
                // just established that its inputs still hold.
                val session = workoutRepository.startWorkoutFromGenerated(
                    generated = currentWorkout,
                    displayName = displayName,
                    displayRationale = displayRationale,
                    userProfile = currentContext.userProfile,
                    recommendation = recommendation
                )
                onWorkoutStarted(session.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorFlow.value = TodayError.START_FAILED
            }
        }
    }

    companion object {
        private const val WEEK_MILLIS = 7 * 24 * 60 * 60 * 1_000L

        fun provideFactory(
            userProfileRepository: UserProfileRepository,
            workoutRepository: WorkoutRepository,
            workoutGenerationContextBuilder: WorkoutGenerationContextBuilder,
            workoutPlanner: WorkoutPlanner,
            programValidator: ProgramValidator
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TodayViewModel(
                    userProfileRepository,
                    workoutRepository,
                    workoutGenerationContextBuilder,
                    workoutPlanner,
                    programValidator
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
