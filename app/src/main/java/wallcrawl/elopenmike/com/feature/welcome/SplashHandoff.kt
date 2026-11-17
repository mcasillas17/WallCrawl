package wallcrawl.elopenmike.com.feature.welcome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Hands the launch splash's emblem to the first-launch intro (docs/launch-animation.md, Wiring 4).
 *
 * On a first launch the splash does not zoom away: it stays up while the intro draws the same
 * emblem at the same place, and is removed once that frame is on screen, so the emblem never
 * jumps. Every other launch resolves to [State.None] and the intro (if any) plays from 0 ms.
 */
class SplashHandoff(initial: State) {
    sealed interface State {
        /** A splash is showing and has not decided yet. */
        data object Pending : State
        /** No splash to continue from. */
        data object None : State
        /** The emblem's centre in window pixels and its drawn width in pixels. */
        class Ready(val centerInWindow: Offset, val emblemWidthPx: Float, val remove: () -> Unit) : State
    }

    var state: State by mutableStateOf(initial)
        private set

    fun handTo(center: Offset, emblemWidthPx: Float, remove: () -> Unit) { state = State.Ready(center, emblemWidthPx, remove) }

    fun none() { state = State.None }

    /** Removes the splash, once the intro has drawn the emblem underneath it. */
    fun release() {
        (state as? State.Ready)?.remove?.invoke()
        state = State.None
    }
}
