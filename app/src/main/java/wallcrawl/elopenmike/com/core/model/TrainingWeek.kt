package wallcrawl.elopenmike.com.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * One ISO training week in a specific zone: Monday at local midnight through the following
 * Monday at local midnight, exclusive.
 *
 * The bounds are derived through [java.time.ZonedDateTime] rather than by adding a fixed
 * number of hours, so a week containing a daylight-saving transition is the 167 or 169 hours
 * that actually elapsed locally instead of a silently misaligned 168.
 *
 * The zone is part of the week's identity. Reading the same calendar week in another zone
 * produces a different [TrainingWeek], and therefore a separately reconstructed ledger,
 * rather than relabeling an existing one.
 */
data class TrainingWeek(
    val startEpochDay: Long,
    val zoneId: ZoneId,
    val startEpochMillis: Long,
    val endEpochMillisExclusive: Long
) {
    init {
        require(endEpochMillisExclusive > startEpochMillis) {
            "endEpochMillisExclusive must be greater than startEpochMillis."
        }
    }

    /**
     * How much time the week actually spans, which is 167 or 169 hours across a
     * daylight-saving transition rather than a fixed 168.
     */
    val elapsedMillis: Long get() = endEpochMillisExclusive - startEpochMillis

    /** True when [epochMillis] falls in `[start, nextWeekStart)`. */
    fun contains(epochMillis: Long): Boolean =
        epochMillis >= startEpochMillis && epochMillis < endEpochMillisExclusive

    companion object {
        /**
         * The ISO week containing [instant] as observed in [zoneId].
         *
         * The calendar date is resolved in the zone first, so an instant just after
         * local midnight belongs to the local week the user is actually training in
         * rather than the UTC week it happens to fall in.
         */
        fun containing(instant: Instant, zoneId: ZoneId): TrainingWeek = startingOn(
            weekStartEpochDay = instant.atZone(zoneId)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toEpochDay(),
            zoneId = zoneId
        )

        /**
         * The ISO week beginning on [weekStartEpochDay], which must be a Monday.
         *
         * `atStartOfDay(zone)` is used deliberately: on the rare day whose local midnight
         * does not exist because the clocks jumped forward, it resolves to the first valid
         * local instant of that day instead of an invalid one.
         */
        fun startingOn(weekStartEpochDay: Long, zoneId: ZoneId): TrainingWeek {
            val startDate = LocalDate.ofEpochDay(weekStartEpochDay)
            require(startDate.dayOfWeek == DayOfWeek.MONDAY) {
                "weekStartEpochDay must name a Monday for ISO week semantics."
            }
            return TrainingWeek(
                startEpochDay = weekStartEpochDay,
                zoneId = zoneId,
                startEpochMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                endEpochMillisExclusive = startDate.plusWeeks(1)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
            )
        }
    }
}
