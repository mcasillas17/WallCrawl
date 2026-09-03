package wallcrawl.elopenmike.com.core.model

import java.util.Collections
import java.util.LinkedHashMap
import kotlin.ConsistentCopyVisibility

enum class CapabilityEvidencePolicyVersion {
    TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1
}

enum class CapabilityEvidenceReason {
    TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS
}

enum class CapabilityEvidenceScope {
    EXACT_EXERCISE,
    DIRECT_APPROVED_REGRESSION
}

enum class ComparableMovementShape {
    WEIGHT_REPETITIONS,
    BODYWEIGHT_REPETITIONS,
    ASSISTED_BODYWEIGHT_REPETITIONS,
    TIMED_DURATION,
    DISTANCE_DURATION_DISTANCE_ONLY,
    DISTANCE_DURATION_TIME_ONLY,
    DISTANCE_DURATION_DISTANCE_AND_TIME
}

data class CapabilityEvidence(
    val policyVersion: CapabilityEvidencePolicyVersion,
    val reason: CapabilityEvidenceReason,
    val appliesToExerciseId: String,
    val demonstratedExerciseId: String,
    val scope: CapabilityEvidenceScope,
    val comparableShape: ComparableMovementShape,
    val qualifyingSessionIds: List<String>
) {
    init {
        require(appliesToExerciseId.isNotBlank()) { "appliesToExerciseId must not be blank." }
        require(demonstratedExerciseId.isNotBlank()) { "demonstratedExerciseId must not be blank." }
        require(qualifyingSessionIds.isNotEmpty()) { "qualifyingSessionIds must not be empty." }
        require(qualifyingSessionIds.all { it.isNotBlank() }) {
            "qualifyingSessionIds must not contain blank ids."
        }
    }
}

@ConsistentCopyVisibility
data class CapabilityEvidenceSet private constructor(
    val records: Map<String, CapabilityEvidence>
) {
    operator fun get(exerciseId: String): CapabilityEvidence? =
        records[exerciseId]

    fun appliesTo(exerciseId: String): Boolean =
        records.containsKey(exerciseId)

    companion object {
        private val EMPTY = CapabilityEvidenceSet(emptyMap())

        fun empty(): CapabilityEvidenceSet = EMPTY

        fun from(records: Map<String, CapabilityEvidence>): CapabilityEvidenceSet {
            if (records.isEmpty()) return EMPTY

            val normalized = records.entries
                .map { (exerciseId, record) ->
                    require(exerciseId.isNotBlank()) {
                        "records must not contain blank exercise ids."
                    }
                    require(exerciseId == record.appliesToExerciseId) {
                        "records must be keyed by appliesToExerciseId."
                    }
                    require(record.appliesToExerciseId.isNotBlank()) {
                        "appliesToExerciseId must not be blank."
                    }
                    require(record.demonstratedExerciseId.isNotBlank()) {
                        "demonstratedExerciseId must not be blank."
                    }
                    val sessionIds = record.qualifyingSessionIds
                        .distinct()
                        .sorted()
                    require(sessionIds.size >= 2) {
                        "qualifyingSessionIds must contain at least two distinct session ids."
                    }
                    record.copy(
                        qualifyingSessionIds = Collections.unmodifiableList(sessionIds)
                    )
                }
                .sortedBy { it.appliesToExerciseId }
                .associateBy { it.appliesToExerciseId }

            val copiedRecords = LinkedHashMap<String, CapabilityEvidence>(normalized.size)
            normalized.forEach { (exerciseId, record) ->
                copiedRecords[exerciseId] = record
            }

            return CapabilityEvidenceSet(Collections.unmodifiableMap(copiedRecords))
        }
    }
}
