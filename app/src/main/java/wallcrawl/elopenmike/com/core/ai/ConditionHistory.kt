package wallcrawl.elopenmike.com.core.ai

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import wallcrawl.elopenmike.com.core.model.ConditionSchedule
import wallcrawl.elopenmike.com.core.model.PendingConditionReassessment
import wallcrawl.elopenmike.com.core.model.conditionDigest
import wallcrawl.elopenmike.com.core.model.conditionList

/** A retained resolved set outcome; exercise instances share their session/exercise identity. */
data class ConditionExposure(val sessionId: String, val exerciseId: String, val timestamp: Long)

data class ConditionScheduleEligibility(
    val complete: Boolean, val due: Boolean, val weeklyExposures: Int, val previousPerformedAtTimestamp: Long?
)

/** Bounded derived facts, never a second mutable work ledger. */
class ConditionHistory(
    val asOfTimestamp: Long,
    val zoneId: ZoneId,
    val windowStartTimestamp: Long,
    complete: Boolean,
    exposures: List<ConditionExposure>,
    pending: List<PendingConditionReassessment>,
    sourceFactsDigest: String? = null
) {
    val exposures = conditionList(exposures)
    val pending = conditionList(pending)
    val complete = complete && windowStartTimestamp in 1..asOfTimestamp && exposures.all {
        it.sessionId.isNotBlank() && it.exerciseId.isNotBlank() && it.timestamp in windowStartTimestamp..asOfTimestamp
    } && pending.all { it.stoppedAtTimestamp <= asOfTimestamp }
    val sourceDigest: String = conditionDigest(mapOf(
        "complete" to this.complete,
        "sourceFacts" to sourceFactsDigest,
        "exposures" to this.exposures.sortedWith(compareBy(ConditionExposure::sessionId, ConditionExposure::exerciseId, ConditionExposure::timestamp))
            .map { listOf(it.sessionId, it.exerciseId, it.timestamp) },
        "pending" to this.pending.sortedWith(compareBy(PendingConditionReassessment::profileId, PendingConditionReassessment::conditionId, PendingConditionReassessment::exerciseId))
            .map { listOf(it.profileId, it.conditionId, it.exerciseId, it.eventId, it.stoppedContextIdentity, it.responsePolicyVersion, it.stoppedAtTimestamp, it.sourceSetId) }
    ))

    fun schedule(exerciseId: String, schedule: ConditionSchedule): ConditionScheduleEligibility {
        val start = Instant.ofEpochMilli(asOfTimestamp).atZone(zoneId).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zoneId).toInstant().toEpochMilli()
        val relevant = exposures.filter { it.exerciseId == exerciseId && it.timestamp <= asOfTimestamp }
        val previous = relevant.maxOfOrNull { it.timestamp }
        val weekly = relevant.filter { it.timestamp >= start }.map { it.sessionId to it.exerciseId }.distinct().size
        val gapMillis = schedule.minimumHoursBetweenSessions.toLong() * 3_600_000L
        val covered = complete && windowStartTimestamp <= minOf(start, asOfTimestamp - gapMillis)
        return ConditionScheduleEligibility(covered, covered && weekly < schedule.sessionsPerWeek &&
            (previous == null || asOfTimestamp - previous >= gapMillis), weekly, previous)
    }
}
