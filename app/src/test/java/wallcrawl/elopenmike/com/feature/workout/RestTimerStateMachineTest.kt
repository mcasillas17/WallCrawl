package wallcrawl.elopenmike.com.feature.workout

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rest timer is driven entirely by an injected monotonic clock and a deadline, so
 * these tests advance time explicitly instead of waiting for it.
 */
class RestTimerStateMachineTest {

    private val clock = FakeElapsedRealtimeClock()
    private val timer = RestTimerStateMachine(clock)

    @Test
    fun startingRest_runsUntilTheDeadlineDerivedFromThePersistedRestSeconds() {
        timer.start(setId = "set-1", restSeconds = 90)

        val running = timer.state.value as RestTimerState.Running
        assertThat(running.setId).isEqualTo("set-1")
        assertThat(running.deadlineElapsedRealtime).isEqualTo(90_000L)
        assertThat(timer.remainingSeconds()).isEqualTo(90)
    }

    @Test
    fun remainingTime_isDerivedFromTheDeadlineSoBackgroundingCannotDrift() {
        timer.start(setId = "set-1", restSeconds = 90)

        // The process was backgrounded for a minute; nothing ticked while it was away.
        clock.advanceMillis(60_000L)

        assertThat(timer.remainingSeconds()).isEqualTo(30)
        assertThat(timer.state.value).isInstanceOf(RestTimerState.Running::class.java)
    }

    @Test
    fun reachingTheDeadline_expiresExactlyOnceForThatSet() {
        timer.start(setId = "set-1", restSeconds = 90)

        clock.advanceMillis(90_000L)
        timer.refresh()

        assertThat(timer.state.value).isEqualTo(RestTimerState.Expired("set-1"))
        assertThat(timer.remainingSeconds()).isEqualTo(0)

        clock.advanceMillis(120_000L)
        timer.refresh()
        assertThat(timer.state.value).isEqualTo(RestTimerState.Expired("set-1"))
    }

    @Test
    fun addingThirtySeconds_extendsTheExistingDeadlineWithoutRestarting() {
        timer.start(setId = "set-1", restSeconds = 90)
        clock.advanceMillis(60_000L)

        timer.addThirtySeconds()

        val running = timer.state.value as RestTimerState.Running
        assertThat(running.setId).isEqualTo("set-1")
        assertThat(running.deadlineElapsedRealtime).isEqualTo(120_000L)
        assertThat(timer.remainingSeconds()).isEqualTo(60)
    }

    @Test
    fun addingTime_isClampedToThePrescriptionsMaximumRest() {
        timer.start(setId = "set-1", restSeconds = RestTimerStateMachine.MAX_REST_SECONDS - 10)

        timer.addThirtySeconds()
        timer.addThirtySeconds()

        val running = timer.state.value as RestTimerState.Running
        assertThat(running.deadlineElapsedRealtime)
            .isEqualTo(RestTimerStateMachine.MAX_REST_SECONDS * 1_000L)
    }

    @Test
    fun addingTimeAfterExpiry_resumesFromNowRatherThanFromAStaleDeadline() {
        timer.start(setId = "set-1", restSeconds = 60)
        clock.advanceMillis(120_000L)
        timer.refresh()
        assertThat(timer.state.value).isEqualTo(RestTimerState.Expired("set-1"))

        timer.addThirtySeconds()

        val running = timer.state.value as RestTimerState.Running
        assertThat(running.setId).isEqualTo("set-1")
        assertThat(timer.remainingSeconds()).isEqualTo(30)
    }

    @Test
    fun skippingRest_endsTheCountdownAsFinishedForThatSet() {
        timer.start(setId = "set-1", restSeconds = 90)

        timer.skip()

        assertThat(timer.state.value).isEqualTo(RestTimerState.Expired("set-1"))
        assertThat(timer.remainingSeconds()).isEqualTo(0)
    }

    @Test
    fun cancellingRest_returnsToIdle() {
        timer.start(setId = "set-1", restSeconds = 90)

        timer.cancel()

        assertThat(timer.state.value).isEqualTo(RestTimerState.Idle)
        assertThat(timer.remainingSeconds()).isEqualTo(0)
    }

    @Test
    fun zeroRest_skipsRunningEntirelyAndStaysIdle() {
        timer.start(setId = "set-1", restSeconds = 0)

        assertThat(timer.state.value).isEqualTo(RestTimerState.Idle)
        assertThat(timer.remainingSeconds()).isEqualTo(0)

        // A subsequent explicit event on a timer that never started changes nothing.
        timer.addThirtySeconds()
        assertThat(timer.state.value).isEqualTo(RestTimerState.Idle)
    }

    @Test
    fun negativeRest_isTreatedAsNoRest() {
        timer.start(setId = "set-1", restSeconds = -30)

        assertThat(timer.state.value).isEqualTo(RestTimerState.Idle)
    }

    @Test
    fun startingRestForAnotherSet_replacesTheRunningTimerInsteadOfStackingOne() {
        timer.start(setId = "set-1", restSeconds = 90)
        clock.advanceMillis(10_000L)

        timer.start(setId = "set-2", restSeconds = 60)

        val running = timer.state.value as RestTimerState.Running
        assertThat(running.setId).isEqualTo("set-2")
        assertThat(running.deadlineElapsedRealtime).isEqualTo(70_000L)
    }

    @Test
    fun restartingTheSameSetsTimer_isIdempotentWithinTheSameInstant() {
        timer.start(setId = "set-1", restSeconds = 90)
        val first = timer.state.value

        timer.start(setId = "set-1", restSeconds = 90)

        assertThat(timer.state.value).isEqualTo(first)
    }

    @Test
    fun cancelOrSkip_whileIdle_changesNothing() {
        timer.skip()
        assertThat(timer.state.value).isEqualTo(RestTimerState.Idle)

        timer.cancel()
        assertThat(timer.state.value).isEqualTo(RestTimerState.Idle)
    }

    @Test
    fun restSecondsAboveTheMaximum_isClampedAtStart() {
        timer.start(setId = "set-1", restSeconds = RestTimerStateMachine.MAX_REST_SECONDS + 600)

        val running = timer.state.value as RestTimerState.Running
        assertThat(running.deadlineElapsedRealtime)
            .isEqualTo(RestTimerStateMachine.MAX_REST_SECONDS * 1_000L)
    }
}

internal class FakeElapsedRealtimeClock(
    private var elapsedRealtime: Long = 0L
) : ElapsedRealtimeClock {
    override fun elapsedRealtimeMillis(): Long = elapsedRealtime

    fun advanceMillis(millis: Long) {
        elapsedRealtime += millis
    }
}
