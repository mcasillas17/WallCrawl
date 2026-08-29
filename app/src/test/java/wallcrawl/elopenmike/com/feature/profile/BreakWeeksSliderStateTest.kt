package wallcrawl.elopenmike.com.feature.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BreakWeeksSliderStateTest {

    @Test
    fun multiStepDrag_thenFinish_persistsExactlyOnceWithFinalValue() {
        val state = BreakWeeksSliderState(persistedWeeks = 0)
        val persistedCalls = mutableListOf<Int>()

        // Simulates a real drag gesture: many onValueChange frames fire before the
        // gesture completes.
        state.onDrag(5)
        state.onDrag(12)
        state.onDrag(20)

        assertThat(persistedCalls).isEmpty()

        state.onDragFinished { weeks -> persistedCalls += weeks }

        assertThat(persistedCalls).containsExactly(20)
        assertThat(state.displayWeeks).isEqualTo(20)
    }

    @Test
    fun externalValueChange_duringDrag_doesNotSnapDisplayValue() {
        val state = BreakWeeksSliderState(persistedWeeks = 4)

        state.onDrag(10)
        // The persisted profile flow can emit a stale/other value while the user is
        // mid-drag; the thumb must not jump away from the user's finger.
        state.onPersistedValueChanged(4)

        assertThat(state.displayWeeks).isEqualTo(10)
    }

    @Test
    fun externalValueChange_afterDragFinished_updatesDisplayValue() {
        val state = BreakWeeksSliderState(persistedWeeks = 4)

        state.onDrag(10)
        state.onDragFinished { }
        state.onPersistedValueChanged(7)

        assertThat(state.displayWeeks).isEqualTo(7)
    }
}
