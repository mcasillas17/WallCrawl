package wallcrawl.elopenmike.com.feature.progress

import org.junit.Assert.assertEquals
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.StrengthPerformance
import wallcrawl.elopenmike.com.core.model.StrengthTrend

class TrendDescriptionTest {
    private fun trend(change: Int, positive: Boolean) = StrengthTrend(
        exerciseId = "squat",
        exerciseName = "Squat",
        previous = PERFORMANCE,
        current = PERFORMANCE,
        percentageChange = change,
        isPositive = positive
    )

    @Test
    fun aChangeStatesItsDirectionAndNoChangeStatesNone() {
        assertEquals(R.string.a11y_trend_up, trendDescription(trend(5, true)))
        assertEquals(R.string.a11y_trend_down, trendDescription(trend(5, false)))
        assertEquals(null, trendDescription(trend(0, true)))
        assertEquals(null, trendDescription(trend(0, false)))
    }

    private companion object {
        val PERFORMANCE = StrengthPerformance(weight = 50.0, reps = 5)
    }
}
