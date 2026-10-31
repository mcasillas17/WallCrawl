package wallcrawl.elopenmike.com.core.database.relation

import wallcrawl.elopenmike.com.core.database.PERSISTED_LIST_SEPARATOR
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

/**
 * Converts a persisted session relation into the domain model.
 *
 * Full session readers share this mapper; focused historical comparisons reuse the
 * exercise/set mapper below rather than constructing partial WorkoutSession values.
 * Exercises and sets are returned in their persisted order, which keeps the result stable
 * regardless of the order the relation happens to load rows in.
 */
internal fun WorkoutSessionWithExercisesAndSets.toWorkoutSession(): WorkoutSession {
    val domainExercises = exercisesWithSets
        .sortedWith(compareBy<WorkoutExerciseWithSets> { it.exercise.orderIndex }.thenBy { it.exercise.id })
        .map { it.toWorkoutExercise() }

    val focusMusclesList = if (session.focusMusclesJson.isBlank()) {
        emptyList()
    } else {
        session.focusMusclesJson.split(PERSISTED_LIST_SEPARATOR).filter { it.isNotBlank() }
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

/** Shared by full immutable sessions and explicitly selected historical exercise slices. */
internal fun WorkoutExerciseWithSets.toWorkoutExercise(): WorkoutExercise {
    require((exercise.targetRepMin == null) == (exercise.targetRepMax == null)) {
        "Persisted repetition target must store both bounds together."
    }
    val effortTarget = persistedEffortTarget(
        minRir = exercise.effortMinRir,
        maxRir = exercise.effortMaxRir,
        owner = "Persisted workout exercise"
    )
    requireCompletePersistedRestTarget(
        restClass = exercise.restClass,
        restTargetSource = exercise.restTargetSource,
        owner = "Persisted workout exercise"
    )
    return WorkoutExercise(
        id = exercise.id,
        sessionId = exercise.sessionId,
        exerciseId = exercise.exerciseId,
        orderIndex = exercise.orderIndex,
        prescription = ExercisePrescription(
            exerciseType = exercise.exerciseType,
            targetSets = exercise.targetSets,
            repRange = exercise.targetRepMin?.let { RepRange(it, requireNotNull(exercise.targetRepMax)) },
            targetWeight = exercise.targetWeight,
            targetAssistanceWeight = exercise.targetAssistanceWeight,
            targetDurationSeconds = exercise.targetDurationSeconds,
            targetDistanceMeters = exercise.targetDistanceMeters,
            restSeconds = exercise.restSeconds,
            effortTarget = effortTarget,
            restClass = exercise.restClass,
            restTargetSource = exercise.restTargetSource
        ),
        notes = exercise.notes,
        sets = sets.sortedWith(compareBy<WorkoutSetEntity> { it.setNumber }.thenBy { it.id }).map { setEntity ->
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
    )
}
