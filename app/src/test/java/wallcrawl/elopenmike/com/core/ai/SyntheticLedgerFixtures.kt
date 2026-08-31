package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.SupportRequirement
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

/**
 * Test-only fixtures for the weekly dose ledger.
 *
 * Every reviewed block built here is **synthetic** and exists only so the ledger can be
 * tested against `APPROVED` metadata while the shipped 37-entry cohort stays `DRAFT`.
 * The provenance strings say so explicitly, and nothing in `src/main` can read this file,
 * so a synthetic approval can never reach the bundled catalog or a real user's ledger.
 */
const val SYNTHETIC_REVIEWER_ROLE: String = "SYNTHETIC_TEST_REVIEWER_NOT_A_HUMAN"

const val SYNTHETIC_REVIEW_RATIONALE: String =
    "SYNTHETIC TEST FIXTURE. Not a human review and never shipped in the bundled catalog."

const val SYNTHETIC_CATALOG_VERSION: String = "synthetic-catalog-version-for-tests"

/** 2026-08-31, a Monday, used as a stable ISO week start across the ledger tests. */
const val MONDAY_EPOCH_DAY: Long = 20_696L

fun syntheticApprovedExercise(
    id: String,
    directPrimaryMuscle: String,
    descriptiveSecondaryMuscles: Set<String> = emptySet(),
    legacyPrimaryMuscles: List<String> = listOf("LEGACY-MUST-NOT-BE-CREDITED"),
    legacySecondaryMuscles: List<String> = listOf("LEGACY-MUST-NOT-BE-CREDITED")
): Exercise = syntheticExercise(
    id = id,
    legacyPrimaryMuscles = legacyPrimaryMuscles,
    legacySecondaryMuscles = legacySecondaryMuscles,
    reviewedMetadata = syntheticReviewedMetadata(
        reviewState = ReviewState.APPROVED,
        directPrimaryMuscle = directPrimaryMuscle,
        descriptiveSecondaryMuscles = descriptiveSecondaryMuscles
    )
)

fun syntheticDraftExercise(
    id: String,
    directPrimaryMuscle: String,
    descriptiveSecondaryMuscles: Set<String> = emptySet()
): Exercise = syntheticExercise(
    id = id,
    reviewedMetadata = syntheticReviewedMetadata(
        reviewState = ReviewState.DRAFT,
        directPrimaryMuscle = directPrimaryMuscle,
        descriptiveSecondaryMuscles = descriptiveSecondaryMuscles
    )
)

fun syntheticExerciseWithoutReviewedMetadata(id: String): Exercise =
    syntheticExercise(id = id, reviewedMetadata = null)

fun syntheticExercise(
    id: String,
    legacyPrimaryMuscles: List<String> = listOf("LEGACY-MUST-NOT-BE-CREDITED"),
    legacySecondaryMuscles: List<String> = listOf("LEGACY-MUST-NOT-BE-CREDITED"),
    reviewedMetadata: ReviewedExerciseMetadata?
): Exercise = Exercise(
    id = id,
    name = "Synthetic $id",
    primaryMuscles = legacyPrimaryMuscles,
    secondaryMuscles = legacySecondaryMuscles,
    listedEquipment = listOf("Bodyweight"),
    type = ExerciseType.WEIGHT_REPS,
    reviewedMetadata = reviewedMetadata
)

fun syntheticReviewedMetadata(
    reviewState: ReviewState,
    directPrimaryMuscle: String,
    descriptiveSecondaryMuscles: Set<String> = emptySet()
): ReviewedExerciseMetadata = ReviewedExerciseMetadata(
    reviewState = reviewState,
    directPrimaryMuscle = directPrimaryMuscle,
    descriptiveSecondaryMuscles = descriptiveSecondaryMuscles,
    movementPattern = MovementPattern.HORIZONTAL_PUSH,
    complexity = ComplexityTier.STANDARD,
    progressionFamily = "synthetic-family",
    prescriptionShape = PrescriptionShape.WEIGHT_REPS,
    approvedRegressions = emptyList(),
    approvedSubstitutions = emptyList(),
    capabilityRequirements = emptySet(),
    supportRequirement = SupportRequirement.SUPPORTED,
    impactLevel = ImpactLevel.LOW,
    equipmentAlternatives = listOf(listOf("Bodyweight")),
    provenance = ReviewProvenance(
        reviewerRole = if (reviewState == ReviewState.APPROVED) SYNTHETIC_REVIEWER_ROLE else null,
        rationaleOrSource = SYNTHETIC_REVIEW_RATIONALE,
        reviewedAtEpochMillis = if (reviewState == ReviewState.APPROVED) 1_756_000_000_000L else null,
        schemaVersion = 1,
        policyVersion = 1
    )
)

