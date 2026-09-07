package wallcrawl.elopenmike.com.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * The immutable provenance of the recommendation one session was started from.
 *
 * ## Why it exists
 *
 * `workout_exercises` records what was planned. This records *how the plan was decided*:
 * which validator, catalog, review, policy, and ledger versions were in force, which
 * adaptation state and accounting week it was built under, what whole-program validation
 * concluded, and — when the reviewed path was enabled — how the proposal was accounted
 * against the configured weekly allowance. None of that is recoverable from history.
 *
 * ## One row per started session
 *
 * The primary key is the session's own id, and the row is written inside the same
 * transaction that inserts the session, its exercises, and its sets. A start that fails
 * leaves neither a session nor a record; a deleted session takes its record with it.
 * A recommendation that was only displayed writes nothing at all.
 *
 * ## Storage decisions
 *
 * Every version-like column is text rather than a converted enum, exactly as the weekly
 * ledger cache stores its policy version: a value written by a future build has to read
 * back as unrecognised rather than being coerced into a meaning this build implements.
 *
 * [reasonCodes] is a separator-joined list of stable code names and [doseAccounting] is the
 * versioned `RecommendationDoseAccountingPayload`. Both are bounded and validated on read.
 *
 * Nothing here carries a name, note, load, repetition count, effort value, or any profile or
 * body measurement.
 */
@Entity(
    tableName = "workout_recommendation_records",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkoutRecommendationRecordEntity(
    @PrimaryKey
    val sessionId: String,
    val validatorVersion: String,
    val durationEstimatorVersion: String,
    val outcome: String,
    val reviewedPathEnabled: Boolean,
    val catalogVersion: String?,
    val reviewPolicyVersion: Int,
    val trainingPolicyVersion: String?,
    val ledgerPolicyVersion: String?,
    val programStatePolicyVersion: String?,
    val adaptationState: String?,
    val weekStartEpochDay: Long?,
    val timeZoneId: String?,
    val profileRevision: Long,
    val contextIdentity: String,
    val reasonCodes: String,
    val doseAccounting: String,
    /** Diagnostics only. Nothing reads it to decide anything. */
    val recordedAtTimestamp: Long
)
