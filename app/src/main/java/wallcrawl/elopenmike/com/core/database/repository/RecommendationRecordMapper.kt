package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.PERSISTED_LIST_SEPARATOR
import wallcrawl.elopenmike.com.core.database.entity.WorkoutRecommendationRecordEntity
import wallcrawl.elopenmike.com.core.model.RecommendationRecord

/**
 * Maps a recommendation record between its domain shape and its stored row.
 *
 * Records now supply progression continuity. An unreadable row must fail the read/export,
 * not masquerade as an honestly absent historical record.
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

internal fun WorkoutRecommendationRecordEntity.toRecommendationRecord(): RecommendationRecord {
    val accounting = requireNotNull(RecommendationDoseAccountingPayload.decode(doseAccounting)) {
        "Stored recommendation accounting is unreadable."
    }
    val codes = if (reasonCodes.isEmpty()) {
        emptyList()
    } else {
        reasonCodes.split(PERSISTED_LIST_SEPARATOR)
    }
    return RecommendationRecord(
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
}
