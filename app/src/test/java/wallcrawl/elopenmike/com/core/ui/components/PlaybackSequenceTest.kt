package wallcrawl.elopenmike.com.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSequenceTest {
    @Test
    fun theLoopGoesThereAndBackWithoutRepeatingAnEnd() {
        assertEquals(listOf(0, 1, 2, 1), playbackSequence(3))
        assertEquals(listOf(0, 1), playbackSequence(2))
        assertEquals(listOf(0), playbackSequence(1))
        assertEquals(emptyList<Int>(), playbackSequence(0))
    }
}
