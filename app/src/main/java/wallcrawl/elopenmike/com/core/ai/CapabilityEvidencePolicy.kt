package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.CapabilityEvidence
import wallcrawl.elopenmike.com.core.model.CapabilityEvidencePolicyVersion
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceReason
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceScope
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.ComparableMovementShape
import wallcrawl.elopenmike.com.core.model.Exercise
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

        val records = observations.values
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
            .associateBy { it.appliesToExerciseId }

        return CapabilityEvidenceSet.from(records)
    }

    private fun exactComparableObservation(
        session: WorkoutSession,
        exercise: WorkoutExercise
    ): ComparableObservation? {
        if (exercise.exerciseId.isBlank()) return null
        if (exercise.prescription.exerciseType != ExerciseType.WEIGHT_REPS) return null
        if (exercise.prescription.repRange == null) return null
        if (exercise.prescription.targetWeight == null ||
            !exercise.prescription.targetWeight.isFinite() ||
            exercise.prescription.targetWeight <= 0.0
        ) {
            return null
        }

        val workSets = exercise.sets.filterNot { it.type == SetType.WARMUP }
        if (workSets.isEmpty()) return null
        if (!workSets.all { it.qualifiesForWeightReps(exercise) }) return null

        return ComparableObservation(
            sessionId = session.id,
            appliesToExerciseId = exercise.exerciseId,
            demonstratedExerciseId = exercise.exerciseId,
            comparableShape = ComparableMovementShape.WEIGHT_REPETITIONS
        )
    }

    private fun WorkoutSession.isCompletedSnapshot(): Boolean =
        id.isNotBlank() &&
            status == SessionStatus.COMPLETED &&
            completedAtTimestamp != null &&
            completedAtTimestamp > 0L

    private fun WorkoutSet.qualifiesForWeightReps(exercise: WorkoutExercise): Boolean {
        if (exercise.exerciseId.isBlank()) return false
        if (exercise.prescription.exerciseType != ExerciseType.WEIGHT_REPS) return false
        if (!isCompleted) return false
        if (feltManageable != true) return false
        if (completedAtTimestamp == null || completedAtTimestamp <= 0L) return false
        if (stoppedAtTimestamp != null) return false
        if (stopReason != null) return false
        if (exerciseType != ExerciseType.WEIGHT_REPS) return false
        if (targetReps == null || targetReps <= 0) return false
        if (completedReps == null || completedReps <= 0) return false
        if (targetWeight == null || !targetWeight.isFinite() || targetWeight <= 0.0) return false
        if (completedWeight == null || !completedWeight.isFinite() || completedWeight <= 0.0) return false
        if (targetAssistanceWeight != null) return false
        if (completedAssistanceWeight != null) return false
        if (targetDurationSeconds != null) return false
        if (completedDurationSeconds != null) return false
        if (targetDistanceMeters != null) return false
        if (completedDistanceMeters != null) return false
        return true
    }

    private data class ComparableObservation(
        val sessionId: String,
        val appliesToExerciseId: String,
        val demonstratedExerciseId: String,
        val comparableShape: ComparableMovementShape
    )
}
