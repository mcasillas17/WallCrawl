package wallcrawl.elopenmike.com.core.model

/**
 * WallCrawl-owned exercise model. Catalog facts are always available while
 * programming metadata is present only after exercise requirements are reviewed.
 */
data class Exercise(
    val id: String,
    val source: ExerciseSource? = null,
    val name: String,
    val searchAliases: List<String> = emptyList(),
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String> = emptyList(),
    val listedEquipment: List<String> = emptyList(),
    val type: ExerciseType,
    val isStretch: Boolean = false,
    val programming: ExerciseProgrammingMetadata? = null,
    val reviewedMetadata: ReviewedExerciseMetadata? = null
)

data class ExerciseSource(
    val catalogId: String,
    val sourceId: String,
    val sourceSlug: String,
    val attribution: ExerciseAttribution
)

data class ExerciseAttribution(
    val creator: String,
    val creatorUrl: String,
    val license: String,
    val licenseUrl: String,
    val source: ExerciseAttributionSource? = null
)

data class ExerciseAttributionSource(
    val name: String,
    val url: String,
    val license: String,
    val licenseUrl: String,
    val changes: String
)

data class ExerciseProgrammingMetadata(
    val requiredEquipmentCombinations: List<List<String>>,
    val movementPattern: MovementPattern,
    val difficulty: Difficulty,
    val mechanics: MechanicsType,
    val recommendedRepRange: RepRange,
    val fatigueScore: Int,
    val progressionType: ProgressionType,
    val alternativeExerciseIds: List<String> = emptyList(),
    val coachingSummary: String
)

/** Human-review status for the future automatic-planning metadata contract. */
enum class ReviewState {
    DRAFT,
    APPROVED
}

enum class ComplexityTier {
    FOUNDATIONAL,
    STANDARD,
    ADVANCED
}

enum class ImpactLevel {
    NONE,
    LOW,
    HIGH
}

enum class SupportRequirement {
    SUPPORTED,
    OPTIONAL_SUPPORT,
    UNSUPPORTED
}

enum class PrescriptionShape {
    WEIGHT_REPS,
    BODYWEIGHT_REPS,
    ASSISTED_BODYWEIGHT,
    DURATION
}

/**
 * Provenance for reviewed planning metadata.
 *
 * Drafts deliberately leave the human reviewer and review time absent. Approved metadata
 * requires both fields at the importer/parser trust boundaries; an AI-authored draft must
 * never manufacture them merely to satisfy a non-null domain shape.
 */
data class ReviewProvenance(
    val reviewerRole: String?,
    val rationaleOrSource: String,
    val reviewedAtEpochMillis: Long?,
    val schemaVersion: Int,
    val policyVersion: Int
)

/** A directed reviewed graph edge; rationale is required for documented exceptions. */
data class ReviewedExerciseLink(
    val exerciseId: String,
    val rationale: String? = null
)

/**
 * WallCrawl-owned categorical metadata for future deterministic planning.
 *
 * This block is separate from [ExerciseProgrammingMetadata]. The current planner continues
 * to consume the legacy block until the later reviewed-only eligibility/policy migration.
 */
data class ReviewedExerciseMetadata(
    val reviewState: ReviewState,
    val directPrimaryMuscle: String,
    val descriptiveSecondaryMuscles: Set<String>,
    val movementPattern: MovementPattern,
    val complexity: ComplexityTier,
    val progressionFamily: String,
    val prescriptionShape: PrescriptionShape,
    val approvedRegressions: List<ReviewedExerciseLink>,
    val approvedSubstitutions: List<ReviewedExerciseLink>,
    val capabilityRequirements: Set<MovementCapabilityType>,
    val supportRequirement: SupportRequirement,
    val impactLevel: ImpactLevel,
    val equipmentAlternatives: List<List<String>>,
    val provenance: ReviewProvenance
)

enum class ExerciseType {
    WEIGHT_REPS,
    BODYWEIGHT_REPS,
    ASSISTED_BODYWEIGHT,
    DURATION,
    DISTANCE_DURATION
}

enum class MovementPattern {
    HORIZONTAL_PUSH,
    VERTICAL_PUSH,
    HORIZONTAL_PULL,
    VERTICAL_PULL,
    SQUAT,
    HINGE,
    LUNGE,
    ISOLATION,
    CARRY,
    CORE,
    OTHER
}

enum class MechanicsType {
    COMPOUND,
    ISOLATION
}

enum class Difficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

enum class ProgressionType {
    REPETITIONS_THEN_LOAD,
    LOAD,
    REPETITIONS,
    DURATION,
    DISTANCE,
    ASSISTANCE_REDUCTION
}

data class RepRange(
    val min: Int,
    val max: Int
) {
    init {
        require(min > 0 && max >= min) { "Rep range must be positive and ordered." }
    }

    override fun toString(): String = if (min == max) "$min" else "$min–$max"
}

object StandardEquipment {
    const val BARBELL = "Barbell"
    const val DUMBBELL = "Dumbbell"
    const val CABLE = "Cable"
    const val MACHINE = "Machine"
    const val BODYWEIGHT = "Bodyweight"
    const val KETTLEBELL = "Kettlebell"
    const val RESISTANCE_BAND = "Resistance Band"
    const val BENCH = "Bench"
    const val PULLUP_BAR = "Pull-up Bar"
    const val DIP_BARS = "Dip Bars"
    const val SQUAT_RACK = "Squat Rack"
    const val BOX = "Box"
    const val CARDIO = "Cardio"
    const val CHAIR = "Chair"
    const val DOORWAY = "Doorway"
    const val PLATE = "Plate"
    const val STABILITY_BALL = "Stability Ball"
    const val TOWEL = "Towel"
    const val WALL = "Wall"

    val ALL = listOf(
        BARBELL, DUMBBELL, CABLE, MACHINE, BODYWEIGHT,
        KETTLEBELL, RESISTANCE_BAND, BENCH, PULLUP_BAR, DIP_BARS, SQUAT_RACK,
        BOX, CARDIO, CHAIR, DOORWAY, PLATE, STABILITY_BALL, TOWEL, WALL
    )
}
