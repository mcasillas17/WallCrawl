package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityEvidence
import wallcrawl.elopenmike.com.core.model.CapabilityEvidencePolicyVersion
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceReason
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceScope
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.ComparableMovementShape
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSession

class CapabilityEvidencePolicyTest {

    private val policy = CapabilityEvidencePolicy()

    @Test
    fun derive_requiresTwoDistinctQualifyingSessions() {
        val sessions = listOf(
            weightRepsSession(
                sessionId = "session-1",
                exerciseId = "bench-press",
                completedAtTimestamp = 10_000L
            )
        )

        val result = policy.derive(sessions = sessions, exercises = emptyList())

        assertThat(result).isEqualTo(CapabilityEvidenceSet.empty())
    }

    @Test
    fun derive_emitsExactEvidenceFromTwoComparableManageableSessions() {
        val sessions = listOf(
            weightRepsSession(
                sessionId = "session-b",
                exerciseId = "bench-press",
                completedAtTimestamp = 20_000L
            ),
            weightRepsSession(
                sessionId = "session-a",
                exerciseId = "bench-press",
                completedAtTimestamp = 10_000L
            )
        )

        val result = policy.derive(sessions = sessions, exercises = exactExerciseCatalog())

        assertThat(result).isEqualTo(
            CapabilityEvidenceSet.from(
                mapOf(
                    "bench-press" to CapabilityEvidence(
                        policyVersion = CapabilityEvidencePolicyVersion.TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1,
                        reason = CapabilityEvidenceReason.TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS,
                        appliesToExerciseId = "bench-press",
                        demonstratedExerciseId = "bench-press",
                        scope = CapabilityEvidenceScope.EXACT_EXERCISE,
                        comparableShape = ComparableMovementShape.WEIGHT_REPETITIONS,
                        qualifyingSessionIds = listOf("session-a", "session-b")
                    )
                )
            )
        )
    }

    @Test
    fun derive_ignoresDuplicateExerciseObservationsWithinOneSession() {
        val sessions = listOf(
            workoutSessionWithDuplicateObservations(
                sessionId = "session-1",
                exerciseId = "bench-press",
                completedAtTimestamp = 10_000L
            )
        )

        val result = policy.derive(sessions = sessions, exercises = exactExerciseCatalog())

        assertThat(result).isEqualTo(CapabilityEvidenceSet.empty())
    }

    private fun exactExerciseCatalog(): List<Exercise> = listOf(
        Exercise(
            id = "bench-press",
            name = "Bench Press",
            primaryMuscles = listOf("Chest"),
            type = ExerciseType.WEIGHT_REPS
        )
    )

    private fun weightRepsSession(
        sessionId: String,
        exerciseId: String,
        completedAtTimestamp: Long
    ): WorkoutSession =
        WorkoutSession(
            id = sessionId,
            name = "Session $sessionId",
            completedAtTimestamp = completedAtTimestamp,
            status = SessionStatus.COMPLETED,
            exercises = listOf(
                WorkoutExercise(
                    id = "$sessionId-$exerciseId",
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 1,
                        repRange = RepRange(8, 8),
                        targetWeight = 45.0
                    ),
                    sets = listOf(
                        WorkoutSet(
                            id = "$sessionId-$exerciseId-set-1",
                            workoutExerciseId = "$sessionId-$exerciseId",
                            setNumber = 1,
                            exerciseType = ExerciseType.WEIGHT_REPS,
                            targetReps = 8,
                            completedReps = 8,
                            targetWeight = 45.0,
                            completedWeight = 45.0,
                            isCompleted = true,
                            feltManageable = true,
                            completedAtTimestamp = completedAtTimestamp - 1_000L,
                            type = SetType.NORMAL
                        )
                    )
                )
            )
        )

    private fun workoutSessionWithDuplicateObservations(
        sessionId: String,
        exerciseId: String,
        completedAtTimestamp: Long
    ): WorkoutSession {
        val firstExercise = weightRepsExercise(
            sessionId = sessionId,
            exerciseId = exerciseId,
            orderIndex = 0,
            completedAtTimestamp = completedAtTimestamp
        )
        val duplicateExercise = weightRepsExercise(
            sessionId = sessionId,
            exerciseId = exerciseId,
            orderIndex = 1,
            completedAtTimestamp = completedAtTimestamp
        )
        return WorkoutSession(
            id = sessionId,
            name = "Session $sessionId",
            completedAtTimestamp = completedAtTimestamp,
            status = SessionStatus.COMPLETED,
            exercises = listOf(firstExercise, duplicateExercise)
        )
    }

    private fun weightRepsExercise(
        sessionId: String,
        exerciseId: String,
        orderIndex: Int,
        completedAtTimestamp: Long
    ): WorkoutExercise =
        WorkoutExercise(
            id = "$sessionId-$exerciseId-$orderIndex",
            sessionId = sessionId,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 1,
                repRange = RepRange(8, 8),
                targetWeight = 45.0
            ),
            sets = listOf(
                WorkoutSet(
                    id = "$sessionId-$exerciseId-$orderIndex-set-1",
                    workoutExerciseId = "$sessionId-$exerciseId-$orderIndex",
                    setNumber = 1,
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    targetReps = 8,
                    completedReps = 8,
                    targetWeight = 45.0,
                    completedWeight = 45.0,
                    isCompleted = true,
                    feltManageable = true,
                    completedAtTimestamp = completedAtTimestamp - 1_000L,
                    type = SetType.NORMAL
                )
            )
        )
}
