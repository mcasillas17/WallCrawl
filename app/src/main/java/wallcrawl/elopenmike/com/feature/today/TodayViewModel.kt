package wallcrawl.elopenmike.com.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import wallcrawl.elopenmike.com.core.ai.ProgramValidationResult
import wallcrawl.elopenmike.com.core.ai.ProgramValidator
import wallcrawl.elopenmike.com.core.ai.ProgramViolation
import wallcrawl.elopenmike.com.core.ai.ProgramViolationCode
import wallcrawl.elopenmike.com.core.ai.RecommendationContextIdentity
import wallcrawl.elopenmike.com.core.ai.RecommendationSnapshot
import wallcrawl.elopenmike.com.core.ai.TrainingPolicyNoGuidanceReason
import wallcrawl.elopenmike.com.core.ai.TrainingPolicyResult
import wallcrawl.elopenmike.com.core.ai.TrainingPolicyResultException
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanner
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanningFailure
import wallcrawl.elopenmike.com.core.ai.WorkoutValidationException
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.DeloadRepository
import wallcrawl.elopenmike.com.core.model.DeloadPreferences
import wallcrawl.elopenmike.com.core.model.DeloadAction
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ai.DeloadOfferPolicy
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.TrainingFrequencyRecencyEvidence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val workoutGenerationContextBuilder: WorkoutGenerationContextBuilder,
    private val workoutPlanner: WorkoutPlanner,
    private val programValidator: ProgramValidator,
    nowTimestamp: () -> Long = System::currentTimeMillis,
    clock: Flow<Long> = minuteClock(nowTimestamp),
    zoneId: () -> ZoneId = ZoneId::systemDefault,
    private val deloadRepository: DeloadRepository? = null
) : ViewModel() {

    /**
     * The workout, validation evidence, and source unit currently on screen.
     *
     * Publish and capture these together: a profile update may arrive while generation is
     * suspended, and a completed generation may arrive while start revalidation is suspended.
     * Neither can pair one plan's values with another plan's unit or context identity.
     *
     * A rejected regeneration deliberately leaves the previous value in place, because the
     * plan it belongs to is still the one on screen.
     */
    private val recommendationFlow = MutableStateFlow<PublishedRecommendation?>(null)
    private var lastSettledContextIdentity: String? = null
    private var observedSchedulingEvidence: TrainingFrequencyRecencyEvidence? = null
    private val isRegeneratingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<TodayError?>(null)
    private val schedulingObservationFailed = MutableStateFlow(false)
    private val deloadObservationFailed = MutableStateFlow(false)
    private val isSavingDeloadFlow = MutableStateFlow(false)
    private val isStartingFlow = MutableStateFlow(false)
    private val deloadDecisionErrorFlow = MutableStateFlow<TodayError?>(null)
    private val visibleErrorFlow = combine(errorFlow, schedulingObservationFailed, deloadObservationFailed) { error, failed, deloadFailed ->
        when {
            deloadFailed -> TodayError.DELOAD_READ_FAILED
            failed -> TodayError.RECOMMENDATION_OUT_OF_DATE
            else -> error
        }
    }
    private var schedulingObserverJob: Job? = null
    private var deloadObserverJob: Job? = null
    private val deloadPreferencesFlow = MutableStateFlow<DeloadPreferences?>(null)
    private val deloadControlFlow = combine(
        deloadPreferencesFlow, isSavingDeloadFlow, deloadDecisionErrorFlow
    ) { preferences, saving, error -> Triple(preferences, saving, error) }
    private val isBusyFlow = combine(isRegeneratingFlow, isSavingDeloadFlow, isStartingFlow) { generating, saving, starting ->
        generating || saving || starting
    }
    private var isTodayVisible = false
    private var generationJob: Job? = null
    private var pendingExplicitGenerations = 0
    private var hasPendingFreshnessCheck = false

    private val sharedClock = clock.shareIn(
        viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), replay = 1
    )
    private val completedThisWeekFlow = sharedClock
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
        recommendationFlow,
        isBusyFlow,
        visibleErrorFlow,
        deloadControlFlow
    ) { sourceState, recommendation, isRegenerating, error, deloadControl ->
        val (preferences, saving, decisionError) = deloadControl
        val deload = preferences?.takeIf {
            sourceState.userProfile.onboardingCompleted && sourceState.activeSession == null
        }?.let {
            TodayDeloadState(
                profileRevision = sourceState.userProfile.revision,
                preferences = it,
                offer = DeloadOfferPolicy.offer(sourceState.userProfile, it),
                acceptedChoice = DeloadOfferPolicy.accepted(it),
                isSaving = saving || isStartingFlow.value,
                error = decisionError
            )
        }
        if (error != null) {
            TodayUiState.Error(error = error, activeSession = sourceState.activeSession, deload = deload)
        } else if (recommendation == null) {
            sourceState.activeSession?.let { TodayUiState.Preparing(it) } ?: TodayUiState.Loading
        } else {
            TodayUiState.Success(
                userProfile = sourceState.userProfile,
                suggestedWorkout = recommendation.workout,
                activeSession = sourceState.activeSession,
                isRegenerating = isRegenerating,
                completedThisWeek = sourceState.completedThisWeek,
                deload = deload,
                prescriptionUnit = recommendation.prescriptionUnit
            )
        }
    }.onStart {
        isTodayVisible = true
        refreshRecommendation()
    }.onCompletion {
        isTodayVisible = false
        schedulingObserverJob?.cancel()
        schedulingObserverJob = null
        deloadObserverJob?.cancel()
        deloadObserverJob = null
        observedSchedulingEvidence = null
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
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
        requestWorkoutGeneration(isRegeneration = false, explicit = true)
    }

    fun regenerateWorkout() {
        ensureSchedulingObservation()
        ensureDeloadObservation()
        requestWorkoutGeneration(isRegeneration = true, explicit = true)
    }

    /** Resume/clock-change check: never leave a stale-looking card until the user starts it. */
    fun refreshRecommendation() {
        ensureSchedulingObservation()
        ensureDeloadObservation()
        requestWorkoutGeneration(isRegeneration = true)
    }

    private fun ensureDeloadObservation() {
        val repository = deloadRepository ?: return
        if (!isTodayVisible || (deloadObserverJob?.isActive == true && !deloadObservationFailed.value)) return
        deloadObserverJob?.cancel()
        deloadObserverJob = viewModelScope.launch {
            repository.observe().distinctUntilChanged().catch { error ->
                if (error is CancellationException) throw error
                deloadObservationFailed.value = true
                deloadPreferencesFlow.value = null
            }.collect {
                deloadObservationFailed.value = false
                deloadPreferencesFlow.value = it
                requestWorkoutGeneration(isRegeneration = true)
            }
        }
    }

    fun decideDeload(action: DeloadAction) {
        val repository = deloadRepository ?: return
        if (isSavingDeloadFlow.value || isStartingFlow.value || deloadObservationFailed.value) return
        val state = when (val current = uiState.value) {
            is TodayUiState.Success -> current.deload.takeIf { current.activeSession == null }
            is TodayUiState.Error -> current.deload.takeIf { current.activeSession == null }
            TodayUiState.Loading, is TodayUiState.Preparing -> null
        } ?: return
        val offerId = when (action) {
            DeloadAction.REQUEST -> null
            DeloadAction.CANCEL -> state.acceptedChoice?.offer?.id ?: return
            else -> state.offer?.id ?: return
        }
        // Set before launching: a tap on Start in the same frame must not race this write.
        isSavingDeloadFlow.value = true
        deloadDecisionErrorFlow.value = null
        viewModelScope.launch {
            try {
                repository.decide(action, state.profileRevision, state.preferences.revision, offerId)
                requestWorkoutGeneration(isRegeneration = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                deloadDecisionErrorFlow.value = TodayError.DELOAD_WRITE_FAILED
            } finally {
                isSavingDeloadFlow.value = false
            }
        }
    }

    private fun ensureSchedulingObservation() {
        if (!isTodayVisible || (schedulingObserverJob?.isActive == true && !schedulingObservationFailed.value)) return
        schedulingObserverJob?.cancel()
        schedulingObserverJob = viewModelScope.launch {
            workoutGenerationContextBuilder.observeSchedulingEvidence(sharedClock)
                .catch { error ->
                    if (error is CancellationException) throw error
                    schedulingObservationFailed.value = true
                }
                .collect { evidence ->
                    // Only a successful subscription read clears an observation failure.
                    // A one-shot generation is not proof that live freshness reconnected.
                    schedulingObservationFailed.value = false
                    observedSchedulingEvidence = evidence
                    // Equal scheduling dates need not mean equal capability/load/rest/dose.
                    // The full consumed-context identity coalesces redundant history reads.
                    requestWorkoutGeneration(isRegeneration = true)
                }
        }
    }

    private fun requestWorkoutGeneration(isRegeneration: Boolean, explicit: Boolean = false) {
        if (explicit) pendingExplicitGenerations++ else hasPendingFreshnessCheck = true
        if (generationJob?.isActive == true) {
            return
        }

        generationJob = viewModelScope.launch {
            var currentRequestIsRegeneration = isRegeneration
            while (pendingExplicitGenerations > 0 || hasPendingFreshnessCheck) {
                val forceGeneration = pendingExplicitGenerations > 0
                if (forceGeneration) pendingExplicitGenerations--
                hasPendingFreshnessCheck = false
                isRegeneratingFlow.value = currentRequestIsRegeneration
                try {
                    generateValidatedWorkout(currentRequestIsRegeneration, forceGeneration)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorFlow.value = userFacingError(e, currentRequestIsRegeneration)
                } finally {
                    isRegeneratingFlow.value = false
                }
                currentRequestIsRegeneration = true
            }
        }
    }

    /**
     * Generates a recommendation and validates the whole proposal before anything is shown.
     *
     * One deterministic repair pass is permitted here and only here. Nothing reaches the
     * screen or the database until whole-program validation has accepted the complete plan
     * against the exact context that produced it.
     */
    private suspend fun generateValidatedWorkout(isRegeneration: Boolean, forceGeneration: Boolean) {
        val observedBeforeBuild = observedSchedulingEvidence
        val deloadBeforeBuild = deloadPreferencesFlow.value
        val context = workoutGenerationContextBuilder.build().let { current ->
            // Freshness rebuilds the displayed variant; only an explicit variation request
            // advances the planner counter. Completed history already advances the split.
            if (forceGeneration) current
            else current.copy(regenerationIndex = recommendationFlow.value?.workout?.generationIndex)
        }
        // Count/history/profile notifications can describe the context just published.
        // Only an explicit variation request may spend another ordinal for equal inputs.
        val identity = RecommendationContextIdentity.of(context)
        if (!forceGeneration && (identity == recommendationFlow.value?.snapshot?.contextIdentity ||
            identity == lastSettledContextIdentity)) return
        val result = try {
            programValidator.validate(
                workout = workoutPlanner.generateWorkout(context),
                context = context,
                allowRepair = true
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            lastSettledContextIdentity = identity
            throw error
        }
        when (result) {
            is ProgramValidationResult.Valid -> {
                // Initial Room observation can arrive after the builder's read. Do not
                // discard that notification. Teardown's null is not newer evidence.
                val latestObservation = observedSchedulingEvidence
                if (latestObservation != null && latestObservation != observedBeforeBuild &&
                    latestObservation != context.schedulingEvidence
                ) {
                    hasPendingFreshnessCheck = true
                    return
                }
                if (deloadPreferencesFlow.value?.let {
                        it != deloadBeforeBuild && it != context.deloadPreferences
                    } == true
                ) {
                    hasPendingFreshnessCheck = true
                    return
                }
                recommendationFlow.value = PublishedRecommendation(
                    result.workout, result.snapshot, context.userProfile.preferredUnit
                )
                errorFlow.value = null
            }

            is ProgramValidationResult.Invalid -> {
                // Fail closed. A rejected proposal is never shown, and the previously
                // displayed workout is left alone rather than replaced by an invalid one.
                errorFlow.value = validationError(result.violations, isRegeneration)
            }
        }
        // Published or failed proposals settle an identity; superseded/cancelled ones do not.
        lastSettledContextIdentity = identity
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
     *
     * A week whose configured allowance is already spent reaches this as a refusal from the
     * prescription policy rather than as a whole-program rejection, because no prescription
     * can be written at all. It is the same configured-policy limit either way, so it gets
     * the same copy as [validationError] gives it instead of a generic failure.
     */
    private fun userFacingError(error: Exception, isRegeneration: Boolean): TodayError {
        val exhaustedAllowance = (error as? TrainingPolicyResultException)?.result
            ?.let { it as? TrainingPolicyResult.NoGuidance }
            ?.reason == TrainingPolicyNoGuidanceReason.WEEKLY_DIRECT_PRIMARY_ALLOWANCE_EXHAUSTED
        if (exhaustedAllowance) return TodayError.WEEKLY_ALLOWANCE_REACHED

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
        if (generationJob?.isActive == true || schedulingObservationFailed.value ||
            deloadObservationFailed.value || isSavingDeloadFlow.value || isStartingFlow.value ||
            errorFlow.value != null
        ) return
        val published = recommendationFlow.value ?: return
        val currentWorkout = published.workout
        val recommendation = published.snapshot
        isStartingFlow.value = true
        viewModelScope.launch {
            try {
                if (workoutRepository.getActiveSessionOnce() != null) return@launch
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
            } finally {
                isStartingFlow.value = false
            }
        }
    }

    companion object {
        fun provideFactory(
            userProfileRepository: UserProfileRepository,
            workoutRepository: WorkoutRepository,
            workoutGenerationContextBuilder: WorkoutGenerationContextBuilder,
            workoutPlanner: WorkoutPlanner,
            programValidator: ProgramValidator,
            deloadRepository: DeloadRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TodayViewModel(
                    userProfileRepository,
                    workoutRepository,
                    workoutGenerationContextBuilder,
                    workoutPlanner,
                    programValidator,
                    deloadRepository = deloadRepository
                ) as T
            }
        }
    }

    private data class PublishedRecommendation(
        val workout: GeneratedWorkout,
        val snapshot: RecommendationSnapshot,
        val prescriptionUnit: WeightUnit
    )

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
