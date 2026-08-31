package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.TrainingWeek

/**
 * ISO week boundaries in real zones, including the two weeks that are not 168 hours long.
 */
class TrainingWeekTest {

    @Test
    fun aSundayBelongsToTheWeekThatStartedOnTheMondayBeforeIt() {
        val newYork = ZoneId.of("America/New_York")
        val sundayEvening = ZonedDateTime.of(2026, 9, 6, 23, 59, 59, 0, newYork).toInstant()

        val week = TrainingWeek.containing(sundayEvening, newYork)

        assertThat(week.startEpochDay).isEqualTo(MONDAY_EPOCH_DAY)
        assertThat(week.contains(sundayEvening.toEpochMilli())).isTrue()
    }

    @Test
    fun aMondayAtLocalMidnightStartsTheNextWeekRatherThanEndingTheOldOne() {
        val newYork = ZoneId.of("America/New_York")
        val mondayMidnight = ZonedDateTime.of(2026, 9, 7, 0, 0, 0, 0, newYork).toInstant()

        val previousWeek = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, newYork)
        val week = TrainingWeek.containing(mondayMidnight, newYork)

        assertThat(previousWeek.contains(mondayMidnight.toEpochMilli())).isFalse()
        assertThat(previousWeek.endEpochMillisExclusive)
            .isEqualTo(mondayMidnight.toEpochMilli())
        assertThat(week.startEpochDay).isEqualTo(MONDAY_EPOCH_DAY + 7L)
        assertThat(week.contains(mondayMidnight.toEpochMilli())).isTrue()
    }

    @Test
    fun aSpringForwardWeekIsOneHourShorterThanSevenFixedDays() {
        // Monday 2026-03-02 in America/New_York; the clocks jump forward on 2026-03-08.
        val week = TrainingWeek.startingOn(20_514L, ZoneId.of("America/New_York"))

        assertThat(week.startEpochMillis).isEqualTo(1_772_427_600_000L)
        assertThat(week.endEpochMillisExclusive).isEqualTo(1_773_028_800_000L)
        assertThat(week.elapsedMillis).isEqualTo(167L * MILLIS_PER_HOUR)
        assertThat(week.endEpochMillisExclusive)
            .isNotEqualTo(week.startEpochMillis + SEVEN_FIXED_DAYS_MILLIS)
    }

    @Test
    fun aFallBackWeekIsOneHourLongerThanSevenFixedDays() {
        // Monday 2026-10-26 in America/New_York; the clocks fall back on 2026-11-01.
        val week = TrainingWeek.startingOn(20_752L, ZoneId.of("America/New_York"))

        assertThat(week.startEpochMillis).isEqualTo(1_792_987_200_000L)
        assertThat(week.endEpochMillisExclusive).isEqualTo(1_793_595_600_000L)
        assertThat(week.elapsedMillis).isEqualTo(169L * MILLIS_PER_HOUR)
        assertThat(week.endEpochMillisExclusive)
            .isNotEqualTo(week.startEpochMillis + SEVEN_FIXED_DAYS_MILLIS)
    }

    @Test
    fun aWeekContainingASkippedLocalMidnightStillHasExactLocalBounds() {
        // In America/Santiago the local midnight of Sunday 2026-09-06 never happens:
        // the clocks jump straight from 23:59:59 to 01:00:00.
        val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZoneId.of("America/Santiago"))

        assertThat(week.startEpochMillis).isEqualTo(1_788_148_800_000L)
        assertThat(week.endEpochMillisExclusive).isEqualTo(1_788_750_000_000L)
        assertThat(week.elapsedMillis).isEqualTo(167L * MILLIS_PER_HOUR)
    }

    @Test
    fun aNonUtcZoneStartsItsWeekAtLocalMidnightRatherThanUtcMidnight() {
        val kolkata = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZoneId.of("Asia/Kolkata"))
        val utc = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZoneId.of("UTC"))

        // Asia/Kolkata is UTC+05:30, so its week opens five and a half hours earlier.
        assertThat(kolkata.startEpochMillis).isEqualTo(1_788_114_600_000L)
        assertThat(utc.startEpochMillis - kolkata.startEpochMillis)
            .isEqualTo(5L * MILLIS_PER_HOUR + 30L * 60_000L)
    }

    @Test
    fun theSameInstantInAnotherZoneProducesADistinctWeekRatherThanARelabelledOne() {
        val instant = Instant.ofEpochMilli(1_788_120_000_000L)
        val kolkata = TrainingWeek.containing(instant, ZoneId.of("Asia/Kolkata"))
        val honolulu = TrainingWeek.containing(instant, ZoneId.of("Pacific/Honolulu"))

        assertThat(kolkata.startEpochDay).isEqualTo(MONDAY_EPOCH_DAY)
        assertThat(honolulu.startEpochDay).isEqualTo(MONDAY_EPOCH_DAY - 7L)
        assertThat(honolulu).isNotEqualTo(kolkata)
    }

    @Test
    fun aWeekStartThatIsNotAMondayIsRejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            TrainingWeek.startingOn(MONDAY_EPOCH_DAY + 1L, ZoneId.of("UTC"))
        }

        assertThat(error).hasMessageThat().contains("Monday")
    }

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000L
        const val SEVEN_FIXED_DAYS_MILLIS = 7L * 24L * MILLIS_PER_HOUR
    }
}
