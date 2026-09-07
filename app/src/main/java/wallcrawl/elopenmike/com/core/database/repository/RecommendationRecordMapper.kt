package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.PERSISTED_LIST_SEPARATOR
import wallcrawl.elopenmike.com.core.database.entity.WorkoutRecommendationRecordEntity
import wallcrawl.elopenmike.com.core.model.RecommendationRecord

/**
 * Maps a recommendation record between its domain shape and its stored row.
 *
 * Reading is strict: a row whose bounded lists no longer decode, or whose values no longer
 * satisfy [RecommendationRecord]'s own contract, reads back as `null` rather than as a
 * partially understood record. Provenance that cannot be trusted is worse than provenance
 * that is missing, and every caller already handles a session that has no record.
 */
internal fun RecommendationRecord.toEntity(): WorkoutRecommendationRecordEntity =
    WorkoutRecommendationRecordEntity(
        sessionId = sessionId,
        validatorVersion = validatorVersion,
        durationEstimatorVersion = durationEstimatorVersion,
        outcome = outcome,
        reviewedPathEnabled = reviewedPathEnabled,
        catalogVersion = catalogVersion,
        reviewPolicyVersion = reviewPolicyVersion,
        trainingPolicyVersion = trainingPolicyVersion,
        ledgerPolicyVersion = ledgerPolicyVersion,
        programStatePolicyVersion = programStatePolicyVersion,
        adaptationState = adaptationState,
        weekStartEpochDay = weekStartEpochDay,
        timeZoneId = timeZoneId,
        profileRevision = profileRevision,
        contextIdentity = contextIdentity,
        reasonCodes = reasonCodes.joinToString(PERSISTED_LIST_SEPARATOR),
        doseAccounting = RecommendationDoseAccountingPayload.encode(doseAccounting),
        recordedAtTimestamp = recordedAtEpochMillis
    )

internal fun WorkoutRecommendationRecordEntity.toRecommendationRecord(): RecommendationRecord? {
    val accounting = RecommendationDoseAccountingPayload.decode(doseAccounting) ?: return null
    val codes = if (reasonCodes.isEmpty()) {
        emptyList()
    } else {
        reasonCodes.split(PERSISTED_LIST_SEPARATOR)
    }
    return runCatching {
        RecommendationRecord(
            sessionId = sessionId,
            validatorVersion = validatorVersion,
            durationEstimatorVersion = durationEstimatorVersion,
            outcome = outcome,
            reviewedPathEnabled = reviewedPathEnabled,
            catalogVersion = catalogVersion,
            reviewPolicyVersion = reviewPolicyVersion,
            trainingPolicyVersion = trainingPolicyVersion,
            ledgerPolicyVersion = ledgerPolicyVersion,
            programStatePolicyVersion = programStatePolicyVersion,
            adaptationState = adaptationState,
            weekStartEpochDay = weekStartEpochDay,
            timeZoneId = timeZoneId,
            profileRevision = profileRevision,
            contextIdentity = contextIdentity,
            reasonCodes = codes,
            doseAccounting = accounting,
            recordedAtEpochMillis = recordedAtTimestamp
        )
    }.getOrNull()
}
