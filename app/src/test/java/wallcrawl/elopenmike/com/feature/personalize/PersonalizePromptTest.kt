package wallcrawl.elopenmike.com.feature.personalize

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PersonalizePromptTest {
    private val now = 100 * DISMISS_MILLIS

    @Test fun hidden_whenPersonalized() =
        assertThat(shouldShowTodayCard(true, 5, null, now)).isFalse()

    @Test fun hidden_beforeFirstCompletedWorkout() =
        assertThat(shouldShowTodayCard(false, 0, null, now)).isFalse()

    @Test fun shown_afterFirstWorkout_neverDismissed() =
        assertThat(shouldShowTodayCard(false, 1, null, now)).isTrue()

    @Test fun hidden_withinSevenDaysOfDismiss() =
        assertThat(shouldShowTodayCard(false, 1, now - 1, now + DISMISS_MILLIS - 2)).isFalse()

    @Test fun shown_afterSevenDays() =
        assertThat(shouldShowTodayCard(false, 1, now, now + DISMISS_MILLIS)).isTrue()
}
