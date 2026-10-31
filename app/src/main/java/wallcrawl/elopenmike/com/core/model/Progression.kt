package wallcrawl.elopenmike.com.core.model

enum class ProgressionAxis { LOAD, REP_RANGE, ASSISTANCE, DURATION, DISTANCE }

enum class ProgressionReason {
    ADVANCED,
    HOLD_INSUFFICIENT_HISTORY,
    HOLD_DUPLICATE_OBSERVATION,
    HOLD_INCOMPLETE_WORK,
    HOLD_INVALID_OBSERVATION,
    HOLD_UNKNOWN_LOAD,
    HOLD_TARGETS_CHANGED,
    HOLD_TARGET_NOT_MET,
    HOLD_MANAGEABLE_NOT_CONFIRMED,
    HOLD_MISSING_EFFORT,
    HOLD_EFFORT_NOT_QUALIFYING,
    HOLD_AT_BOUND,
    HOLD_RETURNING_GUIDANCE,
    HOLD_ACCEPTED_DELOAD,
    HOLD_VALIDATION_REPAIR,
    HOLD_CONFIGURATION_CHANGED,
    HOLD_LEGACY_PATH,
    HOLD_UNSUPPORTED_AUTOMATIC_SHAPE
}

data class ProgressionDecision(
    val exerciseId: String,
    val reason: ProgressionReason,
    val axis: ProgressionAxis?,
    val referencePrescription: ExercisePrescription,
    val prescription: ExercisePrescription,
    val sourceSessionIds: List<String>,
    val baseConfigurationDigest: String,
    val policyVersion: String = VERSION
) {
    init {
        require(exerciseId.isRecordableProgressionId())
        require(policyVersion == VERSION)
        require(baseConfigurationDigest.matches(Regex("[a-f0-9]{64}")))
        require(sourceSessionIds.size <= 2 && sourceSessionIds.distinct().size == sourceSessionIds.size)
        require(sourceSessionIds.all { it.isRecordableProgressionId() })
        require((reason == ProgressionReason.ADVANCED) == (axis != null))
        require(referencePrescription.exerciseType == prescription.exerciseType)
        if (axis != null) {
            require(sourceSessionIds.size == 2 && referencePrescription != prescription)
            require(referencePrescription.withAxisFrom(prescription, axis) == prescription) {
                "Progression must change exactly its declared axis."
            }
        } else if (reason == ProgressionReason.HOLD_ACCEPTED_DELOAD ||
            reason == ProgressionReason.HOLD_VALIDATION_REPAIR
        ) {
            require(prescription.targetSets <= referencePrescription.targetSets)
            require(referencePrescription.copy(targetSets = prescription.targetSets) == prescription)
        } else {
            require(referencePrescription == prescription) { "A hold preserves its reference targets." }
        }
    }

    companion object {
        const val VERSION = "ONE_VARIABLE_PROGRESSION_V1"
    }
}

internal fun String.isRecordableProgressionId(): Boolean =
    isNotBlank() && length <= 200 && none(Char::isISOControl) && "|||" !in this

fun ExercisePrescription.withAxisFrom(other: ExercisePrescription, axis: ProgressionAxis): ExercisePrescription =
    when (axis) {
        ProgressionAxis.LOAD -> copy(targetWeight = other.targetWeight)
        ProgressionAxis.REP_RANGE -> copy(repRange = other.repRange)
        ProgressionAxis.ASSISTANCE -> copy(targetAssistanceWeight = other.targetAssistanceWeight)
        ProgressionAxis.DURATION -> copy(targetDurationSeconds = other.targetDurationSeconds)
        ProgressionAxis.DISTANCE -> copy(targetDistanceMeters = other.targetDistanceMeters)
    }
