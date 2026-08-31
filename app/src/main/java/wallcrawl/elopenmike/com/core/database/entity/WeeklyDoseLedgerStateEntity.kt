package wallcrawl.elopenmike.com.core.database.entity

import androidx.room.Entity

/**
 * A cached, fully reconstructable weekly dose ledger.
 *
 * This table is derived state, never an authority. Completed workout history remains the
 * only source of truth: every row can be deleted at any time and the identical ledger will
 * be rebuilt from history the next time it is read. Nothing increments a row when a set is
 * logged or a workout is generated.
 *
 * A row is only reusable while [sourceFingerprint] still matches the fingerprint of the
 * current inputs, so newly completed work, edited or approved metadata, a new catalog, or a
 * new policy version all invalidate it rather than silently serving a stale count.
 *
 * The key is deliberately (profile, week, zone, policy): reading the same calendar week in
 * another zone or under another policy produces its own row instead of relabelling an
 * existing snapshot.
 *
 * [policyVersion] is stored as text rather than a converted enum so that a value written by
 * a future build decodes as "no usable cache" here instead of being coerced into a policy
 * this build implements. Nothing in this table carries notes, names, effort, load, or any
 * profile or body value.
 */
@Entity(
    tableName = "weekly_dose_ledger_state",
    primaryKeys = ["profileId", "weekStartEpochDay", "timeZoneId", "policyVersion"]
)
data class WeeklyDoseLedgerStateEntity(
    val profileId: String,
    val weekStartEpochDay: Long,
    val timeZoneId: String,
    val policyVersion: String,
    val catalogVersion: String,
    val reviewPolicyVersion: Int,
    val ledgerPayload: String,
    val sourceFingerprint: String,
    /** Diagnostics only. It never takes part in cache validity or in the ledger itself. */
    val generatedAtTimestamp: Long
)
