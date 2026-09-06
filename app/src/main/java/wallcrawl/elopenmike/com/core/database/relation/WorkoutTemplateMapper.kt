package wallcrawl.elopenmike.com.core.database.relation

import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

/**
 * Converts a persisted template relation into the domain model.
 *
 * Like [toWorkoutSession], this is the single reader: the template library, the editor, and
 * an export all see the same stored prescription, so an archived template cannot describe
 * something different from what the app shows.
 */
internal fun WorkoutTemplateWithExercises.toWorkoutTemplate(): WorkoutTemplate = WorkoutTemplate(
    id = template.id,
    name = template.name,
    notes = template.notes,
    createdAtTimestamp = template.createdAtTimestamp,
    updatedAtTimestamp = template.updatedAtTimestamp,
    exercises = exercises.sortedBy { it.orderIndex }.map { exercise ->
        val effortTarget = persistedEffortTarget(
            minRir = exercise.effortMinRir,
            maxRir = exercise.effortMaxRir,
            owner = "Persisted template exercise"
        )
        requireCompletePersistedRestTarget(
            restClass = exercise.restClass,
            restTargetSource = exercise.restTargetSource,
            owner = "Persisted template exercise"
        )
        PlannedExercise(
            exerciseId = exercise.exerciseId,
            prescription = ExercisePrescription(
                exerciseType = exercise.exerciseType,
                targetSets = exercise.targetSets,
                repRange = exercise.targetRepMin?.let { minimum ->
                    RepRange(
                        min = minimum,
                        max = checkNotNull(exercise.targetRepMax) {
                            "Persisted template repetition target is missing its maximum."
                        }
                    )
                },
                targetWeight = exercise.targetWeight,
                targetAssistanceWeight = exercise.targetAssistanceWeight,
                targetDurationSeconds = exercise.targetDurationSeconds,
                targetDistanceMeters = exercise.targetDistanceMeters,
                restSeconds = exercise.restSeconds,
                effortTarget = effortTarget,
                restClass = exercise.restClass,
                restTargetSource = exercise.restTargetSource
            ),
            notes = exercise.notes
        )
    }
)
