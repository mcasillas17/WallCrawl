package wallcrawl.elopenmike.com.core.ui.state

import androidx.annotation.StringRes

/**
 * A refusal message with a count, so the same message is a new value each time. Key its text on
 * [attempt] and a repeat is announced again.
 *
 * It holds a string resource, so lint checks the id. Onboarding's error is an enum instead and keeps
 * its own errorAttempt, and the editor's submitAttempt counts Save taps, refused Apply taps and condition-support refusals, not one message.
 */
data class Refusal(@StringRes val message: Int? = null, val attempt: Int = 0) {
    fun next(@StringRes message: Int) = Refusal(message, attempt + 1)
    fun cleared() = copy(message = null)
}
