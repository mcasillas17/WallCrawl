package wallcrawl.elopenmike.com.core.database.relation

import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

/**
 * Converts a persisted session relation into the domain model.
 *
 * Every reader of completed history goes through this one mapper, so the workout repository
 * and the weekly dose ledger can never disagree about what a stored session contains.
 * Exercises and sets are returned in their persisted order, which keeps the result stable
 * regardless of the order the relation happens to load rows in.
 */
internal fun WorkoutSessionWithExercisesAndSets.toWorkoutSession(): WorkoutSession {
    val domainExercises = exercisesWithSets
        .sortedBy { it.exercise.orderIndex }
        .map { exerciseWithSets ->
            val domainSets = exerciseWithSets.sets
                .sortedBy { it.setNumber }
                .map { setEntity ->
                    WorkoutSet(
                        id = setEntity.id,
                        workoutExerciseId = setEntity.workoutExerciseId,
                        setNumber = setEntity.setNumber,
                        exerciseType = setEntity.exerciseType,
                        targetReps = setEntity.targetReps,
                        completedReps = setEntity.completedReps,
                        targetWeight = setEntity.targetWeight,
                        completedWeight = setEntity.completedWeight,
                        targetAssistanceWeight = setEntity.targetAssistanceWeight,
                        completedAssistanceWeight = setEntity.completedAssistanceWeight,
                        targetDurationSeconds = setEntity.targetDurationSeconds,
                        completedDurationSeconds = setEntity.completedDurationSeconds,
                        targetDistanceMeters = setEntity.targetDistanceMeters,
                        completedDistanceMeters = setEntity.completedDistanceMeters,
                        isCompleted = setEntity.isCompleted,
                        rpe = setEntity.rpe,
                        rir = setEntity.rir,
                        feltManageable = setEntity.feltManageable,
                        completedAtTimestamp = setEntity.completedAtTimestamp,
                        stoppedAtTimestamp = setEntity.stoppedAtTimestamp,
                        stopReason = setEntity.stopReason,
                        type = setEntity.type
                    )
                }

            WorkoutExercise(
                id = exerciseWithSets.exercise.id,
                sessionId = exerciseWithSets.exercise.sessionId,
                exerciseId = exerciseWithSets.exercise.exerciseId,
                orderIndex = exerciseWithSets.exercise.orderIndex,
                prescription = ExercisePrescription(
                    exerciseType = exerciseWithSets.exercise.exerciseType,
                    targetSets = exerciseWithSets.exercise.targetSets,
                    repRange = exerciseWithSets.exercise.targetRepMin?.let { minimum ->
                        RepRange(
                            min = minimum,
                            max = checkNotNull(exerciseWithSets.exercise.targetRepMax) {
                                "Persisted repetition target is missing its maximum."
                            }
                        )
                    },
                    targetWeight = exerciseWithSets.exercise.targetWeight,
                    targetAssistanceWeight = exerciseWithSets.exercise.targetAssistanceWeight,
                    targetDurationSeconds = exerciseWithSets.exercise.targetDurationSeconds,
                    targetDistanceMeters = exerciseWithSets.exercise.targetDistanceMeters,
                    restSeconds = exerciseWithSets.exercise.restSeconds
                ),
                notes = exerciseWithSets.exercise.notes,
                sets = domainSets
            )
        }

    val focusMusclesList = if (session.focusMusclesJson.isBlank()) {
        emptyList()
    } else {
        session.focusMusclesJson.split("|||").filter { it.isNotBlank() }
    }

    return WorkoutSession(
        id = session.id,
        name = session.name,
        startedAtTimestamp = session.startedAtTimestamp,
        completedAtTimestamp = session.completedAtTimestamp,
        targetDurationMinutes = session.targetDurationMinutes,
        actualDurationMinutes = session.actualDurationMinutes,
        weightUnit = session.weightUnit,
        status = session.status,
        origin = session.origin,
        sourceTemplateId = session.sourceTemplateId,
        focusMuscles = focusMusclesList,
        exercises = domainExercises,
        notes = session.notes
    )
}
