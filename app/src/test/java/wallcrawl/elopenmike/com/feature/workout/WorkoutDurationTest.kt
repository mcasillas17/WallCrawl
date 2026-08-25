package wallcrawl.elopenmike.com.feature.workout

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkoutDurationTest {

    @Test
    fun elapsedWorkoutMinutes_usesActualElapsedTimeRatherThanTargetDuration() {
        val startedAt = 10 * MINUTE_MILLIS
        val now = startedAt + (7 * MINUTE_MILLIS) + 59_000L

        assertThat(elapsedWorkoutMinutes(startedAt, now)).isEqualTo(7)
    }

    @Test
    fun elapsedWorkoutMinutes_futureOrSubMinuteStart_returnsMinimumPersistableDuration() {
        assertThat(elapsedWorkoutMinutes(startedAtTimestamp = 1_000L, nowTimestamp = 500L))
            .isEqualTo(1)
        assertThat(elapsedWorkoutMinutes(startedAtTimestamp = 1_000L, nowTimestamp = 50_000L))
            .isEqualTo(1)
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}
