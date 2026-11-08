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

    @Test fun mondayRolloverResetsQuotaWithoutResettingElapsedGap() {
        val sunday = time("2026-11-01T23:59:00Z")
        val exposures = listOf(ConditionExposure("first", "exercise", sunday - 86_400_000), ConditionExposure("second", "exercise", sunday))
        val before = ConditionHistory(sunday, ZoneId.of("UTC"), 1, true, exposures, emptyList()).schedule("exercise", schedule)
        val after = ConditionHistory(sunday + 120_000, ZoneId.of("UTC"), 1, true, exposures, emptyList()).schedule("exercise", schedule)
        assertThat(before.weeklyExposures).isEqualTo(2)
        assertThat(before.due).isFalse()
        assertThat(after.weeklyExposures).isEqualTo(0)
        assertThat(after.due).isFalse()
    }

    @Test fun timeZoneChangeReevaluatesLocalWeekWithoutChangingSourceFacts() {
        val now = time("2026-11-02T01:00:00Z")
        val exposures = listOf(ConditionExposure("session", "exercise", now - 3_600_000))
        val utc = ConditionHistory(now, ZoneId.of("UTC"), 1, true, exposures, emptyList())
        val la = ConditionHistory(now, ZoneId.of("America/Los_Angeles"), 1, true, exposures, emptyList())
        assertThat(utc.sourceDigest).isEqualTo(la.sourceDigest)
        val once = ConditionSchedule(1, 0, ConditionText("FICTIONAL", "FICTICIO"))
        assertThat(utc.schedule("exercise", once).weeklyExposures).isEqualTo(1)
        assertThat(la.schedule("exercise", once).weeklyExposures).isEqualTo(1)
        val next = ConditionHistory(now + 7 * 3_600_000, la.zoneId, 1, true, exposures, emptyList())
        assertThat(next.schedule("exercise", once).weeklyExposures).isEqualTo(0)
        assertThat(next.schedule("exercise", once).due).isTrue()
    }

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
