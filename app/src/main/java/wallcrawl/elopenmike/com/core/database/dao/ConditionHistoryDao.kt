package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import wallcrawl.elopenmike.com.core.ai.ConditionExposure
import wallcrawl.elopenmike.com.core.ai.ConditionHistory
import wallcrawl.elopenmike.com.core.database.entity.ConditionReassessmentPendingEntity
import wallcrawl.elopenmike.com.core.database.entity.toModel
import wallcrawl.elopenmike.com.core.database.repository.toUserProfile
import wallcrawl.elopenmike.com.core.model.*

data class ConditionContextRead(val profile: UserProfile, val program: ProgramReadState?, val programHeader: ProgramHeader, val history: ConditionHistory)

data class ConditionOutcomeRow(val setId: String, val sessionId: String, val exerciseId: String,
    val sessionStartedAtTimestamp: Long, val isCompleted: Boolean, val completedAtTimestamp: Long?, val stoppedAtTimestamp: Long?, val stopReason: String?,
    val sessionStatus: String? = null, val sessionCompletedAt: Long? = null, val purpose: String? = null,
    val completedReps: Int? = null, val completedWeight: Double? = null, val completedAssistanceWeight: Double? = null,
    val completedDurationSeconds: Int? = null, val completedDistanceMeters: Double? = null,
    val rpe: Float? = null, val rir: Int? = null, val feltManageable: Boolean? = null) {
    fun timestamp(): Long? = (when {
        isCompleted && completedAtTimestamp != null && stoppedAtTimestamp == null && stopReason == null -> completedAtTimestamp
        !isCompleted && completedAtTimestamp == null && stoppedAtTimestamp != null && SetStopReason.entries.any { it.name == stopReason } -> stoppedAtTimestamp
        else -> null
    })?.takeIf { sessionStartedAtTimestamp > 0 && it >= sessionStartedAtTimestamp }
}