fun completedSession(
    id: String,
    completedAtEpochMillis: Long,
    exercises: List<WorkoutExercise>,
    status: SessionStatus = SessionStatus.COMPLETED
): WorkoutSession = WorkoutSession(
    id = id,
    name = "Synthetic session $id",
    startedAtTimestamp = completedAtEpochMillis - 1_000L,
    completedAtTimestamp = if (status == SessionStatus.COMPLETED) completedAtEpochMillis else null,
    actualDurationMinutes = 40,
    status = status,
    exercises = exercises.map { exercise -> exercise.copy(sessionId = id) }
)

fun exerciseInstance(
    exerciseId: String,
    sets: List<WorkoutSet>,
    id: String = "instance-${SyntheticSetIds.next()}",
    orderIndex: Int = 0
): WorkoutExercise = WorkoutExercise(
    id = id,
    sessionId = "unassigned",
    exerciseId = exerciseId,
    orderIndex = orderIndex,
    prescription = ExercisePrescription(
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetSets = sets.size.coerceAtLeast(1),
        repRange = RepRange(8, 10),
        targetWeight = 100.0
    ),
    sets = sets.mapIndexed { index, set ->
        set.copy(workoutExerciseId = id, setNumber = index + 1)
    }
)

fun completedNormalSet(
    id: String = "set-${SyntheticSetIds.next()}",
    type: SetType = SetType.NORMAL,
    rpe: Float? = null,
    rir: Int? = null,
    feltManageable: Boolean? = null
): WorkoutSet = WorkoutSet(
    id = id,
    workoutExerciseId = "unassigned",
    setNumber = 1,
    exerciseType = ExerciseType.WEIGHT_REPS,
    targetReps = 10,
    completedReps = 10,
    targetWeight = 100.0,
    completedWeight = 100.0,
    isCompleted = true,
    rpe = rpe,
    rir = rir,
    feltManageable = feltManageable,
    completedAtTimestamp = 1_756_000_000_000L,
    type = type
)

fun unfinishedSet(id: String = "set-${SyntheticSetIds.next()}"): WorkoutSet = WorkoutSet(
    id = id,
    workoutExerciseId = "unassigned",
    setNumber = 1,
    exerciseType = ExerciseType.WEIGHT_REPS,
    targetReps = 10,
    targetWeight = 100.0,
    isCompleted = false,
    type = SetType.NORMAL
)

fun stoppedSet(
    reason: SetStopReason = SetStopReason.USER_SKIPPED,
    id: String = "set-${SyntheticSetIds.next()}"
): WorkoutSet = unfinishedSet(id).copy(
    stopReason = reason,
    stoppedAtTimestamp = 1_756_000_000_000L
)

/** Deterministic, collision-free fixture identifiers so tests never rely on randomness. */
object SyntheticSetIds {
    private var counter = 0
    fun next(): Int = ++counter
}

/** Applies [transform] to every exercise instance in every session. */
fun List<WorkoutSession>.mutateExercises(
    transform: (WorkoutExercise) -> WorkoutExercise
): List<WorkoutSession> = map { session ->
    session.copy(exercises = session.exercises.map(transform))
}

/** Applies [transform] to every set in every exercise instance in every session. */
fun List<WorkoutSession>.mutateSets(
    transform: (WorkoutSet) -> WorkoutSet
): List<WorkoutSession> = mutateExercises { exercise ->
    exercise.copy(sets = exercise.sets.map(transform))
}
