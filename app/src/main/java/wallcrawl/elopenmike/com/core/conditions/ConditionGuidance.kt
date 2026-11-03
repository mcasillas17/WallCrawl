package wallcrawl.elopenmike.com.core.conditions

import wallcrawl.elopenmike.com.core.model.AiReviewProvenance
import wallcrawl.elopenmike.com.core.model.TrainingConstraint

/**
 * A categorical property of how an exercise is normally performed. Deliberately not a score:
 * an exercise either carries a demand or it does not. The definitions live in
 * `docs/research/conditions/CONSOLIDATION.md`, section 2.
 */
enum class MovementDemand {
    PROLONGED_SITTING,
    SUSTAINED_LOCOMOTION,
    PLYOMETRIC_IMPACT,
    HEAVY_EXTERNAL_LOAD,
    SINGLE_LEG_HIP_CONTROL,
    CROSS_BODY_HIP_STRETCH,
    NERVE_TENSIONING,
    HAMSTRING_LENGTH_LOAD,
    RESISTED_HIP_EXTERNAL_ROTATION,
    END_RANGE_HIP_EXTENSION,
    SIDE_LYING_HIP_COMPRESSION,
    SIDE_SUPPORT_HOLD,
    TRUNK_FLEXION_UNDER_LOAD,
    LONG_LYING_SIT_UP,
    END_RANGE_SPINE_POSITION,
    WRIST_EXTENSOR_WORK,
    FOREARM_TWIST_OR_PRONATED_LIFT,
    HAND_GRIP_LOAD,
    WRIST_END_RANGE_LOAD,
    DEEP_KNEE_FLEXION_LOAD,
    END_RANGE_KNEE_EXTENSION_LOAD
}

/**
 * One mobility item. Exactly one of reps or duration; a hold only accompanies reps.
 * [requiresFloorTransition] is authored per entry because stretches and owned exercises
 * carry no reviewed capability metadata to read it from.
 */
data class RoutineEntry(
    val exerciseId: String,
    val targetSets: Int,
    val targetReps: Int?,
    val targetDurationSeconds: Int?,
    val holdSeconds: Int?,
    val restSeconds: Int,
    val requiresFloorTransition: Boolean
)

/** Text is keyed by language; [exerciseCues] by exercise id first, then language. */
data class ConditionRecord(
    val id: String,
    val avoidDemands: Set<MovementDemand>,
    val modifyDemands: Set<MovementDemand>,
    val routine: List<RoutineEntry>,
    val exerciseCues: Map<String, Map<String, String>>,
    val notes: Map<String, List<String>>,
    val redFlags: Map<String, List<String>>,
    val evidenceGrade: String,
    val sources: List<String>,
    val provenance: AiReviewProvenance
)

/** [jointLabelDemands] are modify demands; a joint label never excludes on its own. */
data class ConditionGuidance(
    val version: String,
    val exerciseDemands: Map<String, Set<MovementDemand>>,
    val conditions: Map<String, ConditionRecord>,
    val jointLabelDemands: Map<TrainingConstraint, Set<MovementDemand>>,
    val demandCues: Map<MovementDemand, Map<String, String>>
)

interface ConditionGuidanceSource {
    suspend fun guidance(): ConditionGuidanceResult
}

sealed interface ConditionGuidanceResult {
    data class Loaded(val guidance: ConditionGuidance) : ConditionGuidanceResult

    /** The overlay could not be read or failed validation. Callers must fail closed. */
    data object Unavailable : ConditionGuidanceResult
}

/** Display text only: [language], else English. Never read by a planning decision. */
fun Map<String, List<String>>.textIn(language: String): List<String> =
    this[language] ?: this["en"].orEmpty()
