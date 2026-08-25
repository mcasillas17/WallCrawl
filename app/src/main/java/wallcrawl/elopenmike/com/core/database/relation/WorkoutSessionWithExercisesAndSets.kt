package wallcrawl.elopenmike.com.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity

/**
 * Composite relation containing a workout exercise and all its individual sets.
 */
data class WorkoutExerciseWithSets(
    @Embedded
    val exercise: WorkoutExerciseEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "workoutExerciseId"
    )
    val sets: List<WorkoutSetEntity>
)

/**
 * Full composite relation containing a workout session, all of its ordered exercises,
 * and all logged sets for each exercise.
 */
data class WorkoutSessionWithExercisesAndSets(
    @Embedded
    val session: WorkoutSessionEntity,

    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val exercisesWithSets: List<WorkoutExerciseWithSets>
)
