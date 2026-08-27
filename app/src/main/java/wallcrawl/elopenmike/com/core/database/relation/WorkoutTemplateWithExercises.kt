package wallcrawl.elopenmike.com.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity

/** Room relation for a template and its prescription snapshots. */
data class WorkoutTemplateWithExercises(
    @Embedded
    val template: WorkoutTemplateEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "templateId"
    )
    val exercises: List<WorkoutTemplateExerciseEntity>
)
