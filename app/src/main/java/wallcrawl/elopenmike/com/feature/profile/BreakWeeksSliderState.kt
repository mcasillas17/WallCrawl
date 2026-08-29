package wallcrawl.elopenmike.com.feature.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks the in-progress drag value for the "Returning After a Break" slider.
 *
 * The slider must render every intermediate [onDrag] frame locally, without touching
 * the repository, and persist exactly once when the drag gesture completes via
 * [onDragFinished]. This avoids repeated Room read/write transactions, inflated
 * profile revisions, and repeated Today regeneration while dragging.
 *
 * [displayWeeks] is backed by Compose's snapshot state so a composable reading it
 * recomposes automatically as the user drags, without a separate mirrored state var.
 */
class BreakWeeksSliderState(persistedWeeks: Int) {

    var displayWeeks: Int by mutableStateOf(persistedWeeks)
        private set

    private var isDragging: Boolean = false

    /** Invoked on every onValueChange frame while the user drags the slider. */
    fun onDrag(weeks: Int) {
        isDragging = true
        displayWeeks = weeks
    }

    /** Invoked once from onValueChangeFinished; persists the final value exactly once. */
    fun onDragFinished(persist: (Int) -> Unit) {
        isDragging = false
        persist(displayWeeks)
    }

    /**
     * Invoked when the persisted profile value changes externally (e.g. reloaded from
     * Room). Ignored while a drag is in progress so the thumb doesn't snap away from
     * the user's finger mid-gesture.
     */
    fun onPersistedValueChanged(weeks: Int) {
        if (!isDragging) {
            displayWeeks = weeks
        }
    }
}
