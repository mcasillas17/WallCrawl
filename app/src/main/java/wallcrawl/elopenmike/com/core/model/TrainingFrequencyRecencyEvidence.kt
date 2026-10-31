package wallcrawl.elopenmike.com.core.model

import java.time.ZoneId
import java.time.LocalDate
import java.util.Collections

/** Canonical completed-practice dates, not a dose ledger or a recovery assessment. */
class TrainingFrequencyRecencyEvidence(
    val todayEpochDay: Long,
    val timeZoneId: String,
    practiceDates: Map<String, List<Long>>
) {
    val policyVersion: String = POLICY_VERSION
    val practiceDates: Map<String, List<Long>> = Collections.unmodifiableMap(
        practiceDates.toSortedMap().mapValues { (_, dates) ->
            Collections.unmodifiableList(dates.distinct().sorted())
        }
    )

    init {
        ZoneId.of(timeZoneId)
        LocalDate.ofEpochDay(todayEpochDay)
        require(practiceDates.size <= MAX_PRIMARY_MUSCLES) { "Too many scheduling primary muscles." }
        require(practiceDates.keys.all {
            it.isNotBlank() && it.length <= MuscleDoseAccounting.MAX_MUSCLE_LENGTH && it.none(Char::isISOControl)
        })
        require(practiceDates.values.all { dates ->
            dates.isNotEmpty() && dates.all { it in (todayEpochDay - LOOKBACK_DATES + 1)..todayEpochDay }
        }) { "Practice dates must lie in the scheduling window." }
    }

    override fun equals(other: Any?): Boolean = other is TrainingFrequencyRecencyEvidence &&
        todayEpochDay == other.todayEpochDay && timeZoneId == other.timeZoneId &&
        practiceDates == other.practiceDates

    override fun hashCode(): Int = 31 * (31 * todayEpochDay.hashCode() + timeZoneId.hashCode()) +
        practiceDates.hashCode()

    companion object {
        const val POLICY_VERSION = "TRAINING_FREQUENCY_RECENCY_V1"
        /** Versioned product choices, not paper-derived thresholds. */
        const val LOOKBACK_DATES = 14L
        const val FAMILIAR_DATES = 2
        const val MAX_PRIMARY_MUSCLES = 64
        // Two distinct dates must fit the window; the latest cannot be its oldest date.
        val MAX_LAST_PRACTICE_AGE_DATES = (LOOKBACK_DATES - FAMILIAR_DATES).toInt()
    }
}
