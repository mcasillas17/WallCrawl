package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.ConditionSchedule
import wallcrawl.elopenmike.com.core.model.ConditionText

class ConditionHistoryTest {
    private val schedule = ConditionSchedule(2, 24, ConditionText("FICTIONAL", "FICTICIO"))
    private fun time(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun countsDistinctSessionExerciseAndChecksActualPreviousOutcome() {
        val history = ConditionHistory(time("2026-11-02T12:00:00Z"), ZoneId.of("UTC"), 1, true, listOf(
            ConditionExposure("session", "exercise", time("2026-11-02T01:00:00Z")),
            ConditionExposure("session", "exercise", time("2026-11-02T02:00:00Z"))
        ), emptyList())
        val result = history.schedule("exercise", schedule)
        assertThat(result.weeklyExposures).isEqualTo(1)
        assertThat(result.previousPerformedAtTimestamp).isEqualTo(time("2026-11-02T02:00:00Z"))
        assertThat(result.due).isFalse()
    }

    @Test fun priorWeekStillControlsGapAndDstUsesElapsedHours() {
        val history = ConditionHistory(time("2026-11-02T08:30:00Z"), ZoneId.of("America/Los_Angeles"), 1, true, listOf(
            ConditionExposure("session", "exercise", time("2026-11-01T09:00:00Z"))
        ), emptyList())
        val result = history.schedule("exercise", schedule)
        assertThat(result.weeklyExposures).isEqualTo(0)
        assertThat(result.due).isFalse()
    }

    @Test fun digestExcludesChangingReadWindowWhenFactsStayTheSame() {
        val now = time("2026-11-02T12:00:00Z")
        val earlier = ConditionHistory(now, ZoneId.of("UTC"), 1, true, emptyList(), emptyList())
        val later = ConditionHistory(now, ZoneId.of("UTC"), 2, true, emptyList(), emptyList())
        assertThat(earlier.sourceDigest).isEqualTo(later.sourceDigest)
    }

    @Test fun insufficientWindowIsNeverDue() {
        val now = time("2026-11-02T12:00:00Z")
        val history = ConditionHistory(now, ZoneId.of("UTC"), now - 1, true, emptyList(), emptyList())
        assertThat(history.schedule("exercise", schedule).complete).isFalse()
        assertThat(history.schedule("exercise", schedule).due).isFalse()
    }

    @Test fun incompleteHistoryIsNeverDue() {
        val history = ConditionHistory(time("2026-11-02T12:00:00Z"), ZoneId.of("UTC"), 1, false, emptyList(), emptyList())
        assertThat(history.schedule("exercise", schedule).due).isFalse()
    }
}
