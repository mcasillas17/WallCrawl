package wallcrawl.elopenmike.com.core.ai

import java.time.Instant
import java.time.ZoneId
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetOutcomeRules
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.TrainingFrequencyRecencyEvidence
import wallcrawl.elopenmike.com.core.model.WorkoutSession

/** One binary repeat-practice preference. Never restricts eligibility or prescriptions. */
class TrainingFrequencyRecencyPolicy {
    fun windowStart(now: Instant, zoneId: ZoneId): Long = now.atZone(zoneId).toLocalDate()
        .minusDays(TrainingFrequencyRecencyEvidence.LOOKBACK_DATES - 1)
        .atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun derive(
        sessions: List<WorkoutSession>,
        exercises: List<Exercise>,
        now: Instant,
        zoneId: ZoneId
    ): TrainingFrequencyRecencyEvidence {
        require(sessions.size <= MAX_SESSIONS) { "Scheduling history exceeds its reconstruction bound." }
        require(exercises.map { it.id }.distinct().size == exercises.size) { "Duplicate catalog IDs." }
        val byId = exercises.associateBy(Exercise::id)
        val start = windowStart(now, zoneId)
        val end = now.toEpochMilli()
        val dates = sortedMapOf<String, MutableSet<Long>>()
        val sessionIds = mutableSetOf<String>()
        val instanceIds = mutableSetOf<String>()
        val setIds = mutableSetOf<String>()
        var examinedSets = 0
        sessions.forEach { session ->
            require(sessionIds.add(session.id)) { "Duplicate scheduling session." }
            if (session.status != SessionStatus.COMPLETED) return@forEach
            val completed = requireNotNull(session.completedAtTimestamp) { "Completed session has no timestamp." }
            if (completed !in start..end) return@forEach
            val date = Instant.ofEpochMilli(completed).atZone(zoneId).toLocalDate().toEpochDay()
            session.exercises.forEach { exercise ->
                require(instanceIds.add(exercise.id)) { "Duplicate scheduling exercise instance." }
                exercise.sets.forEach { set ->
                    require(++examinedSets <= MAX_SETS) { "Scheduling work exceeds its reconstruction bound." }
                    require(setIds.add(set.id)) { "Duplicate scheduling set." }
                    SetOutcomeRules.requireValidOutcome(
                        SetPerformanceInput(
                            isCompleted = set.isCompleted, completedAtTimestamp = set.completedAtTimestamp,
                            stopReason = set.stopReason, stoppedAtTimestamp = set.stoppedAtTimestamp,
                            rpe = set.rpe, rir = set.rir, feltManageable = set.feltManageable
                        ),
                        allowMissingCompletionTimestamp = true
                    )
                }
                if (exercise.sets.none { it.isCreditableWorkSet }) return@forEach
                val primary = byId[exercise.exerciseId]?.acceptedMetadata()?.directPrimaryMuscle
                    ?: return@forEach
                dates.getOrPut(primary) { sortedSetOf() }.add(date)
            }
        }
        return TrainingFrequencyRecencyEvidence(
            todayEpochDay = now.atZone(zoneId).toLocalDate().toEpochDay(),
            timeZoneId = zoneId.id,
            practiceDates = dates.mapValues { it.value.toList() }
        )
    }

    fun revisitBandDays(daysPerWeek: Int): Int {
        require(daysPerWeek in 2..6) { "Training frequency must be between 2 and 6 days." }
        return (CALENDAR_WEEK_DAYS + daysPerWeek - 1) / daysPerWeek
    }

    fun preferredMuscles(evidence: TrainingFrequencyRecencyEvidence, daysPerWeek: Int): Set<String> {
        val band = revisitBandDays(daysPerWeek)
        return evidence.practiceDates.filterValues { dates ->
            dates.size >= TrainingFrequencyRecencyEvidence.FAMILIAR_DATES &&
                evidence.todayEpochDay - dates.last() >= band
        }.keys
    }

    companion object {
        private const val CALENDAR_WEEK_DAYS = 7
        /** Pathological storage bounds: refuse, never sample and report complete evidence. */
        const val MAX_SESSIONS = 2_000
        const val MAX_SETS = 100_000
    }
}