/** Same canonical array bytes without retaining 100,000 row strings and a joined JSON copy. */
internal fun conditionOutcomeDigest(rows: Sequence<ConditionOutcomeRow>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update('['.code.toByte())
    rows.forEachIndexed { index, row ->
        if (index > 0) digest.update(','.code.toByte())
        digest.update(conditionCanonicalJson(row.toString()).toByteArray(Charsets.UTF_8))
    }
    digest.update(']'.code.toByte())
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Direct bounded projections: no parent status filter, relation graphs, or inferred event times. */
@Dao
interface ConditionHistoryDao : ProgramDao {
    // A valid closed parent bounds a legacy unknown outcome from above; it never supplies its exact time.
    @Query("""SELECT s.id AS setId, e.sessionId, e.exerciseId, w.startedAtTimestamp AS sessionStartedAtTimestamp, s.isCompleted,
        s.completedAtTimestamp, s.stoppedAtTimestamp, s.stopReason,
        w.status AS sessionStatus, w.completedAtTimestamp AS sessionCompletedAt, e.purpose,
        s.completedReps, s.completedWeight, s.completedAssistanceWeight, s.completedDurationSeconds,
        s.completedDistanceMeters, s.rpe, s.rir, s.feltManageable
        FROM workout_sets s JOIN workout_exercises e ON e.id = s.workoutExerciseId JOIN workout_sessions w ON w.id = e.sessionId
        WHERE e.exerciseId IN (:exerciseIds)
        AND (s.isCompleted = 1 OR s.completedAtTimestamp IS NOT NULL OR s.stoppedAtTimestamp IS NOT NULL OR s.stopReason IS NOT NULL)
        AND (s.completedAtTimestamp >= :start OR s.stoppedAtTimestamp >= :start
            OR (s.completedAtTimestamp IS NULL AND s.stoppedAtTimestamp IS NULL)
            OR w.startedAtTimestamp <= 0 OR s.completedAtTimestamp < w.startedAtTimestamp OR s.stoppedAtTimestamp < w.startedAtTimestamp
            OR (s.isCompleted = 1 AND (s.completedAtTimestamp IS NULL OR s.completedAtTimestamp <= 0 OR s.stoppedAtTimestamp IS NOT NULL OR s.stopReason IS NOT NULL))
            OR (s.isCompleted = 0 AND (s.completedAtTimestamp IS NOT NULL OR s.stoppedAtTimestamp IS NULL OR s.stoppedAtTimestamp <= 0 OR s.stopReason IS NULL OR s.stopReason NOT IN ('USER_SKIPPED', 'PAIN_STOP', 'EQUIPMENT_UNAVAILABLE', 'TIME_CONSTRAINT', 'OTHER'))))
        AND NOT (s.isCompleted = 1 AND s.completedAtTimestamp IS NULL AND s.stoppedAtTimestamp IS NULL AND s.stopReason IS NULL
            AND w.status = 'COMPLETED' AND w.startedAtTimestamp > 0 AND w.completedAtTimestamp IS NOT NULL
            AND w.completedAtTimestamp >= w.startedAtTimestamp AND w.completedAtTimestamp < :start)
        ORDER BY s.id LIMIT :limit""")
    suspend fun conditionOutcomeProbe(exerciseIds: List<String>, start: Long, limit: Int): List<ConditionOutcomeRow>

    @Query("""SELECT * FROM condition_reassessment_pending WHERE profileId = :profileId
        ORDER BY conditionId, exerciseId LIMIT :limit""")
    suspend fun conditionPendingProbe(profileId: String, limit: Int): List<ConditionReassessmentPendingEntity>

    @Query("SELECT * FROM condition_reassessment_pending WHERE profileId = :profileId ORDER BY conditionId, exerciseId LIMIT :limit")
    fun observeConditionPendingProbe(profileId: String, limit: Int): kotlinx.coroutines.flow.Flow<List<ConditionReassessmentPendingEntity>>

    /** Bootstrap is performed by the caller before entering this transaction; no recursive write gate. */
    @Transaction
    suspend fun readConditionContext(profileId: String, support: ConditionSupportCatalog, asOf: Long, zone: ZoneId, programMode: Boolean): ConditionContextRead {
        val program = if (programMode) readProgramState(profileId) else null
        val profile = (when (program) {
            is ProgramReadState.Supported -> program.profile
            is ProgramReadState.Unsupported -> program.profile
            is ProgramReadState.Empty -> program.profile
            null -> getProgramProfile(profileId)?.toUserProfile()
        }) ?: throw IllegalStateException("Profile disappeared before condition snapshot.")
        val header = program?.header ?: readProgramHeader(profileId)
        val schedules = profile.activeConditions.flatMap { selection ->
            support.records[selection.conditionId]?.cases?.singleOrNull { case ->
                case.requiredAnswers.all { (id, answer) -> selection.answers[id] == answer }
            }?.rules.orEmpty()
        }.filter { it.schedule != null }
        val exerciseIds = schedules.map { it.exerciseIdentity.exerciseId }.distinct().sorted()
        val weekStart = Instant.ofEpochMilli(asOf).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
        val maxGap = schedules.maxOfOrNull { requireNotNull(it.schedule).minimumHoursBetweenSessions } ?: 0
        val start = minOf(weekStart, asOf - maxGap * 3_600_000L)
        val outcomes = if (exerciseIds.isNotEmpty() && exerciseIds.size <= MAX_CONDITION_EXERCISES) conditionOutcomeProbe(exerciseIds, start, MAX_CONDITION_OUTCOMES + 1) else emptyList()
        val markers = conditionPendingProbe(profileId, MAX_CONDITION_MARKERS + 1)
        val complete = exerciseIds.size <= MAX_CONDITION_EXERCISES && outcomes.size <= MAX_CONDITION_OUTCOMES && markers.size <= MAX_CONDITION_MARKERS &&
            outcomes.all { it.timestamp()?.let { timestamp -> timestamp in start..asOf } == true }
        val exposures = outcomes.take(MAX_CONDITION_OUTCOMES).mapNotNull { row -> row.timestamp()?.takeIf { it in start..asOf }?.let { ConditionExposure(row.sessionId, row.exerciseId, it) } }
        return ConditionContextRead(profile, program, header, ConditionHistory(asOf, zone, start, complete, exposures, markers.take(MAX_CONDITION_MARKERS).map { it.toModel() },
            sourceFactsDigest = conditionOutcomeDigest(outcomes.asSequence().take(MAX_CONDITION_OUTCOMES))))
    }

    companion object {
        // Resource bounds, not clinical limits. Overflow refuses planning rather than truncating evidence.
        const val MAX_CONDITION_EXERCISES = 512
        const val MAX_CONDITION_OUTCOMES = 100_000
        const val MAX_CONDITION_MARKERS = 512
    }
}
