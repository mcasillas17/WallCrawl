package wallcrawl.elopenmike.com.feature.history

import wallcrawl.elopenmike.com.core.ai.ProgramViolationCode
import wallcrawl.elopenmike.com.core.model.*

/** Decoded recorded facts, never a newly evaluated policy decision or reconstructed prescription. */
data class HistoryReasons(
    val outcome: String?,
    val violations: List<ProgramViolationCode>,
    val progression: List<ProgressionProvenance>,
    val deload: DeloadRecommendationProvenance?,
    val ranking: List<WorkoutRankingReason>,
    val unsupportedIdentities: List<String>
)

fun decodeHistoryReasons(record: RecommendationRecord): HistoryReasons {
    // These allowlists are deliberately explicit: adding an enum in a future policy must
    // not silently opt its interpretation into a historical renderer.
    val validatorSupported = record.validatorVersion in setOf("WHOLE_PROGRAM_V1", "WHOLE_PROGRAM_V2")
    val progression = ProgressionReasonCode.decode(record.reasonCodes)
    val deload = DeloadReasonCode.decode(record.reasonCodes)
    val ranking = WorkoutRankingReasonCode.decode(record.reasonCodes)
    val generationTokens = record.reasonCodes.filter {
        it.substringBefore(':').substringBefore('.') == "PLANNER_GENERATION_V1"
    }
    require(generationTokens.size <= 1) { "Duplicate recorded generation index." }
    generationTokens.forEach { code ->
        val index = code.removePrefix("PLANNER_GENERATION_V1:").toIntOrNull()
        require(index != null && index >= 0 && code == "PLANNER_GENERATION_V1:$index") {
            "Malformed recorded generation index."
        }
    }
    val violations = if (validatorSupported) record.reasonCodes.mapNotNull { code ->
        ProgramViolationCode.entries.firstOrNull { it.name == code }
    } else emptyList()
    val unsupported = buildList {
        fun check(value: String?, supported: Set<String>) {
            if (value != null && value !in supported) add(value)
        }
        check(record.validatorVersion, setOf("WHOLE_PROGRAM_V1", "WHOLE_PROGRAM_V2"))
        check(record.outcome, if (validatorSupported) setOf("VALID", "REPAIRED") else emptySet())
        check(record.durationEstimatorVersion, setOf("DURATION_ESTIMATOR_V1"))
        check(record.trainingPolicyVersion, setOf("STATE_BASED_DOSE_EFFORT_REST_V1", "STATE_BASED_DOSE_EFFORT_REST_V2"))
        check(record.ledgerPolicyVersion, setOf("PRIMARY_ONLY_V1"))
        check(record.programStatePolicyVersion, setOf("PROGRAM_STATE_V1", "PROGRAM_STATE_V2"))
        check(record.adaptationState, setOf(
            "NEEDS_ONBOARDING", "UNCALIBRATED", "INITIATE", "BUILD", "DEVELOP",
            "HOLD", "RETURNING", "DELOAD_OFFERED", "RECALIBRATE"
        ))
        // Catalog/review identities are opaque provenance, not claims of current approval.
        record.reasonCodes.forEach { code ->
            val recognized = code in violations.map { it.name } ||
                code.startsWith("ONE_VARIABLE_PROGRESSION_V1.") ||
                code.startsWith("DELOAD_ONE_WORKOUT_V1.") ||
                code.startsWith("SUPPORTED_REGRESSION_PREFERENCE_V1.") ||
                code.startsWith("TRAINING_FREQUENCY_RECENCY_V1.") ||
                code == "TRAINING_FREQUENCY_RECENCY_V1" ||
                code in generationTokens
            val unknownCapability = code.startsWith("SUPPORTED_REGRESSION_PREFERENCE_V1.") &&
                ".CAPABILITY:" in code &&
                MovementCapabilityType.entries.none { it.name == code.substringAfter(".CAPABILITY:") }
            if (!recognized || unknownCapability) add(code)
        }
    }.distinct()
    return HistoryReasons(
        record.outcome.takeIf { validatorSupported && it in setOf("VALID", "REPAIRED") },
        violations, progression, deload, ranking, unsupported
    )
}

enum class HistoryMeasurementKind { REPS, LOAD, ASSISTANCE, SECONDS, METERS }
data class HistoryMeasurement(val kind: HistoryMeasurementKind, val value: Double?)

/** Null is a missing applicable measurement. Inapplicable dimensions never become fake zeroes. */
fun historyMeasurements(set: WorkoutSet, performed: Boolean): List<HistoryMeasurement> = buildList {
    fun add(kind: HistoryMeasurementKind, target: Number?, actual: Number?) {
        add(HistoryMeasurement(kind, (if (performed) actual else target)?.toDouble()))
    }
    when (set.exerciseType) {
        ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT_REPS, ExerciseType.ASSISTED_BODYWEIGHT -> {
            add(HistoryMeasurementKind.REPS, set.targetReps, set.completedReps)
            if (set.exerciseType == ExerciseType.WEIGHT_REPS)
                add(HistoryMeasurementKind.LOAD, set.targetWeight, set.completedWeight)
            if (set.exerciseType == ExerciseType.ASSISTED_BODYWEIGHT)
                add(HistoryMeasurementKind.ASSISTANCE, set.targetAssistanceWeight, set.completedAssistanceWeight)
        }
        ExerciseType.DURATION ->
            add(HistoryMeasurementKind.SECONDS, set.targetDurationSeconds, set.completedDurationSeconds)
        ExerciseType.DISTANCE_DURATION -> {
            if (set.targetDurationSeconds != null || (performed && set.completedDurationSeconds != null))
                add(HistoryMeasurementKind.SECONDS, set.targetDurationSeconds, set.completedDurationSeconds)
            if (set.targetDistanceMeters != null || (performed && set.completedDistanceMeters != null))
                add(HistoryMeasurementKind.METERS, set.targetDistanceMeters, set.completedDistanceMeters)
        }
    }
}
