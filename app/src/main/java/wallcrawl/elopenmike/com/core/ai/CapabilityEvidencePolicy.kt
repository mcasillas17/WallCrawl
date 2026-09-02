package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.CapabilityEvidence
import wallcrawl.elopenmike.com.core.model.CapabilityEvidencePolicyVersion
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceReason
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceScope
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.ComparableMovementShape
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSession

class CapabilityEvidencePolicy(
    private val policyVersion: CapabilityEvidencePolicyVersion =
        CapabilityEvidencePolicyVersion.TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1
) {

    fun derive(sessions: List<WorkoutSession>, exercises: List<Exercise>): CapabilityEvidenceSet {
        val observations = sessions.asSequence()
            .sortedWith(
                compareBy<WorkoutSession> { it.id }
                    .thenBy { it.completedAtTimestamp ?: Long.MIN_VALUE }
                    .thenBy { it.name }
            )
            .distinctBy { it.id }
            .filter { it.isCompletedSnapshot() }
            .flatMap { session ->
                session.exercises.asSequence()
                    .sortedWith(compareBy<WorkoutExercise> { it.orderIndex }.thenBy { it.id })
                    .mapNotNull { exercise ->
                        exactComparableObservation(session, exercise)
                    }
            }
            .groupBy { observation ->
                observation.appliesToExerciseId to observation.comparableShape
            }

        val evidenceCandidates = observations.values
            .mapNotNull { group ->
                val representative = group.first()
                val sessionIds = group.map { it.sessionId }.distinct().sorted()
                if (sessionIds.size < 2) {
                    null
                } else {
                    CapabilityEvidence(
                        policyVersion = policyVersion,
                        reason = CapabilityEvidenceReason.TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS,
                        appliesToExerciseId = representative.appliesToExerciseId,
                        demonstratedExerciseId = representative.demonstratedExerciseId,
                        scope = CapabilityEvidenceScope.EXACT_EXERCISE,
                        comparableShape = representative.comparableShape,
                        qualifyingSessionIds = sessionIds
                    )
                }
            }
        val records = evidenceCandidates
            .groupBy { it.appliesToExerciseId }
            .mapValues { (_, candidates) ->
                candidates.minWithOrNull(
                    compareBy<CapabilityEvidence> { scopePriority(it.scope) }
                        .thenBy { it.demonstratedExerciseId }
                        .thenBy { it.comparableShape.ordinal }
                        .thenBy { it.qualifyingSessionIds.joinToString(separator = "\u0000") }
                )!!
            }

        return CapabilityEvidenceSet.from(records)
    }

    private fun scopePriority(scope: CapabilityEvidenceScope): Int =
        when (scope) {
            CapabilityEvidenceScope.EXACT_EXERCISE -> 0
            CapabilityEvidenceScope.DIRECT_APPROVED_REGRESSION -> 1
        }

    private fun exactComparableObservation(
        session: WorkoutSession,
        exercise: WorkoutExercise
    ): ComparableObservation? {
        if (exercise.exerciseId.isBlank()) return null

        val comparableShape = exercise.prescription.comparableShapeOrNull() ?: return null
        val workSets = exercise.sets.filterNot { it.type == SetType.WARMUP }
        if (workSets.isEmpty()) return null
        if (!workSets.all { it.qualifiesForComparableShape(exercise.prescription, comparableShape) }) {
            return null
        }

        return ComparableObservation(
            sessionId = session.id,
            appliesToExerciseId = exercise.exerciseId,
            demonstratedExerciseId = exercise.exerciseId,
            comparableShape = comparableShape
        )
    }

    private fun WorkoutSession.isCompletedSnapshot(): Boolean =
        id.isNotBlank() &&
            status == SessionStatus.COMPLETED &&
            completedAtTimestamp != null &&
            completedAtTimestamp > 0L

    private fun ExercisePrescription.comparableShapeOrNull(): ComparableMovementShape? =
        when (exerciseType) {
            ExerciseType.WEIGHT_REPS ->
                if (
                    repRange != null &&
                    targetWeight.isPositiveFinite() &&
                    targetAssistanceWeight == null &&
                    targetDurationSeconds == null &&
                    targetDistanceMeters == null
                ) {
                    ComparableMovementShape.WEIGHT_REPETITIONS
                } else {
                    null
                }

            ExerciseType.BODYWEIGHT_REPS ->
                if (
                    repRange != null &&
                    targetWeight == null &&
                    targetAssistanceWeight == null &&
                    targetDurationSeconds == null &&
                    targetDistanceMeters == null
                ) {
                    ComparableMovementShape.BODYWEIGHT_REPETITIONS
                } else {
                    null
                }

            ExerciseType.ASSISTED_BODYWEIGHT ->
                if (
                    repRange != null &&
                    targetWeight == null &&
                    targetAssistanceWeight.isNonNegativeFinite() &&
                    targetDurationSeconds == null &&
                    targetDistanceMeters == null
                ) {
                    ComparableMovementShape.ASSISTED_BODYWEIGHT_REPETITIONS
                } else {
                    null
                }

            ExerciseType.DURATION ->
                if (
                    repRange == null &&
                    targetWeight == null &&
                    targetAssistanceWeight == null &&
                    targetDurationSeconds.isPositive() &&
                    targetDistanceMeters == null
                ) {
                    ComparableMovementShape.TIMED_DURATION
                } else {
                    null
                }

            ExerciseType.DISTANCE_DURATION -> when {
                repRange != null || targetWeight != null || targetAssistanceWeight != null -> null
                targetDistanceMeters.isPositiveFinite() && targetDurationSeconds.isPositive() ->
                    ComparableMovementShape.DISTANCE_DURATION_DISTANCE_AND_TIME
                targetDistanceMeters.isPositiveFinite() && targetDurationSeconds == null ->
                    ComparableMovementShape.DISTANCE_DURATION_DISTANCE_ONLY
                targetDistanceMeters == null && targetDurationSeconds.isPositive() ->
                    ComparableMovementShape.DISTANCE_DURATION_TIME_ONLY
                else -> null
            }
        }

    private fun WorkoutSet.qualifiesForComparableShape(
        prescription: ExercisePrescription,
        comparableShape: ComparableMovementShape
    ): Boolean {
        if (!isCompleted) return false
        if (feltManageable != true) return false
        if (completedAtTimestamp.isNotPositive()) return false
        if (stoppedAtTimestamp != null) return false
        if (stopReason != null) return false
        if (exerciseType != prescription.exerciseType) return false

        return when (comparableShape) {
            ComparableMovementShape.WEIGHT_REPETITIONS ->
                targetReps.isValidPositiveReps() &&
                    completedReps.isValidPositiveReps() &&
                    targetWeight.isValidPositiveTargetWeight() &&
                    completedWeight.isValidPositiveLoggedWeight() &&
                    targetAssistanceWeight == null &&
                    completedAssistanceWeight == null &&
                    targetDurationSeconds == null &&
                    completedDurationSeconds == null &&
                    targetDistanceMeters == null &&
                    completedDistanceMeters == null

            ComparableMovementShape.BODYWEIGHT_REPETITIONS ->
                targetReps.isValidPositiveReps() &&
                    completedReps.isValidPositiveReps() &&
                    targetWeight == null &&
                    completedWeight == null &&
                    targetAssistanceWeight == null &&
                    completedAssistanceWeight == null &&
                    targetDurationSeconds == null &&
                    completedDurationSeconds == null &&
                    targetDistanceMeters == null &&
                    completedDistanceMeters == null

            ComparableMovementShape.ASSISTED_BODYWEIGHT_REPETITIONS ->
                targetReps.isValidPositiveReps() &&
                    completedReps.isValidPositiveReps() &&
                    targetWeight == null &&
                    completedWeight == null &&
                    targetAssistanceWeight.isValidNonNegativeTargetWeight() &&
                    completedAssistanceWeight.isValidNonNegativeLoggedWeight() &&
                    targetDurationSeconds == null &&
                    completedDurationSeconds == null &&
                    targetDistanceMeters == null &&
                    completedDistanceMeters == null

            ComparableMovementShape.TIMED_DURATION ->
                targetReps == null &&
                    completedReps == null &&
                    targetWeight == null &&
                    completedWeight == null &&
                    targetAssistanceWeight == null &&
                    completedAssistanceWeight == null &&
                    targetDurationSeconds.isValidPositiveDuration() &&
                    completedDurationSeconds.isValidPositiveDuration() &&
                    targetDistanceMeters == null &&
                    completedDistanceMeters == null

            ComparableMovementShape.DISTANCE_DURATION_DISTANCE_ONLY ->
                targetReps == null &&
                    completedReps == null &&
                    targetWeight == null &&
                    completedWeight == null &&
                    targetAssistanceWeight == null &&
                    completedAssistanceWeight == null &&
                    targetDurationSeconds == null &&
                    completedDurationSeconds == null &&
                    targetDistanceMeters.isValidPositiveDistance() &&
                    completedDistanceMeters.isValidPositiveDistance()

            ComparableMovementShape.DISTANCE_DURATION_TIME_ONLY ->
                targetReps == null &&
                    completedReps == null &&
                    targetWeight == null &&
                    completedWeight == null &&
                    targetAssistanceWeight == null &&
                    completedAssistanceWeight == null &&
                    targetDurationSeconds.isValidPositiveDuration() &&
                    completedDurationSeconds.isValidPositiveDuration() &&
                    targetDistanceMeters == null &&
                    completedDistanceMeters == null

            ComparableMovementShape.DISTANCE_DURATION_DISTANCE_AND_TIME ->
                targetReps == null &&
                    completedReps == null &&
                    targetWeight == null &&
                    completedWeight == null &&
                    targetAssistanceWeight == null &&
                    completedAssistanceWeight == null &&
                    targetDurationSeconds.isValidPositiveDuration() &&
                    completedDurationSeconds.isValidPositiveDuration() &&
                    targetDistanceMeters.isValidPositiveDistance() &&
                    completedDistanceMeters.isValidPositiveDistance()
        }
    }

    private fun Int?.isPositive(): Boolean = this != null && this > 0

    private fun Int?.isValidPositiveReps(): Boolean =
        this != null && this in 1..MAX_LOGGED_REPS

    private fun Int?.isValidPositiveDuration(): Boolean =
        this != null && this in 1..MAX_LOGGED_DURATION_SECONDS

    private fun Long?.isNotPositive(): Boolean = this == null || this <= 0L

    private fun Double?.isPositiveFinite(): Boolean = this != null && isFinite() && this > 0.0

    private fun Double?.isNonNegativeFinite(): Boolean = this != null && isFinite() && this >= 0.0

    private fun Double?.isValidPositiveTargetWeight(): Boolean =
        this != null && isFinite() && this > 0.0 && this <= MAX_TARGET_WEIGHT

    private fun Double?.isValidPositiveLoggedWeight(): Boolean =
        this != null && isFinite() && this > 0.0 && this <= MAX_LOGGED_WEIGHT

    private fun Double?.isValidNonNegativeTargetWeight(): Boolean =
        this != null && isFinite() && this >= 0.0 && this <= MAX_TARGET_WEIGHT

    private fun Double?.isValidNonNegativeLoggedWeight(): Boolean =
        this != null && isFinite() && this >= 0.0 && this <= MAX_LOGGED_WEIGHT

    private fun Double?.isValidPositiveDistance(): Boolean =
        this != null && isFinite() && this > 0.0 && this <= MAX_LOGGED_DISTANCE_METERS

    private companion object {
        const val MAX_LOGGED_REPS = 1_000
        const val MAX_TARGET_WEIGHT = 10_000.0
        const val MAX_LOGGED_WEIGHT = 100_000.0
        const val MAX_LOGGED_DURATION_SECONDS = 86_400
        const val MAX_LOGGED_DISTANCE_METERS = 1_000_000.0
    }

    private data class ComparableObservation(
        val sessionId: String,
        val appliesToExerciseId: String,
        val demonstratedExerciseId: String,
        val comparableShape: ComparableMovementShape
    )
}
