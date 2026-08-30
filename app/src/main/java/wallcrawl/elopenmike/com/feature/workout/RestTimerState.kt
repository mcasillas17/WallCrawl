package wallcrawl.elopenmike.com.feature.workout

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A monotonic clock, injected so rest-timer behaviour is deterministic in tests.
 *
 * Rest is measured with elapsed realtime, never wall-clock time: a user changing the
 * device clock, a timezone change, or an NTP correction must not lengthen or shorten a
 * rest period that is already running.
 */
fun interface ElapsedRealtimeClock {
    fun elapsedRealtimeMillis(): Long

    companion object {
        val System: ElapsedRealtimeClock = ElapsedRealtimeClock { SystemClock.elapsedRealtime() }
    }
}

/** Local rest-timer state for the active workout. */
sealed interface RestTimerState {
    /** No rest is being tracked. */
    data object Idle : RestTimerState

    /** Rest is counting down for [setId] until [deadlineElapsedRealtime] on the monotonic clock. */
    data class Running(
        val setId: String,
        val deadlineElapsedRealtime: Long
    ) : RestTimerState

    /** Rest for [setId] finished, either by reaching its deadline or by being skipped. */
    data class Expired(val setId: String) : RestTimerState
}

/**
 * The rest-timer state machine.
 *
 * Remaining time is always derived from the deadline and the monotonic clock rather than
 * decremented, so backgrounding the app, a paused UI, or a missed tick cannot make the
 * countdown drift. Every transition is an explicit event; nothing starts a timer
 * implicitly.
 *
 * This state is deliberately in-memory only. It survives recomposition and configuration
 * changes because it lives in the ViewModel, but it is not restored after process death:
 * a deadline captured against a previous process's elapsed-realtime baseline could be
 * restored as a misleading countdown, and showing an invented rest period is worse than
 * showing none.
 */
class RestTimerStateMachine(
    private val clock: ElapsedRealtimeClock,
    /** Upper bound on total rest, matching the prescription's own valid rest range. */
    private val maxRestSeconds: Int = MAX_REST_SECONDS
) {
    private val _state = MutableStateFlow<RestTimerState>(RestTimerState.Idle)
    val state: StateFlow<RestTimerState> = _state.asStateFlow()

    /** Elapsed-realtime instant this rest period began, used to clamp extensions. */
    private var restStartedAtElapsedRealtime: Long = 0L

    /**
     * Starts (or replaces) the rest period for [setId] using that exercise's persisted
     * rest length. A rest length of zero or less skips the running state entirely and
     * leaves the timer idle, because there is no rest to show.
     */
    fun start(setId: String, restSeconds: Int) {
        val clamped = restSeconds.coerceAtMost(maxRestSeconds)
        if (clamped <= 0) {
            _state.value = RestTimerState.Idle
            return
        }
        val now = clock.elapsedRealtimeMillis()
        restStartedAtElapsedRealtime = now
        _state.value = RestTimerState.Running(
            setId = setId,
            deadlineElapsedRealtime = now + clamped * 1_000L
        )
    }

    /**
     * Extends the current rest period, clamped so total rest never exceeds the
     * prescription's valid maximum. After expiry it resumes from now, which is what the
     * user means when they ask for more rest after the countdown has already ended.
     */
    fun addThirtySeconds() {
        val setId = when (val current = _state.value) {
            is RestTimerState.Running -> current.setId
            is RestTimerState.Expired -> current.setId
            RestTimerState.Idle -> return
        }
        val now = clock.elapsedRealtimeMillis()
        val currentDeadline = (_state.value as? RestTimerState.Running)
            ?.deadlineElapsedRealtime
            ?: now
        val maximumDeadline = restStartedAtElapsedRealtime + maxRestSeconds * 1_000L
        val extended = (currentDeadline + EXTENSION_SECONDS * 1_000L)
            .coerceAtMost(maximumDeadline)
        if (extended <= now) {
            _state.value = RestTimerState.Expired(setId)
            return
        }
        _state.value = RestTimerState.Running(setId = setId, deadlineElapsedRealtime = extended)
    }

    /** Ends the countdown early; rest is over, but the app still knows which set it followed. */
    fun skip() {
        val running = _state.value as? RestTimerState.Running ?: return
        _state.value = RestTimerState.Expired(running.setId)
    }

    /** Dismisses rest entirely. */
    fun cancel() {
        _state.value = RestTimerState.Idle
    }

    /** Moves a running timer to [RestTimerState.Expired] once its deadline has passed. */
    fun refresh() {
        val running = _state.value as? RestTimerState.Running ?: return
        if (clock.elapsedRealtimeMillis() >= running.deadlineElapsedRealtime) {
            _state.value = RestTimerState.Expired(running.setId)
        }
    }

    /** Whole seconds left, derived from the deadline; zero whenever nothing is running. */
    fun remainingSeconds(): Int {
        val running = _state.value as? RestTimerState.Running ?: return 0
        val remainingMillis = running.deadlineElapsedRealtime - clock.elapsedRealtimeMillis()
        if (remainingMillis <= 0L) return 0
        return ((remainingMillis + 999L) / 1_000L).toInt()
    }

    companion object {
        /** Mirrors `ExercisePrescription`'s valid rest range. */
        const val MAX_REST_SECONDS: Int = 1_800
        const val EXTENSION_SECONDS: Int = 30
    }
}
